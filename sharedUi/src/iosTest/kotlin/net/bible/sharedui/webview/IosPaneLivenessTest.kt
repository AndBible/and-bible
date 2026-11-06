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
import platform.Foundation.NSDate
import platform.Foundation.NSRunLoop
import platform.Foundation.dateWithTimeIntervalSinceNow
import platform.Foundation.runUntilDate
import platform.Foundation.timeIntervalSinceDate
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Issue #4004: a pane whose page is dead or hung without a reported termination is rebuilt when the app becomes active. */
@OptIn(ExperimentalForeignApi::class)
class IosPaneLivenessTest {
    private fun webView() = WKWebView(CGRectMake(0.0, 0.0, 320.0, 480.0), WKWebViewConfiguration())

    private fun spin(seconds: Double) {
        val end = NSDate.dateWithTimeIntervalSinceNow(seconds)
        while (NSDate().timeIntervalSinceDate(end) < 0) NSRunLoop.mainRunLoop.runUntilDate(NSDate.dateWithTimeIntervalSinceNow(0.02))
    }

    private fun spinUntil(seconds: Double, done: () -> Boolean) {
        val end = NSDate.dateWithTimeIntervalSinceNow(seconds)
        while (!done() && NSDate().timeIntervalSinceDate(end) < 0) NSRunLoop.mainRunLoop.runUntilDate(NSDate.dateWithTimeIntervalSinceNow(0.02))
    }

    @Test
    fun verdicts() {
        assertEquals(PaneHealth.Alive, paneHealth(answered = true, result = "object", failed = false, loading = false))
        assertEquals(PaneHealth.Dead, paneHealth(answered = true, result = "undefined", failed = false, loading = false))
        assertEquals(PaneHealth.Dead, paneHealth(answered = true, result = null, failed = true, loading = false))
        assertEquals(PaneHealth.Loading, paneHealth(answered = true, result = "undefined", failed = false, loading = true))
        // A reload sent to a hung process keeps the web view loading for good: no answer is hung whatever `loading` says.
        assertEquals(PaneHealth.Unresponsive, paneHealth(answered = false, result = null, failed = false, loading = true))
        assertEquals(PaneHealth.Unresponsive, paneHealth(answered = false, result = null, failed = false, loading = false))
    }

    /** A content process that is stopped never answers the probe (Mac simulator, SIGSTOP): the pane is rebuilt once. */
    @Test
    fun hungPaneIsRebuiltAfterTheTimeout() {
        val view = webView()
        val rebuilt = mutableListOf<String>()
        val liveness = IosPaneLiveness(
            panes = { mapOf("w1" to view) },
            recover = { rebuilt += it },
            timeoutSeconds = 0.05,
            evaluate = { _, _, _ -> },
        )
        liveness.checkAll()
        spin(0.4)
        assertEquals(listOf("w1"), rebuilt)
    }

    @Test
    fun blankOrFailingPageIsRebuiltAndAliveOneIsLeftAlone() {
        val views = mapOf("blank" to webView(), "failed" to webView(), "alive" to webView())
        val rebuilt = mutableListOf<String>()
        val liveness = IosPaneLiveness(
            panes = { views },
            recover = { rebuilt += it },
            timeoutSeconds = 0.05,
            evaluate = { view, js, done ->
                assertEquals(PANE_PROBE_JS, js)
                when (view) {
                    views["blank"] -> done("undefined", false)
                    views["failed"] -> done(null, true)
                    else -> done("object", false)
                }
            },
        )
        liveness.checkAll()
        spin(0.3) // past the timeout: an answered probe must not count as hung later
        assertEquals(setOf("blank", "failed"), rebuilt.toSet())
        assertEquals(2, rebuilt.size)
    }

    /** The window got another web view (released, re-keyed or rebuilt) while the probe ran: the old one's verdict is moot. */
    @Test
    fun replacedWebViewIsNotRebuilt() {
        val old = webView()
        var current = old
        val rebuilt = mutableListOf<String>()
        val liveness = IosPaneLiveness(
            panes = { mapOf("w1" to current) },
            recover = { rebuilt += it },
            timeoutSeconds = 0.05,
            evaluate = { _, _, _ -> },
        )
        liveness.checkAll()
        current = webView()
        spin(0.3)
        assertTrue(rebuilt.isEmpty(), "rebuilt $rebuilt")
    }

    @Test
    fun failingRebuildIsContained() {
        val liveness = IosPaneLiveness(
            panes = { mapOf("w1" to webView()) },
            recover = { error("no window") },
            timeoutSeconds = 0.05,
            evaluate = { _, _, done -> done(null, true) },
            log = {},
        )
        liveness.checkAll()
        spin(0.2)
    }

    /** Against real WebKit: a loaded page without BibleView is dead, one that defines `window.bibleView` is alive. */
    @Test
    fun realProbeTellsBibleViewPageFromBlankPage() {
        val alive = webView().apply { loadHTMLString("<script>window.bibleView = {};</script>", baseURL = null) }
        val blank = webView().apply { loadHTMLString("<p>blank</p>", baseURL = null) }
        spinUntil(15.0) { !alive.loading && !blank.loading }
        val rebuilt = mutableListOf<String>()
        val liveness = IosPaneLiveness(panes = { mapOf("alive" to alive, "blank" to blank) }, recover = { rebuilt += it }, timeoutSeconds = 10.0)
        liveness.checkAll()
        spinUntil(10.0) { rebuilt.isNotEmpty() }
        spin(0.3)
        assertEquals(listOf("blank"), rebuilt)
    }
}
