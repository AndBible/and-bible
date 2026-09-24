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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import net.bible.android.TEST_SDK
import net.bible.service.installzip.DecisionRequest
import net.bible.service.installzip.InstallJobState
import net.bible.service.installzip.InstallPhase
import net.bible.sharedcore.nav.InstallZipResult
import net.bible.sharedui.installzip.InstallUiState
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Slice 8 D1: the InstallZip state machine, moved out of `InstallZipComposeActivity` (A49) so the
 * destination can host it. Same rules as the Activity had: confirm-first for a plain ACTION_VIEW, no
 * confirm for a StudyPad export or a SEND, the formats prelude + picker for an in-app entry, RESULT_OK only
 * once a job THIS entry enqueued has drained (`shouldFinishOnDrain`), CANCELED on every other way out --
 * and exactly one answer.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class InstallZipFlowTest {

    private val jobs = MutableStateFlow<List<InstallJobState>>(emptyList())
    private val enqueued = mutableListOf<Pair<List<Uri>, String?>>()
    private val decisions = mutableListOf<Pair<String, Boolean>>()
    private val results = mutableListOf<InstallZipResult>()
    private var pickerAnswer: Uri? = null
    private var studyPad = false
    private val uri = Uri.parse("content://x/module.zip")
    private val uri2 = Uri.parse("content://x/other.epub")

    private fun flow(scope: CoroutineScope, action: String?, uris: List<Uri>) = InstallZipFlow(
        scope = scope,
        action = action,
        uris = uris,
        seams = InstallZipFlow.Seams(
            jobs = jobs,
            isStudyPadExport = { studyPad },
            displayName = { "module.zip" },
            formatsText = { "formats" },
            pickFile = { pickerAnswer },
            enqueue = { list, act -> enqueued += list to act },
            resolveDecision = { id, ok -> decisions += id to ok },
            mapPhase = { InstallUiState.Progress(it.displayName, "status", percent = null, indeterminate = true) },
            requestNotificationPermission = {},
            noFileManager = {},
        ),
        onFinished = { results += it },
    )

    private val running = InstallJobState("job-1", "module.zip", InstallPhase.Acquiring(10))

    @Test
    fun anExternalViewAsksFirstThenFinishesOkWhenItsJobDrains() = runTest(UnconfinedTestDispatcher()) {
        val f = flow(backgroundScope, Intent.ACTION_VIEW, listOf(uri))
        f.start()
        assertEquals(InstallUiState.ConfirmInstall("module.zip"), f.state.value)

        f.confirm()
        assertEquals(listOf(listOf(uri) to Intent.ACTION_VIEW), enqueued)
        jobs.value = listOf(running)
        jobs.value = emptyList()

        assertEquals(listOf(InstallZipResult.OK), results)
    }

    @Test
    fun aStudyPadExportIsEnqueuedWithoutTheConfirm() = runTest(UnconfinedTestDispatcher()) {
        studyPad = true
        flow(backgroundScope, Intent.ACTION_VIEW, listOf(uri)).start()
        assertEquals(listOf(listOf(uri) to Intent.ACTION_VIEW), enqueued)
    }

    @Test
    fun sendMultipleEnqueuesEveryUri() = runTest(UnconfinedTestDispatcher()) {
        flow(backgroundScope, Intent.ACTION_SEND_MULTIPLE, listOf(uri, uri2)).start()
        assertEquals(listOf(listOf(uri, uri2) to Intent.ACTION_SEND_MULTIPLE), enqueued)
    }

    @Test
    fun anInAppEntryShowsTheFormatsThenPicks() = runTest(UnconfinedTestDispatcher()) {
        val f = flow(backgroundScope, null, emptyList())
        f.start()
        assertEquals(InstallUiState.FormatInfo("formats"), f.state.value)
        pickerAnswer = uri
        f.confirm()
        assertEquals(listOf(listOf(uri) to null), enqueued)
    }

    @Test
    fun aCancelledPickerFinishesCanceled() = runTest(UnconfinedTestDispatcher()) {
        val f = flow(backgroundScope, null, emptyList())
        f.start()
        f.confirm()
        assertEquals(listOf(InstallZipResult.CANCELED), results)
    }

    @Test
    fun dismissingThePreludeFinishesCanceled() = runTest(UnconfinedTestDispatcher()) {
        val f = flow(backgroundScope, Intent.ACTION_VIEW, listOf(uri))
        f.start()
        f.dismiss()
        assertEquals(listOf(InstallZipResult.CANCELED), results)
        assertEquals(emptyList<Pair<List<Uri>, String?>>(), enqueued)
    }

    @Test
    fun backWhileAJobRunsAnswersCanceledExactlyOnce() = runTest(UnconfinedTestDispatcher()) {
        val f = flow(backgroundScope, Intent.ACTION_SEND, listOf(uri))
        f.start()
        jobs.value = listOf(running)
        f.back()
        jobs.value = emptyList()
        assertEquals("the install keeps running in the service; the entry answers once", listOf(InstallZipResult.CANCELED), results)
    }

    @Test
    fun aForeignJobDrainingDoesNotFinishAnEntryThatEnqueuedNothing() = runTest(UnconfinedTestDispatcher()) {
        flow(backgroundScope, null, emptyList()).start()
        jobs.value = listOf(running)
        jobs.value = emptyList()
        assertEquals(emptyList<InstallZipResult>(), results)
    }

    @Test
    fun aJobsDecisionIsAnsweredByConfirmAndDismiss() = runTest(UnconfinedTestDispatcher()) {
        val f = flow(backgroundScope, Intent.ACTION_SEND, listOf(uri))
        f.start()
        jobs.value = listOf(InstallJobState("job-2", "module.zip", InstallPhase.AwaitingDecision(DecisionRequest.EpubUpgrade)))
        f.confirm()
        f.dismiss()
        assertEquals(listOf("job-2" to true, "job-2" to false), decisions)
    }

    @Test
    fun theRouteClassificationMirrorsTheIntentOne() = runTest {
        assertEquals(InstallZipEntryDecision.PickFile, classifyInstallZipRoute(null, emptyList()) { false })
        assertEquals(InstallZipEntryDecision.ConfirmThenEnqueue(uri, Intent.ACTION_VIEW), classifyInstallZipRoute(Intent.ACTION_VIEW, listOf(uri)) { false })
        assertEquals(InstallZipEntryDecision.EnqueueNow(listOf(uri), Intent.ACTION_VIEW), classifyInstallZipRoute(Intent.ACTION_VIEW, listOf(uri)) { true })
        assertEquals(InstallZipEntryDecision.EnqueueNow(listOf(uri), Intent.ACTION_SEND), classifyInstallZipRoute(Intent.ACTION_SEND, listOf(uri)) { false })
        assertEquals(InstallZipEntryDecision.Invalid, classifyInstallZipRoute(Intent.ACTION_SEND, emptyList()) { false })
    }
}
