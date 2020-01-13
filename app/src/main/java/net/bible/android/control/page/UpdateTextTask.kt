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

package net.bible.android.control.page

import android.os.AsyncTask
import android.util.Log

import net.bible.android.activity.R
import net.bible.android.control.page.window.Window
import net.bible.service.format.HtmlMessageFormatter


/**
 * @author Martin Denham [mjdenham at gmail dot com]
 */
abstract class UpdateTextTask: AsyncTask<Window, Int, String>() {

    private var window: Window? = null

	var chapterVerse: ChapterVerse? = null
	var yOffsetRatio: Float? = null

    /** callbacks from base class when result is ready  */
    protected abstract fun showText(
		text: String,
		screenToUpdate: Window
	)

	override fun onPreExecute() {
        //NOOP
    }

    override fun doInBackground(vararg windows: Window): String {
        Log.d(TAG, "Loading html in background")
        var text: String
        try {
            window = windows[0]
            val currentPage = window!!.pageManager.currentPage
            val document = currentPage.currentDocument
            // if bible show whole chapter
            val key = currentPage.key
            // but allow for jump to specific verse e.g. after search result

			if(currentPage is CurrentBiblePage) {
				chapterVerse = currentPage.currentChapterVerse
			} else {
				yOffsetRatio = currentPage.currentYOffsetRatio
			}

            Log.d(TAG, "Loading document:$document key:$key")

            text = currentPage.currentPageContent

        } catch (oom: OutOfMemoryError) {
            Log.e(TAG, "Out of memory error", oom)
            System.gc()
            text = HtmlMessageFormatter.format(R.string.error_page_too_large)
        }

        return text
    }

    override fun onPostExecute(htmlFromDoInBackground: String) {
        Log.d(TAG, "Got html length " + htmlFromDoInBackground.length)
		val win = window
		if(win != null) {
			showText(htmlFromDoInBackground, win)
		} else {
			Log.e(TAG, "Window not found in onPostExecute")
		}
    }

    companion object {

        private val TAG = "UpdateTextTask"
    }
}
