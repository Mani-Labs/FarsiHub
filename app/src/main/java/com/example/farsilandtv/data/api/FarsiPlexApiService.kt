package com.example.farsilandtv.data.api

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader

/**
 * API service for FarsiPlex.com
 * FarsiPlex uses DooPlay theme without clean REST API
 * We use sitemap XML for content discovery
 */
class FarsiPlexApiService(private val httpClient: okhttp3.OkHttpClient) {

    companion object {
        private const val BASE_URL = "https://farsiplex.com"
        const val MOVIES_SITEMAP = "$BASE_URL/wp-sitemap-posts-movies-1.xml"
        const val TVSHOWS_SITEMAP = "$BASE_URL/wp-sitemap-posts-tvshows-1.xml"
        const val EPISODES_SITEMAP = "$BASE_URL/wp-sitemap-posts-episodes-1.xml"
    }

    /**
     * Sitemap URL entry
     */
    data class SitemapUrl(
        val loc: String,          // URL location
        val lastmod: String?      // Last modified date (ISO 8601)
    )

    /**
     * Fetch and parse sitemap XML
     * Returns list of URLs with last modified dates
     */
    suspend fun fetchSitemap(sitemapUrl: String): List<SitemapUrl> {
        return try {
            val request = okhttp3.Request.Builder()
                .url(sitemapUrl)
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                android.util.Log.e("FarsiPlexApi", "Sitemap fetch failed: ${response.code}")
                return emptyList()
            }

            val xmlContent = response.body?.string() ?: return emptyList()
            parseSitemap(xmlContent)
        } catch (e: Exception) {
            android.util.Log.e("FarsiPlexApi", "Error fetching sitemap: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * Parse sitemap XML to extract URLs and lastmod dates
     */
    private fun parseSitemap(xmlContent: String): List<SitemapUrl> {
        val urls = mutableListOf<SitemapUrl>()

        try {
            android.util.Log.d("FarsiPlexApi", "Parsing sitemap XML (${xmlContent.length} chars)")
            val factory = XmlPullParserFactory.newInstance()
            val parser = factory.newPullParser()
            parser.setInput(StringReader(xmlContent))

            var eventType = parser.eventType
            var currentLoc: String? = null
            var currentLastmod: String? = null

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        when (parser.name) {
                            "loc" -> {
                                currentLoc = parser.nextText()
                            }
                            "lastmod" -> {
                                currentLastmod = parser.nextText()
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (parser.name == "url" && currentLoc != null) {
                            urls.add(SitemapUrl(currentLoc, currentLastmod))
                            currentLoc = null
                            currentLastmod = null
                        }
                    }
                }
                eventType = parser.next()
            }
            android.util.Log.d("FarsiPlexApi", "Parsed ${urls.size} URLs from sitemap")
        } catch (e: Exception) {
            android.util.Log.e("FarsiPlexApi", "Error parsing sitemap: ${e.message}", e)
        }

        return urls
    }

    /**
     * Get recent movies from sitemap
     * @param limit Number of recent movies to fetch
     */
    suspend fun getRecentMovies(limit: Int = 20): List<SitemapUrl> {
        val allMovies = fetchSitemap(MOVIES_SITEMAP)
        return allMovies.sortedByDescending { it.lastmod }.take(limit)
    }

    /**
     * Get recent TV shows from sitemap
     * @param limit Number of recent TV shows to fetch
     */
    suspend fun getRecentTvShows(limit: Int = 20): List<SitemapUrl> {
        val allShows = fetchSitemap(TVSHOWS_SITEMAP)
        android.util.Log.d("FarsiPlexApi", "TV Shows sitemap: fetched ${allShows.size} total shows")
        val recent = allShows.sortedByDescending { it.lastmod }.take(limit)
        android.util.Log.d("FarsiPlexApi", "TV Shows sitemap: returning ${recent.size} recent shows (limit=$limit)")
        return recent
    }

    /**
     * Get recent episodes from sitemap
     * @param limit Number of recent episodes to fetch
     */
    suspend fun getRecentEpisodes(limit: Int = 20): List<SitemapUrl> {
        val allEpisodes = fetchSitemap(EPISODES_SITEMAP)
        return allEpisodes.sortedByDescending { it.lastmod }.take(limit)
    }
}
