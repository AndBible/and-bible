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
package net.bible.android.view.activity.page

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.control.page.window.Window
import net.bible.android.control.page.window.WindowControl
import net.bible.android.control.page.window.WindowRepository
import net.bible.android.control.speak.SpeakControl
import net.bible.android.view.activity.page.screen.ComposeReadingViewHost
import net.bible.service.common.CommonUtils
import net.bible.sharedcore.window.WindowPaneMenuItem
import net.bible.test.DatabaseResetter
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Exercises [WindowPaneMenuStateBuilder] against a REAL [WindowControl]/[WindowRepository]/
 * [Window]/[SpeakControl] graph (Robolectric + [TestBibleApplication], same style as
 * [net.bible.android.control.page.window.WindowCommandsImplTest] /
 * [net.bible.android.control.page.toolbar.ToolbarStateServiceImplTest]) rather than mocking the
 * collaborators: `Window`/`WindowControl`/`SpeakControl` are plain (non-`open`) Kotlin classes, so
 * Mockito's default mock maker cannot stub them anyway. `SpeakControl` is resolved from the real
 * Koin container (started by [net.bible.android.BibleApplication.onCreate]), mirroring
 * `ToolbarStateServiceImplTest.setUp`.
 *
 * `workspaceSettings.autoPin = true` by default mirrors [net.bible.android.control.page.window.WindowControlTest] /
 * [net.bible.android.control.page.window.WindowCommandsImplTest]; the one test that needs the
 * `pinMode` row itself (which classic hides entirely while auto-pin is on) flips it off locally,
 * the same way `WindowCommandsImplTest.setPinTogglesPinMode` does.
 *
 * Assertions stick to observable STRUCTURE (ids present/absent, `enabled`/`checkable`/`checked`/
 * `opensDialog`, submenu non-emptiness) rather than exact label strings, mirroring
 * [OptionsMenuStateBuilderTest].
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class WindowPaneMenuStateBuilderTest {

    private lateinit var windowControl: WindowControl
    private lateinit var windowRepository: WindowRepository
    private lateinit var builder: WindowPaneMenuStateBuilder

    @Before
    fun setUp() {
        windowControl = CommonUtils.windowControl
        windowRepository = WindowRepository(CoroutineScope(Dispatchers.Main))
        windowControl.windowRepository = windowRepository
        windowRepository.initialize()
        windowRepository.workspaceSettings.autoPin = true

        val koin = GlobalContext.get()
        builder = WindowPaneMenuStateBuilder(windowControl, koin.get<SpeakControl>())
    }

    @After
    fun tearDown() {
        DatabaseResetter.resetDatabase(windowRepository.scope)
    }

    private fun items(window: Window) = builder.build(window)
    private fun itemById(window: Window, id: String) = items(window).first { it.id == id }
    private fun itemByIdOrNull(window: Window, id: String) = items(window).firstOrNull { it.id == id }
    private fun findInSubmenus(items: List<WindowPaneMenuItem>, id: String): WindowPaneMenuItem? {
        for (item in items) {
            if (item.id == id) return item
            findInSubmenus(item.submenu, id)?.let { return it }
        }
        return null
    }

    @Test
    fun atomicStaticItemsArePresentForANormalUnmaximisedWindow() {
        val w1 = windowRepository.activeWindow
        windowRepository.addNewWindow(w1) // second window, so windowClose/windowMinimise are meaningful
        val built = items(w1)

        assertTrue(built.any { it.id == WindowPaneMenuStateBuilder.ID_WINDOW_NEW })
        assertTrue(built.any { it.id == WindowPaneMenuStateBuilder.ID_WINDOW_MAXIMISE })
        assertTrue(built.any { it.id == WindowPaneMenuStateBuilder.ID_WINDOW_MINIMISE })
        assertTrue(built.any { it.id == WindowPaneMenuStateBuilder.ID_WINDOW_CLOSE })
    }

    @Test
    fun pinModeIsCheckableAndTracksWindowPinState() {
        windowRepository.workspaceSettings.autoPin = false // classic hides pinMode entirely while auto-pin is on
        val window = windowRepository.activeWindow
        window.isPinMode = false

        val unpinned = itemById(window, WindowPaneMenuStateBuilder.ID_PIN_MODE)
        assertTrue(unpinned.checkable, "pinMode is a checkable toggle")
        assertEquals(false, unpinned.checked)
        assertFalse(unpinned.opensDialog, "pinMode acts immediately, atomic via the seam")

        window.isPinMode = true
        val pinned = itemById(window, WindowPaneMenuStateBuilder.ID_PIN_MODE)
        assertEquals(true, pinned.checked)
    }

    @Test
    fun pinModeIsAbsentWhileAutoPinIsOn() {
        // setUp already leaves autoPin = true
        val window = windowRepository.activeWindow
        assertNull(itemByIdOrNull(window, WindowPaneMenuStateBuilder.ID_PIN_MODE))
    }

    @Test
    fun linksWindowHidesWindowNewShowsChangeToNormalAndMaximise() {
        val linksWindow = windowRepository.addNewLinksWindow()

        val built = items(linksWindow)

        assertNull(itemByIdOrNull(linksWindow, WindowPaneMenuStateBuilder.ID_WINDOW_NEW), "windowNew is hidden for a links window")
        // SplitBibleArea.kt:974-977 -- classic's windowMaximise visibility is `!isMaximised` ONLY,
        // no links-window guard, so a links window's Maximise row genuinely shows in classic too.
        assertTrue(built.any { it.id == WindowPaneMenuStateBuilder.ID_WINDOW_MAXIMISE }, "windowMaximise is shown for a links window (classic parity)")
        assertTrue(built.any { it.id == WindowPaneMenuStateBuilder.ID_CHANGE_TO_NORMAL }, "changeToNormal is shown for a links window")
    }

    @Test
    fun windowMinimiseEnabledTracksIsWindowMinimizable() {
        // single window: not minimizable (isWindowMinimizable requires >1 visible window)
        val soleWindow = windowRepository.activeWindow
        val soleItem = itemById(soleWindow, WindowPaneMenuStateBuilder.ID_WINDOW_MINIMISE)
        assertEquals(windowControl.isWindowMinimizable(soleWindow), soleItem.enabled)
        assertFalse(soleItem.enabled, "sanity: a single window cannot be minimized")

        // a second window makes both minimizable
        windowRepository.addNewWindow(soleWindow)
        val afterItem = itemById(soleWindow, WindowPaneMenuStateBuilder.ID_WINDOW_MINIMISE)
        assertEquals(windowControl.isWindowMinimizable(soleWindow), afterItem.enabled)
        assertTrue(afterItem.enabled, "sanity: with 2 windows the first is now minimizable")
    }

    @Test
    fun moveWindowSubMenuHasOneRowPerOtherSamePinModeWindow() {
        val w1 = windowRepository.activeWindow
        val w2 = windowRepository.addNewWindow(w1)

        val moveMenu = itemById(w1, WindowPaneMenuStateBuilder.ID_MOVE_WINDOW_SUBMENU)

        assertTrue(moveMenu.submenu.isNotEmpty(), "moveWindowSubMenu must carry the other window(s)")
        assertTrue(moveMenu.submenu.all { it.id.startsWith("moveItem:") })
        // sanity: w2 exists and shares w1's pin-mode bucket (autoPin=true -> both pinned)
        assertEquals(w1.isPinMode, w2.isPinMode)
    }

    @Test
    fun syncGroupSubMenuHasSixGroupsPlusDisableSyncWhenSynchronised() {
        val window = windowRepository.activeWindow
        window.isSynchronised = true
        window.syncGroup = 2

        val syncMenu = itemById(window, WindowPaneMenuStateBuilder.ID_SYNC_GROUP_SUBMENU)

        assertTrue(syncMenu.submenu.any { it.id == WindowPaneMenuStateBuilder.ID_DISABLE_SYNC }, "disableSync shown while synchronised")
        val groupRows = syncMenu.submenu.filter { it.id.startsWith("syncGroupItem:") }
        assertEquals(5, groupRows.size, "6 groups minus the window's own current group")
    }

    @Test
    fun syncGroupSubMenuHasNoDisableSyncWhenNotSynchronised() {
        val window = windowRepository.activeWindow
        window.isSynchronised = false

        val syncMenu = itemById(window, WindowPaneMenuStateBuilder.ID_SYNC_GROUP_SUBMENU)

        assertFalse(syncMenu.submenu.any { it.id == WindowPaneMenuStateBuilder.ID_DISABLE_SYNC })
        assertEquals(6, syncMenu.submenu.count { it.id.startsWith("syncGroupItem:") })
    }

    @Test
    fun textOptionsSubMenuCollapsesToASingleAllTextOptionsRowByDefault() {
        // fresh test DB -> CommonUtils.lastDisplaySettingsSorted is empty (SplitBibleArea.kt:808-820 collapse)
        val window = windowRepository.activeWindow

        val textOptionsMenu = itemById(window, WindowPaneMenuStateBuilder.ID_TEXT_OPTIONS_SUBMENU)

        assertEquals(1, textOptionsMenu.submenu.size)
        val onlyChild = textOptionsMenu.submenu.first()
        assertEquals(WindowPaneMenuStateBuilder.ID_ALL_TEXT_OPTIONS, onlyChild.id)
        assertTrue(onlyChild.opensDialog, "allTextOptions bridges into TextDisplaySettingsActivity")
    }

    @Test
    fun bridgedRowsOpenDialogWhileAtomicRowsDoNot() {
        val window = windowRepository.activeWindow
        windowRepository.addNewWindow(window) // second window, so windowClose (isWindowRemovable) is visible

        val copyToWorkspace = itemById(window, WindowPaneMenuStateBuilder.ID_COPY_SETTINGS_TO_WORKSPACE)
        val copyToGlobal = itemById(window, WindowPaneMenuStateBuilder.ID_COPY_SETTINGS_TO_GLOBAL)
        val allTextOptions = findInSubmenus(items(window), WindowPaneMenuStateBuilder.ID_ALL_TEXT_OPTIONS)!!
        assertTrue(copyToWorkspace.opensDialog)
        assertTrue(copyToGlobal.opensDialog)
        assertTrue(allTextOptions.opensDialog)

        val windowNew = itemById(window, WindowPaneMenuStateBuilder.ID_WINDOW_NEW)
        val windowClose = itemById(window, WindowPaneMenuStateBuilder.ID_WINDOW_CLOSE)
        assertFalse(windowNew.opensDialog, "windowNew acts immediately via the native-in-Compose seam")
        assertFalse(windowClose.opensDialog, "windowClose acts immediately via the native-in-Compose seam")
    }

    @Test
    fun copySettingsToWindowOnlyAppearsWhenAnotherVisibleWindowExists() {
        val solo = windowRepository.activeWindow
        assertNull(
            itemByIdOrNull(solo, WindowPaneMenuStateBuilder.ID_COPY_SETTINGS_TO_WINDOW),
            "no other window to copy settings to yet",
        )

        windowRepository.addNewWindow(solo)
        assertTrue(items(solo).any { it.id == WindowPaneMenuStateBuilder.ID_COPY_SETTINGS_TO_WINDOW })
    }

    @Test
    fun idsRoundTripThroughParseId() {
        assertEquals(
            WindowPaneMenuStateBuilder.ParsedId.StaticItem(WindowPaneMenuStateBuilder.ID_PIN_MODE),
            WindowPaneMenuStateBuilder.parseId(WindowPaneMenuStateBuilder.ID_PIN_MODE),
        )
        assertEquals(
            WindowPaneMenuStateBuilder.ParsedId.MoveItem(2),
            WindowPaneMenuStateBuilder.parseId(WindowPaneMenuStateBuilder.idForMoveItem(2)),
        )
        assertEquals(
            WindowPaneMenuStateBuilder.ParsedId.SyncGroupItem(3),
            WindowPaneMenuStateBuilder.parseId(WindowPaneMenuStateBuilder.idForSyncGroupItem(3)),
        )
        assertEquals(
            WindowPaneMenuStateBuilder.ParsedId.TextOptionItem(0),
            WindowPaneMenuStateBuilder.parseId(WindowPaneMenuStateBuilder.idForTextOptionItem(0)),
        )
    }

    @Test
    fun parseIdRejectsAnUnknownId() {
        try {
            WindowPaneMenuStateBuilder.parseId("notARealWindowPaneMenuItem")
            throw AssertionError("expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }

    // --- F5b: every row's iconKey mirrors classic's window_popup_menu.xml android:icon ----------

    @Test
    fun paneMenuRowsCarryClassicsIcons() {
        windowRepository.workspaceSettings.autoPin = false // pinMode is hidden entirely while auto-pin is on (see pinModeIsAbsentWhileAutoPinIsOn)
        val window = windowRepository.activeWindow
        windowRepository.addNewWindow(window) // second window, so windowClose (isWindowRemovable) is present

        assertEquals("ic_window_add_outline_black_24dp", itemById(window, "windowNew").iconKey)
        assertEquals("ic_pin", itemById(window, "pinMode").iconKey)
        assertEquals("ic_close_white_24dp", itemById(window, "windowClose").iconKey)
        assertEquals("ic_content_copy_black_24dp", itemById(window, "copySettingsToWorkspace").iconKey)
        assertEquals("ic_text_options_24dp", findInSubmenus(items(window), "allTextOptions")?.iconKey)
    }

    @Test
    fun paneMenuRowsClassicLeavesIconlessHaveNoIconKey() {
        val window = windowRepository.activeWindow
        window.isSynchronised = true // disableSync only appears while synchronised (see syncGroupSubMenuHasSixGroupsPlusDisableSyncWhenSynchronised)

        val disableSync = findInSubmenus(items(window), "disableSync")
        assertTrue(disableSync != null, "sanity: disableSync must actually be present to test its iconKey")
        assertNull(disableSync.iconKey)
    }

    /**
     * The failure this guards against is a builder emitting a key the host's `menuIconResIds` map
     * lacks — the icon then silently disappears (same drift risk as
     * `ComposeReadingViewHostTest.drawerIconResIdsCoverEveryBuilderIconKey`).
     */
    @Test
    fun everyPaneMenuIconKeyIsResolvableByTheHost() {
        fun keys(items: List<WindowPaneMenuItem>): Set<String> =
            items.flatMap { listOfNotNull(it.iconKey) + keys(it.submenu) }.toSet()
        val missing = keys(items(windowRepository.activeWindow)) - ComposeReadingViewHost.menuIconResIds.keys
        assertTrue(missing.isEmpty(), "menuIconResIds is missing: $missing")
    }
}
