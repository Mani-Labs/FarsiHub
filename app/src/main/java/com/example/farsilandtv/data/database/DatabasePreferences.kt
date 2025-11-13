package com.example.farsilandtv.data.database

import android.content.Context
import android.content.SharedPreferences

/**
 * Manages user's database source preference
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
