package net.bible.service.cloudsync

import android.content.ComponentName
import android.content.ContextWrapper
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import java.io.IOException
import java.lang.reflect.Proxy
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.startCoroutine
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import net.bible.android.TestBibleApplication
import net.bible.android.control.buildPlatformBindings
import net.bible.android.platform.AndroidSyncScheduler
import net.bible.service.db.DatabaseContainer
import net.bible.sharedcore.platform.AppSettings
import org.junit.Assert.*
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
class AndroidLeaveFallbackRegressionTest {
    @Test fun fallbackRechecksNetworkAfterPersistence() = runBlocking { recheck("network") }
    @Test fun fallbackRechecksEnabledAfterPersistence() = runBlocking { recheck("enabled") }
    @Test fun fallbackRechecksReplacementAfterPersistence() = runBlocking { recheck("replacement") }

    private suspend fun recheck(reason: String) = coroutineScope {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val replacementEntered = CompletableDeferred<Unit>()
        val endReplacement = CompletableDeferred<Unit>()
        val online = AtomicBoolean(true)
        fixture(signedIn = true, online = online, await = { entered.complete(Unit); release.await() }) { driver, _, _, edges, _, _, settings, _ ->
            var replacement: kotlinx.coroutines.Job? = null
            try {
                val job = driver.onBackground()
                withTimeout(10000) { entered.await() }
                when (reason) {
                    "network" -> online.set(false)
                    "enabled" -> SyncableDatabaseDefinition.ALL.forEach { it.setSyncEnabled(settings, false) }
                    "replacement" -> {
                        replacement = launch(Dispatchers.IO) {
                            DatabaseContainer.replacingDatabases { replacementEntered.complete(Unit); endReplacement.await() }
                        }
                        withTimeout(10000) { replacementEntered.await() }
                    }
                }
                release.complete(Unit)
                withTimeout(10000) { job.join() }
                assertFalse("$reason changed during persistence: no engine pass", edges.contains(true))
            } finally {
                release.complete(Unit)
                endReplacement.complete(Unit)
                withContext(NonCancellable) { withTimeout(10000) { replacement?.join() } }
            }
        }
    }

    @Test fun interactiveFallbackWaitsForExistingBackgroundSignIn() = runBlocking {
        val signInEntered = CompletableDeferred<Unit>()
        val releaseSignIn = CompletableDeferred<Unit>()
        val persistenceEntered = CompletableDeferred<Unit>()
        val releasePersistence = CompletableDeferred<Unit>()
        val secondPersistence = CompletableDeferred<Unit>()
        val continueSecond = CompletableDeferred<Unit>()
        val calls = AtomicInteger()
        fixture(signedIn = true, await = {
            if (calls.incrementAndGet() == 1) {
                persistenceEntered.complete(Unit)
                releasePersistence.await()
            } else {
                secondPersistence.complete(Unit)
                continueSecond.await()
            }
        }, signIn = {
            signInEntered.complete(Unit)
            releaseSignIn.await()
            true
        }) { driver, scheduler, cloud, edges, background, interactive, _, _ ->
            coroutineScope {
                try {
                    val leave = driver.onBackground()
                    withTimeout(10000) { persistenceEntered.await() }
                    val manual = scheduler.startUserInitiatedDatabaseSync()
                    releasePersistence.complete(Unit)
                    withTimeout(10000) { secondPersistence.await() }
                    edges.clear()
                    cloud.dropAdapterForReconnect()
                    val signIn = async(Dispatchers.IO) { cloud.signIn(background = true) }
                    withTimeout(10000) { signInEntered.await() }
                    continueSecond.complete(Unit)
                    delay(200)
                    assertFalse("manual owner must wait for the in-flight sign-in", manual.isCompleted)
                    assertFalse("no engine starts before sign-in finishes", edges.contains(true))
                    releaseSignIn.complete(Unit)
                    withTimeout(10000) { signIn.await(); leave.join(); manual.await() }
                    assertEquals(1, background.get())
                    assertEquals("manual request must share the in-flight sign-in", 0, interactive.get())
                    assertTrue("manual follow-up reaches the real sync engine", edges.contains(true))
                } finally {
                    releasePersistence.complete(Unit)
                    continueSecond.complete(Unit)
                    releaseSignIn.complete(Unit)
                }
            }
        }
    }

    private suspend fun fixture(
        signedIn: Boolean,
        online: AtomicBoolean = AtomicBoolean(true),
        await: suspend () -> Unit = {},
        signIn: suspend () -> Boolean = { true },
        block: suspend (SyncTriggerDriver, AndroidSyncScheduler, CloudSync, CopyOnWriteArrayList<Boolean>, AtomicInteger, AtomicInteger, AppSettings, CloudAdapter) -> Unit,
    ) {
        val context = ApplicationProvider.getApplicationContext<TestBibleApplication>()
        val wrapper = object : ContextWrapper(context) {
            override fun startForegroundService(intent: Intent): ComponentName {
                if (intent.action == SyncService.ACTION_LEAVE) throw IllegalStateException("refused leave")
                return ComponentName(context, SyncService::class.java)
            }
        }
        val global = GlobalContext.get()
        val cloud = global.get<CloudSync>()
        val settings = global.get<AppSettings>()
        val previous = SyncableDatabaseDefinition.ALL.associateWith { it.isSyncEnabled(settings) }
        val oldId = cloud.adapterId
        val adapterField = CloudSync::class.java.getDeclaredField("_adapter").apply { isAccessible = true }
        val factoryField = CloudSync::class.java.getDeclaredField("adapters").apply { isAccessible = true }
        val oldAdapter = adapterField.get(cloud)
        val oldFactory = factoryField.get(cloud)
        val background = AtomicInteger()
        val interactive = AtomicInteger()
        val authenticated = AtomicBoolean(signedIn)
        val adapter = Proxy.newProxyInstance(CloudAdapter::class.java.classLoader, arrayOf(CloudAdapter::class.java)) { _, method, args ->
            when (method.name) {
                "getSignedIn" -> authenticated.get()
                "signIn" -> {
                    val continuation = args.last() as kotlin.coroutines.Continuation<Boolean>
                    val operation: suspend () -> Boolean = { signIn().also { authenticated.set(it) } }
                    operation.startCoroutine(continuation)
                    kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
                }
                else -> throw IOException("fixture transport")
            }
        } as CloudAdapter
        val factory = object : CloudAdapterFactory {
            override val available = listOf("WEBDAV")
            override fun displayName(id: String) = id
            override fun create(id: String): CloudAdapter { interactive.incrementAndGet(); return adapter }
            override fun createForBackground(id: String): CloudAdapter { background.incrementAndGet(); return adapter }
        }
        val scheduler = AndroidSyncScheduler(wrapper)
        val ports = SyncNowPorts(saveWorkspace = {}, networkAvailable = { online.get() }, noNetwork = {}, awaitWorkspacePersisted = await)
        val app = koinApplication {
            androidContext(context)
            modules(buildPlatformBindings(), module { single { cloud }; single { scheduler }; single { ports } })
        }
        val driver = app.koin.get<SyncTriggerDriver>()
        val edges = CopyOnWriteArrayList<Boolean>()
        val subscription = cloud.runningChanged.subscribe { edges += it }
        try {
            cloud.adapterId = "WEBDAV"
            previous.keys.forEach { it.setSyncEnabled(settings, false) }
            SyncableDatabaseDefinition.BOOKMARKS.setSyncEnabled(settings, true)
            factoryField.set(cloud, factory)
            adapterField.set(cloud, if (signedIn) adapter else null)
            block(driver, scheduler, cloud, edges, background, interactive, settings, adapter)
        } finally {
            withContext(NonCancellable) { withTimeout(10000) { driver.stopAndJoin() } }
            driver.close()
            app.close()
            scheduler.complete(false)
            subscription.cancel()
            factoryField.set(cloud, oldFactory)
            cloud.adapterId = oldId
            adapterField.set(cloud, oldAdapter)
            previous.forEach { (category, enabled) -> category.setSyncEnabled(settings, enabled) }
        }
    }
}
