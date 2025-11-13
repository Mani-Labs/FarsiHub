package com.example.farsilandtv.utils

import android.content.Context
import android.content.SharedPreferences

/**
 * Manages background sync preferences and settings
 *
 * Battery-saving features:
 * - WiFi-only sync (default)
 * - Sync frequency control
 * - Charging-only option
 * - Manual enable/disable
 */
class SyncPreferences(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("sync_settings", Context.MODE_PRIVATE)

    /**
     * Enable/disable background sync completely
     * Default: true (enabled)
     */
    var syncEnabled: Boolean
        get() = prefs.getBoolean("sync_enabled", true)
        set(value) = prefs.edit().putBoolean("sync_enabled", value).apply()

    /**
     * Sync only on WiFi (not cellular)
     * Default: true (WiFi-only)
     * Battery saving: prevents expensive cellular sync
     */
    var wifiOnlySync: Boolean
        get() = prefs.getBoolean("wifi_only", true)
        set(value) = prefs.edit().putBoolean("wifi_only", value).apply()

    /**
     * Sync frequency in hours
     * Default: 12 hours
     * Smart sync will skip if < frequency hours since last sync
     */
    var syncFrequencyHours: Int
        get() = prefs.getInt("sync_frequency_hours", 12)
        set(value) = prefs.edit().putInt("sync_frequency_hours", value).apply()

    /**
     * Last sync timestamp (milliseconds)
     * Used to calculate if sync is needed
     */
    var lastSyncTime: Long
        get() = prefs.getLong("last_sync_time", 0)
        set(value) = prefs.edit().putLong("last_sync_time", value).apply()

    /**
     * Sync only when charging
     * Default: false (can sync on battery)
     * Battery saving: sync during charging to preserve battery
     */
    var syncOnlyWhenCharging: Boolean
        get() = prefs.getBoolean("sync_only_charging", false)
        set(value) = prefs.edit().putBoolean("sync_only_charging", value).apply()

    /**
     * Quiet hours start (hour 0-23)
     * Default: 22 (10 PM)
     * Don't sync during quiet hours to avoid interruptions
     */
    var quietHoursStart: Int
        get() = prefs.getInt("quiet_hours_start", 22)
        set(value) = prefs.edit().putInt("quiet_hours_start", value).apply()

    /**
     * Quiet hours end (hour 0-23)
     * Default: 8 (8 AM)
     * Resume sync after quiet hours end
     */
    var quietHoursEnd: Int
        get() = prefs.getInt("quiet_hours_end", 8)
        set(value) = prefs.edit().putInt("quiet_hours_end", value).apply()

    /**
     * Quiet hours enabled
     * Default: false (no quiet hours)
     */
    var quietHoursEnabled: Boolean
        get() = prefs.getBoolean("quiet_hours_enabled", false)
        set(value) = prefs.edit().putBoolean("quiet_hours_enabled", value).apply()

    /**
     * Last content update timestamp (from API)
     * Used to track when content was last updated on server
     */
    var lastContentUpdate: Long
        get() = prefs.getLong("last_content_update", 0)
        set(value) = prefs.edit().putLong("last_content_update", value).apply()

    /**
     * Check if we're currently in quiet hours
     * @return true if within quiet hours period
     */
    fun isInQuietHours(): Boolean {
        if (!quietHoursEnabled) return false

        val currentHour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)

        return if (quietHoursStart < quietHoursEnd) {
            // Normal range (e.g., 22:00 to 08:00 next day)
            currentHour >= quietHoursStart || currentHour < quietHoursEnd
        } else {
            // Same day range (e.g., 08:00 to 22:00)
            currentHour >= quietHoursStart && currentHour < quietHoursEnd
        }
    }

    /**
     * Check if enough time has passed since last sync
     * @return true if sync is needed based on frequency setting
     */
    fun shouldSyncBasedOnFrequency(): Boolean {
        val currentTime = System.currentTimeMillis()
        val timeSinceLastSync = currentTime - lastSyncTime
        val syncIntervalMs = syncFrequencyHours * 60 * 60 * 1000L

        return timeSinceLastSync >= syncIntervalMs
    }

    /**
     * Reset all preferences to defaults
     */
    fun resetToDefaults() {
        prefs.edit().clear().apply()
    }
}
