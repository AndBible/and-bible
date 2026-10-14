package net.bible.android.control.page.window

import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import net.bible.android.BibleApplication
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.control.page.ChapterVerse
import net.bible.android.database.IdType
import net.bible.android.database.WorkspaceDatabase
import net.bible.android.database.WorkspaceEntities
import net.bible.service.common.CommonUtils
import net.bible.service.db.DatabaseContainer
import net.bible.sharedcore.platform.OrderedLauncher
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.SupervisorJob
import net.bible.test.DatabaseResetter
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.zip.ZipFile

/**
 * Review Focus #1: a workspace save is an ordered write-through. The row a later load or a backup
 * reads is the value at enqueue time, not whatever the live page manager holds when the queue drains,
 * and a restore that starts after the enqueue skips the stale write.
 */
/**
 * Runs queued coroutine tasks only after [release]: while [hold] is in force the persistence queue is
 * frozen, so a test decides exactly what happens between enqueue and write. No sleeps, no racing.
 */
private class GateDispatcher : CoroutineDispatcher() {
    private val queue = ArrayList<Runnable>()
    private var held = false
    val pending: Int get() = synchronized(this) { queue.size }
    fun hold() = synchronized(this) { held = true }
    fun release() {
        val tasks = synchronized(this) { held = false; queue.toList().also { queue.clear() } }
        tasks.forEach { Dispatchers.Default.dispatch(EmptyCoroutineContext, it) }
    }
    override fun dispatch(context: CoroutineContext, block: Runnable) {
        val queued = synchronized(this) { if (held) { queue += block; true } else false }
        if (!queued) Dispatchers.Default.dispatch(context, block)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class WorkspacePersistenceOrderingTest {
    private lateinit var repo: WindowRepository
    private var wasReady = false
    private var wasInitialized = false
    private val gate = GateDispatcher()
    private val persistence get() = repo.persistence
    private val dao get() = DatabaseContainer.instance.workspaceDb.workspaceDao()

    @Before fun setUp() {
        DatabaseResetter.resetDatabase()
        wasReady = DatabaseContainer.ready
        DatabaseContainer.ready = true
        CommonUtils.settings.setBoolean("first-time", false)
        wasInitialized = CommonUtils.initialized
        CommonUtils.initialized = true
        repo = WindowRepository(CoroutineScope(Dispatchers.Main))
        repo.persistence = object : WorkspacePersistence(
            OrderedLauncher(CoroutineScope(SupervisorJob() + gate)),
            { DatabaseContainer.instance.workspaceDb.workspaceDao() },
        ) {
            /** Whoever waits for persistence lets the held writes through first: a missing wait never terminates the hold. */
            override suspend fun awaitPersisted() { gate.release(); super.awaitPersisted() }
        }
        CommonUtils.windowControl.windowRepository = repo
        repo.initialize()
        runBlocking { persistence.awaitPersisted() }
    }

    @After fun tearDown() {
        try {
            gate.release()
            runBlocking { persistence.awaitPersisted() }
        } finally {
            try {
                DatabaseResetter.resetDatabase()
            } finally {
                DatabaseContainer.ready = wasReady
                CommonUtils.initialized = wasInitialized
                repo.clear()
            }
        }
    }

    private fun savedVerseNo(): Int = runBlocking {
        dao.pageManager(repo.activeWindow.id)!!.biblePage.verse.verseNo
    }

    /** Breaks if the enqueued save is not drained before a later load reads the workspace. */
    @Test fun saveThenLoadSeesTheSave() {
        repo.activeWindow.pageManager.currentBible.currentChapterVerse = ChapterVerse(1, 7)
        repo.saveIntoDb(false)
        val id = repo.id
        runBlocking { persistence.awaitPersisted() }

        val loaded = WindowRepository(CoroutineScope(Dispatchers.Main))
        loaded.loadFromDb(id)
        assertEquals(7, loaded.activeWindow.pageManager.currentBible.currentBibleVerse.verse.verse)
        loaded.clear()
    }

    /**
     * Breaks if the queued write captures live objects instead of copies taken before
     * [WindowRepository.saveIntoDb] returns. The write is held while the live workspace (its settings list
     * is shared with the repository) and the page manager are mutated; the rows must hold the save-time state.
     */
    @Test fun entityMutatedAfterSaveDoesNotLeakIntoTheWrite() {
        val labelId = IdType()
        repo.activeWindow.pageManager.currentBible.currentChapterVerse = ChapterVerse(1, 7)
        repo.workspaceSettings.recentLabels.add(WorkspaceEntities.RecentLabel(labelId, 1L))
        gate.hold()
        repo.saveIntoDb(false)
        assertEquals("the write must still be queued", true, gate.pending > 0)
        repo.workspaceSettings.recentLabels.add(WorkspaceEntities.RecentLabel(IdType(), 2L))
        repo.activeWindow.pageManager.currentBible.currentChapterVerse = ChapterVerse(1, 9)
        gate.release()
        runBlocking { persistence.awaitPersisted() }
        val saved = runBlocking { dao.workspace(repo.id) }!!.workspaceSettings!!.recentLabels
        assertEquals(listOf(labelId), saved.map { it.labelId })
        assertEquals(7, savedVerseNo())
    }

    /**
     * Breaks if the restore guard is checked only when the save is enqueued: a restore that starts
     * while the write is still queued must not have the pre-restore state written over it.
     */
    @Test fun restoreStartedAfterEnqueueSkipsTheStaleWrite() {
        repo.name = "saved"
        repo.saveIntoDb(false)
        runBlocking { persistence.awaitPersisted() }

        gate.hold()
        repo.name = "stale"
        repo.saveIntoDb(false)
        assertEquals("the write must still be queued", true, gate.pending > 0)
        val restoreEntered = CompletableDeferred<Unit>()
        val releaseRestore = CompletableDeferred<Unit>()
        val restore = CoroutineScope(Dispatchers.Default).launch {
            DatabaseContainer.replacingDatabases {
                restoreEntered.complete(Unit)
                releaseRestore.await()
            }
        }
        try {
            runBlocking { withTimeout(5_000) { restoreEntered.await() } }
            gate.release()
            runBlocking { persistence.awaitPersisted() }
            assertEquals("saved", runBlocking { dao.workspace(repo.id) }!!.name)
        } finally {
            releaseRestore.complete(Unit)
            runBlocking { withTimeout(5_000) { restore.join() } }
        }
    }

    /**
     * Breaks if a reload between enqueue and write lets the stale write through: the epoch is captured
     * at enqueue, so a restore that finished and a reload that refreshed [WindowRepository]'s own epoch
     * in between must still skip it. (The reload's wait lets the held write run.)
     */
    @Test fun restoreAndReloadBeforeTheWriteStillSkipTheStaleWrite() {
        repo.name = "saved"
        repo.saveIntoDb(false)
        runBlocking { persistence.awaitPersisted() }

        gate.hold()
        repo.name = "stale"
        repo.saveIntoDb(false)
        assertEquals("the write must still be queued", true, gate.pending > 0)
        runBlocking { DatabaseContainer.replacingDatabases { } }
        repo.loadFromDb(repo.id)
        assertEquals("saved", runBlocking { dao.workspace(repo.id) }!!.name)
    }

    /** Breaks if the selector's load reads the workspace rows before the save queued just before it. */
    @Test fun selectorLoadRightAfterASaveSeesIt() {
        repo.name = "renamed"
        gate.hold()
        repo.saveIntoDb(false)
        assertEquals("the write must still be queued", true, gate.pending > 0)
        val rows = net.bible.android.view.activity.workspaces.WorkspaceServiceImpl(CommonUtils.windowControl).loadAll()
        assertEquals("renamed", rows.first { it.id == repo.id.toString() }.name)
    }

    /** Breaks if cloning copies page-manager rows before the save queued just before it. */
    @Test fun cloneRightAfterASaveCopiesIt() {
        repo.activeWindow.pageManager.currentBible.currentChapterVerse = ChapterVerse(1, 7)
        gate.hold()
        repo.saveIntoDb(false)
        assertEquals("the write must still be queued", true, gate.pending > 0)
        val service = net.bible.android.view.activity.workspaces.WorkspaceServiceImpl(CommonUtils.windowControl)
        val clone = service.cloneWorkspace(repo.id.toString(), "copy")
        val verse = runBlocking {
            val window = dao.windows(IdType(clone.id)).first()
            dao.pageManager(window.id)!!.biblePage.verse.verseNo
        }
        assertEquals(7, verse)
    }

    /** Breaks if the backup zips the database before the last enqueued save has landed. */
    @Test fun backupContainsTheLastSave() {
        val before = savedVerseNo()
        repo.activeWindow.pageManager.currentBible.currentChapterVerse = ChapterVerse(1, 11)
        assertNotEquals(before, 11)
        // The save inside the backup queues behind the held gate; only BackupControl's own wait lets it through.
        gate.hold()
        val zip = runBlocking { net.bible.test.testBackupControl().makeDatabaseBackupFile() }!!
        val extracted = File(BibleApplication.application.cacheDir, "backup-workspaces.sqlite3").apply { delete() }
        ZipFile(zip).use { z ->
            z.getInputStream(z.getEntry("db/${WorkspaceDatabase.dbFileName}")).use { input ->
                extracted.outputStream().use { input.copyTo(it) }
            }
        }
        val verseNo = SQLiteDatabase.openDatabase(extracted.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            db.rawQuery("SELECT bible_verse_verseNo FROM PageManager", null).use { c ->
                check(c.moveToFirst())
                c.getInt(0)
            }
        }
        extracted.delete()
        assertEquals(11, verseNo)
    }
}
