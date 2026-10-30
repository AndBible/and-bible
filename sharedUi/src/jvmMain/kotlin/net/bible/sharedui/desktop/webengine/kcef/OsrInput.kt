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

import androidx.compose.ui.awt.awtEventOrNull
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.isAltPressed
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.isTertiaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import java.awt.Component
import java.awt.Cursor
import java.awt.event.InputEvent
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelEvent
import kotlin.math.abs
import kotlin.math.roundToInt

/** Where [OsrInput] sends its AWT events; the off-screen pane implements it over the CEF browser. */
internal interface OsrBrowserSink {
    fun sendMouse(e: MouseEvent)
    fun sendWheel(e: MouseWheelEvent)
}

/**
 * Turns Compose pointer input over an off-screen pane into the AWT mouse events CEF's `sendMouseEvent` expects: positions
 * in view DIP (Compose pixels / scale, from [geometry]), AWT button numbers and extended modifiers.
 *
 * Mouse buttons 4 to 7 (back and forward) are neither forwarded nor consumed: the desktop reading input listens to AWT
 * presses of those buttons toolkit-wide and navigates itself, and CEF's off-screen mouse path knows buttons 1 to 3 only.
 *
 * @param source the (invisible) component the events claim to come from
 */
internal class OsrInput(
    private val sink: OsrBrowserSink,
    private val geometry: () -> OsrGeometry,
    private val source: Component,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /**
     * Forwards one mouse event of type [id] ([MouseEvent.MOUSE_PRESSED] etc.) at [xPx], [yPx] (Compose pixels inside the
     * view). Returns true when it was forwarded, i.e. the caller should consume it.
     */
    fun mouse(id: Int, button: Int, modifiersEx: Int, xPx: Float, yPx: Float, clickCount: Int): Boolean {
        if (button !in MouseEvent.NOBUTTON..MouseEvent.BUTTON3) return false
        val p = geometry().toViewDip(xPx, yPx)
        sink.sendMouse(MouseEvent(source, id, clock(), modifiersEx, p.x, p.y, clickCount, button == MouseEvent.BUTTON3 && id == MouseEvent.MOUSE_PRESSED, button))
        return true
    }

    /** The pointer left the view (also sent when it moves onto a popup window). */
    fun exited(xPx: Float, yPx: Float, modifiersEx: Int) {
        mouse(MouseEvent.MOUSE_EXITED, MouseEvent.NOBUTTON, modifiersEx, xPx, yPx, 0)
    }

    /**
     * Forwards a wheel turn of [preciseRotation] notches (positive scrolls down). Horizontal scrolling is expected as
     * shift-wheel in [modifiersEx], as AWT delivers it. The event is always synthesised so that CEF receives
     * -(notches * [WHEEL_PIXELS_PER_NOTCH]) pixels on the dominant axis, see [WHEEL_PIXELS_PER_NOTCH].
     */
    fun wheel(xPx: Float, yPx: Float, modifiersEx: Int, preciseRotation: Double): Boolean {
        if (preciseRotation == 0.0) return false
        val g = geometry()
        val p = g.toViewDip(xPx, yPx)
        val screen = g.toScreen(p)
        // Rotation is the same +-1 as an integer and as a double, so whichever JCEF multiplies gives the same sign.
        val rotation = if (preciseRotation > 0) -1 else 1
        val amount = maxOf(1, (abs(preciseRotation) * WHEEL_PIXELS_PER_NOTCH).roundToInt())
        sink.sendWheel(
            MouseWheelEvent(
                source, MouseEvent.MOUSE_WHEEL, clock(), modifiersEx, p.x, p.y, screen.x, screen.y, 0, false,
                MouseWheelEvent.WHEEL_UNIT_SCROLL, amount, rotation, rotation.toDouble(),
            ),
        )
        return true
    }

    /** Handles one Compose pointer event; returns true when it was forwarded (and its changes should be consumed). */
    @OptIn(ExperimentalComposeUiApi::class)
    fun onPointerEvent(event: PointerEvent): Boolean {
        val change = event.changes.firstOrNull() ?: return false
        return handle(
            event.type, event.button, change.position.x, change.position.y, event.awtEventOrNull,
            synthesizedModifiers(event), change.scrollDelta.x, change.scrollDelta.y,
        )
    }

    /**
     * The conversion behind [onPointerEvent], on plain values. [awt] is the toolkit original when Compose has one: its button,
     * modifiers, click count and wheel data win, only the position is re-targeted. Without it, [composeButton] and
     * [synthModifiers] stand in, and a scroll comes from ([scrollX], [scrollY]): the dominant axis wins, horizontal goes
     * as shift-wheel.
     */
    @OptIn(ExperimentalComposeUiApi::class)
    fun handle(
        type: PointerEventType, composeButton: PointerButton?, x: Float, y: Float, awt: MouseEvent?,
        synthModifiers: Int, scrollX: Float, scrollY: Float,
    ): Boolean {
        val modifiers = awt?.modifiersEx ?: synthModifiers
        return when (type) {
            PointerEventType.Press -> mouse(MouseEvent.MOUSE_PRESSED, awt?.button ?: awtButton(composeButton), modifiers, x, y, awt?.clickCount ?: 1)
            PointerEventType.Release -> mouse(MouseEvent.MOUSE_RELEASED, awt?.button ?: awtButton(composeButton), modifiers, x, y, awt?.clickCount ?: 1)
            PointerEventType.Move, PointerEventType.Enter -> {
                val dragging = modifiers and BUTTON_DOWN_MASKS != 0
                mouse(if (dragging) MouseEvent.MOUSE_DRAGGED else MouseEvent.MOUSE_MOVED, MouseEvent.NOBUTTON, modifiers, x, y, 0)
            }
            PointerEventType.Exit -> { exited(x, y, modifiers); true }
            PointerEventType.Scroll -> {
                if (awt is MouseWheelEvent) {
                    wheel(x, y, modifiers, awt.preciseWheelRotation)
                } else {
                    val horizontal = abs(scrollX) > abs(scrollY)
                    wheel(x, y, if (horizontal) modifiers or InputEvent.SHIFT_DOWN_MASK else modifiers, (if (horizontal) scrollX else scrollY).toDouble())
                }
            }
            else -> false
        }
    }

    companion object {
        /**
         * Pixels CEF scrolls per wheel notch, the amount the windowed pane's page sees as DOM `deltaY` (120 per notch, which
         * the page's scripts and gestures expect). Measured live: JCEF's off-screen wheel path takes rotation times
         * scrollAmount as the CEF delta, with CEF's sign (positive scrolls up), so a plain AWT notch (rotation 1, amount 3)
         * arrived as deltaY = -3. The pane therefore sends rotation `-sign` and amount `|notches| * 120`.
         */
        const val WHEEL_PIXELS_PER_NOTCH = 120

        private const val BUTTON_DOWN_MASKS = InputEvent.BUTTON1_DOWN_MASK or InputEvent.BUTTON2_DOWN_MASK or InputEvent.BUTTON3_DOWN_MASK

        /** AWT button number of a Compose button; back and forward are 4 and 5, null is [MouseEvent.NOBUTTON]. */
        @OptIn(ExperimentalComposeUiApi::class)
        internal fun awtButton(button: PointerButton?): Int = when (button) {
            PointerButton.Primary -> MouseEvent.BUTTON1
            PointerButton.Tertiary -> MouseEvent.BUTTON2
            PointerButton.Secondary -> MouseEvent.BUTTON3
            PointerButton.Back -> 4
            PointerButton.Forward -> 5
            else -> MouseEvent.NOBUTTON
        }

        /** Extended AWT modifiers from Compose's own state, for events that carry no AWT original. */
        private fun synthesizedModifiers(event: PointerEvent): Int {
            var m = 0
            if (event.buttons.isPrimaryPressed) m = m or InputEvent.BUTTON1_DOWN_MASK
            if (event.buttons.isTertiaryPressed) m = m or InputEvent.BUTTON2_DOWN_MASK
            if (event.buttons.isSecondaryPressed) m = m or InputEvent.BUTTON3_DOWN_MASK
            if (event.keyboardModifiers.isShiftPressed) m = m or InputEvent.SHIFT_DOWN_MASK
            if (event.keyboardModifiers.isCtrlPressed) m = m or InputEvent.CTRL_DOWN_MASK
            if (event.keyboardModifiers.isAltPressed) m = m or InputEvent.ALT_DOWN_MASK
            if (event.keyboardModifiers.isMetaPressed) m = m or InputEvent.META_DOWN_MASK
            return m
        }

        /** The AWT cursor for a CEF cursor type (CEF reports the `java.awt.Cursor` constants); unknown types give the default arrow. */
        fun awtCursorFor(cefCursorType: Int): Cursor =
            if (cefCursorType in Cursor.DEFAULT_CURSOR..Cursor.MOVE_CURSOR) Cursor.getPredefinedCursor(cefCursorType) else Cursor.getDefaultCursor()

        /** The Compose pointer icon for a CEF cursor type, see [awtCursorFor]. */
        fun pointerIconFor(cefCursorType: Int): PointerIcon = when (cefCursorType) {
            Cursor.DEFAULT_CURSOR -> PointerIcon.Default
            Cursor.CROSSHAIR_CURSOR -> PointerIcon.Crosshair
            Cursor.TEXT_CURSOR -> PointerIcon.Text
            Cursor.HAND_CURSOR -> PointerIcon.Hand
            else -> if (cefCursorType in Cursor.DEFAULT_CURSOR..Cursor.MOVE_CURSOR) PointerIcon(awtCursorFor(cefCursorType)) else PointerIcon.Default
        }
    }
}

/** Forwards every pointer event over the node to [input] and consumes what it forwarded; a primary press also calls [onPress]. */
@OptIn(ExperimentalComposeUiApi::class)
internal fun Modifier.osrPointerInput(input: OsrInput, onPress: () -> Unit = {}): Modifier = pointerInput(input) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Main)
            if (event.type == PointerEventType.Press && event.button == PointerButton.Primary) onPress()
            if (input.onPointerEvent(event)) event.changes.forEach { it.consume() }
        }
    }
}
