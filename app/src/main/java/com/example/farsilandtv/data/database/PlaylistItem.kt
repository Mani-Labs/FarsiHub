package com.example.farsilandtv.data.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * PlaylistItem entity - Junction table for playlist content
 * Links playlists to their content (movies or series) with ordering
 */
@Entity(
    tableName = "playlist_items",
    foreignKeys = [
        ForeignKey(
            entity = Playlist::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE // Delete all items when playlist is deleted
        )
    ],
    indices = [
        Index(value = ["playlistId", "contentId"], unique = true), // Prevent duplicates
        Index(value = ["playlistId"]) // Fast playlist lookups
    ]
)
data class PlaylistItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playlistId: Long,
    val contentId: String, // "movie-{id}" or "series-{id}"
    val contentType: ContentType,
    val addedAt: Long = System.currentTimeMillis(),
    val position: Int = 0 // Order in playlist (for manual sorting)
) {
    /**
     * Content type enum for playlist items
     */
    enum class ContentType {
        MOVIE, SERIES
    }

    /**
     * Extract numeric ID from contentId
     * e.g., "movie-123" -> 123
     */
    val numericId: Int
        get() = contentId.substringAfter("-").toIntOrNull() ?: 0
}
