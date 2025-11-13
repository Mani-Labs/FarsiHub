package com.example.farsilandtv.data.scraper

/**
 * Sealed class representing the result of a scraping operation
 * Allows callers to distinguish between different failure modes
 */
sealed class ScraperResult<out T> {
    /**
     * Success: Data was scraped successfully
     */
    data class Success<T>(val data: T) : ScraperResult<T>()

    /**
     * NetworkError: Network request failed (timeout, DNS, connection refused)
     * User can retry, may be temporary
     */
    data class NetworkError(val message: String, val cause: Throwable?) : ScraperResult<Nothing>()

    /**
     * ParseError: HTML structure changed or unexpected format
     * Should not retry, needs code update
     */
    data class ParseError(val message: String, val cause: Throwable?) : ScraperResult<Nothing>()

    /**
     * NoDataFound: Scraping succeeded but no data matched (e.g., no video URLs)
     * Different from network/parse errors - the page loaded but was empty
     */
    data class NoDataFound(val message: String) : ScraperResult<Nothing>()
}

/**
 * Extension functions for ScraperResult handling
 */
fun <T> ScraperResult<T>.getOrNull(): T? = when (this) {
    is ScraperResult.Success -> data
    else -> null
}

fun <T> ScraperResult<T>.getOrDefault(default: T): T = when (this) {
    is ScraperResult.Success -> data
    else -> default
}

fun <T> ScraperResult<T>.isSuccess(): Boolean = this is ScraperResult.Success

fun <T> ScraperResult<T>.isRetryable(): Boolean = this is ScraperResult.NetworkError

fun <T> ScraperResult<T>.getErrorMessage(): String = when (this) {
    is ScraperResult.Success -> "Success"
    is ScraperResult.NetworkError -> "Network error: $message"
    is ScraperResult.ParseError -> "Parse error: $message"
    is ScraperResult.NoDataFound -> "No data found: $message"
}
