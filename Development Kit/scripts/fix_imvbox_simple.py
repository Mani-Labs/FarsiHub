#!/usr/bin/env python3
"""
Create MINIMAL IMVBox database - same as FarsiPlex structure
Room will handle FTS creation via migrations
"""
import sqlite3
from pathlib import Path

SRC_DB = Path(__file__).parent / "imvbox_content.db"
DST_DB = Path(__file__).parent / "imvbox_content_minimal.db"

def main():
    print("Creating minimal IMVBox database (like FarsiPlex)...")

    if DST_DB.exists():
        DST_DB.unlink()

    conn = sqlite3.connect(DST_DB)
    cur = conn.cursor()

    # Create MINIMAL tables - SAME as farsiplex_content.db
    # NO FTS, NO room_master_table, NO triggers
    cur.executescript("""
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
            farsilandUrl TEXT NOT NULL,
            airDate TEXT,
            runtime INTEGER,
            dateAdded INTEGER NOT NULL,
            lastUpdated INTEGER NOT NULL
        );

        CREATE TABLE cached_video_urls (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            contentId INTEGER NOT NULL,
            contentType TEXT NOT NULL,
            quality TEXT NOT NULL,
            mp4Url TEXT NOT NULL,
            fileSizeMB REAL,
            cachedAt INTEGER NOT NULL
        );
    """)

    # Attach source and copy data
    cur.execute(f"ATTACH DATABASE '{SRC_DB}' AS src")

    cur.execute("""
        INSERT INTO cached_movies (id, title, posterUrl, farsilandUrl, description, year, rating, runtime, director, "cast", genres, dateAdded, lastUpdated)
        SELECT id, title, posterUrl, farsilandUrl, description, year, rating, runtime, director, "cast", genres, dateAdded, lastUpdated
        FROM src.cached_movies
    """)
    print(f"  Copied {cur.rowcount} movies")

    cur.execute("""
        INSERT INTO cached_series (id, title, posterUrl, backdropUrl, farsilandUrl, description, year, rating, totalSeasons, totalEpisodes, "cast", genres, dateAdded, lastUpdated)
        SELECT id, title, posterUrl, backdropUrl, farsilandUrl, description, year, rating, totalSeasons, totalEpisodes, "cast", genres, dateAdded, lastUpdated
        FROM src.cached_series
    """)
    print(f"  Copied {cur.rowcount} series")

    cur.execute("""
        INSERT INTO cached_episodes (id, seriesId, seriesTitle, episodeId, season, episode, title, description, thumbnailUrl, farsilandUrl, airDate, runtime, dateAdded, lastUpdated)
        SELECT id, seriesId, seriesTitle, episodeId, season, episode, title, description, thumbnailUrl, farsilandUrl, airDate, runtime, dateAdded, lastUpdated
        FROM src.cached_episodes
    """)
    print(f"  Copied {cur.rowcount} episodes")

    conn.commit()

    try:
        cur.execute("DETACH DATABASE src")
    except:
        pass

    conn.close()

    print(f"\nMinimal database saved to: {DST_DB}")
    print(f"Size: {DST_DB.stat().st_size / 1024 / 1024:.2f} MB")

if __name__ == '__main__':
    main()
