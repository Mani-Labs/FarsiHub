package com.example.farsilandtv.ui.presenters

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.leanback.widget.Presenter
import com.example.farsilandtv.R
import com.example.farsilandtv.ui.model.GenreChip

/**
 * Leanback Presenter for genre filter chips
 * Displays genre chips with Persian names and color-coded backgrounds
 * Supports multi-select with visual feedback (selected/unselected states)
 */
class GenreChipPresenter : Presenter() {

    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.genre_chip_view, parent, false)

        return ViewHolder(view)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any) {
        val chip = item as GenreChip
        val chipText = viewHolder.view.findViewById<TextView>(R.id.chip_text)

        // Set text based on chip type
        if (chip.isClearButton) {
            chipText.text = "همه ژانرها" // "All Genres" in Persian
            chipText.setTextColor(Color.WHITE)
        } else {
            chipText.text = chip.genre.persianName
        }

        // Update visual state
        updateChipState(chipText, chip)

        // Update selection state
        viewHolder.view.isSelected = chip.isSelected
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {
        // Cleanup if needed
    }

    /**
     * Update chip visual appearance based on selection state
     */
    private fun updateChipState(chipText: TextView, chip: GenreChip) {
        if (chip.isClearButton) {
            // Clear button has special styling
            chipText.setTextColor(Color.WHITE)
            return
        }

        // Parse genre color
        val genreColor = try {
            Color.parseColor(chip.genre.colorCode)
        } catch (e: Exception) {
            Color.GRAY
        }

        // Set text color based on selection state
        if (chip.isSelected) {
            // Selected: use genre color for text
            chipText.setTextColor(genreColor)

            // Set background to filled with genre color (semi-transparent)
            val background = chipText.background as? GradientDrawable
            background?.setColor(adjustAlpha(genreColor, 0.3f))
        } else {
            // Unselected: white text, subtle background
            chipText.setTextColor(Color.WHITE)

            // Background handled by selector drawable
        }
    }

    /**
     * Adjust color alpha (opacity)
     */
    private fun adjustAlpha(color: Int, alpha: Float): Int {
        val alphaInt = (255 * alpha).toInt()
        val red = Color.red(color)
        val green = Color.green(color)
        val blue = Color.blue(color)
        return Color.argb(alphaInt, red, green, blue)
    }
}
