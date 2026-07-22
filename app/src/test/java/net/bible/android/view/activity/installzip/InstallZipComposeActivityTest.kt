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

import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.activity.R
import net.bible.service.installzip.DecisionRequest
import net.bible.service.installzip.InstallJobState
import net.bible.service.installzip.InstallPhase
import net.bible.sharedui.installzip.InstallUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Coverage for [InstallZipComposeActivity]'s *pure* entry-dispatch classifier ([classifyEntry]),
 * job-picking ([pickActiveJob]), and phase→UI-state mapping ([mapPhaseToUiState]/
 * [resolveErrorMessage]) -- extracted precisely so this logic is testable without driving the
 * whole Activity/lifecycle/Compose tree (see Task B3 brief).
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class InstallZipComposeActivityTest {

    private val context get() = ApplicationProvider.getApplicationContext<TestBibleApplication>()
    private val uri: Uri = Uri.parse("content://net.bible.provider/tmp/module.zip")
    private val uri2: Uri = Uri.parse("content://net.bible.provider/tmp/module2.zip")

    // --- classifyEntry ---

    @Test
    fun actionView_nonStudyPad_confirmsFirst() = runBlocking {
        val intent = Intent(Intent.ACTION_VIEW).setData(uri)
        val decision = classifyEntry(intent) { false }
        assertEquals(InstallZipEntryDecision.ConfirmThenEnqueue(uri, Intent.ACTION_VIEW), decision)
    }

    @Test
    fun actionView_studyPadExport_enqueuesWithoutConfirm() = runBlocking {
        val intent = Intent(Intent.ACTION_VIEW).setData(uri)
        val decision = classifyEntry(intent) { true }
        assertEquals(InstallZipEntryDecision.EnqueueNow(listOf(uri), Intent.ACTION_VIEW), decision)
    }

    @Test
    fun actionView_missingData_isInvalid() = runBlocking {
        val intent = Intent(Intent.ACTION_VIEW)
        val decision = classifyEntry(intent) { false }
        assertEquals(InstallZipEntryDecision.Invalid, decision)
    }

    @Test
    fun actionSend_enqueuesWithoutConfirm() = runBlocking {
        val intent = Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uri)
        val decision = classifyEntry(intent) { error("must not be consulted for ACTION_SEND") }
        assertEquals(InstallZipEntryDecision.EnqueueNow(listOf(uri), Intent.ACTION_SEND), decision)
    }

    @Test
    fun actionSend_missingStream_isInvalid() = runBlocking {
        val intent = Intent(Intent.ACTION_SEND)
        val decision = classifyEntry(intent) { false }
        assertEquals(InstallZipEntryDecision.Invalid, decision)
    }

    @Test
    fun actionSendMultiple_enqueuesAllWithoutConfirm() = runBlocking {
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE)
            .putParcelableArrayListExtra(Intent.EXTRA_STREAM, arrayListOf(uri, uri2))
        val decision = classifyEntry(intent) { error("must not be consulted for ACTION_SEND_MULTIPLE") }
        assertEquals(InstallZipEntryDecision.EnqueueNow(listOf(uri, uri2), Intent.ACTION_SEND_MULTIPLE), decision)
    }

    @Test
    fun actionSendMultiple_emptyList_isInvalid() = runBlocking {
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE)
            .putParcelableArrayListExtra(Intent.EXTRA_STREAM, arrayListOf<Uri>())
        val decision = classifyEntry(intent) { false }
        assertEquals(InstallZipEntryDecision.Invalid, decision)
    }

    @Test
    fun noAction_showsFormatInfoPickerPrelude() = runBlocking {
        val intent = Intent()
        val decision = classifyEntry(intent) { error("must not be consulted with no action") }
        assertEquals(InstallZipEntryDecision.PickFile, decision)
    }

    @Test
    fun nullIntent_showsFormatInfoPickerPrelude() = runBlocking {
        val decision = classifyEntry(null) { error("must not be consulted with a null intent") }
        assertEquals(InstallZipEntryDecision.PickFile, decision)
    }

    // --- pickActiveJob ---

    @Test
    fun pickActiveJob_empty_returnsNull() {
        assertNull(pickActiveJob(emptyList()))
    }

    @Test
    fun pickActiveJob_prioritizesAwaitingDecisionOverEarlierJob() {
        val queued = InstallJobState("job-1", "first.zip", InstallPhase.Acquiring(50))
        val awaiting = InstallJobState("job-2", "second.zip", InstallPhase.AwaitingDecision(DecisionRequest.EpubUpgrade))
        assertEquals(awaiting, pickActiveJob(listOf(queued, awaiting)))
    }

    @Test
    fun pickActiveJob_fallsBackToFirst_whenNoneAwaiting() {
        val first = InstallJobState("job-1", "first.zip", InstallPhase.Acquiring(10))
        val second = InstallJobState("job-2", "second.zip", InstallPhase.Inspecting)
        assertEquals(first, pickActiveJob(listOf(first, second)))
    }

    // --- shouldFinishOnDrain (Task B3 finding I1: a foreign job's drain must never finish a host
    // that never enqueued anything itself) ---

    @Test
    fun shouldFinishOnDrain_foreignJobObservedThenDrained_notEnqueuedHere_doesNotFinish() {
        // A second host opens while a prior host's install still runs: the shared jobs flow goes
        // non-empty (a foreign job), then drains back to empty -- but THIS host never enqueued
        // anything (still at its ConfirmInstall/FormatInfo prelude), so it must not finish.
        assertEquals(false, shouldFinishOnDrain(enqueuedHere = false, jobsEmpty = false))
        assertEquals(false, shouldFinishOnDrain(enqueuedHere = false, jobsEmpty = true))
    }

    @Test
    fun shouldFinishOnDrain_enqueuedHere_andJobsDrained_finishes() {
        assertEquals(true, shouldFinishOnDrain(enqueuedHere = true, jobsEmpty = true))
    }

    @Test
    fun shouldFinishOnDrain_enqueuedHere_butJobsStillRunning_doesNotFinish() {
        assertEquals(false, shouldFinishOnDrain(enqueuedHere = true, jobsEmpty = false))
    }

    // --- mapPhaseToUiState ---

    @Test
    fun acquiring_mapsToCheckingProgress_determinate() {
        val state = mapPhaseToUiState(context, InstallPhase.Acquiring(42), "module.zip")
        assertEquals(
            InstallUiStateProgress("module.zip", context.getString(R.string.checking_zip_file), 42, false),
            state.asProgress(),
        )
    }

    @Test
    fun inspecting_mapsToCheckingProgress_indeterminate() {
        val state = mapPhaseToUiState(context, InstallPhase.Inspecting, "module.zip")
        assertEquals(
            InstallUiStateProgress("module.zip", context.getString(R.string.checking_zip_file), null, true),
            state.asProgress(),
        )
    }

    @Test
    fun queued_mapsToCheckingProgress_indeterminate() {
        val state = mapPhaseToUiState(context, InstallPhase.Queued, "module.zip")
        assertEquals(
            InstallUiStateProgress("module.zip", context.getString(R.string.checking_zip_file), null, true),
            state.asProgress(),
        )
    }

    @Test
    fun committing_mapsToExtractingProgress_withPercent() {
        val state = mapPhaseToUiState(context, InstallPhase.Committing(77), "module.zip")
        assertEquals(
            InstallUiStateProgress("module.zip", context.getString(R.string.extracting_zip_file), 77, false),
            state.asProgress(),
        )
    }

    @Test
    fun done_mapsToExtractingProgress_complete() {
        val state = mapPhaseToUiState(context, InstallPhase.Done, "module.zip")
        assertEquals(
            InstallUiStateProgress("module.zip", context.getString(R.string.extracting_zip_file), 100, false),
            state.asProgress(),
        )
    }

    @Test
    fun awaitingOverwrite_mapsToOverwriteState() {
        val files = listOf("mods.d/a.conf", "mods.d/b.conf")
        val phase = InstallPhase.AwaitingDecision(DecisionRequest.Overwrite(files))
        val state = mapPhaseToUiState(context, phase, "module.zip")
        assertEquals(InstallUiState.Overwrite(files), state)
    }

    @Test
    fun awaitingStudyPadImport_mapsToStudyPadImportState() {
        val phase = InstallPhase.AwaitingDecision(DecisionRequest.StudyPadImport("3 bookmarks, 1 label"))
        val state = mapPhaseToUiState(context, phase, "backup.abdb")
        assertEquals(InstallUiState.StudyPadImport("3 bookmarks, 1 label"), state)
    }

    @Test
    fun awaitingEpubUpgrade_mapsToEpubUpgradeState() {
        val phase = InstallPhase.AwaitingDecision(DecisionRequest.EpubUpgrade)
        val state = mapPhaseToUiState(context, phase, "book.epub")
        assertEquals(InstallUiState.EpubUpgrade, state)
    }

    @Test
    fun error_withoutArg_mapsToResolvedMessage() {
        val phase = InstallPhase.Error(R.string.error_occurred, null)
        val state = mapPhaseToUiState(context, phase, "module.zip")
        assertEquals(
            InstallUiState.Error(context.getString(R.string.error_occurred)),
            state,
        )
    }

    @Test
    fun error_withArg_mapsToResolvedMessageWithArgSubstituted() {
        val phase = InstallPhase.Error(R.string.sqlite_invalid_file, "bad.zip")
        val state = mapPhaseToUiState(context, phase, "module.zip")
        assertEquals(
            InstallUiState.Error(context.getString(R.string.sqlite_invalid_file, "bad.zip")),
            state,
        )
    }

    @Test
    fun cancelled_mapsToCancelledMessage() {
        val state = mapPhaseToUiState(context, InstallPhase.Cancelled, "module.zip")
        assertEquals(
            InstallUiState.Error(context.getString(R.string.install_zip_canceled)),
            state,
        )
    }

    // Small helper: pulls the four fields out of an InstallUiState.Progress for a compact assertEquals
    // (InstallUiState.Progress is already a data class, but comparing it directly reads awkwardly
    // wrapped through .let{} at every call site -- this keeps the test bodies terse).
    private data class InstallUiStateProgress(
        val displayName: String?,
        val statusText: String,
        val percent: Int?,
        val indeterminate: Boolean,
    )

    private fun InstallUiState.asProgress(): InstallUiStateProgress? =
        (this as? InstallUiState.Progress)?.let {
            InstallUiStateProgress(it.displayName, it.statusText, it.percent, it.indeterminate)
        }
}
