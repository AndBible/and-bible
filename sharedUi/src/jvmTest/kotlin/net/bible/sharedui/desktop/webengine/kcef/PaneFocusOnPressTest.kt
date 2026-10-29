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

import java.awt.event.MouseEvent
import kotlin.test.Test
import kotlin.test.assertEquals

class PaneFocusOnPressTest {
    private fun press(l: PaneFocusOnPress) = l.mousePressed(null as MouseEvent?)

    @Test fun aPressRequestsFocusOnceWhenNotFocused() {
        var requests = 0
        press(PaneFocusOnPress({ false }, { requests++ }))
        assertEquals(1, requests)
    }

    @Test fun aPressWhileFocusedRequestsNothing() {
        var requests = 0
        val l = PaneFocusOnPress({ true }, { requests++ })
        press(l); press(l)
        assertEquals(0, requests)
    }

    @Test fun focusFollowsTheCurrentOwnerState() {
        var focused = false
        var requests = 0
        val l = PaneFocusOnPress({ focused }, { requests++; focused = true })
        press(l); press(l)
        assertEquals(1, requests)
        focused = false
        press(l)
        assertEquals(2, requests)
    }
}
