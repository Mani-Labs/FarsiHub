package com.example.farsilandtv.data.scraper

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import com.example.farsilandtv.data.api.RetrofitClient
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * EXTERNAL AUDIT FIX SN-M1: Suspendable OkHttp Call extension to prevent zombie threads
 *
 * Problem: execute() is a blocking call that doesn't respond to coroutine cancellation
 * Result: Cancelled coroutines leave threads blocked for 25 seconds (timeout)
 * Solution: Use enqueue() with suspendCancellableCoroutine for proper cancellation
 */
private suspend fun Call.await(): Response {
    return suspendCancellableCoroutine { continuation ->
        // Register cancellation handler BEFORE starting the call
        continuation.invokeOnCancellation {
            cancel() // Cancel OkHttp call immediately when coroutine is cancelled
        }

        enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                continuation.resume(response)
            }

            override fun onFailure(call: Call, e: IOException) {
                // Only resume with exception if coroutine is still active
                if (continuation.isActive) {
                    continuation.resumeWithException(e)
                }
            }
        })
    }
}

/**
 * HTML scraper for extracting comprehensive episode metadata from Farsiland.com
 *
 * Extracts:
 * - Episode-specific poster URLs
 * - Persian and English titles (H1 vs H2)
 * - Release dates
 * - Ratings and vote counts
 * - Quality indicators (HD/SD)
 * - Schema.org structured data
 * - Series information from breadcrumbs
 */
object EpisodeMetadataScraper {

    private const val TAG = "EpisodeMetadataScraper"
    private val httpClient = RetrofitClient.getHttpClient()

    // EXTERNAL AUDIT FIX SN-L4: Pre-compiled regex patterns (avoid recompilation on every call)
    // Issue: Regex patterns compiled in hot paths cause GC pressure
    // Fix: Compile once at object initialization, reuse throughout lifetime
    private val IMAGE_URL_PATTERN = Regex(""".*\.(jpg|jpeg|png|webp)$""", RegexOption.IGNORE_CASE)
    private val DATE_PATTERN = Regex("""([A-Z][a-z]{2,8}\.?\s+\d{1,2},?\s+\d{4})""")
    private val RATING_PATTERN = Regex("""(\d+\.\d+)\s*""")
    private val VOTE_PATTERN = Regex("""(\d+)\s*votes?""", RegexOption.IGNORE_CASE)

    /**
     * Complete episode metadata extracted from page
     */
    data class EpisodeMetadata(
        val episodePosterUrl: String? = null,
        val persianTitle: String? = null,
        val englishTitle: String? = null,
        val releaseDate: String? = null,
        val rating: Float? = null,
        val voteCount: Int? = null,
        val quality: String? = null,
        val description: String? = null,
        val seriesName: String? = null,
        val seriesUrl: String? = null,
        val uploadDate: String? = null
    )

    /**
     * Extract comprehensive metadata from episode page
     *
     * @param pageUrl Full URL to episode page (e.g., https://farsiland.com/episodes/mahkoum-ep09/)
     * @return ScraperResult containing EpisodeMetadata object with all extracted fields
     *         Returns Success, NetworkError, ParseError, or NoDataFound
     */
    suspend fun extractMetadata(pageUrl: String): ScraperResult<EpisodeMetadata> = withContext(Dispatchers.IO) {
        try {
            android.util.Log.d(TAG, "========================================")
            android.util.Log.d(TAG, "extractMetadata() called")
            android.util.Log.d(TAG, "Page URL: $pageUrl")

            // Fetch HTML content
            val html = fetchHtml(pageUrl)
            android.util.Log.d(TAG, "HTML fetched. Length: ${html.length} characters")

            // Parse HTML with Jsoup
            val doc = Jsoup.parse(html)

            // Extract all metadata
            val metadata = EpisodeMetadata(
                episodePosterUrl = extractEpisodePoster(doc),
                persianTitle = extractPersianTitle(doc),
                englishTitle = extractEnglishTitle(doc),
                releaseDate = extractReleaseDate(doc),
                rating = extractRating(doc),
                voteCount = extractVoteCount(doc),
                quality = extractQuality(doc),
                description = extractDescription(doc),
                seriesName = extractSeriesName(doc),
                seriesUrl = extractSeriesUrl(doc),
                uploadDate = extractUploadDate(doc)
            )

            android.util.Log.d(TAG, "Metadata extracted successfully:")
            android.util.Log.d(TAG, "  Episode Poster: ${metadata.episodePosterUrl}")
            android.util.Log.d(TAG, "  Persian Title: ${metadata.persianTitle}")
            android.util.Log.d(TAG, "  English Title: ${metadata.englishTitle}")
            android.util.Log.d(TAG, "  Release Date: ${metadata.releaseDate}")
            android.util.Log.d(TAG, "  Rating: ${metadata.rating}")
            android.util.Log.d(TAG, "  Vote Count: ${metadata.voteCount}")
            android.util.Log.d(TAG, "  Quality: ${metadata.quality}")
            android.util.Log.d(TAG, "  Series: ${metadata.seriesName}")
            android.util.Log.d(TAG, "========================================")

            ScraperResult.Success(metadata)

        } catch (e: Exception) {
            android.util.Log.e(TAG, "========================================")
            android.util.Log.e(TAG, "EXCEPTION in extractMetadata", e)
            android.util.Log.e(TAG, "Page URL: $pageUrl")
            android.util.Log.e(TAG, "Exception: ${e.message}")
            e.printStackTrace()
            android.util.Log.e(TAG, "========================================")

            // Classify exception type for proper error handling
            when (e) {
                is java.net.UnknownHostException,
                is java.net.SocketTimeoutException,
                is java.net.ConnectException,
                is java.io.IOException -> {
                    ScraperResult.NetworkError("Network error: ${e.message}", e)
                }
                else -> {
                    ScraperResult.ParseError("Failed to parse episode metadata: ${e.message}", e)
                }
            }
        }
    }

    /**
     * Extract episode-specific poster URL
     * Pattern: <a href="https://farsiland.com/wp-content/uploads/...jpg"><img src="..." alt="..."></a>
     */
    private fun extractEpisodePoster(doc: Document): String? {
        try {
            // Method 1: Find link with wp-content/uploads in href
            val posterLink = doc.select("a[href*=wp-content/uploads]").firstOrNull()
            if (posterLink != null) {
                // Get the full-size image URL from the link href
                val href = posterLink.attr("href")
                // EXTERNAL AUDIT FIX SN-L4: Use pre-compiled pattern
                if (href.matches(IMAGE_URL_PATTERN)) {
                    android.util.Log.d(TAG, "Found episode poster (Method 1): $href")
                    return href
                }

                // Or get from img src inside the link
                val img = posterLink.select("img").firstOrNull()
                if (img != null) {
                    val src = img.attr("src")
                    if (src.contains("wp-content/uploads")) {
                        android.util.Log.d(TAG, "Found episode poster (Method 1b): $src")
                        return src
                    }
                }
            }

            // Method 2: Look for img with alt matching episode pattern
            val episodeImg = doc.select("img[alt*=EP], img[alt*=Episode]").firstOrNull()
            if (episodeImg != null) {
                val src = episodeImg.attr("src")
                if (src.contains("wp-content/uploads")) {
                    android.util.Log.d(TAG, "Found episode poster (Method 2): $src")
                    return src
                }
            }

            android.util.Log.d(TAG, "No episode poster found")
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error extracting episode poster", e)
        }

        return null
    }

    /**
     * Extract Persian title from H1
     * Pattern: <h1 class="player-title episode">Mahkoum Episode 9</h1>
     */
    private fun extractPersianTitle(doc: Document): String? {
        try {
            // Method 1: H1 with class "player-title" or "episode"
            val h1 = doc.select("h1.player-title, h1.episode").firstOrNull()
            if (h1 != null) {
                val title = h1.text().trim()
                android.util.Log.d(TAG, "Found Persian title from H1: $title")
                return title
            }

            // Method 2: First H1 on page
            val firstH1 = doc.select("h1").firstOrNull()
            if (firstH1 != null) {
                val title = firstH1.text().trim()
                android.util.Log.d(TAG, "Found title from first H1: $title")
                return title
            }

            android.util.Log.d(TAG, "No Persian title found")
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error extracting Persian title", e)
        }

        return null
    }

    /**
     * Extract English title from H2
     * Pattern: <h2>Mahkoum EP09</h2>
     */
    private fun extractEnglishTitle(doc: Document): String? {
        try {
            // Find H2 near H1 (should be sibling or close by)
            val h2 = doc.select("h2").firstOrNull()
            if (h2 != null) {
                val title = h2.text().trim()
                // Skip if it's "Login" or other non-episode text
                if (!title.contains("Login", ignoreCase = true) &&
                    !title.contains("comment", ignoreCase = true)) {
                    android.util.Log.d(TAG, "Found English title from H2: $title")
                    return title
                }
            }

            // Fallback: Look for itemprop="name" in meta
            val nameMeta = doc.select("meta[itemprop=name]").firstOrNull()
            if (nameMeta != null) {
                val title = nameMeta.attr("content").trim()
                android.util.Log.d(TAG, "Found English title from schema: $title")
                return title
            }

            android.util.Log.d(TAG, "No English title found")
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error extracting English title", e)
        }

        return null
    }

    /**
     * Extract release date
     * Pattern: Container with "Release Date:" text, followed by date like "Oct. 29, 2025"
     */
    private fun extractReleaseDate(doc: Document): String? {
        try {
            // Find element containing "Release Date" text
            val dateElements = doc.select("*:contains(Release Date)")
            for (element in dateElements) {
                val text = element.text()
                // Extract date pattern (e.g., "Oct. 29, 2025")
                // EXTERNAL AUDIT FIX SN-L4: Use pre-compiled pattern
                val match = DATE_PATTERN.find(text)
                if (match != null) {
                    val date = match.value.trim()
                    android.util.Log.d(TAG, "Found release date: $date")
                    return date
                }
            }

            android.util.Log.d(TAG, "No release date found")
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error extracting release date", e)
        }

        return null
    }

    /**
     * Extract rating value from Schema.org microdata
     * Pattern: <span itemprop="ratingValue">6.8</span>
     */
    private fun extractRating(doc: Document): Float? {
        try {
            // Method 1: itemprop="ratingValue"
            val ratingElement = doc.select("[itemprop=ratingValue]").firstOrNull()
            if (ratingElement != null) {
                val ratingText = ratingElement.text().trim()
                val rating = ratingText.toFloatOrNull()
                if (rating != null) {
                    android.util.Log.d(TAG, "Found rating: $rating")
                    return rating
                }
            }

            // Method 2: Look for rating pattern in text (e.g., "6.8")
            // EXTERNAL AUDIT FIX SN-L4: Use pre-compiled pattern
            val voteElements = doc.select("*:contains(votes)")
            for (element in voteElements) {
                val match = RATING_PATTERN.find(element.text())
                if (match != null) {
                    val rating = match.groupValues[1].toFloatOrNull()
                    if (rating != null && rating >= 0 && rating <= 10) {
                        android.util.Log.d(TAG, "Found rating (pattern): $rating")
                        return rating
                    }
                }
            }

            android.util.Log.d(TAG, "No rating found")
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error extracting rating", e)
        }

        return null
    }

    /**
     * Extract vote count from Schema.org microdata
     * Pattern: <span itemprop="ratingCount">345</span> or "345 votes" text
     */
    private fun extractVoteCount(doc: Document): Int? {
        try {
            // Method 1: itemprop="ratingCount"
            val countElement = doc.select("[itemprop=ratingCount]").firstOrNull()
            if (countElement != null) {
                val countText = countElement.text().trim()
                val count = countText.toIntOrNull()
                if (count != null) {
                    android.util.Log.d(TAG, "Found vote count: $count")
                    return count
                }
            }

            // Method 2: Look for "XXX votes" pattern
            // EXTERNAL AUDIT FIX SN-L4: Use pre-compiled pattern
            val voteElements = doc.select("*:contains(votes)")
            for (element in voteElements) {
                val match = VOTE_PATTERN.find(element.text())
                if (match != null) {
                    val count = match.groupValues[1].toIntOrNull()
                    if (count != null) {
                        android.util.Log.d(TAG, "Found vote count (pattern): $count")
                        return count
                    }
                }
            }

            android.util.Log.d(TAG, "No vote count found")
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error extracting vote count", e)
        }

        return null
    }

    /**
     * Extract quality badge (HD, SD, etc.)
     * Pattern: <div class="quality">HD</div>
     */
    private fun extractQuality(doc: Document): String? {
        try {
            // Method 1: Element with class "quality"
            val qualityElement = doc.select(".quality, .Qlty").firstOrNull()
            if (qualityElement != null) {
                val quality = qualityElement.text().trim()
                if (quality.isNotEmpty()) {
                    android.util.Log.d(TAG, "Found quality: $quality")
                    return quality
                }
            }

            // Method 2: Look for HD/SD text in small elements
            val hdElements = doc.select("*:containsOwn(HD), *:containsOwn(SD)")
            for (element in hdElements) {
                val text = element.ownText().trim()
                if (text == "HD" || text == "SD" || text == "4K") {
                    android.util.Log.d(TAG, "Found quality (pattern): $text")
                    return text
                }
            }

            android.util.Log.d(TAG, "No quality badge found")
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error extracting quality", e)
        }

        return null
    }

    /**
     * Extract episode description from Schema.org microdata
     * Pattern: <meta itemprop="description" content="...">
     */
    private fun extractDescription(doc: Document): String? {
        try {
            // Method 1: itemprop="description"
            val descElement = doc.select("[itemprop=description]").firstOrNull()
            if (descElement != null) {
                val desc = descElement.attr("content").trim()
                if (desc.isNotEmpty()) {
                    android.util.Log.d(TAG, "Found description: ${desc.take(100)}...")
                    return desc
                }
            }

            // Method 2: meta description tag
            val metaDesc = doc.select("meta[name=description]").firstOrNull()
            if (metaDesc != null) {
                val desc = metaDesc.attr("content").trim()
                if (desc.isNotEmpty()) {
                    android.util.Log.d(TAG, "Found meta description: ${desc.take(100)}...")
                    return desc
                }
            }

            android.util.Log.d(TAG, "No description found")
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error extracting description", e)
        }

        return null
    }

    /**
     * Extract series name from breadcrumb
     * Pattern: BreadcrumbList Schema.org microdata
     */
    private fun extractSeriesName(doc: Document): String? {
        try {
            // Method 1: From breadcrumb - find the series name (3rd position typically)
            val breadcrumbItems = doc.select("[itemprop=itemListElement]")
            if (breadcrumbItems.size >= 3) {
                // Usually: Home > TV Series > [Series Name] > Episode
                val seriesItem = breadcrumbItems[2]
                val seriesName = seriesItem.select("[itemprop=name]").firstOrNull()?.text()?.trim()
                if (seriesName != null && seriesName.isNotEmpty()) {
                    android.util.Log.d(TAG, "Found series name from breadcrumb: $seriesName")
                    return seriesName
                }
            }

            // Method 2: Look for link to /tvshows/
            val seriesLink = doc.select("a[href*=/tvshows/]").firstOrNull()
            if (seriesLink != null) {
                val seriesName = seriesLink.text().trim()
                if (seriesName.isNotEmpty()) {
                    android.util.Log.d(TAG, "Found series name from link: $seriesName")
                    return seriesName
                }
            }

            android.util.Log.d(TAG, "No series name found")
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error extracting series name", e)
        }

        return null
    }

    /**
     * Extract series URL from breadcrumb or navigation
     * Pattern: Links to /tvshows/{series-slug}/
     */
    private fun extractSeriesUrl(doc: Document): String? {
        try {
            // Method 1: From "ALL" button or similar
            val allButton = doc.select("a[href*=/tvshows/]:contains(ALL)").firstOrNull()
            if (allButton != null) {
                val url = allButton.attr("abs:href")
                if (url.isNotEmpty()) {
                    android.util.Log.d(TAG, "Found series URL from ALL button: $url")
                    return url
                }
            }

            // Method 2: From breadcrumb
            val breadcrumbItems = doc.select("[itemprop=itemListElement]")
            if (breadcrumbItems.size >= 3) {
                val seriesItem = breadcrumbItems[2]
                val seriesLink = seriesItem.select("a[href*=/tvshows/]").firstOrNull()
                if (seriesLink != null) {
                    val url = seriesLink.attr("abs:href")
                    if (url.isNotEmpty()) {
                        android.util.Log.d(TAG, "Found series URL from breadcrumb: $url")
                        return url
                    }
                }
            }

            // Method 3: Any link to /tvshows/
            val seriesLink = doc.select("a[href*=/tvshows/]").firstOrNull()
            if (seriesLink != null) {
                val url = seriesLink.attr("abs:href")
                if (url.isNotEmpty()) {
                    android.util.Log.d(TAG, "Found series URL from link: $url")
                    return url
                }
            }

            android.util.Log.d(TAG, "No series URL found")
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error extracting series URL", e)
        }

        return null
    }

    /**
     * Extract upload date from Schema.org microdata
     * Pattern: <meta itemprop="uploadDate" content="October 29, 2025">
     */
    private fun extractUploadDate(doc: Document): String? {
        try {
            val uploadElement = doc.select("[itemprop=uploadDate]").firstOrNull()
            if (uploadElement != null) {
                val date = uploadElement.attr("content").trim()
                if (date.isNotEmpty()) {
                    android.util.Log.d(TAG, "Found upload date: $date")
                    return date
                }
            }

            android.util.Log.d(TAG, "No upload date found")
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error extracting upload date", e)
        }

        return null
    }

    /**
     * Fetch HTML content from URL
     */
    private suspend fun fetchHtml(url: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        // EXTERNAL AUDIT FIX SN-M1: Use await() instead of execute() to prevent zombie threads
        // Issue: execute() blocks thread even when coroutine is cancelled
        // Fix: await() is cancellation-aware and frees thread immediately
        httpClient.newCall(request).await().use { response ->
            if (!response.isSuccessful) {
                throw Exception("HTTP ${response.code}: ${response.message}")
            }

            response.body?.string() ?: throw Exception("Empty response body")
        }
    }
}
