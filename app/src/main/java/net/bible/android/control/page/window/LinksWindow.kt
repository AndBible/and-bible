/*
 * Copyright (c) 2020-2022 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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

import net.bible.android.control.page.CurrentPageManager
import net.bible.android.control.page.window.WindowLayout.WindowState
import net.bible.android.database.WorkspaceEntities

/**
 * Window used when user selects a link
 */
class LinksWindow(window: WorkspaceEntities.Window, pageManager: CurrentPageManager, windowRepository: WindowRepository):
    Window(window, pageManager, windowRepository)
{
    override val isLinksWindow = true
    override var isPinMode: Boolean = true
        get() = windowRepository.workspaceSettings.autoPin

    /**
     * Page state should reflect active window when links window is being used after being closed.
     * Not enough to select default bible because another module type may be selected in link.
     */
    fun initialisePageStateIfClosed(activeWindow: Window) {
        // set links window state from active window if it was closed
        if (windowState == WindowState.CLOSED && !activeWindow.isLinksWindow) {
            // initialise links window documents from active window
            pageManager.restoreFrom(activeWindow.pageManager.entity)
        }
    }

    fun restoreFrom(windowEntity: WorkspaceEntities.Window,
                    pageManagerEntity: WorkspaceEntities.PageManager?,
                    workspaceTextDisplaySettings: WorkspaceEntities.TextDisplaySettings
    )
    {
        id = windowEntity.id
        workspaceId = windowEntity.workspaceId

        windowLayout.restoreFrom(windowEntity.windowLayout)
        pageManager.restoreFrom(pageManagerEntity, workspaceTextDisplaySettings)
    }
}
