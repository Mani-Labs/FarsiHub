package com.example.farsilandtv.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * DAO for PlaylistItems
 * Handles all database operations for playlist content management
 */
@Dao
interface PlaylistItemDao {

    // ========== Add/Remove Items ==========

    /**
     * Insert new playlist item
     * @return ID of newly created item
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(item: PlaylistItem): Long

    /**
     * Insert multiple items at once
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(items: List<PlaylistItem>): List<Long>

    /**
     * Update playlist item
     */
    @Update
    suspend fun update(item: PlaylistItem)

    /**
     * Delete playlist item
     */
    @Delete
    suspend fun delete(item: PlaylistItem)

    /**
     * Delete item by ID
     */
    @Query("DELETE FROM playlist_items WHERE id = :itemId")
    suspend fun deleteById(itemId: Long)

    /**
     * Delete item by playlist and content
     */
    @Query("DELETE FROM playlist_items WHERE playlistId = :playlistId AND contentId = :contentId")
    suspend fun deleteByContent(playlistId: Long, contentId: String)

    /**
     * Delete all items from a playlist
     */
    @Query("DELETE FROM playlist_items WHERE playlistId = :playlistId")
    suspend fun deleteAllFromPlaylist(playlistId: Long)

    // ========== Query Items ==========

    /**
     * Get all items in a playlist ordered by position
     */
    @Query("SELECT * FROM playlist_items WHERE playlistId = :playlistId ORDER BY position ASC, addedAt DESC")
    fun getPlaylistItems(playlistId: Long): Flow<List<PlaylistItem>>

    /**
     * Get all items in a playlist (one-time)
     */
    @Query("SELECT * FROM playlist_items WHERE playlistId = :playlistId ORDER BY position ASC, addedAt DESC")
    suspend fun getPlaylistItemsOnce(playlistId: Long): List<PlaylistItem>

    /**
     * Get items filtered by content type
     */
    @Query("SELECT * FROM playlist_items WHERE playlistId = :playlistId AND contentType = :contentType ORDER BY position ASC, addedAt DESC")
    fun getItemsByType(playlistId: Long, contentType: String): Flow<List<PlaylistItem>>

    /**
     * Get single item by ID
     */
    @Query("SELECT * FROM playlist_items WHERE id = :itemId")
    suspend fun getItem(itemId: Long): PlaylistItem?

    // ========== Check Existence ==========

    /**
     * Check if content is in a playlist (reactive)
     */
    @Query("SELECT COUNT(*) > 0 FROM playlist_items WHERE playlistId = :playlistId AND contentId = :contentId")
    fun isInPlaylist(playlistId: Long, contentId: String): Flow<Boolean>

    /**
     * Check if content is in a playlist (one-time)
     */
    @Query("SELECT COUNT(*) > 0 FROM playlist_items WHERE playlistId = :playlistId AND contentId = :contentId")
    suspend fun isInPlaylistOnce(playlistId: Long, contentId: String): Boolean

    /**
     * Get all playlists containing specific content
     */
    @Query("SELECT playlistId FROM playlist_items WHERE contentId = :contentId")
    fun getPlaylistsForContent(contentId: String): Flow<List<Long>>

    // ========== Reordering ==========

    /**
     * Update item position
     */
    @Query("UPDATE playlist_items SET position = :position WHERE id = :itemId")
    suspend fun updatePosition(itemId: Long, position: Int)

    /**
     * Get maximum position in playlist (for adding new items at end)
     */
    @Query("SELECT COALESCE(MAX(position), 0) FROM playlist_items WHERE playlistId = :playlistId")
    suspend fun getMaxPosition(playlistId: Long): Int

    // ========== Statistics ==========

    /**
     * Get count of items in a playlist
     */
    @Query("SELECT COUNT(*) FROM playlist_items WHERE playlistId = :playlistId")
    suspend fun getItemCount(playlistId: Long): Int

    /**
     * Get count by content type
     */
    @Query("SELECT COUNT(*) FROM playlist_items WHERE playlistId = :playlistId AND contentType = :contentType")
    suspend fun getCountByType(playlistId: Long, contentType: String): Int

    /**
     * Clear all items (for testing/reset)
     */
    @Query("DELETE FROM playlist_items")
    suspend fun clearAll()
}
