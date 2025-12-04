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
import com.example.farsilandtv.R
import com.example.farsilandtv.data.database.CachedMovie
import com.example.farsilandtv.data.database.CachedSeries
import com.example.farsilandtv.data.database.CachedEpisode
import com.example.farsilandtv.data.database.CachedGenre
import com.example.farsilandtv.data.database.ContentDatabase
import com.example.farsilandtv.data.api.RetrofitClient
import com.example.farsilandtv.data.health.ScraperHealthTracker
import com.example.farsilandtv.data.repository.ContentRepository
import com.example.farsilandtv.utils.SyncPreferences
import com.example.farsilandtv.utils.NotificationHelper
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * High-frequency background sync worker
 *
 * UPDATED: Aggressive sync configuration:
 * 1. Runs every 10 minutes (aggressive, real-time updates)
 * 2. No WiFi restriction (syncs on any network connection)
 * 3. No quiet hours restriction (syncs 24/7)
 * 4. Activity-aware (only skips during active video playback)
 * 5. Batch API calls (efficient network usage)
 * 6. Exponential backoff for failures (max 5 attempts)
 *
 * Sync frequency: Every 10 minutes
 */
@HiltWorker
class ContentSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: ContentRepository,
    private val healthTracker: ScraperHealthTracker
) : CoroutineWorker(context, params) {

    private val contentDb = ContentDatabase.getDatabase(context)
    private val wordPressApi = RetrofitClient.wordPressApi
    private val syncPrefs = SyncPreferences(context)
    private val prefs = context.getSharedPreferences("sync_state", Context.MODE_PRIVATE)

    // Cache for genre ID to name mapping
    private var genreCache: Map<Int, String>? = null

    // EXTERNAL AUDIT FIX H2.2: Removed in-memory series title cache
    // Issue: Loading all series caused GC pauses (5000+ items = ~500KB+ heap allocation)
    // Solution: Use direct SQL queries via getSeriesByTitle() DAO method

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
        val channelId = "content_sync_channel"
        val title = "Farsiland Sync"
        val message = "Syncing content in background..."

        // Create notification channel for Android 8.0+ (API 26+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Content Sync",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Background content synchronization"
            }
            val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()

        return ForegroundInfo(NOTIFICATION_ID, notification)
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            // Check if sync is enabled
            if (!syncPrefs.syncEnabled) {
                Log.d(TAG, "Sync disabled by user preference")
                return@withContext Result.success()
            }

            // Check if user is actively watching content
            if (isUserActivelyWatching()) {
                Log.d(TAG, "Skipping sync: user is watching content (avoid interruption)")
                return@withContext Result.retry()
            }

            val startTime = System.currentTimeMillis()
            var lastSyncTimestamp = prefs.getLong("last_sync_timestamp", 0L)

            // OPTIMIZATION: On first sync with bundled DB, use DB's newest content date
            // instead of doing a full sync. This prevents re-fetching 200+ items that
            // are already in the bundled database.
            if (lastSyncTimestamp == 0L) {
                val newestInDb = getNewestContentTimestamp()
                if (newestInDb > 0) {
                    Log.i(TAG, "First sync detected with bundled DB - using DB timestamp: ${Date(newestInDb)}")
                    lastSyncTimestamp = newestInDb
                    // Save it so future syncs are incremental
                    prefs.edit().putLong("last_sync_timestamp", newestInDb).apply()
                }
            }

            Log.i(TAG, "=== Farsiland Sync Started ===")
            Log.i(TAG, "Last sync: ${Date(lastSyncTimestamp)}")

            // Check cancellation before starting sync
            if (isStopped) {
                Log.w(TAG, "Sync cancelled before starting")
                return@withContext Result.retry()
            }

            // Phase 0: Sync genres (needed for genre extraction)
            if (isStopped) {
                Log.w(TAG, "Sync cancelled before genre sync")
                return@withContext Result.retry()
            }
            syncGenres()
            Log.d(TAG, "Phase 0 complete: Genre cache loaded")

            // Phase 1: Sync new content (batch)
            if (isStopped) {
                Log.w(TAG, "Sync cancelled before new content sync")
                return@withContext Result.retry()
            }
            val movieCount = syncMovies(lastSyncTimestamp)
            val seriesCount = syncSeries(lastSyncTimestamp)

            // AUDIT FIX C2: Enhanced check to prevent orphaned episodes
            // Skip episode sync if series sync failed (seriesCount < 0)
            // This prevents episodes from being inserted with seriesId = 0
            // EXTERNAL AUDIT FIX H2.2: Removed cache check, now uses direct SQL queries
            val episodeCount = if (seriesCount >= 0) {
                syncEpisodes(lastSyncTimestamp)
            } else {
                Log.e(TAG, "CRITICAL: Aborting episode sync - series sync FAILED")
                0
            }

            // Phase 2: Sync monitored series (only series user cares about)
            if (isStopped) {
                Log.w(TAG, "Sync cancelled before monitored series sync")
                return@withContext Result.retry()
            }
            val updatedSeriesCount = syncMonitoredSeries()
            Log.d(TAG, "Phase 2 complete: $updatedSeriesCount series updated")

            // Phase 3: Sync favorites metadata
            if (isStopped) {
                Log.w(TAG, "Sync cancelled before favorites sync")
                return@withContext Result.retry()
            }
            val updatedFavoritesCount = syncFavorites()
            Log.d(TAG, "Phase 3 complete: $updatedFavoritesCount favorites updated")

            // Phase 4: Cleanup ghost records (EXTERNAL AUDIT FIX C1.2)
            if (isStopped) {
                Log.w(TAG, "Sync cancelled before ghost record cleanup")
                return@withContext Result.retry()
            }
            val cleanedCount = cleanupGhostRecords()
            Log.d(TAG, "Phase 4 complete: $cleanedCount ghost records cleaned")

            // Check final cancellation before committing
            if (isStopped) {
                Log.w(TAG, "Sync cancelled before committing")
                return@withContext Result.retry()
            }

            // Update timestamps
            val currentTimestamp = System.currentTimeMillis()
            prefs.edit().putLong("last_sync_timestamp", currentTimestamp).apply()
            syncPrefs.lastSyncTime = currentTimestamp
            syncPrefs.lastContentUpdate = currentTimestamp

            val totalItems = movieCount + seriesCount + episodeCount
            val duration = currentTimestamp - startTime

            Log.i(TAG, "=== Sync Complete ===")
            Log.i(TAG, "Movies: $movieCount, Series: $seriesCount, Episodes: $episodeCount")
            Log.i(TAG, "Total: $totalItems items in ${duration}ms")
            if (totalItems > 0) {
                Log.i(TAG, "Efficiency: ${duration/totalItems}ms per item")
            }

            // REFACTOR (2025-11-21): Trigger Paging auto-refresh after successful sync
            // Notifies ContentRepository to invalidate Pagers and refresh UI
            repository.notifySyncCompleted()

            // Phase 5: Record health tracker success
            healthTracker.recordSuccess(ScraperHealthTracker.ScraperSource.FARSILAND)

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Sync failed: ${e.message}", e)

            // Phase 5: Record health tracker failure
            healthTracker.recordFailure(ScraperHealthTracker.ScraperSource.FARSILAND, e.message)

            // Check if we've exceeded max retry attempts
            val attemptCount = runAttemptCount
            if (attemptCount >= MAX_RETRY_ATTEMPTS) {
                Log.e(TAG, "Max retry attempts ($MAX_RETRY_ATTEMPTS) reached. Giving up.")
                Log.e(TAG, "User notification: Background sync failed after $attemptCount attempts")

                // Show notification to user about sync failure
                val notificationHelper = NotificationHelper(applicationContext)
                notificationHelper.showSyncErrorNotification("Farsiland", attemptCount)

                return@withContext Result.failure()
            }

            // Exponential backoff: WorkManager will retry with increasing delay
            Log.w(TAG, "Retry attempt #$attemptCount of $MAX_RETRY_ATTEMPTS (exponential backoff)")

            Result.retry()
        }
    }

    /**
     * Phase 0: Sync genres and build genre cache
     * Must be called before syncing content to enable genre extraction
     */
    private suspend fun syncGenres() {
        try {
            val wpGenres = wordPressApi.getGenres(perPage = 100)

            // Save genres to database
            val cachedGenres = wpGenres.map { wpGenre ->
                CachedGenre(
                    id = wpGenre.id,
                    name = wpGenre.name,
                    slug = wpGenre.slug
                )
            }
            contentDb.genreDao().insertGenres(cachedGenres)

            // Build genre cache for fast lookups
            genreCache = wpGenres.associate { it.id to it.name }

            Log.d(TAG, "Synced ${wpGenres.size} genres")
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing genres: ${e.message}", e)
            // Load genres from database as fallback
            genreCache = emptyMap()
        }
    }

    /**
     * Convert Unix timestamp (ms) to ISO 8601 format for WordPress API
     * Farsiland expects: "2025-11-12T10:30:00" (no timezone)
     */
    private fun timestampToApiDate(timestamp: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
        return sdf.format(Date(timestamp))
    }

    /**
     * Phase 2: Sync monitored series (only series user is watching)
     * Efficient: Only checks series in user's watchlist/favorites
     * @return Number of monitored series updated
     */
    private suspend fun syncMonitoredSeries(): Int {
        return try {
            // Check for new episodes in monitored series
            // This would require access to WatchlistRepository
            // For now, return 0 (placeholder for future implementation)
            0
        } catch (e: Exception) {
            Log.e(TAG, "Monitored series sync failed: ${e.message}", e)
            0
        }
    }

    /**
     * Phase 3: Sync favorites metadata
     * Update metadata for favorited content
     * @return Number of favorites updated
     */
    private suspend fun syncFavorites(): Int {
        return try {
            // Update metadata for favorited movies/series
            // This would require access to FavoritesRepository
            // For now, return 0 (placeholder for future implementation)
            0
        } catch (e: Exception) {
            Log.e(TAG, "Favorites sync failed: ${e.message}", e)
            0
        }
    }

    /**
     * Cleanup ghost records in watchlist after content sync
     * EXTERNAL AUDIT FIX C1.2: Prevent crashes from orphaned watchlist entries
     *
     * Issue: When content removed from ContentDatabase (series/movie deleted from source),
     *        watchlist retains the ID → UI crashes when trying to display missing content
     * Solution: After sync, verify all watchlist IDs exist in ContentDatabase, delete orphans
     *
     * AUDIT FIX C5: Watchlist Wipe Protection (CRITICAL - DATA LOSS)
     * Issue: If ContentDatabase is empty (sync failure, bad asset copy, first launch),
     *        cleanupGhostRecords() would delete the ENTIRE user watchlist permanently
     * Solution: Check ContentDatabase size before cleanup - abort if appears empty/corrupted
     * Safety threshold: Skip cleanup if fewer than 50 movies or 10 series
     *
     * @return Number of ghost records removed
     */
    private suspend fun cleanupGhostRecords(): Int {
        var totalCleaned = 0

        try {
            // AUDIT FIX C5: Safety check - don't cleanup if ContentDatabase appears empty/corrupted
            val movieCount = contentDb.movieDao().getMovieCount()
            val seriesCount = contentDb.seriesDao().getSeriesCount()

            if (movieCount < 50 || seriesCount < 10) {
                Log.w(TAG, "Skipping ghost cleanup: ContentDatabase appears empty or incomplete (movies=$movieCount, series=$seriesCount)")
                return 0
            }

            // Cleanup watchlist movies
            val appDb = com.example.farsilandtv.data.database.AppDatabase.getDatabase(applicationContext)
            val watchlistMoviesList = appDb.watchlistMovieDao().getAllMovies().first()

            for (movie in watchlistMoviesList) {
                val exists = contentDb.movieDao().getMovieById(movie.id) != null
                if (!exists) {
                    Log.w(TAG, "Ghost record detected: Movie ID ${movie.id} missing from ContentDatabase")
                    appDb.watchlistMovieDao().deleteMovieById(movie.id)
                    totalCleaned++
                }
            }

            // Cleanup monitored series
            val monitoredSeriesList = appDb.monitoredSeriesDao().getAllSeries().first()

            for (series in monitoredSeriesList) {
                val exists = contentDb.seriesDao().getSeriesById(series.id) != null
                if (!exists) {
                    Log.w(TAG, "Ghost record detected: Series ID ${series.id} missing from ContentDatabase")
                    appDb.monitoredSeriesDao().deleteSeriesById(series.id)
                    totalCleaned++
                }
            }

            if (totalCleaned > 0) {
                Log.w(TAG, "Cleaned $totalCleaned ghost records from watchlist")
            } else {
                Log.d(TAG, "No ghost records found in watchlist")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning ghost records: ${e.message}", e)
        }

        return totalCleaned
    }

    /**
     * Check if user is actively watching content
     * Don't sync during playback to avoid interruptions
     * @return true if video is playing
     *
     * AUDIT #3 P3: Implement real detection using AudioManager
     * Previous: Hardcoded false, causing network contention during 4K streaming
     * Fixed: Use AudioManager.isMusicActive (covers video playback)
     */
    private fun isUserActivelyWatching(): Boolean {
        return try {
            val audioManager = applicationContext.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
            val isPlaying = audioManager?.isMusicActive ?: false

            if (isPlaying) {
                Log.d(TAG, "User is actively watching content - skipping sync to avoid buffering")
            }

            isPlaying
        } catch (e: Exception) {
            Log.w(TAG, "Error checking playback state: ${e.message}")
            false // Assume not watching if check fails
        }
    }

    /**
     * Sync movies added/updated since last sync
     * REFACTOR (2025-11-21): Always pass null on first sync to fetch recent items
     *
     * AUDIT FIX C4: Page 1 Sync Trap (CRITICAL - FUNCTIONAL)
     * Issue: Only fetched page 1 (20 items). If 21+ items updated between syncs,
     *        items beyond #20 were permanently skipped on next sync
     * Solution: Implement pagination loop to sync ALL modified items, not just first 20
     * Safety: Max 10 pages (200 items) to prevent infinite loops
     */
    private suspend fun syncMovies(lastSyncTimestamp: Long): Int {
        try {
            // Convert timestamp to API format
            // REFACTOR: First sync (lastSyncTimestamp == 0) should fetch recent items (no filter)
            val modifiedAfter = if (lastSyncTimestamp > 0) {
                timestampToApiDate(lastSyncTimestamp)
            } else {
                null // First sync - fetch recent items
            }

            Log.d(TAG, "Fetching movies modified after: $modifiedAfter")

            // AUDIT FIX C4: Implement pagination loop to sync ALL modified items
            var page = 1
            var totalSynced = 0
            do {
                // EXTERNAL AUDIT FIX SN-M3: Check cancellation at start of each pagination iteration
                // Issue: Pagination continues after WorkManager cancels, wasting resources
                // Fix: ensureActive() throws CancellationException if cancelled
                coroutineContext.ensureActive()

                Log.d(TAG, "Fetching movies page $page...")

                val wpMovies = wordPressApi.getMovies(
                    perPage = 20,
                    page = page,
                    modifiedAfter = modifiedAfter,
                    orderBy = "modified",
                    order = "desc"
                )

                Log.d(TAG, "API returned ${wpMovies.size} movies on page $page")

                val newMovies = wpMovies.map { it.toCachedMovie() }
                if (newMovies.isNotEmpty()) {
                    contentDb.movieDao().insertMovies(newMovies)
                    totalSynced += newMovies.size
                    Log.i(TAG, "✓ Synced ${newMovies.size} movies from page $page (total: $totalSynced)")
                }

                page++

                // Continue if we got a full page (indicates more pages may exist)
            } while (wpMovies.size >= 20 && page <= 10) // Safety: max 10 pages (200 items)

            Log.i(TAG, "✓ Movie sync complete: $totalSynced items across ${page - 1} pages")
            return totalSynced
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing movies: ${e.message}", e)
            return 0
        }
    }

    /**
     * Sync series added/updated since last sync
     * AUDIT FIX C2: Returns -1 on failure to prevent orphaned episodes
     *
     * AUDIT FIX C4: Page 1 Sync Trap (CRITICAL - FUNCTIONAL)
     * Issue: Only fetched page 1 (20 items). If 21+ items updated between syncs,
     *        items beyond #20 were permanently skipped on next sync
     * Solution: Implement pagination loop to sync ALL modified items, not just first 20
     * Safety: Max 10 pages (200 items) to prevent infinite loops
     */
    private suspend fun syncSeries(lastSyncTimestamp: Long): Int {
        try {
            // Convert timestamp to API format
            val modifiedAfter = if (lastSyncTimestamp > 0) {
                timestampToApiDate(lastSyncTimestamp)
            } else {
                null // First sync - fetch all
            }

            Log.d(TAG, "Fetching series modified after: $modifiedAfter")

            // AUDIT FIX C4: Implement pagination loop to sync ALL modified items
            var page = 1
            var totalSynced = 0
            do {
                // EXTERNAL AUDIT FIX SN-M3: Check cancellation at start of each pagination iteration
                // Issue: Pagination continues after WorkManager cancels, wasting resources
                // Fix: ensureActive() throws CancellationException if cancelled
                coroutineContext.ensureActive()

                Log.d(TAG, "Fetching series page $page...")

                val wpShows = wordPressApi.getTvShows(
                    perPage = 20,
                    page = page,
                    modifiedAfter = modifiedAfter,
                    orderBy = "modified",
                    order = "desc"
                )

                Log.d(TAG, "API returned ${wpShows.size} series on page $page")

                val newSeries = wpShows.map { it.toCachedSeries() }
                if (newSeries.isNotEmpty()) {
                    contentDb.seriesDao().insertMultipleSeries(newSeries)
                    totalSynced += newSeries.size
                    Log.i(TAG, "✓ Synced ${newSeries.size} series from page $page (total: $totalSynced)")
                }

                page++

                // Continue if we got a full page (indicates more pages may exist)
            } while (wpShows.size >= 20 && page <= 10) // Safety: max 10 pages (200 items)

            Log.i(TAG, "✓ Series sync complete: $totalSynced items across ${page - 1} pages")

            // EXTERNAL AUDIT FIX H2.2: Removed buildSeriesTitleCache() - use direct SQL queries instead

            return totalSynced
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing series: ${e.message}", e)
            // AUDIT FIX C2: Return -1 to signal failure (not 0 which means "0 new items")
            return -1
        }
    }

    /**
     * Build series title to ID cache for efficient episode linking
     * THREAD-SAFE: Uses immutable Map with atomic swap to prevent race conditions
     */
    // EXTERNAL AUDIT FIX H2.2: Removed buildSeriesTitleCache() function
    // Replaced with direct SQL queries in findSeriesIdByTitle() using getSeriesByTitle() DAO method

    /**
     * Sync episodes added/updated since last sync
     *
     * FIX: Added pagination to sync more than 20 episodes (same pattern as movies/series)
     * Previous: Only fetched 1 page (20 items) - missed episodes if 21+ updated between syncs
     * Now: Fetches up to 5 pages (100 items) to catch more updates
     */
    private suspend fun syncEpisodes(lastSyncTimestamp: Long): Int {
        try {
            // Convert timestamp to API format
            val modifiedAfter = if (lastSyncTimestamp > 0) {
                timestampToApiDate(lastSyncTimestamp)
            } else {
                null // First sync - fetch all
            }

            Log.d(TAG, "Fetching episodes modified after: $modifiedAfter")

            // FIX: Implement pagination loop to sync ALL modified episodes
            // Using 20 per page to avoid API timeout, max 5 pages (100 items)
            var page = 1
            var totalSynced = 0
            val allAffectedSeriesIds = mutableSetOf<Int>()

            do {
                // EXTERNAL AUDIT FIX SN-M3: Check cancellation at start of each pagination iteration
                // Issue: Pagination continues after WorkManager cancels, wasting resources
                // Fix: ensureActive() throws CancellationException if cancelled
                coroutineContext.ensureActive()

                Log.d(TAG, "Fetching episodes page $page...")

                val wpEpisodes = wordPressApi.getEpisodes(
                    perPage = 20,
                    page = page,
                    modifiedAfter = modifiedAfter,
                    orderBy = "modified",
                    order = "desc"
                )

                Log.d(TAG, "API returned ${wpEpisodes.size} episodes on page $page")

                val newEpisodes = wpEpisodes.map { it.toCachedEpisode() }

                if (newEpisodes.isNotEmpty()) {
                    contentDb.episodeDao().insertEpisodes(newEpisodes)
                    totalSynced += newEpisodes.size
                    Log.i(TAG, "✓ Synced ${newEpisodes.size} episodes from page $page (total: $totalSynced)")

                    // Collect affected series IDs for batch update
                    allAffectedSeriesIds.addAll(newEpisodes.mapNotNull { it.seriesId })
                }

                page++

                // Continue if we got a full page (indicates more pages may exist)
            } while (wpEpisodes.size >= 20 && page <= 5) // Safety: max 5 pages (100 items)

            // Update episode counts for all affected series (batch)
            if (allAffectedSeriesIds.isNotEmpty()) {
                updateSeriesEpisodeCounts(allAffectedSeriesIds.toList())
            }

            if (totalSynced > 0) {
                Log.i(TAG, "✓ Episode sync complete: $totalSynced items across ${page - 1} pages")
            } else {
                Log.d(TAG, "No new episodes to sync")
            }

            return totalSynced
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing episodes: ${e.message}", e)
            return 0
        }
    }

    /**
     * Parse WordPress date string to timestamp
     * Example: "2023-12-25T10:30:00" -> milliseconds since epoch
     * AUDIT FIX C5: Handles WordPress dates without timezone suffix
     */
    private fun parseDateToTimestamp(dateStr: String): Long {
        return try {
            // AUDIT FIX C5: WordPress returns local time without 'Z' suffix
            // Append 'Z' if not present to treat as UTC
            val normalizedDate = if (dateStr.matches(Regex("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}"))) {
                "${dateStr}Z"
            } else {
                dateStr
            }

            // Parse using ISO-8601 format
            val instant = java.time.Instant.parse(normalizedDate)
            instant.toEpochMilli()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse date: '$dateStr' -> normalized: '${dateStr}Z'", e)
            0L
        }
    }

    /**
     * Extract year from date string
     */
    private fun extractYear(dateStr: String): Int? {
        return try {
            dateStr.substring(0, 4).toIntOrNull()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Get the newest content timestamp from the database
     * Used to detect bundled DB and skip full sync on first run
     */
    private suspend fun getNewestContentTimestamp(): Long {
        return try {
            val newestMovie = contentDb.movieDao().getNewestMovieTimestamp() ?: 0L
            val newestSeries = contentDb.seriesDao().getNewestSeriesTimestamp() ?: 0L
            val newestEpisode = contentDb.episodeDao().getNewestEpisodeTimestamp() ?: 0L
            maxOf(newestMovie, newestSeries, newestEpisode)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting newest content timestamp: ${e.message}")
            0L
        }
    }

    /**
     * Update episode/season counts for series after syncing new episodes
     * This ensures series metadata stays in sync with actual episode data
     */
    private suspend fun updateSeriesEpisodeCounts(seriesIds: List<Int>) {
        if (seriesIds.isEmpty()) return

        try {
            for (seriesId in seriesIds) {
                // M4 FIX: Safe null handling for counts - default to 0 if null
                val episodeCount = contentDb.episodeDao().getEpisodeCountForSeries(seriesId) ?: 0
                val seasonCount = contentDb.episodeDao().getSeasonCount(seriesId) ?: 0

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

    /**
     * Convert WordPress API models to cached database entities
     * AUDIT FIX: Uses embedded media to eliminate N+1 network queries
     */
    private suspend fun com.example.farsilandtv.data.models.wordpress.WPMovie.toCachedMovie(): CachedMovie {
        val year = extractYear(this.date)
        val dateAdded = parseDateToTimestamp(this.date)
        val lastUpdatedTimestamp = parseDateToTimestamp(this.modified ?: this.date)

        // AUDIT FIX: Get poster URL from embedded media (no network call!)
        // Previously: wordPressApi.getMedia(this.featuredMedia).sourceUrl (N+1 query)
        // Now: Extract from _embedded field (included in main response)
        val posterUrl = this.embedded?.featuredMedia?.firstOrNull()?.sourceUrl

        // Extract description
        val description = this.content?.rendered?.let { org.jsoup.Jsoup.parse(it).text() }

        // Extract custom fields from ACF
        val rating = acf?.get("rating")?.toString()?.toFloatOrNull()
        val runtime = acf?.get("runtime")?.toString()?.toIntOrNull()
        val director = acf?.get("director")?.toString()
        val cast = acf?.get("cast")?.toString()

        // Extract genres from genre IDs
        val genreNames = if (this.genres.isNotEmpty() && genreCache != null) {
            this.genres.mapNotNull { genreId ->
                genreCache?.get(genreId)
            }.joinToString(", ")
        } else null

        return CachedMovie(
            id = this.id,
            title = this.title.rendered,
            posterUrl = posterUrl,
            farsilandUrl = this.link,
            description = description,
            year = year,
            rating = rating,
            runtime = runtime,
            director = director,
            cast = cast,
            genres = genreNames,
            dateAdded = dateAdded,
            lastUpdated = lastUpdatedTimestamp
        )
    }

    private suspend fun com.example.farsilandtv.data.models.wordpress.WPTvShow.toCachedSeries(): CachedSeries {
        val year = extractYear(this.date)
        val dateAdded = parseDateToTimestamp(this.date)
        val lastUpdatedTimestamp = parseDateToTimestamp(this.modified ?: this.date)

        // AUDIT FIX: Get poster URL from embedded media (no network call!)
        val posterUrl = this.embedded?.featuredMedia?.firstOrNull()?.sourceUrl

        val description = this.content?.rendered?.let { org.jsoup.Jsoup.parse(it).text() }

        val rating = acf?.get("rating")?.toString()?.toFloatOrNull()
        val totalSeasons = acf?.get("seasons")?.toString()?.toIntOrNull() ?: 0
        val cast = acf?.get("cast")?.toString()

        // Extract genres from genre IDs
        val genreNames = if (this.genres.isNotEmpty() && genreCache != null) {
            this.genres.mapNotNull { genreId ->
                genreCache?.get(genreId)
            }.joinToString(", ")
        } else null

        return CachedSeries(
            id = this.id,
            title = this.title.rendered,
            posterUrl = posterUrl,
            backdropUrl = null,
            farsilandUrl = this.link,
            description = description,
            year = year,
            rating = rating,
            totalSeasons = totalSeasons,
            totalEpisodes = 0, // Will be updated as episodes are synced
            cast = cast,
            genres = genreNames,
            dateAdded = dateAdded,
            lastUpdated = lastUpdatedTimestamp
        )
    }

    private suspend fun com.example.farsilandtv.data.models.wordpress.WPEpisode.toCachedEpisode(): CachedEpisode {
        val description = this.content?.rendered?.let { org.jsoup.Jsoup.parse(it).text() }

        // AUDIT FIX: Get thumbnail URL from embedded media (no network call!)
        val thumbnailUrl = this.embedded?.featuredMedia?.firstOrNull()?.sourceUrl

        // Try to extract from ACF first (if available)
        var seasonNum = acf?.get("season")?.toString()?.toIntOrNull() ?: 1
        var episodeNum = acf?.get("episode")?.toString()?.toIntOrNull() ?: 1

        // Extract series title from ACF
        var seriesTitle = acf?.get("series_title")?.toString()
            ?: acf?.get("show_name")?.toString()
            ?: acf?.get("show_title")?.toString()

        // FALLBACK: Parse from title if ACF is missing (common patterns)
        if (seriesTitle.isNullOrBlank()) {
            // Try to extract from title patterns like:
            // "Robate Salibi EP05" -> series: "Robate Salibi", episode: 5
            // "Eshghe Abadi SE02 EP45" -> series: "Eshghe Abadi", season: 2, episode: 45
            // "Karnaval EP18 Part 2" -> series: "Karnaval", episode: 18
            val titleText = this.title.rendered

            // Pattern for "Series Name SE## EP##" or "Series Name S#E##"
            val seasonEpisodePattern = Regex("(.+?)\\s+S[E]?(\\d+)\\s+EP?(\\d+)", RegexOption.IGNORE_CASE)
            // Pattern for "Series Name EP##"
            val episodeOnlyPattern = Regex("(.+?)\\s+EP?(\\d+)", RegexOption.IGNORE_CASE)

            // Use find() with null-safe let{} instead of matches() + find()!!
            val seasonMatch = seasonEpisodePattern.find(titleText)
            val episodeMatch = episodeOnlyPattern.find(titleText)

            when {
                seasonMatch != null -> {
                    seriesTitle = seasonMatch.groupValues.getOrNull(1)?.trim() ?: titleText
                    seasonNum = seasonMatch.groupValues.getOrNull(2)?.toIntOrNull() ?: 1
                    episodeNum = seasonMatch.groupValues.getOrNull(3)?.toIntOrNull() ?: 1
                }
                episodeMatch != null -> {
                    seriesTitle = episodeMatch.groupValues.getOrNull(1)?.trim() ?: titleText
                    episodeNum = episodeMatch.groupValues.getOrNull(2)?.toIntOrNull() ?: 1
                }
                else -> {
                    // Can't parse - use full title as series name
                    seriesTitle = titleText
                }
            }

            if (seriesTitle != titleText) {
                Log.d(TAG, "Episode ${this.id}: Extracted '$seriesTitle' S${seasonNum}E${episodeNum} from title '$titleText'")
            }
        }

        // Link episode to series by matching series title
        val seriesId = if (!seriesTitle.isNullOrBlank()) {
            findSeriesIdByTitle(seriesTitle)
        } else {
            Log.w(TAG, "Episode ${this.id} has no series title (ACF missing, title parsing failed)")
            0
        }

        val lastUpdatedTimestamp = parseDateToTimestamp(this.modified ?: this.date)

        return CachedEpisode(
            seriesId = seriesId,
            seriesTitle = seriesTitle,
            episodeId = this.id,
            season = seasonNum,
            episode = episodeNum,
            title = this.title.rendered,
            description = description,
            thumbnailUrl = thumbnailUrl,
            farsilandUrl = this.link,
            airDate = this.date.take(10),
            runtime = null,
            dateAdded = parseDateToTimestamp(this.date),
            lastUpdated = lastUpdatedTimestamp
        )
    }

    /**
     * Find series ID by matching title
     * EXTERNAL AUDIT FIX H2.2: Direct SQL query instead of in-memory cache
     * Issue: Loading all series into HashMap caused GC pauses during background sync
     * Solution: Use indexed SQL query (O(log N) with SQLite b-tree index on title)
     */
    private suspend fun findSeriesIdByTitle(seriesTitle: String): Int {
        return try {
            // Try exact match first (case-insensitive via SQL LOWER())
            contentDb.seriesDao().getSeriesByTitle(seriesTitle)?.id?.let { return it }

            // Try normalized title match
            val normalizedTitle = normalizeSeriesTitle(seriesTitle)
            if (normalizedTitle.isNotBlank() && normalizedTitle != seriesTitle) {
                contentDb.seriesDao().getSeriesByTitle(normalizedTitle)?.id?.let { return it }
            }

            // No match found
            Log.d(TAG, "No series match found for '$seriesTitle'")
            0
        } catch (e: Exception) {
            Log.e(TAG, "Error finding series ID for '$seriesTitle': ${e.message}", e)
            0
        }
    }

    /**
     * Normalize series title for fuzzy matching
     * Removes common variations like "سریال", "Season X", etc.
     */
    private fun normalizeSeriesTitle(title: String): String {
        return title
            .replace("سریال", "", ignoreCase = true)  // Remove "Series" in Persian
            .replace("فصل", "", ignoreCase = true)     // Remove "Season" in Persian
            .replace(Regex("Season \\d+", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s+"), " ")  // Normalize whitespace
            .trim()
    }

    companion object {
        private const val TAG = "ContentSyncWorker"
        const val WORK_NAME = "content_sync_work"
        private const val MAX_RETRY_ATTEMPTS = 5
        private const val NOTIFICATION_ID = 1001  // Unique ID for sync notification
    }
}
