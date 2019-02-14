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

package net.bible.android.view.activity.page

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
    private var autoFullScreen: Boolean = false
    private var lastFullScreenByDoubleTap = false

    private var disableSingleTapOnce = false

    private var verseSelectionMode = false

    private var ev: MotionEvent? = null
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
        autoFullScreen = CommonUtils.getSharedPreferences().getBoolean("auto_fullscreen_pref", true)
    }

    override fun onFling(e1: MotionEvent?, e2: MotionEvent?, velocityX: Float, velocityY: Float): Boolean {
        // prevent interference with window separator drag - fast drags were causing a fling
        if (!TouchOwner.getInstance().isTouchOwned) {
            // avoid NPE on Samsung devices
            if (e1 != null && e2 != null) {
                // get distance between points of the fling
                val vertical = Math.abs(e1.y - e2.y).toDouble()
                val horizontal = Math.abs(e1.x - e2.x).toDouble()

                Log.d(TAG, "onFling vertical:$vertical horizontal:$horizontal VelocityX$velocityX")

                // test vertical distance, make sure it's a swipe
                if (vertical > scaledMinimumDistance) {
                    return false
                } else if (horizontal > scaledMinimumDistance && Math.abs(velocityX) > minScaledVelocity) {
                    // right to left swipe - sometimes velocity seems to have wrong sign so use raw positions to determine direction
                    if (e1.x > e2.x) {
                        mainBibleActivity.next()
                    } else {
                        mainBibleActivity.previous()
                    }// left to right swipe
                    return true
                }// test horizontal distance and velocity
            }
        }
        return false
    }

    fun onEvent(event: MainBibleActivity.AutoFullScreenChanged) {
        autoFullScreen = event.newValue
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
        if (ev == null || e1.eventTime > ev!!.eventTime) {
            // New scroll event
            ev = MotionEvent.obtain(e1)
        }
		ABEventBus.getDefault().post(BibleView.BibleViewTouched(onlyTouch = true))
        if (e2.eventTime - ev!!.eventTime > 1000) {
            // Too slow motion
            ev = MotionEvent.obtain(e2)
        }

        val direction = distanceY > 0
        if (lastDirection != direction) {
            ev = MotionEvent.obtain(e2)
            lastDirection = direction
        }

        val dist = e2.y - ev!!.y
        if (!mainBibleActivity.fullScreen && dist < -scaledMinimumFullScreenScrollDistance) {
            if (!lastFullScreenByDoubleTap && autoFullScreen) {
                mainBibleActivity.fullScreen = true
            }
            ev = MotionEvent.obtain(e2)
        }
        if (mainBibleActivity.fullScreen && dist > scaledMinimumFullScreenScrollDistance) {
            if (!lastFullScreenByDoubleTap && autoFullScreen) {
                mainBibleActivity.fullScreen = false
            }
            ev = MotionEvent.obtain(e2)
        }
        return false
    }


    override fun onDoubleTap(e: MotionEvent): Boolean {
        if (mainBibleActivity.fullScreen && lastFullScreenByDoubleTap) {
            mainBibleActivity.fullScreen = false

        } else {
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
