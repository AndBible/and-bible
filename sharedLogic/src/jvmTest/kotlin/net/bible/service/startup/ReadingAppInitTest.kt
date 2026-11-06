/*
 * Copyright (c) 2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
 *
 * This file is part of AndBible: Bible Study (http://github.com/AndBible/and-bible).
 *
 * AndBible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * AndBible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with AndBible.
 * If not, see http://www.gnu.org/licenses/.
 */
package net.bible.service.startup

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.bible.android.control.page.window.WindowControl
import net.bible.android.database.GlobalTextDisplaySettings
import net.bible.android.database.GlobalTextDisplaySettingsDao
import net.bible.android.database.WorkspaceEntities
import net.bible.android.control.versification.BookInstallWatcher
import net.bible.service.cloudsync.DocumentSyncProvider
import net.bible.service.cloudsync.documents.DocumentSyncSettings
import net.bible.service.sword.ContentSource
import net.bible.service.sword.backgroundimage.BackgroundImageImporter
import net.bible.service.sword.backgroundimage.addManuallyInstalledBackgroundImageBooks
import net.bible.service.sword.backgroundimage.backgroundImageFile
import net.bible.service.sword.persistCipherKey
import net.bible.sharedcore.platform.AppDirectories
import net.bible.sharedcore.platform.AppEnvironment
import net.bible.sharedcore.platform.CoreStrings
import net.bible.sharedlogic.addons.SharedAddonCatalog
import net.bible.sharedlogic.addons.SharedAddonScanner
import net.bible.sharedlogic.addons.SharedPromptPackSource
import okio.Buffer
import okio.Path.Companion.toPath
import org.crosswire.jsword.book.Books
import java.nio.file.Files
import net.bible.sharedcore.cloud.DocumentSyncStarter
import net.bible.sharedlogic.platform.AddonCatalog
import org.crosswire.jsword.book.sword.NullBackend
import org.crosswire.jsword.book.sword.SwordBook
import org.crosswire.jsword.book.sword.SwordBookMetaData
import java.lang.reflect.Proxy
import net.bible.android.database.SwordDocumentInfo
import net.bible.service.common.GlobalTextDisplaySettingsStore
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
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

    /** Removing startup-job ownership must fail before a replacement can close its exact DAO's database. */
    @Test
    fun replacementWaitsForTheStartupSnapshotQueryBeforeClosingItsDatabase() = runBlocking {
        val dao = DatabaseContainer.instance.workspaceDb.globalTextDisplaySettingsDao()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var queryFinished = false
        val gatedDao = object : GlobalTextDisplaySettingsDao by dao {
            override suspend fun get(): GlobalTextDisplaySettings? =
                withContext(NonCancellable) {
                    entered.complete(Unit)
                    release.await()
                    dao.get().also { queryFinished = true }
                }
        }
        val appScope = AppCoroutineScope()
        KoinPlatform.getKoin().loadModules(listOf(module {
            single { appScope }
            single { GlobalTextDisplaySettingsStore(OrderedLauncher(appScope), { gatedDao }) }
        }))
        val load = ReadingAppInit.loadSnapshotStoresInBackground()
        var replacement: Deferred<Unit>? = null
        try {
            withTimeout(30_000) { entered.await() }
            // UNDISPATCHED reaches cancel-and-join before returning: no timing-based "has not closed yet" check.
            replacement = async(start = CoroutineStart.UNDISPATCHED) {
                DatabaseContainer.aiLifetime.replace { token ->
                    assertTrue(queryFinished, "replacement must join the exact startup query before closing")
                    DatabaseContainer.resetOwned(token)
                }
            }
            assertEquals(false, replacement.isCompleted, "replacement must be waiting for the startup query")
            release.complete(Unit)
            withTimeout(30_000) { replacement.await() }
            assertTrue(queryFinished)
            assertTrue(load.isCompleted)
            assertTrue(runCatching { dao.get() }.isFailure, "the exact queried database was closed")
        } finally {
            release.complete(Unit)
            // Even a failed assertion must join the native reader before @AfterTest closes its database.
            // A cleanup timeout must not turn an ownership regression into a JVM crash.
            withContext(NonCancellable) {
                load.join()
                replacement?.join()
                appScope.coroutineContext[Job]!!.let { it.cancel(); it.join() }
            }
        }
    }

    @Test
    fun alreadyInitializedStartupStillLoadsDocumentSyncSettings() = runTest(timeout = 10.minutes) {
        stubSettingsAndBooks()
        val db = DatabaseContainer.instance.documentSyncDb
        db.documentSyncPreferencesDao().set(net.bible.android.database.DocumentSyncPreferences(enabled = true))
        val environment = Proxy.newProxyInstance(AppEnvironment::class.java.classLoader, arrayOf(AppEnvironment::class.java)) { _, m, _ ->
            when (m.returnType) { java.lang.Boolean.TYPE -> true; java.lang.Long.TYPE -> 0L; else -> null }
        } as AppEnvironment
        val documents = DocumentSyncSettings(
            { db.documentSyncPreferencesDao() }, { db.cloudListingStateDao() },
            { db.cloudDocumentSyncTimestampDao() }, OrderedLauncher(scope), environment,
        )
        ReadingAppInit.loadDocumentSyncSettings = { documents.ensureLoaded() }
        val sync = net.bible.service.cloudsync.SyncNow(
            object : net.bible.service.cloudsync.CloudSyncPort {
                override val signedIn = false
                override suspend fun signIn(): Boolean? = false
                override fun start(): Deferred<Boolean> = error("not started")
                override suspend fun waitUntilFinished() = Unit
                override val runningChanged = net.bible.sharedcore.event.EventSource<Boolean>()
            }, net.bible.sharedcore.event.EventSource(),
            { net.bible.service.cloudsync.SyncableDatabaseDefinition.ALL.any { it.isSyncEnabled(settings) } || documents.enabled },
            {}, { true }, { false }, {}, scope,
        )
        try {
            ReadingAppInit.markInitialized()
            ReadingAppInit.initialise(platform)
            assertTrue(sync.available(), "document-only sync must be available after alternate startup")
        } finally { sync.close() }
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
    fun startupRegistersAnImportedBackgroundForTheSavedSelection() = runTest(timeout = 10.minutes) {
        val root = Files.createTempDirectory("startup-background").toFile()
        val dirs = object : AppDirectories {
            override val internalFilesDir = root.resolve("internal").path.toPath()
            override val modulesDir = root.resolve("modules").path.toPath()
            override val manualInstallDir = root.resolve("manual").path.toPath()
            override val manualInstallDir2 = root.resolve("manual2").path.toPath()
            override val manualReadingPlanDir = root.resolve("plans").path.toPath()
        }
        val scanner = SharedAddonScanner({ Books.installed().getBooks() })
        val catalog = SharedAddonCatalog(scanner, SharedPromptPackSource(scanner))
        KoinPlatform.getKoin().loadModules(listOf(module {
            single<AppDirectories> { dirs }
            single<CoreStrings> { unused() }
            single<AppEnvironment> { unused() }
            single<AddonCatalog> { catalog }
        }))
        ReadingAppInit.loadStores = { }
        ReadingAppInit.loadDocumentSyncSettings = { }
        ReadingAppInit.hasInstalledBible = { false }
        val source = object : ContentSource {
            override fun openSource(uri: String) = Buffer().write(byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47))
            override fun displayName(uri: String) = "startup landscape.png"
            override fun mimeType(uri: String) = "image/png"
        }
        val booksBefore = Books.installed().getBooks().toSet()
        try {
            val image = assertNotNull(BackgroundImageImporter.copyAndRegister(source, "test://background"))
            val imported = assertNotNull(Books.installed().getBook("BGIMG_startup_landscape"))
            assertEquals(image, catalog.backgroundImages[imported.initials]?.path)
            val dao = DatabaseContainer.instance.workspaceDb.workspaceDao()
            val workspace = WorkspaceEntities.Workspace(name = "Imported background").apply {
                textDisplaySettings = WorkspaceEntities.TextDisplaySettings.default.copy(
                    colors = WorkspaceEntities.TextDisplaySettings.default.colors!!.copy(
                        dayBackgroundImage = imported.initials,
                        nightBackgroundImage = imported.initials,
                    ),
                )
            }
            dao.insertWorkspace(workspace)

            // Process restart loses synthetic books and catalog caches, but keeps files and saved selections.
            Books.installed().removeBook(imported)
            catalog.clearCaches()
            assertNull(Books.installed().getBook(imported.initials))
            ReadingAppInit.initialise(platform)

            val restored = dao.allWorkspaces().single().textDisplaySettings!!.colors!!
            for (selected in listOf(restored.dayBackgroundImage, restored.nightBackgroundImage)) {
                assertEquals("BGIMG_startup_landscape", selected)
                val registered = assertNotNull(Books.installed().getBook(selected), "startup must register the saved background")
                assertEquals(image, registered.backgroundImageFile)
                assertEquals(image, assertNotNull(catalog.backgroundImages[selected]).path)
            }
            // initialise() is a no-op once initialized is set, so repeat only the scan it would have done.
            addManuallyInstalledBackgroundImageBooks()
            assertEquals(1, Books.installed().getBooks().count { it.initials == imported.initials })
        } finally {
            Books.installed().getBooks().filter { it !in booksBefore }.forEach { Books.installed().removeBook(it) }
            root.deleteRecursively()
        }
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

    private inline fun <reified T : Any> unused(): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, m, _ -> error("unexpected ${m.name}") } as T

    private fun watcher(): BookInstallWatcher {
        val launcher = OrderedLauncher(scope)
        return BookInstallWatcher(
            launcher, unused<DocumentSyncStarter>(), unused<DocumentSyncProvider>(),
            DocumentSyncSettings({ error("unused") }, { error("unused") }, { error("unused") }, launcher, unused()),
            unused<AddonCatalog>(),
        )
    }

    private fun lockedBook() = SwordBook(
        SwordBookMetaData("""
            [LockedKjv]
            Description=Locked King James
            Abbreviation=LKJV
            ModDrv=RawText
            Versification=KJV
            Lang=en
        """.trimIndent().encodeToByteArray(), "LockedKjv"),
        NullBackend(),
    )

    /** Pins iOS FeaturePathsUITests.testCipherWrongRetryRightPersists: the key given in the unlock dialog reaches the next launch's unlock. */
    @Test
    fun persistedCipherKeyOfAModuleWithoutARowIsUnlockedAtNextStartup() = runTest(timeout = 10.minutes) {
        stubSettingsAndBooks()
        val unlocked = mutableListOf<Pair<String, String?>>()
        ReadingAppInit.unlockBook = { initials, key -> unlocked += initials to key }
        val dao = DatabaseContainer.instance.repoDb.swordDocumentInfoDao()
        val book = lockedBook()
        assertEquals(null, dao.getBook(book.initials))

        persistCipherKey(book, "right-key", watcher())

        val row = dao.getBook(book.initials)!!
        assertEquals("right-key", row.cipherKey)
        assertEquals("LockedKjv", row.initials)
        ReadingAppInit.unlockStoredCipherKeys()
        assertEquals<List<Pair<String, String?>>>(listOf("LockedKjv" to "right-key"), unlocked)
    }

    @Test
    fun persistCipherKeyKeepsOtherFieldsOfAnExistingRow() = runTest(timeout = 10.minutes) {
        stubSettingsAndBooks()
        val unlocked = mutableListOf<Pair<String, String?>>()
        ReadingAppInit.unlockBook = { initials, key -> unlocked += initials to key }
        val dao = DatabaseContainer.instance.repoDb.swordDocumentInfoDao()
        val book = lockedBook()
        dao.insert(SwordDocumentInfo(book.initials, "Old name", "OLD", "en", "Some Repo", cipherKey = "wrong"))

        persistCipherKey(book, "right-key", watcher())

        val row = dao.getBook(book.initials)!!
        assertEquals("right-key", row.cipherKey)
        assertEquals("Some Repo", row.repository)
        assertEquals("Old name", row.name)
        ReadingAppInit.unlockStoredCipherKeys()
        assertEquals<List<Pair<String, String?>>>(listOf("LockedKjv" to "right-key"), unlocked)
    }

    @Test
    fun cipherKeysUnlockedIsSetOnlyAfterASuccessfulUnlock() = runTest(timeout = 10.minutes) {
        stubSettingsAndBooks()
        ReadingAppInit.unlockBook = { _, _ -> }
        ReadingAppInit.hasInstalledBible = { false }
        ReadingAppInit.unlockStoredCipherKeys()
        assertEquals(false, ReadingAppInit.cipherKeysUnlocked, "no Bible installed: nothing unlocked, so callers must retry later")
        ReadingAppInit.hasInstalledBible = { true }
        ReadingAppInit.unlockStoredCipherKeys()
        assertTrue(ReadingAppInit.cipherKeysUnlocked)
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

    /**
     * `onConstructed` must run before the repository is taken and loaded: the load posts workspace events
     * synchronously and their subscribers read the repository through the WindowControl.
     */
    @Test
    fun onConstructedRunsBeforeTakeAndLoad() {
        val order = mutableListOf<String>()
        val windowControl = KoinPlatform.getKoin().get<WindowControl>()
        val before = windowControl.windowRepository
        val s = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        ReadingAppInit.loadRepository = { repository ->
            order += if (windowControl.windowRepository === repository) "load(taken)" else "load(not taken)"
        }
        try {
            ReadingAppInit.createWindowRepository(s) { repository ->
                order += if (windowControl.windowRepository === repository) "constructed(taken)" else "constructed(not yet taken)"
            }
            assertEquals(listOf("constructed(not yet taken)", "load(taken)"), order)
            assertNotSame(before, windowControl.windowRepository)
        } finally {
            s.cancel()
        }
    }
}
