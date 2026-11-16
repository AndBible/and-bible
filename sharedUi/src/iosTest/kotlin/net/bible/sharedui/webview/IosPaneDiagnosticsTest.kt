package net.bible.sharedui.webview

import net.bible.sharedui.host.diagnostics.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class IosPaneDiagnosticsTest {
    @Test
    fun eligibilityReportsCountsAndAllSkipReasons() {
        val events = mutableListOf<RuntimeDiagnosticEvent>()
        val candidates = listOf(
            PaneDiagnosticCandidate("p1", "v1", false, false, false),
            PaneDiagnosticCandidate("p2", "v2", true, false, false),
            PaneDiagnosticCandidate("p3", "v3", true, true, false),
            PaneDiagnosticCandidate("p4", "v4", true, true, true),
        )
        observePaneCandidates(candidates, ProbeTrigger.LIFECYCLE) { events += it }
        val skips = events.filterIsInstance<RuntimeDiagnosticEvent.PaneSkipped>()
        assertEquals(listOf(DiagnosticReason.NO_HANDLER, DiagnosticReason.NO_URL, DiagnosticReason.NO_WINDOW), skips.map { it.reason })
        assertEquals(listOf("p1", "p2", "p3"), skips.map { it.paneId })
        assertEquals(RuntimeDiagnosticEvent.PaneCandidates(ProbeTrigger.LIFECYCLE, 4, 1), events.first())
    }

    @Test
    fun identitiesCorrelateCallbacksWithoutExposingWindowIds() {
        val ids = PaneDiagnosticIdentity()
        val first = ids.newEntry()
        val second = ids.newEntry()
        assertNotEquals(first.paneId, second.paneId)
        assertNotEquals(first.paneId, first.webViewId)
        val events = mutableListOf<RuntimeDiagnosticEvent>()
        first.clientReady { events += it }
        first.terminated { events += it }
        assertEquals(RuntimeDiagnosticEvent.ClientReady(first.paneId), events[0])
        assertEquals(RuntimeDiagnosticEvent.ContentTerminated(first.webViewId), events[1])
    }
}
