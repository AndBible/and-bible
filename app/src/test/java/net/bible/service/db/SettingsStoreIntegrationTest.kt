package net.bible.service.db

import android.database.sqlite.SQLiteDatabase
import net.bible.android.BibleApplication.Companion.application
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.database.SettingsDatabase
import net.bible.service.common.CommonUtils
import org.junit.After
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import kotlinx.coroutines.launch
import net.bible.service.common.DisplayColorMode
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class SettingsStoreIntegrationTest {
    /** Other classes in the one-JVM suite may leave a container built in a dead Robolectric data dir; start clean. */
    @Before fun setUp() { DatabaseContainer.reset() }

    @After fun tearDown() { DatabaseContainer.reset() }

    private fun rawSql(sql: String) {
        val f = application.getDatabasePath(SettingsDatabase.dbFileName)
        SQLiteDatabase.openDatabase(f.path, null, 0).use { it.execSQL(sql) }
    }

    @Test fun settingWrittenBeforeCloseAllSurvivesReopen() {
        CommonUtils.settings.setString("d1-key", "v1")
        DatabaseContainer.reset() // closeAll() must flush first
        assertEquals("v1", CommonUtils.settings.getString("d1-key", null))
        assertEquals("v1", DatabaseContainer.instance.settingsDb.stringSettingDao().byKey("d1-key")?.value)
    }

    @Test fun restoredSettingsFileIsReadAfterReset() {
        CommonUtils.settings.setString("d1-key", "before")
        DatabaseContainer.reset()
        // simulate a restore: write a different value straight into the file
        rawSql("UPDATE StringSetting SET value='after' WHERE `key`='d1-key'")
        DatabaseContainer.reset()
        assertEquals("after", CommonUtils.settings.getString("d1-key", null))
    }

    @Test fun intAndFloatRoundTripThroughLongAndDoubleTables() {
        CommonUtils.settings.setInt("i", 7); CommonUtils.settings.setFloat("f", 1.25f)
        DatabaseContainer.reset()
        assertEquals(7, CommonUtils.settings.getInt("i", 0))
        assertEquals(1.25f, CommonUtils.settings.getFloat("f", 0f))
    }

    @Test fun nullRemovesSettingAcrossReopen() {
        CommonUtils.settings.setString("gone", "x")
        DatabaseContainer.reset()
        CommonUtils.settings.setString("gone", null)
        DatabaseContainer.reset()
        assertEquals("dflt", CommonUtils.settings.getString("gone", "dflt"))
    }

    @Test fun resetCreatesANewStore() {
        val first = DatabaseContainer.instance.settingsStore
        DatabaseContainer.reset()
        assertNotSame(first, DatabaseContainer.instance.settingsStore)
    }

    /**
     * Restore/reset paths close the settings file via closeForReplace: queued writes must reach the OLD file first.
     * The writer thread is held by a gated task, so the write is genuinely still queued when closeForReplace runs;
     * the gate opens from a watchdog only after a delay. Without the flush closeForReplace returns at once and the
     * raw read below sees nothing.
     */
    @Test fun closeForReplaceFlushesQueuedSettingsBeforeClosing() {
        val container = DatabaseContainer.instance
        val gate = CountDownLatch(1)
        val started = CountDownLatch(1)
        container.settingsScope.launch { started.countDown(); gate.await() }
        assertTrue(started.await(10, TimeUnit.SECONDS))
        val watchdog = Thread { Thread.sleep(500); gate.countDown() }.apply { isDaemon = true; start() }
        try {
            CommonUtils.settings.setString("q", "queued") // queued behind the gated task
            container.closeForReplace(SettingsDatabase.dbFileName)
            val f = application.getDatabasePath(SettingsDatabase.dbFileName)
            val v = SQLiteDatabase.openDatabase(f.path, null, SQLiteDatabase.OPEN_READONLY).use {
                it.rawQuery("SELECT value FROM StringSetting WHERE `key`='q'", null).use { c -> if (c.moveToFirst()) c.getString(0) else null }
            }
            assertEquals("queued", v)
        } finally {
            gate.countDown(); watchdog.join()
        }
    }

    private val store get() = DatabaseContainer.instance.settingsStore

    @Test fun migrationTurnsMonochromeFalseIntoNormalColorMode() {
        store.setBoolean("monochrome_mode", false)
        CommonUtils.migrateOldSettingsKeys()
        assertEquals(DisplayColorMode.NORMAL.value, store.getString("display_color_mode", null))
        assertEquals("gone", if (store.getBoolean("monochrome_mode", false) || !store.getBoolean("monochrome_mode", true)) "present" else "gone")
    }

    @Test fun migrationLeavesColorModeUnsetWhenMonochromeKeyMissing() {
        CommonUtils.migrateOldSettingsKeys()
        assertNull(store.getString("display_color_mode", null))
    }

    @Test fun migrationTurnsMonochromeTrueIntoBwColorMode() {
        store.setBoolean("monochrome_mode", true)
        CommonUtils.migrateOldSettingsKeys()
        assertEquals(DisplayColorMode.BW.value, store.getString("display_color_mode", null))
    }

    @Test fun migrationRenamesBooleanAndLongKeys() {
        store.setBoolean("gdrive_bookmarks", true)
        store.setLong("gdrive_sync_interval", 42L)
        CommonUtils.migrateOldSettingsKeys()
        assertTrue(store.getBoolean("sync_enable_bookmarks", false))
        assertEquals(42L, store.getLong("cloud_sync_interval", -1L))
        assertEquals(-1L, store.getLong("gdrive_sync_interval", -1L))
        assertFalse(store.getBoolean("gdrive_bookmarks", false))
    }

    /** The zip-restore / reset-database path: close, replace the file, reset() -> next container reloads the replaced file. */
    @Test fun backupRestoreReplacementReloadsSettings() {
        CommonUtils.settings.setString("r", "old")
        DatabaseContainer.instance.closeForReplace(SettingsDatabase.dbFileName)
        rawSql("UPDATE StringSetting SET value='restored' WHERE `key`='r'")
        DatabaseContainer.reset()
        assertEquals("restored", CommonUtils.settings.getString("r", null))
    }

    @Test fun resetDatabaseReloadsSettings() {
        CommonUtils.settings.setString("r", "old")
        DatabaseContainer.instance.closeForReplace(SettingsDatabase.dbFileName)
        application.getDatabasePath(SettingsDatabase.dbFileName).delete()
        DatabaseContainer.reset()
        assertEquals("dflt", CommonUtils.settings.getString("r", "dflt"))
    }
}
