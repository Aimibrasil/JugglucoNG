package tk.glucodata.ui.alerts

import java.util.Locale

/** Named resource URIs survive resource-ID reassignment between app updates. */
internal object BundledAlertSounds {
    // Collection names are proper names, identical in every locale.
    val styles = listOf("Contour", "Porcelain", "Halo", "Timber", "Ember", "Juggluco")
    private val cues = setOf("low", "high", "urgent_low", "urgent_high", "falling", "rising", "signal", "reminder", "notice")

    private val originals = mapOf(
        "low" to "siren", "high" to "classic", "notice" to "ghost",
        "reminder" to "nudge", "signal" to "elves", "urgent_low" to "verylow",
        "urgent_high" to "veryhigh", "falling" to "lowsoon", "rising" to "highsoon"
    )

    fun cueFor(alertTypeId: Int): String = tk.glucodata.alerts.AlertSoundDefaults.cueFor(alertTypeId)

    fun uri(packageName: String, style: String, alertTypeId: Int): String {
        require(style in styles)
        if (style == "Juggluco") {
            return "android.resource://$packageName/raw/${originals.getValue(cueFor(alertTypeId))}"
        }
        return "android.resource://$packageName/raw/alert_${style.lowercase(Locale.ROOT)}_${cueFor(alertTypeId)}"
    }

    fun styleFor(uri: String?, packageName: String): String? {
        val rawPrefix = "android.resource://$packageName/raw/"
        if (uri != null && uri.startsWith(rawPrefix) && uri.removePrefix(rawPrefix) in originals.values) {
            return "Juggluco"
        }
        val prefix = rawPrefix + "alert_"
        if (uri == null || !uri.startsWith(prefix)) return null
        val name = uri.removePrefix(prefix)
        return styles.filterNot { it == "Juggluco" }.firstOrNull { style ->
            val stylePrefix = style.lowercase(Locale.ROOT) + "_"
            name.startsWith(stylePrefix) && name.removePrefix(stylePrefix) in cues
        }
    }

    /** Applying a collection globally preserves each destination's alert meaning. */
    fun forAlert(selectedUri: String?, packageName: String, alertTypeId: Int): String? {
        val style = styleFor(selectedUri, packageName) ?: return selectedUri
        return uri(packageName, style, alertTypeId)
    }
}
