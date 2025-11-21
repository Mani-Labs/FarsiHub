package com.example.farsilandtv.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room database for pre-populated content catalog
 *
 * Separate from AppDatabase (watchlist/progress tracking)
 *
 * Data flow:
 * 1. First launch: Copy from assets/databases/content.db
 * 2. Background sync: Update with new content from WordPress API
 * 3. App queries: Fast local access, fallback to API if needed
 */
@Database(
    entities = [
        CachedMovie::class,
        CachedSeries::class,
        CachedEpisode::class,
        CachedGenre::class,
        CachedVideoUrl::class,
        // AUDIT FIX (FTS4): Register FTS entities to resolve "no such table" compilation error
        CachedMovieFts::class,
        CachedSeriesFts::class,
        CachedEpisodeFts::class
    ],
    version = 2, // AUDIT FIX C1.2: Add FTS4 for fast search
    exportSchema = true
)
abstract class ContentDatabase : RoomDatabase() {

    abstract fun movieDao(): CachedMovieDao
    abstract fun seriesDao(): CachedSeriesDao
    abstract fun episodeDao(): CachedEpisodeDao
    abstract fun genreDao(): CachedGenreDao
    abstract fun videoUrlDao(): CachedVideoUrlDao

    companion object {
        /**
         * Migration from version 1 to 2 (AUDIT FIX C1.2)
         * Adds FTS4 (Full Text Search) virtual tables for fast search
         *
         * Performance Impact:
         * - Before: LIKE '%query%' forces full table scan (500ms+ on 1000s of rows)
         * - After: FTS4 MATCH query uses optimized index (<50ms on same dataset)
         *
         * FTS4 automatically indexes text for:
         * - Prefix matching (query*)
         * - Full-word matching
         * - Phrase matching ("exact phrase")
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Create FTS4 virtual table for movies (indexes title column)
                database.execSQL("""
                    CREATE VIRTUAL TABLE IF NOT EXISTS cached_movies_fts
                    USING fts4(content='cached_movies', title)
                """.trimIndent())

                // Create FTS4 virtual table for series (indexes title column)
                database.execSQL("""
                    CREATE VIRTUAL TABLE IF NOT EXISTS cached_series_fts
                    USING fts4(content='cached_series', title)
                """.trimIndent())

                // Create FTS4 virtual table for episodes (indexes seriesTitle and title)
                database.execSQL("""
                    CREATE VIRTUAL TABLE IF NOT EXISTS cached_episodes_fts
                    USING fts4(content='cached_episodes', seriesTitle, title)
                """.trimIndent())

                // Populate FTS tables with existing data
                database.execSQL("""
                    INSERT INTO cached_movies_fts(docid, title)
                    SELECT id, title FROM cached_movies
                """.trimIndent())

                database.execSQL("""
                    INSERT INTO cached_series_fts(docid, title)
                    SELECT id, title FROM cached_series
                """.trimIndent())

                database.execSQL("""
                    INSERT INTO cached_episodes_fts(docid, seriesTitle, title)
                    SELECT id, seriesTitle, title FROM cached_episodes
                """.trimIndent())

                // Create triggers to keep FTS tables in sync with main tables
                // Movies: INSERT
                database.execSQL("""
                    CREATE TRIGGER IF NOT EXISTS cached_movies_fts_insert
                    AFTER INSERT ON cached_movies BEGIN
                        INSERT INTO cached_movies_fts(docid, title)
                        VALUES (new.id, new.title);
                    END
                """.trimIndent())

                // Movies: UPDATE
                database.execSQL("""
                    CREATE TRIGGER IF NOT EXISTS cached_movies_fts_update
                    AFTER UPDATE ON cached_movies BEGIN
                        UPDATE cached_movies_fts
                        SET title = new.title
                        WHERE docid = new.id;
                    END
                """.trimIndent())

                // Movies: DELETE
                database.execSQL("""
                    CREATE TRIGGER IF NOT EXISTS cached_movies_fts_delete
                    AFTER DELETE ON cached_movies BEGIN
                        DELETE FROM cached_movies_fts WHERE docid = old.id;
                    END
                """.trimIndent())

                // Series: INSERT
                database.execSQL("""
                    CREATE TRIGGER IF NOT EXISTS cached_series_fts_insert
                    AFTER INSERT ON cached_series BEGIN
                        INSERT INTO cached_series_fts(docid, title)
                        VALUES (new.id, new.title);
                    END
                """.trimIndent())

                // Series: UPDATE
                database.execSQL("""
                    CREATE TRIGGER IF NOT EXISTS cached_series_fts_update
                    AFTER UPDATE ON cached_series BEGIN
                        UPDATE cached_series_fts
                        SET title = new.title
                        WHERE docid = new.id;
                    END
                """.trimIndent())

                // Series: DELETE
                database.execSQL("""
                    CREATE TRIGGER IF NOT EXISTS cached_series_fts_delete
                    AFTER DELETE ON cached_series BEGIN
                        DELETE FROM cached_series_fts WHERE docid = old.id;
                    END
                """.trimIndent())

                // Episodes: INSERT
                database.execSQL("""
                    CREATE TRIGGER IF NOT EXISTS cached_episodes_fts_insert
                    AFTER INSERT ON cached_episodes BEGIN
                        INSERT INTO cached_episodes_fts(docid, seriesTitle, title)
                        VALUES (new.id, new.seriesTitle, new.title);
                    END
                """.trimIndent())

                // Episodes: UPDATE
                database.execSQL("""
                    CREATE TRIGGER IF NOT EXISTS cached_episodes_fts_update
                    AFTER UPDATE ON cached_episodes BEGIN
                        UPDATE cached_episodes_fts
                        SET seriesTitle = new.seriesTitle, title = new.title
                        WHERE docid = new.id;
                    END
                """.trimIndent())

                // Episodes: DELETE
                database.execSQL("""
                    CREATE TRIGGER IF NOT EXISTS cached_episodes_fts_delete
                    AFTER DELETE ON cached_episodes BEGIN
                        DELETE FROM cached_episodes_fts WHERE docid = old.id;
                    END
                """.trimIndent())
            }
        }

        @Volatile
        private var INSTANCE: ContentDatabase? = null

        @Volatile
        private var currentDatabaseName: String? = null

        /**
         * Get database instance
         * Database copied from assets on first access if not exists
         * Automatically switches to user's preferred database source
         */
        fun getDatabase(context: Context): ContentDatabase {
            val dbPrefs = DatabasePreferences.getInstance(context)
            val source = dbPrefs.getCurrentSource()
            val databaseName = source.fileName

            android.util.Log.d("ContentDatabase", "getDatabase() called - Requested: ${source.displayName} ($databaseName)")
            android.util.Log.d("ContentDatabase", "Current instance: $currentDatabaseName")

            // AUDIT FIX #13: Prevent ANR - check main thread BEFORE synchronized block
            // If database doesn't exist and we're on main thread, fail fast instead of blocking
            val dbFile = context.applicationContext.getDatabasePath(databaseName)
            if (!dbFile.exists() && android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
                throw IllegalStateException(
                    "Database not initialized. Cannot copy database from assets on Main Thread (would cause ANR). " +
                    "Database must be pre-initialized in Application.onCreate() on background thread. " +
                    "Current source: ${source.displayName} ($databaseName)"
                )
            }

            // Use single synchronized block to prevent double-lock deadlock (Bug #1 fix)
            return synchronized(this) {
                // Check if database source changed (inside synchronized block for thread safety)
                if (currentDatabaseName != null && currentDatabaseName != databaseName) {
                    // AUDIT FIX #2: Safe database switching - close with error handling
                    android.util.Log.i("ContentDatabase", "Database source changed: $currentDatabaseName → $databaseName")

                    // Close old instance safely (may fail if observers still active)
                    try {
                        INSTANCE?.close()
                        android.util.Log.i("ContentDatabase", "Old database instance closed successfully")
                    } catch (e: Exception) {
                        android.util.Log.w("ContentDatabase",
                            "Failed to close old database (observers may still be active): ${e.message}. " +
                            "Instance will be replaced anyway.")
                    }

                    INSTANCE = null
                    currentDatabaseName = null
                }

                // Return existing or create new instance
                INSTANCE ?: run {
                    // Bug #8 fix: Validate database file exists before trying to load
                    try {
                        val assetPath = "databases/$databaseName"
                        val dbExists = try {
                            context.applicationContext.assets.open(assetPath).use { true }
                        } catch (e: Exception) {
                            false
                        }

                        if (!dbExists) {
                            throw IllegalStateException(
                                "Database file not found: $assetPath. " +
                                "Ensure the file exists in src/main/assets/databases/"
                            )
                        }

                        android.util.Log.i("ContentDatabase", "Creating new database instance: ${source.displayName} ($databaseName)")

                        // BUG FIX: Manually copy database from assets with writable permissions
                        // Issue: createFromAsset() copies as read-only, causing permission errors
                        // Solution: Copy manually BEFORE Room opens it, set writable immediately

                        // Get database file path (main thread check already done before synchronized block)
                        val dbFile = context.applicationContext.getDatabasePath(databaseName)

                        // If database doesn't exist, copy from assets with writable permissions
                        // AUDIT FIX #1/#13: Database copy only happens here, and main thread is blocked
                        // before entering synchronized block (see line 60-66)
                        if (!dbFile.exists()) {
                            android.util.Log.i("ContentDatabase", "Database doesn't exist, copying from assets...")
                            copyDatabaseFromAssets(context.applicationContext, assetPath, dbFile)
                        }

                        val instance = Room.databaseBuilder(
                            context.applicationContext,
                            ContentDatabase::class.java,
                            databaseName
                        )
                            // AUDIT FIX C1.2: Add FTS4 migration
                            .addMigrations(MIGRATION_1_2)
                            // Don't use createFromAsset - we copied manually above
                            .fallbackToDestructiveMigration()  // For development
                            .addCallback(object : androidx.room.RoomDatabase.Callback() {
                                override fun onOpen(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                                    super.onOpen(db)
                                    // AUDIT FIX #6: Reduced permission checks - only verify on open, not every operation
                                    // WAL/SHM files are created by SQLite after first transaction
                                    // Permissions are set once during initial copy, this is just verification
                                    android.util.Log.d("ContentDatabase", "Database opened: $databaseName")

                                    // Only fix permissions if actually read-only (rare case)
                                    try {
                                        val walFile = context.applicationContext.getDatabasePath("$databaseName-wal")
                                        val shmFile = context.applicationContext.getDatabasePath("$databaseName-shm")

                                        if (dbFile.exists() && !dbFile.canWrite()) {
                                            android.util.Log.w("ContentDatabase", "Main DB read-only after open, fixing")
                                            dbFile.setWritable(true, false)
                                        }
                                        if (walFile.exists() && !walFile.canWrite()) {
                                            android.util.Log.w("ContentDatabase", "WAL file read-only, fixing")
                                            walFile.setWritable(true, false)
                                        }
                                        if (shmFile.exists() && !shmFile.canWrite()) {
                                            android.util.Log.w("ContentDatabase", "SHM file read-only, fixing")
                                            shmFile.setWritable(true, false)
                                        }
                                    } catch (e: Exception) {
                                        android.util.Log.e("ContentDatabase", "Permission check failed: ${e.message}")
                                    }
                                }
                            })
                            .build()

                        INSTANCE = instance
                        currentDatabaseName = databaseName

                        android.util.Log.i("ContentDatabase", "Database instance created successfully: $databaseName")
                        instance
                    } catch (e: Exception) {
                        android.util.Log.e("ContentDatabase", "Failed to initialize database: ${e.message}", e)
                        throw e
                    }
                }
            }
        }

        /**
         * Switch to a different database source
         * Returns true if database was switched
         *
         * AUDIT FIX #2/#14: Safe database switching with error handling + race condition fix
         * Note: Caller should clear ContentRepository caches after switching
         * Example: contentRepository.clearCache()
         *
         * AUTO-SYNC: Automatically syncs the new database source after switching
         */
        fun switchDatabaseSource(context: Context, source: DatabaseSource): Boolean {
            val dbPrefs = DatabasePreferences.getInstance(context)
            val currentSource = dbPrefs.getCurrentSource()

            if (currentSource != source) {
                android.util.Log.i("ContentDatabase", "Switching database from ${currentSource.displayName} to ${source.displayName}")

                // AUDIT FIX #14: Atomic operation - close + update preferences in same synchronized block
                // Prevents race condition where getDatabase() sees null but reads old preference
                synchronized(this) {
                    try {
                        INSTANCE?.close()
                        android.util.Log.i("ContentDatabase", "Previous database instance closed")
                    } catch (e: Exception) {
                        android.util.Log.w("ContentDatabase",
                            "Error closing previous database (may have active observers): ${e.message}. " +
                            "Proceeding with switch anyway.")
                    }
                    INSTANCE = null
                    currentDatabaseName = null

                    // Update preferences INSIDE synchronized block for atomicity
                    dbPrefs.setDatabaseSource(source)
                }

                // AUTO-SYNC: Trigger sync OUTSIDE synchronized block (don't hold lock during network I/O)
                android.util.Log.i("ContentDatabase", "Database switched to ${source.displayName} - triggering auto-sync")
                triggerSyncForSource(context, source)

                return true
            }
            return false
        }

        /**
         * Trigger sync for a specific database source
         */
        private fun triggerSyncForSource(context: Context, source: DatabaseSource) {
            val syncRequest = when (source) {
                DatabaseSource.FARSILAND -> {
                    androidx.work.OneTimeWorkRequestBuilder<com.example.farsilandtv.data.sync.ContentSyncWorker>().build()
                }
                DatabaseSource.FARSIPLEX -> {
                    androidx.work.OneTimeWorkRequestBuilder<com.example.farsilandtv.data.sync.FarsiPlexSyncWorker>().build()
                }
                DatabaseSource.NAMAKADE -> {
                    // Namakade doesn't have a sync worker (no API available)
                    android.util.Log.i("ContentDatabase", "Namakade is static - no sync available")
                    return
                }
            }

            androidx.work.WorkManager.getInstance(context).enqueue(syncRequest)
            android.util.Log.i("ContentDatabase", "Auto-sync triggered for ${source.displayName}")
        }

        /**
         * Get current database source
         */
        fun getCurrentSource(context: Context): DatabaseSource {
            return DatabasePreferences.getInstance(context).getCurrentSource()
        }

        /**
         * Clear and recreate database from assets
         * Used for "Force Full Re-Sync" option
         */
        fun recreateDatabaseFromAssets(context: Context) {
            synchronized(this) {
                val currentDbName = currentDatabaseName ?: return

                INSTANCE?.close()
                INSTANCE = null

                // Delete existing database
                context.applicationContext.deleteDatabase(currentDbName)

                // Reset current name so next getDatabase() will copy from assets
                currentDatabaseName = null
            }
        }

        /**
         * Close database instance
         */
        fun closeDatabase() {
            synchronized(this) {
                INSTANCE?.close()
                INSTANCE = null
                currentDatabaseName = null
            }
        }

        /**
         * AUDIT FIX #1: Extract database copy logic to separate function
         * Copies database from assets with proper error handling and permissions
         * Should ideally be called from background thread to avoid ANR
         */
        private fun copyDatabaseFromAssets(
            context: android.content.Context,
            assetPath: String,
            dbFile: java.io.File
        ) {
            try {
                dbFile.parentFile?.mkdirs()

                // Use buffered streams for better performance
                context.assets.open(assetPath).buffered().use { input ->
                    dbFile.outputStream().buffered().use { output ->
                        input.copyTo(output, bufferSize = 8192)
                    }
                }

                // Set writable IMMEDIATELY after copy
                dbFile.setWritable(true, false)
                android.util.Log.i("ContentDatabase", "Database copied successfully with write permissions")
            } catch (e: Exception) {
                android.util.Log.e("ContentDatabase", "Error copying database: ${e.message}")
                // Clean up partial file if copy failed
                if (dbFile.exists()) {
                    dbFile.delete()
                }
                throw e
            }
        }
    }
}
