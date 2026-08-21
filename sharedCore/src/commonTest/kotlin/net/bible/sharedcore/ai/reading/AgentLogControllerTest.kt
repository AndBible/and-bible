package net.bible.sharedcore.ai.reading

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class AgentLogControllerTest {

    private class Fake(
        visiblePref: Boolean = false,
        var autoHide: Boolean = true,
        var models: List<ReadingModelVd> = emptyList(),
    ) : AgentSessionService {
        val snap = MutableStateFlow(AgentLogSnapshot())
        override val snapshot: StateFlow<AgentLogSnapshot> = snap
        var stopCount = 0
        var lastDefault: String? = null
        var visiblePrefStore = visiblePref
        var lastSetVisible: Boolean? = null
        var setVisibleCallCount = 0
        override fun stop() { stopCount++ }
        override suspend fun configuredModels() = models
        override fun setDefaultModel(modelId: String) { lastDefault = modelId }
        override fun autoHideEnabled() = autoHide
        override fun logVisiblePref() = visiblePrefStore
        override fun setLogVisiblePref(v: Boolean) { visiblePrefStore = v; lastSetVisible = v; setVisibleCallCount++ }
        override fun currentWorkspaceId() = "ws1"
        override fun refresh() {}
    }

    private class HostCalls { var toast = 0; var rawLog = 0 }

    private fun controller(f: Fake, h: HostCalls = HostCalls()) =
        AgentLogController(f, CoroutineScope(UnconfinedTestDispatcher()), onCompletedToast = { h.toast++ }, onOpenRawLog = { h.rawLog++ })

    @Test fun initialVisibility_fromPref() = runTest {
        val c = controller(Fake(visiblePref = true))
        assertTrue(c.state.value.visible)
    }

    @Test fun autoShow_whenRunStarts() = runTest {
        val f = Fake(visiblePref = false); val c = controller(f)
        assertFalse(c.state.value.visible)
        f.snap.value = AgentLogSnapshot(running = true, statusText = "Working…")
        assertTrue(c.state.value.visible)
        assertEquals(true, f.lastSetVisible)
    }

    @Test fun autoHide_onCompleted_whenEnabled_hidesAndToasts() = runTest {
        val f = Fake(visiblePref = false, autoHide = true); val h = HostCalls(); val c = controller(f, h)
        f.snap.value = AgentLogSnapshot(running = true)
        assertTrue(c.state.value.visible)
        f.snap.value = AgentLogSnapshot(running = false, lastStopReason = AgentStopReasonVd.COMPLETED)
        assertFalse(c.state.value.visible)
        assertEquals(1, h.toast)
    }

    @Test fun noAutoHide_onError_staysVisible() = runTest {
        val f = Fake(visiblePref = false, autoHide = true); val h = HostCalls(); val c = controller(f, h)
        f.snap.value = AgentLogSnapshot(running = true)
        f.snap.value = AgentLogSnapshot(running = false, lastStopReason = AgentStopReasonVd.ERROR)
        assertTrue(c.state.value.visible)
        assertEquals(0, h.toast)
    }

    @Test fun noAutoHide_whenSettingOff() = runTest {
        val f = Fake(visiblePref = false, autoHide = false); val c = controller(f)
        f.snap.value = AgentLogSnapshot(running = true)
        f.snap.value = AgentLogSnapshot(running = false, lastStopReason = AgentStopReasonVd.COMPLETED)
        assertTrue(c.state.value.visible)
    }

    @Test fun autoHide_onCancelled_whenEnabled_hidesButNoToast() = runTest {
        val f = Fake(visiblePref = false, autoHide = true); val h = HostCalls(); val c = controller(f, h)
        f.snap.value = AgentLogSnapshot(running = true)
        assertTrue(c.state.value.visible)
        f.snap.value = AgentLogSnapshot(running = false, lastStopReason = AgentStopReasonVd.CANCELLED)
        assertFalse(c.state.value.visible)
        assertEquals(0, h.toast)
    }

    // Reproduces Finding 1: a `StateFlow` replays its current value to every new collector, so a
    // level-triggered reduce() would spuriously re-fire the auto-hide/toast for a terminal reason
    // it (or an earlier controller instance) already handled. Drives a real completion (hides +
    // toasts once), then simulates the user manually reopening the panel afterwards (as the finding
    // describes) followed by a later snapshot tick that still carries the SAME already-handled
    // terminal reason (e.g. a trailing cost/entries update) — this must NOT re-fire.
    @Test fun terminalReplay_doesNotRefireHideOrToast() = runTest {
        val f = Fake(visiblePref = false, autoHide = true); val h = HostCalls(); val c = controller(f, h)
        f.snap.value = AgentLogSnapshot(running = true)
        f.snap.value = AgentLogSnapshot(running = false, lastStopReason = AgentStopReasonVd.COMPLETED)
        assertFalse(c.state.value.visible)
        assertEquals(1, h.toast)

        // user manually reopens the log after the auto-hide
        c.show()
        assertTrue(c.state.value.visible)
        val setVisibleCallsAfterReopen = f.setVisibleCallCount

        // a later snapshot still carrying the SAME terminal reason (different other field so the
        // StateFlow actually re-emits) must not re-hide/re-toast the manually-reopened panel
        f.snap.value = AgentLogSnapshot(running = false, lastStopReason = AgentStopReasonVd.COMPLETED, headerCost = "$0.03")
        assertTrue(c.state.value.visible)
        assertEquals(1, h.toast)
        assertEquals(setVisibleCallsAfterReopen, f.setVisibleCallCount)
    }

    // The construction-time variant of the same bug: a *fresh* AgentLogController subscribing to a
    // snapshot flow whose CURRENT value already carries a non-error terminal reason (e.g. host
    // recreated on rotation) must not treat that replayed initial value as a new transition, even
    // though the persisted pref says the panel is visible (the exact scenario Finding 1 describes).
    @Test fun terminalReplay_atConstruction_doesNotAutoHideOrToast() = runTest {
        val f = Fake(visiblePref = true, autoHide = true)
        f.snap.value = AgentLogSnapshot(running = false, lastStopReason = AgentStopReasonVd.COMPLETED)
        val h = HostCalls()
        val c = controller(f, h)
        assertTrue(c.state.value.visible)
        assertEquals(0, h.toast)
    }

    @Test fun snapshot_isReflectedInState() = runTest {
        val f = Fake(); val c = controller(f)
        val entries = listOf(AgentLogEntryVd("e1", LogEntryKind.INFO, LogEntryStatus.COMPLETED, "hi"))
        f.snap.value = AgentLogSnapshot(running = true, entries = entries, headerCost = "$0.02")
        assertEquals(entries, c.state.value.snapshot.entries)
        assertEquals("$0.02", c.state.value.snapshot.headerCost)
    }

    @Test fun toggleExpanded_flips() = runTest {
        val c = controller(Fake())
        assertFalse(c.state.value.expanded)
        c.toggleExpanded(); assertTrue(c.state.value.expanded)
        c.toggleExpanded(); assertFalse(c.state.value.expanded)
    }

    @Test fun hide_persistsPref() = runTest {
        val f = Fake(visiblePref = true); val c = controller(f)
        c.hide()
        assertFalse(c.state.value.visible)
        assertEquals(false, f.lastSetVisible)
    }

    @Test fun stop_routesToService() = runTest {
        val f = Fake(); val c = controller(f)
        c.stop(); assertEquals(1, f.stopCount)
    }

    @Test fun modelSelector_loadsAndChoosesDefault() = runTest {
        val f = Fake(models = listOf(ReadingModelVd("m1", "gpt-4o", "OpenAI", true, true)))
        val c = controller(f)
        c.onModelSelectorClick()
        assertEquals(1, c.state.value.modelPicker?.size)
        c.onModelChosen("m1")
        assertEquals("m1", f.lastDefault)
        assertNull(c.state.value.modelPicker)
    }

    @Test fun modelPickerDismiss_clears() = runTest {
        val f = Fake(models = listOf(ReadingModelVd("m1", "gpt-4o", "OpenAI", true, true)))
        val c = controller(f)
        c.onModelSelectorClick(); assertNotNull(c.state.value.modelPicker)
        c.onModelPickerDismiss(); assertNull(c.state.value.modelPicker)
    }

    @Test fun rawLogClick_forwardsToHost() = runTest {
        val h = HostCalls(); val c = controller(Fake(), h)
        c.onRawLogClick(); assertEquals(1, h.rawLog)
    }

    @Test fun drag_upFromCollapsedExpandsAndRecordsTheHeight() = runTest {
        val c = controller(Fake(visiblePref = true))
        c.onHeightDrag(dragUpDp = 120f, collapsedDp = 48f, maxDp = 600f)

        assertTrue(c.state.value.expanded)
        assertEquals(168f, c.state.value.heightDp)
    }

    @Test fun drag_upIsClampedByTheMaximum() = runTest {
        val c = controller(Fake(visiblePref = true))
        c.onHeightDrag(dragUpDp = 5000f, collapsedDp = 48f, maxDp = 300f)

        assertEquals(300f, c.state.value.heightDp)
    }

    @Test fun drag_downIntoTheSnapZoneCollapsesButKeepsTheHeight() = runTest {
        val c = controller(Fake(visiblePref = true))
        c.onHeightDrag(dragUpDp = 300f, collapsedDp = 48f, maxDp = 600f)
        val dragged = c.state.value.heightDp

        c.onHeightDrag(dragUpDp = -1000f, collapsedDp = 48f, maxDp = 600f)

        assertFalse(c.state.value.expanded)
        assertEquals(dragged, c.state.value.heightDp, "collapsing is not closing: the height survives")
    }

    @Test fun toggleExpanded_keepsTheDraggedHeight() = runTest {
        val c = controller(Fake(visiblePref = true))
        c.onHeightDrag(dragUpDp = 200f, collapsedDp = 48f, maxDp = 600f)
        val dragged = c.state.value.heightDp

        c.toggleExpanded()
        c.toggleExpanded()

        assertEquals(dragged, c.state.value.heightDp)
    }

    @Test fun hide_forgetsTheDraggedHeight() = runTest {
        val c = controller(Fake(visiblePref = true))
        c.onHeightDrag(dragUpDp = 200f, collapsedDp = 48f, maxDp = 600f)

        c.hide()

        assertNull(c.state.value.heightDp, "a reopened panel must start at the default height")
    }

    @Test fun autoHide_forgetsTheDraggedHeight() = runTest {
        val f = Fake(visiblePref = false, autoHide = true)
        val c = controller(f)
        f.snap.value = AgentLogSnapshot(running = true)
        c.onHeightDrag(dragUpDp = 200f, collapsedDp = 48f, maxDp = 600f)
        assertEquals(248f, c.state.value.heightDp, "sanity")

        f.snap.value = AgentLogSnapshot(running = false, lastStopReason = AgentStopReasonVd.COMPLETED)

        assertFalse(c.state.value.visible)
        assertNull(c.state.value.heightDp, "an auto-hide is a close too")
    }
}
