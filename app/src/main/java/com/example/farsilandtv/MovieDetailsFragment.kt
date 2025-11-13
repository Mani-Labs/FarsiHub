package com.example.farsilandtv
import coil.load

import android.app.AlertDialog
import android.app.ProgressDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.leanback.app.DetailsSupportFragment
import androidx.leanback.app.DetailsSupportFragmentBackgroundController
import androidx.leanback.widget.*
import com.example.farsilandtv.data.models.Movie
import com.example.farsilandtv.data.models.VideoUrl
import com.example.farsilandtv.data.repository.WatchlistRepository
import com.example.farsilandtv.data.repository.FavoritesRepository
import com.example.farsilandtv.data.repository.PlaybackRepository
import com.example.farsilandtv.data.database.Favorite
import com.example.farsilandtv.data.scraper.VideoUrlScraper
import com.example.farsilandtv.data.scraper.ScraperResult
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first

/**
 * Movie Details Fragment - displays rich metadata for a movie
 * Includes hero section, action buttons, and movie information
 */
class MovieDetailsFragment : DetailsSupportFragment() {

    companion object {
        private const val TAG = "MovieDetailsFragment"
        const val ARG_MOVIE = "movie"

        private const val ACTION_PLAY = 1L
        private const val ACTION_WATCHLIST = 2L
        private const val ACTION_FAVORITE = 3L
        private const val ACTION_WATCHED = 4L
        private const val ACTION_ADD_TO_PLAYLIST = 5L

        private const val DETAIL_THUMB_WIDTH = 274
        private const val DETAIL_THUMB_HEIGHT = 400
    }

    private var selectedMovie: Movie? = null
    private lateinit var detailsBackground: DetailsSupportFragmentBackgroundController
    private lateinit var presenterSelector: ClassPresenterSelector
    private lateinit var rowAdapter: ArrayObjectAdapter
    private val watchlistRepo by lazy { WatchlistRepository(requireContext()) }
    private val favoritesRepo by lazy { FavoritesRepository(requireContext()) }
    private val playbackRepo by lazy { PlaybackRepository(requireContext()) }
    private var isInWatchlist = false
    private var isFavorited = false
    private var isWatched = false
    private lateinit var detailsRow: DetailsOverviewRow
    private lateinit var actionsAdapter: ArrayObjectAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.d(TAG, "onCreate MovieDetailsFragment")

        detailsBackground = DetailsSupportFragmentBackgroundController(this)

        // Get movie from arguments
        selectedMovie = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arguments?.getSerializable(ARG_MOVIE, Movie::class.java)
        } else {
            @Suppress("DEPRECATION")
            arguments?.getSerializable(ARG_MOVIE) as? Movie
        }

        if (selectedMovie != null) {
            presenterSelector = ClassPresenterSelector()
            rowAdapter = ArrayObjectAdapter(presenterSelector)

            setupDetailsOverviewRowPresenter()
            adapter = rowAdapter

        } else {
            Log.e(TAG, "No movie data provided")
            activity?.finish()
        }
    }

    override fun onViewCreated(view: android.view.View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Now that viewLifecycleOwner is available, load images and setup row
        selectedMovie?.let { movie ->
            setupDetailsOverviewRow()
            initializeBackground(movie)
        }
    }

    private fun initializeBackground(movie: Movie?) {
        detailsBackground.enableParallax()

        // Use backdrop if available, otherwise poster
        val backgroundUrl = movie?.backdropUrl ?: movie?.posterUrl

        viewLifecycleOwner.lifecycleScope.launch {
            val imageView = android.widget.ImageView(requireContext())
            imageView.load(backgroundUrl) {
                lifecycle(viewLifecycleOwner)
                size(1920, 1080)
                crossfade(300)
                target(
                    onSuccess = { drawable ->
                        // Convert drawable to bitmap for background
                        val bitmap = (drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
                        if (bitmap != null) {
                            detailsBackground.coverBitmap = bitmap
                            rowAdapter.notifyArrayItemRangeChanged(0, rowAdapter.size())
                        }
                    }
                )
            }
        }
    }

    private fun setupDetailsOverviewRow() {
        Log.d(TAG, "Setting up details for: ${selectedMovie?.title}")

        detailsRow = DetailsOverviewRow(selectedMovie)

        // Set poster image
        detailsRow.imageDrawable = ContextCompat.getDrawable(
            requireContext(),
            R.drawable.default_background
        )

        val width = convertDpToPixel(requireContext(), DETAIL_THUMB_WIDTH)
        val height = convertDpToPixel(requireContext(), DETAIL_THUMB_HEIGHT)

        viewLifecycleOwner.lifecycleScope.launch {
            val posterImageView = android.widget.ImageView(requireContext())
            posterImageView.load(selectedMovie?.posterUrl) {
                lifecycle(viewLifecycleOwner)
                size(width, height)
                crossfade(300)
                placeholder(R.drawable.image_placeholder)
                error(R.drawable.default_background)
                target(
                    onSuccess = { drawable ->
                        detailsRow.imageDrawable = drawable
                        rowAdapter.notifyArrayItemRangeChanged(0, rowAdapter.size())
                    }
                )
            }
        }

        // Load all button states first
        lifecycleScope.launch {
            actionsAdapter = ArrayObjectAdapter()

            // Play button (primary action)
            actionsAdapter.add(Action(
                ACTION_PLAY,
                "▶️ Play Movie"
            ))

            selectedMovie?.let { movie ->
                // Check watchlist status
                isInWatchlist = watchlistRepo.isMovieInWatchlist(movie.id)
                val watchlistText = if (isInWatchlist) "✓ In Watchlist" else "➕ Add to Watchlist"
                actionsAdapter.add(Action(ACTION_WATCHLIST, watchlistText))

                // Check favorite status
                isFavorited = favoritesRepo.isMovieFavorited(movie.id).first()
                val favoriteText = if (isFavorited) "❤️ Favorited" else "🤍 Add to Favorites"
                actionsAdapter.add(Action(ACTION_FAVORITE, favoriteText))

                // Check watched status
                isWatched = playbackRepo.isCompleted(movie.id, "movie").first() ?: false
                val watchedText = if (isWatched) "✓ Watched" else "Mark as Watched"
                actionsAdapter.add(Action(ACTION_WATCHED, watchedText))

                // Add to Playlist action
                actionsAdapter.add(Action(ACTION_ADD_TO_PLAYLIST, "➕ Add to Playlist"))
            }

            detailsRow.actionsAdapter = actionsAdapter
            rowAdapter.add(detailsRow)  // Only add after complete
        }
    }

    /**
     * Update the watchlist button text based on current status
     */
    private fun updateWatchlistButton() {
        val buttonText = if (isInWatchlist) {
            "✓ In Watchlist"
        } else {
            "➕ Add to Watchlist"
        }
        updateActionButton(ACTION_WATCHLIST, buttonText)
    }

    /**
     * Update the favorite button text based on current status
     */
    private fun updateFavoriteButton() {
        val buttonText = if (isFavorited) {
            "❤️ Favorited"
        } else {
            "🤍 Add to Favorites"
        }
        updateActionButton(ACTION_FAVORITE, buttonText)
    }

    /**
     * Update the watched button text based on current status
     */
    private fun updateWatchedButton() {
        val buttonText = if (isWatched) {
            "✓ Watched"
        } else {
            "Mark as Watched"
        }
        updateActionButton(ACTION_WATCHED, buttonText)
    }

    /**
     * Helper method to update action button text
     */
    private fun updateActionButton(actionId: Long, newText: String) {
        var actionIndex = -1
        for (i in 0 until actionsAdapter.size()) {
            val action = actionsAdapter.get(i) as? Action
            if (action?.id == actionId) {
                actionIndex = i
                break
            }
        }

        if (actionIndex >= 0) {
            actionsAdapter.removeItems(actionIndex, 1)
            actionsAdapter.add(actionIndex, Action(actionId, newText))
        } else {
            actionsAdapter.add(Action(actionId, newText))
        }
    }

    private fun setupDetailsOverviewRowPresenter() {
        // Set detail background
        val detailsPresenter = FullWidthDetailsOverviewRowPresenter(
            DetailsDescriptionPresenter()
        )

        detailsPresenter.backgroundColor = ContextCompat.getColor(
            requireContext(),
            R.color.selected_background
        )

        // Make the overview more compact by starting in small state
        detailsPresenter.initialState = FullWidthDetailsOverviewRowPresenter.STATE_SMALL

        // Hook up transition element
        val sharedElementHelper = FullWidthDetailsOverviewSharedElementHelper()
        sharedElementHelper.setSharedElementEnterTransition(
            activity,
            DetailsActivity.SHARED_ELEMENT_NAME
        )
        detailsPresenter.setListener(sharedElementHelper)
        detailsPresenter.isParticipatingEntranceTransition = true

        detailsPresenter.onActionClickedListener = OnActionClickedListener { action ->
            when (action.id) {
                ACTION_PLAY -> {
                    selectedMovie?.let { playMovie(it) }
                }
                ACTION_WATCHLIST -> {
                    selectedMovie?.let { movie ->
                        lifecycleScope.launch {
                            if (isInWatchlist) {
                                watchlistRepo.removeMovieFromWatchlist(movie.id)
                                isInWatchlist = false
                                Toast.makeText(
                                    requireContext(),
                                    "Removed from watchlist",
                                    Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                watchlistRepo.addMovieToWatchlist(movie)
                                isInWatchlist = true
                                Toast.makeText(
                                    requireContext(),
                                    "Added to watchlist",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            updateWatchlistButton()
                        }
                    }
                }
                ACTION_FAVORITE -> {
                    selectedMovie?.let { movie ->
                        lifecycleScope.launch {
                            if (isFavorited) {
                                favoritesRepo.removeMovieFromFavorites(movie.id)
                                isFavorited = false
                                Toast.makeText(
                                    requireContext(),
                                    "Removed from favorites",
                                    Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                favoritesRepo.addMovieToFavorites(movie)
                                isFavorited = true
                                Toast.makeText(
                                    requireContext(),
                                    "Added to favorites",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            updateFavoriteButton()
                        }
                    }
                }
                ACTION_WATCHED -> {
                    selectedMovie?.let { movie ->
                        lifecycleScope.launch {
                            if (isWatched) {
                                playbackRepo.markAsIncomplete(movie.id, "movie")
                                isWatched = false
                                Toast.makeText(
                                    requireContext(),
                                    "Marked as unwatched",
                                    Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                playbackRepo.markAsCompleted(movie.id, "movie")
                                isWatched = true
                                Toast.makeText(
                                    requireContext(),
                                    "Marked as watched",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            updateWatchedButton()
                        }
                    }
                }
                ACTION_ADD_TO_PLAYLIST -> {
                    selectedMovie?.let { movie ->
                        showAddToPlaylistDialog(movie)
                    }
                }
            }
        }

        presenterSelector.addClassPresenter(
            DetailsOverviewRow::class.java,
            detailsPresenter
        )
        presenterSelector.addClassPresenter(
            ListRow::class.java,
            ListRowPresenter()
        )
    }

    private fun playMovie(movie: Movie) {
        if (movie.farsilandUrl.isEmpty()) {
            Log.e(TAG, "Cannot play movie: no URL for ${movie.title}")
            Toast.makeText(context, "Error: Movie URL not available", Toast.LENGTH_SHORT).show()
            return
        }

        Log.d(TAG, "Fetching video URLs for movie: ${movie.title}")
        Log.d(TAG, "Movie ID: ${movie.id}, URL: ${movie.farsilandUrl}")

        // Show loading dialog
        val progressDialog = ProgressDialog(requireContext()).apply {
            setMessage("Fetching video quality options...")
            setCancelable(false)
            show()
        }

        // Scrape video URLs in background
        lifecycleScope.launch {
            try {
                val scraperResult = VideoUrlScraper.extractVideoUrls(movie.farsilandUrl)

                progressDialog.dismiss()

                when (scraperResult) {
                    is ScraperResult.Success -> {
                        val videoUrls = scraperResult.data
                        Log.d(TAG, "Found ${videoUrls.size} quality options")

                        if (videoUrls.isEmpty()) {
                            Toast.makeText(context, "No video sources found", Toast.LENGTH_LONG).show()
                            return@launch
                        }

                        // If multiple qualities available, show selection dialog
                        if (videoUrls.size > 1) {
                            showQualitySelectionDialog(movie, videoUrls)
                        } else {
                            // Single quality - play directly
                            launchPlayer(movie, videoUrls[0])
                        }
                    }
                    is ScraperResult.NetworkError -> {
                        Toast.makeText(
                            context,
                            "Network error: ${scraperResult.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    is ScraperResult.ParseError -> {
                        Toast.makeText(
                            context,
                            "Failed to parse video data: ${scraperResult.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    is ScraperResult.NoDataFound -> {
                        Toast.makeText(
                            context,
                            "No video found: ${scraperResult.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            } catch (e: Exception) {
                progressDialog.dismiss()
                Log.e(TAG, "Error fetching video URLs", e)
                Toast.makeText(
                    context,
                    "Error: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    /**
     * Show quality selection dialog before playback
     */
    private fun showQualitySelectionDialog(movie: Movie, videoUrls: List<VideoUrl>) {
        val qualityNames = videoUrls.map { it.quality }.toTypedArray()

        AlertDialog.Builder(requireContext())
            .setTitle("Select Quality")
            .setItems(qualityNames) { dialog, which ->
                val selectedVideo = videoUrls[which]
                launchPlayer(movie, selectedVideo)
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /**
     * Launch video player with selected quality
     */
    private fun launchPlayer(movie: Movie, selectedVideo: VideoUrl) {
        try {
            Log.d(TAG, "Launching player with quality: ${selectedVideo.quality}")
            Log.d(TAG, "Video URL: ${selectedVideo.url}")

            val intent = Intent(requireContext(), VideoPlayerActivity::class.java).apply {
                putExtra("CONTENT_TYPE", "movie")
                putExtra("CONTENT_ID", movie.id)
                putExtra("CONTENT_TITLE", movie.title)
                putExtra("CONTENT_URL", movie.farsilandUrl)
                putExtra("CONTENT_POSTER_URL", movie.posterUrl)
                // Pass the selected video URL directly
                putExtra("SELECTED_VIDEO_URL", selectedVideo.url)
                putExtra("SELECTED_VIDEO_QUALITY", selectedVideo.quality)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error launching video player", e)
            Toast.makeText(
                context,
                "Error opening video player: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    /**
     * Show add to playlist dialog
     */
    private fun showAddToPlaylistDialog(movie: Movie) {
        Log.d(TAG, "Showing add to playlist dialog for: ${movie.title}")
        val dialog = AddToPlaylistDialogFragment.newInstanceForMovie(movie)
        dialog.show(childFragmentManager, "add_to_playlist")
    }

    private fun convertDpToPixel(context: android.content.Context, dp: Int): Int {
        val density = context.resources.displayMetrics.density
        return Math.round(dp.toFloat() * density)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Glide automatically clears when the view/fragment is destroyed
        // Just log the cleanup
        Log.d(TAG, "onDestroyView: Fragment cleaned up")
    }
}
