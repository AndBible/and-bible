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

import net.bible.sharedcore.bibleview.JsHost
import net.bible.sharedui.app.ports.ExternalLinks
import net.bible.sharedui.reading.bridge.ReadingWindowSurface

/**
 * The platform web engine behind the reading windows, as [IosReadingWindows] assembles them: one cached web view per
 * window id, created on first use and kept until [release]. The assembly builds each window's session over
 * [jsHostFor] and [surfaceFor]; the pane composable that shows the view, and the bridge handler wiring, belong to the
 * platform's pane factory, not to this interface.
 *
 * iOS: the WKWebView cache (`IosReadingWebViews`) with a `WkWebViewJsHost`. A desktop engine does not fit this shape
 * one to one: a `DesktopWebPane` is a [JsHost], but `DesktopWebEngine.createPane` needs the page URL and the
 * bridge's message handler at creation, and the active languages reach the page through the engine's bridge shim
 * (`getActiveLanguages()`), not a document-start script. A desktop implementation must therefore create the pane in
 * [jsHostFor] with a deferred message sink that the session's bridge is attached to later (as `DesktopBibleWebViews`
 * queues JS until its pane exists), and read [setActiveLanguages]'s source when it builds the shim.
 *
 * Main thread only, except [applyBackground].
 */
@kotlin.native.HiddenFromObjC
interface ReadingPaneEngine {
    /** The installed Bibles' languages as a JSON list; read when a window's web view is created. */
    fun setActiveLanguages(source: () -> String)

    /** The window's web view as the session's [JsHost], created on first use (not loaded yet, no bridge handler). */
    fun jsHostFor(windowId: String): JsHost

    /** The window's [ReadingWindowSurface] (share, clipboard, toasts, external links) over [host]. */
    fun surfaceFor(host: JsHost, links: ExternalLinks): ReadingWindowSurface

    /** Paints the window's page background with [argb]; callable from any thread. A window without a web view is ignored. */
    fun applyBackground(windowId: String, argb: Int)

    /** Drops the window's web view for good (window closed); the session is disposed by the caller. */
    fun release(windowId: String)
}
