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
package net.bible.sharedui.ios.reading

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import net.bible.sharedui.assembly.awaitStartupOnMain
import kotlinx.coroutines.runBlocking
import net.bible.android.control.page.window.WindowChange
import net.bible.android.control.page.window.WindowControl
import net.bible.android.control.page.window.WindowLayout
import net.bible.android.control.page.window.WindowStateServiceImpl
import net.bible.android.database.IdType
import net.bible.service.startup.ReadingAppInit
import net.bible.sharedcore.bibleview.BibleViewEvents
import net.bible.sharedlogic.platform.NightModeState
import net.bible.sharedui.assembly.InMemorySecretStore
import net.bible.sharedui.assembly.IosAppConfig
import net.bible.sharedui.assembly.IosAppStartup
import net.bible.sharedui.assembly.IosAssemblyTest
import net.bible.sharedui.ios.app.IosHostShell
import net.bible.sharedui.ios.app.IosNavigator
import net.bible.sharedui.ios.app.IosReadingReady
import net.bible.sharedui.reading.session.ReadingOffsets
import net.bible.sharedui.webview.IosReadingWebViews
import net.bible.sharedui.webview.activeLanguagesScript
import net.bible.sharedui.webview.attachReadingPane
import net.bible.sharedui.webview.bibleBundleUrl
import org.koin.mp.KoinPlatform
import platform.Foundation.NSDate
import platform.Foundation.NSRunLoop
import platform.Foundation.NSURL
import platform.Foundation.dateWithTimeIntervalSinceNow
import platform.Foundation.runUntilDate
import platform.WebKit.WKScriptMessage
import platform.WebKit.WKScriptMessageHandlerProtocol
import platform.WebKit.WKUserContentController
import platform.darwin.NSObject

/** Per-window session assembly: caching, release (relay stop, dispose, handler removal), closed windows, night. */
class IosReadingWindowsTest {
    private object NoNavigator : IosNavigator {
        override fun navigate(route: String) = Unit
        override fun popBackStack(): Boolean = false
        override fun popBackStackTo(route: String, inclusive: Boolean): Boolean = false
    }

    private class FakeNight(var dark: Boolean) : NightModeState {
        override val nightMode: Boolean get() = dark
    }

    private class NoopHandler : NSObject(), WKScriptMessageHandlerProtocol {
        override fun userContentController(userContentController: WKUserContentController, didReceiveScriptMessage: WKScriptMessage) = Unit
    }

    /** Runs [block] on the main dispatcher while the main run loop keeps spinning (runBlocking would starve Main). */
    private fun <T> awaitOnMain(block: suspend () -> T): T {
        val scope = MainScope()
        try {
            val job = scope.async { block() }
            while (!job.isCompleted) NSRunLoop.mainRunLoop.runUntilDate(NSDate.dateWithTimeIntervalSinceNow(0.05))
            return job.getCompleted()
        } finally {
            scope.cancel()
        }
    }

    private var currentHost: IosReadingHost? = null

    private fun withWindows(night: FakeNight = FakeNight(false), body: (windowId: String) -> Unit) {
        try {
            awaitStartupOnMain { IosAppStartup.ensureStarted(
                IosAppConfig(
                    root = IosAssemblyTest.sharedRoot, uiLanguage = "en",
                    repositories = org.crosswire.common.util.PropertyMap(), secretStore = InMemorySecretStore(),
                ),
            ) }
            awaitStartupOnMain { IosAppStartup.openDatabases() }
            // The production step (initialise, prepareData, window repository), not a hand-ordered copy of it.
            awaitOnMain { IosReadingReady.ensure(IosAppStartup.stringTable) }
            val scope: CoroutineScope = IosReadingReady.scope
            val koin = KoinPlatform.getKoin()
            val host = IosReadingHost(scope, IosHostShell(scope, NoNavigator), koin, night)
            currentHost = host
            IosReadingWindows.bind(host, scope, koin, night)
            IosReadingWebViews.bundleLoader = { _, _, _ -> }
            body(koin.get<WindowControl>().activeWindow.id.toString())
        } finally {
            IosReadingWindows.resetForTest()
            IosReadingWebViews.resetForTest()
            IosReadingReady.resetForTest()
            ReadingAppInit.resetForTest()
            awaitStartupOnMain { IosAppStartup.resetForTest() }
        }
    }

    private fun attach(id: String, night: () -> Boolean = { false }) = attachReadingPane(
        id, IosReadingWindows::sessionFor, IosReadingWindows::bridgeFor, night, IosReadingWindows::isOpen,
    )

    /** The loader platform reads the live [ReadingOffsets]: none at the top, the safe area (and sheets) at the bottom pane's foot. */
    @Test
    fun offsetsFollowTheToolbarAndSafeArea() = withWindows { _ ->
        val koin = KoinPlatform.getKoin()
        val windowControl = koin.get<WindowControl>()
        val offsets = ReadingOffsets()
        val platform = IosDocumentLoaderPlatform(
            window = { windowControl.activeWindow }, windowControl = { windowControl }, settings = { koin.get() },
            nightModeState = { FakeNight(false) }, offsets = { offsets }, isPortrait = { true },
        )
        assertEquals(0f, platform.topOffset)
        assertEquals(0f, platform.bottomOffset)
        offsets.setSystemBottomInset(34f)
        assertEquals(0f, platform.topOffset)
        assertEquals(34f, platform.bottomOffset)
    }

    /** The top safe-area inset (full screen) belongs to the topmost pane of a stacked split; side by side, to every pane. */
    @Test
    fun topOffsetOnlyForTheTopmostStackedPane() = withWindows { _ ->
        val koin = KoinPlatform.getKoin()
        val windowControl = koin.get<WindowControl>()
        val first = windowControl.windowRepository.activeWindow
        val second = windowControl.addNewWindow(first)
        val offsets = ReadingOffsets().also { it.setSystemTopInset(59f) }
        fun platform(window: net.bible.android.control.page.window.Window, portrait: Boolean) = IosDocumentLoaderPlatform(
            window = { window }, windowControl = { windowControl }, settings = { koin.get() },
            nightModeState = { FakeNight(false) }, offsets = { offsets }, isPortrait = { portrait },
        )
        try {
            assertEquals(59f, platform(first, portrait = true).topOffset)
            assertEquals(0f, platform(second, portrait = true).topOffset, "a lower stacked pane clears nothing at the top")
            assertEquals(59f, platform(second, portrait = false).topOffset, "side by side, every pane touches the top")
        } finally {
            // The window lives in the shared workspace database; leave it as found (a later test expects one window).
            windowControl.closeWindow(second)
        }
    }

    /** The WebViews get the installed Bibles' languages (whatever this test's module set holds), not a constant. */
    @Test
    fun activeLanguagesFromInstalledBooks() = withWindows { _ ->
        val languages = kotlinx.serialization.json.Json.decodeFromString<List<String>>(IosReadingWebViews.activeLanguages())
        val installed = net.bible.service.sword.SwordDocumentFacade.bibles.map { it.bookMetaData.language.code }.toSet()
        assertTrue(installed.isNotEmpty(), "the fixture installs at least one Bible")
        assertEquals(installed, languages.toSet(), "languages: $languages")
        assertEquals(languages.size, languages.toSet().size, "no duplicates: $languages")
        assertEquals("window.__activeLanguages__ = '[\"en\",\"fi\"]';", activeLanguagesScript("[\"en\",\"fi\"]"))
    }

    /** The session's key actions and verse chooser are the real ones: Alt+M opens the drawer, Ctrl+W the workspace sheet, and the chooser answers through the Grid sheet. */
    @Test
    fun sessionKeyActionsAndVerseChooserAreWired() = withWindows { id ->
        val session = IosReadingWindows.sessionFor(id)
        val hostState = currentHost!!.hostState
        val keys = session.keyActions
        assertTrue(keys is IosReadingKeyActions, "key actions: $keys")
        assertTrue(keys.showWorkspaceSheetIfHosted())
        keys.openDrawer()
        assertTrue(hostState.drawerOpen.value, "Alt+M opens the drawer")
        val answer = awaitOnMain {
            kotlinx.coroutines.coroutineScope {
            val pending = async(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) { session.verseChooser!!.invoke() }
            hostState.onGridPassageChosen("Gen.1.1")
            pending.await()
            }
        }
        assertEquals("Gen.1.1", answer)
    }

    /** A fresh (empty) database gives exactly one visible window, like Android. */
    @Test
    fun freshWorkspaceHasExactlyOneVisibleWindow() = withWindows { _ ->
        val repository = KoinPlatform.getKoin().get<WindowControl>().windowRepository
        assertEquals(1, repository.visibleWindows.size, "visible windows: ${repository.visibleWindows}")
    }

    /** Closing a window through WindowControl reaches the WindowsChanged subscription, which releases its assembly. */
    @Test
    fun closingAWindowThroughWindowControlReleasesItsAssembly() = withWindows { firstId ->
        val windowControl = KoinPlatform.getKoin().get<WindowControl>()
        val second = windowControl.addNewWindow(windowControl.windowRepository.activeWindow)
        val secondId = second.id.toString()
        IosReadingWindows.sessionFor(secondId)
        assertTrue(IosReadingWindows.hasAssembly(secondId))
        windowControl.closeWindow(second)
        // The subscription posts the release to Main; keep the run loop spinning until it ran.
        var spins = 0
        while (IosReadingWindows.hasAssembly(secondId) && spins++ < 100) { // up to ~5 s
            NSRunLoop.mainRunLoop.runUntilDate(NSDate.dateWithTimeIntervalSinceNow(0.05))
        }
        assertFalse(IosReadingWindows.hasAssembly(secondId), "closing the window must release its assembly")
        assertFalse(IosReadingWebViews.hasEntry(secondId))
        assertTrue(firstId.isNotEmpty())
    }

    /**
     * Three windows, close the middle one, add a window again: each window has its own session and WebView with the
     * bridge registered once; the closed one is disposed and gone; the survivors keep their instances; the new window
     * is a different id with a fresh pair (the Kotlin side of the XCUITest "text appears once" scenario).
     */
    @Test
    fun splitThreeCloseMiddleReopenKeepsOneSessionPerWindow() = withWindows { firstId ->
        val windowControl = KoinPlatform.getKoin().get<WindowControl>()
        val second = windowControl.addNewWindow(windowControl.windowRepository.activeWindow)
        val third = windowControl.addNewWindow(second)
        val ids = listOf(firstId, second.id.toString(), third.id.toString())
        assertEquals(3, ids.toSet().size)
        val sessions = ids.map { id -> attach(id); IosReadingWindows.sessionFor(id) }
        assertEquals(3, sessions.toSet().size, "one session per window")
        assertEquals(3, ids.map { IosReadingWebViews.entry(it).webView }.toSet().size, "one WebView per window")
        ids.forEach { assertEquals(1, IosReadingWebViews.bridgeHandlerCount(it)) }
        val firstView = IosReadingWebViews.entry(ids[0]).webView
        val thirdView = IosReadingWebViews.entry(ids[2]).webView

        windowControl.closeWindow(second)
        var spins = 0
        while (IosReadingWindows.hasAssembly(ids[1]) && spins++ < 100) {
            NSRunLoop.mainRunLoop.runUntilDate(NSDate.dateWithTimeIntervalSinceNow(0.05))
        }
        assertFalse(IosReadingWindows.hasAssembly(ids[1]), "the closed middle window is released")
        assertFalse(IosReadingWebViews.hasEntry(ids[1]))
        assertTrue(sessions[1].isDisposed)
        assertFalse(sessions[0].isDisposed || sessions[2].isDisposed, "the other windows keep their sessions")
        assertSame(sessions[0], IosReadingWindows.sessionFor(ids[0]))
        assertSame(sessions[2], IosReadingWindows.sessionFor(ids[2]))
        assertSame(firstView, IosReadingWebViews.entry(ids[0]).webView)
        assertSame(thirdView, IosReadingWebViews.entry(ids[2]).webView)

        val reopened = windowControl.addNewWindow(windowControl.windowRepository.activeWindow)
        val reopenedId = reopened.id.toString()
        assertFalse(reopenedId in ids, "a reopened window is a new window")
        attach(reopenedId)
        assertEquals(1, IosReadingWebViews.bridgeHandlerCount(reopenedId))
        assertFalse(sessions.any { it === IosReadingWindows.sessionFor(reopenedId) })
        ids.filterNot { it == ids[1] }.forEach { assertEquals(1, IosReadingWebViews.bridgeHandlerCount(it)) }
    }

    /** The passage is written only by a save: after the save hook the database row holds the changed key. */
    @Test
    fun saveNowPersistsTheActivePassage() = withWindows { id ->
        val koin = KoinPlatform.getKoin()
        val windowControl = koin.get<WindowControl>()
        val pageManager = windowControl.activeWindowPageManager
        val key = org.crosswire.jsword.passage.PassageKeyFactory.instance()
            .getKey(net.bible.android.database.bookmarks.KJVA, "John.3.16")
        pageManager.setCurrentDocumentAndKey(null, key)
        val live = pageManager.entity.biblePage.verse
        val dao = net.bible.service.db.DatabaseContainer.instance.workspaceDb.workspaceDao()
        val before = runBlocking { dao.pageManager(IdType(id)) }?.biblePage?.verse
        assertTrue(before != live, "precondition: the stored passage differs from the changed one")
        runBlocking { net.bible.sharedui.ios.app.IosWorkspaceSaver.saveNow(koin) }
        assertEquals(live, runBlocking { dao.pageManager(IdType(id)) }?.biblePage?.verse)
    }

    /**
     * A window restored from the database gets its first document load only from the reading host's initial
     * `reloadAllWindows(true)` (Android runs it when the host is built): binding must force a resync of all windows.
     * Without that call `lastForceSyncAll` keeps its construction-time value and a restored window stays blank.
     */
    @Test
    fun bindingTheReadingHostForcesTheInitialContentLoad() = withWindows { _ ->
        val koin = KoinPlatform.getKoin()
        val sync = koin.get<WindowControl>().windowSync
        val before = sync.lastForceSyncAll
        NSRunLoop.mainRunLoop.runUntilDate(NSDate.dateWithTimeIntervalSinceNow(0.05)) // let the clock advance
        val scope = IosReadingReady.scope
        val night = FakeNight(false)
        IosReadingWindows.bind(IosReadingHost(scope, IosHostShell(scope, NoNavigator), koin, night), scope, koin, night)
        assertTrue(sync.lastForceSyncAll > before, "bind must call windowSync.reloadAllWindows(true)")
    }

    @Test
    fun saveNowSkipsWhenReadingIsNotReady() = withWindows { id ->
        val koin = KoinPlatform.getKoin()
        val pageManager = koin.get<WindowControl>().activeWindowPageManager
        val key = org.crosswire.jsword.passage.PassageKeyFactory.instance()
            .getKey(net.bible.android.database.bookmarks.KJVA, "John.3.16")
        pageManager.setCurrentDocumentAndKey(null, key)
        val dao = net.bible.service.db.DatabaseContainer.instance.workspaceDb.workspaceDao()
        val before = runBlocking { dao.pageManager(IdType(id)) }?.biblePage?.verse
        runBlocking { net.bible.sharedui.ios.app.IosWorkspaceSaver.saveNow(koin, ready = { false }) }
        assertEquals(before, runBlocking { dao.pageManager(IdType(id)) }?.biblePage?.verse, "not ready: nothing is saved")
    }

    /** WillResignActive and DidEnterBackground fire back to back: the two saves must never overlap. */
    @Test
    fun concurrentSaveNowCallsDoNotOverlap() = withWindows { _ ->
        val koin = KoinPlatform.getKoin()
        var active = 0
        var maxActive = 0
        var calls = 0
        val save: (net.bible.android.control.page.window.WindowRepository) -> Unit = {
            active++; calls++
            if (active > maxActive) maxActive = active
            platform.posix.usleep(100_000u) // long enough that an unserialised second call would overlap
            active--
        }
        runBlocking {
            val a = async(Dispatchers.Default) { net.bible.sharedui.ios.app.IosWorkspaceSaver.saveNow(koin, { true }, save) }
            val b = async(Dispatchers.Default) { net.bible.sharedui.ios.app.IosWorkspaceSaver.saveNow(koin, { true }, save) }
            a.await(); b.await()
        }
        assertEquals(2, calls)
        assertEquals(1, maxActive, "saveIntoDb must not run concurrently")
    }

    @Test
    fun sessionForIsCachedPerWindow() = withWindows { id ->
        val a = IosReadingWindows.sessionFor(id)
        assertSame(a, IosReadingWindows.sessionFor(id))
        IosReadingWindows.release(id)
        assertNotSame(a, IosReadingWindows.sessionFor(id))
    }

    /**
     * The relay is seen through what it forwards: a layout configuration change reaches the window's relay platform
     * while it runs (positive control) and must not after release. Without `relay.stop()` the second notify counts.
     */
    @Test
    fun releaseDisposesAndStopsTheRelay() = withWindows { id ->
        val session = IosReadingWindows.sessionFor(id)
        val platform = checkNotNull(IosReadingWindows.relayPlatformFor(id))
        val windowService = KoinPlatform.getKoin().get<WindowStateServiceImpl>()
        windowService.notify(WindowChange.LayoutConfigurationChanged)
        assertEquals(1, platform.windowsCheckMarks, "a live relay forwards the change")
        IosReadingWindows.release(id)
        windowService.notify(WindowChange.LayoutConfigurationChanged)
        assertEquals(1, platform.windowsCheckMarks, "a released window's relay must not forward")
        assertTrue(session.isDisposed)
        assertFalse(session.emit(BibleViewEvents.response(1L, null)))
    }

    /**
     * Against the real WKUserContentController: after release, registering "bridge" on the released WebView again must
     * not throw (WebKit raises an NSException, which aborts the test binary, if the name is still registered), so
     * deleting `removeScriptMessageHandlerForName` fails this test. The converse (adding while registered throws) cannot
     * be asserted: Kotlin/Native cannot catch the NSException.
     */
    @Test
    fun reattachAfterReleaseRegistersTheBridgeOnce() = withWindows { id ->
        attach(id)
        attach(id)
        assertEquals(1, IosReadingWebViews.bridgeHandlerCount(id))
        val released = IosReadingWebViews.entry(id).webView
        IosReadingWindows.release(id)
        val controller = released.configuration.userContentController
        val probe = NoopHandler()
        controller.addScriptMessageHandler(probe, "bridge")
        controller.removeScriptMessageHandlerForName("bridge")
        attach(id)
        assertEquals(1, IosReadingWebViews.bridgeHandlerCount(id))
        assertNotSame(released, IosReadingWebViews.entry(id).webView)
    }

    @Test
    fun nightParameterFollowsNightModeState() {
        val night = FakeNight(true)
        val seen = mutableListOf<Boolean>()
        withWindows(night) { id ->
            IosReadingWebViews.bundleLoader = { _, _, dark -> seen += dark }
            attach(id) { IosReadingWindows.nightMode }
            IosReadingWindows.release(id)
            night.dark = false
            attach(id) { IosReadingWindows.nightMode }
        }
        assertEquals(listOf(true, false), seen)
        val index = NSURL.fileURLWithPath("/x/index.html")
        assertTrue(bibleBundleUrl(index, "en", true).absoluteString!!.contains("night=true"))
        assertTrue(bibleBundleUrl(index, "en", false).absoluteString!!.contains("night=false"))
    }

    @Test
    fun nightObserverFollowsStateAndReportsChanges() {
        val night = FakeNight(false)
        var changes = 0
        val observer = IosNightModeObserver(night) { changes++ }
        assertFalse(observer.dark.value)
        night.dark = true
        assertTrue(observer.refresh())
        assertTrue(observer.dark.value)
        observer.refresh() // unchanged: no second report
        night.dark = false
        observer.refresh()
        assertEquals(2, changes)
    }

    /** A missing or closed window is never (re)assembled by the pane path: no session, no WebView entry. */
    @Test
    fun closedOrMissingWindowIsNotReassembled() = withWindows { id ->
        val missing = IdType().toString()
        attach(missing)
        assertFalse(IosReadingWebViews.hasEntry(missing))
        assertFalse(IosReadingWindows.hasAssembly(missing))
        assertFails { IosReadingWindows.sessionFor(missing) }
        assertFalse(IosReadingWebViews.hasEntry(missing))
        assertFalse(IosReadingWindows.hasAssembly(missing))

        IosReadingWindows.sessionFor(id)
        attach(id)
        val window = KoinPlatform.getKoin().get<WindowControl>().windowRepository.getWindow(IdType(id))!!
        val before = window.windowState
        try {
            window.windowState = WindowLayout.WindowState.CLOSED
            IosReadingWindows.release(id)
            attach(id) // a recomposition after the release
            assertFalse(IosReadingWebViews.hasEntry(id), "no WebView entry for a closed window")
            assertFalse(IosReadingWindows.hasAssembly(id), "no session for a closed window")
            assertFails { IosReadingWindows.sessionFor(id) }
            assertFalse(IosReadingWebViews.hasEntry(id))
        } finally {
            window.windowState = before
        }
    }

    @Test
    fun readyEnsureRunsTheStepsInOrderOnceAndRetriesAfterAFailure() {
        val order = mutableListOf<String>()
        val scopes = mutableListOf<CoroutineScope>()
        var failFirst = true
        try {
            awaitStartupOnMain { IosAppStartup.ensureStarted(
                IosAppConfig(
                    root = IosAssemblyTest.sharedRoot, uiLanguage = "en",
                    repositories = org.crosswire.common.util.PropertyMap(), secretStore = InMemorySecretStore(),
                ),
            ) }
            IosReadingReady.initialise = { order += "initialise" }
            IosReadingReady.prepareData = { order += "prepareData" }
            IosReadingReady.createRepository = {
                order += "createRepository"
                scopes += it
                if (failFirst) { failFirst = false; error("boom") }
            }
            val table = IosAppStartup.stringTable
            assertFails { awaitOnMain { IosReadingReady.ensure(table) } }
            awaitOnMain { IosReadingReady.ensure(table) }
            awaitOnMain { IosReadingReady.ensure(table) } // once only
            assertEquals(
                listOf("initialise", "prepareData", "createRepository", "initialise", "prepareData", "createRepository"),
                order,
            )
            assertNotSame(scopes[0], scopes[1], "a retry builds its repository on a fresh scope")
            assertSame(scopes[1], IosReadingReady.scope)
        } finally {
            IosReadingReady.resetForTest()
            awaitStartupOnMain { IosAppStartup.resetForTest() }
        }
    }
}
