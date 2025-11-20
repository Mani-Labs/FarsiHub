package com.example.farsilandtv.data.api

import com.example.farsilandtv.data.models.wordpress.*
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Retrofit service for Farsiland.com WordPress REST API
 * Base URL: https://farsiland.com/wp-json/wp/v2/
 *
 * All endpoints are PUBLIC - no authentication required!
 */
interface WordPressApiService {

    /**
     * Get list of movies
     * Endpoint: GET /wp-json/wp/v2/movies
     *
     * @param perPage Number of items per page (default: 20, max: 100)
     * @param page Page number (starts from 1)
     * @param modifiedAfter Only fetch items modified after this date (ISO 8601 format: "2025-11-12T10:30:00")
     * @param orderBy Sort by: date, title, modified, id (default: date)
     * @param order Sort order: asc, desc (default: desc)
     * @param embed Include embedded resources (media, author, etc.) - AUDIT FIX: Eliminates N+1 queries
     */
    @GET("movies")
    suspend fun getMovies(
        @Query("per_page") perPage: Int = 20,
        @Query("page") page: Int = 1,
        @Query("modified_after") modifiedAfter: String? = null,
        @Query("orderby") orderBy: String? = null,
        @Query("order") order: String? = null,
        @Query("_embed") embed: Boolean = true
    ): List<WPMovie>

    /**
     * Get single movie by ID
     * Endpoint: GET /wp-json/wp/v2/movies/{id}
     */
    @GET("movies/{id}")
    suspend fun getMovie(@Path("id") id: Int): WPMovie

    /**
     * Get list of TV shows
     * Endpoint: GET /wp-json/wp/v2/tvshows
     *
     * @param perPage Number of items per page (default: 20, max: 100)
     * @param page Page number (starts from 1)
     * @param modifiedAfter Only fetch items modified after this date (ISO 8601 format: "2025-11-12T10:30:00")
     * @param orderBy Sort by: date, title, modified, id
     * @param order Sort order: asc, desc
     * @param embed Include embedded resources (media, author, etc.) - AUDIT FIX: Eliminates N+1 queries
     */
    @GET("tvshows")
    suspend fun getTvShows(
        @Query("per_page") perPage: Int = 20,
        @Query("page") page: Int = 1,
        @Query("modified_after") modifiedAfter: String? = null,
        @Query("orderby") orderBy: String? = null,
        @Query("order") order: String? = null,
        @Query("_embed") embed: Boolean = true
    ): List<WPTvShow>

    /**
     * Get single TV show by ID
     * Endpoint: GET /wp-json/wp/v2/tvshows/{id}
     */
    @GET("tvshows/{id}")
    suspend fun getTvShow(@Path("id") id: Int): WPTvShow

    /**
     * Get list of episodes
     * Endpoint: GET /wp-json/wp/v2/episodes
     *
     * @param perPage Number of items per page (default: 20, max: 100)
     * @param page Page number (starts from 1)
     * @param modifiedAfter Only fetch items modified after this date (ISO 8601 format: "2025-11-12T10:30:00")
     * @param orderBy Sort by: date, title, modified, id
     * @param order Sort order: asc, desc
     * @param embed Include embedded resources (media, author, etc.) - AUDIT FIX: Eliminates N+1 queries
     *
     * Note: WordPress API may not link episodes to series directly.
     * We may need to scrape episode lists from series page HTML.
     */
    @GET("episodes")
    suspend fun getEpisodes(
        @Query("per_page") perPage: Int = 20,
        @Query("page") page: Int = 1,
        @Query("modified_after") modifiedAfter: String? = null,
        @Query("orderby") orderBy: String? = null,
        @Query("order") order: String? = null,
        @Query("_embed") embed: Boolean = true
    ): List<WPEpisode>

    /**
     * Get single episode by ID
     * Endpoint: GET /wp-json/wp/v2/episodes/{id}
     */
    @GET("episodes/{id}")
    suspend fun getEpisode(@Path("id") id: Int): WPEpisode

    /**
     * Get media (image) by ID
     * Endpoint: GET /wp-json/wp/v2/media/{id}
     *
     * Used to get poster/backdrop images
     */
    @GET("media/{id}")
    suspend fun getMedia(@Path("id") id: Int): WPMedia

    /**
     * Get list of genres
     * Endpoint: GET /wp-json/wp/v2/genres
     */
    @GET("genres")
    suspend fun getGenres(
        @Query("per_page") perPage: Int = 100
    ): List<WPGenre>

    /**
     * Search movies and TV shows
     * Endpoint: GET /wp-json/wp/v2/movies?search={query}
     *
     * @param query Search query (Persian or English)
     * @param perPage Number of results
     */
    @GET("movies")
    suspend fun searchMovies(
        @Query("search") query: String,
        @Query("per_page") perPage: Int = 20,
        @Query("page") page: Int = 1
    ): List<WPMovie>

    @GET("tvshows")
    suspend fun searchTvShows(
        @Query("search") query: String,
        @Query("per_page") perPage: Int = 20,
        @Query("page") page: Int = 1
    ): List<WPTvShow>

    /**
     * Get movies by genre
     * Endpoint: GET /wp-json/wp/v2/movies?genres={genreId}
     */
    @GET("movies")
    suspend fun getMoviesByGenre(
        @Query("genres") genreId: Int,
        @Query("per_page") perPage: Int = 20,
        @Query("page") page: Int = 1
    ): List<WPMovie>

    @GET("tvshows")
    suspend fun getTvShowsByGenre(
        @Query("genres") genreId: Int,
        @Query("per_page") perPage: Int = 20,
        @Query("page") page: Int = 1
    ): List<WPTvShow>
}
