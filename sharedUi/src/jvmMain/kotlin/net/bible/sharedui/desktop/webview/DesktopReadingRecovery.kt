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

import net.bible.android.control.page.window.WindowControl
import net.bible.android.database.IdType
import net.bible.sharedui.host.reading.IosReadingWindows
import org.koin.core.Koin

/**
 * Recovery of a reading window whose renderer process died; pass it as `DesktopReadingPaneEngine(onRendererGone = ...)`.
 *
 * Android rebuilds the BibleView on a renderer crash. Here the window's assembly is released (session and pane
 * dropped through [IosReadingWindows.release]) and, when the window is still open, assembled again ([IosReadingWindows.sessionFor])
 * and told to load its text, so the new page gets the document that the old session had marked as delivered. The pane
 * factory's recomposition then attaches the new pane. Runs on the Swing thread.
 */
class DesktopReadingRecovery(
    private val release: (String) -> Unit,
    private val isOpen: (String) -> Boolean,
    private val assemble: (String) -> Unit,
    private val reload: (String) -> Unit,
) : (String) -> Unit {
    override fun invoke(windowId: String) {
        release(windowId)
        if (!isOpen(windowId)) return
        assemble(windowId)
        reload(windowId)
    }

    companion object {
        /** The real wiring over [IosReadingWindows] and the window model of [koin]. */
        fun forWindows(koin: Koin): DesktopReadingRecovery = DesktopReadingRecovery(
            release = IosReadingWindows::release,
            isOpen = IosReadingWindows::isOpen,
            assemble = { IosReadingWindows.sessionFor(it) },
            reload = { id -> koin.get<WindowControl>().windowRepository.getWindow(IdType(id))?.loadText() },
        )
    }
}
