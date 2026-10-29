package tk.glucodata.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.toArgb
import kotlin.math.abs
import kotlin.math.min

/**
 * The lines of a forecast comparison: what happened, the standard forecast, the adaptive one.
 *
 * Each line differs in two ways, pattern and colour, so either alone tells them apart. Colour
 * alone is not enough: with dynamic colour from a muted wallpaper, `primary` and `tertiary`
 * come out as the same pale lavender, which is how the two forecasts once looked identical.
 */
internal enum class ForecastLineStyle {
    SOLID,
    DASHED,
    DOTTED;

    fun pathEffect(density: Float): PathEffect? = when (this) {
        SOLID -> null
        DASHED -> PathEffect.dashPathEffect(floatArrayOf(10f * density, 6f * density))
        // A near-zero dash with a round cap draws a dot per interval.
        DOTTED -> PathEffect.dashPathEffect(floatArrayOf(0.01f, 6f * density))
    }

    val cap: StrokeCap get() = if (this == DOTTED) StrokeCap.Round else StrokeCap.Butt
}

object ForecastPalette {
    /** The standard forecast: the app's own colour, as on the dashboard chart. */
    @Composable
    @ReadOnlyComposable
    fun standard(): Color = MaterialTheme.colorScheme.primary

    /**
     * The adaptive forecast: amber or teal, whichever sits further in hue from [standard], so
     * it never blends into the theme's colour whatever the wallpaper made of it.
     */
    @Composable
    @ReadOnlyComposable
    fun adaptive(): Color {
        val dark = isAppInDarkTheme()
        val amber = if (dark) Color(0xFFFFB74D) else Color(0xFFB45309)
        val teal = if (dark) Color(0xFF4DD0C4) else Color(0xFF00796B)
        val primary = standard()
        return if (hueDistance(primary, amber) >= hueDistance(primary, teal)) amber else teal
    }

    private fun hueDistance(a: Color, b: Color): Float {
        val ha = FloatArray(3).also { android.graphics.Color.colorToHSV(a.toArgb(), it) }
        val hb = FloatArray(3).also { android.graphics.Color.colorToHSV(b.toArgb(), it) }
        // A greyish colour has no meaningful hue: anything saturated is far from it.
        if (ha[1] < 0.2f) return 180f
        val d = abs(ha[0] - hb[0])
        return min(d, 360f - d)
    }
}
