/*
 * Copyright (c) 2020-2026 Martin Denham, Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
 *
 * This file is part of AndBible: Bible Study (http://github.com/AndBible/and-bible).
 *
 * AndBible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * AndBible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with AndBible.
 * If not, see http://www.gnu.org/licenses/.
 */

package net.bible.android.view.activity.page

import android.util.Log
import net.bible.android.control.bookmark.BookmarkControl
import net.bible.android.control.download.DownloadControl
import net.bible.android.control.link.LinkControl
import net.bible.android.control.page.PageControl
import net.bible.android.control.page.PageTiltScrollControl
import net.bible.android.control.page.window.Window
import net.bible.android.control.page.window.WindowControl
import net.bible.android.control.search.SearchControl
import net.bible.android.database.IdType
import net.bible.service.common.CommonUtils
import java.lang.ref.WeakReference
import java.util.UUID

import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Build a new BibleView WebView for a Window
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 *
 * R5 fix round 1 (reading-host re-typing review) -- **BLOCKED for R6, left MainBibleActivity-typed
 * on purpose.** The first R5 pass retyped this to the narrow reading-host interface and bridged
 * the gap with `BibleView(this.mainBibleActivity as MainBibleActivity, ...)` -- review Critical 1
 * correctly called that a re-label, not a re-type: the only reason this class holds the value at
 * all is to hand the WHOLE thing to [BibleView], whose constructor reaches upwards of a dozen
 * distinct `MainBibleActivity`-only members (`readingInsets` twice, `isSplitVertically`,
 * `showLlmPromptSelector`, `composeSearchIfHosted`, `currentNightMode`, `startActivityForResult`,
 * `awaitIntent`, plus the interface-shaped ones). Decomposing that into individual constructor
 * parameters on [BibleView] is a real option (per the review's preferred route), but it means
 * redesigning BibleView's entire ~18-call-site dependency surface -- body-level surgery on a
 * 2000+ line, heavily-covered file this task cannot verify with only its own scoped `--tests`
 * filter. That is R6's job (BibleView is at least as big as `ComposeReadingViewHost`), not R5's
 * mechanical, no-bodies-move scope. So this file reverts to `MainBibleActivity` and is EXCLUDED
 * from [net.bible.android.view.activity.page.CollaboratorTypeGuardTest]'s scan -- see that test's
 * KDoc for the same reasoning, kept in one place rather than duplicated.
 */
class BibleViewFactory(val mainBibleActivity: MainBibleActivity) : KoinComponent {
    val pageControl: PageControl by inject()
    val windowControl: WindowControl by inject()
    val linkControl: LinkControl by inject()
    val bookmarkControl: BookmarkControl by inject()
    val downloadControl: DownloadControl by inject()
    val searchControl: SearchControl by inject()


    private val windowPageTiltScrollControlMap: MutableMap<Window, PageTiltScrollControl> = java.util.HashMap()
    private fun getPageTiltScrollControl(window: Window): PageTiltScrollControl {
        return windowPageTiltScrollControlMap[window] ?: synchronized(windowPageTiltScrollControlMap) {
            synchronized(windowPageTiltScrollControlMap) {
                windowPageTiltScrollControlMap[window] ?: PageTiltScrollControl()
            }.also {
                windowPageTiltScrollControlMap[window] = it
            }
        }
    }

    private val windowBibleViewMap: MutableMap<IdType, BibleView> = HashMap()
    init {
        Log.i(TAG, "New BibleViewFactory ${this.hashCode()}")// ${Log.getStackTraceString(Exception())}")
    }
    
    fun getOrCreateBibleView(window: Window): BibleView {
        var bibleView = windowBibleViewMap[window.id]?.also {
            // Update window reference (window objects are created when loading from db, but id's are same)
            it.window = window
            window.bibleView = it
            it.listenEvents = true
        }

        if (bibleView == null) {
            val pageTiltScrollControl = getPageTiltScrollControl(window)
            bibleView = BibleView(this.mainBibleActivity, WeakReference(window), windowControl,
                pageControl, pageTiltScrollControl, linkControl, bookmarkControl, downloadControl, searchControl)
            val bibleJavascriptInterface = BibleJavascriptInterface(bibleView)
            Log.i(TAG, "Creating new BibleView ${this.hashCode()} ${window.id}")//  ${Log.getStackTraceString(Exception())}")
            bibleView.setBibleJavascriptInterface(bibleJavascriptInterface)
            bibleView.initialise()
            bibleView.onDestroy = {
                windowBibleViewMap.remove(window.id)
            }

            windowBibleViewMap[window.id] = bibleView
            window.bibleView = bibleView
        }
        return bibleView

    }

    fun crashAll() {
        Log.i(TAG, "crashAll")
        for (it in windowBibleViewMap) {
            it.value.loadUrl("chrome://crash")
        }
    }

    fun clear() {
        Log.i(TAG, "clear")
        for (it in windowBibleViewMap) {
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
