package com.example.farsilandtv.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room database for Farsiland TV app
 * Currently stores playback positions
 */
@Database(
    entities = [PlaybackPosition::class],
    version = 3, // Bumped for watched status tracking (Feature #2)
    exportSchema = false
)
abstract class FarsilandDatabase : RoomDatabase() {

    abstract fun playbackPositionDao(): PlaybackPositionDao

    companion object {
        @Volatile
        private var INSTANCE: FarsilandDatabase? = null

        /**
         * Migration from version 2 to 3
         * Adds watched status tracking fields:
         * - completedAt: Timestamp when marked as completed (nullable)
         * - Updates lastWatchedAt to be NOT NULL with default 0
         * - isCompleted already existed, keeping its current state
         */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add completedAt column (nullable, for when content is marked complete)
                database.execSQL(
                    "ALTER TABLE playback_positions ADD COLUMN completedAt INTEGER DEFAULT NULL"
                )

                // Note: lastWatchedAt and isCompleted already exist from v2
                // No need to add them again, just ensuring they have proper defaults
                // If any rows have null lastWatchedAt, update them to 0
                database.execSQL(
                    "UPDATE playback_positions SET lastWatchedAt = 0 WHERE lastWatchedAt IS NULL"
                )
            }
        }

        fun getInstance(context: Context): FarsilandDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    FarsilandDatabase::class.java,
                    "farsiland_database"
                )
                    .addMigrations(MIGRATION_2_3)
                    .build()

                INSTANCE = instance
                instance
            }
        }
    }
}
