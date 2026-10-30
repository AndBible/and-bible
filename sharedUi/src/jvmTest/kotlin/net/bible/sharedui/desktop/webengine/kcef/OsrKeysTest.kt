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

import androidx.compose.ui.input.key.KeyEventType
import java.awt.Canvas
import java.awt.event.KeyEvent as Awt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OsrKeysTest {
    private class Sink : OsrKeyboardSink {
        val keys = mutableListOf<Awt>()
        override fun setFocus(focused: Boolean) {}
        override fun sendKey(e: Awt) { keys += e }
    }

    private val src = Canvas()
    private fun awt(id: Int, code: Int, ch: Char, mods: Int = 0) = Awt(src, id, 0L, mods, code, ch)

    /** Stands in for Compose's internal wrapper: a public `nativeEvent` getter holding the AWT original. */
    private class Wrapper(val nativeEvent: Awt)

    @Test fun originalIsTakenFromComposesWrapperOrPassedThrough() {
        val e = awt(Awt.KEY_PRESSED, Awt.VK_A, 'a')
        assertSame(e, OsrKeys.original(Wrapper(e)))
        assertSame(e, OsrKeys.original(e))
        assertNull(OsrKeys.original(Any()))
        assertNull(OsrKeys.original(null))
    }

    @Test fun pressedTypedReleasedForwardedInOrder() {
        val sink = Sink()
        val pressed = awt(Awt.KEY_PRESSED, Awt.VK_A, 'a')
        val typed = awt(Awt.KEY_TYPED, Awt.VK_UNDEFINED, 'a')
        val released = awt(Awt.KEY_RELEASED, Awt.VK_A, 'a')
        // Without a Compose wrapper the originals are what OsrKeys sends, unchanged and in order, KEY_TYPED included.
        listOf(pressed, typed, released).forEach { sink.sendKey(OsrKeys.original(Wrapper(it))!!) }
        assertEquals(listOf(pressed, typed, released), sink.keys)
    }

    @Test fun synthesizedEventsCoverPressTypeRelease() {
        val p = OsrKeys.synthesize(KeyEventType.KeyDown, Awt.VK_B, 'b'.code, Awt.CTRL_DOWN_MASK)!!
        assertEquals(Awt.KEY_PRESSED, p.id); assertEquals(Awt.VK_B, p.keyCode); assertEquals('b', p.keyChar)
        assertTrue(p.isControlDown)
        val t = OsrKeys.synthesize(KeyEventType.Unknown, 0, 'é'.code, 0)!!
        assertEquals(Awt.KEY_TYPED, t.id); assertEquals('é', t.keyChar)
        assertEquals(Awt.KEY_RELEASED, OsrKeys.synthesize(KeyEventType.KeyUp, Awt.VK_B, 'b'.code, 0)!!.id)
        assertNull(OsrKeys.synthesize(KeyEventType.Unknown, 0, 0, 0))
    }

    @Test fun tabIsSynthesizedWithItsKeyCode() {
        val tab = OsrKeys.synthesize(KeyEventType.KeyDown, Awt.VK_TAB, '\t'.code, 0)!!
        assertEquals(Awt.VK_TAB, tab.keyCode)
        assertEquals(Awt.KEY_PRESSED, tab.id)
    }
}
