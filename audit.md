🛡️ Comprehensive FarsiHub Codebase Audit Report

Auditor: Gemini (Coding Partner)
Date: November 22, 2025
Target: FarsiHub (FarsilandTV) Android Application & Scraper Infrastructure
Version Reviewed: c8f911b1e0eb2bf9ad39a0fe66a57112cb4e5640

📊 Executive Summary

The FarsiHub codebase exhibits a mix of modern Android architecture (Jetpack Compose, Room, Paging 3, Coroutines) and legacy/fragile components, particularly in its data ingestion pipeline. While the core application structure is sound, critical flaws in the scraping scripts, database synchronization logic, and resource management pose significant risks to stability, data integrity, and user experience.

Key Findings:

Critical: Offline database functionality is broken due to schema mismatches.

Critical: High risk of app crashes (OOM) on low-end TV devices due to inefficient data processing.

Critical: High risk of server IP bans due to aggressive scraping behavior.

Critical: Data loss scenarios identified in synchronization and watchlist management.

Major: Inefficient pagination and regex operations threaten UI performance.

🚨 Critical Severity Issues

Issues causing immediate crashes, complete feature failure, or permanent data loss.

1. 💥 Database Schema Mismatch (Offline DB Failure)

Location: farsiplex_scraper_dooplay.py vs ContentDao.kt / AppDatabase.kt

Issue: The Python scraper generates a SQLite database with tables named movies and tvshows. However, the Android application's Room entities are mapped to tables named cached_movies and cached_series.

Impact: The application will crash immediately upon attempting to access the offline database with a SQLiteException: no such table: cached_movies error. The "Offline Database" feature is non-functional.

Remediation: Rename the tables in the Python script to match the Android Entity definitions exactly (cached_movies, cached_series, cached_episodes).

2. 💣 Out-of-Memory (OOM) Crash Risk (Aggregated)

Location: VideoUrlScraper.kt (Lines 448, 459, 492, 597)

Issue: The scraper reads up to 15MB of response data into memory per request (maxBytes = 15L * 1024 * 1024). It runs up to 5 concurrent requests. Furthermore, buffer.readUtf8() duplicates this data into a new String.

Impact: This creates a sudden memory spike of ~300MB (5 threads * ~60MB allocation). On Android TV devices with 1GB/2GB RAM, this will trigger an immediate OutOfMemoryError or severe ANR (Application Not Responding).

Remediation: Reduce maxBytes to 2-4MB or implement a streaming JSON parser (JsonReader) to process data without loading the entire payload into memory.

3. 🤖 Server IP Ban Risk (DoS Behavior)

Location: VideoUrlScraper.kt (Lines 1742-1780: extractFromDownloadForms)

Issue: The scraper identifies all available download links (potentially 10+) and immediately launches simultaneous async POST requests to the /get/ endpoint for all of them.

Impact: This aggressive burst of traffic mimics a DDoS attack or bot behavior. Security mechanisms (Cloudflare, Wordfence) on the target server will likely flag and ban the user's IP address.

Remediation: Serialize the requests or use a Semaphore to limit concurrency. Process download links sequentially or in small batches.

4. 📉 Silent Data Loss: The "Page 1" Sync Trap

Location: ContentSyncWorker.kt (Lines 428-453, 463-488)

Issue: The syncMovies and syncSeries functions fetch only the first page of results (perPage = 20) and then return. There is no logic to handle pagination or fetch subsequent pages.

Impact: If more than 20 items are updated on the server between sync intervals, only the latest 20 are retrieved. The remaining items are permanently skipped as the sync timestamp is updated, leading to data inconsistency.

Remediation: Implement a do-while loop to continue fetching pages until the API returns fewer items than perPage.

5. 🗑️ Catastrophic Watchlist Wipe ("Ghost" Killer)

Location: ContentSyncWorker.kt (Line 372: cleanupGhostRecords)

Issue: The worker deletes watchlist items if they are not found in the current Content Database.

Impact: If a user switches to a new database source that is empty, corrupted, or fails to load, the sync worker will view all valid watchlist items as "ghosts" and delete the entire user watchlist.

Remediation: Add a safety guard to prevent cleanupGhostRecords from running if the Content Database is empty or has a very low item count.

6. 🐍 Python Scraper Crash (IndexError)

Location: farsiplex_scraper_dooplay.py (Line 546)

Issue: The script accesses seasons[-1] without verifying if the seasons list is non-empty.

Impact: If a TV show entry exists but has no seasons (e.g., "Coming Soon" or parsing error), the script will crash with an IndexError, terminating the entire database generation process.

Remediation: Add a check if not seasons: continue before accessing the list.

7. ⏳ Infinite Hang Risk (Python Script)

Location: farsiplex_scraper_dooplay.py (Lines 128, 176, 255, 440)

Issue: requests.get() and requests.post() calls are made without a timeout parameter.

Impact: If the server hangs or "tarpits" the connection, the script will wait indefinitely, freezing automated build pipelines or manual updates.

Remediation: Add a global TIMEOUT constant (e.g., 30 seconds) to all network requests.

8. 🚫 Stale Episode Cache (Missing Updates)

Location: ContentRepository.kt (getEpisodes)

Issue: The logic returns cached episodes immediately and stops further execution if any data is found. It does not trigger a background update check.

Impact: Once a show is cached, new episodes released on the server will never appear in the app unless the user manually clears data.

Remediation: Modify getEpisodes to return cached data and launch a background coroutine to fetch and save the latest episodes from the web.

9. 💣 "File-In-Use" Crash (Database Swapping)

Location: ContentDatabase.kt (Line 300)

Issue: The code attempts to delete the database file via context.deleteDatabase while it might still be open by Room/SQLite (due to active LiveData/Flow observers).

Impact: Deleting an open file on Linux/Android leads to undefined behavior or SQLiteDiskIOException, causing the app to crash on subsequent database access attempts.

Remediation: Implement a safe swapping mechanism: copy the new DB to a temp file, use renameTo (atomic), and trigger an app restart or ensure all DB connections are closed.

🟠 High Severity Issues

Significant performance degradation, broken features, or security vulnerabilities.

10. 🐢 Regex Performance on Large Inputs (ANR Risk)

Location: VideoUrlScraper.kt (Line 638)

Issue: Complex regex operations are performed on potentially large (15MB) strings on threads that may block UI rendering indirectly.

Impact: Massive string processing causes CPU spikes, leading to UI stuttering or Application Not Responding (ANR) errors on lower-end hardware.

Remediation: Truncate strings to a reasonable length before applying regex, or use streaming parsing methods.

11. 🔍 FTS Query Syntax Crash

Location: ContentDao.kt (Lines 82, 192, 246)

Issue: Raw user input is passed directly to the FTS4 MATCH operator.

Impact: Search queries containing special characters (", *, -) will cause a SQLiteException, breaking the search functionality for those inputs.

Remediation: Sanitize user input by wrapping it in quotes and escaping special characters before passing it to the database query.

12. 🐌 "Offset by Drop" Pagination (Memory Leak)

Location: ContentRepository.kt (Line 285)

Issue: Pagination is implemented by fetching all previous items plus the new page from the database and discarding the unneeded ones in memory (subList).

Impact: Memory usage and query time grow linearly with page depth. Scrolling to later pages becomes exponentially slower and causes significant garbage collection churn.

Remediation: Use SQL OFFSET and LIMIT clauses in the DAO (already implemented as getRecentMoviesWithOffset) for efficient database-level pagination.

13. ✂️ Broken Scrapers (Over-Aggressive Truncation)

Location: VideoUrlScraper.kt (Line 1548)

Issue: JavaScript content is truncated to 10,000 characters to prevent ReDoS attacks.

Impact: Video URLs located after the 10,000th character in large inline scripts or bundles will be missed, breaking the scraper for those sites.

Remediation: Increase the limit (e.g., to 100KB) or use a sliding window search approach instead of regex on the entire string.

14. 💥 "All-or-Nothing" Content Loading

Location: MainViewModel.kt (Line 128)

Issue: The loading logic awaits multiple async jobs (Movies, Series) within a single try/catch block.

Impact: If a single API call fails, the entire block aborts, and the user sees an error screen even if other content loaded successfully.

Remediation: Use supervisorScope or individual error handling for each content type to allow partial UI rendering.

15. 📅 Strict Date Parsing Failure

Location: ContentRepository.kt (Line 1366)

Issue: Instant.parse() fails on date strings that do not strictly adhere to ISO-8601 format (e.g., missing 'Z' or using space instead of 'T'), which are common in WordPress responses.

Impact: Items with non-standard date formats will fail to parse, resulting in a timestamp of 0. This breaks content sorting, causing new items to appear at the bottom of lists.

Remediation: Implement a flexible date parser that handles common variations before attempting Instant.parse.

16. 📉 Database Thrashing (Python Script)

Location: farsiplex_scraper_dooplay.py (Line 317)

Issue: A new SQLite connection is opened and closed for every single item being saved.

Impact: This causes massive I/O overhead due to repeated file open/close operations and journal file creation, significantly slowing down the scraping process.

Remediation: Reuse a single database connection for the entire batch or session.

17. 🧩 Metadata Black Hole (Python Script)

Location: generate_content_database.py (Lines 108-110)

Issue: The script hardcodes NULL for rich metadata fields like Runtime, Director, and Cast.

Impact: The Android app's UI will show empty fields for offline content, degrading the user experience compared to the online version.

Remediation: Update the script to extract these fields from the source data if available.

🟡 Medium Severity Issues

Logic flaws, UX issues, and maintenance risks.

18. 📱 User-Agent Mismatch

Location: farsiplex_scraper_dooplay.py vs RetrofitClient.kt

Issue: The Python script uses a Chrome User-Agent, while the Android app likely uses the default OkHttp User-Agent.

Impact: The app may be blocked by server-side security rules (WAFs) that allow the script, leading to inconsistent behavior.

Remediation: Ensure RetrofitClient is configured with the exact same User-Agent string as the Python script.

19. 🏁 Auto-Refresh Race Condition

Location: MainViewModel.kt (Lines 87, 145)

Issue: Multiple sync workers finishing in close succession trigger conflicting cache-clear and fetch jobs.

Impact: Redundant network requests, potential UI flickering, and race conditions in data display.

Remediation: Debounce refresh requests or cancel pending refresh jobs before starting a new one.

20. 🧹 Naive HTML Stripping (Script Injection)

Location: ContentRepository.kt (Line 1435)

Issue: The regex <[^>]+> removes tags but leaves the content of script tags visible.

Impact: Users may see raw JavaScript code or style definitions in movie descriptions.

Remediation: Use Html.fromHtml() or a more sophisticated stripper that removes script/style content.

21. 🆔 Hash Collision in Episode IDs

Location: EpisodeListScraper.kt (Line 218)

Issue: String.hashCode() is used to generate integer IDs for episodes.

Impact: In a large library, hash collisions are statistically possible, which could cause DiffUtil issues in RecyclerViews (wrong items updating/displaying).

Remediation: Use Long IDs constructed from unique components (e.g., (seriesId << 32) | ((season << 16) + episode)).

22. 📉 Weak Quality Detection

Location: VideoUrlScraper.kt (Line 1588)

Issue: Simple string containment (contains("1080")) is used to detect video quality.

Impact: Titles or file paths containing "1080" (e.g., "1080 Hours") could be misidentified as 1080p video quality.

Remediation: Use a more specific regex that looks for "1080p" or similar patterns with delimiters.

23. ⏳ Hardcoded UI Delay

Location: EpisodeListScraper.kt (Line 320)

Issue: A delay(500) is hardcoded into the fetchHtml function.

Impact: Introduces an artificial lag to every scraping interaction, making the app feel sluggish.

Remediation: Remove the delay for user-initiated actions; rate limiting should be handled at the network client level for batch operations only.

24. 🏗️ Migration Path Fragility

Location: AppDatabase.kt (Line 380)

Issue: ATTACH DATABASE uses a relative path (farsiland_database.db).

Impact: This may fail on devices with multi-user profiles (Guest/Work) where the relative path resolution is ambiguous.

Remediation: Use the absolute path to the database file.

25. 📸 Image Aspect Ratio Distortion

Location: ImageLoader.kt (Line 97)

Issue: Scale.FILL is used, which stretches images to fill bounds regardless of aspect ratio.

Impact: Movie posters or banners may appear distorted (squashed or stretched) if the ImageView dimensions do not match the image aspect ratio.

Remediation: Use Scale.FIT or android:scaleType="centerCrop"/"fitCenter" on the ImageView.

26. ⚡ "Fire and Forget" Asset Copy

Location: ContentDatabase.kt (Line 62)

Issue: Database copying from assets happens on the calling thread.

Impact: If called from the main thread (even lazily), copying a large database file will cause a UI freeze (ANR) on first launch.

Remediation: Ensure the initial database access happens on a background thread (e.g., Dispatchers.IO).

27. 👻 Ghost Context Leak

Location: ImageLoader.kt (Line 135)

Issue: preloadAdjacentImages captures the provided Context.

Impact: If an Activity context is passed and the coroutine outlives the Activity, it causes a memory leak.

Remediation: Always use context.applicationContext within the utility to ensure safety.

🟢 Low Severity / Code Hygiene

28. Regex Object Churn: Recompiling regexes inside loops (VideoUrlScraper.kt, EpisodeListScraper.kt) wastes CPU. Move to constants.

29. Hardcoded Source Logic: if (url.contains("namakade")) violates clean architecture. Use a Strategy pattern.

30. Hardcoded English Strings: User-facing error messages ("Security: Only HTTPS...", "No video URLs found...") are not localized, preventing Farsi translation.

🏁 Conclusion

The FarsiHub application has a solid architectural foundation but is critically compromised by flaws in its offline data strategy, scraping implementation, and resource management. Addressing the Critical and High severity issues—specifically the schema mismatch, OOM risks, and IP ban vectors—is mandatory before any release.