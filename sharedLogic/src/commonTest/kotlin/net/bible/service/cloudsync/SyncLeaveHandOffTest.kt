package net.bible.service.cloudsync

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame

@OptIn(ExperimentalCoroutinesApi::class)
class SyncLeaveHandOffTest {
    @Test
    fun acceptedLeaveIsHandedOffSynchronouslyWithoutDriverPreparationOrSync() = runTest {
        var prepared = 0
        var syncs = 0
        var handoffs = 0
        val accepted = CompletableDeferred<Boolean>()
        val driver = SyncTriggerDriver(
            policy = policy { syncs++ }, scope = backgroundScope,
            prepare = { prepared++ },
            leaveHandOff = { handoffs++; accepted },
        )

        val job = driver.onBackground()
        assertSame(accepted, job)
        assertEquals(1, handoffs, "hand-off happens before onBackground returns")
        runCurrent()
        assertEquals(0, prepared)
        assertEquals(0, syncs)
        driver.onBackground()
        assertEquals(1, handoffs, "duplicate leave does not start another service")
        assertFalse(job.isCompleted)
        accepted.complete(true)
    }

    @Test
    fun refusedLeaveFallsBackToExactlyOneDriverPass() = runTest {
        var prepared = 0
        var syncs = 0
        val driver = SyncTriggerDriver(
            policy = policy { syncs++ }, scope = backgroundScope,
            prepare = { prepared++ }, leaveHandOff = { null },
        )
        driver.onBackground()
        runCurrent()
        assertEquals(1, prepared)
        assertEquals(1, syncs)
    }

    @Test
    fun noHandOffRetainsExistingLeavePass() = runTest {
        var prepared = 0
        var syncs = 0
        val driver = SyncTriggerDriver(
            policy = policy { syncs++ }, scope = backgroundScope,
            prepare = { prepared++ },
        )
        driver.onBackground()
        runCurrent()
        assertEquals(1, prepared)
        assertEquals(1, syncs)
    }

    private fun policy(onSync: () -> Unit) = BackgroundSyncPolicy(
        syncEnabled = { true }, replacing = { false }, intervalSeconds = { 300 },
        lastSynchronizedMs = { 0 }, nowMs = { 0 }, signedIn = { true },
        signIn = { true }, startSync = { onSync(); CompletableDeferred(true) },
    )
}
