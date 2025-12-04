package com.example.farsilandtv.data.imvbox

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.example.farsilandtv.data.api.IMVBoxAuthManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import kotlin.coroutines.resume

/**
 * Extracts video URLs from IMVBox play pages.
 *
 * IMVBox page structure:
 * - #intro-player: Intro video (media 3628) - plays first, SKIP THIS
 * - #player: ACTUAL MOVIE - YouTube URL is in data-setup JSON attribute
 * - #player-trailer: Trailer (if exists)
 *
 * CLEAN APPROACH: Parse HTML directly to extract YouTube URL from
 * #player element's data-setup attribute. No WebView race conditions!
 *
 * Example HTML:
 * <video-js id="player" data-setup='{"sources": [{"type": "video/youtube",
 *   "src": "https://www.youtube.com/embed/f9hrxIZS7Ck"}]}'>
 *
 * IMPORTANT: For full movie access (not just trailers), the user must be
 * logged in via IMVBoxAuthManager.
 */
class IMVBoxVideoExtractor(private val context: Context) {

    companion object {
        private const val TAG = "IMVBoxVideoExtractor"
        private const val TIMEOUT_MS = 30_000L
        private const val IMVBOX_DOMAIN = "www.imvbox.com"

        // Known intro/ad media IDs that play before all movies
        // These are NOT the actual movie content - must be filtered out
        private val INTRO_MEDIA_IDS = setOf("3628")

        // Regex patterns for HTML parsing
        private val YOUTUBE_EMBED_REGEX = Regex("""youtube\.com/embed/([a-zA-Z0-9_-]{11})""")
        // Match #player element's data-setup attribute (THE ACTUAL MOVIE)
        private val PLAYER_DATA_SETUP_REGEX = Regex(
            """<video-js[^>]*id=["']player["'][^>]*data-setup=['"]([^'"]+)['"]""",
            RegexOption.IGNORE_CASE
        )
        // Alternative: match any video-js with data-setup containing youtube
        private val VIDEOJS_DATA_SETUP_REGEX = Regex(
            """<video-js[^>]*data-setup=['"]([^'"]*youtube[^'"]+)['"]""",
            RegexOption.IGNORE_CASE
        )
        // HLS patterns
        private val HLS_URL_REGEX = Regex("""streaming\.imvbox\.com/media/(\d+)/\d+\.m3u8""")

        /**
         * Check if a media ID is a known intro/ad video
         */
        private fun isIntroMediaId(mediaId: String): Boolean {
            return mediaId in INTRO_MEDIA_IDS
        }
    }

    // OkHttp client for direct HTML fetching
    private val httpClient = OkHttpClient.Builder()
        .followRedirects(true)
        .build()

    /**
     * Result of video extraction
     */
    sealed class VideoSource {
        data class YouTube(val videoId: String) : VideoSource()
        data class HLS(val url: String, val mediaId: String) : VideoSource()
        data class Error(val message: String) : VideoSource()
    }

    /**
     * Inject authentication cookies from IMVBoxAuthManager into WebView's CookieManager.
     * This allows the WebView to access full movie content instead of just trailers.
     *
     * @return true if cookies were injected (user is logged in), false otherwise
     */
    private fun injectAuthCookies(): Boolean {
        if (!IMVBoxAuthManager.isLoggedIn(context)) {
            Log.d(TAG, "User not logged in - will only get trailer content")
            return false
        }

        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)

        try {
            // Get cookies from OkHttp's cookie jar
            val cookies = IMVBoxAuthManager.getWebViewCookies()

            if (cookies.isEmpty()) {
                Log.w(TAG, "No cookies to inject - user may need to re-login")
                return false
            }

            // Clear existing IMVBox cookies first to avoid conflicts
            cookieManager.setCookie("https://$IMVBOX_DOMAIN", "")

            // Inject each cookie into WebView's CookieManager
            val url = "https://$IMVBOX_DOMAIN"
            cookies.forEach { cookieString ->
                cookieManager.setCookie(url, cookieString)
                Log.d(TAG, "Injected cookie to WebView: ${cookieString.take(50)}...")
            }

            // Flush to persist
            cookieManager.flush()

            // Verify cookies were set
            val verifySet = cookieManager.getCookie(url)
            Log.d(TAG, "WebView cookies after injection: ${verifySet?.take(100) ?: "none"}")

            return verifySet?.contains("imvbox_session") == true ||
                   verifySet?.contains("XSRF-TOKEN") == true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to inject auth cookies", e)
            return false
        }
    }

    /**
     * Sync cookies from OkHttp to WebView CookieManager.
     * Must be called on Main thread before WebView loads.
     */
    private fun syncCookiesToWebView() {
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.flush()
        Log.d(TAG, "Cookie sync complete")
    }

    /**
     * CLEAN APPROACH: Extract video source by parsing HTML directly.
     *
     * IMVBox page has this structure:
     * - #intro-player: Intro video (media 3628) - SKIP
     * - #player: THE MOVIE - YouTube URL in data-setup JSON
     * - #player-trailer: Trailer (if exists)
     *
     * We parse the HTML and extract YouTube URL from #player's data-setup attribute.
     * This bypasses the intro entirely - no race conditions!
     *
     * @param playUrl Full IMVBox play page URL
     * @return VideoSource with YouTube ID, HLS URL, or error
     */
    suspend fun extractVideoSource(playUrl: String): VideoSource {
        Log.d(TAG, "=== CLEAN EXTRACTION: Parsing HTML directly ===")
        Log.d(TAG, "Play URL: $playUrl")

        // Ensure user is authenticated for full content access
        val isAuthenticated = withContext(Dispatchers.IO) {
            IMVBoxAuthManager.ensureAuthenticated(context)
        }
        Log.d(TAG, "Authentication status: $isAuthenticated")

        // Try clean HTML parsing first
        val htmlResult = withContext(Dispatchers.IO) {
            extractFromHtml(playUrl)
        }

        if (htmlResult != null) {
            Log.d(TAG, "=== SUCCESS: Got video from HTML parsing ===")
            return htmlResult
        }

        // Fallback to WebView if HTML parsing failed
        Log.w(TAG, "HTML parsing failed, falling back to WebView extraction")
        return extractViaWebView(playUrl)
    }

    /**
     * Parse HTML directly to extract YouTube video ID from #player element.
     * This is the CLEAN approach - no WebView race conditions.
     */
    private suspend fun extractFromHtml(playUrl: String): VideoSource? {
        return try {
            // Get auth cookies for the request
            val cookies = IMVBoxAuthManager.getWebViewCookies()
            val cookieHeader = cookies.joinToString("; ")

            val request = Request.Builder()
                .url(playUrl)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36")
                .header("Cookie", cookieHeader)
                .header("Accept", "text/html,application/xhtml+xml")
                .build()

            val response = httpClient.newCall(request).execute()

            if (!response.isSuccessful) {
                Log.e(TAG, "HTTP request failed: ${response.code}")
                return null
            }

            val html = response.body?.string() ?: return null
            Log.d(TAG, "Fetched HTML: ${html.length} chars")

            // Method 1: Find #player element's data-setup attribute (THE ACTUAL MOVIE)
            // This is where IMVBox puts the movie YouTube URL
            val playerDataSetup = PLAYER_DATA_SETUP_REGEX.find(html)
            if (playerDataSetup != null) {
                val dataSetup = playerDataSetup.groupValues[1]
                    .replace("&quot;", "\"")
                    .replace("&#39;", "'")
                Log.d(TAG, "Found #player data-setup: ${dataSetup.take(200)}...")

                // Parse JSON to get YouTube URL
                val youtubeId = extractYouTubeFromDataSetup(dataSetup)
                if (youtubeId != null) {
                    Log.d(TAG, "=== EXTRACTED MOVIE YouTube ID: $youtubeId ===")
                    return VideoSource.YouTube(youtubeId)
                }

                // Check for HLS in data-setup (non-intro)
                val hlsUrl = extractHlsFromDataSetup(dataSetup)
                if (hlsUrl != null) {
                    val mediaId = HLS_URL_REGEX.find(hlsUrl)?.groupValues?.get(1) ?: "unknown"
                    if (!isIntroMediaId(mediaId)) {
                        Log.d(TAG, "=== EXTRACTED MOVIE HLS: $hlsUrl ===")
                        return VideoSource.HLS(hlsUrl, mediaId)
                    }
                }
            }

            // Method 2: Look for any video-js with YouTube in data-setup
            // (but NOT #intro-player which has media 3628)
            val videoJsMatches = VIDEOJS_DATA_SETUP_REGEX.findAll(html)
            for (match in videoJsMatches) {
                val dataSetup = match.groupValues[1]
                    .replace("&quot;", "\"")
                    .replace("&#39;", "'")

                // Skip if this is the intro player (contains media 3628)
                if (dataSetup.contains("3628")) {
                    Log.d(TAG, "Skipping intro player data-setup")
                    continue
                }

                val youtubeId = extractYouTubeFromDataSetup(dataSetup)
                if (youtubeId != null) {
                    Log.d(TAG, "=== EXTRACTED YouTube from video-js: $youtubeId ===")
                    return VideoSource.YouTube(youtubeId)
                }
            }

            // Method 3: Look for YouTube embed URL in page (not in intro context)
            // Skip any that appear in intro-player section
            val introSection = Regex("""<div[^>]*intro-player[^>]*>.*?</div>""", RegexOption.DOT_MATCHES_ALL)
                .find(html)?.value ?: ""

            YOUTUBE_EMBED_REGEX.findAll(html).forEach { match ->
                val videoId = match.groupValues[1]
                // Check if this YouTube ID appears in the intro section
                if (!introSection.contains(videoId)) {
                    Log.d(TAG, "=== EXTRACTED YouTube from embed: $videoId ===")
                    return VideoSource.YouTube(videoId)
                } else {
                    Log.d(TAG, "Skipping YouTube $videoId (appears in intro section)")
                }
            }

            Log.w(TAG, "No movie source found in HTML")
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing HTML: ${e.message}", e)
            null
        }
    }

    /**
     * Extract YouTube video ID from Video.js data-setup JSON string.
     */
    private fun extractYouTubeFromDataSetup(dataSetup: String): String? {
        return try {
            // Try JSON parsing first
            val json = JSONObject(dataSetup)
            val sources = json.optJSONArray("sources")
            if (sources != null) {
                for (i in 0 until sources.length()) {
                    val source = sources.getJSONObject(i)
                    val src = source.optString("src", "")
                    val type = source.optString("type", "")

                    if (type.contains("youtube") || src.contains("youtube.com")) {
                        YOUTUBE_EMBED_REGEX.find(src)?.let { match ->
                            return match.groupValues[1]
                        }
                    }
                }
            }
            null
        } catch (e: Exception) {
            // Fallback: regex extraction if JSON parsing fails
            YOUTUBE_EMBED_REGEX.find(dataSetup)?.groupValues?.get(1)
        }
    }

    /**
     * Extract HLS URL from Video.js data-setup JSON string.
     */
    private fun extractHlsFromDataSetup(dataSetup: String): String? {
        return try {
            val json = JSONObject(dataSetup)
            val sources = json.optJSONArray("sources")
            if (sources != null) {
                for (i in 0 until sources.length()) {
                    val source = sources.getJSONObject(i)
                    val src = source.optString("src", "")
                    if (src.contains(".m3u8")) {
                        return src
                    }
                }
            }
            null
        } catch (e: Exception) {
            // Fallback: regex extraction
            Regex("""(https?://[^\s"']+\.m3u8)""").find(dataSetup)?.groupValues?.get(1)
        }
    }

    /**
     * Fallback: Extract via WebView if HTML parsing fails.
     * This is the old approach - only used if clean extraction fails.
     */
    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun extractViaWebView(playUrl: String): VideoSource {
        Log.d(TAG, "Using WebView fallback extraction")

        return withContext(Dispatchers.Main) {
            injectAuthCookies()
            syncCookiesToWebView()

            withTimeout(TIMEOUT_MS) {
                suspendCancellableCoroutine { continuation ->
                var webView: WebView? = null
                var resumed = false
                var foundYouTubeId: String? = null

                try {
                    webView = WebView(context).apply {
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            mediaPlaybackRequiresUserGesture = false
                            userAgentString = "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
                        }

                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                        webViewClient = object : WebViewClient() {
                            override fun shouldInterceptRequest(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): WebResourceResponse? {
                                val url = request?.url?.toString() ?: return null

                                // Look for YouTube embeds (skip intro media 3628)
                                if (url.contains("youtube.com/embed") && !resumed) {
                                    YOUTUBE_EMBED_REGEX.find(url)?.let { match ->
                                        val videoId = match.groupValues[1]
                                        Log.d(TAG, "WebView found YouTube: $videoId")
                                        foundYouTubeId = videoId
                                        // Don't return immediately - wait for page to load
                                    }
                                }

                                // Skip intro HLS (media 3628)
                                if (url.contains("m3u8")) {
                                    HLS_URL_REGEX.find(url)?.let { match ->
                                        val mediaId = match.groupValues[1]
                                        if (!isIntroMediaId(mediaId) && !resumed) {
                                            Log.d(TAG, "WebView found HLS: $url")
                                            resumed = true
                                            continuation.resume(VideoSource.HLS(url, mediaId))
                                        }
                                    }
                                }

                                return null
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                Log.d(TAG, "WebView page finished: $url")

                                // Extract data-setup from #player via JavaScript
                                view?.evaluateJavascript("""
                                    (function() {
                                        var player = document.querySelector('#player[data-setup], video-js#player');
                                        if (player) {
                                            var setup = player.getAttribute('data-setup');
                                            if (setup) {
                                                var ytMatch = setup.match(/youtube\.com\/embed\/([a-zA-Z0-9_-]{11})/);
                                                if (ytMatch) return ytMatch[1];
                                            }
                                        }
                                        return null;
                                    })()
                                """.trimIndent()) { result ->
                                    if (result != "null" && result.isNotEmpty() && !resumed) {
                                        val videoId = result.replace("\"", "")
                                        if (videoId.length == 11) {
                                            Log.d(TAG, "JS extracted YouTube: $videoId")
                                            resumed = true
                                            continuation.resume(VideoSource.YouTube(videoId))
                                        }
                                    }
                                }
                            }
                        }
                    }

                    webView.loadUrl(playUrl)

                    // Timeout fallback
                    webView.postDelayed({
                        if (!resumed) {
                            if (foundYouTubeId != null) {
                                Log.d(TAG, "Timeout - using found YouTube: $foundYouTubeId")
                                resumed = true
                                continuation.resume(VideoSource.YouTube(foundYouTubeId!!))
                            } else {
                                Log.e(TAG, "Timeout - no video source found")
                                resumed = true
                                continuation.resume(VideoSource.Error("Timeout: No video source found"))
                            }
                        }
                    }, TIMEOUT_MS - 1000)

                } catch (e: Exception) {
                    Log.e(TAG, "WebView error: ${e.message}", e)
                    if (!resumed) {
                        resumed = true
                        continuation.resume(VideoSource.Error(e.message ?: "Unknown error"))
                    }
                }

                continuation.invokeOnCancellation {
                    webView?.stopLoading()
                    webView?.destroy()
                }
            }
            }
        }
    }

    /**
     * Build YouTube watch URL from video ID
     */
    fun buildYouTubeUrl(videoId: String): String {
        return "https://www.youtube.com/watch?v=$videoId"
    }

    /**
     * Check if URL is an IMVBox play page
     */
    fun isPlayUrl(url: String): Boolean {
        return url.contains("imvbox.com") && url.contains("/play")
    }
}
