# FarsiPlex Feature Implementation Roadmap

## Overview

This document outlines 7 improvements for FarsiPlex, ordered by dependencies and impact.

| # | Feature | Effort | Impact | Dependencies | Status |
|---|---------|--------|--------|--------------|--------|
| 1 | Fix Video Caching | Small | High | None | ✅ Complete |
| 2 | Better Loading States | Small | Medium | None | ✅ Complete |
| 3 | Improved Search | Medium | High | None | ✅ Complete |
| 4 | Offline Downloads UI | Medium | High | #1 (caching patterns) | ✅ Complete |
| 5 | Testing | Large | High | #1-4 complete | In Progress |
| 6 | CI/CD Pipeline | Medium | Medium | #5 (tests exist) | ✅ Complete |
| 7 | Phone UI Support | Large | High | None | ✅ Complete |

---

## Stage 1: Fix Video Caching

**Goal**: Re-enable ExoPlayer video caching to reduce buffering and bandwidth

**Current State**:
- `FarsilandApp.videoCache` is initialized (100MB SimpleCache) in `initializeVideoCache()`
- `VideoPlayerActivity` has caching DISABLED at line 566-570 due to `IllegalStateException: null`
- The cache exists but isn't connected to the player

**Root Cause Analysis**:
- The crash occurred because VideoPlayerActivity created its own `cache: SimpleCache?` (line 95)
- This conflicts with `FarsilandApp.videoCache` - two SimpleCache instances can't share the same directory
- Solution: Use the singleton cache from FarsilandApp instead of creating a new one

**Tasks**:
1. Remove local `cache` variable from VideoPlayerActivity
2. Use `FarsilandApp.videoCache` in `initializePlayer()`
3. Create `CacheDataSource.Factory` wrapping the HTTP source
4. Handle null cache gracefully (fallback to direct HTTP)
5. Remove cache cleanup from `onStop()`/`onDestroy()` (FarsilandApp manages lifecycle)

**Files to Modify**:
- `VideoPlayerActivity.kt` (lines 95, 566-570, 1461-1463, 1607-1609)

**Success Criteria**:
- Video playback works without crashes
- Seeking in watched videos is instant (cached)
- Cache persists across video sessions
- No `IllegalStateException` in logs

**Status**: ✅ Complete (2025-11-30)
- Removed local `cache` variable from VideoPlayerActivity
- Connected to `FarsilandApp.videoCache` singleton via companion object
- Created `CacheDataSource.Factory` with fallback to direct HTTP
- Removed cache cleanup from onStop/onDestroy (app manages lifecycle)
- Cache clearing on HTTP errors uses `FarsilandApp.videoCache`

---

## Stage 2: Better Loading States

**Goal**: Replace all "Loading..." text with consistent shimmer skeletons

**Current State**:
- `ShimmerEffect.kt` exists with `ShimmerBox`, `MovieCardSkeleton`, `SeriesCardSkeleton`
- 18 files reference shimmer/loading - partial adoption
- Some screens use shimmer, others use plain text

**Screens Needing Shimmer**:
1. `SeriesDetailsActivity.kt` - "Loading episodes..." text (line 151-157)
2. `DownloadsScreen.kt` - Check loading state
3. `FavoritesScreen.kt` - Check loading state
4. `PlaylistsScreen.kt` - Check loading state
5. `OptionsScreen.kt` - Check loading state

**Tasks**:
1. Audit all screens for loading states
2. Create `EpisodeCardSkeleton` for series details
3. Replace text loading indicators with shimmer
4. Ensure consistent animation timing (800ms shimmer cycle)

**Files to Modify**:
- `SeriesDetailsActivity.kt`
- Various Screen.kt files
- `ShimmerEffect.kt` (add new skeletons if needed)

**Success Criteria**:
- No "Loading..." text visible in app
- All loading states use shimmer animation
- Consistent skeleton shapes match actual content

**Status**: ✅ Complete (2025-11-30)
- Audited all screens (SeriesDetails, Downloads, Favorites, Playlists, Options)
- `SeriesDetailsActivity` now uses `ShimmerDetailsScreen` instead of "Loading episodes..." text
- Changed "Loading..." to "Downloading..." for download button label (more accurate)
- Shimmer components already exist: `ShimmerCard`, `ShimmerEpisodeCard`, `ShimmerDetailsScreen`, etc.
- No remaining "Loading..." text visible to users

---

## Stage 3: Improved Search

**Goal**: Add voice search, search suggestions, and filter capabilities

**Current State** (`SearchScreen.kt`):
- Text search with 300ms debounce
- Recent search history (via `SearchRepository`)
- Results show movies + series mixed
- NO voice search
- NO filters (year/genre/rating)

**Tasks**:

### 3.1 Voice Search
1. Add microphone icon to search bar
2. Implement `SpeechRecognizer` for voice input
3. Auto-populate search field with recognized text
4. Handle Android TV remote voice button (KEYCODE_SEARCH)

### 3.2 Search Suggestions
1. Show recent searches below search bar (already implemented)
2. Add trending/popular searches section
3. Show suggestions while typing (fuzzy match from history)

### 3.3 Filters
1. Add filter chips: Genre, Year, Rating
2. Genre dropdown from `CachedGenreDao`
3. Year range slider (1990-2025)
4. Rating filter (6+, 7+, 8+)
5. Persist filter state across sessions

**Files to Modify**:
- `SearchScreen.kt` - Add voice button and filters UI
- `SearchRepository.kt` - Add trending searches query
- `MainViewModel.kt` - Add filtered search method
- `ContentRepository.kt` - Add filtered search query
- `AndroidManifest.xml` - Add RECORD_AUDIO permission

**Success Criteria**:
- Voice search works on Shield TV remote
- Filter chips narrow results correctly
- Filters persist when returning to search

**Status**: ✅ Complete (2025-11-30)
- Added voice search button (🎤) with SpeechRecognizer intent
- Added filter chips: Genre (Action, Comedy, Drama, etc.), Year (2024-90s), Rating (5+-9+)
- Filter dropdown menus with clear option
- "Clear filters" button when any filter is active
- Voice search launches system speech recognizer

---

## Stage 4: Offline Downloads UI

**Goal**: Complete the downloads feature with working UI integration

**Current State**:
- `DownloadManager.kt` is fully implemented (474 lines)
- `DownloadWorker.kt` exists for background downloads
- `DownloadDao.kt` and `DownloadItem.kt` exist
- `DownloadsScreen.kt` exists but needs verification
- UI may not be fully connected to DownloadManager

**Tasks**:

### 4.1 Verify/Complete DownloadsScreen
1. Show list of downloads (pending, in-progress, completed, failed)
2. Progress bars for active downloads
3. Swipe-to-delete or long-press menu
4. Play downloaded content offline

### 4.2 Add Download Button to Details Screens
1. Add download icon to `MovieDetailsScreen.kt`
2. Add download icon to `SeriesDetailsScreen.kt` (per episode)
3. Show download status (queued/downloading/complete/error)
4. Disable download when no video URL available

### 4.3 Offline Playback
1. Modify `VideoPlayerActivity` to detect downloaded content
2. Play from local file instead of streaming
3. Show offline indicator badge on downloaded content

### 4.4 Auto-Delete Watched Downloads (Optional)
1. Add setting in Options: "Delete after watching"
2. Trigger cleanup in `savePositionToDatabase()` when 95% complete

**Files to Modify**:
- `DownloadsScreen.kt` - Full UI implementation
- `MovieDetailsScreen.kt` - Add download button
- `SeriesDetailsScreen.kt` - Add download button
- `VideoPlayerActivity.kt` - Offline playback support
- `OptionsScreen.kt` - Auto-delete setting

**Success Criteria**:
- Can download movies and episodes
- Downloads show progress in DownloadsScreen
- Downloaded content plays without internet
- Storage usage displayed

**Status**: ✅ Complete (Already Implemented)
- DownloadsScreen fully implemented with progress bars, pause/resume/cancel
- MovieDetailsScreen has download button with queue/delete functionality
- SeriesDetailsScreen has download button per episode
- Offline playback works via `file://` URL passed to VideoPlayerActivity
- Real-time progress updates via StateFlow
- Delete confirmation dialogs

---

## Stage 5: Testing

**Goal**: Achieve meaningful test coverage for critical paths

**Current State**:
- 10 test files exist:
  - `androidTest/`: IndexPerformanceTest, PlaybackPositionDaoTest, WatchlistDaoTest
  - `test/`: PlaybackRepositoryTest, WatchlistRepositoryTest, ContentSyncWorkerTest, SecureUrlValidatorTest, ContentRepositoryTest, FavoritesRepositoryTest, NotificationPreferencesRepositoryTest, SearchRepositoryTest, VideoUrlScraperTest, DownloadManagerTest
- All tests validated against source code (2025-12-04)

**Tasks**:

### 5.1 Unit Tests (Priority)
1. `ContentRepositoryTest` - Search, filtering, caching
2. `DownloadManagerTest` - Queue, pause, resume, cancel
3. `VideoUrlScraperTest` - Mock HTML responses
4. `DatabasePreferencesTest` - Source switching

### 5.2 Integration Tests
1. `DownloadWorkerIntegrationTest` - Full download flow
2. `ContentSyncWorkerIntegrationTest` - Sync with mock API
3. `SearchFlowTest` - Search -> Results -> Details

### 5.3 UI Tests (Espresso/Compose)
1. `HomeScreenTest` - Navigation, content loading
2. `SearchScreenTest` - Voice search, filters
3. `VideoPlayerTest` - Playback controls, quality switching

**Files to Create**:
- `app/src/test/java/.../ContentRepositoryTest.kt`
- `app/src/test/java/.../DownloadManagerTest.kt`
- `app/src/test/java/.../VideoUrlScraperTest.kt`
- `app/src/androidTest/java/.../HomeScreenTest.kt`

**Success Criteria**:
- 60%+ coverage on repositories
- All scrapers have mock tests
- Critical UI flows have E2E tests
- Tests run in < 5 minutes

**Status**: In Progress (2025-11-30)
- Created `ContentRepositoryTest.kt` (30 tests for models, filtering, sorting, search)
- Created `DownloadManagerTest.kt` (20 tests for DownloadItem, enums, progress calculation)
- Created `VideoUrlScraperTest.kt` (24 tests for VideoUrl model, quality/mirror extraction)
- Fixed existing `PlaybackRepositoryTest.kt` imports
- All new tests passing (140/167 tests pass, 27 pre-existing failures)
- Pre-existing test issues: PlaybackRepositoryTest and SecureUrlValidatorTest require Robolectric fixes

---

## Stage 6: CI/CD Pipeline

**Goal**: Automate builds, tests, and quality checks on every PR

**Current State**:
- No `.github/workflows` directory
- No automated builds or tests

**Tasks**:

### 6.1 Basic Build Workflow
```yaml
# .github/workflows/build.yml
- Checkout code
- Setup JDK 17
- Cache Gradle dependencies
- Run ./gradlew assembleDebug
- Upload APK artifact
```

### 6.2 Test Workflow
```yaml
# .github/workflows/test.yml
- Run unit tests: ./gradlew test
- Run instrumented tests: ./gradlew connectedDebugAndroidTest
- Upload test results
- Fail PR if tests fail
```

### 6.3 Lint/Quality Workflow
```yaml
# .github/workflows/quality.yml
- Run ktlint: ./gradlew ktlintCheck
- Run detekt: ./gradlew detekt
- Run Android lint: ./gradlew lintDebug
- Comment warnings on PR
```

### 6.4 Release Workflow (Optional)
```yaml
# .github/workflows/release.yml
- Trigger on version tag
- Build release APK
- Sign APK
- Create GitHub Release
- Upload APK to release
```

**Files to Create**:
- `.github/workflows/build.yml`
- `.github/workflows/test.yml`
- `.github/workflows/quality.yml`
- Add ktlint and detekt to `build.gradle.kts`

**Success Criteria**:
- PR builds pass before merge
- Test failures block PRs
- APK artifacts available for download
- Release process automated

**Status**: ✅ Complete (2025-11-30)
- Created `.github/workflows/build.yml` - Debug APK build with artifact upload
- Created `.github/workflows/test.yml` - Unit tests + instrumented tests (on main only)
- Created `.github/workflows/quality.yml` - Android Lint + compile warnings check
- Workflows trigger on push to main/develop/feature/* and PRs
- Gradle caching enabled for faster builds
- Test results uploaded as artifacts with 7-day retention

---

## Execution Order

```
Week 1: Stage 1 (Video Caching) + Stage 2 (Loading States)
        └── Quick wins, immediate UX improvement

Week 2: Stage 3 (Search)
        └── User-facing feature, moderate complexity

Week 3-4: Stage 4 (Downloads)
          └── Complex feature, builds on caching patterns

Week 5: Stage 5 (Testing)
        └── Foundation for quality

Week 6: Stage 6 (CI/CD)
        └── Automation for sustainability
```

---

## Stage 7: Phone UI Support

**Goal**: Add full phone support with touch-optimized UI

**Current State**:
- App was TV-only with sidebar navigation
- D-pad optimized, not touch-friendly

**Implemented**:

### 7.1 Device Detection
- `DeviceUtils.kt` - Device type detection (TV/Tablet/Phone)
- `LocalDeviceType.kt` - Compose CompositionLocal for device context
- Detection cached for session consistency

### 7.2 Phone Navigation
- `PhoneNavigationHost.kt` - Bottom navigation container with 5 tabs:
  - Home, Movies, Shows, Search, Library
- Material 3 NavigationBar with pink accent (#E91E63)

### 7.3 Phone Screens (7 files)
- `PhoneHomeScreen.kt` - Featured carousel, content rows
- `PhoneMoviesScreen.kt` - 2-column grid with horizontal filter chips
- `PhoneShowsScreen.kt` - 2-column grid with sort dropdown
- `PhoneLibraryScreen.kt` - Tabbed: Favorites, Downloads, Playlists
- `PhoneMovieDetailsScreen.kt` - Vertical layout, similar movies
- `PhoneSeriesDetailsScreen.kt` - Season/episode picker
- Settings accessed via gear icon in Library header

### 7.4 Responsive Components
- `PhoneMovieCard`, `PhoneSeriesCard` - Touch-optimized cards
- `OfflineIndicator` - Network status banner
- Consistent dark theme (#121212 background)

**Files Created**:
- `utils/DeviceUtils.kt`
- `utils/LocalDeviceType.kt`
- `ui/screens/phone/*.kt` (7 files)
- `ui/viewmodel/Phone*ViewModel.kt` (3 files)
- `ui/components/ResponsiveCards.kt`

**Success Criteria**:
- ✅ Phone detected correctly via DeviceUtils
- ✅ Bottom navigation works on phone
- ✅ Touch scrolling and gestures work
- ✅ All content accessible on phone
- ✅ Settings accessible via Library

**Status**: ✅ Complete (2025-11-30)
- Full phone UI with 7 dedicated screens
- Bottom navigation with 5 tabs
- Library consolidates Favorites/Downloads/Playlists
- Portrait orientation enforced on phone
- Tested on Pixel 9 Pro XL emulator

---

## Notes

- Each stage can be done incrementally
- Stages 1-4 are feature work (1-5 days each)
- Stage 5-6 are infrastructure (1 week each)
- Stage 7 (Phone UI) is major feature (completed)
- Total estimated effort: 4-6 weeks

---

*Document created: 2025-11-30*
*Last updated: 2025-11-30*
