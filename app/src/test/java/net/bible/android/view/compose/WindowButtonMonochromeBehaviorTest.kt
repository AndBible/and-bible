package net.bible.android.view.compose

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import net.bible.android.TEST_SDK
import net.bible.service.common.DisplayColorMode
import net.bible.sharedui.reading.WindowButton
import net.bible.sharedui.reading.WindowButtonMode
import net.bible.sharedui.theme.AbTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WindowButtonMonochromeBehaviorTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun selectedAndUnselectedPaneAndRailUsePaperWithDistinctInkBorders() {
        val dark = mutableStateOf(false)
        val active = mutableStateOf(false)
        val mode = mutableStateOf(WindowButtonMode.Pane)
        val label = mutableStateOf("")
        var density = 1f
        compose.setContent {
            density = LocalDensity.current.density
            AbTheme(darkTheme = dark.value, colorMode = DisplayColorMode.MONOCHROME) {
                Box(Modifier.fillMaxSize()) {
                    WindowButton(label.value, active.value, false, false, false, 0, mode.value, {}, {})
                }
            }
        }
        for (theme in listOf(false, true)) for (buttonMode in WindowButtonMode.entries) {
            for (selected in listOf(false, true)) {
                compose.runOnIdle { dark.value = theme; active.value = selected; mode.value = buttonMode; label.value = "" }
                compose.waitForIdle()
                val content = compose.activity.findViewById<android.view.ViewGroup>(android.R.id.content)
                val bitmap = Bitmap.createBitmap(content.width, content.height, Bitmap.Config.ARGB_8888)
                compose.runOnUiThread { content.draw(Canvas(bitmap)) }
                try {
                    val ink = if (theme) 0xFFFFFF else 0x000000
                    val paper = if (theme) 0x000000 else 0xFFFFFF
                    val y = (20 * density).toInt()
                    assertEquals("Paper fill $buttonMode selected=$selected dark=$theme", paper,
                        bitmap.getPixel((20 * density).toInt(), y) and 0xFFFFFF)
                    assertEquals("Outer ink border", ink,
                        bitmap.getPixel((0.5f * density).toInt(), y) and 0xFFFFFF)
                    assertEquals("Active border must be 2dp, inactive 1dp", if (selected) ink else paper,
                        bitmap.getPixel((1.5f * density).toInt(), y) and 0xFFFFFF)
                } finally { bitmap.recycle() }
                compose.runOnIdle { label.value = "H" }
                compose.waitForIdle()
                val glyph = Bitmap.createBitmap(content.width, content.height, Bitmap.Config.ARGB_8888)
                compose.runOnUiThread { content.draw(Canvas(glyph)) }
                try {
                    val ink = if (theme) 0xFFFFFF else 0x000000
                    var count = 0
                    // Exclude the border and count solid interiors, not antialiasing fringes.
                    for (x in (5 * density).toInt() until (35 * density).toInt()) {
                        for (y in (5 * density).toInt() until (35 * density).toInt()) {
                            if (glyph.getPixel(x, y) and 0xFFFFFF == ink) count++
                        }
                    }
                    assertTrue("Ink label $buttonMode selected=$selected dark=$theme", count > 5 * density * density)
                } finally { glyph.recycle() }
            }
        }
    }
}
