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
import com.example.farsilandtv.data.database.CachedEpisode
import com.example.farsilandtv.data.database.CachedVideoUrl
import com.example.farsilandtv.data.database.ContentDatabase
import com.example.farsilandtv.data.api.FarsiPlexApiService
import com.example.farsilandtv.data.api.RetrofitClient
import com.example.farsilandtv.data.health.ScraperHealthTracker
import com.example.farsilandtv.data.repository.ContentRepository
import com.example.farsilandtv.data.scraper.FarsiPlexMetadataScraper
import com.example.farsilandtv.utils.NotificationHelper
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Background sync worker for FarsiPlex.com content
 *
 * ENHANCED: Now scrapes full metadata like Python auto-updater!
 * - Checks sitemaps every 10-15 minutes for new/updated content
 * - Scrapes FULL metadata (not just URLs)
 * - Extracts video URLs and stores in database
 * - Incremental updates (only new/changed items)
 * - Max 5 retry attempts to prevent infinite loop
 *
 * Sync frequency: Every 15 minutes (configurable in FarsilandApp.kt)
 */
@HiltWorker
class FarsiPlexSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val contentRepo: ContentRepository,
    private val healthTracker: ScraperHealthTracker
) : CoroutineWorker(context, params) {

    private val contentDb = ContentDatabase.getDatabase(context)
    private val farsiPlexApi = FarsiPlexApiService(RetrofitClient.getHttpClient())
    // FIXED: Use same SharedPreferences as SyncSettingsFragment to show sync status
    private val prefs = context.getSharedPreferences("sync_state", Context.MODE_PRIVATE)

    /**
     * EXTERNAL AUDIT FIX CRITICAL 1.2: Implement getForegroundInfo() for expedited work
     * Required for Android API 30 and below to prevent IllegalStateException crash
     *
     * Issue: Emergency sync uses setExpedited() but doesn't provide foreground info
     * Impact: Immediate crash on Android 9, 10, 11 (API 28-30) when emergency sync triggers
     * Fix: Return ForegroundInfo with notification to satisfy OS requirement
     */
    override suspend fun getForegroundInfo(): ForegroundInfo {
        return createForegroundInfo()
    }

    private fun createForegroundInfo(): ForegroundInfo {
        val channelId = "farsiplex_sync_channel"
        val title = "FarsiPlex Sync"
        val message = "Syncing content in background..."

        // Create notification channel for Android 8.0+ (API 26+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "FarsiPlex Sync",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Background FarsiPlex content synchronization"
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
            Log.i(TAG, "Starting FarsiPlex background sync...")

            // Check if sync is enabled
            val syncPrefs = applicationContext.getSharedPreferences("sync_settings", Context.MODE_PRIVATE)
            if (!syncPrefs.getBoolean("farsiplex_sync_enabled", true)) {
                Log.d(TAG, "FarsiPlex sync disabled by user preference")
                return@withContext Result.success()
            }

            // Check cancellation
            if (isStopped) {
                Log.w(TAG, "Sync cancelled before starting")
                return@withContext Result.retry()
            }

            // FIXED: Use same timestamp key as SyncSettingsFragment expects
            var lastSyncTime = prefs.getLong("farsiplex_last_sync_timestamp", 0L)
            val currentTime = System.currentTimeMillis()

            // OPTIMIZATION: On first sync with bundled DB, use DB's newest content date
            if (lastSyncTime == 0L) {
                val newestInDb = getNewestFarsiPlexTimestamp()
                if (newestInDb > 0) {
                    Log.i(TAG, "First sync detected with bundled DB - using DB timestamp")
                    lastSyncTime = newestInDb
                    prefs.edit().putLong("farsiplex_last_sync_timestamp", newestInDb).apply()
                }
            }

            // Sync new content (lightweight - only recent items)
            val newContentCount = syncNewContent()

            // Update sync time (use correct key that UI expects)
            prefs.edit().putLong("last_sync_timestamp", currentTime).apply()

            // Clear repository cache to ensure UI shows fresh data immediately
            if (newContentCount > 0) {
                contentRepo.clearCache()
                Log.i(TAG, "FarsiPlex sync completed: $newContentCount new items (cache cleared)")
            } else {
                Log.i(TAG, "FarsiPlex sync completed: No new content")
            }

            // REFACTOR (2025-11-21): Trigger Paging auto-refresh after successful sync
            // Notifies ContentRepository to invalidate Pagers and refresh UI
            contentRepo.notifySyncCompleted()

            // Phase 5: Record health tracker success
            healthTracker.recordSuccess(ScraperHealthTracker.ScraperSource.FARSIPLEX)

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "FarsiPlex sync failed: ${e.message}", e)

            // Phase 5: Record health tracker failure
            healthTracker.recordFailure(ScraperHealthTracker.ScraperSource.FARSIPLEX, e.message)

            // Check if we've exceeded max retry attempts
            val attemptCount = runAttemptCount
            if (attemptCount >= MAX_RETRY_ATTEMPTS) {
                Log.e(TAG, "Max retry attempts ($MAX_RETRY_ATTEMPTS) reached. Giving up.")
                Log.e(TAG, "User notification: FarsiPlex sync failed after $attemptCount attempts")

                // Show notification to user about sync failure
                val notificationHelper = NotificationHelper(applicationContext)
                notificationHelper.showSyncErrorNotification("FarsiPlex", attemptCount)

                return@withContext Result.failure()
            }

            // Exponential backoff: WorkManager will retry with increasing delay
            Log.w(TAG, "Retry attempt #$attemptCount of $MAX_RETRY_ATTEMPTS")

            Result.retry()
        }
    }

    /**
     * Sync new content from FarsiPlex sitemaps
     * ENHANCED: Now scrapes FULL metadata with video URLs (like Python auto-updater)
     * Only checks recent items (last 20) to keep sync lightweight
     */
    private suspend fun syncNewContent(): Int {
        var newItemsCount = 0

        try {
            // Sync recent movies (last 20 in sitemap)
            val recentMovies = farsiPlexApi.getRecentMovies(limit = 20)
            for (movieUrl in recentMovies) {
                if (isStopped) break

                val slug = extractSlug(movieUrl.loc)
                val lastmod = movieUrl.lastmod

                // Check if already in database
                val exists = contentDb.movieDao().getMovieByUrl(movieUrl.loc)

                // Only scrape if new OR updated (lastmod changed)
                if (exists == null || (lastmod != null && exists.lastUpdated < parseDate(lastmod))) {
                    Log.i(TAG, "Scraping movie: $slug")

                    // Scrape FULL metadata (title, description, poster, rating, etc.)
                    val movie = FarsiPlexMetadataScraper.scrapeMovie(movieUrl.loc, slug, lastmod)
                    if (movie != null) {
                        contentDb.movieDao().insertMovies(listOf(movie))
                        newItemsCount++
                        Log.i(TAG, "✓ Synced movie: ${movie.title}")
                    }
                }
            }

            // Sync recent TV shows (last 20)
            val recentShows = farsiPlexApi.getRecentTvShows(limit = 20)
            Log.i(TAG, "Processing ${recentShows.size} recent TV shows from sitemap")
            for (showUrl in recentShows) {
                if (isStopped) break

                val slug = extractSlug(showUrl.loc)
                val lastmod = showUrl.lastmod

                // Skip malformed entries (only hash, no actual slug)
                if (slug.startsWith("-") && slug.length == 9) {
                    Log.w(TAG, "Skipping malformed series URL: ${showUrl.loc}")
                    continue
                }

                val exists = contentDb.seriesDao().getSeriesByUrl(showUrl.loc)
                val sitemapDate = parseDate(lastmod)

                if (exists != null) {
                    Log.d(TAG, "Series $slug exists: DB lastUpdated=${exists.lastUpdated}, sitemap parsed=$sitemapDate, sitemap raw=$lastmod")
                }

                if (exists == null || (lastmod != null && exists.lastUpdated < parseDate(lastmod))) {
                    val parsedDate = parseDate(lastmod)
                    Log.i(TAG, "Scraping series: $slug (lastmod=$lastmod, parsed=$parsedDate)")

                    // Scrape FULL metadata
                    val series = FarsiPlexMetadataScraper.scrapeSeries(showUrl.loc, slug, lastmod)
                    if (series != null) {
                        contentDb.seriesDao().insertMultipleSeries(listOf(series))
                        newItemsCount++
                        Log.i(TAG, "✓ Synced series: ${series.title} (lastUpdated=${series.lastUpdated})")
                    }
                }
            }

            // Sync recent episodes (last 20)
            val recentEpisodes = farsiPlexApi.getRecentEpisodes(limit = 20)
            val affectedSeriesIds = mutableSetOf<Int>()
            for (episodeUrl in recentEpisodes) {
                if (isStopped) break

                val slug = extractSlug(episodeUrl.loc)
                val lastmod = episodeUrl.lastmod

                val exists = contentDb.episodeDao().getEpisodeByUrl(episodeUrl.loc)

                if (exists == null || (lastmod != null && exists.lastUpdated < parseDate(lastmod))) {
                    Log.i(TAG, "Scraping episode: $slug")

                    // Scrape FULL metadata + video URLs
                    val result = FarsiPlexMetadataScraper.scrapeEpisode(episodeUrl.loc, slug, lastmod)
                    if (result != null) {
                        val (episode, videoUrls) = result

                        // Insert episode
                        contentDb.episodeDao().insertEpisodes(listOf(episode))

                        // Track affected series for episode count update
                        episode.seriesId?.let { affectedSeriesIds.add(it) }

                        // Insert video URLs
                        if (videoUrls.isNotEmpty()) {
                            contentDb.videoUrlDao().insertVideoUrls(videoUrls)
                            Log.d(TAG, "  └─ Saved ${videoUrls.size} video URLs")
                        }

                        newItemsCount++
                        Log.i(TAG, "✓ Synced episode: ${episode.seriesTitle} S${episode.season}E${episode.episode}")
                    }
                }
            }

            // Update episode counts for affected series
            if (affectedSeriesIds.isNotEmpty()) {
                updateSeriesEpisodeCounts(affectedSeriesIds.toList())
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error syncing new content: ${e.message}", e)
        }

        return newItemsCount
    }

    /**
     * Extract slug from FarsiPlex URL
     * Example: https://farsiplex.com/movie/test-movie/ -> test-movie
     */
    private fun extractSlug(url: String): String {
        return url.trimEnd('/').split('/').last()
    }


    /**
     * Parse ISO date to timestamp
     */
    private fun parseDate(dateStr: String?): Long {
        return try {
            if (dateStr != null) {
                // Try ISO 8601 with timezone first (e.g., "2024-05-29T10:30:00+00:00")
                val formatWithTimezone = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", java.util.Locale.US)
                try {
                    formatWithTimezone.parse(dateStr)?.time
                } catch (e: Exception) {
                    // Try with time but no timezone (e.g., "2024-05-29T10:30:00")
                    val formatWithTime = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
                    try {
                        formatWithTime.parse(dateStr)?.time
                    } catch (e: Exception) {
                        // Fallback to date-only format (e.g., "2024-05-29")
                        val formatDateOnly = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                        formatDateOnly.parse(dateStr)?.time
                    }
                } ?: System.currentTimeMillis()
            } else {
                System.currentTimeMillis()
            }
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
    }

    /**
     * Get newest content timestamp from FarsiPlex content in DB
     * Used to detect bundled DB and skip full sync on first run
     */
    private suspend fun getNewestFarsiPlexTimestamp(): Long {
        return try {
            // Query for FarsiPlex URLs only
            val newestMovie = contentDb.movieDao().getNewestMovieTimestampByUrlPattern("%farsiplex.com%") ?: 0L
            val newestSeries = contentDb.seriesDao().getNewestSeriesTimestampByUrlPattern("%farsiplex.com%") ?: 0L
            val newestEpisode = contentDb.episodeDao().getNewestEpisodeTimestampByUrlPattern("%farsiplex.com%") ?: 0L
            maxOf(newestMovie, newestSeries, newestEpisode)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting newest FarsiPlex timestamp: ${e.message}")
            0L
        }
    }

    /**
     * Update episode/season counts for series after syncing new episodes
     */
    private suspend fun updateSeriesEpisodeCounts(seriesIds: List<Int>) {
        if (seriesIds.isEmpty()) return

        try {
            for (seriesId in seriesIds) {
                val episodeCount = contentDb.episodeDao().getEpisodeCountForSeries(seriesId)
                val seasonCount = contentDb.episodeDao().getSeasonCount(seriesId)

                val series = contentDb.seriesDao().getSeriesById(seriesId)
                if (series != null && (series.totalEpisodes != episodeCount || series.totalSeasons != seasonCount)) {
                    val updated = series.copy(
                        totalEpisodes = episodeCount,
                        totalSeasons = seasonCount,
                        lastUpdated = System.currentTimeMillis()
                    )
                    contentDb.seriesDao().updateSeries(updated)
                    Log.d(TAG, "Updated series $seriesId: $seasonCount seasons, $episodeCount episodes")
                }
            }
            Log.i(TAG, "✓ Updated episode counts for ${seriesIds.size} series")
        } catch (e: Exception) {
            Log.e(TAG, "Error updating series episode counts: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "FarsiPlexSyncWorker"
        const val WORK_NAME = "farsiplex_content_sync"
        private const val MAX_RETRY_ATTEMPTS = 5
        private const val NOTIFICATION_ID = 1002  // Unique ID for FarsiPlex sync notification
    }
}
