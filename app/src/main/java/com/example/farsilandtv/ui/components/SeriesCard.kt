package com.example.farsilandtv.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import coil.compose.AsyncImage
import com.example.farsilandtv.data.models.Series
import com.example.farsilandtv.utils.DeviceUtils
import com.example.farsilandtv.utils.LocalDeviceType
import com.example.farsilandtv.utils.TvFeedbackManager

/**
 * Feature #16: Compose Component - Series Card
 * Similar to MovieCard but with season/episode count
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
fun SeriesCard(
    series: Series,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isFavorite: Boolean = false,
    hasUnwatchedEpisodes: Boolean = false,
    onLongClick: (() -> Unit)? = null
) {
    var isFocused by remember { mutableStateOf(false) }
    var longPressTriggered by remember { mutableStateOf(false) }

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
            // Poster image
            AsyncImage(
                model = series.posterUrl,
                contentDescription = series.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // Bottom section: Season/Episode count + Genre badges
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                // Season/Episode count badge
                if (series.totalSeasons > 0 || series.totalEpisodes > 0) {
                    androidx.compose.material3.Surface(
                        color = Color(0xCC000000),
                        shape = RoundedCornerShape(3.dp)
                    ) {
                        Text(
                            text = buildString {
                                if (series.totalSeasons > 0) {
                                    append("S${series.totalSeasons}")
                                }
                                if (series.totalEpisodes > 0) {
                                    if (series.totalSeasons > 0) append("•")
                                    append("${series.totalEpisodes}Ep")
                                }
                            },
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White
                        )
                    }
                }

                // Genre badges
                if (series.genres.isNotEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        series.genres.take(1).forEach { genre ->
                            GenreBadge(genreName = genre)
                        }
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
                if (isFavorite) {
                    FavoriteBadge()
                }
            }

            // Top-left badges: Source + NEW indicator
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                // Source badge
                SourceBadge(sourceUrl = series.farsilandUrl)

                // New episodes indicator
                if (hasUnwatchedEpisodes) {
                    androidx.compose.material3.Surface(
                        color = Color(0xFFFF5722),
                        shape = RoundedCornerShape(3.dp)
                    ) {
                        Text(
                            text = "NEW",
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White
                        )
                    }
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
        val seasonInfo = if (series.totalSeasons > 0) "${series.totalSeasons} seasons" else ""
        val episodeInfo = if (series.totalEpisodes > 0) "${series.totalEpisodes} episodes" else ""
        val accessibilityDescription = buildString {
            append(series.title)
            if (seasonInfo.isNotEmpty() || episodeInfo.isNotEmpty()) {
                append(", ")
                append(listOf(seasonInfo, episodeInfo).filter { it.isNotEmpty() }.joinToString(", "))
            }
            if (isFavorite) append(", favorite")
            if (hasUnwatchedEpisodes) append(", has new episodes")
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
                .then(
                    if (isFocused) {
                        Modifier.border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(6.dp))
                    } else Modifier
                ),
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
 * Skeleton placeholder for SeriesCard during loading
 * TV-L11 FIX: Use shimmer animation for loading skeletons
 */
@Composable
fun SeriesCardSkeleton(
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

        // Season info placeholder
        Box(
            modifier = Modifier
                .width(50.dp)
                .height(10.dp)
                .background(
                    shimmerBrush(),
                    shape = RoundedCornerShape(3.dp)
                )
        )
    }
}
