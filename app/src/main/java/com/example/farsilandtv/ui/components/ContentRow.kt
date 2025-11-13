package com.example.farsilandtv.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
// Paging support will be added in Week 2 of migration
// import androidx.paging.compose.LazyPagingItems
// TvLazyRow will be used once we optimize for TV in Week 4
// import androidx.tv.foundation.lazy.list.TvLazyRow
// import androidx.tv.foundation.lazy.list.items
import com.example.farsilandtv.data.models.Movie
import com.example.farsilandtv.data.models.Series

/**
 * Feature #16: Compose Component - Content Row
 * Horizontal scrolling row of content cards (like Netflix)
 *
 * Integrates with:
 * - Feature #18: Paging 3 for infinite scroll
 * - Feature #20: Skeleton screens for loading states
 * - Feature #21: Focus memory for navigation
 */

/**
 * Movie row with List<Movie>
 * Paging integration will be added in Week 2 of migration
 */
@Composable
fun MovieRow(
    title: String,
    movies: List<Movie>,
    onMovieClick: (Movie) -> Unit,
    getFavoriteStatus: (Int) -> Boolean = { false },
    getWatchedStatus: (Int) -> Boolean = { false },
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.padding(vertical = 16.dp)) {
        // Row title
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(start = 48.dp, end = 48.dp, bottom = 16.dp)
        )

        // Horizontal scrolling row
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(movies) { movie ->
                MovieCard(
                    movie = movie,
                    onClick = { onMovieClick(movie) },
                    isFavorite = getFavoriteStatus(movie.id),
                    isWatched = getWatchedStatus(movie.id)
                )
            }
        }
    }
}

/**
 * Series row with List<Series>
 * Paging integration will be added in Week 2 of migration
 */
@Composable
fun SeriesRow(
    title: String,
    series: List<Series>,
    onSeriesClick: (Series) -> Unit,
    getFavoriteStatus: (Int) -> Boolean = { false },
    getUnwatchedStatus: (Int) -> Boolean = { false },
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.padding(vertical = 16.dp)) {
        // Row title
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(start = 48.dp, end = 48.dp, bottom = 16.dp)
        )

        // Horizontal scrolling row
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(series) { show ->
                SeriesCard(
                    series = show,
                    onClick = { onSeriesClick(show) },
                    isFavorite = getFavoriteStatus(show.id),
                    hasUnwatchedEpisodes = getUnwatchedStatus(show.id)
                )
            }
        }
    }
}

/**
 * Generic content row for mixed content types
 */
@Composable
fun <T> ContentRow(
    title: String,
    content: List<T>,
    onContentClick: (T) -> Unit,
    cardContent: @Composable (T, () -> Unit) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.padding(vertical = 16.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(start = 48.dp, end = 48.dp, bottom = 16.dp)
        )

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(content) { item ->
                cardContent(item) {
                    onContentClick(item)
                }
            }
        }
    }
}

/**
 * Loading skeleton row (Feature #20)
 */
@Composable
fun ContentRowSkeleton(
    title: String,
    itemCount: Int = 10,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.padding(vertical = 16.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(start = 48.dp, end = 48.dp, bottom = 16.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(itemCount) { _ ->
                MovieCardSkeleton()
            }
        }
    }
}
