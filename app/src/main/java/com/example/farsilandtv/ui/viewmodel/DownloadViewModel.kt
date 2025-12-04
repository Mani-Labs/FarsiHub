package com.example.farsilandtv.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.farsilandtv.data.download.DownloadConstants
import com.example.farsilandtv.data.download.DownloadManager
import com.example.farsilandtv.data.download.DownloadStatus
import com.example.farsilandtv.data.models.Episode
import com.example.farsilandtv.data.models.Movie
import com.example.farsilandtv.data.models.VideoUrl
import com.example.farsilandtv.data.scraper.VideoUrlScraper
import com.example.farsilandtv.data.scraper.getOrNull
import com.example.farsilandtv.utils.NetworkUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Shared ViewModel for download functionality
 * Used by PhoneMovieDetailsScreen and PhoneSeriesDetailsScreen
 */
@HiltViewModel
class DownloadViewModel @Inject constructor(
    application: Application,
    private val downloadManager: DownloadManager
) : AndroidViewModel(application) {

    private val context = application.applicationContext

    // Network state
    private val _isNetworkAvailable = MutableStateFlow(true)
    val isNetworkAvailable: StateFlow<Boolean> = _isNetworkAvailable.asStateFlow()

    init {
        // Observe network state
        viewModelScope.launch {
            NetworkUtils.observeNetworkState(context).collect { isConnected ->
                _isNetworkAvailable.value = isConnected
            }
        }
    }

    /**
     * Check if network is available before downloading
     */
    fun checkNetworkBeforeDownload(): Boolean {
        val isConnected = NetworkUtils.isNetworkAvailable(context)
        _isNetworkAvailable.value = isConnected
        return isConnected
    }

    /**
     * Get network type for informational purposes
     */
    fun getNetworkType(): NetworkUtils.NetworkType {
        return NetworkUtils.getNetworkType(context)
    }

    // Scraping state
    private val _isScrapingUrl = MutableStateFlow(false)
    val isScrapingUrl: StateFlow<Boolean> = _isScrapingUrl.asStateFlow()

    private val _scrapingContentId = MutableStateFlow<String?>(null)
    val scrapingContentId: StateFlow<String?> = _scrapingContentId.asStateFlow()

    // Error state
    private val _scrapeError = MutableStateFlow<String?>(null)
    val scrapeError: StateFlow<String?> = _scrapeError.asStateFlow()

    // VM-C4 FIX: Store only necessary data instead of closures to prevent memory leaks
    // Store minimal state needed to retry scraping
    private data class ScrapeRetryData(
        val contentId: String,
        val pageUrl: String,
        val displayName: String,
        val isMovie: Boolean,
        val movieId: Int? = null,
        val episodeId: Int? = null,
        val seriesTitle: String? = null,
        val episodeInfo: String? = null,
        val posterUrl: String? = null
    )
    private var lastScrapeRetryData: ScrapeRetryData? = null

    // Download progress (delegated from DownloadManager)
    val downloadProgress = downloadManager.downloadProgress

    companion object {
        private const val TAG = "DownloadViewModel"
    }

    /**
     * Start downloading a movie
     * VM-C4 FIX: Store minimal retry data instead of closures
     */
    fun downloadMovie(
        movie: Movie,
        onSuccess: () -> Unit,
        onVideoNotAvailable: () -> Unit
    ) {
        val farsilandUrl = movie.farsilandUrl
        if (farsilandUrl.isNullOrBlank()) {
            onVideoNotAvailable()
            return
        }

        val downloadId = DownloadConstants.movieId(movie.id)

        // Store retry data
        lastScrapeRetryData = ScrapeRetryData(
            contentId = downloadId,
            pageUrl = farsilandUrl,
            displayName = movie.title,
            isMovie = true,
            movieId = movie.id,
            posterUrl = movie.posterUrl
        )

        viewModelScope.launch {
            doScrapeAndDownload(
                contentId = downloadId,
                pageUrl = farsilandUrl,
                onDownload = { videoUrl ->
                    downloadManager.queueMovieDownload(
                        movieId = movie.id,
                        title = movie.title,
                        posterUrl = movie.posterUrl,
                        videoUrl = videoUrl.url
                    )
                },
                onSuccess = onSuccess,
                displayName = movie.title
            )
        }
    }

    /**
     * Start downloading an episode
     * VM-C4 FIX: Store minimal retry data instead of closures
     */
    fun downloadEpisode(
        episode: Episode,
        seriesTitle: String,
        onSuccess: () -> Unit,
        onVideoNotAvailable: () -> Unit
    ) {
        val episodeUrl = episode.farsilandUrl
        if (episodeUrl.isNullOrBlank()) {
            onVideoNotAvailable()
            return
        }

        val downloadId = DownloadConstants.episodeId(episode.id)
        val episodeInfo = "S${episode.season} E${episode.episode}"

        // Store retry data
        lastScrapeRetryData = ScrapeRetryData(
            contentId = downloadId,
            pageUrl = episodeUrl,
            displayName = "$seriesTitle - $episodeInfo",
            isMovie = false,
            episodeId = episode.id,
            seriesTitle = seriesTitle,
            episodeInfo = episodeInfo,
            posterUrl = episode.thumbnailUrl ?: episode.episodePosterUrl
        )

        viewModelScope.launch {
            doScrapeAndDownload(
                contentId = downloadId,
                pageUrl = episodeUrl,
                onDownload = { videoUrl ->
                    downloadManager.queueEpisodeDownload(
                        episodeId = episode.id,
                        seriesTitle = seriesTitle,
                        episodeInfo = episodeInfo,
                        posterUrl = episode.thumbnailUrl ?: episode.episodePosterUrl,
                        videoUrl = videoUrl.url
                    )
                },
                onSuccess = onSuccess,
                displayName = "$seriesTitle - $episodeInfo"
            )
        }
    }

    private suspend fun doScrapeAndDownload(
        contentId: String,
        pageUrl: String,
        onDownload: suspend (VideoUrl) -> Boolean,
        onSuccess: () -> Unit,
        displayName: String
    ) {
        Log.d(TAG, "Starting download for $displayName, scraping URL from: $pageUrl")
        _isScrapingUrl.value = true
        _scrapingContentId.value = contentId
        _scrapeError.value = null

        try {
            val result = withContext(Dispatchers.IO) {
                VideoUrlScraper.extractVideoUrls(pageUrl)
            }

            val videoUrls: List<VideoUrl> = result.getOrNull() ?: emptyList()
            Log.d(TAG, "Scraped ${videoUrls.size} video URLs for $displayName")

            val bestUrl: VideoUrl? = videoUrls.maxByOrNull { videoUrl ->
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
                val queued = onDownload(bestUrl)
                if (queued) {
                    Log.d(TAG, "Download started for $displayName (${bestUrl.quality})")
                    lastScrapeRetryData = null  // Clear retry data on success
                    onSuccess()
                } else {
                    _scrapeError.value = "Failed to start download"
                    // Retry data already stored in downloadMovie/downloadEpisode
                }
            } else {
                _scrapeError.value = "No video URL found for $displayName"
                // Retry data already stored in downloadMovie/downloadEpisode
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to scrape video URL: ${e.message}", e)
            _scrapeError.value = e.message ?: "Failed to find video"
            // Retry data already stored in downloadMovie/downloadEpisode
        } finally {
            _isScrapingUrl.value = false
            _scrapingContentId.value = null
        }
    }

    /**
     * Retry the last failed scrape action
     * VM-C4 FIX: Use stored retry data instead of closure
     */
    fun retryScrape() {
        _scrapeError.value = null
        val retryData = lastScrapeRetryData ?: return

        viewModelScope.launch {
            if (retryData.isMovie && retryData.movieId != null) {
                doScrapeAndDownload(
                    contentId = retryData.contentId,
                    pageUrl = retryData.pageUrl,
                    onDownload = { videoUrl ->
                        downloadManager.queueMovieDownload(
                            movieId = retryData.movieId,
                            title = retryData.displayName,
                            posterUrl = retryData.posterUrl,
                            videoUrl = videoUrl.url
                        )
                    },
                    onSuccess = {},
                    displayName = retryData.displayName
                )
            } else if (!retryData.isMovie && retryData.episodeId != null) {
                doScrapeAndDownload(
                    contentId = retryData.contentId,
                    pageUrl = retryData.pageUrl,
                    onDownload = { videoUrl ->
                        downloadManager.queueEpisodeDownload(
                            episodeId = retryData.episodeId,
                            seriesTitle = retryData.seriesTitle ?: "",
                            episodeInfo = retryData.episodeInfo ?: "",
                            posterUrl = retryData.posterUrl,
                            videoUrl = videoUrl.url
                        )
                    },
                    onSuccess = {},
                    displayName = retryData.displayName
                )
            }
        }
    }

    /**
     * Dismiss the error dialog
     * VM-C4 FIX: Clear retry data instead of closure
     */
    fun dismissError() {
        _scrapeError.value = null
        lastScrapeRetryData = null
    }

    /**
     * Check if a movie is downloaded
     */
    suspend fun isMovieDownloaded(movieId: Int): Boolean {
        return downloadManager.isDownloaded(DownloadConstants.movieId(movieId))
    }

    /**
     * Check if an episode is downloaded
     */
    suspend fun isEpisodeDownloaded(episodeId: Int): Boolean {
        return downloadManager.isDownloaded(DownloadConstants.episodeId(episodeId))
    }

    /**
     * Get download status for a movie
     */
    suspend fun getMovieDownloadStatus(movieId: Int): DownloadStatus? {
        return downloadManager.getDownload(DownloadConstants.movieId(movieId))?.status
    }

    /**
     * Get download status for an episode
     */
    suspend fun getEpisodeDownloadStatus(episodeId: Int): DownloadStatus? {
        return downloadManager.getDownload(DownloadConstants.episodeId(episodeId))?.status
    }

    /**
     * Delete a movie download
     */
    suspend fun deleteMovieDownload(movieId: Int) {
        downloadManager.deleteDownload(DownloadConstants.movieId(movieId))
    }

    /**
     * Delete an episode download
     */
    suspend fun deleteEpisodeDownload(episodeId: Int) {
        downloadManager.deleteDownload(DownloadConstants.episodeId(episodeId))
    }

    /**
     * Pause a movie download
     */
    suspend fun pauseMovieDownload(movieId: Int) {
        downloadManager.pauseDownload(DownloadConstants.movieId(movieId))
    }

    /**
     * Resume a movie download
     */
    fun resumeMovieDownload(movieId: Int) {
        downloadManager.resumeDownload(DownloadConstants.movieId(movieId))
    }

    /**
     * Cancel a movie download
     */
    suspend fun cancelMovieDownload(movieId: Int) {
        downloadManager.cancelDownload(DownloadConstants.movieId(movieId))
    }

    /**
     * Pause an episode download
     */
    suspend fun pauseEpisodeDownload(episodeId: Int) {
        downloadManager.pauseDownload(DownloadConstants.episodeId(episodeId))
    }

    /**
     * Resume an episode download
     */
    fun resumeEpisodeDownload(episodeId: Int) {
        downloadManager.resumeDownload(DownloadConstants.episodeId(episodeId))
    }

    /**
     * Cancel an episode download
     */
    suspend fun cancelEpisodeDownload(episodeId: Int) {
        downloadManager.cancelDownload(DownloadConstants.episodeId(episodeId))
    }

}
