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
    fun automaticAndManualServiceRunsRouteActualTransportFailuresDifferently() = runBlocking {
        val cloud = GlobalContext.get().get<CloudSync>()
        val scheduler = GlobalContext.get().get<AndroidSyncScheduler>()
        val settings = GlobalContext.get().get<net.bible.sharedcore.platform.AppSettings>()
        val category = SyncableDatabaseDefinition.BOOKMARKS
        val previousEnabled = category.isSyncEnabled(settings)
        val adapterField = CloudSync::class.java.getDeclaredField("_adapter").apply { isAccessible = true }
        val originalAdapter = adapterField.get(cloud)
        val messages = java.util.concurrent.CopyOnWriteArrayList<net.bible.android.control.event.UserMessage>()
        val subscription = net.bible.android.control.event.UserMessages.messages.subscribe { messages += it }
        var failure: Exception = java.io.IOException("connection refused")
        val observedFailures = java.util.concurrent.CopyOnWriteArrayList<Exception>()
        fun fail(): Nothing { observedFailures += failure; throw failure }
        val adapter = object : CloudAdapter {
            override val signedIn = true
            override suspend fun signIn() = true
            override suspend fun signOut() = Unit
            override suspend fun get(id: String): net.bible.sharedcore.platform.cloud.CloudFile = fail()
            override suspend fun listFiles(parentsIds: List<String>?, name: String?, mimeType: String?, createdTimeAtLeast: Long?): List<net.bible.sharedcore.platform.cloud.CloudFile> = fail()
            override suspend fun getFolders(parentId: String): List<net.bible.sharedcore.platform.cloud.CloudFile> = fail()
            override suspend fun download(id: String, sink: okio.Sink, onProgress: net.bible.sharedcore.platform.cloud.DownloadProgressListener?) { fail() }
            override suspend fun createNewFolder(name: String, parentId: String?): net.bible.sharedcore.platform.cloud.CloudFile = fail()
            override suspend fun upload(name: String, file: okio.Path, parentId: String): net.bible.sharedcore.platform.cloud.CloudFile = fail()
            override suspend fun delete(id: String) { fail() }
            override suspend fun isSyncFolderKnown(dbDef: SyncableDatabaseAccessor<*>, name: String, id: String): Boolean = fail()
            override suspend fun makeSyncFolderKnown(dbDef: SyncableDatabaseAccessor<*>, name: String, id: String) { fail() }
            override suspend fun getConfigs(dbDef: SyncableDatabaseAccessor<*>): List<net.bible.android.database.SyncConfiguration> = fail()
        }
        try {
            adapterField.set(cloud, adapter)
            category.setSyncEnabled(settings, true)
            for ((manual, transport) in listOf(false to true, true to true, false to false, true to false)) {
                failure = if (transport) java.io.IOException("connection refused") else IllegalStateException("server error")
                messages.clear()
                observedFailures.clear()
                val service = Robolectric.buildService(SyncService::class.java).create().get()
                try {
                    val intent = android.content.Intent().setAction(SyncService.START_SERVICE)
                        .putExtra(SyncService.USER_INITIATED, manual)
                    service.onStartCommand(intent, 0, 1)
                    withTimeout(10000) { service.scope.coroutineContext[Job]!!.children.toList().forEach { it.join() } }
                    assertTrue("adapter must throw the actual configured failure: $observedFailures", observedFailures.any { it === failure })
                    if (transport) assertTrue(observedFailures.first() is java.io.IOException)
                    val errors = messages.filterIsInstance<net.bible.android.control.event.UserMessage.ErrorNotification>()
                    if (transport && !manual) assertTrue("automatic transport errors: $errors", errors.isEmpty())
                    else {
                        assertTrue(errors.isNotEmpty())
                        assertTrue(errors.all { it.background == !manual })
                    }
                } finally { service.onDestroy(); scheduler.complete(false) }
            }
        } finally {
            subscription.cancel()
            adapterField.set(cloud, originalAdapter)
            category.setSyncEnabled(settings, previousEnabled)
            cloud.resetSubscribersForTest()
        }
    }

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
