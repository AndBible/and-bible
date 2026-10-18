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

import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import net.bible.android.control.page.window.WindowControl
import net.bible.service.startup.ReadingAppInit
import net.bible.sharedcore.bibleview.BibleViewEvents
import net.bible.sharedlogic.platform.NightModeState
import net.bible.sharedui.assembly.IosAppConfig
import net.bible.sharedui.assembly.IosAppStartup
import net.bible.sharedui.assembly.IosAssemblyTest
import net.bible.sharedui.assembly.InMemorySecretStore
import net.bible.sharedui.ios.app.IosHostShell
import net.bible.sharedui.ios.app.IosNavigator
import net.bible.sharedui.ios.app.IosReadingAppInitPlatform
import net.bible.sharedui.webview.IosReadingWebViews
import net.bible.sharedui.webview.bibleBundleUrl
import net.bible.sharedui.webview.attachReadingPane
import org.koin.mp.KoinPlatform
import platform.Foundation.NSURL
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Per-window session assembly: caching, release (relay stop, dispose, handler removal) and the night parameter. */
class IosReadingWindowsTest {
    private object NoNavigator : IosNavigator {
        override fun navigate(route: String) = Unit
        override fun popBackStack(): Boolean = false
        override fun popBackStackTo(route: String, inclusive: Boolean): Boolean = false
    }

    private class FakeNight(var dark: Boolean) : NightModeState {
        override val nightMode: Boolean get() = dark
    }

    private fun withWindows(night: FakeNight = FakeNight(false), body: (windowId: String) -> Unit) {
        val scope = MainScope()
        val loads = mutableListOf<Pair<String, Boolean>>()
        try {
            IosAppStartup.start(
                IosAppConfig(
                    root = IosAssemblyTest.sharedRoot, uiLanguage = "en",
                    repositories = org.crosswire.common.util.PropertyMap(), secretStore = InMemorySecretStore(),
                ),
            )
            runBlocking { IosAppStartup.openDatabases() }
            val platform = IosReadingAppInitPlatform(IosAppStartup.stringTable)
            runBlocking {
                ReadingAppInit.prepareData(platform)
                ReadingAppInit.initialise(platform)
            }
            val koin = KoinPlatform.getKoin()
            ReadingAppInit.createWindowRepository(scope)
            val host = IosReadingHost(scope, IosHostShell(scope, NoNavigator), koin, night)
            IosReadingWindows.bind(host, scope, koin, night)
            IosReadingWebViews.bundleLoader = { _, lang, dark -> loads += lang to dark }
            body(koin.get<WindowControl>().activeWindow.id.toString())
        } finally {
            IosReadingWindows.resetForTest()
            IosReadingWebViews.resetForTest()
            scope.cancel()
            ReadingAppInit.resetForTest()
            runBlocking { IosAppStartup.resetForTest() }
        }
    }

    @Test
    fun sessionForIsCachedPerWindow() = withWindows { id ->
        val a = IosReadingWindows.sessionFor(id)
        assertSame(a, IosReadingWindows.sessionFor(id))
        IosReadingWindows.release(id)
        assertNotSame(a, IosReadingWindows.sessionFor(id))
    }

    @Test
    fun releaseDisposesAndStopsTheRelay() = withWindows { id ->
        val session = IosReadingWindows.sessionFor(id)
        assertTrue(IosReadingWindows.relayStarted(id))
        IosReadingWindows.release(id)
        assertTrue(session.isDisposed)
        assertFalse(IosReadingWindows.relayStarted(id))
        assertFalse(session.emit(BibleViewEvents.response(1L, null)))
    }

    @Test
    fun reattachAfterReleaseRegistersTheBridgeOnce() = withWindows { id ->
        attachReadingPane(id, IosReadingWindows::sessionFor, IosReadingWindows::bridgeFor) { false }
        attachReadingPane(id, IosReadingWindows::sessionFor, IosReadingWindows::bridgeFor) { false }
        assertEquals(1, IosReadingWebViews.bridgeHandlerCount(id))
        IosReadingWindows.release(id)
        assertEquals(0, IosReadingWebViews.bridgeHandlerCount(id))
        attachReadingPane(id, IosReadingWindows::sessionFor, IosReadingWindows::bridgeFor) { false }
        assertEquals(1, IosReadingWebViews.bridgeHandlerCount(id))
    }

    @Test
    fun nightParameterFollowsNightModeState() {
        val night = FakeNight(true)
        val seen = mutableListOf<Boolean>()
        withWindows(night) { id ->
            IosReadingWebViews.bundleLoader = { _, _, dark -> seen += dark }
            attachReadingPane(id, IosReadingWindows::sessionFor, IosReadingWindows::bridgeFor) { IosReadingWindows.nightMode }
            IosReadingWindows.release(id)
            night.dark = false
            attachReadingPane(id, IosReadingWindows::sessionFor, IosReadingWindows::bridgeFor) { IosReadingWindows.nightMode }
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
}
