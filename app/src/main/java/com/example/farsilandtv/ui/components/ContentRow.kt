package com.example.farsilandtv.ui.components

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.*
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import com.example.farsilandtv.data.models.Movie
import com.example.farsilandtv.data.models.Series

/**
 * Feature #16: Compose Component - Content Row
 * Horizontal scrolling row of content cards (like Netflix)
 * Scaled down for more elegant design - fits more items on screen
 */

/**
 * Movie row with List<Movie>
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun MovieRow(
    title: String,
    movies: List<Movie>,
    onMovieClick: (Movie) -> Unit,
    getFavoriteStatus: (Int) -> Boolean = { false },
    getWatchedStatus: (Int) -> Boolean = { false },
    onOpenSidebar: () -> Unit = {},
    onCloseSidebar: () -> Unit = {},
    onOpenSidebarWithFocus: () -> Unit = {},
    onMovieLongClick: ((Movie) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    Column(modifier = modifier.padding(vertical = 8.dp)) {
        // Row title - smaller for elegance
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = Color.White,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp)
        )

        // Horizontal scrolling row - tighter spacing
        LazyRow(
            state = listState,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // FIXED: Added stable key for efficient recomposition
            itemsIndexed(movies, key = { _, movie -> "movie-${movie.id}" }) { index, movie ->
                MovieCard(
                    movie = movie,
                    onClick = { onMovieClick(movie) },
                    isFavorite = getFavoriteStatus(movie.id),
                    isWatched = getWatchedStatus(movie.id),
                    onLongClick = onMovieLongClick?.let { { it(movie) } },
                    modifier = Modifier
                        .onPreviewKeyEvent { keyEvent ->
                            if (keyEvent.type == KeyEventType.KeyDown) {
                                when (keyEvent.key) {
                                    Key.Back -> {
                                        onOpenSidebar()
                                        true
                                    }
                                    Key.DirectionLeft -> {
                                        if (index == 0) {
                                            onOpenSidebarWithFocus()
                                            true
                                        } else false
                                    }
                                    else -> false
                                }
                            } else false
                        }
                        .onFocusChanged { focusState ->
                            if (focusState.isFocused) {
                                onCloseSidebar()
                            }
                        }
                )
            }
        }
    }
}

/**
 * Series row with List<Series>
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SeriesRow(
    title: String,
    series: List<Series>,
    onSeriesClick: (Series) -> Unit,
    getFavoriteStatus: (Int) -> Boolean = { false },
    getUnwatchedStatus: (Int) -> Boolean = { false },
    onOpenSidebar: () -> Unit = {},
    onCloseSidebar: () -> Unit = {},
    onOpenSidebarWithFocus: () -> Unit = {},
    onSeriesLongClick: ((Series) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    Column(modifier = modifier.padding(vertical = 8.dp)) {
        // Row title - smaller for elegance
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = Color.White,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp)
        )

        // Horizontal scrolling row - tighter spacing
        LazyRow(
            state = listState,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // FIXED: Added stable key for efficient recomposition
            itemsIndexed(series, key = { _, show -> "series-${show.id}" }) { index, show ->
                SeriesCard(
                    series = show,
                    onClick = { onSeriesClick(show) },
                    isFavorite = getFavoriteStatus(show.id),
                    hasUnwatchedEpisodes = getUnwatchedStatus(show.id),
                    onLongClick = onSeriesLongClick?.let { { it(show) } },
                    modifier = Modifier
                        .onPreviewKeyEvent { keyEvent ->
                            if (keyEvent.type == KeyEventType.KeyDown) {
                                when (keyEvent.key) {
                                    Key.Back -> {
                                        onOpenSidebar()
                                        true
                                    }
                                    Key.DirectionLeft -> {
                                        if (index == 0) {
                                            onOpenSidebarWithFocus()
                                            true
                                        } else false
                                    }
                                    else -> false
                                }
                            } else false
                        }
                        .onFocusChanged { focusState ->
                            if (focusState.isFocused) {
                                onCloseSidebar()
                            }
                        }
                )
            }
        }
    }
}

/**
 * Generic content row for mixed content types
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun <T> ContentRow(
    title: String,
    content: List<T>,
    onContentClick: (T) -> Unit,
    cardContent: @Composable (T, () -> Unit, Modifier) -> Unit,
    onOpenSidebar: () -> Unit = {},
    onCloseSidebar: () -> Unit = {},
    onOpenSidebarWithFocus: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    Column(modifier = modifier.padding(vertical = 8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = Color.White,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp)
        )

        LazyRow(
            state = listState,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(content) { index, item ->
                cardContent(
                    item,
                    { onContentClick(item) },
                    Modifier
                        .onPreviewKeyEvent { keyEvent ->
                            if (keyEvent.type == KeyEventType.KeyDown) {
                                when (keyEvent.key) {
                                    Key.Back -> {
                                        onOpenSidebar()
                                        true
                                    }
                                    Key.DirectionLeft -> {
                                        if (index == 0) {
                                            onOpenSidebarWithFocus()
                                            true
                                        } else false
                                    }
                                    else -> false
                                }
                            } else false
                        }
                        .onFocusChanged { focusState ->
                            if (focusState.isFocused) {
                                onCloseSidebar()
                            }
                        }
                )
            }
        }
    }
}

/**
 * Loading skeleton row
 */
@Composable
fun ContentRowSkeleton(
    title: String,
    itemCount: Int = 12,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.padding(vertical = 8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(itemCount) { _ ->
                MovieCardSkeleton()
            }
        }
    }
}
