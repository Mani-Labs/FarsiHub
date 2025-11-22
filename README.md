# FarsiPlex Android TV Application (Rebranding to FarsiHub)

**Production-Ready Streaming Application for Farsi Content on Android TV**

Version 1.0 | Nvidia Shield TV (API 28-36) | Security-Hardened | 97 Automated Tests

---

## Overview

FarsiPlex (soon to be FarsiHub) is a professional Android TV streaming application designed specifically for Nvidia Shield TV devices. It provides a seamless browsing and playback experience for Persian/Farsi movies, TV shows, and episodes with advanced features including quality selection (1080p/720p/480p/360p), continue watching, watchlist management, and intelligent playback position tracking.

**Target Platform:** Nvidia Shield TV (Android API 28-36), expanding to phones
**Architecture:** Monolithic with manual DI (migrating to Hilt-based modular architecture)
**UI Framework:** 85% Android Leanback, 15% Jetpack Compose (gradual migration)
**Status:** Production-Ready (91% audit compliance - 30/33 fixes complete)
**Modernization:** See `docs/Farsihub-Modernization-Plan.md` for universal APK roadmap

---

## Key Features

### Content Discovery
- Browse recent movies, TV shows, and episodes
- Genre-based filtering with dynamic categories
- Search functionality with FTS4 full-text indexing
- Featured content carousel on home screen
- Continue watching row with progress indicators
- Multi-source content aggregation (Farsiland, FarsiPlex, Namakade)

### Playback Experience
- Multi-quality video playback (1080p/720p/480p/360p)
- Automatic playback position resume
- Media3 ExoPlayer-based streaming (80% migrated)
- D-pad optimized navigation for Android TV remotes
- 100MB video cache for smooth playback
- Picture-in-picture (PiP) support planned

### User Features
- Watchlist management with bookmarking
- Automatic watch history tracking
- Progress tracking per movie/episode
- Series monitoring and episode completion tracking
- Offline content caching for improved performance
- 30-minute background sync via WorkManager

### Technical Highlights
- **Dual database architecture by design** (DO NOT MERGE!)
  - AppDatabase: Permanent user data (watchlist, favorites, playback)
  - ContentDatabase: Replaceable content catalog with atomic refresh
- Database-first architecture with API fallback
- Comprehensive error handling with retry logic
- Memory leak prevention and lifecycle safety
- Security-hardened (ReDoS, SQL injection, HTTPS enforcement)
- 97 automated tests (75% code coverage - database layer)
- Firebase Crashlytics ready (currently disabled)
- Optimized for Nvidia Shield TV (2GB RAM)

---

## Technology Stack

**Language:** Kotlin 1.9+

**Dependency Injection:**
- Current: Manual singleton pattern with getInstance()
- Target: Hilt 2.48+ (planned migration)

**UI Frameworks:**
- Android Leanback (~85% of screens - primary TV navigation)
- Jetpack Compose (~15% - emerging: ContentRow, EpisodeCard, ErrorBoundary)
- Compose TV Material 3 (added but partially used)

**Database Architecture (CRITICAL - DUAL BY DESIGN):**
- AppDatabase v10 (permanent user data - DO NOT touch during sync)
- ContentDatabase v2 (replaceable content with FTS4 search)
- Storage: Room 2.5+ with SQLite
- **Pattern:** Atomic content refresh via database replacement
- **WARNING:** This dual pattern is audit-approved and intentional!

**Video Player:**
- Libraries: Media3 (androidx.media3:media3-exoplayer)
- Implementation: ExoPlayer v2 imports (migration 80% complete)
- SimpleCache: 100MB for video caching
- Custom TV controls overlay

**Image Loading:**
- Coil 2.x with lifecycle awareness
- Efficient caching and memory management

**Networking:**
- HTTP: OkHttp + Retrofit with Moshi
- Caching: 10MB HTTP cache + 50-entry in-memory LRU
- Scraping: JSoup for HTML parsing (hardcoded selectors)

**Async Programming:**
- Kotlin Coroutines (IO dispatcher, viewModelScope, lifecycleScope)
- LiveData for most screens (legacy)
- StateFlow for new components only
- Paging 3 for content lists

**Background Processing:**
- WorkManager for periodic sync (30-min intervals)
- Workers: ContentSyncWorker, FarsiPlexSyncWorker

**Build System:** Gradle 8.x with Kotlin DSL

**Testing:**
- JUnit 4 (unit tests - minimal)
- AndroidX Test (integration tests - database)
- Espresso & Leanback Test Utils (UI tests)
- Mockito-Kotlin for mocking
- **Gap:** No repository/ViewModel unit tests

---

## Build Instructions

### Prerequisites
- Android Studio Hedgehog (2023.1.1) or later
- JDK 11 or higher
- Android SDK API 28-36
- Gradle 8.0+
- Windows (for .bat scripts) or Unix-like system

### Quick Start

```bash
# Clone the repository
git clone <repository-url>
cd FarsiPlex

# Compile Kotlin (fast syntax check)
.\gradlew.bat compileDebugKotlin

# Build debug APK
.\gradlew.bat assembleDebug

# Build with full compilation
.\gradlew.bat build
```

### Build Output
- Debug APK: `app/build/outputs/apk/debug/app-debug.apk`
- Release APK: `app/build/outputs/apk/release/app-release.apk`

### Install on Device

```bash
# Install via ADB
adb install app/build/outputs/apk/debug/app-debug.apk

# Launch application (DO NOT change applicationId!)
adb shell am start -n com.example.farsilandtv/.MainActivity
```

---

## Testing Instructions

### Run All Tests

```bash
# Unit tests (minimal coverage)
.\gradlew.bat testDebugUnitTest

# Integration tests (requires emulator/device)
.\gradlew.bat connectedDebugAndroidTest

# All tests
.\gradlew.bat test connectedAndroidTest
```

### Run Specific Test Classes

```bash
# Database integration tests (main test coverage)
.\gradlew.bat connectedAndroidTest --tests "*PlaybackPositionDaoTest"
.\gradlew.bat connectedAndroidTest --tests "*WatchlistDaoTest"

# UI component tests
.\gradlew.bat connectedAndroidTest --tests "*HomeFragmentTest"
.\gradlew.bat connectedAndroidTest --tests "*PlaybackVideoFragmentTest"
```

### Test Coverage

**Total Tests:** 97 automated tests
- Unit Tests: Minimal (placeholder only)
- Integration Tests: 45 tests (database DAOs - main coverage)
- UI Tests: 14 tests (fragments)

**Coverage:** 75% of database layer (repository/ViewModel tests missing)

See `docs/PHASE_7_TEST_SUITE_SUMMARY.md` for detailed test documentation.

---

## Project Status

### External Audit Status (2025-11-21)

**Completion:** 30 out of 33 issues fixed (91%) - Production-Ready

**Fixes Completed by Severity:**
- Critical Issues (C1-C8): ✅ ALL FIXED
- High Priority (H1-H12): ✅ ALL FIXED
- Medium Priority (M1-M9): ✅ ALL FIXED
- Low Priority (L1-L5): ⚠️ 5 PENDING (optional)
- Dead Code (DC1-DC3): ✅ ALL REMOVED

**Key Architecture Decision:**
- **Dual database pattern RETAINED** (audit-approved as safe)
- AppDatabase and ContentDatabase remain separate by design
- This enables atomic content refresh without affecting user data

See `docs/REMEDIATION_PROGRESS.md` for detailed fix tracking.

### Current Architecture Gaps

| Component | Current State | Modernization Target |
|-----------|--------------|---------------------|
| DI Framework | Manual singletons | Hilt 2.48+ |
| Module Structure | Monolithic (single app/) | Multi-module |
| Database | Dual (KEEP AS IS!) | Dual (KEEP AS IS!) |
| UI Framework | 85% Leanback, 15% Compose | Gradual Compose migration |
| Player | Media3 libs, ExoPlayer imports | Pure Media3 |
| Testing | 75% DB layer only | 80% all layers |
| ViewModels | Only 2 exist | One per screen |
| Caching | In-memory LRU | Room queries |

---

## Architecture

### Current Architecture (Monolithic)

```
┌─────────────────────────────────────────┐
│       PRESENTATION LAYER                │
│   (Activities, Fragments, 2 ViewModels) │
│        Manual Singleton Access          │
└──────────────┬──────────────────────────┘
               │
┌──────────────▼──────────────────────────┐
│      REPOSITORY LAYER                   │
│   (Manual Singletons with getInstance)  │
│        ContentRepository (cached)       │
│        PlaybackRepository              │
│        WatchlistRepository             │
└──────────────┬──────────────────────────┘
               │
       ┌───────┴────────┐
       │                │
┌──────▼─────┐   ┌─────▼──────┐
│ AppDatabase│   │ContentDatabase│
│ (User Data)│   │(Content Catalog)│
│  PERMANENT │   │  REPLACEABLE   │
└────────────┘   └────────────┘
       +                +
       │                │
┌──────▼────────────────▼─────┐
│    Remote Services          │
│ (WordPress API, Scrapers)   │
└─────────────────────────────┘
```

### Core Patterns

**Manual Dependency Injection:**
```kotlin
companion object {
    @Volatile private var INSTANCE: ContentRepository? = null
    fun getInstance(context: Context): ContentRepository
}
```

**Repository Pattern:**
- Database-first with API fallback
- 30-second TTL cache per repository
- Source-aware (Farsiland/FarsiPlex/Namakade)

**Dual Database Architecture (DO NOT CHANGE):**
- AppDatabase: User data survives content refresh
- ContentDatabase: Atomically replaced during sync
- This pattern prevents corruption and data loss

### Navigation Flow

```
MainActivity (Leanback Container)
  ├── HomeFragment (Browse: BrowseFragment - Leanback)
  ├── MoviesFragment (All movies - Leanback)
  ├── ShowsFragment (All TV shows - Leanback)
  ├── SearchActivity (Search - Leanback)
  ├── DetailsActivity (Movie/Show - Leanback)
  │     └── Scrapes video URLs on play
  ├── SeriesDetailsActivity (Episodes - Leanback)
  └── VideoPlayerActivity (Media3/ExoPlayer)
        └── PlaybackVideoFragment (custom controls)

Compose Components (15%):
  - ContentRow
  - EpisodeCard
  - ErrorBoundary
```

---

## Key Directories

```
G:\FarsiPlex\
├── app/ (MONOLITHIC - no modules yet)
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/example/farsilandtv/
│   │   │   │   ├── FarsilandApp.kt (app class, cache init)
│   │   │   │   ├── MainActivity.kt (entry point)
│   │   │   │   ├── data/
│   │   │   │   │   ├── database/
│   │   │   │   │   │   ├── AppDatabase.kt (user data - DO NOT MERGE)
│   │   │   │   │   │   └── ContentDatabase.kt (content - REPLACEABLE)
│   │   │   │   │   ├── repository/ (manual singletons)
│   │   │   │   │   │   ├── ContentRepository.kt (main, cached)
│   │   │   │   │   │   └── ... (7+ repositories)
│   │   │   │   │   ├── api/
│   │   │   │   │   │   ├── WordPressApiService.kt
│   │   │   │   │   │   ├── FarsiPlexApiService.kt
│   │   │   │   │   │   └── NamakadeApiService.kt
│   │   │   │   │   └── scraper/
│   │   │   │   │       └── VideoUrlScraper.kt
│   │   │   │   └── ui/
│   │   │   │       ├── fragment/ (85% Leanback)
│   │   │   │       └── compose/ (15% emerging)
│   │   │   └── AndroidManifest.xml
│   ├── build.gradle.kts (all deps here - no modules)
│   └── libs.versions.toml
├── docs/
│   ├── Farsihub-Modernization-Plan.md (NEW - roadmap)
│   ├── REMEDIATION_PROGRESS.md (audit fixes)
│   └── ... (other documentation)
├── CLAUDE.md (AI assistant instructions - UPDATED)
└── README.md (this file - UPDATED)
```

---

## Critical Implementation Rules

**DO NOT EVER:**
- ❌ Merge AppDatabase and ContentDatabase (intentionally separate!)
- ❌ Change applicationId from "com.example.farsilandtv" (data loss!)
- ❌ Use WebViews in background workers without Looper
- ❌ Remove getInstance() before Hilt migration is complete
- ❌ Force all-at-once UI framework migration

**ALWAYS:**
- ✅ Preserve dual database architecture
- ✅ Test on real Nvidia Shield TV device
- ✅ Maintain backwards compatibility
- ✅ Use gradual migration approach
- ✅ Keep user data safe during updates

---

## Modernization Roadmap (FarsiHub)

### Phase Priority (Minimum Viable: 2-3 weeks)

1. **Phase 0.5** - Complete 5 remaining audit fixes (IMMEDIATE)
2. **Phase 1** - Add Hilt DI framework (HIGH - biggest blocker)
3. **Phase 1.5** - Complete Media3 migration (EASY - 80% done)
4. **Phase 2** - Modularize architecture (LONG TERM)
5. **Phase 5** - Add resilience/monitoring (OPERATIONAL)
6. **Phase 3** - Migrate to Compose TV (GRADUAL)
7. **Phase 4** - Add mobile/cast support (NEW FEATURES)
8. **Phase 6** - Setup CI/CD pipeline (FINAL)

**Full Plan:** See `docs/Farsihub-Modernization-Plan.md`

**Estimated Timeline:**
- Minimum Viable (Phases 0.5, 1, 1.5): 2-3 weeks
- Full Modernization: 8-12 weeks

---

## Contributing / Development

### Before Committing

1. Run syntax check: `.\gradlew.bat compileDebugKotlin`
2. Run tests: `.\gradlew.bat test`
3. Verify no console warnings/errors
4. Reference modernization phase in commit message

### Code Quality Standards

- Follow Kotlin naming conventions
- Use safe null handling (`?.let`, `?:`, avoid `!!`)
- Prefer immutable data structures
- Document complex logic
- Use lifecycleScope/viewModelScope (not GlobalScope)
- Maintain thread-safe singletons until Hilt migration

### Current Development Gaps

- No dependency injection framework (manual singletons)
- No modularization (single app module)
- Limited ViewModel usage (only 2 screens)
- Partial Media3 migration (80% complete)
- No CI/CD pipeline
- No monitoring/analytics (Firebase disabled)
- Missing repository/ViewModel unit tests

---

## Documentation

**Project Instructions:**
- `CLAUDE.md` - AI assistant context (UPDATED with reality)

**Modernization:**
- `docs/Farsihub-Modernization-Plan.md` - Universal APK roadmap (NEW)

**Audit & Remediation:**
- `docs/REMEDIATION_PROGRESS.md` - Fix tracking (30/33 complete)
- `docs/audit.md` - Original audit report

**Testing:**
- `docs/PHASE_7_TEST_SUITE_SUMMARY.md` - Test documentation

**Technical Guides:**
- `docs/COMPLETE_SCRAPING_GUIDE.md` - Scraping documentation
- Various API analysis docs in `docs/`

---

## License

Proprietary - Personal Use Only

This project is for personal use behind a firewalled network. All rights reserved.

---

## Support

**Project:** FarsiPlex → FarsiHub (rebranding planned)
**Current Device:** Nvidia Shield TV (Android API 28-36)
**Future Support:** Android phones with Cast capability
**Build System:** Gradle 8.x with Kotlin DSL

**Key Technologies:**
- Kotlin 1.9+
- Android Leanback (85%) + Jetpack Compose (15%)
- Room Database 2.5+ (dual architecture)
- Media3 with ExoPlayer v2 imports (80% migrated)
- Manual DI (migrating to Hilt)

**Last Updated:** 2025-11-22
**Version:** 1.0 (91% Audit Compliance - Production Ready)
**Next Version:** 2.0 (FarsiHub with Hilt, modules, and mobile support)