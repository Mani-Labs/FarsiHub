# External Audit #4 - Remediation Plan
**Date**: November 20, 2025
**Validated Issues**: 7 requiring fixes
**Status**: READY FOR IMPLEMENTATION

---

## Remediation Priority Matrix

| Priority | Issue ID | Description | Impact | Effort |
|----------|----------|-------------|--------|--------|
| **HIGH** | C2.2 | Missing onNewIntent() for singleTop | User frustration, ignored video requests | 30 min |
| **HIGH** | C2.4 | Polling loop in VideoUrlScraper | Battery drain, ANR risk | 2 hours |
| **MEDIUM** | C2.3 | No process death state saving | Lost playback position | 1 hour |
| **MEDIUM** | C4.3 | Network callback registration leak | Potential IllegalArgumentException | 30 min |
| **LOW** | C2.5 | Error responses cached for 10 min | User cannot refresh on error | 30 min |
| **LOW** | C3.4 | Regex compiled in loop | Minor performance hit | 15 min |
| **LOW** | C4.2 | Temp directory fallback unsafe | Cache creation may fail | 30 min |

---

## HIGH PRIORITY FIXES

### Fix C2.2: Implement onNewIntent() for SingleTop Launch Mode

**File**: `VideoPlayerActivity.kt`
**Location**: After `onCreate()`
**Impact**: Critical user experience issue

**Implementation**:
```kotlin
/**
 * Handle new intents when Activity is already running (singleTop launch mode)
 * EXTERNAL AUDIT FIX C2.2: Prevents ignoring video requests when player is open
 */
override fun onNewIntent(intent: Intent?) {
    super.onNewIntent(intent)

    if (intent == null) return

    // Update the activity's intent
    setIntent(intent)

    Log.d(TAG, "onNewIntent() - New video request received")

    // Save current position before switching videos
    saveCurrentPosition()

    // Stop current playback
    player?.stop()

    // Re-extract intent data
    contentType = intent.getStringExtra("CONTENT_TYPE") ?: "movie"
    contentId = intent.getIntExtra("CONTENT_ID", 0)
    contentTitle = intent.getStringExtra("CONTENT_TITLE") ?: "Unknown"
    contentUrl = intent.getStringExtra("CONTENT_URL") ?: ""
    contentPosterUrl = intent.getStringExtra("CONTENT_POSTER_URL")

    if (contentType == "episode") {
        seriesId = intent.getIntExtra("SERIES_ID", 0)
        seasonNumber = intent.getIntExtra("EPISODE_SEASON", 0)
        episodeNumber = intent.getIntExtra("EPISODE_NUMBER", 0)
    }

    // Validate required data
    if (contentUrl.isEmpty() || contentId == 0) {
        Toast.makeText(this, "Error: Invalid video request", Toast.LENGTH_LONG).show()
        return
    }

    // Check if quality was pre-selected
    val selectedVideoUrl = intent.getStringExtra("SELECTED_VIDEO_URL")
    val selectedQuality = intent.getStringExtra("SELECTED_VIDEO_QUALITY")

    if (selectedVideoUrl != null && selectedQuality != null) {
        currentVideoUrl = selectedVideoUrl
        availableQualities = listOf(
            VideoUrl(
                url = selectedVideoUrl,
                quality = selectedQuality,
                fileSizeMb = null,
                mirror = null
            )
        )
        currentQualityIndex = 0
        loadSavedPosition(selectedVideoUrl)
    } else {
        // Fetch new video URLs
        fetchVideoUrlsAndPlay()
    }
}
```

**Testing**:
1. Start playing a video
2. Press Home button
3. Click another video from notification/home screen
4. Verify new video starts playing

---

### Fix C2.4: Replace Polling Loop with Channel Pattern

**File**: `VideoUrlScraper.kt`
**Location**: Lines 306-361 (extractFromDooPlayAPI method)
**Impact**: Battery drain, ANR risk on slow networks

**Current Code (PROBLEMATIC)**:
```kotlin
// Polling loop - wakes CPU every 50ms
while (!foundResult && jobs.any { it.isActive }) {
    for (job in jobs) {
        if (job.isCompleted && !job.isCancelled) {
            // ... process ...
        }
    }
    kotlinx.coroutines.delay(50) // BAD: CPU wake every 50ms
}
```

**New Implementation**:
```kotlin
/**
 * Extract video URLs using DooPlay REST API with Channel-based first-wins pattern
 * EXTERNAL AUDIT FIX C2.4: Replaced polling loop with Channel for better performance
 */
private suspend fun extractFromDooPlayAPI(doc: Document, pageUrl: String): List<VideoUrl> {
    try {
        // Extract post ID
        val postIdInput = doc.select("input[name=id]").firstOrNull() ?: return emptyList()
        val postId = postIdInput.attr("value")
        if (postId.isEmpty()) return emptyList()

        // Determine content type
        val contentType = when {
            pageUrl.contains("/movie/", ignoreCase = true) -> "movie"
            pageUrl.contains("/tvshow/", ignoreCase = true) -> "tv"
            pageUrl.contains("/episode/", ignoreCase = true) -> "tv"
            else -> "movie"
        }

        android.util.Log.d(TAG, "Content type: $contentType, post ID: $postId")

        // Channel-based first-wins pattern (no polling!)
        return coroutineScope {
            val resultChannel = Channel<List<VideoUrl>>(Channel.RENDEZVOUS)

            // Launch 5 concurrent API requests
            val jobs = (1..5).map { num ->
                launch {
                    try {
                        val apiUrl = "https://farsiplex.com/wp-json/dooplayer/v2/$postId/$contentType/$num"
                        android.util.Log.d(TAG, "Trying API: $apiUrl")
                        val urls = fetchFromDooPlayAPI(apiUrl, num)

                        if (urls.isNotEmpty()) {
                            // Send result to channel (first sender wins)
                            resultChannel.trySend(urls)
                            android.util.Log.d(TAG, "Server $num completed first with ${urls.size} URLs")
                        }
                    } catch (e: Exception) {
                        android.util.Log.d(TAG, "Server $num failed: ${e.message}")
                    }
                }
            }

            try {
                // Wait for first successful result (blocks until one completes)
                val firstResult = withTimeout(10_000) { // 10 second timeout
                    resultChannel.receive()
                }

                // Cancel all remaining jobs
                jobs.forEach { it.cancel() }

                android.util.Log.d(TAG, "First-wins completed, found ${firstResult.size} URLs")

                // Remove duplicates and sort by quality
                firstResult.distinctBy { it.url }.sortedWith(
                    compareByDescending {
                        when (it.quality) {
                            "1080p" -> 3
                            "720p" -> 2
                            "480p" -> 1
                            else -> 0
                        }
                    }
                )
            } catch (e: TimeoutCancellationException) {
                android.util.Log.w(TAG, "All servers timed out after 10 seconds")
                jobs.forEach { it.cancel() }
                emptyList()
            } finally {
                resultChannel.close()
            }
        }
    } catch (e: Exception) {
        android.util.Log.e(TAG, "Error extracting from DooPlay API", e)
        return emptyList()
    }
}
```

**Benefits**:
- ✅ No CPU polling (event-driven)
- ✅ Instant cancellation of slow servers
- ✅ 10-second timeout prevents ANR
- ✅ Zero battery drain from polling

**Testing**:
1. Load a FarsiPlex video
2. Monitor battery usage (should drop significantly)
3. Test with slow network (enable airplane mode mid-request)
4. Verify 10-second timeout works

---

## MEDIUM PRIORITY FIXES

### Fix C2.3: Implement Process Death State Saving

**File**: `VideoPlayerActivity.kt`
**Location**: After `onStart()` method
**Impact**: User loses playback position on process death

**Implementation**:
```kotlin
/**
 * EXTERNAL AUDIT FIX C2.3: Save state to Bundle for process death recovery
 */
override fun onSaveInstanceState(outState: Bundle) {
    super.onSaveInstanceState(outState)

    player?.let {
        outState.putLong("playback_position", it.currentPosition)
        outState.putString("video_url", currentVideoUrl)
        outState.putBoolean("was_playing", it.isPlaying)
        outState.putInt("quality_index", currentQualityIndex)
    }

    // Save content metadata
    outState.putInt("content_id", contentId)
    outState.putString("content_type", contentType)
    outState.putString("content_title", contentTitle)
    outState.putString("content_url", contentUrl)

    Log.d(TAG, "State saved to Bundle for process death recovery")
}

/**
 * Restore state from Bundle after process death
 */
private fun restoreStateFromBundle(savedInstanceState: Bundle) {
    val position = savedInstanceState.getLong("playback_position", 0L)
    val videoUrl = savedInstanceState.getString("video_url", "")
    val wasPlaying = savedInstanceState.getBoolean("was_playing", false)
    val qualityIndex = savedInstanceState.getInt("quality_index", 0)

    if (videoUrl.isNotEmpty() && position > 0) {
        Log.d(TAG, "Restoring playback from Bundle: position=${position}ms, url=$videoUrl")

        savedPlaybackState = PlaybackState(
            position = position,
            videoUrl = videoUrl,
            wasPlaying = wasPlaying
        )
        currentQualityIndex = qualityIndex
    }
}
```

**Update `onCreate()`**:
```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    // ... existing code ...

    // Restore state from Bundle if process was killed
    if (savedInstanceState != null) {
        restoreStateFromBundle(savedInstanceState)
    }

    // ... rest of onCreate ...
}
```

**Testing**:
1. Start playing a video, seek to middle
2. Put app in background
3. Use `adb shell am kill com.example.farsilandtv` to kill process
4. Return to app
5. Verify playback resumes from correct position

---

### Fix C4.3: Network Callback Registration Leak

**File**: `VideoPlayerActivity.kt`
**Location**: Lines 57-60, 268-273, 940-947

**Implementation**:
```kotlin
// Add registration flag at top of class (after line 57)
private var isNetworkCallbackRegistered = false

/**
 * EXTERNAL AUDIT FIX C4.3: Track registration status to prevent leak
 */
private fun registerNetworkCallback() {
    networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onLost(network: Network) {
            runOnUiThread {
                player?.pause()
                Toast.makeText(
                    this@VideoPlayerActivity,
                    R.string.network_connection_lost,
                    Toast.LENGTH_LONG
                ).show()
                Log.w(TAG, "Network connection lost during playback")
            }
        }

        override fun onAvailable(network: Network) {
            runOnUiThread {
                Toast.makeText(
                    this@VideoPlayerActivity,
                    R.string.network_connection_restored,
                    Toast.LENGTH_SHORT
                ).show()
                Log.d(TAG, "Network connection restored")
            }
        }
    }

    try {
        connectivityManager.registerDefaultNetworkCallback(networkCallback!!)
        isNetworkCallbackRegistered = true  // Mark as successfully registered
        Log.d(TAG, "Network callback registered successfully")
    } catch (e: Exception) {
        isNetworkCallbackRegistered = false  // Mark as failed
        networkCallback = null  // Clear callback since registration failed
        Log.e(TAG, "Failed to register network callback", e)
    }
}

/**
 * Updated unregister with registration check
 */
override fun onDestroy() {
    super.onDestroy()

    // ... existing code ...

    // EXTERNAL AUDIT FIX C4.3: Only unregister if actually registered
    if (isNetworkCallbackRegistered && networkCallback != null) {
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback!!)
            Log.d(TAG, "Network callback unregistered")
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering network callback", e)
        }
    }
    networkCallback = null
    isNetworkCallbackRegistered = false
}
```

**Testing**:
1. Simulate registration failure (modify code to throw exception)
2. Verify no crash on `onDestroy()`
3. Normal case: verify callback works and unregisters cleanly

---

## LOW PRIORITY FIXES

### Fix C2.5: Validate HTTP Status Before Caching

**File**: `RetrofitClient.kt`
**Location**: Lines 168-181

**Implementation**:
```kotlin
// EXTERNAL AUDIT FIX C2.5: Only cache successful responses (200 OK)
if (isVideoEndpoint) {
    // Respect server's Cache-Control for video endpoints
    android.util.Log.d("HTTP_CACHE", "Video endpoint detected - respecting server cache headers")
    response
} else {
    // AUDIT FIX C2.5: Only cache if response is successful (200 OK)
    if (response.code == 200) {
        // Override server's Cache-Control to cache for 10 minutes
        response.newBuilder()
            .removeHeader("Pragma")
            .removeHeader("Cache-Control")
            .header("Cache-Control", "public, max-age=600") // 10 minutes
            .build()
    } else {
        // Don't cache error responses (4xx, 5xx) or other status codes
        android.util.Log.d("HTTP_CACHE", "Skipping cache for non-200 response: ${response.code}")
        response.newBuilder()
            .removeHeader("Pragma")
            .removeHeader("Cache-Control")
            .header("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0")
            .build()
    }
}
```

---

### Fix C3.4: Move Regex to Companion Object

**File**: `ContentRepository.kt`
**Location**: Lines 731-736

**Implementation**:
```kotlin
companion object {
    private const val TAG = "ContentRepository"
    private const val CACHE_TTL_MS = 30_000L // 30 seconds

    // EXTERNAL AUDIT FIX C3.4: Pre-compiled Regex for title normalization
    private val TITLE_NORMALIZER_REGEX = Regex("[^\\p{L}\\p{N}]")
}

/**
 * AUDIT FIX #7: Enhanced title normalization (now uses pre-compiled Regex)
 */
fun normalizeTitle(title: String): String {
    return title.replace(TITLE_NORMALIZER_REGEX, "")
        .lowercase()
}
```

---

### Fix C4.2: Remove Temp Directory Fallback

**File**: `RetrofitClient.kt`
**Location**: Lines 72-84

**Implementation**:
```kotlin
private fun getOrCreateCache(context: android.content.Context? = null): Cache {
    httpCache?.let { return it }

    synchronized(this) {
        httpCache?.let { return it }

        // EXTERNAL AUDIT FIX C4.2: Always require context, remove temp directory fallback
        val appContext = context?.applicationContext
            ?: FarsilandApp.instance?.applicationContext
            ?: throw IllegalStateException(
                "Cannot create HTTP cache: Application context not available. " +
                "Ensure RetrofitClient is initialized after Application.onCreate()"
            )

        // Always use app cache directory (Android 10+ compatible)
        val cacheDir = File(appContext.cacheDir, "http_cache")
        val cacheSize = 10L * 1024 * 1024 // 10 MB
        httpCache = Cache(cacheDir, cacheSize)

        return httpCache!!
    }
}
```

**Note**: This throws an exception if context is unavailable, forcing proper initialization order. The temp directory fallback was unsafe on Android 10+.

---

## Testing Checklist

### Pre-Fix Testing (Baseline)
- [ ] Record playback position save success rate
- [ ] Measure VideoUrlScraper battery usage (30 min test)
- [ ] Test process death recovery (manual kill)
- [ ] Test network callback lifecycle
- [ ] Test error response caching behavior

### Post-Fix Testing (Validation)
- [ ] onNewIntent works with singleTop launch mode
- [ ] VideoUrlScraper battery usage reduced by >80%
- [ ] Process death recovers playback position
- [ ] Network callback doesn't crash on failed registration
- [ ] Error responses not cached (503, 404, etc.)
- [ ] Regex performance improved (measure with profiler)
- [ ] HTTP cache always uses app cache directory

### Regression Testing
- [ ] Video playback works (all sources)
- [ ] Quality switching works
- [ ] Search functionality works
- [ ] No new crashes introduced
- [ ] Build succeeds with no errors

---

## Implementation Timeline

**Total Estimated Time**: 4-6 hours
**Recommended Approach**: Fix in priority order

### Session 1 (2 hours): HIGH PRIORITY
1. C2.2: onNewIntent implementation (30 min)
2. C2.4: Channel-based first-wins pattern (2 hours)
3. Test both fixes (30 min)

### Session 2 (2 hours): MEDIUM PRIORITY
1. C2.3: Process death state saving (1 hour)
2. C4.3: Network callback flag (30 min)
3. Test both fixes (30 min)

### Session 3 (1-2 hours): LOW PRIORITY
1. C2.5: HTTP status check (30 min)
2. C3.4: Regex companion object (15 min)
3. C4.2: Remove temp directory (30 min)
4. Full regression testing (1 hour)

---

## Deployment Strategy

1. **Create feature branch**: `fix/external-audit-4-remediation`
2. **Commit after each fix** with reference to issue ID (e.g., "Fix C2.2: Implement onNewIntent for singleTop")
3. **Run build after each session**: `.\gradlew.bat compileDebugKotlin`
4. **Test on device** after each priority tier
5. **Merge to main** when all tests pass
6. **Create production build** for validation

---

## Success Criteria

- ✅ All 7 validated issues resolved
- ✅ No regressions introduced
- ✅ Build succeeds with zero errors
- ✅ All tests pass
- ✅ Battery usage reduced by >80% (VideoUrlScraper)
- ✅ Process death recovery works 100%
- ✅ Documentation updated

**Ready to start remediation? Begin with HIGH PRIORITY fixes.**
