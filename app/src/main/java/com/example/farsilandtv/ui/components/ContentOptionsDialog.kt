package com.example.farsilandtv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.farsilandtv.data.models.Movie
import com.example.farsilandtv.data.models.Series

/**
 * Content type for options dialog
 */
sealed class ContentOptionsItem {
    abstract val id: Int
    abstract val title: String
    abstract val posterUrl: String?
    abstract val farsilandUrl: String

    data class MovieItem(val movie: Movie) : ContentOptionsItem() {
        override val id = movie.id
        override val title = movie.title
        override val posterUrl = movie.posterUrl
        override val farsilandUrl = movie.farsilandUrl
    }

    data class SeriesItem(val series: Series) : ContentOptionsItem() {
        override val id = series.id
        override val title = series.title
        override val posterUrl = series.posterUrl
        override val farsilandUrl = series.farsilandUrl
    }
}

/**
 * Options dialog for content items (long-press menu)
 * Shows add/remove options for Watchlist, Favorites, Monitored
 */
@Composable
fun ContentOptionsDialog(
    item: ContentOptionsItem,
    onDismiss: () -> Unit,
    // Status flags
    isInWatchlist: Boolean = false,
    isInFavorites: Boolean = false,
    isMonitored: Boolean = false,
    // Actions
    onToggleWatchlist: () -> Unit = {},
    onToggleFavorites: () -> Unit = {},
    onToggleMonitored: () -> Unit = {},
    onRemoveFromContinueWatching: (() -> Unit)? = null
) {
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .width(320.dp)
                .wrapContentHeight(),
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFF2A2A2A),
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                // Title
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Watchlist option (for Movies)
                if (item is ContentOptionsItem.MovieItem) {
                    OptionButton(
                        text = if (isInWatchlist) "✓ Remove from Watchlist" else "+ Add to Watchlist",
                        isActive = isInWatchlist,
                        onClick = {
                            onToggleWatchlist()
                            onDismiss()
                        },
                        modifier = Modifier.focusRequester(focusRequester)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Monitored option (for Series)
                if (item is ContentOptionsItem.SeriesItem) {
                    OptionButton(
                        text = if (isMonitored) "✓ Stop Monitoring" else "+ Monitor Series",
                        isActive = isMonitored,
                        onClick = {
                            onToggleMonitored()
                            onDismiss()
                        },
                        modifier = Modifier.focusRequester(focusRequester)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Favorites option (for both)
                OptionButton(
                    text = if (isInFavorites) "✓ Remove from Favorites" else "+ Add to Favorites",
                    isActive = isInFavorites,
                    onClick = {
                        onToggleFavorites()
                        onDismiss()
                    },
                    modifier = if (item is ContentOptionsItem.MovieItem && !isInWatchlist)
                        Modifier.focusRequester(focusRequester)
                    else Modifier
                )

                // Remove from Continue Watching option
                if (onRemoveFromContinueWatching != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OptionButton(
                        text = "✕ Remove from Continue Watching",
                        isActive = false,
                        isDestructive = true,
                        onClick = {
                            onRemoveFromContinueWatching()
                            onDismiss()
                        }
                    )
                }

                // Cancel button
                Spacer(modifier = Modifier.height(16.dp))
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text(
                        text = "Cancel",
                        color = Color(0xFFAAAAAA)
                    )
                }
            }
        }
    }
}

@Composable
private fun OptionButton(
    text: String,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isDestructive: Boolean = false
) {
    var isFocused by remember { mutableStateOf(false) }

    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .onFocusChanged { isFocused = it.isFocused },
        shape = RoundedCornerShape(8.dp),
        color = when {
            isFocused -> Color(0xFFFF5722)
            isActive -> Color(0xFF3A3A3A)
            else -> Color(0xFF333333)
        }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                text = text,
                color = when {
                    isFocused -> Color.White
                    isDestructive -> Color(0xFFFF6B6B)
                    isActive -> Color(0xFF4CAF50)
                    else -> Color(0xFFCCCCCC)
                },
                fontSize = 14.sp,
                fontWeight = if (isFocused || isActive) FontWeight.Medium else FontWeight.Normal
            )
        }
    }
}
