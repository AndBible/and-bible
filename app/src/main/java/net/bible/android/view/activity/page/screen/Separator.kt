/*
 * Copyright (c) 2018 Martin Denham, Tuomas Airaksinen and the And Bible contributors.
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

import android.content.Context
import android.content.res.Resources
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout

import net.bible.android.BibleApplication
import net.bible.android.activity.R
import net.bible.android.control.page.window.Window
import net.bible.android.control.page.window.WindowControl
import net.bible.android.view.util.TouchDelegateView
import net.bible.android.view.util.TouchOwner

/**
 * @author Martin Denham [mjdenham at gmail dot com]
 */
class Separator(
		context: Context,
		private val SEPARATOR_WIDTH: Int,
		private val parentLayout: View,
		private val window1: Window,
		private val window2: Window,
		private val numWindows: Int,
		private val isPortrait: Boolean,
		private val windowControl: WindowControl
) : View(context) {

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

    val touchDelegateView1: TouchDelegateView
    val touchDelegateView2: TouchDelegateView
    private val SEPARATOR_COLOUR: Int
    private val SEPARATOR_DRAG_COLOUR: Int

    private val touchOwner = TouchOwner.getInstance()

    private val aveScreenSize: Int
        get() = parentDimensionPx / numWindows

    private val parentDimensionPx: Int
        get() = if (isPortrait) parentLayout.height else parentLayout.width

    private val startingOffsetY: Int
        get() = +parentStartRawPx.toInt() + (top + bottom) / 2
    private val startingOffsetX: Int
        get() = +parentStartRawPx.toInt() + (left + right) / 2

    init {

        val res = BibleApplication.application.resources
        SEPARATOR_COLOUR = res.getColor(R.color.window_separator_colour)
        SEPARATOR_DRAG_COLOUR = res.getColor(R.color.window_separator_drag_colour)
        setBackgroundColor(SEPARATOR_COLOUR)

        touchDelegateView1 = TouchDelegateView(context, this)
        touchDelegateView2 = TouchDelegateView(context, this)
    }

    /**
     * Must use rawY below because this view is moving and getY would give the position relative to a moving component.
     */
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action and MotionEvent.ACTION_MASK) {
            MotionEvent.ACTION_DOWN -> {
                Log.d(TAG, " y:" + event.rawY)
                touchOwner.setTouchOwner(this)
                windowControl.setSeparatorMoving(true)
                setBackgroundColor(SEPARATOR_DRAG_COLOUR)

                val rawParentLocation = IntArray(2)
                parentLayout.getLocationOnScreen(rawParentLocation)
                parentStartRawPx = (if (isPortrait) rawParentLocation[1] else rawParentLocation[0]).toFloat()

                startTouchPx = if (isPortrait) event.rawY.toInt() else event.rawX.toInt()
                startWeight1 = view1LayoutParams.weight //window1.getWeight();
                startWeight2 = view2LayoutParams.weight //window2.getWeight();
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                Log.d(TAG, "Up x:" + event.x + " y:" + event.y)
                setBackgroundColor(SEPARATOR_COLOUR)
                window1.windowLayout.weight = view1LayoutParams.weight
                window2.windowLayout.weight = view2LayoutParams.weight
                windowControl.setSeparatorMoving(false)
                touchOwner.releaseOwnership(this)
            }
            MotionEvent.ACTION_MOVE -> if (System.currentTimeMillis() > lastTouchMoveEvent + DRAG_TOUCH_MOVE_FREQUENCY_MILLIS) {
                Log.d(TAG, "Touch move accepted")
                val parentDimensionPx = parentDimensionPx

                // calculate y offset in pixels from top of parent layout
                var offsetFromEdgePx = if (isPortrait) event.rawY else event.rawX

                // prevent going irretrievably off bottom or right edge
                offsetFromEdgePx = Math.min(offsetFromEdgePx, (parentDimensionPx - SEPARATOR_WIDTH).toFloat())

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
                Log.d(TAG, "Touch move finished")
            }
        }

        return true
    }

    companion object {
        private val DRAG_TOUCH_MOVE_FREQUENCY_MILLIS = 0

        private val TAG = "Separator"
    }
}
