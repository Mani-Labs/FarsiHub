package com.example.farsilandtv.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * DAO for Favorites
 * Handles all database operations for the universal favorites system
 */
@Dao
interface FavoriteDao {

    /**
     * Get all favorites (movies and series combined)
     * Sorted by most recently added first
     */
    @Query("SELECT * FROM favorites ORDER BY addedAt DESC")
    fun getAllFavorites(): Flow<List<Favorite>>

    /**
     * Get favorites filtered by content type
     * @param contentType "MOVIE" or "SERIES"
     */
    @Query("SELECT * FROM favorites WHERE contentType = :contentType ORDER BY addedAt DESC")
    fun getFavoritesByType(contentType: String): Flow<List<Favorite>>

    /**
     * Check if content is favorited
     * Returns Flow to observe real-time changes
     */
    @Query("SELECT COUNT(*) > 0 FROM favorites WHERE contentId = :contentId")
    fun isFavorite(contentId: String): Flow<Boolean>

    /**
     * Get single favorite by ID (for one-time checks)
     */
    @Query("SELECT * FROM favorites WHERE contentId = :contentId")
    suspend fun getFavorite(contentId: String): Favorite?

    /**
     * Insert or replace favorite
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(favorite: Favorite)

    /**
     * Delete favorite by content ID
     */
    @Query("DELETE FROM favorites WHERE contentId = :contentId")
    suspend fun delete(contentId: String)

    /**
     * Delete favorite entity
     */
    @Delete
    suspend fun delete(favorite: Favorite)

    /**
     * Get total favorites count
     */
    @Query("SELECT COUNT(*) FROM favorites")
    suspend fun getFavoritesCount(): Int

    /**
     * Get count by content type
     */
    @Query("SELECT COUNT(*) FROM favorites WHERE contentType = :contentType")
    suspend fun getCountByType(contentType: String): Int

    /**
     * Clear all favorites (for testing/reset)
     */
    @Query("DELETE FROM favorites")
    suspend fun clearAll()
}
