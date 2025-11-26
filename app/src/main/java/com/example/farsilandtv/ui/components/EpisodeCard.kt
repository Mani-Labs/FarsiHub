package com.example.farsilandtv.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.farsilandtv.data.models.Episode

/**
 * Feature #16: Jetpack Compose for TV - Episode Card
 *
 * Card for displaying episode in series details screen
 * Shows thumbnail, title, description, progress, and watched status
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EpisodeCard(
    episode: Episode,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier
            .height(120.dp)
            .fillMaxWidth(),
        border = BorderStroke(2.dp, Color.Transparent),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.fillMaxSize()
        ) {
            // Episode thumbnail (16:9 aspect ratio)
            Box(
                modifier = Modifier
                    .width(180.dp)
                    .fillMaxHeight()
            ) {
                AsyncImage(
                    model = episode.thumbnailUrl ?: episode.episodePosterUrl,
                    contentDescription = "Episode ${episode.formattedNumber} thumbnail",
                    modifier = Modifier.fillMaxSize()
                )

                // Source badge at top-left
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                ) {
                    SourceBadge(sourceUrl = episode.farsilandUrl)
                }

                // Watched checkmark overlay
                if (episode.isWatched) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp),
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.primary
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = "Watched",
                            modifier = Modifier.padding(4.dp),
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }

            // Episode info
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Title and description
                Column {
                    Text(
                        text = "${episode.formattedNumber}: ${episode.title}",
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = episode.description,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Bottom row: progress bar and metadata
                Column {
                    // Runtime and air date
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        episode.runtime?.let { runtime ->
                            Text(
                                text = "${runtime}m",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        episode.airDate?.let { airDate ->
                            Text(
                                text = airDate,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        episode.quality?.let { quality ->
                            Text(
                                text = quality,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    // Progress bar (only show if in progress)
                    if (episode.isInProgress && episode.totalDuration > 0) {
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = episode.playbackPosition.toFloat() / episode.totalDuration.toFloat(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    }
                }
            }
        }
    }
}
