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
package net.bible.desktop

import java.awt.AWTEvent
import java.awt.Toolkit
import java.awt.Window
import java.awt.event.AWTEventListener
import java.awt.event.WindowEvent

/**
 * Whether the app as a whole has the focus. Under `compose.layers.type=WINDOW` every Compose dialog and popup is its
 * own window owned by the main window, so the main window's own activation flips each time one opens; only a change
 * between "some app window is active" and "none is" reaches [onActive] / [onInactive]. Windows are typed `Any` so the
 * logic runs without AWT; [isAppWindow] tells the app's windows (the main window and those it owns) from the rest
 * (null: focus left the JVM).
 */
class DesktopAppFocusTracker(
    private val isAppWindow: (Any?) -> Boolean,
    private val onActive: () -> Unit,
    private val onInactive: () -> Unit,
) {
    private var active = false

    fun activated(window: Any?) {
        if (!isAppWindow(window) || active) return
        active = true
        onActive()
    }

    /** [window] lost the activation to [opposite] (null when the focus went to another application). */
    fun deactivated(window: Any?, opposite: Any?) {
        if (!isAppWindow(window) || !active || isAppWindow(opposite)) return
        active = false
        onInactive()
    }

    companion object {
        /** True for [main] and every window [main] (transitively) owns. */
        fun ownedBy(main: Window): (Any?) -> Boolean = { candidate ->
            var w = candidate as? Window
            var found = false
            while (w != null && !found) {
                found = w === main
                w = w.owner
            }
            found
        }

        /**
         * Feeds the AWT window activation events of the whole toolkit into [tracker]; returns the call that removes the
         * listener.
         */
        fun install(tracker: DesktopAppFocusTracker): () -> Unit {
            val listener = AWTEventListener { event ->
                if (event !is WindowEvent) return@AWTEventListener
                when (event.id) {
                    WindowEvent.WINDOW_ACTIVATED -> tracker.activated(event.window)
                    WindowEvent.WINDOW_DEACTIVATED -> tracker.deactivated(event.window, event.oppositeWindow)
                }
            }
            val toolkit = Toolkit.getDefaultToolkit()
            toolkit.addAWTEventListener(listener, AWTEvent.WINDOW_EVENT_MASK or AWTEvent.WINDOW_FOCUS_EVENT_MASK)
            return { toolkit.removeAWTEventListener(listener) }
        }
    }
}
