/*
 * Copyright (c) 2019-2022 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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

package net.bible.android.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update

@Dao
interface WorkspaceDao {
    @Insert fun insertWorkspace(workspace: WorkspaceEntities.Workspace)

    @Update fun updateWorkspace(workspace: WorkspaceEntities.Workspace)

    @Transaction
    fun cloneWorkspace(workspaceId: IdType, newName: String): WorkspaceEntities.Workspace {
        val oldWorkspace = workspace(workspaceId)
            ?: return WorkspaceEntities.Workspace(newName).apply {
                insertWorkspace(this)
            }
        val newWorkspace = WorkspaceEntities.Workspace(
            name = newName,
            contentsText = oldWorkspace.contentsText,
            orderNumber = oldWorkspace.orderNumber,
            textDisplaySettings = oldWorkspace.textDisplaySettings,
            workspaceSettings = oldWorkspace.workspaceSettings
        )
        insertWorkspace(newWorkspace)

        val windows = windows(oldWorkspace.id)
        for (it in windows) {
            val pageManager = pageManager(it.id)
            it.workspaceId = newWorkspace.id
            it.id = IdType.random()
            insertWindow(it)
            if(pageManager != null) {
                pageManager.windowId = it.id
                insertPageManager(pageManager)
            }
        }
        return newWorkspace
    }
    @Insert fun insertPageManager(pageManager: WorkspaceEntities.PageManager)

    @Insert fun insertWindow(window: WorkspaceEntities.Window)

    @Insert fun insertWindows(windows: List<WorkspaceEntities.Window>)

    @Insert fun insertHistoryItems(historyItems: List<WorkspaceEntities.HistoryItem>)

    @Update fun updateWindows(windows: List<WorkspaceEntities.Window>)

    @Update
    fun updatePageManagers(pageManagers: List<WorkspaceEntities.PageManager>)

    @Query("DELETE FROM Workspace WHERE id = :workspaceId")
    fun deleteWorkspace(workspaceId: IdType)

    @Query("DELETE from Window WHERE id = :windowId")
    fun deleteWindow(windowId: IdType)

    @Query("DELETE from HistoryItem WHERE windowId = :windowId")
    fun deleteHistoryItems(windowId: IdType)

    @Query("SELECT * from Window")
    fun allWindows(): List<WorkspaceEntities.Window>

    @Query("SELECT * from Workspace WHERE id = :workspaceId")
    fun workspace(workspaceId: IdType): WorkspaceEntities.Workspace?

    @Query("SELECT * from Workspace LIMIT 1")
    fun firstWorkspace(): WorkspaceEntities.Workspace?

    @Query("SELECT * from Workspace ORDER BY orderNumber, name")
    fun allWorkspaces(): List<WorkspaceEntities.Workspace>

    @Query("SELECT * from Window WHERE workspaceId = :workspaceId ORDER BY orderNumber ")
    fun windows(workspaceId: IdType): List<WorkspaceEntities.Window>

    @Query("SELECT * from PageManager WHERE windowId = :windowId")
    fun pageManager(windowId: IdType): WorkspaceEntities.PageManager?

    @Query("SELECT * from HistoryItem WHERE windowId = :windowId ORDER BY createdAt")
    fun historyItems(windowId: IdType): List<WorkspaceEntities.HistoryItem>

    @Transaction
    fun updateHistoryItems(windowId: IdType, entities: List<WorkspaceEntities.HistoryItem>) {
        deleteHistoryItems(windowId)
        insertHistoryItems(entities)
    }

    @Transaction
    fun applyTextToDisplaySettingsToAllWorkspaces(displaySettings: WorkspaceEntities.TextDisplaySettings) {
        for(w in allWorkspaces()) {
            w.textDisplaySettings = displaySettings
            updateWorkspace(w)
        }
    }

    @Query("SELECT count() from Workspace")
    fun workspacesCount(): Int

    @Update
    fun updateWorkspaces(items: List<WorkspaceEntities.Workspace>)
}
