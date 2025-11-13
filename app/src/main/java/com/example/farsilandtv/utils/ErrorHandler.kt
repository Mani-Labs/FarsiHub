package com.example.farsilandtv.utils

import android.content.Context
import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.delay
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Centralized error handling with retry mechanisms and bilingual messages
 */
object ErrorHandler {

    private const val TAG = "ErrorHandler"

    /**
     * Get user-friendly error message (Persian + English)
     */
    fun getErrorMessage(throwable: Throwable): String {
        return when (throwable) {
            is UnknownHostException -> {
                "خطا در اتصال به اینترنت\nNo internet connection"
            }
            is SocketTimeoutException -> {
                "زمان اتصال به پایان رسید\nConnection timeout"
            }
            is HttpException -> {
                when (throwable.code()) {
                    404 -> "محتوا پیدا نشد\nContent not found"
                    429 -> "درخواست‌های زیادی ارسال شد. لطفا کمی صبر کنید\nToo many requests. Please wait"
                    500, 502, 503 -> "خطای سرور. لطفا بعدا تلاش کنید\nServer error. Please try again later"
                    else -> "خطای HTTP ${throwable.code()}\nHTTP error ${throwable.code()}"
                }
            }
            is IOException -> {
                "خطا در ارتباط با سرور\nServer communication error"
            }
            else -> {
                "خطای غیرمنتظره: ${throwable.message}\nUnexpected error: ${throwable.message}"
            }
        }
    }

    /**
     * Show error toast with bilingual message
     */
    fun showError(context: Context, throwable: Throwable) {
        val message = getErrorMessage(throwable)
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }

    /**
     * Show error toast with custom message
     */
    fun showError(context: Context, message: String) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }

    /**
     * Retry with exponential backoff
     * @param times Maximum number of retries (default: 3)
     * @param initialDelay Initial delay in milliseconds (default: 1000ms)
     * @param maxDelay Maximum delay in milliseconds (default: 10000ms)
     * @param factor Multiplier for delay increase (default: 2.0)
     * @param block The suspending function to retry
     */
    suspend fun <T> retryWithExponentialBackoff(
        times: Int = 3,
        initialDelay: Long = 1000,
        maxDelay: Long = 10000,
        factor: Double = 2.0,
        block: suspend () -> T
    ): T {
        var currentDelay = initialDelay
        repeat(times - 1) { attempt ->
            try {
                return block()
            } catch (e: Exception) {
                // Log the retry attempt
                println("Retry attempt ${attempt + 1} failed: ${e.message}")
            }
            delay(currentDelay)
            currentDelay = (currentDelay * factor).toLong().coerceAtMost(maxDelay)
        }
        // Last attempt
        return block()
    }

    /**
     * Execute with error handling
     */
    suspend fun <T> executeWithErrorHandling(
        onError: ((Throwable) -> Unit)? = null,
        block: suspend () -> T
    ): Result<T> {
        return try {
            Result.success(block())
        } catch (e: Exception) {
            onError?.invoke(e)
            Result.failure(e)
        }
    }

    /**
     * Log error with consistent formatting
     * Replaces bare e.printStackTrace() calls throughout codebase
     */
    fun logError(tag: String, message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Log.e(tag, "$message: ${throwable.message}", throwable)
        } else {
            Log.e(tag, message)
        }
    }

    /**
     * Log warning with consistent formatting
     */
    fun logWarning(tag: String, message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Log.w(tag, "$message: ${throwable.message}", throwable)
        } else {
            Log.w(tag, message)
        }
    }

    /**
     * Log info with consistent formatting
     */
    fun logInfo(tag: String, message: String) {
        Log.i(tag, message)
    }
}
