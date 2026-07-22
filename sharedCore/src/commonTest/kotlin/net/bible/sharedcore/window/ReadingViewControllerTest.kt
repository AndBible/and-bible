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
        override fun setActive(windowId: String) { activated += windowId }
        override fun commitWeights(windowId1: String, weight1: Float, windowId2: String, weight2: Float) {
            weights += listOf(windowId1, weight1, windowId2, weight2)
        }
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
}
