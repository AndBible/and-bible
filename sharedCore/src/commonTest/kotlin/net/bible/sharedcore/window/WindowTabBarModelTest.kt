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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WindowTabBarModelTest {

    private fun win(
        id: String,
        pin: Boolean = false,
        links: Boolean = false,
        state: WindowStateValue = WindowStateValue.VISIBLE,
    ) = WindowSnapshot(
        id = id,
        state = state,
        weight = 1.0f,
        isVisible = state == WindowStateValue.VISIBLE,
        isPinMode = pin,
        isSynchronised = false,
        syncGroup = 0,
        isLinksWindow = links,
    )

    private fun layout(
        windows: List<WindowSnapshot>,
        active: String = windows.firstOrNull()?.id ?: "",
        maximized: String? = null,
        restoreVisible: Boolean = true,
    ) = WindowLayoutState(
        windows = windows,
        activeWindowId = active,
        maximizedWindowId = maximized,
        reverseSplitMode = false,
        restoreButtonsVisible = restoreVisible,
    )

    @Test
    fun maximisedShowsUnmaximiseOnly() {
        val m = buildWindowTabBar(layout(windows = listOf(win("a")), maximized = "a"))
        assertEquals(RailLeading.Unmaximise, m.leading)
        assertFalse(m.showButtons)
        assertTrue(m.entries.isEmpty())
    }

    @Test
    fun singleWindowShowsAddWindow() {
        val m = buildWindowTabBar(layout(windows = listOf(win("a"))))
        assertEquals(RailLeading.AddWindow, m.leading)
        assertFalse(m.showButtons)
    }

    @Test
    fun multiExpandedListsGroupedTabs() {
        val m = buildWindowTabBar(
            layout(
                windows = listOf(win("p", pin = true), win("n1"), win("n2"), win("lk", links = true)),
                active = "n1", restoreVisible = true,
            )
        )
        assertEquals(RailLeading.CollapseToggle(true), m.leading)
        assertTrue(m.showButtons)
        // pinned | sep | n1,n2 | sep | links  -> 3 groups => 2 separators, 4 tabs
        assertEquals(2, m.entries.count { it is RailEntry.GroupSeparator })
        assertEquals(4, m.entries.count { it is RailEntry.WindowTab })
        assertTrue((m.entries.filterIsInstance<RailEntry.WindowTab>().first { it.window.id == "n1" }).isActive)
    }

    @Test
    fun multiCollapsedHidesButtons() {
        val m = buildWindowTabBar(layout(windows = listOf(win("a"), win("b")), restoreVisible = false))
        assertEquals(RailLeading.CollapseToggle(false), m.leading)
        assertFalse(m.showButtons)
    }

    @Test
    fun minimisedWindowStillListedForRestore() {
        val m = buildWindowTabBar(
            layout(
                windows = listOf(win("a"), win("b", state = WindowStateValue.MINIMISED)),
                restoreVisible = true,
            )
        )
        assertEquals(2, m.entries.count { it is RailEntry.WindowTab })
    }

    @Test
    fun closedWindowsAreExcludedFromEntries() {
        val m = buildWindowTabBar(
            layout(
                windows = listOf(win("a"), win("b", state = WindowStateValue.CLOSED), win("c")),
                restoreVisible = true,
            )
        )
        assertEquals(2, m.entries.count { it is RailEntry.WindowTab })
        assertTrue(m.entries.filterIsInstance<RailEntry.WindowTab>().none { it.window.id == "b" })
    }

    @Test
    fun preservesDisplayOrderWithinEachGroup() {
        val m = buildWindowTabBar(
            layout(
                windows = listOf(win("n2"), win("n1"), win("p2", pin = true), win("p1", pin = true)),
                restoreVisible = true,
            )
        )
        val ids = m.entries.filterIsInstance<RailEntry.WindowTab>().map { it.window.id }
        // pinned group keeps p2,p1 order; non-pinned group keeps n2,n1 order
        assertEquals(listOf("p2", "p1", "n2", "n1"), ids)
    }

    @Test
    fun noSeparatorWhenOnlyOneGroupNonEmpty() {
        val m = buildWindowTabBar(
            layout(windows = listOf(win("a"), win("b")), restoreVisible = true)
        )
        assertEquals(0, m.entries.count { it is RailEntry.GroupSeparator })
        assertEquals(2, m.entries.count { it is RailEntry.WindowTab })
    }
}
