package com.example.farsilandtv.data.repository

import android.content.Context
import android.util.Log
import com.example.farsilandtv.data.database.AppDatabase
import com.example.farsilandtv.data.database.NotificationPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Repository for managing notification preferences
 * Feature #9 - Push Notifications
 */
class NotificationPreferencesRepository(context: Context) {

    companion object {
        private const val TAG = "NotificationPrefsRepo"
    }

    private val database = AppDatabase.getDatabase(context)
    private val dao = database.notificationPreferencesDao()

    /**
     * Get notification preferences as Flow (reactive updates)
     */
    val preferences: Flow<NotificationPreferences> = dao.getPreferences().map { prefs ->
        prefs ?: getDefaultPreferences()
    }

    /**
     * Get notification preferences once (suspend function)
     */
    suspend fun getPreferences(): NotificationPreferences {
        return dao.getPreferencesOnce() ?: run {
            // If no preferences exist, insert defaults
            val defaults = getDefaultPreferences()
            dao.savePreferences(defaults)
            defaults
        }
    }

    /**
     * Update all notification preferences
     */
    suspend fun updatePreferences(preferences: NotificationPreferences) {
        val updated = preferences.copy(lastUpdated = System.currentTimeMillis())
        dao.updatePreferences(updated)
        Log.d(TAG, "Notification preferences updated")
    }

    /**
     * Toggle new episodes notifications
     */
    suspend fun toggleNewEpisodes(enabled: Boolean) {
        val current = getPreferences()
        updatePreferences(current.copy(newEpisodesEnabled = enabled))
        Log.d(TAG, "New episodes notifications: $enabled")
    }

    /**
     * Toggle new seasons notifications
     */
    suspend fun toggleNewSeasons(enabled: Boolean) {
        val current = getPreferences()
        updatePreferences(current.copy(newSeasonsEnabled = enabled))
        Log.d(TAG, "New seasons notifications: $enabled")
    }

    /**
     * Toggle weekly digest notifications
     */
    suspend fun toggleWeeklyDigest(enabled: Boolean) {
        val current = getPreferences()
        updatePreferences(current.copy(weeklyDigestEnabled = enabled))
        Log.d(TAG, "Weekly digest notifications: $enabled")
    }

    /**
     * Update quiet hours
     *
     * @param start Start hour in 24-hour format (0-23)
     * @param end End hour in 24-hour format (0-23)
     */
    suspend fun updateQuietHours(start: Int, end: Int) {
        require(start in 0..23) { "Start hour must be between 0 and 23" }
        require(end in 0..23) { "End hour must be between 0 and 23" }

        val current = getPreferences()
        updatePreferences(
            current.copy(
                quietHoursStart = start,
                quietHoursEnd = end
            )
        )
        Log.d(TAG, "Quiet hours updated: $start:00 - $end:00")
    }

    /**
     * Check if notifications are allowed at current hour
     */
    suspend fun isAllowedAtCurrentHour(): Boolean {
        val currentHour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        return dao.isAllowedAtHour(currentHour) ?: true
    }

    /**
     * Check if new episode notifications are enabled
     */
    suspend fun isNewEpisodesEnabled(): Boolean {
        return dao.isNewEpisodesEnabled() ?: true
    }

    /**
     * Check if new season notifications are enabled
     */
    suspend fun isNewSeasonsEnabled(): Boolean {
        return dao.isNewSeasonsEnabled() ?: true
    }

    /**
     * Check if weekly digest is enabled
     */
    suspend fun isWeeklyDigestEnabled(): Boolean {
        return dao.isWeeklyDigestEnabled() ?: false
    }

    /**
     * Reset to default preferences
     */
    suspend fun resetToDefaults() {
        val defaults = getDefaultPreferences()
        dao.savePreferences(defaults)
        Log.d(TAG, "Notification preferences reset to defaults")
    }

    /**
     * Get default notification preferences
     */
    private fun getDefaultPreferences(): NotificationPreferences {
        return NotificationPreferences(
            id = 1,
            newEpisodesEnabled = true,
            newSeasonsEnabled = true,
            weeklyDigestEnabled = false,
            quietHoursStart = 22, // 10 PM
            quietHoursEnd = 8,    // 8 AM
            lastUpdated = System.currentTimeMillis()
        )
    }
}
