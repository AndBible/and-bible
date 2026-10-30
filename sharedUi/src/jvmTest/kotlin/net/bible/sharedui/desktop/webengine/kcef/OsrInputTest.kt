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

import androidx.compose.ui.input.pointer.PointerIcon
import java.awt.Cursor
import java.awt.Point
import java.awt.event.InputEvent
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelEvent
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class OsrInputTest {
    private class FakeSink : OsrBrowserSink {
        val mouse = mutableListOf<MouseEvent>()
        val wheel = mutableListOf<MouseWheelEvent>()
        override fun sendMouse(e: MouseEvent) { mouse += e }
        override fun sendWheel(e: MouseWheelEvent) { wheel += e }
    }

    private val sink = FakeSink()
    private val source = JPanel()

    /** Scale 2: 200 x 100 DIP view, 400 x 200 px. */
    private val input = OsrInput(sink, { OsrGeometry(400, 200, 2.0, Point(10, 20)) }, source)

    @Test fun pressReleaseClickMappedWithButtonAndModifiers() {
        val down = InputEvent.BUTTON1_DOWN_MASK or InputEvent.SHIFT_DOWN_MASK
        assertTrue(input.mouse(MouseEvent.MOUSE_PRESSED, MouseEvent.BUTTON1, down, 200f, 100f, 2))
        assertTrue(input.mouse(MouseEvent.MOUSE_RELEASED, MouseEvent.BUTTON1, InputEvent.SHIFT_DOWN_MASK, 200f, 100f, 2))
        val (press, release) = sink.mouse
        assertEquals(MouseEvent.MOUSE_PRESSED, press.id)
        assertEquals(MouseEvent.BUTTON1, press.button)
        assertEquals(Point(100, 50), Point(press.x, press.y), "pixels / scale = view DIP")
        assertEquals(2, press.clickCount)
        assertEquals(down, press.modifiersEx)
        assertSame(source, press.source)
        assertEquals(MouseEvent.MOUSE_RELEASED, release.id)
        assertEquals(InputEvent.SHIFT_DOWN_MASK, release.modifiersEx)
    }

    @Test fun rightClickForwardedAsButton3() {
        assertTrue(input.mouse(MouseEvent.MOUSE_PRESSED, MouseEvent.BUTTON3, InputEvent.BUTTON3_DOWN_MASK, 20f, 20f, 1))
        assertEquals(MouseEvent.BUTTON3, sink.mouse.single().button)
    }

    @Test fun backForwardButtonsLeftToWindowListener() {
        for (button in 4..7) {
            assertFalse(input.mouse(MouseEvent.MOUSE_PRESSED, button, 0, 10f, 10f, 1), "button $button is not consumed")
            assertFalse(input.mouse(MouseEvent.MOUSE_RELEASED, button, 0, 10f, 10f, 1))
        }
        assertTrue(sink.mouse.isEmpty(), "D3a's AWT listener navigates; forwarding would navigate twice")
    }

    @Test fun moveAndDragAreForwardedWithoutButton() {
        assertTrue(input.mouse(MouseEvent.MOUSE_MOVED, MouseEvent.NOBUTTON, 0, 4f, 6f, 0))
        assertTrue(input.mouse(MouseEvent.MOUSE_DRAGGED, MouseEvent.NOBUTTON, InputEvent.BUTTON1_DOWN_MASK, 8f, 6f, 0))
        assertEquals(listOf(MouseEvent.MOUSE_MOVED, MouseEvent.MOUSE_DRAGGED), sink.mouse.map { it.id })
        assertEquals(Point(2, 3), Point(sink.mouse[0].x, sink.mouse[0].y))
    }

    @Test fun wheelDeltaKeepsPreciseRotation() {
        assertTrue(input.wheel(100f, 100f, 0, 0.25))
        assertTrue(input.wheel(100f, 100f, 0, -2.5))
        assertEquals(0.25, sink.wheel[0].preciseWheelRotation, 0.0)
        assertEquals(1, sink.wheel[0].wheelRotation, "a fractional step still scrolls in its direction")
        assertEquals(-2.5, sink.wheel[1].preciseWheelRotation, 0.0)
        assertEquals(Point(50, 50), Point(sink.wheel[0].x, sink.wheel[0].y))
        // Horizontal wheel arrives from the toolkit as shift-wheel and stays that way.
        assertTrue(input.wheel(0f, 0f, InputEvent.SHIFT_DOWN_MASK, 3.0))
        assertTrue(sink.wheel[2].modifiersEx and InputEvent.SHIFT_DOWN_MASK != 0)
    }

    @Test fun ctrlWheelForwardedWithModifier() {
        assertTrue(input.wheel(10f, 10f, InputEvent.CTRL_DOWN_MASK, -1.0))
        assertTrue(sink.wheel.single().modifiersEx and InputEvent.CTRL_DOWN_MASK != 0)
    }

    @Test fun exitSendsMouseExited() {
        input.exited(500f, 50f, 0)
        val e = sink.mouse.single()
        assertEquals(MouseEvent.MOUSE_EXITED, e.id)
        assertEquals(250, e.x)
    }

    @Test fun cursorChangeMapsToPointerIcon() {
        assertSame(PointerIcon.Default, OsrInput.pointerIconFor(Cursor.DEFAULT_CURSOR))
        assertSame(PointerIcon.Text, OsrInput.pointerIconFor(Cursor.TEXT_CURSOR))
        assertSame(PointerIcon.Hand, OsrInput.pointerIconFor(Cursor.HAND_CURSOR))
        assertSame(PointerIcon.Crosshair, OsrInput.pointerIconFor(Cursor.CROSSHAIR_CURSOR))
        assertNotNull(OsrInput.pointerIconFor(Cursor.E_RESIZE_CURSOR))
        assertSame(PointerIcon.Default, OsrInput.pointerIconFor(-5), "an unknown type falls back to the default arrow")
        assertSame(PointerIcon.Default, OsrInput.pointerIconFor(99))
        assertEquals(Cursor.E_RESIZE_CURSOR, OsrInput.awtCursorFor(Cursor.E_RESIZE_CURSOR).type)
    }
}
