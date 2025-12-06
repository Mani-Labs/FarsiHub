# Namakade.com API Endpoints Documentation

## Overview

This document details the API endpoints discovered and inferred from analyzing Namakade.com. Note that this platform does not provide official public API documentation, so these endpoints are documented through reverse engineering.

**⚠️ IMPORTANT**: Using these APIs without authorization may violate the terms of service. This documentation is for educational and authorized development purposes only.

---

## Base URLs

```
Website: https://namakade.com
Media CDN: https://media.negahestan.com
API: https://api.gliptv.com
Live TV CDN: https://hd200.glwiz.com
```

---

## 1. IP Detection API

### Get Client IP Address

**Endpoint**: `GET /ip.aspx`

**Base URL**: `https://api.gliptv.com`

**Full URL**: `https://api.gliptv.com/ip.aspx`

**Authentication**: None required

**Rate Limiting**: Unknown

**Request**:
```http
GET /ip.aspx HTTP/1.1
Host: api.gliptv.com
Accept: application/json
User-Agent: Mozilla/5.0
```

**Response** (200 OK):
```json
{
  "clienIP": "142.180.25.26"
}
```

**Response Fields**:
| Field | Type | Description |
|-------|------|-------------|
| `clienIP` | string | Client's IPv4 address (Note: typo in field name) |

**Usage**:
- Geo-location detection
- Access control decisions
- Regional content filtering

**Example (cURL)**:
```bash
curl -X GET "https://api.gliptv.com/ip.aspx" \
  -H "Accept: application/json"
```

**Example (JavaScript)**:
```javascript
fetch('https://api.gliptv.com/ip.aspx')
  .then(response => response.json())
  .then(data => console.log('Client IP:', data.clienIP));
```

**Example (Java/Android)**:
```java
OkHttpClient client = new OkHttpClient();
Request request = new Request.Builder()
    .url("https://api.gliptv.com/ip.aspx")
    .build();

client.newCall(request).enqueue(new Callback() {
    @Override
    public void onResponse(Call call, Response response) throws IOException {
        String json = response.body().string();
        // Parse JSON to get clienIP field
    }
});
```

---

## 2. Content Navigation (Web-based)

### Homepage

**Endpoint**: `GET /index.html`

**Full URL**: `https://namakade.com/index.html?{token}`

**Parameters**:
| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `token` | string | No | Session token (20 chars alphanumeric) |

**Response**: HTML page with featured content

**Example**:
```
https://namakade.com/index.html?nPVr2VBSPvvCSbKExssi
```

### TV Series Listing

**Endpoint**: `GET /best-serial`

**Full URL**: `https://namakade.com/best-serial`

**Response**: HTML page with series grid

**Content**: Iranian and Turkish TV series listings

### TV Series Details

**Endpoint**: `GET /serieses/{series-slug}`

**Full URL**: `https://namakade.com/serieses/{series-slug}?{token}`

**Parameters**:
| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `series-slug` | string | Yes | URL-friendly series name (e.g., "algoritm") |
| `token` | string | No | Session token |

**Example**:
```
https://namakade.com/serieses/algoritm?dMiwyNI1uooCszRimSXF
```

**Response**: HTML with season/episode listing

### Episode Page

**Endpoint**: `GET /serieses/{series-slug}/episodes/{episode-slug}`

**Full URL**: `https://namakade.com/serieses/{series-slug}/episodes/{episode-slug}?{token}`

**Parameters**:
| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `series-slug` | string | Yes | Series identifier |
| `episode-slug` | string | Yes | Episode identifier |
| `token` | string | Yes | Session/auth token |

**Example**:
```
https://namakade.com/serieses/algoritm/episodes/algoritm?vaJn2mfyyTOeTIqwSKdL
```

**Response**: HTML with video player and episode metadata

**Access Control**:
- May return 404 or error page if geo-blocked
- Token appears to be required for video access

### Movies Listing

**Endpoint**: `GET /best-movies`

**Full URL**: `https://namakade.com/best-movies`

**Response**: HTML page with movie grid

### Movie Details

**Endpoint**: `GET /best-1-movies/{genre}/{movie-slug}`

**Full URL**: `https://namakade.com/best-1-movies/{genre}/{movie-slug}`

**Parameters**:
| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `genre` | string | Yes | Movie genre category |
| `movie-slug` | string | Yes | URL-friendly movie name |

**Genre Values** (observed):
- `drama`
- `comedy`
- `action`
- `comedy-foreign`
- `drama-foreign`
- `action-drama-foreign-thriller`
- `drama-foreign-historic`
- `animation-kidschannels`
- `classic`

**Examples**:
```
https://namakade.com/best-1-movies/drama/pir-pesar
https://namakade.com/best-1-movies/comedy-foreign/caramelo
https://namakade.com/best-1-movies/classic/bonbast
```

### Shows Listing

**Endpoint**: `GET /show`

**Full URL**: `https://namakade.com/show`

**Response**: HTML page with Iranian talk shows and programs

### Show Details

**Endpoint**: `GET /shows/{show-slug}`

**Full URL**: `https://namakade.com/shows/{show-slug}`

**Example**:
```
https://namakade.com/shows/aknoon
https://namakade.com/shows/carnaval
```

### Music Videos Listing

**Endpoint**: `GET /musicvideos`

**Full URL**: `https://namakade.com/musicvideos`

### Music Video Details

**Endpoint**: `GET /musicvideos/{artist}/{video-slug}`

**Full URL**: `https://namakade.com/musicvideos/{artist}/{video-slug}`

**Parameters**:
| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `artist` | string | Yes | Artist name (URL-encoded, + for spaces) |
| `video-slug` | string | Yes | Video identifier |

**Examples**:
```
https://namakade.com/musicvideos/alireza+ghandchi/man-ja-mandam
https://namakade.com/musicvideos/minoo/mahe-aseman
https://namakade.com/musicvideos/ebi+martik/nowruz-baraye-iran
```

### Live TV Listing

**Endpoint**: `GET /livetvs`

**Full URL**: `https://namakade.com/livetvs`

**Response**: HTML with live channel grid

### Live TV Channel

**Endpoint**: `GET /livetv/{channel-name}/{ip-address}`

**Full URL**: `https://namakade.com/livetv/{channel-name}/{ip-address}`

**Parameters**:
| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `channel-name` | string | Yes | URL-encoded channel name |
| `ip-address` | string | Yes | IP address (possibly for routing/CDN) |

**Examples**:
```
https://namakade.com/livetv/IRIB%20Channel%203/142.180.25.26
https://namakade.com/livetv/GEM%20TV/142.180.25.26
https://namakade.com/livetv/BBC%20Persian/142.180.25.26
```

### Cartoons Listing

**Endpoint**: `GET /cartoon`

**Full URL**: `https://namakade.com/cartoon`

**Response**: HTML with cartoon series listings

---

## 3. Media CDN Endpoints

### Series Thumbnail

**Endpoint**: `GET /ipnx/media/series/thumbs/{series-name}_smallthumb.jpg`

**Base URL**: `https://media.negahestan.com`

**Example**:
```
https://media.negahestan.com/ipnx/media/series/thumbs/Algoritm_smallthumb.jpg
https://media.negahestan.com/ipnx/media/series/thumbs/Momayezi_smallthumb.jpg
```

### Series Banner

**Endpoint**: `GET /ipnx/media/banners/{series-name}-Poster.jpg`

**Base URL**: `https://media.negahestan.com`

**Variants**:
- Large: `{name}-Poster.jpg`
- Small: `{name}-Poster-S.jpg`

**Examples**:
```
https://media.negahestan.com/ipnx/media/banners/Aknoon-Poster.jpg
https://media.negahestan.com/ipnx/media/banners/Aknoon-Poster-S.jpg
```

### Episode Thumbnail

**Endpoint**: `GET /ipnx/media/series/episodes/thumbs/{series-name}_{episode-number}_thumb.jpg`

**Base URL**: `https://media.negahestan.com`

**Example**:
```
https://media.negahestan.com/ipnx/media/series/episodes/thumbs/Algoritm_01_thumb.jpg
https://media.negahestan.com/ipnx/media/series/episodes/thumbs/Algoritm_14_thumb.jpg
```

### Movie Thumbnail

**Endpoint**: `GET /ipnx/media/movies/thumbs/{movie-name}_thumb.jpg`

**Base URL**: `https://media.negahestan.com`

**Example**:
```
https://media.negahestan.com/ipnx/media/movies/thumbs/Pirpesar_thumb.jpg
https://media.negahestan.com/ipnx/media/movies/thumbs/Caramelo_thumb.jpg
```

### Show Thumbnail

**Endpoint**: `GET /ipnx/media/shows/thumbs/{show-name}_smallthumb.jpg`

**Base URL**: `https://media.negahestan.com`

**Example**:
```
https://media.negahestan.com/ipnx/media/shows/thumbs/Aknoon_smallthumb.jpg
https://media.negahestan.com/ipnx/media/shows/thumbs/Carnaval_smallthumb.jpg
```

### Music Video Thumbnail

**Endpoint**: `GET /ipnx/media/music_videos/thumbs/{video-name}_thumb.jpg`

**Base URL**: `https://media.negahestan.com`

**Example**:
```
https://media.negahestan.com/ipnx/media/music_videos/thumbs/Man_Ja_mandam_thumb.jpg
https://media.negahestan.com/ipnx/media/music_videos/thumbs/Minoo_Mahe_Aseman_thumb.jpg
```

### Live TV Channel Logo

**Endpoint**: `GET /menu/epg/imagesNew/l{channel-id}.png`

**Base URL**: `https://hd200.glwiz.com`

**Example**:
```
https://hd200.glwiz.com/menu/epg/imagesNew/l307356.png
https://hd200.glwiz.com/menu/epg/imagesNew/l300410.png
```

**Note**: Channel IDs are 6-digit numbers

---

## 4. Video Streaming (Inferred)

### HLS Manifest (m3u8)

**Endpoint Pattern** (Hypothetical):
```
GET /ipnx/streams/{content-type}/{content-id}/playlist.m3u8
GET /ipnx/streams/{content-type}/{content-id}/master.m3u8
```

**Base URL**: `https://media.negahestan.com` or dedicated streaming domain

**Content Types**:
- `series`
- `movies`
- `shows`
- `musicvideos`
- `livetv`

**Response**: HLS playlist (m3u8 format)

**Example HLS Master Playlist**:
```m3u8
#EXTM3U
#EXT-X-STREAM-INF:BANDWIDTH=800000,RESOLUTION=640x360
360p/playlist.m3u8
#EXT-X-STREAM-INF:BANDWIDTH=1400000,RESOLUTION=842x480
480p/playlist.m3u8
#EXT-X-STREAM-INF:BANDWIDTH=2800000,RESOLUTION=1280x720
720p/playlist.m3u8
```

**Authentication**:
- Likely requires token in URL or headers
- May use cookie-based authentication
- IP-based access control

**Note**:
- Actual stream URLs not directly accessible without valid session
- Geo-blocking applies
- URLs may be time-limited and signed

---

## 5. Search API (Inferred)

### Search Content

**Endpoint** (Hypothetical):
```
GET /api/search
```

**Parameters**:
| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `q` | string | Yes | Search query |
| `type` | string | No | Content type filter (series/movies/shows/all) |
| `page` | integer | No | Page number for pagination |
| `limit` | integer | No | Results per page |

**Expected Response** (JSON):
```json
{
  "results": [
    {
      "id": "algoritm",
      "title": "Algoritm",
      "type": "series",
      "thumbnail": "https://media.negahestan.com/ipnx/media/series/thumbs/Algoritm_smallthumb.jpg",
      "episodes": 14,
      "genre": ["اکشن", "درام"]
    }
  ],
  "total": 1,
  "page": 1,
  "pages": 1
}
```

**Note**: Actual implementation may differ; this is inferred from typical patterns

---

## 6. Metadata APIs (Inferred)

### Get Series Metadata

**Endpoint** (Hypothetical):
```
GET /api/series/{series-id}
```

**Expected Response**:
```json
{
  "id": "algoritm",
  "title": "Algoritm",
  "titleFarsi": "الگوریتم",
  "description": "...",
  "genre": ["اکشن", "درام"],
  "episodes": 14,
  "seasons": 1,
  "thumbnail": "https://media.negahestan.com/ipnx/media/series/thumbs/Algoritm_smallthumb.jpg",
  "banner": "https://media.negahestan.com/ipnx/media/banners/Algoritm-Poster.jpg",
  "rating": 4.5,
  "year": 2025
}
```

### Get Episode List

**Endpoint** (Hypothetical):
```
GET /api/series/{series-id}/episodes
```

**Expected Response**:
```json
{
  "series": "algoritm",
  "episodes": [
    {
      "number": 1,
      "title": "Episode 1",
      "slug": "algoritm",
      "thumbnail": "https://media.negahestan.com/ipnx/media/series/episodes/thumbs/Algoritm_01_thumb.jpg",
      "duration": 2700,
      "streamUrl": "..."
    },
    {
      "number": 2,
      "title": "Episode 2",
      "slug": "algoritm-",
      "thumbnail": "https://media.negahestan.com/ipnx/media/series/episodes/thumbs/Algoritm_02_thumb.jpg",
      "duration": 2700,
      "streamUrl": "..."
    }
  ]
}
```

### Get Movie Metadata

**Endpoint** (Hypothetical):
```
GET /api/movies/{movie-id}
```

**Expected Response**:
```json
{
  "id": "pir-pesar",
  "title": "Pir Pesar",
  "director": "اکتای براهنی",
  "genre": ["درام", "اجتماعی"],
  "thumbnail": "https://media.negahestan.com/ipnx/media/movies/thumbs/Pirpesar_thumb.jpg",
  "duration": 5400,
  "year": 2025,
  "rating": 4.2,
  "streamUrl": "..."
}
```

### Get Live TV Channels

**Endpoint** (Hypothetical):
```
GET /api/livetv/channels
```

**Expected Response**:
```json
{
  "channels": [
    {
      "id": "irib-3",
      "name": "IRIB Channel 3",
      "logo": "https://hd200.glwiz.com/menu/epg/imagesNew/l300410.png",
      "streamUrl": "...",
      "category": "National"
    },
    {
      "id": "gem-tv",
      "name": "GEM TV",
      "logo": "https://hd200.glwiz.com/menu/epg/imagesNew/l300512.png",
      "streamUrl": "...",
      "category": "Entertainment"
    }
  ]
}
```

---

## 7. Authentication (Web-based)

### Login

**Endpoint** (Inferred):
```
POST /login
```

**Request Body**:
```json
{
  "email": "user@example.com",
  "password": "password123"
}
```

**Expected Response**:
```json
{
  "success": true,
  "token": "session_token_here",
  "user": {
    "name": "User Name",
    "email": "user@example.com",
    "country": "US"
  }
}
```

### Logout

**Endpoint** (Inferred):
```
POST /logout
```

**Response**:
```json
{
  "success": true,
  "message": "Logged out successfully"
}
```

---

## 8. Rating System

### Submit Rating

**Endpoint** (Inferred from jRating.jquery.js):
```
POST /rate
```

**Request Body**:
```json
{
  "contentType": "series",
  "contentId": "algoritm",
  "rating": 5
}
```

**Response**:
```json
{
  "success": true,
  "averageRating": 4.5,
  "totalRatings": 1234
}
```

---

## 9. Request Headers

### Recommended Headers for API Calls

```http
User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36
Accept: application/json, text/html
Accept-Language: fa-IR,fa;q=0.9,en-US;q=0.8,en;q=0.7
Accept-Encoding: gzip, deflate, br
Referer: https://namakade.com/
Origin: https://namakade.com
```

### For Video Streams (HLS)

```http
User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64)
Accept: */*
Origin: https://namakade.com
Referer: https://namakade.com/
```

---

## 10. Error Responses

### Common HTTP Status Codes

| Status Code | Meaning | Description |
|-------------|---------|-------------|
| 200 | OK | Request successful |
| 302 | Redirect | Redirecting to canonical URL or login |
| 404 | Not Found | Content not found or access denied |
| 403 | Forbidden | Geo-blocked or unauthorized |
| 500 | Server Error | Internal server error |

### Error Page (404)

**Response**: HTML error page
**Image**: `https://namakade.com/images/404_done.jpg`

---

## 11. Rate Limiting

**Status**: Unknown - not documented

**Recommendations**:
- Implement exponential backoff
- Respect HTTP 429 (Too Many Requests) if returned
- Add delays between requests (1-2 seconds)
- Cache responses when possible
- Use HEAD requests to check resource availability before GET

---

## 12. CORS (Cross-Origin Resource Sharing)

**Status**: Likely restricted for web browsers

**For Mobile Apps**:
- CORS restrictions don't apply to native apps
- Use native HTTP clients (OkHttp, Retrofit)
- Set appropriate User-Agent header

---

## 13. Session Management

### Token Format

**Length**: 20 characters
**Character Set**: Alphanumeric (a-z, A-Z, 0-9)
**Example**: `nPVr2VBSPvvCSbKExssi`

### Token Location

**URL Query Parameter**: Most common
```
https://namakade.com/serieses/algoritm?dMiwyNI1uooCszRimSXF
```

**Possible Cookie**: `session_id`, `auth_token` (not confirmed)

### Token Lifetime

**Status**: Unknown
**Behavior**: Appears to change per page load
**Recommendation**:
- Extract token from page HTML or initial API response
- Refresh token periodically
- Handle token expiration gracefully

---

## 14. Testing & Development

### Testing Tools

**cURL**:
```bash
# Test IP detection
curl https://api.gliptv.com/ip.aspx

# Test series page
curl "https://namakade.com/serieses/algoritm?dMiwyNI1uooCszRimSXF"

# Download thumbnail
curl -O "https://media.negahestan.com/ipnx/media/series/thumbs/Algoritm_smallthumb.jpg"
```

**Postman Collection**:
```json
{
  "info": {
    "name": "Namakade.com API",
    "schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json"
  },
  "item": [
    {
      "name": "Get Client IP",
      "request": {
        "method": "GET",
        "url": "https://api.gliptv.com/ip.aspx"
      }
    }
  ]
}
```

---

## 15. SDK Recommendations

### For Android Development

**HTTP Client**:
```kotlin
// Retrofit setup
val retrofit = Retrofit.Builder()
    .baseUrl("https://namakade.com/")
    .addConverterFactory(GsonConverterFactory.create())
    .client(okHttpClient)
    .build()

interface NamakadeApi {
    @GET("serieses/{seriesId}")
    suspend fun getSeriesDetails(
        @Path("seriesId") seriesId: String,
        @Query("token") token: String?
    ): Response<String>  // HTML response
}
```

**Image Loading (Glide)**:
```kotlin
Glide.with(context)
    .load("https://media.negahestan.com/ipnx/media/series/thumbs/Algoritm_smallthumb.jpg")
    .into(imageView)
```

**Video Player (ExoPlayer)**:
```kotlin
val player = ExoPlayer.Builder(context).build()
val mediaItem = MediaItem.fromUri("https://stream-url/playlist.m3u8")
player.setMediaItem(mediaItem)
player.prepare()
player.play()
```

---

## 16. API Wishlist (Not Available)

Features that would be useful but are not currently available:

❌ **Official REST API** - No documented JSON API
❌ **API Keys/OAuth** - No authentication system for third-party apps
❌ **Webhooks** - No event notifications
❌ **GraphQL Endpoint** - No GraphQL support
❌ **OpenAPI/Swagger Docs** - No API specification
❌ **Developer Portal** - No developer resources
❌ **SDK/Libraries** - No official SDKs

---

## 17. Legal & Ethical Considerations

⚠️ **Important Notices**:

1. **No Official API**: This documentation is based on reverse engineering
2. **Terms of Service**: May prohibit third-party apps
3. **Content Rights**: All content belongs to Proud Holding LLC
4. **Geo-blocking**: Bypassing may violate terms
5. **Authorization Required**: Contact content owner before building apps

**Recommendation**:
- Reach out to Proud Holding LLC for official API access
- Request partnership or licensing agreement
- Clarify terms for third-party app development

---

## 18. Future Research Needed

To complete this API documentation:

1. ✅ **Token Generation Logic** - How are session tokens created?
2. ⚠️ **Stream URL Format** - Exact pattern for HLS streams
3. ⚠️ **Authentication Flow** - Complete login/logout process
4. ⚠️ **API Error Codes** - Standardized error responses
5. ⚠️ **Rate Limits** - Request throttling policies
6. ⚠️ **Webhook Events** - If any exist
7. ⚠️ **Pagination** - How to page through large result sets
8. ⚠️ **Filtering/Sorting** - Query parameters for content filtering

---

## Appendix A: Example API Client (Kotlin)

```kotlin
class NamakadeClient(private val httpClient: OkHttpClient) {

    private val baseUrl = "https://namakade.com"
    private val apiUrl = "https://api.gliptv.com"
    private val cdnUrl = "https://media.negahestan.com"

    suspend fun getClientIP(): String? {
        val request = Request.Builder()
            .url("$apiUrl/ip.aspx")
            .build()

        return withContext(Dispatchers.IO) {
            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val json = JSONObject(response.body?.string() ?: "")
                    json.optString("clienIP")
                } else null
            }
        }
    }

    suspend fun getSeriesPage(seriesSlug: String, token: String?): String? {
        val url = "$baseUrl/serieses/$seriesSlug" +
                  if (token != null) "?$token" else ""

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0")
            .build()

        return withContext(Dispatchers.IO) {
            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) response.body?.string()
                else null
            }
        }
    }

    fun getThumbnailUrl(contentType: String, name: String): String {
        return when (contentType) {
            "series" -> "$cdnUrl/ipnx/media/series/thumbs/${name}_smallthumb.jpg"
            "movie" -> "$cdnUrl/ipnx/media/movies/thumbs/${name}_thumb.jpg"
            "show" -> "$cdnUrl/ipnx/media/shows/thumbs/${name}_smallthumb.jpg"
            else -> ""
        }
    }
}
```

---

**Document Version**: 1.0
**Last Updated**: 2025-10-29
**Status**: Reverse-engineered - not officially documented
**Contact**: Proud Holding LLC (for official API access)
