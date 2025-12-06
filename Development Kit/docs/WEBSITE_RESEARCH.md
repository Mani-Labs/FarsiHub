# Website Research: FarsiPlex.com & Farsiland.com

**Document Purpose**: Comprehensive analysis of both content sources, their capabilities, and how our app integrates with them.

**Last Updated**: 2025-11-12

---

## Table of Contents
1. [FarsiPlex.com Analysis](#farsiplexcom-analysis)
2. [Farsiland.com Analysis](#farsilandcom-analysis)
3. [Namakade.com Analysis](#namakadecom-analysis)
4. [Current Integration Status](#current-integration-status)
5. [Unused Features & Opportunities](#unused-features--opportunities)
6. [Known Issues](#known-issues)
7. [Recommendations](#recommendations)

---

## FarsiPlex.com Analysis

### Site Technology Stack
- **Platform**: WordPress with DooPlay theme (v2.5.5)
- **Theme**: Custom FarsiPlex theme based on DooPlay
- **Content Types**: Movies, TV Shows, Episodes
- **Player**: Custom dooplayer integration

### Available APIs

#### 1. XML Sitemaps (Currently Used ✅)
```
https://farsiplex.com/wp-sitemap-posts-movies-1.xml
https://farsiplex.com/wp-sitemap-posts-tvshows-1.xml
https://farsiplex.com/wp-sitemap-posts-episodes-1.xml
```

**Structure**:
```xml
<url>
  <loc>https://farsiplex.com/tvshow/beretta-dastane-yek-aslahe-88d90546/</loc>
  <lastmod>2025-10-31T10:00:15+00:00</lastmod>
</url>
```

**Date Format**: `yyyy-MM-dd'T'HH:mm:ssXXX` (WITH timezone `+00:00`)

**Advantages**:
- Simple, lightweight
- Includes last modified dates (updates when new episodes added)
- Minimal bandwidth

**Disadvantages**:
- No metadata (must scrape individual pages)
- Limited to 2000 entries per sitemap
- No filtering/querying capabilities

#### 2. WordPress REST API (NOT Currently Used ❌)

**Base URL**: `https://farsiplex.com/wp-json/wp/v2/`

**Available Endpoints**:
```
/posts                  - Blog posts
/pages                  - Static pages
/media/{id}            - Images/media
/types                 - Custom post types
```

**Custom Post Types** (from site analysis):
- Movies, TV Shows, Episodes exist but NOT exposed via REST API
- Could potentially be accessed via custom routes

**DooPlay Player API**:
```javascript
"player_api": "https://farsiplex.com/wp-json/dooplayer/v2/"
```

**Status**: ⚠️ **Not explored** - Could provide direct video URL access

#### 3. Page Scraping (Currently Used ✅)

**Implementation**: `FarsiPlexMetadataScraper.kt`

**What We Scrape**:
- Series metadata (title, description, poster, rating, genres)
- Episode lists from series pages
- Video URLs from episode pages

**Scraping Method**:
1. Fetch HTML page
2. Parse with JSoup
3. Extract structured data from:
   - Meta tags (OpenGraph, Schema.org)
   - DooPlay theme elements
   - Embedded JSON-LD
   - JavaScript variables

**Video URL Extraction**:
```kotlin
// Pattern 1: dooplayer data attribute
element.attr("data-playerid")

// Pattern 2: Embedded iframe URLs
iframe.attr("src")

// Pattern 3: JavaScript player config
script.contains("dooplay_player")
```

### Content Discovery Flow

**Current Implementation** (`FarsiPlexSyncWorker.kt`):

```
1. Fetch sitemap XML (last 20 entries)
2. Parse <loc> and <lastmod> elements
3. Compare lastmod with database timestamps
4. If new/updated:
   a. Scrape full page HTML
   b. Extract metadata with FarsiPlexMetadataScraper
   c. Extract video URLs
   d. Save to database
5. Skip if already up-to-date
```

**Sync Frequency**: Every 15 minutes (configurable)

**Date Handling**:
```kotlin
// Format: "2025-10-31T10:00:15+00:00"
fun parseDate(dateStr: String?): Long {
    val formatWithTimezone = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)
    return formatWithTimezone.parse(dateStr)?.time ?: System.currentTimeMillis()
}
```

### Known Characteristics

**URL Structure**:
```
Movies:   https://farsiplex.com/movie/{slug}-{hash}/
TV Shows: https://farsiplex.com/tvshow/{slug}-{hash}/
Episodes: https://farsiplex.com/episodes/{slug}-{hash}/
```

**Slug Format**: `{title-slug}-{8-char-hash}`
- Hash prevents URL conflicts
- Slug used for SEO

**Malformed Entries**: Some sitemap entries have only hash as slug (e.g., `-88d90546`), indicating data issues

---

## Farsiland.com Analysis

### Site Technology Stack
- **Platform**: WordPress (standard installation)
- **Content Types**: Movies, TV Shows, Episodes
- **Custom Taxonomies**: Genres, Cast (dtcast), Creators (dtcreator), Studios (dtstudio), Networks (dtnetworks), Years (dtyear)

### Available APIs

#### 1. WordPress REST API (Currently Used ✅)

**Base URL**: `https://farsiland.com/wp-json/wp/v2/`

**Custom Post Types**:
```
/movies              - Movies
/tvshows             - TV Shows
/episodes            - Episodes
/media/{id}          - Media/images
/genres              - Genre taxonomy
/dtcast              - Cast members taxonomy
/dtcreator           - Creators taxonomy
```

**Standard Parameters**:
```
?per_page=20              // Items per page (max 100)
?page=1                   // Page number
?orderby=date|modified    // Sort field
?order=desc|asc           // Sort order
?search=query             // Full-text search
?after=2025-01-01         // Published after date
?modified_after=2025-01-01 // Modified after date (UNUSED!)
```

#### 2. Full API Response Example

```json
{
  "id": 294171,
  "date": "2025-10-31T09:59:56",          // Publish date (NEVER changes)
  "date_gmt": "2025-10-31T09:59:56",
  "modified": "2025-10-31T10:00:14",      // Last update (CHANGES with new episodes)
  "modified_gmt": "2025-10-31T10:00:14",
  "slug": "beretta-dastane-yek-aslahe",
  "status": "publish",
  "type": "tvshows",
  "link": "https://farsiland.com/tvshows/beretta-dastane-yek-aslahe/",
  "title": {
    "rendered": "Beretta: Dastane Yek Aslahe"
  },
  "content": {
    "rendered": "<p>Series description...</p>",
    "protected": false
  },
  "author": 1,
  "featured_media": 294168,
  "genres": [29],
  "dtcast": [6982, 1692, 8480, ...],      // Cast member IDs
  "dtcreator": [8406],                    // Creator IDs
  "dtstudio": [],
  "dtnetworks": [],
  "dtyear": [9189],
  "acf": {                                 // Advanced Custom Fields
    "rating": "8.5",
    "seasons": "1",
    "cast": "Cast names...",
    // Additional metadata
  },
  "yoast_head_json": {
    // Rich SEO metadata
    "og_image": [...],
    "schema": {...}
  }
}
```

**Date Format**: `yyyy-MM-dd'T'HH:mm:ss` (NO timezone)

### Current Integration

**Implementation**: `ContentSyncWorker.kt`

**API Calls**:
```kotlin
val wpMovies = wordPressApi.getMovies(perPage = 20, page = 1)
val wpShows = wordPressApi.getTvShows(perPage = 20, page = 1)
val wpEpisodes = wordPressApi.getEpisodes(perPage = 20, page = 1)
```

**Data Mapping**:
```kotlin
// WPModels.kt
data class WPTvShow(
    val id: Int,
    val title: WPTitle,
    val link: String,
    val featuredMedia: Int,
    val date: String,           // ✅ Currently used
    // val modified: String?    // ❌ MISSING! Should be added
    val acf: Map<String, Any>?
)
```

**Sync Logic** (ContentSyncWorker.kt:278-306):
```kotlin
private suspend fun syncSeries(lastSyncTimestamp: Long): Int {
    val wpShows = wordPressApi.getTvShows(perPage = 20, page = 1)
    val newSeries = mutableListOf<CachedSeries>()

    for (wpShow in wpShows) {
        val existingSeries = contentDb.seriesDao().getSeriesById(wpShow.id)

        // ❌ BUG: Uses publish date, not modified date!
        val seriesTimestamp = parseDateToTimestamp(wpShow.date)

        if (existingSeries == null || seriesTimestamp > lastSyncTimestamp) {
            val cachedSeries = wpShow.toCachedSeries()
            newSeries.add(cachedSeries)
        }
    }

    if (newSeries.isNotEmpty()) {
        contentDb.seriesDao().insertMultipleSeries(newSeries)
    }

    return newSeries.size
}
```

**Critical Issue**:
- Uses `date` (publish date) instead of `modified` (last update date)
- **Result**: Won't detect when series get new episodes
- **Fix Required**: Add `modified` field to model and use it for sync detection

### Taxonomy Resolution

**Current Status**: ❌ **NOT Implemented**

**Available Endpoints**:
```
GET /wp-json/wp/v2/dtcast/{id}     // Get cast member name
GET /wp-json/wp/v2/dtcreator/{id}  // Get creator name
GET /wp-json/wp/v2/genres/{id}     // Get genre name
```

**Potential Enhancement**:
```kotlin
// Resolve taxonomy IDs to names
suspend fun resolveCast(castIds: List<Int>): List<String> {
    return castIds.map { id ->
        wordPressApi.getCastMember(id).name
    }
}
```

---

## Namakade.com Analysis

### Site Technology Stack
- **Platform**: Custom PHP application (NOT WordPress)
- **Theme**: Custom "Negahestan" platform
- **Content**: 923+ TV series, extensive movie catalog

### Available APIs

**Status**: ❌ **NONE**

**Checked Endpoints**:
- `/wp-json/*` - Returns error page
- `/sitemap.xml` - Returns error page
- `/api/*` - Not found

### Content Discovery

**Current Method**: Full HTML scraping with Selenium

**Implementation**: `namakadeh_master_scraper.py`

**Scraping Process**:
1. Browse series listing pages
2. Extract series metadata from HTML structure
3. Navigate to series pages
4. Extract episode lists
5. Extract video URLs from JavaScript variables

**Video URL Extraction**:
```python
# Method 1: <video><source> tags (movies)
video_tag = soup.find('video', id='videoTag')
source_tag = video_tag.find('source')
video_url = source_tag['src']

# Method 2: JavaScript variables (series)
script_text.find('var seriesepisode_respose = ')
# Parse JSON from JavaScript
```

### Date Handling

**Problem**: Website doesn't expose publish/modified dates

**Result**:
- All series in database have IDENTICAL timestamps (scrape time)
- Database: `1762449288380` (Nov 6, 2025 12:14:48) for ALL 923 series
- **Cannot** sort by actual publish date
- **Cannot** detect content updates

**Workaround**: None possible - site architecture limitation

---

## Current Integration Status

### FarsiPlex Integration

| Feature | Status | Implementation |
|---------|--------|----------------|
| Sitemap fetching | ✅ Working | FarsiPlexApiService.kt |
| Date parsing | ✅ Fixed | parseDate() handles timezone |
| Metadata scraping | ✅ Working | FarsiPlexMetadataScraper.kt |
| Video URL extraction | ✅ Working | Scraper extracts from HTML |
| Update detection | ✅ Working | Uses lastmod from sitemap |
| Incremental sync | ✅ Working | Only scrapes new/updated items |
| Database storage | ✅ Fixed | Correct timestamps in farsiplex_content.db |

**Key Files**:
- `app/src/main/java/com/example/farsilandtv/data/api/FarsiPlexApiService.kt`
- `app/src/main/java/com/example/farsilandtv/data/sync/FarsiPlexSyncWorker.kt`
- `app/src/main/java/com/example/farsilandtv/data/scraper/FarsiPlexMetadataScraper.kt`

### Farsiland Integration

| Feature | Status | Implementation |
|---------|--------|----------------|
| REST API calls | ✅ Working | RetrofitClient + WordPressApiService |
| Date parsing | ✅ Working | parseDateToTimestamp() |
| Metadata extraction | ✅ Working | API provides full metadata |
| Update detection | ❌ **BROKEN** | Uses `date` instead of `modified` |
| Incremental sync | ⚠️ Broken | Won't detect episode additions |
| Database storage | ⚠️ Mixed | Recent series OK, old series identical timestamps |

**Critical Bug**: ContentSyncWorker.kt:286 uses wrong date field

**Key Files**:
- `app/src/main/java/com/example/farsilandtv/data/api/WordPressApiService.kt`
- `app/src/main/java/com/example/farsilandtv/data/api/RetrofitClient.kt`
- `app/src/main/java/com/example/farsilandtv/data/sync/ContentSyncWorker.kt`
- `app/src/main/java/com/example/farsilandtv/data/models/wordpress/WPModels.kt`

### Namakade Integration

| Feature | Status | Implementation |
|---------|--------|----------------|
| Content scraping | ✅ Working | Python scraper |
| Metadata extraction | ✅ Working | HTML parsing |
| Video URL extraction | ✅ Working | JavaScript parsing |
| Date tracking | ❌ Not possible | Site doesn't provide dates |
| Update detection | ❌ Not possible | Site limitation |
| Database storage | ✅ Working | namakade.db with 923 series |

**Key Files**:
- `docs/namakade.com/Scraper/namakadeh_master_scraper.py`
- `app/src/main/assets/databases/namakade.db`

---

## Unused Features & Opportunities

### FarsiPlex Unused Features

#### 1. DooPlay Player API (Priority: HIGH)

**Current**: Scraping video URLs from HTML
**Available**: `https://farsiplex.com/wp-json/dooplayer/v2/`

**Potential Benefits**:
- Direct API access to video URLs
- Faster than HTML scraping
- More reliable
- Reduced bandwidth

**Investigation Required**:
- API authentication requirements
- Request/response format
- Rate limits

#### 2. WordPress REST API for Content (Priority: MEDIUM)

**Current**: Using sitemaps + scraping
**Available**: `/wp-json/wp/v2/*` endpoints

**Potential Benefits**:
- Single API call for metadata + URL
- Query filtering (modified_after, search)
- No HTML parsing needed
- Structured responses

**Questions to Investigate**:
- Are custom post types (movies, tvshows, episodes) exposed?
- Can we query by modification date?
- What fields are available?

**Test Endpoints**:
```bash
curl "https://farsiplex.com/wp-json/wp/v2/posts?modified_after=2025-10-20"
curl "https://farsiplex.com/wp-json/wp/v2/posts?search=beretta"
```

#### 3. Additional Sitemaps (Priority: LOW)

**Not Checked**:
- `/wp-sitemap-posts-post-1.xml` (blog posts)
- `/wp-sitemap-taxonomies-genres-1.xml` (genres)
- `/wp-sitemap-taxonomies-*` (cast, directors, etc.)

**Potential Use**: Genre/cast metadata discovery

### Farsiland Unused Features

#### 1. Modified Date Field (Priority: CRITICAL)

**Current**: Using `date` (publish date)
**Available**: `modified` and `modified_gmt` fields in every response

**Required Action**:
```kotlin
// WPModels.kt - ADD:
@Json(name = "modified")
val modified: String?,

@Json(name = "modified_gmt")
val modifiedGmt: String?

// ContentSyncWorker.kt:286 - CHANGE:
val seriesTimestamp = parseDateToTimestamp(wpShow.modified ?: wpShow.date)
```

**Impact**: Fix broken update detection

#### 2. Advanced Query Filters (Priority: HIGH)

**Current**: Fetching last 20 items unconditionally
**Available**: Rich query parameters

**Examples**:
```kotlin
// Only get content modified in last 24 hours
wordPressApi.getTvShows(
    modifiedAfter = yesterday,
    perPage = 100
)

// Search for specific series
wordPressApi.searchTvShows(
    query = "beretta",
    perPage = 20
)

// Sort by modification date
wordPressApi.getTvShows(
    orderBy = "modified",
    order = "desc"
)
```

#### 3. Taxonomy Resolution (Priority: MEDIUM)

**Current**: Storing only taxonomy IDs
**Available**: Full taxonomy endpoints

**Data We're Ignoring**:
```json
{
  "dtcast": [6982, 1692, 8480, ...],      // Cast IDs - could resolve to names
  "dtcreator": [8406],                     // Creator IDs
  "dtyear": [9189],                        // Year taxonomy
  "genres": [29]                           // Genre IDs
}
```

**Potential Enhancement**:
```kotlin
// Resolve and display cast names
suspend fun getCastNames(castIds: List<Int>): List<String> {
    return castIds.map { id ->
        try {
            wordPressApi.getCastMember(id).name
        } catch (e: Exception) {
            null
        }
    }.filterNotNull()
}
```

**UI Enhancement**: Show cast members in series details page

#### 4. Rich Metadata (Priority: LOW)

**Available but Unused**:
- Yoast SEO metadata (schema.org structured data)
- OpenGraph tags
- Twitter card data
- Breadcrumb navigation
- Article metadata

**Potential Use**: Enhanced search, better content categorization

---

## Known Issues

### Issue 1: Farsiland Broken Update Detection

**Severity**: CRITICAL
**Status**: ❌ Not Fixed

**Problem**:
```kotlin
// ContentSyncWorker.kt:286
val seriesTimestamp = parseDateToTimestamp(wpShow.date)  // ❌ Wrong field!
```

**Impact**:
- App won't detect when series get new episodes
- Users won't see new content automatically
- Sync appears to work but misses updates

**Root Cause**: WordPress API returns TWO date fields:
- `date`: Original publish date (never changes)
- `modified`: Last update date (changes with new episodes)

We're using the wrong one!

**Fix**:
1. Add `modified` field to `WPTvShow` model
2. Use `modified` instead of `date` for sync detection
3. Update database with corrected timestamps

**Files to Modify**:
- `WPModels.kt` - Add modified field
- `ContentSyncWorker.kt:286` - Change date to modified
- `farsiland_content.db` - Re-scrape old series with correct dates

### Issue 2: FarsiPlex Database Had Wrong Timestamps

**Severity**: HIGH
**Status**: ✅ FIXED (2025-11-12)

**Problem**: All series in bundled database had identical timestamp

**Root Cause**: parseDate() was failing, falling back to System.currentTimeMillis()

**Fix Applied**:
1. Fixed parseDate() to handle timezone format (`+00:00`)
2. Created Python script to fetch correct dates from sitemap
3. Updated farsiplex_content.db with correct timestamps
4. Rebuilt APK with fixed database

**Verification**: Database now shows correct unique timestamps from sitemap

### Issue 3: Namakade No Date Tracking

**Severity**: MEDIUM
**Status**: ❌ Cannot Fix (Site Limitation)

**Problem**: All 923 series have identical timestamp (Nov 6, 2025 12:14:48)

**Root Cause**: Namakade.com doesn't expose publish/modified dates anywhere:
- No API
- No sitemap
- No structured metadata
- No date fields in HTML

**Impact**:
- Cannot sort by actual publish date
- Cannot detect content updates
- All content shows scrape time as date

**Workaround**: None possible - architectural limitation of Namakade.com

### Issue 4: FarsiPlex Malformed Sitemap Entries

**Severity**: LOW
**Status**: ✅ Handled

**Problem**: Some sitemap entries have only hash as slug (e.g., `-88d90546`)

**Current Handling**:
```kotlin
// FarsiPlexSyncWorker.kt:141-144
if (slug.startsWith("-") && slug.length == 9) {
    Log.w(TAG, "Skipping malformed series URL: ${showUrl.loc}")
    continue
}
```

**Impact**: Minimal - only affects a few entries

---

## Recommendations

### Priority 1: Fix Farsiland Sync (CRITICAL)

**Action Items**:
1. Add `modified` field to WPModels.kt
2. Update ContentSyncWorker to use `modified` instead of `date`
3. Test sync detects new episodes correctly
4. Consider re-scraping old series to fix timestamps

**Estimated Effort**: 2-4 hours
**Impact**: HIGH - Fixes broken update detection

### Priority 2: Investigate FarsiPlex DooPlay API (HIGH)

**Action Items**:
1. Explore `/wp-json/dooplayer/v2/` endpoints
2. Test video URL retrieval
3. Compare reliability vs HTML scraping
4. Implement if beneficial

**Estimated Effort**: 4-8 hours
**Impact**: MEDIUM-HIGH - Could simplify video URL extraction

### Priority 3: Use Advanced Query Filters (MEDIUM)

**Action Items**:
1. Implement `modified_after` parameter in API calls
2. Only fetch content modified since last sync
3. Reduce bandwidth and processing time

**Estimated Effort**: 2-3 hours
**Impact**: MEDIUM - More efficient syncing

### Priority 4: Taxonomy Resolution (LOW-MEDIUM)

**Action Items**:
1. Implement cast/creator name resolution
2. Store in database
3. Display in UI (SeriesDetailsActivity)

**Estimated Effort**: 6-10 hours
**Impact**: LOW-MEDIUM - Better user experience

### Priority 5: Explore FarsiPlex WordPress API (LOW)

**Action Items**:
1. Test if custom post types are exposed
2. Compare with current sitemap approach
3. Evaluate if API replacement is worthwhile

**Estimated Effort**: 4-6 hours
**Impact**: LOW - Current approach works well

---

## Research Needed

### Questions to Answer

#### FarsiPlex.com

1. **DooPlay API Access**
   - Does `/wp-json/dooplayer/v2/` require authentication?
   - What endpoints are available?
   - Request/response format?
   - Rate limits?

2. **Custom Post Types**
   - Are movies/tvshows/episodes exposed via REST API?
   - What fields are available?
   - Can we use `modified_after` filtering?

3. **Video URL Reliability**
   - How often do video URLs change?
   - Are there multiple quality options?
   - Expiration/token requirements?

#### Farsiland.com

1. **Taxonomy Performance**
   - Cost of resolving 10+ cast IDs per series?
   - Caching strategy?
   - Batch resolution possible?

2. **API Rate Limits**
   - What are the limits?
   - Need for exponential backoff?
   - Best practices for bulk sync?

3. **Modified Date Behavior**
   - Does `modified` update when episode added?
   - Reliable for sync detection?
   - Edge cases?

#### General

1. **Content Update Frequency**
   - How often is new content added?
   - Peak times for updates?
   - Optimal sync interval?

2. **Database Size Projections**
   - Growth rate of content catalogs?
   - Storage implications?
   - Cleanup strategies needed?

---

## Testing Checklist

### FarsiPlex Tests

- [ ] Verify sitemap parsing handles all date formats
- [ ] Test parseDate() with edge cases
- [ ] Confirm malformed entry filtering works
- [ ] Validate video URL extraction
- [ ] Test sync skips unchanged content
- [ ] Verify database timestamps are correct

### Farsiland Tests

- [ ] Add `modified` field to model
- [ ] Test sync detects new episodes
- [ ] Verify `modified` vs `date` behavior
- [ ] Test taxonomy ID resolution
- [ ] Validate query parameter filtering
- [ ] Confirm incremental sync works

### Namakade Tests

- [ ] Verify scraper handles all series formats
- [ ] Test video URL extraction methods
- [ ] Validate database storage
- [ ] Confirm 923 series loaded correctly

---

## Change History

| Date | Change | Author |
|------|--------|--------|
| 2025-11-12 | Initial document creation | Claude |
| 2025-11-12 | Fixed FarsiPlex database timestamps | Claude |
| 2025-11-12 | Identified Farsiland modified date bug | Claude |

---

## Related Documentation

- `docs/farsiplex.com/README.md` - FarsiPlex integration details
- `docs/namakade.com/INTEGRATION_COMPLETE.md` - Namakade scraper documentation
- `docs/DATABASE_IO_ERROR_FIX.md` - Database architecture
- `CLAUDE.md` - Project overview and guidelines
