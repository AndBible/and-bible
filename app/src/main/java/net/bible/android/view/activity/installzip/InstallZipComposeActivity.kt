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

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.bible.android.activity.R
import net.bible.android.view.activity.nav.NavHostComposeActivity
import net.bible.service.installzip.DecisionRequest
import net.bible.service.installzip.InstallJobState
import net.bible.service.installzip.InstallPhase
import net.bible.sharedcore.nav.NavRoutes
import net.bible.sharedui.installzip.InstallUiState

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
 * The URI-grant flags an Intent needs to keep the sender's `content://` grant readable
 * (see [composeForwardIntent]) -- and ONLY these; deliberately excludes any `FLAG_ACTIVITY_*`.
 */
private const val URI_GRANT_FLAGS =
    Intent.FLAG_GRANT_READ_URI_PERMISSION or
        Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
        Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
        Intent.FLAG_GRANT_PREFIX_URI_PERMISSION

/**
 * Readdresses an inbound Intent to the nav host, keeping the sender's URI grant (slice 8 §3.2: it
 * IS the forwarding hop again -- [InstallZipComposeActivity] redirects every external module-file
 * Intent to the host's INSTALL_ZIP destination through it).
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
    if (original == null) Intent(context, NavHostComposeActivity::class.java)
    else Intent(original)
        .setClass(context, NavHostComposeActivity::class.java)
        .also { it.flags = it.flags and URI_GRANT_FLAGS }

/**
 * The redirect's Intent (slice 8 §3.2): [composeForwardIntent]'s copy of [original] -- data, clipData and
 * extras, so the URI grant travels; only [URI_GRANT_FLAGS] -- carrying the INSTALL_ZIP start route built from
 * [decision]. Null for [InstallZipEntryDecision.Invalid]: nothing to install, nothing to start.
 */
internal fun installZipRedirectIntent(original: Intent?, context: Context, decision: InstallZipEntryDecision): Intent? {
    val routeArgs: Pair<String?, List<Uri>> = when (decision) {
        is InstallZipEntryDecision.ConfirmThenEnqueue -> Pair(decision.action, listOf(decision.uri))
        is InstallZipEntryDecision.EnqueueNow -> Pair(decision.action, decision.uris)
        InstallZipEntryDecision.PickFile -> Pair(null, emptyList())
        InstallZipEntryDecision.Invalid -> return null
    }
    return composeForwardIntent(original, context)
        .putExtra(NavHostComposeActivity.EXTRA_ROUTE, NavRoutes.installZip(routeArgs.first, routeArgs.second.map(Uri::toString)))
}

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

/**
 * The app's EXPORTED entry for module files (slice 8 §3.2): a file manager's "open with", the Share sheet or any
 * ACTION_VIEW on a .zip/.epub/.ttf lands here (filters in `src/standard/AndroidManifest.xml`; discrete overrides
 * the label) and is redirected to the nav host's INSTALL_ZIP destination in the SAME task, then this finishes.
 * The destination's exit finishes the host, so the user returns to the calling app's task. No result reaches the
 * caller: the redirect is a plain `startActivity` without `FLAG_ACTIVITY_FORWARD_RESULT`, and this Activity has
 * already finished, so delivering an external result is not a supported contract of this entry.
 *
 * A plain [ComponentActivity], not an `ActivityBase`: it draws nothing, initialises nothing and never resumes
 * from a pause -- which also means it never triggers the calculator, exactly like the Activity it replaces
 * (the external-install calculator bypass is recorded, not fixed; spec §2.1).
 *
 * [classifyEntry] runs with `isStudyPadExport = { false }`: the redirect needs only the URIs and validity; the
 * destination re-classifies with the real manifest peek ([classifyInstallZipRoute]). `Main.immediate` so the
 * whole redirect completes inside `onCreate`.
 */
class InstallZipComposeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch(Dispatchers.Main.immediate) {
            val decision = classifyEntry(intent) { false }
            val target = installZipRedirectIntent(intent, this@InstallZipComposeActivity, decision)
            if (target != null) startActivity(target)
            finish()
        }
    }
}
