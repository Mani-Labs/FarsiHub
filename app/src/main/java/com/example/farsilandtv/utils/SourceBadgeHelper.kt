package com.example.farsilandtv.utils

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import androidx.core.content.ContextCompat
import com.example.farsilandtv.R

/**
 * Helper for displaying source badges on content cards
 * Shows single-letter colored badges: F (red) for Farsiland, F (blue) for FarsiPlex, N (green) for Namakade
 */
object SourceBadgeHelper {

    /**
     * Content source detected from URL
     */
    enum class ContentSource(
        val displayLetter: String,
        val colorResId: Int,
        val fullName: String
    ) {
        FARSILAND("F", R.color.source_farsiland, "Farsiland"),
        FARSIPLEX("F", R.color.source_farsiplex, "FarsiPlex"),
        NAMAKADE("N", R.color.source_namakade, "Namakade"),
        UNKNOWN("?", R.color.source_unknown, "Unknown")
    }

    /**
     * Detect content source from URL
     */
    fun detectSource(url: String): ContentSource {
        return when {
            url.contains("farsiland.com", ignoreCase = true) -> ContentSource.FARSILAND
            url.contains("farsiplex.com", ignoreCase = true) -> ContentSource.FARSIPLEX
            url.contains("namakade.com", ignoreCase = true) ||
            url.contains("namakadeh.com", ignoreCase = true) -> ContentSource.NAMAKADE
            else -> ContentSource.UNKNOWN
        }
    }

    /**
     * Create a colored source badge text
     * Returns: "[F] " or "[N] " with appropriate color
     */
    fun createBadgeText(context: Context, url: String): SpannableStringBuilder {
        val source = detectSource(url)
        val badgeText = "[${source.displayLetter}] "

        val spannable = SpannableStringBuilder(badgeText)

        // Apply color to the badge
        val color = ContextCompat.getColor(context, source.colorResId)
        spannable.setSpan(
            ForegroundColorSpan(color),
            0,
            badgeText.length,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        // Make it bold
        spannable.setSpan(
            StyleSpan(Typeface.BOLD),
            0,
            badgeText.length,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        return spannable
    }

    /**
     * Prepend source badge to existing text
     */
    fun prependBadge(context: Context, url: String, existingText: CharSequence): CharSequence {
        android.util.Log.d("SourceBadgeHelper", "prependBadge called with URL: $url")
        val source = detectSource(url)
        android.util.Log.d("SourceBadgeHelper", "Detected source: ${source.fullName}")
        val badge = createBadgeText(context, url)
        val result = badge.append(existingText)
        android.util.Log.d("SourceBadgeHelper", "Badge text: $result")
        return result
    }

    /**
     * Get source color for badge
     */
    fun getSourceColor(context: Context, url: String): Int {
        val source = detectSource(url)
        return ContextCompat.getColor(context, source.colorResId)
    }
}
