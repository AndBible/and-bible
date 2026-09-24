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
import net.bible.android.activity.R
import net.bible.android.control.page.window.WindowControl
import net.bible.android.control.page.window.WindowRepository
import net.bible.android.database.SettingsBundle
import net.bible.android.database.SettingsLevel
import net.bible.android.database.WorkspaceEntities
import androidx.test.core.app.ApplicationProvider
import net.bible.android.view.activity.base.ActivityBase
import net.bible.android.view.activity.nav.NavHostComposeActivity
import net.bible.android.view.activity.page.screen.ComposeReadingViewHost
import net.bible.android.view.activity.settings.TextDisplaySettingsServiceImpl
import net.bible.service.common.CommonUtils
import net.bible.service.db.DatabaseContainer
import net.bible.service.llm.LlmProviderConfig
import net.bible.sharedcore.nav.NavRoutes
import net.bible.sharedcore.settings.SettingsEditorPage
import net.bible.sharedcore.settings.SettingsScope
import net.bible.sharedcore.settings.TextSettingType
import net.bible.sharedcore.settings.TextSettingValue
import net.bible.sharedui.textOptionDrawableRes
import net.bible.test.DatabaseResetter
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Exercises [OptionsMenuStateBuilder] (build + dispatch + the `id`<->`(resId, order)` mapping)
 * against a REAL reading-route [NavHostComposeActivity]/[WindowControl]/[WindowRepository] graph (Robolectric +
 * [TestBibleApplication], same style as [net.bible.android.control.page.window.WindowCommandsImplTest] /
 * [net.bible.android.control.page.toolbar.ToolbarStateServiceImplTest]) rather than mocking the
 * collaborators — `getItemOptions`'s [net.bible.android.view.activity.page.GeneralPreference]/
 * [net.bible.android.view.activity.page.Preference] subclasses are plain (non-`open`) Kotlin
 * classes closing over real `windowRepository`/`windowControl` state, which Mockito's default
 * mock maker cannot stub anyway.
 *
 * The activity is deliberately built WITHOUT `.create()`: [ReadingCommands.buildOptionsMenuItems]/
 * [ReadingCommands.handleOptionsMenuItem] (for the static, non-`Preference`-backed rows exercised
 * here) only need `windowRepository` — set directly, mirroring how `WindowCommandsImplTest` wires a
 * fresh [WindowRepository] into the shared [WindowControl] singleton — not any of `onCreate()`'s
 * UI/WebView setup (`binding`, `documentViewManager`, ...), which a toggle's `.handle()` for OTHER
 * items (e.g. night mode's `applyTheme()`) may touch. `autoPinMode` is used for the dispatch test
 * because `WindowPinningPreference.handle()` (`WindowControl.autoPinChanged()`) only touches window
 * state, not the activity's views.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class OptionsMenuStateBuilderTest {

    private lateinit var windowControl: WindowControl
    private lateinit var windowRepository: WindowRepository
    private lateinit var activity: NavHostComposeActivity

    @Before
    fun setUp() {
        windowControl = CommonUtils.windowControl
        windowRepository = WindowRepository(CoroutineScope(Dispatchers.Main))
        windowControl.windowRepository = windowRepository
        windowRepository.initialize()

        activity = Robolectric.buildActivity(
            NavHostComposeActivity::class.java,
            NavHostComposeActivity.intentFor(ApplicationProvider.getApplicationContext(), NavRoutes.READING),
        ).get()
        activity.readingAppBootstrap.windowRepository = windowRepository
    }

    @After
    fun tearDown() {
        CommonUtils.settings.setString("lastDisplaySettings", null)
        DatabaseResetter.resetDatabase(windowRepository.scope)
    }

    private fun items() = activity.readingCommands.buildOptionsMenuItems()
    private fun itemById(id: String) = items().first { it.id == id }
    private fun itemByIdOrNull(id: String) = items().firstOrNull { it.id == id }

    @Test
    fun nightModeIsAVisibleCheckableToggle() {
        val item = itemById(OptionsMenuStateBuilder.idFor(R.id.nightMode, 0))
        assertTrue(item.checkable, "night mode is a checkable toggle")
        assertFalse(item.opensDialog, "a toggle does not open a further dialog")
        assertTrue(item.label.isNotBlank())
    }

    @Test
    fun fullscreenIsAVisibleNonCheckableAction() {
        val item = itemById(OptionsMenuStateBuilder.idFor(R.id.fullscreen, 0))
        assertFalse(item.checkable, "fullscreen is a fire-and-forget action, not a toggle")
        assertFalse(item.opensDialog, "fullscreen acts immediately, it does not open a dialog")
        assertTrue(item.label.isNotBlank())
    }

    @Test
    fun allTextOptionsOpensADialogAndIsNotCheckable() {
        val item = itemById(OptionsMenuStateBuilder.idFor(R.id.allTextOptions, 0))
        assertTrue(item.opensDialog, "allTextOptions launches the text-display settings screen")
        assertFalse(item.checkable)
        assertTrue(item.label.isNotBlank())
    }

    @Test
    fun splitModeIsAbsentForASingleWindowWorkspace() {
        assertFalse(windowRepository.isMultiWindow, "sanity: fresh workspace has one window")
        assertNull(itemByIdOrNull(OptionsMenuStateBuilder.idFor(R.id.splitMode, 0)))
    }

    @Test
    fun llmActionsSubMenuIsAbsentWhenLlmIsNotConfigured() {
        assertFalse(CommonUtils.settings.llmConfigured, "sanity: no LlmProviderConfig in a fresh test DB")
        assertNull(itemByIdOrNull(OptionsMenuStateBuilder.idFor(R.id.llmActionsSubMenu, 0)))
    }

    @Test
    fun idRoundTripsForBothStaticAndDynamicEntries() {
        for (resId in listOf(
            R.id.fullscreen, R.id.nightMode, R.id.switchToWorkspace, R.id.tiltToScroll,
            R.id.splitMode, R.id.autoPinMode, R.id.autoAssignLabels, R.id.llmActionsSubMenu,
            R.id.allTextOptions,
        )) {
            val id = OptionsMenuStateBuilder.idFor(resId, 0)
            assertEquals(OptionsMenuStateBuilder.ParsedId(resId, 0), OptionsMenuStateBuilder.parseId(id))
        }

        val dynamicId = OptionsMenuStateBuilder.idFor(R.id.textOptionItem, 3)
        assertEquals("textOptionItem:3", dynamicId)
        assertEquals(OptionsMenuStateBuilder.ParsedId(R.id.textOptionItem, 3), OptionsMenuStateBuilder.parseId(dynamicId))
    }

    @Test
    fun parseIdRejectsAnUnknownId() {
        try {
            OptionsMenuStateBuilder.parseId("notARealMenuItem")
            throw AssertionError("expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun togglingABooleanItemStaysOpenAndFlipsChecked() {
        val id = OptionsMenuStateBuilder.idFor(R.id.autoPinMode, 0)
        val before = itemById(id)

        val stayOpen = activity.readingCommands.handleOptionsMenuItem(id)

        assertTrue(stayOpen, "a boolean toggle must tell the host to stay open (and rebuild)")
        val after = itemById(id)
        assertEquals(!before.checked, after.checked)
    }

    @Test
    fun windowTopLabelForReturnsNullForAnUnknownWindow() {
        assertNull(activity.readingCommands.windowTopLabelFor("not-a-window-id"))
    }

    @Test
    fun windowTopLabelForReturnsThePageTitleOfAKnownWindow() {
        val id = activity.hostWindowRepository.activeWindow.id.toString()
        val titleText = activity.hostWindowRepository.activeWindow.pageManager.titleText
        assertTrue(titleText.isNotBlank(), "sanity: a fresh workspace's default verse gives a non-blank titleText")
        assertEquals(titleText, activity.readingCommands.windowTopLabelFor(id))
    }

    // --- F5b: every static row's iconKey mirrors classic's main_bible_options_menu.xml android:icon ---

    @Test
    fun staticOverflowRowsCarryClassicsIcons() {
        // llmActionsSubMenu is hidden unless an LlmProviderConfig exists (see
        // llmActionsSubMenuIsAbsentWhenLlmIsNotConfigured) -- seed one so the row is present.
        DatabaseContainer.instance.aiSettingsDb.llmProviderConfigDao()
            .insert(LlmProviderConfig(providerType = "GEMINI", displayName = "Test Gemini"))

        assertEquals("ic_full_screen_24", itemById("fullscreen").iconKey)
        assertEquals("ic_night_mode_24", itemById("nightMode").iconKey)
        assertEquals("icon_robot", itemById("llmActionsSubMenu").iconKey)
        assertEquals("ic_text_options_24dp", itemById("allTextOptions").iconKey)
    }

    /**
     * A/B batch 3 F4: the "last used actions" rows must carry classic's per-setting icon, not
     * `null` (this test previously asserted the opposite, back when the port shipped them
     * iconless -- see the fixed `OptionsMenuStateBuilder.build`'s dynamic loop). Nothing seeds
     * `lastDisplaySettings` by default, so without this the filtered list below would be empty and
     * `all {}` would pass vacuously (whole-batch review Minor #6) -- seed one display-setting
     * change via the real route (`OptionsMenuStateBuilder.build`'s dynamic loop iterates
     * `CommonUtils.lastDisplaySettingsSorted`) so a genuine row exists to assert against.
     */
    @Test
    fun lastUsedActionRowsCarryAnIcon() {
        CommonUtils.displaySettingChanged(WorkspaceEntities.TextDisplaySettings.Types.STRONGS)
        val dynamic = items().filter { it.id.startsWith("textOptionItem:") }
        assertTrue(dynamic.isNotEmpty(), "fixture has no last-used rows to check")
        assertTrue(dynamic.all { it.iconKey != null }, "iconless: ${dynamic.filter { it.iconKey == null }}")
    }

    /**
     * The failure this guards against is a builder emitting a key the host's `menuIconResIds` map
     * lacks — the icon then silently disappears (same drift risk as
     * `ComposeReadingViewHostTest.drawerIconResIdsCoverEveryBuilderIconKey`).
     *
     * NOTE this only covers the rows visible on THIS test's default fixture — no `LlmProviderConfig`
     * is seeded here (unlike `staticOverflowRowsCarryClassicsIcons`, which seeds its own), so
     * `llmActionsSubMenu`/`"icon_robot"` is absent and NOT exercised by this test either. A
     * renamed/removed `menuIconResIds` entry only a gated row (like this one) depends on would not
     * fail this test. The exhaustive, state-independent guarantee is
     * `ComposeReadingViewHostTest.menuIconResIdsIsExactlyTheseTwentyTwoKeys` — don't over-trust this
     * test alone.
     */
    @Test
    fun everyOverflowIconKeyIsResolvableByTheHost() {
        val missing = items().mapNotNull { it.iconKey }.filter {
            it !in ComposeReadingViewHost.menuIconResIds && textOptionDrawableRes(it) == null
        }
        assertTrue(missing.isEmpty(), "no table resolves: $missing")
    }

    // ------------------------------------------------------------------------------------------
    // Settings editor sheets T11: both reading-view menus route to the in-place sheet.
    //
    // dispatch's `else` branch (where the boolean-toggle check has already failed) checks
    // `textSettingEditorPageFor(itemOptions.type.name)` first. `page != null` means a sheet-editable
    // type: it takes the sheet if a host is mounted, or logs and returns false if not (platform-
    // dialog removal Task 10 -- unreachable in production, since this menu only exists on the host
    // that renders it). `page == null` (CommandPreference, AutoAssignPreference, HIDELABELS, and
    // every type textSettingEditorPageFor does not name) still falls through to `itemOptions
    // .openDialog`, host or no host -- that's the only fall-through left. The tests below pin all
    // three live cases:
    //   * page != null, host set   -> the sheet takes over;
    //   * page != null, no host    -> logged and returns false, `openDialog` NOT reached;
    //   * page == null             -> the classic dialog (host state irrelevant), for HIDELABELS.
    // Plus the negative that never reaches the `else` branch at all: a boolean row.
    // ------------------------------------------------------------------------------------------

    private fun workspaceSettingsBundle() = SettingsBundle(
        level = SettingsLevel.WORKSPACE,
        workspaceId = windowRepository.id,
        workspaceName = windowRepository.name,
        workspaceSettings = windowRepository.textDisplaySettings,
        globalSettings = CommonUtils.globalTextDisplaySettings,
    )

    /**
     * A [Preference] whose `openDialog` only records that it was called, standing in for the real
     * dialog/activity launch it would otherwise perform (a real `AlertDialog` for most types, or
     * -- for [WorkspaceEntities.TextDisplaySettings.Types.BOOKMARKS_HIDELABELS] --
     * `HideLabelsPreference`'s `ManageLabels` intent round-trip via `activity.lifecycleScope` +
     * `awaitIntent`, which needs a resumed activity this file deliberately does not build). The
     * negative-routing tests below only need to know WHETHER dispatch reached `openDialog`, not
     * what it draws, so this keeps them fast and independent of that machinery -- the same
     * "assert the routing decision, not the UI" instruction the positive test follows by reading
     * [ComposeReadingViewHost.textSettingsEditor] instead of rendering anything.
     */
    private class RecordingPreference(
        settings: SettingsBundle,
        type: WorkspaceEntities.TextDisplaySettings.Types,
    ) : Preference(settings, type) {
        var openDialogCalled = false
        override fun openDialog(activity: ActivityBase, onChanged: ((value: Any) -> Unit)?, onReset: (() -> Unit)?): Boolean {
            openDialogCalled = true
            return true
        }
    }

    @Test
    fun aSheetEditableTextOptionGoesToTheHost() {
        val host = ComposeReadingViewHost(activity)
        activity.composeReadingViewHost = host
        val pref = FontSizePreference(workspaceSettingsBundle())

        val stayOpen = OptionsMenuStateBuilder.dispatch(activity, { activity.hostWindowRepository }, { activity.composeReadingViewHost }, { _, _ -> pref }, "textOptionItem:0")

        assertFalse(stayOpen, "a sheet takeover returns false, exactly like a classic dialog launch")
        assertEquals(SettingsEditorPage.Row("FONTSIZE"), host.textSettingsEditor.current)
    }

    /**
     * `host == null` for a type that IS sheet-editable — one of the two null operands that used to
     * make the classic fall-through reachable.
     *
     * Platform-dialog removal Task 10: this used to assert `openDialog` still ran (retargeted from
     * an even older `...WhenComposeIsOff` flag test by the Batch Z-late epilogue). This overflow
     * menu only exists on the Compose reading toolbar the host itself renders, and slice 8 made
     * NavHost the only reading host, so a sheet-editable type with no host mounted is now treated as
     * unreachable in production: `dispatch` logs and returns `false` instead of falling through to
     * `openDialog` — which no longer even HAS a body for FONTSIZE (`FontSizeWidget`'s dialog code is
     * deleted by this task too, alongside `StrongsPreference`'s and the other seven sheet types').
     */
    @Test
    fun aSheetEditableTextOptionWithNoHostLogsAndStaysClosedRatherThanOpeningTheClassicDialog() {
        activity.composeReadingViewHost = null
        val pref = RecordingPreference(workspaceSettingsBundle(), WorkspaceEntities.TextDisplaySettings.Types.FONTSIZE)

        val stayOpen = OptionsMenuStateBuilder.dispatch(activity, { activity.hostWindowRepository }, { activity.composeReadingViewHost }, { _, _ -> pref }, "textOptionItem:0")

        assertFalse(stayOpen)
        assertFalse(pref.openDialogCalled, "no host mounted is now unreachable in production, so openDialog must NOT be reached any more")
    }

    /** Negative #1: a boolean row never reaches the `else` branch at all (the `isBoolean` check
     *  above it returns first) -- pins that the sheet interception did not somehow widen to catch
     *  toggles too. [WorkspaceEntities.TextDisplaySettings.Types.SECTIONTITLES] is not one of the
     *  eight sheet-editable types either, so this also independently confirms it wasn't reached
     *  via that route. */
    @Test
    fun aBooleanTextOptionStillTogglesAndKeepsTheMenuOpen() {
        val pref = Preference(workspaceSettingsBundle(), WorkspaceEntities.TextDisplaySettings.Types.SECTIONTITLES)
        val before = pref.value as Boolean

        val stayOpen = OptionsMenuStateBuilder.dispatch(activity, { activity.hostWindowRepository }, { activity.composeReadingViewHost }, { _, _ -> pref }, "textOptionItem:0")

        assertTrue(stayOpen, "a boolean toggle must stay open, never divert to the sheet")
        assertEquals(!before, pref.value, "the toggle itself must still have flipped")
    }

    /** Negative #2: BOOKMARKS_HIDELABELS DOES reach the `else` branch (it is not boolean) but
     *  `textSettingEditorPageFor` returns `null` for it (it bridges to ManageLabels, not a sheet
     *  row) -- pins that the interception did not widen to catch it too. */
    @Test
    fun hideLabelsStillLaunchesManageLabelsRatherThanASheet() {
        val pref = RecordingPreference(workspaceSettingsBundle(), WorkspaceEntities.TextDisplaySettings.Types.BOOKMARKS_HIDELABELS)

        val stayOpen = OptionsMenuStateBuilder.dispatch(activity, { activity.hostWindowRepository }, { activity.composeReadingViewHost }, { _, _ -> pref }, "textOptionItem:0")

        assertFalse(stayOpen)
        assertTrue(pref.openDialogCalled, "HIDELABELS is not sheet-editable, so it must still reach openDialog")
    }

    /**
     * `page == null` WITH a host mounted — the other null operand, and the gap Task 1's review
     * found (its item b). [hideLabelsStillLaunchesManageLabelsRatherThanASheet] above runs on this
     * file's default fixture, which installs no host, so it cannot tell the two nulls apart: it
     * would pass identically if `textSettingEditorPageFor` started returning a page for HIDELABELS,
     * because the missing host would carry the assertion on its own.
     *
     * With the flag gone `host != null` is true in every production reading-view state, so
     * `page == null` is the only live reason the `else` branch's classic fall-through still exists
     * at all. Nothing pinned it before this test.
     */
    @Test
    fun hideLabelsStillLaunchesManageLabelsEvenWithAHostInstalled() {
        activity.composeReadingViewHost = ComposeReadingViewHost(activity)
        val pref = RecordingPreference(workspaceSettingsBundle(), WorkspaceEntities.TextDisplaySettings.Types.BOOKMARKS_HIDELABELS)

        val stayOpen = OptionsMenuStateBuilder.dispatch(activity, { activity.hostWindowRepository }, { activity.composeReadingViewHost }, { _, _ -> pref }, "textOptionItem:0")

        assertFalse(stayOpen)
        assertTrue(
            pref.openDialogCalled,
            "HIDELABELS has no sheet page, so even a mounted host must leave it on openDialog",
        )
    }

    /**
     * The second menu -- the pane (☰) menu's `ReadingCommands.handleWindowTextOptionItem`, which
     * has no injectable `getItemOptions` (it builds the real [Preference] itself from
     * [CommonUtils.lastDisplaySettingsSorted]), so this drives it end to end through the public
     * [ReadingCommands.handleWindowPaneMenuItem] bridge rather than constructing a fixture
     * directly. Confirms the WINDOW-level branch added in the same task step: the scope handed to
     * the host is `settingsBundle.toScope()` at WINDOW level, not the workspace-level one the
     * overflow-menu tests above exercise.
     */
    @Test
    fun windowPaneSheetEditableTextOptionGoesToTheHost() {
        val host = ComposeReadingViewHost(activity)
        activity.composeReadingViewHost = host
        CommonUtils.displaySettingChanged(WorkspaceEntities.TextDisplaySettings.Types.FONTSIZE)
        val window = windowRepository.activeWindow

        val stayOpen = activity.readingCommands.handleWindowPaneMenuItem(
            window.id.toString(), WindowPaneMenuStateBuilder.idForTextOptionItem(0))

        assertFalse(stayOpen)
        assertEquals(SettingsEditorPage.Row("FONTSIZE"), host.textSettingsEditor.current)
    }

    /**
     * [windowPaneSheetEditableTextOptionGoesToTheHost]'s no-host twin. `ReadingCommands
     * .handleWindowTextOptionItem` and `OptionsMenuStateBuilder.dispatch`'s `else` branch are
     * HAND-DUPLICATED code, not a shared helper, so the overflow menu's own pair proves nothing
     * about this second, independently maintained copy.
     *
     * Platform-dialog removal Task 10: `handleWindowTextOptionItem`'s `host == null` fall-through no
     * longer reaches `openDialog` at all (that body doesn't even exist for FONTSIZE any more --
     * `FontSizeWidget`'s dialog code is deleted by this task). This ☰ pane menu only exists on the
     * Compose reading toolbar the host itself renders, and slice 8 made NavHost the only reading
     * host, so a sheet-editable type with no host mounted is treated as unreachable in production: it
     * logs and returns `false`. The RETURN VALUE is the only seam available here (no injectable
     * `getItemOptions`, unlike `dispatch`), so unlike the overflow twin this test can only confirm
     * "still returns false, doesn't crash" -- it does not by itself distinguish the log+return path
     * from a hypothetical dialog path the way it used to when `openDialog` was still live.
     */
    @Test
    fun windowPaneSheetEditableTextOptionWithNoHostLogsAndReturnsFalseWithoutCrashing() {
        activity.composeReadingViewHost = null
        CommonUtils.displaySettingChanged(WorkspaceEntities.TextDisplaySettings.Types.FONTSIZE)
        val window = windowRepository.activeWindow

        val stayOpen = activity.readingCommands.handleWindowPaneMenuItem(
            window.id.toString(), WindowPaneMenuStateBuilder.idForTextOptionItem(0))

        assertFalse(stayOpen, "no host mounted is unreachable in production; the log+return path still returns false")
    }

    // ------------------------------------------------------------------------------------------
    // Task 10 (platform-dialog removal): the Strongs toolbar button's long-press used to call
    // `StrongsPreference.openDialog` unconditionally -- a native `AlertDialog.Builder` picker with
    // no host check at all. It now routes through the SAME `textSettingEditorPageFor` +
    // `showTextSettingEditor` seam the two menus above use. STRONGS always resolves to a page
    // (`TextSettingEditorPageForTest.theFourListChoiceTypesOpenTheirOwnRowPage`), so the only
    // variable left is whether a host is mounted -- and `StrongsPreference.openDialog` is deleted
    // outright by this task, so there is no classic fallback to test for the no-host case (unlike
    // the two `dispatch`/`handleWindowTextOptionItem` fall-throughs above, which keep `openDialog`
    // alive for OTHER, still-dialog types).
    // ------------------------------------------------------------------------------------------

    @Test
    fun strongsLongPressOpensTheTextSettingsSheetNotAPlatformDialog() {
        val host = ComposeReadingViewHost(activity)
        activity.composeReadingViewHost = host

        activity.readingCommands.composeStrongsLong()

        assertEquals(SettingsEditorPage.Row("STRONGS"), host.textSettingsEditor.current)
    }

    /**
     * Settles spec §6.7's open item: does the sheet's edit path (`TextDisplaySettingsController`'s
     * mutators -> `TextDisplaySettingsServiceImpl.setValue`) keep
     * [CommonUtils.lastDisplaySettingsSorted] -- the very list [build] reads its dynamic rows from
     * -- fresh, the way the classic `Preference.value` setter always has
     * (`OptionsMenuItems.kt:183`)? `setValue` (`TextDisplaySettingsServiceImpl.kt:173`) writes via
     * `getPrefItem(bundle, classic).value = ...`, i.e. THE SAME classic [Preference.value] setter,
     * so it already reaches [CommonUtils.displaySettingChanged] -- ALREADY WIRED, not something
     * this task needed to add. (`lastDisplaySettingsSorted` sorts by enum name for a stable menu
     * render order, not by recency -- `ColorSettingsRecentActionTest` established the same
     * "recorded at all" contract for COLORS's own, separate mutators; this test is that same
     * contract for the eight `Row` types' shared `setValue` path.)
     */
    @Test
    fun editingThroughTheSheetsControllerKeepsTheRecentSettingsListFresh() {
        CommonUtils.settings.setString("lastDisplaySettings", null)
        assertFalse(
            CommonUtils.lastDisplaySettingsSorted.contains(WorkspaceEntities.TextDisplaySettings.Types.MARGINSIZE),
            "sanity: a fresh recent list has no MARGINSIZE entry"
        )

        val impl = TextDisplaySettingsServiceImpl()
        impl.setValue(
            SettingsScope.Workspace(windowRepository.id.toString()),
            TextSettingType.MARGINSIZE,
            TextSettingValue.MarginsValue(5, 5, 180),
        )

        assertTrue(
            CommonUtils.lastDisplaySettingsSorted.contains(WorkspaceEntities.TextDisplaySettings.Types.MARGINSIZE),
            "editing through the sheet's controller/service must record MARGINSIZE as recently used, same as the classic Preference.value setter"
        )
    }
}
