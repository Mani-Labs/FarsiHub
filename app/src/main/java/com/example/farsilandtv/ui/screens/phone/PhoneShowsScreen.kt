package com.example.farsilandtv.ui.screens.phone

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import com.example.farsilandtv.data.models.Series
import com.example.farsilandtv.data.repository.FavoritesRepository
import com.example.farsilandtv.data.repository.WatchlistRepository
import com.example.farsilandtv.ui.components.ContentOptionsDialog
import com.example.farsilandtv.ui.components.ContentOptionsItem
import com.example.farsilandtv.ui.components.PhoneSeriesCard
import com.example.farsilandtv.ui.components.PhoneSeriesCardSkeleton
import com.example.farsilandtv.ui.viewmodel.MainViewModel
import kotlinx.coroutines.launch

/**
 * Phone Shows Screen - Touch-optimized TV series browsing
 *
 * Features:
 * - 2-column grid layout (phone portrait)
 * - Horizontal scrollable filter chips
 * - Sort dropdown
 * - Pull-to-refresh support
 * - Long-press options menu
 * - No hero banner (saves space on small screens)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhoneShowsScreen(
    favoritesRepo: FavoritesRepository,
    watchlistRepo: WatchlistRepository,
    onSeriesClick: (Series) -> Unit,
    onBackClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: MainViewModel = viewModel()
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Data
    val favorites by favoritesRepo.getAllFavorites().collectAsState(initial = emptyList())
    val monitoredSeries by watchlistRepo.getAllMonitoredSeries().collectAsState(initial = emptyList())

    // Filter/Sort state from ViewModel
    val selectedGenre by viewModel.seriesGenreFilter.collectAsState()
    val selectedSort by viewModel.seriesSortOption.collectAsState()
    val series = viewModel.filteredSeries.collectAsLazyPagingItems()
    val gridState = rememberLazyGridState()

    // UI State - genre/sort options
    val genres = listOf("All", "Action", "Comedy", "Drama", "Crime", "Sci-Fi", "Fantasy", "Romance", "Animation", "Documentary", "Family", "Thriller", "Mystery", "Adventure", "War")
    val sortOptions = listOf("Recent", "Year", "Rating", "A-Z")

    // Dialog state
    var showOptionsDialog by remember { mutableStateOf(false) }
    var selectedSeries by remember { mutableStateOf<Series?>(null) }
    var selectedIsMonitored by remember { mutableStateOf(false) }
    var selectedIsInFavorites by remember { mutableStateOf(false) }

    val onSeriesLongPress: (Series) -> Unit = { show ->
        coroutineScope.launch {
            selectedSeries = show
            selectedIsMonitored = monitoredSeries.any { it.id == show.id }
            selectedIsInFavorites = favorites.any { it.contentId == "series-${show.id}" }
            showOptionsDialog = true
        }
    }

    // Options dialog
    val dialogSeries = selectedSeries
    if (showOptionsDialog && dialogSeries != null) {
        ContentOptionsDialog(
            item = ContentOptionsItem.SeriesItem(dialogSeries),
            onDismiss = {
                showOptionsDialog = false
                selectedSeries = null
            },
            isInWatchlist = false,
            isInFavorites = selectedIsInFavorites,
            isMonitored = selectedIsMonitored,
            onToggleFavorites = {
                coroutineScope.launch {
                    if (selectedIsInFavorites) {
                        favoritesRepo.removeSeriesFromFavorites(dialogSeries.id)
                    } else {
                        favoritesRepo.addSeriesToFavorites(dialogSeries)
                    }
                }
            },
            onToggleMonitored = {
                coroutineScope.launch {
                    if (selectedIsMonitored) {
                        watchlistRepo.removeSeriesFromMonitored(dialogSeries.id)
                    } else {
                        watchlistRepo.addSeriesToMonitored(dialogSeries)
                    }
                }
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
    ) {
        // Header with title and count
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "TV Shows",
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${series.itemCount} shows",
                    color = Color(0xFF808080),
                    fontSize = 12.sp
                )
            }

            // Sort dropdown
            PhoneSortButton(
                options = sortOptions,
                selected = selectedSort,
                onSelected = { viewModel.setSeriesSortOption(it) }
            )
        }

        // Genre filter chips (horizontal scroll)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            genres.forEach { genre ->
                PhoneFilterChip(
                    text = genre,
                    isSelected = (selectedGenre ?: "All") == genre,
                    onClick = {
                        viewModel.setSeriesGenreFilter(if (genre == "All") null else genre)
                    }
                )
            }
        }

        // Content Grid
        when (series.loadState.refresh) {
            is LoadState.Loading -> {
                PhoneShowsLoadingSkeleton()
            }

            is LoadState.Error -> {
                PhoneErrorState(
                    message = "Failed to load TV shows",
                    onRetry = { series.retry() }
                )
            }

            else -> {
                if (series.itemCount == 0) {
                    PhoneEmptyState(message = "No TV shows found")
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        state = gridState,
                        contentPadding = PaddingValues(
                            start = 12.dp, end = 12.dp,
                            top = 8.dp, bottom = 80.dp  // Extra bottom for nav bar
                        ),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(
                            count = series.itemCount,
                            key = { index -> series[index]?.id ?: index }
                        ) { index ->
                            val show = series[index]
                            if (show != null) {
                                PhoneSeriesCard(
                                    series = show,
                                    onClick = { onSeriesClick(show) },
                                    isFavorite = favorites.any { it.contentId == "series-${show.id}" },
                                    onLongClick = { onSeriesLongPress(show) },
                                    width = 160.dp,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            } else {
                                PhoneSeriesCardSkeleton(width = 160.dp)
                            }
                        }

                        // Load more indicator
                        if (series.loadState.append is LoadState.Loading) {
                            items(4) {
                                PhoneSeriesCardSkeleton(width = 160.dp)
                            }
                        }
                    }
                }
            }
        }

        // Loading indicator at bottom
        if (series.loadState.append is LoadState.Loading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = Color(0xFFE91E63),
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

/**
 * Phone-optimized filter chip
 */
@Composable
private fun PhoneFilterChip(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FilterChip(
        selected = isSelected,
        onClick = onClick,
        label = {
            Text(
                text = text,
                fontSize = 13.sp,
                fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal
            )
        },
        modifier = modifier.height(32.dp),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = Color.Transparent,
            labelColor = Color(0xFFAAAAAA),
            selectedContainerColor = Color(0xFF9C27B0),  // Purple for series
            selectedLabelColor = Color.White
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = isSelected,
            borderColor = Color(0xFF444444),
            selectedBorderColor = Color(0xFF9C27B0)
        ),
        shape = RoundedCornerShape(16.dp)
    )
}

/**
 * Phone-optimized sort button with dropdown
 */
@Composable
private fun PhoneSortButton(
    options: List<String>,
    selected: String,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        Surface(
            onClick = { expanded = true },
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF2A2A2A)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = selected,
                    color = Color.White,
                    fontSize = 13.sp
                )
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Sort options",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(Color(0xFF2A2A2A))
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = option,
                            color = if (option == selected) Color(0xFF9C27B0) else Color.White
                        )
                    },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

/**
 * Loading skeleton for shows grid
 */
@Composable
private fun PhoneShowsLoadingSkeleton() {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(8) {
            PhoneSeriesCardSkeleton(width = 160.dp)
        }
    }
}

/**
 * Error state
 */
@Composable
private fun PhoneErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = null,
                tint = Color(0xFF9C27B0),
                modifier = Modifier.size(48.dp)
            )
            Text(
                text = message,
                color = Color(0xFFFF6B6B),
                fontSize = 16.sp,
                textAlign = TextAlign.Center
            )
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF9C27B0)
                )
            ) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Retry")
            }
        }
    }
}

/**
 * Empty state
 */
@Composable
private fun PhoneEmptyState(
    message: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "📺",
                fontSize = 48.sp
            )
            Text(
                text = message,
                color = Color(0xFF666666),
                fontSize = 16.sp
            )
        }
    }
}
