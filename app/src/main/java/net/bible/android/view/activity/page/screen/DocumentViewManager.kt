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
package net.bible.android.view.activity.page.screen

import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import net.bible.android.activity.R
import net.bible.android.control.event.ABEventBus
import net.bible.android.control.event.passage.PassageChangeStartedEvent
import net.bible.android.control.event.window.NumberOfWindowsChangedEvent
import net.bible.android.control.page.window.Window
import net.bible.android.control.page.window.WindowControl
import net.bible.android.view.activity.MainBibleActivityScope
import net.bible.android.view.activity.page.BibleView
import net.bible.android.view.activity.page.MainBibleActivity
import javax.inject.Inject

class WebViewsBuiltEvent
class AfterRemoveWebViewEvent

/**
 * Create Views for displaying documents
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
@MainBibleActivityScope
class DocumentViewManager @Inject constructor(
	val mainBibleActivity: MainBibleActivity,
	private val windowControl: WindowControl
) {
    private val parent: LinearLayout = mainBibleActivity.findViewById(R.id.mainBibleView)
    private var lastView: View? = null
    var splitBibleArea: SplitBibleArea? = null

	fun destroy() {
        removeView()
        ABEventBus.getDefault().unregister(this)
        splitBibleArea?.destroy()
    }

    fun onEvent(event: NumberOfWindowsChangedEvent) {
        buildView()
    }

	/**
	 * called just before starting work to change the current passage
	 */
	fun onEventMainThread(event: PassageChangeStartedEvent) {
		buildView()
	}

    fun removeView() {
        parent.removeAllViews()
        lastView = null
        ABEventBus.getDefault().post(AfterRemoveWebViewEvent())
    }

    private fun buildWebViews(forceUpdate: Boolean): SplitBibleArea {
        val topView = splitBibleArea?: SplitBibleArea().also {
            splitBibleArea = it
        }
        topView.update(forceUpdate)
        return topView
    }

    @Synchronized
    fun buildView(forceUpdate: Boolean = false) {
        val view = buildWebViews(forceUpdate)
        if(lastView != view) {
            removeView()
            lastView = view
            parent.addView(view,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT)
            )
        }
        ABEventBus.getDefault().post(WebViewsBuiltEvent())
    }

    val documentView: BibleView get() = getDocumentView(windowControl.activeWindow)

    private fun getDocumentView(window: Window): BibleView {
        // a specific screen is specified to prevent content going to wrong screen if active screen is changed fast
        return mainBibleActivity.bibleViewFactory.getOrCreateBibleView(window)
    }

    init {
		ABEventBus.getDefault().register(this)
    }
}
