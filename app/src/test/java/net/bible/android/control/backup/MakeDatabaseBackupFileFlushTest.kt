package net.bible.android.control.backup

import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import net.bible.android.control.speak.SpeakControl
import net.bible.android.control.speak.SpeakPlatform
import net.bible.android.database.WorkspaceDatabase
import net.bible.service.device.speak.TextToSpeechServiceManager
import net.bible.test.KoinFactorySnapshot
import org.koin.core.context.GlobalContext
import org.koin.dsl.module
import kotlin.time.Duration.Companion.seconds
import net.bible.android.BibleApplication
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.control.page.window.WindowRepository
import net.bible.android.database.SettingsDatabase
import net.bible.service.common.CommonUtils
import net.bible.service.db.DatabaseContainer
import net.bible.test.DatabaseResetter
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.zip.ZipFile

/**
 * D1 final review M3: "backup now" flushes the settings store before it vacuums and zips, so a setting changed
 * just before is in the backup. The settings writer is held by a gated task, so the write is still queued when the
 * backup starts; without the flush the zipped settings file lacks it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class MakeDatabaseBackupFileFlushTest {
    private val backup by lazy { net.bible.test.testBackupControl() }
    private lateinit var repo: WindowRepository
    private var wasReady = false

    @Before fun setUp() {
        DatabaseResetter.resetDatabase()
        wasReady = DatabaseContainer.ready
        DatabaseContainer.ready = true // the backup flushes/vacuums only when the DB is ready
        CommonUtils.settings.setBoolean("first-time", false)
        repo = net.bible.test.testWindowRepository()
        CommonUtils.windowControl.windowRepository = repo
        repo.initializeAtEdge()
    }

    @After fun tearDown() {
        DatabaseResetter.resetDatabase()
        DatabaseContainer.ready = wasReady
        repo.clear()
    }

    @Test fun backgroundBackupCleansUpStoppedSpeechOnMainBeforeWritingArchive() =
        backgroundBackupCleansUpSpeechOnMainBeforeWritingArchive(active = false)

    @Test fun backgroundBackupCleansUpActiveSpeechOnMainBeforeWritingArchive() =
        backgroundBackupCleansUpSpeechOnMainBeforeWritingArchive(active = true)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private fun backgroundBackupCleansUpSpeechOnMainBeforeWritingArchive(active: Boolean) {
        val koin = GlobalContext.get()
        val managerFactory = KoinFactorySnapshot.of<TextToSpeechServiceManager>(koin)
        val mainThread = Thread.currentThread()
        val cleanupCalls = java.util.concurrent.atomic.AtomicInteger()
        val engine = net.bible.service.device.speak.FakeSpeakEngine(mapOf(
            java.util.Locale.ENGLISH to net.bible.sharedcore.platform.speak.SpeakLanguageResult.AVAILABLE,
            java.util.Locale("fi", "FI") to net.bible.sharedcore.platform.speak.SpeakLanguageResult.AVAILABLE,
            java.util.Locale("fi") to net.bible.sharedcore.platform.speak.SpeakLanguageResult.AVAILABLE,
        ))
        val platform = object : SpeakPlatform by net.bible.service.device.speak.FakeSpeakPlatform(engine, autoInit = true) {
            override fun createEngine(listener: net.bible.sharedcore.platform.speak.SpeakEngineListener): net.bible.sharedcore.platform.speak.SpeakEngine {
                listener.onInit(true)
                return object : net.bible.sharedcore.platform.speak.SpeakEngine by engine {
                    override fun stop() {
                        check(Thread.currentThread() === mainThread) { "Speech engine stop must run on Main" }
                        engine.stop()
                    }
                    override fun shutdown() {
                        check(Thread.currentThread() === mainThread) { "Speech engine shutdown must run on Main" }
                        engine.shutdown()
                    }
                }
            }
            override fun abandonAudioFocus() {
                check(Thread.currentThread() === mainThread) {
                    "Backup speech cleanup must run on Main, not ${Thread.currentThread().name}"
                }
                cleanupCalls.incrementAndGet()
            }
        }
        val dispatcher = StandardTestDispatcher()
        var ownedControl: SpeakControl? = null
        Dispatchers.setMain(dispatcher)
        try {
            koin.loadModules(listOf(module {
                single {
                    TextToSpeechServiceManager(
                        get(), get(), get(), get(), platform, get(), get(), get(), get(), get(),
                        { DatabaseContainer.instance.myDocumentDb.myDocumentDao() },
                        net.bible.test.testSwordContentFacade(),
                    )
                }
            }))
            // Resolve every dependency before entering IO: an init failure is not the regression.
            assertTrue("The live snapshot branch must be enabled", CommonUtils.initialized && DatabaseContainer.ready)
            val manager = koin.get<TextToSpeechServiceManager>()
            val control = repo.speakControl
            ownedControl = control
            assertTrue(control.isStopped)
            assertTrue(control === koin.get<SpeakControl>())
            control.stop()
            assertEquals(1, cleanupCalls.get())
            cleanupCalls.set(0)
            repo.name = "Background backup snapshot"

            runTest(dispatcher, timeout = 30.seconds) {
                if (active) {
                    val book = org.crosswire.jsword.book.Books.installed().getBook("FinRK") as org.crosswire.jsword.book.sword.SwordBook
                    val verse = (book.getKey("Rom.1.1") as org.crosswire.jsword.passage.RangedPassage).getVerseAt(0)
                    control.speakBible(book, verse)
                    koin.get<net.bible.sharedcore.platform.OrderedLauncher>().join(net.bible.android.control.speak.SPEAK_KEY)
                    assertTrue("The active cleanup case must really be speaking", control.isSpeaking)
                    assertTrue("The real speech manager must have queued speech", engine.calls.any { it.startsWith("speak:") })
                    cleanupCalls.set(0)
                    engine.calls.clear()
                }
                val zip = withContext(Dispatchers.IO) {
                    assertTrue("Backup must enter from a distinct worker", Thread.currentThread() !== mainThread)
                    backup.makeDatabaseBackupFile()
                }!!.toFile()
                assertEquals("Backup must release speech focus", 1, cleanupCalls.get())
                if (active) {
                    assertEquals("Active backup must stop and dispose the engine", listOf("stop", "shutdown"), engine.calls)
                }
                assertTrue(control.isStopped)
                assertTrue(!manager.isSpeaking && !manager.isPaused)
                val extracted = File(BibleApplication.application.cacheDir, "backup-workspace.sqlite3")
                try {
                    ZipFile(zip).use { archive ->
                        val entry = archive.getEntry("db/${WorkspaceDatabase.dbFileName}")
                        assertTrue("Backup must contain the live workspace database", entry != null)
                        archive.getInputStream(entry).use { input ->
                            extracted.outputStream().use { input.copyTo(it) }
                        }
                    }
                    val name = SQLiteDatabase.openDatabase(extracted.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                        db.rawQuery("SELECT name FROM Workspace WHERE name = 'Background backup snapshot'", null).use { cursor ->
                            if (cursor.moveToFirst()) cursor.getString(0) else null
                        }
                    }
                    assertEquals("Background backup snapshot", name)
                } finally {
                    extracted.delete()
                    zip.delete()
                }
            }
        } finally {
            try {
                ownedControl?.stop()
            } finally {
                managerFactory.restore()
                Dispatchers.resetMain()
            }
        }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test fun aQueuedSettingIsInTheBackup() {
        val dispatcher = StandardTestDispatcher()
        Dispatchers.setMain(dispatcher)
        try {
            runTest(dispatcher, timeout = 30.seconds) { queuedSettingIsInTheBackup() }
        } finally {
            Dispatchers.resetMain()
        }
    }

    private suspend fun queuedSettingIsInTheBackup() {
        val gate = CountDownLatch(1)
        val started = CountDownLatch(1)
        DatabaseContainer.instance.settingsScope.launch { started.countDown(); gate.await() }
        assertTrue(started.await(10, TimeUnit.SECONDS))
        val watchdog = Thread { Thread.sleep(500); gate.countDown() }.apply { isDaemon = true; start() }
        val zip = try {
            CommonUtils.settings.setString("backup-now", "included") // queued behind the gated task
            withContext(Dispatchers.IO) { backup.makeDatabaseBackupFile() }!!.toFile()
        } finally {
            gate.countDown(); watchdog.join()
        }
        val extracted = File(BibleApplication.application.cacheDir, "backup-settings.sqlite3").apply { delete() }
        ZipFile(zip).use { z ->
            z.getInputStream(z.getEntry("db/${SettingsDatabase.dbFileName}")).use { input -> extracted.outputStream().use { input.copyTo(it) } }
        }
        val v = SQLiteDatabase.openDatabase(extracted.path, null, SQLiteDatabase.OPEN_READONLY).use {
            it.rawQuery("SELECT value FROM StringSetting WHERE `key`='backup-now'", null).use { c -> if (c.moveToFirst()) c.getString(0) else null }
        }
        extracted.delete()
        assertEquals("included", v)
    }
}
