package com.example.farsilandtv.ui
import coil.load

import android.content.Context
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.util.Log
import android.util.TypedValue
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.leanback.widget.ImageCardView
import androidx.leanback.widget.Presenter
import com.example.farsilandtv.R
import com.example.farsilandtv.data.models.Movie
import com.example.farsilandtv.data.models.Series
import com.example.farsilandtv.data.models.Episode
import com.example.farsilandtv.data.repository.FavoritesRepository
import com.example.farsilandtv.data.repository.PlaybackRepository
import com.example.farsilandtv.utils.PersianUtils
import com.example.farsilandtv.utils.ImageLoader
import com.example.farsilandtv.utils.SourceBadgeHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import java.util.WeakHashMap
import kotlin.properties.Delegates

/**
 * CardPresenter for displaying Movies and TV Series with badges
 * Shows favorite (heart) and watched (checkmark) badges when applicable
 */
class ContentCardPresenter(private val context: Context? = null) : Presenter() {
    private var mDefaultCardImage: Drawable? = null
    private var sSelectedBackgroundColor: Int by Delegates.notNull()
    private var sDefaultBackgroundColor: Int by Delegates.notNull()

    private val favoritesRepo: FavoritesRepository? by lazy {
        context?.let { FavoritesRepository.getInstance(it) }
    }
    private val playbackRepo: PlaybackRepository? by lazy {
        context?.let { PlaybackRepository.getInstance(it) }
    }

    /**
     * FIX: Managed coroutine scopes per ViewHolder to prevent memory leaks
     * When view is recycled/unbound, scope is cancelled to stop orphan coroutines
     */
    private val viewHolderScopes = WeakHashMap<ViewHolder, CoroutineScope>()

    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        Log.d(TAG, "onCreateViewHolder")

        sDefaultBackgroundColor = ContextCompat.getColor(parent.context, R.color.default_background)
        sSelectedBackgroundColor = ContextCompat.getColor(parent.context, R.color.selected_background)
        mDefaultCardImage = ContextCompat.getDrawable(parent.context, R.drawable.movie)

        val cardView = object : ImageCardView(parent.context) {
            override fun setSelected(selected: Boolean) {
                updateCardBackgroundColor(this, selected)
                super.setSelected(selected)
            }
        }

        cardView.isFocusable = true
        cardView.isFocusableInTouchMode = true
        updateCardBackgroundColor(cardView, false)

        // Add focus change listener for animations
        cardView.setOnFocusChangeListener { view, hasFocus ->
            val scaleAnim = if (hasFocus) {
                AnimationUtils.loadAnimation(parent.context, R.anim.scale_up)
            } else {
                AnimationUtils.loadAnimation(parent.context, R.anim.scale_down)
            }
            view.startAnimation(scaleAnim)

            // Apply focus highlight drawable
            if (hasFocus) {
                view.foreground = ContextCompat.getDrawable(parent.context, R.drawable.focus_highlight)
            } else {
                view.foreground = null
                // Reset background to default when focus is lost
                updateCardBackgroundColor(cardView, false)
            }
        }

        return ViewHolder(cardView)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any) {
        val cardView = viewHolder.view as ImageCardView
        // FIX: Get managed scope for this ViewHolder (cancelled in onUnbindViewHolder)
        val scope = getScopeForViewHolder(viewHolder)

        Log.d(TAG, "onBindViewHolder: ${item::class.simpleName}")

        when (item) {
            is Movie -> bindMovie(cardView, item, scope)
            is Series -> bindSeries(cardView, item, scope)
            is Episode -> bindEpisode(cardView, item)
            else -> Log.w(TAG, "Unknown item type: ${item::class.simpleName}")
        }
    }

    /**
     * Bind Movie data to card view
     */
    private fun bindMovie(cardView: ImageCardView, movie: Movie, scope: CoroutineScope) {
        cardView.titleText = movie.title

        // Show year and rating if available (year in English, rating in Persian)
        val contentText = buildString {
            movie.year?.let { append(it.toString()) }  // English year
            movie.rating?.let {
                if (isNotEmpty()) append(" • ")
                append(PersianUtils.formatRating(it))
            }
        }

        // Add source badge to content text
        val textWithBadge = if (contentText.isEmpty()) {
            SourceBadgeHelper.prependBadge(cardView.context, movie.farsilandUrl, "فیلم")
        } else {
            SourceBadgeHelper.prependBadge(cardView.context, movie.farsilandUrl, contentText)
        }
        cardView.contentText = textWithBadge

        // Load badge (favorite, watched, or new) using managed scope
        loadBadgeForMovie(cardView, movie, scope)

        // Load poster image with progressive blur-up (Feature #17)
        cardView.setMainImageDimensions(CARD_WIDTH, CARD_HEIGHT)

        if (!movie.posterUrl.isNullOrEmpty()) {
            com.example.farsilandtv.utils.ImageLoader.loadWithBlurUp(
                context = cardView.context,
                imageUrl = movie.posterUrl,
                imageView = cardView.mainImageView,
                placeholder = mDefaultCardImage,
                width = CARD_WIDTH,
                height = CARD_HEIGHT
            )
        } else {
            cardView.mainImage = mDefaultCardImage
        }
    }

    /**
     * Bind Series data to card view
     */
    private fun bindSeries(cardView: ImageCardView, series: Series, scope: CoroutineScope) {
        cardView.titleText = series.title

        // Show year, seasons, and rating (year in English, others in Persian)
        val contentText = buildString {
            series.year?.let { append(it.toString()) }  // English year
            if (series.totalSeasons > 0) {
                if (isNotEmpty()) append(" • ")
                append("${PersianUtils.toPersianNumbers(series.totalSeasons)} فصل")
            }
            series.rating?.let {
                if (isNotEmpty()) append(" • ")
                append(PersianUtils.formatRating(it))
            }
        }

        // Add source badge to content text
        val textWithBadge = if (contentText.isEmpty()) {
            SourceBadgeHelper.prependBadge(cardView.context, series.farsilandUrl, "سریال")
        } else {
            SourceBadgeHelper.prependBadge(cardView.context, series.farsilandUrl, contentText)
        }
        cardView.contentText = textWithBadge

        // Load badge (favorite or new) using managed scope
        loadBadgeForSeries(cardView, series, scope)

        // Load poster image with progressive blur-up (Feature #17)
        cardView.setMainImageDimensions(CARD_WIDTH, CARD_HEIGHT)

        if (!series.posterUrl.isNullOrEmpty()) {
            com.example.farsilandtv.utils.ImageLoader.loadWithBlurUp(
                context = cardView.context,
                imageUrl = series.posterUrl,
                imageView = cardView.mainImageView,
                placeholder = mDefaultCardImage,
                width = CARD_WIDTH,
                height = CARD_HEIGHT
            )
        } else {
            cardView.mainImage = mDefaultCardImage
        }
    }

    /**
     * Bind Episode data to card view
     */
    private fun bindEpisode(cardView: ImageCardView, episode: Episode) {
        // Title: Use Persian title if available, otherwise series name or episode title
        val displayTitle = episode.persianTitle
            ?: episode.seriesTitle
            ?: episode.englishTitle
            ?: episode.title
        cardView.titleText = displayTitle

        // Content: S00E00 format, quality badge, rating, and release date
        val contentText = buildString {
            append(episode.formattedNumber)

            // Add quality badge (HD, SD, etc.)
            episode.quality?.let {
                append(" • $it")
            }

            // Add rating if available
            episode.rating?.let {
                append(" • ")
                append(PersianUtils.formatRating(it))
            }

            // Add release date (prefer releaseDate over airDate)
            val date = episode.releaseDate ?: episode.airDate
            date?.let {
                if (isNotEmpty()) append(" • ")
                // Show only month and day if it's a full date
                val shortDate = if (it.contains(",")) {
                    it.substringBefore(",") // "Oct. 29, 2025" -> "Oct. 29"
                } else {
                    it
                }
                append(shortDate)
            }
        }

        // Add source badge to content text
        val textWithBadge = if (contentText.isEmpty()) {
            SourceBadgeHelper.prependBadge(cardView.context, episode.farsilandUrl, "Episode")
        } else {
            SourceBadgeHelper.prependBadge(cardView.context, episode.farsilandUrl, contentText)
        }
        cardView.contentText = textWithBadge

        // Reduce text size for episode cards (make text smaller)
        try {
            val titleView = cardView.findViewById<TextView>(androidx.leanback.R.id.title_text)
            val contentView = cardView.findViewById<TextView>(androidx.leanback.R.id.content_text)

            titleView?.let {
                // Get current size and reduce by 1sp
                val currentSize = it.textSize / cardView.resources.displayMetrics.scaledDensity
                it.setTextSize(TypedValue.COMPLEX_UNIT_SP, currentSize - 1)
            }

            contentView?.let {
                // Get current size and reduce by 1sp
                val currentSize = it.textSize / cardView.resources.displayMetrics.scaledDensity
                it.setTextSize(TypedValue.COMPLEX_UNIT_SP, currentSize - 1)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not adjust episode text size", e)
        }

        // Load episode poster with progressive blur-up (Feature #17)
        cardView.setMainImageDimensions(CARD_WIDTH, CARD_HEIGHT)

        val posterUrl = episode.episodePosterUrl ?: episode.thumbnailUrl
        if (!posterUrl.isNullOrEmpty()) {
            com.example.farsilandtv.utils.ImageLoader.loadWithBlurUp(
                context = cardView.context,
                imageUrl = posterUrl,
                imageView = cardView.mainImageView,
                placeholder = mDefaultCardImage,
                width = CARD_WIDTH,
                height = CARD_HEIGHT
            )
        } else {
            cardView.mainImage = mDefaultCardImage
        }
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {
        Log.d(TAG, "onUnbindViewHolder")
        val cardView = viewHolder.view as ImageCardView

        // FIX: Cancel any pending badge-loading coroutines to prevent memory leaks
        viewHolderScopes[viewHolder]?.cancel()
        viewHolderScopes.remove(viewHolder)

        // Remove references to images for garbage collection
        cardView.badgeImage = null
        cardView.mainImage = null
    }

    /**
     * FIX: Get or create a managed scope for this ViewHolder
     * Scopes are cancelled in onUnbindViewHolder to prevent orphan coroutines
     */
    private fun getScopeForViewHolder(viewHolder: ViewHolder): CoroutineScope {
        return viewHolderScopes.getOrPut(viewHolder) {
            CoroutineScope(Dispatchers.Main + SupervisorJob())
        }
    }

    private fun updateCardBackgroundColor(view: ImageCardView, selected: Boolean) {
        val color = if (selected) sSelectedBackgroundColor else sDefaultBackgroundColor
        view.setBackgroundColor(color)
        view.setInfoAreaBackgroundColor(color)
    }

    /**
     * Load badge for movie (priority: watched > favorited > new)
     * FIX: Uses managed scope instead of orphan CoroutineScope
     */
    private fun loadBadgeForMovie(cardView: ImageCardView, movie: Movie, scope: CoroutineScope) {
        // Check watched status first (highest priority)
        playbackRepo?.let { repo ->
            scope.launch {
                try {
                    val isWatched = repo.isCompleted(movie.id, "movie").first() ?: false
                    if (isWatched) {
                        cardView.badgeImage = ContextCompat.getDrawable(
                            cardView.context,
                            R.drawable.ic_watched_badge
                        )
                        return@launch
                    }

                    // Check favorite status (second priority)
                    checkFavoriteMovie(cardView, movie, scope)
                } catch (e: Exception) {
                    Log.e(TAG, "Error checking watched status for movie ${movie.id}", e)
                    checkFavoriteMovie(cardView, movie, scope)
                }
            }
        } ?: checkFavoriteMovie(cardView, movie, scope)
    }

    /**
     * Check favorite status for movie
     * FIX: Uses managed scope instead of orphan CoroutineScope
     */
    private fun checkFavoriteMovie(cardView: ImageCardView, movie: Movie, scope: CoroutineScope) {
        favoritesRepo?.let { repo ->
            scope.launch {
                try {
                    val isFavorited = repo.isMovieFavorited(movie.id).first()
                    if (isFavorited) {
                        cardView.badgeImage = ContextCompat.getDrawable(
                            cardView.context,
                            R.drawable.ic_favorite_badge
                        )
                        return@launch
                    }

                    // Show NEW badge if recently added (lowest priority)
                    if (movie.isNew) {
                        cardView.badgeImage = ContextCompat.getDrawable(
                            cardView.context,
                            R.drawable.ic_new_badge
                        )
                    } else {
                        cardView.badgeImage = null
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error checking favorite status for movie ${movie.id}", e)
                    // Fallback to NEW badge if available
                    if (movie.isNew) {
                        cardView.badgeImage = ContextCompat.getDrawable(
                            cardView.context,
                            R.drawable.ic_new_badge
                        )
                    } else {
                        cardView.badgeImage = null
                    }
                }
            }
        } ?: run {
            // No repo available, show NEW badge if applicable
            if (movie.isNew) {
                cardView.badgeImage = ContextCompat.getDrawable(
                    cardView.context,
                    R.drawable.ic_new_badge
                )
            } else {
                cardView.badgeImage = null
            }
        }
    }

    /**
     * Load badge for series (priority: favorited > new)
     * FIX: Uses managed scope instead of orphan CoroutineScope
     */
    private fun loadBadgeForSeries(cardView: ImageCardView, series: Series, scope: CoroutineScope) {
        favoritesRepo?.let { repo ->
            scope.launch {
                try {
                    val isFavorited = repo.isSeriesFavorited(series.id).first()
                    if (isFavorited) {
                        cardView.badgeImage = ContextCompat.getDrawable(
                            cardView.context,
                            R.drawable.ic_favorite_badge
                        )
                        return@launch
                    }

                    // Show NEW badge if recently added
                    if (series.isNew) {
                        cardView.badgeImage = ContextCompat.getDrawable(
                            cardView.context,
                            R.drawable.ic_new_badge
                        )
                    } else {
                        cardView.badgeImage = null
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error checking favorite status for series ${series.id}", e)
                    // Fallback to NEW badge if available
                    if (series.isNew) {
                        cardView.badgeImage = ContextCompat.getDrawable(
                            cardView.context,
                            R.drawable.ic_new_badge
                        )
                    } else {
                        cardView.badgeImage = null
                    }
                }
            }
        } ?: run {
            // No repo available, show NEW badge if applicable
            if (series.isNew) {
                cardView.badgeImage = ContextCompat.getDrawable(
                    cardView.context,
                    R.drawable.ic_new_badge
                )
            } else {
                cardView.badgeImage = null
            }
        }
    }

    companion object {
        private const val TAG = "ContentCardPresenter"
        private const val CARD_WIDTH = 550  // Larger cards for better visibility
        private const val CARD_HEIGHT = 310  // Maintain aspect ratio
    }
}
