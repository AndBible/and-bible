package net.bible.sharedui.webview

import net.bible.sharedui.host.diagnostics.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class IosPaneDiagnosticsTest {
    @Test
    fun bridgeRoutesOnlySuccessfulClientReadyToDiagnosticCallback() {
        var ready = 0
        var observed = 0
        val bridge = object : net.bible.sharedcore.bibleview.UnimplementedBibleBridge({}, {}) {
            override fun setClientReady() { ready++ }
        }
        val dispatcher = net.bible.sharedcore.bibleview.BridgeDispatcher(bridge, {}, {})
        val handler = IosBridgeMessageHandler(dispatcher) { observed++ }
        handler.dispatchText("""{"method":"setClientReady","args":[]}""")
        handler.dispatchText("""{"method":"unknown","args":[]}""")
        handler.dispatchText("not json")
        assertEquals(1, ready)
        assertEquals(1, observed)
        val throwingHandler = IosBridgeMessageHandler(dispatcher) { error("sink failed") }
        throwingHandler.dispatchText("""{"method":"setClientReady","args":[]}""")
        assertEquals(2, ready)
    }

    @Test
    fun productionCandidateMappingUsesLiveEntryState() {
        val entry = IosReadingWebViews.entry("diagnostic-private-window")
        try {
            val first = entry.diagnosticCandidate()
            assertEquals(false, first.hasHandler)
            assertEquals(false, first.hasUrl)
            assertEquals(false, first.hasWindow)
            assertEquals(entry.diagnosticIdentity.paneId, first.paneId)
            assertEquals(entry.diagnosticIdentity.webViewId, first.webViewId)
            entry.handlerCount = 1
            assertEquals(true, entry.diagnosticCandidate().hasHandler)
        } finally {
            entry.handlerCount = 0
            IosReadingWebViews.release("diagnostic-private-window")
        }
    }
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
