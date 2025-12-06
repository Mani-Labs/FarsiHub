#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Namakade Database Converter
Converts namakadeh.db to app's ContentDatabase schema format

Usage:
    python convert_namakade_to_app_db.py

Input:  namakadeh.db (Namakade's original schema)
Output: namakade_app.db (App's ContentDatabase schema)
"""

import sqlite3
import hashlib
import sys
from datetime import datetime

# Fix Windows console encoding
if sys.platform == 'win32':
    sys.stdout.reconfigure(encoding='utf-8')

# Database paths
SOURCE_DB = "namakadeh.db"
OUTPUT_DB = "namakade_app.db"

def hash_slug_to_id(slug):
    """Convert string slug to consistent integer ID"""
    # Use first 8 characters of MD5 hash as hex, convert to int
    # This ensures same slug always produces same ID
    hash_hex = hashlib.md5(slug.encode()).hexdigest()[:8]
    return int(hash_hex, 16) % 2147483647  # Keep within INT max


def extract_genres_from_path(link_path):
    """
    Extract genres from Namakade URL path
    Example: /iran-1-movies/action-comedy-foreign/slug -> "Action, Comedy, Foreign"
    """
    try:
        # Split path: /iran-1-movies/action-comedy-foreign/slug
        parts = link_path.strip('/').split('/')

        # For movies: /iran-1-movies/{genres}/slug
        # For series: /series/slug (no genres in path)
        if len(parts) >= 2 and parts[0] == 'iran-1-movies':
            genre_part = parts[1]  # "action-comedy-foreign"

            # Split by hyphen and capitalize
            genres = genre_part.split('-')

            # Capitalize each genre
            capitalized = [g.capitalize() for g in genres]

            # Join with comma
            return ', '.join(capitalized)

        return None
    except Exception as e:
        return None


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
    """Convert series table (contentType='movie') to cached_movies"""

    source_cursor.execute("""
        SELECT id, title, slug, linkPath, thumbnail, description,
               genre, year, rating, createdAt, updatedAt
        FROM series
        WHERE contentType = 'movie'
    """)

    movies = source_cursor.fetchall()
    movie_count = 0
    video_url_count = 0

    for row in movies:
        slug, title, _, link_path, thumbnail, description, genre, year, rating, created_at, updated_at = row

        # Generate consistent integer ID from slug
        movie_id = hash_slug_to_id(slug)

        # Extract genres from URL path (much better than "Unknown")
        extracted_genres = extract_genres_from_path(link_path)
        final_genres = extracted_genres if extracted_genres else (genre if genre != "Unknown" else None)

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
            thumbnail,
            f"https://namakade.com{link_path}",
            description,
            year,
            rating,
            None,  # runtime not available
            None,  # director not available
            None,  # cast not available
            final_genres,
            created_at,
            updated_at
        ))

        # Get video URL for this movie (movies typically have 1 episode)
        source_cursor.execute("""
            SELECT videoUrl FROM episodes WHERE seriesId = ? LIMIT 1
        """, (slug,))

        video_row = source_cursor.fetchone()
        if video_row and video_row[0]:
            video_url = video_row[0]

            # Insert into cached_video_urls using correct field names
            dest_cursor.execute("""
                INSERT INTO cached_video_urls (
                    contentId, contentType, quality, mp4Url,
                    fileSizeMB, cachedAt
                ) VALUES (?, ?, ?, ?, ?, ?)
            """, (
                movie_id,
                'movie',
                'auto',
                video_url,
                None,  # fileSizeMB not available
                created_at
            ))
            video_url_count += 1

        movie_count += 1

    print(f"✓ Converted {movie_count} movies")
    print(f"✓ Created {video_url_count} movie video URLs")


def convert_series(source_cursor, dest_cursor):
    """Convert series table (contentType='series') to cached_series"""

    source_cursor.execute("""
        SELECT id, title, slug, linkPath, thumbnail, banner, description,
               genre, totalEpisodes, seasons, year, rating, isTurkish, createdAt, updatedAt
        FROM series
        WHERE contentType = 'series'
    """)

    series_list = source_cursor.fetchall()
    series_count = 0
    turkish_count = 0

    for row in series_list:
        slug, title, _, link_path, thumbnail, banner, description, genre, \
            total_episodes, seasons, year, rating, is_turkish, created_at, updated_at = row

        # Generate consistent integer ID from slug
        series_id = hash_slug_to_id(slug)

        # Get ACTUAL episode count (totalEpisodes is inflated)
        source_cursor.execute("""
            SELECT COUNT(*) FROM episodes WHERE seriesId = ?
        """, (slug,))
        actual_episode_count = source_cursor.fetchone()[0]

        # Build genres string - add "Turkish" tag if applicable
        genres_list = []
        if is_turkish:
            genres_list.append("Turkish")
            turkish_count += 1

        # Add original genre if not "Unknown"
        if genre and genre != "Unknown":
            genres_list.append(genre)

        final_genres = ", ".join(genres_list) if genres_list else None

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
            thumbnail,
            banner,
            f"https://namakade.com{link_path}",
            description,
            year,
            rating,
            seasons,
            actual_episode_count,  # Use actual count, not metadata
            None,  # cast not available
            final_genres,
            created_at,
            updated_at
        ))

        series_count += 1

    print(f"✓ Converted {series_count} series ({turkish_count} Turkish)")


def convert_episodes(source_cursor, dest_cursor):
    """Convert episodes table to cached_episodes and cached_video_urls"""

    # Get all episodes
    source_cursor.execute("""
        SELECT e.id, e.seriesId, e.title, e.episodeNumber, e.season,
               e.thumbnail, e.videoUrl, e.duration, e.addedAt, e.episodePageUrl,
               s.title as seriesTitle
        FROM episodes e
        JOIN series s ON e.seriesId = s.id
        WHERE s.contentType = 'series'
    """)

    episodes = source_cursor.fetchall()
    episode_count = 0
    video_url_count = 0

    for row in episodes:
        episode_slug, series_slug, title, episode_number, season, \
            thumbnail, video_url, duration, added_at, episode_page_url, series_title = row

        # Generate IDs
        series_id = hash_slug_to_id(series_slug)
        episode_id = hash_slug_to_id(episode_slug)

        # Build unique URL for each episode
        # Use episodePageUrl, but append episode_slug to ensure uniqueness
        if episode_page_url:
            episode_url = f"https://namakade.com{episode_page_url}?ep={episode_slug}"
        else:
            episode_url = f"https://namakade.com/series/{series_slug}/episode/{episode_number}?ep={episode_slug}"

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
            season,
            episode_number,
            title,
            None,  # description not available
            thumbnail,
            episode_url,
            None,  # airDate not available
            duration,
            added_at,
            added_at
        ))

        # Insert video URL if available using correct field names
        if video_url:
            dest_cursor.execute("""
                INSERT INTO cached_video_urls (
                    contentId, contentType, quality, mp4Url,
                    fileSizeMB, cachedAt
                ) VALUES (?, ?, ?, ?, ?, ?)
            """, (
                episode_id,
                'episode',
                'auto',
                video_url,
                None,  # fileSizeMB not available
                added_at
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

    # Check genre coverage
    cursor.execute("SELECT COUNT(*) FROM cached_series WHERE genres IS NULL OR genres = 'Unknown'")
    unknown_genres = cursor.fetchone()[0]
    genre_coverage = ((series_count - unknown_genres) / series_count * 100) if series_count > 0 else 0
    print(f"Genre Coverage:   {genre_coverage:.1f}% (series)")

    print("="*60)


def main():
    """Main conversion process"""

    print("="*60)
    print("NAMAKADE DATABASE CONVERTER")
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
        print(f"1. Copy {OUTPUT_DB} to app/src/main/assets/databases/namakade.db")
        print(f"2. Add NAMAKADE to DatabaseSource enum")
        print(f"3. Test in app")

    except Exception as e:
        print(f"\n✗ Conversion failed: {e}")
        import traceback
        traceback.print_exc()
    finally:
        source_conn.close()
        dest_conn.close()


if __name__ == "__main__":
    main()
