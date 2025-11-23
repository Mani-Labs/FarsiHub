# FarsiHub Logo Implementation Plan

**Created**: 2025-11-22
**Status**: Phase A Ready | Phase B Pending Compose Migration

---

## Overview

This plan implements user-selectable app logos for FarsiHub using Android's activity-alias pattern. Users can choose between 4 logo designs with immediate visual feedback.

**Logo Options:**
- **Embroidery** - Textured, artistic style
- **Origami** - Geometric, modern fold design
- **Pixel** - Sharp, digital aesthetic (Current Default)
- **Watercolor** - Soft, artistic brushstroke style

**Key Features:**
- Icon changes immediately (no app restart required)
- Live preview in settings UI
- Persisted user preference
- Future-proof Compose TV implementation

---

## Phase A: Immediate Logo Change to Pixel

**Status**: ✅ READY TO EXECUTE
**Effort**: 5 minutes
**Purpose**: Replace current logo with Pixel logo as new default

### Step 1: Prepare Pixel Logo Assets

**Source Files:**
- `G:\FarsiPlex\logo_pixel.png` (from project root)

**Required Mipmap Sizes:**
1. mipmap-mdpi: 48x48px
2. mipmap-hdpi: 72x72px
3. mipmap-xhdpi: 96x96px
4. mipmap-xxhdpi: 144x144px
5. mipmap-xxxhdpi: 192x192px

**Action:**
- Resize `logo_pixel.png` to 6 densities using Android Asset Studio or batch tool
- Compress PNGs to minimize APK size (~100-200KB per icon)

### Step 2: Replace Current Launcher Icons

**Files to Replace:**
```
app/src/main/res/mipmap-mdpi/ic_launcher.png       (48x48)
app/src/main/res/mipmap-hdpi/ic_launcher.png       (72x72)
app/src/main/res/mipmap-xhdpi/ic_launcher.png      (96x96)
app/src/main/res/mipmap-xxhdpi/ic_launcher.png     (144x144)
app/src/main/res/mipmap-xxxhdpi/ic_launcher.png    (192x192)
app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml (if exists - adaptive icon)
```

**Backup Original Icons:**
```bash
# Create backup directory
mkdir -p G:\FarsiPlex\original_logo_backup

# Copy current icons to backup
cp app/src/main/res/mipmap-*/ic_launcher.png original_logo_backup/
```

**Replace with Pixel Logo:**
```bash
# Copy new Pixel logo to all mipmap folders
cp path/to/pixel_48x48.png app/src/main/res/mipmap-mdpi/ic_launcher.png
cp path/to/pixel_72x72.png app/src/main/res/mipmap-hdpi/ic_launcher.png
cp path/to/pixel_96x96.png app/src/main/res/mipmap-xhdpi/ic_launcher.png
cp path/to/pixel_144x144.png app/src/main/res/mipmap-xxhdpi/ic_launcher.png
cp path/to/pixel_192x192.png app/src/main/res/mipmap-xxxhdpi/ic_launcher.png
```

### Step 3: Build & Install

```bash
# Clean build to ensure resource refresh
./gradlew clean

# Build debug APK
./gradlew assembleDebug

# Install on Shield TV
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Step 4: Verify Logo Change

**Testing:**
1. ✅ Check Android TV home screen - verify Pixel logo appears
2. ✅ Long-press app icon - verify icon displays correctly
3. ✅ Check all screen densities render properly
4. ✅ Verify APK size increase is minimal (<500KB)

**Expected Result:**
Pixel logo appears as FarsiHub launcher icon on Shield TV home screen.

---

## Phase B: Compose TV Logo Selection Feature

**Status**: ⏸️ WAITING FOR COMPOSE MIGRATION
**Prerequisite**: DetailsActivity + SearchActivity migrated to Compose (Enhancement Roadmap Phase 2.1-2.3)
**Effort**: 4-6 hours
**Reference**: See `docs/ENHANCEMENT_ROADMAP.md` Phase 2.6

### Architecture Overview

**Pattern**: Activity-alias with dynamic enablement via PackageManager
**UI Framework**: Compose TV (androidx.tv.material3)
**Storage**: SharedPreferences
**Threading**: Coroutines with IO dispatcher

**Component Flow:**
```
User Opens Settings
    ↓
OptionsFragment (Leanback/Compose hybrid)
    ↓
[Click "App Logo"]
    ↓
LogoSelectionScreen (Compose TV)
    ↓
[Select Logo] → LogoPreferences.save() → LogoSwitcher.switch()
    ↓
PackageManager enables selected activity-alias
    ↓
Icon changes on Android TV home screen (immediate)
```

---

### Step 1: Optimize & Copy All Logo Assets

**Source Files:**
- `G:\FarsiPlex\logo_embroidery.png`
- `G:\FarsiPlex\logo_origami.png`
- `G:\FarsiPlex\logo_pixel.png`
- `G:\FarsiPlex\logo_watercolor.png`

**Process:**
1. Generate 6 mipmap densities per logo (24 files total)
2. Compress PNGs or convert to WebP
3. Name: `ic_launcher_embroidery.png`, `ic_launcher_origami.png`, etc.

**Output Location:**
```
app/src/main/res/
├── mipmap-mdpi/
│   ├── ic_launcher_embroidery.png  (48x48)
│   ├── ic_launcher_origami.png
│   ├── ic_launcher_pixel.png
│   └── ic_launcher_watercolor.png
├── mipmap-hdpi/       (72x72, same 4 logos)
├── mipmap-xhdpi/      (96x96, same 4 logos)
├── mipmap-xxhdpi/     (144x144, same 4 logos)
├── mipmap-xxxhdpi/    (192x192, same 4 logos)
```

**Expected APK Size Impact:** +1.5-2.5 MB (optimized PNGs)

---

### Step 2: Create Logo Preferences Manager

**File**: `app/src/main/java/com/example/farsilandtv/data/preferences/LogoPreferences.kt`

**Implementation:**
```kotlin
package com.example.farsilandtv.data.preferences

import android.content.Context
import androidx.annotation.DrawableRes
import com.example.farsilandtv.R

/**
 * Logo types available for user selection
 */
enum class LogoType(
    val displayName: String,
    @DrawableRes val iconRes: Int,
    val activityAliasName: String
) {
    EMBROIDERY(
        displayName = "Embroidery",
        iconRes = R.mipmap.ic_launcher_embroidery,
        activityAliasName = ".MainActivityEmbroidery"
    ),
    ORIGAMI(
        displayName = "Origami",
        iconRes = R.mipmap.ic_launcher_origami,
        activityAliasName = ".MainActivityOrigami"
    ),
    PIXEL(
        displayName = "Pixel",
        iconRes = R.mipmap.ic_launcher_pixel,
        activityAliasName = ".MainActivityPixel"
    ),
    WATERCOLOR(
        displayName = "Watercolor",
        iconRes = R.mipmap.ic_launcher_watercolor,
        activityAliasName = ".MainActivityWatercolor"
    )
}

/**
 * Manages user logo selection preference
 * Pattern: Follows DatabasePreferences.kt structure
 */
object LogoPreferences {
    private const val PREF_NAME = "logo_preferences"
    private const val KEY_SELECTED_LOGO = "selected_logo"

    /**
     * Get currently selected logo
     * @return Selected LogoType, defaults to PIXEL
     */
    fun getSelectedLogo(context: Context): LogoType {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val logoName = prefs.getString(KEY_SELECTED_LOGO, LogoType.PIXEL.name)
        return try {
            LogoType.valueOf(logoName ?: LogoType.PIXEL.name)
        } catch (e: IllegalArgumentException) {
            LogoType.PIXEL // Fallback if invalid value stored
        }
    }

    /**
     * Save selected logo preference
     */
    fun setSelectedLogo(context: Context, logoType: LogoType) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SELECTED_LOGO, logoType.name)
            .apply()
    }

    /**
     * Check if logo preference has been set (for migration)
     */
    fun hasLogoPreference(context: Context): Boolean {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .contains(KEY_SELECTED_LOGO)
    }
}
```

**Lines:** ~80

---

### Step 3: Update AndroidManifest.xml

**File**: `app/src/main/AndroidManifest.xml`

**Add 4 Activity-Alias Entries** (inside `<application>` tag, after `<activity android:name=".MainActivity">`):

```xml
<!-- ==================== LOGO SELECTION ACTIVITY ALIASES ==================== -->
<!-- These aliases allow dynamic launcher icon switching without app restart -->

<!-- Pixel Logo (Default - Enabled) -->
<activity-alias
    android:name=".MainActivityPixel"
    android:enabled="true"
    android:exported="true"
    android:icon="@mipmap/ic_launcher_pixel"
    android:label="@string/app_name"
    android:targetActivity=".MainActivity">
    <intent-filter>
        <action android:name="android.intent.action.MAIN" />
        <category android:name="android.intent.category.LAUNCHER" />
        <category android:name="android.intent.category.LEANBACK_LAUNCHER" />
    </intent-filter>
</activity-alias>

<!-- Embroidery Logo (Disabled by default) -->
<activity-alias
    android:name=".MainActivityEmbroidery"
    android:enabled="false"
    android:exported="true"
    android:icon="@mipmap/ic_launcher_embroidery"
    android:label="@string/app_name"
    android:targetActivity=".MainActivity">
    <intent-filter>
        <action android:name="android.intent.action.MAIN" />
        <category android:name="android.intent.category.LAUNCHER" />
        <category android:name="android.intent.category.LEANBACK_LAUNCHER" />
    </intent-filter>
</activity-alias>

<!-- Origami Logo (Disabled by default) -->
<activity-alias
    android:name=".MainActivityOrigami"
    android:enabled="false"
    android:exported="true"
    android:icon="@mipmap/ic_launcher_origami"
    android:label="@string/app_name"
    android:targetActivity=".MainActivity">
    <intent-filter>
        <action android:name="android.intent.action.MAIN" />
        <category android:name="android.intent.category.LAUNCHER" />
        <category android:name="android.intent.category.LEANBACK_LAUNCHER" />
    </intent-filter>
</activity-alias>

<!-- Watercolor Logo (Disabled by default) -->
<activity-alias
    android:name=".MainActivityWatercolor"
    android:enabled="false"
    android:exported="true"
    android:icon="@mipmap/ic_launcher_watercolor"
    android:label="@string/app_name"
    android:targetActivity=".MainActivity">
    <intent-filter>
        <action android:name="android.intent.action.MAIN" />
        <category android:name="android.intent.category.LAUNCHER" />
        <category android:name="android.intent.category.LEANBACK_LAUNCHER" />
    </intent-filter>
</activity-alias>
<!-- ==================== END LOGO SELECTION ALIASES ==================== -->
```

**CRITICAL:**
- Must include `android:exported="true"` for API 31+
- Must include `LEANBACK_LAUNCHER` category for Android TV
- Only ONE alias should be `enabled="true"` at a time

**Lines Added:** ~70

---

### Step 4: Create Activity-Alias Switcher (with Threading)

**File**: `app/src/main/java/com/example/farsilandtv/utils/LogoSwitcher.kt`

**Implementation:**
```kotlin
package com.example.farsilandtv.utils

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.example.farsilandtv.data.preferences.LogoType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Utility to switch app launcher icon using activity-alias pattern
 * CRITICAL: Must run on IO dispatcher to avoid ANR
 */
object LogoSwitcher {
    private const val TAG = "LogoSwitcher"

    /**
     * Switch launcher icon to selected logo
     * Enables selected activity-alias, disables all others
     *
     * @param context Application or Activity context
     * @param logoType Selected logo to enable
     */
    suspend fun switchLogo(context: Context, logoType: LogoType) = withContext(Dispatchers.IO) {
        val packageManager = context.packageManager
        val packageName = context.packageName

        try {
            // Disable all aliases first
            LogoType.values().forEach { type ->
                val componentName = ComponentName(
                    packageName,
                    "$packageName${type.activityAliasName}"
                )

                val newState = if (type == logoType) {
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                } else {
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                }

                packageManager.setComponentEnabledSetting(
                    componentName,
                    newState,
                    PackageManager.DONT_KILL_APP // Keep app running
                )

                Log.d(TAG, "${type.displayName} logo ${if (type == logoType) "enabled" else "disabled"}")
            }

            Log.i(TAG, "Successfully switched launcher icon to ${logoType.displayName}")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to switch launcher icon to ${logoType.displayName}", e)
            // Don't crash app if logo switch fails - degrade gracefully
        }
    }
}
```

**Key Features:**
- ✅ Runs on `Dispatchers.IO` (prevents ANR)
- ✅ Enables only selected logo, disables others
- ✅ Uses `DONT_KILL_APP` flag (no restart needed)
- ✅ Error handling with logging
- ✅ Graceful degradation on failure

**Lines:** ~50

---

### Step 5: Create Compose TV Logo Selection Screen

**File**: `app/src/main/java/com/example/farsilandtv/ui/compose/LogoSelectionScreen.kt`

**Implementation:**
```kotlin
package com.example.farsilandtv.ui.compose

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.*
import com.example.farsilandtv.data.preferences.LogoPreferences
import com.example.farsilandtv.data.preferences.LogoType
import com.example.farsilandtv.utils.LogoSwitcher
import kotlinx.coroutines.launch

/**
 * Compose TV screen for logo selection
 * Uses androidx.tv.material3 components for Android TV
 */
@Composable
fun LogoSelectionScreen(
    onLogoSelected: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Load current selection
    var selectedLogo by remember {
        mutableStateOf(LogoPreferences.getSelectedLogo(context))
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(48.dp)
    ) {
        // Title
        Text(
            text = "Select App Logo",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        // Logo options list
        TvLazyColumn(
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(LogoType.values().size) { index ->
                val logoType = LogoType.values()[index]

                ListItem(
                    selected = logoType == selectedLogo,
                    onClick = {
                        // Update selection
                        selectedLogo = logoType
                        LogoPreferences.setSelectedLogo(context, logoType)

                        // Switch logo in background
                        scope.launch {
                            LogoSwitcher.switchLogo(context, logoType)
                            onLogoSelected() // Navigate back
                        }
                    },
                    headlineContent = {
                        Text(text = logoType.displayName)
                    },
                    supportingContent = {
                        if (logoType == selectedLogo) {
                            Text("Current logo")
                        }
                    },
                    leadingContent = {
                        // Logo preview icon (48dp)
                        Icon(
                            painter = painterResource(id = logoType.iconRes),
                            contentDescription = "${logoType.displayName} logo preview",
                            modifier = Modifier.size(48.dp)
                        )
                    },
                    trailingContent = {
                        // Checkmark for selected logo
                        if (logoType == selectedLogo) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Selected",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
```

**UI Layout:**
```
┌──────────────────────────────────────┐
│  Select App Logo                     │
│                                      │
│  [Icon] Embroidery                   │
│  [Icon] Origami                      │
│  [Icon] Pixel               ✓        │  ← Selected
│  [Icon] Watercolor                   │
└──────────────────────────────────────┘
```

**Features:**
- ✅ Live icon preview (48dp icons from mipmaps)
- ✅ Current selection indicator (checkmark)
- ✅ D-pad navigation optimized for Android TV
- ✅ Immediate feedback on selection
- ✅ Background logo switching (no UI freeze)

**Lines:** ~100

---

### Step 6: Integrate into Settings (OptionsFragment)

**File**: `app/src/main/java/com/example/farsilandtv/OptionsFragment.kt`

**Changes:**

**A. Add ComposeView for Logo Selection**
```kotlin
// Add at class level
private var logoComposeView: ComposeView? = null

// Add method to show logo selection
private fun showLogoSelection() {
    // Create Compose view
    logoComposeView = ComposeView(requireContext()).apply {
        setContent {
            // Use app theme if exists, or default
            MaterialTheme {
                LogoSelectionScreen(
                    onLogoSelected = {
                        // Remove Compose view, return to settings
                        hideLogoSelection()
                        // Refresh settings description to show new logo
                        refreshLogoAction()
                    }
                )
            }
        }
    }

    // Add to fragment container (replace current view)
    (view as? ViewGroup)?.addView(
        logoComposeView,
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT
    )
}

private fun hideLogoSelection() {
    logoComposeView?.let { view ->
        (it.parent as? ViewGroup)?.removeView(it)
    }
    logoComposeView = null
}

private fun refreshLogoAction() {
    // Update action description to show current logo
    // Implementation depends on existing OptionsFragment structure
}
```

**B. Add "App Logo" Action to Settings Menu**
```kotlin
// In onCreateActions() or wherever actions are defined
// Add between "Database Source" and "Clear Cache" actions

actions.add(
    GuidedAction.Builder(context)
        .id(ACTION_LOGO_SELECTION)
        .title("App Logo")
        .description("Current: ${LogoPreferences.getSelectedLogo(requireContext()).displayName}")
        .build()
)
```

**C. Handle Logo Action Click**
```kotlin
// In onGuidedActionClicked() or click handler
when (action.id) {
    ACTION_LOGO_SELECTION -> {
        showLogoSelection()
    }
    // ... other actions
}
```

**Lines Added:** ~40

---

### Step 7: Add Migration Check for Existing Users

**File**: `app/src/main/java/com/example/farsilandtv/FarsilandApp.kt`

**Changes:**
```kotlin
// In onCreate() method - runs once on first launch after update

override fun onCreate() {
    super.onCreate()

    // ... existing initialization code ...

    // One-time logo migration for existing users
    if (!LogoPreferences.hasLogoPreference(this)) {
        // Set default to PIXEL for existing users (matches current icon)
        LogoPreferences.setSelectedLogo(this, LogoType.PIXEL)
    }
}
```

**Purpose:**
- Existing users get PIXEL logo (matches current default)
- New installs get PIXEL logo (consistent experience)
- Prevents null/undefined logo state

**Lines Added:** ~5

---

## Files Summary

### New Files (3)

1. **`data/preferences/LogoPreferences.kt`** (~80 lines)
   - Logo enum with display names + icon resources
   - SharedPreferences manager
   - Migration check method

2. **`utils/LogoSwitcher.kt`** (~50 lines)
   - Activity-alias switcher with IO dispatcher
   - Error handling + logging
   - Graceful degradation

3. **`ui/compose/LogoSelectionScreen.kt`** (~100 lines)
   - Compose TV selection UI
   - Live icon previews
   - D-pad navigation optimized

### Modified Files (3)

1. **`AndroidManifest.xml`** (+70 lines)
   - 4 activity-alias entries
   - Intent filters with LEANBACK_LAUNCHER
   - Proper exported + enabled flags

2. **`ui/OptionsFragment.kt`** (+40 lines)
   - ComposeView integration
   - Logo selection action
   - Navigation logic

3. **`FarsilandApp.kt`** (+5 lines)
   - One-time migration check
   - Default logo initialization

### Assets (24 files)

- 6 mipmap densities × 4 logos = 24 PNG files
- Estimated size: 1.5-2.5 MB (optimized)

**Total Code:** ~375 lines across 6 files

---

## Build & Testing

### Compilation
```bash
# Syntax check (30 sec)
./gradlew compileDebugKotlin

# Full build (2-3 min)
./gradlew assembleDebug
```

### Installation
```bash
# Install on Shield TV
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Uninstall first if needed
adb uninstall com.example.farsilandtv
adb install app/build/outputs/apk/debug/app-debug.apk
```

### Testing Checklist

**Functional Tests:**
- ✅ Navigate to Settings → Options → App Logo
- ✅ D-pad navigation works in logo selection screen
- ✅ All 4 logos display with correct preview icons
- ✅ Current logo shows checkmark indicator
- ✅ Select each logo (Embroidery → Origami → Pixel → Watercolor)
- ✅ Icon changes immediately on Android TV home screen
- ✅ No app restart required for icon change
- ✅ Return to settings shows updated "Current: [Logo]" description

**Persistence Tests:**
- ✅ Select logo, kill app, restart - verify logo persists
- ✅ Select logo, reboot device - verify logo persists
- ✅ Update app - verify logo preference maintained

**Performance Tests:**
- ✅ Rapid logo switching (4 logos in 10 seconds) - no crashes
- ✅ No ANR during logo switch (check logcat)
- ✅ Logo selection screen loads instantly (<500ms)

**Edge Cases:**
- ✅ Long-press app icon on launcher - refreshes correctly
- ✅ APK size increase acceptable (<3 MB)
- ✅ All mipmap densities render correctly (test mdpi/xxxhdpi)
- ✅ Error handling works (simulate PackageManager failure)

**Device Testing:**
- ✅ Nvidia Shield TV (primary target)
- ✅ Android TV emulator (API 28, 31, 35)
- ✅ Generic Android TV device (if available)

### Logcat Monitoring
```bash
# Watch logo switching logs
adb logcat | grep -i "LogoSwitcher"

# Check for errors during switch
adb logcat | grep -E "(ERROR|FATAL|ANR)"
```

---

## Expected Outcome

### User Experience

1. User opens **Settings → Options → App Logo**
2. Compose TV selection screen appears with 4 logo options
3. Each logo shows **live preview icon** (48dp)
4. Current logo has **checkmark indicator**
5. User selects new logo using D-pad
6. Logo switches **immediately** in background
7. Screen returns to settings automatically
8. Android TV home screen shows **new logo instantly**
9. No app restart required

### Technical Behavior

- `PackageManager.setComponentEnabledSetting()` runs on IO dispatcher
- Only one activity-alias enabled at a time
- Preference persists in SharedPreferences
- Existing users default to PIXEL logo
- New installs default to PIXEL logo
- APK size increases by ~2 MB (24 optimized PNGs)

---

## Dependencies

### Compose TV Libraries (Already in Project)
```kotlin
// build.gradle.kts
implementation("androidx.tv:tv-material:1.0.0-alpha10")
implementation("androidx.compose.ui:ui:1.5.4")
implementation("androidx.activity:activity-compose:1.8.1")
```

### No Additional Dependencies Required
All required libraries already present in FarsiHub project.

---

## Rollback Plan

If logo feature causes issues:

**Quick Rollback:**
1. Remove all activity-alias entries from AndroidManifest.xml
2. Keep only original `ic_launcher.png` mipmaps
3. Delete LogoPreferences.kt, LogoSwitcher.kt, LogoSelectionScreen.kt
4. Remove logo action from OptionsFragment.kt
5. Rebuild and deploy

**Partial Rollback:**
- Keep logo assets for future use
- Disable logo selection UI (hide action in OptionsFragment)
- Users keep currently selected logo

---

## Integration with Enhancement Roadmap

**This feature aligns with:**
- ✅ Phase 2: UI Modernization (Compose TV adoption)
- ✅ Phase 2.1-2.3: Compose migration prerequisites
- ✅ Future settings screen full Compose migration

**Roadmap Position:**
- Executes after Phase 2.5 (RemoteMediator)
- Before Phase 3 (Phone Support)
- See `docs/ENHANCEMENT_ROADMAP.md` Phase 2.6

---

## Notes

- All changes maintain audit compliance (30/30 issues fixed)
- Dual database pattern preserved (AppDatabase ≠ ContentDatabase)
- Backward compatible with existing Shield TV users
- No breaking changes to user data
- Future-proof Compose implementation (no technical debt)
- Zero dependencies on Leanback for logo selection UI

---

**Last Updated**: 2025-11-22
**Status**: Documentation Complete - Ready for Phase A execution
