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
import android.content.Context
import android.content.res.Resources
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout

import net.bible.android.BibleApplication
import net.bible.android.activity.R
import net.bible.android.control.event.ABEventBus
import net.bible.android.control.event.window.CurrentWindowChangedEvent
import net.bible.android.control.page.window.WindowControl
import net.bible.android.view.activity.page.BibleView
import net.bible.android.view.util.TouchDelegateView
import net.bible.android.view.util.TouchOwner

/**
 * @author Martin Denham [mjdenham at gmail dot com]
 */
@SuppressLint("ViewConstructor")
class Separator(
    context: Context,
    private val separatorWidth: Int,
    private val parentLayout: View,
    val frame1: BibleFrame,
    val frame2: BibleFrame,
    internal var numWindows: Int,
    private val isSplitVertically: Boolean,
    private val windowControl: WindowControl
) : View(context)
{
    private val activeWindow get() = windowControl.windowRepository.activeWindow

    private val window1 get() = frame1.window
    private val window2 get() = frame2.window

    // offset absolute points from top of layout to enable correct calculation of screen weights in layout
    private var parentStartRawPx: Float = 0.toFloat()

    // the offset of the touch from the centre of the separator - to prevent initial jerk of separator to touch point
    private var startTouchPx: Int = 0
    private var startWeight1 = 1.0f
    private var startWeight2 = 1.0f

    private var lastOffsetFromEdgePx: Int = 0

    // try to prevent swamping of ui thread during splitter drag
    private var lastTouchMoveEvent: Long = 0

    lateinit var view1LayoutParams: LinearLayout.LayoutParams
    lateinit var view2LayoutParams: LinearLayout.LayoutParams

    val touchDelegateView1 = TouchDelegateView(context, this)
    val touchDelegateView2 = TouchDelegateView(context, this)

    private val aveScreenSize: Int
        get() = parentDimensionPx / numWindows

    private val parentDimensionPx: Int
        get() {
            val displayMetrics = Resources.getSystem().displayMetrics
            return if (isSplitVertically) displayMetrics.heightPixels else displayMetrics.widthPixels
        }

	private val res = BibleApplication.application.resources

    private val isActive get() = activeWindow.id == window1.id || activeWindow.id == window2.id

    private val separatorResource get () = if (isActive) R.drawable.separator_active else R.drawable.separator
	private val dragResource = R.drawable.separator_drag

    private fun updateBackground() {
        setBackgroundResource(separatorResource)
    }

    override fun onDetachedFromWindow() {
        ABEventBus.unregister(this)
        super.onDetachedFromWindow()
    }

    override fun onAttachedToWindow() {
        updateBackground()
        ABEventBus.register(this)
        super.onAttachedToWindow()
    }

    fun onEvent(event: CurrentWindowChangedEvent) {
        updateBackground()
    }

    fun onEvent(event: BibleView.BibleViewTouched) {
        if(event.onlyTouch) {
            release()
        }
    }

    fun release() {
        if(windowControl.isSeparatorMoving) {
            setBackgroundResource(separatorResource)
            window1.weight = view1LayoutParams.weight
            window2.weight = view2LayoutParams.weight
            windowControl.isSeparatorMoving = false
            TouchOwner.releaseOwnership()
        }
    }

    /**
     * Must use rawY below because this view is moving and getY would give the position relative to a moving component.
     */
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action and MotionEvent.ACTION_MASK) {
            MotionEvent.ACTION_DOWN -> {
                Log.i(TAG, " y:" + event.rawY)
                TouchOwner.setTouchOwner(this)
                windowControl.isSeparatorMoving = true
                setBackgroundResource(dragResource)

                val rawParentLocation = IntArray(2)
                parentLayout.getLocationOnScreen(rawParentLocation)
                parentStartRawPx = (if (isSplitVertically) rawParentLocation[1] else rawParentLocation[0]).toFloat()

                startTouchPx = if (isSplitVertically) event.rawY.toInt() else event.rawX.toInt()
                startWeight1 = view1LayoutParams.weight
                startWeight2 = view2LayoutParams.weight
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                Log.i(TAG, "Up x:" + event.x + " y:" + event.y)
                release()
            }
            MotionEvent.ACTION_MOVE -> if (System.currentTimeMillis() > lastTouchMoveEvent + DRAG_TOUCH_MOVE_FREQUENCY_MILLIS) {
                Log.i(TAG, "Touch move accepted")
                val parentDimensionPx = parentDimensionPx

                // calculate y offset in pixels from top of parent layout
                var offsetFromEdgePx = if (isSplitVertically) event.rawY else event.rawX

                // prevent going irretrievably off bottom or right edge
                offsetFromEdgePx = Math.min(offsetFromEdgePx, (parentDimensionPx - separatorWidth).toFloat())

                // if position has moved at least one px then redraw separator
                if (offsetFromEdgePx.toInt() != lastOffsetFromEdgePx) {
                    val changePx = offsetFromEdgePx.toInt() - startTouchPx
                    val aveScreenSize = aveScreenSize.toFloat()
                    val variationPercent = changePx / aveScreenSize

                    // change the weights of both bible views to effectively move the separator
                    view1LayoutParams.weight = startWeight1 + variationPercent
                    view2LayoutParams.weight = startWeight2 - variationPercent
                    parentLayout.requestLayout()
                    lastOffsetFromEdgePx = offsetFromEdgePx.toInt()
                }
                lastTouchMoveEvent = System.currentTimeMillis()
                Log.i(TAG, "Touch move finished")
            }
        }

        return true
    }

    companion object {
        private const val DRAG_TOUCH_MOVE_FREQUENCY_MILLIS = 0

        private val TAG = "Separator"
    }
}
