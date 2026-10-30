package tk.glucodata.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** Baseline of the existing centered date text in its unchanged 32dp container. */
@Composable
internal fun chartDateTextBaseline(date: String, topInset: Dp): Float {
    val height = with(LocalDensity.current) { 32.dp.roundToPx() }
    val text = rememberTextMeasurer().measure(
        text = date,
        style = MaterialTheme.typography.labelLarge,
        constraints = Constraints(maxHeight = height),
        maxLines = 1,
        softWrap = false
    )
    return with(LocalDensity.current) { topInset.toPx() } +
        (height - text.size.height) / 2 + text.firstBaseline
}

/** Original compact calibration pill, centered on its timestamp and aligned vertically to the date. */
@Composable
internal fun ChartCalibrationValueChip(
    value: String,
    anchorX: Float,
    textBaseline: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val style = MaterialTheme.typography.labelMediumEmphasized
    val text = rememberTextMeasurer().measure(value, style = style, maxLines = 1, softWrap = false)
    val iconHeight = with(LocalDensity.current) { 12.dp.roundToPx() }
    val baselineOffset = (maxOf(iconHeight, text.size.height) - text.size.height) / 2 + text.firstBaseline
    val shape = RoundedCornerShape(8.dp)
    Surface(
        modifier = modifier
            .offset { IntOffset(anchorX.toInt(), (textBaseline - baselineOffset).roundToInt()) }
            .graphicsLayer { translationX = -size.width / 2f }
            .clip(shape)
            .clickable(onClick = onClick),
        shape = shape,
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
            Text(value, style = style, maxLines = 1, softWrap = false)
        }
    }
}
