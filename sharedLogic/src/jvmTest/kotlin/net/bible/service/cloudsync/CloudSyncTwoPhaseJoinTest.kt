package net.bible.service.cloudsync

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import net.bible.android.control.event.UserMessage
import net.bible.android.control.event.UserMessages
import net.bible.android.control.versification.BookInstallWatcher
import net.bible.android.database.SyncConfiguration
import net.bible.android.database.bookmarks.BookmarkEntities
import net.bible.service.cloudsync.documents.DocumentSync
import net.bible.service.cloudsync.documents.DocumentSyncSettings
import net.bible.service.cloudsync.ios.StubCoreStrings
import net.bible.service.db.DatabaseContainer
import net.bible.service.db.FakeDatabaseHost
import net.bible.service.db.newTestDir
import net.bible.sharedcore.cloud.DocumentSyncStarter
import net.bible.sharedcore.platform.AppEnvironment
import net.bible.sharedcore.platform.OrderedLauncher
import net.bible.sharedcore.platform.cloud.CloudFile
import net.bible.sharedcore.platform.cloud.DownloadProgressListener
import net.bible.sharedcore.platform.sync.SyncScheduler
import net.bible.sharedcore.ui.dialog.AppDialogController
import net.bible.sharedlogic.platform.AddonCatalog
import net.bible.sharedlogic.settings.DatabaseAppSettings
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import okio.Sink
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers as D
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import net.bible.sharedcore.ui.dialog.AppDialogRequest
import net.bible.sharedcore.ui.dialog.AppDialogResult
import kotlin.test.assertTrue

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class CloudSyncTwoPhaseJoinTest {
    private class Environment(private val dir: String) : AppEnvironment {
        override val isInitialized = true
        override val deviceIdentifier = "test-device"
        override val isDiscrete = false
        override val isBeta = false
        override val isDebugMode = true
        override val applicationNameMedium = "Test"
        override val packageName = "net.bible.android.activity"
        override val versionNumber = 1L
        override val isMeteredNetwork = false
        override val megabytesFree = 1000L
        override val tmpDir = dir
        override val cacheDir = dir
        override val dbBackupPath = dir
        override val lastCrashTime = 0L
        override val apkPath: String? = null
        override fun databasePath(name: String) = "$dir/$name"
        override fun deleteDatabase(name: String) = false
    }


    private class Adapter : CloudAdapter {
        val cloudFolderNames = mutableSetOf<String>()
        var folderKnown = true
        val listedNames = mutableListOf<String?>()
        val getFoldersCalls = mutableListOf<String>()
        val deleted = java.util.Collections.synchronizedList(mutableListOf<String>())
        val createdFolders = java.util.Collections.synchronizedList(mutableListOf<String>())
        /** Appended to the id of every listed cloud folder: a changed value simulates a folder recreated by another device. */
        @Volatile var folderIdSuffix = ""
        @Volatile var gate: CompletableDeferred<Unit>? = null
        val entered = CompletableDeferred<Unit>()
        /** Gates the next cloud-folder listing (name containing "-sync-"): the probe of a category stays inside listFiles. */
        @Volatile var listGate: CompletableDeferred<Unit>? = null
        val listEntered = CompletableDeferred<Unit>()
        /** Gates only the FIRST isSyncFolderKnown call, so a pass probe stays in flight while another caller probes freely. */
        @Volatile var knownGateOnce: CompletableDeferred<Unit>? = null
        val knownEntered = CompletableDeferred<Unit>()
        private val knownCalls = java.util.concurrent.atomic.AtomicInteger()
        override val signedIn get() = true
        override suspend fun signIn() = true
        override suspend fun signOut() {}
        override suspend fun listFiles(parentsIds: List<String>?, name: String?, mimeType: String?, createdTimeAtLeast: Long?): List<CloudFile> {
            synchronized(listedNames) { listedNames += name }
            if (name != null && name.contains("-sync-")) listGate?.let { listEntered.complete(Unit); it.await() }
            return if (name != null && name in cloudFolderNames) listOf(CloudFile("cloud-$name$folderIdSuffix", name, 0L, 1L, "root")) else emptyList()
        }
        override suspend fun createNewFolder(name: String, parentId: String?): CloudFile = also { createdFolders += name }.let { CloudFile("new-$name", name, 0L, 1L, parentId ?: "root") }
        override suspend fun get(id: String): CloudFile = error("unused")
        override suspend fun getFolders(parentId: String): List<CloudFile> {
            synchronized(getFoldersCalls) { getFoldersCalls += parentId }
            gate?.let { entered.complete(Unit); it.await() }
            return emptyList()
        }
        override suspend fun download(id: String, sink: Sink, onProgress: DownloadProgressListener?) = error("unused")
        override suspend fun upload(name: String, file: Path, parentId: String): CloudFile = CloudFile("up-$name", name, 1L, 1L, parentId)
        override suspend fun delete(id: String) { deleted += id }
        override suspend fun isSyncFolderKnown(dbDef: SyncableDatabaseAccessor<*>, name: String, id: String): Boolean {
            if (knownCalls.getAndIncrement() == 0) knownGateOnce?.let { knownEntered.complete(Unit); it.await() }
            return folderKnown
        }
        override suspend fun makeSyncFolderKnown(dbDef: SyncableDatabaseAccessor<*>, name: String, id: String) {}
        override suspend fun getConfigs(dbDef: SyncableDatabaseAccessor<*>) = emptyList<SyncConfiguration>()
    }

    private class Fixture(val cloud: CloudSync, val adapter: Adapter, val dialogs: AppDialogController, val settings: DatabaseAppSettings, val scope: CoroutineScope) {
        val requests = mutableListOf<AppDialogRequest>()
        @Volatile var answer: (AppDialogRequest.SyncJoinPlan) -> AppDialogResult = { AppDialogResult.Cancel }
        fun db(c: SyncableDatabaseDefinition) = DatabaseContainer.databaseAccessorsByCategory.getValue(c)
        fun folderName(c: SyncableDatabaseDefinition) = cloud.syncFolderName(db(c).categoryName)
        fun cloudHas(vararg c: SyncableDatabaseDefinition) = c.forEach { adapter.cloudFolderNames += folderName(it) }
        fun enable(vararg c: SyncableDatabaseDefinition) = c.forEach { it.setSyncEnabled(settings, true) }
        fun enabled(c: SyncableDatabaseDefinition) = c.isSyncEnabled(settings)
        fun planRequests() = synchronized(requests) { requests.filterIsInstance<AppDialogRequest.SyncJoinPlan>() }
        suspend fun join(c: SyncableDatabaseDefinition) {
            db(c).dao.setConfig(SYNC_FOLDER_FILE_ID_KEY, "sync-${c.name}")
            db(c).dao.setConfig(SYNC_DEVICE_FOLDER_FILE_ID_KEY, "device-${c.name}")
            db(c).dao.setConfig(SYNC_NAMESPACE_KEY, cloud.namespace)
        }
        /** Waits (real time) until a dialog is showing; the responder, when on, answers it. */
        suspend fun awaitPending() = withContext(D.Default) { withTimeout(30_000) { dialogs.pending.filterNotNull().first() } }
    }

    private fun withFixture(autoAnswer: Boolean = true, block: suspend kotlinx.coroutines.test.TestScope.(Fixture) -> Unit) = runTest {
        val hostField = DatabaseContainer::class.java.getDeclaredField("_host").apply { isAccessible = true }
        val instanceField = DatabaseContainer::class.java.getDeclaredField("_instance").apply { isAccessible = true }
        val previousHost = hostField.get(null)
        val previousInstance = instanceField.get(null)
        val previousReady = DatabaseContainer.ready
        val previousFactory = DatabaseContainer.containerFactory
        val dir = newTestDir()
        var opened: DatabaseContainer? = null
        val adapter = Adapter()
        val responderScope = CoroutineScope(D.Default)
        Dispatchers.setMain(Dispatchers.Unconfined)
        try {
            DatabaseContainer.detachInstanceForTest()
            DatabaseContainer.installHost(FakeDatabaseHost(dir))
            val container = DatabaseContainer.create().also { opened = it }
            DatabaseContainer.containerFactory = { container }
            DatabaseContainer.ready = true
            val env = Environment(dir)
            val settings = DatabaseAppSettings({ container.settingsStore }, { true })
            val launcher = OrderedLauncher(backgroundScope)
            val docSettings = DocumentSyncSettings({ container.documentSyncDb.documentSyncPreferencesDao() },
                { container.documentSyncDb.cloudListingStateDao() }, { container.documentSyncDb.cloudDocumentSyncTimestampDao() }, launcher, env)
            val starter = object : DocumentSyncStarter {
                override fun pushDocuments(initials: List<String>) {}
                override fun start(push: List<String>, download: List<String>, remove: List<String>, purge: List<String>, uninstall: List<String>) {}
                override fun stop() {}
            }
            lateinit var documents: DocumentSync
            val provider = DocumentSyncProvider { documents }
            val dialogs = AppDialogController()
            val cloud = CloudSync(dialogs, StubCoreStrings(), env, settings, object : CloudAdapterFactory {
                override val available = listOf("test")
                override fun displayName(id: String) = id
                override fun create(id: String) = adapter
            }, SyncScheduler { error("not scheduled") }, starter, provider, docSettings)
            val watcher = BookInstallWatcher(launcher, starter, provider, docSettings, object : AddonCatalog {
                override fun clearCaches() {}
                override val readingPlans = emptyMap<String, net.bible.sharedlogic.platform.AddonReadingPlan>()
            })
            documents = DocumentSync(env, CloudSyncProvider { cloud }, starter, watcher, docSettings, { error("no backup") })
            assertEquals(true, cloud.signIn())
            val f = Fixture(cloud, adapter, dialogs, settings, responderScope)
            if (autoAnswer) responderScope.launch {
                dialogs.pending.filterNotNull().collect { shown ->
                    synchronized(f.requests) { f.requests += shown.request }
                    val r = shown.request
                    dialogs.respond(shown.id, if (r is AppDialogRequest.SyncJoinPlan) f.answer(r) else AppDialogResult.Cancel)
                }
            }
            block(f)
        } finally {
            responderScope.cancel()
            Dispatchers.resetMain()
            try {
                DatabaseContainer.detachInstanceForTest()
                withContext(NonCancellable) {
                    opened?.let { container -> DatabaseContainer.aiLifetime.replace { container.closeAllOwned(it) } }
                }
            } finally {
                instanceField.set(null, previousInstance)
                hostField.set(null, previousHost)
                DatabaseContainer.containerFactory = previousFactory
                DatabaseContainer.ready = previousReady
                FileSystem.SYSTEM.deleteRecursively(dir.toPath())
            }
        }
    }

    private fun all(f: Fixture, choice: String) { f.answer = { r -> AppDialogResult.SyncJoinChoices(r.rows.associate { it.key to choice }) } }
    private val three = arrayOf(SyncableDatabaseDefinition.BOOKMARKS, SyncableDatabaseDefinition.WORKSPACES, SyncableDatabaseDefinition.READINGPLANS)

    @Test fun onePlanForAllCategoriesInAPass() = withFixture { f ->
        f.enable(*three); f.cloudHas(*three); all(f, "disable")
        f.cloud.synchronize()
        val plans = f.planRequests()
        assertEquals(1, plans.size, "one collected dialog for the whole pass")
        assertEquals(three.map { it.name }.toSet(), plans.single().rows.map { it.key }.toSet())
        three.forEach { assertFalse(f.enabled(it), "disable answer turns $it off") }
    }

    @Test fun backgroundPassSkipsDecisionCategoriesWithoutDialog() = withFixture { f ->
        f.enable(*three); f.cloudHas(*three)
        f.cloud.synchronize(allowInteraction = false)
        assertEquals(0, f.requests.size, "a background pass raises no dialog")
        assertEquals(0, synchronized(f.adapter.listedNames) { f.adapter.listedNames.count { it != null && it.contains("-sync-") } }, "and does not list the cloud")
        three.forEach {
            assertTrue(f.enabled(it), "$it stays enabled")
            assertNull(f.db(it).dao.getString(SYNC_FOLDER_FILE_ID_KEY), "no folder id written for $it")
        }
    }

    @Test fun cancelDisablesOnlyJoiningCategories() = withFixture { f ->
        val ready = SyncableDatabaseDefinition.BOOKMARKS
        val joining = SyncableDatabaseDefinition.WORKSPACES
        f.enable(ready, joining); f.join(ready); f.cloudHas(joining)
        f.answer = { AppDialogResult.Cancel }
        f.cloud.synchronize()
        assertEquals(1, f.planRequests().size)
        assertTrue(f.enabled(ready), "the Ready category keeps syncing")
        assertFalse(f.enabled(joining), "the category being joined is disabled")
        assertTrue("sync-${ready.name}" in synchronized(f.adapter.getFoldersCalls) { f.adapter.getFoldersCalls.toList() }, "the Ready category ran its download step")
        assertNull(f.db(joining).dao.getString(SYNC_FOLDER_FILE_ID_KEY))
    }

    @Test fun planJoinsSkipsACategoryDisabledForItsVersion() = withFixture { f ->
        val c = SyncableDatabaseDefinition.BOOKMARKS
        f.cloudHas(c)
        f.db(c).dao.setConfig("disabledForVersion", f.db(c).version().toLong())
        assertTrue(f.cloud.planJoins(setOf(c), enable = true), "nothing to plan for it, so nothing was cancelled")
        assertEquals(0, f.planRequests().size, "no question for a category the pass would skip anyway")
        assertEquals(0, synchronized(f.adapter.listedNames) { f.adapter.listedNames.count { it != null && it.contains("-sync-") } }, "and no probe")
        assertTrue(f.cloud.queuedJoins.value.isEmpty())
    }

    @Test fun planJoinsCompletesWhileAPassHoldsTheMutex() = withFixture(autoAnswer = false) { f ->
        val ready = SyncableDatabaseDefinition.BOOKMARKS
        val joining = SyncableDatabaseDefinition.WORKSPACES
        f.enable(ready); f.join(ready); f.cloudHas(joining)
        val gate = CompletableDeferred<Unit>().also { f.adapter.gate = it }
        val pass = f.scope.async { f.cloud.synchronize() }
        withContext(D.Default) { withTimeout(30_000) { f.adapter.entered.await() } }
        var probed = false
        val plan = f.scope.async { f.cloud.planJoins(setOf(joining), onProbed = { probed = true }) }
        val shown = f.awaitPending() // the plan dialog is up although the pass still holds syncMutex
        val request = shown.request as AppDialogRequest.SyncJoinPlan
        assertFalse(gate.isCompleted)
        assertTrue(probed)
        assertFalse(pass.isCompleted)
        assertEquals(joining.name, request.rows.single().key)
        gate.complete(Unit)
        withContext(D.Default) { withTimeout(30_000) { pass.await() } }
        assertEquals(shown.id, f.dialogs.pending.value?.id, "the pass raised no second dialog")
        f.dialogs.cancelAll()
        assertEquals(false, withContext(D.Default) { withTimeout(30_000) { plan.await() } })
        assertFalse(f.enabled(joining))
    }

    @Test fun awaitingCategoryIsSkippedByConcurrentPass() = withFixture(autoAnswer = false) { f ->
        val c = SyncableDatabaseDefinition.BOOKMARKS
        f.enable(c); f.cloudHas(c)
        val plan = f.scope.async { f.cloud.planJoins(setOf(c)) }
        val shown = f.awaitPending()
        val listingsBefore = synchronized(f.adapter.listedNames) { f.adapter.listedNames.size }
        withContext(D.Default) { withTimeout(30_000) { f.cloud.synchronize() } }
        assertEquals(shown.id, f.dialogs.pending.value?.id, "still the same single dialog")
        assertEquals(listingsBefore, synchronized(f.adapter.listedNames) { f.adapter.listedNames.size }, "the pass did not even probe the awaiting category")
        assertTrue(f.enabled(c))
        f.dialogs.respond(shown.id, AppDialogResult.SyncJoinChoices(mapOf(c.name to "disable")))
        assertEquals(true, withContext(D.Default) { withTimeout(30_000) { plan.await() } })
        assertFalse(f.enabled(c), "the disable choice turned it off")
    }

    @Test fun planJoinsCancelRevertsOnlyItsCategoriesAndClearsAwaiting() = withFixture(autoAnswer = false) { f ->
        val mine = SyncableDatabaseDefinition.BOOKMARKS
        val other = SyncableDatabaseDefinition.WORKSPACES
        f.enable(mine, other); f.cloudHas(mine)
        val plan = f.scope.async { f.cloud.planJoins(setOf(mine)) }
        val shown = f.awaitPending()
        f.dialogs.respond(shown.id, AppDialogResult.Cancel)
        assertEquals(false, withContext(D.Default) { withTimeout(30_000) { plan.await() } })
        assertFalse(f.enabled(mine))
        assertTrue(f.enabled(other))
        assertTrue(f.cloud.queuedJoins.value.isEmpty())
    }

    @Test fun cancelledPlanJoinsClearsAwaitingAndDisables() = withFixture(autoAnswer = false) { f ->
        val c = SyncableDatabaseDefinition.BOOKMARKS
        f.enable(c); f.cloudHas(c)
        val plan = f.scope.async { f.cloud.planJoins(setOf(c)) }
        f.awaitPending()
        plan.cancel()
        withContext(D.Default) { withTimeout(30_000) { plan.join() } }
        assertFalse(f.enabled(c))
        // Re-enabled, the category is no longer awaiting: a pass probes (and asks about) it again.
        f.enable(c)
        all(f, "disable")
        val answerer = f.scope.launch { f.awaitPending().let { f.dialogs.respond(it.id, f.answer(it.request as AppDialogRequest.SyncJoinPlan)) } }
        withContext(D.Default) { withTimeout(30_000) { f.cloud.synchronize() } }
        answerer.join()
        assertFalse(f.enabled(c))
    }

    @Test fun storedDecisionIsExecutedByNextPass() = withFixture { f ->
        val c = SyncableDatabaseDefinition.BOOKMARKS
        f.enable(c); f.cloudHas(c); all(f, "fetch")
        assertTrue(f.cloud.planJoins(setOf(c)))
        assertEquals(1, f.planRequests().size)
        assertEquals(setOf(c), f.cloud.queuedJoins.value)
        f.cloud.synchronize()
        assertEquals(1, f.planRequests().size, "the stored decision needs no second dialog")
        assertTrue(f.cloud.queuedJoins.value.isEmpty(), "the pass consumed it")
        assertEquals("cloud-${f.folderName(c)}", f.db(c).dao.getString(SYNC_FOLDER_FILE_ID_KEY), "the fetch join was executed")
        assertTrue(synchronized(f.adapter.listedNames) { INITIAL_BACKUP_FILENAME in f.adapter.listedNames }, "FETCH_INITIAL looked for the initial file")
        assertTrue(f.enabled(c))
    }

    @Test fun planJoinsCancelledDuringProbeDisablesAndClears() = withFixture { f ->
        val c = SyncableDatabaseDefinition.BOOKMARKS
        f.enable(c); f.cloudHas(c); all(f, "disable")
        val gate = CompletableDeferred<Unit>().also { f.adapter.listGate = it }
        val plan = f.scope.async { f.cloud.planJoins(setOf(c)) }
        withContext(D.Default) { withTimeout(30_000) { f.adapter.listEntered.await() } }
        plan.cancel()
        withContext(D.Default) { withTimeout(30_000) { plan.join() } }
        gate.complete(Unit); f.adapter.listGate = null
        assertFalse(f.enabled(c), "a cancel during the probe turns the category off")
        // The awaiting mark is gone: a re-enabled category is asked about by the next pass.
        f.enable(c)
        f.cloud.synchronize()
        assertEquals(1, f.planRequests().size)
    }

    @Test fun cancelledNewPlanDropsAnEarlierStoredDecision() = withFixture { f ->
        val c = SyncableDatabaseDefinition.BOOKMARKS
        f.enable(c); f.cloudHas(c); all(f, "fetch")
        assertTrue(f.cloud.planJoins(setOf(c)))
        assertEquals(setOf(c), f.cloud.queuedJoins.value)
        f.answer = { AppDialogResult.Cancel }
        assertEquals(false, f.cloud.planJoins(setOf(c)))
        assertTrue(f.cloud.queuedJoins.value.isEmpty(), "the stale FETCH is gone")
        f.enable(c)
        f.cloud.synchronize()
        assertEquals(3, f.planRequests().size, "the pass asks again instead of running the old FETCH")
        assertFalse(synchronized(f.adapter.listedNames) { INITIAL_BACKUP_FILENAME in f.adapter.listedNames })
        assertNull(f.db(c).dao.getString(SYNC_FOLDER_FILE_ID_KEY))
    }

    @Test fun disabledCategoryDropsItsStoredDecision() = withFixture { f ->
        val c = SyncableDatabaseDefinition.BOOKMARKS
        f.enable(c); f.cloudHas(c); all(f, "fetch")
        assertTrue(f.cloud.planJoins(setOf(c)))
        c.setSyncEnabled(f.settings, false) // another path turns it off
        f.cloud.synchronize()
        assertTrue(f.cloud.queuedJoins.value.isEmpty(), "a pass that finds the category disabled drops its decision")
        f.enable(c)
        f.answer = { AppDialogResult.Cancel }
        f.cloud.synchronize()
        assertEquals(2, f.planRequests().size, "no old FETCH runs: the re-enabled category is asked again")
    }

    @Test fun nonListedProbeNeverConsumesAStoredDecision() = withFixture { f ->
        val c = SyncableDatabaseDefinition.BOOKMARKS
        f.enable(c); f.cloudHas(c); all(f, "fetch")
        // A half-finished join: the folder id is stored, the device folder id is not.
        f.db(c).dao.setConfig(SYNC_FOLDER_FILE_ID_KEY, "stale")
        f.db(c).dao.setConfig(SYNC_NAMESPACE_KEY, f.cloud.namespace)
        val gate = CompletableDeferred<Unit>().also { f.adapter.knownGateOnce = it }
        // The background pass probes without listing the cloud (placeholder NeedsCreate) and stays in the probe.
        val pass = f.scope.async { f.cloud.synchronize(allowInteraction = false) }
        withContext(D.Default) { withTimeout(30_000) { f.adapter.knownEntered.await() } }
        assertTrue(f.cloud.planJoins(setOf(c))) // stores FETCH meanwhile
        gate.complete(Unit)
        withContext(D.Default) { withTimeout(30_000) { pass.await() } }
        assertEquals(setOf(c), f.cloud.queuedJoins.value, "the placeholder probe left the decision queued")
        assertFalse(synchronized(f.adapter.listedNames) { INITIAL_BACKUP_FILENAME in f.adapter.listedNames }, "nothing was created or fetched")
        assertTrue(f.enabled(c))
        // The next pass that lists the cloud runs the confirmed FETCH.
        f.cloud.synchronize()
        assertTrue(f.cloud.queuedJoins.value.isEmpty())
        assertTrue(synchronized(f.adapter.listedNames) { INITIAL_BACKUP_FILENAME in f.adapter.listedNames })
    }

    @Test fun planJoinsEnableMarksAwaitingBeforeEnabling() = withFixture(autoAnswer = false) { f ->
        val c = SyncableDatabaseDefinition.BOOKMARKS
        f.cloudHas(c)
        assertFalse(f.enabled(c))
        val plan = f.scope.async { f.cloud.planJoins(setOf(c), enable = true) }
        val shown = f.awaitPending()
        assertTrue(f.enabled(c), "enabled while the plan dialog is open")
        val listingsBefore = synchronized(f.adapter.listedNames) { f.adapter.listedNames.size }
        withContext(D.Default) { withTimeout(30_000) { f.cloud.synchronize() } }
        assertEquals(listingsBefore, synchronized(f.adapter.listedNames) { f.adapter.listedNames.size }, "a pass never sees it enabled but not awaiting")
        f.dialogs.respond(shown.id, AppDialogResult.Cancel)
        assertEquals(false, withContext(D.Default) { withTimeout(30_000) { plan.await() } })
        assertFalse(f.enabled(c), "cancel reverts the enable")
    }

    @Test fun planJoinsEnableKeepsCategoryOnAfterAConfirmedPlan() = withFixture { f ->
        val c = SyncableDatabaseDefinition.BOOKMARKS
        f.cloudHas(c); all(f, "merge")
        assertTrue(f.cloud.planJoins(setOf(c), enable = true))
        assertTrue(f.enabled(c))
        assertEquals(setOf(c), f.cloud.queuedJoins.value)
    }

    @Test fun planJoinsEnableIsRevertedWhenCancelledDuringProbe() = withFixture { f ->
        val c = SyncableDatabaseDefinition.BOOKMARKS
        f.cloudHas(c)
        val gate = CompletableDeferred<Unit>().also { f.adapter.listGate = it }
        val plan = f.scope.async { f.cloud.planJoins(setOf(c), enable = true) }
        withContext(D.Default) { withTimeout(30_000) { f.adapter.listEntered.await() } }
        assertTrue(f.enabled(c))
        plan.cancel()
        withContext(D.Default) { withTimeout(30_000) { plan.join() } }
        gate.complete(Unit); f.adapter.listGate = null
        assertFalse(f.enabled(c))
    }

    @Test fun executePhaseSkipsACategoryDisabledAfterItsDecisionWasPeeked() = withFixture { f ->
        val a = SyncableDatabaseDefinition.BOOKMARKS
        val b = SyncableDatabaseDefinition.WORKSPACES
        f.enable(a, b); f.cloudHas(a, b); all(f, "fetch")
        assertTrue(f.cloud.planJoins(setOf(a))) // stores FETCH for a
        // The pass asks about b; while that dialog is open, an Apply turns a off.
        f.answer = { r -> a.setSyncEnabled(f.settings, false); AppDialogResult.SyncJoinChoices(r.rows.associate { it.key to "disable" }) }
        f.cloud.synchronize()
        assertNull(f.db(a).dao.getString(SYNC_FOLDER_FILE_ID_KEY), "the stale FETCH did not run")
        assertFalse(synchronized(f.adapter.listedNames) { INITIAL_BACKUP_FILENAME in f.adapter.listedNames })
        assertFalse(f.enabled(a))
        assertTrue(f.cloud.queuedJoins.value.isEmpty())
    }

    @Test fun executePhaseSkipsAStoredDecisionDroppedMeanwhile() = withFixture { f ->
        val a = SyncableDatabaseDefinition.BOOKMARKS
        val b = SyncableDatabaseDefinition.WORKSPACES
        f.enable(a, b); f.cloudHas(a, b); all(f, "fetch")
        assertTrue(f.cloud.planJoins(setOf(a)))
        f.answer = { r -> f.cloud.dropStoredJoin(a); AppDialogResult.SyncJoinChoices(r.rows.associate { it.key to "disable" }) }
        f.cloud.synchronize()
        assertNull(f.db(a).dao.getString(SYNC_FOLDER_FILE_ID_KEY), "a decision that is gone is not executed from the stale peek")
        assertFalse(synchronized(f.adapter.listedNames) { INITIAL_BACKUP_FILENAME in f.adapter.listedNames })
    }

    @Test fun createDecisionIsNotReusedWhenACloudFolderAppearsMeanwhile() = withFixture { f ->
        val c = SyncableDatabaseDefinition.BOOKMARKS
        f.enable(c); all(f, "disable")
        assertTrue(f.cloud.planJoins(setOf(c))) // no cloud folder: CREATE_NEW stored without a question
        assertEquals(0, f.planRequests().size)
        assertEquals(setOf(c), f.cloud.queuedJoins.value)
        f.cloudHas(c) // another device created it
        f.cloud.synchronize()
        assertEquals(1, f.planRequests().size, "the user is asked: the stored create never replaces an existing folder")
        assertTrue(f.adapter.deleted.isEmpty(), "nothing deleted")
        assertTrue(f.adapter.createdFolders.isEmpty(), "nothing created")
        assertNull(f.db(c).dao.getString(SYNC_FOLDER_FILE_ID_KEY))
        assertFalse(f.enabled(c))
    }

    @Test fun backgroundPassNeverRunsACreateDecisionAgainstAnExistingCloudFolder() = withFixture { f ->
        val c = SyncableDatabaseDefinition.BOOKMARKS
        f.enable(c)
        assertTrue(f.cloud.planJoins(setOf(c)))
        f.cloudHas(c)
        f.cloud.synchronize(allowInteraction = false)
        assertEquals(0, f.requests.size)
        assertTrue(f.adapter.deleted.isEmpty(), "nothing deleted")
        assertTrue(f.adapter.createdFolders.isEmpty(), "nothing created")
        assertNull(f.db(c).dao.getString(SYNC_FOLDER_FILE_ID_KEY))
        assertTrue(f.enabled(c), "a background pass leaves the category as it was")
    }

    @Test fun storedDecisionForAnotherCloudFolderIsNotExecuted() = withFixture { f ->
        val c = SyncableDatabaseDefinition.BOOKMARKS
        f.enable(c); f.cloudHas(c); all(f, "fetch")
        assertTrue(f.cloud.planJoins(setOf(c)))
        f.adapter.folderIdSuffix = "-recreated" // the folder was replaced after the decision
        all(f, "disable")
        f.cloud.synchronize()
        assertEquals(2, f.planRequests().size, "asked again about the new folder")
        assertNull(f.db(c).dao.getString(SYNC_FOLDER_FILE_ID_KEY))
        assertFalse(synchronized(f.adapter.listedNames) { INITIAL_BACKUP_FILENAME in f.adapter.listedNames })
    }

    @Test fun storedCreateDecisionIsExecutedByNextPass() = withFixture { f ->
        val c = SyncableDatabaseDefinition.BOOKMARKS
        f.enable(c); all(f, "disable")
        assertTrue(f.cloud.planJoins(setOf(c))) // no cloud folder: CREATE_NEW stored without a question
        f.cloud.synchronize()
        assertEquals(0, f.planRequests().size, "a stored create needs no dialog")
        assertTrue(f.cloud.queuedJoins.value.isEmpty(), "the pass consumed it")
        assertEquals("new-${f.folderName(c)}", f.db(c).dao.getString(SYNC_FOLDER_FILE_ID_KEY), "the create ran")
        assertTrue(f.enabled(c))
    }

    @Test fun storedAndDialogDecisionsOfOnePassBothExecute() = withFixture { f ->
        val a = SyncableDatabaseDefinition.BOOKMARKS
        val b = SyncableDatabaseDefinition.WORKSPACES
        f.enable(a, b); f.cloudHas(b); all(f, "fetch")
        assertTrue(f.cloud.planJoins(setOf(a))) // create for a, stored
        f.cloud.synchronize() // b is asked, a comes from the store
        assertEquals(1, f.planRequests().size, "only b is asked")
        assertEquals("new-${f.folderName(a)}", f.db(a).dao.getString(SYNC_FOLDER_FILE_ID_KEY), "the stored create ran")
        assertEquals("cloud-${f.folderName(b)}", f.db(b).dao.getString(SYNC_FOLDER_FILE_ID_KEY), "the dialog's fetch ran")
    }

    @Test fun executePhaseDoesNotRunOrDiscardAStoredDecisionReplacedMeanwhile() = withFixture { f ->
        val a = SyncableDatabaseDefinition.BOOKMARKS
        val b = SyncableDatabaseDefinition.WORKSPACES
        f.enable(a, b); f.cloudHas(b); all(f, "fetch")
        assertTrue(f.cloud.planJoins(setOf(a)))
        f.answer = { r ->
            // Another Apply replaces a's decision while this pass's dialog is open.
            kotlinx.coroutines.runBlocking { f.cloud.planJoins(setOf(a)) }
            AppDialogResult.SyncJoinChoices(r.rows.associate { it.key to "fetch" })
        }
        f.cloud.synchronize()
        assertNull(f.db(a).dao.getString(SYNC_FOLDER_FILE_ID_KEY), "the replaced decision is not run by this pass")
        assertEquals(setOf(a), f.cloud.queuedJoins.value, "the newer decision survives for the next pass")
    }

    @Test fun fetchDecisionBoundToAVanishedFolderIsNotRunAsACreate() = withFixture { f ->
        val c = SyncableDatabaseDefinition.BOOKMARKS
        f.enable(c); f.cloudHas(c); all(f, "fetch")
        assertTrue(f.cloud.planJoins(setOf(c)))
        f.adapter.cloudFolderNames.clear() // the folder vanished: the probe now says NeedsCreate
        all(f, "disable")
        f.cloud.synchronize()
        // A missing folder is planned afresh (a create needs no question), never run from the fetch answer.
        assertEquals("new-${f.folderName(c)}", f.db(c).dao.getString(SYNC_FOLDER_FILE_ID_KEY), "created, not fetched")
        assertFalse(synchronized(f.adapter.listedNames) { INITIAL_BACKUP_FILENAME in f.adapter.listedNames }, "no fetch ran")
    }

    @Test fun dropStoredJoinIfSameLeavesAReplacementDecision() = withFixture { f ->
        val c = SyncableDatabaseDefinition.BOOKMARKS
        f.enable(c)
        assertTrue(f.cloud.planJoins(setOf(c)))
        val mine = f.cloud.storedJoinToken(c)
        assertTrue(f.cloud.planJoins(setOf(c))) // a second Apply replaces it
        f.cloud.dropStoredJoinIfSame(c, mine)
        assertEquals(setOf(c), f.cloud.queuedJoins.value, "only my own decision is dropped")
        f.cloud.dropStoredJoinIfSame(c, f.cloud.storedJoinToken(c))
        assertTrue(f.cloud.queuedJoins.value.isEmpty())
    }
}
