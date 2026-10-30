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

import java.awt.AWTEvent
import java.awt.Point
import java.awt.Rectangle
import java.awt.Window
import java.awt.event.AWTEventListener
import java.awt.event.MouseEvent
import javax.swing.SwingUtilities

/**
 * Takes the off-screen pane's focus away when the user presses outside it in the same window (toolbar, drawer, overlays
 * that do not take Compose focus themselves). An AWT toolkit listener sees the press before Compose does, on the EDT; it is
 * installed while the pane's content is composed ([install]) and removed with [uninstall].
 *
 * @param paneRect the pane's bounds on screen (AWT units), null while not laid out
 * @param paneWindow the root window containing the pane, null when unknown
 */
internal class OsrOutsidePress(
    private val focus: OsrFocus,
    private val paneRect: () -> Rectangle?,
    private val paneWindow: () -> Any?,
) : AWTEventListener {
    override fun eventDispatched(event: AWTEvent) {
        val e = event as? MouseEvent ?: return
        if (e.id != MouseEvent.MOUSE_PRESSED || !focus.isFocused) return
        val rect = paneRect()
        val inside = rect != null && rect.contains(e.locationOnScreen)
        if (shouldClear(e.button == MouseEvent.BUTTON1, rootWindow(e), paneWindow(), e.locationOnScreen, rect)) focus.onWindowPress(inside)
    }

    fun install() = java.awt.Toolkit.getDefaultToolkit().addAWTEventListener(this, AWTEvent.MOUSE_EVENT_MASK)

    fun uninstall() = java.awt.Toolkit.getDefaultToolkit().removeAWTEventListener(this)

    companion object {
        /**
         * Whether a press must clear the pane's focus: a primary press in the pane's own window ([pressWindow] equals
         * [paneWindow]) at [screen] outside [paneRect]. Presses of other windows, other buttons or an unknown layout never do.
         */
        fun shouldClear(primary: Boolean, pressWindow: Any?, paneWindow: Any?, screen: Point, paneRect: Rectangle?): Boolean =
            primary && pressWindow != null && pressWindow == paneWindow && paneRect != null && !paneRect.contains(screen)

        /** The root (ownerless) window of the event's component: owned dialogs and popups count as their owner's window. */
        private fun rootWindow(e: MouseEvent): Window? {
            var w: Window? = (e.component as? java.awt.Component)?.let { SwingUtilities.getWindowAncestor(it) ?: it as? Window }
            while (w?.owner != null) w = w.owner
            return w
        }

        /** The root window whose bounds contain [rect]'s centre among the showing windows, or null. */
        fun windowOf(rect: Rectangle?): Window? {
            if (rect == null) return null
            val cx = rect.centerX.toInt()
            val cy = rect.centerY.toInt()
            return Window.getWindows().firstOrNull { it.owner == null && it.isShowing && it.bounds.contains(cx, cy) }
        }
    }
}
