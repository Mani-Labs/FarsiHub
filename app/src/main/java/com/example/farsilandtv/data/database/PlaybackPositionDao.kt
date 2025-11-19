package com.example.farsilandtv.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * DAO for playback position tracking and watched status
 *
 * C1 Fix: Consolidated into AppDatabase (moved from FarsilandDatabase)
 */
@Dao
interface PlaybackPositionDao {

    /**
     * Save or update playback position
     * Uses REPLACE strategy to handle both insert and update
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun savePosition(position: PlaybackPosition)

    /**
     * Get playback position for specific content
     */
    @Query("SELECT * FROM playback_positions WHERE contentId = :contentId AND contentType = :contentType")
    suspend fun getPosition(contentId: Int, contentType: String): PlaybackPosition?

    /**
     * Get recent playback positions for Continue Watching
     * Only returns incomplete content, ordered by most recent
     */
    @Query("""
        SELECT * FROM playback_positions
        WHERE isCompleted = 0
        ORDER BY lastWatchedAt DESC
        LIMIT :limit
    """)
    suspend fun getRecentPositions(limit: Int): List<PlaybackPosition>

    /**
     * Get all completed content (movies and episodes)
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
     * Check if specific content is completed
     */
    @Query("SELECT isCompleted FROM playback_positions WHERE contentId = :contentId AND contentType = :contentType")
    fun isCompleted(contentId: Int, contentType: String): Flow<Boolean?>

    /**
     * Mark content as completed
     */
    @Query("UPDATE playback_positions SET isCompleted = 1, completedAt = :completedAt WHERE contentId = :contentId AND contentType = :contentType")
    suspend fun markAsCompleted(contentId: Int, contentType: String, completedAt: Long)

    /**
     * Mark content as incomplete
     */
    @Query("UPDATE playback_positions SET isCompleted = 0, completedAt = NULL WHERE contentId = :contentId AND contentType = :contentType")
    suspend fun markAsIncomplete(contentId: Int, contentType: String)

    /**
     * Delete specific playback position
     */
    @Delete
    suspend fun deletePosition(position: PlaybackPosition)

    /**
     * Clear all playback positions
     */
    @Query("DELETE FROM playback_positions")
    suspend fun clearAll()

    /**
     * Delete old completed entries (cleanup)
     */
    @Query("DELETE FROM playback_positions WHERE isCompleted = 1 AND completedAt < :timestamp")
    suspend fun deleteOldCompleted(timestamp: Long)
}
