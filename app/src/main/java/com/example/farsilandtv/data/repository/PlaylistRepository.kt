package com.example.farsilandtv.data.repository

import com.example.farsilandtv.data.database.*
import com.example.farsilandtv.data.models.Movie
import com.example.farsilandtv.data.models.Series
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for playlist management
 * Handles playlists and their content with business logic
 *
 * Hilt-managed singleton - injected via constructor
 */
@Singleton
class PlaylistRepository @Inject constructor(
    private val playlistDao: PlaylistDao,
    private val playlistItemDao: PlaylistItemDao
) {

    // ========== Playlist Management ==========

    /**
     * Create new playlist
     * @return ID of newly created playlist
     */
    suspend fun createPlaylist(
        name: String,
        description: String? = null,
        coverImageUrl: String? = null
    ): Long {
        val playlist = Playlist(
            name = name,
            description = description,
            coverImageUrl = coverImageUrl,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        return playlistDao.insert(playlist)
    }

    /**
     * Update existing playlist
     */
    suspend fun updatePlaylist(playlist: Playlist) {
        // Update the updatedAt timestamp
        val updated = playlist.copy(updatedAt = System.currentTimeMillis())
        playlistDao.update(updated)
    }

    /**
     * Update playlist name and description
     */
    suspend fun updatePlaylistInfo(
        playlistId: Long,
        name: String,
        description: String? = null
    ) {
        val playlist = playlistDao.getPlaylistOnce(playlistId)
        if (playlist != null) {
            val updated = playlist.copy(
                name = name,
                description = description,
                updatedAt = System.currentTimeMillis()
            )
            playlistDao.update(updated)
        }
    }

    /**
     * Delete playlist (cascade deletes all items)
     */
    suspend fun deletePlaylist(playlistId: Long) {
        playlistDao.deleteById(playlistId)
    }

    /**
     * Delete playlist by object
     */
    suspend fun deletePlaylist(playlist: Playlist) {
        playlistDao.delete(playlist)
    }

    // ========== Query Playlists ==========

    /**
     * Get all playlists (reactive)
     */
    fun getAllPlaylists(): Flow<List<Playlist>> {
        return playlistDao.getAllPlaylists()
    }

    /**
     * Get single playlist (reactive)
     */
    fun getPlaylist(playlistId: Long): Flow<Playlist?> {
        return playlistDao.getPlaylist(playlistId)
    }

    /**
     * Get single playlist (one-time)
     */
    suspend fun getPlaylistOnce(playlistId: Long): Playlist? {
        return playlistDao.getPlaylistOnce(playlistId)
    }

    /**
     * Get playlists ordered by name
     */
    fun getPlaylistsByName(): Flow<List<Playlist>> {
        return playlistDao.getPlaylistsByName()
    }

    /**
     * Get playlists ordered by creation date
     */
    fun getPlaylistsByCreationDate(): Flow<List<Playlist>> {
        return playlistDao.getPlaylistsByCreationDate()
    }

    /**
     * Search playlists by name
     */
    fun searchPlaylists(query: String): Flow<List<Playlist>> {
        return playlistDao.searchPlaylists(query)
    }

    // ========== Playlist Item Management ==========

    /**
     * Add movie to playlist
     */
    suspend fun addMovieToPlaylist(playlistId: Long, movie: Movie) {
        // Get next position
        val maxPosition = playlistItemDao.getMaxPosition(playlistId)

        val item = PlaylistItem(
            playlistId = playlistId,
            contentId = "movie-${movie.id}",
            contentType = PlaylistItem.ContentType.MOVIE,
            addedAt = System.currentTimeMillis(),
            position = maxPosition + 1
        )
        playlistItemDao.insert(item)

        // Update playlist timestamp
        playlistDao.updateTimestamp(playlistId)
    }

    /**
     * Add series to playlist
     */
    suspend fun addSeriesToPlaylist(playlistId: Long, series: Series) {
        // Get next position
        val maxPosition = playlistItemDao.getMaxPosition(playlistId)

        val item = PlaylistItem(
            playlistId = playlistId,
            contentId = "series-${series.id}",
            contentType = PlaylistItem.ContentType.SERIES,
            addedAt = System.currentTimeMillis(),
            position = maxPosition + 1
        )
        playlistItemDao.insert(item)

        // Update playlist timestamp
        playlistDao.updateTimestamp(playlistId)
    }

    /**
     * Add content to playlist by ID and type
     * Generic method for when you have minimal data
     */
    suspend fun addToPlaylist(
        playlistId: Long,
        contentId: String,
        contentType: String
    ) {
        // Get next position
        val maxPosition = playlistItemDao.getMaxPosition(playlistId)

        val type = when (contentType.uppercase()) {
            "MOVIE" -> PlaylistItem.ContentType.MOVIE
            "SERIES" -> PlaylistItem.ContentType.SERIES
            else -> throw IllegalArgumentException("Invalid content type: $contentType")
        }

        val item = PlaylistItem(
            playlistId = playlistId,
            contentId = contentId,
            contentType = type,
            addedAt = System.currentTimeMillis(),
            position = maxPosition + 1
        )
        playlistItemDao.insert(item)

        // Update playlist timestamp
        playlistDao.updateTimestamp(playlistId)
    }

    /**
     * Remove content from playlist
     */
    suspend fun removeFromPlaylist(playlistId: Long, contentId: String) {
        playlistItemDao.deleteByContent(playlistId, contentId)

        // Update playlist timestamp
        playlistDao.updateTimestamp(playlistId)
    }

    /**
     * Remove item by ID
     */
    suspend fun removePlaylistItem(itemId: Long) {
        val item = playlistItemDao.getItem(itemId)
        if (item != null) {
            playlistItemDao.deleteById(itemId)
            // Update playlist timestamp
            playlistDao.updateTimestamp(item.playlistId)
        }
    }

    /**
     * Clear all items from playlist
     */
    suspend fun clearPlaylist(playlistId: Long) {
        playlistItemDao.deleteAllFromPlaylist(playlistId)

        // Update playlist timestamp
        playlistDao.updateTimestamp(playlistId)
    }

    // ========== Query Playlist Items ==========

    /**
     * Get all items in a playlist (reactive)
     */
    fun getPlaylistItems(playlistId: Long): Flow<List<PlaylistItem>> {
        return playlistItemDao.getPlaylistItems(playlistId)
    }

    /**
     * Get all items in a playlist (one-time)
     */
    suspend fun getPlaylistItemsOnce(playlistId: Long): List<PlaylistItem> {
        return playlistItemDao.getPlaylistItemsOnce(playlistId)
    }

    /**
     * Get items filtered by content type
     */
    fun getItemsByType(
        playlistId: Long,
        contentType: PlaylistItem.ContentType
    ): Flow<List<PlaylistItem>> {
        return playlistItemDao.getItemsByType(playlistId, contentType.name)
    }

    /**
     * Get only movies from playlist
     */
    fun getPlaylistMovies(playlistId: Long): Flow<List<PlaylistItem>> {
        return getItemsByType(playlistId, PlaylistItem.ContentType.MOVIE)
    }

    /**
     * Get only series from playlist
     */
    fun getPlaylistSeries(playlistId: Long): Flow<List<PlaylistItem>> {
        return getItemsByType(playlistId, PlaylistItem.ContentType.SERIES)
    }

    // ========== Check Existence ==========

    /**
     * Check if content is in a playlist (reactive)
     */
    fun isInPlaylist(playlistId: Long, contentId: String): Flow<Boolean> {
        return playlistItemDao.isInPlaylist(playlistId, contentId)
    }

    /**
     * Check if content is in a playlist (one-time)
     */
    suspend fun isInPlaylistOnce(playlistId: Long, contentId: String): Boolean {
        return playlistItemDao.isInPlaylistOnce(playlistId, contentId)
    }

    /**
     * Check if movie is in a playlist (reactive)
     */
    fun isMovieInPlaylist(playlistId: Long, movieId: Int): Flow<Boolean> {
        return playlistItemDao.isInPlaylist(playlistId, "movie-$movieId")
    }

    /**
     * Check if series is in a playlist (reactive)
     */
    fun isSeriesInPlaylist(playlistId: Long, seriesId: Int): Flow<Boolean> {
        return playlistItemDao.isInPlaylist(playlistId, "series-$seriesId")
    }

    /**
     * Get all playlists containing specific content
     */
    fun getPlaylistsForContent(contentId: String): Flow<List<Long>> {
        return playlistItemDao.getPlaylistsForContent(contentId)
    }

    // ========== Reordering ==========

    /**
     * Reorder playlist items
     * @param playlistId ID of the playlist
     * @param itemIds List of item IDs in desired order
     */
    suspend fun reorderPlaylist(playlistId: Long, itemIds: List<Long>) {
        itemIds.forEachIndexed { index, itemId ->
            playlistItemDao.updatePosition(itemId, index)
        }

        // Update playlist timestamp
        playlistDao.updateTimestamp(playlistId)
    }

    /**
     * Move item to specific position
     */
    suspend fun moveItem(itemId: Long, newPosition: Int) {
        val item = playlistItemDao.getItem(itemId)
        if (item != null) {
            playlistItemDao.updatePosition(itemId, newPosition)

            // Update playlist timestamp
            playlistDao.updateTimestamp(item.playlistId)
        }
    }

    // ========== Statistics ==========

    /**
     * Get count of items in a playlist
     */
    suspend fun getItemCount(playlistId: Long): Int {
        return playlistItemDao.getItemCount(playlistId)
    }

    /**
     * Get count of items in a playlist (reactive)
     */
    fun getItemCountFlow(playlistId: Long): Flow<Int> {
        return playlistDao.getItemCountFlow(playlistId)
    }

    /**
     * Get count by content type
     */
    suspend fun getCountByType(
        playlistId: Long,
        contentType: PlaylistItem.ContentType
    ): Int {
        return playlistItemDao.getCountByType(playlistId, contentType.name)
    }

    /**
     * Get movie count in playlist
     */
    suspend fun getMovieCount(playlistId: Long): Int {
        return playlistItemDao.getCountByType(playlistId, PlaylistItem.ContentType.MOVIE.name)
    }

    /**
     * Get series count in playlist
     */
    suspend fun getSeriesCount(playlistId: Long): Int {
        return playlistItemDao.getCountByType(playlistId, PlaylistItem.ContentType.SERIES.name)
    }

    /**
     * Get total playlists count
     */
    suspend fun getTotalPlaylistsCount(): Int {
        return playlistDao.getPlaylistsCount()
    }

    // ========== Utilities ==========

    /**
     * Clear all playlists (for testing/reset)
     */
    suspend fun clearAllPlaylists() {
        playlistDao.clearAll()
    }

    /**
     * Clear all playlist items (for testing/reset)
     */
    suspend fun clearAllItems() {
        playlistItemDao.clearAll()
    }
}
