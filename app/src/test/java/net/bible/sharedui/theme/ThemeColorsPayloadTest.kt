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
    fun `null when no seed`() {
        assertEquals("null", themeColorsJson(null, false, DisplayColorMode.NORMAL))
    }

    @Test
    fun `null for the not-set sentinel`() {
        assertEquals("null", themeColorsJson(DEFAULT_WORKSPACE_COLOR_ARGB, false, DisplayColorMode.NORMAL))
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
     * The COLOR_EINK drift this batch's final review caught: `AbTheme` greyscaled the derived scheme
     * for every non-NORMAL mode while this payload emitted un-greyscaled seeded roles, so the Vue
     * chrome showed hues on an e-ink screen (and `ModalDialog`'s `.monochrome.night` header, whose
     * override only covers `color`, had no `.monochrome` rule to save it). Fixed by folding the pass
     * into `abColorScheme`; this test is what stops it drifting apart again.
     */
    @Test
    fun `COLOR_EINK emits greyscaled roles, like the Compose scheme`() {
        val json = themeColorsJson(ORANGE, false, DisplayColorMode.COLOR_EINK)
        val hexes = Regex("#([0-9A-F]{2})([0-9A-F]{2})([0-9A-F]{2})").findAll(json).toList()
        assertEquals("expected all six roles in $json", 6, hexes.size)
        hexes.forEach { m ->
            val (r, g, b) = m.destructured
            assertTrue("$json contains a coloured role ${m.value}", r == g && g == b)
        }
    }

    /**
     * …and the other half of "one derivation": whatever `abColorScheme` produces is what ships.
     * A future second greyscale/tint pass on either side would break this.
     */
    @Test
    fun `the payload's roles are exactly the scheme's roles`() {
        listOf(DisplayColorMode.NORMAL, DisplayColorMode.COLOR_EINK).forEach { mode ->
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

    /**
     * COLOR_EINK still *seeds*: greyscaling must not flatten every workspace onto the same greys,
     * which is the failure mode that would make the greyscale fix indistinguishable from BW.
     */
    @Test
    fun `COLOR_EINK is still seeded after greyscaling`() {
        assertNotEquals(
            themeColorsJson(TEAL, false, DisplayColorMode.COLOR_EINK),
            themeColorsJson(ORANGE, false, DisplayColorMode.COLOR_EINK),
        )
    }
}
