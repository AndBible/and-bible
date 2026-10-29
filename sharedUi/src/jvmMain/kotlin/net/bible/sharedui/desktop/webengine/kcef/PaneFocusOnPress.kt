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

import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent

/**
 * Gives a reading pane the keyboard focus when the user presses a mouse button in it. Under a window manager the X
 * focus stays on the AWT frame's focus proxy and clicking the native CEF window never moves AWT focus to it, so the page
 * would get no key events; once the component is the AWT focus owner JCEF's own focus listener moves the native focus.
 * Focus is only ever requested on a press (never on a timer or on focus loss), and not when [isFocusOwner] already.
 */
internal class PaneFocusOnPress(
    private val isFocusOwner: () -> Boolean,
    private val requestFocus: () -> Unit,
) : MouseAdapter() {
    override fun mousePressed(e: MouseEvent?) {
        if (!isFocusOwner()) requestFocus()
    }
}
