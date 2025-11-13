package com.example.farsilandtv

import android.graphics.drawable.Drawable
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.leanback.app.GuidedStepSupportFragment
import androidx.leanback.widget.GuidanceStylist
import androidx.leanback.widget.GuidedAction
import androidx.lifecycle.lifecycleScope
import com.example.farsilandtv.data.database.NotificationPreferences
import com.example.farsilandtv.data.repository.NotificationPreferencesRepository
import com.example.farsilandtv.utils.NotificationHelper
import kotlinx.coroutines.launch

/**
 * Fragment for managing notification preferences using Leanback GuidedStepSupportFragment
 * Provides TV-friendly UI for notification settings
 * Feature #9 - Push Notifications
 */
class NotificationSettingsFragment : GuidedStepSupportFragment() {

    companion object {
        private const val ACTION_NEW_EPISODES = 1L
        private const val ACTION_NEW_SEASONS = 2L
        private const val ACTION_WEEKLY_DIGEST = 3L
        private const val ACTION_QUIET_HOURS_START = 4L
        private const val ACTION_QUIET_HOURS_END = 5L
        private const val ACTION_OPEN_SYSTEM_SETTINGS = 6L
        private const val ACTION_RESET_DEFAULTS = 7L
    }

    private lateinit var notificationPrefsRepository: NotificationPreferencesRepository
    private lateinit var notificationHelper: NotificationHelper
    private var currentPreferences: NotificationPreferences? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        notificationPrefsRepository = NotificationPreferencesRepository(requireContext())
        notificationHelper = NotificationHelper(requireContext())

        // Load current preferences
        lifecycleScope.launch {
            currentPreferences = notificationPrefsRepository.getPreferences()
            // Refresh UI after loading preferences
            activity?.recreate()
        }
    }

    override fun onCreateGuidance(savedInstanceState: Bundle?): GuidanceStylist.Guidance {
        val title = "Notification Settings"
        val description = "Manage when and how you receive notifications about new content"
        val breadcrumb = "Settings"
        val icon: Drawable? = ContextCompat.getDrawable(requireContext(), R.drawable.ic_notification)

        return GuidanceStylist.Guidance(title, description, breadcrumb, icon)
    }

    override fun onCreateActions(actions: MutableList<GuidedAction>, savedInstanceState: Bundle?) {
        val prefs = currentPreferences ?: return

        // New Episodes toggle
        actions.add(
            GuidedAction.Builder(requireContext())
                .id(ACTION_NEW_EPISODES)
                .title("New Episodes")
                .description("Get notified when new episodes are available in your monitored series")
                .checkSetId(GuidedAction.CHECKBOX_CHECK_SET_ID)
                .checked(prefs.newEpisodesEnabled)
                .build()
        )

        // New Seasons toggle
        actions.add(
            GuidedAction.Builder(requireContext())
                .id(ACTION_NEW_SEASONS)
                .title("New Seasons")
                .description("Get notified when new seasons are available")
                .checkSetId(GuidedAction.CHECKBOX_CHECK_SET_ID)
                .checked(prefs.newSeasonsEnabled)
                .build()
        )

        // Weekly Digest toggle
        actions.add(
            GuidedAction.Builder(requireContext())
                .id(ACTION_WEEKLY_DIGEST)
                .title("Weekly Digest")
                .description("Receive a weekly summary of new content")
                .checkSetId(GuidedAction.CHECKBOX_CHECK_SET_ID)
                .checked(prefs.weeklyDigestEnabled)
                .build()
        )

        // Quiet Hours Start
        actions.add(
            GuidedAction.Builder(requireContext())
                .id(ACTION_QUIET_HOURS_START)
                .title("Quiet Hours Start")
                .description("No notifications after this time (currently ${formatHour(prefs.quietHoursStart)})")
                .editable(false)
                .build()
        )

        // Quiet Hours End
        actions.add(
            GuidedAction.Builder(requireContext())
                .id(ACTION_QUIET_HOURS_END)
                .title("Quiet Hours End")
                .description("Resume notifications at this time (currently ${formatHour(prefs.quietHoursEnd)})")
                .editable(false)
                .build()
        )

        // Open System Settings
        actions.add(
            GuidedAction.Builder(requireContext())
                .id(ACTION_OPEN_SYSTEM_SETTINGS)
                .title("System Notification Settings")
                .description("Open Android system settings for FarsilandTV notifications")
                .build()
        )

        // Reset to Defaults
        actions.add(
            GuidedAction.Builder(requireContext())
                .id(ACTION_RESET_DEFAULTS)
                .title("Reset to Defaults")
                .description("Restore default notification settings")
                .build()
        )
    }

    override fun onGuidedActionClicked(action: GuidedAction) {
        val prefs = currentPreferences ?: return

        lifecycleScope.launch {
            when (action.id) {
                ACTION_NEW_EPISODES -> {
                    val newValue = !action.isChecked
                    notificationPrefsRepository.toggleNewEpisodes(newValue)
                    action.isChecked = newValue
                    notifyActionChanged(findActionPositionById(ACTION_NEW_EPISODES))
                }
                ACTION_NEW_SEASONS -> {
                    val newValue = !action.isChecked
                    notificationPrefsRepository.toggleNewSeasons(newValue)
                    action.isChecked = newValue
                    notifyActionChanged(findActionPositionById(ACTION_NEW_SEASONS))
                }
                ACTION_WEEKLY_DIGEST -> {
                    val newValue = !action.isChecked
                    notificationPrefsRepository.toggleWeeklyDigest(newValue)
                    action.isChecked = newValue
                    notifyActionChanged(findActionPositionById(ACTION_WEEKLY_DIGEST))
                }
                ACTION_QUIET_HOURS_START -> {
                    // TODO: Show time picker dialog
                    // For now, cycle through common hours (18, 20, 22, 0)
                    val nextHour = when (prefs.quietHoursStart) {
                        18 -> 20
                        20 -> 22
                        22 -> 0
                        else -> 22
                    }
                    notificationPrefsRepository.updateQuietHours(nextHour, prefs.quietHoursEnd)
                    action.description = "No notifications after this time (currently ${formatHour(nextHour)})"
                    notifyActionChanged(findActionPositionById(ACTION_QUIET_HOURS_START))
                    currentPreferences = notificationPrefsRepository.getPreferences()
                }
                ACTION_QUIET_HOURS_END -> {
                    // TODO: Show time picker dialog
                    // For now, cycle through common hours (6, 7, 8, 9)
                    val nextHour = when (prefs.quietHoursEnd) {
                        6 -> 7
                        7 -> 8
                        8 -> 9
                        9 -> 6
                        else -> 8
                    }
                    notificationPrefsRepository.updateQuietHours(prefs.quietHoursStart, nextHour)
                    action.description = "Resume notifications at this time (currently ${formatHour(nextHour)})"
                    notifyActionChanged(findActionPositionById(ACTION_QUIET_HOURS_END))
                    currentPreferences = notificationPrefsRepository.getPreferences()
                }
                ACTION_OPEN_SYSTEM_SETTINGS -> {
                    notificationHelper.openNotificationSettings()
                }
                ACTION_RESET_DEFAULTS -> {
                    notificationPrefsRepository.resetToDefaults()
                    currentPreferences = notificationPrefsRepository.getPreferences()
                    // Refresh all actions
                    activity?.recreate()
                }
            }
        }
    }

    /**
     * Format hour in 12-hour format with AM/PM
     */
    private fun formatHour(hour: Int): String {
        return when {
            hour == 0 -> "12:00 AM"
            hour < 12 -> "$hour:00 AM"
            hour == 12 -> "12:00 PM"
            else -> "${hour - 12}:00 PM"
        }
    }
}
