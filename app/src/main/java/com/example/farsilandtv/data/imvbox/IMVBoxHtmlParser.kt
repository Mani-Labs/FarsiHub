package com.example.farsilandtv.data.imvbox

import android.util.Log
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * HTML parser for IMVBox.com pages
 *
 * Parses various page types:
 * 1. Movie/Series list pages (grid cards)
 * 2. Movie/Show detail pages (metadata, cast)
 * 3. Play pages (HLS video URL extraction)
 * 4. Search results (HTML fragments from AJAX)
 *
 * Uses JSoup for HTML parsing.
 * Also extracts JSON-LD structured data when available.
 */
object IMVBoxHtmlParser {

    private const val TAG = "IMVBoxHtmlParser"

    /**
     * Data class for parsed movie card from list page
     */
    data class MovieCard(
        val title: String,
        val titleFarsi: String?,
        val slug: String,
        val posterUrl: String?,
        val year: Int?,
        val rating: Float?
    )

    /**
     * Data class for parsed series card from list page
     */
    data class SeriesCard(
        val title: String,
        val titleFarsi: String?,
        val slug: String,
        val posterUrl: String?,
        val year: Int?,
        val rating: Float?,
        val seasonCount: Int?
    )

    /**
     * Data class for parsed movie metadata from detail page
     */
    data class MovieMetadata(
        val title: String,
        val titleFarsi: String?,
        val description: String?,
        val year: Int?,
        val duration: Int?, // in minutes
        val rating: Float?,
        val genres: List<String>,
        val posterUrl: String?,
        val thumbnailUrl: String?,
        val hlsUrl: String?,
        val mediaId: String?
    )

    /**
     * Data class for parsed episode from series page
     */
    data class EpisodeItem(
        val title: String,
        val season: Int,
        val episode: Int,
        val thumbnailUrl: String?,
        val duration: Int? // in minutes
    )

    /**
     * Data class for search result item
     */
    data class SearchResult(
        val title: String,
        val type: String, // "movie", "series", "cast"
        val slug: String,
        val thumbnailUrl: String?,
        val url: String
    )

    /**
     * Parse movie cards from movies list page
     *
     * @param doc Jsoup Document of movies list page
     * @return List of MovieCard objects
     */
    fun parseMovieCards(doc: Document): List<MovieCard> {
        val cards = mutableListOf<MovieCard>()

        // IMVBox uses Bootstrap card structure
        val selectors = listOf(
            "div.card",
            ".card",
            "div.movie-card",
            "div.movie-col .card"
        )

        for (selector in selectors) {
            val elements = doc.select(selector)
            if (elements.isNotEmpty()) {
                Log.d(TAG, "Found ${elements.size} movie cards with selector: $selector")
                for (element in elements) {
                    parseMovieCard(element)?.let { cards.add(it) }
                }
                break
            }
        }

        return cards
    }

    /**
     * Parse single movie card element
     */
    private fun parseMovieCard(element: Element): MovieCard? {
        return try {
            // Extract link and slug
            val link = element.selectFirst("a[href*=/movies/]")?.attr("href")
                ?: element.selectFirst("a")?.attr("href")
                ?: return null

            val slug = link.trimEnd('/').substringAfterLast("/")
                .takeIf { it.isNotBlank() && !it.startsWith("movies") }
                ?: return null

            // Extract title - IMVBox uses .card-title or h5
            val title = element.selectFirst(".card-title, h5, h4, h3, h2, .title")?.text()?.trim()
                ?: element.selectFirst("a[title]")?.attr("title")?.let { 
                    // Extract from "Watch 'Movie Name' movie" format
                    Regex("'([^']+)'").find(it)?.groupValues?.getOrNull(1)
                }
                ?: return null

            // Extract Farsi title if available
            val titleFarsi = element.selectFirst(".card-text, .title-fa")?.text()?.trim()

            // Extract poster URL - IMVBox uses picture > data-img for lazy loading
            val posterUrl = element.selectFirst("picture data-img")?.attr("src")?.takeIf { it.isNotBlank() }
                ?: element.selectFirst("picture img")?.let { img ->
                    img.attr("data-src").takeIf { it.isNotBlank() }
                        ?: img.attr("src").takeIf { it.isNotBlank() }
                }
                ?: element.selectFirst(".card-img-top, img")?.let { img ->
                    img.attr("data-src").takeIf { it.isNotBlank() }
                        ?: img.attr("src").takeIf { it.isNotBlank() }
                }

            // Extract year
            val yearText = element.selectFirst(".year, span.year")?.text()
            val year = yearText?.let { extractNumber(it) }

            // Extract rating
            val ratingText = element.selectFirst(".rating, .imdb-rate")?.text()
            val rating = ratingText?.toFloatOrNull()

            MovieCard(
                title = title,
                titleFarsi = titleFarsi,
                slug = slug,
                posterUrl = posterUrl,
                year = year,
                rating = rating
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing movie card", e)
            null
        }
    }

    /**
     * Parse series cards from TV series list page
     *
     * @param doc Jsoup Document of series list page
     * @return List of SeriesCard objects
     */
    fun parseSeriesCards(doc: Document): List<SeriesCard> {
        val cards = mutableListOf<SeriesCard>()

        // IMVBox uses Bootstrap card structure for series too
        val selectors = listOf(
            "div.card",
            ".card",
            "div.series-card",
            "div.movie-col .card"
        )

        for (selector in selectors) {
            val elements = doc.select(selector)
            if (elements.isNotEmpty()) {
                Log.d(TAG, "Found ${elements.size} series cards with selector: $selector")
                for (element in elements) {
                    parseSeriesCard(element)?.let { cards.add(it) }
                }
                break
            }
        }

        return cards
    }

    /**
     * Parse single series card element
     */
    private fun parseSeriesCard(element: Element): SeriesCard? {
        return try {
            val link = element.selectFirst("a[href*=/shows/]")?.attr("href")
                ?: element.selectFirst("a")?.attr("href")
                ?: return null

            val slug = link.trimEnd('/').substringAfterLast("/")
                .takeIf { it.isNotBlank() && !it.startsWith("shows") }
                ?: return null

            // Extract title - IMVBox uses .card-title
            val title = element.selectFirst(".card-title, h5, h4, h3, .title")?.text()?.trim()
                ?: element.selectFirst("a[title]")?.attr("title")?.let { 
                    Regex("'([^']+)'").find(it)?.groupValues?.getOrNull(1)
                }
                ?: return null

            val titleFarsi = element.selectFirst(".card-text, .title-fa")?.text()?.trim()

            // Extract poster URL - IMVBox uses picture > data-img for lazy loading
            val posterUrl = element.selectFirst("picture data-img")?.attr("src")?.takeIf { it.isNotBlank() }
                ?: element.selectFirst("picture img")?.let { img ->
                    img.attr("data-src").takeIf { it.isNotBlank() }
                        ?: img.attr("src").takeIf { it.isNotBlank() }
                }
                ?: element.selectFirst(".card-img-top, img")?.let { img ->
                    img.attr("data-src").takeIf { it.isNotBlank() }
                        ?: img.attr("src").takeIf { it.isNotBlank() }
                }

            val yearText = element.selectFirst(".year, span.year")?.text()
            val year = yearText?.let { extractNumber(it) }

            val ratingText = element.selectFirst(".rating")?.text()
            val rating = ratingText?.toFloatOrNull()

            val seasonText = element.selectFirst(".seasons")?.text()
            val seasonCount = seasonText?.let { extractNumber(it) }

            SeriesCard(
                title = title,
                titleFarsi = titleFarsi,
                slug = slug,
                posterUrl = posterUrl,
                year = year,
                rating = rating,
                seasonCount = seasonCount
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing series card", e)
            null
        }
    }

    /**
     * Parse movie metadata from movie detail page
     *
     * @param doc Jsoup Document of movie detail page
     * @return MovieMetadata object or null
     */
    fun parseMovieMetadata(doc: Document): MovieMetadata? {
        return try {
            // Try JSON-LD first (most reliable)
            val jsonLd = extractJsonLd(doc)

            // Extract title from h1 or meta tags
            val title = doc.selectFirst("h1")?.text()?.trim()
                ?: doc.selectFirst("meta[property=og:title]")?.attr("content")
                ?: return null

            // Title may be "English Name | Farsi Name"
            val titleParts = title.split("|", limit = 2)
            val titleEnglish = titleParts.getOrNull(0)?.trim() ?: title
            val titleFarsi = titleParts.getOrNull(1)?.trim()

            // Description
            val description = doc.selectFirst("meta[property=og:description]")?.attr("content")
                ?: doc.selectFirst("div.description, .synopsis")?.text()

            // Year
            val yearText = doc.selectFirst(".year, span.year")?.text()
            val year = extractNumber(yearText ?: "")

            // Duration from JSON-LD (format: "PT1H37M0S")
            val durationIso = jsonLd?.let { extractJsonValue(it, "duration") }
            val duration = parseDuration(durationIso)

            // Rating
            val ratingText = doc.selectFirst(".rating-value, .imdb-rate")?.text()
            val rating = ratingText?.toFloatOrNull()

            // Genres
            val genres = doc.select("a[href*=/genre/]").map { it.text().trim() }

            // Poster and thumbnail
            val posterUrl = doc.selectFirst("meta[property=og:image]")?.attr("content")
            val thumbnailUrl = jsonLd?.let { extractJsonValue(it, "thumbnailUrl") }

            MovieMetadata(
                title = titleEnglish,
                titleFarsi = titleFarsi,
                description = description,
                year = year,
                duration = duration,
                rating = rating,
                genres = genres,
                posterUrl = posterUrl,
                thumbnailUrl = thumbnailUrl,
                hlsUrl = null, // Extracted from play page
                mediaId = null
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing movie metadata", e)
            null
        }
    }

    /**
     * Extract HLS video URL from play page
     *
     * IMVBox pages contain multiple video sources:
     * - Main movie content (what we want) - usually FIRST
     * - Trailers (/media/trailers/ path) - should skip
     *
     * @param doc Jsoup Document of play page
     * @return HLS stream URL or null
     */
    fun extractHlsUrl(doc: Document): String? {
        val allHlsUrls = mutableListOf<String>()

        // Method 1: Look for ALL source tags with m3u8
        val sourceElements = doc.select("source[src*=.m3u8]")
        for (source in sourceElements) {
            val url = source.attr("src")
            if (url.isNotBlank()) {
                allHlsUrls.add(url)
                Log.d(TAG, "Found HLS URL in source tag: $url")
            }
        }

        // Method 2: Look for streaming.imvbox.com in script tags
        val scripts = doc.select("script").html()
        val hlsRegex = Regex("""https://streaming\.imvbox\.com/media/\d+/\d+\.m3u8""")
        hlsRegex.findAll(scripts).forEach { match ->
            if (match.value !in allHlsUrls) {
                allHlsUrls.add(match.value)
                Log.d(TAG, "Found HLS URL in script: ${match.value}")
            }
        }

        // Method 3: Look for data-source or similar attributes
        val dataSources = doc.select("[data-source*=.m3u8]")
        for (element in dataSources) {
            val url = element.attr("data-source")
            if (url.isNotBlank() && url !in allHlsUrls) {
                allHlsUrls.add(url)
                Log.d(TAG, "Found HLS URL in data-source: $url")
            }
        }

        if (allHlsUrls.isEmpty()) {
            Log.w(TAG, "No HLS URL found in play page")
            return null
        }

        Log.d(TAG, "Found ${allHlsUrls.size} total HLS URLs")

        // Known intro/ad media IDs that play before all movies
        // These are NOT the actual movie content - must be filtered out
        val introMediaIds = setOf("3628")

        // Filter out trailers and intros
        val validUrls = allHlsUrls.filter { url ->
            // Skip trailers (have /trailers/ in path)
            if (url.contains("/trailers/")) {
                Log.d(TAG, "Skipping trailer URL: $url")
                return@filter false
            }

            // Skip known intro/ad videos
            val mediaId = IMVBoxUrlBuilder.extractMediaId(url)
            if (mediaId != null && mediaId in introMediaIds) {
                Log.d(TAG, "Skipping intro video (media ID $mediaId): $url")
                return@filter false
            }

            true
        }

        // BUG FIX: Use firstOrNull() for defensive coding
        // Return the FIRST valid URL (main movie content is usually first)
        val mainUrl = validUrls.firstOrNull()
        if (mainUrl == null) {
            Log.w(TAG, "All URLs are trailers - no full movie available")
            return null
        }
        Log.d(TAG, "Selected main HLS URL: $mainUrl (from ${validUrls.size} valid URLs)")
        return mainUrl
    }

    /**
     * Extract media ID from play page
     *
     * @param doc Jsoup Document of play page
     * @return Numeric media ID or null
     */
    fun extractMediaId(doc: Document): String? {
        // Look for media ID in various places
        val hlsUrl = extractHlsUrl(doc)
        if (hlsUrl != null) {
            return IMVBoxUrlBuilder.extractMediaId(hlsUrl)
        }

        // Look for data-id attributes
        val dataId = doc.selectFirst("[data-id]")?.attr("data-id")
        if (!dataId.isNullOrBlank() && dataId.all { it.isDigit() }) {
            return dataId
        }

        return null
    }

    /**
     * Check if play page requires IMVBox Plus subscription
     *
     * Only returns true if content is FULLY behind paywall (no video player at all).
     * Free movies may have "Watch With Subtitles" upgrade links for premium subtitles,
     * but the video itself plays without subscription.
     *
     * @param doc Jsoup Document of play page
     * @return true if content requires subscription (no video available)
     */
    fun requiresSubscription(doc: Document): Boolean {
        // First check if there's ANY video player or video content
        // If there's a video player, the movie is playable (even if subtitles require subscription)
        val hasVideoPlayer = doc.selectFirst("video, .video-js, #player, [class*=player]") != null
        val hasIframe = doc.selectFirst("iframe") != null
        val hasHlsSource = doc.select("source[src*=.m3u8]").isNotEmpty()

        if (hasVideoPlayer || hasIframe || hasHlsSource) {
            Log.d(TAG, "Video player detected - content is playable (hasPlayer=$hasVideoPlayer, hasIframe=$hasIframe, hasHls=$hasHlsSource)")
            return false
        }

        // No video player found - check if there's an upgrade prompt
        val upgradeLink = doc.selectFirst("a[href*=upgrade], a[href*=planform]")
        if (upgradeLink != null) {
            val text = upgradeLink.text().lowercase()
            // Only "Become a Plus Member" indicates full paywall
            // "Watch With Subtitles" is just for premium subtitles on free movies
            if (text.contains("become") && text.contains("plus") || text.contains("become") && text.contains("member")) {
                Log.d(TAG, "Subscription required: no video player and upgrade prompt found: ${upgradeLink.text()}")
                return true
            }
        }

        // Check for explicit "Become a Plus Member" text (main paywall indicator)
        val bodyText = doc.body().text().lowercase()
        if (bodyText.contains("become a plus member")) {
            Log.d(TAG, "Subscription required: 'Become a Plus Member' text found with no video player")
            return true
        }

        return false
    }

    /**
     * Extract YouTube video ID from play page (if present in static HTML)
     *
     * IMVBox uses video.js with YouTube tech. The YouTube URL is embedded in
     * the data-setup attribute of the <video-js> element as JSON config:
     * data-setup='{"sources": [{"type": "video/youtube", "src": "https://www.youtube.com/embed/VIDEO_ID"}]}'
     *
     * @param doc Jsoup Document of play page
     * @return YouTube video ID or null
     */
    fun extractYouTubeVideoId(doc: Document): String? {
        // Method 1: Look for data-setup attribute on video-js element (PRIMARY METHOD)
        // This is where IMVBox stores the YouTube URL in static HTML
        val videoJsElement = doc.selectFirst("video-js[data-setup], #player[data-setup]")
        if (videoJsElement != null) {
            val dataSetup = videoJsElement.attr("data-setup")
            if (dataSetup.contains("youtube")) {
                Log.d(TAG, "Found data-setup with YouTube config")
                // Extract YouTube URL from JSON: "src": "https://www.youtube.com/embed/VIDEO_ID"
                val ytEmbedRegex = Regex("""youtube\.com/embed/([a-zA-Z0-9_-]{11})""")
                ytEmbedRegex.find(dataSetup)?.let { match ->
                    val videoId = match.groupValues[1]
                    Log.d(TAG, "Found YouTube video ID in data-setup: $videoId")
                    return videoId
                }
            }
        }

        // Method 2: Look for YouTube iframe in static HTML
        val ytIframe = doc.selectFirst("iframe[src*=youtube.com/embed]")
        if (ytIframe != null) {
            val src = ytIframe.attr("src")
            val videoId = Regex("""youtube\.com/embed/([a-zA-Z0-9_-]{11})""").find(src)?.groupValues?.getOrNull(1)
            if (videoId != null) {
                Log.d(TAG, "Found YouTube video ID in iframe: $videoId")
                return videoId
            }
        }

        // Method 3: Look for YouTube embed URL in script tags
        val scripts = doc.select("script").html()
        val ytEmbedRegex = Regex("""youtube\.com/embed/([a-zA-Z0-9_-]{11})""")
        ytEmbedRegex.find(scripts)?.let { match ->
            val videoId = match.groupValues[1]
            Log.d(TAG, "Found YouTube video ID in script: $videoId")
            return videoId
        }

        // Method 4: Look for YouTube watch URLs
        val ytWatchRegex = Regex("""youtube\.com/watch\?v=([a-zA-Z0-9_-]{11})""")
        ytWatchRegex.find(scripts)?.let { match ->
            val videoId = match.groupValues[1]
            Log.d(TAG, "Found YouTube video ID (watch URL) in script: $videoId")
            return videoId
        }

        // Method 5: Look for data attributes with YouTube ID
        val ytDataAttr = doc.selectFirst("[data-youtube-id], [data-video-id]")
        if (ytDataAttr != null) {
            val videoId = ytDataAttr.attr("data-youtube-id").takeIf { it.isNotBlank() }
                ?: ytDataAttr.attr("data-video-id").takeIf { it.isNotBlank() }
            if (videoId != null && videoId.length == 11) {
                Log.d(TAG, "Found YouTube video ID in data attribute: $videoId")
                return videoId
            }
        }

        Log.d(TAG, "No YouTube video ID found in static HTML")
        return null
    }

    /**
     * Parse episode list from show season page
     * IMVBox structure: Episodes are in a grid with links like /{slug}-episode-{n}
     *
     * @param doc Jsoup Document of season page
     * @param season Season number
     * @return List of EpisodeItem objects
     */
    // Pre-compiled regex for episode URL patterns (performance optimization)
    private val EPISODE_URL_PATTERN = Regex("""episod+e-(\d+)""", RegexOption.IGNORE_CASE)

    fun parseEpisodeList(doc: Document, season: Int): List<EpisodeItem> {
        val episodes = mutableListOf<EpisodeItem>()
        val episodeMap = mutableMapOf<Int, EpisodeItem>()

        // METHOD 1: Container-based parsing (faster, more reliable for thumbnails)
        // Look for episode container divs which contain both link and image
        val episodeContainers = doc.select("div.episode-poster-cover, div.episode-item, div.episode-card")

        if (episodeContainers.isNotEmpty()) {
            Log.d(TAG, "Found ${episodeContainers.size} episode containers (container method)")

            for (container in episodeContainers) {
                // Get episode link - be flexible with URL patterns
                val link = container.selectFirst("a[href*=episode-], a[href*=episod]") ?: continue
                val href = link.attr("href")

                // Extract episode number
                val episodeNum = EPISODE_URL_PATTERN.find(href)?.groupValues?.getOrNull(1)?.toIntOrNull()
                    ?: continue

                // Get thumbnail - try multiple methods in order of reliability
                val thumbnailUrl = extractThumbnailFromContainer(container)

                // Get title from descriptions div
                val title = container.selectFirst(".descriptions .title, .title")?.text()?.trim()
                    ?: "Episode $episodeNum"

                // Get duration from descriptions div
                val durationText = container.selectFirst(".descriptions .duration, .duration")?.text()
                val duration = durationText?.let { extractMinutes(it) }

                // Debug log for thumbnail extraction
                Log.d(TAG, "Episode $episodeNum thumbnail: $thumbnailUrl")

                val newEpisode = EpisodeItem(
                    title = title,
                    season = season,
                    episode = episodeNum,
                    thumbnailUrl = thumbnailUrl,
                    duration = duration
                )

                // Keep episode with thumbnail, or first occurrence if neither has one
                val existing = episodeMap[episodeNum]
                if (existing == null || (existing.thumbnailUrl == null && thumbnailUrl != null)) {
                    episodeMap[episodeNum] = newEpisode
                }
            }
        }

        // METHOD 2: Link-based parsing (fallback for different page structures)
        if (episodeMap.isEmpty()) {
            // Limit to episodes-container area to avoid scanning entire page
            val episodesArea = doc.selectFirst(".episodes-container, .tab-content[data-tab=episodes]") ?: doc
            val episodeLinks = episodesArea.select("a[href*=episod]").filter { element ->
                EPISODE_URL_PATTERN.containsMatchIn(element.attr("href"))
            }

            if (episodeLinks.isNotEmpty()) {
                Log.d(TAG, "Found ${episodeLinks.size} episode links (link method)")

                for (element in episodeLinks) {
                    val href = element.attr("href")

                    // Extract episode number - handle various patterns: episode-1, episoddde-01, etc.
                    val episodeNum = EPISODE_URL_PATTERN.find(href)?.groupValues?.getOrNull(1)?.toIntOrNull()
                        ?: continue

                    // Find the parent container that has more info
                    val container = element.parent()

                    // Extract title - look for "Episode X" text in siblings
                    val title = container?.selectFirst("div:contains(Episode)")?.text()?.trim()
                        ?: element.attr("title")?.takeIf { it.isNotBlank() }
                        ?: "Episode $episodeNum"

                    // Extract thumbnail - IMVBox uses various image structures
                    val thumbnailUrl = extractThumbnailUrl(element, container)

                    // Extract duration - look for "XX min" pattern
                    val durationText = container?.select("div")?.find {
                        it.text().matches(Regex("""\d+\s*min"""))
                    }?.text()
                    val duration = durationText?.let { extractMinutes(it) }

                    val newEpisode = EpisodeItem(
                        title = title,
                        season = season,
                        episode = episodeNum,
                        thumbnailUrl = thumbnailUrl,
                        duration = duration
                    )

                    // Keep episode with thumbnail, or first occurrence if neither has one
                    val existing = episodeMap[episodeNum]
                    if (existing == null || (existing.thumbnailUrl == null && thumbnailUrl != null)) {
                        episodeMap[episodeNum] = newEpisode
                    }
                }
            }
        }

        // METHOD 3: Old selectors fallback
        if (episodeMap.isEmpty()) {
            Log.w(TAG, "No episode links found with episode pattern, trying fallback selectors")

            val fallbackSelectors = listOf(
                "div.episode-item",
                "div.episode-card",
                "li.episode"
            )

            for (selector in fallbackSelectors) {
                val elements = doc.select(selector)
                if (elements.isNotEmpty()) {
                    Log.d(TAG, "Found ${elements.size} episodes with fallback selector: $selector")
                    for ((index, element) in elements.withIndex()) {
                        parseEpisodeItemFallback(element, season, index + 1)?.let {
                            episodeMap[it.episode] = it
                        }
                    }
                    break
                }
            }
        }

        episodes.addAll(episodeMap.values)
        // Sort by episode number
        episodes.sortBy { it.episode }
        Log.d(TAG, "Parsed ${episodes.size} unique episodes for season $season")

        return episodes
    }

    /**
     * Extract thumbnail from episode container - optimized for IMVBox structure
     * Handles: <picture><data-img src="..."></picture> and regular img tags
     */
    private fun extractThumbnailFromContainer(container: Element): String? {
        // Method 1: Direct selector for data-img element (IMVBox custom element)
        container.selectFirst("data-img")?.let { dataImg ->
            dataImg.attr("src").takeIf { it.isNotBlank() }?.let {
                Log.d(TAG, "  -> Found data-img (direct): $it")
                return it
            }
        }

        // Method 2: Iterate through picture children by tag name
        container.select("picture").firstOrNull()?.children()?.forEach { child ->
            if (child.tagName().equals("data-img", ignoreCase = true)) {
                child.attr("src").takeIf { it.isNotBlank() }?.let {
                    Log.d(TAG, "  -> Found data-img (iterate): $it")
                    return it
                }
            }
        }

        // Method 3: <picture> with <data-src> having srcset attribute
        container.selectFirst("data-src")?.let { dataSrc ->
            val srcset = dataSrc.attr("srcset")
            if (srcset.isNotBlank()) {
                val url = srcset.split(",").firstOrNull()?.trim()?.split(" ")?.firstOrNull()
                if (!url.isNullOrBlank()) {
                    Log.d(TAG, "  -> Found data-src srcset: $url")
                    return url
                }
            }
        }

        // Method 4: Regular img tag with data-src (lazy loading)
        container.selectFirst("img")?.let { img ->
            img.attr("data-src").takeIf { it.isNotBlank() }?.let {
                Log.d(TAG, "  -> Found img data-src: $it")
                return it
            }
            img.attr("src").takeIf { it.isNotBlank() && !it.contains("placeholder") }?.let {
                Log.d(TAG, "  -> Found img src: $it")
                return it
            }
        }

        Log.d(TAG, "  -> No thumbnail found in container")
        return null
    }

    /**
     * Parse single episode item (fallback method)
     */
    private fun parseEpisodeItemFallback(element: Element, season: Int, defaultEpisode: Int): EpisodeItem? {
        return try {
            // Extract episode number from URL or text
            val link = element.attr("href").takeIf { it.isNotBlank() }
                ?: element.selectFirst("a")?.attr("href")
                ?: ""

            val episodeNum = Regex("""-episode-(\d+)""").find(link)?.groupValues?.getOrNull(1)?.toIntOrNull()
                ?: Regex("""episode[- ]?(\d+)""", RegexOption.IGNORE_CASE).find(element.text())?.groupValues?.getOrNull(1)?.toIntOrNull()
                ?: defaultEpisode

            // Extract title
            val title = element.selectFirst(".title, h3, span.ep-title")?.text()?.trim()
                ?: "Episode $episodeNum"

            // Extract thumbnail using robust helper
            val thumbnailUrl = extractThumbnailUrl(element, element.parent())

            // Extract duration
            val durationText = element.selectFirst(".duration, .ep-duration")?.text()
            val duration = durationText?.let { extractMinutes(it) }

            EpisodeItem(
                title = title,
                season = season,
                episode = episodeNum,
                thumbnailUrl = thumbnailUrl,
                duration = duration
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing episode item", e)
            null
        }
    }

    /**
     * Extract thumbnail URL from element - handles IMVBox's various image structures:
     * 1. Regular <img> tags with src or data-src
     * 2. <picture> elements with <data-img> or <data-src> children
     * 3. Lazy-loaded images with srcset attributes
     */
    private fun extractThumbnailUrl(element: Element, container: Element?): String? {
        // Try multiple extraction methods in order of reliability

        // Method 1: Direct data-img selector (IMVBox custom element)
        element.selectFirst("data-img")?.let { dataImg ->
            dataImg.attr("src").takeIf { it.isNotBlank() }?.let { return it }
        }

        // Method 2: Regular img tag inside element
        element.selectFirst("img")?.let { img ->
            img.attr("data-src").takeIf { it.isNotBlank() }?.let { return it }
            img.attr("src").takeIf { it.isNotBlank() && !it.contains("placeholder") }?.let { return it }
        }

        // Method 3: <data-src> with srcset
        element.selectFirst("data-src")?.let { dataSrc ->
            val srcset = dataSrc.attr("srcset")
            if (srcset.isNotBlank()) {
                return srcset.split(",").firstOrNull()?.trim()?.split(" ")?.firstOrNull()
            }
        }

        // Method 4: Look in parent container
        container?.let { parent ->
            parent.selectFirst("data-img")?.let { dataImg ->
                dataImg.attr("src").takeIf { it.isNotBlank() }?.let { return it }
            }
            parent.selectFirst("img")?.let { img ->
                img.attr("data-src").takeIf { it.isNotBlank() }?.let { return it }
                img.attr("src").takeIf { it.isNotBlank() && !it.contains("placeholder") }?.let { return it }
            }
            parent.selectFirst("data-src")?.let { dataSrc ->
                val srcset = dataSrc.attr("srcset")
                if (srcset.isNotBlank()) {
                    return srcset.split(",").firstOrNull()?.trim()?.split(" ")?.firstOrNull()
                }
            }
        }

        // Method 5: Any image in siblings (episode card structure)
        element.siblingElements().forEach { sibling ->
            sibling.selectFirst("data-img")?.attr("src")?.takeIf { it.isNotBlank() }?.let { return it }
            sibling.selectFirst("img")?.let { img ->
                img.attr("data-src").takeIf { it.isNotBlank() }?.let { return it }
                img.attr("src").takeIf { it.isNotBlank() && !it.contains("placeholder") }?.let { return it }
            }
        }

        return null
    }

    /**
     * Parse search results from full search page HTML
     * Only extracts movies and series (skips cast)
     *
     * @param html Full HTML page from search
     * @return List of SearchResult objects (movies and series only)
     */
    fun parseSearchResults(html: String): List<SearchResult> {
        val results = mutableListOf<SearchResult>()
        val seenUrls = mutableSetOf<String>()

        try {
            val doc = org.jsoup.Jsoup.parse(html)
            Log.d(TAG, "parseSearchResults: HTML length=${html.length}")

            // Method 1: Parse card elements (mobile view structure)
            // Structure: .card > .card-image > a[href] > img.card-img-top
            //            .card > .card-body > .text > a > h5.card-title
            val cards = doc.select("div.card")
            Log.d(TAG, "parseSearchResults: Found ${cards.size} div.card elements")
            for (card in cards) {
                val link = card.selectFirst("a[href*=/movies/], a[href*=/shows/]") ?: continue
                val url = link.attr("href")

                // Skip if already seen (avoid duplicates from mobile/desktop views)
                if (url in seenUrls) continue
                seenUrls.add(url)

                val title = card.selectFirst(".card-title, h5")?.text()?.trim()
                    ?: link.attr("title")?.trim()
                    ?: continue

                val type = when {
                    url.contains("/movies/") -> "movie"
                    url.contains("/shows/") -> "series"
                    else -> continue
                }

                // Get poster from card-img-top - handle both regular img and lazy-loaded picture
                val thumbnail = card.selectFirst("img.card-img-top, img")?.attr("src")?.takeIf { it.isNotBlank() }
                    ?: card.selectFirst("picture.card-img-top data-img")?.attr("src")?.takeIf { it.isNotBlank() }
                    ?: card.selectFirst("picture data-img")?.attr("src")?.takeIf { it.isNotBlank() }
                Log.d(TAG, "Search result: $title -> poster=$thumbnail")

                val slug = url.trimEnd('/').substringAfterLast("/")

                results.add(
                    SearchResult(
                        title = title,
                        type = type,
                        slug = slug,
                        thumbnailUrl = thumbnail,
                        url = if (url.startsWith("http")) url else "${IMVBoxUrlBuilder.BASE_URL}$url"
                    )
                )
            }

            // Method 2: Parse movie-cover elements (desktop view structure)
            // Structure: .movie-cover > img + .descriptions > a[href] + .title
            val covers = doc.select("div.movie-cover")
            for (cover in covers) {
                val link = cover.selectFirst("a[href*=/movies/], a[href*=/shows/]") ?: continue
                val url = link.attr("href")

                // Skip if already seen
                if (url in seenUrls) continue
                seenUrls.add(url)

                val title = cover.selectFirst(".title")?.text()?.trim() ?: continue

                val type = when {
                    url.contains("/movies/") -> "movie"
                    url.contains("/shows/") -> "series"
                    else -> continue
                }

                // Get thumbnail from the cover's img - handle lazy-loaded picture too
                val thumbnail = cover.selectFirst("img")?.attr("src")?.takeIf { it.isNotBlank() }
                    ?: cover.selectFirst("picture data-img")?.attr("src")?.takeIf { it.isNotBlank() }

                val slug = url.trimEnd('/').substringAfterLast("/")

                results.add(
                    SearchResult(
                        title = title,
                        type = type,
                        slug = slug,
                        thumbnailUrl = thumbnail,
                        url = if (url.startsWith("http")) url else "${IMVBoxUrlBuilder.BASE_URL}$url"
                    )
                )
            }

            Log.d(TAG, "Parsed ${results.size} search results (movies/series only)")
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing search results", e)
        }

        return results
    }

    /**
     * Extract CSRF token from page
     *
     * @param doc Jsoup Document
     * @return CSRF token or null
     */
    fun extractCsrfToken(doc: Document): String? {
        return doc.selectFirst("meta[name=csrf-token]")?.attr("content")
    }

    /**
     * Extract JSON-LD structured data from page
     *
     * @param doc Jsoup Document
     * @return JSON-LD string or null
     */
    private fun extractJsonLd(doc: Document): String? {
        return doc.selectFirst("script[type=application/ld+json]")?.html()
    }

    /**
     * Extract value from JSON-LD string using simple regex
     * (Avoids adding JSON library dependency)
     */
    private fun extractJsonValue(json: String, key: String): String? {
        val regex = Regex(""""$key"\s*:\s*"([^"]+)"""")
        return regex.find(json)?.groupValues?.getOrNull(1)
    }

    /**
     * Parse ISO 8601 duration (e.g., "PT1H37M0S") to minutes
     */
    private fun parseDuration(iso: String?): Int? {
        if (iso.isNullOrBlank()) return null

        try {
            var minutes = 0
            val hourMatch = Regex("""(\d+)H""").find(iso)
            val minMatch = Regex("""(\d+)M""").find(iso)

            hourMatch?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { minutes += it * 60 }
            minMatch?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { minutes += it }

            return if (minutes > 0) minutes else null
        } catch (e: Exception) {
            return null
        }
    }

    /**
     * Extract minutes from duration text (e.g., "49 min", "1h 30m")
     */
    private fun extractMinutes(text: String): Int? {
        val hourMatch = Regex("""(\d+)\s*h""", RegexOption.IGNORE_CASE).find(text)
        val minMatch = Regex("""(\d+)\s*(?:min|m)""", RegexOption.IGNORE_CASE).find(text)

        var minutes = 0
        hourMatch?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { minutes += it * 60 }
        minMatch?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { minutes += it }

        return if (minutes > 0) minutes else null
    }

    /**
     * Extract first number from text
     */
    private fun extractNumber(text: String): Int? {
        return Regex("""\d+""").find(text)?.value?.toIntOrNull()
    }
}
