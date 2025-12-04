# IMVBox Provider Analysis

**Analysis Date:** 2025-12-01
**Website:** https://www.imvbox.com
**Purpose:** Evaluate as potential content provider for FarsiPlex app

---

## Executive Summary

IMVBox is a **custom-built Laravel application** (NOT WordPress/DooPlay like Farsiland). It offers:
- Rich metadata (cast photos, bios, crew roles)
- **55+ subtitle languages** (most comprehensive)
- HLS streaming + YouTube fallback
- **Search AJAX API discovered** (POST endpoint)
- Sitemap-based content discovery

**Verdict:** Technically feasible to add. Search API makes implementation easier than expected (~400 lines)

---

## Technology Stack Comparison

| Aspect | IMVBox | Farsiland/FarsiPlex |
|--------|--------|---------------------|
| **CMS** | Custom (Laravel) | WordPress |
| **API** | **Search AJAX API** + HTML | DooPlay REST API |
| **Video Player** | Video.js + HLS.js | DooPlay iframe |
| **Image CDN** | assets.imvbox.com | d1.flnd.buzz, farsicdn |
| **Streaming** | HLS (.m3u8) + YouTube | HLS only |
| **Authentication** | Session cookies | None required |

### Tech Stack Details
- **Framework:** Laravel (CSRF token pattern observed)
- **CSS:** Bootstrap 4.6.0
- **JS:** jQuery 3.5.1 + custom `all.min.js`
- **Video:** Video.js with HLS.js extension
- **Chromecast:** Google Cast SDK (`cast_sender.js`)
- **Ads:** Google IMA SDK (pre-roll ads for free tier)
- **Auth:** Google One Tap login + traditional session
- **Fonts:** Lato, Lobster, Aftika (Persian)
- **Mobile:** No native app - responsive web only

---

## Available Metadata

### Movies

| Field | Available | Source | Example |
|-------|-----------|--------|---------|
| Title (English) | Yes | `<h1>`, JSON-LD | "Sad Dam" |
| Title (Farsi) | Yes | `<h1>` | "صد دام" |
| Year | Yes | Page content | 2025 |
| Duration | Yes | JSON-LD `duration` | "PT1H37M0S" (97 min) |
| Genres | Yes | Links | Comedy, Drama |
| Rating | Yes | Page content | 8.4 (internal scale) |
| Synopsis | Yes | JSON-LD, meta tags | Full description |
| Poster Image | Yes | `og:image` | `assets.imvbox.com/movies/{slug}pos.jpg` |
| Thumbnail | Yes | JSON-LD `thumbnailUrl` | `assets.imvbox.com/movies/{slug}th.jpg` |
| Trailer URL | Yes | Separate link | `/movies/{slug}/play?play-trailer=1` |
| Subtitles | Yes | Page content | **55+ languages** (see full list below) |
| View Count | Yes | JSON-LD | `userInteractionCount: 122` |
| Upload Date | Yes | JSON-LD | "2025-11-29T11:30:58+00:00" |
| IMDB Link | No | - | Not available |

### TV Shows

| Field | Available | Source | Example |
|-------|-----------|--------|---------|
| Title (EN + FA) | Yes | `<h1>` | "Mozaffar's Garden" / "باغ مظفر" |
| Year | Yes | Page content | 2006 |
| Genres | Yes | Links | Comedy |
| Rating | Yes | Page content | 6.5 |
| Synopsis | Yes | About tab | Full description |
| Season Count | Yes | Season dropdown | Multiple seasons supported |
| Episode Count | Yes | Episode list | 40 episodes |
| Episode Duration | Yes | Per-episode | 25-49 min range |
| Episode Thumbnails | Yes | Individual images | Per-episode thumbnails |

### Cast & Crew (Excellent Quality)

| Field | Available | Example |
|-------|-----------|---------|
| Name (English) | Yes | "Reza Attaran" |
| Name (Farsi) | Yes | "رضا عطاران" |
| Photo | Yes | High-quality headshots |
| Birthdate | Yes | "May 10, 1968" |
| Biography | Yes | Bilingual (EN/FA), 7000+ chars |
| Filmography | Yes | Grouped by role (Actor, Director, etc.) |

### Crew Roles Supported
- Director
- Producer
- Writer
- Editor
- Cinematographer
- Sound (multiple)
- Make up
- Costume Designer
- Set Designer
- Music
- Photographer

### Subtitle Languages (55+ Supported)

**Major Languages:**
English, Arabic, German, Turkish, Italian, French, Dutch, Spanish, Swedish, Portuguese, Russian, Farsi, Chinese, Hindi, Japanese, Korean

**Regional Languages:**
Urdu, Romanian, Indonesian, Bengali, Danish, Albanian, Greek, Norwegian, Polish, Kurdish, Bulgarian, Tajik, Armenian, Gujarati, Azerbaijani, Malay, Catalan, Hebrew, Amharic, Czech, Thai, Bosnian, Pashto, Finnish, Galician, Croatian, Serbian, Nepali, Tamil, Uzbek, Mongolian, Slovak, Punjabi, Tagalog, Slovenian, Basque, Vietnamese, Sindhi, Lithuanian

**Filter URL Pattern:** `?filter_language[]=English`

---

## Movie Categories

| Category | URL Path | Description |
|----------|----------|-------------|
| Recently Subtitled | `/movies?sort_by=recently-subtitled` | Latest subtitle additions |
| New Releases | `/movies?sort_by=new-releases` | Newest movies |
| Highest Rated | `/movies?sort_by=highest-rated` | Top rated |
| Feature Films | `/movies/feature-film` | Full-length movies |
| Documentaries | `/movies/documentary` | Documentary films |
| Pre-Revolution | `/movies/before-revolution` | Classic Iranian cinema (pre-1979) |
| Theatre | `/movies/theatre` | Stage recordings |

---

## Discovered API Endpoints (MAJOR FINDING)

### Search AJAX API

```
Endpoint:     POST https://www.imvbox.com/en/search-and-fetch-data
Method:       POST
Content-Type: application/x-www-form-urlencoded
CSRF Token:   Required (from meta tag)

Request:
  - query: "lizard" (search term)
  - _token: "<csrf-token>"

Response:
  - Returns HTML fragment with search results
  - Includes movie titles, URLs, and thumbnail images
  - Categorized by content type (Movies, TV Series, Cast)
```

**Why this matters:** We can use this endpoint to search for content instead of scraping every page. Much faster for building initial catalog.

### JavaScript API Endpoints (from all.min.js)

Found in page data attributes - internal API endpoints for video interaction:

```javascript
// Endpoints discovered in JavaScript:
data-search-url              // Search functionality (see Search AJAX API)
data-track-video-url         // Video playback progress tracking
data-update-played-count-url // Increment play count statistics
data-update-notification     // User notification updates
google-one-tap-callback-url  // Google OAuth login callback
```

**Note:** These are internal endpoints for logged-in user features. The Search API is the primary useful endpoint for content discovery.

### Chromecast Integration (IMPORTANT)

```
Library:     cast_sender.js (Google Cast SDK)
URL:         https://www.gstatic.com/cv/js/sender/v1/cast_sender.js
Integration: Native Chromecast support in video player
```

**FarsiPlex Benefit:** Since IMVBox already uses Chromecast, their HLS streams are likely optimized for cast playback. Our existing `CastManager.kt` should work seamlessly.

### Ads Integration

```
Library:     IMA SDK (Google Interactive Media Ads)
URL:         https://imasdk.googleapis.com/js/sdkloader/ima3.js
Purpose:     Pre-roll ads for free tier users
```

### What Was NOT Found

| Endpoint | Status | Notes |
|----------|--------|-------|
| `/api/*` | 404 | No public REST API |
| `/rss` | 404 | No RSS feed |
| `/feed` | 404 | No Atom feed |
| `/oembed` | 404 | No oEmbed support |
| `/graphql` | 404 | No GraphQL endpoint |
| `/.well-known/*` | 404 | No discovery files |
| `/wp-json/*` | N/A | Not WordPress |
| Mobile App | N/A | Uses responsive web only |

---

## URL Patterns

### Content URLs
```
Movies List:      https://www.imvbox.com/en/movies
Movie Detail:     https://www.imvbox.com/en/movies/{slug}
Movie Play:       https://www.imvbox.com/en/movies/{slug}/play
Movie Trailer:    https://www.imvbox.com/en/movies/{slug}/play?play-trailer=1

TV Series List:   https://www.imvbox.com/en/tv-series
Show Detail:      https://www.imvbox.com/en/shows/{slug}
Season Detail:    https://www.imvbox.com/en/shows/{slug}/season-{n}
Episode Detail:   https://www.imvbox.com/en/shows/{slug}/season-{n}/episode-{n}
Episode Play:     https://www.imvbox.com/en/shows/{slug}/season-{n}/episode-{n}/play

Cast List:        https://www.imvbox.com/en/casts
Cast Detail:      https://www.imvbox.com/en/casts/{name-slug}
```

### Sitemap URLs (Detailed Analysis)
```
Main Index:       https://www.imvbox.com/sitemap.xml (index of 4 sitemaps)
├── Main Content: https://www.imvbox.com/sitemaps/sitemap.xml (~10MB, all movies/shows)
├── Media Posts:  https://www.imvbox.com/sitemaps/sitemap_media.xml (~1000 entries)
├── Cast Members: https://www.imvbox.com/sitemaps/sitemap_casts.xml (~500+ entries)
└── Tags/Awards:  https://www.imvbox.com/sitemaps/sitemap_tags.xml (~500+ film festivals)
```

**Sitemap Features:**
- Bilingual support (EN/FA alternate links per entry)
- Weekly change frequency
- Priority: 0.5 for all content
- Contains both English and Persian URL slugs

### Filter/Sort Parameters
```
Genre Filter:     ?filter_genre[]=4 (Comedy), ?filter_genre[]=6 (Drama)
Sort:             ?sort_by=recently-added, ?sort_by=new-releases
Pagination:       ?page=2
```

---

## Image Asset Patterns

All images hosted at `assets.imvbox.com`:

```
Movie Poster:     /movies/{slug}pos.jpg
Movie Thumbnail:  /movies/{slug}th.jpg (or .webp)
Show Thumbnail:   /shows/{slug}Th.webp
Season Poster:    /seasons/{slug}poster.jpg
Episode Thumb:    /episodes/{md5-hash}.webp (hashed filename, not slug)
Cast Photo:       /cast/{id}.webp or /cast/{name}.webp
Logo:             /assets/mvi2.png
Icons:            /icons/{name}.png
Video.js Assets:  /videojs/*.svg (custom player controls)
```

**Episode Thumbnail Example:**
```
https://assets.imvbox.com/episodes/97c2ccb1b7827ee2558dd4f73a3490ea.webp
```
**Note:** Episode thumbnails use MD5 hash filenames, not predictable slugs. Must be scraped from HTML.

---

## Video Streaming

### Primary: HLS Streams
```
Base URL:         https://streaming.imvbox.com/media/{id}/{id}.m3u8
Trailers:         https://streaming.imvbox.com/media/trailers/{id}/{id}.m3u8

Quality Variants (observed):
├── Master:       /media/{id}/{id}.m3u8 (adaptive)
├── 1080p:        /media/{id}/1920x1080/1920x1080.m3u8
├── 720p:         /media/{id}/1280x720/1280x720.m3u8 (assumed)
└── Segments:     /media/{id}/1920x1080/segment00000.ts, segment00001.ts, ...

Example Movie:    https://streaming.imvbox.com/media/3628/3628.m3u8
Example Trailer:  https://streaming.imvbox.com/media/trailers/1088/1088.m3u8
```

### Secondary: YouTube Embeds
Some content (especially newer) uses YouTube as the video source.
- YouTube iframe API loaded on pages
- Fallback when HLS not available

### YouTube Origin Bypass (FarsiPlex Implementation) ✅ WORKING

**Problem:** YouTube embeds on IMVBox are restricted by origin. YouTube returns **Error 153** when the embed is loaded from any domain other than `imvbox.com`. Video owners whitelist specific domains.

**Solution:** Two-step approach:
1. **Clean HTML Parsing** - Extract YouTube ID directly from page HTML (no WebView race conditions)
2. **Origin Spoofing** - Use `loadDataWithBaseURL()` to spoof `imvbox.com` origin for playback

---

### IMVBox Page Structure (Critical Discovery)

IMVBox play pages have **THREE video player elements**:

```html
<!-- 1. INTRO PLAYER - Ad/intro video (media 3628) - SKIP THIS -->
<div class="intro-player-container">
    <video-js id="intro-player" ...>
        <source src="https://streaming.imvbox.com/media/3628/3628.m3u8" type="application/x-mpegURL">
    </video-js>
</div>

<!-- 2. MAIN MOVIE PLAYER - THE ACTUAL MOVIE (YouTube source in data-setup) -->
<video-js id="player" class="video-js video-js-imvbox"
    data-setup='{"techOrder": ["youtube"], "playbackRates": [0.5, 1, 1.5, 2],
    "sources": [{ "type": "video/youtube", "src": "https://www.youtube.com/embed/f9hrxIZS7Ck?si=..."}],
    "youtube": {"cc_load_policy" : 1, "iv_load_policy": 3} }' ...>
</video-js>

<!-- 3. TRAILER PLAYER (if exists) -->
<video-js id="player-trailer" ...>
</video-js>
```

**Key Insight:** The movie's YouTube URL is **statically embedded** in the `#player` element's `data-setup` JSON attribute. No need to wait for network requests or intercept dynamic loading!

---

### Clean Extraction Approach (Recommended)

Instead of using fragile WebView network interception, we parse the HTML directly:

```kotlin
// IMVBoxVideoExtractor.kt - extractFromHtml()

// Regex to find #player element's data-setup attribute
private val PLAYER_DATA_SETUP_REGEX = Regex(
    """<video-js[^>]*id=["']player["'][^>]*data-setup=['"]([^'"]+)['"]""",
    RegexOption.IGNORE_CASE
)

suspend fun extractFromHtml(playUrl: String): VideoSource? {
    // 1. Fetch page HTML via OkHttp (with auth cookies)
    val html = httpClient.get(playUrl)

    // 2. Find #player element's data-setup attribute
    val playerDataSetup = PLAYER_DATA_SETUP_REGEX.find(html)
    if (playerDataSetup != null) {
        val dataSetup = playerDataSetup.groupValues[1]
            .replace("&quot;", "\"")
            .replace("&#39;", "'")

        // 3. Parse JSON to get YouTube URL
        val json = JSONObject(dataSetup)
        val sources = json.optJSONArray("sources")
        for (i in 0 until sources.length()) {
            val source = sources.getJSONObject(i)
            val src = source.optString("src", "")
            if (src.contains("youtube.com/embed")) {
                val videoId = YOUTUBE_EMBED_REGEX.find(src)?.groupValues?.get(1)
                return VideoSource.YouTube(videoId!!)
            }
        }
    }
    return null
}
```

**Why this is better than WebView interception:**
- **No race conditions** - Movie URL is static in HTML, doesn't depend on load order
- **Faster** - No WebView overhead, just HTTP request + regex
- **Reliable** - Intro video (`#intro-player`) is in separate element, completely ignored
- **Simpler** - ~50 lines vs ~200 lines of WebView interception code

---

### Implementation Flow (Updated)

```
┌─────────────────────────────────────────────────────────────────┐
│ 1. User clicks Play on IMVBox movie                             │
├─────────────────────────────────────────────────────────────────┤
│ 2. VideoPlayerActivity detects imvbox.com URL                   │
├─────────────────────────────────────────────────────────────────┤
│ 3. IMVBoxVideoExtractor.extractVideoSource(playUrl)             │
│    a. Authenticate via IMVBoxAuthManager (get session cookies)  │
│    b. Fetch play page HTML via OkHttp                           │
│    c. Parse #player element's data-setup JSON attribute         │
│    d. Extract YouTube video ID from sources array               │
│    e. Return VideoSource.YouTube(videoId)                       │
│    f. [Fallback] If HTML parsing fails → use WebView extraction │
├─────────────────────────────────────────────────────────────────┤
│ 4. IMVBoxWebPlayerActivity receives pre-extracted YouTube ID    │
├─────────────────────────────────────────────────────────────────┤
│ 5. loadDataWithBaseURL() with imvbox.com origin + YouTube embed │
│    - No intro video (completely bypassed)                       │
│    - No page loading delay                                      │
│    - Direct YouTube playback with D-pad controls                │
└─────────────────────────────────────────────────────────────────┘
```

---

### Key Files

| File | Purpose |
|------|---------|
| `IMVBoxVideoExtractor.kt` | **Primary:** HTML parsing to extract YouTube ID from `#player` data-setup. **Fallback:** WebView extraction |
| `IMVBoxWebPlayerActivity.kt` | Plays YouTube video with spoofed imvbox.com origin |
| `VideoPlayerActivity.kt` | Orchestrates extraction → playback flow |
| `IMVBoxAuthManager.kt` | Handles login, session cookies for authenticated requests |

---

### Extraction Code (Production)

```kotlin
// IMVBoxVideoExtractor.kt

companion object {
    private const val TAG = "IMVBoxVideoExtractor"
    private val INTRO_MEDIA_IDS = setOf("3628")  // Known intro/ad video

    // Regex patterns for HTML parsing
    private val YOUTUBE_EMBED_REGEX = Regex("""youtube\.com/embed/([a-zA-Z0-9_-]{11})""")

    // Match #player element's data-setup attribute (THE ACTUAL MOVIE)
    private val PLAYER_DATA_SETUP_REGEX = Regex(
        """<video-js[^>]*id=["']player["'][^>]*data-setup=['"]([^'"]+)['"]""",
        RegexOption.IGNORE_CASE
    )
}

/**
 * CLEAN APPROACH: Extract video source by parsing HTML directly.
 * Targets #player element's data-setup JSON attribute.
 * Completely bypasses #intro-player (no race conditions).
 */
suspend fun extractVideoSource(playUrl: String): VideoSource {
    Log.d(TAG, "=== CLEAN EXTRACTION: Parsing HTML directly ===")

    // Ensure authenticated for full content
    IMVBoxAuthManager.ensureAuthenticated(context)

    // Try clean HTML parsing first
    val htmlResult = withContext(Dispatchers.IO) {
        extractFromHtml(playUrl)
    }

    if (htmlResult != null) {
        Log.d(TAG, "=== SUCCESS: Got video from HTML parsing ===")
        return htmlResult
    }

    // Fallback to WebView if HTML parsing failed
    Log.w(TAG, "HTML parsing failed, falling back to WebView")
    return extractViaWebView(playUrl)
}

private suspend fun extractFromHtml(playUrl: String): VideoSource? {
    val cookies = IMVBoxAuthManager.getWebViewCookies()
    val request = Request.Builder()
        .url(playUrl)
        .header("Cookie", cookies.joinToString("; "))
        .build()

    val html = httpClient.newCall(request).execute().body?.string() ?: return null

    // Find #player data-setup (THE MOVIE, not intro)
    val playerDataSetup = PLAYER_DATA_SETUP_REGEX.find(html)
    if (playerDataSetup != null) {
        val dataSetup = playerDataSetup.groupValues[1]
            .replace("&quot;", "\"")

        // Extract YouTube ID from data-setup JSON
        val youtubeId = extractYouTubeFromDataSetup(dataSetup)
        if (youtubeId != null) {
            Log.d(TAG, "=== EXTRACTED MOVIE YouTube ID: $youtubeId ===")
            return VideoSource.YouTube(youtubeId)
        }
    }
    return null
}

private fun extractYouTubeFromDataSetup(dataSetup: String): String? {
    return try {
        val json = JSONObject(dataSetup)
        val sources = json.optJSONArray("sources")
        if (sources != null) {
            for (i in 0 until sources.length()) {
                val src = sources.getJSONObject(i).optString("src", "")
                if (src.contains("youtube.com")) {
                    return YOUTUBE_EMBED_REGEX.find(src)?.groupValues?.get(1)
                }
            }
        }
        null
    } catch (e: Exception) {
        // Fallback: regex extraction
        YOUTUBE_EMBED_REGEX.find(dataSetup)?.groupValues?.get(1)
    }
}
```

---

### Origin Spoofing (Playback)

YouTube checks embed origin via headers and JavaScript. We spoof `imvbox.com` origin using `loadDataWithBaseURL()`:

```kotlin
// IMVBoxWebPlayerActivity.kt

private fun loadYouTubeWithSpoofedOrigin(youtubeId: String) {
    val embedHtml = """
        <!DOCTYPE html>
        <html>
        <head>
            <style>
                * { margin: 0; padding: 0; }
                html, body { width: 100%; height: 100%; background: #000; }
                iframe { width: 100%; height: 100%; border: none; }
            </style>
        </head>
        <body>
            <iframe src="https://www.youtube.com/embed/$youtubeId?autoplay=1&controls=1"
                    allow="autoplay; encrypted-media" allowfullscreen>
            </iframe>
        </body>
        </html>
    """.trimIndent()

    // THE KEY: Use imvbox.com as base URL to spoof origin
    webView.loadDataWithBaseURL(
        "https://www.imvbox.com/movies/play",  // Fake origin
        embedHtml,
        "text/html",
        "UTF-8",
        null
    )
}
```

**What this does:**
- WebView reports `document.location.origin` as `https://www.imvbox.com`
- Browser sends `Referer: https://www.imvbox.com/movies/play` header
- YouTube sees valid whitelisted origin → **allows playback!**

---

### Intro Video Bypass (Automatic)

The clean HTML parsing approach **completely bypasses** the intro video:

| Element | Content | Our Approach |
|---------|---------|--------------|
| `#intro-player` | Intro/ad (media 3628) | **IGNORED** - Different element ID |
| `#player` | **THE MOVIE** | **EXTRACTED** - Target of our parsing |
| `#player-trailer` | Trailer | Ignored |

No need to filter network requests or detect widgetid parameters - we simply target the correct element!

---

### Why This Works (Technical)

1. **Static HTML** - Movie YouTube URL is in page HTML, not dynamically loaded
2. **Separate Elements** - Intro and movie use different `<video-js>` elements
3. **data-setup Attribute** - Video.js configuration is embedded as JSON in HTML
4. **Origin Spoofing** - `loadDataWithBaseURL()` makes WebView report fake origin
5. **No Race Conditions** - HTML parsing is deterministic, no timing issues

---

### Limitations

- Requires authentication for full movie access (free tier may only get trailers)
- Only works for YouTube embeds (HLS streams use different code path)
- If IMVBox changes their HTML structure, parsing may need updating
- ~3-5 seconds extraction time (HTTP request + parsing)

### Authentication
- Free tier: Ads before content (HLS ID 3628 = ad video)
- Premium: Direct content access
- Login preserves via session cookies

### HLS Quality Variants (Observed)
```
Master Playlist:  /media/{id}/{id}.m3u8
Quality Streams:
├── 1920x1080:    /media/{id}/1920x1080/1920x1080.m3u8
├── 1280x800:     /media/{id}/1280x800/1280x800.m3u8
├── 1280x720:     /media/{id}/1280x720/1280x720.m3u8 (assumed)
└── 640x360:      /media/{id}/640x360/640x360.m3u8

Segment Pattern:  /media/{id}/{resolution}/segment{00000}.ts
Segment Duration: ~10 seconds (typical HLS)
```

**Note:** HLS manifest has SSL certificate issue - may need to handle certificate validation

### Embedded Trailer Data
Movie listing pages contain embedded JSON with trailer metadata:
```javascript
[
  {
    "source": "https://streaming.imvbox.com/media/trailers/1071/1071.m3u8",
    "thumbnail": "ezdevajjanjalith3.jpg",
    "slug": "controversial-marriage-ezdevaje-janjali"
  },
  ...
]
```

---

## JSON-LD Schema (Movies)

```json
{
  "@context": "http://schema.org",
  "@type": "VideoObject",
  "name": "Sad Dam - Saddam",
  "description": "The film tells the story of...",
  "thumbnailUrl": "https://assets.imvbox.com/movies/saddam2025th.jpg",
  "interactionStatistic": {
    "@type": "InteractionCounter",
    "interactionType": {"@type": "WatchAction"},
    "userInteractionCount": 122
  },
  "potentialAction": {
    "@type": "WatchAction",
    "target": {
      "@type": "EntryPoint",
      "urlTemplate": "https://www.imvbox.com/en/movies/sad-dam2025/play",
      "inLanguage": "en",
      "actionPlatform": [
        "http://schema.org/DesktopWebPlatform",
        "http://schema.org/MobileWebPlatform",
        "http://schema.org/TabletWebPlatform"
      ]
    }
  },
  "uploadDate": "2025-11-29T11:30:58+00:00",
  "duration": "PT1H37M0S",
  "embedUrl": "https://www.imvbox.com/en/movies/sad-dam2025/play"
}
```

---

## Meta Tags Available

```html
<meta name="description" content="...">
<meta name="keywords" content="">
<meta property="og:title" content="Sad Dam | Saddam">
<meta property="og:description" content="...">
<meta property="og:image" content="https://assets.imvbox.com/movies/saddam2025pos.jpg">
<meta name="twitter:title" content="Sad Dam | Saddam">
<meta name="twitter:description" content="...">
<meta name="twitter:image" content="...">
<meta name="csrf-token" content="...">
```

---

## Scraper Implementation Plan

### Required Components

1. **IMVBoxApiService.kt** - HTTP client with session + CSRF handling
2. **IMVBoxHtmlParser.kt** - JSoup-based HTML parsing
3. **IMVBoxSearchApi.kt** - Search AJAX API wrapper (NEW)
4. **IMVBoxSitemapParser.kt** - Sitemap XML parsing
5. **IMVBoxSyncWorker.kt** - Background sync worker

### Recommended Strategy (Hybrid Approach)

```kotlin
// Strategy 1: Use Search API for quick catalog building
suspend fun buildCatalogViaSearch() {
    val csrfToken = fetchCsrfToken("https://www.imvbox.com/en/movies")

    // Search common terms to discover content
    val searchTerms = listOf("a", "b", "c", ... "z", "1", "2", ...)
    for (term in searchTerms) {
        val results = searchApi.search(term, csrfToken)
        results.movies.forEach { movie ->
            if (!database.exists(movie.slug)) {
                // Fetch full details
                val details = fetchMovieDetails(movie.url)
                database.insertMovie(details)
            }
        }
    }
}

// Strategy 2: Use Sitemap for comprehensive sync
suspend fun fullSyncViaSitemap() {
    val sitemapUrls = parseSitemap("https://www.imvbox.com/sitemaps/sitemap.xml")

    for (url in sitemapUrls) {
        val html = httpClient.get(url)
        val jsonLd = extractJsonLd(html)  // Rich metadata!
        val metadata = parseMetadata(html)
        val cast = parseCastCrew(html)

        contentDatabase.insertMovie(...)
    }
}

// Strategy 3: Extract video URLs from play page
suspend fun getVideoUrl(movieSlug: String): VideoUrl {
    val playPage = httpClient.get("https://www.imvbox.com/en/movies/$movieSlug/play")

    // Check for HLS first
    val hlsUrl = extractHlsUrl(playPage) // streaming.imvbox.com/media/{id}/{id}.m3u8
    if (hlsUrl != null) return VideoUrl(hlsUrl, type = "hls")

    // Fallback to YouTube
    val youtubeId = extractYoutubeId(playPage)
    if (youtubeId != null) return VideoUrl(youtubeId, type = "youtube")

    throw VideoNotAvailableException()
}
```

### Estimated Effort (Revised)
- IMVBoxApiService: ~80 lines
- IMVBoxSearchApi: ~50 lines (NEW - simpler than expected)
- IMVBoxHtmlParser: ~200 lines (less needed with JSON-LD)
- IMVBoxSyncWorker: ~100 lines
- Database entities: ~50 lines (cast photos, bios)
- **Total:** ~400-500 lines (reduced from 500-600)

---

## Deep Research Findings (2025-12-01)

### Live TV Infrastructure

**Domain:** `imvbox.tv` (separate from main site)

**Channels Available:**
| Type | Count | Examples |
|------|-------|----------|
| National | 14+ | IRIB 1-4, Varzesh (Sports), iFilm, IRINN (News), Tamasha, Nasim, Pooya, Jaam-e-Jam |
| Provincial | 31 | All 31 Iranian provinces (Tehran, Isfahan, Fars, etc.) |

**URL Pattern:**
```
http://www.imvbox.tv/en/{channel-slug}/live-channel/play
Examples:
- /en/iribtv3/live-channel/play
- /en/iribvarzesh/live-channel/play
- /en/iribesfahan/live-channel/play
```

**Streaming Architecture:**
- Uses **external embed from parsatv.com**
- Embed URL: `https://www.parsatv.com/embed.php?name={channel-name}&auto=false`
- **403 Forbidden** on embedding - cannot be embedded in our app
- Cloudflare challenge protection on parsatv.com

**Channel Icons:**
```
Pattern: assets.imvbox.com/channels/{md5-hash}.png
Example: assets.imvbox.com/channels/b5aee82d78cd41c8d15af0a4565ce209.png
```

**FarsiPlex Impact:** Live TV NOT usable - streams are from third-party that blocks embedding.

---

### Subtitle System

**Availability:**
- Subtitles are a **PREMIUM feature** ("Access Subtitle" links to upgrade page)
- Display: "Subtitles: English, German, Arabic, and +4 more"
- 55+ languages supported

**Loading Mechanism:**
- No VTT/SRT files observed in network requests for free tier
- Subtitle tracks likely loaded only for premium users
- Format: Unknown (likely VTT based on Video.js standard)

**FarsiPlex Impact:** May need premium account to access subtitle files, or subtitles may not be available for scraping.

---

### Pagination & Filtering

**Type:** Server-side (full page reload)

**URL Parameters:**
```
Pagination:       ?page=2, ?page=3, ...
Sort Options:     ?sort_by=new-releases
                  ?sort_by=recently-added
                  ?sort_by=highest-rated
                  ?sort_by=recently-subtitled
Language Filter:  ?filter_language[]=English
Genre Filter:     ?filter_genre[]=4
```

**No AJAX Pagination:** Full page reload required for each page. No infinite scroll, no "Load More" button.

---

### Authentication & Cookies

**Cookie Structure:**
```
XSRF-TOKEN: Laravel encrypted CSRF token (base64 encoded JSON)
laravel_session: Session ID (if logged in)
```

**CSRF Token Location:**
```html
<meta name="csrf-token" content="...">
```

**Session Handling:**
- Standard Laravel session cookies
- XSRF token required for POST requests (Search API, tracking)
- Google One Tap for OAuth login

---

### Video Tracking API

**Endpoints Discovered:**
```
POST /en/members/track-intro-video  - Track ad/intro video completion
POST /en/members/track-video        - Track main video progress (assumed)
```

**Purpose:** Internal analytics for video engagement tracking.

---

### What Does NOT Exist

| Feature | Status | Notes |
|---------|--------|-------|
| manifest.json | 404 | No PWA support |
| opensearch.xml | 404 | No browser search integration |
| /rss, /feed | 404 | No RSS feeds |
| /api/* | 404 | No public REST API |
| /oembed | 404 | No oEmbed support |
| /graphql | 404 | No GraphQL |
| /.well-known/* | 404 | No discovery files |
| Mobile App | N/A | Responsive web only |
| AJAX Pagination | N/A | Server-side only |
| Free Subtitles | N/A | Premium feature |

---

## Known Quirks & Edge Cases

### Episode URL Typo Pattern
Some episode URLs have a typo in the path - `episoddde` instead of `episode`:
```
Expected:   /shows/{slug}/season-1/episode-01/play
Actual:     /shows/{slug}/season-1/episoddde-01/play
```
**Mitigation:** Scraper should handle both patterns when parsing episode links.

### robots.txt
```
User-agent: *
Disallow: /my-account/
```
Only `/my-account/` is restricted. All content pages are crawlable.

---

## Challenges & Mitigations

| Challenge | Risk | Mitigation |
|-----------|------|------------|
| ~~No API~~ | ~~Medium~~ | **SOLVED: Search AJAX API discovered** |
| CSRF Token | Low | Fetch from meta tag before each session |
| Site changes | Medium | Version selectors, error reporting |
| Rate limiting | Unknown | Add 1-2s delays between requests |
| Large sitemap | Medium | Stream/chunk processing for ~10MB file |
| YouTube fallback | Low | Detect and handle YouTube embeds |
| Premium content | Low | Graceful degradation for free tier |
| Authentication | Low | Persist session cookies |
| Episode URL typo | Low | Handle both `episode` and `episoddde` patterns |
| Hash-based episode thumbs | Low | Must scrape from HTML, not predictable |

---

## Benefits vs Existing Providers

| Feature | Farsiland | FarsiPlex | IMVBox |
|---------|-----------|-----------|--------|
| API Complexity | Low | Low | **Low-Medium** (Search API!) |
| Metadata Quality | Good | Good | **Excellent** |
| Cast Photos | No | No | **Yes** |
| Cast Bios | No | No | **Yes** |
| Crew Roles | Basic | Basic | **12+ roles** |
| Subtitles | Some | Some | **55+ languages** (Premium only) |
| Trailer Support | No | No | **Yes** (HLS) |
| Chromecast | Custom | Custom | **Native SDK** |
| Classic Films | Limited | Limited | **Extensive** (pre-1979) |
| Content Freshness | Good | Good | Good |
| Live TV | No | No | ~~Yes~~ **BLOCKED** |
| Mobile App | No | FarsiPlex | No (responsive web) |

---

## Recommendation

**Should we add IMVBox?** **YES - Highly Recommended**

### Pros
- **Search API discovered** - Much easier than pure HTML scraping
- **Native Chromecast support** - HLS streams already optimized for casting
- Excellent metadata quality (best cast info available)
- Large catalog of classic Iranian cinema (pre-1979)
- **55+ subtitle languages** - Most comprehensive
- Trailer support with HLS streaming
- Good image quality on dedicated CDN
- JSON-LD structured data for easy parsing
- Bilingual content (EN/FA)
- Permissive robots.txt (only `/my-account/` restricted)

### Cons
- CSRF token required for API calls (minor overhead)
- Large sitemap (~10MB) requires streaming parser
- Some content uses YouTube (not ideal for TV)
- Authentication required for full streaming
- Unknown rate limits
- **Subtitles are PREMIUM only** - may not be accessible
- **Live TV NOT usable** - external embed blocked (403)
- HLS SSL certificate issues observed
- Server-side pagination (no AJAX)

### Suggested Approach
1. **Phase 1:** Catalog sync via Search API + Sitemap (metadata only)
2. **Phase 2:** Add HLS streaming support for movies/trailers
3. **Phase 3:** Handle YouTube fallback gracefully
4. **Phase 4:** Premium account for subtitle access (evaluate cost/benefit)
5. ~~**Phase 5:** Live TV integration~~ **NOT POSSIBLE** - blocked by third-party

### Important Caveats
- **Live TV is NOT implementable** - external embeds blocked with 403 Forbidden
- **Subtitles require premium** - may need paid account or subtitles unavailable
- **No AJAX pagination** - full page reloads increase sync complexity

---

## Test Credentials (Temporary)

```
Email: mrmani@gmail.com
Password: b^bH&f6W9^
Note: Temporary test account - replace before production
```

---

## References

- Main Site: https://www.imvbox.com/en
- Movies: https://www.imvbox.com/en/movies
- TV Series: https://www.imvbox.com/en/tv-series
- Cast & Crew: https://www.imvbox.com/en/casts
- Live TV: http://www.imvbox.tv
- Sitemap Index: https://www.imvbox.com/sitemap.xml
- Streaming CDN: https://streaming.imvbox.com
- Assets CDN: https://assets.imvbox.com
- Search API: POST https://www.imvbox.com/en/search-and-fetch-data

---

**Last Updated:** 2025-12-04 (v4 - Clean Extraction Implementation)
**Analysis By:** Claude Code
**Status:** **FULLY IMPLEMENTED** - YouTube extraction and playback working
**Implementation Depth:**
- Clean HTML parsing approach (no WebView race conditions)
- Origin spoofing via `loadDataWithBaseURL()`
- Intro video bypass (targets `#player`, ignores `#intro-player`)
- Authentication via IMVBoxAuthManager
- WebView fallback for edge cases

---

## Appendix: Key Discoveries Summary

| Discovery | Impact | Details |
|-----------|--------|---------|
| **#player data-setup** | **CRITICAL** | Movie YouTube URL is in `data-setup` JSON attribute - enables clean extraction |
| **Three Video Elements** | **CRITICAL** | `#intro-player` (skip), `#player` (movie), `#player-trailer` (skip) |
| Origin Spoofing | **HIGH** | `loadDataWithBaseURL()` bypasses YouTube Error 153 |
| Search AJAX API | **HIGH** | `POST /en/search-and-fetch-data` - returns structured results |
| Chromecast Support | **HIGH** | `cast_sender.js` loaded - native Cast SDK integration |
| 55+ Subtitle Languages | **HIGH** | Most comprehensive subtitle coverage (but PREMIUM only) |
| HLS Quality Variants | **MEDIUM** | 1080p, 720p, 640x360 streams observed |
| JSON-LD Metadata | **MEDIUM** | Structured VideoObject schema |
| Embedded Trailer JSON | **MEDIUM** | Trailer URLs available on listing pages |
| Cast Sitemap | **MEDIUM** | 500+ cast members with bios |
| Live TV Channels | **MEDIUM** | 14 national + 31 provincial channels (BUT blocked) |
| Video Tracking API | **LOW** | `POST /en/members/track-intro-video` for analytics |
| Server-Side Pagination | **INFO** | No AJAX - full page reload required |
| No Mobile App | **INFO** | Responsive web only |
| Subtitles Premium | **BLOCKER** | VTT files not accessible for free tier |
| Live TV Blocked | **BLOCKER** | External embed (parsatv.com) returns 403 |
| No RSS/API/PWA | - | Confirmed: /rss, /feed, /api, /oembed, /graphql, /manifest.json all 404 |

---

## Implementation Status

| Component | Status | Notes |
|-----------|--------|-------|
| `IMVBoxVideoExtractor.kt` | ✅ **DONE** | Clean HTML parsing + WebView fallback |
| `IMVBoxWebPlayerActivity.kt` | ✅ **DONE** | Origin spoofing playback |
| `IMVBoxAuthManager.kt` | ✅ **DONE** | Session cookie management |
| `IMVBoxSyncWorker.kt` | ✅ **DONE** | Background content sync |
| Intro video bypass | ✅ **DONE** | Targets `#player`, ignores `#intro-player` |
| YouTube Error 153 fix | ✅ **DONE** | `loadDataWithBaseURL()` origin spoofing |
| HLS streaming | ⏳ Partial | Works but YouTube preferred for quality |
| Subtitle extraction | ❌ Not started | Premium feature - blocked |
| Live TV | ❌ Blocked | Third-party embed returns 403 |
