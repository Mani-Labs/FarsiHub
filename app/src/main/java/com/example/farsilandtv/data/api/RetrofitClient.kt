package com.example.farsilandtv.data.api

import com.example.farsilandtv.FarsilandApp
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.Cache
import okhttp3.CacheControl
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.io.File
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Retrofit client factory for WordPress API
 * Singleton instance with proper configuration
 */
object RetrofitClient {

    private const val BASE_URL = "https://farsiland.com/wp-json/wp/v2/"

    /**
     * User-Agent header to mimic browser requests
     * Some WordPress sites may block requests without proper User-Agent
     */
    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    /**
     * Moshi instance for JSON parsing
     * Configured with Kotlin reflection for data classes
     */
    private val moshi: Moshi by lazy {
        Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
    }

    /**
     * HTTP Cache configuration
     * Cache size: 10MB
     * Cache location: app cache directory
     */
    private val httpCache: Cache by lazy {
        val cacheDir = File(FarsilandApp.instance.cacheDir, "http_cache")
        val cacheSize = 10L * 1024 * 1024 // 10 MB
        Cache(cacheDir, cacheSize)
    }

    /**
     * DNS Cache for faster repeated requests
     * Caches DNS lookups to avoid repeated DNS resolution overhead
     */
    private val dnsCache = object : Dns {
        private val cache = ConcurrentHashMap<String, List<InetAddress>>()

        override fun lookup(hostname: String): List<InetAddress> {
            return cache.getOrPut(hostname) {
                Dns.SYSTEM.lookup(hostname)
            }
        }
    }

    /**
     * Connection Pool for efficient connection reuse
     * 10 idle connections (vs default 5)
     * 2 minute keep-alive (vs default 5 min, optimized for streaming)
     */
    private val connectionPool = ConnectionPool(
        maxIdleConnections = 10,
        keepAliveDuration = 2,
        timeUnit = TimeUnit.MINUTES
    )

    /**
     * Dispatcher for parallel request handling
     * 64 max concurrent requests total
     * 10 max per host (vs default 5) - critical for multi-source app
     */
    private val dispatcher = Dispatcher().apply {
        maxRequests = 64
        maxRequestsPerHost = 10
    }

    /**
     * OkHttpClient with caching, logging and timeouts
     * OPTIMIZED: Connection pooling, HTTP/2, DNS caching (2025-11-10)
     */
    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            // Optimized timeouts for better UX - users don't wait too long for failed requests
            .connectTimeout(20, TimeUnit.SECONDS)  // Reduced from 60s
            .readTimeout(25, TimeUnit.SECONDS)     // Reduced from 30s
            .writeTimeout(25, TimeUnit.SECONDS)     // Reduced from 30s

            // HTTP Cache
            .cache(httpCache)

            // Performance optimizations (2025-11-10)
            .connectionPool(connectionPool)  // 10 idle connections for faster reuse
            .dispatcher(dispatcher)          // 10 requests/host for parallel loading
            .dns(dnsCache)                   // Cache DNS lookups
            .protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))  // Enable HTTP/2

            // Logging interceptor (logs all requests/responses in debug builds)
            .addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BASIC // Changed from BODY to reduce logs
                }
            )

            // Add User-Agent and cache control
            .addNetworkInterceptor { chain ->
                var request = chain.request()

                // Add User-Agent header
                request = request.newBuilder()
                    .header("User-Agent", USER_AGENT)
                    .build()

                android.util.Log.d("HTTP_CACHE", "Network request: ${request.url}")

                val response = chain.proceed(request)

                // Override server's Cache-Control to cache for 1 hour
                response.newBuilder()
                    .removeHeader("Pragma")
                    .removeHeader("Cache-Control")
                    .header("Cache-Control", "public, max-age=3600") // 1 hour
                    .build()
            }

            // Log cache hits/misses and track last network fetch time
            .addInterceptor { chain ->
                val request = chain.request()
                val response = chain.proceed(request)

                val cacheStatus = if (response.cacheResponse != null) {
                    "CACHE HIT"
                } else {
                    "CACHE MISS - Fresh data from network"
                    // Store timestamp of last network fetch
                    updateLastFetchTimestamp()
                }

                android.util.Log.d("HTTP_CACHE", "$cacheStatus: ${request.url}")

                response
            }

            // Offline cache: Use stale cache when no network
            .addInterceptor { chain ->
                var request = chain.request()

                // Check if network is available
                val isNetworkAvailable = try {
                    val connectivityManager = FarsilandApp.instance.getSystemService(
                        android.content.Context.CONNECTIVITY_SERVICE
                    ) as android.net.ConnectivityManager
                    val activeNetwork = connectivityManager.activeNetworkInfo
                    activeNetwork?.isConnectedOrConnecting == true
                } catch (e: Exception) {
                    true // Assume network available if check fails
                }

                // If no network, force cache even if stale (up to 7 days)
                if (!isNetworkAvailable) {
                    request = request.newBuilder()
                        .cacheControl(
                            CacheControl.Builder()
                                .onlyIfCached()
                                .maxStale(7, TimeUnit.DAYS)
                                .build()
                        )
                        .build()
                }

                chain.proceed(request)
            }

            .build()
    }

    /**
     * Retrofit instance for WordPress API
     */
    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
    }

    /**
     * WordPress API service instance
     */
    val wordPressApi: WordPressApiService by lazy {
        retrofit.create(WordPressApiService::class.java)
    }

    /**
     * Get OkHttpClient for direct HTTP requests (used by scraper)
     */
    fun getHttpClient(): OkHttpClient = okHttpClient

    /**
     * Clear HTTP cache
     * Call this to force fresh data on next request
     */
    fun clearCache() {
        try {
            httpCache.evictAll()
            android.util.Log.d("RetrofitClient", "HTTP cache cleared successfully")
        } catch (e: Exception) {
            android.util.Log.e("RetrofitClient", "Error clearing cache", e)
        }
    }

    /**
     * Update the timestamp of last network fetch
     * Called when we get fresh data from the server (CACHE MISS)
     */
    private fun updateLastFetchTimestamp() {
        val prefs = FarsilandApp.instance.getSharedPreferences("app_cache", android.content.Context.MODE_PRIVATE)
        prefs.edit().putLong("last_fetch_time", System.currentTimeMillis()).apply()
        android.util.Log.d("HTTP_CACHE", "Updated last fetch timestamp")
    }

    /**
     * Get the timestamp of last network fetch
     * @return Timestamp in milliseconds, or 0 if never fetched
     */
    fun getLastFetchTimestamp(): Long {
        val prefs = FarsilandApp.instance.getSharedPreferences("app_cache", android.content.Context.MODE_PRIVATE)
        return prefs.getLong("last_fetch_time", 0L)
    }
}
