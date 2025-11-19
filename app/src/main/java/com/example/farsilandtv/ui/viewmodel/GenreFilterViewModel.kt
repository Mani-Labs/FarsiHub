package com.example.farsilandtv.ui.viewmodel

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * ViewModel for genre filtering state management
 * Shared between Movies and Shows screens for consistent filtering UX
 *
 * Features:
 * - Multi-select genre filtering
 * - Active filter count for badge display
 * - Clear all filters functionality
 * - State persists across configuration changes
 */
class GenreFilterViewModel : ViewModel() {

    // Selected genres state (immutable Set for thread safety)
    private val _selectedGenres = MutableStateFlow<Set<String>>(emptySet())
    val selectedGenres: StateFlow<Set<String>> = _selectedGenres.asStateFlow()

    /**
     * Active filter count for badge display
     */
    val activeFiltersCount: Int
        get() = _selectedGenres.value.size

    /**
     * Toggle genre selection (add if not present, remove if present)
     * @param genre Genre name to toggle
     */
    fun toggleGenre(genre: String) {
        _selectedGenres.update { current ->
            if (genre in current) {
                current - genre // Remove genre
            } else {
                current + genre // Add genre
            }
        }
    }

    /**
     * Clear all selected genres
     */
    fun clearFilters() {
        _selectedGenres.value = emptySet()
    }

    /**
     * Check if a specific genre is selected
     * @param genre Genre name to check
     * @return True if genre is selected
     */
    fun isGenreSelected(genre: String): Boolean {
        return genre in _selectedGenres.value
    }

    /**
     * Apply filters (placeholder for future repository integration)
     * Currently, filters are applied via StateFlow observation
     * @param allGenres All available genres (for validation)
     */
    fun applyFilters(allGenres: Set<String>) {
        // Filters are already applied via StateFlow
        // Future: Could add analytics tracking here
    }
}
