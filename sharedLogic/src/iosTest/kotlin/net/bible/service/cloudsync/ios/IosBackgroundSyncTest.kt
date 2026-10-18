package net.bible.service.cloudsync.ios

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import net.bible.service.cloudsync.BackgroundSyncPolicy
import net.bible.sharedcore.platform.sync.BackgroundRefresh
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IosBackgroundSyncTest {
    private class FakeRefresh : BackgroundRefresh {
        val scheduled = mutableListOf<Long>()
        override fun register(handler: suspend () -> Boolean) {}
        override fun schedule(earliestSeconds: Long) { scheduled += earliestSeconds }
        override fun cancel() {}
    }

    private fun policy(startSync: () -> kotlinx.coroutines.Deferred<Boolean>, interval: Long = 900) = BackgroundSyncPolicy(
        syncEnabled = { true }, replacing = { false }, intervalSeconds = { interval },
        lastSynchronizedMs = { 0L }, nowMs = { 10_000_000L }, signedIn = { true }, signIn = { true },
        startSync = startSync,
    )

    @Test
    fun entryWithoutKoinCompletes() = runTest {
        assertTrue(iosBackgroundRefreshEntry(null))
    }

    @Test
    fun entryWithKoinWithoutBindingCompletes() = runTest {
        val app = koinApplication { modules(module { }) }
        assertTrue(iosBackgroundRefreshEntry(app.koin))
    }

    @Test
    fun runAndRescheduleReschedulesOnFailure() = runTest {
        val refresh = FakeRefresh()
        val sync = IosBackgroundSync(policy({ throw IllegalStateException("boom") }), refresh)
        assertFailsWith<IllegalStateException> { sync.runAndReschedule() }
        assertEquals(listOf(900L), refresh.scheduled)
    }

    @Test
    fun runAndRescheduleReschedulesOnSuccess() = runTest {
        val refresh = FakeRefresh()
        val sync = IosBackgroundSync(policy({ CompletableDeferred(true) }, interval = 10), refresh)
        assertTrue(sync.runAndReschedule())
        assertEquals(listOf(60L), refresh.scheduled) // floored at 60
    }

    @Test
    fun entryRunsPolicyWhenStarted() = runTest {
        val refresh = FakeRefresh()
        val sync = IosBackgroundSync(policy({ CompletableDeferred(false) }), refresh)
        val app = koinApplication { modules(module { single { sync } }) }
        assertFalse(iosBackgroundRefreshEntry(app.koin))
        assertEquals(1, refresh.scheduled.size)
    }

    @Test
    fun entryReportsFalseWhenPolicyThrows() = runTest {
        val refresh = FakeRefresh()
        val sync = IosBackgroundSync(policy({ throw IllegalStateException("boom") }), refresh)
        val app = koinApplication { modules(module { single { sync } }) }
        assertFalse(iosBackgroundRefreshEntry(app.koin))
        assertEquals(1, refresh.scheduled.size)
    }
}
