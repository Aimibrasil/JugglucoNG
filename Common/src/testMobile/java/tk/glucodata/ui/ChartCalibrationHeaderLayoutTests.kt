package tk.glucodata.ui

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextLayoutResult
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
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "en-w360dp-h640dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ChartCalibrationHeaderLayoutTests {
    @get:Rule val compose = createComposeRule()
    private val anchorX = mutableFloatStateOf(126f)
    private val topInset = mutableIntStateOf(0)
    private val visible = mutableStateOf(true)
    private val dateLabel = mutableStateOf("Thursday, 29 October")
    private var clicks = 0

    private fun show(fontScale: Float = 1f, multiple: Boolean = false) {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, fontScale)) {
                MaterialTheme(typography = tk.glucodata.ui.theme.AppTypography) {
                    Column(Modifier.fillMaxSize().background(Color.Black)) {
                        Box(Modifier.fillMaxWidth().height(100.dp).testTag("banner"))
                        Spacer(Modifier.height(12.dp))
                        Box(Modifier.fillMaxWidth().height(160.dp).testTag("plot")) {
                            val baseline = chartDateTextBaseline(dateLabel.value, topInset.intValue.dp)
                            Canvas(Modifier.fillMaxSize()) {
                                drawLine(Color.Gray, Offset(anchorX.floatValue, 0f), Offset(anchorX.floatValue, size.height))
                            }
                            if (visible.value) {
                                ChartCalibrationValueChip(
                                    value = "3.0", anchorX = anchorX.floatValue, textBaseline = baseline,
                                    onClick = { clicks++ }, modifier = Modifier.testTag("calibration")
                                )
                                if (multiple) ChartCalibrationValueChip(
                                    value = "4.0", anchorX = 50f, textBaseline = baseline,
                                    onClick = { clicks++ }, modifier = Modifier.testTag("second")
                                )
                            }
                            // The production date container and text retain their original geometry.
                            androidx.compose.animation.AnimatedVisibility(
                                visible = true,
                                modifier = Modifier.align(Alignment.TopEnd)
                                    .padding(top = topInset.intValue.dp, end = 16.dp)
                            ) {
                                Box(
                                    modifier = Modifier.height(32.dp).testTag("date")
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                        .padding(horizontal = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    AnimatedContent(dateLabel.value, label = "date") { label ->
                                        Text(label, style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1, softWrap = false)
                                    }
                                }
                            }
                        }
                        Box(Modifier.fillMaxWidth().height(40.dp).testTag("picker"))
                    }
                }
            }
        }
    }

    private fun bounds(tag: String): Rect = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot

    private fun baseline(text: String): Float {
        val node = compose.onNodeWithText(text, useUnmergedTree = true)
        val layouts = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { assertTrue(it(layouts)) }
        return node.fetchSemanticsNode().positionInRoot.y + layouts.single().firstBaseline
    }

    private fun assertAligned(value: String = "3.0") {
        assertEquals("Only the text baseline is aligned", baseline(dateLabel.value), baseline(value), 0.5f)
    }

    @Test
    fun compactPillKeepsItsTimestampAnchorAndOriginalSize() {
        show()
        assertAligned()
        val pill = bounds("calibration")
        assertEquals(126f, pill.center.x, 0.5f)
        assertEquals(15f, pill.height, 0.5f)
        val textWidth = compose.onNodeWithText("3.0", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.width
        assertEquals(textWidth + 32f, pill.width, 0.5f)
        assertEquals(32f, bounds("date").height, 0.5f)
        compose.onNodeWithTag("calibration").performTouchInput { click(center) }
        assertEquals(1, clicks)
        val directory = File("build/reports/chart-header-layout")
        check(directory.isDirectory || directory.mkdirs())
        File(directory, "timestamp-aligned.png").outputStream().use {
            assertTrue(compose.onNodeWithTag("plot").captureToImage().asAndroidBitmap()
                .compress(Bitmap.CompressFormat.PNG, 100, it))
        }
    }

    @Test
    fun panningMovesEachValueWithItsOwnLineWithoutMovingTheDate() {
        show(multiple = true)
        val date = bounds("date")
        for (x in listOf(84f, 160f, 260f)) {
            compose.runOnIdle { anchorX.floatValue = x }
            assertEquals(x, bounds("calibration").center.x, 0.5f)
            assertEquals(50f, bounds("second").center.x, 0.5f)
            assertEquals(date, bounds("date"))
            assertAligned()
            assertAligned("4.0")
        }
    }

    @Test
    fun calibrationVisibilityDoesNotMoveTheDateOrPlotOrPicker() {
        show()
        val date = bounds("date")
        val plot = bounds("plot")
        val picker = bounds("picker")
        repeat(2) {
            compose.runOnIdle { visible.value = false }
            assertEquals(date, bounds("date"))
            assertEquals(plot, bounds("plot"))
            assertEquals(picker, bounds("picker"))
            compose.runOnIdle { visible.value = true }
            assertAligned()
        }
    }

    @Test
    fun originalDateInsetsKeepThePillInsideThePlotBelowABanner() {
        show()
        for (inset in listOf(0, 8, 16)) {
            compose.runOnIdle { topInset.intValue = inset }
            assertAligned()
            assertEquals(112f + inset, bounds("date").top, 0.5f)
            assertTrue(bounds("calibration").top >= bounds("plot").top)
            assertTrue(bounds("calibration").top > bounds("banner").bottom)
        }
    }

    @Test
    fun enlargedTextAndLocalizedDatesKeepTheirActualBaselinesAligned() {
        show(fontScale = 2f)
        for (date in listOf("Thursday, 29 October", "четверг, 29 октября")) {
            compose.runOnIdle { dateLabel.value = date }
            assertAligned()
            assertEquals(126f, bounds("calibration").center.x, 0.5f)
            assertEquals(32f, bounds("date").height, 0.5f)
        }
    }
}
