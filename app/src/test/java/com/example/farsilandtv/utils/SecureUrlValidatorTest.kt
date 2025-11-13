package com.example.farsilandtv.utils

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for SecureUrlValidator
 *
 * Issue M9: HTTPS Enforcement for Security
 *
 * Test Coverage:
 * - HTTPS URL validation
 * - HTTP rejection (cleartext traffic prevention)
 * - Trusted domain whitelist verification
 * - HTTP to HTTPS normalization (safe upgrade)
 * - URL filtering and security status
 * - OWASP Mobile M3 compliance (Insecure Communication)
 *
 * Priority: HIGH (Security vulnerability)
 */
class SecureUrlValidatorTest {

    // ========== HTTPS Validation Tests ==========

    @Test
    fun `isSecureUrl returns true for HTTPS URLs`() {
        // Arrange
        val httpsUrls = listOf(
            "https://farsiland.com/movie/test",
            "https://d1.flnd.buzz/video.m3u8",
            "HTTPS://farsiland.com/test", // Case insensitive
            "https://subdomain.farsiland.com/path"
        )

        // Act & Assert
        httpsUrls.forEach { url ->
            assertTrue(
                actual = SecureUrlValidator.isSecureUrl(url),
                message = "Expected HTTPS URL to be secure: $url"
            )
        }
    }

    @Test
    fun `isSecureUrl returns false for HTTP URLs`() {
        // Arrange
        val httpUrls = listOf(
            "http://farsiland.com/movie/test",
            "http://malicious.com/video",
            "HTTP://farsiland.com/test" // Case insensitive
        )

        // Act & Assert
        httpUrls.forEach { url ->
            assertFalse(
                actual = SecureUrlValidator.isSecureUrl(url),
                message = "Expected HTTP URL to be rejected: $url"
            )
        }
    }

    @Test
    fun `isSecureUrl returns false for invalid protocols`() {
        // Arrange
        val invalidUrls = listOf(
            "ftp://farsiland.com/file",
            "file:///local/path",
            "javascript:alert(1)",
            "data:text/html,<script>alert(1)</script>",
            "//farsiland.com/path" // Protocol-relative URL
        )

        // Act & Assert
        invalidUrls.forEach { url ->
            assertFalse(
                actual = SecureUrlValidator.isSecureUrl(url),
                message = "Expected invalid protocol to be rejected: $url"
            )
        }
    }

    // ========== Trusted Domain Tests ==========

    @Test
    fun `isTrustedDomain returns true for whitelisted domains`() {
        // Arrange
        val trustedUrls = listOf(
            "https://farsiland.com/movie",
            "https://farsiplex.com/series",
            "https://flnd.buzz/video",
            "https://d1.flnd.buzz/video.m3u8",
            "https://d2.flnd.buzz/video.m3u8",
            "https://namakade.com/content",
            "https://wp.farsiland.com/api",
            "https://negahestan.com/stream",
            "https://media.negahestan.com/video.mp4"
        )

        // Act & Assert
        trustedUrls.forEach { url ->
            assertTrue(
                actual = SecureUrlValidator.isTrustedDomain(url),
                message = "Expected trusted domain: $url"
            )
        }
    }

    @Test
    fun `isTrustedDomain returns true for subdomains of trusted domains`() {
        // Arrange
        val subdomainUrls = listOf(
            "https://cdn.farsiland.com/video",
            "https://api.farsiplex.com/data",
            "https://static.flnd.buzz/image.jpg"
        )

        // Act & Assert
        subdomainUrls.forEach { url ->
            assertTrue(
                actual = SecureUrlValidator.isTrustedDomain(url),
                message = "Expected subdomain to be trusted: $url"
            )
        }
    }

    @Test
    fun `isTrustedDomain returns false for untrusted domains`() {
        // Arrange
        val untrustedUrls = listOf(
            "https://malicious.com/video",
            "https://phishing-farsiland.com/fake",
            "https://evil.com/steal-data",
            "https://random-site.net/content"
        )

        // Act & Assert
        untrustedUrls.forEach { url ->
            assertFalse(
                actual = SecureUrlValidator.isTrustedDomain(url),
                message = "Expected untrusted domain to be rejected: $url"
            )
        }
    }

    @Test
    fun `isTrustedDomain returns false for invalid URL format`() {
        // Arrange
        val invalidUrls = listOf(
            "not-a-url",
            "javascript:alert(1)",
            "just some text",
            ""
        )

        // Act & Assert
        invalidUrls.forEach { url ->
            assertFalse(
                actual = SecureUrlValidator.isTrustedDomain(url),
                message = "Expected invalid URL to be rejected: $url"
            )
        }
    }

    // ========== Full Validation Tests ==========

    @Test
    fun `validateUrl returns true for secure trusted URLs`() {
        // Arrange
        val validUrls = listOf(
            "https://farsiland.com/movie/123",
            "https://d1.flnd.buzz/video.m3u8",
            "https://cdn.farsiland.com/poster.jpg"
        )

        // Act & Assert
        validUrls.forEach { url ->
            assertTrue(
                actual = SecureUrlValidator.validateUrl(url, throwOnFailure = false),
                message = "Expected URL to pass validation: $url"
            )
        }
    }

    @Test
    fun `validateUrl returns false for HTTP URLs`() {
        // Arrange
        val httpUrl = "http://farsiland.com/movie/123"

        // Act
        val result = SecureUrlValidator.validateUrl(httpUrl, throwOnFailure = false)

        // Assert
        assertFalse(
            actual = result,
            message = "Expected HTTP URL to fail validation"
        )
    }

    @Test
    fun `validateUrl returns false for untrusted domains`() {
        // Arrange
        val untrustedUrl = "https://malicious.com/steal-data"

        // Act
        val result = SecureUrlValidator.validateUrl(untrustedUrl, throwOnFailure = false)

        // Assert
        assertFalse(
            actual = result,
            message = "Expected untrusted domain to fail validation"
        )
    }

    @Test
    fun `validateUrl throws SecurityException when throwOnFailure is true for HTTP`() {
        // Arrange
        val httpUrl = "http://farsiland.com/movie"

        // Act & Assert
        try {
            SecureUrlValidator.validateUrl(httpUrl, throwOnFailure = true)
            throw AssertionError("Expected SecurityException to be thrown")
        } catch (e: SecurityException) {
            assertTrue(
                actual = e.message?.contains("Cleartext HTTP") == true,
                message = "Expected exception message to mention cleartext HTTP"
            )
        }
    }

    @Test
    fun `validateUrl throws SecurityException when throwOnFailure is true for untrusted domain`() {
        // Arrange
        val untrustedUrl = "https://malicious.com/video"

        // Act & Assert
        try {
            SecureUrlValidator.validateUrl(untrustedUrl, throwOnFailure = true)
            throw AssertionError("Expected SecurityException to be thrown")
        } catch (e: SecurityException) {
            assertTrue(
                actual = e.message?.contains("untrusted domain") == true,
                message = "Expected exception message to mention untrusted domain"
            )
        }
    }

    // ========== HTTP to HTTPS Normalization Tests ==========

    @Test
    fun `normalizeToHttps returns HTTPS URL unchanged`() {
        // Arrange
        val httpsUrl = "https://farsiland.com/movie/123"

        // Act
        val result = SecureUrlValidator.normalizeToHttps(httpsUrl)

        // Assert
        assertEquals(
            expected = httpsUrl,
            actual = result,
            message = "HTTPS URL should be returned unchanged"
        )
    }

    @Test
    fun `normalizeToHttps upgrades HTTP to HTTPS for trusted domains`() {
        // Arrange
        val httpUrl = "http://farsiland.com/movie/123"
        val expectedHttpsUrl = "https://farsiland.com/movie/123"

        // Act
        val result = SecureUrlValidator.normalizeToHttps(httpUrl)

        // Assert
        assertEquals(
            expected = expectedHttpsUrl,
            actual = result,
            message = "HTTP URL from trusted domain should be upgraded to HTTPS"
        )
    }

    @Test
    fun `normalizeToHttps returns null for HTTP URLs from untrusted domains`() {
        // Arrange
        val untrustedHttpUrl = "http://malicious.com/steal-data"

        // Act
        val result = SecureUrlValidator.normalizeToHttps(untrustedHttpUrl)

        // Assert
        assertNull(
            actual = result,
            message = "HTTP URL from untrusted domain should be rejected"
        )
    }

    @Test
    fun `normalizeToHttps returns null for invalid URL schemes`() {
        // Arrange
        val invalidUrls = listOf(
            "ftp://farsiland.com/file",
            "javascript:alert(1)",
            "not-a-url"
        )

        // Act & Assert
        invalidUrls.forEach { url ->
            assertNull(
                actual = SecureUrlValidator.normalizeToHttps(url),
                message = "Invalid URL scheme should be rejected: $url"
            )
        }
    }

    @Test
    fun `normalizeToHttps is case insensitive for HTTP protocol`() {
        // Arrange
        val mixedCaseUrls = listOf(
            "HTTP://farsiland.com/test",
            "Http://farsiland.com/test",
            "HtTp://farsiland.com/test"
        )
        val expectedResult = "https://farsiland.com/test"

        // Act & Assert
        mixedCaseUrls.forEach { url ->
            assertEquals(
                expected = expectedResult,
                actual = SecureUrlValidator.normalizeToHttps(url),
                message = "Case insensitive upgrade should work for: $url"
            )
        }
    }

    // ========== URL Filtering Tests ==========

    @Test
    fun `filterSecureUrls keeps only valid HTTPS URLs`() {
        // Arrange
        val mixedUrls = listOf(
            "https://farsiland.com/movie/1", // Valid HTTPS
            "http://farsiland.com/movie/2",  // HTTP (will be upgraded)
            "https://malicious.com/video",   // Untrusted domain
            "https://d1.flnd.buzz/video.m3u8", // Valid HTTPS
            "http://evil.com/steal-data"     // HTTP + untrusted
        )

        // Act
        val result = SecureUrlValidator.filterSecureUrls(mixedUrls, normalizeHttp = true)

        // Assert
        assertEquals(
            expected = 3,
            actual = result.size,
            message = "Expected 3 valid URLs (2 HTTPS + 1 upgraded HTTP)"
        )
        assertTrue(
            actual = result.contains("https://farsiland.com/movie/1"),
            message = "Expected valid HTTPS URL to be included"
        )
        assertTrue(
            actual = result.contains("https://farsiland.com/movie/2"),
            message = "Expected upgraded HTTP URL to be included"
        )
        assertTrue(
            actual = result.contains("https://d1.flnd.buzz/video.m3u8"),
            message = "Expected valid HTTPS URL to be included"
        )
        assertFalse(
            actual = result.contains("https://malicious.com/video"),
            message = "Expected untrusted domain to be filtered out"
        )
    }

    @Test
    fun `filterSecureUrls rejects HTTP when normalizeHttp is false`() {
        // Arrange
        val mixedUrls = listOf(
            "https://farsiland.com/movie/1", // Valid HTTPS
            "http://farsiland.com/movie/2"   // HTTP (will NOT be upgraded)
        )

        // Act
        val result = SecureUrlValidator.filterSecureUrls(mixedUrls, normalizeHttp = false)

        // Assert
        assertEquals(
            expected = 1,
            actual = result.size,
            message = "Expected only 1 URL when HTTP normalization is disabled"
        )
        assertEquals(
            expected = "https://farsiland.com/movie/1",
            actual = result.first(),
            message = "Expected only the HTTPS URL to be included"
        )
    }

    @Test
    fun `filterSecureUrls returns empty list for all invalid URLs`() {
        // Arrange
        val invalidUrls = listOf(
            "http://malicious.com/video",
            "ftp://farsiland.com/file",
            "javascript:alert(1)"
        )

        // Act
        val result = SecureUrlValidator.filterSecureUrls(invalidUrls, normalizeHttp = true)

        // Assert
        assertTrue(
            actual = result.isEmpty(),
            message = "Expected empty list when all URLs are invalid"
        )
    }

    // ========== Security Status Tests ==========

    @Test
    fun `getSecurityStatus returns correct status for secure URLs`() {
        // Arrange
        val secureUrl = "https://farsiland.com/movie/123"

        // Act
        val status = SecureUrlValidator.getSecurityStatus(secureUrl)

        // Assert
        assertTrue(
            actual = status.contains("Secure") || status.contains("✅"),
            message = "Expected security status to indicate secure URL"
        )
    }

    @Test
    fun `getSecurityStatus returns correct status for HTTP URLs`() {
        // Arrange
        val httpUrl = "http://farsiland.com/movie/123"

        // Act
        val status = SecureUrlValidator.getSecurityStatus(httpUrl)

        // Assert
        assertTrue(
            actual = status.contains("Insecure") || status.contains("HTTP") || status.contains("❌"),
            message = "Expected security status to indicate insecure HTTP"
        )
    }

    @Test
    fun `getSecurityStatus returns correct status for untrusted domains`() {
        // Arrange
        val untrustedUrl = "https://malicious.com/video"

        // Act
        val status = SecureUrlValidator.getSecurityStatus(untrustedUrl)

        // Assert
        assertTrue(
            actual = status.contains("Untrusted") || status.contains("⚠️"),
            message = "Expected security status to indicate untrusted domain"
        )
    }

    // ========== Edge Cases ==========

    @Test
    fun `handles empty string URL gracefully`() {
        // Arrange
        val emptyUrl = ""

        // Act & Assert
        assertFalse(SecureUrlValidator.isSecureUrl(emptyUrl))
        assertFalse(SecureUrlValidator.isTrustedDomain(emptyUrl))
        assertNull(SecureUrlValidator.normalizeToHttps(emptyUrl))
    }

    @Test
    fun `handles URL with query parameters`() {
        // Arrange
        val urlWithParams = "https://farsiland.com/movie?id=123&quality=1080p"

        // Act & Assert
        assertTrue(
            actual = SecureUrlValidator.isSecureUrl(urlWithParams),
            message = "Expected HTTPS URL with query params to be valid"
        )
        assertTrue(
            actual = SecureUrlValidator.isTrustedDomain(urlWithParams),
            message = "Expected trusted domain with query params to be valid"
        )
    }

    @Test
    fun `handles URL with fragment identifier`() {
        // Arrange
        val urlWithFragment = "https://farsiland.com/movie/123#player"

        // Act & Assert
        assertTrue(
            actual = SecureUrlValidator.isSecureUrl(urlWithFragment),
            message = "Expected HTTPS URL with fragment to be valid"
        )
        assertTrue(
            actual = SecureUrlValidator.isTrustedDomain(urlWithFragment),
            message = "Expected trusted domain with fragment to be valid"
        )
    }

    @Test
    fun `handles URL with port number`() {
        // Arrange
        val urlWithPort = "https://farsiland.com:8443/movie/123"

        // Act & Assert
        assertTrue(
            actual = SecureUrlValidator.isSecureUrl(urlWithPort),
            message = "Expected HTTPS URL with port to be valid"
        )
        assertTrue(
            actual = SecureUrlValidator.isTrustedDomain(urlWithPort),
            message = "Expected trusted domain with port to be valid"
        )
    }

    // ========== OWASP Mobile M3 Compliance Tests ==========

    @Test
    fun `prevents cleartext HTTP traffic (OWASP M3 compliance)`() {
        // Arrange
        val cleartextUrls = listOf(
            "http://farsiland.com/video",
            "http://api.example.com/data"
        )

        // Act & Assert
        cleartextUrls.forEach { url ->
            assertFalse(
                actual = SecureUrlValidator.isSecureUrl(url),
                message = "OWASP M3: Cleartext HTTP traffic must be rejected: $url"
            )
        }
    }

    @Test
    fun `enforces TLS for all external connections (OWASP M3 compliance)`() {
        // Arrange
        val externalUrls = listOf(
            "https://farsiland.com/video",
            "https://d1.flnd.buzz/stream.m3u8"
        )

        // Act & Assert
        externalUrls.forEach { url ->
            assertTrue(
                actual = SecureUrlValidator.validateUrl(url),
                message = "OWASP M3: External connections must use TLS/HTTPS: $url"
            )
        }
    }
}
