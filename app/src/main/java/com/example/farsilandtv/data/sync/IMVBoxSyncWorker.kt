package com.example.farsilandtv.data.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.ForegroundInfo
import androidx.core.app.NotificationCompat
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.hilt.work.HiltWorker
import com.example.farsilandtv.data.database.CachedMovie
import com.example.farsilandtv.data.database.CachedSeries
import com.example.farsilandtv.data.database.ContentDatabase
import com.example.farsilandtv.data.health.ScraperHealthTracker
import com.example.farsilandtv.data.imvbox.IMVBoxApiService
import com.example.farsilandtv.data.repository.ContentRepository
import com.example.farsilandtv.data.scraper.ScraperResult
import com.example.farsilandtv.utils.NotificationHelper
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Background sync worker for IMVBox.com content
 *
 * Syncs movies and series from IMVBox by scraping list pages.
 * Since IMVBox doesn't provide a sitemap API, we sync the first few pages of content.
 *
 * Features:
 * - Syncs recent movies (first 2 pages)
 * - Syncs recent series (first 2 pages)
 * - Incremental updates (checks if content already exists)
 * - Max 5 retry attempts with exponential backoff
 *
 * Sync frequency: Every 15 minutes (configurable in FarsilandApp.kt)
 */
@HiltWorker
class IMVBoxSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val contentRepo: ContentRepository,
    private val healthTracker: ScraperHealthTracker,
    private val imvboxService: IMVBoxApiService
) : CoroutineWorker(context, params) {

    private val contentDb = ContentDatabase.getDatabase(context)
    private val prefs = context.getSharedPreferences("sync_state", Context.MODE_PRIVATE)

    /**
     * Implement getForegroundInfo() for expedited work
     * Required for Android API 30 and below
     */
    override suspend fun getForegroundInfo(): ForegroundInfo {
        return createForegroundInfo()
    }

    private fun createForegroundInfo(): ForegroundInfo {
        val channelId = "imvbox_sync_channel"
        val title = "IMVBox Sync"
        val message = "Syncing content in background..."

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "IMVBox Sync",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Background IMVBox content synchronization"
            }
            val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()

        return ForegroundInfo(NOTIFICATION_ID, notification)
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "Starting IMVBox background sync...")

            // Check if sync is enabled
            val syncPrefs = applicationContext.getSharedPreferences("sync_settings", Context.MODE_PRIVATE)
            if (!syncPrefs.getBoolean("imvbox_sync_enabled", true)) {
                Log.d(TAG, "IMVBox sync disabled by user preference")
                return@withContext Result.success()
            }

            // Check cancellation
            if (isStopped) {
                Log.w(TAG, "Sync cancelled before starting")
                return@withContext Result.retry()
            }

            val currentTime = System.currentTimeMillis()

            // Sync new content
            val newContentCount = syncNewContent()

            // Update sync time
            prefs.edit().putLong("imvbox_last_sync_timestamp", currentTime).apply()

            // Clear repository cache to ensure UI shows fresh data
            if (newContentCount > 0) {
                contentRepo.clearCache()
                Log.i(TAG, "IMVBox sync completed: $newContentCount new items (cache cleared)")
            } else {
                Log.i(TAG, "IMVBox sync completed: No new content")
            }

            // Notify sync completed for Paging auto-refresh
            contentRepo.notifySyncCompleted()

            // Record health tracker success
            healthTracker.recordSuccess(ScraperHealthTracker.ScraperSource.IMVBOX)

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "IMVBox sync failed: ${e.message}", e)

            // Record health tracker failure
            healthTracker.recordFailure(ScraperHealthTracker.ScraperSource.IMVBOX, e.message)

            // Check if we've exceeded max retry attempts
            val attemptCount = runAttemptCount
            if (attemptCount >= MAX_RETRY_ATTEMPTS) {
                Log.e(TAG, "Max retry attempts ($MAX_RETRY_ATTEMPTS) reached. Giving up.")

                // Show notification to user about sync failure
                val notificationHelper = NotificationHelper(applicationContext)
                notificationHelper.showSyncErrorNotification("IMVBox", attemptCount)

                return@withContext Result.failure()
            }

            Log.w(TAG, "Retry attempt #$attemptCount of $MAX_RETRY_ATTEMPTS")
            Result.retry()
        }
    }

    /**
     * Sync new content from IMVBox
     * Fetches first 2 pages of movies and series
     *
     * IMPORTANT: IMVBox returns content sorted by "new-releases" (newest first).
     * We assign timestamps in REVERSE order so the first (newest) item gets
     * the highest dateAdded timestamp, preserving the correct order in our DB.
     */
    private suspend fun syncNewContent(): Int {
        var newItemsCount = 0

        try {
            // Collect all movies from all pages first
            val allMovies = mutableListOf<com.example.farsilandtv.data.models.Movie>()
            for (page in 1..PAGES_TO_SYNC) {
                if (isStopped) break
                val moviesResult = imvboxService.getMovies(page, "new-releases")
                if (moviesResult is ScraperResult.Success) {
                    allMovies.addAll(moviesResult.data)
                }
            }

            // Insert movies with timestamps in REVERSE order
            // First item (newest) gets highest timestamp
            val baseTime = System.currentTimeMillis()
            val moviesToInsert = mutableListOf<CachedMovie>()

            for ((index, movie) in allMovies.withIndex()) {
                if (isStopped) break

                // Check if already in database
                val exists = contentDb.movieDao().getMovieByUrl(movie.farsilandUrl)
                if (exists == null) {
                    // Assign timestamp: newest item (index 0) gets baseTime
                    // Older items get earlier timestamps (baseTime - offset)
                    val timestamp = baseTime - (index * 1000L) // 1 second offset per item

                    val cachedMovie = CachedMovie(
                        id = movie.id,
                        title = movie.title,
                        posterUrl = movie.posterUrl,
                        farsilandUrl = movie.farsilandUrl,
                        description = movie.description,
                        year = movie.year,
                        rating = movie.rating,
                        runtime = null,
                        director = movie.director,
                        cast = null,
                        genres = movie.genres.joinToString(","),
                        dateAdded = timestamp,
                        lastUpdated = timestamp
                    )
                    moviesToInsert.add(cachedMovie)
                    newItemsCount++
                    Log.i(TAG, "✓ Synced movie: ${movie.title}")
                }
            }

            // Batch insert all movies
            if (moviesToInsert.isNotEmpty()) {
                contentDb.movieDao().insertMovies(moviesToInsert)
            }

            // Collect all series from all pages
            val allSeries = mutableListOf<com.example.farsilandtv.data.models.Series>()
            for (page in 1..PAGES_TO_SYNC) {
                if (isStopped) break
                val seriesResult = imvboxService.getSeries(page, "new-releases")
                if (seriesResult is ScraperResult.Success) {
                    allSeries.addAll(seriesResult.data)
                }
            }

            // Insert series with timestamps in REVERSE order
            val seriesBaseTime = System.currentTimeMillis()
            val seriesToInsert = mutableListOf<CachedSeries>()

            for ((index, series) in allSeries.withIndex()) {
                if (isStopped) break

                val exists = contentDb.seriesDao().getSeriesByUrl(series.farsilandUrl)
                if (exists == null) {
                    val timestamp = seriesBaseTime - (index * 1000L)

                    val cachedSeries = CachedSeries(
                        id = series.id,
                        title = series.title,
                        posterUrl = series.posterUrl,
                        backdropUrl = series.backdropUrl,
                        farsilandUrl = series.farsilandUrl,
                        description = series.description,
                        year = series.year,
                        rating = null,
                        totalSeasons = series.totalSeasons ?: 1,
                        totalEpisodes = series.totalEpisodes ?: 0,
                        cast = null,
                        genres = series.genres.joinToString(","),
                        dateAdded = timestamp,
                        lastUpdated = timestamp
                    )
                    seriesToInsert.add(cachedSeries)
                    newItemsCount++
                    Log.i(TAG, "✓ Synced series: ${series.title}")
                }
            }

            // Batch insert all series
            if (seriesToInsert.isNotEmpty()) {
                contentDb.seriesDao().insertMultipleSeries(seriesToInsert)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error syncing new content: ${e.message}", e)
        }

        return newItemsCount
    }

    companion object {
        private const val TAG = "IMVBoxSyncWorker"
        const val WORK_NAME = "imvbox_content_sync"
        private const val MAX_RETRY_ATTEMPTS = 5
        private const val NOTIFICATION_ID = 1003  // Unique ID for IMVBox sync notification
        private const val PAGES_TO_SYNC = 2  // Sync first 2 pages of content
    }
}
