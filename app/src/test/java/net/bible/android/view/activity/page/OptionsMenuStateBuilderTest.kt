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
import net.bible.android.view.activity.base.ActivityBase
import net.bible.android.view.activity.page.screen.ComposeReadingViewHost
import net.bible.android.view.activity.settings.TextDisplaySettingsServiceImpl
import net.bible.service.common.CommonUtils
import net.bible.service.db.DatabaseContainer
import net.bible.service.llm.LlmProviderConfig
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
 * against a REAL [MainBibleActivity]/[WindowControl]/[WindowRepository] graph (Robolectric +
 * [TestBibleApplication], same style as [net.bible.android.control.page.window.WindowCommandsImplTest] /
 * [net.bible.android.control.page.toolbar.ToolbarStateServiceImplTest]) rather than mocking the
 * collaborators — `getItemOptions`'s [net.bible.android.view.activity.page.GeneralPreference]/
 * [net.bible.android.view.activity.page.Preference] subclasses are plain (non-`open`) Kotlin
 * classes closing over real `windowRepository`/`windowControl` state, which Mockito's default
 * mock maker cannot stub anyway.
 *
 * The activity is deliberately built WITHOUT `.create()`: [MainBibleActivity.buildOptionsMenuItems]/
 * [MainBibleActivity.handleOptionsMenuItem] (for the static, non-`Preference`-backed rows exercised
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
    private lateinit var activity: MainBibleActivity

    @Before
    fun setUp() {
        windowControl = CommonUtils.windowControl
        windowRepository = WindowRepository(CoroutineScope(Dispatchers.Main))
        windowControl.windowRepository = windowRepository
        windowRepository.initialize()

        activity = Robolectric.buildActivity(MainBibleActivity::class.java).get()
        activity.windowRepository = windowRepository
    }

    @After
    fun tearDown() {
        CommonUtils.settings.removeBoolean("use_compose_ui")
        CommonUtils.settings.setString("lastDisplaySettings", null)
        DatabaseResetter.resetDatabase(windowRepository.scope)
    }

    private fun items() = activity.buildOptionsMenuItems()
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

        val stayOpen = activity.handleOptionsMenuItem(id)

        assertTrue(stayOpen, "a boolean toggle must tell the host to stay open (and rebuild)")
        val after = itemById(id)
        assertEquals(!before.checked, after.checked)
    }

    @Test
    fun windowTopLabelForReturnsNullForAnUnknownWindow() {
        assertNull(activity.windowTopLabelFor("not-a-window-id"))
    }

    @Test
    fun windowTopLabelForReturnsThePageTitleOfAKnownWindow() {
        val id = activity.windowRepository.activeWindow.id.toString()
        val titleText = activity.windowRepository.activeWindow.pageManager.titleText
        assertTrue(titleText.isNotBlank(), "sanity: a fresh workspace's default verse gives a non-blank titleText")
        assertEquals(titleText, activity.windowTopLabelFor(id))
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
    // dispatch's `else` branch (where the boolean-toggle check has already failed) now checks
    // `textSettingEditorPageFor(itemOptions.type.name)` before falling through to
    // `itemOptions.openDialog`. The four tests below pin, in order: the interception firing for a
    // sheet-editable type with the flag on; the classic dialog still firing for that SAME type
    // with the flag off (so nothing about the type itself changed, only the flag); and the two
    // negatives that matter most -- a boolean row (never reaches the `else` branch at all) and
    // BOOKMARKS_HIDELABELS (reaches the `else` branch but `textSettingEditorPageFor` returns null
    // for it) must both still behave exactly as before, flag or no flag.
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
    fun aSheetEditableTextOptionGoesToTheHostWhenComposeIsOn() {
        CommonUtils.settings.setBoolean("use_compose_ui", true)
        val host = ComposeReadingViewHost(activity)
        activity.composeReadingViewHost = host
        val pref = FontSizePreference(workspaceSettingsBundle())

        val stayOpen = OptionsMenuStateBuilder.dispatch(activity, { _, _ -> pref }, "textOptionItem:0")

        assertFalse(stayOpen, "a sheet takeover returns false, exactly like a classic dialog launch")
        assertEquals(SettingsEditorPage.Row("FONTSIZE"), host.textSettingsEditor.current)
    }

    @Test
    fun aSheetEditableTextOptionStillOpensTheClassicDialogWhenComposeIsOff() {
        CommonUtils.settings.setBoolean("use_compose_ui", false)
        val pref = RecordingPreference(workspaceSettingsBundle(), WorkspaceEntities.TextDisplaySettings.Types.FONTSIZE)

        val stayOpen = OptionsMenuStateBuilder.dispatch(activity, { _, _ -> pref }, "textOptionItem:0")

        assertFalse(stayOpen)
        assertTrue(pref.openDialogCalled, "flag off must still reach openDialog, unchanged from before T11")
    }

    /** Negative #1: a boolean row never reaches the `else` branch at all (the `isBoolean` check
     *  above it returns first) -- pins that the sheet interception did not somehow widen to catch
     *  toggles too. [WorkspaceEntities.TextDisplaySettings.Types.SECTIONTITLES] is not one of the
     *  eight sheet-editable types either, so this also independently confirms it wasn't reached
     *  via that route. */
    @Test
    fun aBooleanTextOptionStillTogglesAndKeepsTheMenuOpen() {
        CommonUtils.settings.setBoolean("use_compose_ui", true)
        val pref = Preference(workspaceSettingsBundle(), WorkspaceEntities.TextDisplaySettings.Types.SECTIONTITLES)
        val before = pref.value as Boolean

        val stayOpen = OptionsMenuStateBuilder.dispatch(activity, { _, _ -> pref }, "textOptionItem:0")

        assertTrue(stayOpen, "a boolean toggle must stay open, never divert to the sheet")
        assertEquals(!before, pref.value, "the toggle itself must still have flipped")
    }

    /** Negative #2: BOOKMARKS_HIDELABELS DOES reach the `else` branch (it is not boolean) but
     *  `textSettingEditorPageFor` returns `null` for it (it bridges to ManageLabels, not a sheet
     *  row) -- pins that the interception did not widen to catch it too. */
    @Test
    fun hideLabelsStillLaunchesManageLabelsRatherThanASheet() {
        CommonUtils.settings.setBoolean("use_compose_ui", true)
        val pref = RecordingPreference(workspaceSettingsBundle(), WorkspaceEntities.TextDisplaySettings.Types.BOOKMARKS_HIDELABELS)

        val stayOpen = OptionsMenuStateBuilder.dispatch(activity, { _, _ -> pref }, "textOptionItem:0")

        assertFalse(stayOpen)
        assertTrue(pref.openDialogCalled, "HIDELABELS is not sheet-editable, so it must still reach openDialog")
    }

    /**
     * The second menu -- the pane (☰) menu's `MainBibleActivity.handleWindowTextOptionItem`, which
     * has no injectable `getItemOptions` (it builds the real [Preference] itself from
     * [CommonUtils.lastDisplaySettingsSorted]), so this drives it end to end through the public
     * [MainBibleActivity.handleWindowPaneMenuItem] bridge rather than constructing a fixture
     * directly. Confirms the WINDOW-level branch added in the same task step: the scope handed to
     * the host is `settingsBundle.toScope()` at WINDOW level, not the workspace-level one the
     * overflow-menu tests above exercise.
     */
    @Test
    fun windowPaneSheetEditableTextOptionGoesToTheHostWhenComposeIsOn() {
        CommonUtils.settings.setBoolean("use_compose_ui", true)
        val host = ComposeReadingViewHost(activity)
        activity.composeReadingViewHost = host
        CommonUtils.displaySettingChanged(WorkspaceEntities.TextDisplaySettings.Types.FONTSIZE)
        val window = windowRepository.activeWindow

        val stayOpen = activity.handleWindowPaneMenuItem(
            window.id.toString(), WindowPaneMenuStateBuilder.idForTextOptionItem(0))

        assertFalse(stayOpen)
        assertEquals(SettingsEditorPage.Row("FONTSIZE"), host.textSettingsEditor.current)
    }

    /**
     * Final fix wave, Fix 6: [windowPaneSheetEditableTextOptionGoesToTheHostWhenComposeIsOn]'s
     * flag-off twin, missing before this fix round. `MainBibleActivity.handleWindowTextOptionItem`
     * and `OptionsMenuStateBuilder.dispatch`'s `else` branch are HAND-DUPLICATED code, not a shared
     * helper, so the overflow menu's own on/off pair (
     * [aSheetEditableTextOptionGoesToTheHostWhenComposeIsOn] /
     * [aSheetEditableTextOptionStillOpensTheClassicDialogWhenComposeIsOff]) proves nothing about
     * this second, independently maintained copy.
     *
     * Can't reuse [RecordingPreference] here the way the overflow twin does: unlike
     * `OptionsMenuStateBuilder.dispatch`, `handleWindowTextOptionItem` has no injectable
     * `getItemOptions` -- it always builds the real `Preference` via the module-level `getPrefItem`.
     * So this asserts the same thing the overflow twin asserts, by the same "assert the routing
     * decision, not the UI" convention this file's class kdoc states, just via the one seam that
     * IS available: the RETURN VALUE. `handleWindowTextOptionItem`'s only `false`-returning paths
     * are the sheet takeover -- gated on `composeReadingViewHost != null`, and so unreachable with
     * no host installed -- and the classic `itemOptions.openDialog(...); false` tail. So `false`
     * here can only mean the classic dialog path ran, exactly as before T11 (proven not to crash
     * under Robolectric with a non-`.create()`d activity, matching the ON twin's own house style of
     * driving the real [MainBibleActivity.handleWindowPaneMenuItem] bridge rather than a fixture).
     *
     * Batch Z-late epilogue, Task 1: this used to clear `use_compose_ui` and leave the host
     * installed. The flag clause is gone from the interception (spec 10.2 -- it is now
     * `page != null && host != null`), so the OFF state this pins is "no host installed".
     *
     * Task 2, carrying a Task 1 review finding: Task 1 kept building a local host that it then did
     * not install, and asserted `assertNull` on its editor stack. Production never sees that
     * object, so the assertion could not fail; both it and the local are gone.
     */
    @Test
    fun windowPaneSheetEditableTextOptionStillOpensTheClassicDialogWhenNoHostIsInstalled() {
        activity.composeReadingViewHost = null
        CommonUtils.displaySettingChanged(WorkspaceEntities.TextDisplaySettings.Types.FONTSIZE)
        val window = windowRepository.activeWindow

        val stayOpen = activity.handleWindowPaneMenuItem(
            window.id.toString(), WindowPaneMenuStateBuilder.idForTextOptionItem(0))

        assertFalse(stayOpen, "no host must still reach the classic dialog tail, which returns false")
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
