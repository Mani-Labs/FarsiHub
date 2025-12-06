package com.example.farsilandtv.utils

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.util.Log

/**
 * Device type detection utility for responsive UI rendering
 *
 * Phase 1: Foundation for phone support
 * Determines device type ONCE at app startup for consistent behavior
 *
 * Usage:
 * - TV: Existing sidebar navigation, D-pad controls
 * - Tablet: Uses TV layout (landscape, sidebar)
 * - Phone: Bottom navigation, touch controls
 */
object DeviceUtils {

    /**
     * Device type classification
     */
    enum class DeviceType {
        TV,     // Android TV devices (Shield, Fire TV, etc.)
        TABLET, // Tablets 600dp+ width (uses TV layout)
        PHONE   // Phones <600dp width (uses phone layout)
    }

    @Volatile
    private var cachedDeviceType: DeviceType? = null

    /**
     * Get the current device type
     *
     * Detection order:
     * 1. Check if running on Android TV (UI_MODE_TYPE_TELEVISION)
     * 2. Check screen width (>= 600dp = tablet, < 600dp = phone)
     *
     * Result is cached for consistent behavior during app session
     */
    fun getDeviceType(context: Context): DeviceType {
        // Return cached value if available (avoid re-detection)
        cachedDeviceType?.let { return it }

        val deviceType = detectDeviceType(context)
        cachedDeviceType = deviceType
        return deviceType
    }

    /**
     * Check if device is a TV
     */
    fun isTV(context: Context): Boolean = getDeviceType(context) == DeviceType.TV

    /**
     * Check if device is a phone
     */
    fun isPhone(context: Context): Boolean = getDeviceType(context) == DeviceType.PHONE

    /**
     * Check if device is a tablet
     */
    fun isTablet(context: Context): Boolean = getDeviceType(context) == DeviceType.TABLET

    /**
     * Check if device should use TV layout (TV or Tablet)
     */
    fun shouldUseTvLayout(context: Context): Boolean {
        val type = getDeviceType(context)
        return type == DeviceType.TV || type == DeviceType.TABLET
    }

    /**
     * Check if device should use phone layout
     */
    fun shouldUsePhoneLayout(context: Context): Boolean = isPhone(context)

    /**
     * Force re-detection (useful for testing or configuration changes)
     */
    fun clearCache() {
        cachedDeviceType = null
    }

    private fun detectDeviceType(context: Context): DeviceType {
        // Check 1: Is this an Android TV device?
        // UT-M2 FIX: Added null check for UiModeManager
        val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
        if (uiModeManager == null) {
            Log.w(TAG, "UiModeManager is null, falling back to screen size detection")
        } else {
            val uiModeType = uiModeManager.currentModeType
            Log.d(TAG, "UI Mode Type: $uiModeType (TV=${Configuration.UI_MODE_TYPE_TELEVISION})")
            if (uiModeType == Configuration.UI_MODE_TYPE_TELEVISION) {
                Log.i(TAG, "Detected: TV (UI_MODE_TYPE_TELEVISION)")
                return DeviceType.TV
            }
        }

        // Check 2: Also check for leanback feature (some TV boxes don't set UI_MODE_TYPE_TELEVISION)
        val hasLeanback = context.packageManager.hasSystemFeature("android.software.leanback")
        Log.d(TAG, "Has Leanback feature: $hasLeanback")
        if (hasLeanback) {
            Log.i(TAG, "Detected: TV (leanback feature)")
            return DeviceType.TV
        }

        // Check 3: Classify by SMALLEST screen width (works regardless of orientation)
        // Use Configuration.smallestScreenWidthDp which is orientation-independent
        val smallestWidthDp = context.resources.configuration.smallestScreenWidthDp
        Log.d(TAG, "Smallest screen width: ${smallestWidthDp}dp (threshold: 600dp)")

        // 600dp is the standard tablet breakpoint (Material Design guidelines)
        // This ensures phones are detected correctly even when forced to landscape
        return if (smallestWidthDp >= 600) {
            Log.i(TAG, "Detected: TABLET (smallestWidthDp >= 600)")
            DeviceType.TABLET
        } else {
            Log.i(TAG, "Detected: PHONE (smallestWidthDp < 600)")
            DeviceType.PHONE
        }
    }

    private const val TAG = "DeviceUtils"
}
