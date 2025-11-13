package com.example.farsilandtv.data.namakade

import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * HTML parser for Namakade.com pages
 *
 * Parses various page types:
 * 1. Category pages (series list, movie list)
 * 2. Series detail pages (episode list)
 * 3. Episode/Movie pages (video URLs)
 *
 * NO REST API available - all content must be scraped from HTML
 */
object NamakadeHtmlParser {

    /**
     * Data class for parsed series card from category page
     */
    data class SeriesCard(
        val title: String,
        val slug: String,
        val thumbnailUrl: String?,
        val genre: String?,
        val totalEpisodes: Int
    )

    /**
     * Data class for parsed movie card from category page
     */
    data class MovieCard(
        val title: String,
        val slug: String,
        val genre: String?,
        val thumbnailUrl: String?,
        val director: String?
    )

    /**
     * Data class for parsed episode item from series page
     */
    data class EpisodeItem(
        val episodeNumber: Int,
        val season: Int,
        val title: String,
        val episodeSlug: String,
        val thumbnailUrl: String?
    )

    /**
     * Parse series cards from category page
     *
     * HTML Structure:
     * <div class="series-card">
     *   <a href="/serieses/{slug}">
     *     <img src="...{SeriesName}_smallthumb.jpg">
     *     <div class="title">{Series Title}</div>
     *     <div class="episodes">Episodes: {count}</div>
     *     <div class="genre">Genre: {genre}</div>
     *   </a>
     * </div>
     *
     * @param doc Jsoup Document of category page
     * @return List of SeriesCard objects
     */
    fun parseSeriesCards(doc: Document): List<SeriesCard> {
        val cards = mutableListOf<SeriesCard>()

        // Try multiple selector patterns to find series cards
        val selectors = listOf(
            "div.series-card",
            "div.movie-card",
            "article.series",
            "div.item"
        )

        for (selector in selectors) {
            val elements = doc.select(selector)
            if (elements.isNotEmpty()) {
                for (element in elements) {
                    val card = parseSeriesCard(element)
                    if (card != null) {
                        cards.add(card)
                    }
                }
                break // Found working selector
            }
        }

        return cards
    }

    /**
     * Parse single series card element
     */
    private fun parseSeriesCard(element: Element): SeriesCard? {
        try {
            // Extract link and slug
            val link = element.selectFirst("a")?.attr("href") ?: return null
            val slug = link.substringAfterLast("/").takeIf { it.isNotBlank() } ?: return null

            // Extract title
            val title = element.selectFirst("div.title, h3, h2, a")?.text()?.trim() ?: return null

            // Extract episode count
            val episodesText = element.selectFirst("div.episodes, span.episodes")?.text() ?: ""
            val episodeCount = extractNumber(episodesText) ?: 0

            // Extract genre
            val genre = element.selectFirst("div.genre, span.genre")?.text()?.replace("Genre: ", "")?.trim()

            // Extract thumbnail
            val thumbnail = element.selectFirst("img")?.attr("src")

            return SeriesCard(
                title = title,
                slug = slug,
                thumbnailUrl = thumbnail,
                genre = genre,
                totalEpisodes = episodeCount
            )
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    /**
     * Parse movie cards from category page
     *
     * HTML Structure similar to series cards
     */
    fun parseMovieCards(doc: Document): List<MovieCard> {
        val cards = mutableListOf<MovieCard>()

        val selectors = listOf(
            "div.movie-card",
            "article.movie",
            "div.item"
        )

        for (selector in selectors) {
            val elements = doc.select(selector)
            if (elements.isNotEmpty()) {
                for (element in elements) {
                    val card = parseMovieCard(element)
                    if (card != null) {
                        cards.add(card)
                    }
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
        try {
            val link = element.selectFirst("a")?.attr("href") ?: return null
            val slug = link.substringAfterLast("/").takeIf { it.isNotBlank() } ?: return null

            val title = element.selectFirst("div.title, h3, h2, a")?.text()?.trim() ?: return null

            val genre = element.selectFirst("div.genre, span.genre")?.text()?.replace("Genre: ", "")?.trim()

            val director = element.selectFirst("div.director, span.director")?.text()?.replace("Director: ", "")?.trim()

            val thumbnail = element.selectFirst("img")?.attr("src")

            return MovieCard(
                title = title,
                slug = slug,
                genre = genre,
                thumbnailUrl = thumbnail,
                director = director
            )
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    /**
     * Parse episode list from series detail page
     *
     * HTML Structure:
     * <li class="animate">
     *   <div class="divBorder4">
     *     <a href="/series/{series-slug}/episodes/{episode-slug}">
     *       <img src="...thumb.jpg">
     *     </a>
     *     <div id="divEpiHolder1">
     *       <div id="divEpiNo1" class="fluid">1</div>
     *     </div>
     *   </div>
     * </li>
     *
     * @param doc Jsoup Document of series page
     * @param seasonNumber Optional season number (for POST responses that don't include season in HTML)
     * @return List of EpisodeItem objects
     */
    fun parseEpisodeList(doc: Document, seasonNumber: Int? = null): List<EpisodeItem> {
        val episodes = mutableListOf<EpisodeItem>()

        // Check for season selector - Namakade uses JavaScript to load seasons dynamically
        val seasonLinks = doc.select("div.titrSeason a.displayseason, #DivTitrSeason a.displayseason")

        if (seasonLinks.isNotEmpty()) {
            android.util.Log.d("NamakadeHtmlParser", "Found ${seasonLinks.size} season links - multi-season show")
            android.util.Log.d("NamakadeHtmlParser", "Note: Multi-season parsing requires separate requests per season")
            android.util.Log.d("NamakadeHtmlParser", "This will be handled by NamakadeApiService")
        }

        // Find episode list items
        // Full page HTML has <ul id="gridMason2" class="gridMasonTR"><li>
        // POST season responses return raw <li> fragments without <ul> wrapper
        // JavaScript later adds "shown" or "animate" classes
        // Since Jsoup doesn't execute JS, we need to select plain <li> elements
        val episodeElements = doc.select("ul#gridMason2 > li, ul.gridMasonTR > li, body > li, li").filter { element ->
            !element.hasClass("adInList") && element.selectFirst("div.divBorder4") != null
        }

        android.util.Log.d("NamakadeHtmlParser", "Found ${episodeElements.size} episode elements")

        for (element in episodeElements) {
            try {
                // Extract episode number from div with class "fluid"
                val episodeNumText = element.selectFirst("div.fluid")?.text()
                if (episodeNumText.isNullOrBlank()) {
                    android.util.Log.d("NamakadeHtmlParser", "Skipping element - no fluid div found")
                    continue
                }

                val (parsedSeason, episode) = parseEpisodeNumber(episodeNumText)
                // Use provided seasonNumber if available, otherwise use parsed season
                val season = seasonNumber ?: parsedSeason

                // Extract episode link from the first <a> tag in divBorder4
                val link = element.selectFirst("div.divBorder4 a")?.attr("href")
                if (link.isNullOrBlank()) {
                    android.util.Log.d("NamakadeHtmlParser", "Skipping episode $episode - no link found")
                    continue
                }

                val episodeSlug = link.substringAfterLast("/")

                // Extract title (use episode number as fallback)
                val title = "Episode $episode"

                // Extract thumbnail from first img tag
                val thumbnail = element.selectFirst("div.divBorder4 img")?.attr("src")

                episodes.add(
                    EpisodeItem(
                        episodeNumber = episode,
                        season = season,
                        title = title,
                        episodeSlug = episodeSlug,
                        thumbnailUrl = thumbnail
                    )
                )

                android.util.Log.d("NamakadeHtmlParser", "Parsed episode $episode: $episodeSlug")
            } catch (e: Exception) {
                android.util.Log.e("NamakadeHtmlParser", "Error parsing episode", e)
                e.printStackTrace()
                continue
            }
        }

        android.util.Log.d("NamakadeHtmlParser", "Returning ${episodes.size} episodes")
        return episodes
    }

    /**
     * Extract video URL from episode/movie page
     *
     * HTML Structure:
     * <video id="videoTag">
     *   <source src="https://media.negahestan.com/ipnx/media/series/episodes/Algoritm_01.mp4" type="video/mp4">
     * </video>
     *
     * @param doc Jsoup Document of episode/movie page
     * @return Direct MP4 URL or null if not found
     */
    fun extractVideoUrl(doc: Document): String? {
        // Method 1: Parse <video><source> tag
        val videoSource = doc.selectFirst("video source")?.attr("src")
        if (!videoSource.isNullOrBlank() && videoSource.endsWith(".mp4", ignoreCase = true)) {
            return videoSource
        }

        // Method 2: Look for video URL in JavaScript
        val scriptText = doc.select("script").joinToString("\n") { it.html() }
        val mp4Regex = Regex("""https://media\.negahestan\.com/[^"']+\.mp4""")
        val match = mp4Regex.find(scriptText)
        if (match != null) {
            return match.value
        }

        // Method 3: Look in any <source> tags
        val sources = doc.select("source[src*=.mp4]")
        for (source in sources) {
            val src = source.attr("src")
            if (src.contains("media.negahestan.com")) {
                return src
            }
        }

        return null
    }

    /**
     * Extract live TV HLS URL from live TV page
     *
     * HTML Structure:
     * <source src="https://glwizhstb46.glwiz.com/GEMTV_HD.m3u8?user=...&session=...">
     *
     * @param doc Jsoup Document of live TV page
     * @return HLS m3u8 URL or null if not found
     */
    fun extractLiveTvUrl(doc: Document): String? {
        // Look for .m3u8 URL in source tags
        val sources = doc.select("source[src*=.m3u8]")
        for (source in sources) {
            val src = source.attr("src")
            if (src.contains("glwiz.com")) {
                return src
            }
        }

        // Look in JavaScript
        val scriptText = doc.select("script").joinToString("\n") { it.html() }
        val m3u8Regex = Regex("""https://[^"']+\.m3u8[^"']*""")
        val match = m3u8Regex.find(scriptText)
        return match?.value
    }

    /**
     * Parse episode number from numerando text
     *
     * Examples:
     * - "1" -> (season=1, episode=1)
     * - "5" -> (season=1, episode=5)
     * - "2 - 3" -> (season=2, episode=3)
     *
     * @param numerando Text from numerando div
     * @return Pair of (season, episode)
     */
    private fun parseEpisodeNumber(numerando: String): Pair<Int, Int> {
        val trimmed = numerando.trim()

        // Check if format is "season - episode"
        if (trimmed.contains("-")) {
            val parts = trimmed.split("-").map { it.trim() }
            val season = parts.getOrNull(0)?.toIntOrNull() ?: 1
            val episode = parts.getOrNull(1)?.toIntOrNull() ?: 1
            return Pair(season, episode)
        }

        // Single number - treat as episode number, season 1
        val episode = trimmed.toIntOrNull() ?: 1
        return Pair(1, episode)
    }

    /**
     * Extract series metadata from series detail page
     *
     * @param doc Jsoup Document of series page
     * @return Map of metadata (description, year, genres, etc.)
     */
    fun extractSeriesMetadata(doc: Document): Map<String, String> {
        val metadata = mutableMapOf<String, String>()

        // Extract description
        val description = doc.selectFirst("div.description, p.description, div.plot")?.text()
        if (!description.isNullOrBlank()) {
            metadata["description"] = description
        }

        // Extract year
        val year = doc.selectFirst("span.year, div.year")?.text()
        if (!year.isNullOrBlank()) {
            metadata["year"] = year
        }

        // Extract genres
        val genres = doc.select("a.genre, span.genre").joinToString(", ") { it.text() }
        if (genres.isNotBlank()) {
            metadata["genres"] = genres
        }

        // Extract cast
        val cast = doc.select("a.cast, span.cast").joinToString(", ") { it.text() }
        if (cast.isNotBlank()) {
            metadata["cast"] = cast
        }

        return metadata
    }

    /**
     * Extract number from text (e.g., "Episodes: 24" -> 24)
     */
    private fun extractNumber(text: String): Int? {
        val regex = Regex("""\d+""")
        val match = regex.find(text)
        return match?.value?.toIntOrNull()
    }

    /**
     * Data class for show metadata (ID, season count, type)
     */
    data class ShowMetadata(
        val showId: String?,
        val seasonCount: Int,
        val pageType: String  // "shows" or "series"
    )

    /**
     * Extract show metadata from HTML
     * Detects:
     * - Show ID (from rating div data-id attribute)
     * - Season count (from season selector links)
     * - Page type (from displayseason pagename attribute)
     */
    fun extractShowMetadata(doc: Document): ShowMetadata {
        // Extract show ID from rating div
        val showId = doc.selectFirst("div.rateMedia[data-id]")?.attr("data-id")

        // Extract season links
        val seasonLinks = doc.select("div.titrSeason a.displayseason, #DivTitrSeason a.displayseason")
        val seasonCount = seasonLinks.size

        // Extract page type from first season link
        val pageType = seasonLinks.firstOrNull()?.attr("pagename") ?: "series"

        android.util.Log.d("NamakadeHtmlParser", "Extracted metadata: showId=$showId, seasons=$seasonCount, type=$pageType")

        return ShowMetadata(showId, seasonCount, pageType)
    }
}
