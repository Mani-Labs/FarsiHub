package com.example.farsilandtv.data.model

/**
 * Represents a filter/action card to be displayed in grid views
 * Used for genre filtering, sorting, search, etc.
 */
data class FilterCard(
    val title: String,
    val subtitle: String? = null,
    val iconRes: Int? = null,
    val activeFiltersCount: Int = 0,
    val cardType: CardType = CardType.FILTER
) {
    val displayTitle: String
        get() = if (activeFiltersCount > 0) {
            "$title ($activeFiltersCount)"
        } else {
            title
        }

    enum class CardType {
        FILTER,
        SEARCH,
        SORT
    }
}
