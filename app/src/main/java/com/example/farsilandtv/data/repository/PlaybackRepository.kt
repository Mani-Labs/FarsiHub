package com.example.farsilandtv.data.repository

import android.content.Context
import com.example.farsilandtv.data.database.AppDatabase
import com.example.farsilandtv.data.database.PlaybackPosition
import kotlinx.coroutines.flow.Flow

/**
 * Repository for playback position and watched status tracking
 * Implements Feature #2: Watched Status Tracking
 *
 * C1 Consolidation: Now uses single AppDatabase instance instead of FarsilandDatabase
 * to eliminate dual database pattern and prevent data consistency issues.
 *
 * SINGLETON PATTERN: Use getInstance() to get the shared instance.
 * This prevents multiple database connections and ensures cache consistency.
 */
class PlaybackRepository private constructor(context: Context) {

    private val database = AppDatabase.getDatabase(context.applicationContext)
    private val dao = database.playbackPositionDao()

    companion object {
        // Threshold for auto-marking content as completed (95%)
        private const val COMPLETION_THRESHOLD = 0.95f
        // Threshold for episodes (90% - typically skip credits)
        private const val COMPLETION_THRESHOLD_EPISODE = 0.90f

        @Volatile
        private var INSTANCE: PlaybackRepository? = null

        /**
         * Get singleton instance of PlaybackRepository
         * Thread-safe double-check locking pattern
         */
        fun getInstance(context: Context): PlaybackRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: PlaybackRepository(context.applicationContext).also {
                    INSTANCE = it
                }
            }
        }
    }

    /**
     * Save playback position with auto-detection for completion
     * Automatically marks content as completed if watched >= 95%
     *
     * @param contentId Content ID (movie or episode)
     * @param contentType Type of content ("movie" or "episode")
     * @param contentTitle Title of the content
     * @param contentUrl Farsiland.com URL
     * @param position Current playback position in milliseconds
     * @param duration Total content duration in milliseconds
     * @param quality Selected video quality (e.g., "1080p")
     */
    suspend fun savePosition(
        contentId: Int,
        contentType: String,
        contentTitle: String,
        contentUrl: String,
        position: Long,
        duration: Long,
        quality: String
    ) {
        val currentTime = System.currentTimeMillis()

        // Calculate watch percentage with bounds checking
        val watchPercentage = if (duration > 0) {
            (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
        } else {
            0f // Unknown duration → always incomplete
        }

        // Auto-detect completion (use 90% for episodes, 95% for movies)
        val threshold = if (contentType == "episode") COMPLETION_THRESHOLD_EPISODE else COMPLETION_THRESHOLD
        val isCompleted = watchPercentage >= threshold
        val completedAt = if (isCompleted) currentTime else null

        val playbackPosition = PlaybackPosition(
            contentId = contentId,
            contentType = contentType,
            contentTitle = contentTitle,
            contentUrl = contentUrl,
            position = position,
            duration = duration,
            quality = quality,
            lastWatchedAt = currentTime,
            isCompleted = isCompleted,
            completedAt = completedAt
        )

        dao.savePosition(playbackPosition)
    }

    /**
     * Get playback position for specific content
     */
    suspend fun getPosition(contentId: Int, contentType: String): PlaybackPosition? {
        return dao.getPosition(contentId, contentType)
    }

    /**
     * Get recent playback positions (for Continue Watching)
     * Only returns incomplete content
     */
    suspend fun getRecentPositions(limit: Int = 20): List<PlaybackPosition> {
        return dao.getRecentPositions(limit)
    }

    /**
     * Get all completed content (movies and episodes)
     */
    fun getCompletedContent(): Flow<List<PlaybackPosition>> {
        return dao.getCompletedContent()
    }

    /**
     * Get completed movies only
     */
    fun getCompletedMovies(): Flow<List<PlaybackPosition>> {
        return dao.getCompletedMovies()
    }

    /**
     * Get completed episodes only
     */
    fun getCompletedEpisodes(): Flow<List<PlaybackPosition>> {
        return dao.getCompletedEpisodes()
    }

    /**
     * Check if specific content is marked as completed
     * Returns Flow for reactive UI updates
     */
    fun isCompleted(contentId: Int, contentType: String): Flow<Boolean?> {
        return dao.isCompleted(contentId, contentType)
    }

    /**
     * Manually mark content as watched
     * Used when user clicks "Mark as Watched" button
     */
    suspend fun markAsCompleted(contentId: Int, contentType: String) {
        val currentTime = System.currentTimeMillis()
        dao.markAsCompleted(contentId, contentType, currentTime)
    }

    /**
     * Manually mark content as unwatched
     * Used when user clicks "Mark as Unwatched" button
     */
    suspend fun markAsIncomplete(contentId: Int, contentType: String) {
        dao.markAsIncomplete(contentId, contentType)
    }

    /**
     * Delete a playback position entry
     */
    suspend fun deletePosition(position: PlaybackPosition) {
        dao.deletePosition(position)
    }

    /**
     * Clear all playback positions
     */
    suspend fun clearAll() {
        dao.clearAll()
    }

    /**
     * Delete old completed entries (older than specified timestamp)
     * Useful for cleanup - e.g., delete entries older than 30 days
     */
    suspend fun deleteOldCompleted(timestamp: Long) {
        dao.deleteOldCompleted(timestamp)
    }
}
