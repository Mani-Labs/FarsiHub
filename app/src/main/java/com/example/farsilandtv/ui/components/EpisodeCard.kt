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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import android.util.Log
import coil.compose.AsyncImage
import com.example.farsilandtv.data.models.Episode
import com.example.farsilandtv.utils.TvFeedbackManager

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
    // TV-L4/L6: Context for haptic feedback
    val context = LocalContext.current

    // UC-L accessibility: semantic description for screen readers
    val accessibilityDescription = buildString {
        append("${episode.formattedNumber}: ${episode.title}")
        if (episode.isWatched) append(", watched")
        else if (episode.isInProgress) {
            val percent = ((episode.playbackPosition.toFloat() / episode.totalDuration) * 100).toInt()
            append(", $percent% watched")
        }
        episode.runtime?.let { append(", $it minutes") }
    }

    // Vertical card layout matching IMVBox website design
    // Poster ratio ~2:3 (width:height) to match IMVBox episode thumbnails
    Card(
        onClick = {
            // TV-L6: Haptic feedback on selection
            TvFeedbackManager.performSelectionHaptic(context)
            onClick()
        },
        modifier = modifier
            .width(120.dp)
            .semantics {
                role = Role.Button
                contentDescription = accessibilityDescription
            },
        border = BorderStroke(2.dp, Color.Transparent),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column {
            // Episode thumbnail (2:3 poster aspect ratio - matches IMVBox)
            Box(
                modifier = Modifier
                    .width(120.dp)
                    .height(180.dp)  // 2:3 ratio (120 x 180)
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

                // Progress bar overlay at bottom of thumbnail (if in progress)
                if (episode.isInProgress && episode.totalDuration > 0) {
                    val progressValue = episode.playbackPosition.toFloat() / episode.totalDuration.toFloat()
                    LinearProgressIndicator(
                        progress = { progressValue },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(3.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = Color.Black.copy(alpha = 0.5f)
                    )
                }
            }

            // Episode info below thumbnail
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 6.dp)
            ) {
                // Episode title
                Text(
                    text = episode.formattedNumber,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // Duration
                episode.runtime?.let { runtime ->
                    Text(
                        text = "$runtime min",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
