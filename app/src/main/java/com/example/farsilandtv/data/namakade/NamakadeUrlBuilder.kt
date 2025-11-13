package com.example.farsilandtv.data.namakade

/**
 * URL builder for Namakade.com (media.negahestan.com CDN)
 *
 * URL Patterns discovered:
 * - Series: https://media.negahestan.com/ipnx/media/series/episodes/{SeriesName}_{EpisodeNumber}.mp4
 * - Movies: https://media.negahestan.com/ipnx/media/movies/{MovieName}.mp4
 * - Thumbnails: https://media.negahestan.com/ipnx/media/series/thumbs/{SeriesName}_smallthumb.jpg
 *
 * Examples:
 * - https://media.negahestan.com/ipnx/media/series/episodes/Algoritm_01.mp4
 * - https://media.negahestan.com/ipnx/media/movies/Pirpesar.mp4
 */
object NamakadeUrlBuilder {

    private const val BASE_CDN = "https://media.negahestan.com/ipnx/media"
    private const val BASE_SITE = "https://namakade.com"

    /**
     * Build video URL for a series episode
     *
     * @param seriesName Series name in title case (e.g., "Algoritm", "Bamdaade_Khomaar")
     * @param episodeNumber Episode number (will be zero-padded to 2 digits)
     * @return Direct MP4 URL
     *
     * Example:
     * buildEpisodeUrl("Algoritm", 1) -> "https://media.negahestan.com/ipnx/media/series/episodes/Algoritm_01.mp4"
     */
    fun buildEpisodeUrl(seriesName: String, episodeNumber: Int): String {
        val episodeNum = episodeNumber.toString().padStart(2, '0')
        return "$BASE_CDN/series/episodes/${seriesName}_${episodeNum}.mp4"
    }

    /**
     * Build video URL for a series episode from slug
     *
     * @param seriesSlug Series slug from URL (e.g., "algoritm", "bamdaade-khomaar")
     * @param episodeNumber Episode number
     * @return Direct MP4 URL
     *
     * Example:
     * buildEpisodeUrlFromSlug("algoritm", 1) -> "https://media.negahestan.com/ipnx/media/series/episodes/Algoritm_01.mp4"
     * buildEpisodeUrlFromSlug("bamdaade-khomaar", 1) -> "https://media.negahestan.com/ipnx/media/series/episodes/Bamdaade_Khomaar_01.mp4"
     */
    fun buildEpisodeUrlFromSlug(seriesSlug: String, episodeNumber: Int): String {
        val seriesName = formatSeriesName(seriesSlug)
        return buildEpisodeUrl(seriesName, episodeNumber)
    }

    /**
     * Build video URL for a movie
     *
     * @param movieName Movie name in title case (e.g., "Pirpesar")
     * @return Direct MP4 URL
     *
     * Example:
     * buildMovieUrl("Pirpesar") -> "https://media.negahestan.com/ipnx/media/movies/Pirpesar.mp4"
     */
    fun buildMovieUrl(movieName: String): String {
        return "$BASE_CDN/movies/${movieName}.mp4"
    }

    /**
     * Build video URL for a movie from slug
     *
     * @param movieSlug Movie slug from URL (e.g., "pir-pesar")
     * @return Direct MP4 URL
     *
     * Example:
     * buildMovieUrlFromSlug("pir-pesar") -> "https://media.negahestan.com/ipnx/media/movies/PirPesar.mp4"
     */
    fun buildMovieUrlFromSlug(movieSlug: String): String {
        val movieName = formatMovieName(movieSlug)
        return buildMovieUrl(movieName)
    }

    /**
     * Build thumbnail URL for a series (small thumb)
     *
     * @param seriesName Series name in title case
     * @return Thumbnail URL
     *
     * Example:
     * buildSeriesThumbnailUrl("Algoritm") -> "https://media.negahestan.com/ipnx/media/series/thumbs/Algoritm_smallthumb.jpg"
     */
    fun buildSeriesThumbnailUrl(seriesName: String, isSmall: Boolean = true): String {
        val suffix = if (isSmall) "_smallthumb" else "_bigthumb"
        return "$BASE_CDN/series/thumbs/${seriesName}${suffix}.jpg"
    }

    /**
     * Build thumbnail URL for an episode
     *
     * @param seriesName Series name in title case
     * @param episodeNumber Episode number
     * @return Thumbnail URL
     *
     * Example:
     * buildEpisodeThumbnailUrl("Algoritm", 1) -> "https://media.negahestan.com/ipnx/media/series/episodes/thumbs/Algoritm_01_thumb.jpg"
     */
    fun buildEpisodeThumbnailUrl(seriesName: String, episodeNumber: Int): String {
        val episodeNum = episodeNumber.toString().padStart(2, '0')
        return "$BASE_CDN/series/episodes/thumbs/${seriesName}_${episodeNum}_thumb.jpg"
    }

    /**
     * Build thumbnail URL for a movie
     *
     * @param movieName Movie name in title case
     * @return Thumbnail URL
     *
     * Example:
     * buildMovieThumbnailUrl("Pirpesar") -> "https://media.negahestan.com/ipnx/media/movies/thumbs/Pirpesar_thumb.jpg"
     */
    fun buildMovieThumbnailUrl(movieName: String): String {
        return "$BASE_CDN/movies/thumbs/${movieName}_thumb.jpg"
    }

    /**
     * Format series slug to CDN naming convention
     * Converts "algoritm" -> "Algoritm"
     * Converts "bamdaade-khomaar" -> "Bamdaade_Khomaar"
     *
     * @param slug Series slug from URL
     * @return Formatted series name for CDN
     */
    fun formatSeriesName(slug: String): String {
        return slug.split("-").joinToString("_") { word ->
            word.replaceFirstChar { it.uppercase() }
        }
    }

    /**
     * Format movie slug to CDN naming convention
     * Converts "pir-pesar" -> "PirPesar"
     * Removes dashes and capitalizes each word
     *
     * @param slug Movie slug from URL
     * @return Formatted movie name for CDN
     */
    fun formatMovieName(slug: String): String {
        return slug.split("-").joinToString("") { word ->
            word.replaceFirstChar { it.uppercase() }
        }
    }

    /**
     * Build category page URL
     *
     * @param category Category name (e.g., "best-serial", "best-movies", "show")
     * @return Full URL to category page
     */
    fun buildCategoryUrl(category: String): String {
        return "$BASE_SITE/$category"
    }

    /**
     * Build series detail page URL
     *
     * @param seriesSlug Series slug
     * @return Full URL to series page
     */
    fun buildSeriesPageUrl(seriesSlug: String): String {
        return "$BASE_SITE/shows/$seriesSlug"
    }

    /**
     * Build episode detail page URL
     *
     * @param seriesSlug Series slug
     * @param episodeSlug Episode slug
     * @return Full URL to episode page
     */
    fun buildEpisodePageUrl(seriesSlug: String, episodeSlug: String): String {
        return "$BASE_SITE/shows/$seriesSlug/episodes/$episodeSlug"
    }

    /**
     * Build movie detail page URL
     *
     * @param genre Movie genre slug
     * @param movieSlug Movie slug
     * @return Full URL to movie page
     */
    fun buildMoviePageUrl(genre: String, movieSlug: String): String {
        return "$BASE_SITE/best-1-movies/$genre/$movieSlug"
    }

    /**
     * Build live TV channel page URL
     *
     * @param channelName Channel name
     * @param clientIp Client IP address (required for GLWiz)
     * @return Full URL to live TV page
     */
    fun buildLiveTvPageUrl(channelName: String, clientIp: String): String {
        return "$BASE_SITE/livetv/$channelName/$clientIp"
    }
}
