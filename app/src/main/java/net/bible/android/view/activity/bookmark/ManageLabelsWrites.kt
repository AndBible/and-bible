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
package net.bible.android.view.activity.bookmark

import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.launch
import net.bible.android.control.bookmark.BookmarkControl
import net.bible.android.database.IdType
import net.bible.android.database.bookmarks.BookmarkEntities

/**
 * The label manager's exit-time writes (classic `ManageLabels.saveAndExit`'s Room half), one per
 * screen visit.
 *
 * - **Once**: the screen leaves only after the writes have landed, so a second Save tap (or a
 *   StudyPad pick, which also saves) can arrive while the first is still writing. That second call
 *   is refused ([start] returns `null`) instead of writing every new label a second time. A failed
 *   save releases the guard, so the user can try again.
 * - **Lands**: the writes run on [appScope] (the application scope), so closing the screen cannot
 *   cancel them; a failure is rethrown there, where the scope's handler logs it even when nobody
 *   awaits the result any more.
 */
class ManageLabelsWrites(
    private val bookmarkControl: BookmarkControl,
    private val appScope: CoroutineScope,
) {
    private val started = AtomicBoolean(false)

    /** Whether a save of this visit is running or done (a later [start] would return `null`). */
    val isStarted: Boolean get() = started.get()

    /**
     * Deletes [deleteKeepingBookmarks] and [deleteWithOrphanedBookmarks], then inserts or updates
     * [toSave]. The result maps each new label's temporary id to its saved id (classic
     * `ManageLabels.kt:695-711`), or is `null` when a save of this visit is already running or done.
     */
    fun start(
        deleteKeepingBookmarks: List<IdType>,
        deleteWithOrphanedBookmarks: List<IdType>,
        toSave: List<BookmarkEntities.Label>,
    ): Deferred<Map<String, String>>? {
        if (!started.compareAndSet(false, true)) return null
        val newLabels = toSave.filter { it.new }
        val existingLabels = toSave.filter { !it.new }
        val result = CompletableDeferred<Map<String, String>>()
        appScope.launch {
            try {
                if (deleteKeepingBookmarks.isNotEmpty()) {
                    bookmarkControl.deleteLabels(deleteKeepingBookmarks, deleteOrphanedBookmarks = false)
                }
                if (deleteWithOrphanedBookmarks.isNotEmpty()) {
                    bookmarkControl.deleteLabels(deleteWithOrphanedBookmarks, deleteOrphanedBookmarks = true)
                }
                val idRemap = mutableMapOf<String, String>()
                for (label in newLabels) {
                    val oldId = label.id.toString()
                    val saved = bookmarkControl.insertOrUpdateLabel(label)
                    label.id = saved.id
                    label.new = false
                    idRemap[oldId] = saved.id.toString()
                }
                for (label in existingLabels) {
                    bookmarkControl.insertOrUpdateLabel(label)
                }
                result.complete(idRemap)
            } catch (e: Throwable) {
                started.set(false)
                result.completeExceptionally(e)
                throw e
            }
        }
        return result
    }
}
