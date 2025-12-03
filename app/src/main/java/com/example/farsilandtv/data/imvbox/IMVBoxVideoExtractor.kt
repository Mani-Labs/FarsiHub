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
import kotlin.coroutines.resume

/**
 * Extracts video URLs from IMVBox play pages.
 *
 * IMVBox uses two video sources:
 * 1. YouTube embeds - Returns YouTube video ID for playback via YouTube player
 * 2. Self-hosted HLS - Returns direct HLS URL for ExoPlayer
 *
 * Uses a hidden WebView to load the page and intercept network requests,
 * avoiding bot detection that blocks headless browsers.
 *
 * IMPORTANT: For full movie access (not just trailers), the user must be
 * logged in via IMVBoxAuthManager. This class will inject session cookies
 * into the WebView before loading the play page.
 */
class IMVBoxVideoExtractor(private val context: Context) {

    companion object {
        private const val TAG = "IMVBoxVideoExtractor"
        private const val TIMEOUT_MS = 30_000L
        private const val IMVBOX_DOMAIN = "www.imvbox.com"

        // Known intro/ad media IDs that play before all movies
        // These are NOT the actual movie content - must be filtered out
        private val INTRO_MEDIA_IDS = setOf("3628")

        // Regex patterns
        private val YOUTUBE_EMBED_REGEX = Regex("""youtube\.com/embed/([a-zA-Z0-9_-]{11})""")
        private val HLS_URL_REGEX = Regex("""streaming\.imvbox\.com/media/(\d+)/\d+\.m3u8""")
        // More flexible HLS patterns - catch any m3u8 from IMVBox domains
        private val HLS_GENERIC_REGEX = Regex("""(https?://[^"'\s]+\.m3u8[^"'\s]*)""")
        private val IMVBOX_STREAM_REGEX = Regex("""imvbox\.com[^"'\s]*\.m3u8""")

        /**
         * Check if a media ID is a known intro/ad video
         */
        private fun isIntroMediaId(mediaId: String): Boolean {
            return mediaId in INTRO_MEDIA_IDS
        }
    }

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
     * Extract video source from IMVBox play page URL
     *
     * @param playUrl Full IMVBox play page URL (e.g., https://www.imvbox.com/en/movies/33-days-33-rooz/play)
     * @return VideoSource with either YouTube ID, HLS URL, or error
     */
    @SuppressLint("SetJavaScriptEnabled")
    suspend fun extractVideoSource(playUrl: String): VideoSource {
        // Ensure user is authenticated for full content access
        val isAuthenticated = withContext(Dispatchers.IO) {
            IMVBoxAuthManager.ensureAuthenticated(context)
        }
        Log.d(TAG, "Authentication status: $isAuthenticated")

        // WebView MUST be created on the Main thread
        return withContext(Dispatchers.Main) {
            // Inject auth cookies before creating WebView
            injectAuthCookies()
            syncCookiesToWebView()

            withTimeout(TIMEOUT_MS) {
                suspendCancellableCoroutine { continuation ->
                var webView: WebView? = null
                var resumed = false

                try {
                    webView = WebView(context).apply {
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            mediaPlaybackRequiresUserGesture = false
                            // Mimic real browser
                            userAgentString = "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
                        }

                        // Enable third-party cookies for this WebView
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                        webViewClient = object : WebViewClient() {
                            override fun shouldInterceptRequest(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): WebResourceResponse? {
                                val url = request?.url?.toString() ?: return null

                                // Log interesting URLs for debugging
                                if (url.contains("m3u8") || url.contains("youtube") ||
                                    url.contains("video") || url.contains("stream") ||
                                    url.contains("player") || url.contains("embed")) {
                                    Log.d(TAG, "Intercepted interesting URL: $url")
                                }

                                // Check for YouTube embed
                                YOUTUBE_EMBED_REGEX.find(url)?.let { match ->
                                    val videoId = match.groupValues[1]
                                    Log.d(TAG, "Found YouTube video: $videoId")
                                    if (!resumed) {
                                        resumed = true
                                        webView?.stopLoading()
                                        continuation.resume(VideoSource.YouTube(videoId))
                                    }
                                }

                                // Check for HLS stream - specific pattern
                                HLS_URL_REGEX.find(url)?.let { match ->
                                    val mediaId = match.groupValues[1]

                                    // Skip known intro/ad videos
                                    if (isIntroMediaId(mediaId)) {
                                        Log.d(TAG, "Skipping intro HLS stream (media ID $mediaId)")
                                        return@let // Continue looking for actual movie
                                    }

                                    val hlsUrl = "https://streaming.imvbox.com/media/$mediaId/$mediaId.m3u8"
                                    Log.d(TAG, "Found HLS stream (specific): $hlsUrl")
                                    if (!resumed) {
                                        resumed = true
                                        webView?.stopLoading()
                                        continuation.resume(VideoSource.HLS(hlsUrl, mediaId))
                                    }
                                }

                                // Check for any m3u8 URL (fallback)
                                if (url.contains(".m3u8") && !resumed) {
                                    Log.d(TAG, "Found generic HLS: $url")
                                    // Extract media ID if possible
                                    val mediaIdMatch = Regex("""/media/(\d+)/""").find(url)
                                    val mediaId = mediaIdMatch?.groupValues?.get(1) ?: "unknown"

                                    // Skip known intro/ad videos
                                    if (isIntroMediaId(mediaId)) {
                                        Log.d(TAG, "Skipping generic intro HLS (media ID $mediaId)")
                                        // Don't return - continue looking for actual movie
                                    } else {
                                        resumed = true
                                        webView?.stopLoading()
                                        continuation.resume(VideoSource.HLS(url, mediaId))
                                    }
                                }

                                return null
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                Log.d(TAG, "Page finished loading: $url")

                                // If page finished loading but no video found, wait a bit more
                                // Video might load dynamically
                                view?.postDelayed({
                                    if (!resumed) {
                                        // Check page content for video sources (especially Video.js configuration)
                                        view.evaluateJavascript("""
                                            (function() {
                                                // Method 1: Check Video.js player instance
                                                if (typeof videojs !== 'undefined') {
                                                    var players = document.querySelectorAll('.video-js');
                                                    for (var i = 0; i < players.length; i++) {
                                                        var player = videojs.getPlayer(players[i]);
                                                        if (player && player.currentSrc()) {
                                                            var src = player.currentSrc();
                                                            if (src.indexOf('.m3u8') !== -1) {
                                                                return JSON.stringify({type: 'hls', url: src});
                                                            }
                                                            if (src.indexOf('youtube.com') !== -1) {
                                                                var ytMatch = src.match(/youtube\.com\/embed\/([a-zA-Z0-9_-]{11})/);
                                                                if (ytMatch) return JSON.stringify({type: 'youtube', id: ytMatch[1]});
                                                            }
                                                        }
                                                    }
                                                }

                                                // Method 2: Check data-setup attribute on video-js elements
                                                var videoJsEl = document.querySelector('video-js[data-setup], #player[data-setup], .video-js[data-setup]');
                                                if (videoJsEl) {
                                                    var setup = videoJsEl.getAttribute('data-setup');
                                                    if (setup) {
                                                        // Look for HLS URL
                                                        var hlsMatch = setup.match(/(https?:\/\/[^"'\s]+\.m3u8[^"'\s]*)/);
                                                        if (hlsMatch) return JSON.stringify({type: 'hls', url: hlsMatch[1]});

                                                        // Look for YouTube
                                                        var ytMatch = setup.match(/youtube\.com\/embed\/([a-zA-Z0-9_-]{11})/);
                                                        if (ytMatch) return JSON.stringify({type: 'youtube', id: ytMatch[1]});
                                                    }
                                                }

                                                // Method 3: Check for YouTube iframe
                                                var iframe = document.querySelector('iframe[src*="youtube.com/embed"]');
                                                if (iframe) {
                                                    var match = iframe.src.match(/youtube\.com\/embed\/([a-zA-Z0-9_-]{11})/);
                                                    if (match) return JSON.stringify({type: 'youtube', id: match[1]});
                                                }

                                                // Method 4: Check for video element with src
                                                var video = document.querySelector('video source[src*=".m3u8"]');
                                                if (video) {
                                                    return JSON.stringify({type: 'hls', url: video.src});
                                                }
                                                var videoEl = document.querySelector('video[src*=".m3u8"]');
                                                if (videoEl) {
                                                    return JSON.stringify({type: 'hls', url: videoEl.src});
                                                }

                                                // Method 5: Check for any iframe with video player
                                                var anyIframe = document.querySelector('iframe[src*="player"], iframe[src*="embed"]');
                                                if (anyIframe) {
                                                    var ytMatch = anyIframe.src.match(/youtube\.com\/embed\/([a-zA-Z0-9_-]{11})/);
                                                    if (ytMatch) return JSON.stringify({type: 'youtube', id: ytMatch[1]});
                                                    return JSON.stringify({type: 'iframe', url: anyIframe.src});
                                                }

                                                // Method 6: Check page source for video URLs
                                                var pageHtml = document.documentElement.innerHTML;

                                                // Check for streaming.imvbox.com HLS
                                                var imvboxHls = pageHtml.match(/streaming\.imvbox\.com\/media\/(\d+)\/\d+\.m3u8/);
                                                if (imvboxHls) {
                                                    var mediaId = imvboxHls[1];
                                                    return JSON.stringify({type: 'hls', url: 'https://streaming.imvbox.com/media/' + mediaId + '/' + mediaId + '.m3u8'});
                                                }

                                                // Check for any HLS URL
                                                var hlsMatch = pageHtml.match(/(https?:\/\/[^\s"'<>]+\.m3u8)/);
                                                if (hlsMatch) {
                                                    return JSON.stringify({type: 'hls', url: hlsMatch[1]});
                                                }

                                                // Check for YouTube in page source
                                                var ytMatch = pageHtml.match(/youtube\.com\/embed\/([a-zA-Z0-9_-]{11})/);
                                                if (ytMatch) {
                                                    return JSON.stringify({type: 'youtube', id: ytMatch[1]});
                                                }

                                                return JSON.stringify({type: 'none', debug: 'No video source found in page'});
                                            })()
                                        """.trimIndent()) { result ->
                                            Log.d(TAG, "JS evaluation result: $result")
                                            if (result != "null" && result.isNotEmpty() && !resumed) {
                                                try {
                                                    val cleanResult = result.replace("\"", "").replace("\\", "")
                                                    if (cleanResult.contains("youtube") && cleanResult.contains("id")) {
                                                        val idMatch = Regex("""id[:\s]*([a-zA-Z0-9_-]{11})""").find(cleanResult)
                                                        idMatch?.let {
                                                            val videoId = it.groupValues[1]
                                                            Log.d(TAG, "Found YouTube via JS: $videoId")
                                                            resumed = true
                                                            continuation.resume(VideoSource.YouTube(videoId))
                                                        }
                                                    } else if (cleanResult.contains("hls") && cleanResult.contains("url")) {
                                                        val urlMatch = Regex("""url[:\s]*(https?://[^\s,}]+\.m3u8[^\s,}]*)""").find(cleanResult)
                                                        urlMatch?.let {
                                                            val hlsUrl = it.groupValues[1]
                                                            val mediaIdMatch = Regex("""/media/(\d+)/""").find(hlsUrl)
                                                            val mediaId = mediaIdMatch?.groupValues?.get(1) ?: "unknown"
                                                            Log.d(TAG, "Found HLS via JS: $hlsUrl")
                                                            resumed = true
                                                            continuation.resume(VideoSource.HLS(hlsUrl, mediaId))
                                                        }
                                                    }
                                                } catch (e: Exception) {
                                                    Log.e(TAG, "Error parsing JS result: ${e.message}")
                                                }
                                            }
                                        }
                                    }
                                }, 5000)  // 5 seconds - give Video.js time to initialize
                            }
                        }
                    }

                    Log.d(TAG, "Loading: $playUrl")
                    webView.loadUrl(playUrl)

                    // Timeout fallback
                    webView.postDelayed({
                        if (!resumed) {
                            Log.w(TAG, "Timeout - no video source found")
                            resumed = true
                            continuation.resume(VideoSource.Error("Timeout - no video source found"))
                        }
                    }, TIMEOUT_MS - 1000)

                } catch (e: Exception) {
                    Log.e(TAG, "Error extracting video: ${e.message}", e)
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
