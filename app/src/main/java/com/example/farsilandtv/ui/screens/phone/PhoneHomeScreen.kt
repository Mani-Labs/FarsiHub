package com.example.farsilandtv.ui.screens.phone

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import com.example.farsilandtv.data.database.ContinueWatchingItem
import com.example.farsilandtv.data.database.Favorite
import com.example.farsilandtv.data.download.DownloadItem
import com.example.farsilandtv.data.download.DownloadStatus
import com.example.farsilandtv.data.models.Episode
import com.example.farsilandtv.data.models.FeaturedContent
import com.example.farsilandtv.data.models.Movie
import com.example.farsilandtv.data.models.Series
import com.example.farsilandtv.ui.components.*
import com.example.farsilandtv.ui.viewmodel.PhoneHomeViewModel
import kotlinx.coroutines.delay

/**
 * Phone Home Screen - Netflix-style vertical scrolling home
 *
 * Phase 4: Full implementation with:
 * - Auto-scrolling featured carousel
 * - Pull-to-refresh gesture (swipe down to refresh)
 * - Continue watching row
 * - Content rows with responsive cards
 * - Touch-optimized interactions
 * - Floating refresh button when scrolled
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PhoneHomeScreen(
    onMovieClick: (Movie) -> Unit,
    onSeriesClick: (Series) -> Unit,
    onEpisodeClick: (Episode) -> Unit,
    onFeaturedClick: (FeaturedContent) -> Unit,
    viewModel: PhoneHomeViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Observe all state from the dedicated ViewModel (single source of truth)
    val movies by viewModel.movies.collectAsState()
    val series by viewModel.series.collectAsState()
    val episodes by viewModel.episodes.collectAsState()
    val featuredContent by viewModel.featuredContent.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val error by viewModel.error.collectAsState()

    // User data from ViewModel (no direct repository access needed)
    val continueWatching by viewModel.continueWatching.collectAsState()
    val favorites by viewModel.favorites.collectAsState()
    val monitoredSeries by viewModel.monitoredSeries.collectAsState()
    val downloads by viewModel.downloads.collectAsState()
    val downloadProgress by viewModel.downloadProgress.collectAsState()

    // Content display
    Column(modifier = Modifier.fillMaxSize()) {
        // Offline indicator at top
        OfflineIndicator()

        Box(modifier = Modifier.weight(1f)) {
        when {
            // Loading state with skeletons (initial load only)
            isLoading && movies.isEmpty() && series.isEmpty() -> {
                PhoneHomeLoadingSkeleton()
            }
            // Error state
            error != null && movies.isEmpty() && series.isEmpty() -> {
                PhoneHomeErrorState(
                    errorMessage = error ?: "Unknown error",
                    onRetry = { viewModel.loadContent() }
                )
            }
            // Empty state (no content available)
            !isLoading && movies.isEmpty() && series.isEmpty() && episodes.isEmpty() -> {
                PhoneHomeEmptyState(onRefresh = { viewModel.loadContent() })
            }
            // Normal content state
            else -> {
                val listState = rememberLazyListState()

                // Track if user is at the top for pull-to-refresh
                val canRefresh by remember {
                    derivedStateOf {
                        listState.firstVisibleItemIndex == 0 &&
                        listState.firstVisibleItemScrollOffset < 10
                    }
                }

                // Pull distance tracking for swipe gesture
                var pullDistance by remember { mutableFloatStateOf(0f) }
                val pullThreshold = 120f // pixels to trigger refresh
                val maxPullDistance = 150f

                // Animated pull indicator height
                val pullIndicatorHeight by animateDpAsState(
                    targetValue = when {
                        isRefreshing -> 56.dp
                        pullDistance > 0 -> (pullDistance / 3).coerceAtMost(56f).dp
                        else -> 0.dp
                    },
                    label = "pullHeight"
                )

                // NestedScroll connection for pull-to-refresh (only captures overscroll)
                val nestedScrollConnection = remember(isRefreshing, canRefresh) {
                    object : NestedScrollConnection {
                        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                            // When pulling up (scrolling content down), reset pull distance
                            if (available.y < 0 && pullDistance > 0) {
                                val consumed = available.y.coerceAtLeast(-pullDistance)
                                pullDistance += consumed
                                return Offset(0f, consumed)
                            }
                            return Offset.Zero
                        }

                        override fun onPostScroll(
                            consumed: Offset,
                            available: Offset,
                            source: NestedScrollSource
                        ): Offset {
                            // Only capture overscroll when at top and pulling down
                            if (!isRefreshing && available.y > 0 && canRefresh) {
                                val newPull = (pullDistance + available.y).coerceIn(0f, maxPullDistance)
                                val consumed = newPull - pullDistance
                                pullDistance = newPull
                                return Offset(0f, consumed)
                            }
                            return Offset.Zero
                        }

                        override suspend fun onPreFling(available: Velocity): Velocity {
                            // Trigger refresh on fling release if threshold met
                            if (pullDistance > pullThreshold && !isRefreshing) {
                                viewModel.refresh()
                            }
                            pullDistance = 0f
                            return Velocity.Zero
                        }

                        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                            // Safety reset in case onPreFling wasn't called
                            pullDistance = 0f
                            return Velocity.Zero
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(nestedScrollConnection)
                ) {
                    // Pull-to-refresh indicator
                    if (pullIndicatorHeight > 0.dp) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(pullIndicatorHeight)
                                .background(Color(0xFF1E1E1E)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isRefreshing) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    CircularProgressIndicator(
                                        color = Color(0xFFE91E63),
                                        modifier = Modifier.size(24.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = "Refreshing...",
                                        color = Color.White,
                                        fontSize = 14.sp
                                    )
                                }
                            } else {
                                val progress = (pullDistance / pullThreshold).coerceIn(0f, 1f)
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Refresh,
                                        contentDescription = null,
                                        tint = Color(0xFFE91E63).copy(alpha = progress),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (pullDistance > pullThreshold) "Release to refresh" else "Pull to refresh",
                                        color = Color.White.copy(alpha = progress),
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }
                    }

                LazyColumn(
                    state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = pullIndicatorHeight)
                    .background(Color(0xFF121212)),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                // Featured Carousel with auto-scroll
                if (featuredContent.isNotEmpty()) {
                    item {
                        PhoneFeaturedCarousel(
                            featuredContent = featuredContent,
                            onFeaturedClick = onFeaturedClick
                        )
                    }
                }

                // Continue Watching Row (from WatchlistRepository)
                if (continueWatching.isNotEmpty()) {
                    item {
                        PhoneContentSection(
                            title = "Continue Watching",
                            subtitle = "${continueWatching.size} in progress"
                        ) {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(continueWatching.take(10)) { item ->
                                    // Extract numeric ID from item.id (e.g., "movie-123" -> 123)
                                    val numericId = item.id.substringAfter("-").toIntOrNull() ?: 0
                                    PhoneContinueWatchingItemCard(
                                        item = item,
                                        onClick = {
                                            when (item.contentType) {
                                                ContinueWatchingItem.ContentType.MOVIE -> {
                                                    movies.find { it.id == numericId }?.let { onMovieClick(it) }
                                                }
                                                ContinueWatchingItem.ContentType.EPISODE -> {
                                                    // Create episode from continue watching item
                                                    val episode = Episode(
                                                        id = item.episodeId ?: numericId,
                                                        seriesId = item.seriesId,
                                                        title = item.subtitle ?: item.title,
                                                        season = item.season ?: 1,
                                                        episode = item.episodeNumber ?: 1,
                                                        thumbnailUrl = item.posterUrl,
                                                        farsilandUrl = item.farsilandUrl
                                                    )
                                                    onEpisodeClick(episode)
                                                }
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // My Shows Row (monitored series)
                if (monitoredSeries.isNotEmpty()) {
                    item {
                        PhoneContentSection(
                            title = "My Shows",
                            subtitle = "${monitoredSeries.size} monitored"
                        ) {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(monitoredSeries.take(10)) { monitored ->
                                    // Convert MonitoredSeries to Series for the card
                                    val seriesItem = Series(
                                        id = monitored.id,
                                        title = monitored.title,
                                        posterUrl = monitored.posterUrl,
                                        backdropUrl = monitored.backdropUrl,
                                        farsilandUrl = monitored.farsilandUrl,
                                        totalSeasons = monitored.totalSeasons
                                    )
                                    PhoneSeriesCard(
                                        series = seriesItem,
                                        onClick = { onSeriesClick(seriesItem) },
                                        width = 130.dp
                                    )
                                }
                            }
                        }
                    }
                }

                // My Favorites Row
                val favoriteMovies = favorites.filter { it.contentType == Favorite.ContentType.MOVIE }
                    .mapNotNull { fav -> movies.find { it.id == fav.numericId } }
                val favoriteSeries = favorites.filter { it.contentType == Favorite.ContentType.SERIES }
                    .mapNotNull { fav -> series.find { it.id == fav.numericId } }
                if (favoriteMovies.isNotEmpty() || favoriteSeries.isNotEmpty()) {
                    item {
                        PhoneContentSection(
                            title = "My Favorites",
                            subtitle = "${favorites.size} items"
                        ) {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(favoriteMovies.take(8)) { movie ->
                                    PhoneMovieCard(
                                        movie = movie,
                                        onClick = { onMovieClick(movie) },
                                        width = 130.dp
                                    )
                                }
                                items(favoriteSeries.take(8)) { show ->
                                    PhoneSeriesCard(
                                        series = show,
                                        onClick = { onSeriesClick(show) },
                                        width = 130.dp
                                    )
                                }
                            }
                        }
                    }
                }

                // Downloads Row (completed and in-progress downloads)
                val completedDownloads = downloads.filter { it.status == DownloadStatus.COMPLETED }
                val activeDownloads = downloads.filter {
                    it.status == DownloadStatus.DOWNLOADING ||
                    it.status == DownloadStatus.PENDING ||
                    it.status == DownloadStatus.PAUSED
                }

                if (completedDownloads.isNotEmpty() || activeDownloads.isNotEmpty()) {
                    item {
                        val activeCount = activeDownloads.count { it.status != DownloadStatus.PAUSED }
                        val pausedCount = activeDownloads.count { it.status == DownloadStatus.PAUSED }
                        val subtitle = buildString {
                            append("${completedDownloads.size} ready")
                            if (activeCount > 0) append(", $activeCount downloading")
                            if (pausedCount > 0) append(", $pausedCount paused")
                        }

                        PhoneContentSection(
                            title = "Downloads",
                            subtitle = subtitle
                        ) {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // Show active downloads first
                                items(activeDownloads, key = { it.id }) { download ->
                                    val realTimeProgress = downloadProgress[download.id] ?: download.progress
                                    PhoneDownloadCard(
                                        download = download,
                                        realTimeProgress = realTimeProgress,
                                        onClick = { /* Can't play yet */ },
                                        onPauseResume = {
                                            when (download.status) {
                                                DownloadStatus.DOWNLOADING, DownloadStatus.PENDING -> {
                                                    viewModel.pauseDownload(download.id)
                                                    android.widget.Toast.makeText(context, "Download paused", android.widget.Toast.LENGTH_SHORT).show()
                                                }
                                                DownloadStatus.PAUSED -> {
                                                    viewModel.resumeDownload(download.id)
                                                    android.widget.Toast.makeText(context, "Download resumed", android.widget.Toast.LENGTH_SHORT).show()
                                                }
                                                else -> {}
                                            }
                                        },
                                        onCancel = {
                                            viewModel.cancelDownload(download.id)
                                            android.widget.Toast.makeText(context, "Download cancelled", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                }
                                // Then completed downloads
                                items(completedDownloads, key = { it.id }) { download ->
                                    PhoneDownloadCard(
                                        download = download,
                                        onClick = {
                                            // Play downloaded content - find matching movie/series
                                            when (download.contentType) {
                                                com.example.farsilandtv.data.download.ContentType.MOVIE -> {
                                                    val movieId = download.id.removePrefix("movie_").toIntOrNull() ?: 0
                                                    movies.find { it.id == movieId }?.let { onMovieClick(it) }
                                                }
                                                com.example.farsilandtv.data.download.ContentType.EPISODE -> {
                                                    // For episodes, navigate to series details
                                                    val episodeId = download.id.removePrefix("episode_").toIntOrNull() ?: 0
                                                    episodes.find { it.id == episodeId }?.let { onEpisodeClick(it) }
                                                }
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // Movies Row
                if (movies.isNotEmpty()) {
                    item {
                        PhoneContentSection(title = "Movies") {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(movies.take(15)) { movie ->
                                    PhoneMovieCard(
                                        movie = movie,
                                        onClick = { onMovieClick(movie) },
                                        width = 130.dp
                                    )
                                }
                            }
                        }
                    }
                }

                // TV Shows Row
                if (series.isNotEmpty()) {
                    item {
                        PhoneContentSection(title = "TV Shows") {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(series.take(15)) { show ->
                                    PhoneSeriesCard(
                                        series = show,
                                        onClick = { onSeriesClick(show) },
                                        width = 130.dp
                                    )
                                }
                            }
                        }
                    }
                }

                // Latest Episodes Row
                if (episodes.isNotEmpty()) {
                    item {
                        PhoneContentSection(title = "Latest Episodes") {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(episodes.take(10)) { episode ->
                                    PhoneEpisodeThumbnailCard(
                                        episode = episode,
                                        onClick = { onEpisodeClick(episode) }
                                    )
                                }
                            }
                        }
                    }
                }

                // More Movies (additional content beyond first row)
                if (movies.size > 15) {
                    item {
                        PhoneContentSection(title = "More Movies") {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(movies.drop(15).take(15)) { movie ->
                                    PhoneMovieCard(
                                        movie = movie,
                                        onClick = { onMovieClick(movie) },
                                        width = 130.dp
                                    )
                                }
                            }
                        }
                    }
                }

                // Popular Shows
                if (series.size > 15) {
                    item {
                        PhoneContentSection(title = "Popular Shows") {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(series.drop(15).take(15)) { show ->
                                    PhoneSeriesCard(
                                        series = show,
                                        onClick = { onSeriesClick(show) },
                                        width = 130.dp
                                    )
                                }
                            }
                        }
                    }
                }
            }

                    // Floating refresh button when scrolled (backup for users who prefer tap)
                    val showRefreshButton by remember {
                        derivedStateOf { listState.firstVisibleItemIndex > 2 && !isRefreshing }
                    }
                    if (showRefreshButton) {
                        FloatingActionButton(
                            onClick = { viewModel.refresh() },
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(16.dp),
                            containerColor = Color(0xFFE91E63)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = "Refresh",
                                tint = Color.White
                            )
                        }
                    }
                } // End of Box with nestedScroll
            }
        }
        } // End of Box with weight
    } // End of Column
}

/**
 * Error state for when content fails to load
 */
@Composable
private fun PhoneHomeErrorState(
    errorMessage: String,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = null,
                tint = Color(0xFFE91E63),
                modifier = Modifier.size(64.dp)
            )
            Text(
                text = "Something went wrong",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = errorMessage,
                color = Color(0xFF808080),
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp)
            )
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFE91E63)
                )
            ) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Try Again")
            }
        }
    }
}

/**
 * Empty state when no content is available
 */
@Composable
private fun PhoneHomeEmptyState(
    onRefresh: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "🎬",
                fontSize = 64.sp
            )
            Text(
                text = "No content available",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Pull down to refresh or check your connection",
                color = Color(0xFF808080),
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp)
            )
            Button(
                onClick = onRefresh,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFE91E63)
                )
            ) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Refresh")
            }
        }
    }
}

/**
 * Featured carousel with auto-scroll and page indicators
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PhoneFeaturedCarousel(
    featuredContent: List<FeaturedContent>,
    onFeaturedClick: (FeaturedContent) -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { featuredContent.size })

    // Auto-scroll effect with proper cancellation check
    LaunchedEffect(pagerState, featuredContent.size) {
        if (featuredContent.isEmpty()) return@LaunchedEffect
        while (isActive) {
            delay(5000) // 5 seconds per slide
            if (isActive && featuredContent.isNotEmpty()) {
                val nextPage = (pagerState.currentPage + 1) % featuredContent.size
                pagerState.animateScrollToPage(nextPage)
            }
        }
    }

    Column {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
        ) { page ->
            val content = featuredContent[page]
            PhoneFeaturedItem(
                content = content,
                onClick = { onFeaturedClick(content) }
            )
        }

        // Page indicators
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            repeat(featuredContent.size) { index ->
                val isSelected = pagerState.currentPage == index
                val size by animateDpAsState(if (isSelected) 8.dp else 6.dp, label = "indicator")
                val color by animateColorAsState(
                    if (isSelected) Color(0xFFE91E63) else Color(0xFF666666),
                    label = "indicatorColor"
                )
                Box(
                    modifier = Modifier
                        .padding(horizontal = 3.dp)
                        .size(size)
                        .clip(CircleShape)
                        .background(color)
                )
            }
        }
    }
}

/**
 * Single featured item in carousel
 */
@Composable
private fun PhoneFeaturedItem(
    content: FeaturedContent,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = content.backdropUrl ?: content.posterUrl,
            contentDescription = content.title,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        // Gradient overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color(0xFF121212).copy(alpha = 0.7f),
                            Color(0xFF121212)
                        ),
                        startY = 80f
                    )
                )
        )

        // Content type badge
        Surface(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp),
            color = Color(0xFFE91E63),
            shape = RoundedCornerShape(4.dp)
        ) {
            Text(
                text = content.contentType.uppercase(),
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // Title and play button
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = content.title,
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = content.description,
                    color = Color(0xFFB0B0B0),
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Play button
            FilledIconButton(
                onClick = onClick,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = Color(0xFFE91E63)
                )
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = "Play",
                    tint = Color.White
                )
            }
        }
    }
}

/**
 * Section header with title
 */
@Composable
private fun PhoneContentSection(
    title: String,
    subtitle: String? = null,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 20.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )
            subtitle?.let {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = it,
                    color = Color(0xFF808080),
                    fontSize = 12.sp
                )
            }
        }
        content()
    }
}

/**
 * Continue watching card for ContinueWatchingItem (from database)
 */
@Composable
private fun PhoneContinueWatchingItemCard(
    item: ContinueWatchingItem,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(180.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
    ) {
        Column {
            Box {
                AsyncImage(
                    model = item.posterUrl,
                    contentDescription = item.title,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f),
                    contentScale = ContentScale.Crop
                )

                // Play button overlay
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = "Play",
                        tint = Color.White,
                        modifier = Modifier.size(40.dp)
                    )
                }

                // Type badge (Movie or Episode number)
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp),
                    color = Color.Black.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = when (item.contentType) {
                            ContinueWatchingItem.ContentType.MOVIE -> "Movie"
                            ContinueWatchingItem.ContentType.EPISODE -> item.subtitle ?: "Episode"
                        },
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Progress bar
                LinearProgressIndicator(
                    progress = { item.progressPercentage / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .align(Alignment.BottomCenter),
                    color = Color(0xFFE91E63),
                    trackColor = Color.Black.copy(alpha = 0.5f)
                )
            }

            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    text = item.title,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                item.subtitle?.let {
                    Text(
                        text = it,
                        color = Color(0xFF808080),
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/**
 * Episode thumbnail card for Latest Episodes row
 */
@Composable
private fun PhoneEpisodeThumbnailCard(
    episode: Episode,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(160.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
    ) {
        Column {
            Box {
                AsyncImage(
                    model = episode.thumbnailUrl ?: episode.episodePosterUrl,
                    contentDescription = episode.title,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f),
                    contentScale = ContentScale.Crop
                )

                // Episode badge
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(6.dp),
                    color = Color.Black.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = episode.formattedNumber,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    text = episode.seriesTitle ?: "Episode",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = episode.title,
                    color = Color(0xFF808080),
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Download card for showing downloaded content with interactive controls
 */
@Composable
private fun PhoneDownloadCard(
    download: DownloadItem,
    realTimeProgress: Int = download.progress,
    onClick: () -> Unit,
    onPauseResume: () -> Unit = {},
    onCancel: () -> Unit = {}
) {
    val isActive = download.status == DownloadStatus.DOWNLOADING ||
                   download.status == DownloadStatus.PENDING ||
                   download.status == DownloadStatus.PAUSED

    Card(
        modifier = Modifier
            .width(160.dp)
            .clickable(
                enabled = download.status == DownloadStatus.COMPLETED,
                onClick = onClick
            ),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
    ) {
        Column {
            Box {
                AsyncImage(
                    model = download.posterUrl,
                    contentDescription = download.title,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f),
                    contentScale = ContentScale.Crop
                )

                // Status overlay based on download status
                when (download.status) {
                    DownloadStatus.COMPLETED -> {
                        // Play icon overlay
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.3f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = "Play",
                                tint = Color.White,
                                modifier = Modifier.size(40.dp)
                            )
                        }
                    }
                    DownloadStatus.DOWNLOADING, DownloadStatus.PENDING -> {
                        // Progress overlay with pause button
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.6f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                // Tap to pause
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.5f))
                                        .clickable { onPauseResume() },
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(
                                        progress = { realTimeProgress.coerceIn(0, 100) / 100f },
                                        modifier = Modifier.size(40.dp),
                                        color = Color(0xFFE91E63),
                                        strokeWidth = 3.dp
                                    )
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Pause",
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "${realTimeProgress}%",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    DownloadStatus.PAUSED -> {
                        // Paused overlay with resume button
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.7f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Resume button
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF4CAF50))
                                            .clickable { onPauseResume() },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = "Resume",
                                            tint = Color.White,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                    // Cancel button
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFFD32F2F))
                                            .clickable { onCancel() },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Cancel",
                                            tint = Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Paused ${realTimeProgress}%",
                                    color = Color(0xFFFFA726),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    DownloadStatus.FAILED -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Red.copy(alpha = 0.4f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Failed",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    else -> {}
                }

                // Downloaded badge for completed items
                if (download.status == DownloadStatus.COMPLETED) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp),
                        color = Color(0xFF4CAF50),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "Downloaded",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    text = download.title,
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                download.subtitle?.let {
                    Text(
                        text = it,
                        color = Color(0xFF808080),
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                // File size for completed downloads
                if (download.status == DownloadStatus.COMPLETED && download.fileSize > 0) {
                    Text(
                        text = formatFileSize(download.fileSize),
                        color = Color(0xFF4CAF50),
                        fontSize = 9.sp
                    )
                }
                // Status for active downloads
                if (isActive) {
                    Text(
                        text = when (download.status) {
                            DownloadStatus.PAUSED -> "Tap to resume"
                            else -> "Tap to pause"
                        },
                        color = Color(0xFFE91E63),
                        fontSize = 9.sp
                    )
                }
            }
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
 * Loading skeleton for home screen
 */
@Composable
private fun PhoneHomeLoadingSkeleton() {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212)),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        // Featured skeleton
        item {
            PhoneFeaturedCardSkeleton(
                modifier = Modifier.padding(horizontal = 0.dp)
            )
        }

        // Content rows skeleton
        repeat(3) {
            item {
                Column(modifier = Modifier.padding(top = 20.dp)) {
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .width(100.dp)
                            .height(18.dp)
                            .background(shimmerBrush(), RoundedCornerShape(4.dp))
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(5) {
                            PhoneMovieCardSkeleton(width = 130.dp)
                        }
                    }
                }
            }
        }
    }
}
