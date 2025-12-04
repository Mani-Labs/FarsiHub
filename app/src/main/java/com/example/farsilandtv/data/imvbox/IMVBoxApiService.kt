package com.example.farsilandtv.data.imvbox

import android.content.Context
import android.util.Log
import com.example.farsilandtv.data.api.RetrofitClient
import com.example.farsilandtv.data.api.IMVBoxAuthManager
import com.example.farsilandtv.data.models.Episode
import com.example.farsilandtv.data.models.Movie
import com.example.farsilandtv.data.models.Series
import com.example.farsilandtv.data.models.VideoUrl
import com.example.farsilandtv.data.scraper.ScraperResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.Request
import org.jsoup.Jsoup
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * API Service for IMVBox.com content scraping
 *
 * Provides methods to fetch and parse HTML pages for:
 * - Movies (list and detail)
 * - TV Series (list, seasons, episodes)
 * - Search (via AJAX API)
 * - Video URLs (HLS streams)
 *
 * Rate Limiting: 500ms delay between requests
 * Authentication: Session cookies + CSRF token for POST requests
 */
@Singleton
class IMVBoxApiService @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val httpClient = RetrofitClient.getHttpClient()

    companion object {
        private const val TAG = "IMVBoxApiService"
        private const val RATE_LIMIT_DELAY_MS = 500L
        private const val MAX_RESPONSE_SIZE = 5 * 1024 * 1024L // 5MB limit

        // Use Mutex only (no redundant AtomicLong)
        private var lastRequestTime = 0L
        private val rateLimitMutex = Mutex()

        /**
         * Enforce rate limiting between requests
         * Thread-safe implementation using Mutex
         */
        private suspend fun enforceRateLimit() {
            rateLimitMutex.withLock {
                val now = System.currentTimeMillis()
                val timeSinceLastRequest = now - lastRequestTime

                if (timeSinceLastRequest < RATE_LIMIT_DELAY_MS) {
                    delay(RATE_LIMIT_DELAY_MS - timeSinceLastRequest)
                }

                lastRequestTime = System.currentTimeMillis()
            }
        }

        /**
         * Build play URL from page URL.
         */
        private fun buildPlayUrl(pageUrl: String): String {
            return if (pageUrl.endsWith("/play")) pageUrl else "$pageUrl/play"
        }

        // Thread-safe singleton for non-Hilt usage (backward compatibility)
        @Volatile
        private var INSTANCE: IMVBoxApiService? = null

        fun getInstance(context: Context): IMVBoxApiService {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: IMVBoxApiService(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    // Cached CSRF token - @Volatile for thread-safe visibility across coroutines
    @Volatile private var cachedCsrfToken: String? = null
    @Volatile private var csrfTokenTime: Long = 0L
    private val csrfTokenMaxAge = 5 * 60 * 1000L // 5 minutes

    /**
     * Search for content using the AJAX search API
     *
     * @param query Search query
     * @return ScraperResult with list of search results
     */
    suspend fun search(query: String): ScraperResult<List<IMVBoxHtmlParser.SearchResult>> = withContext(Dispatchers.IO) {
        try {
            enforceRateLimit()

            // Get CSRF token
            val csrfToken = getCsrfToken()
                ?: return@withContext ScraperResult.ParseError("Failed to get CSRF token", null)

            // Build POST request
            val formBody = FormBody.Builder()
                .add("query", query)
                .add("_token", csrfToken)
                .build()

            val request = Request.Builder()
                .url(IMVBoxUrlBuilder.SEARCH_API)
                .post(formBody)
                .header("X-CSRF-TOKEN", csrfToken)
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Accept", "text/html, */*; q=0.01")
                .header("Referer", "${IMVBoxUrlBuilder.BASE_EN}/movies")
                .build()

            val html = executeRequest(request)
            val results = IMVBoxHtmlParser.parseSearchResults(html)

            Log.d(TAG, "Search '$query' returned ${results.size} results")
            ScraperResult.Success(results)
        } catch (e: IOException) {
            Log.e(TAG, "Network error during search", e)
            ScraperResult.NetworkError("Search failed: ${e.message}", e)
        } catch (e: Exception) {
            Log.e(TAG, "Error during search", e)
            ScraperResult.ParseError("Search failed: ${e.message}", e)
        }
    }

    /**
     * Search for content using GET request (full page search)
     * This is simpler and doesn't require CSRF token
     * Requires minimum 3 characters
     *
     * @param query Search query (min 3 chars)
     * @return ScraperResult with list of search results
     */
    suspend fun searchWeb(query: String): ScraperResult<List<IMVBoxHtmlParser.SearchResult>> = withContext(Dispatchers.IO) {
        try {
            if (query.length < 3) {
                return@withContext ScraperResult.NoDataFound("Search query must be at least 3 characters")
            }

            enforceRateLimit()

            val url = IMVBoxUrlBuilder.buildSearchUrl(query)
            Log.d(TAG, "Searching IMVBox: $url")

            val html = fetchHtml(url)
            val results = IMVBoxHtmlParser.parseSearchResults(html)

            Log.d(TAG, "Search '$query' returned ${results.size} results")
            ScraperResult.Success(results)
        } catch (e: IOException) {
            Log.e(TAG, "Network error during web search", e)
            ScraperResult.NetworkError("Search failed: ${e.message}", e)
        } catch (e: Exception) {
            Log.e(TAG, "Error during web search", e)
            ScraperResult.ParseError("Search failed: ${e.message}", e)
        }
    }

    /**
     * Fetch movies from list page
     *
     * @param page Page number (1-based)
     * @param sortBy Sort option
     * @return ScraperResult with list of Movie objects
     */
    suspend fun getMovies(page: Int = 1, sortBy: String? = null): ScraperResult<List<Movie>> = withContext(Dispatchers.IO) {
        try {
            enforceRateLimit()

            val url = IMVBoxUrlBuilder.buildMoviesListUrl(page, sortBy)
            val html = fetchHtml(url)
            val doc = Jsoup.parse(html)

            val movieCards = IMVBoxHtmlParser.parseMovieCards(doc)

            val movies = movieCards.mapIndexed { index, card ->
                Movie(
                    id = "imvbox_${card.slug}_${page}_$index".hashCode(),
                    title = card.title,
                    posterUrl = card.posterUrl ?: IMVBoxUrlBuilder.buildMoviePosterUrl(card.slug),
                    backdropUrl = card.posterUrl,
                    farsilandUrl = IMVBoxUrlBuilder.buildMovieDetailUrl(card.slug),
                    year = card.year,
                    rating = card.rating,
                    genres = emptyList()
                )
            }

            Log.d(TAG, "Fetched ${movies.size} movies from page $page")
            ScraperResult.Success(movies)
        } catch (e: IOException) {
            Log.e(TAG, "Network error fetching movies", e)
            ScraperResult.NetworkError("Failed to fetch movies: ${e.message}", e)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching movies", e)
            ScraperResult.ParseError("Failed to fetch movies: ${e.message}", e)
        }
    }

    /**
     * Fetch TV series from list page
     *
     * @param page Page number (1-based)
     * @param sortBy Sort option
     * @return ScraperResult with list of Series objects
     */
    suspend fun getSeries(page: Int = 1, sortBy: String? = null): ScraperResult<List<Series>> = withContext(Dispatchers.IO) {
        try {
            enforceRateLimit()

            val url = IMVBoxUrlBuilder.buildSeriesListUrl(page, sortBy)
            val html = fetchHtml(url)
            val doc = Jsoup.parse(html)

            val seriesCards = IMVBoxHtmlParser.parseSeriesCards(doc)

            val series = seriesCards.mapIndexed { index, card ->
                Series(
                    id = "imvbox_${card.slug}_${page}_$index".hashCode(),
                    title = card.title,
                    posterUrl = card.posterUrl ?: IMVBoxUrlBuilder.buildShowThumbnailUrl(card.slug),
                    backdropUrl = card.posterUrl,
                    farsilandUrl = IMVBoxUrlBuilder.buildShowDetailUrl(card.slug),
                    year = card.year,
                    totalSeasons = card.seasonCount ?: 1,
                    genres = emptyList()
                )
            }

            Log.d(TAG, "Fetched ${series.size} series from page $page")
            ScraperResult.Success(series)
        } catch (e: IOException) {
            Log.e(TAG, "Network error fetching series", e)
            ScraperResult.NetworkError("Failed to fetch series: ${e.message}", e)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching series", e)
            ScraperResult.ParseError("Failed to fetch series: ${e.message}", e)
        }
    }

    /**
     * Fetch movie details
     *
     * @param slug Movie slug
     * @return ScraperResult with Movie object
     */
    suspend fun getMovieDetails(slug: String): ScraperResult<Movie> = withContext(Dispatchers.IO) {
        try {
            enforceRateLimit()

            val url = IMVBoxUrlBuilder.buildMovieDetailUrl(slug)
            val html = fetchHtml(url)
            val doc = Jsoup.parse(html)

            val metadata = IMVBoxHtmlParser.parseMovieMetadata(doc)
                ?: return@withContext ScraperResult.ParseError("Failed to parse movie metadata", null)

            val movie = Movie(
                id = "imvbox_$slug".hashCode(),
                title = metadata.title,
                description = metadata.description ?: "",
                posterUrl = metadata.posterUrl ?: IMVBoxUrlBuilder.buildMoviePosterUrl(slug),
                backdropUrl = metadata.posterUrl,
                farsilandUrl = url,
                year = metadata.year,
                rating = metadata.rating,
                genres = metadata.genres
            )

            ScraperResult.Success(movie)
        } catch (e: IOException) {
            Log.e(TAG, "Network error fetching movie details", e)
            ScraperResult.NetworkError("Failed to fetch movie details: ${e.message}", e)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching movie details", e)
            ScraperResult.ParseError("Failed to fetch movie details: ${e.message}", e)
        }
    }

    /**
     * Fetch episodes for a series season
     *
     * @param showSlug Show slug
     * @param season Season number
     * @param seriesId Parent series ID
     * @param seriesTitle Parent series title
     * @return ScraperResult with list of Episode objects
     */
    suspend fun getEpisodes(
        showSlug: String,
        season: Int,
        seriesId: Int = 0,
        seriesTitle: String? = null
    ): ScraperResult<List<Episode>> = withContext(Dispatchers.IO) {
        try {
            enforceRateLimit()

            val url = IMVBoxUrlBuilder.buildSeasonUrl(showSlug, season)
            val html = fetchHtml(url)
            val doc = Jsoup.parse(html)

            val episodeItems = IMVBoxHtmlParser.parseEpisodeList(doc, season)

            val episodes = episodeItems.map { item ->
                Episode(
                    id = "imvbox_${showSlug}_s${item.season}e${item.episode}".hashCode(),
                    seriesId = seriesId,
                    seriesTitle = seriesTitle,
                    title = item.title,
                    thumbnailUrl = item.thumbnailUrl,
                    farsilandUrl = IMVBoxUrlBuilder.buildEpisodeDetailUrl(showSlug, item.season, item.episode),
                    season = item.season,
                    episode = item.episode
                )
            }

            Log.d(TAG, "Fetched ${episodes.size} episodes for $showSlug season $season")
            ScraperResult.Success(episodes)
        } catch (e: IOException) {
            Log.e(TAG, "Network error fetching episodes", e)
            ScraperResult.NetworkError("Failed to fetch episodes: ${e.message}", e)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching episodes", e)
            ScraperResult.ParseError("Failed to fetch episodes: ${e.message}", e)
        }
    }

    /**
     * Extract video URL from movie play page
     * Uses authenticated session for access to premium content
     *
     * @param slug Movie slug
     * @return ScraperResult with VideoUrl object
     */
    suspend fun extractMovieVideoUrl(slug: String): ScraperResult<VideoUrl> = withContext(Dispatchers.IO) {
        try {
            enforceRateLimit()

            val url = IMVBoxUrlBuilder.buildMoviePlayUrl(slug)
            val html = fetchHtmlAuthenticated(url)
            val doc = Jsoup.parse(html)

            val hlsUrl = IMVBoxHtmlParser.extractHlsUrl(doc)
                ?: return@withContext ScraperResult.NoDataFound("No video URL found for movie: $slug")

            val videoUrl = VideoUrl(
                url = hlsUrl,
                quality = "Auto", // HLS adaptive
                mirror = "streaming.imvbox.com"
            )

            ScraperResult.Success(videoUrl)
        } catch (e: IOException) {
            Log.e(TAG, "Network error extracting video URL", e)
            ScraperResult.NetworkError("Failed to extract video URL: ${e.message}", e)
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting video URL", e)
            ScraperResult.ParseError("Failed to extract video URL: ${e.message}", e)
        }
    }

    /**
     * Extract video URL from episode play page
     * Uses authenticated session for access to premium content
     *
     * @param showSlug Show slug
     * @param season Season number
     * @param episode Episode number
     * @return ScraperResult with VideoUrl object
     */
    suspend fun extractEpisodeVideoUrl(
        showSlug: String,
        season: Int,
        episode: Int
    ): ScraperResult<VideoUrl> = withContext(Dispatchers.IO) {
        try {
            enforceRateLimit()

            val url = IMVBoxUrlBuilder.buildEpisodePlayUrl(showSlug, season, episode)
            val html = fetchHtmlAuthenticated(url)
            val doc = Jsoup.parse(html)

            val hlsUrl = IMVBoxHtmlParser.extractHlsUrl(doc)
                ?: return@withContext ScraperResult.NoDataFound("No video URL found for episode: s${season}e${episode}")

            val videoUrl = VideoUrl(
                url = hlsUrl,
                quality = "Auto", // HLS adaptive
                mirror = "streaming.imvbox.com"
            )

            ScraperResult.Success(videoUrl)
        } catch (e: IOException) {
            Log.e(TAG, "Network error extracting episode video URL", e)
            ScraperResult.NetworkError("Failed to extract video URL: ${e.message}", e)
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting episode video URL", e)
            ScraperResult.ParseError("Failed to extract video URL: ${e.message}", e)
        }
    }

    /**
     * Extract video URL from any IMVBox page URL
     *
     * IMVBox uses TWO video sources:
     * 1. YouTube embeds - For newer/popular content (requires JS execution)
     * 2. Self-hosted HLS - For older content (streaming.imvbox.com)
     *
     * First tries static HTML parsing, then falls back to WebView extraction
     * for dynamically loaded content.
     *
     * @param pageUrl Full IMVBox page URL
     * @return ScraperResult with VideoUrl object
     */
    suspend fun extractVideoUrl(pageUrl: String): ScraperResult<VideoUrl> = withContext(Dispatchers.IO) {
        // Build play URL - handle query parameters correctly
        val playUrl = buildPlayUrl(pageUrl)

        Log.d(TAG, "Extracting video URL from: $playUrl")

        // Method 1: Try static HTML parsing first (faster, works for some content)
        try {
            enforceRateLimit()
            Log.d(TAG, ">>> Fetching HTML for: $playUrl")
            val html = fetchHtmlAuthenticated(playUrl)
            val doc = Jsoup.parse(html)
            Log.d(TAG, ">>> HTML fetched, length=${html.length}")

            // Check if content requires subscription
            if (IMVBoxHtmlParser.requiresSubscription(doc)) {
                Log.w(TAG, "Content requires IMVBox Plus subscription")
                return@withContext ScraperResult.NoDataFound("This content requires an IMVBox Plus subscription")
            }

            // Try HLS extraction FIRST - this is the FULL MOVIE
            // YouTube embeds on IMVBox are typically trailers, not full content
            val hlsUrl = IMVBoxHtmlParser.extractHlsUrl(doc)
            Log.d(TAG, ">>> Static HLS extraction result: ${hlsUrl ?: "null"}")
            if (hlsUrl != null) {
                Log.d(TAG, "Found HLS URL (full movie) via static parsing: $hlsUrl")
                return@withContext ScraperResult.Success(
                    VideoUrl(url = hlsUrl, quality = "Auto", mirror = "streaming.imvbox.com")
                )
            }

            // Fall back to YouTube if no HLS found (likely a trailer)
            // BUT: Don't return YouTube yet - the actual movie HLS might be loaded dynamically
            // We should try WebView extraction first to get the real HLS stream
            val youtubeId = IMVBoxHtmlParser.extractYouTubeVideoId(doc)
            Log.d(TAG, ">>> Static YouTube extraction result: ${youtubeId ?: "null"}")
            if (youtubeId != null) {
                Log.d(TAG, "Found YouTube video in static HTML, but will try WebView for HLS first...")
                // Don't return here - continue to WebView extraction
                // YouTube in static HTML is usually just the trailer
            }

            Log.d(TAG, ">>> Static parsing found no HLS, trying WebView extraction for dynamic content...")
        } catch (e: Exception) {
            Log.w(TAG, "Static parsing error (will try WebView): ${e.message}")
        }

        // Method 2: Use WebView to extract dynamically loaded video URLs
        // This handles dynamically loaded HLS (full movie) and YouTube (trailer fallback)
        try {
            Log.d(TAG, ">>> Starting WebView extraction for: $playUrl")
            val extractor = IMVBoxVideoExtractor(context)
            val result = extractor.extractVideoSource(playUrl)
            Log.d(TAG, ">>> WebView extraction result: $result")
            return@withContext when (result) {
                is IMVBoxVideoExtractor.VideoSource.HLS -> {
                    // HLS is the full movie - preferred!
                    Log.d(TAG, ">>> SUCCESS: Found HLS stream (full movie): ${result.url}")
                    ScraperResult.Success(
                        VideoUrl(url = result.url, quality = "Auto", mirror = "streaming.imvbox.com")
                    )
                }
                is IMVBoxVideoExtractor.VideoSource.YouTube -> {
                    // YouTube is likely a trailer - fallback only
                    Log.d(TAG, ">>> FALLBACK: Found YouTube video (trailer): ${result.videoId}")
                    val youtubeUrl = "https://www.youtube.com/watch?v=${result.videoId}"
                    ScraperResult.Success(
                        VideoUrl(url = youtubeUrl, quality = "YouTube", mirror = "youtube.com")
                    )
                }
                is IMVBoxVideoExtractor.VideoSource.Error -> {
                    Log.e(TAG, ">>> ERROR: WebView extraction failed: ${result.message}")
                    ScraperResult.NoDataFound("No video URL found: ${result.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, ">>> EXCEPTION in WebView extraction", e)
            ScraperResult.ParseError("Failed to extract video URL: ${e.message}", e)
        }
    }

    /**
     * Get CSRF token for POST requests
     * Caches token for 5 minutes
     */
    private suspend fun getCsrfToken(): String? {
        val now = System.currentTimeMillis()

        // Use cached token if still valid
        if (cachedCsrfToken != null && (now - csrfTokenTime) < csrfTokenMaxAge) {
            return cachedCsrfToken
        }

        return try {
            val html = fetchHtml(IMVBoxUrlBuilder.buildMoviesListUrl())
            val doc = Jsoup.parse(html)
            val token = IMVBoxHtmlParser.extractCsrfToken(doc)

            if (token != null) {
                cachedCsrfToken = token
                csrfTokenTime = now
            }

            token
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching CSRF token", e)
            null
        }
    }

    /**
     * Fetch HTML content from URL
     * Uses authenticated client if user is logged in to IMVBox
     */
    private suspend fun fetchHtml(url: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .header("Accept-Language", "en-US,en;q=0.9")
            .get()
            .build()

        executeRequest(request, useAuthenticatedClient = false)
    }

    /**
     * Fetch HTML content from URL using authenticated session
     * Required for video playback URLs that need login
     */
    private suspend fun fetchHtmlAuthenticated(url: String): String = withContext(Dispatchers.IO) {
        // Ensure we're authenticated before making the request
        val isAuthenticated = IMVBoxAuthManager.ensureAuthenticated(context)
        if (isAuthenticated) {
            Log.d(TAG, "Using authenticated session for: $url")
        } else {
            Log.w(TAG, "Not authenticated, attempting without login for: $url")
        }

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .header("Accept-Language", "en-US,en;q=0.9")
            .get()
            .build()

        executeRequest(request, useAuthenticatedClient = isAuthenticated)
    }

    /**
     * Execute HTTP request with size limit
     * @param request The request to execute
     * @param useAuthenticatedClient If true, uses the authenticated OkHttpClient with session cookies
     */
    private fun executeRequest(request: Request, useAuthenticatedClient: Boolean = false): String {
        val client = if (useAuthenticatedClient) {
            IMVBoxAuthManager.getAuthenticatedClient()
        } else {
            httpClient
        }

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code}: ${response.message}")
            }

            val body = response.body ?: throw IOException("Empty response body")

            // Check Content-Length to prevent OOM
            val contentLength = response.header("Content-Length")?.toLongOrNull() ?: 0
            if (contentLength > MAX_RESPONSE_SIZE) {
                throw IOException("Response too large: $contentLength bytes (max ${MAX_RESPONSE_SIZE / 1024 / 1024}MB)")
            }

            return body.string()
        }
    }
}
