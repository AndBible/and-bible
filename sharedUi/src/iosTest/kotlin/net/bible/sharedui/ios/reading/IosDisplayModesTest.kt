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

import net.bible.service.common.DisplayColorMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import net.bible.android.control.page.window.WindowControl
import net.bible.service.startup.ReadingAppInit
import net.bible.sharedcore.settings.store.SettingWrite
import net.bible.sharedcore.settings.store.SettingsBackend
import net.bible.sharedcore.settings.store.SettingsSnapshot
import net.bible.sharedcore.settings.store.SettingsStore
import net.bible.sharedlogic.platform.NightModeState
import net.bible.sharedlogic.settings.DatabaseAppSettings
import net.bible.sharedui.assembly.InMemorySecretStore
import net.bible.sharedui.assembly.IosAppConfig
import net.bible.sharedui.assembly.IosAppStartup
import net.bible.sharedui.assembly.awaitStartupOnMain
import net.bible.sharedui.assembly.IosAssemblyTest
import net.bible.sharedui.host.app.HostNavShell
import net.bible.sharedui.host.app.HostNavigator
import net.bible.sharedui.host.app.HostReadingReady
import net.bible.sharedui.webview.IosReadingWebViews
import net.bible.sharedui.webview.attachReadingPane
import org.koin.mp.KoinPlatform
import net.bible.sharedlogic.platform.ios.IosNightModeState
import net.bible.sharedcore.platform.IosDeviceSettings
import platform.Foundation.NSDate
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSUUID
import platform.Foundation.NSUserDefaults
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.Foundation.NSRunLoop
import platform.Foundation.dateWithTimeIntervalSinceNow
import platform.Foundation.runUntilDate
import net.bible.android.view.activity.page.screen.READING_BACKGROUND_DAY
import net.bible.android.view.activity.page.screen.READING_BACKGROUND_NIGHT
import net.bible.sharedui.webview.uiColorFromArgb
import net.bible.sharedui.webview.IosReadingPaneEngine
import net.bible.sharedui.host.reading.HostNightModeObserver
import net.bible.sharedui.host.reading.HostReadingHost
import net.bible.sharedui.host.reading.HostReadingWindows

/** Night, colour mode and animation switches reach shown documents as `set_config`, never as a page reload. */
class IosDisplayModesTest {
    private object NoNavigator : HostNavigator {
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

    private fun withWindows(night: NightModeState, body: (windowId: String) -> Unit) = withWindows({ night }, body)

    /** [nightFor] gets the started Koin, so a test can use the production [NightModeState] binding. */
    private fun withWindows(nightFor: (org.koin.core.Koin) -> NightModeState, body: (windowId: String) -> Unit) {
        try {
            awaitStartupOnMain { IosAppStartup.ensureStarted(
                IosAppConfig(
                    root = IosAssemblyTest.sharedRoot, uiLanguage = "en",
                    repositories = org.crosswire.common.util.PropertyMap(), secretStore = InMemorySecretStore(),
                ),
            ) }
            awaitStartupOnMain { IosAppStartup.openDatabases() }
            awaitOnMain { HostReadingReady.ensure(IosAppStartup.stringTable) }
            val scope: CoroutineScope = HostReadingReady.scope
            val koin = KoinPlatform.getKoin()
            val night = nightFor(koin)
            val host = HostReadingHost(scope, HostNavShell(scope, NoNavigator), koin, IosReadingHostPlatform, night)
            HostReadingWindows.bind(host, scope, koin, night, IosReadingPaneEngine)
            body(koin.get<WindowControl>().activeWindow.id.toString())
        } finally {
            HostReadingWindows.resetForTest()
            IosReadingWebViews.resetForTest()
            HostReadingReady.resetForTest()
            ReadingAppInit.resetForTest()
            runBlocking { IosAppStartup.resetForTest() }
        }
    }

    /**
     * The WebView config is updated through the relay: the bundle is loaded once, the WebView stays the same object.
     * Limit: the test WebView has no page, so this asserts the relay request (`configUpdates`) and the absence of a
     * reload, not the evaluated `set_config` JavaScript.
     */
    @Test
    fun nightSwitchUpdatesWebViewConfigWithoutReload() {
        val night = FakeNight(false)
        withWindows(night) { id ->
            var loads = 0
            IosReadingWebViews.bundleLoader = { _, _, _ -> loads++ }
            attachReadingPane(id, HostReadingWindows::sessionFor, HostReadingWindows::bridgeFor, { HostReadingWindows.nightMode }, HostReadingWindows::isOpen)
            val webView = IosReadingWebViews.entry(id).webView
            val relay = HostReadingWindows.relayPlatformFor(id)!!
            assertEquals(1, loads)
            val before = relay.configUpdates

            night.dark = true
            HostReadingWindows.nightModeChanged()

            assertEquals(before + 1, relay.configUpdates, "night switch re-sends set_config through the relay")
            assertEquals(1, loads, "no second loadBibleBundle: no reload flash, no lost scroll position")
            assertSame(webView, IosReadingWebViews.entry(id).webView)
        }
    }

    /**
     * NM-1: BibleView-JS paints no page background, so the WKWebView and its scroll view carry the colour (Android's
     * `BibleView.setBackgroundColor`). It follows the first `set_config` and every night switch, in both directions.
     */
    @Test
    fun nightSwitchPaintsTheWebViewAndScrollViewBackground() {
        val night = FakeNight(false)
        withWindows(night) { id ->
            IosReadingWebViews.bundleLoader = { _, _, _ -> }
            attachReadingPane(id, HostReadingWindows::sessionFor, HostReadingWindows::bridgeFor, { HostReadingWindows.nightMode }, HostReadingWindows::isOpen)
            val webView = IosReadingWebViews.entry(id).webView
            HostReadingWindows.nightModeChanged()
            assertBackground(webView, READING_BACKGROUND_DAY, "day")

            night.dark = true
            HostReadingWindows.nightModeChanged()
            assertBackground(webView, READING_BACKGROUND_NIGHT, "night")

            night.dark = false
            HostReadingWindows.nightModeChanged()
            assertBackground(webView, READING_BACKGROUND_DAY, "back to day")
        }
    }

    private fun assertBackground(webView: platform.WebKit.WKWebView, argb: Int, label: String) {
        val expected = uiColorFromArgb(argb)
        assertTrue(webView.backgroundColor?.isEqual(expected) == true, "$label: WKWebView background ${webView.backgroundColor}")
        assertTrue(webView.scrollView.backgroundColor?.isEqual(expected) == true, "$label: scroll view background ${webView.scrollView.backgroundColor}")
    }

    /** ARGB ints become opaque black / white and keep their alpha (so a colour with no alpha byte is as transparent as on Android). */
    @Test
    fun argbIntsConvertToUiColours() {
        assertTrue(uiColorFromArgb(READING_BACKGROUND_NIGHT).isEqual(platform.UIKit.UIColor.colorWithRed(0.0, 0.0, 0.0, 1.0)))
        assertTrue(uiColorFromArgb(READING_BACKGROUND_DAY).isEqual(platform.UIKit.UIColor.colorWithRed(1.0, 1.0, 1.0, 1.0)))
        assertTrue(uiColorFromArgb(0x00112233).isEqual(platform.UIKit.UIColor.colorWithRed(0x11 / 255.0, 0x22 / 255.0, 0x33 / 255.0, 0.0)))
    }

    private class MemoryBackend : SettingsBackend {
        override suspend fun loadAll() = SettingsSnapshot(emptyMap(), emptyMap(), emptyMap(), emptyMap())
        override suspend fun write(write: SettingWrite) = Unit
    }

    /**
     * Colour mode and animations are database-backed settings: a write through the real [DatabaseAppSettings] over a
     * [SettingsStore] reaches the observer through the store's change flow, with no app-active or UserDefaults event,
     * and updates [HostNightModeObserver.monochrome] (the predicate the reading host and the loader share).
     */
    @Test
    fun databaseSettingWritesReachTheObserverWithoutAnAppActiveEvent() {
        val store = SettingsStore(MemoryBackend(), CoroutineScope(SupervisorJob() + Dispatchers.Default))
        val settings = DatabaseAppSettings({ store }) { true }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        var changes = 0
        val observer = HostNightModeObserver(FakeNight(false), IosDisplayModeSignals, settings, store.changes) { changes++ }
        try {
            observer.start(scope)
            assertFalse(observer.monochrome.value)
            settings.setString("display_color_mode", DisplayColorMode.MONOCHROME.value)
            assertTrue(observer.monochrome.value)
            assertEquals(1, changes)
            settings.setBoolean("disable_animations", true)
            assertEquals(2, changes)
            settings.setBoolean("disable_animations", true) // same value: no report
            settings.setBoolean("unrelated", true)
            assertEquals(2, changes)
            settings.setString("display_color_mode", DisplayColorMode.NORMAL.value)
            assertFalse(observer.monochrome.value)
            assertEquals(3, changes)
        } finally {
            observer.stop()
            scope.cancel()
        }
    }

    private fun spinMain() = NSRunLoop.mainRunLoop.runUntilDate(NSDate.dateWithTimeIntervalSinceNow(0.1))

    private fun post(name: String?) = NSNotificationCenter.defaultCenter.postNotificationName(name!!, null)

    /**
     * Night mode "system" (an absent preference) while the app is backgrounded and the appearance flips: nothing is
     * delivered in the background; on become-active the observer re-reads, the chrome state follows, and the page gets
     * the new config through the relay (set_config) with the same WebView and no second bundle load (no reload flash,
     * the scroll position survives).
     */
    @Test
    fun systemAppearanceChangeRefreshesOnActive() {
        val suiteName = "display-modes-" + NSUUID().UUIDString
        val defaults = NSUserDefaults(suiteName = suiteName)
        var systemDark = false
        val state = IosNightModeState(IosDeviceSettings(defaults)) { systemDark }
        withWindows(state) { id ->
            var loads = 0
            IosReadingWebViews.bundleLoader = { _, _, _ -> loads++ }
            attachReadingPane(id, HostReadingWindows::sessionFor, HostReadingWindows::bridgeFor, { HostReadingWindows.nightMode }, HostReadingWindows::isOpen)
            val webView = IosReadingWebViews.entry(id).webView
            val relay = HostReadingWindows.relayPlatformFor(id)!!
            val configsBefore = relay.configUpdates
            var changes = 0
            val observer = HostNightModeObserver(state, IosDisplayModeSignals, restored = { null }) {
                changes++
                HostReadingWindows.nightModeChanged()
            }
            try {
                observer.start()
                observer.refresh()
                assertFalse(observer.dark.value)
                assertEquals(0, changes)

                systemDark = true // flips while backgrounded: no notification is delivered
                spinMain()
                assertFalse(observer.dark.value)
                assertEquals(0, changes)

                post(UIApplicationDidBecomeActiveNotification)
                spinMain()
                assertTrue(observer.dark.value, "chrome follows the system on return")
                assertEquals(1, changes)
                assertEquals(configsBefore + 1, relay.configUpdates, "the open page gets set_config")
                assertEquals(1, loads, "no reload flash, no lost scroll position")
                assertSame(webView, IosReadingWebViews.entry(id).webView)

                post(UIApplicationDidBecomeActiveNotification) // active again, nothing changed: no report
                spinMain()
                assertEquals(1, changes)
                assertEquals(configsBefore + 1, relay.configUpdates)
            } finally {
                observer.stop()
                NSUserDefaults.standardUserDefaults.removePersistentDomainForName(suiteName)
            }
        }
    }

    /**
     * The settings screen writes `night_mode_pref3` through [net.bible.android.view.activity.settings.AppSettingsServiceImpl]
     * to the device-local prefs; the production [NightModeState] binding (the one the observer and the loader share)
     * sees that write after the user-defaults notification. The key is removed afterwards (the standard defaults are shared).
     */
    @Test
    fun settingsScreenWriteReachesTheObserver() {
        val standard = NSUserDefaults.standardUserDefaults
        val hadMode = standard.objectForKey("night_mode_pref3")
        val hadManual = standard.objectForKey("night_mode_pref")
        standard.removeObjectForKey("night_mode_pref3")
        standard.setBool(true, forKey = "night_mode_pref")
        withWindows({ it.get<NightModeState>() }) {
            val koin = KoinPlatform.getKoin()
            val observer = HostNightModeObserver(koin.get<NightModeState>(), IosDisplayModeSignals, restored = { null })
            try {
                observer.start()
                observer.refresh()
                koin.get<net.bible.sharedcore.settings.AppSettingsService>().setString("night_mode_pref3", "manual")
                spinMain()
                assertEquals("manual", standard.stringForKey("night_mode_pref3"))
                assertTrue(observer.dark.value, "manual with night_mode_pref=true is dark")
                koin.get<net.bible.sharedcore.settings.AppSettingsService>().setBool("night_mode_pref", false)
                spinMain()
                assertFalse(observer.dark.value)
            } finally {
                observer.stop()
                if (hadMode == null) standard.removeObjectForKey("night_mode_pref3") else standard.setObject(hadMode, forKey = "night_mode_pref3")
                if (hadManual == null) standard.removeObjectForKey("night_mode_pref") else standard.setObject(hadManual, forKey = "night_mode_pref")
            }
        }
    }

    /** A database restore or sign-in announces itself on `DatabaseContainer.databaseRestored`; the observer re-reads then. */
    @Test
    fun restoreRereadsNightMode() {
        val night = FakeNight(false)
        val restored = net.bible.sharedcore.event.EventSource<Unit>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        var changes = 0
        val observer = HostNightModeObserver(night, IosDisplayModeSignals, restored = { restored }) { changes++ }
        try {
            observer.start(scope)
            night.dark = true // the restore replaced the stored mode: no notification of its own
            assertFalse(observer.dark.value)
            restored.emit(Unit)
            assertTrue(observer.dark.value)
            assertEquals(1, changes)
            observer.stop()
            night.dark = false
            restored.emit(Unit)
            assertTrue(observer.dark.value, "a stopped observer no longer listens")
        } finally {
            observer.stop()
            scope.cancel()
        }
    }
}
