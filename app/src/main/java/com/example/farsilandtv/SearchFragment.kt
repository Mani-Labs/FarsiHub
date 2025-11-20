package com.example.farsilandtv

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.view.ContextThemeWrapper
import androidx.leanback.app.SearchSupportFragment
import androidx.leanback.widget.*
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.farsilandtv.data.models.Movie
import com.example.farsilandtv.data.models.Series
import com.example.farsilandtv.data.repository.ContentRepository
import com.example.farsilandtv.data.repository.WatchlistRepository
import com.example.farsilandtv.ui.ContentCardPresenter
import com.example.farsilandtv.ui.viewmodel.MainViewModel
import kotlinx.coroutines.launch

/**
 * Search fragment with debounced search and filtering
 */
class SearchFragment : SearchSupportFragment(), SearchSupportFragment.SearchResultProvider {

    private lateinit var repository: ContentRepository
    private lateinit var watchlistRepo: WatchlistRepository
    private lateinit var viewModel: MainViewModel
    private lateinit var rowsAdapter: ArrayObjectAdapter

    private val searchHandler = Handler(Looper.getMainLooper())
    private var searchRunnable: Runnable? = null

    private var currentMovies: List<Movie> = emptyList()
    private var currentSeries: List<Series> = emptyList()
    private var currentFilter: FilterType = FilterType.ALL

    enum class FilterType { ALL, MOVIES, SERIES }

    companion object {
        private const val TAG = "SearchFragment"
        private const val SEARCH_DELAY_MS = 300L
        private const val MIN_SEARCH_LENGTH = 2
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(TAG, "SearchFragment onCreate")

        repository = ContentRepository.getInstance(requireContext())
        watchlistRepo = WatchlistRepository(requireContext())
        viewModel = ViewModelProvider(this)[MainViewModel::class.java]

        rowsAdapter = ArrayObjectAdapter(ListRowPresenter())
        setSearchResultProvider(this)

        setOnItemViewClickedListener { _, item, _, _ ->
            when (item) {
                is Movie -> showMovieDetails(item)
                is Series -> showSeriesDetails(item)
            }
        }
    }

    override fun onQueryTextChange(newQuery: String?): Boolean {
        searchRunnable?.let { searchHandler.removeCallbacks(it) }

        if (newQuery.isNullOrEmpty() || newQuery.length < MIN_SEARCH_LENGTH) {
            rowsAdapter.clear()
            return true
        }

        searchRunnable = Runnable {
            performSearch(newQuery)
        }
        searchHandler.postDelayed(searchRunnable!!, SEARCH_DELAY_MS)

        return true
    }

    override fun onQueryTextSubmit(query: String?): Boolean {
        if (!query.isNullOrEmpty()) {
            performSearch(query)
        }
        return true
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Clean up handler to prevent memory leak
        searchRunnable?.let { searchHandler.removeCallbacks(it) }
        searchRunnable = null
    }

    private fun performSearch(query: String) {
        Log.d(TAG, "Searching for: $query")

        lifecycleScope.launch {
            try {
                val searchResult = repository.search(query)
                searchResult.onSuccess { results ->
                    currentMovies = results.filterIsInstance<Movie>()
                    currentSeries = results.filterIsInstance<Series>()
                    applyFilter()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Search error", e)
            }
        }
    }

    private fun applyFilter() {
        rowsAdapter.clear()

        val moviesToShow = if (currentFilter == FilterType.SERIES) emptyList() else currentMovies
        val seriesToShow = if (currentFilter == FilterType.MOVIES) emptyList() else currentSeries

        if (moviesToShow.isNotEmpty()) {
            val moviesAdapter = ArrayObjectAdapter(SearchResultPresenter())
            moviesToShow.forEach { moviesAdapter.add(it) }
            val moviesHeader = HeaderItem(0, "Movies (${moviesToShow.size})")
            rowsAdapter.add(ListRow(moviesHeader, moviesAdapter))
        }

        if (seriesToShow.isNotEmpty()) {
            val seriesAdapter = ArrayObjectAdapter(SearchResultPresenter())
            seriesToShow.forEach { seriesAdapter.add(it) }
            val seriesHeader = HeaderItem(1, "TV Shows (${seriesToShow.size})")
            rowsAdapter.add(ListRow(seriesHeader, seriesAdapter))
        }

        if (rowsAdapter.size() == 0) {
            Log.d(TAG, "No results found")
        }
    }

    override fun getResultsAdapter(): ObjectAdapter {
        return rowsAdapter
    }

    private fun showMovieDetails(movie: Movie) {
        val intent = Intent(requireContext(), DetailsActivity::class.java).apply {
            putExtra(DetailsActivity.EXTRA_MOVIE, movie)
        }
        startActivity(intent)
    }

    private fun showSeriesDetails(series: Series) {
        val intent = Intent(requireContext(), SeriesDetailsActivity::class.java).apply {
            putExtra(SeriesDetailsActivity.EXTRA_SERIES, series)
        }
        startActivity(intent)
    }

    /**
     * Presenter for search results with source badges and long-press to add to watchlist
     */
    private inner class SearchResultPresenter : Presenter() {
        private val contentPresenter = ContentCardPresenter(context)

        override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
            return contentPresenter.onCreateViewHolder(parent)
        }

        override fun onBindViewHolder(viewHolder: ViewHolder, item: Any) {
            // Bind normally using ContentCardPresenter (which includes source badges)
            contentPresenter.onBindViewHolder(viewHolder, item)

            // Add long-press listener to add to watchlist
            viewHolder.view.setOnLongClickListener {
                showAddToWatchlistDialog(item)
                true
            }
        }

        override fun onUnbindViewHolder(viewHolder: ViewHolder) {
            contentPresenter.onUnbindViewHolder(viewHolder)
        }
    }

    /**
     * Show dialog to add item to watchlist
     */
    private fun showAddToWatchlistDialog(item: Any) {
        val title = when (item) {
            is Movie -> item.title
            is Series -> item.title
            else -> return
        }

        // Use ContextThemeWrapper for AppCompat theme support
        val themedContext = ContextThemeWrapper(requireContext(), androidx.appcompat.R.style.Theme_AppCompat_Dialog)

        AlertDialog.Builder(themedContext)
            .setTitle("Add to Watchlist")
            .setMessage("Add \"$title\" to your watchlist?")
            .setPositiveButton("Add") { _, _ ->
                addToWatchlist(item)
            }
            .setNegativeButton("Cancel", null)
            .show()
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
                        Toast.makeText(requireContext(), "Added to watchlist", Toast.LENGTH_SHORT).show()
                    }
                    is Series -> {
                        watchlistRepo.addSeriesToMonitored(item)
                        Log.d(TAG, "Added series ${item.id} to monitored")
                        Toast.makeText(requireContext(), "Added to watchlist", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error adding to watchlist", e)
                Toast.makeText(requireContext(), "Error adding to watchlist", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
