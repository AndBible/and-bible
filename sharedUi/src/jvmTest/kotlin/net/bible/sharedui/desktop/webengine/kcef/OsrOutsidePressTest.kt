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
package net.bible.sharedui.desktop.webengine.kcef

import java.awt.Point
import java.awt.Rectangle
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OsrOutsidePressTest {
    private val win = Any()
    private val pane = Rectangle(100, 50, 400, 300)

    @Test fun primaryPressOutsideInTheSameWindowClears() {
        assertTrue(OsrOutsidePress.shouldClear(true, win, win, Point(10, 10), pane))
        assertTrue(OsrOutsidePress.shouldClear(true, win, win, Point(500, 50), pane)) // right edge is outside
    }

    @Test fun pressInsideKeepsFocus() {
        assertFalse(OsrOutsidePress.shouldClear(true, win, win, Point(150, 100), pane))
        assertFalse(OsrOutsidePress.shouldClear(true, win, win, Point(100, 50), pane))
    }

    @Test fun otherWindowOrNonPrimaryOrUnknownPaneChangesNothing() {
        assertFalse(OsrOutsidePress.shouldClear(true, Any(), win, Point(10, 10), pane))
        assertFalse(OsrOutsidePress.shouldClear(false, win, win, Point(10, 10), pane))
        assertFalse(OsrOutsidePress.shouldClear(true, win, null, Point(10, 10), pane))
        assertFalse(OsrOutsidePress.shouldClear(true, null, win, Point(10, 10), pane))
        assertFalse(OsrOutsidePress.shouldClear(true, win, win, Point(10, 10), null))
    }
}
