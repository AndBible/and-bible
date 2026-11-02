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
@file:OptIn(ExperimentalForeignApi::class)

package net.bible.sharedui.webview

import androidx.compose.ui.Modifier
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import net.bible.sharedcore.log.nsLog
import platform.CoreGraphics.CGPointMake
import platform.Foundation.NSNumber
import platform.Foundation.NSProcessInfo
import platform.UIKit.UIScrollView
import platform.WebKit.WKWebView

/**
 * Lets the mouse wheel / trackpad scroll a pane's [webView]: Compose keeps those events for itself (see
 * [wheelScrolls]), so WebKit never sees a wheel. Each event first goes to the page ([INNER_SCROLL_JS]): the nearest
 * scrollable element under the pointer that can still move that way (a bookmark modal, the note editor) takes it.
 * Otherwise the page itself moves: `scrollView.contentOffset`, after which WebKit updates the page and fires DOM
 * `scroll` events as for any scroll. No momentum: movement stops when the pan ends.
 */
internal fun Modifier.wheelScrollsWebView(windowId: String, webView: WKWebView): Modifier =
    wheelScrolls(WebViewWheelTarget(windowId, webView))

/** Value equality (window id + the same WKWebView instance) so recomposition keeps the pointer-input coroutine. */
private data class WebViewWheelTarget(val windowId: String, val webView: WKWebView) : WheelScrollTarget {
    override fun scrollBy(xPoints: Double, yPoints: Double, dxPoints: Double, dyPoints: Double): Boolean {
        val zoom = webView.scrollView.zoomScale.takeIf { it > 0.0 } ?: 1.0
        val script = "$INNER_SCROLL_JS(${xPoints / zoom},${yPoints / zoom},${dxPoints / zoom},${dyPoints / zoom})"
        webView.evaluateJavaScript(script) { result, _ ->
            val inner = (result as? NSNumber)?.boolValue ?: false
            if (!inner) scrollPage(webView.scrollView, dxPoints, dyPoints)
            logThrottled(inner, dyPoints)
        }
        return true
    }

    private fun logThrottled(inner: Boolean, dyPoints: Double) {
        // Temporary Mac diagnostic for the maintainer's check: at most one line per second per pane.
        val now = (NSProcessInfo.processInfo.systemUptime * 1000.0).toLong()
        if (now - (lastLogMs[windowId] ?: 0L) < 1000) return
        lastLogMs[windowId] = now
        val offsetY = webView.scrollView.contentOffset.useContents { y }
        nsLog("MAC-SCROLL pane[$windowId]: dy=$dyPoints pt ${if (inner) "inner element" else "page"} offsetY=$offsetY")
    }
}

private val lastLogMs = mutableMapOf<String, Long>()

private fun scrollPage(scrollView: UIScrollView, dxPoints: Double, dyPoints: Double) {
    if (!scrollView.scrollEnabled) return
    val (viewW, viewH) = scrollView.bounds.useContents { size.width to size.height }
    val (contentW, contentH) = scrollView.contentSize.useContents { width to height }
    val (curX, curY) = scrollView.contentOffset.useContents { x to y }
    val (newX, newY) = scrollView.adjustedContentInset.useContents {
        PointerWheelScroll.scrolledOffset(curX, dxPoints, contentW, viewW, left, right) to
            PointerWheelScroll.scrolledOffset(curY, dyPoints, contentH, viewH, top, bottom)
    }
    if (newX != curX || newY != curY) scrollView.setContentOffset(CGPointMake(newX, newY), animated = false)
}

/**
 * `(x, y, dx, dy)` in CSS px: scrolls the nearest element under the point that has its own overflow scrolling and
 * room left in that direction, and returns true; false (nothing to scroll inside) leaves the page to the native side.
 */
private const val INNER_SCROLL_JS = """(function(x,y,dx,dy){
var e=document.elementFromPoint(x,y),r=document.scrollingElement||document.documentElement;
for(;e&&e!==r&&e!==document.body;e=e.parentElement){
var s=getComputedStyle(e),v=/(auto|scroll|overlay)/.test(s.overflowY),h=/(auto|scroll|overlay)/.test(s.overflowX);
var canY=v&&e.scrollHeight>e.clientHeight&&(dy>0?e.scrollTop+e.clientHeight<e.scrollHeight-1:dy<0&&e.scrollTop>0);
var canX=h&&e.scrollWidth>e.clientWidth&&(dx>0?e.scrollLeft+e.clientWidth<e.scrollWidth-1:dx<0&&e.scrollLeft>0);
if(canY||canX){e.scrollBy(canX?dx:0,canY?dy:0);return true;}}
return false;})"""
