package com.example.farsilandtv.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.farsilandtv.data.download.DownloadConstants
import com.example.farsilandtv.data.download.DownloadManager
import com.example.farsilandtv.data.download.DownloadStatus
import com.example.farsilandtv.data.models.Movie
import com.example.farsilandtv.data.models.VideoUrl
import com.example.farsilandtv.data.repository.FavoritesRepository
import com.example.farsilandtv.data.repository.PlaybackRepository
import com.example.farsilandtv.data.repository.WatchlistRepository
import com.example.farsilandtv.data.scraper.VideoUrlScraper
import com.example.farsilandtv.data.scraper.getOrNull
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * ViewModel for Phone Movie Details Screen
 *
 * Handles:
 * - Movie state (favorite, watchlist, watched)
 * - Download state (downloading, paused, completed)
 * - Video URL scraping for downloads
 * - UI state (loading, error, scraping)
 *
 * Benefits:
 * - Separates business logic from UI
 * - Properly scoped coroutines
 * - Testable download/scrape logic
 * - Survives configuration changes
 */
@HiltViewModel
class PhoneMovieDetailsViewModel @Inject constructor(
    application: Application,
    private val savedStateHandle: SavedStateHandle,
    private val favoritesRepository: FavoritesRepository,
    private val playbackRepository: PlaybackRepository,
    private val watchlistRepository: WatchlistRepository,
    private val downloadManager: DownloadManager
) : AndroidViewModel(application) {

    // Movie state
    private val _isFavorite = MutableStateFlow(false)
    val isFavorite: StateFlow<Boolean> = _isFavorite.asStateFlow()

    private val _isWatched = MutableStateFlow(false)
    val isWatched: StateFlow<Boolean> = _isWatched.asStateFlow()

    private val _isInWatchlist = MutableStateFlow(false)
    val isInWatchlist: StateFlow<Boolean> = _isInWatchlist.asStateFlow()

    // Download state
    private val _isDownloaded = MutableStateFlow(false)
    val isDownloaded: StateFlow<Boolean> = _isDownloaded.asStateFlow()

    private val _isDownloading = MutableStateFlow(false)
    val isDownloading: StateFlow<Boolean> = _isDownloading.asStateFlow()

    private val _isPaused = MutableStateFlow(false)
    val isPaused: StateFlow<Boolean> = _isPaused.asStateFlow()

    private val _downloadProgress = MutableStateFlow(0)
    val downloadProgress: StateFlow<Int> = _downloadProgress.asStateFlow()

    // UI state
    private val _isScrapingUrl = MutableStateFlow(false)
    val isScrapingUrl: StateFlow<Boolean> = _isScrapingUrl.asStateFlow()

    private val _scrapeError = MutableStateFlow<String?>(null)
    val scrapeError: StateFlow<String?> = _scrapeError.asStateFlow()

    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    // Current movie - using SavedStateHandle for process death recovery
    private var currentMovie: Movie? = null
    private var lastScrapeAction: (() -> Unit)? = null

    companion object {
        private const val TAG = "PhoneMovieDetailsVM"
        private const val KEY_MOVIE_ID = "movie_id"
    }

    /**
     * Initialize with movie data
     * FIX: Now persists movie ID in SavedStateHandle for process death recovery
     */
    fun initialize(movie: Movie) {
        currentMovie = movie
        savedStateHandle[KEY_MOVIE_ID] = movie.id
        loadMovieState(movie.id)
        observeDownloadProgress(movie.id)
    }

    /**
     * Check if we have saved state that needs to be restored
     * Call this from the UI to handle process death recovery
     */
    fun hasSavedState(): Boolean = savedStateHandle.get<Int>(KEY_MOVIE_ID) != null

    /**
     * Get saved movie ID for process death recovery
     * The UI should re-fetch the movie from repository using this ID
     */
    fun getSavedMovieId(): Int? = savedStateHandle.get<Int>(KEY_MOVIE_ID)

    private fun loadMovieState(movieId: Int) {
        viewModelScope.launch {
            try {
                _isFavorite.value = favoritesRepository.isMovieFavorited(movieId).first()
                _isWatched.value = playbackRepository.isCompleted(movieId, "movie").first() ?: false
                _isInWatchlist.value = watchlistRepository.isMovieInWatchlist(movieId)

                val downloadId = DownloadConstants.movieId(movieId)
                _isDownloaded.value = downloadManager.isDownloaded(downloadId)

                val download = downloadManager.getDownload(downloadId)
                _isPaused.value = download?.status == DownloadStatus.PAUSED
                _isDownloading.value = download?.status == DownloadStatus.DOWNLOADING
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load movie state: ${e.message}", e)
            }
        }
    }

    private fun observeDownloadProgress(movieId: Int) {
        viewModelScope.launch {
            val downloadId = DownloadConstants.movieId(movieId)
            downloadManager.downloadProgress.collect { progressMap ->
                val progress = progressMap[downloadId] ?: 0
                _downloadProgress.value = progress

                when {
                    progress >= 100 -> {
                        _isDownloading.value = false
                        _isPaused.value = false
                        _isDownloaded.value = true
                    }
                    progress in 1..99 -> {
                        _isDownloading.value = true
                        _isPaused.value = false
                    }
                }
            }
        }
    }

    // Toggle actions
    fun toggleFavorite() {
        val movie = currentMovie ?: return
        viewModelScope.launch {
            if (_isFavorite.value) {
                favoritesRepository.removeMovieFromFavorites(movie.id)
                _toastMessage.value = "Removed from favorites"
            } else {
                favoritesRepository.addMovieToFavorites(movie)
                _toastMessage.value = "Added to favorites"
            }
            _isFavorite.value = !_isFavorite.value
        }
    }

    fun toggleWatchlist() {
        val movie = currentMovie ?: return
        viewModelScope.launch {
            if (_isInWatchlist.value) {
                watchlistRepository.removeMovieFromWatchlist(movie.id)
                _toastMessage.value = "Removed from watchlist"
            } else {
                watchlistRepository.addMovieToWatchlist(movie)
                _toastMessage.value = "Added to watchlist"
            }
            _isInWatchlist.value = !_isInWatchlist.value
        }
    }

    fun toggleWatched() {
        val movie = currentMovie ?: return
        viewModelScope.launch {
            if (_isWatched.value) {
                playbackRepository.markAsIncomplete(movie.id, "movie")
                _toastMessage.value = "Marked as unwatched"
            } else {
                playbackRepository.markAsCompleted(movie.id, "movie")
                _toastMessage.value = "Marked as watched"
            }
            _isWatched.value = !_isWatched.value
        }
    }

    // Download actions
    fun startDownload() {
        val movie = currentMovie ?: return

        if (_isDownloaded.value) {
            // Remove download
            viewModelScope.launch {
                downloadManager.deleteDownload(DownloadConstants.movieId(movie.id))
                _isDownloaded.value = false
                _toastMessage.value = "Download removed"
            }
            return
        }

        if (_isDownloading.value || _isScrapingUrl.value) return

        val farsilandUrl = movie.farsilandUrl
        if (farsilandUrl.isNullOrBlank()) {
            _toastMessage.value = "Video source not available"
            return
        }

        // Define scrape action for retry
        lastScrapeAction = { performScrapeAndDownload(movie, farsilandUrl) }
        performScrapeAndDownload(movie, farsilandUrl)
    }

    private fun performScrapeAndDownload(movie: Movie, url: String) {
        viewModelScope.launch {
            Log.d(TAG, "Starting download, scraping URL from: $url")
            _isScrapingUrl.value = true
            _scrapeError.value = null

            try {
                val result = withContext(Dispatchers.IO) {
                    VideoUrlScraper.extractVideoUrls(url)
                }

                val videoUrls: List<VideoUrl> = result.getOrNull() ?: emptyList()
                Log.d(TAG, "Scraped ${videoUrls.size} video URLs")

                val bestUrl = videoUrls.maxByOrNull { videoUrl ->
                    val quality = videoUrl.quality.lowercase()
                    when {
                        quality.contains("1080") -> 4
                        quality.contains("720") -> 3
                        quality.contains("480") -> 2
                        quality.contains("360") -> 1
                        else -> 0
                    }
                }

                if (bestUrl != null) {
                    _toastMessage.value = "Starting download (${bestUrl.quality})..."
                    val queued = downloadManager.queueMovieDownload(
                        movieId = movie.id,
                        title = movie.title,
                        posterUrl = movie.posterUrl,
                        videoUrl = bestUrl.url
                    )
                    if (queued) {
                        _toastMessage.value = "Download started!"
                        _isDownloading.value = true
                        lastScrapeAction = null
                    } else {
                        _scrapeError.value = "Failed to start download"
                    }
                } else {
                    _scrapeError.value = "No video URL found"
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to scrape video URL: ${e.message}", e)
                _scrapeError.value = e.message ?: "Failed to find video"
            } finally {
                _isScrapingUrl.value = false
            }
        }
    }

    fun retryDownload() {
        _scrapeError.value = null
        lastScrapeAction?.invoke()
    }

    fun clearScrapeError() {
        _scrapeError.value = null
    }

    fun pauseDownload() {
        val movie = currentMovie ?: return
        viewModelScope.launch {
            downloadManager.pauseDownload(DownloadConstants.movieId(movie.id))
            _isPaused.value = true
            _toastMessage.value = "Download paused"
        }
    }

    fun resumeDownload() {
        val movie = currentMovie ?: return
        viewModelScope.launch {
            downloadManager.resumeDownload(DownloadConstants.movieId(movie.id))
            _isPaused.value = false
            _toastMessage.value = "Download resumed"
        }
    }

    // VM-H5 FIX: Wait for operation before state update
    fun cancelDownload() {
        val movie = currentMovie ?: return
        viewModelScope.launch {
            try {
                // VM-H5: Await cancellation before updating UI state
                downloadManager.cancelDownload(DownloadConstants.movieId(movie.id))
                // Update UI state after operation completes
                _isDownloading.value = false
                _isPaused.value = false
                _toastMessage.value = "Download cancelled"
            } catch (e: Exception) {
                Log.e(TAG, "Failed to cancel download: ${e.message}", e)
                _toastMessage.value = "Failed to cancel download"
            }
        }
    }

    fun clearToast() {
        _toastMessage.value = null
    }
}
