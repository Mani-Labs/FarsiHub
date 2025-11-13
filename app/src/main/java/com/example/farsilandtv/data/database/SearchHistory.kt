package com.example.farsilandtv.data.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Search History entity - Tracks user search queries
 * Stores search queries with timestamps for auto-complete suggestions
 */
@Entity(
    tableName = "search_history",
    indices = [Index(value = ["query"])] // For fast auto-complete lookups
)
data class SearchHistory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val query: String,
    val timestamp: Long = System.currentTimeMillis()
)
