package com.example.farsilandtv.utils

import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import java.util.concurrent.TimeoutException

/**
 * Security utility for safe regex execution with timeout protection.
 *
 * Prevents ReDoS (Regular Expression Denial of Service) attacks by:
 * 1. Enforcing maximum execution time (5 seconds default)
 * 2. Input size limits
 * 3. Safe fallback on timeout
 *
 * Security Note: Complex regex patterns with nested quantifiers can cause
 * exponential backtracking on malicious input, freezing the app.
 */
object SecureRegex {

    private const val DEFAULT_TIMEOUT_MS = 5000L // 5 seconds
    private const val MAX_INPUT_SIZE = 10_000_000 // 10MB

    /**
     * Execute regex.find() with timeout protection
     *
     * @param regex The compiled Regex pattern
     * @param input The input string to search
     * @param timeoutMs Maximum execution time in milliseconds (default 5000)
     * @return MatchResult or null if no match found or timeout occurred
     */
    suspend fun findWithTimeout(
        regex: Regex,
        input: String,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS
    ): MatchResult? {
        // Validate input size
        if (input.length > MAX_INPUT_SIZE) {
            android.util.Log.w("SecureRegex", "Input too large for regex: ${input.length} bytes")
            return null
        }

        return try {
            withTimeout(timeoutMs) {
                withContext(Dispatchers.Default) {
                    regex.find(input)
                }
            }
        } catch (e: TimeoutException) {
            android.util.Log.e("SecureRegex", "Regex timeout after ${timeoutMs}ms - possible ReDoS attack", e)
            null
        } catch (e: Exception) {
            android.util.Log.e("SecureRegex", "Regex execution error", e)
            null
        }
    }

    /**
     * Execute regex.findAll() with timeout protection
     *
     * @param regex The compiled Regex pattern
     * @param input The input string to search
     * @param timeoutMs Maximum execution time in milliseconds (default 5000)
     * @return Sequence of MatchResult or empty sequence if timeout occurred
     */
    suspend fun findAllWithTimeout(
        regex: Regex,
        input: String,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS
    ): Sequence<MatchResult> {
        // Validate input size
        if (input.length > MAX_INPUT_SIZE) {
            android.util.Log.w("SecureRegex", "Input too large for regex: ${input.length} bytes")
            return emptySequence()
        }

        return try {
            withTimeout(timeoutMs) {
                withContext(Dispatchers.Default) {
                    regex.findAll(input)
                }
            }
        } catch (e: TimeoutException) {
            android.util.Log.e("SecureRegex", "Regex timeout after ${timeoutMs}ms - possible ReDoS attack", e)
            emptySequence()
        } catch (e: Exception) {
            android.util.Log.e("SecureRegex", "Regex execution error", e)
            emptySequence()
        }
    }

    /**
     * Simplify complex regex patterns to reduce backtracking risk
     *
     * Recommendations:
     * - Avoid nested quantifiers: .*? inside (?:...)?
     * - Use possessive quantifiers when possible: [^}]* instead of .*?
     * - Limit input size before applying regex
     * - Use atomic grouping where appropriate
     */
    fun validatePattern(pattern: String): Boolean {
        // Check for dangerous patterns (nested quantifiers)
        val dangerousPatterns = listOf(
            Regex("""\(\.\*\?\).*?\?"""), // .*? followed by optional group
            Regex("""\.\*.*\.\*"""),       // Multiple unbounded wildcards
            Regex("""\[\^.\]\*.*\[\^.\]\*""") // Multiple negated character classes
        )

        return dangerousPatterns.none { it.containsMatchIn(pattern) }
    }
}
