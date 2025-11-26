package com.example.farsilandtv.data.scraper

import com.example.farsilandtv.data.api.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.Request
import org.jsoup.Jsoup
import android.util.Log

/**
 * Scraper for Farsiland.com homepage to extract featured titles
 * Parses the Featured Titles carousel section
 */
object HomepageScraper {
    private const val TAG = "HomepageScraper"
    private const val HOMEPAGE_URL = "https://farsiland.com/home"
    private const val TIMEOUT_MS = 15_000L

    data class FeaturedItem(
        val title: String,
        val type: String, // "Movie" or "Series"
        val imageUrl: String
    )

    /**
     * Scrape TV shows from homepage
     * @return List of TV show titles in website order
     */
    suspend fun scrapeRecentShows(): Result<List<FeaturedItem>> = withContext(Dispatchers.IO) {
        try {
            withTimeout(TIMEOUT_MS) {
                val request = Request.Builder()
                    .url(HOMEPAGE_URL)
                    .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .build()

                val response = RetrofitClient.getHttpClient().newCall(request).execute()

                if (!response.isSuccessful) {
                    return@withTimeout Result.failure(
                        Exception("Failed to fetch homepage: ${response.code}")
                    )
                }

                val html = response.body?.string() ?: return@withTimeout Result.failure(
                    Exception("Empty response body")
                )

                val document = Jsoup.parse(html)
                val showItems = mutableListOf<FeaturedItem>()

                // Find TV shows section - typically has a heading "Recent Shows" or similar
                // Look for series items in the main content area
                document.select("div.image.homeimage, div.serie-item, div.tv-show-item, a[href*='/series/'], a[href*='/show/']").forEach { element ->
                    try {
                        val title = element.select("h3.title, h3, .title, .show-title").text().ifEmpty {
                            element.attr("title")
                        }
                        val type = element.select("span.item_type").text()
                        val imageUrl = element.attr("data-bg").ifEmpty {
                            element.select("img").attr("src")
                        }

                        // Filter to only include series/shows
                        if (title.isNotEmpty() && (type.contains("Series", ignoreCase = true) ||
                            type.contains("Show", ignoreCase = true) ||
                            element.attr("href").contains("/series/") ||
                            element.attr("href").contains("/show/"))) {
                            showItems.add(
                                FeaturedItem(
                                    title = title.trim(),
                                    type = "Series",
                                    imageUrl = imageUrl
                                )
                            )
                            Log.d(TAG, "Found show: $title")
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Error parsing show item: ${e.message}")
                    }
                }

                if (showItems.isEmpty()) {
                    Log.w(TAG, "No shows found on homepage")
                    return@withTimeout Result.failure(
                        Exception("No shows found")
                    )
                }

                Log.i(TAG, "Successfully scraped ${showItems.size} shows")
                Result.success(showItems)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error scraping shows: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Scrape featured titles from the homepage
     * @return List of featured item titles
     */
    suspend fun scrapeFeaturedTitles(): Result<List<FeaturedItem>> = withContext(Dispatchers.IO) {
        try {
            withTimeout(TIMEOUT_MS) {
                val request = Request.Builder()
                    .url(HOMEPAGE_URL)
                    .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .build()

                val response = RetrofitClient.getHttpClient().newCall(request).execute()

                if (!response.isSuccessful) {
                    return@withTimeout Result.failure(
                        Exception("Failed to fetch homepage: ${response.code}")
                    )
                }

                val html = response.body?.string() ?: return@withTimeout Result.failure(
                    Exception("Empty response body")
                )

                val document = Jsoup.parse(html)
                val featuredItems = mutableListOf<FeaturedItem>()

                // Find featured titles section
                // Structure: <div class="image homeimage"> with <h3 class="title"> inside
                document.select("div.homeimage").forEach { element ->
                    try {
                        val title = element.select("h3.title").text()
                        val type = element.select("span.item_type").text()
                        val imageUrl = element.attr("data-bg").ifEmpty {
                            element.select("img").attr("src")
                        }

                        if (title.isNotEmpty()) {
                            featuredItems.add(
                                FeaturedItem(
                                    title = title.trim(),
                                    type = type.trim().ifEmpty { "Unknown" },
                                    imageUrl = imageUrl
                                )
                            )
                            Log.d(TAG, "Found featured item: $title ($type)")
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Error parsing featured item: ${e.message}")
                    }
                }

                if (featuredItems.isEmpty()) {
                    Log.w(TAG, "No featured items found on homepage")
                    return@withTimeout Result.failure(
                        Exception("No featured items found")
                    )
                }

                Log.i(TAG, "Successfully scraped ${featuredItems.size} featured items")
                Result.success(featuredItems)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error scraping featured titles: ${e.message}", e)
            Result.failure(e)
        }
    }
}
