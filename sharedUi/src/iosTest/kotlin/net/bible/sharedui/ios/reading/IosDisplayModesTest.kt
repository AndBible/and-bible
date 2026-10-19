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
import kotlin.test.assertSame
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import net.bible.android.control.page.window.WindowControl
import net.bible.service.startup.ReadingAppInit
import net.bible.sharedlogic.platform.NightModeState
import net.bible.sharedui.assembly.InMemorySecretStore
import net.bible.sharedui.assembly.IosAppConfig
import net.bible.sharedui.assembly.IosAppStartup
import net.bible.sharedui.assembly.IosAssemblyTest
import net.bible.sharedui.ios.app.IosHostShell
import net.bible.sharedui.ios.app.IosNavigator
import net.bible.sharedui.ios.app.IosReadingReady
import net.bible.sharedui.webview.IosReadingWebViews
import net.bible.sharedui.webview.attachReadingPane
import org.koin.mp.KoinPlatform
import platform.Foundation.NSDate
import platform.Foundation.NSRunLoop
import platform.Foundation.dateWithTimeIntervalSinceNow
import platform.Foundation.runUntilDate

/** Night, colour mode and animation switches reach shown documents as `set_config`, never as a page reload. */
class IosDisplayModesTest {
    private object NoNavigator : IosNavigator {
        override fun navigate(route: String) = Unit
        override fun popBackStack(): Boolean = false
        override fun popBackStackTo(route: String, inclusive: Boolean): Boolean = false
    }

    private class FakeNight(var dark: Boolean) : NightModeState {
        override val nightMode: Boolean get() = dark
    }

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

    private fun withWindows(night: FakeNight, body: (windowId: String) -> Unit) {
        try {
            IosAppStartup.start(
                IosAppConfig(
                    root = IosAssemblyTest.sharedRoot, uiLanguage = "en",
                    repositories = org.crosswire.common.util.PropertyMap(), secretStore = InMemorySecretStore(),
                ),
            )
            runBlocking { IosAppStartup.openDatabases() }
            awaitOnMain { IosReadingReady.ensure(IosAppStartup.stringTable) }
            val scope: CoroutineScope = IosReadingReady.scope
            val koin = KoinPlatform.getKoin()
            val host = IosReadingHost(scope, IosHostShell(scope, NoNavigator), koin, night)
            IosReadingWindows.bind(host, scope, koin, night)
            body(koin.get<WindowControl>().activeWindow.id.toString())
        } finally {
            IosReadingWindows.resetForTest()
            IosReadingWebViews.resetForTest()
            IosReadingReady.resetForTest()
            ReadingAppInit.resetForTest()
            runBlocking { IosAppStartup.resetForTest() }
        }
    }

    /** The WebView config is updated through the relay: the bundle is loaded once, the WebView stays the same object. */
    @Test
    fun nightSwitchUpdatesWebViewConfigWithoutReload() {
        val night = FakeNight(false)
        withWindows(night) { id ->
            var loads = 0
            IosReadingWebViews.bundleLoader = { _, _, _ -> loads++ }
            attachReadingPane(id, IosReadingWindows::sessionFor, IosReadingWindows::bridgeFor, { IosReadingWindows.nightMode }, IosReadingWindows::isOpen)
            val webView = IosReadingWebViews.entry(id).webView
            val relay = IosReadingWindows.relayPlatformFor(id)!!
            assertEquals(1, loads)
            val before = relay.configUpdates

            night.dark = true
            IosReadingWindows.nightModeChanged()

            assertEquals(before + 1, relay.configUpdates, "night switch re-sends set_config through the relay")
            assertEquals(1, loads, "no second loadBibleBundle: no reload flash, no lost scroll position")
            assertSame(webView, IosReadingWebViews.entry(id).webView)
        }
    }

    /** Colour mode and the animation setting count as display modes: a change fires the observer, no change does not. */
    @Test
    fun observerReportsMonochromeAndAnimationChanges() {
        val night = FakeNight(false)
        var color: String? = "NORMAL"
        var noAnimations = false
        val changes = mutableListOf<Boolean>()
        val observer = IosNightModeObserver(night, otherModes = { listOf(color, noAnimations) }) { changes += it }
        observer.refresh()
        assertEquals(emptyList(), changes)
        color = "MONOCHROME"
        observer.refresh()
        noAnimations = true
        observer.refresh()
        observer.refresh()
        assertEquals(listOf(false, false), changes)
    }
}
