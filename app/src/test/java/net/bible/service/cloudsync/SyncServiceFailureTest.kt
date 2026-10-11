package net.bible.service.cloudsync

import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.platform.AndroidSyncScheduler
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.lang.reflect.Proxy

@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class SyncServiceFailureTest {
    @Test
    fun engineFailureCompletesScheduledRunAndReleasesServiceForRetry() = runBlocking {
        val cloud = GlobalContext.get().get<CloudSync>()
        val scheduler = GlobalContext.get().get<AndroidSyncScheduler>()
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.app.Application>()
        val adapterField = CloudSync::class.java.getDeclaredField("_adapter").apply { isAccessible = true }
        val originalAdapter = adapterField.get(cloud)
        // A real engine fails before its per-category catches, through the SDK boundary getter.
        val adapter = Proxy.newProxyInstance(CloudAdapter::class.java.classLoader, arrayOf(CloudAdapter::class.java)) { _, method, _ ->
            if (method.name == "getSignedIn") throw IllegalStateException("SDK credential lookup failed")
            error("Unexpected adapter operation: ${method.name}")
        } as CloudAdapter
        val edges = mutableListOf<Boolean>()
        cloud.runningChanged.subscribe { synchronized(edges) { edges += it } }
        var service: SyncService? = null
        try {
            adapterField.set(cloud, adapter)
            val first = cloud.start()
            val intent = shadowOf(context).nextStartedService
            assertNotNull(intent)
            service = Robolectric.buildService(SyncService::class.java).create().get()
            service.onStartCommand(intent, 0, 1)
            withTimeout(5000) { service.scope.coroutineContext[Job]!!.children.toList().forEach { it.join() } }
            assertTrue("Every failed service run must release its scheduled waiter", first.isCompleted)
            assertFalse(first.await())
            withTimeout(1000) { cloud.waitUntilFinished() }
            assertEquals(listOf(true, false), synchronized(edges) { edges.toList() })
            assertTrue(shadowOf(service).isStoppedBySelf)
            assertFalse(org.robolectric.shadows.ShadowPowerManager.getLatestWakeLock().isHeld)
            val retry = cloud.start()
            assertNotSame(first, retry)
            assertFalse(retry.isCompleted)
            assertNotNull(shadowOf(context).nextStartedService)
        } finally {
            scheduler.complete(false)
            adapterField.set(cloud, originalAdapter)
            cloud.resetSubscribersForTest()
            service?.onDestroy()
        }
    }
}
