package com.example.farsilandtv.utils

/**
 * AUDIT FIX C1/C3: Configuration provider to replace hardcoded domains and logic.
 *
 * This object provides a centralized configuration system that can be updated
 * at runtime, eliminating brittleness from hardcoded CDN domains and CSS selectors.
 *
 * Current Implementation: Static defaults
 * Production TODO: Fetch from Firebase Remote Config or JSON URL on app startup
 *
 * Benefits:
 * - CDN domain changes don't require APK updates
 * - CSS selector updates can be deployed without releases
 * - Graceful fallback to defaults if remote fetch fails
 *
 * Usage:
 * ```kotlin
 * // In MainActivity.onCreate()
 * lifecycleScope.launch {
 *     RemoteConfig.fetchFromRemote()
 * }
 *
 * // In scrapers/validators
 * val mirrors = RemoteConfig.cdnMirrors
 * val domains = RemoteConfig.trustedDomains
 * ```
 */
object RemoteConfig {

    /**
     * AUDIT FIX C1: Dynamic CDN Mirror List
     *
     * Default mirrors for video content delivery.
     * In production, fetch from:
     * - Firebase Remote Config key: "cdn_mirrors"
     * - Or JSON URL: https://your-domain.com/config/cdn.json
     *
     * Format example:
     * ```json
     * {
     *   "cdn_mirrors": ["d1.flnd.buzz", "d2.flnd.buzz", "s1.farsicdn.buzz"]
     * }
     * ```
     */
    var cdnMirrors: List<String> = listOf(
        "d1.flnd.buzz",
        "d2.flnd.buzz"
    )
        private set

    /**
     * AUDIT FIX C1: Dynamic Trusted Domains for HTTPS validation
     *
     * Default trusted domains for content sources.
     * Allows adding new content sources without APK updates.
     */
    var trustedDomains: Set<String> = setOf(
        "farsiland.com",
        "farsiplex.com",
        "flnd.buzz",
        "d1.flnd.buzz",
        "d2.flnd.buzz",
        "namakade.com",
        "wp.farsiland.com",
        "negahestan.com",
        "media.negahestan.com"
    )
        private set

    /**
     * AUDIT FIX C3: Dynamic CSS Selectors for web scraping
     *
     * Allows updating scraper logic without APK releases when
     * source websites change their HTML structure.
     *
     * TODO: Implement when converting scrapers to use this config
     */
    var cssSelectors: Map<String, Map<String, String>> = mapOf(
        "farsiland" to mapOf(
            "search_results" to ".SSh2",
            "grid_container" to "ul#gridMason2",
            "movie_card" to "article.movies"
        ),
        "farsiplex" to mapOf(
            "search_results" to ".search-page",
            "video_embed" to "iframe[src*='jwplayer']"
        ),
        "namakade" to mapOf(
            "video_player" to "#player-embed",
            "episode_list" to ".episode-list"
        )
    )
        private set

    /**
     * Update configuration from remote source
     *
     * Call this from MainActivity.onCreate() to fetch latest config.
     *
     * @param mirrors New CDN mirror list (null = keep current)
     * @param domains New trusted domain set (null = keep current)
     * @param selectors New CSS selector map (null = keep current)
     */
    @Synchronized
    fun update(
        mirrors: List<String>? = null,
        domains: Set<String>? = null,
        selectors: Map<String, Map<String, String>>? = null
    ) {
        mirrors?.let {
            if (it.isNotEmpty()) {
                cdnMirrors = it
                android.util.Log.d("RemoteConfig", "Updated CDN mirrors: ${it.joinToString()}")
            }
        }

        domains?.let {
            if (it.isNotEmpty()) {
                trustedDomains = it
                android.util.Log.d("RemoteConfig", "Updated trusted domains: ${it.size} domains")
            }
        }

        selectors?.let {
            if (it.isNotEmpty()) {
                cssSelectors = it
                android.util.Log.d("RemoteConfig", "Updated CSS selectors for ${it.keys.joinToString()}")
            }
        }
    }

    /**
     * Fetch configuration from remote JSON URL
     *
     * Production implementation example:
     * ```kotlin
     * suspend fun fetchFromRemote(): Boolean = withContext(Dispatchers.IO) {
     *     try {
     *         val url = "https://raw.githubusercontent.com/yourorg/farsiplex-config/main/config.json"
     *         val response = httpClient.get(url)
     *         val json = JSONObject(response)
     *
     *         val mirrors = json.optJSONArray("cdn_mirrors")?.let { arr ->
     *             List(arr.length()) { arr.getString(it) }
     *         }
     *
     *         val domains = json.optJSONArray("trusted_domains")?.let { arr ->
     *             (0 until arr.length()).map { arr.getString(it) }.toSet()
     *         }
     *
     *         update(mirrors, domains)
     *         true
     *     } catch (e: Exception) {
     *         Log.e("RemoteConfig", "Failed to fetch remote config", e)
     *         false // Gracefully fall back to defaults
     *     }
     * }
     * ```
     */
    suspend fun fetchFromRemote(): Boolean {
        // TODO: Implement remote fetch logic
        // For now, return true to indicate "using defaults"
        android.util.Log.d("RemoteConfig", "Using default configuration (remote fetch not yet implemented)")
        return true
    }

    /**
     * Reset to default configuration
     */
    @Synchronized
    fun resetToDefaults() {
        cdnMirrors = listOf("d1.flnd.buzz", "d2.flnd.buzz")
        trustedDomains = setOf(
            "farsiland.com",
            "farsiplex.com",
            "flnd.buzz",
            "d1.flnd.buzz",
            "d2.flnd.buzz",
            "namakade.com",
            "wp.farsiland.com",
            "negahestan.com",
            "media.negahestan.com"
        )
        android.util.Log.d("RemoteConfig", "Configuration reset to defaults")
    }

    /**
     * Get CSS selector for a specific site and element
     *
     * @param site Site identifier (e.g., "farsiland", "farsiplex")
     * @param element Element identifier (e.g., "search_results", "grid_container")
     * @return CSS selector string or null if not found
     */
    fun getCssSelector(site: String, element: String): String? {
        return cssSelectors[site]?.get(element)
    }
}
