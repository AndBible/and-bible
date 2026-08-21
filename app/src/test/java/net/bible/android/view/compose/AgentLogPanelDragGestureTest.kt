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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import net.bible.android.TEST_SDK
import net.bible.android.activity.R
import net.bible.service.common.DisplayColorMode
import net.bible.sharedcore.ai.reading.AgentLogSnapshot
import net.bible.sharedcore.ai.reading.AgentLogUiState
import net.bible.sharedcore.ai.reading.agentPanelHeight
import net.bible.sharedui.ProvideAppLocals
import net.bible.sharedui.ai.reading.AGENT_LOG_DRAG_HANDLE_TAG
import net.bible.sharedui.ai.reading.AgentLogPanel
import net.bible.sharedui.theme.AbTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The one thing the pure `:sharedCore` reducer tests cannot see: that the handle's `draggable` and
 * `clickable` are actually WIRED to the reducer — including `onDragStarted`, whose absence is what
 * made a drag-to-collapse forget the user's height (whole-branch review, Blocker 1).
 *
 * Drives the real [AgentLogPanel] through [AGENT_LOG_DRAG_HANDLE_TAG], the same idiom
 * [net.bible.android.view.compose.SearchFieldCaretTest] uses for the reading toolbar's search field,
 * so it cannot pass against a copy while the real handle stays inert.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class AgentLogPanelDragGestureTest {
    @get:Rule val compose = createComposeRule()

    private val collapsedDp = 48f
    private val maxDp = 600f

    /** The reducer, driven by the real panel — the same arithmetic `AgentLogController` runs. */
    private class Recorder {
        val holder = mutableStateOf(
            AgentLogUiState(
                visible = true, expanded = true,
                snapshot = AgentLogSnapshot(running = true, statusText = "Reading John 3"),
            )
        )
        var state: AgentLogUiState
            get() = holder.value
            set(v) { holder.value = v }
        var starts = 0
        var steps = 0
        var clicks = 0
        var heightAtStart: Float? = null
    }

    @Composable
    private fun PanelUnderTest(r: Recorder, onState: (AgentLogUiState) -> Unit) {
        ProvideAppLocals {
            AbTheme(darkTheme = false, colorMode = DisplayColorMode.NORMAL, disableAnimations = true) {
                Box(Modifier.height(700.dp)) {
                    AgentLogPanel(
                        r.state,
                        animateStatus = false,
                        statusIcon = painterResource(R.drawable.icon_robot),
                        panelHeightDp = if (r.state.expanded) {
                            agentPanelHeight(r.state, collapsedDp, maxDp)
                        } else null,
                        onHeightDragStarted = {
                            r.starts++
                            r.heightAtStart = r.state.heightDp
                        },
                        onHeightDrag = { dragUpDp ->
                            r.steps++
                            val from = agentPanelHeight(r.state, collapsedDp, maxDp)
                            onState(r.state.copy(expanded = true, heightDp = from + dragUpDp))
                        },
                        onCollapsedHeightMeasured = {},
                        onToggleExpanded = { r.clicks++ },
                        onStop = {}, onClose = {},
                        onModelSelectorClick = {}, onModelChosen = {}, onModelPickerDismiss = {},
                        onRawLogClick = {},
                    )
                }
            }
        }
    }

    @Test
    fun aMultiEventDragOnTheHandleResizesThePanelAndReportsTheGestureStart() {
        val r = Recorder()
        compose.setContent { PanelUnderTest(r) { r.state = it } }

        compose.onNodeWithTag(AGENT_LOG_DRAG_HANDLE_TAG).performTouchInput {
            down(center)
            moveBy(Offset(0f, -80f))
            moveBy(Offset(0f, -80f))
            up()
        }
        compose.waitForIdle()

        assertEquals(1, r.starts, "onDragStarted must fire exactly once per gesture")
        assertTrue(r.steps >= 2, "a multi-event drag must reach the reducer more than once, got ${r.steps}")
        assertTrue(
            (r.state.heightDp ?: 0f) > agentPanelHeight(AgentLogUiState(visible = true, expanded = true), collapsedDp, maxDp),
            "dragging up must have grown the panel, got ${r.state.heightDp}",
        )
        assertEquals(0, r.clicks, "a drag is not a tap")
    }

    @Test
    fun aTapOnTheHandleCollapsesThePanel() {
        val r = Recorder()
        compose.setContent { PanelUnderTest(r) { r.state = it } }

        compose.onNodeWithTag(AGENT_LOG_DRAG_HANDLE_TAG).performClick()
        compose.waitForIdle()

        assertEquals(1, r.clicks, "a tap must reach onToggleExpanded")
        assertEquals(0, r.steps, "a tap is not a drag")
    }
}
