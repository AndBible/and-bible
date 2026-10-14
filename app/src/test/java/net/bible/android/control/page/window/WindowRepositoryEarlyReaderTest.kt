package net.bible.android.control.page.window

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.control.bookmark.BookmarkControl
import net.bible.android.control.page.ChapterVerse
import net.bible.android.database.IdType
import net.bible.android.database.WorkspaceEntities
import net.bible.service.common.CommonUtils
import net.bible.service.db.DatabaseContainer
import net.bible.sharedcore.platform.OrderedLauncher
import net.bible.test.DatabaseResetter
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicBoolean

internal val workspaceDao get() = DatabaseContainer.instance.workspaceDb.workspaceDao()

internal fun testPersistence(gate: GateDispatcher? = null) = object : WorkspacePersistence(
    OrderedLauncher(CoroutineScope(SupervisorJob() + (gate ?: Dispatchers.Default))),
    { DatabaseContainer.instance.workspaceDb.workspaceDao() },
) {
    /** Whoever waits for persistence lets held writes through first: a missing wait never terminates the hold. */
    override suspend fun awaitPersisted() { gate?.release(); super.awaitPersisted() }
}

internal fun realLoader(persistence: WorkspacePersistence) = WorkspaceLoader(
    persistence, { workspaceDao },
    org.koin.java.KoinJavaComponent.get<BookmarkControl>(BookmarkControl::class.java), "no-journal",
)

/** Common fixture: a ready database, no workspace-level first-run work, and a repository wired to a controlled persistence. */
internal class Fixture(gate: GateDispatcher? = null) {
    val wasReady = DatabaseContainer.ready
    val wasInitialized = CommonUtils.initialized
    val persistence = testPersistence(gate)
    val repo: WindowRepository

    init {
        DatabaseResetter.resetDatabase()
        DatabaseContainer.ready = true
        CommonUtils.initialized = true
        CommonUtils.settings.setBoolean("first-time", false)
        repo = WindowRepository(CoroutineScope(Dispatchers.Main))
        repo.persistence = persistence
        CommonUtils.windowControl.windowRepository = repo
    }

    fun close() {
        try {
            runBlocking { persistence.awaitPersisted() }
        } finally {
            try { DatabaseResetter.resetDatabase() } finally {
                DatabaseContainer.ready = wasReady
                CommonUtils.initialized = wasInitialized
                repo.clear()
            }
        }
    }
}

/** A loader whose read stops until the test lets it go, and counts how often it is asked. */
private class HoldingLoader(real: WorkspaceLoader, persistence: WorkspacePersistence) :
    WorkspaceLoader(persistence, { workspaceDao },
        org.koin.java.KoinJavaComponent.get<BookmarkControl>(BookmarkControl::class.java), "no-journal") {
    val entered = CompletableDeferred<Unit>()
    val release = CompletableDeferred<Unit>()
    @Volatile var calls = 0
    private val delegate = real
    override suspend fun read(workspaceId: IdType?, createIfMissing: Boolean): LoadedWorkspace {
        calls++
        entered.complete(Unit)
        release.await()
        return delegate.read(workspaceId, createIfMissing)
    }
}

/**
 * Before the first load finishes, an [WindowRepository.activeWindow] reader must neither crash nor
 * start a second load (the 340-nested-loads ANR of `loadingFromDb`'s kdoc), and nothing may be
 * saved from a repository that has not loaded.
 *
 * Step 1 measurement (see the task 11 report): the getter keeps a synchronous edge, because
 * services, media buttons and agent tools read `activeWindow` with no UI host to finish a load first.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class WindowRepositoryEarlyReaderTest {
    private lateinit var f: Fixture

    @Before fun setUp() { f = Fixture() }
    @After fun tearDown() { f.close() }

    /** Breaks if a reader during the read phase starts its own load, or throws instead of joining the one in flight. */
    @Test fun readerDuringReadPhaseJoinsTheLoadInFlight() {
        val loader = HoldingLoader(realLoader(f.persistence), f.persistence)
        f.repo.loaderOverride = loader
        runBlocking {
            val load = launch { f.repo.loadFromDb(null) }
            withTimeout(10_000) { loader.entered.await() }
            assertFalse("the load has not finished", f.repo.initialized)
            Thread { loader.release.complete(Unit) }.start()
            val window = f.repo.activeWindow
            assertNotNull(window)
            withTimeout(10_000) { load.join() }
        }
        assertEquals("the reader must not cause a second read", 1, loader.calls)
        assertTrue(f.repo.initialized)
    }

    /** Breaks if the reader's apply and the suspended load's apply both run (each clears and re-appends). */
    @Test fun afterTheLoadWindowListHasNoDuplicateIds() {
        val loader = HoldingLoader(realLoader(f.persistence), f.persistence)
        f.repo.loaderOverride = loader
        runBlocking {
            val load = launch { f.repo.loadFromDb(null) }
            withTimeout(10_000) { loader.entered.await() }
            Thread { loader.release.complete(Unit) }.start()
            f.repo.activeWindow
            withTimeout(10_000) { load.join() }
        }
        val ids = f.repo.windowList.map { it.id }
        assertTrue("a loaded workspace has a window", ids.isNotEmpty())
        assertEquals(ids.distinct(), ids)
    }

    /** Pins the guard: a save before any load writes nothing (and does not touch the unset `savedEntity`). */
    @Test fun saveBeforeLoadWritesNothing() {
        val before = runBlocking { workspaceDao.allWorkspaces().map { it.id } }
        f.repo.saveIntoDb(false)
        runBlocking { f.persistence.awaitPersisted() }
        assertFalse(f.repo.initialized)
        assertEquals(before, runBlocking { workspaceDao.allWorkspaces().map { it.id } })
        assertEquals(null, runBlocking { workspaceDao.workspace(IdType.empty()) })
    }

    /**
     * Review fix (Task 10 M3): the first start of a repository that finds no workspace creates one. During a
     * restore the create insert used to be skipped by the stale-write guard, leaving an in-memory-only workspace
     * whose window inserts then failed their foreign key.
     */
    @Test fun firstStartDuringARestoreStillWritesTheCreatedWorkspace() {
        runBlocking { workspaceDao.allWorkspaces().forEach { workspaceDao.deleteWorkspace(it.id) } }
        CommonUtils.settings.setString("current_workspace_id", null)
        runBlocking {
            DatabaseContainer.replacingDatabases { f.repo.initialize() }
        }
        assertTrue(f.repo.initialized)
        assertNotNull("the created workspace has a row", runBlocking { workspaceDao.workspace(f.repo.id) })
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class WorkspaceRestoreDrainTest {
    /**
     * Review fix (Task 10 M7): a write that already passed its in-block guard may be mid-DAO-call when a restore
     * starts; the restore must wait for it before it touches the files. Breaks if the restore enters its block
     * while that write is still running.
     */
    @Test fun restoreWaitsForAWriteAlreadyInFlight() {
        DatabaseResetter.resetDatabase()
        try {
            val persistence = org.koin.java.KoinJavaComponent.get<WorkspacePersistence>(WorkspacePersistence::class.java)
            val entered = CompletableDeferred<Unit>()
            val proceed = CompletableDeferred<Unit>()
            val writeDone = AtomicBoolean(false)
            val writeDoneAtRestoreEntry = AtomicBoolean(false)
            persistence.enqueue { entered.complete(Unit); proceed.await(); writeDone.set(true) }
            runBlocking { withTimeout(10_000) { entered.await() } }
            val restore = CoroutineScope(Dispatchers.Default).launch {
                DatabaseContainer.replacingDatabases { writeDoneAtRestoreEntry.set(writeDone.get()) }
            }
            val deadline = System.nanoTime() + 10_000_000_000L
            while (!DatabaseContainer.replacing && System.nanoTime() < deadline) Thread.yield()
            assertTrue("the restore started", DatabaseContainer.replacing)
            proceed.complete(Unit)
            runBlocking { withTimeout(10_000) { restore.join() } }
            assertTrue("the restore must not enter before the in-flight write finished", writeDoneAtRestoreEntry.get())
        } finally {
            DatabaseResetter.resetDatabase()
        }
    }
}
