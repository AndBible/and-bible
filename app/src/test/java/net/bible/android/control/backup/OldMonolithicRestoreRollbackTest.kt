package net.bible.android.control.backup

import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import net.bible.android.BibleApplication
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.control.page.window.WindowRepository
import net.bible.android.database.BookmarkDatabase
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
    private val backup by lazy { net.bible.test.testBackupControl() }
    private lateinit var repo: WindowRepository
    private val app get() = BibleApplication.application
    private val dbDir: File get() = app.getDatabasePath(OLD_MONOLITHIC_DATABASE_NAME).parentFile!!
    private val rollbackParent: File get() = dbDir.parentFile!!
    private val rollbackDir: File get() = File(rollbackParent, "db-restore-rollback")
    private val realCopy = backup.copyStagedDatabase
    private var wasReady = false

    @Before fun setUp() {
        // FileManager.copyFile refuses under Robolectric (StatFs reports 0 free bytes); copy plainly instead.
        backup.copyStagedDatabase = { from, to -> from.copyTo(to, overwrite = true); true }
        DatabaseResetter.resetDatabase()
        wasReady = DatabaseContainer.ready
        DatabaseContainer.ready = true // the restore opens and migrates only when the DB is ready
        CommonUtils.settings.setBoolean("first-time", false)
        repo = WindowRepository(CoroutineScope(Dispatchers.Main))
        CommonUtils.windowControl.windowRepository = repo
        repo.initializeAtEdge()
        repo.name = "Before restore"
        repo.saveIntoDb(false)
        DatabaseContainer.reset() // close + flush, so the files can be fingerprinted
    }

    @After fun tearDown() {
        backup.copyStagedDatabase = realCopy
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

    private fun restore(f: File = fixture()) = runBlocking { backup.restoreOldMonolithicDatabase(f.inputStream()) }

    @Test fun aFailedCopyLeavesThePreRestoreDatabasesAsTheyWere() {
        val before = fingerprint()
        assertTrue("the setup must have written databases", before.isNotEmpty())
        backup.copyStagedDatabase = { _, _ -> false }
        assertFalse(restore())
        assertEquals(before, fingerprint())
        assertFalse(rollbackDir.exists())
        assertEquals("Before restore", runBlocking { DatabaseContainer.instance.workspaceDb.workspaceDao().workspace(repo.id) }?.name)
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

    /**
     * The split databases are written at version 1 and Room migrates each one on its first access, which
     * is after the container opens. Here the container opens fine but one database cannot be opened at its
     * first access (stands in for a throwing Room migration): the restore must roll back, not lose the data.
     */
    @Test fun aMigrationThatFailsOnFirstAccessAfterTheContainerOpensRollsBackToo() {
        val before = fingerprint()
        val real = DatabaseContainer.containerFactory
        val ok = try {
            DatabaseContainer.containerFactory = {
                File(dbDir, OLD_MONOLITHIC_DATABASE_NAME).delete() // stands in for DatabaseSplitMigrations
                val container = real()
                container.bookmarkDb.close() // First access fails on this real closed Room database.
                File(dbDir, BookmarkDatabase.dbFileName).writeText("this is not a database")
                container
            }
            runCatching { restore() }.getOrElse { throw AssertionError("must return false, not throw", it) }
        } finally { DatabaseContainer.containerFactory = real }
        assertFalse(ok)
        assertEquals(before, fingerprint())
        assertFalse(rollbackDir.exists())
        assertEquals("Before restore", runBlocking { DatabaseContainer.instance.workspaceDb.workspaceDao().workspace(repo.id) }?.name)
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
        val before = fingerprint()
        rollbackDir.mkdirs(); File(rollbackDir, "workspaces.sqlite3").writeText("old")
        backup.copyStagedDatabase = { _, _ -> false }
        restore()
        val kept = rollbackParent.listFiles()!!.filter { it.name.startsWith("db-restore-rollback-") }
        assertEquals(1, kept.size)
        assertEquals("old", File(kept.single(), "workspaces.sqlite3").readText())
        assertEquals("the pre-restore databases are back", before, fingerprint())
        assertFalse(rollbackDir.exists())
    }

    @Test fun syncCanAcquireReplacementWhileLegacyRestoreWaitsForIt() = runBlocking {
        val cloud = org.koin.core.context.GlobalContext.get().get<net.bible.service.cloudsync.CloudSync>()
        val adapterField = cloud.javaClass.getDeclaredField("_adapter").apply { isAccessible = true }
        val startedField = cloud.javaClass.getDeclaredField("syncStarted").apply { isAccessible = true }
        val originalAdapter = adapterField.get(cloud)
        val originalStarted = startedField.get(cloud)
        val syncFinished = kotlinx.coroutines.CompletableDeferred<Boolean>()
        val waiting = kotlinx.coroutines.CompletableDeferred<Unit>()
        val adapter = java.lang.reflect.Proxy.newProxyInstance(
            net.bible.service.cloudsync.CloudAdapter::class.java.classLoader,
            arrayOf(net.bible.service.cloudsync.CloudAdapter::class.java),
        ) { _, method, _ ->
            if (method.name == "getSignedIn") { waiting.complete(Unit); true }
            else error("Unexpected adapter operation: ${method.name}")
        }
        val source = fixture()
        backup.copyStagedDatabase = { _, _ -> false }
        var restore: kotlinx.coroutines.Deferred<Boolean>? = null
        try {
            adapterField.set(cloud, adapter)
            startedField.set(cloud, syncFinished)
            // The real beforeRestore suspends in CloudSync.waitUntilFinished.
            restore = async(kotlinx.coroutines.Dispatchers.IO) {
                backup.restoreOldMonolithicDatabase(source.inputStream())
            }
            kotlinx.coroutines.withTimeout(2_000) { waiting.await() }
            kotlinx.coroutines.withTimeout(2_000) {
                DatabaseContainer.replacingDatabases { syncFinished.complete(true) }
                assertFalse(restore.await())
            }
        } finally {
            syncFinished.complete(true)
            restore?.cancel()
            restore?.join()
            adapterField.set(cloud, originalAdapter)
            startedField.set(cloud, originalStarted)
        }
        Unit
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
