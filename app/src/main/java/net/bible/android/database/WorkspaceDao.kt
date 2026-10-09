/*
 * Copyright (c) 2019-2026 Martin Denham, Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.Update

@Dao
interface WorkspaceDao {
    @Insert suspend fun insertWorkspace(workspace: WorkspaceEntities.Workspace)

    @Update suspend fun updateWorkspace(workspace: WorkspaceEntities.Workspace)

    @Transaction
    suspend fun cloneWorkspace(workspaceId: IdType, newName: String): WorkspaceEntities.Workspace {
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
            it.id = IdType()
            insertWindow(it)
            if(pageManager != null) {
                pageManager.windowId = it.id
                insertPageManager(pageManager)
            }
        }

        val overrides = labelOverrides(oldWorkspace.id)
        for (override in overrides) {
            insertOrUpdateLabelOverride(override.copy(workspaceId = newWorkspace.id))
        }

        return newWorkspace
    }
    @Insert suspend fun insertPageManager(pageManager: WorkspaceEntities.PageManager)

    @Insert suspend fun insertWindow(window: WorkspaceEntities.Window)

    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertHistoryItems(historyItems: List<WorkspaceEntities.HistoryItem>)

    @Update suspend fun updateWindows(windows: List<WorkspaceEntities.Window>)

    @Update
    suspend fun updatePageManagers(pageManagers: List<WorkspaceEntities.PageManager>)

    @Query("DELETE FROM Workspace WHERE id = :workspaceId")
    suspend fun deleteWorkspace(workspaceId: IdType)

    @Query("DELETE from Window WHERE id = :windowId")
    suspend fun deleteWindow(windowId: IdType)

    @Query("DELETE from HistoryItem WHERE windowId = :windowId")
    suspend fun deleteHistoryItems(windowId: IdType)

    @Query("SELECT * from Window")
    suspend fun allWindows(): List<WorkspaceEntities.Window>

    @Query("SELECT * from Workspace WHERE id = :workspaceId")
    suspend fun workspace(workspaceId: IdType): WorkspaceEntities.Workspace?

    @Query("SELECT * from Workspace LIMIT 1")
    suspend fun firstWorkspace(): WorkspaceEntities.Workspace?

    @Query("SELECT * from Workspace ORDER BY orderNumber, name")
    suspend fun allWorkspaces(): List<WorkspaceEntities.Workspace>

    @Query("SELECT * from Window WHERE workspaceId = :workspaceId ORDER BY orderNumber ")
    suspend fun windows(workspaceId: IdType): List<WorkspaceEntities.Window>

    @Query("SELECT * from PageManager WHERE windowId = :windowId")
    suspend fun pageManager(windowId: IdType): WorkspaceEntities.PageManager?

    @Query("SELECT * from HistoryItem WHERE windowId = :windowId ORDER BY createdAt")
    suspend fun historyItems(windowId: IdType): List<WorkspaceEntities.HistoryItem>

    @Transaction
    suspend fun updateHistoryItems(windowId: IdType, entities: List<WorkspaceEntities.HistoryItem>) {
        deleteHistoryItems(windowId)
        insertHistoryItems(entities)
    }

    @Transaction
    suspend fun applyTextToDisplaySettingsToAllWorkspaces(displaySettings: WorkspaceEntities.TextDisplaySettings) {
        for(w in allWorkspaces()) {
            w.textDisplaySettings = displaySettings
            updateWorkspace(w)
        }
    }

    @Query("SELECT count() from Workspace")
    suspend fun workspacesCount(): Int

    @Update
    suspend fun updateWorkspaces(items: List<WorkspaceEntities.Workspace>)

    @Query("SELECT * FROM WorkspaceLabelOverride WHERE workspaceId = :workspaceId")
    suspend fun labelOverrides(workspaceId: IdType): List<WorkspaceEntities.WorkspaceLabelOverride>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateLabelOverride(override: WorkspaceEntities.WorkspaceLabelOverride)

    @Query("DELETE FROM WorkspaceLabelOverride WHERE workspaceId = :workspaceId AND labelId = :labelId")
    suspend fun deleteLabelOverride(workspaceId: IdType, labelId: IdType)

    @Query("DELETE FROM WorkspaceLabelOverride WHERE labelId = :labelId")
    suspend fun deleteOverridesByLabelId(labelId: IdType)
}

@Dao
interface GlobalTextDisplaySettingsDao {
    @Query("SELECT * FROM GlobalTextDisplaySettings LIMIT 1")
    suspend fun get(): GlobalTextDisplaySettings?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun set(settings: GlobalTextDisplaySettings)
}
