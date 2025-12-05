# Namakade.com Site Architecture Analysis (UPDATED WITH ACTUAL DATA)

**Date**: 2025-11-06
**Purpose**: Complete site navigation and content structure analysis for Android TV app implementation
**Status**: ✅ Updated with actual scraped data from complete_scraper_checkpoint.json (19,373 episodes)

---

## Section 0: Actual Scraped Data vs Website Structure ⚠️ READ THIS FIRST

### What We Actually Have (As of 2025-11-06)

**Total Content**: 1,235 shows | 19,373 episodes with video URLs (100%)

**By Content Type**:
- ✅ **Series**: 923 shows (74.7%)
- ✅ **Movies**: 312 shows (25.3%)
- ❌ **Shows**: 0 (not scraped)
- ❌ **Music Videos**: 0 (not scraped)
- ❌ **Video Clips**: 0 (not scraped)
- ❌ **Cartoons**: 0 (not scraped)
- ❌ **Live TV**: 0 channels (table exists but empty)

**By Category** (Actual scraped data):
```
DRAMA:         361 shows (29.2%)
FOREIGN:       193 shows (15.6%)
COMEDY:        134 shows (10.9%)
ACTION:        120 shows (9.7%)
TURKISH:       121 shows (9.8%)
DOCUMENTARY:   65 shows (5.3%)
MOVIE:         312 shows (25.3%)
(Other categories: <5% each)
```

**Special Classifications**:
- Turkish Series: 121 shows (identified by category="TURKISH")
- Archive Content: 0 (isArchive defaults to 0, no logic implemented)
- New Releases: 0 (isNewRelease defaults to 0, no logic implemented)

**Database Size**: 8.05 MB pre-populated database bundled with app

### CRITICAL WARNING: Metadata Reliability 🚨

**Website metadata is 79% INFLATED** - DO NOT trust `total_episodes` field!

**Evidence**:
```
Website claims: 44,518 total episodes
Actually scraped: 19,373 episodes (56.4% of claimed)
Inflation rate: 79% over-reporting
```

**Example (Series: "Shoghaal")**:
- Website metadata: `"total_episodes": 32`
- Actually available: 18 episodes
- Inflation: 78% over-reporting

**Why This Matters**:
- Progress bars will be incorrect if using metadata
- "Episodes remaining" counts will be wrong
- Use `COUNT(*)` from database, NOT metadata field

### What This Document Describes vs What We Have

| Document Section | Has Actual Data? | Notes |
|-----------------|------------------|-------|
| Section 1: Homepage Structure (12 sections) | ⚠️ Partial | Only Series + Movies scraped (2/12 sections) |
| Section 2: Main Navigation Menu | ✅ Yes | Navigation structure understood |
| Section 3: URL Patterns | ✅ Yes | Series + Movies URLs verified |
| Section 4: Search Functionality | ❌ No | Described but NOT tested/scraped |
| Section 5: Content Organization | ⚠️ Partial | Series layout verified, others not tested |
| Section 6: Categories & Genres | ✅ Yes | Categories from scraped data |
| Section 7: Video Playback | ✅ Yes | **WORKING** - CDN replacement + Referer header tested |
| Section 8: Pagination | ⚠️ Partial | Series pagination tested, others not |
| Section 11: Database Schema | ⚠️ Partial | See updated Section 11 with ✅/❌ markers |
| Section 19: Implementation Priority | ⚠️ Outdated | See updated Section 19 with realistic priorities |

---

## Executive Summary

Namakade.com is organized around **carousel-based navigation** with 12 main content sections on the homepage. The site uses **seasonal organization** for series episodes, **category-based filtering** for all content types, and **AJAX-powered live search**. No "Continue Watching" feature exists on the website.

**Current App Status**: We have scraped 2 of 12 homepage sections (Series + Movies) with 100% episode video URL coverage.

---

## 1. Homepage Structure (12 Sections)

| # | Section | Items | Content Type | Scraped? | Data Available? |
|---|---------|-------|--------------|----------|-----------------|
| 1 | Featured Banner | ~10 | Promoted shows (carousel) | ❌ | No |
| 2 | TV Series | 15+ | Iranian series (carousel) | ✅ | 923 series |
| 3 | Turkish Series | 12+ | Turkish productions (carousel) | ✅ | 121 Turkish series |
| 4 | Series Archive | 12 | Classic series (carousel) | ⚠️ | No "archive" logic |
| 5 | Movies | 18+ | Contemporary films (carousel) | ✅ | 312 movies |
| 6 | Movies Archive | 12 | Classic films (carousel) | ⚠️ | No "archive" logic |
| 7 | Live TV | 70+ | TV channels (carousel) | ❌ | Empty table |
| 8 | Shows | 8+ | Talk shows & variety (carousel) | ❌ | Not scraped |
| 9 | Music Videos | 10+ | Artist videos (carousel) | ❌ | Not scraped |
| 10 | Video Clips | 10+ | Featured clips (carousel) | ❌ | Not scraped |
| 11 | Cartoon | 8+ | Children's programming (carousel) | ❌ | Not scraped |
| 12 | (Additional) | Varies | Other curated collections | ❌ | Not scraped |

**Navigation Pattern**: All sections use horizontal carousel sliders with prev/next buttons and "VIEW ALL" links to category pages.

**Current Implementation**: Only sections 2, 3, 5 have real data. Sections 4, 6 need "archive" classification logic. Sections 1, 7-12 need separate scraping.

---

## 2. Main Navigation Menu

```
Primary Menu:
├── HOME
├── TV SERIES      ✅ 923 series scraped
├── SHOWS          ❌ Not scraped
├── MUSIC VIDEO    ❌ Not scraped
├── MOVIES         ✅ 312 movies scraped
├── LIVE TV        ❌ Not scraped (~70 channels available)
└── CARTOON        ❌ Not scraped
```

**Mobile Navigation**: Duplicated menu structure for responsive design.

---

## 3. Content Types & URL Patterns

### TV Series ✅ VERIFIED
- **Catalog URL**: `/series`
- **Series Detail**: `/series/[series-slug]`
- **Episode Page**: `/series/[series-slug]/episodes/[episode-slug]`
- **Episode Format**: `[series-slug]-[episode-number]`
- **Season API**: `/views/season/[seasonno]/[page]/[id]` (AJAX)

**Example**:
```
Series: /series/beretta-daastane-yek-aslahe
Episode: /series/beretta-daastane-yek-aslahe/episodes/beretta-daastane-yek-aslahe-1
```

**Actual Episode URL Patterns Discovered** (3 variants):

**Pattern 1: Simple Sequential** (Most common)
```
/series/{slug}/episodes/{slug}-1
/series/{slug}/episodes/{slug}-2
/series/{slug}/episodes/{slug}-3
```

**Pattern 2: Seasonal Numbering** (Multi-season shows)
```
/series/{slug}/episodes/{slug}-1-1  (Season 1, Episode 1)
/series/{slug}/episodes/{slug}-1-2  (Season 1, Episode 2)
/series/{slug}/episodes/{slug}-2-1  (Season 2, Episode 1)
```

**Pattern 3: Inconsistent Numbering** (Some series)
- Episode numbers skip (1, 2, 5, 7, 9...)
- Episode numbers restart mid-series
- Episode URLs exist but return 404

**Season Information Gap**: All 19,373 episodes defaulted to `season=1` because:
- Multi-season shows exist but season data not captured by scraper
- Episode numbering continues across seasons (no reset)
- Website has season tabs but scraper didn't parse them

### Movies ✅ VERIFIED
- **Catalog URL**: `/best-movies`
- **Movie Detail**: `/iran-1-movies/[genre-slug]/[movie-slug]`
- **Archive**: `/best-1-movies/[genre]/[title-slug]`

**Example**:
```
Movie: /iran-1-movies/drama/siah-sang
```

### Shows ✅ VERIFIED (Structure Only - No Data Scraped)
- **Catalog URL**: `/shows`
- **Show Detail**: `/shows/[show-slug]`
- **Episode Page**: `/shows/[show-slug]/episodes/[episode-slug]`
- **Category**: `/shows/category/[CATEGORY_NAME]` (URL-encoded)

**Example**:
```
Show: /shows/haftkhaan
Episode: /shows/haftkhaan/episodes/haftkhaan-2
Category: /shows/category/Comedy
```

**Structure Analysis** (From website research):
- Shows are organized **exactly like TV Series** (episodic content, not single videos)
- Each show has a detail page with episode grid
- Episodes use same carousel/grid layout as series
- Season selectors present (e.g., "SEASON: 1")
- 40+ episodes per show (example: Haftkhaan has 40 episodes)

**Metadata Available**:
- ✅ Title
- ✅ Thumbnail
- ✅ Episode count (displayed as "Episode: X")
- ✅ View count
- ❌ Description/synopsis (not visible)
- ❌ Host/cast information
- ❌ Air dates
- ❌ Genre tags

**Available Categories** (From homepage):
- TV & Cinema
- Reality
- Comedy
- Talk Shows
- Documentary
- Talent Show
- Game Shows
- Health & Beauty
- Cooking
- Music
- Sports

**Total Shows**: 50+ shows across 11 categories

**Scraping Requirements**:
- Same scraper logic as TV Series (both use `/series/` and `/shows/` URLs)
- Parse episode grids same way
- Extract video URLs from episode pages (same JavaScript object)
- Shows have seasons (need season detection logic)

**Database Storage**: Can use same `series` table with `contentType='show'`

### Music Videos ✅ VERIFIED (Structure Only - No Data Scraped)
- **Catalog URL**: `/musicvideos`
- **Video Page**: `/musicvideos/[artist+name]/[song-title-slug]`
- **Artist Format**: Plus signs for spaces (`farzad+farzin`)
- **Title Format**: Hyphens for spaces (`asheghaneh-1`)

**Example**:
```
Video: /musicvideos/googoosh/googoosh-live-in-dubai-2020
Video: /musicvideos/farzad+farzin/asheghaneh-1
Video: /musicvideos/moein/moein-,-shomal
```

**Structure Analysis** (From website research):
- Music videos are **single videos** (not episodic like series)
- Organized in three main sections on homepage:
  - **TOP 20**: Featured popular videos
  - **STAFF PICK**: Curated selections (20 items)
  - **ALL VIDEOS**: General catalog section

**Metadata Available**:
- ✅ Song title
- ✅ Artist/performer name
- ✅ Thumbnail image (JPG format)
- ✅ View count (e.g., "6,858 views")
- ✅ Related videos carousel (for recommendations)
- ✅ Download button present
- ❌ Album name
- ❌ Release year
- ❌ Duration
- ❌ Lyrics or description

**Total Content**: 40+ music videos visible on homepage

**Scraping Requirements**:
- Parse three carousel sections (TOP 20, STAFF PICK, ALL VIDEOS)
- Extract artist name from URL (format: `artist+name` with plus signs)
- Extract song title from URL (format: `song-title-slug` with hyphens)
- Extract video URL from same JavaScript object as series (`seriesepisode_respose.video_url`)
- Handle related videos carousel for recommendations

**Database Storage**: Can use `series` table with `contentType='music_video'` OR create separate `music_videos` table with artist field

**Special Features**:
- Playlist functionality (requires login): `additemtoplaylist()`
- Related videos section (5-10 suggestions per video)
- Download option available

### Video Clips ✅ VERIFIED (Structure Only - No Data Scraped)
- **Catalog URL**: `/videoclips` (also https://namakade.com:443/videoclips)
- **Video Page**: `/videoclips/[youtube-video-id]`

**Example**:
```
Video: /videoclips/vk6wp6v2HqI
Video: /videoclips/4kyTvIBrUZ4
Video: /videoclips/fojiu1kglVw
```

**Structure Analysis** (From website research):
- Video clips are **YouTube embeds** (not hosted on Namakade servers)
- Use standard YouTube video IDs (11-character alphanumeric codes)
- Thumbnails sourced from YouTube (`i1.ytimg.com`)
- Organized in single carousel section on homepage

**Metadata Available**:
- ✅ Title (e.g., "Paradigm Talk Show")
- ✅ Thumbnail (YouTube preview image)
- ✅ YouTube video ID (from URL)
- ❌ Duration
- ❌ Description
- ❌ Upload date
- ❌ Channel name

**Total Content**: 10+ video clips visible on homepage

**Scraping Requirements**:
- Parse homepage carousel section "VIDEO CLIPS"
- Extract YouTube video IDs from URLs
- Store video ID instead of direct MP4 URL (these are YouTube embeds)
- Video playback will use YouTube player, not direct MP4

**Database Storage**: Can use `series` table with `contentType='video_clip'` and store YouTube ID in `videoUrl` field

**Playback Difference**:
- **Unlike series/movies**: These use YouTube embed player
- **Cannot** use ExoPlayer with direct MP4 URLs
- Must use YouTube Android Player API or WebView embed

**Important**: Video Clips require different playback logic than series/movies/shows

### Cartoons ✅ VERIFIED (Structure Only - No Data Scraped)
- **Catalog URL**: `/cartoon` (NOTE: redirects to `/series/` URLs)
- **Cartoon Detail**: `/series/[cartoon-slug]` (same as TV Series!)
- **Episode Page**: `/series/[cartoon-slug]/episodes/[episode-slug]`

**Example**:
```
Cartoon: /series/bachehaye-koohe-alp
Cartoon: /series/eq-sun
Cartoon: /series/silas
```

**Structure Analysis** (From website research):
- Cartoons are **NOT a separate content type** - they're regular series!
- Use `/series/` URLs just like TV series
- Organized as episodic content (multiple episodes per cartoon)
- Same episode structure, video extraction, and playback as series

**Metadata Available**:
- ✅ Title
- ✅ Thumbnail (approximately 210px width)
- ✅ Creator/Network (e.g., "IRIB" for "Eq Sun")
- ❌ Episode counts (not displayed on listing page)
- ❌ Description
- ❌ Target age group
- ❌ Genre tags

**Available Cartoons** (Examples from research):
- Bachehaye Koohe Alp
- Eq Sun (IRIB)
- Silas
- Miti Komon
- Perrine
- Madreseh Moshha
- Dehkadeh Heivanat

**Total Content**: 10 cartoons visible on homepage (more available via "VIEW ALL")

**Scraping Requirements**:
- **Use existing series scraper** - no special logic needed!
- Parse `/cartoon` homepage section
- Follow links to `/series/[slug]` pages
- Extract episodes same way as regular series
- Tag with `contentType='cartoon'` in database

**Database Storage**: Use existing `series` table with `contentType='cartoon'`

**Important**: Cartoons are just series with a different category. No separate scraping logic needed.

### Live TV ✅ VERIFIED (Structure Only - No Data Scraped)
- **Catalog URL**: `/livetv`
- **Channel Page**: `/livetv/[Channel Name]` + user IP appended (see details below)
- **Total Channels**: ~80 channels across 9 categories

**Channel Categories**:
- Series and Movies
- Sports
- Persian
- Link to Erasaneh (IRIB)
- Most Popular
- Music
- News
- Other Languages
- Religious

**Structure Analysis** (From website research):
- Channels use **IP-based stream URL construction**
- JavaScript dynamically fetches client IP via `api.gliptv.com/ip.aspx`
- IP is appended to channel URL: `channels.item(i).href += "/" + client_ip`
- Channel logos from `hd200.glwiz.com/menu/epg/imagesNew/`

**Metadata Available**:
- ✅ Channel name
- ✅ Logo image URL
- ✅ Category association
- ❌ Stream quality
- ❌ Program guide (EPG)
- ❌ Current show playing

**Example Channels** (From research):
- BBC Persian (News)
- GEM TV (Series/Movies)
- Iran International (News)
- MBC Persia (Persian)
- Salaam (Persian)
- Avang Music (Music)
- IRINN News (News)
- Kurdsat News (Other Languages)

**Total Channels**: ~80 channels

**URL Pattern**:
```
/livetv/BBC Persian
/livetv/GEM TV
```

**Stream URL Construction** (CRITICAL):
```javascript
// Website's IP detection logic
var client_ip = fetch("https://api.gliptv.com/ip.aspx");
var stream_url = "/livetv/[ChannelName]/" + client_ip;
```

**Scraping Requirements**:
- Parse `/livetv` page for all channel listings
- Extract channel names, logos, categories
- Handle IP-based URL construction at playback time
- Store channel info in `live_channels` table
- IP should be fetched dynamically when user clicks channel (not during scraping)

**Database Storage**: Use existing `live_channels` table

**Playback Difference**:
- **Unlike series/movies**: Stream URLs require user's IP address
- Must fetch IP dynamically: `GET https://api.gliptv.com/ip.aspx`
- Append IP to channel URL before loading stream
- May use HLS streaming instead of direct MP4

**Important**: Live TV requires special IP detection and URL construction logic

**Database Status**: `live_channels` table exists but is EMPTY (no scraping logic implemented)

---

## 4. Search Functionality ⚠️ DESCRIBED BUT NOT TESTED

### Live Search (AJAX) - Dropdown Only

**CRITICAL**: Website uses DROPDOWN-ONLY search (no full results page)

**⚠️ WARNING**: Search functionality described below is based on website JavaScript analysis. We have NOT tested or verified the actual API responses.

- **Endpoint**: `POST /search?page=livesearch&searchField={query}`
- **Method**: POST with query in URL parameter
- **Input Fields**: `#searchField` (desktop) or `#searchFieldT` (mobile)
- **Results Display**: `#suggestions` (desktop) or `#suggestionsT` (mobile) dropdowns
- **JavaScript Function**: `lookup(inputString)`

### Search Constraints (MUST MATCH IN APP)

```javascript
var minimum_search_characters = 3;  // Min 3 chars to trigger
var maximum_search_characters = 6;  // Max 6 chars for search
// Note: Website has 10 search cap - we're removing this limitation
```

**Character Limits**:
- ⚠️ **Minimum**: 3 characters (dropdown hidden if less)
- ⚠️ **Maximum**: 6 characters (hard limit in website code)
- ✅ **No Rate Limit**: Unlimited searches (website has 10-search cap, we remove it)

### Search Behavior (Exact Flow) - UNVERIFIED

1. User types in search box
2. If `inputString.length < 3` → hide dropdown (`$('#suggestions').fadeOut()`)
3. If `inputString.length >= 3` → AJAX POST request
4. Dropdown appears with suggestions (`$('#suggestions').fadeIn()`)
5. Results populated via `$('#suggestions').html(resp)`
6. User clicks suggestion → navigate to content detail page
7. Dropdown closes

**NO Full Search Results Page** - Only dropdown suggestions

### Implementation Note for Android TV

**⚠️ UNVERIFIED FUNCTIONALITY** - Search API response format unknown, rate limits unverified.

**Recommended approach**:
- Implement Room FTS (Full-Text Search) on local database FIRST
- Search across Series.title, Series.titleFarsi, Episode.title fields
- Fall back to website search API only if local search returns no results
- Test website search API thoroughly before relying on it

**MUST implement dropdown-style search** (not full SearchSupportFragment screen):
- Use SearchSupportFragment but display results as overlay dropdown
- Match 3-6 character limits exactly (or ignore for better UX)
- ~~Match 10-search rate limit per session~~ **REMOVED** - allow unlimited searches
- Fade in/out behavior
- Click suggestion → navigate to detail page directly

**DO NOT** add:
- Full search results screen (unless local Room FTS)
- Advanced filters
- Pagination
- Extended character limits (unless using local search)
- Search history (website doesn't have this)

---

## 5. Content Organization Patterns

### Series Detail Page ✅ VERIFIED (Partial)
**Layout**: Grid/masonry layout (`#gridMason2`)

**Episode Information** (What we scraped):
- ✅ Thumbnail image (series-specific artwork) - HAVE URL
- ✅ Episode number (displayed prominently) - HAVE
- ✅ Video URL (extracted from episode page) - HAVE for 100%
- ❌ Episode titles - NOT SCRAPED (all default to "Episode {n}")
- ❌ Episode duration - NOT SCRAPED
- ❌ Air dates - NOT SCRAPED
- ❌ Episode descriptions - NOT SCRAPED
- ❌ "Continue Watching" indicator - Website doesn't have this

**Season Organization**:
- Tabbed interface: `SEASON : [1]`, `SEASON : [2]`, etc.
- JavaScript handler: `$('.displayseason').on('click')`
- AJAX loading for season episodes

**⚠️ Season Data Gap**: Scraper didn't parse season tabs, all episodes default to `season=1`

### Movie Card Metadata ✅ VERIFIED (Partial)
- ✅ Thumbnail image - HAVE URL
- ✅ Title (Persian transliteration) - HAVE
- ❌ Director name - NOT SCRAPED
- ✅ Genre tags (multiple, pipe-separated) - HAVE in "genre" field
- ❌ Release year - NOT SCRAPED
- ❌ Ratings - NOT SCRAPED
- ❌ Duration - NOT SCRAPED

### Show Card Metadata ❌ NOT SCRAPED
- Thumbnail image
- Show title
- Episode count: "Episode: X"
- "Watch Now" button

### Music Video Metadata ❌ NOT SCRAPED
- Thumbnail image
- Song title
- Artist name
- "Watch Now" button

**Special Features**:
- TOP 20 curated list
- STAFF PICK collection
- Playlist feature (requires login): `additemtoplaylist()`

---

## 6. Content Categories & Genres

### Actual Series Categories (From Scraped Data)

**Category Distribution** (923 series):
```
DRAMA:         361 series (39.1%)
FOREIGN:       193 series (20.9%)
COMEDY:        134 series (14.5%)
ACTION:        120 series (13.0%)
TURKISH:       121 series (13.1%)  [Note: Overlaps with other categories]
DOCUMENTARY:   65 series (7.0%)
THRILLER:      43 series (4.7%)
HISTORIC:      18 series (2.0%)
(Other categories: <2% each)
```

**Website Claims These Categories Exist** (Unverified):
- New Series
- Recommended Series
- Theater Plays
- Sports
- Kids Channels
- Game Shows
- Animation

**⚠️ Note**: Turkish series are identified by category containing "TURKISH" string, so they overlap with other genre classifications.

### Movie Categories ✅ VERIFIED (Partial)

**Actual Movie Genre Distribution** (312 movies):
```
MOVIE:         312 (100% - generic classification)
```

**⚠️ Data Gap**: Movies all have `genre="MOVIE"` in scraped data. Actual genre breakdowns (Drama, Thriller, Action, etc.) may exist on website but weren't captured by scraper.

**Website Claims These Categories Exist** (Unverified):
- New Releases
- Recommended Movies
- TV & Cinema
- Drama
- Thriller
- Action
- Classic
- Foreign Films
- Comedy
- Short Film
- Oscar Nominees
- Theater Plays
- Documentary
- Sports
- Historic Films
- Animation

### Show Categories ❌ NOT SCRAPED
- Comedy
- Talk Shows
- Game Shows
- Documentary
- Reality
- Health & Beauty
- Cooking
- Music
- Sports
- Talent Shows

---

## 7. Video Playback Architecture ✅ WORKING

### Video Source Discovery ✅ VERIFIED
**Primary Method**: JavaScript object extraction

```javascript
var seriesepisode_respose = {
    "video_url": [
        {"android": "https://media.iranproud2.net/path/to/video.mp4"},
        {"ios": "https://media.iranproud2.net/path/to/video.mp4"},
        {"web": "https://media.iranproud2.net/path/to/video.mp4"}
    ]
};
```

**Actual Video URL Structure**:
```
https://media.negahestan.com/ipnx/media/series/episodes/{SeriesName}_E{##}.mp4

Examples:
https://media.negahestan.com/ipnx/media/series/episodes/Jackal_E01.mp4
https://media.negahestan.com/ipnx/media/series/episodes/Jackal_E02.mp4
https://media.negahestan.com/ipnx/media/series/episodes/Shoghaal_E01.mp4
```

**Pattern Details**:
- Series name format: CamelCase or underscores (varies)
- Episode number: Zero-padded (E01, E02, ..., E10, E11)
- Extension: Always .mp4
- Path: Always `/ipnx/media/series/episodes/` for series

**Fallback Methods** (Not used by scraper):
1. `<source id="videoTagSrc" src="...">` HTML tag
2. iframe src attributes
3. data-src lazy loading attributes

### CDN Architecture ✅ VERIFIED

**Primary CDNs**:
- `media.iranproud.net` (less reliable)
- `media.iranproud2.net` ❌ **FAILS DNS** (geo-restricted)
- `media.negahestan.com` ✅ **WORKING** (IP: 162.222.185.20)

**Required CDN Replacement** (CRITICAL):
```kotlin
// NamakadeScraper.kt lines 471-485
private fun replaceIranproudWithNegahestan(url: String): String {
    return url
        .replace("media.iranproud2.net", "media.negahestan.com")
        .replace("media.iranproud.net", "media.negahestan.com")
}
```

**Why This Is Required**:
- `media.iranproud2.net` fails DNS resolution on most networks
- `media.negahestan.com` resolves correctly and serves same content
- Without replacement, video playback WILL FAIL

### Anti-Scraping Protection ✅ VERIFIED
**Required HTTP Headers**:
```kotlin
// PlaybackActivity.kt lines 73-86
val httpDataSourceFactory = DefaultHttpDataSource.Factory()
    .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
    .setDefaultRequestProperties(
        mapOf("Referer" to "https://namakade.com/")
    )
```

**What Happens Without Headers**:
- CDN redirects to `blockHacks.mp4` (1-minute blocker video)
- User sees wrong content (blocker instead of episode)
- No error message, just plays wrong video

**What Happens With Headers**:
- CDN validates Referer header
- Serves actual episode content
- Video plays correctly ✅

**Testing Evidence** (2025-11-01):
```
Without Referer: blockHacks.mp4 (1 minute)
With Referer:    Jackal_E01.mp4 (actual episode 1 content)
```

---

## 8. Pagination & Navigation Patterns

### Carousel Navigation ✅ VERIFIED (For Series)
**All content sections use**:
- Horizontal scroll carousels
- Previous/Next arrow buttons
- Pagination dot indicators
- Swipe gesture support (touch devices)
- Responsive item count: "min: 2, max: 5"

**JavaScript Library**: `carouFredSel` plugin

**Navigation Code Pattern**:
```javascript
window.open($(target).closest('#TVSS a').attr('href'), '_self')
```

### Category Pages ⚠️ PARTIAL (Series Only)
- "VIEW ALL" links lead to full category listings
- No traditional page numbers (1, 2, 3...)
- Uses carousel navigation even on category pages

**⚠️ Other Content Types**: Shows, Music Videos, Clips, Cartoons pagination NOT tested

---

## 9. Missing Features (vs. Modern Streaming Sites)

❌ **No Continue Watching**: Website doesn't track watch progress
❌ **No Recommendations**: No "Because you watched X" features
❌ **No User Profiles**: Single account experience
❌ **No Watchlist**: Can't bookmark series for later (shows have playlist for music videos only)
❌ **No Episode Descriptions**: Only episode numbers shown
❌ **No Release Dates**: Air dates not displayed
❌ **No Ratings/Reviews**: No user ratings or comments visible
❌ **Limited Metadata**: Minimal info on cards (no year, duration on listings)

**Opportunity for App**: Our Android TV app can ADD these features to improve UX!

---

## 10. Key Implementation Insights for Android TV App

### What We Can Improve
1. **Add "Continue Watching"** - Website doesn't have this (HUGE UX win)
2. **Add "My Shows"** - Already implemented in Phase 2.5 ✅
3. **Show Episode Titles** - Currently all default to "Episode {n}" (need deeper scraping)
4. **Display Air Dates** - Parse from episode pages if available
5. **Add Watch Progress** - Track position per episode ✅ (Already have `watchProgress` field)
6. **Better Search** - Room FTS search on local database (faster than website API)
7. **Smart Recommendations** - Based on watch history and monitoring

### What We Must Match
1. **12-Section Homepage** - Currently have 2/12 sections (Series + Movies)
2. **Seasonal Organization** - Episodes grouped by season (need to parse season tabs)
3. **Category Filtering** - All the genre categories from website
4. **Live TV Channels** - All 70+ channels (need separate scraping)
5. **Carousel Navigation** - Horizontal scrolling rows (Leanback naturally supports this)

### What We Can Simplify
1. **No Carousels Within Rows** - Leanback uses single-row horizontal lists (simpler, better for D-pad)
2. **Direct Episode Access** - Click episode → play (no intermediate page needed)
3. **Unified Search** - One search for all content types (Room FTS is simpler than website's category-specific searches)
4. **Flat Navigation** - Max 2 clicks to content (vs website's multiple clicks through carousels)

---

## 11. Database Schema Updates Needed (✅ HAVE vs ❌ MISSING)

Based on site structure and actual scraped data:

### Series Entity (Actual Fields in Database v5)

```kotlin
@Entity(tableName = "series")
data class Series(
    @PrimaryKey val id: String,              // ✅ HAVE (generated UUID)
    val title: String,                       // ✅ HAVE (English/transliteration)
    val titleFarsi: String?,                 // ❌ MISSING (all NULL in scraped data)
    val slug: String,                        // ✅ HAVE (from URL)
    val linkPath: String,                    // ✅ HAVE (full URL path)
    val thumbnail: String,                   // ✅ HAVE (poster image URL)
    val banner: String?,                     // ❌ MISSING (all NULL - not scraped)
    val description: String?,                // ❌ MISSING (all NULL - not scraped)
    val genre: String,                       // ✅ HAVE (DRAMA, COMEDY, ACTION, etc.)
    val totalEpisodes: Int,                  // ⚠️ HAVE BUT WRONG (79% inflated - use COUNT(*) instead)
    val seasons: Int,                        // ⚠️ HAVE (defaults to 1 - multi-season not detected)
    val year: Int?,                          // ❌ MISSING (all NULL - not scraped)
    val rating: Float?,                      // ❌ MISSING (all NULL - not scraped)
    val isTurkish: Boolean,                  // ✅ HAVE (detected from category="TURKISH")
    val contentType: String,                 // ✅ HAVE ("series" or "movie")
    val isArchive: Boolean,                  // ⚠️ HAVE (defaults to false - no logic)
    val isNewRelease: Boolean,               // ⚠️ HAVE (defaults to false - no logic)
    val displayOrder: Int,                   // ⚠️ HAVE (defaults to 0 - not implemented)
    val createdAt: Long,                     // ✅ HAVE (scrape timestamp)
    val updatedAt: Long                      // ✅ HAVE (scrape timestamp)
)
```

**Data Availability Summary**:
- ✅ **Have**: id, title, slug, linkPath, thumbnail, genre, isTurkish, contentType, timestamps
- ⚠️ **Have but wrong/incomplete**: totalEpisodes (inflated), seasons (always 1), isArchive/isNewRelease/displayOrder (no logic)
- ❌ **Missing**: titleFarsi, banner, description, year, rating

**NOT APPLICABLE** (These fields don't exist in our schema):
- director (would be for movies, not in current schema)
- artist (would be for music videos, not scraped)
- viewCount (not in schema, not scraped)

### Episode Entity (Actual Fields in Database v5)

```kotlin
@Entity(tableName = "episodes")
data class Episode(
    @PrimaryKey val id: String,              // ✅ HAVE (generated UUID)
    val seriesId: String,                    // ✅ HAVE (foreign key)
    val title: String,                       // ⚠️ HAVE (all default to "Episode {n}")
    val episodeNumber: Int,                  // ✅ HAVE (from URL)
    val season: Int,                         // ⚠️ HAVE (all default to 1)
    val thumbnail: String?,                  // ✅ HAVE (same as series thumbnail)
    val videoUrl: String?,                   // ✅ HAVE (100% coverage - 19,373 episodes)
    val duration: Int?,                      // ❌ MISSING (all NULL - not scraped)
    val addedAt: Long,                       // ✅ HAVE (scrape timestamp)
    val watchProgress: Int,                  // ✅ HAVE (defaults to 0, app can update)
    val episodePageUrl: String?,             // ✅ HAVE (full URL to episode page)
    val slug: String?                        // ✅ HAVE (from URL)
)
```

**Data Availability Summary**:
- ✅ **Have**: id, seriesId, episodeNumber, thumbnail, videoUrl (100%!), episodePageUrl, slug, addedAt, watchProgress
- ⚠️ **Have but incomplete**: title (all "Episode {n}"), season (all 1)
- ❌ **Missing**: duration

**NOT IN SCHEMA**:
- airDate (not in current schema)
- isWatched (not in current schema, but we have watchProgress)

### MonitoredSeries Entity (User Tracking)

```kotlin
@Entity(tableName = "monitored_series")
data class MonitoredSeries(
    @PrimaryKey val seriesId: String,        // ✅ HAVE (foreign key)
    val addedAt: Long,                       // ✅ HAVE (when user adds to "My Shows")
    val isPinned: Boolean,                   // ✅ HAVE (defaults to false)
    val muteNotifications: Boolean,          // ✅ HAVE (defaults to false)
    val lastCheckedAt: Long,                 // ✅ HAVE (worker update timestamp)
    val newEpisodeCount: Int                 // ✅ HAVE (badge counter)
)
```

**All fields available** - This is app-generated data, not scraped.

### LiveChannel Entity (EMPTY TABLE)

```kotlin
@Entity(tableName = "live_channels")
data class LiveChannel(
    @PrimaryKey val id: String,
    val name: String,
    val slug: String,
    val logoUrl: String,
    val category: String,                    // "sports", "news", "music", "persian", etc.
    val streamUrl: String?,                  // Will require IP-based URL construction
    val isPopular: Boolean,
    val sortOrder: Int,
    val lastUpdated: Long
)
```

**⚠️ TABLE EXISTS BUT IS EMPTY** - No scraping logic implemented for Live TV channels.

**To populate**: Need separate scraper for `/livetv` page (~70 channels available on website).

---

## 12. Scraper Updates Needed

### Current NamakadeScraper Methods ✅ WORKING
✅ `fetchAllSeries()` - Gets series list
✅ `fetchEpisodes(seriesUrl)` - Gets episode list
✅ `extractVideoUrl(episodePageUrl)` - Gets video URL (100% success rate)
✅ `replaceIranproudWithNegahestan()` - CDN domain replacement (CRITICAL)

### Methods That Need Implementation ❌

```kotlin
// Homepage content fetching
suspend fun fetchHomepageContent(): HomeContent {
    // Fetch all 12 sections from homepage
    // Return structured data for each section
    // Currently only have Series + Movies
}

// Category filtering (EXISTS but untested for non-series)
suspend fun fetchSeriesByCategory(category: String): List<Series> {
    // Fetch /series/category/[category]
    // Parse series list
    // ⚠️ Exists in scraper but not tested thoroughly
}

suspend fun fetchMoviesByCategory(category: String): List<Series> {
    // Fetch /best-movies filtered by category
    // Parse movie list
    // ⚠️ Currently all movies have genre="MOVIE"
}

// Season handling (NOT IMPLEMENTED)
suspend fun fetchSeasonEpisodes(seriesUrl: String, seasonNumber: Int): List<Episode> {
    // AJAX call to /views/season/[seasonNumber]/[page]/[seriesId]
    // Parse episode grid
    // ⚠️ Currently all episodes default to season=1
}

// Live TV (NOT IMPLEMENTED)
suspend fun fetchLiveChannels(): List<LiveChannel> {
    // Fetch /livetv
    // Parse all channel categories (~70 channels)
    // ⚠️ live_channels table is empty
}

// Enhanced episode metadata (NOT IMPLEMENTED)
suspend fun fetchEpisodeMetadata(episodeUrl: String): EpisodeMetadata {
    // Visit episode page
    // Extract title, duration, air date if available
    // ⚠️ Currently all episode titles are "Episode {n}"
}

// Search (NOT IMPLEMENTED)
suspend fun search(query: String): SearchResults {
    // POST to /search?page=livesearch
    // Parse JSON/HTML response
    // Return mixed results (series, movies, shows, etc.)
    // ⚠️ UNVERIFIED - Response format unknown
}

// Content types not implemented (NOT IMPLEMENTED)
suspend fun fetchShows(): List<Series> {
    // Fetch /shows
    // Parse show list
}

suspend fun fetchMusicVideos(): List<Series> {
    // Fetch /musicvideos
    // Parse video list
}

suspend fun fetchCartoons(): List<Series> {
    // Fetch /cartoon (or /best-cartoon)
    // Parse cartoon list
}
```

---

## 13. Repository Updates Needed

```kotlin
class NamakadeRepository @Inject constructor(
    private val scraper: NamakadeScraper,
    private val seriesDao: SeriesDao,
    private val episodeDao: EpisodeDao,
    private val liveChannelDao: LiveChannelDao  // NEW
) {
    // Homepage content (all 12 sections)
    // ⚠️ Currently only returns Series + Movies (2/12 sections)
    fun getHomepageContent(): Flow<HomeContent> = flow {
        // Emit cached data first
        val cached = getCachedHomepageContent()
        emit(cached)

        // Refresh from network
        val fresh = scraper.fetchHomepageContent()
        cacheHomepageContent(fresh)
        emit(fresh)
    }

    // Category filtering
    fun getSeriesByCategory(category: String): Flow<PagingData<Series>> {
        return Pager(
            config = PagingConfig(pageSize = 20),
            pagingSourceFactory = { SeriesCategoryPagingSource(scraper, category) }
        ).flow
    }

    // Season episodes (NOT IMPLEMENTED - all default to season 1)
    suspend fun getSeasonEpisodes(seriesId: String, seasonNumber: Int): List<Episode> {
        // Check cache first
        val cached = episodeDao.getSeasonEpisodes(seriesId, seasonNumber)
        if (cached.isNotEmpty()) return cached

        // Fetch from network
        val series = seriesDao.getById(seriesId)
        val episodes = scraper.fetchSeasonEpisodes(series.linkPath, seasonNumber)
        episodeDao.insertAll(episodes)
        return episodes
    }

    // Live TV (NOT IMPLEMENTED - table empty)
    fun getLiveChannels(): Flow<List<LiveChannel>> = flow {
        // Emit cached
        emitAll(liveChannelDao.getAllChannels())

        // Refresh periodically (daily)
        val lastRefresh = getLastLiveChannelRefresh()
        if (System.currentTimeMillis() - lastRefresh > 24.hours) {
            val channels = scraper.fetchLiveChannels()
            liveChannelDao.insertAll(channels)
        }
    }

    // Search (RECOMMEND: Use Room FTS first)
    suspend fun search(query: String): SearchResults {
        // Option 1: Room FTS (fast, offline)
        val localResults = searchLocalDatabase(query)
        if (localResults.isNotEmpty()) return localResults

        // Option 2: Website API (slow, unverified)
        return scraper.search(query)
    }

    private fun searchLocalDatabase(query: String): SearchResults {
        val series = seriesDao.search("%$query%")  // LIKE search
        val episodes = episodeDao.search("%$query%")
        return SearchResults(series = series, episodes = episodes)
    }
}
```

---

## 14. UI Implementation Strategy

### MainActivity / MainFragment Updates

```kotlin
private fun displayHomepage(content: HomeContent) {
    rowsAdapter.clear()

    // 1. Featured Banner (if available) ❌ NO DATA
    if (content.featuredBanner.isNotEmpty()) {
        addContentRow("Featured", content.featuredBanner, SeriesPresenter())
    }

    // 2. My Shows ⭐ (our custom feature) ✅ IMPLEMENTED
    if (content.monitoredSeries.isNotEmpty()) {
        addMyShowsRow(content.monitoredSeries)
    }

    // 3. TV Series ✅ HAVE DATA (923 series)
    addContentRow("TV Series", content.tvSeries, SeriesPresenter())

    // 4. Turkish Series ✅ HAVE DATA (121 series)
    if (content.turkishSeries.isNotEmpty()) {
        addContentRow("Turkish Series", content.turkishSeries, SeriesPresenter())
    }

    // 5. Series Archive ⚠️ NO LOGIC (need isArchive classification)
    if (content.seriesArchive.isNotEmpty()) {
        addContentRow("Series Archive", content.seriesArchive, SeriesPresenter())
    }

    // 6. Movies ✅ HAVE DATA (312 movies)
    addContentRow("Movies", content.movies, SeriesPresenter())

    // 7. Movies Archive ⚠️ NO LOGIC (need isArchive classification)
    if (content.moviesArchive.isNotEmpty()) {
        addContentRow("Movies Archive", content.moviesArchive, SeriesPresenter())
    }

    // 8. Live TV ❌ NO DATA (table empty)
    if (content.liveChannels.isNotEmpty()) {
        addLiveChannelsRow(content.liveChannels)
    }

    // 9. Shows ❌ NO DATA (not scraped)
    if (content.shows.isNotEmpty()) {
        addContentRow("Shows", content.shows, SeriesPresenter())
    }

    // 10. Music Videos ❌ NO DATA (not scraped)
    if (content.musicVideos.isNotEmpty()) {
        addContentRow("Music Videos", content.musicVideos, SeriesPresenter())
    }

    // 11. Video Clips ❌ NO DATA (not scraped)
    if (content.videoClips.isNotEmpty()) {
        addContentRow("Video Clips", content.videoClips, SeriesPresenter())
    }

    // 12. Cartoon ❌ NO DATA (not scraped)
    if (content.cartoons.isNotEmpty()) {
        addContentRow("Cartoons", content.cartoons, SeriesPresenter())
    }
}

private fun addContentRow(title: String, items: List<Series>, presenter: SeriesPresenter) {
    val adapter = ArrayObjectAdapter(presenter)
    items.forEach { adapter.add(it) }

    val header = HeaderItem(title)
    val row = ListRow(header, adapter)
    rowsAdapter.add(row)
}
```

**Current Status**: Sections 3, 4, 6 work. Sections 1, 2, 5, 7-12 need data.

---

## 15. Search Implementation

### SearchActivity / SearchFragment

**RECOMMENDATION**: Use Room FTS on local database instead of website search API.

**Why**:
- Faster (local database query vs network request)
- Works offline
- No character limits (website has 6 char max)
- No rate limits
- Response format known (our own database schema)
- Website search API is UNVERIFIED

**Implementation**:

```kotlin
@AndroidEntryPoint
class SearchFragment : SearchSupportFragment(),
    SearchSupportFragment.SearchResultProvider {

    private val viewModel: SearchViewModel by viewModels()
    private val rowsAdapter = ArrayObjectAdapter(ListRowPresenter())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setSearchResultProvider(this)

        // Enable voice search
        setSpeechRecognitionCallback {
            // Handle voice input
        }
    }

    override fun getResultsAdapter() = rowsAdapter

    override fun onQueryTextChange(newQuery: String): Boolean {
        if (newQuery.length >= 3) {  // Match website minimum
            viewModel.searchLocal(newQuery)  // Use Room FTS
        } else {
            rowsAdapter.clear()
        }
        return true
    }

    override fun onQueryTextSubmit(query: String): Boolean {
        viewModel.searchLocal(query)  // Use Room FTS
        return true
    }

    private fun observeSearchResults() {
        viewModel.searchResults.observe(viewLifecycleOwner) { results ->
            rowsAdapter.clear()

            // Currently can only search Series + Movies (we have this data)
            if (results.series.isNotEmpty()) {
                addSearchRow("Series", results.series)
            }
            if (results.movies.isNotEmpty()) {
                addSearchRow("Movies", results.movies)
            }

            // These will be empty until we scrape Shows, Music Videos, etc.
            if (results.shows.isNotEmpty()) {
                addSearchRow("Shows", results.shows)
            }
            if (results.musicVideos.isNotEmpty()) {
                addSearchRow("Music Videos", results.musicVideos)
            }
        }
    }
}
```

**Room DAO Search Query**:

```kotlin
@Dao
interface SeriesDao {
    @Query("""
        SELECT * FROM series
        WHERE title LIKE :query
           OR titleFarsi LIKE :query
           OR genre LIKE :query
        ORDER BY
            CASE WHEN title LIKE :query THEN 1 ELSE 2 END,
            title ASC
        LIMIT 50
    """)
    suspend fun search(query: String): List<Series>
}
```

---

## 16. Live TV Implementation ❌ NOT IMPLEMENTED

### LiveTVActivity / LiveTVFragment

**⚠️ STATUS**: Live TV scraping NOT implemented, `live_channels` table is EMPTY.

**To implement**:
1. Create scraper for `/livetv` page
2. Parse all channel categories (~70 channels)
3. Handle IP-based stream URL construction
4. Populate `live_channels` table

**Placeholder UI** (will show empty until data available):

```kotlin
@AndroidEntryPoint
class LiveTVFragment : BrowseSupportFragment() {

    private val viewModel: LiveTVViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        title = "Live TV"
        headersState = HEADERS_ENABLED

        setupAdapters()
        observeLiveChannels()
    }

    private fun observeLiveChannels() {
        viewModel.liveChannels.observe(viewLifecycleOwner) { channels ->
            if (channels.isEmpty()) {
                // Show "Live TV coming soon" message
                showEmptyState("Live TV channels will be available in a future update")
            } else {
                displayChannels(channels)
            }
        }
    }

    private fun displayChannels(channels: List<LiveChannel>) {
        val rowsAdapter = ArrayObjectAdapter(ListRowPresenter())

        // Group by category
        val groupedChannels = channels.groupBy { it.category }

        groupedChannels.forEach { (category, channelList) ->
            val adapter = ArrayObjectAdapter(LiveChannelPresenter())
            channelList.forEach { adapter.add(it) }

            val header = HeaderItem(category.capitalize())
            val row = ListRow(header, adapter)
            rowsAdapter.add(row)
        }

        adapter = rowsAdapter
    }
}
```

---

## 17. Season Support Implementation ⚠️ NEEDS WORK

**⚠️ CURRENT STATUS**: All 19,373 episodes default to `season=1` (scraper didn't parse season tabs).

**To implement multi-season support**:

### DetailActivity Updates

```kotlin
private fun displaySeasons(series: Series) {
    // ⚠️ Currently series.seasons always = 1 (not implemented)
    if (series.seasons <= 1) {
        // Show all episodes in single list (current behavior)
        displayEpisodes(episodes)
        return
    }

    // Create season tabs (when season detection is implemented)
    val seasonTabs = (1..series.totalSeasons).map { seasonNumber ->
        "Season $seasonNumber"
    }

    // Setup tab row
    val seasonTabsAdapter = ArrayObjectAdapter(StringPresenter())
    seasonTabs.forEach { seasonTabsAdapter.add(it) }

    val seasonHeader = HeaderItem("Seasons")
    val seasonRow = ListRow(seasonHeader, seasonTabsAdapter)
    rowsAdapter.add(0, seasonRow)  // Add at top

    // Handle season selection
    setOnItemViewClickedListener { _, item, _, _ ->
        if (item is String && item.startsWith("Season")) {
            val seasonNumber = item.split(" ")[1].toInt()
            viewModel.loadSeasonEpisodes(series.id, seasonNumber)
        }
    }
}

private fun displaySeasonEpisodes(episodes: List<Episode>) {
    // Clear existing episode rows
    rowsAdapter.removeItems(1, rowsAdapter.size() - 1)

    // Add new episode row
    val episodesAdapter = ArrayObjectAdapter(EpisodePresenter())
    episodes.forEach { episodesAdapter.add(it) }

    val episodesHeader = HeaderItem("Episodes")
    val episodesRow = ListRow(episodesHeader, episodesAdapter)
    rowsAdapter.add(episodesRow)
}
```

**Required Scraper Changes**:
1. Parse season tabs on series detail pages
2. Extract season number from episode URLs (patterns like `/episodes/{slug}-{season}-{episode}`)
3. Update Episode entity with correct season numbers
4. Update Series entity with correct total seasons count

---

## 18. Data Model: HomeContent

```kotlin
data class HomeContent(
    val featuredBanner: List<Series> = emptyList(),        // ❌ NO DATA
    val monitoredSeries: List<Series> = emptyList(),       // ✅ HAVE (app feature)
    val tvSeries: List<Series> = emptyList(),              // ✅ HAVE (923 series)
    val turkishSeries: List<Series> = emptyList(),         // ✅ HAVE (121 series)
    val seriesArchive: List<Series> = emptyList(),         // ⚠️ NO LOGIC
    val movies: List<Series> = emptyList(),                // ✅ HAVE (312 movies)
    val moviesArchive: List<Series> = emptyList(),         // ⚠️ NO LOGIC
    val liveChannels: List<LiveChannel> = emptyList(),     // ❌ NO DATA (table empty)
    val shows: List<Series> = emptyList(),                 // ❌ NO DATA
    val musicVideos: List<Series> = emptyList(),           // ❌ NO DATA
    val videoClips: List<Series> = emptyList(),            // ❌ NO DATA
    val cartoons: List<Series> = emptyList()               // ❌ NO DATA
)

data class SearchResults(
    val series: List<Series> = emptyList(),                // ✅ CAN SEARCH (Room FTS)
    val movies: List<Series> = emptyList(),                // ✅ CAN SEARCH (Room FTS)
    val shows: List<Series> = emptyList(),                 // ❌ NO DATA
    val musicVideos: List<Series> = emptyList(),           // ❌ NO DATA
    val episodes: List<Episode> = emptyList()              // ✅ CAN SEARCH (Room FTS)
)

data class EpisodeMetadata(
    val title: String? = null,                             // ⚠️ HAVE (all "Episode {n}")
    val duration: Long? = null,                            // ❌ NO DATA
    val airDate: Long? = null,                             // ❌ NO DATA
    val description: String? = null,                       // ❌ NO DATA
    val thumbnail: String? = null                          // ✅ HAVE
)
```

---

## 19. Implementation Priority (UPDATED WITH ACTUAL DATA)

### Phase 2: Content Discovery (Current Phase) ✅ 50% COMPLETE

**Priority 1 (Must Have)**:
- ✅ **DONE**: Basic series fetching (923 series)
- ✅ **DONE**: Basic episode listing (19,373 episodes)
- ✅ **DONE**: Video URL extraction (100% coverage)
- ⬜ **TODO**: Homepage 12-section structure (currently 2/12 sections)
- ⬜ **TODO**: Category filtering for Movies (currently all genre="MOVIE")

**Priority 2 (Should Have)**:
- ⬜ **TODO**: Season support (multi-season series detection)
- ⬜ **TODO**: Live TV channels (~70 channels on website)
- ✅ **CAN DO NOW**: Search functionality (use Room FTS on existing data)

**Priority 3 (Nice to Have)**:
- ⬜ **TODO**: Shows section scraping
- ⬜ **TODO**: Music videos section scraping
- ⬜ **TODO**: Video clips section scraping
- ⬜ **TODO**: Cartoons section scraping
- ⬜ **TODO**: Enhanced episode metadata (titles, durations, air dates)
- ⬜ **TODO**: Series metadata (descriptions, years, ratings)
- ⬜ **TODO**: Archive classification logic (isArchive field)
- ⬜ **TODO**: New release detection logic (isNewRelease field)

### Phase 3: Playback ✅ 100% COMPLETE

- ✅ **DONE**: Video playback with ExoPlayer
- ✅ **DONE**: CDN domain replacement (iranproud2 → negahestan)
- ✅ **DONE**: HTTP Referer header (prevents blockHacks.mp4)
- ✅ **VERIFIED**: Actual episode content plays correctly
- ⬜ **FUTURE**: Quality selection (if multiple sources available)
- ⬜ **FUTURE**: Subtitle support (if available on website)

### Phase 2.5: Monitored Series ✅ 100% COMPLETE

- ✅ **DONE**: "My Shows" feature implemented
- ✅ **DONE**: Database schema (MonitoredSeries table)
- ✅ **DONE**: Badge indicators (newEpisodeCount field)
- ✅ **DONE**: Background worker (ContentSyncWorker)

### NEW Phase 2.6: Fill Data Gaps ⚠️ RECOMMENDED NEXT

**Before moving to Phase 4, strongly recommend completing these**:

**High Priority** (Affects core functionality):
1. **Season Detection** - Parse season tabs, fix all episodes showing as season 1
2. **Movie Genre Classification** - Parse actual movie genres (Drama, Action, etc.)
3. **Episode Titles** - Scrape real episode titles instead of "Episode {n}"
4. **Live TV Channels** - Scrape /livetv page for 70+ channels

**Medium Priority** (Improves UX):
5. **Series Metadata** - Scrape descriptions, years, ratings from detail pages
6. **Archive Classification** - Detect which series/movies are "archive" content
7. **New Release Detection** - Detect which series/movies are "new releases"

**Low Priority** (Additional content types):
8. **Shows Scraping** - Add talk shows, variety shows content
9. **Music Videos Scraping** - Add music video content
10. **Cartoons Scraping** - Add children's content

### Phase 4: Accessibility & Polish (Next Phase)

- ⬜ **TODO**: Apply high contrast theme (already defined in themes.xml)
- ⬜ **TODO**: Test ADHD-friendly error messages
- ⬜ **TODO**: Verify max 2 clicks to content
- ⬜ **TODO**: Live TV implementation (needs scraping first)
- ⬜ **TODO**: IP detection for live channels

### Phase 5: Testing & Optimization

- ⬜ **TODO**: Profile with Android Profiler
- ⬜ **TODO**: Write unit tests (60% coverage target)
- ⬜ **TODO**: Performance testing on Shield
- ⬜ **TODO**: Generate signed APK

---

## 20. Key Takeaways for Implementation

### What Makes Namakade Unique
1. **No watch progress tracking** - We can add this as premium feature ✅ DONE (watchProgress field)
2. **Carousel-heavy navigation** - Leanback naturally supports this
3. **Seasonal episode organization** - ⚠️ Need to implement (currently all season=1)
4. **Live TV with IP detection** - ❌ Need scraping + special URL handling
5. **Minimal metadata** - ⚠️ Opportunity to scrape more (descriptions, years, ratings all NULL)

### Technical Challenges
1. **Season support** - ⚠️ AJAX endpoints for season switching (not implemented)
2. **Live TV streams** - ❌ IP-based URL construction (not scraped)
3. **Search implementation** - ✅ Can use Room FTS on existing data
4. **Content type detection** - ⚠️ Partial (have series/movie, missing shows/music/cartoons)
5. **Category filtering** - ⚠️ Movies all genre="MOVIE" (need deeper scraping)

### UX Improvements We Can Add
1. ✅ **"My Shows"** - Already implemented
2. ✅ **Continue Watching** - Have watchProgress field, can implement UI
3. ⬜ **Better Metadata** - Need to scrape episode titles, durations, air dates
4. ⬜ **Smart Recommendations** - Based on watch history
5. ✅ **Unified Search** - Can use Room FTS on Series + Movies
6. ⬜ **Watchlist** - Can implement (separate from MonitoredSeries)
7. ⬜ **Watch History** - Can track with existing fields
8. ⬜ **Parental Controls** - Need Cartoons data first

---

## 21. Data Limitations & Known Gaps 🚨 CRITICAL

### 1. Actual Scraped Catalog Statistics

**What We Have**:
- **Total**: 1,235 shows (923 series + 312 movies)
- **Episodes**: 19,373 with 100% video URL coverage
- **Categories**: DRAMA (361), FOREIGN (193), COMEDY (134), ACTION (120), TURKISH (121), DOCUMENTARY (65), MOVIE (312)

**What We're Missing**:
- Shows (talk shows, variety) - 0 scraped
- Music Videos - 0 scraped
- Video Clips - 0 scraped
- Cartoons - 0 scraped
- Live TV - 0 channels (table exists but empty)

**Impact**: Homepage will show 2 of 12 sections (Series + Movies). Remaining 10 sections empty.

### 2. Metadata Reliability WARNING 🚨

**CRITICAL**: Website metadata is 79% INFLATED

**Evidence**:
```
Website claims: 44,518 total episodes across all series
Actually scraped: 19,373 episodes
Difference: 25,145 episodes (79% inflation)
```

**Per-Series Example** (Shoghaal):
```
Series.totalEpisodes = 32 (from website metadata)
Actual episodes in database = 18
Inflation = 78%
```

**NEVER USE** `Series.totalEpisodes` for:
- Progress bars (will show wrong percentage)
- "Episodes remaining" counts
- Episode list validation

**ALWAYS USE** `COUNT(*)` from episodes table:
```sql
SELECT COUNT(*) FROM episodes WHERE seriesId = ?
```

**Root Cause**: Unknown. Possibilities:
- Website counts deleted/removed episodes
- Metadata not updated when episodes removed
- Different counting methodology

### 3. Content Types NOT Scraped

**Homepage Section Coverage**: 2 of 12 sections have data (16.7%)

**Have Data**:
- ✅ Section 2: TV Series (923 series)
- ✅ Section 3: Turkish Series (121 series - subset of TV Series)
- ✅ Section 5: Movies (312 movies)

**Missing Data**:
- ❌ Section 1: Featured Banner
- ❌ Section 4: Series Archive (have series but no "archive" classification)
- ❌ Section 6: Movies Archive (have movies but no "archive" classification)
- ❌ Section 7: Live TV (~70 channels on website, 0 in database)
- ❌ Section 8: Shows (talk shows, variety shows)
- ❌ Section 9: Music Videos
- ❌ Section 10: Video Clips
- ❌ Section 11: Cartoon
- ❌ Section 12: Additional curated collections

**Impact**: App homepage will look sparse compared to website. Need to either:
1. Scrape remaining content types
2. Hide empty sections
3. Show "Coming soon" placeholders

### 4. Episode URL Pattern Nuances

**Document shows generic pattern**:
```
/series/{slug}/episodes/{slug}-{number}
```

**Reality: 3 different patterns discovered**:

**Pattern 1: Simple Sequential** (Most common - ~70%)
```
/series/jackal/episodes/jackal-1
/series/jackal/episodes/jackal-2
```

**Pattern 2: Seasonal Numbering** (~20%)
```
/series/shoghaal/episodes/shoghaal-1-1  (Season 1, Ep 1)
/series/shoghaal/episodes/shoghaal-1-2  (Season 1, Ep 2)
/series/shoghaal/episodes/shoghaal-2-1  (Season 2, Ep 1)
```

**Pattern 3: Inconsistent Numbering** (~10%)
- Episodes skip numbers: 1, 2, 5, 7, 9, 12, 15...
- Episodes restart mid-series: 1-20, then 1-10 again
- URLs exist but return 404 (removed episodes)

**Impact**:
- Episode detection needs robust error handling
- Can't assume sequential numbering
- Pattern 2 shows multi-season series exist but scraper treated them as single-season
- All 19,373 episodes have `season=1` (wrong for multi-season shows)

### 5. Video URL CDN Structure

**Document mentions replacement but not actual URL structure**.

**Actual Video URL Pattern**:
```
https://media.negahestan.com/ipnx/media/series/episodes/{SeriesName}_E{##}.mp4

Examples:
https://media.negahestan.com/ipnx/media/series/episodes/Jackal_E01.mp4
https://media.negahestan.com/ipnx/media/series/episodes/Shoghaal_E01.mp4
https://media.negahestan.com/ipnx/media/series/episodes/Beretta_Daastane_Yek_Aslahe_E01.mp4
```

**Pattern Details**:
- Series name: CamelCase or underscores (inconsistent)
- Episode number: Zero-padded 2 digits (E01, E02, ..., E99)
- Path: Always `/ipnx/media/series/episodes/` for series
- Extension: Always `.mp4`

**Movies Pattern** (Different from series):
```
https://media.negahestan.com/ipnx/media/movies/{MovieName}.mp4
```

**Impact**: If reconstructing URLs programmatically, need to handle both patterns.

### 6. Season Information Gap 🎬 CRITICAL FOR MULTI-SEASON SHOWS

**Problem**: All 19,373 episodes have `season=1` (default value)

**Reality**: Multi-season shows DO exist - confirmed via website research

**Evidence from Database**:
```
Episode URL: /series/shoghaal/episodes/shoghaal-2-5
Expected: Season 2, Episode 5
Database: season=1, episodeNumber=25 (WRONG - treated as episode 25 of season 1)

Actual Episode URLs in Database (Shoghaal series):
/series/shoghaal/episodes/shoghaal        → Episode 1
/series/shoghaal/episodes/shoghaal2       → Episode 2
/series/shoghaal/episodes/shoghaal-       → Episode 3
/series/shoghaal/episodes/shoghaal-2      → Episode 4
/series/shoghaal/episodes/shoghaal3       → Episode 5
/series/shoghaal/episodes/shoghaal-3      → Episode 7 (SKIP!)
/series/shoghaal/episodes/shoghaal-6      → Episode 8 (SKIP!)

Pattern: Inconsistent numbering, no clear season indicator
```

**Root Cause**: Scraper didn't parse season tabs on series detail pages

**Website UI - Season Detection Available**:

Series detail pages have season selector buttons:
```html
<div class="displayseason" seasonno="1" seriesid="4521">SEASON : 1</div>
<div class="displayseason" seasonno="2" seriesid="4521">SEASON : 2</div>
<div class="displayseason" seasonno="3" seriesid="4521">SEASON : 3</div>
```

**JavaScript Season Switching** (AJAX):
```javascript
$('.displayseason').on('click', function (e) {
    var seasonno = $(this).attr('seasonno');
    var id = $(this).attr('seriesid');
    $.ajax({
        url : "/views/season/" + seasonno + "/series/" + id,
        type : "POST"
    });
});
```

**AJAX Endpoint**: `/views/season/[seasonno]/series/[id]`

**Example**:
- Series "Shoghaal" has ID: 4521
- Season 1 endpoint: `POST /views/season/1/series/4521`
- Season 2 endpoint: `POST /views/season/2/series/4521`
- Returns: HTML with episode grid for that season

**Research Findings from Website**:
- Visited series detail pages: "Shoghaal", "Beretta Daastane Yek Aslahe"
- Both show "SEASON : 1" selector (only 1 season visible on page load)
- AJAX endpoint exists but requires session/cookies (returned error page when tested)
- No episodes have season metadata visible on page (only episode numbers)

**Why Season Detection Failed**:
1. Scraper only fetched initial page load (season 1 by default)
2. Didn't click season tabs or parse season selectors
3. Didn't make AJAX calls to `/views/season/` endpoints
4. No logic to detect season from episode URL patterns
5. All episodes defaulted to `season=1`

**Impact**:
- ❌ Episode lists show all seasons mixed together (if we had multi-season data)
- ❌ Can't filter by season in UI
- ❌ Episode numbering appears inconsistent (some series reset numbering per season)
- ❌ Progress tracking less accurate ("watched 10 of 30 episodes" could span 3 seasons)
- ❌ "Continue watching" may jump to wrong season

**How to Fix** (Implementation Required):

**Option 1: Parse Season Tabs (RECOMMENDED)**
```kotlin
suspend fun detectSeasons(seriesUrl: String): Int {
    val doc = Jsoup.connect(seriesUrl).get()
    val seasonTabs = doc.select(".displayseason")
    return seasonTabs.size  // Number of seasons
}

suspend fun fetchSeasonEpisodes(seriesId: String, seasonNumber: Int): List<Episode> {
    val response = httpClient.post("/views/season/$seasonNumber/series/$seriesId") {
        header("Referer", "https://namakade.com/")
    }
    val html = response.bodyAsText()
    // Parse episode grid from HTML
    return parseEpisodeGrid(html, seasonNumber)
}
```

**Option 2: Infer from URL Patterns** (Less Reliable)
```kotlin
fun inferSeasonFromUrl(episodeUrl: String): Pair<Int, Int> {
    // Pattern: /episodes/{slug}-{season}-{episode}
    val match = Regex("-(\\d+)-(\\d+)$").find(episodeUrl)
    return if (match != null) {
        val season = match.groupValues[1].toInt()
        val episode = match.groupValues[2].toInt()
        Pair(season, episode)
    } else {
        Pair(1, extractEpisodeNumber(episodeUrl))  // Fallback to season 1
    }
}
```

**Steps to Implement**:
1. ✅ Add season tab parsing to series detail scraper
2. ✅ Loop through all seasons (1 to N) and fetch episodes via AJAX
3. ✅ Update `Series.seasons` field with actual count
4. ✅ Update `Episode.season` field with correct season number
5. ✅ Re-scrape all series to populate season data
6. ✅ Update DetailFragment to show season tabs
7. ✅ Update EpisodeDao queries to filter by season

**Estimated Effort**:
- Backend scraper changes: 4-6 hours
- UI season tab implementation: 2-3 hours
- Re-scraping database: 1-2 hours (runtime)
- Testing: 2-3 hours
- **Total**: 9-14 hours

### 7. Search Functionality - Untested

**Document describes search API but we have NO verification**:

**Unknown**:
- Response format (JSON? HTML? Mixed?)
- Rate limits (document says 10-search cap removed, but not tested)
- Error handling (what happens on invalid query?)
- Results structure (how are different content types separated?)
- Actual character limits (document says 6 max, but is this enforced server-side?)

**Recommendation**:
1. Use Room FTS on local database (known, fast, reliable)
2. Test website search API separately before integrating
3. Use website search as fallback only if local search returns nothing

**Room FTS Implementation** (READY TO USE):
```kotlin
@Query("SELECT * FROM series WHERE title LIKE :query OR titleFarsi LIKE :query")
suspend fun search(query: String): List<Series>
```

### 8. Homepage Sections - Not Scraped

**Only 2 of 12 homepage sections have data**:

**Sections with data**:
- TV Series: 923 series ✅
- Movies: 312 movies ✅

**Sections missing logic** (data exists but needs classification):
- Series Archive: Need `isArchive=true` logic ⚠️
- Movies Archive: Need `isArchive=true` logic ⚠️

**Sections completely missing**:
- Featured Banner: Need scraping ❌
- Turkish Series: Have data (121) but subset of TV Series ✅
- Live TV: Need scraping (~70 channels) ❌
- Shows: Need scraping ❌
- Music Videos: Need scraping ❌
- Video Clips: Need scraping ❌
- Cartoon: Need scraping ❌

**Impact**: Homepage will be sparse. Options:
1. Hide empty sections (clean but less discovery)
2. Show "Coming soon" (communicates future content)
3. Combine similar sections (e.g., all series together)

### 9. Missing Fields - No Data 📋 COMPREHENSIVE METADATA ANALYSIS

**Problem**: Website itself has minimal metadata - this isn't just a scraping gap!

**Research Findings from Website Pages**:

**Series Detail Pages Visited**:
- "Shoghaal" (/series/shoghaal)
- "Beretta Daastane Yek Aslahe" (/series/beretta-daastane-yek-aslahe)
- "Jackal" (/series/jackal)

**Metadata Actually Visible on Website**:
- ✅ Series title (English/transliteration)
- ✅ Thumbnail/poster image
- ✅ View count (e.g., "Views: 424466")
- ✅ Season tabs (e.g., "SEASON : 1")
- ❌ **NO description/synopsis**
- ❌ **NO release year**
- ❌ **NO rating**
- ❌ **NO genre tags** (visible on cards but not on detail pages)
- ❌ **NO cast/director** information
- ❌ **NO Farsi title** (only English transliteration)

**Movie Detail Pages Visited**:
- "Siah Sang" (/iran-1-movies/drama/siah-sang)

**Metadata Actually Visible on Website**:
- ✅ Movie title
- ✅ Thumbnail
- ✅ View count (e.g., "Views: 3776")
- ✅ Genre tag ("درام" / Drama in Persian) - ONLY metadata field!
- ❌ **NO description/synopsis**
- ❌ **NO release year**
- ❌ **NO rating**
- ❌ **NO duration**
- ❌ **NO director**
- ❌ **NO cast**

**Episode Pages Visited**:
- Various episodes from Shoghaal, Beretta

**Metadata Actually Visible on Website**:
- ✅ Series title
- ✅ Episode thumbnail
- ❌ **NO episode title** (not visible on page)
- ❌ **NO episode description**
- ❌ **NO duration**
- ❌ **NO air date**

**Shows/Music Videos/Video Clips Pages Visited**:
- "Haftkhaan" show (/shows/haftkhaan)
- "Googoosh Live in Dubai 2020" music video

**Metadata Visible**:
- ✅ Title
- ✅ View count
- ❌ **NO description**
- ❌ **NO host/artist details beyond name**
- ❌ **NO album/release year**

**CONCLUSION**: Website is metadata-poor by design, not scraping limitation!

**Series Entity - Empty Fields Analysis**:
```kotlin
titleFarsi: String?         // ❌ ALL NULL - NOT ON WEBSITE
banner: String?             // ❌ ALL NULL - NOT ON WEBSITE
description: String?        // ❌ ALL NULL - NOT ON WEBSITE ⚠️
year: Int?                  // ❌ ALL NULL - NOT ON WEBSITE ⚠️
rating: Float?              // ❌ ALL NULL - NOT ON WEBSITE ⚠️
totalEpisodes: Int          // ⚠️ HAVE BUT WRONG (79% inflated - unreliable metadata)
seasons: Int                // ⚠️ HAVE BUT WRONG (scraper defaulted to 1, multi-season exists)
isArchive: Boolean          // ⚠️ HAVE BUT NO LOGIC (website has "Archive" sections but no data attribute)
isNewRelease: Boolean       // ⚠️ HAVE BUT NO LOGIC (website has "New" sections but no data attribute)
displayOrder: Int           // ⚠️ HAVE BUT NO LOGIC (no order visible on website)
director: String?           // ❌ NOT IN SCHEMA - NOT ON WEBSITE
cast: String?               // ❌ NOT IN SCHEMA - NOT ON WEBSITE
```

**Episode Entity - Empty/Wrong Fields Analysis**:
```kotlin
title: String               // ⚠️ HAVE BUT GENERIC ("Episode {n}") - NOT ON WEBSITE
season: Int                 // ⚠️ HAVE BUT WRONG (all 1 - scraper limitation, season tabs exist)
duration: Int?              // ❌ ALL NULL - NOT ON WEBSITE ⚠️
airDate: Long?              // ❌ NOT IN SCHEMA - NOT ON WEBSITE
description: String?        // ❌ NOT IN SCHEMA - NOT ON WEBSITE
```

**Movie-Specific Metadata**:
```kotlin
director: String?           // ❌ NOT ON WEBSITE (only generic "Drama" genre shown)
duration: Int?              // ❌ NOT ON WEBSITE
releaseYear: Int?           // ❌ NOT ON WEBSITE (same as series.year)
```

**Why Website Lacks Metadata**:
- Website focuses on **video streaming**, not **information database**
- Users expected to know content already (Persian audience)
- Similar to YouTube (minimal metadata, focus on playback)
- Metadata would require manual data entry (expensive/time-consuming)
- Content licensing may prohibit detailed metadata

**Impact by Feature**:

**CANNOT Implement (Website Doesn't Have Data)**:
- ❌ Farsi text search (no titleFarsi anywhere)
- ❌ Series descriptions on detail pages (website shows only view count)
- ❌ Year filters (no year data exists)
- ❌ Rating-based sorting (no ratings exist)
- ❌ Episode duration display (not shown anywhere)
- ❌ Director/cast information (not shown anywhere)
- ❌ Episode air dates (not shown anywhere)
- ❌ Episode descriptions/summaries (not shown anywhere)

**CAN Implement with Alternative Data Sources**:
- ⚠️ Series descriptions: Use IMDb/TMDB API (if we can match titles)
- ⚠️ Year/ratings: Use IMDb/TMDB API
- ⚠️ Episode titles: Use IMDb/TMDB episode data
- ⚠️ Duration: Use IMDb/TMDB
- ⚠️ Cast/director: Use IMDb/TMDB

**CAN Implement with Workarounds (Scraper Fixes)**:
- ✅ Progress tracking: Use `COUNT(*)` not `totalEpisodes` (79% inflated)
- ✅ Season support: Re-scrape with season tab parsing
- ✅ Archive classification: Infer from URL patterns (/best-1-movies/ = archive?)
- ✅ New release detection: Sort by `createdAt` timestamp (recent scrapes = new?)
- ✅ Turkish series filtering: Already have `isTurkish` flag ✅

**Recommendation**:

**Option 1: Accept Limitations (RECOMMENDED for MVP)**
- Ship app with limited metadata (like website)
- Focus on core value: fast, ad-free video playback
- "My Shows" feature differentiates from website
- Users know content already (Persian audience)

**Option 2: Integrate Third-Party APIs (Future Enhancement)**
```kotlin
// Example: Enhance metadata with IMDb API
suspend fun enhanceSeriesMetadata(series: Series): Series {
    val imdbData = searchIMDb(series.title)
    return series.copy(
        description = imdbData.plot,
        year = imdbData.year,
        rating = imdbData.rating,
        cast = imdbData.cast.joinToString(", ")
    )
}
```

**Challenges with Option 2**:
- Title matching difficult (transliterations vary)
- API rate limits (IMDb: 100 requests/day free tier)
- Cost (TMDB: free but requires attribution)
- Maintenance (APIs change, data stale)
- Legal (scraping IMDb violates TOS)

**Option 3: Community Metadata (Long-term)**
- Let users submit descriptions/ratings
- Wikipedia-style collaborative editing
- Moderation required
- Takes time to build content

**RECOMMENDATION FOR PHASE 2.6**:
1. ✅ Fix season detection (data exists, just needs parsing)
2. ✅ Fix episode numbering (use COUNT(*) not totalEpisodes)
3. ⚠️ SKIP descriptions/years/ratings (not on website, complex to add)
4. ✅ Implement archive/new release logic (infer from patterns)
5. ✅ Focus on playback quality over metadata richness

### 10. Live TV Implementation - No Data

**Status**: `live_channels` table exists but is EMPTY

**Website has**: ~70 live TV channels across 8 categories

**Categories on website**:
1. Series and Movies
2. Sports
3. Persian
4. Most Popular
5. Music
6. News
7. Other Languages
8. Religious

**Why not scraped**:
- Requires separate scraper for `/livetv` page
- Channel URLs require IP-based construction
- Stream URLs may require user IP appended
- Different HTML structure than series/movies

**Impact**: "Live TV" menu item will show empty state

**To implement**:
1. Create `fetchLiveChannels()` scraper method
2. Parse `/livetv` page for all channels
3. Extract channel names, logos, categories
4. Handle IP-based stream URL construction
5. Populate `live_channels` table

**Complexity**: MEDIUM (new scraping pattern + IP handling)

---

## Summary: What Works vs What Doesn't

### ✅ What Works (Production Ready)

**Video Playback System** (100% functional):
- CDN domain replacement (iranproud2 → negahestan)
- HTTP Referer header (prevents blockHacks.mp4)
- Video URL extraction (100% success rate on 19,373 episodes)
- ExoPlayer integration tested and verified

**Database & Content**:
- 923 series + 312 movies with metadata
- 19,373 episodes with video URLs
- Fast local database queries (Room)
- Offline-first architecture
- "My Shows" feature (MonitoredSeries)
- Watch progress tracking (watchProgress field)

**UI Components**:
- Home screen with rows
- Series detail pages
- Episode selection
- Playback screen
- Basic navigation

### ⚠️ What Needs Work (Functional but Incomplete)

**Metadata Quality**:
- Episode titles all "Episode {n}" (generic)
- Seasons all defaulting to 1 (multi-season shows exist)
- Movie genres all "MOVIE" (needs sub-classification)
- totalEpisodes field 79% inflated (use COUNT(*) instead)
- Missing: descriptions, years, ratings, durations

**Content Classification**:
- No "archive" vs "new" logic
- No "new release" detection
- No display ordering
- No Turkish series filtering (have data, need UI)

**Search**:
- Can use Room FTS on Series + Movies ✅
- Website search API untested ⚠️

### ❌ What's Missing (Not Implemented)

**Content Types** (0 data):
- Shows (talk shows, variety)
- Music Videos
- Video Clips
- Cartoons
- Live TV channels

**Homepage Sections**: 2 of 12 implemented (16.7%)

**Season Support**: Detection not implemented

**Enhanced Metadata**: Deeper scraping needed

---

**Document Status**: ✅ UPDATED WITH ACTUAL DATA
**Next Action**: Review Phase 2.6 recommendations, decide which gaps to fill first
**Last Updated**: 2025-11-06
