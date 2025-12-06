package com.example.farsilandtv.utils

import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.SoundEffectConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView

/**
 * TV-L4 FIX: TV Focus Sound Feedback Manager
 * TV-L6 FIX: Haptic Feedback on Selection
 *
 * Provides audio and haptic feedback for TV navigation:
 * - Focus change sounds (subtle click for D-pad navigation)
 * - Selection sounds (confirmation click)
 * - Haptic feedback on selection (for devices that support it)
 *
 * ## D-pad Navigation Feedback Patterns (TV-L5 Documentation)
 *
 * | Action | Sound | Vibration |
 * |--------|-------|-----------|
 * | D-pad move (focus) | Click | None |
 * | Enter/Select press | Navigation | 50ms tick |
 * | Long press (500ms+) | None | 100ms heavy |
 * | Double-press (300ms) | Navigation | 50ms tick |
 *
 * ## TV-L1: Double-Press Detection
 * To implement double-press for options, use rememberDoublePressHandler()
 * which provides 300ms window detection between presses.
 *
 * ## TV-L2: Long-Press Timing
 * TV Material 3 Surface uses default 500ms for long-press.
 * This is compliant with Android TV guidelines.
 *
 * ## TV-L3: Focus Animation
 * Focus animation is handled by TV Material 3 Surface:
 * - Scale: 1.05x on focus (via ClickableSurfaceDefaults.scale())
 * - Border: 3dp primary color ring (via onFocusChanged modifier)
 * - Glow: Optional via elevation on focused state
 */
object TvFeedbackManager {

    private const val TAG = "TvFeedbackManager"

    // TV-L1: Double-press detection window in milliseconds
    const val DOUBLE_PRESS_WINDOW_MS = 300L

    // TV-L2: Long-press threshold in milliseconds (Android TV standard)
    const val LONG_PRESS_THRESHOLD_MS = 500L

    // TV-L6: Haptic durations
    private const val HAPTIC_TICK_MS = 50L
    private const val HAPTIC_HEAVY_MS = 100L

    /**
     * TV-L4: Play focus change sound
     * Called when D-pad navigation moves focus to a new item
     */
    fun playFocusSound(view: View) {
        view.playSoundEffect(SoundEffectConstants.CLICK)
    }

    /**
     * TV-L4: Play selection sound
     * Called when Enter/Select is pressed on a focused item
     */
    fun playSelectionSound(view: View) {
        view.playSoundEffect(SoundEffectConstants.NAVIGATION_UP)
    }

    /**
     * TV-L4: Play navigation sound using AudioManager
     * Alternative method when View is not available
     */
    fun playNavigationSound(context: Context) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        audioManager?.playSoundEffect(AudioManager.FX_KEY_CLICK)
    }

    /**
     * TV-L6: Trigger haptic feedback on selection
     * Provides tactile confirmation when user selects an item
     */
    fun performSelectionHaptic(context: Context) {
        performHaptic(context, HAPTIC_TICK_MS)
    }

    /**
     * TV-L6: Trigger heavy haptic feedback for long-press
     * Provides stronger feedback for context menu opening
     */
    fun performLongPressHaptic(context: Context) {
        performHaptic(context, HAPTIC_HEAVY_MS)
    }

    /**
     * TV-L6: Internal haptic implementation
     */
    private fun performHaptic(context: Context, durationMs: Long) {
        val vibrator = getVibrator(context) ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val effect = VibrationEffect.createOneShot(
                durationMs,
                VibrationEffect.DEFAULT_AMPLITUDE
            )
            vibrator.vibrate(effect)
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(durationMs)
        }
    }

    /**
     * Get vibrator service (handles API level differences)
     */
    private fun getVibrator(context: Context): Vibrator? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }
}

/**
 * TV-L1: Composable double-press handler
 * Returns a callback that detects double-presses within 300ms window
 *
 * Usage:
 * ```kotlin
 * val handleClick = rememberDoublePressHandler(
 *     onSingleClick = { /* navigate to details */ },
 *     onDoubleClick = { /* open options menu */ }
 * )
 *
 * Surface(onClick = handleClick) { ... }
 * ```
 */
@Composable
fun rememberDoublePressHandler(
    onSingleClick: () -> Unit,
    onDoubleClick: () -> Unit
): () -> Unit {
    val context = LocalContext.current
    val view = LocalView.current
    var lastClickTime = remember { 0L }

    return {
        val currentTime = System.currentTimeMillis()
        val timeSinceLastClick = currentTime - lastClickTime

        if (timeSinceLastClick < TvFeedbackManager.DOUBLE_PRESS_WINDOW_MS && lastClickTime > 0) {
            // Double press detected
            TvFeedbackManager.performSelectionHaptic(context)
            TvFeedbackManager.playSelectionSound(view)
            onDoubleClick()
            lastClickTime = 0L // Reset to prevent triple-press
        } else {
            // Single press - wait for potential double press
            lastClickTime = currentTime
            // Delay single click action to allow for double press detection
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                if (System.currentTimeMillis() - lastClickTime >= TvFeedbackManager.DOUBLE_PRESS_WINDOW_MS) {
                    TvFeedbackManager.playSelectionSound(view)
                    TvFeedbackManager.performSelectionHaptic(context)
                    onSingleClick()
                }
            }, TvFeedbackManager.DOUBLE_PRESS_WINDOW_MS)
        }
    }
}

/**
 * TV-L4/L6: Composable focus feedback handler
 * Plays sound and haptic when focus state changes
 *
 * Usage:
 * ```kotlin
 * var isFocused by remember { mutableStateOf(false) }
 * val focusFeedback = rememberFocusFeedback()
 *
 * Surface(
 *     modifier = Modifier.onFocusChanged { state ->
 *         if (state.isFocused && !isFocused) {
 *             focusFeedback.onFocusGained()
 *         }
 *         isFocused = state.isFocused
 *     }
 * ) { ... }
 * ```
 */
@Composable
fun rememberFocusFeedback(): FocusFeedback {
    val view = LocalView.current

    return remember {
        object : FocusFeedback {
            override fun onFocusGained() {
                TvFeedbackManager.playFocusSound(view)
            }
        }
    }
}

interface FocusFeedback {
    fun onFocusGained()
}
