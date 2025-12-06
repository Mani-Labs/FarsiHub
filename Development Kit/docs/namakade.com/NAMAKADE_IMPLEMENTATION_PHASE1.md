# NAMAKADE.COM INTEGRATION - PHASE 1 COMPLETE

**Date:** 2025-10-29
**Status:** ✅ PHASE 1 COMPLETED - HTML Scraper Foundation Ready
**Build Status:** ✅ Compiles Successfully
**Testing Status:** ✅ Verified with Live Website

---

## IMPLEMENTATION SUMMARY

### Phase 1: HTML Scraper Foundation (COMPLETED)

This phase implements the core HTML scraping infrastructure required to extract content from namakade.com.

#### Files Created

1. **NamakadeUrlBuilder.kt** (`app/src/main/java/com/example/farsilandtv/data/namakade/`)
   - Utility class for building all namakade.com URLs
   - CDN URL patterns for video files
   - Web page URL patterns for scraping
   - Slug formatting functions

2. **NamakadeHtmlParser.kt** (`app/src/main/java/com/example/farsilandtv/data/namakade/`)
   - Jsoup-based HTML parser
   - Parses series cards from category pages
   - Parses episode lists from series pages
   - Extracts video URLs from episode pages
   - Extracts metadata (genres, descriptions, etc.)

3. **NamakadeApiService.kt** (`app/src/main/java/com/example/farsilandtv/data/namakade/`)
   - Main API service for namakade.com content
   - Rate limiting (500ms delay between requests)
   - HTTP client integration (uses existing RetrofitClient)
   - Video URL extraction with fallback generation
   - Video URL verification (HEAD requests)

---

## TESTING RESULTS

### ✅ Live Website Verification

All scraping functionality was tested against the live namakade.com website:

#### 1. Series List Scraping
- **URL Tested:** `https://namakade.com/best-serial`
- **Result:** ✅ SUCCESS
- **Data Extracted:**
  - Series titles (e.g., "Algoritm", "Az Yad Rafteh", "Bamdaade Khomaar")
  - Series slugs (e.g., "algoritm", "bamdaade-khomaar")
  - Episode counts (e.g., 14, 16, 3)
  - Genres (e.g., "اکشن, درام", "درام")

#### 2. Episode List Scraping
- **URL Tested:** `https://namakade.com/series/algoritm`
- **Result:** ✅ SUCCESS
- **Data Extracted:**
  - 14 episodes found
  - Episode numbers: 1-14
  - Episode slugs (e.g., "algoritm", "algoritm-", "algoritm-2")
  - Season information: Season 1

#### 3. Video URL Extraction
- **URL Tested:** `https://namakade.com/series/algoritm/episodes/algoritm`
- **Result:** ✅ SUCCESS
- **Video URL Extracted:**
  ```
  https://media.negahestan.com/ipnx/media/series/episodes/Algoritm_01.mp4
  ```
- **Extraction Method:** `<video><source src="">` tag
- **URL Pattern Match:** ✅ Matches expected CDN pattern

---

## CODE ARCHITECTURE

### URL Building Strategy

```kotlin
// Example: Building episode video URL
val url = NamakadeUrlBuilder.buildEpisodeUrlFromSlug("algoritm", 1)
// Returns: https://media.negahestan.com/ipnx/media/series/episodes/Algoritm_01.mp4

// Example: Building series page URL
val pageUrl = NamakadeUrlBuilder.buildSeriesPageUrl("algoritm")
// Returns: https://namakade.com/serieses/algoritm
```

### Scraping Flow

```
1. Category Page (Series List)
   ├─> NamakadeApiService.getSeriesFromCategory("best-serial")
   ├─> Fetches HTML
   ├─> NamakadeHtmlParser.parseSeriesCards(doc)
   └─> Returns List<Series>

2. Series Page (Episode List)
   ├─> NamakadeApiService.getEpisodes("algoritm")
   ├─> Fetches HTML
   ├─> NamakadeHtmlParser.parseEpisodeList(doc)
   └─> Returns List<Episode>

3. Episode Page (Video URL)
   ├─> NamakadeApiService.extractVideoUrl(episodeUrl)
   ├─> Fetches HTML
   ├─> NamakadeHtmlParser.extractVideoUrl(doc)
   └─> Returns VideoUrl
```

### Rate Limiting

All HTTP requests are rate-limited to **500ms delay** between calls to avoid overwhelming the server.

```kotlin
companion object {
    private const val RATE_LIMIT_DELAY_MS = 500L
    private suspend fun enforceRateLimit() { ... }
}
```

### Fallback Strategy

If HTML scraping fails, the service can generate video URLs using known CDN patterns:

```kotlin
// Fallback URL generation
val generatedUrl = NamakadeApiService.generateVideoUrl("algoritm", 1)
// Verify URL exists before use
val isValid = NamakadeApiService.verifyVideoUrl(generatedUrl.url)
```

---

## INTEGRATION POINTS

### Existing Data Models (Reused)

The implementation uses existing data models from farsiland integration:

- **Movie** (`data/models/UIModels.kt:11`)
- **Series** (`data/models/UIModels.kt:29`)
- **Episode** (`data/models/UIModels.kt:49`)
- **VideoUrl** (`data/models/VideoUrl.kt:7`)

These models already have the `farsilandUrl` field which will store namakade.com URLs for the new content source.

### HTTP Client (Reused)

Uses existing `RetrofitClient.getHttpClient()` for all HTTP requests:
- 30s timeout
- User-Agent header
- Logging interceptor
- Retry logic built-in

---

## NEXT STEPS

### Phase 2: UI Integration (3-4 days)

1. **Add Content Source Selector**
   - Extend MainFragment.kt to show source filter
   - Add "Farsiland" / "Namakade" / "All" toggle
   - Update content loading based on selected source

2. **Update MovieList.kt**
   - Replace hardcoded Google samples
   - Load from NamakadeApiService
   - Mix farsiland and namakade content

3. **Video Playback Integration**
   - No changes needed to PlaybackActivity
   - ExoPlayer already supports direct MP4 URLs
   - Just pass namakade video URLs to player

4. **UI Badges**
   - Add source badge to content cards
   - Show "Farsiland" or "Namakade" indicator
   - Different colors per source

### Phase 3: Database Integration (2 days)

1. **Add Source Field**
   ```kotlin
   @Entity(tableName = "content")
   data class Content(
       @PrimaryKey val id: String,
       val source: String, // "farsiland" or "namakade"
       ...
   )
   ```

2. **Cache Strategy**
   - Series list: 24 hours
   - Episode list: 1 week
   - Video URLs: 24 hours

3. **Room DAOs**
   - Update queries to filter by source
   - Add multi-source search

### Phase 4: Testing & Polish (2 days)

1. **Error Handling**
   - HTML parsing failures
   - Network errors
   - Invalid video URLs

2. **Performance**
   - Lazy loading
   - Image caching
   - Background scraping

3. **User Experience**
   - Loading indicators
   - Error messages
   - Retry options

---

## DEPENDENCIES

### Already Included ✅

- **Jsoup 1.17.1** - HTML parsing (line 58 of build.gradle.kts)
- **OkHttp3** - HTTP client
- **Retrofit** - REST client
- **Moshi** - JSON parsing
- **Coroutines** - Async operations

### No Additional Dependencies Required

---

## BUILD VERIFICATION

```
BUILD SUCCESSFUL in 25s
36 actionable tasks: 13 executed, 23 up-to-date
```

**Warnings:** Only deprecation warnings in existing code (not related to new changes)

---

## API USAGE EXAMPLES

### Get All Series from Category

```kotlin
val service = NamakadeApiService()
val series = service.getSeriesFromCategory("best-serial")

// Returns:
// Series(id=..., title="Algoritm", totalEpisodes=14, genres=["اکشن", "درام"])
// Series(id=..., title="Bamdaade Khomaar", totalEpisodes=3, genres=["درام"])
// ...
```

### Get Episodes for a Series

```kotlin
val episodes = service.getEpisodes("algoritm")

// Returns:
// Episode(id=..., title="Episode 1", season=1, episode=1, ...)
// Episode(id=..., title="Episode 2", season=1, episode=2, ...)
// ...
```

### Get Video URL

```kotlin
// Method 1: Scrape from episode page
val videoUrl = service.extractVideoUrl(
    "https://namakade.com/series/algoritm/episodes/algoritm"
)
// Returns: VideoUrl(url="https://media.negahestan.com/.../Algoritm_01.mp4", ...)

// Method 2: Generate from pattern
val generatedUrl = service.generateVideoUrl("algoritm", 1)
// Returns: VideoUrl(url="https://media.negahestan.com/.../Algoritm_01.mp4", ...)

// Method 3: Get with fallback (recommended)
val url = service.getEpisodeVideoUrl(
    seriesSlug = "algoritm",
    episodeSlug = "algoritm",
    episodeNumber = 1
)
```

### Verify Video URL

```kotlin
val isValid = service.verifyVideoUrl(videoUrl.url)
if (isValid) {
    // Play video
} else {
    // Show error or try fallback
}
```

---

## COMPARISON: FARSILAND vs NAMAKADE

| Feature | Farsiland | Namakade | Status |
|---------|-----------|----------|--------|
| Content API | WordPress REST | HTML Scraping | ✅ Implemented |
| Episode Lists | HTML Scraping | HTML Scraping | ✅ Implemented |
| Video URLs | HTML Microdata | `<source>` tag | ✅ Implemented |
| Rate Limiting | 500ms | 500ms | ✅ Implemented |
| Fallback URLs | Pattern generation | Pattern generation | ✅ Implemented |
| URL Verification | HEAD request | HEAD request | ✅ Implemented |

---

## KNOWN LIMITATIONS

1. **No REST API**
   - All content discovery requires HTML scraping
   - More fragile than API (HTML changes could break parser)

2. **Single Quality**
   - Unlike farsiland (1080p, 720p, 480p options)
   - Namakade appears to only provide 1080p

3. **No Mirror CDNs**
   - Farsiland has d1.flnd.buzz and d2.flnd.buzz
   - Namakade only has media.negahestan.com

4. **HTML Structure Dependency**
   - Parser uses CSS selectors that may change
   - Mitigation: Multiple selector fallbacks implemented

---

## RISK ASSESSMENT

| Risk | Level | Mitigation |
|------|-------|------------|
| HTML structure changes | Medium | Multiple selector patterns, fallback URL generation |
| Rate limiting imposed | Low | 500ms delay, exponential backoff possible |
| CDN blocks Android | Low | User-Agent header set, monitor for issues |
| Video URL patterns change | Low | Scraping as primary, pattern generation as fallback |

---

## TIMELINE

- **Phase 1 (HTML Scraper Foundation):** ✅ COMPLETE (1 day)
- **Phase 2 (UI Integration):** 3-4 days
- **Phase 3 (Database Integration):** 2 days
- **Phase 4 (Testing & Polish):** 2 days

**Total Estimated:** 8-9 days remaining (11-14 days total)

---

## CONCLUSION

Phase 1 is **complete and verified**. The HTML scraping foundation is working correctly with live namakade.com data.

**Ready to proceed to Phase 2: UI Integration**

All core scraping functionality has been:
- ✅ Implemented
- ✅ Compiled successfully
- ✅ Tested with live website
- ✅ Verified video URL extraction

The next phase will integrate this scraping infrastructure into the Android TV UI.

---

**END OF PHASE 1 REPORT**
