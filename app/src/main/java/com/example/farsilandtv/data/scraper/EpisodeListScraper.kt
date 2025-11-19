package com.example.farsilandtv.data.scraper

import com.example.farsilandtv.data.api.RetrofitClient
import com.example.farsilandtv.data.models.Episode
import com.example.farsilandtv.data.namakade.NamakadeApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.util.concurrent.TimeUnit

/**
 * Scraper for extracting episode lists from TV series pages
 *
 * Episodes are typically in <li> elements with classes like:
 * - mark-1, mark-2, mark-3 (episode markers)
 * - Or in structured divs/sections
 *
 * Based on FarsiFlow scraper analysis:
 * - Episodes are in <li class="mark-*"> elements
 * - Each episode has a link to its page
 * - Rate limit: 500ms delay between requests
 */
object EpisodeListScraper {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)  // Reduced from 30s for better UX
        .readTimeout(20, TimeUnit.SECONDS)     // Reduced from 30s for better UX
        .build()

    /**
     * Scrape episode list from series page
     *
     * @param seriesPageUrl URL to series page (e.g., https://farsiland.com/series/shoghal/)
     * @param seriesId Series ID for linking episodes
     * @param seriesTitle Series title to display on episode cards
     * @return Map of season number to list of episodes
     */
    suspend fun scrapeEpisodeList(
        seriesPageUrl: String,
        seriesId: Int,
        seriesTitle: String? = null
    ): Map<Int, List<Episode>> = withContext(Dispatchers.IO) {
        try {
            // Log ALL episode scraping attempts
            android.util.Log.d("EpisodeListScraper", "=== scrapeEpisodeList called ===")
            android.util.Log.d("EpisodeListScraper", "URL: $seriesPageUrl")
            android.util.Log.d("EpisodeListScraper", "SeriesId: $seriesId")
            android.util.Log.d("EpisodeListScraper", "SeriesTitle: $seriesTitle")

            // NAMAKADE DETECTION: Use dedicated API service for Namakade URLs
            if (seriesPageUrl.contains("namakade.com", ignoreCase = true)) {
                android.util.Log.d("EpisodeListScraper", "✓ NAMAKADE DETECTED - Using NamakadeApiService")
                android.util.Log.d("EpisodeListScraper", "Series URL: $seriesPageUrl")

                // Pass full URL directly to preserve /shows/ vs /series/ path
                val namakadeService = NamakadeApiService()
                val episodes = namakadeService.getEpisodes(seriesPageUrl, seriesId, seriesTitle)

                android.util.Log.d("EpisodeListScraper", "Namakade returned ${episodes.size} episodes")

                // Group by season and return
                return@withContext episodes.groupBy { it.season }
            }
            
            // STANDARD SCRAPING: For Farsiland/FarsiPlex URLs
            val html = fetchHtml(seriesPageUrl)
            val doc = Jsoup.parse(html)

            // Extract episodes from HTML
            val episodes = extractEpisodes(doc, seriesId, seriesTitle)

            // Group by season
            episodes.groupBy { it.season }

        } catch (e: Exception) {
            e.printStackTrace()
            emptyMap()
        }
    }

    /**
     * Extract episodes from HTML document
     * Looks for episode markers and links
     */
    private fun extractEpisodes(doc: Document, seriesId: Int, seriesTitle: String?): List<Episode> {
        val episodes = mutableListOf<Episode>()

        // Check for season selector/tabs - Farsiland uses #seasons div with tabs
        val seasonSelector = doc.select("#seasons .se-q, #seasons .seasons-tab, .aa-cnt a[href*='#']").firstOrNull()
        if (seasonSelector != null) {
            android.util.Log.d("EpisodeListScraper", "Found season selector: ${seasonSelector.text()}")
        }

        // Try different selectors for episode lists
        // Pattern 1: .se-c li elements (Farsiland episode list)
        val episodeContainer = doc.select(".se-c, .episodios").firstOrNull()

        // Check ALL season containers, not just the visible one
        val allSeasonContainers = doc.select("#seasons .se-q, .se-c")
        android.util.Log.d("EpisodeListScraper", "Found ${allSeasonContainers.size} season containers")

        val episodeElements = if (allSeasonContainers.size > 1) {
            // Multiple seasons - get episodes from all containers
            allSeasonContainers.flatMap { it.select("li") }
        } else {
            // Single container or fallback
            episodeContainer?.select("li") ?: doc.select("li[class^=mark-]")
        }

        android.util.Log.d("EpisodeListScraper", "Found ${episodeElements.size} episode elements total")

        if (episodeElements.isNotEmpty()) {
            for (element in episodeElements) {
                val episode = parseEpisodeElement(element, seriesId, seriesTitle)
                if (episode != null) {
                    android.util.Log.d("EpisodeListScraper", "Parsed episode: S${episode.season}E${episode.episode} - ${episode.title}")
                    episodes.add(episode)
                } else {
                    android.util.Log.w("EpisodeListScraper", "Failed to parse element: ${element.className()}")
                }
            }
        } else {
            android.util.Log.d("EpisodeListScraper", "No standard episode elements, trying episode links")
            // Pattern 2: Look for episode links in structured divs
            val episodeLinks = doc.select("a[href*=/episodes/]")
            android.util.Log.d("EpisodeListScraper", "Found ${episodeLinks.size} episode links")
            for (link in episodeLinks) {
                val episode = parseEpisodeLink(link, seriesId, seriesTitle)
                if (episode != null) {
                    android.util.Log.d("EpisodeListScraper", "Parsed episode from link: S${episode.season}E${episode.episode} - ${episode.title}")
                    episodes.add(episode)
                }
            }
        }

        val seasonCounts = episodes.groupBy { it.season }.mapValues { it.value.size }
        android.util.Log.d("EpisodeListScraper", "Episodes by season: $seasonCounts")

        return episodes.sortedWith(compareBy({ it.season }, { it.episode }))
    }

    /**
     * Parse episode from <li class="mark-*"> element
     * Farsiland structure:
     * <li class="mark-1-1">  <!-- Season 1, Episode 1 -->
     *   <div class="numerando">1 - 1</div>  <!-- "Season - Episode" -->
     *   <a href="/episodes/...">
     *     <div class="episodiotitle">Episode Title</div>
     *   </a>
     * </li>
     */
    private fun parseEpisodeElement(element: Element, seriesId: Int, seriesTitle: String?): Episode? {
        try {
            // Method 1: Extract from class name (mark-{season}-{episode})
            val className = element.className()
            val classRegex = Regex("""mark-(\d+)-(\d+)""")
            val classMatch = classRegex.find(className)

            var season: Int? = null
            var episode: Int? = null

            if (classMatch != null) {
                season = classMatch.groupValues[1].toIntOrNull()
                episode = classMatch.groupValues[2].toIntOrNull()
            }

            // Method 2: Extract from numerando div ("1 - 5" = Season 1, Episode 5)
            if (season == null || episode == null) {
                val numerando = element.select("div.numerando").firstOrNull()?.text()
                if (numerando != null && numerando.contains(" - ")) {
                    val parts = numerando.split(" - ")
                    if (parts.size == 2) {
                        season = parts[0].trim().toIntOrNull()
                        episode = parts[1].trim().toIntOrNull()
                    }
                }
            }

            if (season == null || episode == null) return null

            // Get episode link
            val link = element.select("a").firstOrNull() ?: return null
            val href = link.attr("abs:href")
            if (href.isEmpty()) return null

            // Get episode title from episodiotitle div or link text
            val title = element.select("div.episodiotitle").firstOrNull()?.text()?.trim()
                ?: link.text().trim().ifEmpty { "Episode $episode" }

            // Get episode poster/thumbnail (check for lazy-loaded images)
            val imgElement = element.select("img").firstOrNull()
            val thumbnail = when {
                // Priority 1: data-src (lazy-loaded full image)
                imgElement?.attr("data-src")?.isNotEmpty() == true -> 
                    imgElement.attr("abs:data-src")
                // Priority 2: data-lazy-src
                imgElement?.attr("data-lazy-src")?.isNotEmpty() == true -> 
                    imgElement.attr("abs:data-lazy-src")
                // Priority 3: Regular src (if not a placeholder)
                imgElement?.attr("src")?.let { src -> 
                    !src.startsWith("data:image/svg+xml") && src.isNotEmpty()
                } == true -> imgElement.attr("abs:src")
                // No valid image found
                else -> null
            }

            // Generate unique ID from series/season/episode to prevent hash collisions
            val uniqueId = "$seriesId-$season-$episode".hashCode()

            return Episode(
                id = uniqueId,
                seriesId = seriesId,
                seriesTitle = seriesTitle,
                title = title,
                description = "",
                thumbnailUrl = thumbnail,
                farsilandUrl = href,
                season = season,
                episode = episode
            )

        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    /**
     * Parse episode from link element
     */
    private fun parseEpisodeLink(link: Element, seriesId: Int, seriesTitle: String?): Episode? {
        try {
            val href = link.attr("abs:href")
            if (href.isEmpty()) return null

            // Extract season and episode from URL patterns
            // Pattern 1: /episodes/show-ep01/ or /episodes/show-ep01-part-2/
            val episodePattern = Regex("""ep(\d+)""", RegexOption.IGNORE_CASE)
            val episodeMatch = episodePattern.find(href)
            
            // Pattern 2: s01e05 style
            val seasonEpisodePattern = Regex("""s(\d+)e(\d+)""", RegexOption.IGNORE_CASE)
            val seasonEpisodeMatch = seasonEpisodePattern.find(href)
            
            val season: Int
            val episode: Int
            
            when {
                seasonEpisodeMatch != null -> {
                    season = seasonEpisodeMatch.groupValues[1].toIntOrNull() ?: return null
                    episode = seasonEpisodeMatch.groupValues[2].toIntOrNull() ?: return null
                }
                episodeMatch != null -> {
                    season = 1 // Default to season 1 for simple episode URLs
                    episode = episodeMatch.groupValues[1].toIntOrNull() ?: return null
                }
                else -> return null
            }

            val title = link.text().trim().ifEmpty { "Episode $episode" }
            
            // Try to get thumbnail from parent element
            val parentLi = link.parent()?.parent()
            val imgElement = parentLi?.select("img")?.firstOrNull()
            val thumbnail = when {
                imgElement?.attr("data-src")?.isNotEmpty() == true -> 
                    imgElement.attr("abs:data-src")
                imgElement?.attr("data-lazy-src")?.isNotEmpty() == true -> 
                    imgElement.attr("abs:data-lazy-src")
                imgElement?.attr("src")?.let { src -> 
                    !src.startsWith("data:image/svg+xml") && src.isNotEmpty()
                } == true -> imgElement.attr("abs:src")
                else -> null
            }

            // Generate unique ID from series/season/episode to prevent hash collisions
            val uniqueId = "$seriesId-$season-$episode".hashCode()

            return Episode(
                id = uniqueId,
                seriesId = seriesId,
                seriesTitle = seriesTitle,
                title = title,
                description = "",
                thumbnailUrl = thumbnail,
                farsilandUrl = href,
                season = season,
                episode = episode
            )

        } catch (e: Exception) {
            return null
        }
    }

    /**
     * Fetch HTML content with rate limiting
     * 500ms delay to avoid overwhelming the server (per FarsiFlow best practice)
     */
    private suspend fun fetchHtml(url: String): String = withContext(Dispatchers.IO) {
        // Rate limiting: 500ms delay
        delay(500)

        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("HTTP ${response.code}: ${response.message}")
            }

            val body = response.body ?: throw Exception("Empty response body")

            // Limit to 5MB to prevent OOM
            val contentLength = body.contentLength()
            if (contentLength > 5_000_000) {
                throw Exception("Response too large: $contentLength bytes")
            }

            return@withContext body.string()
        }
    }

    /**
     * Scrape episode details (description, thumbnail, etc.) from episode page
     * This is optional and can be called on-demand when user views episode details
     */
    suspend fun scrapeEpisodeDetails(episodeUrl: String): Episode? = withContext(Dispatchers.IO) {
        try {
            val html = fetchHtml(episodeUrl)
            val doc = Jsoup.parse(html)

            // Extract season/episode from URL
            val regex = Regex("""s(\d+)e(\d+)""", RegexOption.IGNORE_CASE)
            val match = regex.find(episodeUrl) ?: return@withContext null

            val season = match.groupValues[1].toIntOrNull() ?: return@withContext null
            val episode = match.groupValues[2].toIntOrNull() ?: return@withContext null

            // Extract title
            val title = doc.select("h1.entry-title").firstOrNull()?.text()
                ?: doc.select("title").text()
                ?: "Episode $episode"

            // Extract description
            val description = doc.select("div.entry-content").firstOrNull()?.text()
                ?: doc.select("meta[name=description]").attr("content")
                ?: ""

            // Extract thumbnail
            val thumbnail = doc.select("meta[property=og:image]").attr("content")
                ?: doc.select("img.episode-thumbnail").firstOrNull()?.attr("abs:src")

            // Generate unique ID from URL and episode numbers to prevent collisions
            val uniqueId = "$episodeUrl-$season-$episode".hashCode()

            Episode(
                id = uniqueId,
                seriesId = null,
                title = title,
                description = description,
                thumbnailUrl = thumbnail,
                farsilandUrl = episodeUrl,
                season = season,
                episode = episode
            )

        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
