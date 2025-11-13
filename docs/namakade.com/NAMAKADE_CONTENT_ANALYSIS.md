# Namakade Content Categories Analysis

## Executive Summary

**All content is stored in a single `series` table with `contentType` field:**
- `contentType='movie'` → Movies (including animations, clips, theater plays)
- `contentType='series'` → TV Series

**Categories are NOT separate** - they're embedded as genres in URL paths for movies only.

---

## Content Breakdown

### Movies (312 total)
All movies use path format: `/iran-1-movies/{genres}/{slug}`

#### Discovered Sub-Categories (from URL analysis):

| Category | Count | URL Pattern | Examples |
|----------|-------|-------------|----------|
| **Animation** | 27 | `/iran-1-movies/animation-*/` | Disney/Pixar movies, anime |
| **Theater Plays** | 50 | `/iran-1-movies/*theaterplays*/` | Stage performances |
| **Kids Content** | 14 | `/iran-1-movies/*kidschannels*/` | Children's shows |
| **Short Films** | 2 | `/iran-1-movies/*shortfilm*/` | Short format content |
| **Regular Movies** | ~219 | `/iran-1-movies/{genre}/` | Standard films |

### Series (923 total)
All series use path format: `/series/{slug}`

**Categorization Available:**
- ✅ **Turkish Series**: 121 (tagged via `isTurkish` flag)
- ✅ **Non-Turkish Series**: 802 (Iranian, international)
- ❌ **Genre Data**: Not available in URLs (would need website scraping)

---

## URL Pattern Analysis

### Movies - Genre Extraction Works ✅

**Pattern**: `/iran-1-movies/{genre1-genre2-genre3}/{slug}`

**Examples**:
```
/iran-1-movies/animation-comedy-kidschannels/roya-shahr
→ Genres: Animation, Comedy, Kidschannels

/iran-1-movies/action-drama-thriller/noh
→ Genres: Action, Drama, Thriller

/iran-1-movies/theaterplays/be-zamin-seporde
→ Genres: Theaterplays

/iran-1-movies/animation-foreign-oscar/zaaher-o-baten-2
→ Genres: Animation, Foreign, Oscar

/iran-1-movies/animation-shortfilm/dar-saayeh-sarv
→ Genres: Animation, Shortfilm
```

### Series - No Genre Extraction ❌

**Pattern**: `/series/{slug}` (flat, no genres)

**Examples**:
```
/series/anjomane-ashbah
/series/paytakht-7
/series/shabhaaye-barareh
/series/ghahveye-talkh
```

---

## Current Converter Behavior

### ✅ What Works
1. **Movies** → Genres extracted from URL path (100% coverage)
2. **Movies** → Split into sub-categories via genre tags:
   - Animation movies have "Animation" genre
   - Theater plays have "Theaterplays" genre
   - Kids content has "Kidschannels" genre
   - Shorts have "Shortfilm" genre
3. **Series** → Turkish content tagged (121 Turkish series identified)
4. **Series** → 13.1% genre coverage (Turkish tag provides basic categorization)

### ⚠️ Partial Coverage
1. **Series** → No detailed genre extraction (URLs don't contain genres)
2. **Series** → 802 non-Turkish series have no genre categorization

---

## Recommendation: Add Content Tagging

Since genres are embedded in movie URLs but not series URLs, we should:

### Option 1: Keep Current Approach (RECOMMENDED for MVP)
- Movies: Fully tagged with genres ✅
- Series: No genres (acceptable - user can browse all)
- Clips/Cartoons/Theater: Tagged as genres within movies

**Pros:**
- Already works
- 100% of movies have proper categorization
- Animation, Theater, Kids content properly tagged

**Cons:**
- Series lack genre classification
- Can't filter series by genre

### Option 2: Add Content Type Tagging
Extend the converter to detect special content types and add them as tags:

```python
def detect_content_tags(link_path, genres):
    """Detect special content types and add as tags"""
    tags = []

    if 'animation' in genres.lower():
        tags.append('ANIMATED')

    if 'kidschannels' in genres.lower():
        tags.append('KIDS')

    if 'theaterplays' in genres.lower():
        tags.append('THEATER')

    if 'shortfilm' in genres.lower():
        tags.append('SHORT')

    return tags
```

Then store in a new `tags` field in the database.

### Option 3: Browse Website for Series Genres
Create a scraper to extract genres from series pages on Namakade.com:

```python
def scrape_series_genre(series_slug):
    url = f"https://namakade.com/series/{series_slug}"
    response = requests.get(url)
    # Parse HTML to extract genre
    # Update database
```

**Effort**: 2-3 days for all 923 series

---

## Current Database Structure

### Movies (312)
```sql
SELECT id, title, genres FROM cached_movies LIMIT 3;

5218366 | mantagheye mordeh2        | Classic, Drama, Foreign, Thriller
18358600| sham ba doostan           | Theaterplays
59047212| shahzaadeye maghroor      | Animation, Foreign, Kidschannels
```

✅ **100% have genres extracted**

### Series (923)
```sql
SELECT id, title, genres FROM cached_series LIMIT 3;

1183538 | Wanda Vision             | NULL
2288909 | Heysiate Gomshodeh       | NULL
3035810 | Englisi                  | NULL
```

❌ **0% have genres** (not in URL)

---

## Content Type Separation

### Current: Single Table Approach ✅
All content in one table with `contentType` field:
- Simple queries: `WHERE contentType = 'movie'`
- Genre filtering: `WHERE genres LIKE '%Animation%'`
- Turkish series filtering: `WHERE genres LIKE '%Turkish%'`

### Alternative: Separate Tables (NOT RECOMMENDED)
Could split into:
- `cached_movies` → Regular movies
- `cached_animations` → Animated content
- `cached_theater` → Theater plays
- `cached_shorts` → Short films
- `cached_series` → TV series

**Cons:**
- More complex queries
- Harder to maintain
- Content doesn't fit neatly (e.g., "Action Animation" goes where?)

---

## Filtering Examples

With current structure, users can filter by genre:

```kotlin
// Get all animations (movies)
movieDao.getMoviesByGenre("Animation")

// Get all theater plays
movieDao.getMoviesByGenre("Theaterplays")

// Get all kids content
movieDao.getMoviesByGenre("Kidschannels")

// Get all shorts
movieDao.getMoviesByGenre("Shortfilm")
```

---

## Conclusion

### What We Have:
✅ Movies fully categorized with genres (312/312)
✅ Special content (animation, theater, kids) properly tagged
✅ Content accessible via genre filtering
❌ Series have no genre data (923 series)

### What We're Missing:
- Series genre classification
- No way to know if a series is comedy, drama, action, etc.

### Recommended Action:
**Ship with current state for MVP:**
- Movies work perfectly with full genre categorization
- Series browsable as single list (acceptable UX)
- Add series genre scraping in future update (Phase 2)

---

## Sample Queries for App

### Get all animated movies:
```sql
SELECT * FROM cached_movies WHERE genres LIKE '%Animation%';
-- Returns: 27 animated movies
```

### Get all theater plays:
```sql
SELECT * FROM cached_movies WHERE genres LIKE '%Theaterplays%';
-- Returns: 50 theater performances
```

### Get all kids content:
```sql
SELECT * FROM cached_movies WHERE genres LIKE '%Kidschannels%';
-- Returns: 14 kids shows/movies
```

### Get all short films:
```sql
SELECT * FROM cached_movies WHERE genres LIKE '%Shortfilm%';
-- Returns: 2 short films
```

### Get all Turkish series:
```sql
SELECT * FROM cached_series WHERE genres LIKE '%Turkish%';
-- Returns: 121 Turkish series
```

### Get all non-Turkish series:
```sql
SELECT * FROM cached_series WHERE genres IS NULL OR genres NOT LIKE '%Turkish%';
-- Returns: 802 non-Turkish series
```

### Get all series (mixed):
```sql
SELECT * FROM cached_series ORDER BY dateAdded DESC;
-- Returns: 923 series total
```

---

**Status**: ✅ Content properly categorized for movies, acceptable for MVP
**Next Steps**: Consider series genre scraping in Phase 2 (optional)
