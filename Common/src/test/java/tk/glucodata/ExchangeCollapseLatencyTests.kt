package tk.glucodata

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Replays a sensor that reports every minute through the same pieces
 * SuperGattCallback.emitExchangeOutputs uses to build the payload it hands to the
 * xDrip broadcast: prepareRecentPointsForCurrent -> exchangeTargetTimeMillis ->
 * resolveFromLive -> ExchangeUpdateGate.
 *
 * Shape taken from the field trace: readings every 60 s, each one reaching the
 * callback ~4 s before its own timestamp, smoothing window 3 min.
 */
class ExchangeCollapseLatencyTests {
    private val minute = 60_000L
    private val arrivalLeadMs = 4_000L
    private val sensorId = "test-sensor"

    /** Epoch-aligned to every collapse interval used below (2, 3, 4, 5 min). */
    private val base = 60L * 60 * 60 * minute

    private data class Emission(
        val readingTimeMs: Long,
        val payloadTimeMs: Long,
        val payloadValue: Float,
        val emitted: Boolean
    )

    /**
     * @param collapse what the payload is resolved with (the snapshot's collapse flag).
     * @param gateCollapse what the gate is told; the gate only dedupes when this is true.
     */
    private fun replay(
        readings: Int,
        smoothingMinutes: Int,
        collapse: Boolean,
        gateCollapse: Boolean,
        valueAt: (Int) -> Float = { 100f + it }
    ): List<Emission> {
        val gate = ExchangeUpdateGate()
        val history = ArrayList<GlucosePoint>()
        val out = ArrayList<Emission>()
        for (k in 0 until readings) {
            val stamp = base + k * minute
            val current = CurrentGlucoseSource.Snapshot(
                timeMillis = stamp,
                valueText = "",
                numericValue = valueAt(k),
                rawNumericValue = Float.NaN,
                rate = 0f,
                sensorId = sensorId,
                sensorGen = 0,
                index = k,
                source = "test"
            )
            val processed = CurrentDisplaySource.prepareRecentPointsForCurrent(
                recentPoints = history.toList(),
                current = current,
                historyStart = 0L,
                viewMode = 0,
                smoothAllData = true,
                smoothingMinutes = smoothingMinutes,
                collapseChunks = collapse,
                nowMillis = stamp - arrivalLeadMs
            )
            val target = CurrentDisplaySource.exchangeTargetTimeMillis(collapse, processed, stamp)
            val snapshot = requireNotNull(
                CurrentDisplaySource.resolveFromLive(
                    liveValueText = null,
                    liveNumericValue = valueAt(k),
                    rate = 0f,
                    targetTimeMillis = target,
                    sensorId = sensorId,
                    sensorGen = 0,
                    index = k,
                    source = "test",
                    recentPoints = processed,
                    viewMode = 0,
                    isMmol = false
                )
            )
            out += Emission(
                readingTimeMs = stamp,
                payloadTimeMs = snapshot.timeMillis,
                payloadValue = snapshot.primaryValue,
                emitted = gate.shouldEmit(sensorId, snapshot.timeMillis, gateCollapse)
            )
            history += GlucosePoint(stamp, valueAt(k), 0f)
        }
        return out
    }

    private val steadyState = 6 // skip the warm-up before the first bucket completes

    @Test
    fun collapseOn_sendsOncePerIntervalWithAStaleChunkTimestamp() {
        val run = replay(readings = 18, smoothingMinutes = 3, collapse = true, gateCollapse = true)
            .drop(steadyState)

        val sent = run.filter { it.emitted }
        assertEquals("12 readings, interval 3 min", 4, sent.size)
        sent.forEach {
            assertEquals(
                "the payload carries the last point of the previous chunk, not the reading",
                2 * minute,
                it.readingTimeMs - it.payloadTimeMs
            )
        }
    }

    @Test
    fun collapseOn_neverSendsTheNewestReadingsOwnTime() {
        val run = replay(readings = 18, smoothingMinutes = 3, collapse = true, gateCollapse = true)
            .drop(steadyState)

        assertTrue(run.none { it.emitted && it.payloadTimeMs == it.readingTimeMs })
    }

    @Test
    fun collapseOff_sendsEveryReadingWithItsOwnTimestamp() {
        val run = replay(readings = 18, smoothingMinutes = 3, collapse = false, gateCollapse = false)
            .drop(steadyState)

        assertEquals(12, run.count { it.emitted })
        run.forEach { assertEquals(it.readingTimeMs, it.payloadTimeMs) }
    }

    // --- outputs that drive a closed loop (xDrip broadcast, xInfuus) -----------------

    private fun collapseForLoopFeed(collapsePref: Boolean = true) =
        DataSmoothing.collapseForExchangeSnapshot(
            smoothingMinutes = 3,
            graphOnly = false,
            exchangeOutputsOnly = false,
            collapseChunks = collapsePref,
            liveLoopFeed = true
        )

    @Test
    fun loopFeed_neverCollapses_whateverTheUserSettingsSay() {
        for (graphOnly in listOf(false, true)) {
            for (exchangeOnly in listOf(false, true)) {
                assertEquals(
                    false,
                    DataSmoothing.collapseForExchangeSnapshot(
                        smoothingMinutes = 3,
                        graphOnly = graphOnly,
                        exchangeOutputsOnly = exchangeOnly,
                        collapseChunks = true,
                        liveLoopFeed = true
                    )
                )
            }
        }
    }

    @Test
    fun chunkedTargets_keepTheirCollapseDecision() {
        for (graphOnly in listOf(false, true)) {
            for (exchangeOnly in listOf(false, true)) {
                for (pref in listOf(false, true)) {
                    assertEquals(
                        DataSmoothing.shouldCollapseExchangeOutputs(3, graphOnly, exchangeOnly, pref),
                        DataSmoothing.collapseForExchangeSnapshot(3, graphOnly, exchangeOnly, pref, liveLoopFeed = false)
                    )
                }
            }
        }
    }

    @Test
    fun loopFeed_withCollapseOn_receivesEveryReadingWithItsOwnTimestamp() {
        val collapse = collapseForLoopFeed()

        val run = replay(readings = 18, smoothingMinutes = 3, collapse = collapse, gateCollapse = collapse)
            .drop(steadyState)

        assertEquals(12, run.count { it.emitted })
        run.forEach { assertEquals(it.readingTimeMs, it.payloadTimeMs) }
    }

    @Test
    fun loopFeed_valueIsSmoothedFromTheWindowBehindTheReading() {
        val collapse = collapseForLoopFeed()
        val noisy = { k: Int -> 100f + k + if (k % 2 == 0) 4f else -4f }

        val run = replay(readings = 18, smoothingMinutes = 3, collapse = collapse, gateCollapse = collapse, valueAt = noisy)
            .drop(steadyState)

        run.forEachIndexed { i, e ->
            val k = steadyState + i
            val trailing = (k - 3..k).map(noisy)
            assertTrue(
                "reading $k: ${e.payloadValue} outside the trailing window ${trailing.min()}..${trailing.max()}",
                e.payloadValue >= trailing.min() - 0.001f && e.payloadValue <= trailing.max() + 0.001f
            )
        }
    }

    @Test
    fun loopFeed_underGraphOnly_goesOutAsMeasuredEvenWithCollapseOn() {
        // d7f827240 pulls exchange smoothing back on under "graph only" for the sake of collapse.
        // A loop feed does not collapse, so it has no such reason and honours "graph only".
        assertEquals(
            false,
            DataSmoothing.smoothExchangeSnapshot(3, graphOnly = true, exchangeOutputsOnly = false, collapseChunks = true, liveLoopFeed = true)
        )
        assertEquals(
            true,
            DataSmoothing.smoothExchangeSnapshot(3, graphOnly = true, exchangeOutputsOnly = false, collapseChunks = true, liveLoopFeed = false)
        )
        assertEquals(
            true,
            DataSmoothing.smoothExchangeSnapshot(3, graphOnly = false, exchangeOutputsOnly = false, collapseChunks = true, liveLoopFeed = true)
        )
        assertEquals(
            false,
            DataSmoothing.smoothExchangeSnapshot(0, graphOnly = false, exchangeOutputsOnly = false, collapseChunks = true, liveLoopFeed = true)
        )
    }

    @Test
    fun deviceConfig_exchangeOnlyPlusCollapse_loopFeedStaysSmoothedButNotChunked() {
        // Reported on the affected device: "smooth only exchange outputs" + "collapse into chunks".
        fun snapshot(liveLoopFeed: Boolean) = Pair(
            DataSmoothing.smoothExchangeSnapshot(3, graphOnly = false, exchangeOutputsOnly = true, collapseChunks = true, liveLoopFeed = liveLoopFeed),
            DataSmoothing.collapseForExchangeSnapshot(3, graphOnly = false, exchangeOutputsOnly = true, collapseChunks = true, liveLoopFeed = liveLoopFeed)
        )

        assertEquals(Pair(true, true), snapshot(liveLoopFeed = false)) // Nightscout & co. unchanged
        assertEquals(Pair(true, false), snapshot(liveLoopFeed = true)) // xDrip: smoothed value, real time, every reading
    }
}
