#!/usr/bin/env python3
"""
IMVBox.com Content Scraper v3

Properly scrapes movies and TV series from IMVBox.com with full metadata.
Schema matches Android ContentDatabase exactly.

Usage: python imvbox_scraper.py

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
    """Initialize SQLite database with exact ContentDatabase schema."""
    if DB_FILE.exists():
        os.remove(DB_FILE)

    conn = sqlite3.connect(DB_FILE)

    # Schema must match ContentEntities.kt EXACTLY
    conn.executescript("""
        -- CachedMovie (matches ContentEntities.kt)
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
            lastUpdated INTEGER NOT NULL
        );
        CREATE INDEX idx_movies_farsilandUrl ON cached_movies(farsilandUrl);
        CREATE INDEX idx_movies_dateAdded ON cached_movies(dateAdded);

        -- CachedSeries (matches ContentEntities.kt)
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
            lastUpdated INTEGER NOT NULL
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
    """Scrape detailed movie metadata from detail page."""
    details = {
        'description': None,
        'year': None,
        'rating': None,
        'runtime': None,
        'director': None,
        'cast': None,
        'genres': [],
        'poster_url': None,
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

            if 'aggregateRating' in data:
                rating = data['aggregateRating'].get('ratingValue')
                if rating:
                    details['rating'] = float(rating)

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
    print("IMVBox.com Content Scraper v3")
    print("Schema: Matches Android ContentDatabase")
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
        for movie in movies:
            if movie['slug'] in seen_movies:
                continue
            seen_movies.add(movie['slug'])

            details = scrape_movie_details(movie['url'])

            movie_id = generate_id('imvbox_movie', movie['slug'])
            poster_url = details['poster_url'] or movie['poster_url']

            cursor.execute("""
                INSERT OR REPLACE INTO cached_movies
                (id, title, posterUrl, farsilandUrl, description, year, rating, runtime, director, cast, genres, dateAdded, lastUpdated)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
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
                now,
                now,
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
        for series in series_list:
            if series['slug'] in seen_series:
                continue
            seen_series.add(series['slug'])

            print(f"\n    {series['title']}...", end=' ', flush=True)
            details = scrape_series_details(series['url'], series['slug'])

            series_id = generate_id('imvbox_series', series['slug'])
            poster_url = details['poster_url'] or series['poster_url']
            total_episodes = len(details['episodes'])

            cursor.execute("""
                INSERT OR REPLACE INTO cached_series
                (id, title, posterUrl, backdropUrl, farsilandUrl, description, year, rating, totalSeasons, totalEpisodes, cast, genres, dateAdded, lastUpdated)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
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
                now,
                now,
            ))

            # Insert episodes
            for ep in details['episodes']:
                ep_id = generate_id('imvbox_episode', f"{series['slug']}_s{ep['season']}e{ep['episode']}")

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
                    now,
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
    print("\nNext: Copy to app/src/main/assets/databases/imvbox_content.db")


if __name__ == '__main__':
    main()
