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

package net.bible.android.view.activity.page

import android.content.SharedPreferences
import android.util.Log
import android.view.GestureDetector.SimpleOnGestureListener
import android.view.MotionEvent
import android.view.ViewConfiguration

import net.bible.android.control.event.ABEventBus
import net.bible.android.control.event.window.CurrentWindowChangedEvent
import net.bible.android.view.util.TouchOwner
import net.bible.service.common.CommonUtils

/** Listen for side swipes to change chapter.  This listener class seems to work better that subclassing WebView.
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
class BibleGestureListener(private val mainBibleActivity: MainBibleActivity) : SimpleOnGestureListener() {
    private val scaledMinimumDistance: Int
    private val scaledMinimumFullScreenScrollDistance: Int

    private var minScaledVelocity: Int = 0
    private val autoFullScreen: Boolean get() = CommonUtils.sharedPreferences.getBoolean("auto_fullscreen_pref", false)
    private var lastFullScreenByDoubleTap = false

    private var disableSingleTapOnce = false

    private var verseSelectionMode = false

    private lateinit var scrollEv: MotionEvent
    private lateinit var flingEv: MotionEvent
    private var lastDirection = false

    fun setDisableSingleTapOnce(disableSingleTapOnce: Boolean) {
        this.disableSingleTapOnce = disableSingleTapOnce
    }

    fun setVerseSelectionMode(verseSelectionMode: Boolean) {
        this.verseSelectionMode = verseSelectionMode
        if (!verseSelectionMode) {
            disableSingleTapOnce = true
        }
    }

    init {
        scaledMinimumDistance = CommonUtils.convertDipsToPx(DISTANCE_DIP)
        scaledMinimumFullScreenScrollDistance = CommonUtils.convertDipsToPx(SCROLL_DIP)
        minScaledVelocity = ViewConfiguration.get(mainBibleActivity).scaledMinimumFlingVelocity
        // make it easier to swipe
        minScaledVelocity = (minScaledVelocity * 0.66).toInt()
        ABEventBus.getDefault().register(this)
    }

    override fun onFling(e1: MotionEvent, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
        if (!::flingEv.isInitialized || e1.eventTime > flingEv.eventTime) {
            // New fling event
            flingEv = MotionEvent.obtain(e1)
        }
        if (e2.eventTime - flingEv.eventTime > 1000) {
            // Too slow motion
            flingEv = MotionEvent.obtain(e2)
        }

        // prevent interference with window separator drag - fast drags were causing a fling
        if (!TouchOwner.getInstance().isTouchOwned) {
            // get distance between points of the fling
            val vertical = Math.abs(flingEv.y - e2.y).toDouble()
            val horizontal = Math.abs(flingEv.x - e2.x).toDouble()

            Log.d(TAG, "onFling vertical:$vertical horizontal:$horizontal VelocityX$velocityX")

            // test vertical distance, make sure it's a swipe
            if (vertical > scaledMinimumDistance) {
                return false
            } else if (horizontal > scaledMinimumDistance && Math.abs(velocityX) > minScaledVelocity) {
                // right to left swipe - sometimes velocity seems to have wrong sign so use raw positions to determine direction
                if (flingEv.x > e2.x) {
                    mainBibleActivity.next()
                } else {
                    // left to right swipe
                    mainBibleActivity.previous()
                }
                return true
            }
        }
        return false
    }

    fun onEvent(event: CurrentWindowChangedEvent) {
        disableSingleTapOnce = true
    }

	fun onEvent(event: MainBibleActivity.FullScreenEvent) {
		if(!event.isFullScreen) {
			lastFullScreenByDoubleTap = false
		}
	}

    override fun onScroll(e1: MotionEvent, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
        if (!::scrollEv.isInitialized  || e1.eventTime > scrollEv.eventTime) {
            // New scroll event
            scrollEv = MotionEvent.obtain(e1)
        }
		ABEventBus.getDefault().post(BibleView.BibleViewTouched(onlyTouch = true))
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


    override fun onSingleTapUp(e: MotionEvent?): Boolean {
        ABEventBus.getDefault().post(BibleView.BibleViewTouched(onlyTouch = true))
        return super.onSingleTapUp(e)
    }

    override fun onDoubleTap(e: MotionEvent): Boolean {
        if (mainBibleActivity.fullScreen) {
            mainBibleActivity.fullScreen = false
        } else if(!mainBibleActivity.fullScreen){
            mainBibleActivity.fullScreen = true
			lastFullScreenByDoubleTap = true
        }
        return true
    }

    override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
        if (verseSelectionMode) {
            return false
        }
        if (disableSingleTapOnce) {
            disableSingleTapOnce = false
            return false
        }

        if (mainBibleActivity.fullScreen) {
            mainBibleActivity.fullScreen = false
            return true
        }
        return false
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
