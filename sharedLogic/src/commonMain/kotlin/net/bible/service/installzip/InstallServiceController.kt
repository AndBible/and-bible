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

import androidx.annotation.VisibleForTesting
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import okio.FileSystem
import okio.Path

/**
 * App-scoped orchestrator for headless zip installs: holds the job queue, exposes it as a
 * [StateFlow] for a UI/notification to observe, drives each queued [InstallSource] through
 * [InstallJobRunner] sequentially, and bridges the ask-back [DecisionRequest]s the state machine
 * pauses on ([InstallPhase.AwaitingDecision]) to external [resolveDecision]/[cancel] calls (a
 * dialog button, a notification action, ...).
 *
 * **Cancellation-safety.** Workers run on an injected application-lifetime scope, entirely
 * separate from whatever coroutine calls [enqueue]. So if the caller's coroutine is cancelled
 * (e.g. a Fragment/ViewModel scope torn down while installs are still running), only the
 * *awaiting* of the aggregate [InstallOutcome] stops -- the queued jobs are completely unaffected,
 * keep running to their terminal phase, and [onTerminal] still fires for each. This mirrors how a
 * real foreground install host must behave: it must keep installing after the Activity/UI
 * that started it goes away.
 */
class InstallServiceController(
    private val runnerFactory: () -> InstallJobRunner,
    private val newJobId: () -> JobId,
    private val onTerminal: (jobId: JobId, phase: InstallPhase) -> Unit,
    scope: CoroutineScope,
) {
    /** Data the runner needs per job, provided by the platform install host. */
    class JobDeps(
        val acquireDir: Path,
        val openStream: suspend (InstallSource) -> InstallStream,
        val isStudyPadExport: suspend (InstallSource) -> Boolean,
        val studyPadStats: suspend (Path) -> Pair<String, Path>,
        val swordZipScan: suspend (Path) -> InstallInspector.SwordZipScan,
        val epubUpgradeCheck: suspend (String) -> Boolean,
    )

    /** Hosts every worker coroutine; never tied to a caller's context (see class doc). */
    private val workerScope = scope

    private val _jobs = MutableStateFlow<List<InstallJobState>>(emptyList())
    val jobs: StateFlow<List<InstallJobState>> = _jobs.asStateFlow()

    /** Guards [decisions] and [jobCoroutines]; class-private, never held across a suspension or a callback. */
    private val mapsLock = SynchronizedObject()
    private val decisions = mutableMapOf<JobId, PendingDecision>()

    /** The question a job is paused on and the answer slot of exactly that question (#4192). */
    private class PendingDecision(val request: DecisionRequest, val answer: CompletableDeferred<Boolean> = CompletableDeferred())
    private val jobCoroutines = mutableMapOf<JobId, Job>()

    /**
     * Testability seam: the actual per-source run, factored out of [enqueue]'s sequencing so
     * tests can substitute a scripted lambda instead of a real [InstallJobRunner] +
     * [InstallInspector] + [InstallCommitter] stack (see `InstallServiceControllerTest`). Defaults
     * to wiring [JobDeps] into a freshly-built runner's `runJob`, deriving a per-job temp file
     * under [JobDeps.acquireDir].
     */
    @VisibleForTesting var runOne: suspend (
        jobId: JobId,
        source: InstallSource,
        deps: JobDeps,
        onPhase: (InstallPhase) -> Unit,
        awaitDecision: suspend (DecisionRequest) -> Boolean,
    ) -> InstallPhase = { jobId, source, deps, onPhase, awaitDecision ->
        FileSystem.SYSTEM.createDirectories(deps.acquireDir)
        val acquireTo = deps.acquireDir / "installzip-$jobId.tmp"
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

    /**
     * Completes the pending ask-back decision for [jobId], but only when it is still [request], the
     * question the caller's dialog showed. A job asks several questions in a row; an answer to an
     * earlier one (a double tap, a late tap on a stale dialog) must never answer the next, so a
     * mismatching [request] is dropped. Equality matching relies on each question type being asked at most
     * once per job (the runner asks Restore, Overwrite, ... once each); a repeated identical question would
     * need a per-request sequence token. A no-op if the job isn't currently paused.
     */
    fun resolveDecision(jobId: JobId, request: DecisionRequest, proceed: Boolean) {
        val pending = synchronized(mapsLock) { decisions[jobId] } // L1-common-ok: atomicfu lock
        if (pending != null && pending.request == request) pending.answer.complete(proceed)
    }

    /**
     * Cancels [jobId]. Does both of: (1) completes any pending decision as declined, so a job
     * currently paused on [InstallPhase.AwaitingDecision] unwinds the normal way (the state
     * machine itself reports [InstallPhase.Cancelled]); and (2) cancels the job's own worker
     * coroutine directly, so a job that is NOT currently paused (mid-acquire/mid-commit) also
     * stops. Harmless/no-op for a [jobId] that has already reached a terminal phase.
     */
    fun cancel(jobId: JobId) {
        val (pending, worker) = synchronized(mapsLock) { decisions[jobId] to jobCoroutines[jobId] } // L1-common-ok: atomicfu lock
        pending?.answer?.complete(false)
        worker?.cancel()
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
        // One fresh deferred per decision request (#4192): a job can ask several questions in a
        // row (ModuleBackupRestore, then Overwrite), and a single shared deferred would hand the
        // first answer to every later question. The deferred is registered *before* the
        // AwaitingDecision phase is published, so an answer to a visible question is never lost.
        val publishPhase: (InstallPhase) -> Unit = { phase ->
            if (phase is InstallPhase.AwaitingDecision) {
                synchronized(mapsLock) { decisions[jobId] = PendingDecision(phase.request) } // L1-common-ok: atomicfu lock
            }
            updateJobPhase(jobId, source.displayName, phase)
        }
        val awaitCurrentDecision: suspend (DecisionRequest) -> Boolean = { request ->
            val current = synchronized(mapsLock) { // L1-common-ok: atomicfu lock
                decisions[jobId]?.takeIf { it.request == request }
                    ?: PendingDecision(request).also { decisions[jobId] = it }
            }
            try {
                current.answer.await()
            } finally {
                synchronized(mapsLock) { if (decisions[jobId] === current) decisions.remove(jobId) } // L1-common-ok: atomicfu lock
            }
        }
        var terminal: InstallPhase = InstallPhase.Cancelled
        val job = workerScope.launch {
            terminal = try {
                runOne(
                    jobId, source, deps,
                    publishPhase,
                    { request -> awaitCurrentDecision(request) },
                )
            } catch (e: CancellationException) {
                InstallPhase.Cancelled
            } catch (e: Throwable) {
                // Guards the whole `runOne` call, not just the commit step: `inspector.inspect`
                // (SWORD zip scanning / epub-upgrade lookup / StudyPad stats) reuses the shared
                // JSword engine and can throw `BookException`/other RuntimeExceptions the classic
                // `ZipHandler` explicitly caught -- left unguarded, that would escape this
                // `launch` uncaught on the handler-less SupervisorJob scope (a crash), while
                // `terminal` stayed at its `Cancelled` default, misreporting a real error as a
                // user cancellation.
                InstallPhase.Error(InstallError.GENERIC, null, e)
            }
        }
        synchronized(mapsLock) { jobCoroutines[jobId] = job } // L1-common-ok: atomicfu lock
        job.join()

        synchronized(mapsLock) { // L1-common-ok: atomicfu lock
            decisions.remove(jobId)
            jobCoroutines.remove(jobId)
        }
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
        if (firstError != null) return InstallOutcome.Error(firstError.kind, firstError.arg)
        if (terminals.isNotEmpty() && terminals.all { it is InstallPhase.Cancelled }) return InstallOutcome.Cancelled
        return InstallOutcome.Ok
    }
}
