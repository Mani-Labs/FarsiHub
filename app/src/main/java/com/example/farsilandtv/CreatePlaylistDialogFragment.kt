package com.example.farsilandtv

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.example.farsilandtv.data.repository.PlaylistRepository
import kotlinx.coroutines.launch

/**
 * Dialog fragment for creating a new playlist
 * Simple form with name and optional description
 */
class CreatePlaylistDialogFragment : DialogFragment() {

    companion object {
        private const val TAG = "CreatePlaylistDialog"
    }

    private val playlistRepo by lazy { PlaylistRepository(requireContext()) }
    private var onPlaylistCreatedListener: ((Long) -> Unit)? = null

    private lateinit var nameEditText: EditText
    private lateinit var descriptionEditText: EditText
    private lateinit var createButton: Button
    private lateinit var cancelButton: Button

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_create_playlist, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        nameEditText = view.findViewById(R.id.playlist_name_input)
        descriptionEditText = view.findViewById(R.id.playlist_description_input)
        createButton = view.findViewById(R.id.create_button)
        cancelButton = view.findViewById(R.id.cancel_button)

        createButton.setOnClickListener {
            createPlaylist()
        }

        cancelButton.setOnClickListener {
            dismiss()
        }

        // Focus on name field
        nameEditText.requestFocus()
    }

    private fun createPlaylist() {
        val name = nameEditText.text.toString().trim()
        val description = descriptionEditText.text.toString().trim()

        // Validate name
        if (name.isEmpty()) {
            Toast.makeText(requireContext(), "Please enter a playlist name", Toast.LENGTH_SHORT).show()
            nameEditText.requestFocus()
            return
        }

        // Create playlist
        lifecycleScope.launch {
            try {
                val playlistId = playlistRepo.createPlaylist(
                    name = name,
                    description = description.ifEmpty { null }
                )

                Log.d(TAG, "Created playlist: $name (ID: $playlistId)")
                onPlaylistCreatedListener?.invoke(playlistId)
                dismiss()
            } catch (e: Exception) {
                Log.e(TAG, "Error creating playlist", e)
                Toast.makeText(requireContext(), "Error creating playlist: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun setOnPlaylistCreatedListener(listener: (Long) -> Unit) {
        onPlaylistCreatedListener = listener
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }
}
