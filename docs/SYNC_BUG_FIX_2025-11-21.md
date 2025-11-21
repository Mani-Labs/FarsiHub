# Sync and UI Refresh Bug Fixes - 2025-11-21

## Issues Fixed

### 1. Sync Hang Issue ✅ FIXED
**Problem:** ContentSyncWorker.buildSeriesTitleCache() was using `.collect{}` on a Flow, causing infinite hang
**Solution:** Changed to `.first()` to get single emission
**Result:** Sync completes in ~17 seconds (was hanging forever)

### 2. UI Auto-Refresh After Sync ✅ FIXED
**Problem:** Homepage showed stale content after sync completed
- Sync was working but UI didn't update
- 30-second cache prevented fresh data from loading
- MainViewModel didn't know when sync completed

**Solution:**
- Clear all caches in notifySyncCompleted() to force fresh data
- Add observeSyncCompletion() Flow in ContentRepository
- MainViewModel observes sync completion and auto-reloads content

**Result:** UI updates automatically when sync completes

### 3. Episode Parsing from Titles ✅ FIXED
**Problem:** Farsiland API stopped returning ACF (Advanced Custom Fields) data
- Episodes couldn't be linked to series (missing series_title)
- No season/episode numbers available
- Episodes showed wrong information

**Solution:** Parse episode details from title patterns:
- "Robate Salibi EP05" → Series: Robate Salibi, S1E5
- "Eshghe Abadi SE02 EP45" → Series: Eshghe Abadi, S2E45
- "Karnaval EP18 Part 2" → Series: Karnaval, S1E18

**Result:** Episodes now display correctly with proper series linkage

### 4. Skeleton Screen Cleanup ✅ FIXED
**Problem:** Skeleton loading screens remained stuck when switching between sources (Farsiland ↔ FarsiPlex)
**Solution:**
- Added cleanupSkeletonRows() to remove stuck skeleton cards
- Improved removeFeaturedCarouselRow() to handle all skeleton rows
- Fixed featured carousel insertion position

**Result:** Clean UI transitions when switching sources

## Files Modified

1. **FarsilandApp.kt**
   - Re-enabled triggerImmediateSync() on app launch

2. **ContentSyncWorker.kt**
   - Fixed buildSeriesTitleCache() to use .first() instead of .collect()
   - Added episode title parsing fallback for missing ACF data

3. **ContentRepository.kt**
   - Added notifySyncCompleted() with cache clearing
   - Added observeSyncCompletion() Flow for ViewModels

4. **MainViewModel.kt**
   - Added sync completion observer to auto-reload content

5. **HomeFragment.kt**
   - Added cleanupSkeletonRows() to remove stuck skeleton screens
   - Improved featured carousel row management

## Test Results

- Sync completes in ~17 seconds
- Cache clears immediately on sync completion
- MainViewModel auto-reloads content
- UI updates with fresh data from database
- Episodes parse correctly from titles
- No stuck skeleton screens when switching sources

## Performance Metrics

```
12:39:16.630 - Sync Started
12:39:18.093 - Genres synced (34 items)
12:39:24.534 - Movies synced (20 items)
12:39:31.019 - Series synced (20 items)
12:39:31.077 - Series cache built (325 entries)
12:39:33.262 - Episodes synced (20 items)
12:39:33.264 - Sync Complete (Total: 17 seconds)
```

## Root Cause Analysis

The main issue was that Farsiland.com's WordPress API stopped exposing ACF (Advanced Custom Fields) plugin data through the REST API. This affected:
- Movie metadata (rating, runtime, director, cast)
- Series metadata (season count, cast)
- Episode metadata (series_title, season, episode numbers)

The app now gracefully handles missing ACF data by parsing information from content titles where possible.
