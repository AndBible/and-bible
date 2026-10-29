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
package net.bible.sharedui.desktop.reading

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.bible.sharedui.desktop.webengine.DesktopWebPane
import net.bible.sharedui.desktop.webview.DesktopReadingInput
import java.awt.AWTEvent
import java.awt.Toolkit
import java.awt.event.AWTEventListener
import java.awt.event.MouseEvent

/**
 * The desktop [DesktopReadingInput]: wheel, clicks and keyboard shortcuts over the reading panes and the application
 * window (D3a).
 *
 * Page messages are `{"desktopInput": {"type": T, ...}}` posted by [DesktopInputScript]:
 * - `key`: `key`, `ctrl`, `alt`, `shift`, `editable`, optional `selection` (JSON string of the page selection) -> [DesktopShortcuts]
 * - `mouse`: `button` (DOM 3 = back, 4 = forward)
 * - `wheel`: `dx`, `dy` (pixels), `t` (page clock, ms) -> [DesktopReadingGestures]
 * - `click` -> [DesktopReadingGestures.onClick]
 * Other types go to [extensions] (a handler per type name: `(windowId, message) -> Unit`); a message with the envelope
 * is always consumed, an unknown type is dropped. Everything else is not ours (false, so it reaches the bridge).
 *
 * Page scripts: [scriptParts] (default [DesktopInputScript.DEFAULT_PARTS]) composed into one document-start script; add parts
 * and [extensions] together.
 *
 * Swing thread only. Compose keys: Ctrl combos in the preview handler, Alt+arrows and F11 in the normal one (after a
 * focused field had its chance); Esc is left to Compose, which feeds it to its back handlers itself.
 */
class DesktopReadingInputImpl(
    private val shortcuts: DesktopShortcuts = DesktopShortcuts(DesktopShortcutActionsImpl()),
    private val gestures: DesktopReadingGestures = DesktopReadingGestures(),
    private val installMouseListener: ((AWTEventListener) -> Unit) = ::installAwtListener,
) : DesktopReadingInput {
    /** The page script parts every pane gets. */
    val scriptParts: MutableList<String> = DesktopInputScript.DEFAULT_PARTS.toMutableList()

    /** Handlers for message types beyond the built-in ones, by `type`. */
    val extensions: MutableMap<String, (windowId: String, message: JsonObject) -> Unit> = mutableMapOf()

    private var mouseListenerInstalled = false

    override fun documentStartScripts(windowId: String): List<String> = listOf(DesktopInputScript.compose(scriptParts))

    override fun onPaneCreated(windowId: String, pane: DesktopWebPane) = ensureMouseListener()

    override fun onPaneDisposed(windowId: String, pane: DesktopWebPane) = gestures.forget(windowId)

    override fun handleMessage(windowId: String, text: String): Boolean {
        if (!text.startsWith("{\"desktopInput\"")) return false
        val message = try {
            Json.parseToJsonElement(text).jsonObject["desktopInput"]?.jsonObject
        } catch (e: Exception) {
            null
        } ?: return false
        try {
            dispatch(windowId, message)
        } catch (e: Exception) {
            System.err.println("desktop input message failed on $windowId: $e")
        }
        return true
    }

    private fun dispatch(windowId: String, m: JsonObject) {
        val type = m["type"]?.jsonPrimitive?.contentOrNull
        when (type) {
            "key" -> shortcuts.onChord(
                Chord(
                    key = m["key"]?.jsonPrimitive?.contentOrNull ?: return,
                    ctrl = m.bool("ctrl"), alt = m.bool("alt"), shift = m.bool("shift"),
                    fromPane = true, editable = m.bool("editable"), composing = m.bool("composing"),
                    selection = m["selection"]?.jsonPrimitive?.contentOrNull,
                ),
                windowId,
            )
            "mouse" -> m["button"]?.jsonPrimitive?.intOrNull
                ?.let(DesktopBackDispatcher::navigationForDomMouseButton)
                ?.let { shortcuts.onNavigation(it) }
            "wheel" -> gestures.onWheel(
                windowId,
                m["dx"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                m["dy"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                (m["t"]?.jsonPrimitive?.doubleOrNull ?: System.nanoTime() / 1_000_000.0).toLong(),
            )
            "click" -> gestures.onClick(windowId)
            else -> if (type != null) extensions[type]?.invoke(windowId, m)
        }
    }

    private fun JsonObject.bool(name: String) = this[name]?.jsonPrimitive?.booleanOrNull ?: false

    override fun onPreviewKeyEvent(event: KeyEvent): Boolean {
        ensureMouseListener()
        if (event.type != KeyEventType.KeyDown || !event.isCtrlPressed && !event.isMetaPressed) return false
        val chord = composeChord(event) ?: return false
        return shortcuts.onChord(chord, null)
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown) return false
        val chord = composeChord(event) ?: return false
        if (!(chord.alt || chord.key == "F11") || chord.ctrl) return false
        return shortcuts.onChord(chord, null)
    }

    private fun ensureMouseListener() {
        if (mouseListenerInstalled) return
        mouseListenerInstalled = true
        try {
            installMouseListener(AWTEventListener { e ->
                if (e is MouseEvent && e.id == MouseEvent.MOUSE_PRESSED) {
                    DesktopBackDispatcher.navigationForAwtMouseButton(e.button)?.let { shortcuts.onNavigation(it) }
                }
            })
        } catch (e: Exception) {
            System.err.println("desktop mouse button listener not installed: $e")
        }
    }

    companion object {
        private fun installAwtListener(l: AWTEventListener) =
            Toolkit.getDefaultToolkit().addAWTEventListener(l, AWTEvent.MOUSE_EVENT_MASK)

        /** The shortcut chord of a Compose key event; null for keys the table does not use. */
        internal fun composeChord(event: KeyEvent): Chord? {
            val key = when (event.key) {
                Key.F -> "f"; Key.L -> "l"; Key.B -> "b"
                Key.C -> "c"; Key.V -> "v"; Key.X -> "x"; Key.A -> "a"; Key.Z -> "z"
                Key.Plus, Key.NumPadAdd -> "+"
                Key.Equals -> "="
                Key.Minus, Key.NumPadSubtract -> "-"
                Key.Zero, Key.NumPad0 -> "0"
                Key.Comma -> ","
                Key.Tab -> "Tab"
                Key.F11 -> "F11"
                Key.DirectionLeft -> "ArrowLeft"
                Key.DirectionRight -> "ArrowRight"
                else -> return null
            }
            return Chord(
                key, ctrl = event.isCtrlPressed || event.isMetaPressed, alt = event.isAltPressed, shift = event.isShiftPressed,
            )
        }
    }
}
