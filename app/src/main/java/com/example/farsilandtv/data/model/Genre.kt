package com.example.farsilandtv.data.model

/**
 * Genre enumeration with Persian display names and color codes
 * Used for filtering movies and series by genre
 *
 * Each genre has:
 * - English name (for API filtering/matching)
 * - Persian display name (for UI)
 * - Color code (for visual chips/badges)
 */
enum class Genre(
    val persianName: String,
    val colorCode: String
) {
    ACTION("اکشن", "#FF5722"),           // Red-Orange (high energy)
    COMEDY("کمدی", "#FFC107"),          // Amber (cheerful)
    DRAMA("درام", "#9C27B0"),           // Purple (emotional)
    ROMANCE("عاشقانه", "#E91E63"),      // Pink (romantic)
    THRILLER("هیجان‌انگیز", "#F44336"), // Red (intense)
    HORROR("ترسناک", "#212121"),        // Dark Gray (scary)
    SCIFI("علمی-تخیلی", "#00BCD4"),    // Cyan (futuristic)
    DOCUMENTARY("مستند", "#8BC34A"),    // Light Green (educational)
    ANIMATION("انیمیشن", "#FF9800"),    // Orange (playful)
    FAMILY("خانوادگی", "#4CAF50"),      // Green (wholesome)
    FANTASY("فانتزی", "#673AB7"),       // Deep Purple (magical)
    CRIME("جنایی", "#424242"),          // Gray (dark)
    ADVENTURE("ماجراجویی", "#009688"),  // Teal (adventurous)
    MYSTERY("معمایی", "#3F51B5"),       // Indigo (mysterious)
    WAR("جنگی", "#795548"),             // Brown (gritty)
    HISTORICAL("تاریخی", "#607D8B");    // Blue Gray (classic)

    /**
     * Get English name in lowercase (matches genre field format)
     */
    val englishName: String
        get() = when (this) {
            ACTION -> "action"
            COMEDY -> "comedy"
            DRAMA -> "drama"
            ROMANCE -> "romance"
            THRILLER -> "thriller"
            HORROR -> "horror"
            SCIFI -> "sci-fi"
            DOCUMENTARY -> "documentary"
            ANIMATION -> "animation"
            FAMILY -> "family"
            FANTASY -> "fantasy"
            CRIME -> "crime"
            ADVENTURE -> "adventure"
            MYSTERY -> "mystery"
            WAR -> "war"
            HISTORICAL -> "historical"
        }

    companion object {
        /**
         * Parse genre from English name (case-insensitive)
         * @param name English genre name (e.g., "Action", "sci-fi")
         * @return Matching Genre or null if not found
         */
        fun fromEnglishName(name: String): Genre? {
            val normalized = name.lowercase().trim()
            return values().find { it.englishName == normalized }
        }

        /**
         * Parse genres from comma-separated string
         * @param genresString Comma-separated genre names (e.g., "Action, Drama, Comedy")
         * @return List of matching Genre enums
         */
        fun fromString(genresString: String?): List<Genre> {
            if (genresString.isNullOrBlank()) return emptyList()

            return genresString.split(",")
                .mapNotNull { genreName ->
                    fromEnglishName(genreName.trim())
                }
        }

        /**
         * Get all genres as list
         */
        fun all(): List<Genre> = values().toList()
    }
}
