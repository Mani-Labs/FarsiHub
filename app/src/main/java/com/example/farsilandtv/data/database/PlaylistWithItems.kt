package com.example.farsilandtv.data.database

import androidx.room.Embedded
import androidx.room.Relation

/**
 * Playlist with its items (Room relation)
 * Used for efficient queries joining playlists with their content
 */
data class PlaylistWithItems(
    @Embedded val playlist: Playlist,
    @Relation(
        parentColumn = "id",
        entityColumn = "playlistId"
    )
    val items: List<PlaylistItem>
) {
    /**
     * Get count of items in playlist
     */
    val itemCount: Int
        get() = items.size

    /**
     * Get count by content type
     */
    fun getCountByType(type: PlaylistItem.ContentType): Int {
        return items.count { it.contentType == type }
    }

    /**
     * Get movie count
     */
    val movieCount: Int
        get() = getCountByType(PlaylistItem.ContentType.MOVIE)

    /**
     * Get series count
     */
    val seriesCount: Int
        get() = getCountByType(PlaylistItem.ContentType.SERIES)

    /**
     * Check if playlist is empty
     */
    val isEmpty: Boolean
        get() = items.isEmpty()
}
