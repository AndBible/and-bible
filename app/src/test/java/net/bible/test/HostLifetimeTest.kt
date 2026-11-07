package net.bible.test

import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import net.bible.android.TestBibleApplication
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * #4020: a host's session work that is inside an IO call when the host closes resumes on the test's Main scheduler
 * after `runTest` stopped draining it, so the host's lifecycle job never completes and the database reset that joins
 * it hangs at `join-producer`. [closeAndJoinLifecycle] must keep `runTest` driving the scheduler until that work ends.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class)
class HostLifetimeTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUpMain() { Dispatchers.setMain(dispatcher) }

    @After fun tearDownMain() {
        Dispatchers.resetMain()
        DatabaseResetter.resetDatabase()
    }

    /** A scope first used at the close has not registered its lifecycle observer yet; it must not keep the close waiting. */
    @Test fun closingAnActivityWhoseLifecycleScopeWasNeverUsedReturns() = runTest(dispatcher, timeout = 30.seconds) {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).create()
        controller.closeAndJoinLifecycle(timeout = 5.seconds)
        assertTrue(controller.get().lifecycleScope.coroutineContext[Job]!!.isCompleted)
    }

    @Test fun closingWaitsForSessionWorkThatReturnsFromIoAfterTheClose() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val closing = CountDownLatch(1)
        val runTestReturned = AtomicBoolean(false)
        lateinit var lifecycleJob: Job
        // The IO call ends only once runTest has returned (the late return of the flake), or after 2 s if the
        // close under test keeps runTest open until the work is done.
        val releaser = thread(name = "io-release") {
            closing.await()
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2)
            while (!runTestReturned.get() && System.nanoTime() < deadline) Thread.sleep(5)
            release.countDown()
        }
        try {
            runTest(dispatcher, timeout = 30.seconds) {
                val controller = Robolectric.buildActivity(ComponentActivity::class.java).create()
                val host = controller.get().lifecycleScope
                lifecycleJob = host.coroutineContext[Job]!!
                // Shaped like BibleWindowSession's scope: a SupervisorJob child of the host's lifecycle scope.
                val session = CoroutineScope(host.coroutineContext + SupervisorJob(lifecycleJob))
                session.launch(Dispatchers.Main) {
                    // A blocking read (as Room's) does not stop for cancellation; its result returns to Main.
                    withContext(Dispatchers.IO) { entered.countDown(); release.await() }
                }
                runCurrent()
                assertTrue("session work must be inside its IO call", entered.await(5, TimeUnit.SECONDS))
                closing.countDown()
                controller.closeAndJoinLifecycle()
            }
            runTestReturned.set(true)
            releaser.join()
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2)
            while (!lifecycleJob.isCompleted && System.nanoTime() < deadline) Thread.sleep(5)
            assertTrue("the host's lifecycle job must be complete when runTest returns: ${DatabaseResetter.jobState(lifecycleJob)}",
                lifecycleJob.isCompleted)
        } finally {
            closing.countDown()
            release.countDown()
            runTestReturned.set(true)
            releaser.join()
            // A stranded resumption sits on this scheduler: run it so nothing outlives the test.
            dispatcher.scheduler.advanceUntilIdle()
        }
    }
}
