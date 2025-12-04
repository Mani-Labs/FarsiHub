package com.example.farsilandtv.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.*
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import coil.ImageLoader
import com.example.farsilandtv.FarsilandApp
import com.example.farsilandtv.data.database.AppDatabase
import com.example.farsilandtv.data.database.ContentDatabase
import com.example.farsilandtv.data.database.DatabaseSource
import com.example.farsilandtv.data.health.ScraperHealthTracker
import com.example.farsilandtv.data.sync.ContentSyncWorker
import com.example.farsilandtv.data.sync.FarsiPlexSyncWorker
import com.example.farsilandtv.data.sync.IMVBoxSyncWorker
import com.example.farsilandtv.data.api.IMVBoxAuthManager
import com.example.farsilandtv.utils.DeviceUtils
import com.example.farsilandtv.utils.LocalDeviceType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Options/Settings Screen - Compose TV & Phone
 * Replaces OptionsFragment and all sub-fragments (Leanback)
 *
 * Device-aware: Uses TV Material 3 for TV/Tablet, Material 3 for Phone
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun OptionsScreen(
    healthTracker: ScraperHealthTracker,
    onBackClick: () -> Unit,
    onDatabaseSourceChange: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    val deviceType = LocalDeviceType.current
    val isPhone = deviceType == DeviceUtils.DeviceType.PHONE

    // Preferences
    val syncPrefs = remember { context.getSharedPreferences("sync_settings", Context.MODE_PRIVATE) }
    val syncStatePrefs = remember { context.getSharedPreferences("sync_state", Context.MODE_PRIVATE) }
    val appStatePrefs = remember { context.getSharedPreferences("app_state", Context.MODE_PRIVATE) }

    // State
    var currentSource by remember { mutableStateOf(ContentDatabase.getCurrentSource(context)) }
    var syncEnabled by remember { mutableStateOf(syncPrefs.getBoolean("sync_enabled", true)) }
    var syncFrequency by remember { mutableStateOf(syncPrefs.getLong("sync_interval_minutes", 30L)) }
    var lastSyncTimestamp by remember { mutableStateOf(syncStatePrefs.getLong("last_sync_timestamp", 0L)) }
    var syncStatus by remember { mutableStateOf("") }

    // Phase 5: Scraper Health Monitoring
    val healthStatus by healthTracker.healthStatus.collectAsState()
    val hasHealthAlerts by healthTracker.hasHealthAlerts.collectAsState()

    // Dialog states
    var showDatabaseSourceDialog by remember { mutableStateOf(false) }
    var showFrequencyDialog by remember { mutableStateOf(false) }
    var showClearHistoryDialog by remember { mutableStateOf(false) }
    var showFullResyncDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showIMVBoxLoginDialog by remember { mutableStateOf(false) }

    // IMVBox login state
    var imvboxLoggedIn by remember { mutableStateOf(IMVBoxAuthManager.isLoggedIn(context)) }
    var imvboxEmail by remember { mutableStateOf(IMVBoxAuthManager.getSavedEmail(context) ?: "") }

    // Request focus on first item (TV only - phones use touch)
    LaunchedEffect(Unit) {
        if (!isPhone) {
            try {
                focusRequester.requestFocus()
            } catch (e: IllegalStateException) {
                // FocusRequester not yet attached, ignore
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
            .onPreviewKeyEvent { event ->
                if (event.key == Key.Back && event.type == KeyEventType.KeyUp) {
                    onBackClick()
                    true
                } else {
                    false
                }
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (isPhone) 16.dp else 48.dp)
        ) {
            // Header with back button for phone
            if (isPhone) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBackClick) {
                        androidx.compose.material3.Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                    Text(
                        text = "Settings",
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                // TV header
                Text(
                    text = "Settings",
                    color = Color.White,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "App settings and sync preferences",
                    color = Color.Gray,
                    fontSize = 16.sp,
                    modifier = Modifier.padding(bottom = 24.dp)
                )
            }

            // Options List
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                // Content Source
                item {
                    OptionItem(
                        title = "Content Source",
                        description = "Currently: ${currentSource.displayName}",
                        icon = Icons.Default.List,
                        onClick = { showDatabaseSourceDialog = true },
                        isPhone = isPhone,
                        modifier = if (!isPhone) Modifier.focusRequester(focusRequester) else Modifier
                    )
                }

                // IMVBox Account Login
                item {
                    OptionItem(
                        title = "IMVBox Account",
                        description = if (imvboxLoggedIn) "Logged in as $imvboxEmail" else "Login to enable premium content",
                        icon = Icons.Default.AccountCircle,
                        onClick = { showIMVBoxLoginDialog = true },
                        isPhone = isPhone
                    )
                }

                // Sync Now
                item {
                    OptionItem(
                        title = "Sync Now",
                        description = syncStatus.ifEmpty { "Check websites for new movies & shows" },
                        icon = Icons.Default.Refresh,
                        isPhone = isPhone,
                        onClick = {
                            syncStatus = "Syncing..."
                            triggerManualSync(context) { success ->
                                syncStatus = if (success) "Sync complete" else "Sync failed"
                                lastSyncTimestamp = syncStatePrefs.getLong("last_sync_timestamp", 0L)
                            }
                        }
                    )
                }

                // Auto-Sync Toggle
                item {
                    OptionToggleItem(
                        title = "Automatic Sync",
                        description = if (syncEnabled) "Enabled" else "Disabled",
                        icon = Icons.Default.Check,
                        checked = syncEnabled,
                        isPhone = isPhone,
                        onCheckedChange = { enabled ->
                            syncEnabled = enabled
                            syncPrefs.edit().putBoolean("sync_enabled", enabled).apply()
                            if (enabled) {
                                (context.applicationContext as? FarsilandApp)?.onCreate()
                            } else {
                                WorkManager.getInstance(context).cancelUniqueWork(ContentSyncWorker.WORK_NAME)
                            }
                        }
                    )
                }

                // Sync Frequency (only if enabled)
                if (syncEnabled) {
                    item {
                        OptionItem(
                            title = "Sync Frequency",
                            description = getFrequencyText(syncFrequency),
                            icon = Icons.Default.Info,
                            isPhone = isPhone,
                            onClick = { showFrequencyDialog = true }
                        )
                    }
                }

                // Sync Status
                item {
                    OptionItem(
                        title = "Sync Status",
                        description = getLastSyncText(lastSyncTimestamp),
                        icon = Icons.Default.Info,
                        enabled = false,
                        isPhone = isPhone,
                        onClick = {}
                    )
                }

                // Phase 5: Scraper Health Alerts
                if (hasHealthAlerts) {
                    item {
                        ScraperHealthAlert(
                            healthStatus = healthStatus,
                            isPhone = isPhone,
                            onResetClick = { healthTracker.resetCounters() }
                        )
                    }
                }

                // Divider
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Force Full Re-Sync
                item {
                    OptionItem(
                        title = "Force Full Re-Sync",
                        description = "Reset content database to bundled version",
                        icon = Icons.Default.Refresh,
                        isPhone = isPhone,
                        onClick = { showFullResyncDialog = true }
                    )
                }

                // Clear Cache
                item {
                    OptionItem(
                        title = "Clear Cache",
                        description = "Clear image and data cache",
                        icon = Icons.Default.Clear,
                        isPhone = isPhone,
                        onClick = {
                            clearCache(context)
                            Toast.makeText(context, "Cache cleared", Toast.LENGTH_SHORT).show()
                        }
                    )
                }

                // Clear History
                item {
                    OptionItem(
                        title = "Clear Watch History",
                        description = "Remove all watch history and progress",
                        icon = Icons.Default.Delete,
                        isPhone = isPhone,
                        onClick = { showClearHistoryDialog = true }
                    )
                }

                // Divider
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // About
                item {
                    OptionItem(
                        title = "About",
                        description = "FarsiHub v1.0",
                        icon = Icons.Default.Info,
                        isPhone = isPhone,
                        onClick = { showAboutDialog = true }
                    )
                }

                // Back (only show for TV, phone has back button in header)
                if (!isPhone) {
                    item {
                        OptionItem(
                            title = "Back",
                            description = "Return to home",
                            icon = Icons.Default.ArrowBack,
                            isPhone = false,
                            onClick = onBackClick
                        )
                    }
                }
            }
        }
    }

    // Database Source Dialog
    if (showDatabaseSourceDialog) {
        DatabaseSourceDialog(
            currentSource = currentSource,
            isPhone = isPhone,
            onDismiss = { showDatabaseSourceDialog = false },
            onSourceSelected = { source ->
                showDatabaseSourceDialog = false
                android.util.Log.d("OptionsScreen", "Selected source: ${source.displayName}, current: ${currentSource.displayName}")
                if (source != currentSource) {
                    android.util.Log.d("OptionsScreen", "Switching database...")
                    coroutineScope.launch {
                        try {
                            val switched = withContext(Dispatchers.IO) {
                                ContentDatabase.switchDatabaseSource(context, source)
                            }
                            android.util.Log.d("OptionsScreen", "Switch result: $switched")
                            if (switched) {
                                currentSource = source
                                Toast.makeText(context, "Switched to ${source.displayName}", Toast.LENGTH_SHORT).show()
                                onDatabaseSourceChange()
                            } else {
                                Toast.makeText(context, "Switch failed", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("OptionsScreen", "Database switch error", e)
                            Toast.makeText(context, "Error switching database: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    Toast.makeText(context, "Already on ${source.displayName}", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    // Frequency Dialog
    if (showFrequencyDialog) {
        FrequencyPickerDialog(
            currentFrequency = syncFrequency,
            isPhone = isPhone,
            onDismiss = { showFrequencyDialog = false },
            onFrequencySelected = { minutes ->
                syncFrequency = minutes
                syncPrefs.edit().putLong("sync_interval_minutes", minutes).apply()
                showFrequencyDialog = false
                Toast.makeText(context, "Sync frequency updated", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Clear History Confirmation
    if (showClearHistoryDialog) {
        ConfirmationDialog(
            title = "Clear Watch History?",
            message = "This will remove all watch progress, continue watching items, and playback positions. This cannot be undone.",
            confirmText = "Yes, Clear History",
            isPhone = isPhone,
            onConfirm = {
                showClearHistoryDialog = false
                clearWatchHistory(context, coroutineScope)
            },
            onDismiss = { showClearHistoryDialog = false }
        )
    }

    // Full Resync Confirmation
    if (showFullResyncDialog) {
        ConfirmationDialog(
            title = "Force Full Re-Sync?",
            message = "This will reset the content database to the bundled version and re-sync all content. Use this if the database appears corrupted.",
            confirmText = "Yes, Reset & Re-Sync",
            isPhone = isPhone,
            onConfirm = {
                showFullResyncDialog = false
                performFullResync(context, appStatePrefs)
            },
            onDismiss = { showFullResyncDialog = false }
        )
    }

    // About Dialog
    if (showAboutDialog) {
        AboutDialog(
            context = context,
            isPhone = isPhone,
            onDismiss = { showAboutDialog = false }
        )
    }

    // IMVBox Login Dialog
    if (showIMVBoxLoginDialog) {
        IMVBoxLoginDialog(
            context = context,
            isLoggedIn = imvboxLoggedIn,
            currentEmail = imvboxEmail,
            isPhone = isPhone,
            onDismiss = { showIMVBoxLoginDialog = false },
            onLoginSuccess = { email ->
                imvboxLoggedIn = true
                imvboxEmail = email
                showIMVBoxLoginDialog = false
                Toast.makeText(context, "Logged in to IMVBox", Toast.LENGTH_SHORT).show()
            },
            onLogout = {
                coroutineScope.launch {
                    IMVBoxAuthManager.logout(context)
                    imvboxLoggedIn = false
                    imvboxEmail = ""
                    showIMVBoxLoginDialog = false
                    Toast.makeText(context, "Logged out from IMVBox", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun OptionItem(
    title: String,
    description: String,
    icon: ImageVector,
    onClick: () -> Unit,
    isPhone: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    if (isPhone) {
        // Phone: Use Material 3 Card with clickable modifier for touch support
        Card(
            modifier = modifier
                .fillMaxWidth()
                .height(72.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable(enabled = enabled) { onClick() },
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
            shape = RoundedCornerShape(8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                androidx.compose.material3.Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (enabled) Color.White else Color.Gray,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        color = if (enabled) Color.White else Color.Gray,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = description,
                        color = if (enabled) Color.LightGray else Color.DarkGray,
                        fontSize = 13.sp
                    )
                }
                // Arrow for phone
                androidx.compose.material3.Icon(
                    imageVector = Icons.Default.KeyboardArrowRight,
                    contentDescription = null,
                    tint = Color.Gray,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    } else {
        // TV: Use TV Material 3 Surface for D-pad focus
        androidx.tv.material3.Surface(
            onClick = onClick,
            modifier = modifier
                .fillMaxWidth()
                .height(72.dp),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Color(0xFF1E1E1E),
                focusedContainerColor = Color(0xFF2E7D32)
            ),
            shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(8.dp))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (enabled) Color.White else Color.Gray,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        color = if (enabled) Color.White else Color.Gray,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = description,
                        color = if (enabled) Color.LightGray else Color.DarkGray,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun OptionToggleItem(
    title: String,
    description: String,
    icon: ImageVector,
    checked: Boolean,
    isPhone: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    if (isPhone) {
        // Phone: Use Material 3 Card with clickable modifier for touch support
        Card(
            modifier = modifier
                .fillMaxWidth()
                .height(72.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable { onCheckedChange(!checked) },
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
            shape = RoundedCornerShape(8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                androidx.compose.material3.Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = description,
                        color = Color.LightGray,
                        fontSize = 13.sp
                    )
                }
                Switch(
                    checked = checked,
                    onCheckedChange = { onCheckedChange(it) }
                )
            }
        }
    } else {
        // TV: Use TV Material 3 Surface for D-pad focus
        androidx.tv.material3.Surface(
            onClick = { onCheckedChange(!checked) },
            modifier = modifier
                .fillMaxWidth()
                .height(72.dp),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Color(0xFF1E1E1E),
                focusedContainerColor = Color(0xFF2E7D32)
            ),
            shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(8.dp))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = description,
                        color = Color.LightGray,
                        fontSize = 14.sp
                    )
                }
                Switch(
                    checked = checked,
                    onCheckedChange = null
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun DatabaseSourceDialog(
    currentSource: DatabaseSource,
    isPhone: Boolean,
    onDismiss: () -> Unit,
    onSourceSelected: (DatabaseSource) -> Unit
) {
    val sources = DatabaseSource.values()
    val focusRequesters = remember { sources.map { FocusRequester() } }

    // Request focus on first item when dialog opens (TV only)
    LaunchedEffect(Unit) {
        if (!isPhone) {
            try {
                focusRequesters.firstOrNull()?.requestFocus()
            } catch (_: Exception) {}
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .width(if (isPhone) 320.dp else 400.dp)
                .wrapContentHeight(),
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFF2A2A2A),
            tonalElevation = 8.dp
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Select Content Source",
                    color = Color.White,
                    fontSize = if (isPhone) 18.sp else 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                sources.forEachIndexed { index, source ->
                    if (isPhone) {
                        // Phone: Use Material 3 Card with clickable
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable { onSourceSelected(source) },
                            colors = CardDefaults.cardColors(
                                containerColor = if (source == currentSource) Color(0xFF2E7D32) else Color(0xFF3A3A3A)
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = source.displayName,
                                    color = Color.White,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = getSourceDescription(source),
                                    color = Color.LightGray,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    } else {
                        // TV: Use TV Material 3 Surface
                        androidx.tv.material3.Surface(
                            onClick = { onSourceSelected(source) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .focusRequester(focusRequesters[index]),
                            colors = ClickableSurfaceDefaults.colors(
                                containerColor = if (source == currentSource) Color(0xFF2E7D32) else Color(0xFF3A3A3A),
                                focusedContainerColor = Color(0xFF388E3C)
                            ),
                            shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(8.dp))
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = source.displayName,
                                    color = Color.White,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = getSourceDescription(source),
                                    color = Color.LightGray,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (isPhone) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Cancel", color = Color.White)
                    }
                } else {
                    androidx.tv.material3.Button(
                        onClick = onDismiss,
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Cancel")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun FrequencyPickerDialog(
    currentFrequency: Long,
    isPhone: Boolean,
    onDismiss: () -> Unit,
    onFrequencySelected: (Long) -> Unit
) {
    val frequencies = listOf(
        15L to "Every 15 minutes",
        30L to "Every 30 minutes",
        60L to "Every hour",
        1440L to "Daily"
    )

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .width(if (isPhone) 300.dp else 350.dp)
                .wrapContentHeight(),
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFF2A2A2A),
            tonalElevation = 8.dp
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Sync Frequency",
                    color = Color.White,
                    fontSize = if (isPhone) 18.sp else 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                frequencies.forEach { (minutes, label) ->
                    if (isPhone) {
                        // Phone: Use Material 3 Card with clickable
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable { onFrequencySelected(minutes) },
                            colors = CardDefaults.cardColors(
                                containerColor = if (minutes == currentFrequency) Color(0xFF2E7D32) else Color(0xFF3A3A3A)
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = label,
                                color = Color.White,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    } else {
                        // TV: Use TV Material 3 Surface
                        androidx.tv.material3.Surface(
                            onClick = { onFrequencySelected(minutes) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            colors = ClickableSurfaceDefaults.colors(
                                containerColor = if (minutes == currentFrequency) Color(0xFF2E7D32) else Color(0xFF3A3A3A),
                                focusedContainerColor = Color(0xFF388E3C)
                            ),
                            shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(8.dp))
                        ) {
                            Text(
                                text = label,
                                color = Color.White,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (isPhone) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Cancel", color = Color.White)
                    }
                } else {
                    androidx.tv.material3.Button(
                        onClick = onDismiss,
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Cancel")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ConfirmationDialog(
    title: String,
    message: String,
    confirmText: String,
    isPhone: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .width(if (isPhone) 320.dp else 400.dp)
                .wrapContentHeight(),
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFF2A2A2A),
            tonalElevation = 8.dp
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = if (isPhone) 18.sp else 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Text(
                    text = message,
                    color = Color.LightGray,
                    fontSize = if (isPhone) 13.sp else 14.sp,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    if (isPhone) {
                        // Phone: Use Material 3 buttons
                        TextButton(
                            onClick = onDismiss,
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Text("Cancel", color = Color.White)
                        }
                        Button(
                            onClick = onConfirm,
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFD32F2F)
                            )
                        ) {
                            Text(confirmText, color = Color.White)
                        }
                    } else {
                        // TV: Use TV Material 3 buttons
                        androidx.tv.material3.Button(
                            onClick = onDismiss,
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Text("Cancel")
                        }
                        androidx.tv.material3.Button(
                            onClick = onConfirm,
                            colors = androidx.tv.material3.ButtonDefaults.colors(containerColor = Color(0xFFD32F2F))
                        ) {
                            Text(confirmText)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun AboutDialog(
    context: Context,
    isPhone: Boolean,
    onDismiss: () -> Unit
) {
    val versionName = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    } catch (e: Exception) {
        "1.0"
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .width(if (isPhone) 300.dp else 350.dp)
                .wrapContentHeight(),
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFF2A2A2A),
            tonalElevation = 8.dp
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "FarsiHub",
                    color = Color.White,
                    fontSize = if (isPhone) 20.sp else 24.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Version $versionName",
                    color = Color.LightGray,
                    modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                )
                Text(
                    text = "Your personal Persian content streaming app.",
                    color = Color.White,
                    fontSize = if (isPhone) 14.sp else 16.sp
                )
                Text(
                    text = "Sources: Farsiland, FarsiPlex, Namakade",
                    color = Color.Gray,
                    fontSize = if (isPhone) 12.sp else 14.sp,
                    modifier = Modifier.padding(top = 8.dp, bottom = 16.dp)
                )

                if (isPhone) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("OK", color = Color.White)
                    }
                } else {
                    androidx.tv.material3.Button(
                        onClick = onDismiss,
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("OK")
                    }
                }
            }
        }
    }
}

/**
 * IMVBox Login Dialog
 * Allows users to login to their IMVBox account for authenticated video access
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun IMVBoxLoginDialog(
    context: Context,
    isLoggedIn: Boolean,
    currentEmail: String,
    isPhone: Boolean,
    onDismiss: () -> Unit,
    onLoginSuccess: (String) -> Unit,
    onLogout: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var email by remember { mutableStateOf(currentEmail) }
    var password by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = { if (!isLoading) onDismiss() }) {
        Surface(
            modifier = Modifier
                .width(if (isPhone) 320.dp else 400.dp)
                .wrapContentHeight(),
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFF2A2A2A),
            tonalElevation = 8.dp
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Header
                Text(
                    text = if (isLoggedIn) "IMVBox Account" else "Login to IMVBox",
                    color = Color.White,
                    fontSize = if (isPhone) 18.sp else 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                if (isLoggedIn) {
                    // Show logged in state
                    Text(
                        text = "Currently logged in as:",
                        color = Color.LightGray,
                        fontSize = 14.sp
                    )
                    Text(
                        text = currentEmail,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Logout button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        if (isPhone) {
                            TextButton(onClick = onDismiss) {
                                Text("Close", color = Color.White)
                            }
                            Button(
                                onClick = { onLogout() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
                            ) {
                                Text("Logout", color = Color.White)
                            }
                        } else {
                            androidx.tv.material3.Button(onClick = onDismiss) {
                                Text("Close")
                            }
                            androidx.tv.material3.Button(
                                onClick = { onLogout() },
                                colors = androidx.tv.material3.ButtonDefaults.colors(containerColor = Color(0xFFD32F2F))
                            ) {
                                Text("Logout")
                            }
                        }
                    }
                } else {
                    // Login form
                    Text(
                        text = "Login to access premium IMVBox content",
                        color = Color.LightGray,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    // Email field
                    androidx.compose.material3.OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Email") },
                        singleLine = true,
                        enabled = !isLoading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF4CAF50),
                            unfocusedBorderColor = Color.Gray,
                            focusedLabelColor = Color(0xFF4CAF50),
                            unfocusedLabelColor = Color.Gray
                        )
                    )

                    // Password field
                    androidx.compose.material3.OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password") },
                        singleLine = true,
                        enabled = !isLoading,
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF4CAF50),
                            unfocusedBorderColor = Color.Gray,
                            focusedLabelColor = Color(0xFF4CAF50),
                            unfocusedLabelColor = Color.Gray
                        )
                    )

                    // Error message
                    errorMessage?.let { error ->
                        Text(
                            text = error,
                            color = Color(0xFFFF5252),
                            fontSize = 13.sp,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }

                    // Loading indicator
                    if (isLoading) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            androidx.compose.material3.CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = Color(0xFF4CAF50)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Logging in...", color = Color.LightGray, fontSize = 14.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Action buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        if (isPhone) {
                            TextButton(
                                onClick = onDismiss,
                                enabled = !isLoading
                            ) {
                                Text("Cancel", color = Color.White)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    if (email.isNotBlank() && password.isNotBlank()) {
                                        isLoading = true
                                        errorMessage = null
                                        coroutineScope.launch {
                                            val result = IMVBoxAuthManager.login(context, email, password)
                                            isLoading = false
                                            when (result) {
                                                is IMVBoxAuthManager.LoginResult.Success -> {
                                                    onLoginSuccess(email)
                                                }
                                                is IMVBoxAuthManager.LoginResult.Error -> {
                                                    errorMessage = result.message
                                                }
                                            }
                                        }
                                    } else {
                                        errorMessage = "Please enter email and password"
                                    }
                                },
                                enabled = !isLoading && email.isNotBlank() && password.isNotBlank(),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                            ) {
                                Text("Login", color = Color.White)
                            }
                        } else {
                            androidx.tv.material3.Button(
                                onClick = onDismiss,
                                enabled = !isLoading
                            ) {
                                Text("Cancel")
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            androidx.tv.material3.Button(
                                onClick = {
                                    if (email.isNotBlank() && password.isNotBlank()) {
                                        isLoading = true
                                        errorMessage = null
                                        coroutineScope.launch {
                                            val result = IMVBoxAuthManager.login(context, email, password)
                                            isLoading = false
                                            when (result) {
                                                is IMVBoxAuthManager.LoginResult.Success -> {
                                                    onLoginSuccess(email)
                                                }
                                                is IMVBoxAuthManager.LoginResult.Error -> {
                                                    errorMessage = result.message
                                                }
                                            }
                                        }
                                    } else {
                                        errorMessage = "Please enter email and password"
                                    }
                                },
                                enabled = !isLoading && email.isNotBlank() && password.isNotBlank(),
                                colors = androidx.tv.material3.ButtonDefaults.colors(containerColor = Color(0xFF4CAF50))
                            ) {
                                Text("Login")
                            }
                        }
                    }
                }
            }
        }
    }
}

// Phase 5: Scraper Health Alert Component
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ScraperHealthAlert(
    healthStatus: Map<ScraperHealthTracker.ScraperSource, ScraperHealthTracker.ScraperHealth>,
    isPhone: Boolean,
    onResetClick: () -> Unit
) {
    val unhealthyScrapers = healthStatus.values.filter { !it.isHealthy || it.isStale }

    if (isPhone) {
        // Phone: Use Material 3 Card with clickable
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .clickable { onResetClick() },
            colors = CardDefaults.cardColors(containerColor = Color(0xFF4A1A1A)),
            shape = RoundedCornerShape(8.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    androidx.compose.material3.Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = Color(0xFFFFB74D),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Content Source Issues",
                        color = Color(0xFFFFB74D),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                unhealthyScrapers.forEach { health ->
                    val statusText = when {
                        health.isStale -> "No updates in 24+ hours"
                        !health.isHealthy -> "${(health.successRate * 100).toInt()}% success rate"
                        else -> "Unknown issue"
                    }
                    Text(
                        text = "• ${health.source.name}: $statusText",
                        color = Color.White,
                        fontSize = 13.sp
                    )
                    health.lastErrorMessage?.let { msg ->
                        Text(
                            text = "  Last error: ${msg.take(40)}${if (msg.length > 40) "..." else ""}",
                            color = Color.Gray,
                            fontSize = 11.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Tap to reset counters",
                    color = Color.Gray,
                    fontSize = 11.sp
                )
            }
        }
    } else {
        // TV: Use TV Material 3 Surface
        androidx.tv.material3.Surface(
            onClick = onResetClick,
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Color(0xFF4A1A1A),
                focusedContainerColor = Color(0xFF6A2A2A)
            ),
            shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(8.dp))
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = Color(0xFFFFB74D),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Content Source Issues",
                        color = Color(0xFFFFB74D),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                unhealthyScrapers.forEach { health ->
                    val statusText = when {
                        health.isStale -> "No updates in 24+ hours"
                        !health.isHealthy -> "${(health.successRate * 100).toInt()}% success rate"
                        else -> "Unknown issue"
                    }
                    Text(
                        text = "• ${health.source.name}: $statusText",
                        color = Color.White,
                        fontSize = 14.sp
                    )
                    health.lastErrorMessage?.let { msg ->
                        Text(
                            text = "  Last error: ${msg.take(50)}${if (msg.length > 50) "..." else ""}",
                            color = Color.Gray,
                            fontSize = 12.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Press to reset counters",
                    color = Color.Gray,
                    fontSize = 12.sp
                )
            }
        }
    }
}

// Helper functions
private fun getFrequencyText(minutes: Long): String = when (minutes) {
    15L -> "Every 15 minutes"
    30L -> "Every 30 minutes"
    60L -> "Every hour"
    1440L -> "Daily"
    else -> "Every $minutes minutes"
}

private fun getLastSyncText(timestamp: Long): String {
    if (timestamp <= 0) return "Never synced"

    val dateFormat = SimpleDateFormat("MMM dd, hh:mm a", Locale.US)
    val timeAgo = getTimeAgo(timestamp)
    return "Last: ${dateFormat.format(Date(timestamp))} ($timeAgo)"
}

private fun getTimeAgo(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    val minutes = diff / 60000
    val hours = minutes / 60
    val days = hours / 24

    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "${minutes}m ago"
        hours < 24 -> "${hours}h ago"
        else -> "${days}d ago"
    }
}

private fun getSourceDescription(source: DatabaseSource): String = when (source) {
    DatabaseSource.FARSILAND -> "Original content library"
    DatabaseSource.FARSIPLEX -> "36 movies, 34 TV shows, 558 episodes"
    DatabaseSource.NAMAKADE -> "312 movies, 923 series, 19,373 episodes"
    DatabaseSource.IMVBOX -> "Persian movies & series (syncs from web)"
}

private fun triggerManualSync(context: Context, onComplete: (Boolean) -> Unit) {
    val workManager = WorkManager.getInstance(context)

    val farsilandRequest = OneTimeWorkRequestBuilder<ContentSyncWorker>().build()
    val farsiPlexRequest = OneTimeWorkRequestBuilder<FarsiPlexSyncWorker>().build()
    val imvboxRequest = OneTimeWorkRequestBuilder<IMVBoxSyncWorker>().build()

    workManager.enqueue(farsilandRequest)
    workManager.enqueue(farsiPlexRequest)
    workManager.enqueue(imvboxRequest)

    CoroutineScope(Dispatchers.Main).launch {
        kotlinx.coroutines.delay(5000)
        onComplete(true)
    }
}

private fun clearCache(context: Context) {
    try {
        val imageLoader = ImageLoader(context)
        imageLoader.memoryCache?.clear()
        context.cacheDir.deleteRecursively()
        context.externalCacheDir?.deleteRecursively()
        File(context.cacheDir, "http_cache").deleteRecursively()
    } catch (e: Exception) {
        // Ignore errors
    }
}

private fun clearWatchHistory(context: Context, scope: CoroutineScope) {
    scope.launch(Dispatchers.IO) {
        try {
            val appDb = AppDatabase.getDatabase(context)
            appDb.playbackPositionDao().clearAll()
            appDb.runInTransaction {
                appDb.openHelper.writableDatabase.execSQL("DELETE FROM watchlist_movies")
                appDb.openHelper.writableDatabase.execSQL("DELETE FROM episode_progress")
            }
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Watch history cleared", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

private fun performFullResync(context: Context, prefs: android.content.SharedPreferences) {
    prefs.edit()
        .putBoolean("content_db_initialized", false)
        .putBoolean("force_db_copy", true)
        .apply()
    Toast.makeText(context, "Database will reset on next app start", Toast.LENGTH_LONG).show()
}
