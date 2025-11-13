package com.example.farsilandtv.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Week 4 - Feature #20: Skeleton Loading Screens
 *
 * Provides animated skeleton screens for:
 * - Grid layouts (MoviesScreen, ShowsScreen)
 * - Row layouts (HomeScreen)
 * - Detail screens
 */

/**
 * Shimmer animation state
 */
@Composable
fun rememberShimmerAnimation(): State<Float> {
    val transition = rememberInfiniteTransition(label = "shimmer")
    return transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_offset"
    )
}

/**
 * Shimmer brush for skeleton cards
 */
@Composable
fun shimmerBrush(offset: Float): Brush {
    return Brush.linearGradient(
        colors = listOf(
            Color(0xFF2C2C2C),
            Color(0xFF3A3A3A),
            Color(0xFF2C2C2C)
        ),
        start = Offset(offset - 300f, offset - 300f),
        end = Offset(offset, offset)
    )
}

/**
 * Skeleton card for movie/series posters
 */
@Composable
fun SkeletonCard(
    modifier: Modifier = Modifier
) {
    val shimmerOffset by rememberShimmerAnimation()

    Card(
        modifier = modifier
            .width(150.dp)
            .height(225.dp),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(shimmerBrush(shimmerOffset))
        )
    }
}

/**
 * Skeleton grid for Movies/Shows screens
 */
@Composable
fun SkeletonGrid(
    columns: Int = 6,
    rows: Int = 3,
    modifier: Modifier = Modifier
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(48.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        userScrollEnabled = false // Disable scroll for skeleton
    ) {
        items(columns * rows) {
            SkeletonCard()
        }
    }
}

/**
 * Skeleton row for HomeScreen content rows
 */
@Composable
fun SkeletonRow(
    title: String = "Loading...",
    itemCount: Int = 10,
    modifier: Modifier = Modifier
) {
    val shimmerOffset by rememberShimmerAnimation()

    Column(modifier = modifier) {
        // Title skeleton
        Box(
            modifier = Modifier
                .width(200.dp)
                .height(32.dp)
                .padding(horizontal = 48.dp, vertical = 16.dp)
                .background(
                    shimmerBrush(shimmerOffset),
                    shape = RoundedCornerShape(4.dp)
                )
        )

        // Card row skeleton
        LazyRow(
            contentPadding = PaddingValues(horizontal = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            userScrollEnabled = false
        ) {
            items(itemCount) {
                SkeletonCard()
            }
        }
    }
}

/**
 * Skeleton detail screen
 */
@Composable
fun SkeletonDetailScreen(
    modifier: Modifier = Modifier
) {
    val shimmerOffset by rememberShimmerAnimation()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        userScrollEnabled = false
    ) {
        // Backdrop skeleton
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(400.dp)
                    .background(shimmerBrush(shimmerOffset))
            )
        }

        // Title skeleton
        item {
            Box(
                modifier = Modifier
                    .padding(48.dp)
                    .width(300.dp)
                    .height(48.dp)
                    .background(
                        shimmerBrush(shimmerOffset),
                        shape = RoundedCornerShape(4.dp)
                    )
            )
        }

        // Metadata skeleton
        item {
            Row(
                modifier = Modifier.padding(horizontal = 48.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                repeat(4) {
                    Box(
                        modifier = Modifier
                            .width(80.dp)
                            .height(32.dp)
                            .background(
                                shimmerBrush(shimmerOffset),
                                shape = RoundedCornerShape(16.dp)
                            )
                    )
                }
            }
        }

        // Action buttons skeleton
        item {
            Row(
                modifier = Modifier.padding(48.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                repeat(4) {
                    Box(
                        modifier = Modifier
                            .width(120.dp)
                            .height(48.dp)
                            .background(
                                shimmerBrush(shimmerOffset),
                                shape = RoundedCornerShape(8.dp)
                            )
                    )
                }
            }
        }

        // Description skeleton
        item {
            Column(
                modifier = Modifier.padding(horizontal = 48.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                repeat(4) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(20.dp)
                            .background(
                                shimmerBrush(shimmerOffset),
                                shape = RoundedCornerShape(4.dp)
                            )
                    )
                }
            }
        }
    }
}

/**
 * Empty state view when no content found
 */
@Composable
fun EmptyStateView(
    message: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = androidx.compose.ui.Alignment.Center
    ) {
        androidx.compose.material3.Text(
            text = message,
            style = androidx.compose.material3.MaterialTheme.typography.bodyLarge,
            color = Color.White.copy(alpha = 0.6f)
        )
    }
}
