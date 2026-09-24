package tk.glucodata

import java.util.concurrent.ConcurrentHashMap

/**
 * Lets an exchange payload through once per distinct payload time when "collapse into
 * chunks" is on, and always when it is off. Only meaningful for outputs that are meant to
 * see chunked data; outputs that drive a closed loop must not be routed through it.
 */
class ExchangeUpdateGate {
    private val lastTimeMsBySensor = ConcurrentHashMap<String, Long>()

    fun shouldEmit(sensorId: String?, payloadTimeMs: Long, collapseChunks: Boolean): Boolean {
        if (!collapseChunks || payloadTimeMs <= 0L) {
            return true
        }
        val key = if (!sensorId.isNullOrEmpty()) sensorId else "<unknown>"
        val previous = lastTimeMsBySensor.put(key, payloadTimeMs)
        return previous == null || previous != payloadTimeMs
    }
}
