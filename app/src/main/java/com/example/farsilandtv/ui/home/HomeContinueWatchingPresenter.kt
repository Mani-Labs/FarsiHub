package com.example.farsilandtv.ui.home

import android.util.Log
import android.view.ViewGroup
import androidx.leanback.widget.*
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import coil.load
import com.example.farsilandtv.R
import com.example.farsilandtv.data.database.ContinueWatchingItem
import kotlinx.coroutines.launch

/**
 * H1 REFACTOR: Extracted from HomeFragment.kt
 * Presenter for Continue Watching items with long-press to remove
 * Handles display of resume playback with progress percentage
 */
class HomeContinueWatchingPresenter(
    private val lifecycleOwner: LifecycleOwner,
    private val onLongPress: (ContinueWatchingItem) -> Unit
) : Presenter() {

    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val cardView = ImageCardView(parent.context).apply {
            isFocusable = true
            isFocusableInTouchMode = true
            setMainImageDimensions(454, 255)  // 45% bigger (313*1.45, 176*1.45)
        }
        return ViewHolder(cardView)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any) {
        val continueItem = item as ContinueWatchingItem
        val cardView = viewHolder.view as ImageCardView

        cardView.titleText = if (continueItem.contentType == ContinueWatchingItem.ContentType.EPISODE) {
            "${continueItem.subtitle} - ${continueItem.title}"
        } else {
            continueItem.title
        }

        // Add source badge to progress text
        val progressText = "${continueItem.progressPercentage}% watched"
        cardView.contentText = com.example.farsilandtv.utils.SourceBadgeHelper.prependBadge(
            cardView.context,
            continueItem.farsilandUrl,
            progressText
        )

        if (!continueItem.posterUrl.isNullOrEmpty()) {
            lifecycleOwner.lifecycleScope.launch {
                cardView.mainImageView.load(continueItem.posterUrl) {
                    lifecycle(lifecycleOwner)
                    crossfade(300)
                    placeholder(R.drawable.image_placeholder)
                    error(R.drawable.movie)
                }
            }
        }

        // Add long-press listener for removal
        cardView.setOnLongClickListener {
            onLongPress(continueItem)
            true
        }
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {
        val cardView = viewHolder.view as ImageCardView
        cardView.badgeImage = null
        cardView.mainImage = null
        cardView.setOnLongClickListener(null)
    }

    companion object {
        private const val TAG = "ContinueWatchingPresenter"

        /**
         * Create Continue Watching row from items
         */
        fun createRow(
            items: List<ContinueWatchingItem>,
            lifecycleOwner: LifecycleOwner,
            onLongPress: (ContinueWatchingItem) -> Unit
        ): ListRow {
            val presenter = HomeContinueWatchingPresenter(lifecycleOwner, onLongPress)
            val listRowAdapter = ArrayObjectAdapter(presenter)
            items.forEach {
                Log.d(TAG, "Adding continue watching item: ${it.title}")
                listRowAdapter.add(it)
            }
            Log.d(TAG, "Added ${listRowAdapter.size()} items to Continue Watching row")
            val header = HeaderItem(1, "Continue Watching")
            return ListRow(header, listRowAdapter)
        }
    }
}
