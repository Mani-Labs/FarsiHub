# FarsiPlex Project Documentation

## Section 1: Project Overview

**Name:** FarsiPlex Android TV Application (rebranding to FarsiHub)

**Purpose:** Android TV streaming app for Farsi content with phone & cast support planned

**Target Platform:** Nvidia Shield TV (API 28-36), expanding to phones

**Architecture:** Monolithic app with manual DI, migrating to modular Hilt-based architecture

**Audit Status:** 23/27 issues fixed (85% - Production Ready) - Updated 2025-11-22
**Critical Issues:** 9/9 fixed (100%) - All blocking issues resolved

**Modernization Plan:** See docs/Farsihub-Modernization-Plan.md

---

## Section 2: Technology Stack

**Language:** Kotlin 1.9+

**Dependency Injection:**
- Current: Manual singleton pattern with getInstance()
- Target: Hilt 2.48+

**UI Framework:**
- Android Leanback (~85% of screens - primary)
- Jetpack Compose (~15% - emerging, ContentRow, EpisodeCard, ErrorBoundary)
- Compose TV Material 3 (added, partially used)

**Database Architecture (DUAL BY DESIGN - DO NOT MERGE):**
- AppDatabase v10 (permanent user data: watchlist, favorites, playback positions)
- ContentDatabase v2 (replaceable content catalog with FTS4 search)
- Storage: Room 2.5+ with SQLite
- Pattern: Atomic content refresh via database replacement
- **CRITICAL:** This dual pattern is intentional and audit-approved - never consolidate!

**Video Player:**
- Libraries: Media3 (androidx.media3:media3-exoplayer)
- Implementation: Still using ExoPlayer v2 imports (migration 80% complete)
- Cache: 100MB SimpleCache for video

**Image Loading:** Coil 2.x

**Networking:**
- HTTP: OkHttp + Retrofit with Moshi
- Caching: 10MB HTTP cache + 50-entry in-memory LRU
- Scraping: JSoup for HTML parsing

**Async Patterns:**
- Kotlin Coroutines (IO dispatcher, viewModelScope, lifecycleScope)
- LiveData (legacy screens)
- StateFlow (new screens only)
- Paging 3 for content lists

**Background Processing:**
- WorkManager for periodic sync (30-min intervals)
- Workers: ContentSyncWorker, FarsiPlexSyncWorker

**Build System:** Gradle 8.x with Kotlin DSL

---

## Section 3: Build Commands

```bash
# Compile Kotlin only (fast check)
./gradlew compileDebugKotlin

# Full debug build
./gradlew assembleDebug

# Run unit tests
./gradlew test

# Clean and rebuild
./gradlew clean assembleDebug

# Build with tests
./gradlew build

# Install on connected device
./gradlew installDebug
```

---

## Section 4: Architecture Notes

**Current Architecture (Monolithic):**
- Single app module
- Manual dependency injection via companion object singletons
- No feature modules or layer separation

**Navigation Pattern:**
- Fragment-based navigation with Leanback BrowseFragment as main entry point
- Activity-based details screens (DetailsActivity, SeriesDetailsActivity)
- Modal dialogs for user actions (AddToPlaylistDialogFragment)
- ViewModels: Only 2 exist (GenreFilterViewModel, MainViewModel)

**Data Access Pattern:**
- Repository pattern with manual singletons
- Source-aware (Farsiland/FarsiPlex/Namakade)
- Database-first with API fallback
- 30-second TTL cache per repository
- Direct DAO access (no abstraction layer)

**State Management:**
- LiveData for most screens
- StateFlow in newer components only
- No SavedStateHandle implementation yet

**Database Architecture (CRITICAL - DO NOT MODIFY):**
- AppDatabase v10: User data (permanent)
  - PlaybackPosition (migrated from FarsilandDatabase)
  - Favorites, Watchlist, Playlists
  - SearchHistory, NotificationPreferences
- ContentDatabase v2: Content catalog (replaceable)
  - CachedMovie, CachedSeries, CachedEpisode
  - FTS4 virtual tables for search
  - Atomically replaced during sync

**Video Playback:**
- Media3 libraries with ExoPlayer v2 imports
- SimpleCache for video (100MB)
- Playback position tracking in AppDatabase
- Custom controls overlay for Android TV

**API & Data Sources:**
- Primary: WordPress REST API (Farsiland)
- Scraping: Farsiland, FarsiPlex, Namakade
- Video URLs always scraped (not stored)
- Universal dedup logic across sources

---

## Section 5: Audit Remediation Status

**External Audit Date:** 2025-11-21

**Total Issues Found:** 34 (33 verified, 1 false positive)

**Issue Breakdown by Severity:**
- Critical Issues: 8 (C1-C8) - ✅ ALL FIXED
- High Priority: 12 (H1-H12) - ✅ ALL FIXED
- Medium Priority: 9 (M1-M9) - ✅ ALL FIXED
- Low Priority: 5 (L1-L5) - ⚠️ PENDING (optional but recommended)
- Dead Code: 3 (DC1-DC3) - ✅ ALL REMOVED

**Remediation Status:** COMPLETE - Phase 9 Finished (30/33 fixes - 91%)

**Key Issues Fixed:**
1. Database consolidation (PlaybackPosition moved to AppDatabase)
2. Null safety improvements (removed force unwraps)
3. Memory leak prevention (BackgroundManager, ExoPlayer)
4. Deprecated API migrations
5. HTTP security (size limits, URL validation)
6. Dead code removal

**Remaining Work:**
- L1-L5: Low priority improvements (optional)
- See docs/REMEDIATION_PROGRESS.md for details

**Final Status:** 30/33 fixes complete (91%) - Production-ready

---

## Section 6: Key Directories

```
G:\FarsiPlex\
├── app/ (monolithic module)
│   ├── src/
│   │   └── main/
│   │       ├── java/com/example/farsilandtv/
│   │       │   ├── FarsilandApp.kt (application class, cache init)
│   │       │   ├── MainActivity.kt (entry point)
│   │       │   ├── DetailsActivity.kt (movie/series details)
│   │       │   ├── SeriesDetailsActivity.kt (series detail view)
│   │       │   ├── SearchActivity.kt (search interface)
│   │       │   ├── VideoPlayerActivity.kt (playback screen)
│   │       │   ├── data/
│   │       │   │   ├── database/
│   │       │   │   │   ├── AppDatabase.kt (user data - DO NOT MERGE)
│   │       │   │   │   └── ContentDatabase.kt (content - replaceable)
│   │       │   │   ├── repository/ (manual singletons)
│   │       │   │   │   ├── ContentRepository.kt (main, with cache)
│   │       │   │   │   ├── PlaybackRepository.kt
│   │       │   │   │   ├── WatchlistRepository.kt
│   │       │   │   │   └── ... (7+ repositories)
│   │       │   │   ├── api/
│   │       │   │   │   ├── WordPressApiService.kt
│   │       │   │   │   ├── FarsiPlexApiService.kt
│   │       │   │   │   └── NamakadeApiService.kt
│   │       │   │   └── scraper/
│   │       │   │       ├── VideoUrlScraper.kt
│   │       │   │       └── WebSearchScraper.kt
│   │       │   └── ui/
│   │       │       ├── fragment/ (Leanback-based)
│   │       │       │   ├── HomeFragment.kt (main browse)
│   │       │       │   ├── MoviesFragment.kt
│   │       │       │   ├── ShowsFragment.kt
│   │       │       │   └── PlaybackVideoFragment.kt
│   │       │       └── compose/ (emerging - 15%)
│   │       │           ├── ContentRow.kt
│   │       │           ├── EpisodeCard.kt
│   │       │           └── ErrorBoundary.kt
│   │       └── AndroidManifest.xml
│   ├── build.gradle.kts (all dependencies here)
│   └── libs.versions.toml
├── docs/
│   ├── Farsihub-Modernization-Plan.md (UPDATED)
│   └── REMEDIATION_PROGRESS.md
└── settings.gradle.kts
```

**No Feature Modules Yet** - completely monolithic

---

## Section 7: Development Workflow

**Before Committing:**
1. Run `./gradlew compileDebugKotlin` to check syntax
2. Run `./gradlew test` to verify tests pass
3. Verify no console warnings/errors
4. Reference modernization phase if applicable

**Testing Strategy:**
- Current: 97 tests (75% coverage) - database-focused
- Missing: Repository unit tests, ViewModel tests
- Target: 80% coverage across all layers

**Test Suite Information:**
- Total Tests: 97 automated tests
- Unit Tests: Minimal (placeholder only)
- Integration Tests: 45 tests (DAO tests)
- UI Tests: 14 tests (Fragment tests)
- Coverage: 75% (database layer only)
- Documentation: See docs/PHASE_7_TEST_SUITE_SUMMARY.md

**Test Execution:**
```bash
# Run all unit tests (no device required)
./gradlew testDebugUnitTest

# Run integration tests (requires emulator/device)
./gradlew connectedDebugAndroidTest

# Run specific test class
./gradlew test --tests "*PlaybackRepositoryTest"
```

**Code Quality Standards:**
- Follow Kotlin naming conventions
- Use safe null handling (?.let, ?:, avoid !!)
- Prefer immutable data structures
- Document complex logic
- Use lifecycleScope/viewModelScope (not GlobalScope)
- Maintain thread-safe singletons until Hilt migration

**Current Gaps:**
- No dependency injection framework
- No modularization
- Limited ViewModel usage (only 2)
- Partial Media3 migration
- No CI/CD pipeline
- No monitoring/analytics

---

## Section 8: Critical Implementation Rules

**DO NOT:**
- Merge AppDatabase and ContentDatabase (intentionally separate)
- Change applicationId from "com.example.farsilandtv"
- Use WebViews in background workers without Looper
- Remove getInstance() until Hilt is fully implemented
- Force Media3/Compose adoption all at once

**ALWAYS:**
- Preserve existing user data during migrations
- Test on real Shield TV after changes
- Maintain backwards compatibility
- Keep dual database pattern
- Follow gradual migration approach

---

## Section 9: Modernization Roadmap

**Phase Priority (2-3 weeks minimum viable):**
1. Complete audit fixes (5 remaining) - IMMEDIATE
2. Add Hilt DI framework - HIGH
3. Complete Media3 migration - EASY WIN
4. Modularize architecture - LONG TERM
5. Add resilience/monitoring - OPERATIONAL
6. Migrate to Compose TV - GRADUAL
7. Add mobile/cast support - NEW FEATURES
8. Setup CI/CD pipeline - FINAL

**See:** docs/Farsihub-Modernization-Plan.md for detailed plan

---

## Section 10: Resources

**Project:** FarsiPlex → FarsiHub (rebranding)
**Repository:** G:\FarsiPlex
**Build:** Gradle (use `./gradlew` in Git Bash)
**Target Devices:**
- Current: Nvidia Shield TV (API 28-36)
- Future: Android phones (API 28+)

**Key Dependencies:**
- Kotlin 1.9+
- Android API: min 28, target 35, compile 35
- Room 2.5+ (dual databases)
- Media3 (partial migration)
- Jetpack Compose (15% adoption)
- OkHttp + Retrofit + Moshi
- Coil for images
- WorkManager for sync
- Paging 3 for lists

**Data Sources:**
- Farsiland WordPress API
- FarsiPlex scraper
- Namakade scraper

---

## Section 11: Development Environment - Command Reference

### Machine Setup (Windows 11 + Git Bash)

**Working Directory:** `G:\FarsiPlex`

**Shell:** Git Bash (Unix commands work)

**Android SDK:** `C:\Users\me\AppData\Local\Android\Sdk`

**Installed Tools:**
- Java 17.0.12 LTS
- Gradle 8.13
- Python 3.13.7
- Node.js v22.19.0
- ADB 36.0.0
- Git 2.51.0

**Android Platforms:** API 34, 35, 36

**Build Tools:** 33.0.1, 34.0.0, 35.0.0, 36.1.0

**Active Emulator:** AOSP TV on x86 (API 36) - `emulator-5554`

---

### ✅ ALWAYS Use These Commands:

```bash
# Build commands (from G:\FarsiPlex)
./gradlew compileDebugKotlin      # Fast compile check (~30 sec)
./gradlew assembleDebug            # Build debug APK (~2-3 min)
./gradlew assembleRelease          # Build optimized release APK
./gradlew test                     # Run unit tests
./gradlew clean assembleDebug      # Clean and rebuild

# ADB (emulator/device interaction)
adb devices                        # Check connected devices
adb install -r app/build/outputs/apk/debug/app-debug.apk  # Install APK
adb uninstall com.example.farsilandtv                     # Uninstall app
adb logcat | grep -i farsi         # View app logs (filtered)
adb shell getprop ro.build.version.sdk  # Check device API level
adb shell screencap -p /sdcard/screen.png && adb pull /sdcard/screen.png  # Screenshot

# Emulator (start if needed)
/c/Users/me/AppData/Local/Android/Sdk/emulator/emulator -avd "Namakadeh.com" &
```

---

### ❌ NEVER Use These (Wrong for Git Bash):

```bash
# WRONG - These are for CMD/PowerShell, not Bash
.\gradlew.bat compileDebugKotlin   # ❌ Don't use .bat in Bash
gradlew.bat assembleDebug           # ❌ Don't use .bat in Bash
cmd /c "gradlew.bat ..."           # ❌ Unnecessary wrapper
```

---

### Quick Reference Table

| Task | Command |
|------|---------|
| **Compile check** | `./gradlew compileDebugKotlin` |
| **Build debug APK** | `./gradlew assembleDebug` |
| **Build release APK** | `./gradlew assembleRelease` |
| **Install to device** | `adb install -r app/build/outputs/apk/debug/app-debug.apk` |
| **Run unit tests** | `./gradlew test` |
| **Run instrumented tests** | `./gradlew connectedDebugAndroidTest` |
| **Check devices** | `adb devices` |
| **View logs** | `adb logcat \| grep -i farsi` |
| **Uninstall app** | `adb uninstall com.example.farsilandtv` |
| **Clean build** | `./gradlew clean` |

---

### Standard Development Workflow

```bash
# 1. Make code changes in your editor

# 2. Quick compile check (30 sec)
./gradlew compileDebugKotlin

# 3. If compile passes, build APK (2-3 min)
./gradlew assembleDebug

# 4. Install to connected device/emulator
adb install -r app/build/outputs/apk/debug/app-debug.apk

# 5. Monitor logs during testing
adb logcat | grep -i farsi

# 6. Run tests before committing
./gradlew test
```

---

### APK Output Locations

- **Debug APK:** `app/build/outputs/apk/debug/app-debug.apk` (~48 MB)
- **Release APK:** `app/build/outputs/apk/release/app-release.apk` (~24 MB, optimized)

---

### Important Environment Notes

1. **Shell Type:** Git Bash on Windows 11
   - Use Unix-style commands (`./gradlew`, not `.bat`)
   - Windows paths work with forward slashes: `/c/Users/...`

2. **Environment Variables:**
   - `ANDROID_HOME` not set (but ADB works via PATH)
   - `JAVA_HOME` not set (but Java 17 works)

3. **Emulator Path:**
   - Command not in PATH
   - Full path: `/c/Users/me/AppData/Local/Android/Sdk/emulator/emulator`

4. **Project SDK Configuration:**
   - minSdk: 28 (Android 9)
   - targetSdk: 34
   - compileSdk: 35

---

### Memory Aid for Claude

**When working on this project:**
- ✅ Use `./gradlew` (Unix-style)
- ❌ Never use `.\gradlew.bat` or `cmd` wrappers
- ✅ Use `adb` commands directly (already in PATH)
- ✅ APK location: `app/build/outputs/apk/debug/app-debug.apk`
- ✅ Package name: `com.example.farsilandtv`
- ✅ Working dir: `G:\FarsiPlex`