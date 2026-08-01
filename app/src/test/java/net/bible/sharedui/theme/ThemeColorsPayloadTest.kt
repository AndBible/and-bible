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

import net.bible.service.common.DisplayColorMode
import net.bible.sharedcore.reading.DEFAULT_WORKSPACE_COLOR_ARGB
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val ORANGE = 0xFFFF8000.toInt()

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
}
