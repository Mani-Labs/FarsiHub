#!/usr/bin/env python3
"""
Farsiland Content Database Rebuilder
=====================================
Fetches ALL content from Farsiland WordPress API and builds a fresh SQLite database.

Usage:
    python rebuild_farsiland_db.py

Output:
    app/src/main/assets/databases/farsiland_content.db
"""

import sqlite3
import requests
import time
import re
import json
from pathlib import Path
from datetime import datetime
from html import unescape

# Configuration
BASE_URL = "https://farsiland.com/wp-json/wp/v2"
OUTPUT_DB = Path(__file__).parent.parent / "app/src/main/assets/databases/farsiland_content.db"
PER_PAGE = 100  # Max allowed by WordPress
SERIES_PER_PAGE = 25  # Smaller batches for series (more reliable)
REQUEST_DELAY = 0.5  # Seconds between requests to avoid rate limiting
MAX_RETRIES = 5
DEFAULT_TIMEOUT = 30
SERIES_TIMEOUT = 60  # Longer timeout for series

# Headers to mimic browser
HEADERS = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
    "Accept": "application/json",
}

session = requests.Session()
session.headers.update(HEADERS)


def create_database(db_path: Path) -> sqlite3.Connection:
    """Create fresh database with correct schema."""
    # Remove existing database
    if db_path.exists():
        db_path.unlink()
        print(f"Removed existing database: {db_path}")

    # Ensure directory exists
    db_path.parent.mkdir(parents=True, exist_ok=True)

    conn = sqlite3.connect(str(db_path))
    cursor = conn.cursor()

    # Create tables matching Room schema
    cursor.executescript("""
        -- Movies table
        CREATE TABLE cached_movies (
            id INTEGER PRIMARY KEY NOT NULL,
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
        CREATE UNIQUE INDEX index_cached_movies_farsilandUrl ON cached_movies(farsilandUrl);

        -- Series table
        CREATE TABLE cached_series (
            id INTEGER PRIMARY KEY NOT NULL,
            title TEXT NOT NULL,
            posterUrl TEXT,
            backdropUrl TEXT,
            farsilandUrl TEXT NOT NULL UNIQUE,
            description TEXT,
            year INTEGER,
            rating REAL,
            totalSeasons INTEGER NOT NULL,
            totalEpisodes INTEGER NOT NULL,
            cast TEXT,
            genres TEXT,
            dateAdded INTEGER NOT NULL,
            lastUpdated INTEGER NOT NULL
        );
        CREATE UNIQUE INDEX index_cached_series_farsilandUrl ON cached_series(farsilandUrl);

        -- Episodes table
        CREATE TABLE cached_episodes (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
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
            UNIQUE(seriesId, season, episode)
        );
        CREATE UNIQUE INDEX index_cached_episodes_seriesId_season_episode ON cached_episodes(seriesId, season, episode);
        CREATE UNIQUE INDEX index_cached_episodes_farsilandUrl ON cached_episodes(farsilandUrl);
        CREATE INDEX index_cached_episodes_dateAdded ON cached_episodes(dateAdded);

        -- Genres table
        CREATE TABLE cached_genres (
            id INTEGER PRIMARY KEY NOT NULL,
            name TEXT NOT NULL,
            slug TEXT NOT NULL
        );

        -- FTS4 tables for fast search (Room external content FTS format)
        CREATE VIRTUAL TABLE `cached_movies_fts` USING FTS4(`title` TEXT NOT NULL, content=`cached_movies`);
        CREATE VIRTUAL TABLE `cached_series_fts` USING FTS4(`title` TEXT NOT NULL, content=`cached_series`);
        CREATE VIRTUAL TABLE `cached_episodes_fts` USING FTS4(`seriesTitle` TEXT, `title` TEXT NOT NULL, content=`cached_episodes`);

        -- Room FTS sync triggers (movies)
        CREATE TRIGGER room_fts_content_sync_cached_movies_fts_BEFORE_UPDATE BEFORE UPDATE ON `cached_movies` BEGIN DELETE FROM `cached_movies_fts` WHERE `docid`=OLD.`rowid`; END;
        CREATE TRIGGER room_fts_content_sync_cached_movies_fts_BEFORE_DELETE BEFORE DELETE ON `cached_movies` BEGIN DELETE FROM `cached_movies_fts` WHERE `docid`=OLD.`rowid`; END;
        CREATE TRIGGER room_fts_content_sync_cached_movies_fts_AFTER_UPDATE AFTER UPDATE ON `cached_movies` BEGIN INSERT INTO `cached_movies_fts`(`docid`, `title`) VALUES (NEW.`rowid`, NEW.`title`); END;
        CREATE TRIGGER room_fts_content_sync_cached_movies_fts_AFTER_INSERT AFTER INSERT ON `cached_movies` BEGIN INSERT INTO `cached_movies_fts`(`docid`, `title`) VALUES (NEW.`rowid`, NEW.`title`); END;

        -- Room FTS sync triggers (series)
        CREATE TRIGGER room_fts_content_sync_cached_series_fts_BEFORE_UPDATE BEFORE UPDATE ON `cached_series` BEGIN DELETE FROM `cached_series_fts` WHERE `docid`=OLD.`rowid`; END;
        CREATE TRIGGER room_fts_content_sync_cached_series_fts_BEFORE_DELETE BEFORE DELETE ON `cached_series` BEGIN DELETE FROM `cached_series_fts` WHERE `docid`=OLD.`rowid`; END;
        CREATE TRIGGER room_fts_content_sync_cached_series_fts_AFTER_UPDATE AFTER UPDATE ON `cached_series` BEGIN INSERT INTO `cached_series_fts`(`docid`, `title`) VALUES (NEW.`rowid`, NEW.`title`); END;
        CREATE TRIGGER room_fts_content_sync_cached_series_fts_AFTER_INSERT AFTER INSERT ON `cached_series` BEGIN INSERT INTO `cached_series_fts`(`docid`, `title`) VALUES (NEW.`rowid`, NEW.`title`); END;

        -- Room FTS sync triggers (episodes)
        CREATE TRIGGER room_fts_content_sync_cached_episodes_fts_BEFORE_UPDATE BEFORE UPDATE ON `cached_episodes` BEGIN DELETE FROM `cached_episodes_fts` WHERE `docid`=OLD.`rowid`; END;
        CREATE TRIGGER room_fts_content_sync_cached_episodes_fts_BEFORE_DELETE BEFORE DELETE ON `cached_episodes` BEGIN DELETE FROM `cached_episodes_fts` WHERE `docid`=OLD.`rowid`; END;
        CREATE TRIGGER room_fts_content_sync_cached_episodes_fts_AFTER_UPDATE AFTER UPDATE ON `cached_episodes` BEGIN INSERT INTO `cached_episodes_fts`(`docid`, `seriesTitle`, `title`) VALUES (NEW.`rowid`, NEW.`seriesTitle`, NEW.`title`); END;
        CREATE TRIGGER room_fts_content_sync_cached_episodes_fts_AFTER_INSERT AFTER INSERT ON `cached_episodes` BEGIN INSERT INTO `cached_episodes_fts`(`docid`, `seriesTitle`, `title`) VALUES (NEW.`rowid`, NEW.`seriesTitle`, NEW.`title`); END;

        -- Room metadata
        CREATE TABLE room_master_table (
            id INTEGER PRIMARY KEY,
            identity_hash TEXT
        );
        INSERT INTO room_master_table (id, identity_hash) VALUES (42, 'farsiland_content_v3');
    """)

    conn.commit()
    print(f"Created database schema: {db_path}")
    return conn


def fetch_paginated(endpoint: str, params: dict = None, per_page: int = None, timeout: int = None) -> list:
    """Fetch all pages from a paginated WordPress API endpoint."""
    all_items = []
    page = 1
    params = params or {}
    per_page = per_page or PER_PAGE
    timeout = timeout or DEFAULT_TIMEOUT

    while True:
        params["page"] = page
        params["per_page"] = per_page
        params["_embed"] = "true"  # Include embedded media

        url = f"{BASE_URL}/{endpoint}"

        for attempt in range(MAX_RETRIES):
            try:
                response = session.get(url, params=params, timeout=timeout)

                if response.status_code == 400:
                    # No more pages
                    return all_items

                response.raise_for_status()
                items = response.json()

                if not items:
                    return all_items

                all_items.extend(items)

                # Get total pages from headers
                total_pages = int(response.headers.get("X-WP-TotalPages", 1))
                total_items = int(response.headers.get("X-WP-Total", len(all_items)))

                print(f"  Page {page}/{total_pages}: fetched {len(items)} items (total: {len(all_items)}/{total_items})")

                if page >= total_pages:
                    return all_items

                page += 1
                time.sleep(REQUEST_DELAY)
                break

            except requests.exceptions.RequestException as e:
                print(f"  Error on page {page}, attempt {attempt + 1}: {e}")
                if attempt < MAX_RETRIES - 1:
                    time.sleep(2 ** attempt)  # Exponential backoff
                else:
                    print(f"  Giving up on page {page}")
                    return all_items

    return all_items


def parse_date_to_timestamp(date_str: str) -> int:
    """Convert WordPress date to Unix timestamp (milliseconds).

    Handles dates before 1970 (Unix epoch) which fail on Windows with timestamp().
    """
    try:
        # WordPress format: "2025-11-25T18:45:49"
        dt = datetime.fromisoformat(date_str.replace("Z", "+00:00"))
        # Use calendar.timegm for cross-platform support including pre-1970 dates
        import calendar
        # Handle timezone-aware datetime
        if dt.tzinfo is not None:
            # Convert to UTC timestamp
            utc_tuple = dt.utctimetuple()
            return int(calendar.timegm(utc_tuple) * 1000)
        else:
            # Naive datetime - treat as local time
            return int(calendar.timegm(dt.timetuple()) * 1000)
    except Exception as e:
        print(f"  Warning: Failed to parse date '{date_str}': {e}")
        return int(datetime.now().timestamp() * 1000)


def extract_year(date_str: str) -> int:
    """Extract year from date string."""
    try:
        return int(date_str[:4])
    except:
        return None


def clean_html(html: str) -> str:
    """Remove HTML tags and decode entities."""
    if not html:
        return None
    # Remove HTML tags
    text = re.sub(r'<[^>]+>', '', html)
    # Decode HTML entities
    text = unescape(text)
    # Clean whitespace
    text = ' '.join(text.split())
    return text.strip() if text else None


def get_poster_url(item: dict) -> str:
    """Extract poster URL from WordPress item."""
    # Try embedded media first
    embedded = item.get("_embedded", {})
    featured = embedded.get("wp:featuredmedia", [])
    if featured and len(featured) > 0:
        media = featured[0]
        # Try different sizes
        sizes = media.get("media_details", {}).get("sizes", {})
        for size in ["medium_large", "medium", "large", "full"]:
            if size in sizes:
                return sizes[size].get("source_url")
        return media.get("source_url")

    # Try yoast og:image
    yoast = item.get("yoast_head_json", {})
    og_images = yoast.get("og_image", [])
    if og_images:
        return og_images[0].get("url")

    return None


def extract_genres(item: dict, genre_map: dict) -> str:
    """Extract genre names from item."""
    genre_ids = item.get("genres", [])
    genre_names = [genre_map.get(gid, "") for gid in genre_ids if gid in genre_map]
    return ", ".join(filter(None, genre_names)) if genre_names else None


def parse_episode_info(title: str) -> tuple:
    """Parse series name, season, episode from title.

    Examples:
        "Eshghe Abadi SE02 EP47" -> ("Eshghe Abadi", 2, 47)
        "Robate Salibi EP05" -> ("Robate Salibi", 1, 5)
        "Nime Shab S5E16" -> ("Nime Shab", 5, 16)
    """
    # Pattern: "Series Name SE## EP##" or "Series Name S##E##"
    pattern1 = re.compile(r"(.+?)\s+SE?(\d+)\s*EP?(\d+)", re.IGNORECASE)
    # Pattern: "Series Name EP##"
    pattern2 = re.compile(r"(.+?)\s+EP(\d+)", re.IGNORECASE)
    # Pattern: "Series Name S#E##"
    pattern3 = re.compile(r"(.+?)\s+S(\d+)E(\d+)", re.IGNORECASE)

    match = pattern1.match(title)
    if match:
        return match.group(1).strip(), int(match.group(2)), int(match.group(3))

    match = pattern3.match(title)
    if match:
        return match.group(1).strip(), int(match.group(2)), int(match.group(3))

    match = pattern2.match(title)
    if match:
        return match.group(1).strip(), 1, int(match.group(2))

    return title, 1, 1


def normalize_series_title(title: str) -> str:
    """Normalize series title for matching."""
    # Remove common suffixes and clean
    normalized = title.lower().strip()
    normalized = re.sub(r'\s+', '-', normalized)
    normalized = re.sub(r'[^\w\-]', '', normalized)
    return normalized


def fetch_genres(conn: sqlite3.Connection) -> dict:
    """Fetch and store all genres, return ID->name mapping."""
    print("\n=== Fetching Genres ===")

    genres = fetch_paginated("genres")
    genre_map = {}

    cursor = conn.cursor()
    for genre in genres:
        genre_id = genre["id"]
        name = genre["name"]
        slug = genre["slug"]
        genre_map[genre_id] = name

        cursor.execute(
            "INSERT OR REPLACE INTO cached_genres (id, name, slug) VALUES (?, ?, ?)",
            (genre_id, name, slug)
        )

    conn.commit()
    print(f"Stored {len(genres)} genres")
    return genre_map


def fetch_movies(conn: sqlite3.Connection, genre_map: dict):
    """Fetch and store all movies."""
    print("\n=== Fetching Movies ===")

    movies = fetch_paginated("movies")
    cursor = conn.cursor()
    count = 0

    for movie in movies:
        try:
            movie_id = movie["id"]
            title = movie["title"]["rendered"]
            poster_url = get_poster_url(movie)
            url = movie["link"]
            description = clean_html(movie.get("content", {}).get("rendered"))
            year = extract_year(movie.get("date", ""))
            genres = extract_genres(movie, genre_map)
            date_added = parse_date_to_timestamp(movie.get("date", ""))
            last_updated = parse_date_to_timestamp(movie.get("modified", movie.get("date", "")))

            cursor.execute("""
                INSERT OR REPLACE INTO cached_movies
                (id, title, posterUrl, farsilandUrl, description, year, rating, runtime, director, cast, genres, dateAdded, lastUpdated)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """, (movie_id, title, poster_url, url, description, year, None, None, None, None, genres, date_added, last_updated))
            count += 1

        except Exception as e:
            print(f"  Error processing movie {movie.get('id')}: {e}")

    conn.commit()
    print(f"Stored {count} movies")

    # Populate FTS (external content FTS - manual population)
    cursor.execute("INSERT INTO cached_movies_fts(docid, title) SELECT rowid, title FROM cached_movies")
    conn.commit()


def fetch_series(conn: sqlite3.Connection, genre_map: dict) -> dict:
    """Fetch and store all TV series, return title->id mapping."""
    print("\n=== Fetching TV Series (smaller batches, longer timeout) ===")

    series_list = fetch_paginated("tvshows", per_page=SERIES_PER_PAGE, timeout=SERIES_TIMEOUT)
    cursor = conn.cursor()
    series_map = {}  # normalized_title -> id
    count = 0

    for series in series_list:
        try:
            series_id = series["id"]
            title = series["title"]["rendered"]
            poster_url = get_poster_url(series)
            url = series["link"]
            description = clean_html(series.get("content", {}).get("rendered"))
            year = extract_year(series.get("date", ""))
            genres = extract_genres(series, genre_map)
            date_added = parse_date_to_timestamp(series.get("date", ""))
            last_updated = parse_date_to_timestamp(series.get("modified", series.get("date", "")))

            # Extract slug from URL for matching
            slug = url.rstrip("/").split("/")[-1]

            cursor.execute("""
                INSERT OR REPLACE INTO cached_series
                (id, title, posterUrl, backdropUrl, farsilandUrl, description, year, rating, totalSeasons, totalEpisodes, cast, genres, dateAdded, lastUpdated)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """, (series_id, title, poster_url, None, url, description, year, None, 0, 0, None, genres, date_added, last_updated))

            # Build mapping for episode linking
            series_map[normalize_series_title(title)] = series_id
            series_map[slug] = series_id
            series_map[title.lower()] = series_id

            count += 1

        except Exception as e:
            print(f"  Error processing series {series.get('id')}: {e}")

    conn.commit()
    print(f"Stored {count} series")

    # Populate FTS (external content FTS - manual population)
    cursor.execute("INSERT INTO cached_series_fts(docid, title) SELECT rowid, title FROM cached_series")
    conn.commit()

    return series_map


def fetch_episodes(conn: sqlite3.Connection, series_map: dict):
    """Fetch and store all episodes."""
    print("\n=== Fetching Episodes ===")

    episodes = fetch_paginated("episodes")
    cursor = conn.cursor()
    count = 0
    orphaned = 0
    series_episode_counts = {}  # series_id -> count

    for ep in episodes:
        try:
            ep_id = ep["id"]
            title = ep["title"]["rendered"]
            url = ep["link"]
            description = clean_html(ep.get("content", {}).get("rendered"))
            thumbnail_url = get_poster_url(ep)
            date_added = parse_date_to_timestamp(ep.get("date", ""))
            last_updated = parse_date_to_timestamp(ep.get("modified", ep.get("date", "")))
            air_date = ep.get("date", "")[:10]

            # Parse episode info from title
            series_title, season, episode_num = parse_episode_info(title)

            # Find series ID
            normalized = normalize_series_title(series_title)
            series_id = series_map.get(normalized) or series_map.get(series_title.lower()) or 0

            if series_id == 0:
                orphaned += 1
                # Try to extract from URL
                # e.g., /episodes/eshghe-abadi-se02-ep47/ -> eshghe-abadi
                url_match = re.search(r'/episodes/([^/]+?)(?:-se?\d+)?(?:-ep?\d+)?/?$', url, re.IGNORECASE)
                if url_match:
                    slug = url_match.group(1)
                    series_id = series_map.get(slug, 0)

            # Get series poster as fallback thumbnail
            if not thumbnail_url and series_id:
                cursor.execute("SELECT posterUrl FROM cached_series WHERE id = ?", (series_id,))
                row = cursor.fetchone()
                if row:
                    thumbnail_url = row[0]

            cursor.execute("""
                INSERT OR REPLACE INTO cached_episodes
                (seriesId, seriesTitle, episodeId, season, episode, title, description, thumbnailUrl, farsilandUrl, airDate, runtime, dateAdded, lastUpdated)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """, (series_id, series_title, ep_id, season, episode_num, title, description, thumbnail_url, url, air_date, None, date_added, last_updated))

            count += 1

            # Track episode counts per series
            if series_id:
                series_episode_counts[series_id] = series_episode_counts.get(series_id, 0) + 1

        except sqlite3.IntegrityError:
            # Duplicate episode (same series, season, episode)
            pass
        except Exception as e:
            print(f"  Error processing episode {ep.get('id')}: {e}")

    conn.commit()
    print(f"Stored {count} episodes ({orphaned} orphaned without series)")

    # Update series episode counts
    print("Updating series episode counts...")
    for series_id, ep_count in series_episode_counts.items():
        cursor.execute("UPDATE cached_series SET totalEpisodes = ? WHERE id = ?", (ep_count, series_id))
    conn.commit()

    # Populate FTS (external content FTS - manual population)
    cursor.execute("INSERT INTO cached_episodes_fts(docid, seriesTitle, title) SELECT rowid, seriesTitle, title FROM cached_episodes")
    conn.commit()


def verify_database(conn: sqlite3.Connection):
    """Print database statistics."""
    cursor = conn.cursor()

    print("\n=== Database Statistics ===")

    cursor.execute("SELECT COUNT(*) FROM cached_movies")
    print(f"Movies: {cursor.fetchone()[0]}")

    cursor.execute("SELECT COUNT(*) FROM cached_series")
    print(f"Series: {cursor.fetchone()[0]}")

    cursor.execute("SELECT COUNT(*) FROM cached_episodes")
    print(f"Episodes: {cursor.fetchone()[0]}")

    cursor.execute("SELECT COUNT(*) FROM cached_genres")
    print(f"Genres: {cursor.fetchone()[0]}")

    cursor.execute("SELECT COUNT(*) FROM cached_episodes WHERE seriesId = 0")
    orphaned = cursor.fetchone()[0]
    if orphaned:
        print(f"Orphaned episodes: {orphaned}")

    # Latest content
    print("\nLatest Episodes:")
    cursor.execute("""
        SELECT seriesTitle, season, episode, title
        FROM cached_episodes
        ORDER BY dateAdded DESC
        LIMIT 5
    """)
    for row in cursor.fetchall():
        print(f"  {row[0]} S{row[1]}E{row[2]}: {row[3]}")


def main():
    print("=" * 60)
    print("Farsiland Content Database Rebuilder")
    print("=" * 60)
    print(f"Output: {OUTPUT_DB}")
    print(f"API Base: {BASE_URL}")
    print()

    start_time = time.time()

    # Create fresh database
    conn = create_database(OUTPUT_DB)

    try:
        # Fetch all content
        genre_map = fetch_genres(conn)
        fetch_movies(conn, genre_map)
        series_map = fetch_series(conn, genre_map)
        fetch_episodes(conn, series_map)

        # Verify
        verify_database(conn)

        elapsed = time.time() - start_time
        print(f"\nCompleted in {elapsed:.1f} seconds")
        print(f"Database saved to: {OUTPUT_DB}")

    finally:
        conn.close()


if __name__ == "__main__":
    main()
