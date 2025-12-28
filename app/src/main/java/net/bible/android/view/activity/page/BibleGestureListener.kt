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
import android.view.GestureDetector.SimpleOnGestureListener
import android.view.MotionEvent
import android.view.ViewConfiguration

import net.bible.android.control.event.ABEventBus
import net.bible.android.view.util.TouchOwner
import net.bible.service.common.BibleViewSwipeMode
import net.bible.service.common.CommonUtils
import kotlin.math.abs

/** Listen for side swipes to change chapter.  This listener class seems to work better that subclassing WebView.
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
class BibleGestureListener(
    private val mainBibleActivity: MainBibleActivity,
    val bibleView: BibleView
) : SimpleOnGestureListener() {
    private val scaledMinimumDistance: Int = CommonUtils.convertDipsToPx(DISTANCE_DIP)
    private val scaledMinimumFullScreenScrollDistance: Int = CommonUtils.convertDipsToPx(SCROLL_DIP)

    private var minScaledVelocity: Int = 0
    private val autoFullScreen: Boolean get() = CommonUtils.settings.getBoolean("auto_fullscreen_pref", false)
    private var lastFullScreenByDoubleTap = false

    private lateinit var scrollEv: MotionEvent
    private lateinit var flingEv: MotionEvent
    private var lastDirection = false

    init {
        minScaledVelocity = ViewConfiguration.get(mainBibleActivity).scaledMinimumFlingVelocity
        // make it easier to swipe
        minScaledVelocity = (minScaledVelocity * 0.66).toInt()
        ABEventBus.register(this)
    }

    fun destroy() {
        ABEventBus.unregister(this)
    }

    override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
        e1 ?: return false
        if (!::flingEv.isInitialized || e1.eventTime > flingEv.eventTime) {
            // New fling event
            flingEv = MotionEvent.obtain(e1)
        }
        if (e2.eventTime - flingEv.eventTime > 1000) {
            // Too slow motion
            flingEv = MotionEvent.obtain(e2)
        }

        if(flingEv.isButtonPressed(MotionEvent.BUTTON_PRIMARY)) {
            return false
        }

        // prevent interference with window separator drag - fast drags were causing a fling
        if (!TouchOwner.isTouchOwned) {
            // get distance between points of the fling
            val vertical = abs(flingEv.y - e2.y).toDouble()
            val horizontal = abs(flingEv.x - e2.x).toDouble()

            Log.i(TAG, "onFling vertical:$vertical horizontal:$horizontal VelocityX$velocityX")

            // test vertical distance, make sure it's a swipe
            if (vertical > scaledMinimumDistance) {
                return false
            } else if (horizontal > scaledMinimumDistance && Math.abs(velocityX) > minScaledVelocity) {
                // right to left swipe - sometimes velocity seems to have wrong sign so use raw positions to determine direction
                var goNext = flingEv.x > e2.x
                if(CommonUtils.isRtl)
                    goNext = !goNext

                if (goNext) {
                    when(CommonUtils.settings.bibleViewSwipeMode) {
                        BibleViewSwipeMode.CHAPTER -> mainBibleActivity.next()
                        BibleViewSwipeMode.PAGE -> bibleView.volumeDownPressed()
                        BibleViewSwipeMode.NONE -> {}
                    }
                } else {
                    when(CommonUtils.settings.bibleViewSwipeMode) {
                        BibleViewSwipeMode.CHAPTER -> mainBibleActivity.previous()
                        BibleViewSwipeMode.PAGE -> bibleView.volumeUpPressed()
                        BibleViewSwipeMode.NONE -> {}
                    }
                }
                return true
            }
        }
        return false
    }

	fun onEvent(event: MainBibleActivity.FullScreenEvent) {
		if(!event.isFullScreen) {
			lastFullScreenByDoubleTap = false
		}
	}

    override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
        e1 ?: return false
        if (!::scrollEv.isInitialized  || e1.eventTime > scrollEv.eventTime) {
            // New scroll event
            scrollEv = MotionEvent.obtain(e1)
        }
        if (e2.eventTime - scrollEv.eventTime > 1000) {
            // Too slow motion
            scrollEv = MotionEvent.obtain(e2)
        }

        val direction = distanceY > 0
        if (lastDirection != direction) {
            scrollEv = MotionEvent.obtain(e2)
            lastDirection = direction
        }

        val dist = e2.y - scrollEv.y
        if (!mainBibleActivity.fullScreen && dist < -scaledMinimumFullScreenScrollDistance) {
            if (!lastFullScreenByDoubleTap && autoFullScreen) {
                mainBibleActivity.fullScreen = true
            }
            scrollEv = MotionEvent.obtain(e2)
        }
        if (mainBibleActivity.fullScreen && dist > scaledMinimumFullScreenScrollDistance) {
            if (!lastFullScreenByDoubleTap && autoFullScreen) {
                mainBibleActivity.fullScreen = false
            }
            scrollEv = MotionEvent.obtain(e2)
        }
        return false
    }

    override fun onSingleTapUp(e: MotionEvent): Boolean {
        ABEventBus.post(BibleView.BibleViewTouched(onlyTouch = true))
        return super.onSingleTapUp(e)
    }

    private val doubleTapToFullscreen get() = CommonUtils.settings.getBoolean("double_tap_to_fullscreen", true)

    override fun onDoubleTap(e: MotionEvent): Boolean {
        if (mainBibleActivity.fullScreen) {
            mainBibleActivity.fullScreen = false
        } else if(!mainBibleActivity.fullScreen && doubleTapToFullscreen){
            mainBibleActivity.fullScreen = true
			lastFullScreenByDoubleTap = true
        }
        return true
    }

    companion object {

        // measurements in dips for density independence
        // TODO: final int swipeMinDistance = vc.getScaledTouchSlop();
        // TODO: and other suggestions in http://stackoverflow.com/questions/937313/android-basic-gesture-detection
        private val DISTANCE_DIP = 40
        private val SCROLL_DIP = 56 // should be at least toolbar height

        private val TAG = "BibleGestureListener"
    }
}
