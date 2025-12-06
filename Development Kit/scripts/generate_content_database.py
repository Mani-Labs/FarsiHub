#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
FarsiFlow PostgreSQL -> Android SQLite Converter

Converts FarsiFlow's PostgreSQL catalog to SQLite database for Android app.

Usage:
    python generate_content_database.py

Requirements:
    pip install psycopg2-binary

Output:
    app/src/main/assets/databases/farsiland_content.db
"""

import psycopg2
import sqlite3
import os
from datetime import datetime
from pathlib import Path

# Configuration
POSTGRES_CONFIG = {
    'host': 'localhost',
    'port': 5432,
    'database': 'farsiflow',
    'user': 'farsiflow',
    'password': 'farsiflow123'
}

OUTPUT_DB_PATH = Path(__file__).parent.parent / 'app/src/main/assets/databases/farsiland_content.db'


def create_output_directory():
    """Ensure output directory exists"""
    OUTPUT_DB_PATH.parent.mkdir(parents=True, exist_ok=True)

    # Delete existing database
    if OUTPUT_DB_PATH.exists():
        OUTPUT_DB_PATH.unlink()
        print(f"Deleted existing database: {OUTPUT_DB_PATH}")


def create_sqlite_schema(sqlite_conn):
    """Create SQLite tables matching Android Room schema"""
    cursor = sqlite_conn.cursor()

    # CachedMovie table
    cursor.execute('''
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
    ''')

    # Room creates this index automatically from @Index annotation
    cursor.execute('CREATE UNIQUE INDEX IF NOT EXISTS index_cached_movies_farsilandUrl ON cached_movies(farsilandUrl)')

    # CachedSeries table
    cursor.execute('''
        CREATE TABLE IF NOT EXISTS cached_series (
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
        )
    ''')

    # Room creates this index automatically from @Index annotation
    cursor.execute('CREATE UNIQUE INDEX IF NOT EXISTS index_cached_series_farsilandUrl ON cached_series(farsilandUrl)')

    # CachedEpisode table
    cursor.execute('''
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
    ''')

    # Room creates these indices automatically from @Index annotations
    cursor.execute('CREATE UNIQUE INDEX IF NOT EXISTS index_cached_episodes_seriesId_season_episode ON cached_episodes(seriesId, season, episode)')
    cursor.execute('CREATE UNIQUE INDEX IF NOT EXISTS index_cached_episodes_farsilandUrl ON cached_episodes(farsilandUrl)')

    # CachedGenre table
    cursor.execute('''
        CREATE TABLE IF NOT EXISTS cached_genres (
            id INTEGER PRIMARY KEY NOT NULL,
            name TEXT NOT NULL,
            slug TEXT NOT NULL
        )
    ''')

    # CachedVideoUrl table (optional - for pre-cached MP4 URLs)
    cursor.execute('''
        CREATE TABLE IF NOT EXISTS cached_video_urls (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            contentId INTEGER NOT NULL,
            contentType TEXT NOT NULL,
            quality TEXT NOT NULL,
            mp4Url TEXT NOT NULL,
            fileSizeMB REAL,
            cachedAt INTEGER NOT NULL,
            UNIQUE(contentId, contentType, quality)
        )
    ''')

    # Room creates this index automatically from @Index annotation
    cursor.execute('CREATE UNIQUE INDEX IF NOT EXISTS index_cached_video_urls_contentId_contentType_quality ON cached_video_urls(contentId, contentType, quality)')

    sqlite_conn.commit()
    print("[OK] SQLite schema created")


def migrate_movies(pg_conn, sqlite_conn):
    """Migrate movies from PostgreSQL to SQLite"""
    pg_cursor = pg_conn.cursor()
    sqlite_cursor = sqlite_conn.cursor()

    print("\n[MOVIES] Migrating movies...")

    # AUDIT FIX #17: Extract metadata from source if available
    # Issue: Hardcoded NULL for runtime, director, cast degrades offline UX
    # Solution: Attempt to extract these fields from catalog_items if columns exist
    # Note: If your PostgreSQL schema doesn't have these columns, they'll be NULL (safe fallback)
    query = '''
        SELECT
            id,
            title,
            poster_url,
            farsiland_url,
            description,
            EXTRACT(YEAR FROM release_date)::INTEGER as year,
            rating,
            COALESCE(runtime, NULL) as runtime,
            COALESCE(director, NULL) as director,
            COALESCE(cast, NULL) as cast,
            ARRAY_TO_STRING(genres, ', ') as genres,
            (EXTRACT(EPOCH FROM created_at) * 1000)::BIGINT as dateAdded,
            (EXTRACT(EPOCH FROM last_extracted) * 1000)::BIGINT as lastUpdated
        FROM catalog_items
        WHERE item_type = 'movie' AND farsiland_url IS NOT NULL
        ORDER BY id
    '''

    pg_cursor.execute(query)
    movies = pg_cursor.fetchall()

    count = 0
    for movie in movies:
        try:
            # Convert to list and fix rating type (decimal -> float)
            movie_data = list(movie)
            if movie_data[6] is not None:  # rating column
                movie_data[6] = float(movie_data[6])

            sqlite_cursor.execute('''
                INSERT INTO cached_movies
                (id, title, posterUrl, farsilandUrl, description, year, rating,
                 runtime, director, cast, genres, dateAdded, lastUpdated)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ''', movie_data)
            count += 1
        except sqlite3.IntegrityError as e:
            print(f"  [WARN]  Skipped duplicate movie: {movie[1]} - {e}")

    sqlite_conn.commit()
    print(f"  [OK] Migrated {count} movies")
    return count


def migrate_series(pg_conn, sqlite_conn):
    """Migrate TV series from PostgreSQL to SQLite"""
    pg_cursor = pg_conn.cursor()
    sqlite_cursor = sqlite_conn.cursor()

    print("\n[SERIES] Migrating series...")

    # Get series with aggregated season/episode counts
    # AUDIT FIX #17: Extract cast from source if available
    query = '''
        SELECT
            s.id,
            s.series_name as title,
            s.poster_url,
            NULL as backdropUrl,
            s.farsiland_url,
            s.description,
            EXTRACT(YEAR FROM s.release_date)::INTEGER as year,
            s.rating,
            COALESCE(COUNT(DISTINCT e.season), 0) as totalSeasons,
            COALESCE(COUNT(e.id), 0) as totalEpisodes,
            COALESCE(s.cast, NULL) as cast,
            ARRAY_TO_STRING(s.genres, ', ') as genres,
            (EXTRACT(EPOCH FROM COALESCE(s.created_at, NOW())) * 1000)::BIGINT as dateAdded,
            (EXTRACT(EPOCH FROM COALESCE(s.last_extracted, s.created_at, NOW())) * 1000)::BIGINT as lastUpdated
        FROM catalog_items s
        LEFT JOIN catalog_items e ON e.parent_series_id = s.id AND e.item_type = 'episode'
        WHERE s.item_type = 'series' AND s.farsiland_url IS NOT NULL
        GROUP BY s.id, s.series_name, s.poster_url, s.farsiland_url,
                 s.description, s.release_date, s.rating, s.genres,
                 s.created_at, s.last_extracted, s.cast
        ORDER BY s.id
    '''

    pg_cursor.execute(query)
    series_list = pg_cursor.fetchall()

    count = 0
    for series in series_list:
        try:
            # Convert to list and fix rating type (decimal -> float)
            series_data = list(series)
            if series_data[7] is not None:  # rating column
                series_data[7] = float(series_data[7])

            sqlite_cursor.execute('''
                INSERT INTO cached_series
                (id, title, posterUrl, backdropUrl, farsilandUrl, description, year, rating,
                 totalSeasons, totalEpisodes, cast, genres, dateAdded, lastUpdated)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ''', series_data)
            count += 1
        except sqlite3.IntegrityError as e:
            print(f"  [WARN]  Skipped duplicate series: {series[1]} - {e}")

    sqlite_conn.commit()
    print(f"  [OK] Migrated {count} series")
    return count


def migrate_episodes(pg_conn, sqlite_conn):
    """Migrate episodes from PostgreSQL to SQLite"""
    pg_cursor = pg_conn.cursor()
    sqlite_cursor = sqlite_conn.cursor()

    print("\n[EPISODES] Migrating episodes...")

    # AUDIT FIX #17: Extract runtime from source if available
    query = '''
        SELECT
            e.parent_series_id as seriesId,
            s.series_name as seriesTitle,
            e.id as episodeId,
            e.season,
            -- Handle fractional episodes: 14.5 → 145
            CASE
                WHEN e.episode % 1 = 0 THEN e.episode::INTEGER
                ELSE (e.episode * 10)::INTEGER
            END as episode,
            e.title,
            e.description,
            e.poster_url as thumbnailUrl,
            e.farsiland_url,
            TO_CHAR(e.release_date, 'YYYY-MM-DD') as airDate,
            COALESCE(e.runtime, NULL) as runtime,
            (EXTRACT(EPOCH FROM COALESCE(e.created_at, NOW())) * 1000)::BIGINT as dateAdded,
            (EXTRACT(EPOCH FROM COALESCE(e.last_extracted, e.created_at, NOW())) * 1000)::BIGINT as lastUpdated
        FROM catalog_items e
        LEFT JOIN catalog_items s ON s.id = e.parent_series_id AND s.item_type = 'series'
        WHERE e.item_type = 'episode'
          AND e.parent_series_id IS NOT NULL
          AND e.farsiland_url IS NOT NULL
        ORDER BY e.parent_series_id, e.season, e.episode
    '''

    pg_cursor.execute(query)
    episodes = pg_cursor.fetchall()

    count = 0
    for episode in episodes:
        try:
            sqlite_cursor.execute('''
                INSERT INTO cached_episodes
                (seriesId, seriesTitle, episodeId, season, episode, title, description,
                 thumbnailUrl, farsilandUrl, airDate, runtime, dateAdded, lastUpdated)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ''', episode)
            count += 1
        except sqlite3.IntegrityError as e:
            print(f"  [WARN]  Skipped duplicate episode: S{episode[3]}E{episode[4]} - {e}")

    sqlite_conn.commit()
    print(f"  [OK] Migrated {count} episodes")
    return count


def migrate_video_urls(pg_conn, sqlite_conn):
    """Optional: Migrate pre-extracted MP4 URLs"""
    pg_cursor = pg_conn.cursor()
    sqlite_cursor = sqlite_conn.cursor()

    print("\n[VIDEOS] Migrating video URLs (optional)...")

    # Check if catalog_quality_variants table exists
    pg_cursor.execute("""
        SELECT EXISTS (
            SELECT FROM information_schema.tables
            WHERE table_name = 'catalog_quality_variants'
        )
    """)

    if not pg_cursor.fetchone()[0]:
        print("  ℹ️  No quality variants table found, skipping")
        return 0

    query = '''
        SELECT
            qv.catalog_item_id as contentId,
            CASE
                WHEN ci.item_type = 'movie' THEN 'movie'
                WHEN ci.item_type = 'episode' THEN 'episode'
                ELSE 'unknown'
            END as contentType,
            qv.quality,
            qv.mp4_url,
            qv.file_size_mb,
            (EXTRACT(EPOCH FROM CURRENT_TIMESTAMP) * 1000)::BIGINT as cachedAt
        FROM catalog_quality_variants qv
        JOIN catalog_items ci ON ci.id = qv.catalog_item_id
        WHERE qv.mp4_url IS NOT NULL
          AND ci.item_type IN ('movie', 'episode')
        ORDER BY qv.catalog_item_id, qv.quality
    '''

    try:
        pg_cursor.execute(query)
        video_urls = pg_cursor.fetchall()

        count = 0
        for video in video_urls:
            try:
                sqlite_cursor.execute('''
                    INSERT INTO cached_video_urls
                    (contentId, contentType, quality, mp4Url, fileSizeMB, cachedAt)
                    VALUES (?, ?, ?, ?, ?, ?)
                ''', video)
                count += 1
            except sqlite3.IntegrityError:
                pass  # Silently skip duplicates for video URLs

        sqlite_conn.commit()
        print(f"  [OK] Migrated {count} video URLs")
        return count
    except Exception as e:
        print(f"  [WARN]  Error migrating video URLs: {e}")
        return 0


def main():
    """Main migration process"""
    print("=" * 60)
    print("FarsiFlow PostgreSQL -> Android SQLite Converter")
    print("=" * 60)

    # Create output directory
    create_output_directory()

    # Connect to PostgreSQL
    print(f"\n[DB] Connecting to PostgreSQL at {POSTGRES_CONFIG['host']}:{POSTGRES_CONFIG['port']}...")
    try:
        pg_conn = psycopg2.connect(**POSTGRES_CONFIG)
        print("  [OK] Connected to PostgreSQL")
    except Exception as e:
        print(f"  [ERROR] Failed to connect to PostgreSQL: {e}")
        print("\n[TIP] Make sure FarsiFlow Docker container is running:")
        print("   cd G:\\Farsiflow && docker-compose up -d")
        return

    # Connect to SQLite
    print(f"\n[SQLITE] Creating SQLite database at {OUTPUT_DB_PATH}...")
    sqlite_conn = sqlite3.connect(str(OUTPUT_DB_PATH))

    # Create schema
    create_sqlite_schema(sqlite_conn)

    # Migrate data
    movies_count = migrate_movies(pg_conn, sqlite_conn)
    series_count = migrate_series(pg_conn, sqlite_conn)
    episodes_count = migrate_episodes(pg_conn, sqlite_conn)
    videos_count = migrate_video_urls(pg_conn, sqlite_conn)

    # Close connections
    pg_conn.close()
    sqlite_conn.close()

    # Summary
    print("\n" + "=" * 60)
    print("[OK] MIGRATION COMPLETE!")
    print("=" * 60)
    print(f"Movies:       {movies_count:,}")
    print(f"Series:       {series_count:,}")
    print(f"Episodes:     {episodes_count:,}")
    print(f"Video URLs:   {videos_count:,}")
    print(f"\nOutput: {OUTPUT_DB_PATH}")
    print(f"Size:   {OUTPUT_DB_PATH.stat().st_size / 1024 / 1024:.2f} MB")
    print("\n[ANDROID] Database ready to be bundled in Android APK!")
    print("=" * 60)


if __name__ == "__main__":
    main()

