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
import androidx.compose.foundation.clickable
import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Search
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
import com.example.farsilandtv.data.repository.SearchRepository
import com.example.farsilandtv.data.repository.WatchlistRepository
import com.example.farsilandtv.ui.components.ContentOptionsDialog
import com.example.farsilandtv.ui.components.ContentOptionsItem
import com.example.farsilandtv.ui.components.MovieCard
import com.example.farsilandtv.ui.components.MovieCardSkeleton
import com.example.farsilandtv.ui.components.SeriesCard
import com.example.farsilandtv.ui.components.SeriesCardSkeleton
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
    favoritesRepo: FavoritesRepository,
    watchlistRepo: WatchlistRepository,
    searchRepo: SearchRepository,
    onMovieClick: (Movie) -> Unit,
    onSeriesClick: (Series) -> Unit,
    onBackToSidebar: () -> Unit = {},
    modifier: Modifier = Modifier,
    initialQuery: String = "",
    viewModel: MainViewModel = viewModel()
) {
    val context = LocalContext.current

    // Collect favorites and monitored series
    val favorites by favoritesRepo.getAllFavorites()
        .collectAsState(initial = emptyList())
    val monitoredSeries by watchlistRepo.getAllMonitoredSeries()
        .collectAsState(initial = emptyList())

    // Recent searches for suggestions
    val recentSearches by searchRepo.getRecentSearches(10)
        .collectAsState(initial = emptyList())

    // Search state (initialize with initialQuery for voice search support)
    var searchQuery by remember { mutableStateOf(initialQuery) }
    var debouncedQuery by remember { mutableStateOf(initialQuery) }
    var isSearching by remember { mutableStateOf(false) }
    val searchResults by viewModel.search(debouncedQuery).observeAsState(emptyList())

    val focusRequester = remember { FocusRequester() }
    val coroutineScope = rememberCoroutineScope()

    // Voice search launcher
    val voiceSearchLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spokenText = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
            if (!spokenText.isNullOrEmpty()) {
                searchQuery = spokenText
            }
        }
    }

    // Filter state
    var selectedGenre by remember { mutableStateOf<String?>(null) }
    var selectedYear by remember { mutableStateOf<String?>(null) }
    var selectedRating by remember { mutableStateOf<String?>(null) }
    var showGenreMenu by remember { mutableStateOf(false) }
    var showYearMenu by remember { mutableStateOf(false) }
    var showRatingMenu by remember { mutableStateOf(false) }

    // Filter options
    val genres = listOf("Action", "Comedy", "Drama", "Horror", "Romance", "Sci-Fi", "Thriller")
    val years = listOf("2024", "2023", "2022", "2021", "2020", "2019", "2018", "2010s", "2000s", "90s")
    val ratings = listOf("9+", "8+", "7+", "6+", "5+")

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

    // Stop loading when results arrive and save to history if results found
    LaunchedEffect(searchResults) {
        if (debouncedQuery.isNotEmpty()) {
            // Small delay to ensure smooth transition
            delay(100)
            isSearching = false

            // Save search to history only when meaningful results are found
            if (searchResults.isNotEmpty() && debouncedQuery.trim().length >= 2) {
                searchRepo.saveSearch(debouncedQuery.trim())
            }
        }
    }

    // Auto-focus search field on screen open
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    // Show options dialog when item is long-pressed - capture item to prevent null during recomposition
    val dialogItem = selectedOptionsItem
    if (showOptionsDialog && dialogItem != null) {
        ContentOptionsDialog(
            item = dialogItem,
            onDismiss = {
                showOptionsDialog = false
                selectedOptionsItem = null
            },
            isInWatchlist = selectedItemIsInWatchlist,
            isInFavorites = selectedItemIsInFavorites,
            isMonitored = selectedItemIsMonitored,
            onToggleWatchlist = {
                coroutineScope.launch {
                    if (dialogItem is ContentOptionsItem.MovieItem) {
                        if (selectedItemIsInWatchlist) {
                            watchlistRepo.removeMovieFromWatchlist(dialogItem.movie.id)
                        } else {
                            watchlistRepo.addMovieToWatchlist(dialogItem.movie)
                        }
                    }
                }
            },
            onToggleFavorites = {
                coroutineScope.launch {
                    when (dialogItem) {
                        is ContentOptionsItem.MovieItem -> {
                            if (selectedItemIsInFavorites) {
                                favoritesRepo.removeMovieFromFavorites(dialogItem.movie.id)
                            } else {
                                favoritesRepo.addMovieToFavorites(dialogItem.movie)
                            }
                        }
                        is ContentOptionsItem.SeriesItem -> {
                            if (selectedItemIsInFavorites) {
                                favoritesRepo.removeSeriesFromFavorites(dialogItem.series.id)
                            } else {
                                favoritesRepo.addSeriesToFavorites(dialogItem.series)
                            }
                        }
                    }
                }
            },
            onToggleMonitored = {
                coroutineScope.launch {
                    if (dialogItem is ContentOptionsItem.SeriesItem) {
                        if (selectedItemIsMonitored) {
                            watchlistRepo.removeSeriesFromMonitored(dialogItem.series.id)
                        } else {
                            watchlistRepo.addSeriesToMonitored(dialogItem.series)
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
        // Search bar with mic button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
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

            // Voice search button (uses outlined search as mic icon not in base icons)
            FilledTonalIconButton(
                onClick = {
                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        putExtra(RecognizerIntent.EXTRA_PROMPT, "Search movies and shows...")
                    }
                    try {
                        voiceSearchLauncher.launch(intent)
                    } catch (e: Exception) {
                        // Voice recognition not available
                    }
                },
                modifier = Modifier.size(56.dp)
            ) {
                // Using "🎤" text as mic icon (material icons base doesn't include Mic)
                Text(
                    text = "🎤",
                    style = MaterialTheme.typography.titleLarge
                )
            }
        }

        // Filter chips row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Genre filter
            FilterChipWithMenu(
                label = selectedGenre ?: "Genre",
                isSelected = selectedGenre != null,
                expanded = showGenreMenu,
                onExpandChange = { showGenreMenu = it },
                options = genres,
                onOptionSelected = { genre ->
                    selectedGenre = if (selectedGenre == genre) null else genre
                    showGenreMenu = false
                },
                onClear = { selectedGenre = null }
            )

            // Year filter
            FilterChipWithMenu(
                label = selectedYear ?: "Year",
                isSelected = selectedYear != null,
                expanded = showYearMenu,
                onExpandChange = { showYearMenu = it },
                options = years,
                onOptionSelected = { year ->
                    selectedYear = if (selectedYear == year) null else year
                    showYearMenu = false
                },
                onClear = { selectedYear = null }
            )

            // Rating filter
            FilterChipWithMenu(
                label = selectedRating ?: "Rating",
                isSelected = selectedRating != null,
                expanded = showRatingMenu,
                onExpandChange = { showRatingMenu = it },
                options = ratings,
                onOptionSelected = { rating ->
                    selectedRating = if (selectedRating == rating) null else rating
                    showRatingMenu = false
                },
                onClear = { selectedRating = null }
            )

            // Clear all filters
            if (selectedGenre != null || selectedYear != null || selectedRating != null) {
                TextButton(
                    onClick = {
                        selectedGenre = null
                        selectedYear = null
                        selectedRating = null
                    }
                ) {
                    Text("Clear filters", color = MaterialTheme.colorScheme.error)
                }
            }
        }

        // Apply filters to search results
        val filteredResults = remember(searchResults, selectedGenre, selectedYear, selectedRating) {
            searchResults.filter { item ->
                val matchesGenre = selectedGenre == null || when (item) {
                    is Movie -> item.genres.any { it.contains(selectedGenre!!, ignoreCase = true) }
                    is Series -> item.genres.any { it.contains(selectedGenre!!, ignoreCase = true) }
                    else -> true
                }
                val matchesYear = selectedYear == null || when (item) {
                    is Movie -> {
                        val year = item.year ?: 0
                        when (selectedYear) {
                            "2024" -> year == 2024
                            "2023" -> year == 2023
                            "2022" -> year == 2022
                            "2021" -> year == 2021
                            "2020" -> year == 2020
                            "2019" -> year == 2019
                            "2018" -> year == 2018
                            "2010s" -> year in 2010..2019
                            "2000s" -> year in 2000..2009
                            "90s" -> year in 1990..1999
                            else -> true
                        }
                    }
                    is Series -> {
                        val year = item.year ?: 0
                        when (selectedYear) {
                            "2024" -> year == 2024
                            "2023" -> year == 2023
                            "2022" -> year == 2022
                            "2021" -> year == 2021
                            "2020" -> year == 2020
                            "2019" -> year == 2019
                            "2018" -> year == 2018
                            "2010s" -> year in 2010..2019
                            "2000s" -> year in 2000..2009
                            "90s" -> year in 1990..1999
                            else -> true
                        }
                    }
                    else -> true
                }
                val matchesRating = selectedRating == null || when (item) {
                    is Movie -> {
                        val rating = item.rating ?: 0f
                        val minRating = selectedRating!!.replace("+", "").toFloatOrNull() ?: 0f
                        rating >= minRating
                    }
                    is Series -> {
                        val rating = item.rating ?: 0f
                        val minRating = selectedRating!!.replace("+", "").toFloatOrNull() ?: 0f
                        rating >= minRating
                    }
                    else -> true
                }
                matchesGenre && matchesYear && matchesRating
            }
        }

        // Results or states
        when {
            debouncedQuery.isEmpty() -> {
                // Show recent searches when no query
                RecentSearchesState(
                    recentSearches = recentSearches,
                    onSuggestionClick = { suggestion ->
                        searchQuery = suggestion
                    },
                    onDeleteSearch = { query ->
                        coroutineScope.launch {
                            searchRepo.deleteSearch(query)
                        }
                    },
                    onClearAll = {
                        coroutineScope.launch {
                            searchRepo.clearHistory()
                        }
                    }
                )
            }
            isSearching -> {
                // Loading state
                SearchLoadingState()
            }
            filteredResults.isEmpty() && searchResults.isNotEmpty() -> {
                // Results exist but filters excluded all - show specific message
                EmptySearchState(message = "No results match your filters")
            }
            filteredResults.isEmpty() -> {
                // No results found
                EmptySearchState(message = "No results found for \"$debouncedQuery\"")
            }
            else -> {
                // Display filtered results grouped by source
                GroupedSearchResults(
                    results = filteredResults,
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
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline
        )
    )
}

/**
 * Loading state with shimmer skeleton cards
 * Phase 2: Better Loading States - replaces spinner with animated placeholders
 */
@Composable
private fun SearchLoadingState(
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Movies skeleton section
        item {
            Text(
                text = "Movies",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
            )
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(8) {
                    MovieCardSkeleton()
                }
            }
        }

        // TV Shows skeleton section
        item {
            Text(
                text = "TV Shows",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
            )
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(8) {
                    SeriesCardSkeleton()
                }
            }
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
 * Recent searches state - shown when no query entered
 * Displays recent search history with tap-to-search and delete options
 */
@Composable
private fun RecentSearchesState(
    recentSearches: List<String>,
    onSuggestionClick: (String) -> Unit,
    onDeleteSearch: (String) -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (recentSearches.isEmpty()) {
        // No recent searches - show empty hint
        EmptySearchState(message = "Enter a search query to find movies and TV shows")
    } else {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            // Header with clear all button
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Recent Searches",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    TextButton(onClick = onClearAll) {
                        Text(
                            text = "Clear All",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            // Recent search items
            items(recentSearches) { query ->
                RecentSearchItem(
                    query = query,
                    onClick = { onSuggestionClick(query) },
                    onDelete = { onDeleteSearch(query) }
                )
            }

            // Tip at bottom
            item {
                Spacer(modifier = Modifier.height(24.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Star,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Tip: Search for movies, TV shows, or actors",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * Single recent search item with delete button
 */
@Composable
private fun RecentSearchItem(
    query: String,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp),
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Refresh,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = query,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Clear,
                    contentDescription = "Remove",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
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

/**
 * Filter chip with dropdown menu
 * Used for Genre, Year, and Rating filters
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterChipWithMenu(
    label: String,
    isSelected: Boolean,
    expanded: Boolean,
    onExpandChange: (Boolean) -> Unit,
    options: List<String>,
    onOptionSelected: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        FilterChip(
            selected = isSelected,
            onClick = { onExpandChange(!expanded) },
            label = { Text(label) },
            trailingIcon = {
                if (isSelected) {
                    IconButton(
                        onClick = onClear,
                        modifier = Modifier.size(18.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Clear,
                            contentDescription = "Clear filter",
                            modifier = Modifier.size(14.dp)
                        )
                    }
                } else {
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowDown,
                        contentDescription = "Expand",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        )

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandChange(false) }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = { onOptionSelected(option) },
                    leadingIcon = if (label == option) {
                        {
                            Icon(
                                imageVector = Icons.Filled.Star,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    } else null
                )
            }
        }
    }
}
