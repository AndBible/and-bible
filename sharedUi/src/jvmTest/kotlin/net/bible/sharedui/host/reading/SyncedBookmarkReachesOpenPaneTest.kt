/*
 * Copyright (c) 2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
 *
 * This file is part of AndBible: Bible Study (http://github.com/AndBible/and-bible).
 * AndBible is free software under the GNU General Public License, version 3 or later.
 */
package net.bible.sharedui.host.reading

import androidx.compose.ui.unit.IntSize
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import net.bible.android.control.page.window.WindowControl
import net.bible.android.database.bookmarks.BookmarkEntities
import net.bible.android.database.SyncableRoomDatabase
import net.bible.service.cloudsync.LAST_PATCH_WRITTEN_KEY
import net.bible.service.cloudsync.LAST_SYNCHRONIZED_KEY
import net.bible.service.cloudsync.SyncableDatabaseAccessor
import net.bible.service.cloudsync.SyncableDatabaseDefinition
import net.bible.service.cloudsync.applyPatchesForDatabase
import net.bible.service.cloudsync.createPatchForDatabase
import net.bible.service.db.exec
import net.bible.service.db.useWriterConnectionMarked
import net.bible.sharedcore.platform.AppEnvironment
import net.bible.service.db.DatabaseContainer
import net.bible.service.db.DatabaseHost
import net.bible.service.startup.ReadingAppInit
import net.bible.sharedcore.platform.desktop.DesktopPaths
import net.bible.sharedlogic.fixtures.StartupFixture
import net.bible.sharedlogic.platform.NightModeState
import net.bible.sharedui.assembly.HostStartup
import net.bible.sharedui.assembly.StartupPhase
import net.bible.sharedui.desktop.assembly.DesktopAppStartup
import net.bible.sharedui.desktop.assembly.DesktopReadingHostPlatform
import net.bible.sharedui.desktop.assembly.DesktopWindowHandle
import net.bible.sharedui.desktop.webengine.FakeWebEngine
import net.bible.sharedui.desktop.webengine.kcef.SwingMainDispatcher
import net.bible.sharedui.desktop.webview.DesktopReadingPaneEngine
import net.bible.sharedui.host.app.HostNavShell
import net.bible.sharedui.host.app.HostNavigator
import net.bible.sharedui.host.app.HostReadingReady
import org.crosswire.common.util.PropertyMap
import org.crosswire.jsword.passage.VerseRangeFactory
import org.crosswire.jsword.versification.system.Versifications
import org.koin.mp.KoinPlatform
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * #4151: a highlight that a cloud sync applies (BibleBookmark + its notes + BibleBookmarkToLabel rows from another
 * device) must reach the open desktop reading pane without a reload. Drives the real desktop startup, the real
 * [HostReadingWindows] assembly, a real patch merged by [applyPatchesForDatabase] and the BOOKMARKS accessor's
 * `reactToUpdates`, as `CloudSync` runs them; only the web engine is a fake that records the evaluated JS.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class SyncedBookmarkReachesOpenPaneTest {
    private object WindowHandle : DesktopWindowHandle {
        override val sizeDp = IntSize(1200, 800)
        override val density = 1f
        override fun toFront() = Unit
    }

    private object Navigator : HostNavigator {
        override fun navigate(route: String) = Unit
        override fun popBackStack() = false
        override fun popBackStackTo(route: String, inclusive: Boolean) = false
    }

    private object Day : NightModeState {
        override val nightMode = false
    }

    private val main = SwingMainDispatcher
    private lateinit var root: Path
    private var previousHost: DatabaseHost? = null

    @BeforeTest fun setUp() {
        assertNull(KoinPlatform.getKoinOrNull(), "a Koin context leaked into this test")
        Dispatchers.setMain(main)
        previousHost = runCatching { DatabaseContainer.host }.getOrNull()
        root = Files.createTempDirectory("synced-bookmark-pane")
    }

    @AfterTest fun tearDown() {
        try {
            runBlocking { withContext(Dispatchers.Main) { HostReadingWindows.resetForTest() } }
            HostReadingReady.resetForTest()
            ReadingAppInit.resetForTest()
            DesktopAppStartup.resetForTest()
        } finally {
            previousHost?.let { DatabaseContainer.installHost(it) }
            root.toFile().deleteRecursively()
            Dispatchers.resetMain()
        }
    }

    @Test fun syncedHighlightReachesTheOpenPane() = runBlocking {
        val paths = DesktopPaths(
            { name -> when (name) {
                "XDG_DATA_HOME" -> root.resolve("data").toString()
                "XDG_CONFIG_HOME" -> root.resolve("config").toString()
                "XDG_CACHE_HOME" -> root.resolve("cache").toString()
                else -> null
            } }, "Linux", root.resolve("home"),
        )
        StartupFixture.files.forEach { (name, bytes) ->
            val target = paths.sword.resolve(name)
            Files.createDirectories(target.parent)
            Files.write(target, bytes)
        }
        assertEquals(StartupPhase.Ready, DesktopAppStartup.ensureStarted(paths, WindowHandle, "en", repositories = { PropertyMap() }, notifierSource = net.bible.sharedui.desktop.assembly.DesktopNotifierSource(net.bible.sharedcore.platform.desktop.dbus.DesktopDbus().supportedOs) { net.bible.sharedcore.platform.desktop.dbus.NoOpNativeNotifier }))
        HostReadingReady.ensure(HostStartup.stringTable)
        val koin = KoinPlatform.getKoin()
        val scope = HostReadingReady.scope
        val control = koin.get<WindowControl>()
        val fake = FakeWebEngine()
        val engine = DesktopReadingPaneEngine(fake, {}, koin = { koin })
        val host = withContext(main) { HostReadingHost(scope, HostNavShell(scope, Navigator), koin, DesktopReadingHostPlatform(WindowHandle), Day) }
        try {
            val id = withContext(main) {
                HostReadingWindows.bind(host, scope, koin, Day, engine)
                val id = control.activeWindow.id.toString()
                val session = HostReadingWindows.sessionFor(id)
                engine.attach(id, false, HostReadingWindows.bridgeFor(id), {}, { session.jsChannel.flush() })
                session.jsChannel.markClientReady()
                id
            }

            // The other device's rows, as its patch carries them: edited (at 500) long before this device's sync watermark.
            val dao = DatabaseContainer.instance.bookmarkDb.bookmarkDao()
            val accessor = DatabaseContainer.getDatabaseAccessorFactories(DatabaseContainer.instance)
                .map { it() }.single { it.category == SyncableDatabaseDefinition.BOOKMARKS }
            val env = koin.get<AppEnvironment>()
            val label = BookmarkEntities.Label(name = "Synced label")
            val bookmark = BookmarkEntities.BibleBookmarkWithNotes(
                VerseRangeFactory.fromString(Versifications.instance().getVersification("KJV"), "Ps.33.7"), null, true, null,
            ).apply { notes = "Synced note" }
            dao.insert(label)
            accessor.dao.clearLog()
            accessor.dao.setConfig(LAST_PATCH_WRITTEN_KEY, 0L)
            dao.insert(bookmark.bookmarkEntity)
            dao.insert(bookmark.noteEntity!!)
            dao.insert(BookmarkEntities.BibleBookmarkToLabel(bookmark.id, label.id))
            accessor.localDb.useWriterConnectionMarked { it.exec("UPDATE LogEntry SET lastUpdated = 500, sourceDevice = 'other-device'") }
            @Suppress("UNCHECKED_CAST")
            val otherDevice = (accessor as SyncableDatabaseAccessor<SyncableRoomDatabase>).let {
                SyncableDatabaseAccessor(it.localDb, it.dbFactory, { error("unused") }, it.localDbPath, it.category, deviceId = "other-device")
            }
            val patch = checkNotNull(createPatchForDatabase(otherDevice, env, false))
            dao.delete(bookmark)
            accessor.dao.clearLog()
            accessor.dao.setConfig(LAST_SYNCHRONIZED_KEY, 1000L)
            val pane = fake.live(id)
            pane.evaluated.clear()

            // What CloudSync does with a downloaded patch.
            accessor.reactToUpdates(applyPatchesForDatabase(accessor, env, patch))
            assertEquals(1, dao.bibleBookmarksByIds(listOf(bookmark.id)).size, "sanity: the patch was applied")

            val deadline = System.currentTimeMillis() + 10_000
            fun reached() = synchronized(pane.evaluated) { pane.evaluated.toList() }
                .any { "add_or_update_bookmarks" in it && bookmark.id.toString() in it }
            while (!reached() && System.currentTimeMillis() < deadline) {
                withContext(main) { }
                Thread.sleep(20)
            }
            assertTrue(reached(), "the synced bookmark must reach the open pane: ${pane.evaluated}")
        } finally {
            withContext(main) { host.dispose() }
        }
    }
}
