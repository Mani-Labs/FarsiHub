package com.example.farsilandtv.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.farsilandtv.data.database.ContinueWatchingItem
import com.example.farsilandtv.data.database.Favorite
import com.example.farsilandtv.data.database.MonitoredSeries
import com.example.farsilandtv.data.download.DownloadItem
import com.example.farsilandtv.data.download.DownloadManager
import com.example.farsilandtv.data.download.DownloadStatus
import com.example.farsilandtv.data.models.Episode
import com.example.farsilandtv.data.models.FeaturedContent
import com.example.farsilandtv.data.models.Movie
import com.example.farsilandtv.data.models.Series
import com.example.farsilandtv.data.repository.ContentRepository
import com.example.farsilandtv.data.repository.FavoritesRepository
import com.example.farsilandtv.data.repository.WatchlistRepository
import com.example.farsilandtv.utils.ErrorHandler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import javax.inject.Inject

/**
 * ViewModel for Phone Home Screen
 *
 * Consolidates:
 * - Content data (movies, series, episodes, featured)
 * - User data (continue watching, favorites, monitored series, downloads)
 * - UI state (loading, error, refreshing)
 *
 * Benefits:
 * - Single source of truth for home screen state
 * - Proper lifecycle management
 * - Testable business logic
 * - Survives configuration changes
 */
@HiltViewModel
class PhoneHomeViewModel @Inject constructor(
    application: Application,
    private val contentRepository: ContentRepository,
    private val favoritesRepository: FavoritesRepository,
    private val watchlistRepository: WatchlistRepository,
    private val downloadManager: DownloadManager
) : AndroidViewModel(application) {

    // Content state
    private val _movies = MutableStateFlow<List<Movie>>(emptyList())
    val movies: StateFlow<List<Movie>> = _movies.asStateFlow()

    private val _series = MutableStateFlow<List<Series>>(emptyList())
    val series: StateFlow<List<Series>> = _series.asStateFlow()

    private val _episodes = MutableStateFlow<List<Episode>>(emptyList())
    val episodes: StateFlow<List<Episode>> = _episodes.asStateFlow()

    private val _featuredContent = MutableStateFlow<List<FeaturedContent>>(emptyList())
    val featuredContent: StateFlow<List<FeaturedContent>> = _featuredContent.asStateFlow()

    // User data - collected from repositories as StateFlows
    val continueWatching: StateFlow<List<ContinueWatchingItem>> =
        watchlistRepository.getContinueWatching()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favorites: StateFlow<List<Favorite>> =
        favoritesRepository.getAllFavorites()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val monitoredSeries: StateFlow<List<MonitoredSeries>> =
        watchlistRepository.getAllMonitoredSeries()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val downloads: StateFlow<List<DownloadItem>> =
        downloadManager.getAllDownloads()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val downloadProgress: StateFlow<Map<String, Int>> = downloadManager.downloadProgress

    // UI state
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        loadContent()
    }

    /**
     * Load all content for home screen
     */
    fun loadContent() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            try {
                supervisorScope {
                    // Load featured content
                    async {
                        try {
                            val result = contentRepository.getFeaturedContent()
                            result.onSuccess { featured ->
                                _featuredContent.value = featured
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to load featured content: ${e.message}")
                        }
                    }

                    // Load movies
                    async {
                        try {
                            val result = contentRepository.getMovies(page = 1, perPage = 50)
                            result.onSuccess { movieList ->
                                _movies.value = movieList
                            }.onFailure { e ->
                                _error.value = ErrorHandler.getErrorMessage(e)
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to load movies: ${e.message}")
                        }
                    }

                    // Load series
                    async {
                        try {
                            val result = contentRepository.getTvShows(page = 1, perPage = 50)
                            result.onSuccess { seriesList ->
                                _series.value = seriesList
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to load series: ${e.message}")
                        }
                    }

                    // Load episodes
                    async {
                        try {
                            val result = contentRepository.getRecentEpisodes(page = 1, perPage = 50)
                            result.onSuccess { episodeList ->
                                _episodes.value = episodeList
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to load episodes: ${e.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                _error.value = ErrorHandler.getErrorMessage(e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Refresh content (pull-to-refresh)
     * VM-C3 FIX: Use try-finally to ensure isRefreshing is set false after loadContent completes
     */
    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                loadContent()
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    /**
     * Clear error state
     */
    fun clearError() {
        _error.value = null
    }

    // Download management functions
    fun pauseDownload(downloadId: String) {
        viewModelScope.launch {
            downloadManager.pauseDownload(downloadId)
        }
    }

    fun resumeDownload(downloadId: String) {
        viewModelScope.launch {
            downloadManager.resumeDownload(downloadId)
        }
    }

    fun cancelDownload(downloadId: String) {
        viewModelScope.launch {
            downloadManager.cancelDownload(downloadId)
        }
    }

    // Computed properties for UI convenience
    fun getCompletedDownloads(): List<DownloadItem> {
        return downloads.value.filter { it.status == DownloadStatus.COMPLETED }
    }

    fun getActiveDownloads(): List<DownloadItem> {
        return downloads.value.filter {
            it.status == DownloadStatus.DOWNLOADING ||
            it.status == DownloadStatus.PENDING ||
            it.status == DownloadStatus.PAUSED
        }
    }

    fun getFavoriteMovies(): List<Movie> {
        val favoriteIds = favorites.value
            .filter { it.contentType == Favorite.ContentType.MOVIE }
            .map { it.numericId }
        return movies.value.filter { it.id in favoriteIds }
    }

    fun getFavoriteSeries(): List<Series> {
        val favoriteIds = favorites.value
            .filter { it.contentType == Favorite.ContentType.SERIES }
            .map { it.numericId }
        return series.value.filter { it.id in favoriteIds }
    }

    companion object {
        private const val TAG = "PhoneHomeViewModel"
    }
}
