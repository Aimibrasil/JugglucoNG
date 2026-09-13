package tk.glucodata.drivers.mq

import org.junit.Assert.*
import org.junit.Test

class MQNativeGlucoseMirrorTests {
    private val result = MQAlgorithm.calculateResult(1, 1.0, 24.0, 175.0, 0.0,
        29.3, 0.0, 2.0, 720.0, 1.0)

    @Test fun nativeBoundaryReceivesTrueMgdlAndSecondsWithSensorOwnership() {
        assertEquals(5.9, result.glucoseMmol, 0.0)
        var called = false
        assertTrue(MQNativeGlucoseMirror.write(1_789_000_000_123L, result, "CFD8EBDDF969") { sec, mgdl, id ->
            called = true
            assertEquals(1_789_000_000L, sec)
            assertEquals("CFD8EBDDF969", id)
            assertEquals((5.9 * MQConstants.MMOL_TO_MGDL).toFloat(), mgdl, 0.0001f)
            // Mirror C++ storeGlucoseStreamSample's internal conversion to mg/dL times ten.
            assertEquals(result.mgdlTimes10, (mgdl * 10f).toInt())
            true
        })
        assertTrue(called)
    }

    @Test fun nativeRejectionIsPropagatedAndInvalidSamplesNeverReachStorage() {
        assertFalse(MQNativeGlucoseMirror.write(1_789_000_000_000L, result, "MQ") { _, _, _ -> false })
        val forbidden: (Long, Float, String) -> Boolean = { _, _, _ -> error("Invalid sample written") }
        assertFalse(MQNativeGlucoseMirror.write(0, result, "MQ", forbidden))
        assertFalse(MQNativeGlucoseMirror.write(1_789_000_000_000L, result, "", forbidden))
        assertFalse(MQNativeGlucoseMirror.write(1_789_000_000_000L,
            result.copy(glucoseMmol = Double.NaN), "MQ", forbidden))
    }
}
