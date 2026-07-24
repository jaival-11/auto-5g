package me.jaival.auto5g.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import me.jaival.auto5g.MainActivity
import me.jaival.auto5g.R
import me.jaival.auto5g.data.HotspotMode
import me.jaival.auto5g.data.PermissionMode
import me.jaival.auto5g.data.SettingsRepository
import me.jaival.auto5g.system.NetworkModeController
import me.jaival.auto5g.system.TrafficMonitor

class Smart5GService : Service() {

    companion object {
        const val CHANNEL_ID = "auto_5g_service_channel"
        const val NOTIFICATION_ID = 5001
        const val ACTION_START = "me.jaival.auto5g.ACTION_START"
        const val ACTION_STOP = "me.jaival.auto5g.ACTION_STOP"
    }

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private lateinit var repository: SettingsRepository

    private var isScreenOn = true
    private var isHotspotActive = false
    private var currentLiveMbps = 0.0

    private var displayOffJob: Job? = null
    private var displayOnJob: Job? = null

    // State cached from DataStore
    private var masterEnabled = false
    private var permissionMode = PermissionMode.SHIZUKU_ONETIME
    private var displayTriggerEnabled = true
    private var displayOffDelaySecs = 10
    private var displayOnDelaySecs = 2
    private var smartSwitchingEnabled = true
    private var highMbpsThreshold = 5.0
    private var lowMbpsThreshold = 1.0
    private var whitelistPackages = emptySet<String>()
    private var blacklistPackages = emptySet<String>()
    private var hotspotTriggerEnabled = true
    private var hotspotMode = HotspotMode.SMART
    private var hotspotOnly5G = false
    private var lastAppliedMode: Int? = null

    private val systemReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    isScreenOn = false
                    handleScreenStateChange(false)
                }
                Intent.ACTION_SCREEN_ON -> {
                    isScreenOn = true
                    handleScreenStateChange(true)
                }
                "android.net.wifi.WIFI_AP_STATE_CHANGED" -> {
                    val state = intent.getIntExtra("wifi_state", 0)
                    // 13 is WIFI_AP_STATE_ENABLED
                    isHotspotActive = (state == 13)
                    evaluateAndApplyNetworkMode()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        repository = SettingsRepository(applicationContext)
        createNotificationChannel()

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction("android.net.wifi.WIFI_AP_STATE_CHANGED")
        }
        registerReceiver(systemReceiver, filter)

        observeSettings()
        observeTraffic()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        startForegroundServiceNotification()
        applyTargetMode(NetworkModeController.NETWORK_MODE_4G_PREFERRED, force = true)
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(systemReceiver)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun startForegroundServiceNotification() {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Auto 5G Active")
            .setContentText("Monitoring network status and managing 5G/4G switching.")
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Auto 5G Service Channel",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows service status notification for Auto 5G background operations."
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun observeSettings() {
        serviceScope.launch {
            combine(
                repository.masterSwitchFlow,
                repository.permissionModeFlow,
                repository.displayTriggerEnabledFlow,
                repository.displayOffDelaySecsFlow,
                repository.displayOnDelaySecsFlow,
                repository.smartSwitchingEnabledFlow,
                repository.highMbpsThresholdFlow,
                repository.lowMbpsThresholdFlow
            ) { flows: Array<Any?> ->
                val newMaster = flows[0] as Boolean
                permissionMode = flows[1] as PermissionMode
                displayTriggerEnabled = flows[2] as Boolean
                displayOffDelaySecs = flows[3] as Int
                displayOnDelaySecs = flows[4] as Int
                smartSwitchingEnabled = flows[5] as Boolean
                highMbpsThreshold = flows[6] as Double
                lowMbpsThreshold = flows[7] as Double
                newMaster
            }.collectLatest { isMaster ->
                val wasEnabled = masterEnabled
                masterEnabled = isMaster
                if (!masterEnabled) {
                    stopSelf()
                } else {
                    if (!wasEnabled) {
                        applyTargetMode(NetworkModeController.NETWORK_MODE_4G_PREFERRED, force = true)
                    } else {
                        evaluateAndApplyNetworkMode()
                    }
                }
            }
        }

        serviceScope.launch {
            combine(
                repository.whitelistPackagesFlow,
                repository.blacklistPackagesFlow,
                repository.hotspotTriggerEnabledFlow,
                repository.hotspotModeFlow,
                repository.hotspotOnly5GFlow
            ) { whitelist, blacklist, hsEnabled, hsMode, hsOnly5G ->
                whitelistPackages = whitelist
                blacklistPackages = blacklist
                hotspotTriggerEnabled = hsEnabled
                hotspotMode = hsMode
                hotspotOnly5G = hsOnly5G
            }.collectLatest {
                evaluateAndApplyNetworkMode()
            }
        }
    }

    private fun observeTraffic() {
        serviceScope.launch {
            TrafficMonitor.observeTrafficMbps(1000L).collect { mbps ->
                currentLiveMbps = mbps
                if (masterEnabled) {
                    evaluateAndApplyNetworkMode()
                }
            }
        }
    }

    private fun handleScreenStateChange(screenOn: Boolean) {
        displayOffJob?.cancel()
        displayOnJob?.cancel()

        if (!displayTriggerEnabled) {
            evaluateAndApplyNetworkMode()
            return
        }

        if (screenOn) {
            displayOnJob = serviceScope.launch {
                delay(displayOnDelaySecs * 1000L)
                evaluateAndApplyNetworkMode()
            }
        } else {
            displayOffJob = serviceScope.launch {
                delay(displayOffDelaySecs * 1000L)
                evaluateAndApplyNetworkMode()
            }
        }
    }

    private fun evaluateAndApplyNetworkMode() {
        if (!masterEnabled) return

        val foregroundApp = getForegroundAppPackage()

        // 1. Whitelist/Blacklist rules
        if (foregroundApp != null) {
            if (whitelistPackages.contains(foregroundApp)) {
                applyTargetMode(NetworkModeController.NETWORK_MODE_5G_PREFERRED)
                return
            }
            if (blacklistPackages.contains(foregroundApp)) {
                applyTargetMode(NetworkModeController.NETWORK_MODE_4G_PREFERRED)
                return
            }
        }

        // 2. Hotspot rules
        if (hotspotTriggerEnabled && isHotspotActive) {
            if (hotspotMode == HotspotMode.ALWAYS) {
                val targetMode = if (hotspotOnly5G) {
                    NetworkModeController.NETWORK_MODE_STRICT_5G_ONLY
                } else {
                    NetworkModeController.NETWORK_MODE_5G_PREFERRED
                }
                applyTargetMode(targetMode)
                return
            } else if (hotspotMode == HotspotMode.SMART) {
                if (currentLiveMbps >= highMbpsThreshold) {
                    applyTargetMode(NetworkModeController.NETWORK_MODE_5G_PREFERRED)
                    return
                } else if (currentLiveMbps <= lowMbpsThreshold) {
                    applyTargetMode(NetworkModeController.NETWORK_MODE_4G_PREFERRED)
                    return
                }
            }
        }

        // 3. Smart Switching Traffic rules (Screen Off Heavy Download Exception)
        if (!isScreenOn && smartSwitchingEnabled && currentLiveMbps >= highMbpsThreshold) {
            applyTargetMode(NetworkModeController.NETWORK_MODE_5G_PREFERRED)
            return
        }

        // 4. Display state rules
        if (displayTriggerEnabled && !isScreenOn) {
            applyTargetMode(NetworkModeController.NETWORK_MODE_4G_PREFERRED)
            return
        }

        // 5. Smart Switching default rules during Display ON
        if (smartSwitchingEnabled) {
            if (currentLiveMbps >= highMbpsThreshold) {
                applyTargetMode(NetworkModeController.NETWORK_MODE_5G_PREFERRED)
                return
            } else if (currentLiveMbps <= lowMbpsThreshold) {
                applyTargetMode(NetworkModeController.NETWORK_MODE_4G_PREFERRED)
                return
            }
        }

        // Default when display is ON and no heavy traffic: 5G Preferred
        applyTargetMode(NetworkModeController.NETWORK_MODE_5G_PREFERRED)
    }

    private fun applyTargetMode(mode: Int, force: Boolean = false) {
        if (force || lastAppliedMode != mode) {
            val success = NetworkModeController.setNetworkMode(applicationContext, mode, permissionMode)
            if (success || force) {
                lastAppliedMode = mode
            }
        }
    }

    private fun getForegroundAppPackage(): String? {
        val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return null
        val time = System.currentTimeMillis()
        val events = usageStatsManager.queryEvents(time - 5000, time)
        var lastForegroundApp: String? = null
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                lastForegroundApp = event.packageName
            }
        }
        return lastForegroundApp
    }
}
