package com.example.farsilandtv.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.farsilandtv.data.models.Episode
import com.example.farsilandtv.data.models.FeaturedContent
import com.example.farsilandtv.data.models.Movie
import com.example.farsilandtv.data.models.Series
import com.example.farsilandtv.data.repository.FavoritesRepository
import com.example.farsilandtv.ui.components.*
import com.example.farsilandtv.ui.viewmodel.MainViewModel

/**
 * Feature #16: Jetpack Compose for TV - Home Screen
 * Week 2 Migration - Replaces HomeFragment
 *
 * Features:
 * - FeaturedCarousel at top (6 rotating items, auto-play)
 * - 4 horizontal content rows:
 *   - Latest Episodes
 *   - Recent Movies
 *   - Recent Shows
 *   - Favorites
 * - LazyColumn for vertical scrolling
 * - D-pad navigation support
 */
@Composable
fun HomeScreen(
    onMovieClick: (Movie) -> Unit,
    onSeriesClick: (Series) -> Unit,
    onEpisodeClick: (Episode) -> Unit,
    onFeaturedClick: (FeaturedContent) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MainViewModel = viewModel()
) {
    val context = LocalContext.current
    val favoritesRepo = remember { FavoritesRepository(context) }

    // Observe ViewModel data
    val featuredContent by viewModel.featuredContent.observeAsState(emptyList())
    val recentEpisodes by viewModel.recentEpisodes.observeAsState(emptyList())
    val recentMovies by viewModel.recentMovies.observeAsState(emptyList())
    val recentSeries by viewModel.recentSeries.observeAsState(emptyList())
    val isLoading by viewModel.isLoading.observeAsState(false)

    // Observe favorites
    val favorites by favoritesRepo.getAllFavorites()
        .collectAsState(initial = emptyList())

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 32.dp)
    ) {
        // Featured Carousel (6 items, auto-rotating)
        item {
            if (isLoading && featuredContent.isEmpty()) {
                FeaturedCarouselSkeleton()
            } else if (featuredContent.isNotEmpty()) {
                FeaturedCarousel(
                    content = featuredContent.toFeaturedItems(),
                    onContentClick = { item ->
                        when (item) {
                            is FeaturedItem.MovieItem -> onMovieClick(item.movie)
                            is FeaturedItem.SeriesItem -> onSeriesClick(item.series)
                        }
                    }
                )
            }
            Spacer(modifier = Modifier.height(32.dp))
        }

        // Latest Episodes Row
        item {
            if (isLoading && recentEpisodes.isEmpty()) {
                ContentRowSkeleton(title = "Latest Episodes")
            } else if (recentEpisodes.isNotEmpty()) {
                EpisodeRow(
                    title = "Latest Episodes",
                    episodes = recentEpisodes,
                    onEpisodeClick = onEpisodeClick
                )
            }
        }

        // Recent Movies Row
        item {
            if (isLoading && recentMovies.isEmpty()) {
                ContentRowSkeleton(title = "Recent Movies")
            } else if (recentMovies.isNotEmpty()) {
                MovieRow(
                    title = "Recent Movies",
                    movies = recentMovies,
                    onMovieClick = onMovieClick,
                    getFavoriteStatus = { movieId ->
                        favorites.any { it.contentId == "movie-$movieId" }
                    }
                )
            }
        }

        // Recent Shows Row
        item {
            if (isLoading && recentSeries.isEmpty()) {
                ContentRowSkeleton(title = "Recent Shows")
            } else if (recentSeries.isNotEmpty()) {
                SeriesRow(
                    title = "Recent Shows",
                    series = recentSeries,
                    onSeriesClick = onSeriesClick,
                    getFavoriteStatus = { seriesId ->
                        favorites.any { it.contentId == "series-$seriesId" }
                    }
                )
            }
        }

        // Favorites Row (if any exist)
        item {
            if (favorites.isNotEmpty()) {
                FavoritesRow(
                    title = "My Favorites",
                    favorites = favorites,
                    onMovieClick = onMovieClick,
                    onSeriesClick = onSeriesClick
                )
            }
        }
    }
}

/**
 * Episode row - horizontal scrolling list of episodes
 */
@Composable
private fun EpisodeRow(
    title: String,
    episodes: List<Episode>,
    onEpisodeClick: (Episode) -> Unit,
    modifier: Modifier = Modifier
) {
    ContentRow(
        title = title,
        content = episodes,
        onContentClick = onEpisodeClick,
        cardContent = { episode, onClick ->
            EpisodeCard(
                episode = episode,
                onClick = onClick
            )
        },
        modifier = modifier
    )
}

/**
 * Favorites row - mixed content (movies + series)
 */
@Composable
private fun FavoritesRow(
    title: String,
    favorites: List<com.example.farsilandtv.data.database.Favorite>,
    onMovieClick: (Movie) -> Unit,
    onSeriesClick: (Series) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.padding(vertical = 16.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(start = 48.dp, end = 48.dp, bottom = 16.dp)
        )

        androidx.compose.foundation.lazy.LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(favorites.size) { index ->
                val favorite = favorites[index]

                when (favorite.contentType) {
                    com.example.farsilandtv.data.database.Favorite.ContentType.MOVIE -> {
                        val movieId = favorite.contentId.removePrefix("movie-").toIntOrNull()
                        if (movieId != null) {
                            val movie = Movie(
                                id = movieId,
                                title = favorite.title,
                                posterUrl = favorite.posterUrl,
                                farsilandUrl = "",
                                description = ""
                            )
                            MovieCard(
                                movie = movie,
                                onClick = { onMovieClick(movie) },
                                isFavorite = true
                            )
                        }
                    }
                    com.example.farsilandtv.data.database.Favorite.ContentType.SERIES -> {
                        val seriesId = favorite.contentId.removePrefix("series-").toIntOrNull()
                        if (seriesId != null) {
                            val series = Series(
                                id = seriesId,
                                title = favorite.title,
                                posterUrl = favorite.posterUrl,
                                farsilandUrl = "",
                                description = ""
                            )
                            SeriesCard(
                                series = series,
                                onClick = { onSeriesClick(series) },
                                isFavorite = true
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Episode card component (reused from existing pattern)
 */
@Composable
private fun EpisodeCard(
    episode: Episode,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Using MovieCard as base for episode display (similar dimensions)
    val episodeAsMovie = Movie(
        id = episode.id,
        title = "${episode.formattedNumber}: ${episode.title}",
        posterUrl = episode.thumbnailUrl,
        farsilandUrl = episode.farsilandUrl,
        description = episode.seriesTitle ?: ""
    )

    MovieCard(
        movie = episodeAsMovie,
        onClick = onClick,
        modifier = modifier
    )
}
