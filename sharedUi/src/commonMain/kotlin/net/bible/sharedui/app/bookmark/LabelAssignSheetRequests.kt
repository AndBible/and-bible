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

package net.bible.sharedui.app.bookmark

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** How one label-assign sheet ended (issue #4201). Each carries a `ManageLabelsData` JSON payload. */
@kotlin.native.HiddenFromObjC
sealed interface LabelAssignOutcome {
    /** The sheet closed and saved: [data] is the label manager's result payload, as the full screen delivers it. */
    data class Assigned(val data: String) : LabelAssignOutcome

    /** "Edit labels": the sheet saved its working state into [data]; open the full-screen ASSIGN list with it. */
    data class Expand(val data: String) : LabelAssignOutcome

    /** The sheet was taken down without an answer (its requester was cancelled). */
    data object Cancelled : LabelAssignOutcome
}

/** Opens the assign sheet with an ASSIGN-mode payload and waits for how it ended. */
typealias LabelAssignSheetOpener = suspend (data: String) -> LabelAssignOutcome

/**
 * The one open label-assign sheet, as a suspend call. [request] publishes [current]; the host renders it
 * (`BookmarkHost.Overlays`, above the NavHost, so the sheet covers the bookmark list and the reading
 * view alike) and calls [Request.finish] once. A new request replaces an open one, which ends Cancelled.
 * Main thread only, like the composition that reads [current].
 */
@kotlin.native.HiddenFromObjC
class LabelAssignSheetRequests {
    /** One open sheet: its payload and the continuation it answers. */
    class Request internal constructor(val data: String, private val onFinish: (LabelAssignOutcome) -> Unit) {
        private var finished = false

        /** Answers the request; later calls are ignored. */
        fun finish(outcome: LabelAssignOutcome) {
            if (finished) return
            finished = true
            onFinish(outcome)
        }
    }

    var current: Request? by mutableStateOf(null)
        private set

    suspend fun request(data: String): LabelAssignOutcome {
        current?.finish(LabelAssignOutcome.Cancelled)
        var request: Request? = null
        try {
            return suspendCancellableCoroutine { cont ->
                val open = Request(data) { outcome -> if (cont.isActive) cont.resume(outcome) }
                request = open
                current = open
            }
        } finally {
            // On the requester's own thread (Main), also when it was cancelled.
            if (current === request) current = null
        }
    }
}

/**
 * The label manager's answer for one ASSIGN request, the sheet first: [sheet] opens the assign sheet
 * (null when this host has none: the full screen is used as before), [fullScreen] opens the
 * full-screen list with a payload and returns its result payload, or null when it was left without one.
 * Returns the result payload, or null for no answer.
 */
@kotlin.native.HiddenFromObjC
suspend fun resolveLabelAssign(
    data: String,
    sheet: LabelAssignSheetOpener?,
    fullScreen: suspend (String) -> String?,
): String? = when (val outcome = sheet?.invoke(data)) {
    null -> fullScreen(data)
    is LabelAssignOutcome.Assigned -> outcome.data
    is LabelAssignOutcome.Expand -> fullScreen(outcome.data)
    LabelAssignOutcome.Cancelled -> null
}
