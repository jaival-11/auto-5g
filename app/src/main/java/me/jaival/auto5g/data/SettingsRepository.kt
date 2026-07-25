package me.jaival.auto5g.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "auto_5g_settings")

class SettingsRepository(private val context: Context) {

    companion object {
        val MASTER_SWITCH = booleanPreferencesKey("master_switch")
        val PERMISSION_MODE = stringPreferencesKey("permission_mode")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")

        val DISPLAY_TRIGGER_ENABLED = booleanPreferencesKey("display_trigger_enabled")
        val DISPLAY_OFF_DELAY_SECS = intPreferencesKey("display_off_delay_secs")
        val DISPLAY_ON_DELAY_SECS = intPreferencesKey("display_on_delay_secs")

        val SMART_SWITCHING_ENABLED = booleanPreferencesKey("smart_switching_enabled")
        val HIGH_MBPS_THRESHOLD = doublePreferencesKey("high_mbps_threshold")
        val LOW_MBPS_THRESHOLD = doublePreferencesKey("low_mbps_threshold")
        val HIGH_MBPS_DURATION_SECS = intPreferencesKey("high_mbps_duration_secs")
        val LOW_MBPS_DURATION_SECS = intPreferencesKey("low_mbps_duration_secs")

        val WHITELIST_PACKAGES = stringSetPreferencesKey("whitelist_packages")
        val BLACKLIST_PACKAGES = stringSetPreferencesKey("blacklist_packages")

        val HOTSPOT_TRIGGER_ENABLED = booleanPreferencesKey("hotspot_trigger_enabled")
        val HOTSPOT_MODE = stringPreferencesKey("hotspot_mode")
        val HOTSPOT_ONLY_5G = booleanPreferencesKey("hotspot_only_5g")

        val UPDATER_ENABLED = booleanPreferencesKey("updater_enabled")
        val INCLUDE_PRERELEASES = booleanPreferencesKey("include_prereleases")
        val LAST_CHECKED_TIMESTAMP = longPreferencesKey("last_checked_timestamp")
    }

    val masterSwitchFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[MASTER_SWITCH] ?: false
    }

    val onboardingCompletedFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[ONBOARDING_COMPLETED] ?: false
    }

    val permissionModeFlow: Flow<PermissionMode> = context.dataStore.data.map { prefs ->
        val modeStr = prefs[PERMISSION_MODE] ?: PermissionMode.SHIZUKU_ONETIME.name
        runCatching { PermissionMode.valueOf(modeStr) }.getOrDefault(PermissionMode.SHIZUKU_ONETIME)
    }

    val displayTriggerEnabledFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[DISPLAY_TRIGGER_ENABLED] ?: true
    }

    val displayOffDelaySecsFlow: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[DISPLAY_OFF_DELAY_SECS] ?: 10
    }

    val displayOnDelaySecsFlow: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[DISPLAY_ON_DELAY_SECS] ?: 2
    }

    val smartSwitchingEnabledFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[SMART_SWITCHING_ENABLED] ?: true
    }

    val highMbpsThresholdFlow: Flow<Double> = context.dataStore.data.map { prefs ->
        prefs[HIGH_MBPS_THRESHOLD] ?: 5.0
    }

    val lowMbpsThresholdFlow: Flow<Double> = context.dataStore.data.map { prefs ->
        prefs[LOW_MBPS_THRESHOLD] ?: 1.0
    }

    val highMbpsDurationSecsFlow: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[HIGH_MBPS_DURATION_SECS] ?: 3
    }

    val lowMbpsDurationSecsFlow: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[LOW_MBPS_DURATION_SECS] ?: 10
    }

    val whitelistPackagesFlow: Flow<Set<String>> = context.dataStore.data.map { prefs ->
        prefs[WHITELIST_PACKAGES] ?: emptySet()
    }

    val blacklistPackagesFlow: Flow<Set<String>> = context.dataStore.data.map { prefs ->
        prefs[BLACKLIST_PACKAGES] ?: emptySet()
    }

    val hotspotTriggerEnabledFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[HOTSPOT_TRIGGER_ENABLED] ?: true
    }

    val hotspotModeFlow: Flow<HotspotMode> = context.dataStore.data.map { prefs ->
        val modeStr = prefs[HOTSPOT_MODE] ?: HotspotMode.SMART.name
        runCatching { HotspotMode.valueOf(modeStr) }.getOrDefault(HotspotMode.SMART)
    }

    val hotspotOnly5GFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[HOTSPOT_ONLY_5G] ?: false
    }

    val updaterEnabledFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[UPDATER_ENABLED] ?: true
    }

    val includePrereleasesFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[INCLUDE_PRERELEASES] ?: false
    }

    val lastCheckedTimestampFlow: Flow<Long> = context.dataStore.data.map { prefs ->
        prefs[LAST_CHECKED_TIMESTAMP] ?: 0L
    }

    suspend fun setMasterSwitch(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[MASTER_SWITCH] = enabled }
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.dataStore.edit { prefs -> prefs[ONBOARDING_COMPLETED] = completed }
    }

    suspend fun setPermissionMode(mode: PermissionMode) {
        context.dataStore.edit { prefs -> prefs[PERMISSION_MODE] = mode.name }
    }

    suspend fun setDisplayTriggerEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[DISPLAY_TRIGGER_ENABLED] = enabled }
    }

    suspend fun setDisplayOffDelaySecs(secs: Int) {
        context.dataStore.edit { prefs -> prefs[DISPLAY_OFF_DELAY_SECS] = secs }
    }

    suspend fun setDisplayOnDelaySecs(secs: Int) {
        context.dataStore.edit { prefs -> prefs[DISPLAY_ON_DELAY_SECS] = secs }
    }

    suspend fun setSmartSwitchingEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[SMART_SWITCHING_ENABLED] = enabled }
    }

    suspend fun setHighMbpsThreshold(threshold: Double) {
        context.dataStore.edit { prefs -> prefs[HIGH_MBPS_THRESHOLD] = threshold }
    }

    suspend fun setLowMbpsThreshold(threshold: Double) {
        context.dataStore.edit { prefs -> prefs[LOW_MBPS_THRESHOLD] = threshold }
    }

    suspend fun setHighMbpsDurationSecs(secs: Int) {
        context.dataStore.edit { prefs -> prefs[HIGH_MBPS_DURATION_SECS] = secs }
    }

    suspend fun setLowMbpsDurationSecs(secs: Int) {
        context.dataStore.edit { prefs -> prefs[LOW_MBPS_DURATION_SECS] = secs }
    }

    suspend fun setWhitelistPackages(packages: Set<String>) {
        context.dataStore.edit { prefs -> prefs[WHITELIST_PACKAGES] = packages }
    }

    suspend fun setBlacklistPackages(packages: Set<String>) {
        context.dataStore.edit { prefs -> prefs[BLACKLIST_PACKAGES] = packages }
    }

    suspend fun setHotspotTriggerEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[HOTSPOT_TRIGGER_ENABLED] = enabled }
    }

    suspend fun setHotspotMode(mode: HotspotMode) {
        context.dataStore.edit { prefs -> prefs[HOTSPOT_MODE] = mode.name }
    }

    suspend fun setHotspotOnly5G(only5G: Boolean) {
        context.dataStore.edit { prefs -> prefs[HOTSPOT_ONLY_5G] = only5G }
    }

    suspend fun setUpdaterEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[UPDATER_ENABLED] = enabled }
    }

    suspend fun setIncludePrereleases(include: Boolean) {
        context.dataStore.edit { prefs -> prefs[INCLUDE_PRERELEASES] = include }
    }

    suspend fun setLastCheckedTimestamp(timestamp: Long) {
        context.dataStore.edit { prefs -> prefs[LAST_CHECKED_TIMESTAMP] = timestamp }
    }
}
