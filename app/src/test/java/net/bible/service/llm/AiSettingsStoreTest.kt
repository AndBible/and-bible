package net.bible.service.llm

import androidx.room3.useWriterConnection
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.control.backup.BackupControl
import net.bible.android.database.IdType
import net.bible.service.cloudsync.SyncableDatabaseDefinition
import net.bible.service.cloudsync.applyPatchesForDatabase
import net.bible.service.cloudsync.createPatchForDatabase
import net.bible.service.db.DatabaseContainer
import net.bible.service.db.exec
import net.bible.sharedcore.platform.AppCoroutineScope
import net.bible.sharedcore.platform.AppEnvironment
import net.bible.sharedcore.platform.OrderedLauncher
import net.bible.test.DatabaseResetter
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.java.KoinJavaComponent.get
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class AiSettingsStoreTest {
    private val job = SupervisorJob()
    private val scope = AppCoroutineScope(job + Dispatchers.Default)
    private val launcher = OrderedLauncher(scope)
    private val dao get() = DatabaseContainer.instance.aiSettingsDb.globalAiSettingsDao()
    private val accessor get() = DatabaseContainer.databaseAccessorsByCategory.getValue(SyncableDatabaseDefinition.AI_SETTINGS)
    private val env get() = net.bible.android.platform.AndroidAppEnvironment(net.bible.android.BibleApplication.application)
    private val files = mutableListOf<File>()
    private fun store(debug: Boolean = true) = AiSettingsStore({ dao }, launcher, scope,
        object : AppEnvironment by env { override val isDebugMode = debug }, DatabaseContainer.databaseRestored)

    @Before fun setUp() { DatabaseResetter.resetDatabase() }
    @After fun tearDown() = runBlocking {
        launcher.join("ai-settings")
        job.cancelAndJoin()
        DatabaseResetter.resetDatabase()
        files.forEach { it.delete() }
    }

    @Test fun aDebugReadRequiresExplicitLoading() {
        assertThrows(IllegalStateException::class.java) { store().maxIterations }
    }

    @Test fun releaseReadsDefaultsBeforeLoad() { assertEquals(10, store(false).maxIterations) }

    @Test fun coldLoadPreservesDisclaimerAndDefaultModel() = runBlocking {
        val id = IdType()
        dao.set(GlobalAiSettings(aiDisclaimerAccepted = true, defaultModelId = id))
        val settings = store()
        settings.ensureLoaded()
        settings.ensureLoaded()
        assertTrue(settings.aiDisclaimerAccepted)
        assertEquals(id, settings.defaultModelId)
    }

    @Test fun immediateSettersAccumulateAndPersistInOrderWithActiveObservation() = runBlocking {
        val settings = store()
        settings.ensureLoaded()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        launcher.launch("ai-settings") { entered.complete(Unit); release.await() }
        entered.await()
        try {
            settings.maxIterations = 22
            settings.aiDisclaimerAccepted = true
            // An older emission must not overwrite either pending setter.
            dao.set(GlobalAiSettings(maxIterations = 7))
            delay(100)
            assertEquals(22, settings.maxIterations)
            assertTrue(settings.aiDisclaimerAccepted)
        } finally { release.complete(Unit) }
        launcher.join("ai-settings")
        delay(100)
        assertEquals(22, dao.get()!!.maxIterations)
        assertTrue(dao.get()!!.aiDisclaimerAccepted)
        assertEquals(22, settings.maxIterations)
        assertTrue(settings.aiDisclaimerAccepted)
    }

    @Test fun anIntermediateOwnEmissionCannotReplaceNewerPendingSetters() = runBlocking {
        val firstWritten = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var writes = 0
        val settings = AiSettingsStore({
            val current = dao
            object : GlobalAiSettingsDao by current {
                override suspend fun set(settings: GlobalAiSettings) {
                    current.set(settings)
                    if (++writes == 1) { firstWritten.complete(Unit); release.await() }
                }
            }
        }, launcher, scope, env, DatabaseContainer.databaseRestored)
        settings.ensureLoaded()
        settings.maxIterations = 22
        settings.aiDisclaimerAccepted = true
        firstWritten.await()
        try {
            assertEquals(22, dao.get()!!.maxIterations)
            assertFalse(dao.get()!!.aiDisclaimerAccepted) // first FIFO write only
            delay(100)
            assertTrue(settings.aiDisclaimerAccepted)
            settings.askModelBeforeRun = true
            assertTrue(settings.aiDisclaimerAccepted)
        } finally { release.complete(Unit) }
        launcher.join("ai-settings")
        withTimeout(2_000) {
            while (!settings.askModelBeforeRun || !settings.aiDisclaimerAccepted) delay(10)
        }
        assertEquals(22, dao.get()!!.maxIterations)
        assertTrue(dao.get()!!.aiDisclaimerAccepted)
        assertTrue(dao.get()!!.askModelBeforeRun)
        assertEquals(3, writes)
    }

    @Test fun modelAndConfigEventsRemainSeparateAndReadTheNewSnapshot() = runBlocking {
        val settings = store()
        settings.ensureLoaded()
        val id = IdType()
        var models = 0
        var configs = 0
        val modelSub = settings.defaultModelChanged.subscribe { models++; assertEquals(id, settings.defaultModelId) }
        val configSub = settings.configChanged.subscribe { configs++ }
        try {
            settings.defaultModelId = id
            assertEquals(1, models)
            assertEquals(0, configs)
            settings.notifyConfigChanged()
            assertEquals(1, configs)
        } finally { modelSub.cancel(); configSub.cancel() }
    }

    @Test fun attachedSqlPatchIsVisibleToLoadedSettings() = runBlocking {
        // Generate a real AI_SETTINGS sync patch, then undo locally before attaching it.
        val target = accessor
        net.bible.service.cloudsync.createTriggers(target)
        dao.set(GlobalAiSettings(maxIterations = 7))
        val patch = createPatchForDatabase(target, env)!!.also { files += it }
        target.dao.setConfig(net.bible.service.cloudsync.TRIGGERS_DISABLED_KEY, true)
        dao.set(GlobalAiSettings(maxIterations = 10))
        target.localDb.useWriterConnection { it.exec("DELETE FROM LogEntry WHERE tableName = 'GlobalAiSettings'") }
        target.dao.setConfig(net.bible.service.cloudsync.TRIGGERS_DISABLED_KEY, false)
        val settings = store()
        settings.ensureLoaded()
        delay(100) // start the real Room collector before applying raw attached SQL
        assertEquals(10, settings.maxIterations)
        applyPatchesForDatabase(target, env, patch)
        assertEquals(7, dao.get()!!.maxIterations)
        withTimeout(2_000) { while (settings.maxIterations != 7) delay(10) }
    }

    @Test fun initialCloudSyncSwapResubscribesToReplacementDatabase() = runBlocking {
        val target = accessor
        dao.set(GlobalAiSettings(maxIterations = 41))
        target.localDb.useWriterConnection { it.exec("PRAGMA wal_checkpoint(TRUNCATE)") }
        val downloaded = File.createTempFile("ai-sync-swap-", ".sqlite3").also { files += it }
        target.localDbFile.copyTo(downloaded, overwrite = true)
        dao.set(GlobalAiSettings(maxIterations = 10))
        val settings = store()
        settings.ensureLoaded()
        get<net.bible.service.cloudsync.CloudSync>(net.bible.service.cloudsync.CloudSync::class.java)
            .swapInInitialDb(target, downloaded) { }
        withTimeout(2_000) { while (settings.maxIterations != 41) delay(10) }
        dao.set(GlobalAiSettings(maxIterations = 42))
        withTimeout(2_000) { while (settings.maxIterations != 42) delay(10) }
    }

    @Test fun restoredFileRebindsObservationAndWritesToTheNewDao() = runBlocking {
        val target = accessor
        dao.set(GlobalAiSettings(maxIterations = 31))
        target.localDb.useWriterConnection { it.exec("PRAGMA wal_checkpoint(TRUNCATE)") }
        val backup = File.createTempFile("ai-restored-", ".sqlite3").also { files += it }
        target.localDbFile.copyTo(backup, overwrite = true)
        dao.set(GlobalAiSettings(maxIterations = 10))
        val settings = store()
        settings.ensureLoaded()
        val folder = File(env.tmpDir, "ai-restore-test").apply { mkdirs() }
        val name = accessor.localDbFile.name
        File(folder, "db").mkdirs()
        backup.copyTo(File(folder, "db/$name"), overwrite = true)
        BackupControl(
            get(net.bible.sharedcore.ui.dialog.AppDialogController::class.java),
            get(net.bible.sharedcore.platform.CoreStrings::class.java), env,
            get(net.bible.sharedcore.platform.AppSettings::class.java),
            get(net.bible.android.control.page.window.WindowControl::class.java),
            get(net.bible.service.cloudsync.CloudSync::class.java),
            get(net.bible.sharedcore.platform.UserNotifier::class.java),
        ).restoreSelectedFiles(listOf(name), folder,
            object : BackupControl.RestorePrompts {
                override suspend fun restoreOrImport(category: SyncableDatabaseDefinition, backupFile: File) = true
                override suspend fun confirmOverwrite(category: SyncableDatabaseDefinition) = true
            })
        assertEquals("restored file row", 31, dao.get()!!.maxIterations)
        withTimeout(2_000) { while (settings.maxIterations != 31) delay(10) }
        dao.set(GlobalAiSettings(maxIterations = 32))
        withTimeout(2_000) { while (settings.maxIterations != 32) delay(10) }
        settings.maxIterations = 33
        launcher.join("ai-settings")
        assertEquals(33, dao.get()!!.maxIterations)
    }
}
