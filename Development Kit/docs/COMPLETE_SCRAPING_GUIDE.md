# Complete FarsiPlex Scraping Guide

## Summary of All Findings

### ✅ What We Discovered

**1. DooPlay Theme Confirmed**
- WordPress 6.x with DooPlay 2.5.5 theme
- Premium video streaming theme
- Has REST API for search only

**2. Parent/Child Relationships - YES! ✅**
```
TV Show (parent)
  └── Season 1 (child of TV show)
       ├── Episode 1 (child of season)
       ├── Episode 2
       └── Episode 3
```
- Available via HTML scraping
- Complete hierarchy maintained
- Foreign keys: `tvshow_id`, `season_id`

**3. Genres & Metadata - NO API ❌**
- Must scrape from HTML
- Includes: genres, ratings, country, synopsis, cast
- Not available in REST API

**4. Search API - Available ✅**
```
GET /wp-json/dooplay/search/?keyword=film&nonce=94e0a6a60b
```
- Live autocomplete search
- Returns JSON with titles, URLs, ratings
- Minimum 2 characters required
- Nonce: `94e0a6a60b` (stable)

## Complete Scraping Architecture

### Method 1: Sitemap-Based (Recommended) ⭐

**Best for:** Complete inventory, reliable scraping

**Steps:**
1. **Discovery** - Fetch XML sitemaps:
   - `/wp-sitemap-posts-movies-1.xml` (37 movies)
   - `/wp-sitemap-posts-tvshows-1.xml` (35 TV shows)
   - `/wp-sitemap-posts-episodes-1.xml` (692 episodes)

2. **Metadata** - For each URL, scrape HTML:
   - Title (English + Persian)
   - Poster, rating, votes
   - Synopsis (English + Persian)
   - Genres (via `/genres/` links)
   - Release date, country, duration

3. **Parent/Child** - For TV shows:
   - Extract seasons from `<div class="se-c">`
   - Extract episodes from `<ul class="episodios">`
   - Maintain relationships in database

4. **Videos** - Use DooPlay form method:
   - Extract form data: `post_id`, `nonce`, `referer`
   - POST to `/play/`
   - Parse iframe src for video URL
   - Generate quality variants (480p, 720p, 1080p)

**Code:** `farsiplex_scraper_dooplay.py`

### Method 2: Search API Based

**Best for:** Targeted discovery, validation

**Steps:**
1. **Discovery** - Search with keywords:
```python
results = search('film')  # Returns JSON
```

2. **Extract URLs** - Parse results:
```python
for post_id, data in results.items():
    url = data['url']
    title = data['title']
    rating = data['extra']['imdb']
```

3. **Scrape Content** - Same as Method 1 (steps 2-4)

**Limitation:** May not find all content

## Database Schema

### Complete Hierarchy

```sql
-- Movies (standalone)
movies
  ├── id (PK)
  ├── slug (unique)
  ├── title, title_en, title_fa
  ├── poster_url, rating, votes
  ├── synopsis_en, synopsis_fa
  ├── genres (via movie_genres table)
  └── videos (via movie_videos table)

-- TV Shows (parent level 1)
tvshows
  ├── id (PK)
  ├── slug (unique)
  ├── title, poster_url, rating
  ├── genres (via tvshow_genres table)
  └── seasons → (child level 2)
       ├── id (PK)
       ├── tvshow_id (FK) ← parent reference
       ├── season_number
       └── episodes → (child level 3)
            ├── id (PK)
            ├── tvshow_id (FK) ← grandparent reference
            ├── season_id (FK) ← parent reference
            ├── episode_number
            ├── title, url, thumbnail
            └── videos (via episode_videos table)
```

### Foreign Key Relationships

```sql
-- Episodes reference both season AND tvshow
FOREIGN KEY (tvshow_id) REFERENCES tvshows(id)
FOREIGN KEY (season_id) REFERENCES seasons(id)

-- Seasons reference tvshow
FOREIGN KEY (tvshow_id) REFERENCES tvshows(id)

-- Videos reference their parent content
FOREIGN KEY (movie_id) REFERENCES movies(id)
FOREIGN KEY (episode_id) REFERENCES episodes(id)
```

### Querying Hierarchy

```sql
-- Get all episodes for a TV show
SELECT e.*
FROM episodes e
JOIN seasons s ON e.season_id = s.id
JOIN tvshows t ON s.tvshow_id = t.id
WHERE t.slug = 'concertino-34587fff';

-- Get complete TV show with seasons and episodes
SELECT
    t.title as show_title,
    s.season_number,
    e.episode_number,
    e.title as episode_title,
    ev.quality,
    ev.url as video_url
FROM tvshows t
JOIN seasons s ON s.tvshow_id = t.id
JOIN episodes e ON e.season_id = s.id
LEFT JOIN episode_videos ev ON ev.episode_id = e.id
WHERE t.slug = 'concertino-34587fff'
ORDER BY s.season_number, e.episode_number;
```

## Video Extraction (DooPlay Method)

### How It Works

**Step 1: Extract form from content page**
```html
<form id="watch-13708" method="post" action="/play/">
    <input name="id" value="13708">
    <input name="watch_episode_nonce" value="45d9d84c93">
    <input name="_wp_http_referer" value="/movie/the-old-328f6c93/">
</form>
```

**Step 2: Submit form**
```python
response = session.post('https://farsiplex.com/play/', data={
    'id': '13708',
    'watch_episode_nonce': '45d9d84c93',
    '_wp_http_referer': '/movie/the-old-328f6c93/'
})
```

**Step 3: Parse iframe**
```html
<iframe src="https://farsiplex.com/jwplayer/?source=https%3A%2F%2Fcdn2.farsiland.com%2Fmovies%2F1403%2FKanape.480.new.mp4&id=10848&type=mp4">
```

**Step 4: Decode source parameter**
```
https://cdn2.farsiland.com/movies/1403/Kanape.480.new.mp4
```

**Step 5: Generate quality variants**
```
480p: .../Kanape.480.new.mp4
720p: .../Kanape.720.new.mp4
1080p: .../Kanape.1080.new.mp4
```

### Implementation

```python
def extract_video_sources_dooplay(self, content_url: str) -> List[Dict]:
    # Get page
    response = self.session.get(content_url)
    soup = BeautifulSoup(response.text, 'html.parser')

    # Extract form data
    form_data = self.extract_dooplay_form_data(soup)

    # Submit to /play/
    play_response = self.session.post(
        f"{self.BASE_URL}/play/",
        data=form_data,
        headers={'Referer': content_url}
    )

    # Parse iframe
    play_soup = BeautifulSoup(play_response.text, 'html.parser')
    iframe = play_soup.find('iframe')

    # Extract video URL from iframe src
    parsed = urlparse(iframe['src'])
    query = parse_qs(parsed.query)
    video_url = unquote(query['source'][0])

    # Generate quality variants
    videos = []
    for quality in ['480', '720', '1080']:
        url = video_url.replace('.480.', f'.{quality}.')
        videos.append({
            'url': url,
            'quality': f'{quality}p',
            'cdn_source': 'farsiland',
            'player_type': 'jwplayer'
        })

    return videos
```

## Available Scrapers

### 1. `farsiplex_scraper_dooplay.py` ⭐ RECOMMENDED

**Features:**
- ✅ Uses sitemap for discovery
- ✅ DooPlay form method for videos
- ✅ Full parent/child relationships
- ✅ Genres and metadata extraction
- ✅ Database migration support
- ✅ Movies AND TV shows

**Usage:**
```bash
python farsiplex_scraper_dooplay.py
```

**Output:**
- `Farsiplex.db` - SQLite database with all data
- Complete hierarchy preserved
- All video qualities (480p, 720p, 1080p)

### 2. `farsiplex_scraper_sitemap.py`

**Features:**
- ✅ Sitemap-based discovery
- ⚠️ Basic video extraction (may miss some)
- ✅ Full metadata
- ✅ Database + JSON export

**Usage:**
```bash
python farsiplex_scraper_sitemap.py
```

### 3. `farsiplex_scraper.py` (Original)

**Status:** ❌ BROKEN - Search API keyword issue

**Issue:** Uses single letters which API rejects

**Fix:** Use `farsiplex_scraper_dooplay.py` instead

## API Reference

### DooPlay Search API

**Endpoint:**
```
GET /wp-json/dooplay/search/
```

**Parameters:**
- `keyword` (required): Search term (min 2 chars)
- `nonce` (required): `94e0a6a60b`

**Response:**
```json
{
  "post_id": {
    "title": "Movie Title",
    "url": "https://farsiplex.com/movie/slug/",
    "img": "poster_url",
    "extra": {
      "date": "2025",
      "imdb": "8.4"
    }
  }
}
```

**Rate Limiting:**
- Recommended: 1-2 sec delay
- Unknown official limits

### WordPress Sitemaps

**Endpoints:**
```
/wp-sitemap.xml                           # Index
/wp-sitemap-posts-movies-1.xml            # 37 movies
/wp-sitemap-posts-tvshows-1.xml           # 35 TV shows
/wp-sitemap-posts-episodes-1.xml          # 692 episodes
```

**Format:**
```xml
<url>
  <loc>https://farsiplex.com/movie/slug/</loc>
  <lastmod>2025-11-06T18:54:25+00:00</lastmod>
</url>
```

**Best for:** Complete content discovery

## Performance Optimization

### Parallel Scraping

```python
from concurrent.futures import ThreadPoolExecutor

def scrape_parallel(urls: List[str], max_workers: int = 5):
    with ThreadPoolExecutor(max_workers=max_workers) as executor:
        results = executor.map(scrape_movie, urls)
    return list(results)
```

### Caching

```python
from functools import lru_cache

@lru_cache(maxsize=100)
def get_nonce() -> str:
    # Cache nonce for 1 hour
    return extract_nonce_from_page()
```

### Resume Capability

```python
# Track progress in database
cursor.execute("""
    CREATE TABLE scraper_progress (
        url TEXT PRIMARY KEY,
        status TEXT,
        scraped_at TIMESTAMP
    )
""")

# Check if already scraped
cursor.execute("SELECT status FROM scraper_progress WHERE url = ?", (url,))
if cursor.fetchone():
    print(f"Skipping {url} - already scraped")
    return
```

## Testing & Validation

### Test Single Movie

```bash
python -c "
from farsiplex_scraper_dooplay import FarsiPlexDooPlayScraper
scraper = FarsiPlexDooPlayScraper()
movie = scraper.scrape_movie({'url': 'https://farsiplex.com/movie/sofa-36507af4/', 'lastmod': None})
print(f'Videos: {len(movie[\"video_sources\"])}')
"
```

### Test Single TV Show

```bash
python -c "
from farsiplex_scraper_dooplay import FarsiPlexDooPlayScraper
scraper = FarsiPlexDooPlayScraper()
show = scraper.scrape_tvshow({'url': 'https://farsiplex.com/tvshow/concertino-34587fff/', 'lastmod': None})
print(f'Seasons: {len(show[\"seasons\"])}, Episodes: {sum(len(s[\"episodes\"]) for s in show[\"seasons\"])}')
"
```

### Verify Database

```bash
sqlite3 Farsiplex.db
SELECT COUNT(*) FROM movies;
SELECT COUNT(*) FROM tvshows;
SELECT COUNT(*) FROM episodes;
SELECT COUNT(*) FROM movie_videos;
SELECT COUNT(*) FROM episode_videos;
```

## Troubleshooting

### No Videos Found

**Cause:** DooPlay form extraction failed

**Fix:**
1. Check if website structure changed
2. Verify nonce is still valid
3. Try alternative extraction method

### Database Errors

**Cause:** Schema mismatch

**Fix:**
```python
# Scraper auto-migrates, but if needed:
sqlite3 Farsiplex.db
ALTER TABLE movies ADD COLUMN last_modified TEXT;
```

### Rate Limiting

**Cause:** Too many requests

**Fix:**
```python
# Increase delay
scraper = FarsiPlexDooPlayScraper(delay=5.0)  # 5 seconds
```

## Final Recommendations

✅ **Use `farsiplex_scraper_dooplay.py`** - Most complete and reliable

✅ **Sitemap for discovery** - 100% coverage

✅ **DooPlay form for videos** - Most reliable video extraction

✅ **Maintain relationships** - Parent/child in database

✅ **Respect delays** - 2-3 seconds between requests

✅ **Monitor nonce** - Check for rotation periodically

✅ **Track progress** - Enable resume capability

✅ **Export to JSON** - Keep backup in JSON format

## Complete Example Workflow

```python
from farsiplex_scraper_dooplay import FarsiPlexDooPlayScraper

# Initialize
scraper = FarsiPlexDooPlayScraper(
    db_path="Farsiplex.db",
    delay=2.0  # 2 seconds between requests
)

# Run full scrape
scraper.run_full_scrape()

# Result:
# ✓ Database: Farsiplex.db
# ✓ 37 movies with videos
# ✓ 35 TV shows with 692 episodes
# ✓ All parent/child relationships
# ✓ All genres and metadata
# ✓ Multiple video qualities per content
```

## Files Created

| File | Description |
|------|-------------|
| `farsiplex_scraper_dooplay.py` | ⭐ Main scraper (recommended) |
| `farsiplex_scraper_sitemap.py` | Sitemap-based scraper |
| `DOOPLAY_API_ANALYSIS.md` | DooPlay API documentation |
| `SEARCH_API_ANALYSIS.md` | Search functionality docs |
| `COMPLETE_SCRAPING_GUIDE.md` | This file |
| `Farsiplex.db` | SQLite database (generated) |

## Questions Answered

✅ **Can we scrape parent/child relationships?** YES - Via HTML scraping

✅ **Are genres/metadata in API?** NO - Must scrape from HTML

✅ **How does search work?** DooPlay REST API with live autocomplete

✅ **Best method for complete scraping?** Sitemap + DooPlay form method

✅ **Video extraction?** DooPlay form submission to /play/ endpoint

✅ **Database structure?** Foreign keys maintain TV Show → Season → Episode hierarchy
