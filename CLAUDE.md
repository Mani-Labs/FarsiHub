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

**Audit Status:** 136 issues fixed - Production Ready (2025-12-04)

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

## Section 6: Critical Implementation Rules

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

## Section 7: Roadmap (Pending)

- ⏳ Modularize architecture
- ⏳ Setup CI/CD pipeline

---

**Full documentation:** See `Development Kit/docs/README.md`