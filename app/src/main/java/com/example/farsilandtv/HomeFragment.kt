package com.example.farsilandtv

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.leanback.app.BackgroundManager
import androidx.leanback.app.BrowseSupportFragment
import androidx.leanback.widget.*
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.asLiveData
import androidx.lifecycle.lifecycleScope
import coil.load
import coil.target.Target
import coil.request.Disposable
import com.example.farsilandtv.data.api.RetrofitClient
import com.example.farsilandtv.data.database.ContinueWatchingItem
import com.example.farsilandtv.data.models.Movie
import com.example.farsilandtv.data.models.Series
import com.example.farsilandtv.data.models.FeaturedContent
import com.example.farsilandtv.data.repository.WatchlistRepository
import com.example.farsilandtv.ui.GenreCardPresenter
import com.example.farsilandtv.ui.viewmodel.MainViewModel
import com.example.farsilandtv.ui.presenters.FeaturedCarouselPresenter
import com.example.farsilandtv.utils.SkeletonHelper
import com.example.farsilandtv.utils.FocusMemoryManagerEnhanced as FocusMemoryManager
import com.example.farsilandtv.utils.KeyboardShortcutHandler
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZonedDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*

/**
 * Home fragment - shows Continue Watching, Recent Episodes, Recent Movies, Recent Shows
 * With sidebar navigation to Movies, Shows, Search, Options
 */
class HomeFragment : BrowseSupportFragment() {

    private val mHandler = Handler(Looper.getMainLooper())
    private lateinit var mBackgroundManager: BackgroundManager
    private var mDefaultBackground: Drawable? = null
    private lateinit var mMetrics: DisplayMetrics
    private var mBackgroundTimer: Timer? = null
    private var mBackgroundUri: String? = null
    // Feature #20: Skeleton screens replace ProgressBarManager
    private var isShowingSkeleton = false

    private lateinit var viewModel: MainViewModel
    private lateinit var rowsAdapter: ArrayObjectAdapter
    private val watchlistRepo by lazy { WatchlistRepository(requireContext()) }
    // H11 FIX: Lock for synchronizing rowsAdapter modifications
    private val adapterLock = Any()

    // Featured carousel auto-rotation
    private var carouselRotationTimer: Timer? = null
    private var currentCarouselIndex = 0
    private var featuredRowAdapter: ArrayObjectAdapter? = null
    private var isCarouselFocused = false

    // Track watchlist items - using volatile for thread visibility
    @Volatile
    private var watchlistMovies: List<Movie> = emptyList()
    @Volatile
    private var monitoredSeries: List<Series> = emptyList()

    // Feature #21: Focus memory and keyboard shortcuts
    private val SCREEN_KEY = "home_fragment"
    private lateinit var keyboardHandler: KeyboardShortcutHandler

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        Log.i(TAG, "HomeFragment onActivityCreated")

        // Initialize ViewModel
        viewModel = ViewModelProvider(this)[MainViewModel::class.java]

        // Feature #21: Setup keyboard shortcuts
        keyboardHandler = KeyboardShortcutHandler(
            context = requireContext(),
            onSearch = { openSearch() },
            onFavorites = { openFavorites() },
            onMenu = { showOptionsMenu() }
        )

        prepareBackgroundManager()
        setupUIElements()
        setupAdapter()
        setupEventListeners()
        observeViewModel()
        observeContinueWatching()
        observeWatchlist()

        // Start loading content after all observers are set up
        viewModel.loadContent()
    }

    private fun setupUIElements() {
        title = null // Title moved to sidebar in activity_main.xml
        headersState = HEADERS_HIDDEN // Auto-hide sidebar when browsing rows
        isHeadersTransitionOnBackEnabled = true
        brandColor = resources.getColor(android.R.color.holo_red_dark, null)

        // Feature #20: No more ProgressBarManager - using skeleton screens instead

        // Update timestamp in corner
        updateTimestamp()
    }

    private fun updateTimestamp() {
        val lastFetchMs = RetrofitClient.getLastFetchTimestamp()

        val displayText = if (lastFetchMs > 0) {
            // Convert timestamp to Eastern time
            val instant = Instant.ofEpochMilli(lastFetchMs)
            val easternTime = ZonedDateTime.ofInstant(instant, ZoneId.of("America/New_York"))
            val formatter = DateTimeFormatter.ofPattern("MMM dd, hh:mm a")
            val formattedTime = easternTime.format(formatter)

            // Calculate how long ago
            val ageMinutes = (System.currentTimeMillis() - lastFetchMs) / 60000
            val ageText = when {
                ageMinutes < 1 -> "just now"
                ageMinutes < 60 -> "${ageMinutes}m ago"
                else -> "${ageMinutes / 60}h ${ageMinutes % 60}m ago"
            }

            "Cache: $formattedTime ET ($ageText)"
        } else {
            "Cache: Loading..."
        }

        // Get current database source
        val dbPrefs = com.example.farsilandtv.data.database.DatabasePreferences.getInstance(requireContext())
        val currentSource = dbPrefs.getCurrentSource()

        // Create colored source badge
        val sourceBadge = when (currentSource) {
            com.example.farsilandtv.data.database.DatabaseSource.FARSILAND -> {
                val badge = android.text.SpannableString("[F] ")
                badge.setSpan(
                    android.text.style.ForegroundColorSpan(
                        ContextCompat.getColor(requireContext(), R.color.source_farsiland)
                    ),
                    0, badge.length,
                    android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                badge.setSpan(
                    android.text.style.StyleSpan(android.graphics.Typeface.BOLD),
                    0, badge.length,
                    android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                badge
            }
            com.example.farsilandtv.data.database.DatabaseSource.FARSIPLEX -> {
                val badge = android.text.SpannableString("[F] ")
                badge.setSpan(
                    android.text.style.ForegroundColorSpan(
                        ContextCompat.getColor(requireContext(), R.color.source_farsiplex)
                    ),
                    0, badge.length,
                    android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                badge.setSpan(
                    android.text.style.StyleSpan(android.graphics.Typeface.BOLD),
                    0, badge.length,
                    android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                badge
            }
            com.example.farsilandtv.data.database.DatabaseSource.NAMAKADE -> {
                val badge = android.text.SpannableString("[N] ")
                badge.setSpan(
                    android.text.style.ForegroundColorSpan(
                        ContextCompat.getColor(requireContext(), R.color.source_namakade)
                    ),
                    0, badge.length,
                    android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                badge.setSpan(
                    android.text.style.StyleSpan(android.graphics.Typeface.BOLD),
                    0, badge.length,
                    android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                badge
            }
        }

        // Combine badge with timestamp text
        val fullText = android.text.SpannableStringBuilder()
            .append(sourceBadge)
            .append(displayText)

        (activity as? MainActivity)?.updateTimestamp(fullText)
    }

    private fun setupAdapter() {
        rowsAdapter = ArrayObjectAdapter(ListRowPresenter())
        adapter = rowsAdapter

        // Add navigation rows
        addNavigationRows()
    }

    private fun addNavigationRows() {
        Log.d(TAG, "addNavigationRows() called")
        // Create navigation cards as first row
        val navPresenter = object : Presenter() {
            override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
                val cardView = ImageCardView(parent.context)

                cardView.apply {
                    isFocusable = true
                    isFocusableInTouchMode = true
                    setMainImageDimensions(500, 300)  // Much larger for navigation
                }

                return ViewHolder(cardView)
            }

            override fun onBindViewHolder(viewHolder: ViewHolder, item: Any) {
                val cardView = viewHolder.view as ImageCardView
                val navItem = item as NavigationItem
                cardView.titleText = navItem.title
                cardView.contentText = null  // Remove extra text to make title more prominent

                // Set a placeholder colored image
                val color = when (navItem.destination) {
                    "movies" -> Color.parseColor("#E91E63")
                    "shows" -> Color.parseColor("#9C27B0")
                    "search" -> Color.parseColor("#2196F3")
                    "stats" -> Color.parseColor("#3F51B5")
                    "sync-settings" -> Color.parseColor("#00BCD4")
                    "refresh" -> Color.parseColor("#FF9800")
                    "options" -> Color.parseColor("#4CAF50")
                    else -> Color.parseColor("#757575")
                }

                val drawable = android.graphics.drawable.ColorDrawable(color)
                cardView.mainImageView?.setImageDrawable(drawable)
            }

            override fun onUnbindViewHolder(viewHolder: ViewHolder) {
                val cardView = viewHolder.view as ImageCardView
                cardView.badgeImage = null
                // Don't clear mainImage - it will be set again in onBindViewHolder
                // Clearing it causes colors to disappear when scrolling
            }
        }

        val navAdapter = ArrayObjectAdapter(navPresenter)
        navAdapter.add(NavigationItem("Movies", "movies"))
        navAdapter.add(NavigationItem("TV Shows", "shows"))
        navAdapter.add(NavigationItem("Search", "search"))
        navAdapter.add(NavigationItem("Stats", "stats"))
        navAdapter.add(NavigationItem("Sync Settings", "sync-settings"))
        navAdapter.add(NavigationItem("Refresh", "refresh"))
        navAdapter.add(NavigationItem("Options", "options"))

        Log.d(TAG, "Created navigation adapter with ${navAdapter.size()} items")
        rowsAdapter.add(ListRow(HeaderItem(0, "Navigate"), navAdapter))
        Log.d(TAG, "Added navigation row to rowsAdapter. Total rows: ${rowsAdapter.size()}")
    }

    data class NavigationItem(val title: String, val destination: String)

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

    private fun setupRowsAdapter() {
        rowsAdapter = ArrayObjectAdapter(ListRowPresenter())
        adapter = rowsAdapter
    }

    private fun observeContinueWatching() {
        watchlistRepo.getContinueWatching().asLiveData().observe(viewLifecycleOwner) { items ->
            if (items.isNotEmpty()) {
                Log.d(TAG, "Loaded ${items.size} continue watching items")

                // DEEP AUDIT FIX: Update in-place if row exists, don't remove and re-insert
                // This prevents order swapping when rows are preserved during skeleton display
                var existingRowIndex = -1
                for (i in 0 until rowsAdapter.size()) {
                    val item = rowsAdapter.get(i)
                    if (item is ListRow && item.headerItem?.name == "Continue Watching") {
                        existingRowIndex = i
                        break
                    }
                }

                if (existingRowIndex >= 0) {
                    // Row exists - update items in place (no index change)
                    Log.d(TAG, "Updating Continue Watching row in place at index $existingRowIndex")
                    val existingRow = rowsAdapter.get(existingRowIndex) as ListRow
                    val adapter = existingRow.adapter as? ArrayObjectAdapter
                    adapter?.clear()
                    items.forEach { adapter?.add(it) }
                    rowsAdapter.notifyArrayItemRangeChanged(existingRowIndex, 1)
                } else {
                    // Row doesn't exist - insert after navigation (index 1)
                    Log.d(TAG, "Inserting new Continue Watching row at index 1")
                    val presenter = ContinueWatchingPresenter()
                    val listRowAdapter = ArrayObjectAdapter(presenter)
                    items.forEach { listRowAdapter.add(it) }
                    val header = HeaderItem(1, "Continue Watching")
                    rowsAdapter.add(1, ListRow(header, listRowAdapter))
                }
            }
        }
    }

    private fun observeWatchlist() {
        // Observe watchlist movies
        watchlistRepo.getAllWatchlistedMovies().asLiveData().observe(viewLifecycleOwner) { items ->
            // Create immutable copy to prevent concurrent modification
            watchlistMovies = items.map { wm ->
                Movie(
                    id = wm.id,
                    title = wm.title,
                    posterUrl = wm.posterUrl,
                    farsilandUrl = wm.farsilandUrl
                )
            }.toList()
            Log.d(TAG, "Loaded ${watchlistMovies.size} watchlist movies")
            updateWatchlistRow()
        }

        // Observe monitored series
        watchlistRepo.getAllMonitoredSeries().asLiveData().observe(viewLifecycleOwner) { items ->
            // Create immutable copy to prevent concurrent modification
            monitoredSeries = items.map { ms ->
                Series(
                    id = ms.id,
                    title = ms.title,
                    posterUrl = ms.posterUrl,
                    backdropUrl = ms.backdropUrl,
                    farsilandUrl = ms.farsilandUrl,
                    totalSeasons = ms.totalSeasons
                )
            }.toList()
            Log.d(TAG, "Loaded ${monitoredSeries.size} monitored series")
            updateWatchlistRow()
        }
    }

    private fun updateWatchlistRow() {
        // Create local snapshot of lists to avoid concurrent modification
        val movieSnapshot = watchlistMovies
        val seriesSnapshot = monitoredSeries

        // C7 FIX: Synchronize entire operation to prevent TOCTOU race conditions
        // Multiple observer callbacks can modify rowsAdapter concurrently, causing
        // IndexOutOfBoundsException between bounds check and modify operations
        synchronized(rowsAdapter) {
            // Cache watchlist row index first
            var watchlistRowIndex = -1
            val currentSize = rowsAdapter.size()
            for (i in 0 until currentSize) {
                val item = rowsAdapter.get(i)
                if (item is ListRow && item.headerItem?.name == "My Watchlist") {
                    watchlistRowIndex = i
                    break
                }
            }

            // Add new watchlist row if there are items
            val allItems = movieSnapshot + seriesSnapshot
            if (allItems.isNotEmpty()) {
                if (watchlistRowIndex >= 0 && watchlistRowIndex < rowsAdapter.size()) {
                    // DEEP AUDIT FIX: Row exists - update items in place (no index change)
                    // This prevents order swapping when rows are preserved during skeleton display
                    Log.d(TAG, "Updating My Watchlist row in place at index $watchlistRowIndex")
                    val existingRow = rowsAdapter.get(watchlistRowIndex) as ListRow
                    val adapter = existingRow.adapter as? ArrayObjectAdapter
                    adapter?.clear()
                    allItems.forEach { adapter?.add(it) }
                    rowsAdapter.notifyArrayItemRangeChanged(watchlistRowIndex, 1)
                } else {
                    // Row doesn't exist - insert at correct position
                    val cardPresenter = WatchlistPresenter()
                    val listRowAdapter = ArrayObjectAdapter(cardPresenter)
                    allItems.forEach { listRowAdapter.add(it) }
                    val header = HeaderItem(2, "My Watchlist")

                    // Insert after Continue Watching row (index 2) or after Navigation (index 1)
                    val insertIndex = if (hasContinueWatchingRow()) 2 else 1
                    Log.d(TAG, "Inserting new My Watchlist row at index $insertIndex")

                    // Atomic bounds check and insert to prevent TOCTOU race
                    if (insertIndex <= rowsAdapter.size()) {
                        rowsAdapter.add(insertIndex, ListRow(header, listRowAdapter))
                    }
                }
            } else if (watchlistRowIndex >= 0 && watchlistRowIndex < rowsAdapter.size()) {
                // No items - remove existing row
                Log.d(TAG, "Removing My Watchlist row (no items)")
                rowsAdapter.removeItems(watchlistRowIndex, 1)
            }
        }
    }

    private fun hasContinueWatchingRow(): Boolean {
        for (i in 0 until rowsAdapter.size()) {
            val item = rowsAdapter.get(i)
            if (item is ListRow && item.headerItem?.name == "Continue Watching") {
                return true
            }
        }
        return false
    }

    private fun removeContinueWatchingRow() {
        Log.d(TAG, "removeContinueWatchingRow() - Current row count: ${rowsAdapter.size()}")
        for (i in 0 until rowsAdapter.size()) {
            val item = rowsAdapter.get(i)
            Log.d(TAG, "  Row $i: ${if (item is ListRow) item.headerItem?.name else "Unknown"}")
            if (item is ListRow && item.headerItem?.name == "Continue Watching") {
                Log.d(TAG, "  Removing Continue Watching row at index $i")
                rowsAdapter.removeItems(i, 1)
                break
            }
        }
    }

    private fun observeViewModel() {
        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            if (isLoading) {
                Log.d(TAG, "Loading content from Farsiland.com...")
                // Feature #20: Show skeleton screens instead of spinner
                if (!isShowingSkeleton) {
                    showSkeletonLoading()
                }
            } else {
                // Feature #20: Hide skeleton and show real content
                if (isShowingSkeleton) {
                    hideSkeletonLoading()
                }
                // Update timestamp after loading completes
                updateTimestamp()
            }
        }

        viewModel.error.observe(viewLifecycleOwner) { error ->
            error?.let {
                Log.e(TAG, "Error: $it")
                Toast.makeText(context, it, Toast.LENGTH_LONG).show()
                // Feature #20: Hide skeleton on error
                if (isShowingSkeleton) {
                    hideSkeletonLoading()
                }
            }
        }

        // Featured Content Carousel
        viewModel.featuredContent.observe(viewLifecycleOwner) { featured ->
            if (featured.isNotEmpty()) {
                Log.d(TAG, "Loaded ${featured.size} featured items")
                addFeaturedCarouselRow(featured)
            }
        }

        // Latest Episodes - show all loaded items (supports pagination)
        viewModel.recentEpisodes.observe(viewLifecycleOwner) { episodes ->
            if (episodes.isNotEmpty()) {
                Log.d(TAG, "Loaded ${episodes.size} recent episodes")
                updateEpisodesRow("Latest Episodes", episodes)
            }
        }

        // Recent Movies - show all loaded items (supports pagination)
        viewModel.recentMovies.observe(viewLifecycleOwner) { movies ->
            if (movies.isNotEmpty()) {
                Log.d(TAG, "Loaded ${movies.size} recent movies")
                updateMoviesRow("Recent Movies", movies)
            }
        }

        // Recent Shows - show all loaded items (supports pagination)
        viewModel.recentSeries.observe(viewLifecycleOwner) { series ->
            if (series.isNotEmpty()) {
                Log.d(TAG, "Loaded ${series.size} recent shows")
                updateSeriesRow("Recent Shows", series)
            }
        }
    }

    private fun addContinueWatchingRow(items: List<ContinueWatchingItem>) {
        val presenter = ContinueWatchingPresenter()
        val listRowAdapter = ArrayObjectAdapter(presenter)
        items.forEach {
            Log.d(TAG, "Adding continue watching item: ${it.title}")
            listRowAdapter.add(it)
        }
        Log.d(TAG, "Added ${listRowAdapter.size()} items to Continue Watching row")
        val header = HeaderItem(1, "Continue Watching")
        // Insert after navigation row (index 1)
        rowsAdapter.add(1, ListRow(header, listRowAdapter))
    }

    private fun addEpisodesRow(title: String, episodes: List<com.example.farsilandtv.data.models.Episode>) {
        val cardPresenter = RecentContentPresenter()
        val listRowAdapter = ArrayObjectAdapter(cardPresenter)
        episodes.forEach { listRowAdapter.add(it) }
        val header = HeaderItem(rowsAdapter.size().toLong(), title)
        rowsAdapter.add(ListRow(header, listRowAdapter))
    }

    private fun updateEpisodesRow(title: String, episodes: List<com.example.farsilandtv.data.models.Episode>) {
        Log.d(TAG, "updateEpisodesRow called: title='$title', count=${episodes.size}")

        // H11 FIX: Synchronize adapter access to prevent ConcurrentModificationException
        synchronized(adapterLock) {
            // Find existing row
            var existingRowIndex = -1
            for (i in 0 until rowsAdapter.size()) {
                val item = rowsAdapter.get(i)
                if (item is ListRow && item.headerItem?.name == title) {
                    existingRowIndex = i
                    break
                }
            }

            if (existingRowIndex >= 0) {
                Log.d(TAG, "  → Replacing skeleton row at index $existingRowIndex with real content")
                // IMPORTANT: Replace entire row with new one that has correct presenter
                // Skeleton row has SkeletonCardPresenter which can't render Episode objects!
                rowsAdapter.removeItems(existingRowIndex, 1)

                // Create new row with correct presenter
                val cardPresenter = com.example.farsilandtv.ui.ContentCardPresenter(requireContext())
                val listRowAdapter = ArrayObjectAdapter(cardPresenter)
                episodes.forEach { listRowAdapter.add(it) }
                val header = HeaderItem(title)
                rowsAdapter.add(existingRowIndex, ListRow(header, listRowAdapter))
            } else {
                Log.d(TAG, "  → Row not found, adding new row")
                // Add new row
                addEpisodesRow(title, episodes)
            }
        }
    }

    private fun addMoviesRow(title: String, movies: List<Movie>) {
        val cardPresenter = RecentContentPresenter()
        val listRowAdapter = ArrayObjectAdapter(cardPresenter)
        movies.forEach { listRowAdapter.add(it) }
        val header = HeaderItem(rowsAdapter.size().toLong(), title)
        rowsAdapter.add(ListRow(header, listRowAdapter))
    }

    /**
     * EXTERNAL AUDIT FIX H1: Update movies row with smart in-place updates
     * Prevents UI flickering by updating adapter contents instead of removing/adding rows
     */
    private fun updateMoviesRow(title: String, movies: List<Movie>) {
        Log.d(TAG, "updateMoviesRow called: title='$title', count=${movies.size}")

        // H11 FIX: Synchronize adapter access to prevent ConcurrentModificationException
        synchronized(adapterLock) {
            // Find existing row
            var existingRowIndex = -1
            var existingRow: ListRow? = null
            for (i in 0 until rowsAdapter.size()) {
                val item = rowsAdapter.get(i)
                if (item is ListRow && item.headerItem?.name == title) {
                    existingRowIndex = i
                    existingRow = item
                    break
                }
            }

            if (existingRow != null && existingRowIndex >= 0) {
                val existingAdapter = existingRow.adapter as? ArrayObjectAdapter

                // EXTERNAL AUDIT FIX H1: Check presenter type to decide update strategy
                // If skeleton presenter → replace (presenter incompatible)
                // If same presenter type → update in-place (no flicker)
                // FIX: Check if adapter contains SkeletonCard objects instead of presenter type
                val isSkeleton = existingAdapter?.size() ?: 0 > 0 &&
                                existingAdapter?.get(0) is com.example.farsilandtv.utils.SkeletonCard

                if (isSkeleton) {
                    Log.d(TAG, "  → Replacing skeleton row with real content (presenter mismatch)")
                    // Skeleton presenter can't render Movie objects - must replace
                    rowsAdapter.removeItems(existingRowIndex, 1)
                    val cardPresenter = com.example.farsilandtv.ui.ContentCardPresenter(requireContext())
                    val listRowAdapter = ArrayObjectAdapter(cardPresenter)
                    movies.forEach { listRowAdapter.add(it) }
                    val header = HeaderItem(title)
                    rowsAdapter.add(existingRowIndex, ListRow(header, listRowAdapter))
                } else if (existingAdapter != null) {
                    Log.d(TAG, "  → Updating row in-place (no flicker)")
                    // EXTERNAL AUDIT FIX H1: Update in-place - smooth, no animation
                    existingAdapter.clear()
                    movies.forEach { existingAdapter.add(it) }
                    // Notify adapter of change
                    rowsAdapter.notifyArrayItemRangeChanged(existingRowIndex, 1)
                } else {
                    Log.w(TAG, "  → Existing adapter is null, replacing row")
                    rowsAdapter.removeItems(existingRowIndex, 1)
                    addMoviesRow(title, movies)
                }
            } else {
                Log.d(TAG, "  → Row not found, adding new row")
                // Add new row
                addMoviesRow(title, movies)
            }
        }
    }

    private fun addSeriesRow(title: String, series: List<Series>) {
        val cardPresenter = RecentContentPresenter()
        val listRowAdapter = ArrayObjectAdapter(cardPresenter)
        series.forEach { listRowAdapter.add(it) }
        val header = HeaderItem(rowsAdapter.size().toLong(), title)
        rowsAdapter.add(ListRow(header, listRowAdapter))
    }

    /**
     * EXTERNAL AUDIT FIX H1: Update series row with smart in-place updates
     */
    private fun updateSeriesRow(title: String, series: List<Series>) {
        Log.d(TAG, "updateSeriesRow called: title='$title', count=${series.size}")

        synchronized(adapterLock) {
            var existingRowIndex = -1
            var existingRow: ListRow? = null
            for (i in 0 until rowsAdapter.size()) {
                val item = rowsAdapter.get(i)
                if (item is ListRow && item.headerItem?.name == title) {
                    existingRowIndex = i
                    existingRow = item
                    break
                }
            }

            if (existingRow != null && existingRowIndex >= 0) {
                val existingAdapter = existingRow.adapter as? ArrayObjectAdapter
                // FIX: Check if adapter contains SkeletonCard objects instead of presenter type
                val isSkeleton = existingAdapter?.size() ?: 0 > 0 &&
                                existingAdapter?.get(0) is com.example.farsilandtv.utils.SkeletonCard

                if (isSkeleton) {
                    Log.d(TAG, "  → Replacing skeleton row with real content")
                    rowsAdapter.removeItems(existingRowIndex, 1)
                    val cardPresenter = com.example.farsilandtv.ui.ContentCardPresenter(requireContext())
                    val listRowAdapter = ArrayObjectAdapter(cardPresenter)
                    series.forEach { listRowAdapter.add(it) }
                    val header = HeaderItem(title)
                    rowsAdapter.add(existingRowIndex, ListRow(header, listRowAdapter))
                } else if (existingAdapter != null) {
                    Log.d(TAG, "  → Updating row in-place (no flicker)")
                    existingAdapter.clear()
                    series.forEach { existingAdapter.add(it) }
                    rowsAdapter.notifyArrayItemRangeChanged(existingRowIndex, 1)
                } else {
                    Log.w(TAG, "  → Existing adapter is null, replacing row")
                    rowsAdapter.removeItems(existingRowIndex, 1)
                    addSeriesRow(title, series)
                }
            } else {
                Log.d(TAG, "  → Row not found, adding new row")
                addSeriesRow(title, series)
            }
        }
    }

    private fun setupEventListeners() {
        onItemViewClickedListener = ItemViewClickedListener()
        onItemViewSelectedListener = ItemViewSelectedListener()

        // Feature #21: Keyboard shortcuts are handled via KeyboardShortcutHandler
        // No need to manually request focus - let BrowseSupportFragment handle it
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Start with sidebar hidden (user can press left to show it)
        startHeadersTransition(false)

        // Set initial focus position to trigger rendering
        // This is required for BrowseSupportFragment to display rows
        view.post {
            setSelectedPosition(0, false)
        }
    }

    override fun onResume() {
        super.onResume()

        // SYNC FIX: Check if sync completed while we were away
        val syncStatePrefs = requireContext().getSharedPreferences("sync_state", android.content.Context.MODE_PRIVATE)
        val lastSyncTime = syncStatePrefs.getLong("last_sync_timestamp", 0L)
        val prefs = requireContext().getSharedPreferences("home_state", android.content.Context.MODE_PRIVATE)
        val lastSeenSyncTime = prefs.getLong("last_seen_sync_timestamp", 0L)

        if (lastSyncTime > lastSeenSyncTime && lastSyncTime > 0) {
            Log.i(TAG, "Sync completed while away - refreshing content")
            // Update our seen timestamp
            prefs.edit().putLong("last_seen_sync_timestamp", lastSyncTime).apply()
            // Force refresh to show new content
            refreshContent()
        }

        // Feature #21: Restore focus to last position
        view?.post {
            val focusPos = FocusMemoryManager.restoreFocus(SCREEN_KEY)
            if (focusPos != null) {
                // Bounds check: ensure position is valid before restoring
                if (focusPos.itemPosition < rowsAdapter.size()) {
                    setSelectedPosition(focusPos.itemPosition, true)
                    Log.d(TAG, "Restored focus to row ${focusPos.itemPosition}")
                } else {
                    Log.w(TAG, "Saved focus position ${focusPos.itemPosition} is out of bounds (max ${rowsAdapter.size() - 1}), ignoring")
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        // Cancel timers when fragment is paused to prevent memory leak
        mBackgroundTimer?.cancel()
        stopCarouselRotation()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // H10 FIX: Cancel carousel timer to prevent memory leak and crashes
        stopCarouselRotation()
        // Cancel background timer
        mBackgroundTimer?.cancel()
        // M2 FIX: Note - All data loading uses lifecycleScope.launch which automatically
        // cancels when the fragment's view is destroyed, saving bandwidth and CPU.
        // Release BackgroundManager to prevent memory leak
        if (mBackgroundManager.isAttached) {
            mBackgroundManager.release()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mBackgroundTimer?.cancel()
        stopCarouselRotation()
    }

    // Feature #21: Keyboard shortcuts - handled via KeyboardShortcutHandler in onActivityCreated

    private fun openSearch() {
        startActivity(Intent(requireContext(), SearchActivity::class.java))
    }

    private fun openFavorites() {
        startActivity(Intent(requireContext(), FavoritesActivity::class.java))
    }

    private fun showOptionsMenu() {
        // Show keyboard shortcuts help
        KeyboardShortcutHandler.showShortcutsToast(requireContext())
    }

    /**
     * Force refresh content from server (bypasses cache)
     */
    private fun refreshContent() {
        Log.d(TAG, "Force refresh triggered")
        Toast.makeText(context, "Refreshing content...", Toast.LENGTH_SHORT).show()

        // Update timestamp
        updateTimestamp()

        // DEEP AUDIT FIX: Preserve all user-specific rows (Navigation, Continue Watching, Watchlist)
        // Only remove content rows (movies, series, episodes) to prevent flicker
        var navigationRow: ListRow? = null
        var continueWatchingRow: ListRow? = null
        var watchlistRow: ListRow? = null

        for (i in 0 until rowsAdapter.size()) {
            val item = rowsAdapter.get(i)
            if (item is ListRow) {
                when (item.headerItem?.name) {
                    "Navigate" -> navigationRow = item
                    "Continue Watching" -> continueWatchingRow = item
                    "My Watchlist" -> watchlistRow = item
                }
            }
        }

        // Clear all existing rows
        rowsAdapter.clear()

        // Re-add preserved rows in correct order
        if (navigationRow != null) {
            rowsAdapter.add(navigationRow)
            Log.d(TAG, "Preserved navigation row")
        } else {
            Log.w(TAG, "Navigation row not found, recreating")
            addNavigationRows()
        }

        if (continueWatchingRow != null) {
            rowsAdapter.add(continueWatchingRow)
            Log.d(TAG, "Preserved continue watching row")
        }

        if (watchlistRow != null) {
            rowsAdapter.add(watchlistRow)
            Log.d(TAG, "Preserved watchlist row")
        }

        // Trigger force refresh in ViewModel (will add fresh content rows)
        viewModel.forceRefresh()
    }

    private inner class ItemViewClickedListener : OnItemViewClickedListener {
        override fun onItemClicked(
            itemViewHolder: Presenter.ViewHolder,
            item: Any,
            rowViewHolder: RowPresenter.ViewHolder,
            row: Row
        ) {
            when (item) {
                is NavigationItem -> {
                    if (item.destination == "refresh") {
                        refreshContent()
                    } else {
                        (activity as? MainActivity)?.navigateTo(item.destination)
                    }
                }
                is FeaturedContent -> {
                    // Handle featured content click
                    when (item) {
                        is FeaturedContent.FeaturedMovie -> showMovieDetails(item.movie)
                        is FeaturedContent.FeaturedSeries -> openSeriesDetails(item.series)
                    }
                }
                is ContinueWatchingItem -> resumePlayback(item)
                is Movie -> showMovieDetails(item)
                is Series -> openSeriesDetails(item)
                is com.example.farsilandtv.data.models.Episode -> playEpisode(item)
                else -> {
                    Log.w(TAG, "Unknown item type clicked: ${item::class.java.simpleName}")
                }
            }
        }
    }

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
            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun showMovieDetails(movie: Movie) {
        // H9 FIX: Validate Movie object before passing to DetailsActivity
        if (movie.id == 0 || movie.title.isBlank() || movie.farsilandUrl.isBlank()) {
            Log.e(TAG, "Invalid movie data: id=${movie.id}, title='${movie.title}', url='${movie.farsilandUrl}'")
            Toast.makeText(context, "Error: Invalid content data", Toast.LENGTH_SHORT).show()
            return
        }

        val intent = Intent(requireContext(), DetailsActivity::class.java).apply {
            putExtra(DetailsActivity.EXTRA_MOVIE, movie)
        }
        startActivity(intent)
    }

    private fun openSeriesDetails(series: Series) {
        // H9 FIX: Validate Series object before passing to SeriesDetailsActivity
        if (series.id == 0 || series.title.isBlank() || series.farsilandUrl.isBlank()) {
            Log.e(TAG, "Invalid series data: id=${series.id}, title='${series.title}', url='${series.farsilandUrl}'")
            Toast.makeText(context, "Error: Invalid content data", Toast.LENGTH_SHORT).show()
            return
        }

        val intent = Intent(requireContext(), SeriesDetailsActivity::class.java).apply {
            putExtra(SeriesDetailsActivity.EXTRA_SERIES, series)
        }
        startActivity(intent)
    }

    private fun playEpisode(episode: com.example.farsilandtv.data.models.Episode) {
        val intent = Intent(requireContext(), VideoPlayerActivity::class.java).apply {
            putExtra("CONTENT_TYPE", "episode")
            putExtra("CONTENT_ID", episode.id)
            putExtra("CONTENT_TITLE", "${episode.formattedNumber}: ${episode.title}")
            putExtra("CONTENT_URL", episode.farsilandUrl)
            putExtra("SERIES_ID", episode.seriesId ?: 0)  // FIX: Add required series ID
            putExtra("EPISODE_SEASON", episode.season)
            putExtra("EPISODE_NUMBER", episode.episode)
            putExtra("CONTENT_POSTER_URL", episode.thumbnailUrl)
        }
        startActivity(intent)
    }

    private inner class ItemViewSelectedListener : OnItemViewSelectedListener {
        override fun onItemSelected(
            itemViewHolder: Presenter.ViewHolder?,
            item: Any?,
            rowViewHolder: RowPresenter.ViewHolder,
            row: Row
        ) {
            // Feature #21: Save focus position when item is selected
            if (item != null && row is ListRow) {
                val adapter = row.adapter as? ArrayObjectAdapter
                val itemIndex = adapter?.indexOf(item) ?: 0
                val rowIndex = rowsAdapter.indexOf(row)
                FocusMemoryManager.saveFocus(SCREEN_KEY, itemIndex, rowIndex)
            }

            // Track if carousel is focused (for auto-rotation pause)
            // Featured row has empty header name
            if (row is ListRow && row.headerItem?.name == "") {
                isCarouselFocused = true
                stopCarouselRotation() // Pause rotation when user focuses carousel
            } else {
                if (isCarouselFocused) {
                    isCarouselFocused = false
                    startCarouselRotation() // Resume rotation when focus leaves
                }
            }

            // Background updates disabled per user request
            // (previously changed background to focused item's poster/backdrop)

            // Infinite scroll: Load more items when near end of row
            if (row is ListRow && item != null) {
                val adapter = row.adapter as? ArrayObjectAdapter
                if (adapter != null && adapter.size() > 0) {
                    val itemIndex = (0 until adapter.size()).indexOfFirst { adapter.get(it) == item }

                    // Trigger load earlier (within last 10 items) for smoother experience
                    if (itemIndex >= adapter.size() - 10 && itemIndex >= 0) {
                        when (row.headerItem?.name) {
                            "Latest Episodes" -> {
                                Log.d(TAG, "Near end of episodes row (item $itemIndex/${adapter.size()}), loading more...")
                                viewModel.loadMoreEpisodes()
                            }
                            "Recent Movies" -> {
                                Log.d(TAG, "Near end of movies row (item $itemIndex/${adapter.size()}), loading more...")
                                viewModel.loadMoreMovies()
                            }
                            "Recent Shows" -> {
                                Log.d(TAG, "Near end of shows row (item $itemIndex/${adapter.size()}), loading more...")
                                viewModel.loadMoreSeries()
                            }
                        }
                    }
                }
            }
        }
    }

    private fun updateBackground(uri: String?) {
        // Load background using Coil with lifecycle awareness
        viewLifecycleOwner.lifecycleScope.launch {
            val imageView = android.widget.ImageView(requireContext())
            imageView.load(uri) {
                lifecycle(viewLifecycleOwner)
                size(mMetrics.widthPixels, mMetrics.heightPixels)
                error(mDefaultBackground)
                target(
                    onSuccess = { drawable ->
                        mBackgroundManager.drawable = drawable
                    }
                )
            }
        }
        mBackgroundTimer?.cancel()
    }

    private fun startBackgroundTimer() {
        mBackgroundTimer?.cancel()
        mBackgroundTimer = Timer()
        mBackgroundTimer?.schedule(UpdateBackgroundTask(), 300)
    }

    private inner class UpdateBackgroundTask : TimerTask() {
        override fun run() {
            mHandler.post { updateBackground(mBackgroundUri) }
        }
    }

    /**
     * Show dialog to add/remove item from watchlist
     */
    private fun showWatchlistDialog(item: Any) {
        lifecycleScope.launch {
            try {
                val itemTitle = when (item) {
                    is Movie -> item.title
                    is Series -> item.title
                    else -> "Unknown"
                }

                // Check if item is already in watchlist
                val isInWatchlist = when (item) {
                    is Movie -> watchlistRepo.isMovieInWatchlist(item.id)
                    is Series -> watchlistRepo.isSeriesMonitored(item.id)
                    else -> false
                }

                val dialogTitle = if (isInWatchlist) "Remove from Watchlist" else "Add to Watchlist"
                val dialogMessage = if (isInWatchlist) {
                    "Remove \"$itemTitle\" from your watchlist?"
                } else {
                    "Add \"$itemTitle\" to your watchlist?"
                }
                val positiveButtonText = if (isInWatchlist) "Remove" else "Add"

                android.app.AlertDialog.Builder(requireContext())
                    .setTitle(dialogTitle)
                    .setMessage(dialogMessage)
                    .setPositiveButton(positiveButtonText) { dialog, _ ->
                        if (isInWatchlist) {
                            removeFromWatchlist(item)
                        } else {
                            addToWatchlist(item)
                        }
                        dialog.dismiss()
                    }
                    .setNegativeButton("Cancel") { dialog, _ ->
                        dialog.dismiss()
                    }
                    .show()
            } catch (e: Exception) {
                Log.e(TAG, "Error showing watchlist dialog", e)
                Toast.makeText(
                    requireContext(),
                    "Error: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    /**
     * Add item to watchlist
     */
    private fun addToWatchlist(item: Any) {
        lifecycleScope.launch {
            try {
                when (item) {
                    is Movie -> {
                        watchlistRepo.addMovieToWatchlist(item)
                        Log.d(TAG, "Added movie ${item.id} to watchlist")
                        Toast.makeText(
                            requireContext(),
                            "Added to watchlist",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    is Series -> {
                        watchlistRepo.addSeriesToMonitored(item)
                        Log.d(TAG, "Added series ${item.id} to monitored")
                        Toast.makeText(
                            requireContext(),
                            "Added to watchlist",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error adding to watchlist", e)
                Toast.makeText(
                    requireContext(),
                    "Error: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    /**
     * Show dialog to remove item from watchlist
     */
    private fun showRemoveFromWatchlistDialog(item: Any) {
        val itemTitle = when (item) {
            is Movie -> item.title
            is Series -> item.title
            else -> "Unknown"
        }

        android.app.AlertDialog.Builder(requireContext())
            .setTitle("Remove from Watchlist")
            .setMessage("Remove \"$itemTitle\" from your watchlist?")
            .setPositiveButton("Remove") { dialog, _ ->
                removeFromWatchlist(item)
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    /**
     * Remove item from watchlist
     */
    private fun removeFromWatchlist(item: Any) {
        lifecycleScope.launch {
            try {
                when (item) {
                    is Movie -> {
                        watchlistRepo.removeMovieFromWatchlist(item.id)
                        Log.d(TAG, "Removed movie ${item.id} from watchlist")
                        Toast.makeText(
                            requireContext(),
                            "Removed from watchlist",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    is Series -> {
                        watchlistRepo.removeSeriesFromMonitored(item.id)
                        Log.d(TAG, "Removed series ${item.id} from monitored")
                        Toast.makeText(
                            requireContext(),
                            "Removed from monitored series",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error removing from watchlist", e)
                Toast.makeText(
                    requireContext(),
                    "Error: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    /**
     * Show dialog to remove item from continue watching
     */
    private fun showRemoveFromContinueWatchingDialog(item: ContinueWatchingItem) {
        val itemTitle = if (item.contentType == ContinueWatchingItem.ContentType.EPISODE) {
            "${item.subtitle} - ${item.title}"
        } else {
            item.title
        }

        android.app.AlertDialog.Builder(requireContext())
            .setTitle("Remove from Continue Watching")
            .setMessage("Remove \"$itemTitle\" from continue watching?")
            .setPositiveButton("Remove") { dialog, _ ->
                removeFromContinueWatching(item)
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    /**
     * Remove item from continue watching
     */
    private fun removeFromContinueWatching(item: ContinueWatchingItem) {
        lifecycleScope.launch {
            try {
                when (item.contentType) {
                    ContinueWatchingItem.ContentType.MOVIE -> {
                        val movieId = item.id.removePrefix("movie-").toIntOrNull()
                        if (movieId != null) {
                            watchlistRepo.removeMovieFromContinueWatching(movieId)
                            Log.d(TAG, "Removed movie $movieId from continue watching")
                            Toast.makeText(
                                requireContext(),
                                "Removed from continue watching",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                    ContinueWatchingItem.ContentType.EPISODE -> {
                        val episodeId = item.episodeId
                        if (episodeId != null) {
                            watchlistRepo.removeEpisodeFromContinueWatching(episodeId)
                            Log.d(TAG, "Removed episode $episodeId from continue watching")
                            Toast.makeText(
                                requireContext(),
                                "Removed from continue watching",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error removing from continue watching", e)
                Toast.makeText(
                    requireContext(),
                    "Error: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    /**
     * Presenter for watchlist items with long-press to remove
     */
    private inner class WatchlistPresenter : Presenter() {
        private val contentPresenter = com.example.farsilandtv.ui.ContentCardPresenter(context)

        override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
            return contentPresenter.onCreateViewHolder(parent)
        }

        override fun onBindViewHolder(viewHolder: ViewHolder, item: Any) {
            // Bind normally using ContentCardPresenter (which includes source badges)
            contentPresenter.onBindViewHolder(viewHolder, item)

            // Add long-press listener for removal
            viewHolder.view.setOnLongClickListener {
                showRemoveFromWatchlistDialog(item)
                true
            }
        }

        override fun onUnbindViewHolder(viewHolder: ViewHolder) {
            contentPresenter.onUnbindViewHolder(viewHolder)
            viewHolder.view.setOnLongClickListener(null)
        }
    }

    /**
     * Presenter for recent content with long-press to add/remove from watchlist
     */
    private inner class RecentContentPresenter : Presenter() {
        private val contentPresenter = GenreCardPresenter(context)

        override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
            return contentPresenter.onCreateViewHolder(parent)
        }

        override fun onBindViewHolder(viewHolder: ViewHolder, item: Any) {
            // Bind normally using GenreCardPresenter
            contentPresenter.onBindViewHolder(viewHolder, item)

            // Add long-press listener for add/remove watchlist
            viewHolder.view.setOnLongClickListener {
                showWatchlistDialog(item)
                true
            }
        }

        override fun onUnbindViewHolder(viewHolder: ViewHolder) {
            contentPresenter.onUnbindViewHolder(viewHolder)
            viewHolder.view.setOnLongClickListener(null)
        }
    }

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

            cardView.titleText = if (continueItem.contentType == ContinueWatchingItem.ContentType.EPISODE) {
                "${continueItem.subtitle} - ${continueItem.title}"
            } else {
                continueItem.title
            }

            // Add source badge to progress text
            val progressText = "${continueItem.progressPercentage}% watched"
            cardView.contentText = com.example.farsilandtv.utils.SourceBadgeHelper.prependBadge(
                cardView.context,
                continueItem.farsilandUrl,
                progressText
            )

            if (!continueItem.posterUrl.isNullOrEmpty()) {
                viewLifecycleOwner.lifecycleScope.launch {
                    cardView.mainImageView.load(continueItem.posterUrl) {
                        lifecycle(viewLifecycleOwner)
                        crossfade(300)
                        placeholder(R.drawable.image_placeholder)
                        error(R.drawable.movie)
                    }
                }
            }

            // Add long-press listener for removal
            cardView.setOnLongClickListener {
                showRemoveFromContinueWatchingDialog(continueItem)
                true
            }
        }

        override fun onUnbindViewHolder(viewHolder: ViewHolder) {
            val cardView = viewHolder.view as ImageCardView
            cardView.badgeImage = null
            cardView.mainImage = null
        }
    }

    /**
     * Add featured content carousel row at the top
     * Displays 6 items (mix of movies and series) with auto-rotation every 5 seconds
     */
    private fun addFeaturedCarouselRow(items: List<FeaturedContent>) {
        // Remove existing featured row if any (including skeleton featured rows)
        removeFeaturedCarouselRow()

        val presenter = FeaturedCarouselPresenter()
        featuredRowAdapter = ArrayObjectAdapter(presenter)

        items.forEach {
            featuredRowAdapter?.add(it)
        }

        // UI FIX: Use unique header ID (-1) to avoid conflict with Navigate row (ID 0)
        val header = HeaderItem(-1, "")
        val featuredRow = ListRow(header, featuredRowAdapter)

        // Ensure navigation row exists before inserting featured carousel
        // Insert after navigation row if it exists, otherwise at top
        val insertPosition = if (rowsAdapter.size() > 0) {
            val firstRow = rowsAdapter.get(0)
            if (firstRow is ListRow && firstRow.headerItem?.name == "Navigation") {
                1 // Insert after navigation
            } else {
                0 // Insert at top
            }
        } else {
            0
        }

        rowsAdapter.add(insertPosition, featuredRow)

        // Start auto-rotation
        startCarouselRotation()

        Log.d(TAG, "Added featured carousel with ${items.size} items at position $insertPosition")
    }

    /**
     * Remove existing featured carousel row
     */
    private fun removeFeaturedCarouselRow() {
        // Remove all rows with empty header name (both skeleton and real featured rows)
        val rowsToRemove = mutableListOf<Int>()
        for (i in 0 until rowsAdapter.size()) {
            val item = rowsAdapter.get(i)
            // Featured row has empty header name
            if (item is ListRow && item.headerItem?.name == "") {
                rowsToRemove.add(i)
            }
        }

        // Remove in reverse order to maintain indices
        for (index in rowsToRemove.reversed()) {
            rowsAdapter.removeItems(index, 1)
            Log.d(TAG, "Removed featured/skeleton row at index $index")
        }
    }

    /**
     * Start carousel auto-rotation (every 5 seconds)
     */
    private fun startCarouselRotation() {
        stopCarouselRotation()

        carouselRotationTimer = Timer()
        carouselRotationTimer?.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                // Only rotate if carousel is not focused
                if (!isCarouselFocused && featuredRowAdapter != null && featuredRowAdapter!!.size() > 1) {
                    mHandler.post {
                        currentCarouselIndex = (currentCarouselIndex + 1) % featuredRowAdapter!!.size()
                        // This would require custom row implementation to actually scroll
                        // For now, this is a placeholder for the rotation logic
                        Log.d(TAG, "Carousel auto-rotate to index $currentCarouselIndex")
                    }
                }
            }
        }, 5000, 5000) // Start after 5s, repeat every 5s
    }

    /**
     * Stop carousel auto-rotation
     */
    private fun stopCarouselRotation() {
        carouselRotationTimer?.cancel()
        carouselRotationTimer = null
    }

    /**
     * Feature #20: Show skeleton loading screens
     * Displays 3 skeleton rows (Featured, Movies, Series) with shimmer animation
     * Replaces spinning progress bar for better UX
     */
    private fun showSkeletonLoading() {
        if (isShowingSkeleton) return

        Log.d(TAG, "Showing skeleton loading screens...")
        isShowingSkeleton = true

        // DEEP AUDIT FIX: Preserve all user-specific rows (Navigation, Continue Watching, Watchlist)
        // Only clear content rows (movies, series, episodes)
        var navigationRow: ListRow? = null
        var continueWatchingRow: ListRow? = null
        var watchlistRow: ListRow? = null

        for (i in 0 until rowsAdapter.size()) {
            val item = rowsAdapter.get(i)
            if (item is ListRow) {
                when (item.headerItem?.name) {
                    "Navigate" -> navigationRow = item
                    "Continue Watching" -> continueWatchingRow = item
                    "My Watchlist" -> watchlistRow = item
                }
            }
        }

        // Clear all rows
        rowsAdapter.clear()

        // Re-add preserved rows in correct order
        navigationRow?.let {
            rowsAdapter.add(it)
            Log.d(TAG, "Preserved navigation row during skeleton display")
        }
        continueWatchingRow?.let {
            rowsAdapter.add(it)
            Log.d(TAG, "Preserved continue watching row during skeleton display")
        }
        watchlistRow?.let {
            rowsAdapter.add(it)
            Log.d(TAG, "Preserved watchlist row during skeleton display")
        }

        // Add Featured skeleton row (empty header name)
        val featuredRow = ListRow(
            HeaderItem(""),
            SkeletonHelper.createSkeletonAdapter(1)
        )
        rowsAdapter.add(featuredRow)

        // Add Movies skeleton row
        val moviesRow = ListRow(
            HeaderItem("Recent Movies"),
            SkeletonHelper.createSkeletonAdapter(6)
        )
        rowsAdapter.add(moviesRow)

        // Add Series skeleton row
        val seriesRow = ListRow(
            HeaderItem("Recent Shows"),
            SkeletonHelper.createSkeletonAdapter(6)
        )
        rowsAdapter.add(seriesRow)

        Log.d(TAG, "Skeleton screens displayed (${rowsAdapter.size()} rows)")
    }

    /**
     * Feature #20: Hide skeleton loading screens
     * Smooth transition handled by adapter update when real content is added
     */
    private fun hideSkeletonLoading() {
        if (!isShowingSkeleton) return

        Log.d(TAG, "Hiding skeleton screens...")
        isShowingSkeleton = false

        // Clean up any remaining skeleton rows that might be stuck
        // This prevents the skeleton box from remaining visible when switching sources
        cleanupSkeletonRows()

        Log.d(TAG, "Skeleton flag cleared - observer callbacks will replace skeleton content")
    }

    private fun cleanupSkeletonRows() {
        // Remove all rows that contain SkeletonCard objects
        val rowsToRemove = mutableListOf<Int>()
        for (i in 0 until rowsAdapter.size()) {
            val item = rowsAdapter.get(i)
            if (item is ListRow) {
                val adapter = item.adapter as? ArrayObjectAdapter
                if (adapter != null && adapter.size() > 0) {
                    val firstItem = adapter.get(0)
                    if (firstItem is com.example.farsilandtv.utils.SkeletonCard) {
                        rowsToRemove.add(i)
                        Log.d(TAG, "Found skeleton row at index $i to remove")
                    }
                }
            }
        }

        // Remove skeleton rows in reverse order to maintain indices
        for (index in rowsToRemove.reversed()) {
            rowsAdapter.removeItems(index, 1)
            Log.d(TAG, "Removed skeleton row at index $index")
        }
    }

    companion object {
        private const val TAG = "HomeFragment"
    }
}
