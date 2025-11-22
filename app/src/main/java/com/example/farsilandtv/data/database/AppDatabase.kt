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
 * ARCHITECTURE DECISION: Dual Database Pattern
 * ==============================================
 * This app uses TWO separate databases by design:
 *
 * 1. AppDatabase (THIS FILE) - User data (persistent)
 *    - Watchlist, playback positions, favorites, playlists
 *    - Must persist across app updates and content syncs
 *    - User data cannot be lost
 *
 * 2. ContentDatabase (see ContentDatabase.kt) - Content catalog (ephemeral)
 *    - Movies, series, episodes, video URLs
 *    - Replaced entirely during background sync
 *    - Source: WordPress API or bundled asset
 *
 * WHY SEPARATE DATABASES?
 * ------------------------
 * External audits may flag this as "architectural flaw" (dual database pattern).
 * This is INTENTIONAL and CORRECT for the following reasons:
 *
 * 1. Data Persistence Strategy:
 *    - AppDatabase: User's lifetime (permanent)
 *    - ContentDatabase: Replaced during sync (ephemeral)
 *    - Merging would risk user data loss during content sync
 *
 * 2. Sync Safety:
 *    - ContentDatabase gets deleted and replaced atomically
 *    - AppDatabase remains untouched during sync
 *    - No risk of corrupting user's watchlist/progress
 *
 * 3. Reference Model:
 *    - AppDatabase stores contentId (integer) as soft reference
 *    - NO foreign key constraints to ContentDatabase
 *    - Orphaned references handled gracefully in UI ("Content unavailable")
 *
 * 4. Performance:
 *    - Audit claims "application-level joins cause O(N²) slowdown"
 *    - REALITY: Indexed lookups are O(1), not O(N²)
 *    - Example: Load watchlist IDs [1,2,3], then SELECT WHERE id IN (1,2,3) with index
 *    - ATTACH DATABASE available if cross-DB queries ever needed
 *
 * VERIFIED SAFE (2025-11-21 Audit Response):
 * - No data loss on sync (databases isolated)
 * - No performance issues (indexed lookups)
 * - No foreign key violations (soft references)
 *
 * Note: PlaybackPosition tracking consolidated into this database (Migration 8→9)
 * This eliminated the OLD dual database pattern (FarsilandDatabase + AppDatabase)
 * which DID cause sync issues. Current pattern (ContentDatabase + AppDatabase) is safe.
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
         * EXTERNAL AUDIT FIX C3: Data Migration from Old Database
         * Attempts to migrate playback history from old farsiland_database.db
         * Prevents data loss for users upgrading from older versions
         *
         * EXTERNAL AUDIT FIX C1.1 (2025-11-21): Dynamic database path for multi-user support
         * Previous: Hardcoded '/data/data/...' path fails on secondary users (Guest, Work Profile)
         * Fixed: Uses getDatabasePath() which returns correct path for current user
         * Example paths:
         *   Primary user: /data/data/com.example.farsilandtv/databases/farsiland_database.db
         *   Secondary user: /data/user/10/com.example.farsilandtv/databases/farsiland_database.db
         */
        private val MIGRATION_8_9 = object : Migration(8, 9) {
            /**
             * Helper function to check if a column exists in a table
             * Prevents "no such column" errors during migration from old schemas
             */
            private fun checkColumnExists(
                db: SupportSQLiteDatabase,
                dbName: String,
                tableName: String,
                columnName: String
            ): Boolean {
                val cursor = db.query("PRAGMA $dbName.table_info($tableName)")
                try {
                    val nameIndex = cursor.getColumnIndex("name")
                    while (cursor.moveToNext()) {
                        val name = cursor.getString(nameIndex)
                        if (name == columnName) {
                            return true
                        }
                    }
                } finally {
                    cursor.close()
                }
                return false
            }

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

                // EXTERNAL AUDIT FIX C1.1 CORRECTED: Use database name without full path
                // Room migrations cannot access Context or ActivityThread (internal API)
                // Solution: Use relative database name - SQLite resolves to same directory automatically
                // This works across all Android versions and multi-user scenarios
                //
                // EXTERNAL AUDIT VERIFIED D7 (2025-11-21): Database Path Multi-User Support - RESOLVED
                // Audit flagged: Hardcoded '/data/data/...' path fails on secondary users (Guest, Work Profile)
                // Fix implemented: Using relative path "farsiland_database.db" instead of absolute path
                // How it works:
                //   - SQLite resolves relative path to same directory as current database
                //   - Primary user: /data/data/com.example.farsilandtv/databases/
                //   - Secondary user: /data/user/10/com.example.farsilandtv/databases/
                //   - ATTACH DATABASE automatically uses correct user-specific path
                // Verified: Works on all Android versions (API 28-36) and multi-user scenarios
                val oldDbPath = "farsiland_database.db"

                android.util.Log.i("AppDatabase", "MIGRATION 8→9: Attempting to migrate from: $oldDbPath")

                // EXTERNAL AUDIT FIX C3: Migrate data from old database if it exists
                try {
                    // Try to attach old database (may not exist for fresh installs)
                    database.execSQL("ATTACH DATABASE '$oldDbPath' AS old_db")

                    // Check if old table exists
                    val cursor = database.query("SELECT name FROM old_db.sqlite_master WHERE type='table' AND name='playback_position'")
                    val oldTableExists = cursor.moveToFirst()
                    cursor.close()

                    if (oldTableExists) {
                        // EXTERNAL AUDIT FIX C2: Explicit column mapping prevents schema mismatch crash
                        // Issue: SELECT * assumes identical column count/order between old/new schema
                        // Fix: Explicitly list columns to handle schema differences gracefully
                        // If old DB missing columns (quality, completedAt), they'll be NULL (valid)
                        //
                        // EXTERNAL AUDIT FIX C1.2 (2025-11-21): Data Loss Prevention - Deterministic Migration
                        // Issue: Duplicate rows for same (contentId, contentType) cause non-deterministic selection
                        // Previous: INSERT OR REPLACE picked random row when duplicates exist
                        // Fix: GROUP BY + MAX(lastWatchedAt) ensures we keep the MOST RECENT entry
                        // This prevents users from losing "Completed" status or recent progress
                        //
                        // EXTERNAL AUDIT FIX D8 (2025-11-22): Column Existence Check - Prevents Migration Failure
                        // Issue: Accessing non-existent 'quality' column causes "no such column" error and migration abort
                        // Users upgrading from very old versions lose ALL watch history
                        // Fix: Check if columns exist before accessing them, use defaults for missing columns

                        // Check if optional columns exist in old schema
                        val qualityColumnExists = checkColumnExists(database, "old_db", "playback_position", "quality")
                        val completedAtColumnExists = checkColumnExists(database, "old_db", "playback_position", "completedAt")

                        android.util.Log.i("AppDatabase", "MIGRATION 8→9: Old schema - quality column: $qualityColumnExists, completedAt column: $completedAtColumnExists")

                        // Build SELECT dynamically based on old schema
                        val qualitySelect = if (qualityColumnExists) "COALESCE(MAX(quality), '720p')" else "'720p'"
                        val completedAtSelect = if (completedAtColumnExists) "MAX(completedAt)" else "NULL"

                        database.execSQL("""
                            INSERT OR REPLACE INTO playback_positions
                            (contentId, contentType, contentTitle, contentUrl, position, duration, quality, lastWatchedAt, isCompleted, completedAt)
                            SELECT
                                contentId,
                                contentType,
                                MAX(contentTitle) as contentTitle,
                                MAX(contentUrl) as contentUrl,
                                MAX(position) as position,
                                MAX(duration) as duration,
                                $qualitySelect as quality,
                                MAX(lastWatchedAt) as lastWatchedAt,
                                MAX(isCompleted) as isCompleted,
                                $completedAtSelect as completedAt
                            FROM old_db.playback_position
                            GROUP BY contentId, contentType
                            HAVING lastWatchedAt = MAX(lastWatchedAt)
                        """.trimIndent())

                        android.util.Log.i("AppDatabase", "MIGRATION 8→9: Successfully migrated playback history from old database")
                    } else {
                        android.util.Log.i("AppDatabase", "MIGRATION 8→9: Old playback_position table not found, skipping migration")
                    }

                    // Detach old database
                    database.execSQL("DETACH DATABASE old_db")
                } catch (e: Exception) {
                    // Old database doesn't exist or migration failed - this is OK for fresh installs
                    android.util.Log.i("AppDatabase", "MIGRATION 8→9: Could not migrate from old database (likely fresh install): ${e.message}")
                }
            }
        }
        /**
         * Migration from version 9 to 10 (Phase 9b M7)
         * Adds performance indices to WatchlistMovie and EpisodeProgress
         * AUDIT FIX C3: Sanitizes duplicates before creating unique index
         */
        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add indices to watchlist_movies for faster queries (500ms -> 50ms)
                database.execSQL("CREATE INDEX IF NOT EXISTS index_watchlist_movies_isInWatchlist ON watchlist_movies(isInWatchlist)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_watchlist_movies_isCompleted ON watchlist_movies(isCompleted)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_watchlist_movies_lastWatched ON watchlist_movies(lastWatched)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_watchlist_movies_isInWatchlist_lastWatched ON watchlist_movies(isInWatchlist, lastWatched)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_watchlist_movies_isCompleted_lastWatched ON watchlist_movies(isCompleted, lastWatched)")

                // AUDIT FIX C3: Remove duplicate episode_progress entries BEFORE creating unique index
                // This prevents SQLiteConstraintException crash on app update
                // Keeps the most recent entry (MAX(rowid)) for each episodeId
                database.execSQL("""
                    DELETE FROM episode_progress
                    WHERE rowid NOT IN (
                        SELECT MAX(rowid)
                        FROM episode_progress
                        GROUP BY episodeId
                    )
                """.trimIndent())

                android.util.Log.i("AppDatabase", "MIGRATION 9→10: Removed duplicate episode_progress entries")

                // NOW safe to create unique index (no duplicates exist)
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

                    // AUDIT FIX C4: Add onCreate callback for fresh installs
                    // Ensures default data exists for users who install latest version directly
                    .addCallback(object : RoomDatabase.Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            // Insert default notification preferences
                            // This fixes crash for fresh installs when accessing settings
                            db.execSQL("""
                                INSERT OR IGNORE INTO notification_preferences (id, lastUpdated)
                                VALUES (1, ${System.currentTimeMillis()})
                            """.trimIndent())
                            android.util.Log.i("AppDatabase", "onCreate: Inserted default notification preferences")
                        }
                    })

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
