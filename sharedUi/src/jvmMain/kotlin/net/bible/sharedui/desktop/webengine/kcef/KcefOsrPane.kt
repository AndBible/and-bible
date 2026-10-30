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
import androidx.compose.runtime.SideEffect
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
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import dev.datlag.kcef.KCEFBrowser
import kotlinx.coroutines.CoroutineDispatcher
import net.bible.sharedui.desktop.webengine.DesktopWebPane
import org.cef.browser.CefRendering
import org.cef.input.CefCompositionUnderline
import org.cef.misc.CefRange
import java.awt.Point
import java.awt.Rectangle
import java.awt.event.KeyEvent
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
 * Mouse, wheel and cursor are handled here ([OsrInput]); keyboard and focus through [OsrFocus] (every key goes to the page,
 * which owns the shortcuts) and composed text through [OsrIme].
 */
internal class KcefOsrPane(
    override val id: String,
    url: String,
    bundle: BundleRequestHandler,
    onMessage: (String) -> Unit,
    private val log: (String) -> Unit,
) : DesktopWebPane, OsrBrowserSink, OsrKeyboardSink, OsrImeSink {
    /** Invisible lightweight anchor JCEF holds for the browser; it is never shown or added to a container. */
    private val hostComponent = JPanel().apply { isOpaque = false; isVisible = false }

    @Volatile
    private var geometry = OsrGeometry(1, 1, 1.0, Point(0, 0))
    private var cursor by mutableStateOf<PointerIcon>(PointerIcon.Default)
    private var background by mutableStateOf<Color?>(null)
    private val frames = OsrFrameBuffer(onPersistentMismatch = { log("off-screen: $it") })
    private val input = OsrInput(this, { geometry }, hostComponent)
    private val ime = OsrIme(this)
    private val focus = OsrFocus(this, onFocusChange = ime.gate::setPaneFocused)

    // The page's mousedown asks for focus: in off-screen mode that is Compose focus (which then focuses CEF), not X11.
    private val core: KcefPaneCore = KcefPaneCore(url, bundle, onMessage, log, requestFocus = { focus.requestFocus() }, onEditableFocus = ime.gate::setEditable) {
        // Only the native browser takes a frame rate; before onAfterCreated the call is silently lost (CEF default 30 fps).
        it.setWindowlessFrameRate(FRAME_RATE)
    }
    private val browser: KCEFBrowser = core.createBrowser(
        CefRendering.CefRenderingWithHandler(OsrRenderHandler({ geometry }, frames, onCursor = { type ->
            // CEF calls this on its UI thread; Compose state is written on the EDT.
            SwingUtilities.invokeLater { if (!core.isDisposed) cursor = OsrInput.pointerIconFor(type) }
        }, onImeRange = ime::onCompositionRangeChanged), hostComponent),
    ).also {
        it.createImmediately()
    }

    @Composable
    override fun Content(modifier: Modifier) {
        val scale = LocalDensity.current.density.toDouble()
        val frame by frames.frame
        DisposableEffect(scale) {
            // A new device scale reaches CEF through the next layout size and this notification.
            ime.scale = scale
            update(geometry.widthPx, geometry.heightPx, scale, geometry.screenOrigin)
            onDispose {}
        }
        val focusManager = LocalFocusManager.current
        SideEffect { focus.clearFocus = { focusManager.clearFocus(force = true) } }
        DisposableEffect(Unit) {
            val outsidePress = OsrOutsidePress(focus, ::paneScreenRect) { OsrOutsidePress.windowOf(paneScreenRect()) }
            outsidePress.install()
            onDispose {
                outsidePress.uninstall()
                focus.release()
            }
        }
        Canvas(
            modifier
                .onSizeChanged { update(it.width, it.height, scale, geometry.screenOrigin) }
                .onGloballyPositioned { coords ->
                    val p = coords.positionOnScreen()
                    coords.positionInRoot().let { if (it.isSpecified) ime.originInRootPx = it }
                    if (p.isSpecified) geometry = geometry.copy(screenOrigin = Point((p.x / scale).toInt(), (p.y / scale).toInt()))
                }
                .pointerHoverIcon(cursor)
                .osrPointerInput(input, onPress = focus::requestFocus)
                .osrKeyboard(focus, ime)
                .clipToBounds(),
        ) {
            background?.let { drawRect(it) }
            // At the image's own pixel size: a stale frame of the previous size is clipped or leaves background, never scaled.
            frame?.let { drawImage(it, Offset.Zero) }
        }
    }

    /** The pane's bounds on screen in AWT units, from the last layout. */
    private fun paneScreenRect(): Rectangle? = geometry.let { g -> Rectangle(g.screenOrigin.x, g.screenOrigin.y, g.widthDip, g.heightDip) }

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

    override fun sendMouse(e: MouseEvent) { if (!core.isDisposed) browser.sendMouseEvent(e) }

    override fun sendWheel(e: MouseWheelEvent) { if (!core.isDisposed) browser.sendMouseWheelEvent(e) }

    override fun setFocus(focused: Boolean) { if (!core.isDisposed) browser.setFocus(focused) }

    override fun sendKey(e: KeyEvent) { if (!core.isDisposed) browser.sendKeyEvent(e) }

    override fun setComposition(text: String, underlines: List<CefCompositionUnderline>, replacement: CefRange, selection: CefRange) {
        if (!core.isDisposed) browser.ImeSetComposition(text, underlines, replacement, selection)
    }

    override fun commitText(text: String, replacement: CefRange) { if (!core.isDisposed) browser.ImeCommitText(text, replacement, 0) }

    override fun cancelComposing() { if (!core.isDisposed) browser.ImeCancelComposing() }

    override fun finishComposing() { if (!core.isDisposed) browser.ImeFinishComposingText(false) }

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

    override fun dispose() {
        focus.release()
        core.dispose()
    }

    private companion object {
        const val FRAME_RATE = 60
    }
}
