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
import net.bible.android.control.page.ChapterVerse
import net.bible.android.control.page.CurrentPageManager
import net.bible.android.view.activity.page.actionmode.VerseActionModeMediator

/**
 * Interface allowing javascript to call java methods in app
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
class BibleJavascriptInterface(
	private val verseActionModeMediator: VerseActionModeMediator,
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
    fun verseLongPress(chapterVerse: String) {
        Log.d(TAG, "Verse selected event:$chapterVerse")
        verseActionModeMediator.verseLongPress(ChapterVerse.fromHtmlId(chapterVerse))
    }

    @JavascriptInterface
    fun verseTouch(chapterVerse: String) {
        Log.d(TAG, "Verse touched event:$chapterVerse")
        verseActionModeMediator.verseTouch(ChapterVerse.fromHtmlId(chapterVerse))
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

	private val TAG get() = "BibleView[${bibleView.windowRef.get()?.id}] JSInt"
}
