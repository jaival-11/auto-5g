package me.jaival.auto5g.services

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.Keep
import me.jaival.auto5g.IShizukuController

class ShizukuControllerService() : IShizukuController.Stub() {

    companion object {
        private const val TAG = "Auto5G-ShizukuSvc"

        private val iTelephonyProxy: Any? by lazy {
            try {
                val serviceManagerClass = Class.forName("android.os.ServiceManager")
                val getServiceMethod = serviceManagerClass.getMethod("getService", String::class.java)
                val binder = getServiceMethod.invoke(null, Context.TELEPHONY_SERVICE) as android.os.IBinder
                val stubClass = Class.forName("com.android.internal.telephony.ITelephony\$Stub")
                val asInterfaceMethod = stubClass.getMethod("asInterface", android.os.IBinder::class.java)
                asInterfaceMethod.invoke(null, binder)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get ITelephony proxy via reflection", e)
                null
            }
        }
        
        private val reasonUser by lazy {
            try {
                Class.forName("android.telephony.TelephonyManager")
                    .getDeclaredField("ALLOWED_NETWORK_TYPES_REASON_USER")
                    .getInt(null)
            } catch (e: Exception) {
                0
            }
        }

        private fun invokeTelephonyMethod(methodName: String, vararg args: Any): Any? {
            val proxy = iTelephonyProxy ?: throw IllegalStateException("iTelephony proxy is null")
            val methods = proxy.javaClass.methods
            for (method in methods) {
                if (method.name == methodName && method.parameterTypes.size == args.size) {
                    val paramTypes = method.parameterTypes
                    var matches = true
                    for (i in args.indices) {
                        if (!isTypeCompatible(paramTypes[i], args[i].javaClass)) {
                            matches = false
                            break
                        }
                    }
                    if (matches) {
                        try {
                            method.isAccessible = true
                            return method.invoke(proxy, *args)
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to invoke $methodName", e)
                            throw e
                        }
                    }
                }
            }
            // Fallback: search by method name and param count only
            for (method in methods) {
                if (method.name == methodName && method.parameterTypes.size == args.size) {
                    try {
                        method.isAccessible = true
                        return method.invoke(proxy, *args)
                    } catch (e: Exception) {
                        Log.e(TAG, "Fallback invoke failed for $methodName", e)
                    }
                }
            }
            throw NoSuchMethodException("Method $methodName with ${args.size} args not found in ITelephony")
        }

        private fun isTypeCompatible(paramType: Class<*>, argType: Class<*>): Boolean {
            if (paramType == argType || paramType.isAssignableFrom(argType)) return true
            if (paramType == Int::class.javaPrimitiveType && argType == java.lang.Integer::class.java) return true
            if (paramType == Long::class.javaPrimitiveType && argType == java.lang.Long::class.java) return true
            if (paramType == Boolean::class.javaPrimitiveType && argType == java.lang.Boolean::class.java) return true
            return false
        }
        
        // Get network type bitmasks from Android TelephonyManager constants
        private val NETWORK_TYPE_BITMASK_GSM by lazy { getBitmaskConstant("NETWORK_TYPE_BITMASK_GSM") }
        private val NETWORK_TYPE_BITMASK_GPRS by lazy { getBitmaskConstant("NETWORK_TYPE_BITMASK_GPRS") }
        private val NETWORK_TYPE_BITMASK_EDGE by lazy { getBitmaskConstant("NETWORK_TYPE_BITMASK_EDGE") }
        private val NETWORK_TYPE_BITMASK_UMTS by lazy { getBitmaskConstant("NETWORK_TYPE_BITMASK_UMTS") }
        private val NETWORK_TYPE_BITMASK_CDMA by lazy { getBitmaskConstant("NETWORK_TYPE_BITMASK_CDMA") }
        private val NETWORK_TYPE_BITMASK_EVDO_0 by lazy { getBitmaskConstant("NETWORK_TYPE_BITMASK_EVDO_0") }
        private val NETWORK_TYPE_BITMASK_EVDO_A by lazy { getBitmaskConstant("NETWORK_TYPE_BITMASK_EVDO_A") }
        private val NETWORK_TYPE_BITMASK_1xRTT by lazy { getBitmaskConstant("NETWORK_TYPE_BITMASK_1xRTT") }
        private val NETWORK_TYPE_BITMASK_HSDPA by lazy { getBitmaskConstant("NETWORK_TYPE_BITMASK_HSDPA") }
        private val NETWORK_TYPE_BITMASK_HSUPA by lazy { getBitmaskConstant("NETWORK_TYPE_BITMASK_HSUPA") }
        private val NETWORK_TYPE_BITMASK_HSPA by lazy { getBitmaskConstant("NETWORK_TYPE_BITMASK_HSPA") }
        private val NETWORK_TYPE_BITMASK_EVDO_B by lazy { getBitmaskConstant("NETWORK_TYPE_BITMASK_EVDO_B") }
        private val NETWORK_TYPE_BITMASK_LTE by lazy { getBitmaskConstant("NETWORK_TYPE_BITMASK_LTE") }
        private val NETWORK_TYPE_BITMASK_EHRPD by lazy { getBitmaskConstant("NETWORK_TYPE_BITMASK_EHRPD") }
        private val NETWORK_TYPE_BITMASK_HSPAP by lazy { getBitmaskConstant("NETWORK_TYPE_BITMASK_HSPAP") }
        private val NETWORK_TYPE_BITMASK_TD_SCDMA by lazy { getBitmaskConstant("NETWORK_TYPE_BITMASK_TD_SCDMA") }
        private val NETWORK_TYPE_BITMASK_LTE_CA by lazy { getBitmaskConstant("NETWORK_TYPE_BITMASK_LTE_CA") }
        private val NETWORK_TYPE_BITMASK_IWLAN by lazy { getBitmaskConstant("NETWORK_TYPE_BITMASK_IWLAN") }
        private val NETWORK_TYPE_BITMASK_NR by lazy { getBitmaskConstant("NETWORK_TYPE_BITMASK_NR") }
        
        private fun getBitmaskConstant(name: String): Long {
            return try {
                Class.forName("android.telephony.TelephonyManager")
                    .getDeclaredField(name)
                    .getLong(null)
            } catch (e: Exception) {
                0L
            }
        }
        
        // Combined bitmasks for common network classes
        private fun get2GBitmask(): Long = NETWORK_TYPE_BITMASK_GSM or NETWORK_TYPE_BITMASK_GPRS or 
                NETWORK_TYPE_BITMASK_EDGE or NETWORK_TYPE_BITMASK_CDMA or NETWORK_TYPE_BITMASK_1xRTT
        
        private fun get3GBitmask(): Long = NETWORK_TYPE_BITMASK_EVDO_0 or NETWORK_TYPE_BITMASK_EVDO_A or 
                NETWORK_TYPE_BITMASK_EVDO_B or NETWORK_TYPE_BITMASK_EHRPD or 
                NETWORK_TYPE_BITMASK_HSUPA or NETWORK_TYPE_BITMASK_HSDPA or 
                NETWORK_TYPE_BITMASK_HSPA or NETWORK_TYPE_BITMASK_HSPAP or 
                NETWORK_TYPE_BITMASK_UMTS or NETWORK_TYPE_BITMASK_TD_SCDMA
        
        private fun get4GBitmask(): Long = NETWORK_TYPE_BITMASK_LTE or NETWORK_TYPE_BITMASK_LTE_CA or NETWORK_TYPE_BITMASK_IWLAN
        
        /**
         * Map RIL network mode constants to network type bitmasks for Android 12+
         */
        private fun mapNetworkModeToBitmask(networkMode: Int): Long {
            return when (networkMode) {
                0 -> get2GBitmask() or get3GBitmask() // WCDMA_PREF
                1 -> NETWORK_TYPE_BITMASK_GSM // GSM_ONLY
                2 -> NETWORK_TYPE_BITMASK_UMTS // WCDMA_ONLY
                3 -> get2GBitmask() or get3GBitmask() // GSM_UMTS
                4 -> NETWORK_TYPE_BITMASK_CDMA or NETWORK_TYPE_BITMASK_EVDO_0 or NETWORK_TYPE_BITMASK_EVDO_A or NETWORK_TYPE_BITMASK_EVDO_B // CDMA
                5 -> NETWORK_TYPE_BITMASK_CDMA // CDMA_NO_EVDO
                6 -> NETWORK_TYPE_BITMASK_EVDO_0 or NETWORK_TYPE_BITMASK_EVDO_A or NETWORK_TYPE_BITMASK_EVDO_B // EVDO_NO_CDMA
                7 -> get2GBitmask() or get3GBitmask() or get4GBitmask() // GLOBAL
                8 -> NETWORK_TYPE_BITMASK_LTE or NETWORK_TYPE_BITMASK_CDMA or NETWORK_TYPE_BITMASK_EVDO_0 or NETWORK_TYPE_BITMASK_EVDO_A or NETWORK_TYPE_BITMASK_EVDO_B // LTE_CDMA_EVDO
                9 -> NETWORK_TYPE_BITMASK_LTE or get2GBitmask() or get3GBitmask() // LTE_GSM_WCDMA
                10 -> NETWORK_TYPE_BITMASK_LTE or NETWORK_TYPE_BITMASK_CDMA or NETWORK_TYPE_BITMASK_EVDO_0 or NETWORK_TYPE_BITMASK_EVDO_A or NETWORK_TYPE_BITMASK_EVDO_B or get2GBitmask() or get3GBitmask() // LTE_CDMA_EVDO_GSM_WCDMA
                11 -> NETWORK_TYPE_BITMASK_LTE // LTE_ONLY
                12 -> NETWORK_TYPE_BITMASK_LTE or get3GBitmask() // LTE_WCDMA
                13 -> NETWORK_TYPE_BITMASK_TD_SCDMA // TDSCDMA_ONLY
                14 -> NETWORK_TYPE_BITMASK_TD_SCDMA or NETWORK_TYPE_BITMASK_UMTS // TDSCDMA_WCDMA
                15 -> NETWORK_TYPE_BITMASK_LTE or NETWORK_TYPE_BITMASK_TD_SCDMA // LTE_TDSCDMA
                16 -> NETWORK_TYPE_BITMASK_TD_SCDMA or get2GBitmask() // TDSCDMA_GSM
                17 -> NETWORK_TYPE_BITMASK_LTE or NETWORK_TYPE_BITMASK_TD_SCDMA or get2GBitmask() // LTE_TDSCDMA_GSM
                18 -> NETWORK_TYPE_BITMASK_TD_SCDMA or get2GBitmask() or get3GBitmask() // TDSCDMA_GSM_WCDMA
                19 -> NETWORK_TYPE_BITMASK_LTE or NETWORK_TYPE_BITMASK_TD_SCDMA or get3GBitmask() // LTE_TDSCDMA_WCDMA
                20 -> NETWORK_TYPE_BITMASK_LTE or NETWORK_TYPE_BITMASK_TD_SCDMA or get2GBitmask() or get3GBitmask() // LTE_TDSCDMA_GSM_WCDMA
                21 -> NETWORK_TYPE_BITMASK_TD_SCDMA or NETWORK_TYPE_BITMASK_CDMA or NETWORK_TYPE_BITMASK_EVDO_0 or NETWORK_TYPE_BITMASK_EVDO_A or NETWORK_TYPE_BITMASK_EVDO_B or get2GBitmask() or get3GBitmask() // TDSCDMA_CDMA_EVDO_GSM_WCDMA
                22 -> NETWORK_TYPE_BITMASK_LTE or NETWORK_TYPE_BITMASK_TD_SCDMA or NETWORK_TYPE_BITMASK_CDMA or NETWORK_TYPE_BITMASK_EVDO_0 or NETWORK_TYPE_BITMASK_EVDO_A or NETWORK_TYPE_BITMASK_EVDO_B or get2GBitmask() or get3GBitmask() // LTE_TDSCDMA_CDMA_EVDO_GSM_WCDMA
                23 -> NETWORK_TYPE_BITMASK_NR // NR_ONLY
                24 -> NETWORK_TYPE_BITMASK_NR or NETWORK_TYPE_BITMASK_LTE // NR_LTE
                25 -> NETWORK_TYPE_BITMASK_NR or NETWORK_TYPE_BITMASK_LTE or NETWORK_TYPE_BITMASK_CDMA or NETWORK_TYPE_BITMASK_EVDO_0 or NETWORK_TYPE_BITMASK_EVDO_A or NETWORK_TYPE_BITMASK_EVDO_B // NR_LTE_CDMA_EVDO
                26 -> NETWORK_TYPE_BITMASK_NR or NETWORK_TYPE_BITMASK_LTE or get2GBitmask() or get3GBitmask() // NR_LTE_GSM_WCDMA
                27 -> NETWORK_TYPE_BITMASK_NR or NETWORK_TYPE_BITMASK_LTE or NETWORK_TYPE_BITMASK_CDMA or NETWORK_TYPE_BITMASK_EVDO_0 or NETWORK_TYPE_BITMASK_EVDO_A or NETWORK_TYPE_BITMASK_EVDO_B or get2GBitmask() or get3GBitmask() // NR_LTE_CDMA_EVDO_GSM_WCDMA
                28 -> NETWORK_TYPE_BITMASK_NR or NETWORK_TYPE_BITMASK_LTE or get3GBitmask() // NR_LTE_WCDMA
                29 -> NETWORK_TYPE_BITMASK_NR or NETWORK_TYPE_BITMASK_LTE or NETWORK_TYPE_BITMASK_TD_SCDMA // NR_LTE_TDSCDMA
                30 -> NETWORK_TYPE_BITMASK_NR or NETWORK_TYPE_BITMASK_LTE or NETWORK_TYPE_BITMASK_TD_SCDMA or get2GBitmask() // NR_LTE_TDSCDMA_GSM
                31 -> NETWORK_TYPE_BITMASK_NR or NETWORK_TYPE_BITMASK_LTE or NETWORK_TYPE_BITMASK_TD_SCDMA or get3GBitmask() // NR_LTE_TDSCDMA_WCDMA
                32 -> NETWORK_TYPE_BITMASK_NR or NETWORK_TYPE_BITMASK_LTE or NETWORK_TYPE_BITMASK_TD_SCDMA or get2GBitmask() or get3GBitmask() // NR_LTE_TDSCDMA_GSM_WCDMA
                33 -> NETWORK_TYPE_BITMASK_NR or NETWORK_TYPE_BITMASK_LTE or NETWORK_TYPE_BITMASK_TD_SCDMA or NETWORK_TYPE_BITMASK_CDMA or NETWORK_TYPE_BITMASK_EVDO_0 or NETWORK_TYPE_BITMASK_EVDO_A or NETWORK_TYPE_BITMASK_EVDO_B or get2GBitmask() or get3GBitmask() // NR_LTE_TDSCDMA_CDMA_EVDO_GSM_WCDMA
                else -> get2GBitmask() or get3GBitmask() or get4GBitmask() // Default fallback
            }
        }
        
        /**
         * Map bitmask back to RIL network mode for getCurrentNetworkMode
         */
        private fun mapBitmaskToNetworkMode(bitmask: Long): Int {
            for (mode in 0..33) {
                try {
                    if (bitmask == mapNetworkModeToBitmask(mode)) {
                        return mode
                    }
                } catch (_: Exception) {}
            }
            return when {
                bitmask == NETWORK_TYPE_BITMASK_NR -> 23 // NR_ONLY
                bitmask == NETWORK_TYPE_BITMASK_LTE -> 11 // LTE_ONLY
                bitmask == NETWORK_TYPE_BITMASK_GSM -> 1 // GSM_ONLY
                bitmask == NETWORK_TYPE_BITMASK_UMTS -> 2 // WCDMA_ONLY
                bitmask == NETWORK_TYPE_BITMASK_TD_SCDMA -> 13 // TDSCDMA_ONLY
                bitmask == (NETWORK_TYPE_BITMASK_NR or NETWORK_TYPE_BITMASK_LTE) -> 24 // NR_LTE
                (bitmask and NETWORK_TYPE_BITMASK_NR) != 0L -> 26 // Has 5G, default to 5G Preferred (26)
                (bitmask and NETWORK_TYPE_BITMASK_LTE) != 0L -> 9 // Has LTE, default to 4G Preferred (9)
                (bitmask and get3GBitmask()) != 0L -> 2 // Has 3G, default to WCDMA_ONLY
                (bitmask and get2GBitmask()) != 0L -> 1 // Has 2G, default to GSM_ONLY
                else -> 0 // WCDMA_PREF as ultimate fallback
            }
        }
    }

    @Keep
    constructor(context: Context) : this()

    override fun compatibilityCheck(subId: Int): Boolean {
        Log.d(TAG, "compatibilityCheck called for subId: $subId")
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val current = (invokeTelephonyMethod("getAllowedNetworkTypesForReason", subId, reasonUser) as? Number)?.toLong() ?: 0L
                invokeTelephonyMethod("setAllowedNetworkTypesForReason", subId, reasonUser, current)
            } else {
                val current = (invokeTelephonyMethod("getPreferredNetworkType", subId) as? Number)?.toInt() ?: 0
                invokeTelephonyMethod("setPreferredNetworkType", subId, current)
            }
            Log.d(TAG, "compatibilityCheck successful")
            true
        } catch (e: Exception) {
            Log.e(TAG, "compatibilityCheck failed", e)
            false
        }
    }

    override fun getCurrentNetworkMode(subId: Int): Int {
        Log.d(TAG, "getCurrentNetworkMode called for subId: $subId")
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val currentBitmask = (invokeTelephonyMethod("getAllowedNetworkTypesForReason", subId, reasonUser) as? Number)?.toLong() ?: -1L
                Log.d(TAG, "current bitmask for user reason: $currentBitmask")
                if (currentBitmask == -1L) return -1
                val mode = mapBitmaskToNetworkMode(currentBitmask)
                Log.d(TAG, "mapped mode: $mode")
                mode
            } else {
                val mode = (invokeTelephonyMethod("getPreferredNetworkType", subId) as? Number)?.toInt() ?: -1
                Log.d(TAG, "legacy getPreferredNetworkType: $mode")
                mode
            }
        } catch (e: Exception) {
            Log.e(TAG, "getCurrentNetworkMode failed", e)
            -1
        }
    }

    override fun setNetworkMode(subId: Int, networkMode: Int) {
        Log.d(TAG, "setNetworkMode called for subId: $subId to mode: $networkMode")
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val networkTypeBitmask = mapNetworkModeToBitmask(networkMode)
                Log.d(TAG, "mapped bitmask: $networkTypeBitmask for user reason: $reasonUser")
                try {
                    invokeTelephonyMethod("setAllowedNetworkTypesForReason", subId, reasonUser, networkTypeBitmask)
                    Log.d(TAG, "setAllowedNetworkTypesForReason completed")
                } catch (e: Exception) {
                    Log.w(TAG, "setAllowedNetworkTypesForReason failed, attempting fallback to setPreferredNetworkType", e)
                    invokeTelephonyMethod("setPreferredNetworkType", subId, networkMode)
                }
            } else {
                Log.d(TAG, "using legacy setPreferredNetworkType")
                invokeTelephonyMethod("setPreferredNetworkType", subId, networkMode)
            }
        } catch (e: Exception) {
            Log.e(TAG, "setNetworkMode failed", e)
        }
    }

    override fun destroy() {
        // Cleanup if needed
    }
}


