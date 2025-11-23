# FarsiHub Enhancement Roadmap

**Created**: 2025-11-22
**Updated**: 2025-11-23
**Status**: Phase 2 Partially Complete
**Strategy**: Quick Wins First → UI Modernization → Phone Support

---

## Overview

This roadmap implements premium features for FarsiHub based on validated technical analysis:
- **Modern UI**: Compose TV with Netflix-style carousel
- **Performance**: Paging 3 integration to eliminate jank
- **Premium Playback**: AFR (Auto Frame Rate Matching) for Shield TV
- **Cross-Platform**: Proper phone support with separate entry points

**Excluded**: Tunneling Mode (DRM) - not needed for Farsi content

---

## Phase 1: Quick Wins (1-2 Days) ⚡

### Priority: HIGH | Risk: LOW | Impact: IMMEDIATE

### 1.1 Enable Video Tunneling ✅
**Effort**: 5 minutes
**File**: `app/src/main/java/com/example/farsilandtv/VideoPlayerActivity.kt`

**Changes**:
```kotlin
// Add to ExoPlayer initialization
val trackSelector = DefaultTrackSelector(this).apply {
    parameters = buildUponParameters()
        .setTunnelingEnabled(true)  // Hardware DSP offload
        .build()
}
```

**Benefits**:
- Better audio/video sync on Shield TV
- Offloads processing to hardware DSP
- Lower CPU usage for 4K HDR content

**Testing**: Verify on Shield TV with 4K content

---

### 1.2 Integrate Paging 3 into Compose Screens ✅
**Effort**: 2-3 hours
**Files**:
- `app/src/main/java/com/example/farsilandtv/ui/screens/MoviesScreen.kt`
- `app/src/main/java/com/example/farsilandtv/ui/screens/ShowsScreen.kt`
- `app/src/main/java/com/example/farsilandtv/ui/screens/HomeScreen.kt`

**Current Status**: Repository methods ready (80% complete)

**Changes**:
```kotlin
// Replace LiveData pattern
val movies by viewModel.recentMovies.observeAsState(emptyList())

// With Paging 3 pattern
val movies = viewModel.getMoviesPaged().collectAsLazyPagingItems()

// In LazyRow
items(movies.itemCount) { index ->
    movies[index]?.let { movie ->
        MovieCard(movie = movie, ...)
    }
}
```

**Benefits**:
- Eliminates UI jank in large lists
- Infinite scroll support
- Memory efficient (only loads visible items)

**Testing**: Scroll through 500+ movie library

---

### 1.3 Fix Video Caching Bug ✅
**Effort**: 2 hours
**File**: `app/src/main/java/com/example/farsilandtv/VideoPlayerActivity.kt` (lines 382-386)

**Current Issue**: SimpleCache disabled due to `IllegalStateException` crashes

**Changes**:
- Re-enable SimpleCache with proper error handling
- Add cache corruption detection
- Implement cache clearing on errors

**Benefits**:
- 2-3x performance boost on poor networks
- Reduces bandwidth usage
- Smoother playback during buffering

**Testing**: Test on 3G network simulation

---

## Phase 2: UI Modernization (1-2 Weeks) 🎨

### Priority: HIGH | Risk: LOW-MEDIUM | Impact: HIGH

### 2.1 Migrate DetailsActivity to Compose TV 🎯
**Effort**: 8-10 hours
**Strategy**: Rewrite static detail screens FIRST (avoid Leanback conflicts)

**Files to Create**:
- `app/src/main/java/com/example/farsilandtv/ui/screens/MovieDetailsScreen.kt`
- `app/src/main/java/com/example/farsilandtv/ui/screens/SeriesDetailsScreen.kt`

**Why First**:
- Static page with minimal scrolling
- No complex navigation conflicts
- Proven Compose components available

**Components to Use**:
- `androidx.tv.material3.Card` for movie poster
- `LazyColumn` for metadata/cast
- `EpisodeCard.kt` for series episodes

**Testing**: Verify D-pad navigation, focus management

---

### 2.2 Implement AFR (Auto Frame Rate Matching) ✅ COMPLETED
**Effort**: 4-6 hours (Actual: 2 hours)
**File**: `app/src/main/java/com/example/farsilandtv/VideoPlayerActivity.kt`
**Status**: Implemented 2025-11-23

**Requirements**:
- Android 11+ (API 30+) - Shield TV compatible
- Extract frame rate from video metadata

**Implementation**:
```kotlin
// Step 1: Extract frame rate
player.addListener(object : Player.Listener {
    override fun onVideoSizeChanged(videoSize: VideoSize) {
        val frameRate = videoSize.pixelWidthHeightRatio // or from Format
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val surfaceView = findViewById<SurfaceView>(R.id.video_surface)
            surfaceView.setFrameRate(
                frameRate,
                Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE,
                Surface.CHANGE_FRAME_RATE_ALWAYS
            )
        }
    }
})
```

**Benefits**:
- Buttery-smooth 24fps cinema motion
- Eliminates judder on 60Hz displays
- Professional cinephile experience

**Testing**: Test with 24fps movies, verify TV refresh rate changes

---

### 2.3 Migrate SearchActivity to Compose TV 🔍
**Effort**: 6-8 hours
**File to Create**: `app/src/main/java/com/example/farsilandtv/ui/screens/SearchScreen.kt`

**Strategy**: Second migration target (simpler than home)

**Components**:
- Compose TV search input
- Paging 3 for search results
- `ContentRow.kt` for results display

**Testing**: Test D-pad text input, search performance

---

### 2.4 Add Compose Carousel ✅ COMPLETED
**Effort**: 4-6 hours (Actual: 45 min)
**File**: `app/src/main/java/com/example/farsilandtv/ComposeHomeFragment.kt`
**Status**: Implemented 2025-11-23

**Strategy**: Replace `FeaturedCarouselPresenter` with existing `FeaturedCarousel.kt`

**Implementation**:
- Wrap `FeaturedCarousel` in `ComposeView`
- Integrate into existing `HomeFragment`
- Keep Leanback navigation (no conflicts)

**Benefits**:
- Netflix-style cinematic backgrounds
- Auto-rotating hero banner
- Modern visual appeal

**Testing**: Verify auto-rotation, D-pad navigation

---

### 2.5 Implement RemoteMediator ⚡
**Effort**: 6-8 hours
**File to Create**: `app/src/main/java/com/example/farsilandtv/data/paging/ContentRemoteMediator.kt`

**Strategy**: Wrap WordPress API pagination for on-demand sync

**Implementation**:
```kotlin
class ContentRemoteMediator(
    private val contentRepository: ContentRepository,
    private val apiService: WordPressApiService
) : RemoteMediator<Int, Movie>() {
    override suspend fun load(
        loadType: LoadType,
        state: PagingState<Int, Movie>
    ): MediatorResult {
        // Fetch from API, insert to ContentDatabase
    }
}
```

**Benefits**:
- Replace 30-min bulk sync with on-demand loading
- Incremental content updates
- Better network efficiency

**Testing**: Monitor sync behavior, verify database updates

---

### 2.6 Logo Selection Feature (Compose TV) 🎨
**Effort**: 4-6 hours
**Prerequisite**: DetailsActivity + SearchActivity migrated to Compose (Phase 2.1-2.3)
**Plan**: See `logo_plan.md` for detailed implementation

**Strategy**: User-selectable app logos using Android activity-alias pattern

**Files to Create**:
- `app/src/main/java/com/example/farsilandtv/ui/compose/LogoSelectionScreen.kt`
- `app/src/main/java/com/example/farsilandtv/data/preferences/LogoPreferences.kt`
- `app/src/main/java/com/example/farsilandtv/utils/LogoSwitcher.kt`

**Components**:
- Compose TV `TvLazyColumn` with `ListItem` for logo options
- Live icon preview (48dp mipmap icons)
- Activity-alias dynamic enablement via `PackageManager`
- IO dispatcher for non-blocking icon switch

**Features**:
- 4 logo options: Embroidery, Origami, Pixel, Watercolor
- Immediate icon change (no app restart required)
- Persisted user preference (SharedPreferences)
- Default: Pixel logo

**Benefits**:
- Modern Compose TV implementation (zero Leanback dependency)
- Enhanced personalization for users
- Future-proof architecture (no technical debt)

**Testing**: Verify icon changes on Shield TV home screen, test D-pad navigation, verify persistence after app restart

---

## Phase 3: Phone Support (Proper Implementation) 📱

### Priority: MEDIUM | Risk: HIGH | Impact: HIGH

### 3.1 Create Mobile Entry Point 📲
**Effort**: 8-12 hours
**Strategy**: "Fork in the road" with separate activities

**Files**:
- Create `app/src/main/java/com/example/farsilandtv/MobileMainActivity.kt`
- Update `app/src/main/AndroidManifest.xml`

**Manifest Changes**:
```xml
<!-- TV Entry Point -->
<activity android:name=".MainActivity">
    <intent-filter>
        <action android:name="android.intent.action.MAIN" />
        <category android:name="android.intent.category.LEANBACK_LAUNCHER" />
    </intent-filter>
</activity>

<!-- Phone Entry Point -->
<activity android:name=".MobileMainActivity">
    <intent-filter>
        <action android:name="android.intent.action.MAIN" />
        <category android:name="android.intent.category.LAUNCHER" />
    </intent-filter>
</activity>

<!-- Allow installation on phones -->
<uses-feature
    android:name="android.software.leanback"
    android:required="false" />
```

**MobileMainActivity Implementation**:
- Simple RecyclerView or "Coming Soon" placeholder
- Portrait orientation support
- Touch-optimized navigation

**Testing**: Install on phone, verify correct activity launches

---

### 3.2 Build Phone UI Layouts 📐
**Effort**: 20-30 hours
**Strategy**: Gradual - start with browse/search only

**Layouts to Create**:
- Phone-friendly card sizes (smaller)
- Portrait mode support
- Touch-optimized spacing
- Bottom navigation bar (vs TV rows)

**Components**:
- Use existing Compose components
- Adapt `ContentRow.kt` for vertical `LazyColumn`
- Scale poster images for smaller screens

**Testing**: Extensive phone testing (Pixel, Samsung)

---

### 3.3 Migrate HomeFragment to Compose TV 🏠
**Effort**: 12-16 hours
**Strategy**: LAST migration (most complex)

**File**: Replace `app/src/main/java/com/example/farsilandtv/HomeFragment.kt` with `HomeScreen.kt`

**Challenges**:
- Complex Leanback navigation conflicts
- D-pad focus management
- Multiple content rows

**Solution**:
- Use `androidx.tv.material3.Carousel` for featured
- Use `LazyColumn` with `LazyRow` items for content rows
- Custom focus handling with `LocalFocusManager`

**Testing**: Extensive D-pad navigation testing on Shield TV

---

## Phase 4: Polish & Complete Media3 Migration (Optional)

### 4.1 Complete Media3 Migration
**Effort**: 3 hours
- Remove legacy ExoPlayer v2 imports
- Update all references to Media3 equivalents

### 4.2 Add Hardware Codec Preference
**Effort**: 1 hour
- Prefer H.264/H.265 hardware decoders on Shield TV
- Configure in `DefaultTrackSelector`

---

## Implementation Order

**Week 1 (Quick Wins)**:
1. Enable tunneling (Day 1)
2. Integrate Paging 3 (Day 1-2)
3. Fix caching bug (Day 2)

**Week 2-3 (UI Modernization)**:
4. Migrate DetailsActivity to Compose (Week 2)
5. Implement AFR (Week 2)
6. Add Compose Carousel (Week 2)
7. Migrate SearchActivity (Week 3)
8. Implement RemoteMediator (Week 3)

**Week 4-5 (Phone Support)**:
9. Create MobileMainActivity (Week 4)
10. Build phone UI layouts (Week 4-5)
11. Migrate HomeFragment (Week 5)

---

## Success Metrics

**Phase 1**:
- ✅ Video tunneling enabled (verify with Shield TV)
- ✅ Paging 3 integrated (scroll 500+ items smoothly)
- ✅ Video cache working (2-3x performance boost)

**Phase 2**:
- ✅ Details screens in Compose (D-pad navigation smooth)
- ✅ AFR working (24fps content matches display)
- ✅ Carousel replaced (auto-rotation works)
- ✅ RemoteMediator syncing (incremental updates)

**Phase 3**:
- ✅ App installable on phones
- ✅ Correct activity launches per device type
- ✅ Phone UI functional (browse/search/play)
- ✅ Home screen in Compose (no Leanback conflicts)

---

## Risk Mitigation

**Low Risk**:
- Phase 1 changes are isolated
- Paging 3 repositories already implemented
- Compose components proven

**Medium Risk**:
- AFR requires Shield TV testing
- RemoteMediator needs careful database sync testing

**High Risk**:
- Phone UI requires extensive multi-device testing
- HomeFragment migration may have focus conflicts

**Mitigation Strategy**:
- Test each phase on Shield TV before proceeding
- Keep Leanback fallback until Compose proven
- Maintain dual database pattern (no merges)

---

## Exclusions (As Requested)

❌ **Tunneling Mode (DRM)**: Not implemented - not needed for Farsi content (20+ hours saved)
❌ **Quick Manifest Hack**: Using proper phone UI instead of just changing `required="false"`

---

## Notes

- All changes maintain audit compliance (30/30 issues fixed)
- Dual database pattern preserved (AppDatabase ≠ ContentDatabase)
- Backward compatible with existing Shield TV users
- No breaking changes to user data

---

**Next Action**: Start Phase 1.1 - Enable Video Tunneling
