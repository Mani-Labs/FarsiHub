package com.example.farsilandtv.ui.home

import android.content.Context
import android.util.Log
import android.view.ViewGroup
import androidx.leanback.widget.*
import com.example.farsilandtv.data.models.Movie
import com.example.farsilandtv.data.models.Series

/**
 * H1 REFACTOR: Extracted from HomeFragment.kt
 * Presenter for watchlist items with long-press to remove
 * Displays both movies and series in the "My Watchlist" row
 */
class HomeWatchlistPresenter(
    private val context: Context,
    private val onLongPress: (Any) -> Unit
) : Presenter() {

    private val contentPresenter = com.example.farsilandtv.ui.ContentCardPresenter(context)

    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        return contentPresenter.onCreateViewHolder(parent)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any) {
        // Bind normally using ContentCardPresenter (which includes source badges)
        contentPresenter.onBindViewHolder(viewHolder, item)

        // Add long-press listener for removal
        viewHolder.view.setOnLongClickListener {
            onLongPress(item)
            true
        }
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {
        contentPresenter.onUnbindViewHolder(viewHolder)
        viewHolder.view.setOnLongClickListener(null)
    }

    companion object {
        private const val TAG = "WatchlistPresenter"

        /**
         * Create or update watchlist row
         * @param rowsAdapter The main adapter to add/update row in
         * @param watchlistMovies Current watchlist movies
         * @param monitoredSeries Current monitored series
         * @param context Android context
         * @param onLongPress Callback for long-press to remove
         * @param adapterLock Lock for synchronization
         */
        fun updateWatchlistRow(
            rowsAdapter: ArrayObjectAdapter,
            watchlistMovies: List<Movie>,
            monitoredSeries: List<Series>,
            context: Context,
            onLongPress: (Any) -> Unit,
            adapterLock: Any
        ) {
            // C7 FIX: Synchronize entire operation to prevent TOCTOU race conditions
            synchronized(rowsAdapter) {
                // Find and remove existing watchlist row
                var watchlistRowIndex = -1
                val currentSize = rowsAdapter.size()
                for (i in 0 until currentSize) {
                    val item = rowsAdapter.get(i)
                    if (item is ListRow && item.headerItem?.name == "My Watchlist") {
                        watchlistRowIndex = i
                        break
                    }
                }

                // Atomic bounds check and remove to prevent TOCTOU race
                if (watchlistRowIndex >= 0 && watchlistRowIndex < rowsAdapter.size()) {
                    rowsAdapter.removeItems(watchlistRowIndex, 1)
                }

                // Add new watchlist row if there are items
                val allItems = watchlistMovies + monitoredSeries
                if (allItems.isNotEmpty()) {
                    val cardPresenter = HomeWatchlistPresenter(context, onLongPress)
                    val listRowAdapter = ArrayObjectAdapter(cardPresenter)
                    allItems.forEach { listRowAdapter.add(it) }
                    val header = HeaderItem(2, "My Watchlist")

                    // Insert after Continue Watching row (index 2) or after Navigation (index 1)
                    val hasContinueWatching = rowsAdapter.unmodifiableList<Any>().any {
                        it is ListRow && it.headerItem?.name == "Continue Watching"
                    }
                    val insertIndex = if (hasContinueWatching) 2 else 1

                    // Atomic bounds check and insert to prevent TOCTOU race
                    if (insertIndex <= rowsAdapter.size()) {
                        rowsAdapter.add(insertIndex, ListRow(header, listRowAdapter))
                    }
                }
            }

            Log.d(TAG, "Watchlist row updated: ${watchlistMovies.size} movies, ${monitoredSeries.size} series")
        }
    }
}
