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

package net.bible.android.control

import net.bible.android.control.page.ChapterVerse
import net.bible.android.control.page.window.Window
import net.bible.android.control.page.window.WindowControl
import net.bible.android.view.activity.MainBibleActivityScope
import net.bible.android.view.activity.page.screen.DocumentViewManager

import org.crosswire.jsword.book.BookCategory
import org.crosswire.jsword.passage.VerseRange

import javax.inject.Inject

/** Control content of main view screen
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
@MainBibleActivityScope
class BibleContentManager @Inject
constructor(private val documentViewManager: DocumentViewManager, private val windowControl: WindowControl) {
    init {
        PassageChangeMediator.getInstance().setBibleContentManager(this)
    }

    @JvmOverloads
    fun updateText(window_: Window?, forceUpdate: Boolean = false) {
        val window = window_?: windowControl.activeWindow
        val currentPage = window.pageManager.currentPage
        val document = currentPage.currentDocument
        val key = currentPage.key
        val verse = window.pageManager.currentVersePage.currentBibleVerse.chapterVerse
        val book = window.pageManager.currentVersePage.currentBibleVerse.currentBibleBook
        val previousDocument = window.displayedBook
        val prevVerse = window.displayedKey

        if(!forceUpdate
            && previousDocument == document
            && document?.bookCategory == BookCategory.BIBLE
            && prevVerse is VerseRange
            && prevVerse.start?.book == book
            && window.hasChapterLoaded(verse.chapter)
        )
        {
            window.bibleView?.scrollOrJumpToVerseOnUIThread(ChapterVerse(verse.chapter, verse.verse))
            PassageChangeMediator.getInstance().contentChangeFinished()
        }
        else {
            window.updateText(documentViewManager)
        }

        window.displayedBook = document
        window.displayedKey = key
    }

    companion object {

        private val TAG = "BibleContentManager"
    }
}
