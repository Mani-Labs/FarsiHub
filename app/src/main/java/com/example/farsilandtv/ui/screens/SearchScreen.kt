package com.example.farsilandtv.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.farsilandtv.data.models.Movie
import com.example.farsilandtv.data.models.Series
import com.example.farsilandtv.data.repository.FavoritesRepository
import com.example.farsilandtv.data.repository.WatchlistRepository
import com.example.farsilandtv.ui.components.ContentOptionsDialog
import com.example.farsilandtv.ui.components.ContentOptionsItem
import com.example.farsilandtv.ui.components.MovieCard
import com.example.farsilandtv.ui.components.SeriesCard
import com.example.farsilandtv.ui.viewmodel.MainViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Feature #16: Jetpack Compose for TV - Search Screen
 * Week 2 Migration - Replaces SearchActivity
 *
 * Features:
 * - SearchBar component at top
 * - Results grouped by source (Farsiland, FarsiPlex, Namakade)
 * - Real-time search with 300ms debounce
 * - Loading indicator during search
 * - Mixed results (movies + series)
 * - Empty state when no results
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onMovieClick: (Movie) -> Unit,
    onSeriesClick: (Series) -> Unit,
    onBackToSidebar: () -> Unit = {},
    modifier: Modifier = Modifier,
    initialQuery: String = "",
    viewModel: MainViewModel = viewModel()
) {
    val context = LocalContext.current
    // FIXED: Use singleton getInstance() and key by context
    val favoritesRepo = remember(context) { FavoritesRepository.getInstance(context) }
    val watchlistRepo = remember(context) { WatchlistRepository.getInstance(context) }

    // Collect favorites and monitored series
    val favorites by favoritesRepo.getAllFavorites()
        .collectAsState(initial = emptyList())
    val monitoredSeries by watchlistRepo.getAllMonitoredSeries()
        .collectAsState(initial = emptyList())

    // Search state (initialize with initialQuery for voice search support)
    var searchQuery by remember { mutableStateOf(initialQuery) }
    var debouncedQuery by remember { mutableStateOf(initialQuery) }
    var isSearching by remember { mutableStateOf(false) }
    val searchResults by viewModel.search(debouncedQuery).observeAsState(emptyList())

    val focusRequester = remember { FocusRequester() }
    val coroutineScope = rememberCoroutineScope()

    // Dialog state for long-press options
    var showOptionsDialog by remember { mutableStateOf(false) }
    var selectedOptionsItem by remember { mutableStateOf<ContentOptionsItem?>(null) }
    var selectedItemIsInWatchlist by remember { mutableStateOf(false) }
    var selectedItemIsInFavorites by remember { mutableStateOf(false) }
    var selectedItemIsMonitored by remember { mutableStateOf(false) }

    // Long-press handler for movies
    val onMovieLongPress: (Movie) -> Unit = { movie ->
        coroutineScope.launch {
            selectedOptionsItem = ContentOptionsItem.MovieItem(movie)
            selectedItemIsInWatchlist = watchlistRepo.isMovieInWatchlist(movie.id)
            selectedItemIsInFavorites = favorites.any { it.contentId == "movie-${movie.id}" }
            selectedItemIsMonitored = false
            showOptionsDialog = true
        }
    }

    // Long-press handler for series
    val onSeriesLongPress: (Series) -> Unit = { series ->
        coroutineScope.launch {
            selectedOptionsItem = ContentOptionsItem.SeriesItem(series)
            selectedItemIsInWatchlist = false
            selectedItemIsInFavorites = favorites.any { it.contentId == "series-${series.id}" }
            selectedItemIsMonitored = monitoredSeries.any { it.id == series.id }
            showOptionsDialog = true
        }
    }

    // Debounce search query (300ms)
    LaunchedEffect(searchQuery) {
        if (searchQuery.isNotEmpty()) {
            isSearching = true
        }
        delay(300) // 300ms debounce
        debouncedQuery = searchQuery
    }

    // Stop loading when results arrive
    LaunchedEffect(searchResults) {
        if (debouncedQuery.isNotEmpty()) {
            // Small delay to ensure smooth transition
            delay(100)
            isSearching = false
        }
    }

    // Auto-focus search field on screen open
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    // Show options dialog when item is long-pressed
    if (showOptionsDialog && selectedOptionsItem != null) {
        ContentOptionsDialog(
            item = selectedOptionsItem!!,
            onDismiss = {
                showOptionsDialog = false
                selectedOptionsItem = null
            },
            isInWatchlist = selectedItemIsInWatchlist,
            isInFavorites = selectedItemIsInFavorites,
            isMonitored = selectedItemIsMonitored,
            onToggleWatchlist = {
                coroutineScope.launch {
                    val item = selectedOptionsItem
                    if (item is ContentOptionsItem.MovieItem) {
                        if (selectedItemIsInWatchlist) {
                            watchlistRepo.removeMovieFromWatchlist(item.movie.id)
                        } else {
                            watchlistRepo.addMovieToWatchlist(item.movie)
                        }
                    }
                }
            },
            onToggleFavorites = {
                coroutineScope.launch {
                    when (val item = selectedOptionsItem) {
                        is ContentOptionsItem.MovieItem -> {
                            if (selectedItemIsInFavorites) {
                                favoritesRepo.removeMovieFromFavorites(item.movie.id)
                            } else {
                                favoritesRepo.addMovieToFavorites(item.movie)
                            }
                        }
                        is ContentOptionsItem.SeriesItem -> {
                            if (selectedItemIsInFavorites) {
                                favoritesRepo.removeSeriesFromFavorites(item.series.id)
                            } else {
                                favoritesRepo.addSeriesToFavorites(item.series)
                            }
                        }
                        null -> {}
                    }
                }
            },
            onToggleMonitored = {
                coroutineScope.launch {
                    val item = selectedOptionsItem
                    if (item is ContentOptionsItem.SeriesItem) {
                        if (selectedItemIsMonitored) {
                            watchlistRepo.removeSeriesFromMonitored(item.series.id)
                        } else {
                            watchlistRepo.addSeriesToMonitored(item.series)
                        }
                    }
                }
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 48.dp, vertical = 32.dp)
            .onPreviewKeyEvent { keyEvent ->
                // Handle back button to open sidebar
                if (keyEvent.type == KeyEventType.KeyDown &&
                    keyEvent.key == Key.Back
                ) {
                    onBackToSidebar()
                    true
                } else {
                    false
                }
            }
    ) {
        // Search bar with loading indicator
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SearchBar(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                onClearQuery = {
                    searchQuery = ""
                    isSearching = false
                },
                focusRequester = focusRequester,
                isSearching = isSearching,
                modifier = Modifier.weight(1f)
            )
        }

        // Results or states
        when {
            debouncedQuery.isEmpty() -> {
                // Empty state - no search query yet
                EmptySearchState(message = "Enter a search query to find movies and TV shows")
            }
            isSearching -> {
                // Loading state
                SearchLoadingState()
            }
            searchResults.isEmpty() -> {
                // No results found
                EmptySearchState(message = "No results found for \"$debouncedQuery\"")
            }
            else -> {
                // Display results grouped by source
                GroupedSearchResults(
                    results = searchResults,
                    favorites = favorites,
                    onMovieClick = onMovieClick,
                    onSeriesClick = onSeriesClick,
                    onMovieLongClick = onMovieLongPress,
                    onSeriesLongClick = onSeriesLongPress
                )
            }
        }
    }
}

/**
 * Search bar with clear button and loading indicator
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClearQuery: () -> Unit,
    focusRequester: FocusRequester,
    isSearching: Boolean,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.focusRequester(focusRequester),
        placeholder = {
            Text("Search movies and TV shows...")
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = "Search"
            )
        },
        trailingIcon = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Loading indicator
                AnimatedVisibility(
                    visible = isSearching,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                // Clear button
                if (query.isNotEmpty()) {
                    IconButton(onClick = onClearQuery) {
                        Icon(
                            imageVector = Icons.Filled.Clear,
                            contentDescription = "Clear"
                        )
                    }
                }
            }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            imeAction = ImeAction.Search
        ),
        keyboardActions = KeyboardActions(
            onSearch = { /* Search is already triggered by debounce */ }
        ),
        colors = TextFieldDefaults.outlinedTextFieldColors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline
        )
    )
}

/**
 * Loading state with spinner
 */
@Composable
private fun SearchLoadingState(
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
            CircularProgressIndicator(
                modifier = Modifier.size(48.dp),
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Searching...",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Get source name from item's URL
 */
private fun getSourceName(item: Any): String {
    val url = when (item) {
        is Movie -> item.farsilandUrl
        is Series -> item.farsilandUrl
        else -> null
    } ?: return "Other"

    return when {
        url.contains("farsiland.com", ignoreCase = true) -> "Farsiland"
        url.contains("farsiplex.com", ignoreCase = true) -> "FarsiPlex"
        url.contains("namakade.com", ignoreCase = true) -> "Namakade"
        else -> "Other"
    }
}

/**
 * Normalize title for deduplication
 */
private fun normalizeTitle(title: String): String {
    return title.lowercase()
        .replace(Regex("[^a-z0-9\\u0600-\\u06FF]"), "") // Keep alphanumeric and Persian chars
}

/**
 * Data class for grouped results by source and content type
 */
private data class SourceContentGroup(
    val source: String,
    val movies: List<Movie>,
    val series: List<Series>
)

/**
 * Search results grouped by source AND content type (Movies/TV Shows)
 */
@Composable
private fun GroupedSearchResults(
    results: List<Any>,
    favorites: List<com.example.farsilandtv.data.database.Favorite>,
    onMovieClick: (Movie) -> Unit,
    onSeriesClick: (Series) -> Unit,
    onMovieLongClick: ((Movie) -> Unit)? = null,
    onSeriesLongClick: ((Series) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    // Group results by source, then by type, with deduplication
    val groupedResults = remember(results) {
        val sourceGroups = mutableListOf<SourceContentGroup>()

        // Group by source first
        val bySource = results.groupBy { getSourceName(it) }

        // Process each source
        val sortedSources = bySource.keys.sortedBy { source ->
            when (source) {
                "Farsiland" -> 0
                "FarsiPlex" -> 1
                "Namakade" -> 2
                else -> 3
            }
        }

        for (source in sortedSources) {
            val items = bySource[source] ?: continue

            // Separate movies and series
            val movies = items.filterIsInstance<Movie>()
            val series = items.filterIsInstance<Series>()

            // Deduplicate movies by normalized title
            val seenMovieTitles = mutableSetOf<String>()
            val dedupedMovies = movies.filter { movie ->
                val normalized = normalizeTitle(movie.title)
                if (normalized in seenMovieTitles) {
                    false
                } else {
                    seenMovieTitles.add(normalized)
                    true
                }
            }

            // Deduplicate series by normalized title
            val seenSeriesTitles = mutableSetOf<String>()
            val dedupedSeries = series.filter { s ->
                val normalized = normalizeTitle(s.title)
                if (normalized in seenSeriesTitles) {
                    false
                } else {
                    seenSeriesTitles.add(normalized)
                    true
                }
            }

            if (dedupedMovies.isNotEmpty() || dedupedSeries.isNotEmpty()) {
                sourceGroups.add(SourceContentGroup(source, dedupedMovies, dedupedSeries))
            }
        }

        sourceGroups
    }

    LazyColumn(
        contentPadding = PaddingValues(bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier.fillMaxSize()
    ) {
        groupedResults.forEach { group ->
            // Source header
            item(key = "header_${group.source}") {
                SourceHeader(
                    sourceName = group.source,
                    movieCount = group.movies.size,
                    seriesCount = group.series.size
                )
            }

            // Movies row (if any)
            if (group.movies.isNotEmpty()) {
                item(key = "movies_${group.source}") {
                    ContentTypeRow(
                        title = "Movies",
                        count = group.movies.size
                    )
                }
                item(key = "movies_row_${group.source}") {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(start = 24.dp, end = 24.dp)
                    ) {
                        items(group.movies, key = { "movie_${group.source}_${it.id}" }) { movie ->
                            MovieCard(
                                movie = movie,
                                onClick = { onMovieClick(movie) },
                                isFavorite = favorites.any { it.contentId == "movie-${movie.id}" },
                                onLongClick = onMovieLongClick?.let { { it(movie) } },
                                modifier = Modifier.width(160.dp)
                            )
                        }
                    }
                }
            }

            // TV Shows row (if any)
            if (group.series.isNotEmpty()) {
                item(key = "series_${group.source}") {
                    ContentTypeRow(
                        title = "TV Shows",
                        count = group.series.size
                    )
                }
                item(key = "series_row_${group.source}") {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(start = 24.dp, end = 24.dp)
                    ) {
                        items(group.series, key = { "series_${group.source}_${it.id}" }) { series ->
                            SeriesCard(
                                series = series,
                                onClick = { onSeriesClick(series) },
                                isFavorite = favorites.any { it.contentId == "series-${series.id}" },
                                onLongClick = onSeriesLongClick?.let { { it(series) } },
                                modifier = Modifier.width(160.dp)
                            )
                        }
                    }
                }
            }

            // Spacer between sources
            item(key = "spacer_${group.source}") {
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

/**
 * Content type sub-header (Movies / TV Shows)
 */
@Composable
private fun ContentTypeRow(
    title: String,
    count: Int,
    modifier: Modifier = Modifier
) {
    Text(
        text = "$title ($count)",
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(start = 24.dp, top = 8.dp, bottom = 4.dp)
    )
}

/**
 * Source section header
 */
@Composable
private fun SourceHeader(
    sourceName: String,
    movieCount: Int,
    seriesCount: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 24.dp, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Source badge/icon based on source
        // Farsiland = Primary (red/orange), FarsiPlex = Blue, Namakade = Green
        val sourceColor = when (sourceName) {
            "Farsiland" -> MaterialTheme.colorScheme.primary
            "FarsiPlex" -> Color(0xFF2196F3) // Blue
            "Namakade" -> Color(0xFF4CAF50) // Green
            else -> MaterialTheme.colorScheme.outline
        }

        Surface(
            color = sourceColor.copy(alpha = 0.2f),
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.padding(end = 12.dp)
        ) {
            Text(
                text = sourceName,
                style = MaterialTheme.typography.titleMedium,
                color = sourceColor,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }

        // Show counts
        val totalCount = movieCount + seriesCount
        Text(
            text = "$totalCount results",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Empty state - shown when no results or no query
 */
@Composable
private fun EmptySearchState(
    message: String,
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
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
