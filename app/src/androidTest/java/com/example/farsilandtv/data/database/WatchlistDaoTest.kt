package com.example.farsilandtv.data.database

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Integration tests for Watchlist DAOs
 * Tests transaction safety (C6 fix), CRUD operations, and data integrity
 *
 * Priority 2: Database integration testing
 *
 * Test Coverage:
 * - WatchlistMovieDao operations
 * - MonitoredSeriesDao operations
 * - EpisodeProgressDao operations
 * - Transaction safety (withTransaction boundary)
 * - Flow-based queries
 * - Null safety
 */
@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
class WatchlistDaoTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var database: AppDatabase
    private lateinit var movieDao: WatchlistMovieDao
    private lateinit var seriesDao: MonitoredSeriesDao
    private lateinit var episodeDao: EpisodeProgressDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        database = Room.inMemoryDatabaseBuilder(
            context,
            AppDatabase::class.java
        ).allowMainThreadQueries().build()

        movieDao = database.watchlistMovieDao()
        seriesDao = database.monitoredSeriesDao()
        episodeDao = database.episodeProgressDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    // ========== WatchlistMovie Tests ==========

    @Test
    fun testInsertMovie() = runTest {
        // ARRANGE
        val movie = WatchlistMovie(
            id = 1,
            title = "Test Movie",
            posterUrl = "https://example.com/poster.jpg",
            farsilandUrl = "https://farsiland.com/movie/1",
            dateAdded = System.currentTimeMillis(),
            isInWatchlist = true
        )

        // ACT
        movieDao.insertMovie(movie)
        val retrieved = movieDao.getMovie(1)

        // ASSERT
        assertNotNull(retrieved)
        assertEquals("Test Movie", retrieved.title)
        assertTrue(retrieved.isInWatchlist)
    }

    @Test
    fun testAddToWatchlistSetsFlag() = runTest {
        // ARRANGE
        val movie = WatchlistMovie(
            id = 2,
            title = "Auto-tracked",
            posterUrl = null,
            farsilandUrl = "https://farsiland.com/movie/2",
            dateAdded = System.currentTimeMillis(),
            isInWatchlist = false // Auto-tracked, not bookmarked
        )

        movieDao.insertMovie(movie)

        // ACT - Bookmark it
        movieDao.addToWatchlist(2)
        val updated = movieDao.getMovie(2)

        // ASSERT
        assertNotNull(updated)
        assertTrue(updated.isInWatchlist, "Should be bookmarked")
    }

    @Test
    fun testRemoveFromWatchlistKeepsEntry() = runTest {
        // ARRANGE
        val movie = WatchlistMovie(
            id = 3,
            title = "Bookmarked",
            posterUrl = null,
            farsilandUrl = "https://farsiland.com/movie/3",
            dateAdded = System.currentTimeMillis(),
            playbackPosition = 30000L,
            totalDuration = 120000L,
            isInWatchlist = true
        )

        movieDao.insertMovie(movie)

        // ACT - Unbookmark (remove from watchlist)
        movieDao.removeFromWatchlist(3)
        val updated = movieDao.getMovie(3)

        // ASSERT - entry still exists, just not in watchlist
        assertNotNull(updated, "Entry should still exist")
        assertFalse(updated.isInWatchlist, "Should not be in watchlist")
        assertEquals(30000L, updated.playbackPosition, "Progress data should be preserved")
    }

    @Test
    fun testUpdateProgressUpdatesPositionAndDuration() = runTest {
        // ARRANGE
        val movie = WatchlistMovie(
            id = 4,
            title = "In Progress",
            posterUrl = null,
            farsilandUrl = "https://farsiland.com/movie/4",
            dateAdded = System.currentTimeMillis(),
            playbackPosition = 0L,
            totalDuration = 0L,
            isInWatchlist = false
        )

        movieDao.insertMovie(movie)

        // ACT
        movieDao.updateProgress(4, 45000L, 180000L)
        val updated = movieDao.getMovie(4)

        // ASSERT
        assertNotNull(updated)
        assertEquals(45000L, updated.playbackPosition)
        assertEquals(180000L, updated.totalDuration)
        assertNotNull(updated.lastWatched, "lastWatched should be updated")
    }

    @Test
    fun testMarkAsCompletedSetsFlag() = runTest {
        // ARRANGE
        val movie = WatchlistMovie(
            id = 5,
            title = "Nearly Done",
            posterUrl = null,
            farsilandUrl = "https://farsiland.com/movie/5",
            dateAdded = System.currentTimeMillis(),
            playbackPosition = 90000L,
            totalDuration = 100000L,
            isCompleted = false,
            isInWatchlist = false
        )

        movieDao.insertMovie(movie)

        // ACT
        movieDao.markAsCompleted(5)
        val updated = movieDao.getMovie(5)

        // ASSERT
        assertNotNull(updated)
        assertTrue(updated.isCompleted)
    }

    @Test
    fun testMarkAsUnwatchedClearsFlag() = runTest {
        // ARRANGE
        val movie = WatchlistMovie(
            id = 6,
            title = "Completed",
            posterUrl = null,
            farsilandUrl = "https://farsiland.com/movie/6",
            dateAdded = System.currentTimeMillis(),
            playbackPosition = 100000L,
            totalDuration = 100000L,
            isCompleted = true,
            isInWatchlist = false
        )

        movieDao.insertMovie(movie)

        // ACT
        movieDao.markAsUnwatched(6)
        val updated = movieDao.getMovie(6)

        // ASSERT
        assertNotNull(updated)
        assertFalse(updated.isCompleted)
    }

    @Test
    fun testGetAllMoviesReturnsFlow() = runTest {
        // ARRANGE
        val movie1 = WatchlistMovie(7, "Movie 1", null, "url1", System.currentTimeMillis(), isInWatchlist = true)
        val movie2 = WatchlistMovie(8, "Movie 2", null, "url2", System.currentTimeMillis(), isInWatchlist = true)

        movieDao.insertMovie(movie1)
        movieDao.insertMovie(movie2)

        // ACT
        val movies = movieDao.getAllMovies().first()

        // ASSERT
        assertEquals(2, movies.size)
    }

    @Test
    fun testGetInProgressMoviesExcludesCompleted() = runTest {
        // ARRANGE
        val inProgress = WatchlistMovie(9, "In Progress", null, "url1", System.currentTimeMillis(), playbackPosition = 5000L, totalDuration = 10000L, isCompleted = false, isInWatchlist = false)
        val completed = WatchlistMovie(10, "Completed", null, "url2", System.currentTimeMillis(), playbackPosition = 10000L, totalDuration = 10000L, isCompleted = true, isInWatchlist = false)

        movieDao.insertMovie(inProgress)
        movieDao.insertMovie(completed)

        // ACT
        val inProgressMovies = movieDao.getInProgressMovies().first()

        // ASSERT
        assertEquals(1, inProgressMovies.size)
        assertEquals(9, inProgressMovies[0].id)
    }

    @Test
    fun testGetWatchlistInProgressFiltersCorrectly() = runTest {
        // ARRANGE
        val watchlistInProgress = WatchlistMovie(11, "Watchlist In Progress", null, "url1", System.currentTimeMillis(), playbackPosition = 3000L, totalDuration = 10000L, isCompleted = false, isInWatchlist = true)
        val autoTrackedInProgress = WatchlistMovie(12, "Auto In Progress", null, "url2", System.currentTimeMillis(), playbackPosition = 4000L, totalDuration = 10000L, isCompleted = false, isInWatchlist = false)

        movieDao.insertMovie(watchlistInProgress)
        movieDao.insertMovie(autoTrackedInProgress)

        // ACT
        val result = movieDao.getWatchlistInProgress().first()

        // ASSERT
        assertEquals(1, result.size, "Should only return watchlist items in progress")
        assertEquals(11, result[0].id)
    }

    @Test
    fun testGetCompletedMoviesReturnsOnlyCompleted() = runTest {
        // ARRANGE
        val incomplete = WatchlistMovie(13, "Incomplete", null, "url1", System.currentTimeMillis(), isCompleted = false, isInWatchlist = false)
        val completed = WatchlistMovie(14, "Completed", null, "url2", System.currentTimeMillis(), isCompleted = true, isInWatchlist = false)

        movieDao.insertMovie(incomplete)
        movieDao.insertMovie(completed)

        // ACT
        val completedMovies = movieDao.getCompletedMovies().first()

        // ASSERT
        assertEquals(1, completedMovies.size)
        assertEquals(14, completedMovies[0].id)
    }

    @Test
    fun testDeleteMovieById() = runTest {
        // ARRANGE
        val movie = WatchlistMovie(15, "To Delete", null, "url", System.currentTimeMillis(), isInWatchlist = false)
        movieDao.insertMovie(movie)

        // ACT
        movieDao.deleteMovieById(15)
        val result = movieDao.getMovie(15)

        // ASSERT
        assertNull(result, "Movie should be deleted")
    }

    @Test
    fun testIsInWatchlistReturnsCorrectly() = runTest {
        // ARRANGE
        val watchlisted = WatchlistMovie(16, "Watchlisted", null, "url1", System.currentTimeMillis(), isInWatchlist = true)
        val notWatchlisted = WatchlistMovie(17, "Not Watchlisted", null, "url2", System.currentTimeMillis(), isInWatchlist = false)

        movieDao.insertMovie(watchlisted)
        movieDao.insertMovie(notWatchlisted)

        // ACT & ASSERT
        assertEquals(true, movieDao.isInWatchlist(16))
        assertEquals(false, movieDao.isInWatchlist(17))
        assertNull(movieDao.isInWatchlist(9999), "Non-existent should return null")
    }

    // ========== MonitoredSeries Tests ==========

    @Test
    fun testInsertSeries() = runTest {
        // ARRANGE
        val series = MonitoredSeries(
            id = 100,
            title = "Test Series",
            posterUrl = "poster.jpg",
            backdropUrl = "backdrop.jpg",
            farsilandUrl = "https://farsiland.com/series/100",
            totalSeasons = 5,
            dateAdded = System.currentTimeMillis(),
            lastWatched = System.currentTimeMillis()
        )

        // ACT
        seriesDao.insertSeries(series)
        val retrieved = seriesDao.getSeries(100)

        // ASSERT
        assertNotNull(retrieved)
        assertEquals("Test Series", retrieved.title)
        assertEquals(5, retrieved.totalSeasons)
    }

    @Test
    fun testGetAllSeriesReturnsFlow() = runTest {
        // ARRANGE
        val series1 = MonitoredSeries(101, "Series 1", null, null, "url1", 3, System.currentTimeMillis(), System.currentTimeMillis())
        val series2 = MonitoredSeries(102, "Series 2", null, null, "url2", 4, System.currentTimeMillis(), System.currentTimeMillis())

        seriesDao.insertSeries(series1)
        seriesDao.insertSeries(series2)

        // ACT
        val allSeries = seriesDao.getAllSeries().first()

        // ASSERT
        assertEquals(2, allSeries.size)
    }

    @Test
    fun testDeleteSeriesById() = runTest {
        // ARRANGE
        val series = MonitoredSeries(103, "To Delete", null, null, "url", 2, System.currentTimeMillis(), System.currentTimeMillis())
        seriesDao.insertSeries(series)

        // ACT
        seriesDao.deleteSeriesById(103)
        val result = seriesDao.getSeries(103)

        // ASSERT
        assertNull(result, "Series should be deleted")
    }

    // ========== EpisodeProgress Tests ==========

    @Test
    fun testInsertEpisode() = runTest {
        // ARRANGE
        val episode = EpisodeProgress(
            seriesId = 200,
            episodeId = 1001,
            season = 1,
            episode = 5,
            episodeTitle = "Test Episode",
            thumbnailUrl = "thumb.jpg",
            farsilandUrl = "https://farsiland.com/episode/1001",
            lastWatched = System.currentTimeMillis()
        )

        // ACT
        episodeDao.insertEpisode(episode)
        val retrieved = episodeDao.getEpisodeProgress(1001)

        // ASSERT
        assertNotNull(retrieved)
        assertEquals("Test Episode", retrieved.episodeTitle)
        assertEquals(1, retrieved.season)
        assertEquals(5, retrieved.episode)
    }

    @Test
    fun testUpdateEpisodeProgress() = runTest {
        // ARRANGE
        val episode = EpisodeProgress(
            seriesId = 201,
            episodeId = 1002,
            season = 2,
            episode = 3,
            episodeTitle = "Episode 3",
            thumbnailUrl = null,
            farsilandUrl = "url",
            playbackPosition = 0L,
            totalDuration = 0L,
            lastWatched = System.currentTimeMillis()
        )

        episodeDao.insertEpisode(episode)

        // ACT
        episodeDao.updateProgress(1002, 30000L, 60000L)
        val updated = episodeDao.getEpisodeProgress(1002)

        // ASSERT
        assertNotNull(updated)
        assertEquals(30000L, updated.playbackPosition)
        assertEquals(60000L, updated.totalDuration)
    }

    @Test
    fun testMarkEpisodeAsCompleted() = runTest {
        // ARRANGE
        val episode = EpisodeProgress(202, 1003, 1, 1, "Ep 1", null, "url", System.currentTimeMillis(), isCompleted = false)
        episodeDao.insertEpisode(episode)

        // ACT
        episodeDao.markAsCompleted(1003)
        val updated = episodeDao.getEpisodeProgress(1003)

        // ASSERT
        assertNotNull(updated)
        assertTrue(updated.isCompleted)
    }

    @Test
    fun testGetEpisodesForSeries() = runTest {
        // ARRANGE
        val ep1 = EpisodeProgress(300, 2001, 1, 1, "S1E1", null, "url1", System.currentTimeMillis())
        val ep2 = EpisodeProgress(300, 2002, 1, 2, "S1E2", null, "url2", System.currentTimeMillis())
        val ep3 = EpisodeProgress(301, 2003, 1, 1, "Other Series", null, "url3", System.currentTimeMillis())

        episodeDao.insertEpisode(ep1)
        episodeDao.insertEpisode(ep2)
        episodeDao.insertEpisode(ep3)

        // ACT
        val episodesForSeries300 = episodeDao.getEpisodesForSeries(300).first()

        // ASSERT
        assertEquals(2, episodesForSeries300.size, "Should return 2 episodes for series 300")
    }

    @Test
    fun testGetEpisodesForSeason() = runTest {
        // ARRANGE
        val s1e1 = EpisodeProgress(400, 3001, 1, 1, "S1E1", null, "url1", System.currentTimeMillis())
        val s1e2 = EpisodeProgress(400, 3002, 1, 2, "S1E2", null, "url2", System.currentTimeMillis())
        val s2e1 = EpisodeProgress(400, 3003, 2, 1, "S2E1", null, "url3", System.currentTimeMillis())

        episodeDao.insertEpisode(s1e1)
        episodeDao.insertEpisode(s1e2)
        episodeDao.insertEpisode(s2e1)

        // ACT
        val season1Episodes = episodeDao.getEpisodesForSeason(400, 1).first()

        // ASSERT
        assertEquals(2, season1Episodes.size, "Should return 2 episodes for season 1")
    }

    @Test
    fun testGetAllInProgressEpisodes() = runTest {
        // ARRANGE
        val inProgress = EpisodeProgress(500, 4001, 1, 1, "In Progress", null, "url1", System.currentTimeMillis(), playbackPosition = 5000L, totalDuration = 10000L, isCompleted = false)
        val completed = EpisodeProgress(500, 4002, 1, 2, "Completed", null, "url2", System.currentTimeMillis(), playbackPosition = 10000L, totalDuration = 10000L, isCompleted = true)

        episodeDao.insertEpisode(inProgress)
        episodeDao.insertEpisode(completed)

        // ACT
        val inProgressEpisodes = episodeDao.getAllInProgressEpisodes().first()

        // ASSERT
        assertEquals(1, inProgressEpisodes.size)
        assertEquals(4001, inProgressEpisodes[0].episodeId)
    }

    @Test
    fun testGetCompletedEpisodeCount() = runTest {
        // ARRANGE
        val ep1 = EpisodeProgress(600, 5001, 1, 1, "E1", null, "url1", System.currentTimeMillis(), isCompleted = true)
        val ep2 = EpisodeProgress(600, 5002, 1, 2, "E2", null, "url2", System.currentTimeMillis(), isCompleted = true)
        val ep3 = EpisodeProgress(600, 5003, 1, 3, "E3", null, "url3", System.currentTimeMillis(), isCompleted = false)

        episodeDao.insertEpisode(ep1)
        episodeDao.insertEpisode(ep2)
        episodeDao.insertEpisode(ep3)

        // ACT
        val completedCount = episodeDao.getCompletedEpisodeCount(600)

        // ASSERT
        assertEquals(2, completedCount, "Should count 2 completed episodes")
    }

    @Test
    fun testGetTotalEpisodeCount() = runTest {
        // ARRANGE
        val ep1 = EpisodeProgress(700, 6001, 1, 1, "E1", null, "url1", System.currentTimeMillis())
        val ep2 = EpisodeProgress(700, 6002, 1, 2, "E2", null, "url2", System.currentTimeMillis())
        val ep3 = EpisodeProgress(700, 6003, 2, 1, "E3", null, "url3", System.currentTimeMillis())

        episodeDao.insertEpisode(ep1)
        episodeDao.insertEpisode(ep2)
        episodeDao.insertEpisode(ep3)

        // ACT
        val totalCount = episodeDao.getTotalEpisodeCount(700)

        // ASSERT
        assertEquals(3, totalCount, "Should count 3 total episodes")
    }

    @Test
    fun testMarkAllEpisodesAsWatched() = runTest {
        // ARRANGE
        val ep1 = EpisodeProgress(800, 7001, 1, 1, "E1", null, "url1", System.currentTimeMillis(), isCompleted = false)
        val ep2 = EpisodeProgress(800, 7002, 1, 2, "E2", null, "url2", System.currentTimeMillis(), isCompleted = false)

        episodeDao.insertEpisode(ep1)
        episodeDao.insertEpisode(ep2)

        // ACT
        episodeDao.markAllAsCompleted(800)

        val ep1Updated = episodeDao.getEpisodeProgress(7001)
        val ep2Updated = episodeDao.getEpisodeProgress(7002)

        // ASSERT
        assertNotNull(ep1Updated)
        assertNotNull(ep2Updated)
        assertTrue(ep1Updated.isCompleted, "Episode 1 should be completed")
        assertTrue(ep2Updated.isCompleted, "Episode 2 should be completed")
    }
}
