package me.jaival.auto5g.system

import android.net.TrafficStats
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

object TrafficMonitor {

    fun observeTrafficMbps(samplePeriodMs: Long = 1000L): Flow<Double> = flow {
        var lastRxBytes = TrafficStats.getMobileRxBytes()
        var lastTxBytes = TrafficStats.getMobileTxBytes()
        var lastTime = System.currentTimeMillis()

        while (true) {
            delay(samplePeriodMs)
            val currentRxBytes = TrafficStats.getMobileRxBytes()
            val currentTxBytes = TrafficStats.getMobileTxBytes()
            val currentTime = System.currentTimeMillis()

            val timeDiffSecs = (currentTime - lastTime) / 1000.0
            if (timeDiffSecs > 0 && lastRxBytes != TrafficStats.UNSUPPORTED.toLong() && lastTxBytes != TrafficStats.UNSUPPORTED.toLong()) {
                val rxDiff = if (currentRxBytes >= lastRxBytes) currentRxBytes - lastRxBytes else 0L
                val txDiff = if (currentTxBytes >= lastTxBytes) currentTxBytes - lastTxBytes else 0L
                val totalBytes = rxDiff + txDiff
                val megabits = (totalBytes * 8.0) / (1024.0 * 1024.0)
                val mbps = megabits / timeDiffSecs
                emit(mbps)
            } else {
                emit(0.0)
            }

            lastRxBytes = currentRxBytes
            lastTxBytes = currentTxBytes
            lastTime = currentTime
        }
    }
}
