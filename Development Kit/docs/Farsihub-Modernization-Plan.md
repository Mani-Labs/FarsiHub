🚀 FarsiHub Universal Modernization Master Plan

Status: UPDATED WITH CODEBASE REALITY CHECK
Target: Universal Android APK (Phone + TV)
Tech Stack: Kotlin, Manual DI → Hilt, Leanback → Compose (Mobile + TV), Media3
Last Audit: 2025-11-21 (30/33 fixes complete - 91%)

⚠️ CRITICAL GUARDRAILS (The "Always On" Rules)

The Dual Database Architecture (DO NOT CHANGE):

Rule: ContentDatabase and AppDatabase MUST remain separate.

Why: ContentDatabase is atomically replaced during sync. AppDatabase holds permanent user data.

Audit Status: Verified as safe and intentional by external audit.

The Application ID Sanctity:

Rule: applicationId in app/build.gradle.kts MUST remain "com.example.farsilandtv".

Why: Changing this deletes all user data (Watchlist, Favorites) upon update.

The "No-Crash" Scraper:

Rule: Scrapers running in WorkManager must NOT attempt to inflate WebViews unless wrapped in a Looper.prepare() block.

Why: Background workers crash instantly if they touch UI components without a Looper.

The Repository Singleton Pattern:

Rule: Until Hilt is implemented, maintain getInstance() pattern for thread safety.

Why: Current manual DI uses double-check locking for thread-safe singletons.

🔍 Phase 0: Current State Assessment

COMPLETED ANALYSIS - Codebase Reality Check Done

Current Architecture:
- [✅] Monolithic single module (app/)
- [✅] Manual dependency injection (no Hilt/Dagger)
- [✅] Dual database pattern (intentional, audit-approved)
- [✅] Leanback primary UI (~85%), Compose emerging (~15%)
- [✅] Media3 libraries added but ExoPlayer imports used
- [✅] 97 tests with 75% coverage (database-focused)
- [✅] 30/33 audit fixes complete (91%)

Working Features:
- [✅] Content sync via WorkManager (30-min intervals)
- [✅] Playback position tracking (consolidated in AppDatabase)
- [✅] Search with FTS4 full-text indexing
- [✅] Multi-source scraping (Farsiland, FarsiPlex, Namakade)
- [✅] In-memory caching (LRU 50-entry) + HTTP cache (10MB)

📅 Phase 0.5: Complete Audit Remediation & Prep

Goal: Finish the remaining audit issues and prepare for modernization.

[ ] 0.5.1 Complete Low Priority Audit Fixes:
   - Fix L1-L5 issues from audit (5 remaining)
   - Document fixes in REMEDIATION_PROGRESS.md
   - Run full test suite to verify no regressions

[ ] 0.5.2 Branding Updates (Safe):
   - Update app_name to "FarsiHub" in strings.xml
   - Update rootProject.name = "FarsiHub" in settings.gradle.kts
   - Add comment: // DO NOT CHANGE: applicationId = "com.example.farsilandtv"

[ ] 0.5.3 Establish Performance Baselines:
   - Document current app startup time
   - Measure memory usage on Shield TV
   - Record sync performance metrics
   - Save crash-free rate from Play Console

🏗️ Phase 1: Dependency Injection Foundation

Goal: Migrate from manual singletons to Hilt for better testability and maintainability.

[ ] 1.1 Add Hilt Dependencies:
   - Add to libs.versions.toml:
     - hilt = "2.48"
     - hilt-android = { module = "com.google.dagger:hilt-android" }
     - hilt-compiler = { module = "com.google.dagger:hilt-compiler" }
   - Update app/build.gradle.kts with kapt and hilt plugins

[ ] 1.2 Application Setup:
   - Add @HiltAndroidApp to FarsilandApp (keep existing initialization)
   - Create di/ package structure
   - Test app still launches with Hilt added

[ ] 1.3 Database Module (Preserve Dual Pattern):
   - Create di/DatabaseModule.kt
   - Provide AppDatabase as @Singleton (user data)
   - Provide ContentDatabase as @Singleton (content catalog)
   - Provide all DAOs from both databases
   - IMPORTANT: Keep databases separate - DO NOT merge!

[ ] 1.4 Repository Migration (Gradual):
   - Phase 1A: Convert ContentRepository to @Inject constructor
     - Keep getInstance() temporarily with @Deprecated
     - Update one screen to test injection
   - Phase 1B: Convert remaining repositories one by one
     - PlaybackRepository
     - WatchlistRepository
     - FavoritesRepository
     - PlaylistRepository
   - Phase 1C: Remove all getInstance() methods

[ ] 1.5 ViewModel Migration:
   - Add @HiltViewModel to existing ViewModels (only 2 exist)
   - Create ViewModels for screens without them:
     - HomeFragment → HomeViewModel
     - MoviesFragment → MoviesViewModel
     - ShowsFragment → ShowsViewModel
   - Replace LiveData with StateFlow in new ViewModels only

🔄 Phase 1.5: Complete Media3 Migration

Goal: Finish the partial Media3 implementation (already 80% done).

[ ] 1.5.1 Update Import Paths:
   - Change all com.google.android.exoplayer2 → androidx.media3.exoplayer
   - Update PlayerView references in XML layouts
   - Fix any API differences (minimal expected)

[ ] 1.5.2 Verify Player Functionality:
   - Test local playback on Shield TV
   - Test playback position save/restore
   - Verify controls work with D-pad

[ ] 1.5.3 Clean Dependencies:
   - Remove any remaining exoplayer2 dependencies
   - Verify only media3 libraries in build.gradle.kts

📦 Phase 2: Modularization (From Scratch)

Goal: Break monolith into modules for better separation and build times.

[ ] 2.1 Create Core Modules:
   - :core:database (both AppDatabase and ContentDatabase)
   - :core:network (Retrofit, API services, scrapers)
   - :core:common (shared utilities, extensions)
   - :core:designsystem (theme, colors, shared composables)

[ ] 2.2 Migration Strategy:
   - Step 1: Move databases and DAOs to :core:database
   - Step 2: Move API services to :core:network
   - Step 3: Move repositories to :core:database (they need both DB and network)
   - Step 4: Keep all UI in app module initially

[ ] 2.3 Dependency Configuration:
   - app depends on all core modules
   - core:database depends on core:common
   - core:network depends on core:common
   - No circular dependencies

[ ] 2.4 ProGuard Rules:
   - Add consumer-rules.pro to each module
   - Keep all Room entities and Moshi models
   - Test release build with R8 enabled

🎨 Phase 3: Compose TV (Gradual Migration)

Goal: Migrate Leanback to Compose for TV while maintaining stability.

Current State: ~15% Compose adoption (ContentRow, EpisodeCard, ErrorBoundary)

[ ] 3.1 Complete Existing Compose Setup:
   - Review existing Compose components
   - Ensure TV Material 3 theme is properly configured
   - Test focus handling in existing Compose components

[ ] 3.2 Migration Strategy (Screen by Screen):
   - Keep HomeFragment as Leanback initially (most complex)
   - Migrate simpler screens first:
     - SearchActivity → SearchScreen
     - Settings → SettingsScreen
     - About → AboutScreen

[ ] 3.3 Home Screen Migration (Later):
   - Create TvHomeScreen using TvLazyColumn
   - Implement proper D-pad focus handling
   - A/B test with feature flag before full switch

[ ] 3.4 Player Controls:
   - Keep ExoPlayer/Media3 PlayerView for now
   - Add Compose overlay for additional controls only

📱 Phase 4: Mobile Support & Cast

Goal: Enable phone support and casting to TV.

[ ] 4.1 Manifest Configuration:
   - Mark leanback as required="false"
   - Add MobileMainActivity
   - Add logic to detect UI mode and launch appropriate activity

[ ] 4.2 Mobile UI (Material 3):
   - Create mobile-specific navigation (BottomNavigation)
   - Implement touch gestures (swipe, pull-to-refresh)
   - Adapt layouts for portrait orientation

[ ] 4.3 Cast Integration:
   - Add androidx.media3:media3-cast
   - Initialize CastContext in FarsilandApp
   - Implement CastPlayer when connected
   - Add mini controller to mobile UI

[ ] 4.4 Deep Linking:
   - Define URI scheme: farsihub://details/{id}
   - Update NotificationHelper with deep links
   - Test from notifications and external apps

[ ] 4.5 Picture-in-Picture:
   - Enable PiP in manifest
   - Implement onUserLeaveHint
   - Test on Android 8+ devices

🧠 Phase 5: Resilience & Performance

Goal: Production-grade reliability and monitoring.

[ ] 5.1 Remote Config Scrapers:
   - Move CSS selectors to remote JSON
   - Implement version control for selectors
   - Add fallback to local config
   - Monitor scraper success rates

[ ] 5.2 Caching Strategy:
   - Replace in-memory LRU cache with Room queries
   - Implement proper cache invalidation
   - Add offline mode support
   - Smart prefetch for next episodes

[ ] 5.3 Performance Monitoring:
   - Integrate Firebase Crashlytics (currently disabled)
   - Add Firebase Performance Monitoring
   - Track cold start times
   - Monitor API latencies

[ ] 5.4 Observability:
   - Structured logging with correlation IDs
   - Error boundaries for Compose screens
   - Network request/response logging
   - User analytics (optional)

[ ] 5.5 TV Watch Next Integration:
   - Implement TvContractCompat
   - Publish to Android TV home screen
   - Update on playback progress

🚀 Phase 6: Distribution & Maintenance

Goal: Streamline releases and updates.

[ ] 6.1 CI/CD Pipeline:
   - GitHub Actions for automated testing
   - APK generation on tags
   - Automated Play Store deployment
   - Beta channel via Firebase App Distribution

[ ] 6.2 Build Variants:
   - AAB for Play Store (optimized per-device APKs)
   - Universal APK for sideloading (all ABIs)
   - Debug/Release/Beta flavors

[ ] 6.3 Self-Update Mechanism:
   - Check GitHub releases for updates
   - Download and prompt installation
   - Required for rapid scraper fixes

[ ] 6.4 Release Checklist Automation:
   - Lint checks must pass
   - Tests must pass (maintain 75%+ coverage)
   - ProGuard/R8 verification
   - APK size checks

✅ Success Metrics & Verification

Performance Targets:
- [ ] App startup: < 2 seconds on Shield TV
- [ ] Memory usage: < 150MB on TV, < 100MB on phone
- [ ] Crash-free rate: > 99.5%
- [ ] Scraper success: > 85%
- [ ] Test coverage: > 75% (currently at 75%)

Functional Requirements:
- [ ] Installs on Pixel (Phone) AND Shield TV
- [ ] Existing user data preserved after update
- [ ] D-pad navigation works (no focus traps)
- [ ] Touch scrolling works on phone
- [ ] Cast connects and plays
- [ ] Background sync doesn't crash
- [ ] Notifications open correct screen
- [ ] PiP works on mobile
- [ ] Release build with R8 works

🚨 Risk Mitigation

Database Risks:
- Risk: Accidentally merging dual databases
  Mitigation: Keep audit documentation, add code comments

- Risk: Migration corrupts user data
  Mitigation: Backup AppDatabase before any migration

DI Migration Risks:
- Risk: Hilt breaks existing singleton pattern
  Mitigation: Gradual migration, keep getInstance() deprecated

UI Migration Risks:
- Risk: Compose breaks TV navigation
  Mitigation: Test each screen thoroughly, feature flags

Scraper Risks:
- Risk: Sites change, breaking scrapers
  Mitigation: Remote config, multiple fallback sources

📊 Current vs Target Architecture

| Component | Current | Target | Priority |
|-----------|---------|--------|----------|
| DI Framework | Manual singletons | Hilt | HIGH |
| Module Structure | Monolithic | Multi-module | MEDIUM |
| Database | Dual (keep it!) | Dual (keep it!) | N/A |
| UI Framework | 85% Leanback, 15% Compose | 20% Leanback, 80% Compose | LOW |
| Player | Media3 with ExoPlayer imports | Pure Media3 | HIGH |
| Testing | 75% coverage (DB-focused) | 80% coverage (all layers) | MEDIUM |
| Monitoring | None | Firebase suite | MEDIUM |
| Distribution | Manual APK | CI/CD + Auto-update | LOW |

📝 Implementation Notes

1. NEVER merge the dual database architecture - it's correct as-is
2. Complete remaining audit fixes before major refactoring
3. Test on real Shield TV device after each phase
4. Keep backwards compatibility for existing users
5. Gradual rollout with feature flags where possible
6. Document API changes for scraper maintainers
7. Keep manual APK distribution as fallback

---

Phase Prioritization (Recommended Order):
1. Phase 0.5 - Complete audit fixes (IMMEDIATE)
2. Phase 1 - Add Hilt DI (HIGH VALUE)
3. Phase 1.5 - Complete Media3 (EASY WIN)
4. Phase 2 - Modularization (LONG TERM)
5. Phase 5 - Resilience (OPERATIONAL)
6. Phase 3 - Compose TV (GRADUAL)
7. Phase 4 - Mobile/Cast (NEW FEATURES)
8. Phase 6 - Distribution (FINAL)

Total Estimated Effort: 8-12 weeks for full implementation
Minimum Viable Modernization: Phase 0.5 + 1 + 1.5 (2-3 weeks)