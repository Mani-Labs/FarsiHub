# Namakade Series Breakdown

## Summary

**Total Series**: 923

### Categories Available

| Category | Count | Tag | Filterable |
|----------|-------|-----|------------|
| **Turkish Series** | 121 | `genres LIKE '%Turkish%'` | ✅ YES |
| **Non-Turkish Series** | 802 | `genres IS NULL OR NOT LIKE '%Turkish%'` | ✅ YES |
| **Genre Classification** | 0 | N/A | ❌ NO |

---

## Breakdown Details

### Turkish Series (121)

**Identification**: Tagged via `isTurkish` flag in original database
**Genre Field**: "Turkish"
**URL Pattern**: `/series/{slug}` (no genre info)

**Examples**:
```
Balaaye Joonam        → Turkish
Sabade Rakhte Cherk   → Turkish
Eshghe Siaaho Sefid   → Turkish
Zamane Kooch          → Turkish
Khaahar Va Baraadaraanam → Turkish
```

**App Query**:
```kotlin
seriesDao.getSeriesByGenre("Turkish")
```

---

### Non-Turkish Series (802)

**Identification**: Iranian, international, and other content
**Genre Field**: `NULL` (no genre data)
**URL Pattern**: `/series/{slug}` (no genre info)

**Examples**:
```
Wanda Vision          → NULL
Heysiate Gomshodeh    → NULL
Paytakht 7            → NULL
Ghahveye Talkh        → NULL
Englisi               → NULL
```

**App Query**:
```kotlin
// Get all non-Turkish series
seriesDao.getAllSeries().filter { it.genres != "Turkish" }

// OR use SQL:
@Query("SELECT * FROM cached_series WHERE genres IS NULL OR genres NOT LIKE '%Turkish%'")
```

---

## Why No Detailed Genre Data?

### Movies Have Genres ✅
Movies use URL format: `/iran-1-movies/{genres}/{slug}`

Example:
```
/iran-1-movies/action-drama-thriller/noh
→ Genres: Action, Drama, Thriller
```

### Series Don't Have Genres ❌
Series use URL format: `/series/{slug}` (flat)

Example:
```
/series/paytakht-7
→ Genres: ??? (not in URL)
```

### To Get Series Genres
Would need to scrape individual show pages:

```python
def scrape_series_genre(slug):
    url = f"https://namakade.com/series/{slug}"
    # Parse HTML for genre tags
    # Example: <span class="genre">درام، اکشن</span>
```

**Effort**: ~2-3 days for 923 series

---

## App Implementation

### Browse Screen

**Option 1: Single List (Current)**
```kotlin
// Show all 923 series in one list
val allSeries = seriesDao.getAllSeries()
```

**Option 2: Tabbed Browse**
```kotlin
// Tab 1: Turkish Series (121)
val turkishSeries = seriesDao.getSeriesByGenre("Turkish")

// Tab 2: All Other Series (802)
val otherSeries = seriesDao.getAllSeries()
    .filter { !it.genres.contains("Turkish") }
```

**Option 3: Filter Toggle**
```kotlin
// Toggle: "Show Turkish Only" checkbox
if (showTurkishOnly) {
    seriesDao.getSeriesByGenre("Turkish")
} else {
    seriesDao.getAllSeries()
}
```

---

## Comparison with Movies

| Feature | Movies (312) | Series (923) |
|---------|--------------|--------------|
| **Genre Data** | ✅ 100% | ❌ 0% |
| **Turkish Tag** | ❌ No | ✅ 13.1% |
| **Animation Tag** | ✅ 27 items | ❌ No |
| **Theater Tag** | ✅ 50 items | ❌ No |
| **Kids Tag** | ✅ 14 items | ❌ No |
| **Genre Browse** | ✅ Full support | ⚠️ Turkish only |

---

## Recommendations

### For MVP (Ship Now) ✅
- Show all 923 series in single list
- Add "Turkish Series" filter/section
- Document limitation in release notes

**Pros:**
- Simple implementation
- All content accessible
- Turkish content discoverable

**Cons:**
- Can't filter by genre (drama, comedy, etc.)
- Poor content discovery for non-Turkish shows

---

### For Future Update (Phase 2)

**Option A: Scrape Website for Genres**
- Run scraper for all 923 series
- Extract genres from show pages
- Update database
- **Effort**: 2-3 days

**Option B: Manual Top 100 Classification**
- Classify most popular shows manually
- Leave rare content untagged
- **Effort**: 1 day

**Option C: User-Generated Tags**
- Allow users to suggest genres
- Community-sourced classification
- **Effort**: Ongoing

---

## Database Queries

### Get Turkish series count:
```sql
SELECT COUNT(*) FROM cached_series WHERE genres LIKE '%Turkish%';
-- Result: 121
```

### Get non-Turkish series count:
```sql
SELECT COUNT(*) FROM cached_series
WHERE genres IS NULL OR genres NOT LIKE '%Turkish%';
-- Result: 802
```

### Sample Turkish series:
```sql
SELECT title, genres FROM cached_series
WHERE genres LIKE '%Turkish%'
LIMIT 10;
```

### Sample non-Turkish series:
```sql
SELECT title, genres FROM cached_series
WHERE genres IS NULL
LIMIT 10;
```

---

## Conclusion

**What We Have:**
- ✅ 121 Turkish series properly tagged
- ✅ 802 other series accessible (no genre)
- ✅ Basic categorization (Turkish vs Other)

**What We're Missing:**
- ❌ Detailed genre data (drama, comedy, action, etc.)
- ❌ Content type tags (animation, kids, etc.)
- ❌ Advanced filtering options

**Recommended Action:**
Ship with current Turkish tagging for MVP. Add genre scraping in Phase 2 based on user demand.

---

**Status**: ✅ Acceptable for MVP - Turkish content discoverable
**Next Steps**: Monitor user feedback, add genre scraping if requested
