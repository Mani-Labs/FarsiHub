package com.example.farsilandtv.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Phase 2: Better Loading States
 * Shimmer effect components for skeleton loading screens
 */

// Shimmer color palette for dark TV theme
private val ShimmerBaseColor = Color(0xFF1A1A1A)
private val ShimmerHighlightColor = Color(0xFF2D2D2D)

/**
 * Creates animated shimmer brush for loading effects
 */
@Composable
fun shimmerBrush(
    showShimmer: Boolean = true,
    targetValue: Float = 1000f
): Brush {
    return if (showShimmer) {
        val shimmerColors = listOf(
            ShimmerBaseColor,
            ShimmerHighlightColor,
            ShimmerBaseColor
        )

        val transition = rememberInfiniteTransition(label = "shimmer")
        val translateAnimation by transition.animateFloat(
            initialValue = 0f,
            targetValue = targetValue,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = 1200,
                    easing = LinearEasing
                ),
                repeatMode = RepeatMode.Restart
            ),
            label = "shimmerTranslate"
        )

        // UC-L1 FIX: Memoize brush to avoid recreating on every frame
        androidx.compose.runtime.remember(translateAnimation) {
            Brush.linearGradient(
                colors = shimmerColors,
                start = Offset.Zero,
                end = Offset(x = translateAnimation, y = translateAnimation)
            )
        }
    } else {
        Brush.linearGradient(
            colors = listOf(Color.Transparent, Color.Transparent),
            start = Offset.Zero,
            end = Offset.Zero
        )
    }
}

/**
 * Shimmer placeholder for movie/series cards
 */
@Composable
fun ShimmerCard(
    modifier: Modifier = Modifier,
    width: Dp = 160.dp,
    height: Dp = 240.dp
) {
    Column(
        modifier = modifier.width(width)
    ) {
        // Poster placeholder
        Box(
            modifier = Modifier
                .width(width)
                .height(height)
                .clip(RoundedCornerShape(8.dp))
                .background(shimmerBrush())
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Title placeholder
        Box(
            modifier = Modifier
                .width(width * 0.8f)
                .height(16.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(shimmerBrush())
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Subtitle placeholder
        Box(
            modifier = Modifier
                .width(width * 0.5f)
                .height(12.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(shimmerBrush())
        )
    }
}

/**
 * Shimmer placeholder for episode cards (horizontal layout)
 */
@Composable
fun ShimmerEpisodeCard(
    modifier: Modifier = Modifier,
    width: Dp = 280.dp,
    height: Dp = 160.dp
) {
    Column(
        modifier = modifier.width(width)
    ) {
        // Thumbnail placeholder
        Box(
            modifier = Modifier
                .width(width)
                .height(height)
                .clip(RoundedCornerShape(8.dp))
                .background(shimmerBrush())
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Episode info placeholder
        Box(
            modifier = Modifier
                .width(width * 0.6f)
                .height(14.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(shimmerBrush())
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Series title placeholder
        Box(
            modifier = Modifier
                .width(width * 0.8f)
                .height(12.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(shimmerBrush())
        )
    }
}

/**
 * Shimmer row for content lists (movies, series, episodes)
 */
@Composable
fun ShimmerContentRow(
    modifier: Modifier = Modifier,
    itemCount: Int = 6,
    cardWidth: Dp = 160.dp,
    cardHeight: Dp = 240.dp,
    isEpisode: Boolean = false
) {
    Column(modifier = modifier.fillMaxWidth()) {
        // Row title placeholder
        Box(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .width(200.dp)
                .height(24.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(shimmerBrush())
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Cards row
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(itemCount) {
                if (isEpisode) {
                    ShimmerEpisodeCard(
                        width = cardWidth,
                        height = cardHeight
                    )
                } else {
                    ShimmerCard(
                        width = cardWidth,
                        height = cardHeight
                    )
                }
            }
        }
    }
}

/**
 * Shimmer placeholder for featured carousel
 */
@Composable
fun ShimmerFeaturedCarousel(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(400.dp)
            .padding(horizontal = 16.dp)
    ) {
        // Main carousel image
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(16.dp))
                .background(shimmerBrush())
        )

        // Overlay content at bottom
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(24.dp)
        ) {
            // Title
            Box(
                modifier = Modifier
                    .width(300.dp)
                    .height(32.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(shimmerBrush())
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Description
            Box(
                modifier = Modifier
                    .width(400.dp)
                    .height(16.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(shimmerBrush())
            )

            Spacer(modifier = Modifier.height(4.dp))

            Box(
                modifier = Modifier
                    .width(350.dp)
                    .height(16.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(shimmerBrush())
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Play button placeholder
            Box(
                modifier = Modifier
                    .width(120.dp)
                    .height(48.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(shimmerBrush())
            )
        }

        // Carousel indicators
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            repeat(4) {
                Box(
                    modifier = Modifier
                        .width(if (it == 0) 24.dp else 8.dp)
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(shimmerBrush())
                )
            }
        }
    }
}

/**
 * Full home screen shimmer loading state
 */
@Composable
fun ShimmerHomeScreen(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(top = 16.dp)
    ) {
        // Featured carousel placeholder
        ShimmerFeaturedCarousel()

        Spacer(modifier = Modifier.height(24.dp))

        // Continue Watching row
        ShimmerContentRow(
            cardWidth = 280.dp,
            cardHeight = 160.dp,
            isEpisode = true
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Movies row
        ShimmerContentRow()

        Spacer(modifier = Modifier.height(24.dp))

        // Shows row
        ShimmerContentRow()
    }
}

/**
 * Grid shimmer for Movies/Shows screens
 */
@Composable
fun ShimmerGrid(
    modifier: Modifier = Modifier,
    columns: Int = 5,
    rows: Int = 3,
    cardWidth: Dp = 160.dp,
    cardHeight: Dp = 240.dp
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        repeat(rows) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                repeat(columns) {
                    ShimmerCard(
                        width = cardWidth,
                        height = cardHeight
                    )
                }
            }
        }
    }
}

/**
 * Details screen shimmer (movie/series details)
 */
@Composable
fun ShimmerDetailsScreen(
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        // Poster
        Box(
            modifier = Modifier
                .width(300.dp)
                .height(450.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(shimmerBrush())
        )

        Spacer(modifier = Modifier.width(32.dp))

        // Info column
        Column(
            modifier = Modifier.weight(1f)
        ) {
            // Title
            Box(
                modifier = Modifier
                    .width(400.dp)
                    .height(40.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(shimmerBrush())
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Metadata row
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                repeat(3) {
                    Box(
                        modifier = Modifier
                            .width(80.dp)
                            .height(20.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(shimmerBrush())
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Description
            repeat(4) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .height(16.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(shimmerBrush())
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Action buttons
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(
                    modifier = Modifier
                        .width(150.dp)
                        .height(48.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(shimmerBrush())
                )
                Box(
                    modifier = Modifier
                        .width(150.dp)
                        .height(48.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(shimmerBrush())
                )
            }
        }
    }
}
