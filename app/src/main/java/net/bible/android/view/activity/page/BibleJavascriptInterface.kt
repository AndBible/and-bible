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

import android.util.Log
import android.webkit.JavascriptInterface
import net.bible.android.control.page.CurrentPageManager
import net.bible.android.database.bookmarks.BookmarkEntities
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.sword.SwordBook
import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.passage.VerseRange

/**
 * Interface allowing javascript to call java methods in app
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
class BibleJavascriptInterface(
	private val bibleView: BibleView
) {
    private val currentPageManager: CurrentPageManager get() = bibleView.window.pageManager

    var notificationsEnabled = false

    @JavascriptInterface
    fun scrolledToVerse(verseOrdinal: Int) {
        if (currentPageManager.isBibleShown) {
            currentPageManager.currentBible.currentVerseOrdinal = verseOrdinal
        }
    }

    @JavascriptInterface
    fun setClientReady() {
        Log.d(TAG, "set client ready")
        bibleView.setClientReady()
    }

    @JavascriptInterface
    fun requestMoreTextAtTop(callId: Long) {
        Log.d(TAG, "Request more text at top")
        bibleView.requestMoreTextAtTop(callId)
    }

    @JavascriptInterface
    fun requestMoreTextAtEnd(callId: Long) {
        Log.d(TAG, "Request more text at end")
        bibleView.requestMoreTextAtEnd(callId)
    }

    @JavascriptInterface
    fun makeBookmark(callId: Long, bookInitials: String, startOrdinal: Int, startOffset: Int, endOrdinal: Int, endOffset: Int) {
        Log.d(TAG, "makeBookmark")
        val book = Books.installed().getBook(bookInitials)
        if(book !is SwordBook) {
            // TODO: error response to JS
            return
        }

        val v11n = book.versification
        val verseRange = VerseRange(v11n, Verse(v11n, startOrdinal), Verse(v11n, endOrdinal))
        val textRange = BookmarkEntities.TextRange(startOffset, endOffset)
        val bookmark = BookmarkEntities.Bookmark(verseRange, textRange, book)
        bibleView.bookmarkControl.addOrUpdateBookmark(bookmark)
        bibleView.executeJavascriptOnUiThread("bibleView.response($callId, ${bookmark.toJson()});")
    }

	private val TAG get() = "BibleView[${bibleView.windowRef.get()?.id}] JSInt"
}
