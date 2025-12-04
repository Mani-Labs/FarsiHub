package com.example.farsilandtv.data.imvbox

/**
 * URL builder for IMVBox.com
 *
 * URL Patterns discovered:
 * - Movies List:      https://www.imvbox.com/en/movies
 * - Movie Detail:     https://www.imvbox.com/en/movies/{slug}
 * - Movie Play:       https://www.imvbox.com/en/movies/{slug}/play
 * - TV Series List:   https://www.imvbox.com/en/tv-series
 * - Show Detail:      https://www.imvbox.com/en/shows/{slug}
 * - Episode Detail:   https://www.imvbox.com/en/shows/{slug}/season-{n}/episode-{n}
 * - Episode Play:     https://www.imvbox.com/en/shows/{slug}/season-{n}/episode-{n}/play
 * - Search API:       POST https://www.imvbox.com/en/search-and-fetch-data
 * - HLS Stream:       https://streaming.imvbox.com/media/{id}/{id}.m3u8
 *
 * Asset Patterns:
 * - Poster:           https://assets.imvbox.com/movies/{slug}pos.jpg
 * - Thumbnail:        https://assets.imvbox.com/movies/{slug}th.jpg
 * - Show Thumbnail:   https://assets.imvbox.com/shows/{slug}Th.webp
 */
object IMVBoxUrlBuilder {

    const val BASE_URL = "https://www.imvbox.com"
    const val BASE_EN = "$BASE_URL/en"
    const val ASSETS_CDN = "https://assets.imvbox.com"
    const val STREAMING_CDN = "https://streaming.imvbox.com"

    // Search API endpoint (POST - AJAX live search)
    const val SEARCH_API = "$BASE_EN/search-and-fetch-data"

    // Search page URL (GET - full page search)
    const val SEARCH_PAGE = "$BASE_EN/search"

    /**
     * Build search page URL (GET request)
     * Requires minimum 3 characters
     *
     * @param query Search query
     * @return Full URL to search page
     */
    fun buildSearchUrl(query: String): String {
        val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
        return "$SEARCH_PAGE?website=&key=$encodedQuery"
    }

    /**
     * Build movies list URL
     *
     * @param page Page number (1-based)
     * @param sortBy Sort option (new-releases, highest-rated, recently-subtitled)
     * @return Full URL to movies list
     */
    fun buildMoviesListUrl(page: Int = 1, sortBy: String? = null): String {
        val base = "$BASE_EN/movies"
        val params = mutableListOf<String>()

        if (page > 1) params.add("page=$page")
        if (!sortBy.isNullOrBlank()) params.add("sort_by=$sortBy")

        return if (params.isEmpty()) base else "$base?${params.joinToString("&")}"
    }

    /**
     * Build TV series list URL
     *
     * @param page Page number (1-based)
     * @param sortBy Sort option
     * @return Full URL to TV series list
     */
    fun buildSeriesListUrl(page: Int = 1, sortBy: String? = null): String {
        val base = "$BASE_EN/tv-series"
        val params = mutableListOf<String>()

        if (page > 1) params.add("page=$page")
        if (!sortBy.isNullOrBlank()) params.add("sort_by=$sortBy")

        return if (params.isEmpty()) base else "$base?${params.joinToString("&")}"
    }

    /**
     * Build movie detail page URL
     *
     * @param slug Movie slug (e.g., "sad-dam2025")
     * @return Full URL to movie detail page
     */
    fun buildMovieDetailUrl(slug: String): String {
        return "$BASE_EN/movies/$slug"
    }

    /**
     * Build movie play page URL
     *
     * @param slug Movie slug
     * @return Full URL to movie play page
     */
    fun buildMoviePlayUrl(slug: String): String {
        return "$BASE_EN/movies/$slug/play"
    }

    /**
     * Build show detail page URL
     *
     * @param slug Show slug (e.g., "mozaffars-garden-baghe-mozaffar")
     * @return Full URL to show detail page
     */
    fun buildShowDetailUrl(slug: String): String {
        return "$BASE_EN/shows/$slug"
    }

    /**
     * Build season detail URL
     * IMVBox pattern: /shows/{slug}/{slug}?tab=episodes (season is part of slug for multi-season)
     *
     * @param showSlug Show slug
     * @param season Season number (used for multi-season shows)
     * @return Full URL to season page
     */
    fun buildSeasonUrl(showSlug: String, season: Int): String {
        // IMVBox uses double slug pattern: /shows/{slug}/{slug}
        // For season 1 or single season: just use the slug
        // For other seasons: may append season number to slug
        return if (season <= 1) {
            "$BASE_EN/shows/$showSlug/$showSlug?tab=episodes"
        } else {
            "$BASE_EN/shows/$showSlug/$showSlug-season-$season?tab=episodes"
        }
    }

    /**
     * Build episode detail URL
     * IMVBox pattern: /shows/{slug}/season-{n}/episode-{n}
     *
     * @param showSlug Show slug
     * @param season Season number
     * @param episode Episode number
     * @return Full URL to episode detail page
     */
    fun buildEpisodeDetailUrl(showSlug: String, season: Int, episode: Int): String {
        // IMVBox uses: /shows/{slug}/season-{n}/episode-{n}
        return "$BASE_EN/shows/$showSlug/season-$season/episode-$episode"
    }

    /**
     * Build episode play URL
     * IMVBox pattern: /shows/{slug}/season-{n}/episode-{n}/play
     *
     * @param showSlug Show slug
     * @param season Season number
     * @param episode Episode number
     * @return Full URL to episode play page
     */
    fun buildEpisodePlayUrl(showSlug: String, season: Int, episode: Int): String {
        return "$BASE_EN/shows/$showSlug/season-$season/episode-$episode/play"
    }

    /**
     * Build HLS stream URL
     *
     * @param mediaId Numeric media ID
     * @return Full HLS master playlist URL
     */
    fun buildHlsStreamUrl(mediaId: String): String {
        return "$STREAMING_CDN/media/$mediaId/$mediaId.m3u8"
    }

    /**
     * Build trailer HLS stream URL
     *
     * @param mediaId Numeric media ID
     * @return Full HLS trailer URL
     */
    fun buildTrailerStreamUrl(mediaId: String): String {
        return "$STREAMING_CDN/media/trailers/$mediaId/$mediaId.m3u8"
    }

    /**
     * Build movie poster URL
     *
     * @param slug Movie slug
     * @return Full URL to poster image
     */
    fun buildMoviePosterUrl(slug: String): String {
        return "$ASSETS_CDN/movies/${slug}pos.jpg"
    }

    /**
     * Build movie thumbnail URL
     *
     * @param slug Movie slug
     * @return Full URL to thumbnail image
     */
    fun buildMovieThumbnailUrl(slug: String): String {
        return "$ASSETS_CDN/movies/${slug}th.jpg"
    }

    /**
     * Build show thumbnail URL
     *
     * @param slug Show slug
     * @return Full URL to show thumbnail
     */
    fun buildShowThumbnailUrl(slug: String): String {
        return "$ASSETS_CDN/shows/${slug}Th.webp"
    }

    /**
     * Build cast photo URL
     *
     * @param castId Cast member ID or name slug
     * @return Full URL to cast photo
     */
    fun buildCastPhotoUrl(castId: String): String {
        return "$ASSETS_CDN/cast/$castId.webp"
    }

    /**
     * Extract slug from IMVBox URL
     *
     * @param url Full IMVBox URL
     * @return Slug portion of URL
     */
    fun extractSlug(url: String): String? {
        // Handle URLs like /en/movies/sad-dam2025 or /en/shows/mozaffars-garden
        val regex = Regex("""/(?:movies|shows|tv-series)/([^/?#]+)""")
        return regex.find(url)?.groupValues?.getOrNull(1)
    }

    /**
     * Extract media ID from HLS URL
     *
     * @param hlsUrl Full HLS stream URL
     * @return Numeric media ID
     */
    fun extractMediaId(hlsUrl: String): String? {
        // Handle URLs like streaming.imvbox.com/media/3628/3628.m3u8
        val regex = Regex("""/media/(\d+)/""")
        return regex.find(hlsUrl)?.groupValues?.getOrNull(1)
    }

    /**
     * Check if URL is an IMVBox URL
     */
    fun isIMVBoxUrl(url: String): Boolean {
        return url.contains("imvbox.com", ignoreCase = true)
    }
}
