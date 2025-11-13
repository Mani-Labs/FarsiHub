package com.example.farsilandtv.data.repository

import android.content.Context
import com.example.farsilandtv.data.database.AppDatabase
import com.example.farsilandtv.data.database.Favorite
import com.example.farsilandtv.data.database.FavoriteDao
import com.example.farsilandtv.data.models.Movie
import com.example.farsilandtv.data.models.Series
import kotlinx.coroutines.flow.Flow

/**
 * Repository for universal favorites system
 * Handles favorites for both movies and TV series
 */
class FavoritesRepository(context: Context) {

    private val database = AppDatabase.getDatabase(context)
    private val favoriteDao: FavoriteDao = database.favoriteDao()

    // ========== Add/Remove Favorites ==========

    /**
     * Add movie to favorites
     */
    suspend fun addMovieToFavorites(movie: Movie) {
        val favorite = Favorite(
            contentId = "movie-${movie.id}",
            contentType = Favorite.ContentType.MOVIE,
            title = movie.title,
            posterUrl = movie.posterUrl,
            addedAt = System.currentTimeMillis()
        )
        favoriteDao.insert(favorite)
    }

    /**
     * Add series to favorites
     */
    suspend fun addSeriesToFavorites(series: Series) {
        val favorite = Favorite(
            contentId = "series-${series.id}",
            contentType = Favorite.ContentType.SERIES,
            title = series.title,
            posterUrl = series.posterUrl,
            addedAt = System.currentTimeMillis()
        )
        favoriteDao.insert(favorite)
    }

    /**
     * Add favorite with content ID and type
     * Generic method for when you have minimal data
     */
    suspend fun addFavorite(contentId: String, contentType: Favorite.ContentType, title: String, posterUrl: String?) {
        val favorite = Favorite(
            contentId = contentId,
            contentType = contentType,
            title = title,
            posterUrl = posterUrl,
            addedAt = System.currentTimeMillis()
        )
        favoriteDao.insert(favorite)
    }

    /**
     * Remove favorite by content ID
     */
    suspend fun removeFavorite(contentId: String) {
        favoriteDao.delete(contentId)
    }

    /**
     * Remove movie from favorites
     */
    suspend fun removeMovieFromFavorites(movieId: Int) {
        favoriteDao.delete("movie-$movieId")
    }

    /**
     * Remove series from favorites
     */
    suspend fun removeSeriesFromFavorites(seriesId: Int) {
        favoriteDao.delete("series-$seriesId")
    }

    /**
     * Toggle favorite status for content
     * @return true if now favorited, false if removed
     */
    suspend fun toggleFavorite(contentId: String, contentType: Favorite.ContentType, title: String, posterUrl: String?): Boolean {
        val existing = favoriteDao.getFavorite(contentId)
        return if (existing != null) {
            favoriteDao.delete(contentId)
            false
        } else {
            addFavorite(contentId, contentType, title, posterUrl)
            true
        }
    }

    // ========== Query Favorites ==========

    /**
     * Get all favorites (movies and series combined)
     */
    fun getAllFavorites(): Flow<List<Favorite>> {
        return favoriteDao.getAllFavorites()
    }

    /**
     * Get only movie favorites
     */
    fun getMovieFavorites(): Flow<List<Favorite>> {
        return favoriteDao.getFavoritesByType(Favorite.ContentType.MOVIE.name)
    }

    /**
     * Get only series favorites
     */
    fun getSeriesFavorites(): Flow<List<Favorite>> {
        return favoriteDao.getFavoritesByType(Favorite.ContentType.SERIES.name)
    }

    /**
     * Check if content is favorited (reactive Flow)
     */
    fun isFavorite(contentId: String): Flow<Boolean> {
        return favoriteDao.isFavorite(contentId)
    }

    /**
     * Check if movie is favorited (reactive Flow)
     */
    fun isMovieFavorited(movieId: Int): Flow<Boolean> {
        return favoriteDao.isFavorite("movie-$movieId")
    }

    /**
     * Check if series is favorited (reactive Flow)
     */
    fun isSeriesFavorited(seriesId: Int): Flow<Boolean> {
        return favoriteDao.isFavorite("series-$seriesId")
    }

    /**
     * Get single favorite (one-time check)
     */
    suspend fun getFavorite(contentId: String): Favorite? {
        return favoriteDao.getFavorite(contentId)
    }

    // ========== Statistics ==========

    /**
     * Get total favorites count
     */
    suspend fun getTotalFavoritesCount(): Int {
        return favoriteDao.getFavoritesCount()
    }

    /**
     * Get count by content type
     */
    suspend fun getFavoritesCountByType(contentType: Favorite.ContentType): Int {
        return favoriteDao.getCountByType(contentType.name)
    }

    /**
     * Get movie favorites count
     */
    suspend fun getMovieFavoritesCount(): Int {
        return favoriteDao.getCountByType(Favorite.ContentType.MOVIE.name)
    }

    /**
     * Get series favorites count
     */
    suspend fun getSeriesFavoritesCount(): Int {
        return favoriteDao.getCountByType(Favorite.ContentType.SERIES.name)
    }

    // ========== Utilities ==========

    /**
     * Clear all favorites (for testing/reset)
     */
    suspend fun clearAllFavorites() {
        favoriteDao.clearAll()
    }
}
