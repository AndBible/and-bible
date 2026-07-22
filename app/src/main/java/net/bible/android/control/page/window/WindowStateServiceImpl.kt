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

package net.bible.android.control.page.window

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.bible.android.control.page.window.WindowLayout.WindowState
import net.bible.sharedcore.window.WindowLayoutState
import net.bible.sharedcore.window.WindowSnapshot
import net.bible.sharedcore.window.WindowStateService
import net.bible.sharedcore.window.WindowStateValue

/**
 * Authoritative reactive single-source-of-truth for the window domain (Batch 12a). Owns
 * the [layout] StateFlow; [refresh] rebuilds an immutable [WindowLayoutState] from the
 * current [WindowRepository]. Called from the window-mutation choke-points on
 * `WindowRepository` alongside the legacy `ABEventBus` posts, so the flow and the bus are
 * updated at one site (no drift). The snapshot copies out plain value types — it never
 * holds a live `Window`, so it is safe to expose to collectors even though
 * `WindowRepository.windowList` is mutated on the main thread while a background sync
 * thread iterates it.
 */
class WindowStateServiceImpl : WindowStateService {
    private val _layout = MutableStateFlow(WindowLayoutState.EMPTY)
    override val layout: StateFlow<WindowLayoutState> = _layout.asStateFlow()

    fun refresh(repo: WindowRepository) {
        _layout.value = buildSnapshot(repo)
    }

    private fun buildSnapshot(repo: WindowRepository): WindowLayoutState {
        val windows = repo.sortedWindows.map { w ->
            WindowSnapshot(
                id = w.id.toString(),
                state = w.windowState.toValue(),
                weight = w.weight,
                isVisible = w.isVisible,
                isPinMode = w.isPinMode,
                isSynchronised = w.isSynchronised,
                syncGroup = w.syncGroup,
                isLinksWindow = w.isLinksWindow,
            )
        }
        val settings = repo.workspaceSettings
        return WindowLayoutState(
            windows = windows,
            // Reading activeWindow lazily initialises a window; guard with `initialized`
            // so building a snapshot never has that side effect on an empty repository.
            activeWindowId = if (repo.initialized) repo.activeWindow.id.toString() else "",
            maximizedWindowId = repo.maximizedWindowId?.toString(),
            reverseSplitMode = settings.enableReverseSplitMode,
            restoreButtonsVisible = settings.restoreButtonsVisible,
        )
    }

    private fun WindowState.toValue(): WindowStateValue = when (this) {
        WindowState.VISIBLE -> WindowStateValue.VISIBLE
        WindowState.MINIMISED -> WindowStateValue.MINIMISED
        WindowState.CLOSED -> WindowStateValue.CLOSED
    }
}
