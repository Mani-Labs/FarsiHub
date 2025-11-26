package com.example.farsilandtv

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import com.example.farsilandtv.data.models.Movie
import com.example.farsilandtv.ui.screens.MovieDetailsScreen
import com.example.farsilandtv.ui.theme.FarsilandTVTheme
import com.example.farsilandtv.utils.IntentExtras

/**
 * Movie Details Activity - displays details using Compose TV
 *
 * Back navigation: Returns to previous screen (not home/exit)
 * Phase 2.1: Migrated to Compose TV from MovieDetailsFragment
 */
class DetailsActivity : ComponentActivity() {

    companion object {
        private const val TAG = "DetailsActivity"
        const val SHARED_ELEMENT_NAME = "hero"
        const val EXTRA_MOVIE = "movie"
        const val MOVIE = "Movie" // Legacy constant for old fragments
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Setup back press handler
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                Log.d(TAG, "Back pressed from movie details")
                finish()
            }
        })

        // Get movie from intent
        val movie = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getSerializableExtra(EXTRA_MOVIE, Movie::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getSerializableExtra(EXTRA_MOVIE) as? Movie
        }

        if (movie != null) {
            Log.d(TAG, "Opening details for movie: ${movie.title}")

            setContent {
                FarsilandTVTheme {
                    MovieDetailsScreen(
                        movie = movie,
                        onBackClick = { finish() },
                        onPlayClick = { playMovie(it) },
                        onMovieClick = { /* Navigate to movie details */ }
                    )
                }
            }
        } else {
            Log.e(TAG, "No movie data provided, finishing activity")
            finish()
        }
    }

    private fun playMovie(movie: Movie) {
        Log.d(TAG, "Starting playback for: ${movie.title}")
        val intent = Intent(this, VideoPlayerActivity::class.java).apply {
            putExtra(IntentExtras.CONTENT_TYPE, IntentExtras.ContentType.MOVIE)
            putExtra(IntentExtras.CONTENT_ID, movie.id)
            putExtra(IntentExtras.CONTENT_TITLE, movie.title)
            putExtra(IntentExtras.CONTENT_URL, movie.farsilandUrl)
            putExtra(IntentExtras.CONTENT_POSTER_URL, movie.posterUrl)
        }
        startActivity(intent)
    }
}