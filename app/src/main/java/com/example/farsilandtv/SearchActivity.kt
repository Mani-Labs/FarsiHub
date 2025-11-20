package com.example.farsilandtv

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.FragmentActivity
import androidx.leanback.app.SearchSupportFragment
import androidx.leanback.widget.*
import com.example.farsilandtv.data.models.Movie
import com.example.farsilandtv.data.models.Series
import com.example.farsilandtv.data.repository.ContentRepository
import com.example.farsilandtv.data.repository.SearchRepository
import com.example.farsilandtv.ui.ContentCardPresenter
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest

/**
 * SearchActivity with Leanback SearchFragment
 * Supports Persian text, search-as-you-type, and search history
 *
 * Back navigation: Returns to previous screen (not home/exit)
 */
class SearchActivity : FragmentActivity() {

    companion object {
        private const val TAG = "SearchActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search)

        // Setup back press handler
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                Log.d(TAG, "Back pressed from search")
                finish()
            }
        })

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.search_fragment_container, SearchFragment())
                .commit()
        }
    }

    /**
     * EXTERNAL AUDIT FIX #4: Handle new search intents when activity is already open
     *
     * When launchMode="singleTop", Android delivers new voice search queries via onNewIntent()
     * instead of creating a new activity instance. Without this override, new search queries
     * are ignored and the user sees the old search results.
     *
     * This fix ensures voice search works correctly when the SearchActivity is already open.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        Log.d(TAG, "New search intent received")

        // Update activity intent to the new one
        setIntent(intent)

        // Extract search query from new intent
        val query = intent.getStringExtra(android.app.SearchManager.QUERY)

        if (!query.isNullOrEmpty()) {
            Log.d(TAG, "Processing new voice search query: $query")

            // Find the SearchFragment and trigger new search
            val fragment = supportFragmentManager.findFragmentById(R.id.search_fragment_container)
            if (fragment is SearchFragment) {
                fragment.setSearchQuery(query, true)
            }
        }
    }

    class SearchFragment : SearchSupportFragment(), SearchSupportFragment.SearchResultProvider {

        private lateinit var contentRepository: ContentRepository
        private lateinit var searchRepository: SearchRepository
        private val searchScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        private val searchHandler = Handler(Looper.getMainLooper())

        private lateinit var rowsAdapter: ArrayObjectAdapter
        private var searchRunnable: Runnable? = null

        // Store current search results for filtering
        private var currentMovies: List<Movie> = emptyList()
        private var currentSeries: List<Series> = emptyList()
        private var currentQuery: String = ""
        private var currentFilter: FilterType = FilterType.ALL

        // Store recent searches and suggestions
        private var recentSearches: List<String> = emptyList()
        private var currentSuggestions: List<String> = emptyList()

        enum class FilterType {
            ALL, MOVIES, SERIES
        }

        companion object {
            private const val TAG = "SearchFragment"
            private const val SEARCH_DELAY_MS = 300L
            private const val MIN_SEARCH_LENGTH = 2
        }

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)

            contentRepository = ContentRepository.getInstance(requireContext())
            searchRepository = SearchRepository(requireContext())
            rowsAdapter = ArrayObjectAdapter(ListRowPresenter())
            setSearchResultProvider(this)

            // Set search title
            setTitle(getString(R.string.search_title))

            // Observe recent searches from database
            observeRecentSearches()

            // Set search query listener with debouncing
            setOnItemViewClickedListener { _, item, _, _ ->
                when (item) {
                    is Movie -> openMovieDetails(item)
                    is Series -> openSeriesDetails(item)
                    is String -> {
                        // Handle different types of string items
                        when {
                            item == "Clear History" -> {
                                clearSearchHistory()
                            }
                            item.startsWith("All") -> {
                                currentFilter = FilterType.ALL
                                applyFilter()
                            }
                            item.startsWith("Movies") -> {
                                currentFilter = FilterType.MOVIES
                                applyFilter()
                            }
                            item.startsWith("Series") -> {
                                currentFilter = FilterType.SERIES
                                applyFilter()
                            }
                            else -> {
                                // Clicked on recent search or suggestion - perform search
                                setSearchQuery(item, true)
                            }
                        }
                    }
                }
            }
        }

        override fun onQueryTextChange(newQuery: String?): Boolean {
            Log.d(TAG, "Query changed: $newQuery")

            // Cancel previous search
            searchRunnable?.let { searchHandler.removeCallbacks(it) }

            if (newQuery.isNullOrEmpty()) {
                // Show recent searches when query is empty
                displayRecentSearches()
                return true
            }

            if (newQuery.length < MIN_SEARCH_LENGTH) {
                // Still too short to search, but show suggestions if available
                observeSuggestions(newQuery)
                return true
            }

            // Show suggestions while typing
            observeSuggestions(newQuery)

            // Debounce search with 300ms delay
            searchRunnable = Runnable {
                performSearch(newQuery)
            }

            searchHandler.postDelayed(searchRunnable!!, SEARCH_DELAY_MS)
            return true
        }

        override fun onQueryTextSubmit(query: String?): Boolean {
            Log.d(TAG, "Query submitted: $query")

            if (!query.isNullOrEmpty() && query.length >= MIN_SEARCH_LENGTH) {
                performSearch(query)
                // Save to search history via repository
                searchScope.launch {
                    searchRepository.saveSearch(query)
                }
            }
            return true
        }

        private fun performSearch(query: String) {
            Log.d(TAG, "Performing search for: $query")

            // Save search to history
            searchScope.launch {
                searchRepository.saveSearch(query)
            }

            searchScope.launch {
                try {
                    val result = withContext(Dispatchers.IO) {
                        contentRepository.search(query)
                    }

                    result.onSuccess { items ->
                        displaySearchResults(query, items)
                    }.onFailure { error ->
                        Log.e(TAG, "Search failed", error)
                        displayEmptyState()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Search failed", e)
                    displayEmptyState()
                }
            }
        }

        private fun displaySearchResults(query: String, results: List<Any>) {
            // Store results for filtering
            currentQuery = query
            currentMovies = results.filterIsInstance<Movie>()
            currentSeries = results.filterIsInstance<Series>()

            // Apply current filter
            applyFilter()
        }

        private fun applyFilter() {
            rowsAdapter.clear()

            val moviesToShow = if (currentFilter == FilterType.SERIES) emptyList() else currentMovies
            val seriesToShow = if (currentFilter == FilterType.MOVIES) emptyList() else currentSeries

            if (moviesToShow.isEmpty() && seriesToShow.isEmpty()) {
                displayEmptyState()
                return
            }

            val cardPresenter = com.example.farsilandtv.ui.ContentCardPresenter(requireContext())

            // Add filter buttons row at top
            addFilterRow()

            // Add Movies section if any
            if (moviesToShow.isNotEmpty()) {
                val moviesAdapter = ArrayObjectAdapter(cardPresenter)
                moviesToShow.forEach { moviesAdapter.add(it) }

                val moviesHeader = HeaderItem(0, "Movies (${moviesToShow.size})")
                rowsAdapter.add(ListRow(moviesHeader, moviesAdapter))
            }

            // Add Series section if any
            if (seriesToShow.isNotEmpty()) {
                val seriesAdapter = ArrayObjectAdapter(cardPresenter)
                seriesToShow.forEach { seriesAdapter.add(it) }

                val seriesHeader = HeaderItem(1, "TV Series (${seriesToShow.size})")
                rowsAdapter.add(ListRow(seriesHeader, seriesAdapter))
            }

            Log.d(TAG, "Displayed ${moviesToShow.size + seriesToShow.size} results for '$currentQuery' with filter $currentFilter")
        }

        private fun addFilterRow() {
            val filterPresenter = FilterButtonPresenter()
            val filterAdapter = ArrayObjectAdapter(filterPresenter)

            // Add filter buttons
            filterAdapter.add("All (${currentMovies.size + currentSeries.size})")
            filterAdapter.add("Movies (${currentMovies.size})")
            filterAdapter.add("Series (${currentSeries.size})")

            val filterHeader = HeaderItem(999, "Filter")
            rowsAdapter.add(ListRow(filterHeader, filterAdapter))
        }

        private inner class FilterButtonPresenter : Presenter() {
            override fun onCreateViewHolder(parent: android.view.ViewGroup): ViewHolder {
                val textView = android.widget.TextView(parent.context).apply {
                    layoutParams = android.view.ViewGroup.LayoutParams(250, 80)
                    gravity = android.view.Gravity.CENTER
                    textSize = 16f
                    setTextColor(android.graphics.Color.WHITE)
                    setBackgroundColor(android.graphics.Color.parseColor("#404040"))
                    setPadding(24, 16, 24, 16)
                    isFocusable = true
                    isFocusableInTouchMode = true
                }
                return ViewHolder(textView)
            }

            override fun onBindViewHolder(viewHolder: ViewHolder, item: Any) {
                val textView = viewHolder.view as android.widget.TextView
                val itemText = item as? String ?: return
                textView.text = itemText

                // Highlight selected filter
                val isSelected = when {
                    itemText.startsWith("All") && currentFilter == FilterType.ALL -> true
                    itemText.startsWith("Movies") && currentFilter == FilterType.MOVIES -> true
                    itemText.startsWith("Series") && currentFilter == FilterType.SERIES -> true
                    else -> false
                }

                if (isSelected) {
                    textView.setBackgroundColor(android.graphics.Color.parseColor("#FF5722"))
                } else {
                    textView.setBackgroundColor(android.graphics.Color.parseColor("#404040"))
                }
            }

            override fun onUnbindViewHolder(viewHolder: ViewHolder) {}
        }

        private fun displayEmptyState() {
            // Clear results and show empty state
            rowsAdapter.clear()

            // Could add a custom empty state row here
            val emptyAdapter = ArrayObjectAdapter(object : Presenter() {
                override fun onCreateViewHolder(parent: android.view.ViewGroup): ViewHolder {
                    val textView = android.widget.TextView(parent.context).apply {
                        text = "No results found"
                        textSize = 24f
                        setPadding(48, 48, 48, 48)
                        setTextColor(android.graphics.Color.WHITE)
                    }
                    return ViewHolder(textView)
                }

                override fun onBindViewHolder(viewHolder: ViewHolder, item: Any) {}
                override fun onUnbindViewHolder(viewHolder: ViewHolder) {}
            })

            emptyAdapter.add(Any())
            rowsAdapter.add(ListRow(HeaderItem(""), emptyAdapter))
        }

        /**
         * Observe recent searches from database
         */
        private fun observeRecentSearches() {
            searchScope.launch {
                searchRepository.getRecentSearches(10).collectLatest { searches ->
                    recentSearches = searches
                    Log.d(TAG, "Recent searches updated: ${searches.size} items")

                    // If no active search, display recent searches
                    if (currentQuery.isEmpty()) {
                        displayRecentSearches()
                    }
                }
            }
        }

        /**
         * Observe auto-complete suggestions based on current input
         */
        private fun observeSuggestions(prefix: String) {
            searchScope.launch {
                searchRepository.getSuggestions(prefix).collectLatest { suggestions ->
                    currentSuggestions = suggestions
                    Log.d(TAG, "Suggestions for '$prefix': ${suggestions.size} items")
                    displaySuggestions()
                }
            }
        }

        /**
         * Display recent searches as clickable chips
         */
        private fun displayRecentSearches() {
            rowsAdapter.clear()

            if (recentSearches.isEmpty()) {
                // Show helpful message
                val hintAdapter = ArrayObjectAdapter(object : Presenter() {
                    override fun onCreateViewHolder(parent: android.view.ViewGroup): ViewHolder {
                        val textView = android.widget.TextView(parent.context).apply {
                            text = "Start typing to search movies and TV shows"
                            textSize = 20f
                            setPadding(48, 48, 48, 48)
                            setTextColor(android.graphics.Color.LTGRAY)
                        }
                        return ViewHolder(textView)
                    }

                    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any) {}
                    override fun onUnbindViewHolder(viewHolder: ViewHolder) {}
                })

                hintAdapter.add(Any())
                rowsAdapter.add(ListRow(HeaderItem(""), hintAdapter))
                return
            }

            // Add recent searches row
            val recentSearchesAdapter = ArrayObjectAdapter(SearchChipPresenter())
            recentSearches.forEach { query ->
                recentSearchesAdapter.add(query)
            }

            // Add "Clear History" button
            recentSearchesAdapter.add("Clear History")

            val header = HeaderItem(1000, "Recent Searches")
            rowsAdapter.add(ListRow(header, recentSearchesAdapter))
        }

        /**
         * Display auto-complete suggestions while typing
         */
        private fun displaySuggestions() {
            // Don't show suggestions if we have actual search results
            if (currentMovies.isNotEmpty() || currentSeries.isNotEmpty()) {
                return
            }

            rowsAdapter.clear()

            if (currentSuggestions.isEmpty()) {
                return
            }

            // Add suggestions row
            val suggestionsAdapter = ArrayObjectAdapter(SearchChipPresenter())
            currentSuggestions.forEach { suggestion ->
                suggestionsAdapter.add(suggestion)
            }

            val header = HeaderItem(2000, "Suggestions")
            rowsAdapter.add(ListRow(header, suggestionsAdapter))
        }

        /**
         * Clear all search history with confirmation
         */
        private fun clearSearchHistory() {
            searchScope.launch {
                searchRepository.clearHistory()
                Log.d(TAG, "Search history cleared")
                // Recent searches will be updated automatically via Flow
            }
        }

        /**
         * Presenter for search chips (recent searches and suggestions)
         * Creates focusable buttons styled as chips
         */
        private inner class SearchChipPresenter : Presenter() {
            override fun onCreateViewHolder(parent: android.view.ViewGroup): ViewHolder {
                val textView = android.widget.TextView(parent.context).apply {
                    layoutParams = android.view.ViewGroup.LayoutParams(
                        android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                        100
                    )
                    gravity = android.view.Gravity.CENTER
                    textSize = 18f
                    setTextColor(android.graphics.Color.WHITE)
                    setPadding(40, 20, 40, 20)
                    isFocusable = true
                    isFocusableInTouchMode = true

                    // Styling
                    setBackgroundColor(android.graphics.Color.parseColor("#404040"))

                    // Focus change listener for visual feedback
                    setOnFocusChangeListener { view, hasFocus ->
                        if (hasFocus) {
                            view.setBackgroundColor(android.graphics.Color.parseColor("#FF5722"))
                            (view as android.widget.TextView).setTextColor(android.graphics.Color.WHITE)
                        } else {
                            val textView = view as android.widget.TextView
                            // Check if this is the "Clear History" button
                            if (textView.text == "Clear History") {
                                view.setBackgroundColor(android.graphics.Color.parseColor("#D32F2F"))
                            } else {
                                view.setBackgroundColor(android.graphics.Color.parseColor("#404040"))
                            }
                            textView.setTextColor(android.graphics.Color.WHITE)
                        }
                    }
                }
                return ViewHolder(textView)
            }

            override fun onBindViewHolder(viewHolder: ViewHolder, item: Any) {
                val textView = viewHolder.view as android.widget.TextView
                val text = item as? String ?: return

                textView.text = text

                // Style "Clear History" button differently
                if (text == "Clear History") {
                    textView.setBackgroundColor(android.graphics.Color.parseColor("#D32F2F"))
                    textView.setTextColor(android.graphics.Color.WHITE)
                } else {
                    textView.setBackgroundColor(android.graphics.Color.parseColor("#404040"))
                    textView.setTextColor(android.graphics.Color.WHITE)
                }
            }

            override fun onUnbindViewHolder(viewHolder: ViewHolder) {}
        }

        private fun openMovieDetails(movie: Movie) {
            val intent = Intent(requireContext(), DetailsActivity::class.java).apply {
                putExtra(DetailsActivity.EXTRA_MOVIE, movie)
            }
            startActivity(intent)
        }

        private fun openSeriesDetails(series: Series) {
            val intent = Intent(requireContext(), SeriesDetailsActivity::class.java).apply {
                putExtra(SeriesDetailsActivity.EXTRA_SERIES, series)
            }
            startActivity(intent)
        }

        override fun getResultsAdapter(): ObjectAdapter {
            return rowsAdapter
        }

        override fun onDestroy() {
            super.onDestroy()
            searchScope.cancel()
            searchRunnable?.let { searchHandler.removeCallbacks(it) }
        }
    }
}
