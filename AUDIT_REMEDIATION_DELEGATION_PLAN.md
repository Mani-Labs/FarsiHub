# Audit Remediation Delegation Plan
**Date:** 2025-11-22
**Status:** Strategic Recommendation (Pre-Execution)
**Agent Organizer:** Claude Code
**Target:** 20 Remaining Audit Issues (C9, H5-H18, M19-M27, L28-L30)

---

## Executive Summary

**Current Status:**
- ✅ 10-12 issues already fixed (C1-C8, H1-H4, L23)
- ⏳ **20 issues remaining** to verify and fix
- 🎯 **Estimated Effort:** 15-20 hours (3-4 focused sessions)
- 📊 **Priority:** Critical (1) → High (8) → Medium (9) → Low (2)

**Recommended Approach:**
- **Phase 1 (CRITICAL):** C9 + H5-H10 (6 issues) - 5-6 hours
- **Phase 2 (HIGH):** H11-H18 (8 issues) - 6-8 hours
- **Phase 3 (MEDIUM):** M19-M27 (9 issues) - 4-5 hours
- **Phase 4 (LOW):** L28-L30 (2 issues) - 1 hour

**Key Principle:** Verify FIRST before fixing. Not all claims are true; some are already fixed.

---

## Issue Catalog (20 Remaining)

### CRITICAL PRIORITY (1 issue)

| # | Issue | File | Type | Status |
|---|-------|------|------|--------|
| **C9** | File-In-Use Crash (DB Swapping) | ContentDatabase.kt:300 | Database/Concurrency | ⏳ VERIFY |

### HIGH PRIORITY (8 issues)

| # | Issue | File | Type | Status |
|---|-------|------|------|--------|
| **H5** | Regex Performance on Large Inputs | VideoUrlScraper.kt:638 | Performance | ⏳ VERIFY |
| **H11** | FTS Query Syntax Crash | ContentDao.kt:82,192,246 | Database/Safety | ⏳ VERIFY |
| **H12** | Pagination Memory Leak | ContentRepository.kt:285 | Performance/Memory | ⏳ VERIFY |
| **H13** | Broken Scrapers (JS Truncation) | VideoUrlScraper.kt:1548 | Scraping/Quality | ⏳ VERIFY |
| **H14** | All-or-Nothing Content Loading | MainViewModel.kt:128 | Error Handling | ⏳ VERIFY |
| **H15** | Strict Date Parsing Failure | ContentRepository.kt:1366 | Data Processing | ⏳ VERIFY |
| **H16** | Database Thrashing (Python) | farsiplex_scraper_dooplay.py:317 | Python Performance | ⏳ VERIFY |
| **H17** | Metadata Black Hole (Python) | generate_content_database.py:108-110 | Python/Data | ⏳ VERIFY |
| **H18** | User-Agent Mismatch | farsiplex_scraper_dooplay.py vs RetrofitClient.kt | Scraping/Compatibility | ⏳ VERIFY |

### MEDIUM PRIORITY (9 issues)

| # | Issue | File | Type | Status |
|---|-------|------|------|--------|
| **M19** | Auto-Refresh Race Condition | MainViewModel.kt:87,145 | Concurrency | ⏳ VERIFY |
| **M20** | Naive HTML Stripping | ContentRepository.kt:1435 | Security/Data | ⏳ VERIFY |
| **M21** | Hash Collision in Episode IDs | EpisodeListScraper.kt:218 | Data Integrity | ⏳ VERIFY |
| **M22** | Weak Quality Detection | VideoUrlScraper.kt:1588 | Data Quality | ⏳ VERIFY |
| **M24** | Migration Path Fragility | AppDatabase.kt:380 | Database/Reliability | ⏳ VERIFY |
| **M25** | Image Aspect Ratio Distortion | ImageLoader.kt:97 | UI/UX | ⏳ VERIFY |
| **M26** | Fire and Forget Asset Copy | ContentDatabase.kt:62 | Threading/ANR | ⏳ VERIFY |
| **M27** | Ghost Context Leak | ImageLoader.kt:135 | Memory/Lifecycle | ⏳ VERIFY |
| **M23** | Hardcoded UI Delay | EpisodeListScraper.kt:320 | Performance | ✅ ALREADY FIXED |

### LOW PRIORITY (2 issues)

| # | Issue | File | Type | Status |
|---|-------|------|------|--------|
| **L28** | Regex Object Churn | VideoUrlScraper.kt, EpisodeListScraper.kt | Code Quality | ⏳ VERIFY |
| **L29** | Hardcoded Source Logic | Various (url.contains) | Architecture | ⏳ VERIFY |
| **L30** | Hardcoded English Strings | Various error messages | Localization | ⏳ VERIFY |

---

## Configured Agent Teams

### PHASE 1: Critical + High Priority (Issues C9, H5-H10)

**Team Composition:**

#### Agent 1: `debugger`
- **Role:** Verify and fix exception crashes, null safety issues
- **Assigned Issues:** C9, H11, H12 (3 issues)
- **Responsibilities:**
  - C9: Verify if context.deleteDatabase() crashes with open DB
  - H11: Verify if raw user input breaks FTS MATCH operator
  - H12: Verify if pagination uses subList() or SQL OFFSET
  - Implement fixes with null checks and safe patterns
  - Run compilation tests after each fix

#### Agent 2: `performance-engineer`
- **Role:** Identify and optimize performance bottlenecks
- **Assigned Issues:** H5, H13 (2 issues)
- **Responsibilities:**
  - H5: Verify regex performance on 15MB strings
  - H13: Verify if JavaScript truncation at 10KB line exists
  - Implement fixes with performance tests
  - Verify memory usage improvements

#### Agent 3: `backend-architect`
- **Role:** Design robust data flow and error handling
- **Assigned Issues:** H14, H15, M19 (3 issues)
- **Responsibilities:**
  - H14: Verify if single try/catch aborts all content
  - H15: Verify if date parsing is too strict
  - M19: Verify if multiple workers trigger race conditions
  - Design supervisorScope alternatives
  - Implement flexible date parsing

#### Agent 4: `python-pro`
- **Role:** Analyze and optimize Python scraper scripts
- **Assigned Issues:** H16, H17, H18 (3 issues)
- **Responsibilities:**
  - H16: Verify if DB connection created in loop
  - H17: Verify if metadata fields hardcoded NULL
  - H18: Verify User-Agent mismatch
  - Rewrite Python code with best practices
  - Sync with Android code

---

### PHASE 2: Medium Priority (Issues M20-M27)

**Team Composition:**

#### Agent 1: `code-reviewer`
- **Role:** Audit code for security and quality issues
- **Assigned Issues:** M20, M21, M22 (3 issues)
- **Responsibilities:**
  - M20: Verify HTML stripping leaves <script> content
  - M21: Verify if hashCode() used for episode IDs
  - M22: Verify if "1080" matches "1080 Hours"
  - Recommend security-hardened implementations

#### Agent 2: `frontend-developer`
- **Role:** Fix UI/UX and image loading issues
- **Assigned Issues:** M25, M27 (2 issues)
- **Responsibilities:**
  - M25: Verify Scale.FILL stretches images
  - M27: Verify if Activity context captured
  - Implement proper image scaling and lifecycle management

#### Agent 3: `database-optimizer`
- **Role:** Optimize database reliability and performance
- **Assigned Issues:** M24, M26 (2 issues)
- **Responsibilities:**
  - M24: Verify if migration uses relative path
  - M26: Verify if asset copy on main thread
  - Implement absolute paths and background threading

#### Agent 4: `architecture-reviewer` (or code-reviewer)
- **Role:** Audit code quality and maintainability
- **Assigned Issues:** L28, L29, L30 (3 LOW priority)
- **Responsibilities:**
  - L28: Verify regex object churn in loops
  - L29: Verify hardcoded source checks
  - L30: Verify hardcoded English strings
  - Recommend refactoring patterns

---

## Execution Sequence

### Phase 1: Parallel Execution (CRITICAL + HIGH)

```
Start (Day 1-2):
├─ debugger (C9, H11, H12) ─────────────┐
├─ performance-engineer (H5, H13) ──────┤
├─ backend-architect (H14, H15, M19) ───┤ Parallel
├─ python-pro (H16, H17, H18) ──────────┤
└─ SYNC POINT: Verify all Phase 1 fixes compile
   └─ Run: ./gradlew compileDebugKotlin
   └─ Aggregate results
   └─ Update audit documentation
```

**Expected Duration:** 6-8 hours (wall clock 1-2 days if parallel)

### Phase 2: Parallel Execution (MEDIUM + LOW)

```
Start (Day 3):
├─ code-reviewer (M20, M21, M22, L28-L30) ────┐
├─ frontend-developer (M25, M27) ──────────────┤ Parallel
├─ database-optimizer (M24, M26) ───────────────┤
└─ SYNC POINT: Verify Phase 2 fixes compile
   └─ Run: ./gradlew compileDebugKotlin
   └─ Run: ./gradlew test
   └─ Update documentation
```

**Expected Duration:** 5-6 hours

### Phase 3: Integration & Validation

```
Final (Day 4):
├─ Run full build: ./gradlew build
├─ Run all tests: ./gradlew test
├─ Verify no regressions
├─ Update CLAUDE.md audit status
├─ Create final remediation summary
└─ Prepare for commit/deployment
```

---

## Verification Protocol

**For each issue, agents must:**

1. **Verify Issue Exists** (code inspection)
   - Read the exact file/lines mentioned
   - Confirm the problem pattern exists
   - Mark as TRUE/FALSE/ALREADY_FIXED

2. **If Verified TRUE:**
   - Implement minimal fix
   - Add safety checks
   - Run `./gradlew compileDebugKotlin`
   - Test compilation passes

3. **If Verified FALSE or ALREADY_FIXED:**
   - Document evidence
   - Skip fix
   - Move to next issue

4. **Report Results:**
   - File path and exact lines changed
   - Before/after code comparison
   - Compilation status
   - Any side effects or risks

---

## Quality Checkpoints

### Checkpoint 1: Phase 1 Completion
- [ ] All 6 Phase 1 issues verified
- [ ] All confirmed issues fixed
- [ ] Compilation successful
- [ ] No new warnings
- [ ] CLAUDE.md status updated

### Checkpoint 2: Phase 2 Completion
- [ ] All 11 Phase 2 issues verified
- [ ] All confirmed issues fixed
- [ ] Compilation successful
- [ ] All tests pass
- [ ] No regressions detected

### Checkpoint 3: Final Integration
- [ ] Full build successful
- [ ] All tests passing
- [ ] CLAUDE.md reflects new status
- [ ] Audit documentation updated
- [ ] Ready for production commit

---

## Success Criteria

**Definition of Done:**
1. All 20 issues verified (TRUE/FALSE/ALREADY_FIXED)
2. All TRUE issues fixed with minimal, focused changes
3. Build compiles without errors or warnings
4. All tests pass
5. No regressions introduced
6. Documentation updated to reflect current status

**Metrics:**
- Issues fixed: 20/20 (100%)
- Compilation success: ✅
- Test success: ✅
- Lines of code changed: < 500 total
- Commits created: 4-6 focused commits

---

## Risk Mitigation

**Risks & Mitigations:**

| Risk | Likelihood | Mitigation |
|------|-----------|-----------|
| Issue is FALSE positive | Medium | Verify FIRST with code inspection |
| Fix introduces regression | Medium | Run full test suite after each phase |
| Multiple agents conflict | Low | Clear issue assignments, different files |
| Build breaks mid-phase | Low | Compile after each agent completes |
| Python changes break app | Medium | Sync User-Agent with Android code |

---

## Agent Selection Rationale

### Why These Agents?

- **debugger:** 3+ issues with crash/exception patterns
- **performance-engineer:** 2 issues requiring optimization analysis
- **backend-architect:** 3 issues requiring error handling redesign
- **python-pro:** 3 issues in Python scraper (different language)
- **code-reviewer:** 6 issues requiring security/quality audit
- **frontend-developer:** 2 issues specific to UI/image handling
- **database-optimizer:** 2 issues specific to database operations

### Why Parallel Execution?

- Issues are in different files/languages
- No cross-dependencies between agents
- User has ADHD - prefers clear delegation (not sequential waiting)
- Reduces total wall-clock time from 20 hours to 6-8 hours

---

## Files to Monitor

**Critical files that may change:**
```
G:\FarsiPlex\
├── app/src/main/java/com/example/farsilandtv/
│   ├── data/database/ContentDatabase.kt (C9, M26)
│   ├── data/database/ContentDao.kt (H11, H12)
│   ├── data/repository/ContentRepository.kt (H15, M20)
│   ├── data/repository/MainViewModel.kt (H14, M19)
│   ├── data/scraper/VideoUrlScraper.kt (H5, H13, L28)
│   ├── data/scraper/EpisodeListScraper.kt (M21, L28, L29)
│   ├── ui/ImageLoader.kt (M25, M27)
│   ├── data/database/AppDatabase.kt (M24)
│   └── ...other files
├── app/src/main/python/
│   ├── farsiplex_scraper_dooplay.py (H16, H17, H18)
│   ├── generate_content_database.py (H17)
│   └── ...
└── build.gradle.kts (if dependencies change)
```

---

## Next Steps

1. **Approve Team Composition** - Confirm agents and issue assignments
2. **Authorize Phase 1 Execution** - Delegate to agents
3. **Monitor Progress** - Collect verification reports
4. **Sync at Checkpoints** - Validate and continue
5. **Deploy** - Commit all fixes and update documentation

---

## Timeline Estimate

- **Phase 1:** 6-8 hours (1-2 days if parallel)
- **Phase 2:** 5-6 hours (1 day if parallel)
- **Phase 3:** 2-3 hours
- **Total:** 15-20 hours effective work (3-4 days calendar time)

---

**Status:** ⏳ Awaiting approval to dispatch agents

**Questions?** Ask before delegating to ensure clarity.
