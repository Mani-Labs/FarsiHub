package com.example.farsilandtv

import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import com.example.farsilandtv.data.download.DownloadManager
import com.example.farsilandtv.data.models.Movie
import com.example.farsilandtv.data.repository.ContentRepository
import com.example.farsilandtv.data.repository.FavoritesRepository
import com.example.farsilandtv.data.repository.PlaybackRepository
import com.example.farsilandtv.data.repository.WatchlistRepository
import com.example.farsilandtv.ui.screens.MovieDetailsScreen
import com.example.farsilandtv.ui.screens.phone.PhoneMovieDetailsScreen
import com.example.farsilandtv.ui.theme.FarsilandTVTheme
import com.example.farsilandtv.utils.DeviceUtils
import com.example.farsilandtv.utils.IntentExtras
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Movie Details Activity - displays details using Compose TV
 *
 * Back navigation: Returns to previous screen (not home/exit)
 * Phase 2.1: Migrated to Compose TV from MovieDetailsFragment
 */
@AndroidEntryPoint
class DetailsActivity : ComponentActivity() {

    @Inject lateinit var contentRepository: ContentRepository
    @Inject lateinit var favoritesRepository: FavoritesRepository
    @Inject lateinit var playbackRepository: PlaybackRepository
    @Inject lateinit var watchlistRepository: WatchlistRepository
    @Inject lateinit var downloadManager: DownloadManager

    companion object {
        private const val TAG = "DetailsActivity"
        const val SHARED_ELEMENT_NAME = "hero"
        const val EXTRA_MOVIE = "movie"
        const val MOVIE = "Movie" // Legacy constant for old fragments
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // UT-C3 FIX: Validate Hilt injection completed
        if (!::contentRepository.isInitialized || !::favoritesRepository.isInitialized ||
            !::playbackRepository.isInitialized || !::watchlistRepository.isInitialized ||
            !::downloadManager.isInitialized) {
            Log.e(TAG, "CRITICAL: Hilt injection failed, dependencies not initialized")
            finish()
            return
        }

        // Phase 7: Set orientation based on device type
        requestedOrientation = when (DeviceUtils.getDeviceType(this)) {
            DeviceUtils.DeviceType.PHONE -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            DeviceUtils.DeviceType.TV,
            DeviceUtils.DeviceType.TABLET -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }

        // Setup back press handler
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                Log.d(TAG, "Back pressed from movie details")
                finish()
            }
        })

        // M1 FIX: Safe cast with ClassCastException protection
        val movie: Movie? = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getSerializableExtra(EXTRA_MOVIE, Movie::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getSerializableExtra(EXTRA_MOVIE) as? Movie
            }
        } catch (e: ClassCastException) {
            Log.e(TAG, "Invalid movie object in intent", e)
            null
        }

        if (movie != null) {
            Log.d(TAG, "Opening details for movie: ${movie.title}")

            // Detect device type for responsive UI
            val deviceType = DeviceUtils.getDeviceType(this)
            Log.d(TAG, "Device type: $deviceType")

            setContent {
                FarsilandTVTheme {
                    // Load similar movies based on current movie's genres
                    // contentRepository is now Hilt-injected
                    var similarMovies by remember { mutableStateOf<List<Movie>>(emptyList()) }

                    LaunchedEffect(movie.id) {
                        withContext(Dispatchers.IO) {
                            try {
                                // Get movies and filter by genre match (exclude current movie)
                                val result = contentRepository.getMovies(page = 1, perPage = 50)
                                result.getOrNull()?.let { allMovies ->
                                    val currentGenres = movie.genres.toSet()
                                    similarMovies = allMovies
                                        .filter { it.id != movie.id }
                                        .filter { otherMovie: Movie ->
                                            // Match if they share at least one genre
                                            otherMovie.genres.any { genre -> genre in currentGenres }
                                        }
                                        .take(10) // Limit to 10 similar movies
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed to load similar movies", e)
                            }
                        }
                    }

                    when (deviceType) {
                        DeviceUtils.DeviceType.TV,
                        DeviceUtils.DeviceType.TABLET -> {
                            // TV/Tablet: Use existing D-pad optimized screen
                            MovieDetailsScreen(
                                movie = movie,
                                favoritesRepo = favoritesRepository,
                                playbackRepo = playbackRepository,
                                watchlistRepo = watchlistRepository,
                                downloadManager = downloadManager,
                                onBackClick = { finish() },
                                onPlayClick = { playMovie(it) },
                                onMovieClick = { navigateToMovie(it) },
                                similarMovies = similarMovies
                            )
                        }
                        DeviceUtils.DeviceType.PHONE -> {
                            // Phone: Use touch-optimized screen
                            PhoneMovieDetailsScreen(
                                movie = movie,
                                favoritesRepo = favoritesRepository,
                                playbackRepo = playbackRepository,
                                watchlistRepo = watchlistRepository,
                                downloadManager = downloadManager,
                                onBackClick = { finish() },
                                onPlayClick = { playMovie(it) },
                                onMovieClick = { navigateToMovie(it) },
                                similarMovies = similarMovies
                            )
                        }
                    }
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

    private fun navigateToMovie(movie: Movie) {
        Log.d(TAG, "Navigating to similar movie: ${movie.title}")
        val intent = Intent(this, DetailsActivity::class.java).apply {
            putExtra(EXTRA_MOVIE, movie)
        }
        startActivity(intent)
    }
}