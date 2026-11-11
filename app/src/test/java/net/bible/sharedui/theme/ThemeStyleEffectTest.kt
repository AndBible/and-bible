package net.bible.sharedui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.junit4.v2.createComposeRule
import net.bible.android.TEST_SDK
import net.bible.service.common.DisplayColorMode
import net.bible.service.common.THEME_PALETTE_STYLE_KEY
import net.bible.service.common.ThemePaletteStyle
import net.bible.sharedcore.platform.AppSettings
import net.bible.sharedcore.reading.DEFAULT_WORKSPACE_COLOR_ARGB
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import com.nhaarman.mockitokotlin2.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Issue #4057: observe the actual host's MaterialTheme, not only its palette calculator. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class ThemeStyleEffectTest {
    @get:Rule val compose = createComposeRule()

    private data class Case(val seed: Int?, val dark: Boolean, val mode: DisplayColorMode, val style: ThemePaletteStyle)

    // Only theme reads are substituted; preference resolution and the composed theme are real.
    private fun settings(mode: DisplayColorMode, style: () -> String) = object : AppSettings by mock<AppSettings>() {
        override fun getString(key: String, default: String?): String? = when (key) {
            "display_color_mode" -> mode.value
            THEME_PALETTE_STYLE_KEY -> style()
            else -> default
        }
        override fun getBoolean(key: String, default: Boolean): Boolean = default
    }

    @Test fun selectedStyleChangesChosenWorkspaceInBothThemesButNotOtherModesOrUnsetWorkspace() {
        val purple = 0xFF9C27B0.toInt()
        val cases = listOf(null, DEFAULT_WORKSPACE_COLOR_ARGB, purple).flatMap { seed ->
            listOf(false, true).flatMap { dark ->
                DisplayColorMode.entries.flatMap { mode ->
                    ThemePaletteStyle.entries.map { Case(seed, dark, mode, it) }
                }
            }
        }
        val observed = mutableMapOf<Case, ColorScheme>()
        compose.setContent {
            cases.forEach { case ->
                AbHostTheme(case.dark, settings(case.mode) { case.style.value }, false, { case.seed }) {
                    val scheme = MaterialTheme.colorScheme
                    SideEffect { observed[case] = scheme }
                }
            }
        }
        compose.waitForIdle()
        assertEquals(cases.size, observed.size)
        observed.entries.groupBy { Triple(it.key.seed, it.key.dark, it.key.mode) }.forEach { (group, entries) ->
            val (seed, dark, mode) = group
            val distinctSchemes = entries.map { it.value.toString() }.distinct().size
            if (seed == purple && mode == DisplayColorMode.NORMAL) {
                assertNotEquals("Styles must affect a chosen workspace, dark=$dark", 1, distinctSchemes)
                val default = entries.single { it.key.style == ThemePaletteStyle.DEFAULT }.value
                val monochrome = entries.single { it.key.style == ThemePaletteStyle.MONOCHROME }.value
                assertNotEquals("The reading toolbar's container must change, dark=$dark",
                    default.primaryContainer, monochrome.primaryContainer)
            } else {
                assertEquals("Style is intentionally ignored for $group", 1, distinctSchemes)
            }
        }
        observed.forEach { (case, scheme) ->
            fun hex(argb: Int) = "#%08X".format(argb)
            println("THEME_STYLE_EFFECT seed=${case.seed?.let(::hex)} dark=${case.dark} mode=${case.mode} " +
                "style=${case.style.value} primary=${hex(scheme.primary.toArgb())} " +
                "toolbar=${hex(scheme.primaryContainer.toArgb())} surface=${hex(scheme.surface.toArgb())}")
        }
    }

    @Test fun recomposedHostReadsChangedStoredStyleInDayAndNight() {
        val selection = mutableStateOf(ThemePaletteStyle.DEFAULT.value)
        val observed = mutableMapOf<Boolean, ColorScheme>()
        val settings = settings(DisplayColorMode.NORMAL) { selection.value }
        compose.setContent {
            listOf(false, true).forEach { dark ->
                AbHostTheme(dark, settings, false, { 0xFF9C27B0.toInt() }) {
                    val scheme = MaterialTheme.colorScheme
                    SideEffect { observed[dark] = scheme }
                }
            }
        }
        compose.waitForIdle()
        val before = observed.toMap()
        compose.runOnIdle { selection.value = ThemePaletteStyle.MONOCHROME.value }
        compose.waitForIdle()
        listOf(false, true).forEach { dark ->
            assertNotEquals("Stored style must reach the toolbar after recomposition, dark=$dark",
                before.getValue(dark).primaryContainer, observed.getValue(dark).primaryContainer)
        }
    }
}
