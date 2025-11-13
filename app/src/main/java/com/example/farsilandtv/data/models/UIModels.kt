package com.example.farsilandtv.data.models

import com.example.farsilandtv.utils.EpisodeFormatter
import java.io.Serializable

/**
 * UI Models - Simplified data models for display
 * These are converted from WordPress API models in the Repository layer
 */

/**
 * Movie model for UI display
 */
data class Movie(
    val id: Int,
    val title: String,
    val description: String = "",
    val posterUrl: String? = null,
    val backdropUrl: String? = null,
    val farsilandUrl: String, // URL to movie page for scraping video URLs
    val year: Int? = null,
    val rating: Float? = null,
    val runtime: Int? = null, // In minutes
    val genres: List<String> = emptyList(),
    val director: String? = null,
    val cast: List<String> = emptyList(),
    val dateAdded: Long = 0 // Timestamp when added to site
) : Serializable {
    /**
     * Check if content is new (added within last 14 days)
     */
    val isNew: Boolean
        get() {
            if (dateAdded == 0L) return false
            val fourteenDaysAgo = System.currentTimeMillis() - (14 * 24 * 60 * 60 * 1000L)
            return dateAdded > fourteenDaysAgo
        }
}

/**
 * TV Series model for UI display
 */
data class Series(
    val id: Int,
    val title: String,
    val description: String = "",
    val posterUrl: String? = null,
    val backdropUrl: String? = null,
    val farsilandUrl: String, // URL to series page for scraping episodes
    val year: Int? = null,
    val rating: Float? = null,
    val totalSeasons: Int = 0,
    val totalEpisodes: Int = 0,
    val status: String? = null, // "Ongoing" or "Ended"
    val genres: List<String> = emptyList(),
    val network: String? = null,
    val cast: List<String> = emptyList(),
    val dateAdded: Long = 0 // Timestamp when added to site
) : Serializable {
    /**
     * Check if content is new (added within last 14 days)
     */
    val isNew: Boolean
        get() {
            if (dateAdded == 0L) return false
            val fourteenDaysAgo = System.currentTimeMillis() - (14 * 24 * 60 * 60 * 1000L)
            return dateAdded > fourteenDaysAgo
        }
}

/**
 * Episode model for UI display
 */
data class Episode(
    val id: Int,
    val seriesId: Int? = null,
    val seriesTitle: String? = null, // Series/show name
    val title: String,
    val description: String = "",
    val thumbnailUrl: String? = null,
    val farsilandUrl: String, // URL to episode page for scraping video URLs
    val season: Int,
    val episode: Int,
    val airDate: String? = null,
    val runtime: Int? = null, // In minutes

    // Enhanced metadata from episode pages
    val episodePosterUrl: String? = null, // Episode-specific poster (different from series poster)
    val persianTitle: String? = null, // Persian/Farsi title from H1
    val englishTitle: String? = null, // English title from H2
    val releaseDate: String? = null, // Episode release date
    val rating: Float? = null, // Episode rating (0-10)
    val voteCount: Int? = null, // Number of votes
    val quality: String? = null, // Quality badge (HD, SD, 4K)

    // Watch progress
    var isWatched: Boolean = false,
    var playbackPosition: Long = 0, // In milliseconds
    var totalDuration: Long = 0 // In milliseconds
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
        get() = playbackPosition > 0 && !isWatched

    /**
     * Check if episode is new (aired within last 7 days)
     */
    val isNew: Boolean
        get() {
            if (airDate.isNullOrEmpty()) return false
            return try {
                val airDateTime = java.time.LocalDate.parse(airDate, java.time.format.DateTimeFormatter.ISO_DATE)
                val daysSinceAir = java.time.temporal.ChronoUnit.DAYS.between(airDateTime, java.time.LocalDate.now())
                daysSinceAir in 0..7
            } catch (e: Exception) {
                false
            }
        }
}

/**
 * Season model for grouping episodes
 */
data class Season(
    val number: Int,
    val title: String = EpisodeFormatter.formatSeasonNumber(number),
    val episodes: List<Episode> = emptyList(),
    val posterUrl: String? = null
) {
    /**
     * Returns watched/total episode count
     */
    val watchedCount: Int
        get() = episodes.count { it.isWatched }

    /**
     * Returns total episode count
     */
    val totalCount: Int
        get() = episodes.size

    /**
     * Returns next unwatched episode
     */
    val nextUnwatched: Episode?
        get() = episodes.firstOrNull { !it.isWatched }
}

/**
 * Genre model
 */
data class Genre(
    val id: Int,
    val name: String,
    val slug: String
)

/**
 * Continue watching item (can be movie or episode)
 */
sealed class ContinueWatchingItem {
    abstract val id: Int
    abstract val title: String
    abstract val posterUrl: String?
    abstract val progressPercentage: Int

    data class MovieItem(
        override val id: Int,
        override val title: String,
        override val posterUrl: String?,
        override val progressPercentage: Int,
        val movie: Movie,
        val playbackPosition: Long,
        val totalDuration: Long
    ) : ContinueWatchingItem()

    data class EpisodeItem(
        override val id: Int,
        override val title: String, // Series title + episode number
        override val posterUrl: String?,
        override val progressPercentage: Int,
        val series: Series,
        val episode: Episode,
        val playbackPosition: Long,
        val totalDuration: Long
    ) : ContinueWatchingItem()
}

/**
 * Featured content item for carousel (can be movie or series)
 * Used for the featured content carousel on home screen
 */
sealed class FeaturedContent : Serializable {
    abstract val id: Int
    abstract val title: String
    abstract val description: String
    abstract val posterUrl: String?
    abstract val backdropUrl: String?
    abstract val farsilandUrl: String
    abstract val contentType: String // "movie" or "series"

    data class FeaturedMovie(
        override val id: Int,
        override val title: String,
        override val description: String,
        override val posterUrl: String?,
        override val backdropUrl: String?,
        override val farsilandUrl: String,
        val movie: Movie
    ) : FeaturedContent() {
        override val contentType: String = "movie"
    }

    data class FeaturedSeries(
        override val id: Int,
        override val title: String,
        override val description: String,
        override val posterUrl: String?,
        override val backdropUrl: String?,
        override val farsilandUrl: String,
        val series: Series
    ) : FeaturedContent() {
        override val contentType: String = "series"
    }
}
