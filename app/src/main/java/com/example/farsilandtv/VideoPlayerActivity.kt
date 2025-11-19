package com.example.farsilandtv

import android.app.AlertDialog
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.runBlocking
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
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import java.io.File
import kotlinx.coroutines.launch

/**
 * Video Player Activity for movies and episodes
 * Uses ExoPlayer for direct MP4 playback from Farsiland CDN
 */
class VideoPlayerActivity : AppCompatActivity() {

    private lateinit var playerView: PlayerView
    private lateinit var loadingIndicator: ProgressBar
    private lateinit var errorText: TextView
    private var player: ExoPlayer? = null
    private var cache: SimpleCache? = null

    // M6 FIX: Network monitoring during playback
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private val connectivityManager by lazy {
        getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    }

    private lateinit var repository: ContentRepository
    private val videoScraper = VideoUrlScraper
    private val watchlistRepo by lazy { WatchlistRepository(this) }
    private val playbackRepo by lazy { PlaybackRepository(this) }

    // Content info from intent
    private lateinit var contentType: String // "movie" or "episode"
    private var contentId: Int = 0
    private lateinit var contentTitle: String
    private lateinit var contentUrl: String // Farsiland.com page URL
    private var contentPosterUrl: String? = null // Poster/thumbnail URL
    private var seriesId: Int = 0 // For episodes
    private var seasonNumber: Int = 0 // For episodes
    private var episodeNumber: Int = 0 // For episodes

    // Quality selection
    private var availableQualities: List<VideoUrl> = emptyList()
    private var currentQualityIndex: Int = 0
    private val prefs by lazy {
        getSharedPreferences("VideoPlayerPrefs", Context.MODE_PRIVATE)
    }

    // CDN mirror fallback
    private var currentVideoUrl: String = ""
    private var hasTriedMirror: Boolean = false

    // Playback position tracking (C1: Removed FarsilandDatabase, now using AppDatabase via PlaybackRepository)
    private val positionSaveLock = Any()
    private val positionHandler = Handler(Looper.getMainLooper())
    private val positionSaveRunnable = object : Runnable {
        override fun run() {
            saveCurrentPosition()
            positionHandler.postDelayed(this, POSITION_SAVE_INTERVAL)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            setContentView(R.layout.activity_video_player)

            // Initialize repository
            repository = ContentRepository(this)

            // H4 FIX: Fail-fast validation of required intent extras
            // Extract all intent extras first
            contentType = intent.getStringExtra("CONTENT_TYPE") ?: "movie"
            contentId = intent.getIntExtra("CONTENT_ID", 0)
            contentTitle = intent.getStringExtra("CONTENT_TITLE") ?: "Unknown"
            contentUrl = intent.getStringExtra("CONTENT_URL") ?: ""
            contentPosterUrl = intent.getStringExtra("CONTENT_POSTER_URL")

            // For episodes, get additional info
            if (contentType == "episode") {
                seriesId = intent.getIntExtra("SERIES_ID", 0)
                seasonNumber = intent.getIntExtra("EPISODE_SEASON", 0)
                episodeNumber = intent.getIntExtra("EPISODE_NUMBER", 0)
            }

            // Validate required data - fail-fast to prevent invalid state
            if (contentUrl.isEmpty()) {
                Log.e(TAG, "Error: No content URL provided")
                Toast.makeText(this, "Error: No content URL provided", Toast.LENGTH_LONG).show()
                finish()
                return
            }

            if (contentId == 0) {
                Log.e(TAG, "Error: Invalid content ID (0)")
                Toast.makeText(this, "Error: Invalid content ID", Toast.LENGTH_LONG).show()
                finish()
                return
            }

            if (contentType != "movie" && contentType != "episode") {
                Log.e(TAG, "Error: Invalid content type: $contentType")
                Toast.makeText(this, "Error: Invalid content type", Toast.LENGTH_LONG).show()
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
                    Toast.makeText(this, "Error: Invalid episode metadata", Toast.LENGTH_LONG).show()
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

            // Setup quality button in control bar
            setupQualityButton()

            // Setup back press handler
            onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    // Save current position before leaving
                    saveCurrentPosition()
                    // Navigate back to previous activity
                    finish()
                }
            })

            Log.d(TAG, "Playing $contentType: $contentTitle")
            Log.d(TAG, "Farsiland URL: $contentUrl")
            Log.d(TAG, "Content ID: $contentId")

            // Initialize ExoPlayer
            initializePlayer()

            // M6 FIX: Register network callback to monitor connectivity during playback
            registerNetworkCallback()

            // Check if quality was already selected
            val selectedVideoUrl = intent.getStringExtra("SELECTED_VIDEO_URL")
            val selectedQuality = intent.getStringExtra("SELECTED_VIDEO_QUALITY")

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

    private fun setupQualityButton() {
        // Find the quality button from the custom controls
        playerView.findViewById<View>(R.id.quality_button)?.setOnClickListener {
            showQualitySelector()
        }
    }

    /**
     * M6 FIX: Register network callback to monitor connectivity during playback
     * Notifies user when network drops, preventing silent infinite buffering
     */
    private fun registerNetworkCallback() {
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
                // Network reconnected
                runOnUiThread {
                    Toast.makeText(
                        this@VideoPlayerActivity,
                        R.string.network_connection_restored,
                        Toast.LENGTH_SHORT
                    ).show()
                    Log.d(TAG, "Network connection restored")
                }
            }
        }

        try {
            connectivityManager.registerDefaultNetworkCallback(networkCallback!!)
            Log.d(TAG, "Network callback registered successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register network callback", e)
        }
    }

    private fun initializePlayer() {
        // Use the full episode/movie page URL as Referer header
        // CDN servers validate that requests come from the actual content page
        val referer = contentUrl

        Log.d(TAG, "Setting Referer header: $referer")

        // Setup HTTP data source with appropriate Referer header
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .setDefaultRequestProperties(
                mapOf("Referer" to referer)
            )

        // Initialize 100MB disk cache for video segments
        val cacheSize = 100L * 1024 * 1024 // 100MB cache
        cache = SimpleCache(
            File(cacheDir, "exoplayer_cache"),
            LeastRecentlyUsedCacheEvictor(cacheSize),
            StandaloneDatabaseProvider(this)
        )

        // Wrap HTTP data source with cache for faster quality switches and replays
        val cacheDataSourceFactory = CacheDataSource.Factory()
            .setCache(cache!!)
            .setUpstreamDataSourceFactory(httpDataSourceFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        val mediaSourceFactory = DefaultMediaSourceFactory(cacheDataSourceFactory)

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
        val trackSelector = DefaultTrackSelector(this).apply {
            parameters = parameters
                .buildUpon()
                .setMaxVideoSizeSd() // Start with SD, upgrade to HD
                .setPreferredVideoMimeTypes("video/mp4", "video/avc")
                .build()
        }

        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .setTrackSelector(trackSelector)
            .setBandwidthMeter(DefaultBandwidthMeter.getSingletonInstance(this))
            .build().also { exoPlayer ->
            playerView.player = exoPlayer

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

                    // Try CDN mirror fallback if not already tried
                    if (!hasTriedMirror && currentVideoUrl.contains("d1.flnd.buzz")) {
                        Log.d(TAG, "Primary CDN failed, trying mirror...")
                        tryMirrorCDN()
                    } else {
                        showError("Playback error: ${error.message}")
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

    private fun fetchVideoUrlsAndPlay() {
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

                // Fetch video URLs from the page
                Toast.makeText(this@VideoPlayerActivity, "Fetching video...", Toast.LENGTH_SHORT).show()

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

                Toast.makeText(
                    this@VideoPlayerActivity,
                    "Loading ${selectedVideo.quality}...",
                    Toast.LENGTH_SHORT
                ).show()

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
            Toast.makeText(this, "Playing: $contentTitle", Toast.LENGTH_SHORT).show()

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
     * Try mirror CDN if primary fails
     */
    private fun tryMirrorCDN() {
        if (hasTriedMirror) return

        hasTriedMirror = true
        val mirrorUrl = currentVideoUrl.replace("d1.flnd.buzz", "d2.flnd.buzz")

        Log.d(TAG, "Retrying with mirror CDN: $mirrorUrl")
        Toast.makeText(this, "Retrying with backup server...", Toast.LENGTH_SHORT).show()

        // Get current position before switching
        val currentPosition = player?.currentPosition ?: 0L
        val wasPlaying = player?.isPlaying ?: false

        // Play from mirror CDN at same position
        currentVideoUrl = mirrorUrl
        val mediaItem = MediaItem.fromUri(mirrorUrl)
        player?.apply {
            setMediaItem(mediaItem)
            seekTo(currentPosition)
            prepare()
            playWhenReady = wasPlaying
        }
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
     */
    private fun saveCurrentPosition() {
        synchronized(positionSaveLock) {
            try {
                val currentPlayer = player ?: return
                val position = currentPlayer.currentPosition
                val duration = currentPlayer.duration

                if (duration <= 0) return // Skip if duration not available yet

                // H6 FIX: Use runBlocking during destruction to ensure position is saved
                // before Activity is destroyed. GlobalScope would outlive Activity lifecycle,
                // causing potential crashes and memory leaks.
                val isDestroying = isFinishing || isDestroyed
                if (isDestroying) {
                    // Use runBlocking to ensure synchronous execution before Activity death
                    // This is acceptable here because:
                    // 1. It's during destruction (user is leaving anyway)
                    // 2. Prevents data loss from incomplete async operations
                    // 3. Database write is fast (<50ms typically)
                    runBlocking {
                        try {
                            savePositionToDatabase(position, duration)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error saving position on destroy", e)
                        }
                    }
                } else {
                    lifecycleScope.launch {
                        savePositionToDatabase(position, duration)
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
     * Show quality selection dialog
     */
    private fun showQualitySelector() {
        if (availableQualities.isEmpty()) {
            Toast.makeText(this, "No quality options available", Toast.LENGTH_SHORT).show()
            return
        }

        val qualityNames = availableQualities.map { it.quality }.toTypedArray()
        val currentPosition = player?.currentPosition ?: 0L

        AlertDialog.Builder(this)
            .setTitle("Select Quality")
            .setSingleChoiceItems(qualityNames, currentQualityIndex) { dialog, which ->
                if (which != currentQualityIndex) {
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

        // Reset mirror retry flag when switching quality
        hasTriedMirror = false

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
     * Handle key events for quality menu
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_Q -> {
                showQualitySelector()
                true
            }
            else -> super.onKeyDown(keyCode, event)
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
        // H12 FIX: Release cache in onStop() to prevent file handle leak
        // If Activity is killed without onDestroy(), cache will still be released
        cache?.release()
        cache = null
    }

    override fun onDestroy() {
        super.onDestroy()
        // Stop position tracking
        positionHandler.removeCallbacks(positionSaveRunnable)
        // Save final position
        saveCurrentPosition()
        // M6 FIX: Unregister network callback to prevent memory leak
        networkCallback?.let {
            try {
                connectivityManager.unregisterNetworkCallback(it)
                Log.d(TAG, "Network callback unregistered")
            } catch (e: Exception) {
                Log.e(TAG, "Error unregistering network callback", e)
            }
        }
        networkCallback = null
        // Release player
        player?.release()
        player = null
        // Defensive: release cache again in case onStop wasn't called
        cache?.release()
        cache = null
    }

    companion object {
        private const val TAG = "VideoPlayerActivity"
        private const val PREF_QUALITY = "preferred_quality"
        private const val POSITION_SAVE_INTERVAL = 10_000L // Save every 10 seconds
    }
}
