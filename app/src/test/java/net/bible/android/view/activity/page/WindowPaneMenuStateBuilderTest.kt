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
import net.bible.android.database.WorkspaceEntities
import net.bible.android.view.activity.page.screen.ComposeReadingViewHost
import net.bible.service.common.CommonUtils
import net.bible.sharedcore.window.WindowPaneMenuItem
import net.bible.sharedui.textOptionDrawableRes
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
        // A/B batch 4a F4: copySettingsTo is now nested here too (see class kdoc's "RETAINED
        // DIVERGENCE" note), so the empty-history submenu carries allTextOptions + copySettingsTo,
        // not just allTextOptions alone.
        val window = windowRepository.activeWindow

        val textOptionsMenu = itemById(window, WindowPaneMenuStateBuilder.ID_TEXT_OPTIONS_SUBMENU)

        assertEquals(
            listOf(WindowPaneMenuStateBuilder.ID_ALL_TEXT_OPTIONS, WindowPaneMenuStateBuilder.ID_COPY_SETTINGS_TO_SUBMENU),
            textOptionsMenu.submenu.map { it.id },
        )
        val allTextOptions = textOptionsMenu.submenu.first()
        assertTrue(allTextOptions.opensDialog, "allTextOptions bridges into TextDisplaySettingsActivity")
    }

    @Test
    fun bridgedRowsOpenDialogWhileAtomicRowsDoNot() {
        val window = windowRepository.activeWindow
        windowRepository.addNewWindow(window) // second window, so windowClose (isWindowRemovable) is visible

        // A/B batch 4a F4: these are nested under textOptionsSubMenu > copySettingsTo now (classic's
        // shape), not top-level rows -- look them up via findInSubmenus.
        val copyToWorkspace = findInSubmenus(items(window), WindowPaneMenuStateBuilder.ID_COPY_SETTINGS_TO_WORKSPACE)!!
        val copyToGlobal = findInSubmenus(items(window), WindowPaneMenuStateBuilder.ID_COPY_SETTINGS_TO_GLOBAL)!!
        val allTextOptions = findInSubmenus(items(window), WindowPaneMenuStateBuilder.ID_ALL_TEXT_OPTIONS)!!
        // A/B batch 4a whole-batch review M1: copyToWorkspace/copyToGlobal open the SAME
        // chooseSettingsToCopy dialog as the dynamic copySettingsToWindow rows (WindowPaneMenuItem's
        // opensDialog defaults to false, and those rows don't override it) -- and classic itself never
        // marks any of the three (SplitBibleArea.kt:1002-1010). So all three must be false, unlike
        // allTextOptions (a genuinely distinct bridge classic DOES mark, SplitBibleArea.kt:985).
        assertFalse(copyToWorkspace.opensDialog)
        assertFalse(copyToGlobal.opensDialog)
        assertTrue(allTextOptions.opensDialog)

        val windowNew = itemById(window, WindowPaneMenuStateBuilder.ID_WINDOW_NEW)
        val windowClose = itemById(window, WindowPaneMenuStateBuilder.ID_WINDOW_CLOSE)
        assertFalse(windowNew.opensDialog, "windowNew acts immediately via the native-in-Compose seam")
        assertFalse(windowClose.opensDialog, "windowClose acts immediately via the native-in-Compose seam")
    }

    // A/B batch 4a F4: copySettingsToWindowOnlyAppearsWhenAnotherVisibleWindowExists (the old flat-row
    // test) is superseded by copySettingsToHasNoWindowRowsWhenThereIsOnlyOneVisibleWindow and
    // copySettingsToIsNestedUnderTextOptionsWithOneRowPerOtherVisibleWindow below, which assert the
    // same "no window rows when solo / one row per other window" behaviour against the new nested
    // submenu shape.

    @Test
    fun copySettingsToIsNestedUnderTextOptionsWithOneRowPerOtherVisibleWindow() {
        // Classic nests these under textOptionsSubMenu > copySettingsTo, with one dynamic row per OTHER
        // visible window (SplitBibleArea.kt:778-803). Batch 12b follow-on flattened them into three
        // top-level rows plus a picker dialog; the maintainer overruled that in A/B batch 4a (F4).
        val w1 = windowRepository.activeWindow
        windowRepository.addNewWindow(w1)
        windowRepository.addNewWindow(w1)
        val visible = windowRepository.visibleWindows
        assertEquals(3, visible.size, "fixture must have three visible windows")
        val subjectIdx = visible.indexOfFirst { it.id == w1.id }

        val textOptions = itemById(w1, WindowPaneMenuStateBuilder.ID_TEXT_OPTIONS_SUBMENU)
        val copyTo = textOptions.submenu.first { it.id == WindowPaneMenuStateBuilder.ID_COPY_SETTINGS_TO_SUBMENU }

        // The order carried in each id is the index into visibleWindows INCLUDING the subject window —
        // that is what WindowControl.copySettingsToWindow(window, order) expects.
        val expectedOrders = visible.indices.filter { it != subjectIdx }
        assertEquals(
            expectedOrders.map { WindowPaneMenuStateBuilder.idForCopySettingsToWindow(it) } +
                listOf(
                    WindowPaneMenuStateBuilder.ID_COPY_SETTINGS_TO_WORKSPACE,
                    WindowPaneMenuStateBuilder.ID_COPY_SETTINGS_TO_GLOBAL,
                ),
            copyTo.submenu.map { it.id },
        )
        assertTrue(
            items(w1).none { it.id.startsWith("copySettingsTo") },
            "copy-settings rows must no longer be at the top level: ${items(w1).map { it.id }}",
        )
    }

    @Test
    fun copySettingsToHasNoWindowRowsWhenThereIsOnlyOneVisibleWindow() {
        val solo = windowRepository.activeWindow
        assertEquals(1, windowRepository.visibleWindows.size, "fixture must have a single visible window")

        val textOptions = itemById(solo, WindowPaneMenuStateBuilder.ID_TEXT_OPTIONS_SUBMENU)
        val copyTo = textOptions.submenu.first { it.id == WindowPaneMenuStateBuilder.ID_COPY_SETTINGS_TO_SUBMENU }

        assertEquals(
            listOf(
                WindowPaneMenuStateBuilder.ID_COPY_SETTINGS_TO_WORKSPACE,
                WindowPaneMenuStateBuilder.ID_COPY_SETTINGS_TO_GLOBAL,
            ),
            copyTo.submenu.map { it.id },
        )
    }

    @Test
    fun copySettingsToSurvivesAnEmptyDisplaySettingHistory() {
        // Retained divergence: classic removes the WHOLE textOptionsSubMenu when there is no history
        // (SplitBibleArea.kt:816-820), which incidentally hides copy-settings on a fresh install. The
        // port keeps the submenu, holding allTextOptions + copySettingsTo. A fresh test DB already has
        // an empty lastDisplaySettingsSorted, so nothing is seeded here on purpose.
        val w1 = windowRepository.activeWindow
        windowRepository.addNewWindow(w1)

        val textOptions = itemById(w1, WindowPaneMenuStateBuilder.ID_TEXT_OPTIONS_SUBMENU)

        assertEquals(
            listOf(
                WindowPaneMenuStateBuilder.ID_ALL_TEXT_OPTIONS,
                WindowPaneMenuStateBuilder.ID_COPY_SETTINGS_TO_SUBMENU,
            ),
            textOptions.submenu.map { it.id },
        )
    }

    @Test
    fun parseIdReadsTheCopySettingsTargetOrder() {
        // Order 1, not 0: a test that only covers 0 cannot tell "the parsed order" from "the first
        // other window", which is exactly the mistake the picker dialog was introduced to avoid.
        assertEquals(
            WindowPaneMenuStateBuilder.ParsedId.CopySettingsToWindow(1),
            WindowPaneMenuStateBuilder.parseId("copySettingsToWindow:1"),
        )
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
        // A/B batch 4a F4: the icon lives on the copySettingsTo PARENT now (children are iconless,
        // classic's own shape -- see WindowPaneMenuStateBuilder.buildCopySettingsItems's kdoc).
        assertEquals("ic_content_copy_black_24dp", findInSubmenus(items(window), "copySettingsTo")?.iconKey)
        assertNull(findInSubmenus(items(window), "copySettingsToWorkspace")?.iconKey, "copySettingsTo children are iconless")
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
     *
     * NOTE this only covers the rows visible on THIS test's default single-window fixture — most
     * gated rows (`changeToNormal`, `moveWindowSubMenu`, `addWholePageBookmark`, `exportHtml`/
     * `exportStudypad`/`exportStudypadCsv`, `goToReference`, `goToSpeak`, `windowClose`,
     * `llmActionsSubMenu`, and `pinMode` unless autoPin is off) are absent here, so a renamed/
     * removed map entry only THEY depend on would NOT fail this test. The exhaustive,
     * state-independent guarantee is `ComposeReadingViewHostTest.menuIconResIdsIsExactlyTheseTwentyTwoKeys`
     * — don't over-trust this test alone.
     */
    @Test
    fun everyPaneMenuIconKeyIsResolvableByTheHost() {
        fun keys(items: List<WindowPaneMenuItem>): Set<String> =
            items.flatMap { listOfNotNull(it.iconKey) + keys(it.submenu) }.toSet()
        val missing = keys(items(windowRepository.activeWindow)).filter {
            it !in ComposeReadingViewHost.menuIconResIds && textOptionDrawableRes(it) == null
        }
        assertTrue(missing.isEmpty(), "no table resolves: $missing")
    }

    /**
     * A/B batch 3 F4: the "last used actions" rows must carry classic's per-setting icon, not
     * `null`. Deviates from the plan's literal test body in two ways verified against this
     * fixture: (1) a fresh test DB's `lastDisplaySettingsSorted` is empty (see
     * `textOptionsSubMenuCollapsesToASingleAllTextOptionsRowByDefault`), so a display-setting
     * change is seeded via the real route, same as
     * `OptionsMenuStateBuilderTest.lastUsedActionRowsCarryAnIcon`; (2) the dynamic rows nest under
     * the `textOptionsSubMenu` submenu, not the top-level `items(window)` list, so this looks
     * there instead of filtering the top level (which would vacuously find none).
     */
    @Test
    fun lastUsedActionRowsCarryAnIcon() {
        CommonUtils.displaySettingChanged(WorkspaceEntities.TextDisplaySettings.Types.STRONGS)
        val window = windowRepository.activeWindow
        val textOptionsMenu = itemById(window, WindowPaneMenuStateBuilder.ID_TEXT_OPTIONS_SUBMENU)
        val dynamic = textOptionsMenu.submenu.filter { it.id.startsWith("textOptionItem:") }
        assertTrue(dynamic.isNotEmpty(), "fixture has no last-used rows to check")
        assertTrue(dynamic.all { it.iconKey != null }, "iconless: ${dynamic.filter { it.iconKey == null }}")
    }

    @Test
    fun textOptionsSubMenuAlwaysEndsWithAllTextOptionsEvenWithLastUsedRows() {
        // Classic declares allTextOptions as a STATIC child of textOptionsSubMenu at
        // orderInCategory=1000 (res/menu/window_popup_menu.xml), i.e. present whichever dynamic rows
        // exist. The port produced it only in the no-history branch (A/B batch 4a, F3).
        CommonUtils.displaySettingChanged(WorkspaceEntities.TextDisplaySettings.Types.STRONGS)
        val window = windowRepository.activeWindow

        val submenu = itemById(window, WindowPaneMenuStateBuilder.ID_TEXT_OPTIONS_SUBMENU).submenu

        assertTrue(
            submenu.any { it.id.startsWith("textOptionItem:") },
            "fixture has no last-used rows, so this would pass vacuously: ${submenu.map { it.id }}",
        )
        // Deliberately "after every dynamic row" rather than "last": Task 4 appends copySettingsTo
        // after it (classic's orderInCategory 1000 then 1001), and this assertion must survive that.
        val allIdx = submenu.indexOfFirst { it.id == WindowPaneMenuStateBuilder.ID_ALL_TEXT_OPTIONS }
        val lastDynamicIdx = submenu.indexOfLast { it.id.startsWith("textOptionItem:") }
        assertTrue(allIdx > lastDynamicIdx, "allTextOptions must follow the dynamic rows: ${submenu.map { it.id }}")
    }
}
