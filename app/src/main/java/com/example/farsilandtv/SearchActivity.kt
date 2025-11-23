package com.example.farsilandtv

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import com.example.farsilandtv.data.models.Movie
import com.example.farsilandtv.data.models.Series
import com.example.farsilandtv.ui.screens.SearchScreen
import com.example.farsilandtv.ui.theme.FarsilandTVTheme

/**
 * Search Activity - displays search interface using Compose TV
 * Supports voice search and search-as-you-type
 *
 * Back navigation: Returns to previous screen (not home/exit)
 * Phase 2.3: Migrated to Compose TV from SearchFragment
 */
class SearchActivity : ComponentActivity() {

    companion object {
        private const val TAG = "SearchActivity"
    }

    // State for voice search support
    private var initialSearchQuery by mutableStateOf("")
    private var queryKey by mutableStateOf(0) // Key for forcing recomposition

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Setup back press handler
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                Log.d(TAG, "Back pressed from search")
                finish()
            }
        })

        // Extract initial search query from intent (voice search)
        initialSearchQuery = intent.getStringExtra(android.app.SearchManager.QUERY) ?: ""
        if (initialSearchQuery.isNotEmpty()) {
            Log.d(TAG, "Initial voice search query: $initialSearchQuery")
        }

        setContent {
            FarsilandTVTheme {
                // Use key to force recomposition when voice search query changes
                key(queryKey) {
                    SearchScreen(
                        initialQuery = initialSearchQuery,
                        onMovieClick = { movie -> openMovieDetails(movie) },
                        onSeriesClick = { series -> openSeriesDetails(series) }
                    )
                }
            }
        }
    }

    /**
     * Handle new search intents when activity is already open (voice search)
     * When launchMode="singleTop", Android delivers new voice search queries via onNewIntent()
     * instead of creating a new activity instance.
     *
     * This ensures voice search works correctly when SearchActivity is already open.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        Log.d(TAG, "New search intent received")

        // Update activity intent to the new one
        setIntent(intent)

        // Extract search query from new intent
        val query = intent.getStringExtra(android.app.SearchManager.QUERY)

        if (!query.isNullOrEmpty()) {
            Log.d(TAG, "Processing new voice search query: $query")
            initialSearchQuery = query
            queryKey++ // Increment key to force SearchScreen recomposition
        }
    }

    private fun openMovieDetails(movie: Movie) {
        Log.d(TAG, "Opening movie details: ${movie.title}")
        val intent = Intent(this, DetailsActivity::class.java).apply {
            putExtra(DetailsActivity.EXTRA_MOVIE, movie)
        }
        startActivity(intent)
    }

    private fun openSeriesDetails(series: Series) {
        Log.d(TAG, "Opening series details: ${series.title}")
        val intent = Intent(this, SeriesDetailsActivity::class.java).apply {
            putExtra(SeriesDetailsActivity.EXTRA_SERIES, series)
        }
        startActivity(intent)
    }
}
