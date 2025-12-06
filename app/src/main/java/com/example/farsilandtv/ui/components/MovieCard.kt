package com.example.farsilandtv.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import coil.compose.AsyncImage
import com.example.farsilandtv.data.models.Movie
import com.example.farsilandtv.ui.theme.focusGlow
import com.example.farsilandtv.utils.DeviceUtils
import com.example.farsilandtv.utils.LocalDeviceType
import com.example.farsilandtv.utils.TvFeedbackManager

/**
 * Feature #16: Compose Component - Movie Card
 * TV-optimized card with D-pad focus support
 *
 * Features:
 * - Coil AsyncImage for progressive loading (Feature #17)
 * - Genre badges (Feature #3)
 * - Favorite indicator (Feature #1)
 * - Watched indicator (Feature #2)
 * - TV focus highlight with border
 *
 * UC-L5 FIX: D-pad navigation documentation
 * Focus behavior:
 * - Receives focus via D-pad navigation
 * - Shows focus ring when focused (3dp primary color border)
 * - Enter/Select triggers onClick
 * - Long press (hold Select) triggers onLongClick (opens options menu)
 * - Scale animation (1.05x) on focus for visual feedback
 */
@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MovieCard(
    movie: Movie,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isFavorite: Boolean = false,
    isWatched: Boolean = false,
    progressPercent: Float? = null,  // Phase 3: Watch progress (0.0-1.0)
    onLongClick: (() -> Unit)? = null
) {
    var isFocused by remember { mutableStateOf(false) }
    var longPressTriggered by remember(movie.id) { mutableStateOf(false) }

    // TV-L4/L6: Context and view for sound and haptic feedback
    val context = LocalContext.current
    val view = LocalView.current

    // Check device type - use touch-friendly click for phones
    val deviceType = LocalDeviceType.current
    val isPhone = deviceType == DeviceUtils.DeviceType.PHONE

    // Card content composable to avoid duplication
    val cardContent: @Composable () -> Unit = {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            // Poster image with progressive loading
            // DEBUG: Log poster URL for IMVBox image loading diagnosis
            android.util.Log.d("MovieCard", "Loading image: ${movie.posterUrl} for ${movie.title}")
            AsyncImage(
                model = movie.posterUrl,
                contentDescription = movie.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                onError = { error ->
                    android.util.Log.e("MovieCard", "Image load FAILED for ${movie.title}: ${error.result.throwable?.message}")
                },
                onSuccess = {
                    android.util.Log.d("MovieCard", "Image load SUCCESS for ${movie.title}")
                }
            )

            // Genre badges at bottom
            if (movie.genres.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    movie.genres.take(1).forEach { genre ->
                        GenreBadge(genreName = genre)
                    }
                }
            }

            // Status badges at top-right
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                if (isWatched) {
                    WatchedBadge()
                }
                if (isFavorite) {
                    FavoriteBadge()
                }
            }

            // Source badge at top-left
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(4.dp)
            ) {
                SourceBadge(sourceUrl = movie.farsilandUrl)
            }

            // Phase 3: Progress bar at bottom for continue watching
            if (progressPercent != null && progressPercent > 0f) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(Color.Black.copy(alpha = 0.5f))
                ) {
                    LinearProgressIndicator(
                        progress = { progressPercent.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = Color.Transparent
                    )
                }
            }
        }
    }

    // Use different container based on device type
    if (isPhone) {
        // Phone: Use Box with combinedClickable for touch support
        Box(
            modifier = modifier
                .width(112.dp)
                .height(168.dp)
                .clip(RoundedCornerShape(6.dp))
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick
                )
        ) {
            cardContent()
        }
    } else {
        // TV/Tablet: Use TV Material 3 Surface for D-pad focus support
        // TV-L accessibility: semantic description for screen readers
        val accessibilityDescription = buildString {
            append(movie.title)
            // BUG FIX: Use firstOrNull() for defensive coding
            movie.genres.firstOrNull()?.let { genre ->
                append(", $genre")
            }
            if (isFavorite) append(", favorite")
            if (isWatched) append(", watched")
            if (progressPercent != null && progressPercent > 0f) {
                append(", ${(progressPercent * 100).toInt()}% watched")
            }
        }

        Surface(
            onClick = {
                if (!longPressTriggered) {
                    // TV-L6: Haptic feedback on selection
                    TvFeedbackManager.performSelectionHaptic(context)
                    onClick()
                }
                longPressTriggered = false
            },
            onLongClick = onLongClick?.let { callback ->
                {
                    longPressTriggered = true
                    // TV-L6: Heavy haptic for long-press (context menu)
                    TvFeedbackManager.performLongPressHaptic(context)
                    callback()
                }
            },
            modifier = modifier
                .width(112.dp)
                .height(168.dp)
                .onFocusChanged { focusState ->
                    // TV-L4: Play focus sound when gaining focus
                    if (focusState.isFocused && !isFocused) {
                        TvFeedbackManager.playFocusSound(view)
                    }
                    isFocused = focusState.isFocused
                }
                .semantics {
                    role = Role.Button
                    contentDescription = accessibilityDescription
                }
                .focusGlow(isFocused = isFocused, shape = RoundedCornerShape(6.dp)),
            shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(6.dp)),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Color.Transparent,
                focusedContainerColor = Color.Transparent
            ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f)
        ) {
            cardContent()
        }
    }
}

/**
 * Skeleton version for loading states (Feature #20)
 */
@Composable
fun MovieCardSkeleton(
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.width(112.dp)) {
        // Poster placeholder with shimmer
        Box(
            modifier = Modifier
                .width(112.dp)
                .height(168.dp)
                .background(
                    shimmerBrush(),
                    shape = RoundedCornerShape(6.dp)
                )
        )

        Spacer(modifier = Modifier.height(6.dp))

        // Title placeholder
        Box(
            modifier = Modifier
                .width(90.dp)
                .height(12.dp)
                .background(
                    shimmerBrush(),
                    shape = RoundedCornerShape(3.dp)
                )
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Subtitle placeholder
        Box(
            modifier = Modifier
                .width(60.dp)
                .height(10.dp)
                .background(
                    shimmerBrush(),
                    shape = RoundedCornerShape(3.dp)
                )
        )
    }
}
