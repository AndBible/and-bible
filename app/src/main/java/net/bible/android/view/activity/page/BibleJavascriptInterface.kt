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
import kotlinx.serialization.serializer
import net.bible.android.control.event.ABEventBus
import net.bible.android.control.event.ToastEvent
import net.bible.android.control.page.BibleDocument
import net.bible.android.control.page.CurrentPageManager
import net.bible.android.control.page.MyNotesDocument
import net.bible.android.database.bookmarks.BookmarkEntities
import net.bible.android.database.bookmarks.KJVA
import net.bible.android.view.activity.page.MainBibleActivity.Companion.mainBibleActivity
import net.bible.service.common.CommonUtils.json


class BibleJavascriptInterface(
	private val bibleView: BibleView
) {
    private val currentPageManager: CurrentPageManager get() = bibleView.window.pageManager
    val bookmarkControl get() = bibleView.bookmarkControl

    var notificationsEnabled = false

    @JavascriptInterface
    fun scrolledToVerse(verseOrdinal: Int) {
        val doc = bibleView.firstDocument
        if (doc is BibleDocument || doc is MyNotesDocument) {
            currentPageManager.currentBible.setCurrentVerseOrdinal(verseOrdinal,
                when (doc) {
                    is BibleDocument -> bibleView.initialVerse?.versification
                    is MyNotesDocument -> KJVA
                    else -> throw RuntimeException("Unsupported doc")
                })
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
    fun createNewJournalEntry(labelId: Long, entryType: String, afterEntryId: Long) {
        val entryOrderNumber: Int = when (entryType) {
            "bookmark" -> bookmarkControl.getBookmarkToLabel(afterEntryId, labelId)!!.orderNumber
            "journal" -> bookmarkControl.getJournalById(afterEntryId)!!.orderNumber
            "none" -> -1
            else -> throw RuntimeException("Illegal entry type")
        }
        bookmarkControl.createJournalEntry(labelId, entryOrderNumber)
    }

    @JavascriptInterface
    fun deleteJournalEntry(journalId: Long) = bookmarkControl.deleteJournalEntry(journalId)

    @JavascriptInterface
    fun removeBookmarkLabel(bookmarkId: Long, labelId: Long) = bookmarkControl.removeBookmarkLabel(bookmarkId, labelId)

    @JavascriptInterface
    fun updateOrderNumber(labelId: Long, data: String) {
        val deserialized: Map<String, List<List<Long>>> = json.decodeFromString(serializer(), data)
        val journalTextEntries = deserialized["journals"]!!.map { bookmarkControl.getJournalById(it[0])!!.apply { orderNumber = it[1].toInt() } }
        val bookmarksToLabels = deserialized["bookmarks"]!!.map { bookmarkControl.getBookmarkToLabel(it[0], labelId)!!.apply { orderNumber = it[1].toInt() } }
        bookmarkControl.updateOrderNumbers(labelId, bookmarksToLabels, journalTextEntries)
    }

    @JavascriptInterface
    fun toast(text: String) {
        ABEventBus.getDefault().post(ToastEvent(text))
    }

    @JavascriptInterface
    fun updateJournalTextEntry(data: String) {
        val entry: BookmarkEntities.StudyPadTextEntry = json.decodeFromString(serializer(), data)
        bookmarkControl.updateJournalTextEntry(entry)
    }

    @JavascriptInterface
    fun updateBookmarkToLabel(data: String) {
        val entry: BookmarkEntities.BookmarkToLabel = json.decodeFromString(serializer(), data)
        bookmarkControl.updateBookmarkTimestamp(entry.bookmarkId)
        bookmarkControl.updateBookmarkToLabel(entry)
    }

	private val TAG get() = "BibleView[${bibleView.windowRef.get()?.id}] JSInt"
}
