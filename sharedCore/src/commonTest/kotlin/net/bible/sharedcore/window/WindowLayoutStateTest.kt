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

package net.bible.sharedcore.window

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class WindowLayoutStateTest {
    @Test
    fun empty_hasNoWindows_andRestoreButtonsVisible() {
        val e = WindowLayoutState.EMPTY
        assertEquals(emptyList(), e.windows)
        assertEquals("", e.activeWindowId)
        assertEquals(null, e.maximizedWindowId)
        assertEquals(false, e.reverseSplitMode)
        assertEquals(true, e.restoreButtonsVisible)
    }

    @Test
    fun dataClassEquality_holdsForIdenticalSnapshots_andDiffersOnChange() {
        val w = WindowSnapshot(
            id = "a", state = WindowStateValue.VISIBLE, weight = 1.0f, isVisible = true,
            isPinMode = false, isSynchronised = true, syncGroup = 0, isLinksWindow = false,
        )
        val s1 = WindowLayoutState(listOf(w), "a", null, reverseSplitMode = false, restoreButtonsVisible = true)
        val s2 = WindowLayoutState(listOf(w.copy()), "a", null, reverseSplitMode = false, restoreButtonsVisible = true)
        assertEquals(s1, s2)
        assertNotEquals(s1, s2.copy(activeWindowId = "b"))
        assertNotEquals(s1, s1.copy(windows = listOf(w.copy(state = WindowStateValue.MINIMISED))))
    }
}
