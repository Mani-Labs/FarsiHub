package com.example.farsilandtv

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.example.farsilandtv.data.repository.NotificationPreferencesRepository
import com.example.farsilandtv.utils.NotificationHelper
import kotlinx.coroutines.launch

/**
 * Settings activity for FarsilandTV
 * Currently focuses on notification preferences
 * Feature #9 - Push Notifications
 *
 * NOTE: This uses Leanback GuidedStepSupportFragment for TV-friendly settings UI
 */
class SettingsActivity : FragmentActivity() {

    private lateinit var notificationPrefsRepository: NotificationPreferencesRepository
    private lateinit var notificationHelper: NotificationHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        notificationPrefsRepository = NotificationPreferencesRepository(this)
        notificationHelper = NotificationHelper(this)

        if (savedInstanceState == null) {
            // Show notification settings fragment
            val fragment = NotificationSettingsFragment()
            supportFragmentManager.beginTransaction()
                .replace(android.R.id.content, fragment)
                .commit()
        }
    }
}
