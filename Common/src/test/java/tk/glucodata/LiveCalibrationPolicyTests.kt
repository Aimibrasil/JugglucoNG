package tk.glucodata

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The live value handed to the notification and the alert engine is calibrated
 * unless the driver already did it. On 2026-09-19 AiDEX in raw-primary view
 * fed the bare raw lane to alerts — LOW at 3.4 mmol/L against a calibrated
 * 5.9 on screen — because the gate keyed on "stores its own Room rows" instead.
 */
class LiveCalibrationPolicyTests {

    @Test
    fun aDriverThatDoesNotIntegrateCalibrationIsCalibratedLikeAiDex() {
        assertTrue(LiveCalibrationPolicy.appliesGenericCalibration(integratesUserCalibration = false))
    }

    @Test
    fun aDriverThatFoldsCalibrationInItselfIsLeftAloneLikeSibionicsAuto() {
        assertFalse(LiveCalibrationPolicy.appliesGenericCalibration(integratesUserCalibration = true))
    }
}
