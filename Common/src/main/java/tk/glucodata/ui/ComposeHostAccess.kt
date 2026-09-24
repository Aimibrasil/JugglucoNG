package tk.glucodata.ui

/**
 * Registration seam for [ComposeHost] (plan P1/Q1). MainActivity asks for the
 * registered host instead of resolving `ComposeHostKt` by name.
 */
object ComposeHostAccess {
    @Volatile
    private var host: ComposeHost? = null

    @JvmStatic
    fun register(host: ComposeHost) {
        this.host = host
    }

    /** Null on a flavour without a Compose host (the legacy View UI). */
    @JvmStatic
    fun get(): ComposeHost? = host
}
