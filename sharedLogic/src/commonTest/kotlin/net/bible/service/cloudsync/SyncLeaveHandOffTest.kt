package net.bible.service.cloudsync

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
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
    fun acceptedLeaveHandOffMarksPassStartForNetworkRestore() = runTest {
        val handoff = CompletableDeferred<Boolean>()
        var online = true
        val driver = SyncTriggerDriver(
            policy = BackgroundSyncPolicy(
                syncEnabled = { true }, replacing = { false }, intervalSeconds = { 300 },
                lastSynchronizedMs = { 0 }, nowMs = { currentTime }, signedIn = { true },
                signIn = { true }, startSync = { CompletableDeferred(true) },
            ),
            scope = backgroundScope, leaveHandOff = { handoff },
            networkAvailable = { online }, networkRestoreGapMs = 30_000L,
        )

        assertSame(handoff, driver.onBackground())
        advanceTimeBy(5_000)
        handoff.complete(true)
        runCurrent()
        online = false
        driver.onForeground()
        runCurrent()
        online = true

        assertNull(driver.onNetworkAvailable(), "the hand-off start should suppress a recent network restore")
    }

    @Test
    fun acceptedLeaveHandOffCompletionMovesTimerMark() = runTest {
        val intervalMs = 300_000L
        val handoff = CompletableDeferred<Boolean>()
        var online = true
        var syncs = 0
        val policy = BackgroundSyncPolicy(
            syncEnabled = { true }, replacing = { false }, intervalSeconds = { 300 },
            lastSynchronizedMs = { 0 }, nowMs = { currentTime }, signedIn = { true },
            signIn = { true }, startSync = { syncs++; CompletableDeferred(true) },
        )
        val driver = SyncTriggerDriver(
            policy = policy, scope = backgroundScope, leaveHandOff = { handoff },
            networkAvailable = { online }, networkRestoreGapMs = 30_000L,
        )

        assertSame(handoff, driver.onBackground())
        advanceTimeBy(5_000)
        handoff.complete(true)
        runCurrent()
        online = false
        driver.onForeground()
        runCurrent()
        online = true
        advanceTimeBy(intervalMs - 1)
        assertEquals(0, syncs, "the timer should count from hand-off completion")
        advanceTimeBy(1)
        runCurrent()
        assertEquals(1, syncs, "the timer runs one interval after the hand-off completes")
    }

    @Test
    fun olderLeaveCompletionDoesNotOverwriteNewerOpenStartForNetworkRestore() = runTest {
        val handoff = CompletableDeferred<Boolean>()
        var online = true
        var syncs = 0
        val policy = BackgroundSyncPolicy(
            syncEnabled = { true }, replacing = { false }, intervalSeconds = { 300 },
            lastSynchronizedMs = { 0 }, nowMs = { currentTime }, signedIn = { true },
            signIn = { true }, startSync = { syncs++; CompletableDeferred(true) },
        )
        val driver = SyncTriggerDriver(
            policy = policy, scope = backgroundScope, leaveHandOff = { handoff },
            networkAvailable = { online }, networkRestoreGapMs = 30_000L,
        )

        driver.onBackground()
        advanceTimeBy(10_000)
        driver.onForeground()?.join()
        advanceTimeBy(25_000)
        handoff.complete(true)
        runCurrent()

        assertNull(
            driver.onNetworkAvailable(),
            "the leave completion must not replace the newer Open start and allow a redundant restore sync",
        )
        assertEquals(1, syncs, "only the Open pass should have synced")
    }

    @Test
    fun leaveCompletionDoesNotMoveTimerMarkBackAcrossReentrantOpen() = runTest {
        val handoff = CompletableDeferred<Boolean>()
        var driver: SyncTriggerDriver? = null
        var reenterOnClockRead = false
        var syncs = 0
        val policy = BackgroundSyncPolicy(
            syncEnabled = { true }, replacing = { false }, intervalSeconds = { 300 },
            lastSynchronizedMs = { 0 }, nowMs = {
                val capturedTime = currentTime
                if (reenterOnClockRead) {
                    reenterOnClockRead = false
                    advanceTimeBy(5_000)
                    driver!!.onForeground()
                    runCurrent()
                }
                capturedTime
            },
            signedIn = { true }, signIn = { true },
            startSync = { syncs++; CompletableDeferred(true) },
        )
        driver = SyncTriggerDriver(
            policy = policy, scope = backgroundScope, leaveHandOff = { handoff },
        )

        driver!!.onBackground()
        advanceTimeBy(5_000)
        reenterOnClockRead = true
        handoff.complete(true)
        runCurrent()

        assertEquals(1, syncs, "the reentrant Open pass should finish before the leave callback resumes")
        advanceTimeBy(294_999)
        runCurrent()
        assertEquals(1, syncs, "the timer must not run at the older completion mark's deadline")
        advanceTimeBy(1)
        runCurrent()
        assertEquals(1, syncs, "the newer Open mark must still determine the timer deadline")
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(2, syncs, "the timer should run one interval after the newer Open mark")
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
    fun offlineOpenSignsInOnNetworkRestoreBeforeNextLeave() = runTest {
        var online = false
        var signedIn = false
        var signIns = 0
        var syncs = 0
        var handoffs = 0
        val driver = SyncTriggerDriver(
            policy = BackgroundSyncPolicy(
                syncEnabled = { true }, replacing = { false }, intervalSeconds = { 300 },
                lastSynchronizedMs = { 0 }, nowMs = { currentTime }, signedIn = { signedIn },
                signIn = { signIns++; signedIn = true; true },
                startSync = { syncs++; CompletableDeferred(true) },
            ),
            scope = backgroundScope, networkAvailable = { online },
            leaveHandOff = { handoffs++; CompletableDeferred(true) },
        )
        driver.onForeground()?.join()
        assertEquals(0, signIns, "offline open must not attempt sign-in")
        online = true
        driver.onNetworkAvailable()?.join()
        assertEquals(1, signIns, "network restoration signs in in the foreground")
        assertEquals(1, syncs)
        driver.onBackground().join()
        assertEquals(1, handoffs, "the next leave sees the foreground-restored adapter")
    }

    @Test
    fun skippedHandOffDoesNotPostponeTimer() = runTest {
        val handoff = CompletableDeferred<Boolean>()
        var online = true
        var syncs = 0
        val driver = SyncTriggerDriver(
            policy = BackgroundSyncPolicy(
                syncEnabled = { true }, replacing = { false }, intervalSeconds = { 300 },
                lastSynchronizedMs = { 0 }, nowMs = { currentTime }, signedIn = { true },
                signIn = { true }, startSync = { syncs++; CompletableDeferred(true) },
            ),
            scope = backgroundScope, leaveHandOff = { handoff }, networkAvailable = { online },
        )
        advanceTimeBy(10_000)
        driver.onBackground()
        advanceTimeBy(10_000)
        handoff.complete(false)
        runCurrent()
        online = false
        driver.onForeground()
        runCurrent()
        online = true
        advanceTimeBy(280_000)
        runCurrent()
        assertEquals(1, syncs, "a skipped leave must not move the interval deadline")
    }

    @Test
    fun skippedHandOffDoesNotSuppressNetworkRestore() = runTest {
        val handoff = CompletableDeferred<Boolean>()
        var online = true
        var syncs = 0
        val driver = SyncTriggerDriver(
            policy = BackgroundSyncPolicy(
                syncEnabled = { true }, replacing = { false }, intervalSeconds = { 300 },
                lastSynchronizedMs = { 0 }, nowMs = { currentTime }, signedIn = { true },
                signIn = { true }, startSync = { syncs++; CompletableDeferred(true) },
            ),
            scope = backgroundScope, leaveHandOff = { handoff }, networkAvailable = { online },
        )
        driver.onBackground()
        handoff.complete(false)
        runCurrent()
        online = false
        driver.onForeground()
        runCurrent()
        online = true
        driver.onNetworkAvailable()?.join()
        assertEquals(1, syncs, "a skipped leave must not swallow the next usable network")
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
