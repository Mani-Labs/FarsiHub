package com.example.farsilandtv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import com.example.farsilandtv.data.models.Episode
import com.example.farsilandtv.data.models.FeaturedContent
import com.example.farsilandtv.data.models.Movie
import com.example.farsilandtv.data.models.Series
import com.example.farsilandtv.utils.DeviceUtils
import com.example.farsilandtv.utils.LocalDeviceType

/**
 * Phase 3: Responsive Content Components
 * Touch-friendly card components for phone UI
 */

// =============================================================================
// PHONE-OPTIMIZED MOVIE CARD
// =============================================================================

/**
 * Phone-optimized movie card with touch support
 * Smaller, touch-friendly design with swipe gestures support
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun PhoneMovieCard(
    movie: Movie,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 120.dp,
    isFavorite: Boolean = false,
    isWatched: Boolean = false,
    progressPercent: Float? = null,
    onLongClick: (() -> Unit)? = null
) {
    Card(
        modifier = modifier
            .width(width)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box {
            // UC-L6 FIX: Validate URL before loading
            val isValidUrl = androidx.compose.runtime.remember(movie.posterUrl) {
                movie.posterUrl?.let { url ->
                    url.startsWith("http://") || url.startsWith("https://")
                } ?: false
            }

            // Poster with loading/error states
            if (isValidUrl) {
                SubcomposeAsyncImage(
                    model = movie.posterUrl,
                    contentDescription = movie.title,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(2f / 3f),
                    contentScale = ContentScale.Crop,
                    loading = {
                        ImagePlaceholder(
                            icon = Icons.Filled.PlayArrow,
                            modifier = Modifier.fillMaxSize()
                        )
                    },
                    error = {
                        ImageErrorPlaceholder(
                            icon = Icons.Filled.PlayArrow,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                )
            } else {
                ImageErrorPlaceholder(
                    icon = Icons.Filled.PlayArrow,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(2f / 3f)
                )
            }

            // Gradient overlay at bottom for title
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))
                        )
                    )
            )

            // Title at bottom
            Text(
                text = movie.title,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp),
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            // Status badges top-right
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                if (isWatched) WatchedBadge()
                if (isFavorite) FavoriteBadge()
            }

            // Progress bar for continue watching
            if (progressPercent != null && progressPercent > 0f) {
                LinearProgressIndicator(
                    progress = { progressPercent.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .align(Alignment.BottomCenter),
                    color = Color(0xFFE91E63),
                    trackColor = Color.Black.copy(alpha = 0.5f)
                )
            }

            // NEW badge if applicable
            if (movie.isNew) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(4.dp),
                    color = Color(0xFFE91E63),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = "NEW",
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

// =============================================================================
// PHONE-OPTIMIZED SERIES CARD
// =============================================================================

/**
 * Phone-optimized series card with touch support
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun PhoneSeriesCard(
    series: Series,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 120.dp,
    isFavorite: Boolean = false,
    hasUnwatchedEpisodes: Boolean = false,
    onLongClick: (() -> Unit)? = null
) {
    Card(
        modifier = modifier
            .width(width)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box {
            // UC-L6 FIX: Validate URL before loading
            val isValidUrl = androidx.compose.runtime.remember(series.posterUrl) {
                series.posterUrl?.let { url ->
                    url.startsWith("http://") || url.startsWith("https://")
                } ?: false
            }

            // Poster with loading/error states
            if (isValidUrl) {
                SubcomposeAsyncImage(
                    model = series.posterUrl,
                    contentDescription = series.title,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(2f / 3f),
                    contentScale = ContentScale.Crop,
                    loading = {
                        ImagePlaceholder(
                            icon = Icons.Filled.PlayArrow,
                            modifier = Modifier.fillMaxSize()
                        )
                    },
                    error = {
                        ImageErrorPlaceholder(
                            icon = Icons.Filled.PlayArrow,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                )
            } else {
                ImageErrorPlaceholder(
                    icon = Icons.Filled.PlayArrow,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(2f / 3f)
                )
            }

            // Gradient overlay
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(70.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                        )
                    )
            )

            // Title and episode count
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
            ) {
                Text(
                    text = series.title,
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (series.totalSeasons > 0 || series.totalEpisodes > 0) {
                    Text(
                        text = buildString {
                            if (series.totalSeasons > 0) append("${series.totalSeasons} Seasons")
                            if (series.totalSeasons > 0 && series.totalEpisodes > 0) append(" • ")
                            if (series.totalEpisodes > 0) append("${series.totalEpisodes} Ep")
                        },
                        color = Color(0xFFB0B0B0),
                        fontSize = 9.sp
                    )
                }
            }

            // Status badges
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                if (isFavorite) FavoriteBadge()
            }

            // NEW badge
            if (hasUnwatchedEpisodes || series.isNew) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(4.dp),
                    color = Color(0xFFFF5722),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = "NEW",
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

// =============================================================================
// PHONE-OPTIMIZED EPISODE CARD
// =============================================================================

/**
 * Phone-optimized episode card - horizontal layout for lists
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun PhoneEpisodeCard(
    episode: Episode,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(90.dp)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbnail
            Box(
                modifier = Modifier
                    .width(140.dp)
                    .fillMaxHeight()
            ) {
                // UC-L6 FIX: Validate URL before loading
                val thumbnailUrl = episode.thumbnailUrl ?: episode.episodePosterUrl
                val isValidUrl = androidx.compose.runtime.remember(thumbnailUrl) {
                    thumbnailUrl?.let { url ->
                        url.startsWith("http://") || url.startsWith("https://")
                    } ?: false
                }

                if (isValidUrl) {
                    SubcomposeAsyncImage(
                        model = thumbnailUrl,
                        contentDescription = episode.title,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp)),
                        contentScale = ContentScale.Crop,
                        loading = {
                            ImagePlaceholder(
                                icon = Icons.Filled.PlayArrow,
                                modifier = Modifier.fillMaxSize()
                            )
                        },
                        error = {
                            ImageErrorPlaceholder(
                                icon = Icons.Filled.PlayArrow,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    )
                } else {
                    ImageErrorPlaceholder(
                        icon = Icons.Filled.PlayArrow,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp))
                    )
                }

                // Episode number badge
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(4.dp),
                    color = Color.Black.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = episode.formattedNumber,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Progress bar
                if (episode.isInProgress) {
                    LinearProgressIndicator(
                        progress = { episode.progressPercentage / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .align(Alignment.BottomCenter),
                        color = Color(0xFFE91E63),
                        trackColor = Color.Black.copy(alpha = 0.5f)
                    )
                }
            }

            // Info
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(12.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = episode.title,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    episode.seriesTitle?.let {
                        Text(
                            text = it,
                            color = Color(0xFFB0B0B0),
                            fontSize = 12.sp,
                            maxLines = 1
                        )
                    }
                    episode.runtime?.let {
                        Text(
                            text = "${it}min",
                            color = Color(0xFF808080),
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

// =============================================================================
// PHONE-OPTIMIZED FEATURED CARD
// =============================================================================

/**
 * Phone-optimized featured content card for carousel
 */
@Composable
fun PhoneFeaturedCard(
    content: FeaturedContent,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // UC-L6 FIX: Validate URL before loading
            val imageUrl = content.backdropUrl ?: content.posterUrl
            val isValidUrl = androidx.compose.runtime.remember(imageUrl) {
                imageUrl?.let { url ->
                    url.startsWith("http://") || url.startsWith("https://")
                } ?: false
            }

            // Background image with loading/error states
            if (isValidUrl) {
                SubcomposeAsyncImage(
                    model = imageUrl,
                    contentDescription = content.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    loading = {
                        ImagePlaceholder(
                            icon = Icons.Filled.PlayArrow,
                            modifier = Modifier.fillMaxSize()
                        )
                    },
                    error = {
                        ImageErrorPlaceholder(
                            icon = Icons.Filled.PlayArrow,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                )
            } else {
                ImageErrorPlaceholder(
                    icon = Icons.Filled.PlayArrow,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Gradient overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.7f),
                                Color.Black.copy(alpha = 0.9f)
                            ),
                            startY = 50f
                        )
                    )
            )

            // Content type badge
            Surface(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp),
                color = Color(0xFFE91E63),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    text = content.contentType.uppercase(),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Title and description
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp)
            ) {
                Text(
                    text = content.title,
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = content.description,
                    color = Color(0xFFCCCCCC),
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// =============================================================================
// RESPONSIVE WRAPPER - Chooses TV or Phone component based on device
// =============================================================================

/**
 * Responsive Movie Card - automatically selects TV or Phone variant
 */
@Composable
fun ResponsiveMovieCard(
    movie: Movie,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isFavorite: Boolean = false,
    isWatched: Boolean = false,
    progressPercent: Float? = null,
    onLongClick: (() -> Unit)? = null
) {
    val deviceType = LocalDeviceType.current

    when (deviceType) {
        DeviceUtils.DeviceType.TV,
        DeviceUtils.DeviceType.TABLET -> {
            // Use existing TV-optimized card
            MovieCard(
                movie = movie,
                onClick = onClick,
                modifier = modifier,
                isFavorite = isFavorite,
                isWatched = isWatched,
                progressPercent = progressPercent,
                onLongClick = onLongClick
            )
        }
        DeviceUtils.DeviceType.PHONE -> {
            // Use phone-optimized card
            PhoneMovieCard(
                movie = movie,
                onClick = onClick,
                modifier = modifier,
                isFavorite = isFavorite,
                isWatched = isWatched,
                progressPercent = progressPercent,
                onLongClick = onLongClick
            )
        }
    }
}

/**
 * Responsive Series Card - automatically selects TV or Phone variant
 */
@Composable
fun ResponsiveSeriesCard(
    series: Series,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isFavorite: Boolean = false,
    hasUnwatchedEpisodes: Boolean = false,
    onLongClick: (() -> Unit)? = null
) {
    val deviceType = LocalDeviceType.current

    when (deviceType) {
        DeviceUtils.DeviceType.TV,
        DeviceUtils.DeviceType.TABLET -> {
            // Use existing TV-optimized card
            SeriesCard(
                series = series,
                onClick = onClick,
                modifier = modifier,
                isFavorite = isFavorite,
                hasUnwatchedEpisodes = hasUnwatchedEpisodes,
                onLongClick = onLongClick
            )
        }
        DeviceUtils.DeviceType.PHONE -> {
            // Use phone-optimized card
            PhoneSeriesCard(
                series = series,
                onClick = onClick,
                modifier = modifier,
                isFavorite = isFavorite,
                hasUnwatchedEpisodes = hasUnwatchedEpisodes,
                onLongClick = onLongClick
            )
        }
    }
}

// =============================================================================
// SKELETON LOADERS FOR PHONE
// =============================================================================

@Composable
fun PhoneMovieCardSkeleton(
    modifier: Modifier = Modifier,
    width: Dp = 120.dp
) {
    Card(
        modifier = modifier.width(width),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .background(shimmerBrush())
        )
    }
}

@Composable
fun PhoneSeriesCardSkeleton(
    modifier: Modifier = Modifier,
    width: Dp = 120.dp
) {
    Card(
        modifier = modifier.width(width),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .background(shimmerBrush())
        )
    }
}

@Composable
fun PhoneEpisodeCardSkeleton(
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(90.dp),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .width(140.dp)
                    .fillMaxHeight()
                    .background(shimmerBrush())
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(12.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(150.dp)
                        .height(16.dp)
                        .background(shimmerBrush(), RoundedCornerShape(4.dp))
                )
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .width(100.dp)
                        .height(12.dp)
                        .background(shimmerBrush(), RoundedCornerShape(4.dp))
                )
            }
        }
    }
}

@Composable
fun PhoneFeaturedCardSkeleton(
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(shimmerBrush())
        )
    }
}

// =============================================================================
// IMAGE PLACEHOLDER COMPONENTS
// =============================================================================

/**
 * Loading placeholder for images - shows shimmer effect with icon
 */
@Composable
fun ImagePlaceholder(
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(shimmerBrush()),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color(0xFF4A4A4A),
            modifier = Modifier.size(32.dp)
        )
    }
}

/**
 * Error placeholder for images that failed to load
 */
@Composable
fun ImageErrorPlaceholder(
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(Color(0xFF2A2A2A)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color(0xFF5A5A5A),
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = "Image failed to load",
                tint = Color(0xFF5A5A5A),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
