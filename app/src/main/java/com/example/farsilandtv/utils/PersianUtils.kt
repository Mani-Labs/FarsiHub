package com.example.farsilandtv.utils

/**
 * Utility class for Persian text formatting
 */
object PersianUtils {

    private val persianDigits = arrayOf("۰", "۱", "۲", "۳", "۴", "۵", "۶", "۷", "۸", "۹")
    private val englishDigits = arrayOf("0", "1", "2", "3", "4", "5", "6", "7", "8", "9")

    /**
     * Convert English numbers to Persian/Farsi numbers
     */
    fun toPersianNumbers(input: String): String {
        var result = input
        for (i in englishDigits.indices) {
            result = result.replace(englishDigits[i], persianDigits[i])
        }
        return result
    }

    /**
     * Convert English numbers to Persian/Farsi numbers
     */
    fun toPersianNumbers(number: Int): String {
        return toPersianNumbers(number.toString())
    }

    /**
     * Convert English numbers to Persian/Farsi numbers
     */
    fun toPersianNumbers(number: Double): String {
        return toPersianNumbers(number.toString())
    }

    /**
     * Convert Persian numbers to English numbers
     */
    fun toEnglishNumbers(input: String): String {
        var result = input
        for (i in persianDigits.indices) {
            result = result.replace(persianDigits[i], englishDigits[i])
        }
        return result
    }

    /**
     * Format rating with Persian star symbol
     */
    fun formatRating(rating: Double?): String {
        if (rating == null) return ""
        return "★ ${toPersianNumbers("%.1f".format(rating))}"
    }

    /**
     * Format rating with Persian star symbol (Float overload)
     */
    fun formatRating(rating: Float?): String {
        if (rating == null) return ""
        return "★ ${toPersianNumbers("%.1f".format(rating))}"
    }

    /**
     * Format year in Persian
     */
    fun formatYear(year: Int?): String {
        if (year == null) return ""
        return toPersianNumbers(year)
    }

    /**
     * Format air date from ISO 8601 format to readable format (e.g., "Oct 31, 2025")
     */
    fun formatAirDate(airDate: String?): String {
        if (airDate.isNullOrEmpty()) return ""
        return try {
            val date = java.time.LocalDate.parse(airDate, java.time.format.DateTimeFormatter.ISO_DATE)
            val formatter = java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy", java.util.Locale.ENGLISH)
            date.format(formatter)
        } catch (e: Exception) {
            airDate // Return original string if parsing fails
        }
    }
}
