package net.bible.sharedui.theme

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RippleConfiguration
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.border
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.junit4.createComposeRule
import net.bible.android.TEST_SDK
import net.bible.service.common.DisplayColorMode
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalMaterial3Api::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class AbThemeMonochromeTest {
    @get:Rule val rule = createComposeRule()

    @Test fun `monoBorder adds an ink edge only in MONOCHROME and COLOR_EINK`() {
        val base = Modifier
        val actual = mutableListOf<Triple<DisplayColorMode, Boolean, Modifier>>()
        rule.setContent {
            for (dark in listOf(false, true)) for (mode in DisplayColorMode.entries) {
                AbTheme(darkTheme = dark, colorMode = mode) {
                    assertEquals(mode.isInkBase, isPureMonochrome())
                    actual.add(Triple(mode, dark, base.monoBorder()))
                }
            }
        }
        rule.waitForIdle()
        assertEquals(8, actual.size)
        actual.forEach { (mode, dark, modifier) ->
            if (mode.isInkBase) {
                assertEquals(base.border(1.dp, if (dark) Color.White else Color.Black), modifier)
            } else assertSame(base, modifier)
        }
    }

    @Test fun `disabled content is opaque middle grey only in MONOCHROME and COLOR_EINK`() {
        val actual = mutableListOf<Pair<DisplayColorMode, Pair<Color, Color>>>()
        rule.setContent {
            for (dark in listOf(false, true)) for (mode in DisplayColorMode.entries) {
                AbTheme(darkTheme = dark, colorMode = mode) {
                    actual.add(mode to (LocalAbColors.current.monoDisabled to MaterialTheme.colorScheme.onSurface))
                }
            }
        }
        rule.waitForIdle()
        assertTrue(actual.isNotEmpty())
        actual.forEach { (mode, colors) ->
            assertEquals(if (mode.isInkBase) Color(0xFF808080) else colors.second.copy(alpha = 0.38f), colors.first)
        }
    }

    @Test fun `light MONOCHROME and COLOR_EINK accents are ink`() {
        val colors = mutableListOf<AbColors>()
        rule.setContent {
            for (mode in listOf(DisplayColorMode.MONOCHROME, DisplayColorMode.COLOR_EINK)) {
                AbTheme(darkTheme = false, colorMode = mode) { colors.add(LocalAbColors.current) }
            }
        }
        rule.waitForIdle()
        assertEquals(2, colors.size)
        colors.forEach {
            assertEquals(Color.Black, it.bookmark)
            assertEquals(Color.Black, it.activeWindow)
            assertEquals(Color.Black, it.helperLine)
        }
    }

    @Test fun `dark MONOCHROME accents are ink`() {
        var colors: AbColors? = null
        rule.setContent { AbTheme(darkTheme = true, colorMode = DisplayColorMode.MONOCHROME) { colors = LocalAbColors.current } }
        rule.waitForIdle()
        assertEquals(Color.White, colors!!.bookmark)
        assertEquals(Color.White, colors!!.activeWindow)
        assertEquals(Color.White, colors!!.helperLine)
    }

    @Test fun `MONOCHROME and COLOR_EINK disable even inherited ripple`() {
        val actual = mutableListOf<RippleConfiguration?>()
        rule.setContent {
            CompositionLocalProvider(LocalRippleConfiguration provides RippleConfiguration()) {
                for (mode in listOf(DisplayColorMode.MONOCHROME, DisplayColorMode.COLOR_EINK)) {
                    AbTheme(darkTheme = false, colorMode = mode) { actual.add(LocalRippleConfiguration.current) }
                }
            }
        }
        rule.waitForIdle()
        assertEquals(2, actual.size)
        actual.forEach { assertNull(it) }
    }

    @Test fun `old modes preserve inherited and default ripple`() {
        val inherited = RippleConfiguration(color = Color.Red)
        val results = mutableListOf<RippleConfiguration?>()
        rule.setContent {
            for (mode in listOf(DisplayColorMode.NORMAL, DisplayColorMode.BW)) {
                AbTheme(darkTheme = false, colorMode = mode) { assertNotNull(LocalRippleConfiguration.current) }
                CompositionLocalProvider(LocalRippleConfiguration provides inherited) {
                    AbTheme(darkTheme = false, colorMode = mode) { results.add(LocalRippleConfiguration.current) }
                }
                CompositionLocalProvider(LocalRippleConfiguration provides null) {
                    AbTheme(darkTheme = false, colorMode = mode) { assertNull(LocalRippleConfiguration.current) }
                }
            }
        }
        rule.waitForIdle()
        assertTrue(results.isNotEmpty())
        results.forEach { assertSame(inherited, it) }
    }
}
