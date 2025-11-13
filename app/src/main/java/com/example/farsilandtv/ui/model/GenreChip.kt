package com.example.farsilandtv.ui.model

import com.example.farsilandtv.data.model.Genre

/**
 * UI model for genre filter chips
 * Wraps Genre enum with selection state for multi-select filtering
 */
data class GenreChip(
    val genre: Genre,
    val isSelected: Boolean = false,
    val isClearButton: Boolean = false // Special "Clear Filters" chip
) {
    companion object {
        /**
         * Create "Clear Filters" chip
         */
        fun createClearChip(): GenreChip {
            return GenreChip(
                genre = Genre.ACTION, // Dummy genre (not used)
                isSelected = false,
                isClearButton = true
            )
        }

        /**
         * Create chips for all genres
         */
        fun createChipsForAllGenres(): List<GenreChip> {
            val chips = mutableListOf<GenreChip>()

            // Add "Clear Filters" chip first
            chips.add(createClearChip())

            // Add all genre chips
            Genre.values().forEach { genre ->
                chips.add(GenreChip(genre = genre, isSelected = false))
            }

            return chips
        }
    }
}
