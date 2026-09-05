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

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import net.bible.android.activity.R
import net.bible.android.control.event.ABEventBus
import net.bible.android.control.event.ToastEvent
import net.bible.android.view.activity.base.ActivityBase
import net.bible.service.common.AndBibleBackupManifest
import net.bible.service.common.BackupType
import net.bible.service.installzip.DecisionRequest
import net.bible.service.installzip.DocumentInstallService
import net.bible.service.installzip.InstallJobState
import net.bible.service.installzip.InstallPhase
import net.bible.sharedui.AbAppTheme
import net.bible.sharedui.installzip.InstallUiState
import net.bible.sharedui.installzip.InstallZipContent

/**
 * Pure entry-dispatch decision, computed from the launching [Intent] alone (mirrors classic
 * `InstallZip.onCreate`'s `when (intent?.action)` dispatch). Kept out of the Activity so it is
 * directly unit-testable (see `InstallZipComposeActivityTest`) without driving lifecycle.
 */
internal sealed interface InstallZipEntryDecision {
    /** `ACTION_VIEW`, non-StudyPad: show the "do you want to install [uri]?" prelude first; on
     *  confirm, enqueue with [action] (always `ACTION_VIEW`). */
    data class ConfirmThenEnqueue(val uri: Uri, val action: String) : InstallZipEntryDecision

    /** No prelude confirm needed -- straight to the service. Covers a StudyPad-export
     *  `ACTION_VIEW` (the service asks its own [DecisionRequest.StudyPadImport] instead),
     *  `ACTION_SEND`, and `ACTION_SEND_MULTIPLE`. */
    data class EnqueueNow(val uris: List<Uri>, val action: String?) : InstallZipEntryDecision

    /** No action (launched in-app, e.g. from a menu): show the supported-formats prelude, then
     *  the SAF picker. */
    data object PickFile : InstallZipEntryDecision

    /** A malformed intent for its own action (e.g. `ACTION_VIEW` with no data, `ACTION_SEND`
     *  with no `EXTRA_STREAM`). Classic asserted non-null (`!!`) here and would have crashed;
     *  this host instead cancels cleanly. */
    data object Invalid : InstallZipEntryDecision
}

/**
 * Classifies [intent] into an [InstallZipEntryDecision]. [isStudyPadExport] is a suspend lambda
 * (real impl: `AndBibleBackupManifest.fromUri(uri)?.backupType == BackupType.STUDYPAD_EXPORT`,
 * a cheap peek at the uri's contents) so the ACTION_VIEW branch can decide, exactly as classic
 * `InstallZip` does, whether to skip the initial "do you want to install" confirm.
 *
 * **StudyPad double-prompt choice:** classic peeks the manifest once up front and, for a
 * StudyPad export, skips the confirm entirely (the service's own `StudyPadImport` ask-back,
 * built from real DB stats, is the only prompt the user sees). This implementation preserves
 * that parity-preferred behaviour rather than the simpler "always confirm, accept a StudyPad
 * double-prompt" alternative the brief allowed -- the peek is a cheap, already-suspend,
 * already-cached-by-the-OS content read (same call classic made), so there's no real cost to
 * reading it once here and the service will not re-read it a second time for anything other
 * than a StudyPad export (see `DocumentInstallService.buildDeps().isStudyPadExport`, which reads
 * it again independently -- two reads total for a StudyPad export either way).
 */
internal suspend fun classifyEntry(
    intent: Intent?,
    isStudyPadExport: suspend (Uri) -> Boolean,
): InstallZipEntryDecision = when (intent?.action) {
    Intent.ACTION_VIEW -> {
        val uri = intent.data
        if (uri == null) {
            InstallZipEntryDecision.Invalid
        } else if (isStudyPadExport(uri)) {
            InstallZipEntryDecision.EnqueueNow(listOf(uri), Intent.ACTION_VIEW)
        } else {
            InstallZipEntryDecision.ConfirmThenEnqueue(uri, Intent.ACTION_VIEW)
        }
    }

    Intent.ACTION_SEND -> {
        @Suppress("DEPRECATION")
        val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
        if (uri == null) InstallZipEntryDecision.Invalid
        else InstallZipEntryDecision.EnqueueNow(listOf(uri), Intent.ACTION_SEND)
    }

    Intent.ACTION_SEND_MULTIPLE -> {
        @Suppress("DEPRECATION")
        val uris = intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
        if (uris.isNullOrEmpty()) InstallZipEntryDecision.Invalid
        else InstallZipEntryDecision.EnqueueNow(uris, Intent.ACTION_SEND_MULTIPLE)
    }

    else -> InstallZipEntryDecision.PickFile
}

/**
 * The log tag for this package. Kept as the literal `"InstallZip"` it has always been rather than
 * renamed to match the host class: slice S16 deleted classic `InstallZip.kt`, which declared this
 * as a package-visible `const val TAG` that this file read with no import at all (same package),
 * and log-tag continuity across the release matters more here than symmetry with the class name --
 * a user's logcat capture from before and after the port should filter identically.
 */
private const val TAG = "InstallZip"

/**
 * The URI-grant flags an Intent needs to keep the sender's `content://` grant readable
 * (see [composeForwardIntent]) -- and ONLY these; deliberately excludes any `FLAG_ACTIVITY_*`.
 */
private const val URI_GRANT_FLAGS =
    Intent.FLAG_GRANT_READ_URI_PERMISSION or
        Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
        Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
        Intent.FLAG_GRANT_PREFIX_URI_PERMISSION

/**
 * Normalises an inbound Intent into one addressed at this host, keeping the sender's URI grant.
 *
 * **This is no longer a forwarding hop.** It was written for spec
 * `2026-07-25-compose-pre-ab-state-freshness-design.md` §1 P2, when the external
 * `<intent-filter>`s for `ACTION_VIEW`/`ACTION_SEND`/`ACTION_SEND_MULTIPLE` lived on the classic
 * `InstallZip` Activity — an `<intent-filter>` cannot be toggled at runtime, so classic had to
 * receive the file-manager/Share-sheet Intent and forward a copy here whenever `use_compose_ui`
 * was ON. Slice S16's prep moved those filters onto this host in
 * `app/src/standard/AndroidManifest.xml` and then deleted classic `InstallZip`, so **an external
 * module-file Intent now lands here directly** and nothing in production calls this any more.
 *
 * It is kept, rather than deleted with the forwarder, as this file's URI-grant normaliser: it is
 * small, side-effect-free, and its four `InstallZipComposeActivityTest` cases are the only
 * coverage anywhere of which flags may and may not travel with a `content://` uri — the rule
 * below, which any future re-dispatch of an inbound Intent has to obey. Deleting a tested,
 * documented invariant inside a deletion slice would have cost that coverage for nothing.
 *
 * Building it from a **copy** of the whole Intent keeps every part of the contract the
 * receiving host reads: `action`, `data` + `type`, `clipData`, and all extras.
 *
 * The **flags** are NOT copied wholesale, though — only [URI_GRANT_FLAGS] survive, most
 * importantly `FLAG_GRANT_READ_URI_PERMISSION`, without which the `content://` uri would fail to
 * open with a `SecurityException`. A sender's `FLAG_ACTIVITY_*` flags (e.g. a file manager
 * launching with `FLAG_ACTIVITY_NEW_TASK or FLAG_ACTIVITY_MULTIPLE_TASK`) must NOT travel: they'd
 * make the relaunch resolve by task affinity instead of stacking in the current task,
 * so Back from this host could land on the home screen or yank the whole app task forward
 * (finding M1, pre-A/B state-freshness final review).
 *
 * Kept as a file-level helper (like [classifyEntry] above) so it is unit-testable without driving
 * either Activity's lifecycle — see `InstallZipComposeActivityTest`.
 */
internal fun composeForwardIntent(original: Intent?, context: Context): Intent =
    if (original == null) Intent(context, InstallZipComposeActivity::class.java)
    else Intent(original)
        .setClass(context, InstallZipComposeActivity::class.java)
        .also { it.flags = it.flags and URI_GRANT_FLAGS }

/**
 * Picks the job the host should currently render out of [jobs]: a job paused on
 * [InstallPhase.AwaitingDecision] takes priority over any other (mirrors
 * `DocumentInstallService.onJobsChanged`'s own notification-picking logic -- a pending ask-back
 * must never be starved by a later-queued job's progress), otherwise the first job in the queue.
 */
internal fun pickActiveJob(jobs: List<InstallJobState>): InstallJobState? =
    jobs.firstOrNull { it.phase is InstallPhase.AwaitingDecision } ?: jobs.firstOrNull()

/**
 * Whether the jobs-drained-to-empty watcher in [InstallZipComposeActivity.Content] should finish
 * the host with `RESULT_OK`. Gated on [enqueuedHere] -- whether THIS host instance actually
 * enqueued a job itself (see [InstallZipComposeActivity.enqueue]) -- rather than merely on having
 * observed the shared, app-scoped `DocumentInstallService.controller.jobs` queue become non-empty
 * at some point: the design allows backing out of a running install (the service keeps running in
 * the background), so a SECOND host can open while a PRIOR host's install still runs. That
 * foreign job draining to empty must NOT finish *this* host with `RESULT_OK` if it never enqueued
 * anything itself -- e.g. it is still sitting at its `ConfirmInstall`/`FormatInfo` prelude, having
 * enqueued nothing. Extracted as a pure predicate so this precise "RESULT_OK iff >=1 source
 * enqueued by THIS prelude" contract is directly unit-testable (see Task B3 finding I1).
 */
internal fun shouldFinishOnDrain(enqueuedHere: Boolean, jobsEmpty: Boolean): Boolean =
    enqueuedHere && jobsEmpty

/**
 * Maps a job's [InstallPhase] (+ its [displayName]) to the commonMain-local [InstallUiState] that
 * [InstallZipContent] renders. Every dynamic string ([InstallUiState.Progress.statusText] and
 * [InstallUiState.Error.message]) is resolved HERE via [context] -- `InstallZipScreens.kt`
 * (commonMain) cannot see `R.string` -- mirroring exactly which strings classic `InstallZip`/
 * `ZipHandler`'s `updateProgress`/exception handlers built host-side.
 */
internal fun mapPhaseToUiState(context: Context, phase: InstallPhase, displayName: String?): InstallUiState =
    when (phase) {
        // Queued and Inspecting are both "not yet acquiring/committing with a known percent" --
        // classic's installZipLabel showed the same "Checking given file…" text for both.
        InstallPhase.Queued, InstallPhase.Inspecting ->
            InstallUiState.Progress(displayName, context.getString(R.string.checking_zip_file), percent = null, indeterminate = true)

        is InstallPhase.Acquiring ->
            InstallUiState.Progress(displayName, context.getString(R.string.checking_zip_file), phase.percent, indeterminate = false)

        is InstallPhase.Committing ->
            InstallUiState.Progress(displayName, context.getString(R.string.extracting_zip_file), phase.percent, indeterminate = false)

        // A fleeting, essentially invisible frame in practice -- the controller removes a
        // terminal job from `jobs` immediately after this phase is observed (see
        // InstallServiceController.runSingleJob), and the host's own jobs-drained-to-empty
        // watcher then finishes the Activity. Mapped for `when` exhaustiveness + robustness
        // against a slow collector catching this transient emission.
        InstallPhase.Done ->
            InstallUiState.Progress(displayName, context.getString(R.string.extracting_zip_file), percent = 100, indeterminate = false)

        is InstallPhase.AwaitingDecision -> when (val request = phase.request) {
            is DecisionRequest.Overwrite -> InstallUiState.Overwrite(request.files)
            is DecisionRequest.StudyPadImport -> InstallUiState.StudyPadImport(request.statsText)
            DecisionRequest.EpubUpgrade -> InstallUiState.EpubUpgrade
        }

        is InstallPhase.Error -> InstallUiState.Error(resolveErrorMessage(context, phase))

        // Same "fleeting terminal frame" note as Done above.
        InstallPhase.Cancelled -> InstallUiState.Error(context.getString(R.string.install_zip_canceled))
    }

/** Resolves [InstallPhase.Error]'s `R.string` id (+ optional format arg) to final text. */
internal fun resolveErrorMessage(context: Context, error: InstallPhase.Error): String =
    if (error.arg != null) context.getString(error.messageKey, error.arg) else context.getString(error.messageKey)

/** Same SAF MIME allow-list classic `InstallZip.getFileFromUserAndInstall` passes to
 *  `ACTION_OPEN_DOCUMENT` (`EXTRA_MIME_TYPES`), verbatim. */
private val SAF_MIME_TYPES = arrayOf(
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

/**
 * Compose host for InstallZip (Plan B), behind `use_compose_ui` (routed via `Screen.InstallZip`,
 * Task B4). Handles entry dispatch (ACTION_VIEW/SEND/SEND_MULTIPLE/picker), the guaranteed-present
 * prelude (confirm-install / format-info + SAF pick) with [InstallZipContent] itself, then starts
 * the shared foreground [DocumentInstallService] and renders its [DocumentInstallService.controller]
 * jobs -- including answering its ask-back [DecisionRequest]s -- via the same composable.
 *
 * Unlike [StartupComposeActivity]/[CalculatorComposeActivity] this does NOT override
 * [doNotInitializeApp]: classic `InstallZip` never overrides it either (defaults `false`), and the
 * `doNotInitializeApp` Intent extra some callers (Startup/StartupCompose's "Import" action) set on
 * their `InstallZip`-bound Intent is not read by classic `InstallZip` at all -- `ActivityBase` only
 * consults the *class-level* `doNotInitializeApp` override, never an Intent extra of that name.
 * "Behave as classic" for this host therefore means: leave the default (app init runs normally),
 * matching classic's actual (if perhaps accidental) behaviour rather than the extra's apparent intent.
 */
class InstallZipComposeActivity : ActivityBase() {

    /** Must be registered before STARTED (a field initializer runs during construction, well
     *  before `onCreate`) -- see [androidx.activity.result.ActivityResultCaller]. Proceeds
     *  regardless of the grant result; only the FGS's own notification is affected by a denial. */
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op */ }

    private var preludeState by mutableStateOf<InstallUiState?>(null)
    private var pendingConfirm: PendingConfirm? = null

    /** Set true the moment THIS host actually calls [enqueue] (starts the service for a source it
     *  dispatched/picked itself). Gates the jobs-drained-to-empty finish in [Content] -- see
     *  [shouldFinishOnDrain]'s doc for why this must NOT be inferred from merely observing the
     *  shared jobs queue become non-empty. Not Compose state: it is only ever read from inside
     *  the [LaunchedEffect] body in [Content], which re-reads it fresh on every `jobs` change --
     *  it does not itself need to trigger recomposition. */
    private var enqueuedHere = false

    private data class PendingConfirm(val uri: Uri, val action: String)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        maybeRequestNotificationPermission()

        setContent {
            AbAppTheme {
                    Content()
            }
        }

        dispatchEntry()
    }

    /** Suppresses the service's action-required notification while this host is visible (it
     *  surfaces [InstallPhase.AwaitingDecision] in-app instead). Paired with [onStop] -- NOT
     *  [onPause] -- so a transient system dialog on top (e.g. the SAF picker itself) doesn't
     *  flip the notification on and off. */
    override fun onStart() {
        super.onStart()
        DocumentInstallService.hostBound()
    }

    override fun onStop() {
        DocumentInstallService.hostUnbound()
        super.onStop()
    }

    @Composable
    private fun Content() {
        val jobs by DocumentInstallService.controller.jobs.collectAsState()

        LaunchedEffect(jobs) {
            if (shouldFinishOnDrain(enqueuedHere, jobs.isEmpty())) {
                // The queue drained back to empty after a job THIS host itself enqueued ran --
                // the durable `UpdateMainBibleActivityDocuments` refresh event was already posted
                // by the service itself (`DocumentInstallService.postTerminalEvents`).
                finishWithResult(RESULT_OK)
            }
        }

        val prelude = preludeState
        val activeJob = pickActiveJob(jobs)
        val state = prelude ?: activeJob?.let { mapPhaseToUiState(this@InstallZipComposeActivity, it.phase, it.displayName) }

        if (state != null) {
            InstallZipContent(
                state = state,
                onConfirm = { onStateConfirm(prelude, activeJob) },
                onDismiss = { onStateDismiss(prelude, activeJob) },
            )
        }
    }

    private fun onStateConfirm(prelude: InstallUiState?, activeJob: InstallJobState?) {
        when (prelude) {
            is InstallUiState.ConfirmInstall -> {
                val pending = pendingConfirm
                preludeState = null
                pendingConfirm = null
                if (pending != null) enqueue(listOf(pending.uri), pending.action)
                else finishWithResult(RESULT_CANCELED)
            }

            is InstallUiState.FormatInfo -> {
                preludeState = null
                launchFilePicker()
            }

            // No host-local prelude showing -- this confirm belongs to the active job's
            // mid-flight decision dialog (Overwrite/StudyPadImport/EpubUpgrade).
            null -> activeJob?.let { DocumentInstallService.resolveDecision(it.jobId, true) }

            else -> Unit // Progress/Overwrite/StudyPadImport/EpubUpgrade/Error never set as preludeState.
        }
    }

    private fun onStateDismiss(prelude: InstallUiState?, activeJob: InstallJobState?) {
        if (prelude != null) {
            preludeState = null
            pendingConfirm = null
            finishWithResult(RESULT_CANCELED)
        } else {
            // Job-driven decision dialog declined, or the Error dialog's OK button -- either way
            // a no-op if the job already reached a terminal phase (its decision was consumed/
            // removed already), harmless otherwise.
            activeJob?.let { DocumentInstallService.resolveDecision(it.jobId, false) }
        }
    }

    private fun dispatchEntry() {
        val originalIntent = intent
        lifecycleScope.launch {
            when (val decision = classifyEntry(originalIntent) { uri -> isStudyPadExportUri(uri) }) {
                is InstallZipEntryDecision.EnqueueNow -> enqueue(decision.uris, decision.action)

                is InstallZipEntryDecision.ConfirmThenEnqueue -> {
                    pendingConfirm = PendingConfirm(decision.uri, decision.action)
                    preludeState = InstallUiState.ConfirmInstall(getDisplayName(decision.uri))
                }

                InstallZipEntryDecision.PickFile -> preludeState = InstallUiState.FormatInfo(buildFormatsText())

                InstallZipEntryDecision.Invalid -> finishWithResult(RESULT_CANCELED)
            }
        }
    }

    private suspend fun isStudyPadExportUri(uri: Uri): Boolean =
        AndBibleBackupManifest.fromUri(uri)?.backupType == BackupType.STUDYPAD_EXPORT

    private fun launchFilePicker() {
        lifecycleScope.launch {
            val pickerIntent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                type = "*/*"
                putExtra(Intent.EXTRA_MIME_TYPES, SAF_MIME_TYPES)
            }
            val result = try {
                awaitIntent(pickerIntent)
            } catch (e: ActivityNotFoundException) {
                // Some devices lack a documents UI / file manager to handle ACTION_OPEN_DOCUMENT.
                Log.e(TAG, "No activity found to handle ACTION_OPEN_DOCUMENT", e)
                ABEventBus.post(ToastEvent(getString(R.string.no_file_manager)))
                finishWithResult(RESULT_CANCELED)
                return@launch
            }
            val uri = result.data?.data
            if (result.resultCode == RESULT_OK && uri != null) {
                enqueue(listOf(uri), null)
            } else {
                finishWithResult(RESULT_CANCELED)
            }
        }
    }

    private fun enqueue(uris: List<Uri>, action: String?) {
        enqueuedHere = true
        ContextCompat.startForegroundService(this, DocumentInstallService.enqueueIntent(this, uris, action))
    }

    private fun maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun finishWithResult(resultCode: Int) {
        setResult(resultCode)
        finish()
    }

    /** Mirrors classic `InstallZip.getDisplayName` exactly (also duplicated, with the same
     *  justification, by `DocumentInstallService.getDisplayName`). */
    private fun getDisplayName(uri: Uri): String? =
        contentResolver.query(uri, null, null, null, null)?.use {
            if (it.isLast) return null
            it.moveToFirst()
            val idx = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx < 0) null else it.getString(idx)
        }

    /** Builds the supported-formats blurb for the no-action/in-app-launched prelude, exactly as
     *  classic `InstallZip.getFileFromUserAndInstall` does. */
    private fun buildFormatsText(): String {
        val zip = getString(R.string.format_zip, getString(R.string.app_name_andbible))
        val myBible = getString(R.string.format_mybible)
        val mySword = getString(R.string.format_mysword)
        val eSword = getString(R.string.format_esword)
        val epub = getString(R.string.format_epub)
        val studyPads = getString(R.string.format_studypads)
        val ttf = getString(R.string.format_ttf)
        val csvPrompts = getString(R.string.format_csv_prompts)
        return getString(R.string.choose_file, getString(R.string.app_name_andbible)) + " \n\n" +
            getString(R.string.supported_formats, "$zip, $myBible, $mySword, $eSword, $epub, $ttf, $csvPrompts, $studyPads")
    }
}
