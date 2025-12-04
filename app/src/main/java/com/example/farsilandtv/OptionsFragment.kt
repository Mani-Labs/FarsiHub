package com.example.farsilandtv

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.leanback.app.GuidedStepSupportFragment
import androidx.leanback.widget.GuidanceStylist
import androidx.leanback.widget.GuidedAction
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.farsilandtv.data.database.AppDatabase
import com.example.farsilandtv.data.database.ContentDatabase
import com.example.farsilandtv.data.sync.ContentSyncWorker
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

/**
 * Options/Settings fragment - merged with Sync Settings
 */
@AndroidEntryPoint
class OptionsFragment : GuidedStepSupportFragment() {

    companion object {
        private const val TAG = "OptionsFragment"
        private const val ACTION_DATABASE_SOURCE = 0L
        private const val ACTION_SYNC_NOW = 1L
        private const val ACTION_SYNC_ENABLED = 2L
        private const val ACTION_SYNC_FREQUENCY = 3L
        private const val ACTION_SYNC_STATUS = 4L
        private const val ACTION_FULL_RESYNC = 5L
        private const val ACTION_CLEAR_CACHE = 6L
        private const val ACTION_CLEAR_HISTORY = 7L
        private const val ACTION_ABOUT = 8L
    }

    private val prefs: android.content.SharedPreferences by lazy {
        requireContext().getSharedPreferences("sync_settings", Context.MODE_PRIVATE)
    }

    private val syncStatePrefs: android.content.SharedPreferences by lazy {
        requireContext().getSharedPreferences("sync_state", Context.MODE_PRIVATE)
    }

    private var isToggling = false

    override fun onCreateGuidance(savedInstanceState: Bundle?): GuidanceStylist.Guidance {
        return GuidanceStylist.Guidance(
            "Options",
            "App settings and sync preferences",
            "FarsiHub",
            null
        )
    }

    override fun onCreateActions(actions: MutableList<GuidedAction>, savedInstanceState: Bundle?) {
        // Get current states
        val currentSource = ContentDatabase.getCurrentSource(requireContext())
        val syncEnabled = prefs.getBoolean("sync_enabled", true)
        val syncIntervalMinutes = prefs.getLong("sync_interval_minutes", 30L)
        val lastSyncTimestamp = syncStatePrefs.getLong("last_sync_timestamp", 0L)

        // 1. Content Source
        actions.add(GuidedAction.Builder(requireContext())
            .id(ACTION_DATABASE_SOURCE)
            .title("Content Source")
            .description("Currently: ${currentSource.displayName}")
            .build())

        // 2. Sync Now
        actions.add(GuidedAction.Builder(requireContext())
            .id(ACTION_SYNC_NOW)
            .title("Sync Now")
            .description("Check websites for new movies & shows (adds to existing)")
            .build())

        // 3. Auto-Sync Toggle
        actions.add(GuidedAction.Builder(requireContext())
            .id(ACTION_SYNC_ENABLED)
            .title("Automatic Sync")
            .description(if (syncEnabled) "Enabled" else "Disabled")
            .checkSetId(GuidedAction.DEFAULT_CHECK_SET_ID)
            .checked(syncEnabled)
            .build())

        // 4. Sync Frequency (only show if enabled)
        if (syncEnabled) {
            actions.add(GuidedAction.Builder(requireContext())
                .id(ACTION_SYNC_FREQUENCY)
                .title("Sync Frequency")
                .description(getFrequencyText(syncIntervalMinutes))
                .hasNext(true)
                .build())
        }

        // 5. Sync Status
        val lastSyncText = if (lastSyncTimestamp > 0) {
            val dateFormat = SimpleDateFormat("MMM dd, hh:mm a", Locale.US)
            val timeAgo = getTimeAgo(lastSyncTimestamp)
            "Last: ${dateFormat.format(Date(lastSyncTimestamp))} ($timeAgo)"
        } else {
            "Never synced"
        }
        actions.add(GuidedAction.Builder(requireContext())
            .id(ACTION_SYNC_STATUS)
            .title("Sync Status")
            .description(lastSyncText)
            .enabled(false)
            .build())

        // 6. Force Full Re-Sync
        actions.add(GuidedAction.Builder(requireContext())
            .id(ACTION_FULL_RESYNC)
            .title("Force Full Re-Sync")
            .description("Reset content database to bundled version (use if corrupted)")
            .build())

        // 7. Clear Cache
        actions.add(GuidedAction.Builder(requireContext())
            .id(ACTION_CLEAR_CACHE)
            .title("Clear Cache")
            .description("Clear image and data cache")
            .build())

        // 8. Clear History
        actions.add(GuidedAction.Builder(requireContext())
            .id(ACTION_CLEAR_HISTORY)
            .title("Clear Watch History")
            .description("Remove all watch history and progress")
            .build())

        // 9. About
        actions.add(GuidedAction.Builder(requireContext())
            .id(ACTION_ABOUT)
            .title("About")
            .description("FarsiHub v1.0")
            .build())
    }

    override fun onGuidedActionClicked(action: GuidedAction) {
        when (action.id) {
            ACTION_DATABASE_SOURCE -> showDatabaseSourceSelector()
            ACTION_SYNC_NOW -> triggerManualSync()
            ACTION_SYNC_ENABLED -> toggleSyncEnabled(action)
            ACTION_SYNC_FREQUENCY -> showFrequencyPicker()
            ACTION_FULL_RESYNC -> showFullResyncConfirmation()
            ACTION_CLEAR_CACHE -> clearCache()
            ACTION_CLEAR_HISTORY -> showClearHistoryConfirmation()
            ACTION_ABOUT -> showAboutDialog()
        }
    }

    private fun showDatabaseSourceSelector() {
        GuidedStepSupportFragment.add(
            requireActivity().supportFragmentManager,
            DatabaseSourceSelectionFragment.newInstance()
        )
    }

    private fun triggerManualSync() {
        val action = findActionById(ACTION_SYNC_NOW)
        action.description = "⏳ Syncing..."
        notifyActionChanged(findActionPositionById(ACTION_SYNC_NOW))

        val farsilandSyncRequest = OneTimeWorkRequestBuilder<ContentSyncWorker>().build()
        val farsiPlexSyncRequest = OneTimeWorkRequestBuilder<com.example.farsilandtv.data.sync.FarsiPlexSyncWorker>().build()

        val workManager = WorkManager.getInstance(requireContext())
        workManager.enqueue(farsilandSyncRequest)
        workManager.enqueue(farsiPlexSyncRequest)

        Log.i(TAG, "Manual sync triggered for all sources")

        var farsilandCompleted = false
        var farsiPlexCompleted = false

        fun checkBothCompleted() {
            if (farsilandCompleted && farsiPlexCompleted) {
                action.description = "✓ Sync complete"
                notifyActionChanged(findActionPositionById(ACTION_SYNC_NOW))
                refreshSyncStatus()
            }
        }

        workManager.getWorkInfoByIdLiveData(farsilandSyncRequest.id)
            .observe(viewLifecycleOwner) { workInfo ->
                if (workInfo?.state == WorkInfo.State.SUCCEEDED || workInfo?.state == WorkInfo.State.FAILED) {
                    farsilandCompleted = true
                    checkBothCompleted()
                }
            }

        workManager.getWorkInfoByIdLiveData(farsiPlexSyncRequest.id)
            .observe(viewLifecycleOwner) { workInfo ->
                if (workInfo?.state == WorkInfo.State.SUCCEEDED || workInfo?.state == WorkInfo.State.FAILED) {
                    farsiPlexCompleted = true
                    checkBothCompleted()
                }
            }
    }

    private fun toggleSyncEnabled(action: GuidedAction) {
        if (isToggling) return
        isToggling = true

        val currentState = prefs.getBoolean("sync_enabled", true)
        val newValue = !currentState

        Log.i(TAG, "Toggling sync: $currentState -> $newValue")
        prefs.edit().putBoolean("sync_enabled", newValue).apply()

        if (newValue) {
            (requireActivity().application as FarsilandApp).onCreate()
        } else {
            WorkManager.getInstance(requireContext()).cancelUniqueWork(ContentSyncWorker.WORK_NAME)
        }

        view?.postDelayed({
            actions.clear()
            onCreateActions(actions, null)
            setActions(actions)
            isToggling = false
        }, 100)
    }

    private fun showFrequencyPicker() {
        GuidedStepSupportFragment.add(fragmentManager, FrequencyPickerFragment())
    }

    private fun showFullResyncConfirmation() {
        GuidedStepSupportFragment.add(fragmentManager, FullResyncConfirmationFragment())
    }

    private fun refreshSyncStatus() {
        val lastSyncTimestamp = syncStatePrefs.getLong("last_sync_timestamp", 0L)
        val statusAction = findActionById(ACTION_SYNC_STATUS)

        val lastSyncText = if (lastSyncTimestamp > 0) {
            val dateFormat = SimpleDateFormat("MMM dd, hh:mm a", Locale.US)
            val timeAgo = getTimeAgo(lastSyncTimestamp)
            "Last: ${dateFormat.format(Date(lastSyncTimestamp))} ($timeAgo)"
        } else {
            "Never synced"
        }

        statusAction.description = lastSyncText
        notifyActionChanged(findActionPositionById(ACTION_SYNC_STATUS))
    }

    private fun getFrequencyText(minutes: Long): String = when (minutes) {
        15L -> "Every 15 minutes"
        30L -> "Every 30 minutes"
        60L -> "Every hour"
        1440L -> "Daily"
        else -> "Every $minutes minutes"
    }

    private fun getTimeAgo(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - timestamp
        val minutes = diff / 60000
        val hours = minutes / 60
        val days = hours / 24

        return when {
            minutes < 1 -> "just now"
            minutes < 60 -> "${minutes}m ago"
            hours < 24 -> "${hours}h ago"
            else -> "${days}d ago"
        }
    }

    /**
     * Actually clear the image and data caches
     */
    private fun clearCache() {
        val action = findActionById(ACTION_CLEAR_CACHE)
        action.description = "Clearing..."
        notifyActionChanged(findActionPositionById(ACTION_CLEAR_CACHE))

        try {
            // Clear Coil image cache
            val imageLoader = coil.ImageLoader(requireContext())
            imageLoader.memoryCache?.clear()

            // Clear app cache directory
            requireContext().cacheDir.deleteRecursively()

            // Clear external cache if available
            requireContext().externalCacheDir?.deleteRecursively()

            // Clear HTTP cache (OkHttp)
            val httpCacheDir = java.io.File(requireContext().cacheDir, "http_cache")
            httpCacheDir.deleteRecursively()

            action.description = "✓ Cache cleared"
            notifyActionChanged(findActionPositionById(ACTION_CLEAR_CACHE))

            Toast.makeText(context, "All caches cleared successfully", Toast.LENGTH_SHORT).show()
            Log.i(TAG, "Cache cleared successfully")

        } catch (e: Exception) {
            Log.e(TAG, "Error clearing cache", e)
            action.description = "Clear image and data cache"
            notifyActionChanged(findActionPositionById(ACTION_CLEAR_CACHE))
            Toast.makeText(context, "Error clearing cache: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Show confirmation before clearing watch history
     */
    private fun showClearHistoryConfirmation() {
        GuidedStepSupportFragment.add(fragmentManager, ClearHistoryConfirmationFragment())
    }

    /**
     * Show about dialog with app info
     */
    private fun showAboutDialog() {
        GuidedStepSupportFragment.add(fragmentManager, AboutFragment())
    }
}

/**
 * Confirmation dialog for clearing watch history
 */
@AndroidEntryPoint
class ClearHistoryConfirmationFragment : GuidedStepSupportFragment() {

    companion object {
        private const val ACTION_CONFIRM = 1L
        private const val ACTION_CANCEL = 2L
    }

    override fun onCreateGuidance(savedInstanceState: Bundle?): GuidanceStylist.Guidance {
        return GuidanceStylist.Guidance(
            "Clear Watch History?",
            "This will remove all watch progress, continue watching items, and playback positions. This cannot be undone.",
            "FarsiHub",
            null
        )
    }

    override fun onCreateActions(actions: MutableList<GuidedAction>, savedInstanceState: Bundle?) {
        actions.add(GuidedAction.Builder(requireContext())
            .id(ACTION_CONFIRM)
            .title("Yes, Clear History")
            .description("Remove all watch data")
            .build())

        actions.add(GuidedAction.Builder(requireContext())
            .id(ACTION_CANCEL)
            .title("Cancel")
            .description("Keep my watch history")
            .build())
    }

    override fun onGuidedActionClicked(action: GuidedAction) {
        when (action.id) {
            ACTION_CONFIRM -> {
                clearWatchHistory()
                fragmentManager?.popBackStack()
            }
            ACTION_CANCEL -> fragmentManager?.popBackStack()
        }
    }

    private fun clearWatchHistory() {
        try {
            val context = requireContext()

            // Clear playback positions and watchlist from AppDatabase
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val appDb = AppDatabase.getDatabase(context)

                    // Clear playback positions
                    appDb.playbackPositionDao().clearAll()

                    // Clear continue watching progress using raw queries
                    // (These tables track watch progress for movies and episodes)
                    appDb.runInTransaction {
                        appDb.openHelper.writableDatabase.execSQL("DELETE FROM watchlist_movies")
                        appDb.openHelper.writableDatabase.execSQL("DELETE FROM episode_progress")
                    }

                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Watch history cleared", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Error clearing history: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}

/**
 * About screen with app information
 */
@AndroidEntryPoint
class AboutFragment : GuidedStepSupportFragment() {

    companion object {
        private const val ACTION_BACK = 1L
    }

    override fun onCreateGuidance(savedInstanceState: Bundle?): GuidanceStylist.Guidance {
        val versionName = try {
            requireContext().packageManager.getPackageInfo(requireContext().packageName, 0).versionName
        } catch (e: Exception) {
            "1.0"
        }

        return GuidanceStylist.Guidance(
            "FarsiHub",
            "Version $versionName\n\nYour personal Persian content streaming app.\n\nSources: Farsiland, FarsiPlex, Namakade",
            "About",
            null
        )
    }

    override fun onCreateActions(actions: MutableList<GuidedAction>, savedInstanceState: Bundle?) {
        actions.add(GuidedAction.Builder(requireContext())
            .id(ACTION_BACK)
            .title("Back")
            .build())
    }

    override fun onGuidedActionClicked(action: GuidedAction) {
        fragmentManager?.popBackStack()
    }
}

/**
 * Frequency picker for sync interval
 */
@AndroidEntryPoint
class FrequencyPickerFragment : GuidedStepSupportFragment() {

    companion object {
        private const val ACTION_15_MIN = 1L
        private const val ACTION_30_MIN = 2L
        private const val ACTION_1_HOUR = 3L
        private const val ACTION_DAILY = 4L
        private const val ACTION_CANCEL = 5L
    }

    override fun onCreateGuidance(savedInstanceState: Bundle?): GuidanceStylist.Guidance {
        return GuidanceStylist.Guidance(
            "Sync Frequency",
            "How often should the app check for new content?",
            "FarsiHub",
            null
        )
    }

    override fun onCreateActions(actions: MutableList<GuidedAction>, savedInstanceState: Bundle?) {
        actions.add(GuidedAction.Builder(requireContext())
            .id(ACTION_15_MIN)
            .title("Every 15 minutes")
            .build())
        actions.add(GuidedAction.Builder(requireContext())
            .id(ACTION_30_MIN)
            .title("Every 30 minutes")
            .build())
        actions.add(GuidedAction.Builder(requireContext())
            .id(ACTION_1_HOUR)
            .title("Every hour")
            .build())
        actions.add(GuidedAction.Builder(requireContext())
            .id(ACTION_DAILY)
            .title("Daily")
            .build())
        actions.add(GuidedAction.Builder(requireContext())
            .id(ACTION_CANCEL)
            .title("Cancel")
            .build())
    }

    override fun onGuidedActionClicked(action: GuidedAction) {
        val minutes = when (action.id) {
            ACTION_15_MIN -> 15L
            ACTION_30_MIN -> 30L
            ACTION_1_HOUR -> 60L
            ACTION_DAILY -> 1440L
            else -> null
        }

        if (minutes != null) {
            val prefs = requireContext().getSharedPreferences("sync_settings", android.content.Context.MODE_PRIVATE)
            prefs.edit().putLong("sync_interval_minutes", minutes).apply()
            Toast.makeText(context, "Sync frequency updated", Toast.LENGTH_SHORT).show()
        }
        fragmentManager?.popBackStack()
    }
}

/**
 * Confirmation dialog for full re-sync
 */
@AndroidEntryPoint
class FullResyncConfirmationFragment : GuidedStepSupportFragment() {

    companion object {
        private const val ACTION_CONFIRM = 1L
        private const val ACTION_CANCEL = 2L
    }

    override fun onCreateGuidance(savedInstanceState: Bundle?): GuidanceStylist.Guidance {
        return GuidanceStylist.Guidance(
            "Force Full Re-Sync?",
            "This will reset the content database to the bundled version and re-sync all content. Use this if the database appears corrupted.",
            "FarsiHub",
            null
        )
    }

    override fun onCreateActions(actions: MutableList<GuidedAction>, savedInstanceState: Bundle?) {
        actions.add(GuidedAction.Builder(requireContext())
            .id(ACTION_CONFIRM)
            .title("Yes, Reset & Re-Sync")
            .description("This may take a few minutes")
            .build())

        actions.add(GuidedAction.Builder(requireContext())
            .id(ACTION_CANCEL)
            .title("Cancel")
            .build())
    }

    override fun onGuidedActionClicked(action: GuidedAction) {
        when (action.id) {
            ACTION_CONFIRM -> {
                performFullResync()
                fragmentManager?.popBackStack()
            }
            ACTION_CANCEL -> fragmentManager?.popBackStack()
        }
    }

    private fun performFullResync() {
        try {
            val context = requireContext()

            // Mark database for re-initialization
            val prefs = context.getSharedPreferences("app_state", android.content.Context.MODE_PRIVATE)
            prefs.edit()
                .putBoolean("content_db_initialized", false)
                .putBoolean("force_db_copy", true)
                .apply()

            Toast.makeText(context, "Database will reset on next app start", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
