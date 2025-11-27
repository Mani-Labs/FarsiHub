package com.example.farsilandtv.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import kotlinx.coroutines.launch
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import com.example.farsilandtv.data.api.RetrofitClient
import com.example.farsilandtv.data.models.Episode
import com.example.farsilandtv.data.models.FeaturedContent
import com.example.farsilandtv.data.models.Movie
import com.example.farsilandtv.data.models.Series
import com.example.farsilandtv.data.database.ContinueWatchingItem
import com.example.farsilandtv.data.database.MonitoredSeries
import com.example.farsilandtv.data.repository.FavoritesRepository
import com.example.farsilandtv.data.repository.WatchlistRepository
import com.example.farsilandtv.ui.components.*
import com.example.farsilandtv.ui.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.*
import com.example.farsilandtv.data.database.Favorite

/**
 * Home Screen with Sidebar Navigation - Compose TV
 * Phase 3.3: Complete home screen with sidebar menu
 *
 * Internal Compose navigation - Movies/Shows/Search render inline,
 * no Fragment switching for cleaner architecture
 */
@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun HomeScreenWithSidebar(
    onMovieClick: (Movie) -> Unit,
    onSeriesClick: (Series) -> Unit,
    onEpisodeClick: (Episode) -> Unit,
    onFeaturedClick: (FeaturedContent) -> Unit,
    onNavigate: (String) -> Unit,  // Only for external navigation (options, etc)
    modifier: Modifier = Modifier,
    viewModel: MainViewModel = viewModel()
) {
    val context = LocalContext.current
    // FIXED: Use singleton getInstance() and key by context to prevent leaks
    val favoritesRepo = remember(context) { FavoritesRepository.getInstance(context) }
    val watchlistRepo = remember(context) { WatchlistRepository.getInstance(context) }

    // Internal navigation state - Movies/Shows/Search handled inline
    var currentDestination by remember { mutableStateOf("home") }

    // Observe ViewModel data
    val featuredContent by viewModel.featuredContent.observeAsState(emptyList())
    val recentEpisodes by viewModel.recentEpisodes.observeAsState(emptyList())
    val recentMovies by viewModel.recentMovies.observeAsState(emptyList())
    val recentSeries by viewModel.recentSeries.observeAsState(emptyList())
    val isLoading by viewModel.isLoading.observeAsState(false)

    // Observe favorites
    val favorites by favoritesRepo.getAllFavorites()
        .collectAsState(initial = emptyList())

    // Observe continue watching and monitored series
    val continueWatching by watchlistRepo.getContinueWatching()
        .collectAsState(initial = emptyList())
    val monitoredSeries by watchlistRepo.getAllMonitoredSeries()
        .collectAsState(initial = emptyList())

    var sidebarVisible by remember { mutableStateOf(true) }  // Start visible for TV
    val sidebarFocusRequester = remember { FocusRequester() }
    val coroutineScope = rememberCoroutineScope()

    // Dialog state for long-press options
    var showOptionsDialog by remember { mutableStateOf(false) }
    var selectedOptionsItem by remember { mutableStateOf<ContentOptionsItem?>(null) }
    var selectedItemIsInWatchlist by remember { mutableStateOf(false) }
    var selectedItemIsInFavorites by remember { mutableStateOf(false) }
    var selectedItemIsMonitored by remember { mutableStateOf(false) }
    var selectedContinueWatchingId by remember { mutableStateOf<String?>(null) }

    // Long-press handler for movies
    val onMovieLongPress: (Movie) -> Unit = { movie ->
        coroutineScope.launch {
            selectedOptionsItem = ContentOptionsItem.MovieItem(movie)
            selectedItemIsInWatchlist = watchlistRepo.isMovieInWatchlist(movie.id)
            selectedItemIsInFavorites = favorites.any { it.contentId == "movie-${movie.id}" }
            selectedItemIsMonitored = false
            selectedContinueWatchingId = null
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
            selectedContinueWatchingId = null
            showOptionsDialog = true
        }
    }

    // Long-press handler for continue watching items
    val onContinueWatchingLongPress: (ContinueWatchingItem, Movie?, Series?) -> Unit = { item, movie, series ->
        coroutineScope.launch {
            when {
                movie != null -> {
                    selectedOptionsItem = ContentOptionsItem.MovieItem(movie)
                    selectedItemIsInWatchlist = watchlistRepo.isMovieInWatchlist(movie.id)
                    selectedItemIsInFavorites = favorites.any { it.contentId == "movie-${movie.id}" }
                    selectedItemIsMonitored = false
                }
                series != null -> {
                    selectedOptionsItem = ContentOptionsItem.SeriesItem(series)
                    selectedItemIsInWatchlist = false
                    selectedItemIsInFavorites = favorites.any { it.contentId == "series-${series.id}" }
                    selectedItemIsMonitored = monitoredSeries.any { it.id == series.id }
                }
                else -> return@launch
            }
            selectedContinueWatchingId = item.id
            showOptionsDialog = true
        }
    }

    // Open sidebar and move focus to it
    val openSidebarWithFocus: () -> Unit = {
        sidebarVisible = true
        coroutineScope.launch {
            kotlinx.coroutines.delay(50) // Small delay to ensure sidebar is rendered
            try {
                sidebarFocusRequester.requestFocus()
            } catch (e: Exception) {
                // Ignore focus request errors
            }
        }
    }

    // Calculate last updated timestamp
    val lastUpdatedText = remember {
        derivedStateOf {
            val lastFetchMs = RetrofitClient.getLastFetchTimestamp()
            if (lastFetchMs > 0) {
                val now = System.currentTimeMillis()
                val diff = now - lastFetchMs
                val minutes = diff / 60000
                val hours = minutes / 60
                val days = hours / 24

                when {
                    minutes < 1 -> "Just now"
                    minutes < 60 -> "${minutes}m ago"
                    hours < 24 -> "${hours}h ago"
                    days < 7 -> "${days}d ago"
                    else -> {
                        SimpleDateFormat("MMM dd", Locale.getDefault()).format(Date(lastFetchMs))
                    }
                }
            } else {
                "Never"
            }
        }
    }

    // Show options dialog when item is long-pressed
    // M5 FIX: Capture item to prevent concurrent modification race condition
    val dialogItem = selectedOptionsItem
    if (showOptionsDialog && dialogItem != null) {
        ContentOptionsDialog(
            item = dialogItem,
            onDismiss = {
                showOptionsDialog = false
                selectedOptionsItem = null
                selectedContinueWatchingId = null
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
            },
            onRemoveFromContinueWatching = selectedContinueWatchingId?.let { cwId ->
                {
                    coroutineScope.launch {
                        when (val item = selectedOptionsItem) {
                            is ContentOptionsItem.MovieItem -> {
                                watchlistRepo.removeMovieFromContinueWatching(item.movie.id)
                            }
                            is ContentOptionsItem.SeriesItem -> {
                                // For episodes, we'd need the episode ID
                            }
                            null -> {}
                        }
                    }
                }
            }
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF121212)) // Opaque background to prevent ghosting during transitions
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            // Sidebar navigation - always in composition, animated visibility
            AnimatedVisibility(
                visible = sidebarVisible,
                enter = slideInHorizontally(initialOffsetX = { -it }) + fadeIn(),
                exit = slideOutHorizontally(targetOffsetX = { -it }) + fadeOut()
            ) {
                NavigationSidebar(
                    currentDestination = currentDestination,
                    onNavigate = { destination ->
                        when (destination) {
                            "home", "movies", "shows", "search" -> {
                                // Internal Compose navigation - just update state
                                currentDestination = destination
                                sidebarVisible = false
                            }
                            else -> {
                                // External navigation (options) - delegate to fragment
                                onNavigate(destination)
                            }
                        }
                    },
                    onClose = { sidebarVisible = false },
                    lastUpdatedText = lastUpdatedText.value,
                    focusRequester = sidebarFocusRequester,
                    modifier = Modifier.width(240.dp)
                )
            }

            // Main content area - with opaque background to prevent sidebar ghosting
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(Color(0xFF121212))
            ) {
                // Render different screens based on currentDestination
                when (currentDestination) {
                    "movies" -> {
                        MoviesScreen(
                            onMovieClick = onMovieClick,
                            onSearchClick = { currentDestination = "search" },
                            onBackToSidebar = openSidebarWithFocus,
                            viewModel = viewModel
                        )
                    }
                    "shows" -> {
                        ShowsScreen(
                            onSeriesClick = onSeriesClick,
                            onSearchClick = { currentDestination = "search" },
                            onBackToSidebar = openSidebarWithFocus,
                            viewModel = viewModel
                        )
                    }
                    "search" -> {
                        SearchScreen(
                            onMovieClick = onMovieClick,
                            onSeriesClick = onSeriesClick,
                            onBackToSidebar = openSidebarWithFocus,
                            viewModel = viewModel
                        )
                    }
                    else -> {
                        // Home content
                        HomeContent(
                            featuredContent = featuredContent,
                            recentEpisodes = recentEpisodes,
                            recentMovies = recentMovies,
                            recentSeries = recentSeries,
                            continueWatching = continueWatching,
                            monitoredSeries = monitoredSeries,
                            favorites = favorites,
                            isLoading = isLoading,
                            onMovieClick = onMovieClick,
                            onSeriesClick = onSeriesClick,
                            onEpisodeClick = onEpisodeClick,
                            onOpenSidebar = { sidebarVisible = true },
                            onOpenSidebarWithFocus = openSidebarWithFocus,
                            onMovieLongPress = onMovieLongPress,
                            onSeriesLongPress = onSeriesLongPress,
                            onContinueWatchingLongPress = onContinueWatchingLongPress
                        )
                    }
                }
            }
        }
    }
}

/**
 * Home content - extracted for cleaner code
 */
@Composable
private fun HomeContent(
    featuredContent: List<FeaturedContent>,
    recentEpisodes: List<Episode>,
    recentMovies: List<Movie>,
    recentSeries: List<Series>,
    continueWatching: List<ContinueWatchingItem>,
    monitoredSeries: List<MonitoredSeries>,
    favorites: List<Favorite>,
    isLoading: Boolean,
    onMovieClick: (Movie) -> Unit,
    onSeriesClick: (Series) -> Unit,
    onEpisodeClick: (Episode) -> Unit,
    onOpenSidebar: () -> Unit,
    onOpenSidebarWithFocus: () -> Unit,
    onMovieLongPress: (Movie) -> Unit,
    onSeriesLongPress: (Series) -> Unit,
    onContinueWatchingLongPress: (ContinueWatchingItem, Movie?, Series?) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 0.dp, end = 0.dp, top = 0.dp, bottom = 24.dp)
    ) {
        // Featured Carousel
        item(key = "featured_carousel") {
            if (isLoading && featuredContent.isEmpty()) {
                FeaturedCarouselSkeleton()
            } else if (featuredContent.isNotEmpty()) {
                FeaturedCarousel(
                    content = featuredContent.toFeaturedItems(),
                    onContentClick = { item ->
                        when (item) {
                            is FeaturedItem.MovieItem -> onMovieClick(item.movie)
                            is FeaturedItem.SeriesItem -> onSeriesClick(item.series)
                        }
                    }
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
        }

        // Continue Watching Row (at top)
        item(key = "continue_watching") {
            if (continueWatching.isNotEmpty()) {
                ContinueWatchingRow(
                    title = "Continue Watching",
                    items = continueWatching,
                    onMovieClick = onMovieClick,
                    onEpisodeClick = onEpisodeClick,
                    onOpenSidebar = onOpenSidebar,
                    onOpenSidebarWithFocus = onOpenSidebarWithFocus,
                    onItemLongPress = onContinueWatchingLongPress
                )
            }
        }

        // Monitored Series Row
        item(key = "monitored_series") {
            if (monitoredSeries.isNotEmpty()) {
                MonitoredSeriesRow(
                    title = "My Shows",
                    series = monitoredSeries,
                    onSeriesClick = onSeriesClick,
                    onOpenSidebar = onOpenSidebar,
                    onOpenSidebarWithFocus = onOpenSidebarWithFocus,
                    onSeriesLongPress = onSeriesLongPress
                )
            }
        }

        // Favorites Row - moved here, right below My Shows
        item(key = "favorites") {
            if (favorites.isNotEmpty()) {
                FavoritesRow(
                    title = "My Favorites",
                    favorites = favorites,
                    onMovieClick = onMovieClick,
                    onSeriesClick = onSeriesClick,
                    onOpenSidebar = onOpenSidebar,
                    onOpenSidebarWithFocus = onOpenSidebarWithFocus,
                    onMovieLongPress = onMovieLongPress,
                    onSeriesLongPress = onSeriesLongPress
                )
            }
        }

        // Latest Episodes Row - FIXED: Stable key
        item(key = "latest_episodes") {
            if (isLoading && recentEpisodes.isEmpty()) {
                ContentRowSkeleton(title = "Latest Episodes")
            } else if (recentEpisodes.isNotEmpty()) {
                EpisodeRow(
                    title = "Latest Episodes",
                    episodes = recentEpisodes,
                    onEpisodeClick = onEpisodeClick,
                    onOpenSidebar = onOpenSidebar,
                    onCloseSidebar = {},
                    onOpenSidebarWithFocus = onOpenSidebarWithFocus
                )
            }
        }

        // Recent Movies Row - FIXED: Stable key
        item(key = "recent_movies") {
            if (isLoading && recentMovies.isEmpty()) {
                ContentRowSkeleton(title = "Recent Movies")
            } else if (recentMovies.isNotEmpty()) {
                MovieRow(
                    title = "Recent Movies",
                    movies = recentMovies,
                    onMovieClick = onMovieClick,
                    getFavoriteStatus = { movieId ->
                        favorites.any { it.contentId == "movie-$movieId" }
                    },
                    onOpenSidebar = onOpenSidebar,
                    onCloseSidebar = {},
                    onOpenSidebarWithFocus = onOpenSidebarWithFocus,
                    onMovieLongClick = onMovieLongPress
                )
            }
        }

        // Recent Shows Row - FIXED: Stable key
        item(key = "recent_shows") {
            if (isLoading && recentSeries.isEmpty()) {
                ContentRowSkeleton(title = "Recent Shows")
            } else if (recentSeries.isNotEmpty()) {
                SeriesRow(
                    title = "Recent Shows",
                    series = recentSeries,
                    onSeriesClick = onSeriesClick,
                    getFavoriteStatus = { seriesId ->
                        favorites.any { it.contentId == "series-$seriesId" }
                    },
                    onOpenSidebar = onOpenSidebar,
                    onCloseSidebar = {},
                    onOpenSidebarWithFocus = onOpenSidebarWithFocus,
                    onSeriesLongClick = onSeriesLongPress
                )
            }
        }
    }
}

/**
 * Navigation sidebar with menu items
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun NavigationSidebar(
    currentDestination: String,
    onNavigate: (String) -> Unit,
    onClose: () -> Unit,
    lastUpdatedText: String,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier
) {
    // Navigation items with icons
    data class NavItem(val title: String, val destination: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)
    val navigationItems = listOf(
        NavItem("Home", "home", Icons.Filled.Home),
        NavItem("Movies", "movies", Icons.Filled.PlayArrow),
        NavItem("TV Shows", "shows", Icons.Filled.List),
        NavItem("Search", "search", Icons.Filled.Search),
        NavItem("Settings", "options", Icons.Filled.Settings)
    )

    Box(
        modifier = modifier
            .fillMaxHeight()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF1C2E1C),
                        Color(0xFF1A2A1A),
                        Color(0xFF0F1A0F)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // App Branding Header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 32.dp, bottom = 24.dp, start = 20.dp, end = 20.dp)
            ) {
                Column {
                    // App Logo/Name
                    Text(
                        text = "Farsihub",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    // Accent line
                    Box(
                        modifier = Modifier
                            .width(40.dp)
                            .height(3.dp)
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        Color(0xFFFF5722),
                                        Color(0xFFFF8A65)
                                    )
                                ),
                                shape = RoundedCornerShape(2.dp)
                            )
                    )
                }
            }

            // Divider
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(1.dp)
                    .background(Color.White.copy(alpha = 0.1f))
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Navigation Items
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                navigationItems.forEachIndexed { index, item ->
                    var isFocused by remember { mutableStateOf(false) }
                    val isSelected = currentDestination == item.destination

                    // Animated values
                    val backgroundColor by animateColorAsState(
                        targetValue = when {
                            isFocused -> Color(0xFFFF5722)
                            isSelected -> Color.White.copy(alpha = 0.1f)
                            else -> Color.Transparent
                        },
                        animationSpec = tween(200),
                        label = "bgColor"
                    )
                    val iconColor by animateColorAsState(
                        targetValue = when {
                            isFocused -> Color.White
                            isSelected -> Color(0xFFFF5722)
                            else -> Color(0xFF6B8E6B)
                        },
                        animationSpec = tween(200),
                        label = "iconColor"
                    )
                    val textColor by animateColorAsState(
                        targetValue = when {
                            isFocused -> Color.White
                            isSelected -> Color.White
                            else -> Color(0xFFA8C0A8)
                        },
                        animationSpec = tween(200),
                        label = "textColor"
                    )
                    val scale by animateFloatAsState(
                        targetValue = if (isFocused) 1.02f else 1f,
                        animationSpec = tween(150),
                        label = "scale"
                    )

                    androidx.compose.material3.Surface(
                        onClick = { onNavigate(item.destination) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                            }
                            .then(
                                if (index == 0) Modifier.focusRequester(focusRequester)
                                else Modifier
                            )
                            .onFocusChanged { isFocused = it.isFocused }
                            .onPreviewKeyEvent { keyEvent ->
                                if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.DirectionRight) {
                                    onClose()
                                    true
                                } else {
                                    false
                                }
                            },
                        shape = RoundedCornerShape(12.dp),
                        color = backgroundColor
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Icon
                            Icon(
                                imageVector = item.icon,
                                contentDescription = item.title,
                                tint = iconColor,
                                modifier = Modifier.size(22.dp)
                            )

                            Spacer(modifier = Modifier.width(16.dp))

                            // Title
                            Text(
                                text = item.title,
                                fontSize = 16.sp,
                                color = textColor,
                                fontWeight = if (isFocused || isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                letterSpacing = 0.3.sp
                            )

                            Spacer(modifier = Modifier.weight(1f))

                            // Selected indicator
                            if (isSelected && !isFocused) {
                                Box(
                                    modifier = Modifier
                                        .width(4.dp)
                                        .height(20.dp)
                                        .background(
                                            Color(0xFFFF5722),
                                            shape = RoundedCornerShape(2.dp)
                                        )
                                )
                            }
                        }
                    }
                }
            }

            // Bottom Section
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp)
            ) {
                // Divider
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Color.White.copy(alpha = 0.1f))
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Last Updated
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = null,
                        tint = Color(0xFF4A6B4A),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Updated $lastUpdatedText",
                        fontSize = 12.sp,
                        color = Color(0xFF4A6B4A),
                        fontWeight = FontWeight.Normal
                    )
                }
            }
        }
    }
}

/**
 * Episode row component
 */
@Composable
private fun EpisodeRow(
    title: String,
    episodes: List<Episode>,
    onEpisodeClick: (Episode) -> Unit,
    onOpenSidebar: () -> Unit = {},
    onCloseSidebar: () -> Unit = {},
    onOpenSidebarWithFocus: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    ContentRow(
        title = title,
        content = episodes,
        onContentClick = onEpisodeClick,
        cardContent = { episode, onClick, cardModifier ->
            EpisodeCard(episode = episode, onClick = onClick, modifier = cardModifier)
        },
        onOpenSidebar = onOpenSidebar,
        onCloseSidebar = onCloseSidebar,
        onOpenSidebarWithFocus = onOpenSidebarWithFocus,
        modifier = modifier
    )
}

/**
 * Favorites row - mixed content
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun FavoritesRow(
    title: String,
    favorites: List<com.example.farsilandtv.data.database.Favorite>,
    onMovieClick: (Movie) -> Unit,
    onSeriesClick: (Series) -> Unit,
    onOpenSidebar: () -> Unit = {},
    onOpenSidebarWithFocus: () -> Unit = {},
    onMovieLongPress: ((Movie) -> Unit)? = null,
    onSeriesLongPress: ((Series) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    Column(modifier = modifier.padding(vertical = 8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = Color.White,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp)
        )

        androidx.compose.foundation.lazy.LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    when (keyEvent.key) {
                        Key.Back -> {
                            // Back button pressed - check first visible item
                            val firstVisibleIndex = listState.firstVisibleItemIndex
                            if (firstVisibleIndex > 0) {
                                // Not on first item - scroll to first
                                coroutineScope.launch {
                                    listState.animateScrollToItem(0)
                                }
                            }
                            // Always open sidebar
                            onOpenSidebar()
                            true // Consume event
                        }
                        Key.DirectionLeft -> {
                            val firstVisibleIndex = listState.firstVisibleItemIndex
                            if (firstVisibleIndex == 0) {
                                // Left on first item: open sidebar and move focus
                                onOpenSidebarWithFocus()
                                true // Consume event
                            } else {
                                false // Let default navigation handle it
                            }
                        }
                        else -> false
                    }
                } else {
                    false
                }
            }
        ) {
            // FIXED: Added stable key for efficient recomposition
            itemsIndexed(favorites, key = { _, fav -> fav.contentId }) { index, favorite ->
                when (favorite.contentType) {
                    com.example.farsilandtv.data.database.Favorite.ContentType.MOVIE -> {
                        val movieId = favorite.contentId.removePrefix("movie-").toIntOrNull()
                        if (movieId != null) {
                            val movie = Movie(
                                id = movieId,
                                title = favorite.title,
                                posterUrl = favorite.posterUrl,
                                farsilandUrl = "",
                                description = ""
                            )
                            MovieCard(
                                movie = movie,
                                onClick = { onMovieClick(movie) },
                                isFavorite = true,
                                onLongClick = onMovieLongPress?.let { { it(movie) } }
                            )
                        }
                    }
                    com.example.farsilandtv.data.database.Favorite.ContentType.SERIES -> {
                        val seriesId = favorite.contentId.removePrefix("series-").toIntOrNull()
                        if (seriesId != null) {
                            val series = Series(
                                id = seriesId,
                                title = favorite.title,
                                posterUrl = favorite.posterUrl,
                                farsilandUrl = "",
                                description = ""
                            )
                            SeriesCard(
                                series = series,
                                onClick = { onSeriesClick(series) },
                                isFavorite = true,
                                onLongClick = onSeriesLongPress?.let { { it(series) } }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Episode card component
 */
@Composable
private fun EpisodeCard(
    episode: Episode,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val episodeAsMovie = Movie(
        id = episode.id,
        title = "${episode.formattedNumber}: ${episode.title}",
        posterUrl = episode.thumbnailUrl,
        farsilandUrl = episode.farsilandUrl,
        description = episode.seriesTitle ?: ""
    )

    MovieCard(
        movie = episodeAsMovie,
        onClick = onClick,
        modifier = modifier
    )
}

/**
 * Continue Watching row - shows in-progress movies and episodes
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun ContinueWatchingRow(
    title: String,
    items: List<ContinueWatchingItem>,
    onMovieClick: (Movie) -> Unit,
    onEpisodeClick: (Episode) -> Unit,
    onOpenSidebar: () -> Unit = {},
    onOpenSidebarWithFocus: () -> Unit = {},
    onItemLongPress: ((ContinueWatchingItem, Movie?, Series?) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    Column(modifier = modifier.padding(vertical = 8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = Color.White,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp)
        )

        androidx.compose.foundation.lazy.LazyRow(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .onPreviewKeyEvent { keyEvent ->
                    if (keyEvent.type == KeyEventType.KeyDown) {
                        when (keyEvent.key) {
                            Key.Back -> {
                                onOpenSidebar()
                                true
                            }
                            Key.DirectionLeft -> {
                                val firstVisibleIndex = listState.firstVisibleItemIndex
                                if (firstVisibleIndex == 0) {
                                    onOpenSidebarWithFocus()
                                    true
                                } else false
                            }
                            else -> false
                        }
                    } else false
                },
            contentPadding = PaddingValues(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(items, key = { _, item -> item.id }) { index, item ->
                when (item.contentType) {
                    ContinueWatchingItem.ContentType.MOVIE -> {
                        val movieId = item.id.removePrefix("movie-").toIntOrNull() ?: 0
                        val movie = Movie(
                            id = movieId,
                            title = item.title,
                            posterUrl = item.posterUrl,
                            farsilandUrl = item.farsilandUrl,
                            description = "${item.progressPercentage}% watched"
                        )
                        MovieCard(
                            movie = movie,
                            onClick = { onMovieClick(movie) },
                            onLongClick = onItemLongPress?.let { { it(item, movie, null) } }
                        )
                    }
                    ContinueWatchingItem.ContentType.EPISODE -> {
                        val episode = Episode(
                            id = item.episodeId ?: 0,
                            title = item.title,
                            thumbnailUrl = item.posterUrl,
                            farsilandUrl = item.farsilandUrl,
                            season = item.season ?: 1,
                            episode = item.episodeNumber ?: 1,
                            seriesId = item.seriesId,
                            seriesTitle = item.subtitle
                        )
                        // Create a series model for long-press
                        val series = item.seriesId?.let { sid ->
                            Series(
                                id = sid,
                                title = item.subtitle ?: "",
                                posterUrl = item.posterUrl,
                                farsilandUrl = item.farsilandUrl,
                                description = ""
                            )
                        }
                        EpisodeCard(
                            episode = episode,
                            onClick = { onEpisodeClick(episode) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Monitored Series row - shows series user is tracking
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun MonitoredSeriesRow(
    title: String,
    series: List<MonitoredSeries>,
    onSeriesClick: (Series) -> Unit,
    onOpenSidebar: () -> Unit = {},
    onOpenSidebarWithFocus: () -> Unit = {},
    onSeriesLongPress: ((Series) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()

    Column(modifier = modifier.padding(vertical = 8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = Color.White,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp)
        )

        androidx.compose.foundation.lazy.LazyRow(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .onPreviewKeyEvent { keyEvent ->
                    if (keyEvent.type == KeyEventType.KeyDown) {
                        when (keyEvent.key) {
                            Key.Back -> {
                                onOpenSidebar()
                                true
                            }
                            Key.DirectionLeft -> {
                                val firstVisibleIndex = listState.firstVisibleItemIndex
                                if (firstVisibleIndex == 0) {
                                    onOpenSidebarWithFocus()
                                    true
                                } else false
                            }
                            else -> false
                        }
                    } else false
                },
            contentPadding = PaddingValues(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(series, key = { _, s -> "monitored-${s.id}" }) { index, monitored ->
                val seriesModel = Series(
                    id = monitored.id,
                    title = monitored.title,
                    posterUrl = monitored.posterUrl,
                    backdropUrl = monitored.backdropUrl,
                    farsilandUrl = monitored.farsilandUrl,
                    description = "",
                    totalSeasons = monitored.totalSeasons
                )
                SeriesCard(
                    series = seriesModel,
                    onClick = { onSeriesClick(seriesModel) },
                    isFavorite = false,
                    onLongClick = onSeriesLongPress?.let { { it(seriesModel) } }
                )
            }
        }
    }
}
