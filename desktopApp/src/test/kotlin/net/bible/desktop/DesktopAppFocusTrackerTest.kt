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
package net.bible.desktop

import kotlin.test.Test
import kotlin.test.assertEquals

class DesktopAppFocusTrackerTest {
    private val events = mutableListOf<String>()
    private val tracker = DesktopAppFocusTracker(
        isAppWindow = { it == "main" || it == "dialog" || it == "popup" },
        onActive = { events += "active" },
        onInactive = { events += "inactive" },
    )

    @Test fun openingAndClosingADialogSendsNothing() {
        tracker.activated("main")
        tracker.deactivated("main", "dialog")
        tracker.activated("dialog")
        tracker.deactivated("dialog", "main")
        tracker.activated("main")
        assertEquals(listOf("active"), events)
    }

    @Test fun leavingTheAppFromADialogResignsOnce() {
        tracker.activated("main")
        tracker.deactivated("main", "popup")
        tracker.activated("popup")
        tracker.deactivated("popup", null)
        tracker.activated("main")
        assertEquals(listOf("active", "inactive", "active"), events)
    }

    @Test fun anotherApplicationsWindowCountsAsLeaving() {
        tracker.activated("main")
        tracker.deactivated("main", "someone-else")
        tracker.activated("someone-else")
        assertEquals(listOf("active", "inactive"), events)
    }
}
