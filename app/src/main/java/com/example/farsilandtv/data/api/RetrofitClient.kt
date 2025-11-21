package com.example.farsilandtv.data.api

import android.content.Context
import com.example.farsilandtv.FarsilandApp
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.Cache
import okhttp3.CacheControl
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.io.File
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
     *
     * AUDIT FIX #8: Updated to Chrome 131 (January 2025)
     * Previous: Chrome 120 (December 2023) - outdated, may be flagged by anti-bot systems
     */
    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

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
     *
     * AUDIT FIX C1.3: Safe initialization without Application.instance dependency
     * Creates cache in temp directory if Application not ready (prevents startup crashes)
     */
    @Volatile
    private var httpCache: Cache? = null

    /**
     * EXTERNAL AUDIT FIX M1: Return nullable Cache instead of throwing exception
     * Allows RetrofitClient to work even if accessed before Application.onCreate()
     */
    private fun getOrCreateCache(context: android.content.Context? = null): Cache? {
        httpCache?.let { return it }

        synchronized(this) {
            httpCache?.let { return it }

            // Try to get context from Application instance first
            val appContext = context?.applicationContext
                ?: FarsilandApp.instance?.applicationContext

            // EXTERNAL AUDIT FIX M1: Safe fallback instead of crash
            // Issue: If RetrofitClient accessed before Application.onCreate(), crash occurs
            // Solution: Return null cache, OkHttp works fine without cache (just slower)
            if (appContext == null) {
                android.util.Log.w("RetrofitClient", "Application context not available, HTTP cache disabled")
                android.util.Log.w("RetrofitClient", "This can happen if RetrofitClient is accessed before Application.onCreate()")
                android.util.Log.w("RetrofitClient", "Network requests will work but without caching")
                return null
            }

            // Always use app cache directory (Android 10+ compatible)
            val cacheDir = File(appContext.cacheDir, "http_cache")
            val cacheSize = 10L * 1024 * 1024 // 10 MB
            httpCache = Cache(cacheDir, cacheSize)

            return httpCache
        }
    }

    /**
     * DNS Cache REMOVED - Permanent caching breaks CDN IP rotation
     * Android OS handles DNS caching correctly with proper TTL
     */

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
     * AUDIT FIX C1.3: Safe cache initialization
     * EXTERNAL AUDIT FIX H1: Robust handling when accessed before Application ready
     */
    private val okHttpClient: OkHttpClient by lazy {
        val cache = try {
            getOrCreateCache()
        } catch (e: Exception) {
            android.util.Log.e("RetrofitClient", "Failed to initialize cache, continuing without it: ${e.message}")
            null
        }

        if (cache == null) {
            android.util.Log.w("RetrofitClient", "OkHttpClient initialized WITHOUT caching")
            android.util.Log.w("RetrofitClient", "This is safe but reduces performance")
        }

        OkHttpClient.Builder()
            // Optimized timeouts for better UX - users don't wait too long for failed requests
            .connectTimeout(20, TimeUnit.SECONDS)  // Reduced from 60s
            .readTimeout(25, TimeUnit.SECONDS)     // Reduced from 30s
            .writeTimeout(25, TimeUnit.SECONDS)     // Reduced from 30s

            // HTTP Cache (null is safe - OkHttp works fine without cache)
            .cache(cache)

            // Performance optimizations (2025-11-10)
            .connectionPool(connectionPool)  // 10 idle connections for faster reuse
            .dispatcher(dispatcher)          // 10 requests/host for parallel loading
            // DNS cache removed - Android OS handles this correctly with TTL
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

                // EXTERNAL AUDIT FIX H1 CORRECTED (2025-11-21): Selective Cache Override
                // Issue: Forcing max-age=600 breaks when server sends signed URLs (expire < 5 min)
                // Solution: ONLY respect server headers for video/player endpoints (signed URLs)
                //           KEEP cache override for content endpoints (movies/series lists)
                //
                // Why this works:
                // 1. Content lists (/movies, /tvshows) don't expire - safe to cache 10 min
                // 2. Video URLs (/dooplayer, /player) use signed URLs - respect server's short TTL
                // 3. WordPress sends no-cache for most endpoints - need override for content
                val url = request.url.toString()
                val isVideoOrPlayerEndpoint = url.contains("/wp-json/dooplayer/") ||
                                             url.contains("/player") ||
                                             url.contains("/video/") ||
                                             url.contains(".mp4") ||
                                             url.contains("/stream/")

                if (isVideoOrPlayerEndpoint) {
                    // Respect server's Cache-Control for video/player endpoints
                    // These may have signed URLs that expire quickly
                    android.util.Log.d("HTTP_CACHE", "Video/player endpoint - respecting server cache headers")
                    response
                } else {
                    // Override server's Cache-Control for content endpoints
                    // WordPress sends no-cache, but content lists are safe to cache
                    android.util.Log.d("HTTP_CACHE", "Content endpoint - applying 10min cache")
                    response.newBuilder()
                        .removeHeader("Pragma")
                        .removeHeader("Cache-Control")
                        .header("Cache-Control", "public, max-age=600") // 10 minutes
                        .build()
                }
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

                // AUDIT FIX #3: Safe access to Application instance with null check
                // CRITICAL FIX: Default to offline mode when app instance unavailable
                val isNetworkAvailable = try {
                    val appInstance = FarsilandApp.instance
                    if (appInstance == null) {
                        android.util.Log.w("RetrofitClient", "Application instance not available, forcing offline mode (use cache)")
                        false // CRITICAL FIX: Safer default - use cache when uncertain
                    } else {
                        val connectivityManager = appInstance.getSystemService(
                            android.content.Context.CONNECTIVITY_SERVICE
                        ) as android.net.ConnectivityManager

                        // AUDIT FIX #19: Removed API < 23 fallback (dead code, minSdk = 28)
                        // Use NetworkCapabilities API (available since API 23, minSdk = 28)
                        val activeNetwork = connectivityManager.activeNetwork
                        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
                        capabilities?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
                    }
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
     * EXTERNAL AUDIT FIX H2 (2025-11-21): Eagerly initialize cache on background thread
     * Called from FarsilandApp.onCreate() to prevent main thread I/O
     *
     * This ensures the cache directory is created before first API call
     * Prevents UI jank (20-80ms) when okHttpClient is lazily initialized
     */
    fun initializeCache(context: Context) {
        getOrCreateCache(context)
    }

    /**
     * Clear HTTP cache
     * Call this to force fresh data on next request
     * AUDIT FIX C1.3: Safe null handling
     */
    fun clearCache() {
        try {
            httpCache?.evictAll()
            android.util.Log.d("RetrofitClient", "HTTP cache cleared successfully")
        } catch (e: Exception) {
            android.util.Log.e("RetrofitClient", "Error clearing cache", e)
        }
    }

    /**
     * Update the timestamp of last network fetch
     * Called when we get fresh data from the server (CACHE MISS)
     *
     * AUDIT FIX #3: Safe access with null check
     */
    private fun updateLastFetchTimestamp() {
        try {
            val appInstance = FarsilandApp.instance
            if (appInstance == null) {
                android.util.Log.w("RetrofitClient", "Cannot update fetch timestamp - Application instance not available")
                return
            }

            val prefs = appInstance.getSharedPreferences("app_cache", android.content.Context.MODE_PRIVATE)
            prefs.edit().putLong("last_fetch_time", System.currentTimeMillis()).apply()
            android.util.Log.d("HTTP_CACHE", "Updated last fetch timestamp")
        } catch (e: Exception) {
            android.util.Log.e("RetrofitClient", "Error updating fetch timestamp: ${e.message}")
        }
    }

    /**
     * Get the timestamp of last network fetch
     * @return Timestamp in milliseconds, or 0 if never fetched
     *
     * AUDIT FIX #3: Safe access with null check
     */
    fun getLastFetchTimestamp(): Long {
        return try {
            val appInstance = FarsilandApp.instance
            if (appInstance == null) {
                android.util.Log.w("RetrofitClient", "Cannot get fetch timestamp - Application instance not available")
                return 0L
            }

            val prefs = appInstance.getSharedPreferences("app_cache", android.content.Context.MODE_PRIVATE)
            prefs.getLong("last_fetch_time", 0L)
        } catch (e: Exception) {
            android.util.Log.e("RetrofitClient", "Error getting fetch timestamp: ${e.message}")
            0L
        }
    }
}
