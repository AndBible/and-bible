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

package net.bible.sharedcore.ai

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The user's answer to a tool-permission request. 1:1 mirror of `Dialogs.AgentPermissionResult`. */
enum class AgentPermissionChoice {
    /** Allow this one operation. */
    ALLOW,
    /** Allow this tool for the rest of this agent session. */
    ALLOW_FOR_SESSION,
    /** Allow all tools for the rest of this agent session. */
    ALLOW_ALL_SESSION,
    /** Permanently allow this tool (the caller still shows a confirmation before persisting). */
    ALLOW_ALWAYS,
    /** Deny this operation. Also the answer for a cancelled or undeliverable prompt. */
    DENY,
}

/** What the dialog needs to show. [actionDescription] is null when the tool cannot describe itself. */
data class AgentPermissionRequest(
    val toolDisplayName: String,
    val toolDescription: String,
    val actionDescription: String?,
)

/**
 * Bridges a background agent coroutine's suspending permission request to a Compose dialog.
 *
 * `AgentExecutor` awaits each tool call before issuing the next, so at most one request can be
 * outstanding. A second concurrent request is therefore a defect rather than a normal case: it is
 * answered [AgentPermissionChoice.DENY] immediately (never shown, so never approved) and the
 * already-visible request is left alone — deliberately NOT thrown, because a dropped prompt must
 * not crash an agent run.
 */
class AgentPermissionController {

    private val mutablePending = MutableStateFlow<AgentPermissionRequest?>(null)

    /** The request currently on screen, or null. The host renders the dialog iff this is non-null. */
    val pending: StateFlow<AgentPermissionRequest?> = mutablePending.asStateFlow()

    private var answer: CompletableDeferred<AgentPermissionChoice>? = null

    /** Publishes [request] and suspends until [respond]/[dismiss] answers it. */
    suspend fun await(request: AgentPermissionRequest): AgentPermissionChoice {
        if (mutablePending.value != null) return AgentPermissionChoice.DENY
        val deferred = CompletableDeferred<AgentPermissionChoice>()
        answer = deferred
        mutablePending.value = request
        return try {
            deferred.await()
        } finally {
            mutablePending.value = null
            answer = null
        }
    }

    /** Answers the outstanding request. No-op when nothing is pending. */
    fun respond(choice: AgentPermissionChoice) {
        answer?.complete(choice)
    }

    /** Cancel / back / scrim tap — parity with the classic `setOnCancelListener` resuming DENY. */
    fun dismiss() = respond(AgentPermissionChoice.DENY)
}
