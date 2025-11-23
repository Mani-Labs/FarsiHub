package com.example.farsilandtv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import coil.compose.AsyncImage
import com.example.farsilandtv.data.models.Movie
import com.example.farsilandtv.data.models.Series
import com.example.farsilandtv.data.models.FeaturedContent
import kotlinx.coroutines.delay

/**
 * Feature #16: Compose Component - Featured Carousel
 * Hero banner at top of home screen (Feature #4)
 *
 * Features:
 * - Auto-rotating carousel
 * - Play button with focus
 * - Gradient overlay for text readability
 * - Genre badges
 *
 * Supports both Movie and Series content
 */
sealed class FeaturedItem {
    abstract val id: Int
    abstract val title: String
    abstract val backdropUrl: String?
    abstract val posterUrl: String?
    abstract val year: Int?
    abstract val rating: Float?
    abstract val genres: List<String>
    abstract val description: String

    data class MovieItem(val movie: Movie) : FeaturedItem() {
        override val id = movie.id
        override val title = movie.title
        override val backdropUrl = movie.backdropUrl
        override val posterUrl = movie.posterUrl
        override val year = movie.year
        override val rating = movie.rating
        override val genres = movie.genres
        override val description = movie.description
    }

    data class SeriesItem(val series: Series) : FeaturedItem() {
        override val id = series.id
        override val title = series.title
        override val backdropUrl = series.backdropUrl
        override val posterUrl = series.posterUrl
        override val year = series.year
        override val rating = series.rating
        override val genres = series.genres
        override val description = series.description
    }
}

/**
 * Extension functions to convert FeaturedContent (data model) to FeaturedItem (UI model)
 * Required for HomeScreen integration
 */
fun FeaturedContent.toFeaturedItem(): FeaturedItem = when (this) {
    is FeaturedContent.FeaturedMovie -> FeaturedItem.MovieItem(movie)
    is FeaturedContent.FeaturedSeries -> FeaturedItem.SeriesItem(series)
}

fun List<FeaturedContent>.toFeaturedItems(): List<FeaturedItem> =
    map { it.toFeaturedItem() }

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun FeaturedCarousel(
    content: List<FeaturedItem>,
    onContentClick: (FeaturedItem) -> Unit,
    modifier: Modifier = Modifier,
    autoRotateDelayMs: Long = 5000L
) {
    if (content.isEmpty()) {
        FeaturedCarouselSkeleton()
        return
    }

    var currentIndex by remember { mutableStateOf(0) }

    // Auto-rotate carousel
    LaunchedEffect(currentIndex) {
        delay(autoRotateDelayMs)
        currentIndex = (currentIndex + 1) % content.size
    }

    val currentContent = content[currentIndex]

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(480.dp)
    ) {
        // Backdrop image
        AsyncImage(
            model = currentContent.backdropUrl ?: currentContent.posterUrl,
            contentDescription = currentContent.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // Gradient overlay for text readability
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.7f)
                        ),
                        startY = 200f
                    )
                )
        )

        // Content info (bottom section)
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(48.dp)
                .fillMaxWidth(0.6f)
        ) {
            // Title
            Text(
                text = currentContent.title,
                style = MaterialTheme.typography.displayMedium,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Metadata (year, rating)
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                currentContent.year?.let { year ->
                    Text(
                        text = year.toString(),
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }
                currentContent.rating?.let { rating ->
                    Text(
                        text = "⭐ $rating",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Genre badges
            if (currentContent.genres.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    currentContent.genres.take(3).forEach { genre ->
                        GenreBadge(genreName = genre)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Description
            if (currentContent.description.isNotBlank()) {
                Text(
                    text = currentContent.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.9f),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(20.dp))
            }

            // Play button
            Button(
                onClick = { onContentClick(currentContent) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier.height(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = "Play",
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Watch Now",
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }

        // Carousel indicators (dots)
        if (content.size > 1) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(48.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (index in content.indices) {
                    Box(
                        modifier = Modifier
                            .size(if (index == currentIndex) 12.dp else 8.dp)
                            .clip(RoundedCornerShape(50))
                            .background(
                                if (index == currentIndex)
                                    MaterialTheme.colorScheme.primary
                                else
                                    Color.White.copy(alpha = 0.4f)
                            )
                    )
                }
            }
        }
    }
}

/**
 * Skeleton for loading state
 */
@Composable
fun FeaturedCarouselSkeleton() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(480.dp)
            .background(Color(0xFF2C2C2C))
    )
}
