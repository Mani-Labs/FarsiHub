package com.example.farsilandtv.utils

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.Presenter
import com.example.farsilandtv.R

/**
 * Feature #20: Loading States with Skeleton Screens
 *
 * Helper class for displaying shimmer-based skeleton screens
 * Replaces spinning progress bars with animated skeleton placeholders
 *
 * Benefits:
 * - Better perceived performance
 * - Shows content structure while loading
 * - Smooth fade transitions
 * - Industry-standard loading pattern
 */
object SkeletonHelper {

    /**
     * Show skeleton loading state in fragment
     * Replaces ProgressBarManager.show()
     */
    fun showSkeleton(
        rootView: View,
        skeletonLayoutRes: Int,
        containerId: Int = android.R.id.content
    ): View? {
        val container = rootView.findViewById<ViewGroup>(containerId) ?: return null

        val skeleton = LayoutInflater.from(rootView.context)
            .inflate(skeletonLayoutRes, container, false)

        skeleton.id = R.id.skeleton_loading_view
        container.addView(skeleton)

        return skeleton
    }

    /**
     * Hide skeleton and show real content with smooth transition
     * Replaces ProgressBarManager.hide()
     */
    fun hideSkeleton(rootView: View, containerId: Int = android.R.id.content) {
        val container = rootView.findViewById<ViewGroup>(containerId) ?: return
        val skeleton = container.findViewById<View>(R.id.skeleton_loading_view)

        skeleton?.let {
            // Fade out skeleton
            it.animate()
                .alpha(0f)
                .setDuration(200)
                .withEndAction {
                    container.removeView(it)

                    // Fade in real content
                    container.alpha = 0f
                    container.animate()
                        .alpha(1f)
                        .setDuration(200)
                        .start()
                }
                .start()
        }
    }

    /**
     * Create programmatic skeleton for Leanback grids
     * Used for TV-optimized skeleton loading in BrowseSupportFragment
     *
     * @param itemCount Number of skeleton cards to display
     * @return ArrayObjectAdapter with skeleton cards
     */
    fun createSkeletonAdapter(itemCount: Int = 6): ArrayObjectAdapter {
        val adapter = ArrayObjectAdapter(SkeletonCardPresenter())
        repeat(itemCount) {
            adapter.add(SkeletonCard())
        }
        return adapter
    }
}

/**
 * Dummy data class for skeleton cards
 * Used to populate skeleton adapters
 */
data class SkeletonCard(val id: Int = 0)

/**
 * Presenter for skeleton cards in Leanback grids
 * Inflates skeleton_movie_card.xml for each item
 */
class SkeletonCardPresenter : Presenter() {
    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.skeleton_movie_card, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
        // Skeleton cards don't need binding - shimmer animation is automatic
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {
        // Nothing to unbind
    }
}
