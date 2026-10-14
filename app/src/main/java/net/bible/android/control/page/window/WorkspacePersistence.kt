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

import net.bible.android.database.WorkspaceDao
import net.bible.sharedcore.platform.OrderedLauncher

/**
 * Workspace writes leave the caller's thread in submission order (spec §4: must-complete writes, ordered).
 * The caller captures deep copies before [enqueue] returns; the block runs later, on the application launcher.
 */
class WorkspacePersistence(private val launcher: OrderedLauncher, private val dao: () -> WorkspaceDao) {
    fun enqueue(block: suspend WorkspaceDao.() -> Unit) { launcher.launch(KEY) { dao().block() } }

    /** Waits for every write enqueued before this call. */
    suspend fun awaitPersisted() = launcher.join(KEY)

    companion object { const val KEY = "workspace" }
}
