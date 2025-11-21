package com.example.farsilandtv.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index
import androidx.room.Fts4
import androidx.room.ColumnInfo

/**
 * Room entities for pre-populated content catalog
 * Separate from user watchlist/progress (AppDatabase)
 *
 * Data source: FarsiFlow PostgreSQL → SQLite conversion
 */

/**
 * Cached movie entity
 * Maps from FarsiFlow catalog_items WHERE item_type='movie'
 */
@Entity(
    tableName = "cached_movies",
    indices = [
        Index(value = ["farsilandUrl"], unique = true),
        Index(value = ["dateAdded"]) // M7: Performance for ORDER BY dateAdded DESC queries
    ]
)
data class CachedMovie(
    @PrimaryKey
    val id: Int,  // FarsiFlow catalog_items.id
    val title: String,
    val posterUrl: String?,
    val farsilandUrl: String,
    val description: String?,
    val year: Int?,
    val rating: Float?,
    val runtime: Int?,  // minutes
    val director: String?,
    val cast: String?,  // Comma-separated
    val genres: String?,  // Comma-separated
    val dateAdded: Long,  // Timestamp millis
    val lastUpdated: Long  // For incremental sync
)

/**
 * Cached series entity
 * Maps from FarsiFlow catalog_items WHERE item_type='series'
 */
@Entity(
    tableName = "cached_series",
    indices = [
        Index(value = ["farsilandUrl"], unique = true),
        Index(value = ["dateAdded"]) // M7: Performance for ORDER BY dateAdded DESC queries
    ]
)
data class CachedSeries(
    @PrimaryKey
    val id: Int,  // FarsiFlow catalog_items.id
    val title: String,
    val posterUrl: String?,
    val backdropUrl: String?,
    val farsilandUrl: String,
    val description: String?,
    val year: Int?,
    val rating: Float?,
    val totalSeasons: Int,
    val totalEpisodes: Int,
    val cast: String?,  // Comma-separated
    val genres: String?,  // Comma-separated
    val dateAdded: Long,  // Timestamp millis
    val lastUpdated: Long  // For incremental sync
)

/**
 * Cached episode entity
 * Maps from FarsiFlow catalog_items WHERE item_type='episode'
 */
@Entity(
    tableName = "cached_episodes",
    indices = [
        Index(value = ["seriesId", "season", "episode"], unique = true),
        Index(value = ["farsilandUrl"], unique = true)
    ]
)
data class CachedEpisode(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val seriesId: Int,  // References CachedSeries.id
    val seriesTitle: String?,
    val episodeId: Int,  // FarsiFlow catalog_items.id for this episode
    val season: Int,
    val episode: Int,  // Note: Fractional episodes stored as * 10 (e.g., 14.5 → 145)
    val title: String,
    val description: String?,
    val thumbnailUrl: String?,
    val farsilandUrl: String,
    val airDate: String?,  // YYYY-MM-DD format
    val runtime: Int?,  // minutes
    val dateAdded: Long,  // Timestamp millis
    val lastUpdated: Long  // For incremental sync
)

/**
 * Cached genre entity
 * For browsing by genre
 */
@Entity(tableName = "cached_genres")
data class CachedGenre(
    @PrimaryKey
    val id: Int,
    val name: String,
    val slug: String
)

/**
 * Optional: Cached video URLs from FarsiFlow
 * Pre-populate MP4 URLs to skip scraping
 */
@Entity(
    tableName = "cached_video_urls",
    indices = [Index(value = ["contentId", "contentType", "quality"], unique = true)]
)
data class CachedVideoUrl(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val contentId: Int,  // Movie or Episode ID
    val contentType: String,  // "movie" or "episode"
    val quality: String,  // "480p", "720p", "1080p", "4K"
    val mp4Url: String,
    val fileSizeMB: Float?,
    val cachedAt: Long  // Timestamp millis
)

/**
 * AUDIT FIX (FTS4): FTS4 Virtual Entity for Movies
 * Enables full-text search on movie titles
 *
 * Linked to CachedMovie via contentEntity.
 * Room automatically manages sync between the two tables.
 *
 * Usage in DAO:
 * ```sql
 * SELECT m.* FROM cached_movies m
 * JOIN cached_movies_fts fts ON m.id = fts.rowid
 * WHERE cached_movies_fts MATCH :query
 * ```
 */
@Entity(tableName = "cached_movies_fts")
@Fts4(contentEntity = CachedMovie::class)
data class CachedMovieFts(
    @ColumnInfo(name = "rowid")
    @PrimaryKey
    val rowId: Long,
    val title: String
)

/**
 * AUDIT FIX (FTS4): FTS4 Virtual Entity for Series
 * Enables full-text search on series titles
 */
@Entity(tableName = "cached_series_fts")
@Fts4(contentEntity = CachedSeries::class)
data class CachedSeriesFts(
    @ColumnInfo(name = "rowid")
    @PrimaryKey
    val rowId: Long,
    val title: String
)

/**
 * AUDIT FIX (FTS4): FTS4 Virtual Entity for Episodes
 * Enables full-text search on episode titles and series titles
 */
@Entity(tableName = "cached_episodes_fts")
@Fts4(contentEntity = CachedEpisode::class)
data class CachedEpisodeFts(
    @ColumnInfo(name = "rowid")
    @PrimaryKey
    val rowId: Long,
    val seriesTitle: String?,
    val title: String
)
