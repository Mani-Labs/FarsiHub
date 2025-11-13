package com.example.farsilandtv.utils

/**
 * Utility object for standardized episode and season number formatting
 * throughout the FarsilandTV app.
 *
 * All episode/season displays should use this formatter to ensure consistency.
 *
 * Format: S00E00 (e.g., S01E05, S02E12)
 *
 * Example usage:
 * ```
 * val episodeNumber = EpisodeFormatter.formatEpisodeNumber(1, 5)  // "S01E05"
 * val seasonName = EpisodeFormatter.formatSeasonNumber(2)         // "Season 2"
 * val fullTitle = EpisodeFormatter.formatEpisodeTitle(1, 5, "Pilot")  // "S01E05: Pilot"
 * ```
 */
object EpisodeFormatter {

    /**
     * Format episode number in S00E00 format
     *
     * @param season Season number (1-based)
     * @param episode Episode number (1-based)
     * @return Formatted string (e.g., "S01E05")
     *
     * Edge cases:
     * - Season 0 or Episode 0 are treated as valid (special episodes, pilots)
     * - Negative numbers are converted to 0
     * - Numbers > 99 will wrap (e.g., S100E100 becomes S00E00 due to %02d)
     */
    fun formatEpisodeNumber(season: Int, episode: Int): String {
        val safeSeason = season.coerceAtLeast(0)
        val safeEpisode = episode.coerceAtLeast(0)
        return "S%02d E%02d".format(safeSeason, safeEpisode)
    }

    /**
     * Format season number in human-readable format
     *
     * @param season Season number (1-based)
     * @return Formatted string (e.g., "Season 1")
     *
     * Edge cases:
     * - Season 0 returns "Specials"
     * - Negative numbers are converted to 0
     */
    fun formatSeasonNumber(season: Int): String {
        return when {
            season == 0 -> "Specials"
            season < 0 -> "Season 0"
            else -> "Season $season"
        }
    }

    /**
     * Format complete episode title with number prefix
     *
     * @param season Season number (1-based)
     * @param episode Episode number (1-based)
     * @param title Episode title
     * @return Formatted string (e.g., "S01E05: Pilot")
     *
     * If title is empty or blank, returns only the episode number
     */
    fun formatEpisodeTitle(season: Int, episode: Int, title: String): String {
        val episodeNumber = formatEpisodeNumber(season, episode)
        return if (title.isBlank()) {
            episodeNumber
        } else {
            "$episodeNumber: $title"
        }
    }

    /**
     * Format season and episode range for batch operations
     *
     * @param season Season number
     * @param startEpisode First episode in range
     * @param endEpisode Last episode in range
     * @return Formatted string (e.g., "S01E01-E05")
     */
    fun formatEpisodeRange(season: Int, startEpisode: Int, endEpisode: Int): String {
        val safeSeason = season.coerceAtLeast(0)
        val safeStart = startEpisode.coerceAtLeast(0)
        val safeEnd = endEpisode.coerceAtLeast(0)

        return if (safeStart == safeEnd) {
            formatEpisodeNumber(safeSeason, safeStart)
        } else {
            "S%02dE%02d-E%02d".format(safeSeason, safeStart, safeEnd)
        }
    }

    /**
     * Format season summary (e.g., for season cards)
     *
     * @param season Season number
     * @param episodeCount Total episodes in season
     * @return Formatted string (e.g., "Season 1 • 12 Episodes")
     */
    fun formatSeasonSummary(season: Int, episodeCount: Int): String {
        val seasonName = formatSeasonNumber(season)
        val episodeText = if (episodeCount == 1) "1 Episode" else "$episodeCount Episodes"
        return "$seasonName • $episodeText"
    }

    /**
     * Format episode progress (e.g., for continue watching)
     *
     * @param season Season number
     * @param episode Episode number
     * @param title Episode title
     * @param progressPercentage Progress percentage (0-100)
     * @return Formatted string (e.g., "S01E05: Pilot (45% watched)")
     */
    fun formatEpisodeProgress(
        season: Int,
        episode: Int,
        title: String,
        progressPercentage: Int
    ): String {
        val baseTitle = formatEpisodeTitle(season, episode, title)
        return "$baseTitle (${progressPercentage}% watched)"
    }

    /**
     * Format Persian/Farsi episode number
     * (For future use if Persian numbering is needed)
     *
     * @param season Season number
     * @param episode Episode number
     * @return Formatted string with Persian numbers
     */
    fun formatEpisodeNumberPersian(season: Int, episode: Int): String {
        val englishFormat = formatEpisodeNumber(season, episode)
        // Convert to Persian using PersianUtils
        return PersianUtils.toPersianNumbers(englishFormat)
    }

    /**
     * Parse episode number from S00E00 format string
     * Useful for reverse conversion
     *
     * @param formatted Formatted episode string (e.g., "S01E05")
     * @return Pair of (season, episode) or null if invalid format
     */
    fun parseEpisodeNumber(formatted: String): Pair<Int, Int>? {
        val regex = Regex("S(\\d{2})E(\\d{2})")
        val match = regex.matchEntire(formatted) ?: return null

        val season = match.groupValues[1].toIntOrNull() ?: return null
        val episode = match.groupValues[2].toIntOrNull() ?: return null

        return Pair(season, episode)
    }
}
