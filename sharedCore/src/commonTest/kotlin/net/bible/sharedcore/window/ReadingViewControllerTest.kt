package net.bible.sharedcore.window

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class ReadingViewControllerTest {
    private class FakeService(val flow: MutableStateFlow<WindowLayoutState>) : WindowStateService {
        override val layout: StateFlow<WindowLayoutState> get() = flow
    }
    private class RecordingCommands : WindowCommands {
        val activated = mutableListOf<String>()
        val weights = mutableListOf<List<Any>>()
        val calls = mutableListOf<Pair<String, List<Any?>>>()
        override fun setActive(windowId: String) { activated += windowId }
        override fun commitWeights(windowId1: String, weight1: Float, windowId2: String, weight2: Float) {
            weights += listOf(windowId1, weight1, windowId2, weight2)
        }
        override fun addNewWindow(fromWindowId: String) { calls += "addNewWindow" to listOf(fromWindowId) }
        override fun minimise(windowId: String) { calls += "minimise" to listOf(windowId) }
        override fun close(windowId: String) { calls += "close" to listOf(windowId) }
        override fun restore(windowId: String) { calls += "restore" to listOf(windowId) }
        override fun maximise(windowId: String) { calls += "maximise" to listOf(windowId) }
        override fun unMaximise() { calls += "unMaximise" to listOf() }
        override fun setPin(windowId: String, value: Boolean) { calls += "setPin" to listOf(windowId, value) }
        override fun move(windowId: String, position: Int) { calls += "move" to listOf(windowId, position) }
        override fun setSynchronised(windowId: String, value: Boolean) { calls += "setSynchronised" to listOf(windowId, value) }
        override fun changeSyncGroup(windowId: String, group: Int) { calls += "changeSyncGroup" to listOf(windowId, group) }
        override fun focusNext() { calls += "focusNext" to listOf() }
        override fun focusPrevious() { calls += "focusPrevious" to listOf() }
        override fun setRestoreButtonsVisible(value: Boolean) { calls += "setRestoreButtonsVisible" to listOf(value) }
    }

    @Test fun exposesServiceLayoutFlow() {
        val flow = MutableStateFlow(WindowLayoutState.EMPTY)
        val ctl = ReadingViewController(FakeService(flow), RecordingCommands())
        assertSame(flow, ctl.layout)
    }

    @Test fun activationDelegatesToCommands() {
        val cmds = RecordingCommands()
        val ctl = ReadingViewController(FakeService(MutableStateFlow(WindowLayoutState.EMPTY)), cmds)
        ctl.onWindowActivated("win-1")
        assertEquals(listOf("win-1"), cmds.activated)
    }

    @Test fun separatorCommitDelegatesToCommands() {
        val cmds = RecordingCommands()
        val ctl = ReadingViewController(FakeService(MutableStateFlow(WindowLayoutState.EMPTY)), cmds)
        ctl.onSeparatorCommitted("a", 1.5f, "b", 0.5f)
        assertEquals(listOf<Any>("a", 1.5f, "b", 0.5f), cmds.weights.single())
    }

    @Test fun windowMgmtCommandsDelegate() {
        val cmds = RecordingCommands()
        val ctl = ReadingViewController(FakeService(MutableStateFlow(WindowLayoutState.EMPTY)), cmds)
        ctl.onAddWindow("w1"); ctl.onMinimise("w2"); ctl.onClose("w3"); ctl.onRestore("w4")
        ctl.onMaximise("w5"); ctl.onUnMaximise(); ctl.onSetPin("w6", true); ctl.onMove("w7", 2)
        ctl.onSetSynchronised("w8", false); ctl.onChangeSyncGroup("w9", 3)
        ctl.onFocusNext(); ctl.onFocusPrevious(); ctl.onSetRestoreButtonsVisible(true)
        assertEquals(
            listOf(
                "addNewWindow" to listOf<Any?>("w1"), "minimise" to listOf<Any?>("w2"),
                "close" to listOf<Any?>("w3"), "restore" to listOf<Any?>("w4"),
                "maximise" to listOf<Any?>("w5"), "unMaximise" to listOf<Any?>(),
                "setPin" to listOf<Any?>("w6", true), "move" to listOf<Any?>("w7", 2),
                "setSynchronised" to listOf<Any?>("w8", false), "changeSyncGroup" to listOf<Any?>("w9", 3),
                "focusNext" to listOf<Any?>(), "focusPrevious" to listOf<Any?>(),
                "setRestoreButtonsVisible" to listOf<Any?>(true),
            ),
            cmds.calls,
        )
    }
}
