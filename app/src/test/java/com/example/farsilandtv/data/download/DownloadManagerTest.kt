package com.example.farsilandtv.data.download

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertNull

/**
 * Unit tests for Download models and enums
 *
 * Note: DownloadManager class tests require Robolectric/AndroidTest
 * because it needs Android Context. These tests focus on the pure
 * data classes that don't require Android context.
 */
class DownloadManagerTest {

    // ============================================================================
    // DownloadItem Tests
    // ============================================================================

    @Test
    fun `DownloadItem progress calculates correctly`() {
        // Arrange
        val item = DownloadItem(
            id = "movie_1",
            contentType = ContentType.MOVIE,
            title = "Test Movie",
            subtitle = null,
            posterUrl = null,
            videoUrl = "https://example.com/video.mp4",
            localFilePath = null,
            fileSize = 100,
            downloadedBytes = 50
        )

        // Assert
        assertEquals(50, item.progress, "Progress should be 50%")
    }

    @Test
    fun `DownloadItem progress is 0 when fileSize is 0`() {
        // Arrange
        val item = DownloadItem(
            id = "movie_1",
            contentType = ContentType.MOVIE,
            title = "Test Movie",
            subtitle = null,
            posterUrl = null,
            videoUrl = "https://example.com/video.mp4",
            localFilePath = null,
            fileSize = 0,
            downloadedBytes = 0
        )

        // Assert
        assertEquals(0, item.progress, "Progress should be 0 when fileSize is 0")
    }

    @Test
    fun `DownloadItem progress is 100 when fully downloaded`() {
        // Arrange
        val item = DownloadItem(
            id = "movie_1",
            contentType = ContentType.MOVIE,
            title = "Test Movie",
            subtitle = null,
            posterUrl = null,
            videoUrl = "https://example.com/video.mp4",
            localFilePath = "/path/to/file.mp4",
            fileSize = 1000,
            downloadedBytes = 1000,
            status = DownloadStatus.COMPLETED
        )

        // Assert
        assertEquals(100, item.progress, "Progress should be 100 when fully downloaded")
    }

    @Test
    fun `DownloadItem isComplete is true when status is COMPLETED`() {
        // Arrange
        val item = DownloadItem(
            id = "movie_1",
            contentType = ContentType.MOVIE,
            title = "Test Movie",
            subtitle = null,
            posterUrl = null,
            videoUrl = "https://example.com/video.mp4",
            localFilePath = "/path/to/file.mp4",
            status = DownloadStatus.COMPLETED
        )

        // Assert
        assertTrue(item.isComplete, "Item should be complete")
    }

    @Test
    fun `DownloadItem isComplete is false for other statuses`() {
        // Test all non-completed statuses
        val nonCompletedStatuses = listOf(
            DownloadStatus.PENDING,
            DownloadStatus.DOWNLOADING,
            DownloadStatus.PAUSED,
            DownloadStatus.FAILED,
            DownloadStatus.CANCELLED
        )

        nonCompletedStatuses.forEach { status ->
            val item = DownloadItem(
                id = "movie_1",
                contentType = ContentType.MOVIE,
                title = "Test Movie",
                subtitle = null,
                posterUrl = null,
                videoUrl = "https://example.com/video.mp4",
                localFilePath = null,
                status = status
            )

            assertFalse(item.isComplete, "Item with status $status should not be complete")
        }
    }

    @Test
    fun `DownloadItem isDownloading is true when status is DOWNLOADING`() {
        // Arrange
        val item = DownloadItem(
            id = "movie_1",
            contentType = ContentType.MOVIE,
            title = "Test Movie",
            subtitle = null,
            posterUrl = null,
            videoUrl = "https://example.com/video.mp4",
            localFilePath = null,
            status = DownloadStatus.DOWNLOADING
        )

        // Assert
        assertTrue(item.isDownloading, "Item should be downloading")
    }

    @Test
    fun `DownloadItem canPlay requires COMPLETED status and localFilePath`() {
        // Arrange - completed with path
        val playable = DownloadItem(
            id = "movie_1",
            contentType = ContentType.MOVIE,
            title = "Test",
            subtitle = null,
            posterUrl = null,
            videoUrl = "https://example.com/video.mp4",
            localFilePath = "/path/to/file.mp4",
            status = DownloadStatus.COMPLETED
        )

        // completed without path
        val noPath = playable.copy(localFilePath = null)

        // not completed with path
        val notComplete = playable.copy(status = DownloadStatus.DOWNLOADING)

        // Assert
        assertTrue(playable.canPlay, "Should be playable when complete with path")
        assertFalse(noPath.canPlay, "Should not be playable without path")
        assertFalse(notComplete.canPlay, "Should not be playable when not complete")
    }

    @Test
    fun `DownloadItem handles episode content type`() {
        // Arrange
        val episode = DownloadItem(
            id = "episode_123",
            contentType = ContentType.EPISODE,
            title = "Test Series",
            subtitle = "S1 E5",
            posterUrl = "https://example.com/poster.jpg",
            videoUrl = "https://example.com/video.mp4",
            localFilePath = null,
            status = DownloadStatus.PENDING
        )

        // Assert
        assertEquals(ContentType.EPISODE, episode.contentType)
        assertEquals("S1 E5", episode.subtitle)
    }

    @Test
    fun `DownloadItem default values are correct`() {
        // Arrange & Act
        val item = DownloadItem(
            id = "movie_1",
            contentType = ContentType.MOVIE,
            title = "Test",
            subtitle = null,
            posterUrl = null,
            videoUrl = "https://example.com/video.mp4",
            localFilePath = null
        )

        // Assert defaults
        assertEquals(0, item.fileSize)
        assertEquals(0, item.downloadedBytes)
        assertEquals(DownloadStatus.PENDING, item.status)
        assertNull(item.completedAt)
        assertNull(item.errorMessage)
    }

    @Test
    fun `DownloadItem copy creates independent instance`() {
        // Arrange
        val original = DownloadItem(
            id = "movie_1",
            contentType = ContentType.MOVIE,
            title = "Original",
            subtitle = null,
            posterUrl = null,
            videoUrl = "https://example.com/video.mp4",
            localFilePath = null,
            status = DownloadStatus.PENDING
        )

        // Act
        val copy = original.copy(title = "Copy", status = DownloadStatus.DOWNLOADING)

        // Assert
        assertEquals("Original", original.title)
        assertEquals("Copy", copy.title)
        assertEquals(DownloadStatus.PENDING, original.status)
        assertEquals(DownloadStatus.DOWNLOADING, copy.status)
    }

    // ============================================================================
    // ContentType Tests
    // ============================================================================

    @Test
    fun `ContentType enum has expected values`() {
        // Assert
        assertEquals(2, ContentType.values().size)
        assertTrue(ContentType.values().contains(ContentType.MOVIE))
        assertTrue(ContentType.values().contains(ContentType.EPISODE))
    }

    @Test
    fun `ContentType can be used in when expression`() {
        // Test all enum values are handled
        val testCases = listOf(
            ContentType.MOVIE to "movie",
            ContentType.EPISODE to "episode"
        )

        testCases.forEach { (type, expected) ->
            val result = when (type) {
                ContentType.MOVIE -> "movie"
                ContentType.EPISODE -> "episode"
            }
            assertEquals(expected, result)
        }
    }

    // ============================================================================
    // DownloadStatus Tests
    // ============================================================================

    @Test
    fun `DownloadStatus enum has expected values`() {
        // Assert
        val expectedStatuses = listOf(
            DownloadStatus.PENDING,
            DownloadStatus.DOWNLOADING,
            DownloadStatus.PAUSED,
            DownloadStatus.COMPLETED,
            DownloadStatus.FAILED,
            DownloadStatus.CANCELLED
        )

        assertEquals(6, DownloadStatus.values().size)
        expectedStatuses.forEach { status ->
            assertTrue(DownloadStatus.values().contains(status), "Should contain $status")
        }
    }

    @Test
    fun `DownloadStatus can be used in when expression`() {
        // Test that all statuses can be handled
        DownloadStatus.values().forEach { status ->
            val label = when (status) {
                DownloadStatus.PENDING -> "Pending"
                DownloadStatus.DOWNLOADING -> "Downloading"
                DownloadStatus.PAUSED -> "Paused"
                DownloadStatus.COMPLETED -> "Completed"
                DownloadStatus.FAILED -> "Failed"
                DownloadStatus.CANCELLED -> "Cancelled"
            }
            assertTrue(label.isNotEmpty(), "Status $status should have a label")
        }
    }

    // ============================================================================
    // Download ID Generation Tests
    // ============================================================================

    @Test
    fun `Movie download ID format is correct`() {
        // Standard movie ID format
        val movieId = 123
        val downloadId = "movie_$movieId"

        assertEquals("movie_123", downloadId)
    }

    @Test
    fun `Episode download ID format is correct`() {
        // Standard episode ID format
        val episodeId = 456
        val downloadId = "episode_$episodeId"

        assertEquals("episode_456", downloadId)
    }

    // ============================================================================
    // Progress Calculation Edge Cases
    // ============================================================================

    @Test
    fun `DownloadItem progress handles large file sizes`() {
        // Arrange - 4GB file, 2GB downloaded
        val item = DownloadItem(
            id = "movie_1",
            contentType = ContentType.MOVIE,
            title = "Large Movie",
            subtitle = null,
            posterUrl = null,
            videoUrl = "https://example.com/video.mp4",
            localFilePath = null,
            fileSize = 4L * 1024 * 1024 * 1024, // 4GB
            downloadedBytes = 2L * 1024 * 1024 * 1024 // 2GB
        )

        // Assert
        assertEquals(50, item.progress, "Progress should be 50% for large file")
    }

    @Test
    fun `DownloadItem progress rounds down`() {
        // Arrange - 33.33% complete
        val item = DownloadItem(
            id = "movie_1",
            contentType = ContentType.MOVIE,
            title = "Test",
            subtitle = null,
            posterUrl = null,
            videoUrl = "https://example.com/video.mp4",
            localFilePath = null,
            fileSize = 300,
            downloadedBytes = 100
        )

        // Assert - should be 33 not 34
        assertEquals(33, item.progress, "Progress should round down")
    }
}
