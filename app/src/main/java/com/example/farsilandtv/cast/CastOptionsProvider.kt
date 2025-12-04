package com.example.farsilandtv.cast

import android.content.Context
import com.google.android.gms.cast.CastMediaControlIntent
import com.google.android.gms.cast.LaunchOptions
import com.google.android.gms.cast.framework.CastOptions
import com.google.android.gms.cast.framework.OptionsProvider
import com.google.android.gms.cast.framework.SessionProvider
import com.google.android.gms.cast.framework.media.CastMediaOptions
import com.google.android.gms.cast.framework.media.MediaIntentReceiver
import com.google.android.gms.cast.framework.media.NotificationOptions

/**
 * Chromecast Options Provider
 * Configures the Cast framework for FarsiPlex
 *
 * Uses the Default Media Receiver for broad compatibility with all Chromecast devices.
 * For custom UI on the receiver, register a custom receiver app at:
 * https://cast.google.com/publish
 */
class CastOptionsProvider : OptionsProvider {

    override fun getCastOptions(context: Context): CastOptions {
        // Use Default Media Receiver for broad compatibility
        // This works with all Chromecast devices without custom receiver setup
        val receiverAppId = CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID

        // Notification controls while casting
        val notificationOptions = NotificationOptions.Builder()
            .setActions(
                listOf(
                    MediaIntentReceiver.ACTION_REWIND,
                    MediaIntentReceiver.ACTION_TOGGLE_PLAYBACK,
                    MediaIntentReceiver.ACTION_FORWARD,
                    MediaIntentReceiver.ACTION_STOP_CASTING
                ),
                intArrayOf(1, 2) // Play/Pause and Forward are compact actions
            )
            .build()

        // Media options for playback control
        val mediaOptions = CastMediaOptions.Builder()
            .setNotificationOptions(notificationOptions)
            .build()

        // Launch options - relaunch if already running to update content
        val launchOptions = LaunchOptions.Builder()
            .setRelaunchIfRunning(true)
            .build()

        return CastOptions.Builder()
            .setReceiverApplicationId(receiverAppId)
            .setCastMediaOptions(mediaOptions)
            .setLaunchOptions(launchOptions)
            .setStopReceiverApplicationWhenEndingSession(true)
            .build()
    }

    override fun getAdditionalSessionProviders(context: Context): List<SessionProvider>? {
        return null
    }
}
