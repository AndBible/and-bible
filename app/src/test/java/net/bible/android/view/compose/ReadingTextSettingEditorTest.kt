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
package net.bible.android.view.compose

import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.control.page.window.WindowControl
import net.bible.android.control.page.window.WindowRepository
import net.bible.android.view.activity.base.CurrentActivityHolder
import net.bible.android.view.activity.nav.NavHostComposeActivity
import net.bible.android.view.activity.page.screen.ComposeReadingViewHost
import net.bible.service.common.CommonUtils
import net.bible.sharedcore.ai.reading.ReadingLlmDialog
import net.bible.sharedcore.nav.NavRoutes
import net.bible.sharedcore.settings.ColorField
import net.bible.sharedcore.settings.SettingsEditorPage
import net.bible.sharedcore.settings.SettingsScope
import net.bible.sharedcore.settings.TextDisplaySettingsController
import net.bible.sharedcore.settings.TextDisplaySettingsLabels
import net.bible.sharedcore.settings.TextDisplaySettingsService
import net.bible.sharedcore.settings.TextSettingRowValue
import net.bible.sharedcore.settings.TextSettingType
import net.bible.test.DatabaseResetter
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Settings editor sheets T10 — the `:app` host wiring for the reading view's in-place
 * text-settings editor ([ComposeReadingViewHost.showTextSettingEditor]).
 *
 * No test here uses `createComposeRule` yet (same limit `ReadingSearchHostTest` documents -- and
 * the same correction `SettingsEditorSheetGuardTest`'s kdoc makes: `androidx.compose.ui:ui-test-
 * junit4`/`ui-test-manifest` ARE dependencies of `:app`, `app/build.gradle.kts:508-509`, and eleven
 * `:app` test files already use it, so unavailability is not the reason), and no golden covers
 * `ComposeReadingViewHost`, so these tests drive the host API directly — the callable, tested
 * surface this task is required to leave behind, with nothing yet wired to call it (Task 11 owns
 * the two menu dispatch sites).
 *
 * Host construction/Koin setup is copied verbatim from `ReadingSearchHostTest.setUpRealHost`/
 * `tearDownRealHost` — a real reading-route [NavHostComposeActivity]/[WindowControl]/[WindowRepository] graph, the
 * activity built WITHOUT `.create()`, the host constructed directly and never `.install()`ed.
 */
@RunWith(RobolectricTestRunner::class)
// TestBibleApplication, not the bare Application the other `*HostTest`s use — see
// ReadingSearchHostTest's own kdoc for why (Book.isEpub's static initializer requirement doesn't
// bite here, but this mirrors that fixture exactly rather than inventing a leaner one).
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class ReadingTextSettingEditorTest {

    private lateinit var windowControl: WindowControl
    private lateinit var windowRepository: WindowRepository
    private lateinit var activity: NavHostComposeActivity

    /** Mirrors `ReadingSearchHostTest.setUpRealHost`/`tearDownRealHost` verbatim. */
    @Before
    fun setUpRealHost() {
        windowControl = CommonUtils.windowControl
        windowRepository = WindowRepository(CoroutineScope(Dispatchers.Main))
        windowControl.windowRepository = windowRepository
        windowRepository.initialize()

        activity = Robolectric.buildActivity(
            NavHostComposeActivity::class.java,
            NavHostComposeActivity.intentFor(ApplicationProvider.getApplicationContext(), NavRoutes.READING),
        ).get()
        activity.readingAppBootstrap.windowRepository = windowRepository
        activity.setNewHistoryTraversal(GlobalContext.get().get())
        CurrentActivityHolder.activate(activity)
    }

    @After
    fun tearDownRealHost() {
        CurrentActivityHolder.deactivate(activity)
        DatabaseResetter.resetDatabase(windowRepository.scope)
    }

    private fun host() = ComposeReadingViewHost(activity)

    @Test
    fun showTextSettingEditorOpensTheStackAtThatPage() {
        val host = host()

        host.showTextSettingEditor(
            SettingsScope.Workspace("ws"),
            SettingsEditorPage.Row(TextSettingType.FONTSIZE.name),
        ) { }

        assertEquals(SettingsEditorPage.Row("FONTSIZE"), host.textSettingsEditor.current)
        assertEquals(1, host.textSettingsEditor.depth)
    }

    @Test
    fun openingASecondEditorReplacesTheFirstStackRatherThanStackingOnIt() {
        val host = host()

        host.showTextSettingEditor(SettingsScope.Workspace("ws"), SettingsEditorPage.Colors) { }
        host.textSettingsEditor.push(SettingsEditorPage.ColorPick(ColorField.DAY_TEXT))
        host.showTextSettingEditor(SettingsScope.Workspace("ws"), SettingsEditorPage.Row("FONTSIZE")) { }

        assertEquals(1, host.textSettingsEditor.depth)
        assertEquals(SettingsEditorPage.Row("FONTSIZE"), host.textSettingsEditor.current)
    }

    // ---- Final fix wave, Fix 1: showTextSettingEditor must refresh the cached controller ---------

    /**
     * [ComposeReadingViewHost.textSettingsControllerFor] caches one [TextDisplaySettingsController]
     * per [SettingsScope] for the lifetime of the host, and that controller only reloads its
     * [TextDisplaySettingsController.state] after one of its OWN writes. Nothing forced a reload
     * when the editor merely re-opens: `showTextSettingEditor` used to just set the scope and open
     * the stack. So an edit made through a DIFFERENT controller instance touching the same
     * scope+service -- here standing in for the settings screen's own `controllerCache`, a second
     * window, sync, or a classic dialog -- was invisible to the reading view's cached instance:
     * the reopened sheet would show the OLD value and confirming would write it back, silently
     * undoing the other edit. Reproduces the concrete repro in the review (font size 18 -> the
     * settings screen sets it to 30 -> reopening the reading-view sheet must show 30, not 18).
     */
    @Test
    fun showTextSettingEditorRefreshesTheCachedControllerBeforeReopening() {
        val host = host()
        val scope = SettingsScope.Workspace("ws")
        val key = TextSettingType.FONTSIZE.name

        // First open: getOrPut-constructs and caches the controller for this scope.
        host.showTextSettingEditor(scope, SettingsEditorPage.Row(key)) { }
        val initial = (host.textSettingsControllerFor(scope).state.value.rows
            .getValue(TextSettingType.FONTSIZE).value as TextSettingRowValue.Numeric).value
        host.textSettingsEditor.close()

        // Mutate the SAME underlying setting through a second, independent controller instance
        // wrapping the SAME injected service -- exactly what a controller the host never touches
        // (settings screen, second window, sync, classic dialog) would do.
        val service = GlobalContext.get().get<TextDisplaySettingsService>()
        val other = TextDisplaySettingsController(
            service = service,
            settingsScope = scope,
            labels = TextDisplaySettingsLabels.forTest(),
            onNavigateCallback = { },
        )
        val changed = if (initial == 30) 18 else 30
        other.onNumericChange(key, changed)

        // Reopening must see the fresh value from the SAME cached controller instance, not the
        // stale snapshot it captured on first open.
        host.showTextSettingEditor(scope, SettingsEditorPage.Row(key)) { }
        val reopened = (host.textSettingsControllerFor(scope).state.value.rows
            .getValue(TextSettingType.FONTSIZE).value as TextSettingRowValue.Numeric).value

        assertEquals(changed, reopened)
    }

    // ---- Fix round 1, Finding 1: the pane subtree must survive this editor too ------------------

    /**
     * The invariant `ReadingSearchHostTest.openingAndClosingSearchMustNotRebuildThePaneSubtree`
     * pins for search, pinned here for the in-place text-settings editor: nothing on this path may
     * reach [net.bible.android.view.activity.page.screen.ComposeReadingViewGeneration.rebuild] — a
     * bump re-runs every pane's `AndroidView` factory, destroying and recreating every `BibleView`
     * WebView (losing the loaded document, the scroll position and every bit of JS state) — exactly
     * what would happen to a user nudging a font size mid-read if the slot were ever moved back
     * inside the generation-key block, or if the editor's state were ever fed into whatever computes
     * the generation.
     *
     * No test here uses `createComposeRule` yet, so [ComposeReadingViewHost.TextSettingsEditorSlot]
     * itself never composes in this file (same limit `ReadingSearchHostTest` documents, and the
     * same correction this file's own class kdoc makes above -- `createComposeRule` is available
     * in `:app`, just not exercised here) -- what CAN run is every
     * plain-function step the slot's composition would otherwise trigger: [ComposeReadingViewHost
     * .showTextSettingEditor]/[net.bible.sharedcore.settings.SettingsEditorStack.close] themselves,
     * plus the two controller constructions the slot resolves via `remember(scope) { ... }`
     * ([ComposeReadingViewHost.textSettingsControllerFor]/[ComposeReadingViewHost.colorControllerFor],
     * widened to `internal` for exactly this). Both page kinds are covered because they take
     * different paths through the slot: a `Row` page only ever resolves a
     * `TextDisplaySettingsController`, while `Colors`/`ColorPick`/`BackgroundImage` additionally
     * resolve a `ColorSettingsController` (never cached -- a FRESH instance every time, per T10's
     * own kdoc) -- if constructing either one could itself bump the generation, one of these two
     * tests would say so.
     */
    @Test
    fun openingAndClosingTheRowEditorMustNotRebuildThePaneSubtree() {
        val host = host()
        assertEquals(0, host.generationForTest.state.value, "sanity: nothing has rebuilt yet")

        val scope = SettingsScope.Workspace("ws")
        host.showTextSettingEditor(scope, SettingsEditorPage.Row(TextSettingType.FONTSIZE.name)) { }
        // What TextSettingsEditorSlot's `remember(scope) { textSettingsControllerFor(scope) }` would
        // resolve for a Row page, driven directly since composition cannot run here.
        host.textSettingsControllerFor(scope)
        host.textSettingsEditor.close()

        assertEquals(
            0, host.generationForTest.state.value,
            "opening/closing the row editor (and resolving its TextDisplaySettingsController) must " +
                "not bump the generation -- a bump remounts every pane's BibleView WebView",
        )
    }

    @Test
    fun openingAndClosingTheColoursEditorMustNotRebuildThePaneSubtree() {
        val host = host()
        assertEquals(0, host.generationForTest.state.value, "sanity: nothing has rebuilt yet")

        val scope = SettingsScope.Workspace("ws")
        host.showTextSettingEditor(scope, SettingsEditorPage.Colors) { }
        // What TextSettingsEditorSlot's `remember(scope) { colorControllerFor(scope) }` would
        // resolve for the Colors page family, driven directly since composition cannot run here.
        host.colorControllerFor(scope)
        host.textSettingsEditor.close()

        assertEquals(
            0, host.generationForTest.state.value,
            "opening/closing the colours editor (and constructing its ColorSettingsController) " +
                "must not bump the generation",
        )
    }

    /**
     * Task 5: `AppDialogOverlay`'s `onSheetOpening` calls `NavHostComposeActivity.composeReadingViewHost
     * ?.closeModalOverlays()` before showing an app-wide dialog/sheet, so it never stacks under (or
     * gets stacked under by) a reading-view modal overlay. Opens the text-settings sheet, then
     * asserts [ComposeReadingViewHost.closeModalOverlays] closed it.
     */
    @Test
    fun closeModalOverlaysClosesTheTextSettingsEditor() {
        val host = host()
        host.showTextSettingEditor(SettingsScope.Workspace("ws"), SettingsEditorPage.Colors) { }
        assertEquals(1, host.textSettingsEditor.depth, "sanity: the sheet is open")

        host.closeModalOverlays()

        assertEquals(0, host.textSettingsEditor.depth, "closeModalOverlays must close the text-settings editor")
    }

    /**
     * Controller ruling (2026-09-25, platform-dialog removal Task 18 EXTRA step):
     * [ComposeReadingViewHost.closeModalOverlays] used to dismiss `readingLlmDialogs` unconditionally
     * for [net.bible.sharedcore.reading.ReadingOverlay.Llm], which silently dropped a pending LLM
     * answer -- e.g. free-text instructions the user had already typed into the regenerate-confirm
     * dialog -- the moment ANY app-wide dialog/sheet opened. `Regenerate` is one of the two
     * `AlertDialog` arms (`ReadingLlmDialogController`'s `onSheetOpening` kdoc: a dialog over a sheet
     * is fine, and is not what the exclusion rule is about), so it must survive.
     */
    @Test
    fun closeModalOverlaysDoesNotDismissTheLlmRegenerateConfirm() {
        val host = host()
        var regenerateCalls = 0
        host.readingLlmDialogs.openRegenerate("page-1") { _, _, _, _, _ -> regenerateCalls++ }
        assertIs<ReadingLlmDialog.Regenerate>(host.readingLlmDialogs.state.value.dialog, "sanity: the confirm is showing")

        host.closeModalOverlays()

        assertIs<ReadingLlmDialog.Regenerate>(
            host.readingLlmDialogs.state.value.dialog,
            "closeModalOverlays must not dismiss the regenerate confirm -- a dialog over a sheet is fine",
        )
        assertEquals(0, regenerateCalls, "closeModalOverlays must not itself run the regenerate action")
    }

    /**
     * The other half of the same fix: `PromptSelector`/`ModelSelection` ARE `ModalBottomSheet`s (the
     * two arms `ReadingLlmDialogController`'s `onSheetOpening` DOES fire for), exactly what this
     * exclusion rule is about, so `closeModalOverlays` must keep dismissing them. Reaches
     * `PromptSelector` through the real built-in prompts (`PromptRepository`/`BuiltInPrompts` ship
     * several for `VERSE_SELECTION` with no seeding needed), and idles the main looper because
     * `readingLlmDialogs`' `hostScope` dispatches on `Dispatchers.Main`.
     */
    @Test
    fun closeModalOverlaysStillDismissesTheLlmPromptSelectorSheet() {
        val host = host()
        host.readingLlmDialogs.openPromptSelector("VERSE_SELECTION", null) { _, _, _ -> }
        // openPromptSelector's prompt-group lookup runs on Dispatchers.IO (a real background
        // thread), so its Main-dispatched state update can land after a single idle() call —
        // bounded poll, same idiom as ReadingHostSyncAndRestoreEventsTest.returningToForegroundStartsSync.
        val deadline = System.currentTimeMillis() + 5_000
        while (host.readingLlmDialogs.state.value.dialog !is ReadingLlmDialog.PromptSelector &&
            System.currentTimeMillis() < deadline
        ) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
        assertIs<ReadingLlmDialog.PromptSelector>(
            host.readingLlmDialogs.state.value.dialog,
            "sanity: the built-in VERSE_SELECTION prompts opened the sheet",
        )

        host.closeModalOverlays()

        assertEquals(
            ReadingLlmDialog.None, host.readingLlmDialogs.state.value.dialog,
            "closeModalOverlays must still dismiss a sheet-shaped picker",
        )
    }
}
