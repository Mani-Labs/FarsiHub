package com.example.farsilandtv.data.scraper

import com.example.farsilandtv.FarsilandApp
import com.example.farsilandtv.data.api.RetrofitClient
import com.example.farsilandtv.data.models.VideoUrl
import com.example.farsilandtv.data.namakade.NamakadeApiService
import com.example.farsilandtv.utils.SecureRegex
import com.example.farsilandtv.utils.SecureUrlValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.util.concurrent.ConcurrentHashMap

/**
 * Data class to hold cached video URLs with timestamp
 */
data class CachedUrls(
    val urls: List<VideoUrl>,
    val timestamp: Long
)

/**
 * HTML scraper for extracting video URLs from Farsiland.com and FarsiPlex.com episode/movie pages
 *
 * Supports two distinct HTML structures:
 *
 * FARSILAND.COM (Farsiland theme):
 * - Video URLs in HTML microdata: <link itemprop="contentUrl" href="...mp4">
 * - Download forms with fileid hidden inputs
 * - POST to /get/ endpoint for mirror URLs
 *
 * FARSIPLEX.COM (DooPlay WordPress theme):
 * - Player items with data attributes: <li data-playerid> or <li data-post>
 * - Direct MP4 URLs in page HTML/JavaScript
 * - Iframe sources with video URLs
 *
 * NO AUTHENTICATION REQUIRED - URLs are publicly accessible in HTML!
 *
 * CACHING: Video URLs are cached for 5 minutes to improve replay and quality switching performance
 */
object VideoUrlScraper {

    private const val TAG = "VideoUrlScraper"
    private val httpClient = RetrofitClient.getHttpClient()

    // Thread-safe cache for video URLs (page URL -> cached URLs with timestamp)
    private val urlCache = ConcurrentHashMap<String, CachedUrls>()
    private const val CACHE_DURATION = 5 * 60 * 1000L // 5 minutes in milliseconds

    /**
     * Extract video URLs from episode or movie page
     *
     * @param pageUrl Full URL to episode/movie page (e.g., https://farsiland.com/series/shoghal/s01e01/)
     * @return ScraperResult containing List of VideoUrl objects sorted by quality (1080p first)
     *         Returns Success, NetworkError, ParseError, or NoDataFound
     *
     * Example HTML structure:
     * <link itemprop="contentUrl" href="https://d1.flnd.buzz/series/shoghal/01.1080.mp4">
     * <link itemprop="contentUrl" href="https://d1.flnd.buzz/series/shoghal/01.720.mp4">
     * <link itemprop="contentUrl" href="https://d1.flnd.buzz/series/shoghal/01.480.mp4">
     *
     * CACHING: Results are cached for 5 minutes to improve replay and quality switching performance
     */
    suspend fun extractVideoUrls(pageUrl: String): ScraperResult<List<VideoUrl>> = withContext(Dispatchers.IO) {
        try {
            android.util.Log.d(TAG, "========================================")
            android.util.Log.d(TAG, "extractVideoUrls() called")
            android.util.Log.d(TAG, "Page URL: $pageUrl")

            // SECURITY: Validate URL security before processing (Issue M9)
            val normalizedUrl = SecureUrlValidator.normalizeToHttps(pageUrl)
            if (normalizedUrl == null) {
                android.util.Log.e(TAG, "SECURITY: Rejected insecure or untrusted URL: $pageUrl")
                return@withContext ScraperResult.ParseError(
                    "Security: Only HTTPS URLs from trusted domains are allowed",
                    SecurityException("Cleartext HTTP traffic not permitted")
                )
            }

            // Use normalized HTTPS URL for all operations
            val securePageUrl = normalizedUrl
            if (securePageUrl != pageUrl) {
                android.util.Log.d(TAG, "URL normalized to HTTPS: $pageUrl -> $securePageUrl")
            }

            // Check cache first (use normalized URL as cache key)
            urlCache[pageUrl]?.let { cached ->
                val cacheAge = System.currentTimeMillis() - cached.timestamp
                if (cacheAge < CACHE_DURATION) {
                    val remainingTime = (CACHE_DURATION - cacheAge) / 1000
                    android.util.Log.d(TAG, "CACHE HIT for: $pageUrl")
                    android.util.Log.d(TAG, "Cache age: ${cacheAge / 1000}s, expires in: ${remainingTime}s")
                    android.util.Log.d(TAG, "Returning ${cached.urls.size} cached URLs")
                    android.util.Log.d(TAG, "========================================")
                    return@withContext ScraperResult.Success(cached.urls)
                } else {
                    android.util.Log.d(TAG, "Cache EXPIRED (age: ${cacheAge / 1000}s)")
                    // Remove expired entry
                    urlCache.remove(pageUrl)
                }
            }

            // Cache MISS - proceed with scraping
            android.util.Log.d(TAG, "Cache MISS - scraping HTML")

            // Determine source domain
            val isNamakade = securePageUrl.contains("namakade.com", ignoreCase = true)
            val isFarsiPlex = securePageUrl.contains("farsiplex.com", ignoreCase = true)
            val sourceType = when {
                isNamakade -> "Namakade"
                isFarsiPlex -> "FarsiPlex"
                else -> "Farsiland"
            }
            android.util.Log.d(TAG, "Detected source: $sourceType")

            // Namakade uses a different scraper (NamakadeApiService)
            if (isNamakade) {
                return@withContext extractFromNamakade(securePageUrl)
            }

            // Fetch HTML content
            android.util.Log.d(TAG, "Fetching HTML content...")
            val html = fetchHtml(securePageUrl)
            android.util.Log.d(TAG, "HTML fetched. Length: ${html.length} characters")

            // Parse HTML with Jsoup
            android.util.Log.d(TAG, "Parsing HTML with Jsoup...")
            val doc = Jsoup.parse(html)

            // Route to appropriate extraction method based on domain
            val urlsList = if (isFarsiPlex) {
                extractFromFarsiPlex(doc, securePageUrl)
            } else {
                extractFromFarsiland(doc, securePageUrl)
            }

            if (urlsList.isNotEmpty()) {
                // SECURITY: Filter and normalize URLs to HTTPS only (Issue M9)
                val secureUrls = urlsList.mapNotNull { videoUrl ->
                    val normalizedVideoUrl = SecureUrlValidator.normalizeToHttps(videoUrl.url)
                    if (normalizedVideoUrl != null) {
                        if (normalizedVideoUrl != videoUrl.url) {
                            android.util.Log.d(TAG, "Normalized video URL: ${videoUrl.url} -> $normalizedVideoUrl")
                        }
                        videoUrl.copy(url = normalizedVideoUrl)
                    } else {
                        android.util.Log.w(TAG, "SECURITY: Filtered out insecure video URL: ${videoUrl.url}")
                        null
                    }
                }

                if (secureUrls.isEmpty()) {
                    android.util.Log.e(TAG, "SECURITY: All video URLs failed security validation")
                    android.util.Log.e(TAG, "========================================")
                    return@withContext ScraperResult.NoDataFound("No secure video URLs found. All URLs were HTTP or from untrusted domains.")
                }

                // Cache the result
                urlCache[pageUrl] = CachedUrls(secureUrls, System.currentTimeMillis())
                android.util.Log.d(TAG, "SUCCESS: Found ${secureUrls.size} secure video URLs from $sourceType")
                android.util.Log.d(TAG, "URLs cached for ${CACHE_DURATION / 1000}s")
                android.util.Log.d(TAG, "========================================")
                return@withContext ScraperResult.Success(secureUrls)
            }

            // No URLs found - page loaded successfully but no video URLs found
            android.util.Log.e(TAG, "FAILED: No video URLs found via any extraction method")
            android.util.Log.e(TAG, "========================================")
            ScraperResult.NoDataFound("No video URLs found on page. HTML structure may have changed.")

        } catch (e: Exception) {
            android.util.Log.e(TAG, "========================================")
            android.util.Log.e(TAG, "EXCEPTION in extractVideoUrls", e)
            android.util.Log.e(TAG, "Page URL: $pageUrl")
            android.util.Log.e(TAG, "Exception: ${e.message}")
            e.printStackTrace()
            android.util.Log.e(TAG, "========================================")

            // M4: Log to Firebase Crashlytics for production debugging
            FarsilandApp.logError("Video URL scraping failed: $pageUrl", e)

            // Classify exception type for proper error handling
            when (e) {
                is java.net.UnknownHostException,
                is java.net.SocketTimeoutException,
                is java.net.ConnectException,
                is java.io.IOException -> {
                    ScraperResult.NetworkError("Network error: ${e.message}", e)
                }
                else -> {
                    ScraperResult.ParseError("Failed to parse video URLs: ${e.message}", e)
                }
            }
        }
    }

    /**
     * Extract video URLs from Namakade.com
     * Uses NamakadeApiService which has its own HTML parser
     *
     * @param pageUrl Full URL to episode/movie page
     * @return ScraperResult with video URLs or error
     */
    private suspend fun extractFromNamakade(pageUrl: String): ScraperResult<List<VideoUrl>> {
        android.util.Log.d(TAG, "=== Namakade Extraction ===")

        return try {
            val namakadeService = NamakadeApiService()
            val videoUrl = namakadeService.extractVideoUrl(pageUrl)

            if (videoUrl != null) {
                val urlsList = listOf(videoUrl)
                // Cache the result
                urlCache[pageUrl] = CachedUrls(urlsList, System.currentTimeMillis())
                android.util.Log.d(TAG, "SUCCESS: Found 1 video URL from Namakade")
                android.util.Log.d(TAG, "URL: ${videoUrl.url}")
                android.util.Log.d(TAG, "========================================")
                ScraperResult.Success(urlsList)
            } else {
                android.util.Log.w(TAG, "No video URL found from Namakade")
                android.util.Log.d(TAG, "========================================")
                ScraperResult.NoDataFound("No video URL found on Namakade page")
            }
        } catch (e: java.io.IOException) {
            android.util.Log.e(TAG, "Network error extracting from Namakade", e)
            android.util.Log.d(TAG, "========================================")
            ScraperResult.NetworkError("Network error: ${e.message}", e)
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error extracting from Namakade", e)
            android.util.Log.d(TAG, "========================================")
            ScraperResult.ParseError("Failed to parse Namakade page: ${e.message}", e)
        }
    }

    /**
     * Extract video URLs from FarsiPlex.com (DooPlay theme)
     * FarsiPlex uses DooPlay REST API instead of embedding URLs in HTML
     */
    private suspend fun extractFromFarsiPlex(doc: Document, pageUrl: String): List<VideoUrl> {
        android.util.Log.d(TAG, "=== FarsiPlex Extraction ===")

        // Method 1: Use DooPlay REST API (primary method)
        android.util.Log.d(TAG, "Method 1: Using DooPlay REST API...")
        val apiUrls = extractFromDooPlayAPI(doc, pageUrl)
        if (apiUrls.isNotEmpty()) {
            android.util.Log.d(TAG, "Found ${apiUrls.size} URLs from DooPlay API")
            return apiUrls
        }

        // Method 2: Extract direct MP4 URLs from HTML/JavaScript (fallback)
        android.util.Log.d(TAG, "Method 2: Extracting direct MP4 links...")
        val directUrls = extractDirectMp4Links(doc)
        if (directUrls.isNotEmpty()) {
            android.util.Log.d(TAG, "Found ${directUrls.size} direct MP4 URLs")
            return directUrls
        }

        android.util.Log.w(TAG, "No URLs found via FarsiPlex extraction methods")
        return emptyList()
    }

    /**
     * Extract video URLs using DooPlay REST API
     * Endpoint: https://farsiplex.com/wp-json/dooplayer/v2/{post_id}/{type}/{num}
     */
    private suspend fun extractFromDooPlayAPI(doc: Document, pageUrl: String): List<VideoUrl> {
        try {
            // Extract post ID from hidden input: <input type='hidden' name='id' value='13800'/>
            val postIdInput = doc.select("input[name=id]").firstOrNull()
            if (postIdInput == null) {
                android.util.Log.w(TAG, "No post ID input found in form")
                return emptyList()
            }

            val postId = postIdInput.attr("value")
            if (postId.isEmpty()) {
                android.util.Log.w(TAG, "Empty post ID")
                return emptyList()
            }

            android.util.Log.d(TAG, "Extracted post ID: $postId")

            // Determine content type from URL (movie vs tvshow/episode)
            val contentType = when {
                pageUrl.contains("/movie/", ignoreCase = true) -> "movie"
                pageUrl.contains("/tvshow/", ignoreCase = true) -> "tv"
                pageUrl.contains("/episode/", ignoreCase = true) -> "tv"
                else -> "movie" // Default to movie
            }

            android.util.Log.d(TAG, "Content type: $contentType")

            // P2 FIX: Issue #9 - Parallelize API requests for ~400% speedup
            // Previous code: Sequential for loop waited for each request (500ms × 5 = 2500ms worst case)
            // Fixed: Parallel async requests complete in ~500ms (time of slowest single request)
            val videoUrls = mutableListOf<VideoUrl>()

            // Use coroutineScope to create scope for async
            coroutineScope {
                // Launch all 5 API requests in parallel
                val deferredResults = (1..5).map { num ->
                    async {
                        val apiUrl = "https://farsiplex.com/wp-json/dooplayer/v2/$postId/$contentType/$num"
                        android.util.Log.d(TAG, "Trying API: $apiUrl")
                        fetchFromDooPlayAPI(apiUrl, num)
                    }
                }

                // Collect all results (awaitAll waits for all to complete)
                val allResults = deferredResults.awaitAll()
                for (urls in allResults) {
                    if (urls.isNotEmpty()) {
                        videoUrls.addAll(urls)
                    }
                }
            }

            android.util.Log.d(TAG, "Parallel API requests completed, found ${videoUrls.size} total URLs")

            if (videoUrls.isNotEmpty()) {
                // Remove duplicates and sort by quality
                val uniqueUrls = videoUrls.distinctBy { it.url }
                android.util.Log.d(TAG, "Found ${uniqueUrls.size} unique video URLs from API")
                return uniqueUrls.sortedWith(
                    compareByDescending<VideoUrl> {
                        when (it.quality) {
                            "1080p" -> 3
                            "720p" -> 2
                            "480p" -> 1
                            else -> 0
                        }
                    }
                )
            }

        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error extracting from DooPlay API", e)
        }

        return emptyList()
    }

    /**
     * Fetch video URL from DooPlay API endpoint
     * AUDIT FIX #12: Wrapped in .use {} to prevent resource leaks on exceptions
     */
    private suspend fun fetchFromDooPlayAPI(apiUrl: String, serverNum: Int): List<VideoUrl> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(apiUrl)
                .get()
                .build()

            // AUDIT FIX #12: Use 'use' to ensure response is always closed, even if reading fails
            val result: List<VideoUrl> = httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    // P1 FIX: Issue #3 - OOM Protection with BOUNDED READ for chunked encoding
                    // Problem: contentLength = -1 for chunked encoding, so header check fails
                    // Solution: Read with hard 5MB limit regardless of Content-Length header
                    val body = response.body ?: return@use emptyList()

                    // Step 1: Fast fail for known large sizes (via Content-Length header)
                    val contentLength = body.contentLength()
                    if (contentLength > 5_000_000) {
                        android.util.Log.w(TAG, "Response too large via header: $contentLength bytes")
                        return@use emptyList()
                    }

                    // Step 2: BOUNDED READ - Read max 5MB, stops even if stream is larger/infinite
                    val maxBytes = 5L * 1024 * 1024 // 5MB hard limit
                    val source = body.source()
                    val buffer = okio.Buffer()
                    var totalRead = 0L

                    try {
                        while (totalRead < maxBytes) {
                            val bytesRead = source.read(buffer, maxBytes - totalRead)
                            if (bytesRead == -1L) break // End of stream
                            totalRead += bytesRead
                        }
                    } catch (e: Exception) {
                        android.util.Log.w(TAG, "Error reading response stream", e)
                        return@use emptyList()
                    }

                    // If we hit the limit and there's more data, reject it
                    if (totalRead >= maxBytes && !source.exhausted()) {
                        android.util.Log.w(TAG, "Response exceeded 5MB limit (likely malicious chunked stream)")
                        return@use emptyList()
                    }

                    val bodyString = buffer.readUtf8()

                    android.util.Log.d(TAG, "API Response (server $serverNum): ${bodyString.take(500)}")

                    // Parse JSON response
                    // Expected format: {"embed_url": "https://...", "type": "iframe"}
                    // Or direct: {"type": "mp4", "url": "https://...mp4"}
                    extractUrlsFromDooPlayResponse(bodyString, serverNum)
                } else {
                    android.util.Log.d(TAG, "API returned HTTP ${response.code} for server $serverNum")
                    emptyList()
                }
            }

            if (result.isNotEmpty()) {
                return@withContext result
            }

        } catch (e: Exception) {
            android.util.Log.d(TAG, "Error fetching from API server $serverNum: ${e.message}")
        }

        return@withContext emptyList()
    }

    /**
     * Extract MP4 URLs from DooPlay API JSON response
     */
    private suspend fun extractUrlsFromDooPlayResponse(jsonResponse: String, serverNum: Int): List<VideoUrl> = withContext(Dispatchers.IO) {
        val videoUrls = mutableListOf<VideoUrl>()

        try {
            // Check for embed_url (jwplayer page)
            // Format: "embed_url":"https:\/\/farsiplex.com\/jwplayer\/?source=..."
            val embedPattern = Regex("""["']embed_url["']\s*:\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
            val embedMatch = embedPattern.find(jsonResponse)
            if (embedMatch != null) {
                val embedUrl = embedMatch.groupValues[1]
                    .replace("\\/", "/") // Unescape JSON slashes

                android.util.Log.d(TAG, "Found embed URL: $embedUrl")

                // Extract video URL from 'source' parameter and convert fake CDN to real
                // Format: https://farsiplex.com/jwplayer/?source=https%3A%2F%2Fcdn2.farsiland.com%2F...SadDam-720.mp4
                // Fake CDN: cdn2.farsiland.com
                // Real CDN: d1.flnd.buzz, d2.flnd.buzz, s1.farsicdn.buzz, s2.farsicdn.buzz
                try {
                    val uri = java.net.URI(embedUrl)
                    val queryParams = uri.query?.split("&")?.associate {
                        val parts = it.split("=", limit = 2)
                        parts[0] to (parts.getOrNull(1) ?: "")
                    } ?: emptyMap()

                    val sourceUrl = queryParams["source"]
                    if (!sourceUrl.isNullOrEmpty()) {
                        val decodedUrl = java.net.URLDecoder.decode(sourceUrl, "UTF-8")
                        val quality = detectQualityFromUrl(decodedUrl)

                        android.util.Log.d(TAG, "Source URL: $decodedUrl")
                        android.util.Log.d(TAG, "Detected quality: $quality")

                        // Convert fake CDN domain to real working CDN domains
                        // cdn2.farsiland.com → d1.flnd.buzz and d2.flnd.buzz
                        if (decodedUrl.contains("cdn2.farsiland.com", ignoreCase = true)) {
                            val path = decodedUrl.substringAfter("cdn2.farsiland.com")

                            // Create URLs for both mirrors
                            val mirror1Url = "https://d1.flnd.buzz$path"
                            val mirror2Url = "https://d2.flnd.buzz$path"

                            videoUrls.add(VideoUrl(
                                url = mirror1Url,
                                quality = quality,
                                mirror = "Server ${serverNum}A"
                            ))
                            videoUrls.add(VideoUrl(
                                url = mirror2Url,
                                quality = quality,
                                mirror = "Server ${serverNum}B"
                            ))

                            android.util.Log.d(TAG, "Converted to real CDN URLs:")
                            android.util.Log.d(TAG, "  Mirror 1: $mirror1Url ($quality)")
                            android.util.Log.d(TAG, "  Mirror 2: $mirror2Url ($quality)")

                            // Return immediately - we got working URLs!
                            return@withContext videoUrls
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.d(TAG, "Could not convert source URL to real CDN, falling back to jwplayer scraping")
                }

                // FALLBACK: Fetch the jwplayer page (for older content or different CDN structures)
                var expectedQuality = ""
                try {
                    val jwPlayerHtml = fetchHtml(embedUrl)
                    android.util.Log.d(TAG, "Fetched jwplayer page (${jwPlayerHtml.length} bytes)")

                    // METHOD 1: Extract from jwplayer sources array (supports multiple qualities)
                    // Pattern: sources:[{file:"url1.mp4",label:"1080p"},{file:"url2.mp4",label:"720p"}]
                    // SECURITY: Use timeout-protected regex to prevent ReDoS attacks
                    val sourcesArrayPattern = Regex("""sources\s*:\s*\[(.*?)\]""", RegexOption.DOT_MATCHES_ALL)
                    val sourcesMatch = SecureRegex.findWithTimeout(sourcesArrayPattern, jwPlayerHtml)

                    if (sourcesMatch != null) {
                        val sourcesContent = sourcesMatch.groupValues[1]
                        android.util.Log.d(TAG, "Found sources array: ${sourcesContent.take(200)}")

                        // Extract each source object: {file:"url",label:"quality"}
                        // SECURITY: Simplified pattern to reduce backtracking risk
                        val sourcePattern = Regex("""\{\s*file\s*:\s*["']([^"']+)["'](?:[^}]*label\s*:\s*["']([^"']+)["'])?[^}]*\}""", RegexOption.IGNORE_CASE)
                        val sourceMatches = SecureRegex.findAllWithTimeout(sourcePattern, sourcesContent)

                        for (sourceMatch in sourceMatches) {
                            val url = sourceMatch.groupValues[1].replace("\\/", "/")
                            val label = sourceMatch.groupValues.getOrNull(2)?.takeIf { it.isNotBlank() }

                            // Determine quality: Use label if it looks like a quality (contains p),
                            // otherwise use expectedQuality from source parameter,
                            // finally fall back to detecting from URL
                            val quality = when {
                                label?.contains("p", ignoreCase = true) == true -> label
                                expectedQuality.isNotEmpty() -> expectedQuality
                                else -> detectQualityFromUrl(url)
                            }

                            videoUrls.add(VideoUrl(
                                url = url,
                                quality = quality,
                                mirror = "Server $serverNum"
                            ))
                            android.util.Log.d(TAG, "Extracted from sources: $quality - $url")
                        }
                    }

                    // METHOD 2: Extract M3U8 master playlist (HLS adaptive streaming)
                    val m3u8Pattern = Regex(""""file"\s*:\s*"([^"]+\.m3u8[^"]*)"""", RegexOption.IGNORE_CASE)
                    val m3u8Match = m3u8Pattern.find(jwPlayerHtml)

                    if (m3u8Match != null) {
                        val m3u8Url = m3u8Match.groupValues[1].replace("\\/", "/")
                        android.util.Log.d(TAG, "Found M3U8 master playlist: $m3u8Url")

                        // Parse M3U8 playlist to extract quality variants
                        val m3u8Qualities = parseM3U8Playlist(m3u8Url)
                        if (m3u8Qualities.isNotEmpty()) {
                            videoUrls.addAll(m3u8Qualities)
                            android.util.Log.d(TAG, "Extracted ${m3u8Qualities.size} qualities from M3U8")
                        } else {
                            // Fallback: Add M3U8 as single adaptive URL
                            videoUrls.add(VideoUrl(
                                url = m3u8Url,
                                quality = "Auto (HLS)",
                                mirror = "Server $serverNum"
                            ))
                        }
                    }

                    // METHOD 3: Fallback to jw.file and jw.file2 (legacy support)
                    if (videoUrls.isEmpty()) {
                        val filePattern = Regex(""""file"\s*:\s*"([^"]+\.mp4[^"]*)"""", RegexOption.IGNORE_CASE)
                        val file2Pattern = Regex(""""file2"\s*:\s*"([^"]+\.mp4[^"]*)"""", RegexOption.IGNORE_CASE)

                        val fileMatch = filePattern.find(jwPlayerHtml)
                        val file2Match = file2Pattern.find(jwPlayerHtml)

                        if (fileMatch != null) {
                            val url = fileMatch.groupValues[1].replace("\\/", "/")
                            // Use quality detected from source parameter (e.g., "720p")
                            // Don't detect from URL because jw.file might be hardcoded to 480p
                            val quality = expectedQuality.ifEmpty { detectQualityFromUrl(url) }
                            videoUrls.add(VideoUrl(
                                url = url,
                                quality = quality,
                                mirror = "Server 1"
                            ))
                            android.util.Log.d(TAG, "Extracted jw.file: $url (quality: $quality)")
                        }

                        if (file2Match != null) {
                            val url = file2Match.groupValues[1].replace("\\/", "/")
                            // Use quality detected from source parameter
                            val quality = expectedQuality.ifEmpty { detectQualityFromUrl(url) }
                            videoUrls.add(VideoUrl(
                                url = url,
                                quality = quality,
                                mirror = "Server 2"
                            ))
                            android.util.Log.d(TAG, "Extracted jw.file2: $url (quality: $quality)")
                        }
                    }

                } catch (e: Exception) {
                    android.util.Log.e(TAG, "Error fetching jwplayer page: ${e.message}", e)
                }
            }

            // Fallback: Look for any direct MP4 URLs in the response
            if (videoUrls.isEmpty()) {
                val mp4Pattern = Regex("""https?://[^\s"'<>]+\.mp4[^\s"'<>]*""", RegexOption.IGNORE_CASE)
                val matches = mp4Pattern.findAll(jsonResponse)

                for (match in matches) {
                    val url = match.value.trim().replace("\\/", "/")
                    val quality = detectQualityFromUrl(url)
                    videoUrls.add(VideoUrl(
                        url = url,
                        quality = quality,
                        mirror = "server$serverNum"
                    ))
                    android.util.Log.d(TAG, "Found direct MP4 URL: $url")
                }
            }

        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error parsing DooPlay API response", e)
        }

        return@withContext videoUrls
    }

    /**
     * Extract video URLs from Farsiland.com (Farsiland theme)
     */
    private suspend fun extractFromFarsiland(doc: Document, pageUrl: String): List<VideoUrl> {
        android.util.Log.d(TAG, "=== Farsiland Extraction ===")

        // Method 0: Try DooPlay REST API (farsiland.com also uses DooPlay)
        android.util.Log.d(TAG, "Method 0: Using DooPlay REST API...")
        val apiUrls = extractFromDooPlayAPI(doc, pageUrl)
        if (apiUrls.isNotEmpty()) {
            android.util.Log.d(TAG, "Found ${apiUrls.size} URLs from DooPlay API")
            return apiUrls
        }

        // Debug: Log forms and inputs found
        android.util.Log.d(TAG, "=== HTML Structure Debug ===")
        val allForms = doc.select("form")
        android.util.Log.d(TAG, "Total forms: ${allForms.size}")
        val allFileIdInputs = doc.select("input[name=fileid]")
        android.util.Log.d(TAG, "Total fileid inputs: ${allFileIdInputs.size}")
        allFileIdInputs.forEachIndexed { index, input ->
            val parentInfo = input.parent()?.let { "parent=${it.tagName()}, id=${it.attr("id")}, class=${it.attr("class")}" } ?: "no parent"
            android.util.Log.d(TAG, "FileId input $index: value=${input.attr("value")}, $parentInfo")
        }
        android.util.Log.d(TAG, "===========================")

        // Method 1: Extract from microdata
        android.util.Log.d(TAG, "Method 1: Extracting from microdata...")
        val microdataUrls = extractFromMicrodata(doc)
        android.util.Log.d(TAG, "Found ${microdataUrls.size} URLs from microdata")

        // Method 2: Extract from download forms
        android.util.Log.d(TAG, "Method 2: Extracting from download forms...")
        val downloadUrls = extractFromDownloadForms(doc)
        android.util.Log.d(TAG, "Found ${downloadUrls.size} URLs from download forms")

        // Combine results and remove duplicates
        var allUrls = (microdataUrls + downloadUrls).distinctBy { "${it.quality}-${it.mirror}" }

        if (allUrls.isNotEmpty()) {
            // Sort by quality: 1080p → 720p → 480p
            return allUrls.sortedWith(
                compareByDescending<VideoUrl> {
                    when (it.quality) {
                        "1080p" -> 3
                        "720p" -> 2
                        "480p" -> 1
                        else -> 0
                    }
                }
            )
        }

        // Method 3: Try direct MP4 links
        android.util.Log.d(TAG, "Method 3: Searching for direct MP4 links...")
        val directUrls = extractDirectMp4Links(doc)
        if (directUrls.isNotEmpty()) {
            android.util.Log.d(TAG, "Found ${directUrls.size} direct MP4 URLs")
            return directUrls
        }

        // Method 4: Fallback to pattern generation
        android.util.Log.d(TAG, "Method 4: Attempting URL pattern generation...")
        val fallbackUrls = tryGenerateUrls(pageUrl)
        if (fallbackUrls.isNotEmpty()) {
            android.util.Log.d(TAG, "Generated ${fallbackUrls.size} URLs from pattern")
            return fallbackUrls
        }

        android.util.Log.w(TAG, "No URLs found via Farsiland extraction methods")
        return emptyList()
    }

    /**
     * Extract video URLs from DooPlay player items
     * Looks for: <li data-playerid="..."> or <li data-post="...">
     */
    private fun extractFromDooPlayItems(doc: Document): List<VideoUrl> {
        val videoUrls = mutableListOf<VideoUrl>()

        try {
            // Find all player items with data attributes
            val playerItems = doc.select("li[data-playerid], li[data-post]")
            android.util.Log.d(TAG, "Found ${playerItems.size} player items")

            for ((index, item) in playerItems.withIndex()) {
                try {
                    // Get player ID
                    val playerId = item.attr("data-playerid").ifEmpty { item.attr("data-post") }
                    android.util.Log.d(TAG, "Player item $index: playerId=$playerId")

                    // Try to extract quality from text
                    val qualityText = item.select(".title, span").text()
                    val quality = when {
                        qualityText.contains("1080", ignoreCase = true) -> "1080p"
                        qualityText.contains("720", ignoreCase = true) -> "720p"
                        qualityText.contains("480", ignoreCase = true) -> "480p"
                        else -> "720p"
                    }

                    // Try to extract URL from onclick attribute
                    val onClick = item.attr("onclick")
                    val extractedUrl = extractUrlFromJavaScript(onClick)

                    if (extractedUrl != null && extractedUrl.contains(".mp4", ignoreCase = true)) {
                        videoUrls.add(VideoUrl(
                            url = extractedUrl,
                            quality = quality,
                            mirror = VideoUrl.extractMirror(extractedUrl)
                        ))
                        android.util.Log.d(TAG, "Added URL from player item: $extractedUrl")
                    }

                } catch (e: Exception) {
                    android.util.Log.d(TAG, "Error processing player item $index: ${e.message}")
                }
            }

        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error extracting from DooPlay items", e)
        }

        return videoUrls
    }

    /**
     * Extract URLs from iframe sources
     * Looks for: <iframe src="...mp4"> or <iframe data-src="...mp4">
     */
    private fun extractFromIframes(doc: Document): List<VideoUrl> {
        val videoUrls = mutableListOf<VideoUrl>()

        try {
            // Look for iframes with MP4 sources
            val iframes = doc.select("iframe[src*=.mp4], iframe[data-src*=.mp4], iframe")
            android.util.Log.d(TAG, "Found ${iframes.size} iframe elements")

            for ((index, iframe) in iframes.withIndex()) {
                try {
                    val src = iframe.attr("src").ifEmpty { iframe.attr("data-src") }

                    if (src.isEmpty()) {
                        // Try to extract from srcset or other attributes
                        continue
                    }

                    if (src.contains(".mp4", ignoreCase = true)) {
                        val quality = detectQualityFromUrl(src)
                        val mirror = VideoUrl.extractMirror(src)

                        videoUrls.add(VideoUrl(
                            url = src,
                            quality = quality,
                            mirror = mirror
                        ))
                        android.util.Log.d(TAG, "Added URL from iframe $index: $src")
                    }

                } catch (e: Exception) {
                    android.util.Log.d(TAG, "Error processing iframe $index: ${e.message}")
                }
            }

        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error extracting from iframes", e)
        }

        return videoUrls
    }

    /**
     * Extract URL from JavaScript onclick or similar attributes
     */
    private fun extractUrlFromJavaScript(javaScript: String): String? {
        if (javaScript.isEmpty()) return null

        // Look for URLs in various JavaScript patterns
        val patterns = listOf(
            Regex("""https?://[^\s"'<>()]+\.mp4[^\s"'<>]*""", RegexOption.IGNORE_CASE), // Direct URL
            Regex("""'([^']*\.mp4[^']*)'""", RegexOption.IGNORE_CASE), // Single quoted
            Regex(""""([^"]*\.mp4[^"]*)"""", RegexOption.IGNORE_CASE) // Double quoted
        )

        for (pattern in patterns) {
            val match = pattern.find(javaScript)
            if (match != null) {
                // If regex has groups, get the first group, otherwise get the full match
                val url = if (match.groupValues.size > 1) {
                    match.groupValues[1]
                } else {
                    match.value
                }
                if (url.contains(".mp4", ignoreCase = true)) {
                    return url
                }
            }
        }

        return null
    }

    /**
     * Detect video quality from URL
     */
    private fun detectQualityFromUrl(url: String): String {
        return when {
            url.contains("1080", ignoreCase = true) || url.contains("fhd", ignoreCase = true) -> "1080p"
            url.contains("720", ignoreCase = true) || url.contains("hd", ignoreCase = true) -> "720p"
            url.contains("480", ignoreCase = true) -> "480p"
            url.contains("360", ignoreCase = true) || url.contains("sd", ignoreCase = true) -> "360p"
            else -> VideoUrl.extractQuality(url)
        }
    }

    /**
     * Parse M3U8 master playlist to extract quality variants
     * M3U8 format:
     * #EXTM3U
     * #EXT-X-STREAM-INF:BANDWIDTH=2000000,RESOLUTION=1920x1080
     * 1080p.m3u8
     * #EXT-X-STREAM-INF:BANDWIDTH=1000000,RESOLUTION=1280x720
     * 720p.m3u8
     */
    private suspend fun parseM3U8Playlist(m3u8Url: String): List<VideoUrl> = withContext(Dispatchers.IO) {
        val qualities = mutableListOf<VideoUrl>()

        try {
            android.util.Log.d(TAG, "Parsing M3U8 playlist: $m3u8Url")
            val playlistContent = fetchHtml(m3u8Url)

            // Parse M3U8 format for quality variants
            val lines = playlistContent.lines()
            var currentQuality: String? = null
            var currentResolution: String? = null

            for (i in lines.indices) {
                val line = lines[i].trim()

                // Check for stream info line
                if (line.startsWith("#EXT-X-STREAM-INF:")) {
                    // Extract resolution (e.g., RESOLUTION=1920x1080)
                    val resolutionPattern = Regex("""RESOLUTION=(\d+)x(\d+)""")
                    val resolutionMatch = resolutionPattern.find(line)

                    if (resolutionMatch != null) {
                        val height = resolutionMatch.groupValues[2]
                        currentResolution = height
                        currentQuality = when {
                            height.toIntOrNull() ?: 0 >= 1080 -> "1080p"
                            height.toIntOrNull() ?: 0 >= 720 -> "720p"
                            height.toIntOrNull() ?: 0 >= 480 -> "480p"
                            else -> "360p"
                        }
                    }

                    // Extract bandwidth for quality estimation if resolution not available
                    if (currentQuality == null) {
                        val bandwidthPattern = Regex("""BANDWIDTH=(\d+)""")
                        val bandwidthMatch = bandwidthPattern.find(line)

                        if (bandwidthMatch != null) {
                            val bandwidth = bandwidthMatch.groupValues[1].toLongOrNull() ?: 0
                            currentQuality = when {
                                bandwidth >= 2000000 -> "1080p"
                                bandwidth >= 1000000 -> "720p"
                                bandwidth >= 500000 -> "480p"
                                else -> "360p"
                            }
                        }
                    }
                }

                // Next line after stream info is the variant URL
                if (currentQuality != null && !line.startsWith("#") && line.isNotBlank()) {
                    // Make URL absolute if it's relative
                    val variantUrl = if (line.startsWith("http")) {
                        line
                    } else {
                        // Resolve relative URL against M3U8 base URL
                        val baseUrl = m3u8Url.substringBeforeLast("/")
                        "$baseUrl/$line"
                    }

                    qualities.add(VideoUrl(
                        url = variantUrl,
                        quality = currentQuality,
                        mirror = "HLS Stream"
                    ))

                    android.util.Log.d(TAG, "Extracted M3U8 variant: $currentQuality - $variantUrl")

                    // Reset for next variant
                    currentQuality = null
                    currentResolution = null
                }
            }

        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error parsing M3U8 playlist: ${e.message}", e)
        }

        return@withContext qualities
    }

    /**
     * Extract video URLs from HTML microdata
     * Looks for: <link itemprop="contentUrl" href="...mp4">
     */
    private fun extractFromMicrodata(doc: Document): List<VideoUrl> {
        val videoUrls = mutableListOf<VideoUrl>()

        // Find all <link itemprop="contentUrl"> elements
        val contentUrlElements = doc.select("link[itemprop=contentUrl]")

        for (element in contentUrlElements) {
            val href = element.attr("href")

            // Only process MP4 URLs
            if (href.isNotEmpty() && href.contains(".mp4", ignoreCase = true)) {
                val quality = VideoUrl.extractQuality(href)
                val mirror = VideoUrl.extractMirror(href)

                videoUrls.add(
                    VideoUrl(
                        url = href,
                        quality = quality,
                        mirror = mirror
                    )
                )
            }
        }

        // Sort by quality (1080p first)
        return videoUrls.sortedWith(
            compareByDescending<VideoUrl> {
                when (it.quality) {
                    "1080p" -> 3
                    "720p" -> 2
                    "480p" -> 1
                    else -> 0
                }
            }
        )
    }

    /**
     * Extract video URLs from download forms
     * Looks for: <form id="dlform1" action="https://farsiland.com/get/" method="post">
     *            <input type="hidden" name="fileid" value="..."/>
     * Then POSTs the fileid to /get/ to get the actual video URL
     */
    private suspend fun extractFromDownloadForms(doc: Document): List<VideoUrl> {
        try {
            android.util.Log.d(TAG, "Looking for download forms...")

            // Try multiple selectors for download rows/forms
            val downloadRows = mutableListOf<org.jsoup.nodes.Element>()

            // Method 1: tr[id^=link-]
            downloadRows.addAll(doc.select("tr[id^=link-]"))

            // Method 2: Any form with fileid input
            doc.select("form").forEach { form ->
                if (form.select("input[name=fileid]").isNotEmpty()) {
                    downloadRows.add(form)
                }
            }

            // Method 3: Any element containing fileid input
            doc.select("input[name=fileid]").forEach { input ->
                input.parent()?.let { parent ->
                    if (!downloadRows.contains(parent)) {
                        downloadRows.add(parent)
                    }
                }
            }

            if (downloadRows.isEmpty()) {
                android.util.Log.d(TAG, "No download forms found")
                // Log available forms for debugging
                val allForms = doc.select("form")
                android.util.Log.d(TAG, "Total forms found: ${allForms.size}")
                allForms.forEachIndexed { index, form ->
                    android.util.Log.d(TAG, "Form $index: id=${form.attr("id")}, class=${form.attr("class")}")
                }
                return emptyList()
            }

            android.util.Log.d(TAG, "Found ${downloadRows.size} download elements")
            val videoUrls = mutableListOf<VideoUrl>()

            for ((index, row) in downloadRows.withIndex()) {
                try {
                    // Get the fileid from hidden input
                    val fileidInput = row.select("input[name=fileid]").firstOrNull()
                    if (fileidInput == null) {
                        android.util.Log.d(TAG, "Element $index has no fileid input")
                        continue
                    }

                    val fileid = fileidInput.attr("value")
                    if (fileid.isEmpty()) {
                        android.util.Log.d(TAG, "Element $index has empty fileid")
                        continue
                    }

                    // Try multiple methods to extract quality
                    var quality = "unknown"

                    // Method 1: strong.quality
                    val qualityElement = row.select("strong.quality").firstOrNull()
                    if (qualityElement != null) {
                        val qualityText = qualityElement.text().trim()
                        quality = if (qualityText.isNotEmpty()) "${qualityText}p" else quality
                    }

                    // Method 2: Look for quality patterns in text (480, 720, 1080)
                    if (quality == "unknown") {
                        val rowText = row.text()
                        val qualityPattern = Regex("""(\d{3,4})p?""")
                        val match = qualityPattern.find(rowText)
                        if (match != null) {
                            quality = "${match.groupValues[1]}p"
                        }
                    }

                    // Method 3: Check for quality class names
                    if (quality == "unknown") {
                        val classNames = row.classNames().joinToString(" ")
                        when {
                            classNames.contains("1080") -> quality = "1080p"
                            classNames.contains("720") -> quality = "720p"
                            classNames.contains("480") -> quality = "480p"
                        }
                    }

                    android.util.Log.d(TAG, "Element $index: fileid=$fileid, quality=$quality")

                    // POST to /get/ with the fileid to get both mirror URLs
                    val mirrorUrls = postToGetEndpoint(fileid)
                    if (mirrorUrls.isNotEmpty()) {
                        // Add each mirror URL as a separate VideoUrl object
                        mirrorUrls.forEach { videoUrl ->
                            // Extract quality from URL if we didn't get it from HTML
                            val finalQuality = if (quality == "unknown") {
                                VideoUrl.extractQuality(videoUrl)
                            } else {
                                quality
                            }

                            val mirror = VideoUrl.extractMirror(videoUrl)
                            videoUrls.add(
                                VideoUrl(
                                    url = videoUrl,
                                    quality = finalQuality,
                                    mirror = mirror
                                )
                            )
                            android.util.Log.d(TAG, "Got video URL from fileid: $videoUrl (quality: $finalQuality, mirror: $mirror)")
                        }
                    } else {
                        android.util.Log.w(TAG, "No URLs returned from /get/ for fileid: $fileid")
                    }

                } catch (e: Exception) {
                    android.util.Log.e(TAG, "Error processing element $index", e)
                }
            }

            if (videoUrls.isNotEmpty()) {
                android.util.Log.d(TAG, "SUCCESS: Found ${videoUrls.size} video URLs from download forms")

                // Remove duplicates based on quality and mirror
                val uniqueUrls = videoUrls.distinctBy { "${it.quality}-${it.mirror}" }
                android.util.Log.d(TAG, "After removing duplicates: ${uniqueUrls.size} unique URLs")

                return uniqueUrls.sortedWith(
                    compareByDescending<VideoUrl> {
                        when (it.quality) {
                            "1080p" -> 3
                            "720p" -> 2
                            "480p" -> 1
                            else -> 0
                        }
                    }
                )
            }

        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error extracting from download forms", e)
        }

        return emptyList()
    }

    /**
     * POST fileid to /get/ endpoint to get both mirror URLs (d1 and d2)
     * AUDIT FIX #12: Wrapped in .use {} to prevent resource leaks on exceptions
     */
    private suspend fun postToGetEndpoint(fileid: String): List<String> = withContext(Dispatchers.IO) {
        try {
            android.util.Log.d(TAG, "POSTing fileid to /get/: $fileid")

            // Create form body
            val formBody = okhttp3.FormBody.Builder()
                .add("fileid", fileid)
                .build()

            val request = Request.Builder()
                .url("https://farsiland.com/get/")
                .post(formBody)
                .build()

            // AUDIT FIX #12: Use 'use' to ensure response is always closed, even if reading fails
            val result: List<String> = httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    // P1 FIX: Issue #3 - OOM Protection with BOUNDED READ for chunked encoding
                    // Problem: contentLength = -1 for chunked encoding, so header check fails
                    // Solution: Read with hard 5MB limit regardless of Content-Length header
                    val body = response.body ?: return@use emptyList()

                    // Step 1: Fast fail for known large sizes (via Content-Length header)
                    val contentLength = body.contentLength()
                    if (contentLength > 5_000_000) {
                        android.util.Log.w(TAG, "Response too large via header: $contentLength bytes")
                        return@use emptyList()
                    }

                    // Step 2: BOUNDED READ - Read max 5MB, stops even if stream is larger/infinite
                    val maxBytes = 5L * 1024 * 1024 // 5MB hard limit
                    val source = body.source()
                    val buffer = okio.Buffer()
                    var totalRead = 0L

                    try {
                        while (totalRead < maxBytes) {
                            val bytesRead = source.read(buffer, maxBytes - totalRead)
                            if (bytesRead == -1L) break // End of stream
                            totalRead += bytesRead
                        }
                    } catch (e: Exception) {
                        android.util.Log.w(TAG, "Error reading response stream", e)
                        return@use emptyList()
                    }

                    // If we hit the limit and there's more data, reject it
                    if (totalRead >= maxBytes && !source.exhausted()) {
                        android.util.Log.w(TAG, "Response exceeded 5MB limit (likely malicious chunked stream)")
                        return@use emptyList()
                    }

                    val responseBody = buffer.readUtf8()

                    android.util.Log.d(TAG, "Got response from /get/ (length: ${responseBody.length})")

                    // Extract ALL MP4 URLs from response (both d1 and d2 mirrors)
                    // SECURITY: Use timeout-protected regex execution
                    val mp4Regex = Regex("""https?://[^\s"'<>]+\.mp4[^\s"'<>]*""", RegexOption.IGNORE_CASE)
                    val matches = SecureRegex.findAllWithTimeout(mp4Regex, responseBody)
                    val urls = matches.map { it.value }.distinct().toList()

                    if (urls.isNotEmpty()) {
                        android.util.Log.d(TAG, "Found ${urls.size} MP4 URLs in response:")
                        urls.forEachIndexed { index, url ->
                            android.util.Log.d(TAG, "  [$index] $url")
                        }
                        return@use urls
                    }

                    android.util.Log.w(TAG, "No MP4 URLs found in /get/ response")
                    emptyList()
                } else {
                    android.util.Log.w(TAG, "POST to /get/ failed: HTTP ${response.code}")
                    emptyList()
                }
            }

            if (result.isNotEmpty()) {
                return@withContext result
            }

        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error posting to /get/", e)
        }

        return@withContext emptyList()
    }

    /**
     * Follow a redirect URL to get the final video URL
     * Uses POST method as the form requires
     * AUDIT FIX #12: Wrapped in .use {} to prevent resource leaks on exceptions
     */
    private suspend fun followRedirect(redirectUrl: String): String = withContext(Dispatchers.IO) {
        try {
            android.util.Log.d(TAG, "Following redirect with POST...")

            // Create empty POST body
            val emptyBody = okhttp3.RequestBody.create(null, ByteArray(0))

            val request = Request.Builder()
                .url(redirectUrl)
                .post(emptyBody)
                .build()

            // AUDIT FIX #12: Use 'use' to ensure response is always closed
            val result: String = httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    // Get the final URL after following redirects
                    val finalUrl = response.request.url.toString()
                    android.util.Log.d(TAG, "POST redirect final URL: $finalUrl")

                    // P1 FIX: Issue #3 - OOM Protection with BOUNDED READ for chunked encoding
                    val responseBody = response.body?.let { body ->
                        // Step 1: Fast fail for known large sizes
                        val contentLength = body.contentLength()
                        if (contentLength > 5_000_000) {
                            android.util.Log.w(TAG, "Response too large via header: $contentLength bytes")
                            return@let null
                        }

                        // Step 2: BOUNDED READ - Read max 5MB
                        val maxBytes = 5L * 1024 * 1024
                        val source = body.source()
                        val buffer = okio.Buffer()
                        var totalRead = 0L

                        try {
                            while (totalRead < maxBytes) {
                                val bytesRead = source.read(buffer, maxBytes - totalRead)
                                if (bytesRead == -1L) break
                                totalRead += bytesRead
                            }
                        } catch (e: Exception) {
                            android.util.Log.w(TAG, "Error reading response stream", e)
                            return@let null
                        }

                        if (totalRead >= maxBytes && !source.exhausted()) {
                            android.util.Log.w(TAG, "Response exceeded 5MB limit")
                            return@let null
                        }

                        buffer.readUtf8()
                    } ?: ""

                    // If final URL is different from redirect URL, it worked
                    if (finalUrl != redirectUrl && finalUrl.contains(".mp4", ignoreCase = true)) {
                        return@use finalUrl
                    }

                    // Otherwise, try to extract URL from response body (JavaScript or HTML)
                    if (responseBody.contains(".mp4", ignoreCase = true)) {
                        android.util.Log.d(TAG, "Searching for MP4 URL in POST response body...")
                        android.util.Log.d(TAG, "Response body (first 500 chars): ${responseBody.take(500)}")

                        // Look for MP4 URLs in various formats
                        val patterns = listOf(
                            Regex("""https?://[^\s"'<>()]+\.mp4[^\s"'<>]*""", RegexOption.IGNORE_CASE), // Standard URL
                            Regex("""['"]([^'"]*\.mp4[^'"]*)['"]""", RegexOption.IGNORE_CASE), // Quoted string
                            Regex("""location\.href\s*=\s*['"]([^'"]+)['"]""", RegexOption.IGNORE_CASE), // JavaScript redirect
                            Regex("""window\.location\s*=\s*['"]([^'"]+)['"]""", RegexOption.IGNORE_CASE), // Window location
                            Regex("""src\s*[:=]\s*['"]([^'"]*\.mp4[^'"]*)['"]""", RegexOption.IGNORE_CASE) // src attribute
                        )

                        for (pattern in patterns) {
                            val match = pattern.find(responseBody)
                            if (match != null) {
                                val url = if (match.groupValues.size > 1) match.groupValues[1] else match.value
                                if (url.contains(".mp4", ignoreCase = true)) {
                                    android.util.Log.d(TAG, "Found MP4 URL via pattern: $url")
                                    return@use url.trim().removeSurrounding("\"", "\"").removeSurrounding("'", "'")
                                }
                            }
                        }
                    }

                    finalUrl
                } else {
                    android.util.Log.w(TAG, "POST redirect failed: HTTP ${response.code}")
                    ""
                }
            }

            if (result.isNotEmpty()) {
                return@withContext result
            }

        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error following redirect", e)
        }

        return@withContext ""
    }

    /**
     * Extract any direct MP4 URLs from HTML (links, scripts, etc.)
     */
    private suspend fun extractDirectMp4Links(doc: Document): List<VideoUrl> {
        try {
            android.util.Log.d(TAG, "Searching for direct MP4 links in HTML...")
            val videoUrls = mutableListOf<VideoUrl>()

            // Search in all <a> tags
            val links = doc.select("a[href*=.mp4]")
            for (link in links) {
                val href = link.attr("href")
                if (href.contains(".mp4", ignoreCase = true) &&
                    (href.startsWith("http://") || href.startsWith("https://"))) {

                    val quality = VideoUrl.extractQuality(href)
                    val mirror = VideoUrl.extractMirror(href)

                    videoUrls.add(VideoUrl(url = href, quality = quality, mirror = mirror))
                    android.util.Log.d(TAG, "Found MP4 link: $href")
                }
            }

            // Search in all <source> tags
            val sources = doc.select("source[src*=.mp4]")
            for (source in sources) {
                val src = source.attr("src")
                if (src.contains(".mp4", ignoreCase = true) &&
                    (src.startsWith("http://") || src.startsWith("https://"))) {

                    val quality = VideoUrl.extractQuality(src)
                    val mirror = VideoUrl.extractMirror(src)

                    videoUrls.add(VideoUrl(url = src, quality = quality, mirror = mirror))
                    android.util.Log.d(TAG, "Found MP4 source: $src")
                }
            }

            // Search in scripts for MP4 URLs
            val scripts = doc.select("script")
            for (script in scripts) {
                val scriptContent = script.html()

                // SECURITY: Protect against ReDoS with size limit and timeout
                if (scriptContent.length > 10_000_000) { // 10MB max
                    android.util.Log.w(TAG, "Script too large for regex parsing: ${scriptContent.length} bytes")
                    continue
                }

                // Look for URLs in JavaScript
                // SECURITY: Use timeout-protected regex execution
                val mp4Regex = Regex("""https?://[^\s"']+\.mp4""", RegexOption.IGNORE_CASE)
                val matches = SecureRegex.findAllWithTimeout(mp4Regex, scriptContent)

                for (match in matches) {
                    val url = match.value
                    val quality = VideoUrl.extractQuality(url)
                    val mirror = VideoUrl.extractMirror(url)

                    videoUrls.add(VideoUrl(url = url, quality = quality, mirror = mirror))
                    android.util.Log.d(TAG, "Found MP4 in script: $url")
                }
            }

            if (videoUrls.isNotEmpty()) {
                android.util.Log.d(TAG, "Found ${videoUrls.size} direct MP4 links")
                return videoUrls.sortedWith(
                    compareByDescending<VideoUrl> {
                        when (it.quality) {
                            "1080p" -> 3
                            "720p" -> 2
                            "480p" -> 1
                            else -> 0
                        }
                    }
                )
            }

        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error extracting direct MP4 links", e)
        }

        return emptyList()
    }

    /**
     * Fallback: Generate URLs using CDN pattern
     * Pattern: https://{mirror}/series/{slug}/{episode}.{quality}.mp4
     *
     * Example URL: https://farsiland.com/series/shoghal/s01e01/
     * Generates:
     * - https://d1.flnd.buzz/series/shoghal/01.1080.mp4
     * - https://d1.flnd.buzz/series/shoghal/01.720.mp4
     * - https://d1.flnd.buzz/series/shoghal/01.480.mp4
     */
    private fun tryGenerateUrls(pageUrl: String): List<VideoUrl> {
        try {
            // Extract series slug and episode number from URL
            // Pattern: https://farsiland.com/series/{slug}/s{season}e{episode}/
            val regex = Regex("""/series/([^/]+)/s(\d+)e(\d+)""", RegexOption.IGNORE_CASE)
            val match = regex.find(pageUrl)

            if (match != null) {
                val slug = match.groupValues[1]
                val season = match.groupValues[2].toIntOrNull() ?: 1
                val episode = match.groupValues[3].toIntOrNull() ?: 1

                return generateVideoUrls(slug, season, episode)
            }

            // Try movie pattern: https://farsiland.com/movies/{slug}/
            val movieRegex = Regex("""/movies/([^/]+)""", RegexOption.IGNORE_CASE)
            val movieMatch = movieRegex.find(pageUrl)

            if (movieMatch != null) {
                val slug = movieMatch.groupValues[1]
                return generateMovieUrls(slug)
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }

        return emptyList()
    }

    /**
     * Generate video URLs for a series episode
     * Uses both mirrors (d1.flnd.buzz, d2.flnd.buzz)
     */
    fun generateVideoUrls(seriesSlug: String, season: Int, episode: Int): List<VideoUrl> {
        val episodeNum = String.format("%02d", episode)
        val mirrors = listOf("d1.flnd.buzz", "d2.flnd.buzz")
        val qualities = listOf("1080", "720", "480")

        val videoUrls = mutableListOf<VideoUrl>()

        for (mirror in mirrors) {
            for (quality in qualities) {
                val url = "https://$mirror/series/$seriesSlug/$episodeNum.$quality.mp4"
                videoUrls.add(
                    VideoUrl(
                        url = url,
                        quality = "${quality}p",
                        mirror = mirror
                    )
                )
            }
        }

        // Sort by quality (1080p first), then by mirror (d1 first)
        return videoUrls.sortedWith(
            compareByDescending<VideoUrl> {
                when (it.quality) {
                    "1080p" -> 3
                    "720p" -> 2
                    "480p" -> 1
                    else -> 0
                }
            }.thenByDescending { it.mirror == "d1.flnd.buzz" }
        )
    }

    /**
     * Generate video URLs for a movie
     * Pattern: https://{mirror}/movies/{slug}/{quality}.mp4
     */
    private fun generateMovieUrls(movieSlug: String): List<VideoUrl> {
        val mirrors = listOf("d1.flnd.buzz", "d2.flnd.buzz")
        val qualities = listOf("1080", "720", "480")

        val videoUrls = mutableListOf<VideoUrl>()

        for (mirror in mirrors) {
            for (quality in qualities) {
                val url = "https://$mirror/movies/$movieSlug/$quality.mp4"
                videoUrls.add(
                    VideoUrl(
                        url = url,
                        quality = "${quality}p",
                        mirror = mirror
                    )
                )
            }
        }

        return videoUrls.sortedWith(
            compareByDescending<VideoUrl> {
                when (it.quality) {
                    "1080p" -> 3
                    "720p" -> 2
                    "480p" -> 1
                    else -> 0
                }
            }.thenByDescending { it.mirror == "d1.flnd.buzz" }
        )
    }

    /**
     * Fetch HTML content from URL
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

            response.body?.string() ?: throw Exception("Empty response body")
        }
    }

    /**
     * Verify if a video URL is accessible
     * Sends HEAD request to check if URL returns 200 OK
     */
    suspend fun verifyVideoUrl(url: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(url)
                .head() // HEAD request to avoid downloading entire file
                .build()

            httpClient.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Get working video URL from a list
     * Tests each URL and returns the first working one
     */
    suspend fun getWorkingUrl(videoUrls: List<VideoUrl>): VideoUrl? {
        for (videoUrl in videoUrls) {
            if (verifyVideoUrl(videoUrl.url)) {
                return videoUrl
            }
        }
        return null
    }

    /**
     * Clear the video URL cache
     * Useful for forcing fresh scraping or clearing memory
     */
    fun clearCache() {
        val cacheSize = urlCache.size
        urlCache.clear()
        android.util.Log.d(TAG, "Cache cleared. Removed $cacheSize cached entries")
    }

    /**
     * Clear cache entry for a specific page URL
     * Useful for forcing re-scraping of a specific episode/movie
     */
    fun clearCacheForUrl(pageUrl: String) {
        val removed = urlCache.remove(pageUrl)
        if (removed != null) {
            android.util.Log.d(TAG, "Cleared cache for: $pageUrl")
        } else {
            android.util.Log.d(TAG, "No cache entry found for: $pageUrl")
        }
    }

    /**
     * Get cache statistics for debugging
     */
    fun getCacheStats(): String {
        val size = urlCache.size
        val totalUrls = urlCache.values.sumOf { it.urls.size }
        val avgAge = if (size > 0) {
            val now = System.currentTimeMillis()
            urlCache.values.map { (now - it.timestamp) / 1000 }.average()
        } else 0.0

        return "Cache: $size pages, $totalUrls URLs, avg age: ${avgAge.toInt()}s"
    }
}
