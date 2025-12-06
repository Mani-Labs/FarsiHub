package com.example.farsilandtv.utils

/**
 * Centralized Intent extra key constants for type-safe Intent data passing.
 * Prevents typos and enables IDE refactoring across the codebase.
 *
 * EXTERNAL AUDIT FIX UT-L1: Added KDoc documentation for all constants
 */
object IntentExtras {
    // Content identification

    /** Content type for navigation (movie/episode/series) */
    const val CONTENT_TYPE = "CONTENT_TYPE"

    /** Unique content ID (movie ID, series ID, or episode ID) */
    const val CONTENT_ID = "CONTENT_ID"

    /** Content title for display and logging */
    const val CONTENT_TITLE = "CONTENT_TITLE"

    /** Farsiland/FarsiPlex page URL for the content */
    const val CONTENT_URL = "CONTENT_URL"

    /** Poster image URL for content preview */
    const val CONTENT_POSTER_URL = "CONTENT_POSTER_URL"

    // Episode-specific

    /** Parent series ID for episode navigation */
    const val SERIES_ID = "SERIES_ID"

    /** Season number for episode (1-based) */
    const val EPISODE_SEASON = "EPISODE_SEASON"

    /** Episode number within season (1-based) */
    const val EPISODE_NUMBER = "EPISODE_NUMBER"

    // Video player selection

    /** Pre-selected video URL for VideoPlayerActivity */
    const val SELECTED_VIDEO_URL = "SELECTED_VIDEO_URL"

    /** Pre-selected video quality (1080p, 720p, 480p) */
    const val SELECTED_VIDEO_QUALITY = "SELECTED_VIDEO_QUALITY"

    /**
     * Content type enum values
     * Used with CONTENT_TYPE extra to identify content category
     */
    object ContentType {
        /** Movie content type */
        const val MOVIE = "movie"

        /** Episode content type */
        const val EPISODE = "episode"

        /** Series content type */
        const val SERIES = "series"
    }
}
