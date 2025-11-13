package com.example.farsilandtv.ui
import coil.load

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.leanback.widget.Presenter
// Glide removed - using Coil via ImageLoader
// Glide removed
import com.example.farsilandtv.R
import com.example.farsilandtv.data.model.Genre
import com.example.farsilandtv.data.models.Episode
import com.example.farsilandtv.data.models.Movie
import com.example.farsilandtv.data.models.Series
import com.example.farsilandtv.data.repository.FavoritesRepository
import com.example.farsilandtv.data.repository.PlaybackRepository
// Replaced with ImageLoader
import com.example.farsilandtv.utils.PersianUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Card Presenter with Genre Tags
 * Displays content cards with small genre badges in the bottom-left corner
 */
class GenreCardPresenter(private val context: Context? = null) : Presenter() {
    private var mDefaultCardImage: Drawable? = null

    private val favoritesRepo: FavoritesRepository? by lazy {
        context?.let { FavoritesRepository(it) }
    }
    private val playbackRepo: PlaybackRepository? by lazy {
        context?.let { PlaybackRepository(it) }
    }

    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        Log.d(TAG, "onCreateViewHolder")

        mDefaultCardImage = ContextCompat.getDrawable(parent.context, R.drawable.movie)

        // Use smaller grid layout for Movies/Shows grid pages (300x450dp instead of 550x310dp)
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.content_card_grid, parent, false)

        // Focus effects disabled per user request
        // (previously had scale animations and focus highlight overlay)

        return ViewHolder(view)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any) {
        val view = viewHolder.view as LinearLayout

        Log.d(TAG, "onBindViewHolder: ${item::class.simpleName}")

        when (item) {
            is Movie -> bindMovie(view, item)
            is Series -> bindSeries(view, item)
            is Episode -> bindEpisode(view, item)
            else -> Log.w(TAG, "Unknown item type: ${item::class.simpleName}")
        }
    }

    private fun bindMovie(view: LinearLayout, movie: Movie) {
        val imageView = view.findViewById<ImageView>(R.id.card_image)
        val badgeView = view.findViewById<ImageView>(R.id.card_badge)
        val titleView = view.findViewById<TextView>(R.id.card_title)
        val contentView = view.findViewById<TextView>(R.id.card_content)
        val primaryGenreTag = view.findViewById<TextView>(R.id.genre_tag_primary)
        val secondaryGenreTag = view.findViewById<TextView>(R.id.genre_tag_secondary)

        // Set title
        titleView.text = movie.title

        // Set content text (year and rating)
        val contentText = buildString {
            movie.year?.let { append(it.toString()) }
            movie.rating?.let {
                if (isNotEmpty()) append(" • ")
                append(PersianUtils.formatRating(it))
            }
        }
        contentView.text = contentText.ifEmpty { "فیلم" }

        // Load genre tags
        bindGenreTags(primaryGenreTag, secondaryGenreTag, movie.genres)

        // Load badge (favorite, watched, or new)
        loadBadgeForMovie(badgeView, movie)

        // Load poster image with progressive blur-up (Feature #17)
        // Card size: 173x260dp (2:3 ratio for portrait posters)
        if (!movie.posterUrl.isNullOrEmpty()) {
            com.example.farsilandtv.utils.ImageLoader.loadWithBlurUp(
                context = view.context,
                imageUrl = movie.posterUrl,
                imageView = imageView,
                placeholder = mDefaultCardImage,
                width = 173,
                height = 260
            )
        } else {
            imageView.setImageDrawable(mDefaultCardImage)
        }
    }

    private fun bindSeries(view: LinearLayout, series: Series) {
        val imageView = view.findViewById<ImageView>(R.id.card_image)
        val badgeView = view.findViewById<ImageView>(R.id.card_badge)
        val titleView = view.findViewById<TextView>(R.id.card_title)
        val contentView = view.findViewById<TextView>(R.id.card_content)
        val primaryGenreTag = view.findViewById<TextView>(R.id.genre_tag_primary)
        val secondaryGenreTag = view.findViewById<TextView>(R.id.genre_tag_secondary)

        // Set title
        titleView.text = series.title

        // Set content text (year, seasons, and rating)
        val contentText = buildString {
            series.year?.let { append(it.toString()) }
            if (series.totalSeasons > 0) {
                if (isNotEmpty()) append(" • ")
                append("${PersianUtils.toPersianNumbers(series.totalSeasons)} فصل")
            }
            series.rating?.let {
                if (isNotEmpty()) append(" • ")
                append(PersianUtils.formatRating(it))
            }
        }
        contentView.text = contentText.ifEmpty { "سریال" }

        // Load genre tags
        bindGenreTags(primaryGenreTag, secondaryGenreTag, series.genres)

        // Load badge (favorite or new)
        loadBadgeForSeries(badgeView, series)

        // Load poster image with progressive blur-up (Feature #17)
        // Card size: 173x260dp (2:3 ratio for portrait posters)
        if (!series.posterUrl.isNullOrEmpty()) {
            com.example.farsilandtv.utils.ImageLoader.loadWithBlurUp(
                context = view.context,
                imageUrl = series.posterUrl,
                imageView = imageView,
                placeholder = mDefaultCardImage,
                width = 173,
                height = 260
            )
        } else {
            imageView.setImageDrawable(mDefaultCardImage)
        }
    }

    private fun bindEpisode(view: LinearLayout, episode: Episode) {
        val imageView = view.findViewById<ImageView>(R.id.card_image)
        val badgeView = view.findViewById<ImageView>(R.id.card_badge)
        val titleView = view.findViewById<TextView>(R.id.card_title)
        val contentView = view.findViewById<TextView>(R.id.card_content)
        val primaryGenreTag = view.findViewById<TextView>(R.id.genre_tag_primary)
        val secondaryGenreTag = view.findViewById<TextView>(R.id.genre_tag_secondary)

        // Hide genre tags for episodes (not applicable)
        primaryGenreTag.visibility = View.GONE
        secondaryGenreTag.visibility = View.GONE

        // Title: Use Persian title if available, otherwise series name or episode title
        val displayTitle = episode.persianTitle
            ?: episode.seriesTitle
            ?: episode.englishTitle
            ?: episode.title
        titleView.text = displayTitle

        // Content: S00E00 format, quality badge, rating, and air date
        val contentText = buildString {
            append(episode.formattedNumber)

            episode.quality?.let {
                append(" • $it")
            }

            episode.rating?.let {
                append(" • ")
                append(PersianUtils.formatRating(it))
            }

            // Use airDate if available, fallback to releaseDate
            val date = episode.airDate ?: episode.releaseDate
            date?.let {
                if (isNotEmpty()) append(" • ")
                val formattedDate = PersianUtils.formatAirDate(it)
                append(formattedDate)
            }
        }
        contentView.text = contentText.ifEmpty { "Episode" }

        // Show NEW badge for episodes aired within last 7 days
        if (episode.isNew) {
            badgeView.setImageDrawable(
                ContextCompat.getDrawable(
                    badgeView.context,
                    R.drawable.ic_new_badge
                )
            )
            badgeView.visibility = View.VISIBLE
        } else {
            badgeView.visibility = View.GONE
        }

        // Load episode poster with progressive blur-up (Feature #17)
        // Card size: 173x260dp (2:3 ratio for portrait posters)
        val posterUrl = episode.episodePosterUrl ?: episode.thumbnailUrl
        if (!posterUrl.isNullOrEmpty()) {
            com.example.farsilandtv.utils.ImageLoader.loadWithBlurUp(
                context = view.context,
                imageUrl = posterUrl,
                imageView = imageView,
                placeholder = mDefaultCardImage,
                width = 173,
                height = 260
            )
        } else {
            imageView.setImageDrawable(mDefaultCardImage)
        }
    }

    /**
     * Bind genre tags to the card
     * Shows up to 2 genres with appropriate colors
     */
    private fun bindGenreTags(
        primaryTag: TextView,
        secondaryTag: TextView,
        genreNames: List<String>
    ) {
        // Convert genre names to Genre enums
        val genres = genreNames.mapNotNull { Genre.fromEnglishName(it) }

        if (genres.isEmpty()) {
            primaryTag.visibility = View.GONE
            secondaryTag.visibility = View.GONE
            return
        }

        // Show primary genre
        val primaryGenre = genres[0]
        primaryTag.text = primaryGenre.persianName
        primaryTag.background = createGenreBackground(primaryGenre.colorCode)
        primaryTag.visibility = View.VISIBLE

        // Show secondary genre if available
        if (genres.size > 1) {
            val secondaryGenre = genres[1]
            secondaryTag.text = secondaryGenre.persianName
            secondaryTag.background = createGenreBackground(secondaryGenre.colorCode)
            secondaryTag.visibility = View.VISIBLE
        } else {
            secondaryTag.visibility = View.GONE
        }
    }

    /**
     * Create genre badge background with specific color
     */
    private fun createGenreBackground(colorCode: String): Drawable {
        val drawable = GradientDrawable()
        drawable.shape = GradientDrawable.RECTANGLE
        drawable.cornerRadius = 8f * 2 // Convert dp to pixels (approx)
        try {
            drawable.setColor(Color.parseColor(colorCode))
        } catch (e: Exception) {
            drawable.setColor(Color.parseColor("#9E9E9E")) // Gray fallback
        }
        return drawable
    }

    /**
     * Load badge for movie (priority: watched > favorited > new)
     */
    private fun loadBadgeForMovie(badgeView: ImageView, movie: Movie) {
        playbackRepo?.let { repo ->
            CoroutineScope(Dispatchers.Main).launch {
                try {
                    val isWatched = repo.isCompleted(movie.id, "movie").first() ?: false
                    if (isWatched) {
                        badgeView.setImageDrawable(
                            ContextCompat.getDrawable(
                                badgeView.context,
                                R.drawable.ic_watched_badge
                            )
                        )
                        badgeView.visibility = View.VISIBLE
                        return@launch
                    }

                    checkFavoriteMovie(badgeView, movie)
                } catch (e: Exception) {
                    Log.e(TAG, "Error checking watched status for movie ${movie.id}", e)
                    checkFavoriteMovie(badgeView, movie)
                }
            }
        } ?: checkFavoriteMovie(badgeView, movie)
    }

    private fun checkFavoriteMovie(badgeView: ImageView, movie: Movie) {
        favoritesRepo?.let { repo ->
            CoroutineScope(Dispatchers.Main).launch {
                try {
                    val isFavorited = repo.isMovieFavorited(movie.id).first()
                    if (isFavorited) {
                        badgeView.setImageDrawable(
                            ContextCompat.getDrawable(
                                badgeView.context,
                                R.drawable.ic_favorite_badge
                            )
                        )
                        badgeView.visibility = View.VISIBLE
                        return@launch
                    }

                    if (movie.isNew) {
                        badgeView.setImageDrawable(
                            ContextCompat.getDrawable(
                                badgeView.context,
                                R.drawable.ic_new_badge
                            )
                        )
                        badgeView.visibility = View.VISIBLE
                    } else {
                        badgeView.visibility = View.GONE
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error checking favorite status for movie ${movie.id}", e)
                    if (movie.isNew) {
                        badgeView.setImageDrawable(
                            ContextCompat.getDrawable(
                                badgeView.context,
                                R.drawable.ic_new_badge
                            )
                        )
                        badgeView.visibility = View.VISIBLE
                    } else {
                        badgeView.visibility = View.GONE
                    }
                }
            }
        } ?: run {
            if (movie.isNew) {
                badgeView.setImageDrawable(
                    ContextCompat.getDrawable(
                        badgeView.context,
                        R.drawable.ic_new_badge
                    )
                )
                badgeView.visibility = View.VISIBLE
            } else {
                badgeView.visibility = View.GONE
            }
        }
    }

    /**
     * Load badge for series (priority: favorited > new)
     */
    private fun loadBadgeForSeries(badgeView: ImageView, series: Series) {
        favoritesRepo?.let { repo ->
            CoroutineScope(Dispatchers.Main).launch {
                try {
                    val isFavorited = repo.isSeriesFavorited(series.id).first()
                    if (isFavorited) {
                        badgeView.setImageDrawable(
                            ContextCompat.getDrawable(
                                badgeView.context,
                                R.drawable.ic_favorite_badge
                            )
                        )
                        badgeView.visibility = View.VISIBLE
                        return@launch
                    }

                    if (series.isNew) {
                        badgeView.setImageDrawable(
                            ContextCompat.getDrawable(
                                badgeView.context,
                                R.drawable.ic_new_badge
                            )
                        )
                        badgeView.visibility = View.VISIBLE
                    } else {
                        badgeView.visibility = View.GONE
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error checking favorite status for series ${series.id}", e)
                    if (series.isNew) {
                        badgeView.setImageDrawable(
                            ContextCompat.getDrawable(
                                badgeView.context,
                                R.drawable.ic_new_badge
                            )
                        )
                        badgeView.visibility = View.VISIBLE
                    } else {
                        badgeView.visibility = View.GONE
                    }
                }
            }
        } ?: run {
            if (series.isNew) {
                badgeView.setImageDrawable(
                    ContextCompat.getDrawable(
                        badgeView.context,
                        R.drawable.ic_new_badge
                    )
                )
                badgeView.visibility = View.VISIBLE
            } else {
                badgeView.visibility = View.GONE
            }
        }
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {
        Log.d(TAG, "onUnbindViewHolder")
        val view = viewHolder.view as LinearLayout
        val imageView = view.findViewById<ImageView>(R.id.card_image)
        val badgeView = view.findViewById<ImageView>(R.id.card_badge)

        // Coil handles cleanup automatically when views are recycled
        // No manual cleanup needed (unlike Glide)

        imageView?.setImageDrawable(null)
        badgeView?.setImageDrawable(null)
    }

    companion object {
        private const val TAG = "GenreCardPresenter"
    }
}
