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

import androidx.compose.ui.input.key.KeyEvent
import net.bible.sharedui.desktop.webengine.DesktopWebPane
import org.koin.mp.KoinPlatform

/**
 * Reading input of the desktop app: the per-pane hooks the reading pane engine calls for each window's web pane
 * (the iOS pane factory's `gesturesFor` / `selectionMenu`: an implementation gets them from `HostReadingWindows.gesturesFor`
 * and `HostReadingWindows.selectionMenu`) and the application window's key handlers (keys and back).
 *
 * Owner: plan D3a, bound in `DesktopReadingBindings`. Default ([None]) = today's behaviour: no extra page scripts, page
 * messages all go to the bridge, no pane hooks, keys unhandled.
 *
 * The pane hooks run on the Swing event thread.
 */
interface DesktopReadingInput {
    /** Extra scripts the window's pane runs at document start (a selection-menu script, for example). */
    fun documentStartScripts(windowId: String): List<String> = emptyList()

    /** A message the window's page posted; true when it was consumed here and must not reach the reading bridge. */
    fun handleMessage(windowId: String, text: String): Boolean = false

    /** A pane was created for [windowId] (the first one, or a recreation after a renderer crash). */
    fun onPaneCreated(windowId: String, pane: DesktopWebPane) {}

    /** The [pane] of [windowId] is being disposed (window released, or its renderer died). */
    fun onPaneDisposed(windowId: String, pane: DesktopWebPane) {}

    /** The application window's `onPreviewKeyEvent`: true consumes the event. */
    fun onPreviewKeyEvent(event: KeyEvent): Boolean = false

    /** The application window's `onKeyEvent` (the event no focused element consumed): true consumes it. */
    fun onKeyEvent(event: KeyEvent): Boolean = false

    companion object {
        /** No reading input: today's behaviour, and the answer before the Koin graph exists. */
        val None: DesktopReadingInput = object : DesktopReadingInput {}

        /** The bound reading input of the running app graph, [None] before startup has published it. */
        fun current(): DesktopReadingInput = KoinPlatform.getKoinOrNull()?.getOrNull<DesktopReadingInput>() ?: None
    }
}
