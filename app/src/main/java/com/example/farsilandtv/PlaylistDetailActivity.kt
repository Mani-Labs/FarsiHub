package com.example.farsilandtv

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import dagger.hilt.android.AndroidEntryPoint

/**
 * Activity for viewing playlist details and items
 * Hosts PlaylistDetailFragment
 */
@AndroidEntryPoint
class PlaylistDetailActivity : FragmentActivity() {

    companion object {
        const val EXTRA_PLAYLIST_ID = "playlist_id"
    }

    private var playlistId: Long = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_playlist_detail)

        playlistId = intent.getLongExtra(EXTRA_PLAYLIST_ID, -1)
        if (playlistId == -1L) {
            finish()
            return
        }
    }

    fun getPlaylistId(): Long = playlistId
}
