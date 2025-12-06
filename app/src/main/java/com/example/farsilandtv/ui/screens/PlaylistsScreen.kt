package com.example.farsilandtv.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.*
import coil.compose.AsyncImage
import com.example.farsilandtv.data.database.Playlist
import com.example.farsilandtv.data.database.PlaylistItem
import com.example.farsilandtv.data.repository.PlaylistRepository
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * Playlists Screen - Compose TV
 * Displays all user playlists and allows managing them
 * Replaces PlaylistsActivity and PlaylistDetailActivity
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun PlaylistsScreen(
    playlistRepo: PlaylistRepository,
    onMovieClick: (Int) -> Unit,
    onSeriesClick: (Int) -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }

    // Observe playlists
    val playlists by playlistRepo.getAllPlaylists().collectAsState(initial = emptyList())
    var isLoading by remember { mutableStateOf(true) }

    // Stop loading when playlists load
    LaunchedEffect(playlists) {
        isLoading = false
    }

    // Navigation state
    var selectedPlaylistId by remember { mutableStateOf<Long?>(null) }

    // Dialog states
    var showCreateDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var editingPlaylist by remember { mutableStateOf<Playlist?>(null) }

    // Request focus on first item
    LaunchedEffect(playlists) {
        if (playlists.isNotEmpty() && selectedPlaylistId == null) {
            try {
                focusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0F)) // BackgroundDark
            .onPreviewKeyEvent { event ->
                if (event.key == Key.Back && event.type == KeyEventType.KeyUp) {
                    if (selectedPlaylistId != null) {
                        // Go back to playlists list first
                        selectedPlaylistId = null
                    } else {
                        // Go back to home
                        onBackClick()
                    }
                    true
                } else {
                    false
                }
            }
    ) {
        if (selectedPlaylistId != null) {
            // Show playlist detail view
            PlaylistDetailView(
                playlistId = selectedPlaylistId!!,
                playlistRepo = playlistRepo,
                onMovieClick = onMovieClick,
                onSeriesClick = onSeriesClick,
                onBackClick = { selectedPlaylistId = null },
                onEditClick = { playlist ->
                    editingPlaylist = playlist
                    showEditDialog = true
                },
                onDeleteClick = { playlist ->
                    editingPlaylist = playlist
                    showDeleteDialog = true
                }
            )
        } else {
            // Show playlists list
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
                        imageVector = Icons.Default.List,
                        contentDescription = null,
                        tint = Color(0xFF4CAF50),
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "My Playlists",
                            color = Color.White,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${playlists.size} playlists",
                            color = Color.Gray,
                            fontSize = 16.sp
                        )
                    }
                    // Create new playlist button
                    Button(
                        onClick = { showCreateDialog = true },
                        colors = ButtonDefaults.colors(
                            containerColor = Color(0xFF2E7D32),
                            focusedContainerColor = Color(0xFF388E3C)
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("New Playlist")
                    }
                }

                // Content
                if (isLoading && playlists.isEmpty()) {
                    // Loading skeleton
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 200.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        items(6) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(120.dp),
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFF2A2A2A)
                            ) {
                                // Shimmer effect placeholder
                            }
                        }
                    }
                } else if (playlists.isEmpty()) {
                    // Empty state
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                tint = Color.Gray,
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "No playlists yet",
                                color = Color.Gray,
                                fontSize = 18.sp
                            )
                            Text(
                                text = "Create a playlist to organize your content",
                                color = Color.DarkGray,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            Button(
                                onClick = { showCreateDialog = true },
                                colors = ButtonDefaults.colors(
                                    containerColor = Color(0xFF2E7D32),
                                    focusedContainerColor = Color(0xFF388E3C)
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Create First Playlist")
                            }
                        }
                    }
                } else {
                    // Grid of playlists
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 200.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        items(
                            items = playlists,
                            key = { it.id }
                        ) { playlist ->
                            PlaylistCard(
                                playlist = playlist,
                                onClick = { selectedPlaylistId = playlist.id },
                                onLongClick = {
                                    editingPlaylist = playlist
                                    showDeleteDialog = true
                                },
                                modifier = if (playlists.indexOf(playlist) == 0) {
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
    }

    // Create playlist dialog
    if (showCreateDialog) {
        PlaylistFormDialog(
            title = "Create Playlist",
            initialName = "",
            initialDescription = "",
            onDismiss = { showCreateDialog = false },
            onConfirm = { name, description ->
                coroutineScope.launch {
                    playlistRepo.createPlaylist(name, description)
                    Toast.makeText(context, "Playlist created", Toast.LENGTH_SHORT).show()
                }
                showCreateDialog = false
            }
        )
    }

    // Edit playlist dialog
    if (showEditDialog && editingPlaylist != null) {
        PlaylistFormDialog(
            title = "Edit Playlist",
            initialName = editingPlaylist!!.name,
            initialDescription = editingPlaylist?.description ?: "",
            onDismiss = {
                showEditDialog = false
                editingPlaylist = null
            },
            onConfirm = { name, description ->
                // Capture playlist ID before async lambda
                val playlistId = editingPlaylist?.id
                if (playlistId != null) {
                    coroutineScope.launch {
                        playlistRepo.updatePlaylistInfo(playlistId, name, description)
                        Toast.makeText(context, "Playlist updated", Toast.LENGTH_SHORT).show()
                    }
                }
                showEditDialog = false
                editingPlaylist = null
            }
        )
    }

    // Delete playlist dialog
    if (showDeleteDialog && editingPlaylist != null) {
        val playlist = editingPlaylist!!
        Dialog(onDismissRequest = {
            showDeleteDialog = false
            editingPlaylist = null
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
                        text = "Delete Playlist?",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text(
                        text = "Delete \"${playlist.name}\" and all its items? This cannot be undone.",
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
                                showDeleteDialog = false
                                editingPlaylist = null
                            },
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Text("Cancel")
                        }
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    playlistRepo.deletePlaylist(playlist.id)
                                    Toast.makeText(context, "Playlist deleted", Toast.LENGTH_SHORT).show()
                                    if (selectedPlaylistId == playlist.id) {
                                        selectedPlaylistId = null
                                    }
                                }
                                showDeleteDialog = false
                                editingPlaylist = null
                            },
                            colors = ButtonDefaults.colors(containerColor = Color(0xFFD32F2F))
                        ) {
                            Text("Delete")
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PlaylistCard(
    playlist: Playlist,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var longPressTriggered by remember { mutableStateOf(false) }
    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }

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
            .fillMaxWidth()
            .height(120.dp),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color(0xFF1E293B),
            focusedContainerColor = Color(0xFF2E7D32)
        ),
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(12.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Playlist icon or cover
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .background(
                        Color(0xFF2A2A2A),
                        shape = RoundedCornerShape(8.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (playlist.coverImageUrl != null) {
                    AsyncImage(
                        model = playlist.coverImageUrl,
                        contentDescription = playlist.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.List,
                        contentDescription = null,
                        tint = Color(0xFF4CAF50),
                        modifier = Modifier.size(48.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Playlist info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = playlist.name,
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!playlist.description.isNullOrBlank()) {
                    Text(
                        text = playlist.description,
                        color = Color.Gray,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Updated ${dateFormat.format(Date(playlist.updatedAt))}",
                    color = Color.DarkGray,
                    fontSize = 12.sp
                )
            }

            // Arrow indicator
            Icon(
                imageVector = Icons.Default.KeyboardArrowRight,
                contentDescription = null,
                tint = Color.Gray,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PlaylistDetailView(
    playlistId: Long,
    playlistRepo: PlaylistRepository,
    onMovieClick: (Int) -> Unit,
    onSeriesClick: (Int) -> Unit,
    onBackClick: () -> Unit,
    onEditClick: (Playlist) -> Unit,
    onDeleteClick: (Playlist) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Observe playlist and items
    val playlist by playlistRepo.getPlaylist(playlistId).collectAsState(initial = null)
    val items by playlistRepo.getPlaylistItems(playlistId).collectAsState(initial = emptyList())

    // Remove item dialog
    var itemToRemove by remember { mutableStateOf<PlaylistItem?>(null) }
    var showRemoveDialog by remember { mutableStateOf(false) }

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
            // Back button
            androidx.tv.material3.Surface(
                onClick = onBackClick,
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = Color(0xFF2A2A2A),
                    focusedContainerColor = Color(0xFF3A3A3A)
                ),
                shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(8.dp))
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White,
                    modifier = Modifier.padding(12.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = playlist?.name ?: "Playlist",
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${items.size} items",
                    color = Color.Gray,
                    fontSize = 14.sp
                )
            }

            // Edit button
            if (playlist != null) {
                androidx.tv.material3.Surface(
                    onClick = { onEditClick(playlist!!) },
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = Color(0xFF2A2A2A),
                        focusedContainerColor = Color(0xFF2E7D32)
                    ),
                    shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(8.dp))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Edit", color = Color.White)
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Delete button
                androidx.tv.material3.Surface(
                    onClick = { onDeleteClick(playlist!!) },
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = Color(0xFF2A2A2A),
                        focusedContainerColor = Color(0xFFD32F2F)
                    ),
                    shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(8.dp))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Delete", color = Color.White)
                    }
                }
            }
        }

        // Description
        if (!playlist?.description.isNullOrBlank()) {
            Text(
                text = playlist?.description ?: "",
                color = Color.LightGray,
                fontSize = 14.sp,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }

        // Content
        if (items.isEmpty()) {
            // Empty state
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        tint = Color.Gray,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "This playlist is empty",
                        color = Color.Gray,
                        fontSize = 18.sp
                    )
                    Text(
                        text = "Long-press on movies or shows to add them here",
                        color = Color.DarkGray,
                        fontSize = 14.sp
                    )
                }
            }
        } else {
            // List of items
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(
                    items = items,
                    key = { it.id }
                ) { item ->
                    PlaylistItemRow(
                        item = item,
                        onClick = {
                            when (item.contentType) {
                                PlaylistItem.ContentType.MOVIE -> {
                                    val id = item.contentId.removePrefix("movie-").toIntOrNull()
                                    if (id != null) onMovieClick(id)
                                }
                                PlaylistItem.ContentType.SERIES -> {
                                    val id = item.contentId.removePrefix("series-").toIntOrNull()
                                    if (id != null) onSeriesClick(id)
                                }
                            }
                        },
                        onRemoveClick = {
                            itemToRemove = item
                            showRemoveDialog = true
                        }
                    )
                }
            }
        }
    }

    // Remove item dialog
    if (showRemoveDialog && itemToRemove != null) {
        Dialog(onDismissRequest = {
            showRemoveDialog = false
            itemToRemove = null
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
                        text = "Remove from Playlist?",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text(
                        text = "Remove this item from the playlist?",
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
                                itemToRemove = null
                            },
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Text("Cancel")
                        }
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    playlistRepo.removePlaylistItem(itemToRemove!!.id)
                                    Toast.makeText(context, "Item removed", Toast.LENGTH_SHORT).show()
                                }
                                showRemoveDialog = false
                                itemToRemove = null
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
private fun PlaylistItemRow(
    item: PlaylistItem,
    onClick: () -> Unit,
    onRemoveClick: () -> Unit
) {
    androidx.tv.material3.Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color(0xFF1E293B),
            focusedContainerColor = Color(0xFF2E7D32)
        ),
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(8.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Content type icon
            Icon(
                imageVector = when (item.contentType) {
                    PlaylistItem.ContentType.MOVIE -> Icons.Default.PlayArrow
                    PlaylistItem.ContentType.SERIES -> Icons.Default.List
                },
                contentDescription = null,
                tint = when (item.contentType) {
                    PlaylistItem.ContentType.MOVIE -> Color(0xFF1976D2)
                    PlaylistItem.ContentType.SERIES -> Color(0xFF7B1FA2)
                },
                modifier = Modifier.size(32.dp)
            )

            Spacer(modifier = Modifier.width(16.dp))

            // Item info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.contentId,
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = when (item.contentType) {
                        PlaylistItem.ContentType.MOVIE -> "Movie"
                        PlaylistItem.ContentType.SERIES -> "TV Show"
                    },
                    color = Color.Gray,
                    fontSize = 14.sp
                )
            }

            // Remove button
            androidx.tv.material3.Surface(
                onClick = onRemoveClick,
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = Color.Transparent,
                    focusedContainerColor = Color(0xFFD32F2F)
                ),
                shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(4.dp))
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Remove",
                    tint = Color.Gray,
                    modifier = Modifier
                        .padding(8.dp)
                        .size(20.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PlaylistFormDialog(
    title: String,
    initialName: String,
    initialDescription: String,
    onDismiss: () -> Unit,
    onConfirm: (name: String, description: String?) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var description by remember { mutableStateOf(initialDescription) }

    Dialog(onDismissRequest = onDismiss) {
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
                    text = title,
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Name input
                Text("Name", color = Color.Gray, fontSize = 12.sp)
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    color = Color(0xFF3A3A3A),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    BasicTextField(
                        value = name,
                        onValueChange = { name = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        textStyle = TextStyle(
                            color = Color.White,
                            fontSize = 16.sp
                        ),
                        singleLine = true
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Description input
                Text("Description (optional)", color = Color.Gray, fontSize = 12.sp)
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    color = Color(0xFF3A3A3A),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    BasicTextField(
                        value = description,
                        onValueChange = { description = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        textStyle = TextStyle(
                            color = Color.White,
                            fontSize = 16.sp
                        ),
                        maxLines = 3
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = {
                            if (name.isNotBlank()) {
                                onConfirm(name.trim(), description.trim().ifBlank { null })
                            }
                        },
                        enabled = name.isNotBlank(),
                        colors = ButtonDefaults.colors(
                            containerColor = Color(0xFF2E7D32),
                            focusedContainerColor = Color(0xFF388E3C)
                        )
                    ) {
                        Text("Save")
                    }
                }
            }
        }
    }
}
