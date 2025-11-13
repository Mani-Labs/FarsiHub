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
                    Log.e(TAG, "Error initializing content database: ${e.message}", e)
                    // Don't mark as initialized if failed - will retry next launch
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
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .setRequiresCharging(false)
            .setRequiresDeviceIdle(true) // No sync during playback
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
            // For < 60 minutes, use MINUTES (WorkManager minimum is 15 minutes)
            interval = syncIntervalMinutes
            timeUnit = TimeUnit.MINUTES
            flexInterval = (syncIntervalMinutes / 3).coerceAtLeast(5) // Min 5-minute flex
            flexUnit = TimeUnit.MINUTES
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
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .setRequiresCharging(false)
            .setRequiresDeviceIdle(true) // No sync during playback
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
            interval = syncIntervalMinutes
            timeUnit = TimeUnit.MINUTES
            flexInterval = (syncIntervalMinutes / 3).coerceAtLeast(5)
            flexUnit = TimeUnit.MINUTES
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
