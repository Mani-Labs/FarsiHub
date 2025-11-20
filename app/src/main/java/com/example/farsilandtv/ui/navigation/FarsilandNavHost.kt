package com.example.farsilandtv.ui.navigation

import android.content.Intent
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.farsilandtv.VideoPlayerActivity
import com.example.farsilandtv.data.models.Episode
import com.example.farsilandtv.data.models.Movie
import com.example.farsilandtv.data.models.Series
import com.example.farsilandtv.data.repository.ContentRepository
import com.example.farsilandtv.ui.screens.*
import com.example.farsilandtv.ui.viewmodel.MainViewModel
import kotlinx.coroutines.launch

/**
 * Feature #16: Jetpack Compose for TV - Navigation Host
 * Week 3 implementation
 *
 * Central navigation controller for all Compose screens
 * Handles navigation between:
 * - Home
 * - Movies
 * - Shows
 * - Search
 * - Favorites
 * - Movie Details
 * - Series Details
 */
@Composable
fun FarsilandNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val contentRepository = remember { ContentRepository.getInstance(context) }
    val scope = rememberCoroutineScope()

    // Shared ViewModel for content
    val viewModel: MainViewModel = viewModel()

    // Navigation handlers
    val onMovieClick: (Movie) -> Unit = { movie ->
        navController.navigate(Screen.MovieDetails.createRoute(movie.id))
    }

    val onSeriesClick: (Series) -> Unit = { series ->
        navController.navigate(Screen.SeriesDetails.createRoute(series.id))
    }

    val onPlayMovie: (Movie) -> Unit = { movie ->
        val intent = Intent(context, VideoPlayerActivity::class.java).apply {
            putExtra("CONTENT_TYPE", "movie")
            putExtra("CONTENT_ID", movie.id)
            putExtra("CONTENT_TITLE", movie.title)
            putExtra("CONTENT_URL", movie.farsilandUrl)
            putExtra("CONTENT_POSTER_URL", movie.posterUrl)
        }
        context.startActivity(intent)
    }

    val onPlayEpisode: (Episode) -> Unit = { episode ->
        val intent = Intent(context, VideoPlayerActivity::class.java).apply {
            putExtra("CONTENT_TYPE", "episode")
            putExtra("CONTENT_ID", episode.id)
            putExtra("CONTENT_TITLE", "${episode.formattedNumber}: ${episode.title}")
            putExtra("CONTENT_URL", episode.farsilandUrl)
            putExtra("SERIES_ID", episode.seriesId ?: 0)
            putExtra("EPISODE_SEASON", episode.season)
            putExtra("EPISODE_NUMBER", episode.episode)
            putExtra("CONTENT_POSTER_URL", episode.thumbnailUrl)
        }
        context.startActivity(intent)
    }

    NavHost(
        navController = navController,
        startDestination = Screen.Home.route,
        modifier = modifier
    ) {
        // Home screen
        composable(Screen.Home.route) {
            HomeScreen(
                onMovieClick = onMovieClick,
                onSeriesClick = onSeriesClick,
                onEpisodeClick = onPlayEpisode,
                onFeaturedClick = { featured ->
                    when (featured) {
                        is com.example.farsilandtv.data.models.FeaturedContent.FeaturedMovie -> onMovieClick(featured.movie)
                        is com.example.farsilandtv.data.models.FeaturedContent.FeaturedSeries -> onSeriesClick(featured.series)
                    }
                }
            )
        }

        // Movies screen
        composable(Screen.Movies.route) {
            MoviesScreen(
                onMovieClick = onMovieClick,
                onSearchClick = { navController.navigate(Screen.Search.route) },
                onFilterClick = { /* TODO: Show genre filter dialog */ }
            )
        }

        // Shows screen
        composable(Screen.Shows.route) {
            ShowsScreen(
                onSeriesClick = onSeriesClick,
                onSearchClick = { navController.navigate(Screen.Search.route) },
                onFilterClick = { /* TODO: Show genre filter dialog */ }
            )
        }

        // Search screen
        composable(Screen.Search.route) {
            SearchScreen(
                onMovieClick = onMovieClick,
                onSeriesClick = onSeriesClick
            )
        }

        // Favorites screen
        composable(Screen.Favorites.route) {
            FavoritesScreen(
                onMovieClick = onMovieClick,
                onSeriesClick = onSeriesClick
            )
        }

        // Movie details screen
        composable(
            route = Screen.MovieDetails.route,
            arguments = listOf(
                navArgument(Screen.MovieDetails.ARG_MOVIE_ID) {
                    type = NavType.IntType
                }
            )
        ) { backStackEntry ->
            val movieId = backStackEntry.arguments?.getInt(Screen.MovieDetails.ARG_MOVIE_ID) ?: 0

            // Load movie details
            var movie by remember { mutableStateOf<Movie?>(null) }
            var similarMovies by remember { mutableStateOf<List<Movie>>(emptyList()) }

            LaunchedEffect(movieId) {
                scope.launch {
                    // TODO: Load movie from repository by ID
                    // For now, create a placeholder
                    // In production, you'd use: contentRepository.getMovieById(movieId)
                }
            }

            movie?.let {
                MovieDetailsScreen(
                    movie = it,
                    onBackClick = { navController.popBackStack() },
                    onPlayClick = onPlayMovie,
                    onMovieClick = onMovieClick,
                    similarMovies = similarMovies
                )
            }
        }

        // Series details screen
        composable(
            route = Screen.SeriesDetails.route,
            arguments = listOf(
                navArgument(Screen.SeriesDetails.ARG_SERIES_ID) {
                    type = NavType.IntType
                }
            )
        ) { backStackEntry ->
            val seriesId = backStackEntry.arguments?.getInt(Screen.SeriesDetails.ARG_SERIES_ID) ?: 0

            // Load series details
            var series by remember { mutableStateOf<Series?>(null) }
            var episodesBySeason by remember { mutableStateOf<Map<Int, List<Episode>>>(emptyMap()) }
            var similarSeries by remember { mutableStateOf<List<Series>>(emptyList()) }

            LaunchedEffect(seriesId) {
                scope.launch {
                    // TODO: Load series from repository by ID
                    // TODO: Load episodes for series
                    // For now, create placeholders
                    // In production, you'd use:
                    // - contentRepository.getSeriesById(seriesId)
                    // - contentRepository.getEpisodesForSeries(seriesId)
                }
            }

            series?.let { s ->
                SeriesDetailsScreen(
                    series = s,
                    episodesBySeason = episodesBySeason,
                    onBackClick = { navController.popBackStack() },
                    onPlayEpisode = onPlayEpisode,
                    onSeriesClick = onSeriesClick,
                    similarSeries = similarSeries
                )
            }
        }
    }
}
