package tk.glucodata.alerts

import org.junit.Assert.*
import org.junit.Test

class AlertConfigResetTests {
    @Test fun standardResetUsesTypeDefaultsAndPreservesEnabledInBothUnits() {
        for (type in AlertType.settingsEntries) for (mmol in listOf(false, true)) {
            for (enabled in listOf(false, true)) {
                val changed = AlertConfig(type, enabled = enabled, threshold = 222f,
                    customSoundUri = "content://custom", soundEnabled = false,
                    timeRangeEnabled = true, activeStartHour = 22, activeEndHour = 6,
                    retryEnabled = true, retryCount = 17, soundDelayEnabled = true,
                    soundDelaySeconds = 25, deltaCount = 8, forecastMinutes = 60)
                val reset = changed.resetToDefaults(mmol)
                assertEquals(AlertDefaults.defaultConfig(type, mmol).copy(enabled = enabled), reset)
                assertEquals(reset, reset.resetToDefaults(mmol))
            }
        }
    }

    @Test fun customResetRetainsIdentityEnabledAndSnoozeButRestoresCreationSettings() {
        for (type in CustomAlertType.entries) for (mmol in listOf(false, true)) {
            for (enabled in listOf(false, true)) {
                val changed = CustomAlertConfig(id = "night", name = "Night alarm", type = type,
                    enabled = enabled, threshold = 222f, startTimeMinutes = 1320, endTimeMinutes = 360,
                    sound = false, vibrate = false, flash = true, soundUri = "content://custom",
                    style = "notification", overrideDnd = true, retryEnabled = true,
                    retryCount = 17, durationSeconds = 60, snoozedUntil = 123456789L)
                val reset = changed.resetToDefaults(mmol)
                val threshold = if (type == CustomAlertType.HIGH) {
                    if (mmol) 10f else 180f
                } else if (mmol) 3.9f else 70f
                assertEquals(CustomAlertConfig(id = "night", name = "Night alarm", type = type,
                    threshold = threshold, enabled = enabled, snoozedUntil = 123456789L), reset)
            }
        }
    }
}
