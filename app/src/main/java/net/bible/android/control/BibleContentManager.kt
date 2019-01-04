/*
 * Copyright (c) 2018 Martin Denham, Tuomas Airaksinen and the And Bible contributors.
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

package net.bible.android.control

import android.util.Log

import net.bible.android.control.page.ChapterVerse
import net.bible.android.control.page.UpdateTextTask
import net.bible.android.control.page.window.Window
import net.bible.android.control.page.window.WindowControl
import net.bible.android.view.activity.MainBibleActivityScope
import net.bible.android.view.activity.page.screen.DocumentViewManager

import org.crosswire.jsword.book.Book
import org.crosswire.jsword.passage.Key

import javax.inject.Inject

/** Control content of main view screen
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
@MainBibleActivityScope
class BibleContentManager @Inject
constructor(private val documentViewManager: DocumentViewManager?, private val windowControl: WindowControl) {

    // previous document and verse (currently displayed on the screen)
    private var previousDocument: Book? = null
    private var previousVerse: Key? = null

    init {

        PassageChangeMediator.getInstance().setBibleContentManager(this)
    }

    fun updateText(window: Window) {
        updateText(false, window)
    }

    fun updateText(forceUpdate: Boolean, window_: Window?) {
        var window = window_
        if (window == null) {
            window = windowControl.activeWindow
        }
        val currentPage = window.pageManager.currentPage
        val document = currentPage.currentDocument
        val key = currentPage.key

        // check for duplicate screen update requests
        if (!forceUpdate &&
                document != null && document == previousDocument &&
                key != null && key == previousVerse) {
            Log.w(TAG, "Duplicated screen update. Doc:" + document.initials + " Key:" + key.osisID)
        } else {

            previousDocument = document
            previousVerse = key

            UpdateMainTextTask().execute(window)
        }
    }

    private inner class UpdateMainTextTask : UpdateTextTask() {
        override fun onPreExecute() {
            super.onPreExecute()
            PassageChangeMediator.getInstance().contentChangeStarted()
        }

        override fun onPostExecute(htmlFromDoInBackground: String) {
            super.onPostExecute(htmlFromDoInBackground)
            PassageChangeMediator.getInstance().contentChangeFinished()
        }

        /** callback from base class when result is ready  */
        override fun showText(text: String, window: Window, chapterVerse: ChapterVerse, yOffsetRatio: Float) {
            if (documentViewManager != null) {
                val view = documentViewManager.getDocumentView(window)
                view.show(text, chapterVerse, yOffsetRatio)
            } else {
                Log.w(TAG, "Document view not yet registered")
            }
        }
    }

    companion object {

        private val TAG = "BibleContentManager"
    }
}
