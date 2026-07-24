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

    private const val SETTING_KEY = "preferred_network_mode"

    fun setNetworkMode(context: Context, mode: Int, permissionMode: PermissionMode): Boolean {
        return when (permissionMode) {
            PermissionMode.SHIZUKU_CONTINUOUS -> {
                setNetworkModeViaShizuku(mode)
            }
            PermissionMode.SHIZUKU_ONETIME, PermissionMode.MANUAL_ADB -> {
                setNetworkModeViaSettingsGlobal(context, mode)
            }
        }
    }

    fun getCurrentNetworkMode(context: Context): Int {
        return try {
            Settings.Global.getInt(context.contentResolver, SETTING_KEY, NETWORK_MODE_4G_PREFERRED)
        } catch (e: Exception) {
            NETWORK_MODE_4G_PREFERRED
        }
    }

    private fun setNetworkModeViaSettingsGlobal(context: Context, mode: Int): Boolean {
        return try {
            Settings.Global.putInt(context.contentResolver, SETTING_KEY, mode)
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun setNetworkModeViaShizuku(mode: Int): Boolean {
        return try {
            if (!Shizuku.pingBinder()) return false
            val command = "settings put global $SETTING_KEY $mode\nexit\n"
            val method = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )
            method.isAccessible = true
            val process = method.invoke(null, arrayOf("sh"), null, null) as java.lang.Process
            val os: OutputStream = process.outputStream
            os.write(command.toByteArray(Charsets.UTF_8))
            os.flush()
            os.close()
            process.waitFor() == 0
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
