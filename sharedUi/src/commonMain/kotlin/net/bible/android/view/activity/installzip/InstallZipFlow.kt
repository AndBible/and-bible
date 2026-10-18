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
package net.bible.android.view.activity.installzip

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import net.bible.service.installzip.InstallJobState
import net.bible.service.installzip.InstallPhase
import net.bible.service.installzip.JobId
import net.bible.sharedcore.log.Log
import net.bible.sharedcore.nav.InstallZipResult
import net.bible.sharedui.app.ports.DocumentRef
import net.bible.sharedui.app.ports.NoDocumentPickerException
import net.bible.sharedui.installzip.InstallUiState
import net.bible.sharedui.installzip.nav.InstallZipSession

/** Same literal tag `InstallZipComposeActivity.kt` uses, so a logcat filter keeps working. */
private const val TAG = "InstallZip"

/** Android's `Intent.ACTION_VIEW`, the string the route carries and the install service understands. */
const val INSTALL_ZIP_ACTION_VIEW = "android.intent.action.VIEW"

/** What the destination does on entry, from the ROUTE arguments (the `Uri`-free twin of `InstallZipEntryDecision`). */
sealed interface InstallZipRouteDecision {
    data class ConfirmThenEnqueue(val uri: DocumentRef, val action: String) : InstallZipRouteDecision
    data class EnqueueNow(val uris: List<DocumentRef>, val action: String?) : InstallZipRouteDecision
    data object PickFile : InstallZipRouteDecision
    data object Invalid : InstallZipRouteDecision
}

/**
 * The destination's entry decision, from its ROUTE arguments rather than from an Intent (slice 8 D1).
 * Mirrors `classifyEntry`: an external `ACTION_VIEW` confirms first unless it is a StudyPad export;
 * `SEND`/`SEND_MULTIPLE` enqueue at once; an in-app entry (no action, no URIs) shows the picker prelude.
 * The redirect Activity has already dropped every malformed Intent, so [InstallZipRouteDecision.Invalid]
 * here means an action with no URIs.
 */
suspend fun classifyInstallZipRoute(
    action: String?,
    uris: List<DocumentRef>,
    isStudyPadExport: suspend (DocumentRef) -> Boolean,
): InstallZipRouteDecision = when {
    uris.isEmpty() -> if (action == null) InstallZipRouteDecision.PickFile else InstallZipRouteDecision.Invalid
    action == INSTALL_ZIP_ACTION_VIEW -> {
        val uri = uris.first()
        if (isStudyPadExport(uri)) InstallZipRouteDecision.EnqueueNow(listOf(uri), INSTALL_ZIP_ACTION_VIEW)
        else InstallZipRouteDecision.ConfirmThenEnqueue(uri, INSTALL_ZIP_ACTION_VIEW)
    }
    else -> InstallZipRouteDecision.EnqueueNow(uris, action)
}

/**
 * Picks the job the host should currently render out of [jobs]: a job paused on
 * [InstallPhase.AwaitingDecision] takes priority over any other (mirrors
 * `DocumentInstallService.onJobsChanged`'s own notification-picking logic -- a pending ask-back
 * must never be starved by a later-queued job's progress), otherwise the first job in the queue.
 */
fun pickActiveJob(jobs: List<InstallJobState>): InstallJobState? =
    jobs.firstOrNull { it.phase is InstallPhase.AwaitingDecision } ?: jobs.firstOrNull()

/**
 * Whether the jobs-drained-to-empty watcher in `InstallZipComposeActivity.Content` should finish
 * the host with `RESULT_OK`. Gated on [enqueuedHere] -- whether THIS host instance actually
 * enqueued a job itself (see `InstallZipComposeActivity.enqueue`) -- rather than merely on having
 * observed the shared, app-scoped `DocumentInstallService.controller.jobs` queue become non-empty
 * at some point: the design allows backing out of a running install (the service keeps running in
 * the background), so a SECOND host can open while a PRIOR host's install still runs. That
 * foreign job draining to empty must NOT finish *this* host with `RESULT_OK` if it never enqueued
 * anything itself -- e.g. it is still sitting at its `ConfirmInstall`/`FormatInfo` prelude, having
 * enqueued nothing. Extracted as a pure predicate so this precise "RESULT_OK iff >=1 source
 * enqueued by THIS prelude" contract is directly unit-testable (see Task B3 finding I1).
 */
fun shouldFinishOnDrain(enqueuedHere: Boolean, jobsEmpty: Boolean): Boolean = enqueuedHere && jobsEmpty

/**
 * One InstallZip destination entry (slice 8 §3.2): the prelude (confirm / formats + picker), the hand-off to the
 * install service, the rendering of its jobs, and exactly ONE [InstallZipResult] through [onFinished]. Every
 * platform call is a [Seams] lambda, so the machine is testable.
 *
 * [scope] is the host's scope; every coroutine this starts is cancelled when it answers.
 */
class InstallZipFlow(
    private val scope: CoroutineScope,
    private val action: String?,
    private val uris: List<DocumentRef>,
    private val seams: Seams,
    private val onFinished: (InstallZipResult) -> Unit,
) : InstallZipSession {

    class Seams(
        val jobs: StateFlow<List<InstallJobState>>,
        val isStudyPadExport: suspend (DocumentRef) -> Boolean,
        val displayName: (DocumentRef) -> String?,
        val formatsText: () -> String,
        /** The system file picker; null = the user cancelled. A throw other than cancellation means no picker app. */
        val pickFile: suspend () -> DocumentRef?,
        val enqueue: (List<DocumentRef>, String?) -> Unit,
        val resolveDecision: (JobId, Boolean) -> Unit,
        val mapPhase: (InstallJobState) -> InstallUiState,
        val requestNotificationPermission: () -> Unit,
        val noFileManager: () -> Unit,
    )

    private val prelude = MutableStateFlow<InstallUiState?>(null)
    private val _state = MutableStateFlow<InstallUiState?>(null)
    override val state: StateFlow<InstallUiState?> = _state

    private var pendingConfirm: Pair<DocumentRef, String>? = null
    /** See [shouldFinishOnDrain]: only a job THIS entry enqueued may finish it with OK. */
    private var enqueuedHere = false
    private var started = false
    private var finished = false
    private val work = mutableListOf<Job>()

    override fun start() {
        if (started || finished) return // finished: answered or closed -- a closed session never restarts
        started = true
        seams.requestNotificationPermission()
        work += scope.launch {
            combine(prelude, seams.jobs) { p, jobs -> p ?: pickActiveJob(jobs)?.let(seams.mapPhase) }
                .collect { _state.value = it }
        }
        // The drain watch reads ONLY the jobs flow: a prelude change must never be mistaken for "the queue drained".
        work += scope.launch {
            seams.jobs.collect { jobs ->
                if (shouldFinishOnDrain(enqueuedHere, jobs.isEmpty())) finish(InstallZipResult.OK)
            }
        }
        work += scope.launch { dispatch() }
    }

    private suspend fun dispatch() {
        when (val decision = classifyInstallZipRoute(action, uris, seams.isStudyPadExport)) {
            is InstallZipRouteDecision.EnqueueNow -> enqueue(decision.uris, decision.action)
            is InstallZipRouteDecision.ConfirmThenEnqueue -> {
                pendingConfirm = decision.uri to decision.action
                prelude.value = InstallUiState.ConfirmInstall(seams.displayName(decision.uri))
            }
            InstallZipRouteDecision.PickFile -> prelude.value = InstallUiState.FormatInfo(seams.formatsText())
            InstallZipRouteDecision.Invalid -> finish(InstallZipResult.CANCELED)
        }
    }

    override fun confirm() {
        when (prelude.value) {
            is InstallUiState.ConfirmInstall -> {
                val pending = pendingConfirm
                prelude.value = null
                pendingConfirm = null
                if (pending != null) enqueue(listOf(pending.first), pending.second) else finish(InstallZipResult.CANCELED)
            }
            is InstallUiState.FormatInfo -> {
                prelude.value = null
                launchFilePicker()
            }
            // No prelude: the confirm belongs to the active job's decision dialog.
            null -> pickActiveJob(seams.jobs.value)?.let { seams.resolveDecision(it.jobId, true) }
            else -> Unit
        }
    }

    override fun dismiss() {
        if (prelude.value != null) {
            prelude.value = null
            pendingConfirm = null
            finish(InstallZipResult.CANCELED)
        } else {
            pickActiveJob(seams.jobs.value)?.let { seams.resolveDecision(it.jobId, false) }
        }
    }

    /** Back leaves the destination; a running install keeps running in the service, as before. */
    override fun back() = finish(InstallZipResult.CANCELED)

    /** The entry is gone: cancel everything, answer nothing (a running install keeps running in the service). */
    override fun close() {
        finished = true
        work.toList().forEach { it.cancel() }
    }

    private fun launchFilePicker() {
        work += scope.launch {
            val picked = try {
                seams.pickFile()
            } catch (e: CancellationException) {
                throw e
            } catch (e: NoDocumentPickerException) {
                Log.e(TAG, "No activity found to handle the document picker", e)
                seams.noFileManager()
                finish(InstallZipResult.CANCELED)
                return@launch
            }
            if (picked != null) enqueue(listOf(picked), null) else finish(InstallZipResult.CANCELED)
        }
    }

    private fun enqueue(list: List<DocumentRef>, act: String?) {
        enqueuedHere = true
        seams.enqueue(list, act)
    }

    private fun finish(result: InstallZipResult) {
        if (finished) return
        finished = true
        onFinished(result)
        work.toList().forEach { it.cancel() }
    }
}

/** Classic `InstallZip.getFileFromUserAndInstall`'s SAF allow-list, verbatim. */
val INSTALL_ZIP_SAF_MIME_TYPES: List<String> = listOf(
    "application/zip",
    "application/x-zip-compressed",
    "application/epub+zip",
    "application/x-font-ttf",
    "font/ttf",
    "font/otf",
    "application/x-font-ttf",
    "application/x-font-otf",
    "application/vnd.sqlite3",
    "application/x-sqlite3",
    "application/octet-stream",
    "text/csv",
    "text/comma-separated-values",
    "image/png",
    "image/jpeg",
    "image/webp",
)
