package com.example.farsilandtv.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
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
import com.example.farsilandtv.data.models.Movie
import com.example.farsilandtv.data.repository.FavoritesRepository
import com.example.farsilandtv.data.repository.PlaybackRepository
import com.example.farsilandtv.data.repository.WatchlistRepository
import com.example.farsilandtv.ui.components.GenreBadge
import com.example.farsilandtv.ui.components.MovieCard
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Feature #16: Jetpack Compose for TV - Movie Details Screen
 * Week 3 implementation
 *
 * Replaces: MovieDetailsFragment.kt and DetailsActivity.kt (movie mode)
 *
 * Features:
 * - Backdrop with overlay
 * - Movie info (title, genres, rating, year, duration)
 * - Action buttons (Play, Favorite, Watched, Watchlist)
 * - Synopsis
 * - Similar movies row
 * - D-pad navigation support
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MovieDetailsScreen(
    movie: Movie,
    onBackClick: () -> Unit,
    onPlayClick: (Movie) -> Unit,
    onMovieClick: (Movie) -> Unit = {},
    similarMovies: List<Movie> = emptyList(),
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Repositories
    val favoritesRepo = remember { FavoritesRepository(context) }
    val playbackRepo = remember { PlaybackRepository(context) }
    val watchlistRepo = remember { WatchlistRepository(context) }

    // State
    var isFavorite by remember { mutableStateOf(false) }
    var isWatched by remember { mutableStateOf(false) }
    var isInWatchlist by remember { mutableStateOf(false) }
    var synopsisExpanded by remember { mutableStateOf(false) }

    // Load initial states
    LaunchedEffect(movie.id) {
        isFavorite = favoritesRepo.isMovieFavorited(movie.id).first()
        isWatched = playbackRepo.isCompleted(movie.id, "movie").first() ?: false
        isInWatchlist = watchlistRepo.isMovieInWatchlist(movie.id)
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize()
        ) {
            // Backdrop with gradient overlay
            item {
                BackdropSection(
                    backdropUrl = movie.backdropUrl ?: movie.posterUrl,
                    onBackClick = onBackClick
                )
            }

            // Movie info overlay on backdrop
            item {
                MovieInfoSection(
                    movie = movie,
                    isFavorite = isFavorite,
                    isWatched = isWatched,
                    modifier = Modifier.padding(horizontal = 48.dp)
                )
            }

            // Action buttons
            item {
                ActionButtonsRow(
                    onPlayClick = { onPlayClick(movie) },
                    isFavorite = isFavorite,
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
                    isWatched = isWatched,
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
                    isInWatchlist = isInWatchlist,
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
                    modifier = Modifier.padding(horizontal = 48.dp, vertical = 16.dp)
                )
            }

            // Synopsis section
            item {
                SynopsisSection(
                    synopsis = movie.description,
                    expanded = synopsisExpanded,
                    onExpandClick = { synopsisExpanded = !synopsisExpanded },
                    modifier = Modifier.padding(horizontal = 48.dp, vertical = 16.dp)
                )
            }

            // Additional metadata
            item {
                MetadataSection(
                    movie = movie,
                    modifier = Modifier.padding(horizontal = 48.dp, vertical = 16.dp)
                )
            }

            // Similar movies
            if (similarMovies.isNotEmpty()) {
                item {
                    SimilarMoviesSection(
                        movies = similarMovies,
                        onMovieClick = onMovieClick,
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
            contentDescription = "Movie backdrop",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        // Gradient overlay for readability
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
private fun MovieInfoSection(
    movie: Movie,
    isFavorite: Boolean,
    isWatched: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.offset(y = (-32).dp)
    ) {
        // Title
        Text(
            text = movie.title,
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
            movie.genres.take(3).forEach { genre ->
                GenreBadge(genreName = genre)
            }

            // Rating
            movie.rating?.let { rating ->
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

            // Year and duration
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                movie.year?.let { year ->
                    Text(
                        text = year.toString(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }

                movie.runtime?.let { runtime ->
                    Text(
                        text = "${runtime}m",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }
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
            if (isWatched) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = Color(0xFF4CAF50)
                ) {
                    Text(
                        text = "Watched",
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
private fun ActionButtonsRow(
    onPlayClick: () -> Unit,
    isFavorite: Boolean,
    onFavoriteClick: () -> Unit,
    isWatched: Boolean,
    onWatchedClick: () -> Unit,
    isInWatchlist: Boolean,
    onWatchlistClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Play button (primary)
        Button(
            onClick = onPlayClick,
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
            Text("Play Movie")
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

        // Watched button
        OutlinedButton(onClick = onWatchedClick) {
            Text(if (isWatched) "Watched" else "Mark as Watched")
        }

        // Watchlist button
        OutlinedButton(onClick = onWatchlistClick) {
            Text(if (isInWatchlist) "In Watchlist" else "Add to Watchlist")
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

@Composable
private fun MetadataSection(
    movie: Movie,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        movie.director?.let { director ->
            MetadataRow(label = "Director", value = director)
        }

        if (movie.cast.isNotEmpty()) {
            MetadataRow(label = "Cast", value = movie.cast.take(5).joinToString(", "))
        }
    }
}

@Composable
private fun MetadataRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier) {
        Text(
            text = "$label: ",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun SimilarMoviesSection(
    movies: List<Movie>,
    onMovieClick: (Movie) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = "Similar Movies",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(horizontal = 48.dp, vertical = 16.dp)
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(movies) { movie ->
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
