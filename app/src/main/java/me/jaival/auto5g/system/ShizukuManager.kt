package me.jaival.auto5g.system

import android.content.Context
import android.content.pm.PackageManager
import rikka.shizuku.Shizuku
import java.io.OutputStream

object ShizukuManager {

    const val SHIZUKU_PERMISSION_REQUEST_CODE = 1001

    fun isShizukuAvailable(): Boolean {
        return try {
            Shizuku.pingBinder()
        } catch (e: Throwable) {
            false
        }
    }

    fun hasShizukuPermission(): Boolean {
        return try {
            if (!isShizukuAvailable()) false
            else Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (e: Throwable) {
            false
        }
    }

    fun requestShizukuPermission(listener: Shizuku.OnRequestPermissionResultListener) {
        if (isShizukuAvailable()) {
            Shizuku.addRequestPermissionResultListener(listener)
            Shizuku.requestPermission(SHIZUKU_PERMISSION_REQUEST_CODE)
        }
    }

    fun removePermissionListener(listener: Shizuku.OnRequestPermissionResultListener) {
        try {
            Shizuku.removeRequestPermissionResultListener(listener)
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    fun grantSecureSettingsViaShizuku(context: Context): Boolean {
        if (!hasShizukuPermission()) return false
        val pkg = context.packageName
        val cmds = listOf(
            "pm grant $pkg android.permission.WRITE_SECURE_SETTINGS",
            "pm grant $pkg android.permission.PACKAGE_USAGE_STATS"
        )
        return executeShellCommands(cmds)
    }

    fun executeNetworkModeShellCommands(context: Context, subId: Int, mode: Int, bitmask: Long): Boolean {
        if (!hasShizukuPermission()) return false
        val prefer5g = if (mode == 9) 0 else 1
        val cmds = listOf(
            "settings put global preferred_network_mode $mode",
            "settings put global preferred_network_mode$subId $mode",
            "settings put global user_preferred_network_mode $mode",
            "settings put global user_preferred_network_mode$subId $mode",
            "settings put global prefer_5g $prefer5g",
            "settings put global five_g_service $prefer5g",
            "settings put global five_g_mode $prefer5g",
            "settings put secure prefer_5g $prefer5g",
            "cmd telephony set-allowed-network-types -s $subId $bitmask",
            "cmd telephony set-preferred-network-type -s $subId $mode",
            "cmd telephony set-allowed-network-types $bitmask",
            "cmd telephony set-preferred-network-type $mode"
        )
        return executeShellCommands(cmds)
    }

    fun executeShellCommands(commands: List<String>): Boolean {
        return try {
            val method = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )
            method.isAccessible = true
            val process = method.invoke(null, arrayOf("sh"), null, null) as java.lang.Process
            val os: OutputStream = process.outputStream
            for (cmd in commands) {
                os.write("$cmd\n".toByteArray(Charsets.UTF_8))
            }
            os.write("exit\n".toByteArray(Charsets.UTF_8))
            os.flush()
            os.close()
            val exitCode = process.waitFor()
            exitCode == 0
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}

