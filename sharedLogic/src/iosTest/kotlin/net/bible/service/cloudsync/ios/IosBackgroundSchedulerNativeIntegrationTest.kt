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
    @Test fun expirationStopsPassWithoutCancellingForegroundWaiter() = runTest {
        var stopped = 0
        var ends = 0
        val runner = IosSyncScheduler(this, {
            try { CompletableDeferred<Boolean>().await() } finally { stopped++ }
        }, { 1uL }, { ends++ })
        val shared = runner.startDatabaseSync()
        val foreground = async { shared.await() }
        val policy = BackgroundSyncPolicy({ true }, { false }, { 900 }, { 0 }, { 1_000_000 },
            { true }, { true }, { runner.startDatabaseSync() }, { runner.stop() })
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
