package com.example.farsilandtv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.farsilandtv.ui.theme.FavoriteRed
import com.example.farsilandtv.ui.theme.WatchedGreen

/**
 * Feature #16: Compose Component - Status Badges
 * Shows favorite/watched status on content cards
 */

@Composable
fun FavoriteBadge(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(32.dp)
            .background(Color.Black.copy(alpha = 0.6f), CircleShape)
            .padding(6.dp),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Filled.Favorite,
            contentDescription = "Favorite",
            tint = FavoriteRed,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
fun WatchedBadge(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(32.dp)
            .background(Color.Black.copy(alpha = 0.6f), CircleShape)
            .padding(6.dp),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Filled.Check,
            contentDescription = "Watched",
            tint = WatchedGreen,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
fun ContinueWatchingProgress(
    progress: Float, // 0.0 to 1.0
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.6f))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        androidx.compose.material3.LinearProgressIndicator(
            progress = progress,
            modifier = Modifier
                .size(width = 100.dp, height = 4.dp),
            color = Color(0xFF2196F3), // ContinueWatchingBlue
            trackColor = Color.White.copy(alpha = 0.3f)
        )
    }
}
