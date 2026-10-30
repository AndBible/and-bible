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

import java.awt.Canvas
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class X11PaneFocusTest {
    private val tree = mapOf(1L to listOf(2L, 9L), 2L to listOf(3L), 3L to emptyList())

    @Test fun picksTheDeepestChild() {
        assertEquals(3L, X11PaneFocus.deepest(1L) { tree[it].orEmpty() })
    }

    @Test fun noChildrenMeansTheWindowItself() {
        assertEquals(7L, X11PaneFocus.deepest(7L) { emptyList() })
    }

    @Test fun aCyclicTreeTerminates() {
        val end = X11PaneFocus.deepest(1L) { listOf(if (it == 1L) 2L else 1L) }
        assertTrue(end == 1L || end == 2L)
    }

    @Test fun focusesTheDeepestWindowOfTheComponentsWindow() {
        val set = mutableListOf<Long>()
        val f = X11PaneFocus({ 1L }, { tree[it].orEmpty() }, { set += it }, {})
        f.focus(FakeHeavy())
        assertEquals(listOf(3L), set)
    }

    @Test fun logsTheChosenWindowOncePerPane() {
        val lines = mutableListOf<String>()
        val f = X11PaneFocus({ 1L }, { tree[it].orEmpty() }, {}, { lines += it })
        f.focus(FakeHeavy())
        f.focus(FakeHeavy())
        assertEquals(listOf("x11 focus -> 3 (depth 2)"), lines)
    }

    @Test fun noWindowOrNoHeavyweightDoesNothing() {
        val set = mutableListOf<Long>()
        X11PaneFocus({ null }, { emptyList() }, { set += it }, {}).focus(FakeHeavy())
        X11PaneFocus({ 1L }, { emptyList() }, { set += it }, {}).focus(Canvas()) // not displayable
        assertTrue(set.isEmpty())
    }

    @Test fun aPerPressFailureLogsOnceAndKeepsTrying() {
        val lines = mutableListOf<String>()
        var calls = 0
        val f = X11PaneFocus({ 1L }, { emptyList() }, { calls++; error("boom") }, { lines += it })
        f.focus(FakeHeavy())
        f.focus(FakeHeavy())
        assertTrue(f.enabled)
        assertEquals(2, calls)
        assertEquals(1, lines.count { "failed" in it })
    }

    @Test fun queryAndFocusRunInOneLockSection() {
        val events = mutableListOf<String>()
        var depth = 0
        val f = X11PaneFocus(
            { 1L },
            { events += "query:lock=$depth"; tree[it].orEmpty() },
            { events += "focus:lock=$depth" },
            {},
            { block -> depth++; try { block() } finally { depth-- } },
        )
        f.focus(FakeHeavy())
        assertTrue(events.all { it.endsWith("lock=1") })
        assertEquals("focus:lock=1", events.last())
    }

    @Test fun createNeverThrowsAndLogsAtMostOneLine() {
        val lines = mutableListOf<String>()
        val f = X11PaneFocus.create { lines += it }
        f.focus(Canvas())
        assertTrue(lines.size <= 1)
    }

    private class FakeHeavy : Canvas() {
        override fun isDisplayable() = true
    }

    @Test fun x11OnlyCodeIsInertOnWindows() {
        // X11 pane focus: a disabled instance that logs once and never touches a component.
        val lines = mutableListOf<String>()
        val focus = X11PaneFocus.create("Windows 11") { lines += it }
        assertFalse(focus.enabled)
        focus.focus(FakeHeavy())
        assertEquals(1, lines.size, lines.toString())
        assertTrue("not Linux" in lines.single())

        // The key-code script is NOT X11-only: off-screen panes on Windows get an empty KeyboardEvent.code (measured
        // live in W1), so it is injected there too; never for windowed panes.
        assertTrue(KcefWebEngine.osrKeyCodeFix(KcefRenderingMode.Osr, "sun.awt.windows.WToolkit"))
        assertTrue(KcefWebEngine.osrKeyCodeFix(KcefRenderingMode.Osr, "sun.awt.X11.XToolkit"))
        assertFalse(KcefWebEngine.osrKeyCodeFix(KcefRenderingMode.Windowed, "sun.awt.X11.XToolkit"))
        assertFalse(KcefWebEngine.osrKeyCodeFix(KcefRenderingMode.Windowed, "sun.awt.windows.WToolkit"))
        assertFalse(KcefWebEngine.osrKeyCodeFix(KcefRenderingMode.Osr, "sun.lwawt.macosx.LWCToolkit"))
        val scripts = PaneFocusRequest.documentStartScripts("shim", listOf("page"), KcefWebEngine.osrKeyCodeFix(KcefRenderingMode.Osr, "sun.awt.windows.WToolkit"))
        assertTrue(OsrKeyCode.SCRIPT in scripts)

        // Mouse back/forward: AWT reports 4/5 on Windows, 6/7 only on X11 (4/5 there are horizontal scroll).
        val nav = net.bible.sharedui.desktop.reading.DesktopBackDispatcher
        val back = net.bible.sharedui.desktop.reading.DesktopBackDispatcher.Navigation.Back
        val forward = net.bible.sharedui.desktop.reading.DesktopBackDispatcher.Navigation.Forward
        assertEquals(back, nav.navigationForAwtMouseButton(4, x11 = false))
        assertEquals(forward, nav.navigationForAwtMouseButton(5, x11 = false))
        assertEquals(null, nav.navigationForAwtMouseButton(6, x11 = false))
        assertEquals(null, nav.navigationForAwtMouseButton(4, x11 = true))
    }
}
