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
package net.bible.sharedui.desktop.webengine.kcef

import java.awt.Point
import java.awt.Rectangle
import java.nio.ByteBuffer
import org.cef.browser.CefBrowser
import org.cef.handler.CefRenderHandlerAdapter
import org.cef.handler.CefScreenInfo

/**
 * Off-screen render handler: reports the view geometry from [geometry] (read on every call, so it follows resizes) and feeds
 * paints into [frames]. Popup rectangles arrive in DIP and are stored in view pixels.
 *
 * @param onCursor receives the CEF cursor type constant when the page changes the cursor
 */
internal class OsrRenderHandler(
    private val geometry: () -> OsrGeometry,
    private val frames: OsrFrameBuffer,
    private val onCursor: (Int) -> Unit,
) : CefRenderHandlerAdapter() {
    @Volatile private var popupDip: Rectangle? = null
    @Volatile private var popupShown = false

    override fun getViewRect(browser: CefBrowser?): Rectangle = geometry().let { Rectangle(0, 0, it.widthDip, it.heightDip) }

    override fun getScreenInfo(browser: CefBrowser?, screenInfo: CefScreenInfo): Boolean {
        val g = geometry()
        val r = Rectangle(g.screenOrigin.x, g.screenOrigin.y, g.widthDip, g.heightDip)
        screenInfo.Set(g.scale, 24, 8, false, r, r)
        return true
    }

    override fun getScreenPoint(browser: CefBrowser?, viewPoint: Point): Point = geometry().toScreen(viewPoint)

    override fun getDeviceScaleFactor(browser: CefBrowser?): Double = geometry().scale

    /** CEF reports the popup size after [onPopupShow](true), so the rect is applied here once the popup is shown. */
    override fun onPopupSize(browser: CefBrowser?, size: Rectangle) {
        popupDip = Rectangle(size)
        if (popupShown) applyPopup()
    }

    override fun onPopupShow(browser: CefBrowser?, show: Boolean) {
        popupShown = show
        if (show) {
            applyPopup()
        } else {
            popupDip = null
            frames.popupRect(null)
        }
    }

    private fun applyPopup() {
        val dip = popupDip ?: return
        val g = geometry()
        val origin = g.toViewPx(dip.x, dip.y)
        frames.popupRect(Rectangle(origin.x, origin.y, Math.round(dip.width * g.scale).toInt(), Math.round(dip.height * g.scale).toInt()))
    }

    override fun onPaint(browser: CefBrowser?, popup: Boolean, dirtyRects: Array<Rectangle>, buffer: ByteBuffer, width: Int, height: Int) {
        frames.paint(popup, dirtyRects, buffer, width, height)
    }

    override fun onCursorChange(browser: CefBrowser?, cursorType: Int): Boolean {
        onCursor(cursorType)
        return true
    }
}
