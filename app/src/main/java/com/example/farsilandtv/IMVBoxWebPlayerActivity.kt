package com.example.farsilandtv

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.SslErrorHandler
import android.webkit.WebViewClient
import android.net.http.SslError
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.mediarouter.app.MediaRouteButton
import com.example.farsilandtv.data.api.IMVBoxAuthManager
import com.google.android.gms.cast.framework.CastButtonFactory
import com.google.android.gms.cast.framework.CastContext
import dagger.hilt.android.AndroidEntryPoint

/**
 * Activity for playing IMVBox videos using their native web player.
 *
 * This loads the IMVBox play page directly in a WebView, allowing YouTube
 * embeds to work (since origin=imvbox.com is trusted by YouTube).
 *
 * Features:
 * - Full D-pad/remote control support with visible overlay
 * - Auto-skip intro video when button appears
 * - JavaScript bridge to control Video.js/YouTube player
 * - Fullscreen video support
 */
@AndroidEntryPoint
class IMVBoxWebPlayerActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "IMVBoxWebPlayer"
        private const val EXTRA_PLAY_URL = "play_url"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_YOUTUBE_ID = "youtube_id"  // Pre-extracted YouTube ID to skip intro
        private const val OVERLAY_HIDE_DELAY_MS = 5000L
        private const val IMVBOX_DOMAIN = "www.imvbox.com"

        /**
         * Create intent to launch IMVBox web player
         *
         * @param youtubeId If provided, skips intro by injecting YouTube directly
         */
        fun createIntent(
            context: Context,
            playUrl: String,
            title: String? = null,
            youtubeId: String? = null
        ): Intent {
            return Intent(context, IMVBoxWebPlayerActivity::class.java).apply {
                putExtra(EXTRA_PLAY_URL, playUrl)
                putExtra(EXTRA_TITLE, title)
                youtubeId?.let { putExtra(EXTRA_YOUTUBE_ID, it) }
            }
        }

        /**
         * Check if URL is an IMVBox play page
         */
        fun isIMVBoxPlayUrl(url: String): Boolean {
            return url.contains("imvbox.com") && url.contains("/play")
        }
    }

    // UI Components
    private lateinit var rootLayout: FrameLayout
    private lateinit var webView: WebView
    private lateinit var loadingIndicator: ProgressBar
    private lateinit var castButton: MediaRouteButton

    // Control overlay components
    private lateinit var controlOverlay: LinearLayout
    private lateinit var btnRewind: ImageButton
    private lateinit var btnPlayPause: ImageButton
    private lateinit var btnForward: ImageButton
    private lateinit var controlHint: TextView

    // Player state
    private var playUrl: String = ""
    private var videoTitle: String? = null
    private var preExtractedYouTubeId: String? = null  // If set, skip intro entirely
    private var isPlaying = false
    private var introSkipped = false

    // Fullscreen support
    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null

    // Overlay auto-hide
    private val hideHandler = Handler(Looper.getMainLooper())
    private val hideRunnable = Runnable { hideControlOverlay() }
    private var isOverlayVisible = false

    // Periodic check for intro skip button
    private val skipCheckHandler = Handler(Looper.getMainLooper())
    private val skipCheckRunnable = object : Runnable {
        override fun run() {
            // Check lifecycle before proceeding - prevents crashes after Activity destroyed
            if (isFinishing || isDestroyed) {
                return
            }
            if (!introSkipped) {
                tryClickSkipButton()
                skipCheckHandler.postDelayed(this, 1000) // Check every second
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Keep screen on during playback
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Fullscreen immersive mode
        hideSystemUI()

        // Extract intent data - check for both null AND empty string
        playUrl = intent.getStringExtra(EXTRA_PLAY_URL)?.takeIf { it.isNotBlank() } ?: run {
            Log.e(TAG, "No play URL provided or URL is empty")
            Toast.makeText(this, "Error: No video URL", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        videoTitle = intent.getStringExtra(EXTRA_TITLE)
        preExtractedYouTubeId = intent.getStringExtra(EXTRA_YOUTUBE_ID)

        Log.d(TAG, "Playing IMVBox video: $playUrl, title: $videoTitle, youtubeId: $preExtractedYouTubeId")

        // Use the same layout as YouTubePlayerActivity
        setContentView(R.layout.activity_youtube_player)

        // Initialize views
        rootLayout = findViewById(R.id.root_layout)
        webView = findViewById(R.id.youtube_webview)
        loadingIndicator = findViewById(R.id.loading_indicator)
        castButton = findViewById(R.id.cast_button)

        // Initialize control overlay
        controlOverlay = findViewById(R.id.control_overlay)
        btnRewind = findViewById(R.id.btn_rewind)
        btnPlayPause = findViewById(R.id.btn_play_pause)
        btnForward = findViewById(R.id.btn_forward)
        controlHint = findViewById(R.id.control_hint)

        // Update hint for IMVBox
        controlHint.text = "Use D-pad to control playback"

        // Initialize Cast button
        initializeCast()

        // Setup control buttons
        setupControlButtons()

        // Setup WebView and load video
        setupWebView()
        injectAuthCookies()

        // If we have pre-extracted YouTube ID, use loadDataWithBaseURL to spoof origin
        // This skips loading IMVBox page entirely while pretending to be imvbox.com
        if (!preExtractedYouTubeId.isNullOrEmpty()) {
            Log.d(TAG, "Using loadDataWithBaseURL trick to spoof imvbox.com origin")
            loadYouTubeWithSpoofedOrigin(preExtractedYouTubeId!!)
            introSkipped = true
        } else {
            // No pre-extracted ID - load actual IMVBox page
            loadIMVBoxPage()
            // Start checking for skip button
            skipCheckHandler.postDelayed(skipCheckRunnable, 3000) // Start after 3 seconds
        }

        // Setup back button handling
        setupBackButton()
    }

    private fun hideSystemUI() {
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        )
    }

    private fun initializeCast() {
        try {
            CastContext.getSharedInstance(this)
            CastButtonFactory.setUpMediaRouteButton(this, castButton)
            Log.d(TAG, "Cast button initialized")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Cast: ${e.message}")
            castButton.visibility = View.GONE
        }
    }

    /**
     * Inject authentication cookies for full movie access
     */
    private fun injectAuthCookies() {
        if (!IMVBoxAuthManager.isLoggedIn(this)) {
            Log.w(TAG, "User not logged in to IMVBox - full movies require login")
            Toast.makeText(this, "IMVBox login required for full movies", Toast.LENGTH_LONG).show()
            return
        }

        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)

        try {
            val cookies = IMVBoxAuthManager.getWebViewCookies()
            val url = "https://$IMVBOX_DOMAIN"

            cookies.forEach { cookie ->
                cookieManager.setCookie(url, cookie)
            }
            cookieManager.flush()

            Log.d(TAG, "Injected ${cookies.size} auth cookies")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to inject cookies: ${e.message}")
        }
    }

    private fun setupControlButtons() {
        // Rewind button - seek back 10 seconds
        btnRewind.setOnClickListener {
            seekVideo(-10)
            resetOverlayHideTimer()
        }
        btnRewind.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) resetOverlayHideTimer()
        }

        // Play/Pause button
        btnPlayPause.setOnClickListener {
            togglePlayPause()
            resetOverlayHideTimer()
        }
        btnPlayPause.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) resetOverlayHideTimer()
        }

        // Forward button - seek forward 10 seconds
        btnForward.setOnClickListener {
            seekVideo(10)
            resetOverlayHideTimer()
        }
        btnForward.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) resetOverlayHideTimer()
        }
    }

    /**
     * Toggle play/pause by simulating touch at center of WebView
     * This sends real Android touch events that the YouTube iframe will receive
     */
    private fun togglePlayPause() {
        // LAYOUT FIX: Verify WebView has been laid out before calculating coordinates
        if (webView.width == 0 || webView.height == 0) {
            Log.w(TAG, "WebView not laid out yet, skipping togglePlayPause")
            return
        }

        // Simulate tap at center of WebView to toggle YouTube play/pause
        val centerX = webView.width / 2f
        val centerY = webView.height / 2f

        val downTime = android.os.SystemClock.uptimeMillis()
        val eventTime = downTime + 50

        // Send touch down
        val downEvent = android.view.MotionEvent.obtain(
            downTime, downTime,
            android.view.MotionEvent.ACTION_DOWN,
            centerX, centerY, 0
        )
        webView.dispatchTouchEvent(downEvent)
        downEvent.recycle()

        // Send touch up (complete the tap)
        val upEvent = android.view.MotionEvent.obtain(
            downTime, eventTime,
            android.view.MotionEvent.ACTION_UP,
            centerX, centerY, 0
        )
        webView.dispatchTouchEvent(upEvent)
        upEvent.recycle()

        Log.d(TAG, "Simulated tap at center: $centerX, $centerY")
    }

    /**
     * Seek video by simulating double-tap on left/right edge of WebView
     * YouTube supports double-tap gesture: left = -10s, right = +10s
     */
    private fun seekVideo(seconds: Int) {
        // LAYOUT FIX: Verify WebView has been laid out before calculating coordinates
        if (webView.width == 0 || webView.height == 0) {
            Log.w(TAG, "WebView not laid out yet, skipping seekVideo")
            return
        }

        // Double-tap on left edge for rewind, right edge for forward
        val tapX = if (seconds < 0) {
            webView.width * 0.15f  // Left 15% of screen
        } else {
            webView.width * 0.85f  // Right 15% of screen
        }
        val tapY = webView.height / 2f

        val downTime = android.os.SystemClock.uptimeMillis()

        // First tap
        simulateTap(tapX, tapY, downTime)

        // Second tap (double-tap) after short delay
        // LIFECYCLE FIX: Use hideHandler and check Activity state
        if (!isFinishing && !isDestroyed) {
            hideHandler.postDelayed({
                if (!isFinishing && !isDestroyed) {
                    simulateTap(tapX, tapY, android.os.SystemClock.uptimeMillis())
                    Log.d(TAG, "Double-tap seek ${if (seconds < 0) "back" else "forward"} at $tapX")
                }
            }, 50)
        }
    }

    /**
     * Helper to simulate a single tap at given coordinates
     */
    private fun simulateTap(x: Float, y: Float, downTime: Long) {
        val eventTime = downTime + 30

        val downEvent = android.view.MotionEvent.obtain(
            downTime, downTime,
            android.view.MotionEvent.ACTION_DOWN,
            x, y, 0
        )
        webView.dispatchTouchEvent(downEvent)
        downEvent.recycle()

        val upEvent = android.view.MotionEvent.obtain(
            downTime, eventTime,
            android.view.MotionEvent.ACTION_UP,
            x, y, 0
        )
        webView.dispatchTouchEvent(upEvent)
        upEvent.recycle()
    }

    /**
     * Try to click the "Skip" button for intro video
     * IMVBox uses class "skip-banner-video" for their skip button
     */
    private fun tryClickSkipButton() {
        val js = """
            (function() {
                // IMVBox exact skip button class (found via browser inspection)
                var skipBtn = document.querySelector('.skip-banner-video');
                if (skipBtn && skipBtn.offsetParent !== null) {
                    console.log('Found IMVBox skip button: .skip-banner-video');
                    skipBtn.click();
                    return 'skipped:.skip-banner-video';
                }

                // Also check parent container with class "button"
                var buttonContainer = document.querySelector('.button .skip-banner-video');
                if (buttonContainer && buttonContainer.offsetParent !== null) {
                    console.log('Found skip in button container');
                    buttonContainer.click();
                    return 'skipped:.button .skip-banner-video';
                }

                // Fallback selectors
                var skipSelectors = [
                    'a.skip-banner-video',
                    '.skip-button',
                    '.skip-intro',
                    '.skip-ad',
                    '.skip',
                    'a[href="javascript:;"]'
                ];

                for (var i = 0; i < skipSelectors.length; i++) {
                    var btn = document.querySelector(skipSelectors[i]);
                    if (btn && btn.offsetParent !== null) {
                        var text = (btn.textContent || '').toLowerCase().trim();
                        // Only click if it says "skip"
                        if (text === 'skip' || text.includes('skip')) {
                            console.log('Found skip button: ' + skipSelectors[i] + ' text: ' + text);
                            btn.click();
                            return 'skipped:' + skipSelectors[i];
                        }
                    }
                }

                // Try to find by exact text "Skip"
                var allLinks = document.querySelectorAll('a');
                for (var j = 0; j < allLinks.length; j++) {
                    var link = allLinks[j];
                    var text = (link.textContent || '').trim();
                    if (text === 'Skip' && link.offsetParent !== null) {
                        console.log('Found Skip link by text');
                        link.click();
                        return 'skipped-by-text';
                    }
                }

                return 'no-skip-found';
            })()
        """.trimIndent()

        webView.evaluateJavascript(js) { result ->
            val cleanResult = result?.replace("\"", "") ?: ""
            if (cleanResult.startsWith("skipped") || cleanResult.startsWith("started") || cleanResult.startsWith("clicked-by-text")) {
                Log.d(TAG, "Intro skipped/started: $cleanResult")
                introSkipped = true
                skipCheckHandler.removeCallbacks(skipCheckRunnable)
                // Hide skip button after clicking it
                hideSkipButton()
            } else if (cleanResult == "play-clicked") {
                Log.d(TAG, "Play button clicked, continuing to watch for skip")
            }
        }
    }

    /**
     * Hide the skip button after intro is skipped
     */
    private fun hideSkipButton() {
        val js = """
            (function() {
                // Hide IMVBox skip button and its container
                var skipElements = document.querySelectorAll('.skip-banner-video, .button:has(.skip-banner-video), .skip-button, .skip-intro');
                skipElements.forEach(function(el) {
                    el.style.display = 'none';
                    el.style.visibility = 'hidden';
                });
                return 'hidden:' + skipElements.length;
            })()
        """.trimIndent()

        webView.evaluateJavascript(js) { result ->
            Log.d(TAG, "Hide skip button result: $result")
        }

        // Also hide after a delay in case new elements appear
        // LIFECYCLE FIX: Check Activity state before posting delayed work
        if (!isFinishing && !isDestroyed) {
            hideHandler.postDelayed({
                if (!isFinishing && !isDestroyed) {
                    webView.evaluateJavascript(js) { }
                }
            }, 2000)
        }
    }

    /**
     * Inject a MutationObserver that watches for the skip button and clicks it INSTANTLY
     * Also tries to skip the intro video by seeking to end or triggering ended event
     */
    private fun injectSkipButtonObserver() {
        val js = """
            (function() {
                // Check if already injected
                if (window.farsiPlexSkipInjected) return 'already-injected';
                window.farsiPlexSkipInjected = true;

                console.log('FarsiPlex: Installing intro skip system');

                var introSkipped = false;

                // Function to skip intro by manipulating video player
                function skipIntroVideo() {
                    if (introSkipped) return;

                    // Method 1: Skip intro via Video.js player
                    if (typeof videojs !== 'undefined') {
                        var players = document.querySelectorAll('.video-js');
                        for (var i = 0; i < players.length; i++) {
                            try {
                                var player = videojs.getPlayer(players[i]);
                                if (player && player.currentSrc()) {
                                    var src = player.currentSrc();
                                    // Check if this is the intro (HLS from streaming.imvbox.com with media/3628)
                                    if (src.indexOf('3628') !== -1 || src.indexOf('.m3u8') !== -1) {
                                        console.log('FarsiPlex: Found intro video, skipping to end');
                                        // Seek to end to trigger 'ended' event
                                        var duration = player.duration();
                                        if (duration && duration > 0) {
                                            player.currentTime(duration - 0.1);
                                            console.log('FarsiPlex: Seeked to end: ' + duration);
                                            introSkipped = true;
                                            return true;
                                        }
                                    }
                                }
                            } catch(e) {
                                console.log('FarsiPlex: Error with player ' + i + ': ' + e);
                            }
                        }
                    }

                    // Method 2: Directly manipulate HTML5 video element
                    var videos = document.querySelectorAll('video');
                    videos.forEach(function(video) {
                        if (video.src && video.src.indexOf('.m3u8') !== -1) {
                            console.log('FarsiPlex: Found HLS video element');
                            if (video.duration && video.duration > 0) {
                                video.currentTime = video.duration - 0.1;
                                console.log('FarsiPlex: Seeked HTML5 video to end');
                                introSkipped = true;
                            }
                        }
                    });

                    return introSkipped;
                }

                // Function to click skip button
                function clickSkip(element) {
                    console.log('FarsiPlex: CLICKING SKIP BUTTON!');
                    element.click();
                    element.style.display = 'none';
                    introSkipped = true;
                    return true;
                }

                // Check if skip button already exists
                var existing = document.querySelector('.skip-banner-video');
                if (existing && existing.offsetParent !== null) {
                    clickSkip(existing);
                    return 'clicked-existing';
                }

                // Create MutationObserver for skip button
                var observer = new MutationObserver(function(mutations) {
                    if (introSkipped) return;
                    var skipBtn = document.querySelector('.skip-banner-video');
                    if (skipBtn && skipBtn.offsetParent !== null) {
                        clickSkip(skipBtn);
                        observer.disconnect();
                    }
                });

                observer.observe(document.body, {
                    childList: true,
                    subtree: true,
                    attributes: true
                });

                // Aggressive checking - every 200ms
                var checkCount = 0;
                var intervalId = setInterval(function() {
                    checkCount++;

                    // Try to click skip button
                    var skipBtn = document.querySelector('.skip-banner-video');
                    if (skipBtn && skipBtn.offsetParent !== null) {
                        clickSkip(skipBtn);
                        clearInterval(intervalId);
                        return;
                    }

                    // After 2 seconds, also try to skip intro video directly
                    if (checkCount > 10 && !introSkipped) {
                        skipIntroVideo();
                    }

                    // Stop after 60 seconds
                    if (checkCount > 300 || introSkipped) {
                        clearInterval(intervalId);
                    }
                }, 200);

                return 'skip-system-installed';
            })()
        """.trimIndent()

        webView.evaluateJavascript(js) { result ->
            Log.d(TAG, "Skip system result: $result")
        }
    }

    /**
     * Inject YouTube iframe directly, completely replacing the intro player.
     * This skips the intro entirely because we already know the YouTube ID.
     */
    private fun injectYouTubeDirectly(youtubeId: String) {
        val js = """
            (function() {
                console.log('FarsiPlex: Injecting YouTube directly: $youtubeId');

                // Hide/remove the intro video player
                var videoContainers = document.querySelectorAll('.video-js, #player, .player-container, video');
                videoContainers.forEach(function(el) {
                    el.style.display = 'none';
                    el.pause && el.pause();
                });

                // Stop any playing Video.js players
                if (typeof videojs !== 'undefined') {
                    var players = document.querySelectorAll('.video-js');
                    players.forEach(function(p) {
                        try {
                            var player = videojs.getPlayer(p);
                            if (player) {
                                player.pause();
                                player.dispose();
                            }
                        } catch(e) {}
                    });
                }

                // Find or create container for YouTube iframe
                var container = document.querySelector('.player-container, #player-container, .video-container');
                if (!container) {
                    container = document.body;
                }

                // Create YouTube iframe
                var iframe = document.createElement('iframe');
                iframe.id = 'farsiplex-youtube-player';
                iframe.src = 'https://www.youtube.com/embed/$youtubeId?autoplay=1&enablejsapi=1&rel=0&modestbranding=1&playsinline=1';
                iframe.style.cssText = 'position: fixed; top: 0; left: 0; width: 100vw; height: 100vh; border: none; z-index: 9999; background: #000;';
                iframe.setAttribute('allow', 'accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture');
                iframe.setAttribute('allowfullscreen', 'true');

                // Remove existing farsiplex player if any
                var existing = document.getElementById('farsiplex-youtube-player');
                if (existing) existing.remove();

                // Add to page
                document.body.appendChild(iframe);

                // Hide skip button and other UI
                var hideElements = document.querySelectorAll('.skip-banner-video, .button, .skip-button, .mobile-menu-slider-container, nav.navbar');
                hideElements.forEach(function(el) {
                    el.style.display = 'none';
                });

                console.log('FarsiPlex: YouTube iframe injected successfully');
                return 'youtube-injected';
            })()
        """.trimIndent()

        webView.evaluateJavascript(js) { result ->
            Log.d(TAG, "YouTube injection result: $result")
        }
    }

    /**
     * Load YouTube embed with spoofed imvbox.com origin using loadDataWithBaseURL.
     *
     * This trick makes the WebView report its origin as imvbox.com, which YouTube
     * checks via window.location.ancestorOrigins. Since IMVBox is whitelisted by
     * YouTube video owners, the embed is allowed.
     *
     * This completely skips loading the IMVBox page - we go directly to YouTube!
     */
    private fun loadYouTubeWithSpoofedOrigin(youtubeId: String) {
        Log.d(TAG, "Loading YouTube with spoofed origin: $youtubeId")

        // Create a minimal HTML page with YouTube embed
        // The baseURL parameter of loadDataWithBaseURL sets the origin
        val embedHtml = """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>FarsiPlex Player</title>
                <style>
                    * { margin: 0; padding: 0; box-sizing: border-box; }
                    html, body {
                        width: 100%;
                        height: 100%;
                        background: #000;
                        overflow: hidden;
                    }
                    .player-container {
                        width: 100vw;
                        height: 100vh;
                        display: flex;
                        align-items: center;
                        justify-content: center;
                    }
                    iframe {
                        width: 100%;
                        height: 100%;
                        border: none;
                    }
                </style>
            </head>
            <body>
                <div class="player-container">
                    <iframe id="ytplayer"
                        src="https://www.youtube.com/embed/$youtubeId?autoplay=1&controls=1&modestbranding=1&rel=0&showinfo=0&iv_load_policy=3&fs=1&playsinline=1&enablejsapi=1"
                        allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; fullscreen"
                        allowfullscreen>
                    </iframe>
                </div>
            </body>
            </html>
        """.trimIndent()

        // THE KEY TRICK: Use imvbox.com as the base URL!
        // This makes YouTube think the embed is coming from imvbox.com
        webView.loadDataWithBaseURL(
            "https://www.imvbox.com/movies/play",  // Fake origin = imvbox.com
            embedHtml,
            "text/html",
            "UTF-8",
            null
        )

        loadingIndicator.visibility = View.GONE
        Log.d(TAG, "YouTube loaded with spoofed imvbox.com origin")
    }

    private fun updatePlayPauseButton() {
        btnPlayPause.setImageResource(
            if (isPlaying) R.drawable.ic_play_pause else R.drawable.ic_play_pause
        )
    }

    private fun showControlOverlay() {
        if (!isOverlayVisible) {
            controlOverlay.visibility = View.VISIBLE
            isOverlayVisible = true
            btnPlayPause.requestFocus()
            Log.d(TAG, "Control overlay shown")
        }
        resetOverlayHideTimer()
    }

    private fun hideControlOverlay() {
        if (isOverlayVisible) {
            controlOverlay.visibility = View.GONE
            isOverlayVisible = false
            webView.requestFocus()
            Log.d(TAG, "Control overlay hidden")
        }
    }

    private fun resetOverlayHideTimer() {
        hideHandler.removeCallbacks(hideRunnable)
        hideHandler.postDelayed(hideRunnable, OVERLAY_HIDE_DELAY_MS)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView.apply {
            setBackgroundColor(Color.BLACK)

            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                mediaPlaybackRequiresUserGesture = false
                loadWithOverviewMode = true
                useWideViewPort = true
                builtInZoomControls = false
                displayZoomControls = false
                cacheMode = WebSettings.LOAD_DEFAULT
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                // Use mobile user agent for proper layout
                userAgentString = "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
            }

            // Enable third-party cookies for YouTube embeds
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    Log.d(TAG, "Page loaded: $url")
                    loadingIndicator.visibility = View.GONE

                    // Inject CSS to hide IMVBox website chrome and make video fullscreen
                    injectFullscreenCSS()

                    // If we have pre-extracted YouTube ID, inject it directly (skip intro entirely!)
                    if (!preExtractedYouTubeId.isNullOrEmpty()) {
                        Log.d(TAG, "Injecting pre-extracted YouTube: $preExtractedYouTubeId")
                        injectYouTubeDirectly(preExtractedYouTubeId!!)
                        introSkipped = true
                    } else {
                        // No pre-extracted ID - use normal skip button detection
                        injectSkipButtonObserver()

                        // Auto-click play button after page loads
                        // Use handler instead of view.postDelayed for lifecycle safety
                        hideHandler.postDelayed({
                            if (!isFinishing && !isDestroyed) {
                                clickPlayButton()
                            }
                        }, 500)

                        // Fallback: Also poll for skip button in case observer misses it
                        for (delay in listOf(3000L, 6000L, 10000L, 20000L, 30000L)) {
                            hideHandler.postDelayed({
                                if (!isFinishing && !isDestroyed) {
                                    tryClickSkipButton()
                                }
                            }, delay)
                        }
                    }
                }

                override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                    // Keep navigation within WebView for IMVBox domain
                    return if (url?.contains("imvbox.com") == true) {
                        false
                    } else {
                        // Block external navigation
                        Log.d(TAG, "Blocked external navigation: $url")
                        true
                    }
                }

                // Handle SSL certificate errors for streaming.imvbox.com
                // IMVBox's streaming server has an incomplete certificate chain
                @SuppressLint("WebViewClientOnReceivedSslError")
                override fun onReceivedSslError(
                    view: WebView?,
                    handler: SslErrorHandler?,
                    error: SslError?
                ) {
                    val url = error?.url ?: ""
                    // Only bypass SSL for IMVBox streaming domain
                    if (url.contains("imvbox.com") || url.contains("streaming.imvbox.com")) {
                        Log.w(TAG, "Bypassing SSL error for IMVBox: ${error?.primaryError}")
                        handler?.proceed()
                    } else {
                        Log.e(TAG, "SSL error for non-IMVBox domain: $url")
                        super.onReceivedSslError(view, handler, error)
                    }
                }
            }

            webChromeClient = object : WebChromeClient() {
                // Fullscreen video support
                override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                    if (customView != null) {
                        callback?.onCustomViewHidden()
                        return
                    }
                    customView = view
                    customViewCallback = callback
                    rootLayout.addView(view)
                    webView.visibility = View.GONE
                    hideSystemUI()
                    Log.d(TAG, "Entering fullscreen")
                }

                override fun onHideCustomView() {
                    if (customView == null) return
                    rootLayout.removeView(customView)
                    customView = null
                    webView.visibility = View.VISIBLE
                    customViewCallback?.onCustomViewHidden()
                    customViewCallback = null
                    hideSystemUI()
                    Log.d(TAG, "Exiting fullscreen")
                }

                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    if (newProgress < 100) {
                        loadingIndicator.visibility = View.VISIBLE
                    } else {
                        loadingIndicator.visibility = View.GONE
                    }
                }
            }

            // Enable hardware acceleration
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
        }
    }

    private fun loadIMVBoxPage() {
        Log.d(TAG, "Loading IMVBox page: $playUrl")
        webView.loadUrl(playUrl)
    }

    /**
     * Inject CSS to hide IMVBox website chrome (header/footer only)
     * Be careful not to break YouTube iframe rendering
     */
    private fun injectFullscreenCSS() {
        val css = """
            /* Hide IMVBox navigation menu - exact classes from their site */
            .mobile-menu-slider-container,
            .mobile-menu-slider,
            .navbar.navbar-expand-xl,
            .navbar.sticky-top,
            .mobile-search-container,
            nav.navbar {
                display: none !important;
                height: 0 !important;
                max-height: 0 !important;
                visibility: hidden !important;
                overflow: hidden !important;
            }

            /* Hide alerts and popups */
            .alert.alert-danger,
            .alert-dismissible {
                display: none !important;
            }

            /* Hide movie info overlay at top */
            .movie-info-overlay,
            .movie-details-header {
                display: none !important;
            }

            /* Hide IMVBox specific controls - be conservative to not break YouTube */
            .report-button,
            .flag-button,
            .imvbox-rating,
            .play-info,
            .movie-title-overlay,
            .movie-info,
            .player-title {
                display: none !important;
                visibility: hidden !important;
            }

            /* Make body fill screen with black background */
            html, body {
                margin: 0 !important;
                padding: 0 !important;
                padding-top: 0 !important;
                background: #000 !important;
                overflow: hidden !important;
            }

            /* Ensure video/player takes full screen */
            .video-js,
            #player,
            .vjs-tech {
                width: 100vw !important;
                height: 100vh !important;
            }

            /* Make YouTube iframe visible and properly sized */
            iframe[src*="youtube"],
            iframe[src*="youtu.be"] {
                width: 100% !important;
                height: 100% !important;
                display: block !important;
                visibility: visible !important;
                opacity: 1 !important;
            }

            /* Ensure video element is visible */
            video {
                display: block !important;
                visibility: visible !important;
                opacity: 1 !important;
            }

            /* Skip button will be hidden via JavaScript after intro */
        """.trimIndent()

        val js = """
            (function() {
                // Remove existing injected CSS if any
                var existing = document.getElementById('farsiplex-fullscreen-css');
                if (existing) existing.remove();

                // Inject CSS
                var style = document.createElement('style');
                style.id = 'farsiplex-fullscreen-css';
                style.textContent = `$css`;
                document.head.appendChild(style);
                console.log('FarsiPlex: Fullscreen CSS injected');

                // Ensure YouTube iframe is visible
                var ytIframe = document.querySelector('iframe[src*="youtube"]');
                if (ytIframe) {
                    ytIframe.style.visibility = 'visible';
                    ytIframe.style.opacity = '1';
                    ytIframe.style.display = 'block';
                    console.log('FarsiPlex: YouTube iframe made visible');
                }

                // Ensure video element is visible
                var video = document.querySelector('video');
                if (video) {
                    video.style.visibility = 'visible';
                    video.style.opacity = '1';
                    video.style.display = 'block';
                    console.log('FarsiPlex: Video element made visible');
                }

                return 'css-injected';
            })()
        """.trimIndent()

        webView.evaluateJavascript(js) { result ->
            Log.d(TAG, "CSS injection result: $result")
        }
    }

    /**
     * Click the big play button to start video
     */
    private fun clickPlayButton() {
        val js = """
            (function() {
                // Try multiple selectors for the play button
                var selectors = [
                    '.big-play.icon-play',
                    '.vjs-big-play-button',
                    '.video-js .vjs-big-play-button',
                    '#player .vjs-big-play-button',
                    'button.vjs-big-play-button',
                    '.big-play',
                    '.icon-play',
                    '[class*="play-button"]',
                    '[class*="big-play"]'
                ];

                for (var i = 0; i < selectors.length; i++) {
                    var btn = document.querySelector(selectors[i]);
                    if (btn && btn.offsetParent !== null) {
                        console.log('Found play button with selector: ' + selectors[i]);
                        btn.click();
                        return 'clicked:' + selectors[i];
                    }
                }

                // Try to auto-play the video element directly
                var video = document.querySelector('video');
                if (video) {
                    try {
                        video.play();
                        return 'auto-play-video';
                    } catch(e) {
                        console.log('Auto-play failed: ' + e);
                    }
                }

                // Debug: List all visible buttons
                var allButtons = document.querySelectorAll('button, [role="button"], .btn');
                var visibleBtns = [];
                allButtons.forEach(function(b) {
                    if (b.offsetParent !== null) {
                        visibleBtns.push(b.className || b.id || b.tagName);
                    }
                });
                console.log('Visible buttons: ' + visibleBtns.join(', '));

                return 'not-found';
            })()
        """.trimIndent()

        webView.evaluateJavascript(js) { result ->
            Log.d(TAG, "Play button click result: $result")
            // Retry if not found - LIFECYCLE FIX: Check Activity state
            if (result?.contains("not-found") == true && !isFinishing && !isDestroyed) {
                hideHandler.postDelayed({
                    if (!isFinishing && !isDestroyed) {
                        clickPlayButton()
                    }
                }, 2000)
            }
        }
    }

    private fun setupBackButton() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // If overlay is visible, hide it first
                if (isOverlayVisible) {
                    hideControlOverlay()
                    return
                }

                // If in fullscreen, exit fullscreen first
                if (customView != null) {
                    customViewCallback?.onCustomViewHidden()
                    return
                }

                Log.d(TAG, "Exiting IMVBox player")
                finish()
            }
        })
    }

    // D-pad / Remote control support
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        Log.d(TAG, "onKeyDown: keyCode=$keyCode, overlayVisible=$isOverlayVisible")

        // Handle key events BEFORE WebView can intercept them
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                Log.d(TAG, "Enter/Center pressed - toggling play/pause")
                togglePlayPause()
                return true // Consume the event
            }
            KeyEvent.KEYCODE_SPACE -> {
                Log.d(TAG, "Space pressed - toggling play/pause")
                togglePlayPause()
                return true
            }
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_MEDIA_REWIND -> {
                Log.d(TAG, "Left/Rewind - seeking back 10s")
                seekVideo(-10)
                return true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                Log.d(TAG, "Right/Forward - seeking forward 10s")
                seekVideo(10)
                return true
            }
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            KeyEvent.KEYCODE_MEDIA_PLAY,
            KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                Log.d(TAG, "Media key pressed - toggling play/pause")
                togglePlayPause()
                return true
            }
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                // Let these pass through to WebView for volume control etc.
                return super.onKeyDown(keyCode, event)
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // Intercept key events before they reach WebView
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_CENTER,
                KeyEvent.KEYCODE_ENTER,
                KeyEvent.KEYCODE_NUMPAD_ENTER,
                KeyEvent.KEYCODE_SPACE -> {
                    Log.d(TAG, "dispatchKeyEvent: Intercepting ${event.keyCode} for play/pause")
                    togglePlayPause()
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onResume() {
        super.onResume()
        hideSystemUI()
        webView.onResume()
    }

    override fun onPause() {
        super.onPause()
        webView.onPause()
        // Remove ALL pending callbacks (including inline lambdas)
        hideHandler.removeCallbacksAndMessages(null)
        skipCheckHandler.removeCallbacksAndMessages(null)
    }

    override fun onDestroy() {
        // Clear callbacks FIRST before destroying resources
        hideHandler.removeCallbacksAndMessages(null)
        skipCheckHandler.removeCallbacksAndMessages(null)

        // Clean up WebView properly
        webView.stopLoading()
        webView.destroy()

        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Call super.onDestroy() LAST per Android lifecycle best practice
        super.onDestroy()
    }
}
