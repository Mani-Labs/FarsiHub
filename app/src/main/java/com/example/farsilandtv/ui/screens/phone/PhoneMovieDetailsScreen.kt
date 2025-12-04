package com.example.farsilandtv.ui.screens.phone

import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.farsilandtv.data.download.DownloadConstants
import com.example.farsilandtv.data.download.DownloadManager
import com.example.farsilandtv.data.download.DownloadStatus
import com.example.farsilandtv.data.models.Movie
import com.example.farsilandtv.data.repository.FavoritesRepository
import com.example.farsilandtv.data.repository.PlaybackRepository
import com.example.farsilandtv.data.repository.WatchlistRepository
import com.example.farsilandtv.data.scraper.VideoUrlScraper
import com.example.farsilandtv.data.scraper.getOrNull
import com.example.farsilandtv.data.models.VideoUrl
import com.example.farsilandtv.ui.components.PhoneMovieCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Phone Movie Details Screen - Vertical layout optimized for touch
 *
 * Phase 5: Phone-optimized details with:
 * - Vertical poster-over-info layout
 * - Large touch-friendly buttons
 * - Swipe-friendly scrolling
 * - No D-pad focus management
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhoneMovieDetailsScreen(
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
    var isPaused by remember { mutableStateOf(false) }
    var showFullDescription by remember { mutableStateOf(false) }
    var isScrapingUrl by remember { mutableStateOf(false) }
    var scrapeError by remember { mutableStateOf<String?>(null) }
    var lastScrapeAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    // Download progress
    val downloadId = DownloadConstants.movieId(movie.id)
    val allProgress by downloadManager.downloadProgress.collectAsState()
    val downloadProgress = allProgress[downloadId] ?: 0

    // Load initial states
    LaunchedEffect(movie.id) {
        try {
            isFavorite = favoritesRepo.isMovieFavorited(movie.id).first()
            isWatched = playbackRepo.isCompleted(movie.id, "movie").first() ?: false
            // Move to IO thread to avoid blocking Main
            isInWatchlist = withContext(Dispatchers.IO) {
                watchlistRepo.isMovieInWatchlist(movie.id)
            }
            isDownloaded = downloadManager.isDownloaded(downloadId)
            // Check download status
            val download = downloadManager.getDownload(downloadId)
            isPaused = download?.status == DownloadStatus.PAUSED
            if (download?.status == DownloadStatus.DOWNLOADING) {
                isDownloading = true
            }
        } catch (e: Exception) {
            Log.e("PhoneMovieDetails", "Failed to load initial state: ${e.message}", e)
        }
    }

    // Update download state when progress changes
    LaunchedEffect(downloadProgress) {
        if (downloadProgress > 0 && downloadProgress < 100) {
            isDownloading = true
            isPaused = false
        } else if (downloadProgress >= 100) {
            isDownloading = false
            isPaused = false
            isDownloaded = true
        }
    }

    // Handle system back button
    BackHandler { onBackClick() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    // Share button
                    IconButton(onClick = {
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
                            // Note: farsilandUrl is internal scraping URL, not for sharing
                        }
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, movie.title)
                            putExtra(Intent.EXTRA_TEXT, shareText)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Share movie"))
                    }) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color(0xFF121212)
                )
            )
        },
        containerColor = Color(0xFF121212)
    ) { paddingValues ->
        Box(modifier = modifier.fillMaxSize()) {
            // Blurred backdrop
            AsyncImage(
                model = movie.backdropUrl ?: movie.posterUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(350.dp)
                    .blur(15.dp)
            )

            // Gradient overlay
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(400.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color(0xFF121212).copy(alpha = 0.8f),
                                Color(0xFF121212)
                            ),
                            startY = 100f
                        )
                    )
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                // Hero section with poster and info
                item(key = "hero") {
                    PhoneHeroSection(
                        movie = movie,
                        isFavorite = isFavorite,
                        isWatched = isWatched,
                        isInWatchlist = isInWatchlist,
                        isDownloaded = isDownloaded,
                        isDownloading = isDownloading,
                        isPaused = isPaused,
                        downloadProgress = downloadProgress,
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
                        onDownloadClick = {
                            Log.d("PhoneMovieDetails", "Download button clicked for movie: ${movie.title}")
                            scope.launch {
                                if (isDownloaded) {
                                    // Remove download
                                    Log.d("PhoneMovieDetails", "Removing download")
                                    downloadManager.deleteDownload(DownloadConstants.movieId(movie.id))
                                    Toast.makeText(context, "Download removed", Toast.LENGTH_SHORT).show()
                                    isDownloaded = false
                                } else if (!isDownloading && !isScrapingUrl) {
                                    // Start download - first scrape video URL
                                    val farsilandUrl = movie.farsilandUrl
                                    if (farsilandUrl.isNullOrBlank()) {
                                        Toast.makeText(context, "Video source not available", Toast.LENGTH_SHORT).show()
                                        return@launch
                                    }

                                    // Define the scrape action for retry
                                    suspend fun doScrapeAndDownload() {
                                        Log.d("PhoneMovieDetails", "Starting download, scraping URL from: $farsilandUrl")
                                        isScrapingUrl = true
                                        scrapeError = null

                                        try {
                                            val result = withContext(Dispatchers.IO) {
                                                VideoUrlScraper.extractVideoUrls(farsilandUrl)
                                            }

                                            val videoUrls: List<VideoUrl> = result.getOrNull() ?: emptyList()
                                            Log.d("PhoneMovieDetails", "Scraped ${videoUrls.size} video URLs")
                                            val bestUrl: VideoUrl? = videoUrls.maxByOrNull { videoUrl: VideoUrl ->
                                                val quality = videoUrl.quality.lowercase()
                                                when {
                                                    quality.contains("1080") -> 4
                                                    quality.contains("720") -> 3
                                                    quality.contains("480") -> 2
                                                    quality.contains("360") -> 1
                                                    else -> 0
                                                }
                                            }

                                            if (bestUrl != null) {
                                                Toast.makeText(context, "Starting download (${bestUrl.quality})...", Toast.LENGTH_SHORT).show()
                                                val queued = downloadManager.queueMovieDownload(
                                                    movieId = movie.id,
                                                    title = movie.title,
                                                    posterUrl = movie.posterUrl,
                                                    videoUrl = bestUrl.url
                                                )
                                                if (queued) {
                                                    Toast.makeText(context, "Download started!", Toast.LENGTH_SHORT).show()
                                                    lastScrapeAction = null
                                                } else {
                                                    scrapeError = "Failed to start download"
                                                }
                                            } else {
                                                scrapeError = "No video URL found"
                                            }
                                        } catch (e: Exception) {
                                            Log.e("PhoneMovieDetails", "Failed to scrape video URL: ${e.message}", e)
                                            scrapeError = e.message ?: "Failed to find video"
                                        } finally {
                                            isScrapingUrl = false
                                        }
                                    }

                                    // Store action for retry
                                    lastScrapeAction = { scope.launch { doScrapeAndDownload() } }
                                    doScrapeAndDownload()
                                }
                            }
                        },
                        onCancelDownload = {
                            Log.d("PhoneMovieDetails", "Cancel download clicked for movie: ${movie.title}")
                            scope.launch {
                                try {
                                    downloadManager.cancelDownload(downloadId)
                                    isDownloading = false
                                    isPaused = false
                                    Toast.makeText(context, "Download cancelled", Toast.LENGTH_SHORT).show()
                                } catch (e: Exception) {
                                    Log.e("PhoneMovieDetails", "Failed to cancel download: ${e.message}", e)
                                    Toast.makeText(context, "Failed to cancel download", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        onPauseResume = {
                            Log.d("PhoneMovieDetails", "Pause/Resume clicked, isPaused: $isPaused")
                            scope.launch {
                                if (isPaused) {
                                    downloadManager.resumeDownload(downloadId)
                                    isPaused = false
                                    Toast.makeText(context, "Download resumed", Toast.LENGTH_SHORT).show()
                                } else {
                                    downloadManager.pauseDownload(downloadId)
                                    isPaused = true
                                    Toast.makeText(context, "Download paused", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    )
                }

                // Description
                if (movie.description.isNotBlank()) {
                    item(key = "description") {
                        PhoneDescriptionSection(
                            description = movie.description,
                            showFull = showFullDescription,
                            onToggle = { showFullDescription = !showFullDescription }
                        )
                    }
                }

                // Movie details (genres, cast, etc.)
                item(key = "details") {
                    PhoneDetailsSection(movie = movie)
                }

                // Similar movies
                if (similarMovies.isNotEmpty()) {
                    item(key = "similar") {
                        PhoneSimilarMoviesSection(
                            movies = similarMovies,
                            onMovieClick = onMovieClick
                        )
                    }
                }
            }

            // Loading overlay when scraping video URL
            if (isScrapingUrl) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.7f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(
                            color = Color(0xFFE91E63),
                            strokeWidth = 4.dp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Finding video...",
                            color = Color.White,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }

            // Error dialog with retry option
            scrapeError?.let { error ->
                AlertDialog(
                    onDismissRequest = { scrapeError = null },
                    title = { Text("Download Error") },
                    text = { Text(error) },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                scrapeError = null
                                lastScrapeAction?.invoke()
                            }
                        ) {
                            Text("Retry")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { scrapeError = null }) {
                            Text("Cancel")
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun PhoneHeroSection(
    movie: Movie,
    isFavorite: Boolean,
    isWatched: Boolean,
    isInWatchlist: Boolean,
    isDownloaded: Boolean,
    isDownloading: Boolean,
    isPaused: Boolean,
    downloadProgress: Int,
    onPlayClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    onWatchlistClick: () -> Unit,
    onWatchedClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onCancelDownload: () -> Unit,
    onPauseResume: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Poster
        Card(
            modifier = Modifier
                .width(180.dp)
                .height(270.dp),
            shape = RoundedCornerShape(12.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
        ) {
            AsyncImage(
                model = movie.posterUrl,
                contentDescription = movie.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Title
        Text(
            text = movie.title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Metadata row
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            movie.year?.let { year ->
                Text(
                    text = year.toString(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.width(12.dp))
            }

            movie.rating?.let { rating ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = Color(0xFFFFD700),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = String.format("%.1f", rating),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Medium
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
            }

            movie.runtime?.takeIf { it > 0 }?.let { runtime ->
                Text(
                    text = "${runtime / 60}h ${runtime % 60}m",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.7f)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Play button (large, full width)
        Button(
            onClick = onPlayClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .padding(horizontal = 24.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFE91E63)
            ),
            shape = RoundedCornerShape(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
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

        Spacer(modifier = Modifier.height(16.dp))

        // Action buttons row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            PhoneActionButton(
                icon = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                label = "Favorite",
                isActive = isFavorite,
                activeColor = Color(0xFFE91E63),
                onClick = onFavoriteClick
            )

            PhoneActionButton(
                icon = if (isInWatchlist) Icons.Default.Check else Icons.Default.Add,
                label = "Watchlist",
                isActive = isInWatchlist,
                activeColor = Color(0xFF4CAF50),
                onClick = onWatchlistClick
            )

            PhoneActionButton(
                icon = if (isWatched) Icons.Default.CheckCircle else Icons.Default.Info,
                label = "Watched",
                isActive = isWatched,
                activeColor = Color(0xFF2196F3),
                onClick = onWatchedClick
            )

            // Download button with progress
            if (isDownloading || isPaused) {
                DownloadProgressButton(
                    progress = downloadProgress,
                    isPaused = isPaused,
                    onPauseResume = onPauseResume,
                    onCancel = onCancelDownload
                )
            } else {
                PhoneActionButton(
                    icon = if (isDownloaded) Icons.Default.Delete else Icons.Default.KeyboardArrowDown,
                    label = if (isDownloaded) "Downloaded" else "Download",
                    isActive = isDownloaded,
                    activeColor = Color(0xFF4CAF50),
                    onClick = onDownloadClick
                )
            }
        }
    }
}

@Composable
private fun DownloadProgressButton(
    progress: Int,
    isPaused: Boolean,
    onPauseResume: () -> Unit,
    onCancel: () -> Unit
) {
    val progressColor = if (isPaused) Color(0xFF9E9E9E) else Color(0xFFFF9800)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(8.dp)
    ) {
        Box(
            modifier = Modifier.size(48.dp),
            contentAlignment = Alignment.Center
        ) {
            // Background circle
            CircularProgressIndicator(
                progress = { 1f },
                modifier = Modifier.fillMaxSize(),
                color = Color.White.copy(alpha = 0.2f),
                strokeWidth = 3.dp
            )
            // Progress circle
            CircularProgressIndicator(
                progress = { progress.coerceIn(0, 100) / 100f },
                modifier = Modifier.fillMaxSize(),
                color = progressColor,
                strokeWidth = 3.dp
            )
            // Pause/Resume button (main tap area)
            IconButton(
                onClick = onPauseResume,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Close,
                    contentDescription = if (isPaused) "Resume" else "Pause",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = if (isPaused) "Paused" else "$progress%",
                style = MaterialTheme.typography.labelSmall,
                color = progressColor
            )
            // Small cancel button when paused
            if (isPaused) {
                IconButton(
                    onClick = onCancel,
                    modifier = Modifier.size(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cancel",
                        tint = Color(0xFFFF6B6B),
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun PhoneActionButton(
    icon: ImageVector,
    label: String,
    isActive: Boolean,
    activeColor: Color,
    onClick: () -> Unit
) {
    val color by animateColorAsState(
        targetValue = if (isActive) activeColor else Color.White.copy(alpha = 0.7f),
        label = "actionColor"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(8.dp)
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.1f))
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = color,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color
        )
    }
}

@Composable
private fun PhoneDescriptionSection(
    description: String,
    showFull: Boolean,
    onToggle: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp)
    ) {
        Text(
            text = "Synopsis",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.85f),
            maxLines = if (showFull) Int.MAX_VALUE else 4,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 22.sp
        )

        if (description.length > 200) {
            TextButton(
                onClick = onToggle,
                contentPadding = PaddingValues(0.dp)
            ) {
                Text(
                    text = if (showFull) "Show Less" else "Read More",
                    color = Color(0xFFE91E63)
                )
            }
        }
    }
}

@Composable
private fun PhoneDetailsSection(movie: Movie) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // Genres
        if (!movie.genres.isNullOrEmpty()) {
            Text(
                text = "Genres",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                movie.genres.take(4).forEach { genre ->
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color.White.copy(alpha = 0.1f)
                    ) {
                        Text(
                            text = genre,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Director
        movie.director?.takeIf { it.isNotBlank() }?.let { director ->
            DetailRow(label = "Director", value = director)
        }

        // Cast
        if (!movie.cast.isNullOrEmpty()) {
            DetailRow(label = "Cast", value = movie.cast.take(5).joinToString(", "))
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(
            text = "$label: ",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.6f),
            fontWeight = FontWeight.Medium
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.9f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun PhoneSimilarMoviesSection(
    movies: List<Movie>,
    onMovieClick: (Movie) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp)
    ) {
        Text(
            text = "Similar Movies",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(movies) { movie ->
                PhoneMovieCard(
                    movie = movie,
                    onClick = { onMovieClick(movie) },
                    width = 120.dp
                )
            }
        }
    }
}
