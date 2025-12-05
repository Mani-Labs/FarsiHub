#!/usr/bin/env python3
"""
Convert Farsiplex.db to Android app database format

This script converts the FarsiPlex database schema to match
the Android app's ContentDatabase schema (same as Farsiland).

FIX: Added dateAdded field for correct "Latest Episodes" ordering
"""

import sqlite3
import sys
import os
import time


def convert_database(input_db: str, output_db: str):
    """Convert FarsiPlex database to app format"""

    print(f"Converting {input_db} to {output_db}...")

    # Remove output if exists
    if os.path.exists(output_db):
        os.remove(output_db)

    # Connect to both databases
    source_conn = sqlite3.connect(input_db)
    dest_conn = sqlite3.connect(output_db)

    source_cursor = source_conn.cursor()
    dest_cursor = dest_conn.cursor()

    # Create app database schema (matching Android ContentDatabase)
    create_app_schema(dest_cursor)

    # Convert movies
    print("Converting movies...")
    convert_movies(source_cursor, dest_cursor)

    # Convert TV shows and episodes
    print("Converting TV shows and episodes...")
    convert_tvshows_and_episodes(source_cursor, dest_cursor)

    # Convert genres
    print("Converting genres...")
    convert_genres(source_cursor, dest_cursor)

    # Commit and close
    dest_conn.commit()
    source_conn.close()
    dest_conn.close()

    print(f"Conversion complete: {output_db}")
    print_stats(output_db)


def create_app_schema(cursor):
    """Create Android app database schema matching ContentDatabase entities"""

    # CachedMovie table - matches ContentEntities.kt
    cursor.execute("""
        CREATE TABLE IF NOT EXISTS cached_movies (
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
        )
    """)

    # CachedSeries table - matches ContentEntities.kt
    cursor.execute("""
        CREATE TABLE IF NOT EXISTS cached_series (
            id INTEGER PRIMARY KEY NOT NULL,
            title TEXT NOT NULL,
            posterUrl TEXT,
            backdropUrl TEXT,
            farsilandUrl TEXT NOT NULL UNIQUE,
            description TEXT,
            year INTEGER,
            rating REAL,
            totalSeasons INTEGER NOT NULL DEFAULT 0,
            totalEpisodes INTEGER NOT NULL DEFAULT 0,
            cast TEXT,
            genres TEXT,
            dateAdded INTEGER NOT NULL,
            lastUpdated INTEGER NOT NULL
        )
    """)

    # CachedEpisode table - matches ContentEntities.kt
    cursor.execute("""
        CREATE TABLE IF NOT EXISTS cached_episodes (
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
        )
    """)

    # CachedGenre table - matches ContentEntities.kt
    cursor.execute("""
        CREATE TABLE IF NOT EXISTS cached_genres (
            id INTEGER PRIMARY KEY NOT NULL,
            name TEXT NOT NULL,
            slug TEXT NOT NULL
        )
    """)

    # Create indexes matching ContentEntities.kt @Index annotations
    cursor.execute("CREATE UNIQUE INDEX IF NOT EXISTS index_cached_movies_farsilandUrl ON cached_movies(farsilandUrl)")
    cursor.execute("CREATE INDEX IF NOT EXISTS index_cached_movies_dateAdded ON cached_movies(dateAdded)")
    cursor.execute("CREATE UNIQUE INDEX IF NOT EXISTS index_cached_series_farsilandUrl ON cached_series(farsilandUrl)")
    cursor.execute("CREATE INDEX IF NOT EXISTS index_cached_series_dateAdded ON cached_series(dateAdded)")
    cursor.execute("CREATE UNIQUE INDEX IF NOT EXISTS index_cached_episodes_seriesId_season_episode ON cached_episodes(seriesId, season, episode)")
    cursor.execute("CREATE UNIQUE INDEX IF NOT EXISTS index_cached_episodes_farsilandUrl ON cached_episodes(farsilandUrl)")
    cursor.execute("CREATE INDEX IF NOT EXISTS index_cached_episodes_dateAdded ON cached_episodes(dateAdded)")


def convert_movies(source_cursor, dest_cursor):
    """Convert movies table"""

    # Check source schema to handle different column names
    source_cursor.execute("PRAGMA table_info(cached_movies)")
    source_columns = [col[1] for col in source_cursor.fetchall()]

    # Build SELECT based on available columns
    if 'last_modified' in source_columns:
        # FarsiPlex DooPlay schema
        source_cursor.execute("""
            SELECT id, title, poster_url, url, synopsis_en,
                   CAST(SUBSTR(release_date, 1, 4) AS INTEGER) as year,
                   rating, duration, NULL as director, NULL as cast,
                   NULL as genres, created_at, last_modified
            FROM cached_movies
            ORDER BY id
        """)
    else:
        # Fallback for other schemas
        source_cursor.execute("""
            SELECT id, title, poster_url, url, synopsis_en,
                   CAST(SUBSTR(release_date, 1, 4) AS INTEGER) as year,
                   rating, NULL as duration, NULL as director, NULL as cast,
                   NULL as genres, created_at, updated_at
            FROM cached_movies
            ORDER BY id
        """)

    current_time = int(time.time() * 1000)  # milliseconds

    for row in source_cursor.fetchall():
        movie_id, title, poster_url, url, description, year, rating, runtime, director, cast_str, genres, created_at, last_modified = row

        # FIX: Use created_at for dateAdded (preserves original order)
        # Convert datetime string to milliseconds if needed
        date_added = parse_datetime_to_millis(created_at) if created_at else current_time
        last_updated = parse_datetime_to_millis(last_modified) if last_modified else current_time

        dest_cursor.execute("""
            INSERT OR REPLACE INTO cached_movies (
                id, title, posterUrl, farsilandUrl, description, year, rating,
                runtime, director, cast, genres, dateAdded, lastUpdated
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """, (movie_id, title, poster_url, url, description, year, rating,
              runtime, director, cast_str, genres, date_added, last_updated))


def convert_tvshows_and_episodes(source_cursor, dest_cursor):
    """Convert TV shows and episodes"""

    current_time = int(time.time() * 1000)

    # Convert TV shows
    source_cursor.execute("PRAGMA table_info(cached_series)")
    source_columns = [col[1] for col in source_cursor.fetchall()]

    if 'last_modified' in source_columns:
        source_cursor.execute("""
            SELECT id, title, poster_url, url, synopsis_en,
                   CAST(SUBSTR(release_date, 1, 4) AS INTEGER) as year,
                   rating, NULL as cast, NULL as genres, created_at, last_modified
            FROM cached_series
            ORDER BY id
        """)
    else:
        source_cursor.execute("""
            SELECT id, title, poster_url, url, synopsis_en,
                   CAST(SUBSTR(release_date, 1, 4) AS INTEGER) as year,
                   rating, NULL as cast, NULL as genres, created_at, updated_at
            FROM cached_series
            ORDER BY id
        """)

    series_date_added = {}  # Track dateAdded for episodes fallback

    for row in source_cursor.fetchall():
        series_id, title, poster_url, url, description, year, rating, cast_str, genres, created_at, last_modified = row

        date_added = parse_datetime_to_millis(created_at) if created_at else current_time
        last_updated = parse_datetime_to_millis(last_modified) if last_modified else current_time

        series_date_added[series_id] = date_added

        # Count episodes for this series
        source_cursor.execute("""
            SELECT COUNT(DISTINCT s.season_number), COUNT(e.id)
            FROM episodes e
            JOIN seasons s ON e.season_id = s.id
            WHERE e.tvshow_id = ?
        """, (series_id,))
        total_seasons, total_episodes = source_cursor.fetchone() or (0, 0)

        dest_cursor.execute("""
            INSERT OR REPLACE INTO cached_series (
                id, title, posterUrl, backdropUrl, farsilandUrl, description, year, rating,
                totalSeasons, totalEpisodes, cast, genres, dateAdded, lastUpdated
            ) VALUES (?, ?, ?, NULL, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """, (series_id, title, poster_url, url, description, year, rating,
              total_seasons or 0, total_episodes or 0, cast_str, genres, date_added, last_updated))

    # Convert episodes
    source_cursor.execute("PRAGMA table_info(episodes)")
    ep_columns = [col[1] for col in source_cursor.fetchall()]

    if 'last_modified' in ep_columns:
        source_cursor.execute("""
            SELECT e.id, e.tvshow_id, s.season_number, e.episode_number,
                   e.title, e.url, e.thumbnail_url, e.release_date, e.synopsis,
                   e.created_at, e.last_modified, ts.title as series_title
            FROM episodes e
            JOIN seasons s ON e.season_id = s.id
            LEFT JOIN cached_series ts ON e.tvshow_id = ts.id
            ORDER BY e.tvshow_id, s.season_number, e.episode_number
        """)
    else:
        source_cursor.execute("""
            SELECT e.id, e.tvshow_id, s.season_number, e.episode_number,
                   e.title, e.url, e.thumbnail_url, e.release_date, e.synopsis,
                   NULL as created_at, NULL as last_modified, ts.title as series_title
            FROM episodes e
            JOIN seasons s ON e.season_id = s.id
            LEFT JOIN cached_series ts ON e.tvshow_id = ts.id
            ORDER BY e.tvshow_id, s.season_number, e.episode_number
        """)

    for row in source_cursor.fetchall():
        ep_id, series_id, season, episode, title, url, thumbnail_url, release_date, description, created_at, last_modified, series_title = row

        # FIX: Use airDate → series dateAdded → current time (same fallback chain as Android app)
        if release_date:
            date_added = parse_date_to_millis(release_date)
        elif series_id in series_date_added:
            date_added = series_date_added[series_id]
        else:
            date_added = current_time

        last_updated = parse_datetime_to_millis(last_modified) if last_modified else current_time

        dest_cursor.execute("""
            INSERT OR REPLACE INTO cached_episodes (
                seriesId, seriesTitle, episodeId, season, episode, title, description,
                thumbnailUrl, farsilandUrl, airDate, runtime, dateAdded, lastUpdated
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NULL, ?, ?)
        """, (series_id, series_title, ep_id, season, episode, title or f"Episode {episode}",
              description, thumbnail_url, url, release_date, date_added, last_updated))


def convert_genres(source_cursor, dest_cursor):
    """Convert genres to cached_genres table"""

    # Get unique genres from movies
    source_cursor.execute("SELECT DISTINCT genre FROM movie_genres")
    genres = set(row[0] for row in source_cursor.fetchall())

    # Get unique genres from TV shows
    source_cursor.execute("SELECT DISTINCT genre FROM tvshow_genres")
    genres.update(row[0] for row in source_cursor.fetchall())

    # Insert unique genres
    for i, genre in enumerate(sorted(genres), start=1):
        slug = genre.lower().replace(' ', '-').replace('&', 'and')
        dest_cursor.execute("""
            INSERT INTO cached_genres (id, name, slug) VALUES (?, ?, ?)
        """, (i, genre, slug))


def parse_datetime_to_millis(dt_str):
    """Convert datetime string to milliseconds"""
    if not dt_str:
        return int(time.time() * 1000)

    from datetime import datetime
    try:
        # Try ISO format first
        if 'T' in str(dt_str):
            dt = datetime.fromisoformat(str(dt_str).replace('Z', '+00:00'))
        else:
            dt = datetime.strptime(str(dt_str), '%Y-%m-%d %H:%M:%S')
        return int(dt.timestamp() * 1000)
    except:
        return int(time.time() * 1000)


def parse_date_to_millis(date_str):
    """Convert date string (YYYY-MM-DD) to milliseconds"""
    if not date_str:
        return int(time.time() * 1000)

    from datetime import datetime
    try:
        dt = datetime.strptime(str(date_str)[:10], '%Y-%m-%d')
        return int(dt.timestamp() * 1000)
    except:
        return int(time.time() * 1000)


def print_stats(db_path: str):
    """Print database statistics"""

    conn = sqlite3.connect(db_path)
    cursor = conn.cursor()

    cursor.execute("SELECT COUNT(*) FROM cached_movies")
    movie_count = cursor.fetchone()[0]

    cursor.execute("SELECT COUNT(*) FROM cached_series")
    series_count = cursor.fetchone()[0]

    cursor.execute("SELECT COUNT(*) FROM cached_episodes")
    episode_count = cursor.fetchone()[0]

    cursor.execute("SELECT COUNT(*) FROM cached_genres")
    genre_count = cursor.fetchone()[0]

    conn.close()

    print(f"\nDatabase Statistics:")
    print(f"  Movies: {movie_count}")
    print(f"  TV Shows: {series_count}")
    print(f"  Episodes: {episode_count}")
    print(f"  Genres: {genre_count}")


if __name__ == '__main__':
    input_db = 'Farsiplex.db'
    output_db = 'farsiplex_content.db'

    if len(sys.argv) > 1:
        input_db = sys.argv[1]
    if len(sys.argv) > 2:
        output_db = sys.argv[2]

    if not os.path.exists(input_db):
        print(f"Error: {input_db} not found!")
        sys.exit(1)

    convert_database(input_db, output_db)

    print(f"\nReady to use in Android app!")
    print(f"  Copy {output_db} to: app/src/main/assets/databases/")
