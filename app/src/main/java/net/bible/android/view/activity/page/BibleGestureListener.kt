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
import net.bible.android.control.event.on
import net.bible.service.common.BibleViewSwipeMode
import net.bible.service.common.CommonUtils
import net.bible.sharedcore.reading.AutoFullscreenTracking
import net.bible.sharedcore.reading.FullscreenAction
import net.bible.sharedcore.reading.autoFullscreenAction
import net.bible.sharedcore.reading.reanchoredTracking
import net.bible.sharedcore.reading.shouldReanchor
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

    private lateinit var flingEv: MotionEvent

    /**
     * Threshold/direction/gating state for the auto-fullscreen decision in [onScroll], carried
     * between calls. Replaces the classic `scrollEv`/`lastDirection` fields (their sole purpose
     * was this decision - see the shared [net.bible.sharedcore.reading.AutoFullscreenPolicy] KDoc
     * for the full derivation against classic).
     */
    private var autoFullscreenTracking = AutoFullscreenTracking()

    /**
     * Event time of the last auto-fullscreen re-anchor point - mirrors classic `scrollEv.eventTime`
     * (`BibleGestureListener.kt:121,124,132,139,145` at `b33072833`). [gestureAnchorInitialized]
     * mirrors `!::scrollEv.isInitialized`. Used only to decide, via
     * [net.bible.sharedcore.reading.shouldReanchor], WHEN to reset [autoFullscreenTracking] to a
     * fresh accumulator before delegating to [autoFullscreenAction] in [onScroll] - restoring
     * classic's gesture-boundary + ~1s rate-limit re-anchors that the Task 6 port dropped (both
     * made fullscreen fire more eagerly than classic: two short same-direction swipes across a
     * finger-lift would stack, and a slow multi-second scroll would accumulate unbounded).
     */
    private var gestureAnchorEventTime: Long = 0L
    private var gestureAnchorInitialized = false

    init {
        minScaledVelocity = ViewConfiguration.get(mainBibleActivity).scaledMinimumFlingVelocity
        // make it easier to swipe
        minScaledVelocity = (minScaledVelocity * 0.66).toInt()
        ABEventBus.register(this) {
            on<MainBibleActivity.FullScreenEvent> { event ->
                if(!event.isFullScreen) {
                    lastFullScreenByDoubleTap = false
                }
            }
        }
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

        // The `if (!TouchOwner.isTouchOwned)` guard that used to wrap this block is gone with
        // TouchOwner itself: its only two callers, `setTouchOwner` and `releaseOwnership`, were
        // the classic split reading area's separator drag, so after the epilogue the property
        // was permanently false and this branch was taken unconditionally (spec 10.3).
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
        return false
    }

    override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
        e1 ?: return false

        // Restore classic's gesture-boundary + ~1s rate-limit re-anchors (`:121,124` at
        // `b33072833`), dropped by the Task 6 AutoFullscreenPolicy port. Reset the accumulator to
        // fresh BEFORE delegating whenever a new physical gesture starts or too much time elapsed
        // since the last anchor - without this, two short same-direction swipes across a
        // finger-lift would stack, and a slow multi-second scroll would accumulate unbounded,
        // both making fullscreen fire more eagerly than classic.
        //
        // reanchoredTracking zeroes ONLY the accumulator and PRESERVES lastDirectionUp - mirroring
        // classic re-anchoring the `scrollEv` POSITION while leaving `lastDirection` (genuine
        // persistent cross-gesture state, changed only on an observed flip) untouched. A full
        // `AutoFullscreenTracking()` reset here (fix pass 1) hardcoded `lastDirectionUp = false`,
        // which diverges from classic in the (prev=up, new=down) case - see reanchoredTracking's
        // KDoc for the full trace.
        if (shouldReanchor(gestureAnchorInitialized, e1.eventTime, e2.eventTime, gestureAnchorEventTime)) {
            autoFullscreenTracking = reanchoredTracking(autoFullscreenTracking)
            gestureAnchorEventTime = e2.eventTime
            gestureAnchorInitialized = true
        }

        // Bridge note (Task 6): GestureDetector's distanceY is the NEGATION of the shared policy's
        // "down = positive" deltaY convention - see AutoFullscreenPolicy KDoc.
        val deltaY = -distanceY
        val trackingBeforeCall = autoFullscreenTracking
        val result = autoFullscreenAction(
            deltaY = deltaY,
            isEnabled = autoFullScreen,
            isFullScreen = mainBibleActivity.fullScreen,
            lockedByDoubleTap = lastFullScreenByDoubleTap,
            thresholdPx = scaledMinimumFullScreenScrollDistance.toFloat(),
            tracking = trackingBeforeCall,
        )
        autoFullscreenTracking = result.tracking

        // Classic also re-anchors `scrollEv = e2` whenever it toggles fullscreen (threshold
        // cross, `:139`/`:145` - unconditional regardless of the double-tap-lock/pref gating) or
        // flips direction (`:132`). Both are already reproduced inside autoFullscreenAction's own
        // accumulator math (accumulated resets to 0 in either case) - but the re-anchor TIME must
        // still be tracked here so the next call's ~1s idle check in shouldReanchor restarts from
        // the same points classic used.
        val directionFlipped = (deltaY < 0) != trackingBeforeCall.lastDirectionUp
        if (result.action != FullscreenAction.None || directionFlipped) {
            gestureAnchorEventTime = e2.eventTime
        }

        when (result.action) {
            FullscreenAction.Enter -> mainBibleActivity.fullScreen = true
            FullscreenAction.Exit -> mainBibleActivity.fullScreen = false
            FullscreenAction.None -> {}
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
