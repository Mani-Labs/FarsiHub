package com.example.farsilandtv

import android.os.Bundle
import androidx.fragment.app.FragmentActivity

/**
 * Activity for browsing user playlists
 * Hosts PlaylistsFragment for grid-based playlist browsing
 */
class PlaylistsActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_playlists)
    }
}
