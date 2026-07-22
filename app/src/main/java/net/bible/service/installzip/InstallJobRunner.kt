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

import kotlinx.coroutines.ensureActive
import net.bible.android.activity.R
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import kotlin.coroutines.coroutineContext

/**
 * Pure `suspend` state machine that drives one install job from an [InstallSource] to a terminal
 * [InstallPhase], mirroring classic `InstallZip`'s dispatch but headless: acquiring the stream to
 * local storage, classifying it via [InstallInspector], pausing for a user decision when one is
 * required (overwrite / StudyPad import / epub upgrade), and committing via [InstallCommitter].
 *
 * No I/O beyond the plain file copy in [acquire] is done directly here -- classification and
 * commit side effects are fully delegated to the injected collaborators, so this class stays
 * unit-testable with fakes (see `InstallJobRunnerTest`).
 */
class InstallJobRunner(
    private val inspector: InstallInspector,
    private val committer: InstallCommitter,
) {
    /**
     * Full per-job state machine. [openStream] streams the *original* source (Service holds the
     * forwarded URI grant); [acquireTo] is the local temp target. Emits every phase via [onPhase];
     * pauses on [awaitDecision] which returns true=proceed / false=cancel; honors coroutine
     * cancellation. Always deletes [acquireTo] before returning. Returns the terminal phase.
     */
    suspend fun runJob(
        source: InstallSource,
        acquireTo: File,
        openStream: suspend () -> InputStream,
        isStudyPadExport: suspend (source: InstallSource) -> Boolean,
        studyPadStats: suspend (File) -> Pair<String, File>,
        swordZipScan: suspend (File) -> InstallInspector.SwordZipScan,
        epubUpgradeCheck: suspend (displayName: String) -> Boolean,
        onPhase: (InstallPhase) -> Unit,
        awaitDecision: suspend (DecisionRequest) -> Boolean,
    ): InstallPhase {
        try {
            return runInternal(
                source, acquireTo, openStream, isStudyPadExport, studyPadStats,
                swordZipScan, epubUpgradeCheck, onPhase, awaitDecision,
            )
        } finally {
            acquireTo.delete()
        }
    }

    private suspend fun runInternal(
        source: InstallSource,
        acquireTo: File,
        openStream: suspend () -> InputStream,
        isStudyPadExport: suspend (source: InstallSource) -> Boolean,
        studyPadStats: suspend (File) -> Pair<String, File>,
        swordZipScan: suspend (File) -> InstallInspector.SwordZipScan,
        epubUpgradeCheck: suspend (displayName: String) -> Boolean,
        onPhase: (InstallPhase) -> Unit,
        awaitDecision: suspend (DecisionRequest) -> Boolean,
    ): InstallPhase {
        val displayName = source.displayName ?: source.uri.lastPathSegment ?: acquireTo.name

        onPhase(InstallPhase.Acquiring(0))
        try {
            acquire(openStream, acquireTo) { percent -> onPhase(InstallPhase.Acquiring(percent)) }
        } catch (e: IOException) {
            return InstallPhase.Error(R.string.error_occurred, null).also(onPhase)
        }

        onPhase(InstallPhase.Inspecting)
        val plan = inspector.inspect(
            acquireTo, displayName, source.mimeType, isStudyPadExport(source),
            studyPadStats, swordZipScan, epubUpgradeCheck,
        )

        val terminal = when (plan) {
            is InstallPlan.Invalid ->
                InstallPhase.Error(R.string.sqlite_invalid_file, plan.filename)

            is InstallPlan.SwordZip -> {
                if (plan.existingFiles.isNotEmpty()) {
                    val decision = DecisionRequest.Overwrite(plan.existingFiles)
                    onPhase(InstallPhase.AwaitingDecision(decision))
                    if (!awaitDecision(decision)) return InstallPhase.Cancelled.also(onPhase)
                }
                commitCatching(displayName) {
                    committer.commitSwordZip(acquireTo, plan.totalEntries) { percent ->
                        onPhase(InstallPhase.Committing(percent))
                    }
                    true
                }
            }

            is InstallPlan.EpubFromZip ->
                commitWithProgress(onPhase, plan.displayName) {
                    committer.commitEpub(acquireTo, plan.displayName, deleteExisting = true)
                }

            is InstallPlan.Epub -> {
                if (plan.needsUpgradeConfirm) {
                    onPhase(InstallPhase.AwaitingDecision(DecisionRequest.EpubUpgrade))
                    if (!awaitDecision(DecisionRequest.EpubUpgrade)) return InstallPhase.Cancelled.also(onPhase)
                }
                commitWithProgress(onPhase, plan.displayName) {
                    committer.commitEpub(acquireTo, plan.displayName, deleteExisting = true)
                }
            }

            is InstallPlan.Sqlite -> {
                if (!awaitOverwriteIfNeeded(plan.overwriteName, onPhase, awaitDecision)) {
                    return InstallPhase.Cancelled.also(onPhase)
                }
                commitWithProgress(onPhase, plan.displayName) {
                    committer.commitSqlite(acquireTo, plan.type, plan.displayName)
                    true
                }
            }

            is InstallPlan.Ttf -> {
                if (!awaitOverwriteIfNeeded(plan.overwriteName, onPhase, awaitDecision)) {
                    return InstallPhase.Cancelled.also(onPhase)
                }
                commitWithProgress(onPhase, plan.displayName) {
                    committer.commitTtf(acquireTo, plan.displayName)
                    true
                }
            }

            is InstallPlan.Csv -> {
                if (!awaitOverwriteIfNeeded(plan.overwriteName, onPhase, awaitDecision)) {
                    return InstallPhase.Cancelled.also(onPhase)
                }
                commitWithProgress(onPhase, plan.displayName) {
                    committer.commitCsv(acquireTo, plan.displayName)
                    true
                }
            }

            is InstallPlan.BackgroundImage -> {
                if (!awaitOverwriteIfNeeded(plan.overwriteName, onPhase, awaitDecision)) {
                    return InstallPhase.Cancelled.also(onPhase)
                }
                commitWithProgress(onPhase, plan.fileName) {
                    committer.commitBackgroundImage(acquireTo, plan.fileName)
                    true
                }
            }

            is InstallPlan.StudyPad -> {
                val decision = DecisionRequest.StudyPadImport(plan.statsText)
                onPhase(InstallPhase.AwaitingDecision(decision))
                if (!awaitDecision(decision)) {
                    plan.unzipFolder.deleteRecursively()
                    return InstallPhase.Cancelled.also(onPhase)
                }
                commitWithProgress(onPhase, displayName) {
                    committer.commitStudyPad(plan.unzipFolder)
                    true
                }
            }
        }
        return terminal.also(onPhase)
    }

    /** @return false when a required overwrite decision is declined (caller should Cancel). */
    private suspend fun awaitOverwriteIfNeeded(
        overwriteName: String?,
        onPhase: (InstallPhase) -> Unit,
        awaitDecision: suspend (DecisionRequest) -> Boolean,
    ): Boolean {
        if (overwriteName == null) return true
        val decision = DecisionRequest.Overwrite(listOf(overwriteName))
        onPhase(InstallPhase.AwaitingDecision(decision))
        return awaitDecision(decision)
    }

    /** Committing(0) -> [block] -> Committing(100) on success; maps engine failures to a terminal Error. */
    private suspend fun commitWithProgress(
        onPhase: (InstallPhase) -> Unit,
        displayName: String,
        block: suspend () -> Boolean,
    ): InstallPhase {
        onPhase(InstallPhase.Committing(0))
        return commitCatching(displayName) {
            val ok = block()
            onPhase(InstallPhase.Committing(100))
            ok
        }
    }

    /** Maps [InvalidInstallFile] / a `false` commit result / an engine [IOException] to a terminal Error. */
    private suspend fun commitCatching(displayName: String, block: suspend () -> Boolean): InstallPhase = try {
        if (block()) InstallPhase.Done else InstallPhase.Error(R.string.sqlite_invalid_file, displayName)
    } catch (e: InvalidInstallFile) {
        InstallPhase.Error(R.string.sqlite_invalid_file, e.filename)
    } catch (e: IOException) {
        InstallPhase.Error(R.string.error_occurred, null)
    }

    /** Copies [openStream] to [acquireTo], reporting best-effort percent and honoring cancellation. */
    private suspend fun acquire(
        openStream: suspend () -> InputStream,
        acquireTo: File,
        onProgress: (Int) -> Unit,
    ) {
        openStream().use { input ->
            FileOutputStream(acquireTo).use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var totalRead = 0L
                val totalHint = try { input.available().toLong() } catch (e: IOException) { 0L }
                while (true) {
                    coroutineContext.ensureActive()
                    val read = input.read(buffer)
                    if (read == -1) break
                    output.write(buffer, 0, read)
                    totalRead += read
                    if (totalHint > 0) {
                        onProgress(((totalRead * 100) / totalHint).toInt().coerceIn(0, 99))
                    }
                }
            }
        }
        onProgress(100)
    }

    companion object {
        private const val DEFAULT_BUFFER_SIZE = 8 * 1024
    }
}
