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

package net.bible.service.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DisplayColorModeTest {
    @Test
    fun parsesKnownValues() {
        assertEquals(DisplayColorMode.NORMAL, DisplayColorMode.fromValue("normal"))
        assertEquals(DisplayColorMode.BW, DisplayColorMode.fromValue("bw"))
        assertEquals(DisplayColorMode.COLOR_EINK, DisplayColorMode.fromValue("color_eink"))
    }

    @Test
    fun returnsNullForUnknownOrNull() {
        assertNull(DisplayColorMode.fromValue(null))
        assertNull(DisplayColorMode.fromValue("garbage"))
        assertNull(DisplayColorMode.fromValue(""))
    }
}
