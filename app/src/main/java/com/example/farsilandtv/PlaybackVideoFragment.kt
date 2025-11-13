package com.example.farsilandtv

import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.leanback.app.VideoSupportFragment
import androidx.leanback.app.VideoSupportFragmentGlueHost
import androidx.leanback.media.PlaybackTransportControlGlue
import androidx.leanback.widget.PlaybackControlsRow
import androidx.media3.common.MediaItem
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.leanback.LeanbackPlayerAdapter
import com.example.farsilandtv.Movie

/** Handles video playback with media controls. */
class PlaybackVideoFragment : VideoSupportFragment() {

    private lateinit var mTransportControlGlue: PlaybackTransportControlGlue<LeanbackPlayerAdapter>
    private var player: ExoPlayer? = null

    companion object {
        private const val TAG = "PlaybackVideoFragment"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val movie = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity?.intent?.getSerializableExtra(DetailsActivity.MOVIE, Movie::class.java)
        } else {
            @Suppress("DEPRECATION")
            activity?.intent?.getSerializableExtra(DetailsActivity.MOVIE) as? Movie
        }

        // Early exit if movie is null
        val movieNonNull = movie ?: run {
            activity?.finish()
            return
        }

        // Extract video URL with null check
        val videoUrl = movieNonNull.videoUrl ?: run {
            activity?.finish()
            return
        }

        // Initialize ExoPlayer - add Referer header only for namakade.com URLs
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")

        // Namakade CDN domains that require Referer header
        val namakadeCdnDomains = listOf(
            "namakade",
            "negahestan.com",
            "iranproud2.net",
            "iranproud.net"
        )

        // Only add Referer header for namakade CDN URLs to bypass anti-scraping protection
        if (namakadeCdnDomains.any { videoUrl.contains(it, ignoreCase = true) }) {
            httpDataSourceFactory.setDefaultRequestProperties(
                mapOf("Referer" to "https://namakade.com/")
            )
        }

        val mediaSourceFactory = DefaultMediaSourceFactory(httpDataSourceFactory)

        player = ExoPlayer.Builder(requireContext())
            .setMediaSourceFactory(mediaSourceFactory)
            .build()

        val glueHost = VideoSupportFragmentGlueHost(this@PlaybackVideoFragment)

        val currentPlayer = player
        if (currentPlayer == null) {
            Log.e(TAG, "Player is null, cannot create adapter")
            activity?.finish()
            return
        }

        val playerAdapter = LeanbackPlayerAdapter(requireContext(), currentPlayer, 16)
        playerAdapter.setRepeatAction(PlaybackControlsRow.RepeatAction.INDEX_NONE)

        mTransportControlGlue = PlaybackTransportControlGlue(requireActivity(), playerAdapter)
        mTransportControlGlue.host = glueHost
        mTransportControlGlue.title = movieNonNull.title
        mTransportControlGlue.subtitle = movieNonNull.description

        // Set media item and prepare
        val mediaItem = MediaItem.fromUri(Uri.parse(videoUrl))
        player?.setMediaItem(mediaItem)
        player?.prepare()

        mTransportControlGlue.playWhenPrepared()
    }

    override fun onPause() {
        super.onPause()
        mTransportControlGlue.pause()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Release ExoPlayer in onDestroyView (guaranteed to be called)
        player?.release()
        player = null
    }

    override fun onDestroy() {
        super.onDestroy()
        // Defensive: release again in case onDestroyView wasn't called
        player?.release()
        player = null
    }
}