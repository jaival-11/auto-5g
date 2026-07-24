package me.jaival.5g.system

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

    private fun executeShellCommands(commands: List<String>): Boolean {
        return try {
            val process = Shizuku.newProcess(arrayOf("sh"), null, null)
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
