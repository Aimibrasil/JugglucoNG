package tk.glucodata

/**
 * Registration seam for [NightscoutTreatmentImportBridge] (plan P1/Q1).
 *
 * NightscoutFollowerManager used to find
 * `tk.glucodata.data.journal.NightscoutJournalFollowerImporter` by name. No keep
 * rule protected it, so in a minified build the lookup threw and followed
 * treatments were silently never imported. Explicit registration leaves ordinary
 * interface calls behind.
 */
object NightscoutTreatmentImportAccess {
    @Volatile
    private var bridge: NightscoutTreatmentImportBridge? = null

    @JvmStatic
    fun register(bridge: NightscoutTreatmentImportBridge) {
        this.bridge = bridge
    }

    @JvmStatic
    fun importTreatments(sensorId: String, treatmentsJson: String): Int =
        bridge?.importTreatments(sensorId, treatmentsJson) ?: 0
}
