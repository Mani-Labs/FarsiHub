package com.example.farsilandtv.data.download

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * Phase 6: Offline Mode - Download Data Access Object
 */
@Dao
interface DownloadDao {

    @Query("SELECT * FROM downloads ORDER BY createdAt DESC")
    fun getAllDownloads(): Flow<List<DownloadItem>>

    @Query("SELECT * FROM downloads WHERE status = :status ORDER BY createdAt DESC")
    fun getDownloadsByStatus(status: DownloadStatus): Flow<List<DownloadItem>>

    @Query("SELECT * FROM downloads WHERE status IN ('PENDING', 'DOWNLOADING') ORDER BY createdAt ASC")
    fun getActiveDownloads(): Flow<List<DownloadItem>>

    @Query("SELECT * FROM downloads WHERE status = 'COMPLETED' ORDER BY completedAt DESC")
    fun getCompletedDownloads(): Flow<List<DownloadItem>>

    // Non-Flow version for one-time operations like clearCompletedDownloads
    @Query("SELECT * FROM downloads WHERE status = 'COMPLETED'")
    suspend fun getCompletedDownloadsOnce(): List<DownloadItem>

    @Query("SELECT * FROM downloads WHERE id = :id")
    suspend fun getDownloadById(id: String): DownloadItem?

    @Query("SELECT * FROM downloads WHERE id = :id")
    fun observeDownloadById(id: String): Flow<DownloadItem?>

    @Query("SELECT EXISTS(SELECT 1 FROM downloads WHERE id = :id AND status = 'COMPLETED')")
    suspend fun isDownloaded(id: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(download: DownloadItem)

    @Update
    suspend fun update(download: DownloadItem)

    @Delete
    suspend fun delete(download: DownloadItem)

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE downloads SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: String, status: DownloadStatus)

    @Query("UPDATE downloads SET downloadedBytes = :bytes WHERE id = :id")
    suspend fun updateProgress(id: String, bytes: Long)

    @Query("""
        UPDATE downloads
        SET downloadedBytes = :bytes, status = :status, localFilePath = :filePath, completedAt = :completedAt
        WHERE id = :id
    """)
    suspend fun markCompleted(id: String, bytes: Long, status: DownloadStatus, filePath: String, completedAt: Long)

    @Query("""
        UPDATE downloads
        SET status = :status, errorMessage = :errorMessage
        WHERE id = :id
    """)
    suspend fun markFailed(id: String, status: DownloadStatus, errorMessage: String)

    @Query("DELETE FROM downloads WHERE status = 'COMPLETED'")
    suspend fun clearCompletedDownloads()

    @Query("DELETE FROM downloads")
    suspend fun clearAllDownloads()

    @Query("SELECT COUNT(*) FROM downloads WHERE status = 'COMPLETED'")
    suspend fun getCompletedCount(): Int

    @Query("SELECT SUM(fileSize) FROM downloads WHERE status = 'COMPLETED'")
    suspend fun getTotalDownloadedSize(): Long?
}
