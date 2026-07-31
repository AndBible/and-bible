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
import net.bible.android.database.WorkspaceEntities
import net.bible.android.view.activity.page.screen.ComposeReadingViewHost
import net.bible.service.common.CommonUtils
import net.bible.service.db.DatabaseContainer
import net.bible.service.llm.LlmProviderConfig
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
        assertTrue(item.opensDialog, "allTextOptions launches TextDisplaySettingsActivity")
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
}
