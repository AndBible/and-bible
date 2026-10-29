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

package net.bible.sharedui.host.reading

import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.bible.android.database.IdType
import net.bible.android.view.activity.page.Selection
import net.bible.service.llm.AgentPrompt
import net.bible.service.llm.PromptRepository
import net.bible.service.llm.agent.AgentSessionManager
import net.bible.sharedcore.log.nsLog
import kotlin.coroutines.CoroutineContext

/**
 * Starts an AI agent run for the reading view, as Android's `AgentForegroundService.START_AGENT` does: looks the prompt up and
 * runs [AgentSessionManager.executePrompt] with the prompt, the selection, the user's specification and the model override.
 *
 * iOS has no foreground service, so the run sits inside a UIKit background-task window ([beginBackgroundTask] /
 * [endBackgroundTask]): a short app switch does not kill it. When iOS expires the window the run's agent session is stopped
 * (Android's cancel action) and the run cancelled, because the app is about to be suspended; nothing is left half-running.
 * The progress, the result window and the failure messages are the session manager's own, shown by the agent log.
 *
 * The collaborators are constructor parameters only so a test can replace them; the defaults are the production ones, except
 * the background-task calls, which the iOS entry point supplies (UIKit, see `iosBeginAgentBackgroundTask`).
 *
 * @param windowRepositoryId the current workspace id, read when a run starts; the run's agent session is the one keyed by it
 */
@kotlin.native.HiddenFromObjC
class IosAgentStarter(
    private val scope: CoroutineScope,
    private val windowRepositoryId: () -> IdType,
    private val promptById: suspend (IdType) -> AgentPrompt? = PromptRepository::promptById,
    private val execute: suspend (AgentPrompt, Selection, String?, IdType?) -> Boolean = { prompt, selection, spec, model ->
        AgentSessionManager.executePrompt(prompt, selection, userSpecification = spec, modelOverrideId = model)
    },
    private val regenerate: suspend (IdType, IdType?, String?, Boolean, Boolean, IdType?) -> Boolean =
        { pageId, targetWindowId, instructions, keepPrevious, freshRun, model ->
            AgentSessionManager.regenerateAIDocument(
                pageId, targetWindowId = targetWindowId, additionalInstructions = instructions,
                keepPrevious = keepPrevious, freshRun = freshRun, modelOverrideId = model,
            )
        },
    /** Stops the workspace's agent session; called on the main thread from the expiry handler, so it must not block (it does not: it cancels the run's job and logs). */
    private val stopAgent: (IdType) -> Unit = AgentSessionManager::stopAgent,
    private val beginBackgroundTask: (onExpire: () -> Unit) -> ULong,
    private val endBackgroundTask: (ULong) -> Unit,
    /** The identifier [beginBackgroundTask] returns when no task was granted (UIKit's `invalidTaskId`); it is never ended. */
    private val invalidTaskId: ULong = ULong.MAX_VALUE,
    /** Where the run executes; the agent does model and database work, so not the main thread (Android: `Dispatchers.IO`). */
    private val runContext: CoroutineContext = Dispatchers.Default,
) {
    /**
     * Starts the run and returns at once. A prompt that no longer exists, or a failing run, is logged and ends quietly
     * (Android's service does the same); the background task always ends.
     */
    fun start(promptId: IdType, selection: Selection, userSpecification: String?, modelOverrideId: IdType?) =
        launchRun {
            val prompt = promptById(promptId)
            if (prompt == null) {
                nsLog("IosAgentStarter: prompt not found: $promptId")
            } else {
                execute(prompt, selection, userSpecification, modelOverrideId)
            }
        }

    /** Android's `AgentForegroundService.START_REGENERATE`: regenerates the AI page [pageId], showing the result in [targetWindowId]. */
    fun startRegenerate(
        pageId: IdType, targetWindowId: IdType?, additionalInstructions: String?, keepPrevious: Boolean, freshRun: Boolean,
        modelOverrideId: IdType?,
    ) = launchRun { regenerate(pageId, targetWindowId, additionalInstructions, keepPrevious, freshRun, modelOverrideId) }

    /**
     * Runs [body] in the background-task window. The workspace is read now: it is the one an expiry stops. A cancellation of
     * the run by anything else needs no extra stop, `executePrompt` stops its own session when it is cancelled.
     */
    private fun launchRun(body: suspend () -> Unit) {
        val workspaceId = windowRepositoryId()
        val lock = SynchronizedObject()
        var identifier: ULong? = null
        var expired = false
        var ended = false
        var job: Job? = null

        // UIKit terminates an app whose expiration handler returns with the task still open, so the handler itself ends it;
        // the run's own end and the handler race, hence exactly once.
        fun endOnce() = synchronized(lock) {
            val id = identifier ?: return@synchronized
            if (ended) return@synchronized
            ended = true
            if (id != invalidTaskId) endBackgroundTask(id)
        }

        fun onExpire() {
            val running = synchronized(lock) { expired = true; job }
            try {
                if (running != null && !running.isCompleted) {
                    nsLog("IosAgentStarter: background time expired; stopping the agent of workspace $workspaceId")
                    stopAgent(workspaceId)
                    running.cancel()
                }
            } finally {
                endOnce()
            }
        }

        val id = beginBackgroundTask { onExpire() }
        synchronized(lock) { identifier = id }
        // LAZY, like the Android service (F123): the job is known to the expiry handler before the run can end.
        val run = scope.launch(runContext, start = CoroutineStart.LAZY) {
            try {
                body()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                nsLog("IosAgentStarter: agent execution failed: ${e.stackTraceToString()}")
            } finally {
                endOnce()
            }
        }
        // Expiry can arrive inside beginBackgroundTask, before UIKit returned the identifier: then nothing runs.
        val expiredAlready = synchronized(lock) { job = run; expired }
        if (expiredAlready) {
            run.cancel()
            endOnce()
            return
        }
        run.start()
    }
}
