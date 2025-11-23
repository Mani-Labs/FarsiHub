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
 * HomeComposeFragment - Compose wrapper for HomeScreen
 * Phase 3.3: Replaces HomeFragment with Compose TV implementation
 * while maintaining fragment-based navigation compatibility
 */
class HomeComposeFragment : Fragment() {

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
        // Load content when fragment is created
        viewModel.loadContent()

        return ComposeView(requireContext()).apply {
            // Dispose composition when view lifecycle is destroyed
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)

            setContent {
                FarsilandTVTheme {
                    HomeScreen(
                        onMovieClick = { movie -> openMovieDetails(movie) },
                        onSeriesClick = { series -> openSeriesDetails(series) },
                        onEpisodeClick = { episode -> playEpisode(episode) },
                        onFeaturedClick = { content -> handleFeaturedClick(content) },
                        viewModel = viewModel
                    )
                }
            }
        }
    }

    private fun openMovieDetails(movie: Movie) {
        val intent = Intent(requireContext(), DetailsActivity::class.java).apply {
            putExtra(DetailsActivity.EXTRA_MOVIE, movie)
        }
        startActivity(intent)
    }

    private fun openSeriesDetails(series: Series) {
        val intent = Intent(requireContext(), SeriesDetailsActivity::class.java).apply {
            putExtra(SeriesDetailsActivity.EXTRA_SERIES, series)
        }
        startActivity(intent)
    }

    private fun playEpisode(episode: Episode) {
        val intent = Intent(requireContext(), VideoPlayerActivity::class.java).apply {
            putExtra("CONTENT_TYPE", "episode")
            putExtra("CONTENT_ID", episode.id)
            putExtra("CONTENT_TITLE", episode.title)
            putExtra("CONTENT_URL", episode.farsilandUrl)
            episode.seriesId?.let { putExtra("SERIES_ID", it) }
            putExtra("EPISODE_SEASON", episode.season)
            putExtra("EPISODE_NUMBER", episode.episode)
            putExtra("CONTENT_POSTER_URL", episode.thumbnailUrl)
        }
        startActivity(intent)
    }

    private fun handleFeaturedClick(content: FeaturedContent) {
        when (content) {
            is FeaturedContent.FeaturedMovie -> {
                val movie = Movie(
                    id = content.id,
                    title = content.title,
                    description = content.description,
                    posterUrl = content.posterUrl,
                    farsilandUrl = content.farsilandUrl
                )
                openMovieDetails(movie)
            }
            is FeaturedContent.FeaturedSeries -> {
                val series = Series(
                    id = content.id,
                    title = content.title,
                    description = content.description,
                    posterUrl = content.posterUrl,
                    farsilandUrl = content.farsilandUrl
                )
                openSeriesDetails(series)
            }
        }
    }
}
