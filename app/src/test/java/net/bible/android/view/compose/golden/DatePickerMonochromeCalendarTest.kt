package net.bible.android.view.compose.golden

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onNodeWithContentDescription
import net.bible.android.TEST_SDK
import net.bible.service.common.DisplayColorMode
import net.bible.sharedui.components.AbDatePickerDialog
import net.bible.sharedui.components.ymdToUtcMidnightMillis
import net.bible.sharedui.theme.AbTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/** Real wide dialog exercises the calendar instead of the 320dp input-only golden. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class, qualifiers = "w411dp-h891dp")
class DatePickerMonochromeCalendarTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun lightCalendarDisabledDays() = calendar(false, false)
    @Test fun darkCalendarDisabledDays() = calendar(true, false)
    @Test fun lightYearSelection() = calendar(false, true)
    @Test fun darkYearSelection() = calendar(true, true)

    @Test fun runtimeModeSwitchRestoresAndClearsDialogDim() {
        val mode = androidx.compose.runtime.mutableStateOf(DisplayColorMode.MONOCHROME)
        compose.setContent {
            AbTheme(darkTheme = false, colorMode = mode.value) {
                AbDatePickerDialog(
                    initialUtcMillis = ymdToUtcMidnightMillis(2026, 9, 25),
                    maxUtcMillis = ymdToUtcMidnightMillis(2026, 9, 26),
                    confirmText = "OK", dismissText = "Cancel", onConfirm = { _, _, _ -> }, onDismiss = {},
                )
            }
        }
        compose.waitForIdle()
        val window = ShadowDialog.getLatestDialog().window!!
        val dim = window.attributes.dimAmount
        val flag = android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND
        assertTrue(window.attributes.flags and flag == 0)
        compose.runOnIdle { mode.value = DisplayColorMode.NORMAL }
        compose.waitForIdle()
        assertTrue(ShadowDialog.getLatestDialog().window!!.attributes.flags and flag != 0)
        compose.runOnIdle { mode.value = DisplayColorMode.MONOCHROME }
        compose.waitForIdle()
        assertTrue(ShadowDialog.getLatestDialog().window!!.attributes.flags and flag == 0)
        assertTrue(ShadowDialog.getLatestDialog().window!!.attributes.dimAmount == dim)
    }

    private fun calendar(dark: Boolean, years: Boolean) {
        compose.setContent {
            AbTheme(darkTheme = dark, colorMode = DisplayColorMode.MONOCHROME, disableAnimations = true) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
                    AbDatePickerDialog(
                        initialUtcMillis = ymdToUtcMidnightMillis(2026, 9, 25),
                        maxUtcMillis = ymdToUtcMidnightMillis(2026, 9, 26),
                        confirmText = "OK", dismissText = "Cancel", onConfirm = { _, _, _ -> }, onDismiss = {},
                    )
                }
            }
        }
        compose.waitForIdle()
        if (years) {
            compose.onNodeWithContentDescription("Switch to selecting a year").performClick()
            compose.mainClock.advanceTimeBy(1000)
            compose.waitForIdle()
        }
        val view = ShadowDialog.getLatestDialog().window!!.decorView
        val bitmap = android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888)
        compose.runOnUiThread { view.draw(android.graphics.Canvas(bitmap)) }
        val image = BufferedImage(bitmap.width, bitmap.height, BufferedImage.TYPE_INT_RGB)
        for (x in 0 until bitmap.width) for (y in 0 until bitmap.height) image.setRGB(x, y, bitmap.getPixel(x, y))
        val path = File("build/mono-audit/DatePickerCalendar_${if (years) "years" else "days"}_${if (dark) "mono_dark" else "mono"}.png")
        path.parentFile.mkdirs()
        ImageIO.write(image, "png", path)
        val disabledNode = compose.onNodeWithText(if (years) "Navigate to year 2027" else "Sunday, September 27, 2026", useUnmergedTree = true).fetchSemanticsNode()
        assertTrue("future date/year must be disabled", disabledNode.config.contains(androidx.compose.ui.semantics.SemanticsProperties.Disabled))
        val target = disabledNode.boundsInRoot
        // Thin alpha-tinted numerals can pass the edge heuristic. Their opaque glyph cores
        // must actually use the disabled palette, not merely look achromatic.
        var disabledPixels = 0
        for (x in target.left.toInt() until target.right.toInt()) {
            for (y in target.top.toInt() until target.bottom.toInt()) {
                if (x in 0 until image.width && y in 0 until image.height && (image.getRGB(x, y) and 0xFFFFFF) == 0x808080) disabledPixels++
            }
        }
        assertTrue("future ${if (years) "year" else "day"} must have opaque 808080 glyph cores", disabledPixels > 0)
        val selected = compose.onNodeWithText(if (years) "Navigate to year 2026" else "Friday, September 25, 2026", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val ink = if (dark) 0xFFFFFF else 0x000000
        val paper = if (dark) 0x000000 else 0xFFFFFF
        var inkCount = 0
        var paperCount = 0
        for (x in selected.left.toInt() until selected.right.toInt()) for (y in selected.top.toInt() until selected.bottom.toInt()) {
            when (image.getRGB(x, y) and 0xFFFFFF) {
                ink -> inkCount++
                paper -> paperCount++
            }
        }
        assertTrue("selected date/year needs ink fill", inkCount > 300)
        assertTrue("selected date/year glyphs remain paper", paperCount > 20)
        val audit = MonochromePaletteAudit.audit(image, dark)
        assertTrue("actual calendar palette: $audit; see $path", audit.passed)
    }
}
