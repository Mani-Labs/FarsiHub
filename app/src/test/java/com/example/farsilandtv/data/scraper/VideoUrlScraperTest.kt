package com.example.farsilandtv.data.scraper

import com.example.farsilandtv.data.models.VideoUrl
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Unit tests for VideoUrl model and quality/mirror extraction
 *
 * Note: VideoUrlScraper object tests require Robolectric/AndroidTest
 * because it uses Android Log class. These tests focus on the pure
 * VideoUrl model functions that don't require Android context.
 */
class VideoUrlScraperTest {

    // ============================================================================
    // VideoUrl Model Tests
    // ============================================================================

    @Test
    fun `VideoUrl data class creates correct instance`() {
        // Arrange & Act
        val url = VideoUrl(
            url = "https://d1.flnd.buzz/series/test/01.1080.mp4",
            quality = "1080p",
            fileSizeMb = 500.0f,
            mirror = "d1.flnd.buzz"
        )

        // Assert
        assertEquals("https://d1.flnd.buzz/series/test/01.1080.mp4", url.url)
        assertEquals("1080p", url.quality)
        assertEquals(500.0f, url.fileSizeMb)
        assertEquals("d1.flnd.buzz", url.mirror)
    }

    @Test
    fun `VideoUrl handles optional fields`() {
        // Arrange & Act
        val url = VideoUrl(
            url = "https://example.com/video.mp4",
            quality = "720p"
        )

        // Assert
        assertNull(url.fileSizeMb)
        assertNull(url.mirror)
    }

    @Test
    fun `VideoUrl copy creates independent instance`() {
        // Arrange
        val original = VideoUrl(
            url = "https://example.com/video.mp4",
            quality = "1080p",
            mirror = "d1"
        )

        // Act
        val copy = original.copy(quality = "720p")

        // Assert
        assertEquals("1080p", original.quality)
        assertEquals("720p", copy.quality)
        assertEquals(original.url, copy.url)
    }

    @Test
    fun `VideoUrl equality works correctly`() {
        // Arrange
        val url1 = VideoUrl(
            url = "https://example.com/video.mp4",
            quality = "1080p",
            mirror = "d1.flnd.buzz"
        )
        val url2 = VideoUrl(
            url = "https://example.com/video.mp4",
            quality = "1080p",
            mirror = "d1.flnd.buzz"
        )

        // Assert
        assertEquals(url1, url2)
    }

    // ============================================================================
    // Quality Extraction Tests
    // ============================================================================

    @Test
    fun `extractQuality detects 1080p from URL`() {
        // Arrange
        val url = "https://cdn.example.com/video.1080.mp4"

        // Act
        val quality = VideoUrl.extractQuality(url)

        // Assert
        assertEquals("1080p", quality)
    }

    @Test
    fun `extractQuality detects 720p from URL`() {
        // Arrange
        val url = "https://cdn.example.com/video.720.mp4"

        // Act
        val quality = VideoUrl.extractQuality(url)

        // Assert
        assertEquals("720p", quality)
    }

    @Test
    fun `extractQuality detects 480p from URL`() {
        // Arrange
        val url = "https://cdn.example.com/video.480.mp4"

        // Act
        val quality = VideoUrl.extractQuality(url)

        // Assert
        assertEquals("480p", quality)
    }

    @Test
    fun `extractQuality detects 360p from URL`() {
        // Arrange
        val url = "https://cdn.example.com/video.360.mp4"

        // Act
        val quality = VideoUrl.extractQuality(url)

        // Assert
        assertEquals("360p", quality)
    }

    @Test
    fun `extractQuality handles dash separator`() {
        // Arrange
        val urls = listOf(
            "https://cdn.example.com/video-1080.mp4" to "1080p",
            "https://cdn.example.com/video-720.mp4" to "720p",
            "https://cdn.example.com/video-480.mp4" to "480p"
        )

        // Act & Assert
        urls.forEach { (url, expectedQuality) ->
            val quality = VideoUrl.extractQuality(url)
            assertEquals(expectedQuality, quality, "Quality for $url should be $expectedQuality")
        }
    }

    @Test
    fun `extractQuality returns unknown for unrecognized quality`() {
        // Arrange
        val url = "https://cdn.example.com/video.mp4"

        // Act
        val quality = VideoUrl.extractQuality(url)

        // Assert
        assertEquals("unknown", quality)
    }

    // ============================================================================
    // Mirror Extraction Tests
    // ============================================================================

    @Test
    fun `extractMirror extracts d1 flnd buzz domain`() {
        // Arrange
        val url = "https://d1.flnd.buzz/series/test/01.1080.mp4"

        // Act
        val mirror = VideoUrl.extractMirror(url)

        // Assert
        assertEquals("d1.flnd.buzz", mirror)
    }

    @Test
    fun `extractMirror extracts d2 flnd buzz domain`() {
        // Arrange
        val url = "https://d2.flnd.buzz/series/test/01.1080.mp4"

        // Act
        val mirror = VideoUrl.extractMirror(url)

        // Assert
        assertEquals("d2.flnd.buzz", mirror)
    }

    @Test
    fun `extractMirror extracts generic domain from URL`() {
        // Arrange
        val url = "https://s1.farsicdn.buzz/video.mp4"

        // Act
        val mirror = VideoUrl.extractMirror(url)

        // Assert
        assertEquals("s1.farsicdn.buzz", mirror)
    }

    @Test
    fun `extractMirror handles complex paths`() {
        // Arrange
        val url = "https://cdn.example.com/movies/2024/test-movie/video.1080.mp4"

        // Act
        val mirror = VideoUrl.extractMirror(url)

        // Assert
        assertEquals("cdn.example.com", mirror)
    }

    // ============================================================================
    // URL Validation Tests
    // ============================================================================

    @Test
    fun `VideoUrl validates HTTPS URLs`() {
        // Arrange
        val httpsUrl = VideoUrl(
            url = "https://example.com/video.mp4",
            quality = "1080p"
        )

        // Assert
        assertTrue(httpsUrl.url.startsWith("https://"))
    }

    @Test
    fun `VideoUrl stores quality string correctly`() {
        // Test all standard qualities
        val qualities = listOf("1080p", "720p", "480p", "360p", "unknown")

        qualities.forEach { q ->
            val url = VideoUrl(url = "https://example.com/video.mp4", quality = q)
            assertEquals(q, url.quality)
        }
    }

    // ============================================================================
    // Edge Cases
    // ============================================================================

    @Test
    fun `extractQuality handles URL with 1080 dash format`() {
        // URL with dash format should still detect quality
        val url = "https://cdn.example.com/video-1080.mp4"
        val quality = VideoUrl.extractQuality(url)

        assertEquals("1080p", quality)
    }

    @Test
    fun `extractMirror handles URL with port`() {
        // Arrange
        val url = "https://cdn.example.com:8080/video.mp4"

        // Act
        val mirror = VideoUrl.extractMirror(url)

        // Assert
        // Should extract domain including port
        assertTrue(mirror?.contains("cdn.example.com") == true)
    }

    @Test
    fun `extractMirror handles URL with no path`() {
        // Arrange
        val url = "https://cdn.example.com"

        // Act
        val mirror = VideoUrl.extractMirror(url)

        // Assert
        assertEquals("cdn.example.com", mirror)
    }

    @Test
    fun `VideoUrl list operations work correctly`() {
        // Arrange
        val urls = listOf(
            VideoUrl("https://d1.flnd.buzz/v1.1080.mp4", "1080p", mirror = "d1.flnd.buzz"),
            VideoUrl("https://d1.flnd.buzz/v1.720.mp4", "720p", mirror = "d1.flnd.buzz"),
            VideoUrl("https://d2.flnd.buzz/v1.1080.mp4", "1080p", mirror = "d2.flnd.buzz")
        )

        // Act - filter by quality
        val hd = urls.filter { it.quality == "1080p" }

        // Assert
        assertEquals(2, hd.size)
    }

    @Test
    fun `VideoUrl can be sorted by quality`() {
        // Arrange
        val urls = listOf(
            VideoUrl("https://example.com/v.480.mp4", "480p"),
            VideoUrl("https://example.com/v.1080.mp4", "1080p"),
            VideoUrl("https://example.com/v.720.mp4", "720p")
        )

        // Define quality order
        val qualityOrder = mapOf("1080p" to 3, "720p" to 2, "480p" to 1, "unknown" to 0)

        // Act - sort by quality descending
        val sorted = urls.sortedByDescending { qualityOrder[it.quality] ?: 0 }

        // Assert
        assertEquals("1080p", sorted[0].quality)
        assertEquals("720p", sorted[1].quality)
        assertEquals("480p", sorted[2].quality)
    }

    @Test
    fun `VideoUrl fileSizeMb stores float correctly`() {
        // Test various file sizes
        val testCases = listOf(
            100.0f,
            500.5f,
            1024.75f,
            2048.0f
        )

        testCases.forEach { size ->
            val url = VideoUrl(
                url = "https://example.com/video.mp4",
                quality = "1080p",
                fileSizeMb = size
            )
            assertEquals(size, url.fileSizeMb, "File size should be $size")
        }
    }
}
