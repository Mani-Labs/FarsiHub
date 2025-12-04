# FarsiPlex: Universal Video Aggregator Plan

## Overview

Evolve FarsiPlex from a single-source app into a **Universal Video Aggregator** that pulls Persian content from multiple platforms into one unified interface.

**Status:** Planning/Investigation
**Date:** 2024-12-02

---

## Table of Contents

1. [Current State](#current-state)
2. [Vision: Multi-Platform Aggregation](#vision-multi-platform-aggregation)
3. [Platform Analysis](#platform-analysis)
4. [Core Systems](#core-systems)
   - [Deduplication Engine](#1-deduplication-engine)
   - [Default Sources & Channel Directory](#2-default-sources--channel-directory)
   - [Smart Source Picker (Auto-Pick Best)](#3-smart-source-picker-auto-pick-best)
   - [Direct URL Sources](#4-direct-url-sources)
   - [Telegram Integration](#5-telegram-integration)
5. [Database Schema](#database-schema)
6. [Architecture Diagram](#architecture-diagram)
7. [Implementation Phases](#implementation-phases)
8. [Open Questions](#open-questions)

---

## Current State

FarsiPlex currently supports 3 content sources:

| Source | Type | Method | Direct URL? |
|--------|------|--------|-------------|
| Farsiland | Website | WordPress API + Scraping | Yes |
| FarsiPlex | Website | Sitemap + Scraping | Yes |
| Namakade | Website | HTML Scraping | Yes |

**Limitations:**
- Sources are hardcoded
- No deduplication (same movie from different sources shown separately)
- Can't add new sources without code changes
- Missing metadata (cast, director) for many items

---

## Vision: Multi-Platform Aggregation

```
┌─────────────────────────────────────────────────────────────────┐
│              FARSIPLEX UNIVERSAL AGGREGATOR                      │
│                                                                 │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐          │
│  │   SCRAPERS   │  │  PLATFORM    │  │   USER       │          │
│  │              │  │  APIS        │  │   ADDED      │          │
│  │ • Farsiland  │  │ • YouTube    │  │ • Custom URL │          │
│  │ • FarsiPlex  │  │ • Aparat     │  │ • Telegram   │          │
│  │ • Namakade   │  │ • Vimeo      │  │   Channels   │          │
│  └──────────────┘  └──────────────┘  └──────────────┘          │
│          ↓                ↓                 ↓                   │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │              CONTENT UNIFICATION ENGINE                  │   │
│  │  • Deduplication (same movie, multiple sources)         │   │
│  │  • Metadata merging (best info from each source)        │   │
│  │  • Quality ranking (prefer 1080p > 720p)                │   │
│  │  • Health monitoring (fallback if source dies)          │   │
│  └─────────────────────────────────────────────────────────┘   │
│                              ↓                                  │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │                    UNIFIED LIBRARY                       │   │
│  │  • 10,000+ Movies                                       │   │
│  │  • 500+ Series                                          │   │
│  │  • Multiple quality options per title                   │   │
│  │  • Auto-fallback playback                               │   │
│  └─────────────────────────────────────────────────────────┘   │
│                              ↓                                  │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │                   SMART PLAYBACK                         │   │
│  │  • Pick best available source                           │   │
│  │  • Seamless quality switching                           │   │
│  │  • Cross-platform resume                                │   │
│  │  • Offline cache from direct sources                    │   │
│  └─────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────┘
```

---

## Platform Analysis

### Available Platforms for Persian Content

| Platform | Content Type | API? | Direct URL? | Offline? | Notes |
|----------|--------------|------|-------------|----------|-------|
| **YouTube** | Full movies, clips | Yes (quota) | No | No | Huge library, ads |
| **Aparat** | Full movies, series | Yes | No | No | Iranian YouTube |
| **Telegram** | Full movies | Bot API | Yes! | Yes | High quality, no ads |
| **Farsiland** | Full movies | Scraping | Yes | Yes | Existing source |
| **FarsiPlex** | Full movies | Scraping | Yes | Yes | Existing source |
| **Namakade** | Full movies | Scraping | Yes | Yes | Existing source |
| **Dailymotion** | Some content | Yes | No | No | Limited |
| **Vimeo** | Indie films | Yes | No | No | Limited |

### YouTube Channel Example: TopPersianMovies

**Channel:** https://www.youtube.com/@TopPersianMovies
- 563K subscribers
- 8,600+ videos
- Verified channel
- Daily uploads

**Video Description Structure:**
```
📖 Plot summary in Farsi

🎞 General Info:
  کارگردان (Director): Name
  نویسنده (Writer): Name

⭐ Cast:
  Actor1, Actor2, Actor3...

#hashtags for genres/actors

📖 English plot summary

Related video links
```

**Playlists Available:**
- 🆕 New Persian Movies (820 videos)
- ⚠️ Mystery/Crime Movies (124 videos)
- Comedy, Classic, etc.

---

## Core Systems

### 1. Deduplication Engine

**Problem:** Same movie exists on multiple platforms with different titles:
```
YouTube:     "فیلم سینمایی متری شیش و نیم با بازی پیمان معادی | 1080p"
Farsiland:   "متری شیش و نیم"
Telegram:    "🎬 متری شیش و نیم 🎬 کیفیت عالی"
```

#### Title Normalization

```kotlin
object TitleNormalizer {

    fun normalize(title: String): String {
        return title
            // Remove common prefixes
            .replace(Regex("^(فیلم سینمایی|فیلم|دانلود فیلم|سریال)\\s*"), "")
            // Remove quality tags
            .replace(Regex("(1080p|720p|480p|HD|کیفیت عالی|بدون سانسور)"), "")
            // Remove emojis
            .replace(Regex("[\\uD83C-\\uDBFF\\uDC00-\\uDFFF]+"), "")
            // Remove actor names after "با بازی"
            .replace(Regex("با بازی.*$"), "")
            // Remove channel branding
            .replace(Regex("\\|.*$"), "")
            // Normalize whitespace
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
```

#### Fuzzy Matching

```kotlin
object FuzzyMatcher {

    fun similarity(a: String, b: String): Float {
        val normalized1 = TitleNormalizer.normalize(a)
        val normalized2 = TitleNormalizer.normalize(b)

        val distance = levenshteinDistance(normalized1, normalized2)
        val maxLen = maxOf(normalized1.length, normalized2.length)

        return 1f - (distance.toFloat() / maxLen)
    }

    fun isSameContent(a: String, b: String): Boolean {
        return similarity(a, b) > 0.85f
    }
}
```

#### Multi-Signal Fingerprinting

```kotlin
data class ContentFingerprint(
    val normalizedTitle: String,
    val year: Int?,
    val durationRange: DurationRange,
    val director: String?,
    val leadActor: String?
)

enum class DurationRange {
    SHORT,      // < 30 min (clips, trailers)
    MEDIUM,     // 30-60 min (TV episodes)
    FEATURE,    // 60-150 min (movies)
    LONG        // > 150 min (extended cuts)
}

object ContentMatcher {

    fun match(a: ContentFingerprint, b: ContentFingerprint): MatchResult {
        var score = 0f

        // Title similarity (50% weight)
        score += FuzzyMatcher.similarity(a.normalizedTitle, b.normalizedTitle) * 0.5f

        // Year match (20% weight)
        if (a.year != null && b.year != null) {
            if (a.year == b.year) score += 0.2f
            else if (abs(a.year - b.year) == 1) score += 0.1f
        }

        // Duration range (15% weight)
        if (a.durationRange == b.durationRange) score += 0.15f

        // Director match (10% weight)
        if (a.director != null && b.director != null) {
            if (FuzzyMatcher.similarity(a.director, b.director) > 0.8f) score += 0.1f
        }

        // Actor match (5% weight)
        if (a.leadActor != null && b.leadActor != null) {
            if (FuzzyMatcher.similarity(a.leadActor, b.leadActor) > 0.8f) score += 0.05f
        }

        return when {
            score > 0.85f -> MatchResult.DEFINITE_MATCH
            score > 0.70f -> MatchResult.PROBABLE_MATCH
            score > 0.50f -> MatchResult.POSSIBLE_MATCH
            else -> MatchResult.NO_MATCH
        }
    }
}
```

#### Deduplication Pipeline

```
New content arrives (e.g., from YouTube sync)
                    ↓
┌─────────────────────────────────────────┐
│         FINGERPRINT EXTRACTION          │
│  • Normalize title                      │
│  • Extract year from title/description  │
│  • Classify duration                    │
│  • Parse director/actors                │
└─────────────────────────────────────────┘
                    ↓
┌─────────────────────────────────────────┐
│          CANDIDATE SEARCH               │
│  • Search existing UnifiedContent       │
│  • By normalized title (fuzzy)          │
│  • Filter by year ±1                    │
│  • Filter by duration range             │
└─────────────────────────────────────────┘
                    ↓
┌─────────────────────────────────────────┐
│          MATCH SCORING                  │
│  • Score each candidate                 │
│  • DEFINITE_MATCH (>0.85): Link it      │
│  • PROBABLE_MATCH (>0.70): Link + flag  │
│  • NO_MATCH: Create new UnifiedContent  │
└─────────────────────────────────────────┘
                    ↓
┌─────────────────────────────────────────┐
│          METADATA MERGE                 │
│  • Update UnifiedContent with best data │
│  • Prefer: More complete > More recent  │
│  • Keep all sources linked              │
└─────────────────────────────────────────┘
```

---

### 2. Default Sources & Channel Directory

#### Curated Channel Data Structure

```kotlin
data class ChannelDirectory(
    val version: Int,
    val lastUpdated: Long,
    val channels: List<CuratedChannel>
)

data class CuratedChannel(
    val platform: Platform,
    val channelId: String,
    val handle: String?,
    val name: String,
    val description: String,
    val thumbnailUrl: String,
    val category: ChannelCategory,
    val contentType: ContentType,
    val language: String,
    val isVerified: Boolean,
    val qualityRating: Int,              // 1-5 stars
    val updateFrequency: UpdateFrequency,
    val estimatedVideoCount: Int,
    val recommendedMinDuration: Int,
    val isEnabledByDefault: Boolean,
    val tags: List<String>
)

enum class ChannelCategory {
    MOVIES_GENERAL,
    MOVIES_CLASSIC,
    MOVIES_NEW,
    SERIES,
    DOCUMENTARY,
    KIDS,
    MIXED
}

enum class UpdateFrequency { DAILY, WEEKLY, MONTHLY, RARELY }
```

#### Default Channels (Ship with App)

```kotlin
val DEFAULT_CHANNELS = listOf(
    // YouTube Channels
    CuratedChannel(
        platform = Platform.YOUTUBE,
        channelId = "UCxelJIiHJoGR270g8FqtfJA",
        handle = "@TopPersianMovies",
        name = "TPM - Top Persian Movies",
        description = "بهترین فیلم‌های سینمایی ایرانی",
        category = ChannelCategory.MOVIES_GENERAL,
        qualityRating = 5,
        updateFrequency = UpdateFrequency.DAILY,
        estimatedVideoCount = 8600,
        recommendedMinDuration = 40,
        isEnabledByDefault = true,
        tags = listOf("new-releases", "classic", "popular")
    ),

    // More YouTube channels...

    // Telegram Channels
    CuratedChannel(
        platform = Platform.TELEGRAM,
        channelId = "Persian_Movies_HD",
        name = "Persian Movies HD",
        category = ChannelCategory.MOVIES_GENERAL,
        qualityRating = 4,
        isEnabledByDefault = false,  // Opt-in
        // ...
    )
)
```

#### Remote Directory Updates

Host on GitHub or Firebase for easy updates without app release:

```json
{
  "version": 12,
  "lastUpdated": "2024-12-01T00:00:00Z",
  "channels": [
    {
      "platform": "YOUTUBE",
      "channelId": "UCxelJIiHJoGR270g8FqtfJA",
      "handle": "@TopPersianMovies",
      "name": "TPM - Top Persian Movies",
      "isEnabledByDefault": true,
      "qualityRating": 5,
      "status": "active"
    }
  ],
  "removedChannels": [
    { "channelId": "UCold...", "reason": "Channel deleted" }
  ]
}
```

#### First Launch Experience

```
┌─────────────────────────────────────────────────────┐
│           Welcome to FarsiPlex!                     │
│                                                     │
│  Select content sources to enable:                  │
│                                                     │
│  RECOMMENDED (Auto-enabled)                         │
│  ──────────────────────────                         │
│  ☑️ TPM - Top Persian Movies     ⭐⭐⭐⭐⭐          │
│     8,600 videos • YouTube                          │
│                                                     │
│  ☑️ Farsiland                     ⭐⭐⭐⭐⭐          │
│     15,000+ movies • Website                        │
│                                                     │
│  OPTIONAL                                           │
│  ────────                                           │
│  ☐ Persian Movies HD              ⭐⭐⭐⭐           │
│     Telegram channel                                │
│                                                     │
│  You can change this anytime in Settings            │
│                                                     │
│              [Get Started →]                        │
└─────────────────────────────────────────────────────┘
```

---

### 3. Smart Source Picker (Auto-Pick Best)

#### Priority Scoring Algorithm

```kotlin
object SourcePicker {

    fun pickBest(
        sources: List<ContentSource>,
        userPrefs: UserPreferences,
        networkState: NetworkState
    ): ContentSource? {

        if (sources.isEmpty()) return null

        val scored = sources.map { source ->
            var score = 0f

            // 1. User explicit preference (highest priority)
            if (source.isUserPreferred) score += 100f

            // 2. Health status
            when (source.status) {
                SourceStatus.ACTIVE -> score += 50f
                SourceStatus.UNKNOWN -> score += 25f
                SourceStatus.UNAVAILABLE -> score -= 100f
                SourceStatus.DELETED -> return@map -999f
                SourceStatus.BLOCKED -> score -= 50f
            }

            // 3. Quality preference
            val qualityScore = when (source.quality) {
                VideoQuality.Q2160P -> 40f
                VideoQuality.Q1080P -> 35f
                VideoQuality.Q720P -> 25f
                VideoQuality.Q480P -> 15f
                VideoQuality.Q360P -> 5f
                VideoQuality.UNKNOWN -> 10f
            }

            // Adjust for network
            val adjustedQuality = when (networkState) {
                NetworkState.WIFI -> qualityScore
                NetworkState.MOBILE_FAST -> qualityScore * 0.9f
                NetworkState.MOBILE_SLOW -> (40f - qualityScore)
                NetworkState.OFFLINE -> if (source.supportsOffline) 100f else -999f
            }
            score += adjustedQuality

            // 4. Direct URL availability
            if (source.hasDirectUrl) score += 20f

            // 5. Offline support
            if (source.supportsOffline && userPrefs.prefersOfflineCapable) {
                score += 15f
            }

            // 6. Platform preference
            score += userPrefs.platformPriority[source.platform] ?: 0

            // 7. Subtitles
            if (source.hasSubtitles && userPrefs.prefersSubtitles) {
                score += 10f
            }

            // 8. Recency of verification
            val hoursSinceVerified = (System.currentTimeMillis() - source.lastVerified) / 3600000
            if (hoursSinceVerified < 24) score += 5f
            else if (hoursSinceVerified > 168) score -= 5f

            // 9. Failure history
            score -= source.failCount * 10f

            score
        }

        return sources.zip(scored)
            .filter { it.second > 0 }
            .maxByOrNull { it.second }
            ?.first
    }
}
```

#### Auto-Fallback Playback

```kotlin
class SmartPlayer {

    suspend fun play(content: UnifiedContent): PlaybackResult {
        val sources = contentRepository.getSourcesForContent(content.contentId)
        val rankedSources = sources.sortedByDescending { sourcePicker.scoreSource(it) }

        for (source in rankedSources) {
            try {
                val result = attemptPlayback(source)
                if (result.success) {
                    sourceHealthChecker.markSuccess(source)
                    return result
                }
            } catch (e: PlaybackException) {
                sourceHealthChecker.markFailure(source, e.message)
                continue
            }
        }

        return PlaybackResult.AllSourcesFailed(triedCount = rankedSources.size)
    }

    private suspend fun attemptPlayback(source: ContentSource): PlaybackResult {
        return when (source.platform) {
            Platform.YOUTUBE -> playYouTube(source.platformId)
            Platform.APARAT -> playAparat(source.platformId)
            Platform.TELEGRAM -> playTelegram(source.platformId)
            else -> {
                val directUrl = videoUrlScraper.getUrl(source.platformUrl)
                playDirect(directUrl)
            }
        }
    }
}
```

#### User Override UI

```
┌─────────────────────────────────────────────────────┐
│  متری شیش و نیم                                     │
│                                                     │
│  [▶ Play]  ← Auto-picks best source                │
│                                                     │
│  Choose Source:                                     │
│  ┌──────────────────────────────────────────────┐  │
│  │ ★ YouTube 1080p         [▶] [Set Default]    │  │
│  │   TPM Channel • Verified 2h ago              │  │
│  │                                               │  │
│  │   Farsiland 720p        [▶]                  │  │
│  │   Direct stream • Can download               │  │
│  │                                               │  │
│  │   Telegram 1080p        [▶]                  │  │
│  │   Direct stream • Can download               │  │
│  └──────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────┘
```

---

### 4. Direct URL Sources

#### Why Direct URLs Matter

| Feature | Direct URL | Embedded (YT/Aparat) |
|---------|-----------|----------------------|
| Offline download | ✅ Yes | ❌ No |
| ExoPlayer control | ✅ Full | ❌ Limited |
| No ads | ✅ Yes | ❌ Has ads |
| Speed control | ✅ Custom | ⚠️ Platform dependent |
| Background play | ✅ Yes | ❌ Usually no |
| Chromecast | ✅ Full control | ⚠️ Dependent |
| Resume position | ✅ Precise | ⚠️ May reset |

#### Platform Capabilities

```kotlin
enum class Platform(
    val hasDirectUrl: Boolean,
    val supportsOffline: Boolean,
    val playerType: PlayerType
) {
    FARSILAND(true, true, PlayerType.EXOPLAYER),
    FARSIPLEX(true, true, PlayerType.EXOPLAYER),
    NAMAKADE(true, true, PlayerType.EXOPLAYER),
    TELEGRAM(true, true, PlayerType.EXOPLAYER),
    YOUTUBE(false, false, PlayerType.YOUTUBE_EMBED),
    APARAT(false, false, PlayerType.WEBVIEW)
}
```

#### Download Priority Logic

```kotlin
class DownloadManager {

    fun canDownload(content: UnifiedContent): Boolean {
        return content.sources.any { it.hasDirectUrl && it.supportsOffline }
    }

    suspend fun download(content: UnifiedContent): DownloadResult {
        val downloadableSources = content.sources
            .filter { it.hasDirectUrl && it.supportsOffline }
            .sortedByDescending { it.quality.ordinal }

        if (downloadableSources.isEmpty()) {
            return DownloadResult.NotAvailableOffline(
                reason = "Only available on YouTube/Aparat (cannot download)"
            )
        }

        for (source in downloadableSources) {
            try {
                val urlResult = extractDirectUrl(source)
                if (urlResult is DirectUrlResult.Success) {
                    return startDownload(content, urlResult.urls.first())
                }
            } catch (e: Exception) {
                continue
            }
        }

        return DownloadResult.Failed("All download sources failed")
    }
}
```

---

### 5. Telegram Integration

#### Why Telegram is Valuable

- **Tons of Persian movies** shared freely
- **Direct file URLs** - no scraping complexity
- **High quality** - often 1080p MKV/MP4
- **No ads**
- **No geo-blocking**
- **Works offline** once downloaded

#### How Telegram Channels Work

```
Channel: @Persian_Movies_HD

Message 1: 🎬 متری شیش و نیم 🎬
           کیفیت: 1080p
           حجم: 1.5GB
           [Video File Attached]

Message 2: 🎬 ابد و یک روز 🎬
           [Video File Attached]
```

#### Integration Architecture

```
┌─────────────────────────────────────────────────────────┐
│                  TELEGRAM INTEGRATION                    │
│                                                         │
│  User adds channel    Telegram Bot/Client               │
│  @MoviesCH       ───▶ • Fetches channel messages        │
│                       • Extracts video files            │
│                       • Parses captions for metadata    │
│                                   │                     │
│                                   ▼                     │
│  Caption: "🎬 متری شیش و نیم 🎬\n1080p | 1.5GB"         │
│                      ↓                                  │
│  Parsed:                                                │
│    title: "متری شیش و نیم"                              │
│    quality: 1080p                                       │
│    fileSize: 1.5GB                                      │
│    fileId: "BAACAgQAAxk..."                             │
│                      ↓                                  │
│  Playback:                                              │
│    fileId → Telegram API → Direct CDN URL               │
│    https://api.telegram.org/file/bot.../video.mp4       │
│    ExoPlayer streams directly!                          │
└─────────────────────────────────────────────────────────┘
```

#### Telegram Client Options

**Option A: Bot API (Simpler)**
```kotlin
class TelegramBotClient(private val botToken: String) {
    private val baseUrl = "https://api.telegram.org/bot$botToken"

    suspend fun getChannelMessages(channelUsername: String): List<TelegramMessage> {
        // Bot must be admin in channel
    }

    suspend fun getFileUrl(fileId: String): String {
        val response = httpClient.get("$baseUrl/getFile?file_id=$fileId")
        val filePath = response.result.file_path
        return "https://api.telegram.org/file/bot$botToken/$filePath"
    }
}
```

**Option B: TDLib (More Powerful)**
- Full Telegram client
- Can access any public channel
- Requires phone number auth (one-time)
- More complex setup

**Option C: MTProto Direct (Most Powerful)**
- Direct protocol implementation
- No restrictions
- Most complex

#### Telegram Data Model

```kotlin
@Entity(tableName = "telegram_channels")
data class TelegramChannel(
    @PrimaryKey val channelId: Long,
    val username: String,
    val title: String,
    val photoUrl: String?,
    val memberCount: Int?,
    val isEnabled: Boolean = true,
    val lastSyncMessageId: Int = 0,
    val lastSyncTime: Long = 0
)

@Entity(tableName = "telegram_videos")
data class TelegramVideo(
    @PrimaryKey val messageId: Int,
    val channelId: Long,
    val fileId: String,
    val fileUniqueId: String,
    val fileSize: Long,
    val duration: Int,
    val width: Int?,
    val height: Int?,
    val mimeType: String?,
    val caption: String?,
    val parsedTitle: String?,
    val parsedQuality: String?,
    val parsedYear: Int?,
    val thumbnailFileId: String?,
    val messageDate: Long,
    val syncedAt: Long
)
```

#### Caption Parser

```kotlin
object TelegramCaptionParser {

    private val titlePatterns = listOf(
        Regex("🎬\\s*(.+?)\\s*🎬"),
        Regex("فیلم[:\\s]+(.+?)(?:\\n|$)"),
        Regex("^(.+?)(?:\\n|\\|)")
    )

    private val qualityPattern = Regex("(2160p|1080p|720p|480p|4K|HD)")
    private val sizePattern = Regex("(\\d+(?:\\.\\d+)?\\s*(?:GB|MB|گیگ|مگ))")
    private val yearPattern = Regex("\\b(1[3-4]\\d{2}|19\\d{2}|20[0-2]\\d)\\b")

    fun parse(caption: String?): ParsedCaption {
        if (caption == null) return ParsedCaption()

        return ParsedCaption(
            title = extractTitle(caption),
            quality = qualityPattern.find(caption)?.value,
            fileSize = sizePattern.find(caption)?.value,
            year = yearPattern.find(caption)?.value?.toIntOrNull()
        )
    }
}
```

#### Telegram Playback

```kotlin
class TelegramPlayer(
    private val telegramClient: TelegramBotClient,
    private val exoPlayerFactory: ExoPlayerFactory
) {

    suspend fun play(video: TelegramVideo): ExoPlayer {
        // Get fresh direct URL (expires after ~1 hour)
        val fileUrl = telegramClient.getFileUrl(video.fileId)

        val player = exoPlayerFactory.create()
        val mediaItem = MediaItem.Builder()
            .setUri(fileUrl)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(video.parsedTitle)
                    .build()
            )
            .build()

        player.setMediaItem(mediaItem)
        player.prepare()

        return player
    }
}
```

---

## Database Schema

### Unified Content Tables

```kotlin
// Master content record (deduplicated)
@Entity(tableName = "unified_content")
data class UnifiedContent(
    @PrimaryKey val contentId: String,
    val canonicalTitle: String,
    val canonicalTitleEn: String?,
    val year: Int?,
    val contentType: ContentType,
    val director: String?,
    val cast: String?,                    // JSON array
    val genres: String?,                  // JSON array
    val plotFarsi: String?,
    val plotEnglish: String?,
    val posterUrl: String?,
    val backdropUrl: String?,
    val rating: Float?,
    val durationSeconds: Long?,
    val sourceCount: Int,
    val bestQuality: String?,
    val hasSubtitles: Boolean,
    val createdAt: Long,
    val updatedAt: Long
)

// Individual sources linked to unified content
@Entity(tableName = "content_sources")
data class ContentSource(
    @PrimaryKey val sourceId: String,
    val contentId: String,               // FK to UnifiedContent
    val platform: Platform,
    val platformId: String,
    val platformUrl: String,
    val channelId: String?,
    val channelName: String?,
    val originalTitle: String,
    val thumbnailUrl: String?,
    val quality: VideoQuality,
    val durationSeconds: Long?,
    val hasDirectUrl: Boolean,
    val supportsOffline: Boolean,
    val hasSubtitles: Boolean,
    val status: SourceStatus,
    val lastVerified: Long,
    val failCount: Int,
    val priority: Int,
    val isUserPreferred: Boolean
)

// YouTube/Aparat channels
@Entity(tableName = "video_channels")
data class VideoChannel(
    @PrimaryKey val channelId: String,
    val platform: Platform,
    val channelHandle: String?,
    val channelName: String,
    val channelThumbnail: String?,
    val subscriberCount: Long?,
    val videoCount: Long?,
    val isEnabled: Boolean,
    val customLabel: String?,
    val contentFilter: String,
    val minDurationMinutes: Int,
    val lastSyncTime: Long,
    val syncStatus: String,
    val addedAt: Long
)

// Telegram channels
@Entity(tableName = "telegram_channels")
data class TelegramChannel(
    @PrimaryKey val channelId: Long,
    val username: String,
    val title: String,
    val photoUrl: String?,
    val isEnabled: Boolean,
    val lastSyncMessageId: Int,
    val lastSyncTime: Long
)

enum class Platform { YOUTUBE, APARAT, FARSILAND, FARSIPLEX, NAMAKADE, TELEGRAM }
enum class VideoQuality { Q2160P, Q1080P, Q720P, Q480P, Q360P, UNKNOWN }
enum class SourceStatus { ACTIVE, UNAVAILABLE, DELETED, BLOCKED, UNKNOWN }
enum class ContentType { MOVIE, SERIES, EPISODE, DOCUMENTARY, CLIP, TRAILER, UNKNOWN }
```

---

## Architecture Diagram

```
┌──────────────────────────────────────────────────────────────────┐
│                        FARSIPLEX v2.0                            │
│                                                                  │
│  ┌────────────────────────────────────────────────────────────┐ │
│  │                    SOURCE LAYER                             │ │
│  │                                                             │ │
│  │  Scrapers          Platform APIs       User-Added          │ │
│  │  ─────────         ─────────────       ──────────          │ │
│  │  • Farsiland       • YouTube           • Custom URLs       │ │
│  │  • FarsiPlex       • Aparat            • Telegram channels │ │
│  │  • Namakade        • (future)          • YouTube channels  │ │
│  └────────────────────────────────────────────────────────────┘ │
│                              ↓                                   │
│  ┌────────────────────────────────────────────────────────────┐ │
│  │                 DEDUPLICATION ENGINE                        │ │
│  │                                                             │ │
│  │  • Title normalization & fuzzy matching                    │ │
│  │  • Multi-signal fingerprinting (year, duration, director)  │ │
│  │  • Merge metadata from best sources                        │ │
│  └────────────────────────────────────────────────────────────┘ │
│                              ↓                                   │
│  ┌────────────────────────────────────────────────────────────┐ │
│  │                  UNIFIED CONTENT DB                         │ │
│  │                                                             │ │
│  │  UnifiedContent (1) ←──── (N) ContentSource                │ │
│  │  "متری شیش و نیم"         • YouTube 1080p                  │ │
│  │                            • Farsiland 720p                 │ │
│  │                            • Telegram 1080p                 │ │
│  └────────────────────────────────────────────────────────────┘ │
│                              ↓                                   │
│  ┌────────────────────────────────────────────────────────────┐ │
│  │                  SMART SOURCE PICKER                        │ │
│  │                                                             │ │
│  │  Priority: Quality → Health → Direct URL → User Pref       │ │
│  │  Network-aware: Wifi=best quality, Mobile=adaptive         │ │
│  │  Auto-fallback: Try next source if current fails           │ │
│  └────────────────────────────────────────────────────────────┘ │
│                              ↓                                   │
│  ┌────────────────────────────────────────────────────────────┐ │
│  │                   PLAYBACK LAYER                            │ │
│  │                                                             │ │
│  │  Direct URLs          Embedded Players                     │ │
│  │  ────────────         ────────────────                     │ │
│  │  • ExoPlayer          • YouTube IFrame                     │ │
│  │  • Full control       • Aparat WebView                     │ │
│  │  • Offline capable    • Platform controls                  │ │
│  └────────────────────────────────────────────────────────────┘ │
└──────────────────────────────────────────────────────────────────┘
```

---

## Implementation Phases

### Phase 1: Foundation
- [ ] Create unified content database schema
- [ ] Implement ContentSource entity
- [ ] Create deduplication engine (title normalizer, fuzzy matcher)
- [ ] Migrate existing content to unified schema

### Phase 2: YouTube Integration
- [ ] Add YouTube channel entity
- [ ] Implement RSS feed parser (free, no API key)
- [ ] Create YouTube description parser
- [ ] Add YouTube player (android-youtube-player library)
- [ ] UI: Add "YouTube" section or mixed content

### Phase 3: Smart Source Picker
- [ ] Implement priority scoring algorithm
- [ ] Add network-aware quality selection
- [ ] Create auto-fallback playback system
- [ ] UI: Source selection dialog

### Phase 4: Channel Management
- [ ] Create channel directory system
- [ ] First-launch channel selection UI
- [ ] Settings: Manage channels screen
- [ ] Remote directory updates

### Phase 5: Telegram Integration
- [ ] Set up Telegram Bot
- [ ] Implement caption parser
- [ ] Add Telegram channel sync
- [ ] Telegram playback with ExoPlayer

### Phase 6: Polish
- [ ] Health monitoring system
- [ ] Download prioritization (direct URL sources)
- [ ] Source badges on UI
- [ ] Offline mode handling

---

## Open Questions

1. **YouTube API Key Management**
   - Use RSS (limited to 15 recent) or full API?
   - If API: Who manages quota? Bundled key or user provides?

2. **Telegram Bot Setup**
   - Create dedicated bot for FarsiPlex?
   - How to handle bot token securely?

3. **Deduplication Confidence**
   - What to do with PROBABLE_MATCH (70-85% confidence)?
   - Manual review queue? Auto-merge?

4. **UI Design**
   - Separate sections per platform or fully mixed?
   - How prominent should source badges be?

5. **Default Channels**
   - How many to ship with?
   - All enabled by default or opt-in?

6. **Offline Strategy**
   - Show YouTube-only content when offline?
   - Or hide and show "requires internet" message?

7. **Legal Considerations**
   - YouTube ToS compliance (embedding vs. extracting)
   - Telegram content legality

---

## References

- YouTube Channel: https://www.youtube.com/@TopPersianMovies
- YouTube Data API: https://developers.google.com/youtube/v3
- android-youtube-player: https://github.com/PierfrancescoSofworthy/android-youtube-player
- Telegram Bot API: https://core.telegram.org/bots/api
- TDLib: https://core.telegram.org/tdlib
