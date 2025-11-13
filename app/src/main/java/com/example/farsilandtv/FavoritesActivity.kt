package com.example.farsilandtv

import android.os.Bundle
import androidx.fragment.app.FragmentActivity

/**
 * Activity to host the FavoritesFragment
 * Displays all favorited movies and TV series in a grid layout
 */
class FavoritesActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_favorites)
    }
}
