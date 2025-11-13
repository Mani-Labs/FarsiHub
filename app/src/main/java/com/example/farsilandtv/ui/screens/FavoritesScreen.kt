package com.example.farsilandtv.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import com.example.farsilandtv.data.database.Favorite
import com.example.farsilandtv.data.models.Movie
import com.example.farsilandtv.data.models.Series
import com.example.farsilandtv.data.repository.FavoritesRepository
import com.example.farsilandtv.ui.components.MovieCard
import com.example.farsilandtv.ui.components.SeriesCard
import kotlinx.coroutines.flow.Flow

/**
 * Feature #16: Jetpack Compose for TV - Favorites Screen
 * Migration from FavoritesFragment (Leanback) to Compose
 *
 * Replaces:
 * - VerticalGridSupportFragment
 * - ArrayObjectAdapter
 * - GenreCardPresenter
 *
 * Features:
 * - LazyVerticalGrid for TV grid layout
 * - D-pad navigation support
 * - Empty state handling
 * - Favorites repository integration
 */
@Composable
fun FavoritesScreen(
    onMovieClick: (Movie) -> Unit,
    onSeriesClick: (Series) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val favoritesRepo = remember { FavoritesRepository(context) }

    // Collect favorites as state
    val favorites by favoritesRepo.getAllFavorites()
        .collectAsState(initial = emptyList())

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 48.dp, vertical = 32.dp)
    ) {
        // Screen title
        Text(
            text = "My Favorites",
            style = MaterialTheme.typography.displayMedium,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        if (favorites.isEmpty()) {
            // Empty state
            EmptyFavoritesState()
        } else {
            // Grid of favorites
            FavoritesGrid(
                favorites = favorites,
                onMovieClick = onMovieClick,
                onSeriesClick = onSeriesClick
            )
        }
    }
}

@Composable
private fun FavoritesGrid(
    favorites: List<Favorite>,
    onMovieClick: (Movie) -> Unit,
    onSeriesClick: (Series) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(5), // NUM_COLUMNS from original
        contentPadding = PaddingValues(bottom = 48.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier.fillMaxSize()
    ) {
        items(favorites.size) { index ->
            val favorite = favorites[index]

            when (favorite.contentType) {
                Favorite.ContentType.MOVIE -> {
                    val movieId = favorite.contentId.removePrefix("movie-").toIntOrNull()
                    if (movieId != null) {
                        val movie = Movie(
                            id = movieId,
                            title = favorite.title,
                            posterUrl = favorite.posterUrl,
                            farsilandUrl = "",
                            description = "",
                            year = null,
                            rating = null,
                            genres = emptyList(),
                            backdropUrl = null
                        )
                        MovieCard(
                            movie = movie,
                            onClick = { onMovieClick(movie) },
                            isFavorite = true, // Always true in favorites screen
                            isWatched = false
                        )
                    }
                }
                Favorite.ContentType.SERIES -> {
                    val seriesId = favorite.contentId.removePrefix("series-").toIntOrNull()
                    if (seriesId != null) {
                        val series = Series(
                            id = seriesId,
                            title = favorite.title,
                            posterUrl = favorite.posterUrl,
                            farsilandUrl = "",
                            description = "",
                            year = null,
                            rating = null,
                            genres = emptyList(),
                            totalSeasons = 0,
                            totalEpisodes = 0,
                            backdropUrl = null
                        )
                        SeriesCard(
                            series = series,
                            onClick = { onSeriesClick(series) },
                            isFavorite = true,
                            hasUnwatchedEpisodes = false
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyFavoritesState(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "No favorites yet",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Add movies and shows to your favorites to see them here",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
