package com.example.farsilandtv

import android.os.Bundle
import androidx.leanback.app.GuidedStepSupportFragment
import androidx.leanback.widget.GuidanceStylist
import androidx.leanback.widget.GuidedAction
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.asLiveData
import androidx.lifecycle.lifecycleScope
import com.example.farsilandtv.data.database.ContentDatabase
import com.example.farsilandtv.data.repository.WatchlistRepository
import com.example.farsilandtv.ui.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Statistics fragment - shows cache statistics and loading progress
 */
class StatsFragment : GuidedStepSupportFragment() {

    private lateinit var viewModel: MainViewModel
    private lateinit var watchlistRepo: WatchlistRepository
    private lateinit var contentDatabase: ContentDatabase

    private var moviesCount = 0
    private var seriesCount = 0
    private var episodesCount = 0
    private var continueWatchingCount = 0
    private var watchlistMoviesCount = 0
    private var watchlistSeriesCount = 0
    private var isLoading = false

    companion object {
        private const val ACTION_MOVIES = 1L
        private const val ACTION_SERIES = 2L
        private const val ACTION_EPISODES = 3L
        private const val ACTION_CONTINUE_WATCHING = 4L
        private const val ACTION_WATCHLIST = 5L
        private const val ACTION_LOADING = 6L
        private const val ACTION_REFRESH = 7L
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewModel = ViewModelProvider(this)[MainViewModel::class.java]
        watchlistRepo = WatchlistRepository(requireContext())
        contentDatabase = ContentDatabase.getDatabase(requireContext())

        observeViewModel()
        loadStats()
        loadCachedCounts()
    }

    override fun onCreateGuidance(savedInstanceState: Bundle?): GuidanceStylist.Guidance {
        return GuidanceStylist.Guidance(
            "Cache Statistics",
            "Cached content information",
            "Farsiland TV",
            null
        )
    }

    override fun onCreateActions(actions: MutableList<GuidedAction>, savedInstanceState: Bundle?) {
        updateActions(actions)
    }

    private fun updateActions(actions: MutableList<GuidedAction>) {
        actions.clear()

        // Loading status
        if (isLoading) {
            actions.add(GuidedAction.Builder(requireContext())
                .id(ACTION_LOADING)
                .title("Status")
                .description("⏳ Loading content from cache...")
                .enabled(false)
                .build())
        }

        // Movies count
        actions.add(GuidedAction.Builder(requireContext())
            .id(ACTION_MOVIES)
            .title("Cached Movies")
            .description("$moviesCount movies in database")
            .enabled(false)
            .build())

        // Series count
        actions.add(GuidedAction.Builder(requireContext())
            .id(ACTION_SERIES)
            .title("Cached Shows")
            .description("$seriesCount shows in database")
            .enabled(false)
            .build())

        // Episodes count
        actions.add(GuidedAction.Builder(requireContext())
            .id(ACTION_EPISODES)
            .title("Cached Episodes")
            .description("$episodesCount episodes in database")
            .enabled(false)
            .build())

        // Continue watching count
        actions.add(GuidedAction.Builder(requireContext())
            .id(ACTION_CONTINUE_WATCHING)
            .title("Continue Watching")
            .description("$continueWatchingCount items in progress")
            .enabled(false)
            .build())

        // Watchlist count
        val totalWatchlist = watchlistMoviesCount + watchlistSeriesCount
        actions.add(GuidedAction.Builder(requireContext())
            .id(ACTION_WATCHLIST)
            .title("Watchlist")
            .description("$totalWatchlist items ($watchlistMoviesCount movies, $watchlistSeriesCount shows)")
            .enabled(false)
            .build())

        // Refresh button
        actions.add(GuidedAction.Builder(requireContext())
            .id(ACTION_REFRESH)
            .title("Refresh Statistics")
            .description("Update cache information")
            .build())
    }

    private fun observeViewModel() {
        // Keep loading state from ViewModel (if needed for API operations)
        viewModel.isLoading.observe(this) { loading ->
            // isLoading now managed by loadCachedCounts()
        }
    }

    private fun loadStats() {
        // Observe continue watching
        watchlistRepo.getContinueWatching().asLiveData().observe(this) { items ->
            continueWatchingCount = items.size
            refreshActions()
        }

        // Observe watchlist movies
        watchlistRepo.getAllWatchlistedMovies().asLiveData().observe(this) { movies ->
            watchlistMoviesCount = movies.size
            refreshActions()
        }

        // Observe monitored series
        watchlistRepo.getAllMonitoredSeries().asLiveData().observe(this) { series ->
            watchlistSeriesCount = series.size
            refreshActions()
        }
    }

    /**
     * Load cached content counts from ContentDatabase
     */
    private fun loadCachedCounts() {
        lifecycleScope.launch {
            try {
                isLoading = true
                refreshActions()

                val counts = withContext(Dispatchers.IO) {
                    Triple(
                        contentDatabase.movieDao().getMovieCount(),
                        contentDatabase.seriesDao().getSeriesCount(),
                        contentDatabase.episodeDao().getEpisodeCount()
                    )
                }

                moviesCount = counts.first
                seriesCount = counts.second
                episodesCount = counts.third
                isLoading = false
                refreshActions()
            } catch (e: Exception) {
                isLoading = false
                refreshActions()
            }
        }
    }

    private fun refreshActions() {
        val actions = mutableListOf<GuidedAction>()
        updateActions(actions)
        setActions(actions)
    }

    override fun onGuidedActionClicked(action: GuidedAction) {
        when (action.id) {
            ACTION_REFRESH -> {
                loadStats()
                loadCachedCounts()
            }
        }
    }
}
