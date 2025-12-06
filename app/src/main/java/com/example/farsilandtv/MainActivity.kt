package com.example.farsilandtv

import android.content.pm.ActivityInfo
import android.os.Bundle
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.example.farsilandtv.utils.DeviceUtils
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * MainActivity with sidebar navigation:
 * - Home
 * - Movies
 * - Shows
 * - Search
 * - Options
 *
 * Back navigation:
 * - Non-home screens: navigate back to previous screen
 * - Home screen: double-back-to-exit (press back twice within 2 seconds)
 */
@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    companion object {
        // FIXED: Extract magic numbers to named constants
        private const val DOUBLE_BACK_EXIT_TIMEOUT_MS = 2000L
        private const val DB_INIT_MAX_ATTEMPTS = 120
        private const val DB_INIT_CHECK_INTERVAL_MS = 1000L
    }

    // Double-back-to-exit tracking
    private var backPressedTime: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        // Switch from splash theme to main theme before super.onCreate
        setTheme(R.style.Theme_FarsilandTV)

        super.onCreate(savedInstanceState)

        // Phase 7: Set orientation based on device type
        // - Phone: Portrait (standard mobile experience)
        // - TV/Tablet: Landscape (designed for 10-foot UI)
        val deviceType = DeviceUtils.getDeviceType(this)
        requestedOrientation = when (deviceType) {
            DeviceUtils.DeviceType.PHONE -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            DeviceUtils.DeviceType.TV,
            DeviceUtils.DeviceType.TABLET -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
        setContentView(R.layout.activity_main)

        // Setup back press handler with double-back-to-exit on home screen
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isOnHomeScreen()) {
                    // Home screen: implement double-back-to-exit
                    if (System.currentTimeMillis() - backPressedTime < DOUBLE_BACK_EXIT_TIMEOUT_MS) {
                        // Double back pressed within 2 seconds, exit app
                        finish()
                    } else {
                        // First back press
                        backPressedTime = System.currentTimeMillis()
                        Toast.makeText(this@MainActivity, R.string.exit_prompt, Toast.LENGTH_SHORT).show()
                    }
                } else {
                    // Not on home, just navigate back
                    supportFragmentManager.popBackStack()
                }
            }
        })

        // Check if database is initialized (race condition fix)
        val prefs = getSharedPreferences("app_state", MODE_PRIVATE)
        val isDbInitialized = prefs.getBoolean("content_db_initialized", false)

        if (!isDbInitialized) {
            // Show loading screen while DB initializes
            showDatabaseLoadingScreen()
        } else if (savedInstanceState == null) {
            // DB ready, start with Home fragment (Compose)
            getSupportFragmentManager().beginTransaction()
                .replace(R.id.main_browse_fragment, HomeComposeFragment())
                .commitNow()
        }
    }

    /**
     * Show loading screen while database copies from assets (first launch only)
     * Polls for content_db_initialized flag and starts HomeFragment when ready
     */
    private fun showDatabaseLoadingScreen() {
        // Poll for initialization complete
        lifecycleScope.launch {
            val prefs = getSharedPreferences("app_state", MODE_PRIVATE)
            var attempts = 0

            while (!prefs.getBoolean("content_db_initialized", false) &&
                   !prefs.getBoolean("content_db_error", false) &&
                   attempts < DB_INIT_MAX_ATTEMPTS) {
                delay(DB_INIT_CHECK_INTERVAL_MS)
                attempts++
            }

            // Check final status
            if (prefs.getBoolean("content_db_initialized", false)) {
                // Success - load HomeComposeFragment (Compose)
                if (!supportFragmentManager.isStateSaved) {
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.main_browse_fragment, HomeComposeFragment())
                        .commitNow()
                }
            } else if (prefs.getBoolean("content_db_error", false)) {
                // Fatal error
                val errorMsg = prefs.getString("content_db_error_message", "Unknown error")
                Toast.makeText(
                    this@MainActivity,
                    getString(R.string.error_database_init, errorMsg),
                    Toast.LENGTH_LONG
                ).show()
            } else {
                // Timeout
                Toast.makeText(
                    this@MainActivity,
                    R.string.error_database_timeout,
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    /**
     * Navigate to a specific section
     */
    fun navigateTo(section: String) {
        // Note: movies, shows, search are now handled by internal Compose navigation
        // in HomeScreenWithSidebar. Only options needs fragment navigation.
        val fragment = when (section) {
            "options" -> OptionsFragment()
            else -> HomeComposeFragment() // Return to home for any unknown
        }

        // H2 FIX: Check lifecycle state before committing transaction
        // Prevents IllegalStateException: "Can not perform this action after onSaveInstanceState"
        // Occurs when navigating during background transition (e.g., pressing Home button)
        val transaction = supportFragmentManager.beginTransaction()
            .setCustomAnimations(
                R.anim.slide_in_right,  // enter
                R.anim.slide_out_left,   // exit
                R.anim.slide_in_left,    // popEnter
                R.anim.slide_out_right   // popExit
            )
            .replace(R.id.main_browse_fragment, fragment)
            .addToBackStack("home")

        // Safe commit: use commitAllowingStateLoss if state already saved
        if (supportFragmentManager.isStateSaved) {
            transaction.commitAllowingStateLoss()
        } else {
            transaction.commit()
        }
    }

    /**
     * Check if currently on home screen (backstack is empty)
     */
    private fun isOnHomeScreen(): Boolean {
        return supportFragmentManager.backStackEntryCount == 0
    }
}