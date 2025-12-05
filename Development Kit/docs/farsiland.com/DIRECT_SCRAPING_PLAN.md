# Farsiland Android TV App - Direct Website Scraping Plan

## Architecture Overview

**Approach**: Android TV app scrapes Farsiland.com directly using **shared account credentials** (hardcoded).

```
Android TV App
├── Login Module (shared credentials)
├── Session Manager (cookies/tokens)
├── HTML Scraper (Jsoup)
├── Content Parser (extract metadata + video URLs)
├── Video Player (ExoPlayer)
└── Local Cache (Room database)
```

---

## Authentication Strategy

### Login Flow

**Endpoint**: `https://farsiland.com/wp-login.php`

**Method**: POST

**Parameters**:
```
log={username}
pwd={password}
rememberme=forever
wp-submit=Log In
redirect_to=https://farsiland.com
testcookie=1
```

**Implementation**:
```kotlin
object FarsilandAuth {
    // HARDCODED SHARED ACCOUNT
    private const val USERNAME = "your_account@email.com"
    private const val PASSWORD = "your_password"

    suspend fun login(client: OkHttpClient): Boolean {
        val formBody = FormBody.Builder()
            .add("log", USERNAME)
            .add("pwd", PASSWORD)
            .add("rememberme", "forever")
            .add("wp-submit", "Log In")
            .add("redirect_to", "https://farsiland.com")
            .add("testcookie", "1")
            .build()

        val request = Request.Builder()
            .url("https://farsiland.com/wp-login.php")
            .post(formBody)
            .build()

        val response = client.newCall(request).execute()

        // WordPress sets cookies on successful login:
        // - wordpress_logged_in_{hash}
        // - wordpress_sec_{hash}
        // OkHttp CookieJar will automatically store these

        return response.isSuccessful
    }
}
```

### Session Management

**Cookies to Store** (WordPress sets these):
- `wordpress_logged_in_{hash}` - Main auth cookie
- `wordpress_sec_{hash}` - Security cookie
- `wp-settings-{user_id}` - User settings
- `wp-settings-time-{user_id}` - Settings timestamp

**Implementation**:
```kotlin
class PersistentCookieJar : CookieJar {
    private val cookieStore = mutableMapOf<String, List<Cookie>>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        cookieStore[url.host] = cookies
        // Also persist to SharedPreferences for app restart
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        return cookieStore[url.host] ?: emptyList()
    }
}

// Configure OkHttp
val client = OkHttpClient.Builder()
    .cookieJar(PersistentCookieJar())
    .build()
```

---

## Content Discovery

### WordPress REST API (For Metadata)

Even without auth, the REST API gives us basic metadata:

**Get Movies**:
```
GET /wp-json/wp/v2/movies?per_page=20&page=1
```

**Response** includes:
- `id` - Post ID
- `title.rendered` - Movie title
- `link` - URL to movie page
- `featured_media` - Poster image ID
- `genres` - Genre IDs (need to resolve)
- `date` - Publication date

**Get TV Shows**:
```
GET /wp-json/wp/v2/tvshows?per_page=20&page=1
```

**Get Episodes**:
```
GET /wp-json/wp/v2/episodes?per_page=20&page=1
```

### Resolving Metadata

**Get Featured Image URL**:
```
GET /wp-json/wp/v2/media/{featured_media_id}
```
Returns full image URL in `source_url` field.

**Get Genre Names**:
```
GET /wp-json/wp/v2/genres/{genre_id}
```
Returns genre name.

**Get Cast/Director**:
```
GET /wp-json/wp/v2/dtcast/{cast_id}
GET /wp-json/wp/v2/dtdirector/{director_id}
```

---

## Video URL Extraction

### Step 1: Get Authenticated Page HTML

Once logged in, fetch the movie/episode page:

```kotlin
suspend fun getPageHtml(url: String, client: OkHttpClient): String {
    val request = Request.Builder()
        .url(url)
        .build()

    val response = client.newCall(request).execute()
    return response.body?.string() ?: ""
}
```

### Step 2: Parse HTML for Video URLs

Based on FarsiFlow analysis, video URLs are in microdata or download links:

```kotlin
fun extractVideoUrls(html: String): List<VideoUrl> {
    val doc = Jsoup.parse(html)
    val urls = mutableListOf<VideoUrl>()

    // Method 1: Look for microdata (most reliable)
    doc.select("link[itemprop=contentUrl]").forEach { link ->
        val url = link.attr("href")
        if (url.contains(".mp4")) {
            urls.add(parseVideoUrl(url))
        }
    }

    // Method 2: Look for download links
    doc.select("a[href*=.mp4]").forEach { link ->
        val url = link.attr("href")
        urls.add(parseVideoUrl(url))
    }

    // Method 3: Check for iframes with CDN URLs
    doc.select("iframe[src*=flnd.buzz]").forEach { iframe ->
        val src = iframe.attr("src")
        urls.add(parseVideoUrl(src))
    }

    return urls
}

data class VideoUrl(
    val url: String,
    val quality: String, // "1080p", "720p", "480p"
    val fileSizeMb: Float?
)

fun parseVideoUrl(url: String): VideoUrl {
    // Extract quality from URL pattern
    // https://d1.flnd.buzz/series/shoghal/01.1080.mp4
    val quality = when {
        url.contains(".1080.") -> "1080p"
        url.contains(".720.") -> "720p"
        url.contains(".480.") -> "480p"
        else -> "unknown"
    }

    return VideoUrl(url, quality, null)
}
```

### Step 3: CDN Pattern Recognition

If HTML parsing fails, try generating URLs based on patterns:

```kotlin
fun generateCdnUrls(
    slug: String,
    season: Int?,
    episode: Int?,
    type: String // "movie" or "series"
): List<String> {
    val qualities = listOf("1080", "720", "480")
    val mirrors = listOf("d1.flnd.buzz", "d2.flnd.buzz")

    return mirrors.flatMap { mirror ->
        qualities.map { quality ->
            if (type == "series" && season != null && episode != null) {
                "https://$mirror/series/$slug/${episode.toString().padStart(2, '0')}.$quality.mp4"
            } else {
                "https://$mirror/movies/$slug.$quality.mp4"
            }
        }
    }
}

// Verify URL exists with HEAD request
suspend fun verifyUrl(url: String, client: OkHttpClient): Boolean {
    val request = Request.Builder()
        .url(url)
        .head()
        .build()

    return try {
        val response = client.newCall(request).execute()
        response.isSuccessful
    } catch (e: Exception) {
        false
    }
}
```

---

## Complete Scraping Flow

### For Movies

```kotlin
suspend fun scrapeMovie(movieId: Int): Movie {
    // 1. Get basic metadata from REST API
    val movieMeta = getMovieMetadata(movieId)

    // 2. Resolve poster image
    val posterUrl = getMediaUrl(movieMeta.featuredMedia)

    // 3. Resolve genres
    val genres = movieMeta.genres.map { getGenreName(it) }

    // 4. Get authenticated movie page HTML
    val html = getPageHtml(movieMeta.link, authenticatedClient)

    // 5. Extract video URLs
    val videoUrls = extractVideoUrls(html)

    // 6. If no URLs found, try pattern generation
    val finalUrls = if (videoUrls.isEmpty()) {
        val slug = extractSlug(movieMeta.link)
        generateCdnUrls(slug, null, null, "movie")
            .filter { verifyUrl(it, authenticatedClient) }
            .map { parseVideoUrl(it) }
    } else {
        videoUrls
    }

    return Movie(
        id = movieId,
        title = movieMeta.title,
        posterUrl = posterUrl,
        genres = genres,
        videoUrls = finalUrls
    )
}
```

### For TV Series + Episodes

```kotlin
suspend fun scrapeSeries(seriesId: Int): Series {
    // 1. Get series metadata
    val seriesMeta = getSeriesMetadata(seriesId)

    // 2. Get all episodes for this series
    val episodes = getEpisodes(seriesId)

    // 3. Scrape each episode's video URLs
    val episodeData = episodes.map { episode ->
        scrapeEpisode(episode)
    }

    return Series(
        id = seriesId,
        title = seriesMeta.title,
        posterUrl = getMediaUrl(seriesMeta.featuredMedia),
        genres = seriesMeta.genres.map { getGenreName(it) },
        episodes = episodeData
    )
}

suspend fun scrapeEpisode(episodeId: Int): Episode {
    val episodeMeta = getEpisodeMetadata(episodeId)
    val html = getPageHtml(episodeMeta.link, authenticatedClient)

    // Extract season/episode numbers from title or HTML
    val (season, episode) = extractSeasonEpisode(episodeMeta.title, html)

    val videoUrls = extractVideoUrls(html)

    return Episode(
        id = episodeId,
        season = season,
        episode = episode,
        title = episodeMeta.title,
        videoUrls = videoUrls
    )
}

fun extractSeasonEpisode(title: String, html: String): Pair<Int, Int> {
    // Parse from title like "Shoghal SE01 EP05"
    val seasonRegex = Regex("SE?(\\d+)", RegexOption.IGNORE_CASE)
    val episodeRegex = Regex("EP?(\\d+)", RegexOption.IGNORE_CASE)

    val season = seasonRegex.find(title)?.groupValues?.get(1)?.toIntOrNull() ?: 1
    val episode = episodeRegex.find(title)?.groupValues?.get(1)?.toIntOrNull() ?: 1

    return Pair(season, episode)
}
```

---

## Android Implementation

### Dependencies (build.gradle)

```gradle
dependencies {
    // Core Android TV
    implementation "androidx.leanback:leanback:1.2.0-alpha02"
    implementation "androidx.tvprovider:tvprovider:1.0.0"

    // Networking
    implementation "com.squareup.okhttp3:okhttp:4.11.0"
    implementation "com.squareup.retrofit2:retrofit:2.9.0"
    implementation "com.squareup.retrofit2:converter-gson:2.9.0"

    // HTML Parsing
    implementation "org.jsoup:jsoup:1.16.1"

    // Video Player
    implementation "com.google.android.exoplayer:exoplayer:2.19.1"

    // Database
    implementation "androidx.room:room-runtime:2.5.2"
    implementation "androidx.room:room-ktx:2.5.2"
    kapt "androidx.room:room-compiler:2.5.2"

    // Image Loading
    implementation "io.coil-kt:coil:2.4.0"

    // Coroutines
    implementation "org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3"
}
```

### Session Manager

```kotlin
class SessionManager @Inject constructor(
    private val context: Context
) {
    private val prefs = context.getSharedPreferences("farsiland_session", Context.MODE_PRIVATE)
    private val client = OkHttpClient.Builder()
        .cookieJar(PersistentCookieJar(prefs))
        .build()

    suspend fun ensureLoggedIn(): Boolean {
        // Check if we have valid cookies
        if (hasValidSession()) {
            return true
        }

        // Login with hardcoded credentials
        return FarsilandAuth.login(client)
    }

    private fun hasValidSession(): Boolean {
        val lastLogin = prefs.getLong("last_login", 0)
        val now = System.currentTimeMillis()
        // Session valid for 7 days
        return (now - lastLogin) < 7 * 24 * 60 * 60 * 1000
    }

    fun getClient(): OkHttpClient = client
}
```

### Content Repository

```kotlin
class ContentRepository @Inject constructor(
    private val sessionManager: SessionManager,
    private val database: AppDatabase
) {
    suspend fun getMovies(page: Int): List<Movie> {
        // Ensure we're logged in
        sessionManager.ensureLoggedIn()

        // Fetch from WordPress API
        val response = fetchMoviesFromApi(page)

        // Scrape full details for each
        return response.map { scrapeMovie(it.id) }
    }

    suspend fun getVideoUrls(movieId: Int): List<VideoUrl> {
        sessionManager.ensureLoggedIn()

        // Check cache first
        val cached = database.videoUrlDao().getUrls(movieId)
        if (cached.isNotEmpty() && !isExpired(cached.first().cachedAt)) {
            return cached
        }

        // Scrape fresh
        val movie = scrapeMovie(movieId)

        // Cache for 24 hours
        database.videoUrlDao().insert(movie.videoUrls)

        return movie.videoUrls
    }
}
```

---

## Caching Strategy

### What to Cache

1. **Session Cookies** (SharedPreferences)
   - Persist across app restarts
   - Refresh every 7 days

2. **Content Metadata** (Room Database)
   ```kotlin
   @Entity
   data class CachedMovie(
       @PrimaryKey val id: Int,
       val title: String,
       val posterUrl: String,
       val genres: List<String>,
       val cachedAt: Long
   )
   ```
   - TTL: 24 hours

3. **Video URLs** (Room Database)
   ```kotlin
   @Entity
   data class CachedVideoUrl(
       @PrimaryKey(autoGenerate = true) val id: Long = 0,
       val contentId: Int,
       val quality: String,
       val url: String,
       val cachedAt: Long
   )
   ```
   - TTL: 24 hours (URLs are stable)

4. **Images** (Coil disk cache)
   - Persistent, managed by Coil

---

## Error Handling

### Authentication Errors

```kotlin
class AuthException(message: String) : Exception(message)

suspend fun <T> withAuth(block: suspend () -> T): T {
    try {
        return block()
    } catch (e: UnauthorizedException) {
        // Re-login and retry
        sessionManager.ensureLoggedIn()
        return block()
    }
}
```

### Scraping Errors

```kotlin
sealed class ScrapingError {
    object NoVideoUrls : ScrapingError()
    object PageNotFound : ScrapingError()
    object LoginRequired : ScrapingError()
    data class NetworkError(val cause: Exception) : ScrapingError()
}

fun handleScrapingError(error: ScrapingError) {
    when (error) {
        is NoVideoUrls -> {
            // Try pattern generation
            tryGenerateUrls()
        }
        is LoginRequired -> {
            // Force re-login
            sessionManager.forceLogin()
        }
        else -> {
            // Show error to user
            showErrorMessage(error)
        }
    }
}
```

---

## Performance Optimization

### Parallel Scraping

```kotlin
suspend fun scrapeMovies(movieIds: List<Int>): List<Movie> = coroutineScope {
    movieIds.map { id ->
        async {
            withAuth { scrapeMovie(id) }
        }
    }.awaitAll()
}
```

### Request Throttling

```kotlin
class RateLimiter {
    private val requestTimes = mutableListOf<Long>()

    suspend fun throttle() {
        val now = System.currentTimeMillis()
        requestTimes.removeAll { it < now - 1000 } // Keep last second

        if (requestTimes.size >= 5) {
            // Max 5 requests per second
            delay(200)
        }

        requestTimes.add(now)
    }
}
```

---

## Implementation Stages

### Stage 1: Authentication & Session
- [ ] Implement WordPress login
- [ ] Cookie persistence
- [ ] Session validation
- [ ] Test with real credentials

### Stage 2: Basic Scraping
- [ ] Fetch movie list from API
- [ ] Scrape single movie page
- [ ] Extract video URLs
- [ ] Test on 10 different movies

### Stage 3: Series Support
- [ ] Fetch TV shows
- [ ] Get episode lists
- [ ] Scrape episode pages
- [ ] Extract season/episode numbers

### Stage 4: Pattern Generation
- [ ] Implement CDN URL patterns
- [ ] URL verification (HEAD requests)
- [ ] Fallback to pattern when scraping fails

### Stage 5: UI Integration
- [ ] Browse Fragment (home screen)
- [ ] Details Fragment
- [ ] Video playback
- [ ] Quality selection

### Stage 6: Polish
- [ ] Caching
- [ ] Error handling
- [ ] Performance optimization
- [ ] Testing

---

## Security Considerations

### Hardcoded Credentials

**⚠️ WARNING**: Hardcoding credentials is insecure!

**Better Approach** (if possible):
1. Obfuscate credentials in code
2. Use ProGuard/R8 to make reverse engineering harder
3. Consider server-side proxy (app talks to your server, server talks to Farsiland)

**Minimal Obfuscation**:
```kotlin
object Credentials {
    // Base64 encoded (NOT SECURE, but better than plain text)
    private const val ENC_USER = "eW91cl9hY2NvdW50QGVtYWlsLmNvbQ=="
    private const val ENC_PASS = "eW91cl9wYXNzd29yZA=="

    fun getUsername(): String = String(Base64.decode(ENC_USER, Base64.DEFAULT))
    fun getPassword(): String = String(Base64.decode(ENC_PASS, Base64.DEFAULT))
}
```

---

## Testing Strategy

### Unit Tests
- Video URL extraction logic
- Season/episode parsing
- Pattern generation

### Integration Tests
- Login flow
- Scraping individual pages
- API calls

### Manual Testing
- Test on 20+ movies
- Test on 5+ TV series
- Verify all qualities work
- Check CDN fallback

---

## Summary

**Direct Scraping Approach**:

1. **Login** with hardcoded credentials → Get WordPress cookies
2. **Browse** content via WordPress REST API (metadata)
3. **Scrape** authenticated HTML pages for video URLs
4. **Extract** URLs from microdata or generate from patterns
5. **Play** videos directly from CDN (d1.flnd.buzz)
6. **Cache** everything aggressively to reduce requests

**Trade-offs vs. FarsiFlow Backend**:

| Aspect | Direct Scraping | FarsiFlow Backend |
|--------|----------------|-------------------|
| Setup Complexity | Higher | Lower |
| Maintenance | Breaks if site changes | Backend absorbs changes |
| Performance | Slower (HTML parsing) | Faster (direct queries) |
| Reliability | Depends on site structure | More stable |
| Independence | Fully self-contained | Requires backend running |

**Recommendation**: Direct scraping works, but be prepared for maintenance when Farsiland.com changes their HTML structure.
