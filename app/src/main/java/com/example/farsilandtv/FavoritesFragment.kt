package com.example.farsilandtv

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.leanback.app.VerticalGridSupportFragment
import androidx.leanback.widget.*
import androidx.lifecycle.lifecycleScope
import com.example.farsilandtv.data.database.Favorite
import com.example.farsilandtv.data.repository.FavoritesRepository
import com.example.farsilandtv.data.repository.ContentRepository
import com.example.farsilandtv.data.models.Movie
import com.example.farsilandtv.data.models.Series
import com.example.farsilandtv.ui.GenreCardPresenter
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first

/**
 * Favorites Fragment - displays all favorited content (movies and series)
 * Uses VerticalGridSupportFragment for consistent TV app browsing experience
 */
class FavoritesFragment : VerticalGridSupportFragment() {

    companion object {
        private const val TAG = "FavoritesFragment"
        private const val NUM_COLUMNS = 5
    }

    private lateinit var favoritesRepo: FavoritesRepository
    private lateinit var contentRepo: ContentRepository
    private lateinit var gridAdapter: ArrayObjectAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate FavoritesFragment")

        favoritesRepo = FavoritesRepository(requireContext())
        contentRepo = ContentRepository(requireContext())

        setupUI()
        loadFavorites()
    }

    private fun setupUI() {
        title = "My Favorites"

        val gridPresenter = VerticalGridPresenter()
        gridPresenter.numberOfColumns = NUM_COLUMNS
        setGridPresenter(gridPresenter)

        // Setup adapter with ContentCardPresenter for consistent styling
        val cardPresenter = GenreCardPresenter(requireContext())
        gridAdapter = ArrayObjectAdapter(cardPresenter)
        adapter = gridAdapter

        // Setup item click listener
        onItemViewClickedListener = ItemViewClickedListener()
    }

    /**
     * Load all favorites from repository
     */
    private fun loadFavorites() {
        lifecycleScope.launch {
            try {
                Log.d(TAG, "Loading favorites...")

                // Get all favorites
                val favorites = favoritesRepo.getAllFavorites().first()

                if (favorites.isEmpty()) {
                    showEmptyState()
                    return@launch
                }

                Log.d(TAG, "Found ${favorites.size} favorites")

                // Convert Favorite entities to Movie/Series objects for display
                for (favorite in favorites) {
                    when (favorite.contentType) {
                        Favorite.ContentType.MOVIE -> {
                            // Extract movie ID from contentId (format: "movie-123")
                            val movieId = favorite.contentId.removePrefix("movie-").toIntOrNull()
                            if (movieId != null) {
                                // Create a minimal Movie object for display
                                val movie = Movie(
                                    id = movieId,
                                    title = favorite.title,
                                    posterUrl = favorite.posterUrl,
                                    farsilandUrl = "", // Will be loaded when clicked
                                    description = "",
                                    year = null,
                                    rating = null,
                                    genres = emptyList(),
                                    backdropUrl = null
                                )
                                gridAdapter.add(movie)
                            }
                        }
                        Favorite.ContentType.SERIES -> {
                            // Extract series ID from contentId (format: "series-456")
                            val seriesId = favorite.contentId.removePrefix("series-").toIntOrNull()
                            if (seriesId != null) {
                                // Create a minimal Series object for display
                                val series = Series(
                                    id = seriesId,
                                    title = favorite.title,
                                    posterUrl = favorite.posterUrl,
                                    farsilandUrl = "", // Will be loaded when clicked
                                    description = "",
                                    year = null,
                                    rating = null,
                                    genres = emptyList(),
                                    totalSeasons = 0,
                                    totalEpisodes = 0,
                                    backdropUrl = null
                                )
                                gridAdapter.add(series)
                            }
                        }
                    }
                }

                Log.d(TAG, "Displayed ${gridAdapter.size()} favorite items")

            } catch (e: Exception) {
                Log.e(TAG, "Error loading favorites", e)
                Toast.makeText(
                    requireContext(),
                    "Failed to load favorites: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    /**
     * Show empty state message when no favorites
     */
    private fun showEmptyState() {
        Log.d(TAG, "No favorites found - showing empty state")
        Toast.makeText(
            requireContext(),
            "No favorites yet. Add movies or series to your favorites!",
            Toast.LENGTH_LONG
        ).show()
    }

    /**
     * Handle item clicks - open details for movies/series
     */
    private inner class ItemViewClickedListener : OnItemViewClickedListener {
        override fun onItemClicked(
            itemViewHolder: Presenter.ViewHolder,
            item: Any,
            rowViewHolder: RowPresenter.ViewHolder,
            row: Row
        ) {
            when (item) {
                is Movie -> {
                    Log.d(TAG, "Movie clicked: ${item.title}")
                    openMovieDetails(item)
                }
                is Series -> {
                    Log.d(TAG, "Series clicked: ${item.title}")
                    openSeriesDetails(item)
                }
            }
        }
    }

    /**
     * Open movie details activity
     */
    private fun openMovieDetails(movie: Movie) {
        try {
            // If movie has no URL, try to load full details from database
            lifecycleScope.launch {
                val fullMovie = if (movie.farsilandUrl.isEmpty()) {
                    contentRepo.getMovie(movie.id).getOrNull() ?: movie
                } else {
                    movie
                }

                val intent = Intent(requireContext(), DetailsActivity::class.java).apply {
                    putExtra(DetailsActivity.EXTRA_MOVIE, fullMovie)
                }
                startActivity(intent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error opening movie details", e)
            Toast.makeText(
                requireContext(),
                "Error opening details: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    /**
     * Open series details activity
     */
    private fun openSeriesDetails(series: Series) {
        try {
            // If series has no URL, try to load full details from database
            lifecycleScope.launch {
                val fullSeries = if (series.farsilandUrl.isEmpty()) {
                    contentRepo.getTvShow(series.id).getOrNull() ?: series
                } else {
                    series
                }

                val intent = Intent(requireContext(), SeriesDetailsActivity::class.java).apply {
                    putExtra(SeriesDetailsActivity.EXTRA_SERIES, fullSeries)
                }
                startActivity(intent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error opening series details", e)
            Toast.makeText(
                requireContext(),
                "Error opening details: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }
}
