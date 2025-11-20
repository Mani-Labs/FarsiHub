package com.example.farsilandtv

import android.app.Application
import android.os.Build
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.farsilandtv.data.database.ContentDatabase
import com.example.farsilandtv.data.sync.ContentSyncWorker
// import com.google.firebase.crashlytics.FirebaseCrashlytics  // DISABLED
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Application class for Farsiland TV
 * Provides global context for caching and initialization
 * NEW: Handles first-launch database initialization and background sync
 */
class FarsilandApp : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        instance = this

        // Initialize Firebase Crashlytics (M4)
        // initializeCrashlytics()  // DISABLED: Firebase not configured

        // Initialize content database on first launch
        initializeContentDatabase()

        // Schedule periodic background sync for Farsiland (10 minutes)
        scheduleFarsilandSync()

        // Schedule periodic background sync for FarsiPlex (15 minutes)
        scheduleFarsiPlexSync()
    }

    /**
     * Initialize Firebase Crashlytics for production error tracking (M4)
     * Enables crash reporting and custom logging for better debugging
     */
    private fun initializeCrashlytics() {
        // Firebase disabled - requires valid google-services.json
        Log.i(TAG, "Firebase Crashlytics disabled (placeholder config)")
    }

    /**
     * Initialize content database from bundled assets
     * Room automatically copies database from assets on first access
     * This triggers the copy in background on app launch
     */
    private fun initializeContentDatabase() {
        val prefs = getSharedPreferences("app_state", MODE_PRIVATE)
        val isFirstLaunch = !prefs.getBoolean("content_db_initialized", false)

        if (isFirstLaunch) {
            Log.i(TAG, "First launch detected - initializing content database from assets")

            applicationScope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        // Trigger database creation from assets
                        // Room's createFromAsset() automatically copies the database file
                        val db = ContentDatabase.getDatabase(applicationContext)

                        // Verify database is accessible by querying count
                        val currentSource = ContentDatabase.getCurrentSource(applicationContext)
                        val movieCount = db.movieDao().getMovieCount()
                        val seriesCount = db.seriesDao().getSeriesCount()
                        val episodeCount = db.episodeDao().getEpisodeCount()

                        Log.i(TAG, "Content database initialized successfully!")
                        Log.i(TAG, "Source: ${currentSource.displayName} (${currentSource.fileName})")
                        Log.i(TAG, "Loaded: $movieCount movies, $seriesCount series, $episodeCount episodes")

                        // Mark as initialized
                        withContext(Dispatchers.Main) {
                            prefs.edit().putBoolean("content_db_initialized", true).apply()
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "CRITICAL: Content database initialization failed: ${e.message}", e)

                    // AUDIT FIX #15: "Zombie State" Prevention + Database Recovery Safety
                    // Issue: Deleting DB files while connection is open causes crashes
                    // Fix: Close database connection BEFORE deletion, use Android's deleteDatabase() API
                    try {
                        Log.w(TAG, "Attempting database recovery: closing database and deleting corrupted files")
                        withContext(Dispatchers.IO) {
                            // EXTERNAL AUDIT FIX #1: Close database connection before deleting files
                            // Prevents file locks, crashes, and data corruption from "zombie" state
                            try {
                                ContentDatabase.closeDatabase()
                                Log.i(TAG, "Database instance closed successfully")
                            } catch (e: Exception) {
                                Log.w(TAG, "Error closing database (may already be closed): ${e.message}")
                            }

                            // Use Android's deleteDatabase() API - handles .db, .db-wal, .db-shm automatically
                            val deleted = applicationContext.deleteDatabase("content.db")
                            Log.i(TAG, "Database cleanup: deleted=$deleted")

                            // Mark DB as requiring emergency sync (not permanently failed)
                            withContext(Dispatchers.Main) {
                                prefs.edit()
                                    .putBoolean("content_db_initialized", false)
                                    .putBoolean("content_db_error", true)
                                    .putBoolean("content_db_emergency_sync", true) // NEW: Trigger full sync
                                    .putString("content_db_error_message", e.message ?: "Unknown error")
                                    .apply()

                                Log.w(TAG, "Database deleted. Emergency full sync will be triggered on next launch.")
                            }

                            // Trigger IMMEDIATE emergency sync to rebuild database from network
                            val currentSource = com.example.farsilandtv.data.database.DatabasePreferences.getInstance(applicationContext).getCurrentSource()
                            when (currentSource) {
                                com.example.farsilandtv.data.database.DatabaseSource.FARSILAND -> {
                                    val syncRequest = androidx.work.OneTimeWorkRequestBuilder<com.example.farsilandtv.data.sync.ContentSyncWorker>()
                                        .setExpedited(androidx.work.OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                                        .build()
                                    androidx.work.WorkManager.getInstance(applicationContext).enqueue(syncRequest)
                                    Log.i(TAG, "Emergency Farsiland sync triggered")
                                }
                                com.example.farsilandtv.data.database.DatabaseSource.FARSIPLEX -> {
                                    val syncRequest = androidx.work.OneTimeWorkRequestBuilder<com.example.farsilandtv.data.sync.FarsiPlexSyncWorker>()
                                        .setExpedited(androidx.work.OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                                        .build()
                                    androidx.work.WorkManager.getInstance(applicationContext).enqueue(syncRequest)
                                    Log.i(TAG, "Emergency FarsiPlex sync triggered")
                                }
                                com.example.farsilandtv.data.database.DatabaseSource.NAMAKADE -> {
                                    Log.e(TAG, "FATAL: Namakade has no API sync. User must reinstall app to restore database.")
                                    withContext(Dispatchers.Main) {
                                        prefs.edit().putBoolean("content_db_fatal_error", true).apply()
                                    }
                                }
                            }
                        }
                    } catch (recoveryError: Exception) {
                        Log.e(TAG, "FATAL: Database recovery failed completely", recoveryError)
                        withContext(Dispatchers.Main) {
                            prefs.edit().putBoolean("content_db_fatal_error", true).apply()
                        }
                        Log.e(TAG, "App cannot recover. User must clear app data: Settings → Apps → FarsiPlex → Clear Data")
                    }
                }
            }
        } else {
            Log.i(TAG, "Content database already initialized")
        }
    }

    /**
     * Schedule Farsiland background sync with WorkManager
     *
     * Frequency is configurable via Settings > Sync Settings
     * Options: 15min, 30min, 1hr, 6hr, Daily, Manual
     */
    private fun scheduleFarsilandSync() {
        val prefs = getSharedPreferences("sync_settings", MODE_PRIVATE)
        val syncEnabled = prefs.getBoolean("sync_enabled", true) // Enabled by default
        val syncIntervalMinutes = prefs.getLong("sync_interval_minutes", 30L) // Default: 30 minutes

        // Manual only (0) or disabled
        if (!syncEnabled || syncIntervalMinutes == 0L) {
            Log.i(TAG, "Background sync is disabled (sync_enabled=$syncEnabled, interval=$syncIntervalMinutes)")
            WorkManager.getInstance(this).cancelUniqueWork(ContentSyncWorker.WORK_NAME)
            return
        }

        // Optimized constraints for Shield TV
        // DeviceIdle removed - Android TV rarely enters idle mode (especially with screensavers)
        // EXTERNAL AUDIT FIX #2: Battery constraint removed - Android TV is always plugged in
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresCharging(false)
            .build()

        // Convert minutes to hours if >= 60 minutes for better WorkManager compatibility
        val interval: Long
        val timeUnit: TimeUnit
        val flexInterval: Long
        val flexUnit: TimeUnit

        if (syncIntervalMinutes >= 60) {
            val hours = syncIntervalMinutes / 60
            interval = hours
            timeUnit = TimeUnit.HOURS
            flexInterval = (hours / 3).coerceAtLeast(1) // Flex window = 1/3 of interval, min 1 hour
            flexUnit = TimeUnit.HOURS
        } else {
            // P3 FIX: Issue #14 - Enforce WorkManager's 15-minute minimum for periodic work
            // WorkManager silently clamps values below 15 minutes, so we validate and warn
            interval = syncIntervalMinutes.coerceAtLeast(15)
            timeUnit = TimeUnit.MINUTES
            flexInterval = (interval / 3).coerceAtLeast(5) // Use validated interval for flex calculation
            flexUnit = TimeUnit.MINUTES

            if (syncIntervalMinutes < 15) {
                Log.w(TAG, "Sync interval ${syncIntervalMinutes}min is below WorkManager minimum, using 15 min instead")
            }
        }

        val syncWorkRequest = PeriodicWorkRequestBuilder<ContentSyncWorker>(
            repeatInterval = interval,
            repeatIntervalTimeUnit = timeUnit,
            flexTimeInterval = flexInterval,
            flexTimeIntervalUnit = flexUnit
        )
            .setConstraints(constraints)
            .addTag("content_sync")
            .addTag("user_configured")
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            ContentSyncWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.REPLACE,
            syncWorkRequest
        )

        Log.i(TAG, "Farsiland sync scheduled: Every ${syncIntervalMinutes}min (idle mode, any network)")
    }

    /**
     * Schedule FarsiPlex background sync with WorkManager
     *
     * Shares same frequency setting with Farsiland sync
     * Configurable via Settings > Sync Settings
     */
    private fun scheduleFarsiPlexSync() {
        val prefs = getSharedPreferences("sync_settings", MODE_PRIVATE)
        val syncEnabled = prefs.getBoolean("farsiplex_sync_enabled", true) // Enabled by default
        val syncIntervalMinutes = prefs.getLong("sync_interval_minutes", 30L) // Same as Farsiland

        // Manual only (0) or disabled
        if (!syncEnabled || syncIntervalMinutes == 0L) {
            Log.i(TAG, "FarsiPlex sync is disabled (sync_enabled=$syncEnabled, interval=$syncIntervalMinutes)")
            WorkManager.getInstance(this).cancelUniqueWork(com.example.farsilandtv.data.sync.FarsiPlexSyncWorker.WORK_NAME)
            return
        }

        // Optimized constraints for Shield TV
        // DeviceIdle removed - Android TV rarely enters idle mode (especially with screensavers)
        // EXTERNAL AUDIT FIX #2: Battery constraint removed - Android TV is always plugged in
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresCharging(false)
            .build()

        // Convert minutes to hours if >= 60 minutes for better WorkManager compatibility
        val interval: Long
        val timeUnit: TimeUnit
        val flexInterval: Long
        val flexUnit: TimeUnit

        if (syncIntervalMinutes >= 60) {
            val hours = syncIntervalMinutes / 60
            interval = hours
            timeUnit = TimeUnit.HOURS
            flexInterval = (hours / 3).coerceAtLeast(1)
            flexUnit = TimeUnit.HOURS
        } else {
            // P3 FIX: Issue #14 - Enforce WorkManager's 15-minute minimum for periodic work
            // WorkManager silently clamps values below 15 minutes, so we validate and warn
            interval = syncIntervalMinutes.coerceAtLeast(15)
            timeUnit = TimeUnit.MINUTES
            flexInterval = (interval / 3).coerceAtLeast(5) // Use validated interval for flex calculation
            flexUnit = TimeUnit.MINUTES

            if (syncIntervalMinutes < 15) {
                Log.w(TAG, "FarsiPlex sync interval ${syncIntervalMinutes}min is below WorkManager minimum, using 15 min instead")
            }
        }

        val syncWorkRequest = PeriodicWorkRequestBuilder<com.example.farsilandtv.data.sync.FarsiPlexSyncWorker>(
            repeatInterval = interval,
            repeatIntervalTimeUnit = timeUnit,
            flexTimeInterval = flexInterval,
            flexTimeIntervalUnit = flexUnit
        )
            .setConstraints(constraints)
            .addTag("farsiplex_sync")
            .addTag("user_configured")
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            com.example.farsilandtv.data.sync.FarsiPlexSyncWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.REPLACE,
            syncWorkRequest
        )

        Log.i(TAG, "FarsiPlex sync scheduled: Every ${syncIntervalMinutes}min (idle mode, any network, scrapes full metadata + video URLs)")
    }

    companion object {
        private const val TAG = "FarsilandApp"

        lateinit var instance: FarsilandApp
            private set

        /**
         * Log non-fatal exceptions to Firebase Crashlytics (M4)
         * Use this for caught exceptions that should be tracked but don't crash the app
         *
         * @param message Context about where/why the error occurred
         * @param throwable The exception to report
         */
        fun logError(message: String, throwable: Throwable) {
            Log.e(TAG, message, throwable)
            // Firebase Crashlytics disabled - using local logging only
            // try {
            //     FirebaseCrashlytics.getInstance().apply {
            //         log(message)
            //         recordException(throwable)
            //     }
            // } catch (e: Exception) {
            //     Log.e(TAG, "Failed to log error to Crashlytics: ${e.message}")
            // }
        }

        /**
         * Log informational breadcrumb (Firebase disabled, using local logging)
         *
         * @param message Breadcrumb message (e.g., "User started video playback")
         */
        fun logBreadcrumb(message: String) {
            Log.d(TAG, "Breadcrumb: $message")
            // Firebase Crashlytics disabled
            // try {
            //     FirebaseCrashlytics.getInstance().log(message)
            // } catch (e: Exception) {
            //     Log.e(TAG, "Failed to log breadcrumb to Crashlytics: ${e.message}")
            // }
        }
    }
}
