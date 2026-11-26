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
    fun leaveDoesNotHandOffWhenSyncPolicyCannotRun() = runTest {
        var handoffs = 0
        var prepared = 0
        var syncs = 0
        val driver = SyncTriggerDriver(
            policy = policy(syncEnabled = false) { syncs++ }, scope = backgroundScope,
            prepare = { prepared++ }, leaveHandOff = { handoffs++; null },
        )

        driver.onBackground()
        runCurrent()

        assertEquals(0, handoffs, "disabled sync must not start the Android service")
        assertEquals(0, prepared)
        assertEquals(0, syncs)
    }

    @Test
    fun leaveDoesNotHandOffWhenNetworkIsUnavailable() = runTest {
        var handoffs = 0
        var prepared = 0
        var syncs = 0
        val driver = SyncTriggerDriver(
            policy = policy { syncs++ }, scope = backgroundScope,
            prepare = { prepared++ }, networkAvailable = { false }, leaveHandOff = { handoffs++; null },
        )

        driver.onBackground()
        runCurrent()

        assertEquals(0, handoffs, "offline leave must not start the Android service")
        assertEquals(0, prepared)
        assertEquals(0, syncs)
    }

    @Test
    fun leaveDoesNotHandOffWhenSignedOut() = runTest {
        var handoffs = 0
        var signIns = 0
        var prepared = 0
        var syncs = 0
        val driver = SyncTriggerDriver(
            policy = policy(signedIn = false, onSignIn = { signIns++ }) { syncs++ }, scope = backgroundScope,
            prepare = { prepared++ }, leaveHandOff = { handoffs++; null },
        )

        driver.onBackground()
        runCurrent()

        assertEquals(0, handoffs, "signed-out leave must not start the Android service")
        assertEquals(0, signIns, "leave checks sign-in state without showing sign-in UI")
        assertEquals(0, prepared)
        assertEquals(0, syncs)
    }

    @Test
    fun refusedLeaveWithoutFallbackRetainsDriverPass() = runTest {
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
    fun refusedLeaveFallbackRunsOnceForRepeatedLeaveNotification() = runTest {
        var handoffs = 0
        var fallbacks = 0
        val driver = SyncTriggerDriver(
            policy = policy {}, scope = backgroundScope,
            leaveHandOff = { handoffs++; null }, leaveFallback = { fallbacks++ },
        )

        driver.onBackground()
        driver.onBackground()
        runCurrent()

        assertEquals(1, handoffs)
        assertEquals(1, fallbacks, "a duplicate leave must not select the fallback again")
    }

    @Test
    fun refusedLeaveFallbackOwnsLeaveWorkWithoutDriverPreparationOrSync() = runTest {
        var prepared = 0
        var syncs = 0
        var fallbacks = 0
        val driver = SyncTriggerDriver(
            policy = policy { syncs++ }, scope = backgroundScope,
            prepare = { prepared++ }, leaveHandOff = { null }, leaveFallback = { fallbacks++ },
        )
        driver.onBackground()
        runCurrent()
        assertEquals(0, prepared, "fallback owns saving, so the driver must not prepare first")
        assertEquals(0, syncs, "fallback owns syncing, so the driver must not start a second sync")
        assertEquals(1, fallbacks)
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

    private fun policy(
        syncEnabled: Boolean = true,
        signedIn: Boolean = true,
        onSignIn: () -> Unit = {},
        onSync: () -> Unit,
    ) = BackgroundSyncPolicy(
        syncEnabled = { syncEnabled }, replacing = { false }, intervalSeconds = { 300 },
        lastSynchronizedMs = { 0 }, nowMs = { 0 }, signedIn = { signedIn },
        signIn = { onSignIn(); true }, startSync = { onSync(); CompletableDeferred(true) },
    )
}
