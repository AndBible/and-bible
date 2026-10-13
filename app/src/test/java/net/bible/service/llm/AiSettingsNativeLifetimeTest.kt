package net.bible.service.llm

import kotlinx.coroutines.flow.map

import androidx.room3.Room
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.SQLiteStatement
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.*
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.BibleApplication
import net.bible.android.database.AiSettingsDatabase
import net.bible.android.platform.AndroidAppEnvironment
import net.bible.service.cloudsync.CloudSync
import net.bible.service.cloudsync.SyncableDatabaseDefinition
import net.bible.service.db.DatabaseContainer
import net.bible.sharedcore.platform.AppCoroutineScope
import net.bible.sharedcore.platform.OrderedLauncher
import net.bible.test.DatabaseResetter
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.java.KoinJavaComponent.get
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.Collections
import kotlinx.coroutines.sync.Mutex

@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class AiSettingsNativeLifetimeTest {
    /** Hold the actual bundled statement boundary, and suppress unsafe native closes on RED. */
    private class NativeBoundary : SQLiteDriver {
        private val delegate = BundledSQLiteDriver()
        val armed = AtomicBoolean()
        val ownerLabel = ThreadLocal<String?>()
        val initialRefresh = CompletableDeferred<Unit>()
        val reads = AtomicInteger()
        var matchSql: (String) -> Boolean = { it.startsWith("SELECT * FROM GlobalAiSettings") }
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val unsafeCloses = AtomicInteger()
        val safeCloses = AtomicInteger()
        val attempted = CompletableDeferred<Unit>()
        val writerAttempted = CompletableDeferred<Unit>()
        val secondAttempted = CompletableDeferred<Unit>()
        val queueWriter = AtomicBoolean()
        val events = Collections.synchronizedList(mutableListOf<String>())
        val harnessFailure = java.util.concurrent.atomic.AtomicReference<String?>()
        @Volatile var active: SQLiteConnection? = null
        @Volatile var heldConnection: SQLiteConnection? = null
        @Volatile var activeStatement: SQLiteStatement? = null
        @Volatile var completed = false

        fun event(name: String) { events.add(name); println("NATIVE-ORDER $name") }

        // Observe the real admission mutex, without changing its locking or cancellation behavior.
        fun traceAdmission(lifetime: AiDatabaseLifetime): () -> Unit {
            val field = AiDatabaseLifetime::class.java.getDeclaredField("admission").apply { isAccessible = true }
            val mutex = field.get(lifetime) as Mutex
            field.set(lifetime, object : Mutex by mutex {
                override suspend fun lock(owner: Any?) {
                    if (ownerLabel.get() == "second-replacement") {
                        event("second-replacement-attempt"); secondAttempted.complete(Unit)
                    }
                    if (queueWriter.get() && ownerLabel.get() == "writer") {
                        event("writer-admission-attempt"); writerAttempted.complete(Unit)
                    }
                    if (ownerLabel.get() == "replacement") {
                        event("replacement-attempt")
                        if (mutex.tryLock(owner)) {
                            event("replacement-acquire")
                            // In the bypass mutation, let the real close witness release the handshake.
                            if (active == null) attempted.complete(Unit)
                            return
                        }
                        attempted.complete(Unit)
                    }
                    mutex.lock(owner)
                    if (ownerLabel.get() == "replacement") event("replacement-acquire")
                }
                override fun unlock(owner: Any?) {
                    if (completed && !events.contains("lease-release")) event("lease-release")
                    mutex.unlock(owner)
                }
            })
            return { field.set(lifetime, mutex) }
        }
        override fun open(fileName: String): SQLiteConnection {
            val connection = delegate.open(fileName)
            return object : SQLiteConnection by connection {
                override fun prepare(sql: String): SQLiteStatement {
                    val statement = connection.prepare(sql)
                    return object : SQLiteStatement by statement {
                        override fun step(): Boolean {
                            if (ownerLabel.get() == "store" && matchSql(sql) && armed.compareAndSet(true, false)) {
                                active = connection
                                heldConnection = connection
                                activeStatement = statement
                                event("hold-enter connection=${System.identityHashCode(connection)} statement=${System.identityHashCode(statement)} label=${ownerLabel.get()} sql=$sql")
                                entered.countDown()
                                try {
                                    if (!release.await(10, TimeUnit.SECONDS)) {
                                        harnessFailure.set("HARNESS FAILURE: native hold not released")
                                        event("hold-timeout")
                                        error(harnessFailure.get()!!)
                                    }
                                    event("hold-release")
                                    event("delegate-step-enter")
                                    return statement.step().also { event("delegate-step-exit") }
                                } finally {
                                    active = null
                                    event("step-finally")
                                }
                            }
                            return statement.step()
                        }
                        override fun reset() {
                            statement.reset()
                            if (activeStatement === statement) event("dao-statement-cleanup")
                        }
                        override fun close() {
                            if (activeStatement === statement) {
                                event("active-statement-close connection=${System.identityHashCode(connection)} statement=${System.identityHashCode(statement)}")
                                if (active === connection) { unsafeCloses.incrementAndGet(); return }
                            } else if (active === connection) {
                                event("unrelated-cached-statement-close statement=${System.identityHashCode(statement)}")
                            }
                            statement.close()
                        }
                    }
                }
                override fun close() {
                    if (heldConnection === connection) event("old-connection-close connection=${System.identityHashCode(connection)}")
                    if (active === connection) {
                        unsafeCloses.incrementAndGet()
                        attempted.complete(Unit)
                        return
                    }
                    safeCloses.incrementAndGet()
                    connection.close()
                }
            }
        }
    }

    @Test fun actualCloudSwapWaitsForOwnedNativeSelectBeforeClosingItsConnection() = exerciseNativeBoundary("cloud")

    @Test fun backupRestoreWaitsForOwnedNativeSelectAndQueuedWriteUsesNewDao() = exerciseNativeBoundary("backup")

    @Test fun failedCloudSwapReleasesOwnershipAndRebindsObservation() = exerciseNativeBoundary("failure")

    @Test fun canceledCloudSwapReleasesOwnershipAndRebindsObservation() = exerciseNativeBoundary("cancel")

    @Test fun ownedCancellationDoesNotReleaseActiveNativeQueryBeforeSwap() = exerciseNativeBoundary("ownerCancel")

    @Test fun activeNativeWriteFinishesBeforeSwapAndPostSwapWriteUsesNewDao() = exerciseNativeBoundary("write")

    @Test fun setterQueuedDuringReplacementResolvesOnlyTheReopenedDao() = exerciseNativeBoundary("queued")

    @Test fun nestedCloudSwapUsesTheExplicitOwnerAndWaitsForNativeCompletion() = exerciseNativeBoundary("nested")

    @Test fun concurrentCloudSwapsQueueWhileTheFirstOwnsReplacement() = exerciseNativeBoundary("concurrent")

    private class CleanupProbe {
        var originalAdmission: Any? = null
        var restoreAdmission: (() -> Unit)? = null
        var owner: Job? = null
        var downloaded: File? = null
        val children = mutableListOf<Job>()
        val failure = IllegalStateException("unexpected real swap child failure")
    }

    @Test fun unexpectedSwapChildFailureQuiescesChildrenAndRestoresGlobalAdmission() {
        val probe = CleanupProbe()
        try {
            val failure = runCatching { exerciseNativeBoundary("unexpected", probe) }.exceptionOrNull()
            assertSame("preserve original async child failure", probe.failure, failure)
            val admission = AiDatabaseLifetime::class.java.getDeclaredField("admission").apply { isAccessible = true }
            assertSame("cancelled fixture finally must restore the original global mutex",
                probe.originalAdmission, admission.get(DatabaseContainer.aiLifetime))
            assertTrue("store owner completed", probe.owner!!.isCompleted)
            assertTrue("all retained async children completed", probe.children.isNotEmpty() && probe.children.all { it.isCompleted })
            assertFalse("temporary download removed", probe.downloaded!!.exists())
            assertFalse("mandatory fixture reset cleared replacement epoch", DatabaseContainer.replacing)
            assertEquals(0L, DatabaseContainer.replaceEpoch)
        } finally {
            // Safety net for the RED run: no failed cleanup regression may pollute later classes.
            runBlocking(NonCancellable) {
                probe.children.forEach { it.cancelAndJoin() }
                probe.owner?.cancelAndJoin()
                try { probe.restoreAdmission?.invoke() } finally { DatabaseResetter.resetDatabase() }
            }
        }
    }

    private fun exerciseNativeBoundary(path: String, probe: CleanupProbe? = null) = runBlocking {
        DatabaseResetter.resetDatabase()
        val owner = SupervisorJob()
        val scope = AppCoroutineScope(owner + Dispatchers.Default)
        val boundary = NativeBoundary()
        val writerScope = AppCoroutineScope(owner + Dispatchers.Default + boundary.ownerLabel.asContextElement("writer"))
        val writerLauncher = OrderedLauncher(writerScope)
        val env = AndroidAppEnvironment(BibleApplication.application)
        val container = DatabaseContainer.instance
        container.aiSettingsDb.close()
        container.aiSettingsDb = Room.databaseBuilder<AiSettingsDatabase>(BibleApplication.application, AiSettingsDatabase.dbFileName)
            .setDriver(boundary).setQueryCoroutineContext(Dispatchers.IO)
            .setJournalMode(androidx.room3.RoomDatabase.JournalMode.TRUNCATE).build()
        val target = DatabaseContainer.databaseAccessorsByCategory.getValue(SyncableDatabaseDefinition.AI_SETTINGS)
        val downloaded = File.createTempFile("ai-native-swap-", ".sqlite3")
        val cloud = get<CloudSync>(CloudSync::class.java)
        val settings = AiSettingsStore({
            val current = DatabaseContainer.instance.aiSettingsDb.globalAiSettingsDao()
            object : GlobalAiSettingsDao by current {
                override suspend fun get() = withContext(boundary.ownerLabel.asContextElement("store")) {
                    try { current.get() } finally {
                        if (boundary.reads.incrementAndGet() >= 2) boundary.initialRefresh.complete(Unit)
                        if (boundary.heldConnection != null && !boundary.completed) {
                            boundary.event("dao-completion"); boundary.completed = true
                        }
                    }
                }
                override suspend fun set(settings: GlobalAiSettings) = withContext(boundary.ownerLabel.asContextElement("store")) {
                    if (boundary.queueWriter.get()) boundary.event("queued-writer-dao db=${System.identityHashCode(DatabaseContainer.instance.aiSettingsDb)} value=${settings.maxIterations}")
                    try { current.set(settings) } finally {
                        if (path == "write" && boundary.heldConnection != null && !boundary.completed) {
                            boundary.event("dao-completion"); boundary.completed = true
                        }
                    }
                }
            }
        }, writerLauncher, scope,
            env, DatabaseContainer.databaseRestored, cloud.workspaceRefreshRequired, DatabaseContainer.aiLifetime,
            { DatabaseContainer.instance.aiSettingsDb.invalidationTracker.createFlow("GlobalAiSettings").map { Unit } })
        val admissionField = AiDatabaseLifetime::class.java.getDeclaredField("admission").apply { isAccessible = true }
        probe?.originalAdmission = admissionField.get(DatabaseContainer.aiLifetime)
        probe?.owner = owner
        probe?.downloaded = downloaded
        val restoreAdmission = boundary.traceAdmission(DatabaseContainer.aiLifetime)
        probe?.restoreAdmission = restoreAdmission
        var trigger: Deferred<Unit>? = null
        var laterSwap: Deferred<Unit>? = null
        var swap: Deferred<Unit>? = null
        var originalFailure: Throwable? = null
        try {
            target.localDb.globalDaoForTest().set(GlobalAiSettings(maxIterations = 41))
            target.localDbFile.copyTo(downloaded, overwrite = true)
            target.localDb.globalDaoForTest().set(GlobalAiSettings(maxIterations = 10))
            settings.ensureLoaded()
            withTimeout(5_000) { boundary.initialRefresh.await() }
            if (path == "write") {
                boundary.matchSql = { it.startsWith("INSERT OR REPLACE INTO `GlobalAiSettings`") }
                boundary.armed.set(true)
                settings.maxIterations = 11
                trigger = async(Dispatchers.IO) { writerLauncher.join("ai-settings") }
            } else {
                // Finish the invalidating store write BEFORE arming: startup flow refreshes can
                // otherwise hold admission before that write starts, deadlocking a writer join.
                settings.maxIterations = 11
                writerLauncher.join("ai-settings")
                boundary.armed.set(true)
                trigger = async(Dispatchers.IO) { cloud.notifyWorkspaceRefreshRequired() }
            }
            probe?.children?.add(trigger)
            assertTrue("actual native SELECT entered", withContext(Dispatchers.IO) { boundary.entered.await(5, TimeUnit.SECONDS) })
            if (path == "ownerCancel") owner.cancel()
            val replacementEntered = CompletableDeferred<Unit>()
            swap = async(Dispatchers.IO + boundary.ownerLabel.asContextElement("replacement")) {
                if (path == "backup") {
                    val folder = File(env.tmpDir, "native-ai-restore").apply { mkdirs() }
                    File(folder, "db").mkdirs()
                    downloaded.copyTo(File(folder, "db/${AiSettingsDatabase.dbFileName}"), overwrite = true)
                    try {
                        val backup = net.bible.android.control.backup.BackupControl(
                            get(net.bible.sharedcore.ui.dialog.AppDialogController::class.java),
                            get(net.bible.sharedcore.platform.CoreStrings::class.java), env,
                            get(net.bible.sharedcore.platform.AppSettings::class.java),
                            get(net.bible.android.control.page.window.WindowControl::class.java), cloud,
                            get(net.bible.sharedcore.platform.UserNotifier::class.java),
                        )
                        backup.restoreSelectedFiles(listOf(AiSettingsDatabase.dbFileName), folder,
                            object : net.bible.android.control.backup.BackupControl.RestorePrompts {
                                override suspend fun restoreOrImport(category: SyncableDatabaseDefinition, backupFile: File) = true
                                override suspend fun confirmOverwrite(category: SyncableDatabaseDefinition) = true
                            })
                    } finally { folder.deleteRecursively() }
                } else if (path == "nested") {
                    DatabaseContainer.replacingDatabases { token ->
                        cloud.swapInInitialDb(target, downloaded, token) {
                            assertTrue("outer owner remains active", DatabaseContainer.replacing)
                        }
                    }
                } else {
                    try {
                        cloud.swapInInitialDb(target, downloaded) {
                            replacementEntered.complete(Unit)
                            if (path == "unexpected") throw probe!!.failure
                            if (path == "failure") throw IllegalArgumentException("afterReset failure")
                            if (path == "cancel") awaitCancellation()
                            if (path == "concurrent") {
                                val second = File.createTempFile("ai-second-swap-", ".sqlite3")
                                target.localDbFile.copyTo(second, overwrite = true)
                                try {
                                    laterSwap = async(Dispatchers.IO + boundary.ownerLabel.asContextElement("second-replacement")) {
                                        try { cloud.swapInInitialDb(target, second) { boundary.event("second-replacement-body") } }
                                        finally { second.delete() }
                                    }
                                    withTimeout(3_000) { boundary.secondAttempted.await() }
                                    laterSwap?.let { probe?.children?.add(it) }
                                    assertFalse("second real swap queues behind first owner", laterSwap!!.isCompleted)
                                    boundary.event("first-replacement-body-end")
                                } catch (e: Throwable) { second.delete(); throw e }
                            }
                            if (path == "queued") {
                                boundary.queueWriter.set(true)
                                settings.maxIterations = 43
                                withTimeout(3_000) { boundary.writerAttempted.await() }
                                assertEquals("writer cannot reach any DAO during replacement", 41,
                                    DatabaseContainer.instance.aiSettingsDb.globalAiSettingsDao().get()!!.maxIterations)
                                boundary.event("replacement-body-end")
                            }
                        }
                    } catch (e: IllegalArgumentException) { if (path != "failure") throw e }
                }
            }
            probe?.children?.add(swap)
            withTimeout(5_000) { boundary.attempted.await() }
            assertNotNull("replacement attempted while the same statement is held", boundary.active)
            boundary.release.countDown()
            trigger?.await()
            if (path == "cancel") { replacementEntered.await(); swap.cancelAndJoin() }
            else swap.await()
            laterSwap?.await()
            if (path == "concurrent") assertTrue(boundary.events.indexOf("first-replacement-body-end") <
                boundary.events.indexOf("second-replacement-body"))
            assertNull("timeout is a harness error, never a close-overlap witness", boundary.harnessFailure.get())
            assertEquals("same active native handle must never be closed", 0, boundary.unsafeCloses.get())
            val events = boundary.events.toList()
            fun index(prefix: String) = events.indexOfFirst { it.startsWith(prefix) }.also {
                assertTrue("missing $prefix in $events", it >= 0)
            }
            assertTrue(index("replacement-attempt") < index("hold-release"))
            assertTrue(index("delegate-step-exit") < index("active-statement-close"))
            // Room returns the statement to its cache at DAO close; native finalization is later.
            assertTrue(index("dao-statement-cleanup") < index("dao-completion"))
            assertTrue(index("dao-completion") < index("lease-release"))
            assertTrue(index("lease-release") < index("replacement-acquire"))
            assertTrue(index("replacement-acquire") < index("old-connection-close"))
            if (path == "ownerCancel") {
                owner.join()
                assertEquals(41, DatabaseContainer.instance.aiSettingsDb.globalAiSettingsDao().get()!!.maxIterations)
                return@runBlocking
            }
            if (path == "queued") {
                writerLauncher.join("ai-settings")
                assertEquals(43, DatabaseContainer.instance.aiSettingsDb.globalAiSettingsDao().get()!!.maxIterations)
                assertTrue(boundary.events.indexOf("replacement-body-end") <
                    boundary.events.indexOfFirst { it.startsWith("queued-writer-dao") })
            } else withTimeout(3_000) { while (settings.maxIterations != 41) yield() }
            DatabaseContainer.instance.aiSettingsDb.globalAiSettingsDao().set(GlobalAiSettings(maxIterations = 42))
            withTimeout(3_000) { while (settings.maxIterations != 42) yield() }
            settings.maxIterations = 43
            writerLauncher.join("ai-settings")
            assertEquals(43, DatabaseContainer.instance.aiSettingsDb.globalAiSettingsDao().get()!!.maxIterations)
        } catch (e: Throwable) {
            originalFailure = e
            throw e
        } finally {
            withContext(NonCancellable) {
                var cleanupFailure: Throwable? = null
                suspend fun cleanup(block: suspend () -> Unit) {
                    try { block() } catch (e: Throwable) {
                        if (cleanupFailure == null) cleanupFailure = e else cleanupFailure!!.addSuppressed(e)
                    }
                }
                boundary.release.countDown()
                // Even a failed runBlocking child cancels its caller; quiesce every retained user of
                // the decorated process-global mutex before mandatory restoration and database reset.
                cleanup { trigger?.cancelAndJoin() }
                cleanup { swap?.cancelAndJoin() }
                cleanup { laterSwap?.cancelAndJoin() }
                cleanup { owner.cancelAndJoin() }
                cleanup { restoreAdmission() }
                cleanup { downloaded.delete() }
                cleanup { DatabaseResetter.resetDatabase() }
                cleanupFailure?.let { failure ->
                    if (originalFailure == null) throw failure
                    originalFailure!!.addSuppressed(failure)
                }
            }
        }
    }

    private fun net.bible.android.database.SyncableRoomDatabase.globalDaoForTest() =
        (this as AiSettingsDatabase).globalAiSettingsDao()
}
