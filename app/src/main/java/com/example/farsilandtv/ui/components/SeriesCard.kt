package com.example.farsilandtv.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import coil.compose.AsyncImage
import com.example.farsilandtv.data.models.Series
import com.example.farsilandtv.utils.DeviceUtils
import com.example.farsilandtv.utils.LocalDeviceType

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
        Surface(
            onClick = {
                if (!longPressTriggered) {
                    onClick()
                }
                longPressTriggered = false
            },
            onLongClick = onLongClick?.let { callback ->
                {
                    longPressTriggered = true
                    callback()
                }
            },
            modifier = modifier
                .width(112.dp)
                .height(168.dp)
                .onFocusChanged { focusState ->
                    isFocused = focusState.isFocused
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
 */
@Composable
fun SeriesCardSkeleton(
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .width(112.dp)
            .height(168.dp),
        shape = RoundedCornerShape(6.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF2C2C2C)
        )
    ) {
        Box(modifier = Modifier.fillMaxSize())
    }
}
