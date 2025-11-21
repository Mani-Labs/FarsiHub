package com.example.farsilandtv.data.database

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Manages user's database source preference
 *
 * EXTERNAL AUDIT FIX F2 (2025-11-21): Added Flow-based reactive observation
 * Allows UI and Paging to automatically update when database source changes
 */
class DatabasePreferences(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    /**
     * Get current database source
     */
    fun getCurrentSource(): DatabaseSource {
        val fileName = prefs.getString(KEY_DATABASE_SOURCE, DatabaseSource.FARSILAND.fileName)
        return DatabaseSource.fromFileName(fileName ?: DatabaseSource.FARSILAND.fileName)
    }

    /**
     * EXTERNAL AUDIT FIX F2 (2025-11-21): Observe database source changes as Flow
     *
     * Issue: UI shows stale data when user switches database source (Farsiland → Namakade)
     * Root Cause: Pager is created once with static URL pattern, doesn't react to source changes
     * Solution: Emit new DatabaseSource whenever SharedPreferences change
     *
     * Usage in ContentRepository:
     * ```
     * fun getMoviesPaged(): Flow<PagingData<Movie>> {
     *     return databasePreferences.observeCurrentSource().flatMapLatest { source ->
     *         Pager(...).flow // Recreate Pager for new source
     *     }
     * }
     * ```
     *
     * @return Flow that emits DatabaseSource on every change (initial value + updates)
     */
    fun observeCurrentSource(): Flow<DatabaseSource> = callbackFlow {
        // Emit current value immediately
        trySend(getCurrentSource())

        // Listen for changes
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_DATABASE_SOURCE) {
                trySend(getCurrentSource())
            }
        }

        prefs.registerOnSharedPreferenceChangeListener(listener)

        // Cleanup when Flow is cancelled
        awaitClose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    /**
     * Set database source and return true if changed
     */
    fun setDatabaseSource(source: DatabaseSource): Boolean {
        val currentSource = getCurrentSource()
        if (currentSource != source) {
            prefs.edit()
                .putString(KEY_DATABASE_SOURCE, source.fileName)
                .apply()
            return true // Changed
        }
        return false // No change
    }

    companion object {
        private const val PREFS_NAME = "database_preferences"
        private const val KEY_DATABASE_SOURCE = "database_source"

        @Volatile
        private var INSTANCE: DatabasePreferences? = null

        fun getInstance(context: Context): DatabasePreferences {
            return INSTANCE ?: synchronized(this) {
                val instance = DatabasePreferences(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }
}
