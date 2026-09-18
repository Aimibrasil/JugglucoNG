package tk.glucodata

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The live value handed to the notification and the alert engine is calibrated
 * unless the driver already did it. On 2026-09-19 AiDEX in raw-primary view
 * fed the bare raw lane to alerts — LOW at 3.4 mmol/L against a calibrated
 * 5.9 on screen — because the gate keyed on "stores its own Room rows" instead.
 */
class LiveCalibrationPolicyTests {

    @Test
    fun aNativeDriverIsAlwaysCalibratedOnTheLivePath() {
        assertTrue(LiveCalibrationPolicy.appliesGenericCalibration(managedDriver = false, integratesUserCalibration = false))
    }

    @Test
    fun aManagedDriverThatDoesNotIntegrateCalibrationIsCalibratedLikeAiDex() {
        assertTrue(LiveCalibrationPolicy.appliesGenericCalibration(managedDriver = true, integratesUserCalibration = false))
    }

    @Test
    fun aManagedDriverThatFoldsCalibrationInItselfIsLeftAloneLikeSibionicsAuto() {
        assertFalse(LiveCalibrationPolicy.appliesGenericCalibration(managedDriver = true, integratesUserCalibration = true))
    }

    /**
     * The gate must be asked per lane, and the calibration must be resolved for
     * the reading's own sensor rather than whichever sensor is main.
     */
    @Test
    fun theLivePathAsksTheDriverPerLaneAndCalibratesForItsOwnSensor() {
        val source = File("src/main/java/tk/glucodata/SuperGattCallback.java").readText()
        assertFalse(
            "storing its own Room rows is not a reason to skip calibration",
            source.contains("shouldApplyGenericLiveCalibration = !liveRoomStorage"),
        )
        assertTrue(
            "the driver is asked whether it integrates calibration for the primary lane",
            source.contains("LiveCalibrationPolicy.appliesGenericCalibration(true, managed.integratesUserCalibration(isRawMode))"),
        )
        assertTrue(
            "the warm-up raw branch calibrates for the reading's sensor",
            source.contains("CalibrationAccess.getCalibratedValue(glucoseToUse, timmsec, true, false, SerialNumber)"),
        )
        assertTrue(
            "the live branch calibrates for the reading's sensor",
            source.contains("CalibrationAccess.getCalibratedValue(glucoseToUse, timmsec, isRawMode, false, SerialNumber)"),
        )
    }
}
