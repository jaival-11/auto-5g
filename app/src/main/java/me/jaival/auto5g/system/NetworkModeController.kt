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

    private fun setNetworkModeViaShizuku(context: Context, mode: Int): Boolean {
        return try {
            if (!Shizuku.pingBinder()) return false
            val keys = getSettingKeys(context)
            val commands = mutableListOf<String>()
            for (key in keys) {
                commands.add("settings put global $key $mode")
            }
            commands.add("cmd telephony setAllowedNetworkTypes $mode")
            commands.add("exit")

            val commandStr = commands.joinToString("\n") + "\n"
            val method = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )
            method.isAccessible = true
            val process = method.invoke(null, arrayOf("sh"), null, null) as java.lang.Process
            val os: OutputStream = process.outputStream
            os.write(commandStr.toByteArray(Charsets.UTF_8))
            os.flush()
            os.close()
            process.waitFor() == 0
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
