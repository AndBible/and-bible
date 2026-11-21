/*
 * Copyright (c) 2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
 *
 * This file is part of AndBible: Bible Study (http://github.com/AndBible/and-bible).
 *
 * AndBible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * AndBible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with AndBible.
 * If not, see http://www.gnu.org/licenses/.
 */
package net.bible.android.view.activity.settings

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Job
import net.bible.android.TestBibleApplication
import net.bible.service.cloudsync.SyncableDatabaseDefinition
import net.bible.service.cloudsync.documents.DocumentSyncSettings
import net.bible.service.common.CommonUtils
import net.bible.test.DatabaseResetter
import net.bible.test.awaitFixtureWrites
import kotlinx.coroutines.async
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.cancelAndJoin
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doAnswer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Verifies [SyncSettingsServiceImpl]'s per-key routing to the correct backing store. The
 * OAuth and remote scans are not exercised here. Store routing runs unconfined; the suspended
 * category read uses a dedicated serial UI dispatcher. Unconfined refreshes can resume on DAO
 * threads, so routing assertions await publication rather than assuming a setter cannot suspend.
 * All scopes are cancelled after each test, and document preference writes are drained before DB reset.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class)
class SyncSettingsServiceImplTest {
    /**
     * This test's own instance, on this test's [net.bible.sharedcore.platform.OrderedLauncher]. The Koin
     * singleton is built once per JVM and keeps the launcher of whichever test created it (the fixture swaps
     * the launcher per test and cancels the old one's scope), so joining this test's launcher did not wait for
     * its writes, and its already-loaded state could come from an earlier test's database.
     */
    private val settings: DocumentSyncSettings by lazy {
        fun <T : Any> get(type: Class<T>): T = org.koin.java.KoinJavaComponent.get(type)
        DocumentSyncSettings(
            { net.bible.service.db.DatabaseContainer.instance.documentSyncDb.documentSyncPreferencesDao() },
            { net.bible.service.db.DatabaseContainer.instance.documentSyncDb.cloudListingStateDao() },
            { net.bible.service.db.DatabaseContainer.instance.documentSyncDb.cloudDocumentSyncTimestampDao() },
            get(net.bible.sharedcore.platform.OrderedLauncher::class.java),
            get(net.bible.sharedcore.platform.AppEnvironment::class.java),
        )
    }
    private val scopes = mutableListOf<CoroutineScope>()
    private val schedulers = mutableListOf<kotlinx.coroutines.test.TestCoroutineScheduler>()
    @After fun tearDown() {
        scopes.forEach { it.cancel() }
        schedulers.forEach { it.runCurrent() }
        DatabaseResetter.resetDatabase(*scopes.toTypedArray())
    }

    private fun impl(
        scope: CoroutineScope = CoroutineScope(Job() + kotlinx.coroutines.Dispatchers.Unconfined).also { scopes.add(it) },
        documentSettings: DocumentSyncSettings = settings,
        categories: net.bible.service.cloudsync.SyncSettingsCategories = net.bible.service.cloudsync.SyncSettingsCategories(CommonUtils.settings),
        writeScope: CoroutineScope = scope,
        platform: net.bible.service.cloudsync.SyncSettingsPlatform = org.koin.java.KoinJavaComponent.get(net.bible.service.cloudsync.SyncSettingsPlatform::class.java),
        starter: net.bible.sharedcore.cloud.DocumentSyncStarter? = null,
        tracker: net.bible.service.cloudsync.documents.DocumentSyncProgressTracker? = null,
        permission: net.bible.sharedcore.platform.NotificationPermission? = null,
        running: kotlinx.coroutines.flow.StateFlow<Boolean>? = null,
        runningState: (() -> kotlinx.coroutines.flow.StateFlow<Boolean>)? = running?.let { state -> { state } },
        cloudSync: net.bible.service.cloudsync.CloudSync = org.koin.java.KoinJavaComponent.get(net.bible.service.cloudsync.CloudSync::class.java),
        adapters: net.bible.service.cloudsync.CloudAdapterFactory = org.koin.java.KoinJavaComponent.get(net.bible.service.cloudsync.CloudAdapterFactory::class.java),
        queuedJoins: (() -> kotlinx.coroutines.flow.StateFlow<Set<SyncableDatabaseDefinition>>)? = null,
        onRestored: () -> Unit = {},
    ): SyncSettingsServiceImpl {
        fun <T : Any> get(type: Class<T>): T = org.koin.java.KoinJavaComponent.get(type)
        val service = SyncSettingsServiceImpl(
            scope, writeScope, documentSettings, cloudSync,
            adapters, categories,
            get(net.bible.service.cloudsync.DocumentSyncProvider::class.java),
            starter ?: get(net.bible.sharedcore.cloud.DocumentSyncStarter::class.java),
            platform, net.bible.test.testCoreStrings(),
            get(net.bible.sharedcore.platform.AppEnvironment::class.java), onRestored,
            notificationPermission = permission, progressTracker = tracker, runningState = runningState,
            queuedJoinsState = queuedJoins,
        )
        kotlinx.coroutines.runBlocking { settings.ensureLoaded() }
        return service
    }

    /** Counts the passes CloudSync schedules; queued [gates] are handed to the first calls (a pass that runs until completed). */
    private class Passes {
        val calls = java.util.concurrent.atomic.AtomicInteger()
        val gates = java.util.concurrent.ConcurrentLinkedQueue<CompletableDeferred<Boolean>>()
        val scheduler = net.bible.sharedcore.platform.sync.SyncScheduler { calls.incrementAndGet(); gates.poll() ?: CompletableDeferred(true) }
    }

    /** An adapter that is signed in; [listing] decides what the cloud holds for a folder name (empty: the category needs creating). */
    private class TestAdapter(val listing: (String?) -> List<net.bible.sharedcore.platform.cloud.CloudFile> = { emptyList() }) : net.bible.service.cloudsync.CloudAdapter {
        /** When set, [signIn] waits for it (the sign-in then holds CloudSync's sign-in mutex). */
        @Volatile var signInGate: CompletableDeferred<Unit>? = null
        val signInEntered = CompletableDeferred<Unit>()
        @Volatile private var signed = false
        override val signedIn get() = signed
        override suspend fun signIn(): Boolean {
            signInGate?.let { signInEntered.complete(Unit); it.await() }
            signed = true
            return true
        }
        override suspend fun signOut() {}
        override suspend fun listFiles(parentsIds: List<String>?, name: String?, mimeType: String?, createdTimeAtLeast: Long?) = listing(name)
        override suspend fun createNewFolder(name: String, parentId: String?) = net.bible.sharedcore.platform.cloud.CloudFile("new-$name", name, 0L, 1L, parentId ?: "root")
        override suspend fun get(id: String): net.bible.sharedcore.platform.cloud.CloudFile = error("unused")
        override suspend fun getFolders(parentId: String) = emptyList<net.bible.sharedcore.platform.cloud.CloudFile>()
        override suspend fun download(id: String, sink: okio.Sink, onProgress: net.bible.sharedcore.platform.cloud.DownloadProgressListener?) = error("unused")
        override suspend fun upload(name: String, file: okio.Path, parentId: String) = net.bible.sharedcore.platform.cloud.CloudFile("up-$name", name, 1L, 1L, parentId)
        override suspend fun delete(id: String) {}
        override suspend fun isSyncFolderKnown(dbDef: net.bible.service.cloudsync.SyncableDatabaseAccessor<*>, name: String, id: String) = true
        override suspend fun makeSyncFolderKnown(dbDef: net.bible.service.cloudsync.SyncableDatabaseAccessor<*>, name: String, id: String) {}
        override suspend fun getConfigs(dbDef: net.bible.service.cloudsync.SyncableDatabaseAccessor<*>) = emptyList<net.bible.android.database.SyncConfiguration>()
    }

    /**
     * A real [net.bible.service.cloudsync.CloudSync] over a one-backend factory whose adapter is [adapter] (none: creating one
     * fails), on the real settings with every write recorded in [writes] (key names, in order).
     */
    private class CloudFixture(adapter: net.bible.service.cloudsync.CloudAdapter?, val passes: Passes = Passes()) {
        val writes = java.util.Collections.synchronizedList(mutableListOf<String>())
        val settings = object : net.bible.sharedcore.platform.AppSettings by CommonUtils.settings {
            override fun setString(key: String, value: String?) { writes += key; CommonUtils.settings.setString(key, value) }
            override fun setBoolean(key: String, value: Boolean?) { writes += key; CommonUtils.settings.setBoolean(key, value) }
        }
        val dialogs = net.bible.sharedcore.ui.dialog.AppDialogController()
        val factory = object : net.bible.service.cloudsync.CloudAdapterFactory {
            override val available = listOf("NEXT_CLOUD")
            override fun displayName(id: String) = id
            override fun create(id: String): net.bible.service.cloudsync.CloudAdapter =
                adapter ?: error("The cloud must not be touched")
        }
        val cloud = net.bible.service.cloudsync.CloudSync(
            dialogs, net.bible.test.testCoreStrings(),
            org.koin.java.KoinJavaComponent.get(net.bible.sharedcore.platform.AppEnvironment::class.java),
            settings, factory, passes.scheduler,
            org.koin.java.KoinJavaComponent.get(net.bible.sharedcore.cloud.DocumentSyncStarter::class.java),
            net.bible.service.cloudsync.DocumentSyncProvider { error("No document operations here") },
            org.koin.java.KoinJavaComponent.get(net.bible.service.cloudsync.documents.DocumentSyncSettings::class.java),
        )
        val categories = net.bible.service.cloudsync.SyncSettingsCategories(settings)
    }

    @Test fun applyWaitsForAnotherSignInInsteadOfFailingSilently() = runBlocking {
        val adapter = TestAdapter().apply { signInGate = CompletableDeferred() }
        val fixture = CloudFixture(adapter)
        val service = onService(fixture)
        val other = async(Dispatchers.Default) { fixture.cloud.signIn() }   // another caller holds the sign-in mutex
        withTimeout(5000) { adapter.signInEntered.await() }

        val applying = async(Dispatchers.Default) { service.applyCategoryChanges(mapOf("sync_enable_bookmarks" to true)) }
        kotlinx.coroutines.delay(300)
        assertFalse("Apply waits for the running sign-in", applying.isCompleted)
        adapter.signInGate!!.complete(Unit)

        assertEquals(true, withTimeout(10_000) { other.await() })
        assertEquals(net.bible.sharedcore.settings.ApplyResult.Applied, withTimeout(10_000) { applying.await() })
        assertTrue(fixture.categories.enabled(SyncableDatabaseDefinition.BOOKMARKS))
    }

    @Test fun offsDropAStoredJoinDecisionOfTheCategory() = runBlocking {
        val fixture = CloudFixture(TestAdapter())
        fixture.passes.gates.add(CompletableDeferred())            // a pass is running: the stored decision queues behind it
        fixture.cloud.start()
        val service = onService(fixture)
        service.applyCategoryChanges(mapOf("sync_enable_bookmarks" to true))
        assertEquals(setOf(SyncableDatabaseDefinition.BOOKMARKS), fixture.cloud.queuedJoins.value)

        service.applyCategoryChanges(mapOf("sync_enable_bookmarks" to false))

        assertTrue("a decision must not outlive the switch that turned the category off", fixture.cloud.queuedJoins.value.isEmpty())
    }

    @Test fun decisionsTheFollowUpPassDidNotExecuteDoNotLinger() = runBlocking {
        val fixture = CloudFixture(TestAdapter())                  // the counting scheduler never runs a real pass
        val restored = CompletableDeferred<Unit>()
        val service = onService(fixture, restored = { restored.complete(Unit) })
        service.applyCategoryChanges(mapOf("sync_enable_bookmarks" to true))
        withTimeout(10_000) { restored.await() }
        assertTrue("an unexecuted confirmed decision must not run in a later background pass", fixture.cloud.queuedJoins.value.isEmpty())
    }

    @Test fun applyCategoryChangesSignInFailurePersistsNothing() = runBlocking {
        val categories = net.bible.service.cloudsync.SyncSettingsCategories(CommonUtils.settings)
        categories.setEnabled(SyncableDatabaseDefinition.WORKSPACES, true)
        val adapter: net.bible.service.cloudsync.CloudAdapter = com.nhaarman.mockitokotlin2.mock {
            onBlocking { signIn() } doAnswer { throw net.bible.service.cloudsync.CloudSyncUserCancelledException() }
        }
        val fixture = CloudFixture(adapter)
        val service = impl(categories = categories, cloudSync = fixture.cloud, adapters = fixture.factory)
        var probed = false

        val result = service.applyCategoryChanges(
            mapOf("sync_enable_bookmarks" to true, "sync_enable_workspaces" to false)) { probed = true }

        assertEquals(net.bible.sharedcore.settings.ApplyResult.SignInFailed, result)
        assertFalse(probed)
        assertFalse("the on was not enabled", categories.enabled(SyncableDatabaseDefinition.BOOKMARKS))
        assertTrue("the off was not persisted", categories.enabled(SyncableDatabaseDefinition.WORKSPACES))
        assertEquals(0, fixture.passes.calls.get())
    }

    @Test fun applyCategoryChangesPersistsOffsWithoutProbe() = runBlocking {
        val categories = net.bible.service.cloudsync.SyncSettingsCategories(CommonUtils.settings)
        categories.setEnabled(SyncableDatabaseDefinition.WORKSPACES, true)
        categories.setEnabled(SyncableDatabaseDefinition.PROGRESS, true)
        val fixture = CloudFixture(null)   // any sign-in or probe would create an adapter and fail
        val service = impl(categories = categories, cloudSync = fixture.cloud, adapters = fixture.factory)
        var probed = false

        val result = service.applyCategoryChanges(
            mapOf("sync_enable_workspaces" to false, "sync_enable_progress" to false)) { probed = true }

        assertEquals(net.bible.sharedcore.settings.ApplyResult.Applied, result)
        assertFalse("offs need no cloud probe", probed)
        assertFalse(categories.enabled(SyncableDatabaseDefinition.WORKSPACES))
        assertFalse(categories.enabled(SyncableDatabaseDefinition.PROGRESS))
    }

    /** Runs [block] with the `Main` dispatcher unconfined (the join plan dialog is asked on Main) and an in-memory responder. */
    private fun <T> withMainAndResponder(fixture: CloudFixture, answer: (net.bible.sharedcore.ui.dialog.AppDialogRequest) -> net.bible.sharedcore.ui.dialog.AppDialogResult, block: suspend () -> T): T {
        Dispatchers.setMain(Dispatchers.Unconfined)
        val responder = CoroutineScope(SupervisorJob() + Dispatchers.Default).also { scopes.add(it) }
        responder.launch {
            fixture.dialogs.pending.collect { shown -> if (shown != null) fixture.dialogs.respond(shown.id, answer(shown.request)) }
        }
        try { return runBlocking { block() } } finally { responder.cancel(); Dispatchers.resetMain() }
    }

    private fun onService(fixture: CloudFixture, restored: () -> Unit = {}, permission: net.bible.sharedcore.platform.NotificationPermission? = null) =
        impl(categories = fixture.categories, cloudSync = fixture.cloud, adapters = fixture.factory, onRestored = restored, permission = permission)

    @Test fun applyCategoryChangesPinsThenEnablesPlansAndRunsOneFreshPass() = runBlocking {
        val fixture = CloudFixture(TestAdapter())          // nothing in the cloud: NeedsCreate, no question
        CommonUtils.settings.removeString("sync_adapter")
        val restored = CompletableDeferred<Unit>()
        val service = onService(fixture, restored = { restored.complete(Unit) })
        var probed = false

        val result = service.applyCategoryChanges(mapOf("sync_enable_bookmarks" to true)) { probed = true }

        assertEquals(net.bible.sharedcore.settings.ApplyResult.Applied, result)
        assertTrue("onProbed is forwarded to planJoins", probed)
        assertTrue(fixture.categories.enabled(SyncableDatabaseDefinition.BOOKMARKS))
        assertTrue("the adapter is pinned before the category is enabled",
            fixture.writes.indexOf("sync_adapter") in 0 until fixture.writes.indexOf("sync_enable_bookmarks"))
        withTimeout(10_000) { restored.await() }
        assertEquals("exactly one fresh pass runs the stored decision", 1, fixture.passes.calls.get())
    }

    @Test fun applyCategoryChangesWaitsForARunningPassBeforeStartingTheFreshOne() = runBlocking {
        val fixture = CloudFixture(TestAdapter())
        val running = CompletableDeferred<Boolean>().also { fixture.passes.gates.add(it) }
        assertFalse(fixture.cloud.start().isCompleted)      // a pass is running (call 1)
        val restored = CompletableDeferred<Unit>()
        val service = onService(fixture, restored = { restored.complete(Unit) })

        val result = service.applyCategoryChanges(mapOf("sync_enable_bookmarks" to true))

        assertEquals(net.bible.sharedcore.settings.ApplyResult.Applied, result)
        assertEquals("the stored decision waits: no second pass while one runs", 1, fixture.passes.calls.get())
        assertFalse(restored.isCompleted)
        running.complete(true)
        withTimeout(10_000) { restored.await() }
        assertEquals("a fresh pass starts after the running one", 2, fixture.passes.calls.get())
    }

    @Test fun cancelledJoinPlanTurnsTheOnsOffAndStartsNoPass() {
        val named = { name: String? -> if (name != null) listOf(net.bible.sharedcore.platform.cloud.CloudFile("cloud-$name", name, 0L, 1L, "root")) else emptyList() }
        val fixture = CloudFixture(TestAdapter(named))      // the cloud already has data: the plan dialog asks
        val restored = CompletableDeferred<Unit>()
        val service = onService(fixture, restored = { restored.complete(Unit) })
        val result = withMainAndResponder(fixture, { net.bible.sharedcore.ui.dialog.AppDialogResult.Cancel }) {
            service.applyCategoryChanges(mapOf("sync_enable_bookmarks" to true))
        }
        assertEquals(net.bible.sharedcore.settings.ApplyResult.Cancelled, result)
        assertFalse("planJoins turned the category off again", fixture.categories.enabled(SyncableDatabaseDefinition.BOOKMARKS))
        assertEquals(0, fixture.passes.calls.get())
        assertFalse(restored.isCompleted)
    }

    @Test fun applyingAnOnAsksForNotificationPermissionOnceAndNeverBlocksOrFails() = runBlocking {
        var asks = 0
        val fixture = CloudFixture(TestAdapter())
        val service = onService(fixture, permission = net.bible.sharedcore.platform.NotificationPermission { asks++ })
        assertEquals(0, asks)
        service.applyCategoryChanges(mapOf("sync_enable_bookmarks" to true))
        assertEquals(1, asks)

        val pending = CompletableDeferred<Unit>()
        val blocked = onService(fixture, permission = net.bible.sharedcore.platform.NotificationPermission { pending.await() })
        assertEquals(net.bible.sharedcore.settings.ApplyResult.Applied, withTimeout(5000) { blocked.applyCategoryChanges(mapOf("sync_enable_workspaces" to true)) })
        pending.complete(Unit)

        val failing = onService(fixture, permission = net.bible.sharedcore.platform.NotificationPermission { error("permission failed") })
        assertEquals(net.bible.sharedcore.settings.ApplyResult.Applied, failing.applyCategoryChanges(mapOf("sync_enable_readingplans" to true)))
        Unit
    }

    @Test fun applyingOnlyOffsNeverAsksForNotificationPermission() = runBlocking {
        var asks = 0
        val fixture = CloudFixture(null)
        fixture.categories.setEnabled(SyncableDatabaseDefinition.PROGRESS, true)
        val service = onService(fixture, permission = net.bible.sharedcore.platform.NotificationPermission { asks++ })
        service.applyCategoryChanges(mapOf("sync_enable_progress" to false))
        assertEquals(0, asks)
    }

    @Test fun queuedCategoryShowsWaitingSummary() = runBlocking {
        val queued = kotlinx.coroutines.flow.MutableStateFlow<Set<SyncableDatabaseDefinition>>(emptySet())
        val categories = net.bible.service.cloudsync.SyncSettingsCategories(CommonUtils.settings)
        val service = impl(categories = categories, queuedJoins = { queued })
        val waiting = net.bible.test.testCoreStrings().syncWaitingForPass
        withTimeout(5000) { service.snapshot.first { it.categorySummary.isNotEmpty() } }
        assertFalse(service.snapshot.value.categorySummary.getValue("sync_enable_bookmarks") == waiting)

        queued.value = setOf(SyncableDatabaseDefinition.BOOKMARKS)
        kotlinx.coroutines.delay(300)
        assertFalse("a disabled category shows no waiting text", service.snapshot.value.categorySummary.getValue("sync_enable_bookmarks") == waiting)

        categories.setEnabled(SyncableDatabaseDefinition.BOOKMARKS, true)
        service.refresh()
        withTimeout(5000) { service.snapshot.first { it.categorySummary.getValue("sync_enable_bookmarks") == waiting } }
        assertFalse("other categories keep their summary", service.snapshot.value.categorySummary.getValue("sync_enable_progress") == waiting)

        queued.value = emptySet()
        withTimeout(5000) { service.snapshot.first { it.categorySummary.getValue("sync_enable_bookmarks") != waiting } }
        Unit
    }

    @Test fun manualTransferWhileDisabledIsVisibleFromExistingRunningState() = kotlinx.coroutines.runBlocking {
        val running = kotlinx.coroutines.flow.MutableStateFlow(false)
        val service = impl(running = running)
        assertFalse(service.snapshot.value.documentsEnabled)
        running.value = true
        assertFalse(service.snapshot.value.documentsEnabled)
        assertEquals("Document transfer running", service.snapshot.value.documentTransferSummary)
        running.value = false
        assertNull(service.snapshot.value.documentTransferSummary)
    }

    @Test fun deniedPermissionDoesNotPreventEnablingDocuments() = kotlinx.coroutines.runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined).also { scopes.add(it) }
        var permissionRequests = 0
        var starts = 0
        val starter = object : net.bible.sharedcore.cloud.DocumentSyncStarter {
            override fun pushDocuments(initials: List<String>) = Unit
            override fun start(push: List<String>, download: List<String>, remove: List<String>, purge: List<String>, uninstall: List<String>) { starts++ }
            override fun stop() = Unit
        }
        val service = impl(scope, starter = starter,
            permission = net.bible.sharedcore.platform.NotificationPermission { permissionRequests++; throw IllegalStateException("denied") })
        service.setDocumentsEnabled(net.bible.sharedcore.settings.DocSyncSummaryData(emptyList(), listOf("KJV"), 0, 100))
        withTimeout(5000) { service.snapshot.first { it.documentsEnabled } }
        assertTrue(settings.enabled)
        assertTrue(service.snapshot.value.documentsEnabled)
        assertEquals(1, permissionRequests)
        assertEquals(1, starts)
    }

    @Test fun loadedDocumentMutationPublishesWhileCategoryReadIsSuspended() = runBlocking {
        settings.ensureLoaded()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val dao = object : net.bible.android.database.SyncDao by com.nhaarman.mockitokotlin2.mock() {
            override suspend fun getLong(keyName: String): Long? {
                assertEquals(net.bible.service.cloudsync.LAST_SYNCHRONIZED_KEY, keyName)
                entered.complete(Unit)
                release.await()
                return null
            }
        }
        val categorySettings = object : net.bible.sharedcore.platform.AppSettings by CommonUtils.settings {
            override fun getBoolean(key: String, default: Boolean) = key == "sync_enable_bookmarks"
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined).also { scopes.add(it) }
        try {
            val service = impl(scope,
                categories = net.bible.service.cloudsync.SyncSettingsCategories(categorySettings) { dao },
                starter = com.nhaarman.mockitokotlin2.mock())
            withTimeout(5000) { entered.await() }
            service.setDocumentsEnabled(net.bible.sharedcore.settings.DocSyncSummaryData(emptyList(), emptyList(), 0, 0))
            assertTrue(settings.enabled)
            assertTrue("loaded enable must publish without waiting for unrelated category metadata",
                service.snapshot.value.documentsEnabled)
            assertTrue(service.snapshot.value.autoTogglesVisible)
            assertTrue(service.snapshot.value.wifiOnlyVisible)
            service.setDocumentSyncToggle("sync_documents_auto_download", false)
            assertFalse(service.snapshot.value.autoDownload)
            service.setDocumentSyncToggle("sync_documents_auto_upload", false)
            assertFalse(service.snapshot.value.autoUpload)
            service.setDocumentSyncToggle("sync_documents_auto_delete", false)
            assertFalse(service.snapshot.value.autoDelete)
            service.setDocumentSyncToggle("sync_documents_wifi_only", false)
            assertFalse(service.snapshot.value.wifiOnly)
            service.disableDocuments()
            assertFalse(service.snapshot.value.documentsEnabled)
            assertFalse(service.snapshot.value.autoTogglesVisible)
            assertFalse(service.snapshot.value.wifiOnlyVisible)
        } finally {
            scope.cancel()
            release.complete(Unit)
            withTimeout(5000) { scope.coroutineContext[Job]!!.join() }
        }
    }

    @Test fun olderCategoryRefreshCannotOverwriteNewerDocumentMutation() = runBlocking {
        settings.ensureLoaded()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var reads = 0
        val dao = object : net.bible.android.database.SyncDao by com.nhaarman.mockitokotlin2.mock() {
            override suspend fun getLong(keyName: String): Long? {
                assertEquals(net.bible.service.cloudsync.LAST_SYNCHRONIZED_KEY, keyName)
                if (++reads == 1) {
                    entered.complete(Unit)
                    release.await()
                }
                return null
            }
        }
        val categorySettings = object : net.bible.sharedcore.platform.AppSettings by CommonUtils.settings {
            override fun getBoolean(key: String, default: Boolean) = key == "sync_enable_bookmarks"
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined).also { scopes.add(it) }
        try {
            val service = impl(scope,
                categories = net.bible.service.cloudsync.SyncSettingsCategories(categorySettings) { dao },
                starter = com.nhaarman.mockitokotlin2.mock())
            withTimeout(5000) { entered.await() }
            val olderRefreshes = scope.coroutineContext[Job]!!.children.toList()
            service.setDocumentsEnabled(net.bible.sharedcore.settings.DocSyncSummaryData(emptyList(), emptyList(), 0, 0))
            assertTrue("newer refresh publishes the enabled state", service.snapshot.value.documentsEnabled)
            release.complete(Unit)
            withTimeout(5000) { olderRefreshes.forEach { it.join() } }
            assertTrue(settings.enabled)
            assertTrue("an older suspended refresh must not restore the pre-enable state",
                service.snapshot.value.documentsEnabled)
            assertTrue(service.snapshot.value.autoTogglesVisible)
            assertTrue(service.snapshot.value.wifiOnlyVisible)
        } finally {
            scope.cancel()
            release.complete(Unit)
            withTimeout(5000) { scope.coroutineContext[Job]!!.join() }
        }
    }

    @Test fun controllerToggleRefreshSeesLoadedMutationBeforeWorkerRuns() = kotlinx.coroutines.runBlocking {
        settings.ensureLoaded()
        settings.enabled = true
        val ui = CoroutineScope(Job() + kotlinx.coroutines.Dispatchers.Unconfined).also { scopes.add(it) }
        val worker = kotlinx.coroutines.test.StandardTestDispatcher().also { schedulers.add(it.scheduler) }
        val writes = CoroutineScope(Job() + worker).also { scopes.add(it) }
        val service = impl(ui, writeScope = writes)
        val controller = net.bible.sharedcore.settings.SyncSettingsController(service, ui,
            net.bible.sharedcore.settings.SyncSettingsLabels.forTest(), {})
        controller.onSwitch("sync_documents_auto_download", false)
        assertFalse("loaded toggle must mutate before the controller's refresh", settings.autoDownload)
        val row = controller.state.value.screen.items.filterIsInstance<net.bible.sharedcore.settings.SettingsItem.SwitchRow>()
            .first { it.key == "sync_documents_auto_download" }
        assertFalse("controller must not retain the old checked value", row.checked)
        controller.onSwitch("sync_enable_documents", false)
        assertFalse(service.snapshot.value.documentsEnabled)
        worker.scheduler.runCurrent()
        assertFalse(settings.enabled)
    }

    @Test fun coldTogglesWaitForSnapshotThenPublishInSubmissionOrder() = kotlinx.coroutines.runBlocking {
        val db = net.bible.service.db.DatabaseContainer.instance.documentSyncDb
        val entered = kotlinx.coroutines.CompletableDeferred<Unit>()
        val release = kotlinx.coroutines.CompletableDeferred<Unit>()
        val prefsDao = object : net.bible.android.database.DocumentSyncPreferencesDao by db.documentSyncPreferencesDao() {
            override suspend fun get(): net.bible.android.database.DocumentSyncPreferences? {
                entered.complete(Unit)
                release.await()
                return net.bible.android.database.DocumentSyncPreferences(autoDownload = true, wifiOnly = false)
            }
        }
        val ui = CoroutineScope(Job() + kotlinx.coroutines.Dispatchers.Unconfined).also { scopes.add(it) }
        val worker = kotlinx.coroutines.test.StandardTestDispatcher().also { schedulers.add(it.scheduler) }
        val writes = CoroutineScope(Job() + worker).also { scopes.add(it) }
        val launcher = net.bible.sharedcore.platform.OrderedLauncher(ui)
        val fresh = DocumentSyncSettings({ prefsDao }, { db.cloudListingStateDao() },
            { db.cloudDocumentSyncTimestampDao() }, launcher,
            org.koin.java.KoinJavaComponent.get(net.bible.sharedcore.platform.AppEnvironment::class.java))
        val service = impl(ui, fresh, writeScope = writes)
        entered.await()
        service.setDocumentSyncToggle("sync_documents_auto_download", false)
        service.setDocumentSyncToggle("sync_documents_auto_download", true)
        service.setDocumentSyncToggle("sync_documents_auto_download", false)
        release.complete(Unit)
        worker.scheduler.advanceUntilIdle()
        launcher.join("document-sync-settings")
        assertFalse(fresh.autoDownload)
        assertFalse("cold load must retain unrelated persisted preference", fresh.wifiOnly)
        assertFalse("mutation must publish after loading even without controller refresh", service.snapshot.value.autoDownload)
        assertFalse(db.documentSyncPreferencesDao().get()!!.autoDownload)
    }

    @Test fun persistenceBarrierWaitsForTheSettingsOwnedCurrentWrite() = runBlocking {
        val db = net.bible.service.db.DatabaseContainer.instance.documentSyncDb
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val owner = CoroutineScope(SupervisorJob() + Dispatchers.Default).also {
            scopes.add(it)
            net.bible.test.ReadingFixtureOwners.retain(listOf(it))
        }
        val launcher = net.bible.sharedcore.platform.OrderedLauncher(owner)
        val dao = object : net.bible.android.database.DocumentSyncPreferencesDao by db.documentSyncPreferencesDao() {
            override suspend fun set(prefs: net.bible.android.database.DocumentSyncPreferences) {
                entered.complete(Unit)
                release.await()
                db.documentSyncPreferencesDao().set(prefs)
            }
        }
        val fresh = DocumentSyncSettings({ dao }, { db.cloudListingStateDao() },
            { db.cloudDocumentSyncTimestampDao() }, launcher,
            org.koin.java.KoinJavaComponent.get(net.bible.sharedcore.platform.AppEnvironment::class.java))
        var primary: Throwable? = null
        val barrierScope = CoroutineScope(SupervisorJob() + Dispatchers.Default).also {
            scopes.add(it)
            net.bible.test.ReadingFixtureOwners.retain(listOf(it))
        }
        var barrier: kotlinx.coroutines.Deferred<Unit>? = null
        try {
            fresh.ensureLoaded()
            fresh.wifiOnly = false
            assertFalse("snapshot publication precedes persistence", fresh.wifiOnly)
            withTimeout(5000) { entered.await() }
            barrier = barrierScope.async(start = CoroutineStart.UNDISPATCHED) { fresh.awaitFixtureWrites() }
            // UNDISPATCHED reaches the actual join before returning; the DAO is still held.
            assertFalse("foreign queue cannot establish current-write persistence", barrier.isCompleted)
            release.complete(Unit)
            withTimeout(5000) { barrier.await() }
            assertFalse(db.documentSyncPreferencesDao().get()!!.wifiOnly)
        } catch (failure: Throwable) {
            primary = failure
            throw failure
        } finally {
            release.complete(Unit)
            net.bible.test.finishFixtureScopes(listOf(owner, barrierScope), primary)
        }
    }

    @Test fun heldRealWriteCleanupRetainsItsOwnerAndStillDisposesBarrierScope() {
        val db = net.bible.service.db.DatabaseContainer.instance.documentSyncDb
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val owner = CoroutineScope(SupervisorJob() + Dispatchers.Default).also { scopes.add(it) }
        val barrierOwner = CoroutineScope(SupervisorJob() + Dispatchers.Default).also { scopes.add(it) }
        val launcher = net.bible.sharedcore.platform.OrderedLauncher(owner)
        val dao = object : net.bible.android.database.DocumentSyncPreferencesDao by db.documentSyncPreferencesDao() {
            override suspend fun set(prefs: net.bible.android.database.DocumentSyncPreferences) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                    entered.complete(Unit)
                    release.await()
                    db.documentSyncPreferencesDao().set(prefs)
                }
            }
        }
        val fresh = DocumentSyncSettings({ dao }, { db.cloudListingStateDao() },
            { db.cloudDocumentSyncTimestampDao() }, launcher,
            org.koin.java.KoinJavaComponent.get(net.bible.sharedcore.platform.AppEnvironment::class.java))
        val primary = AssertionError("primary assertion survives slow DAO cleanup")
        try {
            runBlocking { fresh.ensureLoaded() }
            fresh.wifiOnly = false
            runBlocking { withTimeout(5000) { entered.await() } }
            val barrier = barrierOwner.async(start = CoroutineStart.UNDISPATCHED) { fresh.awaitFixtureWrites() }
            net.bible.test.finishFixtureScopes(listOf(owner, barrierOwner), primary, timeoutMs = 50)
            assertTrue(primary.suppressed.isNotEmpty())
            assertFalse("real held DAO owner remains retained", owner.coroutineContext[Job]!!.isCompleted)
            assertTrue("second scope disposed after first timeout", barrierOwner.coroutineContext[Job]!!.isCompleted)
            assertTrue(barrier.isCancelled)
            org.junit.Assert.assertThrows(IllegalStateException::class.java) { net.bible.test.ReadingFixtureOwners.requireEmpty() }
            org.junit.Assert.assertSame("no teardown under held writer", db,
                net.bible.service.db.DatabaseContainer.instance.documentSyncDb)
            release.complete(Unit)
            net.bible.test.finishFixtureScopes(listOf(owner, barrierOwner))
            DatabaseResetter.runResetBlocking { net.bible.test.ReadingFixtureOwners.drain() }
            assertFalse(runBlocking { db.documentSyncPreferencesDao().get()!! }.wifiOnly)
        } finally {
            release.complete(Unit)
            // Independent safety cleanup keeps omission experiments from leaking either scope.
            owner.coroutineContext[Job]!!.cancel()
            barrierOwner.coroutineContext[Job]!!.cancel()
            net.bible.test.finishFixtureScopes(listOf(owner, barrierOwner))
            DatabaseResetter.runResetBlocking { net.bible.test.ReadingFixtureOwners.drain() }
        }
    }

    @Test fun rapidLoadedTogglesKeepSubmissionOrderWithDelayedWorker() = kotlinx.coroutines.runBlocking {
        settings.ensureLoaded()
        val ui = CoroutineScope(Job() + kotlinx.coroutines.Dispatchers.Unconfined).also { scopes.add(it) }
        val worker = kotlinx.coroutines.test.StandardTestDispatcher().also { schedulers.add(it.scheduler) }
        val writes = CoroutineScope(Job() + worker).also { scopes.add(it) }
        val service = impl(ui, writeScope = writes)
        service.setDocumentSyncToggle("sync_documents_wifi_only", false)
        service.setDocumentSyncToggle("sync_documents_wifi_only", true)
        service.setDocumentSyncToggle("sync_documents_wifi_only", false)
        assertFalse("last submitted write is visible without scheduling worker", settings.wifiOnly)
        worker.scheduler.runCurrent()
        settings.awaitFixtureWrites()
        assertFalse(net.bible.service.db.DatabaseContainer.instance.documentSyncDb.documentSyncPreferencesDao().get()!!.wifiOnly)
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test fun suspendedCategoryDaoLeavesMainScopeResponsive() {
        val executor = java.util.concurrent.Executors.newSingleThreadExecutor()
        val dispatcher = executor.asCoroutineDispatcher()
        kotlinx.coroutines.Dispatchers.setMain(dispatcher)
        val scope = CoroutineScope(Job() + kotlinx.coroutines.Dispatchers.Main).also { scopes.add(it) }
        val entered = java.util.concurrent.CountDownLatch(1)
        val release = kotlinx.coroutines.CompletableDeferred<Unit>()
        val dao = object : net.bible.android.database.SyncDao by com.nhaarman.mockitokotlin2.mock() {
            override suspend fun getLong(keyName: String): Long? {
                assertEquals(net.bible.service.cloudsync.LAST_SYNCHRONIZED_KEY, keyName)
                entered.countDown()
                release.await()
                return 1234L
            }
        }
        CommonUtils.settings.setBoolean("sync_enable_bookmarks", true)
        try {
            val service = impl(scope, categories = net.bible.service.cloudsync.SyncSettingsCategories(CommonUtils.settings) { dao })
            assertTrue("refresh reached the category DAO", entered.await(5, java.util.concurrent.TimeUnit.SECONDS))
            val mainResponsive = java.util.concurrent.CountDownLatch(1)
            scope.launch { mainResponsive.countDown() }
            assertTrue("a suspended category read must not block the UI dispatcher", mainResponsive.await(2, java.util.concurrent.TimeUnit.SECONDS))
            assertTrue(service.snapshot.value.categorySummary.isEmpty())
            release.complete(Unit)
            kotlinx.coroutines.runBlocking {
                val loaded = kotlinx.coroutines.withTimeout(5000) { service.snapshot.first { it.categorySummary.isNotEmpty() } }
                assertEquals(
                    net.bible.test.testCoreStrings().syncCategoryContents("BOOKMARKS") + ".\n\n" +
                        net.bible.test.testCoreStrings().syncLastUpdated(net.bible.android.platform.AndroidSyncSettingsPlatform(
                            androidx.test.core.app.ApplicationProvider.getApplicationContext()).formatSyncTime(1234L)),
                    loaded.categorySummary.getValue("sync_enable_bookmarks"),
                )
            }
        } finally {
            release.complete(Unit)
            scope.cancel()
            kotlinx.coroutines.Dispatchers.resetMain()
            dispatcher.close()
            executor.shutdownNow()
        }
    }

    @Test fun initialRefreshLoadsPersistedDocumentSyncBeforeBuild() = kotlinx.coroutines.runBlocking {
        val db = net.bible.service.db.DatabaseContainer.instance.documentSyncDb
        db.documentSyncPreferencesDao().set(net.bible.android.database.DocumentSyncPreferences(enabled = true, wifiOnly = false))
        val scope = CoroutineScope(Job() + kotlinx.coroutines.Dispatchers.Unconfined).also { scopes.add(it) }
        val launcher = net.bible.sharedcore.platform.OrderedLauncher(scope)
        val fresh = DocumentSyncSettings({ db.documentSyncPreferencesDao() }, { db.cloudListingStateDao() },
            { db.cloudDocumentSyncTimestampDao() }, launcher,
            org.koin.java.KoinJavaComponent.get<net.bible.sharedcore.platform.AppEnvironment>(net.bible.sharedcore.platform.AppEnvironment::class.java))
        val service = impl(scope, documentSettings = fresh)
        val snapshot = kotlinx.coroutines.withTimeout(10_000) { service.snapshot.first { it.documentsEnabled } }
        assertTrue(snapshot.autoTogglesVisible)
        assertFalse(snapshot.wifiOnly)
        assertTrue(fresh.enabled)
    }

    @Test fun categoryDescriptionsRetainResourceText() {
        val strings = net.bible.test.testCoreStrings()
        val service = impl()
        val snapshot = kotlinx.coroutines.runBlocking {
            kotlinx.coroutines.withTimeout(5000) { service.snapshot.first { it.categorySummary.isNotEmpty() } }
        }
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val ids = mapOf(
            "BOOKMARKS" to net.bible.android.activity.R.string.bookmarks_contents,
            "WORKSPACES" to net.bible.android.activity.R.string.workspaces_contents,
            "READINGPLANS" to net.bible.android.activity.R.string.reading_plans_content,
            "MYDOCUMENTS" to net.bible.android.activity.R.string.my_documents_contents,
            "AI_SETTINGS" to net.bible.android.activity.R.string.ai_settings_sync_contents,
            "PROGRESS" to net.bible.android.activity.R.string.progress_sync_contents,
        )
        SyncableDatabaseDefinition.entries.forEach { category ->
            assertEquals(context.getString(ids.getValue(category.name)), strings.syncCategoryContents(category.name))
            snapshot.categorySummary["sync_enable_${category.name.lowercase()}"]?.let {
                assertEquals(strings.syncCategoryContents(category.name), it)
            }
        }
    }

    @Test(expected = IllegalArgumentException::class) fun unknownCategoryRejected() {
        net.bible.test.testCoreStrings().syncCategoryContents("unknown")
    }

    @Test fun serverUrl_valid_persistsToRealShared() {
        val service = impl()
        assertTrue(service.setText("cloud_sync_server_url", "https://example.com"))
        assertEquals("https://example.com", CommonUtils.realSharedPreferences.getString("cloud_sync_server_url", null))
    }

    @Test fun serverUrls_areNormalizedBeforeStorageForBothBackends() {
        val service = impl()
        for ((adapter, key) in listOf("NEXT_CLOUD" to "cloud_sync_server_url", "WEBDAV" to "webdav_sync_server_url")) {
            service.setAdapter(adapter)
            for ((input, expected) in listOf(
                "  https://example.com///\n" to "https://example.com",
                "https://example.com/nextcloud/" to "https://example.com/nextcloud",
                "https://example.com/remote.php/dav/files/me/" to "https://example.com/remote.php/dav/files/me",
                "https://example.com/already-normalized" to "https://example.com/already-normalized",
            )) {
                assertTrue(service.setText("cloud_sync_server_url", input))
                assertEquals("$adapter: $input", expected, CommonUtils.realSharedPreferences.getString(key, null))
            }
        }
    }

    @Test fun serverUrl_invalid_returnsFalse_notPersisted() {
        val service = impl()
        assertFalse(service.setText("cloud_sync_server_url", "not a url"))
        assertNull(CommonUtils.realSharedPreferences.getString("cloud_sync_server_url", null))
    }

    @Test fun httpNextcloudRefusedEverywhere_localHostsToo() {
        CommonUtils.settings.setString("sync_adapter", "NEXT_CLOUD")
        val service = impl()
        listOf("http://example.org/nc", "  http://NAS.local/nc  ", "http://127.0.0.1:8080", "HTTP://192.168.1.5").forEach {
            assertFalse(it, service.setText("cloud_sync_server_url", it))
            assertNull(it, CommonUtils.realSharedPreferences.getString("cloud_sync_server_url", null))
        }
    }

    @Test fun httpsNextcloudAccepted() {
        CommonUtils.settings.setString("sync_adapter", "NEXT_CLOUD")
        assertTrue(impl().setText("cloud_sync_server_url", "  https://NAS.local/nc/  "))
        assertEquals("https://NAS.local/nc", CommonUtils.realSharedPreferences.getString("cloud_sync_server_url", null))
    }

    @Test fun platformRefusalReachesControllerWithoutMislabelingMalformedHttp() {
        CommonUtils.settings.setString("sync_adapter", "NEXT_CLOUD")
        val scope = CoroutineScope(Job() + Dispatchers.Unconfined).also { scopes.add(it) }
        val service = impl(scope)
        val labels = net.bible.sharedcore.settings.SyncSettingsLabels.forTest()
        val controller = net.bible.sharedcore.settings.SyncSettingsController(service, scope, labels, {})
        controller.onTextInput("cloud_sync_server_url", "  HTTP://example.org/nc  ")
        assertEquals(labels.httpsRequiredMessage, (controller.state.value.dialog as net.bible.sharedcore.settings.SyncDialog.UrlError).message)
        controller.onTextInput("cloud_sync_server_url", "http://bad host")
        assertEquals(labels.invalidUrlMessage, (controller.state.value.dialog as net.bible.sharedcore.settings.SyncDialog.UrlError).message)
        controller.onTextInput("cloud_sync_server_url", "not a url")
        assertEquals(labels.invalidUrlMessage, (controller.state.value.dialog as net.bible.sharedcore.settings.SyncDialog.UrlError).message)
        assertNull(CommonUtils.realSharedPreferences.getString("cloud_sync_server_url", null))
    }

    @Test fun androidMalformedHttpKeepsInvalidUrlMessage() {
        CommonUtils.settings.setString("sync_adapter", "NEXT_CLOUD")
        val scope = CoroutineScope(Job() + Dispatchers.Unconfined).also { scopes.add(it) }
        val controller = net.bible.sharedcore.settings.SyncSettingsController(impl(scope), scope,
            net.bible.sharedcore.settings.SyncSettingsLabels.forTest(), {})
        controller.onTextInput("cloud_sync_server_url", "http://bad host")
        assertEquals(net.bible.sharedcore.settings.SyncSettingsLabels.forTest().invalidUrlMessage,
            (controller.state.value.dialog as net.bible.sharedcore.settings.SyncDialog.UrlError).message)
    }

    @Test fun username_persistsToRealShared() {
        val service = impl()
        assertTrue(service.setText("cloud_sync_username", "alice"))
        assertEquals("alice", CommonUtils.realSharedPreferences.getString("cloud_sync_username", null))
    }

    @Test fun documentToggle_persistsToDocumentSyncSettings() = runBlocking {
        val service = impl()
        service.setDocumentSyncToggle("sync_documents_auto_download", false)
        withTimeout(5000) { service.snapshot.first { !it.autoDownload } }
        assertFalse(settings.autoDownload)
        service.setDocumentSyncToggle("sync_documents_wifi_only", false)
        withTimeout(5000) { service.snapshot.first { !it.wifiOnly } }
        assertFalse(settings.wifiOnly)
        settings.awaitFixtureWrites()
        val persisted = net.bible.service.db.DatabaseContainer.instance.documentSyncDb.documentSyncPreferencesDao().get()!!
        assertFalse(persisted.autoDownload)
        assertFalse(persisted.wifiOnly)
    }

    @Test fun documentToggle_waitsForInFlightRefreshPublication() = runBlocking {
        settings.ensureLoaded()
        val categoryRead = CompletableDeferred<Unit>()
        val publish = CompletableDeferred<Unit>()
        val publicationEntered = java.util.concurrent.CountDownLatch(1)
        val finishPublication = java.util.concurrent.CountDownLatch(1)
        val dao = object : net.bible.android.database.SyncDao by com.nhaarman.mockitokotlin2.mock() {
            override suspend fun getLong(keyName: String): Long? {
                categoryRead.complete(Unit)
                publish.await()
                return null
            }
        }
        val categorySettings = object : net.bible.sharedcore.platform.AppSettings by CommonUtils.settings {
            override fun getBoolean(key: String, default: Boolean) = key == "sync_enable_bookmarks"
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined).also { scopes.add(it) }
        val publisher = CoroutineScope(SupervisorJob() + Dispatchers.Default).also { scopes.add(it) }
        var reads = 0
        val running = kotlinx.coroutines.flow.MutableStateFlow(false)
        val service = impl(scope,
            categories = net.bible.service.cloudsync.SyncSettingsCategories(categorySettings) { dao },
            runningState = {
                // The observer reads twice, then build() reads once. The fourth read
                // projects document fields while publishRefresh holds its mutation lock.
                if (++reads == 4) {
                    publicationEntered.countDown()
                    check(finishPublication.await(5, java.util.concurrent.TimeUnit.SECONDS))
                }
                running
            })
        var publication: Job? = null
        try {
            withTimeout(5000) { categoryRead.await() }
            // Complete the DAO read on a worker, where the unconfined refresh resumes.
            publication = publisher.launch { publish.complete(Unit) }
            assertTrue("initial refresh reached publication", publicationEntered.await(5, java.util.concurrent.TimeUnit.SECONDS))
            service.setDocumentSyncToggle("sync_documents_auto_download", false)
            assertTrue("toggle is queued behind the in-flight refresh, not lost", settings.autoDownload)
            finishPublication.countDown()
            withTimeout(5000) { service.snapshot.first { !it.autoDownload } }
            assertFalse(settings.autoDownload)
            settings.awaitFixtureWrites()
            assertFalse(net.bible.service.db.DatabaseContainer.instance.documentSyncDb.documentSyncPreferencesDao().get()!!.autoDownload)
        } finally {
            finishPublication.countDown()
            publish.complete(Unit)
            publication?.join()
        }
    }

    @Test fun categoryEnabled_persistsToSyncableDatabaseDefinition() = runBlocking {
        val fixture = CloudFixture(TestAdapter())
        val service = onService(fixture)
        service.applyCategoryChanges(mapOf("sync_enable_bookmarks" to true))
        assertTrue(SyncableDatabaseDefinition.BOOKMARKS.isSyncEnabled(CommonUtils.settings))
        assertTrue(CommonUtils.settings.getBoolean("sync_enable_bookmarks", false))
        service.applyCategoryChanges(mapOf("sync_enable_bookmarks" to false))
        assertFalse(CommonUtils.settings.getBoolean("sync_enable_bookmarks", true))
    }

    // #3902: the reading-plan category must reach the screen and its toggle must enable that category's sync.
    @Test fun readingPlansCategoryInSnapshotAndTogglePersists() = runBlocking {
        val fixture = CloudFixture(TestAdapter())
        val service = onService(fixture)
        val snapshot = kotlinx.coroutines.runBlocking {
            kotlinx.coroutines.withTimeout(5000) { service.snapshot.first { it.categorySummary.isNotEmpty() } }
        }
        assertEquals(false, snapshot.categoryEnabled["sync_enable_readingplans"])
        assertEquals(net.bible.test.testCoreStrings().syncCategoryContents("READINGPLANS"), snapshot.categorySummary["sync_enable_readingplans"])
        service.applyCategoryChanges(mapOf("sync_enable_readingplans" to true))
        assertTrue(SyncableDatabaseDefinition.READINGPLANS.isSyncEnabled(CommonUtils.settings))
        Unit
    }

    @Test fun adapter_persistsViaCloudAdapters() {
        val service = impl()
        service.setAdapter("NEXT_CLOUD")
        assertEquals("NEXT_CLOUD", CommonUtils.settings.getString("sync_adapter", null))
    }
    /**
     * The cloud usage line shows megabytes with two decimals in the default locale (Finnish comma, US point).
     * A signed-in state is forced by installing a mocked adapter into the real [net.bible.service.cloudsync.CloudSync];
     * the bytes come from one SyncStatus row (1.5 MiB) of the bookmarks database.
     */
    @Test fun cloudInfoSummaryFormatsTwoDecimalsInDefaultLocale() = kotlinx.coroutines.runBlocking {
        val cloudSync = org.koin.java.KoinJavaComponent.get<net.bible.service.cloudsync.CloudSync>(net.bible.service.cloudsync.CloudSync::class.java)
        val field = cloudSync.javaClass.getDeclaredField("_adapter").also { it.isAccessible = true }
        val adapter = com.nhaarman.mockitokotlin2.mock<net.bible.service.cloudsync.CloudAdapter> {
            on { signedIn } doReturn true
        }
        val savedLocale = java.util.Locale.getDefault()
        val savedAdapter = field.get(cloudSync)
        field.set(cloudSync, adapter)
        CommonUtils.settings.setBoolean("sync_enable_bookmarks", true)
        try {
            SyncableDatabaseDefinition.BOOKMARKS.accessor.dao.addStatus(
                net.bible.android.database.SyncStatus("other-device", 1, 1_572_864, 0))
            for ((locale, expected) in listOf(java.util.Locale("fi") to "1,50", java.util.Locale.US to "1.50")) {
                java.util.Locale.setDefault(locale)
                val scope = CoroutineScope(Job() + kotlinx.coroutines.Dispatchers.Unconfined).also { scopes.add(it) }
                val service = impl(scope)
                val snapshot = withTimeout(10_000) { service.snapshot.first { it.cloudInfoSummary != null } }
                assertEquals(net.bible.test.testCoreStrings().syncCloudInfo(expected), snapshot.cloudInfoSummary)
            }
        } finally {
            java.util.Locale.setDefault(savedLocale)
            field.set(cloudSync, savedAdapter)
        }
    }
}
