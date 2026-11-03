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

import net.bible.sharedcore.log.nsLog
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSProcessInfo
import platform.Foundation.NSSelectorFromString
import platform.UIKit.UIApplicationDidEnterBackgroundNotification
import platform.WebKit.WKWebView
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.Platform

/** Debug-only launch flag: kill every reading WebView's WebContent process when the app goes to the background. */
internal const val KILL_WEB_CONTENT_ON_BACKGROUND = "-killWebContentOnBackground"

/**
 * Debug-only test seam for the "panes stay loading after the app was hidden" bug: macOS/iOS WebKit kills the
 * WebContent process of a hidden app, the page restarts on the same native session. With [KILL_WEB_CONTENT_ON_BACKGROUND]
 * (debug binaries only) the reading WebViews' content processes are killed through WebKit's private
 * `_killWebContentProcess` (the call WebKit's own tests use) as soon as the app enters the background, so a UI test can
 * reproduce that on the simulator. Release builds never read the flag.
 */
@OptIn(ExperimentalNativeApi::class)
internal object IosWebContentKillHook {
    private var installed = false

    /** Registers the background observer once when the flag is present; [webViews] lists the live reading WebViews. */
    fun installIfRequested(webViews: () -> List<WKWebView>) {
        if (installed || !Platform.isDebugBinary) return
        if (NSProcessInfo.processInfo.arguments.none { it.toString() == KILL_WEB_CONTENT_ON_BACKGROUND }) return
        installed = true
        NSNotificationCenter.defaultCenter.addObserverForName(
            UIApplicationDidEnterBackgroundNotification, null, NSOperationQueue.mainQueue,
        ) { _ -> killAll(webViews()) }
    }

    private fun killAll(webViews: List<WKWebView>) {
        val selector = NSSelectorFromString("_killWebContentProcess")
        for (webView in webViews) {
            try {
                if (webView.respondsToSelector(selector)) {
                    nsLog("I0-WEBVIEW-KILL requested")
                    webView.performSelector(selector)
                } else {
                    nsLog("I0-WEBVIEW-KILL unavailable")
                }
            } catch (t: Throwable) {
                nsLog("I0-WEBVIEW-KILL failure $t")
            }
        }
    }
}
