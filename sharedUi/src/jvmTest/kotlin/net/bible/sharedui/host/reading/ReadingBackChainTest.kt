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
package net.bible.sharedui.host.reading

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import net.bible.sharedui.PassThroughBackHandler
import net.bible.sharedui.PlatformBackHandler
import net.bible.sharedui.desktop.reading.DesktopBackDispatcher
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The reading host's back chain: drawer, then the search sheet, then the search bar, else pass through. */
@OptIn(ExperimentalTestApi::class)
class ReadingBackChainTest {
    private class Fake(var drawer: Boolean = false, var sheet: Boolean = false, var bar: Boolean = false) {
        val log = mutableListOf<String>()
        val chain = ReadingBackChain(
            isDrawerOpen = { drawer },
            closeDrawer = { drawer = false; log += "drawer" },
            isSearchSheetVisible = { sheet },
            closeSearchSheet = { sheet = false; log += "sheet" },
            isSearchBarOpen = { bar },
            closeSearchBar = { bar = false; log += "bar" },
        )

        /** Runs one back press; returns whether it was passed through. */
        fun press(): Boolean {
            var passed = false
            chain.onBack { passed = true }
            return passed
        }
    }

    @Test fun escClosesDrawer() {
        val f = Fake(drawer = true)
        assertFalse(f.press())
        assertEquals(listOf("drawer"), f.log)
    }

    @Test fun escClosesIndexNeededSheet() {
        // The index prompt lives in the search sheet; the search bar stays up behind it.
        val f = Fake(sheet = true, bar = true)
        assertFalse(f.press())
        assertEquals(listOf("sheet"), f.log)
        assertTrue(f.bar)
    }

    @Test fun escClosesInReadingSearchBar() {
        val f = Fake(bar = true)
        assertFalse(f.press())
        assertEquals(listOf("bar"), f.log)
    }

    @Test fun nothingOpenPassesThrough() {
        val f = Fake()
        assertTrue(f.press())
        assertEquals(emptyList(), f.log)
    }

    @Test fun drawerThenSheetThenBar() {
        val f = Fake(drawer = true, sheet = true, bar = true)
        repeat(3) { f.press() }
        assertEquals(listOf("drawer", "sheet", "bar"), f.log)
        assertTrue(f.press())
    }

    @Test fun escClosesTopmostOverlayFirst() = runComposeUiTest {
        // A Compose sheet (its own back handler) over the open drawer: the first Esc closes the sheet, the second the drawer.
        val f = Fake(drawer = true)
        var sheetOpen by mutableStateOf(true)
        var drawerOpen by mutableStateOf(true)
        val chain = ReadingBackChain(
            isDrawerOpen = { drawerOpen },
            closeDrawer = { drawerOpen = false; f.log += "drawer" },
            isSearchSheetVisible = { false },
            closeSearchSheet = {},
            isSearchBarOpen = { false },
            closeSearchBar = {},
        )
        setContent {
            PassThroughBackHandler(enabled = true) { passThrough -> chain.onBack(passThrough) }
            if (sheetOpen) PlatformBackHandler(enabled = true) { sheetOpen = false; f.log += "sheet" }
        }
        waitForIdle()
        assertTrue(DesktopBackDispatcher.back())
        waitForIdle()
        assertEquals(listOf("sheet"), f.log)
        assertTrue(drawerOpen)
        assertTrue(DesktopBackDispatcher.back())
        waitForIdle()
        assertEquals(listOf("sheet", "drawer"), f.log)
        assertFalse(drawerOpen)
        // Nothing left: the press passes through and is reported unhandled.
        assertFalse(DesktopBackDispatcher.back())
    }
}
