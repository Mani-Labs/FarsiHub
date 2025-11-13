package com.example.farsilandtv.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * DAO for playback position tracking
 */
@Dao
interface PlaybackPositionDao {

    /**
     * Save or update playback position
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun savePosition(position: PlaybackPosition)

    /**
     * Get playback position for a specific content
     */
    @Query("SELECT * FROM playback_positions WHERE contentId = :contentId AND contentType = :contentType")
    suspend fun getPosition(contentId: Int, contentType: String): PlaybackPosition?

    /**
     * Get all saved positions (for Continue Watching row)
     */
    @Query("SELECT * FROM playback_positions WHERE isCompleted = 0 ORDER BY lastWatchedAt DESC LIMIT :limit")
    suspend fun getRecentPositions(limit: Int = 20): List<PlaybackPosition>

    /**
     * Delete position (when user finishes watching)
     */
    @Delete
    suspend fun deletePosition(position: PlaybackPosition)

    /**
     * Clear all positions
     */
    @Query("DELETE FROM playback_positions")
    suspend fun clearAll()

    /**
     * Delete old completed entries (older than 30 days)
     */
    @Query("DELETE FROM playback_positions WHERE isCompleted = 1 AND lastWatchedAt < :timestamp")
    suspend fun deleteOldCompleted(timestamp: Long)

    // ========== NEW: Watched Status Tracking Methods (Feature #2) ==========

    /**
     * Get all completed content (movies and episodes)
     * Returns Flow for reactive updates
     */
    @Query("SELECT * FROM playback_positions WHERE isCompleted = 1 ORDER BY completedAt DESC")
    fun getCompletedContent(): Flow<List<PlaybackPosition>>

    /**
     * Get completed movies only
     */
    @Query("SELECT * FROM playback_positions WHERE isCompleted = 1 AND contentType = 'movie' ORDER BY completedAt DESC")
    fun getCompletedMovies(): Flow<List<PlaybackPosition>>

    /**
     * Get completed episodes only
     */
    @Query("SELECT * FROM playback_positions WHERE isCompleted = 1 AND contentType = 'episode' ORDER BY completedAt DESC")
    fun getCompletedEpisodes(): Flow<List<PlaybackPosition>>

    /**
     * Check if specific content is marked as completed
     */
    @Query("SELECT isCompleted FROM playback_positions WHERE contentId = :contentId AND contentType = :contentType")
    fun isCompleted(contentId: Int, contentType: String): Flow<Boolean?>

    /**
     * Manually mark content as completed
     * Used when user clicks "Mark as Watched" button
     */
    @Query("UPDATE playback_positions SET isCompleted = 1, completedAt = :completedAt WHERE contentId = :contentId AND contentType = :contentType")
    suspend fun markAsCompleted(contentId: Int, contentType: String, completedAt: Long)

    /**
     * Manually mark content as incomplete (unwatch)
     * Used when user clicks "Mark as Unwatched" button
     */
    @Query("UPDATE playback_positions SET isCompleted = 0, completedAt = NULL WHERE contentId = :contentId AND contentType = :contentType")
    suspend fun markAsIncomplete(contentId: Int, contentType: String)
}
