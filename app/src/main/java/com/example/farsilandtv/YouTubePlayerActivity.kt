package com.example.farsilandtv

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.mediarouter.app.MediaRouteButton
import com.google.android.gms.cast.framework.CastButtonFactory
import com.google.android.gms.cast.framework.CastContext
import dagger.hilt.android.AndroidEntryPoint

/**
 * Activity for playing YouTube videos with in-app WebView.
 * Used for IMVBox content that uses YouTube as the video source.
 *
 * Features:
 * - In-app YouTube playback via WebView embed
 * - Full D-pad/remote control support with visible overlay
 * - Fullscreen video support
 * - Auto-hiding control overlay
 */
@AndroidEntryPoint
class YouTubePlayerActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "YouTubePlayerActivity"
        private const val EXTRA_VIDEO_ID = "video_id"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_START_POSITION = "start_position"
        private const val EXTRA_SKIP_EMBED = "skip_embed"
        private const val OVERLAY_HIDE_DELAY_MS = 5000L // 5 seconds

        /**
         * Create intent to launch YouTube player
         *
         * @param skipEmbed If true, skip WebView embed and open YouTube app directly.
         *                  Use for sources like IMVBox where embed fails with Error 153.
         */
        fun createIntent(
            context: Context,
            videoId: String,
            title: String? = null,
            startPositionSeconds: Float = 0f,
            skipEmbed: Boolean = false
        ): Intent {
            return Intent(context, YouTubePlayerActivity::class.java).apply {
                putExtra(EXTRA_VIDEO_ID, videoId)
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_START_POSITION, startPositionSeconds)
                putExtra(EXTRA_SKIP_EMBED, skipEmbed)
            }
        }

        /**
         * Extract YouTube video ID from various URL formats
         */
        fun extractVideoId(url: String): String? {
            val patterns = listOf(
                Regex("""youtube\.com/watch\?v=([a-zA-Z0-9_-]{11})"""),
                Regex("""youtube\.com/embed/([a-zA-Z0-9_-]{11})"""),
                Regex("""youtu\.be/([a-zA-Z0-9_-]{11})"""),
                Regex("""youtube\.com/v/([a-zA-Z0-9_-]{11})"""),
                Regex("""youtube-nocookie\.com/embed/([a-zA-Z0-9_-]{11})""")
            )

            for (pattern in patterns) {
                pattern.find(url)?.let { match ->
                    return match.groupValues[1]
                }
            }

            // If the string itself looks like a video ID
            if (url.matches(Regex("""^[a-zA-Z0-9_-]{11}$"""))) {
                return url
            }

            return null
        }
    }

    // UI Components
    private lateinit var webView: WebView
    private lateinit var loadingIndicator: ProgressBar
    private lateinit var castButton: MediaRouteButton
    private lateinit var rootLayout: FrameLayout
    private lateinit var focusInterceptor: View

    // Control overlay components
    private lateinit var controlOverlay: LinearLayout
    private lateinit var btnRewind: ImageButton
    private lateinit var btnPlayPause: ImageButton
    private lateinit var btnForward: ImageButton
    private lateinit var controlHint: TextView

    // Player state
    private var videoId: String = ""
    private var videoTitle: String? = null
    private var startPositionSeconds: Float = 0f

    // Fullscreen support
    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null

    // Overlay auto-hide
    private val hideHandler = Handler(Looper.getMainLooper())
    private val hideRunnable = Runnable { hideControlOverlay() }
    private var isOverlayVisible = false

    // Fallback to YouTube app when embed fails
    private var hasTriedYouTubeApp = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Keep screen on during playback
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Fullscreen immersive mode
        hideSystemUI()

        // Extract intent data
        videoId = intent.getStringExtra(EXTRA_VIDEO_ID) ?: run {
            Log.e(TAG, "No video ID provided")
            Toast.makeText(this, "Error: No video ID", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        videoTitle = intent.getStringExtra(EXTRA_TITLE)
        startPositionSeconds = intent.getFloatExtra(EXTRA_START_POSITION, 0f)
        val skipEmbed = intent.getBooleanExtra(EXTRA_SKIP_EMBED, false)

        Log.d(TAG, "Playing YouTube video: $videoId, title: $videoTitle, start: $startPositionSeconds, skipEmbed: $skipEmbed")

        // Skip embed attempt - show dialog for IMVBox content (Error 153 would block embed)
        if (skipEmbed) {
            Log.d(TAG, "Skip embed flag set - showing playback options dialog")
            showPlaybackOptionsDialog()
            return
        }

        setContentView(R.layout.activity_youtube_player)

        // Initialize views
        rootLayout = findViewById(R.id.root_layout)
        webView = findViewById(R.id.youtube_webview)
        loadingIndicator = findViewById(R.id.loading_indicator)
        castButton = findViewById(R.id.cast_button)
        focusInterceptor = findViewById(R.id.focus_interceptor)

        // Give focus to interceptor, not WebView
        focusInterceptor.requestFocus()

        // Initialize control overlay
        controlOverlay = findViewById(R.id.control_overlay)
        btnRewind = findViewById(R.id.btn_rewind)
        btnPlayPause = findViewById(R.id.btn_play_pause)
        btnForward = findViewById(R.id.btn_forward)
        controlHint = findViewById(R.id.control_hint)

        // Initialize Cast button
        initializeCast()

        // Setup control buttons
        setupControlButtons()

        // Setup WebView and load video
        setupWebView()
        loadYouTubeVideo()

        // Setup back button handling
        setupBackButton()
    }

    private fun hideSystemUI() {
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

    private fun setupControlButtons() {
        // Rewind button - seek back 10 seconds
        btnRewind.setOnClickListener {
            sendSeekCommand(backward = true)
            resetOverlayHideTimer()
        }
        btnRewind.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) resetOverlayHideTimer()
        }

        // Play/Pause button
        btnPlayPause.setOnClickListener {
            sendPlayPauseCommand()
            resetOverlayHideTimer()
        }
        btnPlayPause.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) resetOverlayHideTimer()
        }

        // Forward button - seek forward 10 seconds
        btnForward.setOnClickListener {
            sendSeekCommand(backward = false)
            resetOverlayHideTimer()
        }
        btnForward.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) resetOverlayHideTimer()
        }
    }

    private fun sendPlayPauseCommand() {
        // Use YouTube IFrame API via JavaScript
        webView.evaluateJavascript("""
            (function() {
                try {
                    // Try IFrame API first (if player was created with API)
                    if (typeof player !== 'undefined' && player.getPlayerState) {
                        var state = player.getPlayerState();
                        if (state === 1) { // Playing
                            player.pauseVideo();
                        } else {
                            player.playVideo();
                        }
                        return 'api';
                    }

                    // Fallback: Click on video element to toggle
                    var video = document.querySelector('video');
                    if (video) {
                        if (video.paused) {
                            video.play();
                        } else {
                            video.pause();
                        }
                        return 'video';
                    }

                    // Last resort: Click the play button
                    var playBtn = document.querySelector('.ytp-play-button');
                    if (playBtn) {
                        playBtn.click();
                        return 'button';
                    }

                    return 'none';
                } catch(e) {
                    return 'error: ' + e.message;
                }
            })()
        """.trimIndent()) { result ->
            Log.d(TAG, "Play/pause result: $result")
        }
    }

    private fun sendSeekCommand(backward: Boolean) {
        val seekSeconds = if (backward) -10 else 10
        webView.evaluateJavascript("""
            (function() {
                try {
                    // Try IFrame API first
                    if (typeof player !== 'undefined' && player.getCurrentTime) {
                        var time = player.getCurrentTime();
                        player.seekTo(time + ($seekSeconds), true);
                        return 'api';
                    }

                    // Fallback: Direct video element control
                    var video = document.querySelector('video');
                    if (video) {
                        video.currentTime += $seekSeconds;
                        return 'video';
                    }

                    return 'none';
                } catch(e) {
                    return 'error: ' + e.message;
                }
            })()
        """.trimIndent()) { result ->
            Log.d(TAG, "Seek ${if (backward) "back" else "forward"} result: $result")
        }
    }

    private fun showControlOverlay() {
        if (!isOverlayVisible) {
            controlOverlay.visibility = View.VISIBLE
            isOverlayVisible = true
            // Request focus on play/pause button for D-pad navigation
            btnPlayPause.requestFocus()
            Log.d(TAG, "Control overlay shown")
        }
        resetOverlayHideTimer()
    }

    private fun hideControlOverlay() {
        if (isOverlayVisible) {
            controlOverlay.visibility = View.GONE
            isOverlayVisible = false
            Log.d(TAG, "Control overlay hidden")
        }
    }

    private fun toggleControlOverlay() {
        if (isOverlayVisible) {
            hideControlOverlay()
        } else {
            showControlOverlay()
        }
    }

    private fun resetOverlayHideTimer() {
        hideHandler.removeCallbacks(hideRunnable)
        hideHandler.postDelayed(hideRunnable, OVERLAY_HIDE_DELAY_MS)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView.apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                mediaPlaybackRequiresUserGesture = false
                loadWithOverviewMode = true
                useWideViewPort = true
                builtInZoomControls = false
                displayZoomControls = false
                cacheMode = WebSettings.LOAD_DEFAULT
            }

            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    Log.d(TAG, "Page loaded: $url")
                    loadingIndicator.visibility = View.GONE

                    // Check for YouTube playback errors after page loads
                    view?.postDelayed({
                        checkForYouTubeError(view)
                    }, 2000) // Wait 2s for player to initialize
                }

                override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                    // Keep navigation within WebView
                    return false
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

            // CRITICAL: Disable WebView focus so D-pad events go to Activity
            isFocusable = false
            isFocusableInTouchMode = false
        }
    }

    private fun loadYouTubeVideo() {
        // Validate video ID
        val trimmedVideoId = videoId.trim()
        if (trimmedVideoId.length != 11 || !trimmedVideoId.matches(Regex("^[a-zA-Z0-9_-]+$"))) {
            Log.e(TAG, "Invalid video ID format: '$trimmedVideoId'")
            Toast.makeText(this, "Invalid YouTube video ID", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val startSeconds = startPositionSeconds.toInt()

        Log.d(TAG, "Loading YouTube video via IFrame API: $trimmedVideoId, start: $startSeconds")

        // HTML page with YouTube IFrame Player API for proper JavaScript control
        val html = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                <style>
                    * { margin: 0; padding: 0; box-sizing: border-box; }
                    html, body { width: 100%; height: 100%; background: #000; overflow: hidden; }
                    #player { width: 100%; height: 100%; }
                </style>
            </head>
            <body>
                <div id="player"></div>
                <script src="https://www.youtube.com/iframe_api"></script>
                <script>
                    var player;

                    function onYouTubeIframeAPIReady() {
                        player = new YT.Player('player', {
                            videoId: '$trimmedVideoId',
                            playerVars: {
                                'autoplay': 1,
                                'controls': 1,
                                'playsinline': 1,
                                'rel': 0,
                                'modestbranding': 1,
                                'start': $startSeconds,
                                'fs': 0,
                                'enablejsapi': 1
                            },
                            events: {
                                'onReady': onPlayerReady,
                                'onError': onPlayerError
                            }
                        });
                    }

                    function onPlayerReady(event) {
                        console.log('YouTube player ready');
                        event.target.playVideo();
                    }

                    function onPlayerError(event) {
                        console.log('YouTube player error: ' + event.data);
                        // Error codes: 2=invalid ID, 5=HTML5 error, 100=not found, 101/150=embed blocked
                        if (event.data === 101 || event.data === 150) {
                            window.location.href = 'https://www.youtube.com/watch?v=$trimmedVideoId';
                        }
                    }
                </script>
            </body>
            </html>
        """.trimIndent()

        // Load with YouTube origin to avoid embedding restrictions
        webView.loadDataWithBaseURL(
            "https://www.youtube.com",
            html,
            "text/html",
            "UTF-8",
            null
        )
    }

    /**
     * Check for YouTube playback errors (like Error 153) by inspecting the page content.
     * If an error is detected, fallback to opening the YouTube app.
     */
    private fun checkForYouTubeError(view: WebView) {
        if (hasTriedYouTubeApp) return

        view.evaluateJavascript("""
            (function() {
                // Check for error overlay
                var errorText = document.querySelector('.ytp-error-content-wrap-reason');
                if (errorText) return errorText.innerText;

                // Check for error container
                var errorContainer = document.querySelector('.ytp-error');
                if (errorContainer && errorContainer.style.display !== 'none') {
                    return 'YouTube player error detected';
                }

                // Check for playback errors in player state
                var html = document.body ? document.body.innerHTML : '';
                if (html.indexOf('Video unavailable') !== -1) return 'Video unavailable';
                if (html.indexOf('playback on other websites') !== -1) return 'Embedding disabled';
                if (html.indexOf('Error 153') !== -1) return 'Error 153';

                return '';
            })()
        """.trimIndent()) { result ->
            val error = result?.replace("\"", "")?.trim() ?: ""
            Log.d(TAG, "YouTube error check result: '$error'")

            if (error.isNotEmpty() && error != "null") {
                Log.w(TAG, "YouTube embed error detected: $error")
                openYouTubeApp()
            }
        }
    }

    /**
     * Show dialog with playback options for IMVBox content
     * IMVBox full movies require login for HLS streaming - YouTube is just trailer
     */
    private fun showPlaybackOptionsDialog() {
        val builder = android.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
        builder.setTitle("IMVBox Playback")
        builder.setMessage(
            "Full movie playback requires IMVBox login.\n\n" +
            "• Login: Go to Settings → IMVBox Account\n" +
            "• This video is a YouTube trailer\n\n" +
            "Would you like to open in YouTube app?"
        )
        builder.setPositiveButton("Open YouTube") { _, _ ->
            openYouTubeApp()
        }
        builder.setNegativeButton("Go Back") { _, _ ->
            finish()
        }
        builder.setOnCancelListener {
            finish()
        }
        builder.show()
    }

    /**
     * Open video in YouTube app as fallback when embedding fails
     */
    private fun openYouTubeApp() {
        if (hasTriedYouTubeApp) return
        hasTriedYouTubeApp = true

        Log.d(TAG, "Opening YouTube app for video: $videoId")
        Toast.makeText(this, "Opening in YouTube...", Toast.LENGTH_SHORT).show()

        val youtubeUrl = "https://www.youtube.com/watch?v=$videoId"

        try {
            // Let the system choose the best YouTube handler
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(youtubeUrl))
            startActivity(intent)
            finish()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open YouTube: ${e.message}")
            Toast.makeText(this, "Cannot play this video", Toast.LENGTH_LONG).show()
            finish()
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

                // If WebView can go back, go back
                if (webView.canGoBack()) {
                    webView.goBack()
                    return
                }

                Log.d(TAG, "Exiting YouTube player")
                finish()
            }
        })
    }

    // D-pad / Remote control support - intercept at dispatch level before WebView consumes them
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // Only handle key down events to avoid double-processing
        if (event.action != KeyEvent.ACTION_DOWN) {
            // Let back button pass through normally
            if (event.keyCode == KeyEvent.KEYCODE_BACK) {
                return super.dispatchKeyEvent(event)
            }
            return true // Consume key up for handled keys
        }

        val keyCode = event.keyCode
        Log.d(TAG, "Key event: $keyCode")

        // If overlay is visible, let the buttons handle D-pad navigation
        if (isOverlayVisible) {
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_CENTER,
                KeyEvent.KEYCODE_ENTER -> {
                    // Let focused button handle click
                    return super.dispatchKeyEvent(event)
                }
                KeyEvent.KEYCODE_DPAD_LEFT,
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    // Navigate between overlay buttons
                    resetOverlayHideTimer()
                    return super.dispatchKeyEvent(event)
                }
                KeyEvent.KEYCODE_DPAD_UP,
                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    // Hide overlay
                    hideControlOverlay()
                    return true
                }
                KeyEvent.KEYCODE_BACK -> {
                    // Hide overlay on back
                    hideControlOverlay()
                    return true
                }
            }
        }

        // Handle D-pad for video control (before WebView gets them)
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER -> {
                sendPlayPauseCommand()
                showControlOverlay()
                return true
            }
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                showControlOverlay()
                return true
            }
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_MEDIA_REWIND -> {
                sendSeekCommand(backward = true)
                showControlOverlay()
                return true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                sendSeekCommand(backward = false)
                showControlOverlay()
                return true
            }
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            KeyEvent.KEYCODE_MEDIA_PLAY,
            KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                sendPlayPauseCommand()
                return true
            }
            KeyEvent.KEYCODE_BACK -> {
                // Let back button pass through to normal handling
                return super.dispatchKeyEvent(event)
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onResume() {
        super.onResume()
        hideSystemUI()
        // Only access webView if it was initialized (skipEmbed skips setContentView)
        if (::webView.isInitialized) {
            webView.onResume()
        }
    }

    override fun onPause() {
        super.onPause()
        // Only access webView if it was initialized (skipEmbed skips setContentView)
        if (::webView.isInitialized) {
            webView.onPause()
        }
        hideHandler.removeCallbacks(hideRunnable)
    }

    override fun onDestroy() {
        super.onDestroy()
        hideHandler.removeCallbacks(hideRunnable)
        // Only destroy webView if it was initialized (skipEmbed skips setContentView)
        if (::webView.isInitialized) {
            webView.destroy()
        }
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}
