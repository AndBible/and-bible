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

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import net.bible.android.control.page.window.WindowChange
import net.bible.android.control.page.window.WindowControl
import net.bible.android.control.page.window.WindowLayout
import net.bible.android.control.page.window.WindowStateServiceImpl
import net.bible.android.database.IdType
import net.bible.service.startup.ReadingAppInit
import net.bible.sharedcore.bibleview.BibleViewEvents
import net.bible.sharedlogic.platform.NightModeState
import net.bible.sharedui.assembly.IosAppConfig
import net.bible.sharedui.assembly.IosAppStartup
import net.bible.sharedui.assembly.IosAssemblyTest
import net.bible.sharedui.assembly.InMemorySecretStore
import net.bible.sharedui.ios.app.IosHostShell
import net.bible.sharedui.ios.app.IosNavigator
import net.bible.sharedui.ios.app.IosReadingReady
import net.bible.sharedui.webview.IosReadingWebViews
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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

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

    private fun withWindows(night: FakeNight = FakeNight(false), body: (windowId: String) -> Unit) {
        try {
            IosAppStartup.start(
                IosAppConfig(
                    root = IosAssemblyTest.sharedRoot, uiLanguage = "en",
                    repositories = org.crosswire.common.util.PropertyMap(), secretStore = InMemorySecretStore(),
                ),
            )
            runBlocking { IosAppStartup.openDatabases() }
            // The production step (initialise, prepareData, window repository), not a hand-ordered copy of it.
            awaitOnMain { IosReadingReady.ensure(IosAppStartup.stringTable) }
            val scope: CoroutineScope = IosReadingReady.scope
            val koin = KoinPlatform.getKoin()
            val host = IosReadingHost(scope, IosHostShell(scope, NoNavigator), koin, night)
            IosReadingWindows.bind(host, scope, koin, night)
            IosReadingWebViews.bundleLoader = { _, _, _ -> }
            body(koin.get<WindowControl>().activeWindow.id.toString())
        } finally {
            IosReadingWindows.resetForTest()
            IosReadingWebViews.resetForTest()
            IosReadingReady.resetForTest()
            ReadingAppInit.resetForTest()
            runBlocking { IosAppStartup.resetForTest() }
        }
    }

    private fun attach(id: String, night: () -> Boolean = { false }) = attachReadingPane(
        id, IosReadingWindows::sessionFor, IosReadingWindows::bridgeFor, night, IosReadingWindows::isOpen,
    )

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
        val changes = mutableListOf<Boolean>()
        val observer = IosNightModeObserver(night) { changes += it }
        assertFalse(observer.dark.value)
        night.dark = true
        assertTrue(observer.refresh())
        assertTrue(observer.dark.value)
        observer.refresh() // unchanged: no second report
        night.dark = false
        observer.refresh()
        assertEquals(listOf(true, false), changes)
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
            IosAppStartup.start(
                IosAppConfig(
                    root = IosAssemblyTest.sharedRoot, uiLanguage = "en",
                    repositories = org.crosswire.common.util.PropertyMap(), secretStore = InMemorySecretStore(),
                ),
            )
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
            runBlocking { IosAppStartup.resetForTest() }
        }
    }
}
