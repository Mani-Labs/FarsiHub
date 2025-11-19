package com.example.farsilandtv

import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.example.farsilandtv.data.database.Playlist
import com.example.farsilandtv.data.models.Movie
import com.example.farsilandtv.data.models.Series
import com.example.farsilandtv.data.repository.PlaylistRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Dialog for adding content (movie or series) to playlists
 * Shows list of existing playlists with checkboxes
 * Option to create new playlist
 */
class AddToPlaylistDialogFragment : DialogFragment() {

    companion object {
        private const val TAG = "AddToPlaylistDialog"
        const val ARG_MOVIE = "movie"
        const val ARG_SERIES = "series"

        fun newInstanceForMovie(movie: Movie): AddToPlaylistDialogFragment {
            return AddToPlaylistDialogFragment().apply {
                arguments = Bundle().apply {
                    putSerializable(ARG_MOVIE, movie)
                }
            }
        }

        fun newInstanceForSeries(series: Series): AddToPlaylistDialogFragment {
            return AddToPlaylistDialogFragment().apply {
                arguments = Bundle().apply {
                    putSerializable(ARG_SERIES, series)
                }
            }
        }
    }

    private val playlistRepo by lazy { PlaylistRepository(requireContext()) }
    private var movie: Movie? = null
    private var series: Series? = null
    private val selectedPlaylists = mutableSetOf<Long>()

    private lateinit var playlistsContainer: LinearLayout
    private lateinit var createNewButton: Button
    private lateinit var doneButton: Button
    private lateinit var cancelButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        movie = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arguments?.getSerializable(ARG_MOVIE, Movie::class.java)
        } else {
            @Suppress("DEPRECATION")
            arguments?.getSerializable(ARG_MOVIE) as? Movie
        }
        series = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arguments?.getSerializable(ARG_SERIES, Series::class.java)
        } else {
            @Suppress("DEPRECATION")
            arguments?.getSerializable(ARG_SERIES) as? Series
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_add_to_playlist, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        playlistsContainer = view.findViewById(R.id.playlists_container)
        createNewButton = view.findViewById(R.id.create_new_playlist_button)
        doneButton = view.findViewById(R.id.done_button)
        cancelButton = view.findViewById(R.id.cancel_button)

        createNewButton.setOnClickListener {
            showCreatePlaylistDialog()
        }

        doneButton.setOnClickListener {
            addToPlaylists()
        }

        cancelButton.setOnClickListener {
            dismiss()
        }

        loadPlaylists()
    }

    private fun loadPlaylists() {
        lifecycleScope.launch {
            try {
                val playlists = playlistRepo.getAllPlaylists().first()
                Log.d(TAG, "Loaded ${playlists.size} playlists")

                if (playlists.isEmpty()) {
                    showEmptyState()
                } else {
                    displayPlaylists(playlists)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading playlists", e)
                Toast.makeText(requireContext(), getString(R.string.error_loading_playlists, e.message ?: "Unknown error"), Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showEmptyState() {
        playlistsContainer.removeAllViews()
        val emptyText = TextView(requireContext()).apply {
            text = "No playlists yet. Create one to get started!"
            textSize = 16f
            setPadding(32, 32, 32, 32)
        }
        playlistsContainer.addView(emptyText)
    }

    private fun displayPlaylists(playlists: List<Playlist>) {
        playlistsContainer.removeAllViews()

        val movieCopy = movie
        val seriesCopy = series

        val contentId = when {
            movieCopy != null -> "movie-${movieCopy.id}"
            seriesCopy != null -> "series-${seriesCopy.id}"
            else -> return
        }

        playlists.forEach { playlist ->
            lifecycleScope.launch {
                // Check if already in playlist
                val isInPlaylist = playlistRepo.isInPlaylistOnce(playlist.id, contentId)

                val checkBox = CheckBox(requireContext()).apply {
                    text = "${playlist.name} (${playlistRepo.getItemCount(playlist.id)} items)"
                    textSize = 18f
                    setPadding(24, 16, 24, 16)
                    isChecked = isInPlaylist

                    setOnCheckedChangeListener { _, isChecked ->
                        if (isChecked) {
                            selectedPlaylists.add(playlist.id)
                        } else {
                            selectedPlaylists.remove(playlist.id)
                        }
                    }
                }

                playlistsContainer.addView(checkBox)
            }
        }
    }

    private fun showCreatePlaylistDialog() {
        val dialog = CreatePlaylistDialogFragment()
        dialog.setOnPlaylistCreatedListener { playlistId ->
            Log.d(TAG, "New playlist created: $playlistId")
            // Reload playlists to show the new one
            loadPlaylists()
        }
        dialog.show(childFragmentManager, "create_playlist")
    }

    private fun addToPlaylists() {
        if (selectedPlaylists.isEmpty()) {
            Toast.makeText(requireContext(), "No playlists selected", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                var addedCount = 0

                val movieCopy = movie
                val seriesCopy = series

                selectedPlaylists.forEach { playlistId ->
                    when {
                        movieCopy != null -> {
                            playlistRepo.addMovieToPlaylist(playlistId, movieCopy)
                            addedCount++
                        }
                        seriesCopy != null -> {
                            playlistRepo.addSeriesToPlaylist(playlistId, seriesCopy)
                            addedCount++
                        }
                    }
                }

                val message = if (addedCount == 1) {
                    "Added to 1 playlist"
                } else {
                    "Added to $addedCount playlists"
                }

                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
                dismiss()
            } catch (e: Exception) {
                Log.e(TAG, "Error adding to playlists", e)
                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.7).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }
}
