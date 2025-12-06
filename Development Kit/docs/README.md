# FarsiPlex

**Cross-Platform Farsi Streaming Application for Android TV, Tablets, and Phones**

---

## What It Does

FarsiPlex streams Persian/Farsi movies and TV shows from multiple online sources. It aggregates content from 4 streaming sites and provides a unified interface with device-adaptive layouts.

**Key Capabilities:**
- Browse and search Farsi movies, TV series, and episodes
- Play video content in multiple qualities (1080p, 720p, 480p, 360p)
- Resume playback where you left off
- Cast to Chromecast devices
- Save favorites and create playlists
- Download for offline viewing
- Track watch progress and series completion

---

## Supported Platforms

| Platform | UI | Navigation | Orientation |
|----------|-----|------------|-------------|
| Nvidia Shield TV | Compose TV | D-pad sidebar | Landscape |
| Android TV | Compose TV | D-pad sidebar | Landscape |
| Android Tablet | Compose TV | D-pad/touch sidebar | Landscape |
| Android Phone | Compose M3 | Bottom nav (5 tabs) | Portrait |

**API Support:** Android 9-15 (API 28-35)

---

## Content Sources

FarsiPlex aggregates from 4 streaming websites:

| Source | Method | Content Type |
|--------|--------|--------------|
| **Farsiland** | WordPress REST API | Movies, Series, Episodes |
| **FarsiPlex** | Sitemap + DooPlay API | Movies, Series |
| **Namakade** | HTML Scraping | Movies, Series |
| **IMVBox** | REST API + HTML | Movies via YouTube embeds |

Content syncs automatically every 30 minutes (default, configurable) via WorkManager with exponential backoff.

---

## How It Works

### Architecture Overview

```
                    +------------------+
                    |    MainActivity   |
                    +--------+---------+
                             |
              +--------------+--------------+
              |                             |
    +---------v---------+       +-----------v-----------+
    | HomeComposeFragment|       |   Detail Activities    |
    +---------+---------+       | (Movie, Series, Player)|
              |                 +-----------+-----------+
    +---------v---------+                   |
    |  Device Detection  |                   |
    |   (TV/Phone)       |                   |
    +----+----------+----+                   |
         |          |                        |
+--------v---+  +---v--------+               |
|TV Sidebar  |  |Phone Bottom|               |
|Navigation  |  |Navigation  |               |
+-----+------+  +-----+------+               |
      |               |                      |
      +-------+-------+                      |
              |                              |
    +---------v---------+                    |
    |   Compose Screens  |<------------------+
    |  (Movies, Shows,   |
    |  Search, Details)  |
    +---------+---------+
              |
    +---------v---------+
    |    ViewModels      |
    | (MainViewModel,    |
    |  PhoneViewModels)  |
    +---------+---------+
              |
    +---------v---------+
    |   Repositories     |
    | (Content, Favorites|
    |  Watchlist, etc.)  |
    +---------+---------+
              |
    +---------v---------+
    |    Data Layer      |
    | +------+ +-------+ |
    | |AppDB | |Content| |
    | | v11  | | DB v3 | |
    | +------+ +-------+ |
    +---------+---------+
              |
    +---------v---------+
    |   External APIs    |
    | (WordPress, Scraper|
    |  IMVBox, Namakade) |
    +-------------------+
```

### Data Flow

1. **User opens app** → MainActivity detects device type
2. **HomeComposeFragment loads** → Shows TV sidebar or phone bottom nav
3. **User browses content** → Compose screens display data from ViewModels
4. **ViewModels query** → Repositories (ContentRepository primary)
5. **Repository fetches** → Local databases first (fast), scrapers for video URLs
6. **User plays video** → VideoPlayerActivity (ExoPlayer) or IMVBoxWebPlayerActivity (YouTube)
7. **Playback position saved** → PlaybackRepository stores progress to AppDatabase

### Dual Database Design

**AppDatabase v11** - User data (permanent, survives updates):
- PlaybackPosition (resume points)
- Favorite (user favorites)
- WatchlistMovie, MonitoredSeries, EpisodeProgress (tracking)
- Playlist, PlaylistItem (custom playlists)
- SearchHistory (recent searches)
- NotificationPreferences (alert settings)
- DownloadItem (offline content)

**ContentDatabase v3** - Catalog data (replaceable via sync):
- CachedMovie, CachedSeries, CachedEpisode
- CachedGenre (genre mappings)
- CachedVideoUrl (scraped URLs with 5-min TTL)
- CachedMovieFts, CachedSeriesFts, CachedEpisodeFts (FTS4 search indexes)

**Why two databases?**
- Content can be fully replaced during sync without losing user data
- AppDatabase migrations preserve user preferences across app updates
- ContentDatabase can be wiped clean if content source changes

---

## File Structure (148 Production Kotlin Files)

```
G:\FarsiPlex\
├── app/src/main/java/com/example/farsilandtv/
│   │
│   │   # Entry Points (17 files)
│   ├── FarsilandApp.kt              # Application class (@HiltAndroidApp)
│   ├── MainActivity.kt               # Single-activity entry, device detection
│   ├── HomeComposeFragment.kt        # Compose host fragment
│   ├── DetailsActivity.kt            # Movie details (TV/Phone adaptive)
│   ├── SeriesDetailsActivity.kt      # Series details (TV/Phone adaptive)
│   ├── VideoPlayerActivity.kt        # ExoPlayer video playback
│   ├── IMVBoxWebPlayerActivity.kt    # WebView + YouTube (origin spoof)
│   ├── YouTubePlayerActivity.kt      # Standalone YouTube player
│   ├── DatabaseSourceDialogFragment.kt # Source selector dialog
│   ├── SearchActivity.kt             # Legacy (deprecated)
│   ├── FavoritesActivity.kt          # Legacy (deprecated)
│   ├── PlaylistsActivity.kt          # Legacy (deprecated)
│   ├── PlaylistDetailActivity.kt     # Legacy (deprecated)
│   ├── OptionsFragment.kt            # Legacy (deprecated)
│   ├── CardPresenter.kt              # Legacy Leanback (deprecated)
│   ├── Movie.kt                      # Legacy model (deprecated)
│   └── MovieList.kt                  # Legacy model (deprecated)
│   │
│   ├── cast/ (2 files)
│   │   ├── CastManager.kt            # Chromecast session management
│   │   └── CastOptionsProvider.kt    # Cast SDK configuration
│   │
│   ├── data/
│   │   ├── api/ (5 files)
│   │   │   ├── RetrofitClient.kt     # HTTP client (OkHttp + Retrofit)
│   │   │   ├── WordPressApiService.kt # Farsiland WordPress API
│   │   │   ├── FarsiPlexApiService.kt # FarsiPlex sitemap parsing
│   │   │   ├── IMVBoxAuthManager.kt   # IMVBox login/cookie storage
│   │   │   └── BackendApiService.kt   # Future backend (placeholder)
│   │   │
│   │   ├── cache/ (1 file)
│   │   │   └── PrefetchManager.kt    # Image/metadata preloading
│   │   │
│   │   ├── database/ (20 files)
│   │   │   ├── AppDatabase.kt        # User data v11 (permanent)
│   │   │   ├── ContentDatabase.kt    # Content catalog v3 (replaceable)
│   │   │   ├── DatabaseSource.kt     # Source enum (Farsiland/FarsiPlex/etc)
│   │   │   ├── DatabasePreferences.kt # Source selection storage
│   │   │   ├── ContentEntities.kt    # CachedMovie, CachedSeries, CachedEpisode
│   │   │   ├── ContentDao.kt         # Content queries + paging
│   │   │   ├── PlaybackPosition.kt   # Resume tracking entity
│   │   │   ├── PlaybackPositionDao.kt # Resume queries
│   │   │   ├── Favorite.kt           # Favorites entity
│   │   │   ├── FavoriteDao.kt        # Favorites queries
│   │   │   ├── WatchlistEntities.kt  # WatchlistMovie, MonitoredSeries
│   │   │   ├── WatchlistDao.kt       # Watchlist queries
│   │   │   ├── Playlist.kt           # Playlist entity
│   │   │   ├── PlaylistItem.kt       # Playlist item entity
│   │   │   ├── PlaylistDao.kt        # Playlist queries
│   │   │   ├── PlaylistItemDao.kt    # Playlist item queries
│   │   │   ├── PlaylistWithItems.kt  # Playlist + items relation
│   │   │   ├── SearchHistory.kt      # Search history entity
│   │   │   ├── SearchHistoryDao.kt   # Search history queries
│   │   │   └── NotificationPreferences.kt # Notification settings
│   │   │
│   │   ├── download/ (5 files)
│   │   │   ├── DownloadItem.kt       # Download entity
│   │   │   ├── DownloadDao.kt        # Download queries
│   │   │   ├── DownloadManager.kt    # Queue management
│   │   │   ├── DownloadWorker.kt     # Background download worker
│   │   │   └── DownloadConstants.kt  # Config constants
│   │   │
│   │   ├── health/ (1 file)
│   │   │   └── ScraperHealthTracker.kt # Source health monitoring
│   │   │
│   │   ├── imvbox/ (4 files)
│   │   │   ├── IMVBoxApiService.kt   # REST API + web search
│   │   │   ├── IMVBoxVideoExtractor.kt # YouTube ID extraction
│   │   │   ├── IMVBoxHtmlParser.kt   # Metadata parsing
│   │   │   └── IMVBoxUrlBuilder.kt   # URL construction
│   │   │
│   │   ├── model/ (2 files)
│   │   │   ├── Genre.kt              # Genre enum with Persian/English
│   │   │   └── FilterCard.kt         # Filter UI model
│   │   │
│   │   ├── models/ (3 files)
│   │   │   ├── UIModels.kt           # Movie, Series, Episode, FeaturedContent
│   │   │   ├── VideoUrl.kt           # Video URL with quality info
│   │   │   └── wordpress/WPModels.kt # WordPress API response models
│   │   │
│   │   ├── namakade/ (3 files)
│   │   │   ├── NamakadeApiService.kt # API wrapper
│   │   │   ├── NamakadeHtmlParser.kt # HTML parsing
│   │   │   └── NamakadeUrlBuilder.kt # URL construction
│   │   │
│   │   ├── repository/ (7 files)
│   │   │   ├── ContentRepository.kt  # Primary content access (2000+ lines)
│   │   │   ├── FavoritesRepository.kt # Favorites management
│   │   │   ├── WatchlistRepository.kt # Watchlist + monitored series
│   │   │   ├── PlaybackRepository.kt  # Position tracking
│   │   │   ├── PlaylistRepository.kt  # Playlist management
│   │   │   ├── SearchRepository.kt    # FTS4 search
│   │   │   └── NotificationPreferencesRepository.kt
│   │   │
│   │   ├── scraper/ (6 files)
│   │   │   ├── ScraperResult.kt      # Sealed result class
│   │   │   ├── VideoUrlScraper.kt    # Multi-source video URLs
│   │   │   ├── EpisodeListScraper.kt # Episode extraction
│   │   │   ├── EpisodeMetadataScraper.kt # Rich episode data
│   │   │   ├── FarsiPlexMetadataScraper.kt # FarsiPlex metadata
│   │   │   └── WebSearchScraper.kt   # Cross-site web search
│   │   │
│   │   └── sync/ (3 files)
│   │       ├── ContentSyncWorker.kt  # WordPress API sync
│   │       ├── FarsiPlexSyncWorker.kt # Sitemap sync
│   │       └── IMVBoxSyncWorker.kt   # IMVBox content sync
│   │
│   ├── di/ (3 files)
│   │   ├── DatabaseModule.kt         # Database + DAO providers
│   │   ├── NetworkModule.kt          # OkHttp + Retrofit providers
│   │   └── RepositoryModule.kt       # Repository providers
│   │
│   ├── ui/
│   │   ├── components/ (16 files)
│   │   │   ├── MovieCard.kt          # Movie poster card
│   │   │   ├── MovieCardAnimated.kt  # Animated variant
│   │   │   ├── SeriesCard.kt         # Series poster card
│   │   │   ├── EpisodeCard.kt        # Episode thumbnail card
│   │   │   ├── ContentRow.kt         # Horizontal scroll row
│   │   │   ├── FeaturedCarousel.kt   # Auto-rotating hero banner
│   │   │   ├── ContentOptionsDialog.kt # Long-press menu
│   │   │   ├── FilterComponents.kt   # Genre/sort filter chips
│   │   │   ├── GenreBadge.kt         # Genre label badge
│   │   │   ├── StatusBadge.kt        # Favorite/watched indicators
│   │   │   ├── OfflineIndicator.kt   # Network status indicator
│   │   │   ├── ResponsiveCards.kt    # Adaptive card sizing
│   │   │   ├── ShimmerEffect.kt      # Loading skeleton animation
│   │   │   ├── ErrorBoundary.kt      # Error state wrapper
│   │   │   ├── AnimatedButton.kt     # Focus animation button
│   │   │   └── SharedElementTransitions.kt # Navigation transitions
│   │   │
│   │   ├── model/ (1 file)
│   │   │   └── GenreChip.kt          # Genre chip UI model
│   │   │
│   │   ├── presenters/ (1 file)
│   │   │   └── FeaturedCarouselPresenter.kt # Legacy Leanback presenter
│   │   │
│   │   ├── screens/ (10 TV/Tablet screens)
│   │   │   ├── HomeScreenWithSidebar.kt # Main home + sidebar nav
│   │   │   ├── MoviesScreen.kt       # Movie grid + filters
│   │   │   ├── ShowsScreen.kt        # Series grid + filters
│   │   │   ├── SearchScreen.kt       # FTS4 search (shared TV/Phone)
│   │   │   ├── FavoritesScreen.kt    # User favorites
│   │   │   ├── PlaylistsScreen.kt    # User playlists
│   │   │   ├── DownloadsScreen.kt    # Offline downloads
│   │   │   ├── OptionsScreen.kt      # Settings (shared TV/Phone)
│   │   │   ├── MovieDetailsScreen.kt # Movie details (TV)
│   │   │   └── SeriesDetailsScreen.kt # Series details (TV)
│   │   │
│   │   ├── screens/phone/ (7 Phone screens)
│   │   │   ├── PhoneNavigationHost.kt # Bottom nav container
│   │   │   ├── PhoneHomeScreen.kt    # Phone home (featured, rows)
│   │   │   ├── PhoneMoviesScreen.kt  # Phone movies (2-col grid)
│   │   │   ├── PhoneShowsScreen.kt   # Phone series (2-col grid)
│   │   │   ├── PhoneLibraryScreen.kt # Favorites/Downloads/Playlists tabs
│   │   │   ├── PhoneMovieDetailsScreen.kt # Phone movie details
│   │   │   └── PhoneSeriesDetailsScreen.kt # Phone series details
│   │   │
│   │   ├── theme/ (3 files)
│   │   │   ├── Theme.kt              # App theme definition
│   │   │   ├── Color.kt              # Color palette
│   │   │   └── Type.kt               # Typography
│   │   │
│   │   └── viewmodel/ (5 files)
│   │       ├── MainViewModel.kt      # Primary home ViewModel
│   │       ├── DownloadViewModel.kt  # Downloads state
│   │       ├── PhoneHomeViewModel.kt # Phone home state
│   │       ├── PhoneMovieDetailsViewModel.kt
│   │       └── PhoneSeriesDetailsViewModel.kt
│   │
│   └── utils/ (23 files)
│       ├── DeviceUtils.kt            # Device type detection
│       ├── LocalDeviceType.kt        # CompositionLocal for device
│       ├── IntentExtras.kt           # Intent key constants
│       ├── NetworkUtils.kt           # Connectivity monitoring
│       ├── SecureUrlValidator.kt     # HTTPS validation
│       ├── SecureRegex.kt            # ReDoS protection
│       ├── SqlSanitizer.kt           # SQL injection prevention
│       ├── ErrorHandler.kt           # Centralized error handling
│       ├── ImageLoader.kt            # Coil image loading
│       ├── AutoFrameRateHelper.kt    # AFR matching for TV
│       ├── EpisodeFormatter.kt       # S##E## formatting
│       ├── PersianUtils.kt           # Farsi text utilities
│       ├── SourceBadgeHelper.kt      # Source badge rendering
│       ├── RemoteConfig.kt           # CDN mirror config
│       ├── SyncPreferences.kt        # Sync interval config
│       ├── GenrePreferences.kt       # Filter persistence
│       ├── NotificationHelper.kt     # Notification channels
│       ├── BackNavigationManager.kt  # Back button handling
│       ├── FocusMemoryManagerEnhanced.kt # Focus position memory
│       ├── FocusDebugger.kt          # Focus debugging
│       ├── KeyboardShortcutHandler.kt # Remote shortcuts
│       ├── PerformanceOptimization.kt # Compose optimizations
│       └── SkeletonHelper.kt         # Skeleton loading utils
│
├── app/src/test/ (11 unit test files - validated 2025-12-04)
│   └── java/com/example/farsilandtv/
│       ├── NetworkMonitoringTest.kt
│       ├── data/repository/
│       │   ├── ContentRepositoryTest.kt
│       │   ├── PlaybackRepositoryTest.kt
│       │   ├── WatchlistRepositoryTest.kt
│       │   ├── FavoritesRepositoryTest.kt
│       │   ├── SearchRepositoryTest.kt
│       │   └── NotificationPreferencesRepositoryTest.kt
│       ├── data/scraper/VideoUrlScraperTest.kt
│       ├── data/sync/ContentSyncWorkerTest.kt
│       ├── data/download/DownloadManagerTest.kt
│       └── utils/SecureUrlValidatorTest.kt
│
├── app/src/androidTest/ (3 instrumented tests)
│   └── java/com/example/farsilandtv/data/database/
│       ├── IndexPerformanceTest.kt
│       ├── PlaybackPositionDaoTest.kt
│       └── WatchlistDaoTest.kt
│
└── scripts/
    ├── generate_content_database.py
    └── rebuild_farsiland_db.py
```

---

## Video Playback

### Standard Playback (VideoPlayerActivity)

Uses Media3 ExoPlayer for direct video streams:
- Adaptive bitrate (HLS/DASH support)
- Quality selection (1080p/720p/480p/360p)
- Position resume (saves every 10s)
- Speed control (0.5x-2x)
- Skip controls (10s tap, 30s long-press)
- Chromecast handoff via CastManager
- Auto Frame Rate matching for TV
- Network monitoring (auto-pause on disconnect)

### IMVBox YouTube Playback (IMVBoxWebPlayerActivity)

IMVBox hosts movies as YouTube embeds restricted to imvbox.com domain. We use origin spoofing:

```kotlin
// Fake origin so YouTube allows playback
webView.loadDataWithBaseURL(
    "https://www.imvbox.com/movies/play",  // Spoofed origin
    embedHtml,                              // YouTube iframe HTML
    "text/html", "UTF-8", null
)
```

Features:
- YouTube embed with imvbox.com origin
- Auto-skip intro (MutationObserver detects skip button)
- D-pad control via touch simulation
- IMVBox cookies for premium content

---

## Navigation Structure

### TV/Tablet (Sidebar)

```
HomeScreenWithSidebar
├── Sidebar (6 items, D-pad navigable)
│   ├── Home → Featured carousel, Continue Watching, Recent content
│   ├── Movies → 4-column grid, genre/sort filters
│   ├── TV Shows → 4-column grid, genre/sort filters
│   ├── Search → FTS4 full-text search
│   ├── Downloads → Offline content management
│   └── Settings → Options screen
│
└── Detail Screens (via Activity)
    ├── DetailsActivity → MovieDetailsScreen
    └── SeriesDetailsActivity → SeriesDetailsScreen
```

### Phone (Bottom Navigation)

```
PhoneNavigationHost
├── Bottom Nav (5 tabs)
│   ├── Home → PhoneHomeScreen (featured, continue watching)
│   ├── Movies → PhoneMoviesScreen (2-column grid)
│   ├── Shows → PhoneShowsScreen (2-column grid)
│   ├── Search → SearchScreen (shared with TV)
│   └── Library → PhoneLibraryScreen (3 tabs: Favorites/Downloads/Playlists)
│
└── Detail Screens (via Activity)
    ├── DetailsActivity → PhoneMovieDetailsScreen
    └── SeriesDetailsActivity → PhoneSeriesDetailsScreen
```

---

## Technology Stack

| Component | Technology | Version |
|-----------|------------|---------|
| Language | Kotlin | 2.0.0 |
| DI | Hilt | 2.51.1 |
| UI (TV) | Compose TV | 1.0.0-alpha12 |
| UI (Phone) | Compose Material 3 | 2024.11.00 |
| Database | Room | 2.6.1 |
| Player | Media3 ExoPlayer | 1.3.1 |
| Cast | Google Cast SDK | 21.4.0 |
| HTTP | OkHttp + Retrofit | 4.12.0 / 2.9.0 |
| JSON | Moshi | 1.15.1 |
| HTML | JSoup | 1.17.1 |
| Images | Coil | 2.5.0 |
| Paging | Paging 3 | 3.2.1 |
| Background | WorkManager | 2.9.0 |

---

## Build Commands

```bash
# Quick syntax check (fastest)
./gradlew compileDebugKotlin

# Build debug APK
./gradlew assembleDebug

# Run unit tests
./gradlew test

# Install on device
./gradlew installDebug

# Clean build
./gradlew clean assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk`

**Important:** Always use `./gradlew` (Unix-style), never `.\gradlew.bat`

---

## Development Environment

**Machine:** Windows 11 + Git Bash
**Working Directory:** `G:\FarsiPlex`
**Android SDK:** `C:\Users\me\AppData\Local\Android\Sdk`

### Tools

| Tool | Version |
|------|---------|
| Java | 17.0.12 LTS |
| Gradle | 8.13 |
| Python | 3.13.7 |
| ADB | 36.0.0 |
| Git | 2.51.0 |

### SDK Configuration

| Setting | Value |
|---------|-------|
| minSdk | 28 (Android 9) |
| targetSdk | 34 (Android 14) |
| compileSdk | 35 (Android 15) |

### Emulators

| Property | Pixel_9_Pro_XL (Phone) | Namakadeh.com (TV) |
|----------|------------------------|---------------------|
| Device ID | emulator-5554 | emulator-5556 |
| Android Version | 16 (API 36) | 16 (API 36) |
| Architecture | x86_64 | x86 |
| Resolution | 1344x2992 | 3840x2160 (4K) |
| Density | 480 dpi (xxhdpi) | 640 dpi (xxxhdpi) |

---

## Development Notes

### Important Rules

**DO NOT:**
- Merge AppDatabase and ContentDatabase (intentional separation)
- Change applicationId (`com.example.farsilandtv`)
- Skip HTTPS validation in scrapers
- Use blocking calls on Main thread

**ALWAYS:**
- Use `@Inject constructor` for new classes (Hilt migration complete)
- Use `@ApplicationContext` for context injection
- Validate URLs with `SecureUrlValidator`
- Use `lifecycleScope`/`viewModelScope` for coroutines
- Test on real Shield TV after significant changes

### Key Patterns

**Database-First:**
ContentRepository queries local database first for instant UX. Background sync keeps data fresh.

**Reactive Paging:**
Uses `flatMapLatest` to automatically refresh when database source changes.

**Thread Safety:**
- All repositories use `@Singleton` + `@Inject constructor` (Hilt migration complete)
- Caches use `LruCache` (internally synchronized) or `AtomicReference`
- All scraping runs on `Dispatchers.IO`

---

## Test Coverage (Validated 2025-12-04)

| Test File | Status | Notes |
|-----------|--------|-------|
| ContentRepositoryTest | ✅ | Models, filtering, sorting |
| PlaybackRepositoryTest | ✅ | 95% completion threshold |
| WatchlistRepositoryTest | ✅ | 95% completion threshold |
| FavoritesRepositoryTest | ✅ | Content ID format |
| SearchRepositoryTest | ✅ | SQL sanitization, limits |
| NotificationPreferencesRepositoryTest | ✅ | Default values, quiet hours |
| VideoUrlScraperTest | ✅ | Quality/mirror extraction |
| ContentSyncWorkerTest | ✅ | WordPress sync |
| DownloadManagerTest | ✅ | Download states |
| SecureUrlValidatorTest | ✅ | HTTPS, trusted domains |
| NetworkMonitoringTest | ✅ | Connectivity monitoring |

**Integration Tests:** IndexPerformanceTest, PlaybackPositionDaoTest, WatchlistDaoTest

---

## Audit Status

136-issue deep audit completed - **ALL FIXED**

| Priority | Found | Fixed |
|----------|-------|-------|
| Critical | 18 | 18 |
| High | 29 | 29 |
| Medium | 49 | 49 |
| Low | 40 | 40 |
| **Total** | **136** | **136** |

---

## Roadmap

- [x] Compose TV migration (all screens)
- [x] Compose Phone UI (7 screens)
- [x] Media3 ExoPlayer migration
- [x] Chromecast support
- [x] IMVBox YouTube integration
- [x] Deep audit fixes (136 issues)
- [x] Complete Hilt migration (all repositories use @Singleton + @Inject)
- [ ] Modularize architecture
- [ ] CI/CD pipeline

---

## License

Proprietary - Personal Use Only

---

**Version:** 2.7 | **Last Updated:** 2025-12-05 | **Files:** 148 Production + 14 Tests = 162 Total
