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
@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package net.bible.sharedui.webview

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCAction
import kotlinx.cinterop.useContents
import net.bible.sharedcore.log.nsLog
import platform.Foundation.NSProcessInfo
import platform.Foundation.NSSelectorFromString
import platform.UIKit.UIGestureRecognizer
import platform.UIKit.UIGestureRecognizerDelegateProtocol
import platform.UIKit.UIGestureRecognizerStateBegan
import platform.UIKit.UIGestureRecognizerStateCancelled
import platform.UIKit.UIGestureRecognizerStateEnded
import platform.UIKit.UIGestureRecognizerStateFailed
import platform.UIKit.UIPanGestureRecognizer
import platform.UIKit.UIScrollTypeMaskContinuous
import platform.UIKit.UIScrollTypeMaskDiscrete
import platform.UIKit.UIWindow
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/**
 * Reports the phase and kind of indirect scroll gestures (trackpad, mouse wheel) to [WheelGestureSignals], which
 * Compose's `Scroll` events lack. Two pan recognisers on the window, one per `allowedScrollTypesMask`
 * (continuous = trackpad / Magic Mouse, discrete = wheel notches), with no allowed touch types: they never see a
 * finger, never cancel or delay anything and recognise alongside every other recogniser (CMP's own
 * `ScrollGestureRecognizer` on its input overlay included; a recogniser on an ancestor of the hit view gets the
 * same events). They only observe; Compose still decides which element scrolls.
 *
 * The end is posted to the next main-queue turn so that CMP's own final `Scroll` event (delivered by its recogniser
 * in the same UIKit pass, in no fixed order with ours) is handled first.
 */
internal object IosWheelGestureObserver {
    private val installed = mutableSetOf<UIWindow>()

    /** Adds the observers to [window] once. Main thread only. */
    fun installOn(window: UIWindow) {
        if (!installed.add(window)) return
        window.addGestureRecognizer(observer.recogniser(UIScrollTypeMaskContinuous, "continuousPanned:"))
        window.addGestureRecognizer(observer.recogniser(UIScrollTypeMaskDiscrete, "discretePanned:"))
        nsLog("MAC-SCROLL: gesture observer installed")
    }

    private val observer = Observer()

    private class Observer : NSObject(), UIGestureRecognizerDelegateProtocol {
        fun recogniser(mask: platform.UIKit.UIScrollTypeMask, action: String) =
            UIPanGestureRecognizer(target = this, action = NSSelectorFromString(action)).apply {
                allowedScrollTypesMask = mask
                allowedTouchTypes = emptyList<Any>()
                cancelsTouchesInView = false
                delaysTouchesBegan = false
                delaysTouchesEnded = false
                delegate = this@Observer
            }

        override fun gestureRecognizer(
            gestureRecognizer: UIGestureRecognizer,
            shouldRecognizeSimultaneouslyWithGestureRecognizer: UIGestureRecognizer,
        ): Boolean = true

        @ObjCAction
        fun continuousPanned(sender: UIPanGestureRecognizer) = panned(sender, WheelGestureKind.CONTINUOUS)

        @ObjCAction
        fun discretePanned(sender: UIPanGestureRecognizer) = panned(sender, WheelGestureKind.DISCRETE)

        private fun panned(sender: UIPanGestureRecognizer, kind: WheelGestureKind) {
            when (sender.state) {
                UIGestureRecognizerStateBegan -> WheelGestureSignals.emit(WheelGestureSignal.Began(kind))
                UIGestureRecognizerStateEnded -> {
                    // Same clock as UIEvent.timestamp, which CMP passes on as the pointer events' uptimeMillis.
                    val now = (NSProcessInfo.processInfo.systemUptime * 1000.0).toLong()
                    // UIKit's velocity is the finger's; the content offset moves the other way.
                    val (vx, vy) = sender.velocityInView(sender.view).useContents { -x to -y }
                    dispatch_async(dispatch_get_main_queue()) {
                        WheelGestureSignals.emit(WheelGestureSignal.Ended(kind, now, vx, vy))
                    }
                }
                UIGestureRecognizerStateCancelled, UIGestureRecognizerStateFailed ->
                    WheelGestureSignals.emit(WheelGestureSignal.Cancelled)
                else -> {}
            }
        }
    }
}
