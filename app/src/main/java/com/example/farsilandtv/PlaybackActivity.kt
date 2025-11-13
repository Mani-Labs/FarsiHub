package com.example.farsilandtv

import android.os.Bundle
import androidx.fragment.app.FragmentActivity

/** Loads [PlaybackVideoFragment]. */
class PlaybackActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_playback)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.playback_fragment_container, PlaybackVideoFragment())
                .commit()
        }
    }
}