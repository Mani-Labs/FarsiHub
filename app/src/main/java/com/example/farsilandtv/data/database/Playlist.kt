package com.example.farsilandtv.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Playlist entity - Custom playlists for organizing content
 * Users can create playlists and add both movies and series to them
 */
@Entity(tableName = "playlists")
data class Playlist(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val coverImageUrl: String? = null // Optional custom cover, or use first item's poster
)
