package tk.glucodata.ui

import android.content.Context
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * The tick under a tab switch: the shortest, sharpest primitive the vibrator has, at low
 * strength, so it reads as a crisp tap rather than a buzz. (`PRIMITIVE_LOW_TICK` is softer
 * but low-frequency, which feels duller and longer.)
 *
 * It plays only where it can be rendered cleanly. A device whose actuator supports
 * `PRIMITIVE_TICK` has the kind of motor that makes a crisp, faint tap; an older or
 * cheaper motor would turn the same request into a dull buzz, so those devices get nothing.
 * It is sent as touch feedback, so the system's touch-vibration setting and intensity
 * still decide whether and how strongly it is felt.
 */
class NavigationTick internal constructor(context: Context) {
    private val vibrator: Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) capableVibrator(context) else null

    fun play() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val vibrator = vibrator ?: return
        runCatching {
            vibrator.vibrate(
                VibrationEffect.startComposition()
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, TICK_SCALE)
                    .compose(),
                VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH)
            )
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun capableVibrator(context: Context): Vibrator? = runCatching {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
            ?.takeIf { it.hasVibrator() }
            ?.takeIf { it.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_TICK) }
    }.getOrNull()

    private companion object {
        const val TICK_SCALE = 0.3f
    }
}

@Composable
fun rememberNavigationTick(): NavigationTick {
    val context = LocalContext.current
    return remember(context) { NavigationTick(context.applicationContext) }
}
