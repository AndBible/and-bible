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
 * Tells the pane whether the page's keyboard focus is in an editable element. The off-screen pane runs an IME text input
 * session only then: with a session always on, a CJK input method would swallow the letters the reading view's shortcut script
 * needs. Same pattern as [PaneFocusRequest]: [SCRIPT] (a document-start script) reports `focusin` / `focusout` through the
 * reserved NUL-prefixed `cefQuery`, which [parse] recognises so it never reaches the bridge.
 */
internal object PageEditableFocus {
    /** Prefix of the reserved request; NUL first, so no bridge message can equal it. */
    const val PREFIX: String = "\u0000kcef:editable:"

    /** Reports a change of "an editable element has focus" (also inside an open shadow root or a focused contenteditable). */
    const val SCRIPT: String =
        "(function () { var last = false;" +
            "function editable(el) { if (!el) return false; var t = (el.tagName || '').toUpperCase();" +
            "if (t === 'TEXTAREA') return !el.readOnly;" +
            "if (t === 'INPUT') return !el.readOnly && !/^(button|checkbox|radio|submit|reset|range|color|file|image)\$/i.test(el.type || 'text');" +
            "return !!el.isContentEditable; }" +
            "function report() { var a = document.activeElement; while (a && a.shadowRoot && a.shadowRoot.activeElement) a = a.shadowRoot.activeElement;" +
            "var now = editable(a); if (now === last) return; last = now;" +
            "try { window.cefQuery({request: '\\u0000kcef:editable:' + (now ? '1' : '0'), persistent: false, onSuccess: function () {}, onFailure: function () {}}); } catch (e) {} }" +
            "window.addEventListener('focusin', function () { setTimeout(report, 0); }, true);" +
            "window.addEventListener('focusout', function () { setTimeout(report, 0); }, true); })();"

    /** The editable flag carried by [text] (true for `...:1`, false for `...:0`), or null when [text] is not this request. */
    fun parse(text: String): Boolean? = when (text) {
        PREFIX + "1" -> true
        PREFIX + "0" -> false
        else -> null
    }
}
