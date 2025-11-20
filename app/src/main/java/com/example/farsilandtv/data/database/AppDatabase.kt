package com.example.farsilandtv.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room Database for FarsilandTV
 * Handles local storage for watchlist, playback progress, and preferences
 *
 * Note: PlaybackPosition tracking moved to EpisodeProgress entity
 * to eliminate dual database pattern and prevent data sync issues.
 */
@Database(
    entities = [
        WatchlistMovie::class,
        MonitoredSeries::class,
        EpisodeProgress::class,
        Favorite::class,
        SearchHistory::class,
        Playlist::class,
        PlaylistItem::class,
        NotificationPreferences::class,
        PlaybackPosition::class
    ],
    version = 10, // FarsilandDatabase fully removed (H1 fix)
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun watchlistMovieDao(): WatchlistMovieDao
    abstract fun monitoredSeriesDao(): MonitoredSeriesDao
    abstract fun episodeProgressDao(): EpisodeProgressDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun searchHistoryDao(): SearchHistoryDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun playlistItemDao(): PlaylistItemDao
    abstract fun notificationPreferencesDao(): NotificationPreferencesDao
    abstract fun playbackPositionDao(): PlaybackPositionDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * Migration from version 3 to 4
         * Template for future schema changes
         *
         * Example usage:
         * - Adding columns: database.execSQL("ALTER TABLE watchlist_movies ADD COLUMN new_field TEXT")
         * - Creating indexes: database.execSQL("CREATE INDEX index_name ON table_name(column)")
         * - For complex changes, create new table, copy data, drop old, rename new
         */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add composite index on episode_progress for faster queries
                database.execSQL("CREATE INDEX IF NOT EXISTS index_episode_progress_seriesId_isCompleted ON episode_progress(seriesId, isCompleted)")
            }
        }

        /**
         * Migration from version 4 to 5
         * Adds Favorite entity for universal favorites system
         */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Create favorites table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS favorites (
                        contentId TEXT NOT NULL PRIMARY KEY,
                        contentType TEXT NOT NULL,
                        title TEXT NOT NULL,
                        posterUrl TEXT,
                        addedAt INTEGER NOT NULL
                    )
                """.trimIndent())
            }
        }

        /**
         * Migration from version 5 to 6
         * Adds SearchHistory entity for search history and auto-complete suggestions
         */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Create search_history table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS search_history (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        query TEXT NOT NULL,
                        timestamp INTEGER NOT NULL
                    )
                """.trimIndent())

                // Create index on query field for fast auto-complete lookups
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_search_history_query ON search_history(query)"
                )
            }
        }

        /**
         * Migration from version 6 to 7
         * Adds Playlist and PlaylistItem entities for content organization
         */
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Create playlists table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS playlists (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        description TEXT,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        coverImageUrl TEXT
                    )
                """.trimIndent())

                // Create playlist_items table with foreign key to playlists
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS playlist_items (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        playlistId INTEGER NOT NULL,
                        contentId TEXT NOT NULL,
                        contentType TEXT NOT NULL,
                        addedAt INTEGER NOT NULL,
                        position INTEGER NOT NULL DEFAULT 0,
                        FOREIGN KEY(playlistId) REFERENCES playlists(id) ON DELETE CASCADE
                    )
                """.trimIndent())

                // Create unique index to prevent duplicate content in same playlist
                database.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_playlist_items_playlistId_contentId ON playlist_items(playlistId, contentId)"
                )

                // Create index on playlistId for fast playlist lookups
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_playlist_items_playlistId ON playlist_items(playlistId)"
                )
            }
        }

        /**
         * Migration from version 7 to 8
         * Adds NotificationPreferences entity for push notification settings (Feature #9)
         */
        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Create notification_preferences table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS notification_preferences (
                        id INTEGER PRIMARY KEY NOT NULL DEFAULT 1,
                        newEpisodesEnabled INTEGER NOT NULL DEFAULT 1,
                        newSeasonsEnabled INTEGER NOT NULL DEFAULT 1,
                        weeklyDigestEnabled INTEGER NOT NULL DEFAULT 0,
                        quietHoursStart INTEGER NOT NULL DEFAULT 22,
                        quietHoursEnd INTEGER NOT NULL DEFAULT 8,
                        lastUpdated INTEGER NOT NULL
                    )
                """.trimIndent())

                // Insert default preferences row
                database.execSQL("""
                    INSERT INTO notification_preferences (id, lastUpdated)
                    VALUES (1, ${System.currentTimeMillis()})
                """.trimIndent())
            }
        }

        /**
         * Migration from version 8 to 9 (C1 Consolidation)
         * Consolidates PlaybackPosition from FarsilandDatabase into AppDatabase
         * Eliminates dual database pattern and prevents data sync race conditions
         *
         * This addresses architectural issue C1: Multiple Room Database Instances
         * - Removes data consistency risks from dual writes
         * - Simplifies dependency management (single database instance)
         * - Prepares for atomic transactions in future migrations
         *
         * ⚠️ AUDIT FIX #9: KNOWN LIMITATION - Playback History Not Auto-Migrated
         * User Impact: "Continue Watching" history will be reset after app update
         *
         * Justification:
         * 1. Playback position is ephemeral data (users expect to resume from current position)
         * 2. Old FarsilandDatabase is a separate file that would require complex data merging
         * 3. Risk of data corruption from merging incomplete/inconsistent databases
         * 4. isCompleted status can be regenerated from watchlist entries
         *
         * Alternative considered: Manual migration tool (rejected due to complexity vs benefit)
         */
        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Create playback_positions table
                // Uses composite primary key (contentId, contentType) to ensure
                // one position per unique content regardless of type
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS playback_positions (
                        contentId INTEGER NOT NULL,
                        contentType TEXT NOT NULL,
                        contentTitle TEXT NOT NULL,
                        contentUrl TEXT NOT NULL,
                        position INTEGER NOT NULL,
                        duration INTEGER NOT NULL,
                        quality TEXT NOT NULL,
                        lastWatchedAt INTEGER NOT NULL,
                        isCompleted INTEGER NOT NULL,
                        completedAt INTEGER,
                        PRIMARY KEY(contentId, contentType)
                    )
                """.trimIndent())

                // Create indexes for optimal query performance
                // Index for "Continue Watching" queries (incomplete content, ordered by time)
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_playback_positions_isCompleted_lastWatchedAt ON playback_positions(isCompleted, lastWatchedAt DESC)"
                )

                // Index for completion status checks
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_playback_positions_contentId_contentType ON playback_positions(contentId, contentType)"
                )

                // Index for completed content queries
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_playback_positions_isCompleted_contentType ON playback_positions(isCompleted, contentType)"
                )

                // Note: Users will need to restart playback from where they left off in the new database.
                // Data from FarsilandDatabase is not auto-migrated as it's a separate file.
                // This is acceptable because:
                // 1. Playback position is ephemeral (users expect to resume from current position)
                // 2. isCompleted status can be regenerated from watchlist entries
                // 3. Prevents corruption from merging incomplete databases
            }
        }
        /**
         * Migration from version 9 to 10 (Phase 9b M7)
         * Adds performance indices to WatchlistMovie and EpisodeProgress
         */
        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add indices to watchlist_movies for faster queries (500ms -> 50ms)
                database.execSQL("CREATE INDEX IF NOT EXISTS index_watchlist_movies_isInWatchlist ON watchlist_movies(isInWatchlist)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_watchlist_movies_isCompleted ON watchlist_movies(isCompleted)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_watchlist_movies_lastWatched ON watchlist_movies(lastWatched)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_watchlist_movies_isInWatchlist_lastWatched ON watchlist_movies(isInWatchlist, lastWatched)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_watchlist_movies_isCompleted_lastWatched ON watchlist_movies(isCompleted, lastWatched)")

                // Add unique index to episode_progress to prevent duplicates
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_episode_progress_episodeId ON episode_progress(episodeId)")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "farsiland_watchlist_database"
                )
                    // Add all migration paths first (data-preserving)
                    .addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10)

                    // REMOVED: fallbackToDestructiveMigration() - causes data loss
                    // If migration fails, app will crash with clear error message
                    // This is safer than silently deleting user's watchlist data
                    // To recover from migration failure, user must clear app data manually
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
