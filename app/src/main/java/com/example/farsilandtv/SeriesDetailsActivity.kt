package com.example.farsilandtv

import android.content.Intent
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
import com.example.farsilandtv.data.models.Episode
import com.example.farsilandtv.data.models.Series
import com.example.farsilandtv.data.repository.ContentRepository
import com.example.farsilandtv.ui.screens.SeriesDetailsScreen
import com.example.farsilandtv.ui.theme.FarsilandTVTheme
import kotlinx.coroutines.launch

/**
 * Series Details Activity - displays episodes using Compose TV
 * Scrapes episode list from series page and groups by season
 *
 * Back navigation: Returns to previous screen (not home/exit)
 * Phase 2.1: Migrated to Compose TV from SeriesDetailsFragment
 */
class SeriesDetailsActivity : ComponentActivity() {

    companion object {
        private const val TAG = "SeriesDetailsActivity"
        const val EXTRA_SERIES = "series"
    }

    private lateinit var contentRepository: ContentRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize repository
        contentRepository = ContentRepository.getInstance(applicationContext)

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

        setContent {
            FarsilandTVTheme {
                var episodesBySeason by remember { mutableStateOf<Map<Int, List<Episode>>?>(null) }
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

                when {
                    isLoading -> {
                        // Show loading state
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Loading episodes...",
                                color = Color.White,
                                style = androidx.compose.material3.MaterialTheme.typography.titleLarge
                            )
                        }
                    }
                    errorMessage != null -> {
                        // Show error state
                        LaunchedEffect(errorMessage) {
                            Toast.makeText(this@SeriesDetailsActivity, errorMessage, Toast.LENGTH_LONG).show()
                            finish()
                        }
                    }
                    episodesBySeason != null -> {
                        // Show series details
                        SeriesDetailsScreen(
                            series = series,
                            episodesBySeason = episodesBySeason!!,
                            onBackClick = { finish() },
                            onPlayEpisode = { episode -> playEpisode(episode) },
                            onSeriesClick = { /* Navigate to series details */ }
                        )
                    }
                }
            }
        }
    }

    private fun playEpisode(episode: Episode) {
        Log.d(TAG, "Starting playback for: ${episode.title}")
        val intent = Intent(this, VideoPlayerActivity::class.java).apply {
            putExtra("CONTENT_TYPE", "episode")
            putExtra("CONTENT_ID", episode.id)
            putExtra("CONTENT_TITLE", episode.title)
            putExtra("CONTENT_URL", episode.farsilandUrl)
            putExtra("CONTENT_POSTER_URL", episode.thumbnailUrl)
            episode.seriesId?.let { putExtra("SERIES_ID", it) }
            putExtra("EPISODE_SEASON", episode.season)
            putExtra("EPISODE_NUMBER", episode.episode)
        }
        startActivity(intent)
    }

    // Legacy stubs for SeriesDetailsFragment compatibility (not used with Compose)
    @Deprecated("Legacy fragment support - not used with Compose UI")
    fun getSeriesData(): Series? = null

    @Deprecated("Legacy fragment support - not used with Compose UI")
    fun getEpisodesData(): Map<Int, List<Episode>>? = null
}
