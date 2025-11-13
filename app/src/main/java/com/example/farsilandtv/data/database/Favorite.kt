package com.example.farsilandtv.data.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Favorite entity - Universal favorites for both movies and TV series
 * Supports adding any content type to favorites with minimal metadata
 */
@Entity(
    tableName = "favorites",
    indices = [
        Index(value = ["contentType"]) // M7: Filter favorites by content type (movies vs series)
    ]
)
data class Favorite(
    @PrimaryKey val contentId: String, // "movie-{id}" or "series-{id}"
    val contentType: ContentType,
    val title: String,
    val posterUrl: String?,
    val addedAt: Long = System.currentTimeMillis()
) {
    /**
     * Content type enum for favorites
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
