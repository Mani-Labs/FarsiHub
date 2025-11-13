package com.example.farsilandtv.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.farsilandtv.data.models.Episode
import com.example.farsilandtv.data.models.Series
import com.example.farsilandtv.data.repository.FavoritesRepository
import com.example.farsilandtv.data.repository.WatchlistRepository
import com.example.farsilandtv.ui.components.EpisodeCard
import com.example.farsilandtv.ui.components.GenreBadge
import com.example.farsilandtv.ui.components.SeriesCard
import com.example.farsilandtv.utils.EpisodeFormatter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Feature #16: Jetpack Compose for TV - Series Details Screen
 * Week 3 implementation
 *
 * Replaces: SeriesDetailsFragment.kt and SeriesDetailsActivity.kt
 *
 * Features:
 * - Backdrop with overlay
 * - Series info (title, genres, rating, year, seasons/episodes count)
 * - Season selector dropdown
 * - Episode list with progress and watched status
 * - Action buttons (Play First, Monitor, Favorite, Mark All Watched)
 * - Synopsis
 * - Similar series row
 * - D-pad navigation support
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeriesDetailsScreen(
    series: Series,
    episodesBySeason: Map<Int, List<Episode>>,
    onBackClick: () -> Unit,
    onPlayEpisode: (Episode) -> Unit,
    onSeriesClick: (Series) -> Unit = {},
    similarSeries: List<Series> = emptyList(),
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Repositories
    val favoritesRepo = remember { FavoritesRepository(context) }
    val watchlistRepo = remember { WatchlistRepository(context) }

    // State
    var isFavorite by remember { mutableStateOf(false) }
    var isMonitored by remember { mutableStateOf(false) }
    var synopsisExpanded by remember { mutableStateOf(false) }
    var selectedSeason by remember { mutableStateOf(episodesBySeason.keys.minOrNull() ?: 1) }

    // Load initial states
    LaunchedEffect(series.id) {
        isFavorite = favoritesRepo.isSeriesFavorited(series.id).first()
        isMonitored = watchlistRepo.isSeriesMonitored(series.id)
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize()
        ) {
            // Backdrop with gradient overlay
            item {
                BackdropSection(
                    backdropUrl = series.backdropUrl ?: series.posterUrl,
                    onBackClick = onBackClick
                )
            }

            // Series info overlay on backdrop
            item {
                SeriesInfoSection(
                    series = series,
                    isFavorite = isFavorite,
                    isMonitored = isMonitored,
                    modifier = Modifier.padding(horizontal = 48.dp)
                )
            }

            // Action buttons
            item {
                SeriesActionButtonsRow(
                    onPlayFirstClick = {
                        // Find first episode
                        val firstSeason = episodesBySeason.keys.minOrNull()
                        val firstEpisode = firstSeason?.let { episodesBySeason[it]?.minByOrNull { ep -> ep.episode } }
                        firstEpisode?.let { onPlayEpisode(it) }
                    },
                    isFavorite = isFavorite,
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
                    isMonitored = isMonitored,
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
                            val totalEpisodes = episodesBySeason.values.flatten().size
                            Toast.makeText(context, "Marked $totalEpisodes episodes as watched", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.padding(horizontal = 48.dp, vertical = 16.dp)
                )
            }

            // Synopsis section
            item {
                SynopsisSection(
                    synopsis = series.description,
                    expanded = synopsisExpanded,
                    onExpandClick = { synopsisExpanded = !synopsisExpanded },
                    modifier = Modifier.padding(horizontal = 48.dp, vertical = 16.dp)
                )
            }

            // Season selector
            if (episodesBySeason.keys.size > 1) {
                item {
                    SeasonSelector(
                        seasons = episodesBySeason.keys.sorted(),
                        selectedSeason = selectedSeason,
                        onSeasonSelect = { selectedSeason = it },
                        modifier = Modifier.padding(horizontal = 48.dp, vertical = 16.dp)
                    )
                }
            }

            // Episode list for selected season
            item {
                EpisodeList(
                    season = selectedSeason,
                    episodes = episodesBySeason[selectedSeason] ?: emptyList(),
                    onEpisodeClick = onPlayEpisode,
                    modifier = Modifier.padding(horizontal = 48.dp)
                )
            }

            // Similar series
            if (similarSeries.isNotEmpty()) {
                item {
                    SimilarSeriesSection(
                        series = similarSeries,
                        onSeriesClick = onSeriesClick,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                }
            }

            // Bottom spacing
            item {
                Spacer(modifier = Modifier.height(48.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BackdropSection(
    backdropUrl: String?,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(400.dp)
    ) {
        // Backdrop image
        AsyncImage(
            model = backdropUrl,
            contentDescription = "Series backdrop",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        // Gradient overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.7f)
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
                tint = Color.White
            )
        }
    }
}

@Composable
private fun SeriesInfoSection(
    series: Series,
    isFavorite: Boolean,
    isMonitored: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.offset(y = (-32).dp)
    ) {
        // Title
        Text(
            text = series.title,
            style = MaterialTheme.typography.displayMedium,
            color = Color.White
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Badges row
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Genre badges (first 3)
            series.genres.take(3).forEach { genre ->
                GenreBadge(genreName = genre)
            }

            // Rating
            series.rating?.let { rating ->
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.primary
                ) {
                    Text(
                        text = "★ ${"%.1f".format(rating)}",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }

            // Year and season/episode count
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                series.year?.let { year ->
                    Text(
                        text = year.toString(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }

                Text(
                    text = "${series.totalSeasons} Seasons",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.7f)
                )

                Text(
                    text = "${series.totalEpisodes} Episodes",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.7f)
                )
            }

            // Status badges
            if (isFavorite) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = Color(0xFFE91E63)
                ) {
                    Text(
                        text = "Favorite",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        color = Color.White
                    )
                }
            }
            if (isMonitored) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = Color(0xFF2196F3)
                ) {
                    Text(
                        text = "Monitored",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun SeriesActionButtonsRow(
    onPlayFirstClick: () -> Unit,
    isFavorite: Boolean,
    onFavoriteClick: () -> Unit,
    isMonitored: Boolean,
    onMonitorClick: () -> Unit,
    onMarkAllWatchedClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Play first episode (primary)
        Button(
            onClick = onPlayFirstClick,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Play First Episode")
        }

        // Monitor button
        OutlinedButton(onClick = onMonitorClick) {
            Icon(
                imageVector = if (isMonitored) Icons.Filled.Check else Icons.Filled.Add,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(if (isMonitored) "Monitored" else "Monitor Series")
        }

        // Favorite button
        OutlinedButton(onClick = onFavoriteClick) {
            Icon(
                imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(if (isFavorite) "Favorited" else "Add to Favorites")
        }

        // Mark all watched
        OutlinedButton(onClick = onMarkAllWatchedClick) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Mark All Watched")
        }
    }
}

@Composable
private fun SynopsisSection(
    synopsis: String,
    expanded: Boolean,
    onExpandClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = "Synopsis",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Text(
            text = synopsis,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = if (expanded) Int.MAX_VALUE else 3,
            overflow = TextOverflow.Ellipsis
        )

        if (synopsis.length > 200) {
            TextButton(onClick = onExpandClick) {
                Text(if (expanded) "Show less" else "Show more")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SeasonSelector(
    seasons: List<Int>,
    selectedSeason: Int,
    onSeasonSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = "Select Season",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // Simple row of season chips
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(seasons) { season ->
                FilterChip(
                    selected = season == selectedSeason,
                    onClick = { onSeasonSelect(season) },
                    label = {
                        Text(EpisodeFormatter.formatSeasonNumber(season))
                    }
                )
            }
        }
    }
}

@Composable
private fun EpisodeList(
    season: Int,
    episodes: List<Episode>,
    onEpisodeClick: (Episode) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = "${EpisodeFormatter.formatSeasonNumber(season)} Episodes",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(vertical = 16.dp)
        )

        // Sort episodes by episode number
        val sortedEpisodes = episodes.sortedBy { it.episode }

        // Episode cards
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            sortedEpisodes.forEach { episode ->
                EpisodeCard(
                    episode = episode,
                    onClick = { onEpisodeClick(episode) }
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
            text = "Similar Series",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(horizontal = 48.dp, vertical = 16.dp)
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(series) { show ->
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
