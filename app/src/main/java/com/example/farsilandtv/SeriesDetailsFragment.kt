package com.example.farsilandtv
import coil.load
import coil.request.ImageRequest
import coil.imageLoader
import android.graphics.drawable.BitmapDrawable

import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.leanback.app.DetailsSupportFragment
import androidx.leanback.app.DetailsSupportFragmentBackgroundController
import androidx.leanback.widget.*

// Glide imports for legacy Leanback fragments

import com.example.farsilandtv.data.models.Episode
import com.example.farsilandtv.data.models.Series
import com.example.farsilandtv.ui.GenreCardPresenter
import com.example.farsilandtv.data.repository.WatchlistRepository
import com.example.farsilandtv.data.repository.FavoritesRepository
import com.example.farsilandtv.data.repository.PlaybackRepository
import com.example.farsilandtv.data.database.Favorite
import com.example.farsilandtv.utils.EpisodeFormatter
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first

/**
 * Fragment to display series details with hero section
 * Shows metadata, action buttons, and episodes grouped by season
 */
class SeriesDetailsFragment : DetailsSupportFragment() {

    companion object {
        private const val TAG = "SeriesDetailsFragment"
        const val ARG_SERIES = "series"
        const val ARG_EPISODES_BY_SEASON = "episodes_by_season"

        private const val ACTION_PLAY_NEXT = 1L
        private const val ACTION_MONITOR = 2L
        private const val ACTION_MARK_WATCHED = 3L
        private const val ACTION_FAVORITE = 4L
        private const val ACTION_ADD_TO_PLAYLIST = 5L
    }

    private var series: Series? = null
    private var episodesBySeason: Map<Int, List<Episode>>? = null
    private lateinit var detailsBackground: DetailsSupportFragmentBackgroundController
    private lateinit var presenterSelector: ClassPresenterSelector
    private lateinit var rowAdapter: ArrayObjectAdapter
    private val watchlistRepo by lazy { WatchlistRepository(requireContext()) }
    private val favoritesRepo by lazy { FavoritesRepository(requireContext()) }
    private val playbackRepo by lazy { PlaybackRepository(requireContext()) }
    private var isMonitored = false
    private var isFavorited = false
    private lateinit var detailsRow: DetailsOverviewRow
    private lateinit var actionsAdapter: ArrayObjectAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.d(TAG, "onCreate SeriesDetailsFragment")

        detailsBackground = DetailsSupportFragmentBackgroundController(this)

        // Get series from activity (passed via getSeriesData method)
        (activity as? SeriesDetailsActivity)?.let { seriesActivity ->
            series = seriesActivity.getSeriesData()
            episodesBySeason = seriesActivity.getEpisodesData()
        }

        if (series != null && episodesBySeason != null) {
            presenterSelector = ClassPresenterSelector()
            rowAdapter = ArrayObjectAdapter(presenterSelector)

            setupDetailsOverviewRow()
            setupDetailsOverviewRowPresenter()

            adapter = rowAdapter
            initializeBackground(series)

        } else {
            Log.e(TAG, "No series data provided")
            activity?.finish()
        }
    }

    private fun initializeBackground(series: Series?) {
        detailsBackground.enableParallax()

        val backgroundUrl = series?.backdropUrl ?: series?.posterUrl

        val request = ImageRequest.Builder(requireContext())
            .data(backgroundUrl)
            .target { drawable ->
                val bitmap = (drawable as? BitmapDrawable)?.bitmap
                if (bitmap != null) {
                    detailsBackground.coverBitmap = bitmap
                    rowAdapter.notifyArrayItemRangeChanged(0, rowAdapter.size())
                }
            }
            .error(R.drawable.default_background)
            .build()
        
        requireContext().imageLoader.enqueue(request)
    }

    private fun setupDetailsOverviewRow() {
        Log.d(TAG, "Setting up details for: ${series?.title}")

        detailsRow = DetailsOverviewRow(series)

        // Load all button states first
        lifecycleScope.launch {
            actionsAdapter = ArrayObjectAdapter()
            actionsAdapter.add(Action(ACTION_PLAY_NEXT, "▶️ Play First Episode"))

            series?.let { s ->
                // Check monitor status
                isMonitored = watchlistRepo.isSeriesMonitored(s.id)
                val monitorText = if (isMonitored) "✓ Monitored" else "➕ Monitor Series"
                actionsAdapter.add(Action(ACTION_MONITOR, monitorText))

                // Check favorite status
                isFavorited = favoritesRepo.isSeriesFavorited(s.id).first()
                val favoriteText = if (isFavorited) "❤️ Favorited" else "🤍 Add to Favorites"
                actionsAdapter.add(Action(ACTION_FAVORITE, favoriteText))

                // Mark all watched action
                actionsAdapter.add(Action(ACTION_MARK_WATCHED, "✓ Mark All Watched"))

                // Add to Playlist action
                actionsAdapter.add(Action(ACTION_ADD_TO_PLAYLIST, "➕ Add to Playlist"))
            }

            detailsRow.actionsAdapter = actionsAdapter
            rowAdapter.add(detailsRow)  // Only add after complete

            // Move episode loading here to prevent UI flicker
            setupEpisodeRows()
        }
    }

    /**
     * Update the monitor button text based on current status
     */
    private fun updateMonitorButton() {
        val monitorText = if (isMonitored) {
            "✓ Monitored"
        } else {
            "➕ Monitor Series"
        }
        updateActionButton(ACTION_MONITOR, monitorText)
    }

    /**
     * Update the favorite button text based on current status
     */
    private fun updateFavoriteButton() {
        val favoriteText = if (isFavorited) {
            "❤️ Favorited"
        } else {
            "🤍 Add to Favorites"
        }
        updateActionButton(ACTION_FAVORITE, favoriteText)
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
        val detailsPresenter = FullWidthDetailsOverviewRowPresenter(
            DetailsDescriptionPresenter()
        )

        detailsPresenter.backgroundColor = ContextCompat.getColor(
            requireContext(),
            R.color.selected_background
        )

        // Make the overview more compact
        detailsPresenter.initialState = FullWidthDetailsOverviewRowPresenter.STATE_SMALL

        // Align to start to minimize vertical space
        detailsPresenter.setAlignmentMode(FullWidthDetailsOverviewRowPresenter.ALIGN_MODE_START)

        // Don't show logo/image area to save space
        detailsPresenter.isParticipatingEntranceTransition = false

        detailsPresenter.onActionClickedListener = OnActionClickedListener { action ->
            when (action.id) {
                ACTION_PLAY_NEXT -> {
                    playFirstEpisode()
                }
                ACTION_MONITOR -> {
                    series?.let { s ->
                        lifecycleScope.launch {
                            if (isMonitored) {
                                watchlistRepo.removeSeriesFromMonitored(s.id)
                                isMonitored = false
                                Toast.makeText(
                                    requireContext(),
                                    "Removed from monitoring",
                                    Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                watchlistRepo.addSeriesToMonitored(s)
                                isMonitored = true
                                Toast.makeText(
                                    requireContext(),
                                    "Series is now monitored",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            updateMonitorButton()
                        }
                    }
                }
                ACTION_FAVORITE -> {
                    series?.let { s ->
                        lifecycleScope.launch {
                            if (isFavorited) {
                                favoritesRepo.removeSeriesFromFavorites(s.id)
                                isFavorited = false
                                Toast.makeText(
                                    requireContext(),
                                    "Removed from favorites",
                                    Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                favoritesRepo.addSeriesToFavorites(s)
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
                ACTION_MARK_WATCHED -> {
                    series?.let { s ->
                        lifecycleScope.launch {
                            // Mark all episodes as watched
                            watchlistRepo.markAllEpisodesAsWatched(s.id)

                            val allEpisodes = episodesBySeason?.values?.flatten() ?: emptyList()
                            Toast.makeText(
                                requireContext(),
                                "Marked ${allEpisodes.size} episodes as watched",
                                Toast.LENGTH_SHORT
                                ).show()
                        }
                    }
                }
                ACTION_ADD_TO_PLAYLIST -> {
                    series?.let { s ->
                        showAddToPlaylistDialog(s)
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

    private fun setupEpisodeRows() {
        episodesBySeason?.let { episodes ->
            val sortedSeasons = episodes.keys.sorted()

            // Check if all episodes have the same thumbnail (generic series image)
            val allEpisodes = episodes.values.flatten()
            val allThumbnails = allEpisodes.mapNotNull { it.thumbnailUrl }.distinct()
            val hasGenericThumbnails = allThumbnails.size == 1 // All episodes share one image

            Log.d(TAG, "All episodes: ${allEpisodes.size}, distinct thumbnails: ${allThumbnails.size}")
            if (hasGenericThumbnails) {
                Log.d(TAG, "Detected generic thumbnail used for all episodes: ${allThumbnails.firstOrNull()}")
            }

            for (season in sortedSeasons) {
                val seasonEpisodes = episodes[season] ?: continue

                val cardPresenter = GenreCardPresenter(requireContext())
                val listRowAdapter = ArrayObjectAdapter(cardPresenter)

                // Sort by air date if available, otherwise by episode number
                val sortedEpisodes = if (seasonEpisodes.any { !it.airDate.isNullOrEmpty() }) {
                    // Sort by air date (newest first), then by episode number as fallback
                    seasonEpisodes.sortedWith(compareByDescending<Episode> {
                        it.airDate?.takeIf { date -> date.isNotEmpty() }
                    }.thenBy { it.episode })
                } else {
                    // Default to episode number sorting
                    seasonEpisodes.sortedBy { it.episode }
                }

                for (episode in sortedEpisodes) {
                    // If all episodes have the same generic thumbnail, use series poster instead
                    val episodeWithPoster = if (hasGenericThumbnails) {
                        episode.copy(
                            thumbnailUrl = series?.posterUrl,
                            episodePosterUrl = null
                        )
                    } else {
                        episode
                    }
                    listRowAdapter.add(episodeWithPoster)
                }

                val header = HeaderItem(season.toLong(), EpisodeFormatter.formatSeasonNumber(season))
                rowAdapter.add(ListRow(header, listRowAdapter))
            }

            onItemViewClickedListener = ItemViewClickedListener()
        }
    }

    private fun playFirstEpisode() {
        val firstSeason = episodesBySeason?.keys?.minOrNull()
        val firstEpisode = firstSeason?.let { episodesBySeason?.get(it)?.minByOrNull { it.episode } }

        if (firstEpisode != null) {
            playEpisode(firstEpisode)
        } else {
            Toast.makeText(requireContext(), "No episodes available", Toast.LENGTH_SHORT).show()
        }
    }

    private inner class ItemViewClickedListener : OnItemViewClickedListener {
        override fun onItemClicked(
            itemViewHolder: Presenter.ViewHolder,
            item: Any,
            rowViewHolder: RowPresenter.ViewHolder,
            row: Row
        ) {
            when (item) {
                is Episode -> {
                    Log.d(TAG, "Episode clicked: ${item.formattedNumber} - ${item.title}")
                    playEpisode(item)
                }
            }
        }
    }

    /**
     * Play an episode
     */
    private fun playEpisode(episode: Episode) {
        if (episode.farsilandUrl.isEmpty()) {
            Log.e(TAG, "Cannot play episode: no URL for ${episode.formattedNumber}")
            Toast.makeText(context, "Error: Episode URL not available", Toast.LENGTH_SHORT).show()
            return
        }

        Log.d(TAG, "Launching player for episode: ${episode.formattedNumber}")
        Log.d(TAG, "Episode ID: ${episode.id}, URL: ${episode.farsilandUrl}")

        try {
            val intent = Intent(requireContext(), VideoPlayerActivity::class.java).apply {
                putExtra("CONTENT_TYPE", "episode")
                putExtra("CONTENT_ID", episode.id)
                putExtra("CONTENT_TITLE", "${episode.formattedNumber}: ${episode.title}")
                putExtra("CONTENT_URL", episode.farsilandUrl)
                putExtra("SERIES_ID", series?.id ?: 0)
                putExtra("EPISODE_SEASON", episode.season)
                putExtra("EPISODE_NUMBER", episode.episode)
                putExtra("CONTENT_POSTER_URL", episode.thumbnailUrl)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error launching video player", e)
            Toast.makeText(context, "Error opening video player: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Show add to playlist dialog
     */
    private fun showAddToPlaylistDialog(series: Series) {
        Log.d(TAG, "Showing add to playlist dialog for: ${series.title}")
        val dialog = AddToPlaylistDialogFragment.newInstanceForSeries(series)
        dialog.show(childFragmentManager, "add_to_playlist")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Glide automatically clears when the view/fragment is destroyed
        // Just log the cleanup
        Log.d(TAG, "onDestroyView: Fragment cleaned up")
    }
}
