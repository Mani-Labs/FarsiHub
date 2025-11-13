package com.example.farsilandtv.utils

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

/**
 * Week 4 - Feature #16: Enhanced Focus Memory Manager
 *
 * Manages focus memory with persistent storage:
 * - Save scroll positions for all grid/list screens
 * - Restore focus on back navigation
 * - Remember last tab on HomeScreen
 * - Persist across app restarts via SharedPreferences
 * - Automatic cleanup of old states (7 day expiry)
 */
object FocusMemoryManagerEnhanced {
    private const val TAG = "FocusMemoryEnhanced"
    private const val PREFS_NAME = "focus_memory_prefs"

    private var prefs: SharedPreferences? = null
    private val memoryCache = mutableMapOf<String, FocusState>()

    data class FocusState(
        val screenKey: String,
        val itemPosition: Int,
        val scrollOffset: Int = 0,
        val timestamp: Long = System.currentTimeMillis()
    )

    /**
     * Initialize with application context
     * Call from Application.onCreate()
     */
    fun initialize(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            Log.d(TAG, "FocusMemoryManagerEnhanced initialized")
        }
    }

    /**
     * Save focus position for a screen
     */
    fun saveFocus(screenKey: String, itemPosition: Int, scrollOffset: Int = 0) {
        val focusState = FocusState(screenKey, itemPosition, scrollOffset)

        // Save to memory cache
        memoryCache[screenKey] = focusState

        // Save to persistent storage
        prefs?.edit()?.apply {
            putInt("${screenKey}_position", itemPosition)
            putInt("${screenKey}_offset", scrollOffset)
            putLong("${screenKey}_timestamp", focusState.timestamp)
            apply()
        }

        Log.d(TAG, "Saved focus for $screenKey: position=$itemPosition, offset=$scrollOffset")
    }

    /**
     * Restore focus position for a screen
     */
    fun restoreFocus(screenKey: String): FocusState? {
        // Check memory cache first
        memoryCache[screenKey]?.let { return it }

        // Fall back to persistent storage
        prefs?.let { p ->
            val position = p.getInt("${screenKey}_position", -1)
            if (position >= 0) {
                val offset = p.getInt("${screenKey}_offset", 0)
                val timestamp = p.getLong("${screenKey}_timestamp", 0)

                val focusState = FocusState(screenKey, position, offset, timestamp)
                memoryCache[screenKey] = focusState

                Log.d(TAG, "Restored focus from storage for $screenKey: $position")
                return focusState
            }
        }

        return null
    }

    /**
     * Clear focus memory for a specific screen
     */
    fun clearFocus(screenKey: String) {
        memoryCache.remove(screenKey)
        prefs?.edit()?.apply {
            remove("${screenKey}_position")
            remove("${screenKey}_offset")
            remove("${screenKey}_timestamp")
            apply()
        }
    }

    /**
     * Clear all focus memory
     */
    fun clearAll() {
        memoryCache.clear()
        prefs?.edit()?.clear()?.apply()
        Log.d(TAG, "Cleared all focus memory")
    }

    /**
     * Save/restore last active tab on HomeScreen
     */
    fun saveLastTab(tabIndex: Int) {
        saveFocus("home_last_tab", tabIndex)
    }

    fun restoreLastTab(): Int {
        return restoreFocus("home_last_tab")?.itemPosition ?: 0
    }

    /**
     * Clean up old focus states (older than 7 days)
     */
    fun cleanupOldStates() {
        val weekAgo = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000)

        val keysToRemove = memoryCache.filter { it.value.timestamp < weekAgo }.keys
        keysToRemove.forEach { memoryCache.remove(it) }

        prefs?.let { p ->
            val editor = p.edit()
            p.all.keys.filter { it.endsWith("_timestamp") }.forEach { key ->
                val timestamp = p.getLong(key, 0)
                if (timestamp < weekAgo) {
                    val baseKey = key.removeSuffix("_timestamp")
                    editor.remove("${baseKey}_position")
                    editor.remove("${baseKey}_offset")
                    editor.remove(key)
                }
            }
            editor.apply()
        }

        Log.d(TAG, "Cleaned up ${keysToRemove.size} old focus states")
    }
}
