package tk.glucodata.ui

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlin.math.roundToInt
import tk.glucodata.DataSmoothing
import tk.glucodata.R
import tk.glucodata.ui.components.AppTopBar
import tk.glucodata.ui.components.CardPosition
import tk.glucodata.ui.components.MasterSwitchCard
import tk.glucodata.ui.components.SectionLabel
import tk.glucodata.ui.components.SettingsSwitchItem
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.vector.ImageVector
import tk.glucodata.ui.components.DisclosingSwitchCard
import tk.glucodata.ui.components.SettingsItem
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
    val contentAlpha by animateFloatAsState(
        targetValue = if (isEnabled) 1f else 0.7f,
        label = "dataSmoothingContentAlpha"
    )

    val selectedMinutes = options[sliderIndex.roundToInt().coerceIn(0, options.lastIndex)]
    val selectedLabel = stringResource(R.string.minutes_short_format, selectedMinutes)
    val collapseIntervalMinutes = DataSmoothing.collapseIntervalMinutes(configuredMinutes)
    val collapseSubtitle = when {
        collapseIntervalMinutes in 1 until configuredMinutes ->
            stringResource(R.string.data_smoothing_collapse_desc_capped, configuredMinutes)
        else ->
            stringResource(R.string.data_smoothing_collapse_desc_match, collapseIntervalMinutes)
    }

    val smoothedAnywhere = targets.chart || targets.current || targets.sent
    val fewerOn = targets.fewerChart || targets.fewerCurrent || targets.fewerSent

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
                subtitle = if (isEnabled && smoothedAnywhere) {
                    stringResource(R.string.data_smoothing_on_desc, smoothingMinutes)
                } else {
                    stringResource(R.string.settings_smoothing_off_desc)
                },
                checked = isEnabled,
                onCheckedChange = { viewModel.setDataSmoothingEnabled(it) },
                icon = Icons.AutoMirrored.Filled.TrendingUp
            )

            Column(modifier = Modifier.alpha(contentAlpha)) {
                Spacer(Modifier.height(16.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (isEnabled) {
                            MaterialTheme.colorScheme.surfaceContainerLow
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerLowest
                        }
                    ),
                    shape = MaterialTheme.shapes.extraLarge,
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.data_smoothing_window_title),
                                    style = MaterialTheme.typography.titleMediumEmphasized
                                )

                                Text(
                                    text = stringResource(R.string.data_smoothing_window_desc),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isEnabled) {
                                    MaterialTheme.colorScheme.surfaceContainerHighest
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerHigh
                                }
                            ) {
                                Text(
                                    text = selectedLabel,
                                    style = MaterialTheme.typography.labelLargeEmphasized,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                )
                            }
                        }

                        Slider(
                            value = sliderIndex,
                            onValueChange = { sliderIndex = it },
                            onValueChangeFinished = {
                                val nextMinutes = options[sliderIndex.roundToInt().coerceIn(0, options.lastIndex)]
                                if (nextMinutes != smoothingMinutes) {
                                    viewModel.setChartSmoothingMinutes(nextMinutes)
                                }
                            },
                            valueRange = 0f..options.lastIndex.toFloat(),
                            steps = (options.size - 2).coerceAtLeast(0),
                            enabled = isEnabled
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.minutes_short_format, options.first()),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = stringResource(R.string.minutes_short_format, options.last()),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                SectionLabel(stringResource(R.string.data_smoothing_scope_title))
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    SmoothingTargetRow(
                        title = stringResource(R.string.data_smoothing_target_chart_title),
                        subtitle = stringResource(R.string.data_smoothing_target_chart_desc),
                        icon = Icons.AutoMirrored.Filled.ShowChart,
                        checked = targets.chart,
                        onCheckedChange = { viewModel.setDataSmoothingTargets(targets.copy(chart = it)) },
                        position = CardPosition.TOP,
                        enabled = isEnabled
                    )
                    SmoothingTargetRow(
                        title = stringResource(R.string.data_smoothing_target_current_title),
                        subtitle = stringResource(R.string.data_smoothing_target_current_desc),
                        icon = Icons.Default.WaterDrop,
                        checked = targets.current,
                        onCheckedChange = { viewModel.setDataSmoothingTargets(targets.copy(current = it)) },
                        position = CardPosition.MIDDLE,
                        enabled = isEnabled
                    )
                    SmoothingTargetRow(
                        title = stringResource(R.string.data_smoothing_target_sent_title),
                        subtitle = stringResource(R.string.data_smoothing_target_sent_desc),
                        icon = Icons.AutoMirrored.Filled.Send,
                        checked = targets.sent,
                        onCheckedChange = { viewModel.setDataSmoothingTargets(targets.copy(sent = it)) },
                        position = CardPosition.BOTTOM,
                        enabled = isEnabled
                    )
                }

                // Fewer readings is a mechanism of its own, so it keeps a switch; where it
                // applies is a scope inside its card. Clearing the last place switches it off.
                Spacer(Modifier.height(24.dp))
                DisclosingSwitchCard(
                    title = stringResource(R.string.data_smoothing_collapse_title),
                    subtitle = collapseSubtitle,
                    icon = Icons.Default.FilterAlt,
                    iconTint = MaterialTheme.colorScheme.secondary,
                    checked = fewerOn,
                    onCheckedChange = { on ->
                        viewModel.setDataSmoothingTargets(
                            if (on) {
                                // Start where readings are smoothed, as the old switch did.
                                targets.copy(
                                    fewerChart = targets.chart || !smoothedAnywhere,
                                    fewerCurrent = targets.current || !smoothedAnywhere,
                                    fewerSent = targets.sent || !smoothedAnywhere
                                )
                            } else {
                                targets.copy(fewerChart = false, fewerCurrent = false, fewerSent = false)
                            }
                        )
                    },
                    position = CardPosition.SINGLE,
                    enabled = isEnabled
                ) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FewerTargetChip(
                            label = stringResource(R.string.data_smoothing_target_chart_title),
                            selected = targets.fewerChart,
                            onClick = { viewModel.setDataSmoothingTargets(targets.copy(fewerChart = !targets.fewerChart)) },
                            enabled = isEnabled
                        )
                        FewerTargetChip(
                            label = stringResource(R.string.data_smoothing_target_current_title),
                            selected = targets.fewerCurrent,
                            onClick = { viewModel.setDataSmoothingTargets(targets.copy(fewerCurrent = !targets.fewerCurrent)) },
                            enabled = isEnabled
                        )
                        FewerTargetChip(
                            label = stringResource(R.string.data_smoothing_target_sent_title),
                            selected = targets.fewerSent,
                            onClick = { viewModel.setDataSmoothingTargets(targets.copy(fewerSent = !targets.fewerSent)) },
                            enabled = isEnabled
                        )
                    }
                    Text(
                        text = stringResource(R.string.data_smoothing_preserve_originals),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.height(8.dp))
                SettingsSwitchItem(
                    title = stringResource(R.string.data_smoothing_causal_title),
                    subtitle = stringResource(R.string.data_smoothing_causal_desc),
                    checked = causal,
                    onCheckedChange = { viewModel.setDataSmoothingCausal(it) },
                    icon = Icons.Default.Functions,
                    iconTint = MaterialTheme.colorScheme.primary,
                    position = CardPosition.SINGLE,
                    enabled = isEnabled
                )
            }
        }
    }
}

/** A place smoothing can apply to: a scope, so a checkbox rather than a switch. */
@Composable
private fun SmoothingTargetRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    position: CardPosition,
    enabled: Boolean,
) {
    SettingsItem(
        title = title,
        subtitle = subtitle,
        icon = icon,
        iconTint = MaterialTheme.colorScheme.primary,
        position = position,
        onClick = if (enabled) {
            { onCheckedChange(!checked) }
        } else {
            null
        },
        trailingContent = {
            Checkbox(checked = checked, onCheckedChange = null, enabled = enabled)
        }
    )
}

@Composable
private fun FewerTargetChip(label: String, selected: Boolean, onClick: () -> Unit, enabled: Boolean) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        enabled = enabled,
        leadingIcon = if (selected) {
            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
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
