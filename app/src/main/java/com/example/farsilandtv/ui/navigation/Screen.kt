package com.example.farsilandtv.ui.navigation

/**
 * Feature #16: Jetpack Compose for TV - Navigation Routes
 *
 * Defines all screen routes for navigation
 * Week 3 implementation
 */
sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Movies : Screen("movies")
    object Shows : Screen("shows")
    object Search : Screen("search")
    object Favorites : Screen("favorites")

    object MovieDetails : Screen("movie/{movieId}") {
        fun createRoute(movieId: Int) = "movie/$movieId"
        const val ARG_MOVIE_ID = "movieId"
    }

    object SeriesDetails : Screen("series/{seriesId}") {
        fun createRoute(seriesId: Int) = "series/$seriesId"
        const val ARG_SERIES_ID = "seriesId"
    }
}
