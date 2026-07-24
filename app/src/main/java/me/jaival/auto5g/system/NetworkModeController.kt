package me.jaival.auto5g.system

import android.content.Context
import android.provider.Settings
import me.jaival.auto5g.data.PermissionMode
import rikka.shizuku.Shizuku
import java.io.OutputStream

object NetworkModeController {

    const val NETWORK_MODE_4G_PREFERRED = 9  // LTE/GSM/WCDMA
    const val NETWORK_MODE_5G_PREFERRED = 26 // NR/LTE/GSM/WCDMA
    const val NETWORK_MODE_STRICT_5G_ONLY = 20 // NR Only

    private const val PRIMARY_SETTING_KEY = "preferred_network_mode"

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
            val subManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? android.telephony.SubscriptionManager
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
        return when (permissionMode) {
            PermissionMode.SHIZUKU_CONTINUOUS -> {
                setNetworkModeViaShizuku(context, mode)
            }
            PermissionMode.SHIZUKU_ONETIME, PermissionMode.MANUAL_ADB -> {
                setNetworkModeViaSettingsGlobal(context, mode)
            }
        }
    }

    fun getCurrentNetworkMode(context: Context): Int {
        if (cachedShizukuService?.asBinder()?.pingBinder() == true) {
            try {
                val subManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? android.telephony.SubscriptionManager
                val activeSubId = subManager?.activeSubscriptionInfoList?.firstOrNull()?.subscriptionId 
                    ?: android.telephony.SubscriptionManager.getDefaultSubscriptionId()
                val shizukuMode = cachedShizukuService?.getCurrentNetworkMode(activeSubId) ?: -1
                if (shizukuMode != -1) return shizukuMode
            } catch (e: Exception) {
                e.printStackTrace()
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
                e.printStackTrace()
            }
        }
        if (!success && ShizukuManager.isShizukuAvailable() && ShizukuManager.hasShizukuPermission()) {
            return setNetworkModeViaShizuku(context, mode)
        }
        return success
    }

    private var cachedShizukuService: me.jaival.auto5g.IShizukuController? = null

    private fun setNetworkModeViaShizuku(context: Context, mode: Int): Boolean {
        if (!Shizuku.pingBinder()) return false
        var success = false
        
        if (cachedShizukuService?.asBinder()?.pingBinder() == true) {
            return try {
                val subManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? android.telephony.SubscriptionManager
                val activeSubId = subManager?.activeSubscriptionInfoList?.firstOrNull()?.subscriptionId 
                    ?: android.telephony.SubscriptionManager.getDefaultSubscriptionId()
                cachedShizukuService?.setNetworkMode(activeSubId, mode)
                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }

        val latch = java.util.concurrent.CountDownLatch(1)
        val args = Shizuku.UserServiceArgs(android.content.ComponentName(context, me.jaival.auto5g.services.ShizukuControllerService::class.java))
            .processNameSuffix("service")
            .debuggable(me.jaival.auto5g.BuildConfig.DEBUG)
            .version(me.jaival.auto5g.BuildConfig.VERSION_CODE)
            .tag("Auto5G")
        
        val connection = object : android.content.ServiceConnection {
            override fun onServiceConnected(componentName: android.content.ComponentName?, binder: android.os.IBinder?) {
                if (binder != null && binder.pingBinder()) {
                    try {
                        val service = me.jaival.auto5g.IShizukuController.Stub.asInterface(binder)
                        cachedShizukuService = service
                        val subManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? android.telephony.SubscriptionManager
                        val activeSubId = subManager?.activeSubscriptionInfoList?.firstOrNull()?.subscriptionId 
                            ?: android.telephony.SubscriptionManager.getDefaultSubscriptionId()
                        
                        service.setNetworkMode(activeSubId, mode)
                        success = true
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                latch.countDown()
            }
            override fun onServiceDisconnected(componentName: android.content.ComponentName?) {
                cachedShizukuService = null
            }
        }
        
        try {
            Shizuku.bindUserService(args, connection)
            latch.await(5, java.util.concurrent.TimeUnit.SECONDS)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return success
    }
}
