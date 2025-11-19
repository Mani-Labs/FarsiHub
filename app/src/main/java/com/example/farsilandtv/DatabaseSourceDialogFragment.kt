package com.example.farsilandtv

import android.os.Bundle
import android.widget.Toast
import androidx.leanback.app.GuidedStepSupportFragment
import androidx.leanback.widget.GuidanceStylist
import androidx.leanback.widget.GuidedAction
import com.example.farsilandtv.data.database.ContentDatabase
import com.example.farsilandtv.data.database.DatabaseSource

/**
 * Guided step fragment to select database source (Farsiland or FarsiPlex)
 */
class DatabaseSourceSelectionFragment : GuidedStepSupportFragment() {

    private var onSourceChanged: ((DatabaseSource) -> Unit)? = null

    // Bug #3 fix: Clear callback references to prevent memory leaks
    override fun onDestroyView() {
        onSourceChanged = null
        super.onDestroyView()
    }

    override fun onCreateGuidance(savedInstanceState: Bundle?): GuidanceStylist.Guidance {
        val currentSource = ContentDatabase.getCurrentSource(requireContext())

        return GuidanceStylist.Guidance(
            "Select Content Source",
            "Currently using: ${currentSource.displayName}\n\n" +
            "Choose which content library to browse",
            "Farsiland TV",
            null
        )
    }

    override fun onCreateActions(actions: MutableList<GuidedAction>, savedInstanceState: Bundle?) {
        val currentSource = ContentDatabase.getCurrentSource(requireContext())
        val sources = DatabaseSource.values()

        sources.forEachIndexed { index, source ->
            val isChecked = source == currentSource

            actions.add(GuidedAction.Builder(requireContext())
                .id(index.toLong())
                .title(source.displayName)
                .description(getSourceDescription(source))
                .checked(isChecked)
                .checkSetId(GuidedAction.DEFAULT_CHECK_SET_ID)
                .build())
        }
    }

    override fun onGuidedActionClicked(action: GuidedAction) {
        val sources = DatabaseSource.values()
        val selectedSource = sources[action.id.toInt()]
        val currentSource = ContentDatabase.getCurrentSource(requireContext())

        if (selectedSource != currentSource) {
            // Show confirmation step
            showConfirmationStep(selectedSource)
        } else {
            // Same source, just go back
            requireActivity().supportFragmentManager.popBackStack()
        }
    }

    private fun showConfirmationStep(newSource: DatabaseSource) {
        GuidedStepSupportFragment.add(
            requireActivity().supportFragmentManager,
            DatabaseSourceConfirmationFragment.newInstance(newSource, onSourceChanged)
        )
    }

    private fun getSourceDescription(source: DatabaseSource): String {
        return when (source) {
            DatabaseSource.FARSILAND -> "Original content library"
            DatabaseSource.FARSIPLEX -> "36 movies, 34 TV shows, 558 episodes"
            DatabaseSource.NAMAKADE -> "312 movies, 923 series, 19,373 episodes"
        }
    }

    fun setOnSourceChangedListener(listener: (DatabaseSource) -> Unit) {
        onSourceChanged = listener
    }

    companion object {
        fun newInstance(listener: ((DatabaseSource) -> Unit)? = null): DatabaseSourceSelectionFragment {
            return DatabaseSourceSelectionFragment().apply {
                listener?.let { setOnSourceChangedListener(it) }
            }
        }
    }
}

/**
 * Confirmation step for database switch
 */
class DatabaseSourceConfirmationFragment : GuidedStepSupportFragment() {

    private var onSourceChanged: ((DatabaseSource) -> Unit)? = null

    // Get newSource from arguments directly (called during UI creation)
    private val newSource: DatabaseSource
        get() {
            val sourceName = arguments?.getString(ARG_SOURCE_NAME)
            android.util.Log.d("DatabaseSwitch", "ARG_SOURCE_NAME = $sourceName")
            return sourceName?.let {
                DatabaseSource.fromFileName(it)
            } ?: DatabaseSource.FARSILAND
        }

    // Bug #3 fix: Clear callback references to prevent memory leaks
    override fun onDestroyView() {
        onSourceChanged = null
        super.onDestroyView()
    }

    override fun onCreateGuidance(savedInstanceState: Bundle?): GuidanceStylist.Guidance {
        return GuidanceStylist.Guidance(
            "Switch to ${newSource.displayName}?",
            "The app will reload with different content.\n\n" +
            "Your favorites and playlists will remain unchanged.",
            "Confirm Switch",
            null
        )
    }

    override fun onCreateActions(actions: MutableList<GuidedAction>, savedInstanceState: Bundle?) {
        actions.add(GuidedAction.Builder(requireContext())
            .id(ACTION_CONFIRM)
            .title("Switch Now")
            .description("Load ${newSource.displayName} content")
            .build())

        actions.add(GuidedAction.Builder(requireContext())
            .id(ACTION_CANCEL)
            .title("Cancel")
            .description("Keep current source")
            .build())
    }

    override fun onGuidedActionClicked(action: GuidedAction) {
        when (action.id) {
            ACTION_CONFIRM -> {
                // Switch database
                val switched = ContentDatabase.switchDatabaseSource(requireContext(), newSource)

                if (switched) {
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.switched_to_database_source, newSource.displayName),
                        Toast.LENGTH_SHORT
                    ).show()

                    onSourceChanged?.invoke(newSource)

                    // Clear back stack and restart activity
                    val fragmentManager = requireActivity().supportFragmentManager

                    // Pop all guided step fragments (both confirmation and selection)
                    fragmentManager.popBackStack(null, androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE)

                    // Restart MainActivity properly instead of recreate()
                    requireActivity().window.decorView.post {
                        val intent = requireActivity().intent
                        requireActivity().finish()
                        requireActivity().startActivity(intent)
                    }
                }
            }
            ACTION_CANCEL -> {
                // Just go back
                requireActivity().supportFragmentManager.popBackStack()
            }
        }
    }

    companion object {
        private const val ARG_SOURCE_NAME = "source_name"
        private const val ACTION_CONFIRM = 1L
        private const val ACTION_CANCEL = 2L

        fun newInstance(
            source: DatabaseSource,
            listener: ((DatabaseSource) -> Unit)? = null
        ): DatabaseSourceConfirmationFragment {
            return DatabaseSourceConfirmationFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_SOURCE_NAME, source.fileName)
                }
                listener?.let { onSourceChanged = it }
            }
        }
    }
}
