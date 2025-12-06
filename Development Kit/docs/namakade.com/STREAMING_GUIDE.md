# Namakade.com Video Streaming Implementation Guide

## Overview

This guide details how to extract and play video streams from Namakade.com in a standalone Android application. The platform uses HLS (HTTP Live Streaming) protocol with Video.js player and has geo-blocking restrictions.

**⚠️ Legal Notice**: Ensure you have proper authorization from Proud Holding LLC before implementing streaming in your app.

---

## Table of Contents

1. [Streaming Technology Stack](#1-streaming-technology-stack)
2. [HLS Protocol Details](#2-hls-protocol-details)
3. [Video Player Implementation](#3-video-player-implementation)
4. [Stream URL Extraction](#4-stream-url-extraction)
5. [Android Implementation](#5-android-implementation)
6. [DRM & Protection](#6-drm--protection)
7. [Quality Selection](#7-quality-selection)
8. [Live TV Streaming](#8-live-tv-streaming)
9. [Troubleshooting](#9-troubleshooting)

---

## 1. Streaming Technology Stack

### 1.1 Server-Side

**Streaming Protocol**: HLS (HTTP Live Streaming)
**File Format**: MPEG-TS segments (.ts) packaged in m3u8 playlists
**Adaptive Bitrate**: Yes (multiple quality levels)
**CDN**: media.negahestan.com (primary) + possible streaming subdomain

### 1.2 Client-Side (Web)

**Primary Player**: Video.js
```html
<script src="/js/videojshttpstreaming/node_modules/video.js/dist/video.min.js"></script>
```

**HLS Support**:
- **Native HLS** (Safari, iOS): Direct browser support
- **HLS.js** (Chrome, Firefox, etc.): JavaScript implementation
  ```html
  <script src="https://cdn.jsdelivr.net/npm/hls.js@latest"></script>
  ```

**HTTP Streaming Plugin**:
```html
<script src="/js/videojshttpstreaming/node_modules/videojs/http-streaming/dist/videojs-http-streaming.js"></script>
```

### 1.3 Video Element Structure

```html
<video id="videoTag" class="video-js" controls>
  <source id="videoTagSrc" src="[STREAM_URL]" type="application/vnd.apple.mpegurl">
</video>
```

---

## 2. HLS Protocol Details

### 2.1 HLS Basics

HLS delivers video as a sequence of small file downloads:

1. **Master Playlist** (master.m3u8): Lists available quality levels
2. **Media Playlists** (variants): Contain segment URLs for each quality
3. **Media Segments** (.ts files): Actual video/audio chunks (typically 6-10 seconds each)

### 2.2 Master Playlist Example

```m3u8
#EXTM3U
#EXT-X-VERSION:3
#EXT-X-STREAM-INF:BANDWIDTH=800000,RESOLUTION=640x360,CODECS="avc1.42e01e,mp4a.40.2"
360p/playlist.m3u8
#EXT-X-STREAM-INF:BANDWIDTH=1400000,RESOLUTION=854x480,CODECS="avc1.42e01e,mp4a.40.2"
480p/playlist.m3u8
#EXT-X-STREAM-INF:BANDWIDTH=2800000,RESOLUTION=1280x720,CODECS="avc1.42e01e,mp4a.40.2"
720p/playlist.m3u8
#EXT-X-STREAM-INF:BANDWIDTH=5000000,RESOLUTION=1920x1080,CODECS="avc1.42e01e,mp4a.40.2"
1080p/playlist.m3u8
```

### 2.3 Media Playlist Example

```m3u8
#EXTM3U
#EXT-X-VERSION:3
#EXT-X-TARGETDURATION:10
#EXT-X-MEDIA-SEQUENCE:0
#EXTINF:10.0,
segment0.ts
#EXTINF:10.0,
segment1.ts
#EXTINF:10.0,
segment2.ts
#EXT-X-ENDLIST
```

### 2.4 Codecs Used

**Video Codec**: H.264 (AVC)
- Profile: Baseline, Main, or High
- Typical levels: 3.0-4.0

**Audio Codec**: AAC-LC
- Codec string: `mp4a.40.2`
- Sample rate: Typically 48 kHz or 44.1 kHz
- Channels: Stereo (2.0)

### 2.5 ABR Configuration (from ads.js)

```javascript
{
  abrEwmaFastLive: 0.1,    // Fast bandwidth estimation weight
  abrEwmaSlowLive: 0.2,    // Slow bandwidth estimation weight
  defaultAudioCodec: "mp4a.40.2"  // AAC-LC audio
}
```

**ABR Algorithm**: EWMA (Exponentially Weighted Moving Average)
- Monitors bandwidth and buffer levels
- Switches quality levels automatically
- Prevents buffering while maximizing quality

---

## 3. Video Player Implementation

### 3.1 Video.js Setup (Web Reference)

```javascript
// Initialize Video.js player
var player = videojs('videoTag', {
  controls: true,
  autoplay: false,
  preload: 'auto',
  fluid: true,  // Responsive sizing
  html5: {
    hls: {
      overrideNative: true,
      abrEwmaFastLive: 0.1,
      abrEwmaSlowLive: 0.2,
      defaultAudioCodec: "mp4a.40.2"
    }
  }
});

// Set stream source
player.src({
  src: 'https://stream-url/playlist.m3u8',
  type: 'application/vnd.apple.mpegurl'
});

// Load and play
player.load();
player.play();
```

### 3.2 HLS.js Implementation (Alternative)

```javascript
if (Hls.isSupported()) {
  var video = document.getElementById('videoTag');
  var hls = new Hls({
    abrEwmaFastVoD: 0.1,
    abrEwmaSlowVoD: 0.2,
    defaultAudioCodec: "mp4a.40.2"
  });

  hls.loadSource('https://stream-url/playlist.m3u8');
  hls.attachMedia(video);

  hls.on(Hls.Events.MANIFEST_PARSED, function() {
    video.play();
  });
}
```

### 3.3 Error Handling

**Plugin**: `reloadSourceOnError`
- Automatically retries on playback failure
- Reloads source if stream errors occur

```javascript
player.reloadSourceOnError({
  errorInterval: 10
});
```

---

## 4. Stream URL Extraction

### 4.1 Method 1: HTML Parsing

**Steps**:
1. Load episode/movie page HTML
2. Find `<video>` element with id="videoTag"
3. Extract `<source>` element with id="videoTagSrc"
4. Get `src` attribute (m3u8 URL)

**Example (Kotlin)**:
```kotlin
import org.jsoup.Jsoup

fun extractStreamUrl(pageHtml: String): String? {
    val doc = Jsoup.parse(pageHtml)
    val sourceElement = doc.select("#videoTagSrc").first()
    return sourceElement?.attr("src")
}
```

**Example (Python)**:
```python
from bs4 import BeautifulSoup

def extract_stream_url(page_html):
    soup = BeautifulSoup(page_html, 'html.parser')
    source = soup.find('source', {'id': 'videoTagSrc'})
    if source:
        return source.get('src')
    return None
```

### 4.2 Method 2: JavaScript Parsing

If stream URL is dynamically set via JavaScript:

**Search for**:
```javascript
player.src({ src: 'URL_HERE', type: '...' })
// or
document.getElementById('videoTagSrc').src = 'URL_HERE';
```

**Regex Pattern**:
```regex
player\.src\(\s*\{\s*src:\s*['"]([^'"]+\.m3u8[^'"]*)['"]/
```

### 4.3 Method 3: Network Interception

**Browser DevTools Approach**:
1. Open browser DevTools (F12)
2. Go to Network tab
3. Filter by "m3u8"
4. Load video page
5. Look for master.m3u8 or playlist.m3u8 request
6. Copy URL

**Programmatic Approach** (using Playwright/Selenium):
```kotlin
// Using Playwright
val requests = mutableListOf<String>()
page.route("**/*.m3u8") { route ->
    requests.add(route.request().url())
    route.continue_()
}
page.navigate("https://namakade.com/serieses/algoritm/episodes/algoritm")
// requests now contains m3u8 URLs
```

### 4.4 Expected URL Patterns

**Hypothetical patterns**:
```
https://media.negahestan.com/ipnx/streams/series/{series-id}/{episode-id}/playlist.m3u8

https://stream.namakade.com/vod/{content-id}/master.m3u8?token={auth-token}

https://cdn.namakade.com/hls/{content-id}/index.m3u8

https://media.negahestan.com/live/{channel-id}/playlist.m3u8
```

**URL Components**:
- Base URL: streaming CDN
- Content path: /vod/, /live/, /streams/
- Content ID: unique identifier
- Playlist file: master.m3u8, playlist.m3u8, index.m3u8
- Query params: ?token=..., ?auth=..., ?ip=...

### 4.5 Authentication Tokens in URLs

Stream URLs may contain authentication:
```
https://stream-url/playlist.m3u8?token=ABC123&expires=1234567890&signature=XYZ
```

**Token Types**:
- **Session token**: From web session
- **Temporary token**: Time-limited access
- **Signature**: HMAC-signed for verification
- **IP lock**: Tied to requesting IP address

---

## 5. Android Implementation

### 5.1 ExoPlayer Setup

**Add Dependencies** (build.gradle):
```gradle
dependencies {
    // ExoPlayer for HLS playback
    implementation "com.google.android.exoplayer:exoplayer:2.19.1"
    implementation "com.google.android.exoplayer:exoplayer-hls:2.19.1"
    implementation "com.google.android.exoplayer:exoplayer-ui:2.19.1"

    // OkHttp for network requests
    implementation "com.squareup.okhttp3:okhttp:4.12.0"

    // Jsoup for HTML parsing
    implementation "org.jsoup:jsoup:1.17.2"
}
```

### 5.2 Basic ExoPlayer Implementation

```kotlin
import android.net.Uri
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.source.hls.HlsMediaSource
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource
import com.google.android.exoplayer2.ui.PlayerView

class VideoPlayerActivity : AppCompatActivity() {

    private var player: ExoPlayer? = null
    private lateinit var playerView: PlayerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video_player)

        playerView = findViewById(R.id.player_view)
    }

    private fun initializePlayer(streamUrl: String) {
        // Create ExoPlayer instance
        player = ExoPlayer.Builder(this).build()

        // Attach player to view
        playerView.player = player

        // Create data source factory with custom headers
        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .setDefaultRequestProperties(mapOf(
                "Referer" to "https://namakade.com/",
                "Origin" to "https://namakade.com"
            ))

        // Create HLS media source
        val mediaSource = HlsMediaSource.Factory(dataSourceFactory)
            .createMediaSource(MediaItem.fromUri(Uri.parse(streamUrl)))

        // Set media source and prepare
        player?.setMediaSource(mediaSource)
        player?.prepare()

        // Auto-play
        player?.playWhenReady = true
    }

    override fun onStop() {
        super.onStop()
        player?.release()
        player = null
    }
}
```

### 5.3 Advanced ExoPlayer with ABR

```kotlin
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter

private fun initializePlayerWithABR(streamUrl: String) {
    // Bandwidth meter for adaptive streaming
    val bandwidthMeter = DefaultBandwidthMeter.Builder(this).build()

    // Track selector with adaptive parameters
    val trackSelector = DefaultTrackSelector(this).apply {
        setParameters(
            buildUponParameters()
                .setMaxVideoSizeSd()  // Limit to SD on mobile data
                .setForceHighestSupportedBitrate(false)
                .build()
        )
    }

    // Create player with track selector
    player = ExoPlayer.Builder(this)
        .setTrackSelector(trackSelector)
        .setBandwidthMeter(bandwidthMeter)
        .build()

    playerView.player = player

    // ... rest of setup
}
```

### 5.4 Complete Streaming Flow

```kotlin
class NamakadeStreamPlayer(private val context: Context) {

    private val httpClient = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    suspend fun playEpisode(seriesId: String, episodeId: String, token: String?) {
        // Step 1: Get episode page HTML
        val pageUrl = "https://namakade.com/serieses/$seriesId/episodes/$episodeId" +
                     if (token != null) "?$token" else ""

        val pageHtml = fetchPageHtml(pageUrl) ?: run {
            Log.e("StreamPlayer", "Failed to fetch page")
            return
        }

        // Step 2: Extract stream URL
        val streamUrl = extractStreamUrl(pageHtml) ?: run {
            Log.e("StreamPlayer", "Failed to extract stream URL")
            return
        }

        Log.d("StreamPlayer", "Stream URL: $streamUrl")

        // Step 3: Initialize player
        initializePlayer(streamUrl)
    }

    private suspend fun fetchPageHtml(url: String): String? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0")
            .build()

        try {
            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    response.body?.string()
                } else {
                    Log.e("StreamPlayer", "HTTP ${response.code}")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e("StreamPlayer", "Error fetching page", e)
            null
        }
    }

    private fun extractStreamUrl(html: String): String? {
        val doc = Jsoup.parse(html)
        return doc.select("#videoTagSrc").first()?.attr("src")
    }
}
```

### 5.5 Handling Authentication

```kotlin
class AuthenticatedStreamPlayer(context: Context) {

    private val cookieJar = PersistentCookieJar()

    private val httpClient = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .addHeader("User-Agent", "Mozilla/5.0")
                .addHeader("Referer", "https://namakade.com/")
                .build()
            chain.proceed(request)
        }
        .build()

    suspend fun login(email: String, password: String): Boolean {
        // Implement login logic
        // Store session cookies in cookieJar
        // Return true if successful
    }

    // Use the same httpClient for all requests
    // Cookies will be automatically included
}
```

### 5.6 Layout XML

```xml
<!-- activity_video_player.xml -->
<androidx.constraintlayout.widget.ConstraintLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent">

    <com.google.android.exoplayer2.ui.PlayerView
        android:id="@+id/player_view"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        app:show_buffering="when_playing"
        app:resize_mode="fit"
        app:use_controller="true"
        app:controller_layout_id="@layout/exo_player_control_view" />

</androidx.constraintlayout.widget.ConstraintLayout>
```

---

## 6. DRM & Protection

### 6.1 Current Protection Methods

**Observed**:
✅ Geo-blocking (IP-based)
✅ Session tokens in URLs
✅ Referer checking
⚠️ HLS encryption (possible but not confirmed)

**Not Observed**:
❌ Widevine DRM
❌ PlayReady DRM
❌ FairPlay DRM

### 6.2 HLS AES-128 Encryption

If streams are encrypted with AES-128:

**Master Playlist**:
```m3u8
#EXT-X-KEY:METHOD=AES-128,URI="https://key-server/key.bin",IV=0x123456789ABCDEF
```

**ExoPlayer Handling**:
- ExoPlayer supports AES-128 automatically
- Key fetching is handled by player
- May require authentication for key URL

### 6.3 Bypassing Geo-blocks (Authorized Use)

**VPN Approach**:
```kotlin
// Note: User must have VPN installed and configured
// Your app can detect VPN connection

fun isVpnConnected(): Boolean {
    val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val activeNetwork = connectivityManager.activeNetwork ?: return false
    val caps = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
    return caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
}
```

**Proxy Approach**:
```kotlin
// Configure OkHttp with proxy
val proxy = Proxy(Proxy.Type.HTTP, InetSocketAddress("proxy-server.com", 8080))
val client = OkHttpClient.Builder()
    .proxy(proxy)
    .build()
```

**IP Whitelisting**:
- Contact Proud Holding LLC
- Request IP whitelist for your users
- Provide app authentication

---

## 7. Quality Selection

### 7.1 Automatic Quality (Default)

ExoPlayer handles ABR automatically:
```kotlin
val trackSelector = DefaultTrackSelector(context)
player = ExoPlayer.Builder(context)
    .setTrackSelector(trackSelector)
    .build()
```

### 7.2 Manual Quality Selection

**Get Available Tracks**:
```kotlin
player.addListener(object : Player.Listener {
    override fun onTracksChanged(tracks: Tracks) {
        val videoTracks = tracks.groups
            .filter { it.type == C.TRACK_TYPE_VIDEO }

        videoTracks.forEach { group ->
            for (i in 0 until group.length) {
                val format = group.getTrackFormat(i)
                Log.d("Quality", "${format.width}x${format.height} @ ${format.bitrate}bps")
            }
        }
    }
})
```

**Force Quality Level**:
```kotlin
fun setQuality(width: Int, height: Int) {
    val trackSelector = player.trackSelector as? DefaultTrackSelector ?: return

    trackSelector.setParameters(
        trackSelector.buildUponParameters()
            .setMaxVideoSize(width, height)
            .build()
    )
}

// Usage
setQuality(1280, 720)  // Force 720p max
setQuality(1920, 1080) // Force 1080p max
```

### 7.3 Quality Selection UI

```kotlin
class QualityDialog : DialogFragment() {

    data class QualityOption(val label: String, val width: Int, val height: Int)

    val qualities = listOf(
        QualityOption("Auto", 0, 0),
        QualityOption("360p", 640, 360),
        QualityOption("480p", 854, 480),
        QualityOption("720p", 1280, 720),
        QualityOption("1080p", 1920, 1080)
    )

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val items = qualities.map { it.label }.toTypedArray()

        return AlertDialog.Builder(requireContext())
            .setTitle("Select Quality")
            .setItems(items) { _, which ->
                val quality = qualities[which]
                onQualitySelected(quality)
            }
            .create()
    }

    private fun onQualitySelected(quality: QualityOption) {
        // Callback to activity/fragment
    }
}
```

---

## 8. Live TV Streaming

### 8.1 Live TV URLs

**Pattern**:
```
https://namakade.com/livetv/{channel-name}/{ip-address}
```

**Examples**:
```
https://namakade.com/livetv/IRIB%20Channel%203/142.180.25.26
https://namakade.com/livetv/GEM%20TV/142.180.25.26
```

### 8.2 GLWiz Integration

**Provider**: GLWiz (hd200.glwiz.com)

**Channel List API** (Hypothetical):
```
GET https://hd200.glwiz.com/api/channels
```

**Stream URL Pattern**:
```
https://hd200.glwiz.com/live/{channel-id}/playlist.m3u8
```

### 8.3 Live Streaming with ExoPlayer

```kotlin
fun playLiveTV(channelStreamUrl: String) {
    val dataSourceFactory = DefaultHttpDataSource.Factory()
        .setUserAgent("Mozilla/5.0")

    val mediaSource = HlsMediaSource.Factory(dataSourceFactory)
        .setAllowChunklessPreparation(true)  // For live streams
        .createMediaSource(MediaItem.fromUri(channelStreamUrl))

    player?.setMediaSource(mediaSource)
    player?.prepare()
    player?.playWhenReady = true
}
```

### 8.4 Live Stream Considerations

**Differences from VOD**:
- ✅ Continuous updating playlist
- ✅ No end-of-list marker (#EXT-X-ENDLIST)
- ✅ Player polls for new segments
- ⚠️ Higher latency (20-60 seconds typical)
- ⚠️ Cannot seek backward beyond buffer

**Live Playlist Example**:
```m3u8
#EXTM3U
#EXT-X-VERSION:3
#EXT-X-TARGETDURATION:10
#EXT-X-MEDIA-SEQUENCE:12345
#EXTINF:10.0,
segment12345.ts
#EXTINF:10.0,
segment12346.ts
#EXTINF:10.0,
segment12347.ts
# Playlist continues to update
```

---

## 9. Troubleshooting

### 9.1 Common Issues

#### Issue: "Unable to connect to server"

**Causes**:
- Geo-blocking
- Invalid/expired token
- Server down

**Solutions**:
```kotlin
// Check IP first
val clientIP = api.getClientIP()
Log.d("Debug", "Your IP: $clientIP")

// Verify URL accessibility
val testUrl = "https://media.negahestan.com/test.m3u8"
// Try accessing with cURL or browser first
```

#### Issue: "Stream not playing"

**Causes**:
- CORS restrictions (web only)
- Missing authentication headers
- Wrong MIME type

**Solutions**:
```kotlin
// Add headers
val dataSourceFactory = DefaultHttpDataSource.Factory()
    .setDefaultRequestProperties(mapOf(
        "User-Agent" to "Mozilla/5.0",
        "Referer" to "https://namakade.com/",
        "Origin" to "https://namakade.com"
    ))
```

#### Issue: "403 Forbidden"

**Causes**:
- IP not whitelisted
- Missing session cookies
- Invalid token

**Solutions**:
```kotlin
// Use VPN
// Or extract and include session cookies
cookieJar.saveFromResponse(url, cookies)
```

#### Issue: "Buffering / Low Quality"

**Causes**:
- Slow internet connection
- Far from CDN server
- ABR selecting low quality

**Solutions**:
```kotlin
// Set minimum bitrate
trackSelector.setParameters(
    trackSelector.buildUponParameters()
        .setMinVideoBitrate(1400000)  // 1.4 Mbps minimum
        .build()
)

// Increase buffer
val loadControl = DefaultLoadControl.Builder()
    .setBufferDurationsMs(
        30000,  // Min buffer: 30s
        60000,  // Max buffer: 60s
        2500,   // Playback buffer: 2.5s
        5000    // Playback rebuffer: 5s
    )
    .build()

player = ExoPlayer.Builder(context)
    .setLoadControl(loadControl)
    .build()
```

### 9.2 Debugging Tools

**Logcat Filtering**:
```bash
adb logcat | grep -E "ExoPlayer|StreamPlayer|HLS"
```

**Network Capture**:
```kotlin
// Add logging interceptor to OkHttp
val loggingInterceptor = HttpLoggingInterceptor().apply {
    level = HttpLoggingInterceptor.Level.HEADERS
}

val client = OkHttpClient.Builder()
    .addInterceptor(loggingInterceptor)
    .build()
```

**Test Stream URLs**:
```kotlin
// Apple test stream (always works)
val testUrl = "https://devstreaming-cdn.apple.com/videos/streaming/examples/bipbop_4x3/bipbop_4x3_variant.m3u8"

// Test with this first to verify player setup
initializePlayer(testUrl)
```

### 9.3 Error Handling

```kotlin
player.addListener(object : Player.Listener {
    override fun onPlayerError(error: PlaybackException) {
        when (error.errorCode) {
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED -> {
                Log.e("Player", "Network error - check internet connection")
                showError("Network error. Please check your connection.")
            }
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> {
                Log.e("Player", "Connection timeout")
                showError("Connection timeout. Try again.")
            }
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> {
                Log.e("Player", "HTTP error - possibly geo-blocked")
                showError("Unable to access content. Check geo-restrictions.")
            }
            else -> {
                Log.e("Player", "Playback error: ${error.message}")
                showError("Playback error occurred.")
            }
        }
    }

    override fun onPlaybackStateChanged(state: Int) {
        when (state) {
            Player.STATE_BUFFERING -> {
                progressBar.visibility = View.VISIBLE
            }
            Player.STATE_READY -> {
                progressBar.visibility = View.GONE
            }
            Player.STATE_ENDED -> {
                // Video finished
                onVideoEnded()
            }
        }
    }
})
```

### 9.4 Performance Optimization

```kotlin
// Release player when not visible
override fun onStop() {
    super.onStop()
    player?.stop()
    player?.release()
    player = null
}

// Resume playback position
private var playbackPosition = 0L

override fun onPause() {
    super.onPause()
    playbackPosition = player?.currentPosition ?: 0L
}

override fun onResume() {
    super.onResume()
    initializePlayer(streamUrl)
    player?.seekTo(playbackPosition)
}
```

---

## 10. Best Practices

### 10.1 User Experience

✅ **Show loading indicator** during buffering
✅ **Display error messages** clearly
✅ **Allow quality selection** for data saving
✅ **Remember playback position** for resume
✅ **Implement picture-in-picture** for multitasking
✅ **Add playback speed control** (0.5x, 1x, 1.5x, 2x)
✅ **Support subtitles** if available in HLS

### 10.2 Network Efficiency

✅ **Cache thumbnails** and metadata
✅ **Preload next episode** in background
✅ **Use lower quality** on cellular data by default
✅ **Implement download for offline** (if legally allowed)

### 10.3 Legal Compliance

⚠️ **Get permission** from Proud Holding LLC
⚠️ **Respect geo-blocking** or get authorization to bypass
⚠️ **Include proper attribution** for content
⚠️ **Implement DRM** if required by content owner
⚠️ **Handle copyright** notices properly

---

## 11. Summary

### What Works
✅ HLS streaming protocol
✅ ExoPlayer supports HLS natively
✅ Adaptive bitrate streaming
✅ Live TV and VOD content
✅ Multiple quality levels

### What's Challenging
⚠️ Geo-blocking (requires VPN or whitelist)
⚠️ Session token management
⚠️ No official API (requires scraping)
⚠️ Possible AES encryption on streams
⚠️ Authentication for premium content

### Required Components
1. **OkHttp** - HTTP client for page/API requests
2. **Jsoup** - HTML parsing for stream URL extraction
3. **ExoPlayer** - HLS video playback
4. **Glide/Coil** - Image loading for thumbnails
5. **VPN or Proxy** - Bypass geo-restrictions (with authorization)

### Estimated Difficulty
**Overall**: Medium-High
**Streaming Implementation**: Medium (ExoPlayer handles complexity)
**URL Extraction**: Medium (requires web scraping)
**Geo-blocking**: High (needs VPN or official authorization)

---

**Document Version**: 1.0
**Last Updated**: 2025-10-29
**Platform**: Android
**Min SDK**: 21 (Lollipop)
**Target SDK**: 34
