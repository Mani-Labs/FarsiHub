package com.example.farsilandtv.data.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.example.farsilandtv.R
import com.example.farsilandtv.data.scraper.VideoUrlScraper
import com.example.farsilandtv.data.scraper.getOrNull
import kotlinx.coroutines.flow.firstOrNull
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * WorkManager worker for background downloads
 * Handles downloading content even when the app is in the background
 */
@HiltWorker
class DownloadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val downloadManager: DownloadManager
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "DownloadWorker"
        private const val CHANNEL_ID = "download_channel"
        private const val NOTIFICATION_ID = 1001

        // Input keys
        const val KEY_CONTENT_TYPE = "content_type"
        const val KEY_CONTENT_ID = "content_id"
        const val KEY_TITLE = "title"
        const val KEY_SUBTITLE = "subtitle"
        const val KEY_POSTER_URL = "poster_url"
        const val KEY_PAGE_URL = "page_url"

        // Content types
        const val TYPE_MOVIE = "movie"
        const val TYPE_EPISODE = "episode"

        /**
         * Create work request for downloading a movie
         */
        fun createMovieDownloadRequest(
            movieId: Int,
            title: String,
            posterUrl: String?,
            pageUrl: String
        ): OneTimeWorkRequest {
            val inputData = workDataOf(
                KEY_CONTENT_TYPE to TYPE_MOVIE,
                KEY_CONTENT_ID to movieId,
                KEY_TITLE to title,
                KEY_POSTER_URL to (posterUrl ?: ""),
                KEY_PAGE_URL to pageUrl
            )

            return OneTimeWorkRequestBuilder<DownloadWorker>()
                .setInputData(inputData)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .addTag("download_movie_$movieId")
                .build()
        }

        /**
         * Create work request for downloading an episode
         */
        fun createEpisodeDownloadRequest(
            episodeId: Int,
            seriesTitle: String,
            episodeInfo: String,
            posterUrl: String?,
            pageUrl: String
        ): OneTimeWorkRequest {
            val inputData = workDataOf(
                KEY_CONTENT_TYPE to TYPE_EPISODE,
                KEY_CONTENT_ID to episodeId,
                KEY_TITLE to seriesTitle,
                KEY_SUBTITLE to episodeInfo,
                KEY_POSTER_URL to (posterUrl ?: ""),
                KEY_PAGE_URL to pageUrl
            )

            return OneTimeWorkRequestBuilder<DownloadWorker>()
                .setInputData(inputData)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .addTag("download_episode_$episodeId")
                .build()
        }
    }

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    override suspend fun doWork(): Result {
        val contentType = inputData.getString(KEY_CONTENT_TYPE) ?: return Result.failure()
        val contentId = inputData.getInt(KEY_CONTENT_ID, -1)
        val title = inputData.getString(KEY_TITLE) ?: "Unknown"
        val subtitle = inputData.getString(KEY_SUBTITLE)
        val posterUrl = inputData.getString(KEY_POSTER_URL)
        val pageUrl = inputData.getString(KEY_PAGE_URL) ?: return Result.failure()

        if (contentId == -1) return Result.failure()

        Log.d(TAG, "Starting background download for: $title ($contentType)")

        // Create notification channel
        createNotificationChannel()

        // Show initial notification
        val displayName = if (subtitle != null) "$title - $subtitle" else title
        showProgressNotification(displayName, 0)

        return try {
            // Step 1: Scrape video URL
            setProgress(workDataOf("status" to "Scraping"))
            showProgressNotification(displayName, 10, "Finding video...")

            val videoUrls = withContext(Dispatchers.IO) {
                VideoUrlScraper.extractVideoUrls(pageUrl)
            }.getOrNull() ?: emptyList()

            if (videoUrls.isEmpty()) {
                showFailureNotification(displayName, "No video URL found")
                return Result.failure()
            }

            // Select best quality
            val bestUrl = videoUrls.maxByOrNull { videoUrl ->
                val quality = videoUrl.quality.lowercase()
                when {
                    quality.contains("1080") -> 4
                    quality.contains("720") -> 3
                    quality.contains("480") -> 2
                    quality.contains("360") -> 1
                    else -> 0
                }
            }

            if (bestUrl == null) {
                showFailureNotification(displayName, "No suitable video quality")
                return Result.failure()
            }

            // Step 2: Queue download with DownloadManager
            setProgress(workDataOf("status" to "Downloading"))
            showProgressNotification(displayName, 30, "Downloading (${bestUrl.quality})...")

            // DL-H7 FIX: Observe download progress and update notification
            val downloadId = when (contentType) {
                TYPE_MOVIE -> DownloadConstants.movieId(contentId)
                TYPE_EPISODE -> DownloadConstants.episodeId(contentId)
                else -> null
            }

            val queued = when (contentType) {
                TYPE_MOVIE -> {
                    downloadManager.queueMovieDownload(
                        movieId = contentId,
                        title = title,
                        posterUrl = posterUrl,
                        videoUrl = bestUrl.url
                    )
                }
                TYPE_EPISODE -> {
                    downloadManager.queueEpisodeDownload(
                        episodeId = contentId,
                        seriesTitle = title,
                        episodeInfo = subtitle ?: "",
                        posterUrl = posterUrl,
                        videoUrl = bestUrl.url
                    )
                }
                else -> false
            }

            if (queued) {
                showSuccessNotification(displayName)
                Result.success()
            } else {
                showFailureNotification(displayName, "Failed to start download")
                Result.failure()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Download failed: ${e.message}", e)
            showFailureNotification(displayName, e.message ?: "Download failed")
            Result.failure()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Download progress notifications"
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun showProgressNotification(title: String, progress: Int, status: String = "Preparing...") {
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(status)
            .setProgress(100, progress, progress == 0)
            .setOngoing(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun showSuccessNotification(title: String) {
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Download Complete")
            .setContentText(title)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun showFailureNotification(title: String, error: String) {
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("Download Failed")
            .setContentText("$title: $error")
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }
}
