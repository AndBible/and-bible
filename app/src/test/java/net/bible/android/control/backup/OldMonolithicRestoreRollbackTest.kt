package net.bible.android.control.backup

import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import net.bible.android.BibleApplication
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.control.page.window.WindowRepository
import net.bible.service.common.CommonUtils
import net.bible.service.db.DatabaseContainer
import net.bible.service.db.OLD_MONOLITHIC_DATABASE_NAME
import net.bible.test.DatabaseResetter
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.security.MessageDigest

/**
 * F120 (fix batch 7 §1.1): a failed old-format restore must leave the user's databases exactly as they
 * were, instead of the empty ones Room creates after the old delete-then-copy.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class OldMonolithicRestoreRollbackTest {
    private lateinit var repo: WindowRepository
    private val app get() = BibleApplication.application
    private val dbDir: File get() = app.getDatabasePath(OLD_MONOLITHIC_DATABASE_NAME).parentFile!!
    private val rollbackParent: File get() = dbDir.parentFile!!
    private val rollbackDir: File get() = File(rollbackParent, "db-restore-rollback")
    private val realCopy = BackupControl.copyStagedDatabase
    private val realReadVersion = BackupControl.readDatabaseVersion
    private var wasReady = false

    @Before fun setUp() {
        // FileManager.copyFile refuses under Robolectric (StatFs reports 0 free bytes); copy plainly instead.
        BackupControl.copyStagedDatabase = { from, to -> from.copyTo(to, overwrite = true); true }
        // requery's SQLite is not on the unit-test classpath; read the fixture's version with the framework's.
        BackupControl.readDatabaseVersion = { f ->
            SQLiteDatabase.openDatabase(f.path, null, SQLiteDatabase.OPEN_READONLY).use { it.version }
        }
        DatabaseResetter.resetDatabase()
        wasReady = DatabaseContainer.ready
        DatabaseContainer.ready = true // the restore opens and migrates only when the DB is ready
        CommonUtils.settings.setBoolean("first-time", false)
        repo = WindowRepository(CoroutineScope(Dispatchers.Main))
        CommonUtils.windowControl.windowRepository = repo
        repo.initialize()
        repo.name = "Before restore"
        repo.saveIntoDb(false)
        DatabaseContainer.reset() // close + flush, so the files can be fingerprinted
    }

    @After fun tearDown() {
        BackupControl.copyStagedDatabase = realCopy
        BackupControl.readDatabaseVersion = realReadVersion
        DatabaseResetter.resetDatabase()
        DatabaseContainer.ready = wasReady
        repo.clear()
        rollbackParent.listFiles()!!.filter { it.name.startsWith("db-restore-rollback") }.forEach { it.deleteRecursively() }
    }

    private fun fixture(version: Int = 69): File {
        val f = File(app.cacheDir, "monolithic-fixture.db").apply { delete() }
        SQLiteDatabase.openOrCreateDatabase(f, null).use {
            it.execSQL("CREATE TABLE Dummy(id INTEGER PRIMARY KEY)")
            it.version = version
        }
        return f
    }

    private fun fingerprint(): Map<String, String> = dbDir.listFiles()!!.filter { it.isFile }.associate { f ->
        f.name to MessageDigest.getInstance("SHA-256").digest(f.readBytes()).joinToString("") { "%02x".format(it) }
    }

    private fun restore(f: File = fixture()) = runBlocking { BackupControl.restoreOldMonolithicDatabase(f.inputStream()) }

    @Test fun aFailedCopyLeavesThePreRestoreDatabasesAsTheyWere() {
        val before = fingerprint()
        assertTrue("the setup must have written databases", before.isNotEmpty())
        BackupControl.copyStagedDatabase = { _, _ -> false }
        assertFalse(restore())
        assertEquals(before, fingerprint())
        assertFalse(rollbackDir.exists())
        assertEquals("Before restore", DatabaseContainer.instance.workspaceDb.workspaceDao().workspace(repo.id)?.name)
    }

    @Test fun aMigrationThatThrowsRollsBackToo() {
        val before = fingerprint()
        val real = DatabaseContainer.containerFactory
        val ok = try {
            DatabaseContainer.containerFactory = { error("migration failed") }
            runCatching { restore() }.getOrElse { throw AssertionError("must return false, not throw", it) }
        } finally { DatabaseContainer.containerFactory = real }
        assertFalse(ok)
        assertEquals(before, fingerprint())
        assertFalse(rollbackDir.exists())
    }

    /** C1: the containerFactory stands in for DatabaseSplitMigrations, which consumes the old file. */
    @Test fun aGoodRestoreSwapsTheFileInAndLeavesNoStagingOrRollback() {
        val f = fixture()
        var seenSize = -1L
        val real = DatabaseContainer.containerFactory
        try {
            DatabaseContainer.containerFactory = {
                val mono = File(dbDir, OLD_MONOLITHIC_DATABASE_NAME)
                if (mono.exists()) { seenSize = mono.length(); mono.delete() }
                real()
            }
            assertTrue(restore(f))
        } finally { DatabaseContainer.containerFactory = real }
        assertEquals(f.length(), seenSize)
        assertFalse(File(dbDir, "$OLD_MONOLITHIC_DATABASE_NAME.staging").exists())
        assertFalse(rollbackDir.exists())
    }

    /** Review Focus 1. */
    @Test fun aLeftoverRollbackFromAnEarlierCrashIsKeptAside() {
        rollbackDir.mkdirs(); File(rollbackDir, "workspaces.sqlite3").writeText("old")
        BackupControl.copyStagedDatabase = { _, _ -> false }
        restore()
        val kept = rollbackParent.listFiles()!!.filter { it.name.startsWith("db-restore-rollback-") }
        assertEquals(1, kept.size)
        assertEquals("old", File(kept.single(), "workspaces.sqlite3").readText())
    }

    /** Review Focus 2. */
    @Test fun aTooNewFileChangesNothing() {
        val before = fingerprint()
        assertFalse(restore(fixture(version = 70)))
        assertEquals(before, fingerprint())
    }

    /** Review Focus 3. */
    @Test fun aRestoreIntoAnEmptyDatabaseDirWorks() {
        dbDir.listFiles()!!.filter { it.isFile }.forEach { it.delete() }
        val real = DatabaseContainer.containerFactory
        try {
            DatabaseContainer.containerFactory = { File(dbDir, OLD_MONOLITHIC_DATABASE_NAME).delete(); real() }
            assertTrue(restore())
        } finally { DatabaseContainer.containerFactory = real }
        assertFalse(rollbackDir.exists())
    }
}
