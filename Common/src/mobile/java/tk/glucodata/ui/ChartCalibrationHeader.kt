package tk.glucodata.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.AlignmentLine
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.dp
import tk.glucodata.data.calibration.CalibrationEntity
import kotlin.math.roundToInt

/** Fixed corner anchors, with calibration values immediately before the date. */
@Composable
internal fun ChartHeaderRow(
    hasCalibrations: Boolean,
    hasDate: Boolean,
    start: @Composable () -> Unit,
    calibrations: @Composable () -> Unit,
    end: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    Layout(
        modifier = modifier,
        content = {
            Box { start() }
            // Only the two end labels share height; an expanded insulin panel stays independent.
            ChartHeaderEndGroup(hasCalibrations, hasDate, calibrations, end)
        }
    ) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val startChip = measurables[0].measure(loose)
        val gap = if (startChip.width > 0) 8.dp.roundToPx() else 0
        val endGroup = measurables[1].measure(
            loose.copy(maxWidth = (constraints.maxWidth - startChip.width - gap).coerceAtLeast(0))
        )
        val height = constraints.constrainHeight(maxOf(startChip.height, endGroup.height))
        layout(constraints.maxWidth, height) {
            startChip.placeRelative(0, 0)
            endGroup.placeRelative(constraints.maxWidth - endGroup.width, 0)
        }
    }
}

@Composable
private fun ChartHeaderEndGroup(
    hasCalibrations: Boolean,
    hasDate: Boolean,
    calibrations: @Composable () -> Unit,
    end: @Composable () -> Unit
) {
    Layout(
        modifier = Modifier.height(IntrinsicSize.Min),
        content = {
            Box { calibrations() }
            Box { end() }
        }
    ) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val gap = 8.dp.roundToPx()
        // Preserve date space on narrow screens. Extra calibrations scroll inside their slot.
        val calibrationWidth = if (!hasCalibrations) 0 else if (hasDate) constraints.maxWidth / 2 else constraints.maxWidth
        val values = measurables[0].measure(loose.copy(maxWidth = calibrationWidth))
        val reservedGap = if (values.width > 0) gap else 0
        val date = measurables[1].measure(
            loose.copy(maxWidth = (constraints.maxWidth - values.width - reservedGap).coerceAtLeast(0))
        )
        val endGap = if (values.width > 0 && date.width > 0) gap else 0
        val height = constraints.constrainHeight(maxOf(values.height, date.height))
        layout(constraints.maxWidth, height) {
            date.placeRelative(constraints.maxWidth - date.width, 0)
            values.placeRelative(constraints.maxWidth - date.width - endGap - values.width, 0)
        }
    }
}

/** Both the date and calibration values use this container and label typography. */
@Composable
internal fun ChartHeaderChip(
    color: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable RowScope.() -> Unit
) {
    val shape = RoundedCornerShape(8.dp)
    val reference = rememberTextMeasurer().measure(
        text = "0123456789",
        style = MaterialTheme.typography.labelLarge,
        maxLines = 1,
        softWrap = false
    )
    Surface(
        modifier = modifier.fillMaxHeight().clip(shape).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = shape,
        color = color,
        contentColor = contentColor,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .layout { measurable, constraints ->
                    val child = measurable.measure(constraints.copy(minHeight = 0))
                    val height = constraints.constrainHeight(maxOf(32.dp.roundToPx(), child.height + 8.dp.roundToPx()))
                    val baseline = child[FirstBaseline]
                    // Date truncation can change its line bounds; use one numeric baseline for both chips.
                    val top = if (baseline != AlignmentLine.Unspecified) {
                        ((height - reference.size.height) / 2f + reference.firstBaseline - baseline).roundToInt()
                    } else {
                        (height - child.height) / 2
                    }
                    layout(child.width, height) {
                        child.placeRelative(0, top.coerceIn(0, (height - child.height).coerceAtLeast(0)))
                    }
                }
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
}

@Composable
internal fun ChartCalibrationValueChips(
    calibrations: List<CalibrationEntity>,
    unit: String,
    onCalibrationClick: ((CalibrationEntity) -> Unit)?,
    modifier: Modifier = Modifier
) {
    val locale = java.util.Locale.getDefault()
    if (calibrations.isNotEmpty()) {
        Row(
            modifier = modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            calibrations.forEach { calibration ->
                val value = remember(calibration.userValue, unit, locale) {
                    String.format(locale, if (unit.contains("mmol", true)) "%.1f" else "%.0f", calibration.userValue)
                }
                ChartHeaderChip(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    onClick = { onCalibrationClick?.invoke(calibration) }
                ) {
                    Icon(
                        imageVector = Icons.Filled.WaterDrop,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(value, style = MaterialTheme.typography.labelLarge, maxLines = 1, softWrap = false)
                }
            }
        }
    }
}
