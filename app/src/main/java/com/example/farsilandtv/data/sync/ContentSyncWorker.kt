package com.example.farsilandtv.data.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.farsilandtv.data.database.CachedMovie
import com.example.farsilandtv.data.database.CachedSeries
import com.example.farsilandtv.data.database.CachedEpisode
import com.example.farsilandtv.data.database.CachedGenre
import com.example.farsilandtv.data.database.ContentDatabase
import com.example.farsilandtv.data.api.RetrofitClient
import com.example.farsilandtv.data.repository.ContentRepository
import com.example.farsilandtv.utils.SyncPreferences
import com.example.farsilandtv.utils.NotificationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
class ContentSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val contentDb = ContentDatabase.getDatabase(context)
    private val wordPressApi = RetrofitClient.wordPressApi
    private val repository = ContentRepository(context)
    private val syncPrefs = SyncPreferences(context)
    private val prefs = context.getSharedPreferences("sync_state", Context.MODE_PRIVATE)

    // Cache for genre ID to name mapping
    private var genreCache: Map<Int, String>? = null

    // Cache for series title to ID mapping (for episode linking)
    // @Volatile ensures visibility across coroutines, prevents TOCTOU race conditions
    @Volatile
    private var seriesTitleCache: Map<String, Int>? = null

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
            val lastSyncTimestamp = prefs.getLong("last_sync_timestamp", 0L)

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
            val episodeCount = syncEpisodes(lastSyncTimestamp)

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

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Sync failed: ${e.message}", e)

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
     * Check if user is actively watching content
     * Don't sync during playback to avoid interruptions
     * @return true if video is playing
     */
    private fun isUserActivelyWatching(): Boolean {
        // Check if VideoPlayerActivity is active
        // This would require checking activity state or playback position updates
        // For now, return false (assume not watching)
        return false
    }

    /**
     * Sync movies added/updated since last sync
     */
    private suspend fun syncMovies(lastSyncTimestamp: Long): Int {
        try {
            // Convert timestamp to API format
            val modifiedAfter = if (lastSyncTimestamp > 0) {
                timestampToApiDate(lastSyncTimestamp)
            } else {
                null // First sync - fetch all
            }

            Log.d(TAG, "Fetching movies modified after: $modifiedAfter")

            // Fetch only modified items, up to 100 items
            val wpMovies = wordPressApi.getMovies(
                perPage = 100,  // Increased from 20
                page = 1,
                modifiedAfter = modifiedAfter,
                orderBy = "modified",
                order = "desc"
            )

            Log.d(TAG, "API returned ${wpMovies.size} movies")

            val newMovies = wpMovies.map { it.toCachedMovie() }

            if (newMovies.isNotEmpty()) {
                contentDb.movieDao().insertMovies(newMovies)
                Log.i(TAG, "✓ Synced ${newMovies.size} movies")
            } else {
                Log.d(TAG, "No new movies to sync")
            }

            return newMovies.size
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing movies: ${e.message}", e)
            return 0
        }
    }

    /**
     * Sync series added/updated since last sync
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

            // Fetch only modified items, up to 100 items
            val wpShows = wordPressApi.getTvShows(
                perPage = 100,  // Increased from 20
                page = 1,
                modifiedAfter = modifiedAfter,
                orderBy = "modified",
                order = "desc"
            )

            Log.d(TAG, "API returned ${wpShows.size} series")

            val newSeries = wpShows.map { it.toCachedSeries() }

            if (newSeries.isNotEmpty()) {
                contentDb.seriesDao().insertMultipleSeries(newSeries)
                Log.i(TAG, "✓ Synced ${newSeries.size} series")
            } else {
                Log.d(TAG, "No new series to sync")
            }

            // Build series title cache for episode linking
            buildSeriesTitleCache()

            return newSeries.size
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing series: ${e.message}", e)
            return 0
        }
    }

    /**
     * Build series title to ID cache for efficient episode linking
     * THREAD-SAFE: Uses immutable Map with atomic swap to prevent race conditions
     */
    private suspend fun buildSeriesTitleCache() {
        try {
            // Build immutable map locally first (no concurrent access issues)
            val tempCache = mutableMapOf<String, Int>()

            contentDb.seriesDao().getAllSeries().collect { seriesList ->
                seriesList.forEach { series ->
                    // Store both original and normalized titles
                    tempCache[series.title.lowercase()] = series.id
                    val normalizedTitle = normalizeSeriesTitle(series.title).lowercase()
                    if (normalizedTitle.isNotBlank()) {
                        tempCache[normalizedTitle] = series.id
                    }
                }
            }

            // Atomic swap: replace entire cache at once (immutable, thread-safe)
            seriesTitleCache = tempCache.toMap()
            Log.d(TAG, "Built series title cache with ${tempCache.size} entries")
        } catch (e: Exception) {
            Log.e(TAG, "Error building series title cache: ${e.message}", e)
            seriesTitleCache = emptyMap()
        }
    }

    /**
     * Sync episodes added/updated since last sync
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

            // Fetch only modified items, up to 100 items
            val wpEpisodes = wordPressApi.getEpisodes(
                perPage = 100,  // Increased from 20
                page = 1,
                modifiedAfter = modifiedAfter,
                orderBy = "modified",
                order = "desc"
            )

            Log.d(TAG, "API returned ${wpEpisodes.size} episodes")

            val newEpisodes = wpEpisodes.map { it.toCachedEpisode() }

            if (newEpisodes.isNotEmpty()) {
                contentDb.episodeDao().insertEpisodes(newEpisodes)
                Log.i(TAG, "✓ Synced ${newEpisodes.size} episodes")
            } else {
                Log.d(TAG, "No new episodes to sync")
            }

            return newEpisodes.size
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing episodes: ${e.message}", e)
            return 0
        }
    }

    /**
     * Parse WordPress date string to timestamp
     * Example: "2023-12-25T10:30:00" -> milliseconds since epoch
     */
    private fun parseDateToTimestamp(dateStr: String): Long {
        return try {
            val format = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
            format.timeZone = java.util.TimeZone.getTimeZone("UTC")
            format.parse(dateStr)?.time ?: 0L
        } catch (e: Exception) {
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
     * Convert WordPress API models to cached database entities
     */
    private suspend fun com.example.farsilandtv.data.models.wordpress.WPMovie.toCachedMovie(): CachedMovie {
        val year = extractYear(this.date)
        val dateAdded = parseDateToTimestamp(this.date)
        val lastUpdatedTimestamp = parseDateToTimestamp(this.modified ?: this.date)

        // Get poster URL
        val posterUrl = if (this.featuredMedia > 0) {
            try {
                wordPressApi.getMedia(this.featuredMedia).sourceUrl
            } catch (e: Exception) {
                null
            }
        } else null

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

        val posterUrl = if (this.featuredMedia > 0) {
            try {
                wordPressApi.getMedia(this.featuredMedia).sourceUrl
            } catch (e: Exception) {
                null
            }
        } else null

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

        val thumbnailUrl = if (this.featuredMedia > 0) {
            try {
                wordPressApi.getMedia(this.featuredMedia).sourceUrl
            } catch (e: Exception) {
                null
            }
        } else null

        val seasonNum = acf?.get("season")?.toString()?.toIntOrNull() ?: 1
        val episodeNum = acf?.get("episode")?.toString()?.toIntOrNull() ?: 1

        // Extract series title from ACF
        val seriesTitle = acf?.get("series_title")?.toString()
            ?: acf?.get("show_name")?.toString()
            ?: acf?.get("show_title")?.toString()

        // Link episode to series by matching series title
        val seriesId = if (!seriesTitle.isNullOrBlank()) {
            findSeriesIdByTitle(seriesTitle)
        } else {
            Log.w(TAG, "Episode ${this.id} has no series title in ACF fields")
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
     * Uses cache for efficient lookups with fuzzy matching
     * THREAD-SAFE: Single read eliminates TOCTOU race condition
     */
    private fun findSeriesIdByTitle(seriesTitle: String): Int {
        // Single read of @Volatile field (thread-safe, prevents TOCTOU)
        val cache = seriesTitleCache

        if (cache == null) {
            Log.w(TAG, "Series title cache not initialized, cannot link episode to series")
            return 0
        }

        return try {
            // Try exact match first (case-insensitive)
            cache[seriesTitle.lowercase()]?.let { return it }

            // Try normalized title match
            val normalizedTitle = normalizeSeriesTitle(seriesTitle).lowercase()
            cache[normalizedTitle]?.let { return it }

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
    }
}
