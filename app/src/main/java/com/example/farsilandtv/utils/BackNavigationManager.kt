package com.example.farsilandtv.utils

import android.util.Log
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity

/**
 * BackNavigationManager - Centralized back navigation handling for Android TV app
 *
 * Provides consistent back navigation behavior:
 * - All activities return to previous screen (never exits from non-home screens)
 * - MainActivity home screen uses double-back-to-exit pattern
 * - Prevents unintended app exits
 */
object BackNavigationManager {
    private const val TAG = "BackNavigationManager"

    /**
     * Handle back navigation for an activity
     *
     * @param activity The activity handling the back press
     * @param isHomeScreen Whether this is the home/main screen
     * @param onDoubleBackExit Callback when double-back should exit (only if isHomeScreen=true)
     * @return true if back press was handled, false otherwise
     */
    fun handleBackPress(
        activity: FragmentActivity,
        isHomeScreen: Boolean = false,
        onDoubleBackExit: (() -> Unit)? = null
    ): Boolean {
        Log.d(TAG, "handleBackPress called - isHomeScreen=$isHomeScreen")

        if (isHomeScreen && onDoubleBackExit != null) {
            // Home screen - let caller handle double-back logic
            return false
        }

        // All non-home screens: just navigate back
        Log.d(TAG, "Navigating back to previous screen")
        return true
    }

    /**
     * Log back press event for debugging
     *
     * @param screenName Name of the screen/fragment
     * @param backStackCount Current back stack entry count (if applicable)
     */
    fun logBackPress(screenName: String, backStackCount: Int = -1) {
        val stackInfo = if (backStackCount >= 0) ", backStackCount=$backStackCount" else ""
        Log.d(TAG, "Back pressed from $screenName$stackInfo")
    }

    /**
     * Check if on home screen (for MainActivity fragments)
     * UT-M3 FIX: Wrapped in try-catch for IllegalStateException
     *
     * @param activity The activity to check
     * @return true if no fragments are on back stack (home screen)
     */
    fun isOnHomeScreen(activity: FragmentActivity): Boolean {
        return try {
            val isHome = activity.supportFragmentManager.backStackEntryCount == 0
            Log.d(TAG, "isOnHomeScreen: $isHome (backStackCount=${activity.supportFragmentManager.backStackEntryCount})")
            isHome
        } catch (e: IllegalStateException) {
            Log.e(TAG, "IllegalStateException checking home screen state: ${e.message}")
            true // Assume home screen if check fails
        }
    }
}
