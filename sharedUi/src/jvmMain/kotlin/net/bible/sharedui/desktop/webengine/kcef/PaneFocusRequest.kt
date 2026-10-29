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

/**
 * Moves CEF's native keyboard focus into a pane when the user presses in it. Under a window manager the X focus stays on
 * the AWT frame's focus proxy and KCEF forwards no native mouse presses to AWT listeners, so the page itself reports the
 * press: [SCRIPT] (a document-start script) sends the reserved query [REQUEST] on every `mousedown`, and the pane's
 * router answers it with [handle] (CEF `setFocus(true)`), never passing it on to the bridge.
 */
internal object PaneFocusRequest {
    /** The reserved `cefQuery` request; starts with NUL, so no bridge message can equal it. */
    const val REQUEST: String = "\u0000kcef:focus"

    /** Capture-phase mousedown on window; never prevents the default or stops the event. */
    const val SCRIPT: String =
        "window.addEventListener('mousedown', function () { try { window.cefQuery({request: '\\u0000kcef:focus', persistent: false, " +
            "onSuccess: function () {}, onFailure: function () {}}); } catch (e) {} }, true);"

    /** The engine's document-start scripts: the bridge [shim], the focus script once, then the page's own [scripts]. */
    fun documentStartScripts(shim: String, scripts: List<String>): List<String> = listOf(shim, SCRIPT) + scripts

    /** Handles [text] when it is the focus request (calls [setFocus], logs once) and returns true; false for anything else. */
    fun handle(text: String, setFocus: () -> Unit, log: (String) -> Unit): Boolean {
        if (text != REQUEST) return false
        log("press: focus to pane")
        setFocus()
        return true
    }
}
