package com.example.farsilandtv.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.*
import coil.compose.AsyncImage
import com.example.farsilandtv.data.database.Favorite
import com.example.farsilandtv.data.repository.FavoritesRepository
import kotlinx.coroutines.launch

/**
 * Favorites Screen - Compose TV
 * Displays all favorited movies and series in a grid layout
 * Replaces FavoritesActivity and FavoritesFragment
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun FavoritesScreen(
    favoritesRepo: FavoritesRepository,
    onMovieClick: (Int) -> Unit,
    onSeriesClick: (Int) -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }

    // Observe favorites
    val favorites by favoritesRepo.getAllFavorites().collectAsState(initial = emptyList())

    // Filter states
    var filterType by remember { mutableStateOf<Favorite.ContentType?>(null) }

    // Long-press item state
    var selectedFavorite by remember { mutableStateOf<Favorite?>(null) }
    var showRemoveDialog by remember { mutableStateOf(false) }

    // Request focus on first item
    LaunchedEffect(favorites) {
        if (favorites.isNotEmpty()) {
            try {
                focusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

    // Filter favorites based on selected type
    val filteredFavorites = remember(favorites, filterType) {
        when (filterType) {
            Favorite.ContentType.MOVIE -> favorites.filter { it.contentType == Favorite.ContentType.MOVIE }
            Favorite.ContentType.SERIES -> favorites.filter { it.contentType == Favorite.ContentType.SERIES }
            null -> favorites
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0F)) // BackgroundDark
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(48.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = null,
                    tint = Color(0xFFF59E0B), // FarsilandAmber
                    modifier = Modifier.size(36.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "My Favorites",
                        color = Color.White,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${favorites.size} items",
                        color = Color.Gray,
                        fontSize = 16.sp
                    )
                }
            }

            // Filter tabs
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(bottom = 24.dp)
            ) {
                FilterChip(
                    label = "All",
                    selected = filterType == null,
                    onClick = { filterType = null }
                )
                FilterChip(
                    label = "Movies",
                    selected = filterType == Favorite.ContentType.MOVIE,
                    onClick = { filterType = Favorite.ContentType.MOVIE }
                )
                FilterChip(
                    label = "Shows",
                    selected = filterType == Favorite.ContentType.SERIES,
                    onClick = { filterType = Favorite.ContentType.SERIES }
                )
            }

            // Content
            if (filteredFavorites.isEmpty()) {
                // Empty state
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.FavoriteBorder,
                            contentDescription = null,
                            tint = Color.Gray,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = when (filterType) {
                                Favorite.ContentType.MOVIE -> "No favorite movies yet"
                                Favorite.ContentType.SERIES -> "No favorite shows yet"
                                null -> "No favorites yet"
                            },
                            color = Color.Gray,
                            fontSize = 18.sp
                        )
                        Text(
                            text = "Long-press on any movie or show to add to favorites",
                            color = Color.DarkGray,
                            fontSize = 14.sp
                        )
                    }
                }
            } else {
                // Grid of favorites
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 150.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(
                        items = filteredFavorites,
                        key = { it.contentId }
                    ) { favorite ->
                        FavoriteCard(
                            favorite = favorite,
                            onClick = {
                                when (favorite.contentType) {
                                    Favorite.ContentType.MOVIE -> onMovieClick(favorite.numericId)
                                    Favorite.ContentType.SERIES -> onSeriesClick(favorite.numericId)
                                }
                            },
                            onLongClick = {
                                selectedFavorite = favorite
                                showRemoveDialog = true
                            },
                            modifier = if (filteredFavorites.indexOf(favorite) == 0) {
                                Modifier.focusRequester(focusRequester)
                            } else {
                                Modifier
                            }
                        )
                    }
                }
            }

            // Back button at bottom
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onBackClick,
                colors = ButtonDefaults.colors(
                    containerColor = Color(0xFF2A2A2A),
                    focusedContainerColor = Color(0xFF2E7D32)
                )
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Back")
            }
        }
    }

    // Remove confirmation dialog
    if (showRemoveDialog && selectedFavorite != null) {
        val favorite = selectedFavorite!!
        Dialog(onDismissRequest = {
            showRemoveDialog = false
            selectedFavorite = null
        }) {
            Surface(
                modifier = Modifier
                    .width(400.dp)
                    .wrapContentHeight(),
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF2A2A2A),
                tonalElevation = 8.dp
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Remove from Favorites?",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text(
                        text = "Remove \"${favorite.title}\" from your favorites?",
                        color = Color.LightGray,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Button(
                            onClick = {
                                showRemoveDialog = false
                                selectedFavorite = null
                            },
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Text("Cancel")
                        }
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    favoritesRepo.removeFavorite(favorite.contentId)
                                    Toast.makeText(context, "Removed from favorites", Toast.LENGTH_SHORT).show()
                                }
                                showRemoveDialog = false
                                selectedFavorite = null
                            },
                            colors = ButtonDefaults.colors(containerColor = Color(0xFFD32F2F))
                        ) {
                            Text("Remove")
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun FilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    androidx.tv.material3.Surface(
        onClick = onClick,
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) Color(0xFF2E7D32) else Color(0xFF2A2A2A),
            focusedContainerColor = if (selected) Color(0xFF388E3C) else Color(0xFF3A3A3A)
        ),
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(20.dp))
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = 14.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun FavoriteCard(
    favorite: Favorite,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var longPressTriggered by remember { mutableStateOf(false) }

    androidx.tv.material3.Surface(
        onClick = {
            if (!longPressTriggered) {
                onClick()
            }
            longPressTriggered = false
        },
        onLongClick = {
            longPressTriggered = true
            onLongClick()
        },
        modifier = modifier
            .width(150.dp)
            .aspectRatio(2f / 3f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color(0xFF1E293B),
            focusedContainerColor = Color(0xFF2E7D32)
        ),
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(8.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f)
    ) {
        Box {
            // Poster image
            AsyncImage(
                model = favorite.posterUrl,
                contentDescription = favorite.title,
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
                                Color.Black.copy(alpha = 0.8f)
                            ),
                            startY = 100f
                        )
                    )
            )

            // Content type badge
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
                color = when (favorite.contentType) {
                    Favorite.ContentType.MOVIE -> Color(0xFF1976D2)
                    Favorite.ContentType.SERIES -> Color(0xFF7B1FA2)
                },
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    text = when (favorite.contentType) {
                        Favorite.ContentType.MOVIE -> "Movie"
                        Favorite.ContentType.SERIES -> "Show"
                    },
                    color = Color.White,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }

            // Title at bottom
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
            ) {
                Text(
                    text = favorite.title,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Favorite heart indicator
            Icon(
                imageVector = Icons.Default.Favorite,
                contentDescription = null,
                tint = Color(0xFFE91E63),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .size(20.dp)
            )
        }
    }
}
