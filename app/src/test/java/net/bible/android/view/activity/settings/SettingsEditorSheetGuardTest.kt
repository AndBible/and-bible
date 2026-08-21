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
     */
    @Test fun noSettingsScreenConstructsItsOwnModalBottomSheet() {
        listOf(
            "../sharedUi/src/commonMain/kotlin/net/bible/sharedui/settings/AbSettingsScreen.kt",
            "../sharedUi/src/commonMain/kotlin/net/bible/sharedui/settings/TextDisplaySettingsScreen.kt",
            "../sharedUi/src/commonMain/kotlin/net/bible/sharedui/ai/AiConnectionSettingsScreen.kt",
        ).forEach { path ->
            val src = strippedSource(path)
            assertEquals(
                "$path must reach the sheet through SettingsEditorSheet, not construct its own",
                0, Regex("""\bModalBottomSheet\s*\(""").findAll(src).count(),
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
        val sheetComposables = listOf("SettingsEditorSheet(", "ColorSettingsEditorSheet(", "TextSettingRowEditorSheet(")
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
}
