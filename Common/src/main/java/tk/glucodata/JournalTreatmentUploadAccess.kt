package tk.glucodata

/**
 * Registration seam for [JournalTreatmentUploadBridge] (plan P1/Q1).
 *
 * NightPost used to find `tk.glucodata.data.journal.JournalTreatmentUploader` by
 * name and invoke `uploadAll`/`getReceiveTreatments`. No keep rule protected it,
 * so in a minified build the lookup threw and every treatment upload silently
 * failed. Explicit registration leaves ordinary interface calls behind.
 */
object JournalTreatmentUploadAccess {
    @Volatile
    private var bridge: JournalTreatmentUploadBridge? = null

    @JvmStatic
    fun register(bridge: JournalTreatmentUploadBridge) {
        this.bridge = bridge
    }

    /** False without a journal, so NightPost treats uploads as a no-op success. */
    @JvmStatic
    fun getReceiveTreatments(): Boolean = bridge?.getReceiveTreatments() ?: false

    @JvmStatic
    fun uploadAll(useV3: Boolean): Boolean = bridge?.uploadAll(useV3) ?: true
}
