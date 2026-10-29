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

package net.bible.sharedui.webview

import net.bible.sharedcore.bibleview.JsHost
import net.bible.sharedui.app.ports.ExternalLinks
import net.bible.sharedui.ios.reading.ReadingPaneEngine
import net.bible.sharedui.reading.bridge.ReadingWindowSurface

/** The iOS [ReadingPaneEngine]: the [IosReadingWebViews] WKWebView cache, a [WkWebViewJsHost] and [iosReadingWindowSurface]. */
@kotlin.native.HiddenFromObjC
object IosReadingPaneEngine : ReadingPaneEngine {
    override fun setActiveLanguages(source: () -> String) {
        IosReadingWebViews.activeLanguages = source
    }

    override fun jsHostFor(windowId: String): JsHost = WkWebViewJsHost(IosReadingWebViews.webViewFor(windowId))

    override fun surfaceFor(host: JsHost, links: ExternalLinks): ReadingWindowSurface = iosReadingWindowSurface(host, links = links)

    override fun applyBackground(windowId: String, argb: Int) = IosReadingWebViews.applyBackground(windowId, argb)

    override fun release(windowId: String) = IosReadingWebViews.release(windowId)
}
