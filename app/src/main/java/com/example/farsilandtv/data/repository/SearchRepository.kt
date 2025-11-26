package com.example.farsilandtv.data.repository

import android.content.Context
import com.example.farsilandtv.data.database.AppDatabase
import com.example.farsilandtv.data.database.SearchHistory
import com.example.farsilandtv.utils.SqlSanitizer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Repository for search history and auto-complete suggestions
 * Implements deduplication and limit management
 *
 * SINGLETON PATTERN: Use getInstance() to get the shared instance.
 * This prevents multiple database connections and ensures cache consistency.
 */
class SearchRepository private constructor(context: Context) {

    private val database = AppDatabase.getDatabase(context.applicationContext)
    private val searchHistoryDao = database.searchHistoryDao()

    companion object {
        private const val MAX_HISTORY_SIZE = 50 // Keep only 50 most recent searches
        private const val DEFAULT_RECENT_LIMIT = 10 // Show 10 recent searches by default
        private const val DEFAULT_SUGGESTION_LIMIT = 5 // Show 5 auto-complete suggestions

        @Volatile
        private var INSTANCE: SearchRepository? = null

        /**
         * Get singleton instance of SearchRepository
         * Thread-safe double-check locking pattern
         */
        fun getInstance(context: Context): SearchRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SearchRepository(context.applicationContext).also {
                    INSTANCE = it
                }
            }
        }
    }

    /**
     * Save search query to history
     * Automatically deduplicates and trims to max size
     * @param query The search query text
     */
    suspend fun saveSearch(query: String) {
        val trimmedQuery = query.trim()

        // Don't save empty queries
        if (trimmedQuery.isEmpty()) {
            return
        }

        // Insert/update search (timestamp will be updated if query exists)
        val searchHistory = SearchHistory(
            query = trimmedQuery,
            timestamp = System.currentTimeMillis()
        )
        searchHistoryDao.insert(searchHistory)

        // Maintain size limit (keep only 50 most recent)
        searchHistoryDao.trimToLimit(MAX_HISTORY_SIZE)
    }

    /**
     * Get recent searches
     * Returns just the query strings, not full entities
     * @param limit Maximum number of searches to return (default 10)
     */
    fun getRecentSearches(limit: Int = DEFAULT_RECENT_LIMIT): Flow<List<String>> {
        return searchHistoryDao.getRecentSearches(limit).map { searches ->
            searches.map { it.query }
        }
    }

    /**
     * Get auto-complete suggestions based on prefix
     * Returns suggestions sorted by recency
     *
     * SECURITY: Sanitizes input to prevent SQL injection via LIKE wildcards
     *
     * @param prefix The text prefix to match (case-insensitive)
     */
    fun getSuggestions(prefix: String): Flow<List<String>> {
        val trimmedPrefix = prefix.trim()

        // Return empty list if prefix is too short
        if (trimmedPrefix.isEmpty()) {
            return kotlinx.coroutines.flow.flowOf(emptyList())
        }

        // SECURITY: Sanitize input to prevent SQL wildcard injection
        val sanitizedPrefix = SqlSanitizer.sanitizeLikePattern(trimmedPrefix)

        // Return suggestions matching prefix
        return searchHistoryDao.searchSuggestions(sanitizedPrefix, DEFAULT_SUGGESTION_LIMIT)
    }

    /**
     * Delete specific search query from history
     * @param query The query to remove
     */
    suspend fun deleteSearch(query: String) {
        searchHistoryDao.deleteSearch(query.trim())
    }

    /**
     * Clear all search history
     */
    suspend fun clearHistory() {
        searchHistoryDao.clearAll()
    }

    /**
     * Get total search history count
     */
    suspend fun getHistoryCount(): Int {
        return searchHistoryDao.getCount()
    }
}
