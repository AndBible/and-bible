package net.bible.service.cloudsync

import android.content.Intent
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import net.bible.android.TestBibleApplication
import net.bible.android.platform.AndroidSyncScheduler
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
class SyncServiceLeaveBehaviorTest {
    @Test fun disabledSyncSkipsLeave() = runBlocking { assertLeaveSkipped(enabled = false, online = true) }
    @Test fun offlineSyncSkipsLeave() = runBlocking { assertLeaveSkipped(enabled = true, online = false) }

    private suspend fun assertLeaveSkipped(enabled: Boolean, online: Boolean) {
        val koin = GlobalContext.get()
        val cloud = koin.get<CloudSync>()
        val scheduler = koin.get<AndroidSyncScheduler>()
        val settings = koin.get<net.bible.sharedcore.platform.AppSettings>()
        val previous = SyncableDatabaseDefinition.ALL.associateWith { it.isSyncEnabled(settings) }
        val oldId = cloud.adapterId
        val field = CloudSync::class.java.getDeclaredField("_adapter").apply { isAccessible = true }
        val oldAdapter = field.get(cloud)
        val snapshot = KoinFactorySnapshot.of<SyncNowPorts>()
        val prepares = AtomicInteger()
        val edges = CopyOnWriteArrayList<Boolean>()
        val subscription = cloud.runningChanged.subscribe { edges += it }
        val adapter = java.lang.reflect.Proxy.newProxyInstance(CloudAdapter::class.java.classLoader, arrayOf(CloudAdapter::class.java)) { _, method, _ ->
            if (method.name == "getSignedIn") true else error("Unexpected adapter call: ${method.name}")
        } as CloudAdapter
        var service: SyncService? = null
        try {
            cloud.adapterId = "WEBDAV"
            previous.keys.forEach { it.setSyncEnabled(settings, false) }
            if (enabled) SyncableDatabaseDefinition.BOOKMARKS.setSyncEnabled(settings, true)
            field.set(cloud, adapter)
            loadKoinModules(module { single { SyncNowPorts(saveWorkspace = {}, networkAvailable = { online }, noNetwork = {}, awaitWorkspacePersisted = { prepares.incrementAndGet() }) } })
            service = Robolectric.buildService(SyncService::class.java).create().get()
            val pending = scheduler.startLeave()!!
            service.onStartCommand(Intent().setAction(SyncService.ACTION_LEAVE), 0, 1)
            withTimeout(10000) { service.scope.coroutineContext[Job]!!.children.toList().forEach { it.join() } }
            assertTrue("skipped leave completes pending", pending.isCompleted)
            assertEquals(0, prepares.get())
            assertFalse(edges.contains(true))
            assertTrue(org.robolectric.Shadows.shadowOf(service).isStoppedBySelf)
        } finally {
            service?.let { kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) { withTimeout(10000) { it.scope.coroutineContext[Job]!!.cancelAndJoin() }; it.onDestroy() } }
            scheduler.complete(false)
            subscription.cancel()
            snapshot.restore()
            cloud.adapterId = oldId
            field.set(cloud, oldAdapter)
            previous.forEach { (category, value) -> category.setSyncEnabled(settings, value) }
        }
    }

    @Test fun leaveWaitsForPersistenceBeforeStartingEngine() = runBlocking {
        val koin = GlobalContext.get()
        val cloud = koin.get<CloudSync>()
        val scheduler = koin.get<AndroidSyncScheduler>()
        val settings = koin.get<net.bible.sharedcore.platform.AppSettings>()
        val category = SyncableDatabaseDefinition.BOOKMARKS
        val enabled = category.isSyncEnabled(settings)
        val oldId = cloud.adapterId
        val field = CloudSync::class.java.getDeclaredField("_adapter").apply { isAccessible = true }
        val original = field.get(cloud)
        val snapshot = KoinFactorySnapshot.of<SyncNowPorts>()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val edges = CopyOnWriteArrayList<Boolean>()
        val subscription = cloud.runningChanged.subscribe { edges += it }
        val adapter = java.lang.reflect.Proxy.newProxyInstance(CloudAdapter::class.java.classLoader, arrayOf(CloudAdapter::class.java)) { _, method, _ ->
            if (method.name == "getSignedIn") true else throw IOException("fixture")
        } as CloudAdapter
        var service: SyncService? = null
        try {
            cloud.adapterId = "WEBDAV"
            category.setSyncEnabled(settings, true)
            field.set(cloud, adapter)
            loadKoinModules(module { single { SyncNowPorts(saveWorkspace = {}, networkAvailable = { true }, noNetwork = {}, awaitWorkspacePersisted = { entered.complete(Unit); release.await() }) } })
            service = Robolectric.buildService(SyncService::class.java).create().get()
            scheduler.startLeave()
            service.onStartCommand(Intent().setAction(SyncService.ACTION_LEAVE), 0, 1)
            withTimeout(10000) { entered.await() }
            assertFalse("engine must wait for workspace persistence", edges.contains(true))
            release.complete(Unit)
            withTimeout(10000) { service.scope.coroutineContext[Job]!!.children.toList().forEach { it.join() } }
            assertTrue("engine starts after persistence", edges.contains(true))
        } finally {
            release.complete(Unit)
            service?.let { kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) { withTimeout(10000) { it.scope.coroutineContext[Job]!!.cancelAndJoin() }; it.onDestroy() } }
            scheduler.complete(false)
            subscription.cancel()
            snapshot.restore()
            cloud.adapterId = oldId
            field.set(cloud, original)
            category.setSyncEnabled(settings, enabled)
        }
    }

    @Test fun leaveRechecksNetworkAfterPersistence() = runBlocking { recheckAfterPersistence("network") }
    @Test fun leaveRechecksEnabledAfterPersistence() = runBlocking { recheckAfterPersistence("enabled") }
    @Test fun leaveRechecksReplacementAfterPersistence() = runBlocking { recheckAfterPersistence("replacement") }

    private suspend fun recheckAfterPersistence(reason: String) = kotlinx.coroutines.coroutineScope {
        val koin = GlobalContext.get()
        val cloud = koin.get<CloudSync>()
        val scheduler = koin.get<AndroidSyncScheduler>()
        val settings = koin.get<net.bible.sharedcore.platform.AppSettings>()
        val previous = SyncableDatabaseDefinition.ALL.associateWith { it.isSyncEnabled(settings) }
        val oldId = cloud.adapterId
        val field = CloudSync::class.java.getDeclaredField("_adapter").apply { isAccessible = true }
        val original = field.get(cloud)
        val snapshot = KoinFactorySnapshot.of<SyncNowPorts>()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val replacementEntered = CompletableDeferred<Unit>()
        val endReplacement = CompletableDeferred<Unit>()
        var replacement: Job? = null
        val online = java.util.concurrent.atomic.AtomicBoolean(true)
        val edges = CopyOnWriteArrayList<Boolean>()
        val subscription = cloud.runningChanged.subscribe { edges += it }
        val adapter = java.lang.reflect.Proxy.newProxyInstance(CloudAdapter::class.java.classLoader, arrayOf(CloudAdapter::class.java)) { _, method, _ ->
            if (method.name == "getSignedIn") true else throw IOException("fixture")
        } as CloudAdapter
        var service: SyncService? = null
        try {
            cloud.adapterId = "WEBDAV"
            previous.keys.forEach { it.setSyncEnabled(settings, false) }
            SyncableDatabaseDefinition.BOOKMARKS.setSyncEnabled(settings, true)
            field.set(cloud, adapter)
            loadKoinModules(module { single { SyncNowPorts(saveWorkspace = {}, networkAvailable = { online.get() }, noNetwork = {}, awaitWorkspacePersisted = { entered.complete(Unit); release.await() }) } })
            service = Robolectric.buildService(SyncService::class.java).create().get()
            val pending = scheduler.startLeave()!!
            service.onStartCommand(Intent().setAction(SyncService.ACTION_LEAVE), 0, 1)
            withTimeout(10000) { entered.await() }
            when (reason) {
                "network" -> online.set(false)
                "enabled" -> previous.keys.forEach { it.setSyncEnabled(settings, false) }
                "replacement" -> {
                    replacement = launch(kotlinx.coroutines.Dispatchers.IO) {
                        net.bible.service.db.DatabaseContainer.replacingDatabases {
                            replacementEntered.complete(Unit)
                            endReplacement.await()
                        }
                    }
                    withTimeout(10000) { replacementEntered.await() }
                }
            }
            release.complete(Unit)
            withTimeout(10000) { pending.await() }
            assertFalse("$reason changed during persistence: no engine pass", edges.contains(true))
        } finally {
            release.complete(Unit)
            endReplacement.complete(Unit)
            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) { withTimeout(10000) { replacement?.join() } }
            service?.let { kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) { withTimeout(10000) { it.scope.coroutineContext[Job]!!.cancelAndJoin() }; it.onDestroy() } }
            scheduler.complete(false)
            subscription.cancel()
            snapshot.restore()
            cloud.adapterId = oldId
            field.set(cloud, original)
            previous.forEach { (category, enabled) -> category.setSyncEnabled(settings, enabled) }
        }
    }

    @Test fun cleanupCancelsAndJoinsBlockedWorkerBeforeRestoration() = runBlocking {
        val koin = GlobalContext.get()
        val cloud = koin.get<CloudSync>()
        val scheduler = koin.get<AndroidSyncScheduler>()
        val settings = koin.get<net.bible.sharedcore.platform.AppSettings>()
        val category = SyncableDatabaseDefinition.BOOKMARKS
        val enabled = category.isSyncEnabled(settings)
        val oldId = cloud.adapterId
        val field = CloudSync::class.java.getDeclaredField("_adapter").apply { isAccessible = true }
        val original = field.get(cloud)
        val snapshot = KoinFactorySnapshot.of<SyncNowPorts>()
        val entered = CompletableDeferred<Unit>()
        val ended = CompletableDeferred<Unit>()
        val never = CompletableDeferred<Unit>()
        val adapter = java.lang.reflect.Proxy.newProxyInstance(CloudAdapter::class.java.classLoader, arrayOf(CloudAdapter::class.java)) { _, method, _ ->
            if (method.name == "getSignedIn") true else error("Unexpected adapter call: ${method.name}")
        } as CloudAdapter
        var service: SyncService? = null
        try {
            cloud.adapterId = "WEBDAV"
            category.setSyncEnabled(settings, true)
            field.set(cloud, adapter)
            loadKoinModules(module { single { SyncNowPorts(saveWorkspace = {}, networkAvailable = { true }, noNetwork = {}, awaitWorkspacePersisted = { try { entered.complete(Unit); never.await() } finally { ended.complete(Unit) } }) } })
            service = Robolectric.buildService(SyncService::class.java).create().get()
            scheduler.startLeave()
            service.onStartCommand(Intent().setAction(SyncService.ACTION_LEAVE), 0, 1)
            withTimeout(10000) { entered.await() }
            withTimeout(10000) { service.scope.coroutineContext[Job]!!.cancelAndJoin() }
            assertTrue("worker finally completes before restoring shared state", ended.isCompleted)
            assertTrue(service.scope.coroutineContext[Job]!!.isCompleted)
        } finally {
            never.complete(Unit)
            service?.let { kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) { withTimeout(10000) { it.scope.coroutineContext[Job]!!.cancelAndJoin() }; it.onDestroy() } }
            scheduler.complete(false)
            snapshot.restore()
            cloud.adapterId = oldId
            field.set(cloud, original)
            category.setSyncEnabled(settings, enabled)
        }
    }

    @Test fun signedOutLeaveSkipsPreparationAndDoesNotStartSyncOrSignIn() = runBlocking {
        val koin = GlobalContext.get()
        val cloud = koin.get<CloudSync>()
        val scheduler = koin.get<AndroidSyncScheduler>()
        val settings = koin.get<net.bible.sharedcore.platform.AppSettings>()
        val category = SyncableDatabaseDefinition.BOOKMARKS
        val oldEnabled = category.isSyncEnabled(settings)
        val oldAdapterId = cloud.adapterId
        val adapterField = CloudSync::class.java.getDeclaredField("_adapter").apply { isAccessible = true }
        val oldAdapter = adapterField.get(cloud)
        val portsSnapshot = KoinFactorySnapshot.of<SyncNowPorts>()
        val prepares = AtomicInteger()
        val calls = CopyOnWriteArrayList<String>()
        val edges = CopyOnWriteArrayList<Boolean>()
        val subscription = cloud.runningChanged.subscribe { edges += it }
        val adapter = java.lang.reflect.Proxy.newProxyInstance(
            CloudAdapter::class.java.classLoader, arrayOf(CloudAdapter::class.java),
        ) { _, method, _ ->
            calls += method.name
            if (method.name == "getSignedIn") false else error("Unexpected adapter call: ${method.name}")
        } as CloudAdapter
        var service: SyncService? = null
        try {
            cloud.adapterId = "WEBDAV"
            category.setSyncEnabled(settings, true)
            adapterField.set(cloud, adapter)
            loadKoinModules(module { single { SyncNowPorts(saveWorkspace = {}, networkAvailable = { true }, noNetwork = {}, awaitWorkspacePersisted = { prepares.incrementAndGet() }) } })
            service = Robolectric.buildService(SyncService::class.java).create().get()
            scheduler.startLeave()
            service.onStartCommand(Intent().setAction(SyncService.ACTION_LEAVE), 0, 1)
            withTimeout(10000) { service.scope.coroutineContext[Job]!!.children.toList().forEach { it.join() } }
            assertEquals(0, prepares.get())
            assertFalse("signed-out leave must not start an engine pass", edges.contains(true))
            assertFalse("signed-out leave must not sign in", calls.contains("signIn"))
        } finally {
            service?.let { kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                withTimeout(10000) { it.scope.coroutineContext[Job]!!.cancelAndJoin() }
                it.onDestroy()
            } }
            scheduler.complete(false)
            subscription.cancel()
            portsSnapshot.restore()
            cloud.adapterId = oldAdapterId
            adapterField.set(cloud, oldAdapter)
            category.setSyncEnabled(settings, oldEnabled)
            cloud.resetSubscribersForTest()
        }
    }

    @Test fun refusedLeaveDuringRunningSyncAndManualFollowUpErrorRouting() = runBlocking { manualFollowUp(refuse = true) }
    @Test fun deliveredLeaveAfterManualSyncDoesNotReportTransportFailure() = runBlocking { manualFollowUp(refuse = false) }

    @Test fun manualRequestJoiningRunningLeaveReportsTransportFailure() = runBlocking { manualFollowUp(refuse = false, manualJoinsLeave = true) }

    private suspend fun manualFollowUp(refuse: Boolean, manualJoinsLeave: Boolean = false) {
        val koin = GlobalContext.get()
        val cloud = koin.get<CloudSync>()
        val realScheduler = koin.get<AndroidSyncScheduler>()
        val starts = AtomicInteger()
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<TestBibleApplication>()
        val refusingContext = object : android.content.ContextWrapper(context) {
            override fun startForegroundService(intent: Intent): android.content.ComponentName {
                if (starts.incrementAndGet() > 1 && refuse) throw IllegalStateException("leave refused")
                return android.content.ComponentName(context, SyncService::class.java)
            }
        }
        val scheduler = AndroidSyncScheduler(refusingContext)
        val schedulerSnapshot = KoinFactorySnapshot.of<AndroidSyncScheduler>()
        val settings = koin.get<net.bible.sharedcore.platform.AppSettings>()
        val category = SyncableDatabaseDefinition.BOOKMARKS
        val oldEnabled = category.isSyncEnabled(settings)
        val oldAdapterId = cloud.adapterId
        val adapterField = CloudSync::class.java.getDeclaredField("_adapter").apply { isAccessible = true }
        val oldAdapter = adapterField.get(cloud)
        val portsSnapshot = KoinFactorySnapshot.of<SyncNowPorts>()
        val accessor = net.bible.service.db.DatabaseContainer.databaseAccessorFactories.map { it.invoke() }
            .first { it.category == category }
        val oldFolder = accessor.dao.getString(SYNC_FOLDER_FILE_ID_KEY)
        val oldDeviceFolder = accessor.dao.getString(SYNC_DEVICE_FOLDER_FILE_ID_KEY)
        val prepares = AtomicInteger()
        val firstProbe = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val probes = AtomicInteger()
        val folderReads = AtomicInteger()
        val running = CopyOnWriteArrayList<Boolean>()
        val subscription = cloud.runningChanged.subscribe { running += it }
        val messages = CopyOnWriteArrayList<net.bible.android.control.event.UserMessage>()
        val messageSubscription = net.bible.android.control.event.UserMessages.messages.subscribe { messages += it }
        val adapter = object : CloudAdapter {
            override val signedIn = true
            override suspend fun signIn() = true
            override suspend fun signOut() = Unit
            override suspend fun get(id: String): net.bible.sharedcore.platform.cloud.CloudFile = throw IOException("fixture")
            override suspend fun listFiles(parentsIds: List<String>?, name: String?, mimeType: String?, createdTimeAtLeast: Long?): List<net.bible.sharedcore.platform.cloud.CloudFile> {
                if (probes.incrementAndGet() == 1) { firstProbe.complete(Unit); release.await() }
                throw IOException("fixture")
            }
            override suspend fun getFolders(parentId: String): List<net.bible.sharedcore.platform.cloud.CloudFile> {
                if (folderReads.incrementAndGet() == 1) {
                    firstProbe.complete(Unit)
                    release.await()
                    return emptyList()
                }
                throw IOException("follow-up transport failure")
            }
            override suspend fun download(id: String, sink: okio.Sink, onProgress: net.bible.sharedcore.platform.cloud.DownloadProgressListener?) { throw IOException("fixture") }
            override suspend fun createNewFolder(name: String, parentId: String?): net.bible.sharedcore.platform.cloud.CloudFile = throw IOException("fixture")
            override suspend fun upload(name: String, file: okio.Path, parentId: String) = net.bible.sharedcore.platform.cloud.CloudFile("uploaded", name, 0, 1, parentId)
            override suspend fun delete(id: String) { throw IOException("fixture") }
            override suspend fun isSyncFolderKnown(dbDef: SyncableDatabaseAccessor<*>, name: String, id: String): Boolean {
                probes.incrementAndGet()
                return true
            }
            override suspend fun makeSyncFolderKnown(dbDef: SyncableDatabaseAccessor<*>, name: String, id: String) { throw IOException("fixture") }
            override suspend fun getConfigs(dbDef: SyncableDatabaseAccessor<*>): List<net.bible.android.database.SyncConfiguration> = throw IOException("fixture")
        }
        var service: SyncService? = null
        try {
            cloud.adapterId = "WEBDAV"
            category.setSyncEnabled(settings, true)
            accessor.dao.setConfig(SYNC_FOLDER_FILE_ID_KEY, "test-folder")
            accessor.dao.setConfig(SYNC_DEVICE_FOLDER_FILE_ID_KEY, "test-device")
            adapterField.set(cloud, adapter)
            loadKoinModules(module {
                single { scheduler }
                single { SyncNowPorts(saveWorkspace = {}, networkAvailable = { true }, noNetwork = {}, awaitWorkspacePersisted = { prepares.incrementAndGet() }) }
            })
            val pending = if (manualJoinsLeave) scheduler.startLeave()!! else scheduler.startUserInitiatedDatabaseSync()
            service = Robolectric.buildService(SyncService::class.java).create().get()
            val initial = if (manualJoinsLeave) Intent().setAction(SyncService.ACTION_LEAVE)
                else Intent().setAction(SyncService.START_SERVICE).putExtra(SyncService.USER_INITIATED, true)
            service.onStartCommand(initial, 0, 1)
            withTimeout(10000) { firstProbe.await() }
            val leave = if (manualJoinsLeave) scheduler.startUserInitiatedDatabaseSync() else scheduler.startLeave()
            assertEquals("a running owner needs no additional service start", 1, starts.get())
            val secondLeave = scheduler.startLeave()
            assertSame("multiple leaves join the same pending work", pending, secondLeave)
            if (!refuse) {
                service.onStartCommand(Intent().setAction(SyncService.ACTION_LEAVE), 0, 2)
                service.onStartCommand(Intent().setAction(SyncService.ACTION_LEAVE), 0, 3)
            }
            messages.clear()
            release.complete(Unit)
            withTimeout(10000) { service.scope.coroutineContext[Job]!!.children.toList().forEach { it.join() } }
            assertSame("refused wakeup joins running service", pending, leave)
            assertEquals("leave preparation runs only for the leave pass", 1, prepares.get())
            assertEquals("one original sync plus one follow-up", 2, running.count { it })
            assertTrue("both passes reach the real adapter", probes.get() >= 2)
            val errors = messages.filterIsInstance<net.bible.android.control.event.UserMessage.ErrorNotification>()
            assertTrue("follow-up must exercise the real transport", folderReads.get() >= 2)
            if (manualJoinsLeave) {
                assertTrue("manual follow-up transport failure must be reported in the foreground: $errors", errors.any { !it.background })
            } else {
                assertTrue("automatic leave transport failure must remain silent: $errors", errors.isEmpty())
            }
        } finally {
            release.complete(Unit)
            service?.let { kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                withTimeout(10000) { it.scope.coroutineContext[Job]!!.cancelAndJoin() }
                it.onDestroy()
            } }
            if (oldFolder == null) accessor.dao.removeConfig(SYNC_FOLDER_FILE_ID_KEY)
            else accessor.dao.setConfig(SYNC_FOLDER_FILE_ID_KEY, oldFolder)
            if (oldDeviceFolder == null) accessor.dao.removeConfig(SYNC_DEVICE_FOLDER_FILE_ID_KEY)
            else accessor.dao.setConfig(SYNC_DEVICE_FOLDER_FILE_ID_KEY, oldDeviceFolder)
            scheduler.complete(false)
            subscription.cancel()
            messageSubscription.cancel()
            schedulerSnapshot.restore()
            realScheduler.complete(false)
            portsSnapshot.restore()
            cloud.adapterId = oldAdapterId
            adapterField.set(cloud, oldAdapter)
            category.setSyncEnabled(settings, oldEnabled)
            cloud.resetSubscribersForTest()
        }
    }
}
