package com.example.farsilandtv.ui.home

import android.content.Context
import android.util.Log
import android.view.ViewGroup
import androidx.leanback.widget.*
import com.example.farsilandtv.data.models.Episode
import com.example.farsilandtv.data.models.Movie
import com.example.farsilandtv.data.models.Series
import com.example.farsilandtv.ui.GenreCardPresenter

/**
 * H1 REFACTOR: Extracted from HomeFragment.kt
 * Presenter for recent content (movies, series, episodes) with long-press to add/remove from watchlist
 */
class HomeRecentContentPresenter(
    private val context: Context,
    private val onLongPress: (Any) -> Unit
) : Presenter() {

    private val genreCardPresenter = GenreCardPresenter(context)

    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        return genreCardPresenter.onCreateViewHolder(parent)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any) {
        // Bind normally using GenreCardPresenter
        genreCardPresenter.onBindViewHolder(viewHolder, item)

        // Add long-press listener for add/remove watchlist
        viewHolder.view.setOnLongClickListener {
            onLongPress(item)
            true
        }
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {
        genreCardPresenter.onUnbindViewHolder(viewHolder)
        viewHolder.view.setOnLongClickListener(null)
    }

    companion object {
        private const val TAG = "RecentContentPresenter"

        /**
         * Update or add episodes row
         */
        fun updateEpisodesRow(
            rowsAdapter: ArrayObjectAdapter,
            title: String,
            episodes: List<Episode>,
            context: Context,
            onLongPress: (Any) -> Unit,
            adapterLock: Any
        ) {
            Log.d(TAG, "updateEpisodesRow called: title='$title', count=${episodes.size}")

            // H11 FIX: Synchronize adapter access to prevent ConcurrentModificationException
            synchronized(adapterLock) {
                // Find existing row
                var existingRowIndex = -1
                for (i in 0 until rowsAdapter.size()) {
                    val item = rowsAdapter.get(i)
                    if (item is ListRow && item.headerItem?.name == title) {
                        existingRowIndex = i
                        break
                    }
                }

                if (existingRowIndex >= 0) {
                    Log.d(TAG, "  → Replacing skeleton row at index $existingRowIndex with real content")
                    // Replace entire row with new one that has correct presenter
                    rowsAdapter.removeItems(existingRowIndex, 1)

                    // Create new row with correct presenter
                    val cardPresenter = com.example.farsilandtv.ui.ContentCardPresenter(context)
                    val listRowAdapter = ArrayObjectAdapter(cardPresenter)
                    episodes.forEach { listRowAdapter.add(it) }
                    val header = HeaderItem(title)
                    rowsAdapter.add(existingRowIndex, ListRow(header, listRowAdapter))
                } else {
                    Log.d(TAG, "  → Row not found, adding new row")
                    // Add new row
                    val cardPresenter = HomeRecentContentPresenter(context, onLongPress)
                    val listRowAdapter = ArrayObjectAdapter(cardPresenter)
                    episodes.forEach { listRowAdapter.add(it) }
                    val header = HeaderItem(rowsAdapter.size().toLong(), title)
                    rowsAdapter.add(ListRow(header, listRowAdapter))
                }
            }
        }

        /**
         * Update or add movies row
         */
        fun updateMoviesRow(
            rowsAdapter: ArrayObjectAdapter,
            title: String,
            movies: List<Movie>,
            context: Context,
            onLongPress: (Any) -> Unit,
            adapterLock: Any
        ) {
            Log.d(TAG, "updateMoviesRow called: title='$title', count=${movies.size}")

            // H11 FIX: Synchronize adapter access to prevent ConcurrentModificationException
            synchronized(adapterLock) {
                // Find existing row
                var existingRowIndex = -1
                for (i in 0 until rowsAdapter.size()) {
                    val item = rowsAdapter.get(i)
                    if (item is ListRow && item.headerItem?.name == title) {
                        existingRowIndex = i
                        break
                    }
                }

                if (existingRowIndex >= 0) {
                    Log.d(TAG, "  → Replacing skeleton row at index $existingRowIndex with real content")
                    // Replace entire row with new one that has correct presenter
                    rowsAdapter.removeItems(existingRowIndex, 1)

                    // Create new row with correct presenter
                    val cardPresenter = com.example.farsilandtv.ui.ContentCardPresenter(context)
                    val listRowAdapter = ArrayObjectAdapter(cardPresenter)
                    movies.forEach { listRowAdapter.add(it) }
                    val header = HeaderItem(title)
                    rowsAdapter.add(existingRowIndex, ListRow(header, listRowAdapter))
                } else {
                    Log.d(TAG, "  → Row not found, adding new row")
                    // Add new row
                    val cardPresenter = HomeRecentContentPresenter(context, onLongPress)
                    val listRowAdapter = ArrayObjectAdapter(cardPresenter)
                    movies.forEach { listRowAdapter.add(it) }
                    val header = HeaderItem(rowsAdapter.size().toLong(), title)
                    rowsAdapter.add(ListRow(header, listRowAdapter))
                }
            }
        }

        /**
         * Update or add series row
         */
        fun updateSeriesRow(
            rowsAdapter: ArrayObjectAdapter,
            title: String,
            series: List<Series>,
            context: Context,
            onLongPress: (Any) -> Unit,
            adapterLock: Any
        ) {
            Log.d(TAG, "updateSeriesRow called: title='$title', count=${series.size}")

            // H11 FIX: Synchronize adapter access to prevent ConcurrentModificationException
            synchronized(adapterLock) {
                // Find existing row
                var existingRowIndex = -1
                for (i in 0 until rowsAdapter.size()) {
                    val item = rowsAdapter.get(i)
                    if (item is ListRow && item.headerItem?.name == title) {
                        existingRowIndex = i
                        break
                    }
                }

                if (existingRowIndex >= 0) {
                    Log.d(TAG, "  → Replacing skeleton row at index $existingRowIndex with real content")
                    // Replace entire row with new one that has correct presenter
                    rowsAdapter.removeItems(existingRowIndex, 1)

                    // Create new row with correct presenter
                    val cardPresenter = com.example.farsilandtv.ui.ContentCardPresenter(context)
                    val listRowAdapter = ArrayObjectAdapter(cardPresenter)
                    series.forEach { listRowAdapter.add(it) }
                    val header = HeaderItem(title)
                    rowsAdapter.add(existingRowIndex, ListRow(header, listRowAdapter))
                } else {
                    Log.d(TAG, "  → Row not found, adding new row")
                    // Add new row
                    val cardPresenter = HomeRecentContentPresenter(context, onLongPress)
                    val listRowAdapter = ArrayObjectAdapter(cardPresenter)
                    series.forEach { listRowAdapter.add(it) }
                    val header = HeaderItem(rowsAdapter.size().toLong(), title)
                    rowsAdapter.add(ListRow(header, listRowAdapter))
                }
            }
        }
    }
}
