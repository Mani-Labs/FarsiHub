package com.example.farsilandtv.data.database

import androidx.room.*
import com.example.farsilandtv.utils.EpisodeFormatter

/**
 * Movie with playback progress and optional watchlist status
 * Can track progress without being in watchlist (for continue watching)
 */
@Entity(
    tableName = "watchlist_movies",
    indices = [
        Index(value = ["isInWatchlist"]), // M7: Filter bookmarked movies
        Index(value = ["isCompleted"]), // M7: Filter completed movies
        Index(value = ["lastWatched"]), // M7: Order by recently watched
        Index(value = ["isInWatchlist", "lastWatched"]), // M7: Continue watching bookmarked
        Index(value = ["isCompleted", "lastWatched"]) // M7: Continue watching in-progress
    ]
)
data class WatchlistMovie(
    @PrimaryKey val id: Int,
    val title: String,
    val posterUrl: String?,
    val farsilandUrl: String,
    val dateAdded: Long = System.currentTimeMillis(),
    val lastWatched: Long? = null,
    val playbackPosition: Long = 0, // milliseconds
    val totalDuration: Long = 0, // milliseconds
    val isCompleted: Boolean = false,
    val isInWatchlist: Boolean = false // true = manually bookmarked, false = just tracking progress
) {
    /**
     * Returns watch progress percentage (0-100)
     */
    val progressPercentage: Int
        get() = if (totalDuration > 0) {
            ((playbackPosition.toFloat() / totalDuration) * 100).toInt()
        } else 0

    /**
     * Returns if movie is in progress (started but not finished)
     */
    val isInProgress: Boolean
        get() = playbackPosition > 0 && !isCompleted
}

/**
 * Monitored TV series with tracking
 */
@Entity(
    tableName = "monitored_series",
    indices = [
        Index(value = ["lastWatched"]) // M7: Order by recently watched series
    ]
)
data class MonitoredSeries(
    @PrimaryKey val id: Int,
    val title: String,
    val posterUrl: String?,
    val backdropUrl: String?,
    val farsilandUrl: String,
    val totalSeasons: Int = 0,
    val dateAdded: Long = System.currentTimeMillis(),
    val lastWatched: Long = System.currentTimeMillis()
)

/**
 * Episode progress tracking (independent of monitored status)
 * Can track any episode for continue watching without requiring series to be monitored
 */
@Entity(
    tableName = "episode_progress",
    indices = [
        Index(value = ["seriesId"]),
        Index(value = ["seriesId", "isCompleted"]), // BUG #36: Composite index for fast filtering
        Index(value = ["episodeId"], unique = true) // M7: Ensure data integrity, no duplicate episodes
    ]
)
data class EpisodeProgress(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val seriesId: Int,
    val episodeId: Int, // WordPress post ID
    val season: Int,
    val episode: Int,
    val episodeTitle: String,
    val thumbnailUrl: String?,
    val farsilandUrl: String,
    val playbackPosition: Long = 0, // milliseconds
    val totalDuration: Long = 0, // milliseconds
    val isCompleted: Boolean = false,
    val lastWatched: Long = System.currentTimeMillis()
) {
    /**
     * Returns formatted episode number (e.g., "S01E05")
     * Uses EpisodeFormatter utility for consistent formatting throughout the app
     */
    val formattedNumber: String
        get() = EpisodeFormatter.formatEpisodeNumber(season, episode)

    /**
     * Returns watch progress percentage (0-100)
     */
    val progressPercentage: Int
        get() = if (totalDuration > 0) {
            ((playbackPosition.toFloat() / totalDuration) * 100).toInt()
        } else 0

    /**
     * Returns if episode is in progress (started but not finished)
     */
    val isInProgress: Boolean
        get() = playbackPosition > 0 && !isCompleted
}

/**
 * Continue watching item - union of movies and episodes
 */
data class ContinueWatchingItem(
    val id: String, // Unique ID (movie-{id} or episode-{id})
    val contentType: ContentType,
    val title: String,
    val subtitle: String?, // Episode number for series
    val posterUrl: String?,
    val farsilandUrl: String,
    val playbackPosition: Long,
    val totalDuration: Long,
    val lastWatched: Long,
    val seriesId: Int? = null, // For episodes
    val episodeId: Int? = null, // For episodes
    val season: Int? = null,
    val episodeNumber: Int? = null
) {
    enum class ContentType {
        MOVIE, EPISODE
    }

    val progressPercentage: Int
        get() = if (totalDuration > 0) {
            ((playbackPosition.toFloat() / totalDuration) * 100).toInt()
        } else 0
}
