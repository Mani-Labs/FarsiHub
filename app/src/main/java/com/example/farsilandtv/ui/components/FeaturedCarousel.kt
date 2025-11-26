package com.example.farsilandtv.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.tv.material3.ExperimentalTvMaterial3Api
import coil.compose.AsyncImage
import com.example.farsilandtv.data.models.Movie
import com.example.farsilandtv.data.models.Series
import com.example.farsilandtv.data.models.FeaturedContent
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Feature #16: Compose Component - Featured Carousel
 * Hero banner at top of home screen
 *
 * Redesigned with elegant Netflix-style aesthetics:
 * - Poster thumbnail alongside info
 * - Smooth crossfade animations
 * - Multi-layer gradient overlay
 * - Content type badge (Movie/TV)
 * - Pill-style carousel indicators
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

fun FeaturedContent.toFeaturedItem(): FeaturedItem = when (this) {
    is FeaturedContent.FeaturedMovie -> FeaturedItem.MovieItem(movie)
    is FeaturedContent.FeaturedSeries -> FeaturedItem.SeriesItem(series)
}

fun List<FeaturedContent>.toFeaturedItems(): List<FeaturedItem> =
    map { it.toFeaturedItem() }

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun FeaturedCarousel(
    content: List<FeaturedItem>,
    onContentClick: (FeaturedItem) -> Unit,
    modifier: Modifier = Modifier,
    autoRotateDelayMs: Long = 8000L
) {
    if (content.isEmpty()) {
        FeaturedCarouselSkeleton()
        return
    }

    var currentIndex by remember { mutableStateOf(0) }
    var isPaused by remember { mutableStateOf(false) }
    var isFocused by remember { mutableStateOf(false) }

    // Focus requesters for buttons
    val playButtonFocusRequester = remember { FocusRequester() }
    val infoButtonFocusRequester = remember { FocusRequester() }

    // Bring into view when focused - ensures full carousel is visible when scrolling up
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val coroutineScope = rememberCoroutineScope()

    val lifecycleOwner = LocalLifecycleOwner.current

    // Auto-rotate carousel
    LaunchedEffect(content.size) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                if (!isPaused && content.isNotEmpty()) {
                    delay(autoRotateDelayMs)
                    currentIndex = (currentIndex + 1) % content.size
                } else {
                    delay(500)
                }
            }
        }
    }

    val currentContent = content[currentIndex]
    val isMovie = currentContent is FeaturedItem.MovieItem

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(420.dp)
            .bringIntoViewRequester(bringIntoViewRequester)
            .onFocusChanged { focusState ->
                isFocused = focusState.hasFocus
                // When carousel gains focus, scroll it fully into view
                if (focusState.hasFocus) {
                    coroutineScope.launch {
                        bringIntoViewRequester.bringIntoView()
                    }
                }
            }
    ) {
        // Background with crossfade animation
        AnimatedContent(
            targetState = currentContent,
            transitionSpec = {
                fadeIn(animationSpec = tween(600)) togetherWith
                fadeOut(animationSpec = tween(600))
            },
            label = "featured_backdrop"
        ) { item ->
            Box(modifier = Modifier.fillMaxSize()) {
                // Main backdrop image
                AsyncImage(
                    model = item.backdropUrl ?: item.posterUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                // Multi-layer gradient overlay for depth
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.9f),
                                    Color.Black.copy(alpha = 0.6f),
                                    Color.Transparent
                                ),
                                startX = 0f,
                                endX = 800f
                            )
                        )
                )

                // Bottom gradient for text area
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.8f)
                                ),
                                startY = 0f,
                                endY = 1200f
                            )
                        )
                )

                // Top vignette
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                        .align(Alignment.TopCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color(0xFF121212),
                                    Color.Transparent
                                )
                            )
                        )
                )
            }
        }

        // Content row: Poster + Info
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 48.dp, end = 48.dp, top = 60.dp, bottom = 32.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Poster thumbnail with shadow
            AnimatedContent(
                targetState = currentContent.posterUrl,
                transitionSpec = {
                    fadeIn(tween(400)) togetherWith fadeOut(tween(400))
                },
                label = "poster_transition"
            ) { posterUrl ->
                Box(
                    modifier = Modifier
                        .width(180.dp)
                        .height(270.dp)
                        .shadow(
                            elevation = 16.dp,
                            shape = RoundedCornerShape(12.dp),
                            ambientColor = Color.Black,
                            spotColor = Color.Black
                        )
                        .clip(RoundedCornerShape(12.dp))
                        .border(
                            width = if (isFocused) 3.dp else 0.dp,
                            color = if (isFocused) MaterialTheme.colorScheme.primary else Color.Transparent,
                            shape = RoundedCornerShape(12.dp)
                        )
                ) {
                    AsyncImage(
                        model = posterUrl,
                        contentDescription = currentContent.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            Spacer(modifier = Modifier.width(40.dp))

            // Info column
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 100.dp),
                verticalArrangement = Arrangement.Center
            ) {
                // Content type badge + Year + Rating row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Content type badge
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = if (isMovie) Color(0xFFE50914) else Color(0xFF5C6BC0)
                    ) {
                        Text(
                            text = if (isMovie) "MOVIE" else "TV SHOW",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            letterSpacing = 1.sp
                        )
                    }

                    // Year
                    currentContent.year?.let { year ->
                        Text(
                            text = year.toString(),
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }

                    // Rating with star
                    currentContent.rating?.let { rating ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "★",
                                color = Color(0xFFFFD700),
                                fontSize = 16.sp
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = String.format("%.1f", rating),
                                style = MaterialTheme.typography.bodyLarge,
                                color = Color.White.copy(alpha = 0.9f),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Title with animation
                AnimatedContent(
                    targetState = currentContent.title,
                    transitionSpec = {
                        slideInVertically { it / 2 } + fadeIn() togetherWith
                        slideOutVertically { -it / 2 } + fadeOut()
                    },
                    label = "title_transition"
                ) { title ->
                    Text(
                        text = title,
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 44.sp
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Genre chips
                if (currentContent.genres.isNotEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        currentContent.genres.take(3).forEach { genre ->
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = Color.White.copy(alpha = 0.15f),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    Color.White.copy(alpha = 0.3f)
                                )
                            ) {
                                Text(
                                    text = genre,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.White.copy(alpha = 0.9f)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Description
                if (currentContent.description.isNotBlank()) {
                    Text(
                        text = currentContent.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.75f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 22.sp
                    )

                    Spacer(modifier = Modifier.height(24.dp))
                }

                // Action buttons
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Play button (primary, filled)
                    var playBtnFocused by remember { mutableStateOf(false) }
                    Button(
                        onClick = { onContentClick(currentContent) },
                        modifier = Modifier
                            .height(52.dp)
                            .focusRequester(playButtonFocusRequester)
                            .onFocusChanged { playBtnFocused = it.isFocused }
                            .onPreviewKeyEvent { keyEvent ->
                                if (keyEvent.type == KeyEventType.KeyDown) {
                                    when (keyEvent.key) {
                                        Key.DirectionLeft -> {
                                            // Change to previous item
                                            isPaused = true
                                            currentIndex = if (currentIndex > 0) currentIndex - 1 else content.size - 1
                                            true
                                        }
                                        Key.DirectionRight -> {
                                            // Move focus to More Info button
                                            infoButtonFocusRequester.requestFocus()
                                            true
                                        }
                                        else -> false
                                    }
                                } else false
                            },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (playBtnFocused) Color.White else Color.White.copy(alpha = 0.95f),
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(8.dp),
                        elevation = ButtonDefaults.buttonElevation(
                            defaultElevation = 4.dp,
                            focusedElevation = 8.dp
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Play",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // More Info button (secondary, outlined)
                    var infoBtnFocused by remember { mutableStateOf(false) }
                    OutlinedButton(
                        onClick = { onContentClick(currentContent) },
                        modifier = Modifier
                            .height(52.dp)
                            .focusRequester(infoButtonFocusRequester)
                            .onFocusChanged { infoBtnFocused = it.isFocused }
                            .onPreviewKeyEvent { keyEvent ->
                                if (keyEvent.type == KeyEventType.KeyDown) {
                                    when (keyEvent.key) {
                                        Key.DirectionLeft -> {
                                            // Move focus back to Play button
                                            playButtonFocusRequester.requestFocus()
                                            true
                                        }
                                        Key.DirectionRight -> {
                                            // Change to next item
                                            isPaused = true
                                            currentIndex = (currentIndex + 1) % content.size
                                            true
                                        }
                                        else -> false
                                    }
                                } else false
                            },
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (infoBtnFocused) Color.White.copy(alpha = 0.3f) else Color.Black.copy(alpha = 0.5f),
                            contentColor = Color.White
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            width = if (infoBtnFocused) 2.dp else 1.dp,
                            color = if (infoBtnFocused) Color.White else Color.White.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Info,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "More Info",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        // Pill-style carousel indicators (bottom right)
        if (content.size > 1) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 48.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                content.forEachIndexed { index, _ ->
                    val isActive = index == currentIndex
                    val width by animateDpAsState(
                        targetValue = if (isActive) 24.dp else 8.dp,
                        animationSpec = tween(300),
                        label = "indicator_width"
                    )
                    val alpha by animateFloatAsState(
                        targetValue = if (isActive) 1f else 0.4f,
                        animationSpec = tween(300),
                        label = "indicator_alpha"
                    )

                    Box(
                        modifier = Modifier
                            .width(width)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(
                                if (isActive)
                                    MaterialTheme.colorScheme.primary
                                else
                                    Color.White.copy(alpha = alpha)
                            )
                    )
                }
            }
        }

        // Current position indicator (bottom left)
        if (content.size > 1) {
            Text(
                text = "${currentIndex + 1} / ${content.size}",
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 48.dp, bottom = 24.dp),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.5f),
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * Skeleton loading state
 */
@Composable
fun FeaturedCarouselSkeleton() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(420.dp)
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        Color(0xFF1E1E1E),
                        Color(0xFF2A2A2A),
                        Color(0xFF1E1E1E)
                    )
                )
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 48.dp, top = 60.dp, bottom = 32.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Poster skeleton
            Box(
                modifier = Modifier
                    .width(180.dp)
                    .height(270.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF333333))
            )

            Spacer(modifier = Modifier.width(40.dp))

            Column {
                // Badge skeleton
                Box(
                    modifier = Modifier
                        .width(70.dp)
                        .height(24.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFF333333))
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Title skeleton
                Box(
                    modifier = Modifier
                        .width(300.dp)
                        .height(40.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFF333333))
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Genre skeleton
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(3) {
                        Box(
                            modifier = Modifier
                                .width(80.dp)
                                .height(28.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0xFF333333))
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Description skeleton
                Box(
                    modifier = Modifier
                        .width(400.dp)
                        .height(44.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFF333333))
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Buttons skeleton
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Box(
                        modifier = Modifier
                            .width(120.dp)
                            .height(52.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF444444))
                    )
                    Box(
                        modifier = Modifier
                            .width(140.dp)
                            .height(52.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF333333))
                    )
                }
            }
        }
    }
}
