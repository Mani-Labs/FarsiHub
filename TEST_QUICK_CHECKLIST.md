# FarsiPlex Test Suite - Quick Action Checklist

## IMMEDIATE ACTIONS (This Week)

### Critical Fixes
- [ ] Remove `assertTrue(true)` from PlaybackRepositoryTest.kt (9 instances, lines 102, 191, 204, 266, etc.)
- [ ] Remove `assertTrue(true)` from WatchlistRepositoryTest.kt (7 instances, lines 108, 134, 153, etc.)
- [ ] Remove `Thread.sleep(100)` from NetworkMonitoringTest.kt (line 345)
- [ ] Fix `Mockito.mockStatic(AppDatabase::class.java)` in PlaybackRepositoryTest.kt (lines 67-76)

### File Updates
- [ ] Move PlaybackRepositoryTest.kt to `app/src/androidTest/` directory
- [ ] Update import paths after moving to AndroidTest
- [ ] Add `@RunWith(AndroidJUnit4::class)` annotation

### Testing
- [ ] Run `./gradlew test` locally - should pass
- [ ] Run `./gradlew connectedAndroidTest` - should pass
- [ ] Check flaky test rate - should drop from 35% to ~5%

---

## WEEK 2-3 (New Tests)

### Create Missing Test Files
- [ ] **FavoritesRepositoryTest.kt** (25 tests)
  - [ ] Add to favorites
  - [ ] Remove from favorites
  - [ ] Get all favorites
  - [ ] Check if favorited
  - [ ] Bulk operations

- [ ] **SearchRepositoryTest.kt** (20 tests)
  - [ ] Movie search
  - [ ] Series search
  - [ ] Search history
  - [ ] FTS4 performance
  - [ ] Unicode/special chars

- [ ] **NotificationPreferencesRepositoryTest.kt** (15 tests)
  - [ ] Get preferences
  - [ ] Update preferences
  - [ ] Default values
  - [ ] Flow-based queries

### Database Tests
- [ ] Add concurrency tests to PlaybackPositionDaoTest
- [ ] Add transaction tests to WatchlistDaoTest
- [ ] Add cascade delete tests

---

## WEEK 4-5 (Integration Tests)

### Create Integration Test Files
- [ ] **VideoUrlScraperIntegrationTest.kt** (15 tests)
  - [ ] Farsiland scraping
  - [ ] FarsiPlex scraping
  - [ ] Multi-source fallback
  - [ ] LRU cache behavior
  - [ ] TTL expiration
  - [ ] Error handling

- [ ] **DownloadManagerIntegrationTest.kt** (20 tests)
  - [ ] Start download
  - [ ] Pause download
  - [ ] Resume download
  - [ ] Cancel download
  - [ ] Progress tracking
  - [ ] Disk space checks

### Scraper Integration Tests
- [ ] Test with MockWebServer
- [ ] Test cache eviction (100 entries)
- [ ] Test 5-minute TTL
- [ ] Test ReDoS protection

---

## WEEK 6-7 (E2E Tests)

### Create E2E Test Suite
- [ ] **SearchAndPlayE2ETest.kt**
  - [ ] Search for content
  - [ ] View results
  - [ ] Open details
  - [ ] Play video
  - [ ] Verify playback started

- [ ] **WatchlistWorkflowE2ETest.kt**
  - [ ] Add to watchlist
  - [ ] Navigate to watchlist
  - [ ] Open content
  - [ ] Resume from saved position
  - [ ] Mark as complete

- [ ] **DownloadWorkflowE2ETest.kt**
  - [ ] Start download
  - [ ] Monitor progress
  - [ ] Complete download
  - [ ] Play offline
  - [ ] Resume playback

- [ ] **NetworkErrorHandlingE2ETest.kt**
  - [ ] Network loss during playback
  - [ ] Auto-pause
  - [ ] Network restore
  - [ ] Resume option shown
  - [ ] Retry logic

---

## Quality Gates (CI/CD)

### Before Merging
- [ ] All tests pass locally
- [ ] `./gradlew test` succeeds
- [ ] `./gradlew connectedAndroidTest` succeeds
- [ ] Coverage report generated
- [ ] No new placeholder assertions
- [ ] No flaky tests (run 3x locally)

### Code Coverage Targets
- [ ] Unit tests: 60%+ (currently 35%)
- [ ] Integration tests: 40%+ (currently 30%)
- [ ] E2E tests: 15+ scenarios (currently 0)
- [ ] Overall: 75%+ (currently 45%)

---

## Test File Status Matrix

| File | Type | Status | Severity | Action |
|------|------|--------|----------|--------|
| SecureUrlValidatorTest.kt | Unit | ✅ Excellent | - | Keep as-is |
| ContentSyncWorkerTest.kt | Unit | ✅ Excellent | - | Keep as-is |
| IndexPerformanceTest.kt | Integration | ✅ Excellent | - | Keep as-is |
| PlaybackPositionDaoTest.kt | Integration | ✅ Excellent | - | Add concurrency |
| WatchlistDaoTest.kt | Integration | ✅ Excellent | - | Add transactions |
| ContentRepositoryTest.kt | Unit | ⚠️ Partial | HIGH | Add repository tests |
| DownloadManagerTest.kt | Unit | ⚠️ Partial | HIGH | Create integration |
| VideoUrlScraperTest.kt | Unit | ⚠️ Partial | HIGH | Create integration |
| PlaybackRepositoryTest.kt | Unit | 🔴 Poor | CRITICAL | Remove placeholders |
| WatchlistRepositoryTest.kt | Unit | 🔴 Poor | CRITICAL | Remove placeholders |
| NetworkMonitoringTest.kt | Unit | 🔴 Poor | CRITICAL | Fix timing |
| FavoritesRepositoryTest.kt | Unit | ❌ Missing | CRITICAL | Create |
| SearchRepositoryTest.kt | Unit | ❌ Missing | CRITICAL | Create |
| NotificationPrefsRepositoryTest.kt | Unit | ❌ Missing | HIGH | Create |
| PlaylistRepositoryTest.kt | Unit | ❌ Missing | HIGH | Create |
| VideoUrlScraperIntegrationTest.kt | Integration | ❌ Missing | HIGH | Create |
| DownloadManagerIntegrationTest.kt | Integration | ❌ Missing | HIGH | Create |
| E2E Test Suite | E2E | ❌ Missing | CRITICAL | Create |

---

## Testing Commands

### Run All Tests
```bash
# Unit tests only
./gradlew test

# Integration tests (requires emulator/device)
./gradlew connectedAndroidTest

# Both
./gradlew test connectedAndroidTest

# With coverage
./gradlew test connectedAndroidTest jacocoTestReport

# Single test file
./gradlew test --tests PlaybackRepositoryTest

# Run until failure (detect flaky tests)
for i in {1..5}; do ./gradlew test --tests NetworkMonitoringTest || break; done
```

### View Coverage
```bash
# Generate HTML report
./gradlew jacocoTestReport

# Open report
open build/reports/jacoco/test/html/index.html
```

---

## Common Issues & Fixes

### Issue: `Mockito.mockStatic()` doesn't work in unit tests
**Error**: `java.lang.IllegalStateException`
**Fix**: Move to AndroidTest with real Room database
**Time**: 1 hour per test file

### Issue: Tests fail intermittently
**Cause**: `Thread.sleep()` for synchronization
**Fix**: Use `CountdownLatch` or coroutine suspension
**Time**: 30 minutes per test

### Issue: Placeholder assertions pass meaninglessly
**Example**: `assertTrue(true, "Test verified")`
**Fix**: Replace with real assertions or delete test
**Time**: 10 minutes per instance

### Issue: Mock database doesn't reflect production
**Cause**: Using `Mockito.mockStatic(AppDatabase.class)`
**Fix**: Use `Room.inMemoryDatabaseBuilder()`
**Time**: 2 hours per test file

---

## Performance Targets

| Operation | Target | Current | Status |
|-----------|--------|---------|--------|
| Unit test suite | < 5s | ~3s | ✅ Good |
| Integration tests | < 30s | ~20s | ✅ Good |
| E2E tests | < 2min | - | ❌ N/A |
| Single test | < 500ms | 200ms avg | ✅ Good |
| DB query | < 100ms | ~50ms | ✅ Good |
| Video scrape | < 1s | varies | ⚠️ Needs test |
| Download start | < 500ms | varies | ⚠️ Needs test |

---

## Success Metrics

### Week 1 (Stabilization)
- [ ] 0 placeholder assertions in production code
- [ ] 0 flaky tests in CI
- [ ] 100% test pass rate
- [ ] Estimated: 2-3 hours work

### Week 2-3 (New Tests)
- [ ] 3 new test files created (60 tests)
- [ ] Coverage: 45% → 60%
- [ ] All tests pass consistently
- [ ] Estimated: 40 hours work

### Week 4-5 (Integration)
- [ ] 2 new integration test files (35 tests)
- [ ] Coverage: 60% → 70%
- [ ] Performance benchmarks established
- [ ] Estimated: 50 hours work

### Week 6-7 (E2E)
- [ ] E2E test suite created (15 workflows)
- [ ] Coverage: 70% → 80%+
- [ ] Critical workflows validated
- [ ] Estimated: 60 hours work

**Total Effort**: ~150-160 hours (4 weeks full-time, 8 weeks part-time)

---

## Tools Required

### Already Have
- ✅ JUnit 4
- ✅ Mockito
- ✅ Turbine (Flow testing)
- ✅ Kotlin Test
- ✅ Robolectric
- ✅ AndroidJUnit4

### Need to Add
- [ ] MockWebServer (for HTTP tests)
- [ ] Espresso/Compose Testing (for E2E)

### Gradle Dependencies
```kotlin
// Add to build.gradle.kts
testImplementation("com.squareup.okhttp3:mockwebserver:4.10.0")
androidTestImplementation("androidx.compose.ui:ui-test-junit4:1.5.0")
androidTestImplementation("androidx.compose.ui:ui-test-manifest:1.5.0")
```

---

## Files to Keep for Reference

1. **TEST_SUITE_CODE_REVIEW.md** (12 KB)
   - Detailed analysis of all 11 test files
   - 100+ specific issues with line numbers
   - Implementation guidance

2. **TEST_IMPLEMENTATION_EXAMPLES.md** (15 KB)
   - Ready-to-copy code for 5 missing test files
   - E2E test example
   - Integration test examples

3. **TEST_REVIEW_EXECUTIVE_SUMMARY.md** (5 KB)
   - High-level overview
   - Quick status matrix
   - 8-week roadmap

4. **TEST_QUICK_CHECKLIST.md** (This file)
   - Action items by week
   - Common issues & fixes
   - Commands to run

---

## Weekly Standup Template

### Each Monday
```
COMPLETED (Last Week):
- [ ] Issue X fixed
- [ ] Tests Y created
- [ ] Coverage: X% → Y%

IN PROGRESS:
- [ ] Creating Z tests
- [ ] Fixing mock setup
- [ ] Performance testing

BLOCKERS:
- [ ] Need design review for E2E approach
- [ ] Waiting for API mocking decision

NEXT WEEK:
- [ ] Complete Y tests
- [ ] Reach X% coverage
- [ ] Fix Z issues
```

---

## Links & References

- Android Testing Docs: https://developer.android.com/training/testing
- Robolectric Guide: https://robolectric.org/
- Espresso Guide: https://developer.android.com/training/testing/espresso
- JUnit Best Practices: https://junit.org/junit4/

---

## Sign-Off

**Report Date**: 2025-12-01
**Reviewed By**: Test Automation Specialist
**Status**: Ready for Implementation
**Confidence Level**: High

**Next Review**: After Week 2 (Critical fixes)

---

**Remember**: Tests are not complete until they validate actual behavior, not just pass with `assertTrue(true)`.

