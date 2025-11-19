package com.example.farsilandtv.ui.home

import android.content.Context
import android.graphics.Color
import android.view.ViewGroup
import androidx.leanback.widget.*
import com.example.farsilandtv.HomeFragment

/**
 * H1 REFACTOR: Extracted from HomeFragment.kt
 * Manages navigation cards (Movies, TV Shows, Search, Stats, Sync Settings, Refresh, Options)
 * Reduces HomeFragment from 1,398 lines to ~400 lines
 */
class HomeNavigationManager(private val context: Context) {

    /**
     * Navigation item data class
     */
    data class NavigationItem(val title: String, val destination: String)

    /**
     * Create navigation row with all navigation cards
     */
    fun createNavigationRow(): ListRow {
        val navPresenter = object : Presenter() {
            override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
                val cardView = ImageCardView(parent.context)

                cardView.apply {
                    isFocusable = true
                    isFocusableInTouchMode = true
                    setMainImageDimensions(500, 300)  // Much larger for navigation
                }

                return ViewHolder(cardView)
            }

            override fun onBindViewHolder(viewHolder: ViewHolder, item: Any) {
                val cardView = viewHolder.view as ImageCardView
                val navItem = item as NavigationItem
                cardView.titleText = navItem.title
                cardView.contentText = null  // Remove extra text to make title more prominent

                // Set a placeholder colored image
                val color = when (navItem.destination) {
                    "movies" -> Color.parseColor("#E91E63")
                    "shows" -> Color.parseColor("#9C27B0")
                    "search" -> Color.parseColor("#2196F3")
                    "stats" -> Color.parseColor("#3F51B5")
                    "sync-settings" -> Color.parseColor("#00BCD4")
                    "refresh" -> Color.parseColor("#FF9800")
                    "options" -> Color.parseColor("#4CAF50")
                    else -> Color.parseColor("#757575")
                }

                val drawable = android.graphics.drawable.ColorDrawable(color)
                cardView.mainImageView?.setImageDrawable(drawable)
            }

            override fun onUnbindViewHolder(viewHolder: ViewHolder) {
                val cardView = viewHolder.view as ImageCardView
                cardView.badgeImage = null
                // Don't clear mainImage - it will be set again in onBindViewHolder
                // Clearing it causes colors to disappear when scrolling
            }
        }

        val navAdapter = ArrayObjectAdapter(navPresenter)
        navAdapter.add(NavigationItem("Movies", "movies"))
        navAdapter.add(NavigationItem("TV Shows", "shows"))
        navAdapter.add(NavigationItem("Search", "search"))
        navAdapter.add(NavigationItem("Stats", "stats"))
        navAdapter.add(NavigationItem("Sync Settings", "sync-settings"))
        navAdapter.add(NavigationItem("Refresh", "refresh"))
        navAdapter.add(NavigationItem("Options", "options"))

        return ListRow(HeaderItem(0, "Navigate"), navAdapter)
    }
}
