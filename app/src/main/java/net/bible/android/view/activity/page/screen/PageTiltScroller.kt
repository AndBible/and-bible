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

package net.bible.android.view.activity.page.screen

import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.util.Log

import net.bible.android.control.page.PageTiltScrollControl
import net.bible.android.control.page.PageTiltScrollControl.TiltScrollInfo
import net.bible.android.view.activity.page.BibleView

import java.lang.ref.WeakReference

/** The WebView component that shows teh main bible and commentary text
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
class PageTiltScroller(
	private val webView: BibleView,
	private val pageTiltScrollControl: PageTiltScrollControl
) {

    private var scrollTriggerThread: Thread? = null
    private var isScrolling: Boolean = false
    private val scrollMsgHandler = ScrollMsgHandler(this)

    private val scrollTrigger = ScrollTrigger()

	fun destroy() {
		stopScrollThread()
	}

    /** start or stop tilt to scroll functionality
     */
    fun enableTiltScroll(enable: Boolean) {
        if (pageTiltScrollControl.enableTiltScroll(enable)) {
            if (enable) {
                recalculateViewingPosition()
                kickOffScrollThread()
            } else {
                stopScrollThread()
            }
        }
    }

    /** called when user touches screen to reset home position
     */
    fun recalculateViewingPosition() {
        pageTiltScrollControl.recalculateViewingPosition()
    }
    /**
     * Scroll screen at a certain speed
     */

    /** start scrolling handler
     */
    private fun kickOffScrollThread() {
        if (scrollTriggerThread == null) {
            scrollTrigger.enable()
            scrollTriggerThread = Thread(scrollTrigger)
            scrollTriggerThread!!.start()
        }
    }

    /** start scrolling handler
     */
    private fun stopScrollThread() {
        if (scrollTriggerThread != null) {
            scrollTrigger.stop()
            scrollTriggerThread = null
        }
    }

    private inner class ScrollTrigger : Runnable {
        private var isContinue = true

        internal fun enable() {
            isContinue = true
        }

        internal fun stop() {
            isContinue = false
        }

        override fun run() {
            Log.d(TAG, "Tilt-Scroll loop starting")
            while (isContinue) {
                try {
                    val tiltScrollInfo = pageTiltScrollControl.tiltScrollInfo

                    if (tiltScrollInfo.scrollPixels != 0) {
                        val msg = Message()
                        val b = Bundle()
                        b.putInt(SCROLL_PIXELS_KEY, tiltScrollInfo.scrollPixels)
                        b.putBoolean(FORWARD_KEY, tiltScrollInfo.forward)
                        msg.data = b
                        scrollMsgHandler.sendMessageAtFrontOfQueue(msg)
                    }

                    if (pageTiltScrollControl.isTiltScrollEnabled) {
                        val delay = (if (isScrolling) tiltScrollInfo.delayToNextScroll else TiltScrollInfo.TIME_TO_POLL_WHEN_NOT_SCROLLING).toLong()
                        Thread.sleep(delay)
                    } else {
                        isContinue = false
                    }
                } catch (e: Exception) {
                    Log.v("Error", e.toString())
                    isContinue = false
                }

            }
            Log.d(TAG, "Tilt-Scroll loop exiting")
        }
    }

    /** handle message requesting the bible view be scrolled up one pixel
     */
    private class ScrollMsgHandler internal constructor(pageTiltScroller: PageTiltScroller) : Handler() {
        // avoid potential memory leak.  See http://stackoverflow.com/questions/11407943/this-handler-class-should-be-static-or-leaks-might-occur-incominghandler
        private val pageTiltScrollerRef: WeakReference<PageTiltScroller>

        init {
            this.pageTiltScrollerRef = WeakReference(pageTiltScroller)
        }

        /** scroll the window 1 pixel up
         */
        override fun handleMessage(msg: Message) {
            val b = msg.data
            val scrollPixels = b.getInt(SCROLL_PIXELS_KEY, 1)
            val forward = b.getBoolean(FORWARD_KEY, true)

            val pageTiltScroller = pageTiltScrollerRef.get()
            if (pageTiltScroller != null) {
                pageTiltScroller.isScrolling = pageTiltScroller.webView.scroll(forward, scrollPixels)
            }
        }
    }

    companion object {

        private val FORWARD_KEY = "Forward"

        private val SCROLL_PIXELS_KEY = "ScrollPixels"

        private val TAG = "PageTiltScroller"
    }

}
