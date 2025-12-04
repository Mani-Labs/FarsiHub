package com.example.farsilandtv

import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.lifecycleScope
import com.example.farsilandtv.data.download.DownloadManager
import com.example.farsilandtv.data.models.Episode
import com.example.farsilandtv.data.models.Series
import com.example.farsilandtv.data.repository.ContentRepository
import com.example.farsilandtv.data.repository.FavoritesRepository
import com.example.farsilandtv.data.repository.WatchlistRepository
import com.example.farsilandtv.ui.components.ShimmerDetailsScreen
import com.example.farsilandtv.ui.screens.SeriesDetailsScreen
import com.example.farsilandtv.ui.screens.phone.PhoneSeriesDetailsScreen
import com.example.farsilandtv.ui.theme.FarsilandTVTheme
import com.example.farsilandtv.utils.DeviceUtils
import com.example.farsilandtv.utils.IntentExtras
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Series Details Activity - displays episodes using Compose TV
 * Scrapes episode list from series page and groups by season
 *
 * Back navigation: Returns to previous screen (not home/exit)
 * Phase 2.1: Migrated to Compose TV from SeriesDetailsFragment
 */
@AndroidEntryPoint
class SeriesDetailsActivity : ComponentActivity() {

    companion object {
        private const val TAG = "SeriesDetailsActivity"
        const val EXTRA_SERIES = "series"
    }

    @Inject lateinit var contentRepository: ContentRepository
    @Inject lateinit var favoritesRepository: FavoritesRepository
    @Inject lateinit var watchlistRepository: WatchlistRepository
    @Inject lateinit var downloadManager: DownloadManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Phase 7: Set orientation based on device type
        requestedOrientation = when (DeviceUtils.getDeviceType(this)) {
            DeviceUtils.DeviceType.PHONE -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            DeviceUtils.DeviceType.TV,
            DeviceUtils.DeviceType.TABLET -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
        // contentRepository is now Hilt-injected

        // Setup back press handler
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                Log.d(TAG, "Back pressed from series details")
                finish()
            }
        })

        // Get series data from intent
        val series = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getSerializableExtra(EXTRA_SERIES, Series::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getSerializableExtra(EXTRA_SERIES) as? Series
        }

        if (series == null) {
            Toast.makeText(this, "No series data provided", Toast.LENGTH_LONG).show()
            Log.e(TAG, "No series data provided, finishing activity")
            finish()
            return
        }

        Log.d(TAG, "Loading series: ${series.title}")

        // Detect device type for responsive UI
        val deviceType = DeviceUtils.getDeviceType(this)
        Log.d(TAG, "Device type: $deviceType")

        setContent {
            FarsilandTVTheme {
                var episodesBySeason by remember { mutableStateOf<Map<Int, List<Episode>>?>(null) }
                var similarSeries by remember { mutableStateOf<List<Series>>(emptyList()) }
                var isLoading by remember { mutableStateOf(true) }
                var errorMessage by remember { mutableStateOf<String?>(null) }

                // Load episodes on launch
                LaunchedEffect(series.id) {
                    try {
                        val result = contentRepository.getEpisodes(
                            series.id,
                            series.farsilandUrl ?: ""
                        )

                        val episodes = result.getOrNull()
                        if (episodes.isNullOrEmpty()) {
                            errorMessage = "No episodes found for this series"
                        } else {
                            episodesBySeason = episodes
                            Log.d(TAG, "Loaded ${episodes.values.flatten().size} episodes")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error loading episodes", e)
                        errorMessage = "Error loading episodes: ${e.message}"
                    } finally {
                        isLoading = false
                    }
                }

                // Load similar series based on genres
                LaunchedEffect(series.id) {
                    try {
                        val result = contentRepository.getTvShows(page = 1, perPage = 50)
                        result.getOrNull()?.let { allSeries ->
                            val currentGenres = series.genres.toSet()
                            similarSeries = allSeries
                                .filter { it.id != series.id }
                                .filter { otherSeries ->
                                    // Match if they share at least one genre
                                    otherSeries.genres.any { it in currentGenres }
                                }
                                .take(10) // Limit to 10 similar shows
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to load similar series", e)
                    }
                }

                when {
                    isLoading -> {
                        // Show shimmer loading state
                        ShimmerDetailsScreen()
                    }
                    errorMessage != null -> {
                        // Show error state
                        LaunchedEffect(errorMessage) {
                            Toast.makeText(this@SeriesDetailsActivity, errorMessage, Toast.LENGTH_LONG).show()
                            finish()
                        }
                    }
                    episodesBySeason != null -> {
                        // Show series details based on device type
                        when (deviceType) {
                            DeviceUtils.DeviceType.TV,
                            DeviceUtils.DeviceType.TABLET -> {
                                // TV/Tablet: Use D-pad optimized screen
                                SeriesDetailsScreen(
                                    series = series,
                                    episodesBySeason = episodesBySeason!!,
                                    favoritesRepo = favoritesRepository,
                                    watchlistRepo = watchlistRepository,
                                    downloadManager = downloadManager,
                                    onBackClick = { finish() },
                                    onPlayEpisode = { episode -> playEpisode(episode) },
                                    onSeriesClick = { navigateToSeries(it) },
                                    similarSeries = similarSeries
                                )
                            }
                            DeviceUtils.DeviceType.PHONE -> {
                                // Phone: Use touch-optimized screen
                                PhoneSeriesDetailsScreen(
                                    series = series,
                                    episodesBySeason = episodesBySeason!!,
                                    favoritesRepo = favoritesRepository,
                                    watchlistRepo = watchlistRepository,
                                    downloadManager = downloadManager,
                                    onBackClick = { finish() },
                                    onPlayEpisode = { episode -> playEpisode(episode) },
                                    onSeriesClick = { navigateToSeries(it) },
                                    similarSeries = similarSeries
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun playEpisode(episode: Episode) {
        Log.d(TAG, "Starting playback for: ${episode.title}")
        val intent = Intent(this, VideoPlayerActivity::class.java).apply {
            putExtra(IntentExtras.CONTENT_TYPE, IntentExtras.ContentType.EPISODE)
            putExtra(IntentExtras.CONTENT_ID, episode.id)
            putExtra(IntentExtras.CONTENT_TITLE, episode.title)
            putExtra(IntentExtras.CONTENT_URL, episode.farsilandUrl)
            putExtra(IntentExtras.CONTENT_POSTER_URL, episode.thumbnailUrl)
            episode.seriesId?.let { putExtra(IntentExtras.SERIES_ID, it) }
            putExtra(IntentExtras.EPISODE_SEASON, episode.season)
            putExtra(IntentExtras.EPISODE_NUMBER, episode.episode)
        }
        startActivity(intent)
    }

    private fun navigateToSeries(series: Series) {
        Log.d(TAG, "Navigating to similar series: ${series.title}")
        val intent = Intent(this, SeriesDetailsActivity::class.java).apply {
            putExtra(EXTRA_SERIES, series)
        }
        startActivity(intent)
    }

    // Legacy stubs for SeriesDetailsFragment compatibility (not used with Compose)
    @Deprecated("Legacy fragment support - not used with Compose UI")
    fun getSeriesData(): Series? = null

    @Deprecated("Legacy fragment support - not used with Compose UI")
    fun getEpisodesData(): Map<Int, List<Episode>>? = null
}
