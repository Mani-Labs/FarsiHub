package com.example.farsilandtv.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.example.farsilandtv.data.download.DownloadManager
import com.example.farsilandtv.data.models.Episode
import com.example.farsilandtv.data.models.Series
import com.example.farsilandtv.data.repository.FavoritesRepository
import com.example.farsilandtv.data.repository.WatchlistRepository
import com.example.farsilandtv.ui.components.SeriesCard
import com.example.farsilandtv.utils.EpisodeFormatter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Series Details Screen - Redesigned with elegant layout and season navigation
 *
 * Design principles:
 * - Blurred backdrop as ambient background
 * - Poster + info side-by-side layout
 * - Horizontal season tabs (NOT dropdown)
 * - Episode list with thumbnails and progress
 * - Clear focus states for D-pad navigation
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeriesDetailsScreen(
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
    var selectedSeason by rememberSaveable { mutableStateOf(episodesBySeason.keys.minOrNull() ?: 1) }
    var downloadingAll by remember { mutableStateOf(false) }

    // Load initial states
    LaunchedEffect(series.id) {
        isFavorite = favoritesRepo.isSeriesFavorited(series.id).first()
        isMonitored = watchlistRepo.isSeriesMonitored(series.id)
    }

    // Focus requesters
    val playButtonFocus = remember { FocusRequester() }
    val favoriteButtonFocus = remember { FocusRequester() }
    val monitorButtonFocus = remember { FocusRequester() }
    val downloadButtonFocus = remember { FocusRequester() }
    val shareButtonFocus = remember { FocusRequester() }

    // Request focus on Play button when screen loads
    LaunchedEffect(Unit) {
        playButtonFocus.requestFocus()
    }

    // Calculate total episodes
    val totalEpisodes = episodesBySeason.values.flatten().size

    Box(modifier = modifier.fillMaxSize()) {
        // Blurred backdrop
        AsyncImage(
            model = series.backdropUrl ?: series.posterUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .blur(25.dp)
        )

        // Dark overlay
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
            // Hero section
            item(key = "hero") {
                SeriesHeroSection(
                    series = series,
                    totalSeasons = episodesBySeason.keys.size,
                    totalEpisodes = totalEpisodes,
                    isFavorite = isFavorite,
                    isMonitored = isMonitored,
                    onBackClick = onBackClick,
                    onPlayClick = {
                        // Find first episode to play
                        val firstSeason = episodesBySeason.keys.minOrNull()
                        val firstEpisode = firstSeason?.let {
                            episodesBySeason[it]?.minByOrNull { ep -> ep.episode }
                        }
                        firstEpisode?.let { onPlayEpisode(it) }
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
                                Toast.makeText(context, "Removed from monitoring", Toast.LENGTH_SHORT).show()
                            } else {
                                watchlistRepo.addSeriesToMonitored(series)
                                Toast.makeText(context, "Series is now monitored", Toast.LENGTH_SHORT).show()
                            }
                            isMonitored = !isMonitored
                        }
                    },
                    onMarkAllWatchedClick = {
                        scope.launch {
                            watchlistRepo.markAllEpisodesAsWatched(series.id)
                            Toast.makeText(context, "Marked $totalEpisodes episodes as watched", Toast.LENGTH_SHORT).show()
                        }
                    },
                    downloadingAll = downloadingAll,
                    onDownloadAllClick = {
                        scope.launch {
                            downloadingAll = true
                            Toast.makeText(context, "Download feature requires playing episodes first", Toast.LENGTH_LONG).show()
                            downloadingAll = false
                        }
                    },
                    onShareClick = {
                        val shareText = buildString {
                            append("Check out \"${series.title}\"")
                            series.year?.let { append(" ($it)") }
                            series.rating?.let { append(" ⭐ ${String.format("%.1f", it)}") }
                            append("\n")
                            append("$totalEpisodes Episodes")
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
                    },
                    playButtonFocus = playButtonFocus,
                    favoriteButtonFocus = favoriteButtonFocus,
                    monitorButtonFocus = monitorButtonFocus,
                    downloadButtonFocus = downloadButtonFocus,
                    shareButtonFocus = shareButtonFocus
                )
            }

            // Synopsis
            if (series.description.isNotBlank()) {
                item(key = "synopsis") {
                    SeriesSynopsisSection(
                        synopsis = series.description,
                        modifier = Modifier.padding(horizontal = 48.dp, vertical = 16.dp)
                    )
                }
            }

            // Season tabs + Episodes section
            if (episodesBySeason.isNotEmpty()) {
                // Season tabs
                item(key = "season_tabs") {
                    SeasonTabsSection(
                        seasons = episodesBySeason.keys.sorted(),
                        selectedSeason = selectedSeason,
                        onSeasonSelect = { selectedSeason = it },
                        modifier = Modifier.padding(horizontal = 48.dp, vertical = 16.dp)
                    )
                }

                // Episode list for selected season
                item(key = "episodes_header") {
                    Text(
                        text = "${episodesBySeason[selectedSeason]?.size ?: 0} Episodes",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.padding(horizontal = 48.dp, vertical = 8.dp)
                    )
                }

                // Episode items
                val episodes = episodesBySeason[selectedSeason]?.sortedBy { it.episode } ?: emptyList()
                itemsIndexed(
                    items = episodes,
                    key = { _, ep -> "episode-${ep.season}-${ep.episode}" }
                ) { index, episode ->
                    EpisodeListItem(
                        episode = episode,
                        onClick = { onPlayEpisode(episode) },
                        modifier = Modifier.padding(horizontal = 48.dp, vertical = 4.dp)
                    )
                }
            }

            // Similar series
            if (similarSeries.isNotEmpty()) {
                item(key = "similar") {
                    SimilarSeriesSection(
                        series = similarSeries,
                        onSeriesClick = onSeriesClick,
                        modifier = Modifier.padding(vertical = 24.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SeriesHeroSection(
    series: Series,
    totalSeasons: Int,
    totalEpisodes: Int,
    isFavorite: Boolean,
    isMonitored: Boolean,
    downloadingAll: Boolean,
    onBackClick: () -> Unit,
    onPlayClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    onMonitorClick: () -> Unit,
    onMarkAllWatchedClick: () -> Unit,
    onDownloadAllClick: () -> Unit,
    onShareClick: () -> Unit,
    playButtonFocus: FocusRequester,
    favoriteButtonFocus: FocusRequester,
    monitorButtonFocus: FocusRequester,
    downloadButtonFocus: FocusRequester,
    shareButtonFocus: FocusRequester,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(380.dp)
    ) {
        // Top gradient
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

        // Content row
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 48.dp, end = 48.dp, top = 48.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Poster
            Box(
                modifier = Modifier
                    .width(180.dp)
                    .height(270.dp)
                    .shadow(
                        elevation = 24.dp,
                        shape = RoundedCornerShape(12.dp),
                        ambientColor = Color.Black,
                        spotColor = Color.Black
                    )
                    .clip(RoundedCornerShape(12.dp))
            ) {
                AsyncImage(
                    model = series.posterUrl,
                    contentDescription = series.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(modifier = Modifier.width(36.dp))

            // Info column
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 32.dp),
                verticalArrangement = Arrangement.Center
            ) {
                // Badge row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // TV SHOW badge
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color(0xFF5C6BC0)
                    ) {
                        Text(
                            text = "TV SHOW",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            letterSpacing = 1.sp
                        )
                    }

                    // Year
                    series.year?.let { year ->
                        Text(
                            text = year.toString(),
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }

                    // Rating
                    series.rating?.let { rating ->
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

                    // Season/Episode count
                    Text(
                        text = "$totalSeasons Season${if (totalSeasons != 1) "s" else ""} · $totalEpisodes Ep",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Title
                Text(
                    text = series.title,
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 40.sp
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Genre chips
                if (series.genres.isNotEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        series.genres.take(4).forEach { genre ->
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = Color.White.copy(alpha = 0.15f),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f))
                            ) {
                                Text(
                                    text = genre,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.White.copy(alpha = 0.9f)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Description preview
                if (series.description.isNotBlank()) {
                    Text(
                        text = series.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.7f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 20.sp
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Action buttons
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SeriesActionButton(
                        icon = Icons.Filled.PlayArrow,
                        label = "Play S1E1",
                        isPrimary = true,
                        isActive = false,
                        onClick = onPlayClick,
                        focusRequester = playButtonFocus,
                        onLeftPress = { },
                        onRightPress = { favoriteButtonFocus.requestFocus() }
                    )

                    SeriesActionButton(
                        icon = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        label = if (isFavorite) "Favorited" else "Favorite",
                        isPrimary = false,
                        isActive = isFavorite,
                        onClick = onFavoriteClick,
                        focusRequester = favoriteButtonFocus,
                        onLeftPress = { playButtonFocus.requestFocus() },
                        onRightPress = { monitorButtonFocus.requestFocus() }
                    )

                    SeriesActionButton(
                        icon = if (isMonitored) Icons.Filled.Notifications else Icons.Default.Notifications,
                        label = if (isMonitored) "Monitoring" else "Monitor",
                        isPrimary = false,
                        isActive = isMonitored,
                        onClick = onMonitorClick,
                        focusRequester = monitorButtonFocus,
                        onLeftPress = { favoriteButtonFocus.requestFocus() },
                        onRightPress = { downloadButtonFocus.requestFocus() }
                    )

                    // Download All button
                    SeriesActionButton(
                        icon = if (downloadingAll) Icons.Default.Refresh else Icons.Default.ArrowDropDown,
                        label = if (downloadingAll) "Downloading..." else "Download All",
                        isPrimary = false,
                        isActive = downloadingAll,
                        onClick = onDownloadAllClick,
                        focusRequester = downloadButtonFocus,
                        onLeftPress = { monitorButtonFocus.requestFocus() },
                        onRightPress = { shareButtonFocus.requestFocus() }
                    )

                    // Share button
                    SeriesActionButton(
                        icon = Icons.Default.Share,
                        label = "Share",
                        isPrimary = false,
                        isActive = false,
                        onClick = onShareClick,
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
private fun SeriesActionButton(
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
                .height(44.dp)
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
            shape = RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(horizontal = 16.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
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
                .height(44.dp)
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
            shape = RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(horizontal = 12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = if (isActive && !isFocused) MaterialTheme.colorScheme.primary else Color.White
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (isActive) FontWeight.Medium else FontWeight.Normal
            )
        }
    }
}

@Composable
private fun SeriesSynopsisSection(
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
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Text(
            text = synopsis,
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White.copy(alpha = 0.85f),
            maxLines = if (expanded) Int.MAX_VALUE else 3,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 24.sp
        )

        if (synopsis.length > 200) {
            TextButton(
                onClick = { expanded = !expanded },
                modifier = Modifier.padding(top = 2.dp)
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
private fun SeasonTabsSection(
    seasons: List<Int>,
    selectedSeason: Int,
    onSeasonSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = "Seasons",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(seasons, key = { "season-$it" }) { season ->
                SeasonTab(
                    seasonNumber = season,
                    isSelected = season == selectedSeason,
                    onClick = { onSeasonSelect(season) }
                )
            }
        }
    }
}

@Composable
private fun SeasonTab(
    seasonNumber: Int,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }

    Surface(
        onClick = onClick,
        modifier = modifier
            .onFocusChanged { isFocused = it.isFocused },
        shape = RoundedCornerShape(8.dp),
        color = when {
            isFocused -> MaterialTheme.colorScheme.primary
            isSelected -> Color.White.copy(alpha = 0.2f)
            else -> Color.Transparent
        },
        border = if (!isFocused && isSelected) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else if (!isFocused) {
            BorderStroke(1.dp, Color.White.copy(alpha = 0.3f))
        } else null
    ) {
        Text(
            text = EpisodeFormatter.formatSeasonNumber(seasonNumber),
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = if (isSelected || isFocused) FontWeight.Bold else FontWeight.Normal,
            color = if (isFocused) Color.Black else Color.White
        )
    }
}

@Composable
private fun EpisodeListItem(
    episode: Episode,
    onClick: () -> Unit,
    onDownloadClick: (() -> Unit)? = null,
    isDownloaded: Boolean = false,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }

    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { isFocused = it.isFocused },
        shape = RoundedCornerShape(8.dp),
        color = when {
            isFocused -> Color.White.copy(alpha = 0.15f)
            else -> Color.White.copy(alpha = 0.05f)
        },
        border = if (isFocused) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Episode thumbnail
            Box(
                modifier = Modifier
                    .width(140.dp)
                    .height(80.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF2A2A2A))
            ) {
                AsyncImage(
                    model = episode.thumbnailUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                // Play icon overlay on focus
                if (isFocused) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.4f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = "Play",
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }

                // Downloaded badge
                if (isDownloaded) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .background(Color(0xFF4CAF50), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = "Downloaded",
                            tint = Color.White,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Episode info
            Column(
                modifier = Modifier.weight(1f)
            ) {
                // Episode number + title
                Text(
                    text = "E${episode.episode}: ${episode.title}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Description
                episode.description?.let { desc ->
                    if (desc.isNotBlank()) {
                        Text(
                            text = desc,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.6f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            lineHeight = 18.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Duration + download status
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    episode.runtime?.let { runtime ->
                        Text(
                            text = "${runtime} min",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.5f)
                        )
                    }

                    if (isDownloaded) {
                        Text(
                            text = "• Downloaded",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color(0xFF4CAF50)
                        )
                    }
                }
            }

            // Download button (if callback provided and not downloaded)
            if (onDownloadClick != null && !isDownloaded) {
                IconButton(
                    onClick = onDownloadClick,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = "Download",
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // Play button indicator
            if (isFocused) {
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

@Composable
private fun SimilarSeriesSection(
    series: List<Series>,
    onSeriesClick: (Series) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = "Similar Shows",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(start = 48.dp, bottom = 16.dp)
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(series, key = { it.id }) { show ->
                SeriesCard(
                    series = show,
                    onClick = { onSeriesClick(show) },
                    isFavorite = false,
                    hasUnwatchedEpisodes = false
                )
            }
        }
    }
}
