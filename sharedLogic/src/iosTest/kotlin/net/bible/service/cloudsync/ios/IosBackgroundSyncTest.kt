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
    private class FakeRefresh(private val failSchedule: Boolean = false) : BackgroundRefresh {
        val scheduled = mutableListOf<Long>()
        override fun register(handler: suspend () -> Boolean) {}
        override fun schedule(earliestSeconds: Long) {
            scheduled += earliestSeconds
            if (failSchedule) throw IllegalStateException("schedule failed")
        }
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
        var started = 0
        val sync = IosBackgroundSync(policy({ started++; CompletableDeferred(false) }), refresh)
        val app = koinApplication { modules(module { single { sync } }) }
        assertFalse(iosBackgroundRefreshEntry(app.koin))
        assertEquals(1, started)
        assertEquals(1, refresh.scheduled.size)
    }

    @Test
    fun rescheduleFailureDoesNotMaskPolicyFailure() = runTest {
        val refresh = FakeRefresh(failSchedule = true)
        val sync = IosBackgroundSync(policy({ throw IllegalStateException("policy boom") }), refresh)
        val e = assertFailsWith<IllegalStateException> { sync.runAndReschedule() }
        assertEquals("policy boom", e.message)
        assertEquals(1, refresh.scheduled.size)
    }

    @Test
    fun rescheduleFailureDoesNotFailSuccessfulRun() = runTest {
        val refresh = FakeRefresh(failSchedule = true)
        val sync = IosBackgroundSync(policy({ CompletableDeferred(true) }), refresh)
        assertTrue(sync.runAndReschedule())
        sync.schedule() // must not throw
    }

    @Test
    fun entryWithKoinMissingSettingsReturnsFalse() = runTest {
        val refresh = FakeRefresh()
        // Built the way the real binding does: lambdas resolve AppSettings from Koin, which is not bound.
        val app = koinApplication {
            modules(module {
                single {
                    IosBackgroundSync(
                        BackgroundSyncPolicy(
                            syncEnabled = { get<net.bible.sharedcore.platform.AppSettings>().getBoolean("x", false) },
                            replacing = { false }, intervalSeconds = { 900 }, lastSynchronizedMs = { 0L },
                            nowMs = { 0L }, signedIn = { true }, signIn = { true },
                            startSync = { CompletableDeferred(true) },
                        ),
                        refresh,
                    )
                }
            })
        }
        assertFalse(iosBackgroundRefreshEntry(app.koin))
        assertEquals(listOf(900L), refresh.scheduled)
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
