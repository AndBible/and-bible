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
import net.bible.sharedcore.log.nsLog
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSProcessInfo
import platform.Foundation.NSNumber
import platform.Foundation.valueForKey
import platform.posix.SIGKILL
import platform.posix.kill
import platform.UIKit.UIApplicationWillResignActiveNotification
import platform.WebKit.WKWebView
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.Platform

/** Debug-only launch flag: kill every reading WebView's WebContent process when the app leaves the foreground. */
internal const val KILL_WEB_CONTENT_ON_BACKGROUND = "-killWebContentOnBackground"

/**
 * Debug-only test seam for the "panes stay loading after the app was hidden" bug: macOS/iOS WebKit kills the
 * WebContent process of a hidden app, the page restarts on the same native session. With [KILL_WEB_CONTENT_ON_BACKGROUND]
 * (debug binaries only) every reading WebView's WebContent process gets SIGKILL as soon as the app resigns active
 * (on its way to the background), as the system's memory killer does, so WebKit reports a real termination
 * (`webViewWebContentProcessDidTerminate`). WebKit's own `_killWebContentProcess` does not do: a client-requested
 * termination is not reported to the navigation delegate (Mac run 20261103-124841: no I0-WEBVIEW-TERMINATED). The
 * trigger is `willResignActive`, not `didEnterBackground`: on the iPadOS 26 simulator the Home button only takes the
 * focus away and the app never enters the background (Mac run 20261103-141732). The process id comes from WebKit's
 * private `_webProcessIdentifier`. Release builds never read the flag.
 */
@OptIn(ExperimentalNativeApi::class, ExperimentalForeignApi::class)
internal object IosWebContentKillHook {
    private var installed = false

    /** Registers the background observer once when the flag is present; [webViews] lists the live reading WebViews by window id. */
    fun installIfRequested(webViews: () -> Map<String, WKWebView>) {
        if (installed || !Platform.isDebugBinary) return
        if (NSProcessInfo.processInfo.arguments.none { it.toString() == KILL_WEB_CONTENT_ON_BACKGROUND }) return
        installed = true
        NSNotificationCenter.defaultCenter.addObserverForName(
            UIApplicationWillResignActiveNotification, null, NSOperationQueue.mainQueue,
        ) { _ -> killAll(webViews()) }
    }

    private fun killAll(webViews: Map<String, WKWebView>) {
        // Assembled at run time: the private key never appears as one literal in the binary.
        val pidKey = listOf("_web", "Process", "Identifier").joinToString("")
        for ((windowId, webView) in webViews) {
            try {
                val pid = (webView.valueForKey(pidKey) as? NSNumber)?.intValue ?: 0
                if (pid <= 0) {
                    nsLog("I0-WEBVIEW-KILL $windowId: no content process")
                    continue
                }
                val result = kill(pid, SIGKILL)
                nsLog("I0-WEBVIEW-KILL $windowId: pid $pid result $result")
            } catch (t: Throwable) {
                nsLog("I0-WEBVIEW-KILL $windowId: failure $t")
            }
        }
    }
}
