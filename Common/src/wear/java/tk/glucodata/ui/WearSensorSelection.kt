package tk.glucodata.ui

import tk.glucodata.Log
import tk.glucodata.SensorIdentity
import tk.glucodata.SensorVisuals
import tk.glucodata.WearSensorSelectionSync

/**
 * Which sensors the watch shows, and in what order — the phone's answer.
 *
 * The watch used to decide this on its own: the sensor the user had tapped if
 * it was fresh, otherwise whichever had reported most recently. With two live
 * sensors that is a coin toss every reading, so the chart, the sensor list and
 * the complications each showed a different one from minute to minute and none
 * of them agreed with the phone.
 *
 * The phone's selection ([tk.glucodata.MultiSensorSelection]) is mirrored here
 * with the display preferences; the first entry is the primary, the rest are
 * drawn beside it as peers. Tapping a sensor asks the phone to make it the
 * primary, and applies that at once so the watch does not wait on the round
 * trip.
 */
object WearSensorSelection {
    private const val TAG = "WearSensorSelection"

    /** The sensors on show, primary first. */
    @JvmStatic
    fun selected(): List<String> = WearSensorSelectionSync.selected()

    /** The sensor the screens draw first. */
    @JvmStatic
    fun resolve(): String? = WearSensorSelectionSync.primary()

    /** The sensors drawn beside the primary, in the phone's order. */
    @JvmStatic
    fun peers(): List<String> = selected().drop(1)

    /** The colour each selected sensor is drawn in; the phone assigns the same. */
    @JvmStatic
    fun colors(): Map<String, Int> =
        runCatching { SensorVisuals.distinctColorArgbMap(selected()) }.getOrDefault(emptyMap())

    @JvmStatic
    fun colorOf(sensorId: String?, colors: Map<String, Int> = colors()): Int? =
        colors.entries.firstOrNull { (selected, _) -> SensorIdentity.matches(selected, sensorId) }?.value

    /** Makes [sensorId] the primary, here and on the phone. */
    @JvmStatic
    fun makePrimary(sensorId: String?) {
        val serial = sensorId?.trim()?.takeIf { it.isNotEmpty() } ?: return
        Log.i(TAG, "primary sensor -> $serial")
        WearSensorSelectionSync.requestPrimary(serial)
        WearGlucoseStore.refresh(force = true)
    }
}
