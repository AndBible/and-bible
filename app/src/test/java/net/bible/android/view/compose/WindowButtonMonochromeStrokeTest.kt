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
import kotlin.math.roundToInt

/**
 * MONO window button strokes at a fractional density (300 dpi, the Go103 e-ink panel: 1dp = 1.875px).
 * A stroke whose width or position is a fraction of a pixel antialiases into grey on e-ink, so every
 * MONO stroke must be whole pixels wide and lie fully inside the button.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [TEST_SDK], application = android.app.Application::class, qualifiers = "300dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WindowButtonMonochromeStrokeTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val dark = mutableStateOf(false)
    private val mode = mutableStateOf(WindowButtonMode.Rail)
    private val active = mutableStateOf(false)
    private val minimised = mutableStateOf(false)
    private var density = 1f

    private fun setContent() {
        compose.setContent {
            density = LocalDensity.current.density
            AbTheme(darkTheme = dark.value, colorMode = DisplayColorMode.MONOCHROME) {
                Box(Modifier.fillMaxSize()) {
                    WindowButton("", active.value, minimised.value, false, false, 0, mode.value, {}, {})
                }
            }
        }
    }

    private fun capture(block: (Bitmap) -> Unit) {
        compose.waitForIdle()
        val content = compose.activity.findViewById<android.view.ViewGroup>(android.R.id.content)
        val bitmap = Bitmap.createBitmap(content.width, content.height, Bitmap.Config.ARGB_8888)
        compose.runOnUiThread { content.draw(Canvas(bitmap)) }
        try { block(bitmap) } finally { bitmap.recycle() }
    }

    private fun px(dp: Float) = (dp * density).roundToInt()

    @Test fun solidBordersAreWholePixelsWithoutGreyFringe() {
        setContent()
        assertEquals("Fixture must run at the Go103 density", 1.875f, density, 0.001f)
        for (theme in listOf(false, true)) for (buttonMode in WindowButtonMode.entries) {
            for (selected in listOf(false, true)) {
                compose.runOnIdle {
                    dark.value = theme; mode.value = buttonMode; active.value = selected; minimised.value = false
                }
                capture { b ->
                    val ink = if (theme) 0xFFFFFF else 0x000000
                    val paper = if (theme) 0x000000 else 0xFFFFFF
                    val width = px(if (selected) 1.5f else 1f)
                    val y = px(20f)
                    val row = (0..width).map { b.getPixel(it, y) and 0xFFFFFF }
                    val expected = List(width) { ink } + paper
                    assertEquals("$buttonMode selected=$selected dark=$theme left edge " +
                        row.joinToString { "%06x".format(it) }, expected, row)
                }
            }
        }
    }

    @Test fun minimisedDashesAreWholePixelsInsideTheButton() {
        setContent()
        for (theme in listOf(false, true)) for (buttonMode in WindowButtonMode.entries) {
            for (selected in listOf(false, true)) {
                compose.runOnIdle {
                    dark.value = theme; mode.value = buttonMode; active.value = selected; minimised.value = true
                }
                capture { b ->
                    val ink = if (theme) 0xFFFFFF else 0x000000
                    val paper = if (theme) 0x000000 else 0xFFFFFF
                    val width = px(1f)
                    val context = "$buttonMode selected=$selected dark=$theme"
                    // Each edge: walk along it; wherever the outermost pixel is a full-ink dash, the
                    // dash's cross-section must be exactly `width` ink pixels, then paper.
                    fun checkEdge(name: String, along: IntRange, at: (along: Int, depth: Int) -> Int) {
                        var dashes = 0
                        for (i in along) {
                            if (at(i, 0) and 0xFFFFFF != ink) continue
                            dashes++
                            val cross = (0..width).map { at(i, it) and 0xFFFFFF }
                            assertEquals("$context $name edge at $i: " + cross.joinToString { "%06x".format(it) },
                                List(width) { ink } + paper, cross)
                        }
                        assertTrue("$context $name edge has full-ink dash pixels, got $dashes", dashes >= px(4f))
                    }
                    val mid = px(10f) until px(30f)
                    checkEdge("left", mid) { y, d -> b.getPixel(d, y) }
                    checkEdge("top", mid) { x, d -> b.getPixel(x, d) }
                }
            }
        }
    }
}
