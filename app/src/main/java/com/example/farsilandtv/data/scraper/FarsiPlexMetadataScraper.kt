package com.example.farsilandtv.data.scraper

import android.util.Log
import com.example.farsilandtv.data.api.RetrofitClient
import com.example.farsilandtv.data.database.CachedMovie
import com.example.farsilandtv.data.database.CachedSeries
import com.example.farsilandtv.data.database.CachedEpisode
import com.example.farsilandtv.data.database.CachedVideoUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

/**
 * FarsiPlex Metadata Scraper
 *
 * Scrapes full metadata from FarsiPlex.com content pages
 * Similar to Python farsiplex_scraper_dooplay.py
 *
 * Features:
 * - Extracts movie/series metadata from HTML
 * - Scrapes video URLs from DooPlay player
 * - Handles quality detection
 * - Caches scraped data
 */
object FarsiPlexMetadataScraper {

    private const val TAG = "FarsiPlexMetaScraper"
    private val httpClient = RetrofitClient.getHttpClient()

    /**
     * Scrape full movie metadata from FarsiPlex page
     */
    suspend fun scrapeMovie(url: String, slug: String, lastmod: String?): CachedMovie? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Scraping movie: $slug")

            val response = httpClient.newCall(
                okhttp3.Request.Builder()
                    .url(url)
                    .build()
            ).execute()

            if (!response.isSuccessful) {
                Log.e(TAG, "Failed to fetch movie page: ${response.code}")
                return@withContext null
            }

            val html = response.body?.string() ?: return@withContext null
            val doc = Jsoup.parse(html)

            // Extract metadata from DooPlay theme structure
            val title = doc.select("h1.entry-title, .sheader h1").text()
                .ifEmpty {
                    // Extract clean title from slug (remove hash suffix like -3a7c9079)
                    // Slug format: "movie-name-12abc345" -> "Movie Name"
                    val cleanSlug = slug.substringBeforeLast("-").takeIf { it.contains("-") } ?: slug
                    cleanSlug.replace("-", " ").split(" ")
                        .joinToString(" ") { it.replaceFirstChar { c -> c.uppercaseChar() } }
                }

            val description = doc.select(".wp-content p, .description p").text()
                .ifEmpty { doc.select("meta[name=description]").attr("content") }

            val posterUrl = doc.select(".poster img, .sheader img").attr("src")
                .ifEmpty { doc.select("meta[property=og:image]").attr("content") }
                .takeIf { it.isNotEmpty() }

            val year = extractYear(doc)
            val rating = extractRating(doc)
            val runtime = extractRuntime(doc)
            val director = doc.select(".director a, .data .item:contains(Director) span").text()
                .takeIf { it.isNotEmpty() }
            val cast = doc.select(".cast a, .data .item:contains(Cast) span").text()
                .takeIf { it.isNotEmpty() }
            val genres = doc.select(".genre a, .genres a").joinToString(", ") { it.text() }
                .takeIf { it.isNotEmpty() }

            // Scrape video URLs
            val videoUrls = scrapeVideoUrls(doc, url)

            Log.i(TAG, "✓ Scraped movie: $title (${videoUrls.size} video URLs)")

            CachedMovie(
                id = slug.hashCode(),
                title = title,
                posterUrl = posterUrl,
                farsilandUrl = url,
                description = description,
                year = year,
                rating = rating?.toFloat(),
                runtime = runtime,
                director = director,
                cast = cast,
                genres = genres,
                dateAdded = parseDate(lastmod),
                lastUpdated = parseDate(lastmod)  // Use sitemap publish date, not scrape time
            )

        } catch (e: Exception) {
            Log.e(TAG, "Error scraping movie $slug: ${e.message}", e)
            null
        }
    }

    /**
     * Scrape full series metadata from FarsiPlex page
     */
    suspend fun scrapeSeries(url: String, slug: String, lastmod: String?): CachedSeries? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Scraping series: $slug")

            val response = httpClient.newCall(
                okhttp3.Request.Builder()
                    .url(url)
                    .build()
            ).execute()

            if (!response.isSuccessful) {
                Log.e(TAG, "Failed to fetch series page: ${response.code}")
                return@withContext null
            }

            val html = response.body?.string() ?: return@withContext null
            val doc = Jsoup.parse(html)

            // Extract metadata
            val title = doc.select("h1.entry-title, .sheader h1").text()
                .ifEmpty {
                    // Extract clean title from slug (remove hash suffix)
                    val cleanSlug = slug.substringBeforeLast("-").takeIf { it.contains("-") } ?: slug
                    cleanSlug.replace("-", " ").split(" ")
                        .joinToString(" ") { it.replaceFirstChar { c -> c.uppercaseChar() } }
                }

            val description = doc.select(".wp-content p, .description p").text()
                .ifEmpty { doc.select("meta[name=description]").attr("content") }

            val posterUrl = doc.select(".poster img, .sheader img").attr("src")
                .ifEmpty { doc.select("meta[property=og:image]").attr("content") }
                .takeIf { it.isNotEmpty() }

            val backdropUrl = doc.select(".backdrop img, .background img").attr("src")
                .takeIf { it.isNotEmpty() }

            val year = extractYear(doc)
            val rating = extractRating(doc)
            val cast = doc.select(".cast a, .data .item:contains(Cast) span").text()
                .takeIf { it.isNotEmpty() }
            val genres = doc.select(".genre a, .genres a").joinToString(", ") { it.text() }
                .takeIf { it.isNotEmpty() }

            // Count seasons from season selector
            val totalSeasons = doc.select("#seasons option, .se-c .se-q").size
                .takeIf { it > 0 } ?: 1

            // Count total episodes
            val totalEpisodes = doc.select(".episodios li, .episode-item").size

            Log.i(TAG, "✓ Scraped series: $title ($totalSeasons seasons, $totalEpisodes episodes)")

            CachedSeries(
                id = slug.hashCode(),
                title = title,
                posterUrl = posterUrl,
                backdropUrl = backdropUrl,
                farsilandUrl = url,
                description = description,
                year = year,
                rating = rating?.toFloat(),
                totalSeasons = totalSeasons,
                totalEpisodes = totalEpisodes,
                cast = cast,
                genres = genres,
                dateAdded = parseDate(lastmod),
                lastUpdated = parseDate(lastmod)  // Use sitemap publish date, not scrape time
            )

        } catch (e: Exception) {
            Log.e(TAG, "Error scraping series $slug: ${e.message}", e)
            null
        }
    }

    /**
     * Scrape full episode metadata from FarsiPlex page
     */
    suspend fun scrapeEpisode(url: String, slug: String, lastmod: String?): Pair<CachedEpisode, List<CachedVideoUrl>>? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Scraping episode: $slug")

            val response = httpClient.newCall(
                okhttp3.Request.Builder()
                    .url(url)
                    .build()
            ).execute()

            if (!response.isSuccessful) {
                Log.e(TAG, "Failed to fetch episode page: ${response.code}")
                return@withContext null
            }

            val html = response.body?.string() ?: return@withContext null
            val doc = Jsoup.parse(html)

            // Extract metadata
            val title = doc.select("h1.entry-title, .sheader h1").text()
                .ifEmpty { "Episode" }

            val description = doc.select(".wp-content p, .description p").text()

            val thumbnailUrl = doc.select(".poster img, .sheader img").attr("src")
                .ifEmpty { doc.select("meta[property=og:image]").attr("content") }
                .takeIf { it.isNotEmpty() }

            // Extract season/episode numbers from slug or page
            val (season, episode) = extractSeasonEpisode(slug, doc)

            // Extract series title
            val seriesTitle = doc.select(".serie-title a, .tvshows a").text()
                .ifEmpty {
                    // Remove hash suffix first, then extract series name
                    // Slug: "bamdade-khomar-ep01-e28da9c8" -> "bamdade-khomar-ep01" -> "Bamdade Khomar"
                    val cleanSlug = slug.substringBeforeLast("-").takeIf { it.contains("-") } ?: slug
                    cleanSlug.split("-")
                        .takeWhile { !it.matches(Regex("s\\d+e\\d+|ep\\d+", RegexOption.IGNORE_CASE)) }
                        .joinToString(" ") { it.replaceFirstChar { c -> c.uppercaseChar() } }
                }

            val airDate = lastmod?.take(10)

            // Scrape video URLs
            val videoUrls = scrapeVideoUrls(doc, url)

            val episodeId = slug.hashCode()
            val seriesId = seriesTitle.hashCode()

            // Create CachedVideoUrl entries
            val cachedVideoUrls = videoUrls.mapIndexed { index, videoUrl ->
                CachedVideoUrl(
                    contentId = episodeId,
                    contentType = "episode",
                    quality = videoUrl.quality,
                    mp4Url = videoUrl.url,
                    fileSizeMB = null,
                    cachedAt = System.currentTimeMillis()
                )
            }

            Log.i(TAG, "✓ Scraped episode: $seriesTitle S${season}E${episode} (${videoUrls.size} video URLs)")

            val cachedEpisode = CachedEpisode(
                seriesId = seriesId,
                seriesTitle = seriesTitle,
                episodeId = episodeId,
                season = season,
                episode = episode,
                title = title,
                description = description,
                thumbnailUrl = thumbnailUrl,
                farsilandUrl = url,
                airDate = airDate,
                runtime = null,
                dateAdded = parseDate(lastmod),
                lastUpdated = parseDate(lastmod)  // Use sitemap publish date, not scrape time
            )

            Pair(cachedEpisode, cachedVideoUrls)

        } catch (e: Exception) {
            Log.e(TAG, "Error scraping episode $slug: ${e.message}", e)
            null
        }
    }

    /**
     * Scrape video URLs from DooPlay player
     * Similar to VideoUrlScraper but optimized for FarsiPlex
     */
    private suspend fun scrapeVideoUrls(doc: Document, pageUrl: String): List<VideoUrl> {
        val videoUrls = mutableListOf<VideoUrl>()

        try {
            // Method 1: Extract from DooPlay data-playerid/data-post attributes
            val playerItems = doc.select("li[data-playerid], li[data-post]")
            for (item in playerItems) {
                val playerId = item.attr("data-playerid").ifEmpty { item.attr("data-post") }
                val quality = item.select(".title, span").text()
                    .takeIf { it.matches(Regex("\\d{3,4}p|HD|SD", RegexOption.IGNORE_CASE)) }
                    ?: "720p"

                // Try to extract direct URL from onclick or iframe
                val onClick = item.attr("onclick")
                val iframeUrl = extractUrlFromOnClick(onClick)

                if (iframeUrl != null && iframeUrl.contains(".mp4", ignoreCase = true)) {
                    videoUrls.add(VideoUrl(
                        url = iframeUrl,
                        quality = quality,
                        mirror = videoUrls.size + 1
                    ))
                }
            }

            // Method 2: Extract MP4 URLs from page source
            val mp4Pattern = Regex("https?://[^\\s\"'<>]+\\.mp4(?:\\?[^\\s\"'<>]*)?", RegexOption.IGNORE_CASE)
            val mp4Matches = mp4Pattern.findAll(doc.html())
            for (match in mp4Matches) {
                val url = match.value
                if (!videoUrls.any { it.url == url }) {
                    videoUrls.add(VideoUrl(
                        url = url,
                        quality = detectQuality(url),
                        mirror = videoUrls.size + 1
                    ))
                }
            }

            // Method 3: Check for iframe sources
            val iframes = doc.select("iframe[src*='.mp4'], iframe[data-src*='.mp4']")
            for (iframe in iframes) {
                val src = iframe.attr("src").ifEmpty { iframe.attr("data-src") }
                if (src.isNotEmpty() && !videoUrls.any { it.url == src }) {
                    videoUrls.add(VideoUrl(
                        url = src,
                        quality = detectQuality(src),
                        mirror = videoUrls.size + 1
                    ))
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error scraping video URLs: ${e.message}", e)
        }

        return videoUrls
    }

    /**
     * Extract URL from onclick JavaScript
     */
    private fun extractUrlFromOnClick(onClick: String): String? {
        if (onClick.isEmpty()) return null

        val urlPattern = Regex("https?://[^\\s\"'<>]+")
        return urlPattern.find(onClick)?.value
    }

    /**
     * Detect video quality from URL or filename
     */
    private fun detectQuality(url: String): String {
        return when {
            url.contains("1080", ignoreCase = true) || url.contains("fhd", ignoreCase = true) -> "1080p"
            url.contains("720", ignoreCase = true) || url.contains("hd", ignoreCase = true) -> "720p"
            url.contains("480", ignoreCase = true) -> "480p"
            url.contains("360", ignoreCase = true) || url.contains("sd", ignoreCase = true) -> "360p"
            else -> "720p" // Default
        }
    }

    /**
     * Extract year from document
     */
    private fun extractYear(doc: Document): Int? {
        val yearText = doc.select(".year, .date, .data .item:contains(Year) span").text()
        return yearText.filter { it.isDigit() }.take(4).toIntOrNull()
    }

    /**
     * Extract rating from document
     */
    private fun extractRating(doc: Document): Double? {
        val ratingText = doc.select(".rating, .dt_rating_vgs, .data .item:contains(Rating) span").text()
        return ratingText.filter { it.isDigit() || it == '.' }.toDoubleOrNull()
    }

    /**
     * Extract runtime from document
     */
    private fun extractRuntime(doc: Document): Int? {
        val runtimeText = doc.select(".runtime, .data .item:contains(Runtime) span").text()
        return runtimeText.filter { it.isDigit() }.toIntOrNull()
    }

    /**
     * Extract season/episode numbers from slug or document
     */
    private fun extractSeasonEpisode(slug: String, doc: Document): Pair<Int, Int> {
        // Try to extract from slug first (e.g., "show-name-s01e05")
        val seasonEpisodeMatch = Regex("s(\\d+)e(\\d+)", RegexOption.IGNORE_CASE).find(slug)
        if (seasonEpisodeMatch != null) {
            val season = seasonEpisodeMatch.groupValues[1].toIntOrNull() ?: 1
            val episode = seasonEpisodeMatch.groupValues[2].toIntOrNull() ?: 1
            return Pair(season, episode)
        }

        // Try to extract from slug (e.g., "show-name-ep05")
        val epMatch = Regex("ep(\\d+)", RegexOption.IGNORE_CASE).find(slug)
        if (epMatch != null) {
            val episode = epMatch.groupValues[1].toIntOrNull() ?: 1
            return Pair(1, episode)
        }

        // Try to extract from document
        val seasonText = doc.select(".season, .data .item:contains(Season) span").text()
        val episodeText = doc.select(".episode, .data .item:contains(Episode) span").text()

        val season = seasonText.filter { it.isDigit() }.toIntOrNull() ?: 1
        val episode = episodeText.filter { it.isDigit() }.toIntOrNull() ?: 1

        return Pair(season, episode)
    }

    /**
     * Parse ISO date to timestamp
     */
    private fun parseDate(dateStr: String?): Long {
        return try {
            if (dateStr != null) {
                // Try ISO 8601 with timezone first (e.g., "2024-05-29T10:30:00+00:00")
                val formatWithTimezone = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", java.util.Locale.US)
                try {
                    val result = formatWithTimezone.parse(dateStr)?.time
                    if (result != null) {
                        android.util.Log.d(TAG, "Parsed date with timezone: $dateStr -> $result")
                        return result
                    }
                } catch (e: Exception) {
                    android.util.Log.d(TAG, "Failed to parse with timezone format: $dateStr (${e.message})")
                }

                // Try with time but no timezone (e.g., "2024-05-29T10:30:00")
                val formatWithTime = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
                try {
                    val result = formatWithTime.parse(dateStr)?.time
                    if (result != null) {
                        android.util.Log.d(TAG, "Parsed date without timezone: $dateStr -> $result")
                        return result
                    }
                } catch (e: Exception) {
                    android.util.Log.d(TAG, "Failed to parse without timezone: $dateStr (${e.message})")
                }

                // Fallback to date-only format (e.g., "2024-05-29")
                val formatDateOnly = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                try {
                    val result = formatDateOnly.parse(dateStr)?.time
                    if (result != null) {
                        android.util.Log.d(TAG, "Parsed date-only: $dateStr -> $result")
                        return result
                    }
                } catch (e: Exception) {
                    android.util.Log.d(TAG, "Failed to parse date-only: $dateStr (${e.message})")
                }

                android.util.Log.w(TAG, "All date parsing failed for: $dateStr, using current time")
                System.currentTimeMillis()
            } else {
                android.util.Log.d(TAG, "Date string is null, using current time")
                System.currentTimeMillis()
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Exception in parseDate: $dateStr", e)
            System.currentTimeMillis()
        }
    }

    /**
     * Video URL data class
     */
    data class VideoUrl(
        val url: String,
        val quality: String,
        val mirror: Int
    )
}
