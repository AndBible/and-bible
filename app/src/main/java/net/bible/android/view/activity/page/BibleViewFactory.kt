/*
 * Copyright (c) 2020-2022 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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
import net.bible.service.common.CommonUtils
import java.lang.ref.WeakReference
import java.util.UUID

import javax.inject.Inject

/**
 * Build a new BibleView WebView for a Window
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
class BibleViewFactory(val mainBibleActivity: MainBibleActivity) {
    @Inject lateinit var pageControl: PageControl
    @Inject lateinit var windowControl: WindowControl
    @Inject lateinit var linkControl: LinkControl
    @Inject lateinit var bookmarkControl: BookmarkControl
    @Inject lateinit var downloadControl: DownloadControl
    @Inject lateinit var searchControl: SearchControl

    init {
        CommonUtils.buildActivityComponent().inject(this)
    }

    private val windowPageTiltScrollControlMap: MutableMap<Window, PageTiltScrollControl> = java.util.HashMap()
    private fun getPageTiltScrollControl(window: Window): PageTiltScrollControl {
        return windowPageTiltScrollControlMap[window] ?: synchronized(windowPageTiltScrollControlMap) {
            synchronized(windowPageTiltScrollControlMap) {
                windowPageTiltScrollControlMap[window] ?: PageTiltScrollControl(mainBibleActivity)
            }.also {
                windowPageTiltScrollControlMap[window] = it
            }
        }
    }

    private val windowBibleViewMap: MutableMap<String, BibleView> = HashMap()
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
