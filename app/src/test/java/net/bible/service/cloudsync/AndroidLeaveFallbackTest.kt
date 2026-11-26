package net.bible.service.cloudsync

import android.content.ComponentName
import android.content.ContextWrapper
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import java.lang.reflect.Proxy
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.bible.android.control.page.window.WindowControl
import org.junit.Assert.assertNull
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import net.bible.android.TestBibleApplication
import net.bible.android.control.buildPlatformBindings
import net.bible.android.platform.AndroidSyncScheduler
import net.bible.sharedcore.platform.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.GlobalContext
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [35])
class AndroidLeaveFallbackTest {
    @Test fun manualSyncJoinsRunningInProcessFallback(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<TestBibleApplication>()
        val starts = AtomicInteger()
        val wrapper = object : ContextWrapper(context) {
            override fun startForegroundService(intent: Intent): ComponentName? {
                if (starts.incrementAndGet() == 1) throw IllegalStateException("background start refused")
                return ComponentName(context, SyncService::class.java)
            }
        }
        val global = GlobalContext.get()
        val cloud = global.get<CloudSync>()
        val settings = global.get<AppSettings>()
        val category = SyncableDatabaseDefinition.BOOKMARKS
        val oldEnabled = category.isSyncEnabled(settings)
        val oldId = cloud.adapterId
        val adapterField = CloudSync::class.java.getDeclaredField("_adapter").apply { isAccessible = true }
        val oldAdapter = adapterField.get(cloud)
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val scheduler = AndroidSyncScheduler(wrapper)
        val subscription = cloud.runningChanged.subscribe { running ->
            if (running) {
                entered.countDown()
                release.await(10, TimeUnit.SECONDS)
            }
        }
        val adapter = Proxy.newProxyInstance(CloudAdapter::class.java.classLoader, arrayOf(CloudAdapter::class.java)) { _, method, _ ->
            if (method.name == "getSignedIn") true else throw java.io.IOException("offline test adapter")
        } as CloudAdapter
        val app = koinApplication {
            androidContext(context)
            modules(buildPlatformBindings(), module {
                single { scheduler }
                single { cloud }
                single<AppSettings> { settings }
                single { SyncNowPorts(saveWorkspace = {}, networkAvailable = { true }, noNetwork = {}) }
            })
        }
        val driver = app.koin.get<SyncTriggerDriver>()
        try {
            cloud.adapterId = "WEBDAV"
            category.setSyncEnabled(settings, true)
            adapterField.set(cloud, adapter)
            val job = driver.onBackground()
            assertTrue("fallback must enter the sync job", withContext(Dispatchers.IO) { entered.await(10, TimeUnit.SECONDS) })
            val manual = scheduler.startUserInitiatedDatabaseSync()
            assertEquals("manual sync must join the fallback instead of starting a service", 1, starts.get())
            assertFalse(manual.isCompleted)
            release.countDown()
            withTimeout(10000) { job.join(); manual.await() }
        } finally {
            release.countDown()
            scheduler.complete(false)
            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) { withTimeout(10000) { driver.stopAndJoin() } }
            driver.close()
            app.close()
            subscription.cancel()
            cloud.adapterId = oldId
            adapterField.set(cloud, oldAdapter)
            category.setSyncEnabled(settings, oldEnabled)
            cloud.resetSubscribersForTest()
        }
    }

    @Test fun manualSyncJoinsFallbackDuringWorkspaceSave(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<TestBibleApplication>()
        val starts = AtomicInteger()
        val wrapper = object : ContextWrapper(context) {
            override fun startForegroundService(intent: Intent): ComponentName? {
                if (starts.incrementAndGet() == 1) throw IllegalStateException("background start refused")
                return ComponentName(context, SyncService::class.java)
            }
        }
        val global = GlobalContext.get()
        val cloud = global.get<CloudSync>()
        val settings = global.get<AppSettings>()
        val category = SyncableDatabaseDefinition.BOOKMARKS
        val oldEnabled = category.isSyncEnabled(settings)
        val oldId = cloud.adapterId
        val adapterField = CloudSync::class.java.getDeclaredField("_adapter").apply { isAccessible = true }
        val oldAdapter = adapterField.get(cloud)
        val entered = kotlinx.coroutines.CompletableDeferred<Unit>()
        val release = kotlinx.coroutines.CompletableDeferred<Unit>()
        val scheduler = AndroidSyncScheduler(wrapper)
        val adapter = Proxy.newProxyInstance(CloudAdapter::class.java.classLoader, arrayOf(CloudAdapter::class.java)) { _, method, _ ->
            if (method.name == "getSignedIn") true else throw java.io.IOException("offline test adapter")
        } as CloudAdapter
        val app = koinApplication {
            androidContext(context)
            modules(buildPlatformBindings(), module {
                single { scheduler }
                single { cloud }
                single<AppSettings> { settings }
                single { SyncNowPorts(saveWorkspace = { entered.complete(Unit); release.await() }, networkAvailable = { true }, noNetwork = {}) }
            })
        }
        val driver = app.koin.get<SyncTriggerDriver>()
        try {
            cloud.adapterId = "WEBDAV"
            category.setSyncEnabled(settings, true)
            adapterField.set(cloud, adapter)
            val job = driver.onBackground()
            withTimeout(10000) { entered.await() }
            val manual = scheduler.startUserInitiatedDatabaseSync()
            assertEquals("manual sync must join while the fallback saves", 1, starts.get())
            assertFalse(manual.isCompleted)
            release.complete(Unit)
            withTimeout(10000) { job.join(); manual.await() }
        } finally {
            release.complete(Unit)
            scheduler.complete(false)
            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) { withTimeout(10000) { driver.stopAndJoin() } }
            driver.close()
            app.close()
            cloud.adapterId = oldId
            adapterField.set(cloud, oldAdapter)
            category.setSyncEnabled(settings, oldEnabled)
            cloud.resetSubscribersForTest()
        }
    }

    @Test fun persistenceBarrierDoesNotCreateRepositoryWhenNoneIsLoaded() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<TestBibleApplication>()
        val control = WindowControl()
        val app = koinApplication {
            androidContext(context)
            modules(buildPlatformBindings(), module { single { control } })
        }
        try {
            app.koin.get<SyncNowPorts>().awaitWorkspacePersisted()
            assertNull("a leave barrier must not create a lazy repository", control.currentWindowRepositoryOrNull)
        } finally {
            control.retireWindowRepositoryForTest()
            app.close()
        }
    }

    @Test fun refusedLeaveRunsDirectSyncOnceAndDoesNotStrandScheduler() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<TestBibleApplication>()
        val starts = AtomicInteger()
        var refuse = true
        val wrapper = object : ContextWrapper(context) {
            override fun startForegroundService(intent: Intent): ComponentName? {
                starts.incrementAndGet()
                if (refuse) throw IllegalStateException("background start refused")
                return ComponentName(context, SyncService::class.java)
            }
        }
        val global = GlobalContext.get()
        val cloud = global.get<CloudSync>()
        val settings = global.get<AppSettings>()
        val category = SyncableDatabaseDefinition.BOOKMARKS
        val oldEnabled = category.isSyncEnabled(settings)
        val oldId = cloud.adapterId
        val adapterField = CloudSync::class.java.getDeclaredField("_adapter").apply { isAccessible = true }
        val oldAdapter = adapterField.get(cloud)
        val scheduler = AndroidSyncScheduler(wrapper)
        val prepares = AtomicInteger()
        val edges = CopyOnWriteArrayList<Boolean>()
        val subscription = cloud.runningChanged.subscribe { edges += it }
        val adapter = Proxy.newProxyInstance(CloudAdapter::class.java.classLoader, arrayOf(CloudAdapter::class.java)) { _, method, _ ->
            when (method.name) {
                "getSignedIn" -> true
                else -> throw java.io.IOException("offline test adapter: ${method.name}")
            }
        } as CloudAdapter
        val app = koinApplication {
            androidContext(context)
            modules(buildPlatformBindings(), module {
                single { scheduler }
                single { cloud }
                single<AppSettings> { settings }
                single { SyncNowPorts(saveWorkspace = { prepares.incrementAndGet() }, networkAvailable = { true }, noNetwork = {}) }
            })
        }
        val driver = app.koin.get<SyncTriggerDriver>()
        try {
            cloud.adapterId = "WEBDAV"
            category.setSyncEnabled(settings, true)
            adapterField.set(cloud, adapter)
            val job = driver.onBackground()
            withTimeout(10000) { job.join() }
            assertEquals("one rejected service start", 1, starts.get())
            assertEquals("fallback saves once", 1, prepares.get())
            assertEquals("fallback invokes the actual sync job", 1, edges.count { it })
            refuse = false
            val retry = scheduler.startDatabaseSync()
            assertEquals("later sync must start a new service", 2, starts.get())
            assertFalse(retry.isCompleted)
            scheduler.complete(true)
            assertTrue(retry.await())
        } finally {
            scheduler.complete(false)
            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                withTimeout(10000) { driver.stopAndJoin() }
            }
            driver.close()
            app.close()
            subscription.cancel()
            cloud.adapterId = oldId
            adapterField.set(cloud, oldAdapter)
            category.setSyncEnabled(settings, oldEnabled)
            cloud.resetSubscribersForTest()
        }
    }
}
