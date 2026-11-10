/*
 * Copyright (c) 2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
 *
 * This file is part of AndBible: Bible Study (http://github.com/AndBible/and-bible).
 * AndBible is free software under the GNU General Public License, version 3 or later.
 */
package net.bible.sharedui.host.reading

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.IntSize
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Executors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.yield
import kotlinx.coroutines.withContext
import net.bible.android.control.page.window.WindowControl
import net.bible.android.database.WorkspaceEntities
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
import net.bible.sharedui.desktop.webview.DesktopReadingPaneEngine
import net.bible.sharedui.host.app.HostNavShell
import net.bible.sharedui.host.app.HostNavigator
import net.bible.sharedui.host.app.HostReadingReady
import org.crosswire.common.util.CWProject
import org.crosswire.common.util.PropertyMap
import org.koin.mp.KoinPlatform
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class WorkspaceSwitchContentTest {
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

    private val main = Executors.newSingleThreadExecutor { Thread(it, "workspace-switch-main").apply { isDaemon = true } }.asCoroutineDispatcher()
    private lateinit var root: Path
    private var previousHost: DatabaseHost? = null
    private var restoreCwProject: () -> Unit = {}

    @BeforeTest fun setUp() {
        assertNull(KoinPlatform.getKoinOrNull(), "a Koin context leaked into this test")
        Dispatchers.setMain(main)
        previousHost = runCatching { DatabaseContainer.host }.getOrNull()
        restoreCwProject = snapshotCwProject()
        root = Files.createTempDirectory("workspace-switch-content")
    }

    @AfterTest fun tearDown() {
        try {
            runBlocking { withContext(Dispatchers.Main) { HostReadingWindows.resetForTest() } }
            HostReadingReady.resetForTest()
            ReadingAppInit.resetForTest()
            DesktopAppStartup.resetForTest()
        } finally {
            previousHost?.let { DatabaseContainer.installHost(it) }
            restoreCwProject()
            root.toFile().deleteRecursively()
            Dispatchers.resetMain()
            main.close()
        }
    }

    @OptIn(org.koin.core.annotation.KoinInternalApi::class)
    @Test fun finalFixActualHostBackReopensSameResultsForBothResultTargets() {
        runBlocking {
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
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + main)
        val restoreAllowed = net.bible.sharedui.components.OverlayPresence.restoreAllowed
        val originalSearch = koin.get<net.bible.sharedcore.search.BibleSearchService>()
        val searchKey = org.koin.core.definition.indexKey(net.bible.sharedcore.search.BibleSearchService::class, null,
            org.koin.core.qualifier.StringQualifier("_root_"))
        val searchFactory = checkNotNull(koin.instanceRegistry.instances[searchKey])
        val fixtureModule = org.koin.dsl.module {
            single<net.bible.sharedcore.search.BibleSearchService> {
                object : net.bible.sharedcore.search.BibleSearchService by originalSearch {
                    override fun unindexedAmong(translationIds: List<String>) = emptyList<String>()
                    override suspend fun searchMulti(request: net.bible.sharedcore.search.SearchRequest): net.bible.sharedcore.search.MultiSearchResults =
                        error("the host must retain cached rows without rerunning search")
                }
            }
        }
        koin.loadModules(listOf(fixtureModule))
        withContext(main) {
            val host = HostReadingHost(scope, HostNavShell(scope, Navigator), koin, DesktopReadingHostPlatform(WindowHandle), Day)
            try {
                // Inspect the real host's state; the production back chain and navigation are not replaced.
                val search = HostReadingHost::class.java.getDeclaredField("searchState").apply { isAccessible = true }
                    .get(host) as net.bible.sharedui.reading.host.slots.ReadingSearchState
                val book = assertNotNull(org.crosswire.jsword.book.Books.installed().getBook("K4RawText"))
                book.indexStatus = org.crosswire.jsword.index.IndexStatus.DONE
                val repository = koin.get<WindowControl>().windowRepository
                val cache = koin.get<net.bible.sharedcore.search.SearchResultsCache>()
                val source = repository.activeWindow
                val request = net.bible.sharedcore.search.SearchRequest(
                    "light", net.bible.sharedcore.search.SearchType.ANY_WORDS,
                    net.bible.sharedcore.search.SearchBibleSection.ALL, listOf("K4RawText"),
                    koin.get<net.bible.android.control.search.SearchControl>().currentBookName,
                )
                val rows = net.bible.sharedcore.search.MultiSearchResults(
                    listOf(net.bible.sharedcore.search.SwordResultRow("Gen.1.2", emptyList(), net.bible.sharedcore.search.StyledText.plain("light"))),
                    emptyList(), 1,
                )
                for (links in listOf(false, true)) {
                    repository.activeWindow = source
                    source.pageManager.setCurrentDocumentAndKey(book, book.getKey("Gen.1.1"))
                    cache.put(request.copy(currentBookName = koin.get<net.bible.android.control.search.SearchControl>().currentBookName), rows)
                    host.openSearchRequest(net.bible.sharedcore.search.ReadingSearchRequest(
                        query = "light", documentId = "K4RawText", translationIds = listOf("K4RawText"), preDecorated = true,
                    ))
                    search.setOpenResultInLinksWindow(links)
                    kotlinx.coroutines.withTimeout(5_000) { search.searchBar.first { it != null } }
                    assertEquals(net.bible.sharedcore.search.ReadingSearchPhase.Results("K4RawText", false), search.controller.phase.value)
                    assertEquals(rows, search.searchResults.results.value)
                    val listState = search.searchResultsListState.value
                    search.searchResultsExpanded["Gen.1.2"] = true
                    search.onSearchResultSelected("Gen.1.2", "K4RawText")
                    assertFalse(search.searchSheetVisible.value)
                    if (links) assertTrue(source.targetLinksWindow.isLinksWindow)
                    else assertEquals("Gen.1.2", source.pageManager.currentBible.singleKey.getOsisRef())
                    var passed = false
                    host.backChain.onBack { passed = true }
                    assertFalse(passed)
                    assertTrue(search.searchSheetVisible.value, "first Back must reopen results (links=$links)")
                    assertTrue(search.controller.searchModeActive.value)
                    assertEquals("light", search.queries.query.value)
                    assertEquals(rows, search.searchResults.results.value)
                    assertSame(listState, search.searchResultsListState.value)
                    assertEquals(true, search.searchResultsExpanded["Gen.1.2"])
                    host.backChain.onBack { passed = true }
                    assertFalse(search.searchSheetVisible.value)
                    assertTrue(search.controller.searchModeActive.value)
                    host.backChain.onBack { passed = true }
                    assertFalse(search.controller.searchModeActive.value)
                    assertFalse(passed)
                }
            } finally {
                host.dispose()
                net.bible.sharedui.components.OverlayPresence.restoreAllowed = restoreAllowed
                scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
                koin.unloadModules(listOf(fixtureModule))
                koin.instanceRegistry.saveMapping(true, searchKey, searchFactory)
            }
        }
        scope.coroutineContext[kotlinx.coroutines.Job]?.join()
        }
    }

    @OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
    @Test fun searchLinksWindowCanBeConvertedWhileTheSplitIsComposed() {
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
        runBlocking {
            assertEquals(StartupPhase.Ready, DesktopAppStartup.ensureStarted(paths, WindowHandle, "en", repositories = { PropertyMap() }, notifierSource = net.bible.sharedui.desktop.assembly.DesktopNotifierSource(net.bible.sharedcore.platform.desktop.dbus.DesktopDbus().supportedOs) { net.bible.sharedcore.platform.desktop.dbus.NoOpNativeNotifier }))
            HostReadingReady.ensure(HostStartup.stringTable)
        }
        val koin = KoinPlatform.getKoin()
        val control = koin.get<WindowControl>()
        val host = runBlocking { withContext(main) {
            HostReadingHost(HostReadingReady.scope, HostNavShell(HostReadingReady.scope, Navigator), koin, DesktopReadingHostPlatform(WindowHandle), Day)
        } }
        val painter = androidx.compose.ui.graphics.painter.ColorPainter(androidx.compose.ui.graphics.Color.Black)
        val deps = runBlocking { withContext(main) {
            val book = assertNotNull(org.crosswire.jsword.book.Books.installed().getBook("K4RawText"))
            val list = net.bible.service.sword.BookAndKeyList().apply {
                addAll(net.bible.service.sword.BookAndKey(book.getKey("Gen.1.2"), book))
            }
            control.showLink(koin.get<net.bible.service.download.FakeBookFactory>().multiDocument, list)
            host.deps(net.bible.sharedui.reading.ReadingToolbarIcons(painter, painter, painter, painter, painter, painter, painter, painter, painter))
        } }
        val fake = FakeWebEngine()
        val engine = DesktopReadingPaneEngine(fake, {}, koin = { koin })
        runBlocking { withContext(main) { HostReadingWindows.bind(host, HostReadingReady.scope, koin, Day, engine) } }
        val paneFactory = net.bible.sharedui.desktop.webview.desktopReadingPaneFactory(
            engine, HostReadingWindows::sessionFor, HostReadingWindows::bridgeFor, isOpen = HostReadingWindows::isOpen,
        )
        val links = control.windowRepository.visibleWindows.single { it.isLinksWindow }
        val keyBefore = links.pageManager.currentPage.key?.getOsisRef()
        try {
            runComposeUiTest {
                setContent {
                    androidx.compose.runtime.CompositionLocalProvider(
                        net.bible.sharedui.strings.LocalStrings provides net.bible.sharedui.strings.iosStrings("en"),
                        net.bible.sharedui.reading.host.LocalReadingPane provides paneFactory,
                        net.bible.sharedui.reading.LocalPinIcon provides { painter },
                    ) {
                        net.bible.sharedui.theme.AbTheme(darkTheme = false) {
                            net.bible.sharedui.reading.host.ReadingHost(deps)
                        }
                    }
                }
                runOnIdle {
                    deps.controller.onWindowActivated(links.id.toString())
                    deps.state.openPaneMenu(links.id.toString(), net.bible.android.view.activity.page.screen.PaneMenuAnchor.Pane)
                }
                onNode(hasText("Change to normal window")).performClick()
                waitForIdle()
                val convertedId = runBlocking { withContext(main) {
                    control.windowRepository.windowList.single {
                        it.pageManager.currentPage.currentDocument == koin.get<net.bible.service.download.FakeBookFactory>().multiDocument
                    }.id.toString()
                } }
                assertFalse(engine.hasFailed(convertedId), "the replacement pane must attach successfully")
                assertNotNull(engine.paneFor(convertedId))
                onNode(androidx.compose.ui.test.hasTestTag("pane-$convertedId")).assertIsDisplayed()
            }
            runBlocking { withContext(main) {
                val converted = control.windowRepository.windowList.single { it.pageManager.currentPage.currentDocument == koin.get<net.bible.service.download.FakeBookFactory>().multiDocument }
                assertFalse(converted.isLinksWindow)
                assertEquals(keyBefore, converted.pageManager.currentPage.key?.getOsisRef())
                assertNull(control.windowRepository.getWindow(links.id))
                assertTrue(deps.controller.layout.value.windows.single { it.id == converted.id.toString() }.isVisible)
            } }
        } finally {
            runBlocking { withContext(main) { host.dispose() } }
        }
    }

    @Test fun appSettingsWritesUpdateEveryOpenPaneWithoutReloading() = runBlocking {
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
        withContext(main) {
            val host = HostReadingHost(scope, HostNavShell(scope, Navigator), koin, DesktopReadingHostPlatform(WindowHandle), Day)
            try {
                HostReadingWindows.bind(host, scope, koin, Day, engine)
                val first = control.activeWindow
                val windows = listOf(first, control.addNewWindow(first))
                val sessions = windows.map { window ->
                    val id = window.id.toString()
                    HostReadingWindows.sessionFor(id).also { session ->
                        engine.attach(id, false, HostReadingWindows.bridgeFor(id), {}, { session.jsChannel.flush() })
                        session.jsChannel.markClientReady()
                    }
                }
                val settings = koin.get<net.bible.sharedcore.settings.AppSettingsService>()
                fun assertWrite(fragment: String, write: () -> Unit) {
                    fake.panes.forEach { it.evaluated.clear() }
                    val generations = sessions.map { it.documentGeneration }
                    write()
                    fake.panes.forEach { pane ->
                        assertTrue(pane.evaluated.any { "set_config" in it && fragment in it },
                            "settings write must send $fragment to pane ${pane.id}: ${pane.evaluated}")
                    }
                    assertEquals(generations, sessions.map { it.documentGeneration }, "do not reload the chapter")
                    assertEquals(2, fake.panes.size, "do not recreate the native panes")
                }
                assertWrite("fontSizeMultiplier: 1.5") { settings.setInt("font_size_multiplier", 150) }
                assertWrite("einkMode: true") { settings.setBool("eink_mode", true) }
                assertWrite("disableClickToEdit:  true") { settings.setBool("disable_click_to_edit", true) }
                assertWrite("notesContentType: \"MARKDOWN\"") { settings.setString("notes_content_type", "MARKDOWN") }
                assertWrite("enabledExperimentalFeatures: [\"test-feature\"]") { settings.setStringSet("experimental_features", setOf("test-feature")) }
                // The service accepts enabled buttons, but the config receives the inverse disabled set.
                assertWrite("disableBibleModalButtons:") { settings.setStringSet("disable_bible_bookmark_modal_buttons", emptySet()) }
                fake.panes.forEach { it.evaluated.clear() }
                settings.setBool("volume_keys_scroll", false)
                assertTrue(fake.panes.all { pane -> pane.evaluated.none { "set_config" in it } }, "unrelated settings need no config")
            } finally { host.dispose() }
        }
    }

    @Test fun workspaceSwitchReusesOnePaneAndDisposesTheSpares() = runBlocking {
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
        val repository = control.windowRepository
        val fake = FakeWebEngine()
        val engine = DesktopReadingPaneEngine(fake, {}, koin = { koin })
        val target = WorkspaceEntities.Workspace(name = "Target", contentsText = null, orderNumber = 1)
        val dao = DatabaseContainer.instance.workspaceDb.workspaceDao()

        withContext(Dispatchers.Main) {
            val host = HostReadingHost(scope, HostNavShell(scope, Navigator), koin, DesktopReadingHostPlatform(WindowHandle), Day)
            HostReadingWindows.bind(host, scope, koin, Day, engine)
            val first = repository.activeWindow
            val second = control.addNewWindow(first)
            val third = control.addNewWindow(second)
            val outgoing = listOf(first, second, third)
            outgoing.forEach { window ->
                val id = window.id.toString()
                val session = HostReadingWindows.sessionFor(id)
                engine.attach(id, false, HostReadingWindows.bridgeFor(id), {}, { session.jsChannel.flush() })
                assertNotNull(window.bibleView)
            }
            assertEquals(3, fake.panes.size)
            val originalPanes = fake.panes.toList()
            val outgoingGenerations = outgoing.map { HostReadingWindows.sessionFor(it.id.toString()).documentGeneration }
            repository.saveIntoDb()
            repository.persistence.awaitPersisted()
            dao.insertWorkspace(target)

            // Use the host's real switch path, not a simulated repository notification.
            val actions = HostReadingQuickActions(
                windowControl = { control }, fakeBookFactory = { koin.get() }, settings = koin.get(),
                scope = scope, toast = {}, chooserTarget = { error("not used by workspace switch") },
            )
            actions.switchToWorkspace(target.id.toString(), saveOutgoing = false)
            val incoming = repository.activeWindow
            assertTrue(outgoing.none { it.id == incoming.id })
            val id = incoming.id.toString()
            val session = HostReadingWindows.sessionFor(id)
            engine.attach(id, false, HostReadingWindows.bridgeFor(id), {}, { session.jsChannel.flush() })
            val adopted = assertNotNull(engine.paneFor(id))
            assertTrue(originalPanes.any { it === adopted }, "the incoming window must adopt an outgoing native pane")
            // #3975: the adopted pane's old page still posts with its old document generation; a call that acts on
            // the window's current state implicitly must not reach the incoming window.
            val staleGeneration = outgoingGenerations[originalPanes.indexOfFirst { it === adopted }]
            (adopted as net.bible.sharedui.desktop.webengine.FakePane).onMessage(
                """{"method":"saveState","args":["outgoing page"],"documentGeneration":$staleGeneration}""")
            assertTrue(incoming.pageManager.jsState != "outgoing page", "a stale page call must not reach the incoming window")
            assertTrue(outgoing.all { it.bibleView == null }, "outgoing owners must detach their views")
            assertSame(adopted, engine.paneFor(id))
            // Simulate the completed-composition cleanup seam.
            HostReadingWindows.releaseSpares()
            assertEquals(3, fake.panes.size, "switching must not create a new native pane")
            assertEquals(2, originalPanes.count { it.disposed }, "unused outgoing panes must be disposed")
            assertFalse(originalPanes.single { it === adopted }.disposed)

            val staleView = incoming.bibleView
            val previousGeneration = HostReadingWindows.switchGeneration
            session.jsChannel.markClientReady()
            actions.switchToWorkspace(target.id.toString(), saveOutgoing = false)
            val reconstructed = repository.activeWindow
            assertEquals(incoming.id, reconstructed.id)
            assertTrue(incoming !== reconstructed)
            assertNull(incoming.bibleView)
            assertFalse(checkNotNull(staleView).listenEvents)
            assertNotNull(reconstructed.bibleView, "same-ID owner must be adopted without pane recomposition")
            assertFalse(session.jsChannel.loading, "adoption preserves page readiness")
            val before = session.firstDocument
            staleView.loadDocument(net.bible.android.control.page.ErrorDocument("stale", net.bible.android.control.page.ErrorSeverity.NORMAL), false, null, null, null, null)
            assertSame(before, session.firstDocument, "late outgoing delivery must be ignored")
            HostReadingWindows.releaseSpares(previousGeneration)
            assertFalse(originalPanes.single { it === adopted }.disposed, "older cleanup must not dispose a newer switch's pane")
            HostReadingWindows.releaseSpares()
            assertFalse(originalPanes.single { it === adopted }.disposed, "cleanup must not dispose the displayed same-ID pane")
            assertSame(session, HostReadingWindows.sessionFor(id))
            assertSame(adopted, engine.paneFor(id))
            assertSame(reconstructed, session.window)
            HostReadingWindows.releaseSpares()
        }
    }

    /** Waits (letting the main thread run) until [session] has admitted a document other than [not]. */
    private suspend fun awaitAdmitted(session: net.bible.sharedui.reading.session.BibleWindowSession, not: Long = -1L): Long {
        kotlinx.coroutines.withTimeout(20_000) {
            while (session.documentGeneration == net.bible.sharedcore.bibleview.NO_DOCUMENT_GENERATION ||
                session.documentGeneration == not) kotlinx.coroutines.delay(20)
        }
        return session.documentGeneration
    }

    /** Lets queued main-thread work (a guarded call's main-thread step) run. */
    private suspend fun settleMain() = repeat(5) { kotlinx.coroutines.delay(20) }

    private fun message(method: String, args: String, generation: Long) =
        """{"method":"$method","args":[$args],"documentGeneration":$generation}"""

    /**
     * #3975 reviews A1, A2, A4, through the desktop host's real pane message and link paths: after a workspace switch
     * the adopted pane's old page (a) has its modals closed, (b) cannot reach the incoming window with a call of its old
     * document or with a link, while (c) the same calls of the incoming window's own document do reach it. (c) fails if
     * the gate drops everything; (b) fails if it drops nothing.
     */
    @Test fun anAdoptedPanesOldPageCannotReachTheIncomingWindowButItsNewDocumentCan() = runBlocking {
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
        val repository = control.windowRepository
        val fake = FakeWebEngine()
        val engine = DesktopReadingPaneEngine(fake, {}, koin = { koin })
        val target = WorkspaceEntities.Workspace(name = "Target", contentsText = null, orderNumber = 1)
        val dao = DatabaseContainer.instance.workspaceDb.workspaceDao()
        val book = assertNotNull(org.crosswire.jsword.book.Books.installed().getBook("K4RawText"))

        withContext(Dispatchers.Main) {
            val host = HostReadingHost(scope, HostNavShell(scope, Navigator), koin, DesktopReadingHostPlatform(WindowHandle), Day)
            HostReadingWindows.bind(host, scope, koin, Day, engine)
            fun attach(id: String) {
                val session = HostReadingWindows.sessionFor(id)
                engine.attach(id, false, HostReadingWindows.bridgeFor(id), {}, { session.jsChannel.flush() },
                    openLink = { HostReadingWindows.openLink(id, it) })
                session.jsChannel.markClientReady()
            }
            val outgoing = repository.activeWindow
            outgoing.pageManager.setCurrentDocumentAndKey(book, book.getKey("John 6:58"))
            val outgoingId = outgoing.id.toString()
            attach(outgoingId)
            val outgoingSession = HostReadingWindows.sessionFor(outgoingId)
            outgoing.loadText()
            val old = awaitAdmitted(outgoingSession)
            val pane = engine.paneFor(outgoingId) as net.bible.sharedui.desktop.webengine.FakePane
            pane.onMessage(message("documentShown", "$old", old))
            yield() // documentShown reaches the admission state on the main thread, posted like any bridge call
            assertTrue(outgoingSession.pageShowsAdmittedDocument(), "fixture: the outgoing page shows its document")
            repository.saveIntoDb()
            repository.persistence.awaitPersisted()
            dao.insertWorkspace(target)

            val actions = HostReadingQuickActions(
                windowControl = { control }, fakeBookFactory = { koin.get() }, settings = koin.get(),
                scope = scope, toast = {}, chooserTarget = { error("not used by workspace switch") },
            )
            pane.evaluated.clear()
            actions.switchToWorkspace(target.id.toString(), saveOutgoing = false)
            val incoming = repository.activeWindow
            val id = incoming.id.toString()
            attach(id)
            val adopted = engine.paneFor(id) as net.bible.sharedui.desktop.webengine.FakePane
            assertSame(pane, adopted, "fixture: the incoming window adopts the outgoing pane")
            // A2: the old page's modals are closed when the pane is handed over.
            assertTrue(adopted.evaluated.any { it.contains("\"close_modals\"") }, "close_modals: ${adopted.evaluated}")

            incoming.pageManager.setCurrentDocumentAndKey(book, book.getKey("John 6:5"))
            incoming.loadText()
            val new = awaitAdmitted(HostReadingWindows.sessionFor(id), not = old)
            val state = { repository.windowList.map { it.id to it.pageManager.currentPage.key?.getOsisRef() } }
            val before = state()

            // The old page, before it has processed the new document's clear_document: its calls carry the old
            // generation, and it still reports showing the old document.
            adopted.onMessage(message("saveState", "\"outgoing page\"", old))
            adopted.onMessage(message("scrolledToOrdinal", "\"John.6\",${org.crosswire.jsword.passage.KeyUtil.getVerse(book.getKey("John 6:58")).ordinal}", old))
            adopted.navigate("osis://?osis=Gen.2.1")
            settleMain()
            assertTrue(incoming.pageManager.jsState != "outgoing page", "a call of the old document must not reach the incoming window")
            assertEquals(before, state(), "neither the old page's report nor its link may move or open a window")

            // The page has shown the incoming window's document: its calls and links act.
            adopted.onMessage(message("documentShown", "$new", new))
            yield() // documentShown reaches the admission state on the main thread, posted like any bridge call
            adopted.onMessage(message("saveState", "\"incoming page\"", new))
            settleMain()
            assertEquals("incoming page", incoming.pageManager.jsState, "a call of the window's own document must reach it")
            adopted.navigate("osis://?osis=Gen.2.1")
            settleMain()
            assertTrue(before != state(), "a link of the window's own document must open")
            HostReadingWindows.releaseSpares()
        }
    }

    @Test fun hostWindowCommandsRestoreSplitWindowIdentities() = runBlocking {
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
        withContext(Dispatchers.Main) {
            val host = HostReadingHost(scope, HostNavShell(scope, Navigator), koin, DesktopReadingHostPlatform(WindowHandle), Day)
            val painter = androidx.compose.ui.graphics.painter.ColorPainter(androidx.compose.ui.graphics.Color.Black)
            val deps = host.deps(net.bible.sharedui.reading.ReadingToolbarIcons(painter, painter, painter, painter, painter, painter, painter, painter, painter))
            val control = koin.get<WindowControl>()
            val repository = control.windowRepository
            try {
                val first = control.activeWindow
                deps.controller.onSetPin(first.id.toString(), true)
                deps.controller.onAddWindow(first.id.toString())
                val second = control.activeWindow
                val ids = setOf(first.id, second.id)
                assertEquals(2, ids.size)
                assertEquals(ids, repository.visibleWindows.map { it.id }.toSet())
                deps.handlePaneMenuItem(first.id.toString(), net.bible.android.view.activity.page.WindowPaneMenuStateBuilder.ID_WINDOW_MAXIMISE)
                assertEquals(first.id, repository.maximizedWindowId)
                deps.controller.onUnMaximise()
                assertNull(repository.maximizedWindowId)
                assertEquals(ids, repository.visibleWindows.map { it.id }.toSet())
                deps.handlePaneMenuItem(second.id.toString(), net.bible.android.view.activity.page.WindowPaneMenuStateBuilder.ID_WINDOW_MINIMISE)
                assertEquals(setOf(first.id), repository.visibleWindows.map { it.id }.toSet())
                deps.controller.onRestore(second.id.toString())
                assertEquals(ids, repository.visibleWindows.map { it.id }.toSet())
            } finally {
                host.dispose()
            }
        }
    }

    @OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
    @Test fun toolbarUsesRealHostCallbacksForWorkspaceAndOverflow() {
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
        runBlocking {
            assertEquals(StartupPhase.Ready, DesktopAppStartup.ensureStarted(paths, WindowHandle, "en", repositories = { PropertyMap() }, notifierSource = net.bible.sharedui.desktop.assembly.DesktopNotifierSource(net.bible.sharedcore.platform.desktop.dbus.DesktopDbus().supportedOs) { net.bible.sharedcore.platform.desktop.dbus.NoOpNativeNotifier }))
            HostReadingReady.ensure(HostStartup.stringTable)
        }
        val koin = KoinPlatform.getKoin()
        val host = runBlocking { withContext(Dispatchers.Main) {
            HostReadingHost(HostReadingReady.scope, HostNavShell(HostReadingReady.scope, Navigator), koin, DesktopReadingHostPlatform(WindowHandle), Day)
        } }
        val painter = androidx.compose.ui.graphics.painter.ColorPainter(androidx.compose.ui.graphics.Color.Black)
        val icons = net.bible.sharedui.reading.ReadingToolbarIcons(painter, painter, painter, painter, painter, painter, painter, painter, painter)
        val deps = runBlocking { withContext(Dispatchers.Main) {
            val book = checkNotNull(org.crosswire.jsword.book.Books.installed().getBook("K4RawText"))
            book.indexStatus = org.crosswire.jsword.index.IndexStatus.DONE
            koin.get<WindowControl>().activeWindow.pageManager.setCurrentDocumentAndKey(book, book.getKey("John 3:16"))
            host.deps(icons)
        } }
        try {
            runComposeUiTest {
                setContent {
                    androidx.compose.runtime.CompositionLocalProvider(net.bible.sharedui.strings.LocalStrings provides net.bible.sharedui.strings.iosStrings("en")) {
                        net.bible.sharedui.theme.AbTheme(darkTheme = false) {
                            val toolbar = deps.toolbar.collectAsState()
                            net.bible.sharedui.reading.ReadingToolbar(
                                // Desktop has no TTS; expose the iOS-supported button while keeping host callbacks real.
                                toolbar.value.copy(speakable = true, speakStopped = true), icons, deps.toolbarCallbacks,
                                searchBar = deps.searchBar.collectAsState().value,
                                searchBarCallbacks = deps.searchBarCallbacks,
                                overflowItems = deps.overflowItems.value,
                                overflowExpanded = deps.overflowExpanded.value,
                                onOverflowItemClick = deps.onOverflowItemClick,
                                onOverflowDismiss = deps.onOverflowDismiss,
                                modifier = androidx.compose.ui.Modifier.size(androidx.compose.ui.unit.Dp(1200f), androidx.compose.ui.unit.Dp(56f)),
                            )
                        }
                    }
                }
                onNode(androidx.compose.ui.test.hasContentDescription("Speak")).performClick()
                assertTrue(koin.get<net.bible.sharedcore.speak.SpeakTransportService>().state.value.visible)
                onNode(androidx.compose.ui.test.hasContentDescription("Find")).performClick()
                waitUntil(timeoutMillis = 5000) { deps.searchBar.value != null }
                onNode(androidx.compose.ui.test.hasTestTag(net.bible.sharedui.reading.SEARCH_FIELD_TAG)).assertExists()
                onNode(androidx.compose.ui.test.hasContentDescription(net.bible.sharedui.strings.iosStrings("en").searchClose)).performClick()
                onNode(androidx.compose.ui.test.hasContentDescription("Workspaces")).performClick()
                assertEquals(net.bible.sharedcore.reading.ReadingQuickSheet.Workspaces, host.hostState.quickSheet.value)
                runBlocking { withContext(Dispatchers.Main) { host.hostState.closeQuickSheet() } }
                onNode(androidx.compose.ui.test.hasContentDescription("More options")).performClick()
                onNode(androidx.compose.ui.test.hasText("Night mode")).assertExists()
                onNode(androidx.compose.ui.test.hasText("All text options …")).assertExists()
            }
        } finally {
            runBlocking { withContext(Dispatchers.Main) { host.dispose() } }
        }
    }

    @Test fun drawerProgressChapterReturnsToAskingWindow() = runBlocking {
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
        val routes = kotlinx.coroutines.channels.Channel<String>(kotlinx.coroutines.channels.Channel.UNLIMITED)
        val navigator = object : HostNavigator {
            override fun navigate(route: String) { routes.trySend(route) }
            override fun popBackStack() = true
            override fun popBackStackTo(route: String, inclusive: Boolean) = false
        }
        val shell = HostNavShell(scope, navigator)
        val channels = net.bible.sharedui.host.app.hostResultChannels({ error("not used") }, {})
        val control = koin.get<WindowControl>()
        val results = HostReadingResults(
            channels.keyChooser, channels.passage, channels.document, channels.myDocumentPages, channels.myDocuments,
            channels.bookmark, channels.readingProgress, channels.workspace,
            targetFor = { asker -> (asker as? net.bible.android.control.page.CurrentPageManager)?.let {
                hostChooserTarget(koin, HostStartup.stringTable, it, scope)
            } },
            activeAskerProvider = { control.activeWindowPageManager },
        )
        val host = withContext(Dispatchers.Main) {
            HostReadingHost(scope, shell, koin, DesktopReadingHostPlatform(WindowHandle), Day, readingResults = results)
        }
        try {
            val painter = androidx.compose.ui.graphics.painter.ColorPainter(androidx.compose.ui.graphics.Color.Black)
            val deps = withContext(Dispatchers.Main) {
                val book = checkNotNull(org.crosswire.jsword.book.Books.installed().getBook("K4RawText"))
                control.activeWindow.pageManager.setCurrentDocumentAndKey(book, book.getKey("John 3:16"))
                host.deps(net.bible.sharedui.reading.ReadingToolbarIcons(painter, painter, painter, painter, painter, painter, painter, painter, painter))
            }
            val asking = control.activeWindow
            withContext(Dispatchers.Main) { deps.onDrawerItemClick("readingProgressButton") }
            assertEquals(net.bible.sharedcore.nav.NavRoutes.readingProgress(0), kotlinx.coroutines.withTimeout(5000) { routes.receive() })
            assertTrue(results.isAwaiting(ChooserKind.ReadingProgress))

            val settings = net.bible.sharedui.app.settings.SettingsHost(
                shell, net.bible.sharedui.strings.iosStrings("en"), { koin.get() },
                koin.get(), koin.get(), koin.get(),
                object : net.bible.sharedui.app.ports.SystemScreens { override fun openAppLinkSettings() = Unit },
                object : net.bible.sharedui.app.ports.HelpDialogs { override fun show(topic: net.bible.sharedui.app.ports.HelpTopic) = Unit },
                { error("unused") }, { error("unused") }, { error("unused") },
                { koin.get<net.bible.android.control.progress.ReadingProgressServiceImpl>() },
            )
            val progress = settings.readingProgressControllerFor(0, {}, results::onReadingProgress)
            progress.load()
            val model = kotlinx.coroutines.withTimeout(20_000) { progress.model.first { it.ntBooks.isNotEmpty() } }
            assertTrue(model.otBooks.isNotEmpty())
            val john = model.ntBooks.single { it.bookId == "JOHN" }
            progress.openChapterDetail(john.bookId)
            val detail = kotlinx.coroutines.withTimeout(20_000) { progress.model.first { it.chapterDetail?.bookId == john.bookId } }.chapterDetail!!
            assertTrue(detail.chapters.any { it.chapter == 4 })
            withContext(Dispatchers.Main) { progress.chapterTap(john.bookId, 4) }
            assertEquals("John.4.1", asking.pageManager.currentBible.singleKey.getOsisRef())
            assertFalse(results.isAwaiting(ChooserKind.ReadingProgress))
        } finally {
            withContext(Dispatchers.Main) { host.dispose() }
        }
    }

    /**
     * #4028: an app language change (Application preferences writes `locale_pref` and the shell switches the string
     * table) reaches the live reading view without a restart: the drawer rows, the toolbar's Bible book name (JSword)
     * and the BibleView pages, which reload in the new language.
     */
    @Test fun appLanguageChangeReachesTheDrawerTheToolbarBookNameAndThePanes() = runBlocking {
        val previousLocale = java.util.Locale.getDefault()
        java.util.Locale.setDefault(java.util.Locale.US)
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
        try {
            assertEquals(StartupPhase.Ready, DesktopAppStartup.ensureStarted(paths, WindowHandle, "en", repositories = { PropertyMap() }, notifierSource = net.bible.sharedui.desktop.assembly.DesktopNotifierSource(net.bible.sharedcore.platform.desktop.dbus.DesktopDbus().supportedOs) { net.bible.sharedcore.platform.desktop.dbus.NoOpNativeNotifier }))
            HostReadingReady.ensure(HostStartup.stringTable)
            val koin = KoinPlatform.getKoin()
            val scope = HostReadingReady.scope
            val fake = FakeWebEngine()
            val engine = DesktopReadingPaneEngine(fake, {}, koin = { koin })
            val holder = koin.get<net.bible.sharedui.assembly.HostStringTableHolder>()
            val painter = androidx.compose.ui.graphics.painter.ColorPainter(androidx.compose.ui.graphics.Color.Gray)
            val icons = net.bible.sharedui.reading.ReadingToolbarIcons(painter, painter, painter, painter, painter, painter, painter, painter, painter)
            withContext(Dispatchers.Main) {
                val host = HostReadingHost(scope, HostNavShell(scope, Navigator), koin, DesktopReadingHostPlatform(WindowHandle), Day)
                try {
                    HostReadingWindows.bind(host, scope, koin, Day, engine)
                    val window = koin.get<WindowControl>().activeWindow
                    val book = checkNotNull(org.crosswire.jsword.book.Books.installed().getBook("K4RawText"))
                    window.pageManager.setCurrentDocumentAndKey(book, book.getKey("John 3:16"))
                    val deps = host.deps(icons)
                    val id = window.id.toString()
                    fun attach() {
                        val session = HostReadingWindows.sessionFor(id)
                        engine.attach(id, false, HostReadingWindows.bridgeFor(id), {}, { session.jsChannel.flush() })
                    }
                    attach()
                    settleMain()
                    val englishPane = fake.live(id)
                    assertTrue("lang=en" in englishPane.url, englishPane.url)
                    assertTrue(host.drawerMenuState.value.groups.flatMap { it.items }.any { it.label == "Choose Document" })
                    assertTrue(deps.toolbar.value.pageTitle.startsWith("John"), deps.toolbar.value.pageTitle)

                    koin.get<net.bible.sharedcore.platform.desktop.DesktopDeviceSettings>().setString("locale_pref", "fi")
                    holder.switchTo("fi")
                    // The host's language collector and the page rebuild run on Main after this coroutine yields.
                    kotlinx.coroutines.withTimeout(10_000) {
                        while (!englishPane.disposed || !deps.toolbar.value.pageTitle.startsWith("Johannes")) kotlinx.coroutines.delay(20)
                    }

                    val labels = host.drawerMenuState.value.groups.flatMap { it.items }.map { it.label }
                    assertTrue("Valitse teos" in labels, "the drawer must follow the new language: $labels")
                    assertTrue(deps.toolbar.value.pageTitle.startsWith("Johannes"), deps.toolbar.value.pageTitle)
                    assertTrue(englishPane.disposed, "the page loaded in the old language must be replaced")
                    attach()
                    val finnishPane = fake.live(id)
                    assertTrue("lang=fi" in finnishPane.url, finnishPane.url)
                } finally {
                    host.dispose()
                }
            }
        } finally {
            java.util.Locale.setDefault(previousLocale)
        }
    }

    @OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
    @Test fun hostDrawerRowsRemainReachableAtAx5() = drawerChromeJourney("en")

    @OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
    @Test fun finnishHostDrawerAndOverflowExposeNoTranslatedEnglish() = drawerChromeJourney("fi")

    @OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
    private fun drawerChromeJourney(language: String) {
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
        runBlocking {
            assertEquals(StartupPhase.Ready, DesktopAppStartup.ensureStarted(paths, WindowHandle, "en", repositories = { PropertyMap() }, notifierSource = net.bible.sharedui.desktop.assembly.DesktopNotifierSource(net.bible.sharedcore.platform.desktop.dbus.DesktopDbus().supportedOs) { net.bible.sharedcore.platform.desktop.dbus.NoOpNativeNotifier }))
            HostReadingReady.ensure(HostStartup.stringTable)
        }
        val koin = KoinPlatform.getKoin()
        koin.get<net.bible.sharedui.assembly.HostStringTableHolder>().switchTo(language)
        var drawer by androidx.compose.runtime.mutableStateOf(true)
        val host = runBlocking { withContext(Dispatchers.Main) {
            HostReadingHost(HostReadingReady.scope, HostNavShell(HostReadingReady.scope, Navigator), koin, DesktopReadingHostPlatform(WindowHandle), Day)
        } }
        val painter = androidx.compose.ui.graphics.painter.ColorPainter(androidx.compose.ui.graphics.Color.Gray)
        val deps = runBlocking { withContext(Dispatchers.Main) {
            host.deps(net.bible.sharedui.reading.ReadingToolbarIcons(painter, painter, painter, painter, painter, painter, painter, painter, painter))
        } }
        try {
            androidx.compose.ui.test.v2.runSkikoComposeUiTest(size = androidx.compose.ui.geometry.Size(390f, 844f)) {
                setContent {
                    androidx.compose.runtime.CompositionLocalProvider(
                        net.bible.sharedui.strings.LocalStrings provides net.bible.sharedui.strings.iosStrings(language),
                        androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(1f, 53f / 17f),
                        net.bible.sharedui.components.LocalBarFontScaleCap provides 1.35f,
                        net.bible.sharedui.reading.LocalDrawerRowsGrowWithText provides true,
                    ) {
                        net.bible.sharedui.theme.AbTheme(darkTheme = false, disableAnimations = true) {
                            androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.size(androidx.compose.ui.unit.Dp(390f), androidx.compose.ui.unit.Dp(844f))) {
                                if (drawer) net.bible.sharedui.reading.ReadingDrawerContent(host.drawerMenuState.value, { null }, {})
                                else net.bible.sharedui.reading.ReadingOverflowMenuRows(deps.overflowItems.value, {}, { null })
                            }
                        }
                    }
                }
                waitUntil(timeoutMillis = 5000) { host.drawerMenuState.value.groups.isNotEmpty() }
                val labels = if (language == "fi") listOf("Valitse teos", "Opintoalustat", "Historia", "Varmuuskopioi & palauta", "Laitteiden synkronointi")
                    else listOf("Choose Document", "Study Pads", "History", "Backup & Restore", "Device synchronization")
                for (label in labels) {
                    onNode(hasScrollAction()).performScrollToNode(hasText(label))
                    val row = onNode(hasText(label))
                    row.assertIsDisplayed()
                    val bounds = row.fetchSemanticsNode().boundsInRoot
                    assertTrue(bounds.left >= 0f && bounds.right <= 390f && bounds.top >= 0f && bounds.bottom <= 844f,
                        "$label must fit in the viewport at AX5: $bounds")
                    val text = onNode(hasText(label), useUnmergedTree = true).fetchSemanticsNode()
                    val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
                    text.config[androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult].action!!.invoke(layouts)
                    assertFalse(layouts.single().didOverflowHeight, "$label must not be vertically clipped at AX5")
                    if (language == "fi") with(net.bible.sharedui.strings.FinnishChromeAssertions) {
                        assertNoTranslatedEnglish("reading drawer at $label")
                    }
                }
                if (language == "fi") {
                    runOnIdle { host.callbacks().onOverflow(); drawer = false }
                    onNode(hasText("Yötila")).assertIsDisplayed()
                    with(net.bible.sharedui.strings.FinnishChromeAssertions) { assertNoTranslatedEnglish("reading overflow") }
                }
            }
        } finally {
            runBlocking { withContext(Dispatchers.Main) { host.dispose() } }
        }
    }

    private fun snapshotCwProject(): () -> Unit {
        fun field(name: String) = (runCatching { CWProject::class.java.getDeclaredField(name) }.getOrNull()
            ?: CWProject.Companion::class.java.getDeclaredField(name)).apply { isAccessible = true }
        fun owner(f: java.lang.reflect.Field): Any? = if (java.lang.reflect.Modifier.isStatic(f.modifiers)) null else CWProject.Companion
        val statics = listOf("homeProperty", "homeDirectory", "homeAltDirectory").map(::field)
        val values = statics.map { it.get(owner(it)) }
        val instanceField = field("instance")
        val instance = instanceField.get(owner(instanceField))
        val established = CWProject::class.java.getDeclaredField("writeHome").apply { isAccessible = true }.get(instance) != null
        return {
            statics.zip(values).forEach { (f, v) -> f.set(owner(f), v) }
            if (established) instanceField.set(owner(instanceField), instance) else {
                val reset = runCatching { CWProject::class.java.getDeclaredMethod("reset") }
                    .getOrElse { CWProject.Companion::class.java.getDeclaredMethod("reset") }
                reset.isAccessible = true
                reset.invoke(if (java.lang.reflect.Modifier.isStatic(reset.modifiers)) null else CWProject.Companion)
            }
        }
    }
}
