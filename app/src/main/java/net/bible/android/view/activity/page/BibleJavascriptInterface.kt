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
import net.bible.android.control.event.ABEventBus
import net.bible.android.control.page.CurrentPageManager
import net.bible.android.view.activity.page.MainBibleActivity.Companion.mainBibleActivity

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
    fun saveBookmarkNote(bookmarkId: Long, note: String?) {
        bibleView.bookmarkControl.saveBookmarkNote(bookmarkId, note)
    }

    @JavascriptInterface
    fun removeBookmark(bookmarkId: Long) {
        bibleView.bookmarkControl.deleteBookmarksById(listOf(bookmarkId))
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

	private val TAG get() = "BibleView[${bibleView.windowRef.get()?.id}] JSInt"
}
