package com.example.farsilandtv

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import dagger.hilt.android.AndroidEntryPoint

/**
 * Activity for browsing user playlists
 * Hosts PlaylistsFragment for grid-based playlist browsing
 */
@AndroidEntryPoint
class PlaylistsActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_playlists)
    }
}
