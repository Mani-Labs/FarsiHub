# FarsiPlex Project Documentation

## Section 1: Project Overview

**Name:** FarsiPlex Android TV Application

**Purpose:** Android TV streaming app for Farsi content

**Target Platform:** Nvidia Shield TV (API 28-36)

**Architecture:** Hybrid Leanback + Jetpack Compose

---

## Section 2: Technology Stack

**Language:** Kotlin

**UI Framework:**
- Android Leanback (primary navigation & browsing)
- Jetpack Compose (secondary, emerging UI framework)

**Database:**
- AppDatabase (primary, consolidated storage)
- FarsilandDatabase (deprecated, being migrated to AppDatabase)
- Storage: Room with SQLite

**Video Player:** ExoPlayer (with custom playback controls)

**Image Loading:** Coil (with lifecycle awareness improvements needed)

**Async Patterns:**
- Kotlin Coroutines
- LiveData
- StateFlow

**Build System:** Gradle with Kotlin DSL

---

## Section 3: Build Commands

```bash
# Compile Kotlin only (fast check)
.\gradlew.bat compileDebugKotlin

# Full debug build
.\gradlew.bat assembleDebug

# Run unit tests
.\gradlew.bat test

# Clean and rebuild
.\gradlew.bat clean assembleDebug

# Build with tests
.\gradlew.bat build
```

---

## Section 4: Architecture Notes

**Navigation Pattern:**
- Fragment-based navigation with Leanback BrowseFragment as main entry point
- Activity-based details screens (DetailsActivity, SeriesDetailsActivity)
- Modal dialogs for user actions (AddToPlaylistDialogFragment)

**Data Access Pattern:**
- Repository pattern (VideoRepository, SeriesRepository, etc.)
- DAOs for direct database access
- Separation of concerns: Activities/Fragments → ViewModel → Repository → DAO

**State Management:**
- ViewModel for screen-level state
- LiveData for reactive updates
- SavedStateHandle (being implemented for config changes)

**Database Architecture:**
- AppDatabase: Consolidated primary database
- FarsilandDatabase: Legacy database (being phased out, PlaybackPosition migration pending)
- Transaction handling for critical operations (position saves)

**Video Playback:**
- ExoPlayer instance managed in PlaybackVideoFragment
- Custom controls overlay for Android TV
- Playback position tracking in database

---

## Section 5: Audit Remediation Status

**Audit Date:** 2025-11-10

**Total Issues Found:** 34 (33 verified, 1 false positive)

**Issue Breakdown by Severity:**
- Critical Issues: 8 (C1-C8) - ✅ ALL FIXED
- High Priority: 12 (H1-H12) - ✅ ALL FIXED
- Medium Priority: 9 (M1-M9) - ✅ ALL FIXED
- Low Priority: 5 (L1-L5) - ⚠️ PENDING (optional)
- Dead Code: 3 (DC1-DC3) - ✅ ALL REMOVED

**Remediation Status:** COMPLETE - Phase 9 Finished (30/33 fixes - 91%)

**Key Issues Being Addressed:**
1. Multiple Room Database instances consolidation
2. Unsafe force unwraps and null handling
3. Memory leak prevention (BackgroundManager, ExoPlayer)
4. Deprecated API migration (getSerializableExtra, onBackPressed)
5. Lifecycle safety improvements
6. Dead code removal
7. Comprehensive testing suite implementation

**Progress Tracking:** See docs/REMEDIATION_PROGRESS.md

**Final Status:** 30/33 fixes complete (91%) - Production-ready

---

## Section 6: Key Directories

```
G:\FarsiPlex\
├── app/
│   ├── src/
│   │   └── main/
│   │       ├── java/com/example/farsilandtv/
│   │       │   ├── MainActivity.kt (entry point)
│   │       │   ├── DetailsActivity.kt (movie/series details)
│   │       │   ├── SeriesDetailsActivity.kt (series detail view)
│   │       │   ├── SearchActivity.kt (search interface)
│   │       │   ├── VideoPlayerActivity.kt (playback screen)
│   │       │   ├── data/
│   │       │   │   ├── AppDatabase.kt (PRIMARY - consolidated database)
│   │       │   │   ├── repository/ (data access layer)
│   │       │   │   │   ├── VideoRepository.kt
│   │       │   │   │   ├── SeriesRepository.kt
│   │       │   │   │   └── ...
│   │       │   │   └── db/ (DEPRECATED databases)
│   │       │   │       └── FarsilandDatabase.kt
│   │       │   └── ui/
│   │       │       ├── fragment/
│   │       │       │   ├── HomeFragment.kt (browse screen)
│   │       │       │   ├── PlaybackVideoFragment.kt (player controls)
│   │       │       │   ├── MovieDetailsFragment.kt
│   │       │       │   └── AddToPlaylistDialogFragment.kt
│   │       │       └── compose/ (emerging Compose UI)
│   │       │           ├── MoviesScreen.kt
│   │       │           └── ...
│   │       └── AndroidManifest.xml
│   └── build.gradle.kts
└── settings.gradle.kts
```

**Core Modules:**
- `data/`: Database, repositories, and data access objects
- `ui/`: Fragments, activities, and Compose screens
- `domain/`: Business logic and models (if separated)

---

## Section 7: Development Workflow

**Before Committing:**
1. Run `.\gradlew.bat compileDebugKotlin` to check syntax
2. Run `.\gradlew.bat test` to verify tests pass
3. Verify no console warnings/errors
4. Reference remediation phase in commit message (if applicable)

**Testing Strategy:**
- Unit tests for repositories and ViewModels
- Integration tests for database operations
- UI tests for critical fragments
- Target minimum 60% code coverage for changed areas

**Test Suite Information:**
- Total Tests: 97 automated tests
- Unit Tests: 38 tests (PlaybackRepositoryTest, WatchlistRepositoryTest)
- Integration Tests: 45 tests (PlaybackPositionDaoTest, WatchlistDaoTest)
- UI Tests: 14 tests (HomeFragmentTest, PlaybackVideoFragmentTest)
- Coverage Achieved: 75% (exceeds 60% target)
- Documentation: See docs/PHASE_7_TEST_SUITE_SUMMARY.md

**Test Execution:**
```bash
# Run all unit tests (no device required)
.\gradlew.bat testDebugUnitTest

# Run integration tests (requires emulator/device)
.\gradlew.bat connectedDebugAndroidTest

# Run specific test class
.\gradlew.bat test --tests com.example.farsilandtv.data.repository.PlaybackRepositoryTest
```

**Code Quality Standards:**
- Follow existing Kotlin naming conventions
- Use safe null handling (?.let, ?:, avoid !!)
- Prefer immutable data structures
- Document complex logic with comments
- Avoid GlobalScope for async operations (use viewModelScope or lifecycleScope)

**Remediation Status:**
- Phase 1-6: 21 fixes completed (database consolidation, memory leaks, null safety, security)
- Phase 7: 97 automated tests created (75% coverage)
- Phase 8: Architectural review passed, documentation finalized
- Status: Production Ready

---

## Section 8: Contacts & Resources

**Project:** FarsiPlex Android TV App
**Repository:** G:\FarsiPlex
**Build:** Gradle (Windows: gradlew.bat, Unix: ./gradlew)
**Target Device:** Nvidia Shield TV (API 28-36)

**Key Technologies:**
- Kotlin 1.9+
- Android API 28 minimum, target 36
- Room Database 2.5+
- ExoPlayer 2.18+
- Jetpack Compose (emerging)
