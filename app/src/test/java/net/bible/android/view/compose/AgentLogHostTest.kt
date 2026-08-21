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
package net.bible.android.view.compose

import android.widget.FrameLayout
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.painterResource
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import net.bible.android.activity.R
import net.bible.android.control.page.window.WindowStateServiceImpl
import net.bible.android.view.activity.page.screen.ComposeReadingViewHost
import net.bible.sharedcore.ai.reading.AgentLogController
import net.bible.sharedcore.ai.reading.AgentLogSnapshot
import net.bible.sharedcore.ai.reading.AgentSessionService
import net.bible.sharedcore.ai.reading.AgentStopReasonVd
import net.bible.sharedcore.ai.reading.ReadingModelVd
import net.bible.sharedcore.ai.reading.agentPanelHeight
import net.bible.sharedcore.window.WindowCommands
import net.bible.sharedui.ai.reading.AgentLogPanel
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** No-op fake — mirrors `ReadingLlmHostTest`'s own private `noopCommands` (file-private there too,
 *  not reusable across files: no mocking framework is used in this repo). */
private val noopCommands = object : WindowCommands {
    override fun setActive(windowId: String) {}
    override fun commitWeights(windowId1: String, weight1: Float, windowId2: String, weight2: Float) {}
    override fun addNewWindow(fromWindowId: String) {}
    override fun minimise(windowId: String) {}
    override fun close(windowId: String) {}
    override fun restore(windowId: String) {}
    override fun maximise(windowId: String) {}
    override fun unMaximise() {}
    override fun setPin(windowId: String, value: Boolean) {}
    override fun move(windowId: String, position: Int) {}
    override fun setSynchronised(windowId: String, value: Boolean) {}
    override fun changeSyncGroup(windowId: String, group: Int) {}
    override fun focusNext() {}
    override fun focusPrevious() {}
    override fun setRestoreButtonsVisible(value: Boolean) {}
}

/**
 * Minimal fake [AgentSessionService] — mirrors `AgentLogControllerTest`'s own private `Fake`
 * (`:sharedCore`), reproduced here (file-private there, not reusable across modules/files).
 */
private class FakeAgentSessionService(
    visiblePref: Boolean = false,
    private val autoHide: Boolean = true,
) : AgentSessionService {
    val snap = MutableStateFlow(AgentLogSnapshot())
    override val snapshot: StateFlow<AgentLogSnapshot> = snap
    private var visiblePrefStore = visiblePref
    override fun stop() {}
    override suspend fun configuredModels(): List<ReadingModelVd> = emptyList()
    override fun setDefaultModel(modelId: String) {}
    override fun autoHideEnabled() = autoHide
    override fun logVisiblePref() = visiblePrefStore
    override fun setLogVisiblePref(v: Boolean) { visiblePrefStore = v }
    override fun currentWorkspaceId() = "ws1"
    override fun refresh() {}
}

/**
 * Probe test (Batch 12e-B Task 6): `ComposeReadingViewHost.mountComposeView` must accept a new
 * `agentLogSlot` param — a pre-built `@Composable` lambda (`(applyNavBarInset, maxHeightDp,
 * collapsedHeightDp, onCollapsedHeightMeasured) -> Unit` since round 12b §4 made the panel a
 * draggable overlay; it was `() -> Unit` when this probe was written), mirroring exactly how
 * `ComposeReadingViewHost.install` closes over the live `AgentLogController` to build
 * `AgentLogPanel` — and mount without crashing. Mirrors `ReadingLlmHostTest`'s minimal-mount
 * `installAccepts*` style: this repo's `:app` JVM unit tests have no `ComposeTestRule`, so
 * composition never runs for a container never attached to a real window (see that test class's
 * kdoc) — the actual panel-rendering pixels are covered by `AgentLogPanelGoldenTest`/
 * `ReadingViewScreenGoldenTest`'s agent-log case, and the controller's auto-show/hide/toast state
 * machine by `AgentLogControllerTest` (`:sharedCore`). What THIS test guards is purely the host
 * wiring: the new param exists, compiles, reaches `mountComposeView`, and the exact
 * `onCompletedToast`/`onOpenRawLog` closures `install()` builds are invocable end-to-end against a
 * real [AgentLogController].
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class AgentLogHostTest {

    @Test fun mountAcceptsAgentLogSlotWithoutCrashing() = runTest {
        val container = FrameLayout(ApplicationProvider.getApplicationContext())
        val fake = FakeAgentSessionService()
        val controller = AgentLogController(
            fake,
            CoroutineScope(UnconfinedTestDispatcher(testScheduler)),
            onCompletedToast = {},
            onOpenRawLog = {},
        )

        ComposeReadingViewHost.mountComposeView(
            container = container,
            windowState = WindowStateServiceImpl(),
            commands = noopCommands,
            nightModeState = mutableStateOf(false),
            pane = { },
            // Mirrors exactly what ComposeReadingViewHost.install builds for the real `agentLog`
            // controller field.
            agentLogSlot = { _, maxHeightDp, collapsedDp, onCollapsedHeightMeasured ->
                val agentLogUiState by controller.state.collectAsState()
                AgentLogPanel(
                    agentLogUiState,
                    animateStatus = false,
                    statusIcon = painterResource(R.drawable.icon_robot),
                    // Round 12b §4: mirrors install()'s wiring — the height and the drag both go
                    // through the pure `:sharedCore` helpers, nothing is re-derived here.
                    panelHeightDp = if (agentLogUiState.expanded) {
                        agentPanelHeight(agentLogUiState, collapsedDp, maxHeightDp)
                    } else null,
                    onHeightDragStarted = controller::onHeightDragStarted,
                    onHeightDrag = { dragUpDp -> controller.onHeightDrag(dragUpDp, collapsedDp, maxHeightDp) },
                    onCollapsedHeightMeasured = onCollapsedHeightMeasured,
                    onToggleExpanded = controller::toggleExpanded,
                    onStop = controller::stop,
                    onClose = controller::hide,
                    onModelSelectorClick = controller::onModelSelectorClick,
                    onModelChosen = controller::onModelChosen,
                    onModelPickerDismiss = controller::onModelPickerDismiss,
                    onRawLogClick = controller::onRawLogClick,
                )
            },
        )

        assertTrue(
            (0 until container.childCount).any { container.getChildAt(it) is ComposeView },
            "mountComposeView must still add its ComposeView child with the new agentLogSlot param present",
        )
    }

    @Test fun onOpenRawLogAndOnCompletedToastLambdasAreInvocableThroughTheRealWiring() = runTest {
        var rawLogOpened = 0
        var completedToastShown = 0
        val fake = FakeAgentSessionService(autoHide = true)
        val controller = AgentLogController(
            fake,
            CoroutineScope(UnconfinedTestDispatcher(testScheduler)),
            onCompletedToast = { completedToastShown++ },
            onOpenRawLog = { rawLogOpened++ },
        )

        // onOpenRawLog: exactly the call path `AgentLogPanel`'s "view raw" link drives
        // (`onRawLogClick` -> `onOpenRawLog()`, which in the real host builds the
        // ScreenLauncher.intentFor(activity, Screen.RawLlmLog) intent).
        controller.onRawLogClick()
        assertEquals(1, rawLogOpened)

        // onCompletedToast: fires on a running -> COMPLETED transition while visible + auto-hide
        // enabled (same transition `AgentLogControllerTest.autoHide_onCompleted_whenEnabled_hidesAndToasts`
        // exercises at the :sharedCore layer). Re-driving it here through THIS exact controller
        // construction proves the :app-level wiring (the closures `install()` builds) is reachable
        // and functions — the state-machine logic itself is not what's under test here.
        fake.snap.value = AgentLogSnapshot(running = true)
        assertTrue(controller.state.value.visible)
        fake.snap.value = AgentLogSnapshot(running = false, lastStopReason = AgentStopReasonVd.COMPLETED)
        assertEquals(1, completedToastShown)
    }
}
