package com.example.farsilandtv.ui.screens.phone

import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import com.example.farsilandtv.data.models.Episode
import com.example.farsilandtv.data.models.Series
import com.example.farsilandtv.data.models.VideoUrl
import com.example.farsilandtv.data.repository.FavoritesRepository
import com.example.farsilandtv.data.repository.WatchlistRepository
import com.example.farsilandtv.data.scraper.VideoUrlScraper
import com.example.farsilandtv.data.scraper.getOrNull
import com.example.farsilandtv.ui.components.PhoneSeriesCard
import com.example.farsilandtv.utils.EpisodeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Phone Series Details Screen - Vertical layout with season tabs
 *
 * Phase 5: Phone-optimized series details with:
 * - Vertical scrolling layout
 * - Scrollable season tabs
 * - Touch-friendly episode list
 * - Large action buttons
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhoneSeriesDetailsScreen(
    series: Series,
    episodesBySeason: Map<Int, List<Episode>>,
    favoritesRepo: FavoritesRepository,
    watchlistRepo: WatchlistRepository,
    downloadManager: DownloadManager,
    onBackClick: () -> Unit,
    onPlayEpisode: (Episode) -> Unit,
    onSeriesClick: (Series) -> Unit = {},
    similarSeries: List<Series> = emptyList(),
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // State
    var isFavorite by remember { mutableStateOf(false) }
    var isMonitored by remember { mutableStateOf(false) }
    var selectedSeason by remember { mutableStateOf(episodesBySeason.keys.minOrNull() ?: 1) }
    var showFullDescription by remember { mutableStateOf(false) }

    // Download states
    var downloadedEpisodes by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var downloadingEpisodes by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var pausedEpisodes by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var downloadingAll by remember { mutableStateOf(false) }
    var scrapingEpisodeId by remember { mutableStateOf<Int?>(null) }
    var scrapeError by remember { mutableStateOf<String?>(null) }
    var lastScrapeAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    // Download progress from manager
    val allProgress by downloadManager.downloadProgress.collectAsState()

    // Load initial states
    LaunchedEffect(series.id) {
        try {
            isFavorite = favoritesRepo.isSeriesFavorited(series.id).first()
            isMonitored = watchlistRepo.isSeriesMonitored(series.id)
        } catch (e: Exception) {
            Log.e("PhoneSeriesDetails", "Failed to load series state: ${e.message}", e)
        }
    }

    // Single authoritative source for download state - prevents race conditions
    LaunchedEffect(episodesBySeason, allProgress) {
        val downloaded = mutableSetOf<Int>()
        val downloading = mutableSetOf<Int>()
        val paused = mutableSetOf<Int>()

        episodesBySeason.values.flatten().forEach { episode ->
            val downloadId = DownloadConstants.episodeId(episode.id)
            val progress = allProgress[downloadId] ?: 0
            val download = downloadManager.getDownload(downloadId)

            when {
                // Progress >= 100 or marked as downloaded
                progress >= 100 || downloadManager.isDownloaded(downloadId) -> {
                    downloaded.add(episode.id)
                }
                // Paused state
                download?.status == DownloadStatus.PAUSED -> {
                    paused.add(episode.id)
                }
                // Actively downloading or pending
                progress in 1..99 || download?.status == DownloadStatus.DOWNLOADING ||
                    download?.status == DownloadStatus.PENDING -> {
                    downloading.add(episode.id)
                }
            }
        }

        // Atomic state update
        downloadedEpisodes = downloaded
        downloadingEpisodes = downloading
        pausedEpisodes = paused
    }

    // Handle system back button
    BackHandler { onBackClick() }

    // Get episodes for selected season
    val currentEpisodes = episodesBySeason[selectedSeason] ?: emptyList()
    val seasons = episodesBySeason.keys.sorted()

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
                    IconButton(onClick = {
                        val shareText = buildString {
                            append("Check out \"${series.title}\"")
                            series.year?.let { append(" ($it)") }
                            series.rating?.let { append(" ⭐ ${String.format("%.1f", it)}") }
                            append("\n")
                            append("${series.totalSeasons} Seasons • ${episodesBySeason.values.flatten().size} Episodes")
                            append("\n\n")
                            if (series.description.isNotBlank()) {
                                val shortDesc = if (series.description.length > 150) {
                                    series.description.take(150) + "..."
                                } else {
                                    series.description
                                }
                                append(shortDesc)
                                append("\n\n")
                            }
                            append("Watch on FarsiPlex!")
                        }
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, series.title)
                            putExtra(Intent.EXTRA_TEXT, shareText)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Share series"))
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
                model = series.backdropUrl ?: series.posterUrl,
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
                // Hero section
                item(key = "hero") {
                    PhoneSeriesHeroSection(
                        series = series,
                        isFavorite = isFavorite,
                        isMonitored = isMonitored,
                        totalEpisodes = episodesBySeason.values.flatten().size,
                        downloadedCount = downloadedEpisodes.size,
                        downloadingAll = downloadingAll,
                        onPlayClick = {
                            // Play first episode of selected season
                            currentEpisodes.firstOrNull()?.let { onPlayEpisode(it) }
                        },
                        onFavoriteClick = {
                            scope.launch {
                                if (isFavorite) {
                                    favoritesRepo.removeSeriesFromFavorites(series.id)
                                    Toast.makeText(context, "Removed from favorites", Toast.LENGTH_SHORT).show()
                                } else {
                                    favoritesRepo.addSeriesToFavorites(series)
                                    Toast.makeText(context, "Added to favorites", Toast.LENGTH_SHORT).show()
                                }
                                isFavorite = !isFavorite
                            }
                        },
                        onMonitorClick = {
                            scope.launch {
                                if (isMonitored) {
                                    watchlistRepo.removeSeriesFromMonitored(series.id)
                                    Toast.makeText(context, "Stopped monitoring", Toast.LENGTH_SHORT).show()
                                } else {
                                    watchlistRepo.addSeriesToMonitored(series)
                                    Toast.makeText(context, "Monitoring new episodes", Toast.LENGTH_SHORT).show()
                                }
                                isMonitored = !isMonitored
                            }
                        },
                        onDownloadClick = {
                            scope.launch {
                                val totalEpisodes = episodesBySeason.values.flatten().size
                                if (downloadedEpisodes.isNotEmpty() && downloadedEpisodes.size == totalEpisodes) {
                                    // All episodes downloaded - clear all
                                    downloadingAll = true
                                    episodesBySeason.values.flatten().forEach { episode ->
                                        try {
                                            downloadManager.deleteDownload(DownloadConstants.episodeId(episode.id))
                                        } catch (e: Exception) {
                                            Log.e("PhoneSeriesDetails", "Failed to delete episode ${episode.id}: ${e.message}")
                                        }
                                    }
                                    downloadedEpisodes = emptySet()
                                    downloadingAll = false
                                    Toast.makeText(context, "All downloads removed", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Tap an episode's download button to download it", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    )
                }

                // Description
                if (series.description.isNotBlank()) {
                    item(key = "description") {
                        PhoneSeriesDescriptionSection(
                            description = series.description,
                            showFull = showFullDescription,
                            onToggle = { showFullDescription = !showFullDescription }
                        )
                    }
                }

                // Season tabs
                if (seasons.isNotEmpty()) {
                    item(key = "seasons") {
                        PhoneSeasonTabs(
                            seasons = seasons,
                            selectedSeason = selectedSeason,
                            onSeasonSelected = { selectedSeason = it }
                        )
                    }
                }

                // Episodes list
                items(currentEpisodes, key = { it.id }) { episode ->
                    val downloadId = DownloadConstants.episodeId(episode.id)
                    val isEpisodeDownloaded = downloadedEpisodes.contains(episode.id)
                    val isEpisodeDownloading = downloadingEpisodes.contains(episode.id)
                    val isEpisodePaused = pausedEpisodes.contains(episode.id)
                    val episodeProgress = allProgress[downloadId] ?: 0

                    PhoneEpisodeListItem(
                        episode = episode,
                        isDownloaded = isEpisodeDownloaded,
                        isDownloading = isEpisodeDownloading,
                        isPaused = isEpisodePaused,
                        downloadProgress = episodeProgress,
                        onClick = { onPlayEpisode(episode) },
                        onDownloadClick = {
                            Log.d("PhoneSeriesDetails", "Download clicked for episode: ${episode.title}")
                            scope.launch {
                                if (isEpisodeDownloaded) {
                                    // Remove download
                                    Log.d("PhoneSeriesDetails", "Removing episode download")
                                    downloadManager.deleteDownload(downloadId)
                                    downloadedEpisodes = downloadedEpisodes - episode.id
                                    Toast.makeText(context, "Download removed", Toast.LENGTH_SHORT).show()
                                } else if (!isEpisodeDownloading && !isEpisodePaused && scrapingEpisodeId != episode.id) {
                                    // Start download
                                    val episodeUrl = episode.farsilandUrl
                                    if (episodeUrl.isNullOrBlank()) {
                                        Toast.makeText(context, "Video source not available", Toast.LENGTH_SHORT).show()
                                        return@launch
                                    }

                                    // Define the scrape action for retry
                                    suspend fun doScrapeAndDownload() {
                                        Log.d("PhoneSeriesDetails", "Starting download, URL: $episodeUrl")
                                        scrapingEpisodeId = episode.id
                                        scrapeError = null

                                        try {
                                            val result = withContext(Dispatchers.IO) {
                                                VideoUrlScraper.extractVideoUrls(episodeUrl)
                                            }

                                            val videoUrls: List<VideoUrl> = result.getOrNull() ?: emptyList()
                                            val bestUrl = videoUrls.maxByOrNull { videoUrl: VideoUrl ->
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
                                                val episodeInfo = "S${episode.season} E${episode.episode}"
                                                Toast.makeText(context, "Downloading $episodeInfo (${bestUrl.quality})...", Toast.LENGTH_SHORT).show()
                                                downloadManager.queueEpisodeDownload(
                                                    episodeId = episode.id,
                                                    seriesTitle = series.title,
                                                    episodeInfo = episodeInfo,
                                                    posterUrl = episode.thumbnailUrl ?: episode.episodePosterUrl,
                                                    videoUrl = bestUrl.url
                                                )
                                                // Only mark as downloading AFTER successful queue
                                                downloadingEpisodes = downloadingEpisodes + episode.id
                                                lastScrapeAction = null
                                            } else {
                                                scrapeError = "No video URL found for S${episode.season} E${episode.episode}"
                                            }
                                        } catch (e: Exception) {
                                            Log.e("PhoneSeriesDetails", "Failed to scrape video URL: ${e.message}", e)
                                            scrapeError = e.message ?: "Failed to find video"
                                        } finally {
                                            scrapingEpisodeId = null
                                        }
                                    }

                                    // Store action for retry
                                    lastScrapeAction = { scope.launch { doScrapeAndDownload() } }
                                    doScrapeAndDownload()
                                }
                            }
                        },
                        onPauseResume = {
                            scope.launch {
                                if (isEpisodePaused) {
                                    downloadManager.resumeDownload(downloadId)
                                    pausedEpisodes = pausedEpisodes - episode.id
                                    downloadingEpisodes = downloadingEpisodes + episode.id
                                    Toast.makeText(context, "Download resumed", Toast.LENGTH_SHORT).show()
                                } else {
                                    downloadManager.pauseDownload(downloadId)
                                    pausedEpisodes = pausedEpisodes + episode.id
                                    downloadingEpisodes = downloadingEpisodes - episode.id
                                    Toast.makeText(context, "Download paused", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        onCancelDownload = {
                            scope.launch {
                                downloadManager.cancelDownload(downloadId)
                                downloadingEpisodes = downloadingEpisodes - episode.id
                                pausedEpisodes = pausedEpisodes - episode.id
                                Toast.makeText(context, "Download cancelled", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }

                // Similar series
                if (similarSeries.isNotEmpty()) {
                    item(key = "similar") {
                        PhoneSimilarSeriesSection(
                            series = similarSeries,
                            onSeriesClick = onSeriesClick
                        )
                    }
                }
            }

            // Loading overlay when scraping video URL
            if (scrapingEpisodeId != null) {
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
private fun PhoneSeriesHeroSection(
    series: Series,
    isFavorite: Boolean,
    isMonitored: Boolean,
    totalEpisodes: Int,
    downloadedCount: Int,
    downloadingAll: Boolean,
    onPlayClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    onMonitorClick: () -> Unit,
    onDownloadClick: () -> Unit
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
                model = series.posterUrl,
                contentDescription = series.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Title
        Text(
            text = series.title,
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
            // TV SERIES badge
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = Color(0xFF4CAF50)
            ) {
                Text(
                    text = "TV SERIES",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            series.year?.let { year ->
                Text(
                    text = year.toString(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.width(12.dp))
            }

            series.rating?.let { rating ->
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
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Episode count
        Text(
            text = "${series.totalSeasons} Seasons • $totalEpisodes Episodes",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.6f)
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Play button
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

        // Action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            PhoneSeriesActionButton(
                icon = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                label = "Favorite",
                isActive = isFavorite,
                activeColor = Color(0xFFE91E63),
                onClick = onFavoriteClick
            )

            PhoneSeriesActionButton(
                icon = if (isMonitored) Icons.Default.Notifications else Icons.Default.Email,
                label = "Monitor",
                isActive = isMonitored,
                activeColor = Color(0xFF4CAF50),
                onClick = onMonitorClick
            )

            PhoneSeriesActionButton(
                icon = if (downloadedCount > 0) Icons.Default.CheckCircle else Icons.Default.KeyboardArrowDown,
                label = if (downloadingAll) "..." else if (downloadedCount > 0) "$downloadedCount DL" else "Download",
                isActive = downloadedCount > 0,
                activeColor = Color(0xFFFF9800),
                onClick = onDownloadClick
            )
        }
    }
}

@Composable
private fun PhoneSeriesActionButton(
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
private fun PhoneSeriesDescriptionSection(
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
private fun PhoneSeasonTabs(
    seasons: List<Int>,
    selectedSeason: Int,
    onSeasonSelected: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Text(
            text = "Seasons",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(seasons) { season ->
                val isSelected = season == selectedSeason
                FilterChip(
                    selected = isSelected,
                    onClick = { onSeasonSelected(season) },
                    label = {
                        Text(
                            text = EpisodeFormatter.formatSeasonNumber(season),
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFFE91E63),
                        selectedLabelColor = Color.White,
                        containerColor = Color.White.copy(alpha = 0.1f),
                        labelColor = Color.White.copy(alpha = 0.8f)
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Episodes",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}

@Composable
private fun PhoneEpisodeListItem(
    episode: Episode,
    isDownloaded: Boolean = false,
    isDownloading: Boolean = false,
    isPaused: Boolean = false,
    downloadProgress: Int = 0,
    onClick: () -> Unit,
    onDownloadClick: () -> Unit = {},
    onPauseResume: () -> Unit = {},
    onCancelDownload: () -> Unit = {}
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(90.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbnail
            Box(
                modifier = Modifier
                    .width(130.dp)
                    .fillMaxHeight()
            ) {
                AsyncImage(
                    model = episode.thumbnailUrl ?: episode.episodePosterUrl,
                    contentDescription = episode.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp))
                )

                // Play overlay
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }

                // Episode number badge
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopStart)
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

                // Downloaded badge
                if (isDownloaded) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .background(Color(0xFF4CAF50), RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Downloaded",
                            tint = Color.White,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }

                // Progress bar if watching (only show for valid progress 1-99%)
                if (episode.isInProgress && episode.progressPercentage in 1..99) {
                    LinearProgressIndicator(
                        progress = { episode.progressPercentage.coerceIn(0, 100) / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .align(Alignment.BottomCenter),
                        color = Color(0xFFE91E63),
                        trackColor = Color.Black.copy(alpha = 0.5f)
                    )
                }
            }

            // Episode info
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(12.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = episode.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    episode.runtime?.let { runtime ->
                        Text(
                            text = "${runtime}min",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                    }

                    episode.airDate?.let { date ->
                        Text(
                            text = date,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                    }

                    if (episode.isWatched) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Watched",
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    if (isDownloaded) {
                        Text(
                            text = "• Offline",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF4CAF50)
                        )
                    }
                }
            }

            // Download button with progress
            if (isDownloading || isPaused) {
                // Show progress with pause/resume
                EpisodeDownloadProgress(
                    progress = downloadProgress,
                    isPaused = isPaused,
                    onPauseResume = onPauseResume,
                    onCancel = onCancelDownload
                )
            } else {
                IconButton(
                    onClick = onDownloadClick,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = if (isDownloaded) Icons.Default.Delete else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (isDownloaded) "Remove download" else "Download",
                        tint = if (isDownloaded) Color(0xFF4CAF50) else Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun EpisodeDownloadProgress(
    progress: Int,
    isPaused: Boolean,
    onPauseResume: () -> Unit,
    onCancel: () -> Unit
) {
    val progressColor = if (isPaused) Color(0xFF9E9E9E) else Color(0xFFFF9800)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.padding(end = 8.dp)
    ) {
        // Progress indicator with pause/resume button
        Box(
            modifier = Modifier.size(36.dp),
            contentAlignment = Alignment.Center
        ) {
            // Background circle
            CircularProgressIndicator(
                progress = { 1f },
                modifier = Modifier.fillMaxSize(),
                color = Color.White.copy(alpha = 0.2f),
                strokeWidth = 2.dp
            )
            // Progress circle
            CircularProgressIndicator(
                progress = { progress.coerceIn(0, 100) / 100f },
                modifier = Modifier.fillMaxSize(),
                color = progressColor,
                strokeWidth = 2.dp
            )
            // Pause/Resume button
            IconButton(
                onClick = onPauseResume,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Close,
                    contentDescription = if (isPaused) "Resume" else "Pause",
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
            }
        }

        // Progress text
        Text(
            text = if (isPaused) "Paused" else "$progress%",
            style = MaterialTheme.typography.labelSmall,
            color = progressColor,
            modifier = Modifier.width(40.dp)
        )

        // Cancel button (only when paused)
        if (isPaused) {
            IconButton(
                onClick = onCancel,
                modifier = Modifier.size(20.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Cancel",
                    tint = Color(0xFFFF6B6B),
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

@Composable
private fun PhoneSimilarSeriesSection(
    series: List<Series>,
    onSeriesClick: (Series) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 24.dp)
    ) {
        Text(
            text = "Similar Shows",
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
            items(series) { show ->
                PhoneSeriesCard(
                    series = show,
                    onClick = { onSeriesClick(show) },
                    width = 120.dp
                )
            }
        }
    }
}
