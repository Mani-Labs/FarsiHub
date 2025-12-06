package com.example.farsilandtv.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Minimal Dark Theme - Atmospheric depth with warm amber
 * Based on designprompts.dev/minimal-dark design system
 *
 * Philosophy: Layered darkness with warm amber accents that glow like embers.
 * At least 3 distinct dark tones (#0A0A0F → #12121A → #1A1A24)
 */

// Primary colors (Warm Amber accent - "glowing embers")
val FarsilandAmber = Color(0xFFF59E0B)        // Main accent - amber-500
val FarsilandAmberLight = Color(0xFFFBBF24)  // Light variant - amber-400
val FarsilandAmberDark = Color(0xFFD97706)   // Dark variant - amber-600
val FarsilandAmberGlow = Color(0x26F59E0B)   // 15% alpha for glow backgrounds (accentMuted)
val FarsilandAmberBorderGlow = Color(0x4DF59E0B) // 30% alpha for border glows

// Legacy aliases for compatibility
val FarsilandPink = FarsilandAmber
val FarsilandPinkLight = FarsilandAmberLight
val FarsilandPinkDark = FarsilandAmberDark

// Background colors (Layered darkness - NOT pure black)
val BackgroundDark = Color(0xFF0A0A0F)       // Deepest - almost black but warmer
val BackgroundAlt = Color(0xFF12121A)        // Slightly elevated surfaces
val SurfaceDark = Color(0xFF1A1A24)          // Card backgrounds, muted surfaces
val SurfaceLight = Color(0xFF2A2A35)         // Lighter surface for selection/hover

// Glass-effect card backgrounds (semi-transparent)
val CardBackground = Color(0x991A1A24)       // 60% opacity for glass effect
val CardBackgroundSolid = Color(0xFF1A1A24)  // Solid card background

// Text colors
val OnPrimary = Color(0xFF0A0A0F)            // Dark text on amber
val OnBackground = Color(0xFFFAFAFA)         // Near-white text (foreground)
val OnSurface = Color(0xFFFAFAFA)            // Same as foreground
val OnSurfaceVariant = Color(0xFF71717A)     // Muted text (zinc-500)

// Border colors (very subtle)
val BorderDefault = Color(0x14FFFFFF)        // 8% white opacity
val BorderHover = Color(0x26FFFFFF)          // 15% white opacity

// Focus/Selection colors
val FocusHighlight = FarsilandAmber          // Amber focus ring
val FocusGlow = Color(0x66F59E0B)            // 40% alpha for button hover glow
val SelectedBackground = Color(0xFF2A2A35)   // Elevated surface
val DefaultBackground = Color(0xFF1A1A24)    // Card level

// Genre badge colors (keep unchanged - work well)
val GenreAction = Color(0xFFE53935)
val GenreComedy = Color(0xFFFFA726)
val GenreDrama = Color(0xFF5E35B1)
val GenreHorror = Color(0xFF424242)
val GenreRomance = Color(0xFFEC407A)
val GenreSci_Fi = Color(0xFF26C6DA)
val GenreThriller = Color(0xFFD32F2F)
val GenreAnimation = Color(0xFF7E57C2)
val GenreDocumentary = Color(0xFF66BB6A)
val GenreFamily = Color(0xFF42A5F5)
val GenreFantasy = Color(0xFF9C27B0)
val GenreCrime = Color(0xFF8D6E63)
val GenreMystery = Color(0xFF5C6BC0)
val GenreAdventure = Color(0xFF26A69A)
val GenreWar = Color(0xFF8D6E63)
val GenreHistory = Color(0xFFAB47BC)
val GenreMusic = Color(0xFFEF5350)
val GenreWestern = Color(0xFFD4A574)
val GenreBiography = Color(0xFF78909C)

// Status indicator colors
val WatchedGreen = Color(0xFF4CAF50)
val FavoriteRed = Color(0xFFFF5252)
val ContinueWatchingBlue = Color(0xFF2196F3)
