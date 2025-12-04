package com.example.farsilandtv.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.farsilandtv.data.download.DownloadConstants
import com.example.farsilandtv.data.download.DownloadManager
import com.example.farsilandtv.data.models.Movie
import com.example.farsilandtv.data.scraper.ScraperResult
import com.example.farsilandtv.data.scraper.VideoUrlScraper
import com.example.farsilandtv.data.repository.FavoritesRepository
import com.example.farsilandtv.data.repository.PlaybackRepository
import com.example.farsilandtv.data.repository.WatchlistRepository
import com.example.farsilandtv.ui.components.MovieCard
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Movie Details Screen - Redesigned with elegant Netflix-style layout
 *
 * Design principles:
 * - Blurred backdrop as ambient background (no cropping issues)
 * - Poster + info side-by-side layout
 * - All essential info visible without scrolling
 * - Clear focus states for D-pad navigation
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MovieDetailsScreen(
    movie: Movie,
    favoritesRepo: FavoritesRepository,
    playbackRepo: PlaybackRepository,
    watchlistRepo: WatchlistRepository,
    downloadManager: DownloadManager,
    onBackClick: () -> Unit,
    onPlayClick: (Movie) -> Unit,
    onMovieClick: (Movie) -> Unit = {},
    similarMovies: List<Movie> = emptyList(),
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // State
    var isFavorite by remember { mutableStateOf(false) }
    var isWatched by remember { mutableStateOf(false) }
    var isInWatchlist by remember { mutableStateOf(false) }
    var isDownloaded by remember { mutableStateOf(false) }
    var isDownloading by remember { mutableStateOf(false) }

    // Load initial states
    LaunchedEffect(movie.id) {
        isFavorite = favoritesRepo.isMovieFavorited(movie.id).first()
        isWatched = playbackRepo.isCompleted(movie.id, "movie").first() ?: false
        isInWatchlist = watchlistRepo.isMovieInWatchlist(movie.id)
        isDownloaded = downloadManager.isDownloaded(DownloadConstants.movieId(movie.id))
    }

    // Focus requesters for buttons
    val playButtonFocus = remember { FocusRequester() }
    val favoriteButtonFocus = remember { FocusRequester() }
    val watchlistButtonFocus = remember { FocusRequester() }
    val watchedButtonFocus = remember { FocusRequester() }
    val downloadButtonFocus = remember { FocusRequester() }
    val shareButtonFocus = remember { FocusRequester() }

    // Request focus on Play button when screen loads
    LaunchedEffect(Unit) {
        playButtonFocus.requestFocus()
    }

    Box(modifier = modifier.fillMaxSize()) {
        // Blurred backdrop as ambient background
        AsyncImage(
            model = movie.backdropUrl ?: movie.posterUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .blur(25.dp)
        )

        // Dark overlay for readability
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.75f))
        )

        // Main content
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 48.dp)
        ) {
            // Hero section - Poster + Info
            item(key = "hero") {
                HeroSection(
                    movie = movie,
                    isFavorite = isFavorite,
                    isWatched = isWatched,
                    isInWatchlist = isInWatchlist,
                    onBackClick = onBackClick,
                    onPlayClick = { onPlayClick(movie) },
                    onFavoriteClick = {
                        scope.launch {
                            if (isFavorite) {
                                favoritesRepo.removeMovieFromFavorites(movie.id)
                                Toast.makeText(context, "Removed from favorites", Toast.LENGTH_SHORT).show()
                            } else {
                                favoritesRepo.addMovieToFavorites(movie)
                                Toast.makeText(context, "Added to favorites", Toast.LENGTH_SHORT).show()
                            }
                            isFavorite = !isFavorite
                        }
                    },
                    onWatchlistClick = {
                        scope.launch {
                            if (isInWatchlist) {
                                watchlistRepo.removeMovieFromWatchlist(movie.id)
                                Toast.makeText(context, "Removed from watchlist", Toast.LENGTH_SHORT).show()
                            } else {
                                watchlistRepo.addMovieToWatchlist(movie)
                                Toast.makeText(context, "Added to watchlist", Toast.LENGTH_SHORT).show()
                            }
                            isInWatchlist = !isInWatchlist
                        }
                    },
                    onWatchedClick = {
                        scope.launch {
                            if (isWatched) {
                                playbackRepo.markAsIncomplete(movie.id, "movie")
                                Toast.makeText(context, "Marked as unwatched", Toast.LENGTH_SHORT).show()
                            } else {
                                playbackRepo.markAsCompleted(movie.id, "movie")
                                Toast.makeText(context, "Marked as watched", Toast.LENGTH_SHORT).show()
                            }
                            isWatched = !isWatched
                        }
                    },
                    isDownloaded = isDownloaded,
                    isDownloading = isDownloading,
                    onDownloadClick = {
                        scope.launch {
                            if (isDownloaded) {
                                downloadManager.deleteDownload(DownloadConstants.movieId(movie.id))
                                Toast.makeText(context, "Download removed", Toast.LENGTH_SHORT).show()
                                isDownloaded = false
                            } else if (!isDownloading) {
                                // Get the page URL to scrape video from
                                val pageUrl = movie.farsilandUrl
                                if (pageUrl.isBlank()) {
                                    Toast.makeText(context, "No source URL available", Toast.LENGTH_SHORT).show()
                                    return@launch
                                }

                                isDownloading = true
                                Toast.makeText(context, "Finding video URL...", Toast.LENGTH_SHORT).show()

                                // Scrape video URL
                                when (val result = VideoUrlScraper.extractVideoUrls(pageUrl)) {
                                    is ScraperResult.Success -> {
                                        val videoUrls = result.data
                                        if (videoUrls.isNotEmpty()) {
                                            // Get best quality URL (first one is highest quality)
                                            val bestUrl = videoUrls.first()
                                            Toast.makeText(context, "Starting download (${bestUrl.quality})...", Toast.LENGTH_SHORT).show()

                                            // Queue download
                                            val queued = downloadManager.queueMovieDownload(
                                                movieId = movie.id,
                                                title = movie.title,
                                                posterUrl = movie.posterUrl,
                                                videoUrl = bestUrl.url
                                            )

                                            if (queued) {
                                                Toast.makeText(context, "Download started!", Toast.LENGTH_SHORT).show()
                                            } else {
                                                Toast.makeText(context, "Failed to start download", Toast.LENGTH_SHORT).show()
                                            }
                                        } else {
                                            Toast.makeText(context, "No video URLs found", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                    is ScraperResult.NetworkError -> {
                                        Toast.makeText(context, "Network error: ${result.message}", Toast.LENGTH_LONG).show()
                                    }
                                    is ScraperResult.ParseError -> {
                                        Toast.makeText(context, "Parse error: ${result.message}", Toast.LENGTH_LONG).show()
                                    }
                                    is ScraperResult.NoDataFound -> {
                                        Toast.makeText(context, "No video found: ${result.message}", Toast.LENGTH_LONG).show()
                                    }
                                }
                                isDownloading = false
                            }
                        }
                    },
                    playButtonFocus = playButtonFocus,
                    favoriteButtonFocus = favoriteButtonFocus,
                    watchlistButtonFocus = watchlistButtonFocus,
                    watchedButtonFocus = watchedButtonFocus,
                    downloadButtonFocus = downloadButtonFocus,
                    shareButtonFocus = shareButtonFocus,
                    context = context
                )
            }

            // Synopsis section
            if (movie.description.isNotBlank()) {
                item(key = "synopsis") {
                    SynopsisSection(
                        synopsis = movie.description,
                        modifier = Modifier.padding(horizontal = 48.dp, vertical = 24.dp)
                    )
                }
            }

            // Cast & Crew section
            if (movie.director != null || movie.cast.isNotEmpty()) {
                item(key = "credits") {
                    CreditsSection(
                        director = movie.director,
                        cast = movie.cast,
                        modifier = Modifier.padding(horizontal = 48.dp, vertical = 16.dp)
                    )
                }
            }

            // Similar movies
            if (similarMovies.isNotEmpty()) {
                item(key = "similar") {
                    SimilarContentSection(
                        title = "Similar Movies",
                        movies = similarMovies,
                        onMovieClick = onMovieClick,
                        modifier = Modifier.padding(vertical = 24.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroSection(
    movie: Movie,
    isFavorite: Boolean,
    isWatched: Boolean,
    isInWatchlist: Boolean,
    isDownloaded: Boolean,
    isDownloading: Boolean,
    onBackClick: () -> Unit,
    onPlayClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    onWatchlistClick: () -> Unit,
    onWatchedClick: () -> Unit,
    onDownloadClick: () -> Unit,
    playButtonFocus: FocusRequester,
    favoriteButtonFocus: FocusRequester,
    watchlistButtonFocus: FocusRequester,
    watchedButtonFocus: FocusRequester,
    downloadButtonFocus: FocusRequester,
    shareButtonFocus: FocusRequester,
    context: android.content.Context,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(400.dp)
    ) {
        // Top gradient for back button visibility
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.6f),
                            Color.Transparent
                        )
                    )
                )
        )

        // Back button
        IconButton(
            onClick = onBackClick,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.ArrowBack,
                contentDescription = "Back",
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
        }

        // Content row: Poster + Info
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 48.dp, end = 48.dp, top = 48.dp, bottom = 24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Poster with shadow
            Box(
                modifier = Modifier
                    .width(200.dp)
                    .height(300.dp)
                    .shadow(
                        elevation = 24.dp,
                        shape = RoundedCornerShape(12.dp),
                        ambientColor = Color.Black,
                        spotColor = Color.Black
                    )
                    .clip(RoundedCornerShape(12.dp))
            ) {
                AsyncImage(
                    model = movie.posterUrl,
                    contentDescription = movie.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(modifier = Modifier.width(40.dp))

            // Info column
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 48.dp),
                verticalArrangement = Arrangement.Center
            ) {
                // Content type badge + metadata row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // MOVIE badge
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color(0xFFE50914)
                    ) {
                        Text(
                            text = "MOVIE",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            letterSpacing = 1.sp
                        )
                    }

                    // Year
                    movie.year?.let { year ->
                        Text(
                            text = year.toString(),
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }

                    // Rating
                    movie.rating?.let { rating ->
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

                    // Duration
                    movie.runtime?.let { runtime ->
                        Text(
                            text = formatDuration(runtime),
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Title
                Text(
                    text = movie.title,
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 44.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Genre chips
                if (movie.genres.isNotEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        movie.genres.take(4).forEach { genre ->
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = Color.White.copy(alpha = 0.15f),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f))
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

                // Short description preview
                if (movie.description.isNotBlank()) {
                    Text(
                        text = movie.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.7f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 22.sp
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Action buttons (scrollable for smaller screens)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Play button
                    DetailActionButton(
                        icon = Icons.Filled.PlayArrow,
                        label = "Play",
                        isPrimary = true,
                        isActive = false,
                        onClick = onPlayClick,
                        focusRequester = playButtonFocus,
                        onLeftPress = { /* Already leftmost */ },
                        onRightPress = { favoriteButtonFocus.requestFocus() }
                    )

                    // Favorite button
                    DetailActionButton(
                        icon = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        label = if (isFavorite) "Favorited" else "Favorite",
                        isPrimary = false,
                        isActive = isFavorite,
                        onClick = onFavoriteClick,
                        focusRequester = favoriteButtonFocus,
                        onLeftPress = { playButtonFocus.requestFocus() },
                        onRightPress = { watchlistButtonFocus.requestFocus() }
                    )

                    // Watchlist button
                    DetailActionButton(
                        icon = if (isInWatchlist) Icons.Filled.Check else Icons.Filled.Add,
                        label = if (isInWatchlist) "In List" else "Watchlist",
                        isPrimary = false,
                        isActive = isInWatchlist,
                        onClick = onWatchlistClick,
                        focusRequester = watchlistButtonFocus,
                        onLeftPress = { favoriteButtonFocus.requestFocus() },
                        onRightPress = { watchedButtonFocus.requestFocus() }
                    )

                    // Watched button
                    DetailActionButton(
                        icon = if (isWatched) Icons.Filled.CheckCircle else Icons.Default.Done,
                        label = if (isWatched) "Watched" else "Mark Watched",
                        isPrimary = false,
                        isActive = isWatched,
                        onClick = onWatchedClick,
                        focusRequester = watchedButtonFocus,
                        onLeftPress = { watchlistButtonFocus.requestFocus() },
                        onRightPress = { downloadButtonFocus.requestFocus() }
                    )

                    // Download button
                    DetailActionButton(
                        icon = when {
                            isDownloaded -> Icons.Filled.CheckCircle
                            isDownloading -> Icons.Filled.Refresh
                            else -> Icons.Default.KeyboardArrowDown
                        },
                        label = when {
                            isDownloaded -> "Downloaded"
                            isDownloading -> "Downloading..."
                            else -> "Download"
                        },
                        isPrimary = false,
                        isActive = isDownloaded || isDownloading,
                        onClick = onDownloadClick,
                        focusRequester = downloadButtonFocus,
                        onLeftPress = { watchedButtonFocus.requestFocus() },
                        onRightPress = { shareButtonFocus.requestFocus() }
                    )

                    // Share button
                    DetailActionButton(
                        icon = Icons.Default.Share,
                        label = "Share",
                        isPrimary = false,
                        isActive = false,
                        onClick = {
                            val shareText = buildString {
                                append("Check out \"${movie.title}\"")
                                movie.year?.let { append(" ($it)") }
                                movie.rating?.let { append(" ⭐ ${String.format("%.1f", it)}") }
                                append("\n\n")
                                if (movie.description.isNotBlank()) {
                                    val shortDesc = if (movie.description.length > 150) {
                                        movie.description.take(150) + "..."
                                    } else {
                                        movie.description
                                    }
                                    append(shortDesc)
                                    append("\n\n")
                                }
                                append("Watch on FarsiPlex!")
                            }
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, movie.title)
                                putExtra(Intent.EXTRA_TEXT, shareText)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share movie"))
                        },
                        focusRequester = shareButtonFocus,
                        onLeftPress = { downloadButtonFocus.requestFocus() },
                        onRightPress = { /* Already rightmost */ }
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailActionButton(
    icon: ImageVector,
    label: String,
    isPrimary: Boolean,
    isActive: Boolean,
    onClick: () -> Unit,
    focusRequester: FocusRequester,
    onLeftPress: () -> Unit,
    onRightPress: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }

    if (isPrimary) {
        Button(
            onClick = onClick,
            modifier = modifier
                .height(48.dp)
                .focusRequester(focusRequester)
                .onFocusChanged { isFocused = it.isFocused }
                .onPreviewKeyEvent { keyEvent ->
                    if (keyEvent.type == KeyEventType.KeyDown) {
                        when (keyEvent.key) {
                            Key.DirectionLeft -> { onLeftPress(); true }
                            Key.DirectionRight -> { onRightPress(); true }
                            else -> false
                        }
                    } else false
                },
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isFocused) Color.White else Color.White.copy(alpha = 0.95f),
                contentColor = Color.Black
            ),
            shape = RoundedCornerShape(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = modifier
                .height(48.dp)
                .focusRequester(focusRequester)
                .onFocusChanged { isFocused = it.isFocused }
                .onPreviewKeyEvent { keyEvent ->
                    if (keyEvent.type == KeyEventType.KeyDown) {
                        when (keyEvent.key) {
                            Key.DirectionLeft -> { onLeftPress(); true }
                            Key.DirectionRight -> { onRightPress(); true }
                            else -> false
                        }
                    } else false
                },
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = when {
                    isFocused -> Color.White.copy(alpha = 0.3f)
                    isActive -> Color.White.copy(alpha = 0.15f)
                    else -> Color.Black.copy(alpha = 0.5f)
                },
                contentColor = Color.White
            ),
            border = BorderStroke(
                width = if (isFocused) 2.dp else 1.dp,
                color = when {
                    isFocused -> Color.White
                    isActive -> MaterialTheme.colorScheme.primary
                    else -> Color.White.copy(alpha = 0.5f)
                }
            ),
            shape = RoundedCornerShape(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = if (isActive && !isFocused) MaterialTheme.colorScheme.primary else Color.White
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (isActive) FontWeight.Medium else FontWeight.Normal
            )
        }
    }
}

@Composable
private fun SynopsisSection(
    synopsis: String,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        Text(
            text = "Synopsis",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        Text(
            text = synopsis,
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White.copy(alpha = 0.85f),
            maxLines = if (expanded) Int.MAX_VALUE else 4,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 26.sp
        )

        if (synopsis.length > 300) {
            TextButton(
                onClick = { expanded = !expanded },
                modifier = Modifier.padding(top = 4.dp)
            ) {
                Text(
                    text = if (expanded) "Show less" else "Show more",
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun CreditsSection(
    director: String?,
    cast: List<String>,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = "Cast & Crew",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        director?.let {
            Row(modifier = Modifier.padding(bottom = 8.dp)) {
                Text(
                    text = "Director: ",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.6f)
                )
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.9f),
                    fontWeight = FontWeight.Medium
                )
            }
        }

        if (cast.isNotEmpty()) {
            Row {
                Text(
                    text = "Cast: ",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.6f)
                )
                Text(
                    text = cast.take(6).joinToString(", "),
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.9f)
                )
            }
        }
    }
}

@Composable
private fun SimilarContentSection(
    title: String,
    movies: List<Movie>,
    onMovieClick: (Movie) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(start = 48.dp, bottom = 16.dp)
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(movies, key = { it.id }) { movie ->
                MovieCard(
                    movie = movie,
                    onClick = { onMovieClick(movie) },
                    isFavorite = false,
                    isWatched = false
                )
            }
        }
    }
}

private fun formatDuration(minutes: Int): String {
    val hours = minutes / 60
    val mins = minutes % 60
    return if (hours > 0) "${hours}h ${mins}m" else "${mins}m"
}
