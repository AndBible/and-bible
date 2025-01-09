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

import android.annotation.SuppressLint
import android.os.Build
import android.util.Log
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.view.GestureDetectorCompat
import net.bible.android.control.event.ABEventBus
import net.bible.android.control.page.window.Window
import net.bible.android.control.page.window.WindowControl
import net.bible.android.view.activity.page.BibleView
import net.bible.android.view.activity.page.BibleViewFactory
import net.bible.android.view.activity.page.MainBibleActivity
import net.bible.android.view.util.widget.WindowButtonWidget
import net.bible.service.common.CommonUtils
import javax.inject.Inject

class WindowButtonGestureListener(private val mainBibleActivity: MainBibleActivity): GestureDetector.SimpleOnGestureListener() {
    var gesturePerformed = BibleFrame.GestureType.UNSET

    override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
        e1?: return false
        var minScaledVelocity = ViewConfiguration.get(mainBibleActivity).scaledMinimumFlingVelocity
        val scaledMinimumDistance = CommonUtils.convertDipsToPx(40)
        minScaledVelocity = (minScaledVelocity * 0.66).toInt()

        Log.i("WBGListener", "onFling")
        val vertical = Math.abs(e1.y - e2.y).toDouble()
        val horizontal = Math.abs(e1.x - e2.x).toDouble()

        if (vertical > scaledMinimumDistance && Math.abs(velocityY) > minScaledVelocity) {
            gesturePerformed = if (e1.y > e2.y) {
                BibleFrame.GestureType.SWIPE_UP
            } else {
                BibleFrame.GestureType.SWIPE_DOWN
            }
            return true

        } else if (horizontal > scaledMinimumDistance && Math.abs(velocityX) > minScaledVelocity) {
            gesturePerformed = if (e1.x > e2.x) {
                BibleFrame.GestureType.SWIPE_RIGHT
            } else {
                BibleFrame.GestureType.SWIPE_LEFT
            }
            return true
        }
        return super.onFling(e1, e2, velocityX, velocityY)
    }

    override fun onLongPress(e: MotionEvent) {
        gesturePerformed = BibleFrame.GestureType.LONG_PRESS
    }

    override fun onSingleTapUp(e: MotionEvent): Boolean {
        gesturePerformed = BibleFrame.GestureType.SINGLE_TAP
        return true
    }
}

@SuppressLint("ViewConstructor")
class BibleFrame(
    val window: Window,
    private val allViews: SplitBibleArea,
    private val mainBibleActivity: MainBibleActivity,
): FrameLayout(allViews.context) {
    @Inject
    lateinit var windowControl: WindowControl
    private val bibleViewFactory: BibleViewFactory get() = mainBibleActivity.bibleViewFactory
    enum class GestureType {
        UNSET, SWIPE_UP, SWIPE_DOWN, SWIPE_LEFT, SWIPE_RIGHT, LONG_PRESS, SINGLE_TAP
    }

    init {
        CommonUtils.buildActivityComponent().inject(this)
    }

    fun updatePaddings() {
        val left = if(isLeftWindow) mainBibleActivity.leftOffset1 else 0
        val right = if(isRightWindow) mainBibleActivity.rightOffset1 else 0
        Log.i(TAG, "updating padding for $window: $left $right")
        if(left != paddingLeft || right != paddingRight) {
            setPadding(left, 0, right, 0)
            window.loadText()
        }
    }

    fun onEvent(event: MainBibleActivity.ConfigurationChanged) {
        updatePaddings()
    }

    private val isLeftWindow
        get() = mainBibleActivity.isSplitVertically || windowControl.windowRepository.firstVisibleWindow == window

    private val isRightWindow
        get() = mainBibleActivity.isSplitVertically || windowControl.windowRepository.lastVisibleWindow == window

    private val windowRepository = windowControl.windowRepository

    init {
        build()
        ABEventBus.safelyRegister(this)
    }

    fun destroy() {
        ABEventBus.unregister(this)
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

        addView(bibleView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        mainBibleActivity.registerForContextMenu(bibleView as View)
        addWindowButton()
    }

    private fun addWindowButton() {
        if (allViews.hideWindowButtons) return
        if (windowRepository.isMaximized) return

        val button = createWindowMenuButton(window)

        if (!mainBibleActivity.isSplitVertically) {
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
            LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.END))
    }


    private fun createWindowMenuButton(window: Window): WindowButtonWidget {
        val gestureListener = WindowButtonGestureListener(mainBibleActivity)
        val gestureDetector = GestureDetectorCompat(allViews.context, gestureListener)

        val text = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) "☰" else "="
        return createTextButton(text,
            { v, motionEvent ->
                gestureDetector.onTouchEvent(motionEvent)
                if (gestureListener.gesturePerformed == GestureType.SINGLE_TAP && (motionEvent.action == MotionEvent.ACTION_UP)) {allViews.showPopupMenu(window, v)}
                else if (gestureListener.gesturePerformed == GestureType.LONG_PRESS) {windowControl.minimiseWindow(window)}
                else if (gestureListener.gesturePerformed == GestureType.SWIPE_UP) {windowControl.maximiseWindow(window)}
                else if (gestureListener.gesturePerformed == GestureType.SWIPE_DOWN) {windowControl.minimiseWindow(window)}
                true },
            window
        )
    }

    private fun createTextButton(text: String,
                                 onTouchListener: ((View, MotionEvent) -> Boolean)? = null,
                                 window: Window?): WindowButtonWidget =
        WindowButtonWidget(window, windowControl, false, mainBibleActivity).apply {
            this.text = text
            setOnTouchListener(onTouchListener)
        }

    fun updateWindowButton() {
        removeView(windowButton)
        windowButton = null
        addWindowButton()
    }

    private val TAG = "BibleFrame[${window.displayId}]"

    private fun handleTouchEvent(v:View, event:MotionEvent, window: Window){
        windowControl.minimiseWindow(window)
    }
}
