package com.example.farsilandtv.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.example.farsilandtv.data.cache.PrefetchManager
import com.example.farsilandtv.data.models.Movie
import com.example.farsilandtv.data.models.Series
import com.example.farsilandtv.data.models.Genre
import com.example.farsilandtv.data.models.FeaturedContent
import com.example.farsilandtv.data.repository.ContentRepository
import com.example.farsilandtv.utils.ErrorHandler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import javax.inject.Inject

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
@HiltViewModel
class MainViewModel @Inject constructor(
    application: Application,
    private val repository: ContentRepository,
    private val prefetchManager: PrefetchManager
) : AndroidViewModel(application) {

    // Feature #18: Paging 3 - Unlimited scrolling (replaces 300-item caps)
    // These flows are database-backed and can handle unlimited items efficiently
    // Lazy initialization to match repository's lazy pattern
    val movies: Flow<PagingData<Movie>> by lazy {
        repository.getMoviesPaged().cachedIn(viewModelScope)
    }

    val series: Flow<PagingData<Series>> by lazy {
        repository.getSeriesPaged().cachedIn(viewModelScope)
    }

    val episodes: Flow<PagingData<com.example.farsilandtv.data.models.Episode>> by lazy {
        repository.getEpisodesPaged().cachedIn(viewModelScope)
    }

    // Genre/Sort filter state for MoviesScreen
    private val _movieGenreFilter = MutableStateFlow<String?>(null)
    val movieGenreFilter: StateFlow<String?> = _movieGenreFilter.asStateFlow()

    private val _movieSortOption = MutableStateFlow("Recent")
    val movieSortOption: StateFlow<String> = _movieSortOption.asStateFlow()

    // Filtered movies flow - reacts to genre/sort changes
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val filteredMovies: Flow<PagingData<Movie>> = combine(
        _movieGenreFilter,
        _movieSortOption
    ) { genre, sort -> Pair(genre, sort) }
        .flatMapLatest { (genre, sort) ->
            repository.getMoviesPagedWithFilter(genre, sort)
        }
        .cachedIn(viewModelScope)

    fun setMovieGenreFilter(genre: String?) {
        _movieGenreFilter.value = genre
    }

    fun setMovieSortOption(sort: String) {
        _movieSortOption.value = sort
    }

    // Genre/Sort filter state for ShowsScreen
    private val _seriesGenreFilter = MutableStateFlow<String?>(null)
    val seriesGenreFilter: StateFlow<String?> = _seriesGenreFilter.asStateFlow()

    private val _seriesSortOption = MutableStateFlow("Recent")
    val seriesSortOption: StateFlow<String> = _seriesSortOption.asStateFlow()

    // Filtered series flow - reacts to genre/sort changes
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val filteredSeries: Flow<PagingData<Series>> = combine(
        _seriesGenreFilter,
        _seriesSortOption
    ) { genre, sort -> Pair(genre, sort) }
        .flatMapLatest { (genre, sort) ->
            repository.getSeriesPagedWithFilter(genre, sort)
        }
        .cachedIn(viewModelScope)

    fun setSeriesGenreFilter(genre: String?) {
        _seriesGenreFilter.value = genre
    }

    fun setSeriesSortOption(sort: String) {
        _seriesSortOption.value = sort
    }

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
    // H1 FIX: Use @Volatile for thread-safe access from multiple coroutines
    @Volatile private var isLoadingMoreMovies = false
    @Volatile private var isLoadingMoreSeries = false
    @Volatile private var isLoadingMoreEpisodes = false

    // LiveData for genres
    private val _genres = MutableLiveData<List<Genre>>()
    val genres: LiveData<List<Genre>> = _genres

    // H2 FIX: Cache LiveData per genre to avoid recreation on each call
    private val moviesByGenreCache = mutableMapOf<Int, MutableLiveData<List<Movie>>>()
    private val seriesByGenreCache = mutableMapOf<Int, MutableLiveData<List<Series>>>()

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

    // AUDIT FIX #19: Track refresh job to prevent race conditions
    private var refreshJob: kotlinx.coroutines.Job? = null

    init {
        // Observe sync completion and auto-reload data
        // AUDIT FIX #19: Cancel pending refresh before starting new one
        viewModelScope.launch {
            repository.observeSyncCompletion().collect { _ ->
                Log.d(TAG, "Sync completed - reloading content automatically")

                // Cancel any pending refresh to prevent duplicate requests
                refreshJob?.cancel()

                // Start new refresh and track the job
                refreshJob = launch {
                    // Don't call loadContent() as it triggers isLoading which clears watchlist rows
                    // Instead, just refresh the main content data
                    refreshContentWithoutLoadingState()
                }
            }
        }
    }

    /**
     * Load all content (movies, series, genres) with retry logic
     * AUDIT FIX #14: Partial loading - each content type loads independently
     */
    fun loadContent() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            try {
                // AUDIT FIX #14: Use supervisorScope for independent failure handling
                // If one content type fails, others still load successfully
                kotlinx.coroutines.supervisorScope {
                    // Load genres first (non-blocking failures)
                    async {
                        try {
                            loadGenres()
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to load genres: ${e.message}")
                        }
                    }

                    // Load featured content for carousel (non-blocking failures)
                    async {
                        try {
                            loadFeaturedContent()
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to load featured content: ${e.message}")
                        }
                    }

                    // Load movies, series, and episodes independently
                    val moviesDeferred = async {
                        try {
                            loadMovies()
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to load movies: ${e.message}")
                            _error.postValue(ErrorHandler.getErrorMessage(e))
                        }
                    }

                    val seriesDeferred = async {
                        try {
                            loadSeries()
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to load series: ${e.message}")
                        }
                    }

                    val episodesDeferred = async {
                        try {
                            loadEpisodes()
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to load episodes: ${e.message}")
                        }
                    }

                    // Wait for all to complete (failures don't propagate)
                    moviesDeferred.await()
                    seriesDeferred.await()
                    episodesDeferred.await()

                    // Phase 4: Background prefetch images after content loads
                    prefetchLoadedContent()
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
     * Phase 4: Prefetch images for loaded content
     */
    private fun prefetchLoadedContent() {
        try {
            // Prefetch featured content images (highest priority)
            _featuredContent.value?.let { featured ->
                // Convert FeaturedContent sealed class to Movie/Series for prefetch
                val featuredMovies = featured.filterIsInstance<FeaturedContent.FeaturedMovie>()
                    .map { Movie(
                        id = it.id,
                        title = it.title,
                        posterUrl = it.posterUrl,
                        backdropUrl = it.backdropUrl,
                        farsilandUrl = it.farsilandUrl,
                        description = it.description
                    ) }
                val featuredSeries = featured.filterIsInstance<FeaturedContent.FeaturedSeries>()
                    .map { Series(
                        id = it.id,
                        title = it.title,
                        posterUrl = it.posterUrl,
                        backdropUrl = it.backdropUrl,
                        farsilandUrl = it.farsilandUrl,
                        description = it.description
                    ) }
                prefetchManager.prefetchFeaturedContent(featuredMovies, featuredSeries)
            }

            // Prefetch recent movies and series posters
            _recentMovies.value?.let { movies ->
                prefetchManager.prefetchMoviePosters(movies)
            }
            _recentSeries.value?.let { seriesList ->
                prefetchManager.prefetchSeriesPosters(seriesList)
            }

            // Prefetch recent episode thumbnails
            _recentEpisodes.value?.let { episodes ->
                prefetchManager.prefetchEpisodeThumbnails(episodes)
            }

            Log.d(TAG, "Phase 4: Background prefetch initiated for loaded content")
        } catch (e: Exception) {
            Log.w(TAG, "Prefetch failed (non-critical): ${e.message}")
        }
    }

    /**
     * Refresh content without triggering loading state
     * Used after sync completion to avoid clearing watchlist rows
     * AUDIT FIX #19: Added to use supervisorScope for independent failures
     */
    private fun refreshContentWithoutLoadingState() {
        viewModelScope.launch {
            try {
                // Clear cache to get fresh data
                com.example.farsilandtv.data.api.RetrofitClient.clearCache()
                repository.clearCache()
                Log.d(TAG, "refreshContentWithoutLoadingState: Cleared caches")

                // Reload content WITHOUT setting isLoading (prevents row clearing)
                // AUDIT FIX #19: Use supervisorScope for independent failure handling
                kotlinx.coroutines.supervisorScope {
                    // Load genres (non-blocking failures)
                    async {
                        try {
                            loadGenres()
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to refresh genres: ${e.message}")
                        }
                    }

                    // Load featured content (non-blocking failures)
                    async {
                        try {
                            loadFeaturedContent()
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to refresh featured content: ${e.message}")
                        }
                    }

                    // Load in parallel with independent error handling
                    val moviesDeferred = async {
                        try {
                            loadMovies()
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to refresh movies: ${e.message}")
                        }
                    }

                    val seriesDeferred = async {
                        try {
                            loadSeries()
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to refresh series: ${e.message}")
                        }
                    }

                    val episodesDeferred = async {
                        try {
                            loadEpisodes()
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to refresh episodes: ${e.message}")
                        }
                    }

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
     * AUDIT FIX #14: Updated to use supervisorScope for independent failures
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

                // AUDIT FIX #14: Use supervisorScope for independent failure handling
                kotlinx.coroutines.supervisorScope {
                    // Load genres (non-blocking failures)
                    async {
                        try {
                            loadGenres()
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to force refresh genres: ${e.message}")
                        }
                    }

                    // Load featured content (non-blocking failures)
                    async {
                        try {
                            loadFeaturedContent()
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to force refresh featured content: ${e.message}")
                        }
                    }

                    // Load in parallel with independent error handling
                    val moviesDeferred = async {
                        try {
                            loadMovies()
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to force refresh movies: ${e.message}")
                            _error.postValue(ErrorHandler.getErrorMessage(e))
                        }
                    }

                    val seriesDeferred = async {
                        try {
                            loadSeries()
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to force refresh series: ${e.message}")
                        }
                    }

                    val episodesDeferred = async {
                        try {
                            loadEpisodes()
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to force refresh episodes: ${e.message}")
                        }
                    }

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
        val result = repository.getMovies(page = 1, perPage = 50)
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

            // N15 FIX: Use try-finally to ensure loading flag is always reset
            try {
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
            } finally {
                isLoadingMoreMovies = false
            }
        }
    }

    /**
     * Load recent TV series (initial load)
     */
    private suspend fun loadSeries() {
        seriesPage = 1
        val result = repository.getTvShows(page = 1, perPage = 50)
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

            // N15 FIX: Use try-finally to ensure loading flag is always reset
            try {
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
            } finally {
                isLoadingMoreSeries = false
            }
        }
    }

    /**
     * Load recent episodes (initial load)
     */
    private suspend fun loadEpisodes() {
        episodesPage = 1
        val result = repository.getRecentEpisodes(page = 1, perPage = 50)
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

            // N15 FIX: Use try-finally to ensure loading flag is always reset
            try {
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
            } finally {
                isLoadingMoreEpisodes = false
            }
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
     * H2 FIX: Cache LiveData per genre to avoid recreation on each call
     */
    fun loadMoviesByGenre(genreId: Int): LiveData<List<Movie>> {
        // Return cached LiveData if available
        moviesByGenreCache[genreId]?.let { return it }

        val liveData = MutableLiveData<List<Movie>>()
        moviesByGenreCache[genreId] = liveData

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
     * H2 FIX: Cache LiveData per genre to avoid recreation on each call
     */
    fun loadSeriesByGenre(genreId: Int): LiveData<List<Series>> {
        // Return cached LiveData if available
        seriesByGenreCache[genreId]?.let { return it }

        val liveData = MutableLiveData<List<Series>>()
        seriesByGenreCache[genreId] = liveData

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
