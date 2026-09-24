package tk.glucodata

import java.util.concurrent.ConcurrentHashMap

/**
 * Rate limit for exchange outputs under "collapse into chunks": lets the first reading of each
 * interval through and holds back the rest. It decides *whether* to send, never *what*: the
 * payload is always the newest reading under its own timestamp.
 *
 * The interval is counted on the reading's own time, so it does not depend on when the
 * callback happens to run, and only moves forward: an older reading never sends.
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
        // Only a newer interval sends. A reading that arrives out of order (a backfill, an older
        // timestamp) belongs to an interval already sent, and must not make the gate forget it.
        var emit = false
        lastBucketBySensor.compute(key) { _, previous ->
            if (previous == null || bucket > previous) {
                emit = true
                bucket
            } else {
                previous
            }
        }
        return emit
    }
}
