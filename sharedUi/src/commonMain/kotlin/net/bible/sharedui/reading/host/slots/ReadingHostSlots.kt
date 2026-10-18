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
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package net.bible.sharedui.reading.host.slots

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import net.bible.android.view.activity.page.screen.speakBarVisible
import net.bible.sharedcore.ai.reading.AgentLogController
import net.bible.sharedcore.reading.speakBarOwnsTopEdge
import net.bible.sharedcore.speak.SpeakTransportController
import net.bible.sharedui.app.ports.ExternalLinks
import net.bible.sharedui.reading.SpeakTransportBar
import net.bible.sharedui.reading.host.ReadingHostState
import net.bible.sharedui.strings.Strings

/**
 * The reading view's host-built slots (the `@Composable` lambdas `ReadingViewScreen` takes), in
 * common code. A platform host builds one instance and hands `{ slots.X() }` lambdas to the screen,
 * so Android and iOS share the slot bodies. Every collaborator is a constructor parameter or lambda
 * (no Koin, no platform types); [strings] and [disableAnimations] are lambdas so a late change
 * (locale, the animations preference) is read on each composition. [fullScreen] is read inside
 * composition, so passing a `State`'s getter makes the speak bar recompose when it flips.
 *
 * The agent log's glyph comes from [LocalAgentStatusIcon]; `AgentLogController` is built by the
 * host (its raw-log action starts a platform activity).
 */
@kotlin.native.HiddenFromObjC
class ReadingHostSlots(
    val hostState: ReadingHostState,
    val strings: () -> Strings,
    val externalLinks: ExternalLinks,
    val agentLog: AgentLogController,
    val speakTransport: SpeakTransportController,
    val disableAnimations: () -> Boolean,
    val fullScreen: () -> Boolean,
) {
    /** The reading view's own dialogs (help text, delete-page confirmation). */
    @Composable
    fun ReadingDialog() = ReadingDialogContent(hostState, strings(), externalLinks)

    /** The agent-log panel; same parameters as `ReadingViewScreen`'s `agentLogSlot`. */
    @Composable
    fun AgentLog(
        applyNavBarInset: Boolean,
        maxHeightDp: Float,
        collapsedDp: Float,
        onCollapsedHeightMeasured: (Float) -> Unit,
    ) = AgentLogSlotContent(
        agentLog, disableAnimations(), applyNavBarInset, maxHeightDp, collapsedDp, onCollapsedHeightMeasured,
    )

    /** The Speak transport bar, hidden in full screen and while the transport is not visible. */
    @Composable
    fun SpeakBar(applyNavBarInset: Boolean) {
        val speakState by speakTransport.state.collectAsState()
        // The agent panel's visibility, narrowed to a boolean through `derivedStateOf`: collecting
        // `agentLog.state` raw here would resubscribe this slot to every log line, status change and
        // cost update of a running agent, when all it needs is the one flip.
        val agentLogState = agentLog.state.collectAsState()
        val agentLogVisible = remember { derivedStateOf { agentLogState.value.visible } }
        if (speakBarVisible(fullScreen = fullScreen(), transportVisible = speakState.visible)) {
            SpeakTransportBar(
                speakState,
                onPlayPause = { speakTransport.togglePlayPause() },
                onStop = { speakTransport.stop() },
                onRewind = { speakTransport.rewind() },
                onForward = { speakTransport.forward() },
                onPrev = { speakTransport.prevVerse() },
                onNext = { speakTransport.nextVerse() },
                onBookmark = { speakTransport.onBookmarkButton() },
                onConfig = { speakTransport.onConfig() },
                applyNavBarInset = applyNavBarInset,
                ownsTopEdge = speakBarOwnsTopEdge(agentLogVisible.value),
            )
        }
    }
}
