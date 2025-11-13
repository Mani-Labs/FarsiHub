package com.example.farsilandtv.ui.presenters

import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.leanback.widget.Presenter

import com.example.farsilandtv.R
import com.example.farsilandtv.data.models.FeaturedContent

/**
 * Presenter for Featured Content Carousel cards
 * Displays large backdrop cards with title, description, and action buttons
 * Supports auto-rotation and manual navigation
 */
class FeaturedCarouselPresenter : Presenter() {

    companion object {
        private const val TAG = "FeaturedCarouselPresenter"
    }

    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.featured_carousel_card, parent, false)

        val viewHolder = ViewHolder(view)

        // Setup focus handling
        view.setOnFocusChangeListener { v, hasFocus ->
            val focusBorder = v.findViewById<View>(R.id.focus_border)

            if (hasFocus) {
                // Show focus border
                focusBorder?.visibility = View.VISIBLE

                // Slightly scale up for emphasis
                v.animate()
                    .scaleX(1.05f)
                    .scaleY(1.05f)
                    .setDuration(200)
                    .start()
            } else {
                // Hide focus border
                focusBorder?.visibility = View.GONE

                // Scale back to normal
                v.animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .setDuration(200)
                    .start()
            }
        }

        return viewHolder
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any) {
        val featuredItem = item as FeaturedContent
        val view = viewHolder.view

        // Find views
        val backdropImage = view.findViewById<ImageView>(R.id.featured_backdrop)
        val titleText = view.findViewById<TextView>(R.id.featured_title)
        val descriptionText = view.findViewById<TextView>(R.id.featured_description)

        // Set title and description
        titleText.text = featuredItem.title
        descriptionText.text = featuredItem.description

        // Load backdrop image with progressive blur-up (Feature #17)
        val backdropUrl = featuredItem.backdropUrl ?: featuredItem.posterUrl
        if (!backdropUrl.isNullOrEmpty()) {
            com.example.farsilandtv.utils.ImageLoader.loadWithBlurUp(
                context = view.context,
                imageUrl = backdropUrl,
                imageView = backdropImage,
                width = 1920,
                height = 1080
            )
        } else {
            backdropImage.setImageResource(R.drawable.default_background)
        }

        Log.d(TAG, "Bound featured item: ${featuredItem.title} (${featuredItem.contentType})")
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {
        val view = viewHolder.view
        val backdropImage = view.findViewById<ImageView>(R.id.featured_backdrop)

        // Coil automatically cancels requests when views are recycled (Feature #12: Lazy Loading)
        // This prevents loading images for off-screen items, reducing memory usage

        // Clear image to free memory
        backdropImage.setImageDrawable(null)

        // Reset scale
        view.scaleX = 1.0f
        view.scaleY = 1.0f
    }
}
