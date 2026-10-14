package net.bible.service.common

import androidx.room3.Room
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import net.bible.android.BibleApplication.Companion.application
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.database.GlobalTextDisplaySettings
import net.bible.android.database.WorkspaceDatabase
import net.bible.android.database.WorkspaceEntities
import net.bible.service.db.sqliteDriverFactory
import net.bible.sharedcore.platform.AppCoroutineScope
import net.bible.sharedcore.platform.OrderedLauncher
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The global text display settings snapshot: synchronous reads and writes, ordered write-through to the DAO. */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class GlobalTextDisplaySettingsStoreTest {
    private lateinit var db: WorkspaceDatabase
    private lateinit var launcher: OrderedLauncher
    private lateinit var store: GlobalTextDisplaySettingsStore

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(application, WorkspaceDatabase::class.java).setDriver(sqliteDriverFactory()).build()
        launcher = OrderedLauncher(AppCoroutineScope())
        store = GlobalTextDisplaySettingsStore(launcher) { db.globalTextDisplaySettingsDao() }
    }

    @After fun tearDown() { db.close() }

    private fun settings(strongs: Int) = WorkspaceEntities.TextDisplaySettings(strongsMode = strongs)
    private fun stored() = runBlocking { db.globalTextDisplaySettingsDao().get()?.textDisplaySettings }

    @Test fun loadMakesCurrentEqualTheDatabaseRow() = runBlocking {
        db.globalTextDisplaySettingsDao().set(GlobalTextDisplaySettings(textDisplaySettings = settings(2)))
        store.load()
        assertEquals(settings(2), store.current)
    }

    @Test fun setIsVisibleImmediatelyAndPersistedAfterTheLauncherDrains() = runBlocking {
        store.load()
        store.set(settings(1))
        assertEquals(settings(1), store.current)
        withTimeout(10_000) { launcher.join(GlobalTextDisplaySettingsStore.KEY) }
        assertEquals(settings(1), stored())
    }

    @Test fun currentBeforeLoadIsTheDefault() {
        assertEquals(WorkspaceEntities.TextDisplaySettings(), store.current)
    }

    @Test fun lastOfTwoSetsWinsInTheDatabase() = runBlocking {
        store.load()
        store.set(settings(1))
        store.set(settings(2))
        withTimeout(10_000) { launcher.join(GlobalTextDisplaySettingsStore.KEY) }
        assertEquals(settings(2), stored())
        assertEquals(settings(2), store.current)
    }

    @Test fun loadWithNoRowKeepsTheDefault() = runBlocking {
        store.load()
        assertEquals(WorkspaceEntities.TextDisplaySettings(), store.current)
    }
}
