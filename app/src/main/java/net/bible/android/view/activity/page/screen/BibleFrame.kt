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
import android.os.Build
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import net.bible.android.BibleApplication
import net.bible.android.control.event.ABEventBus
import net.bible.android.control.page.window.Window
import net.bible.android.control.page.window.WindowControl
import net.bible.android.view.activity.DaggerMainBibleActivityComponent
import net.bible.android.view.activity.MainBibleActivityModule
import net.bible.android.view.activity.page.BibleView
import net.bible.android.view.activity.page.BibleViewFactory
import net.bible.android.view.activity.page.MainBibleActivity
import net.bible.android.view.activity.page.MainBibleActivity.Companion.mainBibleActivity
import net.bible.android.view.util.widget.WindowButtonWidget
import net.bible.service.common.CommonUtils
import javax.inject.Inject


@SuppressLint("ViewConstructor")
class BibleFrame(
    val window: Window,
    private val allViews: SplitBibleArea
): FrameLayout(allViews.context) {
    @Inject
    lateinit var windowControl: WindowControl
    private val bibleViewFactory: BibleViewFactory = allViews.bibleViewFactory

    init {
        DaggerMainBibleActivityComponent.builder()
            .applicationComponent(BibleApplication.application.applicationComponent)
            .mainBibleActivityModule(MainBibleActivityModule(mainBibleActivity))
            .build()
            .inject(this)
    }

    fun updatePaddings() {
        val left = if(isLeftWindow) mainBibleActivity.leftOffset1 else 0
        val right = if(isRightWindow) mainBibleActivity.rightOffset1 else 0
        Log.d(TAG, "updating padding for $window: $left $right")
        if(left != paddingLeft || right != paddingRight) {
            setPadding(left, 0, right, 0)
            window.updateText()
        }
    }

    fun onEvent(event: MainBibleActivity.ConfigurationChanged) {
        updatePaddings()
    }

    private val isLeftWindow
        get() = CommonUtils.isSplitVertically || windowControl.windowRepository.firstVisibleWindow == window

    private val isRightWindow
        get() = CommonUtils.isSplitVertically || windowControl.windowRepository.lastVisibleWindow == window

    private val windowRepository = windowControl.windowRepository

    init {
        build()
        ABEventBus.getDefault().safelyRegister(this)
    }

    fun destroy() {
        ABEventBus.getDefault().unregister(this)
        mainBibleActivity.unregisterForContextMenu(bibleView as View)
        removeView(bibleView)
    }

    lateinit var bibleView: BibleView
    var windowButton: View? = null

    private fun build() {
        val bibleView = bibleViewFactory.getOrCreateBibleView(window)
        this.bibleView = bibleView
        bibleView.updateBackgroundColor()
        setBackgroundColor(bibleView.backgroundColor)

        addView(bibleView, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        mainBibleActivity.registerForContextMenu(bibleView as View)
        addWindowButton()
    }

    private fun addWindowButton() {
        val isSingleWindow = windowControl.isSingleWindow
        if (allViews.hideWindowButtons) return
        if (windowRepository.isMaximized) return

        val button =
            when {
                isSingleWindow -> return
                window.isLinksWindow -> createCloseButton(window)
                else -> createWindowMenuButton(window)
            }

        if (!isSplitVertically) {
            button.translationY = mainBibleActivity.topOffset2.toFloat()
        } else {
            if (windowRepository.firstVisibleWindow.id == window.id) {
                button.translationY =
                    if (windowControl.isSingleWindow) -mainBibleActivity.bottomOffset2.toFloat()
                    else mainBibleActivity.topOffset2.toFloat()
            }
        }

        windowButton = button
        addView(button,
            LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                if (isSingleWindow) Gravity.BOTTOM or Gravity.RIGHT else Gravity.TOP or Gravity.RIGHT))
    }

    private fun createCloseButton(window: Window): WindowButtonWidget {
        val text = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) "☰" else "="
        return createTextButton(text,
            { v -> allViews.showPopupWindow(window, v)},
            { v -> windowControl.closeWindow(window); true},
            window
        )
    }

    private fun createWindowMenuButton(window: Window): WindowButtonWidget {
        val text = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) "☰" else "="
        return createTextButton(text,
            { v -> allViews.showPopupWindow(window, v) },
            { v -> windowControl.minimiseWindow(window); true },
            window
        )
    }

    private fun createTextButton(text: String, onClickListener: (View) -> Unit,
                                 onLongClickListener: ((View) -> Boolean)? = null,
                                 window: Window?): WindowButtonWidget {
        return WindowButtonWidget(window, windowControl, false, mainBibleActivity).apply {
            this.text = text
            setOnClickListener(onClickListener)
            setOnLongClickListener(onLongClickListener)
        }
    }

    fun updateWindowButton() {
        removeView(windowButton)
        windowButton = null
        addWindowButton()
    }

    private val TAG = "BibleFrame[${window.id}]"
}
