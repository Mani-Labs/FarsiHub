package com.example.farsilandtv

import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.leanback.app.GuidedStepSupportFragment
import androidx.leanback.widget.GuidanceStylist
import androidx.leanback.widget.GuidedAction
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.farsilandtv.data.database.ContentDatabase
import com.example.farsilandtv.data.database.DatabaseSource
import com.example.farsilandtv.data.sync.ContentSyncWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

/**
 * Sync Settings Screen for Android TV
 *
 * Allows users to:
 * - Enable/disable automatic sync
 * - Choose sync frequency (15min/30min/1hr/Daily/Manual)
 * - Force manual sync now
 * - View last sync status
 * - Force full database re-sync
 */
class SyncSettingsFragment : GuidedStepSupportFragment() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // Use lazy initialization to avoid lateinit crashes (onCreateActions called before onCreate)
    private val prefs: android.content.SharedPreferences by lazy {
        requireContext().getSharedPreferences("sync_settings", Context.MODE_PRIVATE)
    }

    private val syncStatePrefs: android.content.SharedPreferences by lazy {
        requireContext().getSharedPreferences("sync_state", Context.MODE_PRIVATE)
    }

    // Prevent rapid toggle clicks (debounce)
    private var isToggling = false

    // Action IDs
    private val ACTION_SYNC_ENABLED = 1L
    private val ACTION_SYNC_FREQUENCY = 2L
    private val ACTION_SYNC_NOW = 3L
    private val ACTION_SYNC_STATUS = 4L
    private val ACTION_FULL_RESYNC = 5L
    private val ACTION_BACK = 6L

    override fun onCreateGuidance(savedInstanceState: Bundle?): GuidanceStylist.Guidance {
        return GuidanceStylist.Guidance(
            "Sync Settings",
            "Configure automatic content synchronization",
            "",
            null
        )
    }

    override fun onCreateActions(actions: MutableList<GuidedAction>, savedInstanceState: Bundle?) {
        val syncEnabled = prefs.getBoolean("sync_enabled", true)
        val syncIntervalMinutes = prefs.getLong("sync_interval_minutes", 30L) // Default: 30 minutes
        val lastSyncTimestamp = syncStatePrefs.getLong("last_sync_timestamp", 0L)

        // 1. Enable/Disable Auto-Sync
        actions.add(
            GuidedAction.Builder(requireContext())
                .id(ACTION_SYNC_ENABLED)
                .title("Automatic Sync")
                .description(if (syncEnabled) "Enabled" else "Disabled")
                .checkSetId(GuidedAction.DEFAULT_CHECK_SET_ID)
                .checked(syncEnabled)
                .build()
        )

        // 2. Sync Frequency (only show if enabled)
        if (syncEnabled) {
            val frequencyText = getFrequencyText(syncIntervalMinutes)
            actions.add(
                GuidedAction.Builder(requireContext())
                    .id(ACTION_SYNC_FREQUENCY)
                    .title("Sync Frequency")
                    .description(frequencyText)
                    .hasNext(true)
                    .build()
            )
        }

        // 3. Sync Now Button
        actions.add(
            GuidedAction.Builder(requireContext())
                .id(ACTION_SYNC_NOW)
                .title("Sync Now")
                .description("Manually check for new content")
                .build()
        )

        // 4. Last Sync Status
        val lastSyncText = if (lastSyncTimestamp > 0) {
            val dateFormat = SimpleDateFormat("MMM dd, hh:mm a", Locale.US)
            val timeAgo = getTimeAgo(lastSyncTimestamp)
            "Last synced: ${dateFormat.format(Date(lastSyncTimestamp))} ($timeAgo)"
        } else {
            "Never synced"
        }

        actions.add(
            GuidedAction.Builder(requireContext())
                .id(ACTION_SYNC_STATUS)
                .title("Sync Status")
                .description(lastSyncText)
                .enabled(false)
                .build()
        )

        // 5. Full Database Re-Sync
        actions.add(
            GuidedAction.Builder(requireContext())
                .id(ACTION_FULL_RESYNC)
                .title("Force Full Re-Sync")
                .description("Re-copy entire database from bundled assets (experimental)")
                .build()
        )

        // 6. Back Button
        actions.add(
            GuidedAction.Builder(requireContext())
                .id(ACTION_BACK)
                .title("Back")
                .description("")
                .build()
        )
    }

    override fun onGuidedActionClicked(action: GuidedAction) {
        when (action.id) {
            ACTION_SYNC_ENABLED -> {
                toggleSyncEnabled(action)
            }
            ACTION_SYNC_FREQUENCY -> {
                showFrequencyPicker()
            }
            ACTION_SYNC_NOW -> {
                triggerManualSync()
            }
            ACTION_FULL_RESYNC -> {
                showFullResyncConfirmation()
            }
            ACTION_BACK -> {
                // For GuidedStepSupportFragment, pop this fragment from the stack
                requireActivity().supportFragmentManager.popBackStack()
            }
        }
    }

    /**
     * Toggle automatic sync on/off
     * Bug Fix #1: Properly refresh UI when toggling sync state
     * Bug Fix #1.1: Added debounce to prevent multiple rapid toggles
     */
    private fun toggleSyncEnabled(action: GuidedAction) {
        // Debounce: Prevent multiple rapid clicks
        if (isToggling) {
            Log.w(TAG, "Toggle already in progress, ignoring")
            return
        }
        isToggling = true

        val currentState = prefs.getBoolean("sync_enabled", true)
        val newValue = !currentState  // Toggle based on actual preference, not UI state

        Log.i(TAG, "Toggling sync: $currentState -> $newValue")
        prefs.edit().putBoolean("sync_enabled", newValue).apply()

        // Reschedule or cancel WorkManager
        if (newValue) {
            // Re-initialize sync (will schedule WorkManager)
            (requireActivity().application as FarsilandApp).onCreate()
            Log.i(TAG, "Auto-sync enabled")
        } else {
            WorkManager.getInstance(requireContext()).cancelUniqueWork(ContentSyncWorker.WORK_NAME)
            Log.i(TAG, "Auto-sync disabled")
        }

        // Refresh entire action list to show/hide frequency picker
        // Post with delay to ensure preference is saved and to allow UI to settle
        view?.postDelayed({
            actions.clear()
            onCreateActions(actions, null)
            setActions(actions)
            isToggling = false  // Reset flag after UI update
        }, 100) // 100ms delay to debounce
    }

    /**
     * Show frequency picker dialog
     * Bug Fix #2: Use proper GuidedStepFragment pattern instead of broken sub-actions
     */
    private fun showFrequencyPicker() {
        val frequencyPickerFragment = FrequencyPickerFragment()
        GuidedStepSupportFragment.add(fragmentManager, frequencyPickerFragment)
    }

    /**
     * Trigger immediate one-time sync for ALL database sources
     * Syncs Farsiland AND FarsiPlex (Namakade is static)
     */
    private fun triggerManualSync() {
        val action = findActionById(ACTION_SYNC_NOW)
        action.description = "⏳ Syncing all sources..."
        notifyActionChanged(findActionPositionById(ACTION_SYNC_NOW))

        // Create sync requests for ALL sources
        val farsilandSyncRequest = OneTimeWorkRequestBuilder<ContentSyncWorker>().build()
        val farsiPlexSyncRequest = OneTimeWorkRequestBuilder<com.example.farsilandtv.data.sync.FarsiPlexSyncWorker>().build()

        // Enqueue both syncs
        val workManager = WorkManager.getInstance(requireContext())
        workManager.enqueue(farsilandSyncRequest)
        workManager.enqueue(farsiPlexSyncRequest)

        Log.i(TAG, "Manual sync triggered for ALL sources (Farsiland + FarsiPlex)")

        // Track completion of both syncs
        var farsilandCompleted = false
        var farsiPlexCompleted = false

        fun checkBothCompleted() {
            if (farsilandCompleted && farsiPlexCompleted) {
                action.description = "✓ All sources synced successfully"
                notifyActionChanged(findActionPositionById(ACTION_SYNC_NOW))
                refreshSyncStatus()
            }
        }

        // Observe Farsiland sync
        workManager.getWorkInfoByIdLiveData(farsilandSyncRequest.id)
            .observe(viewLifecycleOwner) { workInfo ->
                if (workInfo != null) {
                    when (workInfo.state) {
                        WorkInfo.State.SUCCEEDED -> {
                            farsilandCompleted = true
                            Log.i(TAG, "Farsiland sync completed")
                            checkBothCompleted()
                        }
                        WorkInfo.State.FAILED -> {
                            action.description = "✗ Farsiland sync failed"
                            notifyActionChanged(findActionPositionById(ACTION_SYNC_NOW))
                        }
                        WorkInfo.State.RUNNING -> {
                            action.description = "⏳ Syncing Farsiland..."
                            notifyActionChanged(findActionPositionById(ACTION_SYNC_NOW))
                        }
                        else -> {}
                    }
                }
            }

        // Observe FarsiPlex sync
        workManager.getWorkInfoByIdLiveData(farsiPlexSyncRequest.id)
            .observe(viewLifecycleOwner) { workInfo ->
                if (workInfo != null) {
                    when (workInfo.state) {
                        WorkInfo.State.SUCCEEDED -> {
                            farsiPlexCompleted = true
                            Log.i(TAG, "FarsiPlex sync completed")
                            checkBothCompleted()
                        }
                        WorkInfo.State.FAILED -> {
                            action.description = "✗ FarsiPlex sync failed"
                            notifyActionChanged(findActionPositionById(ACTION_SYNC_NOW))
                        }
                        WorkInfo.State.RUNNING -> {
                            action.description = "⏳ Syncing FarsiPlex..."
                            notifyActionChanged(findActionPositionById(ACTION_SYNC_NOW))
                        }
                        else -> {}
                    }
                }
            }
    }

    /**
     * Show confirmation for full database re-sync
     */
    private fun showFullResyncConfirmation() {
        val confirmFragment = FullResyncConfirmationFragment()
        GuidedStepSupportFragment.add(fragmentManager, confirmFragment)
    }

    /**
     * Refresh sync status display
     */
    private fun refreshSyncStatus() {
        val lastSyncTimestamp = syncStatePrefs.getLong("last_sync_timestamp", 0L)
        val action = findActionById(ACTION_SYNC_STATUS)

        val lastSyncText = if (lastSyncTimestamp > 0) {
            val dateFormat = SimpleDateFormat("MMM dd, hh:mm a", Locale.US)
            val timeAgo = getTimeAgo(lastSyncTimestamp)
            "Last synced: ${dateFormat.format(Date(lastSyncTimestamp))} ($timeAgo)"
        } else {
            "Never synced"
        }

        action.description = lastSyncText
        notifyActionChanged(findActionPositionById(ACTION_SYNC_STATUS))
    }

    /**
     * Get human-readable frequency text
     */
    private fun getFrequencyText(minutes: Long): String {
        return when (minutes) {
            15L -> "Every 15 minutes"
            30L -> "Every 30 minutes"
            60L -> "Every hour"
            360L -> "Every 6 hours"
            1440L -> "Daily"
            0L -> "Manual only"
            else -> "Every $minutes minutes"
        }
    }

    /**
     * Get human-readable time ago text
     */
    private fun getTimeAgo(timestamp: Long): String {
        val diff = System.currentTimeMillis() - timestamp
        val minutes = diff / (1000 * 60)
        val hours = diff / (1000 * 60 * 60)
        val days = diff / (1000 * 60 * 60 * 24)

        return when {
            minutes < 1 -> "just now"
            minutes < 60 -> "${minutes}m ago"
            hours < 24 -> "${hours}h ago"
            else -> "${days}d ago"
        }
    }

    companion object {
        private const val TAG = "SyncSettingsFragment"
    }
}

/**
 * Frequency Picker Fragment
 * Shows sync frequency options in a separate guided step screen
 */
class FrequencyPickerFragment : GuidedStepSupportFragment() {

    private val prefs: android.content.SharedPreferences by lazy {
        requireContext().getSharedPreferences("sync_settings", Context.MODE_PRIVATE)
    }

    override fun onCreateGuidance(savedInstanceState: Bundle?): GuidanceStylist.Guidance {
        return GuidanceStylist.Guidance(
            "Select Sync Frequency",
            "Choose how often to automatically check for new content",
            "",
            null
        )
    }

    override fun onCreateActions(actions: MutableList<GuidedAction>, savedInstanceState: Bundle?) {
        val currentFreq = prefs.getLong("sync_interval_minutes", 30L)

        // Add frequency options
        actions.add(createFrequencyAction(100L, "Every 15 minutes", 15L, currentFreq))
        actions.add(createFrequencyAction(101L, "Every 30 minutes", 30L, currentFreq))
        actions.add(createFrequencyAction(102L, "Every hour", 60L, currentFreq))
        actions.add(createFrequencyAction(103L, "Every 6 hours", 360L, currentFreq))
        actions.add(createFrequencyAction(104L, "Daily", 1440L, currentFreq))
        actions.add(createFrequencyAction(105L, "Manual only", 0L, currentFreq))
    }

    private fun createFrequencyAction(
        id: Long,
        title: String,
        minutes: Long,
        currentFreq: Long
    ): GuidedAction {
        return GuidedAction.Builder(requireContext())
            .id(id)
            .title(title)
            .description(if (minutes == currentFreq) "✓ Current" else "")
            .checkSetId(GuidedAction.DEFAULT_CHECK_SET_ID)
            .checked(minutes == currentFreq)
            .build()
    }

    override fun onGuidedActionClicked(action: GuidedAction) {
        val minutes = when (action.id) {
            100L -> 15L
            101L -> 30L
            102L -> 60L
            103L -> 360L
            104L -> 1440L
            105L -> 0L
            else -> {
                popBackStackToGuidedStepSupportFragment(SyncSettingsFragment::class.java, 0)
                return
            }
        }

        // Save preference
        prefs.edit().putLong("sync_interval_minutes", minutes).apply()

        // Reschedule WorkManager with new interval
        (requireActivity().application as FarsilandApp).onCreate()

        Log.i("FrequencyPicker", "Sync frequency changed to: $minutes minutes")

        // Go back to settings screen
        popBackStackToGuidedStepSupportFragment(SyncSettingsFragment::class.java, 0)
    }
}

/**
 * Confirmation dialog for full database re-sync
 */
class FullResyncConfirmationFragment : GuidedStepSupportFragment() {

    override fun onCreateGuidance(savedInstanceState: Bundle?): GuidanceStylist.Guidance {
        return GuidanceStylist.Guidance(
            "Force Full Re-Sync",
            "This will delete the current database and re-copy from bundled assets. All sync progress will be reset. Continue?",
            "",
            null
        )
    }

    override fun onCreateActions(actions: MutableList<GuidedAction>, savedInstanceState: Bundle?) {
        actions.add(
            GuidedAction.Builder(requireContext())
                .id(1)
                .title("Yes, Re-Sync")
                .build()
        )

        actions.add(
            GuidedAction.Builder(requireContext())
                .id(2)
                .title("Cancel")
                .build()
        )
    }

    override fun onGuidedActionClicked(action: GuidedAction) {
        when (action.id) {
            1L -> {
                performFullResync()
                popBackStackToGuidedStepSupportFragment(SyncSettingsFragment::class.java, 0)
            }
            2L -> {
                popBackStackToGuidedStepSupportFragment(SyncSettingsFragment::class.java, 0)
            }
        }
    }

    private fun performFullResync() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    // Close and delete database
                    ContentDatabase.recreateDatabaseFromAssets(requireContext())

                    // Reset sync state
                    val syncStatePrefs = requireContext().getSharedPreferences("sync_state", Context.MODE_PRIVATE)
                    syncStatePrefs.edit().clear().apply()

                    val appStatePrefs = requireContext().getSharedPreferences("app_state", Context.MODE_PRIVATE)
                    appStatePrefs.edit().putBoolean("content_db_initialized", false).apply()

                    Log.i("FullResync", "Database reset complete")
                }

                // Re-initialize database
                (requireActivity().application as FarsilandApp).onCreate()

            } catch (e: Exception) {
                Log.e("FullResync", "Error during full re-sync: ${e.message}", e)
            }
        }
    }
}
