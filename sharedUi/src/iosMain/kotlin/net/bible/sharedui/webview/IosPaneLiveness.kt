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
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.WebKit.WKWebView
import platform.darwin.DISPATCH_TIME_NOW
import platform.darwin.dispatch_after
import platform.darwin.dispatch_get_main_queue
import platform.darwin.dispatch_time

/** What a liveness probe found out about a reading page. */
internal enum class PaneHealth {
    /** The BibleView page answered. */
    Alive,

    /** The page is (re)loading: BibleView-JS has not run yet, so its answer says nothing. */
    Loading,

    /** The page answered without BibleView (blank page) or the evaluation failed (no content process). */
    Dead,

    /** No answer within the timeout: the content process is alive but hung (or suspended and never resumed). */
    Unresponsive,
}

/**
 * The verdict on one probe: [answered] (the evaluation came back before the timeout), its [result] and whether it
 * [failed], and whether the web view was [loading] at that moment.
 */
internal fun paneHealth(answered: Boolean, result: Any?, failed: Boolean, loading: Boolean): PaneHealth = when {
    !answered -> PaneHealth.Unresponsive
    loading -> PaneHealth.Loading
    failed || result?.toString() != PANE_PROBE_ALIVE -> PaneHealth.Dead
    else -> PaneHealth.Alive
}

/** Evaluated in the page: BibleView-JS defines `window.bibleView` (main.ts) when it starts. */
internal const val PANE_PROBE_JS = "typeof window.bibleView"
private const val PANE_PROBE_ALIVE = "object"

/** How long a page may take to answer the probe before it counts as hung. */
internal const val PANE_PROBE_TIMEOUT_SECONDS = 10.0

/**
 * Recovers reading panes whose page died without WebKit telling us (issue #4004). WebKit reports a killed content process
 * through `webViewWebContentProcessDidTerminate` ([IosNavDelegate] reloads then), but not every way a page dies while the
 * app is hidden: on the Mac (AndBible left on another virtual desktop for a long time) the panes came back blank. A
 * content process that is alive but stopped is the reproducible case: WebKit only logs
 * `WebProcessProxy::didBecomeUnresponsive`, never terminates it, and a `reload()` is sent to the same hung process, so
 * the pane stays blank for good (Mac simulator runs, SIGSTOP of the content process while the app was inactive).
 *
 * So whenever the app becomes active again, every wired pane ([panes]) is probed ([PANE_PROBE_JS]). A pane that answers
 * without BibleView or fails ([PaneHealth.Dead]) or does not answer within [timeoutSeconds] ([PaneHealth.Unresponsive])
 * is handed to [recover], which gives the window a new web view (new content process) and session
 * (`HostReadingWindows.rebuild`). A pane that is (re)loading is left alone. A pane whose web view changed while its
 * probe ran (released, re-keyed, rebuilt) is not recovered for the old one. Main thread.
 */
internal class IosPaneLiveness(
    private val panes: () -> Map<String, WKWebView>,
    private val recover: (windowId: String) -> Unit,
    private val timeoutSeconds: Double = PANE_PROBE_TIMEOUT_SECONDS,
    /** Evaluates the probe; calls back with (result, failed). A test seam: the default is `evaluateJavaScript`. */
    private val evaluate: (WKWebView, String, (Any?, Boolean) -> Unit) -> Unit = { webView, js, done ->
        webView.evaluateJavaScript(js) { result, error -> done(result, error != null) }
    },
    private val log: (String) -> Unit = { nsLog(it) },
) {
    private var observer: Any? = null

    /** Probes every pane each time the app becomes active (once installed, later calls do nothing). */
    fun installOnBecomeActive() {
        if (observer != null) return
        observer = NSNotificationCenter.defaultCenter.addObserverForName(
            UIApplicationDidBecomeActiveNotification, null, NSOperationQueue.mainQueue,
        ) { _ -> checkAll() }
    }

    fun checkAll() {
        for ((windowId, webView) in panes()) check(windowId, webView)
    }

    fun check(windowId: String, webView: WKWebView) {
        var settled = false
        fun settle(health: PaneHealth) {
            if (settled) return
            settled = true
            if (health != PaneHealth.Dead && health != PaneHealth.Unresponsive) return
            if (panes()[windowId] !== webView) return
            log("I0-WEBVIEW-DEAD $windowId: $health, rebuilding the pane")
            try {
                recover(windowId)
            } catch (t: Throwable) {
                log("bridge[$windowId]: pane rebuild failure $t")
            }
        }
        try {
            evaluate(webView, PANE_PROBE_JS) { result, failed -> settle(paneHealth(true, result, failed, webView.loading)) }
        } catch (t: Throwable) {
            log("bridge[$windowId]: liveness probe failure $t")
            return
        }
        val delay = (timeoutSeconds * 1_000_000_000.0).toLong()
        dispatch_after(dispatch_time(DISPATCH_TIME_NOW, delay), dispatch_get_main_queue()) {
            settle(paneHealth(answered = false, result = null, failed = false, loading = webView.loading))
        }
    }
}
