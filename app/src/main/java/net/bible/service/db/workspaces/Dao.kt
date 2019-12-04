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

package net.bible.service.db.workspaces

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface WorkspaceDao {
    @Insert
    fun insertWorkspaces(vararg workspaces: Workspace)

    @Insert
    fun insertWindows(vararg windows: Window)

    @Insert
    fun insertHistoryItems(vararg historyItems: HistoryItem)

    @Update
    fun updateWindows(vararg windows: Window)

    @Delete
    fun deleteWorkspaces(vararg workspaces: Workspace)

    @Delete
    fun deleteWindows(vararg window: Window)

    @Query("SELECT * from Workspace")
    fun allWorkspaces(): Array<Workspace>

    @Query("SELECT * from Window WHERE workspaceId = :workspaceId AND NOT isLinksWindow ORDER BY orderNumber ")
    fun windows(workspaceId: Int): Array<Window>

    @Query("SELECT * from Window WHERE workspaceId = :workspaceId AND isLinksWindow")
    fun linksWindow(workspaceId: Int): Window

    @Query("SELECT * from HistoryItem WHERE windowId = :windowId ORDER BY createdAt")
    fun historyItems(windowId: Int): Array<HistoryItem>
}
