#!/usr/bin/env python3
"""
Fix IMVBox database schema to match Room entities exactly
"""
import sqlite3
import shutil
from pathlib import Path

SRC_DB = Path(__file__).parent / "imvbox_content.db"
DST_DB = Path(__file__).parent / "imvbox_content_fixed.db"

def main():
    print("Fixing IMVBox database schema...")

    # Remove old fixed db if exists
    if DST_DB.exists():
        DST_DB.unlink()

    conn = sqlite3.connect(DST_DB)
    cur = conn.cursor()

    # Create tables with EXACT Room schema
    cur.executescript("""
        -- cached_movies (matches CachedMovie entity)
        CREATE TABLE cached_movies (
            id INTEGER PRIMARY KEY NOT NULL,
            title TEXT NOT NULL,
            posterUrl TEXT,
            farsilandUrl TEXT NOT NULL,
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
        CREATE UNIQUE INDEX IF NOT EXISTS index_cached_movies_farsilandUrl ON cached_movies(farsilandUrl);
        CREATE INDEX IF NOT EXISTS index_cached_movies_dateAdded ON cached_movies(dateAdded);

        -- cached_series (matches CachedSeries entity)
        CREATE TABLE cached_series (
            id INTEGER PRIMARY KEY NOT NULL,
            title TEXT NOT NULL,
            posterUrl TEXT,
            backdropUrl TEXT,
            farsilandUrl TEXT NOT NULL,
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
        CREATE UNIQUE INDEX IF NOT EXISTS index_cached_series_farsilandUrl ON cached_series(farsilandUrl);
        CREATE INDEX IF NOT EXISTS index_cached_series_dateAdded ON cached_series(dateAdded);

        -- cached_episodes (matches CachedEpisode entity)
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
        CREATE UNIQUE INDEX IF NOT EXISTS index_cached_episodes_seriesId_season_episode ON cached_episodes(seriesId, season, episode);
        CREATE UNIQUE INDEX IF NOT EXISTS index_cached_episodes_farsilandUrl ON cached_episodes(farsilandUrl);
        CREATE INDEX IF NOT EXISTS index_cached_episodes_dateAdded ON cached_episodes(dateAdded);

        -- cached_genres (matches CachedGenre entity)
        CREATE TABLE cached_genres (
            id INTEGER PRIMARY KEY NOT NULL,
            name TEXT NOT NULL,
            slug TEXT NOT NULL
        );

        -- cached_video_urls (matches CachedVideoUrl entity)
        CREATE TABLE cached_video_urls (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            contentId INTEGER NOT NULL,
            contentType TEXT NOT NULL,
            quality TEXT NOT NULL,
            mp4Url TEXT NOT NULL,
            fileSizeMB REAL,
            cachedAt INTEGER NOT NULL
        );
        CREATE UNIQUE INDEX IF NOT EXISTS index_cached_video_urls_contentId_contentType_quality ON cached_video_urls(contentId, contentType, quality);

        -- FTS4 tables (EXACT Room format with backticks and types)
        CREATE VIRTUAL TABLE `cached_movies_fts` USING FTS4(`title` TEXT NOT NULL, content=`cached_movies`);
        CREATE VIRTUAL TABLE `cached_series_fts` USING FTS4(`title` TEXT NOT NULL, content=`cached_series`);
        CREATE VIRTUAL TABLE `cached_episodes_fts` USING FTS4(`seriesTitle` TEXT, `title` TEXT NOT NULL, content=`cached_episodes`);

        -- Room FTS sync triggers for movies
        CREATE TRIGGER room_fts_content_sync_cached_movies_fts_BEFORE_UPDATE BEFORE UPDATE ON `cached_movies` BEGIN DELETE FROM `cached_movies_fts` WHERE `docid`=OLD.`rowid`; END;
        CREATE TRIGGER room_fts_content_sync_cached_movies_fts_BEFORE_DELETE BEFORE DELETE ON `cached_movies` BEGIN DELETE FROM `cached_movies_fts` WHERE `docid`=OLD.`rowid`; END;
        CREATE TRIGGER room_fts_content_sync_cached_movies_fts_AFTER_UPDATE AFTER UPDATE ON `cached_movies` BEGIN INSERT INTO `cached_movies_fts`(`docid`, `title`) VALUES (NEW.`rowid`, NEW.`title`); END;
        CREATE TRIGGER room_fts_content_sync_cached_movies_fts_AFTER_INSERT AFTER INSERT ON `cached_movies` BEGIN INSERT INTO `cached_movies_fts`(`docid`, `title`) VALUES (NEW.`rowid`, NEW.`title`); END;

        -- Room FTS sync triggers for series
        CREATE TRIGGER room_fts_content_sync_cached_series_fts_BEFORE_UPDATE BEFORE UPDATE ON `cached_series` BEGIN DELETE FROM `cached_series_fts` WHERE `docid`=OLD.`rowid`; END;
        CREATE TRIGGER room_fts_content_sync_cached_series_fts_BEFORE_DELETE BEFORE DELETE ON `cached_series` BEGIN DELETE FROM `cached_series_fts` WHERE `docid`=OLD.`rowid`; END;
        CREATE TRIGGER room_fts_content_sync_cached_series_fts_AFTER_UPDATE AFTER UPDATE ON `cached_series` BEGIN INSERT INTO `cached_series_fts`(`docid`, `title`) VALUES (NEW.`rowid`, NEW.`title`); END;
        CREATE TRIGGER room_fts_content_sync_cached_series_fts_AFTER_INSERT AFTER INSERT ON `cached_series` BEGIN INSERT INTO `cached_series_fts`(`docid`, `title`) VALUES (NEW.`rowid`, NEW.`title`); END;

        -- Room FTS sync triggers for episodes
        CREATE TRIGGER room_fts_content_sync_cached_episodes_fts_BEFORE_UPDATE BEFORE UPDATE ON `cached_episodes` BEGIN DELETE FROM `cached_episodes_fts` WHERE `docid`=OLD.`rowid`; END;
        CREATE TRIGGER room_fts_content_sync_cached_episodes_fts_BEFORE_DELETE BEFORE DELETE ON `cached_episodes` BEGIN DELETE FROM `cached_episodes_fts` WHERE `docid`=OLD.`rowid`; END;
        CREATE TRIGGER room_fts_content_sync_cached_episodes_fts_AFTER_UPDATE AFTER UPDATE ON `cached_episodes` BEGIN INSERT INTO `cached_episodes_fts`(`docid`, `seriesTitle`, `title`) VALUES (NEW.`rowid`, NEW.`seriesTitle`, NEW.`title`); END;
        CREATE TRIGGER room_fts_content_sync_cached_episodes_fts_AFTER_INSERT AFTER INSERT ON `cached_episodes` BEGIN INSERT INTO `cached_episodes_fts`(`docid`, `seriesTitle`, `title`) VALUES (NEW.`rowid`, NEW.`seriesTitle`, NEW.`title`); END;

        -- Room metadata table
        CREATE TABLE room_master_table (
            id INTEGER PRIMARY KEY,
            identity_hash TEXT
        );
    """)

    # Attach source database and copy data
    cur.execute(f"ATTACH DATABASE '{SRC_DB}' AS src")

    # Copy movies (only columns that Room expects)
    cur.execute("""
        INSERT INTO cached_movies (id, title, posterUrl, farsilandUrl, description, year, rating, runtime, director, "cast", genres, dateAdded, lastUpdated)
        SELECT id, title, posterUrl, farsilandUrl, description, year, rating, runtime, director, "cast", genres, dateAdded, lastUpdated
        FROM src.cached_movies
    """)
    movie_count = cur.rowcount
    print(f"  Copied {movie_count} movies")

    # Copy series (only columns that Room expects)
    cur.execute("""
        INSERT INTO cached_series (id, title, posterUrl, backdropUrl, farsilandUrl, description, year, rating, totalSeasons, totalEpisodes, "cast", genres, dateAdded, lastUpdated)
        SELECT id, title, posterUrl, backdropUrl, farsilandUrl, description, year, rating, totalSeasons, totalEpisodes, "cast", genres, dateAdded, lastUpdated
        FROM src.cached_series
    """)
    series_count = cur.rowcount
    print(f"  Copied {series_count} series")

    # Copy episodes (all columns match)
    cur.execute("""
        INSERT INTO cached_episodes (id, seriesId, seriesTitle, episodeId, season, episode, title, description, thumbnailUrl, farsilandUrl, airDate, runtime, dateAdded, lastUpdated)
        SELECT id, seriesId, seriesTitle, episodeId, season, episode, title, description, thumbnailUrl, farsilandUrl, airDate, runtime, dateAdded, lastUpdated
        FROM src.cached_episodes
    """)
    episode_count = cur.rowcount
    print(f"  Copied {episode_count} episodes")

    # Populate FTS tables
    cur.execute("INSERT INTO cached_movies_fts(cached_movies_fts) VALUES('rebuild')")
    cur.execute("INSERT INTO cached_series_fts(cached_series_fts) VALUES('rebuild')")
    cur.execute("INSERT INTO cached_episodes_fts(cached_episodes_fts) VALUES('rebuild')")
    print("  Built FTS indexes")

    # Set Room identity hash (ContentDatabase version 2)
    # This hash must match what Room generates for the schema
    cur.execute("INSERT INTO room_master_table (id, identity_hash) VALUES (42, 'imvbox_v2')")

    # Commit before detach
    conn.commit()

    # Detach can fail but data is already committed
    try:
        cur.execute("DETACH DATABASE src")
    except:
        pass

    conn.close()

    print(f"\nFixed database saved to: {DST_DB}")
    print(f"Size: {DST_DB.stat().st_size / 1024 / 1024:.2f} MB")

if __name__ == '__main__':
    main()
