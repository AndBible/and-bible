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

import android.net.Uri
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.activity.R
import net.bible.android.control.backup.BackupControl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Exercises [InstallServiceController]'s queue/StateFlow/decision-channel/aggregate-outcome
 * behavior via the `runOne` testability seam -- a scripted fake stands in for a real
 * [InstallJobRunner] so these tests assert the CONTROLLER's orchestration logic (sequencing,
 * `jobs` updates, ask-back plumbing, cancellation-safety) in isolation, not the state machine
 * (that's [InstallJobRunnerTest]'s job). One test at the bottom exercises the real default
 * `runOne` wiring end-to-end against a real [InstallJobRunner] to prove the [JobDeps] plumbing.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class InstallServiceControllerTest {

    private fun idGen(): () -> JobId {
        val n = AtomicInteger(0)
        return { "job-${n.getAndIncrement()}" }
    }

    private fun src(name: String) = InstallSource(Uri.parse("content://x/$name"), null, name, null)

    private fun deps() = InstallServiceController.JobDeps(
        acquireDir = File(System.getProperty("java.io.tmpdir"), "install-controller-test"),
        openStream = { error("unused: runOne is faked in these tests") },
        isStudyPadExport = { false },
        studyPadStats = { error("unused") },
        swordZipScan = { error("unused") },
        epubUpgradeCheck = { false },
    )

    private fun controller(onTerminal: (JobId, InstallPhase) -> Unit = { _, _ -> }) =
        InstallServiceController(
            runnerFactory = { error("unused: runOne is faked in these tests") },
            newJobId = idGen(),
            onTerminal = onTerminal,
        )

    @Test fun `two sources run sequentially and jobs are removed on terminal`() = runTest {
        val log = mutableListOf<String>()
        var jobsWhenSecondStarts: List<InstallJobState>? = null
        val c = controller()
        c.runOne = { _, source, _, onPhase, _ ->
            log += "start:${source.displayName}"
            onPhase(InstallPhase.Inspecting)
            if (source.displayName == "b") jobsWhenSecondStarts = c.jobs.value
            log += "end:${source.displayName}"
            InstallPhase.Done
        }

        val outcome = c.enqueue(listOf(src("a"), src("b")), deps())

        assertEquals(InstallOutcome.Ok, outcome)
        assertEquals(listOf("start:a", "end:a", "start:b", "end:b"), log)
        // "a" must already be gone from `jobs` by the time "b" starts running (sequential, not
        // concurrent) -- this is what actually distinguishes a *sequential* worker from a
        // parallel one.
        assertTrue(jobsWhenSecondStarts!!.none { it.displayName == "a" })
        assertTrue("all jobs removed once terminal", c.jobs.value.isEmpty())
    }

    @Test fun `resolveDecision true unblocks a paused job and enqueue returns Ok`() = runTest {
        val c = controller()
        c.runOne = { _, _, _, onPhase, awaitDecision ->
            onPhase(InstallPhase.AwaitingDecision(DecisionRequest.EpubUpgrade))
            if (awaitDecision(DecisionRequest.EpubUpgrade)) InstallPhase.Done else InstallPhase.Cancelled
        }
        val resolver = launch(Dispatchers.Default) {
            val jobId = c.jobs.first { list -> list.any { it.phase is InstallPhase.AwaitingDecision } }
                .first { it.phase is InstallPhase.AwaitingDecision }.jobId
            c.resolveDecision(jobId, true)
        }

        val outcome = c.enqueue(listOf(src("a")), deps())

        resolver.join()
        assertEquals(InstallOutcome.Ok, outcome)
    }

    @Test fun `cancel on a paused job cancels it and fires onTerminal`() = runTest {
        var terminalJobId: JobId? = null
        var terminalPhase: InstallPhase? = null
        val c = controller(onTerminal = { id, phase -> terminalJobId = id; terminalPhase = phase })
        c.runOne = { _, _, _, onPhase, awaitDecision ->
            onPhase(InstallPhase.AwaitingDecision(DecisionRequest.EpubUpgrade))
            if (awaitDecision(DecisionRequest.EpubUpgrade)) InstallPhase.Done else InstallPhase.Cancelled
        }
        val canceller = launch(Dispatchers.Default) {
            val jobId = c.jobs.first { list -> list.any { it.phase is InstallPhase.AwaitingDecision } }
                .first { it.phase is InstallPhase.AwaitingDecision }.jobId
            c.cancel(jobId)
        }

        val outcome = c.enqueue(listOf(src("a")), deps())

        canceller.join()
        assertEquals(InstallOutcome.Cancelled, outcome)
        assertEquals(InstallPhase.Cancelled, terminalPhase)
        assertTrue(terminalJobId != null)
        assertTrue(c.jobs.value.isEmpty())
    }

    /**
     * Finding I2: [InstallServiceController.runSingleJob] used to catch ONLY
     * [kotlinx.coroutines.CancellationException] around `runOne`, so a non-cancellation
     * `Throwable` (e.g. a `BookException`/`RuntimeException` from the reused JSword engine during
     * inspect/commit -- classic `ZipHandler` explicitly caught these) would escape the worker
     * `launch` uncaught on the handler-less `SupervisorJob` scope (a crash), while `terminal`
     * stayed at its `Cancelled` default -- misreporting a real error as a user cancellation. This
     * must now map to a terminal [InstallPhase.Error], fire [onTerminal] with it, and surface as
     * [InstallOutcome.Error] from `enqueue`, with no exception escaping the controller.
     */
    @Test fun `non-cancellation throwable from runOne maps to Error terminal, not Cancelled`() = runTest {
        var terminalJobId: JobId? = null
        var terminalPhase: InstallPhase? = null
        val c = controller(onTerminal = { id, phase -> terminalJobId = id; terminalPhase = phase })
        c.runOne = { _, _, _, _, _ -> throw RuntimeException("simulated BookException from JSword") }

        val outcome = c.enqueue(listOf(src("a")), deps())

        assertTrue(outcome is InstallOutcome.Error)
        assertEquals(R.string.error_occurred, (outcome as InstallOutcome.Error).messageKey)
        assertTrue(terminalJobId != null)
        assertTrue(terminalPhase is InstallPhase.Error)
        assertEquals(R.string.error_occurred, (terminalPhase as InstallPhase.Error).messageKey)
        assertTrue("no job may linger after an unhandled throwable", c.jobs.value.isEmpty())
    }

    /** Companion assertion: cancellation must still map to Cancelled, not the new Error catch --
     *  order of the two `catch` clauses in `runSingleJob` matters (CancellationException first). */
    @Test fun `cancellation still maps to Cancelled, not Error`() = runTest {
        var terminalPhase: InstallPhase? = null
        val c = controller(onTerminal = { _, phase -> terminalPhase = phase })
        c.runOne = { _, _, _, onPhase, awaitDecision ->
            onPhase(InstallPhase.AwaitingDecision(DecisionRequest.EpubUpgrade))
            if (awaitDecision(DecisionRequest.EpubUpgrade)) InstallPhase.Done else InstallPhase.Cancelled
        }
        val canceller = launch(Dispatchers.Default) {
            val jobId = c.jobs.first { list -> list.any { it.phase is InstallPhase.AwaitingDecision } }
                .first { it.phase is InstallPhase.AwaitingDecision }.jobId
            c.cancel(jobId)
        }

        val outcome = c.enqueue(listOf(src("a")), deps())

        canceller.join()
        assertEquals(InstallOutcome.Cancelled, outcome)
        assertEquals(InstallPhase.Cancelled, terminalPhase)
    }

    @Test fun `aggregate outcome is Error when any job errors even if another succeeds`() = runTest {
        val c = controller()
        c.runOne = { _, source, _, _, _ ->
            if (source.displayName == "bad") InstallPhase.Error(R.string.error_occurred, "bad")
            else InstallPhase.Done
        }

        val outcome = c.enqueue(listOf(src("good"), src("bad")), deps())

        assertTrue(outcome is InstallOutcome.Error)
        assertEquals(R.string.error_occurred, (outcome as InstallOutcome.Error).messageKey)
        assertEquals("bad", outcome.arg)
    }

    @Test fun `empty sources returns NothingEnqueued without touching jobs`() = runTest {
        val c = controller()
        val outcome = c.enqueue(emptyList(), deps())
        assertEquals(InstallOutcome.NothingEnqueued, outcome)
        assertTrue(c.jobs.value.isEmpty())
    }

    /**
     * The load-bearing test: cancelling the coroutine that is AWAITING [InstallServiceController
     * .enqueue] must NOT cancel the worker actually running the job. The worker is gated behind a
     * [CompletableDeferred] the CALLER's cancellation cannot reach (it's not part of the caller's
     * job hierarchy); only after explicitly completing that gate does the worker finish and fire
     * `onTerminal`, proven here with a plain [CountDownLatch] (deliberately NOT a coroutine
     * primitive, so this assertion is independent of the coroutine-cancellation machinery under
     * test).
     */
    @Test fun `cancelling enqueue's caller does not cancel the worker`() = runTest {
        val terminalLatch = CountDownLatch(1)
        var terminalPhase: InstallPhase? = null
        val c = controller(onTerminal = { _, phase -> terminalPhase = phase; terminalLatch.countDown() })
        val proceedGate = CompletableDeferred<Unit>()
        c.runOne = { _, _, _, onPhase, _ ->
            onPhase(InstallPhase.Committing(0))
            proceedGate.await()
            InstallPhase.Done
        }

        val caller = launch(Dispatchers.Default) { c.enqueue(listOf(src("a")), deps()) }
        // Wait (real suspension, no polling) until the worker is actually mid-flight.
        c.jobs.first { list -> list.any { it.phase is InstallPhase.Committing } }

        caller.cancel()
        caller.join()
        assertTrue("caller coroutine must be cancelled", caller.isCancelled)

        // The worker must still be alive and pending on the gate -- prove it by completing the
        // gate now and observing the terminal fire, well after the caller is gone.
        proceedGate.complete(Unit)
        val fired = withContext(Dispatchers.IO) { terminalLatch.await(5, TimeUnit.SECONDS) }
        assertTrue("onTerminal must still fire for a job whose caller was cancelled", fired)
        assertEquals(InstallPhase.Done, terminalPhase)
    }

    @Test fun `default runOne wires JobDeps into a real InstallJobRunner`() = runTest {
        val acquireDir = File(System.getProperty("java.io.tmpdir"), "install-controller-real-${System.nanoTime()}")
        val committer = object : InstallCommitter {
            var swordCalls = 0
            override suspend fun commitSwordZip(localFile: File, totalEntries: Int, onProgress: (Int) -> Unit) {
                swordCalls++; onProgress(100)
            }
            override suspend fun commitEpub(localFile: File, displayName: String, deleteExisting: Boolean) = true
            override suspend fun commitSqlite(localFile: File, type: SqliteBookType, displayName: String) {}
            override suspend fun commitTtf(localFile: File, displayName: String) {}
            override suspend fun commitCsv(localFile: File, displayName: String) {}
            override suspend fun commitBackgroundImage(localFile: File, fileName: String) {}
            override suspend fun commitStudyPad(unzipFolder: File) {}
        }
        val runner = InstallJobRunner(InstallInspector { BackupControl.AbDbFileType.ZIP }, committer)
        val c = InstallServiceController(
            runnerFactory = { runner },
            newJobId = idGen(),
            onTerminal = { _, _ -> },
        )
        val realDeps = InstallServiceController.JobDeps(
            acquireDir = acquireDir,
            openStream = { "data".byteInputStream() },
            isStudyPadExport = { false },
            studyPadStats = { error("unused") },
            swordZipScan = { InstallInspector.SwordZipScan(emptyList(), 3, isEpub = false, invalid = false) },
            epubUpgradeCheck = { false },
        )

        val outcome = c.enqueue(listOf(src("m.zip")), realDeps)

        assertEquals(InstallOutcome.Ok, outcome)
        assertEquals(1, committer.swordCalls)
        assertTrue(c.jobs.value.isEmpty())
        acquireDir.deleteRecursively()
    }
}
