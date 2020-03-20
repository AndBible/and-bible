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
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import net.bible.android.BibleApplication
import net.bible.android.control.page.window.Window
import net.bible.android.control.page.window.WindowControl
import net.bible.android.view.activity.DaggerMainBibleActivityComponent
import net.bible.android.view.activity.MainBibleActivityModule
import net.bible.android.view.activity.page.BibleView
import net.bible.android.view.activity.page.BibleViewFactory
import net.bible.android.view.activity.page.MainBibleActivity
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
            .mainBibleActivityModule(MainBibleActivityModule(MainBibleActivity.mainBibleActivity))
            .build()
            .inject(this)
    }

    private val windowRepository = windowControl.windowRepository

    init {
        build()
    }

    fun destroy() {
        MainBibleActivity.mainBibleActivity.unregisterForContextMenu(bibleView as View)
        removeView(bibleView)
    }

    lateinit var bibleView: BibleView
    var windowButton: WindowButtonWidget? = null

    private fun build() {
        val bibleView = bibleViewFactory.getOrCreateBibleView(window)
        this.bibleView = bibleView
        bibleView.updateBackgroundColor()
        setBackgroundColor(bibleView.backgroundColor)

        addView(bibleView, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        MainBibleActivity.mainBibleActivity.registerForContextMenu(bibleView as View)
        addWindowButton()
    }

    private fun addWindowButton() {
        val isSingleWindow = windowControl.isSingleWindow
        if(!isSingleWindow && CommonUtils.sharedPreferences.getBoolean("hide_window_buttons", false) && !window.isLinksWindow) return
        if (windowRepository.isMaximized && !window.isLinksWindow) return

        val defaultWindowActionButton =
            when {
                isSingleWindow -> createSingleWindowButton(window)
                window.isLinksWindow -> createCloseButton(window)
                else -> createWindowMenuButton(window)
            }

        if (!isSplitVertically) {
            defaultWindowActionButton.translationY = MainBibleActivity.mainBibleActivity.topOffset2
            if (windowRepository.lastVisibleWindow.id == window.id) {
                defaultWindowActionButton.translationX = -MainBibleActivity.mainBibleActivity.rightOffset1
            }
        } else {
            if (windowRepository.firstVisibleWindow.id == window.id) {
                defaultWindowActionButton.translationY =
                    if (windowControl.isSingleWindow) -MainBibleActivity.mainBibleActivity.bottomOffset2
                    else MainBibleActivity.mainBibleActivity.topOffset2
            }
            defaultWindowActionButton.translationX = -MainBibleActivity.mainBibleActivity.rightOffset1
        }

        windowButton = defaultWindowActionButton
        addView(defaultWindowActionButton,
            LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                if (isSingleWindow) Gravity.BOTTOM or Gravity.RIGHT else Gravity.TOP or Gravity.RIGHT))
    }

    private fun createSingleWindowButton(window: Window): WindowButtonWidget {
        return createTextButton("⊕",
            { v -> windowControl.addNewWindow()},
            { v -> false},
            window
        )
    }

    private fun createCloseButton(window: Window): WindowButtonWidget {
        return createTextButton("X",
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
        return WindowButtonWidget(window, windowControl, false, MainBibleActivity.mainBibleActivity).apply {
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
}
