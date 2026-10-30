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
import net.bible.sharedcore.platform.AppSettings
import net.bible.sharedui.host.reading.IosReadingGestures
import net.bible.sharedui.reading.host.ReadingFullScreen
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(androidx.compose.ui.InternalComposeUiApi::class)
class DesktopReadingInputImplTest {
    private class Rec : DesktopShortcutActions {
        val log = mutableListOf<String>()
        val modalChecks = mutableListOf<String?>()
        override fun isOnReadingScreen() = true
        override fun activeWindowId() = "active"
        override fun search(windowId: String) { log += "search:$windowId" }
        override fun goToReference(windowId: String) { log += "goto:$windowId" }
        override fun back(historyFallback: Boolean) { log += "back:$historyFallback" }
        override fun navigateBack(windowId: String?) { modalChecks += windowId; log += "back:true" }
        override fun bookmarkSelection(windowId: String, selectionJson: String) { log += "bookmark:$windowId" }
        override fun changeFontSize(change: FontSizeChange, steps: Int) { log += if (steps == 1) "font:$change" else "font:$change*$steps" }
        override fun focusNextWindow() { log += "next" }
        override fun focusPreviousWindow() { log += "prev" }
        override fun toggleFullScreen() { log += "fs" }
        override fun openSettings() { log += "settings" }
        override fun escapeAllowed(windowId: String) = true
        override fun closePageModals(windowId: String?): Boolean { modalChecks += windowId; return false }
    }

    private val rec = Rec()
    private var installs = 0
    private var now = 1_000L
    private val input = DesktopReadingInputImpl(
        DesktopShortcuts(rec) { false }, DesktopReadingGestures({ null }), { installs++ }, x11Toolkit = false, clockMillis = { now },
    )

    private fun ev(key: Key, ctrl: Boolean = false, alt: Boolean = false) =
        KeyEvent(key, KeyEventType.KeyDown, isCtrlPressed = ctrl, isAltPressed = alt)

    private fun msg(body: String) = "{\"desktopInput\":$body}"

    @Test fun onlyTheEnvelopeIsConsumed() {
        assertFalse(input.handleMessage("w", "{\"method\":\"x\"}"))
        assertFalse(input.handleMessage("w", "plain"))
        assertTrue(input.handleMessage("w", msg("{\"type\":\"click\"}")))
        assertTrue(input.handleMessage("w", msg("{\"type\":\"unknown\"}")))
        assertFalse(input.handleMessage("w", "{\"desktopInput\":")) // starts like ours but is not JSON
    }

    @Test fun keyMessagesReachTheShortcuts() {
        input.handleMessage("w1", msg("{\"type\":\"key\",\"key\":\"l\",\"ctrl\":true}"))
        input.handleMessage("w1", msg("{\"type\":\"key\",\"key\":\"ArrowLeft\",\"alt\":true}"))
        input.handleMessage("w1", msg("{\"type\":\"key\",\"key\":\"Escape\"}"))
        input.handleMessage("w1", msg("{\"type\":\"key\",\"key\":\"a\",\"editable\":true}"))
        assertEquals(listOf("goto:w1", "back:true", "back:false"), rec.log)
    }

    @Test fun letterChordsAreDecodedByCodeOnAnyLayout() {
        // Cyrillic layout: key is "д", the physical key is KeyL.
        input.handleMessage("w1", msg("{\"type\":\"key\",\"key\":\"д\",\"code\":\"KeyL\",\"ctrl\":true}"))
        assertEquals(listOf("goto:w1"), rec.log)
    }

    // Compose's own AWT conversion is internal; its events are the same shape: key down/up by type, the KEY_TYPED as Unknown.
    private fun keyEv(type: KeyEventType, ctrl: Boolean) =
        KeyEvent(Key.Comma, type, codePoint = ','.code, isCtrlPressed = ctrl)

    @Test fun typedCharacterOfAConsumedChordIsConsumedToo() {
        val pressed = keyEv(KeyEventType.KeyDown, true)
        val typed = keyEv(KeyEventType.Unknown, true)
        val released = keyEv(KeyEventType.KeyUp, true)
        assertTrue(input.onPreviewKeyEvent(pressed))
        assertTrue(input.onPreviewKeyEvent(typed))
        assertFalse(input.onPreviewKeyEvent(released))
        // After the key-up a typed character is the user's again.
        assertFalse(input.onPreviewKeyEvent(typed))
        assertEquals(listOf("settings"), rec.log)
    }

    @Test fun plainTypedCommaIsNotConsumed() {
        assertFalse(input.onPreviewKeyEvent(keyEv(KeyEventType.KeyDown, false)))
        assertFalse(input.onPreviewKeyEvent(keyEv(KeyEventType.Unknown, false)))
        assertEquals(emptyList(), rec.log)
    }

    @Test fun awtBackAndForwardButtonsAcceptWindowsAndX11NumberingOnPressOnly() {
        fun ev(id: Int, button: Int) = id to button // headless AWT cannot build extra-button MouseEvents
        for (b in listOf(4)) input.onAwtMouse(ev(java.awt.event.MouseEvent.MOUSE_PRESSED, b).first, ev(java.awt.event.MouseEvent.MOUSE_PRESSED, b).second)
        for (b in listOf(5)) input.onAwtMouse(ev(java.awt.event.MouseEvent.MOUSE_PRESSED, b).first, ev(java.awt.event.MouseEvent.MOUSE_PRESSED, b).second)
        input.onAwtMouse(ev(java.awt.event.MouseEvent.MOUSE_RELEASED, 4).first, ev(java.awt.event.MouseEvent.MOUSE_RELEASED, 4).second)
        input.onAwtMouse(ev(java.awt.event.MouseEvent.MOUSE_CLICKED, 4).first, ev(java.awt.event.MouseEvent.MOUSE_CLICKED, 4).second)
        input.onAwtMouse(ev(java.awt.event.MouseEvent.MOUSE_PRESSED, 1).first, ev(java.awt.event.MouseEvent.MOUSE_PRESSED, 1).second)
        assertEquals(listOf("back:true"), rec.log)
    }

    @Test fun x11NumberingTakesOnlyButtons6And7() {
        val x11 = DesktopReadingInputImpl(DesktopShortcuts(rec) { false }, DesktopReadingGestures({ null }), {}, x11Toolkit = true)
        val p = java.awt.event.MouseEvent.MOUSE_PRESSED
        x11.onAwtMouse(p, 4) // horizontal scroll on X11
        x11.onAwtMouse(p, 5)
        assertEquals(emptyList(), rec.log)
        x11.onAwtMouse(p, 6)
        x11.onAwtMouse(p, 7)
        assertEquals(listOf("back:true"), rec.log)
    }

    @Test fun altGrChordsAreNotConsumedNorTheirTypedCharacter() {
        // Windows AltGr = Ctrl+Alt: AltGr+L (ł), AltGr+0 (}) must reach the text field.
        for (key in listOf(Key.L, Key.Zero)) {
            val down = KeyEvent(key, KeyEventType.KeyDown, isCtrlPressed = true, isAltPressed = true)
            val typed = KeyEvent(key, KeyEventType.Unknown, codePoint = 'ł'.code, isCtrlPressed = true, isAltPressed = true)
            assertFalse(input.onPreviewKeyEvent(down))
            assertFalse(input.onPreviewKeyEvent(typed))
        }
        assertEquals(emptyList(), rec.log)
    }

    @Test fun wheelFramesAreStampedWithTheKotlinClockNotThePageClock() {
        // Two panes with unrelated page clocks (a fresh document starts near 0) feed one gesture object. With the Kotlin
        // clock the 200 frames form one gesture (80 px, past the full screen threshold); with the page's `t` the
        // alternating clocks would restart the gesture every frame.
        val settings = java.lang.reflect.Proxy.newProxyInstance(
            AppSettings::class.java.classLoader, arrayOf(AppSettings::class.java),
        ) { _, m, a ->
            when (m.name) {
                "getBoolean" -> if (a[0] == "auto_fullscreen_pref") true else a[1]
                "getString", "getLong", "getInt", "getDouble", "getFloat" -> a[1]
                else -> null
            }
        } as AppSettings
        val fullScreen = ReadingFullScreen()
        val real = IosReadingGestures(fullScreen, settings, { false }, { _, _ -> }, onPaneTouched = {})
        val wheelInput = DesktopReadingInputImpl(
            DesktopShortcuts(rec) { false }, DesktopReadingGestures({ real }), {}, x11Toolkit = false, clockMillis = { now },
        )
        for (i in 0 until 200) {
            now = 10_000L + i * 4L
            val (id, pageClock) = if (i % 2 == 0) "w1" to 50_000.0 + i else "w2" to 100.0 + i
            wheelInput.handleMessage(id, msg("{\"type\":\"wheel\",\"dx\":0,\"dy\":0.4,\"t\":$pageClock}"))
        }
        assertTrue(fullScreen.state.value)
        assertFalse("performance.now" in DesktopInputScript.WHEEL_PART)
    }

    @Test fun zoomMessagesChangeFontSize() {
        input.handleMessage("w1", msg("{\"type\":\"zoom\",\"dy\":-100}"))
        input.handleMessage("w1", msg("{\"type\":\"zoom\",\"dy\":1,\"steps\":2}"))
        assertEquals(listOf("font:Larger", "font:Smaller*2"), rec.log)
    }

    @Test fun scriptKeepsFieldKeysFromThePageWithoutBlockingTheTarget() {
        val k = DesktopInputScript.KEY_PART
        assertTrue(k.contains("e.code"))
        assertTrue(k.contains("t.addEventListener('keydown', f)")) // one-shot target listener, after the target's own
        assertFalse(k.contains("e.stopPropagation(); return;")) // no window-level stop for field keys
        val z = DesktopInputScript.ZOOM_PART
        assertTrue(z.contains("preventDefault") && z.contains("passive: false") && z.contains("steps"))
    }

    @Test fun mouseButtonsMapToBackAndForward() {
        input.handleMessage("w1", msg("{\"type\":\"mouse\",\"button\":3}"))
        input.handleMessage("w1", msg("{\"type\":\"mouse\",\"button\":4}"))
        assertEquals(listOf("back:true"), rec.log)
    }

    @Test fun mouseBackPassesItsPaneToTheModalCheck() {
        input.handleMessage("w2", msg("{\"type\":\"mouse\",\"button\":3}"))
        assertEquals(listOf<String?>("w2"), rec.modalChecks)
    }

    @Test fun extensionsGetTheirMessages() {
        var got: String? = null
        input.extensions["contextmenu"] = { w, m -> got = w + m["x"].toString() }
        input.handleMessage("w9", msg("{\"type\":\"contextmenu\",\"x\":5}"))
        assertEquals("w95", got)
    }

    @Test fun scriptIsOneDocumentStartScriptWithAllParts() {
        val s = input.documentStartScripts("w1").single()
        for (p in DesktopInputScript.DEFAULT_PARTS) assertTrue(s.contains(p.trim()))
        input.scriptParts += "/* extra */"
        assertTrue(input.documentStartScripts("w1").single().contains("/* extra */"))
    }

    @Test fun mouseListenerIsInstalledOnce() {
        input.onPaneCreated("w1", noPane())
        input.onPaneCreated("w2", noPane())
        input.onPreviewKeyEvent(ev(Key.A))
        assertEquals(1, installs)
    }

    @Test fun composeKeysMapToChords() {
        val ctrlTab = DesktopReadingInputImpl.composeChord(ev(Key.Tab, ctrl = true))
        assertEquals(Chord("Tab", ctrl = true), ctrlTab)
        val altLeft = DesktopReadingInputImpl.composeChord(ev(Key.DirectionLeft, alt = true))
        assertEquals(Chord("ArrowLeft", alt = true), altLeft)
        assertTrue(input.onPreviewKeyEvent(ev(Key.Tab, ctrl = true)))
        assertTrue(input.onKeyEvent(ev(Key.DirectionLeft, alt = true)))
        assertFalse(input.onPreviewKeyEvent(ev(Key.C, ctrl = true)))
        assertFalse(input.onKeyEvent(ev(Key.Escape)))
        assertEquals(listOf("next", "back:true"), rec.log)
    }

    @Test fun backFallsBackToHistoryOnlyWhenAsked() {
        val log = mutableListOf<String>()
        val a = DesktopShortcutActionsImpl({ false }, { true }, { log += "history" })
        a.back(historyFallback = false)
        assertEquals(emptyList(), log)
        a.back(historyFallback = true)
        assertEquals(listOf("history"), log)
        val consumed = DesktopShortcutActionsImpl({ true }, { true }, { log += "again" })
        consumed.back(historyFallback = true)
        assertEquals(listOf("history"), log)
    }

    private fun noPane(): net.bible.sharedui.desktop.webengine.DesktopWebPane =
        net.bible.sharedui.desktop.webengine.FakeWebEngine().createPane("p", "u", emptyList()) {}
}
