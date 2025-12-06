#!/usr/bin/env python3
"""
IMVBox.com Async Content Scraper v5

FAST scraping using concurrent async requests.
5-10x faster than sequential version.

Usage: python imvbox_scraper_async.py

Requirements:
    pip install aiohttp beautifulsoup4 lxml
"""

import os
import re
import json
import sqlite3
import hashlib
import asyncio
import aiohttp
from bs4 import BeautifulSoup
from typing import Dict, List, Optional, Tuple
from pathlib import Path
import time
from datetime import datetime

# Configuration
BASE_URL = "https://www.imvbox.com/en"
DB_FILE = Path(__file__).parent / "imvbox_content.db"
MAX_CONCURRENT = 10  # Max parallel requests
DELAY_BETWEEN_BATCHES = 0.5  # Seconds between batches
MAX_PAGES = 200

# Stats
stats = {'requests': 0, 'start_time': 0}


async def fetch_url(session: aiohttp.ClientSession, url: str, semaphore: asyncio.Semaphore) -> Optional[str]:
    """Fetch URL with concurrency limit."""
    async with semaphore:
        try:
            stats['requests'] += 1
            async with session.get(url, timeout=aiohttp.ClientTimeout(total=30)) as response:
                if response.status == 404:
                    return None
                if response.status != 200:
                    return None
                return await response.text()
        except Exception as e:
            return None


def init_database() -> sqlite3.Connection:
    """Initialize SQLite database."""
    if DB_FILE.exists():
        os.remove(DB_FILE)

    conn = sqlite3.connect(DB_FILE)
    conn.executescript("""
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
            director TEXT,
            writer TEXT,
            producer TEXT,
            subtitles TEXT,
            viewCount INTEGER,
            uploadDate TEXT
        );
        CREATE INDEX idx_series_farsilandUrl ON cached_series(farsilandUrl);

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

        CREATE TABLE cached_genres (
            id INTEGER PRIMARY KEY,
            name TEXT NOT NULL,
            slug TEXT NOT NULL
        );

        CREATE VIRTUAL TABLE cached_movies_fts USING fts4(title, content='cached_movies', tokenize=unicode61);
        CREATE VIRTUAL TABLE cached_series_fts USING fts4(title, content='cached_series', tokenize=unicode61);
        CREATE VIRTUAL TABLE cached_episodes_fts USING fts4(seriesTitle, title, content='cached_episodes', tokenize=unicode61);
    """)
    return conn


def generate_id(prefix: str, slug: str) -> int:
    hash_str = f"{prefix}:{slug}"
    return int(hashlib.md5(hash_str.encode()).hexdigest()[:8], 16)


def parse_date_to_timestamp(date_str: Optional[str], fallback: int) -> int:
    """Parse ISO date string to milliseconds timestamp. Returns fallback if parsing fails."""
    if not date_str:
        return fallback
    try:
        # Handle various ISO formats: "2024-01-15", "2024-01-15T10:30:00", etc.
        date_str = date_str.split('T')[0]  # Take just the date part
        dt = datetime.strptime(date_str, '%Y-%m-%d')
        return int(dt.timestamp() * 1000)
    except (ValueError, TypeError):
        return fallback


def extract_poster_from_picture(element) -> Optional[str]:
    if not element:
        return None
    data_img = element.select_one('data-img')
    if data_img and data_img.get('src'):
        return data_img.get('src')
    data_src = element.select_one('data-src[srcset]')
    if data_src:
        srcset = data_src.get('srcset', '')
        if srcset:
            return srcset.split(',')[0].split()[0]
    img = element.select_one('img')
    if img:
        return img.get('data-src') or img.get('src')
    return None


def parse_movie_list_page(html: str, page: int) -> Tuple[List[Dict], bool]:
    """Parse movies from listing page HTML."""
    soup = BeautifulSoup(html, 'lxml')
    movies = []

    for card in soup.select('div.card'):
        link = card.select_one('a[href*="/movies/"]')
        if not link:
            continue
        href = link.get('href', '')
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

    has_next = bool(soup.select_one(f'a[href*="page={page + 1}"]') or soup.select_one('a.page-link[rel="next"]'))
    return movies, has_next


def parse_movie_details(html: str) -> Dict:
    """Parse movie details from HTML."""
    details = {
        'description': None, 'year': None, 'rating': None, 'runtime': None,
        'director': None, 'cast': None, 'genres': [], 'poster_url': None,
        'writer': None, 'producer': None, 'music': None, 'cinematographer': None,
        'subtitles': None, 'view_count': None, 'upload_date': None,
    }

    soup = BeautifulSoup(html, 'lxml')
    page_text = soup.get_text()

    # Description from og:description
    meta = soup.select_one('meta[property="og:description"]')
    if meta:
        details['description'] = meta.get('content')

    # Poster from og:image
    meta = soup.select_one('meta[property="og:image"]')
    if meta:
        details['poster_url'] = meta.get('content')

    # Year from page text (format: "2022 | 90 min")
    year_match = re.search(r'\b(19\d{2}|20\d{2})\s*\|', page_text)
    if year_match:
        details['year'] = int(year_match.group(1))

    # JSON-LD for runtime, upload date, view count
    json_ld = soup.select_one('script[type="application/ld+json"]')
    if json_ld:
        try:
            data = json.loads(json_ld.string)
            if isinstance(data, list):
                data = data[0]

            if 'duration' in data:
                match = re.match(r'PT(?:(\d+)H)?(?:(\d+)M)?', data['duration'])
                if match:
                    details['runtime'] = int(match.group(1) or 0) * 60 + int(match.group(2) or 0)

            if 'uploadDate' in data:
                details['upload_date'] = data['uploadDate']

            if 'interactionStatistic' in data:
                stats_data = data['interactionStatistic']
                if isinstance(stats_data, dict):
                    details['view_count'] = stats_data.get('userInteractionCount')
        except:
            pass

    # Director from page text (format: "Director: Name (Persian)")
    director_match = re.search(r'Director[:\s]+([A-Za-z\s]+?)(?:\s*\(|$|\n)', page_text)
    if director_match:
        details['director'] = director_match.group(1).strip()

    # Cast - find person links
    cast_names = []
    for link in soup.select('a[href*="/people/"]')[:10]:
        name = link.get_text(strip=True)
        if name and re.match(r'^[A-Za-z\s]+$', name) and name not in cast_names:
            cast_names.append(name)
    if cast_names:
        details['cast'] = ', '.join(cast_names)

    # Genres from genre links
    for link in soup.select('a[href*="/genre/"]'):
        genre = link.get_text(strip=True)
        if genre and genre not in details['genres']:
            details['genres'].append(genre)

    return details


def parse_series_list_page(html: str, page: int) -> Tuple[List[Dict], bool]:
    """Parse series from listing page HTML."""
    soup = BeautifulSoup(html, 'lxml')
    series_list = []

    for card in soup.select('div.card'):
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


def parse_series_details(html: str, url: str) -> Dict:
    """Parse series details and episodes from HTML."""
    details = {
        'description': None, 'year': None, 'rating': None, 'poster_url': None,
        'backdrop_url': None, 'cast': None, 'genres': [], 'total_seasons': 1,
        'episodes': [], 'director': None, 'writer': None, 'producer': None,
        'subtitles': None, 'view_count': None, 'upload_date': None,
    }

    soup = BeautifulSoup(html, 'lxml')

    meta = soup.select_one('meta[property="og:description"]')
    if meta:
        details['description'] = meta.get('content')

    meta = soup.select_one('meta[property="og:image"]')
    if meta:
        details['poster_url'] = meta.get('content')

    year_elem = soup.select_one('span.year, span.moviepage-year')
    if year_elem:
        match = re.search(r'(\d{4})', year_elem.get_text())
        if match:
            details['year'] = int(match.group(1))

    # Find max season
    max_season = 1
    for link in soup.select('a[href*="/season-"]'):
        match = re.search(r'/season-(\d+)', link.get('href', ''))
        if match:
            max_season = max(max_season, int(match.group(1)))
    details['total_seasons'] = max_season

    # Episodes from current page
    seen = set()
    for link in soup.select('a[href*="/episode-"]'):
        href = link.get('href', '')
        match = re.search(r'/season-(\d+)/episode-(\d+)', href)
        if match:
            season = int(match.group(1))
            ep = int(match.group(2))
            key = (season, ep)
            if key not in seen:
                seen.add(key)
                parent = link.parent
                thumbnail = None
                if parent:
                    picture = parent.select_one('picture')
                    thumbnail = extract_poster_from_picture(picture)
                details['episodes'].append({
                    'season': season, 'episode': ep,
                    'title': f"Episode {ep}",
                    'url': f"{url}/season-{season}/episode-{ep}",
                    'thumbnail_url': thumbnail,
                })

    return details


async def scrape_movies(session: aiohttp.ClientSession, semaphore: asyncio.Semaphore, conn: sqlite3.Connection):
    """Scrape all movies using concurrent requests."""
    cursor = conn.cursor()
    now = int(time.time() * 1000)
    all_genres = {}
    seen_movies = set()
    total_movies = 0

    print("[MOVIES] Scraping with concurrency...\n")

    page = 1
    while page <= MAX_PAGES:
        # Fetch list page
        url = f"{BASE_URL}/movies?sort_by=new-releases" + (f"&page={page}" if page > 1 else "")
        html = await fetch_url(session, url, semaphore)
        if not html:
            break

        movies, has_next = parse_movie_list_page(html, page)
        if not movies:
            break

        # Filter out seen movies
        new_movies = [m for m in movies if m['slug'] not in seen_movies]
        for m in new_movies:
            seen_movies.add(m['slug'])

        # Fetch all detail pages concurrently
        detail_tasks = [fetch_url(session, m['url'], semaphore) for m in new_movies]
        detail_htmls = await asyncio.gather(*detail_tasks)

        # Process results
        batch_count = 0
        for movie, detail_html in zip(new_movies, detail_htmls):
            details = parse_movie_details(detail_html) if detail_html else {}

            movie_id = generate_id('imvbox_movie', movie['slug'])
            poster_url = details.get('poster_url') or movie['poster_url']

            # Use upload_date for dateAdded (for proper sorting by release date)
            upload_date_str = details.get('upload_date')
            date_added = parse_date_to_timestamp(upload_date_str, now)

            cursor.execute("""
                INSERT OR REPLACE INTO cached_movies
                (id, title, posterUrl, farsilandUrl, description, year, rating, runtime, director, cast, genres, dateAdded, lastUpdated,
                 writer, producer, music, cinematographer, subtitles, viewCount, uploadDate)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """, (
                movie_id, movie['title'], poster_url, movie['url'],
                details.get('description'), details.get('year'), details.get('rating'),
                details.get('runtime'), details.get('director'), details.get('cast'),
                ','.join(details.get('genres', [])) or None, date_added, now,
                details.get('writer'), details.get('producer'), details.get('music'),
                details.get('cinematographer'), details.get('subtitles'),
                details.get('view_count'), upload_date_str,
            ))

            for genre in details.get('genres', []):
                if genre not in all_genres:
                    all_genres[genre] = generate_id('genre', genre.lower())

            batch_count += 1
            total_movies += 1

        conn.commit()
        print(f"  Page {page}: {batch_count} movies (Total: {total_movies}, Requests: {stats['requests']})")

        if not has_next:
            break
        page += 1
        await asyncio.sleep(DELAY_BETWEEN_BATCHES)

    return all_genres, total_movies


async def scrape_series(session: aiohttp.ClientSession, semaphore: asyncio.Semaphore, conn: sqlite3.Connection):
    """Scrape all series using concurrent requests."""
    cursor = conn.cursor()
    now = int(time.time() * 1000)
    seen_series = set()
    total_series = 0
    total_episodes = 0

    print("\n[TV SERIES] Scraping with concurrency...\n")

    page = 1
    while page <= MAX_PAGES:
        url = f"{BASE_URL}/tv-series?sort_by=new-releases" + (f"&page={page}" if page > 1 else "")
        html = await fetch_url(session, url, semaphore)
        if not html:
            break

        series_list, has_next = parse_series_list_page(html, page)
        if not series_list:
            break

        new_series = [s for s in series_list if s['slug'] not in seen_series]
        for s in new_series:
            seen_series.add(s['slug'])

        # Fetch season-1 pages concurrently
        detail_tasks = [fetch_url(session, f"{s['url']}/season-1", semaphore) for s in new_series]
        detail_htmls = await asyncio.gather(*detail_tasks)

        batch_count = 0
        for series, detail_html in zip(new_series, detail_htmls):
            details = parse_series_details(detail_html, series['url']) if detail_html else {}

            series_id = generate_id('imvbox_series', series['slug'])
            poster_url = details.get('poster_url') or series['poster_url']
            ep_count = len(details.get('episodes', []))

            # Use upload_date for dateAdded (for proper sorting by release date)
            upload_date_str = details.get('upload_date')
            date_added = parse_date_to_timestamp(upload_date_str, now)

            cursor.execute("""
                INSERT OR REPLACE INTO cached_series
                (id, title, posterUrl, backdropUrl, farsilandUrl, description, year, rating, totalSeasons, totalEpisodes, cast, genres, dateAdded, lastUpdated,
                 director, writer, producer, subtitles, viewCount, uploadDate)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """, (
                series_id, series['title'], poster_url, details.get('backdrop_url'),
                series['url'], details.get('description'), details.get('year'),
                details.get('rating'), details.get('total_seasons', 1), ep_count,
                details.get('cast'), ','.join(details.get('genres', [])) or None, date_added, now,
                details.get('director'), details.get('writer'), details.get('producer'),
                details.get('subtitles'), details.get('view_count'), upload_date_str,
            ))

            for ep in details.get('episodes', []):
                ep_id = generate_id('imvbox_episode', f"{series['slug']}_s{ep['season']}e{ep['episode']}")
                cursor.execute("""
                    INSERT OR REPLACE INTO cached_episodes
                    (seriesId, seriesTitle, episodeId, season, episode, title, description, thumbnailUrl, farsilandUrl, airDate, runtime, dateAdded, lastUpdated)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, (
                    series_id, series['title'], ep_id, ep['season'], ep['episode'],
                    ep['title'], None, ep['thumbnail_url'], ep['url'], None, None, date_added, now,
                ))
                total_episodes += 1

            batch_count += 1
            total_series += 1

        conn.commit()
        print(f"  Page {page}: {batch_count} series (Total: {total_series}, Episodes: {total_episodes})")

        if not has_next:
            break
        page += 1
        await asyncio.sleep(DELAY_BETWEEN_BATCHES)

    return total_series, total_episodes


def populate_fts(conn: sqlite3.Connection):
    cursor = conn.cursor()
    cursor.execute("DELETE FROM cached_movies_fts")
    cursor.execute("INSERT INTO cached_movies_fts(rowid, title) SELECT id, title FROM cached_movies")
    cursor.execute("DELETE FROM cached_series_fts")
    cursor.execute("INSERT INTO cached_series_fts(rowid, title) SELECT id, title FROM cached_series")
    cursor.execute("DELETE FROM cached_episodes_fts")
    cursor.execute("INSERT INTO cached_episodes_fts(rowid, seriesTitle, title) SELECT id, seriesTitle, title FROM cached_episodes")
    conn.commit()


async def main():
    print("=" * 50)
    print("IMVBox.com ASYNC Scraper v5")
    print(f"Concurrency: {MAX_CONCURRENT} parallel requests")
    print("=" * 50 + "\n")

    stats['start_time'] = time.time()

    conn = init_database()
    semaphore = asyncio.Semaphore(MAX_CONCURRENT)

    headers = {
        'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120.0.0.0 Safari/537.36',
        'Accept': 'text/html,application/xhtml+xml',
    }

    async with aiohttp.ClientSession(headers=headers) as session:
        all_genres, movie_count = await scrape_movies(session, semaphore, conn)
        series_count, episode_count = await scrape_series(session, semaphore, conn)

    # Save genres
    cursor = conn.cursor()
    for name, genre_id in all_genres.items():
        slug = name.lower().replace(' ', '-')
        cursor.execute("INSERT OR REPLACE INTO cached_genres (id, name, slug) VALUES (?, ?, ?)", (genre_id, name, slug))
    conn.commit()

    print("\n[FTS] Building search index...")
    populate_fts(conn)
    conn.close()

    elapsed = time.time() - stats['start_time']
    print("\n" + "=" * 50)
    print("SCRAPING COMPLETE")
    print("=" * 50)
    print(f"Movies:   {movie_count}")
    print(f"Series:   {series_count}")
    print(f"Episodes: {episode_count}")
    print(f"Genres:   {len(all_genres)}")
    print(f"Requests: {stats['requests']}")
    print(f"Time:     {elapsed:.1f} seconds ({elapsed/60:.1f} min)")
    print(f"\nDatabase: {DB_FILE}")


if __name__ == '__main__':
    asyncio.run(main())
