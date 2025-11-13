package com.example.farsilandtv

import android.os.Bundle
import android.widget.Toast
import androidx.leanback.app.GuidedStepSupportFragment
import androidx.leanback.widget.GuidanceStylist
import androidx.leanback.widget.GuidedAction
import com.example.farsilandtv.data.database.ContentDatabase

/**
 * Options/Settings fragment
 */
class OptionsFragment : GuidedStepSupportFragment() {

    companion object {
        private const val ACTION_DATABASE_SOURCE = 0L
        private const val ACTION_CLEAR_CACHE = 1L
        private const val ACTION_CLEAR_HISTORY = 2L
        private const val ACTION_ABOUT = 3L
    }

    override fun onCreateGuidance(savedInstanceState: Bundle?): GuidanceStylist.Guidance {
        return GuidanceStylist.Guidance(
            "Options",
            "App settings and preferences",
            "Farsiland TV",
            null
        )
    }

    override fun onCreateActions(actions: MutableList<GuidedAction>, savedInstanceState: Bundle?) {
        // Get current database source
        val currentSource = ContentDatabase.getCurrentSource(requireContext())

        actions.add(GuidedAction.Builder(requireContext())
            .id(ACTION_DATABASE_SOURCE)
            .title("Content Source")
            .description("Currently: ${currentSource.displayName}")
            .build())

        actions.add(GuidedAction.Builder(requireContext())
            .id(ACTION_CLEAR_CACHE)
            .title("Clear Cache")
            .description("Clear image and data cache")
            .build())

        actions.add(GuidedAction.Builder(requireContext())
            .id(ACTION_CLEAR_HISTORY)
            .title("Clear Watch History")
            .description("Remove all watch history and progress")
            .build())

        actions.add(GuidedAction.Builder(requireContext())
            .id(ACTION_ABOUT)
            .title("About")
            .description("App version and information")
            .build())
    }

    override fun onGuidedActionClicked(action: GuidedAction) {
        when (action.id) {
            ACTION_DATABASE_SOURCE -> {
                showDatabaseSourceSelector()
            }
            ACTION_CLEAR_CACHE -> {
                Toast.makeText(context, "Cache cleared", Toast.LENGTH_SHORT).show()
                // TODO: Implement cache clearing
            }
            ACTION_CLEAR_HISTORY -> {
                Toast.makeText(context, "Watch history cleared", Toast.LENGTH_SHORT).show()
                // TODO: Implement history clearing
            }
            ACTION_ABOUT -> {
                Toast.makeText(context, "Farsiland TV v1.0", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showDatabaseSourceSelector() {
        // Launch guided step fragment for database selection
        GuidedStepSupportFragment.add(
            requireActivity().supportFragmentManager,
            DatabaseSourceSelectionFragment.newInstance()
        )
    }
}
