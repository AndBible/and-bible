package net.bible.service.cloudsync

import android.app.Service
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class SyncServiceForegroundTest {
    private lateinit var service: SyncService
    private lateinit var workerScope: CoroutineScope

    @Before
    fun setUp() {
        service = Robolectric.buildService(SyncService::class.java).create().get()
        // Hold IO work queued: the foreground deadline must not depend on a worker being available.
        workerScope = CoroutineScope(SupervisorJob() + StandardTestDispatcher())
        service.scope.cancel()
        SyncService::class.java.getDeclaredField("scope").apply { isAccessible = true }.set(service, workerScope)
    }

    @After
    fun tearDown() {
        workerScope.cancel()
        service.onDestroy()
    }

    @Test
    fun nullIntentEntersForegroundAndStopsWithoutRestarting() {
        val result = service.onStartCommand(null, 0, 1)

        // stopForeground(REMOVE) clears the notification, but ShadowService retains its id.
        assertEquals("A null-intent restart must satisfy the foreground deadline", 2, shadowOf(service).lastForegroundNotificationId)
        assertTrue(shadowOf(service).isStoppedBySelf)
        assertEquals(Service.START_NOT_STICKY, result)
        assertEquals(0, workerCount())
    }

    @Test
    fun unknownCommandEntersForegroundAndStopsWithoutStartingSync() {
        val result = service.onStartCommand(Intent().setAction("unknown-command"), 0, 1)

        assertEquals(2, shadowOf(service).lastForegroundNotificationId)
        assertTrue(shadowOf(service).isStoppedBySelf)
        assertEquals(Service.START_NOT_STICKY, result)
        assertEquals(0, workerCount())
    }

    @Test
    fun startEntersForegroundBeforeWorkerRuns() {
        val result = service.onStartCommand(startIntent(), 0, 1)

        assertNotNull("Foreground entry must happen synchronously, not on the IO worker", shadowOf(service).lastForegroundNotification)
        assertFalse(shadowOf(service).isStoppedBySelf)
        assertEquals(Service.START_NOT_STICKY, result)
        assertEquals(1, workerCount())
    }

    @Test
    fun secondStartEntersForegroundAgainWithoutLaunchingAnotherSync() {
        service.onStartCommand(startIntent(), 0, 1)
        val firstNotification = shadowOf(service).lastForegroundNotification

        service.onStartCommand(startIntent(), 0, 2)

        val secondNotification = shadowOf(service).lastForegroundNotification
        assertNotNull(secondNotification)
        assertNotSame("Every foreground-service start must be acknowledged", firstNotification, secondNotification)
        assertFalse(shadowOf(service).isStoppedBySelf)
        assertEquals("A duplicate start must not create a second sync/cleanup owner", 1, workerCount())
    }

    @Test
    fun unknownCommandWhileSyncingEntersForegroundWithoutStoppingTheSync() {
        service.onStartCommand(startIntent(), 0, 1)
        val firstNotification = shadowOf(service).lastForegroundNotification

        service.onStartCommand(Intent().setAction("unknown-command"), 0, 2)

        assertNotNull(shadowOf(service).lastForegroundNotification)
        assertNotSame(firstNotification, shadowOf(service).lastForegroundNotification)
        assertFalse(shadowOf(service).isStoppedBySelf)
        assertEquals(1, workerCount())
    }

    private fun startIntent() = Intent().setAction(SyncService.START_SERVICE)

    private fun workerCount() = workerScope.coroutineContext[Job]!!.children.count()
}
