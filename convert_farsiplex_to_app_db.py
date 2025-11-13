#!/usr/bin/env python3
"""
Convert Farsiplex.db to Android app database format

This script converts the FarsiPlex database schema to match
the Android app's ContentDatabase schema (same as Farsiland).
"""

import sqlite3
import sys
import os

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

    # Create app database schema
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

    # Convert video URLs
    print("Converting video URLs...")
    convert_video_urls(source_cursor, dest_cursor)

    # Commit and close
    dest_conn.commit()
    source_conn.close()
    dest_conn.close()

    print(f"Conversion complete: {output_db}")
    print_stats(output_db)


def create_app_schema(cursor):
    """Create Android app database schema"""

    # CachedMovie table
    cursor.execute("""
        CREATE TABLE IF NOT EXISTS CachedMovie (
            id INTEGER PRIMARY KEY NOT NULL,
            title TEXT NOT NULL,
            titleEn TEXT,
            titleFa TEXT,
            url TEXT NOT NULL,
            posterUrl TEXT,
            rating REAL,
            releaseDate TEXT,
            country TEXT,
            synopsisEn TEXT,
            synopsisFa TEXT,
            duration TEXT,
            contentType TEXT NOT NULL DEFAULT 'movie',
            lastUpdated INTEGER NOT NULL
        )
    """)

    # CachedSeries table
    cursor.execute("""
        CREATE TABLE IF NOT EXISTS CachedSeries (
            id INTEGER PRIMARY KEY NOT NULL,
            title TEXT NOT NULL,
            titleEn TEXT,
            titleFa TEXT,
            url TEXT NOT NULL,
            posterUrl TEXT,
            rating REAL,
            releaseDate TEXT,
            status TEXT,
            synopsisEn TEXT,
            synopsisFa TEXT,
            contentType TEXT NOT NULL DEFAULT 'series',
            lastUpdated INTEGER NOT NULL
        )
    """)

    # CachedEpisode table
    cursor.execute("""
        CREATE TABLE IF NOT EXISTS CachedEpisode (
            id INTEGER PRIMARY KEY NOT NULL,
            seriesId INTEGER NOT NULL,
            seasonNumber INTEGER NOT NULL,
            episodeNumber INTEGER NOT NULL,
            title TEXT,
            url TEXT NOT NULL,
            thumbnailUrl TEXT,
            releaseDate TEXT,
            lastUpdated INTEGER NOT NULL,
            FOREIGN KEY (seriesId) REFERENCES CachedSeries(id) ON DELETE CASCADE
        )
    """)

    # CachedGenre table
    cursor.execute("""
        CREATE TABLE IF NOT EXISTS CachedGenre (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            contentId INTEGER NOT NULL,
            contentType TEXT NOT NULL,
            genre TEXT NOT NULL
        )
    """)

    # CachedVideoUrl table
    cursor.execute("""
        CREATE TABLE IF NOT EXISTS CachedVideoUrl (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            contentId INTEGER NOT NULL,
            contentType TEXT NOT NULL,
            quality TEXT NOT NULL,
            url TEXT NOT NULL,
            cdnSource TEXT,
            playerType TEXT
        )
    """)

    # Create indexes
    cursor.execute("CREATE INDEX IF NOT EXISTS idx_movie_id ON CachedMovie(id)")
    cursor.execute("CREATE INDEX IF NOT EXISTS idx_series_id ON CachedSeries(id)")
    cursor.execute("CREATE INDEX IF NOT EXISTS idx_episode_series ON CachedEpisode(seriesId)")
    cursor.execute("CREATE INDEX IF NOT EXISTS idx_genre_content ON CachedGenre(contentId, contentType)")
    cursor.execute("CREATE INDEX IF NOT EXISTS idx_video_content ON CachedVideoUrl(contentId, contentType)")


def convert_movies(source_cursor, dest_cursor):
    """Convert movies table"""

    source_cursor.execute("""
        SELECT id, title, title_en, title_fa, url, poster_url,
               rating, release_date, country, synopsis_en, synopsis_fa, duration
        FROM movies
    """)

    import time
    current_time = int(time.time() * 1000)  # milliseconds

    for row in source_cursor.fetchall():
        dest_cursor.execute("""
            INSERT INTO CachedMovie (
                id, title, titleEn, titleFa, url, posterUrl,
                rating, releaseDate, country, synopsisEn, synopsisFa,
                duration, contentType, lastUpdated
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'movie', ?)
        """, (*row, current_time))


def convert_tvshows_and_episodes(source_cursor, dest_cursor):
    """Convert TV shows and episodes"""

    import time
    current_time = int(time.time() * 1000)

    # Convert TV shows
    source_cursor.execute("""
        SELECT id, title, title_en, title_fa, url, poster_url,
               rating, release_date, status, synopsis_en, synopsis_fa
        FROM tvshows
    """)

    for row in source_cursor.fetchall():
        dest_cursor.execute("""
            INSERT INTO CachedSeries (
                id, title, titleEn, titleFa, url, posterUrl,
                rating, releaseDate, status, synopsisEn, synopsisFa,
                contentType, lastUpdated
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'series', ?)
        """, (*row, current_time))

    # Convert episodes (join with seasons to get season_number)
    source_cursor.execute("""
        SELECT e.id, e.tvshow_id, s.season_number, e.episode_number,
               e.title, e.url, e.thumbnail_url, e.release_date
        FROM episodes e
        JOIN seasons s ON e.season_id = s.id
    """)

    for row in source_cursor.fetchall():
        dest_cursor.execute("""
            INSERT INTO CachedEpisode (
                id, seriesId, seasonNumber, episodeNumber,
                title, url, thumbnailUrl, releaseDate, lastUpdated
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """, (*row, current_time))


def convert_genres(source_cursor, dest_cursor):
    """Convert movie and TV show genres"""

    # Movie genres
    source_cursor.execute("SELECT movie_id, genre FROM movie_genres")
    for movie_id, genre in source_cursor.fetchall():
        dest_cursor.execute("""
            INSERT INTO CachedGenre (contentId, contentType, genre)
            VALUES (?, 'movie', ?)
        """, (movie_id, genre))

    # TV show genres
    source_cursor.execute("SELECT tvshow_id, genre FROM tvshow_genres")
    for tvshow_id, genre in source_cursor.fetchall():
        dest_cursor.execute("""
            INSERT INTO CachedGenre (contentId, contentType, genre)
            VALUES (?, 'series', ?)
        """, (tvshow_id, genre))


def convert_video_urls(source_cursor, dest_cursor):
    """Convert movie and episode video URLs"""

    # Movie videos
    source_cursor.execute("""
        SELECT movie_id, quality, url, cdn_source, player_type
        FROM movie_videos
    """)
    for movie_id, quality, url, cdn_source, player_type in source_cursor.fetchall():
        dest_cursor.execute("""
            INSERT INTO CachedVideoUrl (contentId, contentType, quality, url, cdnSource, playerType)
            VALUES (?, 'movie', ?, ?, ?, ?)
        """, (movie_id, quality, url, cdn_source, player_type))

    # Episode videos
    source_cursor.execute("""
        SELECT episode_id, quality, url, cdn_source, player_type
        FROM episode_videos
    """)
    for episode_id, quality, url, cdn_source, player_type in source_cursor.fetchall():
        dest_cursor.execute("""
            INSERT INTO CachedVideoUrl (contentId, contentType, quality, url, cdnSource, playerType)
            VALUES (?, 'episode', ?, ?, ?, ?)
        """, (episode_id, quality, url, cdn_source, player_type))


def print_stats(db_path: str):
    """Print database statistics"""

    conn = sqlite3.connect(db_path)
    cursor = conn.cursor()

    cursor.execute("SELECT COUNT(*) FROM CachedMovie")
    movie_count = cursor.fetchone()[0]

    cursor.execute("SELECT COUNT(*) FROM CachedSeries")
    series_count = cursor.fetchone()[0]

    cursor.execute("SELECT COUNT(*) FROM CachedEpisode")
    episode_count = cursor.fetchone()[0]

    cursor.execute("SELECT COUNT(*) FROM CachedVideoUrl WHERE contentType = 'movie'")
    movie_video_count = cursor.fetchone()[0]

    cursor.execute("SELECT COUNT(*) FROM CachedVideoUrl WHERE contentType = 'episode'")
    episode_video_count = cursor.fetchone()[0]

    conn.close()

    print(f"\nDatabase Statistics:")
    print(f"  Movies: {movie_count}")
    print(f"  TV Shows: {series_count}")
    print(f"  Episodes: {episode_count}")
    print(f"  Movie Videos: {movie_video_count}")
    print(f"  Episode Videos: {episode_video_count}")
    print(f"  Total Video URLs: {movie_video_count + episode_video_count}")


if __name__ == '__main__':
    input_db = 'Farsiplex.db'
    output_db = 'farsiplex_content.db'

    if not os.path.exists(input_db):
        print(f"Error: {input_db} not found!")
        sys.exit(1)

    convert_database(input_db, output_db)

    print(f"\nReady to use in Android app!")
    print(f"  Copy {output_db} to: app/src/main/assets/databases/")
