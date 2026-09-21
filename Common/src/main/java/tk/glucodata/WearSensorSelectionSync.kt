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
 * Choosing on the watch goes the other way. [requestPrimary] and
 * [requestToggle] are the phone's own two controls — promote a sensor to the
 * primary, show or hide a sensor on the chart — applied locally, so they take
 * effect at once and work with the phone out of reach, then asked of the phone.
 * The phone applies and pushes its preferences back, which is how the two
 * converge if it disagreed.
 */
object WearSensorSelectionSync {
    private const val LOG_ID = "WearSensorSelectionSync"

    /** Wire: `<action>:<serial>`, one command per message. */
    private const val ACTION_PRIMARY = "primary"
    private const val ACTION_TOGGLE = "toggle"

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
     * Makes [serial] the primary sensor — what tapping a peer's chip on the
     * phone's hero does. Applied here first, then asked of the phone.
     */
    @JvmStatic
    fun requestPrimary(serial: String?) {
        val target = serial?.trim()?.takeIf { SensorIdentity.isUsableSensorId(it) } ?: return
        applyPrimary(target)
        send(ACTION_PRIMARY, target)
    }

    /**
     * Shows or hides [serial] on the chart — the check on the phone's sensor
     * card. The last shown sensor cannot be hidden, and hiding the primary
     * promotes the next one, exactly as [MultiSensorSelection.toggle] does it.
     */
    @JvmStatic
    fun requestToggle(serial: String?) {
        val target = serial?.trim()?.takeIf { SensorIdentity.isUsableSensorId(it) } ?: return
        applyToggle(target)
        send(ACTION_TOGGLE, target)
    }

    private fun send(action: String, serial: String) {
        if (!Applic.isWearable) return
        runCatching {
            MessageSender.getMessageSender()
                ?.sendMainSensorCommand(encodeCommand(action, serial).toByteArray(Charsets.UTF_8))
        }.onFailure { Log.stack(LOG_ID, "send $action", it) }
    }

    @JvmStatic
    fun encodeCommand(action: String, serial: String): String = "$action:$serial"

    /** `(action, serial)`, or null when the payload is not a command this build knows. */
    @JvmStatic
    fun decodeCommand(payload: String?): Pair<String, String>? {
        val text = payload?.trim() ?: return null
        val split = text.indexOf(':')
        // A bare serial is the first build's "make primary".
        val action = if (split <= 0) ACTION_PRIMARY else text.substring(0, split)
        val serial = (if (split <= 0) text else text.substring(split + 1)).trim()
        if (action != ACTION_PRIMARY && action != ACTION_TOGGLE) return null
        if (!SensorIdentity.isUsableSensorId(serial)) return null
        return action to serial
    }

    /** Phone: applies a watch's choice. The pushed preferences carry the result back. */
    @JvmStatic
    fun onCommand(data: ByteArray?) {
        if (Applic.isWearable) return
        val (action, serial) = decodeCommand(data?.toString(Charsets.UTF_8)) ?: run {
            Log.w(LOG_ID, "ignoring unusable sensor-selection command")
            return
        }
        when (action) {
            ACTION_PRIMARY -> applyPrimary(serial)
            ACTION_TOGGLE -> applyToggle(serial)
        }
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

    private fun applyToggle(serial: String) {
        runCatching {
            val currentPrimary = SensorIdentity.resolveMainSensor()
            val selected = MultiSensorSelection.toggle(
                sensorId = serial,
                availableSensorIds = NotificationMultiSensorSource.candidateSensorIds(currentPrimary),
                primarySensorId = currentPrimary,
            )
            // Hiding the primary hands the role to the next shown sensor.
            selected.firstOrNull()?.let { primary ->
                if (!SensorIdentity.matches(currentPrimary, primary)) {
                    SensorBluetooth.setCurrentSensorSelection(primary)
                    if (!Applic.isWearable) HistorySyncAccess.mergeFullSyncForSensor(primary)
                }
            }
            UiRefreshBus.requestDataRefresh()
        }.onFailure { Log.stack(LOG_ID, "applyToggle", it) }
    }
}
