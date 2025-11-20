package com.example.farsilandtv.data.scraper

import android.util.Log
import com.example.farsilandtv.data.api.RetrofitClient
import com.example.farsilandtv.data.models.Movie
import com.example.farsilandtv.data.models.Series
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URLEncoder

/**
 * Web Search Scraper for FarsiPlex and Namakade websites
 *
 * Scrapes search results from website HTML since they don't have structured APIs
 *
 * Features:
 * - FarsiPlex search: https://farsiplex.com/?s={query}
 * - Namakade search: https://namakade.com/search?page=search&searchField={query}
 * - Extracts movies and series from search result pages
 * - Parses DooPlay theme structure
 * - CDN fallback chain for image URLs (handles CDN failures gracefully)
 */
object WebSearchScraper {

    private const val TAG = "WebSearchScraper"
    private val httpClient = RetrofitClient.getHttpClient()

    /**
     * CDN domain mappings with fallback chain
     * If primary CDN fails, tries alternatives in order
     */
    private val cdnFallbackChain = listOf(
        "media.iranproud2.net" to listOf("media.negahestan.com", "media.iranproud.net"),
        "media.iranproud.net" to listOf("media.negahestan.com", "media.iranproud2.net")
    ).toMap()

    /**
     * AUDIT FIX #10 (Post-Release): Check if title matches search query
     * Prevents returning 393 irrelevant results (like "samad" search returning everything)
     *
     * Uses same aggressive normalization as ContentRepository deduplication
     * - Removes ALL non-alphanumeric characters (spaces, hyphens, punctuation)
     * - Case-insensitive matching
     *
     * @return true if title contains the query as a substring
     */
    private fun titleMatchesQuery(title: String, query: String): Boolean {
        val normalizedTitle = title.replace(Regex("[^\\p{L}\\p{N}]"), "").lowercase()
        val normalizedQuery = query.replace(Regex("[^\\p{L}\\p{N}]"), "").lowercase()
        return normalizedTitle.contains(normalizedQuery)
    }

    /**
     * Fix CDN URL by trying fallback domains
     * If original domain is known to be unreliable, returns URL with working alternative
     */
    private fun fixCdnUrl(url: String): String {
        if (url.isEmpty()) return url

        // Check if URL contains a problematic CDN domain
        for ((problemDomain, alternatives) in cdnFallbackChain) {
            if (url.contains(problemDomain)) {
                // Use first alternative (most reliable)
                val fixedUrl = url.replace(problemDomain, alternatives.first())
                Log.d(TAG, "CDN fallback: $problemDomain -> ${alternatives.first()}")
                return fixedUrl
            }
        }

        // No known issues with this domain
        return url
    }

    /**
     * Search Farsiland website
     * URL pattern: https://farsiland.com/?s={query}
     */
    suspend fun searchFarsiland(query: String): List<Any> = withContext(Dispatchers.IO) {
        val results = mutableListOf<Any>()

        try {
            val encodedQuery = URLEncoder.encode(query.trim(), "UTF-8")
            val searchUrl = "https://farsiland.com/?s=$encodedQuery"
            Log.d(TAG, "Searching Farsiland: $searchUrl")

            httpClient.newCall(
                okhttp3.Request.Builder()
                    .url(searchUrl)
                    .build()
            ).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "Farsiland search failed: ${response.code}")
                    return@withContext results
                }

                val html = response.body?.string() ?: return@withContext results
                val doc = Jsoup.parse(html)

                // Parse DooPlay theme search results
                // Try multiple selectors for search results
                var searchItems = doc.select("article.movies, article.tvshows")
                if (searchItems.isEmpty()) {
                    // Fallback: select any article elements in search results
                    searchItems = doc.select("article")
                }

                Log.d(TAG, "Found ${searchItems.size} search results")

                for (item in searchItems) {
                    try {
                        // Check if it's a series by multiple indicators
                        val isSeries = item.hasClass("tvshows") ||
                                       item.select(".tpe").text().contains("TV", ignoreCase = true) ||
                                       item.text().contains(" TV", ignoreCase = true) ||
                                       item.select("a[href*='/tvshows/']").isNotEmpty()

                        val parsedItem = if (isSeries) {
                            parseSeriesResult(item)
                        } else {
                            parseMovieResult(item)
                        }

                        // AUDIT FIX #10: Filter out irrelevant results
                        parsedItem?.let {
                            val title = when (it) {
                                is Movie -> it.title
                                is Series -> it.title
                                else -> ""
                            }

                            if (titleMatchesQuery(title, query)) {
                                results.add(it)
                            } else {
                                Log.d(TAG, "Skipping non-matching Farsiland result: $title")
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing Farsiland result: ${e.message}")
                    }
                }

                Log.i(TAG, "Farsiland search completed: ${results.size} results")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Farsiland search error: ${e.message}", e)
        }

        results
    }

    /**
     * Search FarsiPlex website
     * URL pattern: https://farsiplex.com/?s={query}
     */
    suspend fun searchFarsiPlex(query: String): List<Any> = withContext(Dispatchers.IO) {
        val results = mutableListOf<Any>()

        try {
            val encodedQuery = URLEncoder.encode(query.trim(), "UTF-8")
            val searchUrl = "https://farsiplex.com/?s=$encodedQuery"
            Log.d(TAG, "Searching FarsiPlex: $searchUrl")

            httpClient.newCall(
                okhttp3.Request.Builder()
                    .url(searchUrl)
                    .build()
            ).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "FarsiPlex search failed: ${response.code}")
                    return@withContext results
                }

                val html = response.body?.string() ?: return@withContext results
                val doc = Jsoup.parse(html)

                // Parse DooPlay theme search results
                // Structure: .result-item or .search-item with .movies or .tvshows class
                val searchItems = doc.select(".result-item, .search-item, article.movies, article.tvshows, .item.movies, .item.tvshows")

                Log.d(TAG, "Found ${searchItems.size} search results")

                for (item in searchItems) {
                    try {
                        val isMovie = item.hasClass("movies") ||
                                      item.select(".movies").isNotEmpty() ||
                                      item.attr("href").contains("/movie/") ||
                                      item.select("a[href*='/movie/']").isNotEmpty()

                        val isSeries = item.hasClass("tvshows") ||
                                       item.select(".tvshows").isNotEmpty() ||
                                       item.attr("href").contains("/tvshows/") ||
                                       item.select("a[href*='/tvshows/']").isNotEmpty()

                        val parsedItem = if (isMovie) {
                            parseMovieResult(item)
                        } else if (isSeries) {
                            parseSeriesResult(item)
                        } else {
                            // Try to determine from URL
                            val link = item.select("a").attr("href")
                            when {
                                link.contains("/movie/") -> parseMovieResult(item)
                                link.contains("/tvshows/") -> parseSeriesResult(item)
                                else -> null
                            }
                        }

                        // AUDIT FIX #10: Filter out irrelevant results
                        parsedItem?.let {
                            val title = when (it) {
                                is Movie -> it.title
                                is Series -> it.title
                                else -> ""
                            }

                            if (titleMatchesQuery(title, query)) {
                                results.add(it)
                            } else {
                                Log.d(TAG, "Skipping non-matching FarsiPlex result: $title")
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing search result item: ${e.message}")
                    }
                }

                Log.i(TAG, "FarsiPlex search completed: ${results.size} results")
            }

        } catch (e: Exception) {
            Log.e(TAG, "FarsiPlex search error: ${e.message}", e)
        }

        results
    }

    /**
     * Search Namakade website
     * URL pattern: https://namakade.com/search?page=search&searchField={query}
     */
    suspend fun searchNamakade(query: String): List<Any> = withContext(Dispatchers.IO) {
        val results = mutableListOf<Any>()

        try {
            val encodedQuery = URLEncoder.encode(query.trim(), "UTF-8")
            val searchUrl = "https://namakade.com/search?page=search&searchField=$encodedQuery"
            Log.d(TAG, "Searching Namakade: $searchUrl")

            httpClient.newCall(
                okhttp3.Request.Builder()
                    .url(searchUrl)
                    .build()
            ).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "Namakade search failed: ${response.code}")
                    return@withContext results
                }

                val html = response.body?.string() ?: return@withContext results
                val doc = Jsoup.parse(html)

                // Parse Namakade search results
                // Shows are in <ul id="gridMason2"> and movies in <ul id="gridMason4">
                val showItems = doc.select("ul#gridMason2 > li")
                val movieItems = doc.select("ul#gridMason4 > li")

                Log.d(TAG, "Found ${showItems.size} shows and ${movieItems.size} movies")

                // Parse shows
                for (item in showItems) {
                    try {
                        val link = item.select("a").first()?.attr("href") ?: continue
                        val title = item.select(".SSh2").text()

                        if (title.isEmpty()) continue

                        // AUDIT FIX #10: Filter out irrelevant results
                        if (!titleMatchesQuery(title, query)) {
                            Log.d(TAG, "Skipping non-matching show: $title")
                            continue
                        }

                        // Try multiple selectors for poster URL
                        var posterUrl = item.select("img").attr("src")
                            .ifEmpty { item.select("img").attr("data-src") }
                            .ifEmpty { item.select("img").attr("data-original") }
                            .ifEmpty { item.select(".SSimage img").attr("src") }
                            .ifEmpty {
                                // Last resort: find any img within this item
                                item.selectFirst("img")?.attr("src") ?: ""
                            }

                        // Fix broken CDN domain using fallback chain
                        posterUrl = fixCdnUrl(posterUrl)

                        Log.d(TAG, "Namakade show: $title, link: $link")
                        Log.d(TAG, "  Poster URL: ${if (posterUrl.isNotEmpty()) posterUrl else "NOT FOUND"}")

                        val series = Series(
                            id = link.hashCode(),
                            title = title,
                            description = "",
                            posterUrl = if (posterUrl.isNotEmpty()) posterUrl else null,
                            backdropUrl = null,
                            farsilandUrl = if (link.startsWith("http")) link else "https://namakade.com$link",
                            year = null,
                            rating = null,
                            totalSeasons = 0,
                            totalEpisodes = 0,
                            status = null,
                            genres = emptyList(),
                            network = null,
                            cast = emptyList(),
                            dateAdded = System.currentTimeMillis()
                        )
                        results.add(series)
                        Log.d(TAG, "Added show: $title -> $link")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing Namakade show: ${e.message}")
                    }
                }

                // Parse movies
                for (item in movieItems) {
                    try {
                        val link = item.select("a").first()?.attr("href") ?: continue
                        val title = item.select(".SSh3").text()

                        if (title.isEmpty()) continue

                        // AUDIT FIX #10: Filter out irrelevant results
                        if (!titleMatchesQuery(title, query)) {
                            Log.d(TAG, "Skipping non-matching movie: $title")
                            continue
                        }

                        // Try multiple selectors for poster URL
                        var posterUrl = item.select("img").attr("src")
                            .ifEmpty { item.select("img").attr("data-src") }
                            .ifEmpty { item.select("img").attr("data-original") }
                            .ifEmpty { item.select(".SSimage img").attr("src") }
                            .ifEmpty {
                                // Last resort: find any img within this item
                                item.selectFirst("img")?.attr("src") ?: ""
                            }

                        // Fix broken CDN domain using fallback chain
                        posterUrl = fixCdnUrl(posterUrl)

                        val director = item.select(".SSpD1").text().replace("Director:", "").trim()
                        val genre = item.select(".SSpD2").text().replace("Genre:", "").trim()

                        Log.d(TAG, "Namakade movie: $title, link: $link")
                        Log.d(TAG, "  Poster URL: ${if (posterUrl.isNotEmpty()) posterUrl else "NOT FOUND"}")

                        val movie = Movie(
                            id = link.hashCode(),
                            title = title,
                            description = "",
                            posterUrl = if (posterUrl.isNotEmpty()) posterUrl else null,
                            backdropUrl = null,
                            farsilandUrl = if (link.startsWith("http")) link else "https://namakade.com$link",
                            year = extractYear(title),
                            rating = null,
                            runtime = null,
                            genres = if (genre.isNotEmpty()) listOf(genre) else emptyList(),
                            director = if (director.isNotEmpty()) director else null,
                            cast = emptyList(),
                            dateAdded = System.currentTimeMillis()
                        )
                        results.add(movie)
                        Log.d(TAG, "Added movie: $title -> $link")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing Namakade movie: ${e.message}")
                    }
                }

                Log.i(TAG, "Namakade search completed: ${results.size} results")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Namakade search error: ${e.message}", e)
        }

        results
    }

    /**
     * Parse movie from search result item
     */
    private fun parseMovieResult(item: Element): Movie? {
        try {
            val title = item.select("h2, h3, .title, .entry-title").text()
                .ifEmpty { item.select("a").attr("title") }
                .ifEmpty { return null }

            val url = item.select("a").attr("href")
                .takeIf { it.isNotEmpty() } ?: return null

            val posterUrl = item.select("img").attr("src")
                .ifEmpty { item.select("img").attr("data-src") }
                .takeIf { it.isNotEmpty() }

            val description = item.select(".description, .excerpt, p").text()
                .takeIf { it.isNotEmpty() }

            val year = extractYear(item.text())

            val rating = item.select(".rating, .vote_average").text()
                .replace("[^0-9.]".toRegex(), "")
                .toDoubleOrNull()
                ?.toFloat()

            return Movie(
                id = url.hashCode(),
                title = title,
                description = description ?: "",
                posterUrl = posterUrl,
                backdropUrl = null,
                farsilandUrl = url,
                year = year,
                rating = rating,
                runtime = null,
                genres = emptyList(),
                director = null,
                cast = emptyList(),
                dateAdded = System.currentTimeMillis()
            )

        } catch (e: Exception) {
            Log.e(TAG, "Error parsing movie: ${e.message}")
            return null
        }
    }

    /**
     * Parse series from search result item
     */
    private fun parseSeriesResult(item: Element): Series? {
        try {
            val title = item.select("h2, h3, .title, .entry-title").text()
                .ifEmpty { item.select("a").attr("title") }
                .ifEmpty { return null }

            val url = item.select("a").attr("href")
                .takeIf { it.isNotEmpty() } ?: return null

            val posterUrl = item.select("img").attr("src")
                .ifEmpty { item.select("img").attr("data-src") }
                .takeIf { it.isNotEmpty() }

            val description = item.select(".description, .excerpt, p").text()
                .takeIf { it.isNotEmpty() }

            val year = extractYear(item.text())

            val rating = item.select(".rating, .vote_average").text()
                .replace("[^0-9.]".toRegex(), "")
                .toDoubleOrNull()
                ?.toFloat()

            return Series(
                id = url.hashCode(),
                title = title,
                description = description ?: "",
                posterUrl = posterUrl,
                backdropUrl = null,
                farsilandUrl = url,
                year = year,
                rating = rating,
                totalSeasons = 0,
                totalEpisodes = 0,
                status = null,
                genres = emptyList(),
                network = null,
                cast = emptyList(),
                dateAdded = System.currentTimeMillis()
            )

        } catch (e: Exception) {
            Log.e(TAG, "Error parsing series: ${e.message}")
            return null
        }
    }

    /**
     * Extract year from text
     */
    private fun extractYear(text: String): Int? {
        val yearPattern = Regex("\\b(19|20)\\d{2}\\b")
        return yearPattern.find(text)?.value?.toIntOrNull()
    }
}
