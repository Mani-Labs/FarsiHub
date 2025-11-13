package com.example.farsilandtv.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * DAO for Watchlist Movies
 */
@Dao
interface WatchlistMovieDao {
    // Watchlist queries (manually bookmarked only)
    @Query("SELECT * FROM watchlist_movies WHERE isInWatchlist = 1 ORDER BY dateAdded DESC")
    fun getAllMovies(): Flow<List<WatchlistMovie>>

    @Query("SELECT * FROM watchlist_movies WHERE isInWatchlist = 1 AND isCompleted = 0 ORDER BY lastWatched DESC")
    fun getWatchlistInProgress(): Flow<List<WatchlistMovie>>

    @Query("SELECT * FROM watchlist_movies WHERE isInWatchlist = 1 AND isCompleted = 1 ORDER BY lastWatched DESC")
    fun getCompletedMovies(): Flow<List<WatchlistMovie>>

    // Continue watching (any movie with progress, regardless of watchlist status)
    @Query("SELECT * FROM watchlist_movies WHERE isCompleted = 0 AND playbackPosition > 0 ORDER BY lastWatched DESC")
    fun getInProgressMovies(): Flow<List<WatchlistMovie>>

    @Query("SELECT * FROM watchlist_movies WHERE id = :movieId")
    suspend fun getMovie(movieId: Int): WatchlistMovie?

    @Query("SELECT * FROM watchlist_movies WHERE id = :movieId")
    fun getMovieFlow(movieId: Int): Flow<WatchlistMovie?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMovie(movie: WatchlistMovie)

    @Update
    suspend fun updateMovie(movie: WatchlistMovie)

    @Delete
    suspend fun deleteMovie(movie: WatchlistMovie)

    @Query("DELETE FROM watchlist_movies WHERE id = :movieId")
    suspend fun deleteMovieById(movieId: Int)

    @Query("UPDATE watchlist_movies SET playbackPosition = :position, lastWatched = :timestamp WHERE id = :movieId")
    suspend fun updatePlaybackPosition(movieId: Int, position: Long, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE watchlist_movies SET playbackPosition = :position, totalDuration = :duration, lastWatched = :timestamp WHERE id = :movieId")
    suspend fun updateProgress(movieId: Int, position: Long, duration: Long, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE watchlist_movies SET isCompleted = 1, lastWatched = :timestamp WHERE id = :movieId")
    suspend fun markAsCompleted(movieId: Int, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE watchlist_movies SET isCompleted = 0, playbackPosition = 0 WHERE id = :movieId")
    suspend fun markAsUnwatched(movieId: Int)

    @Query("UPDATE watchlist_movies SET isInWatchlist = 1 WHERE id = :movieId")
    suspend fun addToWatchlist(movieId: Int)

    @Query("UPDATE watchlist_movies SET isInWatchlist = 0 WHERE id = :movieId")
    suspend fun removeFromWatchlist(movieId: Int)

    @Query("SELECT isInWatchlist FROM watchlist_movies WHERE id = :movieId")
    suspend fun isInWatchlist(movieId: Int): Boolean?
}

/**
 * DAO for Monitored Series
 */
@Dao
interface MonitoredSeriesDao {
    @Query("SELECT * FROM monitored_series ORDER BY lastWatched DESC")
    fun getAllSeries(): Flow<List<MonitoredSeries>>

    @Query("SELECT * FROM monitored_series WHERE id = :seriesId")
    suspend fun getSeries(seriesId: Int): MonitoredSeries?

    @Query("SELECT * FROM monitored_series WHERE id = :seriesId")
    fun getSeriesFlow(seriesId: Int): Flow<MonitoredSeries?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSeries(series: MonitoredSeries)

    @Update
    suspend fun updateSeries(series: MonitoredSeries)

    @Delete
    suspend fun deleteSeries(series: MonitoredSeries)

    @Query("DELETE FROM monitored_series WHERE id = :seriesId")
    suspend fun deleteSeriesById(seriesId: Int)

    @Query("UPDATE monitored_series SET lastWatched = :timestamp WHERE id = :seriesId")
    suspend fun updateLastWatched(seriesId: Int, timestamp: Long = System.currentTimeMillis())
}

/**
 * DAO for Episode Progress
 */
@Dao
interface EpisodeProgressDao {
    @Query("SELECT * FROM episode_progress WHERE seriesId = :seriesId ORDER BY season ASC, episode ASC")
    fun getEpisodesForSeries(seriesId: Int): Flow<List<EpisodeProgress>>

    @Query("SELECT * FROM episode_progress WHERE seriesId = :seriesId AND season = :season ORDER BY episode ASC")
    fun getEpisodesForSeason(seriesId: Int, season: Int): Flow<List<EpisodeProgress>>

    @Query("SELECT * FROM episode_progress WHERE seriesId = :seriesId AND isCompleted = 0 AND playbackPosition > 0 ORDER BY lastWatched DESC LIMIT 1")
    suspend fun getLastWatchedEpisode(seriesId: Int): EpisodeProgress?

    @Query("SELECT * FROM episode_progress WHERE seriesId = :seriesId AND isCompleted = 0 ORDER BY season ASC, episode ASC LIMIT 1")
    suspend fun getNextUnwatchedEpisode(seriesId: Int): EpisodeProgress?

    @Query("SELECT * FROM episode_progress WHERE episodeId = :episodeId")
    suspend fun getEpisodeProgress(episodeId: Int): EpisodeProgress?

    @Query("SELECT * FROM episode_progress WHERE episodeId = :episodeId")
    fun getEpisodeProgressFlow(episodeId: Int): Flow<EpisodeProgress?>

    @Query("SELECT * FROM episode_progress WHERE isCompleted = 0 AND playbackPosition > 0 ORDER BY lastWatched DESC")
    fun getAllInProgressEpisodes(): Flow<List<EpisodeProgress>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEpisode(episode: EpisodeProgress)

    @Update
    suspend fun updateEpisode(episode: EpisodeProgress)

    @Delete
    suspend fun deleteEpisode(episode: EpisodeProgress)

    @Query("DELETE FROM episode_progress WHERE seriesId = :seriesId")
    suspend fun deleteAllEpisodesForSeries(seriesId: Int)

    @Query("UPDATE episode_progress SET playbackPosition = :position, lastWatched = :timestamp WHERE episodeId = :episodeId")
    suspend fun updatePlaybackPosition(episodeId: Int, position: Long, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE episode_progress SET playbackPosition = :position, totalDuration = :duration, lastWatched = :timestamp WHERE episodeId = :episodeId")
    suspend fun updateProgress(episodeId: Int, position: Long, duration: Long, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE episode_progress SET isCompleted = 1, lastWatched = :timestamp WHERE episodeId = :episodeId")
    suspend fun markAsCompleted(episodeId: Int, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE episode_progress SET isCompleted = 0, playbackPosition = 0 WHERE episodeId = :episodeId")
    suspend fun markAsUnwatched(episodeId: Int)

    @Query("UPDATE episode_progress SET isCompleted = 1 WHERE seriesId = :seriesId")
    suspend fun markAllAsCompleted(seriesId: Int)

    @Query("SELECT COUNT(*) FROM episode_progress WHERE seriesId = :seriesId")
    suspend fun getTotalEpisodeCount(seriesId: Int): Int

    @Query("SELECT COUNT(*) FROM episode_progress WHERE seriesId = :seriesId AND isCompleted = 1")
    suspend fun getCompletedEpisodeCount(seriesId: Int): Int

    @Query("SELECT COUNT(*) FROM episode_progress WHERE seriesId = :seriesId AND season = :season")
    suspend fun getSeasonEpisodeCount(seriesId: Int, season: Int): Int

    @Query("SELECT COUNT(*) FROM episode_progress WHERE seriesId = :seriesId AND season = :season AND isCompleted = 1")
    suspend fun getSeasonCompletedCount(seriesId: Int, season: Int): Int
}
