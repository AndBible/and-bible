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
package net.bible.sharedui.desktop.webview

import androidx.compose.ui.platform.testTag
import net.bible.sharedcore.bibleview.BibleBridge
import net.bible.sharedcore.bibleview.BibleViewEvents
import net.bible.sharedui.reading.bridge.ReadingBridge
import net.bible.sharedui.reading.host.ReadingPaneFactory
import net.bible.sharedui.reading.session.BibleWindowSession

/**
 * Desktop [ReadingPaneFactory] (counterpart of `iosReadingPaneFactory`, without gestures and the selection menu, which
 * are D3a): at composition of an open window it wires the pane once through [DesktopReadingPaneEngine.attach] (asking
 * [sessionFor] and [bridgeFor] then) and shows the engine's current pane. [nightMode] only feeds the initial `night`
 * URL parameter. A closed window gets no session and no pane, whatever the recomposition order.
 */
fun desktopReadingPaneFactory(
    engine: DesktopReadingPaneEngine,
    sessionFor: (windowId: String) -> BibleWindowSession,
    bridgeFor: ((windowId: String) -> BibleBridge)? = null,
    nightMode: () -> Boolean = { false },
    isOpen: (windowId: String) -> Boolean = { true },
): ReadingPaneFactory = { windowId, modifier ->
    if (isOpen(windowId)) {
        if (engine.paneFor(windowId) == null && !engine.hasFailed(windowId)) {
            try {
                val session = sessionFor(windowId)
                val bridge = bridgeFor?.invoke(windowId) ?: ReadingBridge(session)
                engine.attach(
                    windowId,
                    nightMode(),
                    bridge,
                    respondNull = { callId -> session.emit(BibleViewEvents.response(callId, null)) },
                    onAttached = { session.jsChannel.flush() },
                )
            } catch (t: Throwable) {
                engine.markFailed(windowId, t)
            }
        }
        engine.paneFor(windowId)?.Content(modifier.testTag("pane-$windowId"))
    }
}
