package me.jaival.5g.ui.home

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import me.jaival.5g.BuildConfig
import me.jaival.5g.data.HotspotMode
import me.jaival.5g.data.SettingsRepository
import me.jaival.5g.service.Smart5GService
import me.jaival.5g.system.TrafficMonitor
import me.jaival.5g.updater.UpdateManager
import me.jaival.5g.updater.UpdateManagerImpl

data class AppItemInfo(
    val packageName: String,
    val appName: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    repository: SettingsRepository,
    onNavigateToAbout: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val masterEnabled by repository.masterSwitchFlow.collectAsState(initial = false)
    val displayTriggerEnabled by repository.displayTriggerEnabledFlow.collectAsState(initial = true)
    val displayOffDelaySecs by repository.displayOffDelaySecsFlow.collectAsState(initial = 10)
    val displayOnDelaySecs by repository.displayOnDelaySecsFlow.collectAsState(initial = 2)

    val smartSwitchingEnabled by repository.smartSwitchingEnabledFlow.collectAsState(initial = true)
    val highMbpsThreshold by repository.highMbpsThresholdFlow.collectAsState(initial = 5.0)
    val lowMbpsThreshold by repository.lowMbpsThresholdFlow.collectAsState(initial = 1.0)

    val whitelistPackages by repository.whitelistPackagesFlow.collectAsState(initial = emptySet())
    val blacklistPackages by repository.blacklistPackagesFlow.collectAsState(initial = emptySet())

    val hotspotTriggerEnabled by repository.hotspotTriggerEnabledFlow.collectAsState(initial = true)
    val hotspotMode by repository.hotspotModeFlow.collectAsState(initial = HotspotMode.SMART)
    val hotspotOnly5G by repository.hotspotOnly5GFlow.collectAsState(initial = false)

    val updaterEnabled by repository.updaterEnabledFlow.collectAsState(initial = true)
    val includePrereleases by repository.includePrereleasesFlow.collectAsState(initial = false)

    var liveMbps by remember { mutableStateOf(0.0) }
    var showAppPickerFor by remember { mutableStateOf<String?>(null) } // "whitelist" or "blacklist"

    val updateManager: UpdateManager = remember { UpdateManagerImpl() }
    val updateState by updateManager.updateState.collectAsState()

    LaunchedEffect(Unit) {
        TrafficMonitor.observeTrafficMbps(1000L).collect { mbps ->
            liveMbps = mbps
        }
    }

    LaunchedEffect(masterEnabled) {
        val serviceIntent = Intent(context, Smart5GService::class.java)
        if (masterEnabled) {
            context.startForegroundService(serviceIntent)
        } else {
            context.stopService(serviceIntent)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Auto 5G Dashboard",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                },
                actions = {
                    IconButton(onClick = onNavigateToAbout) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "About Auto 5G"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Master Switch Card
            MasterSwitchCard(
                masterEnabled = masterEnabled,
                onToggle = { enabled ->
                    scope.launch { repository.setMasterSwitch(enabled) }
                }
            )

            // Display Trigger Card
            DisplayTriggerCard(
                enabled = displayTriggerEnabled,
                onToggle = { enabled -> scope.launch { repository.setDisplayTriggerEnabled(enabled) } },
                offDelaySecs = displayOffDelaySecs,
                onOffDelayChange = { secs -> scope.launch { repository.setDisplayOffDelaySecs(secs) } },
                onDelaySecs = displayOnDelaySecs,
                onOnDelayChange = { secs -> scope.launch { repository.setDisplayOnDelaySecs(secs) } }
            )

            // Smart Switching Card
            SmartSwitchingCard(
                enabled = smartSwitchingEnabled,
                onToggle = { enabled -> scope.launch { repository.setSmartSwitchingEnabled(enabled) } },
                liveMbps = liveMbps,
                highThreshold = highMbpsThreshold,
                onHighChange = { thresh -> scope.launch { repository.setHighMbpsThreshold(thresh) } },
                lowThreshold = lowMbpsThreshold,
                onLowChange = { thresh -> scope.launch { repository.setLowMbpsThreshold(thresh) } }
            )

            // Whitelist / Blacklist Card
            AppListCard(
                whitelistPackages = whitelistPackages,
                blacklistPackages = blacklistPackages,
                onOpenWhitelistPicker = { showAppPickerFor = "whitelist" },
                onOpenBlacklistPicker = { showAppPickerFor = "blacklist" },
                onRemoveWhitelist = { pkg ->
                    scope.launch { repository.setWhitelistPackages(whitelistPackages - pkg) }
                },
                onRemoveBlacklist = { pkg ->
                    scope.launch { repository.setBlacklistPackages(blacklistPackages - pkg) }
                }
            )

            // Hotspot Card
            HotspotCard(
                enabled = hotspotTriggerEnabled,
                onToggle = { enabled -> scope.launch { repository.setHotspotTriggerEnabled(enabled) } },
                hotspotMode = hotspotMode,
                onModeChange = { mode -> scope.launch { repository.setHotspotMode(mode) } },
                only5G = hotspotOnly5G,
                onOnly5GChange = { only5g -> scope.launch { repository.setHotspotOnly5G(only5g) } }
            )

            // GitHub Flavor Updater Card
            if (BuildConfig.ENABLE_UPDATER) {
                UpdaterCard(
                    enabled = updaterEnabled,
                    onToggleEnabled = { enabled -> scope.launch { repository.setUpdaterEnabled(enabled) } },
                    includePrereleases = includePrereleases,
                    onTogglePrereleases = { include -> scope.launch { repository.setIncludePrereleases(include) } },
                    updateState = updateState,
                    onCheckNow = {
                        scope.launch { updateManager.checkForUpdates(context, force = true) }
                    }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        // App Selection Dialog
        if (showAppPickerFor != null) {
            AppSelectionDialog(
                context = context,
                currentSelected = if (showAppPickerFor == "whitelist") whitelistPackages else blacklistPackages,
                onDismiss = { showAppPickerFor = null },
                onConfirm = { selected ->
                    scope.launch {
                        if (showAppPickerFor == "whitelist") {
                            repository.setWhitelistPackages(selected)
                        } else {
                            repository.setBlacklistPackages(selected)
                        }
                        showAppPickerFor = null
                    }
                }
            )
        }
    }
}

@Composable
private fun MasterSwitchCard(
    masterEnabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    val animatedBg by animateColorAsState(
        targetValue = if (masterEnabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        animationSpec = spring()
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = animatedBg)
    ) {
        Row(
            modifier = Modifier
                .padding(20.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (masterEnabled) "Auto 5G Service Active" else "Auto 5G Service Disabled",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = if (masterEnabled) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (masterEnabled) "Background service is actively controlling network modes." else "Tap switch to enable automated network switching.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (masterEnabled) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            }
            Switch(
                checked = masterEnabled,
                onCheckedChange = onToggle
            )
        }
    }
}

@Composable
private fun DisplayTriggerCard(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    offDelaySecs: Int,
    onOffDelayChange: (Int) -> Unit,
    onDelaySecs: Int,
    onOnDelayChange: (Int) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Display Trigger", style = MaterialTheme.typography.titleMedium)
                    Text("Switch to 4G on screen off, 5G on screen on.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = enabled, onCheckedChange = onToggle)
            }

            AnimatedVisibility(
                visible = enabled,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    Text("Screen Off Delay: ${offDelaySecs}s", style = MaterialTheme.typography.labelLarge)
                    Slider(
                        value = offDelaySecs.toFloat(),
                        onValueChange = { onOffDelayChange(it.toInt()) },
                        valueRange = 1f..60f,
                        steps = 59
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text("Screen On Delay: ${onDelaySecs}s", style = MaterialTheme.typography.labelLarge)
                    Slider(
                        value = onDelaySecs.toFloat(),
                        onValueChange = { onOnDelayChange(it.toInt()) },
                        valueRange = 0f..30f,
                        steps = 30
                    )
                }
            }
        }
    }
}

@Composable
private fun SmartSwitchingCard(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    liveMbps: Double,
    highThreshold: Double,
    onHighChange: (Double) -> Unit,
    lowThreshold: Double,
    onLowChange: (Double) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Smart Bandwidth Switching", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "Current Throughput: %.2f Mbps".format(liveMbps),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Switch(checked = enabled, onCheckedChange = onToggle)
            }

            AnimatedVisibility(
                visible = enabled,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    Text("Switch to 5G Threshold: %.1f Mbps".format(highThreshold), style = MaterialTheme.typography.labelLarge)
                    Slider(
                        value = highThreshold.toFloat(),
                        onValueChange = { onHighChange(it.toDouble()) },
                        valueRange = 1f..50f
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text("Switch to 4G Threshold: %.1f Mbps".format(lowThreshold), style = MaterialTheme.typography.labelLarge)
                    Slider(
                        value = lowThreshold.toFloat(),
                        onValueChange = { onLowChange(it.toDouble()) },
                        valueRange = 0.1f..10f
                    )
                }
            }
        }
    }
}

@Composable
private fun AppListCard(
    whitelistPackages: Set<String>,
    blacklistPackages: Set<String>,
    onOpenWhitelistPicker: () -> Unit,
    onOpenBlacklistPicker: () -> Unit,
    onRemoveWhitelist: (String) -> Unit,
    onRemoveBlacklist: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("App Whitelist / Blacklist", style = MaterialTheme.typography.titleMedium)
            Text("Force 5G for whitelisted apps or 4G for blacklisted apps.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

            Spacer(modifier = Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = onOpenWhitelistPicker,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Whitelist (${whitelistPackages.size})")
                }
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedButton(
                    onClick = onOpenBlacklistPicker,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Blacklist (${blacklistPackages.size})")
                }
            }
        }
    }
}

@Composable
private fun HotspotCard(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    hotspotMode: HotspotMode,
    onModeChange: (HotspotMode) -> Unit,
    only5G: Boolean,
    onOnly5GChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Hotspot Detection", style = MaterialTheme.typography.titleMedium)
                    Text("Automatically handle network mode when Wi-Fi Hotspot is active.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = enabled, onCheckedChange = onToggle)
            }

            AnimatedVisibility(
                visible = enabled,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    Text("Hotspot Mode:", style = MaterialTheme.typography.labelLarge)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { onModeChange(HotspotMode.SMART) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp)
                        ) {
                            Text(
                                "Smart",
                                fontWeight = if (hotspotMode == HotspotMode.SMART) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                        OutlinedButton(
                            onClick = { onModeChange(HotspotMode.ALWAYS) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(topEnd = 12.dp, bottomEnd = 12.dp)
                        ) {
                            Text(
                                "Always 5G",
                                fontWeight = if (hotspotMode == HotspotMode.ALWAYS) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }

                    if (hotspotMode == HotspotMode.ALWAYS) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = only5G, onCheckedChange = onOnly5GChange)
                            Text("Strict 5G Only (No 4G fallback)", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UpdaterCard(
    enabled: Boolean,
    onToggleEnabled: (Boolean) -> Unit,
    includePrereleases: Boolean,
    onTogglePrereleases: (Boolean) -> Unit,
    updateState: me.jaival.5g.updater.UpdateInfo,
    onCheckNow: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("In-App Updater", style = MaterialTheme.typography.titleMedium)
                    Text("Check GitHub releases automatically.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = enabled, onCheckedChange = onToggleEnabled)
            }

            AnimatedVisibility(
                visible = enabled,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = includePrereleases, onCheckedChange = onTogglePrereleases)
                        Text("Include Pre-releases (Alpha/Beta)", style = MaterialTheme.typography.bodyMedium)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (updateState.hasUpdate) {
                        Text(
                            "New Version Available: v${updateState.latestVersion}",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else if (updateState.errorMessage != null) {
                        Text(
                            "Error checking update: ${updateState.errorMessage}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = onCheckNow,
                        enabled = !updateState.isChecking,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (updateState.isChecking) "Checking..." else "Check for Updates Now")
                    }
                }
            }
        }
    }
}

@Composable
private fun AppSelectionDialog(
    context: Context,
    currentSelected: Set<String>,
    onDismiss: () -> Unit,
    onConfirm: (Set<String>) -> Unit
) {
    val installedApps = remember {
        val pm = context.packageManager
        pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 }
            .map { AppItemInfo(it.packageName, pm.getApplicationLabel(it).toString()) }
            .sortedBy { it.appName }
    }

    var tempSelected by remember { mutableStateOf(currentSelected) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Applications") },
        text = {
            LazyColumn(modifier = Modifier.height(300.dp)) {
                items(installedApps) { app ->
                    val isChecked = tempSelected.contains(app.packageName)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                tempSelected = if (isChecked) tempSelected - app.packageName else tempSelected + app.packageName
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = isChecked,
                            onCheckedChange = { checked ->
                                tempSelected = if (checked) tempSelected + app.packageName else tempSelected - app.packageName
                            }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(app.appName, style = MaterialTheme.typography.bodyLarge)
                            Text(app.packageName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(tempSelected) }) {
                Text("Save")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
