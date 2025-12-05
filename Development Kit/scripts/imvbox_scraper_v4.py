#!/usr/bin/env python3
"""
IMVBox.com Content Scraper v4

Scrapes ALL available metadata from IMVBox.com.
Core schema matches Android ContentDatabase.
Extra columns (writer, producer, music, etc.) stored for future use.

Usage: python imvbox_scraper_v4.py

Requirements:
    pip install requests beautifulsoup4 lxml
"""

import os
import re
import time
import json
import sqlite3
import hashlib
import requests
from bs4 import BeautifulSoup
from typing import Dict, List, Optional, Tuple
from pathlib import Path
from urllib.parse import urljoin
from datetime import datetime

# Configuration
BASE_URL = "https://www.imvbox.com/en"
ASSETS_URL = "https://assets.imvbox.com"
DB_FILE = Path(__file__).parent / "imvbox_content.db"
RATE_LIMIT_SEC = 0.5  # 500ms between requests
MAX_PAGES = 200  # Max pages to scrape

# Session for connection pooling
session = requests.Session()
session.headers.update({
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120.0.0.0 Safari/537.36',
    'Accept': 'text/html,application/xhtml+xml',
    'Accept-Language': 'en-US,en;q=0.9',
})

last_request_time = 0


def parse_upload_date(date_str: Optional[str]) -> Optional[int]:
    """
    Parse uploadDate string to millisecond timestamp.
    Supports formats: 'YYYY-MM-DD', 'YYYY-MM-DDTHH:MM:SS', ISO 8601 variants.
    Returns None if parsing fails.
    """
    if not date_str:
        return None

    # Try common formats
    formats = [
        '%Y-%m-%d',                    # 2024-01-15
        '%Y-%m-%dT%H:%M:%S',           # 2024-01-15T10:30:00
        '%Y-%m-%dT%H:%M:%SZ',          # 2024-01-15T10:30:00Z
        '%Y-%m-%dT%H:%M:%S%z',         # 2024-01-15T10:30:00+00:00
        '%Y/%m/%d',                    # 2024/01/15
    ]

    # Clean the date string
    clean_date = date_str.strip()

    for fmt in formats:
        try:
            dt = datetime.strptime(clean_date[:len(fmt.replace('%', '').replace('-', '').replace(':', '').replace('T', '').replace('Z', '')) + 10], fmt)
            return int(dt.timestamp() * 1000)
        except ValueError:
            continue

    # Try ISO format parsing (Python 3.7+)
    try:
        # Remove timezone suffix for simpler parsing
        if clean_date.endswith('Z'):
            clean_date = clean_date[:-1]
        if '+' in clean_date:
            clean_date = clean_date.split('+')[0]

        dt = datetime.fromisoformat(clean_date)
        return int(dt.timestamp() * 1000)
    except ValueError:
        pass

    return None


def calculate_order_based_date(base_time: int, page: int, item_index: int, items_per_page: int = 20) -> int:
    """
    Calculate a descending timestamp based on page and item order.
    Page 1 items get higher timestamps than page 2, etc.
    Within a page, earlier items get higher timestamps.

    This ensures items without uploadDate still sort in website order.
    """
    # Each page is 1 hour apart, each item is 1 minute apart
    page_offset = (page - 1) * 3600 * 1000  # Hours in ms
    item_offset = item_index * 60 * 1000     # Minutes in ms

    return base_time - page_offset - item_offset


def fetch_url(url: str) -> Optional[str]:
    """Fetch URL with rate limiting."""
    global last_request_time

    elapsed = time.time() - last_request_time
    if elapsed < RATE_LIMIT_SEC:
        time.sleep(RATE_LIMIT_SEC - elapsed)

    try:
        response = session.get(url, timeout=30)
        last_request_time = time.time()

        if response.status_code == 404:
            return None
        if response.status_code != 200:
            print(f"  [WARN] HTTP {response.status_code}")
            return None

        return response.text
    except Exception as e:
        print(f"  [ERROR] {e}")
        return None


def init_database() -> sqlite3.Connection:
    """Initialize SQLite database with full schema + extra columns."""
    if DB_FILE.exists():
        os.remove(DB_FILE)

    conn = sqlite3.connect(DB_FILE)

    # Core schema matches ContentEntities.kt
    # Extra columns (writer, producer, etc.) will be ignored by Room but stored for future
    conn.executescript("""
        -- CachedMovie (core matches ContentEntities.kt + extras)
        CREATE TABLE cached_movies (
            id INTEGER PRIMARY KEY,
            title TEXT NOT NULL,
            posterUrl TEXT,
            farsilandUrl TEXT NOT NULL UNIQUE,
            description TEXT,
            year INTEGER,
            rating REAL,
            runtime INTEGER,
            director TEXT,
            cast TEXT,
            genres TEXT,
            dateAdded INTEGER NOT NULL,
            lastUpdated INTEGER NOT NULL,
            -- Extra columns (Room will ignore these)
            writer TEXT,
            producer TEXT,
            music TEXT,
            cinematographer TEXT,
            subtitles TEXT,
            viewCount INTEGER,
            uploadDate TEXT
        );
        CREATE INDEX idx_movies_farsilandUrl ON cached_movies(farsilandUrl);
        CREATE INDEX idx_movies_dateAdded ON cached_movies(dateAdded);

        -- CachedSeries (core matches ContentEntities.kt + extras)
        CREATE TABLE cached_series (
            id INTEGER PRIMARY KEY,
            title TEXT NOT NULL,
            posterUrl TEXT,
            backdropUrl TEXT,
            farsilandUrl TEXT NOT NULL UNIQUE,
            description TEXT,
            year INTEGER,
            rating REAL,
            totalSeasons INTEGER NOT NULL DEFAULT 1,
            totalEpisodes INTEGER NOT NULL DEFAULT 0,
            cast TEXT,
            genres TEXT,
            dateAdded INTEGER NOT NULL,
            lastUpdated INTEGER NOT NULL,
            -- Extra columns
            director TEXT,
            writer TEXT,
            producer TEXT,
            subtitles TEXT,
            viewCount INTEGER,
            uploadDate TEXT
        );
        CREATE INDEX idx_series_farsilandUrl ON cached_series(farsilandUrl);
        CREATE INDEX idx_series_dateAdded ON cached_series(dateAdded);

        -- CachedEpisode (matches ContentEntities.kt)
        CREATE TABLE cached_episodes (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            seriesId INTEGER NOT NULL,
            seriesTitle TEXT,
            episodeId INTEGER NOT NULL,
            season INTEGER NOT NULL,
            episode INTEGER NOT NULL,
            title TEXT NOT NULL,
            description TEXT,
            thumbnailUrl TEXT,
            farsilandUrl TEXT NOT NULL UNIQUE,
            airDate TEXT,
            runtime INTEGER,
            dateAdded INTEGER NOT NULL,
            lastUpdated INTEGER NOT NULL,
            FOREIGN KEY (seriesId) REFERENCES cached_series(id)
        );
        CREATE UNIQUE INDEX idx_episodes_series_season_ep ON cached_episodes(seriesId, season, episode);
        CREATE INDEX idx_episodes_farsilandUrl ON cached_episodes(farsilandUrl);
        CREATE INDEX idx_episodes_dateAdded ON cached_episodes(dateAdded);

        -- CachedGenre (matches ContentEntities.kt)
        CREATE TABLE cached_genres (
            id INTEGER PRIMARY KEY,
            name TEXT NOT NULL,
            slug TEXT NOT NULL
        );

        -- FTS4 for full-text search (Room-compatible)
        CREATE VIRTUAL TABLE cached_movies_fts USING fts4(
            title,
            content='cached_movies',
            tokenize=unicode61
        );

        CREATE VIRTUAL TABLE cached_series_fts USING fts4(
            title,
            content='cached_series',
            tokenize=unicode61
        );

        CREATE VIRTUAL TABLE cached_episodes_fts USING fts4(
            seriesTitle,
            title,
            content='cached_episodes',
            tokenize=unicode61
        );
    """)

    return conn


def generate_id(prefix: str, slug: str) -> int:
    """Generate consistent integer ID from slug."""
    hash_str = f"{prefix}:{slug}"
    return int(hashlib.md5(hash_str.encode()).hexdigest()[:8], 16)


def extract_poster_from_picture(element) -> Optional[str]:
    """Extract poster URL from IMVBox's <picture> element structure."""
    if not element:
        return None

    # Try data-img src first (actual image)
    data_img = element.select_one('data-img')
    if data_img and data_img.get('src'):
        return data_img.get('src')

    # Try data-src srcset
    data_src = element.select_one('data-src[srcset]')
    if data_src:
        srcset = data_src.get('srcset', '')
        if srcset:
            return srcset.split(',')[0].split()[0]

    # Fallback to regular img
    img = element.select_one('img')
    if img:
        return img.get('data-src') or img.get('src')

    return None


def scrape_movie_list_page(page: int) -> Tuple[List[Dict], bool]:
    """Scrape movies from listing page. Returns (movies, has_next_page)."""
    url = f"{BASE_URL}/movies?sort_by=new-releases" + (f"&page={page}" if page > 1 else "")
    html = fetch_url(url)
    if not html:
        return [], False

    soup = BeautifulSoup(html, 'lxml')
    movies = []

    # Find all movie cards
    cards = soup.select('div.card')

    for card in cards:
        link = card.select_one('a[href*="/movies/"]')
        if not link:
            continue

        href = link.get('href', '')
        # Skip category links
        if any(x in href for x in ['/trending', '/subtitled', '/feature-film', '/documentary', '/theatre', '?', '/movies?']):
            continue

        slug = href.rstrip('/').split('/')[-1]
        if not slug or slug == 'movies':
            continue

        title_elem = card.select_one('h5.card-title')
        title = title_elem.get_text(strip=True) if title_elem else slug.replace('-', ' ').title()

        picture = card.select_one('picture.card-img-top')
        poster_url = extract_poster_from_picture(picture)

        movies.append({
            'slug': slug,
            'title': title,
            'url': href if href.startswith('http') else f"{BASE_URL}/movies/{slug}",
            'poster_url': poster_url,
        })

    # Check for next page
    has_next = bool(soup.select_one(f'a[href*="page={page + 1}"]') or soup.select_one('a.page-link[rel="next"]'))

    return movies, has_next


def scrape_movie_details(url: str) -> Dict:
    """Scrape ALL available movie metadata from detail page."""
    details = {
        'description': None,
        'year': None,
        'rating': None,
        'runtime': None,
        'director': None,
        'cast': None,
        'genres': [],
        'poster_url': None,
        # Extra fields
        'writer': None,
        'producer': None,
        'music': None,
        'cinematographer': None,
        'subtitles': None,
        'view_count': None,
        'upload_date': None,
    }

    html = fetch_url(url)
    if not html:
        return details

    soup = BeautifulSoup(html, 'lxml')

    # Description from og:description
    meta = soup.select_one('meta[property="og:description"]')
    if meta:
        details['description'] = meta.get('content')

    # Poster from og:image
    meta = soup.select_one('meta[property="og:image"]')
    if meta:
        details['poster_url'] = meta.get('content')

    # Year from various sources
    year_elem = soup.select_one('span.year, span.moviepage-year, .movie-year')
    if year_elem:
        year_text = year_elem.get_text(strip=True)
        match = re.search(r'(\d{4})', year_text)
        if match:
            details['year'] = int(match.group(1))

    # Try JSON-LD for structured data
    json_ld = soup.select_one('script[type="application/ld+json"]')
    if json_ld:
        try:
            data = json.loads(json_ld.string)
            if isinstance(data, list):
                data = data[0]

            if 'duration' in data:
                match = re.match(r'PT(?:(\d+)H)?(?:(\d+)M)?', data['duration'])
                if match:
                    hours = int(match.group(1) or 0)
                    mins = int(match.group(2) or 0)
                    details['runtime'] = hours * 60 + mins

            if 'datePublished' in data and not details['year']:
                match = re.search(r'(\d{4})', data['datePublished'])
                if match:
                    details['year'] = int(match.group(1))

            if 'uploadDate' in data:
                details['upload_date'] = data['uploadDate']

            if 'aggregateRating' in data:
                rating = data['aggregateRating'].get('ratingValue')
                if rating:
                    details['rating'] = float(rating)

            if 'interactionStatistic' in data:
                stats = data['interactionStatistic']
                if isinstance(stats, dict):
                    details['view_count'] = stats.get('userInteractionCount')
                elif isinstance(stats, list):
                    for stat in stats:
                        if stat.get('interactionType') == 'http://schema.org/WatchAction':
                            details['view_count'] = stat.get('userInteractionCount')
                            break

            if 'director' in data:
                director = data['director']
                if isinstance(director, dict):
                    details['director'] = director.get('name')
                elif isinstance(director, list):
                    details['director'] = ', '.join(d.get('name', '') for d in director if isinstance(d, dict))

            if 'actor' in data:
                actors = data['actor']
                if isinstance(actors, list):
                    details['cast'] = ', '.join(a.get('name', '') for a in actors if isinstance(a, dict))

            if 'genre' in data:
                genres = data['genre']
                if isinstance(genres, list):
                    details['genres'] = genres
                elif isinstance(genres, str):
                    details['genres'] = [genres]
        except:
            pass

    # Extract crew from page text (look for patterns)
    page_text = soup.get_text()

    # Writer pattern
    writer_match = re.search(r'Writer[s]?[:\s]+([^\n]+)', page_text, re.IGNORECASE)
    if writer_match:
        details['writer'] = writer_match.group(1).strip()

    # Producer pattern
    producer_match = re.search(r'Producer[s]?[:\s]+([^\n]+)', page_text, re.IGNORECASE)
    if producer_match:
        details['producer'] = producer_match.group(1).strip()

    # Music/Composer pattern
    music_match = re.search(r'(?:Music|Composer)[:\s]+([^\n]+)', page_text, re.IGNORECASE)
    if music_match:
        details['music'] = music_match.group(1).strip()

    # Cinematographer/Photographer pattern
    cinematographer_match = re.search(r'(?:Cinematograph|Photograph)[ery]*[:\s]+([^\n]+)', page_text, re.IGNORECASE)
    if cinematographer_match:
        details['cinematographer'] = cinematographer_match.group(1).strip()

    # Subtitles - look for language indicators
    subtitle_langs = []
    for lang in ['English', 'German', 'Arabic', 'Turkish', 'French', 'Russian', 'Spanish', 'Farsi', 'Persian']:
        if f'{lang} subtitle' in page_text.lower() or f'{lang} sub' in page_text.lower():
            subtitle_langs.append(lang)

    # Also check for subtitle badges/icons
    subtitle_elements = soup.select('[class*="subtitle"], [class*="sub-"], .language-badge')
    for elem in subtitle_elements:
        text = elem.get_text(strip=True)
        if text and text not in subtitle_langs:
            subtitle_langs.append(text)

    if subtitle_langs:
        details['subtitles'] = ', '.join(subtitle_langs)

    # Genres from page
    if not details['genres']:
        genre_links = soup.select('a[href*="/genre/"]')
        for link in genre_links:
            genre = link.get_text(strip=True)
            if genre and genre not in details['genres']:
                details['genres'].append(genre)

    return details


def scrape_series_list_page(page: int) -> Tuple[List[Dict], bool]:
    """Scrape TV series from listing page."""
    url = f"{BASE_URL}/tv-series?sort_by=new-releases" + (f"&page={page}" if page > 1 else "")
    html = fetch_url(url)
    if not html:
        return [], False

    soup = BeautifulSoup(html, 'lxml')
    series_list = []

    cards = soup.select('div.card')

    for card in cards:
        link = card.select_one('a[href*="/shows/"]')
        if not link:
            continue

        href = link.get('href', '')
        if any(x in href for x in ['?', '/shows?', '/trending']):
            continue

        slug = href.rstrip('/').split('/')[-1]
        if not slug or slug in ['shows', 'tv-series']:
            continue

        title_elem = card.select_one('h5.card-title')
        title = title_elem.get_text(strip=True) if title_elem else slug.replace('-', ' ').title()

        picture = card.select_one('picture.card-img-top')
        poster_url = extract_poster_from_picture(picture)

        series_list.append({
            'slug': slug,
            'title': title,
            'url': f"{BASE_URL}/shows/{slug}",
            'poster_url': poster_url,
        })

    has_next = bool(soup.select_one(f'a[href*="page={page + 1}"]') or soup.select_one('a.page-link[rel="next"]'))

    return series_list, has_next


def scrape_series_details(url: str, slug: str) -> Dict:
    """Scrape series metadata and episodes."""
    details = {
        'description': None,
        'year': None,
        'rating': None,
        'poster_url': None,
        'backdrop_url': None,
        'cast': None,
        'genres': [],
        'total_seasons': 1,
        'episodes': [],
        # Extra fields
        'director': None,
        'writer': None,
        'producer': None,
        'subtitles': None,
        'view_count': None,
        'upload_date': None,
    }

    # Series pages redirect to /season-1, so try that directly
    season1_url = f"{url}/season-1"
    html = fetch_url(season1_url)
    if not html:
        html = fetch_url(url)
    if not html:
        return details

    soup = BeautifulSoup(html, 'lxml')

    # Description
    meta = soup.select_one('meta[property="og:description"]')
    if meta:
        details['description'] = meta.get('content')

    # Poster
    meta = soup.select_one('meta[property="og:image"]')
    if meta:
        details['poster_url'] = meta.get('content')

    # Year
    year_elem = soup.select_one('span.year, span.moviepage-year')
    if year_elem:
        match = re.search(r'(\d{4})', year_elem.get_text())
        if match:
            details['year'] = int(match.group(1))

    # JSON-LD data
    json_ld = soup.select_one('script[type="application/ld+json"]')
    if json_ld:
        try:
            data = json.loads(json_ld.string)
            if isinstance(data, list):
                data = data[0]

            if 'uploadDate' in data:
                details['upload_date'] = data['uploadDate']

            if 'interactionStatistic' in data:
                stats = data['interactionStatistic']
                if isinstance(stats, dict):
                    details['view_count'] = stats.get('userInteractionCount')

            if 'director' in data:
                director = data['director']
                if isinstance(director, dict):
                    details['director'] = director.get('name')
                elif isinstance(director, list):
                    details['director'] = ', '.join(d.get('name', '') for d in director if isinstance(d, dict))

            if 'actor' in data:
                actors = data['actor']
                if isinstance(actors, list):
                    details['cast'] = ', '.join(a.get('name', '') for a in actors if isinstance(a, dict))
        except:
            pass

    # Find max season number
    max_season = 1
    season_links = soup.select('a[href*="/season-"]')
    for link in season_links:
        match = re.search(r'/season-(\d+)', link.get('href', ''))
        if match:
            max_season = max(max_season, int(match.group(1)))
    details['total_seasons'] = max_season

    # Scrape episodes from all seasons
    for season_num in range(1, max_season + 1):
        if season_num > 1:
            season_url = f"{url}/season-{season_num}"
            html = fetch_url(season_url)
            if not html:
                continue
            soup = BeautifulSoup(html, 'lxml')

        seen_episodes = set()
        episode_links = soup.select('a[href*="/episode-"]')

        for link in episode_links:
            href = link.get('href', '')
            match = re.search(r'/season-(\d+)/episode-(\d+)', href)
            if match:
                ep_season = int(match.group(1))
                ep_num = int(match.group(2))

                if ep_season != season_num:
                    continue
                if ep_num in seen_episodes:
                    continue
                seen_episodes.add(ep_num)

                # Get thumbnail
                parent = link.parent
                thumbnail = None
                if parent:
                    picture = parent.select_one('picture')
                    thumbnail = extract_poster_from_picture(picture)

                details['episodes'].append({
                    'season': season_num,
                    'episode': ep_num,
                    'title': f"Episode {ep_num}",
                    'url': f"{url}/season-{season_num}/episode-{ep_num}",
                    'thumbnail_url': thumbnail,
                })

    details['episodes'].sort(key=lambda x: (x['season'], x['episode']))

    return details


def populate_fts(conn: sqlite3.Connection):
    """Populate FTS tables for search."""
    cursor = conn.cursor()

    # Movies FTS
    cursor.execute("DELETE FROM cached_movies_fts")
    cursor.execute("""
        INSERT INTO cached_movies_fts(rowid, title)
        SELECT id, title FROM cached_movies
    """)

    # Series FTS
    cursor.execute("DELETE FROM cached_series_fts")
    cursor.execute("""
        INSERT INTO cached_series_fts(rowid, title)
        SELECT id, title FROM cached_series
    """)

    # Episodes FTS
    cursor.execute("DELETE FROM cached_episodes_fts")
    cursor.execute("""
        INSERT INTO cached_episodes_fts(rowid, seriesTitle, title)
        SELECT id, seriesTitle, title FROM cached_episodes
    """)

    conn.commit()


def main():
    print("=" * 50)
    print("IMVBox.com Content Scraper v4")
    print("Schema: Core + Extra metadata columns")
    print("=" * 50 + "\n")

    conn = init_database()
    cursor = conn.cursor()

    now = int(time.time() * 1000)
    movie_count = 0
    series_count = 0
    episode_count = 0
    all_genres = {}
    seen_movies = set()
    seen_series = set()

    # ==================== SCRAPE MOVIES ====================
    print("[MOVIES] Scraping...\n")

    for page in range(1, MAX_PAGES + 1):
        print(f"  Page {page}...", end=' ', flush=True)

        movies, has_next = scrape_movie_list_page(page)

        if not movies:
            print("No movies found.")
            break

        new_count = 0
        for item_index, movie in enumerate(movies):
            if movie['slug'] in seen_movies:
                continue
            seen_movies.add(movie['slug'])

            details = scrape_movie_details(movie['url'])

            movie_id = generate_id('imvbox_movie', movie['slug'])
            poster_url = details['poster_url'] or movie['poster_url']

            # Use uploadDate if available, otherwise calculate from page order
            # This ensures sorting matches IMVBox website order
            date_added = parse_upload_date(details['upload_date'])
            if date_added is None:
                date_added = calculate_order_based_date(now, page, item_index)

            cursor.execute("""
                INSERT OR REPLACE INTO cached_movies
                (id, title, posterUrl, farsilandUrl, description, year, rating, runtime, director, cast, genres, dateAdded, lastUpdated,
                 writer, producer, music, cinematographer, subtitles, viewCount, uploadDate)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """, (
                movie_id,
                movie['title'],
                poster_url,
                movie['url'],
                details['description'],
                details['year'],
                details['rating'],
                details['runtime'],
                details['director'],
                details['cast'],
                ','.join(details['genres']) if details['genres'] else None,
                date_added,
                now,
                # Extra fields
                details['writer'],
                details['producer'],
                details['music'],
                details['cinematographer'],
                details['subtitles'],
                details['view_count'],
                details['upload_date'],
            ))

            for genre in details['genres']:
                if genre not in all_genres:
                    all_genres[genre] = generate_id('genre', genre.lower())

            movie_count += 1
            new_count += 1

        print(f"Found {new_count} movies")
        conn.commit()

        if not has_next:
            print("  No more pages.")
            break

    # ==================== SCRAPE TV SERIES ====================
    print(f"\n[TV SERIES] Scraping...\n")

    for page in range(1, MAX_PAGES + 1):
        print(f"  Page {page}...", end=' ', flush=True)

        series_list, has_next = scrape_series_list_page(page)

        if not series_list:
            print("No series found.")
            break

        new_count = 0
        for item_index, series in enumerate(series_list):
            if series['slug'] in seen_series:
                continue
            seen_series.add(series['slug'])

            print(f"\n    {series['title']}...", end=' ', flush=True)
            details = scrape_series_details(series['url'], series['slug'])

            series_id = generate_id('imvbox_series', series['slug'])
            poster_url = details['poster_url'] or series['poster_url']
            total_episodes = len(details['episodes'])

            # Use uploadDate if available, otherwise calculate from page order
            date_added = parse_upload_date(details['upload_date'])
            if date_added is None:
                date_added = calculate_order_based_date(now, page, item_index)

            cursor.execute("""
                INSERT OR REPLACE INTO cached_series
                (id, title, posterUrl, backdropUrl, farsilandUrl, description, year, rating, totalSeasons, totalEpisodes, cast, genres, dateAdded, lastUpdated,
                 director, writer, producer, subtitles, viewCount, uploadDate)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """, (
                series_id,
                series['title'],
                poster_url,
                details['backdrop_url'],
                series['url'],
                details['description'],
                details['year'],
                details['rating'],
                details['total_seasons'],
                total_episodes,
                details['cast'],
                ','.join(details['genres']) if details['genres'] else None,
                date_added,
                now,
                # Extra fields
                details['director'],
                details['writer'],
                details['producer'],
                details['subtitles'],
                details['view_count'],
                details['upload_date'],
            ))

            # Insert episodes - use series date_added as base, decrement per episode
            for ep_index, ep in enumerate(details['episodes']):
                ep_id = generate_id('imvbox_episode', f"{series['slug']}_s{ep['season']}e{ep['episode']}")

                # Episodes get slightly older dates (1 second apart) to maintain order
                ep_date_added = date_added - (ep_index * 1000)

                cursor.execute("""
                    INSERT OR REPLACE INTO cached_episodes
                    (seriesId, seriesTitle, episodeId, season, episode, title, description, thumbnailUrl, farsilandUrl, airDate, runtime, dateAdded, lastUpdated)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, (
                    series_id,
                    series['title'],
                    ep_id,
                    ep['season'],
                    ep['episode'],
                    ep['title'],
                    None,  # description
                    ep['thumbnail_url'],
                    ep['url'],
                    None,  # airDate
                    None,  # runtime
                    ep_date_added,
                    now,
                ))
                episode_count += 1

            print(f"({total_episodes} eps)")
            series_count += 1
            new_count += 1

        print(f"  Found {new_count} series")
        conn.commit()

        if not has_next:
            print("  No more pages.")
            break

    # ==================== SAVE GENRES ====================
    print("\n[GENRES] Saving...")
    for name, genre_id in all_genres.items():
        slug = name.lower().replace(' ', '-')
        cursor.execute("""
            INSERT OR REPLACE INTO cached_genres (id, name, slug)
            VALUES (?, ?, ?)
        """, (genre_id, name, slug))
    conn.commit()

    # ==================== POPULATE FTS ====================
    print("[FTS] Building search index...")
    populate_fts(conn)

    conn.close()

    print("\n" + "=" * 50)
    print("SCRAPING COMPLETE")
    print("=" * 50)
    print(f"Movies:   {movie_count}")
    print(f"Series:   {series_count}")
    print(f"Episodes: {episode_count}")
    print(f"Genres:   {len(all_genres)}")
    print(f"\nDatabase: {DB_FILE}")
    print("\nExtra columns stored: writer, producer, music, cinematographer, subtitles, viewCount, uploadDate")
    print("\nDate handling: Uses uploadDate from JSON-LD when available, page order otherwise")
    print("              (ensures app sorting matches IMVBox.com website order)")
    print("\nNext: Copy to app/src/main/assets/databases/imvbox_content.db")


if __name__ == '__main__':
    main()
