package com.example.farsilandtv.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.farsilandtv.data.models.Movie
import com.example.farsilandtv.data.models.Series
import com.example.farsilandtv.data.repository.FavoritesRepository
import com.example.farsilandtv.ui.components.MovieCard
import com.example.farsilandtv.ui.components.MovieCardSkeleton
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
 * - Results grid below (LazyVerticalGrid 5 columns)
 * - Real-time search with 300ms debounce
 * - Mixed results (movies + series)
 * - Empty state when no results
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onMovieClick: (Movie) -> Unit,
    onSeriesClick: (Series) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MainViewModel = viewModel()
) {
    val context = LocalContext.current
    val favoritesRepo = remember { FavoritesRepository(context) }

    // Collect favorites
    val favorites by favoritesRepo.getAllFavorites()
        .collectAsState(initial = emptyList())

    // Search state
    var searchQuery by remember { mutableStateOf("") }
    var debouncedQuery by remember { mutableStateOf("") }
    val searchResults by viewModel.search(debouncedQuery).observeAsState(emptyList())

    val focusRequester = remember { FocusRequester() }
    val coroutineScope = rememberCoroutineScope()

    // Debounce search query (300ms)
    LaunchedEffect(searchQuery) {
        coroutineScope.launch {
            delay(300) // 300ms debounce
            debouncedQuery = searchQuery
        }
    }

    // Auto-focus search field on screen open
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 48.dp, vertical = 32.dp)
    ) {
        // Search bar
        SearchBar(
            query = searchQuery,
            onQueryChange = { searchQuery = it },
            onClearQuery = { searchQuery = "" },
            focusRequester = focusRequester,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        )

        // Results grid or empty state
        if (debouncedQuery.isEmpty()) {
            // Empty state - no search query yet
            EmptySearchState(message = "Enter a search query to find movies and TV shows")
        } else if (searchResults.isEmpty()) {
            // No results found
            EmptySearchState(message = "No results found for \"$debouncedQuery\"")
        } else {
            // Display results grid
            SearchResultsGrid(
                results = searchResults,
                favorites = favorites,
                onMovieClick = onMovieClick,
                onSeriesClick = onSeriesClick
            )
        }
    }
}

/**
 * Search bar with clear button
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClearQuery: () -> Unit,
    focusRequester: FocusRequester,
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
            if (query.isNotEmpty()) {
                IconButton(onClick = onClearQuery) {
                    Icon(
                        imageVector = Icons.Filled.Clear,
                        contentDescription = "Clear"
                    )
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
 * Search results grid - mixed content (movies + series)
 */
@Composable
private fun SearchResultsGrid(
    results: List<Any>,
    favorites: List<com.example.farsilandtv.data.database.Favorite>,
    onMovieClick: (Movie) -> Unit,
    onSeriesClick: (Series) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(5),
        contentPadding = PaddingValues(bottom = 48.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier.fillMaxSize()
    ) {
        items(results.size) { index ->
            when (val result = results[index]) {
                is Movie -> {
                    MovieCard(
                        movie = result,
                        onClick = { onMovieClick(result) },
                        isFavorite = favorites.any { it.contentId == "movie-${result.id}" }
                    )
                }
                is Series -> {
                    SeriesCard(
                        series = result,
                        onClick = { onSeriesClick(result) },
                        isFavorite = favorites.any { it.contentId == "series-${result.id}" }
                    )
                }
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
