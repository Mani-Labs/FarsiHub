package com.example.farsilandtv.data.repository

import android.content.Context
import android.util.Log
import androidx.room.withTransaction
import com.example.farsilandtv.data.database.*
import com.example.farsilandtv.data.models.Episode
import com.example.farsilandtv.data.models.Movie
import com.example.farsilandtv.data.models.Series
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/**
 * Repository for watchlist and progress tracking
 * Handles all watchlist operations and playback progress
 */
class WatchlistRepository(context: Context) {

    private val database = AppDatabase.getDatabase(context)
    private val movieDao = database.watchlistMovieDao()
    private val seriesDao = database.monitoredSeriesDao()
    private val episodeDao = database.episodeProgressDao()

    companion object {
        private const val TAG = "WatchlistRepository"

        // P0 FIX: Issue #2 - Unified completion threshold (matches PlaybackRepository)
        // Changed from 0.90f (90%) to 0.95f (95%) to eliminate dual source of truth
        // This prevents UI contradictions where watchlist shows "Completed" but continue watching shows "Resume"
        private const val COMPLETION_THRESHOLD = 0.95f
    }

    // ========== Movie Watchlist (Manual Bookmarks) ==========

    /**
     * Get all bookmarked movies (manually added to watchlist)
     */
    fun getAllWatchlistedMovies(): Flow<List<WatchlistMovie>> {
        return movieDao.getAllMovies()
    }

    /**
     * Get bookmarked movies in progress
     */
    fun getWatchlistInProgress(): Flow<List<WatchlistMovie>> {
        return movieDao.getWatchlistInProgress()
    }

    /**
     * Get completed bookmarked movies
     */
    fun getCompletedMovies(): Flow<List<WatchlistMovie>> {
        return movieDao.getCompletedMovies()
    }

    /**
     * Check if movie is in watchlist (bookmarked)
     */
    suspend fun isMovieInWatchlist(movieId: Int): Boolean {
        return movieDao.isInWatchlist(movieId) == true
    }

    /**
     * Get movie data (whether bookmarked or just tracked)
     */
    suspend fun getWatchlistMovie(movieId: Int): WatchlistMovie? {
        return movieDao.getMovie(movieId)
    }

    /**
     * Add movie to watchlist (bookmark it)
     * Creates entry if doesn't exist, or updates existing
     */
    suspend fun addMovieToWatchlist(movie: Movie) {
        val existing = movieDao.getMovie(movie.id)
        if (existing == null) {
            // Create new entry with watchlist flag
            val watchlistMovie = WatchlistMovie(
                id = movie.id,
                title = movie.title,
                posterUrl = movie.posterUrl,
                farsilandUrl = movie.farsilandUrl,
                dateAdded = System.currentTimeMillis(),
                isInWatchlist = true
            )
            movieDao.insertMovie(watchlistMovie)
        } else {
            // Just mark existing entry as bookmarked
            movieDao.addToWatchlist(movie.id)
        }
    }

    /**
     * Remove movie from watchlist (unbookmark it)
     * Keeps progress data, just removes bookmark flag
     */
    suspend fun removeMovieFromWatchlist(movieId: Int) {
        movieDao.removeFromWatchlist(movieId)
    }

    /**
     * Update movie playback progress
     * Auto-tracks content for continue watching (does NOT add to watchlist)
     * Uses database transaction for atomic updates (prevents race conditions)
     */
    suspend fun updateMovieProgress(movieId: Int, position: Long, duration: Long, title: String = "Unknown", farsilandUrl: String = "", posterUrl: String? = null) {
        // Use database.withTransaction for proper transaction handling with suspend functions
        // @Transaction only works on DAO methods, not Repository methods
        database.withTransaction {
            // P0 FIX: Issue #2 - Use unified COMPLETION_THRESHOLD (95%, was 90%)
            val isCompleted = duration > 0 && (position.toFloat() / duration) >= COMPLETION_THRESHOLD

            val existing = movieDao.getMovie(movieId)
            if (existing == null) {
                // Auto-track movie for continue watching (NOT in watchlist)
                val newMovie = WatchlistMovie(
                    id = movieId,
                    title = title,
                    posterUrl = posterUrl,
                    farsilandUrl = farsilandUrl,
                    dateAdded = System.currentTimeMillis(),
                    lastWatched = System.currentTimeMillis(),
                    playbackPosition = position,
                    totalDuration = duration,
                    isCompleted = isCompleted,
                    isInWatchlist = false // Not bookmarked, just tracking
                )
                movieDao.insertMovie(newMovie)
            } else {
                movieDao.updateProgress(movieId, position, duration)
                if (isCompleted) {
                    movieDao.markAsCompleted(movieId)
                }
            }
        }
    }

    /**
     * Mark movie as watched
     */
    suspend fun markMovieAsWatched(movieId: Int) {
        movieDao.markAsCompleted(movieId)
    }

    /**
     * Mark movie as unwatched
     */
    suspend fun markMovieAsUnwatched(movieId: Int) {
        movieDao.markAsUnwatched(movieId)
    }

    // ========== Series Monitoring ==========

    /**
     * Get all monitored series
     */
    fun getAllMonitoredSeries(): Flow<List<MonitoredSeries>> {
        return seriesDao.getAllSeries()
    }

    /**
     * Check if series is monitored
     */
    suspend fun isSeriesMonitored(seriesId: Int): Boolean {
        return seriesDao.getSeries(seriesId) != null
    }

    /**
     * Get monitored series
     */
    suspend fun getMonitoredSeries(seriesId: Int): MonitoredSeries? {
        return seriesDao.getSeries(seriesId)
    }

    /**
     * Add series to monitored list
     */
    suspend fun addSeriesToMonitored(series: Series) {
        val monitoredSeries = MonitoredSeries(
            id = series.id,
            title = series.title,
            posterUrl = series.posterUrl,
            backdropUrl = series.backdropUrl,
            farsilandUrl = series.farsilandUrl,
            totalSeasons = series.totalSeasons,
            dateAdded = System.currentTimeMillis(),
            lastWatched = System.currentTimeMillis()
        )
        seriesDao.insertSeries(monitoredSeries)
    }

    /**
     * Remove series from monitored list
     */
    suspend fun removeSeriesFromMonitored(seriesId: Int) {
        seriesDao.deleteSeriesById(seriesId)
    }

    // ========== Episode Progress ==========

    /**
     * Get all episodes for a series
     */
    fun getEpisodesForSeries(seriesId: Int): Flow<List<EpisodeProgress>> {
        return episodeDao.getEpisodesForSeries(seriesId)
    }

    /**
     * Get episodes for a season
     */
    fun getEpisodesForSeason(seriesId: Int, season: Int): Flow<List<EpisodeProgress>> {
        return episodeDao.getEpisodesForSeason(seriesId, season)
    }

    /**
     * Get episode progress
     */
    suspend fun getEpisodeProgress(episodeId: Int): EpisodeProgress? {
        return episodeDao.getEpisodeProgress(episodeId)
    }

    /**
     * Track episode (add to database if not exists)
     */
    suspend fun trackEpisode(episode: Episode, seriesId: Int) {
        val existing = episodeDao.getEpisodeProgress(episode.id)
        if (existing == null) {
            val progress = EpisodeProgress(
                seriesId = seriesId,
                episodeId = episode.id,
                season = episode.season,
                episode = episode.episode,
                episodeTitle = episode.title,
                thumbnailUrl = episode.thumbnailUrl,
                farsilandUrl = episode.farsilandUrl,
                lastWatched = System.currentTimeMillis()
            )
            episodeDao.insertEpisode(progress)
        }
    }

    /**
     * Update episode playback progress
     * Auto-adds to tracking if not already tracked
     * Uses database transaction for atomic updates (prevents race conditions)
     */
    suspend fun updateEpisodeProgress(
        episodeId: Int,
        position: Long,
        duration: Long,
        seriesId: Int = 0,
        season: Int = 0,
        episodeNumber: Int = 0,
        episodeTitle: String = "Unknown",
        farsilandUrl: String = "",
        thumbnailUrl: String? = null
    ) {
        // Use database.withTransaction for proper transaction handling with suspend functions
        // @Transaction only works on DAO methods, not Repository methods
        database.withTransaction {
            // P0 FIX: Issue #2 - Use unified COMPLETION_THRESHOLD (95%, was 90%)
            val isCompleted = duration > 0 && (position.toFloat() / duration) >= COMPLETION_THRESHOLD

            val existing = episodeDao.getEpisodeProgress(episodeId)
            if (existing == null) {
                // Auto-add episode when first played
                val newEpisode = EpisodeProgress(
                    seriesId = seriesId,
                    episodeId = episodeId,
                    season = season,
                    episode = episodeNumber,
                    episodeTitle = episodeTitle,
                    thumbnailUrl = thumbnailUrl,
                    farsilandUrl = farsilandUrl,
                    playbackPosition = position,
                    totalDuration = duration,
                    isCompleted = isCompleted,
                    lastWatched = System.currentTimeMillis()
                )
                episodeDao.insertEpisode(newEpisode)
            } else {
                episodeDao.updateProgress(episodeId, position, duration)
                if (isCompleted) {
                    episodeDao.markAsCompleted(episodeId)
                }
            }
        }
    }

    /**
     * Mark episode as watched
     */
    suspend fun markEpisodeAsWatched(episodeId: Int) {
        episodeDao.markAsCompleted(episodeId)
    }

    /**
     * Mark episode as unwatched
     */
    suspend fun markEpisodeAsUnwatched(episodeId: Int) {
        episodeDao.markAsUnwatched(episodeId)
    }

    /**
     * Mark all episodes in series as watched
     */
    suspend fun markAllEpisodesAsWatched(seriesId: Int) {
        episodeDao.markAllAsCompleted(seriesId)
    }

    /**
     * Get next unwatched episode for series
     */
    suspend fun getNextUnwatchedEpisode(seriesId: Int): EpisodeProgress? {
        return episodeDao.getNextUnwatchedEpisode(seriesId)
    }

    /**
     * Get last watched episode for series
     */
    suspend fun getLastWatchedEpisode(seriesId: Int): EpisodeProgress? {
        return episodeDao.getLastWatchedEpisode(seriesId)
    }

    /**
     * Get series progress (watched/total)
     */
    suspend fun getSeriesProgress(seriesId: Int): Pair<Int, Int> {
        val completed = episodeDao.getCompletedEpisodeCount(seriesId)
        val total = episodeDao.getTotalEpisodeCount(seriesId)
        return Pair(completed, total)
    }

    /**
     * Get season progress (watched/total)
     */
    suspend fun getSeasonProgress(seriesId: Int, season: Int): Pair<Int, Int> {
        val completed = episodeDao.getSeasonCompletedCount(seriesId, season)
        val total = episodeDao.getSeasonEpisodeCount(seriesId, season)
        return Pair(completed, total)
    }

    // ========== Continue Watching ==========

    /**
     * P1 FIX: Issue #5 - Remove movie from continue watching without deleting bookmarks
     * Previous code: deleteMovieById() deleted entire row → user lost bookmark!
     * Fixed: Check if movie is bookmarked, if so reset progress only, else delete
     */
    suspend fun removeMovieFromContinueWatching(movieId: Int) {
        val movie = movieDao.getMovie(movieId)
        if (movie != null && movie.isInWatchlist) {
            // Movie is bookmarked - keep it but reset progress
            Log.d(TAG, "Movie $movieId is bookmarked, resetting progress only (preserving bookmark)")
            // Reset progress fields but keep bookmark
            val updated = movie.copy(
                playbackPosition = 0,
                lastWatched = null,
                isCompleted = false
            )
            movieDao.insertMovie(updated)
        } else {
            // Not bookmarked - safe to delete entirely
            Log.d(TAG, "Movie $movieId not bookmarked, deleting from continue watching")
            movieDao.deleteMovieById(movieId)
        }
    }

    /**
     * Remove episode from continue watching (deletes progress)
     */
    suspend fun removeEpisodeFromContinueWatching(episodeId: Int) {
        val episode = episodeDao.getEpisodeProgress(episodeId)
        if (episode != null) {
            episodeDao.deleteEpisode(episode)
        }
    }

    /**
     * Get continue watching items (movies + episodes in progress)
     */
    fun getContinueWatching(): Flow<List<ContinueWatchingItem>> {
        val moviesFlow = movieDao.getInProgressMovies()
        val episodesFlow = episodeDao.getAllInProgressEpisodes()

        return combine(moviesFlow, episodesFlow) { movies, episodes ->
            val items = mutableListOf<ContinueWatchingItem>()

            // Add movies
            movies.forEach { movie ->
                items.add(
                    ContinueWatchingItem(
                        id = "movie-${movie.id}",
                        contentType = ContinueWatchingItem.ContentType.MOVIE,
                        title = movie.title,
                        subtitle = null,
                        posterUrl = movie.posterUrl,
                        farsilandUrl = movie.farsilandUrl,
                        playbackPosition = movie.playbackPosition,
                        totalDuration = movie.totalDuration,
                        lastWatched = movie.lastWatched ?: 0
                    )
                )
            }

            // Add episodes
            episodes.forEach { episode ->
                items.add(
                    ContinueWatchingItem(
                        id = "episode-${episode.episodeId}",
                        contentType = ContinueWatchingItem.ContentType.EPISODE,
                        title = episode.episodeTitle,
                        subtitle = episode.formattedNumber,
                        posterUrl = episode.thumbnailUrl,
                        farsilandUrl = episode.farsilandUrl,
                        playbackPosition = episode.playbackPosition,
                        totalDuration = episode.totalDuration,
                        lastWatched = episode.lastWatched,
                        seriesId = episode.seriesId,
                        episodeId = episode.episodeId,
                        season = episode.season,
                        episodeNumber = episode.episode
                    )
                )
            }

            // Sort by last watched (most recent first)
            items.sortedByDescending { it.lastWatched }.take(10)
        }
    }
}
