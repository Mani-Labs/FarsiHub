#!/usr/bin/env python3
"""Fix naming conflicts in ContentRepository"""

# Read the file
with open('app/src/main/java/com/example/farsilandtv/data/repository/ContentRepository.kt', 'r', encoding='utf-8') as f:
    content = f.read()

# Fix 1: Rename getSeriesByGenres to getTvShowsByGenres (for consistency)
content = content.replace('suspend fun getSeriesByGenres(', 'suspend fun getTvShowsByGenres(')
content = content.replace('handleApiError("getSeriesByGenres(', 'handleApiError("getTvShowsByGenres(')

# Fix 2: Change getSeries(page, perPage) to getTvShows(page, perPage)
content = content.replace('return@withContext getSeries(page, perPage)', 'return@withContext getTvShows(page, perPage)')

# Write back
with open('app/src/main/java/com/example/farsilandtv/data/repository/ContentRepository.kt', 'w', encoding='utf-8') as f:
    f.write(content)

print("Fixed:")
print("1. Renamed getSeriesByGenres -> getTvShowsByGenres")
print("2. Fixed getSeries(page, perPage) -> getTvShows(page, perPage)")
