# FarsiPlex Project Documentation

## Section 1: Project Overview

**Name:** FarsiPlex Android TV & Phone Application (rebranding to FarsiHub)

**Purpose:** Cross-platform streaming app for Farsi content (TV, Tablet, Phone) with Chromecast support

**Target Platforms:**
- Nvidia Shield TV (primary)
- Android TV devices (API 28-36)
- Android Tablets (API 28+)
- Android Phones (API 28+)

**Architecture:** Full Hilt Dependency Injection
- Hilt annotations on Activities/Fragments/Workers/ViewModels/Repositories
- All repositories migrated to `@Singleton` + `@Inject constructor`
- Full Hilt injection COMPLETE (no more `getInstance()` pattern)

**Audit Status:** Deep audit complete (179 issues found, ALL FIXED) - Production Ready - Updated 2025-12-04

| Priority | Found | Fixed |
|----------|-------|-------|
| Critical | 18 | 18 |
| High | 29 | 29 |
| Medium | 49 | 49 |
| Low | 40 | 40 |
| **Total** | **136** | **136** |

**Fixes by Category:**
- UI (TV/Phone/Components): 51 issues fixed
- Backend (ViewModels, Download, Repository): 43 issues fixed
- Infrastructure (Utils, Cache, Cast): 33 issues fixed
- Build Config & Dependencies: 11 issues fixed
- Test Suite: 11 unit test files + 3 integration test files (all validated 2025-12-04)

**Key Improvements:**
- Progress throttling (500ms/1% minimum)
- Exponential backoff retry patterns
- Cache metrics tracking
- Error classification enums
- Thread-safe collections (ConcurrentHashMap)
- Disk space validation before downloads
- SavedStateHandle for process death recovery

---

## Section 2: Technology Stack

**Language:** Kotlin 2.0.0

**Dependency Injection:**
- Hilt 2.51.1 (Activities, Fragments, Workers, ViewModels, Repositories)
- All repositories now use `@Singleton` + `@Inject constructor` (migration COMPLETE)
- Pattern: Full Hilt injection throughout
- 3 Hilt Modules: DatabaseModule, NetworkModule, RepositoryModule (RepositoryModule now empty - all auto-discovered)

**UI Framework:**
- Jetpack Compose TV (~95% - All TV screens)
- Jetpack Compose Material 3 (~95% - All Phone screens)
- Legacy (~5% - Deprecated activities only)
- Device-adaptive layouts via DeviceUtils + LocalDeviceType

**Database Architecture (DUAL BY DESIGN):**
- **AppDatabase v11** - User data (PERMANENT):
  - PlaybackPosition, Favorite, WatchlistMovie, MonitoredSeries, EpisodeProgress
  - Playlist, PlaylistItem, SearchHistory, NotificationPreferences
  - DownloadItem (offline downloads with progress)
  - 8 migrations (3→11)
- **ContentDatabase v3** - Content catalog (REPLACEABLE):
  - CachedMovie, CachedSeries, CachedEpisode, CachedGenre
  - CachedVideoUrl (scraped URLs with expiry)
  - FTS4 virtual tables: CachedMovieFts, CachedSeriesFts, CachedEpisodeFts (full-text search)
- **CRITICAL:** Never merge these databases - intentional separation!

**Video Player:**
- Media3 ExoPlayer (androidx.media3:media3-exoplayer:1.3.1)
- Media3 UI, Common, DataSource modules
- Media3 Cast integration (media3-cast:1.3.1)
- Custom ExoPlayer control overlay (exo_playback_control_view.xml)
- Auto Frame Rate (AFR) matching via AutoFrameRateHelper

**Chromecast:**
- Google Cast SDK (play-services-cast-framework:21.4.0)
- CastManager singleton with session lifecycle handling
- Default Media Receiver (no custom receiver app)
- Position-preserving handoff between local/remote playback

**Image Loading:** Coil 2.x with lifecycle-aware coroutine scopes

**Networking:**
- OkHttp 4.x with custom interceptors
- Retrofit 2.x + Moshi for WordPress API
- JSoup for HTML scraping
- 10MB HTTP cache + 100-entry video URL LRU cache (5 min TTL)

**Async Patterns:**
- Kotlin Coroutines (Dispatchers.IO, Main)
- LiveData (MainViewModel for home screen data)
- StateFlow (newer components, repository flows)
- Paging 3 (content lists with infinite scroll)

**Background Processing:**
- WorkManager with CoroutineWorker
- ContentSyncWorker: WordPress API sync (Farsiland)
- FarsiPlexSyncWorker: Sitemap scraping (FarsiPlex)
- DownloadWorker: Offline file downloads
- Sync interval: 30 minutes default (configurable, min 15 min due to WorkManager) with exponential backoff

**Build System:** Gradle 8.13 with Kotlin DSL, Version Catalogs

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

# Install on connected device
./gradlew installDebug
```

---

## Section 4: Architecture Deep Dive

### Application Entry Point

**FarsilandApp.kt** (`Application` class):
- Initializes Hilt via `@HiltAndroidApp`
- Sets up SimpleCache for video (100MB)
- Initializes CastManager for Chromecast
- Configures Firebase Crashlytics (if enabled)
- Schedules background sync workers via WorkManager

**MainActivity.kt** (`FragmentActivity`):
- Single-Activity architecture
- Detects device type via DeviceUtils (TV/Tablet/Phone)
- Sets orientation: Landscape for TV/Tablet, Portrait for Phone
- Loads HomeComposeFragment
- Double-back-to-exit on home screen

### Navigation Architecture

**TV/Tablet Flow (Sidebar Navigation):**
```
MainActivity
  └── HomeComposeFragment (@AndroidEntryPoint)
        └── HomeScreenWithSidebar (Compose TV)
              ├── NavigationSidebar (6 items: Home, Movies, TV Shows, Search, Downloads, Settings)
              └── Content Area (switches via Compose state)
                    ├── "home" → HomeContent (Featured, Continue Watching, Recent)
                    ├── "movies" → MoviesScreen (Grid + Filters)
                    ├── "shows" → ShowsScreen (Grid + Filters)
                    ├── "search" → SearchScreen (FTS4 search)
                    ├── "favorites" → FavoritesScreen
                    ├── "playlists" → PlaylistsScreen
                    ├── "downloads" → DownloadsScreen
                    └── "options" → OptionsScreen
```

**Phone Flow (Bottom Navigation):**
```
MainActivity
  └── HomeComposeFragment (@AndroidEntryPoint)
        └── PhoneNavigationHost (Compose Material 3)
              ├── Bottom Navigation (5 tabs)
              │   ├── Home → PhoneHomeScreen (featured, continue watching)
              │   ├── Movies → PhoneMoviesScreen (2-col grid, filters)
              │   ├── Shows → PhoneShowsScreen (2-col grid, filters)
              │   ├── Search → SearchScreen (reused, voice search)
              │   └── Library → PhoneLibraryScreen
              │         ├── Favorites tab (movies, series)
              │         ├── Downloads tab (offline content)
              │         ├── Playlists tab (user collections)
              │         └── Settings gear → OptionsScreen
              └── Detail Screens (via Activity)
                    ├── Movie → DetailsActivity → PhoneMovieDetailsScreen
                    └── Series → SeriesDetailsActivity → PhoneSeriesDetailsScreen
```

**External Navigation (Activity-based):**
- Movie details: `DetailsActivity` → MovieDetailsScreen (TV) / PhoneMovieDetailsScreen (Phone)
- Series details: `SeriesDetailsActivity` → SeriesDetailsScreen (TV) / PhoneSeriesDetailsScreen (Phone)
- Video playback: `VideoPlayerActivity`
- Legacy (deprecated): SearchActivity, FavoritesActivity, PlaylistsActivity, PlaylistDetailActivity

### Data Flow Architecture

```
UI Layer (Compose Screens)
     ↓ observeAsState() / collectAsState()
ViewModel (MainViewModel, DownloadViewModel, Phone*ViewModel)
     ↓ LiveData/StateFlow
Repository (ContentRepository, WatchlistRepository, etc.)
     ↓ Flow/suspend
Database (AppDatabase v11, ContentDatabase v3)
     ↑
API/Scraper (RetrofitClient, VideoUrlScraper, Namakade)
```

### Repository Pattern (Hilt Singleton)

```kotlin
// Pattern used throughout codebase - Full Hilt injection
@Singleton
class ContentRepository @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val wordPressApi: WordPressApiService
) {
    private val videoScraper = VideoUrlScraper
    private val episodeScraper = EpisodeListScraper

    // 30-second in-memory cache per content type
    private val _syncCompletionTrigger = MutableStateFlow(System.currentTimeMillis())

    companion object {
        private const val TAG = "ContentRepository"
        private const val CACHE_TTL_MS = 30_000L // 30 seconds
    }

    // Paging-backed flows for unlimited scrolling
    val movies: Flow<PagingData<Movie>> = repository.getMoviesPaged()
    val series: Flow<PagingData<Series>> = repository.getSeriesPaged()
}
```

---

## Section 5: Key Components Detail

### MainViewModel

**Location:** `ui/viewmodel/MainViewModel.kt`

**Responsibilities:**
- Exposes LiveData for featured content, recent movies, series, episodes
- Manages genre/sort filtering via StateFlow
- Provides Paging 3 flows for infinite scroll
- Auto-refresh on sync completion

**Key Properties:**
```kotlin
val featuredContent: LiveData<List<FeaturedContent>>
val recentMovies: LiveData<List<Movie>>
val recentSeries: LiveData<List<Series>>
val recentEpisodes: LiveData<List<Episode>>
val isLoading: LiveData<Boolean>
val selectedGenre: StateFlow<Genre?>
val selectedSort: StateFlow<SortOption>
```

### Phone ViewModels

**PhoneHomeViewModel** - Phone-specific home screen state
**PhoneMovieDetailsViewModel** - Movie details + similar movies for phone layout
**PhoneSeriesDetailsViewModel** - Series episodes grouped by season for phone

### DownloadViewModel

**Location:** `ui/viewmodel/DownloadViewModel.kt`

**Responsibilities:**
- Tracks download state, progress, completion
- Queue/cancel downloads
- Observes downloaded items from DownloadDao

### VideoPlayerActivity

**Location:** `VideoPlayerActivity.kt`

**Features:**
- ExoPlayer with adaptive bitrate streaming
- Quality selection dialog (1080p, 720p, 480p)
- Playback position persistence (auto-save every 10s)
- Resume playback from saved position
- Chromecast handoff via CastManager
- Skip controls (10s tap, 30s long-press)
- Speed control (0.5x to 2x)
- Network monitoring with auto-pause on disconnect
- Auto Frame Rate (AFR) matching

### IMVBoxWebPlayerActivity

**Location:** `IMVBoxWebPlayerActivity.kt`

**Purpose:** Play IMVBox content with YouTube embeds using origin spoofing

**Features:**
- **Origin Spoofing:** Uses `loadDataWithBaseURL()` with imvbox.com origin
- **YouTube Bypass:** IMVBox is whitelisted by YouTube video owners
- **Auto-skip Intro:** MutationObserver detects and clicks skip button
- **D-pad Control:** Touch simulation for play/pause and seek
- **Fullscreen Support:** WebChromeClient custom view handling
- **Auth Cookies:** Injects IMVBox login cookies for full movie access

**Key Technique:**
```kotlin
// Spoof origin to bypass YouTube embed restrictions
webView.loadDataWithBaseURL(
    "https://www.imvbox.com/movies/play",  // Fake origin
    embedHtml,
    "text/html", "UTF-8", null
)
```

**D-pad Controls (Touch Simulation):**
- Center/Enter: Tap center to toggle play/pause
- Left/Right: Double-tap edges for ±10s seek
- Uses `MotionEvent.dispatchTouchEvent()` for real touch events

### YouTubePlayerActivity

**Location:** `YouTubePlayerActivity.kt`

**Purpose:** Standalone YouTube player for non-IMVBox YouTube content

**Features:**
- YouTube IFrame API with JavaScript bridge
- D-pad control overlay with auto-hide
- Chromecast support via Cast SDK

### VideoUrlScraper

**Location:** `data/scraper/VideoUrlScraper.kt`

**Supports:**
- **Farsiland:** DooPlay REST API + microdata fallback
- **FarsiPlex:** DooPlay REST API with parallel server queries
- **Namakade:** NamakadeApiService HTML scraping

**Caching:**
- LRU cache with 100 entries max (prevents OOM)
- 5-minute TTL per URL
- Thread-safe via `android.util.LruCache`

**Security:**
- HTTPS-only via SecureUrlValidator
- ReDoS protection via input size limits
- Pre-compiled regex patterns (performance)

### Background Workers

**ContentSyncWorker** (Farsiland WordPress API):
- Syncs movies, series, episodes via REST API
- Incremental sync using `modifiedAfter` filter
- Ghost record cleanup
- Health tracking via ScraperHealthTracker

**FarsiPlexSyncWorker** (FarsiPlex sitemap):
- Parses sitemap XML for new content
- Scrapes full metadata using FarsiPlexMetadataScraper
- Incremental sync

**DownloadWorker** (Offline downloads):
- Handles HTTP streaming downloads
- Progress updates, pause/resume
- Disk space validation

---

## Section 6: Directory Structure

```
G:\FarsiPlex\
├── app/
│   ├── src/main/java/com/example/farsilandtv/
│   │   ├── FarsilandApp.kt                    # Application (@HiltAndroidApp)
│   │   ├── MainActivity.kt                     # Entry point, device detection
│   │   ├── HomeComposeFragment.kt              # Compose wrapper (@AndroidEntryPoint)
│   │   ├── DetailsActivity.kt                  # Movie details (TV/Phone adaptive)
│   │   ├── SeriesDetailsActivity.kt            # Series details (TV/Phone adaptive)
│   │   ├── VideoPlayerActivity.kt              # ExoPlayer + Chromecast
│   │   ├── YouTubePlayerActivity.kt            # Standalone YouTube player
│   │   ├── IMVBoxWebPlayerActivity.kt          # IMVBox WebView player (origin spoof)
│   │   ├── DatabaseSourceDialogFragment.kt     # Database selector dialog
│   │   ├── OptionsFragment.kt                  # Legacy options (deprecated)
│   │   ├── SearchActivity.kt                   # Legacy search (deprecated)
│   │   ├── FavoritesActivity.kt                # Legacy favorites (deprecated)
│   │   ├── PlaylistsActivity.kt                # Legacy playlists (deprecated)
│   │   ├── PlaylistDetailActivity.kt           # Legacy playlist detail (deprecated)
│   │   │
│   │   ├── cast/                               # Chromecast (2 files)
│   │   │   ├── CastManager.kt                  # Session lifecycle management
│   │   │   └── CastOptionsProvider.kt          # Cast framework config
│   │   │
│   │   ├── data/
│   │   │   ├── api/                            # Network (5 files)
│   │   │   │   ├── RetrofitClient.kt           # HTTP client setup
│   │   │   │   ├── WordPressApiService.kt      # WordPress REST API
│   │   │   │   ├── FarsiPlexApiService.kt      # Sitemap parsing
│   │   │   │   ├── IMVBoxAuthManager.kt        # IMVBox login/cookie management
│   │   │   │   └── BackendApiService.kt        # Future backend (placeholder)
│   │   │   │
│   │   │   ├── cache/                          # Caching (1 file)
│   │   │   │   └── PrefetchManager.kt          # Image/metadata preloading
│   │   │   │
│   │   │   ├── database/                       # Database (20 files)
│   │   │   │   ├── AppDatabase.kt              # User data v11 (PERMANENT)
│   │   │   │   ├── ContentDatabase.kt          # Content v2 (REPLACEABLE)
│   │   │   │   ├── ContentEntities.kt          # Cached* entities
│   │   │   │   ├── ContentDao.kt               # Content queries + paging
│   │   │   │   ├── DatabaseSource.kt           # Source enum
│   │   │   │   ├── DatabasePreferences.kt      # Source selection
│   │   │   │   ├── PlaybackPosition.kt         # Resume tracking entity
│   │   │   │   ├── PlaybackPositionDao.kt      # Resume queries
│   │   │   │   ├── Favorite.kt                 # Favorites entity
│   │   │   │   ├── FavoriteDao.kt              # Favorites queries
│   │   │   │   ├── Playlist.kt + PlaylistItem.kt # Playlist entities
│   │   │   │   ├── PlaylistDao.kt + PlaylistItemDao.kt
│   │   │   │   ├── SearchHistory.kt            # Search history
│   │   │   │   ├── NotificationPreferences.kt  # Notification settings
│   │   │   │   └── WatchlistEntities.kt        # Legacy watchlist
│   │   │   │
│   │   │   ├── download/                       # Offline (5 files)
│   │   │   │   ├── DownloadItem.kt             # Download entity
│   │   │   │   ├── DownloadDao.kt              # Download queries
│   │   │   │   ├── DownloadManager.kt          # Queue management
│   │   │   │   ├── DownloadWorker.kt           # Background download
│   │   │   │   └── DownloadConstants.kt        # Config constants
│   │   │   │
│   │   │   ├── health/                         # Monitoring (1 file)
│   │   │   │   └── ScraperHealthTracker.kt     # Source health metrics
│   │   │   │
│   │   │   ├── model/                          # Data classes (2 files)
│   │   │   │   ├── Genre.kt                    # Genre enum with Persian/English
│   │   │   │   └── FilterCard.kt               # Filter UI model
│   │   │   │
│   │   │   ├── models/                         # UI Data classes (3 files)
│   │   │   │   ├── UIModels.kt                 # Movie, Series, Episode, Season, Genre, ContinueWatchingItem, FeaturedContent
│   │   │   │   ├── VideoUrl.kt                 # Video URL with quality
│   │   │   │   └── wordpress/WPModels.kt       # WordPress API response models
│   │   │   │
│   │   │   ├── imvbox/                         # IMVBox source (4 files)
│   │   │   │   ├── IMVBoxApiService.kt         # REST API wrapper
│   │   │   │   ├── IMVBoxVideoExtractor.kt     # YouTube ID extraction from HTML
│   │   │   │   ├── IMVBoxHtmlParser.kt         # HTML parsing for metadata
│   │   │   │   └── IMVBoxUrlBuilder.kt         # URL construction
│   │   │   │
│   │   │   ├── namakade/                       # Namakade source (3 files)
│   │   │   │   ├── NamakadeApiService.kt       # API wrapper
│   │   │   │   ├── NamakadeHtmlParser.kt       # HTML parsing
│   │   │   │   └── NamakadeUrlBuilder.kt       # URL construction
│   │   │   │
│   │   │   ├── repository/                     # Business logic (7 files)
│   │   │   │   ├── ContentRepository.kt        # Main content access
│   │   │   │   ├── FavoritesRepository.kt      # Favorites management
│   │   │   │   ├── WatchlistRepository.kt      # Watchlist management
│   │   │   │   ├── PlaybackRepository.kt       # Position tracking
│   │   │   │   ├── PlaylistRepository.kt       # Playlist management
│   │   │   │   ├── SearchRepository.kt         # FTS4 search
│   │   │   │   └── NotificationPreferencesRepository.kt
│   │   │   │
│   │   │   ├── scraper/                        # Content extraction (6 files)
│   │   │   │   ├── ScraperResult.kt            # Sealed result class
│   │   │   │   ├── VideoUrlScraper.kt          # Multi-source video URLs
│   │   │   │   ├── EpisodeListScraper.kt       # Episode extraction
│   │   │   │   ├── EpisodeMetadataScraper.kt   # Rich episode data
│   │   │   │   ├── FarsiPlexMetadataScraper.kt # FarsiPlex metadata
│   │   │   │   └── WebSearchScraper.kt         # (placeholder)
│   │   │   │
│   │   │   └── sync/                           # Background sync (3 files)
│   │   │       ├── ContentSyncWorker.kt        # WordPress API sync
│   │   │       ├── FarsiPlexSyncWorker.kt      # Sitemap sync
│   │   │       └── IMVBoxSyncWorker.kt         # IMVBox content sync
│   │   │
│   │   ├── di/                                 # Hilt modules (3 files)
│   │   │   ├── DatabaseModule.kt               # Database + DAO providers
│   │   │   ├── NetworkModule.kt                # OkHttp + Retrofit providers
│   │   │   └── RepositoryModule.kt             # Repository providers
│   │   │
│   │   ├── ui/
│   │   │   ├── components/                     # Reusable UI (16 files)
│   │   │   │   ├── MovieCard.kt                # Movie poster card
│   │   │   │   ├── MovieCardAnimated.kt        # Animated variant
│   │   │   │   ├── SeriesCard.kt               # Series poster card
│   │   │   │   ├── EpisodeCard.kt              # Episode thumbnail card
│   │   │   │   ├── ContentRow.kt               # Horizontal scroll row
│   │   │   │   ├── FeaturedCarousel.kt         # Auto-rotating hero
│   │   │   │   ├── ContentOptionsDialog.kt     # Long-press menu
│   │   │   │   ├── FilterComponents.kt         # Genre/sort filter chips
│   │   │   │   ├── GenreBadge.kt               # Genre label
│   │   │   │   ├── StatusBadge.kt              # Favorite/Watched badges
│   │   │   │   ├── OfflineIndicator.kt         # Network status
│   │   │   │   ├── ResponsiveCards.kt          # Adaptive sizing
│   │   │   │   ├── ShimmerEffect.kt            # Loading skeleton
│   │   │   │   ├── ErrorBoundary.kt            # Error state wrapper
│   │   │   │   ├── AnimatedButton.kt           # Focus animations
│   │   │   │   └── SharedElementTransitions.kt # Navigation animations
│   │   │   │
│   │   │   ├── model/                          # UI models (1 file)
│   │   │   │   └── GenreChip.kt                # Genre chip UI model
│   │   │   │
│   │   │   ├── presenters/                     # Legacy presenters (1 file)
│   │   │   │   └── FeaturedCarouselPresenter.kt # Leanback presenter (deprecated)
│   │   │   │
│   │   │   ├── screens/                        # TV/Tablet screens (10 files)
│   │   │   │   ├── HomeScreenWithSidebar.kt    # Main home + sidebar nav
│   │   │   │   ├── MoviesScreen.kt             # Movie grid + filters
│   │   │   │   ├── ShowsScreen.kt              # Series grid + filters
│   │   │   │   ├── SearchScreen.kt             # FTS4 search (shared with Phone)
│   │   │   │   ├── FavoritesScreen.kt          # User favorites
│   │   │   │   ├── PlaylistsScreen.kt          # User playlists
│   │   │   │   ├── DownloadsScreen.kt          # Offline downloads
│   │   │   │   ├── OptionsScreen.kt            # Settings (shared with Phone)
│   │   │   │   ├── MovieDetailsScreen.kt       # Movie details (TV)
│   │   │   │   └── SeriesDetailsScreen.kt      # Series details (TV)
│   │   │   │
│   │   │   ├── screens/phone/                  # Phone screens (7 files)
│   │   │   │   ├── PhoneNavigationHost.kt      # Bottom nav container (5 tabs)
│   │   │   │   ├── PhoneHomeScreen.kt          # Phone home (featured, rows)
│   │   │   │   ├── PhoneMoviesScreen.kt        # Phone movies (2-col grid)
│   │   │   │   ├── PhoneShowsScreen.kt         # Phone series (2-col grid)
│   │   │   │   ├── PhoneLibraryScreen.kt       # Phone library (Favorites/Downloads/Playlists)
│   │   │   │   ├── PhoneMovieDetailsScreen.kt  # Phone movie details
│   │   │   │   └── PhoneSeriesDetailsScreen.kt # Phone series details
│   │   │   │
│   │   │   ├── theme/                          # Theme (3 files)
│   │   │   │   ├── Theme.kt                    # App theme definition
│   │   │   │   ├── Color.kt                    # Color palette
│   │   │   │   └── Type.kt                     # Typography
│   │   │   │
│   │   │   └── viewmodel/                      # State management (5 files)
│   │   │       ├── MainViewModel.kt            # Primary home ViewModel
│   │   │       ├── DownloadViewModel.kt        # Downloads state
│   │   │       ├── PhoneHomeViewModel.kt       # Phone home state
│   │   │       ├── PhoneMovieDetailsViewModel.kt
│   │   │       └── PhoneSeriesDetailsViewModel.kt
│   │   │
│   │   └── utils/                              # Utilities (23 files)
│   │       ├── IntentExtras.kt                 # Intent key constants
│   │       ├── DeviceUtils.kt                  # Device type detection
│   │       ├── LocalDeviceType.kt              # CompositionLocal device
│   │       ├── NetworkUtils.kt                 # Connectivity monitoring
│   │       ├── SecureUrlValidator.kt           # HTTPS validation
│   │       ├── SecureRegex.kt                  # ReDoS protection
│   │       ├── SqlSanitizer.kt                 # SQL injection prevention
│   │       ├── RemoteConfig.kt                 # CDN mirror config
│   │       ├── AutoFrameRateHelper.kt          # AFR matching
│   │       ├── ImageLoader.kt                  # Coil image loading
│   │       ├── EpisodeFormatter.kt             # S##E## formatting
│   │       ├── PersianUtils.kt                 # Farsi text handling
│   │       ├── SourceBadgeHelper.kt            # Source badges
│   │       ├── ErrorHandler.kt                 # Centralized errors
│   │       ├── BackNavigationManager.kt        # Back handling
│   │       ├── NotificationHelper.kt           # Notification channels
│   │       ├── SyncPreferences.kt              # Sync config
│   │       ├── GenrePreferences.kt             # Filter persistence
│   │       ├── FocusMemoryManagerEnhanced.kt   # Focus position memory
│   │       ├── FocusDebugger.kt                # Focus debugging
│   │       ├── KeyboardShortcutHandler.kt      # Remote shortcuts
│   │       ├── PerformanceOptimization.kt      # Compose optimization
│   │       └── SkeletonHelper.kt               # Skeleton loading
│   │
│   ├── src/main/res/
│   │   ├── layout/
│   │   │   ├── activity_main.xml
│   │   │   ├── activity_video_player.xml
│   │   │   └── exo_playback_control_view.xml   # Custom player controls
│   │   └── values/
│   │       └── strings.xml                     # Localized strings
│   │
│   └── src/main/assets/
│       └── farsiplex_content.db                # Bundled content database
│
├── scripts/
│   ├── README.md
│   ├── generate_content_database.py            # DB generation
│   └── rebuild_farsiland_db.py
│
├── farsiplex_auto_updater.py                   # Python auto-updater
├── convert_farsiplex_to_app_db.py              # DB conversion tool
├── requirements.txt                             # Python dependencies
├── README.md                                    # Project readme
└── CLAUDE.md                                    # This file
```

---

## Section 7: Data Models

### Content Models (data/models/)

```kotlin
data class Movie(
    val id: Int,
    val title: String,
    val posterUrl: String?,
    val farsilandUrl: String,
    val description: String? = null,
    val year: Int? = null,
    val rating: Float? = null,
    val genres: String? = null
)

data class Series(
    val id: Int,
    val title: String,
    val posterUrl: String?,
    val backdropUrl: String? = null,
    val farsilandUrl: String,
    val description: String? = null,
    val totalSeasons: Int? = null,
    val totalEpisodes: Int? = null
)

data class Episode(
    val id: Int,
    val title: String,
    val thumbnailUrl: String?,
    val farsilandUrl: String,
    val season: Int,
    val episode: Int,
    val seriesId: Int? = null,
    val seriesTitle: String? = null
)

data class VideoUrl(
    val url: String,
    val quality: String,      // "1080p", "720p", "480p"
    val fileSizeMb: Int? = null,
    val mirror: String? = null // "d1.flnd.buzz", "Server 1", etc.
)
```

### Database Entities

**AppDatabase v11 Entities:**
- `PlaybackPosition` - Resume position per content
- `Favorite` - User favorites (movies/series)
- `WatchlistMovie` - Movies to watch later with progress
- `MonitoredSeries` - Series user is tracking
- `EpisodeProgress` - Per-episode progress tracking
- `Playlist`, `PlaylistItem` - User playlists
- `SearchHistory` - Recent searches
- `NotificationPreferences` - Notification settings
- `DownloadItem` - Offline downloads with progress

**ContentDatabase v3 Entities:**
- `CachedMovie`, `CachedSeries`, `CachedEpisode`
- `CachedGenre` - Genre ID to name mapping
- `CachedVideoUrl` - Scraped URLs with expiry
- `CachedMovieFts`, `CachedSeriesFts`, `CachedEpisodeFts` - FTS4 virtual tables

---

## Section 8: Critical Implementation Rules

**DO NOT:**
- Merge AppDatabase and ContentDatabase (intentional separation)
- Change applicationId from `com.example.farsilandtv`
- Skip HTTPS validation in scrapers
- Use blocking calls on Main thread

**ALWAYS:**
- Use `@Inject constructor` for new classes (Hilt migration complete)
- Use `@ApplicationContext` for context injection
- Validate URLs with `SecureUrlValidator` before use
- Use `lifecycleScope`/`viewModelScope` for coroutines
- Handle all scraper results via sealed `ScraperResult` class
- Test on real Shield TV device after significant changes
- Use DeviceUtils to check device type before UI decisions

---

## Section 9: Platform Support Matrix

| Feature | TV/Tablet | Phone |
|---------|-----------|-------|
| Home Screen | HomeScreenWithSidebar | PhoneHomeScreen |
| Navigation | Sidebar (D-pad) | Bottom Nav (5 tabs) |
| Movie Grid | MoviesScreen (4-col) | PhoneMoviesScreen (2-col) |
| Shows Grid | ShowsScreen (4-col) | PhoneShowsScreen (2-col) |
| Search | SearchScreen (shared) | SearchScreen (shared) |
| Library | Sidebar: Favorites/Downloads | PhoneLibraryScreen (3 tabs) |
| Movie Details | MovieDetailsScreen | PhoneMovieDetailsScreen |
| Series Details | SeriesDetailsScreen | PhoneSeriesDetailsScreen |
| Options | OptionsScreen (shared) | Via Library gear icon |
| Orientation | Landscape | Portrait |
| Focus | D-pad focus rings | Touch targets |

**Device Detection:** `DeviceUtils.getDeviceType(context)` returns `DeviceType.TV`, `TABLET`, or `PHONE`

---

## Section 10: Modernization Roadmap

1. ✅ Complete audit fixes (179 issues - ALL FIXED)
2. ✅ Migrate Home to Compose TV
3. ✅ Migrate Movies/Shows/Search to Compose
4. ✅ Add Chromecast support
5. ✅ Add shimmer loading states
6. ✅ Migrate Options to Compose
7. ✅ Migrate Details screens to Compose
8. ✅ Complete Media3 migration (no ExoPlayer v2 imports)
9. ✅ Add Phone UI support (7 phone screens)
10. ✅ Deep audit complete (136 issues fixed, 89 tests added)
11. ✅ IMVBox integration with YouTube playback (origin spoofing)
12. ✅ Complete Hilt migration (all repositories use @Singleton + @Inject)
13. ⏳ Modularize architecture - NEXT
14. ⏳ Setup CI/CD pipeline

---

## Section 11: Development Environment

**Machine:** Windows 11 + Git Bash
**Working Directory:** `G:\FarsiPlex`
**Android SDK:** `C:\Users\me\AppData\Local\Android\Sdk`

**Tools:**
- Java 17.0.12 LTS
- Gradle 8.13
- Python 3.13.7
- ADB 36.0.0
- Git 2.51.0

**SDK Configuration:**
- minSdk: 28 (Android 9)
- targetSdk: 34 (Android 14)
- compileSdk: 35 (Android 15)

**Build Commands:**
```bash
./gradlew compileDebugKotlin    # Quick compile check
./gradlew assembleDebug          # Build debug APK
./gradlew test                   # Run unit tests
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb logcat | grep -i farsi       # View logs
```

**Important:** Always use `./gradlew` (Unix-style), never `.\gradlew.bat`

---

## Section 12: Key Dependencies

```kotlin
// Core
kotlin = "2.0.0"
agp = "8.13.0"

// Hilt
hilt = "2.51.1"

// Compose
composeBom = "2024.11.00"
tvFoundation = "1.0.0-alpha12"
tvMaterial = "1.1.0-alpha01"

// Media3
media3 = "1.3.1"

// Chromecast
playServicesCastFramework = "21.4.0"

// Database
room = "2.6.1"

// Network
okhttp = "4.12.0"
retrofit = "2.9.0"
moshi = "1.15.1"

// Image
coil = "2.5.0"

// Paging
paging = "3.2.1"
```

---

## Section 13: Data Sources

**Farsiland (Primary):**
- WordPress REST API: `/wp-json/wp/v2/movies`, `/tv`, `/episodes`
- DooPlay REST API: `/wp-json/dooplayer/v2/{post_id}/{type}/{num}`
- CDN Mirrors: d1.flnd.buzz, d2.flnd.buzz, s1.farsicdn.buzz

**FarsiPlex:**
- Sitemap scraping: `/sitemap.xml`, `/post-sitemap.xml`
- DooPlay REST API (same pattern as Farsiland)
- Full metadata scraping via FarsiPlexMetadataScraper

**Namakade:**
- HTML scraping via NamakadeApiService
- Custom extraction patterns via NamakadeHtmlParser

**IMVBox:**
- REST API for movie listings and metadata
- HTML scraping for YouTube video ID extraction (data-setup attribute)
- **YouTube Playback:** Uses origin spoofing technique via `loadDataWithBaseURL()`
- **Auth Required:** Full movies require IMVBox login (cookies injected via IMVBoxAuthManager)
- Playback via IMVBoxWebPlayerActivity (WebView with spoofed imvbox.com origin)

---

## Section 14: File Count Summary

| Layer | Files | Key Components |
|-------|-------|----------------|
| Root Activities/Fragments | 17 | FarsilandApp, MainActivity, VideoPlayerActivity, etc. |
| UI - TV Screens | 10 | HomeScreenWithSidebar, MoviesScreen, ShowsScreen, etc. |
| UI - Phone Screens | 7 | PhoneNavigationHost, PhoneHomeScreen, PhoneLibraryScreen, etc. |
| UI - Components | 16 | MovieCard, FeaturedCarousel, ShimmerEffect, etc. |
| UI - Theme | 3 | Theme, Color, Type |
| UI - Model | 1 | GenreChip |
| ViewModels | 5 | MainViewModel, DownloadViewModel, Phone*ViewModels |
| Database | 20 | Entities, DAOs, migrations |
| Data Model | 5 | Genre, FilterCard, UIModels, VideoUrl, WPModels |
| Repository | 7 | ContentRepository, FavoritesRepository, etc. |
| API | 5 | RetrofitClient, WordPress, FarsiPlex, IMVBoxAuthManager |
| IMVBox | 4 | IMVBoxApiService, IMVBoxVideoExtractor, IMVBoxHtmlParser, IMVBoxUrlBuilder |
| Namakade | 3 | NamakadeApiService, NamakadeHtmlParser, NamakadeUrlBuilder |
| Scraper | 6 | VideoUrlScraper, EpisodeListScraper, etc. |
| Sync Workers | 3 | ContentSyncWorker, FarsiPlexSyncWorker, IMVBoxSyncWorker |
| Download | 5 | DownloadItem, DownloadManager, DownloadWorker |
| Utilities | 23 | Security, network, formatting, performance |
| DI Modules | 3 | DatabaseModule, NetworkModule, RepositoryModule |
| Cast | 2 | CastManager, CastOptionsProvider |
| Cache | 1 | PrefetchManager |
| Health | 1 | ScraperHealthTracker |
| UI - Presenters | 1 | FeaturedCarouselPresenter (legacy) |
| **Production Total** | **148** | (some files overlap between categories) |
| Unit Tests | 11 | Repository, scraper, sync, download, utils tests (validated 2025-12-04) |
| Android Tests | 3 | Database instrumented tests |
| **GRAND TOTAL** | **162** | Production-ready cross-platform app |

---

## Section 15: IMVBox Integration Technical Details

### Overview
IMVBox is an Iranian movie streaming service that hosts movies via YouTube embeds. YouTube restricts certain embeds to specific domains (whitelist). IMVBox is whitelisted, so we spoof the origin.

### YouTube Embed Restriction Problem
YouTube video owners can restrict embeds to specific domains. IMVBox movies are restricted to `imvbox.com`. When loaded from our app, YouTube returns Error 153 ("This video is restricted").

### Solution: Origin Spoofing via loadDataWithBaseURL

```kotlin
// In IMVBoxWebPlayerActivity.kt
webView.loadDataWithBaseURL(
    "https://www.imvbox.com/movies/play",  // FAKE origin
    embedHtml,                              // HTML with YouTube iframe
    "text/html",
    "UTF-8",
    null
)
```

This makes the WebView report `window.location.ancestorOrigins[0]` as `imvbox.com`, which YouTube checks.

### Video ID Extraction (Clean Method)
IMVBox stores video metadata in a `data-setup` attribute on their Video.js player. The IMVBoxVideoExtractor parses this JSON to find the YouTube ID:

```kotlin
// In IMVBoxVideoExtractor.kt - Clean extraction from data-setup
val dataSetup = doc.select("[data-setup]").first()?.attr("data-setup")
val json = JSONObject(dataSetup)
val sources = json.getJSONArray("sources")
// Find source with type "video/youtube"
```

### Files Structure
```
data/imvbox/
├── IMVBoxApiService.kt       # REST API for listings
├── IMVBoxVideoExtractor.kt   # YouTube ID extraction from HTML
├── IMVBoxHtmlParser.kt       # Metadata parsing
└── IMVBoxUrlBuilder.kt       # URL construction

data/api/
└── IMVBoxAuthManager.kt      # Login cookie management

data/sync/
└── IMVBoxSyncWorker.kt       # Background content sync

# Activity
IMVBoxWebPlayerActivity.kt    # WebView player with origin spoof
```

### D-pad Control (Known Limitation)
YouTube iframes don't respond to programmatic JavaScript commands when origin is spoofed. The current approach uses Android `MotionEvent` touch simulation:
- **Play/Pause:** Tap at center of WebView
- **Seek ±10s:** Double-tap at left/right edges (YouTube gesture)

**Note:** This approach may have inconsistent results depending on YouTube's internal state.

### Authentication
Full movies require IMVBox login. IMVBoxAuthManager stores credentials and provides cookies for WebView injection.
