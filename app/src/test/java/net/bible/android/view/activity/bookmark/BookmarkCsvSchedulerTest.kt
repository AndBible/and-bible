package net.bible.android.view.activity.bookmark

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.test.DatabaseResetter
import net.bible.test.ReadingFixtureOwners
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
@OptIn(ExperimentalCoroutinesApi::class)
class BookmarkCsvSchedulerTest {
    // Catches reset-before-restoration: the real preparation cannot hop to queued test Main.
    @Test fun completedCsvChildrenPermitResetAndFollowingApplicationStartup() = verifyRestoration(false)

    // Catches skipped restoration on reset failure and loss of the original failure.
    @Test fun failingResetRestoresMainAndPermitsFollowingApplicationStartup() = verifyRestoration(true)

    @Test fun incompleteCsvChildBlocksRestorationAndResetUntilExactCompletion() {
        val dispatcher = StandardTestDispatcher()
        val owner = CoroutineScope(SupervisorJob() + dispatcher)
        val release = CompletableDeferred<Unit>()
        val entered = CompletableDeferred<Unit>()
        val finished = CompletableDeferred<Unit>()
        val child = owner.launch {
            try { entered.complete(Unit); awaitCancellation() }
            finally { withContext(NonCancellable + Dispatchers.Main) { release.await(); finished.complete(Unit) } }
        }
        var reset = false
        Dispatchers.setMain(dispatcher)
        try {
            dispatcher.scheduler.runCurrent()
            assertTrue(entered.isCompleted)
            child.cancel()
            dispatcher.scheduler.runCurrent()
            assertThrows(IllegalStateException::class.java) { finishCsvFixture(listOf(child)) { reset = true } }
            assertFalse(reset)
            assertFalse(finished.isCompleted)
        } finally {
            release.complete(Unit)
            dispatcher.scheduler.runCurrent()
            check(child.isCompleted) { "Exact CSV child must complete before restoring Main" }
            runBlocking { child.join() }
            owner.cancel()
            check(owner.coroutineContext[Job]!!.isCompleted)
            finishCsvFixture(listOf(child)) { }
        }
        assertTrue(finished.isCompleted)
    }

    private fun verifyRestoration(failCommit: Boolean) {
        val dispatcher = StandardTestDispatcher()
        val bodyJobs = mutableListOf<Job>()
        val childFinished = CompletableDeferred<Unit>()
        val entered = CountDownLatch(1)
        val aiDrained = CountDownLatch(1)
        val mainEntered = CountDownLatch(1)
        val drained = CountDownLatch(1)
        val returned = CountDownLatch(1)
        val worker = AtomicReference<Job>()
        val expected = IllegalArgumentException("fake commit failure")
        var committed = false
        var failure: Throwable? = null
        val app = RuntimeEnvironment.getApplication() as TestBibleApplication
        Dispatchers.setMain(dispatcher)
        try {
            runTest(dispatcher) {
                bodyJobs += checkNotNull(currentCoroutineContext()[Job])
                val child = launch {
                    try { awaitCancellation() } finally {
                        withContext(NonCancellable + dispatcher) { childFinished.complete(Unit) }
                    }
                }
                runCurrent()
                child.cancelAndJoin()
            }
            assertTrue(childFinished.isCompleted)
            assertTrue(bodyJobs.single().isCompleted)
            try {
                finishCsvFixture(bodyJobs) {
                    DatabaseResetter.runResetBlocking(timeoutMs = 1000) {
                        worker.set(checkNotNull(checkNotNull(currentCoroutineContext()[Job]).parent))
                        entered.countDown()
                        try {
                            app.drainAiSettingsFixture()
                            aiDrained.countDown()
                            withContext(Dispatchers.Main.immediate) {
                                assertSame("reset Main work must retain Android thread affinity", android.os.Looper.getMainLooper(), android.os.Looper.myLooper())
                                mainEntered.countDown()
                            }
                            ReadingFixtureOwners.drain()
                            drained.countDown()
                            commitReset {
                                check(bodyJobs.all { it.isCompleted } && childFinished.isCompleted)
                                committed = true // Deliberately no native close in this probe.
                                if (failCommit) throw expected
                            }
                        } finally { returned.countDown() }
                    }
                }
            } catch (e: Throwable) { failure = e }
            if (committed) {
                // Check BEFORE the defensive finally resetMain, so it cannot hide skipped restoration.
                DatabaseResetter.runResetBlocking(timeoutMs = 1000) {
                    worker.set(checkNotNull(checkNotNull(currentCoroutineContext()[Job]).parent))
                    withContext(Dispatchers.Main.immediate) { }
                }
            }
        } finally {
            // Even the intentional RED must execute the aborted continuation on its EXACT queue.
            // Replacing Main does not move continuations already dispatched to this scheduler.
            val exact = worker.get()
            if (exact != null) {
                val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
                while (!exact.isCompleted && System.nanoTime() < deadline) {
                    dispatcher.scheduler.runCurrent()
                    Thread.yield()
                }
                check(exact.isCompleted) { "Exact reset worker still incomplete; Main must not be restored" }
                runBlocking { exact.join() }
            }
            DatabaseResetter.requireNoRetainedWorkers()
            Dispatchers.resetMain()
        }
        assertEquals("real worker entered", 0L, entered.count)
        assertEquals("AI preparation completed before the Main hop", 0L, aiDrained.count)
        assertEquals("real Main hop entered", 0L, mainEntered.count)
        assertEquals("real Main preparation must return, not starve on CSV scheduler", 0L, drained.count)
        assertEquals("worker finally completed", 0L, returned.count)
        assertTrue("guarded fake commit must be reached", committed)
        if (failCommit) {
            // Coroutine stack recovery may copy the exception, retaining the original as its cause.
            assertTrue("original fake-commit failure must survive", generateSequence(failure) { it.cause }.any { it === expected })
        } else assertNull(failure)
        // Also exercises the restored real Main and admission in this very same JVM/application.
        DatabaseResetter.runResetBlocking { withContext(Dispatchers.Main.immediate) { } }
        ReadingFixtureOwners.requireEmpty()
        app.onTerminate()
        app.onCreate()
    }
}
