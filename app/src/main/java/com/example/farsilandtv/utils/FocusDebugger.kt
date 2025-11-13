package com.example.farsilandtv.utils

import android.util.Log
import android.view.View
import android.view.ViewGroup

/**
 * Feature #21: Focus Debugger
 *
 * Debug helper to visualize focus issues
 * Enable in debug builds only to troubleshoot navigation problems
 *
 * Usage:
 * - FocusDebugger.logFocusChain(rootView) to see entire focus hierarchy
 * - FocusDebugger.findNextFocusableView(currentView, direction) to test focus search
 */
object FocusDebugger {

    /**
     * Log entire focus chain starting from a view
     * Shows which views are focusable and which are currently focused
     */
    fun logFocusChain(view: View, depth: Int = 0) {
        // Debug logging enabled during development
        if (false) return // Change to true to disable

        val indent = "  ".repeat(depth)
        val focusable = if (view.isFocusable) "FOCUSABLE" else ""
        val focused = if (view.isFocused) "FOCUSED" else ""
        val visibility = when (view.visibility) {
            View.VISIBLE -> "VISIBLE"
            View.INVISIBLE -> "INVISIBLE"
            View.GONE -> "GONE"
            else -> "UNKNOWN"
        }

        Log.d(TAG, "$indent${view.javaClass.simpleName} $focusable $focused $visibility id=${view.id}")

        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                logFocusChain(view.getChildAt(i), depth + 1)
            }
        }
    }

    /**
     * Find next focusable view in a given direction
     * Useful for testing focus navigation logic
     */
    fun findNextFocusableView(currentView: View, direction: Int): View? {
        // Debug logging enabled during development
        if (false) return null // Change condition to true to disable

        val nextView = currentView.focusSearch(direction)

        val directionName = when (direction) {
            View.FOCUS_UP -> "UP"
            View.FOCUS_DOWN -> "DOWN"
            View.FOCUS_LEFT -> "LEFT"
            View.FOCUS_RIGHT -> "RIGHT"
            View.FOCUS_FORWARD -> "FORWARD"
            View.FOCUS_BACKWARD -> "BACKWARD"
            else -> "UNKNOWN"
        }

        Log.d(TAG, "Focus search from ${currentView.javaClass.simpleName} direction=$directionName -> ${nextView?.javaClass?.simpleName}")

        return nextView
    }

    /**
     * Log current focus state
     */
    fun logCurrentFocus(rootView: View) {
        // Debug logging enabled during development
        if (false) return // Change to true to disable

        val focusedView = rootView.findFocus()
        if (focusedView != null) {
            Log.d(TAG, "Currently focused: ${focusedView.javaClass.simpleName} id=${focusedView.id}")
        } else {
            Log.d(TAG, "No view currently focused")
        }
    }

    /**
     * Check if view is actually visible and focusable
     */
    fun isViewFocusable(view: View): Boolean {
        // Debug logging enabled during development
        if (false) return false // Change condition to true to disable

        val focusable = view.isFocusable && view.visibility == View.VISIBLE
        Log.d(TAG, "${view.javaClass.simpleName} focusable=$focusable (isFocusable=${view.isFocusable}, visibility=${view.visibility})")

        return focusable
    }

    private const val TAG = "FocusDebug"
}
