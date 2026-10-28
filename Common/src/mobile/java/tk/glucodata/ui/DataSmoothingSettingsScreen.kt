package tk.glucodata.ui

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AutoGraph
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlin.math.roundToInt
import tk.glucodata.DataSmoothing
import tk.glucodata.R
import tk.glucodata.ui.components.AppTopBar
import tk.glucodata.ui.components.CardPosition
import tk.glucodata.ui.components.IconTile
import tk.glucodata.ui.components.IconTileDefaults
import tk.glucodata.ui.components.MasterSwitchCard
import tk.glucodata.ui.components.SectionLabel
import tk.glucodata.ui.components.SettingsControlsCard
import tk.glucodata.ui.components.SettingsSlider
import tk.glucodata.ui.components.SettingsSwitchItem
import tk.glucodata.ui.components.cardShape
import tk.glucodata.ui.viewmodel.DashboardViewModel

@Composable
fun DataSmoothingSettingsScreen(
    navController: NavController,
    viewModel: DashboardViewModel
) {
    val context = LocalContext.current
    val smoothingMinutes by viewModel.chartSmoothingMinutes.collectAsStateWithLifecycle()
    val targets by viewModel.dataSmoothingTargets.collectAsStateWithLifecycle()
    val causal by viewModel.dataSmoothingCausal.collectAsStateWithLifecycle()

    val isEnabled = smoothingMinutes > 0
    val options = remember { DataSmoothing.enabledMinutesOptions().toList() }
    val configuredMinutes = if (isEnabled) smoothingMinutes else DataSmoothing.getLastEnabledMinutes(context)
    val selectedIndex = options.indexOf(configuredMinutes).coerceAtLeast(0)
    var sliderIndex by rememberSaveable(smoothingMinutes) { mutableFloatStateOf(selectedIndex.toFloat()) }
    val selectedMinutes = options[sliderIndex.roundToInt().coerceIn(0, options.lastIndex)]
    val contentAlpha by animateFloatAsState(
        targetValue = if (isEnabled) 1f else 0.7f,
        label = "dataSmoothingContentAlpha"
    )

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            AppTopBar(
                title = stringResource(R.string.graph_smoothing_title),
                onNavigateBack = { navController.popBackStack() },
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 32.dp)
        ) {
            MasterSwitchCard(
                title = stringResource(R.string.graph_smoothing_title),
                subtitle = if (isEnabled) {
                    stringResource(R.string.data_smoothing_on_desc, smoothingMinutes)
                } else {
                    stringResource(R.string.settings_smoothing_off_desc)
                },
                checked = isEnabled,
                onCheckedChange = { viewModel.setDataSmoothingEnabled(it) },
                icon = Icons.AutoMirrored.Filled.TrendingUp
            )

            Column(modifier = Modifier.alpha(contentAlpha)) {
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    SettingsControlsCard(position = CardPosition.TOP) {
                        SettingsSlider(
                            title = stringResource(R.string.data_smoothing_window_value, selectedMinutes),
                            subtitle = stringResource(R.string.data_smoothing_window_desc),
                            value = sliderIndex,
                            onValueChange = { sliderIndex = it },
                            onValueChangeFinished = {
                                // Read the index now: a track tap can change and finish
                                // before the screen recomposes, leaving selectedMinutes stale.
                                val nextMinutes = options[sliderIndex.roundToInt().coerceIn(0, options.lastIndex)]
                                if (nextMinutes != smoothingMinutes) {
                                    viewModel.setChartSmoothingMinutes(nextMinutes)
                                }
                            },
                            range = 0f..options.lastIndex.toFloat(),
                            steps = (options.size - 2).coerceAtLeast(0),
                            enabled = isEnabled
                        )
                    }
                    // The filter estimates the current and the sent reading; the chart keeps
                    // the averaging window, so with neither of those smoothed it has nothing to do.
                    SettingsSwitchItem(
                        title = stringResource(R.string.data_smoothing_causal_title),
                        subtitle = stringResource(R.string.data_smoothing_causal_desc),
                        checked = causal,
                        onCheckedChange = { viewModel.setDataSmoothingCausal(it) },
                        icon = Icons.Default.AutoGraph,
                        iconTint = MaterialTheme.colorScheme.secondary,
                        position = CardPosition.BOTTOM,
                        enabled = isEnabled && (targets.current || targets.sent)
                    )
                }

                SectionLabel(stringResource(R.string.data_smoothing_scope_title))
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    SmoothingTargetRow(
                        title = stringResource(R.string.data_smoothing_target_chart_title),
                        subtitle = stringResource(R.string.data_smoothing_target_chart_desc),
                        icon = Icons.AutoMirrored.Filled.ShowChart,
                        iconTint = MaterialTheme.colorScheme.primary,
                        smoothed = targets.chart,
                        fewer = targets.fewerChart,
                        onSmoothedChange = { viewModel.setDataSmoothingTargets(targets.copy(chart = it)) },
                        onFewerChange = { viewModel.setDataSmoothingTargets(targets.copy(fewerChart = it)) },
                        enabled = isEnabled,
                        position = CardPosition.TOP
                    )
                    SmoothingTargetRow(
                        title = stringResource(R.string.data_smoothing_target_current_title),
                        subtitle = stringResource(R.string.data_smoothing_target_current_desc),
                        icon = Icons.Default.WaterDrop,
                        iconTint = MaterialTheme.colorScheme.primary,
                        smoothed = targets.current,
                        fewer = targets.fewerCurrent,
                        onSmoothedChange = { viewModel.setDataSmoothingTargets(targets.copy(current = it)) },
                        onFewerChange = { viewModel.setDataSmoothingTargets(targets.copy(fewerCurrent = it)) },
                        enabled = isEnabled,
                        position = CardPosition.MIDDLE
                    )
                    SmoothingTargetRow(
                        title = stringResource(R.string.data_smoothing_target_sent_title),
                        subtitle = stringResource(R.string.data_smoothing_target_sent_desc),
                        icon = Icons.AutoMirrored.Filled.Send,
                        iconTint = MaterialTheme.colorScheme.tertiary,
                        smoothed = targets.sent,
                        fewer = targets.fewerSent,
                        onSmoothedChange = { viewModel.setDataSmoothingTargets(targets.copy(sent = it)) },
                        onFewerChange = { viewModel.setDataSmoothingTargets(targets.copy(fewerSent = it)) },
                        enabled = isEnabled,
                        position = CardPosition.BOTTOM
                    )
                }
                Text(
                    text = stringResource(
                        R.string.data_smoothing_fewer_caption,
                        DataSmoothing.collapseIntervalMinutes(selectedMinutes)
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp)
                )
            }
        }
    }
}

/**
 * One place readings go, with what happens to them there. The two are independent, so they
 * are filter chips rather than a choice: smoothed, thinned to fewer readings, both or neither.
 */
@Composable
private fun SmoothingTargetRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconTint: Color,
    smoothed: Boolean,
    fewer: Boolean,
    onSmoothedChange: (Boolean) -> Unit,
    onFewerChange: (Boolean) -> Unit,
    enabled: Boolean,
    position: CardPosition,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = cardShape(position),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        // Chips under the text make this a three-line item, so the tile sits at the top.
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 12.dp),
            verticalAlignment = Alignment.Top
        ) {
            IconTile(
                icon = icon,
                tint = iconTint,
                containerColor = iconTint.copy(alpha = IconTileDefaults.ContainerAlpha),
            )
            Spacer(Modifier.width(IconTileDefaults.Gap))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TargetChip(stringResource(R.string.data_smoothing_smoothed), smoothed, onSmoothedChange, enabled)
                    TargetChip(stringResource(R.string.data_smoothing_collapse_title), fewer, onFewerChange, enabled)
                }
            }
        }
    }
}

@Composable
private fun TargetChip(label: String, selected: Boolean, onChange: (Boolean) -> Unit, enabled: Boolean) {
    FilterChip(
        selected = selected,
        onClick = { onChange(!selected) },
        label = { Text(label) },
        enabled = enabled,
        leadingIcon = if (selected) {
            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
        } else null,
        shapes = FilterChipDefaults.shapes(),
        colors = FilterChipDefaults.filterChipColors()
    )
}

/** Where smoothing applies, as the Settings row summarises it; null when it applies nowhere. */
internal fun dataSmoothingScopeLabel(targets: DataSmoothing.Targets): Int? =
    scopeLabel(targets.chart, targets.current, targets.sent)

/** Where readings are thinned to fewer, in the same words; null when nowhere. */
internal fun dataSmoothingFewerScopeLabel(targets: DataSmoothing.Targets): Int? =
    scopeLabel(targets.fewerChart, targets.fewerCurrent, targets.fewerSent)

private fun scopeLabel(chart: Boolean, current: Boolean, sent: Boolean): Int? = when {
    chart && current && sent -> R.string.data_smoothing_scope_all
    chart && current -> R.string.data_smoothing_scope_chart_and_current
    chart && sent -> R.string.data_smoothing_scope_graph_and_sent
    current && sent -> R.string.data_smoothing_scope_current_and_sent
    chart -> R.string.data_smoothing_graph_only_title
    current -> R.string.data_smoothing_scope_current_only
    sent -> R.string.data_smoothing_exchange_only_title
    else -> null
}
