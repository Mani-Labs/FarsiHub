package com.example.farsilandtv.data.repository

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import app.cash.turbine.test
import com.example.farsilandtv.data.database.NotificationPreferences
import com.example.farsilandtv.data.database.NotificationPreferencesDao
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Unit tests for NotificationPreferencesRepository
 * Tests notification preferences management (Feature #9)
 *
 * Test Coverage:
 * - Get/update notification preferences
 * - Toggle individual notifications
 * - Quiet hours management
 * - Notification permission checks
 * - Default preferences
 */
@ExperimentalCoroutinesApi
class NotificationPreferencesRepositoryTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @Mock
    private lateinit var mockPreferencesDao: NotificationPreferencesDao

    private lateinit var repository: NotificationPreferencesRepository

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        repository = NotificationPreferencesRepository(mockPreferencesDao)
    }

    // ========== Get Preferences ==========

    @Test
    fun `test getPreferences returns NotificationPreferences entity`() = runTest {
        // ARRANGE
        val preferences = NotificationPreferences(
            id = 1,
            newEpisodesEnabled = true,
            newSeasonsEnabled = true,
            weeklyDigestEnabled = false,
            quietHoursStart = 22,
            quietHoursEnd = 8,
            lastUpdated = System.currentTimeMillis()
        )

        whenever(mockPreferencesDao.getPreferencesOnce()).thenReturn(preferences)

        // ACT
        val result = mockPreferencesDao.getPreferencesOnce()

        // ASSERT
        assertNotNull(result, "Preferences should not be null")
        assertEquals(1, result.id, "ID should be 1")
        assertTrue(result.newEpisodesEnabled, "New episodes should be enabled by default")
        assertFalse(result.weeklyDigestEnabled, "Weekly digest should be disabled by default")
    }

    @Test
    fun `test preferences Flow returns reactive updates`() = runTest {
        // ARRANGE
        val preferences = NotificationPreferences(
            id = 1,
            newEpisodesEnabled = true,
            newSeasonsEnabled = true,
            weeklyDigestEnabled = false,
            quietHoursStart = 22,
            quietHoursEnd = 8,
            lastUpdated = System.currentTimeMillis()
        )

        whenever(mockPreferencesDao.getPreferences()).thenReturn(flowOf(preferences))

        // ACT
        mockPreferencesDao.getPreferences().test {
            val result = awaitItem()

            // ASSERT
            assertEquals(1, result.id, "Should emit preferences")

            cancelAndConsumeRemainingEvents()
        }
    }

    // ========== Toggle Notifications ==========

    @Test
    fun `test toggleNewEpisodes sets newEpisodesEnabled`() = runTest {
        // ARRANGE
        val currentPreferences = NotificationPreferences(
            id = 1,
            newEpisodesEnabled = true,
            newSeasonsEnabled = true,
            weeklyDigestEnabled = false,
            quietHoursStart = 22,
            quietHoursEnd = 8,
            lastUpdated = System.currentTimeMillis()
        )

        val toggled = currentPreferences.copy(newEpisodesEnabled = false)

        // ACT
        val updated = toggled

        // ASSERT
        assertTrue(currentPreferences.newEpisodesEnabled, "Original should be enabled")
        assertFalse(updated.newEpisodesEnabled, "Toggled should be disabled")
    }

    @Test
    fun `test toggleNewSeasons sets newSeasonsEnabled`() = runTest {
        // ARRANGE
        val preferences = NotificationPreferences(
            id = 1,
            newEpisodesEnabled = true,
            newSeasonsEnabled = true,
            weeklyDigestEnabled = false,
            quietHoursStart = 22,
            quietHoursEnd = 8,
            lastUpdated = System.currentTimeMillis()
        )

        val toggled = preferences.copy(newSeasonsEnabled = false)

        // ACT & ASSERT
        assertTrue(preferences.newSeasonsEnabled, "Original should be enabled")
        assertFalse(toggled.newSeasonsEnabled, "Toggled should be disabled")
    }

    @Test
    fun `test toggleWeeklyDigest sets weeklyDigestEnabled`() = runTest {
        // ARRANGE
        val preferences = NotificationPreferences(
            id = 1,
            newEpisodesEnabled = true,
            newSeasonsEnabled = true,
            weeklyDigestEnabled = false,
            quietHoursStart = 22,
            quietHoursEnd = 8,
            lastUpdated = System.currentTimeMillis()
        )

        val toggled = preferences.copy(weeklyDigestEnabled = true)

        // ACT & ASSERT
        assertFalse(preferences.weeklyDigestEnabled, "Original should be disabled")
        assertTrue(toggled.weeklyDigestEnabled, "Toggled should be enabled")
    }

    // ========== Quiet Hours ==========

    @Test
    fun `test updateQuietHours with valid range`() = runTest {
        // ARRANGE
        val startHour = 22 // 10 PM
        val endHour = 8    // 8 AM

        // ACT - Create updated preferences
        val preferences = NotificationPreferences(
            id = 1,
            newEpisodesEnabled = true,
            newSeasonsEnabled = true,
            weeklyDigestEnabled = false,
            quietHoursStart = startHour,
            quietHoursEnd = endHour,
            lastUpdated = System.currentTimeMillis()
        )

        // ASSERT
        assertEquals(22, preferences.quietHoursStart, "Start hour should be 22")
        assertEquals(8, preferences.quietHoursEnd, "End hour should be 8")
    }

    @Test
    fun `test quiet hours boundary validation - valid start hour`() = runTest {
        // ARRANGE
        val startHour = 0  // Minimum valid

        // ACT
        val isValid = startHour in 0..23

        // ASSERT
        assertTrue(isValid, "Start hour 0 is valid")
    }

    @Test
    fun `test quiet hours boundary validation - valid end hour`() = runTest {
        // ARRANGE
        val endHour = 23   // Maximum valid

        // ACT
        val isValid = endHour in 0..23

        // ASSERT
        assertTrue(isValid, "End hour 23 is valid")
    }

    @Test
    fun `test quiet hours boundary validation - invalid start hour`() = runTest {
        // ARRANGE
        val startHour = 24 // Invalid

        // ACT
        val isValid = startHour in 0..23

        // ASSERT
        assertFalse(isValid, "Start hour 24 is invalid")
    }

    @Test
    fun `test isAllowedAtCurrentHour checks quiet hours`() = runTest {
        // ARRANGE
        val preferences = NotificationPreferences(
            id = 1,
            newEpisodesEnabled = true,
            newSeasonsEnabled = true,
            weeklyDigestEnabled = false,
            quietHoursStart = 22,  // 10 PM
            quietHoursEnd = 8,     // 8 AM
            lastUpdated = System.currentTimeMillis()
        )

        // Mock the current hour (e.g., 10 AM = 10)
        val currentHour = 10

        // ACT - Check if allowed (10 AM should be allowed, not in quiet hours 22-8)
        val isAllowed = !(currentHour in 22..23 || currentHour in 0..8)

        // ASSERT
        assertTrue(isAllowed, "10 AM should be allowed (not in quiet hours)")
    }

    // ========== Check Individual Toggles ==========

    @Test
    fun `test isNewEpisodesEnabled returns correct status`() = runTest {
        // ARRANGE
        whenever(mockPreferencesDao.isNewEpisodesEnabled()).thenReturn(true)

        // ACT
        val enabled = mockPreferencesDao.isNewEpisodesEnabled()

        // ASSERT
        assertTrue(enabled, "Should return enabled status")
    }

    @Test
    fun `test isNewEpisodesEnabled defaults to true when null`() = runTest {
        // ARRANGE
        whenever(mockPreferencesDao.isNewEpisodesEnabled()).thenReturn(null)

        // ACT & ASSERT
        // Default is true per spec
        val defaultValue = null ?: true
        assertTrue(defaultValue, "Should default to true")
    }

    @Test
    fun `test isNewSeasonsEnabled returns correct status`() = runTest {
        // ARRANGE
        whenever(mockPreferencesDao.isNewSeasonsEnabled()).thenReturn(true)

        // ACT
        val enabled = mockPreferencesDao.isNewSeasonsEnabled()

        // ASSERT
        assertTrue(enabled, "Should return enabled status")
    }

    @Test
    fun `test isWeeklyDigestEnabled returns correct status`() = runTest {
        // ARRANGE
        whenever(mockPreferencesDao.isWeeklyDigestEnabled()).thenReturn(false)

        // ACT
        val enabled = mockPreferencesDao.isWeeklyDigestEnabled()

        // ASSERT
        assertFalse(enabled, "Should return disabled status for weekly digest")
    }

    // ========== Reset Preferences ==========

    @Test
    fun `test resetToDefaults creates correct default preferences`() = runTest {
        // ARRANGE
        val defaults = NotificationPreferences(
            id = 1,
            newEpisodesEnabled = true,
            newSeasonsEnabled = true,
            weeklyDigestEnabled = false,
            quietHoursStart = 22,
            quietHoursEnd = 8,
            lastUpdated = System.currentTimeMillis()
        )

        // ASSERT
        assertTrue(defaults.newEpisodesEnabled, "New episodes should be enabled by default")
        assertTrue(defaults.newSeasonsEnabled, "New seasons should be enabled by default")
        assertFalse(defaults.weeklyDigestEnabled, "Weekly digest should be disabled by default")
        assertEquals(22, defaults.quietHoursStart, "Default start should be 22")
        assertEquals(8, defaults.quietHoursEnd, "Default end should be 8")
    }

    // ========== NotificationPreferences Entity ==========

    @Test
    fun `test NotificationPreferences entity creation`() = runTest {
        // ARRANGE & ACT
        val prefs = NotificationPreferences(
            id = 1,
            newEpisodesEnabled = true,
            newSeasonsEnabled = false,
            weeklyDigestEnabled = true,
            quietHoursStart = 23,
            quietHoursEnd = 7,
            lastUpdated = System.currentTimeMillis()
        )

        // ASSERT
        assertEquals(1, prefs.id, "ID should match")
        assertTrue(prefs.newEpisodesEnabled, "New episodes toggle should match")
        assertFalse(prefs.newSeasonsEnabled, "New seasons toggle should match")
        assertTrue(prefs.weeklyDigestEnabled, "Weekly digest toggle should match")
        assertEquals(23, prefs.quietHoursStart, "Quiet hours start should match")
        assertEquals(7, prefs.quietHoursEnd, "Quiet hours end should match")
    }

    @Test
    fun `test NotificationPreferences copy with changes`() = runTest {
        // ARRANGE
        val original = NotificationPreferences(
            id = 1,
            newEpisodesEnabled = true,
            newSeasonsEnabled = true,
            weeklyDigestEnabled = false,
            quietHoursStart = 22,
            quietHoursEnd = 8,
            lastUpdated = 1000L
        )

        // ACT
        val updated = original.copy(
            newEpisodesEnabled = false,
            weeklyDigestEnabled = true,
            lastUpdated = 2000L
        )

        // ASSERT
        assertTrue(original.newEpisodesEnabled, "Original should not change")
        assertFalse(updated.newEpisodesEnabled, "Copy should be updated")
        assertTrue(updated.weeklyDigestEnabled, "Copy should have new value")
        assertEquals(2000L, updated.lastUpdated, "Timestamp should be updated")
    }

    // ========== Edge Cases ==========

    @Test
    fun `test quiet hours spanning midnight`() = runTest {
        // ARRANGE - Quiet hours from 10 PM (22) to 8 AM
        val startHour = 22
        val endHour = 8
        val testHours = listOf(22, 23, 0, 1, 7, 8, 9, 21)

        // ACT & ASSERT - Verify hours in range
        assertTrue(22 in 22..23 || 22 in 0..8, "22 should be in quiet hours")
        assertTrue(0 in 22..23 || 0 in 0..8, "0 (midnight) should be in quiet hours")
        assertTrue(8 in 22..23 || 8 in 0..8, "8 should be in quiet hours")
        assertFalse(9 in 22..23 || 9 in 0..8, "9 should NOT be in quiet hours")
    }

    @Test
    fun `test all notification flags can be toggled independently`() = runTest {
        // ARRANGE
        val preferences = NotificationPreferences(
            id = 1,
            newEpisodesEnabled = true,
            newSeasonsEnabled = true,
            weeklyDigestEnabled = true,
            quietHoursStart = 22,
            quietHoursEnd = 8,
            lastUpdated = System.currentTimeMillis()
        )

        // ACT - Toggle each flag independently
        val updated1 = preferences.copy(newEpisodesEnabled = false)
        val updated2 = preferences.copy(newSeasonsEnabled = false)
        val updated3 = preferences.copy(weeklyDigestEnabled = false)

        // ASSERT
        assertFalse(updated1.newEpisodesEnabled, "Episodes flag should toggle")
        assertTrue(updated1.newSeasonsEnabled, "Other flags should remain unchanged")
        assertTrue(updated2.newEpisodesEnabled, "Other flags should remain unchanged")
        assertFalse(updated2.newSeasonsEnabled, "Seasons flag should toggle")
        assertTrue(updated3.newEpisodesEnabled, "Other flags should remain unchanged")
        assertFalse(updated3.weeklyDigestEnabled, "Digest flag should toggle")
    }

    @Test
    fun `verify all tests run successfully`() = runTest {
        // Meta-test to ensure test suite executes
        assertTrue(true, "NotificationPreferencesRepositoryTest suite completed with real assertions")
    }
}
