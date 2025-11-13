package com.example.farsilandtv.ui.presenters

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.leanback.widget.Presenter
import com.example.farsilandtv.R
import com.example.farsilandtv.data.model.FilterCard

/**
 * Presenter for displaying filter/action cards in grid views
 */
class FilterCardPresenter : Presenter() {

    private val CARD_WIDTH = 220
    private val CARD_HEIGHT = 330

    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val context = parent.context
        val marginDp = 8
        val marginPx = (marginDp * context.resources.displayMetrics.density).toInt()

        val cardView = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            // Use MarginLayoutParams to add margins matching content cards
            layoutParams = ViewGroup.MarginLayoutParams(CARD_WIDTH, CARD_HEIGHT).apply {
                setMargins(marginPx, marginPx, marginPx, marginPx) // 8dp margins
            }
            setPadding(20, 20, 20, 20)
            // Don't set background here - will be set in onBindViewHolder
            isFocusable = true
            isFocusableInTouchMode = true

            // Focus effects disabled per user request
            // Color remains constant regardless of focus state
        }

        val iconView = ImageView(parent.context).apply {
            layoutParams = LinearLayout.LayoutParams(120, 120)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setColorFilter(Color.WHITE)
        }
        cardView.addView(iconView)

        val titleView = TextView(parent.context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 16
            }
            gravity = Gravity.CENTER
            textSize = 18f
            setTextColor(Color.WHITE)
            maxLines = 2
        }
        cardView.addView(titleView)

        val subtitleView = TextView(parent.context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 8
            }
            gravity = Gravity.CENTER
            textSize = 14f
            setTextColor(Color.LTGRAY)
            maxLines = 2
        }
        cardView.addView(subtitleView)

        return ViewHolder(cardView)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
        val filterCard = item as? FilterCard ?: return
        val cardView = viewHolder.view as LinearLayout

        val iconView = cardView.getChildAt(0) as ImageView
        val titleView = cardView.getChildAt(1) as TextView
        val subtitleView = cardView.getChildAt(2) as TextView

        // Set icon
        filterCard.iconRes?.let { iconRes ->
            iconView.setImageResource(iconRes)
        }

        // Set title
        titleView.text = filterCard.displayTitle

        // Set subtitle
        subtitleView.text = filterCard.subtitle ?: ""
        subtitleView.visibility = if (filterCard.subtitle.isNullOrEmpty()) {
            android.view.View.GONE
        } else {
            android.view.View.VISIBLE
        }

        // Set background color based on card type and state
        // Using GradientDrawable to ensure color stays constant (no focus state changes)
        val backgroundColor = when (filterCard.cardType) {
            FilterCard.CardType.SEARCH -> Color.parseColor("#3B82F6") // Blue for search
            FilterCard.CardType.FILTER -> {
                if (filterCard.activeFiltersCount > 0) {
                    Color.parseColor("#FF6B35") // Orange for active filter
                } else {
                    Color.parseColor("#6B7280") // Gray for inactive filter
                }
            }
            FilterCard.CardType.SORT -> Color.parseColor("#8B5CF6") // Purple for sort
        }

        // Create a drawable that doesn't change on focus
        val drawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 16f
            setColor(backgroundColor)
        }
        cardView.background = drawable
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {
        // Cleanup if needed
    }
}
