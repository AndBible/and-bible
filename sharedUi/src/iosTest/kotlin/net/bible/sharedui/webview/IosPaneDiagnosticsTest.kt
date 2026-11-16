@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package net.bible.sharedui.webview

import net.bible.sharedui.ios.diagnostics.IosLifecycleDiagnostics
import platform.CoreGraphics.CGRectMake
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene
import kotlin.test.assertTrue
import net.bible.sharedui.host.diagnostics.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class IosPaneDiagnosticsTest {
    private class OwnerWindow(private val ownerScene: UIWindowScene) : UIWindow(frame = CGRectMake(0.0, 0.0, 320.0, 480.0)) {
        override fun windowScene(): UIWindowScene? = ownerScene
    }

    @Test
    fun productionPaneOwnershipFollowsActualWindowsAndReparenting() {
        val events = mutableListOf<RuntimeDiagnosticEvent>()
        val lifecycle = IosLifecycleDiagnostics(events::add)
        val firstWindow = OwnerWindow(UIWindowScene())
        val secondWindow = OwnerWindow(UIWindowScene())
        val firstHost = UIViewController()
        val secondHost = UIViewController()
        firstWindow.rootViewController = firstHost
        secondWindow.rootViewController = secondHost
        firstWindow.addSubview(firstHost.view)
        secondWindow.addSubview(secondHost.view)
        val first = IosReadingWebViews.entry("private-first-window")
        val second = IosReadingWebViews.entry("private-second-window")
        val previousSink = IosReadingWebViews.diagnosticSink
        IosReadingWebViews.diagnosticSink = events::add
        try {
            lifecycle.attachHost("first-host", firstHost)
            lifecycle.attachHost("second-host", secondHost)
            val hosts = events.filterIsInstance<RuntimeDiagnosticEvent.OwnershipObserved>()
            assertNotEquals(hosts[0].windowId, hosts[1].windowId)
            assertNotEquals(hosts[0].sceneId, hosts[1].sceneId)
            firstHost.view.addSubview(first.webView)
            secondHost.view.addSubview(second.webView)
            fun ownership(paneId: String) = events.filterIsInstance<RuntimeDiagnosticEvent.PaneOwnershipObserved>()
                .last { it.paneId == paneId }
            val firstAttached = ownership(first.diagnosticIdentity.paneId)
            val secondAttached = ownership(second.diagnosticIdentity.paneId)
            assertEquals(first.diagnosticIdentity.webViewId, firstAttached.webViewId)
            assertEquals(hosts[0].windowId, firstAttached.windowId)
            assertEquals(hosts[0].sceneId, firstAttached.sceneId)
            assertEquals(hosts[1].windowId, secondAttached.windowId)
            assertEquals(hosts[1].sceneId, secondAttached.sceneId)
            assertTrue(firstAttached.attached && secondAttached.attached)

            first.webView.removeFromSuperview()
            val detached = ownership(first.diagnosticIdentity.paneId)
            assertEquals("none", detached.windowId)
            assertEquals("none", detached.sceneId)
            assertEquals(false, detached.attached)
            secondHost.view.addSubview(first.webView)
            val moved = ownership(first.diagnosticIdentity.paneId)
            assertEquals(firstAttached.paneId, moved.paneId)
            assertEquals(firstAttached.webViewId, moved.webViewId)
            assertEquals(hosts[1].windowId, moved.windowId)
            assertEquals(hosts[1].sceneId, moved.sceneId)
            assertTrue(moved.attached)
            assertEquals(secondAttached, ownership(second.diagnosticIdentity.paneId))
        } finally {
            IosReadingWebViews.release("private-first-window")
            IosReadingWebViews.release("private-second-window")
            IosReadingWebViews.diagnosticSink = previousSink
            lifecycle.close()
            firstWindow.rootViewController = null
            secondWindow.rootViewController = null
        }
    }

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
