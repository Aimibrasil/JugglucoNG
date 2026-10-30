package tk.glucodata.ui

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
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
    private val expansion = mutableFloatStateOf(1f)
    private var clicks = 0
    private val calibration = CalibrationEntity(
        id = 1, timestamp = 1_500L, sensorId = "test", sensorValue = 3f,
        sensorValueRaw = 3f, userValue = 3f, valueUnit = 1
    )

    private fun show(clearance: Int = 24, fontScale: Float = 1f) {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, fontScale)) {
                MaterialTheme(typography = tk.glucodata.ui.theme.AppTypography) {
                    val chipHeight = calibrationValueChipHeight()
                    val progress = expansion.floatValue
                    Column(Modifier.fillMaxSize().background(Color.Black)) {
                        Box(Modifier.fillMaxWidth().height(100.dp).testTag("preceding"))
                        Spacer(Modifier.height(clearance.dp))
                        Surface(
                            modifier = Modifier.fillMaxWidth().height(300.dp)
                                .extendChartTop(chipHeight * progress, clearance.dp)
                        ) {
                            ChartPlotLayout(
                                topGutter = chipHeight * progress,
                                pickerLift = 0.dp,
                                onPickerOverhangChanged = {},
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Box(Modifier.fillMaxSize().testTag("plot")) {
                                    val headerTop = chartHeaderTopInset(chipHeight, progress)
                                    Column(Modifier.align(Alignment.TopStart)) {
                                        Spacer(Modifier.height(headerTop))
                                        Text("Insulin", Modifier.testTag("insulin"))
                                    }
                                    Column(Modifier.align(Alignment.TopEnd)) {
                                        Spacer(Modifier.height(headerTop))
                                        Text("Thursday, 29 October", Modifier.testTag("date"))
                                    }
                                }
                                Box(Modifier.fillMaxWidth().height(40.dp).testTag("picker"))
                                ChartCalibrationValueRow(
                                    calibrations = listOf(calibration),
                                    viewportStart = { viewportStart.longValue },
                                    viewportDuration = { 1_000f },
                                    rightInset = 0.dp,
                                    topInset = 8.dp * (1f - progress),
                                    unit = "mmol/L",
                                    onCalibrationClick = { clicks++ }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun bounds(tag: String): Rect = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot

    @Test
    fun expandedHeadersKeepTheirOriginalAnchorWhenCalibrationsEnterAndLeave() {
        show()
        val date = bounds("date")
        val insulin = bounds("insulin")
        val picker = bounds("picker")
        // The pre-existing plot anchor is 100dp card + 24dp list spacing.
        assertEquals(124f, date.top, 0.5f)
        assertEquals(date.top, insulin.top, 0.5f)
        repeat(2) {
            compose.runOnIdle { viewportStart.longValue = 1_000L }
            assertEquals(date, bounds("date"))
            assertEquals(insulin, bounds("insulin"))
            assertEquals(picker, bounds("picker"))
            assertCalibrationClearAndClickable()
            compose.runOnIdle { viewportStart.longValue = 2_000L }
            assertEquals(date, bounds("date"))
            assertEquals(insulin, bounds("insulin"))
        }
        assertEquals(2, clicks)
    }

    private fun assertCalibrationClearAndClickable() {
        val chip = compose.onNodeWithText("3.0")
        val rect = chip.fetchSemanticsNode().boundsInRoot
        assertTrue("Calibration overlaps preceding card: $rect", rect.top >= bounds("preceding").bottom)
        assertTrue("Calibration overlaps date: $rect", rect.bottom <= bounds("date").top)
        // A real coordinate tap also checks the part of the strip above the old chart bounds.
        chip.performTouchInput { click(center) }
    }

    @Test
    fun bannerClearanceKeepsTheRaisedChipVisibleAndClickable() {
        viewportStart.longValue = 1_000L
        show(clearance = 12)
        assertCalibrationClearAndClickable()
        assertEquals(1, clicks)
    }

    @Test
    fun enlargedTextFitsAboveHeadersWithoutCoveringThePrecedingCard() {
        viewportStart.longValue = 1_000L
        show(clearance = 12, fontScale = 2f)
        assertCalibrationClearAndClickable()
        assertEquals(1, clicks)
    }

    @Test
    fun expansionKeepsThePlotAndPickerStableAndCalibrationAboveTheHeaders() {
        viewportStart.longValue = 1_000L
        show()
        val picker = bounds("picker")
        val plotHeight = bounds("plot").height
        for (progress in listOf(0f, 0.25f, 0.5f, 1f)) {
            compose.runOnIdle { expansion.floatValue = progress }
            assertCalibrationClearAndClickable()
            assertEquals(picker, bounds("picker"))
            assertEquals(plotHeight, bounds("plot").height, 0.5f)
        }
        val directory = File("/private/tmp/chart-header-render").apply { mkdirs() }
        File(directory, "expanded.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
