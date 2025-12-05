# DooPlay Theme API Analysis for FarsiPlex

## Summary

**Yes** - Parent/child relationships can be scraped
**No** - Genres and metadata are NOT in API, must be scraped from HTML

## Available APIs

### 1. DooPlay Search API ✅
**Endpoint:** `/wp-json/dooplay/search/`

**Parameters:**
- `keyword`: Search term (minimum 2 characters)
- `nonce`: Security token (currently: `94e0a6a60b`)

**Response:**
```json
{
  "10848": {
    "title": "Kanape",
    "url": "https://farsiplex.com/movie/sofa-36507af4/",
    "img": "https://image.tmdb.org/t/p/w185/poster.jpg",
    "extra": {
      "date": "2025",
      "imdb": "8.4"
    }
  }
}
```

**Use:** Content discovery (but sitemap.xml is better)

### 2. DooPlay Glossary API ✅
**Endpoint:** `/wp-json/dooplay/glossary/`

**Use:** Unknown - needs investigation

### 3. WordPress REST API ❌
**Endpoints:**
- `/wp-json/wp/v2/tvshows` - Does NOT exist
- `/wp-json/wp/v2/movies` - Does NOT exist
- `/wp-json/wp/v2/episodes` - Does NOT exist

**Conclusion:** WordPress REST API is disabled or restricted for custom post types

## What Must Be Scraped from HTML

### ✅ Parent/Child Relationships

**TV Show Structure:**
```
TV Show (tvshows table)
├── Season 1 (seasons table, foreign key: tvshow_id)
│   ├── Episode 1 (episodes table, foreign keys: tvshow_id, season_id)
│   ├── Episode 2
│   └── Episode 3
└── Season 2
    ├── Episode 1
    └── Episode 2
```

**Example from HTML:**
```html
<!-- TV Show Page -->
<h1>Concertino</h1>

<!-- Season Container -->
<div class="se-c">
    <span class="se-t">Season 1</span>
    <span class="date">May. 29, 2024</span>

    <!-- Episodes List -->
    <ul class="episodios">
        <li class="mark-1">
            <div class="numerando">1 - 1</div>
            <a href="https://farsiplex.com/episode/concertino-ep01-8df79eb9/">Episode 1</a>
            <span class="date">May. 29, 2024</span>
        </li>
        <li class="mark-2">
            <div class="numerando">1 - 2</div>
            <a href="https://farsiplex.com/episode/concertino-ep02-fe0282b8/">Episode 2</a>
            <span class="date">Jun. 05, 2024</span>
        </li>
    </ul>
</div>
```

**Scraping Strategy:**
1. Get TV show URL from sitemap
2. Parse TV show page for metadata (title, poster, rating, genres)
3. Extract all seasons
4. For each season, extract all episode URLs
5. Visit each episode URL to get video sources

### ❌ Genres

**NOT in API** - Must scrape from HTML

**HTML Pattern:**
```html
<a href="https://farsiplex.com/genres/music/">Music</a>
<a href="https://farsiplex.com/genres/talk-show/">Talk Show</a>
```

**Scraping:**
```python
genre_links = soup.find_all('a', href=re.compile(r'/genres/'))
genres = [link.text.strip() for link in genre_links]
```

### ❌ Metadata

**NOT in API** - Must scrape from HTML

**Available Metadata:**
- Title (English + Persian)
- Poster URL
- Rating (site rating + IMDb + TMDb)
- Votes count
- Release date
- Country
- Duration
- Synopsis (English + Persian)
- Genres
- Cast (if implemented)

## Video Source Extraction

### DooPlay Method (Recommended) ✅

**Step 1: Extract form data from movie/episode page**
```html
<form id="watch-10848" method="post" action="/play/">
    <input name="id" value="10848">
    <input name="watch_episode_nonce" value="45d9d84c93">
    <input name="_wp_http_referer" value="/movie/sofa-36507af4/">
</form>
```

**Step 2: Submit form to /play/**
```python
response = session.post('https://farsiplex.com/play/', data={
    'id': '10848',
    'watch_episode_nonce': '45d9d84c93',
    '_wp_http_referer': '/movie/sofa-36507af4/'
})
```

**Step 3: Parse iframe source**
```html
<iframe src="https://farsiplex.com/jwplayer/?source=https%3A%2F%2Fcdn2.farsiland.com%2Fmovies%2F1403%2FKanape.480.new.mp4&id=10848&type=mp4"></iframe>
```

**Step 4: Extract video URL**
```
https://cdn2.farsiland.com/movies/1403/Kanape.480.new.mp4
```

**Step 5: Get other qualities**
```html
<li data-post="10848" data-type="tv" data-nume="1">480</li>
<li data-post="10848" data-type="tv" data-nume="2">720</li>
<li data-post="10848" data-type="tv" data-nume="3">1080</li>
```

**Quality URL Pattern:**
```
480p: .../Kanape.480.new.mp4
720p: .../Kanape.720.new.mp4
1080p: .../Kanape.1080.new.mp4
```

## Database Schema (Already Implemented)

```sql
-- TV Shows (parent)
CREATE TABLE tvshows (
    id INTEGER PRIMARY KEY,
    slug TEXT UNIQUE,
    title TEXT,
    poster_url TEXT,
    rating REAL,
    ...
);

-- Seasons (child of tvshow)
CREATE TABLE seasons (
    id INTEGER PRIMARY KEY,
    tvshow_id INTEGER,  -- Foreign key
    season_number INTEGER,
    release_date TEXT,
    FOREIGN KEY (tvshow_id) REFERENCES tvshows(id)
);

-- Episodes (child of season and tvshow)
CREATE TABLE episodes (
    id INTEGER PRIMARY KEY,
    tvshow_id INTEGER,  -- Foreign key
    season_id INTEGER,  -- Foreign key
    episode_number INTEGER,
    slug TEXT UNIQUE,
    url TEXT,
    ...
    FOREIGN KEY (tvshow_id) REFERENCES tvshows(id),
    FOREIGN KEY (season_id) REFERENCES seasons(id)
);

-- Episode Videos
CREATE TABLE episode_videos (
    id INTEGER PRIMARY KEY,
    episode_id INTEGER,  -- Foreign key
    quality TEXT,
    url TEXT,
    ...
    FOREIGN KEY (episode_id) REFERENCES episodes(id)
);

-- Genres (many-to-many)
CREATE TABLE tvshow_genres (
    tvshow_id INTEGER,
    genre TEXT,
    PRIMARY KEY (tvshow_id, genre)
);
```

## Complete Scraping Workflow

### For Movies:
1. Get URLs from sitemap: `/wp-sitemap-posts-movies-1.xml`
2. For each movie URL:
   - Parse HTML for metadata & genres
   - Extract DooPlay form data
   - Submit to /play/ to get videos
   - Save to database with all qualities

### For TV Shows:
1. Get TV show URLs from sitemap: `/wp-sitemap-posts-tvshows-1.xml`
2. For each TV show URL:
   - Parse HTML for metadata & genres
   - Extract seasons and episode URLs
   - Save TV show to database
3. For each episode URL:
   - Parse HTML for episode metadata
   - Extract DooPlay form data
   - Submit to /play/ to get videos
   - Save episode + videos to database with foreign keys

### For Episodes (Direct):
1. Get episode URLs from sitemap: `/wp-sitemap-posts-episodes-1.xml`
2. Match episodes to parent TV shows via URL patterns
3. Extract videos using DooPlay method

## Optimization Tips

1. **Use sitemaps for discovery** - More reliable than search API
2. **Batch episode scraping** - Process all episodes from TV show page at once
3. **Cache nonces** - Nonces seem stable (but validate before production)
4. **Respect delays** - 2-3 seconds between requests
5. **Resume capability** - Track progress in database for large scrapes
6. **Parallel processing** - Can scrape multiple TV shows in parallel

## Missing from API

These require HTML scraping:
- ❌ Genres
- ❌ Cast/Crew
- ❌ Synopsis
- ❌ Ratings (IMDb, TMDb)
- ❌ Country
- ❌ Duration
- ❌ Parent/child relationships
- ❌ Video sources (use DooPlay form method)

## Available via Sitemap

These are in sitemap.xml:
- ✅ All content URLs (movies, TV shows, episodes)
- ✅ Last modified dates
- ✅ Complete inventory (no missing items)
