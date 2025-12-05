# FarsiPlex Sitemap Scraper

**The recommended scraper** - Uses WordPress sitemap.xml for complete and reliable content discovery.

## Why Use This Scraper?

✅ **More Reliable**: Uses sitemap.xml instead of search API
✅ **Complete Coverage**: Discovers ALL content (37 movies, 35 TV shows, 692 episodes)
✅ **No API Issues**: Doesn't need nonce authentication or keyword guessing
✅ **Update Tracking**: Tracks `lastmod` dates for smart incremental updates
✅ **Faster Discovery**: Direct XML parsing vs. multiple search queries

## Quick Start

### 1. Install Dependencies

```bash
pip install -r requirements.txt
```

### 2. Run the Scraper

```bash
python farsiplex_scraper_sitemap.py
```

**Output:**
- Database: `Farsiplex.db`
- JSON files: `farsiplex_movies.json`, `farsiplex_tvshows.json`

## How It Works

### Content Discovery

The scraper fetches three WordPress sitemaps:

```
https://farsiplex.com/wp-sitemap-posts-movies-1.xml    → 37 movies
https://farsiplex.com/wp-sitemap-posts-tvshows-1.xml   → 35 TV shows
https://farsiplex.com/wp-sitemap-posts-episodes-1.xml  → 692 episodes
```

Each sitemap entry contains:
```xml
<url>
  <loc>https://farsiplex.com/movie/siyah-sang/</loc>
  <lastmod>2025-11-06T18:54:25+00:00</lastmod>
</url>
```

### Data Extraction

For each URL, the scraper:
1. Fetches the page HTML
2. Extracts metadata (title, poster, rating, synopsis, genres, etc.)
3. Extracts ALL video sources (MP4 URLs for all qualities)
4. Saves to SQLite with parent-child relationships
5. Exports to JSON files

### Database Schema

Same structure as the original scraper:

- **movies** - Movie metadata
- **movie_videos** - Video sources (1080p, 720p, 480p, etc.)
- **movie_genres** - Movie genres
- **tvshows** - TV show metadata
- **seasons** - Season data (linked to tvshows)
- **episodes** - Episode data (linked to seasons)
- **episode_videos** - Episode video sources
- **tvshow_genres** - TV show genres

## Update Checking

Check for new/updated content:

```python
from farsiplex_scraper_sitemap import FarsiPlexSitemapScraper

scraper = FarsiPlexSitemapScraper()
updates = scraper.check_for_updates()

print(f"New movies: {len(updates['new_movies'])}")
print(f"Updated movies: {len(updates['updated_movies'])}")
print(f"New TV shows: {len(updates['new_tvshows'])}")
print(f"Updated TV shows: {len(updates['updated_tvshows'])}")
```

The update checker compares sitemap `lastmod` dates with database records to find:
- **New content**: URLs not in database
- **Updated content**: URLs with different `lastmod` dates

## Comparison with Old Scraper

| Feature | Old Scraper (farsiplex_scraper.py) | New Scraper (farsiplex_scraper_sitemap.py) |
|---------|-----------------------------------|-------------------------------------------|
| Discovery Method | Search API with keywords | Sitemap.xml parsing |
| Keyword Issue | ❌ Fails with single letters | ✅ No keywords needed |
| Completeness | ⚠️ May miss content | ✅ 100% coverage |
| API Authentication | ❌ Requires nonce | ✅ No auth needed |
| Update Detection | Basic (checks explore page) | ✅ Smart (uses lastmod dates) |
| Speed | Slow (many search queries) | ✅ Fast (direct XML parsing) |
| Reliability | ⚠️ API-dependent | ✅ Very reliable |

## What Gets Scraped

### Movies
- Title (English + Persian)
- Poster URL
- Release date, country, rating, votes
- Synopsis (English + Persian)
- Genres
- **All video qualities**: 1080p, 720p, 480p, 360p
- CDN sources: FarsiLand, FarsiCDN

### TV Shows
- All movie fields above
- **Seasons** with release dates
- **Episodes** with:
  - Episode number, title, thumbnail
  - Episode-specific metadata
  - **All video qualities** for each episode

### Example Output

```
=== Content Discovery via Sitemaps ===

[1/3] Movies:
Fetching sitemap: https://farsiplex.com/wp-sitemap-posts-movies-1.xml...
✓ Found 37 URLs

[2/3] TV Shows:
Fetching sitemap: https://farsiplex.com/wp-sitemap-posts-tvshows-1.xml...
✓ Found 35 URLs

[3/3] Episodes:
Fetching sitemap: https://farsiplex.com/wp-sitemap-posts-episodes-1.xml...
✓ Found 692 URLs

✓ Total content discovered:
  - Movies: 37
  - TV Shows: 35
  - Episodes: 692

=== Scraping 37 Movies ===
[1/37] Scraping movie: siyah-sang... ✓ (3 videos)
[2/37] Scraping movie: another-movie... ✓ (4 videos)
...

=== Scraping 35 TV Shows ===
[1/35] Scraping TV show: show-name... ✓ (4 seasons)
  Scraping videos for S01E01... ✓ (3 sources)
  Scraping videos for S01E02... ✓ (3 sources)
...

=== Exporting to JSON ===
✓ Exported 37 movies to farsiplex_movies.json
✓ Exported 35 TV shows to farsiplex_tvshows.json

✓ Scraping complete!
  Time elapsed: 45.3 minutes
  Database: Farsiplex.db
```

## Configuration

You can customize scraper behavior:

```python
scraper = FarsiPlexSitemapScraper(
    db_path="Farsiplex.db",  # Database file
    delay=2.0                 # Delay between requests (seconds)
)
```

**Recommended delay**: 2.0 seconds (be respectful to the server)

## Troubleshooting

### No video sources found
- Check if the website structure has changed
- Verify JWPlayer is still being used
- Check console output for extraction errors

### Connection errors
- Check internet connection
- Verify farsiplex.com is accessible
- Try increasing the delay between requests

### Database errors
- Delete `Farsiplex.db` to start fresh
- Check file permissions
- Ensure SQLite is installed

## Next Steps

After scraping:

1. **Query the database**:
   ```bash
   sqlite3 Farsiplex.db
   SELECT * FROM movies LIMIT 5;
   ```

2. **Use JSON exports**:
   ```python
   import json
   with open('farsiplex_movies.json', 'r') as f:
       movies = json.load(f)
   ```

3. **Check for updates daily**:
   ```python
   scraper.check_for_updates()
   ```

## License

Personal use only. Respect farsiplex.com's terms of service.
