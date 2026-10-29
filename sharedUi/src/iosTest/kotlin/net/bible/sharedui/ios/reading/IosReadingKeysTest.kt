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
import kotlinx.coroutines.cancel
import net.bible.android.control.link.LinkControl
import net.bible.android.control.page.window.WindowControl
import net.bible.sharedcore.nav.PassageResult
import net.bible.sharedcore.navigation.GridStep
import net.bible.sharedcore.platform.AppSettings
import net.bible.sharedcore.reading.KeyChooserKind
import net.bible.sharedcore.reading.ReadingQuickSheet
import net.bible.sharedlogic.platform.NightModeState
import net.bible.sharedui.assembly.IosAppStartup
import net.bible.sharedui.ios.ports.IosChooserHostPlatform
import net.bible.sharedcore.reading.ToolbarStateService
import net.bible.sharedui.reading.host.paneButtonsShown
import net.bible.sharedui.reading.session.ConfigInputs
import org.crosswire.jsword.book.BookCategory
import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.versification.BibleBook
import org.crosswire.jsword.versification.system.Versifications
import org.koin.core.Koin
import org.koin.mp.KoinPlatform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * One test per reading preference that the iOS reading surface must honour. Each writes the Android key through the
 * production [AppSettings] and asserts what the iOS consumer does with it. The settings are removed in `finally` (the
 * database-backed store is shared by the whole test run).
 */
class IosReadingKeysTest {
    private class FakeNightForKeys(override val nightMode: Boolean) : NightModeState

    /** The config inputs the active window's loader builds (`set_config`), read through the production settings. */
    private fun configFor(koin: Koin, beta: Boolean = false): ConfigInputs = IosDocumentLoaderPlatform(
        window = { koin.get<WindowControl>().activeWindow },
        windowControl = { koin.get<WindowControl>() },
        settings = { koin.get<AppSettings>() },
        nightModeState = { FakeNightForKeys(false) },
        isPortrait = ::iosIsPortrait,
        isBeta = { beta },
    ).configInputs(emptyList())

    /**
     * `toolbar_button_actions`: a swap mode makes the Bible button switch to the suggested Bible instead of opening the
     * document sheet. The iOS fixture installs one Bible, so the switch itself is only observable with a second one; with
     * one, the swap mode's toolbar state hides the Bible button (no suggestion) and the press leaves the document alone.
     * Both branches are asserted, so the test holds whichever module set runs; the window's Bible and the `default-BIBLE`
     * setting are restored in `finally`.
     */
    @Test
    fun toolbarButtonActionsSwapsTheToolbarAction() = withIosReadingHost { host, deps ->
        val koin = KoinPlatform.getKoin()
        val settings = koin.get<AppSettings>()
        val pageManager = koin.get<WindowControl>().activeWindowPageManager
        val originalBible = pageManager.currentBible.currentDocument
        val defaultBibleKey = "default-" + BookCategory.BIBLE.name
        val originalDefault = settings.getString(defaultBibleKey, null)
        try {
            settings.setString("toolbar_button_actions", "swap-menu")
            assertEquals("swap-menu", host.toolbarHost.toolbarButtonSetting, "the toolbar host reads the setting")
            host.toolbarHost.refreshToolbar()
            val suggested = host.toolbarHost.suggestedBibleInitials()
            assertEquals(
                suggested != null, koin.get<ToolbarStateService>().toolbar.value.showBible,
                "swap: the Bible button is shown only when there is a suggested Bible",
            )
            deps.toolbarCallbacks.onBible()
            assertNull(host.hostState.quickSheet.value, "swap: the document sheet does not open")
            if (suggested != null) {
                assertEquals(suggested, pageManager.currentBible.currentDocument?.initials, "swap: switches to the suggested Bible")
            } else {
                assertEquals(originalBible?.initials, pageManager.currentBible.currentDocument?.initials, "swap, no suggestion: nothing changes")
            }
        } finally {
            settings.setString("toolbar_button_actions", null)
            originalBible?.let { pageManager.setCurrentDocument(it) }
            settings.setString(defaultBibleKey, originalDefault)
        }
    }

    /** `show_errorbox`: Android shows the error box only in a beta build (`BibleView.showErrorBox`); iOS now follows that gate. */
    @Test
    fun showErrorboxReachesTheConfig() = withIosReadingHost { _, _ ->
        val koin = KoinPlatform.getKoin()
        val settings = koin.get<AppSettings>()
        try {
            settings.setBoolean("show_errorbox", true)
            assertFalse(configFor(koin, beta = false).showErrorBox, "release build: the setting alone does not show the box")
            assertTrue(configFor(koin, beta = true).showErrorBox, "beta build: the setting reaches the config")
        } finally {
            settings.setBoolean("show_errorbox", null)
        }
        assertFalse(configFor(koin, beta = true).showErrorBox, "setting removed: the box is off again")
    }

    /**
     * `open_links_in_special_window_pref`: a link from the text (`LinkControl.showLink`, the path the reading bridge takes)
     * opens the links window when on, and in the current window when off.
     */
    @Test
    fun specialWindowPrefOpensLinksInTheLinksWindow() = withIosReadingHost { _, _ ->
        val koin = KoinPlatform.getKoin()
        val settings = koin.get<AppSettings>()
        val windowControl = koin.get<WindowControl>()
        val linkControl = koin.get<LinkControl>()
        val key = Verse(Versifications.instance().getVersification("KJV"), BibleBook.GEN, 1, 1)
        val main = windowControl.activeWindow
        val links = main.targetLinksWindow
        try {
            // `targetLinksWindow` creates the links window on first use, and a new links window starts MINIMISED. The
            // explicit minimise keeps the premise ("no links window is showing") independent of that default.
            windowControl.windowRepository.minimise(links)
            assertFalse(links.isVisible, "premise: no links window is open")
            settings.setBoolean("open_links_in_special_window_pref", false)
            linkControl.showLink(null, key)
            assertFalse(links.isVisible, "pref off: the link stays in the current window")
            assertEquals(main.id, windowControl.activeWindow.id)

            settings.setBoolean("open_links_in_special_window_pref", true)
            linkControl.showLink(null, key)
            assertTrue(links.isVisible, "pref on: the link opens the links window")
            assertEquals(links.id, windowControl.activeWindow.id, "pref on: the links window is the active one")
        } finally {
            settings.setBoolean("open_links_in_special_window_pref", null)
            if (links.isVisible) windowControl.closeWindow(links)
        }
    }

    /**
     * `navigate_to_verse_pref`: picking a chapter in the passage grid ends at its first verse when the pref is off, and
     * goes on to the verse step when on. The grid reads the pref when the controller is built, so it is set first.
     * The quick-sheet key chooser reads the same pref into its `navigateToVerse` flag.
     */
    @Test
    fun navigateToVersePrefChangesTheChooserFlow() = withIosReadingHost { host, _ ->
        val koin = KoinPlatform.getKoin()
        val settings = koin.get<AppSettings>()
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val chooser = IosChooserHostPlatform(koin, scope, { IosAppStartup.stringTable }, net.bible.sharedui.ios.ports.IosWindowMetrics())
        try {
            settings.setBoolean("navigate_to_verse_pref", false)
            val offResults = mutableListOf<String>()
            val off = chooser.gridChoosePassageController(scope, true) { result: PassageResult -> offResults += result.verse }
            // The grid's book ids are BibleBook ordinals (the intro "books" come first), not positions among the real books.
            off.pick(BibleBook.GEN.ordinal) // Genesis: has chapters, so the chapter step
            off.pick(1) // chapter 1: finishes at its first verse
            assertEquals(listOf("Gen.1.1"), offResults, "pref off: a chapter finishes the chooser")

            settings.setBoolean("navigate_to_verse_pref", true)
            val onResults = mutableListOf<String>()
            val on = chooser.gridChoosePassageController(scope, true) { result: PassageResult -> onResults += result.verse }
            on.pick(BibleBook.GEN.ordinal)
            on.pick(1)
            assertEquals(emptyList(), onResults, "pref on: a chapter does not finish the chooser")
            assertEquals(GridStep.VERSE, on.step.value, "pref on: the verse step follows")

            settings.setBoolean("navigate_to_verse_pref", false)
            host.hostState.showKeyChooserSheet(KeyChooserKind.Grid, emptyList())
            assertEquals(ReadingQuickSheet.KeyChooser(KeyChooserKind.Grid, navigateToVerse = false), host.hostState.quickSheet.value)
            host.hostState.closeQuickSheet()
            settings.setBoolean("navigate_to_verse_pref", true)
            host.hostState.showKeyChooserSheet(KeyChooserKind.Grid, emptyList())
            assertEquals(ReadingQuickSheet.KeyChooser(KeyChooserKind.Grid, navigateToVerse = true), host.hostState.quickSheet.value)
            host.hostState.closeQuickSheet()
        } finally {
            settings.setBoolean("navigate_to_verse_pref", null)
            scope.cancel()
        }
    }

    /**
     * `hide_window_buttons`: the rail's gate is the shared [paneButtonsShown] predicate that `ReadingHost` calls, read from
     * the host's own settings store. Hidden with the pref on, shown with it off or unset, and a maximised window hides it.
     */
    @Test
    fun hideWindowButtonsHidesThem() = withIosReadingHost { host, _ ->
        val settings = KoinPlatform.getKoin().get<AppSettings>()
        try {
            settings.setBoolean("hide_window_buttons", true)
            assertFalse(paneButtonsShown(host.hostState.settings, maximised = false), "pref on: the rail is hidden")
            settings.setBoolean("hide_window_buttons", false)
            assertTrue(paneButtonsShown(host.hostState.settings, maximised = false), "pref off: the rail is shown")
            assertFalse(paneButtonsShown(host.hostState.settings, maximised = true), "a maximised window hides the rail")
        } finally {
            settings.setBoolean("hide_window_buttons", null)
        }
    }

    /** `show_active_window_indicator`: the loader's `hasActiveIndicator` needs the pref on (default on), an active window, and a second window. */
    @Test
    fun activeWindowIndicatorReachesTheConfig() = withIosReadingHost { _, _ ->
        val koin = KoinPlatform.getKoin()
        val settings = koin.get<AppSettings>()
        val windowControl = koin.get<WindowControl>()
        assertFalse(configFor(koin).hasActiveIndicator, "one window: no indicator")
        val second = windowControl.addNewWindow(windowControl.windowRepository.activeWindow)
        try {
            assertTrue(configFor(koin).hasActiveIndicator, "two windows, pref unset (default on): the active window shows it")
            settings.setBoolean("show_active_window_indicator", false)
            assertFalse(configFor(koin).hasActiveIndicator, "pref off: no indicator")
        } finally {
            settings.setBoolean("show_active_window_indicator", null)
            windowControl.closeWindow(second)
        }
    }

    /** `font_size_multiplier`: the percentage (150) reaches the config as a factor (1.5); unset is 100 %. */
    @Test
    fun fontSizeMultiplierReachesTheConfig() = withIosReadingHost { _, _ ->
        val koin = KoinPlatform.getKoin()
        val settings = koin.get<AppSettings>()
        try {
            settings.setInt("font_size_multiplier", 150)
            assertEquals(1.5f, configFor(koin).fontSizeMultiplier)
        } finally {
            settings.setInt("font_size_multiplier", null)
        }
        assertEquals(1f, configFor(koin).fontSizeMultiplier, "unset: 100 %")
    }

    /** `disable_click_to_edit`: the pref reaches the config, which the page reads to require a long press to edit a note. */
    @Test
    fun disableClickToEditReachesTheConfig() = withIosReadingHost { _, _ ->
        val koin = KoinPlatform.getKoin()
        val settings = koin.get<AppSettings>()
        try {
            settings.setBoolean("disable_click_to_edit", true)
            assertTrue(configFor(koin).disableClickToEdit)
        } finally {
            settings.setBoolean("disable_click_to_edit", null)
        }
        assertFalse(configFor(koin).disableClickToEdit)
    }
}
