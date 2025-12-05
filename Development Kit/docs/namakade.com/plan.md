# Namakade.com Integration Plan

**Created**: 2025-01-08
**Status**: Ready for Implementation
**Database**: namakadeh.db (8.05 MB, 19,373 episodes)

---

## Executive Summary

✅ **Database is READY** - Pre-scraped with 19,373 working video URLs
⚠️ **Main Issue** - All genres marked as "Unknown" (needs enrichment)
❌ **Schema Incompatibility** - Different structure than current app
📊 **Content**: 923 series + 312 movies = 1,235 total shows

---

## Table of Contents

1. [Database Analysis](#1-database-analysis)
2. [Schema Comparison](#2-schema-comparison)
3. [Data Quality Assessment](#3-data-quality-assessment)
4. [Integration Challenges](#4-integration-challenges)
5. [Recommended Approach](#5-recommended-approach)
6. [Implementation Phases](#6-implementation-phases)
7. [Video Playback Configuration](#7-video-playback-configuration)
8. [Risks & Mitigation](#8-risks--mitigation)

---

## 1. Database Analysis

### Content Statistics

```
Total Shows:     1,235
├─ Series:       923 (75%)
└─ Movies:       312 (25%)

Total Episodes:  19,373 (100% have video URLs)
Live Channels:   0 (table exists but empty)
Genre Coverage:  0% (all marked as "Unknown")
```

### Database Schema (namakadeh.db)

#### SERIES Table
```sql
CREATE TABLE series (
    id TEXT PRIMARY KEY,              -- ⚠️ String slugs (vs app's Integer)
    title TEXT NOT NULL,              -- ✅ All populated
    titleFarsi TEXT,                  -- ❌ NULL for all records
    slug TEXT NOT NULL,               -- ✅ All populated
    linkPath TEXT NOT NULL,           -- ✅ All populated
    thumbnail TEXT NOT NULL,          -- ✅ All populated
    banner TEXT,                      -- ❌ NULL for all records
    description TEXT,                 -- ❌ NULL for all records
    genre TEXT NOT NULL,              -- ⚠️ All "Unknown"
    totalEpisodes INTEGER NOT NULL,   -- ⚠️ 79% inflated (metadata error)
    seasons INTEGER DEFAULT 1,        -- ⚠️ All default to 1
    year INTEGER,                     -- ❌ NULL for all records
    rating REAL,                      -- ❌ NULL for all records
    isTurkish INTEGER DEFAULT 0,      -- ✅ Populated
    contentType TEXT DEFAULT 'series',-- ✅ 'series' or 'movie'
    isArchive INTEGER DEFAULT 0,
    isNewRelease INTEGER DEFAULT 0,
    displayOrder INTEGER DEFAULT 0,
    createdAt INTEGER NOT NULL,
    updatedAt INTEGER NOT NULL
);
```

#### EPISODES Table
```sql
CREATE TABLE episodes (
    id TEXT PRIMARY KEY,              -- ⚠️ String IDs (vs app's Long)
    seriesId TEXT NOT NULL,           -- ⚠️ Foreign key to series.id (TEXT)
    title TEXT NOT NULL,              -- ⚠️ All "Episode {n}"
    episodeNumber INTEGER NOT NULL,   -- ✅ All populated
    season INTEGER DEFAULT 1,         -- ⚠️ All default to 1
    thumbnail TEXT,                   -- ⚠️ Some NULL
    videoUrl TEXT,                    -- ✅ 100% populated!
    duration INTEGER,                 -- ❌ NULL for all records
    addedAt INTEGER NOT NULL,
    watchProgress INTEGER DEFAULT 0,
    episodePageUrl TEXT,
    slug TEXT,
    FOREIGN KEY (seriesId) REFERENCES series(id) ON DELETE CASCADE
);
```

#### MONITORED_SERIES Table
```sql
CREATE TABLE monitored_series (
    seriesId TEXT PRIMARY KEY,
    addedAt INTEGER NOT NULL,
    isPinned INTEGER DEFAULT 0,
    muteNotifications INTEGER DEFAULT 0,
    lastCheckedAt INTEGER DEFAULT 0,
    newEpisodeCount INTEGER DEFAULT 0,
    FOREIGN KEY (seriesId) REFERENCES series(id) ON DELETE CASCADE
);
```

#### LIVE_CHANNELS Table
```sql
CREATE TABLE live_channels (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    slug TEXT NOT NULL,
    logoUrl TEXT NOT NULL,
    category TEXT NOT NULL,
    streamUrl TEXT,
    isPopular INTEGER DEFAULT 0,
    sortOrder INTEGER DEFAULT 0,
    lastUpdated INTEGER NOT NULL
);
-- ❌ Empty - not scraped
```

---

## 2. Schema Comparison

### Namakade DB vs App Schema (ContentEntities.kt)

| Feature | Namakade DB | App Schema | Status |
|---------|-------------|------------|--------|
| **Primary Keys** | String slugs | Integer IDs | ❌ INCOMPATIBLE |
| **Series/Movies** | Single `series` table | Separate `cached_movies` + `cached_series` | ❌ INCOMPATIBLE |
| **Foreign Keys** | seriesId (TEXT) | seriesId (INTEGER) | ❌ INCOMPATIBLE |
| **Episode IDs** | Manual string IDs | Auto-increment LONG | ❌ INCOMPATIBLE |
| **Genre Storage** | Single TEXT field | Comma-separated + table | ⚠️ Needs mapping |
| **Video URLs** | Embedded in episodes | Separate `cached_video_urls` | ⚠️ Needs mapping |
| **Source URLs** | linkPath field | farsilandUrl field | ⚠️ Different naming |

### Current App Schema (for reference)

```kotlin
// cached_movies
@Entity(tableName = "cached_movies")
data class CachedMovie(
    @PrimaryKey val id: Int,          // ← Integer IDs
    val title: String,
    val posterUrl: String?,
    val farsilandUrl: String,         // ← vs linkPath
    val description: String?,
    val year: Int?,
    val rating: Float?,
    val runtime: Int?,
    val director: String?,
    val cast: String?,
    val genres: String?,              // ← Comma-separated
    val dateAdded: Long,
    val lastUpdated: Long
)

// cached_series
@Entity(tableName = "cached_series")
data class CachedSeries(
    @PrimaryKey val id: Int,          // ← Integer IDs
    val title: String,
    val posterUrl: String?,
    val backdropUrl: String?,
    val farsilandUrl: String,
    val description: String?,
    val year: Int?,
    val rating: Float?,
    val totalSeasons: Int,
    val totalEpisodes: Int,
    val cast: String?,
    val genres: String?,
    val dateAdded: Long,
    val lastUpdated: Long
)

// cached_episodes
@Entity(tableName = "cached_episodes")
data class CachedEpisode(
    @PrimaryKey(autoGenerate = true) val id: Long = 0, // ← Auto-increment
    val seriesId: Int,                // ← Integer FK
    val seriesTitle: String?,
    val episodeId: Int,
    val season: Int,
    val episode: Int,
    val title: String,
    val description: String?,
    val thumbnailUrl: String?,
    val farsilandUrl: String,
    val airDate: String?,
    val runtime: Int?,
    val dateAdded: Long,
    val lastUpdated: Long
)

// cached_video_urls (separate table)
@Entity(tableName = "cached_video_urls")
data class CachedVideoUrl(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val contentId: Int,
    val contentType: String,          // "movie" or "episode"
    val quality: String,              // "1080p", "720p", "auto"
    val videoUrl: String,
    val serverName: String?,
    val addedAt: Long,
    val lastVerified: Long?
)
```

---

## 3. Data Quality Assessment

### ✅ What Works (Ready to Use)

1. **Video URLs**: 100% coverage (19,373 episodes)
   - All URLs tested and working
   - Direct MP4 URLs from `media.negahestan.com` CDN
   - **CRITICAL**: Requires Referer header

2. **Basic Metadata**: All shows have:
   - Title (English)
   - Slug
   - Link path
   - Thumbnail URL
   - Content type (series/movie)
   - Timestamps

3. **Content Classification**:
   - Properly tagged as 'series' or 'movie'
   - Turkish content flagged with `isTurkish`

4. **Episode Structure**:
   - All episodes numbered sequentially
   - Series-to-episode relationships intact

### ❌ Critical Missing Data

| Field | Status | Impact | Fix Required |
|-------|--------|--------|--------------|
| **Genres** | All "Unknown" | HIGH - No genre browsing | ✅ Scrape or API |
| **Farsi Titles** | All NULL | MEDIUM - Persian UI missing | ⚠️ Optional scrape |
| **Descriptions** | All NULL | MEDIUM - Detail pages empty | ⚠️ Optional scrape |
| **Banners** | All NULL | LOW - No backdrop images | ⚠️ Optional |
| **Years** | All NULL | MEDIUM - Can't filter by year | ⚠️ Optional API |
| **Ratings** | All NULL | LOW - No quality indicator | ⚠️ Optional API |
| **Episode Titles** | All "Episode {n}" | LOW - Generic names | ⚠️ Optional scrape |
| **Durations** | All NULL | LOW - No runtime shown | ⚠️ Optional |

### ⚠️ Data Reliability Issues

1. **totalEpisodes Field**:
   - 79% of series have inflated counts
   - Website metadata error (counts announcement episodes)
   - **Solution**: Always use `COUNT(*)` from episodes table

2. **Season Numbers**:
   - All episodes default to `season = 1`
   - Multi-season shows not detected
   - **Solution**: Season detection algorithm needed

3. **Live Channels**:
   - Table exists but completely empty
   - ~70 channels available on website
   - **Solution**: Create separate scraper

### Sample Data Quality Query

```sql
-- Verify episode count accuracy
SELECT
    s.title,
    s.totalEpisodes as metadata_count,
    COUNT(e.id) as actual_count,
    (s.totalEpisodes - COUNT(e.id)) as difference
FROM series s
LEFT JOIN episodes e ON e.seriesId = s.id
WHERE s.contentType = 'series'
GROUP BY s.id
HAVING difference > 0
ORDER BY difference DESC
LIMIT 10;

-- Result: 79% of series have positive difference
```

---

## 4. Integration Challenges

### Challenge 1: Incompatible Primary Keys

**Problem**: Namakade uses string slugs, app uses integer IDs

**Example**:
```kotlin
// Namakade DB
series.id = "shoghaal"  // String slug
episodes.seriesId = "shoghaal"

// Current App
CachedSeries.id = 12345  // Integer
CachedEpisode.seriesId = 12345
```

**Impact**:
- Can't directly merge databases
- Foreign key relationships break
- DAO queries fail

**Solution Options**:

**Option A: Modify App Schema** (RECOMMENDED)
```kotlin
@Entity(tableName = "unified_series")
data class UnifiedSeries(
    @PrimaryKey val id: String,  // Support both "12345" and "shoghaal"
    val source: DatabaseSource,   // FARSILAND, FARSIPLEX, NAMAKADE
    val contentType: String,      // "movie" or "series"
    // ... rest of fields
)
```
✅ Pros: Clean, supports all sources
❌ Cons: Breaking change, migration needed

**Option B: Generate Integer IDs**
```kotlin
// Hash slug to integer
fun slugToId(slug: String): Int = slug.hashCode()

// Migration
INSERT INTO cached_series (id, ...)
VALUES (hash('shoghaal'), ...)
```
✅ Pros: Works with existing schema
❌ Cons: Hash collisions possible, complex mapping

**Option C: Separate Tables** (EASIEST)
```kotlin
// Keep Namakade in separate database
@Database(
    entities = [NamakadeSeries::class, NamakadeEpisode::class],
    version = 1
)
abstract class NamakadeDatabase : RoomDatabase()
```
✅ Pros: No migration needed, isolated
❌ Cons: Can't unified browse, duplicate code

---

### Challenge 2: Single Series Table vs Separate Tables

**Problem**: Namakade combines series+movies, app separates them

**Namakade Structure**:
```sql
SELECT * FROM series WHERE contentType = 'series';  -- TV shows
SELECT * FROM series WHERE contentType = 'movie';   -- Movies
```

**App Structure**:
```kotlin
movieDao.getAllMovies()   // cached_movies table
seriesDao.getAllSeries()  // cached_series table
```

**Impact**:
- UI logic expects separate DAOs
- Browse screens query different tables
- Search needs to query both

**Solution Options**:

**Option A: Database Views** (RECOMMENDED)
```sql
CREATE VIEW cached_movies_view AS
SELECT
    id,
    title,
    thumbnail as posterUrl,
    linkPath as farsilandUrl,
    -- ... map other fields
FROM series
WHERE contentType = 'movie';

CREATE VIEW cached_series_view AS
SELECT * FROM series WHERE contentType = 'series';
```
✅ Pros: No data duplication, queries work as-is
❌ Cons: Views can't have different schema

**Option B: Migrate to Separate Tables**
```kotlin
// On import, split data
db.execSQL("""
    INSERT INTO cached_movies
    SELECT * FROM namakade_series WHERE contentType = 'movie'
""")
```
✅ Pros: Matches app structure perfectly
❌ Cons: Data duplication, complex migration

**Option C: Update App to Use Single Table**
```kotlin
@Query("SELECT * FROM content_items WHERE contentType = :type")
fun getContentByType(type: String): Flow<List<ContentItem>>
```
✅ Pros: More flexible, cleaner architecture
❌ Cons: Requires app-wide refactoring

---

### Challenge 3: Missing Genre Data

**Problem**: All 923 series have `genre = "Unknown"`

**Impact**:
- Genre browsing completely broken
- Poor content discovery
- No filtering by genre
- Search results lack context

**Solution Options**:

**Option A: Scrape from Namakade.com**
```python
# Parse genre from show page
def scrape_genre(series_id):
    url = f"https://namakade.com/series/{series_id}"
    # Extract from HTML: <span class="genre">اکشن، ماجراجویی</span>
    return parse_genres(response.text)
```
✅ Pros: Accurate, Farsi names
❌ Cons: Requires scraping 923 pages, slow

**Option B: Use TMDB API** (RECOMMENDED)
```python
import tmdbsimple as tmdb

# Match by title and get metadata
def enrich_series(title, year=None):
    search = tmdb.Search()
    results = search.tv(query=title, year=year)

    if results['results']:
        show = results['results'][0]
        return {
            'genres': show['genre_ids'],  # [18, 10765]
            'description': show['overview'],
            'rating': show['vote_average'],
            'year': show['first_air_date'][:4],
            'backdrop': show['backdrop_path']
        }
```
✅ Pros: Gets genres + descriptions + ratings
❌ Cons: Requires API key, matching errors possible

**Option C: Manual Classification for Top Shows**
```sql
-- Classify top 100 popular shows manually
UPDATE series SET genre = 'اکشن، درام' WHERE id = 'shoghaal';
UPDATE series SET genre = 'کمدی' WHERE id = 'algoritm';
-- ... etc
```
✅ Pros: Fast for popular content
❌ Cons: Incomplete, manual work

**Recommendation**: Combine B + C
1. Use TMDB API for automated enrichment
2. Manually fix top 50 shows for accuracy
3. Leave rare content as "Unknown" initially

---

### Challenge 4: Video URL Format Differences

**Problem**: App expects separate `cached_video_urls` table

**Namakade**:
```sql
episodes.videoUrl = "https://media.negahestan.com/.../episode1.mp4"
-- Single URL embedded in episode record
```

**App**:
```sql
-- Separate table with quality options
cached_video_urls:
  contentId = 12345
  quality = "1080p"
  videoUrl = "https://..."

  contentId = 12345
  quality = "720p"
  videoUrl = "https://..."
```

**Impact**:
- Quality selection UI won't work
- Video player expects cached_video_urls lookup
- No fallback quality options

**Solution Options**:

**Option A: Migrate to cached_video_urls**
```kotlin
// On import, create video_urls entries
episodes.forEach { episode ->
    val videoUrl = CachedVideoUrl(
        contentId = episode.id.hashCode(),
        contentType = "episode",
        quality = "auto",  // Default quality
        videoUrl = episode.videoUrl,
        serverName = "Namakade CDN",
        addedAt = System.currentTimeMillis()
    )
    videoUrlDao.insert(videoUrl)
}
```
✅ Pros: Works with existing player
❌ Cons: Only one quality available

**Option B: Update Player to Support Both**
```kotlin
// VideoPlayerActivity.kt
val videoUrl = when (source) {
    DatabaseSource.NAMAKADE -> {
        // Get URL directly from episode
        episodeDao.getEpisodeById(id).videoUrl
    }
    else -> {
        // Get from cached_video_urls
        videoUrlDao.getVideoUrl(id, "auto")
    }
}
```
✅ Pros: No data duplication
❌ Cons: Requires player code changes

**Recommendation**: Option A for MVP (migrate to cached_video_urls)

---

## 5. Recommended Approach

### Strategy: Parallel Database Integration

Instead of merging databases immediately, run them **side-by-side** with a unified interface layer.

```
┌─────────────────────────────────────────┐
│         App UI Layer                    │
│  (Browse, Search, Detail, Player)       │
└─────────────────┬───────────────────────┘
                  │
┌─────────────────▼───────────────────────┐
│     Repository Adapter Layer            │
│  - Unified content interface            │
│  - Source-aware queries                 │
│  - ID format handling                   │
└─────────────────┬───────────────────────┘
                  │
         ┌────────┴────────┐
         │                 │
┌────────▼────────┐  ┌────▼──────────────┐
│ ContentDatabase │  │ NamakadeDatabase  │
│ (FarsiFlow DB)  │  │ (Namakade DB)     │
│ - Integer IDs   │  │ - String IDs      │
│ - Dual tables   │  │ - Single table    │
└─────────────────┘  └───────────────────┘
```

### Benefits

✅ **No Breaking Changes**: Existing FarsiFlow data untouched
✅ **Incremental Migration**: Add features gradually
✅ **Easy Rollback**: Can disable Namakade if issues
✅ **Performance**: Keep both databases optimized separately

---

## 6. Implementation Phases

### Phase 1: Basic Integration (1-2 days)

**Goal**: Get Namakade working as a third database source

#### Step 1.1: Copy Database
```bash
# Copy database to assets
cp namakadeh.db app/src/main/assets/databases/namakade.db
```

#### Step 1.2: Update DatabaseSource Enum
```kotlin
// DatabasePreferences.kt
enum class DatabaseSource(val fileName: String, val displayName: String) {
    FARSILAND("farsiland.db", "Farsiland.com"),
    FARSIPLEX("farsiplex.db", "FarsiPlex.com"),
    NAMAKADE("namakade.db", "Namakade.com")  // NEW
}
```

#### Step 1.3: Create NamakadeDatabase
```kotlin
// NamakadeDatabase.kt
@Database(
    entities = [
        NamakadeSeries::class,
        NamakadeEpisode::class,
        MonitoredSeries::class
    ],
    version = 1
)
abstract class NamakadeDatabase : RoomDatabase() {
    abstract fun seriesDao(): NamakadeSeriesDao
    abstract fun episodeDao(): NamakadeEpisodeDao
    abstract fun monitoredSeriesDao(): MonitoredSeriesDao

    companion object {
        fun getInstance(context: Context): NamakadeDatabase {
            return Room.databaseBuilder(
                context,
                NamakadeDatabase::class.java,
                "namakade.db"
            )
            .createFromAsset("databases/namakade.db")
            .build()
        }
    }
}
```

#### Step 1.4: Create Entity Classes
```kotlin
// NamakadeEntities.kt
@Entity(tableName = "series")
data class NamakadeSeries(
    @PrimaryKey val id: String,
    val title: String,
    val titleFarsi: String?,
    val slug: String,
    val linkPath: String,
    val thumbnail: String,
    val banner: String?,
    val description: String?,
    val genre: String,
    val totalEpisodes: Int,
    val seasons: Int,
    val year: Int?,
    val rating: Float?,
    val isTurkish: Boolean,
    val contentType: String,
    val isArchive: Boolean,
    val isNewRelease: Boolean,
    val displayOrder: Int,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(tableName = "episodes")
data class NamakadeEpisode(
    @PrimaryKey val id: String,
    val seriesId: String,
    val title: String,
    val episodeNumber: Int,
    val season: Int,
    val thumbnail: String?,
    val videoUrl: String?,
    val duration: Int?,
    val addedAt: Long,
    val watchProgress: Int,
    val episodePageUrl: String?,
    val slug: String?
)
```

#### Step 1.5: Test Database Access
```kotlin
// Test in MainActivity
lifecycleScope.launch {
    val db = NamakadeDatabase.getInstance(this@MainActivity)
    val series = db.seriesDao().getAllSeries()
    Log.d("Namakade", "Loaded ${series.size} series")

    val episodes = db.episodeDao().getAllEpisodes()
    Log.d("Namakade", "Loaded ${episodes.size} episodes")
}
```

**Expected Output**:
```
D/Namakade: Loaded 1235 series
D/Namakade: Loaded 19373 episodes
```

---

### Phase 2: Video Playback Integration (1 day)

**Goal**: Play Namakade videos in VideoPlayerActivity

#### Step 2.1: Update ExoPlayer Configuration
```kotlin
// VideoPlayerActivity.kt
private fun setupPlayer(videoUrl: String, source: DatabaseSource) {
    val httpDataSourceFactory = DefaultHttpDataSource.Factory()
        .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")

    // CRITICAL: Add Referer header for Namakade
    if (source == DatabaseSource.NAMAKADE) {
        httpDataSourceFactory.setDefaultRequestProperties(
            mapOf("Referer" to "https://namakade.com/")
        )
    }

    val mediaSource = ProgressiveMediaSource.Factory(httpDataSourceFactory)
        .createMediaSource(MediaItem.fromUri(videoUrl))

    player?.setMediaSource(mediaSource)
    player?.prepare()
    player?.play()
}
```

#### Step 2.2: Test Video Playback
```kotlin
// Test with known working URL
val testUrl = "https://media.negahestan.com/..."
setupPlayer(testUrl, DatabaseSource.NAMAKADE)
```

**Expected**: Video plays correctly (not `blockHacks.mp4`)

---

### Phase 3: UI Integration (2-3 days)

**Goal**: Display Namakade content in browse screens

#### Step 3.1: Create Repository Adapter
```kotlin
// UnifiedContentRepository.kt
class UnifiedContentRepository(context: Context) {
    private val currentSource = DatabasePreferences.getInstance(context).getCurrentSource()

    fun getMovies(): Flow<List<UnifiedMovie>> {
        return when (currentSource) {
            DatabaseSource.NAMAKADE -> {
                namakadeDb.seriesDao()
                    .getSeriesByType("movie")
                    .map { it.toUnifiedMovies() }
            }
            else -> {
                contentDb.movieDao()
                    .getAllMovies()
                    .map { it.toUnifiedMovies() }
            }
        }
    }

    fun getSeries(): Flow<List<UnifiedSeries>> {
        return when (currentSource) {
            DatabaseSource.NAMAKADE -> {
                namakadeDb.seriesDao()
                    .getSeriesByType("series")
                    .map { it.toUnifiedSeries() }
            }
            else -> {
                contentDb.seriesDao()
                    .getAllSeries()
                    .map { it.toUnifiedSeries() }
            }
        }
    }
}
```

#### Step 3.2: Create Unified Data Classes
```kotlin
// UnifiedModels.kt
data class UnifiedMovie(
    val id: String,  // Support both integer and string IDs
    val source: DatabaseSource,
    val title: String,
    val posterUrl: String?,
    val description: String?,
    val year: Int?,
    val rating: Float?,
    val genres: List<String>
)

data class UnifiedSeries(
    val id: String,
    val source: DatabaseSource,
    val title: String,
    val posterUrl: String?,
    val description: String?,
    val totalSeasons: Int,
    val totalEpisodes: Int,
    val genres: List<String>
)
```

#### Step 3.3: Update ViewModel
```kotlin
// MainViewModel.kt
class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = UnifiedContentRepository(app)

    val movies: Flow<List<UnifiedMovie>> = repository.getMovies()
    val series: Flow<List<UnifiedSeries>> = repository.getSeries()
}
```

#### Step 3.4: Update MainFragment
```kotlin
// MainFragment.kt
private fun observeViewModel() {
    // Movies row
    viewLifecycleOwner.lifecycleScope.launch {
        viewModel.movies.collect { movies ->
            val adapter = ArrayObjectAdapter(MoviePresenter())
            movies.forEach { adapter.add(it) }
            rowsAdapter.add(ListRow(HeaderItem("Movies"), adapter))
        }
    }

    // Series row
    viewLifecycleOwner.lifecycleScope.launch {
        viewModel.series.collect { series ->
            val adapter = ArrayObjectAdapter(SeriesPresenter())
            series.forEach { adapter.add(it) }
            rowsAdapter.add(ListRow(HeaderItem("Series"), adapter))
        }
    }
}
```

---

### Phase 4: Genre Enrichment (2-3 days)

**Goal**: Fix "Unknown" genres using TMDB API

#### Step 4.1: Add TMDB Dependency
```gradle
// app/build.gradle
dependencies {
    implementation 'com.squareup.retrofit2:retrofit:2.9.0'
    implementation 'com.squareup.retrofit2:converter-moshi:2.9.0'
}
```

#### Step 4.2: Create TMDB Service
```kotlin
// TmdbService.kt
interface TmdbService {
    @GET("search/tv")
    suspend fun searchTvShows(
        @Query("api_key") apiKey: String,
        @Query("query") query: String,
        @Query("first_air_date_year") year: Int? = null
    ): TmdbSearchResponse

    @GET("tv/{tv_id}")
    suspend fun getTvShowDetails(
        @Path("tv_id") tvId: Int,
        @Query("api_key") apiKey: String
    ): TmdbTvShow
}

data class TmdbTvShow(
    val id: Int,
    val name: String,
    val overview: String,
    val genres: List<TmdbGenre>,
    val vote_average: Float,
    val first_air_date: String,
    val backdrop_path: String?,
    val poster_path: String?
)

data class TmdbGenre(
    val id: Int,
    val name: String
)
```

#### Step 4.3: Create Genre Enrichment Worker
```kotlin
// GenreEnrichmentWorker.kt
class GenreEnrichmentWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val tmdb = RetrofitClient.createTmdbService()
    private val db = NamakadeDatabase.getInstance(context)

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val unknownSeries = db.seriesDao().getSeriesByGenre("Unknown")

        unknownSeries.forEach { series ->
            try {
                // Search TMDB
                val searchResults = tmdb.searchTvShows(
                    apiKey = BuildConfig.TMDB_API_KEY,
                    query = series.title,
                    year = series.year
                )

                if (searchResults.results.isNotEmpty()) {
                    val tmdbShow = searchResults.results.first()
                    val details = tmdb.getTvShowDetails(
                        tvId = tmdbShow.id,
                        apiKey = BuildConfig.TMDB_API_KEY
                    )

                    // Update series with enriched data
                    val updated = series.copy(
                        genre = details.genres.joinToString(", ") { it.name },
                        description = details.overview,
                        rating = details.vote_average,
                        year = details.first_air_date.take(4).toIntOrNull(),
                        banner = "https://image.tmdb.org/t/p/w1280${details.backdrop_path}"
                    )

                    db.seriesDao().update(updated)
                    Log.d(TAG, "Enriched: ${series.title} -> ${updated.genre}")
                }

                delay(250) // Rate limit: 4 requests/second
            } catch (e: Exception) {
                Log.e(TAG, "Failed to enrich ${series.title}: ${e.message}")
            }
        }

        Result.success()
    }
}
```

#### Step 4.4: Schedule Enrichment
```kotlin
// FarsilandApp.kt
private fun scheduleGenreEnrichment() {
    val workRequest = OneTimeWorkRequestBuilder<GenreEnrichmentWorker>()
        .setConstraints(
            Constraints.Builder()
                .setRequiredNetworkType(NetworkType.UNMETERED)
                .setRequiresCharging(true)
                .build()
        )
        .build()

    WorkManager.getInstance(this).enqueue(workRequest)
}
```

**Expected**: After running, genres updated from "Unknown" to actual genres

---

### Phase 5: Search Integration (1-2 days)

**Goal**: Unified search across all databases

#### Step 5.1: Update SearchActivity
```kotlin
// SearchActivity.kt
private fun searchContent(query: String) {
    viewLifecycleOwner.lifecycleScope.launch {
        val source = DatabasePreferences.getInstance(this).getCurrentSource()

        val results = when (source) {
            DatabaseSource.NAMAKADE -> {
                searchNamakade(query)
            }
            else -> {
                searchFarsiFlow(query)
            }
        }

        displayResults(results)
    }
}

private suspend fun searchNamakade(query: String): List<UnifiedContent> {
    val db = NamakadeDatabase.getInstance(this)

    return withContext(Dispatchers.IO) {
        db.seriesDao().searchSeries("%$query%").map { it.toUnifiedContent() }
    }
}
```

#### Step 5.2: Add Search DAO Method
```kotlin
// NamakadeSeriesDao.kt
@Dao
interface NamakadeSeriesDao {
    @Query("""
        SELECT * FROM series
        WHERE title LIKE :query
           OR titleFarsi LIKE :query
           OR slug LIKE :query
        ORDER BY
            CASE WHEN isNewRelease = 1 THEN 0 ELSE 1 END,
            displayOrder ASC
        LIMIT 50
    """)
    suspend fun searchSeries(query: String): List<NamakadeSeries>
}
```

---

### Phase 6: Detail Screens (1-2 days)

**Goal**: Show Namakade content in DetailsActivity and SeriesDetailsActivity

#### Step 6.1: Update DetailsActivity
```kotlin
// DetailsActivity.kt
private fun loadMovieDetails(movieId: String) {
    lifecycleScope.launch {
        val source = DatabasePreferences.getInstance(this).getCurrentSource()

        val movie = when (source) {
            DatabaseSource.NAMAKADE -> {
                val db = NamakadeDatabase.getInstance(this)
                db.seriesDao().getSeriesById(movieId)?.toUnifiedMovie()
            }
            else -> {
                val db = ContentDatabase.getDatabase(this)
                db.movieDao().getMovieById(movieId.toInt())?.toUnifiedMovie()
            }
        }

        movie?.let { displayMovieDetails(it) }
    }
}
```

#### Step 6.2: Update SeriesDetailsActivity
```kotlin
// SeriesDetailsActivity.kt
private fun loadSeriesDetails(seriesId: String) {
    lifecycleScope.launch {
        val source = DatabasePreferences.getInstance(this).getCurrentSource()

        val (series, episodes) = when (source) {
            DatabaseSource.NAMAKADE -> {
                val db = NamakadeDatabase.getInstance(this)
                val s = db.seriesDao().getSeriesById(seriesId)
                val e = db.episodeDao().getEpisodesBySeriesId(seriesId)
                Pair(s?.toUnifiedSeries(), e.map { it.toUnifiedEpisode() })
            }
            else -> {
                val db = ContentDatabase.getDatabase(this)
                val s = db.seriesDao().getSeriesById(seriesId.toInt())
                val e = db.episodeDao().getEpisodesForSeries(seriesId.toInt())
                Pair(s?.toUnifiedSeries(), e.map { it.toUnifiedEpisode() })
            }
        }

        displaySeriesDetails(series, episodes)
    }
}
```

---

## 7. Video Playback Configuration

### Critical: Referer Header Required

**Without Referer**: Video plays `blockHacks.mp4` (1-minute blocker)
**With Referer**: Video plays correctly

### ExoPlayer Setup

```kotlin
// VideoPlayerActivity.kt
private fun createDataSourceFactory(source: DatabaseSource): DataSource.Factory {
    val httpDataSourceFactory = DefaultHttpDataSource.Factory()
        .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")

    // CRITICAL: Add Referer header for Namakade content
    if (source == DatabaseSource.NAMAKADE) {
        httpDataSourceFactory.setDefaultRequestProperties(
            mapOf("Referer" to "https://namakade.com/")
        )
    }

    return httpDataSourceFactory
}

private fun playVideo(videoUrl: String, source: DatabaseSource) {
    val dataSourceFactory = createDataSourceFactory(source)

    val mediaSource = ProgressiveMediaSource.Factory(dataSourceFactory)
        .createMediaSource(MediaItem.fromUri(videoUrl))

    player = ExoPlayer.Builder(this)
        .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
        .build()
        .apply {
            setMediaSource(mediaSource)
            prepare()
            playWhenReady = true
        }

    playerView.player = player
}
```

### Testing Video Playback

```kotlin
// Test URLs (from actual database)
val testUrls = listOf(
    "https://media.negahestan.com/8be8f/upload/movies/Shoghaal/S01E01.mp4",
    "https://media.negahestan.com/8be8f/upload/movies/Algoritm/S01E01.mp4"
)

testUrls.forEach { url ->
    playVideo(url, DatabaseSource.NAMAKADE)
    delay(5000) // Play 5 seconds
    player?.stop()
}
```

**Expected**: All videos play correctly (no blocker)

---

## 8. Risks & Mitigation

### Risk 1: Schema Incompatibility Breaks App

**Impact**: HIGH
**Probability**: HIGH if merging databases

**Mitigation**:
1. ✅ Keep databases separate (Phase 1 approach)
2. ✅ Use adapter pattern for abstraction
3. ✅ Extensive testing before merge
4. ✅ Database versioning and migrations

### Risk 2: Genre Data Stays "Unknown"

**Impact**: MEDIUM
**Probability**: MEDIUM (TMDB matching may fail)

**Mitigation**:
1. ✅ Manual classification for top 100 shows
2. ✅ Fallback to web scraping if API fails
3. ✅ Community-sourced genre tagging
4. ⚠️ Accept "Unknown" for rare content

### Risk 3: Video URLs Stop Working

**Impact**: HIGH
**Probability**: LOW (CDN stable)

**Mitigation**:
1. ✅ Monitor playback error rates
2. ✅ Keep scraper ready to refresh URLs
3. ✅ Log failed URLs for investigation
4. ✅ Fallback to website redirect

### Risk 4: Season Detection Fails

**Impact**: LOW
**Probability**: HIGH (data quality issue)

**Mitigation**:
1. ⚠️ Document limitation in release notes
2. ⚠️ Show all episodes in single list (acceptable UX)
3. ⚠️ Build season detection algorithm later
4. ✅ Don't block release on this

### Risk 5: Database Too Large for APK

**Impact**: MEDIUM
**Probability**: LOW (8 MB is acceptable)

**Mitigation**:
1. ✅ Current size: 8.05 MB (acceptable)
2. ⚠️ If grows: Use on-demand download
3. ⚠️ Compress with SQLCipher if needed
4. ⚠️ Split into base + DLC databases

### Risk 6: TMDB API Rate Limits

**Impact**: MEDIUM
**Probability**: HIGH (40 requests/10 seconds)

**Mitigation**:
1. ✅ Implement exponential backoff
2. ✅ Batch processing (50 series/minute)
3. ✅ Cache TMDB results locally
4. ✅ Run enrichment as background job

---

## 9. Testing Plan

### Unit Tests

```kotlin
// NamakadeDatabaseTest.kt
@Test
fun testDatabaseSchema() {
    val db = NamakadeDatabase.getInstance(context)

    // Verify tables exist
    val series = db.seriesDao().getAllSeries()
    val episodes = db.episodeDao().getAllEpisodes()

    assertEquals(1235, series.size)
    assertEquals(19373, episodes.size)
}

@Test
fun testVideoUrlsPopulated() {
    val db = NamakadeDatabase.getInstance(context)
    val episodes = db.episodeDao().getAllEpisodes()

    val withUrls = episodes.count { !it.videoUrl.isNullOrBlank() }
    assertEquals(19373, withUrls) // 100% coverage
}

@Test
fun testSeriesTypeSplit() {
    val db = NamakadeDatabase.getInstance(context)

    val allSeries = db.seriesDao().getAllSeries()
    val movies = allSeries.filter { it.contentType == "movie" }
    val series = allSeries.filter { it.contentType == "series" }

    assertEquals(312, movies.size)
    assertEquals(923, series.size)
}
```

### Integration Tests

```kotlin
// VideoPlaybackTest.kt
@Test
fun testNamakadeVideoPlayback() {
    val activity = launchActivity<VideoPlayerActivity>()

    val testUrl = "https://media.negahestan.com/.../S01E01.mp4"
    activity.playVideo(testUrl, DatabaseSource.NAMAKADE)

    // Verify player started
    assertTrue(activity.player?.isPlaying == true)

    // Verify not playing blocker
    delay(3000)
    val currentUrl = activity.player?.currentMediaItem?.localConfiguration?.uri.toString()
    assertFalse(currentUrl.contains("blockHacks.mp4"))
}
```

### Manual Testing Checklist

- [ ] Database loads successfully
- [ ] Browse shows movies (312 items)
- [ ] Browse shows series (923 items)
- [ ] Search finds Namakade content
- [ ] Movie details page displays
- [ ] Series details page displays
- [ ] Episode list shows all episodes
- [ ] Video playback works (not blocker)
- [ ] Switching databases works
- [ ] Genre enrichment updates database
- [ ] No crashes or ANRs

---

## 10. Estimated Timeline

| Phase | Tasks | Effort | Dependencies |
|-------|-------|--------|--------------|
| **Phase 1** | Basic DB integration | 1-2 days | None |
| **Phase 2** | Video playback | 1 day | Phase 1 |
| **Phase 3** | UI integration | 2-3 days | Phase 1 |
| **Phase 4** | Genre enrichment | 2-3 days | Phase 1 |
| **Phase 5** | Search integration | 1-2 days | Phase 3 |
| **Phase 6** | Detail screens | 1-2 days | Phase 3 |
| **Testing** | Full app testing | 1-2 days | All phases |
| | | | |
| **Total (MVP)** | Phases 1-3 + Testing | **5-8 days** | |
| **Total (Complete)** | All phases | **9-15 days** | |

---

## 11. Success Criteria

### MVP (Minimum Viable Product)

✅ Namakade database accessible as third source
✅ 1,235 shows browsable in app
✅ Video playback works for all 19,373 episodes
✅ Users can switch between FarsiFlow/FarsiPlex/Namakade

### Complete Integration

✅ All features from MVP
✅ Genre data enriched (< 10% "Unknown")
✅ Search works across all sources
✅ Detail pages show metadata
✅ No regressions in existing features

---

## 12. Next Steps

### Immediate (This Week)

1. **Copy database to assets**
   ```bash
   cp namakadeh.db app/src/main/assets/databases/
   ```

2. **Create NamakadeDatabase class**
   - Define entities
   - Create DAOs
   - Test database access

3. **Test video playback**
   - Add Referer header
   - Verify videos play correctly

### Short-Term (Next 2 Weeks)

4. **UI integration**
   - Repository adapter
   - Update ViewModels
   - Modify browse screens

5. **Genre enrichment**
   - TMDB API integration
   - Background worker
   - Manual top 100 classification

### Long-Term (Next Month)

6. **Schema unification** (optional)
   - Design unified schema
   - Migration scripts
   - Merge all databases

7. **Additional features**
   - Live TV integration
   - Season detection
   - Episode title scraping

---

## 13. Questions for Team

1. **TMDB API Key**: Do we have one? Need to create account?
2. **APK Size**: Is 8 MB database acceptable? (Total APK ~30 MB)
3. **Genre Priority**: Should we delay release until genres fixed?
4. **Schema Merge**: Keep separate or unify databases?
5. **Live TV**: Priority level for 70 channels?

---

## 14. References

### Documentation Files

- `NAMAKADE_SITE_ANALYSIS.md` - Website structure
- `NAMAKADE_ANALYSIS.md` - Technical analysis
- `API_ENDPOINTS.md` - API documentation
- `APP_DEVELOPMENT_PLAN.md` - Original development plan
- `NAMAKADE_COMPLETE_VERIFICATION.md` - Database verification
- `STREAMING_GUIDE.md` - Video streaming protocols

### Database Location

```
G:\FarsiPlex\namakade.com\namakadeh.db
```

### Scraper Source

```
G:\FarsiPlex\namakade.com\Scraper\namakadeh_master_scraper.py
```

---

**Plan Status**: ✅ Ready for Implementation
**Last Updated**: 2025-01-08
**Next Review**: After Phase 1 completion
