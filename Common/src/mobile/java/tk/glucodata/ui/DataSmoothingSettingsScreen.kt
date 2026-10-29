package tk.glucodata.ui

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.material.icons.filled.Timeline
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

    // Smoothing applied nowhere is smoothing off, which the switch at the top already says,
    // so the last place that is smoothed stays on. The same holds for fewer readings, whose
    // own switch is the way to turn it off.
    val smoothedCount = listOf(targets.chart, targets.current, targets.sent).count { it }
    val fewerCount = listOf(targets.fewerChart, targets.fewerCurrent, targets.fewerSent).count { it }
    val fewerOn = fewerCount > 0
    fun smoothEnabled(on: Boolean) = isEnabled && !(on && smoothedCount == 1)
    fun fewerEnabled(on: Boolean) = isEnabled && !(on && fewerCount == 1)

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
                    SettingsSwitchItem(
                        title = stringResource(R.string.data_smoothing_target_chart_title),
                        subtitle = stringResource(R.string.data_smoothing_target_chart_desc),
                        checked = targets.chart,
                        onCheckedChange = { viewModel.setDataSmoothingTargets(targets.copy(chart = it)) },
                        icon = Icons.AutoMirrored.Filled.ShowChart,
                        iconTint = MaterialTheme.colorScheme.primary,
                        position = CardPosition.TOP,
                        enabled = smoothEnabled(targets.chart)
                    )
                    SettingsSwitchItem(
                        title = stringResource(R.string.data_smoothing_target_current_title),
                        subtitle = stringResource(R.string.data_smoothing_target_current_desc),
                        checked = targets.current,
                        onCheckedChange = { viewModel.setDataSmoothingTargets(targets.copy(current = it)) },
                        icon = Icons.Default.WaterDrop,
                        iconTint = MaterialTheme.colorScheme.primary,
                        position = CardPosition.MIDDLE,
                        enabled = smoothEnabled(targets.current)
                    )
                    SettingsSwitchItem(
                        title = stringResource(R.string.data_smoothing_target_sent_title),
                        subtitle = stringResource(R.string.data_smoothing_target_sent_desc),
                        checked = targets.sent,
                        onCheckedChange = { viewModel.setDataSmoothingTargets(targets.copy(sent = it)) },
                        icon = Icons.AutoMirrored.Filled.Send,
                        iconTint = MaterialTheme.colorScheme.tertiary,
                        position = CardPosition.BOTTOM,
                        enabled = smoothEnabled(targets.sent)
                    )
                }

                // Fewer readings is chosen per place, apart from smoothing: switching it on
                // starts where readings are smoothed, and the rows it reveals say where it goes.
                Spacer(Modifier.height(24.dp))
                SettingsSwitchItem(
                    title = stringResource(R.string.data_smoothing_collapse_title),
                    subtitle = collapseSubtitle,
                    checked = fewerOn,
                    onCheckedChange = { on ->
                        viewModel.setDataSmoothingTargets(
                            if (on) {
                                targets.copy(
                                    fewerChart = targets.chart,
                                    fewerCurrent = targets.current,
                                    fewerSent = targets.sent
                                )
                            } else {
                                targets.copy(fewerChart = false, fewerCurrent = false, fewerSent = false)
                            }
                        )
                    },
                    icon = Icons.Default.FilterAlt,
                    iconTint = MaterialTheme.colorScheme.secondary,
                    position = if (fewerOn) CardPosition.TOP else CardPosition.SINGLE,
                    animatePosition = true,
                    enabled = isEnabled
                )
                AnimatedVisibility(
                    visible = fewerOn,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column {
                        Column(
                            modifier = Modifier.padding(top = 2.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            SettingsSwitchItem(
                                title = stringResource(R.string.data_smoothing_target_chart_title),
                                checked = targets.fewerChart,
                                onCheckedChange = { viewModel.setDataSmoothingTargets(targets.copy(fewerChart = it)) },
                                icon = Icons.AutoMirrored.Filled.ShowChart,
                                iconTint = MaterialTheme.colorScheme.primary,
                                position = CardPosition.MIDDLE,
                                enabled = fewerEnabled(targets.fewerChart)
                            )
                            SettingsSwitchItem(
                                title = stringResource(R.string.data_smoothing_target_current_title),
                                checked = targets.fewerCurrent,
                                onCheckedChange = { viewModel.setDataSmoothingTargets(targets.copy(fewerCurrent = it)) },
                                icon = Icons.Default.WaterDrop,
                                iconTint = MaterialTheme.colorScheme.primary,
                                position = CardPosition.MIDDLE,
                                enabled = fewerEnabled(targets.fewerCurrent)
                            )
                            SettingsSwitchItem(
                                title = stringResource(R.string.data_smoothing_target_sent_title),
                                checked = targets.fewerSent,
                                onCheckedChange = { viewModel.setDataSmoothingTargets(targets.copy(fewerSent = it)) },
                                icon = Icons.AutoMirrored.Filled.Send,
                                iconTint = MaterialTheme.colorScheme.tertiary,
                                position = CardPosition.BOTTOM,
                                enabled = fewerEnabled(targets.fewerSent)
                            )
                        }
                        Text(
                            text = stringResource(R.string.data_smoothing_preserve_originals),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp)
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))
                SettingsSwitchItem(
                    title = stringResource(R.string.data_smoothing_causal_title),
                    subtitle = stringResource(R.string.data_smoothing_causal_desc),
                    checked = causal,
                    onCheckedChange = { viewModel.setDataSmoothingCausal(it) },
                    icon = Icons.Default.Timeline,
                    iconTint = MaterialTheme.colorScheme.primary,
                    position = CardPosition.SINGLE,
                    enabled = isEnabled
                )
            }
        }
    }
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
