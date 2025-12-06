# Namakadeh Master Scraper - Technical Documentation

**Version**: 2.0
**Target Website**: https://namakade.com (formerly namakadeh.com)
**Purpose**: Complete content discovery and video URL extraction for Persian/Farsi streaming content
**Last Updated**: 2025-11-06

---

## Table of Contents

1. [Overview](#overview)
2. [Website Architecture](#website-architecture)
3. [HTML/CSS Structure](#htmlcss-structure)
4. [Video URL Extraction](#video-url-extraction)
5. [CDN & Anti-Scraping](#cdn--anti-scraping)
6. [Scraper Architecture](#scraper-architecture)
7. [Data Model](#data-model)
8. [Common Issues & Solutions](#common-issues--solutions)
9. [Performance Optimization](#performance-optimization)
10. [Legal & Ethical Considerations](#legal--ethical-considerations)

---

## Overview

### What This Scraper Does

1. **Content Discovery**: Scrapes catalog of all series and movies from namakade.com
2. **Episode Extraction**: Finds all episodes for each series
3. **Video URL Extraction**: Extracts direct MP4 video URLs from episode/movie pages
4. **CDN Replacement**: Replaces blocked CDN domains with working alternatives
5. **Data Management**: Maintains checkpoint system for resumable scraping

### Technology Stack

```python
# Core Dependencies
selenium==4.x          # Browser automation
beautifulsoup4==4.x    # HTML parsing
requests==2.x          # HTTP requests (for static pages)

# Browser
ChromeDriver           # Must match your Chrome version
```

---

## Website Architecture

### Domain & Hosting

**Primary Domain**: `https://namakade.com`
**Previous Domains**: `namakadeh.com`, `namakadeh.net` (redirects)
**CDN Domains**:
- `media.negahestan.com` (working)
- `media.iranproud2.net` (geo-blocked, must replace)
- `media.iranproud.net` (geo-blocked, must replace)

**Hosting**: CloudFlare-protected
**Server Location**: Iran (likely)
**Content Type**: Persian/Farsi movies and series (licensed + user-uploaded)

### Page Types

#### 1. Homepage
- **URL**: `https://namakade.com/`
- **Purpose**: Featured content, categories
- **Not used by scraper**: We scrape directly from catalog pages

#### 2. Catalog Pages (Initial Discovery)
- **URL Pattern**: `https://namakade.com/series`, `https://namakade.com/iran-1-movies`
- **HTML Structure**:
  ```html
  <ul id="gridMason" class="gridMasonTR">
    <li>
      <a href="/series/show-slug">
        <img src="thumbnail.jpg" alt="Show Title">
        <div class="overlay">Title in Persian</div>
      </a>
    </li>
    <!-- Repeat for each show -->
  </ul>
  ```
- **Pagination**: JavaScript-based infinite scroll (not traditional pagination)
- **Show Metadata**: Limited info (title, thumbnail, URL)

#### 3. Series Detail Pages
- **URL Pattern**: `https://namakade.com/series/{slug}`
- **Example**: `https://namakade.com/series/shoghaal`
- **Purpose**: Lists all episodes for a series
- **HTML Structure**:
  ```html
  <div id="divVideoHolder">
    <ul id="gridMason2" class="gridMasonTR">
      <li>
        <a href="/series/shoghaal/episodes/shoghaal">
          <img src="episode_thumb.jpg">
          <div class="episodeNumber">1</div>
        </a>
      </li>
      <!-- Repeat for each episode -->
    </ul>
  </div>

  <!-- View count -->
  <div id="divVidDet09">Views: 12345</div>
  ```

**CRITICAL CSS SELECTORS**:
- `ul#gridMason2` - Episode list container (REQUIRED)
- `ul#gridMason2 li` - Individual episode items
- `ul#gridMason2 li a` - Episode link (href = episode page URL)
- `ul#gridMason2 li img` - Episode thumbnail
- `div#divVidDet09`, `div#divVidDet08`, `div#divVidDet07` - View count containers

**IMPORTANT**: Some series use `ul#gridMason` instead of `gridMason2` - always check both!

#### 4. Episode Pages (Video URL Source)
- **URL Pattern**: `https://namakade.com/series/{series-slug}/episodes/{episode-slug}`
- **Example**: `https://namakade.com/series/shoghaal/episodes/shoghaal`
- **Purpose**: Contains actual video URL in JavaScript
- **HTML Structure**:
  ```html
  <head>
    <script type="text/javascript">
      var seriesepisode_respose = {
        "video_url": [
          {"android": "https://media.iranproud2.net/ipnx/.../episode.mp4"},
          {"ios": "https://media.iranproud2.net/ipnx/.../episode.mp4"}
        ],
        "title": "Episode Title",
        "duration": "45:30"
      };
    </script>
  </head>

  <body>
    <div id="divVideoHolder">
      <!-- Video player loads here via JavaScript -->
    </div>
  </body>
  ```

**CRITICAL JAVASCRIPT VARIABLE**:
```javascript
var seriesepisode_respose = { ... }
```
This is the PRIMARY source of video URLs for series episodes.

**Alternative Structure** (fallback - not reliable):
```html
<div id="divVideoHolder" videosrc="https://...mp4">
  <video id="videoTag">
    <source src="https://...mp4">
  </video>
</div>
```

#### 5. Movie Pages (Different Structure!)
- **URL Pattern**: `https://namakade.com/iran-1-movies/{category}/{slug}`
- **Example**: `https://namakade.com/iran-1-movies/foreign-horror/ehzar`
- **Purpose**: Movie detail + video URL
- **HTML Structure** (DIFFERENT from series!):
  ```html
  <div id="divVideoHolder">
    <video id="videoTag" class="video-js vjs-default-skin">
      <source src="https://media.negahestan.com/ipnx/media/movies/Movie.mp4" type="video/mp4">
    </video>
  </div>
  ```

**CRITICAL**: Movies use `<video><source>` tags, NOT JavaScript variable!

**After JavaScript Loads** (ID changes!):
```html
<video id="videoTag_html5_api" class="vjs-tech">
  <source src="https://media.negahestan.com/ipnx/media/movies/Movie.mp4">
</video>
```

---

## HTML/CSS Structure

### Key CSS Selectors

#### Content Lists
```css
/* Catalog page - show list */
ul#gridMason.gridMasonTR

/* Series page - episode list */
ul#gridMason2.gridMasonTR

/* Individual items */
ul#gridMason2 > li
ul#gridMason2 > li > a
ul#gridMason2 > li > a > img
```

#### Video Player
```css
/* Video container */
div#divVideoHolder

/* Video tag (movies) */
video#videoTag.video-js.vjs-default-skin

/* Video tag after JS load (ID CHANGES!) */
video#videoTag_html5_api.vjs-tech

/* Source tag */
video > source[src]
```

#### Metadata
```css
/* View count */
div#divVidDet09  /* Primary */
div#divVidDet08  /* Fallback */
div#divVidDet07  /* Fallback */

/* Title */
h1.title

/* Episode number indicator */
div.episodeNumber
```

### CSS Classes to Avoid

**These are styling classes - do NOT rely on them:**
- `.overlay`, `.hover-effect` - Visual styling only
- `.grid-item`, `.card` - Layout classes, inconsistent
- `.thumbnail` - May not always be present

**Always use IDs for critical selectors** (`#gridMason2`, `#videoTag`, etc.)

---

## Video URL Extraction

### Method 1: JavaScript Variable (Series Episodes)

**Source**: `seriesepisode_respose` JavaScript object
**Location**: `<script>` tag in `<head>` or `<body>`
**Reliability**: 95% (primary method)

**Extraction Code**:
```python
soup = BeautifulSoup(driver.page_source, 'html.parser')
scripts = soup.find_all('script')

for script in scripts:
    if script.string and 'seriesepisode_respose' in script.string:
        script_text = script.string

        # Find JSON object
        start = script_text.find('var seriesepisode_respose = ')
        if start != -1:
            start += len('var seriesepisode_respose = ')
        else:
            start = script_text.find('var seriesepisode_respose=')  # No space
            if start != -1:
                start += len('var seriesepisode_respose=')

        if start != -1:
            end = script_text.find(';', start)
            json_str = script_text[start:end].strip()

            data = json.loads(json_str)
            video_urls = data.get('video_url', [])

            # Priority: android > ios
            for url_obj in video_urls:
                if 'android' in url_obj:
                    return url_obj['android']

            for url_obj in video_urls:
                if 'ios' in url_obj:
                    return url_obj['ios']
```

**JSON Structure**:
```json
{
  "video_url": [
    {
      "android": "https://media.iranproud2.net/ipnx/media/series/episodes/Episode.mp4"
    },
    {
      "ios": "https://media.iranproud2.net/ipnx/media/series/episodes/Episode.mp4"
    }
  ],
  "title": "Episode Title",
  "duration": "45:30",
  "thumbnail": "https://media.negahestan.com/.../thumb.jpg"
}
```

**Why Both android and ios?**
Originally served different formats (MP4 vs HLS), now usually identical.

### Method 2: HTML Video Tag (Movies)

**Source**: `<video><source>` tags
**Location**: `div#divVideoHolder`
**Reliability**: 90% (primary for movies)

**Extraction Code**:
```python
soup = BeautifulSoup(driver.page_source, 'html.parser')

# Try multiple selectors (ID changes after JS load)
video_tag = (
    soup.find('video', id='videoTag') or
    soup.find('video', class_='video-js') or
    soup.find('video')
)

if video_tag:
    source_tag = video_tag.find('source')
    if source_tag and source_tag.get('src'):
        return source_tag['src']
```

**CRITICAL**: Must wait for JavaScript to load (5 seconds for movies, 3 for episodes)

```python
driver.get(url)
time.sleep(5)  # REQUIRED for video player initialization
```

### Method 3: Fallback (NOT Recommended)

**Source**: `div#divVideoHolder` attribute
**Location**: `videosrc` attribute
**Reliability**: 20% (deprecated, rarely present)

```python
video_div = soup.find('div', id='divVideoHolder')
if video_div and video_div.get('videosrc'):
    return video_div['videosrc']
```

**Do NOT rely on this!** Use as last resort only.

---

## CDN & Anti-Scraping

### CDN Domain Issues

**Problem**: Original CDN domains are geo-blocked outside Iran

**Blocked Domains**:
- `media.iranproud2.net` - DNS fails (ERR_NAME_NOT_RESOLVED)
- `media.iranproud.net` - DNS fails

**Working Domain**:
- `media.negahestan.com` - Resolves to 162.222.185.20

**Solution**: Replace all CDN domains in extracted URLs

```python
def replace_cdn_domain(url: str) -> str:
    """Replace blocked CDN domains with working alternative"""
    url = url.replace('media.iranproud2.net', 'media.negahestan.com')
    url = url.replace('media.iranproud.net', 'media.negahestan.com')
    return url
```

**CRITICAL**: Apply this to ALL video URLs before storing!

### Anti-Scraping Protection

#### 1. Referer Header Requirement

**Problem**: Direct video URL access returns `blockHacks.mp4` (1-minute blocker video)

**Solution**: Send `Referer` header when playing videos

```python
# For ExoPlayer (Android app)
val httpDataSourceFactory = DefaultHttpDataSource.Factory()
    .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
    .setDefaultRequestProperties(
        mapOf("Referer" to "https://namakade.com/")
    )
```

**Required Headers**:
```http
Referer: https://namakade.com/
User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36
```

**WITHOUT these headers**: Video redirects to blocker video!

#### 2. Rate Limiting

**Behavior**: Too many requests → `ERR_NAME_NOT_RESOLVED` or 429 errors

**Symptoms**:
```
Message: unknown error: net::ERR_NAME_NOT_RESOLVED
```

**Solutions**:
1. **Reduce workers**: Use 1-3 instead of 5
2. **Add delays**: `time.sleep(0.5)` between requests
3. **Retry logic**: Wait 2-3 minutes, then retry

**Safe Request Rate**: ~20 requests/minute per worker

#### 3. JavaScript Requirement

**Problem**: Video URLs only appear after JavaScript execution

**Wrong Approach** (fails):
```python
response = requests.get(url)  # No JavaScript execution!
soup = BeautifulSoup(response.text, 'html.parser')
# Won't find seriesepisode_respose!
```

**Correct Approach** (works):
```python
driver = webdriver.Chrome()
driver.get(url)
time.sleep(3)  # Wait for JavaScript
soup = BeautifulSoup(driver.page_source, 'html.parser')
# Now seriesepisode_respose is present!
```

**CRITICAL**: Always use Selenium for episode/movie pages!

#### 4. CloudFlare Protection

**Status**: Currently minimal (no CAPTCHA, no JS challenge)

**May Encounter**:
- IP blocking after excessive requests
- Temporary bans (usually 5-30 minutes)

**If Blocked**:
1. Wait 30 minutes
2. Change IP (VPN/proxy)
3. Reduce scraping speed

---

## Scraper Architecture

### Data Flow

```
1. Load Checkpoint (or create new)
   ↓
2. Analyze Status (what's missing?)
   ↓
3. Create Task Queue
   - Movies without video URLs
   - Series without episodes
   - Episodes without video URLs
   ↓
4. Parallel Processing (ThreadPoolExecutor)
   ↓
   Worker 1: Setup Browser → Scrape Show → Extract URLs → Close Browser
   Worker 2: Setup Browser → Scrape Show → Extract URLs → Close Browser
   Worker N: Setup Browser → Scrape Show → Extract URLs → Close Browser
   ↓
5. Aggregate Results (thread-safe)
   ↓
6. Save Checkpoint (every 10 tasks)
   ↓
7. Final Save + Export
```

### Thread Safety

**Critical Sections** (require locks):

```python
checkpoint_lock = threading.Lock()

# When updating checkpoint
with checkpoint_lock:
    all_episodes.append(new_episode)
    save_checkpoint(checkpoint)

print_lock = threading.Lock()

# When printing (prevents garbled output)
with print_lock:
    print(message)
```

**Why Separate Browsers Per Worker?**
- Selenium WebDriver is NOT thread-safe
- Sharing driver causes race conditions
- Each worker needs isolated browser instance

```python
# WRONG (crashes)
driver = setup_browser()  # Shared
for task in tasks:
    executor.submit(scrape_task, driver, task)  # Race condition!

# CORRECT
for task in tasks:
    executor.submit(scrape_task, setup_browser(), task)  # Isolated
```

### Checkpoint System

**File**: `complete_scraper_checkpoint.json`

**Structure**:
```json
{
  "phase": "detail_scraping" | "complete",
  "last_detail_index": 762,
  "shows": [
    {
      "id": "show-slug",
      "title_english": "Show Title",
      "type": "series" | "movie",
      "category": "DRAMA",
      "genre": "Action, Thriller",
      "url": "https://namakade.com/series/show-slug",
      "thumbnail": "https://media.negahestan.com/.../thumb.jpg",
      "total_episodes": 10,
      "view_count": 12345,
      "video_url": "https://..." // Only for movies
    }
  ],
  "episodes": [
    {
      "id": "show-slug_ep1",
      "show_id": "show-slug",
      "episode_number": 1,
      "slug": "episode-slug",
      "url": "/series/show-slug/episodes/episode-slug",
      "thumbnail": "https://media.negahestan.com/.../ep_thumb.jpg",
      "video_url": "https://media.negahestan.com/ipnx/.../episode.mp4"
    }
  ]
}
```

**Key Fields**:

| Field | Type | Purpose | Required |
|-------|------|---------|----------|
| `phase` | string | Scraping state | Yes |
| `last_detail_index` | int | Resume point | Yes |
| `shows[].id` | string | Unique slug | Yes |
| `shows[].type` | string | "series" or "movie" | Yes |
| `episodes[].show_id` | string | Links to parent show | Yes |
| `episodes[].video_url` | string | Direct MP4 URL | Yes |

**Phases**:
- `detail_scraping` - Active scraping mode
- `complete` - All scraping finished

**Resume Logic**:
```python
checkpoint = load_checkpoint()
start_idx = checkpoint['last_detail_index'] + 1
remaining = all_shows[start_idx:]
# Continue from where we left off
```

### Deduplication Strategy

**Problem**: Re-scraping can create duplicates

**Solution**: Check episode ID before adding

```python
existing_episode_ids = {ep['id'] for ep in all_episodes}

for new_ep in scraped_episodes:
    if new_ep['id'] not in existing_episode_ids:
        all_episodes.append(new_ep)  # Add new
        existing_episode_ids.add(new_ep['id'])
    else:
        # Update existing (merge new data)
        for i, ep in enumerate(all_episodes):
            if ep['id'] == new_ep['id']:
                all_episodes[i] = new_ep
                break
```

**CRITICAL**: Episode ID format is `{show_id}_ep{number}`
- Must be unique per show
- Episode number based on position in gridMason2
- Duplicates happen when show is re-scraped

---

## Data Model

### Show Entity

```python
{
    "id": str,              # Unique slug (e.g., "shoghaal")
    "title_english": str,   # English title
    "title_persian": str,   # Persian title (may contain non-ASCII)
    "slug": str,            # URL slug (same as id)
    "type": str,            # "series" or "movie"
    "category": str,        # "DRAMA", "TURKISH+SERIES", "COMEDY", etc.
    "genre": str,           # "Action, Thriller, Drama" (comma-separated)
    "url": str,             # Full or relative URL
    "thumbnail": str,       # Show poster URL
    "total_episodes": int,  # Expected episode count (UNRELIABLE!)
    "view_count": int,      # Total views
    "video_url": str        # Only for movies (direct MP4 URL)
}
```

**CRITICAL NOTES**:
- `total_episodes` is UNRELIABLE (70-80% inflated due to metadata errors)
- Always verify against actual episode count from series page
- `video_url` only present for movies, NOT series

### Episode Entity

```python
{
    "id": str,              # Format: "{show_id}_ep{number}"
    "show_id": str,         # Foreign key to show
    "episode_number": int,  # Sequential number (1, 2, 3, ...)
    "slug": str,            # URL slug (last part of episode URL)
    "url": str,             # Episode page URL
    "thumbnail": str,       # Episode thumbnail URL
    "video_url": str        # Direct MP4 URL (THE GOAL!)
}
```

**CRITICAL NOTES**:
- `episode_number` is positional (1st in list = 1, 2nd = 2, etc.)
- May NOT match actual episode name (e.g., "Episode 5" might be `episode_number: 3`)
- `id` must be unique per show
- `video_url` is the PRIMARY goal of scraping

### Android App Schema (Target)

**What the Android app expects**:

```kotlin
// Series.kt
data class Series(
    @PrimaryKey val id: String,
    val title: String,
    val thumbnail: String,
    val totalEpisodes: Int,  // We provide this
    val category: String
)

// Episode.kt
data class Episode(
    @PrimaryKey val id: String,
    val seriesId: String,       // Foreign key
    val title: String,          // We DON'T scrape this!
    val episodeNumber: Int,     // We provide this
    val season: Int = 1,        // We DON'T scrape this!
    val thumbnail: String,
    val videoUrl: String,       // CRITICAL - we provide this
    val duration: Int?,         // We DON'T scrape this
    val episodePageUrl: String, // We provide as "url"
    val slug: String
)
```

**Data Gaps**:
- ❌ `Episode.title` - Not scraped (use episode_number as fallback)
- ❌ `Episode.season` - Not scraped (defaults to 1)
- ❌ `Episode.duration` - Not scraped (would need video file metadata)

**Implications**:
- App must handle missing titles (show "Episode 1" instead)
- Multi-season shows will appear as single season
- No duration display (not critical for streaming)

---

## Database Schema

### Complete Android Room Database Schema

The Android app uses Room database with foreign key relationships. Here's the complete schema:

#### Series.kt (Series/Shows Table)

```kotlin
@Entity(
    tableName = "series",
    indices = [
        Index(value = ["category"]),
        Index(value = ["genre"]),
        Index(value = ["viewCount"])
    ]
)
data class Series(
    @PrimaryKey val id: String,              // Unique slug (e.g., "shoghaal")
    val title: String,                       // English title
    val titlePersian: String?,               // Persian title
    val thumbnail: String?,                  // Poster image URL
    val totalEpisodes: Int,                  // Expected count (unreliable!)
    val category: String?,                   // "DRAMA", "TURKISH+SERIES", etc.
    val genre: String?,                      // "Action, Thriller" (comma-separated)
    val viewCount: Int = 0,                  // Total views
    val url: String?,                        // Series detail page URL
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
```

#### Episode.kt (Episodes Table)

```kotlin
@Entity(
    tableName = "episodes",
    foreignKeys = [
        ForeignKey(
            entity = Series::class,
            parentColumns = ["id"],
            childColumns = ["seriesId"],
            onDelete = ForeignKey.CASCADE  // Delete episodes when series deleted
        )
    ],
    indices = [
        Index(value = ["seriesId"]),        // Fast lookups by series
        Index(value = ["episodeNumber"]),   // Fast sorting
        Index(value = ["season", "episodeNumber"])  // Season-based queries
    ]
)
data class Episode(
    @PrimaryKey val id: String,              // Format: "{seriesId}_ep{number}"
    val seriesId: String,                    // Foreign key to Series
    val title: String?,                      // ❌ NOT SCRAPED (use fallback)
    val episodeNumber: Int,                  // Positional number (1, 2, 3...)
    val season: Int = 1,                     // ❌ NOT SCRAPED (defaults to 1)
    val thumbnail: String?,                  // Episode thumbnail URL
    val videoUrl: String?,                   // Direct MP4 URL (CRITICAL!)
    val duration: Int?,                      // ❌ NOT SCRAPED (in seconds)
    val episodePageUrl: String?,             // Episode detail page URL
    val slug: String?,                       // URL slug for episode
    val watched: Boolean = false,            // User watch status
    val lastWatchPosition: Long = 0,         // Resume position (milliseconds)
    val createdAt: Long = System.currentTimeMillis(),
    val scrapedAt: Long = System.currentTimeMillis()
)
```

#### Movie.kt (Movies Table)

```kotlin
@Entity(
    tableName = "movies",
    indices = [
        Index(value = ["category"]),
        Index(value = ["genre"]),
        Index(value = ["viewCount"])
    ]
)
data class Movie(
    @PrimaryKey val id: String,              // Unique slug
    val title: String,                       // English title
    val titlePersian: String?,               // Persian title
    val thumbnail: String?,                  // Movie poster URL
    val videoUrl: String?,                   // Direct MP4 URL (CRITICAL!)
    val category: String?,                   // "COMEDY", "HORROR", "ACTION", etc.
    val genre: String?,                      // "Action, Thriller" (comma-separated)
    val viewCount: Int = 0,                  // Total views
    val url: String?,                        // Movie detail page URL
    val duration: Int?,                      // ❌ NOT SCRAPED (in seconds)
    val watched: Boolean = false,            // User watch status
    val lastWatchPosition: Long = 0,         // Resume position (milliseconds)
    val createdAt: Long = System.currentTimeMillis(),
    val scrapedAt: Long = System.currentTimeMillis()
)
```

#### MonitoredSeries.kt (User's "My Shows" Table)

```kotlin
@Entity(
    tableName = "monitored_series",
    foreignKeys = [
        ForeignKey(
            entity = Series::class,
            parentColumns = ["id"],
            childColumns = ["seriesId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["seriesId"])]
)
data class MonitoredSeries(
    @PrimaryKey val seriesId: String,        // Foreign key to Series
    val lastCheckedAt: Long = 0,             // Last check timestamp
    val lastKnownEpisodeCount: Int = 0,      // Previous episode count
    val newEpisodeCount: Int = 0,            // Badge count (new episodes)
    val addedAt: Long = System.currentTimeMillis()
)
```

### Scraper to Database Mapping

This table shows which fields are provided by the scraper and which require fallback values:

| Scraper Field | Database Field | Status | Notes |
|---------------|----------------|--------|-------|
| **Series Mapping** |
| `show['id']` | `Series.id` | ✅ Provided | Unique slug from URL |
| `show['title_english']` | `Series.title` | ✅ Provided | Primary title |
| `show['title_persian']` | `Series.titlePersian` | ✅ Provided | Persian text |
| `show['thumbnail']` | `Series.thumbnail` | ✅ Provided | Poster image URL |
| `show['total_episodes']` | `Series.totalEpisodes` | ⚠️ Unreliable | Metadata is 79% inflated |
| `show['category']` | `Series.category` | ✅ Provided | "DRAMA", "COMEDY", etc. |
| `show['genre']` | `Series.genre` | ✅ Provided | Comma-separated genres |
| `show['view_count']` | `Series.viewCount` | ✅ Provided | Total views |
| `show['url']` | `Series.url` | ✅ Provided | Series detail page |
| N/A | `Series.createdAt` | 🔧 Auto-generated | Current timestamp |
| N/A | `Series.updatedAt` | 🔧 Auto-generated | Current timestamp |
| **Episode Mapping** |
| `episode['id']` | `Episode.id` | ✅ Provided | Format: `{seriesId}_ep{n}` |
| `episode['show_id']` | `Episode.seriesId` | ✅ Provided | Foreign key |
| N/A | `Episode.title` | ❌ **NOT SCRAPED** | Use "Episode {n}" fallback |
| `episode['episode_number']` | `Episode.episodeNumber` | ✅ Provided | Positional (1, 2, 3...) |
| N/A | `Episode.season` | ❌ **NOT SCRAPED** | Defaults to 1 |
| `episode['thumbnail']` | `Episode.thumbnail` | ✅ Provided | Episode thumbnail |
| `episode['video_url']` | `Episode.videoUrl` | ✅ Provided | **CRITICAL - Direct MP4 URL** |
| N/A | `Episode.duration` | ❌ **NOT SCRAPED** | Requires video metadata |
| `episode['url']` | `Episode.episodePageUrl` | ✅ Provided | Episode detail page |
| `episode['slug']` | `Episode.slug` | ✅ Provided | URL slug |
| N/A | `Episode.watched` | 🔧 Default: false | User progress tracking |
| N/A | `Episode.lastWatchPosition` | 🔧 Default: 0 | Resume position |
| N/A | `Episode.createdAt` | 🔧 Auto-generated | Current timestamp |
| N/A | `Episode.scrapedAt` | 🔧 Auto-generated | Current timestamp |
| **Movie Mapping** |
| `show['id']` | `Movie.id` | ✅ Provided | Unique slug |
| `show['title_english']` | `Movie.title` | ✅ Provided | English title |
| `show['title_persian']` | `Movie.titlePersian` | ✅ Provided | Persian title |
| `show['thumbnail']` | `Movie.thumbnail` | ✅ Provided | Movie poster |
| `show['video_url']` | `Movie.videoUrl` | ✅ Provided | **CRITICAL - Direct MP4 URL** |
| `show['category']` | `Movie.category` | ✅ Provided | Movie category |
| `show['genre']` | `Movie.genre` | ✅ Provided | Comma-separated genres |
| `show['view_count']` | `Movie.viewCount` | ✅ Provided | Total views |
| `show['url']` | `Movie.url` | ✅ Provided | Movie detail page |
| N/A | `Movie.duration` | ❌ **NOT SCRAPED** | Requires video metadata |
| N/A | `Movie.watched` | 🔧 Default: false | User progress tracking |
| N/A | `Movie.lastWatchPosition` | 🔧 Default: 0 | Resume position |
| N/A | `Movie.createdAt` | 🔧 Auto-generated | Current timestamp |
| N/A | `Movie.scrapedAt` | 🔧 Auto-generated | Current timestamp |

### Legend:
- ✅ **Provided**: Scraper extracts this field from website
- ⚠️ **Unreliable**: Provided but often incorrect (metadata errors)
- ❌ **NOT SCRAPED**: Not available from website, requires fallback/default
- 🔧 **Auto-generated**: Created by app logic, not from scraper

### Missing Data & Implications

#### 1. Episode Titles (❌ NOT SCRAPED)

**Problem**: Website doesn't display episode titles in list view.

**Where it exists**: Individual episode pages MAY have titles, but scraping each would require 19,373 extra page loads.

**App Solution**:
```kotlin
fun getEpisodeDisplayTitle(episode: Episode): String {
    return episode.title ?: "Episode ${episode.episodeNumber}"
}
```

**User Impact**:
- Episodes show as "Episode 1", "Episode 2" instead of actual names
- Acceptable for most users (they remember by number)
- Critical episodes (finales, specials) not highlighted

#### 2. Season Numbers (❌ NOT SCRAPED)

**Problem**: Website aggregates all seasons on one page without season metadata.

**Example**: Turkish series "sibe-mamnooeh" has 230 total episodes but only 38 on website (2 seasons mixed together).

**App Solution**:
```kotlin
// All episodes default to season = 1
val episode = Episode(
    season = 1,  // Always 1
    episodeNumber = position  // Continues sequentially
)

// Multi-season shows appear as:
// Episode 1, 2, 3... 38 (all "Season 1")
```

**User Impact**:
- Multi-season shows (2,820 episodes affected) lose season structure
- Users can't filter by season
- "Season 2, Episode 1" becomes "Episode 39" (continuous numbering)
- Acceptable trade-off vs scraping complexity

**Workaround**: Manual curation of season boundaries for popular shows.

#### 3. Duration/Runtime (❌ NOT SCRAPED)

**Problem**: Video file metadata not accessible without downloading.

**Where it exists**: Only available after parsing video file headers (requires HEAD request or partial download).

**App Solution**: Don't display duration before playback.

**User Impact**:
- No "45 min" badges on episodes
- Can't filter by "movies under 90 minutes"
- Minimal - most users don't care about pre-playback duration

#### 4. Metadata Inflation (⚠️ UNRELIABLE)

**Problem**: `total_episodes` field is 79% inflated on average.

**Examples**:
- `sibe-mamnooeh`: Says 230 episodes, actually has 38 (83% inflated)
- `momayezi`: Says 147 episodes, actually has 13 (91% inflated)

**Why it happens**:
- Turkish series aggregate all seasons (website only has 1-2)
- Deleted/removed episodes still in metadata
- External sources counted, not actual pages

**App Solution**: Use actual episode count from database, ignore metadata.

```kotlin
// DON'T use this:
val progress = watchedCount / series.totalEpisodes  // Wrong!

// DO use this:
val actualEpisodeCount = episodeDao.getEpisodeCount(seriesId)
val progress = watchedCount / actualEpisodeCount  // Correct!
```

**User Impact**:
- If using metadata: Progress bars show wrong percentages (e.g., "15% watched" when actually 82%)
- If using database count: Accurate progress tracking

### Database Import Instructions

To import scraped data into Android Room database:

```kotlin
// 1. Load checkpoint JSON
val checkpoint = File("complete_scraper_checkpoint.json")
    .readText()
    .let { Json.decodeFromString<Checkpoint>(it) }

// 2. Import shows (series + movies)
val series = checkpoint.shows
    .filter { it.type == "series" }
    .map { show ->
        Series(
            id = show.id,
            title = show.title_english,
            titlePersian = show.title_persian,
            thumbnail = show.thumbnail,
            totalEpisodes = show.total_episodes,  // Remember: unreliable!
            category = show.category,
            genre = show.genre,
            viewCount = show.view_count,
            url = show.url
        )
    }

val movies = checkpoint.shows
    .filter { it.type == "movie" }
    .map { show ->
        Movie(
            id = show.id,
            title = show.title_english,
            titlePersian = show.title_persian,
            thumbnail = show.thumbnail,
            videoUrl = show.video_url,  // Already extracted!
            category = show.category,
            genre = show.genre,
            viewCount = show.view_count,
            url = show.url
        )
    }

// 3. Import episodes
val episodes = checkpoint.episodes.map { ep ->
    Episode(
        id = ep.id,
        seriesId = ep.show_id,
        title = null,  // Not scraped
        episodeNumber = ep.episode_number,
        season = 1,    // Not scraped, default to 1
        thumbnail = ep.thumbnail,
        videoUrl = ep.video_url,  // Already extracted!
        duration = null,  // Not scraped
        episodePageUrl = ep.url,
        slug = ep.slug
    )
}

// 4. Insert into database
database.seriesDao().insertAll(series)
database.movieDao().insertAll(movies)
database.episodeDao().insertAll(episodes)
```

### Database Statistics (Current)

As of 2025-11-06 (after cleanup and completion):

```
Series:          923 (100%)
Movies:          312 (100%)
Total Shows:   1,235 (100%)

Episodes:     19,373 (100%)
├─ With URLs: 19,371 (99.99%)
└─ Missing:         2 (0.01%)

Duplicates:        0 (0%)

Total Data Size:
├─ Checkpoint JSON:     ~8.3 MB
├─ Room Database:      ~15-20 MB (estimated)
└─ Thumbnail Cache:    ~500 MB (if cached)
```

### Foreign Key Relationships

```
Series (1) ──┬──> Episodes (N)
             └──> MonitoredSeries (0..1)

Movie (1) ──> (No relations)

Cascade Rules:
- Delete Series → Auto-delete all Episodes
- Delete Series → Auto-delete MonitoredSeries entry
```

**Why cascading deletes**:
- If series removed from website, clean up all episodes automatically
- Maintains referential integrity
- Prevents orphaned episodes

---

## Common Issues & Solutions

### Issue 1: "No such element: gridMason2"

**Symptom**:
```python
episode_grid = soup.find('ul', id='gridMason2')
# Returns None
```

**Causes**:
1. Page is a movie (no episodes)
2. Page uses `gridMason` instead of `gridMason2`
3. JavaScript hasn't loaded yet
4. Show has been deleted/removed

**Solution**:
```python
# Try both selectors
episode_grid = soup.find('ul', id='gridMason2')
if not episode_grid:
    episode_grid = soup.find('ul', id='gridMason')

if not episode_grid:
    # Check if error page
    title = soup.find('title')
    if title and 'error' in title.string.lower():
        return None  # Show deleted

    # Wait longer for JavaScript
    time.sleep(5)
    soup = BeautifulSoup(driver.page_source, 'html.parser')
    episode_grid = soup.find('ul', id='gridMason2')
```

### Issue 2: "Video plays blockHacks.mp4"

**Symptom**: Video URL works in browser but plays 1-minute blocker in app

**Cause**: Missing Referer header

**Solution**: Add headers to video player (see [Anti-Scraping](#cdn--anti-scraping))

### Issue 3: "ERR_NAME_NOT_RESOLVED"

**Symptom**: Browser can't resolve domain after many requests

**Causes**:
1. Rate limiting (too many requests)
2. Temporary DNS issues
3. IP blocked by CloudFlare

**Solutions**:
```python
# 1. Reduce workers
NUM_WORKERS = 1  # Instead of 5

# 2. Add delays
time.sleep(0.5)  # Between requests

# 3. Retry logic
for attempt in range(3):
    try:
        driver.get(url)
        break
    except:
        if attempt < 2:
            time.sleep(60)  # Wait 1 minute
        else:
            raise
```

### Issue 4: "total_episodes doesn't match actual count"

**Symptom**: Show metadata says 230 episodes, website has 38

**Cause**: Metadata is inflated/wrong (Turkish series, multi-season aggregates)

**Solution**: ALWAYS use actual episode count from page
```python
# WRONG
expected = show['total_episodes']  # Unreliable!

# CORRECT
episode_items = episode_grid.find_all('li')
actual_count = len(episode_items)  # Use this!
```

### Issue 5: "Duplicate episodes"

**Symptom**: Same episode appears multiple times with different IDs

**Cause**: Show was scraped multiple times without deduplication

**Solution**: Use deduplication logic (see [Deduplication Strategy](#deduplication-strategy))

### Issue 6: "Video URL extraction returns empty string"

**Symptom**: `extract_video_url()` returns `""`

**Causes**:
1. JavaScript variable not found (wrong page structure)
2. JSON parsing failed (malformed JavaScript)
3. Page hasn't loaded yet (need more wait time)

**Debug**:
```python
# Save page HTML for inspection
with open('debug_page.html', 'w', encoding='utf-8') as f:
    f.write(driver.page_source)

# Check for JavaScript variable
scripts = soup.find_all('script')
for script in scripts:
    if script.string and 'seriesepisode' in script.string:
        print("FOUND:", script.string[:500])
```

**Solutions**:
```python
# Increase wait time
time.sleep(5)  # Instead of 3

# Check for alternative structures
if not video_url:
    # Try HTML video tag (movies)
    video_tag = soup.find('video')
    if video_tag:
        source_tag = video_tag.find('source')
        if source_tag:
            video_url = source_tag.get('src', '')
```

---

## Performance Optimization

### Browser Configuration

**Headless Mode** (faster):
```python
chrome_options.add_argument("--headless")
chrome_options.add_argument("--disable-gpu")
chrome_options.add_argument("--no-sandbox")
```

**Disable Unnecessary Features**:
```python
chrome_options.add_argument("--disable-extensions")
chrome_options.add_argument("--disable-images")  # Saves bandwidth
chrome_options.add_argument("--disable-javascript")  # DON'T! Breaks video extraction
```

**Memory Management**:
```python
chrome_options.add_argument("--disable-dev-shm-usage")  # Use /tmp instead of /dev/shm
chrome_options.add_argument("--disable-popup-blocking")
```

### Parallelization

**Optimal Worker Count**:
- **1 worker**: Safest, ~10 episodes/min
- **3 workers**: Recommended, ~30 episodes/min
- **5 workers**: Fast, ~50 episodes/min, may trigger rate limiting

**Memory Usage Per Worker**:
- Chrome browser: ~200-300 MB
- Python overhead: ~50 MB
- Total per worker: ~250-350 MB
- **5 workers**: ~1.5-2 GB RAM

**Batch Processing**:
```python
batch_size = NUM_WORKERS * 2  # Process 2 batches at a time
for batch_start in range(0, len(tasks), batch_size):
    batch = tasks[batch_start:batch_start+batch_size]
    # Process batch
    # Save checkpoint
```

### Network Optimization

**Request Timing**:
```python
# Series episodes (smaller pages)
time.sleep(3)

# Movies (larger pages, more JavaScript)
time.sleep(5)

# Between requests (avoid rate limiting)
time.sleep(0.5)
```

**Retry Strategy**:
```python
def fetch_with_retry(driver, url, max_attempts=3):
    for attempt in range(max_attempts):
        try:
            driver.get(url)
            time.sleep(3)
            return driver.page_source
        except Exception as e:
            if attempt < max_attempts - 1:
                time.sleep(60 * (attempt + 1))  # Exponential backoff
            else:
                raise
```

### Checkpoint Frequency

**Too Frequent**: Slows down scraping (I/O overhead)
```python
save_checkpoint()  # Every request - BAD!
```

**Too Infrequent**: Risk losing progress
```python
save_checkpoint()  # Only at end - BAD!
```

**Optimal**:
```python
if tasks_completed % 10 == 0:  # Every 10 tasks - GOOD!
    save_checkpoint()
```

---

## Legal & Ethical Considerations

### Robots.txt

**URL**: https://namakade.com/robots.txt

**Likely Contents** (check current version):
```
User-agent: *
Disallow: /admin/
Disallow: /api/
# May or may not disallow scraping
```

**Ethical Scraping**:
1. Respect robots.txt if present
2. Rate limit (don't overload server)
3. Identify yourself (User-Agent)
4. Use data responsibly

### Terms of Service

**Check**: https://namakade.com/terms (if exists)

**Typical Restrictions**:
- Automated data collection may be prohibited
- Redistribution of content may be illegal
- Video URLs are for personal use only

**Your Use Case**: Personal Android TV app (non-commercial)
- Generally acceptable for personal use
- Do NOT redistribute scraped data
- Do NOT monetize extracted content

### Copyright

**Content**: All videos are copyrighted by original creators/studios

**Video URLs**: Direct links, not downloads
- Streaming = less problematic than downloading
- Still requires proper licensing for commercial use

**Your Responsibility**:
1. Personal use only (non-commercial)
2. Respect geo-restrictions (if any)
3. Do NOT share video URLs publicly
4. Do NOT create competing streaming service

### Rate Limiting Ethics

**Server Impact**:
- 1 worker = minimal impact
- 5 workers = moderate impact
- 10+ workers = aggressive, unethical

**Best Practices**:
1. Scrape during off-peak hours (3am-6am Iran time)
2. Use delays between requests
3. Stop if errors increase (server struggling)
4. Don't run continuously (give server breaks)

---

## Troubleshooting Guide

### Diagnostic Commands

**Check ChromeDriver Version**:
```bash
chromedriver --version
```

**Check Chrome Version**:
```bash
google-chrome --version  # Linux
# or
"C:\Program Files\Google\Chrome\Application\chrome.exe" --version  # Windows
```

**Versions MUST match!** Download correct ChromeDriver from:
https://chromedriver.chromium.org/downloads

**Test Selenium**:
```python
from selenium import webdriver
driver = webdriver.Chrome()
driver.get("https://www.google.com")
print(driver.title)  # Should print "Google"
driver.quit()
```

### Debug Logging

**Enable Selenium Logging**:
```python
import logging
logging.basicConfig(level=logging.DEBUG)
```

**Save Failed Pages**:
```python
try:
    driver.get(url)
except Exception as e:
    timestamp = datetime.now().strftime('%Y%m%d_%H%M%S')
    with open(f'error_page_{timestamp}.html', 'w', encoding='utf-8') as f:
        f.write(driver.page_source)
    raise
```

**Monitor Network Requests** (manual):
1. Run browser in non-headless mode
2. Open DevTools (F12)
3. Go to Network tab
4. Watch for video URL requests

### Common Error Messages

| Error | Meaning | Solution |
|-------|---------|----------|
| `ERR_NAME_NOT_RESOLVED` | DNS lookup failed | Wait, reduce workers, check DNS |
| `TimeoutException` | Page didn't load in time | Increase timeout, check internet |
| `NoSuchElementException` | Selector not found | Check HTML structure, wait longer |
| `JSONDecodeError` | Malformed JSON | Check JavaScript parsing logic |
| `WebDriverException` | ChromeDriver issue | Update ChromeDriver, check Chrome |
| `ConnectionRefusedError` | Can't connect to site | Check internet, site may be down |

### Performance Monitoring

**Track Progress**:
```python
start_time = time.time()
completed = 0

# ... scraping ...

elapsed = time.time() - start_time
rate = completed / elapsed * 60  # Episodes per minute
eta = (total - completed) / rate  # Minutes remaining

print(f"Rate: {rate:.1f} eps/min, ETA: {eta:.0f}m")
```

**Memory Usage** (Linux):
```bash
ps aux | grep chrome
```

**Memory Usage** (Python):
```python
import psutil
process = psutil.Process()
print(f"Memory: {process.memory_info().rss / 1024 / 1024:.1f} MB")
```

---

## Advanced Topics

### Multi-Language Support

**Persian Text Handling**:
```python
# Always use UTF-8 encoding
with open(file, 'r', encoding='utf-8') as f:
    data = f.read()

# JSON with Persian characters
json.dump(data, f, ensure_ascii=False, indent=2)
```

**Title Extraction**:
```python
# Persian title
title_fa = soup.find('h1', class_='title-fa')

# English title
title_en = soup.find('h1', class_='title-en')

# Fallback to meta tags
if not title_en:
    meta_title = soup.find('meta', property='og:title')
    if meta_title:
        title_en = meta_title.get('content', '')
```

### Season Detection

**Problem**: Website doesn't explicitly show seasons

**Heuristics**:
```python
# Check URL for season indicators
if 'season-2' in show['url'] or 's02' in show['url']:
    season = 2

# Check title
if 'فصل دوم' in show['title_persian']:  # "Season 2" in Persian
    season = 2

# Episode count gaps (advanced)
# If 100+ episodes, likely multi-season
if len(episodes) > 100:
    # Divide by 20-50 per season (estimate)
    pass
```

**Reality**: No reliable way to detect seasons from website. Requires manual curation or external database.

### Incremental Updates

**Problem**: How to update catalog without re-scraping everything?

**Solution**: Track last scrape time
```python
checkpoint['last_update'] = time.time()

# Only scrape shows newer than last update
if show['added_date'] > checkpoint['last_update']:
    scrape_show(show)
```

**Challenge**: Website doesn't provide "added_date" - need to track manually

---

## Appendix: HTML Examples

### Series Page (Full)

```html
<!DOCTYPE html>
<html lang="fa">
<head>
    <meta charset="UTF-8">
    <title>Shoghaal | Negahestan.com</title>
    <meta property="og:title" content="Shoghaal">
    <meta property="og:image" content="https://media.negahestan.com/.../thumb.jpg">
</head>
<body>
    <div class="container">
        <div class="series-header">
            <h1 class="title-fa">شغال</h1>
            <h2 class="title-en">Shoghaal (Jackal)</h2>
        </div>

        <div id="divVideoHolder" class="video-container">
            <ul id="gridMason2" class="gridMasonTR">
                <li class="grid-item">
                    <a href="/series/shoghaal/episodes/shoghaal">
                        <img src="https://media.negahestan.com/.../ep1_thumb.jpg" alt="Episode 1">
                        <div class="overlay">
                            <span class="episode-num">1</span>
                        </div>
                    </a>
                </li>
                <li class="grid-item">
                    <a href="/series/shoghaal/episodes/shoghaal-2">
                        <img src="https://media.negahestan.com/.../ep2_thumb.jpg" alt="Episode 2">
                        <div class="overlay">
                            <span class="episode-num">2</span>
                        </div>
                    </a>
                </li>
                <!-- More episodes... -->
            </ul>
        </div>

        <div class="series-info">
            <div id="divVidDet09">Views: 553187</div>
            <div id="divVidDet10">Genre: Action, Thriller</div>
        </div>
    </div>
</body>
</html>
```

### Episode Page (Full)

```html
<!DOCTYPE html>
<html lang="fa">
<head>
    <meta charset="UTF-8">
    <title>Shoghaal Episode 1 | Negahestan.com</title>

    <script type="text/javascript">
        var seriesepisode_respose = {
            "video_url": [
                {
                    "android": "https://media.iranproud2.net/ipnx/media/series/episodes/Jackal_E01.mp4"
                },
                {
                    "ios": "https://media.iranproud2.net/ipnx/media/series/episodes/Jackal_E01.mp4"
                }
            ],
            "title": "Episode 1",
            "duration": "48:30",
            "thumbnail": "https://media.negahestan.com/.../ep1_thumb.jpg",
            "views": 12345
        };
    </script>
</head>
<body>
    <div class="container">
        <div id="divVideoHolder" class="video-holder">
            <!-- Video player loads here via JavaScript -->
            <div id="divVideoPlayer"></div>
        </div>

        <div class="episode-info">
            <h1>Shoghaal - Episode 1</h1>
            <p>Duration: 48:30</p>
        </div>
    </div>

    <script src="video-player.js"></script>
</body>
</html>
```

### Movie Page (Full)

```html
<!DOCTYPE html>
<html lang="fa">
<head>
    <meta charset="UTF-8">
    <title>Ehzar | Negahestan.com</title>
</head>
<body>
    <div class="container">
        <div id="divVideoHolder" class="fluid" ismankan="1">
            <div id="divVideoPlayer">
                <video class="video-js vjs-default-skin"
                       controls=""
                       id="videoTag"
                       poster="https://media.negahestan.com/ipnx/media/movies/thumbs/TheConjuring_LastRites_thumb.jpg"
                       preload="auto"
                       width="1080">
                    <source src="https://media.negahestan.com/ipnx/media/movies/TheConjuring_LastRites.mp4"
                            type="video/mp4"/>
                </video>
            </div>
        </div>

        <div class="movie-info">
            <h1>The Conjuring: Last Rites (Ehzar)</h1>
            <div id="divVidDet09">Views: 25341</div>
        </div>
    </div>

    <script src="video-js.min.js"></script>
</body>
</html>
```

---

## Version History

**v2.0** (2025-11-06)
- Combined series + movie scraping
- Added verification & gap filling
- Menu-driven interface
- Checkpoint management utilities

**v1.0** (2025-11-04)
- Initial separate scrapers
- Basic episode extraction
- Movie URL support

---

## Contact & Support

**Project**: Namakadeh Android TV App (Personal Project)
**Developer**: [Your Name]
**Last Updated**: 2025-11-06

**For Issues**:
1. Check this documentation first
2. Enable debug logging
3. Save error pages for inspection
4. Check website hasn't changed structure

**Website Changes**: If namakade.com changes their HTML structure, update selectors in script.

---

**END OF DOCUMENTATION**
