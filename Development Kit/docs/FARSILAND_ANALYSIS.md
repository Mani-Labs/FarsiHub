# Farsiland.com Complete Technical Analysis
**For Standalone Android TV App - NO Backend Server**

---

## Executive Summary

Farsiland.com is a WordPress-based Persian streaming site with **4,000+ movies and TV shows**. After comprehensive analysis, building a **100% standalone Android TV app** is **HIGHLY FEASIBLE**.

**Key Discovery**:
- ✅ Video URLs are publicly accessible (HTML microdata)
- ✅ WordPress REST API provides all metadata (no authentication)
- ✅ No backend server needed
- ✅ Direct CDN playback (d1.flnd.buzz)

---

## How Farsiland.com Works

### Content Structure

**WordPress Post Types**:
```
/movies/{slug}/          - Movie detail pages
/tvshows/{slug}/         - TV series detail pages
/episodes/{slug}/        - Individual episode pages
```

**Discovery Pages**:
- https://farsiland.com/iran-movie-2025/ (New Movies)
- https://farsiland.com/old-iranian-movies/ (Classic Movies)
- https://farsiland.com/series-26/ (New TV Shows)
- https://farsiland.com/iranian-series/ (Classic Shows)
- https://farsiland.com/episodes-15/ (Latest Episodes)

### Parent-Child Relationships

```
TV Series (Post Type: tvshows)
├── Season 1
│   ├── Episode 1 (Post Type: episodes)
│   ├── Episode 2
│   └── Episode 3
└── Season 2
    ├── Episode 1
    └── Episode 2
```

**WordPress Structure**:
- Series pages contain episode lists in HTML
- Episodes link back to parent series
- Season/episode numbers in title or URL

---

## WordPress REST API (Public Access)

### 1. Get Movies

```http
GET https://farsiland.com/wp-json/wp/v2/movies?per_page=20&page=1
```

**Response Structure**:
```json
{
  "id": 294091,
  "date": "2025-10-27T15:30:00",
  "title": {
    "rendered": "Pir Pesar"
  },
  "link": "https://farsiland.com/movies/the-old-bachelor/",
  "featured_media": 294074,
  "genres": [32, 30],
  "dtcast": [5821, 1234],
  "dtdirector": [5432],
  "dtyear": [2025],
  "content": {
    "rendered": "<p>Movie description...</p>"
  }
}
```

**Available Fields**:
- `id` - WordPress post ID
- `title.rendered` - Movie title (HTML decoded)
- `link` - URL to movie page
- `featured_media` - Poster image ID (resolve separately)
- `genres` - Genre IDs (resolve separately)
- `dtcast` - Cast member IDs
- `dtdirector` - Director IDs
- `dtyear` - Year IDs
- `content.rendered` - Description (HTML)
- `date` - Publication date

### 2. Get TV Shows

```http
GET https://farsiland.com/wp-json/wp/v2/tvshows?per_page=20&page=1
```

**Same structure as movies**

### 3. Get Episodes

```http
GET https://farsiland.com/wp-json/wp/v2/episodes?per_page=20&page=1
```

**Limitation**: Episodes endpoint does NOT include parent series ID!

**Workaround**: HTML scrape series page to get episode list

### 4. Resolve Poster Images

```http
GET https://farsiland.com/wp-json/wp/v2/media/{featured_media_id}
```

**Response**:
```json
{
  "id": 294074,
  "source_url": "https://farsiland.com/wp-content/uploads/2025/10/pir-pesar.jpg",
  "media_details": {
    "sizes": {
      "dt_poster_a": {
        "source_url": "https://farsiland.com/.../pir-pesar-185x274.jpg",
        "width": 185,
        "height": 274
      },
      "large": {
        "source_url": "https://farsiland.com/.../pir-pesar-708x1024.jpg",
        "width": 708,
        "height": 1024
      }
    }
  }
}
```

**Use `dt_poster_a` for TV grid**, `large` for details screen

### 5. Get Genres

```http
GET https://farsiland.com/wp-json/wp/v2/genres?per_page=100
```

**Response**:
```json
[
  {"id": 32, "name": "Drama", "slug": "drama"},
  {"id": 30, "name": "Thriller", "slug": "thriller"},
  {"id": 21, "name": "Comedy", "slug": "comedy"}
]
```

**Cache genres locally** - they rarely change

### 6. Search

```http
GET https://farsiland.com/wp-json/wp/v2/search?search={query}&type=post&subtype=movies,tvshows
```

**Or use WordPress search**:
```http
GET https://farsiland.com/?s={query}&post_type=movies
```

---

## Video URL Extraction

### Method 1: HTML Microdata (Recommended)

**Episode pages contain video URLs in microdata**:

```html
<link itemprop="contentUrl" href="https://d1.flnd.buzz/series/bamdade-khomar/01.1080.mp4">
<link itemprop="contentUrl" href="https://d1.flnd.buzz/series/bamdade-khomar/01.720.mp4">
<link itemprop="contentUrl" href="https://d1.flnd.buzz/series/bamdade-khomar/01.480.mp4">
```

**Extraction Code (Jsoup)**:
```kotlin
val doc = Jsoup.parse(html)
val videoUrls = doc.select("link[itemprop=contentUrl]").map { element ->
    val href = element.attr("href")
    val quality = when {
        ".1080." in href -> "1080p"
        ".720." in href -> "720p"
        ".480." in href -> "480p"
        else -> "unknown"
    }
    VideoUrl(url = href, quality = quality)
}
```

**✅ No authentication required!**

### Method 2: Pattern Generation (Fallback)

If scraping fails, generate URLs using known pattern:

```
https://{mirror}/series/{series_slug}/{episode:02d}.{quality}.mp4
```

**Examples**:
```
https://d1.flnd.buzz/series/shoghal/01.1080.mp4  (S1E1, 1080p)
https://d1.flnd.buzz/series/shoghal/01.720.mp4   (S1E1, 720p)
https://d1.flnd.buzz/series/shoghal/02.1080.mp4  (S1E2, 1080p)
```

**Pattern Generation Code**:
```kotlin
fun generateVideoUrls(seriesSlug: String, season: Int, episode: Int): List<VideoUrl> {
    val episodeNum = String.format("%02d", episode)
    val mirrors = listOf("d1.flnd.buzz", "d2.flnd.buzz")
    val qualities = listOf("1080", "720", "480")

    return mirrors.flatMap { mirror ->
        qualities.map { quality ->
            VideoUrl(
                url = "https://$mirror/series/$seriesSlug/$episodeNum.$quality.mp4",
                quality = "${quality}p"
            )
        }
    }
}
```

**Extract series slug from URL**:
```
https://farsiland.com/tvshows/shoghal/ → "shoghal"
https://farsiland.com/episodes/shoghal-s1e5/ → "shoghal"
```

### CDN Mirrors

- **Primary**: `d1.flnd.buzz`
- **Backup**: `d2.flnd.buzz`

**Always try primary first, fallback to backup on failure**

### URL Verification

```kotlin
suspend fun verifyUrl(url: String): Boolean {
    val request = Request.Builder().url(url).head().build()
    val response = httpClient.newCall(request).execute()
    return response.isSuccessful
}
```

---

## Series/Episode Structure

### Getting Episodes for a Series

**WordPress REST API limitation**: No direct way to get episodes per series

**Solution: HTML Scraping**

1. Fetch series page HTML
2. Parse episode list from `<li class="mark-{season}-{episode}">`
3. Extract season/episode from `<div class="numerando">1 - 5</div>` (Season 1, Episode 5)

**HTML Structure**:
```html
<ul class="se-c">
  <li class="mark-1-1">
    <div class="numerando">1 - 1</div>
    <a href="/episodes/shoghal-s1e1/">Episode 1</a>
  </li>
  <li class="mark-1-2">
    <div class="numerando">1 - 2</div>
    <a href="/episodes/shoghal-s1e2/">Episode 2</a>
  </li>
</ul>
```

**Parsing Code** (from FarsiFlow reference):
```kotlin
val episodeItems = soup.select("li[class*=mark-]")

episodeItems.forEach { item ->
    val link = item.selectFirst("a[href*=/episodes/]")
    val episodeUrl = link?.attr("href")

    val numerando = item.selectFirst(".numerando")
    val numText = numerando?.text() // "1 - 5"

    if (numText?.contains(" - ") == true) {
        val parts = numText.split(" - ")
        val season = parts[0].toInt()
        val episode = parts[1].toInt()

        episodes.add(Episode(season, episode, episodeUrl))
    }
}
```

**Rate Limiting**: Add 500ms delay between episode page requests (per FarsiFlow best practice)

---

## Complete Metadata Available

### Movies
- Title (Persian + English if available)
- Poster URL (multiple sizes)
- Description
- Genres
- Year
- Cast (as IDs, need to resolve)
- Director (as IDs, need to resolve)
- Video URLs (1080p, 720p, 480p)

### TV Shows
- Same as movies, plus:
- Season/episode structure
- Episode titles
- Episode URLs
- Total episodes (calculated)

### Episodes
- Season number
- Episode number
- Title
- Series name (from parent page)
- Video URLs (all qualities)
- Thumbnail (usually series poster)

### NOT Available
- IMDb ratings
- Runtime
- Episode air dates (inconsistent)
- Subtitles
- Content ratings
- Detailed cast/crew information

---

## Android TV App Architecture

```
┌──────────────────────────────────────────┐
│    Android TV App (100% Standalone)      │
│                                          │
│  ┌────────────────────────────────────┐ │
│  │  Leanback UI (Browse/Details)      │ │
│  └────────────┬───────────────────────┘ │
│               ↓                          │
│  ┌────────────────────────────────────┐ │
│  │  ViewModels (MVVM)                 │ │
│  └────────────┬───────────────────────┘ │
│               ↓                          │
│  ┌────────────────────────────────────┐ │
│  │  Repository Layer                  │ │
│  └─────┬──────────────────────┬───────┘ │
│        ↓                      ↓          │
│  ┌──────────┐          ┌──────────────┐ │
│  │WordPress │          │HTML Scraper  │ │
│  │REST API  │          │(Jsoup)       │ │
│  └──────────┘          └──────────────┘ │
│        ↓                      ↓          │
│  ┌────────────────────────────────────┐ │
│  │  Room Database (Local Cache)       │ │
│  └────────────────────────────────────┘ │
│               ↓                          │
│  ┌────────────────────────────────────┐ │
│  │  ExoPlayer (Video Playback)        │ │
│  └────────────────────────────────────┘ │
└──────────────────────────────────────────┘
             ↓
    ┌────────────────┐
    │ Internet       │
    └───┬────────┬───┘
        ↓        ↓
  Farsiland.com  CDN
  (WordPress)    (d1.flnd.buzz)
```

**Key Points**:
- ❌ NO backend server
- ❌ NO FarsiFlow dependency
- ✅ Just app + internet

---

## Implementation Requirements

### Dependencies

```gradle
// HTTP & HTML
implementation "com.squareup.okhttp3:okhttp:4.12.0"
implementation "org.jsoup:jsoup:1.17.1"

// WordPress API
implementation "com.squareup.retrofit2:retrofit:2.9.0"
implementation "com.squareup.retrofit2:converter-moshi:2.9.0"

// Video Player
implementation "androidx.media3:media3-exoplayer:1.2.0"
implementation "androidx.media3:media3-ui:1.2.0"

// Android TV
implementation "androidx.leanback:leanback:1.2.0-alpha02"

// Database
implementation "androidx.room:room-runtime:2.6.1"
implementation "androidx.room:room-ktx:2.6.1"

// Image Loading
implementation "io.coil-kt:coil:2.5.0"

// Async
implementation "org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3"
```

### Core Components

**1. HTTP Client (OkHttp)**
```kotlin
val client = OkHttpClient.Builder()
    .connectTimeout(30, TimeUnit.SECONDS)
    .readTimeout(60, TimeUnit.SECONDS)
    .addInterceptor { chain ->
        val request = chain.request().newBuilder()
            .header("User-Agent", "Mozilla/5.0...")
            .build()
        chain.proceed(request)
    }
    .build()
```

**2. WordPress API (Retrofit)**
```kotlin
interface WordPressApi {
    @GET("/wp-json/wp/v2/movies")
    suspend fun getMovies(@Query("per_page") perPage: Int): List<WPMovie>

    @GET("/wp-json/wp/v2/tvshows")
    suspend fun getTvShows(@Query("per_page") perPage: Int): List<WPTvShow>

    @GET("/wp-json/wp/v2/media/{id}")
    suspend fun getMedia(@Path("id") id: Int): WPMedia
}
```

**3. HTML Scraper (Jsoup)**
```kotlin
object VideoScraper {
    suspend fun extractVideoUrls(pageUrl: String): List<VideoUrl> {
        val html = httpClient.get(pageUrl)
        val doc = Jsoup.parse(html)

        return doc.select("link[itemprop=contentUrl]").mapNotNull { element ->
            val href = element.attr("href")
            if (href.contains(".mp4")) {
                val quality = when {
                    ".1080." in href -> "1080p"
                    ".720." in href -> "720p"
                    ".480." in href -> "480p"
                    else -> "unknown"
                }
                VideoUrl(url = href, quality = quality)
            } else null
        }
    }
}
```

**4. Local Cache (Room)**
```kotlin
@Entity
data class CachedMovie(
    @PrimaryKey val id: Int,
    val title: String,
    val posterUrl: String?,
    val link: String,
    val cachedAt: Long
)

@Entity
data class CachedGenre(
    @PrimaryKey val id: Int,
    val name: String,
    val slug: String
)
```

---

## Data Flow Examples

### Browse Movies

```
1. App launches
   ↓
2. Fetch genres (REST API)
   GET /wp-json/wp/v2/genres
   ↓
3. Cache genres in Room
   ↓
4. Fetch movies (REST API)
   GET /wp-json/wp/v2/movies?per_page=20
   ↓
5. For each movie, resolve poster
   GET /wp-json/wp/v2/media/{featured_media}
   ↓
6. Cache movies + posters in Room
   ↓
7. Display in Leanback grid
```

### Play Movie

```
1. User selects movie
   ↓
2. Fetch movie page HTML
   GET https://farsiland.com/movies/{slug}/
   ↓
3. Extract video URLs (Jsoup)
   <link itemprop="contentUrl" href="...">
   ↓
4. Show quality selector
   [1080p] [720p] [480p]
   ↓
5. User selects 1080p
   ↓
6. Play with ExoPlayer
   exoPlayer.setMediaItem(videoUrl)
```

### Watch TV Series

```
1. User selects series
   ↓
2. Fetch series page HTML
   GET https://farsiland.com/tvshows/{slug}/
   ↓
3. Parse episode list (Jsoup)
   <li class="mark-1-1">... S01E01
   ↓
4. Display episodes in grid
   ↓
5. User selects episode
   ↓
6. Fetch episode page HTML
   GET https://farsiland.com/episodes/{slug}/
   ↓
7. Extract video URLs
   ↓
8. Play with ExoPlayer
```

---

## Caching Strategy

### What to Cache

**Genres** (Cache forever):
- Fetch once on first launch
- Update weekly in background

**Movie/Show Listings** (1 hour TTL):
- Cache page 1-3 for offline browsing
- Refresh on app open

**Poster Images** (Persistent):
- Coil disk cache (250MB max)
- Never expire

**Video URLs** (24 hour TTL):
- Cache after extraction
- Re-fetch if expired

**Watchlist** (Local only):
- Room database
- Never synced to server

---

## Error Handling

### Network Errors
```kotlin
try {
    val movies = wordPressApi.getMovies(20)
} catch (e: IOException) {
    // No internet - use cached data
    val cached = database.movieDao().getAll()
    emit(cached)
}
```

### HTML Parsing Errors
```kotlin
val urls = try {
    VideoScraper.extractVideoUrls(pageUrl)
} catch (e: Exception) {
    // Scraping failed - try pattern generation
    VideoScraper.generateVideoUrls(seriesSlug, season, episode)
}
```

### CDN Failures
```kotlin
val primaryUrl = "https://d1.flnd.buzz/..."
if (!verifyUrl(primaryUrl)) {
    val backupUrl = primaryUrl.replace("d1.flnd.buzz", "d2.flnd.buzz")
    playVideo(backupUrl)
}
```

---

## Performance Optimization

### Pagination
- Load 20 items per page
- Infinite scroll (load more at bottom)
- Prefetch next page when 5 items from end

### Image Loading
- Use Coil with memory + disk cache
- Load thumbnails (185x274) for grids
- Load full size (708x1024) for details

### Parallel Requests
```kotlin
// Fetch movies and genres in parallel
coroutineScope {
    val moviesDeferred = async { api.getMovies(20) }
    val genresDeferred = async { api.getGenres(100) }

    val movies = moviesDeferred.await()
    val genres = genresDeferred.await()
}
```

### Rate Limiting
- 500ms delay between episode page requests
- Throttle background refresh to 1 req/second

---

## Testing Strategy

### Unit Tests
- Video URL extraction (Jsoup)
- Pattern generation
- Season/episode parsing

### Integration Tests
- WordPress API calls
- HTML scraping real pages
- CDN URL verification

### UI Tests
- Leanback navigation
- D-pad controls
- Video playback

---

## Known Limitations

1. **Episodes REST API** - No parent series ID
   - Workaround: HTML scrape series page

2. **Cast/Director Names** - Only IDs available
   - Workaround: Resolve via `/wp-json/wp/v2/dtcast/{id}`

3. **No IMDb Ratings** - Not in WordPress API
   - Workaround: Scrape from page HTML or skip

4. **Rate Limiting** - Unknown limits
   - Workaround: Conservative 500ms delays

5. **CDN Geo-blocking** - Possible
   - Workaround: Mirror fallback (d1 → d2)

---

## Timeline Estimate

| Phase | Duration | Tasks |
|-------|----------|-------|
| Setup | 1 day | Android TV project, dependencies |
| HTTP Client | 2 days | OkHttp, Retrofit, Jsoup setup |
| WordPress API | 3 days | Movies, shows, genres, media |
| HTML Scraper | 3 days | Video URL extraction, episodes |
| Local Cache | 2 days | Room database, caching logic |
| Repository | 2 days | Data layer, error handling |
| ViewModels | 2 days | Business logic, UI state |
| Leanback UI | 5 days | Browse, details, search screens |
| ExoPlayer | 2 days | Video playback, quality selection |
| Testing | 3 days | Unit, integration, UI tests |
| **TOTAL** | **25 days** | **~5 weeks** |

---

## Success Criteria

**Functional**:
- ✅ Browse 4,000+ movies/shows
- ✅ Search by title
- ✅ Filter by genre
- ✅ Play videos (all qualities)
- ✅ Track watchlist
- ✅ Work offline (cached content)

**Performance**:
- ✅ Movie list loads < 5 sec
- ✅ Episode scraping < 30 sec
- ✅ Video plays < 3 sec from click
- ✅ Memory < 200MB
- ✅ APK size < 50MB

**Quality**:
- ✅ Zero crashes (1 hour test)
- ✅ All errors handled gracefully
- ✅ Persian text displays correctly (RTL)
- ✅ D-pad navigation smooth

---

## Summary

**Farsiland.com Standalone Android TV App**:

**✅ FEASIBLE** - All required data accessible:
- WordPress REST API (public, no auth)
- HTML microdata (video URLs)
- CDN direct playback (no decryption)

**✅ SIMPLE** - No backend server needed:
- Just HTTP client + HTML parser
- Local caching with Room
- Direct MP4 playback

**✅ PROVEN** - FarsiFlow scraper demonstrates:
- HTML scraping works reliably
- Rate limiting strategy (500ms delays)
- Pattern generation as fallback

**Next Step**: Create Android TV project and start with WordPress API integration (movies, genres, poster resolution).
