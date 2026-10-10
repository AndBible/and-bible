package net.bible.android.view.compose

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.res.painterResource
import net.bible.android.activity.R
import net.bible.sharedui.reading.LocalPinIcon
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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

    @Test fun railButtonsUseClassicTabShapePaneKeepsRoundCorners() {
        data class Case(val mode: WindowButtonMode, val pinned: Boolean, val links: Boolean, val minimised: Boolean)
        val case = mutableStateOf(Case(WindowButtonMode.Rail, false, false, false))
        var density = 1f
        compose.setContent {
            density = LocalDensity.current.density
            AbTheme(darkTheme = false, colorMode = DisplayColorMode.MONOCHROME) {
                Box(Modifier.fillMaxSize().background(Color.White)) {
                    val c = case.value
                    CompositionLocalProvider(LocalPinIcon provides { painterResource(R.drawable.ic_pin) }) {
                        WindowButton("", false, c.minimised, c.pinned, c.links, 0, c.mode, {}, {})
                    }
                }
            }
        }
        fun capture(c: Case, block: (Bitmap) -> Unit) {
            compose.runOnIdle { case.value = c }
            compose.waitForIdle()
            val content = compose.activity.findViewById<android.view.ViewGroup>(android.R.id.content)
            val bitmap = Bitmap.createBitmap(content.width, content.height, Bitmap.Config.ARGB_8888)
            compose.runOnUiThread { content.draw(Canvas(bitmap)) }
            try { block(bitmap) } finally { bitmap.recycle() }
        }
        // Darkness of the 2x2dp corner patch: sum over pixels of (255 - luminance). A rounded corner
        // leaves that patch mostly paper/background; a square corner puts border ink in it.
        fun cornerInk(b: Bitmap, top: Boolean): Int {
            val d = density
            val y0 = if (top) 0 else (38 * d).toInt()
            var sum = 0
            for (x in 0 until (2 * d).toInt()) for (y in y0 until y0 + (2 * d).toInt())
                sum += 255 - (b.getPixel(x, y) and 0xFF)
            return sum
        }
        for (minimised in listOf(false, true)) {
            var plainTop = 0; var pinnedTop = 0; var linksTop = 0
            capture(Case(WindowButtonMode.Rail, false, false, minimised)) { b ->
                plainTop = cornerInk(b, top = true)
                assertTrue("Rail bottom-left corner must be square (minimised=$minimised)",
                    cornerInk(b, top = false) > 0)
            }
            capture(Case(WindowButtonMode.Rail, true, false, minimised)) { b ->
                pinnedTop = cornerInk(b, top = true)
                assertTrue("Pinned rail bottom-left corner must be square (minimised=$minimised)",
                    cornerInk(b, top = false) > 0)
            }
            capture(Case(WindowButtonMode.Rail, false, true, minimised)) { b -> linksTop = cornerInk(b, top = true) }
            assertTrue("Plain (1dp) top corner must be squarer than pinned (6dp): $plainTop vs $pinnedTop",
                plainTop > pinnedTop)
            assertEquals("Links window uses the pinned 6dp shape", pinnedTop, linksTop)
        }
        capture(Case(WindowButtonMode.Pane, true, false, false)) { b ->
            assertEquals("Pane keeps rounded 8dp bottom corners (no ink in the corner patch)", 0,
                cornerInk(b, top = false))
        }
    }

    @Test fun minimisedButtonUsesInkContentAndInkDashesWithoutGrey() {
        val dark = mutableStateOf(false)
        val mode = mutableStateOf(WindowButtonMode.Rail)
        val active = mutableStateOf(false)
        var density = 1f
        compose.setContent {
            density = LocalDensity.current.density
            AbTheme(darkTheme = dark.value, colorMode = DisplayColorMode.MONOCHROME) {
                Box(Modifier.fillMaxSize()) {
                    WindowButton("H", active.value, true, false, false, 0, mode.value, {}, {})
                }
            }
        }
        for (theme in listOf(false, true)) for (buttonMode in WindowButtonMode.entries) {
            for (selected in listOf(false, true)) {
                compose.runOnIdle { dark.value = theme; mode.value = buttonMode; active.value = selected }
                compose.waitForIdle()
                val content = compose.activity.findViewById<android.view.ViewGroup>(android.R.id.content)
                val bitmap = Bitmap.createBitmap(content.width, content.height, Bitmap.Config.ARGB_8888)
                compose.runOnUiThread { content.draw(Canvas(bitmap)) }
                try {
                    val ink = if (theme) 0xFFFFFF else 0x000000
                    val paper = if (theme) 0x000000 else 0xFFFFFF
                    var inkCount = 0
                    // Exclude the border and count solid label interiors, not antialiasing fringes.
                    for (x in (5 * density).toInt() until (35 * density).toInt())
                        for (y in (5 * density).toInt() until (35 * density).toInt())
                            if (bitmap.getPixel(x, y) and 0xFFFFFF == ink) inkCount++
                    val context = "$buttonMode selected=$selected dark=$theme"
                    assertTrue("Ink label $context, got $inkCount", inkCount > 5 * density * density)
                    // Sample within the left-edge stroke: ink dashes and paper gaps, never disabled grey.
                    val x = (0.5f * density).toInt()
                    var dashPx = 0; var gapPx = 0; var greyPx = 0
                    for (y in (10 * density).toInt() until (30 * density).toInt()) {
                        val c = bitmap.getPixel(x, y) and 0xFFFFFF
                        val lum = c and 0xFF
                        when {
                            // A centred 1.5dp stroke leaves 0.75px inside the clip at mdpi:
                            // ink becomes #3f3f3f (light) / #c0c0c0 (dark) through antialiasing.
                            (if (theme) lum >= 0xBF else lum <= 0x40) -> dashPx++
                            c == paper -> gapPx++
                            lum in 0x60..0xA0 -> greyPx++
                        }
                    }
                    assertTrue("Ink dash pixels on edge $context, got $dashPx", dashPx >= (4 * density).toInt())
                    assertTrue("Dash gap pixels on edge $context, got $gapPx", gapPx >= (2 * density).toInt())
                    assertEquals("No mid-grey on the dashed edge $context", 0, greyPx)
                } finally { bitmap.recycle() }
            }
        }
    }

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
