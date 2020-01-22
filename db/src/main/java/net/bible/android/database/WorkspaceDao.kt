/*
 * Copyright (c) 2019 Martin Denham, Tuomas Airaksinen and the And Bible contributors.
 *
 * This file is part of And Bible (http://github.com/AndBible/and-bible).
 *
 * And Bible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * And Bible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with And Bible.
 * If not, see http://www.gnu.org/licenses/.
 *
 */

package net.bible.android.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update

@Dao
interface WorkspaceDao {
    @Insert
    fun insertWorkspace(workspace: WorkspaceEntities.Workspace): Long

    @Update
    fun updateWorkspace(workspace: WorkspaceEntities.Workspace)

    @Transaction
    fun cloneWorkspace(workspaceId: Long, newName: String): WorkspaceEntities.Workspace {
        val oldWorkspace = workspace(workspaceId)
            ?: return WorkspaceEntities.Workspace(newName).apply {
                id = insertWorkspace(this)
            }
        val newWorkspace = WorkspaceEntities.Workspace(newName, 0, oldWorkspace.textDisplaySettings, oldWorkspace.windowBehaviorSettings)
        newWorkspace.id = insertWorkspace(newWorkspace)

        val windows = windows(oldWorkspace.id)
        windows.forEach {
            val pageManager = pageManager(it.id)
            it.workspaceId = newWorkspace.id
            it.id = 0
            it.id = insertWindow(it)
            if(pageManager != null) {
                pageManager.windowId = it.id
                insertPageManager(pageManager)
            }
        }
        return newWorkspace
    }
    @Insert
    fun insertPageManager(pageManager: WorkspaceEntities.PageManager)

    @Insert
    fun insertWindow(window: WorkspaceEntities.Window): Long

    @Insert
    fun insertWindows(windows: List<WorkspaceEntities.Window>): Array<Long>

    @Insert
    fun insertHistoryItems(historyItems: List<WorkspaceEntities.HistoryItem>)

    @Update
    fun updateWindows(windows: List<WorkspaceEntities.Window>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun updatePageManagers(pageManagers: List<WorkspaceEntities.PageManager>)

    @Query("DELETE FROM Workspace WHERE id = :workspaceId")
    fun deleteWorkspace(workspaceId: Long)

    @Query("DELETE from Window WHERE id = :windowId")
    fun deleteWindow(windowId: Long)

    @Query("DELETE from HistoryItem WHERE windowId = :windowId")
    fun deleteHistoryItems(windowId: Long)

    @Query("SELECT * from Workspace WHERE id = :workspaceId")
    fun workspace(workspaceId: Long): WorkspaceEntities.Workspace?

    @Query("SELECT * from Workspace LIMIT 1")
    fun firstWorkspace(): WorkspaceEntities.Workspace?

    @Query("SELECT * from Workspace")
    fun allWorkspaces(): List<WorkspaceEntities.Workspace>

    @Query("SELECT * from Window WHERE workspaceId = :workspaceId AND NOT isLinksWindow ORDER BY orderNumber ")
    fun windows(workspaceId: Long): List<WorkspaceEntities.Window>

    @Query("SELECT * from Window WHERE workspaceId = :workspaceId AND isLinksWindow")
    fun linksWindow(workspaceId: Long): WorkspaceEntities.Window?

    @Query("SELECT * from PageManager WHERE windowId = :windowId")
    fun pageManager(windowId: Long): WorkspaceEntities.PageManager?

    @Query("SELECT * from HistoryItem WHERE windowId = :windowId ORDER BY createdAt")
    fun historyItems(windowId: Long): List<WorkspaceEntities.HistoryItem>

    @Transaction
    fun updateHistoryItems(windowId: Long, entities: List<WorkspaceEntities.HistoryItem>) {
        deleteHistoryItems(windowId)
        insertHistoryItems(entities)
    }
}
