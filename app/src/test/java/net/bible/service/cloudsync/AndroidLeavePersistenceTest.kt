package net.bible.service.cloudsync

import android.content.ComponentName
import android.content.ContextWrapper
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import java.io.IOException
import java.lang.reflect.Proxy
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import net.bible.android.TestBibleApplication
import net.bible.android.control.buildPlatformBindings
import net.bible.android.platform.AndroidSyncScheduler
import net.bible.service.db.DatabaseContainer
import net.bible.sharedcore.platform.AppSettings
import net.bible.test.KoinFactorySnapshot
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.GlobalContext
import org.koin.core.context.loadKoinModules
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [35])
class AndroidLeavePersistenceTest {
    @Test fun serviceSkipsStalledPersistenceAndReleasesPending(): Unit = runBlocking {
        stalledPersistence(fallback = false)
    }

    @Test fun fallbackSkipsStalledPersistenceAndReleasesPending(): Unit = runBlocking {
        stalledPersistence(fallback = true)
    }

    private suspend fun stalledPersistence(fallback: Boolean) {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var stalled = true
        val saves = AtomicInteger()
        fixture(fallback, save = { saves.incrementAndGet(); entered.complete(Unit); release.await() }, await = {
            if (stalled) { entered.complete(Unit); release.await() }
        }) { driver, scheduler, service, cloud ->
            val runs = AtomicInteger()
            val subscription = cloud.runningChanged.subscribe { if (it) runs.incrementAndGet() }
            try {
                val job = if (fallback) driver.onBackground() else scheduler.startLeave()!!.also {
                    service!!.onStartCommand(Intent().setAction(SyncService.ACTION_LEAVE), 0, 1)
                }
                withTimeout(10000) { entered.await() }
                val finished = withTimeoutOrNull(8000) { job.join(); true } ?: false
                assertTrue("stalled persistence must release the sync owner within its bounded wait", finished)
                assertEquals("unfinished local persistence must never be followed by a sync", 0, runs.get())
                assertEquals("leave fallback must not repeat the independent onPause save", 0, saves.get())
                stalled = false
                if (fallback) {
                    val retry = scheduler.startDatabaseSync()
                    assertFalse("a later trigger gets a fresh pending owner", retry.isCompleted)
                    scheduler.complete(false)
                } else {
                    val retry = scheduler.startLeave()!!
                    service!!.onStartCommand(Intent().setAction(SyncService.ACTION_LEAVE), 0, 2)
                    withTimeout(10000) { retry.await() }
                    assertEquals("a later trigger may sync once persistence is ready", 1, runs.get())
                }
            } finally {
                release.complete(Unit)
                subscription.cancel()
            }
        }
    }

    @Test fun fallbackRechecksReplacementBeforeFollowUp(): Unit = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val prepares = AtomicInteger()
        val prepare: suspend () -> Unit = {
            if (prepares.incrementAndGet() == 1) {
                entered.complete(Unit)
                release.await()
                throw IOException("first iteration failed during replacement")
            }
        }
        fixture(fallback = true, save = prepare, await = prepare) { driver, scheduler, _, _ ->
            val replacementEntered = CompletableDeferred<Unit>()
            val endReplacement = CompletableDeferred<Unit>()
            coroutineScope {
                var replacement: Job? = null
                try {
                    val job = driver.onBackground()
                    withTimeout(10000) { entered.await() }
                    scheduler.startLeave()
                    replacement = launch(Dispatchers.IO) {
                        DatabaseContainer.replacingDatabases {
                            replacementEntered.complete(Unit)
                            endReplacement.await()
                        }
                    }
                    withTimeout(10000) { replacementEntered.await() }
                    release.complete(Unit)
                    withTimeout(10000) { job.join() }
                    assertEquals("database replacement must block follow-up persistence and engine work", 1, prepares.get())
                } finally {
                    release.complete(Unit)
                    endReplacement.complete(Unit)
                    withContext(NonCancellable) { withTimeout(10000) { replacement?.join() } }
                }
            }
        }
    }

    @Test fun fallbackRechecksDisabledPolicyBeforeFollowUp(): Unit = runBlocking { followUpBlocked("disabled") }
    @Test fun fallbackRechecksNetworkBeforeFollowUp(): Unit = runBlocking { followUpBlocked("offline") }

    private suspend fun followUpBlocked(reason: String) {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val prepares = AtomicInteger()
        var online = true
        val prepare: suspend () -> Unit = {
            if (prepares.incrementAndGet() == 1) { entered.complete(Unit); release.await() }
        }
        fixture(fallback = true, save = prepare, await = prepare, online = { online }) { driver, scheduler, _, _ ->
            try {
                val job = driver.onBackground()
                withTimeout(10000) { entered.await() }
                assertNotNull("another leave joins the fallback owner", scheduler.startLeave())
                when (reason) {
                    "disabled" -> SyncableDatabaseDefinition.ALL.forEach { it.setSyncEnabled(GlobalContext.get().get(), false) }
                    "offline" -> online = false
                }
                release.complete(Unit)
                withTimeout(10000) { job.join() }
                assertEquals("$reason must skip the queued iteration before persistence or engine work", 1, prepares.get())
            } finally {
                release.complete(Unit)
            }
        }
    }

    private suspend fun fixture(
        fallback: Boolean,
        save: suspend () -> Unit,
        await: suspend () -> Unit,
        online: () -> Boolean = { true },
        block: suspend (SyncTriggerDriver, AndroidSyncScheduler, SyncService?, CloudSync) -> Unit,
    ) {
        val context = ApplicationProvider.getApplicationContext<TestBibleApplication>()
        val wrapper = object : ContextWrapper(context) {
            override fun startForegroundService(intent: Intent): ComponentName {
                if (fallback && intent.action == SyncService.ACTION_LEAVE) throw IllegalStateException("fixture refused leave")
                return ComponentName(context, SyncService::class.java)
            }
        }
        val global = GlobalContext.get()
        val cloud = global.get<CloudSync>()
        val settings = global.get<AppSettings>()
        val previous = SyncableDatabaseDefinition.ALL.associateWith { it.isSyncEnabled(settings) }
        val oldId = cloud.adapterId
        val field = CloudSync::class.java.getDeclaredField("_adapter").apply { isAccessible = true }
        val oldAdapter = field.get(cloud)
        val adapter = Proxy.newProxyInstance(CloudAdapter::class.java.classLoader, arrayOf(CloudAdapter::class.java)) { _, method, _ ->
            if (method.name == "getSignedIn") true else throw IOException("fixture transport")
        } as CloudAdapter
        val scheduler = AndroidSyncScheduler(wrapper)
        val ports = SyncNowPorts(saveWorkspace = save, networkAvailable = online, noNetwork = {}, awaitWorkspacePersisted = await)
        val portsSnapshot = KoinFactorySnapshot.of<SyncNowPorts>()
        val schedulerSnapshot = KoinFactorySnapshot.of<AndroidSyncScheduler>()
        val app = koinApplication {
            androidContext(context)
            modules(buildPlatformBindings(), module {
                single { cloud }
                single { scheduler }
                single { ports }
            })
        }
        val driver = app.koin.get<SyncTriggerDriver>()
        var service: SyncService? = null
        try {
            cloud.adapterId = "WEBDAV"
            previous.keys.forEach { it.setSyncEnabled(settings, false) }
            SyncableDatabaseDefinition.BOOKMARKS.setSyncEnabled(settings, true)
            field.set(cloud, adapter)
            loadKoinModules(module { single { ports }; single { scheduler } })
            if (!fallback) service = Robolectric.buildService(SyncService::class.java).create().get()
            block(driver, scheduler, service, cloud)
        } finally {
            try {
                withContext(NonCancellable) {
                    try {
                        withTimeout(10000) { driver.stopAndJoin() }
                    } finally {
                        service?.let { withTimeout(10000) { it.scope.coroutineContext[Job]!!.cancelAndJoin() }; it.onDestroy() }
                    }
                }
            } finally {
                driver.close()
                app.close()
                scheduler.complete(false)
                portsSnapshot.restore()
                schedulerSnapshot.restore()
                cloud.adapterId = oldId
                field.set(cloud, oldAdapter)
                previous.forEach { (category, enabled) -> category.setSyncEnabled(settings, enabled) }
                cloud.resetSubscribersForTest()
            }
        }
    }
}
