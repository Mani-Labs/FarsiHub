package com.example.farsilandtv.ui.home

import android.util.Log
import androidx.leanback.widget.*
import com.example.farsilandtv.utils.SkeletonHelper

/**
 * H1 REFACTOR: Extracted from HomeFragment.kt
 * Manages skeleton loading screens for home fragment
 * Shows placeholder shimmer cards while content is loading
 */
class HomeSkeletonManager(
    private val rowsAdapter: ArrayObjectAdapter
) {

    private var isShowingSkeleton = false

    /**
     * Show skeleton loading screens
     * Displays 3 skeleton rows (Featured, Movies, Series) with shimmer animation
     * Replaces spinning progress bar for better UX
     */
    fun showSkeleton() {
        if (isShowingSkeleton) return

        Log.d(TAG, "Showing skeleton loading screens...")
        isShowingSkeleton = true

        // Clear existing rows except navigation
        val navigationRow = if (rowsAdapter.size() > 0) rowsAdapter.get(0) else null
        rowsAdapter.clear()
        navigationRow?.let { rowsAdapter.add(it) }

        // Add Featured skeleton row (empty header name)
        val featuredRow = ListRow(
            HeaderItem(""),
            SkeletonHelper.createSkeletonAdapter(1)
        )
        rowsAdapter.add(featuredRow)

        // Add Movies skeleton row
        val moviesRow = ListRow(
            HeaderItem("Recent Movies"),
            SkeletonHelper.createSkeletonAdapter(6)
        )
        rowsAdapter.add(moviesRow)

        // Add Series skeleton row
        val seriesRow = ListRow(
            HeaderItem("Recent Shows"),
            SkeletonHelper.createSkeletonAdapter(6)
        )
        rowsAdapter.add(seriesRow)

        Log.d(TAG, "Skeleton screens displayed (${rowsAdapter.size()} rows)")
    }

    /**
     * Hide skeleton loading screens
     * Smooth transition handled by adapter update when real content is added
     */
    fun hideSkeleton() {
        if (!isShowingSkeleton) return

        Log.d(TAG, "Hiding skeleton screens...")
        isShowingSkeleton = false

        // Don't remove anything! Let update methods naturally replace skeleton content when data arrives.
        // This fixes race condition where data arrives after hideSkeleton() runs.

        Log.d(TAG, "Skeleton flag cleared - observer callbacks will replace skeleton content")
    }

    /**
     * Check if currently showing skeleton screens
     */
    fun isShowingSkeleton(): Boolean = isShowingSkeleton

    companion object {
        private const val TAG = "HomeSkeletonManager"
    }
}
