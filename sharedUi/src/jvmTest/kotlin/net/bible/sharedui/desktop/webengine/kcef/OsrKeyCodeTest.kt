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
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OsrKeyCodeTest {
    @Test fun printableKeysMapToUsPositionalCodes() {
        assertEquals("Minus", OsrKeyCode.codeFor(189, 1))
        assertEquals("KeyF", OsrKeyCode.codeFor(70, 1))
        assertEquals("Equal", OsrKeyCode.codeFor(187, 1))
        assertEquals("Digit1", OsrKeyCode.codeFor(49, 1))
        assertEquals("Semicolon", OsrKeyCode.codeFor(186, 1))
        assertEquals("BracketLeft", OsrKeyCode.codeFor(219, 1))
        assertEquals("Comma", OsrKeyCode.codeFor(188, 1))
        assertEquals("Backquote", OsrKeyCode.codeFor(192, 1))
        assertEquals("KeyA", OsrKeyCode.codeFor(65, 0))
        assertEquals("KeyZ", OsrKeyCode.codeFor(90, 0))
        assertEquals("Digit0", OsrKeyCode.codeFor(48, 0))
    }

    @Test fun editingNavigationAndFunctionKeys() {
        assertEquals("Tab", OsrKeyCode.codeFor(9, 1))
        assertEquals("Escape", OsrKeyCode.codeFor(27, 1))
        assertEquals("ArrowLeft", OsrKeyCode.codeFor(37, 1))
        assertEquals("ArrowDown", OsrKeyCode.codeFor(40, 1))
        assertEquals("PageDown", OsrKeyCode.codeFor(34, 1))
        assertEquals("F1", OsrKeyCode.codeFor(112, 1))
        assertEquals("F12", OsrKeyCode.codeFor(123, 1))
        assertEquals("Space", OsrKeyCode.codeFor(32, 1))
    }

    @Test fun locationDecidesLeftRightEnterAndNumpad() {
        assertEquals("ControlLeft", OsrKeyCode.codeFor(17, 1))
        assertEquals("ControlRight", OsrKeyCode.codeFor(17, 2))
        assertEquals("ShiftRight", OsrKeyCode.codeFor(16, 2))
        assertEquals("AltLeft", OsrKeyCode.codeFor(18, 1))
        assertEquals("MetaLeft", OsrKeyCode.codeFor(91, 1))
        assertEquals("MetaRight", OsrKeyCode.codeFor(92, 2))
        assertEquals("Enter", OsrKeyCode.codeFor(13, 1))
        assertEquals("NumpadEnter", OsrKeyCode.codeFor(13, 3))
        assertEquals("Numpad5", OsrKeyCode.codeFor(101, 3))
        assertEquals("NumpadDecimal", OsrKeyCode.codeFor(110, 3))
        assertEquals("NumpadDivide", OsrKeyCode.codeFor(111, 3))
    }

    @Test fun unmappedKeysFallBack() {
        assertNull(OsrKeyCode.codeFor(0, 0))
        assertNull(OsrKeyCode.codeFor(229, 0))
    }

    @Test fun scriptIsGeneratedFromTheTableAndKeepsTheOriginalGetter() {
        val s = OsrKeyCode.SCRIPT
        assertTrue("Object.defineProperty(KeyboardEvent.prototype, 'code'" in s || "Object.defineProperty(proto, 'code'" in s)
        assertTrue("getOwnPropertyDescriptor" in s && "d.get.call(this)" in s, "falls back to the original getter")
        for (entry in listOf("189:'Minus'", "70:'KeyF'", "13:'Enter'", "17:['ControlLeft','ControlRight']", "111:'NumpadDivide'")) {
            assertTrue(entry in s, entry)
        }
    }

    @Test fun scriptOnlyWhenTheKeyCodeFixApplies() {
        val base = listOf("SHIM", "page")
        assertTrue(OsrKeyCode.SCRIPT in PaneFocusRequest.documentStartScripts("SHIM", listOf("page"), keyCodeFix = true))
        assertFalse(OsrKeyCode.SCRIPT in PaneFocusRequest.documentStartScripts("SHIM", listOf("page")))
        assertFalse(OsrKeyCode.SCRIPT in PaneFocusRequest.documentStartScripts("SHIM", listOf("page"), keyCodeFix = false))
        assertEquals(base, PaneFocusRequest.documentStartScripts("SHIM", listOf("page"), keyCodeFix = true).filter { it in base })
        val list = PaneFocusRequest.documentStartScripts("SHIM", listOf("page"), keyCodeFix = true)
        assertTrue(list.indexOf(OsrKeyCode.SCRIPT) < list.indexOf("page"), "before the page's own scripts")
    }
}
