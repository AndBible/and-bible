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
package net.bible.sharedui.webview

import kotlinx.cinterop.ExperimentalForeignApi
import platform.CoreGraphics.CGRectMake
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalForeignApi::class)
class IosNavDelegateTerminationTest {
    /** A killed WebContent process restarts the page and asks the window to load its text at the current position. */
    @Test
    fun terminationRestartsThePageOnce() {
        var restarts = 0
        val delegate = IosNavDelegate("w1", openLink = { false }, onPageRestart = { restarts++ })
        val webView = WKWebView(CGRectMake(0.0, 0.0, 320.0, 480.0), WKWebViewConfiguration())

        delegate.webViewWebContentProcessDidTerminate(webView)

        assertEquals(1, restarts)
    }

    /** A failing restart hook must not escape into WebKit's delegate callback. */
    @Test
    fun failingRestartHookIsContained() {
        val delegate = IosNavDelegate("w1", openLink = { false }, onPageRestart = { error("no window") })
        val webView = WKWebView(CGRectMake(0.0, 0.0, 320.0, 480.0), WKWebViewConfiguration())

        delegate.webViewWebContentProcessDidTerminate(webView)
    }
}
