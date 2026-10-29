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
@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class, kotlin.experimental.ExperimentalObjCRefinement::class)

package net.bible.sharedui.webview

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCAction
import kotlinx.cinterop.useContents
import net.bible.sharedui.host.reading.HostReadingGestures
import platform.Foundation.NSProcessInfo
import platform.Foundation.NSSelectorFromString
import platform.UIKit.UIGestureRecognizer
import platform.UIKit.UIGestureRecognizerDelegateProtocol
import platform.UIKit.UIGestureRecognizerStateBegan
import platform.UIKit.UIGestureRecognizerStateChanged
import platform.UIKit.UIPanGestureRecognizer
import platform.UIKit.UISwipeGestureRecognizer
import platform.UIKit.UISwipeGestureRecognizerDirectionLeft
import platform.UIKit.UISwipeGestureRecognizerDirectionRight
import platform.UIKit.UITapGestureRecognizer
import platform.WebKit.WKWebView
import platform.darwin.NSObject

/**
 * Attaches the reading gestures to one pane's WKWebView and feeds [HostReadingGestures]: a two-tap recogniser, a non-cancelling tap recogniser that reports touches, left and
 * right swipe recognisers, and an observer of the scroll view's own pan recogniser for the auto-full-screen deltas.
 *
 * Nothing of WebKit is replaced (builder's call: no KVO on `contentOffset` and no scroll-delegate forwarding). The
 * recognisers are added beside WebKit's and this object, their delegate, answers "recognise simultaneously" so text
 * selection, scrolling, links and WebKit's own double-tap handling keep working. The scroll deltas come from a target added
 * to `scrollView.panGestureRecognizer`: it only runs while a finger drags, which is exactly when Android's
 * `GestureDetector.onScroll` runs (programmatic scrolls and fling deceleration produce none), and each call reads the
 * content offset change since the previous one.
 *
 * Main thread only. [detach] removes everything (the pane is being released).
 */
internal class IosReadingGestureRecognisers(
    private val windowId: String,
    private val webView: WKWebView,
    private val gestures: HostReadingGestures,
) : NSObject(), UIGestureRecognizerDelegateProtocol {
    private val doubleTap = UITapGestureRecognizer(target = this, action = NSSelectorFromString("doubleTapped:")).apply {
        numberOfTapsRequired = 2u
        delegate = this@IosReadingGestureRecognisers
    }
    /** Reports every tap to the host without consuming it (links, selection and WebKit's own taps still get the touch). */
    private val touch = UITapGestureRecognizer(target = this, action = NSSelectorFromString("touched:")).apply {
        cancelsTouchesInView = false
        delaysTouchesBegan = false
        delaysTouchesEnded = false
        delegate = this@IosReadingGestureRecognisers
    }
    private val swipeLeft = UISwipeGestureRecognizer(target = this, action = NSSelectorFromString("swipedLeft:")).apply {
        direction = UISwipeGestureRecognizerDirectionLeft
        delegate = this@IosReadingGestureRecognisers
    }
    private val swipeRight = UISwipeGestureRecognizer(target = this, action = NSSelectorFromString("swipedRight:")).apply {
        direction = UISwipeGestureRecognizerDirectionRight
        delegate = this@IosReadingGestureRecognisers
    }
    private var lastOffsetY = 0.0
    private var attached = false

    /** Adds the recognisers; a second call does nothing. */
    fun attach() {
        if (attached) return
        attached = true
        webView.addGestureRecognizer(doubleTap)
        webView.addGestureRecognizer(touch)
        webView.addGestureRecognizer(swipeLeft)
        webView.addGestureRecognizer(swipeRight)
        webView.scrollView.panGestureRecognizer.addTarget(this, NSSelectorFromString("scrollPanned:"))
    }

    /** Removes the recognisers and the scroll observer. */
    fun detach() {
        if (!attached) return
        attached = false
        webView.removeGestureRecognizer(doubleTap)
        webView.removeGestureRecognizer(touch)
        webView.removeGestureRecognizer(swipeLeft)
        webView.removeGestureRecognizer(swipeRight)
        webView.scrollView.panGestureRecognizer.removeTarget(this, NSSelectorFromString("scrollPanned:"))
    }

    override fun gestureRecognizer(
        gestureRecognizer: UIGestureRecognizer,
        shouldRecognizeSimultaneouslyWithGestureRecognizer: UIGestureRecognizer,
    ): Boolean = true

    @ObjCAction
    fun doubleTapped(sender: UIGestureRecognizer) = gestures.onDoubleTap()

    @ObjCAction
    fun touched(sender: UIGestureRecognizer) = gestures.onTouched()

    @ObjCAction
    fun swipedLeft(sender: UIGestureRecognizer) = gestures.onSwipe(windowId, towardsNext = true)

    @ObjCAction
    fun swipedRight(sender: UIGestureRecognizer) = gestures.onSwipe(windowId, towardsNext = false)

    @ObjCAction
    fun scrollPanned(sender: UIPanGestureRecognizer) {
        val now = (NSProcessInfo.processInfo.systemUptime * 1000.0).toLong()
        val y = webView.scrollView.contentOffset.useContents { y }
        when (sender.state) {
            UIGestureRecognizerStateBegan -> {
                lastOffsetY = y
                gestures.onScrollGestureBegan(now)
            }
            UIGestureRecognizerStateChanged -> {
                // Content offset grows when the finger moves up; the policy counts finger movement down as positive.
                gestures.onScroll((lastOffsetY - y).toFloat(), now)
                lastOffsetY = y
            }
            else -> {}
        }
    }
}
