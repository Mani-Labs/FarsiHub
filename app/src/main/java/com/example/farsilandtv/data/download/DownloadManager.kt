package com.example.farsilandtv.data.download

import android.content.Context
import android.util.Log
import android.os.StatFs
import com.example.farsilandtv.data.database.AppDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Offline Mode - Download Manager
 *
 * Handles downloading video files for offline playback:
 * - Queue management (add, pause, resume, cancel)
 * - Background downloading with progress tracking
 * - Storage management
 * - Download resumption after app restart
 *
 * Hilt-managed singleton - injected via constructor
 */
@Singleton
class DownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloadDao: DownloadDao
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    // Active download jobs - using ConcurrentHashMap for thread-safety
    // FIX: mutableMapOf is not thread-safe, which can cause race conditions
    // when multiple coroutines access the map concurrently
    private val activeDownloads = ConcurrentHashMap<String, Job>()

    // Current download state
    private val _isDownloading = MutableStateFlow(false)
    val isDownloading: StateFlow<Boolean> = _isDownloading.asStateFlow()

    // Download progress for UI
    private val _downloadProgress = MutableStateFlow<Map<String, Int>>(emptyMap())
    val downloadProgress: StateFlow<Map<String, Int>> = _downloadProgress.asStateFlow()

    companion object {
        private const val TAG = "DownloadManager"
        private const val DOWNLOADS_DIR = "downloads"
        private const val BUFFER_SIZE = 8 * 1024 // 8KB buffer
        private const val MIN_FREE_SPACE_MB = 100L // Minimum 100MB free space required

        // DL-L2: Configurable progress throttling
        var PROGRESS_THROTTLE_MS = 500L // Throttle progress updates (configurable)
        var PROGRESS_MIN_CHANGE = 1 // Minimum % change to trigger update (configurable)
    }

    // DL-H1: Track last progress update time per download for throttling
    private val lastProgressUpdateTime = ConcurrentHashMap<String, Long>()

    /**
     * DL-L3: Safe file deletion helper
     * Extracts repeated file deletion pattern with error handling
     */
    private fun safeDeleteFile(path: String?): Boolean {
        return path?.let {
            try {
                File(it).delete()
            } catch (e: Exception) {
                Log.w(TAG, "Delete failed: $path - ${e.message}")
                false
            }
        } ?: false
    }

    /**
     * DL-L7: Retry with exponential backoff helper
     * Retries operation with increasing delays between attempts
     * @param maxAttempts Maximum number of retry attempts (default: 3)
     * @param initialDelayMs Initial delay before first retry in milliseconds (default: 1000ms)
     * @param block Suspending operation to retry
     * @return Result of successful operation
     * @throws Exception Last exception if all retries fail
     */
    private suspend fun <T> withRetry(
        maxAttempts: Int = 3,
        initialDelayMs: Long = 1000,
        block: suspend () -> T
    ): T {
        var currentDelay = initialDelayMs
        repeat(maxAttempts - 1) { attempt ->
            try {
                return block()
            } catch (e: Exception) {
                Log.w(TAG, "Retry attempt ${attempt + 1}/$maxAttempts failed: ${e.message}")
                delay(currentDelay)
                currentDelay *= 2 // Exponential backoff
            }
        }
        // Final attempt (will throw if it fails)
        return block()
    }

    /**
     * Get downloads directory
     */
    private fun getDownloadsDir(): File {
        val dir = File(context.getExternalFilesDir(null), DOWNLOADS_DIR)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    /**
     * ROBUSTNESS FIX: Check available disk space before downloading
     * Prevents crash/corruption when storage is full
     * @return Available space in MB, or -1 if unable to determine
     */
    private fun getAvailableSpaceMb(): Long {
        return try {
            val path = getDownloadsDir()
            val stat = StatFs(path.absolutePath)
            val availableBytes = stat.availableBlocksLong * stat.blockSizeLong
            availableBytes / (1024 * 1024) // Convert to MB
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check available space: ${e.message}")
            -1L // Unable to determine
        }
    }

    /**
     * Check if there's enough space for a download
     * @param requiredMb Required space in MB (0 to just check minimum)
     * @return true if enough space available
     */
    fun hasEnoughSpace(requiredMb: Long = 0): Boolean {
        val available = getAvailableSpaceMb()
        if (available < 0) return true // Can't determine, proceed anyway
        val required = maxOf(requiredMb, MIN_FREE_SPACE_MB)
        return available >= required
    }

    /**
     * Queue a movie for download
     */
    suspend fun queueMovieDownload(
        movieId: Int,
        title: String,
        posterUrl: String?,
        videoUrl: String
    ): Boolean {
        val id = DownloadConstants.movieId(movieId)
        return queueDownload(
            DownloadItem(
                id = id,
                contentType = ContentType.MOVIE,
                title = title,
                subtitle = null,
                posterUrl = posterUrl,
                videoUrl = videoUrl,
                localFilePath = null
            )
        )
    }

    /**
     * Queue an episode for download
     */
    suspend fun queueEpisodeDownload(
        episodeId: Int,
        seriesTitle: String,
        episodeInfo: String,  // e.g., "S1 E5"
        posterUrl: String?,
        videoUrl: String
    ): Boolean {
        val id = DownloadConstants.episodeId(episodeId)
        return queueDownload(
            DownloadItem(
                id = id,
                contentType = ContentType.EPISODE,
                title = seriesTitle,
                subtitle = episodeInfo,
                posterUrl = posterUrl,
                videoUrl = videoUrl,
                localFilePath = null
            )
        )
    }

    /**
     * Queue a download
     * ROBUSTNESS FIX: Checks available disk space before queueing
     * DL-H2 FIX: Check actual file size vs available space (estimate based on fileSize if available)
     */
    private suspend fun queueDownload(item: DownloadItem): Boolean {
        return try {
            // DL-H2: Check disk space with better estimation
            // If fileSize is known, require that much + buffer. Otherwise use MIN_FREE_SPACE_MB
            val requiredMb = if (item.fileSize > 0) {
                (item.fileSize / (1024 * 1024)) + 50 // File size + 50MB buffer
            } else {
                MIN_FREE_SPACE_MB // Default minimum if file size unknown
            }

            if (!hasEnoughSpace(requiredMb)) {
                Log.e(TAG, "Insufficient disk space for download: ${item.title} (needs ${requiredMb}MB)")
                return false
            }

            // Check if already downloading or completed
            val existing = downloadDao.getDownloadById(item.id)
            if (existing?.isComplete == true) {
                Log.d(TAG, "Already downloaded: ${item.id}")
                return true
            }
            if (existing?.isDownloading == true) {
                Log.d(TAG, "Already downloading: ${item.id}")
                return true
            }

            // Insert or update download entry
            downloadDao.insert(item.copy(status = DownloadStatus.PENDING))
            Log.d(TAG, "Queued download: ${item.title}")

            // Start download
            startDownload(item.id)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to queue download: ${e.message}")
            false
        }
    }

    /**
     * Start downloading a queued item
     */
    private fun startDownload(downloadId: String) {
        Log.d(TAG, "startDownload called for: $downloadId")
        // Cancel existing job if any
        activeDownloads[downloadId]?.cancel()

        val job = scope.launch {
            try {
                Log.d(TAG, "Coroutine launched for: $downloadId")
                val download = downloadDao.getDownloadById(downloadId)
                if (download == null) {
                    Log.e(TAG, "Download not found in DB: $downloadId")
                    return@launch
                }

                Log.d(TAG, "Starting download: ${download.title} from ${download.videoUrl}")
                downloadDao.updateStatus(downloadId, DownloadStatus.DOWNLOADING)
                _isDownloading.value = true

                // Generate local file path
                val fileName = "${downloadId}_${System.currentTimeMillis()}.mp4"
                val localFile = File(getDownloadsDir(), fileName)
                Log.d(TAG, "Will save to: ${localFile.absolutePath}")

                // Download the file
                val success = downloadFile(download.videoUrl, localFile, downloadId)

                if (success) {
                    val fileSize = localFile.length()
                    downloadDao.markCompleted(
                        id = downloadId,
                        bytes = fileSize,
                        status = DownloadStatus.COMPLETED,
                        filePath = localFile.absolutePath,
                        completedAt = System.currentTimeMillis()
                    )
                    Log.d(TAG, "Download completed: ${download.title}")
                } else {
                    downloadDao.markFailed(
                        id = downloadId,
                        status = DownloadStatus.FAILED,
                        errorMessage = "Download failed"
                    )
                    localFile.delete()
                }

            } catch (e: CancellationException) {
                // DL-H5 FIX: Properly distinguish CancellationException from errors
                Log.d(TAG, "Download cancelled: $downloadId")
                downloadDao.updateStatus(downloadId, DownloadStatus.PAUSED)
                // Re-throw to propagate cancellation properly
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Download error: ${e.message}")
                downloadDao.markFailed(
                    id = downloadId,
                    status = DownloadStatus.FAILED,
                    errorMessage = e.message ?: "Unknown error"
                )
            } finally {
                activeDownloads.remove(downloadId)
                updateDownloadingState()
            }
        }

        activeDownloads[downloadId] = job
    }

    /**
     * Download file from URL to local storage
     */
    private suspend fun downloadFile(
        url: String,
        localFile: File,
        downloadId: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "downloadFile starting for: $url")
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 10; SHIELD Android TV) FarsiHub/1.0")
                .build()

            Log.d(TAG, "Making HTTP request...")
            httpClient.newCall(request).execute().use { response ->
                Log.d(TAG, "Got response: ${response.code}")
                if (!response.isSuccessful) {
                    Log.e(TAG, "Download failed: HTTP ${response.code}")
                    return@withContext false
                }

                val body = response.body ?: return@withContext false
                val contentLength = body.contentLength()

                // DL-H4 FIX: Re-fetch download item before update to avoid stale data
                // Update file size
                if (contentLength > 0) {
                    val currentDownload = downloadDao.getDownloadById(downloadId)
                    currentDownload?.let { download ->
                        downloadDao.update(download.copy(fileSize = contentLength))
                    }
                }

                FileOutputStream(localFile).use { output ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        var bytesRead: Int
                        var totalBytesRead: Long = 0

                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            // Check for cancellation
                            if (!isActive) {
                                return@withContext false
                            }

                            output.write(buffer, 0, bytesRead)
                            totalBytesRead += bytesRead

                            // DL-H1: Throttle progress updates to reduce database writes and UI updates
                            if (contentLength > 0) {
                                val progress = ((totalBytesRead * 100) / contentLength).toInt()
                                val now = System.currentTimeMillis()
                                val lastUpdate = lastProgressUpdateTime[downloadId] ?: 0L
                                val timeSinceLastUpdate = now - lastUpdate

                                // Update if: progress changed by at least 1% OR 500ms elapsed OR completed
                                val currentProgress = _downloadProgress.value[downloadId] ?: 0
                                val progressDelta = progress - currentProgress

                                if (progressDelta >= 1 || timeSinceLastUpdate >= PROGRESS_THROTTLE_MS || progress >= 100) {
                                    downloadDao.updateProgress(downloadId, totalBytesRead)
                                    // N7 FIX: Use atomic update instead of non-atomic read-modify-write
                                    _downloadProgress.update { it + (downloadId to progress) }
                                    lastProgressUpdateTime[downloadId] = now
                                }
                            }
                        }
                    }
                }

                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Download error: ${e.message}")
            false
        }
    }

    /**
     * Pause a download
     */
    suspend fun pauseDownload(downloadId: String) {
        activeDownloads[downloadId]?.cancel()
        downloadDao.updateStatus(downloadId, DownloadStatus.PAUSED)
    }

    /**
     * Resume a paused download
     */
    fun resumeDownload(downloadId: String) {
        startDownload(downloadId)
    }

    /**
     * Cancel and delete a download
     * DL-L3: Uses safe file deletion helper
     */
    suspend fun cancelDownload(downloadId: String) {
        activeDownloads[downloadId]?.cancel()

        // Delete local file if exists (using safe helper)
        val filePath = downloadDao.getDownloadById(downloadId)?.localFilePath
        safeDeleteFile(filePath)

        downloadDao.deleteById(downloadId)
        _downloadProgress.value = _downloadProgress.value - downloadId
    }

    /**
     * Delete a completed download
     * DL-H3 FIX: Use withContext for atomic read + delete operation
     * DL-L3: Uses safe file deletion helper
     */
    suspend fun deleteDownload(downloadId: String) {
        withContext(Dispatchers.IO) {
            // Read file path and delete file atomically (using safe helper)
            val download = downloadDao.getDownloadById(downloadId)
            safeDeleteFile(download?.localFilePath)

            // Delete DB entry
            downloadDao.deleteById(downloadId)
        }
    }

    /**
     * Check if content is downloaded
     */
    suspend fun isDownloaded(contentId: String): Boolean {
        return downloadDao.isDownloaded(contentId)
    }

    /**
     * Get download by ID
     */
    suspend fun getDownload(downloadId: String): DownloadItem? {
        return downloadDao.getDownloadById(downloadId)
    }

    /**
     * Get local file path for downloaded content
     */
    suspend fun getLocalFilePath(contentId: String): String? {
        return downloadDao.getDownloadById(contentId)?.localFilePath
    }

    /**
     * Get all downloads as Flow
     */
    fun getAllDownloads(): Flow<List<DownloadItem>> {
        return downloadDao.getAllDownloads()
    }

    /**
     * Get completed downloads
     */
    fun getCompletedDownloads(): Flow<List<DownloadItem>> {
        return downloadDao.getCompletedDownloads()
    }

    /**
     * Get active downloads
     */
    fun getActiveDownloads(): Flow<List<DownloadItem>> {
        return downloadDao.getActiveDownloads()
    }

    /**
     * Get total download storage used
     */
    suspend fun getTotalStorageUsed(): Long {
        return downloadDao.getTotalDownloadedSize() ?: 0L
    }

    /**
     * Get completed downloads count
     */
    suspend fun getCompletedCount(): Int {
        return downloadDao.getCompletedCount()
    }

    /**
     * Clear all completed downloads
     * FIX: Only delete files for completed downloads, not ALL files in directory
     * Previous bug would delete in-progress downloads too
     * DL-L3: Uses safe file deletion helper
     * DL-L4: Optional filter parameter to clear downloads older than N days
     * @param olderThanDays If provided, only clear downloads completed more than N days ago
     */
    suspend fun clearCompletedDownloads(olderThanDays: Int? = null) {
        // Get only completed downloads from database first
        val completedDownloads = downloadDao.getCompletedDownloadsOnce()

        // Filter by age if requested
        val downloadsToDelete = if (olderThanDays != null) {
            val cutoffTime = System.currentTimeMillis() - (olderThanDays * 24 * 60 * 60 * 1000L)
            completedDownloads.filter { (it.completedAt ?: 0) < cutoffTime }
        } else {
            completedDownloads
        }

        // Delete only files for selected downloads (using safe helper)
        downloadsToDelete.forEach { download ->
            safeDeleteFile(download.localFilePath)
        }

        // Remove entries from database
        if (olderThanDays != null) {
            // Delete specific IDs
            downloadsToDelete.forEach { download ->
                downloadDao.deleteById(download.id)
            }
        } else {
            // Delete all completed
            downloadDao.clearCompletedDownloads()
        }
    }

    /**
     * Update downloading state
     */
    private fun updateDownloadingState() {
        _isDownloading.value = activeDownloads.isNotEmpty()
    }

    /**
     * Resume pending downloads on app start
     * DL-C4 FIX: Use firstOrNull() instead of return@collect for proper Flow handling
     */
    fun resumePendingDownloads() {
        scope.launch {
            try {
                // Get current downloads once using firstOrNull() instead of collect + return@collect
                val downloads = downloadDao.getAllDownloads().firstOrNull() ?: emptyList()

                // Resume any downloads that were in progress
                downloads.filter { it.status == DownloadStatus.DOWNLOADING }
                    .forEach { download ->
                        // Reset to pending and restart
                        downloadDao.updateStatus(download.id, DownloadStatus.PENDING)
                        startDownload(download.id)
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Error resuming downloads: ${e.message}")
            }
        }
    }

    /**
     * Cleanup resources when manager is no longer needed
     * Note: Hilt manages the lifecycle, but this can be called for explicit cleanup
     */
    fun cleanup() {
        Log.d(TAG, "Cleaning up DownloadManager resources")
        // Cancel all active downloads
        activeDownloads.values.forEach { it.cancel() }
        activeDownloads.clear()
        // Cancel the coroutine scope
        scope.cancel()
    }
}
