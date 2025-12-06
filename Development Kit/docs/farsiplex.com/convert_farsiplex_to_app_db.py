#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
FarsiPlex Database Converter
Converts farsiplex_old.db (old schema) to app's ContentDatabase schema format

Usage:
    python convert_farsiplex_to_app_db.py

Input:  farsiplex_old.db (Old FarsiPlex schema with CachedMovie, CachedSeries, etc.)
Output: farsiplex_content.db (App's ContentDatabase schema)
"""

import sqlite3
import sys
from datetime import datetime

# Fix Windows console encoding
if sys.platform == 'win32':
    sys.stdout.reconfigure(encoding='utf-8')

# Database paths
SOURCE_DB = "farsiplex_old.db"
OUTPUT_DB = "farsiplex_content.db"


def create_app_schema(cursor):
    """Create tables matching app's ContentDatabase schema EXACTLY"""

    # cached_movies table - matches farsiland_content.db schema
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

    # cached_series table - matches farsiland_content.db schema
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
            totalSeasons INTEGER NOT NULL,
            totalEpisodes INTEGER NOT NULL,
            cast TEXT,
            genres TEXT,
            dateAdded INTEGER NOT NULL,
            lastUpdated INTEGER NOT NULL
        )
    """)

    # cached_episodes table - matches farsiland_content.db schema EXACTLY
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

    # cached_video_urls table - matches farsiland_content.db schema EXACTLY
    cursor.execute("""
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
    """)

    print("[OK] Created app schema tables")


def convert_movies(source_cursor, dest_cursor):
    """Convert CachedMovie table to cached_movies"""

    source_cursor.execute("""
        SELECT id, title, url, posterUrl, synopsisFa, rating, releaseDate, lastUpdated
        FROM CachedMovie
    """)

    movies = source_cursor.fetchall()
    movie_count = 0
    video_url_count = 0

    for row in movies:
        movie_id, title, url, poster_url, synopsis_fa, rating, release_date, last_updated = row

        # Extract year from releaseDate (format: "2023-01-15")
        year = None
        if release_date:
            try:
                year = int(release_date.split('-')[0])
            except:
                pass

        # Insert into cached_movies
        dest_cursor.execute("""
            INSERT OR REPLACE INTO cached_movies (
                id, title, posterUrl, farsilandUrl, description,
                year, rating, runtime, director, cast, genres,
                dateAdded, lastUpdated
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """, (
            movie_id,
            title,
            poster_url,
            url,  # url -> farsilandUrl
            synopsis_fa,  # description
            year,
            rating,
            None,  # runtime not available
            None,  # director not available
            None,  # cast not available
            None,  # genres not available in old schema
            last_updated,  # dateAdded
            last_updated
        ))

        # Get video URLs for this movie
        source_cursor.execute("""
            SELECT quality, url
            FROM CachedVideoUrl
            WHERE contentId = ? AND contentType = 'movie'
        """, (movie_id,))

        video_urls = source_cursor.fetchall()
        for quality, video_url in video_urls:
            dest_cursor.execute("""
                INSERT INTO cached_video_urls (
                    contentId, contentType, quality, mp4Url,
                    fileSizeMB, cachedAt
                ) VALUES (?, ?, ?, ?, ?, ?)
            """, (
                movie_id,
                'movie',
                quality,
                video_url,
                None,  # fileSizeMB not available
                last_updated
            ))
            video_url_count += 1

        movie_count += 1

    print(f"✓ Converted {movie_count} movies")
    print(f"✓ Created {video_url_count} movie video URLs")


def convert_series(source_cursor, dest_cursor):
    """Convert CachedSeries table to cached_series"""

    source_cursor.execute("""
        SELECT id, title, url, posterUrl, synopsisFa, rating, releaseDate, lastUpdated
        FROM CachedSeries
    """)

    series_list = source_cursor.fetchall()
    series_count = 0

    for row in series_list:
        series_id, title, url, poster_url, synopsis_fa, rating, release_date, last_updated = row

        # Extract year from releaseDate
        year = None
        if release_date:
            try:
                year = int(release_date.split('-')[0])
            except:
                pass

        # Count actual episodes for this series
        source_cursor.execute("""
            SELECT COUNT(*) FROM CachedEpisode WHERE seriesId = ?
        """, (series_id,))
        total_episodes = source_cursor.fetchone()[0]

        # Count seasons
        source_cursor.execute("""
            SELECT MAX(seasonNumber) FROM CachedEpisode WHERE seriesId = ?
        """, (series_id,))
        total_seasons = source_cursor.fetchone()[0] or 1

        # Insert into cached_series
        dest_cursor.execute("""
            INSERT OR REPLACE INTO cached_series (
                id, title, posterUrl, backdropUrl, farsilandUrl,
                description, year, rating, totalSeasons, totalEpisodes,
                cast, genres, dateAdded, lastUpdated
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """, (
            series_id,
            title,
            poster_url,
            None,  # backdropUrl not available
            url,  # url -> farsilandUrl
            synopsis_fa,
            year,
            rating,
            total_seasons,
            total_episodes,
            None,  # cast not available
            None,  # genres not available
            last_updated,  # dateAdded
            last_updated
        ))

        series_count += 1

    print(f"✓ Converted {series_count} series")


def convert_episodes(source_cursor, dest_cursor):
    """Convert CachedEpisode table to cached_episodes and cached_video_urls"""

    # Get all episodes with series title
    source_cursor.execute("""
        SELECT e.id, e.seriesId, e.seasonNumber, e.episodeNumber,
               e.title, e.url, e.thumbnailUrl, e.releaseDate, e.lastUpdated,
               s.title as seriesTitle
        FROM CachedEpisode e
        JOIN CachedSeries s ON e.seriesId = s.id
    """)

    episodes = source_cursor.fetchall()
    episode_count = 0
    video_url_count = 0

    for row in episodes:
        episode_id, series_id, season_number, episode_number, \
            title, url, thumbnail_url, release_date, last_updated, series_title = row

        # Insert into cached_episodes
        dest_cursor.execute("""
            INSERT INTO cached_episodes (
                seriesId, seriesTitle, episodeId, season, episode,
                title, description, thumbnailUrl, farsilandUrl,
                airDate, runtime, dateAdded, lastUpdated
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """, (
            series_id,
            series_title,
            episode_id,
            season_number,
            episode_number,
            title or f"Episode {episode_number}",
            None,  # description not available
            thumbnail_url,
            url,  # url -> farsilandUrl
            release_date,  # airDate
            None,  # runtime not available
            last_updated,  # dateAdded
            last_updated
        ))

        # Insert video URLs for this episode
        source_cursor.execute("""
            SELECT quality, url
            FROM CachedVideoUrl
            WHERE contentId = ? AND contentType = 'episode'
        """, (episode_id,))

        video_urls = source_cursor.fetchall()
        for quality, video_url in video_urls:
            dest_cursor.execute("""
                INSERT INTO cached_video_urls (
                    contentId, contentType, quality, mp4Url,
                    fileSizeMB, cachedAt
                ) VALUES (?, ?, ?, ?, ?, ?)
            """, (
                episode_id,
                'episode',
                quality,
                video_url,
                None,  # fileSizeMB not available
                last_updated
            ))
            video_url_count += 1

        episode_count += 1

    print(f"✓ Converted {episode_count} episodes")
    print(f"✓ Created {video_url_count} episode video URLs")


def create_indexes(cursor):
    """
    Create NO additional indexes - UNIQUE constraints are inline in table definitions
    Room will see the inline UNIQUE constraints and won't try to create duplicate indices
    """
    print("✓ Schema created with inline UNIQUE constraints (no additional indexes needed)")


def print_statistics(cursor):
    """Print conversion statistics"""

    print("\n" + "="*60)
    print("CONVERSION STATISTICS")
    print("="*60)

    cursor.execute("SELECT COUNT(*) FROM cached_movies")
    movie_count = cursor.fetchone()[0]
    print(f"Movies:           {movie_count}")

    cursor.execute("SELECT COUNT(*) FROM cached_series")
    series_count = cursor.fetchone()[0]
    print(f"Series:           {series_count}")

    cursor.execute("SELECT COUNT(*) FROM cached_episodes")
    episode_count = cursor.fetchone()[0]
    print(f"Episodes:         {episode_count}")

    cursor.execute("SELECT COUNT(*) FROM cached_video_urls WHERE contentType='movie'")
    movie_urls = cursor.fetchone()[0]
    print(f"Movie Video URLs: {movie_urls}")

    cursor.execute("SELECT COUNT(*) FROM cached_video_urls WHERE contentType='episode'")
    episode_urls = cursor.fetchone()[0]
    print(f"Episode Video URLs: {episode_urls}")

    # Check video URL coverage
    cursor.execute("""
        SELECT COUNT(*) FROM cached_episodes e
        WHERE NOT EXISTS (
            SELECT 1 FROM cached_video_urls v
            WHERE v.contentId = e.episodeId AND v.contentType = 'episode'
        )
    """)
    missing_urls = cursor.fetchone()[0]
    coverage = ((episode_count - missing_urls) / episode_count * 100) if episode_count > 0 else 0
    print(f"Video URL Coverage: {coverage:.1f}% ({episode_count - missing_urls}/{episode_count})")

    print("="*60)


def main():
    """Main conversion process"""

    print("="*60)
    print("FARSIPLEX DATABASE CONVERTER")
    print("="*60)
    print(f"Source: {SOURCE_DB}")
    print(f"Output: {OUTPUT_DB}")
    print()

    # Connect to databases
    try:
        source_conn = sqlite3.connect(SOURCE_DB)
        source_cursor = source_conn.cursor()
        print("✓ Connected to source database")
    except Exception as e:
        print(f"✗ Failed to open source database: {e}")
        return

    try:
        # Delete existing output database
        import os
        if os.path.exists(OUTPUT_DB):
            os.remove(OUTPUT_DB)
            print("✓ Removed existing output database")

        dest_conn = sqlite3.connect(OUTPUT_DB)
        dest_cursor = dest_conn.cursor()
        print("✓ Created output database")
    except Exception as e:
        print(f"✗ Failed to create output database: {e}")
        source_conn.close()
        return

    try:
        # Step 1: Create schema
        print("\n[1/5] Creating app schema...")
        create_app_schema(dest_cursor)

        # Step 2: Convert movies
        print("\n[2/5] Converting movies...")
        convert_movies(source_cursor, dest_cursor)

        # Step 3: Convert series
        print("\n[3/5] Converting series...")
        convert_series(source_cursor, dest_cursor)

        # Step 4: Convert episodes
        print("\n[4/5] Converting episodes...")
        convert_episodes(source_cursor, dest_cursor)

        # Step 5: Create indexes
        print("\n[5/5] Creating indexes...")
        create_indexes(dest_cursor)

        # Commit changes
        dest_conn.commit()
        print("\n✓ Changes committed")

        # Print statistics
        print_statistics(dest_cursor)

        print(f"\n✓ Conversion complete!")
        print(f"✓ Output saved to: {OUTPUT_DB}")
        print(f"\nNext steps:")
        print(f"1. Copy {OUTPUT_DB} to app/src/main/assets/databases/farsiplex_content.db")
        print(f"2. Rebuild app and test FarsiPlex content")

    except Exception as e:
        print(f"\n✗ Conversion failed: {e}")
        import traceback
        traceback.print_exc()
    finally:
        source_conn.close()
        dest_conn.close()


if __name__ == "__main__":
    main()
