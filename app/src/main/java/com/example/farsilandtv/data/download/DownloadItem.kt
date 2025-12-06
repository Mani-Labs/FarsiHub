package com.example.farsilandtv.data.download

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter

/**
 * Phase 6: Offline Mode - Download Item Entity
 *
 * Tracks downloaded content for offline playback
 */
@Entity(tableName = "downloads")
data class DownloadItem(
    @PrimaryKey
    val id: String,  // Content ID (movie_123 or episode_456)
    val contentType: ContentType,
    val title: String,
    val subtitle: String?,  // For episodes: "S1 E5" etc.
    val posterUrl: String?,
    val videoUrl: String,
    val localFilePath: String?,  // Path to downloaded file
    val fileSize: Long = 0,
    val downloadedBytes: Long = 0,
    val status: DownloadStatus = DownloadStatus.PENDING,
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
    val errorMessage: String? = null
) {
    val progress: Int
        get() = if (fileSize > 0) ((downloadedBytes * 100) / fileSize).toInt() else 0

    val isComplete: Boolean
        get() = status == DownloadStatus.COMPLETED

    val isDownloading: Boolean
        get() = status == DownloadStatus.DOWNLOADING

    val canPlay: Boolean
        get() = isComplete && localFilePath != null
}

enum class ContentType {
    MOVIE,
    EPISODE
}

enum class DownloadStatus {
    PENDING,      // Queued for download
    DOWNLOADING,  // Currently downloading
    PAUSED,       // Paused by user
    COMPLETED,    // Download finished
    FAILED,       // Download failed
    CANCELLED     // Cancelled by user
}

/**
 * DL-L5: TypeConverters for enum types
 * Room requires TypeConverters to store enums in database
 */
class DownloadTypeConverters {
    @TypeConverter
    fun fromDownloadStatus(status: DownloadStatus): String = status.name

    @TypeConverter
    fun toDownloadStatus(value: String): DownloadStatus = DownloadStatus.valueOf(value)

    @TypeConverter
    fun fromContentType(type: ContentType): String = type.name

    @TypeConverter
    fun toContentType(value: String): ContentType = ContentType.valueOf(value)
}
