package com.example.farsilandtv.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * DAO for Playlists
 * Handles all database operations for playlist management
 */
@Dao
interface PlaylistDao {

    // ========== Create/Update/Delete ==========

    /**
     * Insert new playlist
     * @return ID of newly created playlist
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(playlist: Playlist): Long

    /**
     * Update existing playlist
     */
    @Update
    suspend fun update(playlist: Playlist)

    /**
     * Delete playlist (cascade deletes all items)
     */
    @Delete
    suspend fun delete(playlist: Playlist)

    /**
     * Delete playlist by ID
     */
    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun deleteById(playlistId: Long)

    // ========== Query Playlists ==========

    /**
     * Get all playlists ordered by most recently updated
     */
    @Query("SELECT * FROM playlists ORDER BY updatedAt DESC")
    fun getAllPlaylists(): Flow<List<Playlist>>

    /**
     * Get single playlist by ID (reactive)
     */
    @Query("SELECT * FROM playlists WHERE id = :playlistId")
    fun getPlaylist(playlistId: Long): Flow<Playlist?>

    /**
     * Get single playlist by ID (one-time)
     */
    @Query("SELECT * FROM playlists WHERE id = :playlistId")
    suspend fun getPlaylistOnce(playlistId: Long): Playlist?

    /**
     * Get playlists ordered by name
     */
    @Query("SELECT * FROM playlists ORDER BY name ASC")
    fun getPlaylistsByName(): Flow<List<Playlist>>

    /**
     * Get playlists ordered by creation date
     */
    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    fun getPlaylistsByCreationDate(): Flow<List<Playlist>>

    /**
     * Search playlists by name
     */
    @Query("SELECT * FROM playlists WHERE name LIKE '%' || :query || '%' ORDER BY updatedAt DESC")
    fun searchPlaylists(query: String): Flow<List<Playlist>>

    // ========== Playlist Item Count ==========

    /**
     * Get count of items in a playlist
     */
    @Query("SELECT COUNT(*) FROM playlist_items WHERE playlistId = :playlistId")
    suspend fun getItemCount(playlistId: Long): Int

    /**
     * Get count of items in a playlist (reactive)
     */
    @Query("SELECT COUNT(*) FROM playlist_items WHERE playlistId = :playlistId")
    fun getItemCountFlow(playlistId: Long): Flow<Int>

    // ========== Statistics ==========

    /**
     * Get total playlists count
     */
    @Query("SELECT COUNT(*) FROM playlists")
    suspend fun getPlaylistsCount(): Int

    /**
     * Update playlist's updatedAt timestamp
     */
    @Query("UPDATE playlists SET updatedAt = :timestamp WHERE id = :playlistId")
    suspend fun updateTimestamp(playlistId: Long, timestamp: Long = System.currentTimeMillis())

    /**
     * Clear all playlists (for testing/reset)
     */
    @Query("DELETE FROM playlists")
    suspend fun clearAll()
}
