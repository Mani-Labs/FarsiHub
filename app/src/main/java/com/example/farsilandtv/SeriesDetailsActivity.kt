package com.example.farsilandtv

import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.example.farsilandtv.data.models.Episode
import com.example.farsilandtv.data.models.Series
import com.example.farsilandtv.data.repository.ContentRepository
import com.example.farsilandtv.data.scraper.EpisodeListScraper
import kotlinx.coroutines.launch

/**
 * Series Details Activity - displays episodes for a TV series
 * Scrapes episode list from series page and groups by season
 *
 * Back navigation: Returns to previous screen (not home/exit)
 */
class SeriesDetailsActivity : FragmentActivity() {

    companion object {
        private const val TAG = "SeriesDetailsActivity"
        const val EXTRA_SERIES = "series"
        private const val STATE_SERIES = "state_series"
        private const val STATE_EPISODES_DATA = "state_episodes_data"
    }

    private var series: Series? = null
    private var episodesData: Map<Int, List<Episode>>? = null
    private lateinit var contentRepository: ContentRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_series_details)

        // Initialize repository
        contentRepository = ContentRepository(applicationContext)

        // Setup back press handler
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                Log.d(TAG, "Back pressed from series details")
                finish()
            }
        })

        // Restore from savedInstanceState if available (configuration change)
        if (savedInstanceState != null) {
            series = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                savedInstanceState.getSerializable(STATE_SERIES, Series::class.java)
            } else {
                @Suppress("DEPRECATION")
                savedInstanceState.getSerializable(STATE_SERIES) as? Series
            }

            Log.d(TAG, "Restored series from savedInstanceState: ${series?.title}")
            // Episodes not saved (not Serializable), will reload from database/scrape
        }

        // Get series data from intent if not restored (first launch)
        if (series == null) {
            series = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getSerializableExtra(EXTRA_SERIES, Series::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getSerializableExtra(EXTRA_SERIES) as? Series
            }
        }

        if (series == null) {
            Toast.makeText(this, "Error: No series data provided", Toast.LENGTH_LONG).show()
            Log.e(TAG, "No series data provided, finishing activity")
            finish()
            return
        }

        Log.d(TAG, "Loading series: ${series?.title}")
        Log.d(TAG, "URL: ${series?.farsilandUrl}")

        loadEpisodes()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // Save only series data (Episode objects are not Serializable)
        // Episodes will be reloaded from database/scraped on restore (2-5 seconds)
        series?.let { outState.putSerializable(STATE_SERIES, it) }
        Log.d(TAG, "Saved state: ${series?.title}")
    }

    /**
     * Get series data for the fragment
     */
    fun getSeriesData(): Series? = series

    /**
     * Get episodes data for the fragment
     */
    fun getEpisodesData(): Map<Int, List<Episode>>? = episodesData

    private fun loadEpisodes() {
        Toast.makeText(this, "Loading episodes...", Toast.LENGTH_SHORT).show()

        lifecycleScope.launch {
            try {
                // Use ContentRepository (checks database first for Namakade, then scrapes if needed)
                val result = contentRepository.getEpisodes(
                    series?.id ?: 0,
                    series?.farsilandUrl ?: ""
                )

                val episodesBySeason = result.getOrNull()

                if (episodesBySeason.isNullOrEmpty()) {
                    Toast.makeText(
                        this@SeriesDetailsActivity,
                        "No episodes found for this series",
                        Toast.LENGTH_LONG
                    ).show()
                    finish()
                    return@launch
                }

                Log.d(TAG, "Found ${episodesBySeason.values.flatten().size} episodes in ${episodesBySeason.size} seasons")
                displayEpisodes(episodesBySeason)

            } catch (e: Exception) {
                Log.e(TAG, "Error loading episodes", e)
                Toast.makeText(
                    this@SeriesDetailsActivity,
                    getString(R.string.error_loading_episodes, e.message ?: "Unknown error"),
                    Toast.LENGTH_LONG
                ).show()
                finish()
            }
        }
    }

    private fun displayEpisodes(episodesBySeason: Map<Int, List<Episode>>) {
        // Store episodes data for fragment
        episodesData = episodesBySeason

        val totalEpisodes = episodesBySeason.values.flatten().size
        val seasons = episodesBySeason.keys.sorted()

        Log.d(TAG, "Displaying $totalEpisodes episodes in ${seasons.size} seasons")

        // Update series metadata with actual scraped season count
        series?.let { currentSeries ->
            if (currentSeries.totalSeasons != seasons.size) {
                Log.d(TAG, "Updating season count from ${currentSeries.totalSeasons} to ${seasons.size}")

                // Update in-memory series object
                series = currentSeries.copy(
                    totalSeasons = seasons.size,
                    totalEpisodes = totalEpisodes
                )

                // Update database for persistence
                lifecycleScope.launch {
                    try {
                        val repository = com.example.farsilandtv.data.repository.ContentRepository(this@SeriesDetailsActivity)
                        repository.updateSeriesMetadata(currentSeries.id, seasons.size, totalEpisodes)
                        Log.d(TAG, "Successfully updated series metadata in database")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to update series metadata", e)
                    }
                }
            }
        }

        // Create and add the series details fragment
        val fragment = SeriesDetailsFragment()

        supportFragmentManager.beginTransaction()
            .replace(R.id.series_details_fragment, fragment)
            .commit()
    }
}
