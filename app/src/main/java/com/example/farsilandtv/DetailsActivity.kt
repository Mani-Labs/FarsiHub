package com.example.farsilandtv

import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.FragmentActivity
import com.example.farsilandtv.data.models.Movie

/**
 * Movie Details Activity - displays details and allows playing a movie
 *
 * Back navigation: Returns to previous screen (not home/exit)
 */
class DetailsActivity : FragmentActivity() {

    companion object {
        private const val TAG = "DetailsActivity"
        const val SHARED_ELEMENT_NAME = "hero"
        const val EXTRA_MOVIE = "movie"
        const val MOVIE = "Movie" // Legacy constant for old fragments
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_details)

        // Setup back press handler
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                Log.d(TAG, "Back pressed from movie details")
                finish()
            }
        })

        if (savedInstanceState == null) {
            val movie = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getSerializableExtra(EXTRA_MOVIE, Movie::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getSerializableExtra(EXTRA_MOVIE) as? Movie
            }

            if (movie != null) {
                Log.d(TAG, "Opening details for movie: ${movie.title}")
                val fragment = MovieDetailsFragment()
                val args = Bundle().apply {
                    putSerializable(MovieDetailsFragment.ARG_MOVIE, movie)
                }
                fragment.arguments = args

                supportFragmentManager.beginTransaction()
                    .replace(R.id.details_fragment, fragment)
                    .commitNow()
            } else {
                Log.e(TAG, "No movie data provided, finishing activity")
                finish()
            }
        }
    }
}