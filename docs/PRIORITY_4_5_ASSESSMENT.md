# Priority 4 & 5 Assessment

**Assessment Date:** 2025-11-12
**User Request:** Evaluate remaining priorities before build/test

---

## Priority 4: Taxonomy Resolution (Cast/Creators)

### Current Status

**IDs Captured?** NO
- `WPModels.kt` does NOT include `dtcast`, `dtcreator`, `dtyear` fields
- API returns these IDs but we're ignoring them
- Example from API: `"dtcast": [6982, 1692, 8480]`

**Endpoints Work?** YES
- Tested: `https://farsiland.com/wp-json/wp/v2/dtcast/6982`
- Returns: `{"id": 6982, "name": "Alireza Jafari", "slug": "alireza-jafari"}`
- Endpoints available: `/dtcast/{id}`, `/dtcreator/{id}`, `/dtyear/{id}`

### Effort Estimate

**Original:** 6-10 hours
**Revised:** 8-12 hours (more accurate)

**What's Required:**

1. **Model Changes** (1 hour)
   - Add `dtcast`, `dtcreator`, `dtyear` fields to `WPTvShow` and `WPMovie`
   - Add new models: `WPCastMember`, `WPCreator`

2. **Database Changes** (2 hours)
   - Add `cast_members` table
   - Add `creators` table
   - Add junction tables for many-to-many relationships
   - Database migration code

3. **API Changes** (2 hours)
   - Add `getCastMember(id)` endpoint to `WordPressApiService`
   - Add `getCreator(id)` endpoint
   - Implement batch resolution (10+ IDs per series)

4. **Sync Logic** (2 hours)
   - Resolve cast/creator IDs during content sync
   - Handle rate limiting (could be 50+ API calls for 20 series)
   - Cache resolved names to avoid re-fetching
   - Error handling for missing IDs

5. **UI Changes** (2 hours)
   - Update `SeriesDetailsActivity` to display cast
   - Update layout XML
   - Handle loading states

6. **Testing** (1 hour)
   - Test with series that have cast data
   - Test with series missing cast data
   - Test caching behavior

### Risks

1. **Rate Limiting** - 20 series × 10 cast members = 200 API calls per sync
2. **Performance** - Additional database complexity
3. **Sync Time** - Could increase from 30s to 2-3 minutes
4. **Maintenance** - More moving parts to maintain

### Recommendation: SKIP FOR NOW

**Reasons:**

1. **LOW Priority** - User wants to TEST soon, not add features
2. **8-12 Hours Work** - Significant time investment for cosmetic feature
3. **No User Request** - User hasn't asked for cast information
4. **Complexity** - Adds database tables, API calls, sync logic
5. **Rate Limit Risk** - Could impact sync reliability

**Better Alternative:**

- **Store IDs now** (30 minutes) - Add fields to capture IDs
- **Resolve later** (post-launch) - Implement lazy loading when user views details
- **UI-triggered** - Only fetch cast names when user opens series details, not during sync

**If Needed Later:**

```kotlin
// Minimal implementation - Store IDs now
data class WPTvShow(
    // ... existing fields ...
    @Json(name = "dtcast") val castIds: List<Int> = emptyList(),
    @Json(name = "dtcreator") val creatorIds: List<Int> = emptyList()
)

// Resolve on-demand in SeriesDetailsActivity
suspend fun loadCastNames() {
    viewModelScope.launch {
        castNames = series.castIds.map { id ->
            try { api.getCastMember(id).name } catch (e: Exception) { null }
        }.filterNotNull()
    }
}
```

**Time:** 1-2 hours vs 8-12 hours
**Impact:** Same end result, less upfront work

---

## Priority 5: Explore FarsiPlex WordPress API

### Status: ALREADY COMPLETE

**Investigated During Priority 2:**

Tested FarsiPlex custom post type endpoints:
- `/wp-json/wp/v2/movies` - Returns 404
- `/wp-json/wp/v2/tvshows` - Returns 404
- `/wp-json/wp/v2/episodes` - Returns 404

**Documented In:**
- `docs/DOOPLAY_API_TEST_RESULTS.md`
- Lines 199-270 detail full investigation

**Conclusion:**
- Custom post types NOT exposed via WordPress REST API
- DooPlay API exists but returns empty data (not configured)
- Current sitemap + POST /play/ method works perfectly
- No benefit to using API even if available

**Recommendation:** MARK AS COMPLETE

---

## Overall Assessment

### Summary Table

| Priority | Status | Effort | Impact | Recommendation |
|----------|--------|--------|--------|----------------|
| Priority 1 (Farsiland sync) | COMPLETE | 2h | HIGH | Done |
| Priority 2 (DooPlay API) | COMPLETE | 4h | NONE | Tested, not usable |
| Priority 3 (Query filters) | COMPLETE | 3h | MEDIUM | Implemented |
| Priority 4 (Taxonomy) | PENDING | 8-12h | LOW | SKIP |
| Priority 5 (FarsiPlex API) | COMPLETE | 4h | NONE | Already tested |

### Recommendation: PROCEED TO BUILD/TEST NOW

**Reasons:**

1. All CRITICAL/HIGH priorities are complete
2. App is in working state (97 tests passing, 75% coverage)
3. User explicitly wants to "build and install on emulator to test"
4. Priority 4 is LOW priority feature (8-12 hours work)
5. Priority 5 already investigated (no action needed)

**Next Steps:**

1. Build debug APK
2. Install on emulator
3. Test core functionality:
   - FarsiPlex content sync
   - Farsiland content sync (with new modified date fix)
   - Video playback
   - Search
   - Watchlist
4. Identify any bugs from real-world testing
5. Fix critical bugs if found
6. Consider Priority 4 ONLY if user requests cast information

---

## Priority 4 - If User Insists

**Minimal Implementation Plan:**

### Phase 1: Capture IDs (30 minutes)
```kotlin
// WPModels.kt - Add fields
@Json(name = "dtcast") val castIds: List<Int> = emptyList(),
@Json(name = "dtcreator") val creatorIds: List<Int> = emptyList(),
@Json(name = "dtyear") val yearIds: List<Int> = emptyList()
```

### Phase 2: Store in DB (1 hour)
```kotlin
// AppDatabase - Add columns to cached_series table
@ColumnInfo(name = "cast_ids") val castIds: String = "", // JSON array
@ColumnInfo(name = "creator_ids") val creatorIds: String = ""
```

### Phase 3: Lazy Resolution (2 hours)
```kotlin
// SeriesDetailsViewModel - Load on-demand
fun loadCastDetails() {
    viewModelScope.launch {
        _castMembers.value = series.castIds.map { id ->
            repository.getCastMember(id)
        }
    }
}
```

**Total Time:** 3-4 hours (vs 8-12 for full implementation)
**Benefit:** Same user experience, less complexity
**Trade-off:** Cast loads slower (when viewing details, not during sync)

---

## Final Recommendation

**BUILD AND TEST NOW** - All necessary work is complete.

Priority 4 is optional cosmetic enhancement that can be added post-launch if user requests it.

---

**Next Command:**
```bash
cd G:\FarsiPlex
.\gradlew.bat assembleDebug
```
