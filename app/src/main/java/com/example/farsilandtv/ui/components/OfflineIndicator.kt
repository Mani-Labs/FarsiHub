package com.example.farsilandtv.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.farsilandtv.utils.NetworkUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Offline mode indicator component
 * Shows a banner when the device is offline
 */
@Composable
fun OfflineIndicator(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isOnline by remember { mutableStateOf(true) }
    var showRestored by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    // Observe network state - use DisposableEffect for proper cleanup
    DisposableEffect(context) {
        val job = coroutineScope.launch {
            NetworkUtils.observeNetworkState(context).collect { connected ->
                if (connected && !isOnline) {
                    // Connection restored - show brief message
                    showRestored = true
                    isOnline = true
                    delay(2000)
                    showRestored = false
                } else if (!connected) {
                    isOnline = false
                    showRestored = false
                }
            }
        }
        onDispose {
            job.cancel()
        }
    }

    AnimatedVisibility(
        visible = !isOnline || showRestored,
        enter = slideInVertically(tween(300)) + fadeIn(),
        exit = slideOutVertically(tween(300)) + fadeOut(),
        modifier = modifier
    ) {
        val backgroundColor = if (showRestored) Color(0xFF4CAF50) else Color(0xFFF59E0B) // FarsilandAmber
        val icon = if (showRestored) Icons.Filled.Check else Icons.Filled.Warning
        val message = if (showRestored) "Back online" else "No internet connection"

        Surface(
            color = backgroundColor,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp, horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = message,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

/**
 * Wrapper that adds offline indicator to content
 */
@Composable
fun WithOfflineIndicator(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Column(modifier = modifier) {
        OfflineIndicator()
        Box(modifier = Modifier.weight(1f)) {
            content()
        }
    }
}

/**
 * Composable for showing download offline warning
 */
@Composable
fun OfflineDownloadWarning(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = null,
                tint = Color(0xFFF59E0B) // FarsilandAmber
            )
        },
        title = { Text("No Internet Connection") },
        text = { Text("Please connect to the internet to download content.") },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("OK")
            }
        },
        modifier = modifier
    )
}
