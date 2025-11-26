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
import com.example.farsilandtv.data.models.Series
import com.example.farsilandtv.data.repository.FavoritesRepository
import com.example.farsilandtv.data.repository.WatchlistRepository
import com.example.farsilandtv.ui.components.ContentOptionsDialog
import com.example.farsilandtv.ui.components.ContentOptionsItem
import com.example.farsilandtv.ui.components.SeriesCard
import com.example.farsilandtv.ui.components.SeriesCardSkeleton
import com.example.farsilandtv.ui.viewmodel.MainViewModel
import kotlinx.coroutines.launch

/**
 * TV Shows Library Screen - Premium TV Experience
 * Netflix/Plex inspired design with hero banner, genre filters, and grid
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun ShowsScreen(
    onSeriesClick: (Series) -> Unit,
    onSearchClick: () -> Unit = {},
    onFilterClick: () -> Unit = {},
    onBackToSidebar: () -> Unit = {},  // Called when pressing Back/Left on first item
    modifier: Modifier = Modifier,
    viewModel: MainViewModel = viewModel()
) {
    val context = LocalContext.current
    val favoritesRepo = remember(context) { FavoritesRepository.getInstance(context) }
    val watchlistRepo = remember(context) { WatchlistRepository.getInstance(context) }
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

    // Hero banner series - updates when user focuses different cards
    var focusedSeries by remember { mutableStateOf<Series?>(null) }
    val heroShow = focusedSeries ?: remember(series.itemCount) {
        if (series.itemCount > 0) series[0] else null
    }

    // Back button goes back to sidebar/home
    BackHandler {
        onBackToSidebar()
    }

    val onSeriesLongPress: (Series) -> Unit = { show ->
        coroutineScope.launch {
            selectedSeries = show
            selectedIsMonitored = monitoredSeries.any { it.id == show.id }
            selectedIsInFavorites = favorites.any { it.contentId == "series-${show.id}" }
            showOptionsDialog = true
        }
    }

    // Options dialog
    if (showOptionsDialog && selectedSeries != null) {
        ContentOptionsDialog(
            item = ContentOptionsItem.SeriesItem(selectedSeries!!),
            onDismiss = {
                showOptionsDialog = false
                selectedSeries = null
            },
            isInWatchlist = false,
            isInFavorites = selectedIsInFavorites,
            isMonitored = selectedIsMonitored,
            onToggleFavorites = {
                coroutineScope.launch {
                    selectedSeries?.let { show ->
                        if (selectedIsInFavorites) {
                            favoritesRepo.removeSeriesFromFavorites(show.id)
                        } else {
                            favoritesRepo.addSeriesToFavorites(show)
                        }
                    }
                }
            },
            onToggleMonitored = {
                coroutineScope.launch {
                    selectedSeries?.let { show ->
                        if (selectedIsMonitored) {
                            watchlistRepo.removeSeriesFromMonitored(show.id)
                        } else {
                            watchlistRepo.addSeriesToMonitored(show)
                        }
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
            // Hero Banner - shows focused series or first series
            if (heroShow != null && series.loadState.refresh !is LoadState.Loading) {
                val isHeroMonitored = monitoredSeries.any { it.id == heroShow.id }
                HeroBanner(
                    series = heroShow,
                    isMonitored = isHeroMonitored,
                    onClick = { onSeriesClick(heroShow) },
                    onPlayClick = { onSeriesClick(heroShow) },
                    onAddToListClick = { onSeriesLongPress(heroShow) }
                )
            }

            // Filter Bar
            FilterBar(
                genres = genres,
                selectedGenre = selectedGenre ?: "All",
                onGenreSelected = { genre ->
                    viewModel.setSeriesGenreFilter(if (genre == "All") null else genre)
                },
                sortOptions = sortOptions,
                selectedSort = selectedSort,
                onSortSelected = { sort ->
                    viewModel.setSeriesSortOption(sort)
                },
                onSearchClick = onSearchClick,
                itemCount = series.itemCount
            )

            // Content Grid
            when (series.loadState.refresh) {
                is LoadState.Loading -> {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(6),
                        contentPadding = PaddingValues(horizontal = 48.dp, vertical = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(24) { SeriesCardSkeleton() }
                    }
                }

                is LoadState.Error -> {
                    ErrorState(
                        message = "Failed to load TV shows",
                        onRetry = { series.retry() }
                    )
                }

                else -> {
                    if (series.itemCount == 0) {
                        EmptyState(message = "No TV shows found")
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(6),
                            state = gridState,
                            contentPadding = PaddingValues(
                                start = 48.dp, end = 48.dp,
                                top = 16.dp, bottom = 48.dp
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
                                val isLeftmostColumn = index % 6 == 0  // 6-column grid
                                if (show != null) {
                                    SeriesCard(
                                        series = show,
                                        onClick = { onSeriesClick(show) },
                                        isFavorite = favorites.any { it.contentId == "series-${show.id}" },
                                        onLongClick = { onSeriesLongPress(show) },
                                        modifier = Modifier
                                            .onFocusChanged { focusState ->
                                                if (focusState.isFocused) {
                                                    focusedSeries = show
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
                                    SeriesCardSkeleton()
                                }
                            }

                            if (series.loadState.append is LoadState.Loading) {
                                items(6) { SeriesCardSkeleton() }
                            }
                        }
                    }
                }
            }
        }

        // Loading overlay
        if (series.loadState.append is LoadState.Loading) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp)
            ) {
                CircularProgressIndicator(
                    color = Color(0xFFFF5722),
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}

/**
 * Hero Banner - Featured series with backdrop
 */
@Composable
private fun HeroBanner(
    series: Series,
    isMonitored: Boolean,
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
            model = series.backdropUrl ?: series.posterUrl,
            contentDescription = series.title,
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
                color = Color(0xFF9C27B0),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    text = "FEATURED SERIES",
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
                text = series.title,
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
                series.year?.let {
                    Text(text = it.toString(), color = Color(0xFFB0B0B0), fontSize = 14.sp)
                }
                series.rating?.let {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "★", color = Color(0xFFFFD700), fontSize = 14.sp)
                        Text(text = " $it", color = Color(0xFFB0B0B0), fontSize = 14.sp)
                    }
                }
                if (series.totalSeasons > 0) {
                    Text(
                        text = "${series.totalSeasons} Season${if (series.totalSeasons != 1) "s" else ""}",
                        color = Color(0xFFB0B0B0),
                        fontSize = 14.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Genres
            if (series.genres.isNotEmpty()) {
                Text(
                    text = series.genres.take(3).joinToString(" • "),
                    color = Color(0xFF888888),
                    fontSize = 13.sp
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action buttons
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HeroButton(
                    text = "Watch",
                    isPrimary = true,
                    onClick = onPlayClick
                )
                HeroButton(
                    text = if (isMonitored) "✓ Monitoring" else "+ Monitor",
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
            isFocused -> Color(0xFFFF5722)
            isPrimary -> Color.White
            else -> Color(0xFF333333)
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
    onSearchClick: () -> Unit,
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
                text = "$itemCount shows",
                color = Color(0xFF666666),
                fontSize = 14.sp
            )

            // Sort dropdown
            SortButton(
                options = sortOptions,
                selected = selectedSort,
                onSelected = onSortSelected
            )

            // Search button
            SearchButton(onClick = onSearchClick)
        }
    }
}

@Composable
private fun GenreChip(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    val backgroundColor by animateColorAsState(
        targetValue = when {
            isFocused -> Color(0xFFFF5722)
            isSelected -> Color(0xFF333333)
            else -> Color.Transparent
        },
        animationSpec = tween(150),
        label = "chip_bg"
    )
    val borderColor = when {
        isFocused -> Color(0xFFFF5722)
        isSelected -> Color(0xFF333333)
        else -> Color(0xFF444444)
    }

    Surface(
        onClick = onClick,
        modifier = modifier.onFocusChanged { isFocused = it.isFocused },
        shape = RoundedCornerShape(20.dp),
        color = backgroundColor,
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            color = if (isFocused || isSelected) Color.White else Color(0xFFAAAAAA),
            fontSize = 13.sp,
            fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal
        )
    }
}

@Composable
private fun SortButton(
    options: List<String>,
    selected: String,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        Surface(
            onClick = { expanded = true },
            modifier = Modifier.onFocusChanged { isFocused = it.isFocused },
            shape = RoundedCornerShape(8.dp),
            color = if (isFocused) Color(0xFFFF5722) else Color(0xFF1E1E1E)
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
                    contentDescription = "Sort",
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
                            color = if (option == selected) Color(0xFFFF5722) else Color.White
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

@Composable
private fun SearchButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }

    Surface(
        onClick = onClick,
        modifier = modifier.onFocusChanged { isFocused = it.isFocused },
        shape = RoundedCornerShape(8.dp),
        color = if (isFocused) Color(0xFFFF5722) else Color(0xFF1E1E1E)
    ) {
        Icon(
            imageVector = Icons.Filled.Search,
            contentDescription = "Search",
            tint = Color.White,
            modifier = Modifier
                .padding(10.dp)
                .size(20.dp)
        )
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
                color = if (isFocused) Color(0xFFFF5722) else Color(0xFF333333)
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
