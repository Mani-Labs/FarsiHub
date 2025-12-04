package com.example.farsilandtv

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Uri
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.media3.common.PlaybackParameters
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.Lifecycle
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withTimeout
import com.example.farsilandtv.data.models.VideoUrl
import com.example.farsilandtv.data.repository.ContentRepository
import com.example.farsilandtv.data.repository.WatchlistRepository
import com.example.farsilandtv.data.repository.PlaybackRepository
import com.example.farsilandtv.data.scraper.VideoUrlScraper
import com.example.farsilandtv.data.scraper.ScraperResult
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import androidx.media3.common.Format
import androidx.media3.common.Tracks
import androidx.media3.common.C
import com.example.farsilandtv.utils.AutoFrameRateHelper
import com.example.farsilandtv.utils.IntentExtras
import com.example.farsilandtv.cast.CastManager
import com.example.farsilandtv.data.imvbox.IMVBoxVideoExtractor
import androidx.mediarouter.app.MediaRouteButton
import com.google.android.gms.cast.framework.CastButtonFactory
import com.google.android.gms.cast.framework.CastSession
import java.io.File
import kotlinx.coroutines.launch
import androidx.media3.datasource.okhttp.OkHttpDataSource
import okhttp3.OkHttpClient
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import java.util.concurrent.TimeUnit

/**
 * Video Player Activity for movies and episodes
 * Uses ExoPlayer for direct MP4 playback from Farsiland CDN
 */
@AndroidEntryPoint
class VideoPlayerActivity : AppCompatActivity() {

    private lateinit var playerView: PlayerView
    private lateinit var loadingIndicator: ProgressBar
    private lateinit var errorText: TextView
    private var player: ExoPlayer? = null

    // Enhanced player controls
    private lateinit var skipIndicator: TextView
    private lateinit var speedIndicator: TextView
    private lateinit var infoOverlay: LinearLayout
    private lateinit var infoTitle: TextView
    private lateinit var infoPosition: TextView
    private lateinit var infoQuality: TextView
    private lateinit var infoSpeed: TextView
    private lateinit var infoBuffer: TextView

    // Playback speed control
    private val speedOptions = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
    private var currentSpeedIndex = 2 // Default 1.0x

    // Skip control - L2 FIX: Use constants from companion object
    private var isLongPressing = false
    private var longPressHandler = Handler(Looper.getMainLooper())

    // Info overlay auto-hide
    private var infoOverlayHandler = Handler(Looper.getMainLooper())

    // Flag to defer video URL fetching until activity is started
    private var pendingVideoFetch = false
    // Video caching is managed by FarsilandApp.videoCache singleton

    // YouTube WebView player (for fallback when no YouTube app)
    private var youTubeWebView: android.webkit.WebView? = null

    // M6 FIX: Network monitoring during playback
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    // EXTERNAL AUDIT FIX C4.3: Track registration status to prevent double-registration
    private var isNetworkCallbackRegistered = false
    private val connectivityManager by lazy {
        getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    }

    @javax.inject.Inject lateinit var repository: ContentRepository
    @javax.inject.Inject lateinit var watchlistRepo: WatchlistRepository
    @javax.inject.Inject lateinit var playbackRepo: PlaybackRepository
    private val videoScraper = VideoUrlScraper

    // Content info from intent
    private lateinit var contentType: String // "movie" or "episode"
    private var contentId: Int = 0
    private lateinit var contentTitle: String
    private lateinit var contentUrl: String // Farsiland.com page URL
    private var contentPosterUrl: String? = null // Poster/thumbnail URL
    private var seriesId: Int = 0 // For episodes
    private var seasonNumber: Int = 0 // For episodes
    private var episodeNumber: Int = 0 // For episodes
    private var isImvboxContent: Boolean = false // Skip YouTube embed for IMVBox (Error 153)

    // Quality selection
    private var availableQualities: List<VideoUrl> = emptyList()
    private var currentQualityIndex: Int = 0
    private val prefs by lazy {
        getSharedPreferences("VideoPlayerPrefs", Context.MODE_PRIVATE)
    }

    // Chromecast support
    @javax.inject.Inject lateinit var castManager: CastManager
    private var castButton: MediaRouteButton? = null
    private var isCastingActive = false

    // CDN mirror fallback - track which URLs have been tried
    private var currentVideoUrl: String = ""
    private var triedUrls: MutableSet<String> = mutableSetOf()

    // Playback position tracking (C1: Removed FarsilandDatabase, now using AppDatabase via PlaybackRepository)
    private val positionSaveLock = Any()
    private val positionHandler = Handler(Looper.getMainLooper())
    private val positionSaveRunnable = object : Runnable {
        override fun run() {
            saveCurrentPosition()
            positionHandler.postDelayed(this, POSITION_SAVE_INTERVAL)
        }
    }

    // P0 FIX: Issue #1 - Save playback state for restoration after onStop()
    // Prevents crash when user presses Home button and returns
    private data class PlaybackState(
        val position: Long,
        val videoUrl: String,
        val wasPlaying: Boolean
    )
    private var savedPlaybackState: PlaybackState? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            setContentView(R.layout.activity_video_player)
            // Repository, watchlistRepo, playbackRepo are now Hilt-injected

            // H4 FIX: Fail-fast validation of required intent extras
            // Extract all intent extras first
            contentType = intent.getStringExtra(IntentExtras.CONTENT_TYPE) ?: IntentExtras.ContentType.MOVIE
            contentId = intent.getIntExtra(IntentExtras.CONTENT_ID, 0)
            contentTitle = intent.getStringExtra(IntentExtras.CONTENT_TITLE) ?: "Unknown"
            contentUrl = intent.getStringExtra(IntentExtras.CONTENT_URL) ?: ""
            contentPosterUrl = intent.getStringExtra(IntentExtras.CONTENT_POSTER_URL)

            // For episodes, get additional info
            if (contentType == IntentExtras.ContentType.EPISODE) {
                seriesId = intent.getIntExtra(IntentExtras.SERIES_ID, 0)
                seasonNumber = intent.getIntExtra(IntentExtras.EPISODE_SEASON, 0)
                episodeNumber = intent.getIntExtra(IntentExtras.EPISODE_NUMBER, 0)
            }

            // Validate required data - fail-fast to prevent invalid state
            if (contentUrl.isEmpty()) {
                Log.e(TAG, "Error: No content URL provided")
                // AUDIT FIX #30: Use localized string resource
                Toast.makeText(this, getString(R.string.error_no_content_url), Toast.LENGTH_LONG).show()
                finish()
                return
            }

            // IMVBox content: Pre-extract YouTube ID to skip intro video entirely
            // YouTube embeds only work on imvbox.com (whitelisted by video owners)
            // Direct youtube.com/embed fails with Error 153
            if (contentUrl.contains("imvbox.com")) {
                Log.d(TAG, "IMVBox content detected - extracting YouTube ID to skip intro")
                isImvboxContent = true
                val playUrl = if (contentUrl.endsWith("/play")) contentUrl else "$contentUrl/play"

                // Pre-extract YouTube ID in background to skip ~45 second intro
                lifecycleScope.launch {
                    try {
                        Log.d(TAG, "Extracting video source from IMVBox...")
                        val extractor = IMVBoxVideoExtractor(this@VideoPlayerActivity)
                        val videoSource = extractor.extractVideoSource(playUrl)

                        val youtubeId: String? = when (videoSource) {
                            is IMVBoxVideoExtractor.VideoSource.YouTube -> {
                                Log.d(TAG, "Extracted YouTube ID: ${videoSource.videoId}")
                                videoSource.videoId
                            }
                            is IMVBoxVideoExtractor.VideoSource.HLS -> {
                                Log.d(TAG, "Got HLS stream, no YouTube ID to pre-inject")
                                null
                            }
                            is IMVBoxVideoExtractor.VideoSource.Error -> {
                                Log.w(TAG, "Extraction failed: ${videoSource.message}")
                                null
                            }
                        }

                        // Launch WebView player with optional pre-extracted YouTube ID
                        val intent = IMVBoxWebPlayerActivity.createIntent(
                            context = this@VideoPlayerActivity,
                            playUrl = playUrl,
                            title = contentTitle,
                            youtubeId = youtubeId  // Skip intro if YouTube ID available
                        )
                        startActivity(intent)
                        finish()
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to extract YouTube ID, falling back", e)
                        // Fallback: launch without pre-extracted ID (will use skip button)
                        val intent = IMVBoxWebPlayerActivity.createIntent(
                            context = this@VideoPlayerActivity,
                            playUrl = playUrl,
                            title = contentTitle
                        )
                        startActivity(intent)
                        finish()
                    }
                }
                return
            }

            if (contentId == 0) {
                Log.e(TAG, "Error: Invalid content ID (0)")
                // AUDIT FIX #30: Use localized string resource
                Toast.makeText(this, getString(R.string.error_invalid_content_id), Toast.LENGTH_LONG).show()
                finish()
                return
            }

            if (contentType != IntentExtras.ContentType.MOVIE && contentType != IntentExtras.ContentType.EPISODE) {
                Log.e(TAG, "Error: Invalid content type: $contentType")
                // AUDIT FIX #30: Use localized string resource
                Toast.makeText(this, getString(R.string.error_invalid_content_type), Toast.LENGTH_LONG).show()
                finish()
                return
            }

            // H4 FIX: Validate episode-specific metadata
            // Prevents crashes when accessing invalid episode metadata
            if (contentType == "episode") {
                // NOTE: seriesId is optional - episodes may not have series metadata in database
                // Only validate season/episode numbers which are essential for playback tracking
                if (seasonNumber == 0 || episodeNumber == 0) {
                    Log.e(TAG, "Error: Invalid season ($seasonNumber) or episode ($episodeNumber) number")
                    // AUDIT FIX #30: Use localized string resource
                    Toast.makeText(this, getString(R.string.error_invalid_episode_metadata), Toast.LENGTH_LONG).show()
                    finish()
                    return
                }
                // Log warning if series ID missing (helpful for debugging) but don't block playback
                if (seriesId == 0) {
                    Log.w(TAG, "Warning: Episode has no series ID - playback will work but series context unavailable")
                }
            }

            // Find views
            playerView = findViewById(R.id.player_view)
            loadingIndicator = findViewById(R.id.loading_indicator)
            errorText = findViewById(R.id.error_text)

            // Enhanced player control views
            skipIndicator = findViewById(R.id.skip_indicator)
            speedIndicator = findViewById(R.id.speed_indicator)
            infoOverlay = findViewById(R.id.info_overlay)
            infoTitle = findViewById(R.id.info_title)
            infoPosition = findViewById(R.id.info_position)
            infoQuality = findViewById(R.id.info_quality)
            infoSpeed = findViewById(R.id.info_speed)
            infoBuffer = findViewById(R.id.info_buffer)

            // Disable automatic controller show - we'll control it manually
            // This allows Left/Right to skip without showing controls
            playerView.controllerAutoShow = false
            playerView.controllerHideOnTouch = true

            // Setup quality button in control bar
            setupQualityButton()

            // Setup back button in player controls (for phone users)
            setupBackButton()

            // Setup back press handler
            onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    // EXTERNAL AUDIT FIX C2.1: Use forceSync=true to prevent race condition
                    // Without this, lifecycleScope.launch could fire while finish() is executing
                    saveCurrentPosition(forceSync = true)
                    // Navigate back to previous activity
                    finish()
                }
            })

            Log.d(TAG, "Playing $contentType: $contentTitle")
            Log.d(TAG, "Farsiland URL: $contentUrl")
            Log.d(TAG, "Content ID: $contentId")

            // Initialize ExoPlayer
            initializePlayer()

            // Initialize Chromecast support
            initializeCast()

            // M6 FIX: Register network callback to monitor connectivity during playback
            registerNetworkCallback()

            // Check if quality was already selected
            val selectedVideoUrl = intent.getStringExtra(IntentExtras.SELECTED_VIDEO_URL)
            val selectedQuality = intent.getStringExtra(IntentExtras.SELECTED_VIDEO_QUALITY)

            if (selectedVideoUrl != null && selectedQuality != null) {
                // Quality already selected - play directly
                Log.d(TAG, "Using pre-selected quality: $selectedQuality")
                Log.d(TAG, "Video URL: $selectedVideoUrl")
                currentVideoUrl = selectedVideoUrl
                availableQualities = listOf(
                    com.example.farsilandtv.data.models.VideoUrl(
                        url = selectedVideoUrl,
                        quality = selectedQuality,
                        fileSizeMb = null,
                        mirror = null
                    )
                )
                currentQualityIndex = 0
                loadSavedPosition(selectedVideoUrl)
            } else {
                // No quality selected - fetch and auto-select
                fetchVideoUrlsAndPlay()
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error in onCreate", e)
            Toast.makeText(this, "Failed to initialize player: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    /**
     * EXTERNAL AUDIT FIX C2.2: Handle new intents when Activity is already running (singleTop)
     *
     * Problem: With launchMode="singleTop", clicking another video while player is open
     *          delivers intent to existing instance via onNewIntent(), not onCreate()
     * Result: New video request is ignored, player continues playing old video
     * Solution: Update intent and re-initialize player with new content
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        Log.d(TAG, "onNewIntent() - New video request received while player is open")

        // Update the activity's intent so getIntent() returns the new one
        setIntent(intent)

        // Save current position before switching videos
        saveCurrentPosition(forceSync = true)

        // Stop current playback
        player?.stop()

        // Extract new content data from intent
        contentType = intent.getStringExtra(IntentExtras.CONTENT_TYPE) ?: IntentExtras.ContentType.MOVIE
        contentId = intent.getIntExtra(IntentExtras.CONTENT_ID, 0)
        contentTitle = intent.getStringExtra(IntentExtras.CONTENT_TITLE) ?: "Unknown"
        contentUrl = intent.getStringExtra(IntentExtras.CONTENT_URL) ?: ""
        contentPosterUrl = intent.getStringExtra(IntentExtras.CONTENT_POSTER_URL)

        if (contentType == IntentExtras.ContentType.EPISODE) {
            seriesId = intent.getIntExtra(IntentExtras.SERIES_ID, 0)
            seasonNumber = intent.getIntExtra(IntentExtras.EPISODE_SEASON, 0)
            episodeNumber = intent.getIntExtra(IntentExtras.EPISODE_NUMBER, 0)
        }

        // Validate required data
        if (contentUrl.isEmpty() || contentId == 0) {
            Toast.makeText(this, "Error: Invalid video request", Toast.LENGTH_LONG).show()
            Log.e(TAG, "onNewIntent: Missing required data - contentUrl=$contentUrl, contentId=$contentId")
            return
        }

        // Reset tried URLs for new video
        triedUrls.clear()

        Log.d(TAG, "onNewIntent: Switching to new video - $contentType: $contentTitle")

        // Check if quality was pre-selected
        val selectedVideoUrl = intent.getStringExtra(IntentExtras.SELECTED_VIDEO_URL)
        val selectedQuality = intent.getStringExtra(IntentExtras.SELECTED_VIDEO_QUALITY)

        if (selectedVideoUrl != null && selectedQuality != null) {
            currentVideoUrl = selectedVideoUrl
            availableQualities = listOf(
                VideoUrl(
                    url = selectedVideoUrl,
                    quality = selectedQuality,
                    fileSizeMb = null,
                    mirror = null
                )
            )
            currentQualityIndex = 0
            loadSavedPosition(selectedVideoUrl)
        } else {
            // Fetch new video URLs and start playback
            fetchVideoUrlsAndPlay()
        }
    }

    private fun setupQualityButton() {
        // Find the quality button from the custom controls
        playerView.findViewById<View>(R.id.quality_button)?.setOnClickListener {
            showQualitySelector()
        }
    }

    /**
     * Setup back button for phone users
     * Allows easy exit from fullscreen video player
     */
    private fun setupBackButton() {
        playerView.findViewById<View>(R.id.exo_back)?.setOnClickListener {
            // Save position and finish activity
            saveCurrentPosition(forceSync = true)
            finish()
        }

        // Set title text in player controls
        playerView.findViewById<TextView>(R.id.exo_title)?.text = contentTitle
    }

    /**
     * Initialize Chromecast support
     * Sets up MediaRouteButton and handles cast session callbacks
     */
    private fun initializeCast() {
        try {
            // CastManager is injected via Hilt @Inject
            castManager.initialize()

            // Setup cast button in player controls
            castButton = playerView.findViewById(R.id.cast_button)
            castButton?.let { button ->
                CastButtonFactory.setUpMediaRouteButton(this, button)
                button.visibility = View.VISIBLE
                Log.d(TAG, "Cast button initialized")
            }

            // Listen for cast session events
            castManager.onCastSessionStarted = { session ->
                Log.d(TAG, "Cast session started - transferring playback to Chromecast")
                handleCastSessionStarted(session)
            }

            castManager.onCastSessionEnded = {
                Log.d(TAG, "Cast session ended - returning to local playback")
                handleCastSessionEnded()
            }

            castManager.onCastAvailabilityChanged = { available ->
                Log.d(TAG, "Cast availability changed: $available")
                runOnUiThread {
                    castButton?.visibility = if (available) View.VISIBLE else View.GONE
                }
            }

            Log.i(TAG, "Chromecast support initialized")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Chromecast: ${e.message}", e)
            // Non-fatal - app works fine without cast
        }
    }

    /**
     * Handle cast session started - transfer playback to Chromecast
     */
    private fun handleCastSessionStarted(session: CastSession) {
        if (isCastingActive) return // Already casting

        // Save current playback state
        val currentPosition = player?.currentPosition ?: 0L
        val wasPlaying = player?.isPlaying ?: false

        // Pause local playback
        player?.pause()

        // Get current video info
        val videoUrl = currentVideoUrl
        val quality = availableQualities.getOrNull(currentQualityIndex)?.quality ?: ""
        val title = if (contentType == "episode") {
            "$contentTitle (S${seasonNumber}E${episodeNumber})"
        } else {
            contentTitle
        }

        if (videoUrl.isNotEmpty()) {
            // Start casting
            castManager.castMedia(
                videoUrl = videoUrl,
                title = title,
                posterUrl = contentPosterUrl,
                position = currentPosition
            )

            isCastingActive = true

            runOnUiThread {
                Toast.makeText(this, "Casting to ${session.castDevice?.friendlyName ?: "Chromecast"}", Toast.LENGTH_SHORT).show()
            }

            Log.i(TAG, "Started casting: $title at position ${currentPosition}ms")
        }
    }

    /**
     * Handle cast session ended - return to local playback
     */
    private fun handleCastSessionEnded() {
        if (!isCastingActive) return

        // Get position from CastPlayer before it becomes unavailable
        val castPosition = castManager.getSavedPosition()
        val wasPlaying = castManager.wasPlayingBeforeHandoff()

        isCastingActive = false

        runOnUiThread {
            // Resume local playback at saved position
            if (currentVideoUrl.isNotEmpty() && castPosition > 0) {
                val mediaItem = MediaItem.fromUri(currentVideoUrl)
                player?.apply {
                    setMediaItem(mediaItem)
                    seekTo(castPosition)
                    prepare()
                    playWhenReady = wasPlaying
                }

                Toast.makeText(this, "Playback returned to device", Toast.LENGTH_SHORT).show()
                Log.i(TAG, "Local playback resumed at position ${castPosition}ms")
            }
        }
    }

    // H3 FIX: Lock object for network callback registration to prevent race condition
    private val networkCallbackLock = Any()

    /**
     * M6 FIX: Register network callback to monitor connectivity during playback
     * Notifies user when network drops, preventing silent infinite buffering
     * EXTERNAL AUDIT FIX C4.3: Check registration flag to prevent double-registration
     * H3 FIX: Added synchronization to prevent race condition
     */
    private fun registerNetworkCallback() {
        synchronized(networkCallbackLock) {
            // EXTERNAL AUDIT FIX C4.3: Prevent double-registration memory leak
            if (isNetworkCallbackRegistered) {
                Log.d(TAG, "Network callback already registered, skipping")
                return
            }

            networkCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onLost(network: Network) {
                    // Network disconnected during playback
                    runOnUiThread {
                        player?.pause()
                        Toast.makeText(
                            this@VideoPlayerActivity,
                            R.string.network_connection_lost,
                            Toast.LENGTH_LONG
                        ).show()
                        Log.w(TAG, "Network connection lost during playback")
                    }
                }

                override fun onAvailable(network: Network) {
                    // Network reconnected - no toast needed, just log
                    Log.d(TAG, "Network connection restored")
                }
            }

            try {
                connectivityManager.registerDefaultNetworkCallback(networkCallback!!)
                isNetworkCallbackRegistered = true  // EXTERNAL AUDIT FIX C4.3: Mark as registered
                Log.d(TAG, "Network callback registered successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to register network callback", e)
                isNetworkCallbackRegistered = false  // EXTERNAL AUDIT FIX C4.3: Mark as failed
            }
        }
    }

    private fun initializePlayer() {
        // Use the full episode/movie page URL as Referer header
        // CDN servers validate that requests come from the actual content page
        val referer = contentUrl

        Log.d(TAG, "Setting Referer header: $referer")

        // Setup HTTP data source with appropriate Referer header
        // Use OkHttpDataSource with custom SSL handling for IMVBox streaming
        // IMVBox's streaming server has incomplete certificate chain (server misconfiguration)
        val httpDataSourceFactory = createDataSourceFactory(referer)

        // Use FarsilandApp's singleton video cache (100MB)
        // Falls back to direct HTTP if cache not available
        val appCache = FarsilandApp.videoCache
        val dataSourceFactory = if (appCache != null) {
            Log.d(TAG, "Video caching enabled - using FarsilandApp.videoCache")
            CacheDataSource.Factory()
                .setCache(appCache)
                .setUpstreamDataSourceFactory(httpDataSourceFactory)
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        } else {
            Log.d(TAG, "Video cache not available - using direct HTTP")
            httpDataSourceFactory
        }

        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)

        // M5 FIX: Shield TV optimized buffer configuration (2GB RAM device)
        // - 20s min buffer: Ensures smooth playback without stuttering
        // - 40s max buffer: Acceptable memory usage (~50-70MB) for 2GB RAM device
        // - 5s playback buffer: Better tolerance for WiFi fluctuations (up from 2.5s)
        // - 10s rebuffer: TV-appropriate delay, less aggressive than mobile (up from 5s)
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                20000,  // Min buffer: 20s (smooth playback)
                40000,  // Max buffer: 40s (Shield TV optimized)
                5000,   // Playback buffer: 5s (WiFi resilient)
                10000   // Rebuffer: 10s (TV-appropriate)
            )
            .setTargetBufferBytes(10 * 1024 * 1024) // 10MB target
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        // Adaptive track selector: start with SD, upgrade to HD based on bandwidth
        // ENHANCEMENT: Enable tunneling for hardware DSP offload on Shield TV
        val trackSelector = DefaultTrackSelector(this).apply {
            parameters = parameters
                .buildUpon()
                .setMaxVideoSizeSd() // Start with SD, upgrade to HD
                .setPreferredVideoMimeTypes("video/mp4", "video/avc")
                .setTunnelingEnabled(true) // Hardware DSP offload for better AV sync
                .build()
        }

        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .setTrackSelector(trackSelector)
            .setBandwidthMeter(DefaultBandwidthMeter.getSingletonInstance(this))
            .build().also { exoPlayer ->
            playerView.player = exoPlayer

            // Restore saved playback speed
            val prefs = getSharedPreferences("player_prefs", Context.MODE_PRIVATE)
            currentSpeedIndex = prefs.getInt(PREF_SPEED_INDEX, 2) // Default 1.0x (index 2)
            val savedSpeed = speedOptions[currentSpeedIndex]
            exoPlayer.playbackParameters = PlaybackParameters(savedSpeed)
            if (savedSpeed != 1.0f) {
                speedIndicator.text = "${savedSpeed}x"
                speedIndicator.visibility = View.VISIBLE
            }

            // Add player listeners
            exoPlayer.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    when (playbackState) {
                        Player.STATE_BUFFERING -> {
                            loadingIndicator.visibility = View.VISIBLE
                        }
                        Player.STATE_READY -> {
                            loadingIndicator.visibility = View.GONE
                            errorText.visibility = View.GONE
                        }
                        Player.STATE_ENDED -> {
                            Log.d(TAG, "Playback ended")
                            // TODO: Auto-play next episode
                        }
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    Log.e(TAG, "Playback error", error)

                    // EXTERNAL AUDIT FIX C2.5: Detect HTTP errors and clear cache to prevent caching error responses
                    val cause = error.cause
                    if (cause is HttpDataSource.InvalidResponseCodeException) {
                        val statusCode = cause.responseCode
                        Log.e(TAG, "HTTP error detected: $statusCode for URL: $currentVideoUrl")

                        // Clear cache for this video to prevent repeated errors from cached bad response
                        // Clear cached data for this URL on HTTP errors
                        FarsilandApp.videoCache?.let { appCache ->
                            try {
                                // Remove all cache keys - ExoPlayer will rebuild cache on next load
                                val keys = appCache.keys.toList() // Copy to avoid ConcurrentModification
                                for (key in keys) {
                                    appCache.removeResource(key)
                                }
                                Log.d(TAG, "Cache cleared after HTTP error $statusCode")
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed to clear cache after HTTP error", e)
                            }
                        }

                        // Try next available mirror URL on 403/404/other HTTP errors
                        if (tryNextMirrorUrl()) {
                            return // Successfully started trying another mirror
                        }

                        // No more mirrors available
                        showError("HTTP error $statusCode - No working video sources found")
                        return
                    }

                    // Try next mirror for other playback errors too
                    if (tryNextMirrorUrl()) {
                        return
                    }

                    showError("Playback error: ${error.message}")
                }

                // AFR: Detect video frame rate and switch display mode
                override fun onTracksChanged(tracks: Tracks) {
                    super.onTracksChanged(tracks)

                    // Extract video frame rate from selected video track
                    val videoFormat = tracks.groups
                        .filter { it.type == C.TRACK_TYPE_VIDEO && it.isSelected }
                        .firstOrNull()
                        ?.getTrackFormat(0)

                    val frameRate = videoFormat?.frameRate ?: Format.NO_VALUE.toFloat()

                    if (frameRate != Format.NO_VALUE.toFloat() && frameRate > 0) {
                        Log.d(TAG, "Video frame rate detected: ${frameRate}fps")

                        val afrEnabled = AutoFrameRateHelper.enableAFR(
                            this@VideoPlayerActivity,
                            frameRate
                        )

                        if (afrEnabled) {
                            // Show brief toast indicating display mode change
                            Toast.makeText(
                                this@VideoPlayerActivity,
                                "Display: ${String.format("%.0f", frameRate)}fps",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    } else {
                        Log.w(TAG, "Frame rate not available in video metadata")
                    }
                }
            })
        }
    }

    /**
     * Check if network connectivity is available
     */
    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /**
     * EXTERNAL AUDIT FIX C3: Check if activity is in valid lifecycle state before touching views
     * Prevents WindowManager$BadTokenException and view leaks
     */
    private fun isActivityAlive(): Boolean {
        return lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
    }

    private fun fetchVideoUrlsAndPlay(skipLifecycleCheck: Boolean = false) {
        // EXTERNAL AUDIT FIX C3: Early lifecycle check
        // If activity not started yet, defer the fetch until onStart()
        // Skip check when called from onStart() (we know it's safe then)
        if (!skipLifecycleCheck && !isActivityAlive()) {
            Log.w(TAG, "fetchVideoUrlsAndPlay called but activity is not in STARTED state, deferring until onStart()")
            pendingVideoFetch = true
            return
        }

        // Clear pending flag since we're executing now
        pendingVideoFetch = false

        loadingIndicator.visibility = View.VISIBLE
        errorText.visibility = View.GONE

        // Check network connectivity before scraping
        if (!isNetworkAvailable()) {
            Toast.makeText(this, "No network connection. Please check your internet.", Toast.LENGTH_LONG).show()
            showError("No network connection available")
            finish()
            return
        }

        lifecycleScope.launch {
            try {
                Log.d(TAG, "========================================")
                Log.d(TAG, "Starting video URL fetch")
                Log.d(TAG, "Content Type: $contentType")
                Log.d(TAG, "Content ID: $contentId")
                Log.d(TAG, "Content Title: $contentTitle")
                Log.d(TAG, "Farsiland URL: $contentUrl")
                Log.d(TAG, "========================================")

                // EXTERNAL AUDIT FIX C3: Lifecycle check before showing Toast
                // Skip this check when called from onStart() (we know it's safe then)
                if (!skipLifecycleCheck && !isActivityAlive()) {
                    Log.w(TAG, "Activity destroyed during scraping setup, aborting")
                    return@launch
                }

                // Fetch video URLs from the page
                val scraperResult = videoScraper.extractVideoUrls(contentUrl)

                // Handle different scraper result types
                val videoUrls = when (scraperResult) {
                    is ScraperResult.Success -> {
                        Log.d(TAG, "SUCCESS: Found ${scraperResult.data.size} video URLs")
                        scraperResult.data.forEachIndexed { index, videoUrl ->
                            Log.d(TAG, "  [$index] Quality: ${videoUrl.quality}")
                            Log.d(TAG, "       URL: ${videoUrl.url}")
                            Log.d(TAG, "       Mirror: ${videoUrl.mirror}")
                        }
                        scraperResult.data
                    }
                    is ScraperResult.NetworkError -> {
                        Log.e(TAG, "Network error while fetching video URLs: ${scraperResult.message}")
                        showError("Network error. Please check your connection and try again.\n\nDetails: ${scraperResult.message}")
                        return@launch
                    }
                    is ScraperResult.ParseError -> {
                        Log.e(TAG, "Parse error while extracting video URLs: ${scraperResult.message}")
                        showError("Failed to parse video data. The website may have changed.\n\nDetails: ${scraperResult.message}")
                        return@launch
                    }
                    is ScraperResult.NoDataFound -> {
                        Log.e(TAG, "No video URLs found: ${scraperResult.message}")
                        showError(getString(R.string.no_video_urls_found, scraperResult.message))
                        return@launch
                    }
                }

                // Store all available qualities
                availableQualities = videoUrls

                // Get user's preferred quality from preferences
                val preferredQuality = prefs.getString(PREF_QUALITY, null)
                Log.d(TAG, "User preferred quality: ${preferredQuality ?: "auto (highest)"}")

                // Try to find preferred quality, fallback to highest available
                currentQualityIndex = if (preferredQuality != null) {
                    videoUrls.indexOfFirst {
                        it.quality.contains(preferredQuality, ignoreCase = true)
                    }.takeIf { it >= 0 } ?: findHighestQuality(videoUrls)
                } else {
                    findHighestQuality(videoUrls)
                }

                val selectedVideo = videoUrls[currentQualityIndex]

                // NOTE: Quality is only saved when user manually changes it via quality selector
                // NOT auto-saved to prevent feedback loop where lower quality becomes permanent

                Log.d(TAG, "========================================")
                Log.d(TAG, "Selected video for playback:")
                Log.d(TAG, "  Quality: ${selectedVideo.quality}")
                Log.d(TAG, "  URL: ${selectedVideo.url}")
                Log.d(TAG, "========================================")

                // EXTERNAL AUDIT FIX C3: Lifecycle check before showing Toast and starting playback
                // Skip this check when called from onStart() (we know it's safe then)
                if (!skipLifecycleCheck && !isActivityAlive()) {
                    Log.w(TAG, "Activity destroyed after scraping, aborting playback")
                    return@launch
                }

                // Load saved position if available
                loadSavedPosition(selectedVideo.url)

            } catch (e: Exception) {
                Log.e(TAG, "========================================")
                Log.e(TAG, "EXCEPTION in fetchVideoUrlsAndPlay", e)
                Log.e(TAG, "Exception type: ${e.javaClass.simpleName}")
                Log.e(TAG, "Exception message: ${e.message}")
                Log.e(TAG, "Stack trace:")
                e.printStackTrace()
                Log.e(TAG, "========================================")

                showError("Failed to load video: ${e.message}\n\nCheck logcat for details.")
            }
        }
    }

    private fun playVideo(videoUrl: String) {
        try {
            Log.d(TAG, "========================================")
            Log.d(TAG, "playVideo() called")
            Log.d(TAG, "Video URL: $videoUrl")
            Log.d(TAG, "========================================")

            currentVideoUrl = videoUrl

            // Validate URL format
            if (!videoUrl.startsWith("http://") && !videoUrl.startsWith("https://")) {
                throw IllegalArgumentException("Invalid video URL format: $videoUrl")
            }

            // Check if this is a YouTube URL - ExoPlayer can't play these directly
            if (isYouTubeUrl(videoUrl)) {
                Log.d(TAG, "YouTube URL detected - launching YouTube player")
                // For IMVBox content, show login prompt since HLS requires authentication
                if (isImvboxContent) {
                    Toast.makeText(
                        this,
                        "Full movie requires IMVBox login. Go to Settings → IMVBox Account",
                        Toast.LENGTH_LONG
                    ).show()
                }
                launchYouTubePlayer(videoUrl)
                return
            }

            Log.d(TAG, "Creating MediaItem from URI...")
            val mediaItem = MediaItem.fromUri(videoUrl)

            Log.d(TAG, "Setting media item to player...")
            player?.apply {
                setMediaItem(mediaItem)
                Log.d(TAG, "Preparing player...")
                prepare()
                Log.d(TAG, "Setting playWhenReady=true...")
                playWhenReady = true
            }

            loadingIndicator.visibility = View.GONE
            Log.d(TAG, "Player initialized successfully")

        } catch (e: Exception) {
            Log.e(TAG, "========================================")
            Log.e(TAG, "EXCEPTION in playVideo", e)
            Log.e(TAG, "Video URL: $videoUrl")
            Log.e(TAG, "Exception type: ${e.javaClass.simpleName}")
            Log.e(TAG, "Exception message: ${e.message}")
            e.printStackTrace()
            Log.e(TAG, "========================================")
            showError("Error playing video: ${e.message}")
        }
    }

    /**
     * Create data source factory with custom SSL handling for IMVBox streaming.
     *
     * IMVBox's streaming.imvbox.com server has an incomplete certificate chain
     * (missing intermediate CA certificates). This is a server misconfiguration
     * on their side, but we handle it here for personal use app.
     *
     * SECURITY NOTE: This relaxed SSL is ONLY applied for personal use behind
     * a firewalled network (as documented in CLAUDE.md). For public apps,
     * the server should be fixed to send the full certificate chain.
     */
    private fun createDataSourceFactory(referer: String): OkHttpDataSource.Factory {
        // Create OkHttpClient with relaxed SSL for streaming.imvbox.com
        val okHttpClient = try {
            // Trust manager that accepts all certificates
            // This is safe for personal use app behind firewalled network
            val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
                override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            })

            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(null, trustAllCerts, java.security.SecureRandom())

            OkHttpClient.Builder()
                .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
                .hostnameVerifier { _, _ -> true }  // Accept all hostnames
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create custom SSL OkHttpClient, using default: ${e.message}")
            OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build()
        }

        Log.d(TAG, "Using OkHttpDataSource with custom SSL handling for IMVBox streaming")

        return OkHttpDataSource.Factory(okHttpClient)
            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .setDefaultRequestProperties(mapOf("Referer" to referer))
    }

    /**
     * Check if URL is a YouTube video URL
     */
    private fun isYouTubeUrl(url: String): Boolean {
        return url.contains("youtube.com/watch") ||
               url.contains("youtu.be/") ||
               url.contains("youtube.com/embed/")
    }

    /**
     * Extract YouTube video ID from various URL formats
     */
    private fun extractYouTubeVideoId(url: String): String? {
        // youtube.com/watch?v=VIDEO_ID
        val watchPattern = Regex("""youtube\.com/watch\?v=([a-zA-Z0-9_-]{11})""")
        watchPattern.find(url)?.let { return it.groupValues[1] }

        // youtu.be/VIDEO_ID
        val shortPattern = Regex("""youtu\.be/([a-zA-Z0-9_-]{11})""")
        shortPattern.find(url)?.let { return it.groupValues[1] }

        // youtube.com/embed/VIDEO_ID
        val embedPattern = Regex("""youtube\.com/embed/([a-zA-Z0-9_-]{11})""")
        embedPattern.find(url)?.let { return it.groupValues[1] }

        return null
    }

    /**
     * Launch YouTube player for video playback
     * Uses YouTubePlayerActivity with built-in Chromecast support
     *
     * For IMVBox content, skipEmbed=true bypasses the embed attempt (Error 153)
     * and opens YouTube app directly.
     */
    private fun launchYouTubePlayer(url: String) {
        val videoId = extractYouTubeVideoId(url)

        if (videoId == null) {
            Log.e(TAG, "Could not extract YouTube video ID from: $url")
            showError("Invalid YouTube URL")
            return
        }

        Log.d(TAG, "Launching YouTubePlayerActivity for video ID: $videoId, skipEmbed: $isImvboxContent")

        try {
            // Use our custom YouTubePlayerActivity with Chromecast support
            // For IMVBox content, skip embed attempt and open YouTube app directly
            val intent = YouTubePlayerActivity.createIntent(
                context = this,
                videoId = videoId,
                title = contentTitle,
                startPositionSeconds = 0f,
                skipEmbed = isImvboxContent
            )
            startActivity(intent)
            finish() // Close VideoPlayerActivity since we're switching to YouTube player

        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch YouTubePlayerActivity: ${e.message}", e)
            showError("Could not play YouTube video: ${e.message}")
        }
    }

    /**
     * Play YouTube video in an embedded WebView
     * This is used as fallback when no YouTube app is installed
     * Uses YouTube IFrame API for a clean, fullscreen player experience
     */
    @android.annotation.SuppressLint("SetJavaScriptEnabled")
    private fun playYouTubeInWebView(videoId: String) {
        Log.d(TAG, "Playing YouTube video in WebView: $videoId")

        // Hide ExoPlayer, show loading
        playerView.visibility = View.GONE
        loadingIndicator.visibility = View.VISIBLE

        // Keep screen on during YouTube playback
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Create and show WebView
        val webView = android.webkit.WebView(this).apply {
            setBackgroundColor(android.graphics.Color.BLACK)
            layoutParams = android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT
            )

            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                mediaPlaybackRequiresUserGesture = false
                loadWithOverviewMode = true
                useWideViewPort = true
                builtInZoomControls = false
                displayZoomControls = false
                // Desktop Chrome for better YouTube player experience
                userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
            }

            webChromeClient = object : android.webkit.WebChromeClient() {
                override fun onProgressChanged(view: android.webkit.WebView?, newProgress: Int) {
                    // Hide loading when page is mostly loaded
                    if (newProgress > 80) {
                        loadingIndicator.visibility = View.GONE
                    }
                }
            }

            webViewClient = object : android.webkit.WebViewClient() {
                override fun onPageFinished(view: android.webkit.WebView?, url: String?) {
                    Log.d(TAG, "YouTube WebView loaded: $url")
                    loadingIndicator.visibility = View.GONE
                    // Auto-click play button after a short delay
                    view?.postDelayed({
                        view.evaluateJavascript("""
                            (function() {
                                var playBtn = document.querySelector('.ytp-large-play-button');
                                if (playBtn) playBtn.click();
                            })();
                        """.trimIndent(), null)
                    }, 500)
                }

                override fun onReceivedError(
                    view: android.webkit.WebView?,
                    request: android.webkit.WebResourceRequest?,
                    error: android.webkit.WebResourceError?
                ) {
                    // Only handle main frame errors
                    if (request?.isForMainFrame == true) {
                        Log.e(TAG, "YouTube WebView error: ${error?.description}")
                        loadingIndicator.visibility = View.GONE
                        showError("Failed to load video. Check your internet connection.")
                    }
                }
            }
        }

        // Store reference for cleanup
        youTubeWebView = webView

        // Add WebView to root layout
        val rootView = findViewById<android.widget.FrameLayout>(android.R.id.content)
        rootView.addView(webView)

        // Create a clean fullscreen YouTube player using nocookie domain
        val embedHtml = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                <style>
                    * { margin: 0; padding: 0; box-sizing: border-box; }
                    html, body {
                        width: 100%;
                        height: 100%;
                        background: #000;
                        overflow: hidden;
                    }
                    .player-container {
                        position: fixed;
                        top: 0;
                        left: 0;
                        width: 100%;
                        height: 100%;
                        display: flex;
                        align-items: center;
                        justify-content: center;
                        background: #000;
                    }
                    iframe {
                        width: 100%;
                        height: 100%;
                        border: none;
                    }
                    .loading {
                        position: absolute;
                        color: #fff;
                        font-family: sans-serif;
                        font-size: 18px;
                    }
                </style>
            </head>
            <body>
                <div class="player-container">
                    <iframe id="ytplayer"
                        src="https://www.youtube-nocookie.com/embed/$videoId?autoplay=1&controls=1&modestbranding=1&rel=0&showinfo=0&iv_load_policy=3&fs=0&cc_load_policy=0&playsinline=1&enablejsapi=1"
                        allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture"
                        allowfullscreen>
                    </iframe>
                </div>
            </body>
            </html>
        """.trimIndent()

        Log.d(TAG, "Loading YouTube embed player for: $videoId")
        webView.loadDataWithBaseURL(
            "https://www.youtube-nocookie.com",
            embedHtml,
            "text/html",
            "UTF-8",
            null
        )

        // Handle back button with modern API
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                cleanupYouTubeWebView()
                finish()
            }
        })

        // Also support D-pad/remote back key
        webView.isFocusableInTouchMode = true
        webView.requestFocus()
        webView.setOnKeyListener { _, keyCode, event ->
            if (keyCode == android.view.KeyEvent.KEYCODE_BACK && event.action == android.view.KeyEvent.ACTION_UP) {
                cleanupYouTubeWebView()
                finish()
                true
            } else {
                false
            }
        }

        Log.d(TAG, "YouTube WebView player started")
    }

    /**
     * Clean up YouTube WebView resources
     */
    private fun cleanupYouTubeWebView() {
        youTubeWebView?.let { webView ->
            Log.d(TAG, "Cleaning up YouTube WebView")
            val rootView = findViewById<android.widget.FrameLayout>(android.R.id.content)
            rootView.removeView(webView)
            webView.stopLoading()
            webView.destroy()
            youTubeWebView = null
        }
        // Remove keep screen on flag
        window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // Only access playerView if it was initialized (activity may finish early for IMVBox redirect)
        if (::playerView.isInitialized) {
            playerView.visibility = View.VISIBLE
        }
    }

    /**
     * Check if an intent can be resolved (app exists to handle it)
     */
    private fun isIntentResolvable(intent: Intent): Boolean {
        return packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) != null
    }

    /**
     * Try next available mirror URL when current one fails
     * Cycles through ALL available video URLs from the scraper
     * Returns true if another URL is being tried, false if no more URLs available
     */
    private fun tryNextMirrorUrl(): Boolean {
        // Mark current URL as tried
        triedUrls.add(currentVideoUrl)

        Log.d(TAG, "Tried URLs so far: $triedUrls")
        Log.d(TAG, "Available URLs: ${availableQualities.map { it.url }}")

        // Find next untried URL from availableQualities list
        val nextUrl = availableQualities.find { !triedUrls.contains(it.url) }

        if (nextUrl != null) {
            Log.d(TAG, "Trying next mirror URL: ${nextUrl.url} (${nextUrl.quality})")
            Toast.makeText(this, "Trying backup server...", Toast.LENGTH_SHORT).show()

            // Get current position before switching
            val currentPosition = player?.currentPosition ?: 0L
            val wasPlaying = player?.isPlaying ?: true  // Default to playing

            // Play from next URL at same position
            currentVideoUrl = nextUrl.url
            val mediaItem = MediaItem.fromUri(nextUrl.url)
            player?.apply {
                setMediaItem(mediaItem)
                seekTo(currentPosition)
                prepare()
                playWhenReady = wasPlaying
            }
            return true
        }

        Log.e(TAG, "No more mirror URLs to try. Tried ${triedUrls.size} URLs.")
        return false
    }

    private fun showError(message: String) {
        loadingIndicator.visibility = View.GONE
        errorText.visibility = View.VISIBLE
        errorText.text = message
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    /**
     * Find index of highest quality video URL
     * Priority: 1080p > 720p > 480p > 360p > unknown
     */
    private fun findHighestQuality(videoUrls: List<VideoUrl>): Int {
        val qualityPriority = mapOf(
            "1080p" to 4,
            "720p" to 3,
            "480p" to 2,
            "360p" to 1
        )

        return videoUrls.indices.maxByOrNull { index ->
            val quality = videoUrls[index].quality.lowercase()
            qualityPriority.entries.find { quality.contains(it.key) }?.value ?: 0
        } ?: 0
    }

    /**
     * Load saved playback position and start playing
     */
    private fun loadSavedPosition(videoUrl: String) {
        lifecycleScope.launch {
            try {
                Log.d(TAG, "Loading saved position for contentId=$contentId, type=$contentType")

                // Check for saved position (C1: Using PlaybackRepository instead of direct database access)
                val savedPosition = playbackRepo.getPosition(contentId, contentType)

                if (savedPosition != null && !savedPosition.isCompleted) {
                    // Resume from saved position
                    Log.d(TAG, "Resuming from position: ${savedPosition.position}ms")
                    playVideoAtPosition(videoUrl, savedPosition.position)
                    Toast.makeText(
                        this@VideoPlayerActivity,
                        "Resuming playback...",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    // Start from beginning
                    Log.d(TAG, "No saved position found, starting from beginning")
                    playVideo(videoUrl)
                }

                // Start position auto-save
                startPositionTracking()

            } catch (e: Exception) {
                Log.e(TAG, "Error loading saved position", e)
                e.printStackTrace()
                // Fallback to playing from beginning if database error
                try {
                    playVideo(videoUrl)
                    startPositionTracking()
                } catch (e2: Exception) {
                    Log.e(TAG, "Error in fallback playback", e2)
                    showError("Failed to start playback: ${e2.message}")
                }
            }
        }
    }

    /**
     * Play video at specific position
     */
    private fun playVideoAtPosition(videoUrl: String, position: Long) {
        try {
            currentVideoUrl = videoUrl

            // Check if this is a YouTube URL - can't resume position in YouTube app
            if (isYouTubeUrl(videoUrl)) {
                Log.d(TAG, "YouTube URL detected - launching YouTube app (position not supported)")
                launchYouTubePlayer(videoUrl)
                return
            }

            val mediaItem = MediaItem.fromUri(videoUrl)
            player?.apply {
                setMediaItem(mediaItem)
                seekTo(position)
                prepare()
                playWhenReady = true
            }

            loadingIndicator.visibility = View.GONE

        } catch (e: Exception) {
            Log.e(TAG, "Error playing video", e)
            showError("Error playing video: ${e.message}")
        }
    }

    /**
     * Start auto-saving playback position every 10 seconds
     */
    private fun startPositionTracking() {
        positionHandler.removeCallbacks(positionSaveRunnable)
        positionHandler.postDelayed(positionSaveRunnable, POSITION_SAVE_INTERVAL)
    }

    /**
     * Save current playback position to database
     *
     * EXTERNAL AUDIT FIX C2: Replaced runBlocking with simple coroutine
     *
     * Previous Issue:
     * - Used runBlocking on Main Thread during Activity destruction
     * - Caused 5+ second ANR on slow storage (old TV boxes)
     * - Android kills app if UI thread blocks >5 seconds
     *
     * New Solution:
     * - Use regular lifecycleScope.launch (no blocking)
     * - Best-effort save (acceptable for playback position)
     * - No Main Thread blocking = No ANR risk
     *
     * Trade-off:
     * - Position save might not complete if Activity destroyed immediately
     * - Acceptable trade-off: Prevents ANR (critical) vs occasional position loss (minor)
     */
    private fun saveCurrentPosition(forceSync: Boolean = false) {
        synchronized(positionSaveLock) {
            try {
                val currentPlayer = player ?: return
                val position = currentPlayer.currentPosition
                val duration = currentPlayer.duration

                if (duration <= 0) return // Skip if duration not available yet

                // EXTERNAL AUDIT FIX C2: Use async save (no blocking)
                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        savePositionToDatabase(position, duration)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error saving position", e)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in saveCurrentPosition", e)
                // Don't crash
            }
        }
    }

    /**
     * Internal method to perform the actual database save
     * Auto-marks as watched at 95% using PlaybackRepository
     */
    private suspend fun savePositionToDatabase(position: Long, duration: Long) {
        try {
            // Calculate watch percentage
            val watchedPercent = (position.toFloat() / duration.toFloat()) * 100

            // C1 FIX: Single write to AppDatabase via PlaybackRepository
            // REMOVED: Dual write to FarsilandDatabase (lines 569-582) to eliminate data consistency risks
            playbackRepo.savePosition(
                contentId = contentId,
                contentType = contentType,
                contentTitle = contentTitle,
                contentUrl = contentUrl,
                position = position,
                duration = duration,
                quality = availableQualities.getOrNull(currentQualityIndex)?.quality ?: "unknown"
            )

            // Save to watchlist database (separate concern from playback position)
            when (contentType) {
                "movie" -> {
                    watchlistRepo.updateMovieProgress(
                        movieId = contentId,
                        position = position,
                        duration = duration,
                        title = contentTitle,
                        farsilandUrl = contentUrl,
                        posterUrl = contentPosterUrl
                    )
                }
                "episode" -> {
                    watchlistRepo.updateEpisodeProgress(
                        episodeId = contentId,
                        position = position,
                        duration = duration,
                        seriesId = seriesId,
                        season = seasonNumber,
                        episodeNumber = episodeNumber,
                        episodeTitle = contentTitle,
                        farsilandUrl = contentUrl,
                        thumbnailUrl = contentPosterUrl
                    )
                }
            }

            Log.d(TAG, "Position saved: ${position}ms / ${duration}ms (${watchedPercent.toInt()}%)")

            // Check if auto-marked as completed (95% threshold)
            if (watchedPercent >= 95f) {
                Log.d(TAG, "Content auto-marked as watched (95%+ threshold)")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error saving position to database", e)
            // Don't crash, just log the error
        }
    }

    /**
     * P2 FIX: Issue #12 - Capture position at selection time, not dialog open time
     * Previous code: Position captured when dialog opens → 15s later user selects → video jumps back 15s
     * Fixed: Capture position when user clicks option → preserves exact playback position
     */
    private fun showQualitySelector() {
        if (availableQualities.isEmpty()) {
            Toast.makeText(this, "No quality options available", Toast.LENGTH_SHORT).show()
            return
        }

        val qualityNames = availableQualities.map { it.quality }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Select Quality")
            .setSingleChoiceItems(qualityNames, currentQualityIndex) { dialog, which ->
                if (which != currentQualityIndex) {
                    // P2 FIX: Capture position HERE (at selection time), not at dialog open time
                    // This prevents time jumps if user takes time deciding
                    val currentPosition = player?.currentPosition ?: 0L
                    switchQuality(which, currentPosition)
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /**
     * Switch to a different quality while preserving playback position
     */
    private fun switchQuality(qualityIndex: Int, position: Long) {
        if (qualityIndex < 0 || qualityIndex >= availableQualities.size) {
            return
        }

        currentQualityIndex = qualityIndex
        val selectedQuality = availableQualities[qualityIndex]

        Log.d(TAG, "Switching to ${selectedQuality.quality} at position ${position}ms")

        // Save preference
        prefs.edit().putString(PREF_QUALITY, selectedQuality.quality).apply()

        // Reset tried URLs when switching quality so all mirrors can be tried fresh
        triedUrls.clear()

        // Get current playback state
        val wasPlaying = player?.isPlaying ?: false

        // Prepare new media item at saved position
        currentVideoUrl = selectedQuality.url
        val mediaItem = MediaItem.fromUri(currentVideoUrl)
        player?.apply {
            setMediaItem(mediaItem)
            seekTo(position)
            prepare()
            playWhenReady = wasPlaying
        }

        Toast.makeText(this, getString(R.string.switched_to_quality, selectedQuality.quality), Toast.LENGTH_SHORT).show()
    }

    /**
     * Intercept key events BEFORE PlayerView processes them
     * This is critical - PlayerView shows controls on any D-pad press by default
     * Using dispatchKeyEvent ensures we handle Left/Right for seeking before PlayerView
     *
     * Q/Menu: Quality selector
     * S: Cycle playback speed
     * I: Toggle info overlay
     * Left/Right: Skip 10s/30s ONLY when controls are hidden
     * Up/Down/Center: Shows player controls
     *
     * When controls are visible, D-pad navigates the control bar normally
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val keyCode = event.keyCode
        val action = event.action

        // Handle key down events
        if (action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            when (keyCode) {
                KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_Q -> {
                    showQualitySelector()
                    return true
                }
                KeyEvent.KEYCODE_S -> {
                    cyclePlaybackSpeed()
                    return true
                }
                KeyEvent.KEYCODE_I -> {
                    toggleInfoOverlay()
                    return true
                }
                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_CENTER,
                KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                    // Show controller on Up/Down/Center/Enter
                    if (!playerView.isControllerFullyVisible) {
                        playerView.showController()
                        return true
                    }
                    // Let default handle navigation when already visible
                }
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    // CRITICAL: Check visibility and intercept BEFORE PlayerView
                    if (!playerView.isControllerFullyVisible) {
                        isLongPressing = false
                        longPressHandler.postDelayed({
                            isLongPressing = true
                            skipPlayback(-SKIP_LONG_MS)
                        }, LONG_PRESS_THRESHOLD)
                        return true  // Consume event - don't let PlayerView show controls
                    }
                    // Controls visible - let PlayerView handle navigation
                }
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    // CRITICAL: Check visibility and intercept BEFORE PlayerView
                    if (!playerView.isControllerFullyVisible) {
                        isLongPressing = false
                        longPressHandler.postDelayed({
                            isLongPressing = true
                            skipPlayback(SKIP_LONG_MS)
                        }, LONG_PRESS_THRESHOLD)
                        return true  // Consume event - don't let PlayerView show controls
                    }
                    // Controls visible - let PlayerView handle navigation
                }
            }
        }

        // Handle key up events for skip completion
        if (action == KeyEvent.ACTION_UP) {
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    if (!playerView.isControllerFullyVisible) {
                        longPressHandler.removeCallbacksAndMessages(null)
                        if (!isLongPressing) {
                            skipPlayback(-SKIP_SHORT_MS)
                        }
                        isLongPressing = false
                        return true
                    }
                }
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    if (!playerView.isControllerFullyVisible) {
                        longPressHandler.removeCallbacksAndMessages(null)
                        if (!isLongPressing) {
                            skipPlayback(SKIP_SHORT_MS)
                        }
                        isLongPressing = false
                        return true
                    }
                }
            }
        }

        return super.dispatchKeyEvent(event)
    }

    /**
     * Cycle through playback speed options
     * Saves preference for next playback session
     */
    private fun cyclePlaybackSpeed() {
        currentSpeedIndex = (currentSpeedIndex + 1) % speedOptions.size
        val newSpeed = speedOptions[currentSpeedIndex]

        player?.playbackParameters = PlaybackParameters(newSpeed)

        // Save speed preference for future sessions
        getSharedPreferences("player_prefs", Context.MODE_PRIVATE)
            .edit()
            .putInt(PREF_SPEED_INDEX, currentSpeedIndex)
            .apply()

        // Show speed indicator
        val speedText = if (newSpeed == 1.0f) "1x" else "${newSpeed}x"
        speedIndicator.text = speedText
        speedIndicator.visibility = View.VISIBLE

        // Auto-hide after 2 seconds (unless not 1x)
        speedIndicator.removeCallbacks(hideSpeedIndicatorRunnable)
        if (newSpeed == 1.0f) {
            speedIndicator.postDelayed(hideSpeedIndicatorRunnable, 2000)
        }

        Log.d(TAG, "Playback speed changed to: $speedText")
    }

    private val hideSpeedIndicatorRunnable = Runnable {
        speedIndicator.visibility = View.GONE
    }

    /**
     * Skip playback by specified milliseconds (positive = forward, negative = backward)
     */
    private fun skipPlayback(milliseconds: Long) {
        player?.let { p ->
            val currentPos = p.currentPosition
            val duration = p.duration
            val newPos = (currentPos + milliseconds).coerceIn(0, duration)

            p.seekTo(newPos)

            // Show skip indicator
            val skipText = if (milliseconds > 0) {
                "+${milliseconds / 1000}s"
            } else {
                "${milliseconds / 1000}s"
            }
            showSkipIndicator(skipText)

            Log.d(TAG, "Skipped $skipText to position: ${newPos}ms")
        }
    }

    /**
     * Show skip feedback indicator briefly
     */
    private fun showSkipIndicator(text: String) {
        skipIndicator.text = text
        skipIndicator.visibility = View.VISIBLE
        skipIndicator.removeCallbacks(hideSkipIndicatorRunnable)
        skipIndicator.postDelayed(hideSkipIndicatorRunnable, 800)
    }

    private val hideSkipIndicatorRunnable = Runnable {
        skipIndicator.visibility = View.GONE
    }

    /**
     * Toggle info overlay visibility
     */
    private fun toggleInfoOverlay() {
        if (infoOverlay.visibility == View.VISIBLE) {
            infoOverlay.visibility = View.GONE
            infoOverlayHandler.removeCallbacksAndMessages(null)
        } else {
            updateInfoOverlay()
            infoOverlay.visibility = View.VISIBLE
            // Auto-hide after timeout
            infoOverlayHandler.removeCallbacksAndMessages(null)
            infoOverlayHandler.postDelayed({
                infoOverlay.visibility = View.GONE
            }, INFO_OVERLAY_TIMEOUT)
        }
    }

    /**
     * Update info overlay with current playback stats
     */
    private fun updateInfoOverlay() {
        infoTitle.text = contentTitle

        player?.let { p ->
            val position = p.currentPosition
            val duration = p.duration
            val posText = "${formatTime(position)} / ${formatTime(duration)}"
            infoPosition.text = "Position: $posText"

            val quality = availableQualities.getOrNull(currentQualityIndex)?.quality ?: "Unknown"
            infoQuality.text = "Quality: $quality"

            val speed = speedOptions[currentSpeedIndex]
            val speedText = if (speed == 1.0f) "1x (Normal)" else "${speed}x"
            infoSpeed.text = "Speed: $speedText"

            val bufferedPercent = p.bufferedPercentage
            infoBuffer.text = "Buffered: $bufferedPercent%"
        }
    }

    /**
     * Format milliseconds to HH:MM:SS or MM:SS
     */
    private fun formatTime(ms: Long): String {
        if (ms <= 0) return "00:00"
        val totalSeconds = ms / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60

        return if (hours > 0) {
            String.format("%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
    }

    override fun onPause() {
        super.onPause()
        player?.pause()
        // Stop position tracking handler to prevent battery drain
        positionHandler.removeCallbacks(positionSaveRunnable)
        // Save position immediately when user pauses or leaves
        saveCurrentPosition()
    }

    override fun onStop() {
        super.onStop()
        // P0 FIX: Issue #1 - Save playback state and release BOTH player and cache
        // This prevents crash when user returns after pressing Home button
        // Previously: cache was released but player remained alive → crash when player accessed closed cache

        // Save current playback state for restoration in onStart()
        player?.let {
            savedPlaybackState = PlaybackState(
                position = it.currentPosition,
                videoUrl = currentVideoUrl,
                wasPlaying = it.isPlaying
            )
            Log.d(TAG, "Saved playback state: position=${it.currentPosition}ms, playing=${it.isPlaying}")
        }

        // Stop position tracking to prevent battery drain
        positionHandler.removeCallbacks(positionSaveRunnable)

        // Save position to database immediately
        saveCurrentPosition()

        // Release player (cache is managed by FarsilandApp singleton)
        player?.release()
        player = null

        Log.d(TAG, "Player released in onStop() - cache managed by FarsilandApp")
    }

    /**
     * EXTERNAL AUDIT FIX C2.3: Save state to Bundle for process death recovery
     *
     * Problem: Android kills background processes when RAM is low
     * Result: User loses playback position if they leave app and Android reclaims memory
     * Solution: Persist critical state to Bundle which survives process death
     */
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        player?.let {
            outState.putLong("playback_position", it.currentPosition)
            outState.putString("video_url", currentVideoUrl)
            outState.putBoolean("was_playing", it.isPlaying)
            outState.putInt("quality_index", currentQualityIndex)
        }

        // Save content metadata
        outState.putInt("content_id", contentId)
        outState.putString("content_type", contentType)
        outState.putString("content_title", contentTitle)
        outState.putString("content_url", contentUrl)
        outState.putString("content_poster_url", contentPosterUrl)

        if (contentType == "episode") {
            outState.putInt("series_id", seriesId)
            outState.putInt("season_number", seasonNumber)
            outState.putInt("episode_number", episodeNumber)
        }

        Log.d(TAG, "State saved to Bundle for process death recovery - position=${player?.currentPosition}ms")
    }

    /**
     * EXTERNAL AUDIT FIX C2.3: Restore state from Bundle after process death
     */
    private fun restoreStateFromBundle(savedInstanceState: Bundle) {
        val position = savedInstanceState.getLong("playback_position", 0L)
        val videoUrl = savedInstanceState.getString("video_url", "")
        val wasPlaying = savedInstanceState.getBoolean("was_playing", false)
        val qualityIndex = savedInstanceState.getInt("quality_index", 0)

        // Restore content metadata
        contentId = savedInstanceState.getInt("content_id", contentId)
        contentType = savedInstanceState.getString("content_type", contentType)
        contentTitle = savedInstanceState.getString("content_title", contentTitle)
        contentUrl = savedInstanceState.getString("content_url", contentUrl)
        contentPosterUrl = savedInstanceState.getString("content_poster_url", contentPosterUrl)

        if (contentType == "episode") {
            seriesId = savedInstanceState.getInt("series_id", seriesId)
            seasonNumber = savedInstanceState.getInt("season_number", seasonNumber)
            episodeNumber = savedInstanceState.getInt("episode_number", episodeNumber)
        }

        if (videoUrl.isNotEmpty() && position > 0) {
            Log.d(TAG, "Restoring playback from Bundle after process death: position=${position}ms, url=$videoUrl")

            savedPlaybackState = PlaybackState(
                position = position,
                videoUrl = videoUrl,
                wasPlaying = wasPlaying
            )
            currentQualityIndex = qualityIndex
        }
    }

    override fun onStart() {
        super.onStart()
        // EXTERNAL AUDIT FIX S4: Wrap in try-catch to prevent crash on storage errors
        try {
            // P0 FIX: Issue #1 - Restore player and cache if released in onStop()
            // This handles the case when user returns after pressing Home button

            if (player == null && savedPlaybackState != null) {
            Log.d(TAG, "Re-initializing player after onStop(), restoring state")

            // Re-initialize player and cache
            initializePlayer()

            // Restore playback from saved state
            val state = savedPlaybackState!!
            val mediaItem = MediaItem.fromUri(state.videoUrl)
            player?.apply {
                setMediaItem(mediaItem)
                seekTo(state.position)
                prepare()
                playWhenReady = state.wasPlaying
            }

            // Restart position tracking
            if (state.wasPlaying) {
                startPositionTracking()
            }

            Log.d(TAG, "Player restored: position=${state.position}ms, playing=${state.wasPlaying}")
        }

            // Execute pending video fetch if it was deferred from onCreate/onNewIntent
            if (pendingVideoFetch) {
                Log.d(TAG, "Executing deferred video fetch now that activity is started")
                fetchVideoUrlsAndPlay(skipLifecycleCheck = true)  // Skip check - we're in onStart()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in onStart() - failed to restore player", e)
            Toast.makeText(this, "Error restoring playback: ${e.message}", Toast.LENGTH_SHORT).show()
            // Don't crash - just log the error and continue
        }
    }

    override fun onDestroy() {
        // CRITICAL FIX: Do ALL cleanup BEFORE super.onDestroy() because:
        // 1. super.onDestroy() marks lifecycle as DESTROYED
        // 2. lifecycleScope.launch() crashes if lifecycle is DESTROYED
        // 3. Handlers must be cleared before lifecycle destruction

        // Save final position FIRST (uses lifecycleScope which requires valid lifecycle)
        saveCurrentPosition()

        // C2 FIX: Remove ALL pending callbacks from ALL handlers to prevent memory leaks
        // Each Handler holds implicit reference to Activity - must clear all callbacks
        positionHandler.removeCallbacksAndMessages(null)
        longPressHandler.removeCallbacksAndMessages(null)
        infoOverlayHandler.removeCallbacksAndMessages(null)

        // Clean up YouTube WebView if it was used (prevents memory leak)
        cleanupYouTubeWebView()

        // M6 FIX: Unregister network callback to prevent memory leak
        // EXTERNAL AUDIT FIX C4.3: Clear registration flag after unregister
        networkCallback?.let {
            try {
                connectivityManager.unregisterNetworkCallback(it)
                isNetworkCallbackRegistered = false  // EXTERNAL AUDIT FIX C4.3: Mark as unregistered
                Log.d(TAG, "Network callback unregistered")
            } catch (e: Exception) {
                Log.e(TAG, "Error unregistering network callback", e)
            }
        }
        networkCallback = null
        isNetworkCallbackRegistered = false  // EXTERNAL AUDIT FIX C4.3: Ensure flag is cleared

        // Defensive cleanup (player should already be null from onStop)
        // Note: Video cache is managed by FarsilandApp singleton - never release it here
        player?.release()
        player = null

        // AFR: Restore default display mode before destroying activity
        AutoFrameRateHelper.disableAFR(this)

        // Clear cast callbacks to prevent leaks
        if (::castManager.isInitialized) {
            castManager.onCastSessionStarted = null
            castManager.onCastSessionEnded = null
            castManager.onCastAvailabilityChanged = null
        }

        // Clear saved state
        savedPlaybackState = null

        // Call super.onDestroy() LAST per Android lifecycle best practice
        super.onDestroy()
    }

    companion object {
        private const val TAG = "VideoPlayerActivity"
        private const val PREF_QUALITY = "preferred_quality"
        private const val PREF_SPEED_INDEX = "preferred_speed_index"
        private const val POSITION_SAVE_INTERVAL = 10_000L // Save every 10 seconds

        // L2 FIX: Define all magic numbers as named constants
        private const val SKIP_SHORT_MS = 10_000L       // 10 second skip
        private const val SKIP_LONG_MS = 30_000L        // 30 second skip
        private const val LONG_PRESS_THRESHOLD = 500L   // ms to detect long press
        private const val INFO_OVERLAY_TIMEOUT = 5_000L // 5 seconds auto-hide
        private const val SKIP_INDICATOR_TIMEOUT = 800L // Quick feedback indicator
    }
}
