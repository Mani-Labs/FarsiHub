package com.example.farsilandtv.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity for tracking playback positions and watched status
 * Consolidated into AppDatabase (was previously in FarsilandDatabase)
 *
 * C1 Fix: Moved from separate FarsilandDatabase to AppDatabase
 * to eliminate dual database pattern and prevent data sync issues
 */
@Entity(
    tableName = "playback_positions",
    primaryKeys = ["contentId", "contentType"]
)
data class PlaybackPosition(
    val contentId: Int,
    val contentType: String, // "movie" or "episode"
    val contentTitle: String,
    val contentUrl: String,
    val position: Long, // milliseconds
    val duration: Long, // milliseconds
    val quality: String, // e.g., "1080p"
    val lastWatchedAt: Long,
    val isCompleted: Boolean,
    val completedAt: Long? = null
)
