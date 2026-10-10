package net.bible.android.view.compose

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import net.bible.android.TEST_SDK
import net.bible.service.common.DisplayColorMode
import net.bible.sharedui.reading.ReadingDrawerHeader
import net.bible.sharedui.theme.AbTheme
import net.bible.sharedui.theme.monoInk
import net.bible.sharedui.theme.monoPaper
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class ReadingDrawerMonochromeTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun logoIsInkInLightMono() = logo(false, DisplayColorMode.MONOCHROME, monoInk(false))
    @Test fun logoIsInkInDarkMono() = logo(true, DisplayColorMode.MONOCHROME, monoInk(true))
    @Test fun legacyLogoRetainsItsOriginalColor() = logo(false, DisplayColorMode.NORMAL, Color.Magenta)

    private fun logo(dark: Boolean, mode: DisplayColorMode, expected: Color) {
        var density = 1f
        compose.setContent {
            density = LocalDensity.current.density
            AbTheme(darkTheme = dark, colorMode = mode) {
                Box(Modifier.fillMaxSize().background(monoPaper(dark))) {
                    ReadingDrawerHeader("AndBible", ColorPainter(Color.Magenta))
                }
            }
        }
        compose.waitForIdle()
        val content = compose.activity.findViewById<android.view.ViewGroup>(android.R.id.content)
        val bitmap = android.graphics.Bitmap.createBitmap(content.width, content.height, android.graphics.Bitmap.Config.ARGB_8888)
        compose.runOnUiThread { content.draw(android.graphics.Canvas(bitmap)) }
        assertEquals(expected, Color(bitmap.getPixel((32 * density).toInt(), (74 * density).toInt())))
    }
}
