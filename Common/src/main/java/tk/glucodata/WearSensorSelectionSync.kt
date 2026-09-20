package tk.glucodata

/**
 * Keeps the watch on the same sensors as the phone.
 *
 * With two sensors running, the watch used to choose for itself: whichever had
 * reported most recently became the one on screen, and every synced chunk moved
 * native's "current sensor" slot to the sensor it carried. Two live sensors
 * report alternately, so the complications, the sensor list and the home
 * screen's sensor card each flipped between them on their own schedule, while
 * the phone sat on one primary with the other drawn beside it.
 *
 * The phone's selection — [MultiSensorSelection], primary first — now travels
 * with the mirrored display preferences ([WearPrefsSync]), and everything on the
 * watch resolves through [selected]. The native slot is a consequence of that
 * choice, not an input to it: [alignCurrentSensor] moves it to the primary
 * whenever a chunk lands or the mirrored order changes.
 *
 * Choosing a sensor on the watch goes the other way: [requestPrimary] applies
 * it locally, so it takes effect at once and works with the phone out of reach,
 * and asks the phone to make the same choice. The phone applies and pushes its
 * preferences back, which is how the two converge if it disagreed.
 */
object WearSensorSelectionSync {
    private const val LOG_ID = "WearSensorSelectionSync"

    /** The sensors this device displays, primary first, as the phone lists them. */
    @JvmStatic
    fun selected(fallbackPrimary: String? = null): List<String> {
        val primary = runCatching { SensorIdentity.resolveMainSensor() }.getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: fallbackPrimary
        return runCatching { NotificationMultiSensorSource.selectedSensorIds(primary) }
            .getOrDefault(emptyList())
    }

    /** The sensor the screens draw first. */
    @JvmStatic
    fun primary(fallbackPrimary: String? = null): String? =
        selected(fallbackPrimary).firstOrNull() ?: fallbackPrimary

    /**
     * Points native's current-sensor slot at the selection's primary. Returns
     * the primary. [fallback] is adopted when nothing else is selectable — a
     * watch receiving its first chunk before any preferences have arrived.
     */
    @JvmStatic
    fun alignCurrentSensor(fallback: String? = null): String? {
        val current = runCatching { SensorIdentity.resolveMainSensor() }.getOrNull()
            ?.takeIf { it.isNotBlank() }
        val primary = primary(current ?: fallback) ?: return current
        if (current == null || !SensorIdentity.matches(current, primary)) {
            runCatching { SensorBluetooth.setCurrentSensorSelection(primary) }
                .onFailure { Log.stack(LOG_ID, "alignCurrentSensor", it) }
            if (Log.doLog) Log.i(LOG_ID, "current sensor ${current ?: "-"} -> $primary")
        }
        return primary
    }

    /**
     * Makes [serial] the primary sensor. Applied here first, then asked of the
     * phone; on the phone itself this is what the sensor list's tap does.
     */
    @JvmStatic
    fun requestPrimary(serial: String?) {
        val target = serial?.trim()?.takeIf { SensorIdentity.isUsableSensorId(it) } ?: return
        applyPrimary(target)
        if (Applic.isWearable) {
            runCatching {
                MessageSender.getMessageSender()
                    ?.sendMainSensorCommand(target.toByteArray(Charsets.UTF_8))
            }.onFailure { Log.stack(LOG_ID, "requestPrimary", it) }
        }
    }

    /** Phone: applies a watch's choice. The pushed preferences carry the result back. */
    @JvmStatic
    fun onCommand(data: ByteArray?) {
        if (Applic.isWearable) return
        val serial = data?.toString(Charsets.UTF_8)?.trim()
            ?.takeIf { SensorIdentity.isUsableSensorId(it) }
        if (serial == null) {
            Log.w(LOG_ID, "ignoring unusable main-sensor command")
            return
        }
        applyPrimary(serial)
    }

    private fun applyPrimary(serial: String) {
        runCatching {
            MultiSensorSelection.moveToFront(
                serial,
                NotificationMultiSensorSource.candidateSensorIds(serial),
            )
            SensorBluetooth.setCurrentSensorSelection(serial)
            // The phone reads its history from Room; the sensor list's own
            // tap merges the new primary's native history in, so this does too.
            if (!Applic.isWearable) HistorySyncAccess.mergeFullSyncForSensor(serial)
            UiRefreshBus.requestDataRefresh()
        }.onFailure { Log.stack(LOG_ID, "applyPrimary", it) }
    }
}
