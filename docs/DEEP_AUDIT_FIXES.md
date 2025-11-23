# Deep Audit Fixes - Implementation Guide

**Date:** 2025-11-22
**Status:** READY TO APPLY
**Priority:** CRITICAL (Pagination bug), MEDIUM (Others)

---

## FIX 1: CRITICAL - Pagination Bug (Broken Infinite Scrolling)

**File:** `app/src/main/java/com/example/farsilandtv/data/database/ContentDao.kt`
**Location:** Add after line 55 (after `fun getMoviesByGenre()`)
**Issue:** Filtering paged API data client-side breaks when full page has no matching genres

### Step 1: Add New DAO Method for Movies

Add this method to `CachedMovieDao` interface after line 55:

```kotlin
// DEEP AUDIT FIX: Multi-genre query with pagination to fix broken infinite scrolling
// Issue: Filtering paged API data client-side breaks when full page has no matches
// Solution: Database-only filtering with proper LIMIT/OFFSET pagination
// Supports up to 5 genres with OR logic (matches ANY selected genre)
// Callers MUST sanitize each genre with SqlSanitizer.sanitizeLikePattern()
@Query("""
    SELECT DISTINCT * FROM cached_movies
    WHERE genres LIKE '%' || :genre1 || '%' ESCAPE '\\'
       OR (:genre2 IS NOT NULL AND genres LIKE '%' || :genre2 || '%' ESCAPE '\\')
       OR (:genre3 IS NOT NULL AND genres LIKE '%' || :genre3 || '%' ESCAPE '\\')
       OR (:genre4 IS NOT NULL AND genres LIKE '%' || :genre4 || '%' ESCAPE '\\')
       OR (:genre5 IS NOT NULL AND genres LIKE '%' || :genre5 || '%' ESCAPE '\\')
    ORDER BY dateAdded DESC
    LIMIT :limit OFFSET :offset
""")
suspend fun getMoviesByGenresPaginated(
    genre1: String,
    genre2: String? = null,
    genre3: String? = null,
    genre4: String? = null,
    genre5: String? = null,
    limit: Int,
    offset: Int
): List<CachedMovie>
```

### Step 2: Add New DAO Method for Series

Add this method to `CachedSeriesDao` interface after line 141 (after `fun getSeriesByGenre()`):

```kotlin
// DEEP AUDIT FIX: Multi-genre query with pagination (same fix for series)
@Query("""
    SELECT DISTINCT * FROM cached_series
    WHERE genres LIKE '%' || :genre1 || '%' ESCAPE '\\'
       OR (:genre2 IS NOT NULL AND genres LIKE '%' || :genre2 || '%' ESCAPE '\\')
       OR (:genre3 IS NOT NULL AND genres LIKE '%' || :genre3 || '%' ESCAPE '\\')
       OR (:genre4 IS NOT NULL AND genres LIKE '%' || :genre4 || '%' ESCAPE '\\')
       OR (:genre5 IS NOT NULL AND genres LIKE '%' || :genre5 || '%' ESCAPE '\\')
    ORDER BY dateAdded DESC
    LIMIT :limit OFFSET :offset
""")
suspend fun getSeriesByGenresPaginated(
    genre1: String,
    genre2: String? = null,
    genre3: String? = null,
    genre4: String? = null,
    genre5: String? = null,
    limit: Int,
    offset: Int
): List<CachedSeries>
```

### Step 3: Update Repository to Use Database-Only Filtering

**File:** `app/src/main/java/com/example/farsilandtv/data/repository/ContentRepository.kt`
**Location:** Replace `getMoviesByGenres()` function (lines 1061-1110)

**REPLACE THIS:**
```kotlin
suspend fun getMoviesByGenres(genres: List<com.example.farsilandtv.data.model.Genre>, page: Int = 1): Result<List<Movie>> =
    withContext(Dispatchers.IO) {
        try {
            if (genres.isEmpty()) {
                return@withContext getMovies(page, perPage = 20)
            }

            // Try API first - fetch movies and filter by genre
            ensureActive()
            val allMovies = getMovies(page, perPage = 100).getOrNull() ?: emptyList()
            ensureActive()

            // Filter movies that contain ANY of the selected genres (OR logic)
            val genreNames = genres.map { it.englishName.lowercase() }
            val filteredMovies = allMovies.filter { movie ->
                movie.genres.any { movieGenre ->
                    genreNames.contains(movieGenre.lowercase())
                }
            }

            Result.success(filteredMovies)
        } catch (e: Exception) {
            // ... existing fallback code with N+1 query ...
        }
    }
```

**WITH THIS:**
```kotlin
/**
 * DEEP AUDIT FIX: Database-only filtering with proper pagination
 *
 * Previous Issue: Fetched 100 items from API, filtered client-side
 * Result: Page 2 might have 0 matches → infinite scroll breaks
 *
 * New Approach: Use database-only filtering with LIMIT/OFFSET
 * Why: Database is synced every 30min, has all content, supports proper pagination
 */
suspend fun getMoviesByGenres(
    genres: List<com.example.farsilandtv.data.model.Genre>,
    page: Int = 1,
    perPage: Int = 20
): Result<List<Movie>> = withContext(Dispatchers.IO) {
    try {
        if (genres.isEmpty()) {
            // No filter - use existing method
            return@withContext getMovies(page, perPage)
        }

        // DEEP AUDIT FIX: Use database-only filtering (no API calls)
        val genreNames = genres.map { it.englishName }
        val sanitizedGenres = genreNames.map { SqlSanitizer.sanitizeLikePattern(it) }

        // Pad genres list to 5 elements (DAO expects up to 5)
        val genre1 = sanitizedGenres.getOrNull(0) ?: return@withContext Result.success(emptyList())
        val genre2 = sanitizedGenres.getOrNull(1)
        val genre3 = sanitizedGenres.getOrNull(2)
        val genre4 = sanitizedGenres.getOrNull(3)
        val genre5 = sanitizedGenres.getOrNull(4)

        val offset = (page - 1) * perPage

        // Single database query with proper pagination
        val cachedMovies = getContentDb().movieDao().getMoviesByGenresPaginated(
            genre1 = genre1,
            genre2 = genre2,
            genre3 = genre3,
            genre4 = genre4,
            genre5 = genre5,
            limit = perPage,
            offset = offset
        )

        // Convert to Movie objects
        val movies = cachedMovies.map { it.toMovie() }
        Result.success(movies)
    } catch (e: Exception) {
        handleApiError("getMoviesByGenres(genres=${genres.size}, page=$page)", e)
    }
}
```

### Step 4: Update `getTvShowsByGenres()` the same way

Apply the same pattern to `getTvShowsByGenres()` function.

---

## FIX 2: N+1 Query Problem

**STATUS:** FIXED BY FIX 1 (Database-only filtering eliminates the loop)

The new implementation removes the N+1 query loop entirely by using a single SQL query with OR clauses.

---

## FIX 3: Delete Zombie Code

**File:** `app/src/main/java/com/example/farsilandtv/data/repository/ContentRepository.kt`
**Location:** Line 924
**Action:** DELETE ENTIRE FUNCTION

Delete this function:

```kotlin
/**
 * [DEPRECATED - Use searchCurrentDatabase() instead]
 * Search a specific database
 * WARNING: Creates new Room instance - very expensive (50-200ms I/O + memory allocation)
 */
private suspend fun searchDatabase(source: DatabaseSource, query: String): List<Any> {
    // ... entire function body ...
}
```

**Why:** Creates new Room database instances (extremely expensive). Marked deprecated but not deleted. Risk of accidental usage.

---

## FIX 4: Increase Scraper Timeout

**File:** `app/src/main/java/com/example/farsilandtv/data/scraper/VideoUrlScraper.kt`
**Location:** Line 460
**Change:** 3 seconds → 8 seconds

**BEFORE:**
```kotlin
val maxWaitMs = 3000L // 3 second timeout for fast UX
```

**AFTER:**
```kotlin
// DEEP AUDIT FIX: Increased from 3s to 8s for slow networks/VPN users
// Issue: 3s timeout caused "No Links Found" errors for legitimate slow connections
// Solution: 8s gives adequate time without excessive waiting
val maxWaitMs = 8000L // 8 second timeout (balances UX vs reliability)
```

---

## FIX 5: Fix Image Scaling for Backgrounds

**File:** `app/src/main/java/com/example/farsilandtv/utils/ImageLoader.kt`
**Location:** Lines 99-119
**Issue:** `load()` function uses `Scale.FIT` for backgrounds, causing letterboxing

### Solution: Add `scaleType` Parameter

**REPLACE THIS:**
```kotlin
fun load(
    context: Context,
    imageUrl: String?,
    imageView: ImageView
) {
    if (imageUrl.isNullOrEmpty()) {
        imageView.setImageDrawable(ContextCompat.getDrawable(context, R.drawable.image_placeholder))
        return
    }

    imageView.load(imageUrl, getImageLoader(context)) {
        crossfade(300)
        placeholder(R.drawable.image_placeholder)
        error(R.drawable.movie)
        size(480, 270)
        // AUDIT FIX #25: Changed from Scale.FILL to Scale.FIT to prevent aspect ratio distortion
        scale(Scale.FIT)
        memoryCachePolicy(CachePolicy.ENABLED)
        diskCachePolicy(CachePolicy.ENABLED)
    }
}
```

**WITH THIS:**
```kotlin
/**
 * Load image with configurable scaling
 *
 * DEEP AUDIT FIX: Added scaleType parameter
 * - Use Scale.FILL for backgrounds (fills screen, may crop)
 * - Use Scale.FIT for cards (fits inside, preserves aspect ratio)
 */
fun load(
    context: Context,
    imageUrl: String?,
    imageView: ImageView,
    scaleType: Scale = Scale.FIT  // Default to FIT for cards
) {
    if (imageUrl.isNullOrEmpty()) {
        imageView.setImageDrawable(ContextCompat.getDrawable(context, R.drawable.image_placeholder))
        return
    }

    imageView.load(imageUrl, getImageLoader(context)) {
        crossfade(300)
        placeholder(R.drawable.image_placeholder)
        error(R.drawable.movie)
        size(480, 270)
        // DEEP AUDIT FIX: Use parameterized scale type
        // Backgrounds should pass Scale.FILL, cards should pass Scale.FIT
        scale(scaleType)
        memoryCachePolicy(CachePolicy.ENABLED)
        diskCachePolicy(CachePolicy.ENABLED)
    }
}
```

### Update Callers:

**For backgrounds (DetailsActivity, SeriesDetailsActivity):**
```kotlin
// OLD
ImageLoader.load(this, backdropUrl, backdropImageView)

// NEW
ImageLoader.load(this, backdropUrl, backdropImageView, Scale.FILL)
```

**For cards (keep as is, uses default Scale.FIT):**
```kotlin
// No change needed - defaults to Scale.FIT
ImageLoader.load(context, posterUrl, imageView)
```

---

## FIX 6: Fix Focus Management (Race Condition)

**File:** `app/src/main/java/com/example/farsilandtv/utils/TvFocusOptimization.kt`
**Location:** Lines 49-63
**Issue:** Hardcoded `delay(100)` causes focus failures on slower devices

### Solution: Replace Delay with State-Driven Approach

**REPLACE THIS:**
```kotlin
fun Modifier.requestInitialFocus(
    focusRequester: FocusRequester,
    delayMillis: Long = 100
): Modifier = composed {
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(delayMillis)
        try {
            focusRequester.requestFocus()
        } catch (e: Exception) {
            Log.w("TvFocus", "Failed to request initial focus", e)
        }
    }

    this.focusRequester(focusRequester)
}
```

**WITH THIS:**
```kotlin
/**
 * DEEP AUDIT FIX: Replace delay with layout-ready detection
 *
 * Previous Issue: delay(100) assumed UI ready in 100ms
 * Result: Focus failed on slow devices (took >110ms to render)
 *
 * New Approach: Wait for onGloballyPositioned event (UI actually ready)
 */
fun Modifier.requestInitialFocus(
    focusRequester: FocusRequester
): Modifier = composed {
    var hasPositioned by remember { mutableStateOf(false) }

    LaunchedEffect(hasPositioned) {
        if (hasPositioned) {
            // UI is positioned and ready for focus
            try {
                focusRequester.requestFocus()
            } catch (e: Exception) {
                Log.w("TvFocus", "Failed to request initial focus", e)
            }
        }
    }

    this
        .focusRequester(focusRequester)
        .onGloballyPositioned {
            // UI is now positioned and ready
            if (!hasPositioned) {
                hasPositioned = true
            }
        }
}
```

---

## FIX 7: Make Skeleton Sizes Responsive

**File:** `app/src/main/java/com/example/farsilandtv/ui/components/SkeletonScreen.kt`
**Location:** Lines 66-86
**Issue:** Hardcoded 150x225dp causes layout shift when real content loads

### Solution: Add Size Parameters

**REPLACE THIS:**
```kotlin
@Composable
fun SkeletonCard(
    modifier: Modifier = Modifier
) {
    val shimmerOffset by rememberShimmerAnimation()

    Card(
        modifier = modifier
            .width(150.dp)
            .height(225.dp),
        ...
    )
}
```

**WITH THIS:**
```kotlin
/**
 * DEEP AUDIT FIX: Made dimensions configurable to match actual content
 *
 * Previous Issue: Hardcoded 150x225dp caused layout shift when data loaded
 * Solution: Accept width/height parameters to match real card sizes
 */
@Composable
fun SkeletonCard(
    modifier: Modifier = Modifier,
    width: Dp = 150.dp,  // Default matches movie card
    height: Dp = 225.dp  // Default matches movie card
) {
    val shimmerOffset by rememberShimmerAnimation()

    Card(
        modifier = modifier
            .width(width)
            .height(height),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(shimmerBrush(shimmerOffset))
        )
    }
}
```

### Update Callers:

If your actual movie cards are different sizes, pass the correct dimensions:

```kotlin
// Example: If your real cards are 160x240dp
SkeletonCard(width = 160.dp, height = 240.dp)
```

---

## VERIFICATION STEPS

After applying fixes:

1. **Compile Check:**
   ```bash
   ./gradlew compileDebugKotlin
   ```

2. **Run Tests:**
   ```bash
   ./gradlew test
   ```

3. **Test Pagination:**
   - Filter by genre (e.g., "Action")
   - Scroll to end of page 1
   - Verify page 2 loads correctly (no empty list)

4. **Test Scraper:**
   - Play video on slow network
   - Verify links load within 8s (not 3s timeout)

5. **Test Image Scaling:**
   - Open movie details
   - Verify background fills screen (no black bars)

6. **Test Focus:**
   - Navigate with D-pad on slow emulator
   - Verify focus cursor appears immediately when UI loads

---

## PRIORITY SUMMARY

**MUST FIX (Critical):**
1. ✅ Pagination Bug (Fix 1) - Breaks infinite scrolling
2. ✅ Delete Zombie Code (Fix 3) - Prevents accidental usage

**SHOULD FIX (Medium):**
3. ✅ Increase Scraper Timeout (Fix 4) - Improves reliability
4. ✅ Fix Image Scaling (Fix 5) - Better UX
5. ✅ Fix Focus Management (Fix 6) - Works on all devices

**NICE TO HAVE (Low):**
6. ✅ Make Skeleton Sizes Responsive (Fix 7) - Smoother loading

---

## FILES TO MODIFY

1. `app/src/main/java/com/example/farsilandtv/data/database/ContentDao.kt`
2. `app/src/main/java/com/example/farsilandtv/data/repository/ContentRepository.kt`
3. `app/src/main/java/com/example/farsilandtv/data/scraper/VideoUrlScraper.kt`
4. `app/src/main/java/com/example/farsilandtv/utils/ImageLoader.kt`
5. `app/src/main/java/com/example/farsilandtv/utils/TvFocusOptimization.kt`
6. `app/src/main/java/com/example/farsilandtv/ui/components/SkeletonScreen.kt`

**Estimated Time:** 30-45 minutes
**Risk Level:** LOW (All fixes are localized, no architectural changes)

---

## ROLLBACK PLAN

If issues occur:
1. Run `git diff` to see changes
2. Run `git checkout <file>` to revert specific file
3. Run `./gradlew clean assembleDebug` to rebuild
4. Test on emulator before deploying

