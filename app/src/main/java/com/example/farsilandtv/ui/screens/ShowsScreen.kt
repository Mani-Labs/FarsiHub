package com.example.farsilandtv.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.farsilandtv.data.models.Series
import com.example.farsilandtv.data.repository.FavoritesRepository
import com.example.farsilandtv.ui.components.SeriesCard
import com.example.farsilandtv.ui.viewmodel.MainViewModel

/**
 * Feature #16: Jetpack Compose for TV - TV Shows Screen
 * Week 2 Migration - Replaces ShowsFragment
 *
 * Features:
 * - LazyVerticalGrid with 5 columns
 * - Filter cards row at top (Search + Genre filter)
 * - Paging 3 integration for infinite scroll
 * - D-pad navigation support
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShowsScreen(
    onSeriesClick: (Series) -> Unit,
    onSearchClick: () -> Unit,
    onFilterClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MainViewModel = viewModel()
) {
    val context = LocalContext.current
    val favoritesRepo = remember { FavoritesRepository(context) }

    // Collect favorites
    val favorites by favoritesRepo.getAllFavorites()
        .collectAsState(initial = emptyList())

    // For now, use LiveData series (Paging 3 integration requires PagingDataAdapter bridge)
    // This is simplified for Leanback compatibility - full Paging 3 in Week 4
    val series by viewModel.recentSeries.observeAsState(emptyList())
    val isLoading by viewModel.isLoading.observeAsState(false)

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 48.dp, vertical = 32.dp)
    ) {
        // Screen title
        Text(
            text = "TV Shows",
            style = MaterialTheme.typography.displayMedium,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(5),
            contentPadding = PaddingValues(bottom = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalArrangement = Arrangement.spacedBy(32.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            // Filter cards row (Search + Genre filter)
            item {
                SearchCard(onClick = onSearchClick)
            }

            item {
                FilterCard(
                    activeFiltersCount = 0, // TODO: Track selected genres
                    onClick = onFilterClick
                )
            }

            // Empty grid cells to complete the row
            items(3) {
                Spacer(modifier = Modifier.height(1.dp))
            }

            // Series grid
            if (isLoading && series.isEmpty()) {
                // Show skeleton cards while loading
                items(15) {
                    SeriesCardSkeleton()
                }
            } else {
                items(series.size) { index ->
                    val show = series[index]
                    SeriesCard(
                        series = show,
                        onClick = { onSeriesClick(show) },
                        isFavorite = favorites.any { it.contentId == "series-${show.id}" }
                    )
                }
            }
        }
    }
}

/**
 * Search card - action card to open search screen
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }

    Card(
        onClick = onClick,
        modifier = modifier
            .width(280.dp)
            .height(420.dp)
            .onFocusChanged { focusState ->
                isFocused = focusState.isFocused
            },
        border = if (isFocused) {
            BorderStroke(4.dp, MaterialTheme.colorScheme.primary)
        } else {
            null
        },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = "Search",
                    modifier = Modifier.size(72.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Search",
                    style = MaterialTheme.typography.headlineMedium
                )
            }
        }
    }
}

/**
 * Filter card - action card to open genre filter dialog
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterCard(
    activeFiltersCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }

    Card(
        onClick = onClick,
        modifier = modifier
            .width(280.dp)
            .height(420.dp)
            .onFocusChanged { focusState ->
                isFocused = focusState.isFocused
            },
        border = if (isFocused) {
            BorderStroke(4.dp, MaterialTheme.colorScheme.primary)
        } else {
            null
        },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.List,
                    contentDescription = "Filter",
                    modifier = Modifier.size(72.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Filter",
                    style = MaterialTheme.typography.headlineMedium
                )
                if (activeFiltersCount > 0) {
                    Badge(
                        containerColor = MaterialTheme.colorScheme.primary
                    ) {
                        Text(text = activeFiltersCount.toString())
                    }
                }
            }
        }
    }
}

/**
 * Skeleton version for SeriesCard (needed for loading states)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SeriesCardSkeleton(
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .width(280.dp)
            .height(420.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Box(modifier = Modifier.fillMaxSize())
    }
}
