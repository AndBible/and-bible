/*
 * Copyright (c) 2020 Martin Denham, Tuomas Airaksinen and the And Bible contributors.
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

package net.bible.android.view.activity.page

import android.content.Intent
import android.net.Uri
import android.util.Log
import android.webkit.JavascriptInterface
import net.bible.android.control.bookmark.JournalTextEntryAddedOrUpdatedEvent
import net.bible.android.control.event.ABEventBus
import net.bible.android.control.page.CurrentPageManager
import net.bible.android.database.bookmarks.BookmarkEntities
import net.bible.android.view.activity.page.MainBibleActivity.Companion.mainBibleActivity


class BibleJavascriptInterface(
	private val bibleView: BibleView
) {
    private val currentPageManager: CurrentPageManager get() = bibleView.window.pageManager
    val bookmarkControl get() = bibleView.bookmarkControl

    var notificationsEnabled = false

    @JavascriptInterface
    fun scrolledToVerse(verseOrdinal: Int) {
        if (currentPageManager.isBibleShown || currentPageManager.isMyNotesShown) {
            currentPageManager.currentBible.setCurrentVerseOrdinal(verseOrdinal, bibleView.initialVerse?.versification)
        }
    }

    @JavascriptInterface
    fun setClientReady() {
        Log.d(TAG, "set client ready")
        bibleView.setClientReady()
    }

    @JavascriptInterface
    fun requestPreviousChapter(callId: Long) {
        Log.d(TAG, "Request more text at top")
        bibleView.requestPreviousChapter(callId)
    }

    @JavascriptInterface
    fun requestNextChapter(callId: Long) {
        Log.d(TAG, "Request more text at end")
        bibleView.requestNextChapter(callId)
    }

    @JavascriptInterface
    fun saveBookmarkNote(bookmarkId: Long, note: String?) {
        bookmarkControl.saveBookmarkNote(bookmarkId, if(note?.trim()?.isEmpty() == true) null else note)
    }

    @JavascriptInterface
    fun removeBookmark(bookmarkId: Long) {
        bookmarkControl.deleteBookmarksById(listOf(bookmarkId))
    }

    @JavascriptInterface
    fun assignLabels(bookmarkId: Long) {
        bibleView.assignLabels(bookmarkId)
    }

    @JavascriptInterface
    fun console(loggerName: String, message: String) {
        Log.d(TAG, "Console[$loggerName] $message")
    }

    @JavascriptInterface
    fun selectionCleared() {
        Log.d(TAG, "Selection cleared!")
        bibleView.stopSelection()
    }

    @JavascriptInterface
    fun reportInputFocus(newValue: Boolean) {
        Log.d(TAG, "Focus mode now $newValue")
        ABEventBus.getDefault().post(BibleViewInputFocusChanged(bibleView, newValue))
    }

    @JavascriptInterface
    fun openExternalLink(link: String) {
        mainBibleActivity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)))
    }

    @JavascriptInterface
    fun setActionMode(enabled: Boolean) {
        bibleView.actionModeEnabled = enabled
    }

    @JavascriptInterface
    fun updateJournalTextEntry(id: Long, labelId: Long, text: String, indentLevel: Int, orderNum: Int) {
        bookmarkControl.updateJournalTextEntry(
            BookmarkEntities.JournalTextEntry(
                id = id,
                labelId = labelId,
                text = text,
                indentLevel = indentLevel,
                orderNumber = orderNum
            )
        )
    }

    @JavascriptInterface
    fun updateJournalBookmark(labelId: Long, bookmarkId: Long, indentLevel: Int, orderNum: Int) {
        bookmarkControl.updateBookmarkTimestamp(bookmarkId)
        bookmarkControl.updateBookmarkToLabel(
            BookmarkEntities.BookmarkToLabel(
                labelId = labelId,
                bookmarkId = bookmarkId,
                indentLevel = indentLevel,
                orderNumber = orderNum
            )
        )
    }

    @JavascriptInterface
    fun createNewJournalEntry(labelId: Long, entryType: String, afterEntryId: Long) {
        val entryOrderNumber: Int = when (entryType) {
            "bookmark" -> bookmarkControl.getBookmarkToLabel(afterEntryId, labelId)!!.orderNumber
            "journal" -> bookmarkControl.getJournalById(afterEntryId)!!.orderNumber
            else -> throw RuntimeException("illegal entry type")
        }
        bookmarkControl.createJournalEntry(labelId, entryOrderNumber)
    }

    @JavascriptInterface
    fun deleteJournalEntry(journalId: Long) = bookmarkControl.deleteJournalEntry(journalId)

    @JavascriptInterface
    fun removeBookmarkLabel(bookmarkId: Long, labelId: Long) = bookmarkControl.removeBookmarkLabel(bookmarkId, labelId)

	private val TAG get() = "BibleView[${bibleView.windowRef.get()?.id}] JSInt"
}
