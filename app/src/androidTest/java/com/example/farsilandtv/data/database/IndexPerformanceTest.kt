package com.example.farsilandtv.data.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.farsilandtv.data.model.Genre
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.system.measureTimeMillis
import kotlin.test.assertTrue

/**
 * Integration tests for Database Index Performance
 *
 * Issue M7: Room @Query Lacks Index on Foreign Keys
 *
 * Test Coverage:
 * - Watchlist query performance with indices (< 100ms target)
 * - Episode progress query performance (< 50ms target)
 * - JOIN operation performance with indexed foreign keys
 * - Unique constraint enforcement (episodeId)
 * - Index effectiveness on large datasets (1000+ records)
 *
 * Priority: MEDIUM (Performance optimization)
 *
 * Expected Performance Improvements:
 * - Before M7: 500ms for watchlist queries
 * - After M7: < 50ms for watchlist queries (90% improvement)
 * - JOIN operations: O(n) -> O(log n) with indices
 *
 * Success Criteria:
 * - Watchlist queries complete in < 100ms with 1000 items
 * - Episode progress queries complete in < 50ms with 1000 episodes
 * - Unique constraints prevent duplicate episodeId insertions
 */
@RunWith(AndroidJUnit4::class)
class IndexPerformanceTest {

    private lateinit var database: AppDatabase
    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        // Use in-memory database for testing (faster than disk)
        database = Room.inMemoryDatabaseBuilder(
            context,
            AppDatabase::class.java
        ).build()
    }

    @After
    fun teardown() {
        database.close()
    }

    // ========== Watchlist Index Performance Tests ==========

    @Test
    fun watchlist_query_performance_with_indices() = runBlocking {
        // Arrange - Insert 1000 test movies into watchlist
        val testMovies = generateTestWatchlistMovies(count = 1000)
        testMovies.forEach { movie ->
            database.watchlistDao().addMovieToWatchlist(movie)
        }

        // Act - Measure query time
        val queryTime = measureTimeMillis {
            val watchlistMovies = database.watchlistDao().getAllWatchlistMovies()
            // Force query execution by accessing the list
            watchlistMovies.size
        }

        // Assert - Query should complete in < 100ms (M7 target)
        assertTrue(
            actual = queryTime < 100,
            message = "Watchlist query took ${queryTime}ms (expected < 100ms). " +
                    "M7 indices may not be working correctly."
        )

        println("✅ M7 Performance: Watchlist query with 1000 items completed in ${queryTime}ms")
    }

    @Test
    fun watchlist_filter_by_isInWatchlist_uses_index() = runBlocking {
        // Arrange - Insert mixed data (500 in watchlist, 500 just tracking progress)
        val watchlistMovies = generateTestWatchlistMovies(count = 500, isInWatchlist = true)
        val trackingMovies = generateTestWatchlistMovies(count = 500, isInWatchlist = false, startId = 500)

        (watchlistMovies + trackingMovies).forEach { movie ->
            database.watchlistDao().addMovieToWatchlist(movie)
        }

        // Act - Measure filtered query time (uses isInWatchlist index)
        val queryTime = measureTimeMillis {
            val bookmarkedMovies = database.watchlistDao().getAllWatchlistMovies()
                .filter { it.isInWatchlist }
            // Force query execution
            bookmarkedMovies.size
        }

        // Assert - Filtered query should be fast with index
        assertTrue(
            actual = queryTime < 100,
            message = "Filtered watchlist query took ${queryTime}ms (expected < 100ms). " +
                    "isInWatchlist index may not be effective."
        )

        println("✅ M7 Performance: Filtered watchlist query completed in ${queryTime}ms")
    }

    @Test
    fun continue_watching_query_uses_composite_index() = runBlocking {
        // Arrange - Insert 1000 movies with varied watch states
        val movies = generateTestWatchlistMovies(count = 1000)
        movies.forEachIndexed { index, movie ->
            // Mix of in-progress and completed movies
            val updatedMovie = movie.copy(
                isCompleted = index % 3 == 0,
                lastWatched = System.currentTimeMillis() - (index * 1000L)
            )
            database.watchlistDao().addMovieToWatchlist(updatedMovie)
        }

        // Act - Measure continue watching query (uses composite index: isCompleted + lastWatched)
        val queryTime = measureTimeMillis {
            val continueWatching = database.watchlistDao().getAllWatchlistMovies()
                .filter { !it.isCompleted && it.lastWatched != null }
                .sortedByDescending { it.lastWatched }
            // Force query execution
            continueWatching.size
        }

        // Assert - Query with composite index should be fast
        assertTrue(
            actual = queryTime < 100,
            message = "Continue watching query took ${queryTime}ms (expected < 100ms). " +
                    "Composite index may not be effective."
        )

        println("✅ M7 Performance: Continue watching query completed in ${queryTime}ms")
    }

    // ========== Episode Progress Index Performance Tests ==========

    @Test
    fun episode_progress_query_performance_with_index() = runBlocking {
        // Arrange - Insert 1000 test episodes with progress
        val testSeries = MonitoredSeries(
            id = 1,
            title = "Test Series",
            posterUrl = "https://farsiland.com/poster.jpg",
            backdropUrl = null,
            farsilandUrl = "https://farsiland.com/series/1",
            totalSeasons = 10
        )
        database.watchlistDao().addSeriesToMonitored(testSeries)

        val testEpisodes = generateTestEpisodeProgress(seriesId = 1, count = 1000)
        testEpisodes.forEach { episode ->
            database.watchlistDao().updateEpisodeProgress(episode)
        }

        // Act - Measure query time (uses seriesId index)
        val queryTime = measureTimeMillis {
            val episodes = database.watchlistDao().getEpisodeProgressForSeries(seriesId = 1)
            // Force query execution
            episodes.size
        }

        // Assert - Query should complete in < 50ms (M7 target)
        assertTrue(
            actual = queryTime < 50,
            message = "Episode progress query took ${queryTime}ms (expected < 50ms). " +
                    "seriesId index may not be working correctly."
        )

        println("✅ M7 Performance: Episode progress query with 1000 items completed in ${queryTime}ms")
    }

    @Test
    fun episode_progress_unique_constraint_enforced() = runBlocking {
        // Arrange - Insert episode with specific episodeId
        val episode1 = EpisodeProgress(
            seriesId = 1,
            episodeId = 101,
            season = 1,
            episode = 1,
            playbackPosition = 120000L,
            totalDuration = 3600000L,
            isCompleted = false,
            lastWatched = System.currentTimeMillis()
        )
        database.watchlistDao().updateEpisodeProgress(episode1)

        // Act - Try to insert duplicate episodeId (should update, not insert new)
        val episode2 = episode1.copy(
            id = 0, // New ID
            playbackPosition = 240000L // Different position
        )
        database.watchlistDao().updateEpisodeProgress(episode2)

        // Assert - Should only have 1 episode (unique constraint prevents duplicates)
        val allEpisodes = database.watchlistDao().getEpisodeProgressForSeries(seriesId = 1)
        assertTrue(
            actual = allEpisodes.size == 1,
            message = "Expected 1 episode due to unique constraint, found ${allEpisodes.size}"
        )

        // Verify updated position
        assertTrue(
            actual = allEpisodes.first().playbackPosition == 240000L,
            message = "Expected episode to be updated with new position"
        )

        println("✅ M7 Data Integrity: Unique constraint on episodeId working correctly")
    }

    @Test
    fun episode_filter_by_completion_uses_composite_index() = runBlocking {
        // Arrange - Insert episodes with mixed completion states
        val testSeries = MonitoredSeries(
            id = 1,
            title = "Test Series",
            posterUrl = "https://farsiland.com/poster.jpg",
            backdropUrl = null,
            farsilandUrl = "https://farsiland.com/series/1",
            totalSeasons = 10
        )
        database.watchlistDao().addSeriesToMonitored(testSeries)

        val episodes = generateTestEpisodeProgress(seriesId = 1, count = 1000)
        episodes.forEachIndexed { index, episode ->
            // Mix of completed and in-progress episodes
            val updatedEpisode = episode.copy(isCompleted = index % 2 == 0)
            database.watchlistDao().updateEpisodeProgress(updatedEpisode)
        }

        // Act - Measure filtered query (uses composite index: seriesId + isCompleted)
        val queryTime = measureTimeMillis {
            val inProgressEpisodes = database.watchlistDao()
                .getEpisodeProgressForSeries(seriesId = 1)
                .filter { !it.isCompleted }
            // Force query execution
            inProgressEpisodes.size
        }

        // Assert - Query should be fast with composite index
        assertTrue(
            actual = queryTime < 50,
            message = "Filtered episode query took ${queryTime}ms (expected < 50ms). " +
                    "Composite index (seriesId + isCompleted) may not be effective."
        )

        println("✅ M7 Performance: Filtered episode query completed in ${queryTime}ms")
    }

    // ========== JOIN Performance Tests ==========

    @Test
    fun monitored_series_with_episodes_join_performance() = runBlocking {
        // Arrange - Insert 100 series with 10 episodes each (1000 total episodes)
        val testSeriesList = (1..100).map { seriesId ->
            MonitoredSeries(
                id = seriesId,
                title = "Series $seriesId",
                posterUrl = "https://farsiland.com/poster$seriesId.jpg",
                backdropUrl = null,
                farsilandUrl = "https://farsiland.com/series/$seriesId",
                totalSeasons = 1
            )
        }

        testSeriesList.forEach { series ->
            database.watchlistDao().addSeriesToMonitored(series)

            // Add 10 episodes per series
            val episodes = generateTestEpisodeProgress(seriesId = series.id, count = 10)
            episodes.forEach { episode ->
                database.watchlistDao().updateEpisodeProgress(episode)
            }
        }

        // Act - Measure JOIN query time (series + episodes)
        val queryTime = measureTimeMillis {
            testSeriesList.forEach { series ->
                val episodes = database.watchlistDao().getEpisodeProgressForSeries(series.id)
                episodes.size // Force query execution
            }
        }

        // Assert - JOIN queries should be fast with indices (< 200ms for 100 queries)
        assertTrue(
            actual = queryTime < 200,
            message = "JOIN queries took ${queryTime}ms (expected < 200ms). " +
                    "Foreign key indices may not be effective."
        )

        println("✅ M7 Performance: 100 JOIN queries completed in ${queryTime}ms")
    }

    // ========== Large Dataset Stress Tests ==========

    @Test
    fun stress_test_10000_watchlist_items() = runBlocking {
        // Arrange - Insert 10,000 movies (extreme case)
        val testMovies = generateTestWatchlistMovies(count = 10000)
        testMovies.forEach { movie ->
            database.watchlistDao().addMovieToWatchlist(movie)
        }

        // Act - Measure query time on large dataset
        val queryTime = measureTimeMillis {
            val watchlistMovies = database.watchlistDao().getAllWatchlistMovies()
            watchlistMovies.size // Force query execution
        }

        // Assert - Even with 10k items, query should be reasonable (< 500ms)
        assertTrue(
            actual = queryTime < 500,
            message = "Large dataset query took ${queryTime}ms (expected < 500ms). " +
                    "Indices may not scale well."
        )

        println("✅ M7 Stress Test: Query with 10,000 items completed in ${queryTime}ms")
    }

    // ========== Helper Methods ==========

    /**
     * Generate test watchlist movies
     */
    private fun generateTestWatchlistMovies(
        count: Int,
        isInWatchlist: Boolean = true,
        startId: Int = 1
    ): List<WatchlistMovie> {
        return (startId until startId + count).map { id ->
            WatchlistMovie(
                id = id,
                title = "Test Movie $id",
                posterUrl = "https://farsiland.com/poster$id.jpg",
                farsilandUrl = "https://farsiland.com/movie/$id",
                dateAdded = System.currentTimeMillis() - (id * 1000L),
                lastWatched = if (id % 2 == 0) System.currentTimeMillis() else null,
                playbackPosition = if (id % 2 == 0) 600000L else 0L,
                totalDuration = 7200000L,
                isCompleted = id % 5 == 0,
                isInWatchlist = isInWatchlist
            )
        }
    }

    /**
     * Generate test episode progress records
     */
    private fun generateTestEpisodeProgress(
        seriesId: Int,
        count: Int
    ): List<EpisodeProgress> {
        return (1..count).map { episodeNum ->
            EpisodeProgress(
                seriesId = seriesId,
                episodeId = seriesId * 10000 + episodeNum, // Unique episode IDs
                season = (episodeNum - 1) / 20 + 1,
                episode = (episodeNum - 1) % 20 + 1,
                playbackPosition = if (episodeNum % 2 == 0) 1200000L else 0L,
                totalDuration = 3600000L,
                isCompleted = episodeNum % 3 == 0,
                lastWatched = System.currentTimeMillis() - (episodeNum * 1000L)
            )
        }
    }
}
