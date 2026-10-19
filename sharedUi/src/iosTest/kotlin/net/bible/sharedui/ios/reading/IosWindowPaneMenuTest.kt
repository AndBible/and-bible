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

package net.bible.sharedui.ios.reading

import net.bible.android.view.activity.page.WindowPaneMenuDispatcher
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The iOS omit list and implemented list partition the dispatcher's platform rows: nothing shown that does nothing. */
class IosWindowPaneMenuTest {
    @Test
    fun omittedAndImplementedRowsPartitionThePlatformRows() {
        assertTrue((IOS_OMITTED_PANE_MENU_ROWS intersect IOS_IMPLEMENTED_PANE_MENU_ROWS).isEmpty(), "a row is omitted or implemented, not both")
        assertEquals(WindowPaneMenuDispatcher.PLATFORM_ROWS, IOS_OMITTED_PANE_MENU_ROWS + IOS_IMPLEMENTED_PANE_MENU_ROWS)
    }
}
