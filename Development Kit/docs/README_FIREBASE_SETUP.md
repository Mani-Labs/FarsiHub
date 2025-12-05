# Firebase Cloud Messaging Setup Guide

**Application:** FarsiPlex Android TV (Rebranding to FarsiHub)
**Feature:** Push Notifications for New Episodes & Seasons
**Status:** ⚠️ CURRENTLY DISABLED - PLACEHOLDER CONFIG
**Audit Note:** Firebase integration exists but is disabled (no google-services.json)

---

## Current Status

The application has a **placeholder Firebase configuration** that will not work in production:

- **File:** `app/google-services.json`
- **Status:** Contains dummy API keys and project IDs
- **Impact:** Push notifications (Feature #9) are non-functional

---

## Why Firebase is Used

Firebase Cloud Messaging (FCM) powers the notification system:

### Features Enabled:
1. **New Episode Notifications** - Alert users when new episodes are available
2. **New Season Notifications** - Notify about new seasons of favorite series
3. **Weekly Digest** - Summary of new content each week
4. **Notification Preferences** - User control over notification types
5. **Quiet Hours** - Respect user's do-not-disturb settings

### Code Implementation:
- **Service:** `FarsilandMessagingService.kt` (330+ lines)
- **Dependencies:** Firebase BOM 32.7.0, FCM, Analytics
- **Manifest:** Service registered with proper intent filters

---

## Option 1: Configure Firebase (Recommended)

### Prerequisites
- Google account (personal Gmail account works fine)
- 10 minutes of setup time
- Internet connection

### Step-by-Step Setup

#### 1. Create Firebase Project

1. **Go to Firebase Console:**
   - Visit: https://console.firebase.google.com/
   - Sign in with your Google account

2. **Create New Project:**
   - Click "Add project"
   - **Project Name:** `FarsilandTV` (or any name you prefer)
   - **Google Analytics:** You can disable it for simplicity
   - Click "Create project"
   - Wait for project creation (30-60 seconds)

#### 2. Register Android App

1. **Add Android App to Project:**
   - In Firebase Console, click "Add app" → Android icon
   - **Package Name:** `com.example.farsilandtv` (MUST match exactly)
   - **App Nickname:** FarsilandTV (optional)
   - **Debug signing certificate:** Leave blank for now
   - Click "Register app"

2. **Download Configuration File:**
   - Click "Download google-services.json"
   - **IMPORTANT:** Save this file securely

3. **Replace Placeholder Config:**
   ```bash
   # Backup the placeholder config (optional)
   cd G:\FarsiPlex\app
   copy google-services.json google-services.json.placeholder

   # Copy the downloaded file to app/ directory
   # Replace: G:\FarsiPlex\app\google-services.json
   ```

#### 3. Enable Cloud Messaging

1. **In Firebase Console:**
   - Go to Project Settings → Cloud Messaging tab
   - Note the **Server Key** (you'll need this for backend notifications)
   - Note the **Sender ID**

2. **No Additional Configuration Needed:**
   - Firebase is already integrated in the app
   - Dependencies are already in build.gradle.kts
   - Service is already registered in AndroidManifest.xml

#### 4. Build and Test

1. **Clean and Rebuild:**
   ```bash
   cd G:\FarsiPlex
   gradlew clean
   gradlew assembleDebug
   ```

2. **Install on Android TV Device:**
   ```bash
   adb install app/build/outputs/apk/debug/app-debug.apk
   ```

3. **Check Logs for Firebase Token:**
   ```bash
   adb logcat | findstr /i "firebase"
   ```

   **Expected Output:**
   ```
   D/FirebaseApp: Firebase initialized successfully
   D/FarsilandMessaging: New FCM token: [long token string]
   ```

4. **Test Notification from Firebase Console:**
   - In Firebase Console → Cloud Messaging → Send test message
   - Enter the FCM token from logs
   - Send a test notification
   - Should appear on your Android TV device

#### 5. Backend Integration (Optional)

To send notifications from your backend server:

**HTTP API Request:**
```bash
curl -X POST https://fcm.googleapis.com/fcm/send \
  -H "Authorization: key=[YOUR_SERVER_KEY]" \
  -H "Content-Type: application/json" \
  -d '{
    "to": "[DEVICE_FCM_TOKEN]",
    "data": {
      "type": "new_episode",
      "series_title": "Breaking Bad",
      "episode_title": "Pilot",
      "content_id": "12345"
    }
  }'
```

**Supported Notification Types:**
- `new_episode` - New episode available
- `new_season` - New season released
- `weekly_digest` - Weekly content summary

**Required Fields:**
- `type` - Notification type
- `series_title` - Show name
- `content_id` - For deep linking

**Optional Fields:**
- `episode_title` - Episode name (for new_episode)
- `season_number` - Season number (for new_season)
- `thumbnail_url` - Image URL for notification

---

## Option 2: Remove Firebase (Alternative)

If you don't need push notifications, remove Firebase to reduce APK size and complexity.

### Benefits of Removal:
- **Smaller APK:** ~2-3 MB reduction
- **No Configuration Required:** No need for Firebase account
- **Faster Build Times:** Fewer dependencies
- **Privacy:** No Google services tracking

### Steps to Remove Firebase

#### 1. Remove Gradle Plugin

**File:** `app/build.gradle.kts`

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.kotlin.compose)
    // DELETE this line:
    // id("com.google.gms.google-services")
}
```

#### 2. Remove Dependencies

**File:** `app/build.gradle.kts`

```kotlin
dependencies {
    // ... other dependencies ...

    // DELETE these lines:
    // implementation(platform("com.google.firebase:firebase-bom:32.7.0"))
    // implementation("com.google.firebase:firebase-messaging-ktx")
    // implementation("com.google.firebase:firebase-analytics-ktx")
}
```

#### 3. Remove Root Plugin

**File:** `build.gradle.kts` (root project)

```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.kapt) apply false
    // DELETE this line:
    // id("com.google.gms.google-services") version "4.4.0" apply false
}
```

#### 4. Delete Firebase Files

```bash
cd G:\FarsiPlex

# Remove Firebase service
del app\src\main\java\com\example\farsilandtv\services\FarsilandMessagingService.kt

# Remove Firebase config
del app\google-services.json
```

#### 5. Update AndroidManifest.xml

**File:** `app/src/main/AndroidManifest.xml`

```xml
<!-- DELETE these permissions: -->
<!-- <uses-permission android:name="android.permission.POST_NOTIFICATIONS" /> -->
<!-- <uses-permission android:name="android.permission.WAKE_LOCK" /> -->

<application ...>
    <!-- ... other activities ... -->

    <!-- DELETE this entire service block: -->
    <!--
    <service
        android:name=".services.FarsilandMessagingService"
        android:exported="false">
        <intent-filter>
            <action android:name="com.google.firebase.MESSAGING_EVENT" />
        </intent-filter>
    </service>
    -->

    <!-- DELETE these metadata entries: -->
    <!--
    <meta-data
        android:name="com.google.firebase.messaging.default_notification_icon"
        android:resource="@drawable/ic_notification" />
    <meta-data
        android:name="com.google.firebase.messaging.default_notification_color"
        android:resource="@color/notification_color" />
    <meta-data
        android:name="com.google.firebase.messaging.default_notification_channel_id"
        android:value="@string/default_notification_channel_id" />
    -->
</application>
```

#### 6. Remove ProGuard Rules

**File:** `app/proguard-rules.pro`

```proguard
# DELETE the entire Firebase section:
# ==================== FIREBASE CLOUD MESSAGING ====================
# ... (remove all Firebase rules) ...
```

#### 7. Clean and Rebuild

```bash
cd G:\FarsiPlex
gradlew clean
gradlew assembleDebug
```

#### 8. Verify Build

```bash
# Check APK size reduction
dir app\build\outputs\apk\debug\app-debug.apk

# Install and test
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

---

## Comparison Table

| Feature | With Firebase | Without Firebase |
|---------|---------------|------------------|
| **APK Size** | ~25-30 MB | ~22-27 MB |
| **Push Notifications** | ✅ Yes | ❌ No |
| **New Episode Alerts** | ✅ Yes | ❌ No |
| **Weekly Digest** | ✅ Yes | ❌ No |
| **Analytics** | ✅ Yes | ❌ No |
| **Setup Complexity** | Medium | Low |
| **Privacy** | Google Services | Fully Private |
| **Build Time** | Slower | Faster |
| **Dependencies** | More | Fewer |

---

## Security Considerations

### If Using Firebase:

1. **Protect Server Key:**
   - NEVER commit Firebase Server Key to version control
   - Store in environment variables or secret management system
   - Restrict key to your backend server IPs only

2. **Validate Notification Payload:**
   - The app already validates notification types
   - Additional validation in `FarsilandMessagingService.kt`

3. **User Privacy:**
   - FCM tokens are device-specific, not user-specific
   - Implement opt-out mechanism (already in app)
   - Respect quiet hours (already implemented)

### If Removing Firebase:

1. **Alternative Notification Methods:**
   - In-app notifications (when user opens app)
   - Email notifications (if you have user emails)
   - RSS feeds for new content

---

## Troubleshooting

### Issue: Build Fails with "google-services.json not found"

**Solution:**
- Ensure `google-services.json` is in `app/` directory (not `app/src/`)
- File must be named exactly `google-services.json`
- Check file permissions (should be readable)

### Issue: Firebase Initialization Failed

**Check:**
1. Package name matches: `com.example.farsilandtv`
2. File is valid JSON (not corrupted)
3. File contains real API key (not placeholder)

**Debug:**
```bash
adb logcat | findstr /i "firebase error"
```

### Issue: No FCM Token Generated

**Possible Causes:**
- Device not connected to internet
- Google Play Services not installed (Android TV emulators)
- Firebase project disabled

**Test:**
```bash
# Check Google Play Services
adb shell dumpsys package com.google.android.gms | findstr /i "version"
```

### Issue: Notifications Not Appearing

**Check:**
1. Device notification permissions enabled
2. App notification channels enabled
3. Quiet hours not active
4. Notification type enabled in preferences

**Debug:**
```bash
adb logcat -s FarsilandMessaging
```

---

## Additional Resources

### Official Documentation:
- **Firebase Setup:** https://firebase.google.com/docs/android/setup
- **FCM Integration:** https://firebase.google.com/docs/cloud-messaging/android/client
- **FCM HTTP API:** https://firebase.google.com/docs/cloud-messaging/http-server-ref

### App-Specific Files:
- **Service Implementation:** `app/src/main/java/.../services/FarsilandMessagingService.kt`
- **Notification Helper:** `app/src/main/java/.../utils/NotificationHelper.kt`
- **Database DAO:** `app/src/main/java/.../data/dao/NotificationPreferencesDao.kt`

### Testing Tools:
- **Firebase Console Test Messaging:** https://console.firebase.google.com/
- **FCM CLI Tool:** https://github.com/firebase/firebase-tools

---

## Decision Matrix

### Choose Firebase (Option 1) if:
- ✅ You want to send push notifications to users
- ✅ You have or can create a Google account
- ✅ You're okay with ~2-3 MB larger APK
- ✅ You want user engagement features
- ✅ You plan to implement backend notification system

### Remove Firebase (Option 2) if:
- ✅ Push notifications are not needed
- ✅ You want smallest possible APK size
- ✅ You prioritize privacy and no Google tracking
- ✅ You want simpler build configuration
- ✅ Users can manually check for new content

---

## Next Steps

### If Choosing Option 1 (Firebase):
1. [ ] Create Firebase project
2. [ ] Download google-services.json
3. [ ] Replace placeholder config
4. [ ] Build and test app
5. [ ] Send test notification
6. [ ] Integrate with backend (optional)

### If Choosing Option 2 (Remove Firebase):
1. [ ] Remove Gradle plugins
2. [ ] Remove dependencies
3. [ ] Delete Firebase files
4. [ ] Update AndroidManifest.xml
5. [ ] Clean ProGuard rules
6. [ ] Build and test app

---

**Last Updated:** 2025-11-09
**Maintained By:** FarsiPlex Development Team
