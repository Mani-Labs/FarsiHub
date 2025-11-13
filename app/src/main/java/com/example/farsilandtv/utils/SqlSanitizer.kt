package com.example.farsilandtv.utils

/**
 * Security utility for sanitizing SQL LIKE pattern inputs.
 *
 * Prevents SQL injection via LIKE wildcards by:
 * 1. Escaping % (matches any sequence of characters)
 * 2. Escaping _ (matches any single character)
 * 3. Escaping the escape character itself (\)
 *
 * Security Note: Without sanitization, user input like "%" returns
 * the entire database catalog (10,000+ rows), causing UI freeze and DoS.
 *
 * Example Attack:
 * - User searches for "%"
 * - Query becomes: WHERE title LIKE '%%'
 * - Database returns ALL 10,000+ movies → UI freeze
 *
 * Usage:
 * ```kotlin
 * val userInput = "%" // Malicious input
 * val sanitized = SqlSanitizer.sanitizeLikePattern(userInput)
 * // Query: WHERE title LIKE '%' || :sanitized || '%' ESCAPE '\'
 * ```
 */
object SqlSanitizer {

    /**
     * Escape SQL LIKE wildcards in user input
     *
     * @param input User-provided search query
     * @return Sanitized string with escaped wildcards
     */
    fun sanitizeLikePattern(input: String): String {
        return input
            .replace("\\", "\\\\")  // Escape the escape character first
            .replace("%", "\\%")    // Escape % wildcard
            .replace("_", "\\_")    // Escape _ wildcard
    }

    /**
     * Validate that sanitization is working correctly
     *
     * @param input Original user input
     * @return True if input contains wildcards that need escaping
     */
    fun containsWildcards(input: String): Boolean {
        return input.contains("%") || input.contains("_") || input.contains("\\")
    }

    /**
     * Test sanitization with common attack patterns
     */
    fun testSanitization(): Map<String, String> {
        val testCases = mapOf(
            "%" to "\\%",                    // Full wildcard attack
            "%%" to "\\%\\%",                // Double wildcard
            "test%" to "test\\%",            // Trailing wildcard
            "_test" to "\\_test",            // Single char wildcard
            "test_test" to "test\\_test",    // Mid wildcard
            "normal query" to "normal query", // No wildcards
            "\\" to "\\\\",                  // Escape character
            "\\%" to "\\\\\\%"               // Already escaped
        )

        return testCases.mapValues { (input, _) -> sanitizeLikePattern(input) }
    }
}
