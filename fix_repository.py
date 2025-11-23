#!/usr/bin/env python3
"""
Fix ContentRepository.kt - Replace getMoviesByGenres and getTvShowsByGenres
with database-only filtering
"""

import re

# Read the file
with open('app/src/main/java/com/example/farsilandtv/data/repository/ContentRepository.kt', 'r', encoding='utf-8') as f:
    content = f.read()

# Pattern to match getMoviesByGenres function
movies_pattern = r'    /\*\*\s+\* Get movies by multiple genres.*?^\s{4}\}'
movies_replacement = '''    /**
     * DEEP AUDIT FIX: Database-only filtering with proper pagination
     *
     * Previous Issue: Fetched 100 items from API, filtered client-side
     * Result: Page 2 might have 0 matches → infinite scroll breaks
     *
     * New Approach: Use database-only filtering with LIMIT/OFFSET
     * Why: Database is synced every 30min, has all content, supports proper pagination
     *
     * @param genres List of Genre enums to filter by
     * @param page Page number (starts from 1)
     * @param perPage Items per page (default 20)
     * @return List of movies matching any of the selected genres
     */
    suspend fun getMoviesByGenres(
        genres: List<com.example.farsilandtv.data.model.Genre>,
        page: Int = 1,
        perPage: Int = 20
    ): Result<List<Movie>> = withContext(Dispatchers.IO) {
        try {
            if (genres.isEmpty()) {
                // No filter - use existing method
                return@withContext getMovies(page, perPage)
            }

            // DEEP AUDIT FIX: Use database-only filtering (no API calls)
            val genreNames = genres.map { it.englishName }
            val sanitizedGenres = genreNames.map { SqlSanitizer.sanitizeLikePattern(it) }

            // Pad genres list to 5 elements (DAO expects up to 5)
            val genre1 = sanitizedGenres.getOrNull(0) ?: return@withContext Result.success(emptyList())
            val genre2 = sanitizedGenres.getOrNull(1)
            val genre3 = sanitizedGenres.getOrNull(2)
            val genre4 = sanitizedGenres.getOrNull(3)
            val genre5 = sanitizedGenres.getOrNull(4)

            val offset = (page - 1) * perPage

            // Single database query with proper pagination
            val cachedMovies = getContentDb().movieDao().getMoviesByGenresPaginated(
                genre1 = genre1,
                genre2 = genre2,
                genre3 = genre3,
                genre4 = genre4,
                genre5 = genre5,
                limit = perPage,
                offset = offset
            )

            // Convert to Movie objects
            val movies = cachedMovies.map { it.toMovie() }
            Result.success(movies)
        } catch (e: Exception) {
            handleApiError("getMoviesByGenres(genres=${genres.size}, page=$page)", e)
        }
    }'''

# Replace getMoviesByGenres
content = re.sub(movies_pattern, movies_replacement, content, flags=re.DOTALL | re.MULTILINE)

# Pattern to match getTvShowsByGenres function
shows_pattern = r'    /\*\*\s+\* Get series by multiple genres.*?^\s{4}\}'
shows_replacement = '''    /**
     * DEEP AUDIT FIX: Database-only filtering (same fix for TV shows)
     */
    suspend fun getTvShowsByGenres(
        genres: List<com.example.farsilandtv.data.model.Genre>,
        page: Int = 1,
        perPage: Int = 20
    ): Result<List<Series>> = withContext(Dispatchers.IO) {
        try {
            if (genres.isEmpty()) {
                return@withContext getTvShows(page, perPage)
            }

            val genreNames = genres.map { it.englishName }
            val sanitizedGenres = genreNames.map { SqlSanitizer.sanitizeLikePattern(it) }

            val genre1 = sanitizedGenres.getOrNull(0) ?: return@withContext Result.success(emptyList())
            val genre2 = sanitizedGenres.getOrNull(1)
            val genre3 = sanitizedGenres.getOrNull(2)
            val genre4 = sanitizedGenres.getOrNull(3)
            val genre5 = sanitizedGenres.getOrNull(4)

            val offset = (page - 1) * perPage

            val cachedSeries = getContentDb().seriesDao().getSeriesByGenresPaginated(
                genre1 = genre1,
                genre2 = genre2,
                genre3 = genre3,
                genre4 = genre4,
                genre5 = genre5,
                limit = perPage,
                offset = offset
            )

            val series = cachedSeries.map { it.toSeries() }
            Result.success(series)
        } catch (e: Exception) {
            handleApiError("getTvShowsByGenres(genres=${genres.size}, page=$page)", e)
        }
    }'''

# Replace getTvShowsByGenres
content = re.sub(shows_pattern, shows_replacement, content, flags=re.DOTALL | re.MULTILINE)

# Write back
with open('app/src/main/java/com/example/farsilandtv/data/repository/ContentRepository.kt', 'w', encoding='utf-8') as f:
    f.write(content)

print("ContentRepository.kt updated successfully!")
print("✅ getMoviesByGenres - Database-only filtering")
print("✅ getTvShowsByGenres - Database-only filtering")
