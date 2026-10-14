package net.bible.service.common

import androidx.room3.Room
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import net.bible.android.BibleApplication.Companion.application
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.database.progress.GlobalReadingProgressSettings
import net.bible.android.database.progress.ProgressDatabase
import net.bible.service.db.sqliteDriverFactory
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import net.bible.android.control.page.window.GateDispatcher
import net.bible.sharedcore.event.EventSource
import net.bible.sharedcore.platform.AppCoroutineScope
import net.bible.sharedcore.platform.OrderedLauncher
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The reading progress settings snapshot: synchronous reads and writes, ordered write-through to the DAO. */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class ReadingProgressSettingsStoreTest {
    private lateinit var db: ProgressDatabase
    private lateinit var launcher: OrderedLauncher
    private lateinit var store: ReadingProgressSettingsStore
    private val gate = GateDispatcher()
    private val restored = EventSource<Unit>()
    private val synced = EventSource<Unit>()
    /** A NEW flow per call, like `invalidationTracker.createFlow` on whichever database is current. */
    private val flows = java.util.concurrent.CopyOnWriteArrayList<MutableSharedFlow<Unit>>()
    private val invalidations get() = flows.first()
    private val observerScope = AppCoroutineScope()

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(application, ProgressDatabase::class.java).setDriver(sqliteDriverFactory()).build()
        launcher = OrderedLauncher(AppCoroutineScope(kotlinx.coroutines.SupervisorJob() + gate))
        store = ReadingProgressSettingsStore(launcher, { db.globalReadingProgressSettingsDao() }, restored, synced, observerScope) { MutableSharedFlow<Unit>().also { flows.add(it) } }
    }

    @After fun tearDown() { gate.release(); observerScope.coroutineContext[kotlinx.coroutines.Job]!!.cancel(); db.close() }

    private fun settings(strongs: Int) = GlobalReadingProgressSettings(activeCycle = strongs)
    private fun stored() = runBlocking { db.globalReadingProgressSettingsDao().get() }

    @Test fun loadMakesCurrentEqualTheDatabaseRow() = runBlocking {
        db.globalReadingProgressSettingsDao().set(settings(2))
        store.load()
        assertEquals(settings(2), store.current)
    }

    @Test fun setIsVisibleImmediatelyAndPersistedAfterTheLauncherDrains() = runBlocking {
        store.load()
        store.set(settings(1))
        assertEquals(settings(1), store.current)
        withTimeout(10_000) { launcher.join(ReadingProgressSettingsStore.KEY) }
        assertEquals(settings(1), stored())
    }

    @Test fun currentBeforeLoadIsTheDefault() {
        assertEquals(GlobalReadingProgressSettings(), store.current)
    }

    /** The first write is held until the second is queued, so an unordered launcher could end with `a`. */
    @Test fun lastOfTwoSetsWinsInTheDatabase() = runBlocking {
        store.load()
        gate.hold()
        store.set(settings(1))
        store.set(settings(2))
        gate.release()
        withTimeout(10_000) { launcher.join(ReadingProgressSettingsStore.KEY) }
        assertEquals(settings(2), stored())
        assertEquals(settings(2), store.current)
    }

    private fun writeBehindTheStore(value: GlobalReadingProgressSettings) = runBlocking {
        db.globalReadingProgressSettingsDao().set(value)
    }

    private suspend fun settle() = withTimeout(10_000) { launcher.join(ReadingProgressSettingsStore.KEY) }

    @Test fun snapshotFollowsADatabaseRestore() = runBlocking {
        store.load()
        store.set(settings(1)); settle()
        writeBehindTheStore(settings(7))
        restored.emit(Unit)
        gate.release(); settle()
        assertEquals(settings(7), store.current)
    }

    @Test fun snapshotFollowsACloudSyncRefresh() = runBlocking {
        store.load()
        writeBehindTheStore(settings(8))
        synced.emit(Unit)
        settle()
        assertEquals(settings(8), store.current)
    }

    @Test fun snapshotFollowsTableInvalidations() = runBlocking {
        store.load()
        withTimeout(10_000) { invalidations.subscriptionCount.first { it > 0 } }
        writeBehindTheStore(settings(9))
        invalidations.emit(Unit)
        withTimeout(10_000) { while (store.current != settings(9)) kotlinx.coroutines.delay(10) }
        assertEquals(settings(9), store.current)
    }

    @Test fun afterARestoreTheFlowOfTheNewDatabaseIsFollowed() = runBlocking {
        store.load()
        withTimeout(10_000) { while (flows.isEmpty() || flows[0].subscriptionCount.value == 0) kotlinx.coroutines.delay(10) }
        restored.emit(Unit)
        settle()
        withTimeout(10_000) { while (flows.size < 2 || flows[1].subscriptionCount.value == 0) kotlinx.coroutines.delay(10) }
        writeBehindTheStore(settings(5))
        flows[1].emit(Unit)
        withTimeout(10_000) { while (store.current != settings(5)) kotlinx.coroutines.delay(10) }
        assertEquals(settings(5), store.current)
    }

    @Test fun aWriteQueuedBeforeARestoreDoesNotLandInTheRestoredDatabase() = runBlocking {
        store.load()
        gate.hold()
        store.set(settings(1))               // queued, not yet run
        writeBehindTheStore(settings(7))     // the restored database
        restored.emit(Unit)
        gate.release(); settle()
        assertEquals(settings(7), stored())
        assertEquals(settings(7), store.current)
    }

    @Test fun aReloadQueuedBeforeALocalSetDoesNotClobberIt() = runBlocking {
        store.load()
        gate.hold()
        synced.emit(Unit)                    // reload queued first
        store.set(settings(3))               // fresher local value
        gate.release(); settle()
        assertEquals(settings(3), store.current)
        assertEquals(settings(3), stored())
    }

    @Test fun loadWithNoRowKeepsTheDefault() = runBlocking {
        store.load()
        assertEquals(GlobalReadingProgressSettings(), store.current)
    }

    @Test fun updatesComposeOnTheSnapshotAndPersistInOrder() = runBlocking {
        store.load()
        gate.hold()
        store.update { copy(activeCycle = 4) }
        store.update { copy(memorizeTypeFullWords = true) }
        assertEquals(GlobalReadingProgressSettings(activeCycle = 4, memorizeTypeFullWords = true), store.current)
        gate.release(); settle()
        assertEquals(GlobalReadingProgressSettings(activeCycle = 4, memorizeTypeFullWords = true), stored())
    }
}
