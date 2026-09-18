package tk.glucodata.alerts

/** Restore editable settings without arming or disarming the alarm. */
fun AlertConfig.resetToDefaults(isMmol: Boolean): AlertConfig =
    AlertDefaults.defaultConfig(type, isMmol).copy(enabled = enabled)

/** Keep custom identity and an existing snooze when restoring creation defaults. */
fun CustomAlertConfig.resetToDefaults(isMmol: Boolean): CustomAlertConfig =
    CustomAlertConfig(
        id = id,
        name = name,
        type = type,
        threshold = if (type == CustomAlertType.HIGH) {
            if (isMmol) 10f else 180f
        } else {
            if (isMmol) 3.9f else 70f
        },
        enabled = enabled,
        snoozedUntil = snoozedUntil
    )
