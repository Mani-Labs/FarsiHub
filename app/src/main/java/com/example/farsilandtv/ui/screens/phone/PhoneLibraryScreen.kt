package com.example.farsilandtv.ui.screens.phone

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.farsilandtv.data.database.Favorite
import com.example.farsilandtv.data.database.PlaylistWithItems
import com.example.farsilandtv.data.download.DownloadItem
import com.example.farsilandtv.data.download.DownloadManager
import com.example.farsilandtv.data.download.DownloadStatus
import com.example.farsilandtv.data.models.Movie
import com.example.farsilandtv.data.models.Series
import com.example.farsilandtv.data.repository.FavoritesRepository
import com.example.farsilandtv.data.repository.PlaylistRepository
import com.example.farsilandtv.data.repository.WatchlistRepository
import com.example.farsilandtv.ui.components.PhoneMovieCard
import com.example.farsilandtv.ui.components.PhoneSeriesCard
import com.example.farsilandtv.ui.components.OfflineIndicator
import com.example.farsilandtv.ui.viewmodel.MainViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Phone Library Screen - Hub for user's personal content
 *
 * Contains tabs for:
 * - Favorites (movies and series)
 * - Downloads (offline content)
 * - Playlists (user-created collections)
 *
 * Design: Netflix/Disney+ style with horizontal tabs at top
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhoneLibraryScreen(
    favoritesRepo: FavoritesRepository,
    watchlistRepo: WatchlistRepository,
    playlistRepo: PlaylistRepository,
    downloadManager: DownloadManager,
    onMovieClick: (Movie) -> Unit,
    onSeriesClick: (Series) -> Unit,
    onSettingsClick: () -> Unit,
    viewModel: MainViewModel
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Tab state
    var selectedTab by remember { mutableStateOf(LibraryTab.FAVORITES) }

    // Data state
    var favorites by remember { mutableStateOf<List<Favorite>>(emptyList()) }
    var downloads by remember { mutableStateOf<List<DownloadItem>>(emptyList()) }
    val movies by viewModel.recentMovies.observeAsState(emptyList())
    val series by viewModel.recentSeries.observeAsState(emptyList())

    // Load data
    LaunchedEffect(Unit) {
        coroutineScope.launch {
            favorites = favoritesRepo.getAllFavorites().first()
        }
        // Collect downloads in same effect block
        downloadManager.getAllDownloads().collect { items ->
            downloads = items
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
    ) {
        // Offline indicator
        OfflineIndicator()

        // Header with settings button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "My Library",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            IconButton(onClick = onSettingsClick) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = "Settings",
                    tint = Color.White
                )
            }
        }

        // Tab row
        TabRow(
            selectedTabIndex = selectedTab.ordinal,
            containerColor = Color(0xFF1E1E1E),
            contentColor = Color.White
        ) {
            LibraryTab.entries.forEach { tab ->
                Tab(
                    selected = selectedTab == tab,
                    onClick = { selectedTab = tab },
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(tab.label)
                        }
                    },
                    selectedContentColor = Color(0xFFE91E63),
                    unselectedContentColor = Color(0xFFB0B0B0)
                )
            }
        }

        // Content based on selected tab
        when (selectedTab) {
            LibraryTab.FAVORITES -> FavoritesContent(
                favorites = favorites,
                movies = movies,
                series = series,
                onMovieClick = onMovieClick,
                onSeriesClick = onSeriesClick,
                onRemoveFavorite = { favorite ->
                    coroutineScope.launch {
                        when (favorite.contentType) {
                            Favorite.ContentType.MOVIE -> favoritesRepo.removeMovieFromFavorites(favorite.numericId)
                            Favorite.ContentType.SERIES -> favoritesRepo.removeSeriesFromFavorites(favorite.numericId)
                        }
                        favorites = favoritesRepo.getAllFavorites().first()
                    }
                }
            )
            LibraryTab.DOWNLOADS -> DownloadsContent(
                downloads = downloads,
                movies = movies,
                onMovieClick = onMovieClick,
                downloadManager = downloadManager
            )
            LibraryTab.PLAYLISTS -> PlaylistsContent(
                playlistRepo = playlistRepo
            )
        }
    }
}

/**
 * Favorites tab content
 */
@Composable
private fun FavoritesContent(
    favorites: List<Favorite>,
    movies: List<Movie>,
    series: List<Series>,
    onMovieClick: (Movie) -> Unit,
    onSeriesClick: (Series) -> Unit,
    onRemoveFavorite: (Favorite) -> Unit
) {
    if (favorites.isEmpty()) {
        EmptyStateMessage(
            icon = Icons.Filled.FavoriteBorder,
            title = "No Favorites Yet",
            message = "Long-press on any movie or series to add it to your favorites"
        )
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            // Favorite movies
            val favoriteMovies = favorites.filter { it.contentType == Favorite.ContentType.MOVIE }
            if (favoriteMovies.isNotEmpty()) {
                item {
                    SectionHeader(title = "Movies", count = favoriteMovies.size)
                }
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(favoriteMovies) { favorite ->
                            val movie = movies.find { it.id == favorite.numericId }
                            if (movie != null) {
                                PhoneMovieCard(
                                    movie = movie,
                                    onClick = { onMovieClick(movie) },
                                    width = 130.dp,
                                    isFavorite = true,
                                    onLongClick = { onRemoveFavorite(favorite) }
                                )
                            }
                        }
                    }
                }
            }

            // Favorite series
            val favoriteSeries = favorites.filter { it.contentType == Favorite.ContentType.SERIES }
            if (favoriteSeries.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(24.dp))
                    SectionHeader(title = "TV Shows", count = favoriteSeries.size)
                }
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(favoriteSeries) { favorite ->
                            val show = series.find { it.id == favorite.numericId }
                            if (show != null) {
                                PhoneSeriesCard(
                                    series = show,
                                    onClick = { onSeriesClick(show) },
                                    width = 130.dp,
                                    isFavorite = true,
                                    onLongClick = { onRemoveFavorite(favorite) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Downloads tab content
 */
@Composable
private fun DownloadsContent(
    downloads: List<DownloadItem>,
    movies: List<Movie>,
    onMovieClick: (Movie) -> Unit,
    downloadManager: DownloadManager
) {
    val coroutineScope = rememberCoroutineScope()
    val completedDownloads = downloads.filter { it.status == DownloadStatus.COMPLETED }
    val activeDownloads = downloads.filter {
        it.status == DownloadStatus.DOWNLOADING ||
        it.status == DownloadStatus.PENDING ||
        it.status == DownloadStatus.PAUSED
    }

    if (downloads.isEmpty()) {
        EmptyStateMessage(
            icon = Icons.Filled.KeyboardArrowDown,
            title = "No Downloads",
            message = "Download movies and episodes to watch offline"
        )
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Storage info
            item {
                val totalSize = completedDownloads.sumOf { it.fileSize }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Storage Used",
                                color = Color(0xFFB0B0B0),
                                fontSize = 12.sp
                            )
                            Text(
                                text = formatFileSize(totalSize),
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "${completedDownloads.size} items",
                            color = Color(0xFFE91E63),
                            fontSize = 14.sp
                        )
                    }
                }
            }

            // Active downloads
            if (activeDownloads.isNotEmpty()) {
                item {
                    SectionHeader(title = "In Progress", count = activeDownloads.size)
                }
                items(activeDownloads) { download ->
                    DownloadItemCard(
                        download = download,
                        onPause = {
                            coroutineScope.launch { downloadManager.pauseDownload(download.id) }
                        },
                        onResume = {
                            coroutineScope.launch { downloadManager.resumeDownload(download.id) }
                        },
                        onCancel = {
                            coroutineScope.launch { downloadManager.cancelDownload(download.id) }
                        }
                    )
                }
            }

            // Completed downloads
            if (completedDownloads.isNotEmpty()) {
                item {
                    SectionHeader(title = "Ready to Watch", count = completedDownloads.size)
                }
                items(completedDownloads) { download ->
                    DownloadItemCard(
                        download = download,
                        onClick = {
                            // Find and play movie
                            val movieId = download.id.removePrefix("movie_").toIntOrNull() ?: 0
                            movies.find { it.id == movieId }?.let { onMovieClick(it) }
                        },
                        onDelete = {
                            coroutineScope.launch { downloadManager.deleteDownload(download.id) }
                        }
                    )
                }
            }
        }
    }
}

/**
 * Playlists tab content
 */
@Composable
private fun PlaylistsContent(
    playlistRepo: PlaylistRepository
) {
    val coroutineScope = rememberCoroutineScope()
    var playlists by remember { mutableStateOf<List<com.example.farsilandtv.data.database.Playlist>>(emptyList()) }

    LaunchedEffect(Unit) {
        coroutineScope.launch {
            playlists = playlistRepo.getAllPlaylists().first()
        }
    }

    if (playlists.isEmpty()) {
        EmptyStateMessage(
            icon = Icons.Filled.Add,
            title = "No Playlists",
            message = "Create playlists to organize your favorite content"
        )
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(playlists) { playlist ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { /* TODO: Open playlist */ },
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.List,
                            contentDescription = null,
                            tint = Color(0xFFE91E63),
                            modifier = Modifier.size(40.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = playlist.name,
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium
                            )
                            playlist.description?.let { desc ->
                                Text(
                                    text = desc,
                                    color = Color(0xFFB0B0B0),
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Icon(
                            imageVector = Icons.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = Color(0xFF808080)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Download item card for list
 */
@Composable
private fun DownloadItemCard(
    download: DownloadItem,
    onClick: (() -> Unit)? = null,
    onPause: (() -> Unit)? = null,
    onResume: (() -> Unit)? = null,
    onCancel: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Status icon
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        color = when (download.status) {
                            DownloadStatus.COMPLETED -> Color(0xFF4CAF50)
                            DownloadStatus.DOWNLOADING, DownloadStatus.PENDING -> Color(0xFFE91E63)
                            DownloadStatus.PAUSED -> Color(0xFFFFA726)
                            else -> Color(0xFF808080)
                        }.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(8.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                when (download.status) {
                    DownloadStatus.COMPLETED -> Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF4CAF50)
                    )
                    DownloadStatus.DOWNLOADING, DownloadStatus.PENDING -> CircularProgressIndicator(
                        progress = { download.progress / 100f },
                        modifier = Modifier.size(28.dp),
                        color = Color(0xFFE91E63),
                        strokeWidth = 3.dp
                    )
                    DownloadStatus.PAUSED -> Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = null,
                        tint = Color(0xFFFFA726)
                    )
                    else -> Icon(
                        imageVector = Icons.Filled.Warning,
                        contentDescription = null,
                        tint = Color(0xFFD32F2F)
                    )
                }
            }

            // Info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = download.title,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                download.subtitle?.let {
                    Text(
                        text = it,
                        color = Color(0xFFB0B0B0),
                        fontSize = 12.sp,
                        maxLines = 1
                    )
                }
                when (download.status) {
                    DownloadStatus.DOWNLOADING, DownloadStatus.PENDING -> {
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { download.progress / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp),
                            color = Color(0xFFE91E63),
                            trackColor = Color(0xFF333333)
                        )
                        Text(
                            text = "${download.progress}%",
                            color = Color(0xFFE91E63),
                            fontSize = 11.sp
                        )
                    }
                    DownloadStatus.COMPLETED -> {
                        Text(
                            text = formatFileSize(download.fileSize),
                            color = Color(0xFF4CAF50),
                            fontSize = 12.sp
                        )
                    }
                    DownloadStatus.PAUSED -> {
                        Text(
                            text = "Paused - ${download.progress}%",
                            color = Color(0xFFFFA726),
                            fontSize = 12.sp
                        )
                    }
                    else -> {}
                }
            }

            // Action buttons
            when (download.status) {
                DownloadStatus.DOWNLOADING, DownloadStatus.PENDING -> {
                    IconButton(onClick = { onPause?.invoke() }) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = "Pause",
                            tint = Color.White
                        )
                    }
                    IconButton(onClick = { onCancel?.invoke() }) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Cancel",
                            tint = Color(0xFFD32F2F)
                        )
                    }
                }
                DownloadStatus.PAUSED -> {
                    IconButton(onClick = { onResume?.invoke() }) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = "Resume",
                            tint = Color(0xFF4CAF50)
                        )
                    }
                    IconButton(onClick = { onCancel?.invoke() }) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Cancel",
                            tint = Color(0xFFD32F2F)
                        )
                    }
                }
                DownloadStatus.COMPLETED -> {
                    IconButton(onClick = { onDelete?.invoke() }) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = "Delete",
                            tint = Color(0xFFD32F2F)
                        )
                    }
                }
                else -> {}
            }
        }
    }
}

/**
 * Section header
 */
@Composable
private fun SectionHeader(title: String, count: Int? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold
        )
        count?.let {
            Text(
                text = "$it",
                color = Color(0xFF808080),
                fontSize = 14.sp
            )
        }
    }
}

/**
 * Empty state message
 */
@Composable
private fun EmptyStateMessage(
    icon: ImageVector,
    title: String,
    message: String
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color(0xFF4A4A4A),
                modifier = Modifier.size(64.dp)
            )
            Text(
                text = title,
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = message,
                color = Color(0xFF808080),
                fontSize = 14.sp
            )
        }
    }
}

/**
 * Format file size for display
 */
private fun formatFileSize(bytes: Long): String {
    return when {
        bytes >= 1_000_000_000 -> String.format("%.1f GB", bytes / 1_000_000_000.0)
        bytes >= 1_000_000 -> String.format("%.1f MB", bytes / 1_000_000.0)
        bytes >= 1_000 -> String.format("%.1f KB", bytes / 1_000.0)
        else -> "$bytes B"
    }
}

/**
 * Library tabs
 */
private enum class LibraryTab(
    val label: String,
    val icon: ImageVector
) {
    FAVORITES("Favorites", Icons.Filled.Favorite),
    DOWNLOADS("Downloads", Icons.Filled.KeyboardArrowDown),
    PLAYLISTS("Playlists", Icons.Filled.List)
}

// Extension to observe LiveData as State
@Composable
private fun <T> androidx.lifecycle.LiveData<T>.observeAsState(initial: T): State<T> {
    val state = remember { mutableStateOf(initial) }
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(this, lifecycleOwner) {
        val observer = androidx.lifecycle.Observer<T> { value ->
            state.value = value
        }
        observe(lifecycleOwner, observer)
        onDispose { removeObserver(observer) }
    }
    return state
}
