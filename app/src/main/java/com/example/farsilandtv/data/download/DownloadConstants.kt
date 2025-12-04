package com.example.farsilandtv.data.download

/**
 * Constants for download ID prefixes
 * Used to identify content type from download IDs
 */
object DownloadConstants {
    const val MOVIE_PREFIX = "movie_"
    const val EPISODE_PREFIX = "episode_"

    /**
     * Create a movie download ID
     */
    fun movieId(id: Int): String = "$MOVIE_PREFIX$id"

    /**
     * Create an episode download ID
     */
    fun episodeId(id: Int): String = "$EPISODE_PREFIX$id"
}
