package com.example.farsilandtv.data.database

import androidx.paging.PagingSource
import androidx.room.*
import com.example.farsilandtv.utils.SqlSanitizer
import kotlinx.coroutines.flow.Flow

/**
 * DAOs for querying pre-populated content catalog
 */

@Dao
interface CachedMovieDao {
    @Query("SELECT * FROM cached_movies ORDER BY dateAdded DESC")
    fun getAllMovies(): Flow<List<CachedMovie>>

    @Query("SELECT * FROM cached_movies ORDER BY lastUpdated DESC LIMIT :limit")
    fun getRecentMovies(limit: Int = 20): Flow<List<CachedMovie>>

    // SECURITY: Use ESCAPE '\\' clause to prevent SQL injection via LIKE wildcards
    // Callers MUST sanitize input with SqlSanitizer.sanitizeLikePattern() before passing urlPattern
    // OPTIMIZATION: Uses lastUpdated (website publish date) instead of dateAdded (scrape date) for correct chronological order
    @Query("SELECT * FROM cached_movies WHERE farsilandUrl LIKE :urlPattern ESCAPE '\\' ORDER BY lastUpdated DESC LIMIT :limit")
    fun getRecentMoviesFiltered(urlPattern: String, limit: Int = 20): Flow<List<CachedMovie>>

    // Feature #18: Paging 3 for unlimited scrolling (replaces 300-item caps)
    @Query("SELECT * FROM cached_movies ORDER BY dateAdded DESC")
    fun getMoviesPaged(): PagingSource<Int, CachedMovie>

    // SECURITY: Use ESCAPE '\\' clause to prevent SQL injection via LIKE wildcards
    // Callers MUST sanitize input with SqlSanitizer.sanitizeLikePattern() before passing urlPattern
    // OPTIMIZATION: Uses lastUpdated (website publish date) instead of dateAdded (scrape date) for correct chronological order
    @Query("SELECT * FROM cached_movies WHERE farsilandUrl LIKE :urlPattern ESCAPE '\\' ORDER BY lastUpdated DESC")
    fun getMoviesPagedFiltered(urlPattern: String): PagingSource<Int, CachedMovie>

    @Query("SELECT * FROM cached_movies WHERE id = :movieId")
    suspend fun getMovieById(movieId: Int): CachedMovie?

    @Query("SELECT * FROM cached_movies WHERE farsilandUrl = :url")
    suspend fun getMovieByUrl(url: String): CachedMovie?

    // SECURITY: Use ESCAPE '\\' clause to prevent SQL injection via LIKE wildcards
    // Callers MUST sanitize input with SqlSanitizer.sanitizeLikePattern() before passing genre
    @Query("SELECT * FROM cached_movies WHERE genres LIKE '%' || :genre || '%' ESCAPE '\\' ORDER BY dateAdded DESC")
    fun getMoviesByGenre(genre: String): Flow<List<CachedMovie>>

    // AUDIT FIX C1.2: Use FTS4 for fast full-text search (no leading wildcard)
    // FTS4 provides orders of magnitude faster search than LIKE '%query%'
    // Query is automatically sanitized by FTS4 tokenizer
    // @SkipQueryVerification: FTS4 virtual table is created via migration, not Room entity
    @androidx.room.SkipQueryVerification
    @Query("""
        SELECT m.* FROM cached_movies m
        INNER JOIN cached_movies_fts fts ON m.id = fts.docid
        WHERE cached_movies_fts MATCH :query
        ORDER BY m.dateAdded DESC
    """)
    fun searchMovies(query: String): Flow<List<CachedMovie>>

    @Query("SELECT COUNT(*) FROM cached_movies")
    suspend fun getMovieCount(): Int

    @Query("SELECT * FROM cached_movies WHERE lastUpdated > :timestamp")
    suspend fun getMoviesUpdatedAfter(timestamp: Long): List<CachedMovie>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMovie(movie: CachedMovie)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMovies(movies: List<CachedMovie>)

    @Update
    suspend fun updateMovie(movie: CachedMovie)

    @Delete
    suspend fun deleteMovie(movie: CachedMovie)

    @Query("DELETE FROM cached_movies")
    suspend fun deleteAllMovies()
}

@Dao
interface CachedSeriesDao {
    @Query("SELECT * FROM cached_series ORDER BY dateAdded DESC")
    fun getAllSeries(): Flow<List<CachedSeries>>

    @Query("SELECT * FROM cached_series ORDER BY lastUpdated DESC LIMIT :limit")
    fun getRecentSeries(limit: Int = 20): Flow<List<CachedSeries>>

    // SECURITY: Use ESCAPE '\\' clause to prevent SQL injection via LIKE wildcards
    // Callers MUST sanitize input with SqlSanitizer.sanitizeLikePattern() before passing urlPattern
    // OPTIMIZATION: Uses lastUpdated (website publish date) instead of dateAdded (scrape date) for correct chronological order
    @Query("SELECT * FROM cached_series WHERE farsilandUrl LIKE :urlPattern ESCAPE '\\' ORDER BY lastUpdated DESC LIMIT :limit")
    fun getRecentSeriesFiltered(urlPattern: String, limit: Int = 20): Flow<List<CachedSeries>>

    // Feature #18: Paging 3 for unlimited scrolling (replaces 300-item caps)
    @Query("SELECT * FROM cached_series ORDER BY dateAdded DESC")
    fun getSeriesPaged(): PagingSource<Int, CachedSeries>

    // SECURITY: Use ESCAPE '\\' clause to prevent SQL injection via LIKE wildcards
    // Callers MUST sanitize input with SqlSanitizer.sanitizeLikePattern() before passing urlPattern
    // OPTIMIZATION: Uses lastUpdated (website publish date) instead of dateAdded (scrape date) for correct chronological order
    @Query("SELECT * FROM cached_series WHERE farsilandUrl LIKE :urlPattern ESCAPE '\\' ORDER BY lastUpdated DESC")
    fun getSeriesPagedFiltered(urlPattern: String): PagingSource<Int, CachedSeries>

    @Query("SELECT * FROM cached_series WHERE id = :seriesId")
    suspend fun getSeriesById(seriesId: Int): CachedSeries?

    @Query("SELECT * FROM cached_series WHERE farsilandUrl = :url")
    suspend fun getSeriesByUrl(url: String): CachedSeries?

    // SECURITY: Use ESCAPE '\\' clause to prevent SQL injection via LIKE wildcards
    // Callers MUST sanitize input with SqlSanitizer.sanitizeLikePattern() before passing genre
    @Query("SELECT * FROM cached_series WHERE genres LIKE '%' || :genre || '%' ESCAPE '\\' ORDER BY dateAdded DESC")
    fun getSeriesByGenre(genre: String): Flow<List<CachedSeries>>

    // AUDIT FIX C1.2: Use FTS4 for fast full-text search (no leading wildcard)
    // FTS4 provides orders of magnitude faster search than LIKE '%query%'
    // Query is automatically sanitized by FTS4 tokenizer
    // @SkipQueryVerification: FTS4 virtual table is created via migration, not Room entity
    @androidx.room.SkipQueryVerification
    @Query("""
        SELECT s.* FROM cached_series s
        INNER JOIN cached_series_fts fts ON s.id = fts.docid
        WHERE cached_series_fts MATCH :query
        ORDER BY s.dateAdded DESC
    """)
    fun searchSeries(query: String): Flow<List<CachedSeries>>

    @Query("SELECT COUNT(*) FROM cached_series")
    suspend fun getSeriesCount(): Int

    @Query("SELECT * FROM cached_series WHERE lastUpdated > :timestamp")
    suspend fun getSeriesUpdatedAfter(timestamp: Long): List<CachedSeries>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSeries(series: CachedSeries)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMultipleSeries(series: List<CachedSeries>)

    @Update
    suspend fun updateSeries(series: CachedSeries)

    @Delete
    suspend fun deleteSeries(series: CachedSeries)

    @Query("DELETE FROM cached_series")
    suspend fun deleteAllSeries()
}

@Dao
interface CachedEpisodeDao {
    @Query("SELECT * FROM cached_episodes WHERE seriesId = :seriesId ORDER BY season, episode")
    fun getEpisodesForSeries(seriesId: Int): Flow<List<CachedEpisode>>

    @Query("SELECT * FROM cached_episodes WHERE seriesId = :seriesId AND season = :season ORDER BY episode")
    fun getEpisodesForSeason(seriesId: Int, season: Int): Flow<List<CachedEpisode>>

    @Query("SELECT * FROM cached_episodes ORDER BY lastUpdated DESC LIMIT :limit")
    fun getRecentEpisodes(limit: Int = 20): Flow<List<CachedEpisode>>

    // SECURITY: Use ESCAPE '\\' clause to prevent SQL injection via LIKE wildcards
    // Callers MUST sanitize input with SqlSanitizer.sanitizeLikePattern() before passing urlPattern
    @Query("SELECT * FROM cached_episodes WHERE farsilandUrl LIKE :urlPattern ESCAPE '\\' ORDER BY lastUpdated DESC LIMIT :limit")
    fun getRecentEpisodesFiltered(urlPattern: String, limit: Int = 20): Flow<List<CachedEpisode>>

    // Feature #18: Paging 3 for unlimited scrolling (replaces 300-item caps)
    @Query("SELECT * FROM cached_episodes ORDER BY dateAdded DESC")
    fun getEpisodesPaged(): PagingSource<Int, CachedEpisode>

    // SECURITY: Use ESCAPE '\\' clause to prevent SQL injection via LIKE wildcards
    // Callers MUST sanitize input with SqlSanitizer.sanitizeLikePattern() before passing urlPattern
    @Query("SELECT * FROM cached_episodes WHERE farsilandUrl LIKE :urlPattern ESCAPE '\\' ORDER BY lastUpdated DESC")
    fun getEpisodesPagedFiltered(urlPattern: String): PagingSource<Int, CachedEpisode>

    @Query("SELECT * FROM cached_episodes WHERE id = :id")
    suspend fun getEpisodeById(id: Long): CachedEpisode?

    @Query("SELECT * FROM cached_episodes WHERE episodeId = :episodeId")
    suspend fun getEpisodeByEpisodeId(episodeId: Int): CachedEpisode?

    @Query("SELECT * FROM cached_episodes WHERE farsilandUrl = :url")
    suspend fun getEpisodeByUrl(url: String): CachedEpisode?

    @Query("SELECT * FROM cached_episodes WHERE seriesId = :seriesId AND season = :season AND episode = :episode")
    suspend fun getSpecificEpisode(seriesId: Int, season: Int, episode: Int): CachedEpisode?

    // AUDIT FIX C1.2: Use FTS4 for fast full-text search (no leading wildcard)
    // FTS4 provides orders of magnitude faster search than LIKE '%query%'
    // Query is automatically sanitized by FTS4 tokenizer
    // @SkipQueryVerification: FTS4 virtual table is created via migration, not Room entity
    @androidx.room.SkipQueryVerification
    @Query("""
        SELECT e.* FROM cached_episodes e
        INNER JOIN cached_episodes_fts fts ON e.id = fts.docid
        WHERE cached_episodes_fts MATCH :query
        ORDER BY e.dateAdded DESC
    """)
    fun searchEpisodes(query: String): Flow<List<CachedEpisode>>

    @Query("SELECT COUNT(*) FROM cached_episodes")
    suspend fun getEpisodeCount(): Int

    @Query("SELECT COUNT(DISTINCT season) FROM cached_episodes WHERE seriesId = :seriesId")
    suspend fun getSeasonCount(seriesId: Int): Int

    @Query("SELECT * FROM cached_episodes WHERE lastUpdated > :timestamp")
    suspend fun getEpisodesUpdatedAfter(timestamp: Long): List<CachedEpisode>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEpisode(episode: CachedEpisode)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEpisodes(episodes: List<CachedEpisode>)

    @Update
    suspend fun updateEpisode(episode: CachedEpisode)

    @Delete
    suspend fun deleteEpisode(episode: CachedEpisode)

    @Query("DELETE FROM cached_episodes WHERE seriesId = :seriesId")
    suspend fun deleteEpisodesForSeries(seriesId: Int)

    @Query("DELETE FROM cached_episodes")
    suspend fun deleteAllEpisodes()
}

@Dao
interface CachedGenreDao {
    @Query("SELECT * FROM cached_genres ORDER BY name")
    fun getAllGenres(): Flow<List<CachedGenre>>

    @Query("SELECT * FROM cached_genres WHERE id = :genreId")
    suspend fun getGenreById(genreId: Int): CachedGenre?

    @Query("SELECT * FROM cached_genres WHERE slug = :slug")
    suspend fun getGenreBySlug(slug: String): CachedGenre?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGenre(genre: CachedGenre)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGenres(genres: List<CachedGenre>)

    @Delete
    suspend fun deleteGenre(genre: CachedGenre)

    @Query("DELETE FROM cached_genres")
    suspend fun deleteAllGenres()
}

@Dao
interface CachedVideoUrlDao {
    @Query("SELECT * FROM cached_video_urls WHERE contentId = :contentId AND contentType = :contentType")
    suspend fun getVideoUrlsForContent(contentId: Int, contentType: String): List<CachedVideoUrl>

    @Query("SELECT * FROM cached_video_urls WHERE contentId = :contentId AND contentType = :contentType AND quality = :quality")
    suspend fun getVideoUrl(contentId: Int, contentType: String, quality: String): CachedVideoUrl?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVideoUrl(videoUrl: CachedVideoUrl)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVideoUrls(videoUrls: List<CachedVideoUrl>)

    @Delete
    suspend fun deleteVideoUrl(videoUrl: CachedVideoUrl)

    @Query("DELETE FROM cached_video_urls WHERE contentId = :contentId AND contentType = :contentType")
    suspend fun deleteVideoUrlsForContent(contentId: Int, contentType: String)

    @Query("DELETE FROM cached_video_urls")
    suspend fun deleteAllVideoUrls()
}
