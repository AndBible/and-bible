package net.bible.service.cloudsync

import android.content.ComponentName
import android.content.ContextWrapper
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import java.lang.reflect.Proxy
import java.util.concurrent.CopyOnWriteArrayList
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
