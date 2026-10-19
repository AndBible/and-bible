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

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import net.bible.android.control.page.window.WindowControl
import net.bible.service.startup.ReadingAppInit
import net.bible.sharedlogic.platform.NightModeState
import net.bible.sharedui.assembly.IosAppConfig
import net.bible.sharedui.assembly.IosAppStartup
import net.bible.sharedui.assembly.IosAssemblyTest
import net.bible.sharedui.assembly.InMemorySecretStore
import net.bible.sharedui.ios.IosPending
import net.bible.sharedui.ios.app.IosHostShell
import net.bible.sharedui.ios.app.IosNavigator
import net.bible.sharedui.ios.app.IosReadingAppInitPlatform
import net.bible.sharedui.reading.ReadingToolbarIcons
import org.koin.mp.KoinPlatform
import platform.UIKit.UIApplicationOpenSettingsURLString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The iOS reading host deps build once the window repository exists, and follow the active window and night mode. */
class IosReadingHostTest {
    private object NoNavigator : IosNavigator {
        override fun navigate(route: String) = Unit
        override fun popBackStack(): Boolean = false
        override fun popBackStackTo(route: String, inclusive: Boolean): Boolean = false
    }

    private class FakeNight(override val nightMode: Boolean) : NightModeState

    @Test
    fun depsBuildAfterWindowRepository() {
        val scope = MainScope()
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
                ReadingAppInit.initialise(platform) // production order: initialise, then prepareData
                ReadingAppInit.prepareData(platform)
            }
            val koin = KoinPlatform.getKoin()
            ReadingAppInit.createWindowRepository(scope)
            val shell = IosHostShell(scope, NoNavigator)
            val p = ColorPainter(Color.Black)
            val icons = ReadingToolbarIcons(p, p, p, p, p, p, p, p, p)

            val host = IosReadingHost(scope, shell, koin)
            val deps = host.deps(icons)
            val activeId = koin.get<WindowControl>().activeWindow.id.toString()
            assertEquals(activeId, deps.controller.layout.value.activeWindowId)
            // hostState reads the active window through its WindowControl and HistoryManager.
            host.hostState.historyEntriesForActiveWindow()
            assertTrue(deps.state === host.hostState)
            assertEquals("Bible Study", host.navDeps {}.windowTitle)

            // Every toolbar callback is safe to invoke; the pending ones log under their member name.
            IosPending.resetForTest()
            deps.toolbarCallbacks.apply {
                onHome(); onTitleTap(); onTitleLongPress(); onTitleFlingVertical(); onTitleFlingHorizontal(true)
                onBible(); onBibleLong(); onCommentary(); onCommentaryLong(); onStrongs(); onStrongsLong()
                onSearch(); onSpeak(); onSpeakLong(); onWorkspace(); onOverflow()
            }
            for (name in listOf(
                "onHome", "onTitleLongPress", "onTitleFlingHorizontal", "onBible", "onBibleLong", "onCommentary",
                "onCommentaryLong", "onStrongs", "onStrongsLong", "onSpeak", "onSpeakLong", "onOverflow",
            )) {
                assertTrue("ReadingToolbar.$name" in IosPending.reported, "missing pending log for $name")
            }

            // Night mode follows NightModeState, both ways.
            assertTrue(IosReadingHost(scope, shell, koin, FakeNight(true)).deps(icons).nightMode.value)
            assertFalse(IosReadingHost(scope, shell, koin, FakeNight(false)).deps(icons).nightMode.value)
        } finally {
            scope.cancel()
            ReadingAppInit.resetForTest()
            runBlocking { IosAppStartup.resetForTest() }
        }
    }

    /** Boots the app parts the host reads (as the first test does) and runs [block]; tears everything down after. */
    private fun withReadingHost(
        openUrl: (String) -> Unit = {},
        block: (host: IosReadingHost, deps: net.bible.sharedui.reading.host.ReadingHostDeps) -> Unit,
    ) {
        val scope = MainScope()
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
                ReadingAppInit.initialise(platform)
                ReadingAppInit.prepareData(platform)
            }
            val koin = KoinPlatform.getKoin()
            ReadingAppInit.createWindowRepository(scope)
            val p = ColorPainter(Color.Black)
            val icons = ReadingToolbarIcons(p, p, p, p, p, p, p, p, p)
            val host = IosReadingHost(
                scope, IosHostShell(scope, NoNavigator), koin, FakeNight(false), openUrl = openUrl,
            )
            try {
                block(host, host.deps(icons))
            } finally {
                host.dispose()
            }
        } finally {
            scope.cancel()
            ReadingAppInit.resetForTest()
            runBlocking { IosAppStartup.resetForTest() }
        }
    }

    /**
     * No slot of the built deps is left at the inert default `ReadingHostDeps` ships. Default lambdas are shared
     * singletons (non-capturing, hoisted by the compiler), so identity with a default-built instance means "not
     * supplied"; the first assertion pins that premise so the comparison cannot pass vacuously.
     */
    @Test
    fun everySlotIsSupplied() = withReadingHost { host, deps ->
        val p = ColorPainter(Color.Black)
        val icons = ReadingToolbarIcons(p, p, p, p, p, p, p, p, p)
        val d1 = net.bible.sharedui.reading.host.ReadingHostDeps(deps.state, deps.controller, deps.toolbarCallbacks, { icons })
        val d2 = net.bible.sharedui.reading.host.ReadingHostDeps(deps.state, deps.controller, deps.toolbarCallbacks, { icons })
        assertTrue(d1.searchSheetSlot === d2.searchSheetSlot, "premise: default slot lambdas are shared instances")

        assertTrue(deps.agentLogSlot != null && d1.agentLogSlot == null, "agentLogSlot")
        assertTrue(deps.speakBarSlot != null && d1.speakBarSlot == null, "speakBarSlot")
        assertTrue(deps.agentLogVisible !== d1.agentLogVisible, "agentLogVisible")
        assertTrue(deps.speakBarVisible !== d1.speakBarVisible, "speakBarVisible")
        assertTrue(deps.searchSheetSlot !== d1.searchSheetSlot, "searchSheetSlot")
        assertTrue(deps.searchSettingsSlot !== d1.searchSettingsSlot, "searchSettingsSlot")
        assertTrue(deps.textSettingsEditorSlot !== d1.textSettingsEditorSlot, "textSettingsEditorSlot")
        assertTrue(deps.speakSettingsSlot !== d1.speakSettingsSlot, "speakSettingsSlot")
        assertTrue(deps.quickSheetContent !== d1.quickSheetContent, "quickSheetContent")
        assertTrue(deps.readingDialogSlot !== d1.readingDialogSlot, "readingDialogSlot")
        assertTrue(deps.disableAnimations !== d1.disableAnimations, "disableAnimations")
    }

    /** The overlay-exclusion hooks reach the slots' own stacks: opening a quick sheet closes the Speak sheet. */
    @Test
    fun overlayExclusionClosesTheSpeakSheetThroughTheSlots() = withReadingHost { host, _ ->
        host.slots.showSpeakSettings()
        assertTrue(host.slots.speakSheet.depth > 0, "speak sheet opened")
        host.hostState.showHistorySheet()
        assertEquals(0, host.slots.speakSheet.depth, "speak sheet closed by the quick sheet")
    }

    /** The Speak sheet's system-TTS row opens the app's page in Settings through the host's URL port. */
    @Test
    fun speakSettingsOpensSystemSettingsThroughThePort() {
        val opened = mutableListOf<String>()
        withReadingHost(openUrl = { opened += it }) { host, _ ->
            host.slots.speakPlatform.openSystemTtsSettings()
        }
        assertEquals(listOf(UIApplicationOpenSettingsURLString), opened)
    }

    /** The port on its own: one call, one URL, the Settings-app URL. */
    @Test
    fun speakSettingsPlatformOpensTheSettingsUrl() {
        val opened = mutableListOf<String>()
        IosSpeakSettingsPlatform { opened += it }.openSystemTtsSettings()
        assertEquals(listOf(UIApplicationOpenSettingsURLString), opened)
    }
}
