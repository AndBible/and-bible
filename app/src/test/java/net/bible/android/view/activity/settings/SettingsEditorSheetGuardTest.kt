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
package net.bible.android.view.activity.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File

/**
 * Strips Kotlin `//` line comments and slash-star ... star-slash block comments from [text], so a
 * source-text scan can't be defeated -- or taxed -- by a comment that legitimately quotes the very
 * pattern the scan looks for. Same helper as
 * `net.bible.android.view.activity.page.screen.SearchSheetStructureGuardTest` and
 * `net.bible.android.view.activity.SearchHostBackRoutingGuardTest` -- both are `private` top-level
 * functions scoped to their own file (and in different packages), so there is nothing importable to
 * share; this copy follows the same shape and the same documented rationale rather than inventing a
 * third one.
 *
 * Deliberately simple, NOT a Kotlin lexer: it does not track string literals, so a comment marker
 * that happens to appear inside a Kotlin string constant would be (wrongly) treated as the start of
 * a comment. Acceptable for a guard scanning hand-written production source, where that pattern
 * doesn't occur in the lines these assertions care about.
 */
private fun stripComments(text: String): String {
    val noBlockComments = Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL).replace(text, "")
    return noBlockComments.lines().joinToString("\n") { line ->
        val commentAt = line.indexOf("//")
        if (commentAt >= 0) line.substring(0, commentAt) else line
    }
}

/** Reads [path] (relative to the `:app` module root, i.e. this test's working directory) and
 *  returns its comment-stripped source, via [stripComments]. */
private fun strippedSource(path: String): String = stripComments(File(path).readText())

/**
 * Structural guards for the settings editor sheet. These are source-text assertions, in the style of
 * `SearchSheetStructureGuardTest` and `SettingsBadgeLayoutDriftTest`.
 *
 * **Correction (T13 fix round 1, Finding 2):** an earlier version of this kdoc claimed the
 * behaviours here "cannot be reached by a `:app` unit test (no `ComposeTestRule` in this repo)".
 * That is false -- `androidx.compose.ui:ui-test-junit4`/`ui-test-manifest` ARE dependencies of this
 * module (`app/build.gradle.kts`), and eleven `:app` test files already use
 * `createComposeRule`/`createAndroidComposeRule`, including `SettingsBadgeLayoutDriftTest`'s own
 * neighbour `TextDisplaySettingsComposeActivityColorsTest` and the new
 * `SettingsEditorSheetListChoiceScrollTest` this fix round added. `SearchSheetStructureGuardTest`'s
 * own kdoc already warns against exactly this overclaim, for the same reason: compose-ui-test is
 * available in this module, so unavailability is not why a behaviour goes untested here.
 *
 * The real reason these three guards are source-text assertions rather than `createComposeRule`
 * tests is narrower and per-guard: [noSettingsScreenConstructsItsOwnModalBottomSheet] and
 * [theSettingsPathHoldsNoEditorAlertDialogs] are absence checks across whole files, which a render
 * test cannot express (there is no "assert this composable was never called" render assertion); and
 * [noGoldenTestCapturesSettingsEditorSheet] is a check on OTHER test files' source, which is
 * inherently a source scan, not a render test, regardless of what harness is available. The sheet's
 * own chrome (header/back-arrow/close button/title truncation/RTL) and the page-routing wiring have
 * NOT been proven by any test yet -- that is a real, currently-open gap, not an impossible one; see
 * `docs/compose-ondevice-verification-checklist.md`'s "Settings editor bottom sheets" round entry.
 * What remains permanently off-limits, for every harness, is capturing an OPEN `ModalBottomSheet` in
 * Roborazzi -- that hangs the whole `:app` suite -- which is exactly what
 * [noGoldenTestCapturesSettingsEditorSheet] polices.
 */
class SettingsEditorSheetGuardTest {

    /**
     * The checkable half of the "one sheet" invariant (Settings editor sheets design, §6.6, amended
     * during execution). The spec's ORIGINAL wording claimed "exactly one `ModalBottomSheet` per
     * screen" -- that is false as a structural count: `TextDisplaySettingsScreen` legitimately holds
     * two `SettingsEditorSheet` call sites (`AbSettingsScreen`'s generic one for the three
     * list-choice/text-input/multi-select row kinds, plus `TextSettingRowEditorSheet` for the numeric
     * and margin pages), and `AiConnectionSettingsScreen` likewise holds its own beside the generic
     * one. That is safe BY CONSTRUCTION, not by luck: `SettingsEditorSheet`/`ColorSettingsEditorSheet`/
     * `TextSettingRowEditorSheet` all render nothing when their page/stack is empty, and a single row
     * tap can only ever populate one stack -- `AbSettingsContent.onOpenEditor` fires for the three
     * generic row kinds, each screen's own trigger fires for the others, and no row is both. So two
     * `ModalBottomSheet`s can never actually be open together, and asserting "at most one call site"
     * would be asserting something false about working, reviewed code.
     *
     * What IS checkable, and what actually matters, is the other half: no settings screen constructs
     * a `ModalBottomSheet` directly. Every screen reaches Material3's sheet only through
     * `SettingsEditorSheet` (which every sheet composable in this package -- including
     * `ColorSettingsEditorSheet` and `TextSettingRowEditorSheet` -- itself calls). A screen that grew
     * its own `ModalBottomSheet(...)` would either duplicate the chrome/dismiss contract
     * `SettingsEditorSheet` centralises, or -- if it could ever be open at the same time as the
     * routed one -- recreate the "two open sheets" M3 violation and Roborazzi hazard the design
     * avoided by construction. This guard would fail the moment either screen source gained a literal
     * `ModalBottomSheet(` call.
     *
     * **One deliberate exception (17f-A9):** `AiConnectionSettingsScreen.kt` now legitimately holds
     * exactly ONE direct `ModalBottomSheet(` call, `RetentionSheet` (the log-retention editor). Its
     * state is a single independent `Boolean` toggled by this screen's own `onNavigate` override, not
     * a `SettingsEditorPage`/`SettingsEditorStack` entry -- it does not fit the generic page-routing
     * shape `SettingsEditorSheet` exists to share, so wrapping a plain confirm/cancel body in that
     * machinery would add indirection without removing any duplicated chrome. The class-level kdoc's
     * "two/three sheets" invariant paragraph already proves this can never coincide with the routed
     * sheet or the screen's own `editor` stack. The expected count is therefore per-file, not a
     * blanket zero -- and still fails the moment a SECOND raw call sneaks into any of the three files.
     */
    @Test fun noSettingsScreenConstructsItsOwnModalBottomSheet() {
        mapOf(
            "../sharedUi/src/commonMain/kotlin/net/bible/sharedui/settings/AbSettingsScreen.kt" to 0,
            "../sharedUi/src/commonMain/kotlin/net/bible/sharedui/settings/TextDisplaySettingsScreen.kt" to 0,
            "../sharedUi/src/commonMain/kotlin/net/bible/sharedui/ai/AiConnectionSettingsScreen.kt" to 1,
        ).forEach { (path, expected) ->
            val src = strippedSource(path)
            assertEquals(
                "$path must reach the sheet through SettingsEditorSheet, not construct its own " +
                    "(the sole exception is AiConnectionSettingsScreen's own RetentionSheet, 17f-A9)",
                expected, Regex("""\bModalBottomSheet\s*\(""").findAll(src).count(),
            )
        }
    }

    /**
     * Three of the four editor kinds `AbSettingsContent` used to open as dialogs must not regress
     * to an `AlertDialog` here. `AbColorPickerDialog` is deliberately NOT asserted in this file:
     * it is never reachable from `AbSettingsScreen.kt` at all -- Colours goes through
     * `ColorSettingsEditorSheet`/`ColorSettingsScreen` instead, and the only two real call sites
     * are `ColorSettingsScreen.kt` (the full-screen colours route, which legitimately keeps its own
     * colour-picker dialogs) and `LabelIdentitySheet.kt`. Asserting its absence here would not be
     * merely useless, it would be WRONG: this file was never one of its callers, so the assertion
     * would protect nothing while implying a regression risk that doesn't exist at this call site.
     * (T13 fix round 1, Finding 4.)
     */
    @Test fun theSettingsPathHoldsNoEditorAlertDialogs() {
        val src = strippedSource("../sharedUi/src/commonMain/kotlin/net/bible/sharedui/settings/AbSettingsScreen.kt")
        listOf("AbListChoiceDialog", "AbTextInputDialog", "AbMultiSelectDialog")
            .forEach { dialog ->
                assertFalse("$dialog must be a sheet page here, not a dialog", src.contains("$dialog("))
            }
    }

    /**
     * C1: a golden that forces a sheet open hangs the whole `:app` suite. Covers all THREE sheet
     * composables this branch built, not just [net.bible.sharedui.settings.SettingsEditorSheet]
     * itself -- [net.bible.sharedui.settings.ColorSettingsEditorSheet] and
     * [net.bible.sharedui.settings.TextSettingRowEditorSheet] each open their own `ModalBottomSheet`
     * internally (both by calling `SettingsEditorSheet`), so a golden that opened either of those two
     * would hang Roborazzi exactly as one opening `SettingsEditorSheet` directly would. This guard
     * would fail the moment any golden test file's source contained a call to any of the three.
     *
     * **Widened (final fix wave, Fix 2):** this used to walk only
     * `src/test/java/net/bible/android/view/compose/golden`, but golden tests are not confined to
     * that one package -- `net.bible.android.view.activity.settings.TextDisplaySettingsGoldenTest`
     * and `AppSettingsGoldenTest` (this very package, both `GoldenHarness`/`captureMatrix` users)
     * are golden tests too, and `TextDisplaySettingsGoldenTest` is the likeliest future offender:
     * it is exactly where `NumericSliderContent`/`MarginContent` get captured, one edit away from
     * someone capturing `TextSettingRowEditorSheet` itself. A violation there would hang the whole
     * `:app` suite while this guard, scoped to the other package, said nothing. Walking the whole
     * `src/test/java` module tree (this test's working directory is the `:app` module root) closes
     * that gap without having to enumerate every golden-test package by name.
     */
    @Test fun noGoldenTestCapturesSettingsEditorSheet() {
        val testSourceRoot = File("src/test/java")
        val sheetComposables = listOf(
            "SettingsEditorSheet(", "ColorSettingsEditorSheet(", "TextSettingRowEditorSheet(", "SpeakSettingsSheet(",
            // Round 14a's ten dialog->sheet conversions (spec §3 group 2). Each name is the WRAPPER;
            // its `…Content` sibling is what goldens capture and is deliberately NOT listed, since
            // e.g. "AbChoiceSheetContent(" does not contain the literal "AbChoiceSheet(".
            "AbChoiceSheet(", "AbMultiSelectSheet(", "AbActionSheet(",
            "PromptSelectorSheet(", "ModelSelectionSheet(", "AbReadHistorySheet(",
            // Round 15b's shared quick-sheet shell (whole-branch review finding M3). Same rule:
            // "AbQuickSheetContent(" does not contain the literal "AbQuickSheet(", so the …Content
            // captures throughout AbQuickSheetGoldenTest/WorkspaceQuickGoldenTest/HistoryGoldenTest
            // do not false-positive here.
            "AbQuickSheet(",
            // Round 17b's labels search-options sheet. Same rule: goldens capture
            // "ManageLabelsSearchOptionsSheetContent(", which does not contain this literal.
            "ManageLabelsSearchOptionsSheet(",
            // Final-review fix I2: two sheets the guard never picked up. "RetentionSheet(" (from
            // AiConnectionSettingsScreen.kt) predates this diff -- an earlier round's gap closed here
            // rather than in a separate edit. "PromptFilterSheet(" is this plan's own new sheet.
            "RetentionSheet(", "PromptFilterSheet(",
        )
        val offenders = testSourceRoot.walkTopDown().filter { it.extension == "kt" }
            // This guard's own file is excluded: widening the walk to the whole test tree means it
            // now sees its own source, and `sheetComposables` above is a list of STRING LITERALS
            // that are themselves exact substrings of "...Sheet(" -- so without this exclusion the
            // guard would always report itself as an offender, self-defeating the whole check.
            .filterNot { it.name == "SettingsEditorSheetGuardTest.kt" }
            .filter { file -> sheetComposables.any { file.readText().contains(it) } }
            .map { it.name }.toList()
        assertEquals(
            "Capture the page's *Content composable in a plain Column instead -- an open " +
                "ModalBottomSheet hangs Roborazzi and takes the whole suite with it",
            emptyList<String>(), offenders,
        )
    }

    /**
     * The INDIRECT form of the same hazard, which [noGoldenTestCapturesSettingsEditorSheet]'s
     * name list structurally cannot see. Round 14a converted TWO of `ReadingLlmDialogs`' four arms
     * -- the prompt selector and the model chooser -- from `AlertDialog` to `ModalBottomSheet`. A
     * golden that captures `ReadingLlmDialogs(...)` with one of those arms as its state opens a real
     * sheet without the literal string "PromptSelectorSheet(" ever appearing in the test source, so
     * it would sail past `sheetComposables` and hang the whole `:app` suite. Forbidding
     * `ReadingLlmDialogs(` outright is NOT an option: its other two arms (`SpecifyBeforeRun`,
     * `Regenerate`) are still dialogs and are legitimately captured through the dispatcher.
     *
     * **Widened (final-review fix wave, I1):** the same *screen + state* shape opens a sheet
     * indirectly in FOUR more places this round converted, none of which mention a wrapper name
     * either. [sheetArms] now also matches the literal a test would actually write to reach each:
     * - `SyncNowDialogState(` -- constructing this data class (`CloudDocumentsController.kt:10`) is
     *   how `CloudDocumentsScreen(syncNowDialog = ...)` opens the sync-now `AbChoiceSheet`. A bare
     *   type reference like `syncNowDialog: SyncNowDialogState? = null` does NOT match (no trailing
     *   `(`), so a parameter declaration alone doesn't false-positive the guard -- only an actual
     *   construction does, exactly as `ReadingLlmDialog.PromptSelector(` above only matches a call.
     * - `Step.PICK_TYPE` -- `AiProvidersScreen`'s `editState = ProviderEditState(step =
     *   ProviderEditState.Step.PICK_TYPE, ...)` opens its type-picker `AbChoiceSheet`
     *   (`AiProvidersScreen.kt:153`). Existing goldens only ever use `Step.FORM`
     *   (`AiProvidersGoldenTest.kt:56/71`), so this is untested territory today, same as the other
     *   three.
     * - `Step.PICK_PROVIDER` -- `AiModelsScreen`'s `editState = ModelEditState(step =
     *   ModelEditState.Step.PICK_PROVIDER, ...)` opens its provider-picker `AbChoiceSheet`
     *   (`AiModelsController.kt:46` declares the enum; existing goldens use `PICK_MODEL`).
     * - `EasySetupStep.PICK` -- `EasySetupWizard`'s `state = EasySetupState(step =
     *   EasySetupStep.PICK, ...)` opens its setup-picker `AbChoiceSheet`
     *   (`EasySetupWizard.kt:47/111`; existing goldens use `ENTER_KEY`).
     *
     * Each literal is a SUBSTRING of how the real call site spells it (verified by reading
     * `CloudDocumentsController.kt`, `AiProvidersController.kt`, `AiModelsController.kt` and
     * `EasySetupWizard.kt` directly, not assumed), so a golden test that constructs the sheet-arm
     * state -- however it names its local variable -- still contains the matched text, the same
     * "match the state literal, not the wrapper name" idiom the two `ReadingLlmDialog.*` entries
     * already use. `AiPromptsScreen`'s fifth candidate from the same table (T5's
     * `initiallyMoveToCategoryPromptId`) needs no entry here: T5 deleted that seam outright rather
     * than leaving a state shape that could re-open it.
     *
     * Scoped to files that actually CAPTURE, not to the whole test tree, because
     * `net.bible.android.view.compose.ReadingLlmHostTest` legitimately builds a
     * `PromptSelector` state: it is a mount probe whose container is never attached to a window, so
     * composition never runs and no sheet is ever opened. Keying on the harness's own capture
     * helpers is what tells the two apart.
     */
    @Test fun noGoldenTestCapturesTheLlmSheetArmsThroughTheDispatcher() {
        val captureHelpers = listOf("captureMatrix(", "captureGolden(", "captureRtl(", "captureRoboImage(")
        val sheetArms = listOf(
            "ReadingLlmDialog.PromptSelector(", "ReadingLlmDialog.ModelSelection(",
            "SyncNowDialogState(", "Step.PICK_TYPE", "Step.PICK_PROVIDER", "EasySetupStep.PICK",
        )
        val offenders = File("src/test/java").walkTopDown().filter { it.extension == "kt" }
            .filterNot { it.name == "SettingsEditorSheetGuardTest.kt" }
            .map { it to it.readText() }
            .filter { (_, text) -> captureHelpers.any { text.contains(it) } }
            .filter { (_, text) -> sheetArms.any { text.contains(it) } }
            .map { (file, _) -> file.name }.toList()
        assertEquals(
            "This state opens a ModalBottomSheet indirectly (no wrapper name in sight) -- capture " +
                "the sheet's own …Content composable in a plain Surface instead, or a non-sheet step",
            emptyList<String>(), offenders,
        )
    }

    /**
     * The Speak sheet analogue of [noSettingsScreenConstructsItsOwnModalBottomSheet]: every Speak
     * page body must reach the sheet through `SpeakSettingsSheet`, never build its own
     * `ModalBottomSheet`. Paths are relative to this test's working directory, the `:app` module
     * root, same convention as the two settings-screen paths above.
     */
    @Test fun noSpeakPageBodyConstructsItsOwnModalBottomSheet() {
        val bodies = listOf(
            "../sharedUi/src/commonMain/kotlin/net/bible/sharedui/speak/BibleSpeakScreen.kt",
            "../sharedUi/src/commonMain/kotlin/net/bible/sharedui/speak/AdvancedSpeakSettingsScreen.kt",
            "../sharedUi/src/commonMain/kotlin/net/bible/sharedui/speak/SpeakRangeContent.kt",
            "../sharedUi/src/commonMain/kotlin/net/bible/sharedui/speak/SleepTimerContent.kt",
        )
        bodies.forEach { path ->
            val src = strippedSource(path)
            assertEquals(
                "$path must reach the sheet through SpeakSettingsSheet, not build its own",
                0, Regex("""\bModalBottomSheet\s*\(""").findAll(src).count(),
            )
        }
    }

    /**
     * Round 15b's analogue of [noSpeakPageBodyConstructsItsOwnModalBottomSheet]: every quick-sheet
     * BODY must reach the sheet through `AbQuickSheet`, never build its own `ModalBottomSheet`.
     * `AbQuickSheet.kt` itself is deliberately NOT in this list -- it is the shell, exactly as
     * `SpeakSettingsSheet.kt`/`SettingsEditorSheet.kt` are for their families, and legitimately
     * constructs the one `ModalBottomSheet` its bodies share.
     *
     * Spec §5.2 rule 2 says this guard "will be extended to the new one" (whole-branch review
     * finding M3).
     */
    @Test fun noQuickSheetBodyConstructsItsOwnModalBottomSheet() {
        val bodies = listOf(
            "../sharedUi/src/commonMain/kotlin/net/bible/sharedui/workspaces/WorkspaceQuickContent.kt",
            "../sharedUi/src/commonMain/kotlin/net/bible/sharedui/history/HistoryScreen.kt",
        )
        bodies.forEach { path ->
            val src = strippedSource(path)
            assertEquals(
                "$path must reach the sheet through AbQuickSheet, not build its own",
                0, Regex("""\bModalBottomSheet\s*\(""").findAll(src).count(),
            )
        }
    }
}
