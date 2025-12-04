package com.example.farsilandtv.data.namakade

import com.example.farsilandtv.data.api.RetrofitClient
import com.example.farsilandtv.data.models.Episode
import com.example.farsilandtv.data.models.Movie
import com.example.farsilandtv.data.models.Series
import com.example.farsilandtv.data.models.VideoUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.jsoup.Jsoup
import java.io.IOException
import java.util.concurrent.atomic.AtomicLong

/**
 * API Service for Namakade.com content scraping
 *
 * Provides methods to fetch and parse HTML pages for:
 * - Series listings
 * - Movie listings
 * - Episode lists
 * - Video URLs
 *
 * Rate Limiting: 500ms delay between requests to avoid overloading server
 */
class NamakadeApiService {

    private val httpClient = RetrofitClient.getHttpClient()

    companion object {
        private const val RATE_LIMIT_DELAY_MS = 500L
        private val lastRequestTime = AtomicLong(0L)
        private val rateLimitMutex = Mutex()

        /**
         * Enforce rate limiting between requests
         * Thread-safe implementation using Mutex to prevent race conditions
         *
         * Ensures atomic check-delay-update operation to prevent concurrent
         * coroutines from bypassing the 500ms delay during batch syncs
         */
        private suspend fun enforceRateLimit() {
            rateLimitMutex.withLock {
                val now = System.currentTimeMillis()
                val last = lastRequestTime.get()
                val timeSinceLastRequest = now - last

                if (timeSinceLastRequest < RATE_LIMIT_DELAY_MS) {
                    delay(RATE_LIMIT_DELAY_MS - timeSinceLastRequest)
                }

                lastRequestTime.set(System.currentTimeMillis())
            }
        }
    }

    /**
     * Fetch series from a category page
     *
     * @param category Category name (e.g., "best-serial", "serieses")
     * @return List of Series objects
     *
     * Example:
     * val series = service.getSeriesFromCategory("best-serial")
     */
    suspend fun getSeriesFromCategory(category: String = "best-serial"): List<Series> = withContext(Dispatchers.IO) {
        try {
            enforceRateLimit()

            val url = NamakadeUrlBuilder.buildCategoryUrl(category)
            val html = fetchHtml(url)
            val doc = Jsoup.parse(html)

            val seriesCards = NamakadeHtmlParser.parseSeriesCards(doc)

            seriesCards.mapIndexed { index, card ->
                Series(
                    id = "namakade_${card.slug}_${index}".hashCode(),
                    title = card.title,
                    description = "",
                    posterUrl = card.thumbnailUrl,
                    backdropUrl = card.thumbnailUrl,
                    farsilandUrl = NamakadeUrlBuilder.buildSeriesPageUrl(card.slug),
                    totalEpisodes = card.totalEpisodes,
                    genres = card.genre?.let { listOf(it) } ?: emptyList()
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * Fetch movies from a category page
     *
     * @param category Category name (e.g., "best-movies", "movies")
     * @return List of Movie objects
     */
    suspend fun getMoviesFromCategory(category: String = "best-movies"): List<Movie> = withContext(Dispatchers.IO) {
        try {
            enforceRateLimit()

            val url = NamakadeUrlBuilder.buildCategoryUrl(category)
            val html = fetchHtml(url)
            val doc = Jsoup.parse(html)

            val movieCards = NamakadeHtmlParser.parseMovieCards(doc)

            movieCards.mapIndexed { index, card ->
                // Extract genre from slug if not in metadata
                val genre = card.genre ?: card.slug.split("-").firstOrNull() ?: ""

                Movie(
                    id = "namakade_${card.slug}_${index}".hashCode(),
                    title = card.title,
                    description = "",
                    posterUrl = card.thumbnailUrl,
                    backdropUrl = card.thumbnailUrl,
                    farsilandUrl = NamakadeUrlBuilder.buildMoviePageUrl(genre, card.slug),
                    genres = card.genre?.let { listOf(it) } ?: emptyList(),
                    director = card.director
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * Fetch episode list for a series
     *
     * @param seriesUrlOrSlug Full series URL or just the slug (for backward compatibility)
     * @return List of Episode objects
     *
     * Example:
     * val episodes = service.getEpisodes("https://namakade.com/shows/oscar")
     * val episodes = service.getEpisodes("algoritm")  // Also works, builds URL as /series/
     */
    suspend fun getEpisodes(
        seriesUrlOrSlug: String,
        seriesId: Int = 0,
        seriesTitle: String? = null
    ): List<Episode> = withContext(Dispatchers.IO) {
        try {
            android.util.Log.d("NamakadeApiService", "=== getEpisodes called ===")
            android.util.Log.d("NamakadeApiService", "Input: $seriesUrlOrSlug")

            enforceRateLimit()

            // Support both full URL and slug for backward compatibility
            val url = if (seriesUrlOrSlug.startsWith("http", ignoreCase = true)) {
                seriesUrlOrSlug  // Use URL directly
            } else {
                NamakadeUrlBuilder.buildSeriesPageUrl(seriesUrlOrSlug)  // Build from slug
            }

            // Extract slug from URL for ID generation
            val seriesSlug = url.substringAfterLast("/")

            android.util.Log.d("NamakadeApiService", "Slug: $seriesSlug")
            android.util.Log.d("NamakadeApiService", "Fetching URL: $url")

            val html = fetchHtml(url)
            android.util.Log.d("NamakadeApiService", "HTML fetched, length: ${html.length} chars")

            val doc = Jsoup.parse(html)

            // Check if this is a multi-season show
            val metadata = NamakadeHtmlParser.extractShowMetadata(doc)

            val allEpisodeItems = if (metadata.seasonCount > 1 && metadata.showId != null) {
                // Multi-season show - fetch each season separately
                android.util.Log.d("NamakadeApiService", "Multi-season show detected: ${metadata.seasonCount} seasons")

                val episodesList = mutableListOf<NamakadeHtmlParser.EpisodeItem>()

                for (seasonNum in 1..metadata.seasonCount) {
                    try {
                        enforceRateLimit()

                        val seasonUrl = "https://namakade.com/views/season/$seasonNum/${metadata.pageType}/${metadata.showId}"
                        android.util.Log.d("NamakadeApiService", "Fetching season $seasonNum from: $seasonUrl (POST)")

                        val seasonHtml = fetchHtmlPost(seasonUrl)
                        val seasonDoc = Jsoup.parse(seasonHtml)
                        val seasonEpisodes = NamakadeHtmlParser.parseEpisodeList(seasonDoc, seasonNumber = seasonNum)

                        android.util.Log.d("NamakadeApiService", "Season $seasonNum: found ${seasonEpisodes.size} episodes")
                        episodesList.addAll(seasonEpisodes)
                    } catch (e: Exception) {
                        android.util.Log.e("NamakadeApiService", "Error fetching season $seasonNum", e)
                    }
                }

                android.util.Log.d("NamakadeApiService", "Total episodes from all seasons: ${episodesList.size}")
                episodesList
            } else {
                // Single season or no metadata - use default parsing
                val episodeItems = NamakadeHtmlParser.parseEpisodeList(doc)
                android.util.Log.d("NamakadeApiService", "Parser returned ${episodeItems.size} episodes")
                episodeItems
            }

            allEpisodeItems.map { item ->
                Episode(
                    id = "namakade_${seriesSlug}_s${item.season}e${item.episodeNumber}".hashCode(),
                    seriesId = seriesId,
                    seriesTitle = seriesTitle,
                    title = item.title,
                    description = "",
                    thumbnailUrl = item.thumbnailUrl,
                    farsilandUrl = NamakadeUrlBuilder.buildEpisodePageUrl(seriesSlug, item.episodeSlug),
                    season = item.season,
                    episode = item.episodeNumber
                )
            }
        } catch (e: Exception) {
            android.util.Log.e("NamakadeApiService", "Error fetching episodes for $seriesUrlOrSlug", e)
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * Extract video URL from episode or movie page
     *
     * @param pageUrl Full URL to episode/movie page
     * @return VideoUrl object or null if not found
     *
     * Example:
     * val videoUrl = service.extractVideoUrl("https://namakade.com/serieses/algoritm/episodes/episode-1")
     */
    suspend fun extractVideoUrl(pageUrl: String): VideoUrl? = withContext(Dispatchers.IO) {
        try {
            enforceRateLimit()

            val html = fetchHtml(pageUrl)
            val doc = Jsoup.parse(html)

            val videoUrlString = NamakadeHtmlParser.extractVideoUrl(doc)

            if (videoUrlString != null) {
                VideoUrl(
                    url = videoUrlString,
                    quality = "1080p", // Namakade typically has single quality
                    mirror = "media.negahestan.com"
                )
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Extract video URL using fallback pattern generation
     *
     * This is a backup method if HTML parsing fails.
     * Tries to generate video URL from series slug and episode number.
     *
     * @param seriesSlug Series slug
     * @param episodeNumber Episode number
     * @return VideoUrl object
     *
     * Example:
     * val videoUrl = service.generateVideoUrl("algoritm", 1)
     * // Returns: https://media.negahestan.com/ipnx/media/series/episodes/Algoritm_01.mp4
     */
    fun generateVideoUrl(seriesSlug: String, episodeNumber: Int): VideoUrl {
        val url = NamakadeUrlBuilder.buildEpisodeUrlFromSlug(seriesSlug, episodeNumber)
        return VideoUrl(
            url = url,
            quality = "1080p",
            mirror = "media.negahestan.com"
        )
    }

    /**
     * Generate movie video URL
     *
     * @param movieSlug Movie slug
     * @return VideoUrl object
     */
    fun generateMovieVideoUrl(movieSlug: String): VideoUrl {
        val url = NamakadeUrlBuilder.buildMovieUrlFromSlug(movieSlug)
        return VideoUrl(
            url = url,
            quality = "1080p",
            mirror = "media.negahestan.com"
        )
    }

    /**
     * Verify if a video URL is accessible
     *
     * Sends HEAD request to check if URL returns 200 OK
     *
     * @param url Video URL to verify
     * @return True if URL is accessible
     */
    suspend fun verifyVideoUrl(url: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(url)
                .head()
                .build()

            httpClient.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Get video URL for episode with fallback
     *
     * Tries HTML scraping first, falls back to URL generation if scraping fails.
     *
     * @param seriesSlug Series slug
     * @param episodeSlug Episode slug
     * @param episodeNumber Episode number (for fallback)
     * @return VideoUrl object or null
     */
    suspend fun getEpisodeVideoUrl(
        seriesSlug: String,
        episodeSlug: String,
        episodeNumber: Int
    ): VideoUrl? {
        // Try scraping from episode page first
        val pageUrl = NamakadeUrlBuilder.buildEpisodePageUrl(seriesSlug, episodeSlug)
        val scrapedUrl = extractVideoUrl(pageUrl)

        if (scrapedUrl != null) {
            return scrapedUrl
        }

        // Fallback: Generate URL from pattern
        val generatedUrl = generateVideoUrl(seriesSlug, episodeNumber)

        // Verify generated URL works
        if (verifyVideoUrl(generatedUrl.url)) {
            return generatedUrl
        }

        return null
    }

    /**
     * Fetch series details with metadata
     *
     * @param seriesSlug Series slug
     * @return Series object with description and metadata
     */
    suspend fun getSeriesDetails(seriesSlug: String): Series? = withContext(Dispatchers.IO) {
        try {
            enforceRateLimit()

            val url = NamakadeUrlBuilder.buildSeriesPageUrl(seriesSlug)
            val html = fetchHtml(url)
            val doc = Jsoup.parse(html)

            val metadata = NamakadeHtmlParser.extractSeriesMetadata(doc)
            val episodes = NamakadeHtmlParser.parseEpisodeList(doc)

            Series(
                id = "namakade_$seriesSlug".hashCode(),
                title = doc.title(),
                description = metadata["description"] ?: "",
                posterUrl = null,
                backdropUrl = null,
                farsilandUrl = url,
                year = metadata["year"]?.toIntOrNull(),
                totalEpisodes = episodes.size,
                genres = metadata["genres"]?.split(",")?.map { it.trim() } ?: emptyList()
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Fetch HTML content from URL
     *
     * @param url URL to fetch
     * @return HTML content as string
     */
    private suspend fun fetchHtml(url: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("HTTP ${response.code}: ${response.message}")
            }

            val body = response.body ?: throw Exception("Empty response body")

            // EXTERNAL AUDIT FIX SN-M2: Add response size limit to prevent OOM
            // Issue: No limit on response size → OOM risk on large malicious responses
            // Fix: Check Content-Length header before reading, reject responses > 5MB
            val contentLength = response.header("Content-Length")?.toLongOrNull() ?: 0
            if (contentLength > 5 * 1024 * 1024) { // 5MB limit
                throw IOException("Response too large: $contentLength bytes (max 5MB)")
            }

            body.string()
        }
    }

    /**
     * Fetch HTML content from URL using POST request
     *
     * @param url URL to fetch
     * @return HTML content as string
     */
    private suspend fun fetchHtmlPost(url: String): String = withContext(Dispatchers.IO) {
        val emptyBody = ByteArray(0).toRequestBody(null)
        val request = Request.Builder()
            .url(url)
            .post(emptyBody)
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("HTTP ${response.code}: ${response.message}")
            }

            response.body?.string() ?: throw Exception("Empty response body")
        }
    }
}
