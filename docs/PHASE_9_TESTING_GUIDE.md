# Phase 9 Testing - Quick Start Guide

Simple guide for testing Phase 9 Medium Priority fixes.

---

## What Was Added

**4 New Test Files:**
1. `SecureUrlValidatorTest.kt` - HTTPS security (35 tests)
2. `NetworkMonitoringTest.kt` - Network monitoring (22 tests)
3. `MoviesFragmentLifecycleTest.kt` - Fragment lifecycle (15 tests)
4. `IndexPerformanceTest.kt` - Database performance (11 tests)

**Total: 83 new automated tests**

---

## Running Tests

### Quick Test (5 minutes)
```bash
.\gradlew.bat testDebugUnitTest
```
Runs all unit tests (SecureUrlValidator, NetworkMonitoring, Lifecycle)

### Full Test (30+ minutes)
```bash
.\gradlew.bat testDebugUnitTest connectedDebugAndroidTest
```
Includes database performance tests (needs device/emulator connected)

### Specific Test File
```bash
# Security tests
.\gradlew.bat testDebugUnitTest --tests SecureUrlValidatorTest

# Network tests
.\gradlew.bat testDebugUnitTest --tests NetworkMonitoringTest

# Lifecycle tests
.\gradlew.bat testDebugUnitTest --tests MoviesFragmentLifecycleTest

# Database tests (needs device)
.\gradlew.bat connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.farsilandtv.data.database.IndexPerformanceTest
```

---

## What Each Test Checks

### 1. SecureUrlValidatorTest (M9: HTTPS Security)
**What it tests:**
- HTTPS URLs pass ✅
- HTTP URLs blocked ❌
- Only trusted domains allowed (farsiland.com, flnd.buzz, etc.)
- HTTP auto-upgraded to HTTPS (when safe)

**Why it matters:** Prevents man-in-the-middle attacks

### 2. NetworkMonitoringTest (M6: Network Monitoring)
**What it tests:**
- Video pauses when WiFi drops
- User sees "Network lost" message
- User sees "Network restored" message
- No crashes from network changes

**Why it matters:** Better user experience during network issues

### 3. MoviesFragmentLifecycleTest (M2: Lifecycle)
**What it tests:**
- Loading stops when you navigate away
- No memory leaks from background tasks
- Battery not drained by unnecessary work

**Why it matters:** App doesn't waste battery/memory

### 4. IndexPerformanceTest (M7: Database Speed)
**What it tests:**
- Watchlist loads in < 100ms (even with 1000 items)
- Episode list loads in < 50ms
- Database scales to 10,000+ items

**Why it matters:** Instant UI, no lag

---

## Manual Testing Needed

Some Phase 9 fixes need manual testing (not automated):

### M1: Back Button Behavior
1. Open movie details
2. Press back button 5 times rapidly
3. **Expected:** App exits cleanly (no duplicate activities)

### M3: Text Messages
1. Trigger exit prompt (back button twice)
2. Toggle WiFi during video playback
3. **Expected:** See proper messages (not hard-coded text)

### M4: Crash Reporting
1. Add this to `onCreate()`: `throw RuntimeException("Test crash")`
2. Run app, let it crash
3. Wait 5 minutes
4. **Expected:** Crash appears in Firebase Console

### M5: Video Buffering
1. Play video on Nvidia Shield TV
2. Watch for 30+ minutes
3. **Expected:** No stuttering, no out-of-memory crashes

### M8: Push Notifications
1. Send test notification from Firebase Console
2. **Expected:** No crashes, message logged in Crashlytics

---

## Expected Results

### All Tests Should Pass
```
SecureUrlValidatorTest: 35/35 ✅
NetworkMonitoringTest: 22/22 ✅
MoviesFragmentLifecycleTest: 15/15 ✅
IndexPerformanceTest: 11/11 ✅

Total: 83/83 PASS
```

### If Tests Fail
1. Check error message in console
2. Look for file/line number in stack trace
3. Common issues:
   - Missing test dependency → Run `.\gradlew.bat build`
   - Device not connected → Connect Nvidia Shield for IndexPerformanceTest
   - Network issue → Check internet connection for tests

---

## Quick Verification

**After running tests, verify:**
- [x] All 83 automated tests pass
- [x] Manual tests (M1, M3, M4, M5, M8) completed
- [x] No console errors/warnings
- [x] Build successful

**Then:** Phase 9 testing complete! ✅

---

## Files Created

**Test Files:**
- `app/src/test/java/com/example/farsilandtv/utils/SecureUrlValidatorTest.kt`
- `app/src/test/java/com/example/farsilandtv/NetworkMonitoringTest.kt`
- `app/src/test/java/com/example/farsilandtv/ui/fragment/MoviesFragmentLifecycleTest.kt`
- `app/src/androidTest/java/com/example/farsilandtv/data/database/IndexPerformanceTest.kt`

**Documentation:**
- `docs/PHASE_9_TEST_SUITE_SUMMARY.md` (detailed report)
- `PHASE_9_TESTING_GUIDE.md` (this file - quick reference)

**Build Config:**
- `app/build.gradle.kts` (added Robolectric + lifecycle-testing dependencies)

---

## Need Help?

**Common Commands:**
```bash
# Clean build
.\gradlew.bat clean

# Rebuild everything
.\gradlew.bat build

# Check syntax only (fast)
.\gradlew.bat compileDebugKotlin

# Full test suite
.\gradlew.bat testDebugUnitTest connectedDebugAndroidTest
```

**Test Reports:**
- Unit tests: `app/build/reports/tests/testDebugUnitTest/index.html`
- Android tests: `app/build/reports/androidTests/connected/index.html`

---

**Remember:** Tests ensure Phase 9 fixes work correctly and prevent future bugs!
