package net.bible.service.cloudsync

import android.content.Intent
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import net.bible.android.TestBibleApplication
import net.bible.android.platform.AndroidSyncScheduler
import net.bible.sharedcore.platform.AppSettings
import net.bible.test.KoinFactorySnapshot
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.koin.core.context.loadKoinModules
import org.koin.dsl.module
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [35])
class SyncServicePendingRegressionTest {
    @Test fun preparationFailureStillRunsPendingLeave() = preparationFailureStillRunsPendingLeave(IOException("first preparation failed"))

    @Test fun preparationCancellationFromActiveJobStillRunsPendingLeave() =
        preparationFailureStillRunsPendingLeave(kotlinx.coroutines.CancellationException("authorization cancelled"))

    private fun preparationFailureStillRunsPendingLeave(failure: Exception) = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val preparations = AtomicInteger()
        fixture(signedIn = true, prepare = {
            if (preparations.incrementAndGet() == 1) {
                entered.complete(Unit)
                release.await()
                throw failure
            }
        }) { service, scheduler, _ ->
            try {
                val result = scheduler.startLeave()!!
                service.onStartCommand(Intent().setAction(SyncService.ACTION_LEAVE), 0, 1)
                withTimeout(10000) { entered.await() }
                assertSame(result, scheduler.startLeave())
                release.complete(Unit)
                withTimeout(10000) { result.await() }
                assertEquals("a failed preparation must not discard the pending leave", 2, preparations.get())
            } finally {
                release.complete(Unit)
            }
        }
    }

    @Test fun skippedFollowUpDoesNotReturnPreviousSuccess() = runBlocking {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val first = AtomicBoolean(true)
        fixture(signedIn = true, enableCategory = false) { service, scheduler, cloud ->
            val subscription = cloud.runningChanged.subscribe { running ->
                if (!running && first.compareAndSet(true, false)) {
                    entered.countDown()
                    check(release.await(10, TimeUnit.SECONDS))
                }
            }
            try {
                val result = scheduler.startDatabaseSync()
                service.onStartCommand(Intent().setAction(SyncService.START_SERVICE), 0, 1)
                assertTrue("first successful pass reaches completion", entered.await(10, TimeUnit.SECONDS))
                assertSame(result, scheduler.startLeave())
                release.countDown()
                assertFalse("skipped leave must not inherit the previous pass result", withTimeout(10000) { result.await() })
            } finally {
                release.countDown()
                subscription.cancel()
            }
        }
    }

    @Test fun manualRequestUpgradesQueuedSignedOutLeave() = runBlocking {
        fixture(signedIn = false) { service, scheduler, cloud ->
            val factoryField = CloudSync::class.java.getDeclaredField("adapters").apply { isAccessible = true }
            val originalFactory = factoryField.get(cloud)
            val signIns = AtomicInteger()
            val adapter = java.lang.reflect.Proxy.newProxyInstance(
                CloudAdapter::class.java.classLoader, arrayOf(CloudAdapter::class.java),
            ) { _, method, _ ->
                when (method.name) {
                    "getSignedIn" -> true
                    "signIn" -> { signIns.incrementAndGet(); true }
                    else -> throw IOException("fixture transport failure")
                }
            } as CloudAdapter
            factoryField.set(cloud, object : CloudAdapterFactory {
                override val available = listOf("WEBDAV")
                override fun displayName(id: String) = id
                override fun create(id: String) = adapter
            })
            try {
                val result = scheduler.startLeave()!!
                assertSame(result, scheduler.startUserInitiatedDatabaseSync())
                service.onStartCommand(Intent().setAction(SyncService.ACTION_LEAVE), 0, 1)
                withTimeout(10000) { result.await() }
                assertEquals("manual request must attempt interactive sign-in", 1, signIns.get())
            } finally {
                withContext(NonCancellable) { withTimeout(10000) { service.scope.coroutineContext[Job]!!.children.toList().forEach { it.join() } } }
                factoryField.set(cloud, originalFactory)
            }
        }
    }

    private suspend fun fixture(
        signedIn: Boolean,
        enableCategory: Boolean = true,
        prepare: suspend () -> Unit = {},
        block: suspend (SyncService, AndroidSyncScheduler, CloudSync) -> Unit,
    ) {
        val koin = GlobalContext.get()
        val cloud = koin.get<CloudSync>()
        val scheduler = koin.get<AndroidSyncScheduler>()
        val settings = koin.get<AppSettings>()
        val previous = SyncableDatabaseDefinition.ALL.associateWith { it.isSyncEnabled(settings) }
        val oldId = cloud.adapterId
        val field = CloudSync::class.java.getDeclaredField("_adapter").apply { isAccessible = true }
        val oldAdapter = field.get(cloud)
        val portsSnapshot = KoinFactorySnapshot.of<SyncNowPorts>()
        val adapter = java.lang.reflect.Proxy.newProxyInstance(
            CloudAdapter::class.java.classLoader, arrayOf(CloudAdapter::class.java),
        ) { _, method, _ ->
            if (method.name == "getSignedIn") signedIn else throw IOException("fixture transport failure")
        } as CloudAdapter
        var service: SyncService? = null
        try {
            cloud.adapterId = "WEBDAV"
            previous.keys.forEach { it.setSyncEnabled(settings, false) }
            if (enableCategory) SyncableDatabaseDefinition.BOOKMARKS.setSyncEnabled(settings, true)
            field.set(cloud, adapter)
            loadKoinModules(module { single { SyncNowPorts(saveWorkspace = {}, networkAvailable = { true }, noNetwork = {}, awaitWorkspacePersisted = prepare) } })
            service = Robolectric.buildService(SyncService::class.java).create().get()
            block(service, scheduler, cloud)
        } finally {
            service?.let { withContext(NonCancellable) {
                withTimeout(10000) { it.scope.coroutineContext[Job]!!.cancelAndJoin() }
                it.onDestroy()
            } }
            scheduler.complete(false)
            portsSnapshot.restore()
            cloud.adapterId = oldId
            field.set(cloud, oldAdapter)
            previous.forEach { (category, value) -> category.setSyncEnabled(settings, value) }
            cloud.resetSubscribersForTest()
        }
    }
}
