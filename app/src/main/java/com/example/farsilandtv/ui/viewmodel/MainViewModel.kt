package com.example.farsilandtv.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.example.farsilandtv.data.models.Movie
import com.example.farsilandtv.data.models.Series
import com.example.farsilandtv.data.models.Genre
import com.example.farsilandtv.data.models.FeaturedContent
import com.example.farsilandtv.data.repository.ContentRepository
import com.example.farsilandtv.utils.ErrorHandler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.async

/**
 * ViewModel for main browse screen
 * Loads movies, TV shows, and genres from Farsiland.com
 * NEW: Uses AndroidViewModel to access Application context for database
 *
 * CRITICAL FIX: Lazy repository initialization to prevent ANR
 * - Repository init triggers database creation and migrations
 * - MIGRATION_9_10 runs heavy DELETE with GROUP BY on main thread
 * - Lazy init defers until first use in viewModelScope (background thread)
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository by lazy {
        ContentRepository.getInstance(application.applicationContext)
    }

    // Feature #18: Paging 3 - Unlimited scrolling (replaces 300-item caps)
    // These flows are database-backed and can handle unlimited items efficiently
    val movies: Flow<PagingData<Movie>> = repository.getMoviesPaged()
        .cachedIn(viewModelScope)

    val series: Flow<PagingData<Series>> = repository.getSeriesPaged()
        .cachedIn(viewModelScope)

    val episodes: Flow<PagingData<com.example.farsilandtv.data.models.Episode>> = repository.getEpisodesPaged()
        .cachedIn(viewModelScope)

    // Legacy LiveData for compatibility (used by existing code)
    private val _recentMovies = MutableLiveData<List<Movie>>()
    val recentMovies: LiveData<List<Movie>> = _recentMovies

    private val _recentSeries = MutableLiveData<List<Series>>()
    val recentSeries: LiveData<List<Series>> = _recentSeries

    private val _recentEpisodes = MutableLiveData<List<com.example.farsilandtv.data.models.Episode>>()
    val recentEpisodes: LiveData<List<com.example.farsilandtv.data.models.Episode>> = _recentEpisodes

    // Legacy pagination state (kept for compatibility with API-based methods)
    private var moviesPage = 1
    private var seriesPage = 1
    private var episodesPage = 1
    private var isLoadingMoreMovies = false
    private var isLoadingMoreSeries = false
    private var isLoadingMoreEpisodes = false

    // LiveData for genres
    private val _genres = MutableLiveData<List<Genre>>()
    val genres: LiveData<List<Genre>> = _genres

    // LiveData for featured content carousel
    private val _featuredContent = MutableLiveData<List<FeaturedContent>>()
    val featuredContent: LiveData<List<FeaturedContent>> = _featuredContent

    // Loading state
    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    // Error state
    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    // Removed auto-load from init to prevent race condition
    // Fragment will call loadContent() after observers are ready

    init {
        // Observe sync completion and auto-reload data
        viewModelScope.launch {
            repository.observeSyncCompletion().collect { _ ->
                Log.d(TAG, "Sync completed - reloading content automatically")
                // Don't call loadContent() as it triggers isLoading which clears watchlist rows
                // Instead, just refresh the main content data
                refreshContentWithoutLoadingState()
            }
        }
    }

    /**
     * Load all content (movies, series, genres) with retry logic
     */
    fun loadContent() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            try {
                // Load with retry mechanism
                ErrorHandler.retryWithExponentialBackoff(times = 3) {
                    // Load genres first
                    loadGenres()

                    // Load featured content for carousel
                    loadFeaturedContent()

                    // Load movies, series, and episodes in ACTUAL parallel (using async/await)
                    val moviesDeferred = async { loadMovies() }
                    val seriesDeferred = async { loadSeries() }
                    val episodesDeferred = async { loadEpisodes() }

                    // Wait for all to complete
                    moviesDeferred.await()
                    seriesDeferred.await()
                    episodesDeferred.await()
                }

            } catch (e: Exception) {
                _error.value = ErrorHandler.getErrorMessage(e)
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Refresh content without triggering loading state
     * Used after sync completion to avoid clearing watchlist rows
     */
    private fun refreshContentWithoutLoadingState() {
        viewModelScope.launch {
            try {
                // Clear cache to get fresh data
                com.example.farsilandtv.data.api.RetrofitClient.clearCache()
                repository.clearCache()
                Log.d(TAG, "refreshContentWithoutLoadingState: Cleared caches")

                // Reload content WITHOUT setting isLoading (prevents row clearing)
                ErrorHandler.retryWithExponentialBackoff(times = 3) {
                    loadGenres()
                    loadFeaturedContent()

                    // Load in parallel
                    val moviesDeferred = async { loadMovies() }
                    val seriesDeferred = async { loadSeries() }
                    val episodesDeferred = async { loadEpisodes() }

                    moviesDeferred.await()
                    seriesDeferred.await()
                    episodesDeferred.await()
                }
            } catch (e: Exception) {
                _error.value = ErrorHandler.getErrorMessage(e)
                e.printStackTrace()
            }
        }
    }

    /**
     * Force refresh content (clears cache and reloads) with retry logic
     */
    fun forceRefresh() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            try {
                // SYNC FIX: Clear both HTTP cache AND repository cache
                com.example.farsilandtv.data.api.RetrofitClient.clearCache()
                repository.clearCache()
                Log.d(TAG, "forceRefresh: Cleared all caches")

                // Reload with retry mechanism
                ErrorHandler.retryWithExponentialBackoff(times = 3) {
                    loadGenres()
                    loadFeaturedContent()

                    // Load in parallel
                    val moviesDeferred = async { loadMovies() }
                    val seriesDeferred = async { loadSeries() }
                    val episodesDeferred = async { loadEpisodes() }

                    moviesDeferred.await()
                    seriesDeferred.await()
                    episodesDeferred.await()
                }

            } catch (e: Exception) {
                _error.value = ErrorHandler.getErrorMessage(e)
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Load recent movies (initial load)
     */
    private suspend fun loadMovies() {
        moviesPage = 1
        val result = repository.getMovies(page = 1, perPage = 30)
        result.onSuccess { movies ->
            _recentMovies.postValue(movies)
        }.onFailure { e ->
            _error.postValue(ErrorHandler.getErrorMessage(e))
        }
    }

    /**
     * Load more movies (pagination)
     * DEPRECATED: Use `movies` Flow<PagingData<Movie>> instead for unlimited scrolling
     * This method is kept for backwards compatibility with API-based genre filtering
     */
    @Deprecated("Use movies Flow<PagingData<Movie>> for unlimited scrolling")
    fun loadMoreMovies() {
        if (isLoadingMoreMovies) {
            Log.d("MainViewModel", "Already loading more movies, skipping...")
            return
        }

        // Feature #18: 300-item cap REMOVED - now using Paging 3 for unlimited items
        // Keeping this method only for backwards compatibility with genre filtering

        viewModelScope.launch {
            isLoadingMoreMovies = true
            moviesPage++
            Log.d("MainViewModel", "Loading movies page $moviesPage...")

            val result = repository.getMovies(page = moviesPage, perPage = 30)
            result.onSuccess { newMovies ->
                if (newMovies.isNotEmpty()) {
                    val currentMovies = _recentMovies.value ?: emptyList()
                    _recentMovies.postValue(currentMovies + newMovies)
                    Log.d("MainViewModel", "Loaded ${newMovies.size} more movies (total: ${currentMovies.size + newMovies.size})")
                } else {
                    Log.d("MainViewModel", "No more movies to load")
                    moviesPage-- // No more items, revert page
                }
            }.onFailure { e ->
                moviesPage-- // Revert page on failure
                Log.e("MainViewModel", "Failed to load more movies: ${e.message}")
            }

            isLoadingMoreMovies = false
        }
    }

    /**
     * Load recent TV series (initial load)
     */
    private suspend fun loadSeries() {
        seriesPage = 1
        val result = repository.getTvShows(page = 1, perPage = 30)
        result.onSuccess { series ->
            _recentSeries.postValue(series)
        }.onFailure { e ->
            _error.postValue(ErrorHandler.getErrorMessage(e))
        }
    }

    /**
     * Load more TV series (pagination)
     * DEPRECATED: Use `series` Flow<PagingData<Series>> instead for unlimited scrolling
     * This method is kept for backwards compatibility with API-based genre filtering
     */
    @Deprecated("Use series Flow<PagingData<Series>> for unlimited scrolling")
    fun loadMoreSeries() {
        if (isLoadingMoreSeries) {
            Log.d("MainViewModel", "Already loading more series, skipping...")
            return
        }

        // Feature #18: 300-item cap REMOVED - now using Paging 3 for unlimited items
        // Keeping this method only for backwards compatibility with genre filtering

        viewModelScope.launch {
            isLoadingMoreSeries = true
            seriesPage++
            Log.d("MainViewModel", "Loading series page $seriesPage...")

            val result = repository.getTvShows(page = seriesPage, perPage = 30)
            result.onSuccess { newSeries ->
                if (newSeries.isNotEmpty()) {
                    val currentSeries = _recentSeries.value ?: emptyList()
                    _recentSeries.postValue(currentSeries + newSeries)
                    Log.d("MainViewModel", "Loaded ${newSeries.size} more series (total: ${currentSeries.size + newSeries.size})")
                } else {
                    Log.d("MainViewModel", "No more series to load")
                    seriesPage-- // No more items, revert page
                }
            }.onFailure { e ->
                seriesPage-- // Revert page on failure
                Log.e("MainViewModel", "Failed to load more series: ${e.message}")
            }

            isLoadingMoreSeries = false
        }
    }

    /**
     * Load recent episodes (initial load)
     */
    private suspend fun loadEpisodes() {
        episodesPage = 1
        val result = repository.getRecentEpisodes(page = 1, perPage = 30)
        result.onSuccess { episodes ->
            _recentEpisodes.postValue(episodes)
        }.onFailure { e ->
            _error.postValue(ErrorHandler.getErrorMessage(e))
        }
    }

    /**
     * Load more episodes (pagination)
     * DEPRECATED: Use `episodes` Flow<PagingData<Episode>>instead for unlimited scrolling
     * This method is kept for backwards compatibility
     */
    @Deprecated("Use episodes Flow<PagingData<Episode>> for unlimited scrolling")
    fun loadMoreEpisodes() {
        if (isLoadingMoreEpisodes) {
            Log.d("MainViewModel", "Already loading more episodes, skipping...")
            return
        }

        // Feature #18: 300-item cap REMOVED - now using Paging 3 for unlimited items
        // Keeping this method only for backwards compatibility

        viewModelScope.launch {
            isLoadingMoreEpisodes = true
            episodesPage++
            Log.d("MainViewModel", "Loading episodes page $episodesPage...")

            val result = repository.getRecentEpisodes(page = episodesPage, perPage = 30)
            result.onSuccess { newEpisodes ->
                if (newEpisodes.isNotEmpty()) {
                    val currentEpisodes = _recentEpisodes.value ?: emptyList()
                    _recentEpisodes.postValue(currentEpisodes + newEpisodes)
                    Log.d("MainViewModel", "Loaded ${newEpisodes.size} more episodes (total: ${currentEpisodes.size + newEpisodes.size})")
                } else {
                    Log.d("MainViewModel", "No more episodes to load")
                    episodesPage-- // No more items, revert page
                }
            }.onFailure { e ->
                episodesPage-- // Revert page on failure
                Log.e("MainViewModel", "Failed to load more episodes: ${e.message}")
            }

            isLoadingMoreEpisodes = false
        }
    }

    /**
     * Load genres
     */
    private suspend fun loadGenres() {
        val result = repository.getGenres()
        result.onSuccess { genres ->
            _genres.postValue(genres)
        }.onFailure { e ->
            _error.postValue(ErrorHandler.getErrorMessage(e))
        }
    }

    /**
     * Load featured content for carousel (6 items: 3 movies + 3 series)
     */
    private suspend fun loadFeaturedContent() {
        val result = repository.getFeaturedContent()
        result.onSuccess { featured ->
            _featuredContent.postValue(featured)
        }.onFailure { e ->
            _error.postValue(ErrorHandler.getErrorMessage(e))
        }
    }

    /**
     * Load movies by genre
     */
    fun loadMoviesByGenre(genreId: Int): LiveData<List<Movie>> {
        val liveData = MutableLiveData<List<Movie>>()

        viewModelScope.launch {
            val result = repository.getMoviesByGenre(genreId, page = 1)
            result.onSuccess { movies ->
                liveData.postValue(movies)
            }.onFailure {
                liveData.postValue(emptyList())
            }
        }

        return liveData
    }

    /**
     * Load TV shows by genre
     */
    fun loadSeriesByGenre(genreId: Int): LiveData<List<Series>> {
        val liveData = MutableLiveData<List<Series>>()

        viewModelScope.launch {
            val result = repository.getTvShowsByGenre(genreId, page = 1)
            result.onSuccess { series ->
                liveData.postValue(series)
            }.onFailure {
                liveData.postValue(emptyList())
            }
        }

        return liveData
    }

    /**
     * Search content
     */
    fun search(query: String): LiveData<List<Any>> {
        val liveData = MutableLiveData<List<Any>>()

        viewModelScope.launch {
            val result = repository.search(query)
            result.onSuccess { results ->
                liveData.postValue(results)
            }.onFailure {
                liveData.postValue(emptyList())
            }
        }

        return liveData
    }

    companion object {
        private const val TAG = "MainViewModel"
    }
}
