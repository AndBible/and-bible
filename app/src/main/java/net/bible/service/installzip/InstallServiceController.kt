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

package net.bible.service.installzip

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap

/**
 * App-scoped orchestrator for headless zip installs: holds the job queue, exposes it as a
 * [StateFlow] for a UI/notification to observe, drives each queued [InstallSource] through
 * [InstallJobRunner] sequentially, and bridges the ask-back [DecisionRequest]s the state machine
 * pauses on ([InstallPhase.AwaitingDecision]) to external [resolveDecision]/[cancel] calls (a
 * dialog button, a notification action, ...).
 *
 * **Cancellation-safety.** Workers run on a controller-owned [SupervisorJob] scope, entirely
 * separate from whatever coroutine calls [enqueue]. So if the caller's coroutine is cancelled
 * (e.g. a Fragment/ViewModel scope torn down while installs are still running), only the
 * *awaiting* of the aggregate [InstallOutcome] stops -- the queued jobs are completely unaffected,
 * keep running to their terminal phase, and [onTerminal] still fires for each. This mirrors how a
 * real Android [android.app.Service] must behave: it must keep installing after the Activity/UI
 * that started it goes away.
 */
class InstallServiceController(
    private val runnerFactory: () -> InstallJobRunner,
    private val newJobId: () -> JobId,
    private val onTerminal: (jobId: JobId, phase: InstallPhase) -> Unit,
) {
    /** Data the runner needs per job, provided by the Service (Android-bound lambdas). */
    class JobDeps(
        val acquireDir: File,
        val openStream: suspend (InstallSource) -> InputStream,
        val isStudyPadExport: suspend (InstallSource) -> Boolean,
        val studyPadStats: suspend (File) -> Pair<String, File>,
        val swordZipScan: suspend (File) -> InstallInspector.SwordZipScan,
        val epubUpgradeCheck: suspend (String) -> Boolean,
    )

    /** Owns every worker coroutine; never tied to a caller's context (see class doc). */
    private val workerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _jobs = MutableStateFlow<List<InstallJobState>>(emptyList())
    val jobs: StateFlow<List<InstallJobState>> = _jobs.asStateFlow()

    private val decisions = ConcurrentHashMap<JobId, CompletableDeferred<Boolean>>()
    private val jobCoroutines = ConcurrentHashMap<JobId, Job>()

    /**
     * Testability seam: the actual per-source run, factored out of [enqueue]'s sequencing so
     * tests can substitute a scripted lambda instead of a real [InstallJobRunner] +
     * [InstallInspector] + [InstallCommitter] stack (see `InstallServiceControllerTest`). Defaults
     * to wiring [JobDeps] into a freshly-built runner's `runJob`, deriving a per-job temp file
     * under [JobDeps.acquireDir].
     */
    internal var runOne: suspend (
        jobId: JobId,
        source: InstallSource,
        deps: JobDeps,
        onPhase: (InstallPhase) -> Unit,
        awaitDecision: suspend (DecisionRequest) -> Boolean,
    ) -> InstallPhase = { jobId, source, deps, onPhase, awaitDecision ->
        deps.acquireDir.mkdirs()
        val acquireTo = File(deps.acquireDir, "installzip-$jobId.tmp")
        runnerFactory().runJob(
            source = source,
            acquireTo = acquireTo,
            openStream = { deps.openStream(source) },
            isStudyPadExport = deps.isStudyPadExport,
            studyPadStats = deps.studyPadStats,
            swordZipScan = deps.swordZipScan,
            epubUpgradeCheck = deps.epubUpgradeCheck,
            onPhase = onPhase,
            awaitDecision = awaitDecision,
        )
    }

    /** Completes the pending ask-back decision for [jobId]. A no-op if it isn't currently paused. */
    fun resolveDecision(jobId: JobId, proceed: Boolean) {
        decisions[jobId]?.complete(proceed)
    }

    /**
     * Cancels [jobId]. Does both of: (1) completes any pending decision as declined, so a job
     * currently paused on [InstallPhase.AwaitingDecision] unwinds the normal way (the state
     * machine itself reports [InstallPhase.Cancelled]); and (2) cancels the job's own worker
     * coroutine directly, so a job that is NOT currently paused (mid-acquire/mid-commit) also
     * stops. Harmless/no-op for a [jobId] that has already reached a terminal phase.
     */
    fun cancel(jobId: JobId) {
        decisions[jobId]?.complete(false)
        jobCoroutines[jobId]?.cancel()
    }

    /**
     * Enqueues one job per source and suspends until the *aggregate* terminal [InstallOutcome].
     * All [InstallJobState]s (phase [InstallPhase.Queued]) are added to [jobs] synchronously
     * before this function's first suspension point, then a worker is launched on [workerScope]
     * (NOT the caller's coroutine context) that runs each source in turn via [runOne]. The
     * suspension here is a plain [CompletableDeferred] await, structurally separate from that
     * worker -- see the class doc for why that makes this cancellation-safe.
     */
    suspend fun enqueue(sources: List<InstallSource>, deps: JobDeps): InstallOutcome {
        if (sources.isEmpty()) return InstallOutcome.NothingEnqueued

        val entries = sources.map { newJobId() to it }
        _jobs.update { current ->
            current + entries.map { (id, source) -> InstallJobState(id, source.displayName, InstallPhase.Queued) }
        }

        val result = CompletableDeferred<InstallOutcome>()
        workerScope.launch {
            val terminals = entries.map { (jobId, source) -> runSingleJob(jobId, source, deps) }
            result.complete(aggregate(terminals))
        }
        return result.await()
    }

    /** Runs one job to its terminal phase, updates [jobs] accordingly, and reports [onTerminal]. */
    private suspend fun runSingleJob(jobId: JobId, source: InstallSource, deps: JobDeps): InstallPhase {
        val decision = CompletableDeferred<Boolean>()
        decisions[jobId] = decision
        var terminal: InstallPhase = InstallPhase.Cancelled
        val job = workerScope.launch {
            terminal = try {
                runOne(
                    jobId, source, deps,
                    { phase -> updateJobPhase(jobId, source.displayName, phase) },
                    { request -> decision.await() },
                )
            } catch (e: CancellationException) {
                InstallPhase.Cancelled
            }
        }
        jobCoroutines[jobId] = job
        job.join()

        decisions.remove(jobId)
        jobCoroutines.remove(jobId)
        removeJob(jobId)
        onTerminal(jobId, terminal)
        return terminal
    }

    private fun updateJobPhase(jobId: JobId, displayName: String?, phase: InstallPhase) {
        _jobs.update { current ->
            val idx = current.indexOfFirst { it.jobId == jobId }
            val updated = InstallJobState(jobId, displayName, phase)
            if (idx == -1) current + updated else current.toMutableList().apply { set(idx, updated) }
        }
    }

    private fun removeJob(jobId: JobId) {
        _jobs.update { current -> current.filterNot { it.jobId == jobId } }
    }

    private fun aggregate(terminals: List<InstallPhase>): InstallOutcome {
        val firstError = terminals.filterIsInstance<InstallPhase.Error>().firstOrNull()
        if (firstError != null) return InstallOutcome.Error(firstError.messageKey, firstError.arg)
        if (terminals.isNotEmpty() && terminals.all { it is InstallPhase.Cancelled }) return InstallOutcome.Cancelled
        return InstallOutcome.Ok
    }
}
