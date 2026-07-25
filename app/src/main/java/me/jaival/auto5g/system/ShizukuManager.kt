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

    fun getSlotIndex(context: Context, subId: Int): Int {
        return try {
            val subManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? android.telephony.SubscriptionManager
            val info = subManager?.getActiveSubscriptionInfo(subId)
            if (info != null && info.simSlotIndex != android.telephony.SubscriptionManager.INVALID_SIM_SLOT_INDEX) {
                info.simSlotIndex
            } else {
                val slot = android.telephony.SubscriptionManager.getSlotIndex(subId)
                if (slot != android.telephony.SubscriptionManager.INVALID_SIM_SLOT_INDEX && slot >= 0) slot else 0
            }
        } catch (e: Throwable) {
            0
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

    fun setAllowedNetworkTypesViaCmdPhone(context: Context, subId: Int, mode: Int): Pair<Boolean, String> {
        if (!hasShizukuPermission()) return Pair(false, "Shizuku permission not granted")

        val slotIndex = getSlotIndex(context, subId)
        val bitmaskStr = when (mode) {
            NetworkModeController.NETWORK_MODE_4G_PREFERRED -> "01001111101111111111" // NR disabled (01001111101111111111)
            NetworkModeController.NETWORK_MODE_5G_PREFERRED -> "11001111101111111111" // NR enabled (11001111101111111111)
            NetworkModeController.NETWORK_MODE_STRICT_5G_ONLY -> "10000000000000000000" // NR only (10000000000000000000)
            11 -> "01000001000000000000" // LTE only
            else -> "01001111101111111111"
        }

        val prefer5g = if (mode == NetworkModeController.NETWORK_MODE_4G_PREFERRED) 0 else 1
        val setCmd = "cmd phone set-allowed-network-types-for-users -s $slotIndex $bitmaskStr"
        val getCmd = "cmd phone get-allowed-network-types-for-users -s $slotIndex"

        val cmds = listOf(
            "settings put global preferred_network_mode $mode",
            "settings put global preferred_network_mode$subId $mode",
            "settings put global prefer_5g $prefer5g",
            "settings put global five_g_service $prefer5g",
            "settings put global five_g_mode $prefer5g",
            setCmd
        )
        executeShellCommands(cmds)

        val verificationOutput = executeShellCommandWithOutput(getCmd)
        android.util.Log.d("ShizukuManager", "cmd phone set executed on slot $slotIndex with bitmask $bitmaskStr. Verification output: '$verificationOutput'")

        val success = verificationOutput.isNotEmpty() && !verificationOutput.contains("failed", ignoreCase = true)
        return Pair(success, verificationOutput)
    }

    fun getAllowedNetworkTypesViaCmdPhone(context: Context, subId: Int): String {
        if (!hasShizukuPermission()) return ""
        val slotIndex = getSlotIndex(context, subId)
        val getCmd = "cmd phone get-allowed-network-types-for-users -s $slotIndex"
        return executeShellCommandWithOutput(getCmd)
    }

    fun parseAllowedTechsToMode(techs: String): Int {
        if (techs.isEmpty() || techs.contains("failed", ignoreCase = true)) return -1
        val hasNr = techs.contains("NR", ignoreCase = false)
        val hasLte = techs.contains("LTE", ignoreCase = false)
        return when {
            hasNr && !hasLte -> NetworkModeController.NETWORK_MODE_STRICT_5G_ONLY
            hasNr && hasLte -> NetworkModeController.NETWORK_MODE_5G_PREFERRED
            !hasNr && hasLte -> NetworkModeController.NETWORK_MODE_4G_PREFERRED
            else -> NetworkModeController.NETWORK_MODE_4G_PREFERRED
        }
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

    fun executeShellCommandWithOutput(command: String): String {
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
            os.write("$command\n".toByteArray(Charsets.UTF_8))
            os.write("exit\n".toByteArray(Charsets.UTF_8))
            os.flush()
            os.close()

            val reader = java.io.BufferedReader(java.io.InputStreamReader(process.inputStream))
            val output = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                output.append(line).append("\n")
            }
            process.waitFor()
            output.toString().trim()
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }
}


