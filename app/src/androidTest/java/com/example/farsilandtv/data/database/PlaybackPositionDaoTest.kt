package com.example.farsilandtv.data.database

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.farsilandtv.data.db.PlaybackPosition
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
 * Integration tests for PlaybackPositionDao
 * Tests C1 fix: Database consolidation - PlaybackPosition now in AppDatabase
 *
 * Priority 2: Database integration testing
 *
 * Test Coverage:
 * - C1: Verify PlaybackPosition works in AppDatabase (not FarsilandDatabase)
 * - CRUD operations (savePosition, getPosition, deletePosition)
 * - Query filtering (completed vs incomplete)
 * - Flow-based reactive queries
 * - Composite primary key (contentId + contentType)
 * - Transaction safety
 */
@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
class PlaybackPositionDaoTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var database: AppDatabase
    private lateinit var dao: com.example.farsilandtv.data.db.PlaybackPositionDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        // Create in-memory database for testing (C1 fix verification)
        // This verifies PlaybackPosition is in AppDatabase, not FarsilandDatabase
        database = Room.inMemoryDatabaseBuilder(
            context,
            AppDatabase::class.java
        ).allowMainThreadQueries().build()

        dao = database.playbackPositionDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    // ========== C1 Fix Verification Tests ==========

    @Test
    fun testPlaybackPositionExistsInAppDatabase() = runTest {
        // ARRANGE - This test verifies the C1 consolidation fix
        // PlaybackPosition should be accessible via AppDatabase, not FarsilandDatabase

        // ACT
        val position = PlaybackPosition(
            contentId = 1,
            contentType = "movie",
            contentTitle = "Test Movie",
            contentUrl = "https://farsiland.com/movie/1",
            position = 30000L,
            duration = 120000L,
            quality = "1080p",
            lastWatchedAt = System.currentTimeMillis(),
            isCompleted = false,
            completedAt = null
        )

        dao.savePosition(position)
        val retrieved = dao.getPosition(1, "movie")

        // ASSERT - successful save/retrieve confirms C1 fix
        assertNotNull(retrieved, "PlaybackPosition should exist in AppDatabase")
        assertEquals("Test Movie", retrieved.contentTitle)
    }

    // ========== CRUD Operation Tests ==========

    @Test
    fun testSavePositionInsertsNewRecord() = runTest {
        // ARRANGE
        val position = PlaybackPosition(
            contentId = 100,
            contentType = "movie",
            contentTitle = "New Movie",
            contentUrl = "https://farsiland.com/movie/100",
            position = 45000L,
            duration = 180000L,
            quality = "720p",
            lastWatchedAt = System.currentTimeMillis(),
            isCompleted = false,
            completedAt = null
        )

        // ACT
        dao.savePosition(position)
        val retrieved = dao.getPosition(100, "movie")

        // ASSERT
        assertNotNull(retrieved)
        assertEquals(100, retrieved.contentId)
        assertEquals("movie", retrieved.contentType)
        assertEquals("New Movie", retrieved.contentTitle)
        assertEquals(45000L, retrieved.position)
        assertEquals(180000L, retrieved.duration)
        assertEquals("720p", retrieved.quality)
        assertFalse(retrieved.isCompleted)
    }

    @Test
    fun testSavePositionReplacesExistingRecord() = runTest {
        // ARRANGE - Test REPLACE conflict strategy
        val original = PlaybackPosition(
            contentId = 200,
            contentType = "episode",
            contentTitle = "Episode 1",
            contentUrl = "https://farsiland.com/episode/200",
            position = 10000L,
            duration = 60000L,
            quality = "1080p",
            lastWatchedAt = System.currentTimeMillis(),
            isCompleted = false,
            completedAt = null
        )

        dao.savePosition(original)

        // ACT - Update with same primary key (contentId + contentType)
        val updated = PlaybackPosition(
            contentId = 200,
            contentType = "episode",
            contentTitle = "Episode 1",
            contentUrl = "https://farsiland.com/episode/200",
            position = 50000L, // Updated position
            duration = 60000L,
            quality = "1080p",
            lastWatchedAt = System.currentTimeMillis(),
            isCompleted = true, // Now completed
            completedAt = System.currentTimeMillis()
        )

        dao.savePosition(updated)
        val retrieved = dao.getPosition(200, "episode")

        // ASSERT - should have updated values, not duplicate rows
        assertNotNull(retrieved)
        assertEquals(50000L, retrieved.position, "Position should be updated")
        assertTrue(retrieved.isCompleted, "Should be marked complete")
        assertNotNull(retrieved.completedAt)
    }

    @Test
    fun testGetPositionReturnsNullForNonExistent() = runTest {
        // ARRANGE - Query non-existent content (H4 null safety related)

        // ACT
        val result = dao.getPosition(9999, "movie")

        // ASSERT - should return null, not crash
        assertNull(result, "Non-existent content should return null")
    }

    @Test
    fun testDeletePositionRemovesRecord() = runTest {
        // ARRANGE
        val position = PlaybackPosition(
            contentId = 300,
            contentType = "movie",
            contentTitle = "To Delete",
            contentUrl = "https://farsiland.com/movie/300",
            position = 20000L,
            duration = 90000L,
            quality = "480p",
            lastWatchedAt = System.currentTimeMillis(),
            isCompleted = false,
            completedAt = null
        )

        dao.savePosition(position)

        // ACT
        dao.deletePosition(position)
        val result = dao.getPosition(300, "movie")

        // ASSERT
        assertNull(result, "Deleted position should not be retrievable")
    }

    @Test
    fun testClearAllRemovesAllRecords() = runTest {
        // ARRANGE - Insert multiple records
        val positions = listOf(
            PlaybackPosition(400, "movie", "Movie 1", "url1", 1000, 10000, "1080p", System.currentTimeMillis(), false, null),
            PlaybackPosition(401, "movie", "Movie 2", "url2", 2000, 20000, "720p", System.currentTimeMillis(), false, null),
            PlaybackPosition(402, "episode", "Episode 1", "url3", 3000, 30000, "1080p", System.currentTimeMillis(), false, null)
        )

        positions.forEach { dao.savePosition(it) }

        // ACT
        dao.clearAll()

        // ASSERT - all records should be gone
        assertNull(dao.getPosition(400, "movie"))
        assertNull(dao.getPosition(401, "movie"))
        assertNull(dao.getPosition(402, "episode"))
    }

    // ========== Query Tests ==========

    @Test
    fun testGetRecentPositionsReturnsIncompleteOnly() = runTest {
        // ARRANGE - Mix of complete and incomplete
        val incomplete1 = PlaybackPosition(500, "movie", "In Progress 1", "url1", 5000, 10000, "1080p", System.currentTimeMillis(), false, null)
        val incomplete2 = PlaybackPosition(501, "movie", "In Progress 2", "url2", 3000, 10000, "720p", System.currentTimeMillis() - 1000, false, null)
        val completed = PlaybackPosition(502, "movie", "Completed", "url3", 9500, 10000, "1080p", System.currentTimeMillis(), true, System.currentTimeMillis())

        dao.savePosition(incomplete1)
        dao.savePosition(incomplete2)
        dao.savePosition(completed)

        // ACT
        val recentPositions = dao.getRecentPositions(limit = 10)

        // ASSERT - should only return incomplete
        assertEquals(2, recentPositions.size, "Should return 2 incomplete positions")
        assertTrue(recentPositions.all { !it.isCompleted }, "All should be incomplete")

        // Verify sorted by lastWatchedAt DESC (most recent first)
        assertEquals(500, recentPositions[0].contentId, "Most recent should be first")
        assertEquals(501, recentPositions[1].contentId, "Second most recent should be second")
    }

    @Test
    fun testGetRecentPositionsRespectsLimit() = runTest {
        // ARRANGE - Insert more than limit
        repeat(15) { index ->
            val position = PlaybackPosition(
                contentId = 600 + index,
                contentType = "movie",
                contentTitle = "Movie $index",
                contentUrl = "url$index",
                position = 1000,
                duration = 10000,
                quality = "1080p",
                lastWatchedAt = System.currentTimeMillis() - (index * 1000L),
                isCompleted = false,
                completedAt = null
            )
            dao.savePosition(position)
        }

        // ACT
        val recentPositions = dao.getRecentPositions(limit = 5)

        // ASSERT
        assertEquals(5, recentPositions.size, "Should respect limit of 5")
    }

    @Test
    fun testGetCompletedContentReturnsFlow() = runTest {
        // ARRANGE
        val completed1 = PlaybackPosition(700, "movie", "Complete 1", "url1", 10000, 10000, "1080p", System.currentTimeMillis(), true, System.currentTimeMillis())
        val completed2 = PlaybackPosition(701, "episode", "Complete 2", "url2", 20000, 20000, "720p", System.currentTimeMillis(), true, System.currentTimeMillis())
        val incomplete = PlaybackPosition(702, "movie", "Incomplete", "url3", 5000, 10000, "1080p", System.currentTimeMillis(), false, null)

        dao.savePosition(completed1)
        dao.savePosition(completed2)
        dao.savePosition(incomplete)

        // ACT
        val completedContent = dao.getCompletedContent().first()

        // ASSERT
        assertEquals(2, completedContent.size, "Should return 2 completed items")
        assertTrue(completedContent.all { it.isCompleted }, "All should be completed")
    }

    @Test
    fun testGetCompletedMoviesFiltersCorrectly() = runTest {
        // ARRANGE
        val completedMovie = PlaybackPosition(800, "movie", "Movie", "url1", 10000, 10000, "1080p", System.currentTimeMillis(), true, System.currentTimeMillis())
        val completedEpisode = PlaybackPosition(801, "episode", "Episode", "url2", 20000, 20000, "720p", System.currentTimeMillis(), true, System.currentTimeMillis())

        dao.savePosition(completedMovie)
        dao.savePosition(completedEpisode)

        // ACT
        val completedMovies = dao.getCompletedMovies().first()

        // ASSERT
        assertEquals(1, completedMovies.size, "Should return only completed movies")
        assertEquals("movie", completedMovies[0].contentType)
    }

    @Test
    fun testGetCompletedEpisodesFiltersCorrectly() = runTest {
        // ARRANGE
        val completedMovie = PlaybackPosition(900, "movie", "Movie", "url1", 10000, 10000, "1080p", System.currentTimeMillis(), true, System.currentTimeMillis())
        val completedEpisode = PlaybackPosition(901, "episode", "Episode", "url2", 20000, 20000, "720p", System.currentTimeMillis(), true, System.currentTimeMillis())

        dao.savePosition(completedMovie)
        dao.savePosition(completedEpisode)

        // ACT
        val completedEpisodes = dao.getCompletedEpisodes().first()

        // ASSERT
        assertEquals(1, completedEpisodes.size, "Should return only completed episodes")
        assertEquals("episode", completedEpisodes[0].contentType)
    }

    @Test
    fun testIsCompletedReturnsFlow() = runTest {
        // ARRANGE
        val position = PlaybackPosition(1000, "movie", "Test", "url", 9500, 10000, "1080p", System.currentTimeMillis(), true, System.currentTimeMillis())
        dao.savePosition(position)

        // ACT
        val isCompleted = dao.isCompleted(1000, "movie").first()

        // ASSERT
        assertEquals(true, isCompleted, "Should return true for completed content")
    }

    @Test
    fun testIsCompletedReturnsNullForNonExistent() = runTest {
        // ARRANGE - Query non-existent content

        // ACT
        val isCompleted = dao.isCompleted(9999, "movie").first()

        // ASSERT - Flow returns null for non-existent content (null safety)
        assertNull(isCompleted, "Should return null for non-existent content")
    }

    // ========== Manual Completion Tests ==========

    @Test
    fun testMarkAsCompletedSetsFlag() = runTest {
        // ARRANGE
        val position = PlaybackPosition(1100, "episode", "Test", "url", 5000, 10000, "1080p", System.currentTimeMillis(), false, null)
        dao.savePosition(position)

        // ACT
        val completedAt = System.currentTimeMillis()
        dao.markAsCompleted(1100, "episode", completedAt)

        val updated = dao.getPosition(1100, "episode")

        // ASSERT
        assertNotNull(updated)
        assertTrue(updated.isCompleted, "Should be marked complete")
        assertNotNull(updated.completedAt)
    }

    @Test
    fun testMarkAsIncompleteRemovesFlag() = runTest {
        // ARRANGE
        val position = PlaybackPosition(1200, "movie", "Test", "url", 10000, 10000, "1080p", System.currentTimeMillis(), true, System.currentTimeMillis())
        dao.savePosition(position)

        // ACT
        dao.markAsIncomplete(1200, "movie")

        val updated = dao.getPosition(1200, "movie")

        // ASSERT
        assertNotNull(updated)
        assertFalse(updated.isCompleted, "Should be marked incomplete")
        assertNull(updated.completedAt, "completedAt should be null")
    }

    // ========== Cleanup Tests ==========

    @Test
    fun testDeleteOldCompletedRemovesOldEntries() = runTest {
        // ARRANGE
        val thirtyDaysAgo = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000)
        val twentyDaysAgo = System.currentTimeMillis() - (20L * 24 * 60 * 60 * 1000)

        val oldCompleted = PlaybackPosition(1300, "movie", "Old", "url1", 10000, 10000, "1080p", thirtyDaysAgo, true, thirtyDaysAgo)
        val recentCompleted = PlaybackPosition(1301, "movie", "Recent", "url2", 10000, 10000, "1080p", twentyDaysAgo, true, twentyDaysAgo)

        dao.savePosition(oldCompleted)
        dao.savePosition(recentCompleted)

        // ACT - Delete entries older than 25 days
        val twentyFiveDaysAgo = System.currentTimeMillis() - (25L * 24 * 60 * 60 * 1000)
        dao.deleteOldCompleted(twentyFiveDaysAgo)

        // ASSERT
        assertNull(dao.getPosition(1300, "movie"), "Old entry should be deleted")
        assertNotNull(dao.getPosition(1301, "movie"), "Recent entry should still exist")
    }

    // ========== Composite Primary Key Tests ==========

    @Test
    fun testCompositePrimaryKeyAllowsSameIdDifferentType() = runTest {
        // ARRANGE - Same contentId, different contentType
        val movie = PlaybackPosition(1400, "movie", "Movie", "url1", 5000, 10000, "1080p", System.currentTimeMillis(), false, null)
        val episode = PlaybackPosition(1400, "episode", "Episode", "url2", 3000, 8000, "720p", System.currentTimeMillis(), false, null)

        // ACT
        dao.savePosition(movie)
        dao.savePosition(episode)

        val retrievedMovie = dao.getPosition(1400, "movie")
        val retrievedEpisode = dao.getPosition(1400, "episode")

        // ASSERT - both should exist independently
        assertNotNull(retrievedMovie)
        assertNotNull(retrievedEpisode)
        assertEquals("Movie", retrievedMovie.contentTitle)
        assertEquals("Episode", retrievedEpisode.contentTitle)
    }
}
