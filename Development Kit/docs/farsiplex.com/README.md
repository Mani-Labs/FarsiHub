# FarsiPlex Database Converter

Converts FarsiPlex database from old schema to the standardized app ContentDatabase schema.

## Overview

The FarsiPlex database originally used a different schema (`CachedMovie`, `CachedSeries`, `CachedEpisode`) that was incompatible with the app's Room entities. This converter transforms it to match the standardized schema used by Farsiland and Namakade databases.

## Database Schema Comparison

### Old Schema (farsiplex_old.db)
```
Tables:
- CachedMovie (url, titleEn, titleFa, synopsisFa, etc.)
- CachedSeries (url, titleEn, titleFa, synopsisFa, etc.)
- CachedEpisode (url, seriesId, seasonNumber, episodeNumber, etc.)
- CachedVideoUrl (url, quality, contentId, contentType, etc.)
```

### New Schema (farsiplex_content.db)
```
Tables:
- cached_movies (farsilandUrl, title, posterUrl, description, etc.)
- cached_series (farsilandUrl, title, totalSeasons, totalEpisodes, etc.)
- cached_episodes (farsilandUrl, seriesId, season, episode, etc.)
- cached_video_urls (mp4Url, contentId, contentType, quality, etc.)
```

## Usage

### 1. Prerequisites
```bash
cd G:\FarsiPlex\farsiplex.com
```

Ensure you have:
- Python 3.x installed
- Source database: `farsiplex_old.db`

### 2. Run Converter
```bash
python convert_farsiplex_to_app_db.py
```

### 3. Output
```
============================================================
FARSIPLEX DATABASE CONVERTER
============================================================
Source: farsiplex_old.db
Output: farsiplex_content.db

[1/5] Creating app schema...
[2/5] Converting movies...
[3/5] Converting series...
[4/5] Converting episodes...
[5/5] Creating indexes...

============================================================
CONVERSION STATISTICS
============================================================
Movies:           36
Series:           34
Episodes:         558
Movie Video URLs: 98
Episode Video URLs: 1410
Video URL Coverage: 100.0% (558/558)
============================================================
```

### 4. Deploy to App
```bash
# Copy converted database to app assets
cp farsiplex_content.db ../app/src/main/assets/databases/farsiplex_content.db

# Rebuild app
cd ..
./gradlew clean assembleDebug

# Install on device
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Conversion Process

### Movies Conversion
- `CachedMovie` → `cached_movies`
- `url` → `farsilandUrl`
- `synopsisFa` → `description`
- `releaseDate` → extract `year`
- Preserves: `id`, `title`, `posterUrl`, `rating`

### Series Conversion
- `CachedSeries` → `cached_series`
- `url` → `farsilandUrl`
- `synopsisFa` → `description`
- Calculates: `totalSeasons`, `totalEpisodes` from actual episode count
- Preserves: `id`, `title`, `posterUrl`, `rating`

### Episodes Conversion
- `CachedEpisode` → `cached_episodes`
- `url` → `farsilandUrl`
- `seasonNumber` → `season`
- `episodeNumber` → `episode`
- Joins with `CachedSeries` to get `seriesTitle`
- Preserves: `episodeId`, `seriesId`, `thumbnailUrl`

### Video URLs Conversion
- `CachedVideoUrl` → `cached_video_urls`
- `url` → `mp4Url`
- Preserves: `contentId`, `contentType`, `quality`
- All 1,508 video URLs converted (100% coverage)

## Key Features

### Schema Compliance
- ✅ Matches Room entity expectations exactly
- ✅ Uses inline `UNIQUE` constraints (not separate indices)
- ✅ All `PRIMARY KEY` columns marked `NOT NULL`
- ✅ Correct field names (`farsilandUrl`, `mp4Url`, `cachedAt`)

### Data Integrity
- ✅ Preserves all movie, series, and episode data
- ✅ Maintains video URL associations
- ✅ Extracts year from release dates
- ✅ Calculates accurate season/episode counts

### Performance
- ✅ Efficient schema with inline constraints
- ✅ Smaller database size (808K → 430K)
- ✅ Fast queries with proper indexing

## File Structure
```
farsiplex.com/
├── README.md                          # This file
├── convert_farsiplex_to_app_db.py    # Converter script
├── farsiplex_old.db                  # Source database (old schema)
└── farsiplex_content.db              # Output database (new schema)
```

## Related Documentation

See also:
- `G:\FarsiPlex\namakade.com\README.md` - Namakade database conversion
- `G:\FarsiPlex\app\src\main\java\com\example\farsilandtv\data\database\ContentEntities.kt` - Room entities

## Troubleshooting

### Database Not Found
```bash
# Ensure you're in the correct directory
cd G:\FarsiPlex\farsiplex.com

# Verify source database exists
ls farsiplex_old.db
```

### Schema Mismatch Errors
If you see Room validation errors after deployment:
1. Verify output database schema matches Room entities
2. Check that all `UNIQUE` constraints are inline (not separate indices)
3. Ensure `PRIMARY KEY` columns have `NOT NULL` constraint

### Video Playback Issues
If episodes don't play after conversion:
1. Verify video URLs were converted correctly
2. Check that `contentId` matches `episodeId` in cached_episodes
3. Ensure `contentType` is set to `'episode'` (not `'series'`)

## Notes

- The converter uses the existing `id` values from the source database to maintain consistency
- Video URLs are preserved with their original quality settings
- The conversion is idempotent - running it multiple times produces the same result
- Original source database (`farsiplex_old.db`) is preserved for backup

## Version History

- **2025-11-08**: Initial converter created
  - Converts old FarsiPlex schema to standardized ContentDatabase schema
  - 100% video URL coverage (1,508 URLs)
  - Schema compliance with Room validation
