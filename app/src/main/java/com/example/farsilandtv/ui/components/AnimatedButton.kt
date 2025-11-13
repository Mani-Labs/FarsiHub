package com.example.farsilandtv.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * Week 4 - Feature #16: Animated Button with Press Effects
 *
 * TV-optimized button with:
 * - Scale animation on press (spring effect)
 * - Focus border (4dp primary color)
 * - Haptic feedback simulation via animation
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnimatedButton(
    onClick: () -> Unit,
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    enabled: Boolean = true
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    var isFocused by remember { mutableStateOf(false) }

    // Scale animation with spring
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "button_scale"
    )

    Button(
        onClick = onClick,
        modifier = modifier
            .scale(scale)
            .onFocusChanged { focusState ->
                isFocused = focusState.isFocused
            },
        interactionSource = interactionSource,
        colors = colors,
        enabled = enabled,
        border = if (isFocused) {
            BorderStroke(4.dp, MaterialTheme.colorScheme.primary)
        } else null
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(text)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnimatedOutlinedButton(
    onClick: () -> Unit,
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    var isFocused by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "outlined_button_scale"
    )

    OutlinedButton(
        onClick = onClick,
        modifier = modifier
            .scale(scale)
            .onFocusChanged { focusState ->
                isFocused = focusState.isFocused
            },
        interactionSource = interactionSource,
        enabled = enabled,
        border = BorderStroke(
            width = if (isFocused) 4.dp else 1.dp,
            color = if (isFocused) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outline
            }
        )
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(text)
    }
}

/**
 * Icon button with scale animation
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnimatedIconButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = LocalContentColor.current
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    var isFocused by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.9f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "icon_button_scale"
    )

    IconButton(
        onClick = onClick,
        modifier = modifier
            .scale(scale)
            .onFocusChanged { focusState ->
                isFocused = focusState.isFocused
            },
        interactionSource = interactionSource,
        enabled = enabled
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = if (isFocused) {
                Modifier.size(28.dp) // Slightly larger when focused
            } else {
                Modifier.size(24.dp)
            }
        )
    }
}
