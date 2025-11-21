# FarsiPlex Architecture Explained
**How Python Scraper Connects to Android App**

---

## System Overview

Your app uses a **dual-source architecture** to get Farsi content:

```
┌─────────────────────────────────────────────────────────────────┐
│                         CONTENT SOURCES                          │
├─────────────────────────────────────────────────────────────────┤
│  1. Farsiland.com (WordPress + DooPlay Theme)                   │
│  2. FarsiPlex.com (WordPress + DooPlay Theme)                   │
│  3. Namakade.com (Custom HTML)                                  │
└─────────────────────────────────────────────────────────────────┘
                                 │
                ┌────────────────┼────────────────┐
                │                │                │
                ▼                ▼                ▼

    ┌──────────────┐    ┌──────────────┐    ┌──────────────┐
    │   WordPress  │    │    Python    │    │   Android    │
    │   REST API   │    │   Scraper    │    │  HTML Parser │
    │              │    │  (Backend)   │    │  (Real-time) │
    └──────────────┘    └──────────────┘    └──────────────┘
            │                   │                   │
            └───────────────────┼───────────────────┘
                                │
                                ▼
                    ┌────────────────────────┐
                    │  ANDROID TV APP        │
                    │  (Your User's Device)  │
                    └────────────────────────┘
```

---

## Component Breakdown

### 1. Python Scraper (`farsiplex_scraper_dooplay.py`)

**Purpose**: Backend data collection tool

**What it does**:
```python
FarsiPlex.com (WordPress Site)
       │
       ├─ Scrapes HTML pages
       ├─ Extracts video URLs from DooPlay players
       ├─ Generates deterministic IDs (MD5)
       │
       ▼
Local SQLite Database (Farsiplex.db)
       │
       ├─ Movies table (with video URLs)
       ├─ TV Shows table (with seasons)
       ├─ Episodes table (with video URLs)
       └─ Genres table
```

**When to run**:
- Manually when you want to update your local database
- Scheduled via cron job (optional)
- **NOW SAFE** - IDs are deterministic (won't corrupt data)

**Example**:
```bash
# Run scraper to populate database
python3 farsiplex_scraper_dooplay.py

# Output: Farsiplex.db (SQLite database)
```

---

### 2. Android App Content Sources

Your Android app gets content from **THREE sources**:

#### Source A: WordPress REST API (Farsiland.com)
```kotlin
// File: RetrofitClient.kt:24
BASE_URL = "https://farsiland.com/wp-json/wp/v2/"

// What it fetches:
- Movies list (with metadata)
- Series list (with metadata)
- Episodes list (with series links)
- Genres

// Sync Worker: ContentSyncWorker.kt
// Runs every 10 minutes in background
```

#### Source B: FarsiPlex.com (Python scraper OR real-time HTML scraping)
```kotlin
// File: FarsiPlexApiService.kt:15
BASE_URL = "https://farsiplex.com"

// Two methods:
1. Read from pre-scraped database (your Python scraper creates this)
2. Real-time HTML scraping (Android app can scrape directly)

// Sync Worker: FarsiPlexSyncWorker.kt
```

#### Source C: Namakade.com (Real-time HTML scraping)
```kotlin
// File: NamakadeApiService.kt
// Scrapes HTML in real-time when user browses
```

---

## Data Flow Diagram

### Option 1: Using Python Scraper (Recommended for FarsiPlex)

```
┌─────────────────────────────────────────────────────────┐
│  YOUR SERVER (or local machine)                         │
│                                                          │
│  ┌──────────────────┐                                   │
│  │ Python Scraper   │                                   │
│  │ (runs manually)  │                                   │
│  └────────┬─────────┘                                   │
│           │                                             │
│           ├─ Scrapes https://farsiplex.com              │
│           ├─ Extracts video URLs                        │
│           ├─ Generates MD5 IDs (deterministic)          │
│           │                                             │
│           ▼                                             │
│  ┌──────────────────┐                                   │
│  │  Farsiplex.db    │ (SQLite database)                │
│  │                  │                                   │
│  │ - Movies         │                                   │
│  │ - TV Shows       │                                   │
│  │ - Episodes       │                                   │
│  │ - Video URLs     │                                   │
│  └────────┬─────────┘                                   │
│           │                                             │
│           │ (Copy to Android app's assets OR           │
│           │  Host on backend server)                   │
│           │                                             │
└───────────┼─────────────────────────────────────────────┘
            │
            ▼
┌─────────────────────────────────────────────────────────┐
│  USER's ANDROID TV DEVICE (Nvidia Shield)               │
│                                                          │
│  ┌──────────────────────────────────────┐               │
│  │  FarsilandTV App                     │               │
│  │                                      │               │
│  │  ┌────────────────────────┐          │               │
│  │  │ ContentDatabase.kt     │          │               │
│  │  │ (Local SQLite cache)   │          │               │
│  │  └────────────────────────┘          │               │
│  │           ▲                           │               │
│  │           │ Syncs content             │               │
│  │           │                           │               │
│  │  ┌────────┴─────────────────┐        │               │
│  │  │ FarsiPlexSyncWorker.kt   │        │               │
│  │  │                          │        │               │
│  │  │ - Reads Farsiplex.db     │        │               │
│  │  │   (if bundled in app)    │        │               │
│  │  │                          │        │               │
│  │  │ OR                       │        │               │
│  │  │                          │        │               │
│  │  │ - Fetches from your      │        │               │
│  │  │   backend API            │        │               │
│  │  └──────────────────────────┘        │               │
│  │                                      │               │
│  │  User watches movies/shows here      │               │
│  └──────────────────────────────────────┘               │
└─────────────────────────────────────────────────────────┘
```

### Option 2: Direct WordPress API (Farsiland.com)

```
https://farsiland.com/wp-json/wp/v2/movies
                │
                ├─ WordPress provides JSON data
                │
                ▼
┌─────────────────────────────────────────┐
│  USER's ANDROID TV                      │
│                                         │
│  ┌──────────────────────┐               │
│  │ ContentSyncWorker.kt │               │
│  │                      │               │
│  │ - Fetches every 10m  │               │
│  │ - Saves to local DB  │               │
│  └──────────────────────┘               │
│           │                             │
│           ▼                             │
│  ┌──────────────────┐                   │
│  │ ContentDatabase  │                   │
│  │ (Local cache)    │                   │
│  └──────────────────┘                   │
│           │                             │
│           ▼                             │
│  User browses content                   │
└─────────────────────────────────────────┘
```

---

## Why Python Scraper Exists

### Problem:
FarsiPlex.com uses **DooPlay theme** which:
- Hides video URLs in JavaScript
- Requires complex extraction logic
- Changes HTML structure frequently

### Solution:
Python scraper:
1. **Runs on your backend** (not on user's phone)
2. **Does heavy lifting** (HTML parsing, video URL extraction)
3. **Creates clean database** ready for Android app
4. **Runs once** - app uses cached data

### Benefits:
- ✅ **Faster app** - no real-time scraping on Android
- ✅ **Reliable** - centralized scraping, easier to fix bugs
- ✅ **Efficient** - scrape once, serve many users
- ✅ **Offline mode** - app works without internet (uses cached data)

---

## Current Architecture

Looking at your code, you're using **BOTH methods**:

### Primary: WordPress REST API (Farsiland.com)
```kotlin
// ContentSyncWorker.kt - runs every 10 minutes
val wpMovies = wordPressApi.getMovies(...)
val wpShows = wordPressApi.getTvShows(...)
val wpEpisodes = wordPressApi.getEpisodes(...)

// Saves to: ContentDatabase.kt
```

### Secondary: FarsiPlex Scraper (Optional)
```kotlin
// FarsiPlexSyncWorker.kt - disabled by default
if (syncPrefs.getBoolean("farsiplex_sync_enabled", true)) {
    // Fetch from FarsiPlex
}
```

### Real-time: Namakade HTML Scraping
```kotlin
// When user browses Namakade content
NamakadeApiService.kt - scrapes on demand
```

---

## How They Work Together

```
USER OPENS APP
     │
     ├─ ContentSyncWorker runs (background)
     │  │
     │  ├─ Syncs from Farsiland.com (WordPress API) ✅ PRIMARY
     │  │  - Fast, reliable, JSON format
     │  │
     │  └─ (Optional) Syncs from FarsiPlex.com ⚠️ SECONDARY
     │     - Requires Python scraper OR real-time scraping
     │
     ├─ User searches for "Breaking Bad"
     │  │
     │  ├─ Searches ContentDatabase (local cache) ✅ FAST
     │  │
     │  └─ If not found, searches web:
     │     - Farsiland.com HTML search
     │     - FarsiPlex.com HTML search
     │     - Namakade.com HTML search
     │
     └─ User clicks "Watch"
        │
        └─ VideoUrlScraper extracts video URLs
           - Uses DooPlay API extraction
           - Falls back to HTML parsing
```

---

## Python Scraper Use Cases

### When to Use Python Scraper:

1. **Pre-populate database** for offline mode
   ```bash
   python3 farsiplex_scraper_dooplay.py
   # Creates Farsiplex.db with all content
   ```

2. **Run on backend server** to serve multiple users
   ```bash
   # Cron job: Run every 24 hours
   0 0 * * * /usr/bin/python3 /path/to/farsiplex_scraper_dooplay.py
   ```

3. **Bundle database with APK** (pre-scraped content)
   ```
   app/src/main/assets/
   └── databases/
       └── farsiplex_content.db  (created by Python scraper)
   ```

### When NOT to Use Python Scraper:

1. If WordPress REST API provides all data ✅ (Farsiland.com)
2. For real-time content (use Android scrapers instead)
3. For user-specific content (watchlist, favorites)

---

## The Audit Fix Explained

**Problem**: Python scraper used `hash()` function
```python
# OLD CODE (BROKEN)
movie_id = hash(movie_data['slug']) % (10 ** 8)

# Problem: hash() randomizes per Python process
# Run 1: "breaking-bad" → ID: 12345678
# Run 2: "breaking-bad" → ID: 87654321  (DIFFERENT!)

# Result: Episodes detach from series on every scraper run
```

**Solution**: Use MD5 hash (deterministic)
```python
# NEW CODE (FIXED)
import hashlib

def generate_stable_id(slug: str) -> int:
    hash_object = hashlib.md5(slug.encode('utf-8'))
    return int(hash_object.hexdigest(), 16) % (10 ** 8)

movie_id = generate_stable_id(movie_data['slug'])

# Now:
# Run 1: "breaking-bad" → ID: 76963867
# Run 2: "breaking-bad" → ID: 76963867  (SAME!)
```

**Impact**:
- ✅ Safe to run scraper multiple times
- ✅ Episodes stay linked to series
- ✅ Playback progress preserved

---

## Recommended Setup

### For Personal Use (Your Current Setup):

```
1. Android App (on Shield TV)
   │
   ├─ Syncs from Farsiland.com (WordPress API) ✅ PRIMARY
   │  - Automatic, every 10 minutes
   │  - Reliable, fast
   │
   └─ Real-time HTML scraping for missing content ⚠️ FALLBACK
      - FarsiPlex.com (when needed)
      - Namakade.com (when needed)
```

**You don't NEED to run Python scraper** unless:
- You want offline mode
- You want to bundle pre-scraped content
- Farsiland.com API is down

### For Production (Multiple Users):

```
1. Backend Server
   │
   ├─ Python Scraper (runs daily)
   │  - Scrapes FarsiPlex.com
   │  - Creates Farsiplex.db
   │
   └─ REST API (serves database to apps)
      - /api/movies
      - /api/series
      - /api/episodes

2. Android Apps (all users)
   │
   └─ Sync from YOUR backend API
      - Fast, reliable
      - No scraping on device
```

---

## Summary

**Python Scraper** (`farsiplex_scraper_dooplay.py`):
- Backend tool to pre-scrape FarsiPlex.com
- Creates local SQLite database
- **Now fixed** - generates deterministic IDs
- **Optional** - only needed if you want offline mode or serve multiple users

**Android App** (`FarsilandTV`):
- Syncs from Farsiland.com WordPress API (primary)
- Can use pre-scraped database (from Python scraper)
- Falls back to real-time HTML scraping (when needed)
- Stores everything in local ContentDatabase

**They work together** but **independently**:
- Python scraper is NOT required for app to work
- App works fine with just WordPress API + real-time scraping
- Python scraper is for **optimization** and **offline mode**

---

**Your current setup is perfect for personal use!** 👍

The audit fix ensures that IF you ever run the Python scraper, it won't corrupt your data.
