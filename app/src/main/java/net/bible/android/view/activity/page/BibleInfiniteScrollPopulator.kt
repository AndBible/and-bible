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

import net.bible.android.control.page.CurrentBiblePage
import org.apache.commons.lang3.StringEscapeUtils

import org.jetbrains.anko.AnkoLogger
import org.jetbrains.anko.debug
import org.jetbrains.anko.doAsync

/**
 * Get next or previous page for insertion at the top or bottom of the current webview.
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
class BibleInfiniteScrollPopulator(
    private val bibleView: BibleView) : AnkoLogger
{

    fun requestMoreTextAtTop() {
        debug("requestMoreTextAtTop")
        // do in background thread
        doAsync {
            // get page fragment for previous chapter
            val currentPage = bibleView.window.pageManager.currentPage
            if (currentPage is CurrentBiblePage) {
                val newChap = bibleView.minChapter - 1

                if(newChap < 0) return@doAsync

                val fragment = currentPage.getFragmentForChapter(newChap) ?: return@doAsync
                bibleView.insertTextAtTop(newChap, fragment)
            }
        }
    }

    fun requestMoreTextAtEnd() {
        debug("requestMoreTextAtEnd")
        // do in background thread
        doAsync {
            // get page fragment for previous chapter
            val currentPage = bibleView.window.pageManager.currentPage
            if (currentPage is CurrentBiblePage) {
                val newChap = bibleView.maxChapter + 1
                val verse = currentPage.currentBibleVerse.verse
                val lastChap = verse.versification.getLastVerse(verse.book, verse.chapter)
                if(newChap > lastChap) return@doAsync
                val fragment = currentPage.getFragmentForChapter(newChap)?: return@doAsync
                //fragment = StringEscapeUtils.escapeEcmaScript(fragment)
                bibleView.insertTextAtEnd(newChap, fragment)
            }
        }
    }
}
