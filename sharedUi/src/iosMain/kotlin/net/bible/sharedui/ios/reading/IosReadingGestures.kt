/*
 * Copyright (c) 2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package net.bible.sharedui.ios.reading

import net.bible.android.view.activity.page.SwipeAction
import net.bible.android.view.activity.page.swipeAction
import net.bible.service.common.BibleViewSwipeMode
import net.bible.sharedcore.platform.AppSettings
import net.bible.sharedcore.reading.AutoFullscreenTracking
import net.bible.sharedcore.reading.FullscreenAction
import net.bible.sharedcore.reading.autoFullscreenAction
import net.bible.sharedcore.reading.reanchoredTracking
import net.bible.sharedcore.reading.shouldReanchor
import net.bible.sharedui.reading.host.ReadingFullScreen

/**
 * The reading view's gesture rules on iOS: double tap, auto full screen and swipe mode. The decisions are Android's
 * `BibleGestureListener` (`onDoubleTap`, `onScroll`, `onFling`) through the same common functions ([autoFullscreenAction],
 * [shouldReanchor], [reanchoredTracking], [swipeAction]); only the touch plumbing differs. No UIKit in here, so the rules
 * are testable; `IosReadingGestureRecognisers` feeds it from the pane's recognisers and scroll view.
 *
 * One instance serves all panes (full screen is one state for the whole reading view). Main thread only.
 *
 * @param setFullScreen how a full-screen change is applied. The default only changes [fullScreen]; the host passes its own
 *   function so the status bar follows too (builder's call: a trailing parameter with a default keeps the planned
 *   constructor shape).
 * @param onSwipe receives the window the swipe was on and the decided action (never [SwipeAction.None]).
 */
@kotlin.native.HiddenFromObjC
class IosReadingGestures(
    private val fullScreen: ReadingFullScreen,
    private val settings: AppSettings,
    private val isRtl: () -> Boolean,
    private val onSwipe: (windowId: String, SwipeAction) -> Unit,
    private val setFullScreen: (value: Boolean, byDoubleTap: Boolean) -> Unit = { value, byDoubleTap -> fullScreen.set(value, byDoubleTap) },
) {
    private var tracking = AutoFullscreenTracking()
    private var anchorTime = 0L
    private var anchorInitialized = false
    private var gestureStartTime = 0L

    private val doubleTapToFullscreen: Boolean get() = settings.getBoolean(DOUBLE_TAP_KEY, true)
    private val autoFullScreen: Boolean get() = settings.getBoolean(AUTO_FULLSCREEN_KEY, false)

    /**
     * A double tap on a pane: leaves full screen when in it, otherwise enters it (locked, so scrolling does not undo it)
     * unless the "double tap to full screen" setting is off. Same as Android's `onDoubleTap`.
     */
    fun onDoubleTap() {
        if (fullScreen.state.value) {
            setFullScreen(false, false)
        } else if (doubleTapToFullscreen) {
            setFullScreen(true, true)
        }
    }

    /** A finger went down on the pane's scroll view: the next [onScroll] calls belong to a new gesture (Android's `e1`). */
    fun onScrollGestureBegan(timestampMillis: Long) {
        gestureStartTime = timestampMillis
    }

    /**
     * The finger moved by [deltaY] points on the pane's scroll view; [timestampMillis] is any monotonic clock in
     * milliseconds. Convention of [autoFullscreenAction]: moving down is positive, so a reader scrolling forward (finger
     * up, content offset growing) passes a negative value. Mirrors Android's `onScroll` including the re-anchor
     * bookkeeping; with auto full screen off it only tracks, never changes full screen.
     */
    fun onScroll(deltaY: Float, timestampMillis: Long) {
        if (shouldReanchor(anchorInitialized, gestureStartTime, timestampMillis, anchorTime)) {
            tracking = reanchoredTracking(tracking)
            anchorTime = timestampMillis
            anchorInitialized = true
        }
        val before = tracking
        val result = autoFullscreenAction(
            deltaY = deltaY,
            isEnabled = autoFullScreen,
            isFullScreen = fullScreen.state.value,
            lockedByDoubleTap = fullScreen.lockedByDoubleTap,
            thresholdPx = SCROLL_THRESHOLD_POINTS,
            tracking = before,
        )
        tracking = result.tracking
        val directionFlipped = (deltaY < 0) != before.lastDirectionUp
        if (result.action != FullscreenAction.None || directionFlipped) anchorTime = timestampMillis
        when (result.action) {
            FullscreenAction.Enter -> setFullScreen(true, false)
            FullscreenAction.Exit -> setFullScreen(false, false)
            FullscreenAction.None -> {}
        }
    }

    /**
     * A horizontal swipe on [windowId]'s pane. [towardsNext] is the swipe that reads on: the finger moving right to left.
     * The setting is read now; an unknown stored value counts as chapter mode (Android would throw).
     */
    fun onSwipe(windowId: String, towardsNext: Boolean) {
        val mode = runCatching { BibleViewSwipeMode.valueOf(settings.getString(SWIPE_MODE_KEY, "CHAPTER") ?: "CHAPTER") }
            .getOrDefault(BibleViewSwipeMode.CHAPTER)
        val action = swipeAction(towardsNext, isRtl(), mode)
        if (action != SwipeAction.None) onSwipe(windowId, action)
    }

    internal companion object {
        const val DOUBLE_TAP_KEY = "double_tap_to_fullscreen"
        const val AUTO_FULLSCREEN_KEY = "auto_fullscreen_pref"
        const val SWIPE_MODE_KEY = "bible_view_swipe_mode"

        /** Android's `SCROLL_DIP` (56 dp, "at least toolbar height"); a point is the iOS dp. */
        const val SCROLL_THRESHOLD_POINTS = 56f
    }
}
