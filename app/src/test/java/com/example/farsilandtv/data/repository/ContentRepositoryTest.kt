package com.example.farsilandtv.data.repository

import android.content.Context
import com.example.farsilandtv.data.api.WordPressApiService
import com.example.farsilandtv.data.database.DatabaseSource
import com.example.farsilandtv.data.models.Movie
import com.example.farsilandtv.data.models.Series
import com.example.farsilandtv.data.models.Episode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

/**
 * Unit tests for ContentRepository
 *
 * Tests focus on:
 * - Data model creation and manipulation
 * - Content filtering and searching
 * - DatabaseSource enumeration
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class ContentRepositoryTest {

    private lateinit var context: Context
    private lateinit var wordPressApi: WordPressApiService

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        context = mock()
        wordPressApi = mock()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ============================================================================
    // Movie Model Tests
    // ============================================================================

    @Test
    fun `Movie data class creates correct instance`() {
        // Arrange & Act
        val movie = Movie(
            id = 1,
            title = "Test Movie",
            description = "A test movie",
            posterUrl = "https://example.com/poster.jpg",
            farsilandUrl = "https://farsiland.com/movies/test-movie/",
            year = 2024,
            rating = 8.5f,
            genres = listOf("Action", "Drama")
        )

        // Assert
        assertEquals(1, movie.id)
        assertEquals("Test Movie", movie.title)
        assertEquals("A test movie", movie.description)
        assertEquals(2024, movie.year)
        assertEquals(8.5f, movie.rating)
        assertEquals(2, movie.genres.size)
    }

    @Test
    fun `Movie copy creates independent instance`() {
        // Arrange
        val original = Movie(
            id = 1,
            title = "Original",
            farsilandUrl = "https://example.com/",
            year = 2020,
            rating = 7.0f,
            genres = listOf("Comedy")
        )

        // Act
        val copy = original.copy(title = "Copy", year = 2024)

        // Assert
        assertEquals("Original", original.title)
        assertEquals("Copy", copy.title)
        assertEquals(2020, original.year)
        assertEquals(2024, copy.year)
        assertEquals(original.id, copy.id)
    }

    @Test
    fun `Movie handles null optional fields`() {
        // Arrange & Act
        val movie = Movie(
            id = 1,
            title = "Minimal Movie",
            farsilandUrl = "https://example.com/",
            posterUrl = null,
            year = null,
            rating = null,
            genres = emptyList()
        )

        // Assert
        assertNotNull(movie)
        assertEquals(null, movie.posterUrl)
        assertEquals(null, movie.year)
        assertEquals(null, movie.rating)
        assertTrue(movie.genres.isEmpty())
    }

    // ============================================================================
    // Series Model Tests
    // ============================================================================

    @Test
    fun `Series data class creates correct instance`() {
        // Arrange & Act
        val series = Series(
            id = 1,
            title = "Test Series",
            description = "A test series",
            posterUrl = "https://example.com/poster.jpg",
            backdropUrl = "https://example.com/backdrop.jpg",
            farsilandUrl = "https://farsiland.com/series/test-series/",
            year = 2024,
            rating = 9.0f,
            genres = listOf("Thriller", "Mystery"),
            totalSeasons = 3,
            totalEpisodes = 30
        )

        // Assert
        assertEquals(1, series.id)
        assertEquals("Test Series", series.title)
        assertEquals(3, series.totalSeasons)
        assertEquals(30, series.totalEpisodes)
    }

    @Test
    fun `Series default season and episode counts are 0`() {
        // Arrange & Act
        val series = Series(
            id = 1,
            title = "New Series",
            farsilandUrl = "https://example.com/"
        )

        // Assert
        assertNotNull(series)
        assertEquals(0, series.totalSeasons)
        assertEquals(0, series.totalEpisodes)
    }

    // ============================================================================
    // Episode Model Tests
    // ============================================================================

    @Test
    fun `Episode data class creates correct instance`() {
        // Arrange & Act
        val episode = Episode(
            id = 1,
            seriesId = 100,
            seriesTitle = "Test Series",
            title = "Pilot",
            thumbnailUrl = "https://example.com/thumb.jpg",
            farsilandUrl = "https://farsiland.com/episode/test/s01e01/",
            season = 1,
            episode = 1
        )

        // Assert
        assertEquals(1, episode.id)
        assertEquals("Pilot", episode.title)
        assertEquals(1, episode.season)
        assertEquals(1, episode.episode)
        assertEquals(100, episode.seriesId)
        assertEquals("Test Series", episode.seriesTitle)
    }

    @Test
    fun `Episode handles null series reference`() {
        // Arrange & Act
        val episode = Episode(
            id = 1,
            title = "Standalone Episode",
            farsilandUrl = "https://example.com/",
            season = 1,
            episode = 1
        )

        // Assert
        assertNotNull(episode)
        assertEquals(null, episode.seriesId)
        assertEquals(null, episode.seriesTitle)
    }

    // ============================================================================
    // DatabaseSource Tests
    // ============================================================================

    @Test
    fun `DatabaseSource FARSILAND has correct URL pattern`() {
        // Assert - urlPattern is SQL LIKE pattern with wildcards
        assertEquals("%farsiland.com%", DatabaseSource.FARSILAND.urlPattern)
    }

    @Test
    fun `DatabaseSource FARSIPLEX has correct URL pattern`() {
        // Assert - urlPattern is SQL LIKE pattern with wildcards
        assertEquals("%farsiplex.com%", DatabaseSource.FARSIPLEX.urlPattern)
    }

    @Test
    fun `DatabaseSource NAMAKADE has correct URL pattern`() {
        // Assert - urlPattern is SQL LIKE pattern with wildcards
        assertEquals("%namakade%", DatabaseSource.NAMAKADE.urlPattern)
    }

    @Test
    fun `DatabaseSource IMVBOX has correct URL pattern`() {
        // Assert - urlPattern is SQL LIKE pattern with wildcards
        assertEquals("%imvbox%", DatabaseSource.IMVBOX.urlPattern)
    }

    @Test
    fun `DatabaseSource enum has expected values`() {
        // Assert
        val sources = DatabaseSource.values()
        assertTrue(sources.size >= 4, "Should have at least 4 database sources")
        assertTrue(sources.any { it.name == "FARSILAND" })
        assertTrue(sources.any { it.name == "FARSIPLEX" })
        assertTrue(sources.any { it.name == "NAMAKADE" })
        assertTrue(sources.any { it.name == "IMVBOX" })
    }

    // ============================================================================
    // Content Filtering Tests
    // ============================================================================

    @Test
    fun `Movies can be filtered by genre`() {
        // Arrange
        val movies = listOf(
            Movie(1, "Action Movie", farsilandUrl = "url1", genres = listOf("Action")),
            Movie(2, "Comedy Movie", farsilandUrl = "url2", genres = listOf("Comedy")),
            Movie(3, "Action Comedy", farsilandUrl = "url3", genres = listOf("Action", "Comedy")),
            Movie(4, "Drama Movie", farsilandUrl = "url4", genres = listOf("Drama"))
        )

        // Act
        val actionMovies = movies.filter { "Action" in it.genres }

        // Assert
        assertEquals(2, actionMovies.size)
        assertTrue(actionMovies.all { "Action" in it.genres })
    }

    @Test
    fun `Movies can be filtered by year`() {
        // Arrange
        val movies = listOf(
            Movie(1, "Movie 2020", farsilandUrl = "url1", year = 2020),
            Movie(2, "Movie 2021", farsilandUrl = "url2", year = 2021),
            Movie(3, "Movie 2022", farsilandUrl = "url3", year = 2022),
            Movie(4, "Movie 2023", farsilandUrl = "url4", year = 2023)
        )

        // Act
        val recentMovies = movies.filter { (it.year ?: 0) >= 2022 }

        // Assert
        assertEquals(2, recentMovies.size)
    }

    @Test
    fun `Movies can be filtered by minimum rating`() {
        // Arrange
        val movies = listOf(
            Movie(1, "Low Rated", farsilandUrl = "url1", rating = 5.0f),
            Movie(2, "Medium Rated", farsilandUrl = "url2", rating = 7.0f),
            Movie(3, "High Rated", farsilandUrl = "url3", rating = 8.5f),
            Movie(4, "No Rating", farsilandUrl = "url4", rating = null)
        )

        // Act
        val highRatedMovies = movies.filter { (it.rating ?: 0f) >= 7.0f }

        // Assert
        assertEquals(2, highRatedMovies.size)
    }

    // ============================================================================
    // Content Sorting Tests
    // ============================================================================

    @Test
    fun `Movies can be sorted by year descending`() {
        // Arrange
        val movies = listOf(
            Movie(1, "Old", farsilandUrl = "url1", year = 2015),
            Movie(2, "New", farsilandUrl = "url2", year = 2024),
            Movie(3, "Mid", farsilandUrl = "url3", year = 2020)
        )

        // Act
        val sorted = movies.sortedByDescending { it.year }

        // Assert
        assertEquals(2024, sorted[0].year)
        assertEquals(2020, sorted[1].year)
        assertEquals(2015, sorted[2].year)
    }

    @Test
    fun `Movies can be sorted by rating descending`() {
        // Arrange
        val movies = listOf(
            Movie(1, "Low", farsilandUrl = "url1", rating = 5.0f),
            Movie(2, "High", farsilandUrl = "url2", rating = 9.0f),
            Movie(3, "Medium", farsilandUrl = "url3", rating = 7.0f)
        )

        // Act
        val sorted = movies.sortedByDescending { it.rating }

        // Assert
        assertEquals(9.0f, sorted[0].rating)
        assertEquals(7.0f, sorted[1].rating)
        assertEquals(5.0f, sorted[2].rating)
    }

    @Test
    fun `Episodes can be sorted by season and episode number`() {
        // Arrange
        val episodes = listOf(
            Episode(1, title = "S2E5", farsilandUrl = "url1", season = 2, episode = 5),
            Episode(2, title = "S1E1", farsilandUrl = "url2", season = 1, episode = 1),
            Episode(3, title = "S1E10", farsilandUrl = "url3", season = 1, episode = 10),
            Episode(4, title = "S2E1", farsilandUrl = "url4", season = 2, episode = 1)
        )

        // Act
        val sorted = episodes.sortedWith(compareBy({ it.season }, { it.episode }))

        // Assert
        assertEquals("S1E1", sorted[0].title)
        assertEquals("S1E10", sorted[1].title)
        assertEquals("S2E1", sorted[2].title)
        assertEquals("S2E5", sorted[3].title)
    }

    // ============================================================================
    // Search Tests
    // ============================================================================

    @Test
    fun `Movies can be searched by title`() {
        // Arrange
        val movies = listOf(
            Movie(1, "The Matrix", farsilandUrl = "url1"),
            Movie(2, "Inception", farsilandUrl = "url2"),
            Movie(3, "The Matrix Reloaded", farsilandUrl = "url3")
        )

        // Act
        val searchResults = movies.filter {
            it.title.contains("Matrix", ignoreCase = true)
        }

        // Assert
        assertEquals(2, searchResults.size)
    }

    @Test
    fun `Series can be searched by title case-insensitive`() {
        // Arrange
        val seriesList = listOf(
            Series(1, "Breaking Bad", farsilandUrl = "url1"),
            Series(2, "Game of Thrones", farsilandUrl = "url2"),
            Series(3, "Better Call Saul", farsilandUrl = "url3")
        )

        // Act
        val searchResults = seriesList.filter {
            it.title.contains("breaking", ignoreCase = true)
        }

        // Assert
        assertEquals(1, searchResults.size)
        assertEquals("Breaking Bad", searchResults[0].title)
    }

    // ============================================================================
    // Edge Cases
    // ============================================================================

    @Test
    fun `Empty movie list operations don't crash`() {
        // Arrange
        val movies = emptyList<Movie>()

        // Act & Assert - should not throw
        assertEquals(0, movies.filter { "Action" in it.genres }.size)
        assertEquals(0, movies.sortedByDescending { it.rating }.size)
        assertTrue(movies.isEmpty())
    }

    @Test
    fun `Movie with empty genres list is valid`() {
        // Arrange & Act
        val movie = Movie(
            id = 1,
            title = "No Genre Movie",
            farsilandUrl = "https://example.com/",
            year = 2024,
            rating = 7.0f,
            genres = emptyList()
        )

        // Assert
        assertTrue(movie.genres.isEmpty())
        assertFalse("Action" in movie.genres)
    }

    @Test
    fun `Episode season and episode numbers are preserved`() {
        // Test various edge cases for episode numbering
        val testCases = listOf(
            Triple(1, 1, "S01E01"),
            Triple(1, 10, "S01E10"),
            Triple(10, 1, "S10E01"),
            Triple(10, 99, "S10E99")
        )

        testCases.forEach { (seasonNum, episodeNum, expected) ->
            val episode = Episode(
                id = 1,
                title = expected,
                farsilandUrl = "url",
                season = seasonNum,
                episode = episodeNum
            )

            assertEquals(seasonNum, episode.season, "Season should be $seasonNum")
            assertEquals(episodeNum, episode.episode, "Episode should be $episodeNum")
        }
    }
}
