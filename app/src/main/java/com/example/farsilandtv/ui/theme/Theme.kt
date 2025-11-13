package com.example.farsilandtv.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Feature #16: Jetpack Compose for TV - Theme
 * Dark theme optimized for TV viewing from 10-foot distance
 */

private val FarsilandDarkColorScheme = darkColorScheme(
    // Primary colors (Pink accent)
    primary = FarsilandPink,
    onPrimary = OnPrimary,
    primaryContainer = FarsilandPinkDark,
    onPrimaryContainer = Color.White,

    // Secondary colors
    secondary = FarsilandPinkLight,
    onSecondary = Color.Black,
    secondaryContainer = FarsilandPinkDark,
    onSecondaryContainer = Color.White,

    // Background colors
    background = BackgroundDark,
    onBackground = OnBackground,

    // Surface colors
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
