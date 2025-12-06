package com.example.farsilandtv.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * DAO for Search History
 * Handles all database operations for search history and auto-complete
 */
@Dao
interface SearchHistoryDao {

    /**
     * Insert new search query
     * Uses REPLACE strategy to update timestamp if query already exists
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(searchHistory: SearchHistory)

    /**
     * Get recent searches
     * Sorted by most recent first
     * @param limit Maximum number of searches to return (default 10)
     */
    @Query("SELECT * FROM search_history ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentSearches(limit: Int = 10): Flow<List<SearchHistory>>

    /**
     * Get auto-complete suggestions based on prefix
     * Case-insensitive prefix matching, sorted by recency
     *
     * SECURITY: Uses ESCAPE clause to prevent SQL injection via LIKE wildcards.
     * Caller MUST sanitize prefix using SqlSanitizer.sanitizeLikePattern() before calling.
     *
     * @param prefix Search prefix to match (must be sanitized)
     * @param limit Maximum number of suggestions (default 5)
     */
    @Query("""
        SELECT DISTINCT query
        FROM search_history
        WHERE query LIKE :prefix || '%' ESCAPE '\'
        ORDER BY timestamp DESC
        LIMIT :limit
    """)
    fun searchSuggestions(prefix: String, limit: Int = 5): Flow<List<String>>

    /**
     * Delete specific search query
     * Removes all occurrences of the query
     */
    @Query("DELETE FROM search_history WHERE query = :query")
    suspend fun deleteSearch(query: String)

    /**
     * Clear all search history
     */
    @Query("DELETE FROM search_history")
    suspend fun clearAll()

    /**
     * Get total search history count
     */
    @Query("SELECT COUNT(*) FROM search_history")
    suspend fun getCount(): Int

    /**
     * Delete oldest searches to maintain limit
     * Keeps only the most recent N searches
     * @param limit Maximum number of searches to keep
     */
    @Query("""
        DELETE FROM search_history
        WHERE id NOT IN (
            SELECT id FROM search_history
            ORDER BY timestamp DESC
            LIMIT :limit
        )
    """)
    suspend fun trimToLimit(limit: Int)
}
