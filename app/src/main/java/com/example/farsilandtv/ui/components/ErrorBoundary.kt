package com.example.farsilandtv.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * L5 FIX: Error Boundary for Compose screens
 * Note: Compose doesn't support try-catch around @Composable functions directly.
 * This is a simplified error boundary that allows parent composables to pass
 * error state and render error screens when needed.
 *
 * Usage:
 * ```kotlin
 * var error by remember { mutableStateOf<Throwable?>(null) }
 *
 * ErrorBoundary(
 *     error = error,
 *     onError = { throwable ->
 *         ErrorScreen(
 *             message = "Failed to load movies",
 *             error = throwable,
 *             onRetry = { error = null }
 *         )
 *     }
 * ) {
 *     // Your composable content
 * }
 * ```
 */
@Composable
fun ErrorBoundary(
    error: Throwable? = null,
    onError: @Composable (Throwable) -> Unit,
    content: @Composable () -> Unit
) {
    error?.let {
        onError(it)
    } ?: content()
}

/**
 * L5 FIX: Standard error screen for Compose UIs
 * Shows user-friendly error message with retry option
 */
@Composable
fun ErrorScreen(
    message: String,
    error: Throwable,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = error.message ?: "Unknown error",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onRetry) {
            Text("Retry")
        }
    }
}
