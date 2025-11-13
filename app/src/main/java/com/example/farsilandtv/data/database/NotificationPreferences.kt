package com.example.farsilandtv.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * Entity for storing user notification preferences
 * Feature #9 - Push Notifications
 */
@Entity(tableName = "notification_preferences")
data class NotificationPreferences(
    @PrimaryKey val id: Int = 1, // Single row table
    val newEpisodesEnabled: Boolean = true,
    val newSeasonsEnabled: Boolean = true,
    val weeklyDigestEnabled: Boolean = false,
    val quietHoursStart: Int = 22, // 10 PM (24-hour format)
    val quietHoursEnd: Int = 8,    // 8 AM (24-hour format)
    val lastUpdated: Long = System.currentTimeMillis()
)

/**
 * DAO for notification preferences
 */
@Dao
interface NotificationPreferencesDao {

    @Query("SELECT * FROM notification_preferences WHERE id = 1")
    fun getPreferences(): Flow<NotificationPreferences?>

    @Query("SELECT * FROM notification_preferences WHERE id = 1")
    suspend fun getPreferencesOnce(): NotificationPreferences?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun savePreferences(preferences: NotificationPreferences)

    @Update
    suspend fun updatePreferences(preferences: NotificationPreferences)

    /**
     * Check if notifications are allowed at current time
     * Respects quiet hours setting
     */
    @Query("""
        SELECT CASE
            WHEN quietHoursStart < quietHoursEnd THEN
                :currentHour >= quietHoursEnd AND :currentHour < quietHoursStart
            ELSE
                :currentHour >= quietHoursEnd OR :currentHour < quietHoursStart
        END
        FROM notification_preferences WHERE id = 1
    """)
    suspend fun isAllowedAtHour(currentHour: Int): Boolean?

    /**
     * Get specific preference flags
     */
    @Query("SELECT newEpisodesEnabled FROM notification_preferences WHERE id = 1")
    suspend fun isNewEpisodesEnabled(): Boolean?

    @Query("SELECT newSeasonsEnabled FROM notification_preferences WHERE id = 1")
    suspend fun isNewSeasonsEnabled(): Boolean?

    @Query("SELECT weeklyDigestEnabled FROM notification_preferences WHERE id = 1")
    suspend fun isWeeklyDigestEnabled(): Boolean?
}
