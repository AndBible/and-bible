/*
 * Copyright (c) 2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
 *
 * This file is part of AndBible: Bible Study (http://github.com/AndBible/and-bible).
 * AndBible is free software under the GNU General Public License, version 3 or later.
 */
package net.bible.sharedui.host.reading

import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import net.bible.android.control.page.KeyChooserLauncher
import net.bible.android.control.page.window.WindowControl
import net.bible.service.db.DatabaseContainer
import net.bible.service.db.DatabaseHost
import net.bible.service.startup.ReadingAppInit
import net.bible.sharedcore.nav.NavRoutes
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
import org.koin.mp.KoinPlatform
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

/**
 * #4169: every Bible/commentary key-chooser entry point opens the compact passage quick sheet, the same one a title tap
 * opens, never a full-screen book grid. Runs the real desktop reading host: the page's Ctrl+B (desktop Ctrl+L, iOS
 * Cmd+B), the `KeyChooserLauncher` port `CurrentPageManager` uses after a document switch, and a chooser asked for by a
 * window that is not the active one (the sheet answers the active window, so that window is activated first).
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class KeyChooserEntryPointsTest {
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
        root = Files.createTempDirectory("key-chooser-entry-points")
    }

    @AfterTest fun tearDown() {
        try {
            HostRootLinks.reset()
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

    private fun withHost(body: suspend (host: HostReadingHost, control: WindowControl) -> Unit) = runBlocking {
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
        withContext(Dispatchers.Main) {
            val host = HostReadingHost(scope, HostNavShell(scope, navigator), koin, DesktopReadingHostPlatform(WindowHandle), Day)
            try {
                val book = checkNotNull(org.crosswire.jsword.book.Books.installed().getBook("K4RawText"))
                control.activeWindow.pageManager.setCurrentDocumentAndKey(book, book.getKey("John 3:16"))
                routes.clear()
                body(host, control)
            } finally {
                host.hostState.closeQuickSheet()
                host.dispose()
            }
        }
    }

    /** Waits until the chooser opened: a quick sheet, or a navigated route (the full-screen chooser). */
    private suspend fun awaitSheet(host: HostReadingHost): ReadingQuickSheet? = withTimeout(10_000) {
        while (host.hostState.quickSheet.value == null && routes.isEmpty()) delay(10)
        host.hostState.quickSheet.value
    }

    private fun assertGridSheet(host: HostReadingHost, sheet: ReadingQuickSheet?) {
        assertEquals(emptyList(), routes.toList(), "no full-screen chooser is navigated to")
        assertEquals(KeyChooserKind.Grid, (sheet as? ReadingQuickSheet.KeyChooser)?.kind, "the passage quick sheet: $sheet")
    }

    @Test fun pageCtrlKeyBOpensThePassageSheet() = withHost { host, control ->
        val engine = DesktopReadingPaneEngine(FakeWebEngine(), {}, koin = { KoinPlatform.getKoin() })
        HostReadingWindows.bind(host, HostReadingReady.scope, KoinPlatform.getKoin(), Day, engine)
        val id = control.activeWindow.id.toString()
        val session = HostReadingWindows.sessionFor(id)
        engine.attach(id, false, HostReadingWindows.bridgeFor(id), {}, { session.jsChannel.flush() })
        session.jsChannel.markClientReady()
        HostReadingWindows.bridgeFor(id).onKeyDown("CtrlKeyB")
        assertGridSheet(host, awaitSheet(host))
    }

    @Test fun keyChooserLauncherOpensThePassageSheet() = withHost { host, control ->
        HostRootLinks.attachReading(host.navigator, { _, _ -> false })
        val started = KoinPlatform.getKoin().get<KeyChooserLauncher>().startKeyChooser(control.activeWindow.pageManager.currentPage)
        assertEquals(true, started)
        assertGridSheet(host, awaitSheet(host))
    }

    @Test fun aChooserForAnInactiveWindowActivatesItAndOpensThePassageSheet() = withHost { host, control ->
        val asking = control.activeWindow
        val other = control.addNewWindow(asking)
        control.activeWindow = other
        assertSame(other, control.activeWindow)
        host.navigator.startKeyChooser(asking.pageManager.currentPage)
        assertGridSheet(host, awaitSheet(host))
        assertSame(asking, control.activeWindow, "the sheet answers the active window, so the asking window is activated")
    }

    @Test fun dictionaryKeepsItsFullScreenChooser() = withHost { host, control ->
        host.navigator.startKeyChooser(control.activeWindow.pageManager.currentDictionary)
        withTimeout(10_000) { while (routes.isEmpty()) delay(10) }
        assertEquals(listOf(NavRoutes.CHOOSE_DICTIONARY_WORD), routes.toList())
        assertNull(host.hostState.quickSheet.value)
    }
}
