package com.example.farsilandtv.utils

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.focusable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import android.util.Log

/**
 * Week 4 - Feature #16: TV Focus Optimization
 *
 * Provides utilities for enhanced D-pad navigation:
 * - Custom focus indicators (4dp pink border)
 * - Focus order optimization
 * - Initial focus management
 * - Focus traversal improvements
 */

/**
 * Standard TV focus border
 * Applies 4dp primary color border when focused
 */
fun Modifier.tvFocusBorder(): Modifier = composed {
    var isFocused by remember { mutableStateOf(false) }

    this
        .onFocusChanged { focusState ->
            isFocused = focusState.isFocused
        }
        .then(
            if (isFocused) {
                Modifier // Border is now handled in Card/Button components
            } else {
                Modifier
            }
        )
}

/**
 * Request initial focus after a delay
 * Useful for ensuring focus on important elements (Play button, first card)
 */
fun Modifier.requestInitialFocus(
    focusRequester: FocusRequester,
    delayMillis: Long = 100
): Modifier = composed {
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(delayMillis)
        try {
            focusRequester.requestFocus()
        } catch (e: Exception) {
            Log.w("TvFocus", "Failed to request initial focus", e)
        }
    }

    this.focusRequester(focusRequester)
}

/**
 * Skip disabled items during focus traversal
 * Automatically moves to next focusable item
 */
fun Modifier.skipWhenDisabled(enabled: Boolean): Modifier {
    return if (enabled) {
        this.focusable()
    } else {
        this // Not focusable
    }
}

/**
 * Focus properties for TV components
 */
data class TvFocusProperties(
    val borderWidth: androidx.compose.ui.unit.Dp = 4.dp,
    val borderColor: Color = Color.Transparent,
    val cornerRadius: androidx.compose.ui.unit.Dp = 8.dp,
    val scaleOnFocus: Float = 1.0f
)

/**
 * Default TV focus properties
 */
@Composable
fun rememberTvFocusProperties(): TvFocusProperties {
    val primaryColor = MaterialTheme.colorScheme.primary

    return remember {
        TvFocusProperties(
            borderWidth = 4.dp,
            borderColor = primaryColor,
            cornerRadius = 8.dp,
            scaleOnFocus = 1.05f
        )
    }
}

/**
 * Create focus border stroke for focused state
 */
@Composable
fun focusBorderStroke(isFocused: Boolean): BorderStroke? {
    val primaryColor = MaterialTheme.colorScheme.primary

    return if (isFocused) {
        BorderStroke(4.dp, primaryColor)
    } else {
        null
    }
}

/**
 * Focus logging for debugging
 */
object TvFocusDebugger {
    private const val TAG = "TvFocus"

    fun logFocusChange(componentName: String, isFocused: Boolean) {
        Log.d(TAG, "Focus changed: $componentName = $isFocused")
    }

    fun logFocusRequest(componentName: String, success: Boolean) {
        Log.d(TAG, "Focus request: $componentName, success = $success")
    }

    fun logFocusTraversal(from: String, to: String) {
        Log.d(TAG, "Focus traversal: $from -> $to")
    }
}

/**
 * Custom focus order for complex layouts
 * Ensures logical tab order for D-pad navigation
 */
class TvFocusOrder {
    private val focusRequesters = mutableListOf<Pair<String, FocusRequester>>()

    fun add(name: String, focusRequester: FocusRequester) {
        focusRequesters.add(name to focusRequester)
    }

    fun requestNext(currentName: String) {
        val currentIndex = focusRequesters.indexOfFirst { it.first == currentName }
        if (currentIndex >= 0 && currentIndex < focusRequesters.size - 1) {
            val nextFocusRequester = focusRequesters[currentIndex + 1].second
            try {
                nextFocusRequester.requestFocus()
                TvFocusDebugger.logFocusTraversal(
                    focusRequesters[currentIndex].first,
                    focusRequesters[currentIndex + 1].first
                )
            } catch (e: Exception) {
                Log.w("TvFocusOrder", "Failed to request next focus", e)
            }
        }
    }

    fun requestPrevious(currentName: String) {
        val currentIndex = focusRequesters.indexOfFirst { it.first == currentName }
        if (currentIndex > 0) {
            val prevFocusRequester = focusRequesters[currentIndex - 1].second
            try {
                prevFocusRequester.requestFocus()
                TvFocusDebugger.logFocusTraversal(
                    focusRequesters[currentIndex].first,
                    focusRequesters[currentIndex - 1].first
                )
            } catch (e: Exception) {
                Log.w("TvFocusOrder", "Failed to request previous focus", e)
            }
        }
    }

    fun requestFirst() {
        focusRequesters.firstOrNull()?.second?.let { focusRequester ->
            try {
                focusRequester.requestFocus()
            } catch (e: Exception) {
                Log.w("TvFocusOrder", "Failed to request first focus", e)
            }
        }
    }
}

/**
 * Remember TV focus order
 */
@Composable
fun rememberTvFocusOrder(): TvFocusOrder {
    return remember { TvFocusOrder() }
}
