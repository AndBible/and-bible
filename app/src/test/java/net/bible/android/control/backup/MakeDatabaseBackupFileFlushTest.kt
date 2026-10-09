package net.bible.android.control.backup

import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
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
    private lateinit var repo: WindowRepository
    private var wasReady = false

    @Before fun setUp() {
        DatabaseResetter.resetDatabase()
        wasReady = DatabaseContainer.ready
        DatabaseContainer.ready = true // the backup flushes/vacuums only when the DB is ready
        CommonUtils.settings.setBoolean("first-time", false)
        repo = WindowRepository(CoroutineScope(Dispatchers.Main))
        CommonUtils.windowControl.windowRepository = repo
        repo.initialize()
    }

    @After fun tearDown() {
        DatabaseResetter.resetDatabase()
        DatabaseContainer.ready = wasReady
        repo.clear()
    }

    @Test fun aQueuedSettingIsInTheBackup() {
        val gate = CountDownLatch(1)
        val started = CountDownLatch(1)
        DatabaseContainer.instance.settingsScope.launch { started.countDown(); gate.await() }
        assertTrue(started.await(10, TimeUnit.SECONDS))
        val watchdog = Thread { Thread.sleep(500); gate.countDown() }.apply { isDaemon = true; start() }
        val zip = try {
            CommonUtils.settings.setString("backup-now", "included") // queued behind the gated task
            runBlocking { BackupControl.makeDatabaseBackupFile() }!!
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
