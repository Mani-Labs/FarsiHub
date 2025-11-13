package com.example.farsilandtv.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import coil.compose.AsyncImage
import com.example.farsilandtv.data.models.Movie

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
 */
@OptIn(ExperimentalTvMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun MovieCard(
    movie: Movie,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isFavorite: Boolean = false,
    isWatched: Boolean = false
) {
    var isFocused by remember { mutableStateOf(false) }

    Card(
        onClick = onClick,
        modifier = modifier
            .width(280.dp)
            .height(420.dp)
            .onFocusChanged { focusState ->
                isFocused = focusState.isFocused
            },
        shape = RoundedCornerShape(8.dp),
        border = if (isFocused) {
            BorderStroke(4.dp, MaterialTheme.colorScheme.primary)
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
            // Poster image with progressive loading
            AsyncImage(
                model = movie.posterUrl,
                contentDescription = movie.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // Genre badges at bottom
            if (movie.genres.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    movie.genres.take(2).forEach { genre ->
                        GenreBadge(genreName = genre)
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
                if (isWatched) {
                    WatchedBadge()
                }
                if (isFavorite) {
                    FavoriteBadge()
                }
            }
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
    Card(
        modifier = modifier
            .width(280.dp)
            .height(420.dp),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF2C2C2C)
        )
    ) {
        // Shimmer effect would go here in full implementation
        Box(modifier = Modifier.fillMaxSize())
    }
}
