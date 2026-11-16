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
import net.bible.sharedui.host.diagnostics.*

/** Issue #4004: a pane whose page is dead or hung without a reported termination is rebuilt when the app becomes active. */
@OptIn(ExperimentalForeignApi::class)
class IosPaneLivenessTest {
    @Test
    fun injectedIdentitiesAndCandidateCountReachProbeContext() {
        val view = webView()
        val events = mutableListOf<RuntimeDiagnosticEvent>()
        IosPaneLiveness(panes = { mapOf("private" to view) }, recover = {},
            identities = { _, _ -> "pane" to "view" }, observeCandidates = { 4 },
            evaluate = { _, _, done -> done("object", false) }, diagnostic = { events += it }, log = {}).checkAll()
        val context = events.filterIsInstance<RuntimeDiagnosticEvent.ProbeContext>().single()
        assertEquals("pane", context.paneId)
        assertEquals("view", context.webViewId)
        assertEquals(4, context.candidateCount)
        assertEquals(1, context.eligibleCount)
    }

    @Test
    fun observationsSettleOnceAndPreserveRecovery() {
        val view = webView()
        val events = mutableListOf<RuntimeDiagnosticEvent>()
        var answer: String? = "object"
        var callback: ((Any?, Boolean) -> Unit)? = null
        var recoveries = 0
        val liveness = IosPaneLiveness(
            panes = { mapOf("private-window" to view) }, recover = { recoveries++ }, timeoutSeconds = 0.02,
            evaluate = { _, _, done -> if (answer == null) callback = done else done(answer, false) },
            diagnostic = { events += it }, log = {},
        )
        liveness.checkAll()
        answer = "undefined"
        liveness.checkAll()
        answer = null
        liveness.checkAll()
        spin(0.2)
        callback?.invoke("object", false)
        val results = events.filterIsInstance<RuntimeDiagnosticEvent.ProbeResult>()
        assertEquals(listOf(ProbeOutcome.ALIVE, ProbeOutcome.DEAD, ProbeOutcome.UNRESPONSIVE), results.map { it.outcome })
        assertEquals(2, recoveries)
        assertEquals(3, events.filterIsInstance<RuntimeDiagnosticEvent.ProbeContext>().size)
        assertTrue(results.all { it.durationMillis >= 0 && it.paneId != "private-window" })
    }

    @Test
    fun loadingAnswerIsObservedWithoutRecovery() {
        val view = webView().apply { loadHTMLString("<p>loading</p>", baseURL = null) }
        assertTrue(view.loading)
        val events = mutableListOf<RuntimeDiagnosticEvent>()
        var calls = 0
        IosPaneLiveness(panes = { mapOf("w" to view) }, recover = { calls++ },
            evaluate = { _, _, done -> done("undefined", false) }, diagnostic = { events += it }, log = {}).checkAll()
        assertEquals(ProbeOutcome.LOADING, events.filterIsInstance<RuntimeDiagnosticEvent.ProbeResult>().single().outcome)
        assertEquals(0, calls)
    }

    @Test
    fun replacedViewReportsStaleAliveAndLoadingWithoutRecovery() {
        for ((loading, expected) in listOf(false to ProbeOutcome.ALIVE, true to ProbeOutcome.LOADING)) {
            var current = webView()
            var callback: ((Any?, Boolean) -> Unit)? = null
            val events = mutableListOf<RuntimeDiagnosticEvent>()
            var recoveries = 0
            val liveness = IosPaneLiveness(
                panes = { mapOf("w" to current) }, recover = { recoveries++ },
                evaluate = { _, _, done -> callback = done }, isLoading = { loading },
                diagnostic = { events += it }, log = {},
            )
            liveness.checkAll()
            current = webView()
            callback!!("object", false)
            val result = events.filterIsInstance<RuntimeDiagnosticEvent.ProbeResult>().single()
            assertEquals(expected, result.outcome)
            assertTrue(result.staleIdentity)
            assertEquals(0, recoveries)
        }
    }

    @Test
    fun timeoutWaitsForDeadlineAndIgnoresLateAnswer() {
        val view = webView()
        var callback: ((Any?, Boolean) -> Unit)? = null
        var scheduled: (() -> Unit)? = null
        var delayNanos = -1L
        var recoveries = 0
        val events = mutableListOf<RuntimeDiagnosticEvent>()
        val liveness = IosPaneLiveness(
            panes = { mapOf("w" to view) }, recover = { recoveries++ }, timeoutSeconds = 10.0,
            evaluate = { _, _, done -> callback = done },
            scheduleTimeout = { delay, action -> delayNanos = delay; scheduled = action },
            diagnostic = { events += it }, log = {},
        )
        liveness.checkAll()
        assertEquals(10_000_000_000L, delayNanos)
        assertEquals(0, recoveries)
        assertTrue(events.filterIsInstance<RuntimeDiagnosticEvent.ProbeResult>().isEmpty())
        scheduled!!()
        assertEquals(1, recoveries)
        callback!!("object", false)
        scheduled!!()
        assertEquals(1, recoveries)
        assertEquals(listOf(ProbeOutcome.UNRESPONSIVE), events.filterIsInstance<RuntimeDiagnosticEvent.ProbeResult>().map { it.outcome })
    }

    @Test
    fun observationsReportStaleGiveUpAndRecoveryFailure() {
        val old = webView()
        var current = old
        var callback: ((Any?, Boolean) -> Unit)? = null
        val events = mutableListOf<RuntimeDiagnosticEvent>()
        var calls = 0
        val liveness = IosPaneLiveness(
            panes = { mapOf("w" to current) }, recover = { calls++; error("private failure") },
            maxRebuilds = 1, timeoutSeconds = 10.0,
            evaluate = { _, _, done -> callback = done }, diagnostic = { events += it }, log = {},
        )
        liveness.checkAll()
        current = webView()
        callback!!(null, true)
        assertTrue(events.filterIsInstance<RuntimeDiagnosticEvent.ProbeResult>().single().staleIdentity)
        liveness.checkAll(); callback!!(null, true)
        liveness.checkAll(); callback!!(null, true)
        assertEquals(1, calls)
        val recovery = events.filterIsInstance<RuntimeDiagnosticEvent.RecoveryResult>()
        assertEquals(listOf(RecoveryAction.REQUESTED, RecoveryAction.FAILED, RecoveryAction.GIVE_UP), recovery.map { it.action })
        assertEquals(RecoveryFailure.OTHER, recovery[1].failure)
    }

    @Test
    fun evaluationThrowIsReportedWithoutRecoveryOrTimeout() {
        val events = mutableListOf<RuntimeDiagnosticEvent>()
        var calls = 0
        val view = webView()
        IosPaneLiveness(panes = { mapOf("w" to view) }, recover = { calls++ }, timeoutSeconds = 0.02,
            evaluate = { _, _, _ -> error("private error") }, diagnostic = { events += it }, log = {}).checkAll()
        spin(0.2)
        assertEquals(0, calls)
        assertTrue(events.filterIsInstance<RuntimeDiagnosticEvent.ProbeResult>().isEmpty())
        assertEquals(DiagnosticReason.OTHER, events.filterIsInstance<RuntimeDiagnosticEvent.ProbeSkipped>().single().reason)
    }

    @Test
    fun observerFailureDoesNotPreventRecovery() {
        val view = webView()
        var calls = 0
        IosPaneLiveness(panes = { mapOf("w" to view) }, recover = { calls++ },
            evaluate = { _, _, done -> done(null, true) }, diagnostic = { error("observer") }, log = {}).checkAll()
        assertEquals(1, calls)
    }

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

    /** A window that keeps dying is rebuilt at most [MAX_LIVENESS_REBUILDS] times in a row; an alive probe resets the count. */
    @Test
    fun consecutiveRebuildsAreCappedAndAliveProbeResets() {
        val rebuilt = mutableListOf<String>()
        val logs = mutableListOf<String>()
        var answer = "undefined"
        val view = webView()
        val liveness = IosPaneLiveness(
            panes = { mapOf("w1" to view) },
            recover = { rebuilt += it },
            timeoutSeconds = 10.0,
            evaluate = { _, _, done -> done(answer, false) },
            log = { logs += it },
        )
        repeat(MAX_LIVENESS_REBUILDS + 2) { liveness.checkAll() }
        assertEquals(MAX_LIVENESS_REBUILDS, rebuilt.size)
        assertEquals(1, logs.count { it.contains("giving up") }, "the cap is logged once")
        answer = "object"
        liveness.checkAll()
        answer = "undefined"
        liveness.checkAll()
        assertEquals(MAX_LIVENESS_REBUILDS + 1, rebuilt.size, "an alive probe resets the cap")
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
