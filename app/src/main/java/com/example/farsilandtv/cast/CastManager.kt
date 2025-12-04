package com.example.farsilandtv.cast

import android.content.Context
import android.util.Log
import androidx.media3.cast.CastPlayer
import androidx.media3.cast.SessionAvailabilityListener
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManager
import com.google.android.gms.cast.framework.SessionManagerListener
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * CastManager - Handles Chromecast session management and playback
 *
 * Features:
 * - Automatic session management
 * - Seamless handoff between local and remote playback
 * - Position preservation when switching devices
 */
@Singleton
class CastManager @Inject constructor(
    @ApplicationContext context: Context
) {

    companion object {
        private const val TAG = "CastManager"
    }

    private val appContext = context.applicationContext
    private var castContext: CastContext? = null
    private var castPlayer: CastPlayer? = null
    private var sessionManager: SessionManager? = null

    // AUDIT FIX: Track initialization to prevent NPE from accessing uninitialized fields
    @Volatile
    private var isInitialized = false

    // Callbacks for UI updates
    var onCastSessionStarted: ((CastSession) -> Unit)? = null
    var onCastSessionEnded: (() -> Unit)? = null
    var onCastAvailabilityChanged: ((Boolean) -> Unit)? = null

    // Current playback state for handoff
    private var currentMediaItem: MediaItem? = null
    private var currentPosition: Long = 0L
    private var wasPlaying: Boolean = false

    private val sessionManagerListener = object : SessionManagerListener<CastSession> {
        override fun onSessionStarting(session: CastSession) {
            Log.d(TAG, "Cast session starting")
        }

        override fun onSessionStarted(session: CastSession, sessionId: String) {
            Log.d(TAG, "Cast session started: $sessionId")
            onCastSessionStarted?.invoke(session)
        }

        override fun onSessionStartFailed(session: CastSession, error: Int) {
            Log.e(TAG, "Cast session start failed: $error")
        }

        override fun onSessionEnding(session: CastSession) {
            Log.d(TAG, "Cast session ending")
            // Save CastPlayer position before session ends
            castPlayer?.let { player ->
                if (player.playbackState != Player.STATE_IDLE) {
                    currentPosition = player.currentPosition
                    wasPlaying = player.isPlaying
                    Log.d(TAG, "Saved cast position: ${currentPosition}ms, playing: $wasPlaying")
                }
            }
        }

        override fun onSessionEnded(session: CastSession, error: Int) {
            Log.d(TAG, "Cast session ended: error=$error")
            onCastSessionEnded?.invoke()
        }

        override fun onSessionResuming(session: CastSession, sessionId: String) {
            Log.d(TAG, "Cast session resuming: $sessionId")
        }

        override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {
            Log.d(TAG, "Cast session resumed")
            onCastSessionStarted?.invoke(session)
        }

        override fun onSessionResumeFailed(session: CastSession, error: Int) {
            Log.e(TAG, "Cast session resume failed: $error")
        }

        override fun onSessionSuspended(session: CastSession, reason: Int) {
            Log.d(TAG, "Cast session suspended: reason=$reason")
        }
    }

    /**
     * Initialize Cast framework
     * Call this in Application.onCreate() or Activity.onCreate()
     * CD-H1 FIX: Synchronized to prevent race condition in initialization check
     */
    @Synchronized
    fun initialize() {
        // AUDIT FIX: Prevent double initialization
        if (isInitialized) {
            Log.d(TAG, "CastManager already initialized, skipping")
            return
        }

        try {
            castContext = CastContext.getSharedInstance(appContext)
            sessionManager = castContext?.sessionManager
            sessionManager?.addSessionManagerListener(sessionManagerListener, CastSession::class.java)

            // AUDIT FIX: Check castContext before creating CastPlayer
            val ctx = castContext
            if (ctx == null) {
                Log.e(TAG, "CastContext is null, Cast not available on this device")
                // EXTERNAL AUDIT FIX CD-L3: Clear callbacks on init failure
                onCastSessionStarted = null
                onCastSessionEnded = null
                onCastAvailabilityChanged = null
                return
            }

            // Create CastPlayer
            castPlayer = CastPlayer(ctx)

            // Listen for cast availability changes
            castPlayer?.setSessionAvailabilityListener(object : SessionAvailabilityListener {
                override fun onCastSessionAvailable() {
                    Log.d(TAG, "Cast session available")
                    onCastAvailabilityChanged?.invoke(true)
                }

                override fun onCastSessionUnavailable() {
                    Log.d(TAG, "Cast session unavailable")
                    onCastAvailabilityChanged?.invoke(false)
                }
            })

            // Mark as initialized only after successful setup
            isInitialized = true
            Log.i(TAG, "CastManager initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Cast: ${e.message}", e)
            isInitialized = false
            // EXTERNAL AUDIT FIX CD-L3: Clear callbacks on init failure
            onCastSessionStarted = null
            onCastSessionEnded = null
            onCastAvailabilityChanged = null
        }
    }

    /**
     * Check if CastManager has been initialized
     */
    fun isReady(): Boolean = isInitialized

    /**
     * Check if casting is available
     */
    fun isCastAvailable(): Boolean {
        if (!isInitialized) return false
        return castContext?.castState != com.google.android.gms.cast.framework.CastState.NO_DEVICES_AVAILABLE
    }

    /**
     * Check if currently casting
     */
    fun isCasting(): Boolean {
        if (!isInitialized) return false
        return sessionManager?.currentCastSession != null
    }

    /**
     * Get the CastPlayer for remote playback
     */
    fun getCastPlayer(): CastPlayer? {
        if (!isInitialized) return null
        return castPlayer
    }

    /**
     * Get current cast session
     */
    fun getCurrentSession(): CastSession? {
        if (!isInitialized) return null
        return sessionManager?.currentCastSession
    }

    /**
     * Start casting media
     * Call this when user taps cast button during playback
     *
     * @param videoUrl The video URL to cast
     * @param title Content title
     * @param posterUrl Poster/thumbnail URL
     * @param contentType Content type (movie/episode)
     * @param position Starting position in milliseconds
     */
    fun castMedia(
        videoUrl: String,
        title: String,
        posterUrl: String? = null,
        contentType: String = "video/mp4",
        position: Long = 0L
    ) {
        // AUDIT FIX: Check initialization before accessing fields
        if (!isInitialized) {
            Log.e(TAG, "CastManager not initialized, call initialize() first")
            return
        }

        val castPlayer = this.castPlayer ?: run {
            Log.e(TAG, "CastPlayer not initialized")
            return
        }

        if (!isCasting()) {
            Log.e(TAG, "No active cast session")
            return
        }

        // Build metadata
        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .apply {
                if (posterUrl != null) {
                    setArtworkUri(android.net.Uri.parse(posterUrl))
                }
            }
            .build()

        // Build MediaItem
        val mediaItem = MediaItem.Builder()
            .setUri(videoUrl)
            .setMediaMetadata(metadata)
            .setMimeType(contentType)
            .build()

        // Set and play
        castPlayer.setMediaItem(mediaItem)
        castPlayer.seekTo(position)
        castPlayer.prepare()
        castPlayer.play()

        Log.i(TAG, "Started casting: $title at position ${position}ms")
    }

    /**
     * Save current playback state for handoff
     * Call before switching from local to cast or vice versa
     */
    fun savePlaybackState(player: Player) {
        currentPosition = player.currentPosition
        wasPlaying = player.isPlaying
        currentMediaItem = player.currentMediaItem
    }

    /**
     * Get saved position for resuming
     */
    fun getSavedPosition(): Long = currentPosition

    /**
     * Check if playback was active before handoff
     */
    fun wasPlayingBeforeHandoff(): Boolean = wasPlaying

    /**
     * Stop casting and return to local playback
     */
    fun stopCasting() {
        castPlayer?.stop()
        sessionManager?.endCurrentSession(true)
    }

    /**
     * Cleanup - call in Application.onTerminate() or Activity.onDestroy()
     * CD-H2 FIX: Proper cleanup documented
     */
    @Synchronized
    fun release() {
        if (!isInitialized) {
            Log.d(TAG, "CastManager not initialized, nothing to release")
            return
        }

        sessionManager?.removeSessionManagerListener(sessionManagerListener, CastSession::class.java)
        castPlayer?.setSessionAvailabilityListener(null)
        castPlayer?.release()

        // CD-L3 FIX: Clear callbacks to prevent memory leaks
        onCastSessionStarted = null
        onCastSessionEnded = null
        onCastAvailabilityChanged = null

        castPlayer = null
        castContext = null
        sessionManager = null
        isInitialized = false  // AUDIT FIX: Reset flag on release

        Log.d(TAG, "CastManager released successfully")
        // Note: Hilt manages singleton lifecycle, no INSTANCE cleanup needed
    }
}
