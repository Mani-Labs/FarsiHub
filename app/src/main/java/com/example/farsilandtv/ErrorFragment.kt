package com.example.farsilandtv

import android.os.Bundle
import android.view.View
import androidx.core.content.ContextCompat
import androidx.leanback.app.ErrorSupportFragment

/**
 * Enhanced Error Fragment with retry support and bilingual messages
 */
class ErrorFragment : ErrorSupportFragment() {

    private var onRetryCallback: (() -> Unit)? = null
    private var errorMessage: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = resources.getString(R.string.app_name)
    }

    /**
     * Set error content with custom message and retry callback
     */
    fun setErrorContent(
        message: String = resources.getString(R.string.error_fragment_message),
        onRetry: (() -> Unit)? = null
    ) {
        this.errorMessage = message
        this.onRetryCallback = onRetry

        imageDrawable = ContextCompat.getDrawable(
            requireContext(),
            androidx.leanback.R.drawable.lb_ic_sad_cloud
        )
        this.message = message
        setDefaultBackground(TRANSLUCENT)

        if (onRetry != null) {
            // Show retry button if callback is provided
            buttonText = "تلاش مجدد / Retry"
            buttonClickListener = View.OnClickListener {
                // Remove error fragment and retry
                parentFragmentManager.beginTransaction().remove(this@ErrorFragment).commit()
                onRetry.invoke()
            }
        } else {
            // Just dismiss button
            buttonText = resources.getString(R.string.dismiss_error)
            buttonClickListener = View.OnClickListener {
                parentFragmentManager.beginTransaction().remove(this@ErrorFragment).commit()
            }
        }
    }

    /**
     * Legacy setErrorContent for backwards compatibility
     */
    internal fun setErrorContent() {
        setErrorContent(
            message = resources.getString(R.string.error_fragment_message),
            onRetry = null
        )
    }

    companion object {
        private const val TRANSLUCENT = true

        /**
         * Create new instance with error message and retry callback
         */
        fun newInstance(
            errorMessage: String,
            onRetry: (() -> Unit)? = null
        ): ErrorFragment {
            return ErrorFragment().apply {
                setErrorContent(errorMessage, onRetry)
            }
        }
    }
}