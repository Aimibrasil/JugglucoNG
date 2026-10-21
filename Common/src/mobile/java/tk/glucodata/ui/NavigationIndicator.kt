package tk.glucodata.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.pow

/**
 * One active-indicator pill for the whole navigation bar or rail, which travels to the
 * chosen item instead of each item fading its own pill in place.
 *
 * It moves like a drop of liquid: the edge facing the destination leaps ahead on a stiff
 * spring while the trailing edge follows on a soft one, so the pill stretches across the
 * gap, thins as it stretches, and bulges a little as the trailing edge lands. The stock
 * per-item indicators are made transparent by the caller; their press ripples stay.
 *
 * Items report where their icons sit with [navigationIndicatorAnchor]; the bar or rail
 * paints its container and the pill with [navigationIndicatorHost].
 */
@Stable
class NavigationIndicatorState internal constructor(internal val axis: Orientation) {
    /** Each item's icon bounds, relative to the host, so the two can never disagree mid-move. */
    internal val anchors = mutableStateMapOf<String, Rect>()
    private var host: LayoutCoordinates? = null
    private val icons = HashMap<String, LayoutCoordinates>()

    /** False until the pill first shows, which it does in place rather than growing. */
    internal var shown = false

    internal val left = Animatable(0f)
    internal val top = Animatable(0f)
    internal val right = Animatable(0f)
    internal val bottom = Animatable(0f)
    internal val alpha = Animatable(0f)
    internal var restSize = Size.Zero

    internal fun placeHost(coordinates: LayoutCoordinates) {
        host = coordinates
        icons.keys.forEach(::measure)
    }

    internal fun placeIcon(route: String, coordinates: LayoutCoordinates) {
        icons[route] = coordinates
        measure(route)
    }

    private fun measure(route: String) {
        val host = host?.takeIf { it.isAttached } ?: return
        val icon = icons[route]?.takeIf { it.isAttached } ?: return
        val bounds = host.localBoundingBoxOf(icon, clipBounds = false)
        if (anchors[route] != bounds) anchors[route] = bounds
    }
}

@Composable
fun rememberNavigationIndicatorState(selectedRoute: String?, axis: Orientation): NavigationIndicatorState {
    val state = remember(axis) { NavigationIndicatorState(axis) }
    val density = LocalDensity.current
    state.restSize = with(density) { Size(56.dp.toPx(), 32.dp.toPx()) }
    val route by rememberUpdatedState(selectedRoute)

    // The pill's resting rectangle, in host coordinates, centred on the chosen icon.
    val target by remember(state) {
        derivedStateOf {
            val icon = route?.let { state.anchors[it] } ?: return@derivedStateOf null
            Rect(center = icon.center, radius = 0f).inflate(state.restSize)
        }
    }

    LaunchedEffect(state, target) {
        val goal = target
        if (goal == null) {
            state.alpha.animateTo(0f, spring(dampingRatio = 1f, stiffness = 1600f))
            return@LaunchedEffect
        }
        if (!state.shown) {
            // The app opening, or the bar arriving after a rotation, is not a switch.
            state.shown = true
            state.left.snapTo(goal.left)
            state.top.snapTo(goal.top)
            state.right.snapTo(goal.right)
            state.bottom.snapTo(goal.bottom)
            state.alpha.snapTo(1f)
            return@LaunchedEffect
        }
        val growing = state.alpha.value == 0f
        if (growing) {
            // Back from a screen that belongs to no tab: grow the pill out of the icon's
            // centre rather than sliding in from wherever it last was.
            state.left.snapTo(goal.center.x - state.restSize.width * 0.2f)
            state.right.snapTo(goal.center.x + state.restSize.width * 0.2f)
            state.top.snapTo(goal.top)
            state.bottom.snapTo(goal.bottom)
        }
        val horizontal = state.axis == Orientation.Horizontal
        val forward = if (horizontal) {
            goal.center.x >= (state.left.value + state.right.value) / 2f
        } else {
            goal.center.y >= (state.top.value + state.bottom.value) / 2f
        }
        val across = spring<Float>(dampingRatio = 0.8f, stiffness = 380f)
        val lead = if (growing) across else spring(dampingRatio = 0.62f, stiffness = 720f)
        val trail = if (growing) across else spring(dampingRatio = 0.68f, stiffness = 240f)
        coroutineScope {
            launch { state.alpha.animateTo(1f, spring(dampingRatio = 1f, stiffness = 1600f)) }
            if (horizontal) {
                launch { state.left.animateTo(goal.left, if (forward) trail else lead) }
                launch { state.right.animateTo(goal.right, if (forward) lead else trail) }
                launch { state.top.animateTo(goal.top, across) }
                launch { state.bottom.animateTo(goal.bottom, across) }
            } else {
                launch { state.top.animateTo(goal.top, if (forward) trail else lead) }
                launch { state.bottom.animateTo(goal.bottom, if (forward) lead else trail) }
                launch { state.left.animateTo(goal.left, across) }
                launch { state.right.animateTo(goal.right, across) }
            }
        }
    }
    return state
}

private fun Rect.inflate(size: Size) = Rect(
    left = left - size.width / 2f,
    top = top - size.height / 2f,
    right = right + size.width / 2f,
    bottom = bottom + size.height / 2f
)

/** Marks the icon of the item for [route] as a place the pill can travel to. */
fun Modifier.navigationIndicatorAnchor(state: NavigationIndicatorState, route: String): Modifier =
    onGloballyPositioned { state.placeIcon(route, it) }

/**
 * Paints the bar's or rail's [containerColor] and, over it, the travelling pill. Give the bar
 * itself a transparent container so this shows through behind its items.
 */
fun Modifier.navigationIndicatorHost(
    state: NavigationIndicatorState,
    containerColor: Color,
    indicatorColor: Color,
): Modifier = onGloballyPositioned { state.placeHost(it) }
    .drawBehind {
        drawRect(containerColor)
        val alpha = state.alpha.value
        if (alpha <= 0f) return@drawBehind
        val rest = state.restSize
        val width = (state.right.value - state.left.value).coerceAtLeast(0f)
        val height = (state.bottom.value - state.top.value).coerceAtLeast(0f)
        // Squash and stretch: the pill keeps roughly its area, thinning across the direction
        // of travel while it is drawn out and fattening for a moment as it lands.
        val stretch = if (state.axis == Orientation.Horizontal) width / rest.width else height / rest.height
        val thickness = if (stretch > 0f) (1f / stretch).pow(0.3f).coerceIn(0.72f, 1.1f) else 1f
        val drawn = if (state.axis == Orientation.Horizontal) {
            Size(width, height * thickness)
        } else {
            Size(width * thickness, height)
        }
        val center = Offset((state.left.value + state.right.value) / 2f, (state.top.value + state.bottom.value) / 2f)
        val radius = minOf(drawn.width, drawn.height) / 2f
        if (abs(drawn.width) < 0.5f || abs(drawn.height) < 0.5f) return@drawBehind
        drawRoundRect(
            color = indicatorColor,
            topLeft = center - Offset(drawn.width / 2f, drawn.height / 2f),
            size = drawn,
            cornerRadius = CornerRadius(radius),
            alpha = alpha
        )
    }
