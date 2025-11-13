# NAMAKADE.COM COMPLETE TECHNICAL ANALYSIS

**Date:** 2025-10-29
**Status:** COMPLETE - INTEGRATION FEASIBLE

---

## EXECUTIVE SUMMARY

Namakade.com (also known as Negahestan.com) is **fully scrapable and integrable** with your existing Android TV app. The website uses **direct MP4 video delivery** for on-demand content and **HLS streaming** for live TV channels.

### Key Findings:
✅ Direct MP4 URLs for series, movies, and shows
✅ No authentication required for on-demand videos
✅ HLS streaming for live TV (requires session tokens)
✅ CDN: media.negahestan.com
✅ HTML scraping required (no REST API)
✅ Similar architecture to farsiland.com

**CRITICAL:** Integration is feasible using HTML scraping approach similar to farsiland.com

---

## 1. WEBSITE ARCHITECTURE

### Technology Stack
- **Frontend:** jQuery with carouFredSel carousel plugin
- **Video Player:** Video.js with HTTP streaming support
- **Server Rendering:** Traditional server-side HTML rendering
- **CDN:** media.negahestan.com/ipnx/
- **Live Streaming:** GLWiz platform (glwiz.com)

### Content Categories
1. **TV Series** (Iranian & Turkish) - `/best-serial`, `/serieses/`
2. **Movies** - `/best-movies`, `/movies/`
3. **Shows** - `/show`, `/shows/`
4. **Music Videos** - `/musicvideos`
5. **Live TV** - `/livetvs` (30+ channels)
6. **Cartoons** - `/cartoon`, `/series/`
7. **Video Clips** - `/videoclips` (YouTube embeds)

---

## 2. VIDEO DELIVERY MECHANISMS

### On-Demand Content (Series, Movies, Shows)

**Delivery Method:** Direct MP4 file download/streaming
**Protocol:** HTTP/HTTPS
**Authentication:** None required
**CDN:** media.negahestan.com

#### URL Patterns Discovered:

**Series Episodes:**
```
https://media.negahestan.com/ipnx/media/series/episodes/{SeriesName}_{EpisodeNumber}.mp4

Example:
https://media.negahestan.com/ipnx/media/series/episodes/Algoritm_01.mp4
https://media.negahestan.com/ipnx/media/series/episodes/Algoritm_02.mp4
```

**Movies:**
```
https://media.negahestan.com/ipnx/media/movies/{MovieName}.mp4

Example:
https://media.negahestan.com/ipnx/media/movies/Pirpesar.mp4
```

**Thumbnails:**
```
Series: https://media.negahestan.com/ipnx/media/series/thumbs/{SeriesName}_smallthumb.jpg
Episodes: https://media.negahestan.com/ipnx/media/series/episodes/thumbs/{SeriesName}_{EpisodeNumber}_thumb.jpg
Movies: https://media.negahestan.com/ipnx/media/movies/thumbs/{MovieName}_thumb.jpg
```

### Live TV Streaming

**Delivery Method:** HLS (HTTP Live Streaming)
**Protocol:** .m3u8 playlists
**Authentication:** Session tokens required
**CDN:** GLWiz platform

**Live TV URL Pattern:**
```
https://glwizhstb46.glwiz.com/{ChannelName}_HD.m3u8?user={user_id}&session={session_token}&group_id={group_id}

Example:
https://glwizhstb46.glwiz.com/GEMTV_HD.m3u8?user=sgls76551&session=efb5530e6ad3064004662e83e4fe8587270c111eccc21f6ccccdcd5b88ccd787759da44f728a1efa&group_id=76551
```

**IP Geolocation:**
- Uses `https://api.gliptv.com/ip.aspx` to get client IP
- IP appended to live TV URLs for access control

---

## 3. API ENDPOINTS & DATA SOURCES

### ❌ NO REST API

Unlike farsiland.com (which has WordPress REST API), namakade.com does **NOT** provide JSON APIs for content listings.

### Available Endpoints (All return HTML):

| Endpoint | Method | Returns | Purpose |
|----------|--------|---------|---------|
| `/search?page=livesearch&q={query}` | GET | HTML | Live search results |
| `/ratings` | POST | HTML/JSON | Submit content ratings |
| `/playlist` | GET/POST | HTML | Playlist management |
| `/logout` | GET | Redirect | Session termination |

**IMPLICATION:** All content discovery must be done via **HTML scraping**

---

## 4. CONTENT STRUCTURE & HTML SCRAPING

### Series Listing Page

**URL:** `https://namakade.com/best-serial` or `https://namakade.com/serieses/{series-slug}`

**Scraping Strategy:**
```
1. Fetch category page (e.g., /best-serial)
2. Parse series cards with CSS selectors:
   - Title: link text
   - Slug: extract from href="/series/{slug}"
   - Episodes: "Episodes: {number}"
   - Genre: "Genre: {genre}"
   - Thumbnail: <img src="...{SeriesName}_smallthumb.jpg">
```

### Series Episode List

**URL:** `https://namakade.com/serieses/{series-slug}`

**HTML Structure:**
```html
<li class="mark-*">
  <div class="numerando">1</div>
  <a href="/series/{slug}/episodes/{episode-slug}">Episode 1</a>
</li>
```

**Scraping Steps:**
1. Fetch series page
2. Extract season selector (if multiple seasons)
3. Parse episode list items
4. Extract episode numbers and slugs
5. Construct episode URLs

### Episode Video Page

**URL:** `https://namakade.com/serieses/{series-slug}/episodes/{episode-slug}`

**Video URL Extraction:**
```html
<video id="videoTag">
  <source src="https://media.negahestan.com/ipnx/media/series/episodes/Algoritm_01.mp4" type="video/mp4">
</video>
```

**Scraping Method:**
- Parse `<video>` tag
- Extract `<source src="...">` attribute
- Validate URL pattern

### Movie Listing Page

**URL:** `https://namakade.com/best-movies`

**Movie Card Structure:**
```html
<div class="movie-card">
  <a href="/best-1-movies/{genre}/{movie-slug}">
    <img src="https://media.negahestan.com/ipnx/media/movies/thumbs/{MovieName}_thumb.jpg">
    <div>{Movie Title}</div>
    <div>Director: {Director Name}</div>
    <div>Genre: {Genre}</div>
  </a>
</div>
```

### Live TV Channel List

**URL:** `https://namakade.com/livetvs`

**Channel Card:**
```html
<a href="/livetv/{ChannelName}/{ClientIP}">
  <img src="https://hd200.glwiz.com/menu/epg/imagesNew/l{channel_id}.png">
</a>
```

**Video Source (after clicking):**
- Page contains `<source>` tag with HLS m3u8 URL
- Session tokens are dynamically generated

---

## 5. INTEGRATION WITH FARSILAND APP

### Feasibility: ✅ HIGH

Namakade.com can be integrated using the **same architectural patterns** as farsiland.com:

| Feature | Farsiland.com | Namakade.com | Approach |
|---------|--------------|--------------|----------|
| Content Listing | WordPress REST API | HTML Scraping | **Add HTML Scraper** |
| Episode Lists | HTML Scraping | HTML Scraping | **Reuse Logic** |
| Video URLs | Direct CDN URLs | Direct MP4 URLs | **Same Pattern** |
| Player | ExoPlayer | ExoPlayer | **No Change** |
| Thumbnails | CDN | CDN | **Same Logic** |
| Caching | Room DB | Room DB | **Extend Schema** |

### Required Changes to Existing App:

#### 1. Add Namakade Content Provider

**New Classes:**
```kotlin
// Network Layer
class NamakadeApiService {
    suspend fun scrapeCategoryPage(url: String): List<SeriesCard>
    suspend fun scrapeSeriesPage(slug: String): SeriesDetails
    suspend fun scrapeEpisodeList(slug: String): List<Episode>
    suspend fun scrapeVideoUrl(episodeUrl: String): String
}

// HTML Parser
class NamakadeHtmlParser {
    fun parseSeriesCards(html: String): List<SeriesCard>
    fun parseEpisodeList(html: String): List<Episode>
    fun parseVideoSource(html: String): String
}

// URL Generator
object NamakadeUrlBuilder {
    fun buildSeriesVideoUrl(seriesName: String, episode: Int): String {
        return "https://media.negahestan.com/ipnx/media/series/episodes/${seriesName}_${episode.toString().padStart(2, '0')}.mp4"
    }

    fun buildMovieVideoUrl(movieName: String): String {
        return "https://media.negahestan.com/ipnx/media/movies/${movieName}.mp4"
    }
}
```

#### 2. Extend Database Schema

```kotlin
@Entity(tableName = "content_source")
data class ContentSource(
    @PrimaryKey val id: String,
    val name: String, // "farsiland" or "namakade"
    val enabled: Boolean
)

@Entity(tableName = "series")
data class Series(
    @PrimaryKey val id: String,
    val title: String,
    val slug: String,
    val source: String, // "farsiland" or "namakade"
    val thumbnail: String,
    val genre: String,
    val totalEpisodes: Int
)
```

#### 3. Update UI

**MainFragment.kt:**
```kotlin
// Add source filter
val sourceSelector = Spinner()
sourceSelector.adapter = ArrayAdapter(
    context,
    android.R.layout.simple_spinner_item,
    listOf("All", "Farsiland", "Namakade")
)

// Load content from both sources
viewModel.loadContent(selectedSource)
```

#### 4. Video Player Integration

**NO CHANGES REQUIRED** - ExoPlayer already supports:
- Direct MP4 URLs ✅
- HLS streams (.m3u8) ✅
- HTTP/HTTPS protocols ✅

**PlaybackActivity.kt:**
```kotlin
// Just pass the URL - works for both sources
val videoUrl = episode.videoUrl // Could be farsiland or namakade URL
player.setMediaItem(MediaItem.fromUri(videoUrl))
```

---

## 6. IMPLEMENTATION PLAN

### Phase 1: HTML Scraper Foundation (2-3 days)

**Tasks:**
1. Create `NamakadeHtmlParser` class
2. Implement Jsoup for HTML parsing
3. Write unit tests for parsing logic
4. Test with sample pages

**Dependencies:**
```gradle
implementation 'org.jsoup:jsoup:1.17.1'
```

### Phase 2: Content Discovery (3-4 days)

**Tasks:**
1. Scrape series/movie category pages
2. Extract series details and episode lists
3. Parse video URLs from episode pages
4. Cache scraped data (24h TTL)

**Rate Limiting:**
- 500ms delay between requests
- Exponential backoff on errors

### Phase 3: Database Integration (2 days)

**Tasks:**
1. Add `source` field to all content tables
2. Migrate existing farsiland data
3. Update DAOs for multi-source queries
4. Create source management UI

### Phase 4: UI Integration (2-3 days)

**Tasks:**
1. Add source filter to home screen
2. Display source badge on content cards
3. Test navigation and playback
4. Handle errors gracefully

### Phase 5: Testing & Polish (2 days)

**Tasks:**
1. End-to-end testing
2. Performance optimization
3. Error handling
4. Analytics integration

**Total Estimated Time:** 11-14 days

---

## 7. URL CONSTRUCTION PATTERNS

### Series Episode URL Algorithm

```kotlin
fun buildEpisodeVideoUrl(seriesSlug: String, episodeNumber: Int): String {
    // Convert slug to title case without dashes
    val seriesName = seriesSlug.split("-")
        .joinToString("_") { it.capitalize() }

    val episodeStr = episodeNumber.toString().padStart(2, '0')

    return "https://media.negahestan.com/ipnx/media/series/episodes/${seriesName}_${episodeStr}.mp4"
}

// Examples:
// "algoritm" -> "Algoritm_01.mp4"
// "bamdaade-khomaar" -> "Bamdaade_Khomaar_01.mp4"
```

**Validation Required:**
- Some series may use different naming conventions
- Verify URL exists before playback (HEAD request)
- Implement fallback scraping if pattern fails

### Quality/Mirror Options

**Current Findings:**
- Only single quality MP4 found
- No multiple quality options observed
- No mirror CDN detected (unlike farsiland's d1/d2)

**Future Enhancement:**
- Monitor for quality variants (720p, 1080p)
- Check for alternative CDN domains

---

## 8. LIVE TV INTEGRATION

### Challenges:
1. **Session Tokens:** Live TV requires dynamic session tokens
2. **IP Restrictions:** GLWiz checks client IP address
3. **Token Expiry:** Tokens expire after unknown duration

### Implementation Options:

#### Option A: Scrape Token from Page (Recommended)
```kotlin
suspend fun getLiveTvUrl(channelName: String): String {
    val clientIP = getClientIP() // from api.gliptv.com/ip.aspx
    val pageHtml = httpClient.get("https://namakade.com/livetv/$channelName/$clientIP")
    return NamakadeHtmlParser.parseVideoSource(pageHtml)
}
```

#### Option B: Reverse Engineer Token Generation (Complex)
- Analyze JavaScript for token algorithm
- Risk: May violate ToS
- Not recommended

**Recommendation:** Use Option A with token caching (5-minute TTL)

---

## 9. CACHING STRATEGY

### Content Metadata Cache

| Data Type | Cache Duration | Storage |
|-----------|---------------|---------|
| Series List | 24 hours | Room DB |
| Episode List | 1 week | Room DB |
| Video URLs | 24 hours | Room DB |
| Thumbnails | Permanent | Disk Cache (Glide) |
| Live TV Tokens | 5 minutes | In-Memory |

### Cache Invalidation:
- Manual refresh option in UI
- Automatic refresh on cache miss
- Clear cache on app update

---

## 10. ERROR HANDLING

### Potential Issues:

1. **HTML Structure Changes**
   - Risk: High (server-side rendering can change)
   - Mitigation: Version detection, fallback parsers
   - Alert: Log parsing failures to analytics

2. **Video URL 404**
   - Risk: Medium (CDN issues, content removed)
   - Mitigation: Try alternative patterns, scrape from page
   - UX: Show error message, suggest refresh

3. **Rate Limiting**
   - Risk: Low (no observed limits)
   - Mitigation: 500ms delay, exponential backoff
   - Recovery: Retry with increased delay

4. **Network Errors**
   - Risk: Medium (user connectivity)
   - Mitigation: Retry logic (3 attempts)
   - UX: Offline mode with cached content

---

## 11. RISKS & MITIGATIONS

### Technical Risks:

| Risk | Impact | Probability | Mitigation |
|------|--------|-------------|------------|
| HTML parsing breaks | High | Medium | Version detection, fallback logic |
| CDN blocks Android | High | Low | Rotate user agents, mirror detection |
| Video URLs change pattern | High | Low | Scrape from page as fallback |
| Rate limiting imposed | Medium | Low | Implement delays, cache aggressively |
| Geo-blocking | Medium | Medium | User notification, VPN suggestion |

### Legal/ToS Risks:

⚠️ **IMPORTANT:**
- Scraping may violate website Terms of Service
- No explicit API or licensing agreement
- For **personal use only** behind firewalled network (per user's context)
- Commercial distribution not recommended

**Recommendation:** Contact site owner for API access or licensing

---

## 12. COMPARISON: FARSILAND vs NAMAKADE

| Feature | Farsiland.com | Namakade.com |
|---------|--------------|--------------|
| **API** | ✅ WordPress REST API | ❌ None (HTML only) |
| **Auth** | ❌ Not required | ❌ Not required |
| **Video Format** | MP4 | MP4 (On-demand), HLS (Live) |
| **CDN** | d1/d2.flnd.buzz | media.negahestan.com |
| **Mirrors** | ✅ d1, d2 | ❌ Single CDN |
| **Qualities** | 1080p, 720p, 480p | Single quality only |
| **Episode Lists** | HTML scraping | HTML scraping |
| **Live TV** | ❌ None | ✅ 30+ channels (HLS) |
| **Scraping Difficulty** | Easy (REST + HTML) | Medium (HTML only) |
| **Reliability** | High | Medium |

**Integration Complexity:**
- Farsiland: ⭐⭐ (Easy - REST API available)
- Namakade: ⭐⭐⭐ (Medium - HTML scraping only)

---

## 13. CODE EXAMPLES

### Scraping Series List

```kotlin
class NamakadeSeriesScraper(private val httpClient: HttpClient) {

    suspend fun getSeries Category(category: String = "best-serial"): List<Series> {
        val html = httpClient.get("https://namakade.com/$category").bodyAsText()
        val doc = Jsoup.parse(html)

        return doc.select("div.series-card").mapNotNull { element ->
            try {
                val link = element.selectFirst("a")?.attr("href") ?: return@mapNotNull null
                val slug = link.substringAfterLast("/")
                val title = element.selectFirst("div.title")?.text() ?: return@mapNotNull null
                val episodesText = element.selectFirst("div.episodes")?.text() ?: ""
                val episodeCount = episodesText.replace("Episodes: ", "").toIntOrNull() ?: 0
                val genre = element.selectFirst("div.genre")?.text()?.replace("Genre: ", "") ?: ""
                val thumbnail = element.selectFirst("img")?.attr("src") ?: ""

                Series(
                    id = "namakade_$slug",
                    title = title,
                    slug = slug,
                    source = "namakade",
                    thumbnail = thumbnail,
                    genre = genre,
                    totalEpisodes = episodeCount
                )
            } catch (e: Exception) {
                Log.e("NamakadeScraper", "Failed to parse series", e)
                null
            }
        }
    }
}
```

### Extracting Video URL

```kotlin
class NamakadeVideoUrlExtractor(private val httpClient: HttpClient) {

    suspend fun getEpisodeVideoUrl(episodeUrl: String): String? {
        val html = httpClient.get(episodeUrl).bodyAsText()
        val doc = Jsoup.parse(html)

        // Method 1: Parse video source tag
        val videoSource = doc.selectFirst("video source")?.attr("src")
        if (!videoSource.isNullOrBlank()) {
            return videoSource
        }

        // Method 2: Find in JavaScript
        val scriptText = doc.select("script").joinToString("\n") { it.html() }
        val mp4Regex = Regex("""https://media\.negahestan\.com/[^"']+\.mp4""")
        val match = mp4Regex.find(scriptText)

        return match?.value
    }

    // Validate URL before playback
    suspend fun validateVideoUrl(url: String): Boolean {
        return try {
            val response = httpClient.head(url)
            response.status.value == 200
        } catch (e: Exception) {
            false
        }
    }
}
```

### Building URLs Programmatically

```kotlin
object NamakadeUrlBuilder {

    private const val BASE_CDN = "https://media.negahestan.com/ipnx/media"

    fun buildEpisodeUrl(seriesName: String, episode: Int): String {
        val formattedName = formatSeriesName(seriesName)
        val episodeNum = episode.toString().padStart(2, '0')
        return "$BASE_CDN/series/episodes/${formattedName}_${episodeNum}.mp4"
    }

    fun buildMovieUrl(movieName: String): String {
        val formattedName = formatMovieName(movieName)
        return "$BASE_CDN/movies/${formattedName}.mp4"
    }

    fun buildThumbnailUrl(seriesName: String, isSmall: Boolean = true): String {
        val formattedName = formatSeriesName(seriesName)
        val suffix = if (isSmall) "_smallthumb" else "_bigthumb"
        return "$BASE_CDN/series/thumbs/${formattedName}${suffix}.jpg"
    }

    private fun formatSeriesName(slug: String): String {
        // Convert "algoritm" -> "Algoritm"
        // Convert "bamdaade-khomaar" -> "Bamdaade_Khomaar"
        return slug.split("-").joinToString("_") { word ->
            word.replaceFirstChar { it.uppercase() }
        }
    }

    private fun formatMovieName(slug: String): String {
        // Remove dashes and capitalize
        return slug.split("-").joinToString("") { word ->
            word.replaceFirstChar { it.uppercase() }
        }
    }
}
```

---

## 14. TESTING CHECKLIST

### Unit Tests
- [ ] HTML parser with sample pages
- [ ] URL builder algorithms
- [ ] Video URL validation
- [ ] Error handling scenarios

### Integration Tests
- [ ] Scrape series list from live site
- [ ] Extract episode list from series page
- [ ] Fetch video URL from episode page
- [ ] Validate video URLs (HEAD requests)

### E2E Tests
- [ ] Browse categories → select series → play episode
- [ ] Search content → select result → play
- [ ] Live TV selection → play stream
- [ ] Error states (offline, 404, parsing failure)

### Performance Tests
- [ ] Measure scraping latency
- [ ] Cache hit/miss ratios
- [ ] Memory usage during parsing
- [ ] Network bandwidth consumption

---

## 15. DEPLOYMENT RECOMMENDATIONS

### Phased Rollout

**Phase 1: Beta Testing**
- Enable namakade source for internal testing only
- Collect analytics on success/failure rates
- Monitor parsing errors
- Duration: 1-2 weeks

**Phase 2: Limited Release**
- Enable for 10% of users
- A/B test performance impact
- Gather user feedback
- Duration: 2-3 weeks

**Phase 3: Full Release**
- Enable for all users
- Monitor support tickets
- Iterate based on feedback

### Analytics Tracking

**Events to Track:**
```kotlin
analytics.logEvent("namakade_scrape_success", bundle {
    putString("content_type", "series")
    putLong("duration_ms", scrapeDuration)
})

analytics.logEvent("namakade_scrape_failure", bundle {
    putString("content_type", "series")
    putString("error_type", errorType)
    putString("url", scrapedUrl)
})

analytics.logEvent("namakade_video_play", bundle {
    putString("content_id", contentId)
    putString("video_url", videoUrl)
    putBoolean("from_cache", fromCache)
})
```

---

## 16. FUTURE ENHANCEMENTS

### Potential Improvements

1. **Predictive URL Generation**
   - Machine learning to predict URL patterns
   - Reduce need for HTML scraping
   - Faster content loading

2. **OCR for Subtitles**
   - Extract embedded subtitles
   - Support for hearing-impaired users

3. **Quality Detection**
   - Auto-detect available qualities
   - Dynamic quality switching

4. **Offline Download**
   - Allow downloading for offline viewing
   - Manage storage efficiently

5. **User Personalization**
   - Track watch history
   - Recommend similar content
   - Resume playback across devices

---

## 17. CONCLUSION

### Summary

Namakade.com is **fully compatible** with your existing Android TV app architecture. Integration requires:

1. **HTML scraping layer** (similar to farsiland episode scraping)
2. **URL pattern detection** for video files
3. **Multi-source content management** in UI and database
4. **No changes to video player** (ExoPlayer works as-is)

### Effort Estimate: 11-14 days

### Risk Level: Medium
- HTML parsing fragility
- No official API support
- Potential ToS concerns

### Recommendation: ✅ PROCEED

The integration is technically feasible and aligns with your existing codebase patterns. Use the phased rollout approach to minimize risk.

**Next Steps:**
1. Review this analysis with your team
2. Decide on implementation timeline
3. Set up development environment
4. Begin Phase 1 (HTML scraper foundation)

---

## 18. REFERENCES

### Discovered URLs

**Content Pages:**
- Home: https://namakade.com/home.html
- Series: https://namakade.com/best-serial
- Movies: https://namakade.com/best-movies
- Shows: https://namakade.com/show
- Live TV: https://namakade.com/livetvs
- Music Videos: https://namakade.com/musicvideos

**CDN Domains:**
- On-Demand: media.negahestan.com
- Live TV: glwizhstb46.glwiz.com
- Thumbnails: hd200.glwiz.com (for live TV channels)

**JavaScript Libraries:**
- Video.js (player)
- HLS.js (HLS support)
- Flowplayer (alternative player)
- jQuery carouFredSel (carousels)

### Contact Information
- Website Owner: Proud Holding LLC
- Privacy Policy: https://namakade.com/privacy-policy

---

**END OF ANALYSIS**
