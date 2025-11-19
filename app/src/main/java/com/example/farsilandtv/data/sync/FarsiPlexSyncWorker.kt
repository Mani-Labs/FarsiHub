package com.example.farsilandtv.data.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.farsilandtv.data.database.CachedMovie
import com.example.farsilandtv.data.database.CachedSeries
import com.example.farsilandtv.data.database.CachedEpisode
import com.example.farsilandtv.data.database.CachedVideoUrl
import com.example.farsilandtv.data.database.ContentDatabase
import com.example.farsilandtv.data.api.FarsiPlexApiService
import com.example.farsilandtv.data.api.RetrofitClient
import com.example.farsilandtv.data.repository.ContentRepository
import com.example.farsilandtv.data.scraper.FarsiPlexMetadataScraper
import com.example.farsilandtv.utils.NotificationHelper
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
class FarsiPlexSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val contentDb = ContentDatabase.getDatabase(context)
    private val farsiPlexApi = FarsiPlexApiService(RetrofitClient.getHttpClient())
    private val contentRepo = ContentRepository(context)
    // FIXED: Use same SharedPreferences as SyncSettingsFragment to show sync status
    private val prefs = context.getSharedPreferences("sync_state", Context.MODE_PRIVATE)

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
            val lastSyncTime = prefs.getLong("last_sync_timestamp", 0L)
            val currentTime = System.currentTimeMillis()

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

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "FarsiPlex sync failed: ${e.message}", e)

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

    companion object {
        private const val TAG = "FarsiPlexSyncWorker"
        const val WORK_NAME = "farsiplex_content_sync"
        private const val MAX_RETRY_ATTEMPTS = 5
    }
}
