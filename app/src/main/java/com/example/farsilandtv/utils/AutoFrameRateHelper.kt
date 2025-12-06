package com.example.farsilandtv.utils

import android.app.Activity
import android.os.Build
import android.util.Log
import android.view.Display
import androidx.annotation.RequiresApi

/**
 * Auto Frame Rate (AFR) Matching for Shield TV
 * Automatically switches display refresh rate to match video frame rate
 *
 * Requirements:
 * - API 30+ (Android 11+)
 * - Shield TV with HDMI 2.0+ display
 *
 * Supported frame rates: 23.976, 24, 25, 29.97, 30, 50, 59.94, 60 fps
 *
 * UT-M5 NOTE: This implementation uses Window.setAttributes() which does NOT require
 * WRITE_SETTINGS permission. It only affects the current window, not system-wide settings.
 */
object AutoFrameRateHelper {
    private const val TAG = "AutoFrameRate"

    /**
     * Enable AFR for the given activity and video frame rate
     *
     * @param activity VideoPlayerActivity instance
     * @param videoFrameRate Frame rate from video metadata (e.g., 23.976f, 29.97f, 60f)
     * @return true if AFR was enabled, false if not supported or failed
     */
    fun enableAFR(activity: Activity, videoFrameRate: Float): Boolean {
        // AFR requires API 30+
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            Log.d(TAG, "AFR not supported - requires API 30+, current: ${Build.VERSION.SDK_INT}")
            return false
        }

        return enableAFRApi30(activity, videoFrameRate)
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun enableAFRApi30(activity: Activity, videoFrameRate: Float): Boolean {
        try {
            val display = activity.display
            if (display == null) {
                Log.e(TAG, "Failed to get display instance")
                return false
            }

            val currentMode = display.mode
            Log.d(TAG, "Current display mode: ${currentMode.modeId}, refresh=${currentMode.refreshRate}Hz")

            // Find best matching display mode for video frame rate
            val targetMode = findBestDisplayMode(display, videoFrameRate)
            if (targetMode == null) {
                Log.w(TAG, "No suitable display mode found for ${videoFrameRate}fps")
                return false
            }

            // Check if already at target mode
            if (currentMode.modeId == targetMode.modeId) {
                Log.d(TAG, "Already at target mode: ${targetMode.refreshRate}Hz")
                return true
            }

            // Apply the display mode
            val layoutParams = activity.window.attributes
            layoutParams.preferredDisplayModeId = targetMode.modeId
            activity.window.attributes = layoutParams

            Log.i(TAG, "AFR enabled: ${videoFrameRate}fps → ${targetMode.refreshRate}Hz (mode ${targetMode.modeId})")
            return true

        } catch (e: Exception) {
            Log.e(TAG, "Failed to enable AFR", e)
            return false
        }
    }

    /**
     * Find best display mode matching the video frame rate
     *
     * Matching logic:
     * - 23.976/24 fps → 24Hz
     * - 25 fps → 25Hz or 50Hz
     * - 29.97/30 fps → 30Hz or 60Hz
     * - 50 fps → 50Hz
     * - 59.94/60 fps → 60Hz
     */
    @RequiresApi(Build.VERSION_CODES.R)
    private fun findBestDisplayMode(display: Display, videoFrameRate: Float): Display.Mode? {
        val supportedModes = display.supportedModes
        Log.d(TAG, "Searching ${supportedModes.size} display modes for ${videoFrameRate}fps")

        // Determine target refresh rate based on video frame rate
        val targetRefreshRate = when {
            videoFrameRate in 23.5f..24.5f -> 24f // 23.976/24 fps → 24Hz
            videoFrameRate in 24.5f..25.5f -> 25f // 25 fps → 25Hz
            videoFrameRate in 29.5f..30.5f -> 30f // 29.97/30 fps → 30Hz
            videoFrameRate in 49.5f..50.5f -> 50f // 50 fps → 50Hz
            videoFrameRate in 59.5f..60.5f -> 60f // 59.94/60 fps → 60Hz
            else -> {
                Log.w(TAG, "Unsupported frame rate: ${videoFrameRate}fps")
                return null
            }
        }

        // Find exact match first
        var bestMode = supportedModes.find { mode ->
            val diff = kotlin.math.abs(mode.refreshRate - targetRefreshRate)
            diff < 0.5f // Within 0.5Hz tolerance
        }

        // Fallback: Find closest higher refresh rate (e.g., 25fps can use 50Hz)
        if (bestMode == null && targetRefreshRate <= 30f) {
            val fallbackRate = targetRefreshRate * 2 // Try double refresh rate
            bestMode = supportedModes.find { mode ->
                val diff = kotlin.math.abs(mode.refreshRate - fallbackRate)
                diff < 0.5f
            }
            if (bestMode != null) {
                Log.d(TAG, "Using fallback: ${targetRefreshRate}Hz → ${bestMode.refreshRate}Hz")
            }
        }

        if (bestMode != null) {
            Log.d(TAG, "Selected mode: ${bestMode.modeId} (${bestMode.refreshRate}Hz)")
        } else {
            Log.w(TAG, "No matching mode found for ${targetRefreshRate}Hz")
        }

        return bestMode
    }

    /**
     * Disable AFR and restore default display mode
     */
    fun disableAFR(activity: Activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return
        }

        try {
            val layoutParams = activity.window.attributes
            layoutParams.preferredDisplayModeId = 0 // Reset to default
            activity.window.attributes = layoutParams
            Log.d(TAG, "AFR disabled - restored default display mode")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to disable AFR", e)
        }
    }
}
