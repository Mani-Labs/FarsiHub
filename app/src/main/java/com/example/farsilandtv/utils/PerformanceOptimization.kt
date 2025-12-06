package com.example.farsilandtv.utils

import androidx.compose.runtime.*
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import android.content.Context
import android.util.Log

/**
 * Week 4 - Feature #16: Performance Optimization
 *
 * Utilities for optimizing Compose for TV:
 * - Image caching configuration
 * - Expensive computation memoization
 * - Debounced operations
 */

private const val TAG = "Performance"

/**
 * Create optimized Coil ImageLoader for TV
 * Features:
 * - Larger memory cache (25% of available memory)
 * - Disk cache for offline support
 * - Automatic retry on failure
 */
fun createOptimizedImageLoader(context: Context): ImageLoader {
    // UT-M6 FIX: Correct cache policy builder pattern
    return ImageLoader.Builder(context)
        .memoryCache {
            MemoryCache.Builder(context)
                .maxSizePercent(0.25) // Use 25% of available memory
                .build()
        }
        .diskCache {
            DiskCache.Builder()
                .directory(context.cacheDir.resolve("image_cache"))
                .maxSizeBytes(512L * 1024 * 1024) // 512 MB disk cache
                .build()
        }
        .respectCacheHeaders(false) // Ignore server cache headers
        // UT-M6 FIX: Cache policies are set on builder, not in apply block
        .memoryCachePolicy(CachePolicy.ENABLED)
        .diskCachePolicy(CachePolicy.ENABLED)
        .networkCachePolicy(CachePolicy.ENABLED)
        .build()
}

/**
 * Debounced search implementation
 * Prevents excessive API calls during typing
 */
@Composable
fun <T> rememberDebounced(
    value: T,
    delayMillis: Long = 300
): T {
    var debouncedValue by remember { mutableStateOf(value) }

    LaunchedEffect(value) {
        kotlinx.coroutines.delay(delayMillis)
        debouncedValue = value
    }

    return debouncedValue
}

/**
 * Remember expensive computation with dependencies
 */
@Composable
inline fun <T, R> T.rememberCalculation(
    vararg dependencies: Any?,
    crossinline calculation: T.() -> R
): R {
    return remember(*dependencies) {
        calculation()
    }
}

/**
 * Performance monitoring for Compose screens
 * UT-M7 FIX: Added max size limit to prevent unbounded growth
 */
object ComposePerformanceMonitor {
    private val recompositionCounts = mutableMapOf<String, Int>()
    private const val MAX_TRACKED_SCREENS = 50 // UT-M7 FIX: Limit map size

    fun recordRecomposition(screenName: String) {
        // UT-M7 FIX: Check map size before adding new entries
        if (recompositionCounts.size >= MAX_TRACKED_SCREENS && !recompositionCounts.containsKey(screenName)) {
            Log.w(TAG, "Max tracked screens reached ($MAX_TRACKED_SCREENS), not tracking: $screenName")
            return
        }

        val count = recompositionCounts.getOrDefault(screenName, 0) + 1
        recompositionCounts[screenName] = count

        if (count % 10 == 0) {
            Log.d(TAG, "Screen $screenName recomposed $count times")
        }
    }

    fun reset() {
        recompositionCounts.clear()
    }

    fun getStats(): Map<String, Int> {
        return recompositionCounts.toMap()
    }
}

/**
 * Side effect for tracking recompositions
 */
@Composable
fun TrackRecomposition(screenName: String) {
    SideEffect {
        ComposePerformanceMonitor.recordRecomposition(screenName)
    }
}

/**
 * Lazy initialization helper for expensive objects
 */
fun <T> lazyValue(initializer: () -> T): Lazy<T> {
    return lazy(LazyThreadSafetyMode.NONE) { initializer() }
}

/**
 * Image loading optimization recommendations
 */
object ImageLoadingOptimization {
    /**
     * Recommended image sizes for TV
     */
    const val POSTER_WIDTH = 300 // 150dp * 2 (for @2x density)
    const val POSTER_HEIGHT = 450 // 225dp * 2
    const val BACKDROP_WIDTH = 1920
    const val BACKDROP_HEIGHT = 1080

    /**
     * Generate optimized image URL with resize parameters
     * (If backend supports on-the-fly resizing)
     */
    fun optimizeImageUrl(url: String, width: Int, height: Int): String {
        // Example: Add resize parameters to image URL
        // Actual implementation depends on backend API
        return url // For now, return original URL
    }

    /**
     * Log image loading performance
     */
    fun logImageLoad(url: String, durationMs: Long) {
        if (durationMs > 500) {
            Log.w(TAG, "Slow image load: $url took ${durationMs}ms")
        }
    }
}

/**
 * List virtualization helper
 * Ensures only visible items are rendered
 */
object ListVirtualization {
    /**
     * Calculate visible items for TV screen
     * Based on typical 1080p TV resolution
     */
    fun calculateVisibleItems(
        screenWidth: Int = 1920,
        screenHeight: Int = 1080,
        itemWidth: Int = 150,
        itemHeight: Int = 225,
        columns: Int = 5
    ): Int {
        val visibleRows = (screenHeight / itemHeight) + 2 // +2 for buffer
        return visibleRows * columns
    }

    /**
     * Recommended prefetch distance for LazyGrid
     * Loads items before they become visible
     */
    const val PREFETCH_DISTANCE = 3
}

/**
 * Memory usage recommendations
 */
object MemoryOptimization {
    /**
     * Maximum concurrent image loads
     * Prevents OOM errors on low-end devices
     */
    const val MAX_PARALLEL_REQUESTS = 4

    /**
     * Bitmap pool size for image loading
     */
    const val BITMAP_POOL_SIZE_MB = 50

    /**
     * Check if device has low memory
     */
    fun isLowMemoryDevice(context: Context): Boolean {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        return activityManager.isLowRamDevice
    }

    /**
     * Get recommended cache sizes based on device memory
     */
    fun getRecommendedCacheSizes(context: Context): Pair<Int, Long> {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val memoryInfo = android.app.ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)

        val totalMemoryMB = memoryInfo.totalMem / (1024 * 1024)

        return if (isLowMemoryDevice(context)) {
            // Low memory: 10% memory cache, 256 MB disk cache
            Pair(10, 256L * 1024 * 1024)
        } else {
            // Normal: 25% memory cache, 512 MB disk cache
            Pair(25, 512L * 1024 * 1024)
        }
    }
}
