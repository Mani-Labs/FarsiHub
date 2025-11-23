package com.example.farsilandtv

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.farsilandtv.data.models.Episode
import com.example.farsilandtv.data.models.FeaturedContent
import com.example.farsilandtv.data.models.Movie
import com.example.farsilandtv.data.models.Series
import com.example.farsilandtv.ui.screens.HomeScreen
import com.example.farsilandtv.ui.theme.FarsilandTVTheme
import com.example.farsilandtv.ui.viewmodel.MainViewModel

/**
 * Fragment wrapper for Compose HomeScreen
 * Integrates Compose UI with existing Fragment-based navigation
 */
class ComposeHomeFragment : Fragment() {

    private lateinit var viewModel: MainViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(this)[MainViewModel::class.java]
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            // Dispose composition when view is detached to prevent memory leaks
            setViewCompositionStrategy(
                ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
            )

            setContent {
                FarsilandTVTheme {
                    HomeScreen(
                        onMovieClick = { movie -> navigateToMovieDetails(movie) },
                        onSeriesClick = { series -> navigateToSeriesDetails(series) },
                        onEpisodeClick = { episode -> navigateToEpisodePlayer(episode) },
                        onFeaturedClick = { content -> navigateToFeaturedContent(content) },
                        viewModel = viewModel
                    )
                }
            }
        }
    }

    private fun navigateToMovieDetails(movie: Movie) {
        val intent = Intent(requireContext(), DetailsActivity::class.java).apply {
            putExtra(DetailsActivity.EXTRA_MOVIE, movie)
        }
        startActivity(intent)
    }

    private fun navigateToSeriesDetails(series: Series) {
        val intent = Intent(requireContext(), SeriesDetailsActivity::class.java).apply {
            putExtra(SeriesDetailsActivity.EXTRA_SERIES, series)
        }
        startActivity(intent)
    }

    private fun navigateToEpisodePlayer(episode: Episode) {
        val intent = Intent(requireContext(), VideoPlayerActivity::class.java).apply {
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

    private fun navigateToFeaturedContent(content: FeaturedContent) {
        when (content) {
            is FeaturedContent.FeaturedMovie -> navigateToMovieDetails(content.movie)
            is FeaturedContent.FeaturedSeries -> navigateToSeriesDetails(content.series)
        }
    }
}
