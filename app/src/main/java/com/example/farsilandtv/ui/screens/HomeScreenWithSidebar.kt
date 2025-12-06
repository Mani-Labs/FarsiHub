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
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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
import com.example.farsilandtv.data.health.ScraperHealthTracker
import com.example.farsilandtv.data.repository.FavoritesRepository
import com.example.farsilandtv.data.repository.PlaylistRepository
import com.example.farsilandtv.data.repository.SearchRepository
import com.example.farsilandtv.data.repository.WatchlistRepository
import com.example.farsilandtv.data.download.DownloadConstants
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
    favoritesRepo: FavoritesRepository,
    watchlistRepo: WatchlistRepository,
    searchRepo: SearchRepository,
    playlistRepo: PlaylistRepository,
    healthTracker: ScraperHealthTracker,
    downloadManager: com.example.farsilandtv.data.download.DownloadManager,
    modifier: Modifier = Modifier,
    viewModel: MainViewModel = viewModel()
) {
    val context = LocalContext.current

    // Internal navigation state - Movies/Shows/Search handled inline
    var currentDestination by remember { mutableStateOf("home") }

    // Observe ViewModel data
    val featuredContent by viewModel.featuredContent.observeAsState(emptyList())
    val recentEpisodes by viewModel.recentEpisodes.observeAsState(emptyList())
    val recentMovies by viewModel.recentMovies.observeAsState(emptyList())
    val recentSeries by viewModel.recentSeries.observeAsState(emptyList())
    val isLoading by viewModel.isLoading.observeAsState(false)
    val errorMessage by viewModel.error.observeAsState()

    // Snackbar state for error messages
    val snackbarHostState = remember { SnackbarHostState() }

    // Show error snackbar with retry when error occurs
    LaunchedEffect(errorMessage) {
        errorMessage?.let { error ->
            val result = snackbarHostState.showSnackbar(
                message = error,
                actionLabel = "Retry",
                duration = SnackbarDuration.Long
            )
            if (result == SnackbarResult.ActionPerformed) {
                try {
                    viewModel.loadContent()
                } catch (e: Exception) {
                    android.util.Log.e("HomeScreenWithSidebar", "Failed to retry load content", e)
                }
            }
        }
    }

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
    var selectedItemIsDownloaded by remember { mutableStateOf(false) }
    var selectedContinueWatchingId by remember { mutableStateOf<String?>(null) }

    // Long-press handler for movies
    val onMovieLongPress: (Movie) -> Unit = { movie ->
        coroutineScope.launch {
            selectedOptionsItem = ContentOptionsItem.MovieItem(movie)
            selectedItemIsInWatchlist = watchlistRepo.isMovieInWatchlist(movie.id)
            selectedItemIsInFavorites = favorites.any { it.contentId == "movie-${movie.id}" }
            selectedItemIsMonitored = false
            selectedItemIsDownloaded = downloadManager.isDownloaded(DownloadConstants.movieId(movie.id))
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
    }

    // Request focus when sidebar becomes visible
    LaunchedEffect(sidebarVisible) {
        if (sidebarVisible) {
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
            },
            isDownloaded = selectedItemIsDownloaded,
            onDownload = (selectedOptionsItem as? ContentOptionsItem.MovieItem)?.let { movieItem ->
                {
                    coroutineScope.launch {
                        val movie = movieItem.movie
                        if (selectedItemIsDownloaded) {
                            // Delete download
                            downloadManager.deleteDownload(DownloadConstants.movieId(movie.id))
                            android.widget.Toast.makeText(context, "Download removed", android.widget.Toast.LENGTH_SHORT).show()
                        } else {
                            // Start download - scrape video URL first
                            val pageUrl = movie.farsilandUrl
                            if (pageUrl.isBlank()) {
                                android.widget.Toast.makeText(context, "No source URL available", android.widget.Toast.LENGTH_SHORT).show()
                                return@launch
                            }
                            android.widget.Toast.makeText(context, "Finding video URL...", android.widget.Toast.LENGTH_SHORT).show()

                            when (val result = com.example.farsilandtv.data.scraper.VideoUrlScraper.extractVideoUrls(pageUrl)) {
                                is com.example.farsilandtv.data.scraper.ScraperResult.Success -> {
                                    val videoUrls = result.data
                                    if (videoUrls.isNotEmpty()) {
                                        val bestUrl = videoUrls.first()
                                        val queued = downloadManager.queueMovieDownload(
                                            movieId = movie.id,
                                            title = movie.title,
                                            posterUrl = movie.posterUrl,
                                            videoUrl = bestUrl.url
                                        )
                                        if (queued) {
                                            android.widget.Toast.makeText(context, "Download started!", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    } else {
                                        android.widget.Toast.makeText(context, "No video URLs found", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                }
                                else -> {
                                    android.widget.Toast.makeText(context, "Failed to find video", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                }
            }
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0F)) // BackgroundDark // Opaque background to prevent ghosting during transitions
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
                            "home", "movies", "shows", "search", "favorites", "playlists", "downloads", "options" -> {
                                // Internal Compose navigation - just update state
                                currentDestination = destination
                                sidebarVisible = false
                            }
                            else -> {
                                // External navigation - delegate to fragment
                                onNavigate(destination)
                            }
                        }
                    },
                    onClose = { sidebarVisible = false },
                    lastUpdatedText = lastUpdatedText.value,
                    focusRequester = sidebarFocusRequester,
                    modifier = Modifier.width(160.dp)
                )
            }

            // Main content area - with opaque background to prevent sidebar ghosting
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(Color(0xFF0A0A0F)) // BackgroundDark
            ) {
                // Render different screens based on currentDestination
                when (currentDestination) {
                    "movies" -> {
                        MoviesScreen(
                            favoritesRepo = favoritesRepo,
                            watchlistRepo = watchlistRepo,
                            onMovieClick = onMovieClick,
                            onBackToSidebar = openSidebarWithFocus,
                            viewModel = viewModel
                        )
                    }
                    "shows" -> {
                        ShowsScreen(
                            favoritesRepo = favoritesRepo,
                            watchlistRepo = watchlistRepo,
                            onSeriesClick = onSeriesClick,
                            onBackToSidebar = openSidebarWithFocus,
                            viewModel = viewModel
                        )
                    }
                    "search" -> {
                        SearchScreen(
                            favoritesRepo = favoritesRepo,
                            watchlistRepo = watchlistRepo,
                            searchRepo = searchRepo,
                            onMovieClick = onMovieClick,
                            onSeriesClick = onSeriesClick,
                            onBackToSidebar = openSidebarWithFocus,
                            viewModel = viewModel
                        )
                    }
                    "favorites" -> {
                        FavoritesScreen(
                            favoritesRepo = favoritesRepo,
                            onMovieClick = { movieId ->
                                // Find movie and navigate
                                val movie = recentMovies.find { it.id == movieId }
                                if (movie != null) onMovieClick(movie)
                            },
                            onSeriesClick = { seriesId ->
                                // Find series and navigate
                                val series = recentSeries.find { it.id == seriesId }
                                if (series != null) onSeriesClick(series)
                            },
                            onBackClick = {
                                currentDestination = "home"
                                openSidebarWithFocus()
                            }
                        )
                    }
                    "playlists" -> {
                        PlaylistsScreen(
                            playlistRepo = playlistRepo,
                            onMovieClick = { movieId ->
                                val movie = recentMovies.find { it.id == movieId }
                                if (movie != null) onMovieClick(movie)
                            },
                            onSeriesClick = { seriesId ->
                                val series = recentSeries.find { it.id == seriesId }
                                if (series != null) onSeriesClick(series)
                            },
                            onBackClick = {
                                currentDestination = "home"
                                openSidebarWithFocus()
                            }
                        )
                    }
                    "downloads" -> {
                        DownloadsScreen(
                            downloadManager = downloadManager,
                            onPlayDownload = { download ->
                                // Play downloaded content from local file
                                if (download.canPlay && download.localFilePath != null) {
                                    val intent = android.content.Intent(context, com.example.farsilandtv.VideoPlayerActivity::class.java).apply {
                                        putExtra(com.example.farsilandtv.utils.IntentExtras.CONTENT_TYPE,
                                            if (download.contentType == com.example.farsilandtv.data.download.ContentType.MOVIE)
                                                com.example.farsilandtv.utils.IntentExtras.ContentType.MOVIE
                                            else
                                                com.example.farsilandtv.utils.IntentExtras.ContentType.EPISODE
                                        )
                                        // Extract ID from download id (e.g., "movie_123" -> 123)
                                        val contentId = download.id.substringAfter("_").toIntOrNull() ?: 0
                                        putExtra(com.example.farsilandtv.utils.IntentExtras.CONTENT_ID, contentId)
                                        putExtra(com.example.farsilandtv.utils.IntentExtras.CONTENT_TITLE, download.title)
                                        putExtra(com.example.farsilandtv.utils.IntentExtras.CONTENT_URL, download.videoUrl)
                                        putExtra(com.example.farsilandtv.utils.IntentExtras.CONTENT_POSTER_URL, download.posterUrl)
                                        // Pass local file as pre-selected video URL
                                        putExtra(com.example.farsilandtv.utils.IntentExtras.SELECTED_VIDEO_URL, "file://${download.localFilePath}")
                                        putExtra(com.example.farsilandtv.utils.IntentExtras.SELECTED_VIDEO_QUALITY, "Downloaded")
                                    }
                                    context.startActivity(intent)
                                } else {
                                    android.widget.Toast.makeText(
                                        context,
                                        "Download not ready to play",
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                }
                            },
                            onBackClick = {
                                currentDestination = "home"
                                openSidebarWithFocus()
                            }
                        )
                    }
                    "options" -> {
                        OptionsScreen(
                            healthTracker = healthTracker,
                            onBackClick = {
                                currentDestination = "home"
                                openSidebarWithFocus()
                            },
                            onDatabaseSourceChange = {
                                // Trigger content refresh
                                viewModel.loadContent()
                            }
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

        // Error Snackbar - positioned at bottom of screen
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp)
        ) { snackbarData ->
            Snackbar(
                snackbarData = snackbarData,
                containerColor = Color(0xFFB71C1C),
                contentColor = Color.White,
                actionColor = Color(0xFFFFCDD2)
            )
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
        contentPadding = PaddingValues(start = 0.dp, end = 0.dp, top = 0.dp, bottom = 16.dp)
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
        NavItem("TV Shows", "shows", Icons.Filled.Menu),
        NavItem("Search", "search", Icons.Filled.Search),
        NavItem("Downloads", "downloads", Icons.Filled.KeyboardArrowDown),
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
                    .padding(top = 12.dp, bottom = 8.dp, start = 12.dp, end = 12.dp)
            ) {
                Column {
                    // App Logo/Name
                    Text(
                        text = "Farsihub",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    // Accent line
                    Box(
                        modifier = Modifier
                            .width(32.dp)
                            .height(2.dp)
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        Color(0xFFF59E0B),
                                        Color(0xFFFBBF24)
                                    )
                                ),
                                shape = RoundedCornerShape(1.dp)
                            )
                    )
                }
            }

            // Divider
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
                    .height(1.dp)
                    .background(Color.White.copy(alpha = 0.1f))
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Navigation Items
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                navigationItems.forEachIndexed { index, item ->
                    var isFocused by remember { mutableStateOf(false) }
                    val isSelected = currentDestination == item.destination

                    // Animated values
                    val backgroundColor by animateColorAsState(
                        targetValue = when {
                            isFocused -> Color(0xFFF59E0B)
                            isSelected -> Color.White.copy(alpha = 0.1f)
                            else -> Color.Transparent
                        },
                        animationSpec = tween(200),
                        label = "bgColor"
                    )
                    val iconColor by animateColorAsState(
                        targetValue = when {
                            isFocused -> Color.White
                            isSelected -> Color(0xFFF59E0B)
                            else -> Color(0xFF94A3B8)
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
                        shape = RoundedCornerShape(8.dp),
                        color = backgroundColor
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Icon
                            Icon(
                                imageVector = item.icon,
                                contentDescription = item.title,
                                tint = iconColor,
                                modifier = Modifier.size(18.dp)
                            )

                            Spacer(modifier = Modifier.width(12.dp))

                            // Title
                            Text(
                                text = item.title,
                                fontSize = 12.sp,
                                color = textColor,
                                fontWeight = if (isFocused || isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                letterSpacing = 0.2.sp
                            )

                            Spacer(modifier = Modifier.weight(1f))

                            // Selected indicator
                            if (isSelected && !isFocused) {
                                Box(
                                    modifier = Modifier
                                        .width(3.dp)
                                        .height(14.dp)
                                        .background(
                                            Color(0xFFF59E0B),
                                            shape = RoundedCornerShape(1.5.dp)
                                        )
                                )
                            }
                        }
                    }
                }
            }

            // Bottom Section
            Column(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
            ) {
                // Divider
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Color.White.copy(alpha = 0.1f))
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Last Updated
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = null,
                        tint = Color(0xFF4A6B4A),
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Updated $lastUpdatedText",
                        fontSize = 9.sp,
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

    Column(modifier = modifier.padding(vertical = 6.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 6.dp)
        )

        androidx.compose.foundation.lazy.LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
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
    // DEBUG: Log episode thumbnail URL to diagnose image loading issue
    android.util.Log.d("HomeScreen", "EpisodeCard: ${episode.formattedNumber} thumbnailUrl=${episode.thumbnailUrl}")

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

    Column(modifier = modifier.padding(vertical = 6.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 6.dp)
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
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
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
                            progressPercent = item.progressPercentage / 100f,  // Phase 3: Progress bar
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

    Column(modifier = modifier.padding(vertical = 6.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 6.dp)
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
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
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
