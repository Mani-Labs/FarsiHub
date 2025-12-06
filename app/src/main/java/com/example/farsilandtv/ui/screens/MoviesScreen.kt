package com.example.farsilandtv.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import coil.compose.AsyncImage
import com.example.farsilandtv.data.models.Movie
import com.example.farsilandtv.data.repository.FavoritesRepository
import com.example.farsilandtv.data.repository.WatchlistRepository
import com.example.farsilandtv.ui.components.ContentOptionsDialog
import com.example.farsilandtv.ui.components.ContentOptionsItem
import com.example.farsilandtv.ui.components.GenreChip
import com.example.farsilandtv.ui.components.MovieCard
import com.example.farsilandtv.ui.components.MovieCardSkeleton
import com.example.farsilandtv.ui.components.SortButton
import com.example.farsilandtv.ui.viewmodel.MainViewModel
import kotlinx.coroutines.launch

/**
 * Movies Library Screen - Premium TV Experience
 * Netflix/Plex inspired design with hero banner, genre filters, and grid
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun MoviesScreen(
    favoritesRepo: FavoritesRepository,
    watchlistRepo: WatchlistRepository,
    onMovieClick: (Movie) -> Unit,
    onFilterClick: () -> Unit = {},
    onBackToSidebar: () -> Unit = {},  // Called when pressing Back/Left on first item
    modifier: Modifier = Modifier,
    viewModel: MainViewModel = viewModel()
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Data
    val favorites by favoritesRepo.getAllFavorites().collectAsState(initial = emptyList())
    val watchlistMovies by watchlistRepo.getAllWatchlistedMovies().collectAsState(initial = emptyList())

    // Filter/Sort state from ViewModel
    val selectedGenre by viewModel.movieGenreFilter.collectAsState()
    val selectedSort by viewModel.movieSortOption.collectAsState()
    val movies = viewModel.filteredMovies.collectAsLazyPagingItems()
    val gridState = rememberLazyGridState()

    // UI State - genre/sort options
    val genres = listOf("All", "Action", "Comedy", "Drama", "Horror", "Sci-Fi", "Romance", "Thriller", "Animation", "Documentary", "Family", "Fantasy", "Crime", "Adventure", "Mystery", "War")
    val sortOptions = listOf("Recent", "Year", "Rating", "A-Z")

    // Dialog state
    var showOptionsDialog by remember { mutableStateOf(false) }
    var selectedMovie by remember { mutableStateOf<Movie?>(null) }
    var selectedIsInWatchlist by remember { mutableStateOf(false) }
    var selectedIsInFavorites by remember { mutableStateOf(false) }

    // Hero banner movie - updates when user focuses different cards
    var focusedMovie by remember { mutableStateOf<Movie?>(null) }
    val heroMovie = focusedMovie ?: remember(movies.itemCount) {
        if (movies.itemCount > 0) movies[0] else null
    }

    // Back button goes back to sidebar/home
    BackHandler {
        onBackToSidebar()
    }

    val onMovieLongPress: (Movie) -> Unit = { movie ->
        coroutineScope.launch {
            selectedMovie = movie
            selectedIsInWatchlist = watchlistRepo.isMovieInWatchlist(movie.id)
            selectedIsInFavorites = favorites.any { it.contentId == "movie-${movie.id}" }
            showOptionsDialog = true
        }
    }

    // Options dialog - capture item to prevent null during recomposition
    val dialogMovie = selectedMovie
    if (showOptionsDialog && dialogMovie != null) {
        ContentOptionsDialog(
            item = ContentOptionsItem.MovieItem(dialogMovie),
            onDismiss = {
                showOptionsDialog = false
                selectedMovie = null
            },
            isInWatchlist = selectedIsInWatchlist,
            isInFavorites = selectedIsInFavorites,
            isMonitored = false,
            onToggleWatchlist = {
                coroutineScope.launch {
                    if (selectedIsInWatchlist) {
                        watchlistRepo.removeMovieFromWatchlist(dialogMovie.id)
                    } else {
                        watchlistRepo.addMovieToWatchlist(dialogMovie)
                    }
                }
            },
            onToggleFavorites = {
                coroutineScope.launch {
                    if (selectedIsInFavorites) {
                        favoritesRepo.removeMovieFromFavorites(dialogMovie.id)
                    } else {
                        favoritesRepo.addMovieToFavorites(dialogMovie)
                    }
                }
            }
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0D0D0D))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Hero Banner - shows focused movie or first movie
            if (heroMovie != null && movies.loadState.refresh !is LoadState.Loading) {
                val isHeroInWatchlist = watchlistMovies.any { it.id == heroMovie.id }
                HeroBanner(
                    movie = heroMovie,
                    isInWatchlist = isHeroInWatchlist,
                    onClick = { onMovieClick(heroMovie) },
                    onPlayClick = { onMovieClick(heroMovie) },
                    onAddToListClick = { onMovieLongPress(heroMovie) }
                )
            }

            // Filter Bar
            FilterBar(
                genres = genres,
                selectedGenre = selectedGenre ?: "All",
                onGenreSelected = { genre ->
                    viewModel.setMovieGenreFilter(if (genre == "All") null else genre)
                },
                sortOptions = sortOptions,
                selectedSort = selectedSort,
                onSortSelected = { sort ->
                    viewModel.setMovieSortOption(sort)
                },
                itemCount = movies.itemCount
            )

            // Content Grid
            when (movies.loadState.refresh) {
                is LoadState.Loading -> {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(4),
                        contentPadding = PaddingValues(horizontal = 48.dp, vertical = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(16) { MovieCardSkeleton() }
                    }
                }

                is LoadState.Error -> {
                    ErrorState(
                        message = "Failed to load movies",
                        onRetry = { movies.retry() }
                    )
                }

                else -> {
                    if (movies.itemCount == 0) {
                        EmptyState(message = "No movies found")
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(4),
                            state = gridState,
                            contentPadding = PaddingValues(
                                start = 48.dp, end = 48.dp,
                                top = 16.dp, bottom = 48.dp
                            ),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalArrangement = Arrangement.spacedBy(20.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(
                                count = movies.itemCount,
                                key = { index -> movies[index]?.id ?: index }
                            ) { index ->
                                val movie = movies[index]
                                val isLeftmostColumn = index % 4 == 0  // 4-column grid
                                if (movie != null) {
                                    MovieCard(
                                        movie = movie,
                                        onClick = { onMovieClick(movie) },
                                        isFavorite = favorites.any { it.contentId == "movie-${movie.id}" },
                                        onLongClick = { onMovieLongPress(movie) },
                                        modifier = Modifier
                                            .onFocusChanged { focusState ->
                                                if (focusState.isFocused) {
                                                    focusedMovie = movie
                                                }
                                            }
                                            .onPreviewKeyEvent { keyEvent ->
                                                if (keyEvent.type == KeyEventType.KeyDown &&
                                                    keyEvent.key == Key.DirectionLeft &&
                                                    isLeftmostColumn
                                                ) {
                                                    onBackToSidebar()
                                                    true
                                                } else {
                                                    false
                                                }
                                            }
                                    )
                                } else {
                                    MovieCardSkeleton()
                                }
                            }

                            if (movies.loadState.append is LoadState.Loading) {
                                items(6) { MovieCardSkeleton() }
                            }
                        }
                    }
                }
            }
        }

        // Loading overlay
        if (movies.loadState.append is LoadState.Loading) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp)
            ) {
                CircularProgressIndicator(
                    color = Color(0xFFF59E0B), // FarsilandAmber
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}

/**
 * Hero Banner - Featured movie with backdrop
 */
@Composable
private fun HeroBanner(
    movie: Movie,
    isInWatchlist: Boolean,
    onClick: () -> Unit,
    onPlayClick: () -> Unit,
    onAddToListClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(280.dp)
    ) {
        // Backdrop image
        AsyncImage(
            model = movie.backdropUrl ?: movie.posterUrl,
            contentDescription = movie.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // Gradient overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xFF0D0D0D),
                            Color(0xCC0D0D0D),
                            Color.Transparent
                        )
                    )
                )
        )

        // Bottom gradient
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color(0xFF0D0D0D))
                    )
                )
        )

        // Content
        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 48.dp, end = 200.dp)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.Center
        ) {
            // Featured label
            Surface(
                color = Color(0xFFF59E0B), // FarsilandAmber
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    text = "FEATURED",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Title
            Text(
                text = movie.title,
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Meta info
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                movie.year?.let {
                    Text(text = it.toString(), color = Color(0xFFB0B0B0), fontSize = 14.sp)
                }
                movie.rating?.let {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "★", color = Color(0xFFFFD700), fontSize = 14.sp)
                        Text(text = " $it", color = Color(0xFFB0B0B0), fontSize = 14.sp)
                    }
                }
                movie.runtime?.let {
                    Text(text = "${it}m", color = Color(0xFFB0B0B0), fontSize = 14.sp)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Genres
            if (movie.genres.isNotEmpty()) {
                Text(
                    text = movie.genres.take(3).joinToString(" • "),
                    color = Color(0xFF888888),
                    fontSize = 13.sp
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action buttons
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HeroButton(
                    text = "Play",
                    isPrimary = true,
                    onClick = onPlayClick
                )
                HeroButton(
                    text = if (isInWatchlist) "✓ In List" else "+ My List",
                    isPrimary = false,
                    onClick = onAddToListClick
                )
            }
        }
    }
}

@Composable
private fun HeroButton(
    text: String,
    isPrimary: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.05f else 1f,
        animationSpec = tween(150),
        label = "scale"
    )
    val backgroundColor by animateColorAsState(
        targetValue = when {
            isFocused -> Color(0xFFF59E0B) // FarsilandAmber
            isPrimary -> Color.White
            else -> Color(0xFF1A1A24) // SurfaceDark
        },
        animationSpec = tween(150),
        label = "bg"
    )
    val textColor = when {
        isFocused -> Color.White
        isPrimary -> Color.Black
        else -> Color.White
    }

    Surface(
        onClick = onClick,
        modifier = modifier
            .scale(scale)
            .onFocusChanged { isFocused = it.isFocused },
        shape = RoundedCornerShape(6.dp),
        color = backgroundColor
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
            color = textColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/**
 * Filter Bar - Genre chips, sort, search
 */
@Composable
private fun FilterBar(
    genres: List<String>,
    selectedGenre: String,
    onGenreSelected: (String) -> Unit,
    sortOptions: List<String>,
    selectedSort: String,
    onSortSelected: (String) -> Unit,
    itemCount: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 48.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Genre chips
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(genres) { genre ->
                GenreChip(
                    text = genre,
                    isSelected = genre == selectedGenre,
                    onClick = { onGenreSelected(genre) }
                )
            }
        }

        Spacer(modifier = Modifier.width(24.dp))

        // Right side controls
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Count
            Text(
                text = "$itemCount movies",
                color = Color(0xFF666666),
                fontSize = 14.sp
            )

            // Sort dropdown
            SortButton(
                options = sortOptions,
                selected = selectedSort,
                onSelected = onSortSelected
            )
        }
    }
}

@Composable
private fun ErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = message,
                color = Color(0xFFFF6B6B),
                fontSize = 18.sp
            )
            Spacer(modifier = Modifier.height(16.dp))
            var isFocused by remember { mutableStateOf(false) }
            Surface(
                onClick = onRetry,
                modifier = Modifier.onFocusChanged { isFocused = it.isFocused },
                shape = RoundedCornerShape(8.dp),
                color = if (isFocused) Color(0xFFF59E0B) else Color(0xFF1A1A24) // Amber / SurfaceDark
            ) {
                Text(
                    text = "Retry",
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                    color = Color.White
                )
            }
        }
    }
}

@Composable
private fun EmptyState(
    message: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            color = Color(0xFF666666),
            fontSize = 18.sp
        )
    }
}
