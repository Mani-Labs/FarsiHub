# Agent Team Assignments - Audit Remediation

**Execute in parallel. Each agent verifies first, fixes only if confirmed TRUE.**

---

## TEAM A: Database & Exception Fixes
**Agent:** `debugger`
**Issues:** C9, H11, H12
**Files:** ContentDatabase.kt, ContentDao.kt, ContentRepository.kt
**Time:** 2-3 hours

### C9: File-In-Use Crash
- **File:** `ContentDatabase.kt:300`
- **Check:** Is `context.deleteDatabase()` called while DB might be open?
- **If TRUE:** Implement safe swap (close connections first, use rename instead)
- **Result:** No crash on database replacement

### H11: FTS Query Syntax Crash
- **File:** `ContentDao.kt:82, 192, 246`
- **Check:** Is raw user input passed to MATCH operator?
- **If TRUE:** Wrap in quotes, escape special chars (" * -)
- **Result:** Search works for all inputs

### H12: Pagination Memory Leak
- **File:** `ContentRepository.kt:285, 359-366`
- **Check:** Does pagination use `subList()` or SQL OFFSET?
- **If TRUE:** Switch to `getRecentMoviesWithOffset(limit, offset)`
- **Result:** Fast pagination, no memory leak

---

## TEAM B: Performance Optimization
**Agent:** `performance-engineer`
**Issues:** H5, H13
**Files:** VideoUrlScraper.kt
**Time:** 2-3 hours

### H5: Regex Performance on Large Inputs
- **File:** `VideoUrlScraper.kt:638`
- **Check:** Is complex regex run on 15MB strings?
- **If TRUE:** Truncate to 1MB or use streaming parser
- **Result:** No ANR on slow devices

### H13: Broken Scrapers (JavaScript Truncation)
- **File:** `VideoUrlScraper.kt:1548`
- **Check:** Is JavaScript truncated to 10,000 chars?
- **If TRUE:** Increase to 100KB or use sliding window
- **Result:** All video URLs found

---

## TEAM C: Data Flow & Error Handling
**Agent:** `backend-architect`
**Issues:** H14, H15, M19
**Files:** MainViewModel.kt, ContentRepository.kt
**Time:** 2-3 hours

### H14: All-or-Nothing Content Loading
- **File:** `MainViewModel.kt:128`
- **Check:** Does single try/catch wrap all async jobs?
- **If TRUE:** Use `supervisorScope` or separate error handling
- **Result:** Partial content loads if one API fails

### H15: Strict Date Parsing Failure
- **File:** `ContentRepository.kt:1366`
- **Check:** Is `Instant.parse()` called without fallback?
- **If TRUE:** Add flexible parser (handle non-ISO-8601 dates)
- **Result:** All dates parse correctly

### M19: Auto-Refresh Race Condition
- **File:** `MainViewModel.kt:87, 145`
- **Check:** Can multiple workers trigger conflicting refreshes?
- **If TRUE:** Add debounce or cancel pending jobs
- **Result:** No duplicate network requests

---

## TEAM D: Python Scripts
**Agent:** `python-pro`
**Issues:** H16, H17, H18
**Files:** farsiplex_scraper_dooplay.py, generate_content_database.py, RetrofitClient.kt
**Time:** 2-3 hours

### H16: Database Thrashing (Python)
- **File:** `farsiplex_scraper_dooplay.py:317`
- **Check:** Is new DB connection created in loop?
- **If TRUE:** Reuse single connection for batch
- **Result:** Faster scraping, less I/O

### H17: Metadata Black Hole (Python)
- **File:** `generate_content_database.py:108-110`
- **Check:** Are Runtime, Director, Cast hardcoded NULL?
- **If TRUE:** Extract actual metadata if available
- **Result:** Rich metadata in offline database

### H18: User-Agent Mismatch
- **File:** `farsiplex_scraper_dooplay.py` vs `RetrofitClient.kt`
- **Check:** Do script and app use different User-Agents?
- **If TRUE:** Sync to same User-Agent string
- **Result:** Consistent server access

---

## TEAM E: Code Quality & Medium Issues
**Agent:** `code-reviewer`
**Issues:** M20, M21, M22, L28, L29, L30
**Files:** ContentRepository.kt, EpisodeListScraper.kt, VideoUrlScraper.kt
**Time:** 2-3 hours

### M20: Naive HTML Stripping
- **File:** `ContentRepository.kt:1435`
- **Check:** Does regex `<[^>]+>` leave `<script>` content?
- **If TRUE:** Use `Html.fromHtml()` or remove script/style
- **Result:** No raw JavaScript in descriptions

### M21: Hash Collision in Episode IDs
- **File:** `EpisodeListScraper.kt:218`
- **Check:** Is `String.hashCode()` used for IDs?
- **If TRUE:** Use Long IDs with unique components
- **Result:** No DiffUtil issues

### M22: Weak Quality Detection
- **File:** `VideoUrlScraper.kt:1588`
- **Check:** Does `contains("1080")` match "1080 Hours"?
- **If TRUE:** Use regex for "1080p" with delimiters
- **Result:** Correct quality detection

### L28: Regex Object Churn
- **File:** `VideoUrlScraper.kt`, `EpisodeListScraper.kt`
- **Check:** Is `Regex()` compiled in loops?
- **If TRUE:** Move to constants
- **Result:** Reduced CPU usage

### L29: Hardcoded Source Logic
- **File:** Various (url.contains("namakade"))
- **Check:** How many hardcoded source checks?
- **If TRUE:** Move to Strategy pattern
- **Result:** Cleaner architecture

### L30: Hardcoded English Strings
- **File:** Various error messages
- **Check:** Are user-facing strings hardcoded in code?
- **If TRUE:** Move to strings.xml
- **Result:** Can be translated to Farsi

---

## TEAM F: UI & Database Issues
**Agent 1:** `frontend-developer` (M25, M27)
**Agent 2:** `database-optimizer` (M24, M26)
**Issues:** M24, M25, M26, M27
**Files:** AppDatabase.kt, ImageLoader.kt, ContentDatabase.kt
**Time:** 2-3 hours

### M24: Migration Path Fragility
- **File:** `AppDatabase.kt:380`
- **Check:** Does ATTACH DATABASE use relative path?
- **If TRUE:** Use absolute path
- **Result:** Works on all device profiles

### M25: Image Aspect Ratio Distortion
- **File:** `ImageLoader.kt:97`
- **Check:** Is `Scale.FILL` used (stretches image)?
- **If TRUE:** Use `Scale.FIT` or `centerCrop`
- **Result:** No stretched posters

### M26: Fire and Forget Asset Copy
- **File:** `ContentDatabase.kt:62`
- **Check:** Is database copy on main thread?
- **If TRUE:** Move to `Dispatchers.IO`
- **Result:** No ANR on first launch

### M27: Ghost Context Leak
- **File:** `ImageLoader.kt:135`
- **Check:** Does `preloadAdjacentImages` capture Activity context?
- **If TRUE:** Use `applicationContext`
- **Result:** No memory leak

---

## Execution Checklist

### Before Starting
- [ ] All agents read this document
- [ ] Each agent knows their 2-4 issues
- [ ] Each agent has file paths and line numbers

### During Execution
- [ ] Agent verifies issue FIRST (code inspection)
- [ ] If TRUE: Implement minimal fix
- [ ] Run `./gradlew compileDebugKotlin` after each fix
- [ ] Report: [ISSUE] [TRUE/FALSE/ALREADY_FIXED] [FILES_CHANGED]

### After Each Phase
- [ ] All agents report completion
- [ ] Compilation check passes
- [ ] No new warnings
- [ ] CLAUDE.md updated

### Final Sync
- [ ] All issues verified (20/20)
- [ ] All fixes compiled (compilation successful)
- [ ] All tests passing
- [ ] Ready for commit

---

## Format for Agent Reports

Each agent should report:

```
## [AGENT_NAME] - Phase [X] Complete

### Issue Status
- [C9/H11/etc]: [VERIFICATION RESULT]
- [H12/H15/etc]: [VERIFICATION RESULT]
- [M19/etc]: [VERIFICATION RESULT]

### Files Changed
- File1.kt: Lines X-Y (brief description)
- File2.kt: Lines X-Y (brief description)

### Compilation Test
./gradlew compileDebugKotlin
[RESULT: ✅ SUCCESS or ❌ FAILED]

### Notes
- Any side effects
- Any risks
- Time taken
```

---

## Ready to Execute?

Once all agents ready: Start Phase 1 (Teams A, B, C, D in parallel)

Estimated time: 6-8 hours total (teams working in parallel)
