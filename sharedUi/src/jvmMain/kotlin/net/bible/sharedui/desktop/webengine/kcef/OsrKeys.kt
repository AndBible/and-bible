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

import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import java.awt.Canvas
import java.awt.event.KeyEvent as AwtKeyEvent

/** Where the off-screen pane's keyboard and focus events go; implemented over the CEF browser. */
internal interface OsrKeyboardSink {
    /** CEF's keyboard focus for the pane. */
    fun setFocus(focused: Boolean)

    /** One AWT key event (pressed, typed or released) for the page. */
    fun sendKey(e: AwtKeyEvent)
}

/**
 * Compose keys to CEF. Compose Desktop 1.11 hands the handler an `InternalKeyEvent` whose `nativeEvent` is the toolkit's own
 * [AwtKeyEvent] (AWT `KEY_TYPED` arrives as type `KeyEventType.Unknown`). That original carries the key char, key code,
 * modifiers, location and the raw platform code JCEF reads, so it is forwarded unchanged; the class is internal to Compose,
 * hence the reflective read of its public getter. An event without an AWT original (synthetic, e.g. from tests) is rebuilt from
 * what Compose exposes. The page's own script turns the DOM key events into shortcuts: nothing is re-implemented here.
 */
internal object OsrKeys {
    private val source = Canvas()
    private val synthLogged = java.util.concurrent.atomic.AtomicBoolean(false)
    private val getters = java.util.concurrent.ConcurrentHashMap<Class<*>, java.lang.reflect.Method?>()

    /** Where the one-time "synthesized" note goes. */
    internal var log: (String) -> Unit = { println("kcef: $it") }

    /** The AWT event inside Compose's wrapper [nativeEvent] (or [nativeEvent] itself when it already is one); null when there is none. */
    fun original(nativeEvent: Any?): AwtKeyEvent? {
        if (nativeEvent == null) return null
        if (nativeEvent is AwtKeyEvent) return nativeEvent
        val getter = getters.computeIfAbsent(nativeEvent.javaClass) { c -> runCatching { c.getMethod("getNativeEvent") }.getOrNull() } ?: return null
        return runCatching { getter.invoke(nativeEvent) as? AwtKeyEvent }.getOrNull()
    }

    /** An AWT event rebuilt from Compose's view of a key; null for an [KeyEventType.Unknown] event that carries no character. */
    fun synthesize(type: KeyEventType, keyCode: Int, codePoint: Int, modifiersEx: Int): AwtKeyEvent? {
        val ch = if (codePoint > 0 && codePoint <= Char.MAX_VALUE.code) codePoint.toChar() else AwtKeyEvent.CHAR_UNDEFINED
        val (id, code) = when (type) {
            KeyEventType.KeyDown -> AwtKeyEvent.KEY_PRESSED to keyCode
            KeyEventType.KeyUp -> AwtKeyEvent.KEY_RELEASED to keyCode
            else -> if (ch == AwtKeyEvent.CHAR_UNDEFINED) return null else AwtKeyEvent.KEY_TYPED to AwtKeyEvent.VK_UNDEFINED
        }
        return AwtKeyEvent(source, id, System.currentTimeMillis(), modifiersEx, code, ch)
    }

    /** The AWT event to send for [e]: its original, or a rebuilt one. */
    fun awtEvent(e: KeyEvent): AwtKeyEvent? = original(e.nativeKeyEvent) ?: synthesize(e.type, e.key.nativeKeyCode, e.utf16CodePoint, modifiersOf(e))?.also {
        if (synthLogged.compareAndSet(false, true)) log("key: no AWT original in Compose's event, rebuilt it (raw platform code is lost)")
    }

    /** Test hook: makes the next synthesized event log again. */
    internal fun resetSynthLog() = synthLogged.set(false)

    private fun modifiersOf(e: KeyEvent): Int {
        var m = 0
        if (e.isShiftPressed) m = m or AwtKeyEvent.SHIFT_DOWN_MASK
        if (e.isCtrlPressed) m = m or AwtKeyEvent.CTRL_DOWN_MASK
        if (e.isAltPressed) m = m or AwtKeyEvent.ALT_DOWN_MASK
        if (e.isMetaPressed) m = m or AwtKeyEvent.META_DOWN_MASK
        return m
    }

    /** Forwards [e] to [sink]; returns true (consume) when something was forwarded. */
    fun forward(e: KeyEvent, sink: OsrKeyboardSink): Boolean {
        val awt = awtEvent(e) ?: return false
        sink.sendKey(awt)
        return true
    }
}
