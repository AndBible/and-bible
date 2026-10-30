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
 * - `wheel`: `dx`, `dy` (pixels), stamped with [clockMillis] when handled (the page clocks are per document) -> [DesktopReadingGestures]
 * - `zoom`: `dy` (Ctrl+wheel step; negative = larger) -> the font size shortcut
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
    private val selectionMenu: DesktopSelectionMenu = DesktopSelectionMenu(),
    private val x11Toolkit: Boolean = DesktopBackDispatcher.isX11Toolkit(),
    private val clockMillis: () -> Long = { System.nanoTime() / 1_000_000 },
) : DesktopReadingInput {
    /** The page script parts every pane gets. */
    val scriptParts: MutableList<String> = DesktopInputScript.DEFAULT_PARTS.toMutableList()

    /** Handlers for message types beyond the built-in ones, by `type`. */
    val extensions: MutableMap<String, (windowId: String, message: JsonObject) -> Unit> = mutableMapOf()

    private var mouseListenerInstalled = false

    init {
        selectionMenu.install(this)
    }

    override fun documentStartScripts(windowId: String): List<String> = listOf(DesktopInputScript.compose(scriptParts))

    // Installed once, on the first pane: the mouse buttons outside panes are only needed once panes exist.
    override fun onPaneCreated(windowId: String, pane: DesktopWebPane) {
        selectionMenu.onPaneCreated(windowId, pane)
        ensureMouseListener()
    }

    override fun onPaneDisposed(windowId: String, pane: DesktopWebPane) {
        selectionMenu.onPaneDisposed(windowId, pane)
        gestures.forget(windowId)
    }

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
                    key = letterOfCode(m["code"]?.jsonPrimitive?.contentOrNull)
                        ?: m["key"]?.jsonPrimitive?.contentOrNull ?: return,
                    ctrl = m.bool("ctrl"), alt = m.bool("alt"), shift = m.bool("shift"),
                    fromPane = true, editable = m.bool("editable"), composing = m.bool("composing"),
                    selection = m["selection"]?.jsonPrimitive?.contentOrNull,
                ),
                windowId,
            )
            "mouse" -> m["button"]?.jsonPrimitive?.intOrNull
                ?.let(DesktopBackDispatcher::navigationForDomMouseButton)
                ?.let { shortcuts.onNavigation(it, windowId) }
            "wheel" -> gestures.onWheel(
                windowId,
                m["dx"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                m["dy"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                clockMillis(),
            )
            "zoom" -> shortcuts.onZoom(m["dy"]?.jsonPrimitive?.doubleOrNull ?: 0.0, m["steps"]?.jsonPrimitive?.intOrNull ?: 1)
            "click" -> gestures.onClick(windowId)
            else -> if (type != null) extensions[type]?.invoke(windowId, m)
        }
    }

    /** The Latin letter of a DOM `code` such as "KeyL" (layout independent, as the page's own shortcuts), else null. */
    private fun letterOfCode(code: String?): String? =
        if (code != null && code.length == 4 && code.startsWith("Key") && code[3] in 'A'..'Z') code.substring(3).lowercase() else null

    private fun JsonObject.bool(name: String) = this[name]?.jsonPrimitive?.booleanOrNull ?: false

    /** A key-down chord was consumed and its key is still down: the typed character that follows belongs to it. */
    private var swallowTyped = false

    override fun onPreviewKeyEvent(event: KeyEvent): Boolean {
        when {
            event.type == KeyEventType.KeyUp -> swallowTyped = false
            // Compose delivers the AWT KEY_TYPED as its own event (type Unknown); a consumed chord must not type its character.
            isTyped(event) -> return swallowTyped
            event.type == KeyEventType.KeyDown -> {
                swallowTyped = false
                if (!event.isCtrlPressed && !event.isMetaPressed) return false
                val chord = composeChord(event) ?: return false
                swallowTyped = shortcuts.onChord(chord, null)
                return swallowTyped
            }
        }
        return false
    }

    /** The AWT KEY_TYPED reaches Compose as an event of type Unknown (key down and up have their own types). */
    private fun isTyped(event: KeyEvent) = event.type == KeyEventType.Unknown

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown) return false
        val chord = composeChord(event) ?: return false
        if (!(chord.alt || chord.key == "F11") || chord.ctrl) return false
        return shortcuts.onChord(chord, null)
    }

    /** An AWT mouse event outside the panes: only the press of the back/forward buttons counts (not release or click). */
    internal fun onAwtEvent(e: AWTEvent) {
        if (e is MouseEvent) onAwtMouse(e.id, e.button)
    }

    internal fun onAwtMouse(eventId: Int, button: Int) {
        if (eventId == MouseEvent.MOUSE_PRESSED) {
            DesktopBackDispatcher.navigationForAwtMouseButton(button, x11Toolkit)?.let { shortcuts.onNavigation(it) }
        }
    }

    private fun ensureMouseListener() {
        if (mouseListenerInstalled) return
        mouseListenerInstalled = true
        try {
            installMouseListener(AWTEventListener(::onAwtEvent))
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
