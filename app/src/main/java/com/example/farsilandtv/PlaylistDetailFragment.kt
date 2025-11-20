package com.example.farsilandtv
import coil.imageLoader
import coil.load

import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.leanback.app.DetailsSupportFragment
import androidx.leanback.app.DetailsSupportFragmentBackgroundController
import androidx.leanback.widget.*
import androidx.lifecycle.lifecycleScope


import com.example.farsilandtv.data.database.Playlist
import com.example.farsilandtv.data.database.PlaylistItem
import com.example.farsilandtv.data.models.Movie
import com.example.farsilandtv.data.models.Series
import com.example.farsilandtv.data.repository.ContentRepository
import com.example.farsilandtv.data.repository.PlaylistRepository
import com.example.farsilandtv.ui.GenreCardPresenter
import kotlinx.coroutines.launch

/**
 * Fragment for displaying playlist details and items
 * Shows playlist info and all content in the playlist
 */
class PlaylistDetailFragment : DetailsSupportFragment() {

    companion object {
        private const val TAG = "PlaylistDetailFragment"
        private const val ACTION_PLAY_ALL = 1L
        private const val ACTION_DELETE_PLAYLIST = 2L
    }

    private var playlistId: Long = -1
    private var playlist: Playlist? = null
    private val playlistRepo by lazy { PlaylistRepository(requireContext()) }
    private val contentRepo by lazy { ContentRepository(requireContext()) }

    private lateinit var detailsBackground: DetailsSupportFragmentBackgroundController
    private lateinit var presenterSelector: ClassPresenterSelector
    private lateinit var rowAdapter: ArrayObjectAdapter
    private lateinit var detailsRow: DetailsOverviewRow
    private lateinit var actionsAdapter: ArrayObjectAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.d(TAG, "onCreate PlaylistDetailFragment")

        detailsBackground = DetailsSupportFragmentBackgroundController(this)

        // Get playlist ID from activity
        playlistId = (activity as? PlaylistDetailActivity)?.getPlaylistId() ?: -1L

        if (playlistId == -1L) {
            Log.e(TAG, "No playlist ID provided")
            activity?.finish()
            return
        }

        presenterSelector = ClassPresenterSelector()
        rowAdapter = ArrayObjectAdapter(presenterSelector)

        setupDetailsOverviewRowPresenter()
        loadPlaylist()

        adapter = rowAdapter
    }

    private fun loadPlaylist() {
        lifecycleScope.launch {
            try {
                // Load playlist info
                playlistRepo.getPlaylist(playlistId).collect { pl ->
                    playlist = pl
                    pl?.let {
                        Log.d(TAG, "Loaded playlist: ${it.name}")
                        setupDetailsOverviewRow(it)
                        loadPlaylistItems(it)
                    } ?: run {
                        Log.e(TAG, "Playlist not found: $playlistId")
                        Toast.makeText(requireContext(), "Playlist not found", Toast.LENGTH_SHORT).show()
                        activity?.finish()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading playlist", e)
                Toast.makeText(requireContext(), getString(R.string.error_generic, e.message ?: "Unknown error"), Toast.LENGTH_LONG).show()
                activity?.finish()
            }
        }
    }

    private fun setupDetailsOverviewRow(playlist: Playlist) {
        detailsRow = DetailsOverviewRow(playlist)

        // Set default image
        detailsRow.imageDrawable = ContextCompat.getDrawable(
            requireContext(),
            R.drawable.default_background
        )

        // AUDIT FIX #11: Load cover image using ImageRequest (fixes K2 compiler crash + efficiency)
        // Issue: imageView.load with nested target{} lambda causes K2 compiler crash
        // Fix: Use ImageRequest.Builder with direct enqueue - more efficient, no dummy ImageView
        if (!playlist.coverImageUrl.isNullOrEmpty()) {
            val request = coil.request.ImageRequest.Builder(requireContext())
                .data(playlist.coverImageUrl)
                .size(512, 512)
                .crossfade(300)
                .target { drawable ->
                    // Update row image
                    detailsRow.imageDrawable = drawable

                    // Update background
                    val bitmap = (drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
                    if (bitmap != null) {
                        detailsBackground.coverBitmap = bitmap
                    }
                }
                .build()

            requireContext().imageLoader.enqueue(request)
        }

        // Setup actions
        actionsAdapter = ArrayObjectAdapter()
        actionsAdapter.add(Action(ACTION_PLAY_ALL, "▶️ Play All"))
        actionsAdapter.add(Action(ACTION_DELETE_PLAYLIST, "🗑️ Delete Playlist"))

        detailsRow.actionsAdapter = actionsAdapter

        // Clear and add details row
        rowAdapter.clear()
        rowAdapter.add(detailsRow)
    }

    private fun loadPlaylistItems(playlist: Playlist) {
        lifecycleScope.launch {
            try {
                playlistRepo.getPlaylistItems(playlistId).collect { items ->
                    Log.d(TAG, "Loaded ${items.size} items in playlist")

                    // Remove existing item rows (keep details row)
                    while (rowAdapter.size() > 1) {
                        rowAdapter.removeItems(1, 1)
                    }

                    if (items.isEmpty()) {
                        Log.d(TAG, "Playlist is empty")
                        // Could add empty state row here
                        return@collect
                    }

                    // Group items by type
                    val movies = items.filter { it.contentType == PlaylistItem.ContentType.MOVIE }
                    val series = items.filter { it.contentType == PlaylistItem.ContentType.SERIES }

                    // Add movies row
                    if (movies.isNotEmpty()) {
                        addMoviesRow(movies)
                    }

                    // Add series row
                    if (series.isNotEmpty()) {
                        addSeriesRow(series)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading playlist items", e)
                Toast.makeText(requireContext(), getString(R.string.error_loading_playlist_items, e.message ?: "Unknown error"), Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun addMoviesRow(items: List<PlaylistItem>) {
        lifecycleScope.launch {
            val cardPresenter = GenreCardPresenter(requireContext())
            val listRowAdapter = ArrayObjectAdapter(cardPresenter)

            items.forEach { item ->
                try {
                    val movieId = item.numericId
                    val result = contentRepo.getMovie(movieId)
                    result.onSuccess { movie ->
                        listRowAdapter.add(movie)
                    }.onFailure { error ->
                        Log.e(TAG, "Failed to load movie $movieId: ${error.message}")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error loading movie ${item.contentId}", e)
                }
            }

            if (listRowAdapter.size() > 0) {
                val header = HeaderItem(rowAdapter.size().toLong(), "Movies")
                rowAdapter.add(ListRow(header, listRowAdapter))
            }
        }
    }

    private fun addSeriesRow(items: List<PlaylistItem>) {
        lifecycleScope.launch {
            val cardPresenter = GenreCardPresenter(requireContext())
            val listRowAdapter = ArrayObjectAdapter(cardPresenter)
            val repository = ContentRepository(requireContext())

            items.forEach { item ->
                try {
                    val seriesId = item.numericId
                    // P3 FIX: Issue #15 - Load series from database using new getSeries method
                    val result = repository.getSeries(seriesId)
                    result.onSuccess { series ->
                        listRowAdapter.add(series)
                        Log.d(TAG, "Loaded series: ${series.title}")
                    }.onFailure { e ->
                        Log.e(TAG, "Failed to load series $seriesId: ${e.message}")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error loading series ${item.contentId}", e)
                }
            }

            if (listRowAdapter.size() > 0) {
                val header = HeaderItem(rowAdapter.size().toLong(), "TV Series")
                rowAdapter.add(ListRow(header, listRowAdapter))
            }
        }
    }

    private fun setupDetailsOverviewRowPresenter() {
        val detailsPresenter = FullWidthDetailsOverviewRowPresenter(
            PlaylistDescriptionPresenter()
        )

        detailsPresenter.backgroundColor = ContextCompat.getColor(
            requireContext(),
            R.color.selected_background
        )

        detailsPresenter.initialState = FullWidthDetailsOverviewRowPresenter.STATE_SMALL
        detailsPresenter.isParticipatingEntranceTransition = false

        detailsPresenter.onActionClickedListener = OnActionClickedListener { action ->
            when (action.id) {
                ACTION_PLAY_ALL -> {
                    Toast.makeText(requireContext(), "Play All not implemented yet", Toast.LENGTH_SHORT).show()
                }
                ACTION_DELETE_PLAYLIST -> {
                    deletePlaylist()
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

        // Add item click listener for content cards
        onItemViewClickedListener = ItemViewClickedListener()
    }

    private fun deletePlaylist() {
        lifecycleScope.launch {
            try {
                playlist?.let {
                    playlistRepo.deletePlaylist(it)
                    Toast.makeText(requireContext(), "Playlist deleted", Toast.LENGTH_SHORT).show()
                    activity?.finish()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting playlist", e)
                Toast.makeText(requireContext(), "Error deleting playlist: ${e.message}", Toast.LENGTH_LONG).show()
            }
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
                is Movie -> {
                    Log.d(TAG, "Movie clicked: ${item.title}")
                    showMovieDetails(item)
                }
                is Series -> {
                    Log.d(TAG, "Series clicked: ${item.title}")
                    openSeriesDetails(item)
                }
            }
        }
    }

    private fun showMovieDetails(movie: Movie) {
        try {
            val intent = Intent(requireContext(), DetailsActivity::class.java).apply {
                putExtra(DetailsActivity.EXTRA_MOVIE, movie)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error opening movie details", e)
            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun openSeriesDetails(series: Series) {
        try {
            val intent = Intent(requireContext(), SeriesDetailsActivity::class.java).apply {
                putExtra(SeriesDetailsActivity.EXTRA_SERIES, series)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error opening series details", e)
            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Description presenter for playlist details
     */
    private inner class PlaylistDescriptionPresenter : AbstractDetailsDescriptionPresenter() {
        override fun onBindDescription(vh: ViewHolder, item: Any) {
            val playlist = item as? Playlist ?: return

            vh.title.text = playlist.name
            vh.subtitle.text = playlist.description ?: "No description"

            // Show creation date
            val createdDate = java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.US)
                .format(java.util.Date(playlist.createdAt))
            vh.body.text = "Created: $createdDate"
        }
    }
}
