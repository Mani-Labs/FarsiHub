package com.example.farsilandtv

import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
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
class MainActivity : FragmentActivity() {

    // Double-back-to-exit tracking
    private var backPressedTime: Long = 0
    private val backPressInterval = 2000L // 2 seconds

    override fun onCreate(savedInstanceState: Bundle?) {
        // Switch from splash theme to main theme before super.onCreate
        setTheme(R.style.Theme_FarsilandTV)

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Setup back press handler with double-back-to-exit on home screen
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isOnHomeScreen()) {
                    // Home screen: implement double-back-to-exit
                    if (System.currentTimeMillis() - backPressedTime < backPressInterval) {
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
            // DB ready, start with Compose Home screen
            getSupportFragmentManager().beginTransaction()
                .replace(R.id.main_browse_fragment, ComposeHomeFragment())
                .commitNow()
        }
    }

    /**
     * Show loading screen while database copies from assets (first launch only)
     * Polls for content_db_initialized flag and starts HomeFragment when ready
     */
    private fun showDatabaseLoadingScreen() {
        // Show loading message in timestamp area
        val timestampView = findViewById<TextView>(R.id.timestamp_text)
        timestampView?.apply {
            text = "Loading content database..."
            visibility = View.VISIBLE
        }

        // Poll for initialization complete
        lifecycleScope.launch {
            val prefs = getSharedPreferences("app_state", MODE_PRIVATE)
            var attempts = 0
            val maxAttempts = 120 // 2 minutes max (120 * 1000ms)

            while (!prefs.getBoolean("content_db_initialized", false) &&
                   !prefs.getBoolean("content_db_error", false) &&
                   attempts < maxAttempts) {
                delay(1000) // Check every second
                attempts++

                // Update progress
                timestampView?.text = "Loading content database... ${attempts}s"
            }

            // Check final status
            if (prefs.getBoolean("content_db_initialized", false)) {
                // Success - load Compose Home screen
                timestampView?.visibility = View.GONE
                if (!supportFragmentManager.isStateSaved) {
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.main_browse_fragment, ComposeHomeFragment())
                        .commitNow()
                }
            } else if (prefs.getBoolean("content_db_error", false)) {
                // Fatal error - show permanent error message
                val errorMsg = prefs.getString("content_db_error_message", "Unknown error")
                timestampView?.text = "FATAL: Database corrupted. Clear app data: Settings → Apps → FarsiPlex → Storage → Clear Data"
                Toast.makeText(
                    this@MainActivity,
                    "Database error: $errorMsg",
                    Toast.LENGTH_LONG
                ).show()
            } else {
                // Timeout - show error
                timestampView?.text = "Database initialization timed out. Please restart the app."
                Toast.makeText(
                    this@MainActivity,
                    "Database initialization timed out",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    /**
     * Update the timestamp display in the corner
     * Accepts CharSequence to support colored SpannableStrings
     */
    fun updateTimestamp(text: CharSequence) {
        findViewById<TextView>(R.id.timestamp_text)?.text = text
    }

    /**
     * Navigate to a specific section
     */
    fun navigateTo(section: String) {
        val fragment = when (section) {
            "movies" -> MoviesFragment()
            "shows" -> ShowsFragment()
            "search" -> SearchFragment()
            "stats" -> StatsFragment()
            "sync-settings" -> SyncSettingsFragment()
            "options" -> OptionsFragment()
            else -> ComposeHomeFragment()
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