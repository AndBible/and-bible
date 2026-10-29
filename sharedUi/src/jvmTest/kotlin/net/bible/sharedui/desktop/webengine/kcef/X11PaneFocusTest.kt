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

    @Test fun noWindowOrNoHeavyweightDoesNothing() {
        val set = mutableListOf<Long>()
        X11PaneFocus({ null }, { emptyList() }, { set += it }, {}).focus(FakeHeavy())
        X11PaneFocus({ 1L }, { emptyList() }, { set += it }, {}).focus(Canvas()) // not displayable
        assertTrue(set.isEmpty())
    }

    @Test fun aFailureLogsOnceAndDisables() {
        val lines = mutableListOf<String>()
        var calls = 0
        val f = X11PaneFocus({ 1L }, { emptyList() }, { calls++; error("boom") }, { lines += it })
        f.focus(FakeHeavy())
        f.focus(FakeHeavy())
        assertFalse(f.enabled)
        assertEquals(1, calls)
        assertEquals(1, lines.size)
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
}
