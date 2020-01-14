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

import net.bible.android.control.bookmark.BookmarkControl
import net.bible.android.control.link.LinkControl
import net.bible.android.control.mynote.MyNoteControl
import net.bible.android.control.page.PageControl
import net.bible.android.control.page.PageTiltScrollControlFactory
import net.bible.android.control.page.window.Window
import net.bible.android.control.page.window.WindowControl
import net.bible.android.view.activity.MainBibleActivityScope
import net.bible.android.view.activity.page.actionmode.VerseActionModeMediator
import net.bible.android.view.activity.page.actionmode.VerseMenuCommandHandler
import java.lang.ref.WeakReference

import javax.inject.Inject

/**
 * Build a new BibleView WebView for a Window
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
@MainBibleActivityScope
class BibleViewFactory @Inject constructor(
    private val mainBibleActivity: MainBibleActivity,
    private val pageControl: PageControl,
    private val pageTiltScrollControlFactory: PageTiltScrollControlFactory,
    private val windowControl: WindowControl,
    private val bibleKeyHandler: BibleKeyHandler,
    private val linkControl: LinkControl,
    private val bookmarkControl: BookmarkControl,
    private val myNoteControl: MyNoteControl
) {

    private val windowBibleViewMap: MutableMap<Long, BibleView> = HashMap()

    fun getOrCreateBibleView(window: Window): BibleView {
        var bibleView = windowBibleViewMap[window.id]?.also {
            // Update window reference (window objects are created when loading from db, but id's are same)
            it.window = window
            window.bibleView = it
            it.listenEvents = true
        }

        if (bibleView == null) {
            val pageTiltScrollControl = pageTiltScrollControlFactory.getPageTiltScrollControl(window)
            bibleView = BibleView(this.mainBibleActivity, WeakReference(window), windowControl, bibleKeyHandler,
                pageControl, pageTiltScrollControl, linkControl)
            val bibleViewVerseActionModeMediator = VerseActionModeMediator(mainBibleActivity, bibleView, pageControl,
                VerseMenuCommandHandler(mainBibleActivity, pageControl, bookmarkControl, myNoteControl), bookmarkControl)
            val bibleInfiniteScrollPopulator = BibleInfiniteScrollPopulator(bibleView)
            val verseCalculator = VerseCalculator()
            val bibleJavascriptInterface = BibleJavascriptInterface(bibleViewVerseActionModeMediator, windowControl,
                verseCalculator, bibleInfiniteScrollPopulator, bibleView)
            bibleView.setBibleJavascriptInterface(bibleJavascriptInterface)
            bibleView.id = BIBLE_WEB_VIEW_ID_BASE + window.id.toInt()
            bibleView.initialise()
            bibleView.onDestroy = {
                windowBibleViewMap.remove(window.id)
            }

            windowBibleViewMap[window.id] = bibleView
        }
        return bibleView

    }

    fun clear() {
        windowBibleViewMap.forEach { it ->
            val bw = it.value
            bw.onDestroy = null
            bw.doDestroy()
        }
        windowBibleViewMap.clear()
    }

    companion object {

        private val BIBLE_WEB_VIEW_ID_BASE = 990
		private val TAG = "BibleViewFactory"
    }
}
