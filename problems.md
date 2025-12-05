# FarsiPlex Code Review - Bugs & Issues

**Review Date:** 2025-12-04
**Total Issues Found:** 23
**Status:** Pending Fixes

---

## Summary by Severity

| Severity | Count | Status |
|----------|-------|--------|
| Critical | 6 | Pending |
| High | 7 | Pending |
| Medium | 7 | Pending |
| Low | 3 | Pending |

---

## Critical Issues (6)

### C1: SSL Certificate Bypass - VideoPlayerActivity

**File:** `app/src/main/java/com/example/farsilandtv/VideoPlayerActivity.kt`
**Lines:** 1002-1013

**Problem:** `trustAllCerts` array accepts ALL certificates, completely bypassing SSL validation. This allows Man-in-the-Middle attacks.

```kotlin
private val trustAllCerts = arrayOf<TrustManager>(
    object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
        override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
    }
)
```

**Risk:** MITM attacks can intercept video streams and inject malicious content.

**Fix:** Remove `trustAllCerts`. Use proper certificate pinning or system trust store.

---

### C2: SSL Error Bypass with Weak Domain Check - IMVBoxWebPlayerActivity

**File:** `app/src/main/java/com/example/farsilandtv/IMVBoxWebPlayerActivity.kt`
**Lines:** 843-857

**Problem:** SSL errors are bypassed if URL contains "imvbox.com" - easily spoofed.

```kotlin
override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
    val url = error?.url ?: ""
    if (url.contains("imvbox.com")) {
        handler?.proceed()  // Dangerous!
    } else {
        handler?.cancel()
    }
}
```

**Risk:** Attacker can use domain like `malicious.com/imvbox.com/` to bypass SSL.

**Fix:** Use proper hostname verification with `URL(url).host.endsWith(".imvbox.com")`.

---

### C3: Lifecycle Race Condition - VideoPlayerActivity

**File:** `app/src/main/java/com/example/farsilandtv/VideoPlayerActivity.kt`
**Lines:** 1454-1476

**Problem:** `saveCurrentPosition()` uses `lifecycleScope.launch` without checking if activity is destroyed.

```kotlin
private fun saveCurrentPosition() {
    lifecycleScope.launch {  // Can crash if activity is destroyed
        player?.let { exoPlayer ->
            val position = exoPlayer.currentPosition
            // ... save to database
        }
    }
}
```

**Risk:** `IllegalStateException` crash when activity is destroyed during save.

**Fix:** Check `lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)` before launch.

---

### C4: Handler Memory Leak - IMVBoxWebPlayerActivity

**File:** `app/src/main/java/com/example/farsilandtv/IMVBoxWebPlayerActivity.kt`
**Lines:** 812-825

**Problem:** Handler with delayed runnable posted in `onPageFinished` not cancelled on destroy.

```kotlin
override fun onPageFinished(view: WebView?, url: String?) {
    Handler(Looper.getMainLooper()).postDelayed({
        setupAutoSkip()  // References activity
    }, 3000)
}
```

**Risk:** Memory leak - activity kept alive by Handler reference.

**Fix:** Store Handler reference and call `handler.removeCallbacksAndMessages(null)` in `onDestroy()`.

---

### C5: Mixed Content Always Allowed - IMVBoxWebPlayerActivity

**File:** `app/src/main/java/com/example/farsilandtv/IMVBoxWebPlayerActivity.kt`
**Line:** 784

**Problem:** WebView allows mixed HTTP/HTTPS content unconditionally.

```kotlin
settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
```

**Risk:** HTTP resources can be intercepted/modified by attackers.

**Fix:** Use `MIXED_CONTENT_COMPATIBILITY_MODE` or `MIXED_CONTENT_NEVER_ALLOW`.

---

### C6: Aggressive Cache Clear on Error - VideoPlayerActivity

**File:** `app/src/main/java/com/example/farsilandtv/VideoPlayerActivity.kt`
**Lines:** 722-732

**Problem:** Entire video cache cleared on ANY playback error.

```kotlin
private fun handlePlaybackError(error: PlaybackException) {
    SimpleCache.getInstance(this).release()  // Clears ALL cached videos
    // ...
}
```

**Risk:** User loses all downloaded content on single playback error.

**Fix:** Only clear cache for the specific failing URL, not entire cache.

---

## High Priority Issues (7)

### H1: Non-Thread-Safe Pagination Flags - MainViewModel

**File:** `app/src/main/java/com/example/farsilandtv/ui/viewmodel/MainViewModel.kt`
**Lines:** 128-130

**Problem:** Boolean flags accessed from multiple threads without synchronization.

```kotlin
private var isLoadingMoreMovies = false
private var isLoadingMoreSeries = false
private var hasMoreMovies = true
```

**Risk:** Race condition can cause duplicate data loads or missed loads.

**Fix:** Use `AtomicBoolean` or `@Volatile` annotation.

---

### H2: LiveData Recreation on Each Call - MainViewModel

**File:** `app/src/main/java/com/example/farsilandtv/ui/viewmodel/MainViewModel.kt`
**Lines:** 625-638

**Problem:** `loadMoviesByGenre()` creates new `MutableLiveData` on each call.

```kotlin
fun loadMoviesByGenre(genreId: Int): LiveData<List<Movie>> {
    val result = MutableLiveData<List<Movie>>()  // New instance each call!
    viewModelScope.launch {
        // ...
    }
    return result
}
```

**Risk:** Multiple observers accumulate, memory waste, inconsistent state.

**Fix:** Cache LiveData per genre in a map, return existing if available.

---

### H3: Skip Button Polling Every Second - IMVBoxWebPlayerActivity

**File:** `app/src/main/java/com/example/farsilandtv/IMVBoxWebPlayerActivity.kt`
**Lines:** 115-126

**Problem:** JavaScript polling runs every 1 second indefinitely.

```kotlin
private fun setupAutoSkip() {
    webView.evaluateJavascript("""
        setInterval(function() {
            var skipBtn = document.querySelector('.ytp-ad-skip-button');
            if (skipBtn) skipBtn.click();
        }, 1000);
    """, null)
}
```

**Risk:** Battery drain, performance impact on lower-end devices.

**Fix:** Use MutationObserver instead of setInterval, or increase interval to 3-5 seconds.

---

### H4: Database Instance Created in Worker - ContentSyncWorker

**File:** `app/src/main/java/com/example/farsilandtv/data/sync/ContentSyncWorker.kt`
**Lines:** 56-57

**Problem:** Worker creates new database instance instead of using Hilt injection.

```kotlin
private val contentDb = ContentDatabase.getInstance(applicationContext)
private val appDb = AppDatabase.getInstance(applicationContext)
```

**Risk:** Multiple database connections, potential deadlocks, inconsistent with Hilt architecture.

**Fix:** Use `@HiltWorker` and `@AssistedInject` for proper DI.

---

### H5: Genre Cache Not Cleared Between Syncs - ContentSyncWorker

**File:** `app/src/main/java/com/example/farsilandtv/data/sync/ContentSyncWorker.kt`
**Line:** 62

**Problem:** `genreCache` map accumulates entries across syncs.

```kotlin
private val genreCache = mutableMapOf<String, Int>()  // Never cleared
```

**Risk:** Memory growth over time, stale data.

**Fix:** Clear cache at start of `doWork()` or use scoped cache.

---

### H6: No Timeout on Similar Movies Load - DetailsActivity

**File:** `app/src/main/java/com/example/farsilandtv/DetailsActivity.kt`
**Lines:** 103-122

**Problem:** Network call for similar movies has no timeout.

```kotlin
lifecycleScope.launch {
    val similar = contentRepository.getSimilarMovies(movieId)  // No timeout
    // ...
}
```

**Risk:** ANR if network is slow, UI frozen indefinitely.

**Fix:** Use `withTimeout(10_000)` or configure OkHttp timeout.

---

### H7: Overly Broad Exception Catching - HomeComposeFragment

**File:** `app/src/main/java/com/example/farsilandtv/HomeComposeFragment.kt`
**Lines:** 87-93

**Problem:** Catches `IllegalStateException` too broadly.

```kotlin
try {
    // UI setup
} catch (e: IllegalStateException) {
    // Swallows important errors
}
```

**Risk:** Real bugs hidden, debugging difficulty.

**Fix:** Catch specific exceptions or log with Crashlytics.

---

## Medium Priority Issues (7)

### M1: Regex Created Inside Loop - ContentSyncWorker

**File:** `app/src/main/java/com/example/farsilandtv/data/sync/ContentSyncWorker.kt`
**Lines:** 822-824

**Problem:** Regex pattern compiled inside loop iteration.

```kotlin
for (item in items) {
    val yearPattern = Regex("\\((\\d{4})\\)")  // Compiled each iteration
    // ...
}
```

**Risk:** Performance degradation with large item lists.

**Fix:** Move regex to companion object as `private val`.

---

### M2: Hardcoded Sync Interval - ContentSyncWorker

**File:** `app/src/main/java/com/example/farsilandtv/data/sync/ContentSyncWorker.kt`
**Lines:** 45-48

**Problem:** Sync interval hardcoded, no user configuration.

```kotlin
companion object {
    const val SYNC_INTERVAL_MINUTES = 30L
}
```

**Risk:** Users can't adjust sync frequency for battery/data savings.

**Fix:** Read from SharedPreferences with sensible default.

---

### M3: IMVBox Missing from Trusted Domains - SecureUrlValidator

**File:** `app/src/main/java/com/example/farsilandtv/utils/SecureUrlValidator.kt`
**Location:** DEFAULT_TRUSTED_DOMAINS list

**Problem:** `imvbox.com` not in trusted domains but used throughout app.

**Risk:** IMVBox URLs may fail validation in some code paths.

**Fix:** Add `"imvbox.com"` to DEFAULT_TRUSTED_DOMAINS.

---

### M4: Position Save Frequency Too High - VideoPlayerActivity

**File:** `app/src/main/java/com/example/farsilandtv/VideoPlayerActivity.kt`
**Lines:** 890-905

**Problem:** Position saved every 5 seconds, even when paused.

**Risk:** Unnecessary database writes, battery drain.

**Fix:** Only save on state changes (pause, seek) or every 15-30 seconds during playback.

---

### M5: No Disk Space Check Before Download

**File:** `app/src/main/java/com/example/farsilandtv/data/download/DownloadManager.kt`

**Problem:** Downloads start without checking available disk space.

**Risk:** Download fails mid-way, wasted bandwidth, corrupted partial files.

**Fix:** Check `StatFs.getAvailableBytes()` before starting download.

---

### M6: Cast Session Listener Leak - CastManager

**File:** `app/src/main/java/com/example/farsilandtv/cast/CastManager.kt`

**Problem:** Session listeners may not be removed on all code paths.

**Risk:** Memory leak, stale callbacks.

**Fix:** Ensure `removeSessionManagerListener()` called in all cleanup paths.

---

### M7: Network Callback Not Unregistered - NetworkUtils

**File:** `app/src/main/java/com/example/farsilandtv/utils/NetworkUtils.kt`

**Problem:** `ConnectivityManager.NetworkCallback` registered but may not be unregistered.

**Risk:** Memory leak, system resource waste.

**Fix:** Call `unregisterNetworkCallback()` when monitoring stops.

---

## Low Priority Issues (3)

### L1: Test Mocks Don't Verify DAO Calls - WatchlistRepositoryTest

**File:** `app/src/test/java/com/example/farsilandtv/data/repository/WatchlistRepositoryTest.kt`

**Problem:** Tests create mock data but don't verify actual DAO interactions.

```kotlin
@Test
fun `test addMovieToWatchlist creates entry with isInWatchlist true`() = runTest {
    val expectedMovie = WatchlistMovie(...)
    assertTrue(expectedMovie.isInWatchlist)  // Only verifies object creation
    // Missing: verify(mockDao).insert(expectedMovie)
}
```

**Risk:** Tests pass but actual database operations may fail.

**Fix:** Add Mockito `verify()` calls or use in-memory Room database.

---

### L2: Unused Imports in Test Files

**File:** `app/src/test/java/com/example/farsilandtv/utils/SecureUrlValidatorTest.kt`
**Lines:** 6-7

**Problem:** Unused imports present.

```kotlin
import kotlin.test.assertNotNull  // Used
import kotlin.test.assertNull     // Used
```

**Risk:** Code cleanliness, minor.

**Fix:** Run IDE "Optimize Imports" action.

---

### L3: Ghost Record Cleanup Threshold Hardcoded

**File:** `app/src/main/java/com/example/farsilandtv/data/sync/ContentSyncWorker.kt`
**Lines:** 343-392

**Problem:** Ghost cleanup uses hardcoded 7-day threshold.

```kotlin
private suspend fun cleanupGhostRecords() {
    val threshold = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000)
    // ...
}
```

**Risk:** Not configurable, may not suit all use cases.

**Fix:** Make threshold configurable via SyncPreferences.

---

## Recommended Fix Priority

1. **Immediate (Security):** C1, C2, C5
2. **Next Sprint:** C3, C4, C6, H1, H4
3. **Following Sprint:** H2, H3, H5, H6, H7
4. **Backlog:** M1-M7, L1-L3

---

## Notes

- All line numbers reference code as of 2025-12-04
- Some issues may require architectural changes
- Test coverage should be added for fixed issues
- Consider security audit after C1, C2, C5 fixes
