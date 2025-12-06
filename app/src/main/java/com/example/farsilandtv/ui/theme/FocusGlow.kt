package com.example.farsilandtv.ui.theme

import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Focus glow effect for TV navigation
 * Uses shadow for ambient glow (hardware-accelerated, TV-safe)
 */

/**
 * Applies an ambient glow effect when focused
 * @param isFocused Whether the element is currently focused
 * @param glowColor Color for the glow effect (defaults to amber)
 * @param glowRadius Size of the glow effect
 * @param borderWidth Width of the focus border
 * @param shape Shape for the glow and border
 */
@Composable
fun Modifier.focusGlow(
    isFocused: Boolean,
    glowColor: Color = FarsilandAmber,
    glowRadius: Dp = 12.dp,
    borderWidth: Dp = 2.dp,
    shape: Shape
): Modifier = if (isFocused) {
    this
        .shadow(
            elevation = glowRadius,
            shape = shape,
            ambientColor = glowColor.copy(alpha = 0.5f),
            spotColor = glowColor.copy(alpha = 0.3f)
        )
        .border(borderWidth, glowColor, shape)
} else {
    this
}

/**
 * Simplified focus glow with default amber color
 */
@Composable
fun Modifier.focusGlow(
    isFocused: Boolean,
    shape: Shape
): Modifier = focusGlow(
    isFocused = isFocused,
    glowColor = FarsilandAmber,
    glowRadius = 12.dp,
    borderWidth = 2.dp,
    shape = shape
)
