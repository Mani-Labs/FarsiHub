package com.example.farsilandtv.utils

import android.content.Context
import android.graphics.drawable.Drawable
import android.widget.ImageView
import androidx.core.content.ContextCompat
import coil.ImageLoader
import coil.load
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Scale
import com.example.farsilandtv.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Image loading utility using Coil (replaced Glide)
 *
 * Migration from Glide to Coil:
 * - Modern, Kotlin-first library
 * - Smaller APK footprint (~2MB savings)
 * - Better Compose integration
 * - Coroutines support
 *
 * Performance targets:
 * - Memory-efficient caching
 * - < 500ms load time per image
 * - > 80% cache hit rate
 */
object ImageLoader {

    /**
     * Shared Coil ImageLoader instance with optimized caching
     */
    private var imageLoader: ImageLoader? = null

    private fun getImageLoader(context: Context): ImageLoader {
        if (imageLoader == null) {
            imageLoader = createOptimizedImageLoader(context.applicationContext)
        }
        return imageLoader!!
    }

    /**
     * Load image with progressive blur-up effect using Coil
     *
     * Coil provides similar blur-up loading through:
     * - Placeholder for immediate display
     * - Progressive image decoding
     * - Cross-fade transitions
     *
     * @param context Android context
     * @param imageUrl Full resolution image URL
     * @param imageView Target ImageView
     * @param placeholder Optional placeholder drawable
     * @param width Target width (default: 480px)
     * @param height Target height (default: 270px)
     */
    fun loadWithBlurUp(
        context: Context,
        imageUrl: String?,
        imageView: ImageView,
        placeholder: Drawable? = null,
        width: Int = 480,
        height: Int = 270
    ) {
        if (imageUrl.isNullOrEmpty()) {
            imageView.setImageDrawable(
                placeholder ?: ContextCompat.getDrawable(context, R.drawable.image_placeholder)
            )
            return
        }

        // Load image with Coil
        imageView.load(imageUrl, getImageLoader(context)) {
            crossfade(300) // 300ms cross-fade (same as Glide version)
            placeholder(placeholder ?: ContextCompat.getDrawable(context, R.drawable.image_placeholder))
            error(R.drawable.movie)
            size(width, height)
            scale(Scale.FILL) // Equivalent to centerCrop
            memoryCachePolicy(CachePolicy.ENABLED)
            diskCachePolicy(CachePolicy.ENABLED)
        }
    }

    /**
     * Load image with configurable scaling
     *
     * DEEP AUDIT FIX: Added scaleType parameter
     * - Use Scale.FILL for backgrounds (fills screen, may crop)
     * - Use Scale.FIT for cards (fits inside, preserves aspect ratio)
     *
     * Use for:
     * - Background images (pass Scale.FILL)
     * - Detail screens
     * - Non-scrolling content
     *
     * @param context Android context
     * @param imageUrl Image URL
     * @param imageView Target ImageView
     * @param scaleType Scaling mode (default: Scale.FIT for cards)
     */
    fun load(
        context: Context,
        imageUrl: String?,
        imageView: ImageView,
        scaleType: Scale = Scale.FIT  // DEEP AUDIT FIX: Default to FIT for cards
    ) {
        if (imageUrl.isNullOrEmpty()) {
            imageView.setImageDrawable(ContextCompat.getDrawable(context, R.drawable.image_placeholder))
            return
        }

        imageView.load(imageUrl, getImageLoader(context)) {
            crossfade(300)
            placeholder(R.drawable.image_placeholder)
            error(R.drawable.movie)
            size(480, 270) // Standard card size
            // DEEP AUDIT FIX: Use parameterized scale type
            // Backgrounds should pass Scale.FILL, cards should use Scale.FIT (default)
            scale(scaleType)
            memoryCachePolicy(CachePolicy.ENABLED)
            diskCachePolicy(CachePolicy.ENABLED)
        }
    }

    /**
     * Preload images for adjacent items
     *
     * Benefits:
     * - Smooth scrolling experience
     * - No loading delay when user navigates
     * - Images already in cache
     *
     * AUDIT FIX C1.1: Now accepts lifecycle-aware CoroutineScope to prevent memory leaks
     * Caller MUST pass lifecycleScope or viewModelScope
     *
     * AUDIT FIX #27: Use applicationContext to prevent Activity context leak
     *
     * @param context Android context
     * @param scope Lifecycle-aware CoroutineScope (lifecycleScope or viewModelScope)
     * @param currentPosition Current focused position
     * @param totalItems Total number of items in list
     * @param imageUrlProvider Function that returns image URL for a position
     * @param cardWidth Target card width (default: 480px)
     * @param cardHeight Target card height (default: 270px)
     * @param preloadRange Number of items to preload ahead/behind (default: 3)
     */
    fun preloadAdjacentImages(
        context: Context,
        scope: CoroutineScope,
        currentPosition: Int,
        totalItems: Int,
        imageUrlProvider: (Int) -> String?,
        cardWidth: Int = 480,
        cardHeight: Int = 270,
        preloadRange: Int = 3
    ) {
        // AUDIT FIX #27: Use applicationContext to prevent memory leak
        val appContext = context.applicationContext
        val loader = getImageLoader(appContext)

        // Preload items in range [currentPosition - 3, currentPosition + 3]
        // AUDIT FIX C1.1: Use provided lifecycle-aware scope instead of creating new one
        scope.launch(Dispatchers.IO) {
            for (offset in -preloadRange..preloadRange) {
                val position = currentPosition + offset

                // Skip if out of bounds or current position
                if (position !in 0 until totalItems || position == currentPosition) {
                    continue
                }

                val imageUrl = imageUrlProvider(position)
                if (!imageUrl.isNullOrEmpty()) {
                    // Preload into cache
                    // AUDIT FIX #27: Use applicationContext to prevent context leak
                    val request = ImageRequest.Builder(appContext)
                        .data(imageUrl)
                        .size(cardWidth, cardHeight)
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .build()

                    loader.enqueue(request)
                }
            }
        }
    }

    /**
     * Preload single image (for immediate next item)
     *
     * Use for:
     * - Featured carousel (preload next slide)
     * - Detail screens (preload related content)
     *
     * AUDIT FIX C1.1: Now accepts lifecycle-aware CoroutineScope to prevent memory leaks
     * Caller MUST pass lifecycleScope or viewModelScope
     *
     * AUDIT FIX #27: Use applicationContext to prevent Activity context leak
     *
     * @param context Android context
     * @param scope Lifecycle-aware CoroutineScope (lifecycleScope or viewModelScope)
     * @param imageUrl Image URL to preload
     * @param width Target width
     * @param height Target height
     */
    fun preloadImage(
        context: Context,
        scope: CoroutineScope,
        imageUrl: String?,
        width: Int = 1920,
        height: Int = 1080
    ) {
        if (imageUrl.isNullOrEmpty()) return

        // AUDIT FIX #27: Use applicationContext to prevent memory leak
        val appContext = context.applicationContext
        val loader = getImageLoader(appContext)

        // AUDIT FIX C1.1: Use provided lifecycle-aware scope instead of creating new one
        scope.launch(Dispatchers.IO) {
            // AUDIT FIX #27: Use applicationContext to prevent context leak
            val request = ImageRequest.Builder(appContext)
                .data(imageUrl)
                .size(width, height)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .diskCachePolicy(CachePolicy.ENABLED)
                .build()

            loader.enqueue(request)
        }
    }

    /**
     * Clear memory cache (Coil version)
     *
     * Use when:
     * - Low memory warning received
     * - User clears cache manually
     */
    fun clearMemoryCache(context: Context) {
        getImageLoader(context).memoryCache?.clear()
    }

    /**
     * Clear disk cache (Coil version)
     *
     * Use when:
     * - User clears cache manually
     * - Corrupted cache detected
     */
    suspend fun clearDiskCache(context: Context) {
        getImageLoader(context).diskCache?.clear()
    }

    /**
     * Get memory cache size in MB (for debugging/profiling)
     */
    fun getMemoryCacheSize(context: Context): Long {
        val memoryCache = getImageLoader(context).memoryCache
        val maxSize = memoryCache?.maxSize?.toLong() ?: 0L
        return maxSize / (1024 * 1024)
    }
}
