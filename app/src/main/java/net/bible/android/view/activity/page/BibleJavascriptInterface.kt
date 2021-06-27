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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.serialization.serializer
import net.bible.android.activity.R
import net.bible.android.control.event.ABEventBus
import net.bible.android.control.event.ToastEvent
import net.bible.android.control.page.BibleDocument
import net.bible.android.control.page.CurrentPageManager
import net.bible.android.control.page.MyNotesDocument
import net.bible.android.control.page.OsisDocument
import net.bible.android.control.page.StudyPadDocument
import net.bible.android.database.bookmarks.BookmarkEntities
import net.bible.android.database.bookmarks.KJVA
import net.bible.android.view.activity.page.MainBibleActivity.Companion.mainBibleActivity
import net.bible.android.view.util.widget.ShareWidget
import net.bible.service.common.CommonUtils.json


class BibleJavascriptInterface(
	private val bibleView: BibleView
) {
    private val currentPageManager: CurrentPageManager get() = bibleView.window.pageManager
    val bookmarkControl get() = bibleView.bookmarkControl

    var notificationsEnabled = false

    @JavascriptInterface
    fun scrolledToOrdinal(ordinal: Int) {
        val doc = bibleView.firstDocument
        if (doc is BibleDocument || doc is MyNotesDocument) {
            currentPageManager.currentBible.setCurrentVerseOrdinal(ordinal,
                when (doc) {
                    is BibleDocument -> bibleView.initialVerse?.versification
                    is MyNotesDocument -> KJVA
                    else -> throw RuntimeException("Unsupported doc")
                })
        } else if(doc is OsisDocument || doc is StudyPadDocument) {
            currentPageManager.currentPage.anchorOrdinal = ordinal
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

    @JavascriptInterface
    fun shareBookmarkVerse(bookmarkId: Long) {
        val bookmark = bookmarkControl.bookmarkById(bookmarkId)!!
        GlobalScope.launch(Dispatchers.Main) {
            ShareWidget.dialog(mainBibleActivity, bookmark)
        }
    }

    @JavascriptInterface
    fun openStudyPad(labelId: Long, bookmarkId: Long) {
        GlobalScope.launch(Dispatchers.Main) {
            bibleView.linkControl.openJournal(labelId, bookmarkId)
        }
    }

    @JavascriptInterface
    fun openMyNotes(bookmarkId: Long) {
        GlobalScope.launch(Dispatchers.Main) {
            bibleView.linkControl.openMyNotes(bookmarkId)
        }
    }

    @JavascriptInterface
    fun setAsPrimaryLabel(bookmarkId: Long, labelId: Long) {
        val label = bookmarkControl.labelById(labelId)!!
        if(label.isUnlabeledLabel) {
            return
        }
        bookmarkControl.setAsPrimaryLabel(bookmarkId, labelId)
        bibleView.windowControl.windowRepository.updateRecentLabels(listOf(labelId))
    }

    @JavascriptInterface
    fun toggleBookmarkLabel(bookmarkId: Long, labelId: Long) {
        val bookmark = bookmarkControl.bookmarkById(bookmarkId)!!
        val labels = bookmarkControl.labelsForBookmark(bookmark).toMutableList()
        val foundLabel = labels.find { it.id == labelId }
        if(foundLabel !== null) {
            labels.remove(foundLabel)
        } else {
            labels.add(bookmarkControl.labelById(labelId)!!)
        }
        bookmarkControl.setLabelsForBookmark(bookmark, labels)
    }

    @JavascriptInterface
    fun reportModalState(value: Boolean) {
        bibleView.modalOpen = value
    }

    @JavascriptInterface
    fun setBookmarkWholeVerse(bookmarkId: Long, value: Boolean) {
        val bookmark = bookmarkControl.bookmarkById(bookmarkId)!!
        if(!value && bookmark.textRange == null) {
            ABEventBus.getDefault().post(ToastEvent(R.string.cant_change_wholeverse))
            return
        }
        bookmark.wholeVerse = value

        bookmarkControl.addOrUpdateBookmark(bookmark)
        if(value) ABEventBus.getDefault().post(ToastEvent(R.string.whole_verse_turned_on))
    }

    @JavascriptInterface
    fun toggleCompareDocument(documentId: String) {
        val hideDocs = bibleView.workspaceSettings.hideCompareDocuments
        if(hideDocs.contains(documentId)) {
            hideDocs.remove(documentId)
        } else {
            hideDocs.add(documentId)
        }
        ABEventBus.getDefault().post(AppSettingsUpdated())
    }

    private val TAG get() = "BibleView[${bibleView.windowRef.get()?.id}] JSInt"
}
