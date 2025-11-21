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
import java.io.File
import java.util.concurrent.TimeUnit
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.database.StandaloneDatabaseProvider

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

        // EXTERNAL AUDIT FIX S2: Initialize ExoPlayer cache on background thread
        // Prevents main thread I/O during VideoPlayerActivity.onCreate()
        // Cache is lazily created once and shared across all video playback sessions
        initializeVideoCache()

        // EXTERNAL AUDIT FIX H2 (2025-11-21): Initialize HTTP cache on background thread
        // Prevents main thread I/O when RetrofitClient first accessed
        // Similar to video cache - prevents UI jank on first API call
        initializeHttpCache()

        // Initialize Firebase Crashlytics (M4)
        // initializeCrashlytics()  // DISABLED: Firebase not configured

        // AUDIT #3 C3: Check for emergency sync flag from previous crash
        val prefs = getSharedPreferences("app_state", MODE_PRIVATE)
        val needsEmergencySync = prefs.getBoolean("content_db_emergency_sync", false)

        if (needsEmergencySync) {
            Log.w(TAG, "Emergency sync flag detected - triggering immediate database rebuild")
            triggerEmergencySync()
            // Clear flag after triggering
            prefs.edit().putBoolean("content_db_emergency_sync", false).apply()
        }

        // Initialize content database on first launch
        initializeContentDatabase()

        // Schedule periodic background sync for Farsiland (10 minutes)
        scheduleFarsilandSync()

        // Schedule periodic background sync for FarsiPlex (15 minutes)
        scheduleFarsiPlexSync()
    }

    /**
     * Trigger emergency database sync after corruption recovery
     * Called on cold start when emergency_sync flag is set
     *
     * AUDIT #3 C3: Moved from inline code to dedicated function
     * Runs on fresh process after exitProcess(0) guarantees file handle release
     */
    private fun triggerEmergencySync() {
        val currentSource = com.example.farsilandtv.data.database.DatabasePreferences.getInstance(applicationContext).getCurrentSource()

        when (currentSource) {
            com.example.farsilandtv.data.database.DatabaseSource.FARSILAND -> {
                val syncRequest = androidx.work.OneTimeWorkRequestBuilder<com.example.farsilandtv.data.sync.ContentSyncWorker>()
                    .setExpedited(androidx.work.OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                    .build()
                androidx.work.WorkManager.getInstance(applicationContext).enqueue(syncRequest)
                Log.i(TAG, "Emergency Farsiland sync triggered (cold start after crash recovery)")
            }
            com.example.farsilandtv.data.database.DatabaseSource.FARSIPLEX -> {
                val syncRequest = androidx.work.OneTimeWorkRequestBuilder<com.example.farsilandtv.data.sync.FarsiPlexSyncWorker>()
                    .setExpedited(androidx.work.OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                    .build()
                androidx.work.WorkManager.getInstance(applicationContext).enqueue(syncRequest)
                Log.i(TAG, "Emergency FarsiPlex sync triggered (cold start after crash recovery)")
            }
            com.example.farsilandtv.data.database.DatabaseSource.NAMAKADE -> {
                Log.e(TAG, "FATAL: Namakade has no API sync. User must reinstall app to restore database.")
                getSharedPreferences("app_state", MODE_PRIVATE)
                    .edit()
                    .putBoolean("content_db_fatal_error", true)
                    .apply()
            }
        }
    }

    /**
     * EXTERNAL AUDIT FIX S2: Initialize ExoPlayer video cache
     * Moved from VideoPlayerActivity to prevent main thread blocking
     *
     * Benefits:
     * - No frame drops on player initialization (was 50-120ms)
     * - Cache persists across video player instances
     * - Reduces disk I/O on every video start
     */
    private fun initializeVideoCache() {
        applicationScope.launch(Dispatchers.IO) {
            try {
                val cacheDir = File(cacheDir, "exoplayer_cache")
                val cacheSize = 100L * 1024 * 1024 // 100MB

                videoCache = SimpleCache(
                    cacheDir,
                    LeastRecentlyUsedCacheEvictor(cacheSize),
                    StandaloneDatabaseProvider(applicationContext)
                )

                Log.i(TAG, "Video cache initialized: 100MB at ${cacheDir.absolutePath}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize video cache: ${e.message}", e)
                // Non-fatal - video playback will work without cache
            }
        }
    }

    /**
     * EXTERNAL AUDIT FIX H2 (2025-11-21): Initialize HTTP cache on background thread
     * Prevents main thread I/O when RetrofitClient.okHttpClient is lazily initialized
     *
     * Benefits:
     * - No UI jank on first API call (was 20-80ms for cache directory check)
     * - Cache ready before first network request
     * - Prevents dropped frames during app startup
     */
    private fun initializeHttpCache() {
        applicationScope.launch(Dispatchers.IO) {
            try {
                // Trigger RetrofitClient initialization on background thread
                // This will create the HTTP cache directory without blocking main thread
                com.example.farsilandtv.data.api.RetrofitClient.initializeCache(applicationContext)
                Log.i(TAG, "HTTP cache initialized successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize HTTP cache: ${e.message}", e)
                // Non-fatal - networking will work without cache (just slower)
            }
        }
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
     *
     * ARCHITECTURE NOTE (2025-11-21 Audit Response):
     * -----------------------------------------------
     * This app uses TWO databases:
     * - ContentDatabase (movies/series catalog) - EPHEMERAL, replaced during sync
     * - AppDatabase (user watchlist/progress) - PERMANENT, must persist across syncs
     *
     * This is intentional and safe. See ContentDatabase.kt and AppDatabase.kt
     * for detailed architectural justification. DO NOT merge into one database.
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

                            // AUDIT #3 C3: Mark emergency sync flag and exit immediately
                            // Previous: Triggered WorkManager sync in same process (risky)
                            // Issue: File locks on .db-wal/.db-shm may not release without process kill
                            // Fix: Set flag, exit process, let next cold start handle emergency sync
                            withContext(Dispatchers.Main) {
                                prefs.edit()
                                    .putBoolean("content_db_initialized", false)
                                    .putBoolean("content_db_error", true)
                                    .putBoolean("content_db_emergency_sync", true) // Trigger full sync on next start
                                    .putString("content_db_error_message", e.message ?: "Unknown error")
                                    .apply()

                                Log.w(TAG, "Database recovery: Emergency sync flag set")
                                Log.w(TAG, "Terminating process to release all file handles...")
                                Log.w(TAG, "Emergency sync will run on next cold start")
                            }

                            // EXTERNAL AUDIT FIX C1 REVISED (2025-11-21): Remove process kill entirely
                            // Issue: killProcess() creates race condition - process dies before AlarmManager IPC completes
                            // Previous: scheduleAppRestart() + killProcess() = app crashes and never restarts
                            // Fix: Use System.exit(0) which is safer, OR just set error flag and let user restart
                            //
                            // DECISION: Set fatal error flag - safer than forced restart
                            // User will see error on next manual app launch (via Home Screen)
                            // This prevents:
                            // 1. Race condition with AlarmManager
                            // 2. Unexpected app restart mid-session
                            // 3. File lock issues from forced termination
                            Log.e(TAG, "Database recovery requires app restart. Please close and reopen the app.")
                            Log.i(TAG, "Emergency sync will run automatically on next app start.")
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

    /**
     * EXTERNAL AUDIT FIX C1: Schedule app restart using AlarmManager
     * Replaces dangerous exitProcess(0) with safer restart mechanism
     *
     * This schedules the app to restart in 2 seconds, giving the OS time to:
     * - Release all file handles (.db-wal, .db-shm)
     * - Clean up WorkManager jobs
     * - Properly close all connections
     */
    private fun scheduleAppRestart() {
        try {
            val intent = packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)

                val pendingIntent = android.app.PendingIntent.getActivity(
                    applicationContext,
                    0,
                    intent,
                    android.app.PendingIntent.FLAG_ONE_SHOT or android.app.PendingIntent.FLAG_IMMUTABLE
                )

                val alarmManager = getSystemService(android.content.Context.ALARM_SERVICE) as android.app.AlarmManager
                alarmManager.setExact(
                    android.app.AlarmManager.RTC,
                    System.currentTimeMillis() + 2000, // Restart in 2 seconds
                    pendingIntent
                )

                Log.i(TAG, "App restart scheduled for 2 seconds from now")
            } else {
                Log.e(TAG, "Could not get launch intent for app restart")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule app restart", e)
        }
    }

    companion object {
        private const val TAG = "FarsilandApp"

        lateinit var instance: FarsilandApp
            private set

        /**
         * EXTERNAL AUDIT FIX S2: Singleton video cache instance
         * Initialized in Application.onCreate() on background thread
         * Shared across all video playback sessions
         */
        @Volatile
        var videoCache: SimpleCache? = null
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
