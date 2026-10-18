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


package net.bible.sharedui.reading.bridge

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.bible.android.control.bookmark.BookmarkControl
import net.bible.android.control.bookmark.BookmarkJsActions
import net.bible.android.control.page.window.WorkspaceChanges
import net.bible.android.database.IdType
import net.bible.android.database.bookmarks.BookmarkEntities
import net.bible.android.database.bookmarks.BookmarkEntities.EditAction
import net.bible.android.view.activity.page.Selection
import net.bible.android.view.activity.page.positiveOrNull
import net.bible.sharedcore.platform.AppJson
import net.bible.sharedcore.platform.CoreStrings
import net.bible.sharedui.reading.session.BibleWindowSession
import net.bible.sharedui.reading.session.BookmarkCreation
import kotlinx.serialization.serializer
import org.koin.mp.KoinPlatform

/**
 * The bookmark, label and StudyPad calls of the JS bridge. Every write is queued through
 * [BookmarkJsActions] under the window's id (the per-window ordered queue), so a window's writes run
 * in call order. Data writes are not dropped after the session is disposed: they are keyed by
 * bookmark/label id (never by window state) and dropping a note save would lose user input;
 * only the label chooser UI is skipped for a disposed session. A chooser already open when the session
 * is disposed (view destroyed) still saves its result: it runs on the host-lifetime scope.
 */
internal class BookmarkBridgeFamily(
    private val session: BibleWindowSession,
    private val bookmarkJsActions: BookmarkJsActions,
    private val bookmarkControl: BookmarkControl,
    val creation: BookmarkCreation = BookmarkCreation(session, bookmarkControl, bookmarkJsActions),
) {
    private val windowId get() = session.window.id
    private val json get() = AppJson
    private val strings: CoreStrings by lazy { KoinPlatform.getKoin().get() }

    fun saveBookmarkNote(bookmarkId: String, note: String?) {
        bookmarkJsActions.saveBibleBookmarkNote(windowId, IdType(bookmarkId), note)
    }

    fun saveGenericBookmarkNote(bookmarkId: String, note: String?) {
        bookmarkJsActions.saveGenericBookmarkNote(windowId, IdType(bookmarkId), note)
    }

    fun removeBookmark(bookmarkId: String) {
        bookmarkJsActions.deleteBibleBookmarks(windowId, listOf(IdType(bookmarkId)))
    }

    fun removeGenericBookmark(bookmarkId: String) {
        bookmarkJsActions.deleteGenericBookmarks(windowId, listOf(IdType(bookmarkId)))
    }

    fun assignLabels(bookmarkId: String) {
        // Ordered after this window's earlier writes, so the label dialog sees them.
        bookmarkJsActions.launch(windowId) {
            val bookmark = bookmarkControl.bibleBookmarkById(IdType(bookmarkId)) ?: return@launch
            launchAssignLabels(bookmark)
        }
    }

    fun genericAssignLabels(bookmarkId: String) {
        bookmarkJsActions.launch(windowId) {
            val bookmark = bookmarkControl.genericBookmarkById(IdType(bookmarkId)) ?: return@launch
            launchAssignLabels(bookmark)
        }
    }

    /** The chooser runs on the main thread beside the queue, so an open dialog does not block the window's writes. */
    private fun launchAssignLabels(bookmark: BookmarkEntities.BaseBookmarkWithNotes) {
        if (session.isDisposed) return
        // Host lifetime, as at base (BibleView.assignLabels ran on the activity scope): the chosen labels are
        // user data, so the write must survive the session being disposed while the chooser is open.
        session.hostLifetimeScope.launch(Dispatchers.Main) { creation.assignLabels(bookmark) }
    }

    fun setBookmarkEditAction(bookmarkId: String, valueStr: String) {
        val editAction = json.decodeFromString<EditAction>(serializer(), valueStr)
        bookmarkJsActions.updateBookmarkEditAction(windowId, IdType(bookmarkId), editAction)
    }

    fun createNewStudyPadEntry(labelId: String, entryType: String, afterEntryId: String) {
        bookmarkJsActions.createStudyPadEntry(windowId, IdType(labelId), entryType, if (entryType == "none") null else IdType(afterEntryId))
    }

    fun deleteStudyPadEntry(studyPadId: String) {
        bookmarkJsActions.deleteStudyPadEntry(windowId, IdType(studyPadId))
    }

    fun removeBookmarkLabel(bookmarkId: String, labelId: String) {
        bookmarkJsActions.removeBibleBookmarkLabel(windowId, IdType(bookmarkId), IdType(labelId))
    }

    fun removeGenericBookmarkLabel(bookmarkId: String, labelId: String) {
        bookmarkJsActions.removeGenericBookmarkLabel(windowId, IdType(bookmarkId), IdType(labelId))
    }

    fun updateOrderNumber(labelId: String, data: String) {
        val deserialized: Map<String, List<Pair<String, Int>>> = json.decodeFromString(serializer(), data)
        bookmarkJsActions.updateOrderNumbers(
            windowId, IdType(labelId),
            studyPadItems = deserialized["studyPadTextItems"]!!,
            bookmarkItems = deserialized["bookmarks"]!!,
            genericBookmarkItems = deserialized["genericBookmarks"]!!,
        )
    }

    fun setStudyPadCursor(labelId: String, orderNumber: Int) {
        val windowRepository = bookmarkControl.windowControl.windowRepository
        val workspaceSettings = windowRepository.workspaceSettings
        workspaceSettings.studyPadCursors[IdType(labelId)] = orderNumber
        WorkspaceChanges.notifySettingsEdited()
    }

    fun updateStudyPadTextEntry(data: String) {
        val entry: BookmarkEntities.StudyPadTextEntryWithText = json.decodeFromString(serializer(), data)
        bookmarkJsActions.updateStudyPadTextEntry(windowId, entry)
    }

    fun updateStudyPadTextEntryText(id: String, text: String) {
        bookmarkJsActions.updateStudyPadTextEntryText(windowId, IdType(id), text)
    }

    fun updateBookmarkToLabel(data: String) {
        val entry: BookmarkEntities.BibleBookmarkToLabel = json.decodeFromString(serializer(), data)
        bookmarkJsActions.updateBibleBookmarkToLabel(windowId, entry)
    }

    fun updateGenericBookmarkToLabel(data: String) {
        val entry: BookmarkEntities.GenericBookmarkToLabel = json.decodeFromString(serializer(), data)
        bookmarkJsActions.updateGenericBookmarkToLabel(windowId, entry)
    }

    fun addBookmark(bookInitials: String, startOrdinal: Int, endOrdinal: Int, addNote: Boolean) {
        creation.makeBookmark(Selection(bookInitials, startOrdinal, positiveOrNull(endOrdinal)), true, addNote)
    }

    fun addGenericBookmark(bookInitials: String, osisRef: String, startOrdinal: Int, endOrdinal: Int, addNote: Boolean) {
        creation.makeBookmark(Selection(bookInitials, osisRef, startOrdinal, positiveOrNull(endOrdinal)), true, addNote)
    }

    fun addParagraphBreakBookmark(bookInitials: String, startOrdinal: Int, endOrdinal: Int) {
        creation.addParagraphBreakBookmark(Selection(bookInitials, startOrdinal, positiveOrNull(endOrdinal)))
    }

    fun addGenericParagraphBreakBookmark(bookInitials: String, osisRef: String, startOrdinal: Int, endOrdinal: Int) {
        creation.addParagraphBreakBookmark(Selection(bookInitials, osisRef, startOrdinal, positiveOrNull(endOrdinal)))
    }

    fun createWholePageBookmark(bookInitials: String, bookKey: String) {
        creation.createWholePageBookmark(bookInitials, bookKey)
    }

    fun setAsPrimaryLabel(bookmarkId: String, labelId: String) = setPrimary(bookmarkId, labelId, generic = false)

    fun setAsPrimaryLabelGeneric(bookmarkId: String, labelId: String) = setPrimary(bookmarkId, labelId, generic = true)

    private fun setPrimary(bookmarkId: String, labelId: String, generic: Boolean) {
        bookmarkJsActions.setAsPrimaryLabel(windowId, IdType(bookmarkId), IdType(labelId), generic = generic) {
            session.windowControl.windowRepository.updateRecentLabels(listOf(IdType(labelId)))
        }
    }

    fun toggleBookmarkLabel(bookmarkId: String, labelId: String) {
        bookmarkJsActions.toggleBibleBookmarkLabel(windowId, IdType(bookmarkId), labelId)
    }

    fun toggleGenericBookmarkLabel(bookmarkId: String, labelId: String) {
        bookmarkJsActions.toggleGenericBookmarkLabel(windowId, IdType(bookmarkId), labelId)
    }

    fun setBookmarkCustomIcon(bookmarkId: String, value: String?) {
        bookmarkJsActions.setBibleBookmarkCustomIcon(windowId, IdType(bookmarkId), value)
    }

    fun setGenericBookmarkCustomIcon(bookmarkId: String, value: String?) {
        bookmarkJsActions.setGenericBookmarkCustomIcon(windowId, IdType(bookmarkId), value)
    }

    fun setBookmarkWholeVerse(bookmarkId: String, value: Boolean) {
        bookmarkJsActions.setBibleBookmarkWholeVerse(windowId, IdType(bookmarkId), value,
            onRefused = { session.surface.toast(strings.cantChangeWholeVerse) },
            onTurnedOn = { session.surface.toast(strings.wholeVerseTurnedOn) })
    }

    fun setGenericBookmarkWholeVerse(bookmarkId: String, value: Boolean) {
        bookmarkJsActions.setGenericBookmarkWholeVerse(windowId, IdType(bookmarkId), value,
            onRefused = { session.surface.toast(strings.cantChangeWholeVerse) },
            onTurnedOn = { session.surface.toast(strings.wholeVerseTurnedOn) })
    }
}
