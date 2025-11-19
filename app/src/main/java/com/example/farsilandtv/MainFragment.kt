package com.example.farsilandtv
import coil.load

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityOptionsCompat
import androidx.core.content.ContextCompat
import androidx.leanback.app.BackgroundManager
import androidx.leanback.app.BrowseSupportFragment
import androidx.leanback.widget.*
import androidx.lifecycle.ViewModelProvider
// Glide removed - using Coil via ImageLoader
import androidx.lifecycle.asLiveData
import androidx.leanback.app.GuidedStepSupportFragment
import com.example.farsilandtv.data.database.ContinueWatchingItem
import com.example.farsilandtv.data.database.ContentDatabase
import com.example.farsilandtv.data.database.DatabaseSource
import com.example.farsilandtv.data.models.Movie
import com.example.farsilandtv.data.models.Series
import com.example.farsilandtv.data.repository.WatchlistRepository
import com.example.farsilandtv.ui.GenreCardPresenter
import com.example.farsilandtv.ui.viewmodel.MainViewModel
import java.util.*

/**
 * Main browse fragment - displays movies and TV shows from Farsiland.com
 */
class MainFragment : BrowseSupportFragment() {

    private val mHandler = Handler(Looper.myLooper()!!)
    private lateinit var mBackgroundManager: BackgroundManager
    private var mDefaultBackground: Drawable? = null
    private lateinit var mMetrics: DisplayMetrics
    private var mBackgroundTimer: Timer? = null
    private var mBackgroundUri: String? = null

    private lateinit var viewModel: MainViewModel
    private lateinit var rowsAdapter: ArrayObjectAdapter
    private val watchlistRepo by lazy { WatchlistRepository(requireContext()) }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        Log.i(TAG, "onCreate")
        super.onActivityCreated(savedInstanceState)

        // Initialize ViewModel
        viewModel = ViewModelProvider(this)[MainViewModel::class.java]

        prepareBackgroundManager()
        setupUIElements()
        setupRowsAdapter()
        setupEventListeners()
        observeViewModel()
        observeContinueWatching()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy: $mBackgroundTimer")
        mBackgroundTimer?.cancel()
    }

    private fun prepareBackgroundManager() {
        mBackgroundManager = BackgroundManager.getInstance(activity)
        // Only attach if not already attached (prevents crash when navigating back)
        if (!mBackgroundManager.isAttached) {
            mBackgroundManager.attach(requireActivity().window)
        }
        mDefaultBackground = ContextCompat.getDrawable(requireContext(), R.drawable.default_background)
        mMetrics = DisplayMetrics()

        // Use proper API based on Android version
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            // API 30+ (Android 11+): Use WindowMetrics
            val windowMetrics = requireActivity().windowManager.currentWindowMetrics
            val bounds = windowMetrics.bounds
            mMetrics.widthPixels = bounds.width()
            mMetrics.heightPixels = bounds.height()
        } else {
            // API 28-29: Use deprecated getMetrics()
            @Suppress("DEPRECATION")
            requireActivity().windowManager.defaultDisplay.getMetrics(mMetrics)
        }
    }

    private fun setupUIElements() {
        title = getString(R.string.browse_title)
        headersState = HEADERS_ENABLED
        isHeadersTransitionOnBackEnabled = true

        // Set fastLane (headers) background color
        brandColor = ContextCompat.getColor(requireContext(), R.color.fastlane_background)
        searchAffordanceColor = ContextCompat.getColor(requireContext(), R.color.search_opaque)
    }

    private fun setupRowsAdapter() {
        rowsAdapter = ArrayObjectAdapter(ListRowPresenter())
        adapter = rowsAdapter
    }

    private fun observeContinueWatching() {
        // Observe continue watching items
        watchlistRepo.getContinueWatching().asLiveData().observe(viewLifecycleOwner) { items ->
            if (items.isNotEmpty()) {
                Log.d(TAG, "Loaded ${items.size} continue watching items")
                // Remove existing continue watching row if any
                removeContinueWatchingRow()
                // Add new continue watching row at top
                addContinueWatchingRow(items)
            }
        }
    }

    private fun removeContinueWatchingRow() {
        // Find and remove continue watching row
        for (i in 0 until rowsAdapter.size()) {
            val item = rowsAdapter.get(i)
            if (item is ListRow && item.headerItem?.name == "Continue Watching") {
                rowsAdapter.removeItems(i, 1)
                break
            }
        }
    }

    private fun observeViewModel() {
        // Add database switcher row at the very top (first thing user sees)
        addDatabaseSwitcherRow()

        // Observe loading state
        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            if (isLoading) {
                Log.d(TAG, "Loading content from Farsiland.com...")
            }
        }

        // Observe errors
        viewModel.error.observe(viewLifecycleOwner) { error ->
            error?.let {
                Log.e(TAG, "Error: $it")
                Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            }
        }

        // Observe recent movies
        viewModel.recentMovies.observe(viewLifecycleOwner) { movies ->
            if (movies.isNotEmpty()) {
                Log.d(TAG, "Loaded ${movies.size} movies")
                addMoviesRow("Recently Added Movies", movies)
            }
        }

        // Observe recent series
        viewModel.recentSeries.observe(viewLifecycleOwner) { series ->
            if (series.isNotEmpty()) {
                Log.d(TAG, "Loaded ${series.size} TV series")
                addSeriesRow("Recently Added TV Shows", series)
            }
        }

        // Observe recent episodes
        viewModel.recentEpisodes.observe(viewLifecycleOwner) { episodes ->
            if (episodes.isNotEmpty()) {
                Log.d(TAG, "Loaded ${episodes.size} episodes")
                addEpisodesRow("Recently Released Episodes", episodes)

                // Add options row right after episodes
                addOptionsRow()
            }
        }

        // Observe genres
        viewModel.genres.observe(viewLifecycleOwner) { genres ->
            if (genres.isNotEmpty()) {
                Log.d(TAG, "Loaded ${genres.size} genres")
                addGenreRows(genres.take(5)) // Add first 5 genres as rows
            }
        }
    }

    /**
     * Add movies row to the adapter
     */
    private fun addMoviesRow(title: String, movies: List<Movie>) {
        val cardPresenter = GenreCardPresenter(requireContext())
        val listRowAdapter = ArrayObjectAdapter(cardPresenter)

        for (movie in movies) {
            listRowAdapter.add(movie)
        }

        val header = HeaderItem(rowsAdapter.size().toLong(), title)
        rowsAdapter.add(ListRow(header, listRowAdapter))
    }

    /**
     * Add TV series row to the adapter
     */
    private fun addSeriesRow(title: String, series: List<Series>) {
        val cardPresenter = GenreCardPresenter(requireContext())
        val listRowAdapter = ArrayObjectAdapter(cardPresenter)

        for (show in series) {
            listRowAdapter.add(show)
        }

        val header = HeaderItem(rowsAdapter.size().toLong(), title)
        rowsAdapter.add(ListRow(header, listRowAdapter))
    }

    /**
     * Add continue watching row to the adapter
     */
    private fun addContinueWatchingRow(items: List<ContinueWatchingItem>) {
        val presenter = ContinueWatchingPresenter()
        val listRowAdapter = ArrayObjectAdapter(presenter)

        for (item in items) {
            listRowAdapter.add(item)
        }

        val header = HeaderItem(0, "Continue Watching")
        rowsAdapter.add(0, ListRow(header, listRowAdapter))
    }

    /**
     * Add episodes row to the adapter
     */
    private fun addEpisodesRow(title: String, episodes: List<com.example.farsilandtv.data.models.Episode>) {
        val cardPresenter = GenreCardPresenter(requireContext())
        val listRowAdapter = ArrayObjectAdapter(cardPresenter)

        for (episode in episodes) {
            listRowAdapter.add(episode)
        }

        val header = HeaderItem(rowsAdapter.size().toLong(), title)
        rowsAdapter.add(ListRow(header, listRowAdapter))
    }

    /**
     * Add genre-based rows
     */
    private fun addGenreRows(genres: List<com.example.farsilandtv.data.models.Genre>) {
        for (genre in genres) {
            // Load movies for this genre
            viewModel.loadMoviesByGenre(genre.id).observe(viewLifecycleOwner) { movies ->
                if (movies.isNotEmpty()) {
                    addMoviesRow("${genre.name} Movies", movies)
                }
            }

            // Load series for this genre
            viewModel.loadSeriesByGenre(genre.id).observe(viewLifecycleOwner) { series ->
                if (series.isNotEmpty()) {
                    addSeriesRow("${genre.name} Series", series)
                }
            }
        }
    }

    /**
     * Add database switcher row at the top of sidebar
     */
    private fun addDatabaseSwitcherRow() {
        val currentSource = ContentDatabase.getCurrentSource(requireContext())
        val switchHeader = HeaderItem(0L, "DATABASE SOURCE")
        val gridPresenter = GridItemPresenter()
        val gridRowAdapter = ArrayObjectAdapter(gridPresenter)

        // Single toggle button showing current source
        gridRowAdapter.add("🌐 ${currentSource.displayName} (tap to switch)")

        rowsAdapter.add(0, ListRow(switchHeader, gridRowAdapter))
    }

    /**
     * Add options/settings row at the bottom
     */
    private fun addOptionsRow() {
        // Check if options row already exists
        val hasOptionsRow = (0 until rowsAdapter.size()).any { index ->
            val item = rowsAdapter.get(index)
            item is ListRow && (item.headerItem?.name == "OPTIONS")
        }

        if (!hasOptionsRow) {
            val gridHeader = HeaderItem(rowsAdapter.size().toLong(), "OPTIONS")
            val gridPresenter = GridItemPresenter()
            val gridRowAdapter = ArrayObjectAdapter(gridPresenter)

            // Add favorites option
            gridRowAdapter.add("❤️ My Favorites")

            // Add playlists option
            gridRowAdapter.add("📋 My Playlists")

            // Add refresh option
            gridRowAdapter.add("🔄 Refresh Content")

            rowsAdapter.add(ListRow(gridHeader, gridRowAdapter))
        }
    }

    private fun setupEventListeners() {
        setOnSearchClickedListener {
            val intent = Intent(requireContext(), SearchActivity::class.java)
            startActivity(intent)
        }

        onItemViewClickedListener = ItemViewClickedListener()
        onItemViewSelectedListener = ItemViewSelectedListener()

        // Add custom key handler for refresh (R key or Menu button)
        view?.isFocusableInTouchMode = true
        view?.requestFocus()
        view?.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN) {
                when (keyCode) {
                    KeyEvent.KEYCODE_R, KeyEvent.KEYCODE_MENU -> {
                        refreshContent()
                        true
                    }
                    else -> false
                }
            } else {
                false
            }
        }
    }

    /**
     * Force refresh content from server (bypasses cache)
     * Triggers appropriate sync worker based on current database source
     */
    private fun refreshContent() {
        Log.d(TAG, "Force refresh triggered")

        // Get current database source
        val currentSource = ContentDatabase.getCurrentSource(requireContext())
        Log.d(TAG, "Triggering sync for ${currentSource.displayName}")

        Toast.makeText(requireContext(), "Refreshing ${currentSource.displayName} content...", Toast.LENGTH_SHORT).show()

        // Clear all existing rows
        rowsAdapter.clear()

        // Trigger force refresh in ViewModel
        viewModel.forceRefresh()

        // Trigger appropriate sync worker
        triggerSyncWorker(currentSource)
    }

    /**
     * Trigger the appropriate sync worker based on database source
     */
    private fun triggerSyncWorker(source: DatabaseSource) {
        val syncRequest = when (source) {
            DatabaseSource.FARSILAND -> {
                androidx.work.OneTimeWorkRequestBuilder<com.example.farsilandtv.data.sync.ContentSyncWorker>().build()
            }
            DatabaseSource.FARSIPLEX -> {
                androidx.work.OneTimeWorkRequestBuilder<com.example.farsilandtv.data.sync.FarsiPlexSyncWorker>().build()
            }
            DatabaseSource.NAMAKADE -> {
                // Namakade doesn't have a sync worker yet (no API/sitemap available)
                // Just show message and return
                Toast.makeText(requireContext(), "Namakade content is static (no sync available)", Toast.LENGTH_LONG).show()
                return
            }
        }

        androidx.work.WorkManager.getInstance(requireContext()).enqueue(syncRequest)
        Log.i(TAG, "Triggered ${source.displayName} sync worker")
    }

    /**
     * Open Favorites screen
     */
    private fun openFavorites() {
        Log.d(TAG, "Opening Favorites")
        try {
            val intent = Intent(requireContext(), FavoritesActivity::class.java)
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error opening favorites", e)
            Toast.makeText(context, "Error opening favorites: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Open Playlists screen
     */
    private fun openPlaylists() {
        Log.d(TAG, "Opening Playlists")
        try {
            val intent = Intent(requireContext(), PlaylistsActivity::class.java)
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error opening playlists", e)
            Toast.makeText(context, "Error opening playlists: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Toggle database source (quick switch without dialog)
     * Cycles through: FARSILAND → FARSIPLEX → NAMAKADE → FARSILAND
     */
    private fun toggleDatabaseSource() {
        Log.d(TAG, "Toggling database source")
        try {
            val currentSource = ContentDatabase.getCurrentSource(requireContext())

            // Cycle to the next source
            val newSource = when (currentSource) {
                DatabaseSource.FARSILAND -> DatabaseSource.FARSIPLEX
                DatabaseSource.FARSIPLEX -> DatabaseSource.NAMAKADE
                DatabaseSource.NAMAKADE -> DatabaseSource.FARSILAND
            }

            // Switch database
            val switched = ContentDatabase.switchDatabaseSource(requireContext(), newSource)

            if (switched) {
                Toast.makeText(
                    requireContext(),
                    "Switching to ${newSource.displayName}...",
                    Toast.LENGTH_SHORT
                ).show()

                // Reload activity to show new content
                requireActivity().recreate()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error toggling database", e)
            Toast.makeText(context, "Error switching database: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Show movie details
     */
    private fun showMovieDetails(movie: Movie) {
        Log.d(TAG, "Opening details for movie: ${movie.title}")

        try {
            val intent = Intent(requireContext(), DetailsActivity::class.java).apply {
                putExtra(DetailsActivity.EXTRA_MOVIE, movie)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error opening movie details", e)
            Toast.makeText(context, "Error opening details: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Play an episode
     */
    private fun playEpisode(episode: com.example.farsilandtv.data.models.Episode) {
        // Validate episode URL
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
     * Open series details to show episodes
     */
    private fun openSeriesDetails(series: Series) {
        if (series.farsilandUrl.isEmpty()) {
            Log.e(TAG, "Cannot open series: no URL for ${series.title}")
            Toast.makeText(context, "Error: Series URL not available", Toast.LENGTH_SHORT).show()
            return
        }

        Log.d(TAG, "Opening series details: ${series.title}")
        Log.d(TAG, "Series ID: ${series.id}, URL: ${series.farsilandUrl}")

        try {
            val intent = Intent(requireContext(), SeriesDetailsActivity::class.java).apply {
                putExtra(SeriesDetailsActivity.EXTRA_SERIES, series)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error opening series details", e)
            Toast.makeText(context, "Error opening series: ${e.message}", Toast.LENGTH_LONG).show()
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
                is ContinueWatchingItem -> {
                    Log.d(TAG, "Continue watching item clicked: ${item.title}")
                    resumePlayback(item)
                }
                is Movie -> {
                    Log.d(TAG, "Movie clicked: ${item.title}")
                    showMovieDetails(item)
                }
                is Series -> {
                    Log.d(TAG, "Series clicked: ${item.title}")
                    openSeriesDetails(item)
                }
                is com.example.farsilandtv.data.models.Episode -> {
                    Log.d(TAG, "Episode clicked: ${item.formattedNumber} - ${item.title}")
                    playEpisode(item)
                }
                is String -> {
                    // Handle database switcher (top row)
                    if (item.contains("(tap to switch)")) {
                        toggleDatabaseSource()
                    }
                    // Handle options row clicks
                    else if (item.contains("Refresh")) {
                        refreshContent()
                    } else if (item.contains("Favorites")) {
                        openFavorites()
                    } else if (item.contains("Playlists")) {
                        openPlaylists()
                    }
                }
            }
        }
    }

    /**
     * Resume playback from continue watching
     */
    private fun resumePlayback(item: ContinueWatchingItem) {
        try {
            val intent = Intent(requireContext(), VideoPlayerActivity::class.java).apply {
                when (item.contentType) {
                    ContinueWatchingItem.ContentType.MOVIE -> {
                        putExtra("CONTENT_TYPE", "movie")
                        putExtra("CONTENT_ID", item.id.removePrefix("movie-").toIntOrNull() ?: 0)
                        putExtra("CONTENT_TITLE", item.title)
                        putExtra("CONTENT_URL", item.farsilandUrl)
                        putExtra("CONTENT_POSTER_URL", item.posterUrl)
                    }
                    ContinueWatchingItem.ContentType.EPISODE -> {
                        putExtra("CONTENT_TYPE", "episode")
                        putExtra("CONTENT_ID", item.episodeId ?: 0)
                        putExtra("CONTENT_TITLE", "${item.subtitle}: ${item.title}")
                        putExtra("CONTENT_URL", item.farsilandUrl)
                        putExtra("SERIES_ID", item.seriesId ?: 0)
                        putExtra("EPISODE_SEASON", item.season ?: 0)
                        putExtra("EPISODE_NUMBER", item.episodeNumber ?: 0)
                        putExtra("CONTENT_POSTER_URL", item.posterUrl)
                    }
                }
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error resuming playback", e)
            Toast.makeText(context, "Error opening player: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private inner class ItemViewSelectedListener : OnItemViewSelectedListener {
        override fun onItemSelected(
            itemViewHolder: Presenter.ViewHolder?,
            item: Any?,
            rowViewHolder: RowPresenter.ViewHolder,
            row: Row
        ) {
            // Background updates disabled per user request
            // (previously changed background to focused item's poster/backdrop)
        }
    }

    private fun updateBackground(uri: String?) {
        val width = mMetrics.widthPixels
        val height = mMetrics.heightPixels

        // Load background using Coil
        val imageView = android.widget.ImageView(requireContext())
        imageView.load(uri) {
            size(mMetrics.widthPixels, mMetrics.heightPixels)
            error(mDefaultBackground)
            target(
                onSuccess = { drawable ->
                    mBackgroundManager.drawable = drawable
                }
            )
        }
        mBackgroundTimer?.cancel()
    }

    private fun startBackgroundTimer() {
        mBackgroundTimer?.cancel()
        mBackgroundTimer = Timer()
        mBackgroundTimer?.schedule(UpdateBackgroundTask(), BACKGROUND_UPDATE_DELAY.toLong())
    }

    private inner class UpdateBackgroundTask : TimerTask() {
        override fun run() {
            mHandler.post { updateBackground(mBackgroundUri) }
        }
    }

    /**
     * Presenter for options grid items
     */
    private inner class GridItemPresenter : Presenter() {
        override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
            val view = TextView(parent.context)
            view.layoutParams = ViewGroup.LayoutParams(GRID_ITEM_WIDTH, GRID_ITEM_HEIGHT)
            view.isFocusable = true
            view.isFocusableInTouchMode = true
            view.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.default_background))
            view.setTextColor(Color.WHITE)
            view.gravity = Gravity.CENTER
            view.textSize = 16f
            return ViewHolder(view)
        }

        override fun onBindViewHolder(viewHolder: ViewHolder, item: Any) {
            val textView = viewHolder.view as? TextView ?: return
            val text = item as? String ?: return
            textView.text = text
        }

        override fun onUnbindViewHolder(viewHolder: ViewHolder) {}
    }

    /**
     * Presenter for Continue Watching items with progress bar
     */
    private inner class ContinueWatchingPresenter : Presenter() {
        override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
            val cardView = ImageCardView(parent.context).apply {
                isFocusable = true
                isFocusableInTouchMode = true
                setMainImageDimensions(454, 255)  // 45% bigger (313*1.45, 176*1.45)
            }
            return ViewHolder(cardView)
        }

        override fun onBindViewHolder(viewHolder: ViewHolder, item: Any) {
            val continueItem = item as ContinueWatchingItem
            val cardView = viewHolder.view as ImageCardView

            // Set title
            cardView.titleText = if (continueItem.contentType == ContinueWatchingItem.ContentType.EPISODE) {
                "${continueItem.subtitle} - ${continueItem.title}"
            } else {
                continueItem.title
            }

            // Set content text with progress
            cardView.contentText = "${continueItem.progressPercentage}% watched"

            // Load image
            if (!continueItem.posterUrl.isNullOrEmpty()) {
                cardView.mainImageView.load(continueItem.posterUrl) {
                    crossfade(300)
                    placeholder(R.drawable.image_placeholder)
                    error(R.drawable.movie)
                }
            }
        }

        override fun onUnbindViewHolder(viewHolder: ViewHolder) {
            val cardView = viewHolder.view as ImageCardView
            cardView.badgeImage = null
            cardView.mainImage = null
        }
    }

    companion object {
        private const val TAG = "MainFragment"
        private const val BACKGROUND_UPDATE_DELAY = 300
        private const val GRID_ITEM_WIDTH = 300
        private const val GRID_ITEM_HEIGHT = 120
    }
}
