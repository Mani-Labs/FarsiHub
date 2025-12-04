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
import android.util.Log
import coil.compose.AsyncImage
import com.example.farsilandtv.data.models.Episode

/**
 * Feature #16: Jetpack Compose for TV - Episode Card
 *
 * Card for displaying episode in series details screen
 * Shows thumbnail, title, description, progress, and watched status
 *
 * UC-L5 FIX: D-pad navigation documentation
 * Focus behavior:
 * - Receives focus via D-pad navigation
 * - Shows focus ring when focused (card border changes)
 * - Enter/Select triggers onClick (plays episode)
 * - Horizontal layout optimized for TV landscape orientation
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
            .height(84.dp)
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
                    .width(126.dp)
                    .fillMaxHeight()
            ) {
                val imageUrl = episode.thumbnailUrl ?: episode.episodePosterUrl
                Log.d("EpisodeCard", "Episode ${episode.id} thumbnailUrl=$imageUrl")
                AsyncImage(
                    model = imageUrl,
                    contentDescription = "Episode ${episode.formattedNumber} thumbnail",
                    modifier = Modifier.fillMaxSize()
                )

                // Source badge at top-left
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(4.dp)
                ) {
                    SourceBadge(sourceUrl = episode.farsilandUrl)
                }

                // Watched checkmark overlay
                if (episode.isWatched) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp),
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.primary
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = "Watched",
                            modifier = Modifier
                                .padding(2.dp)
                                .size(12.dp),
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }

            // Episode info
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Title and description
                Column {
                    Text(
                        text = "${episode.formattedNumber}: ${episode.title}",
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (!episode.description.isNullOrBlank()) {
                        Text(
                            text = episode.description,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Bottom row: progress bar and metadata
                Column {
                    // Runtime and air date
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        episode.runtime?.let { runtime ->
                            Text(
                                text = "${runtime}m",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        episode.quality?.let { quality ->
                            Text(
                                text = quality,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    // Progress bar (only show if in progress)
                    if (episode.isInProgress && episode.totalDuration > 0) {
                        Spacer(modifier = Modifier.height(2.dp))
                        LinearProgressIndicator(
                            progress = episode.playbackPosition.toFloat() / episode.totalDuration.toFloat(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(2.dp),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    }
                }
            }
        }
    }
}
