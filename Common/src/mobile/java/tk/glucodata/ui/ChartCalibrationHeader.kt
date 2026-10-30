package tk.glucodata.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.offset
import tk.glucodata.data.calibration.CalibrationEntity
import kotlin.math.roundToInt

@Composable
internal fun calibrationHeaderStripHeight(): Dp {
    val measurer = rememberTextMeasurer()
    val textHeight = measurer.measure(
        text = "0",
        style = MaterialTheme.typography.labelMediumEmphasized,
        maxLines = 1,
        softWrap = false
    ).size.height
    // Keep 4dp of clear space above and below the compact value pill.
    return with(LocalDensity.current) { maxOf(12.dp, textHeight.toDp()) + 8.dp }
}

internal fun chartHeaderTopInset(chipHeight: Dp, backdropProgress: Float): Dp =
    lerp(16.dp + chipHeight, 0.dp, backdropProgress)

/** Include the upper strip in layout and hit testing, using only the host's available gap. */
internal fun Modifier.extendChartTop(gutter: Dp, clearance: Dp): Modifier = layout { measurable, constraints ->
    val extension = minOf(gutter, clearance).coerceAtLeast(0.dp).roundToPx()
    val child = measurable.measure(constraints.offset(vertical = extension))
    layout(child.width, (child.height - extension).coerceAtLeast(0)) {
        child.place(0, -extension)
    }
}

/** Children are the plot, range picker, and calibration strip, in that order. */
@Composable
internal fun ChartPlotLayout(
    topGutter: Dp,
    pickerLift: Dp,
    onPickerOverhangChanged: (Float) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val picker = measurables[1].measure(constraints.copy(minHeight = 0))
        val lift = pickerLift.roundToPx()
        val height = if (constraints.hasBoundedHeight) constraints.maxHeight else picker.height
        val gutter = topGutter.roundToPx().coerceIn(0, height)
        val plotHeight = (height - gutter - picker.height + lift).coerceAtLeast(0)
        val plot = measurables[0].measure(constraints.copy(minHeight = plotHeight, maxHeight = plotHeight))
        val calibration = measurables[2].measure(constraints.copy(minHeight = 0))
        onPickerOverhangChanged((picker.height - lift).toFloat())
        layout(constraints.maxWidth, height) {
            plot.place(0, gutter)
            picker.place(0, gutter + plotHeight - lift, zIndex = 1f)
            calibration.place(0, 0, zIndex = 1f)
        }
    }
}

@Composable
internal fun ChartCalibrationValueRow(
    calibrations: List<CalibrationEntity>,
    viewportStart: () -> Long,
    viewportDuration: () -> Float,
    rightInset: Dp,
    topInset: Dp,
    unit: String,
    onCalibrationClick: ((CalibrationEntity) -> Unit)?
) {
    val locale = java.util.Locale.getDefault()
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val start = viewportStart()
        val duration = viewportDuration().coerceAtLeast(1f)
        val end = start + duration.toLong()
        val dataWidth = (constraints.maxWidth - with(LocalDensity.current) { rightInset.toPx() }).coerceAtLeast(1f)
        calibrations.filter { it.timestamp in start..end }.forEach { calibration ->
            val value = remember(calibration.userValue, unit, locale) {
                String.format(locale, if (unit.contains("mmol", true)) "%.1f" else "%.0f", calibration.userValue)
            }
            val x = dataWidth * ((calibration.timestamp - start).toFloat() / duration)
            Surface(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset { IntOffset(x.coerceIn(0f, dataWidth).roundToInt(), (topInset + 4.dp).roundToPx()) }
                    .graphicsLayer { translationX = -size.width / 2f }
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onCalibrationClick?.invoke(calibration) },
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shadowElevation = 0.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.WaterDrop,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(value, style = MaterialTheme.typography.labelMediumEmphasized, maxLines = 1, softWrap = false)
                }
            }
        }
    }
}
