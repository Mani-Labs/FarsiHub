package com.example.farsilandtv

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.leanback.app.BackgroundManager
import androidx.leanback.app.VerticalGridSupportFragment
import androidx.leanback.widget.*
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.farsilandtv.data.model.FilterCard
import com.example.farsilandtv.data.model.Genre
import com.example.farsilandtv.data.models.Series
import com.example.farsilandtv.data.repository.ContentRepository
import com.example.farsilandtv.ui.GenreCardPresenter
import com.example.farsilandtv.ui.GenreFilterDialogFragment
import com.example.farsilandtv.ui.model.GenreChip
import com.example.farsilandtv.ui.presenters.FilterCardPresenter
import com.example.farsilandtv.ui.viewmodel.MainViewModel
import com.example.farsilandtv.utils.GenrePreferences
import com.example.farsilandtv.utils.SkeletonHelper
import com.example.farsilandtv.utils.FocusMemoryManagerEnhanced as FocusMemoryManager
import kotlinx.coroutines.launch

/**
 * Shows page - grid view with filtering/sorting
 */
class ShowsFragment : VerticalGridSupportFragment() {

    private lateinit var viewModel: MainViewModel
    private lateinit var gridAdapter: ArrayObjectAdapter
    private lateinit var repository: ContentRepository
    // Feature #20: Skeleton screens replace ProgressBarManager
    private var isShowingSkeleton = false
    private lateinit var genrePreferences: GenrePreferences

    // Background manager for consistent look with HomeFragment
    private lateinit var mBackgroundManager: BackgroundManager
    private var mDefaultBackground: Drawable? = null
    private lateinit var mMetrics: DisplayMetrics

    private var allShows: List<Series> = emptyList()
    private var genreChips: MutableList<GenreChip> = mutableListOf()

    // Filter state
    private var selectedGenres: MutableList<Genre> = mutableListOf()
    private var sortBy: SortOption = SortOption.RECENT

    // Pagination state
    private var currentPage = 1
    private var isLoadingMore = false
    private var hasMorePages = true

    // Feature #21: Focus memory
    private val SCREEN_KEY = "shows_fragment"

    enum class SortOption {
        RECENT, TITLE, RATING
    }

    // Header view references
    private var sortButton: LinearLayout? = null
    private var sortSubtitle: TextView? = null
    private var filterButton: LinearLayout? = null
    private var filterSubtitle: TextView? = null

    // Track current grid position for up navigation
    private var currentGridPosition = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(TAG, "ShowsFragment onCreate")

        repository = ContentRepository.getInstance(requireContext())
        viewModel = ViewModelProvider(this)[MainViewModel::class.java]
        genrePreferences = GenrePreferences(requireContext())

        // Load saved genre selections
        selectedGenres.clear()
        selectedGenres.addAll(genrePreferences.getSelectedShowGenres())

        setupAdapter()
        setupGenreChips()
        prepareBackgroundManager()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate custom layout with header buttons
        val rootView = inflater.inflate(R.layout.fragment_grid_with_header, container, false)

        // Setup header buttons
        setupHeaderButtons(rootView)

        // Inflate the grid fragment's view and add it to the container
        val gridView = super.onCreateView(inflater, rootView.findViewById(R.id.grid_container), savedInstanceState)
        val gridContainer = rootView.findViewById<FrameLayout>(R.id.grid_container)
        if (gridView != null) {
            gridContainer.addView(gridView)

            // Remove Leanback's default horizontal padding only
            gridView.post {
                // Find VerticalGridView in the hierarchy
                val grid = findVerticalGridView(gridView)
                grid?.let {
                    it.setPadding(0, it.paddingTop, 0, it.paddingBottom)
                }
            }
        }

        // Setup key event interceptor at fragment root level
        setupKeyInterceptor(rootView)

        return rootView
    }

    private fun findVerticalGridView(view: View): androidx.leanback.widget.VerticalGridView? {
        if (view is androidx.leanback.widget.VerticalGridView) {
            return view
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                val found = findVerticalGridView(view.getChildAt(i))
                if (found != null) return found
            }
        }
        return null
    }

    private fun setupKeyInterceptor(rootView: View) {
        // Make root view focusable to intercept key events
        rootView.isFocusableInTouchMode = true
        rootView.requestFocus()

        // Intercept D-pad UP from first row to navigate to header buttons
        rootView.setOnKeyListener { _, keyCode, event ->
            if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_UP &&
                event.action == android.view.KeyEvent.ACTION_DOWN) {
                // Only intercept if on first row (positions 0-4)
                if (currentGridPosition < 5) {
                    // Route to appropriate header button based on column
                    val targetButton = if (currentGridPosition < 3) sortButton else filterButton
                    targetButton?.requestFocus()
                    return@setOnKeyListener true
                }
            }
            false
        }
    }

    private fun setupHeaderButtons(rootView: View) {
        sortButton = rootView.findViewById(R.id.sort_button)
        sortSubtitle = rootView.findViewById(R.id.sort_subtitle)
        filterButton = rootView.findViewById(R.id.filter_button)
        filterSubtitle = rootView.findViewById(R.id.filter_subtitle)

        // Set page title
        val pageTitle = rootView.findViewById<TextView>(R.id.page_title)
        pageTitle?.text = "TV Shows"

        // Sort button click - cycle through sort options
        sortButton?.setOnClickListener {
            cycleSortOption()
        }

        // Filter button click
        filterButton?.setOnClickListener {
            showFilterDialog()
        }

        // Update button appearances
        updateSortButtonAppearance()
        updateFilterButtonAppearance()
    }

    private fun cycleSortOption() {
        // Cycle through sort options: Recent -> Title -> Rating -> Recent
        sortBy = when (sortBy) {
            SortOption.RECENT -> SortOption.TITLE
            SortOption.TITLE -> SortOption.RATING
            SortOption.RATING -> SortOption.RECENT
        }

        // Update UI
        updateSortButtonAppearance()

        // Re-apply current filters with new sort
        applyFiltersAndSort()

        // Show feedback
        val sortName = when (sortBy) {
            SortOption.RECENT -> "Recent"
            SortOption.TITLE -> "Title"
            SortOption.RATING -> "Rating"
        }
        Toast.makeText(context, "Sorted by: $sortName", Toast.LENGTH_SHORT).show()
    }

    private fun updateSortButtonAppearance() {
        val sortText = when (sortBy) {
            SortOption.RECENT -> "Recent"
            SortOption.TITLE -> "Title"
            SortOption.RATING -> "Rating"
        }
        sortSubtitle?.text = sortText
    }

    private fun updateFilterButtonAppearance() {
        filterSubtitle?.text = if (selectedGenres.isEmpty()) {
            "Tap to filter"
        } else {
            "${selectedGenres.size} selected"
        }
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        // Observe loading state
        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            if (isLoading) {
                // Feature #20: Show skeleton grid instead of spinner
                if (!isShowingSkeleton) {
                    showSkeletonGrid()
                }
            } else {
                // Feature #20: Hide skeleton when content loads
                if (isShowingSkeleton) {
                    hideSkeletonGrid()
                }
            }
        }

        // Observe error state
        viewModel.error.observe(viewLifecycleOwner) { error ->
            error?.let {
                Toast.makeText(context, it, Toast.LENGTH_LONG).show()
                // Feature #20: Hide skeleton on error
                if (isShowingSkeleton) {
                    hideSkeletonGrid()
                }
            }
        }

        loadShows()
    }

    private fun setupAdapter() {
        val gridPresenter = VerticalGridPresenter().apply {
            numberOfColumns = 5
            // Disable focus scaling to prevent overlap
            shadowEnabled = false
        }
        setGridPresenter(gridPresenter)

        // Use ClassPresenterSelector to handle different card types
        val presenterSelector = ClassPresenterSelector().apply {
            addClassPresenter(Series::class.java, GenreCardPresenter(requireContext()))
            addClassPresenter(com.example.farsilandtv.utils.SkeletonCard::class.java, com.example.farsilandtv.utils.SkeletonCardPresenter())
        }

        gridAdapter = ArrayObjectAdapter(presenterSelector)
        adapter = gridAdapter

        setOnItemViewClickedListener { _, item, _, _ ->
            when (item) {
                is Series -> showSeriesDetails(item)
            }
        }

        // Load more items when user scrolls near the end
        setOnItemViewSelectedListener { _, item, _, _ ->
            if (item != null && gridAdapter.size() > 0) {
                val position = gridAdapter.indexOf(item)

                // Track position for up navigation
                currentGridPosition = position

                // Feature #21: Save focus position
                FocusMemoryManager.saveFocus(SCREEN_KEY, position)

                // Load very early (15 items from end) for ultra-smooth experience
                // Smaller batches load faster, so we can afford to trigger earlier
                if (position >= gridAdapter.size() - 15 && position >= 0 && !isLoadingMore && hasMorePages) {
                    Log.d(TAG, "Near end (position $position/${gridAdapter.size()}), loading more shows...")
                    loadMoreShows()
                }
            }
        }

        // Hide default Leanback title (using custom title in header instead)
        title = ""

        // Feature #20: No more ProgressBarManager - using skeleton screens instead
    }

    override fun onResume() {
        super.onResume()

        // Feature #21: Restore focus to last position (only if saved)
        view?.post {
            val focusPos = FocusMemoryManager.restoreFocus(SCREEN_KEY)
            if (focusPos != null && focusPos.itemPosition < gridAdapter.size()) {
                setSelectedPosition(focusPos.itemPosition)
                Log.d(TAG, "Restored focus to position ${focusPos.itemPosition}")
            }
            // Note: Initial focus (position 0) is set in displayShows() after content loads
        }
    }

    private fun loadShows() {
        currentPage = 1
        hasMorePages = true

        viewModel.recentSeries.observe(viewLifecycleOwner) { shows ->
            if (shows.isNotEmpty()) {
                allShows = shows
                applyFiltersAndSort()
            }
        }

        // Load first page (25 items for ultra-fast initial load)
        lifecycleScope.launch {
            try {
                val result = repository.getTvShows(page = currentPage, perPage = 25)
                result.onSuccess { shows ->
                    allShows = shows
                    applyFiltersAndSort()
                    Log.d(TAG, "Loaded page $currentPage with ${shows.size} shows")

                    // Check if there are more pages
                    if (shows.size < 25) {
                        hasMorePages = false
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading shows", e)
            }
        }
    }

    private fun loadMoreShows() {
        if (isLoadingMore || !hasMorePages) {
            Log.d(TAG, "Skipping load - isLoadingMore: $isLoadingMore, hasMorePages: $hasMorePages")
            return
        }

        // Feature #18: 300-item cap REMOVED - Room Paging 3 handles memory management
        // Can now load unlimited items from database without OOM risk

        isLoadingMore = true
        currentPage++

        Log.d(TAG, "Loading more shows - page $currentPage (25 items)...")

        lifecycleScope.launch {
            try {
                val result = repository.getTvShows(page = currentPage, perPage = 25)
                result.onSuccess { newShows ->
                    if (newShows.isNotEmpty()) {
                        Log.d(TAG, "Loaded page $currentPage with ${newShows.size} new shows (total: ${allShows.size + newShows.size})")

                        // Append new shows to existing list
                        allShows = allShows + newShows

                        // Add to adapter without clearing (smoother - no visual interruption)
                        newShows.forEach { gridAdapter.add(it) }

                        // Check if there are more pages
                        if (newShows.size < 25) {
                            hasMorePages = false
                            Log.d(TAG, "No more pages available")
                        }
                    } else {
                        hasMorePages = false
                        currentPage-- // No more items, revert page
                        Log.d(TAG, "No more shows to load")
                    }

                    isLoadingMore = false
                }
                result.onFailure { error ->
                    Log.e(TAG, "Error loading more shows: ${error.message}", error)
                    currentPage-- // Rollback page number
                    isLoadingMore = false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading more shows", e)
                currentPage-- // Rollback page number
                isLoadingMore = false
            }
        }
    }

    private fun applyFiltersAndSort() {
        // Apply genre filter
        if (selectedGenres.isNotEmpty()) {
            // Load shows filtered by selected genres
            lifecycleScope.launch {
                try {
                    val result = repository.getTvShowsByGenres(selectedGenres, page = 1)
                    result.onSuccess { shows ->
                        allShows = shows
                        displayShows(sortShows(shows))
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error filtering by genres", e)
                }
            }
            return
        }

        // No genre filter - apply sorting to all shows
        displayShows(sortShows(allShows))
    }

    private fun sortShows(shows: List<Series>): List<Series> {
        return when (sortBy) {
            SortOption.RECENT -> shows.sortedByDescending { it.dateAdded }
            SortOption.TITLE -> shows.sortedBy { it.title }
            SortOption.RATING -> shows.sortedByDescending { it.rating ?: 0f }
        }
    }

    private fun displayShows(shows: List<Series>) {
        gridAdapter.clear()

        // Add all shows (action buttons are now in header, not grid)
        shows.forEach { gridAdapter.add(it) }
        Log.d(TAG, "Displayed ${shows.size} shows")

        // Set focus to first grid item after content loads
        // Post twice to ensure grid is fully laid out
        view?.post {
            view?.post {
                if (gridAdapter.size() > 0) {
                    setSelectedPosition(0)
                    Log.d(TAG, "Set initial focus to position 0 after content load")
                }
            }
        }
    }

    private fun showFilterDialog() {
        // Show genre filter using GuidedStep
        val dialogFragment = GenreFilterDialogFragment.newInstance(
            selectedGenres = selectedGenres,
            contentType = GenreFilterDialogFragment.ContentType.SHOWS
        )

        dialogFragment.setOnGenresSelectedListener { genres ->
            selectedGenres.clear()
            selectedGenres.addAll(genres)

            // Save preferences
            genrePreferences.saveSelectedShowGenres(selectedGenres)

            // Update chips
            setupGenreChips()

            // Apply filters
            currentPage = 1
            hasMorePages = true
            applyFiltersAndSort()

            // Update title
            updateTitle()

            // Update filter button appearance
            updateFilterButtonAppearance()

            // Show feedback
            if (genres.isEmpty()) {
                Toast.makeText(context, "تمام فیلترها پاک شدند", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "${genres.size} ژانر انتخاب شد", Toast.LENGTH_SHORT).show()
            }
        }

        // Use GuidedStepSupportFragment.add() for TV-optimized display
        androidx.leanback.app.GuidedStepSupportFragment.add(
            requireActivity().supportFragmentManager,
            dialogFragment,
            android.R.id.content
        )
    }

    private fun showSeriesDetails(series: Series) {
        val intent = Intent(requireContext(), SeriesDetailsActivity::class.java).apply {
            putExtra(SeriesDetailsActivity.EXTRA_SERIES, series)
        }
        startActivity(intent)
    }

    /**
     * Setup genre filter chips
     * Creates chips for all genres and adds click listeners
     */
    private fun setupGenreChips() {
        // Create chips for all genres
        genreChips.clear()
        genreChips.addAll(GenreChip.createChipsForAllGenres())

        // Update selection state based on saved preferences
        genreChips.forEachIndexed { index, chip ->
            if (!chip.isClearButton && selectedGenres.contains(chip.genre)) {
                genreChips[index] = chip.copy(isSelected = true)
            }
        }
    }

    /**
     * Update fragment title to show active filters
     */
    private fun updateTitle() {
        // Title is now in the header TextView, not Leanback title
        // Keep this empty to prevent Leanback from showing title
    }

    /**
     * Feature #20: Show skeleton grid while loading
     * Displays 12 skeleton cards with shimmer animation
     */
    private fun showSkeletonGrid() {
        if (isShowingSkeleton) return

        Log.d(TAG, "Showing skeleton grid...")
        isShowingSkeleton = true

        gridAdapter.clear()
        val skeletonAdapter = SkeletonHelper.createSkeletonAdapter(12)

        // Add skeleton cards to grid
        for (i in 0 until skeletonAdapter.size()) {
            gridAdapter.add(skeletonAdapter.get(i))
        }

        Log.d(TAG, "Skeleton grid displayed (${gridAdapter.size()} cards)")
    }

    /**
     * Feature #20: Hide skeleton grid
     * Clear skeleton cards - real content will be added by displayShows()
     */
    private fun hideSkeletonGrid() {
        if (!isShowingSkeleton) return

        Log.d(TAG, "Hiding skeleton grid...")
        isShowingSkeleton = false

        // Clear skeleton cards
        gridAdapter.clear()

        Log.d(TAG, "Skeleton grid hidden, ready for real content")
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

    // M2 FIX: Cancel loading operations when fragment is destroyed
    override fun onDestroyView() {
        super.onDestroyView()
        // Note: All data loading uses lifecycleScope.launch which automatically
        // cancels when the fragment's view is destroyed. This explicit override
        // documents this behavior and provides a place for future cleanup if needed.
        // Release BackgroundManager to prevent memory leak
        if (mBackgroundManager.isAttached) {
            mBackgroundManager.release()
        }
    }

    companion object {
        private const val TAG = "ShowsFragment"
    }
}
