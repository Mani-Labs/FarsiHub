package com.example.farsilandtv

import android.graphics.drawable.Drawable
import android.view.animation.AnimationUtils
import androidx.leanback.widget.ImageCardView
import androidx.leanback.widget.Presenter
import androidx.core.content.ContextCompat
import android.util.Log
import android.view.ViewGroup

import coil.load
import com.example.farsilandtv.utils.ImageLoader
import kotlin.properties.Delegates

/**
 * A CardPresenter is used to generate Views and bind Objects to them on demand.
 * It contains an ImageCardView.
 */
class CardPresenter : Presenter() {
    private var mDefaultCardImage: Drawable? = null
    private var sSelectedBackgroundColor: Int by Delegates.notNull()
    private var sDefaultBackgroundColor: Int by Delegates.notNull()

    override fun onCreateViewHolder(parent: ViewGroup): Presenter.ViewHolder {
        Log.d(TAG, "onCreateViewHolder")

        sDefaultBackgroundColor = ContextCompat.getColor(parent.context, R.color.default_background)
        sSelectedBackgroundColor =
            ContextCompat.getColor(parent.context, R.color.selected_background)
        mDefaultCardImage = ContextCompat.getDrawable(parent.context, R.drawable.movie)

        val cardView = object : ImageCardView(parent.context) {
            override fun setSelected(selected: Boolean) {
                updateCardBackgroundColor(this, selected)
                super.setSelected(selected)
            }
        }

        cardView.isFocusable = true
        cardView.isFocusableInTouchMode = true
        updateCardBackgroundColor(cardView, false)

        // Add focus change listener for animations
        cardView.setOnFocusChangeListener { view, hasFocus ->
            val scaleAnim = if (hasFocus) {
                AnimationUtils.loadAnimation(parent.context, R.anim.scale_up)
            } else {
                AnimationUtils.loadAnimation(parent.context, R.anim.scale_down)
            }
            view.startAnimation(scaleAnim)

            // Apply focus highlight drawable
            if (hasFocus) {
                view.foreground = ContextCompat.getDrawable(parent.context, R.drawable.focus_highlight)
            } else {
                view.foreground = null
                // Reset background to default when focus is lost
                updateCardBackgroundColor(cardView, false)
            }
        }

        return Presenter.ViewHolder(cardView)
    }

    override fun onBindViewHolder(viewHolder: Presenter.ViewHolder, item: Any) {
        // Use safe cast to prevent crashes if wrong type is passed
        val movie = item as? Movie
        if (movie == null) {
            Log.e(TAG, "Expected Movie but got ${item::class.simpleName}")
            return
        }

        val cardView = viewHolder.view as ImageCardView

        Log.d(TAG, "onBindViewHolder")
        if (movie.cardImageUrl != null) {
            cardView.titleText = movie.title
            cardView.contentText = movie.studio
            cardView.setMainImageDimensions(CARD_WIDTH, CARD_HEIGHT)
            // Using Coil instead of Glide for image loading
            cardView.mainImageView.load(movie.cardImageUrl) {
                crossfade(300) // 300ms cross-fade
                placeholder(R.drawable.image_placeholder)
                error(R.drawable.movie)
                size(CARD_WIDTH, CARD_HEIGHT)
            }
        }
    }

    override fun onUnbindViewHolder(viewHolder: Presenter.ViewHolder) {
        Log.d(TAG, "onUnbindViewHolder")
        val cardView = viewHolder.view as ImageCardView
        // Coil handles request cancellation automatically when views are recycled
        // Remove references to images so that the garbage collector can free up memory
        cardView.badgeImage = null
        cardView.mainImage = null
    }

    private fun updateCardBackgroundColor(view: ImageCardView, selected: Boolean) {
        val color = if (selected) sSelectedBackgroundColor else sDefaultBackgroundColor
        // Both background colors should be set because the view"s background is temporarily visible
        // during animations.
        view.setBackgroundColor(color)
        view.setInfoAreaBackgroundColor(color)
    }

    companion object {
        private const val TAG = "CardPresenter"

        private const val CARD_WIDTH = 454  // 313 * 1.45
        private const val CARD_HEIGHT = 255  // 176 * 1.45
    }
}