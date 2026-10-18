package net.bible.service.startup

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.bible.android.control.page.window.WindowControl
import net.bible.android.database.SwordDocumentInfo
import net.bible.android.database.bookmarks.BookmarkEntities
import net.bible.android.database.bookmarks.BookmarkSortOrder
import net.bible.android.database.bookmarks.KJVA
import net.bible.android.database.bookmarks.LabelType
import net.bible.service.db.DatabaseContainer
import net.bible.service.db.DatabaseHost
import net.bible.service.db.FakeDatabaseHost
import net.bible.service.db.newTestDir
import net.bible.service.sword.mydocument.MyDocumentBookManager
import net.bible.sharedcore.platform.AppCoroutineScope
import net.bible.sharedcore.platform.AppSettings
import net.bible.sharedcore.platform.OrderedLauncher
import net.bible.sharedlogic.platform.FirstLoadEdge
import org.crosswire.jsword.passage.VerseRangeFactory
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.koin.mp.KoinPlatform
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

/** Real bundled SQLite and a Koin test module; documents and platform pieces are counted fakes. */
class ReadingAppInitTest {
    private var previousHost: DatabaseHost? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val settings = MapSettings()

    private class MapSettings : AppSettings {
        val values = mutableMapOf<String, Any?>()
        override fun getString(key: String, default: String?) = values[key] as String? ?: default
        override fun getLong(key: String, default: Long) = values[key] as Long? ?: default
        override fun getInt(key: String, default: Int) = values[key] as Int? ?: default
        override fun getBoolean(key: String, default: Boolean) = values[key] as Boolean? ?: default
        override fun getDouble(key: String, default: Double) = values[key] as Double? ?: default
        override fun getFloat(key: String, default: Float) = values[key] as Float? ?: default
        override fun setString(key: String, value: String?) { values[key] = value }
        override fun setLong(key: String, value: Long?) { values[key] = value }
        override fun setInt(key: String, value: Int?) { values[key] = value }
        override fun setBoolean(key: String, value: Boolean?) { values[key] = value }
        override fun setDouble(key: String, value: Double?) { values[key] = value }
        override fun setFloat(key: String, value: Float?) { values[key] = value }
    }

    private class FakePlatform : ReadingAppInitPlatform {
        var starts = 0
        var sweeps = 0
        override suspend fun platformStart() { starts++ }
        override val ioDispatcher get() = Dispatchers.Default
        override val exampleLabelNames = ExampleLabelNames("Red!", "Green!", "Blue!", "Under!", "Salvation!", "Migrated!")
        override fun workspaceName(n: Int) = "WS $n"
        override fun sweepOrphanSearchIndexes() { sweeps++ }
    }

    private val platform = FakePlatform()

    @BeforeTest
    fun setUp() {
        // MyDocumentBookManager reads Dispatchers.Main.immediate when it is first touched.
        Dispatchers.setMain(Dispatchers.Unconfined)
        previousHost = try { DatabaseContainer.host } catch (_: IllegalStateException) { null }
        DatabaseContainer.installHost(FakeDatabaseHost(newTestDir()))
        val container = runBlocking { DatabaseContainer.create() }
        DatabaseContainer.containerFactory = { container }
        DatabaseContainer.ready = true
        if (KoinPlatform.getKoinOrNull() != null) stopKoin()
        startKoin {
            modules(module {
                single<AppSettings> { settings }
                single<FirstLoadEdge> { object : FirstLoadEdge { override fun <T> awaitFirstLoad(body: suspend () -> T): T = runBlocking { body() } } }
                single { WindowControl() }
                single { net.bible.android.control.page.window.WorkspacePersistence(OrderedLauncher(AppCoroutineScope())) { DatabaseContainer.instance.workspaceDb.workspaceDao() } }
            })
        }
        MyDocumentBookManager.launcher = OrderedLauncher(AppCoroutineScope())
        MyDocumentBookManager.mainDispatcher = Dispatchers.Unconfined
        ReadingAppInit.resetForTest()
    }

    @AfterTest
    fun tearDown() {
        ReadingAppInit.resetForTest()
        scope.cancel()
        runBlocking { DatabaseContainer.aiLifetime.replace { DatabaseContainer.resetOwned(it) } }
        DatabaseContainer.ready = false
        DatabaseContainer.forgetReplacesForTest()
        DatabaseContainer.detachInstanceForTest()
        stopKoin()
        Dispatchers.resetMain()
        previousHost?.let { DatabaseContainer.installHost(it) }
    }

    private fun stubSettingsAndBooks(counter: IntArray = IntArray(2)) {
        ReadingAppInit.loadStores = { }
        ReadingAppInit.loadDocumentSyncSettings = { }
        ReadingAppInit.installManualBooks = { counter[0]++ }
        ReadingAppInit.hasInstalledBible = { true }
    }

    @Test
    fun initialiseIsIdempotent() = runTest(timeout = 10.minutes) {
        val installs = IntArray(2)
        stubSettingsAndBooks(installs)
        ReadingAppInit.initialise(platform)
        assertTrue(ReadingAppInit.initialized)
        ReadingAppInit.initialise(platform)
        assertEquals(1, installs[0], "book installers ran once")
        assertEquals(1, platform.starts, "platform start ran once")
        assertEquals(1, platform.sweeps, "index sweep ran once")
    }

    @Test
    fun initialiseUnlocksStoredCipherKeys() = runTest(timeout = 10.minutes) {
        stubSettingsAndBooks()
        val unlocked = mutableListOf<Pair<String, String?>>()
        ReadingAppInit.unlockBook = { initials, key -> unlocked += initials to key }
        val dao = DatabaseContainer.instance.repoDb.swordDocumentInfoDao()
        dao.insert(SwordDocumentInfo("Locked", "Locked book", "Lk", "en", "repo", cipherKey = "secret"))
        dao.insert(SwordDocumentInfo("Open", "Open book", "Op", "en", "repo", cipherKey = null))

        ReadingAppInit.initialise(platform)
        ReadingAppInit.initialise(platform)

        assertEquals<List<Pair<String, String?>>>(listOf("Locked" to "secret"), unlocked)
    }

    @Test
    fun prepareDataCreatesExampleLabelsOnlyOnce() = runTest(timeout = 10.minutes) {
        val bookmarkDao = DatabaseContainer.instance.bookmarkDb.bookmarkDao()
        // An existing bookmark skips the example bookmarks (they need an installed Bible); the labels are independent of it.
        val existing = BookmarkEntities.BibleBookmarkWithNotes(
            VerseRangeFactory.fromString(KJVA, "Joh.3.16"), textRange = null, wholeVerse = true, book = null)
        bookmarkDao.insert(existing.bookmarkEntity)

        ReadingAppInit.prepareData(platform)

        fun highlightNames() = runBlocking { bookmarkDao.allLabelsSortedByName() }
            .filter { it.type == LabelType.HIGHLIGHT }.map { it.name }.sorted()
        assertEquals(listOf("Blue!", "Green!", "Red!", "Under!"), highlightNames())
        assertEquals(1L, settings.getLong("data-version", 0))
        assertEquals(1, bookmarkDao.allBookmarks(BookmarkSortOrder.ORDER_NUMBER).size)
        val workspaces = DatabaseContainer.instance.workspaceDb.workspaceDao().allWorkspaces()
        assertEquals(listOf("WS 1", "WS 2"), workspaces.map { it.name }.sorted())
        assertTrue(settings.getString("current_workspace_id") in workspaces.map { it.id.toString() })

        // Same call again (version already 1) and with the version forced back to 0: labels exist, none are added.
        ReadingAppInit.prepareData(platform)
        settings.setLong("data-version", 0)
        ReadingAppInit.prepareData(platform)
        assertEquals(listOf("Blue!", "Green!", "Red!", "Under!"), highlightNames())
        assertEquals(1L, settings.getLong("data-version", 0))
        // The forced second run found workspaces: it keeps them and marks the app as not first-time.
        assertEquals(2, DatabaseContainer.instance.workspaceDb.workspaceDao().allWorkspaces().size)
        assertEquals(false, settings.getBoolean("first-time", true))
    }

    @Test
    fun createWindowRepositoryIsIdempotentPerScope() {
        val scopeA = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val scopeB = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val loaded = mutableListOf<Any>()
        ReadingAppInit.loadRepository = { loaded += it }
        try {
            val first = ReadingAppInit.createWindowRepository(scopeA)
            assertSame(first, ReadingAppInit.createWindowRepository(scopeA))
            assertSame(first, KoinPlatform.getKoin().get<WindowControl>().windowRepository)
            val second = ReadingAppInit.createWindowRepository(scopeB)
            assertNotSame(first, second)
            assertEquals(2, loaded.size, "each repository is loaded once")
            assertSame(second, KoinPlatform.getKoin().get<WindowControl>().windowRepository)
        } finally {
            scopeA.cancel(); scopeB.cancel()
        }
    }
}
