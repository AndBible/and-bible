/*
 * Copyright (c) 2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
 *
 * This file is part of AndBible: Bible Study (http://github.com/AndBible/and-bible).
 *
 * AndBible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * AndBible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with AndBible.
 * If not, see http://www.gnu.org/licenses/.
 */
package net.bible.sharedui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.toArgb
import net.bible.service.common.DisplayColorMode
import net.bible.sharedcore.reading.DEFAULT_WORKSPACE_COLOR_ARGB
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val ORANGE = 0xFFFF8000.toInt()
private const val TEAL = 0xFF008080.toInt()

class ThemeColorsPayloadTest {
    @Test
    fun `monochrome ships null like BW, seed or no seed`() {
        assertEquals("null", themeColorsJson(0xFFFF8000.toInt(), dark = false, colorMode = DisplayColorMode.MONOCHROME))
        assertEquals("null", themeColorsJson(0xFFFF8000.toInt(), dark = true, colorMode = DisplayColorMode.MONOCHROME))
        assertEquals("null", themeColorsJson(null, dark = false, colorMode = DisplayColorMode.MONOCHROME))
        assertEquals("null", themeColorsJson(null, dark = true, colorMode = DisplayColorMode.MONOCHROME))
    }

    /** The inconsistency this spec fixes: default colour gave lilac Compose but Vue's own greys. */
    @Test
    fun `NORMAL always ships a payload, also for no seed and the default colour`() {
        listOf(null, DEFAULT_WORKSPACE_COLOR_ARGB).forEach { seed ->
            assertNotEquals("seed=$seed", "null", themeColorsJson(seed, false, DisplayColorMode.NORMAL))
        }
    }

    @Test
    fun `unset workspace payload uses stock Material roles in light and dark`() {
        for (dark in listOf(false, true)) {
            val stock = if (dark) darkColorScheme() else lightColorScheme()
            for (seed in listOf(null, DEFAULT_WORKSPACE_COLOR_ARGB)) {
                val json = themeColorsJson(seed, dark, DisplayColorMode.NORMAL)
                assertTrue(json, json.contains("\"primaryContainer\":\"#%06X\"".format(0xFFFFFF and stock.primaryContainer.toArgb())))
                assertTrue(json, json.contains("\"primary\":\"#%06X\"".format(0xFFFFFF and stock.primary.toArgb())))
            }
        }
    }

    @Test
    fun `null in COLOR_EINK and MONOCHROME, which share the ink-paper look (#4069)`() {
        for (mode in listOf(DisplayColorMode.COLOR_EINK, DisplayColorMode.MONOCHROME)) {
            for (seed in listOf(DEFAULT_WORKSPACE_COLOR_ARGB, null, ORANGE)) {
                assertEquals("$mode seed=$seed", "null", themeColorsJson(seed, false, mode))
            }
        }
    }

    @Test
    fun `null in BW, because the scheme there ignores the seed anyway`() {
        assertEquals("null", themeColorsJson(ORANGE, false, DisplayColorMode.BW))
    }

    @Test
    fun `emits the six roles as CSS hex`() {
        val json = themeColorsJson(ORANGE, false, DisplayColorMode.NORMAL)
        listOf(
            "primary", "onPrimary", "primaryContainer",
            "onPrimaryContainer", "secondaryContainer", "onSecondaryContainer",
        ).forEach { assertTrue("missing $it in $json", json.contains("\"$it\":\"#")) }
        assertTrue("hex must be 6 digits", Regex("#[0-9A-F]{6}\"").containsMatchIn(json))
    }

    /**
     * …and the other half of "one derivation": whatever `abColorScheme` produces is what ships.
     * A future second greyscale/tint pass on either side would break this.
     */
    @Test
    fun `the payload's roles are exactly the scheme's roles`() {
        listOf(DisplayColorMode.NORMAL).forEach { mode ->
            listOf(false, true).forEach { dark ->
                val scheme = abColorScheme(ORANGE, dark, mode)
                val json = themeColorsJson(ORANGE, dark, mode)
                listOf(
                    "primary" to scheme.primary,
                    "onPrimary" to scheme.onPrimary,
                    "primaryContainer" to scheme.primaryContainer,
                    "onPrimaryContainer" to scheme.onPrimaryContainer,
                    "secondaryContainer" to scheme.secondaryContainer,
                    "onSecondaryContainer" to scheme.onSecondaryContainer,
                ).forEach { (name, color) ->
                    val hex = "#%06X".format(0xFFFFFF and color.toArgb())
                    assertTrue("$mode/dark=$dark: $name should be $hex in $json", json.contains("\"$name\":\"$hex\""))
                }
            }
        }
    }
}
