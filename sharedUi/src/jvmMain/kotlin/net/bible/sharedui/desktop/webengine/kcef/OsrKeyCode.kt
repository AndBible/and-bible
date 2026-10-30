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
 * Workaround for a JCEF upstream bug: on Linux an off-screen browser's `sendKeyEvent` puts the key's keysym/char into CEF's
 * `native_key_code`, which Chromium decodes as an XKB keycode, so `KeyboardEvent.code` is wrong (`-` reports `KeyK`, `f`
 * `NonConvert`) while `keyCode` and `key` are right; on Windows `code` is empty for every key (W1). The page's shortcut
 * scripts match chords on `code`. [SCRIPT] redefines
 * `KeyboardEvent.prototype.code` to derive the US-positional DOM code from `keyCode` (and `location` for left/right
 * modifiers, numpad and numpad Enter); keys it does not know fall through to the original getter. Installed only for
 * off-screen panes on the X11 and Windows toolkits ([KcefWebEngine.osrKeyCodeFix]).
 */
internal object OsrKeyCode {
    private val simple: Map<Int, String> = buildMap {
        for (c in 'A'..'Z') put(c.code, "Key$c")
        for (d in 0..9) put('0'.code + d, "Digit$d")
        for (d in 0..9) put(96 + d, "Numpad$d")
        for (n in 1..12) put(111 + n, "F$n")
        putAll(
            mapOf(
                189 to "Minus", 187 to "Equal", 219 to "BracketLeft", 221 to "BracketRight", 220 to "Backslash",
                186 to "Semicolon", 222 to "Quote", 188 to "Comma", 190 to "Period", 191 to "Slash", 192 to "Backquote",
                8 to "Backspace", 9 to "Tab", 13 to "Enter", 27 to "Escape", 32 to "Space", 33 to "PageUp", 34 to "PageDown",
                35 to "End", 36 to "Home", 37 to "ArrowLeft", 38 to "ArrowUp", 39 to "ArrowRight", 40 to "ArrowDown",
                45 to "Insert", 46 to "Delete", 20 to "CapsLock", 91 to "MetaLeft", 92 to "MetaRight",
                106 to "NumpadMultiply", 107 to "NumpadAdd", 109 to "NumpadSubtract", 110 to "NumpadDecimal", 111 to "NumpadDivide",
            ),
        )
    }

    /** Keys whose code depends on the side: location 2 is the right one, anything else the left. */
    private val sided: Map<Int, Pair<String, String>> = mapOf(
        16 to ("ShiftLeft" to "ShiftRight"),
        17 to ("ControlLeft" to "ControlRight"),
        18 to ("AltLeft" to "AltRight"),
    )

    /** The DOM `KeyboardEvent.code` for [keyCode] at DOM [location], or null when unmapped (the original getter answers then). */
    fun codeFor(keyCode: Int, location: Int): String? {
        if (keyCode == 13 && location == 3) return "NumpadEnter"
        sided[keyCode]?.let { return if (location == 2) it.second else it.first }
        return simple[keyCode]
    }

    /** The document-start script, generated from the tables above. */
    val SCRIPT: String = run {
        val t = simple.entries.joinToString(",") { "${it.key}:'${it.value}'" }
        val s = sided.entries.joinToString(",") { "${it.key}:['${it.value.first}','${it.value.second}']" }
        "(function () { var proto = KeyboardEvent.prototype; var d = Object.getOwnPropertyDescriptor(proto, 'code');" +
            "if (!d || !d.get) return; var T = {$t}; var S = {$s};" +
            "Object.defineProperty(proto, 'code', {configurable: true, enumerable: d.enumerable, get: function () {" +
            "var k = this.keyCode, c;" +
            "if (k === 13 && this.location === 3) c = 'NumpadEnter'; else if (S[k]) c = S[k][this.location === 2 ? 1 : 0]; else c = T[k];" +
            "return c !== undefined ? c : d.get.call(this); }}); })();"
    }
}
