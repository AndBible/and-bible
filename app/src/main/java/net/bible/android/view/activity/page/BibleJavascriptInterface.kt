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
import net.bible.android.control.PassageChangeMediator
import net.bible.android.control.page.ChapterVerse
import net.bible.android.control.page.CurrentPageManager
import net.bible.android.control.page.window.WindowControl
import net.bible.android.view.activity.base.Callback
import net.bible.android.view.activity.base.SharedActivityState
import net.bible.android.view.activity.page.actionmode.VerseActionModeMediator
import org.json.JSONException
import org.json.JSONObject

/**
 * Interface allowing javascript to call java methods in app
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
class BibleJavascriptInterface(
	private val verseActionModeMediator: VerseActionModeMediator,
	private val windowControl: WindowControl,
	private val verseCalculator: VerseCalculator,
	private val bibleInfiniteScrollPopulator: BibleInfiniteScrollPopulator,
	private val bibleView: BibleView
) {
    private val currentPageManager: CurrentPageManager get() = bibleView.window.pageManager

    var notificationsEnabled = false

    private var addingContentAtTop = false

    private var prevCurrentChapterVerse = ChapterVerse(0, 0)

    // Create Json Object using Facebook Data
	@JavascriptInterface
	fun getChapterInfo(): String
	{
		val verse = currentPageManager.currentBible.singleKey

		val jsonObject = JSONObject()
		try {
			jsonObject.put("infinite_scroll", currentPageManager.isBibleShown)
			jsonObject.put("chapter", verse.chapter)
			jsonObject.put("first_chapter", 1)
			jsonObject.put("last_chapter", verse.versification.getLastChapter(verse.book))
		} catch (e: JSONException) {
			Log.e(TAG, "JSON error fetching chapter info", e)
		}

		return jsonObject.toString()
	}

    @JavascriptInterface
    fun onScroll(newYPos_: Int) {
        var newYPos = newYPos_
        // do not try to change verse while the page is changing - can cause all sorts of errors e.g. selected verse may not be valid in new chapter and cause chapter jumps
        if (notificationsEnabled
            && !addingContentAtTop
            && !windowControl.isSeparatorMoving()
            && bibleView.contentVisible)
        {
            if (currentPageManager.isBibleShown) {
                // All this does is change the current chapter/verse as if the user had just scrolled to another verse in the same chapter.
                // I originally thought a PassageChangeEvent would need to be raised as well as CurrentVerseChangedEvent but it seems to work fine as is!

                // if not fullscreen, and (if windows are split vertically and is firstwindow) or (windows are split horizontally) we need to add some offset
                if (!SharedActivityState.instance.isFullScreen && bibleView.isTopWindow) {
                    newYPos += (bibleView.mainBibleActivity.topOffset2 / bibleView.resources.displayMetrics.density).toInt()
                }
                val currentChapterVerse = verseCalculator.calculateCurrentVerse(newYPos)
                if (currentChapterVerse != prevCurrentChapterVerse) {
                    currentPageManager.currentBible.currentChapterVerse = currentChapterVerse
                    prevCurrentChapterVerse = currentChapterVerse
                }
            }
        }
    }

    @JavascriptInterface
    fun setContentReady() {
        Log.d(TAG, "set content ready")
        bibleView.setContentReady()
    }

    @JavascriptInterface
    fun clearVersePositionCache() {
        Log.d(TAG, "clear verse positions")
        verseCalculator.init()
    }

    @JavascriptInterface
    fun registerVersePosition(chapterVerseId: String, offset: Int) {
        verseCalculator.registerVersePosition(ChapterVerse.fromHtmlId(chapterVerseId), offset)
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
    fun requestMoreTextAtTop(chapter: Int, textId: String) {
        Log.d(TAG, "Request more text at top:$textId")
        addingContentAtTop = true
        bibleInfiniteScrollPopulator.requestMoreTextAtTop(chapter, textId, Callback { addingContentAtTop = false })
    }

    @JavascriptInterface
    fun requestMoreTextAtEnd(chapter: Int, textId: String) {
        Log.d(TAG, "Request more text at end:$textId")
        bibleInfiniteScrollPopulator.requestMoreTextAtEnd(chapter, textId)
    }

	private val TAG get() = "BibleView[${bibleView.windowRef.get()?.id}] JSInt"
}
