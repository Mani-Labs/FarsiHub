package com.example.farsilandtv.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Playback position tracking entity
 * Saves where users left off watching movies and episodes
 */
@Entity(
    tableName = "playback_positions",
    primaryKeys = ["contentId", "contentType"],
    indices = [
        Index(value = ["isCompleted", "lastWatchedAt"]),
        Index(value = ["contentId", "contentType"]),
        Index(value = ["isCompleted", "contentType"])
    ]
)
data class PlaybackPosition(
    val contentId: Int, // Movie or Episode ID

    val contentType: String, // "movie" or "episode"
    val contentTitle: String,
    val contentUrl: String, // Farsiland.com URL

    val position: Long, // Playback position in milliseconds
    val duration: Long, // Total duration in milliseconds
    val quality: String, // Selected quality (e.g., "1080p")

    val lastWatchedAt: Long, // Timestamp when last watched (updated on every position save)
    val isCompleted: Boolean = false, // True if watched 95%+ or manually marked
    val completedAt: Long? = null // Timestamp when marked as completed (nullable)
)
