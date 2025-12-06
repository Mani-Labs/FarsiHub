package com.example.farsilandtv.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.farsilandtv.data.download.DownloadConstants
import com.example.farsilandtv.data.download.DownloadItem
import com.example.farsilandtv.data.download.DownloadManager
import com.example.farsilandtv.data.download.DownloadStatus
import com.example.farsilandtv.data.models.Episode
import com.example.farsilandtv.data.models.Series
import com.example.farsilandtv.data.models.VideoUrl
import com.example.farsilandtv.data.repository.FavoritesRepository
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
 * ViewModel for Phone Series Details Screen
 *
 * Handles:
 * - Series state (favorite, monitored)
 * - Episode download states (downloading, paused, completed per episode)
 * - Season selection
 * - Video URL scraping for episode downloads
 * - UI state (loading, error, scraping)
 *
 * Benefits:
 * - Centralizes complex multi-episode download logic
 * - Prevents race conditions in download state
 * - Properly scoped coroutines
 * - Survives configuration changes
 */
@HiltViewModel
class PhoneSeriesDetailsViewModel @Inject constructor(
    application: Application,
    private val savedStateHandle: SavedStateHandle,
    private val favoritesRepository: FavoritesRepository,
    private val watchlistRepository: WatchlistRepository,
    private val downloadManager: DownloadManager
) : AndroidViewModel(application) {

    // Series state
    private val _isFavorite = MutableStateFlow(false)
    val isFavorite: StateFlow<Boolean> = _isFavorite.asStateFlow()

    private val _isMonitored = MutableStateFlow(false)
    val isMonitored: StateFlow<Boolean> = _isMonitored.asStateFlow()

    // Season selection
    private val _selectedSeason = MutableStateFlow(1)
    val selectedSeason: StateFlow<Int> = _selectedSeason.asStateFlow()

    // Episode download states - single authoritative source
    private val _downloadedEpisodes = MutableStateFlow<Set<Int>>(emptySet())
    val downloadedEpisodes: StateFlow<Set<Int>> = _downloadedEpisodes.asStateFlow()

    private val _downloadingEpisodes = MutableStateFlow<Set<Int>>(emptySet())
    val downloadingEpisodes: StateFlow<Set<Int>> = _downloadingEpisodes.asStateFlow()

    private val _pausedEpisodes = MutableStateFlow<Set<Int>>(emptySet())
    val pausedEpisodes: StateFlow<Set<Int>> = _pausedEpisodes.asStateFlow()

    // Download progress per episode
    private val _episodeProgress = MutableStateFlow<Map<Int, Int>>(emptyMap())
    val episodeProgress: StateFlow<Map<Int, Int>> = _episodeProgress.asStateFlow()

    // UI state
    private val _scrapingEpisodeId = MutableStateFlow<Int?>(null)
    val scrapingEpisodeId: StateFlow<Int?> = _scrapingEpisodeId.asStateFlow()

    private val _scrapeError = MutableStateFlow<String?>(null)
    val scrapeError: StateFlow<String?> = _scrapeError.asStateFlow()

    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    private val _downloadingAll = MutableStateFlow(false)
    val downloadingAll: StateFlow<Boolean> = _downloadingAll.asStateFlow()

    // Current data - using SavedStateHandle for process death recovery
    private var currentSeries: Series? = null
    private var episodesBySeason: Map<Int, List<Episode>> = emptyMap()
    private var lastScrapeAction: (() -> Unit)? = null

    companion object {
        private const val TAG = "PhoneSeriesDetailsVM"
        private const val KEY_SERIES_ID = "series_id"
        private const val KEY_SELECTED_SEASON = "selected_season"
    }

    /**
     * Initialize with series and episodes data
     * FIX: Now persists series ID in SavedStateHandle for process death recovery
     */
    fun initialize(series: Series, episodes: Map<Int, List<Episode>>) {
        currentSeries = series
        episodesBySeason = episodes
        savedStateHandle[KEY_SERIES_ID] = series.id

        // Restore or set initial season
        val savedSeason = savedStateHandle.get<Int>(KEY_SELECTED_SEASON)
        _selectedSeason.value = savedSeason ?: episodes.keys.minOrNull() ?: 1

        loadSeriesState(series.id)
        observeDownloadProgress(episodes)
    }

    /**
     * Check if we have saved state that needs to be restored
     * Call this from the UI to handle process death recovery
     */
    fun hasSavedState(): Boolean = savedStateHandle.get<Int>(KEY_SERIES_ID) != null

    /**
     * Get saved series ID for process death recovery
     * The UI should re-fetch the series from repository using this ID
     */
    fun getSavedSeriesId(): Int? = savedStateHandle.get<Int>(KEY_SERIES_ID)

    private fun loadSeriesState(seriesId: Int) {
        viewModelScope.launch {
            try {
                _isFavorite.value = favoritesRepository.isSeriesFavorited(seriesId).first()
                _isMonitored.value = watchlistRepository.isSeriesMonitored(seriesId)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load series state: ${e.message}", e)
            }
        }
    }

    // VM-H3 FIX: Cache getDownload() results to avoid repeated database queries
    private val downloadCache = mutableMapOf<String, DownloadItem?>()
    private var lastCacheUpdate = 0L
    private val CACHE_VALIDITY_MS = 1000L // 1 second cache validity

    private fun observeDownloadProgress(episodes: Map<Int, List<Episode>>) {
        viewModelScope.launch {
            downloadManager.downloadProgress.collect { progressMap ->
                val downloaded = mutableSetOf<Int>()
                val downloading = mutableSetOf<Int>()
                val paused = mutableSetOf<Int>()
                val progressUpdate = mutableMapOf<Int, Int>()

                // VM-H3: Refresh cache if stale
                val now = System.currentTimeMillis()
                if (now - lastCacheUpdate > CACHE_VALIDITY_MS) {
                    downloadCache.clear()
                    lastCacheUpdate = now
                }

                episodes.values.flatten().forEach { episode ->
                    val downloadId = DownloadConstants.episodeId(episode.id)
                    val progress = progressMap[downloadId] ?: 0

                    // VM-H3: Use cached download status
                    val download = downloadCache.getOrPut(downloadId) {
                        downloadManager.getDownload(downloadId)
                    }

                    progressUpdate[episode.id] = progress

                    when {
                        progress >= 100 || downloadManager.isDownloaded(downloadId) -> {
                            downloaded.add(episode.id)
                        }
                        download?.status == DownloadStatus.PAUSED -> {
                            paused.add(episode.id)
                        }
                        progress in 1..99 || download?.status == DownloadStatus.DOWNLOADING ||
                            download?.status == DownloadStatus.PENDING -> {
                            downloading.add(episode.id)
                        }
                    }
                }

                // Atomic state updates
                _downloadedEpisodes.value = downloaded
                _downloadingEpisodes.value = downloading
                _pausedEpisodes.value = paused
                _episodeProgress.value = progressUpdate
            }
        }
    }

    // Season selection - persisted in SavedStateHandle for process death recovery
    fun selectSeason(season: Int) {
        _selectedSeason.value = season
        savedStateHandle[KEY_SELECTED_SEASON] = season
    }

    // Toggle actions
    fun toggleFavorite() {
        val series = currentSeries ?: return
        viewModelScope.launch {
            if (_isFavorite.value) {
                favoritesRepository.removeSeriesFromFavorites(series.id)
                _toastMessage.value = "Removed from favorites"
            } else {
                favoritesRepository.addSeriesToFavorites(series)
                _toastMessage.value = "Added to favorites"
            }
            _isFavorite.value = !_isFavorite.value
        }
    }

    fun toggleMonitored() {
        val series = currentSeries ?: return
        viewModelScope.launch {
            if (_isMonitored.value) {
                watchlistRepository.removeSeriesFromMonitored(series.id)
                _toastMessage.value = "Stopped monitoring"
            } else {
                watchlistRepository.addSeriesToMonitored(series)
                _toastMessage.value = "Monitoring new episodes"
            }
            _isMonitored.value = !_isMonitored.value
        }
    }

    // Bulk download management
    // VM-H4 FIX: Update UI after all operations complete (already using viewModelScope.launch)
    fun clearAllDownloads() {
        val totalEpisodes = episodesBySeason.values.flatten().size
        if (_downloadedEpisodes.value.size != totalEpisodes) {
            _toastMessage.value = "Tap an episode's download button to download it"
            return
        }

        viewModelScope.launch {
            _downloadingAll.value = true
            try {
                episodesBySeason.values.flatten().forEach { episode ->
                    try {
                        downloadManager.deleteDownload(DownloadConstants.episodeId(episode.id))
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to delete episode ${episode.id}: ${e.message}")
                    }
                }
                // VM-H4: Update UI state after all deletes complete
                _downloadedEpisodes.value = emptySet()
                _toastMessage.value = "All downloads removed"
            } finally {
                _downloadingAll.value = false
            }
        }
    }

    // Episode download actions
    fun startEpisodeDownload(episode: Episode) {
        val series = currentSeries ?: return

        if (_downloadedEpisodes.value.contains(episode.id)) {
            // Remove download
            viewModelScope.launch {
                downloadManager.deleteDownload(DownloadConstants.episodeId(episode.id))
                _downloadedEpisodes.value = _downloadedEpisodes.value - episode.id
                _toastMessage.value = "Download removed"
            }
            return
        }

        if (_downloadingEpisodes.value.contains(episode.id) ||
            _pausedEpisodes.value.contains(episode.id) ||
            _scrapingEpisodeId.value == episode.id) {
            return
        }

        val episodeUrl = episode.farsilandUrl
        if (episodeUrl.isNullOrBlank()) {
            _toastMessage.value = "Video source not available"
            return
        }

        // Define scrape action for retry
        lastScrapeAction = { performEpisodeScrapeAndDownload(series, episode, episodeUrl) }
        performEpisodeScrapeAndDownload(series, episode, episodeUrl)
    }

    private fun performEpisodeScrapeAndDownload(series: Series, episode: Episode, url: String) {
        viewModelScope.launch {
            Log.d(TAG, "Starting episode download, URL: $url")
            _scrapingEpisodeId.value = episode.id
            _scrapeError.value = null

            try {
                val result = withContext(Dispatchers.IO) {
                    VideoUrlScraper.extractVideoUrls(url)
                }

                val videoUrls: List<VideoUrl> = result.getOrNull() ?: emptyList()
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
                    val episodeInfo = "S${episode.season} E${episode.episode}"
                    _toastMessage.value = "Downloading $episodeInfo (${bestUrl.quality})..."
                    downloadManager.queueEpisodeDownload(
                        episodeId = episode.id,
                        seriesTitle = series.title,
                        episodeInfo = episodeInfo,
                        posterUrl = episode.thumbnailUrl ?: episode.episodePosterUrl,
                        videoUrl = bestUrl.url
                    )
                    _downloadingEpisodes.value = _downloadingEpisodes.value + episode.id
                    lastScrapeAction = null
                } else {
                    _scrapeError.value = "No video URL found for S${episode.season} E${episode.episode}"
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to scrape video URL: ${e.message}", e)
                _scrapeError.value = e.message ?: "Failed to find video"
            } finally {
                _scrapingEpisodeId.value = null
            }
        }
    }

    fun pauseEpisodeDownload(episodeId: Int) {
        viewModelScope.launch {
            downloadManager.pauseDownload(DownloadConstants.episodeId(episodeId))
            _pausedEpisodes.value = _pausedEpisodes.value + episodeId
            _downloadingEpisodes.value = _downloadingEpisodes.value - episodeId
            _toastMessage.value = "Download paused"
        }
    }

    fun resumeEpisodeDownload(episodeId: Int) {
        viewModelScope.launch {
            downloadManager.resumeDownload(DownloadConstants.episodeId(episodeId))
            _pausedEpisodes.value = _pausedEpisodes.value - episodeId
            _downloadingEpisodes.value = _downloadingEpisodes.value + episodeId
            _toastMessage.value = "Download resumed"
        }
    }

    fun cancelEpisodeDownload(episodeId: Int) {
        viewModelScope.launch {
            downloadManager.cancelDownload(DownloadConstants.episodeId(episodeId))
            _downloadingEpisodes.value = _downloadingEpisodes.value - episodeId
            _pausedEpisodes.value = _pausedEpisodes.value - episodeId
            _toastMessage.value = "Download cancelled"
        }
    }

    fun retryDownload() {
        _scrapeError.value = null
        lastScrapeAction?.invoke()
    }

    fun clearScrapeError() {
        _scrapeError.value = null
    }

    fun clearToast() {
        _toastMessage.value = null
    }

    // Helper functions
    fun getEpisodeProgress(episodeId: Int): Int {
        return _episodeProgress.value[episodeId] ?: 0
    }

    fun isEpisodeDownloaded(episodeId: Int): Boolean {
        return _downloadedEpisodes.value.contains(episodeId)
    }

    fun isEpisodeDownloading(episodeId: Int): Boolean {
        return _downloadingEpisodes.value.contains(episodeId)
    }

    fun isEpisodePaused(episodeId: Int): Boolean {
        return _pausedEpisodes.value.contains(episodeId)
    }
}
