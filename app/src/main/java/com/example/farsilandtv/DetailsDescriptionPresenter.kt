package com.example.farsilandtv

import android.view.ViewGroup
import androidx.leanback.widget.AbstractDetailsDescriptionPresenter
import com.example.farsilandtv.data.models.Movie
import com.example.farsilandtv.data.models.Series
import com.example.farsilandtv.data.models.Episode

class DetailsDescriptionPresenter : AbstractDetailsDescriptionPresenter() {

    override fun onBindDescription(
        viewHolder: AbstractDetailsDescriptionPresenter.ViewHolder,
        item: Any
    ) {
        when (item) {
            is Movie -> {
                viewHolder.title.text = item.title

                // Subtitle with year and rating
                val subtitle = buildString {
                    if (item.year != null) append(item.year)
                    if (item.rating != null) {
                        if (item.year != null) append(" • ")
                        append("⭐ ${String.format("%.1f", item.rating)}")
                    }
                    if (item.runtime != null) {
                        append(" • ${item.runtime}min")
                    }
                }
                viewHolder.subtitle.text = subtitle

                // Body with description, director, and cast
                val body = buildString {
                    if (item.description.isNotEmpty()) {
                        append(item.description)
                        appendLine()
                        appendLine()
                    }

                    if (item.director != null) {
                        append("Director: ${item.director}")
                        appendLine()
                    }

                    if (item.cast.isNotEmpty()) {
                        append("Cast: ${item.cast.take(5).joinToString(", ")}")
                        if (item.cast.size > 5) {
                            append(", and ${item.cast.size - 5} more")
                        }
                    }
                }
                viewHolder.body.text = body
            }

            is Series -> {
                viewHolder.title.text = item.title

                // Subtitle with year, rating, seasons
                val subtitle = buildString {
                    if (item.year != null) append(item.year)
                    if (item.rating != null) {
                        if (item.year != null) append(" • ")
                        append("⭐ ${String.format("%.1f", item.rating)}")
                    }
                    if (item.totalSeasons > 0) {
                        append(" • ${item.totalSeasons} Seasons")
                    }
                    if (item.totalEpisodes > 0) {
                        append(" • ${item.totalEpisodes} Episodes")
                    }
                }
                viewHolder.subtitle.text = subtitle

                // Body with description and cast
                val body = buildString {
                    if (item.description.isNotEmpty()) {
                        append(item.description)
                        appendLine()
                        appendLine()
                    }

                    if (item.cast.isNotEmpty()) {
                        append("Cast: ${item.cast.take(5).joinToString(", ")}")
                        if (item.cast.size > 5) {
                            append(", and ${item.cast.size - 5} more")
                        }
                    }
                }
                viewHolder.body.text = body
            }

            is Episode -> {
                // Use Persian title if available, otherwise use regular title
                val displayTitle = item.persianTitle ?: item.englishTitle ?: item.title
                viewHolder.title.text = displayTitle

                // Subtitle with episode number, rating, quality, and release date
                val subtitle = buildString {
                    append(item.formattedNumber) // S01E05
                    
                    if (item.rating != null) {
                        append(" • ⭐ ${String.format("%.1f", item.rating)}")
                        if (item.voteCount != null) {
                            append(" (${item.voteCount} votes)")
                        }
                    }
                    
                    if (item.quality != null) {
                        append(" • ${item.quality}")
                    }
                    
                    if (item.releaseDate != null) {
                        append(" • ${item.releaseDate}")
                    } else if (item.airDate != null) {
                        append(" • ${item.airDate}")
                    }
                    
                    if (item.runtime != null) {
                        append(" • ${item.runtime}min")
                    }
                }
                viewHolder.subtitle.text = subtitle

                // Body with series name and description
                val body = buildString {
                    if (item.seriesTitle != null) {
                        append("Series: ${item.seriesTitle}")
                        appendLine()
                        appendLine()
                    }
                    
                    if (item.description.isNotEmpty()) {
                        append(item.description)
                    }
                }
                viewHolder.body.text = body
            }

            else -> {
                // Fallback for old Movie class
                viewHolder.title.text = item.toString()
                viewHolder.subtitle.text = ""
                viewHolder.body.text = ""
            }
        }

        // Minimize padding on the description view to reduce empty space
        val descriptionView = viewHolder.view
        descriptionView?.let { view ->
            // Minimal vertical padding
            val horizontalPadding = view.paddingLeft
            view.setPadding(
                horizontalPadding,
                5,   // Top padding - minimal
                horizontalPadding,
                5    // Bottom padding - minimal
            )

            // Set layout to wrap content height
            val params = view.layoutParams
            if (params != null) {
                params.height = ViewGroup.LayoutParams.WRAP_CONTENT
                view.layoutParams = params
            }
        }

        // Limit the description body to 4 lines maximum for series to keep it compact
        if (item is Series) {
            viewHolder.body?.maxLines = 4
        } else {
            viewHolder.body?.maxLines = 6
        }
    }
}