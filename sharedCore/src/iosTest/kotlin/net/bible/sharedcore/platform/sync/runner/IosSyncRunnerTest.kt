package net.bible.sharedcore.platform.sync

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class IosSyncRunnerTest {
    @Test fun stopDoesNotCancelForegroundWaiter() = runTest {
        val entered = CompletableDeferred<Unit>()
        val cleanup = CompletableDeferred<Unit>()
        var passes = 0
        var ends = 0
        val scheduler = IosSyncRunner(this, {
            passes++
            entered.complete(Unit)
            try { CompletableDeferred<Boolean>().await() }
            finally { withContext(NonCancellable) { withTimeout(5_000) { cleanup.await() } } }
        }, { 1uL }, { ends++ })
        val shared = scheduler.startDatabaseSync()
        val foreground = async { scheduler.startDatabaseSync().await() }
        runCurrent()
        entered.await()
        try {
            scheduler.stop()
            assertFalse(shared.isCancelled, "stop must cancel the pass, not its shared result")
            assertSame(shared, scheduler.startDatabaseSync(), "No replacement before cancellation cleanup")
            runCurrent()
            assertFalse(foreground.isCompleted)
        } finally {
            cleanup.complete(Unit)
        }
        runCurrent()
        assertFalse(foreground.await(), "The cancelled pass resolves false normally")
        assertEquals(1, passes)
        assertEquals(1, ends)
    }

    @Test fun stopAfterRealCompletionKeepsSuccess() = runTest {
        val finish = CompletableDeferred<Boolean>()
        val scheduler = IosSyncRunner(this, { finish.await() }, { 1uL }, {})
        val result = scheduler.startDatabaseSync()
        val foreground = async { result.await() }
        runCurrent()
        finish.complete(true)
        runCurrent()
        scheduler.stop()
        assertTrue(foreground.await())
    }
}
