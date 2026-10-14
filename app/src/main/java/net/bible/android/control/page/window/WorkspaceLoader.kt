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

import net.bible.android.activity.R
import net.bible.android.control.bookmark.BookmarkControl
import net.bible.android.database.IdType
import net.bible.android.database.WorkspaceDao
import net.bible.android.database.WorkspaceEntities
import net.bible.android.database.bookmarks.BookmarkEntities
import net.bible.service.common.CommonUtils.getResourceString

/** One window as read from the database, with its StudyPad label prefetched (the apply phase must not read the database). */
class LoadedWindow(
    val entity: WorkspaceEntities.Window,
    val pageManager: WorkspaceEntities.PageManager?,
    val history: List<WorkspaceEntities.HistoryItem>,
    val studyPadLabel: BookmarkEntities.Label?,
)

class LoadedWorkspace(
    val entity: WorkspaceEntities.Workspace,
    val windows: List<LoadedWindow>,
)

/**
 * The database half of a workspace load. Waits for pending writes first, so a switch-away-and-back
 * reads the save. Everything is read here, before [WindowRepository] clears itself: the apply phase
 * posts events synchronously and must not run inside a database call.
 */
open class WorkspaceLoader(
    private val persistence: WorkspacePersistence,
    private val dao: () -> WorkspaceDao,
    private val bookmarkControl: BookmarkControl,
    private val journalInitials: String,
) {
    /**
     * @param createIfMissing true on a repository's first start: a missing or unknown stored workspace
     * is created ("Workspace 1"), not replaced by another existing workspace.
     */
    open suspend fun read(workspaceId: IdType?, createIfMissing: Boolean = false): LoadedWorkspace {
        // Reads must see every write this repository has queued (ordered write-through).
        persistence.awaitPersisted()
        val workspaceDao = dao()
        val existing = if (workspaceId != null && !workspaceId.isEmpty) workspaceDao.workspace(workspaceId) else null
        val entity = existing
            ?: (if (createIfMissing) createWorkspace() else null)
            ?: workspaceDao.firstWorkspace()
            ?: WorkspaceEntities.Workspace("").also { workspaceDao.insertWorkspace(it) }
        val windows = workspaceDao.windows(entity.id).map { window ->
            val pageManager = workspaceDao.pageManager(window.id)
            val page = pageManager?.generalBookPage
            val parts = page?.key?.split(":")
            val label = if (page?.document == journalInitials && parts?.size == 2) {
                bookmarkControl.labelById(IdType(parts[1]))
            } else null
            LoadedWindow(window, pageManager, workspaceDao.historyItems(window.id), label)
        }
        return LoadedWorkspace(entity, windows)
    }

    /**
     * Queued through [persistence] but NOT behind the restore guard that stale saves use: a created
     * workspace is not stale state, and without its row every later window insert fails its foreign
     * key (the repository would hold an in-memory-only workspace). Awaited, so those inserts find it.
     */
    private suspend fun createWorkspace(): WorkspaceEntities.Workspace {
        val created = WorkspaceEntities.Workspace(getResourceString(R.string.workspace_number, 1))
        val row = created.deepCopy()
        persistence.enqueue { insertWorkspace(row) }
        persistence.awaitPersisted()
        return created
    }
}
