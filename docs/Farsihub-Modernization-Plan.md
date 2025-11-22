🚀 FarsiHub Universal Modernization Master Plan

Status: VALIDATED & REFINED
Target: Universal Android APK (Phone + TV)
Tech Stack: Kotlin, Hilt, Jetpack Compose (Mobile + TV), Media3

⚠️ CRITICAL GUARDRAILS (The "Always On" Rules)

The Database Lock:

Rule: ContentRepository MUST be a @Singleton.

Check: NEVER instantiate AppDatabase manually. Always inject it.

Why: Prevents SQLiteDatabaseLockedException when the Scraper service and UI try to write simultaneously.

The Application ID Sanctity:

Rule: applicationId in app/build.gradle.kts MUST remain "com.example.farsilandtv".

Why: Changing this deletes all user data (Watchlist, Favorites) upon update.

The "No-Crash" Scraper:

Rule: Scrapers running in WorkManager must NOT attempt to inflate WebViews unless wrapped in a standard Looper.prepare() block or running on the Main thread.

Why: Background workers crash instantly if they touch UI components without a Looper.

📅 Phase 0: Branding & Safe Refactor Prep

Goal: Rename visible elements without breaking the compilation.

[ ] 0.1 String Updates: Update app_name to "FarsiHub" in strings.xml.

[ ] 0.2 Gradle Root: Update rootProject.name = "FarsiHub" in settings.gradle.kts.

[ ] 0.3 AppID Lock: Add a comment in build.gradle.kts: // DO NOT CHANGE: applicationId = "com.example.farsilandtv".

🏗️ Phase 1: The Plumbing (Hilt & StateFlow)

Goal: Remove ContentRepository.getInstance() and enforce Dependency Injection.

[ ] 1.1 Dependencies: Add Hilt to libs.versions.toml and build.gradle.kts.

[ ] 1.2 App Class: Rename FarsilandApp -> FarsiHubApp. Add @HiltAndroidApp.

[ ] 1.3 Core Module: Create di/DatabaseModule.kt.

Provide AppDatabase.

Provide ContentDao, WatchlistDao, etc.

[ ] 1.4 Repo Refactor:

Refactor ContentRepository to use @Inject constructor.

DELETE the companion object { fun getInstance() } method.

Fix all red compilation errors by injecting the repo into ViewModels/Factories.

[ ] 1.5 ViewModel Migration:

Convert MainViewModel to @HiltViewModel.

Replace LiveData with private val _state = MutableStateFlow() / val state = _state.asStateFlow().

🔄 Phase 1.5: Media3 Migration (CRITICAL ADDITION)

Goal: Replace ExoPlayer v2 with Media3 to enable seamless Casting in Phase 4.

[ ] 1.5.1 Remove ExoPlayer: Remove com.google.android.exoplayer2 dependencies.

[ ] 1.5.2 Add Media3: Add androidx.media3:media3-exoplayer, media3-ui, and media3-common.

[ ] 1.5.3 Refactor Player:

Replace SimpleExoPlayer with ExoPlayer (Media3 interface).

Update PlayerView XML references to androidx.media3.ui.PlayerView.

Verify: Ensure local video playback works on TV before proceeding.

📦 Phase 2: Modularization

Goal: Isolate the data layer so it can be shared between TV and Mobile UIs.

[ ] 2.1 Create Modules:

:core:data (Database, API, Scrapers, Models)

:core:designsystem (Theme, Colors, Type - Shared between TV/Mobile)

:feature:home (The UI logic)

[ ] 2.2 The Great Migration:

Move AppDatabase, Daos, Entities to :core:data.

Move RetrofitClient and API services to :core:data.

Move Scraper logic to :core:data.

[ ] 2.3 Dependency Wiring: app depends on all feature/core modules.

[ ] 2.4 ProGuard Safety: Create consumer-rules.pro in :core:data to keep Gson models from being stripped by R8/Minification.

🎨 Phase 3: Compose TV (The "Big Screen" Rewrite)

Goal: Replace Leanback BrowseFragment with Compose for TV.

[ ] 3.1 Dependencies: Add androidx.tv.material3 and androidx.navigation:navigation-compose.

[ ] 3.2 Theme Setup: Implement FarsiHubTvTheme in :core:designsystem.

[ ] 3.3 Home Screen:

Create TvHomeScreen.kt using TvLazyColumn and StandardCard.

Focus Handling: Ensure D-Pad navigation highlights cards correctly.

[ ] 3.4 Detail Screen: Create TvDetailsScreen.kt using Details scaffold from TV Material3.

[ ] 3.5 Player Overlay: Create a Compose-based overlay for the Media3 player.

[ ] 3.6 Universal Search Logic: Extract search logic into SearchViewModel (Shared) so it can drive both TvSearchScreen and MobileSearchScreen.

📱 Phase 4: Mobile & Cast (The Universal Expansion)

Goal: Enable the APK to run on Phones and Cast to TV.

[ ] 4.1 Manifest Split:

Mark leanback as required="false".

Add MobileMainActivity with android.intent.category.LAUNCHER.

Logic: On app launch, check uiMode. If TV -> Start TV Activity (or NavHost). If Phone -> Start Mobile Activity.

[ ] 4.2 Mobile UI:

Create MobileHomeScreen using standard androidx.compose.material3 (Scaffold, LazyColumn).

Implement Touch gestures (pull-to-refresh, swipe).

[ ] 4.3 Permissions: Implement Android 13 POST_NOTIFICATIONS request logic in MobileMainActivity.

[ ] 4.4 Cast Integration:

Add androidx.media3:media3-cast.

Initialize CastContext in FarsiHubApp.

Player Switcher: In PlayerViewModel, implement logic to return CastPlayer if connected, otherwise ExoPlayer.

[ ] 4.5 The Mini Controller:

Add a persistent bottom bar in MobileMainActivity Scaffold that shows "Now Casting: [Movie Title]" with Pause/Stop buttons.

[ ] 4.6 Deep Linking:

Define Deep Links in NavHost (e.g., farsihub://details/{id}).

Update NotificationHelper to build PendingIntents using these URIs so tapping a notification opens the specific movie.

[ ] 4.7 Picture-in-Picture (PiP):

Enable android:supportsPictureInPicture="true" in Manifest.

Implement onUserLeaveHint in VideoPlayerActivity to trigger PiP mode automatically when swiping home.

🧠 Phase 5: Resilience & Performance

Goal: Ensure the app doesn't break when websites change.

[ ] 5.1 Remote Config Scraper:

Move scraper selectors (CSS classes/IDs) to a JSON file hosted on Firebase/GitHub.

App fetches JSON on startup. ScraperEngine uses these selectors dynamically.

[ ] 5.2 Baseline Profiles: Generate profiles to speed up cold start on TV devices (which often have slow CPUs).

[ ] 5.3 TV Watch Next Channel:

Implement TvContractCompat.

Publish "Continue Watching" items to the Android TV Home Screen so users can resume playback without opening the app first.

🚀 Phase 6: Distribution & Maintenance

Goal: Optimize the final artifact and ensure longevity.

[ ] 6.1 App Bundle (AAB): Configure build to produce .aab. This allows Google Play to serve smaller, optimized APKs to phones vs TVs.

[ ] 6.2 Universal APK (Fallback): Maintain a CI workflow to generate a "Universal APK" (containing all ABIs and resources) for manual sideloading on FireTV/Android boxes.

[ ] 6.3 In-App Self-Updater:

Crucial for Scrapers: Since scrapers break often, you need a way to push hotfixes without Play Store approval.

Implement a check against a GitHub Release JSON.

If a new version exists, prompt the user to download and install the APK (requires REQUEST_INSTALL_PACKAGES permission).

✅ Verification Checklist (The Definition of Done)

[ ] Install: Installs on Pixel (Phone) AND Nvidia Shield (TV).

[ ] Data: Existing Watchlist items appear after update.

[ ] Focus: TV D-Pad never gets "stuck" (focus lost).

[ ] Touch: Phone UI is fully scrollable.

[ ] Cast:

Cast connects.

Video plays on TV.

Phone shows Mini Controller.

Phone volume keys control TV volume.

[ ] Sync: Background sync works without crashing (Hilt context check).

[ ] Notifications: Tapping a "New Episode" notification opens the correct details screen.

[ ] PiP: Video continues playing in a small window when swiping home (Mobile).

[ ] Updates: App detects a newer version from the remote JSON and prompts update.

[ ] Release Build: App compiles with minifyEnabled true and data loads correctly (ProGuard check).