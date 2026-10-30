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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PaneFocusRequestTest {
    @Test fun theReservedRequestIsConsumedAndFocusesOnce() {
        var focus = 0
        val lines = mutableListOf<String>()
        assertTrue(PaneFocusRequest.handle(PaneFocusRequest.REQUEST, { focus++ }, { lines += it }))
        assertEquals(1, focus)
        assertEquals(listOf("press: focus to pane"), lines)
    }

    @Test fun onlyTheFirstPressOfAPaneIsLogged() {
        var focus = 0
        val lines = mutableListOf<String>()
        val logged = java.util.concurrent.atomic.AtomicBoolean(false)
        repeat(3) { assertTrue(PaneFocusRequest.handle(PaneFocusRequest.REQUEST, { focus++ }, { lines += it }, logged)) }
        assertEquals(3, focus)
        assertEquals(listOf("press: focus to pane"), lines)
    }

    @Test fun otherRequestsAreLeftToTheBridge() {
        var focus = 0
        for (t in listOf("{\"method\":\"x\"}", "kcef:focus", "")) {
            assertFalse(PaneFocusRequest.handle(t, { focus++ }, {}))
        }
        assertEquals(0, focus)
    }

    @Test fun theFocusScriptIsInstalledOnceAfterTheShim() {
        val list = PaneFocusRequest.documentStartScripts("SHIM", listOf("page1", "page2"))
        assertEquals(listOf("SHIM", PaneFocusRequest.SCRIPT, "page1", "page2"), list)
        assertEquals(1, list.count { it == PaneFocusRequest.SCRIPT })
        assertTrue("'mousedown'" in PaneFocusRequest.SCRIPT && "true);" in PaneFocusRequest.SCRIPT)
        assertFalse("preventDefault" in PaneFocusRequest.SCRIPT || "stopPropagation" in PaneFocusRequest.SCRIPT)
    }
}
