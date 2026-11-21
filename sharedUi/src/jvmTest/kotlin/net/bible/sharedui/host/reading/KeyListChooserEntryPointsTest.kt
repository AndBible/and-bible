/*
 * Copyright (c) 2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
 *
 * This file is part of AndBible: Bible Study (http://github.com/AndBible/and-bible).
 * AndBible is free software under the GNU General Public License, version 3 or later.
 */
package net.bible.sharedui.host.reading

import androidx.compose.runtime.snapshots.Snapshot
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import net.bible.android.control.page.CurrentPage
import net.bible.android.control.page.KeyChooserLauncher
import net.bible.android.control.page.window.WindowControl
import net.bible.service.db.DatabaseContainer
import net.bible.service.db.DatabaseHost
import net.bible.service.startup.ReadingAppInit
import net.bible.sharedcore.platform.desktop.DesktopPaths
import net.bible.sharedcore.reading.KeyChooserKind
import net.bible.sharedcore.reading.ReadingQuickSheet
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
import net.bible.sharedui.host.app.HostRootLinks
import org.crosswire.common.util.PropertyMap
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.Books
import org.koin.mp.KoinPlatform
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

/**
 * #4172: every general-book and map key-chooser entry point opens the reading view's key-list quick sheet, the one a
 * title tap opens, never a full-screen chooser. Runs the real desktop reading host: the page's Ctrl+B (desktop Ctrl+L,
 * iOS Cmd+B), `CurrentPageManager`'s auto-open after switching to a general book or map, the `KeyChooserLauncher`
 * port, and a chooser asked for by a window that is not the active one (the sheet answers the active window).
 *
 * The general book is the K4 corpus's `K4GenBook`; the map is the same data under a `Category=Maps` conf.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class KeyListChooserEntryPointsTest {
    private object WindowHandle : DesktopWindowHandle {
        override val sizeDp = androidx.compose.ui.unit.IntSize(1200, 800)
        override val density = 1f
        override fun toFront() = Unit
    }

    private object Day : NightModeState {
        override val nightMode = false
    }

    private val routes = java.util.Collections.synchronizedList(mutableListOf<String>())
    private val navigator = object : HostNavigator {
        override fun navigate(route: String) { routes += route }
        override fun popBackStack() = false
        override fun popBackStackTo(route: String, inclusive: Boolean) = false
    }

    private lateinit var root: Path
    private var previousHost: DatabaseHost? = null

    @BeforeTest fun setUp() {
        assertNull(KoinPlatform.getKoinOrNull(), "a Koin context leaked into this test")
        Dispatchers.setMain(SwingMainDispatcher)
        previousHost = runCatching { DatabaseContainer.host }.getOrNull()
        root = Files.createTempDirectory("key-list-chooser-entry-points")
    }

    @AfterTest fun tearDown() {
        try {
            HostRootLinks.reset()
            runBlocking { withContext(Dispatchers.Main) { HostReadingWindows.resetForTest() } }
            HostReadingReady.resetForTest()
            ReadingAppInit.resetForTest()
            DesktopAppStartup.resetForTest()
        } finally {
            listOf(GEN_BOOK, MAP).forEach { id -> Books.installed().getBook(id)?.let { Books.installed().removeBook(it) } }
            previousHost?.let { DatabaseContainer.installHost(it) }
            root.toFile().deleteRecursively()
            Dispatchers.resetMain()
        }
    }

    private fun installFixtures(sword: Path) {
        StartupFixture.files.forEach { (name, bytes) ->
            val target = sword.resolve(name)
            Files.createDirectories(target.parent)
            Files.write(target, bytes)
        }
        val corpus = File("../jsword/src/commonTest/resources/k4-modules/space dir/ä")
        corpus.resolve("modules/$GEN_BOOK").copyRecursively(sword.resolve("modules/$GEN_BOOK").toFile())
        val conf = corpus.resolve("mods.d/$GEN_BOOK.conf").readText()
        Files.writeString(sword.resolve("mods.d/$GEN_BOOK.conf"), conf)
        Files.writeString(sword.resolve("mods.d/$MAP.conf"), conf.replace("[$GEN_BOOK]", "[$MAP]") + "Category=Maps\n")
    }

    private fun withHost(body: suspend (host: HostReadingHost, control: WindowControl) -> Unit) = runBlocking {
        val paths = DesktopPaths(
            { name -> when (name) {
                "XDG_DATA_HOME" -> root.resolve("data").toString()
                "XDG_CONFIG_HOME" -> root.resolve("config").toString()
                "XDG_CACHE_HOME" -> root.resolve("cache").toString()
                else -> null
            } }, "Linux", root.resolve("home"),
        )
        installFixtures(paths.sword)
        assertEquals(StartupPhase.Ready, DesktopAppStartup.ensureStarted(paths, WindowHandle, "en", repositories = { PropertyMap() }, notifierSource = net.bible.sharedui.desktop.assembly.DesktopNotifierSource(net.bible.sharedcore.platform.desktop.dbus.DesktopDbus().supportedOs) { net.bible.sharedcore.platform.desktop.dbus.NoOpNativeNotifier }))
        HostReadingReady.ensure(HostStartup.stringTable)
        val koin = KoinPlatform.getKoin()
        val scope = HostReadingReady.scope
        val control = koin.get<WindowControl>()
        withContext(Dispatchers.Main) {
            val host = HostReadingHost(scope, HostNavShell(scope, navigator), koin, DesktopReadingHostPlatform(WindowHandle), Day)
            try {
                val bible = checkNotNull(Books.installed().getBook("K4RawText"))
                control.activeWindow.pageManager.setCurrentDocumentAndKey(bible, bible.getKey("John 3:16"))
                routes.clear()
                body(host, control)
            } finally {
                host.hostState.closeQuickSheet()
                host.dispose()
            }
        }
    }

    private fun book(initials: String): Book = checkNotNull(Books.installed().getBook(initials)) { "$initials is not installed" }

    /** Waits until a chooser opened: a quick sheet, or a navigated route (a full-screen chooser). */
    private suspend fun awaitChooser(host: HostReadingHost): ReadingQuickSheet? = withTimeout(10_000) {
        while (host.hostState.quickSheet.value == null && routes.isEmpty()) delay(10)
        host.hostState.quickSheet.value
    }

    private fun assertKeyListSheet(host: HostReadingHost, kind: KeyChooserKind, sheet: ReadingQuickSheet?) {
        assertEquals(emptyList(), routes.toList(), "no full-screen chooser is navigated to")
        assertEquals(kind, (sheet as? ReadingQuickSheet.KeyChooser)?.kind, "the key-list quick sheet: $sheet")
        assertEquals(listOf("Part 1", "Chapter 1", "Chapter 2", "Part 2"), host.hostState.keyChooserKeys.map { it.getName() })
    }

    /** Opens [initials] in the active window (at its first key), as the page a chooser is then asked for. */
    private fun pageOf(control: WindowControl, initials: String): CurrentPage {
        val manager = control.activeWindow.pageManager
        manager.setCurrentDocumentAndKey(book(initials), book(initials).getKey("Part 1"))
        routes.clear()
        return manager.currentPage
    }

    private fun ctrlKeyB(host: HostReadingHost, control: WindowControl) {
        val engine = DesktopReadingPaneEngine(FakeWebEngine(), {}, koin = { KoinPlatform.getKoin() })
        HostReadingWindows.bind(host, HostReadingReady.scope, KoinPlatform.getKoin(), Day, engine)
        val id = control.activeWindow.id.toString()
        val session = HostReadingWindows.sessionFor(id)
        engine.attach(id, false, HostReadingWindows.bridgeFor(id), {}, { session.jsChannel.flush() })
        session.jsChannel.markClientReady()
        HostReadingWindows.bridgeFor(id).onKeyDown("CtrlKeyB")
    }

    @Test fun generalBookCtrlKeyBOpensTheKeyListSheet() = withHost { host, control ->
        pageOf(control, GEN_BOOK)
        ctrlKeyB(host, control)
        assertKeyListSheet(host, KeyChooserKind.GeneralBook, awaitChooser(host))
    }

    @Test fun mapCtrlKeyBOpensTheKeyListSheet() = withHost { host, control ->
        pageOf(control, MAP)
        ctrlKeyB(host, control)
        assertKeyListSheet(host, KeyChooserKind.Map, awaitChooser(host))
    }

    /** `CurrentPageManager`'s auto-open: switching to a general book with no key asks for a key. */
    @Test fun switchingToAGeneralBookOpensTheKeyListSheet() = withHost { host, control ->
        HostRootLinks.attachReading(host.navigator, { _, _ -> false })
        control.activeWindow.pageManager.setCurrentDocument(book(GEN_BOOK))
        assertKeyListSheet(host, KeyChooserKind.GeneralBook, awaitChooser(host))
    }

    @Test fun switchingToAMapOpensTheKeyListSheet() = withHost { host, control ->
        HostRootLinks.attachReading(host.navigator, { _, _ -> false })
        control.activeWindow.pageManager.setCurrentDocument(book(MAP))
        assertKeyListSheet(host, KeyChooserKind.Map, awaitChooser(host))
    }

    /**
     * The full-screen choosers' cancel guard, kept: an auto-opened sheet left without a choice, on a book that still has
     * no key, goes back in history instead of leaving an empty page.
     */
    @Test fun dismissingTheAutoOpenedSheetWithoutAKeyGoesBack() = withHost { host, control ->
        HostRootLinks.attachReading(host.navigator, { _, _ -> false })
        val manager = control.activeWindow.pageManager
        manager.setCurrentDocument(book(GEN_BOOK))
        assertKeyListSheet(host, KeyChooserKind.GeneralBook, awaitChooser(host))
        assertNull(manager.currentPage.key, "precondition: the switched-to book has no key yet")
        host.hostState.closeQuickSheet()
        Snapshot.sendApplyNotifications()
        withTimeout(10_000) { while (manager.currentPage !== manager.currentBible) delay(10) }
        assertEquals("K4RawText", manager.currentPage.currentDocument?.initials)
    }

    /** A key picked in the sheet (applied in the same click that closes it) is not undone by the cancel guard. */
    @Test fun aChosenKeyIsKept() = withHost { host, control ->
        HostRootLinks.attachReading(host.navigator, { _, _ -> false })
        val manager = control.activeWindow.pageManager
        manager.setCurrentDocument(book(GEN_BOOK))
        assertKeyListSheet(host, KeyChooserKind.GeneralBook, awaitChooser(host))
        val chosen = host.hostState.keyChooserKeys[2]
        host.hostState.closeQuickSheet()
        manager.setCurrentDocumentAndKey(book(GEN_BOOK), chosen)
        Snapshot.sendApplyNotifications()
        delay(500)
        assertSame(manager.currentGeneralBook, manager.currentPage)
        assertEquals("Chapter 2", manager.currentPage.key?.getName())
    }

    @Test fun keyChooserLauncherOpensTheKeyListSheet() = withHost { host, control ->
        HostRootLinks.attachReading(host.navigator, { _, _ -> false })
        val started = KoinPlatform.getKoin().get<KeyChooserLauncher>().startKeyChooser(pageOf(control, GEN_BOOK))
        assertEquals(true, started)
        assertKeyListSheet(host, KeyChooserKind.GeneralBook, awaitChooser(host))
    }

    /** The sheet reads and answers the active window, so the asking window is activated first. */
    @Test fun aChooserForAnInactiveWindowActivatesItAndOpensTheKeyListSheet() = withHost { host, control ->
        val asking = control.activeWindow
        val page = pageOf(control, MAP)
        val other = control.addNewWindow(asking)
        control.activeWindow = other
        assertSame(other, control.activeWindow)
        host.navigator.startKeyChooser(page)
        assertKeyListSheet(host, KeyChooserKind.Map, awaitChooser(host))
        assertSame(asking, control.activeWindow, "the sheet answers the active window, so the asking window is activated")
    }

    private companion object {
        const val GEN_BOOK = "K4GenBook"
        const val MAP = "K4Map"
    }
}
