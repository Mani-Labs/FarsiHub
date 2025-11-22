package com.example.farsilandtv.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.example.farsilandtv.R

/**
 * Helper class for managing notifications
 * Feature #9 - Push Notifications
 */
class NotificationHelper(private val context: Context) {

    companion object {
        private const val TAG = "NotificationHelper"

        // Notification channel IDs
        const val CHANNEL_ID_NEW_EPISODES = "farsiland_new_episodes"
        const val CHANNEL_ID_NEW_SEASONS = "farsiland_new_seasons"
        const val CHANNEL_ID_WEEKLY_DIGEST = "farsiland_weekly_digest"
        const val CHANNEL_ID_PLAYBACK = "farsiland_playback"
        const val CHANNEL_ID_SYNC_ERROR = "farsiland_sync_error"

        // Notification IDs
        const val NOTIFICATION_ID_NEW_EPISODE = 1001
        const val NOTIFICATION_ID_NEW_SEASON = 1002
        const val NOTIFICATION_ID_WEEKLY_DIGEST = 1003
        const val NOTIFICATION_ID_PLAYBACK = 2001
        const val NOTIFICATION_ID_SYNC_ERROR = 3001
    }

    private val notificationManager: NotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    /**
     * Create all notification channels (Android O+)
     * Should be called when app starts
     */
    fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channels = listOf(
                createNewEpisodesChannel(),
                createNewSeasonsChannel(),
                createWeeklyDigestChannel(),
                createPlaybackChannel(),
                createSyncErrorChannel()
            )

            channels.forEach { channel ->
                notificationManager.createNotificationChannel(channel)
                Log.d(TAG, "Created notification channel: ${channel.id}")
            }
        }
    }

    /**
     * Create channel for new episode notifications
     */
    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNewEpisodesChannel(): NotificationChannel {
        return NotificationChannel(
            CHANNEL_ID_NEW_EPISODES,
            "New Episodes",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Notifications for new episodes in your monitored series"
            enableVibration(true)
            setShowBadge(true)
        }
    }

    /**
     * Create channel for new season notifications
     */
    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNewSeasonsChannel(): NotificationChannel {
        return NotificationChannel(
            CHANNEL_ID_NEW_SEASONS,
            "New Seasons",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Notifications for new seasons of your favorite series"
            enableVibration(true)
            setShowBadge(true)
        }
    }

    /**
     * Create channel for weekly digest notifications
     */
    @RequiresApi(Build.VERSION_CODES.O)
    private fun createWeeklyDigestChannel(): NotificationChannel {
        return NotificationChannel(
            CHANNEL_ID_WEEKLY_DIGEST,
            "Weekly Digest",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Weekly summary of new content"
            enableVibration(false)
            setShowBadge(false)
        }
    }

    /**
     * Create channel for playback notifications
     */
    @RequiresApi(Build.VERSION_CODES.O)
    private fun createPlaybackChannel(): NotificationChannel {
        return NotificationChannel(
            CHANNEL_ID_PLAYBACK,
            "Playback Controls",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Media playback controls"
            enableVibration(false)
            setShowBadge(false)
        }
    }

    /**
     * Create channel for sync error notifications
     */
    @RequiresApi(Build.VERSION_CODES.O)
    private fun createSyncErrorChannel(): NotificationChannel {
        return NotificationChannel(
            CHANNEL_ID_SYNC_ERROR,
            "Sync Errors",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Notifications when background sync fails"
            enableVibration(true)
            setShowBadge(true)
        }
    }

    /**
     * Check if current time is within quiet hours
     *
     * @param currentHour Current hour in 24-hour format (0-23)
     * @param quietHoursStart Start of quiet hours (0-23)
     * @param quietHoursEnd End of quiet hours (0-23)
     * @return true if current time is within quiet hours
     */
    fun isQuietHour(
        currentHour: Int,
        quietHoursStart: Int,
        quietHoursEnd: Int
    ): Boolean {
        // Handle case where quiet hours span midnight
        // Example: 22:00 to 08:00 (10 PM to 8 AM)
        return if (quietHoursStart < quietHoursEnd) {
            // Normal case: 10:00 to 14:00 means quiet hours are between these times
            currentHour >= quietHoursStart && currentHour < quietHoursEnd
        } else {
            // Overnight case: 22:00 to 08:00 means quiet hours span midnight
            currentHour >= quietHoursStart || currentHour < quietHoursEnd
        }
    }

    /**
     * Check if a specific notification channel is enabled
     */
    @RequiresApi(Build.VERSION_CODES.O)
    fun isChannelEnabled(channelId: String): Boolean {
        val channel = notificationManager.getNotificationChannel(channelId)
        return channel?.importance != NotificationManager.IMPORTANCE_NONE
    }

    /**
     * Check if notifications are enabled globally
     */
    fun areNotificationsEnabled(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            notificationManager.areNotificationsEnabled()
        } else {
            true // Assume enabled for older versions
        }
    }

    /**
     * Open notification settings for the app
     */
    fun openNotificationSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        }
    }

    /**
     * Cancel all notifications
     */
    fun cancelAllNotifications() {
        notificationManager.cancelAll()
        Log.d(TAG, "All notifications cancelled")
    }

    /**
     * Cancel specific notification by ID
     */
    fun cancelNotification(notificationId: Int) {
        notificationManager.cancel(notificationId)
        Log.d(TAG, "Cancelled notification: $notificationId")
    }

    /**
     * Show sync error notification to user
     * @param syncType Type of sync that failed (e.g., "Farsiland", "FarsiPlex")
     * @param attemptCount Number of retry attempts that failed
     */
    fun showSyncErrorNotification(syncType: String, attemptCount: Int) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID_SYNC_ERROR)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("$syncType Sync Failed")
            .setContentText("Background sync failed after $attemptCount attempts. Check your internet connection.")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("Background sync for $syncType failed after $attemptCount retry attempts. " +
                        "Please check your internet connection and try refreshing manually from Settings > Sync."))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        notificationManager.notify(NOTIFICATION_ID_SYNC_ERROR, notification)
        Log.d(TAG, "Sync error notification shown for $syncType")
    }
}
