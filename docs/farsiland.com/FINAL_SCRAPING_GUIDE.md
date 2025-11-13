# Farsiland Android TV App - Final Scraping Guide
**NO AUTHENTICATION REQUIRED!**

## Discovery Summary

After analyzing your FarsiFlow scraper code, I discovered that:

✅ **Video URLs are publicly accessible** - No login needed!
✅ **URLs are in HTML microdata** - `<link itemprop="contentUrl">`
✅ **Simple HTTP GET requests** - No cookies, sessions, or authentication
✅ **Fallback pattern generation** - Can generate URLs if scraping fails

---

## How Farsiland.com Really Works

### Video URL Structure

**Direct CDN URLs**:
```
https://d1.flnd.buzz/series/{slug}/{episode:02d}.{quality}.mp4

Examples:
https://d1.flnd.buzz/series/shoghal/01.1080.mp4  (S1E1, 1080p)
https://d1.flnd.buzz/series/shoghal/01.720.mp4   (S1E1, 720p)
https://d1.flnd.buzz/series/shoghal/01.480.mp4   (S1E1, 480p)
```

**CDN Mirrors**:
- Primary: `d1.flnd.buzz`
- Backup: `d2.flnd.buzz`

### How URLs Are Embedded

**In Episode Page HTML** (publicly visible):
```html
<link itemprop="contentUrl" href="https://d1.flnd.buzz/series/shoghal/01.1080.mp4">
<link itemprop="contentUrl" href="https://d1.flnd.buzz/series/shoghal/01.720.mp4">
<link itemprop="contentUrl" href="https://d1.flnd.buzz/series/shoghal/01.480.mp4">
```

This is **Schema.org microdata** for SEO - publicly accessible, no login required!

---

## Android Implementation

### Simple 3-Step Process

```kotlin
// Step 1: Fetch episode page HTML
val html = getEpisodePageHtml("https://farsiland.com/episodes/shoghal-s1e1/")

// Step 2: Extract video URLs with Jsoup
val videoUrls = parseVideoUrls(html)

// Step 3: Play with ExoPlayer
exoPlayer.play(videoUrls.first().url)
```

### Dependencies (build.gradle.kts)

```kotlin
dependencies {
    // Core Android TV
    implementation("androidx.leanback:leanback:1.2.0-alpha02")

    // HTTP Client
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // HTML Parser
    implementation("org.jsoup:jsoup:1.17.1")

    // Video Player
    implementation("androidx.media3:media3-exoplayer:1.2.0")
    implementation("androidx.media3:media3-ui:1.2.0")

    // JSON (for WordPress API)
    implementation("com.squareup.moshi:moshi:1.15.0")
    implementation("com.squareup.moshi:moshi-kotlin:1.15.0")

    // Image Loading
    implementation("io.coil-kt:coil:2.5.0")

    // Database
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
}
```

---

## Complete Code Implementation

### 1. HTTP Client Setup

```kotlin
// network/HttpClient.kt
object HttpClient {
    val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build()
            chain.proceed(request)
        }
        .build()

    suspend fun get(url: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).build()
        val response = client.newCall(request).execute()

        if (!response.isSuccessful) {
            throw IOException("HTTP ${response.code}")
        }

        response.body?.string() ?: throw IOException("Empty response")
    }
}
```

### 2. Video URL Scraper

```kotlin
// scraper/VideoUrlScraper.kt
object VideoUrlScraper {

    data class VideoUrl(
        val url: String,
        val quality: String, // "1080p", "720p", "480p"
        val fileSizeMb: Float? = null
    )

    /**
     * Extract video URLs from episode page HTML
     *
     * Method: Parse microdata <link itemprop="contentUrl">
     */
    suspend fun extractVideoUrls(episodeUrl: String): List<VideoUrl> {
        // Fetch HTML
        val html = HttpClient.get(episodeUrl)

        // Parse with Jsoup
        val doc = Jsoup.parse(html)

        // Find all contentUrl links in microdata
        val videoUrls = doc.select("link[itemprop=contentUrl]").mapNotNull { element ->
            val href = element.attr("href")

            if (href.isNotEmpty() && ".mp4" in href) {
                val quality = when {
                    ".1080.mp4" in href -> "1080p"
                    ".720.mp4" in href -> "720p"
                    ".480.mp4" in href -> "480p"
                    else -> "unknown"
                }

                VideoUrl(url = href, quality = quality)
            } else {
                null
            }
        }

        return videoUrls.sortedByDescending {
            when (it.quality) {
                "1080p" -> 3
                "720p" -> 2
                "480p" -> 1
                else -> 0
            }
        }
    }

    /**
     * Fallback: Generate URLs using pattern if scraping fails
     */
    fun generateVideoUrls(
        seriesSlug: String,
        season: Int,
        episode: Int
    ): List<VideoUrl> {
        val episodeNum = String.format("%02d", episode)
        val mirrors = listOf("d1.flnd.buzz", "d2.flnd.buzz")
        val qualities = listOf(
            "1080" to "1080p",
            "720" to "720p",
            "480" to "480p"
        )

        return mirrors.flatMap { mirror ->
            qualities.map { (ext, quality) ->
                VideoUrl(
                    url = "https://$mirror/series/$seriesSlug/$episodeNum.$ext.mp4",
                    quality = quality
                )
            }
        }
    }

    /**
     * Verify URL exists with HEAD request
     */
    suspend fun verifyUrl(url: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(url).head().build()
            val response = HttpClient.client.newCall(request).execute()
            response.isSuccessful
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Get working video URL with fallback
     */
    suspend fun getWorkingVideoUrl(episodeUrl: String, seriesSlug: String, season: Int, episode: Int): VideoUrl? {
        // Try scraping first
        val scrapedUrls = try {
            extractVideoUrls(episodeUrl)
        } catch (e: Exception) {
            Log.e("Scraper", "Failed to scrape: ${e.message}")
            emptyList()
        }

        if (scrapedUrls.isNotEmpty()) {
            // Verify the first URL works
            if (verifyUrl(scrapedUrls.first().url)) {
                return scrapedUrls.first()
            }
        }

        // Fallback: Generate URLs and test them
        val generatedUrls = generateVideoUrls(seriesSlug, season, episode)

        for (videoUrl in generatedUrls) {
            if (verifyUrl(videoUrl.url)) {
                return videoUrl
            }
        }

        return null
    }
}
```

### 3. WordPress API Client (for metadata)

```kotlin
// api/WordPressApi.kt
data class WPMovie(
    val id: Int,
    val title: WPTitle,
    val link: String,
    val featured_media: Int,
    val genres: List<Int>,
    val date: String
)

data class WPTitle(
    val rendered: String
)

data class WPMedia(
    val id: Int,
    val source_url: String
)

interface WordPressApiService {
    @GET("/wp-json/wp/v2/movies")
    suspend fun getMovies(
        @Query("per_page") perPage: Int = 20,
        @Query("page") page: Int = 1
    ): List<WPMovie>

    @GET("/wp-json/wp/v2/tvshows")
    suspend fun getTvShows(
        @Query("per_page") perPage: Int = 20,
        @Query("page") page: Int = 1
    ): List<WPMovie>

    @GET("/wp-json/wp/v2/episodes")
    suspend fun getEpisodes(
        @Query("per_page") perPage: Int = 20,
        @Query("page") page: Int = 1
    ): List<WPMovie>

    @GET("/wp-json/wp/v2/media/{id}")
    suspend fun getMedia(@Path("id") id: Int): WPMedia
}

object WordPressApi {
    private val retrofit = Retrofit.Builder()
        .baseUrl("https://farsiland.com")
        .client(HttpClient.client)
        .addConverterFactory(MoshiConverterFactory.create())
        .build()

    val service: WordPressApiService = retrofit.create(WordPressApiService::class.java)
}
```

### 4. Complete Content Repository

```kotlin
// repository/ContentRepository.kt
class ContentRepository(
    private val database: AppDatabase
) {

    /**
     * Get movies from WordPress API
     */
    suspend fun getMovies(page: Int = 1): List<Movie> {
        val wpMovies = WordPressApi.service.getMovies(page = page)

        return wpMovies.map { wpMovie ->
            // Get poster URL
            val posterUrl = try {
                val media = WordPressApi.service.getMedia(wpMovie.featured_media)
                media.source_url
            } catch (e: Exception) {
                null
            }

            Movie(
                id = wpMovie.id,
                title = wpMovie.title.rendered,
                posterUrl = posterUrl,
                farsilandUrl = wpMovie.link,
                year = wpMovie.date.substring(0, 4).toIntOrNull()
            )
        }
    }

    /**
     * Get TV shows from WordPress API
     */
    suspend fun getTvShows(page: Int = 1): List<Series> {
        val wpShows = WordPressApi.service.getTvShows(page = page)

        return wpShows.map { wpShow ->
            val posterUrl = try {
                val media = WordPressApi.service.getMedia(wpShow.featured_media)
                media.source_url
            } catch (e: Exception) {
                null
            }

            Series(
                id = wpShow.id,
                title = wpShow.title.rendered,
                posterUrl = posterUrl,
                farsilandUrl = wpShow.link
            )
        }
    }

    /**
     * Get video URLs for movie/episode
     */
    suspend fun getVideoUrls(farsilandUrl: String, seriesSlug: String? = null, season: Int = 1, episode: Int = 1): List<VideoUrlScraper.VideoUrl> {
        // Try scraping first
        val scrapedUrls = try {
            VideoUrlScraper.extractVideoUrls(farsilandUrl)
        } catch (e: Exception) {
            Log.e("Repository", "Scraping failed: ${e.message}")
            emptyList()
        }

        if (scrapedUrls.isNotEmpty()) {
            return scrapedUrls
        }

        // Fallback: Generate URLs if we have series slug
        if (seriesSlug != null) {
            val generatedUrls = VideoUrlScraper.generateVideoUrls(seriesSlug, season, episode)

            // Verify at least one works
            for (url in generatedUrls) {
                if (VideoUrlScraper.verifyUrl(url.url)) {
                    return generatedUrls.filter { it.url.contains(url.url.split("/")[2]) } // Same mirror
                }
            }
        }

        return emptyList()
    }
}
```

### 5. ExoPlayer Setup

```kotlin
// player/VideoPlayer.kt
class VideoPlayerActivity : AppCompatActivity() {

    private lateinit var player: ExoPlayer
    private lateinit var playerView: PlayerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video_player)

        playerView = findViewById(R.id.player_view)

        // Get video URL from intent
        val videoUrl = intent.getStringExtra("VIDEO_URL") ?: return
        val title = intent.getStringExtra("TITLE") ?: "Video"

        setupPlayer(videoUrl, title)
    }

    private fun setupPlayer(url: String, title: String) {
        player = ExoPlayer.Builder(this).build()
        playerView.player = player

        // Create media item
        val mediaItem = MediaItem.Builder()
            .setUri(url)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .build()
            )
            .build()

        // Load and play
        player.setMediaItem(mediaItem)
        player.prepare()
        player.play()
    }

    override fun onStop() {
        super.onStop()
        player.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        player.release()
    }
}
```

---

## Usage Examples

### Example 1: Play Movie

```kotlin
suspend fun playMovie(movieId: Int) {
    // 1. Get movie details from WordPress API
    val movie = repository.getMovie(movieId)

    // 2. Extract video URLs from movie page
    val videoUrls = VideoUrlScraper.extractVideoUrls(movie.farsilandUrl)

    // 3. Let user choose quality
    val selectedUrl = showQualitySelector(videoUrls) // User picks 1080p

    // 4. Play video
    val intent = Intent(this, VideoPlayerActivity::class.java).apply {
        putExtra("VIDEO_URL", selectedUrl.url)
        putExtra("TITLE", movie.title)
    }
    startActivity(intent)
}
```

### Example 2: Play Episode

```kotlin
suspend fun playEpisode(episodeId: Int) {
    // 1. Get episode info
    val episode = repository.getEpisode(episodeId)

    // 2. Parse season/episode from URL or title
    val (season, episodeNum) = parseSeasonEpisode(episode.link)
    // Example: "shoghal-s1e5" -> (1, 5)

    // 3. Extract series slug
    val seriesSlug = extractSeriesSlug(episode.link)
    // Example: "https://farsiland.com/episodes/shoghal-s1e5/" -> "shoghal"

    // 4. Get video URL (scrape or generate)
    val videoUrl = VideoUrlScraper.getWorkingVideoUrl(
        episodeUrl = episode.link,
        seriesSlug = seriesSlug,
        season = season,
        episode = episodeNum
    )

    // 5. Play video
    if (videoUrl != null) {
        playVideo(videoUrl.url, "S${season}E${episodeNum}")
    }
}

fun extractSeriesSlug(url: String): String {
    // https://farsiland.com/episodes/shoghal-s1e5/
    val regex = Regex("episodes/([a-z-]+)-s\\d+e\\d+")
    return regex.find(url)?.groupValues?.get(1) ?: ""
}

fun parseSeasonEpisode(url: String): Pair<Int, Int> {
    // Parse from URL: shoghal-s1e5 -> (1, 5)
    val regex = Regex("s(\\d+)e(\\d+)", RegexOption.IGNORE_CASE)
    val match = regex.find(url) ?: return Pair(1, 1)

    val season = match.groupValues[1].toInt()
    val episode = match.groupValues[2].toInt()

    return Pair(season, episode)
}
```

### Example 3: Quality Selection UI

```kotlin
fun showQualitySelector(videoUrls: List<VideoUrlScraper.VideoUrl>): VideoUrlScraper.VideoUrl {
    val qualities = videoUrls.map { it.quality }

    // Show dialog or menu on TV
    AlertDialog.Builder(this)
        .setTitle("Select Quality")
        .setItems(qualities.toTypedArray()) { _, which ->
            val selected = videoUrls[which]
            playVideo(selected.url)
        }
        .show()

    // Return default (1080p)
    return videoUrls.first()
}
```

---

## Error Handling

### Scraping Failures

```kotlin
suspend fun getVideoUrlSafely(episodeUrl: String): VideoUrl? {
    return try {
        val urls = VideoUrlScraper.extractVideoUrls(episodeUrl)

        if (urls.isEmpty()) {
            Log.e("Scraper", "No URLs found in HTML")
            null
        } else {
            // Verify URL works
            if (VideoUrlScraper.verifyUrl(urls.first().url)) {
                urls.first()
            } else {
                Log.e("Scraper", "URL verification failed")
                null
            }
        }
    } catch (e: IOException) {
        Log.e("Scraper", "Network error: ${e.message}")
        null
    } catch (e: Exception) {
        Log.e("Scraper", "Unexpected error: ${e.message}")
        null
    }
}
```

### CDN Mirror Fallback

```kotlin
suspend fun playWithFallback(videoUrl: String) {
    var workingUrl = videoUrl

    // Try primary mirror (d1.flnd.buzz)
    if (!VideoUrlScraper.verifyUrl(workingUrl)) {
        // Try backup mirror (d2.flnd.buzz)
        workingUrl = videoUrl.replace("d1.flnd.buzz", "d2.flnd.buzz")

        if (!VideoUrlScraper.verifyUrl(workingUrl)) {
            // Both failed
            showError("Video not available")
            return
        }
    }

    // Play working URL
    player.setMediaItem(MediaItem.fromUri(workingUrl))
    player.play()
}
```

---

## Performance Optimization

### Caching Strategy

```kotlin
@Entity
data class CachedVideoUrl(
    @PrimaryKey val episodeUrl: String,
    val videoUrl: String,
    val quality: String,
    val cachedAt: Long = System.currentTimeMillis()
)

class VideoUrlCache(private val dao: VideoUrlDao) {

    suspend fun getOrFetch(episodeUrl: String): VideoUrl? {
        // Check cache first (24 hour TTL)
        val cached = dao.get(episodeUrl)
        val now = System.currentTimeMillis()

        if (cached != null && (now - cached.cachedAt) < 24 * 60 * 60 * 1000) {
            return VideoUrl(cached.videoUrl, cached.quality)
        }

        // Fetch fresh
        val fresh = VideoUrlScraper.extractVideoUrls(episodeUrl).firstOrNull()

        // Cache for next time
        if (fresh != null) {
            dao.insert(
                CachedVideoUrl(
                    episodeUrl = episodeUrl,
                    videoUrl = fresh.url,
                    quality = fresh.quality
                )
            )
        }

        return fresh
    }
}
```

### Parallel Fetching

```kotlin
suspend fun getMultipleVideoUrls(episodeUrls: List<String>): List<VideoUrl> = coroutineScope {
    episodeUrls.map { url ->
        async {
            try {
                VideoUrlScraper.extractVideoUrls(url).firstOrNull()
            } catch (e: Exception) {
                null
            }
        }
    }.awaitAll().filterNotNull()
}
```

---

## Testing

### Unit Test Example

```kotlin
@Test
fun testVideoUrlExtraction() = runBlocking {
    val testHtml = """
        <html>
            <head>
                <link itemprop="contentUrl" href="https://d1.flnd.buzz/series/test/01.1080.mp4">
                <link itemprop="contentUrl" href="https://d1.flnd.buzz/series/test/01.720.mp4">
            </head>
        </html>
    """.trimIndent()

    val doc = Jsoup.parse(testHtml)
    val urls = doc.select("link[itemprop=contentUrl]").map { it.attr("href") }

    assertEquals(2, urls.size)
    assertTrue(urls[0].contains("1080"))
    assertTrue(urls[1].contains("720"))
}
```

---

## Summary

### What We Have

✅ **No authentication needed** - Simple HTTP GET requests
✅ **Video URLs in HTML microdata** - Easy to parse with Jsoup
✅ **Pattern generation fallback** - Works even if scraping fails
✅ **Multiple quality options** - 1080p, 720p, 480p
✅ **CDN mirror fallback** - d1.flnd.buzz + d2.flnd.buzz
✅ **WordPress REST API** - For metadata (titles, posters, etc.)

### Implementation Complexity

| Task | Complexity |
|------|------------|
| HTTP requests | ⭐ Very Easy |
| HTML parsing | ⭐⭐ Easy |
| Video playback | ⭐⭐ Easy (ExoPlayer) |
| UI (Leanback) | ⭐⭐⭐ Moderate |
| Total | **⭐⭐ Easy Project!** |

### Next Step

**Create Android TV project** and start implementing:

1. **Stage 1**: HTTP client + HTML scraper (1-2 days)
2. **Stage 2**: Video playback (1 day)
3. **Stage 3**: Leanback UI (2-3 days)
4. **Stage 4**: Polish (1 day)

**Total: ~1 week for working app!**

Ready to start building?
