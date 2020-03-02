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
package net.bible.android.view.activity.page.screen

import android.annotation.SuppressLint
import android.util.Log
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
import net.bible.android.view.activity.base.DocumentView
import net.bible.android.view.activity.mynote.MyNoteViewBuilder
import net.bible.android.view.activity.page.BibleView
import net.bible.android.view.activity.page.MainBibleActivity
import javax.inject.Inject

class WebViewsBuiltEvent
class AfterRemoveWebViewEvent

@MainBibleActivityScope
class DocumentWebViewBuilder @Inject constructor() {
    private var allBibleViewsContainer: AllBibleViewsContainer? = null

    @SuppressLint("RtlHardcoded")
    fun buildWebViews(): AllBibleViewsContainer {
        Log.d(TAG, "Layout web views")

        val topView = allBibleViewsContainer?: AllBibleViewsContainer().also {
            allBibleViewsContainer = it
        }
        topView.update()
        return topView
    }

    fun destroy() {
        allBibleViewsContainer?.destroy()
    }

    fun getBibleView(window: Window): BibleView {
        return allBibleViewsContainer!!.bibleViewFactory.getOrCreateBibleView(window)
    }

    fun clearBibleViewFactory() {
        allBibleViewsContainer!!.bibleViewFactory.clear()
    }

    companion object {
        private const val TAG = "DocumentWebViewBuilder"
    }
}

/**
 * Create Views for displaying documents
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
@MainBibleActivityScope
class DocumentViewManager @Inject constructor(
	private val mainBibleActivity: MainBibleActivity,
	private val documentWebViewBuilder: DocumentWebViewBuilder,
	private val myNoteViewBuilder: MyNoteViewBuilder,
	private val windowControl: WindowControl
) {
    private val parent: LinearLayout = mainBibleActivity.findViewById(R.id.mainBibleView)
    private var lastView: View? = null

	fun destroy() {
        ABEventBus.getDefault().unregister(this)
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

    private fun removeView() {
        parent.removeAllViews()
        ABEventBus.getDefault().post(AfterRemoveWebViewEvent())
        myNoteViewBuilder.afterRemove()
    }

    @Synchronized
    fun buildView() {
        if (myNoteViewBuilder.isMyNoteViewType) {
            removeView()
            mainBibleActivity.resetSystemUi()
            lastView = myNoteViewBuilder.addMyNoteView(parent)
        } else {
            val view = documentWebViewBuilder.buildWebViews()
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
    }

    val documentView: DocumentView
        get() = getDocumentView(windowControl.activeWindow)

    fun getDocumentView(window: Window): DocumentView {
        return if (myNoteViewBuilder.isMyNoteViewType) {
            myNoteViewBuilder.view
        } else { // a specific screen is specified to prevent content going to wrong screen if active screen is changed fast
            documentWebViewBuilder.getBibleView(window)
        }
    }

    fun clearBibleViewFactory() {
        documentWebViewBuilder.clearBibleViewFactory()
    }

    init {
		ABEventBus.getDefault().register(this)
    }
}
