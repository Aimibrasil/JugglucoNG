package tk.glucodata.ui

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import tk.glucodata.data.calibration.CalibrationEntity
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "en-w360dp-h640dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ChartCalibrationHeaderLayoutTests {
    @get:Rule val compose = createComposeRule()
    private val viewportStart = mutableLongStateOf(2_000L)
    private val topInset = mutableIntStateOf(0)
    private val showDate = mutableStateOf(true)
    private val showInsulin = mutableStateOf(true)
    private val clicks = mutableListOf<Int>()
    private val calibration = CalibrationEntity(
        id = 1, timestamp = 1_500L, sensorId = "test", sensorValue = 3f,
        sensorValueRaw = 3f, userValue = 3f, valueUnit = 1
    )
    private val dateLabel = "Thursday, 29 October"

    private fun show(clearance: Int = 24, fontScale: Float = 1f, multiple: Boolean = false) {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, fontScale)) {
                MaterialTheme(typography = tk.glucodata.ui.theme.AppTypography) {
                    val all = if (multiple) listOf(calibration, calibration.copy(id = 2, userValue = 4f),
                        calibration.copy(id = 3, userValue = 5f)) else listOf(calibration)
                    val visible = all.filter { it.timestamp in viewportStart.longValue..(viewportStart.longValue + 1_000L) }
                    Column(Modifier.fillMaxSize().background(Color.Black)) {
                        Box(Modifier.fillMaxWidth().height(100.dp).testTag("preceding"))
                        Spacer(Modifier.height(clearance.dp))
                        BoxWithConstraints(Modifier.fillMaxWidth().height(260.dp).testTag("plot")) {
                            val startMaxWidth = maxWidth * 0.44f
                            ChartHeaderRow(
                                modifier = Modifier.fillMaxWidth().testTag("header-row").padding(
                                    start = 16.dp, end = 16.dp, top = topInset.intValue.dp
                                ),
                                hasCalibrations = visible.isNotEmpty(),
                                hasDate = showDate.value,
                                start = {
                                    if (showInsulin.value) Surface(
                                        modifier = Modifier.widthIn(max = startMaxWidth).testTag("insulin"),
                                        shape = RoundedCornerShape(16.dp),
                                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    ) {
                                        Text("Insulin", modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                            style = MaterialTheme.typography.labelMediumEmphasized, maxLines = 1)
                                    }
                                },
                                calibrations = {
                                    ChartCalibrationValueChips(
                                        calibrations = visible,
                                        unit = "mmol/L",
                                        onCalibrationClick = { clicks.add(it.id) },
                                        modifier = Modifier.testTag("calibrations")
                                    )
                                },
                                end = {
                                    androidx.compose.animation.AnimatedVisibility(visible = showDate.value) {
                                        ChartHeaderChip(
                                            modifier = Modifier.testTag("date"),
                                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                        ) {
                                            AnimatedContent(targetState = dateLabel, label = "testDateHeader") { label ->
                                                Text(label, style = MaterialTheme.typography.labelLarge,
                                                    maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                                            }
                                        }
                                    }
                                }
                            )
                        }
                        Box(Modifier.fillMaxWidth().height(40.dp).testTag("picker"))
                    }
                }
            }
        }
    }

    private fun bounds(tag: String): Rect = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot

    private fun assertInline(value: String = "3.0") {
        val chip = compose.onNodeWithText(value).fetchSemanticsNode().boundsInRoot
        val date = bounds("date")
        assertEquals("Chip and date top", date.top, chip.top, 0.5f)
        assertEquals("Chip and date height", date.height, chip.height, 0.5f)
        assertTrue("Chip needs 8dp before date: $chip / $date", date.left - chip.right >= 8f)
        if (showInsulin.value) assertTrue("Chip covers insulin", chip.left - bounds("insulin").right >= 8f)
        assertEquals("Matching text baseline", baseline(dateLabel), baseline(value), 0.5f)
        compose.onNodeWithText(value).performTouchInput { click(center) }
    }

    private fun baseline(text: String): Float {
        val node = compose.onNodeWithText(text, useUnmergedTree = true)
        val layouts = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { assertTrue(it(layouts)) }
        return node.fetchSemanticsNode().positionInRoot.y + layouts.single().firstBaseline
    }

    @Test
    fun headersKeepTheirOriginalAnchorsWhenCalibrationsEnterAndLeave() {
        show()
        val date = bounds("date")
        val insulin = bounds("insulin")
        val plot = bounds("plot")
        val picker = bounds("picker")
        assertEquals(124f, date.top, 0.5f)
        repeat(2) {
            compose.runOnIdle { viewportStart.longValue = 1_000L }
            assertInline()
            assertEquals(date, bounds("date"))
            assertEquals(insulin, bounds("insulin"))
            assertEquals(plot, bounds("plot"))
            assertEquals(picker, bounds("picker"))
            val directory = File("build/reports/chart-header-layout")
            check(directory.isDirectory || directory.mkdirs())
            File(directory, "inline.png").outputStream().use {
                assertTrue(compose.onNodeWithTag("header-row").captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it))
            }
            compose.runOnIdle { viewportStart.longValue = 2_000L }
            assertEquals(date, bounds("date"))
            assertEquals(insulin, bounds("insulin"))
        }
        assertEquals(listOf(1, 1), clicks)
    }

    @Test
    fun bannerAndCollapsedInsetsKeepValuesInTheDateRowAndInsideThePlot() {
        viewportStart.longValue = 1_000L
        show(clearance = 12)
        val picker = bounds("picker")
        for (inset in listOf(0, 8, 16)) {
            compose.runOnIdle { topInset.intValue = inset }
            assertInline()
            assertTrue(bounds("date").top >= bounds("plot").top)
            assertTrue(bounds("date").top > bounds("preceding").bottom)
            assertEquals(picker, bounds("picker"))
        }
    }

    @Test
    @Config(qualifiers = "en-w320dp-h640dp-mdpi")
    fun enlargedTextKeepsCalibrationAndDateAlignedWithoutOverlappingInsulin() {
        viewportStart.longValue = 1_000L
        show(fontScale = 2f)
        assertInline()
        assertTrue(bounds("date").height > 32f)
        assertEquals(listOf(1), clicks)
    }

    @Test
    fun multipleCalibrationsScrollWithinTheirSlotAndRemainClickable() {
        viewportStart.longValue = 1_000L
        show(multiple = true)
        val slot = bounds("calibrations")
        assertTrue(bounds("date").left - slot.right >= 8f)
        assertTrue(slot.left - bounds("insulin").right >= 8f)
        compose.onNodeWithText("5.0").performScrollTo()
        assertInline("5.0")
        assertEquals(listOf(3), clicks)
    }

    @Test
    fun calibrationStaysInBoundsWithoutInsulinOrDate() {
        viewportStart.longValue = 1_000L
        showInsulin.value = false
        showDate.value = false
        show()
        val chip = compose.onNodeWithText("3.0").fetchSemanticsNode().boundsInRoot
        assertEquals(124f, chip.top, 0.5f)
        assertEquals(344f, chip.right, 0.5f)
        compose.onNodeWithText("3.0").performTouchInput { click(center) }
        assertEquals(listOf(1), clicks)
    }
}
