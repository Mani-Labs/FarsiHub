package com.example.farsilandtv.utils

import android.util.Log
import java.net.URL

/**
 * Security utility for URL validation
 *
 * Issue M9: Enforce HTTPS-only URLs to prevent man-in-the-middle attacks
 *
 * Features:
 * - HTTPS enforcement for all content URLs
 * - Domain whitelist validation
 * - HTTP -> HTTPS normalization (when safe)
 * - Rejection of suspicious URLs
 *
 * Usage:
 * ```
 * if (!SecureUrlValidator.isSecureUrl(url)) {
 *     throw SecurityException("Only HTTPS URLs are allowed")
 * }
 * ```
 */
object SecureUrlValidator {

    private const val TAG = "SecureUrlValidator"

    /**
     * AUDIT FIX C1: Use RemoteConfig for trusted domains
     * Allows updating domains without APK releases
     *
     * Before: Hardcoded setOf(...) - required APK update for new domains
     * After: RemoteConfig.trustedDomains - updatable via Firebase Remote Config
     */
    private val TRUSTED_DOMAINS: Set<String>
        get() = RemoteConfig.trustedDomains

    /**
     * AUDIT FIX M3.1: Whitelist of trusted domains that only support HTTP
     * Some Iranian CDNs or private servers may not have valid SSL certificates
     * These domains are allowed to use HTTP as a fallback
     */
    private val HTTP_ALLOWED_DOMAINS: Set<String> = setOf(
        // Add specific HTTP-only trusted domains here as needed
        // Example: "cdn.example.ir", "legacy-server.example.com"
    )

    /**
     * Check if URL uses HTTPS protocol
     *
     * @param url URL to validate
     * @return true if URL starts with https://, false otherwise
     */
    fun isSecureUrl(url: String): Boolean {
        return url.startsWith("https://", ignoreCase = true)
    }

    /**
     * Check if URL is from a trusted domain
     *
     * @param url URL to validate
     * @return true if URL is from a whitelisted domain, false otherwise
     */
    fun isTrustedDomain(url: String): Boolean {
        return try {
            val parsedUrl = URL(url)
            val host = parsedUrl.host.lowercase()

            // Check exact match or subdomain match
            TRUSTED_DOMAINS.any { trustedDomain ->
                host == trustedDomain || host.endsWith(".$trustedDomain")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Invalid URL format: $url", e)
            false
        }
    }

    /**
     * Validate URL for security (HTTPS + trusted domain)
     *
     * @param url URL to validate
     * @param throwOnFailure If true, throws SecurityException on validation failure
     * @return true if URL is secure and trusted, false otherwise
     * @throws SecurityException if throwOnFailure is true and validation fails
     */
    fun validateUrl(url: String, throwOnFailure: Boolean = false): Boolean {
        // Check HTTPS
        if (!isSecureUrl(url)) {
            val message = "Security: Cleartext HTTP traffic not permitted: $url"
            Log.w(TAG, message)
            if (throwOnFailure) {
                throw SecurityException(message)
            }
            return false
        }

        // Check trusted domain
        if (!isTrustedDomain(url)) {
            val message = "Security: URL from untrusted domain: $url"
            Log.w(TAG, message)
            if (throwOnFailure) {
                throw SecurityException(message)
            }
            return false
        }

        return true
    }

    /**
     * AUDIT FIX M3.1: Check if HTTP is allowed for this domain
     * Some trusted domains may only support HTTP (no valid SSL cert)
     *
     * @param url URL to check
     * @return true if HTTP is allowed for this domain
     */
    fun isHttpAllowed(url: String): Boolean {
        return try {
            val parsedUrl = URL(url)
            val host = parsedUrl.host.lowercase()

            // Check if domain is in HTTP whitelist
            HTTP_ALLOWED_DOMAINS.any { allowedDomain: String ->
                host == allowedDomain || host.endsWith(".$allowedDomain")
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Normalize HTTP URL to HTTPS (safe upgrade)
     *
     * AUDIT FIX M3.1: Added fallback for HTTP-only trusted domains
     * Only upgrades URLs from trusted domains. Unknown domains are rejected.
     *
     * @param url URL to normalize
     * @return HTTPS URL if upgrade is safe, HTTP URL if domain is in HTTP whitelist, null if URL should be rejected
     */
    fun normalizeToHttps(url: String): String? {
        // Already HTTPS - return as-is
        if (isSecureUrl(url)) {
            return url
        }

        // HTTP URL - check if from trusted domain before upgrading
        if (!url.startsWith("http://", ignoreCase = true)) {
            Log.w(TAG, "Invalid URL scheme: $url")
            return null
        }

        // AUDIT FIX M3.1: Check if HTTP is explicitly allowed for this domain
        if (isHttpAllowed(url)) {
            Log.d(TAG, "HTTP allowed for whitelisted domain: $url")
            return url  // Return HTTP URL as-is
        }

        // Upgrade to HTTPS
        val httpsUrl = url.replaceFirst("http://", "https://", ignoreCase = true)

        // Verify upgraded URL is from trusted domain
        if (!isTrustedDomain(httpsUrl)) {
            Log.w(TAG, "Cannot upgrade HTTP URL from untrusted domain: $url")
            return null
        }

        Log.d(TAG, "Upgraded HTTP to HTTPS: $url -> $httpsUrl")
        return httpsUrl
    }

    /**
     * Filter list of URLs to only include secure URLs
     *
     * @param urls List of URLs to filter
     * @param normalizeHttp If true, attempts to upgrade HTTP URLs to HTTPS
     * @return List of validated HTTPS URLs
     */
    fun filterSecureUrls(urls: List<String>, normalizeHttp: Boolean = true): List<String> {
        return urls.mapNotNull { url ->
            when {
                isSecureUrl(url) && isTrustedDomain(url) -> url
                normalizeHttp -> normalizeToHttps(url)
                else -> {
                    Log.w(TAG, "Filtered out insecure URL: $url")
                    null
                }
            }
        }
    }

    /**
     * Get security status message for URL
     *
     * @param url URL to check
     * @return Human-readable security status message
     */
    fun getSecurityStatus(url: String): String {
        return when {
            !isSecureUrl(url) -> "❌ Insecure: HTTP traffic not permitted"
            !isTrustedDomain(url) -> "⚠️ Untrusted domain"
            else -> "✅ Secure: HTTPS from trusted domain"
        }
    }

    /**
     * Log URL security validation result
     *
     * @param url URL being validated
     * @param context Operation context (e.g., "Video playback", "Scraping")
     */
    fun logValidation(url: String, context: String) {
        val status = getSecurityStatus(url)
        Log.d(TAG, "[$context] $status: $url")
    }
}
