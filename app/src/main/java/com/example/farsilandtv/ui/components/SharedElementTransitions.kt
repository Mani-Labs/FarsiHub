package com.example.farsilandtv.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Week 4 - Feature #16: Shared Element Transitions
 *
 * Provides smooth animations between screens:
 * - Card → Detail Screen transition
 * - Fade backdrop on navigation
 * - Scale animation for cards on click
 * - Slide-in/out for side panels
 */

/**
 * Standard fade + slide transition for content appearing
 */
fun contentEnterTransition(): EnterTransition {
    return fadeIn(
        animationSpec = tween(300, easing = LinearEasing)
    ) + slideInVertically(
        initialOffsetY = { it / 4 },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMedium
        )
    )
}

/**
 * Standard fade + slide transition for content disappearing
 */
fun contentExitTransition(): ExitTransition {
    return fadeOut(
        animationSpec = tween(200, easing = LinearEasing)
    ) + slideOutVertically(
        targetOffsetY = { -it / 4 },
        animationSpec = tween(200, easing = FastOutSlowInEasing)
    )
}

/**
 * Scale animation for cards on press
 */
fun cardScaleAnimation(isPressed: Boolean): Float {
    return if (isPressed) 0.95f else 1f
}

/**
 * Spring spec for card press animations
 */
val cardPressSpring = spring<Float>(
    dampingRatio = Spring.DampingRatioMediumBouncy,
    stiffness = Spring.StiffnessLow
)

/**
 * Backdrop parallax multiplier
 * Backdrop moves at half speed of content
 */
const val PARALLAX_MULTIPLIER = 0.5f

/**
 * Slide-in from right for side panels
 */
fun sidePanelEnterTransition(): EnterTransition {
    return slideInHorizontally(
        initialOffsetX = { it },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        )
    ) + fadeIn()
}

/**
 * Slide-out to right for side panels
 */
fun sidePanelExitTransition(): ExitTransition {
    return slideOutHorizontally(
        targetOffsetX = { it },
        animationSpec = tween(250, easing = FastOutSlowInEasing)
    ) + fadeOut()
}

/**
 * Stagger delay for list items
 * Each item delays by 50ms
 */
fun staggerDelay(index: Int): Int {
    return (index * 50).coerceAtMost(500) // Max 500ms delay
}

/**
 * Fade transition for backdrop images
 */
val backdropFadeSpec = tween<Float>(
    durationMillis = 400,
    easing = LinearEasing
)
