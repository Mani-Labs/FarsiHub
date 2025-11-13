package com.example.farsilandtv
import coil.load

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.leanback.app.VerticalGridSupportFragment
import androidx.leanback.widget.*
import androidx.lifecycle.lifecycleScope

// Glide imports for legacy Leanback fragments


import com.example.farsilandtv.data.database.Playlist
import com.example.farsilandtv.data.repository.PlaylistRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Fragment for browsing user playlists in a vertical grid
 * Shows all playlists with option to create new ones
 */
class PlaylistsFragment : VerticalGridSupportFragment() {

    companion object {
        private const val TAG = "PlaylistsFragment"
        private const val NUM_COLUMNS = 4
        private const val CREATE_PLAYLIST_ID = -1L
        private const val CARD_WIDTH = 313
        private const val CARD_HEIGHT = 176
    }

    private val playlistRepo by lazy { PlaylistRepository(requireContext()) }
    private lateinit var mAdapter: ArrayObjectAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate")

        title = "My Playlists"
        setupAdapter()
        loadPlaylists()
    }

    private fun setupAdapter() {
        val gridPresenter = VerticalGridPresenter()
        gridPresenter.numberOfColumns = NUM_COLUMNS
        setGridPresenter(gridPresenter)

        mAdapter = ArrayObjectAdapter(PlaylistCardPresenter())
        adapter = mAdapter

        onItemViewClickedListener = ItemViewClickedListener()
    }

    private fun loadPlaylists() {
        lifecycleScope.launch {
            try {
                // Clear existing items
                mAdapter.clear()

                // Add "Create New Playlist" card at the beginning
                mAdapter.add(CreatePlaylistCard())

                // Load all playlists
                playlistRepo.getAllPlaylists().collect { playlists ->
                    Log.d(TAG, "Loaded ${playlists.size} playlists")

                    // Remove all playlists (keep create card)
                    while (mAdapter.size() > 1) {
                        mAdapter.removeItems(1, 1)
                    }

                    // Add playlists
                    playlists.forEach { playlist ->
                        lifecycleScope.launch {
                            val itemCount = playlistRepo.getItemCount(playlist.id)
                            mAdapter.add(PlaylistCardData(playlist, itemCount))
                        }
                    }

                    // Show empty state if no playlists
                    if (playlists.isEmpty()) {
                        Log.d(TAG, "No playlists found")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading playlists", e)
                Toast.makeText(requireContext(), getString(R.string.error_loading_playlists, e.message ?: "Unknown error"), Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * Show create playlist dialog
     */
    private fun showCreatePlaylistDialog() {
        Log.d(TAG, "Opening create playlist dialog")

        val dialog = CreatePlaylistDialogFragment()
        dialog.setOnPlaylistCreatedListener { playlistId ->
            Log.d(TAG, "Playlist created with ID: $playlistId")
            Toast.makeText(requireContext(), "Playlist created", Toast.LENGTH_SHORT).show()
            // The flow will automatically update the UI
        }
        dialog.show(childFragmentManager, "create_playlist")
    }

    /**
     * Open playlist detail screen
     */
    private fun openPlaylistDetail(playlistId: Long) {
        Log.d(TAG, "Opening playlist detail: $playlistId")
        try {
            val intent = Intent(requireContext(), PlaylistDetailActivity::class.java).apply {
                putExtra(PlaylistDetailActivity.EXTRA_PLAYLIST_ID, playlistId)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error opening playlist detail", e)
            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private inner class ItemViewClickedListener : OnItemViewClickedListener {
        override fun onItemClicked(
            itemViewHolder: Presenter.ViewHolder,
            item: Any,
            rowViewHolder: RowPresenter.ViewHolder,
            row: Row
        ) {
            when (item) {
                is CreatePlaylistCard -> {
                    showCreatePlaylistDialog()
                }
                is PlaylistCardData -> {
                    openPlaylistDetail(item.playlist.id)
                }
            }
        }
    }

    /**
     * Marker class for "Create New Playlist" card
     */
    data class CreatePlaylistCard(val dummy: Boolean = true)

    /**
     * Data class for playlist card with item count
     */
    data class PlaylistCardData(
        val playlist: Playlist,
        val itemCount: Int
    )

    /**
     * Presenter for playlist cards
     */
    private inner class PlaylistCardPresenter : Presenter() {
        override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
            val cardView = ImageCardView(parent.context).apply {
                isFocusable = true
                isFocusableInTouchMode = true
                setMainImageDimensions(CARD_WIDTH, CARD_HEIGHT)
            }
            return ViewHolder(cardView)
        }

        override fun onBindViewHolder(viewHolder: ViewHolder, item: Any) {
            val cardView = viewHolder.view as ImageCardView

            when (item) {
                is CreatePlaylistCard -> {
                    cardView.titleText = "Create New Playlist"
                    cardView.contentText = "Add a new playlist"
                    cardView.mainImage = ContextCompat.getDrawable(requireContext(), R.drawable.ic_add_playlist)
                }
                is PlaylistCardData -> {
                    cardView.titleText = item.playlist.name
                    cardView.contentText = "${item.itemCount} items"

                    // Load cover image (first item's poster or default)
                    if (!item.playlist.coverImageUrl.isNullOrEmpty()) {
                        cardView.mainImageView.load(item.playlist.coverImageUrl) {
                            crossfade(300)
                            placeholder(R.drawable.image_placeholder)
                            error(R.drawable.default_background)
                        }
                    } else {
                        cardView.mainImage = ContextCompat.getDrawable(requireContext(), R.drawable.movie)
                    }
                }
            }
        }

        override fun onUnbindViewHolder(viewHolder: ViewHolder) {
            val cardView = viewHolder.view as ImageCardView
            cardView.badgeImage = null
            cardView.mainImage = null
        }
    }
}
