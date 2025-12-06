# FarsiPlex.com - Complete Technical Analysis

## Overview
FarsiPlex is a Persian movies and TV shows streaming platform built on WordPress with the DooPlay theme (version 2.5.5). The site provides curated Iranian cinema content with high-quality video streaming.

---

## Site Architecture

### Technology Stack
- **CMS**: WordPress 6.x
- **Theme**: DooPlay 2.5.5 (Premium video streaming theme)
- **CDN**: Cloudflare
- **Video Player**: JWPlayer 8.36.6
- **Frontend**: jQuery, Owl Carousel, Custom theme assets
- **Content Source**: FarsiLand CDN (`cdn2.farsiland.com` and `s2.farsicdn.buzz`)

### Content Types
1. **Movies** (`/movie/`)
2. **TV Shows** (`/tvshow/`)
3. **Episodes** (`/episode/`)
4. **Explore/Home** (`/explore/`)

---

## API Endpoints Discovered

### 1. Search API
**Endpoint**: `/wp-json/dooplay/search/`

**Method**: GET

**Parameters**:
- `keyword` (string): Search query
- `nonce` (string): WordPress nonce for verification (e.g., `94e0a6a60b`)

**Response Structure**:
```json
{
  "[ID]": {
    "title": "Movie/Show Title",
    "url": "https://farsiplex.com/movie/slug/",
    "img": "https://farsiplex.com/wp-content/uploads/.../thumbnail.jpg",
    "extra": {
      "date": "2025",
      "imdb": "7.6" // or false if not available
    }
  }
}
```

**Example**:
```
GET /wp-json/dooplay/search/?keyword=test&nonce=94e0a6a60b
```

---

### 2. WordPress REST API

**Available Namespaces**:
- `wp/v2` - Standard WordPress REST API
- `dooplay` - DooPlay theme custom endpoints
- `dooplayer/v2` - Media player functionality
- `sps/v1` - Site performance and statistics
- `spc/v1` - Caching and optimization
- `psd/v1` - Push notifications

**Key Endpoints**:
- `/wp-json/wp/v2/posts` - Blog posts
- `/wp-json/wp/v2/pages` - Static pages
- `/wp-json/wp/v2/media` - Attachments
- `/wp-json/dooplay/search` - Search functionality
- `/wp-json/dooplay/glossary` - Terminology
- `/wp-json/dooplayer/v2/` - Video player data

---

## Content Structure

### Movie Page Structure
**URL Pattern**: `https://farsiplex.com/movie/{slug}/`

**Key Metadata**:
- Title (English & Persian)
- Release date
- Country (Iran)
- Rating (User votes)
- Genres
- Synopsis (English & Persian)
- Poster image
- Video player options (HD)

**Example**: `/movie/siyah-sang/` (Coal - 2025)

---

### TV Show Structure
**URL Pattern**: `https://farsiplex.com/tvshow/{slug}/`

**Key Metadata**:
- Title
- Release date
- Rating & votes
- Genres
- Seasons list with episodes
- Episode structure:
  - Season number
  - Episode number
  - Episode title
  - Release date
  - Thumbnail
  - Direct link to episode page

**Example**: `/tvshow/beretta-dastane-yek-aslahe-88d90546/`
- Season 1 with 2 episodes
- Each episode has URL: `/episode/{slug}/`

---

### Episode Pages
**URL Pattern**: `https://farsiplex.com/episode/{slug}/`

Each episode links to the TV show parent and contains its own video player.

---

## Video Player System

### Player Architecture
FarsiPlex uses a multi-player system with 3 options:

1. **Player 1** (Default): JWPlayer with iframe embed
2. **Player 2**: Alternative player
3. **Player 3**: Backup player

### Video Source Discovery

**Play Page Pattern**:
```
https://farsiplex.com/play/
```

**JWPlayer Iframe**:
```
https://farsiplex.com/jwplayer/?source={encoded_video_url}&id={post_id}&type=mp4
```

**Example Video Source**:
```
https://cdn2.farsiland.com/movies/1404/Siah-Sang.new.mp4
```

Or via alternate CDN:
```
https://s2.farsicdn.buzz/movies/1404/Siah-Sang.new.mp4?md5={hash}
```

### Video Quality
- **1080p HD** - Primary quality available
- Direct MP4 streaming
- No DRM protection detected

---

## Scraping Strategy

### Recommended Approach

#### 1. Content Discovery
Use the search API to discover all content:
```bash
GET /wp-json/dooplay/search/?keyword={a-z}&nonce={nonce}
```

Iterate through all letters/keywords to build complete catalog.

#### 2. Metadata Extraction
For each discovered item:
- Parse movie/tvshow pages for complete metadata
- Extract:
  - Title (EN/FA)
  - Synopsis (EN/FA)
  - Release date
  - Genres
  - Ratings
  - Poster images
  - For TV shows: season/episode structure

#### 3. Video Source Extraction
**Method 1 - Direct URL Pattern**:
```
https://cdn2.farsiland.com/movies/{year}/{title}.mp4
https://cdn2.farsiland.com/movies/{year}/{title}.new.mp4
```

**Method 2 - Parse Play Page**:
1. Click HD/Player link from movie/episode page
2. Extract iframe src from `/play/` page
3. Parse iframe URL to get video source
4. Format: `/jwplayer/?source={url}&id={id}&type=mp4`

**Method 3 - WordPress API**:
Try dooplayer endpoints (may require authentication):
```
/wp-json/dooplayer/v2/
```

#### 4. Image Assets
**Poster Images**:
```
https://farsiplex.com/wp-content/uploads/{year}/{month}/{filename}.jpg
```

Multiple sizes available:
- Original
- 225x300 (thumbnail)
- 90x135 (small)

---

## Technical Considerations

### Rate Limiting
- No obvious rate limiting detected
- Cloudflare protection active
- Use reasonable delays between requests (1-2s)

### Authentication
- No authentication required for content browsing
- Nonce required for search API (can be extracted from homepage)
- Video sources are public URLs (no token auth)

### Content Freshness
- New content added regularly
- Check homepage `/explore/` for latest additions
- Episode lists show release dates

---

## Scraping Implementation Plan

### Phase 1: Discovery
```python
1. GET homepage to extract search nonce
2. Iterate search API with different keywords
3. Build database of all movies, TV shows, episodes
4. Store: ID, title, URL, thumbnail, date, rating
```

### Phase 2: Metadata Collection
```python
1. For each content URL from Phase 1
2. Parse HTML page
3. Extract full metadata (title, synopsis, genres, etc.)
4. For TV shows: parse season/episode structure
5. Store complete metadata in database
```

### Phase 3: Video Source Extraction
```python
1. For each movie/episode
2. Simulate "HD" button click (POST to /play/)
3. Extract iframe src
4. Parse video URL from iframe
5. Store direct video URL
6. Optionally: Test alternate CDN patterns
```

### Phase 4: Asset Download (Optional)
```python
1. Download poster images
2. Store thumbnails locally
3. Organize by content type
```

---

## Key URLs Reference

### Navigation
- Homepage: `https://farsiplex.com/`
- Explore: `https://farsiplex.com/explore/`
- Movies: `https://farsiplex.com/movie/`
- TV Shows: `https://farsiplex.com/tvshow/`
- Episodes: `https://farsiplex.com/episode/`

### API
- Search: `/wp-json/dooplay/search/?keyword={q}&nonce={n}`
- WP REST: `/wp-json/wp/v2/`
- Player: `/wp-json/dooplayer/v2/`

### CDN Sources
- Primary: `cdn2.farsiland.com`
- Alternate: `s2.farsicdn.buzz`

---

## Sample Data Structures

### Movie Object
```json
{
  "id": 13877,
  "title": "Siyah Sang",
  "title_en": "Coal",
  "slug": "siyah-sang",
  "type": "movie",
  "url": "https://farsiplex.com/movie/siyah-sang/",
  "poster": "https://farsiplex.com/wp-content/uploads/2025/11/Siah-Sang.jpg",
  "release_date": "2025-11-06",
  "country": "Iran",
  "rating": 9.0,
  "votes": 1,
  "genres": ["Short Film"],
  "synopsis_en": "Isa and Mokhtar, two miners, get into a fight...",
  "synopsis_fa": "عیسی و مختار، دو معدن کار...",
  "video_source": "https://cdn2.farsiland.com/movies/1404/Siah-Sang.new.mp4",
  "quality": "1080p"
}
```

### TV Show Object
```json
{
  "id": 12345,
  "title": "Beretta: Dastane Yek Aslahe",
  "slug": "beretta-dastane-yek-aslahe-88d90546",
  "type": "tvshow",
  "url": "https://farsiplex.com/tvshow/beretta-dastane-yek-aslahe-88d90546/",
  "poster": "https://farsiplex.com/wp-content/uploads/.../poster.jpg",
  "release_date": "2025-10-31",
  "rating": 8.1,
  "votes": 14,
  "genres": ["Crime"],
  "seasons": [
    {
      "season": 1,
      "release_date": "2025-10-31",
      "episodes": [
        {
          "episode": 1,
          "title": "Episode 1",
          "url": "https://farsiplex.com/episode/beretta-dastane-yek-aslahe-ep01-7f894733/",
          "release_date": "2025-10-31",
          "thumbnail": "url"
        },
        {
          "episode": 2,
          "title": "Episode 2",
          "url": "https://farsiplex.com/episode/beretta-dastane-yek-aslahe-ep02/",
          "release_date": "2025-11-07",
          "thumbnail": "url"
        }
      ]
    }
  ]
}
```

---

## Legal & Ethical Notes

⚠️ **Important Considerations**:

1. **Copyright**: All content is copyrighted material. Scraping for personal use only.
2. **Terms of Service**: Review FarsiPlex terms at `/terms/`
3. **Rate Limiting**: Implement respectful delays
4. **Attribution**: Credit FarsiPlex as content source
5. **Personal Use**: Do not redistribute scraped content commercially

---

## Implementation Recommendations

### Tools
- **Python** with `requests`, `BeautifulSoup4`, `lxml`
- **Selenium** or **Playwright** for dynamic content (if needed)
- **Database**: SQLite or PostgreSQL for metadata storage
- **Video Player**: ExoPlayer (Android) or VLC integration

### Best Practices
1. Cache search nonce (refresh periodically)
2. Store metadata locally to reduce requests
3. Implement exponential backoff on errors
4. Handle Cloudflare challenges gracefully
5. Log all requests for debugging
6. Validate video URLs before storing
7. Check for content updates periodically

---

## Next Steps for Integration

1. **Create Data Models**: Define schema for movies/shows/episodes
2. **Build Scraper**: Implement phased scraping approach
3. **Video Player Integration**: Embed video sources in app
4. **Metadata Display**: Design UI for browsing content
5. **Search Functionality**: Local search on scraped data
6. **Update Mechanism**: Periodic sync for new content
7. **Offline Support**: Download videos for offline viewing (optional)

---

**Analysis Date**: November 7, 2025
**Site Version**: DooPlay 2.5.5
**Status**: Active & Maintained
