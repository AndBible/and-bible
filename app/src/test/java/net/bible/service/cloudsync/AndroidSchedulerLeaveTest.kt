package net.bible.service.cloudsync

import android.content.ComponentName
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicInteger
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import net.bible.android.control.buildPlatformBindings
import net.bible.service.common.CommonUtils
import net.bible.sharedcore.platform.AppSettings
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import net.bible.android.TestBibleApplication
import net.bible.android.platform.AndroidSyncScheduler
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [35])
class AndroidSchedulerLeaveTest {
    @Test fun acceptedUndeliveredLeaveKeepsPendingWhenEarlierSyncFinishes() {
        val app = ApplicationProvider.getApplicationContext<TestBibleApplication>()
        val sent = mutableListOf<Intent>()
        val context = object : ContextWrapper(app) {
            override fun startForegroundService(intent: Intent): ComponentName {
                sent += intent
                return ComponentName(app, SyncService::class.java)
            }
        }
        val scheduler = AndroidSyncScheduler(context)
        val previous = scheduler.startDatabaseSync()
        val leave = scheduler.startLeave()!!
        assertSame(previous, leave)
        assertEquals("running owner is reused without another service intent", SyncService.START_SERVICE, sent.last().action)
        assertEquals("service claims accepted leave at the previous pass boundary", AndroidSyncScheduler.Request.Leave, scheduler.consumeRequestOrComplete(true))
        assertFalse("accepted leave remains pending until its work finishes", leave.isCompleted)
        assertSame("foreground joins accepted leave", leave, scheduler.startDatabaseSync())
        assertEquals("existing owner needs no extra wakeup", 1, sent.size)
        assertNull(scheduler.consumeRequestOrComplete(true))
        assertTrue(leave.isCompleted)
    }

    @Test fun foregroundDuringPendingLeaveJoinsWithoutAnotherServiceStart() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<TestBibleApplication>()
        val scheduler = AndroidSyncScheduler(app)
        val calls = AtomicInteger()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val policy = BackgroundSyncPolicy(
            syncEnabled = { true }, replacing = { false }, intervalSeconds = { 300 },
            lastSynchronizedMs = { 0 }, nowMs = { System.currentTimeMillis() },
            signedIn = { true }, signIn = { true },
            startSync = { calls.incrementAndGet(); scheduler.startDatabaseSync() },
        )
        val driver = SyncTriggerDriver(policy, scope, leaveHandOff = { scheduler.startLeave() })
        try {
            while (org.robolectric.Shadows.shadowOf(app).nextStartedService != null) { }
            val leave = driver.onBackground()
            assertNotNull(org.robolectric.Shadows.shadowOf(app).nextStartedService)
            val foreground = driver.onForeground()!!
            withTimeout(5000) {
                while (calls.get() == 0) kotlinx.coroutines.yield()
            }
            assertNull("foreground must join pending leave, not start a second service", org.robolectric.Shadows.shadowOf(app).nextStartedService)
            assertFalse(leave.isCompleted)
            scheduler.complete(true)
            withTimeout(5000) { foreground.join(); leave.join() }
        } finally {
            scheduler.complete(false)
            driver.close()
            scope.cancel()
        }
    }

    @Test fun refusedLeaveDuringRunningSyncStaysOwnedByExistingPendingJob() {
        val app = ApplicationProvider.getApplicationContext<TestBibleApplication>()
        var refuse = false
        val context = object : ContextWrapper(app) {
            override fun startForegroundService(intent: Intent): ComponentName {
                if (refuse) throw IllegalStateException("refused")
                return ComponentName(app, SyncService::class.java)
            }
        }
        val scheduler = AndroidSyncScheduler(context)
        val running = scheduler.startDatabaseSync()
        refuse = true
        val leave = scheduler.startLeave()
        assertSame("existing service owns the leave follow-up even if its wakeup is refused", running, leave)
        assertEquals(AndroidSyncScheduler.Request.Leave, scheduler.consumeRequestOrComplete(true))
        assertFalse("running pass cannot complete a pending leave follow-up", running.isCompleted)
        scheduler.complete(false)
    }

    @Test fun operationCancellationDoesNotDiscardFallbackFollowUp() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<TestBibleApplication>()
        val scheduler = AndroidSyncScheduler(app)
        var iterations = 0
        val result = runCatching {
            scheduler.runFallback {
                if (++iterations == 1) {
                    scheduler.startLeave()
                    throw kotlinx.coroutines.CancellationException("SDK task cancelled, owner still active")
                }
                true
            }
        }
        assertEquals("an active fallback must serve its queued leave after operation cancellation", 2, iterations)
        assertEquals(true, result.getOrNull())
    }

    @Test fun securityExceptionOnLeaveIsARefusedStart() = assertRuntimeRefusal(SecurityException("permission denied"))

    @Test fun runtimeExceptionOnLeaveIsARefusedStart() = assertRuntimeRefusal(IllegalArgumentException("invalid foreground start"))

    private fun assertRuntimeRefusal(failure: RuntimeException) {
        val app = ApplicationProvider.getApplicationContext<TestBibleApplication>()
        var refusing = true
        var starts = 0
        val context = object : ContextWrapper(app) {
            override fun startForegroundService(service: Intent): ComponentName {
                starts++
                if (refusing) throw failure
                return ComponentName(app, SyncService::class.java)
            }
        }
        val scheduler = AndroidSyncScheduler(context)
        val result = runCatching { scheduler.startLeave() }
        assertTrue("A refused lifecycle start must not throw: ${result.exceptionOrNull()}", result.isSuccess)
        assertNull("refusal selects the in-process fallback", result.getOrNull())
        refusing = false
        val retry = scheduler.startDatabaseSync()
        assertEquals("refusal must release pending", 2, starts)
        assertFalse(retry.isCompleted)
        scheduler.complete(false)
    }

    @Test fun refusedLeaveReleasesPendingSoLaterNormalSyncCanStart() {
        val app = ApplicationProvider.getApplicationContext<TestBibleApplication>()
        var refusing = true
        var starts = 0
        val context = object : ContextWrapper(app) {
            override fun startForegroundService(service: Intent): ComponentName? {
                starts++
                if (refusing) throw IllegalStateException("background start refused")
                return ComponentName(app, SyncService::class.java)
            }
        }
        val scheduler = AndroidSyncScheduler(context)
        assertNull("refusal must permit in-process fallback", scheduler.startLeave())
        refusing = false
        val retry = scheduler.startDatabaseSync()
        assertEquals("a rejected leave must not strand pending", 2, starts)
        assertFalse(retry.isCompleted)
        scheduler.complete(true)
        assertTrue(retry.isCompleted)
    }
}
