package tk.glucodata.ui

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
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
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Checkbox
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import tk.glucodata.ui.components.SettingsItem
import androidx.compose.material.icons.filled.Functions
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
    DataSmoothingSettingsContent(
        smoothingMinutes = smoothingMinutes,
        lastEnabledMinutes = remember(smoothingMinutes) { DataSmoothing.getLastEnabledMinutes(context) },
        targets = targets,
        causal = causal,
        onEnabledChange = viewModel::setDataSmoothingEnabled,
        onMinutesChange = viewModel::setChartSmoothingMinutes,
        onTargetsChange = viewModel::setDataSmoothingTargets,
        onCausalChange = viewModel::setDataSmoothingCausal,
        onNavigateBack = { navController.popBackStack() }
    )
}

/** The screen without its view model, so a test can draw it. */
@Composable
internal fun DataSmoothingSettingsContent(
    smoothingMinutes: Int,
    lastEnabledMinutes: Int,
    targets: DataSmoothing.Targets,
    causal: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onMinutesChange: (Int) -> Unit,
    onTargetsChange: (DataSmoothing.Targets) -> Unit,
    onCausalChange: (Boolean) -> Unit,
    onNavigateBack: () -> Unit,
) {
    val isEnabled = smoothingMinutes > 0
    val options = remember { DataSmoothing.enabledMinutesOptions().toList() }
    val configuredMinutes = if (isEnabled) smoothingMinutes else lastEnabledMinutes
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
    val fewerScope = dataSmoothingFewerScopeLabel(targets)

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            AppTopBar(
                title = stringResource(R.string.graph_smoothing_title),
                onNavigateBack = onNavigateBack,
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
                subtitle = when {
                    isEnabled && smoothedAnywhere -> stringResource(R.string.data_smoothing_on_desc, smoothingMinutes)
                    // Only thinned: say where, rather than "shown as measured" under an on switch.
                    isEnabled && fewerScope != null -> stringResource(
                        R.string.data_smoothing_fewer_summary_format,
                        stringResource(fewerScope)
                    )
                    else -> stringResource(R.string.settings_smoothing_off_desc)
                },
                checked = isEnabled,
                onCheckedChange = { onEnabledChange(it) },
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
                                    onMinutesChange(nextMinutes)
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

                // Where each mechanism applies: places as rows, Smooth and Fewer as two
                // checkbox columns, so "smooth everywhere, thin only what is sent" reads at
                // a glance. Either column may be empty; Fewer works without smoothing.
                Row(
                    modifier = Modifier.padding(top = 24.dp, bottom = 8.dp, end = 16.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text(
                        text = stringResource(R.string.data_smoothing_scope_title),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    ColumnHeader(stringResource(R.string.data_smoothing_column_smooth))
                    ColumnHeader(stringResource(R.string.data_smoothing_column_fewer))
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    DataSmoothingPlace.entries.forEach { place ->
                        SmoothingPlaceRow(
                            place = place,
                            smoothed = place.smoothed(targets),
                            fewer = place.fewer(targets),
                            onSmoothedChange = { onTargetsChange(place.toggleSmoothed(targets)) },
                            onFewerChange = { onTargetsChange(place.toggleFewer(targets)) },
                            enabled = isEnabled
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.data_smoothing_fewer_summary_format, collapseSubtitle) +
                        " · " + stringResource(R.string.data_smoothing_preserve_originals),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp)
                )

                Spacer(Modifier.height(24.dp))
                SettingsSwitchItem(
                    title = stringResource(R.string.data_smoothing_causal_title),
                    subtitle = stringResource(R.string.data_smoothing_causal_desc),
                    checked = causal,
                    onCheckedChange = { onCausalChange(it) },
                    icon = Icons.Default.Functions,
                    iconTint = MaterialTheme.colorScheme.primary,
                    position = CardPosition.SINGLE,
                    enabled = isEnabled
                )
            }
        }
    }
}

/** The three places readings go. */
private enum class DataSmoothingPlace(val title: Int, val description: Int, val icon: ImageVector) {
    CHART(R.string.data_smoothing_target_chart_title, R.string.data_smoothing_target_chart_desc, Icons.AutoMirrored.Filled.ShowChart),
    CURRENT(R.string.data_smoothing_target_current_title, R.string.data_smoothing_target_current_desc, Icons.Default.WaterDrop),
    SENT(R.string.data_smoothing_target_sent_title, R.string.data_smoothing_target_sent_desc, Icons.AutoMirrored.Filled.Send);

    val position: CardPosition
        get() = when (this) {
            CHART -> CardPosition.TOP
            CURRENT -> CardPosition.MIDDLE
            SENT -> CardPosition.BOTTOM
        }

    fun smoothed(t: DataSmoothing.Targets) = when (this) {
        CHART -> t.chart
        CURRENT -> t.current
        SENT -> t.sent
    }

    fun fewer(t: DataSmoothing.Targets) = when (this) {
        CHART -> t.fewerChart
        CURRENT -> t.fewerCurrent
        SENT -> t.fewerSent
    }

    fun toggleSmoothed(t: DataSmoothing.Targets) = when (this) {
        CHART -> t.copy(chart = !t.chart)
        CURRENT -> t.copy(current = !t.current)
        SENT -> t.copy(sent = !t.sent)
    }

    fun toggleFewer(t: DataSmoothing.Targets) = when (this) {
        CHART -> t.copy(fewerChart = !t.fewerChart)
        CURRENT -> t.copy(fewerCurrent = !t.fewerCurrent)
        SENT -> t.copy(fewerSent = !t.fewerSent)
    }
}

/** Checkbox columns are this wide; the headers above centre on them. */
private val PlaceColumnWidth = 56.dp

@Composable
private fun ColumnHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        maxLines = 1,
        softWrap = false,
        // A longer translation may run past its column rather than wrap.
        modifier = Modifier.width(PlaceColumnWidth).wrapContentWidth(unbounded = true)
    )
}

/** One place: icon, what it covers, then a checkbox under each column header. */
@Composable
private fun SmoothingPlaceRow(
    place: DataSmoothingPlace,
    smoothed: Boolean,
    fewer: Boolean,
    onSmoothedChange: () -> Unit,
    onFewerChange: () -> Unit,
    enabled: Boolean,
) {
    val title = stringResource(place.title)
    val smoothLabel = "$title, " + stringResource(R.string.data_smoothing_column_smooth)
    val fewerLabel = "$title, " + stringResource(R.string.data_smoothing_column_fewer)
    SettingsItem(
        title = title,
        subtitle = stringResource(place.description),
        icon = place.icon,
        iconTint = MaterialTheme.colorScheme.primary,
        position = place.position,
        trailingContent = {
            Row {
                PlaceCheckbox(smoothed, onSmoothedChange, enabled, smoothLabel)
                PlaceCheckbox(fewer, onFewerChange, enabled, fewerLabel)
            }
        }
    )
}

@Composable
private fun PlaceCheckbox(checked: Boolean, onToggle: () -> Unit, enabled: Boolean, label: String) {
    Box(modifier = Modifier.width(PlaceColumnWidth), contentAlignment = Alignment.Center) {
        Checkbox(
            checked = checked,
            onCheckedChange = { onToggle() },
            enabled = enabled,
            // Spoken with its row and column, since neither is next to it in the tree.
            modifier = Modifier.semantics { contentDescription = label }
        )
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
