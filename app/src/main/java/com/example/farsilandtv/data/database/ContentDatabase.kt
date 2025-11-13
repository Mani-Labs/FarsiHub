package com.example.farsilandtv.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

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
        CachedVideoUrl::class
    ],
    version = 1,
    exportSchema = true
)
abstract class ContentDatabase : RoomDatabase() {

    abstract fun movieDao(): CachedMovieDao
    abstract fun seriesDao(): CachedSeriesDao
    abstract fun episodeDao(): CachedEpisodeDao
    abstract fun genreDao(): CachedGenreDao
    abstract fun videoUrlDao(): CachedVideoUrlDao

    companion object {
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

            // Use single synchronized block to prevent double-lock deadlock (Bug #1 fix)
            return synchronized(this) {
                // Check if database source changed (inside synchronized block for thread safety)
                if (currentDatabaseName != null && currentDatabaseName != databaseName) {
                    // User switched database source - close old instance
                    android.util.Log.i("ContentDatabase", "Database source changed: $currentDatabaseName → $databaseName")
                    INSTANCE?.close()
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

                        val dbFile = context.applicationContext.getDatabasePath(databaseName)

                        // If database doesn't exist, copy from assets with writable permissions
                        if (!dbFile.exists()) {
                            android.util.Log.i("ContentDatabase", "Database doesn't exist, copying from assets...")
                            try {
                                dbFile.parentFile?.mkdirs()
                                context.applicationContext.assets.open(assetPath).use { input ->
                                    dbFile.outputStream().use { output ->
                                        input.copyTo(output)
                                    }
                                }
                                // Set writable IMMEDIATELY after copy
                                dbFile.setWritable(true, false)
                                android.util.Log.i("ContentDatabase", "Database copied successfully with write permissions")
                            } catch (e: Exception) {
                                android.util.Log.e("ContentDatabase", "Error copying database: ${e.message}")
                                throw e
                            }
                        }

                        val instance = Room.databaseBuilder(
                            context.applicationContext,
                            ContentDatabase::class.java,
                            databaseName
                        )
                            // Don't use createFromAsset - we copied manually above
                            .fallbackToDestructiveMigration()  // For development
                            .addCallback(object : androidx.room.RoomDatabase.Callback() {
                                override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                                    super.onCreate(db)
                                    // CRITICAL FIX: Set write permissions in onCreate callback
                                    // This runs AFTER Room copies from assets but BEFORE opening WAL mode
                                    android.util.Log.d("ContentDatabase", "Room onCreate callback - fixing file permissions...")

                                    try {
                                        // Give database time to flush writes
                                        Thread.sleep(100)

                                        // Fix main database file
                                        if (dbFile.exists() && !dbFile.canWrite()) {
                                            android.util.Log.w("ContentDatabase", "Main DB read-only, fixing: ${dbFile.absolutePath}")
                                            dbFile.setWritable(true, false)
                                        }

                                        // Fix WAL file (Write-Ahead Log)
                                        val walFile = context.applicationContext.getDatabasePath("$databaseName-wal")
                                        if (walFile.exists() && !walFile.canWrite()) {
                                            android.util.Log.w("ContentDatabase", "WAL read-only, fixing")
                                            walFile.setWritable(true, false)
                                        }

                                        // Fix SHM file (Shared Memory)
                                        val shmFile = context.applicationContext.getDatabasePath("$databaseName-shm")
                                        if (shmFile.exists() && !shmFile.canWrite()) {
                                            android.util.Log.w("ContentDatabase", "SHM read-only, fixing")
                                            shmFile.setWritable(true, false)
                                        }

                                        android.util.Log.i("ContentDatabase", "Database permissions fixed in onCreate")
                                    } catch (e: Exception) {
                                        android.util.Log.e("ContentDatabase", "Error fixing permissions: ${e.message}")
                                    }
                                }

                                override fun onOpen(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                                    super.onOpen(db)
                                    // Also fix on every open (in case files were recreated)
                                    android.util.Log.d("ContentDatabase", "Room onOpen - double-checking permissions...")
                                    try {
                                        if (dbFile.exists()) dbFile.setWritable(true, false)
                                        val walFile = context.applicationContext.getDatabasePath("$databaseName-wal")
                                        if (walFile.exists()) walFile.setWritable(true, false)
                                        val shmFile = context.applicationContext.getDatabasePath("$databaseName-shm")
                                        if (shmFile.exists()) shmFile.setWritable(true, false)
                                    } catch (e: Exception) {
                                        android.util.Log.e("ContentDatabase", "onOpen permission fix failed: ${e.message}")
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
         * Bug #10 fix: Close database BEFORE updating preferences to prevent corruption
         * AUTO-SYNC: Automatically syncs the new database source after switching
         */
        fun switchDatabaseSource(context: Context, source: DatabaseSource): Boolean {
            val dbPrefs = DatabasePreferences.getInstance(context)
            val currentSource = dbPrefs.getCurrentSource()

            if (currentSource != source) {
                // Close database FIRST (Bug #10 fix: prevents file lock/corruption if crash occurs)
                synchronized(this) {
                    INSTANCE?.close()
                    INSTANCE = null
                    currentDatabaseName = null
                }

                // THEN update preferences
                dbPrefs.setDatabaseSource(source)

                // AUTO-SYNC: Trigger sync for the newly selected database
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
    }
}
