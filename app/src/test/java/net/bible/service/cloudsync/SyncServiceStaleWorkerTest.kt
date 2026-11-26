package net.bible.service.cloudsync

import android.content.Intent
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import net.bible.android.TestBibleApplication
import net.bible.android.platform.AndroidSyncScheduler
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [35])
class SyncServiceStaleWorkerTest {
    @Test fun timedOutWorkerCannotCompleteNewPlainPending() = exercise(false)
    @Test fun timedOutWorkerCannotConsumeNewLeaveDemand() = exercise(true)

    private fun exercise(leave: Boolean) = runBlocking {
        val koin = GlobalContext.get()
        val cloud = koin.get<CloudSync>()
        val scheduler = koin.get<AndroidSyncScheduler>()
        val settings = koin.get<net.bible.sharedcore.platform.AppSettings>()
        val category = SyncableDatabaseDefinition.BOOKMARKS
        val oldEnabled = category.isSyncEnabled(settings)
        val oldId = cloud.adapterId
        val field = CloudSync::class.java.getDeclaredField("_adapter").apply { isAccessible = true }
        val original = field.get(cloud)
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val blocking = AtomicBoolean(true)
        val adapter = java.lang.reflect.Proxy.newProxyInstance(CloudAdapter::class.java.classLoader, arrayOf(CloudAdapter::class.java)) { _, method, _ ->
            if (method.name == "getSignedIn") true else throw java.io.IOException("fixture")
        } as CloudAdapter
        val subscription = cloud.runningChanged.subscribe { running ->
            if (!running && blocking.compareAndSet(true, false)) {
                entered.countDown()
                check(release.await(10, TimeUnit.SECONDS))
            }
        }
        var service: SyncService? = null
        try {
            cloud.adapterId = "WEBDAV"
            category.setSyncEnabled(settings, true)
            field.set(cloud, adapter)
            scheduler.startDatabaseSync()
            service = Robolectric.buildService(SyncService::class.java).create().get()
            service.onStartCommand(Intent().setAction(SyncService.START_SERVICE), 0, 1)
            assertTrue("worker reaches terminal result callback", entered.await(10, TimeUnit.SECONDS))
            val worker = service.scope.coroutineContext[Job]!!.children.single()
            service.onTimeout(1, 1)
            val next = if (leave) scheduler.startLeave()!! else scheduler.startDatabaseSync()
            release.countDown()
            withTimeout(10000) { worker.join() }
            assertFalse("stale worker must not complete a newer request", next.isCompleted)
            if (leave) assertEquals("stale worker must not consume newer leave demand", AndroidSyncScheduler.Request.Leave, scheduler.consumeRequest())
        } finally {
            release.countDown()
            service?.let { withContext(NonCancellable) { withTimeout(10000) { it.scope.coroutineContext[Job]!!.cancelAndJoin() }; it.onDestroy() } }
            scheduler.complete(false)
            subscription.cancel()
            cloud.adapterId = oldId
            field.set(cloud, original)
            category.setSyncEnabled(settings, oldEnabled)
            cloud.resetSubscribersForTest()
        }
    }
}
