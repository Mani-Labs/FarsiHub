package com.example.farsilandtv.utils

/**
 * Centralized Intent extra key constants for type-safe Intent data passing.
 * Prevents typos and enables IDE refactoring across the codebase.
 */
object IntentExtras {
    // Content identification
    const val CONTENT_TYPE = "CONTENT_TYPE"
    const val CONTENT_ID = "CONTENT_ID"
    const val CONTENT_TITLE = "CONTENT_TITLE"
    const val CONTENT_URL = "CONTENT_URL"
    const val CONTENT_POSTER_URL = "CONTENT_POSTER_URL"

    // Episode-specific
    const val SERIES_ID = "SERIES_ID"
    const val EPISODE_SEASON = "EPISODE_SEASON"
    const val EPISODE_NUMBER = "EPISODE_NUMBER"

    // Video player selection
    const val SELECTED_VIDEO_URL = "SELECTED_VIDEO_URL"
    const val SELECTED_VIDEO_QUALITY = "SELECTED_VIDEO_QUALITY"

    // Content type values
    object ContentType {
        const val MOVIE = "movie"
        const val EPISODE = "episode"
        const val SERIES = "series"
    }
}
