package com.example.farsilandtv.utils

import android.content.Context
import android.content.SharedPreferences
import com.example.farsilandtv.data.model.Genre

/**
 * Helper for persisting selected genres across app restarts
 * Uses SharedPreferences to store genre selections
 */
class GenrePreferences(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Save selected genres for movies
     */
    fun saveSelectedMovieGenres(genres: List<Genre>) {
        val genreNames = genres.joinToString(",") { it.englishName }
        prefs.edit().putString(KEY_MOVIE_GENRES, genreNames).apply()
    }

    /**
     * Get selected genres for movies
     */
    fun getSelectedMovieGenres(): List<Genre> {
        val genreString = prefs.getString(KEY_MOVIE_GENRES, "") ?: ""
        if (genreString.isEmpty()) return emptyList()

        return genreString.split(",")
            .mapNotNull { Genre.fromEnglishName(it) }
    }

    /**
     * Save selected genres for TV shows
     */
    fun saveSelectedShowGenres(genres: List<Genre>) {
        val genreNames = genres.joinToString(",") { it.englishName }
        prefs.edit().putString(KEY_SHOW_GENRES, genreNames).apply()
    }

    /**
     * Get selected genres for TV shows
     */
    fun getSelectedShowGenres(): List<Genre> {
        val genreString = prefs.getString(KEY_SHOW_GENRES, "") ?: ""
        if (genreString.isEmpty()) return emptyList()

        return genreString.split(",")
            .mapNotNull { Genre.fromEnglishName(it) }
    }

    /**
     * Clear all genre selections for movies
     */
    fun clearMovieGenres() {
        prefs.edit().remove(KEY_MOVIE_GENRES).apply()
    }

    /**
     * Clear all genre selections for TV shows
     */
    fun clearShowGenres() {
        prefs.edit().remove(KEY_SHOW_GENRES).apply()
    }

    companion object {
        private const val PREFS_NAME = "genre_filter_prefs"
        private const val KEY_MOVIE_GENRES = "selected_movie_genres"
        private const val KEY_SHOW_GENRES = "selected_show_genres"
    }
}
