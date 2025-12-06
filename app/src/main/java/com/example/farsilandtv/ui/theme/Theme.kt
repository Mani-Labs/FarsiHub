package com.example.farsilandtv.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Minimal Dark Theme - Deep Slate with Warm Amber
 * Atmospheric dark mode optimized for TV viewing from 10-foot distance
 */

private val FarsilandDarkColorScheme = darkColorScheme(
    // Primary colors (Warm Amber accent)
    primary = FarsilandAmber,
    onPrimary = OnPrimary,
    primaryContainer = FarsilandAmberDark,
    onPrimaryContainer = Color.White,

    // Secondary colors
    secondary = FarsilandAmberLight,
    onSecondary = Color.Black,
    secondaryContainer = FarsilandAmberDark,
    onSecondaryContainer = Color.White,

    // Background colors (Deep slate)
    background = BackgroundDark,
    onBackground = OnBackground,

    // Surface colors (Slate family)
    surface = SurfaceDark,
    onSurface = OnSurface,
    surfaceVariant = SurfaceLight,
    onSurfaceVariant = OnSurfaceVariant,

    // Error colors
    error = Color(0xFFCF6679),
    onError = Color.Black
)

@Composable
fun FarsilandTVTheme(
    darkTheme: Boolean = true, // TV apps should always be dark
    content: @Composable () -> Unit
) {
    val colorScheme = FarsilandDarkColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = FarsilandTypography,
        content = content
    )
}
