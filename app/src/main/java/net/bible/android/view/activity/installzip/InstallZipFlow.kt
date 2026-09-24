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

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import net.bible.android.activity.R
import net.bible.service.installzip.InstallJobState
import net.bible.service.installzip.JobId
import net.bible.sharedcore.nav.InstallZipResult
import net.bible.sharedui.installzip.InstallUiState
import net.bible.sharedui.installzip.nav.InstallZipSession

/** Same literal tag `InstallZipComposeActivity.kt` uses, so a logcat filter keeps working (its kdoc on TAG). */
private const val TAG = "InstallZip"

/**
 * The destination's entry decision, from its ROUTE arguments rather than from an Intent (slice 8 D1).
 * Mirrors [classifyEntry]: an external `ACTION_VIEW` confirms first unless it is a StudyPad export;
 * `SEND`/`SEND_MULTIPLE` enqueue at once; an in-app entry (no action, no URIs) shows the picker prelude.
 * The redirect Activity has already dropped every malformed Intent, so [InstallZipEntryDecision.Invalid]
 * here means an action with no URIs.
 */
internal suspend fun classifyInstallZipRoute(
    action: String?,
    uris: List<Uri>,
    isStudyPadExport: suspend (Uri) -> Boolean,
): InstallZipEntryDecision = when {
    uris.isEmpty() -> if (action == null) InstallZipEntryDecision.PickFile else InstallZipEntryDecision.Invalid
    action == Intent.ACTION_VIEW -> {
        val uri = uris.first()
        if (isStudyPadExport(uri)) InstallZipEntryDecision.EnqueueNow(listOf(uri), Intent.ACTION_VIEW)
        else InstallZipEntryDecision.ConfirmThenEnqueue(uri, Intent.ACTION_VIEW)
    }
    else -> InstallZipEntryDecision.EnqueueNow(uris, action)
}

/**
 * One InstallZip destination entry (slice 8 §3.2) -- `InstallZipComposeActivity`'s body (A49) with the
 * Activity taken out: the prelude (confirm / formats + picker), the hand-off to the foreground
 * `DocumentInstallService`, the rendering of its jobs, and exactly ONE [InstallZipResult] through
 * [onFinished]. Every Android call is a [Seams] lambda the host supplies, so the machine is testable.
 *
 * [scope] is the host's `lifecycleScope`; every coroutine this starts is cancelled when it answers.
 */
internal class InstallZipFlow(
    private val scope: CoroutineScope,
    private val action: String?,
    private val uris: List<Uri>,
    private val seams: Seams,
    private val onFinished: (InstallZipResult) -> Unit,
) : InstallZipSession {

    class Seams(
        val jobs: StateFlow<List<InstallJobState>>,
        val isStudyPadExport: suspend (Uri) -> Boolean,
        val displayName: (Uri) -> String?,
        val formatsText: () -> String,
        /** The SAF picker; null = the user cancelled. May throw [ActivityNotFoundException]. */
        val pickFile: suspend () -> Uri?,
        val enqueue: (List<Uri>, String?) -> Unit,
        val resolveDecision: (JobId, Boolean) -> Unit,
        val mapPhase: (InstallJobState) -> InstallUiState,
        val requestNotificationPermission: () -> Unit,
        val noFileManager: () -> Unit,
    )

    private val prelude = MutableStateFlow<InstallUiState?>(null)
    private val _state = MutableStateFlow<InstallUiState?>(null)
    override val state: StateFlow<InstallUiState?> = _state

    private var pendingConfirm: Pair<Uri, String>? = null
    /** See [shouldFinishOnDrain]: only a job THIS entry enqueued may finish it with OK. */
    private var enqueuedHere = false
    private var started = false
    private var finished = false
    private val work = mutableListOf<Job>()

    override fun start() {
        if (started) return
        started = true
        seams.requestNotificationPermission()
        work += scope.launch {
            combine(prelude, seams.jobs) { p, jobs -> p ?: pickActiveJob(jobs)?.let(seams.mapPhase) }
                .collect { _state.value = it }
        }
        // The drain watch reads ONLY the jobs flow, like the Activity's LaunchedEffect(jobs): a prelude
        // change must never be mistaken for "the queue drained".
        work += scope.launch {
            seams.jobs.collect { jobs ->
                if (shouldFinishOnDrain(enqueuedHere, jobs.isEmpty())) finish(InstallZipResult.OK)
            }
        }
        work += scope.launch { dispatch() }
    }

    private suspend fun dispatch() {
        when (val decision = classifyInstallZipRoute(action, uris, seams.isStudyPadExport)) {
            is InstallZipEntryDecision.EnqueueNow -> enqueue(decision.uris, decision.action)
            is InstallZipEntryDecision.ConfirmThenEnqueue -> {
                pendingConfirm = decision.uri to decision.action
                prelude.value = InstallUiState.ConfirmInstall(seams.displayName(decision.uri))
            }
            InstallZipEntryDecision.PickFile -> prelude.value = InstallUiState.FormatInfo(seams.formatsText())
            InstallZipEntryDecision.Invalid -> finish(InstallZipResult.CANCELED)
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

    private fun launchFilePicker() {
        work += scope.launch {
            val picked = try {
                seams.pickFile()
            } catch (e: ActivityNotFoundException) {
                Log.e(TAG, "No activity found to handle ACTION_OPEN_DOCUMENT", e)
                seams.noFileManager()
                finish(InstallZipResult.CANCELED)
                return@launch
            }
            if (picked != null) enqueue(listOf(picked), null) else finish(InstallZipResult.CANCELED)
        }
    }

    private fun enqueue(list: List<Uri>, act: String?) {
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

/** Classic `InstallZip.getFileFromUserAndInstall`'s SAF allow-list, verbatim (moved from A49). */
internal val INSTALL_ZIP_SAF_MIME_TYPES = arrayOf(
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

/** Classic `InstallZip.getDisplayName`, moved verbatim from A49. */
internal fun installZipDisplayName(context: Context, uri: Uri): String? =
    context.contentResolver.query(uri, null, null, null, null)?.use {
        if (it.isLast) return null
        it.moveToFirst()
        val idx = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (idx < 0) null else it.getString(idx)
    }

/** Classic `InstallZip.getFileFromUserAndInstall`'s supported-formats blurb, moved verbatim from A49. */
internal fun installZipFormatsText(context: Context): String {
    val zip = context.getString(R.string.format_zip, context.getString(R.string.app_name_andbible))
    val myBible = context.getString(R.string.format_mybible)
    val mySword = context.getString(R.string.format_mysword)
    val eSword = context.getString(R.string.format_esword)
    val epub = context.getString(R.string.format_epub)
    val studyPads = context.getString(R.string.format_studypads)
    val ttf = context.getString(R.string.format_ttf)
    val csvPrompts = context.getString(R.string.format_csv_prompts)
    return context.getString(R.string.choose_file, context.getString(R.string.app_name_andbible)) + " \n\n" +
        context.getString(R.string.supported_formats, "$zip, $myBible, $mySword, $eSword, $epub, $ttf, $csvPrompts, $studyPads")
}
