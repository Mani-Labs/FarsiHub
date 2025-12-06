package com.example.farsilandtv.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.*
import coil.compose.AsyncImage
import com.example.farsilandtv.data.download.DownloadItem
import com.example.farsilandtv.data.download.DownloadManager
import com.example.farsilandtv.data.download.DownloadStatus
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * Downloads Screen - Shows all downloaded content
 * Allows playing and deleting downloads
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun DownloadsScreen(
    downloadManager: DownloadManager,
    onPlayDownload: (DownloadItem) -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
)  {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current

    // Observe downloads
    val downloads by downloadManager.getAllDownloads().collectAsState(initial = emptyList())

    // Observe real-time download progress
    val progressMap by downloadManager.downloadProgress.collectAsState()

    // Dialog state
    var itemToDelete by remember { mutableStateOf<DownloadItem?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showClearAllDialog by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0F)) // BackgroundDark
            .onPreviewKeyEvent { event ->
                if (event.key == Key.Back && event.type == KeyEventType.KeyUp) {
                    onBackClick()
                    true
                } else {
                    false
                }
            }
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
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = Color(0xFF2196F3),
                    modifier = Modifier.size(36.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Downloads",
                        color = Color.White,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold
                    )
                    val completedCount = downloads.count { it.status == DownloadStatus.COMPLETED }
                    val totalSize = downloads.filter { it.status == DownloadStatus.COMPLETED }
                        .sumOf { it.fileSize }
                    Text(
                        text = "$completedCount downloads • ${formatFileSize(totalSize)}",
                        color = Color.Gray,
                        fontSize = 16.sp
                    )
                }

                // Clear all button (only if there are completed downloads)
                if (downloads.any { it.status == DownloadStatus.COMPLETED }) {
                    Button(
                        onClick = { showClearAllDialog = true },
                        colors = ButtonDefaults.colors(
                            containerColor = Color(0xFFD32F2F),
                            focusedContainerColor = Color(0xFFE53935)
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Clear All")
                    }
                }
            }

            // Content
            if (downloads.isEmpty()) {
                // Empty state
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = Color.Gray,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No downloads yet",
                            color = Color.Gray,
                            fontSize = 18.sp
                        )
                        Text(
                            text = "Downloaded movies and episodes will appear here",
                            color = Color.DarkGray,
                            fontSize = 14.sp
                        )
                    }
                }
            } else {
                // List of downloads
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(
                        items = downloads,
                        key = { it.id }
                    ) { download ->
                        // Get real-time progress if available
                        val realTimeProgress = progressMap[download.id] ?: download.progress

                        DownloadItemRow(
                            download = download,
                            realTimeProgress = realTimeProgress,
                            onClick = {
                                if (download.status == DownloadStatus.COMPLETED) {
                                    onPlayDownload(download)
                                }
                            },
                            onPauseResume = {
                                coroutineScope.launch {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    when (download.status) {
                                        DownloadStatus.DOWNLOADING, DownloadStatus.PENDING -> {
                                            downloadManager.pauseDownload(download.id)
                                            Toast.makeText(context, "Download paused", Toast.LENGTH_SHORT).show()
                                        }
                                        DownloadStatus.PAUSED -> {
                                            downloadManager.resumeDownload(download.id)
                                            Toast.makeText(context, "Download resumed", Toast.LENGTH_SHORT).show()
                                        }
                                        else -> {}
                                    }
                                }
                            },
                            onCancelClick = {
                                coroutineScope.launch {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    downloadManager.cancelDownload(download.id)
                                    Toast.makeText(context, "Download cancelled", Toast.LENGTH_SHORT).show()
                                }
                            },
                            onDeleteClick = {
                                itemToDelete = download
                                showDeleteDialog = true
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
                    focusedContainerColor = Color(0xFF2196F3)
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

    // Delete single item dialog - capture item to prevent null during recomposition
    val deleteItem = itemToDelete
    if (showDeleteDialog && deleteItem != null) {
        Dialog(onDismissRequest = {
            showDeleteDialog = false
            itemToDelete = null
        }) {
            androidx.compose.material3.Surface(
                modifier = Modifier
                    .width(400.dp)
                    .wrapContentHeight(),
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF2A2A2A),
                tonalElevation = 8.dp
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Delete Download?",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text(
                        text = "Delete \"${deleteItem.title}\"? The file will be removed from your device.",
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
                                itemToDelete = null
                            },
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Text("Cancel")
                        }
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    downloadManager.deleteDownload(deleteItem.id)
                                    Toast.makeText(context, "Download deleted", Toast.LENGTH_SHORT).show()
                                }
                                showDeleteDialog = false
                                itemToDelete = null
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

    // Clear all dialog
    if (showClearAllDialog) {
        Dialog(onDismissRequest = { showClearAllDialog = false }) {
            androidx.compose.material3.Surface(
                modifier = Modifier
                    .width(400.dp)
                    .wrapContentHeight(),
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF2A2A2A),
                tonalElevation = 8.dp
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Clear All Downloads?",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text(
                        text = "Delete all downloaded files? This cannot be undone.",
                        color = Color.LightGray,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Button(
                            onClick = { showClearAllDialog = false },
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Text("Cancel")
                        }
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    downloadManager.clearCompletedDownloads()
                                    Toast.makeText(context, "All downloads cleared", Toast.LENGTH_SHORT).show()
                                }
                                showClearAllDialog = false
                            },
                            colors = ButtonDefaults.colors(containerColor = Color(0xFFD32F2F))
                        ) {
                            Text("Clear All")
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun DownloadItemRow(
    download: DownloadItem,
    realTimeProgress: Int,
    onClick: () -> Unit,
    onPauseResume: () -> Unit,
    onCancelClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }
    // Use real-time progress for display
    val displayProgress = realTimeProgress

    androidx.tv.material3.Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(100.dp),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color(0xFF1E293B),
            focusedContainerColor = Color(0xFF2196F3)
        ),
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(8.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbnail
            Box(
                modifier = Modifier
                    .width(120.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF2A2A2A)),
                contentAlignment = Alignment.Center
            ) {
                if (download.posterUrl != null) {
                    AsyncImage(
                        model = download.posterUrl,
                        contentDescription = download.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.Gray,
                        modifier = Modifier.size(32.dp)
                    )
                }

                // Status overlay
                when (download.status) {
                    DownloadStatus.COMPLETED -> {
                        // Play icon overlay
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.4f)),
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
                        // Progress overlay
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.6f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "${displayProgress}%",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    DownloadStatus.PAUSED -> {
                        // Paused overlay
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.6f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Paused",
                                    tint = Color(0xFFFFA726),
                                    modifier = Modifier.size(24.dp)
                                )
                                Text(
                                    text = "${displayProgress}%",
                                    color = Color(0xFFFFA726),
                                    fontSize = 12.sp
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
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Failed",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                    else -> {}
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = download.title,
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (download.subtitle != null) {
                    Text(
                        text = download.subtitle,
                        color = Color.Gray,
                        fontSize = 14.sp
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Status row
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Status indicator
                    val (statusText, statusColor) = when (download.status) {
                        DownloadStatus.PENDING -> "Starting..." to Color(0xFFFFA726)
                        DownloadStatus.DOWNLOADING -> "Downloading ${displayProgress}%" to Color(0xFF2196F3)
                        DownloadStatus.PAUSED -> "Paused at ${displayProgress}%" to Color(0xFFFFA726)
                        DownloadStatus.COMPLETED -> formatFileSize(download.fileSize) to Color(0xFF4CAF50)
                        DownloadStatus.FAILED -> "Failed - tap to retry" to Color(0xFFD32F2F)
                        DownloadStatus.CANCELLED -> "Cancelled" to Color.Gray
                    }

                    Text(
                        text = statusText,
                        color = statusColor,
                        fontSize = 12.sp
                    )

                    if (download.status == DownloadStatus.COMPLETED && download.completedAt != null) {
                        Text(
                            text = " • ${dateFormat.format(Date(download.completedAt))}",
                            color = Color.DarkGray,
                            fontSize = 12.sp
                        )
                    }
                }

                // Progress bar for downloading/paused
                if (download.status == DownloadStatus.DOWNLOADING ||
                    download.status == DownloadStatus.PENDING ||
                    download.status == DownloadStatus.PAUSED) {
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { displayProgress / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .semantics {
                                contentDescription = "Download progress $displayProgress percent"
                            },
                        color = if (download.status == DownloadStatus.PAUSED)
                            Color(0xFFFFA726) else Color(0xFF2196F3),
                        trackColor = Color(0xFF3A3A3A)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Action buttons based on status
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Pause/Resume button for active downloads
                if (download.status == DownloadStatus.DOWNLOADING ||
                    download.status == DownloadStatus.PENDING ||
                    download.status == DownloadStatus.PAUSED) {

                    androidx.tv.material3.Surface(
                        onClick = onPauseResume,
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = Color(0xFF2A2A2A),
                            focusedContainerColor = Color(0xFF2196F3)
                        ),
                        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(4.dp))
                    ) {
                        Icon(
                            imageVector = if (download.status == DownloadStatus.PAUSED)
                                Icons.Default.PlayArrow else Icons.Default.Close,
                            contentDescription = if (download.status == DownloadStatus.PAUSED)
                                "Resume" else "Pause",
                            tint = if (download.status == DownloadStatus.PAUSED)
                                Color(0xFF4CAF50) else Color(0xFFFFA726),
                            modifier = Modifier
                                .padding(10.dp)
                                .size(24.dp)
                        )
                    }

                    // Cancel button for active/paused downloads
                    androidx.tv.material3.Surface(
                        onClick = onCancelClick,
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = Color(0xFF2A2A2A),
                            focusedContainerColor = Color(0xFFD32F2F)
                        ),
                        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(4.dp))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Cancel",
                            tint = Color(0xFFD32F2F),
                            modifier = Modifier
                                .padding(10.dp)
                                .size(24.dp)
                        )
                    }
                }

                // Delete button (for completed/failed downloads)
                if (download.status == DownloadStatus.COMPLETED ||
                    download.status == DownloadStatus.FAILED ||
                    download.status == DownloadStatus.CANCELLED) {

                    androidx.tv.material3.Surface(
                        onClick = onDeleteClick,
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = Color.Transparent,
                            focusedContainerColor = Color(0xFFD32F2F)
                        ),
                        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(4.dp))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = Color.Gray,
                            modifier = Modifier
                                .padding(12.dp)
                                .size(24.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes >= 1_000_000_000 -> String.format("%.1f GB", bytes / 1_000_000_000.0)
        bytes >= 1_000_000 -> String.format("%.1f MB", bytes / 1_000_000.0)
        bytes >= 1_000 -> String.format("%.1f KB", bytes / 1_000.0)
        else -> "$bytes B"
    }
}
