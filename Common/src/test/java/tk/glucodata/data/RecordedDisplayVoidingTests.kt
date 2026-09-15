package tk.glucodata.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The seam the record-keeping never specified: a driver that integrates the
 * user's calibration re-measures its whole history on a fingerstick. The
 * record froze the old numbers, so the calibration moved nothing older than
 * the grace window and looked dead. A record whose sensor has rewritten the
 * number it was drawn from is void; one whose number stands is kept.
 */
class RecordedDisplayVoidingTests {

    private companion object {
        const val MINUTE = ReadingDisplay.MINUTE_MS
        const val T0 = 1_800_000_000_000L
        const val SERIAL = "SIBI:0683013AQT9"
    }

    private fun record(minute: Int, shown: Float, viewMode: Int = 0, serial: String = SERIAL) = ReadingDisplay(
        timestamp = ReadingDisplay.minuteOf(T0 + minute * MINUTE),
        sensorSerial = serial,
        displayMgdl = shown,
        viewMode = viewMode,
        calibrationFingerprint = 1L,
        recordedAt = T0,
    )

    private fun reading(minute: Int, value: Float, raw: Float = value, serial: String = SERIAL) = HistoryReading(
        timestamp = T0 + minute * MINUTE + 17_000L,
        sensorSerial = serial,
        value = value,
        rawValue = raw,
        rate = null,
    )

    @Test
    fun aRecordWhoseNumberTheDriverRewroteIsVoid() {
        val records = listOf(record(0, 100f), record(1, 104f), record(2, 108f))
        // A fingerstick lifted the whole line by 12.
        val rewritten = listOf(reading(0, 112f), reading(1, 116f), reading(2, 120f))
        assertEquals(
            records.map { it.timestamp },
            RecordedDisplayVoiding.minutesToVoid(records, SERIAL, rewritten, autoLaneIntegrated = true, rawLaneIntegrated = false),
        )
    }

    @Test
    fun aRecordWhoseNumberStandsIsKeptAndSoIsItsOwnership() {
        // A rebuild for a reason other than calibration replays the same DSP
        // over the same samples and lands on the same numbers. Nothing to void:
        // the record, and who owned the minute, survive.
        val records = listOf(record(0, 100f), record(1, 104f), record(2, 108f))
        val rewritten = listOf(reading(0, 100f), reading(1, 104f), reading(2, 108.004f))
        assertTrue(
            RecordedDisplayVoiding.minutesToVoid(records, SERIAL, rewritten, autoLaneIntegrated = true, rawLaneIntegrated = false).isEmpty(),
        )
    }

    @Test
    fun onlyTheMinutesInsideTheRewriteAreConsidered() {
        val records = listOf(record(0, 100f), record(5, 120f), record(9, 140f))
        val rewritten = listOf(reading(4, 200f), reading(5, 200f), reading(6, 200f))
        assertEquals(
            listOf(record(5, 0f).timestamp),
            RecordedDisplayVoiding.minutesToVoid(records, SERIAL, rewritten, autoLaneIntegrated = true, rawLaneIntegrated = false),
        )
    }

    @Test
    fun aMinuteTheRewriteNoLongerHasAReadingForIsVoidToo() {
        // Inside the rewritten range, but the sensor no longer reports that
        // minute: the record would describe a reading that does not exist.
        val records = listOf(record(0, 100f), record(1, 104f), record(2, 108f))
        val rewritten = listOf(reading(0, 100f), reading(2, 108f))
        assertEquals(
            listOf(record(1, 0f).timestamp),
            RecordedDisplayVoiding.minutesToVoid(records, SERIAL, rewritten, autoLaneIntegrated = true, rawLaneIntegrated = false),
        )
    }

    @Test
    fun anotherSensorsRecordsAreNotTouched() {
        // Two sensors in the same minutes; only the rewriting sensor's records
        // are in question, and only its own readings are looked at.
        val records = listOf(record(0, 100f, serial = "other"), record(1, 104f))
        val rewritten = listOf(reading(0, 150f), reading(1, 150f), reading(0, 300f, serial = "other"))
        assertEquals(
            listOf(record(1, 0f).timestamp),
            RecordedDisplayVoiding.minutesToVoid(records, SERIAL, rewritten, autoLaneIntegrated = true, rawLaneIntegrated = false),
        )
    }

    @Test
    fun aLaneTheDriverDoesNotIntegrateKeepsItsRecord() {
        // Sibionics integrates the auto lane only. A raw-lane record may hold
        // the app's own calibration of the raw number, which differs from the
        // stored raw on purpose; the rewrite says nothing about it.
        val records = listOf(record(0, 100f, viewMode = 0), record(1, 90f, viewMode = 1))
        val rewritten = listOf(reading(0, 112f, raw = 95f), reading(1, 116f, raw = 99f))
        assertEquals(
            listOf(record(0, 0f).timestamp),
            RecordedDisplayVoiding.minutesToVoid(records, SERIAL, rewritten, autoLaneIntegrated = true, rawLaneIntegrated = false),
        )
        assertEquals(
            listOf(record(1, 0f).timestamp),
            RecordedDisplayVoiding.minutesToVoid(records, SERIAL, rewritten, autoLaneIntegrated = false, rawLaneIntegrated = true),
        )
    }

    @Test
    fun aDriverThatIntegratesNothingVoidsNothing() {
        // Every other driver's wholesale rewrite is a re-sync of the same
        // numbers under an app-side calibration; the record is the whole point.
        val records = listOf(record(0, 100f))
        val rewritten = listOf(reading(0, 80f))
        assertTrue(
            RecordedDisplayVoiding.minutesToVoid(records, SERIAL, rewritten, autoLaneIntegrated = false, rawLaneIntegrated = false).isEmpty(),
        )
    }

    @Test
    fun theRepositoryVoidsOnlyAfterADriversBatchReplacedItsHistory() {
        // The voiding is wired to the one write a driver uses to replace its
        // own history, and to nothing else — recording, revising, and the
        // sensor-removal delete keep their own rules.
        val repo = java.io.File("src/mobile/java/tk/glucodata/data/HistoryRepository.kt").readText()
        val batch = repo.substringAfter("fun storeHistoryBatchBlocking").substringBefore("\n    }\n")
        assertTrue(batch.contains("if (stored) repository.voidRecordsRewrittenByDriver("))
        val voiding = repo.substringAfter("suspend fun voidRecordsRewrittenByDriver").substringBefore("\n    }\n")
        assertTrue("gated on the driver integrating the calibration", voiding.contains("integratesUserCalibration"))
        assertTrue("a deletion, never a revision", voiding.contains("deleteAtMinutes"))
    }
}
