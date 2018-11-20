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

package net.bible.android.view.activity.page

import net.bible.android.control.page.CurrentBiblePage
import net.bible.android.control.page.CurrentPageManager
import net.bible.android.view.activity.base.Callback

import org.apache.commons.lang3.StringEscapeUtils
import org.jetbrains.anko.AnkoLogger
import org.jetbrains.anko.debug
import org.jetbrains.anko.doAsync

/**
 * Get next or previous page for insertion at the top or bottom of the current webview.
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br></br>
 * The copyright to this program is held by it's author.
 */
class BibleInfiniteScrollPopulator(private val bibleViewtextInserter: BibleViewTextInserter, private val currentPageManager: CurrentPageManager) : AnkoLogger {

    fun requestMoreTextAtTop(chapter: Int, textId: String, callback: Callback) {
        debug("requestMoreTextAtTop")
        // do in background thread
        doAsync {
            // get page fragment for previous chapter
            val currentPage = currentPageManager.currentPage
            if (currentPage is CurrentBiblePage) {
                var fragment = currentPage.getFragmentForChapter(chapter)
                fragment = StringEscapeUtils.escapeEcmaScript(fragment)
                bibleViewtextInserter.insertTextAtTop(textId, fragment)
            }
            // tell js interface that insert is complete
            callback.okay()
        }
    }

    fun requestMoreTextAtEnd(chapter: Int, textId: String) {
        debug("requestMoreTextAtEnd")
        // do in background thread
        doAsync {
            // get page fragment for previous chapter
            val currentPage = currentPageManager.currentPage
            if (currentPage is CurrentBiblePage) {
                var fragment = currentPage.getFragmentForChapter(chapter)
                fragment = StringEscapeUtils.escapeEcmaScript(fragment)
                bibleViewtextInserter.insertTextAtEnd(textId, fragment)
            }
        }
    }
}
