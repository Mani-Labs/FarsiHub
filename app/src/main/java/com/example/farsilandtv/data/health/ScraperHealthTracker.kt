package com.example.farsilandtv.data.health

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Scraper Health Monitoring
 *
 * Tracks success/failure rates of scrapers for:
 * - Farsiland (WordPress API + HTML scraping)
 * - FarsiPlex (sitemap + page scraping)
 * - Namakade (sitemap + page scraping)
 *
 * Alerts users in Options screen when scrapers are failing
 *
 * Hilt-managed singleton - injected via constructor
 */
@Singleton
class ScraperHealthTracker @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    companion object {
        private const val TAG = "ScraperHealthTracker"
        private const val PREFS_NAME = "scraper_health"
        private const val KEY_PREFIX_SUCCESS = "success_count_"
        private const val KEY_PREFIX_FAILURE = "failure_count_"
        private const val KEY_PREFIX_LAST_SUCCESS = "last_success_"
        private const val KEY_PREFIX_LAST_ERROR = "last_error_"
        private const val KEY_PREFIX_LAST_ERROR_MSG = "last_error_msg_"
        private const val KEY_PREFIX_ERROR_TYPE = "error_type_"

        // Health threshold: below 70% success rate = unhealthy
        private const val HEALTH_THRESHOLD = 0.7f

        // Consider stale if no success in 24 hours
        private const val STALE_THRESHOLD_MS = 24 * 60 * 60 * 1000L

        // CH-C3 FIX: Truncate error messages to prevent SharedPrefs bloat
        private const val MAX_ERROR_MESSAGE_LENGTH = 500

        // CH-H3 FIX: TTL for health data (30 days)
        private const val HEALTH_DATA_TTL_MS = 30L * 24 * 60 * 60 * 1000
    }

    /**
     * EXTERNAL AUDIT FIX CH-L2: Error classification for better diagnostics
     * Helps identify patterns in scraper failures
     */
    enum class ErrorType {
        NETWORK,    // Network connectivity issues (timeout, DNS, connection refused)
        PARSE,      // HTML parsing errors (invalid structure, missing elements)
        TIMEOUT,    // Explicit timeout errors
        SECURITY,   // HTTPS validation failures
        UNKNOWN     // Unclassified errors
    }

    // Scraper sources
    enum class ScraperSource {
        FARSILAND,
        FARSIPLEX,
        NAMAKADE,
        IMVBOX,    // IMVBox.com
        VIDEO_URL  // Video URL extraction
    }

    // Health status for each scraper
    data class ScraperHealth(
        val source: ScraperSource,
        val successCount: Int,
        val failureCount: Int,
        val lastSuccessTimestamp: Long,
        val lastErrorTimestamp: Long,
        val lastErrorMessage: String?,
        val lastErrorType: ErrorType = ErrorType.UNKNOWN  // EXTERNAL AUDIT FIX CH-L2
    ) {
        val totalRequests: Int get() = successCount + failureCount
        val successRate: Float get() = if (totalRequests > 0) successCount.toFloat() / totalRequests else 1f
        val isHealthy: Boolean get() = successRate >= HEALTH_THRESHOLD
        // Only flag as stale if there were previous successes AND it's been 24h+ since last one
        val isStale: Boolean get() = lastSuccessTimestamp > 0 &&
            System.currentTimeMillis() - lastSuccessTimestamp > STALE_THRESHOLD_MS
        val hasRecentErrors: Boolean get() = failureCount > 0 && lastErrorTimestamp > lastSuccessTimestamp
        // No data means no tracking yet - not an alert condition
        val hasNoData: Boolean get() = totalRequests == 0 && lastSuccessTimestamp == 0L
    }

    // In-memory counters for current session
    private val sessionSuccesses = ConcurrentHashMap<ScraperSource, Int>()
    private val sessionFailures = ConcurrentHashMap<ScraperSource, Int>()

    // StateFlow for UI observation
    private val _healthStatus = MutableStateFlow<Map<ScraperSource, ScraperHealth>>(emptyMap())
    val healthStatus: StateFlow<Map<ScraperSource, ScraperHealth>> = _healthStatus.asStateFlow()

    // Alert state
    private val _hasHealthAlerts = MutableStateFlow(false)
    val hasHealthAlerts: StateFlow<Boolean> = _hasHealthAlerts.asStateFlow()

    // CH-H4 FIX: Cache unhealthy scrapers list to avoid repeated filtering
    @Volatile
    private var cachedUnhealthyScrapers: List<ScraperHealth>? = null
    private var unhealthyCacheTime: Long = 0
    private val UNHEALTHY_CACHE_TTL_MS = 30_000L // 30 seconds

    init {
        // Load persisted health data
        loadHealthData()
        // CH-H3 FIX: Clean up old health data on init
        cleanupOldHealthData()
    }

    /**
     * Record a successful scraper operation
     */
    fun recordSuccess(source: ScraperSource) {
        val current = sessionSuccesses.getOrDefault(source, 0)
        sessionSuccesses[source] = current + 1

        // Persist
        val persistedCount = prefs.getInt(KEY_PREFIX_SUCCESS + source.name, 0)
        prefs.edit()
            .putInt(KEY_PREFIX_SUCCESS + source.name, persistedCount + 1)
            .putLong(KEY_PREFIX_LAST_SUCCESS + source.name, System.currentTimeMillis())
            .apply()

        updateHealthStatus()
        Log.d(TAG, "Recorded success for $source (total: ${persistedCount + 1})")
    }

    /**
     * EXTERNAL AUDIT FIX CH-L2: Classify error type for better diagnostics
     */
    private fun classifyError(message: String?): ErrorType {
        if (message == null) return ErrorType.UNKNOWN

        val lowerMessage = message.lowercase()
        return when {
            lowerMessage.contains("timeout") -> ErrorType.TIMEOUT
            lowerMessage.contains("network") || lowerMessage.contains("connection") ||
            lowerMessage.contains("dns") || lowerMessage.contains("host") -> ErrorType.NETWORK
            lowerMessage.contains("parse") || lowerMessage.contains("html") ||
            lowerMessage.contains("json") || lowerMessage.contains("xml") -> ErrorType.PARSE
            lowerMessage.contains("https") || lowerMessage.contains("security") ||
            lowerMessage.contains("ssl") || lowerMessage.contains("certificate") -> ErrorType.SECURITY
            else -> ErrorType.UNKNOWN
        }
    }

    /**
     * Record a failed scraper operation
     * CH-C2 FIX: Uses apply() instead of commit() to avoid ANR from disk I/O
     * CH-C3 FIX: Truncates error messages to max 500 chars
     * EXTERNAL AUDIT FIX CH-L2: Classifies error type for better diagnostics
     */
    fun recordFailure(source: ScraperSource, errorMessage: String? = null) {
        val current = sessionFailures.getOrDefault(source, 0)
        sessionFailures[source] = current + 1

        // CH-C3 FIX: Truncate error message to prevent SharedPrefs bloat
        val truncatedError = errorMessage?.take(MAX_ERROR_MESSAGE_LENGTH)

        // EXTERNAL AUDIT FIX CH-L2: Classify error type
        val errorType = classifyError(truncatedError)

        // Persist (CH-C2: already using apply())
        val persistedCount = prefs.getInt(KEY_PREFIX_FAILURE + source.name, 0)
        prefs.edit()
            .putInt(KEY_PREFIX_FAILURE + source.name, persistedCount + 1)
            .putLong(KEY_PREFIX_LAST_ERROR + source.name, System.currentTimeMillis())
            .putString(KEY_PREFIX_LAST_ERROR_MSG + source.name, truncatedError)
            .putString(KEY_PREFIX_ERROR_TYPE + source.name, errorType.name)
            .apply()

        updateHealthStatus()
        Log.w(TAG, "Recorded failure for $source [$errorType]: $truncatedError (total failures: ${persistedCount + 1})")
    }

    /**
     * Get health status for a specific scraper
     * EXTERNAL AUDIT FIX CH-L2: Includes error type classification
     */
    fun getHealth(source: ScraperSource): ScraperHealth {
        val errorTypeStr = prefs.getString(KEY_PREFIX_ERROR_TYPE + source.name, null)
        val errorType = try {
            if (errorTypeStr != null) ErrorType.valueOf(errorTypeStr) else ErrorType.UNKNOWN
        } catch (e: IllegalArgumentException) {
            ErrorType.UNKNOWN
        }

        return ScraperHealth(
            source = source,
            successCount = prefs.getInt(KEY_PREFIX_SUCCESS + source.name, 0),
            failureCount = prefs.getInt(KEY_PREFIX_FAILURE + source.name, 0),
            lastSuccessTimestamp = prefs.getLong(KEY_PREFIX_LAST_SUCCESS + source.name, 0),
            lastErrorTimestamp = prefs.getLong(KEY_PREFIX_LAST_ERROR + source.name, 0),
            lastErrorMessage = prefs.getString(KEY_PREFIX_LAST_ERROR_MSG + source.name, null),
            lastErrorType = errorType
        )
    }

    /**
     * Get all scrapers with health issues
     * Excludes scrapers with no data (never tracked)
     * CH-H4 FIX: Caches result for 30 seconds to avoid repeated filtering
     */
    fun getUnhealthyScrapers(): List<ScraperHealth> {
        val now = System.currentTimeMillis()
        val cached = cachedUnhealthyScrapers
        if (cached != null && now - unhealthyCacheTime < UNHEALTHY_CACHE_TTL_MS) {
            return cached
        }

        val unhealthy = ScraperSource.values().map { getHealth(it) }
            .filter { !it.hasNoData && (!it.isHealthy || it.isStale) }

        cachedUnhealthyScrapers = unhealthy
        unhealthyCacheTime = now
        return unhealthy
    }

    /**
     * Get alert summary for display in Options screen
     */
    fun getAlertSummary(): String? {
        val unhealthy = getUnhealthyScrapers()
        if (unhealthy.isEmpty()) return null

        return buildString {
            append("Content source issues:\n")
            unhealthy.forEach { health ->
                val status = when {
                    health.isStale -> "No updates in 24h"
                    !health.isHealthy -> "${(health.successRate * 100).toInt()}% success rate"
                    else -> "Unknown issue"
                }
                append("• ${health.source.name}: $status\n")
            }
        }.trim()
    }

    /**
     * Reset health counters (for debugging or after fixing issues)
     */
    fun resetCounters(source: ScraperSource? = null) {
        val editor = prefs.edit()
        val sources = source?.let { listOf(it) } ?: ScraperSource.values().toList()

        sources.forEach { s ->
            editor.putInt(KEY_PREFIX_SUCCESS + s.name, 0)
            editor.putInt(KEY_PREFIX_FAILURE + s.name, 0)
            editor.putLong(KEY_PREFIX_LAST_SUCCESS + s.name, 0)
            editor.putLong(KEY_PREFIX_LAST_ERROR + s.name, 0)
            editor.putString(KEY_PREFIX_LAST_ERROR_MSG + s.name, null)
            editor.putString(KEY_PREFIX_ERROR_TYPE + s.name, null)  // EXTERNAL AUDIT FIX CH-L2
            sessionSuccesses.remove(s)
            sessionFailures.remove(s)
        }
        editor.apply()

        updateHealthStatus()
        Log.d(TAG, "Reset health counters for ${sources.map { it.name }}")
    }

    private fun loadHealthData() {
        updateHealthStatus()
    }

    private fun updateHealthStatus() {
        val healthMap = ScraperSource.values().associateWith { getHealth(it) }
        _healthStatus.value = healthMap
        // Only show alerts for scrapers that have been tracked and have issues
        _hasHealthAlerts.value = healthMap.values.any { !it.hasNoData && (!it.isHealthy || it.isStale) }
        // CH-H4 FIX: Invalidate cache when health data changes
        cachedUnhealthyScrapers = null
    }

    /**
     * CH-H3 FIX: Clean up old health data older than 30 days
     * Prevents SharedPrefs from growing indefinitely
     */
    private fun cleanupOldHealthData() {
        val cutoffTime = System.currentTimeMillis() - HEALTH_DATA_TTL_MS
        val editor = prefs.edit()
        var cleaned = false

        ScraperSource.values().forEach { source ->
            val lastSuccess = prefs.getLong(KEY_PREFIX_LAST_SUCCESS + source.name, 0)
            val lastError = prefs.getLong(KEY_PREFIX_LAST_ERROR + source.name, 0)
            val lastActivity = maxOf(lastSuccess, lastError)

            if (lastActivity > 0 && lastActivity < cutoffTime) {
                // Data is older than 30 days, clean it up
                editor.remove(KEY_PREFIX_SUCCESS + source.name)
                editor.remove(KEY_PREFIX_FAILURE + source.name)
                editor.remove(KEY_PREFIX_LAST_SUCCESS + source.name)
                editor.remove(KEY_PREFIX_LAST_ERROR + source.name)
                editor.remove(KEY_PREFIX_LAST_ERROR_MSG + source.name)
                editor.remove(KEY_PREFIX_ERROR_TYPE + source.name)  // EXTERNAL AUDIT FIX CH-L2
                cleaned = true
                Log.d(TAG, "Cleaned up old health data for $source (older than 30 days)")
            }
        }

        if (cleaned) {
            editor.apply()
        }
    }
}
