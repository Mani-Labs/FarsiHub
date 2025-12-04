package com.example.farsilandtv.data.api

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.util.concurrent.TimeUnit

/**
 * IMVBox Authentication Manager
 * Handles login, session management, and authenticated requests for IMVBox
 *
 * Authentication Flow:
 * 1. GET login page to extract CSRF token (_token)
 * 2. POST email/password + _token to /en/members/login
 * 3. Server sets session cookies (laravel_session, XSRF-TOKEN)
 * 4. Use cookies for all subsequent authenticated requests
 */
object IMVBoxAuthManager {
    private const val TAG = "IMVBoxAuth"

    private const val BASE_URL = "https://www.imvbox.com"
    private const val LOGIN_URL = "$BASE_URL/en/members/login"
    private const val LOGOUT_URL = "$BASE_URL/en/members/logout"

    private const val PREFS_NAME = "imvbox_auth_prefs"
    private const val KEY_EMAIL = "imvbox_email"
    private const val KEY_PASSWORD = "imvbox_password"
    private const val KEY_IS_LOGGED_IN = "imvbox_logged_in"
    private const val KEY_SESSION_COOKIES = "imvbox_cookies"

    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

    // In-memory cookie storage
    private val cookieStore = mutableMapOf<String, MutableList<Cookie>>()

    // Custom CookieJar for OkHttp
    private val cookieJar = object : CookieJar {
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            val host = url.host
            cookieStore.getOrPut(host) { mutableListOf() }.apply {
                // Remove existing cookies with same name
                val newCookieNames = cookies.map { it.name }.toSet()
                removeAll { it.name in newCookieNames }
                addAll(cookies)
            }
            Log.d(TAG, "Saved ${cookies.size} cookies for $host: ${cookies.map { it.name }}")
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            val host = url.host
            val cookies = cookieStore[host]?.filter { cookie ->
                // Check if cookie is still valid and matches the path
                !cookie.expiresAt.let { it < System.currentTimeMillis() } &&
                url.encodedPath.startsWith(cookie.path)
            } ?: emptyList()
            Log.d(TAG, "Loading ${cookies.size} cookies for $host")
            return cookies
        }
    }

    // Authenticated OkHttpClient with cookie support
    private val authClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    /**
     * Get encrypted SharedPreferences for secure credential storage
     */
    private fun getEncryptedPrefs(context: Context): SharedPreferences {
        return try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create encrypted prefs, falling back to regular prefs", e)
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    /**
     * Save IMVBox credentials securely
     */
    fun saveCredentials(context: Context, email: String, password: String) {
        val prefs = getEncryptedPrefs(context)
        prefs.edit()
            .putString(KEY_EMAIL, email)
            .putString(KEY_PASSWORD, password)
            .apply()
        Log.d(TAG, "Credentials saved for: $email")
    }

    /**
     * Get saved email
     */
    fun getSavedEmail(context: Context): String? {
        return getEncryptedPrefs(context).getString(KEY_EMAIL, null)
    }

    /**
     * Check if credentials are saved
     */
    fun hasCredentials(context: Context): Boolean {
        val prefs = getEncryptedPrefs(context)
        return !prefs.getString(KEY_EMAIL, null).isNullOrBlank() &&
               !prefs.getString(KEY_PASSWORD, null).isNullOrBlank()
    }

    /**
     * Check if currently logged in (session valid)
     */
    fun isLoggedIn(context: Context): Boolean {
        return getEncryptedPrefs(context).getBoolean(KEY_IS_LOGGED_IN, false)
    }

    /**
     * Clear saved credentials and session
     */
    fun clearCredentials(context: Context) {
        val prefs = getEncryptedPrefs(context)
        prefs.edit()
            .remove(KEY_EMAIL)
            .remove(KEY_PASSWORD)
            .putBoolean(KEY_IS_LOGGED_IN, false)
            .remove(KEY_SESSION_COOKIES)
            .apply()
        cookieStore.clear()
        Log.d(TAG, "Credentials and session cleared")
    }

    /**
     * Login to IMVBox
     * @return LoginResult with success status and optional error message
     */
    suspend fun login(context: Context, email: String, password: String): LoginResult = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Attempting login for: $email")

            // Step 1: Get login page to extract CSRF token
            val loginPageRequest = Request.Builder()
                .url(LOGIN_URL)
                .header("User-Agent", USER_AGENT)
                .get()
                .build()

            val loginPageResponse = authClient.newCall(loginPageRequest).execute()
            if (!loginPageResponse.isSuccessful) {
                return@withContext LoginResult.Error("Failed to load login page: ${loginPageResponse.code}")
            }

            val loginPageHtml = loginPageResponse.body?.string() ?: ""

            // Extract CSRF token from hidden input field
            val doc = Jsoup.parse(loginPageHtml)
            val csrfToken = doc.select("input[name=_token]").attr("value")

            if (csrfToken.isBlank()) {
                Log.w(TAG, "No CSRF token found, proceeding without it")
            } else {
                Log.d(TAG, "Found CSRF token: ${csrfToken.take(10)}...")
            }

            // Step 2: Submit login form
            val formBody = FormBody.Builder()
                .add("email", email)
                .add("password", password)

            if (csrfToken.isNotBlank()) {
                formBody.add("_token", csrfToken)
            }

            val loginRequest = Request.Builder()
                .url(LOGIN_URL)
                .header("User-Agent", USER_AGENT)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Referer", LOGIN_URL)
                .header("Origin", BASE_URL)
                .post(formBody.build())
                .build()

            val loginResponse = authClient.newCall(loginRequest).execute()
            val responseBody = loginResponse.body?.string() ?: ""

            // Check for successful login
            // Success: Redirected to home page or responseBody contains logged-in indicators
            val isSuccess = loginResponse.isSuccessful &&
                    (loginResponse.request.url.toString() == "$BASE_URL/en" ||
                     loginResponse.request.url.toString() == "$BASE_URL/en/" ||
                     responseBody.contains("Logout", ignoreCase = true) ||
                     responseBody.contains("My Account", ignoreCase = true))

            if (isSuccess) {
                // Save credentials and session state
                saveCredentials(context, email, password)
                getEncryptedPrefs(context).edit()
                    .putBoolean(KEY_IS_LOGGED_IN, true)
                    .apply()

                Log.d(TAG, "Login successful!")
                return@withContext LoginResult.Success
            } else {
                // Check for specific error messages
                val errorMessage = when {
                    responseBody.contains("Invalid credentials", ignoreCase = true) ->
                        "Invalid email or password"
                    responseBody.contains("Too many attempts", ignoreCase = true) ->
                        "Too many login attempts. Please wait and try again."
                    responseBody.contains("Account suspended", ignoreCase = true) ->
                        "Account suspended"
                    else -> "Login failed. Please check your credentials."
                }
                Log.e(TAG, "Login failed: $errorMessage")
                return@withContext LoginResult.Error(errorMessage)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Login exception", e)
            return@withContext LoginResult.Error("Network error: ${e.message}")
        }
    }

    /**
     * Login with saved credentials
     */
    suspend fun loginWithSavedCredentials(context: Context): LoginResult = withContext(Dispatchers.IO) {
        val prefs = getEncryptedPrefs(context)
        val email = prefs.getString(KEY_EMAIL, null)
        val password = prefs.getString(KEY_PASSWORD, null)

        if (email.isNullOrBlank() || password.isNullOrBlank()) {
            return@withContext LoginResult.Error("No saved credentials")
        }

        return@withContext login(context, email, password)
    }

    /**
     * Logout from IMVBox
     */
    suspend fun logout(context: Context): Boolean = withContext(Dispatchers.IO) {
        try {
            // Call logout URL to invalidate server session
            val logoutRequest = Request.Builder()
                .url(LOGOUT_URL)
                .header("User-Agent", USER_AGENT)
                .get()
                .build()

            authClient.newCall(logoutRequest).execute()

            // Clear local session state (but keep credentials for re-login)
            getEncryptedPrefs(context).edit()
                .putBoolean(KEY_IS_LOGGED_IN, false)
                .apply()
            cookieStore.clear()

            Log.d(TAG, "Logout successful")
            return@withContext true
        } catch (e: Exception) {
            Log.e(TAG, "Logout failed", e)
            return@withContext false
        }
    }

    /**
     * Get authenticated OkHttpClient for making requests to IMVBox
     * Use this client for all IMVBox API calls that require authentication
     */
    fun getAuthenticatedClient(): OkHttpClient = authClient

    /**
     * Get cookies for a specific domain as a list of name=value pairs.
     * Used to sync cookies to WebView's CookieManager.
     */
    fun getCookiesForDomain(domain: String): List<Pair<String, String>> {
        val cookies = cookieStore[domain] ?: return emptyList()
        return cookies.map { it.name to "${it.name}=${it.value}" }
    }

    /**
     * Get all IMVBox cookies formatted for WebView CookieManager.
     * Returns list of cookie strings in format "name=value; path=/; domain=.imvbox.com"
     */
    fun getWebViewCookies(): List<String> {
        val result = mutableListOf<String>()
        cookieStore["www.imvbox.com"]?.forEach { cookie ->
            val cookieString = buildString {
                append("${cookie.name}=${cookie.value}")
                append("; path=${cookie.path}")
                append("; domain=.imvbox.com")
                if (cookie.secure) append("; secure")
                if (cookie.httpOnly) append("; httponly")
            }
            result.add(cookieString)
            Log.d(TAG, "WebView cookie: ${cookie.name}=${cookie.value.take(20)}...")
        }
        return result
    }

    /**
     * Make an authenticated GET request to IMVBox
     */
    suspend fun authenticatedGet(url: String): String? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .get()
                .build()

            val response = authClient.newCall(request).execute()
            if (response.isSuccessful) {
                return@withContext response.body?.string()
            } else {
                Log.e(TAG, "Authenticated GET failed: ${response.code}")
                return@withContext null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Authenticated GET exception", e)
            return@withContext null
        }
    }

    /**
     * Ensure session is valid, re-login if needed
     * Call this before making authenticated requests
     */
    suspend fun ensureAuthenticated(context: Context): Boolean = withContext(Dispatchers.IO) {
        if (isLoggedIn(context)) {
            // Verify session by checking a protected page
            try {
                val response = authenticatedGet("$BASE_URL/en/my-account")
                if (response != null && response.contains("My Account", ignoreCase = true)) {
                    Log.d(TAG, "Session still valid")
                    return@withContext true
                }
            } catch (e: Exception) {
                Log.w(TAG, "Session check failed, will re-login")
            }
        }

        // Session expired or invalid, try to re-login
        if (hasCredentials(context)) {
            val result = loginWithSavedCredentials(context)
            return@withContext result is LoginResult.Success
        }

        return@withContext false
    }

    /**
     * Login result sealed class
     */
    sealed class LoginResult {
        object Success : LoginResult()
        data class Error(val message: String) : LoginResult()
    }
}
