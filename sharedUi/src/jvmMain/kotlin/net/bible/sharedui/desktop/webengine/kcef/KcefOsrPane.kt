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

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.platform.LocalDensity
import dev.datlag.kcef.KCEFBrowser
import kotlinx.coroutines.CoroutineDispatcher
import net.bible.sharedui.desktop.webengine.DesktopWebPane
import org.cef.browser.CefRendering
import java.awt.Point
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelEvent
import javax.swing.JPanel
import javax.swing.SwingUtilities

/**
 * Off-screen pane: CEF paints into [OsrFrameBuffer] and [Content] draws the latest frame in a Compose canvas, one frame pixel
 * per screen pixel, top-left aligned and clipped (the last frame stays visible until the first paint at a new size).
 * The browser is created once and survives leaving and re-entering composition. Everything shared with the windowed pane is
 * in [KcefPaneCore].
 *
 * Mouse, wheel and cursor are handled here ([OsrInput]); keyboard and IME are not yet.
 */
internal class KcefOsrPane(
    override val id: String,
    url: String,
    bundle: BundleRequestHandler,
    onMessage: (String) -> Unit,
    private val log: (String) -> Unit,
) : DesktopWebPane, OsrBrowserSink {
    /** Invisible lightweight anchor JCEF holds for the browser; it is never shown or added to a container. */
    private val hostComponent = JPanel().apply { isOpaque = false; isVisible = false }

    @Volatile
    private var geometry = OsrGeometry(1, 1, 1.0, Point(0, 0))
    private var cursor by mutableStateOf<PointerIcon>(PointerIcon.Default)
    private var background by mutableStateOf<Color?>(null)
    private val frames = OsrFrameBuffer(onPersistentMismatch = { log("off-screen: $it") })
    private val input = OsrInput(this, { geometry }, hostComponent)
    private val core: KcefPaneCore = KcefPaneCore(url, bundle, onMessage, log) { core.browser.setFocus(true) }
    private val browser: KCEFBrowser = core.createBrowser(
        CefRendering.CefRenderingWithHandler(OsrRenderHandler({ geometry }, frames) { type -> cursor = OsrInput.pointerIconFor(type) }, hostComponent),
    ).also {
        it.createImmediately()
        it.setWindowlessFrameRate(FRAME_RATE)
    }

    @Composable
    override fun Content(modifier: Modifier) {
        val scale = LocalDensity.current.density.toDouble()
        val frame by frames.frame
        DisposableEffect(scale) {
            // A new device scale reaches CEF through the next layout size and this notification.
            update(geometry.widthPx, geometry.heightPx, scale, geometry.screenOrigin)
            onDispose {}
        }
        Canvas(
            modifier
                .onSizeChanged { update(it.width, it.height, scale, geometry.screenOrigin) }
                .onGloballyPositioned { coords ->
                    val p = coords.positionOnScreen()
                    if (p.isSpecified) geometry = geometry.copy(screenOrigin = Point((p.x / scale).toInt(), (p.y / scale).toInt()))
                }
                .pointerHoverIcon(cursor)
                .osrPointerInput(input)
                .clipToBounds(),
        ) {
            background?.let { drawRect(it) }
            // At the image's own pixel size: a stale frame of the previous size is clipped or leaves background, never scaled.
            frame?.let { drawImage(it, Offset.Zero) }
        }
    }

    /** Applies a new view size or scale: the frame buffer gets the size CEF will paint, CEF is told. */
    private fun update(widthPx: Int, heightPx: Int, scale: Double, origin: Point) {
        val old = geometry
        val g = OsrGeometry(widthPx, heightPx, scale, origin)
        geometry = g
        if (old.widthPx == widthPx && old.heightPx == heightPx && old.scale == scale) return
        frames.resize(g.bufferWidthPx, g.bufferHeightPx)
        if (core.isDisposed) return
        if (old.scale != scale) browser.notifyScreenInfoChanged()
        browser.wasResized(g.widthDip, g.heightDip)
    }

    override fun sendMouse(e: MouseEvent) = browser.sendMouseEvent(e)

    override fun sendWheel(e: MouseWheelEvent) = browser.sendMouseWheelEvent(e)

    override fun setBackground(argb: Int) {
        background = Color(argb)
        core.setBackground(argb)
    }

    override fun setOnRendererGone(listener: () -> Unit) = core.setOnRendererGone(listener)

    override val isMainThread: Boolean get() = SwingUtilities.isEventDispatchThread()
    override val isAttached: Boolean get() = !core.isDisposed
    override val mainDispatcher: CoroutineDispatcher = SwingMainDispatcher

    override fun post(block: () -> Unit) = SwingUtilities.invokeLater { if (!core.isDisposed) block() }

    override fun evaluate(js: String, callback: ((String) -> Unit)?) = core.evaluate(js, callback)

    override fun dispose() = core.dispose()

    private companion object {
        const val FRAME_RATE = 60
    }
}
