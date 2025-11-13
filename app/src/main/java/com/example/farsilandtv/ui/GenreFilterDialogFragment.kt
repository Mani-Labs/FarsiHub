package com.example.farsilandtv.ui

import android.os.Bundle
import androidx.leanback.app.GuidedStepSupportFragment
import androidx.leanback.widget.GuidanceStylist
import androidx.leanback.widget.GuidedAction
import com.example.farsilandtv.data.model.Genre

/**
 * Guided Step fragment for genre filtering
 * Uses Leanback GuidedStep pattern for TV-optimized multi-select
 */
class GenreFilterDialogFragment : GuidedStepSupportFragment() {

    private val selectedGenres = mutableListOf<Genre>()
    private var onGenresSelectedListener: ((List<Genre>) -> Unit)? = null

    enum class ContentType {
        MOVIES, SHOWS
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Get selected genres from arguments
        arguments?.getStringArrayList(ARG_SELECTED_GENRES)?.let { genreNames ->
            selectedGenres.addAll(genreNames.mapNotNull { Genre.fromEnglishName(it) })
        }
    }

    override fun onCreateGuidance(savedInstanceState: Bundle?): GuidanceStylist.Guidance {
        val title = "فیلتر بر اساس ژانر"
        val description = "ژانرهای مورد نظر خود را انتخاب کنید"
        val breadcrumb = ""

        return GuidanceStylist.Guidance(title, description, breadcrumb, null)
    }

    override fun onCreateActions(actions: MutableList<GuidedAction>, savedInstanceState: Bundle?) {
        // Add "Clear All" action
        actions.add(
            GuidedAction.Builder(requireContext())
                .id(ACTION_CLEAR_ALL)
                .title("پاک کردن همه فیلترها")
                .build()
        )

        // Add genre actions (checkable)
        Genre.values().forEachIndexed { index, genre ->
            val isChecked = selectedGenres.contains(genre)
            actions.add(
                GuidedAction.Builder(requireContext())
                    .id(ACTION_GENRE_START + index.toLong())
                    .title(genre.persianName)
                    .checkSetId(GENRE_CHECK_SET_ID)
                    .checked(isChecked)
                    .build()
            )
        }
    }

    override fun onCreateButtonActions(actions: MutableList<GuidedAction>, savedInstanceState: Bundle?) {
        // Add "Apply" and "Cancel" buttons
        actions.add(
            GuidedAction.Builder(requireContext())
                .id(ACTION_APPLY)
                .title("اعمال")
                .build()
        )

        actions.add(
            GuidedAction.Builder(requireContext())
                .id(ACTION_CANCEL)
                .title("لغو")
                .build()
        )
    }

    override fun onGuidedActionClicked(action: GuidedAction) {
        when (action.id) {
            ACTION_CLEAR_ALL -> {
                // Clear all selections
                selectedGenres.clear()

                // Uncheck all genre actions
                actions.filter { it.checkSetId == GENRE_CHECK_SET_ID }.forEach {
                    it.isChecked = false
                    notifyActionChanged(findActionPositionById(it.id))
                }
            }

            ACTION_APPLY -> {
                // Apply selections and dismiss
                onGenresSelectedListener?.invoke(selectedGenres.toList())
                finishGuidedStepSupportFragments()
            }

            ACTION_CANCEL -> {
                // Cancel and dismiss
                finishGuidedStepSupportFragments()
            }

            in ACTION_GENRE_START until (ACTION_GENRE_START + Genre.values().size) -> {
                // Toggle genre selection
                val genreIndex = (action.id - ACTION_GENRE_START).toInt()
                val genre = Genre.values()[genreIndex]

                if (action.isChecked) {
                    if (!selectedGenres.contains(genre)) {
                        selectedGenres.add(genre)
                    }
                } else {
                    selectedGenres.remove(genre)
                }
            }
        }
    }

    fun setOnGenresSelectedListener(listener: (List<Genre>) -> Unit) {
        onGenresSelectedListener = listener
    }

    companion object {
        private const val ARG_SELECTED_GENRES = "selected_genres"
        private const val ARG_CONTENT_TYPE = "content_type"

        private const val ACTION_CLEAR_ALL = 1L
        private const val ACTION_APPLY = 2L
        private const val ACTION_CANCEL = 3L
        private const val ACTION_GENRE_START = 100L
        private const val GENRE_CHECK_SET_ID = 1

        fun newInstance(selectedGenres: List<Genre>, contentType: ContentType): GenreFilterDialogFragment {
            val fragment = GenreFilterDialogFragment()
            val args = Bundle().apply {
                putStringArrayList(ARG_SELECTED_GENRES, ArrayList(selectedGenres.map { it.englishName }))
                putString(ARG_CONTENT_TYPE, contentType.name)
            }
            fragment.arguments = args
            return fragment
        }
    }
}
