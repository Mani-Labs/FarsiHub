package com.example.farsilandtv.utils

import android.util.Log
import androidx.compose.runtime.compositionLocalOf

/**
 * CompositionLocal for device type
 *
 * Phase 1: Foundation for phone support
 * Provides device type to all Compose components without prop drilling
 *
 * Usage in Composables:
 * ```
 * val deviceType = LocalDeviceType.current
 * when (deviceType) {
 *     DeviceType.TV -> HomeScreenWithSidebar(...)
 *     DeviceType.TABLET -> HomeScreenWithSidebar(...)
 *     DeviceType.PHONE -> HomeScreenMobile(...)
 * }
 * ```
 *
 * EXTERNAL AUDIT FIX UT-L2: Added warning log when defaulting to TV
 */
val LocalDeviceType = compositionLocalOf {
    Log.w("LocalDeviceType", "No DeviceType provided via CompositionLocalProvider, defaulting to TV")
    DeviceUtils.DeviceType.TV
}
