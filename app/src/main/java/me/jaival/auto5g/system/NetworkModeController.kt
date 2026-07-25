package me.jaival.auto5g.system

import android.content.Context
import android.provider.Settings
import android.telephony.SubscriptionManager
import android.util.Log
import me.jaival.auto5g.data.PermissionMode
import rikka.shizuku.Shizuku

object NetworkModeController {

    private const val TAG = "Auto5G-NetCtrl"

    const val NETWORK_MODE_4G_PREFERRED = 9   // LTE/GSM/WCDMA
    const val NETWORK_MODE_5G_PREFERRED = 26  // NR/LTE/GSM/WCDMA
    const val NETWORK_MODE_STRICT_5G_ONLY = 23 // NR Only (Mode 23 in Android Telephony)

    private const val PRIMARY_SETTING_KEY = "preferred_network_mode"

    fun getValidSubId(context: Context): Int {
        return try {
            val dataSubId = SubscriptionManager.getDefaultDataSubscriptionId()
            if (SubscriptionManager.isValidSubscriptionId(dataSubId)) {
                return dataSubId
            }
            val defaultSubId = SubscriptionManager.getDefaultSubscriptionId()
            if (SubscriptionManager.isValidSubscriptionId(defaultSubId)) {
                return defaultSubId
            }
            val subManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
            val list = subManager?.activeSubscriptionInfoList
            val validSub = list?.firstOrNull { SubscriptionManager.isValidSubscriptionId(it.subscriptionId) }
            validSub?.subscriptionId ?: 1
        } catch (e: Throwable) {
            1
        }
    }

    private fun getSettingKeys(context: Context): Set<String> {
        val keys = mutableSetOf(
            PRIMARY_SETTING_KEY,
            "preferred_network_mode1",
            "preferred_network_mode2",
            "user_preferred_network_mode",
            "user_preferred_network_mode1",
            "user_preferred_network_mode2"
        )
        try {
            val activeSubId = getValidSubId(context)
            keys.add("preferred_network_mode$activeSubId")
            keys.add("preferred_network_mode_${activeSubId}")
            keys.add("user_preferred_network_mode$activeSubId")

            val subManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
            if (subManager != null) {
                val activeList = subManager.activeSubscriptionInfoList
                activeList?.forEach { info ->
                    val subId = info.subscriptionId
                    val slotIndex = info.simSlotIndex
                    keys.add("preferred_network_mode$subId")
                    keys.add("preferred_network_mode$slotIndex")
                    keys.add("preferred_network_mode_${subId}")
                    keys.add("preferred_network_mode_${slotIndex}")
                    keys.add("user_preferred_network_mode$subId")
                    keys.add("user_preferred_network_mode$slotIndex")
                }
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }
        return keys
    }

    fun setNetworkMode(context: Context, mode: Int, permissionMode: PermissionMode): Boolean {
        Log.d(TAG, "setNetworkMode requested with mode: $mode, permissionMode: $permissionMode")
        val validSubId = getValidSubId(context)

        var shizukuSuccess = false
        if (ShizukuManager.isShizukuAvailable() && ShizukuManager.hasShizukuPermission()) {
            val (cmdSuccess, verifTechs) = ShizukuManager.setAllowedNetworkTypesViaCmdPhone(context, validSubId, mode)
            Log.d(TAG, "cmd phone setAllowedNetworkTypes result: $cmdSuccess, verification techs: '$verifTechs'")
            
            val ipcSuccess = setNetworkModeViaShizuku(context, mode)
            Log.d(TAG, "Shizuku IPC setNetworkMode result: $ipcSuccess")
            
            shizukuSuccess = cmdSuccess || ipcSuccess
        }
        
        val settingsSuccess = setNetworkModeViaSettingsGlobal(context, mode)
        Log.d(TAG, "SettingsGlobal setNetworkMode result: $settingsSuccess")

        return shizukuSuccess || settingsSuccess
    }

    fun getCurrentNetworkMode(context: Context): Int {
        Log.d(TAG, "getCurrentNetworkMode called")
        if (ShizukuManager.isShizukuAvailable() && ShizukuManager.hasShizukuPermission()) {
            val activeSubId = getValidSubId(context)
            val cmdTechs = ShizukuManager.getAllowedNetworkTypesViaCmdPhone(context, activeSubId)
            val cmdMode = ShizukuManager.parseAllowedTechsToMode(cmdTechs)
            if (cmdMode != -1) {
                Log.d(TAG, "getCurrentNetworkMode from cmd phone: $cmdMode (techs: '$cmdTechs')")
                return cmdMode
            }

            if (cachedShizukuService?.asBinder()?.pingBinder() == true) {
                try {
                    val shizukuMode = cachedShizukuService?.getCurrentNetworkMode(activeSubId) ?: -1
                    Log.d(TAG, "getCurrentNetworkMode from cached Shizuku: $shizukuMode (subId: $activeSubId)")
                    if (shizukuMode != -1) return shizukuMode
                } catch (e: Exception) {
                    Log.e(TAG, "getCurrentNetworkMode via cached Shizuku failed", e)
                }
            }
        }

        val keys = getSettingKeys(context)
        for (key in keys) {
            try {
                val valMode = Settings.Global.getInt(context.contentResolver, key, -1)
                if (valMode != -1) {
                    return valMode
                }
            } catch (e: Exception) {
                // Continue checking other keys
            }
        }
        return NETWORK_MODE_4G_PREFERRED
    }

    private fun setNetworkModeViaSettingsGlobal(context: Context, mode: Int): Boolean {
        var success = false
        val keys = getSettingKeys(context)
        for (key in keys) {
            try {
                if (Settings.Global.putInt(context.contentResolver, key, mode)) {
                    success = true
                }
            } catch (e: Exception) {
                // Silently skip read-only keys
            }
        }
        val prefer5g = if (mode == 9) 0 else 1
        try {
            Settings.Global.putInt(context.contentResolver, "prefer_5g", prefer5g)
            Settings.Global.putInt(context.contentResolver, "five_g_service", prefer5g)
            Settings.Global.putInt(context.contentResolver, "five_g_mode", prefer5g)
        } catch (e: Exception) {}
        return success
    }

    private var cachedShizukuService: me.jaival.auto5g.IShizukuController? = null

    private fun setNetworkModeViaShizuku(context: Context, mode: Int): Boolean {
        Log.d(TAG, "setNetworkModeViaShizuku: Checking Shizuku binder ping")
        if (!Shizuku.pingBinder()) {
            Log.e(TAG, "Shizuku binder ping failed!")
            return false
        }
        var success = false
        
        if (cachedShizukuService?.asBinder()?.pingBinder() == true) {
            Log.d(TAG, "setNetworkModeViaShizuku: Using cached Shizuku service")
            return try {
                val activeSubId = getValidSubId(context)
                cachedShizukuService?.setNetworkMode(activeSubId, mode)
                Log.d(TAG, "setNetworkModeViaShizuku: Success via cached service on subId: $activeSubId")
                true
            } catch (e: Exception) {
                Log.e(TAG, "setNetworkModeViaShizuku: Exception with cached service", e)
                cachedShizukuService = null
                false
            }
        }

        Log.d(TAG, "setNetworkModeViaShizuku: Binding new Shizuku service")
        val latch = java.util.concurrent.CountDownLatch(1)
        val args = Shizuku.UserServiceArgs(android.content.ComponentName(context, me.jaival.auto5g.services.ShizukuControllerService::class.java))
            .processNameSuffix("service")
            .debuggable(me.jaival.auto5g.BuildConfig.DEBUG)
            .version(me.jaival.auto5g.BuildConfig.VERSION_CODE)
            .tag("Auto5G")
        
        val connection = object : android.content.ServiceConnection {
            override fun onServiceConnected(componentName: android.content.ComponentName?, binder: android.os.IBinder?) {
                Log.d(TAG, "Shizuku service connected")
                if (binder != null && binder.pingBinder()) {
                    try {
                        val service = me.jaival.auto5g.IShizukuController.Stub.asInterface(binder)
                        cachedShizukuService = service
                        val activeSubId = getValidSubId(context)
                        
                        Log.d(TAG, "Setting mode $mode for subId $activeSubId")
                        service.setNetworkMode(activeSubId, mode)
                        success = true
                    } catch (e: Exception) {
                        Log.e(TAG, "Error while setting network mode in callback", e)
                        e.printStackTrace()
                    }
                } else {
                    Log.e(TAG, "Binder is null or dead upon connection")
                }
                latch.countDown()
            }
            override fun onServiceDisconnected(componentName: android.content.ComponentName?) {
                Log.d(TAG, "Shizuku service disconnected")
                cachedShizukuService = null
            }
        }
        
        try {
            Shizuku.bindUserService(args, connection)
            val awaitSuccess = latch.await(5, java.util.concurrent.TimeUnit.SECONDS)
            Log.d(TAG, "Latch await result: $awaitSuccess, overall success: $success")
        } catch (e: Exception) {
            Log.e(TAG, "Exception binding Shizuku service", e)
            e.printStackTrace()
        }
        return success
    }
}


