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

package net.bible.android.database

import net.bible.android.database.WorkspaceEntities.Colors
import org.junit.Assert.assertEquals
import org.junit.Test

class ColorsMergeTest {
    private fun blank() = Colors(null, null, null, null, null, null, null, null, null, null)

    @Test
    fun mergeFallsBackImageFieldsPerField() {
        val base = blank().copy(dayBackgroundImage = "BGIMG_a", dayBackgroundImageOpacity = 40)
        val override = blank().copy(nightBackgroundImage = "BGIMG_b")
        val merged = base.merge(override)
        assertEquals("BGIMG_a", merged.dayBackgroundImage)
        assertEquals("BGIMG_b", merged.nightBackgroundImage)
        assertEquals(40, merged.dayBackgroundImageOpacity)
    }

    @Test
    fun overrideImageWins() {
        val base = blank().copy(dayBackgroundImage = "BGIMG_a")
        val override = blank().copy(dayBackgroundImage = "BGIMG_b")
        assertEquals("BGIMG_b", base.merge(override).dayBackgroundImage)
    }

    @Test
    fun defaultOpacityIsHundredAndImageNull() {
        val colors = WorkspaceEntities.TextDisplaySettings.default.colors!!
        assertEquals(null, colors.dayBackgroundImage)
        assertEquals(null, colors.nightBackgroundImage)
        assertEquals(100, colors.dayBackgroundImageOpacity)
        assertEquals(100, colors.nightBackgroundImageOpacity)
    }
}
