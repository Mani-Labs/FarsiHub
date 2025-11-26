package com.example.farsilandtv.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.tv.material3.ExperimentalTvMaterial3Api
import coil.compose.AsyncImage
import com.example.farsilandtv.data.models.Series

/**
 * Feature #16: Compose Component - Series Card
 * Similar to MovieCard but with season/episode count
 */
@OptIn(ExperimentalTvMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
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

    Card(
        onClick = onClick,
        modifier = modifier
            .width(160.dp)
            .height(240.dp)
            .onFocusChanged { focusState ->
                isFocused = focusState.isFocused
            }
            .then(
                if (onLongClick != null) {
                    Modifier.combinedClickable(
                        onClick = onClick,
                        onLongClick = onLongClick
                    )
                } else Modifier
            ),
        shape = RoundedCornerShape(6.dp),
        border = if (isFocused) {
            BorderStroke(3.dp, MaterialTheme.colorScheme.primary)
        } else {
            null
        },
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent
        )
    ) {
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
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Season/Episode count badge
                if (series.totalSeasons > 0 || series.totalEpisodes > 0) {
                    androidx.compose.material3.Surface(
                        color = Color(0xCC000000),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = buildString {
                                if (series.totalSeasons > 0) {
                                    append("S${series.totalSeasons}")
                                }
                                if (series.totalEpisodes > 0) {
                                    if (series.totalSeasons > 0) append(" • ")
                                    append("${series.totalEpisodes} Ep")
                                }
                            },
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White
                        )
                    }
                }

                // Genre badges
                if (series.genres.isNotEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        series.genres.take(2).forEach { genre ->
                            GenreBadge(genreName = genre)
                        }
                    }
                }
            }

            // Status badges at top-right
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (isFavorite) {
                    FavoriteBadge()
                }
            }

            // Top-left badges: Source + NEW indicator
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Source badge
                SourceBadge(sourceUrl = series.farsilandUrl)

                // New episodes indicator
                if (hasUnwatchedEpisodes) {
                    androidx.compose.material3.Surface(
                        color = Color(0xFFFF5722),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "NEW",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White
                        )
                    }
                }
            }
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
            .width(160.dp)
            .height(240.dp),
        shape = RoundedCornerShape(6.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF2C2C2C)
        )
    ) {
        Box(modifier = Modifier.fillMaxSize())
    }
}
