package com.example.farsilandtv.ui.screens.phone

import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.farsilandtv.data.download.DownloadManager
import com.example.farsilandtv.data.health.ScraperHealthTracker
import com.example.farsilandtv.data.models.Episode
import com.example.farsilandtv.data.models.FeaturedContent
import com.example.farsilandtv.data.models.Movie
import com.example.farsilandtv.data.models.Series
import com.example.farsilandtv.data.repository.FavoritesRepository
import com.example.farsilandtv.data.repository.PlaylistRepository
import com.example.farsilandtv.data.repository.SearchRepository
import com.example.farsilandtv.data.repository.WatchlistRepository
import com.example.farsilandtv.ui.screens.OptionsScreen
import com.example.farsilandtv.ui.screens.SearchScreen
import com.example.farsilandtv.ui.viewmodel.MainViewModel

/**
 * Phone Navigation Host - Main phone UI with Bottom Navigation Bar
 *
 * Phase 2: Phone Navigation Shell
 * Provides bottom navigation for phone users while TV uses sidebar
 *
 * Navigation Items:
 * - Home: Featured content, continue watching, content rows
 * - Movies: Movie grid with genre filters
 * - Shows: TV series grid with genre filters
 * - Search: Search functionality
 * - Settings: App settings and preferences
 */
private const val TAG = "PhoneNavigationHost"

@Composable
fun PhoneNavigationHost(
    onMovieClick: (Movie) -> Unit,
    onSeriesClick: (Series) -> Unit,
    onEpisodeClick: (Episode) -> Unit,
    onFeaturedClick: (FeaturedContent) -> Unit,
    onNavigate: (String) -> Unit,
    favoritesRepo: FavoritesRepository,
    watchlistRepo: WatchlistRepository,
    searchRepo: SearchRepository,
    playlistRepo: PlaylistRepository,
    downloadManager: DownloadManager,
    healthTracker: ScraperHealthTracker,
    viewModel: MainViewModel
) {
    var selectedTab by rememberSaveable { mutableStateOf(PhoneTab.HOME) }

    // Debug logging - moved to LaunchedEffect to avoid recomposition spam
    LaunchedEffect(Unit) {
        Log.i(TAG, "PhoneNavigationHost composable started!")
    }

    Scaffold(
        bottomBar = {
            PhoneBottomNavigation(
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it }
            )
        },
        containerColor = Color(0xFF121212)
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (selectedTab) {
                PhoneTab.HOME -> PhoneHomeScreen(
                    onMovieClick = onMovieClick,
                    onSeriesClick = onSeriesClick,
                    onEpisodeClick = onEpisodeClick,
                    onFeaturedClick = onFeaturedClick
                    // PhoneHomeScreen uses hiltViewModel() internally
                )
                PhoneTab.MOVIES -> PhoneMoviesScreen(
                    favoritesRepo = favoritesRepo,
                    watchlistRepo = watchlistRepo,
                    onMovieClick = onMovieClick,
                    onBackClick = { selectedTab = PhoneTab.HOME },
                    viewModel = viewModel
                )
                PhoneTab.SHOWS -> PhoneShowsScreen(
                    favoritesRepo = favoritesRepo,
                    watchlistRepo = watchlistRepo,
                    onSeriesClick = onSeriesClick,
                    onBackClick = { selectedTab = PhoneTab.HOME },
                    viewModel = viewModel
                )
                PhoneTab.SEARCH -> SearchScreen(
                    favoritesRepo = favoritesRepo,
                    watchlistRepo = watchlistRepo,
                    searchRepo = searchRepo,
                    onMovieClick = onMovieClick,
                    onSeriesClick = onSeriesClick,
                    onBackToSidebar = { selectedTab = PhoneTab.HOME },
                    viewModel = viewModel
                )
                PhoneTab.LIBRARY -> {
                    // Track if showing settings screen within library
                    var showSettings by remember { mutableStateOf(false) }

                    if (showSettings) {
                        OptionsScreen(
                            healthTracker = healthTracker,
                            onBackClick = { showSettings = false },
                            onDatabaseSourceChange = { viewModel.loadContent() }
                        )
                    } else {
                        PhoneLibraryScreen(
                            favoritesRepo = favoritesRepo,
                            watchlistRepo = watchlistRepo,
                            playlistRepo = playlistRepo,
                            downloadManager = downloadManager,
                            onMovieClick = onMovieClick,
                            onSeriesClick = onSeriesClick,
                            onSettingsClick = { showSettings = true },
                            viewModel = viewModel
                        )
                    }
                }
            }
        }
    }
}

/**
 * Bottom Navigation Bar for phone
 */
@Composable
private fun PhoneBottomNavigation(
    selectedTab: PhoneTab,
    onTabSelected: (PhoneTab) -> Unit
) {
    NavigationBar(
        containerColor = Color(0xFF1E1E1E),
        contentColor = Color.White
    ) {
        PhoneTab.entries.forEach { tab ->
            NavigationBarItem(
                selected = selectedTab == tab,
                onClick = { onTabSelected(tab) },
                icon = {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = tab.label
                    )
                },
                label = { Text(tab.label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Color(0xFFE91E63),
                    selectedTextColor = Color(0xFFE91E63),
                    unselectedIconColor = Color(0xFFB0B0B0),
                    unselectedTextColor = Color(0xFFB0B0B0),
                    indicatorColor = Color(0xFF2D2D2D)
                )
            )
        }
    }
}

/**
 * Phone navigation tabs
 * Uses same icon for selected/unselected - color differentiates
 */
enum class PhoneTab(
    val label: String,
    val icon: ImageVector
) {
    HOME("Home", Icons.Filled.Home),
    MOVIES("Movies", Icons.Filled.Star),
    SHOWS("Shows", Icons.Filled.DateRange),
    SEARCH("Search", Icons.Filled.Search),
    LIBRARY("Library", Icons.Filled.AccountCircle)
}
