package com.example.farsilandtv.data.models

/**
 * Represents a video URL extracted from episode/movie page HTML
 * Video URLs are found in <link itemprop="contentUrl"> tags
 */
data class VideoUrl(
    val url: String, // Direct MP4 URL (e.g., https://d1.flnd.buzz/series/shoghal/01.1080.mp4)
    val quality: String, // "1080p", "720p", "480p"
    val fileSizeMb: Float? = null, // Optional file size
    val mirror: String? = null // "d1.flnd.buzz" or "d2.flnd.buzz"
) {
    companion object {
        /**
         * Extract quality from URL
         * Example: https://d1.flnd.buzz/series/shoghal/01.1080.mp4 -> "1080p"
         */
        fun extractQuality(url: String): String {
            return when {
                ".1080." in url || "-1080." in url -> "1080p"
                ".720." in url || "-720." in url -> "720p"
                ".480." in url || "-480." in url -> "480p"
                ".360." in url || "-360." in url -> "360p"
                else -> "unknown"
            }
        }

        /**
         * Extract mirror from URL
         * Example: https://d1.flnd.buzz/series/shoghal/01.1080.mp4 -> "d1.flnd.buzz"
         */
        fun extractMirror(url: String): String? {
            return when {
                "d1.flnd.buzz" in url -> "d1.flnd.buzz"
                "d2.flnd.buzz" in url -> "d2.flnd.buzz"
                else -> url.substringAfter("://").substringBefore("/")
            }
        }
    }
}

/**
 * Quality variants for a video with multiple mirrors
 */
data class QualityVariants(
    val quality: String, // "1080p"
    val urls: List<VideoUrl> // Multiple mirrors for same quality
)
