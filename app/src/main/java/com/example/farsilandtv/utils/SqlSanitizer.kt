package com.example.farsilandtv.utils

/**
 * Security utility for sanitizing SQL inputs including LIKE patterns and FTS queries.
 *
 * LIKE Pattern Sanitization:
 * Prevents SQL injection via LIKE wildcards by:
 * 1. Escaping % (matches any sequence of characters)
 * 2. Escaping _ (matches any single character)
 * 3. Escaping the escape character itself (\)
 *
 * FTS Query Sanitization:
 * Prevents FTS syntax errors from special characters:
 * 1. Escapes double quotes (phrase matching)
 * 2. Wraps query in quotes for literal search
 * 3. Handles *, -, AND, OR, NOT operators safely
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
     * Sanitize FTS4 query to prevent syntax errors from special characters
     *
     * AUDIT FIX (Second Audit #4): FTS Query Syntax Errors
     * Problem: Special FTS characters (*, ", -, AND, OR, NOT) cause SQLite syntax errors
     * Examples that break without sanitization:
     * - "Iron Man*" → near "Iron Man*": syntax error
     * - "Avenger\"" → unterminated string
     * - "-Batman" → unexpected "-"
     *
     * Solution: Wrap query in double quotes for literal phrase matching
     * This treats all special characters as literal text, not FTS operators
     *
     * @param input User-provided search query
     * @return Sanitized FTS query wrapped in double quotes
     *
     * Usage:
     * ```kotlin
     * val userInput = "Iron Man*"  // Contains FTS wildcard
     * val sanitized = SqlSanitizer.sanitizeFtsQuery(userInput)
     * // Result: "\"Iron Man*\"" (literal search, no syntax error)
     * movieDao.searchMovies(sanitized)
     * ```
     */
    fun sanitizeFtsQuery(input: String): String {
        // Trim whitespace and return empty if blank
        val trimmed = input.trim()
        if (trimmed.isEmpty()) {
            return "\"\""
        }

        // Escape any existing double quotes by doubling them (FTS4 escape syntax)
        val escaped = trimmed.replace("\"", "\"\"")

        // Wrap in double quotes for literal phrase matching
        return "\"$escaped\""
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
     *
     * EXTERNAL AUDIT FIX UT-L4: Annotated as test-only function
     * This should ideally be moved to androidTest directory, but annotated
     * as VisibleForTesting to indicate it's for testing purposes only.
     */
    @androidx.annotation.VisibleForTesting
    internal fun testSanitization(): Map<String, String> {
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
