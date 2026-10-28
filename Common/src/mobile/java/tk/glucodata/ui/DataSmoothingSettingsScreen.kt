package tk.glucodata.ui

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AutoGraph
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import tk.glucodata.ui.components.SettingsControlsCard
import tk.glucodata.ui.components.SettingsSlider
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
    val collapseChunks by viewModel.dataSmoothingCollapseChunks.collectAsStateWithLifecycle()
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

    val collapseIntervalMinutes = DataSmoothing.collapseIntervalMinutes(configuredMinutes)
    val collapseSubtitle = listOf(
        if (collapseIntervalMinutes in 1 until configuredMinutes) {
            stringResource(R.string.data_smoothing_collapse_desc_capped, configuredMinutes)
        } else {
            stringResource(R.string.data_smoothing_collapse_desc_match, collapseIntervalMinutes)
        },
        stringResource(R.string.data_smoothing_preserve_originals)
    ).joinToString("\n")

    // Smoothing applied nowhere is smoothing off, which the switch at the top already says,
    // so the last target that is on stays on.
    val targetsOn = listOf(targets.chart, targets.current, targets.sent).count { it }
    fun targetEnabled(on: Boolean) = isEnabled && !(on && targetsOn == 1)

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
                SettingsControlsCard {
                    SettingsSlider(
                        title = stringResource(R.string.data_smoothing_window_value, selectedMinutes),
                        subtitle = stringResource(R.string.data_smoothing_window_desc),
                        value = sliderIndex,
                        onValueChange = { sliderIndex = it },
                        onValueChangeFinished = {
                            if (selectedMinutes != smoothingMinutes) {
                                viewModel.setChartSmoothingMinutes(selectedMinutes)
                            }
                        },
                        range = 0f..options.lastIndex.toFloat(),
                        steps = (options.size - 2).coerceAtLeast(0),
                        enabled = isEnabled
                    )
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
                        enabled = targetEnabled(targets.chart)
                    )
                    SettingsSwitchItem(
                        title = stringResource(R.string.data_smoothing_target_current_title),
                        subtitle = stringResource(R.string.data_smoothing_target_current_desc),
                        checked = targets.current,
                        onCheckedChange = { viewModel.setDataSmoothingTargets(targets.copy(current = it)) },
                        icon = Icons.Default.WaterDrop,
                        iconTint = MaterialTheme.colorScheme.primary,
                        position = CardPosition.MIDDLE,
                        enabled = targetEnabled(targets.current)
                    )
                    SettingsSwitchItem(
                        title = stringResource(R.string.data_smoothing_target_sent_title),
                        subtitle = stringResource(R.string.data_smoothing_target_sent_desc),
                        checked = targets.sent,
                        onCheckedChange = { viewModel.setDataSmoothingTargets(targets.copy(sent = it)) },
                        icon = Icons.AutoMirrored.Filled.Send,
                        iconTint = MaterialTheme.colorScheme.tertiary,
                        position = CardPosition.BOTTOM,
                        enabled = targetEnabled(targets.sent)
                    )
                }

                SectionLabel(stringResource(R.string.advanced))
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    SettingsSwitchItem(
                        title = stringResource(R.string.data_smoothing_collapse_title),
                        subtitle = collapseSubtitle,
                        checked = collapseChunks,
                        onCheckedChange = { viewModel.setDataSmoothingCollapseChunks(it) },
                        icon = Icons.Default.FilterAlt,
                        iconTint = MaterialTheme.colorScheme.secondary,
                        position = CardPosition.TOP,
                        enabled = isEnabled
                    )
                    // The filter estimates the current and the sent reading; the chart keeps
                    // the averaging window, so with neither of those on it has nothing to do.
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
            }
        }
    }
}

/** Where smoothing applies, as the Settings row summarises it; null when it applies nowhere. */
internal fun dataSmoothingScopeLabel(targets: DataSmoothing.Targets): Int? = when {
    targets.chart && targets.current && targets.sent -> R.string.data_smoothing_scope_all
    targets.chart && targets.current -> R.string.data_smoothing_scope_chart_and_current
    targets.chart && targets.sent -> R.string.data_smoothing_scope_graph_and_sent
    targets.current && targets.sent -> R.string.data_smoothing_scope_current_and_sent
    targets.chart -> R.string.data_smoothing_graph_only_title
    targets.current -> R.string.data_smoothing_scope_current_only
    targets.sent -> R.string.data_smoothing_exchange_only_title
    else -> null
}
