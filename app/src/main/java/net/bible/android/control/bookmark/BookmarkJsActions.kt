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

package net.bible.android.control.bookmark

import kotlinx.coroutines.Job
import net.bible.android.database.IdType
import net.bible.android.database.bookmarks.BookmarkEntities
import net.bible.android.database.bookmarks.BookmarkEntities.EditAction
import net.bible.sharedcore.platform.OrderedLauncher

/**
 * Fire-and-forget bookmark writes from BibleView JS, serialized per window (spec L1a §4): two calls for
 * the same window run in call order (save note, then delete), different windows run concurrently.
 * Everything runs on the launcher's application scope, so a write survives its screen closing.
 */
class BookmarkJsActions(
    private val launcher: OrderedLauncher,
    private val bookmarkControl: BookmarkControl,
) {
    /** Runs a compound write (read, modify, write, follow-up) in this window's order. */
    fun launch(windowId: Any, block: suspend () -> Unit): Job = launcher.launch(windowId, block)

    private fun normalize(note: String?) = if (note?.trim()?.isEmpty() == true) null else note

    fun saveBibleBookmarkNote(windowId: Any, id: IdType, note: String?) =
        launch(windowId) { bookmarkControl.saveBibleBookmarkNote(id, normalize(note)) }

    fun saveGenericBookmarkNote(windowId: Any, id: IdType, note: String?) =
        launch(windowId) { bookmarkControl.saveGenericBookmarkNote(id, normalize(note)) }

    fun deleteBibleBookmarks(windowId: Any, ids: List<IdType>) =
        launch(windowId) { bookmarkControl.deleteBibleBookmarksById(ids) }

    fun deleteGenericBookmarks(windowId: Any, ids: List<IdType>) =
        launch(windowId) { bookmarkControl.deleteGenericBookmarksById(ids) }

    fun updateBookmarkEditAction(windowId: Any, id: IdType, editAction: EditAction) =
        launch(windowId) { bookmarkControl.updateBookmarkEditAction(id, editAction) }

    /** [entryType] is the JS entry type of the entry the new one goes after ("none" puts it first). */
    fun createStudyPadEntry(windowId: Any, labelId: IdType, entryType: String, afterEntryId: IdType?) =
        launch(windowId) {
            val orderNumber: Int = when (entryType) {
                "bookmark" -> bookmarkControl.getBibleBookmarkToLabel(afterEntryId!!, labelId)!!.orderNumber
                "generic-bookmark" -> bookmarkControl.getGenericBookmarkToLabel(afterEntryId!!, labelId)!!.orderNumber
                "journal" -> bookmarkControl.getStudyPadById(afterEntryId!!)!!.orderNumber
                "none" -> -1
                else -> throw RuntimeException("Illegal entry type")
            }
            bookmarkControl.createStudyPadEntry(labelId, orderNumber)
        }

    fun deleteStudyPadEntry(windowId: Any, id: IdType) =
        launch(windowId) { bookmarkControl.deleteStudyPadTextEntry(id) }

    fun removeBibleBookmarkLabel(windowId: Any, bookmarkId: IdType, labelId: IdType) =
        launch(windowId) { bookmarkControl.removeBibleBookmarkLabel(bookmarkId, labelId) }

    fun removeGenericBookmarkLabel(windowId: Any, bookmarkId: IdType, labelId: IdType) =
        launch(windowId) { bookmarkControl.removeGenericBookmarkLabel(bookmarkId, labelId) }

    /** Each pair is (entry id, new order number). */
    fun updateOrderNumbers(
        windowId: Any,
        labelId: IdType,
        studyPadItems: List<Pair<String, Int>>,
        bookmarkItems: List<Pair<String, Int>>,
        genericBookmarkItems: List<Pair<String, Int>>,
    ) = launch(windowId) {
        val studyPadTextItems = studyPadItems.map { bookmarkControl.getStudyPadById(IdType(it.first))!!.apply { orderNumber = it.second } }
        val bookmarksToLabels = bookmarkItems.map { bookmarkControl.getBibleBookmarkToLabel(IdType(it.first), labelId)!!.apply { orderNumber = it.second } }
        val genericToLabels = genericBookmarkItems.map { bookmarkControl.getGenericBookmarkToLabel(IdType(it.first), labelId)!!.apply { orderNumber = it.second } }
        bookmarkControl.updateOrderNumbers(labelId, bookmarksToLabels, genericToLabels, studyPadTextItems)
    }

    fun updateStudyPadTextEntry(windowId: Any, entry: BookmarkEntities.StudyPadTextEntryWithText) =
        launch(windowId) { bookmarkControl.updateStudyPadTextEntry(entry.studyPadTextEntryEntity) }

    fun updateStudyPadTextEntryText(windowId: Any, id: IdType, text: String) =
        launch(windowId) { bookmarkControl.updateStudyPadTextEntryText(id, text) }

    fun updateBibleBookmarkToLabel(windowId: Any, entry: BookmarkEntities.BibleBookmarkToLabel) = launch(windowId) {
        bookmarkControl.updateBibleBookmarkTimestamp(entry.bookmarkId)
        bookmarkControl.updateBookmarkToLabel(entry)
    }

    fun updateGenericBookmarkToLabel(windowId: Any, entry: BookmarkEntities.GenericBookmarkToLabel) = launch(windowId) {
        bookmarkControl.updateGenericBookmarkTimestamp(entry.bookmarkId)
        bookmarkControl.updateBookmarkToLabel(entry)
    }

    /** Makes [labelId] the primary label (unless it is the Unlabeled label); [onSet] runs after the write. */
    fun setAsPrimaryLabel(windowId: Any, bookmarkId: IdType, labelId: IdType, generic: Boolean, onSet: () -> Unit) =
        launch(windowId) {
            val label = bookmarkControl.labelById(labelId) ?: return@launch
            if (label.isUnlabeledLabel) return@launch
            if (generic) bookmarkControl.setAsPrimaryLabelForGeneric(bookmarkId, labelId)
            else bookmarkControl.setAsPrimaryLabelForBible(bookmarkId, labelId)
            onSet()
        }

    fun toggleBibleBookmarkLabel(windowId: Any, bookmarkId: IdType, labelId: String) = launch(windowId) {
        val bookmark = bookmarkControl.bibleBookmarkById(bookmarkId) ?: return@launch
        bookmarkControl.toggleBookmarkLabel(bookmark, labelId)
    }

    fun toggleGenericBookmarkLabel(windowId: Any, bookmarkId: IdType, labelId: String) = launch(windowId) {
        val bookmark = bookmarkControl.genericBookmarkById(bookmarkId) ?: return@launch
        bookmarkControl.toggleBookmarkLabel(bookmark, labelId)
    }

    fun setBibleBookmarkCustomIcon(windowId: Any, bookmarkId: IdType, icon: String?) = launch(windowId) {
        val bookmark = bookmarkControl.bibleBookmarkById(bookmarkId) ?: return@launch
        bookmark.customIcon = icon
        bookmarkControl.addOrUpdateBibleBookmark(bookmark)
    }

    fun setGenericBookmarkCustomIcon(windowId: Any, bookmarkId: IdType, icon: String?) = launch(windowId) {
        val bookmark = bookmarkControl.genericBookmarkById(bookmarkId) ?: return@launch
        bookmark.customIcon = icon
        bookmarkControl.addOrUpdateGenericBookmark(bookmark)
    }

    /** [onRefused] runs instead of the write when whole-verse is turned off on a bookmark without a text range. */
    fun setBibleBookmarkWholeVerse(windowId: Any, bookmarkId: IdType, value: Boolean, onRefused: () -> Unit, onTurnedOn: () -> Unit) =
        launch(windowId) {
            val bookmark = bookmarkControl.bibleBookmarkById(bookmarkId) ?: return@launch
            if (!value && bookmark.textRange == null) { onRefused(); return@launch }
            bookmark.wholeVerse = value
            bookmarkControl.addOrUpdateBibleBookmark(bookmark)
            if (value) onTurnedOn()
        }

    fun setGenericBookmarkWholeVerse(windowId: Any, bookmarkId: IdType, value: Boolean, onRefused: () -> Unit, onTurnedOn: () -> Unit) =
        launch(windowId) {
            val bookmark = bookmarkControl.genericBookmarkById(bookmarkId) ?: return@launch
            if (!value && bookmark.textRange == null) { onRefused(); return@launch }
            bookmark.wholeVerse = value
            bookmarkControl.addOrUpdateGenericBookmark(bookmark)
            if (value) onTurnedOn()
        }
}
