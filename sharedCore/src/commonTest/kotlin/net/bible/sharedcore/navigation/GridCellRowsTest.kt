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
package net.bible.sharedcore.navigation

import kotlin.test.Test
import kotlin.test.assertEquals

class GridCellRowsTest {
    @Test
    fun `cell rows never drop below the designer's minimum`() {
        assertEquals(10, gridCellRows(buttonCount = 1, columns = 5, minRows = 10))
        assertEquals(11, gridCellRows(buttonCount = 66, columns = 6, minRows = 11))
        // A long verse list still uses its own, larger row count and scrolls.
        assertEquals(36, gridCellRows(buttonCount = 176, columns = 5, minRows = 10))
    }

    @Test
    fun `sectioned cell rows apply the same floor across all sections`() {
        // Two tiny sections sum to 2 rows - well below the designer's floor of 10, which must win.
        assertEquals(10, gridCellRowsForSections(sectionSizes = listOf(2, 3), columns = 6, minRows = 10))
        // A bigger sectioned list (two 6-item sections, 6 columns = 1 row each) already exceeds a
        // floor of 1 on its own, so the floor is a no-op here - proving it doesn't over-apply.
        assertEquals(2, gridCellRowsForSections(sectionSizes = listOf(6, 6), columns = 6, minRows = 1))
    }
}
