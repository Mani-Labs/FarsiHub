package com.example.farsilandtv.utils

import android.content.Context
import android.util.Log
import android.view.KeyEvent
import android.widget.Toast

/**
 * Feature #21: Keyboard Shortcuts Handler
 *
 * Handles keyboard shortcuts for TV remote and keyboard
 * Makes app faster to navigate for power users
 *
 * Shortcuts:
 * - S or Search button: Open Search
 * - P or Play button: Play/Pause
 * - F or Bookmark button: Open Favorites
 * - M or Menu button: Open Menu/Options
 */
class KeyboardShortcutHandler(
    private val context: Context,
    private val onSearch: () -> Unit = {},
    private val onPlay: () -> Unit = {},
    private val onFavorites: () -> Unit = {},
    private val onMenu: () -> Unit = {}
) {

    fun handleKeyEvent(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return false

        return when (event.keyCode) {
            // Search shortcuts: S key or Search button
            KeyEvent.KEYCODE_S,
            KeyEvent.KEYCODE_SEARCH -> {
                Log.d(TAG, "Search shortcut triggered")
                onSearch()
                true
            }

            // Play/Pause shortcuts: P key, Media Play, or Media Play/Pause
            KeyEvent.KEYCODE_P,
            KeyEvent.KEYCODE_MEDIA_PLAY,
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                Log.d(TAG, "Play/Pause shortcut triggered")
                onPlay()
                true
            }

            // Favorites shortcuts: F key or Bookmark
            KeyEvent.KEYCODE_F,
            KeyEvent.KEYCODE_BOOKMARK -> {
                Log.d(TAG, "Favorites shortcut triggered")
                onFavorites()
                true
            }

            // Menu shortcuts: M key or Menu button
            KeyEvent.KEYCODE_M,
            KeyEvent.KEYCODE_MENU -> {
                Log.d(TAG, "Menu shortcut triggered")
                onMenu()
                true
            }

            // Fast forward / Rewind (for future video player enhancement)
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                Log.d(TAG, "Fast forward pressed (no action)")
                false // Let player handle
            }

            KeyEvent.KEYCODE_MEDIA_REWIND -> {
                Log.d(TAG, "Rewind pressed (no action)")
                false // Let player handle
            }

            else -> false
        }
    }

    companion object {
        private const val TAG = "KeyShortcut"

        /**
         * Show keyboard shortcuts help toast
         */
        fun showShortcutsToast(context: Context) {
            val message = """
                Keyboard Shortcuts:
                S - Search
                P - Play/Pause
                F - Favorites
                M - Menu
            """.trimIndent()

            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }
}
