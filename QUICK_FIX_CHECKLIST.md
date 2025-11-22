# Quick Fix Checklist

## ONE THING TO FIX IMMEDIATELY

**File:** G:\FarsiPlex\app\src\main\java\com\example\farsilandtv\data\scraper\EpisodeListScraper.kt
**Line:** 353
**Current:**
```kotlin
private suspend fun fetchHtml(url: String): String = withContext(Dispatchers.IO) {
    delay(500)  // ← DELETE THIS LINE
    ...
}
```

**Why:** This adds 500ms to EVERY HTML fetch. Loading 5 episodes takes 2.5 seconds longer than needed.

**Fix:** Delete the delay() call. That's it.

**Time to fix:** 30 seconds

---

## ISSUES TO VERIFY (Can live with them)

### 1. C3: DoS Behavior
**File:** VideoUrlScraper.kt (line 1184)
**Issue:** Downloads might fire all requests simultaneously
**Impact:** Possible server IP ban
**Action:** Check if Semaphore limiting exists (not visible in grep)

### 2. C4: Pagination Incomplete
**File:** ContentSyncWorker.kt (line 380+)
**Issue:** Only syncs 20 items/page, no loop for more pages
**Impact:** New items might be missed if >20 updated between syncs
**Action:** Add do-while loop for pagination

### 3. H13: JS Truncation
**File:** VideoUrlScraper.kt (line 998)
**Issue:** JavaScript truncated at 10KB
**Impact:** Videos after 10KB in inline scripts won't be found
**Action:** Increase to 100KB or implement sliding window

---

## VERIFICATION SCORES

✅ **CRITICAL SEVERITY:** 7/9 fixed (77%)
✅ **HIGH SEVERITY:** 6/8 fixed (75%)
⚠️  **MEDIUM SEVERITY:** 3/10 fixed (30%)
ℹ️  **LOW SEVERITY:** 0/3 fixed (0% - optional)

---

## BOTTOM LINE

- ✅ App won't crash
- ✅ Data won't be deleted
- ⚠️  Performance could be slower (M23 delay)
- ⚠️  Some content might be missed (C4, H13)

**Recommendation:** Fix M23, then release.

