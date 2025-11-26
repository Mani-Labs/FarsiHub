package com.example.farsilandtv

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.farsilandtv.data.models.Episode
import com.example.farsilandtv.data.models.FeaturedContent
import com.example.farsilandtv.data.models.Movie
import com.example.farsilandtv.data.models.Series
import com.example.farsilandtv.ui.screens.HomeScreenWithSidebar
import com.example.farsilandtv.ui.theme.FarsilandTVTheme
import com.example.farsilandtv.ui.viewmodel.MainViewModel
import com.example.farsilandtv.utils.IntentExtras

/**
 * HomeComposeFragment - Compose wrapper for HomeScreenWithSidebar
 * Phase 3.3: Replaces HomeFragment with Compose TV implementation
 * with sidebar navigation menu
 */
class HomeComposeFragment : Fragment() {

    private lateinit var viewModel: MainViewModel
    private var composeView: ComposeView? = null
    private var wrapper: FrameLayout? = null

    // Flag to block key events during navigation to prevent focus crash
    @Volatile
    private var isNavigating = false

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

        // Wrap ComposeView in FrameLayout that intercepts ALL key events during navigation
        // This prevents the Compose focus crash when fragment is being replaced
        val wrapperView = object : FrameLayout(requireContext()) {
            override fun dispatchKeyEvent(event: KeyEvent?): Boolean {
                // Block ALL key events during navigation to prevent Compose focus crash
                // Must consume the event completely (return true) to prevent propagation
                if (isNavigating) {
                    return true
                }
                // Wrap in try-catch to handle race condition where ComposeView
                // gets detached during the super call (LayoutCoordinate crash)
                return try {
                    super.dispatchKeyEvent(event)
                } catch (e: IllegalStateException) {
                    // Compose focus system tried to access detached view
                    // Consume the event to prevent crash
                    true
                }
            }

            // Also intercept at onInterceptTouchEvent level for completeness
            override fun onInterceptTouchEvent(ev: android.view.MotionEvent?): Boolean {
                if (isNavigating) return true
                return super.onInterceptTouchEvent(ev)
            }
        }
        wrapper = wrapperView

        val compose = ComposeView(requireContext()).apply {
            composeView = this
            // Dispose composition when view lifecycle is destroyed
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)

            setContent {
                FarsilandTVTheme {
                    HomeScreenWithSidebar(
                        onMovieClick = { movie -> openMovieDetails(movie) },
                        onSeriesClick = { series -> openSeriesDetails(series) },
                        onEpisodeClick = { episode -> playEpisode(episode) },
                        onFeaturedClick = { content -> handleFeaturedClick(content) },
                        onNavigate = { destination -> handleNavigation(destination) },
                        viewModel = viewModel
                    )
                }
            }
        }

        wrapperView.addView(compose, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))

        return wrapperView
    }

    override fun onResume() {
        super.onResume()
        // Reset navigation flag when returning to this fragment
        isNavigating = false
    }

    override fun onDestroyView() {
        composeView = null
        wrapper = null
        super.onDestroyView()
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
            putExtra(IntentExtras.CONTENT_TYPE, IntentExtras.ContentType.EPISODE)
            putExtra(IntentExtras.CONTENT_ID, episode.id)
            putExtra(IntentExtras.CONTENT_TITLE, episode.title)
            putExtra(IntentExtras.CONTENT_URL, episode.farsilandUrl)
            episode.seriesId?.let { putExtra(IntentExtras.SERIES_ID, it) }
            putExtra(IntentExtras.EPISODE_SEASON, episode.season)
            putExtra(IntentExtras.EPISODE_NUMBER, episode.episode)
            putExtra(IntentExtras.CONTENT_POSTER_URL, episode.thumbnailUrl)
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

    private fun handleNavigation(destination: String) {
        when (destination) {
            "refresh" -> {
                // Refresh content by reloading from ViewModel
                viewModel.loadContent()
            }
            "home" -> {
                // Already on home, do nothing
            }
            else -> {
                // CRITICAL: Set flag FIRST before ANY other operations
                // This blocks key events at the wrapper level
                isNavigating = true

                // Dispose and remove ComposeView to prevent focus crash
                composeView?.let { cv ->
                    // Clear focus first
                    cv.clearFocus()
                    // Hide the view to prevent any visual focus operations
                    cv.visibility = View.GONE
                    // Dispose the Compose composition completely
                    cv.disposeComposition()
                    // Remove from parent to prevent any lingering key events
                    wrapper?.removeView(cv)
                }
                composeView = null

                // Delegate to MainActivity for navigation
                (activity as? MainActivity)?.navigateTo(destination)
            }
        }
    }
}
