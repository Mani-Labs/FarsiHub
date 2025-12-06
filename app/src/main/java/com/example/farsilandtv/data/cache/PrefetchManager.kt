package com.example.farsilandtv.data.cache

import android.content.Context
import android.util.Log
import coil.ImageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.example.farsilandtv.data.models.Episode
import com.example.farsilandtv.data.models.Movie
import com.example.farsilandtv.data.models.Series
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Background Pre-fetch Manager
 *
 * Pre-loads images and data for smoother user experience:
 * - Pre-caches poster images for visible content rows
 * - Prefetches next page of content for infinite scroll
 * - Background loads episode lists for series the user is watching
 *
 * Hilt-managed singleton - injected via constructor
 */
@Singleton
class PrefetchManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    // FIX: Store SupervisorJob reference so we can cancel it in cleanup()
    // Previous implementation never cancelled the scope, causing memory leak
    private val supervisorJob = SupervisorJob()
    private val scope = CoroutineScope(supervisorJob + Dispatchers.IO)

    // EXTERNAL AUDIT FIX CH-L1: Use shared ImageLoader instance instead of creating separate one
    // Issue: Creating separate ImageLoader instance bypasses shared cache, wastes memory
    // Solution: Use shared singleton from ImageLoader utility
    private val imageLoader: coil.ImageLoader by lazy {
        com.example.farsilandtv.utils.ImageLoader.getSharedInstance(context)
    }

    companion object {
        private const val TAG = "PrefetchManager"
        private const val MAX_PREFETCH_ITEMS = 20
        // CH-H2 FIX: Minimum free disk space required for prefetching (100MB)
        private const val MIN_FREE_DISK_SPACE_MB = 100L
    }

    /**
     * Prefetch poster images for movies
     * CH-H2 FIX: Checks disk space before prefetching
     */
    fun prefetchMoviePosters(movies: List<Movie>) {
        if (!hasSufficientDiskSpace()) {
            Log.w(TAG, "Insufficient disk space, skipping prefetch")
            return
        }
        scope.launch {
            movies.take(MAX_PREFETCH_ITEMS).forEach { movie ->
                movie.posterUrl?.let { url ->
                    prefetchImage(url, "movie-${movie.id}")
                }
                // Also prefetch backdrop if available
                movie.backdropUrl?.let { url ->
                    prefetchImage(url, "movie-backdrop-${movie.id}")
                }
            }
        }
    }

    /**
     * Prefetch poster images for series
     */
    fun prefetchSeriesPosters(series: List<Series>) {
        scope.launch {
            series.take(MAX_PREFETCH_ITEMS).forEach { s ->
                s.posterUrl?.let { url ->
                    prefetchImage(url, "series-${s.id}")
                }
                s.backdropUrl?.let { url ->
                    prefetchImage(url, "series-backdrop-${s.id}")
                }
            }
        }
    }

    /**
     * Prefetch episode thumbnails for a series
     */
    fun prefetchEpisodeThumbnails(episodes: List<Episode>) {
        scope.launch {
            episodes.take(MAX_PREFETCH_ITEMS).forEach { episode ->
                episode.thumbnailUrl?.let { url ->
                    prefetchImage(url, "episode-${episode.id}")
                }
            }
        }
    }

    /**
     * Prefetch images for featured content carousel
     * Higher priority - loads backdrops for hero images
     */
    fun prefetchFeaturedContent(movies: List<Movie>, series: List<Series>) {
        scope.launch {
            // Featured items use backdrop images
            movies.take(5).forEach { movie ->
                movie.backdropUrl?.let { url ->
                    prefetchImage(url, "featured-movie-${movie.id}")
                }
                movie.posterUrl?.let { url ->
                    prefetchImage(url, "featured-movie-poster-${movie.id}")
                }
            }
            series.take(5).forEach { s ->
                s.backdropUrl?.let { url ->
                    prefetchImage(url, "featured-series-${s.id}")
                }
                s.posterUrl?.let { url ->
                    prefetchImage(url, "featured-series-poster-${s.id}")
                }
            }
        }
    }

    /**
     * Prefetch images for Continue Watching items
     * Priority loading for user's current content
     */
    fun prefetchContinueWatching(
        movieUrls: List<String>,
        episodeUrls: List<String>
    ) {
        scope.launch {
            movieUrls.forEachIndexed { index, url ->
                prefetchImage(url, "continue-movie-$index")
            }
            episodeUrls.forEachIndexed { index, url ->
                prefetchImage(url, "continue-episode-$index")
            }
        }
    }

    /**
     * Internal image prefetch with Coil
     */
    private suspend fun prefetchImage(url: String, tag: String) {
        try {
            val request = ImageRequest.Builder(context)
                .data(url)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .diskCachePolicy(CachePolicy.ENABLED)
                .build()

            imageLoader.enqueue(request)
            Log.d(TAG, "Prefetching image: $tag")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to prefetch $tag: ${e.message}")
        }
    }

    /**
     * Clear prefetch queue - call when leaving screen
     * FIX: Actually cancel pending coroutines instead of just logging
     */
    fun cancelPendingPrefetch() {
        // Cancel all child coroutines but keep the scope alive
        supervisorJob.children.forEach { it.cancel() }
        Log.d(TAG, "Prefetch queue cleared")
    }

    /**
     * Cleanup resources when the manager is no longer needed
     * Called from Application.onTerminate() or when clearing all app data
     */
    fun cleanup() {
        Log.d(TAG, "Cleaning up PrefetchManager resources")
        supervisorJob.cancel()
    }

    /**
     * CH-H2 FIX: Check if device has sufficient disk space for prefetching
     */
    private fun hasSufficientDiskSpace(): Boolean {
        return try {
            val cacheDir = context.cacheDir
            val freeSpace = cacheDir.usableSpace / (1024 * 1024) // Convert to MB
            val hasSpace = freeSpace >= MIN_FREE_DISK_SPACE_MB
            if (!hasSpace) {
                Log.w(TAG, "Low disk space: ${freeSpace}MB available, need ${MIN_FREE_DISK_SPACE_MB}MB")
            }
            hasSpace
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check disk space: ${e.message}")
            false // Fail safe - don't prefetch if check fails
        }
    }
}
