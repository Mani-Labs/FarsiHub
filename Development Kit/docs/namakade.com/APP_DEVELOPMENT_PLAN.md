# Namakade Android App - Complete Development Plan

## Project Overview

**App Name**: Namakade (Negahestan)
**Platform**: Android
**Min SDK**: 21 (Android 5.0 Lollipop)
**Target SDK**: 34 (Android 14)
**Language**: Kotlin
**Architecture**: MVVM + Clean Architecture

**Purpose**: A standalone Android application that pulls content from Namakade.com and streams Persian/Farsi TV series, movies, shows, live TV, and music videos.

---

## Table of Contents

1. [Project Phases](#1-project-phases)
2. [Technology Stack](#2-technology-stack)
3. [App Architecture](#3-app-architecture)
4. [Feature Specifications](#4-feature-specifications)
5. [Development Roadmap](#5-development-roadmap)
6. [Implementation Guide](#6-implementation-guide)
7. [Testing Strategy](#7-testing-strategy)
8. [Deployment Plan](#8-deployment-plan)
9. [Legal & Compliance](#9-legal--compliance)
10. [Budget & Resources](#10-budget--resources)

---

## 1. Project Phases

### Phase 1: Foundation (Weeks 1-3)
- ✅ Website analysis (COMPLETED)
- ✅ API documentation (COMPLETED)
- ✅ Streaming protocol analysis (COMPLETED)
- 🔲 Legal authorization from Proud Holding LLC
- 🔲 Project setup and architecture
- 🔲 Core networking layer
- 🔲 Basic UI framework

### Phase 2: Core Features (Weeks 4-8)
- 🔲 Content browsing (series, movies, shows)
- 🔲 Video player implementation
- 🔲 Search functionality
- 🔲 Content details pages
- 🔲 Episode listing

### Phase 3: Advanced Features (Weeks 9-12)
- 🔲 Live TV integration
- 🔲 User authentication
- 🔲 Favorites/watchlist
- 🔲 Continue watching
- 🔲 Quality selection
- 🔲 Subtitles support (if available)

### Phase 4: Polish & Optimization (Weeks 13-15)
- 🔲 Performance optimization
- 🔲 Offline caching
- 🔲 Analytics integration
- 🔲 Crash reporting
- 🔲 UI/UX refinements
- 🔲 Accessibility improvements

### Phase 5: Testing & Launch (Weeks 16-18)
- 🔲 QA testing
- 🔲 Beta testing with users
- 🔲 Bug fixes
- 🔲 Play Store submission
- 🔲 Marketing materials
- 🔲 Launch

---

## 2. Technology Stack

### 2.1 Core Android

| Component | Technology | Version |
|-----------|-----------|---------|
| Language | Kotlin | 1.9+ |
| Min SDK | Android 5.0 (Lollipop) | API 21 |
| Target SDK | Android 14 | API 34 |
| Build System | Gradle (Kotlin DSL) | 8.2+ |

### 2.2 Architecture & UI

| Component | Technology | Purpose |
|-----------|-----------|---------|
| Architecture | MVVM + Clean Architecture | Separation of concerns |
| Dependency Injection | Hilt | Dependency management |
| Navigation | Jetpack Navigation | Fragment navigation |
| UI Framework | Material Design 3 | Modern UI components |
| View Binding | View Binding | Type-safe view access |
| Coroutines | Kotlin Coroutines | Asynchronous programming |
| Flow | Kotlin Flow | Reactive data streams |

### 2.3 Networking & Data

| Component | Technology | Purpose |
|-----------|-----------|---------|
| HTTP Client | Retrofit + OkHttp | REST API calls |
| JSON Parsing | Moshi | JSON serialization |
| HTML Parsing | Jsoup | Web scraping |
| Image Loading | Glide | Image caching & loading |
| Local Database | Room | Local data persistence |
| DataStore | Preferences DataStore | Settings storage |

### 2.4 Media Playback

| Component | Technology | Purpose |
|-----------|-----------|---------|
| Video Player | ExoPlayer | HLS video playback |
| HLS Support | ExoPlayer HLS module | Adaptive streaming |
| Media Session | MediaSessionCompat | Media controls |
| Picture-in-Picture | PiP API | Background playback |

### 2.5 Additional Libraries

| Component | Technology | Purpose |
|-----------|-----------|---------|
| Logging | Timber | Debug logging |
| Analytics | Firebase Analytics | Usage tracking |
| Crash Reporting | Firebase Crashlytics | Error monitoring |
| Remote Config | Firebase Remote Config | Feature flags |
| Performance | Firebase Performance | Performance monitoring |
| Testing | JUnit, Espresso, Mockk | Unit & UI testing |

---

## 3. App Architecture

### 3.1 Clean Architecture Layers

```
┌─────────────────────────────────────────┐
│         Presentation Layer              │
│  (Activities, Fragments, ViewModels)    │
└─────────────────────────────────────────┘
                   ↓
┌─────────────────────────────────────────┐
│          Domain Layer                   │
│   (Use Cases, Repository Interfaces,    │
│    Domain Models)                       │
└─────────────────────────────────────────┘
                   ↓
┌─────────────────────────────────────────┐
│          Data Layer                     │
│  (Repository Implementations, Data      │
│   Sources, API, Database)               │
└─────────────────────────────────────────┘
```

### 3.2 Package Structure

```
com.namakade.app
├── presentation
│   ├── main
│   │   └── MainActivity.kt
│   ├── home
│   │   ├── HomeFragment.kt
│   │   └── HomeViewModel.kt
│   ├── series
│   │   ├── SeriesListFragment.kt
│   │   ├── SeriesDetailFragment.kt
│   │   └── SeriesViewModel.kt
│   ├── movies
│   │   ├── MoviesFragment.kt
│   │   ├── MovieDetailFragment.kt
│   │   └── MoviesViewModel.kt
│   ├── player
│   │   ├── PlayerActivity.kt
│   │   └── PlayerViewModel.kt
│   ├── livetv
│   │   ├── LiveTVFragment.kt
│   │   └── LiveTVViewModel.kt
│   └── common
│       └── BaseViewModel.kt
├── domain
│   ├── model
│   │   ├── Series.kt
│   │   ├── Episode.kt
│   │   ├── Movie.kt
│   │   └── LiveChannel.kt
│   ├── repository
│   │   ├── SeriesRepository.kt
│   │   ├── MoviesRepository.kt
│   │   └── LiveTVRepository.kt
│   └── usecase
│       ├── GetSeriesListUseCase.kt
│       ├── GetEpisodeStreamUseCase.kt
│       └── SearchContentUseCase.kt
├── data
│   ├── repository
│   │   ├── SeriesRepositoryImpl.kt
│   │   └── MoviesRepositoryImpl.kt
│   ├── remote
│   │   ├── api
│   │   │   └── NamakadeApi.kt
│   │   ├── scraper
│   │   │   ├── SeriesScraper.kt
│   │   │   └── MovieScraper.kt
│   │   └── dto
│   │       ├── SeriesDto.kt
│   │       └── MovieDto.kt
│   ├── local
│   │   ├── database
│   │   │   ├── AppDatabase.kt
│   │   │   └── dao
│   │   │       ├── SeriesDao.kt
│   │   │       └── FavoritesDao.kt
│   │   └── entity
│   │       ├── SeriesEntity.kt
│   │       └── FavoriteEntity.kt
│   └── mapper
│       ├── SeriesMapper.kt
│       └── MovieMapper.kt
└── di
    ├── AppModule.kt
    ├── NetworkModule.kt
    ├── DatabaseModule.kt
    └── RepositoryModule.kt
```

### 3.3 Data Flow Example

```
User Action (Click on Series)
        ↓
    Fragment
        ↓
   ViewModel (observes LiveData/StateFlow)
        ↓
   Use Case
        ↓
  Repository Interface
        ↓
Repository Implementation
        ↓
   Data Source (API/Scraper + Local Cache)
        ↓
  Network/Database
        ↓
   Response flows back up ↑
```

---

## 4. Feature Specifications

### 4.1 Home Screen

**Components**:
- App bar with logo and search icon
- Horizontal carousels for:
  - Featured content
  - Continue watching
  - Trending series
  - New movies
  - Popular shows
  - Live TV channels
- Bottom navigation (Home, Series, Movies, Shows, Profile)

**Technical Implementation**:
```kotlin
// HomeViewModel.kt
class HomeViewModel @Inject constructor(
    private val getHomeContentUseCase: GetHomeContentUseCase
) : ViewModel() {

    private val _homeState = MutableStateFlow<HomeState>(HomeState.Loading)
    val homeState: StateFlow<HomeState> = _homeState.asStateFlow()

    init {
        loadHomeContent()
    }

    private fun loadHomeContent() {
        viewModelScope.launch {
            getHomeContentUseCase()
                .catch { e ->
                    _homeState.value = HomeState.Error(e.message ?: "Unknown error")
                }
                .collect { content ->
                    _homeState.value = HomeState.Success(content)
                }
        }
    }
}

sealed class HomeState {
    object Loading : HomeState()
    data class Success(val content: HomeContent) : HomeState()
    data class Error(val message: String) : HomeState()
}
```

### 4.2 Series Listing

**Features**:
- Grid layout with poster images
- Filter by genre
- Sort by: Latest, Popular, A-Z
- Infinite scroll pagination
- Pull to refresh

**Data Model**:
```kotlin
data class Series(
    val id: String,
    val title: String,
    val titleFarsi: String?,
    val description: String?,
    val posterUrl: String,
    val bannerUrl: String?,
    val genres: List<String>,
    val totalEpisodes: Int,
    val totalSeasons: Int,
    val rating: Float,
    val year: Int?
)
```

### 4.3 Series Detail

**Components**:
- Banner image
- Title and metadata (year, genre, episodes)
- Description
- Star rating
- Season selector
- Episode list (grid or list)
- "Add to Favorites" button
- "Share" button

**Episodes List**:
```kotlin
data class Episode(
    val number: Int,
    val title: String,
    val slug: String,
    val thumbnailUrl: String,
    val duration: Int?, // in seconds
    val description: String?,
    val streamUrl: String? // Extracted on demand
)
```

### 4.4 Video Player

**Features**:
- Fullscreen playback
- Adaptive quality selection
- Manual quality override
- Play/pause, seek
- Fast forward/rewind (10s)
- Volume control
- Brightness control (swipe gesture)
- Picture-in-Picture support
- Auto-play next episode
- Continue watching from last position

**Player Implementation**:
```kotlin
class PlayerViewModel @Inject constructor(
    private val getStreamUrlUseCase: GetStreamUrlUseCase,
    private val updateWatchProgressUseCase: UpdateWatchProgressUseCase
) : ViewModel() {

    private val _playerState = MutableStateFlow<PlayerState>(PlayerState.Loading)
    val playerState: StateFlow<PlayerState> = _playerState.asStateFlow()

    fun loadEpisode(seriesId: String, episodeId: String) {
        viewModelScope.launch {
            _playerState.value = PlayerState.Loading

            getStreamUrlUseCase(seriesId, episodeId)
                .catch { e ->
                    _playerState.value = PlayerState.Error(e.message ?: "Failed to load stream")
                }
                .collect { streamUrl ->
                    _playerState.value = PlayerState.Ready(streamUrl)
                }
        }
    }

    fun saveWatchProgress(episodeId: String, position: Long) {
        viewModelScope.launch {
            updateWatchProgressUseCase(episodeId, position)
        }
    }
}
```

### 4.5 Search

**Features**:
- Search across all content types
- Real-time search (debounced)
- Search history
- Filter by content type (All, Series, Movies, Shows)
- Recent searches

**Search Implementation**:
```kotlin
class SearchViewModel @Inject constructor(
    private val searchContentUseCase: SearchContentUseCase
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val searchResults: StateFlow<List<SearchResult>> = searchQuery
        .debounce(300) // Wait 300ms after user stops typing
        .filter { it.length >= 2 } // Minimum 2 characters
        .distinctUntilChanged()
        .flatMapLatest { query ->
            searchContentUseCase(query)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }
}
```

### 4.6 Live TV

**Features**:
- Grid of live channels with logos
- Channel categories (News, Entertainment, Sports, etc.)
- Now playing info (if available)
- EPG (Electronic Program Guide) if available

**Live Channel Model**:
```kotlin
data class LiveChannel(
    val id: String,
    val name: String,
    val logoUrl: String,
    val streamUrl: String,
    val category: String,
    val nowPlaying: ProgramInfo?
)

data class ProgramInfo(
    val title: String,
    val startTime: Long,
    val endTime: Long
)
```

### 4.7 User Profile & Settings

**Features**:
- User login/register (if supported)
- Favorites list
- Watch history
- Settings:
  - Video quality preference (Auto, WiFi only HD, Always SD)
  - Auto-play next episode
  - Language preference
  - Notifications
  - Clear cache
  - About app

**Settings Storage**:
```kotlin
class UserPreferences @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    companion object {
        val VIDEO_QUALITY_KEY = stringPreferencesKey("video_quality")
        val AUTO_PLAY_KEY = booleanPreferencesKey("auto_play")
    }

    val videoQuality: Flow<String> = dataStore.data
        .map { preferences ->
            preferences[VIDEO_QUALITY_KEY] ?: "auto"
        }

    suspend fun setVideoQuality(quality: String) {
        dataStore.edit { preferences ->
            preferences[VIDEO_QUALITY_KEY] = quality
        }
    }
}
```

---

## 5. Development Roadmap

### Week 1-2: Project Setup & Foundation

**Tasks**:
- [x] Analyze website (COMPLETED)
- [ ] Obtain legal authorization from Proud Holding LLC
- [ ] Create Android project with Kotlin
- [ ] Setup Hilt dependency injection
- [ ] Configure Retrofit for networking
- [ ] Setup Room database
- [ ] Create base architecture (MVVM layers)
- [ ] Design app icon and branding

**Deliverables**:
- Working project skeleton
- Network layer functional
- Database structure defined

### Week 3-4: Content Browsing

**Tasks**:
- [ ] Implement HTML scraping with Jsoup
- [ ] Create Series Repository
- [ ] Build Home screen with carousels
- [ ] Implement Series listing screen
- [ ] Add image loading with Glide
- [ ] Create detail screens for series
- [ ] Implement navigation between screens

**Deliverables**:
- Browsable content catalog
- Working navigation flow
- Cached thumbnails

### Week 5-6: Video Player

**Tasks**:
- [ ] Integrate ExoPlayer
- [ ] Implement stream URL extraction
- [ ] Build player UI with controls
- [ ] Add quality selection
- [ ] Implement playback state persistence
- [ ] Add Picture-in-Picture support
- [ ] Handle errors gracefully

**Deliverables**:
- Fully functional video player
- Adaptive streaming working
- PiP mode functional

### Week 7-8: Search & Discovery

**Tasks**:
- [ ] Implement search functionality
- [ ] Create search UI
- [ ] Add search history
- [ ] Implement filters (genre, year, type)
- [ ] Add sort options
- [ ] Build recommendation system (simple)

**Deliverables**:
- Working search feature
- Content filtering
- Search history

### Week 9-10: Live TV & Additional Content

**Tasks**:
- [ ] Integrate GLWiz for live TV
- [ ] Build live TV channel grid
- [ ] Implement channel playback
- [ ] Add Movies section
- [ ] Add Shows section
- [ ] Add Music Videos section

**Deliverables**:
- Live TV functional
- All content types accessible

### Week 11-12: User Features

**Tasks**:
- [ ] Implement user authentication
- [ ] Create favorites system
- [ ] Build watch history
- [ ] Add continue watching
- [ ] Implement progress tracking
- [ ] Create profile screen

**Deliverables**:
- User account system
- Personalization features

### Week 13-14: Optimization & Polish

**Tasks**:
- [ ] Optimize image loading
- [ ] Implement caching strategy
- [ ] Add offline mode (cache metadata)
- [ ] Performance profiling
- [ ] Memory leak fixes
- [ ] UI/UX improvements
- [ ] Accessibility features

**Deliverables**:
- Optimized app performance
- Better user experience
- Accessibility compliance

### Week 15-16: Testing

**Tasks**:
- [ ] Write unit tests (repositories, use cases)
- [ ] Write UI tests (Espresso)
- [ ] Manual QA testing
- [ ] Beta testing with users
- [ ] Bug fixes
- [ ] Load testing

**Deliverables**:
- >80% code coverage
- Bug-free core features
- Beta feedback incorporated

### Week 17-18: Launch Preparation

**Tasks**:
- [ ] Create Play Store listing
- [ ] Prepare screenshots and videos
- [ ] Write app description (English & Farsi)
- [ ] Setup Firebase Analytics
- [ ] Configure Crashlytics
- [ ] Create privacy policy
- [ ] Submit to Play Store
- [ ] Marketing materials

**Deliverables**:
- App published on Play Store
- Marketing ready
- Monitoring in place

---

## 6. Implementation Guide

### 6.1 Initial Setup

**build.gradle (Project level)**:
```kotlin
buildscript {
    ext.kotlin_version = "1.9.20"
    ext.hilt_version = "2.48"

    repositories {
        google()
        mavenCentral()
    }

    dependencies {
        classpath "com.android.tools.build:gradle:8.2.0"
        classpath "org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlin_version"
        classpath "com.google.dagger:hilt-android-gradle-plugin:$hilt_version"
        classpath "com.google.gms:google-services:4.4.0"
    }
}
```

**build.gradle (App level)**:
```kotlin
plugins {
    id 'com.android.application'
    id 'kotlin-android'
    id 'kotlin-kapt'
    id 'dagger.hilt.android.plugin'
    id 'com.google.gms.google-services'
}

android {
    namespace = "com.namakade.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.namakade.app"
        minSdk = 21
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            minifyEnabled = true
            shrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        viewBinding = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // AndroidX
    implementation "androidx.core:core-ktx:1.12.0"
    implementation "androidx.appcompat:appcompat:1.6.1"
    implementation "com.google.android.material:material:1.11.0"
    implementation "androidx.constraintlayout:constraintlayout:2.1.4"

    // Navigation
    implementation "androidx.navigation:navigation-fragment-ktx:2.7.6"
    implementation "androidx.navigation:navigation-ui-ktx:2.7.6"

    // Lifecycle
    implementation "androidx.lifecycle:lifecycle-viewmodel-ktx:2.6.2"
    implementation "androidx.lifecycle:lifecycle-livedata-ktx:2.6.2"
    implementation "androidx.lifecycle:lifecycle-runtime-ktx:2.6.2"

    // Coroutines
    implementation "org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3"

    // Hilt
    implementation "com.google.dagger:hilt-android:$hilt_version"
    kapt "com.google.dagger:hilt-compiler:$hilt_version"

    // Networking
    implementation "com.squareup.retrofit2:retrofit:2.9.0"
    implementation "com.squareup.retrofit2:converter-moshi:2.9.0"
    implementation "com.squareup.okhttp3:okhttp:4.12.0"
    implementation "com.squareup.okhttp3:logging-interceptor:4.12.0"

    // JSON
    implementation "com.squareup.moshi:moshi:1.15.0"
    kapt "com.squareup.moshi:moshi-kotlin-codegen:1.15.0"

    // HTML Parsing
    implementation "org.jsoup:jsoup:1.17.2"

    // Image Loading
    implementation "com.github.bumptech.glide:glide:4.16.0"
    kapt "com.github.bumptech.glide:compiler:4.16.0"

    // Room
    implementation "androidx.room:room-runtime:2.6.1"
    implementation "androidx.room:room-ktx:2.6.1"
    kapt "androidx.room:room-compiler:2.6.1"

    // DataStore
    implementation "androidx.datastore:datastore-preferences:1.0.0"

    // ExoPlayer
    implementation "com.google.android.exoplayer:exoplayer:2.19.1"
    implementation "com.google.android.exoplayer:exoplayer-hls:2.19.1"
    implementation "com.google.android.exoplayer:exoplayer-ui:2.19.1"

    // Firebase
    implementation platform("com.google.firebase:firebase-bom:32.7.0")
    implementation "com.google.firebase:firebase-analytics-ktx"
    implementation "com.google.firebase:firebase-crashlytics-ktx"
    implementation "com.google.firebase:firebase-config-ktx"

    // Logging
    implementation "com.jakewharton.timber:timber:5.0.1"

    // Testing
    testImplementation "junit:junit:4.13.2"
    testImplementation "io.mockk:mockk:1.13.8"
    testImplementation "org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3"
    androidTestImplementation "androidx.test.ext:junit:1.1.5"
    androidTestImplementation "androidx.test.espresso:espresso-core:3.5.1"
}
```

### 6.2 Network Module

```kotlin
// di/NetworkModule.kt
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = if (BuildConfig.DEBUG)
                    HttpLoggingInterceptor.Level.BODY
                else
                    HttpLoggingInterceptor.Level.NONE
            })
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .addHeader("User-Agent", "Mozilla/5.0")
                    .addHeader("Referer", "https://namakade.com/")
                    .build()
                chain.proceed(request)
            }
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://namakade.com/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    fun provideNamakadeApi(retrofit: Retrofit): NamakadeApi {
        return retrofit.create(NamakadeApi::class.java)
    }
}
```

### 6.3 Database Module

```kotlin
// di/DatabaseModule.kt
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "namakade_db"
        ).build()
    }

    @Provides
    fun provideSeriesDao(database: AppDatabase): SeriesDao {
        return database.seriesDao()
    }

    @Provides
    fun provideFavoritesDao(database: AppDatabase): FavoritesDao {
        return database.favoritesDao()
    }
}

// data/local/database/AppDatabase.kt
@Database(
    entities = [
        SeriesEntity::class,
        EpisodeEntity::class,
        FavoriteEntity::class,
        WatchProgressEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun seriesDao(): SeriesDao
    abstract fun favoritesDao(): FavoritesDao
    abstract fun watchProgressDao(): WatchProgressDao
}
```

### 6.4 Scraper Implementation

```kotlin
// data/remote/scraper/SeriesScraper.kt
class SeriesScraper @Inject constructor(
    private val okHttpClient: OkHttpClient
) {

    suspend fun getSeriesList(): List<SeriesDto> = withContext(Dispatchers.IO) {
        val url = "https://namakade.com/best-serial"
        val html = fetchHtml(url)

        Jsoup.parse(html).select(".series-item").map { element ->
            SeriesDto(
                id = element.attr("data-id"),
                title = element.select(".title").text(),
                thumbnailUrl = element.select("img").attr("src"),
                episodes = element.select(".episodes").text()
                    .replace("Episodes: ", "").toIntOrNull() ?: 0,
                genre = element.select(".genre").text()
            )
        }
    }

    suspend fun getEpisodeStreamUrl(
        seriesId: String,
        episodeId: String
    ): String? = withContext(Dispatchers.IO) {
        val url = "https://namakade.com/serieses/$seriesId/episodes/$episodeId"
        val html = fetchHtml(url)

        Jsoup.parse(html)
            .select("#videoTagSrc")
            .firstOrNull()
            ?.attr("src")
    }

    private suspend fun fetchHtml(url: String): String {
        val request = Request.Builder()
            .url(url)
            .build()

        return okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("HTTP error: ${response.code}")
            }
            response.body?.string() ?: throw IOException("Empty response")
        }
    }
}
```

### 6.5 Repository Implementation

```kotlin
// data/repository/SeriesRepositoryImpl.kt
class SeriesRepositoryImpl @Inject constructor(
    private val seriesScraper: SeriesScraper,
    private val seriesDao: SeriesDao,
    private val seriesMapper: SeriesMapper
) : SeriesRepository {

    override fun getSeriesList(): Flow<List<Series>> = flow {
        // Try to load from cache first
        val cachedSeries = seriesDao.getAllSeries()
        if (cachedSeries.isNotEmpty()) {
            emit(cachedSeries.map { seriesMapper.toDomain(it) })
        }

        // Fetch fresh data
        try {
            val freshSeries = seriesScraper.getSeriesList()
            val entities = freshSeries.map { seriesMapper.toEntity(it) }

            // Update cache
            seriesDao.insertAll(entities)

            // Emit fresh data
            emit(freshSeries.map { seriesMapper.toDomain(it) })

        } catch (e: Exception) {
            Timber.e(e, "Failed to fetch series")
            if (cachedSeries.isEmpty()) {
                throw e
            }
            // If we have cache, don't throw error
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun getEpisodeStreamUrl(
        seriesId: String,
        episodeId: String
    ): String = withContext(Dispatchers.IO) {
        seriesScraper.getEpisodeStreamUrl(seriesId, episodeId)
            ?: throw IOException("Failed to extract stream URL")
    }
}
```

---

## 7. Testing Strategy

### 7.1 Unit Tests

**Repository Tests**:
```kotlin
class SeriesRepositoryTest {

    private lateinit var repository: SeriesRepositoryImpl
    private val seriesScraper: SeriesScraper = mockk()
    private val seriesDao: SeriesDao = mockk()
    private val mapper: SeriesMapper = SeriesMapper()

    @Before
    fun setup() {
        repository = SeriesRepositoryImpl(seriesScraper, seriesDao, mapper)
    }

    @Test
    fun `getSeriesList returns cached data first`() = runTest {
        // Given
        val cachedEntities = listOf(/* mock data */)
        coEvery { seriesDao.getAllSeries() } returns cachedEntities

        // When
        val result = repository.getSeriesList().first()

        // Then
        assertThat(result).isNotEmpty()
        coVerify { seriesDao.getAllSeries() }
    }
}
```

**ViewModel Tests**:
```kotlin
class HomeViewModelTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private lateinit var viewModel: HomeViewModel
    private val getHomeContentUseCase: GetHomeContentUseCase = mockk()

    @Before
    fun setup() {
        viewModel = HomeViewModel(getHomeContentUseCase)
    }

    @Test
    fun `initial state is Loading`() {
        assertThat(viewModel.homeState.value).isInstanceOf(HomeState.Loading::class.java)
    }

    @Test
    fun `loadHomeContent updates state to Success`() = runTest {
        // Given
        val mockContent = HomeContent(/* mock data */)
        coEvery { getHomeContentUseCase() } returns flowOf(mockContent)

        // When
        advanceUntilIdle()

        // Then
        assertThat(viewModel.homeState.value).isInstanceOf(HomeState.Success::class.java)
    }
}
```

### 7.2 UI Tests

```kotlin
@RunWith(AndroidJUnit4::class)
class HomeFragmentTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Test
    fun seriesListDisplayed() {
        launchFragmentInHiltContainer<HomeFragment> {
            // Verify series carousel is visible
            onView(withId(R.id.series_recycler_view))
                .check(matches(isDisplayed()))

            // Verify at least one series item
            onView(withId(R.id.series_recycler_view))
                .check(matches(hasDescendant(withText(containsString("Algoritm")))))
        }
    }

    @Test
    fun clickSeriesNavigatesToDetail() {
        launchFragmentInHiltContainer<HomeFragment> {
            // Click on first series
            onView(withId(R.id.series_recycler_view))
                .perform(RecyclerViewActions.actionOnItemAtPosition<RecyclerView.ViewHolder>(0, click()))

            // Verify navigation to detail screen
            onView(withId(R.id.series_detail_layout))
                .check(matches(isDisplayed()))
        }
    }
}
```

### 7.3 Integration Tests

Test actual web scraping:
```kotlin
class SeriesScraperIntegrationTest {

    private lateinit var scraper: SeriesScraper
    private val client = OkHttpClient()

    @Before
    fun setup() {
        scraper = SeriesScraper(client)
    }

    @Test
    fun `scraper fetches real series list`() = runTest {
        val series = scraper.getSeriesList()

        assertThat(series).isNotEmpty()
        assertThat(series.first().title).isNotBlank()
    }

    @Test
    fun `scraper extracts stream URL`() = runTest {
        val streamUrl = scraper.getEpisodeStreamUrl("algoritm", "algoritm")

        assertThat(streamUrl).isNotNull()
        assertThat(streamUrl).contains(".m3u8")
    }
}
```

---

## 8. Deployment Plan

### 8.1 Pre-Launch Checklist

- [ ] All features implemented and tested
- [ ] Crash-free rate > 99%
- [ ] App size < 50MB
- [ ] Launch time < 3 seconds
- [ ] Legal authorization obtained
- [ ] Privacy policy created
- [ ] Terms of service written
- [ ] Content rating obtained
- [ ] All required permissions justified
- [ ] ProGuard rules configured
- [ ] Signing key secured

### 8.2 Play Store Assets

**App Listing**:
- **Title**: Namakade - Persian TV & Movies
- **Short Description**: Stream Persian TV series, movies, shows, and live TV
- **Full Description**:
```
Watch your favorite Persian content on-the-go!

Namakade brings you:
📺 TV Series - Iranian and Turkish dramas
🎬 Movies - Latest and classic films
🎭 Shows - Talk shows and entertainment
📡 Live TV - 40+ channels
🎵 Music Videos - Concerts and clips

Features:
✨ HD streaming with adaptive quality
💾 Continue watching from any device
❤️ Save your favorites
🔍 Smart search
📱 Picture-in-Picture mode

Download now and enjoy unlimited Persian entertainment!
```

**Screenshots Required**:
- Phone: 2-8 screenshots (minimum 320px, 16:9 or 9:16)
- Tablet (optional): 1-8 screenshots
- Feature graphic: 1024x500px

**Promotional Video** (optional but recommended):
- 30-120 seconds
- Showcase key features
- Persian and English subtitles

### 8.3 Release Variants

**Internal Testing** (Alpha):
- Team members only
- Test core functionality
- 10-20 testers

**Closed Testing** (Beta):
- Invited users
- Gather feedback
- 50-100 testers
- Duration: 2-4 weeks

**Open Testing** (Optional):
- Public beta
- Wider audience
- Unlimited users

**Production**:
- Full release
- Monitored rollout (10% → 50% → 100%)

### 8.4 Version Management

**Version Code**: Integer that increases with each release
```
versionCode = MAJOR * 10000 + MINOR * 100 + PATCH

Example:
1.0.0 → 10000
1.2.3 → 10203
2.0.0 → 20000
```

**Version Name**: User-visible string
```
1.0.0 - Initial release
1.1.0 - Added live TV
1.2.0 - Search improvements
2.0.0 - Major redesign
```

---

## 9. Legal & Compliance

### 9.1 Required Permissions

**AndroidManifest.xml**:
```xml
<!-- Network access for streaming -->
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />

<!-- Storage for caching -->
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE"
    android:maxSdkVersion="28" />

<!-- Picture-in-Picture -->
<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
```

**Runtime Permissions**:
- None required for basic functionality
- Storage permission handled by scoped storage (Android 10+)

### 9.2 Privacy Policy

Must include:
- Data collection (usage analytics, crash reports)
- How data is used
- Third-party services (Firebase, etc.)
- User rights (access, deletion)
- Contact information

**Template**: https://app-privacy-policy-generator.firebaseapp.com/

### 9.3 Content Rating

**IARC Questionnaire**:
- Answer questions about content
- Obtain ratings for all regions
- Update if content changes

**Expected Rating**: Teen (13+)

### 9.4 Copyright & Licensing

**Critical**:
1. ⚠️ **Obtain written permission** from Proud Holding LLC
2. ⚠️ Include proper content attribution
3. ⚠️ Respect DRM and access controls
4. ⚠️ Do not bypass geo-blocking without authorization
5. ⚠️ Include DMCA compliance procedure

**License Agreement Template**:
```
This app requires a content licensing agreement with:
Proud Holding LLC
Content provider for Namakade.com

Contact: [to be provided]
```

---

## 10. Budget & Resources

### 10.1 Development Costs

| Item | Cost (USD) | Notes |
|------|------------|-------|
| Developer (18 weeks) | $18,000 - $36,000 | $1,000-$2,000/week |
| UI/UX Designer (4 weeks) | $2,000 - $4,000 | $500-$1,000/week |
| QA Tester (4 weeks) | $1,600 - $3,200 | $400-$800/week |
| Content License Fee | Variable | Negotiate with Proud Holding LLC |
| Play Store Registration | $25 | One-time |
| Firebase (Spark Plan) | $0 | Free tier sufficient initially |
| **Total (excluding license)** | **$21,625 - $43,225** | |

### 10.2 Ongoing Costs

| Item | Monthly Cost (USD) | Notes |
|------|-------------------|-------|
| Firebase Blaze Plan | $50 - $200 | Scales with usage |
| Server/API costs | $0 - $100 | If needed for proxy/authentication |
| Support & Maintenance | $500 - $1,000 | Bug fixes, updates |
| Marketing | $200 - $1,000 | Optional |
| **Total Monthly** | **$750 - $2,300** | |

### 10.3 Team Structure

**Minimum Team**:
- 1 x Android Developer (Kotlin, ExoPlayer)
- 1 x UI/UX Designer
- 1 x QA Tester
- 1 x Project Manager (part-time)

**Recommended Team**:
- 2 x Android Developers
- 1 x UI/UX Designer
- 1 x Backend Developer (if custom API needed)
- 1 x QA Tester
- 1 x Product Manager

### 10.4 Tools & Services

**Free Tier**:
- Android Studio (IDE)
- Git + GitHub/GitLab
- Firebase Spark Plan
- Play Console ($25 one-time)

**Paid Tools** (Optional):
- Figma Pro ($12/month) - Design
- Jira ($7.75/user/month) - Project management
- Sentry ($26/month) - Error monitoring
- TestFlight alternative for Android

---

## 11. Risk Assessment

### 11.1 Technical Risks

| Risk | Probability | Impact | Mitigation |
|------|------------|--------|------------|
| Geo-blocking prevents access | HIGH | HIGH | VPN integration, official whitelist |
| Stream URLs change format | MEDIUM | HIGH | Flexible scraping, error recovery |
| Website structure changes | MEDIUM | MEDIUM | Automated tests, quick updates |
| Performance issues | MEDIUM | MEDIUM | Profiling, optimization |
| ExoPlayer compatibility | LOW | MEDIUM | Thorough testing, fallbacks |

### 11.2 Legal Risks

| Risk | Probability | Impact | Mitigation |
|------|------------|--------|------------|
| No content license granted | MEDIUM | CRITICAL | Early contact with Proud Holding |
| DMCA takedown | LOW | HIGH | Proper attribution, compliance |
| Terms of service violation | MEDIUM | HIGH | Official API access |
| Copyright infringement | LOW | CRITICAL | License agreement |

### 11.3 Business Risks

| Risk | Probability | Impact | Mitigation |
|------|------------|--------|------------|
| Low user adoption | MEDIUM | HIGH | Marketing, quality product |
| Competition | MEDIUM | MEDIUM | Unique features, better UX |
| Maintenance costs | MEDIUM | MEDIUM | Efficient architecture, automation |
| Revenue model unclear | HIGH | MEDIUM | Plan monetization (ads, premium) |

---

## 12. Success Metrics

### 12.1 Launch Metrics (First 3 Months)

- **Downloads**: 10,000+
- **Active Users (DAU)**: 1,000+
- **Retention (D7)**: >40%
- **Crash-free Rate**: >99.5%
- **Average Session**: >15 minutes
- **Play Store Rating**: >4.0 stars

### 12.2 Quality Metrics

- **Code Coverage**: >80%
- **Build Success Rate**: >95%
- **Average Load Time**: <3 seconds
- **Video Start Time**: <5 seconds
- **App Size**: <50MB

---

## 13. Future Enhancements (Post-Launch)

### Phase 6: Additional Features
- [ ] Download episodes for offline viewing
- [ ] Chromecast support
- [ ] Subtitles/captions
- [ ] Multiple audio tracks
- [ ] Parental controls
- [ ] Profiles (multiple users per account)
- [ ] Notifications for new episodes
- [ ] Social features (share, rate, review)
- [ ] Watch parties (synchronized viewing)

### Phase 7: Monetization
- [ ] Ad integration (if content license permits)
- [ ] Premium subscription (ad-free, early access)
- [ ] In-app purchases
- [ ] Referral program

### Phase 8: Expansion
- [ ] iOS app
- [ ] Web app
- [ ] Smart TV apps (Android TV, Fire TV)
- [ ] Regional content expansion

---

## Conclusion

This development plan provides a comprehensive roadmap for building a standalone Android app that streams content from Namakade.com. The estimated timeline is **18 weeks** with a team of 2-3 developers.

**Critical Success Factors**:
1. ✅ Obtain legal authorization from Proud Holding LLC
2. ✅ Build reliable content scraping/API integration
3. ✅ Implement robust video streaming with ExoPlayer
4. ✅ Handle geo-blocking appropriately
5. ✅ Deliver excellent user experience

**Next Steps**:
1. Contact Proud Holding LLC for content licensing
2. Assemble development team
3. Begin Phase 1 implementation
4. Iterate based on user feedback

---

**Document Version**: 1.0
**Created**: 2025-10-29
**Status**: Ready for Implementation
**Estimated Cost**: $22K - $43K (development only)
**Timeline**: 18 weeks
**Platform**: Android (iOS future consideration)
