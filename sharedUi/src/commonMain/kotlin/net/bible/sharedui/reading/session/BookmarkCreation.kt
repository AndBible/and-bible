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


@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package net.bible.sharedui.reading.session

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import net.bible.android.control.bookmark.BookmarkControl
import net.bible.android.control.bookmark.BookmarkJsActions
import net.bible.android.database.IdType
import net.bible.android.database.bookmarks.BookmarkEntities
import net.bible.android.database.bookmarks.isAddonId
import net.bible.android.view.activity.page.Selection
import net.bible.sharedcore.bibleview.BibleViewEvents
import net.bible.sharedcore.log.Log
import org.crosswire.jsword.book.BookCategory
import org.crosswire.jsword.book.Books
import org.koin.mp.KoinPlatform

/**
 * Creates bookmarks for one reading window (selection to bookmark, whole page, paragraph break) and
 * runs the label chooser. Every write goes through [BookmarkJsActions] under the window's id, the
 * per-window ordered queue, and the click event that opens the new bookmark follows the write.
 */
@kotlin.native.HiddenFromObjC
class BookmarkCreation(
    private val session: BibleWindowSession,
    private val bookmarkControl: BookmarkControl,
    private val bookmarkJsActions: BookmarkJsActions = KoinPlatform.getKoin().get(),
) {
    private fun newBookmark(selection: Selection, wholeVerse: Boolean): BookmarkEntities.BaseBookmarkWithNotes {
        val textRange =
            if (selection.startOffset != null && selection.endOffset != null)
                BookmarkEntities.TextRange(selection.startOffset!!, selection.endOffset!!)
            else null

        return if (selection.book?.bookCategory == BookCategory.BIBLE) {
            val verseRange = selection.verseRange
            BookmarkEntities.BibleBookmarkWithNotes(verseRange!!, textRange, wholeVerse, selection.swordBook)
        } else {
            BookmarkEntities.GenericBookmarkWithNotes(
                key = selection.osisRef!!,
                book = selection.book!!,
                ordinalStart = selection.startOrdinal,
                ordinalEnd = selection.endOrdinal,
                textRange = textRange,
                wholeVerse = wholeVerse,
                new = true,
            )
        }
    }

    /** Bookmarks [selection] with the workspace's auto-assign labels; a null selection does nothing. */
    fun makeBookmark(selection: Selection?, wholeVerse: Boolean, openNotes: Boolean) {
        selection ?: return
        Log.i(TAG, "makeBookmark")

        val initialLabels = session.workspaceSettings.autoAssignLabels
        val primaryLabelId = session.workspaceSettings.autoAssignPrimaryLabel
        val bookmark = newBookmark(selection, wholeVerse)

        // Write in this window's order on the application scope; the click event follows the write.
        bookmarkJsActions.launch(session.window.id) {
            if (primaryLabelId != null) {
                val label = bookmarkControl.labelById(primaryLabelId)
                if (label != null) {
                    bookmark.primaryLabelId = primaryLabelId
                }
            }

            bookmarkControl.addOrUpdateBookmark(bookmark, initialLabels)
            if (initialLabels.isEmpty() || openNotes) {
                session.emit(BibleViewEvents.bookmarkClicked(bookmark.id.toString(), openNotes))
            }
        }
    }

    /**
     * "Bookmark this verse" on an add-on collection bookmark: the user's own new bookmark of the same range, with the
     * workspace's auto-assign labels (add-on labels among them are dropped), opened like any new bookmark. The add-on
     * bookmark itself is read-only and stays as it is.
     */
    fun bookmarkAddonVerse(addonBookmarkId: IdType) {
        val initialLabels = session.workspaceSettings.autoAssignLabels
        val primaryLabelId = session.workspaceSettings.autoAssignPrimaryLabel
        bookmarkJsActions.launch(session.window.id) {
            val bookmark = bookmarkControl.newOwnBookmarkLike(addonBookmarkId) ?: return@launch
            if (primaryLabelId != null && !primaryLabelId.isAddonId && bookmarkControl.labelById(primaryLabelId) != null) {
                bookmark.primaryLabelId = primaryLabelId
            }
            bookmarkControl.addOrUpdateBookmark(bookmark, initialLabels)
            session.emit(BibleViewEvents.bookmarkClicked(bookmark.id.toString(), false))
        }
    }

    /** Bookmarks a whole page (generic document key); an unknown [bookInitials] does nothing. */
    fun createWholePageBookmark(bookInitials: String, bookKey: String) {
        val book = Books.installed().getBook(bookInitials) ?: return

        val initialLabels = session.workspaceSettings.autoAssignLabels
        val primaryLabelId = session.workspaceSettings.autoAssignPrimaryLabel

        val bookmark = BookmarkEntities.GenericBookmarkWithNotes(
            key = bookKey,
            book = book,
            ordinalStart = null,
            ordinalEnd = null,
            textRange = null,
            wholeVerse = true,
            new = true,
        )

        bookmarkJsActions.launch(session.window.id) {
            if (primaryLabelId != null) {
                val label = bookmarkControl.labelById(primaryLabelId)
                if (label != null) {
                    bookmark.primaryLabelId = primaryLabelId
                }
            }

            bookmarkControl.addOrUpdateGenericBookmark(bookmark, initialLabels)
            if (initialLabels.isEmpty()) {
                session.emit(BibleViewEvents.bookmarkClicked(bookmark.id.toString(), null))
            }
        }
    }

    /** Bookmarks [selection] with the paragraph break label as its only and primary label. */
    fun addParagraphBreakBookmark(selection: Selection?) {
        selection ?: return
        Log.i(TAG, "addParagraphBreakBookmark")

        val bookmark = newBookmark(selection, wholeVerse = false)

        bookmarkJsActions.launch(session.window.id) {
            val paragraphBreakLabelId = bookmarkControl.paragraphBreakLabel().id
            bookmark.primaryLabelId = paragraphBreakLabelId
            bookmarkControl.addOrUpdateBookmark(bookmark, setOf(paragraphBreakLabelId))
        }
    }

    /**
     * Lets the user choose labels for [bookmark] through [net.bible.sharedui.reading.bridge.ReadingNavigator]
     * and saves the choice; a cancelled chooser leaves the labels unchanged. Call it on the main
     * thread (the chooser is UI); the database work moves to IO.
     */
    suspend fun assignLabels(bookmark: BookmarkEntities.BaseBookmarkWithNotes) {
        val labels = withContext(Dispatchers.IO) {
            try {
                // An add-on collection bookmark is read-only: the chooser is not offered at all.
                bookmarkControl.requireOwnRows(bookmark.id)
            } catch (e: net.bible.android.control.bookmark.AddonReadOnlyException) {
                bookmarkControl.reportAddonReadOnly(e)
                return@withContext null
            }
            bookmarkControl.labelsForBookmark(bookmark).map { it.id }
        } ?: return
        val chosen = session.navigator.assignLabels(bookmark, labels) ?: return
        withContext(Dispatchers.IO) {
            try {
                bookmarkControl.addOrUpdateBookmark(bookmark, chosen.toSet())
            } catch (e: net.bible.android.control.bookmark.AddonReadOnlyException) {
                bookmarkControl.reportAddonReadOnly(e) // e.g. an add-on label put on the bookmark
            }
        }
    }

    private companion object {
        const val TAG = "BookmarkCreation"
    }
}
