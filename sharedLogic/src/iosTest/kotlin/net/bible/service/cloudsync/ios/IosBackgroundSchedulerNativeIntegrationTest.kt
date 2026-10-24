package net.bible.service.cloudsync.ios

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import net.bible.service.cloudsync.BackgroundSyncPolicy
import net.bible.sharedcore.platform.sync.IosSyncScheduler
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class IosBackgroundSchedulerNativeIntegrationTest {
    @Test fun staleCancellationOfCompletedPassDoesNotStopReplacement() = runTest {
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler))
        val firstGate = CompletableDeferred<Unit>()
        val secondGate = CompletableDeferred<Unit>()
        var passes = 0
        var replacementStopped = false
        val runner = IosSyncScheduler(scope, {
            val pass = ++passes
            try {
                kotlinx.coroutines.withTimeout(5_000) { (if (pass == 1) firstGate else secondGate).await() }
                true
            } catch (e: CancellationException) {
                if (pass == 2) replacementStopped = true
                throw e
            }
        }, { 1uL }, {})
        val first = runner.startDatabaseSync()
        val policy = BackgroundSyncPolicy({ true }, { false }, { 900 }, { 0 }, { 1_000_000 },
            { true }, { true }, { runner.startDatabaseSync() }, { result -> runner.stop(result) })
        val oldWaiter = backgroundScope.launch { policy.runOnce() }
        try {
            runCurrent()
            firstGate.complete(Unit)
            assertTrue(first.isCompleted)
            assertTrue(first.await())
            val replacement = runner.startDatabaseSync()
            assertEquals(2, passes)
            assertFalse(replacement.isCompleted)
            oldWaiter.cancel()
            runCurrent()
            oldWaiter.join()
            assertFalse(replacementStopped, "Stale cancellation of A must not stop replacement B")
            assertFalse(replacement.isCompleted)
            secondGate.complete(Unit)
            assertTrue(replacement.await())
        } finally {
            firstGate.complete(Unit)
            secondGate.complete(Unit)
            oldWaiter.cancel()
            scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
            runCurrent()
        }
    }

    @Test fun expirationStopsPassWithoutCancellingForegroundWaiter() = runTest {
        var stopped = 0
        var ends = 0
        val runner = IosSyncScheduler(this, {
            try { CompletableDeferred<Boolean>().await() } finally { stopped++ }
        }, { 1uL }, { ends++ })
        val shared = runner.startDatabaseSync()
        val foreground = async { shared.await() }
        val policy = BackgroundSyncPolicy({ true }, { false }, { 900 }, { 0 }, { 1_000_000 },
            { true }, { true }, { runner.startDatabaseSync() }, { result -> runner.stop(result) })
        var cancellation: Throwable? = null
        val background = launch { try { policy.runOnce() } catch (e: CancellationException) { cancellation = e; throw e } }
        runCurrent()
        background.cancel()
        background.join()
        runCurrent()
        assertIs<CancellationException>(cancellation)
        assertFalse(shared.isCancelled, "BG policy must call scheduler.stop, never shared.cancel")
        assertFalse(foreground.await())
        assertEquals(1, stopped)
        assertEquals(1, ends)
    }
}
