package tk.glucodata

import java.util.concurrent.ConcurrentHashMap

/**
 * Rate limit for exchange outputs under "collapse into chunks": lets the first reading of each
 * interval through and holds back the rest. It decides *whether* to send, never *what*: the
 * payload is always the newest reading under its own timestamp.
 *
 * The interval is counted on the reading's own time, so it does not depend on when the
 * callback happens to run.
 */
class ExchangeUpdateGate {
    private val lastBucketBySensor = ConcurrentHashMap<String, Long>()

    /** @param intervalMinutes 0 (or an unusable [payloadTimeMs]) lets everything through. */
    fun shouldEmit(sensorId: String?, payloadTimeMs: Long, intervalMinutes: Int): Boolean {
        if (intervalMinutes <= 0 || payloadTimeMs <= 0L) {
            return true
        }
        val key = if (!sensorId.isNullOrEmpty()) sensorId else "<unknown>"
        val bucket = payloadTimeMs / (intervalMinutes * 60_000L)
        val previous = lastBucketBySensor.put(key, bucket)
        return previous == null || previous != bucket
    }
}
