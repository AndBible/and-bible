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

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Round 14b §7.a / spec D4: every `ModalBottomSheet` in the port opens at its content height.
 *
 * The device complaint this enforces: with `skipPartiallyExpanded = false` a sheet opens partially
 * expanded and a bounded scroll region inside it demands TWO different gestures — drag the sheet,
 * then scroll the content — while signalling neither, so a user who has dragged the sheet to its
 * maximum reasonably concludes they are seeing everything. Two of the seven existing sheets already
 * passed `true`, so this finishes an existing practice; what makes it worth a guard is that the
 * default is `false`, so the regression is a DELETION and there is nothing else to notice it.
 *
 * Deliberately a WALK of `:sharedUi`, not a list of file names. Round 14a's sibling container is
 * adding three brand-new sheet wrappers (`AbChoiceSheet`, `AbMultiSelectSheet`, `AbActionSheet`)
 * which spec §4 says take `skipPartiallyExpanded = true` — a walk covers them the moment the two
 * branches merge, where a list would have said nothing.
 *
 * The rule asserted is the shape every sheet in this repo actually has: each `ModalBottomSheet(`
 * call site creates its OWN state in the same file, with `skipPartiallyExpanded = true`, AND passes
 * that state to the sheet via a `sheetState = ` argument. A wrapper that took a `SheetState` as a
 * parameter instead would fail the count check — that is intentional. It means "explain yourself
 * here", not "you are wrong": add the file to the exclusions WITH the reason.
 *
 * Paths are relative to the `:app` module dir, this test's working directory.
 *
 * **Scope note, current as of round 14b's fix wave:** the walk covers `sharedUi/src/commonMain/kotlin`
 * only, because that is where every `ModalBottomSheet(` call site in this repo lives today (a repo-wide
 * grep for `ModalBottomSheet(` outside that directory turns up nothing but this guard's own source and
 * `SettingsEditorSheetGuardTest`). A future sheet added directly under `:app` or an `androidMain`
 * source set would NOT be covered by this walk and would escape silently — if that ever happens, widen
 * the walk rather than trusting this comment.
 */
class SheetExpansionGuardTest {
    private val sharedUiCommon = File("../sharedUi/src/commonMain/kotlin")

    /** No exclusions today. Kept as an explicit empty list so a future one has an obvious home. */
    private val excluded = emptySet<String>()

    private fun stripComments(text: String): String {
        val noBlockComments = Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL).replace(text, "")
        return noBlockComments.lines().joinToString("\n") { line ->
            val commentAt = line.indexOf("//")
            if (commentAt >= 0) line.substring(0, commentAt) else line
        }
    }

    private fun sheetFiles(): List<File> = sharedUiCommon.walkTopDown()
        .filter { it.isFile && it.extension == "kt" && it.name !in excluded }
        .filter { Regex("""\bModalBottomSheet\s*\(""").containsMatchIn(stripComments(it.readText())) }
        .toList()

    /**
     * Returns the (unparenthesized) argument text of every call to [functionName] in [src], found by
     * walking parens from each call's opening `(` to its BALANCED closing `)` — not a bounded regex
     * like `[^)]*`, which breaks the moment an argument contains its own parens (a lambda default, a
     * nested call). This is what lets [everySheetCallSiteHasItsOwnSkipPartiallyExpandedState] tolerate
     * a trailing comma or a second argument such as `confirmValueChange = { ... }` without a spurious
     * failure, and what lets [everySheetCallSitePassesASheetStateArgument] look inside the actual
     * `ModalBottomSheet(...)` argument list rather than the whole file.
     */
    private fun callArgLists(src: String, functionName: String): List<String> {
        val args = mutableListOf<String>()
        for (m in Regex("""\b$functionName\s*\(""").findAll(src)) {
            val open = m.range.last
            var depth = 0
            var i = open
            while (i < src.length) {
                when (src[i]) {
                    '(' -> depth++
                    ')' -> {
                        depth--
                        if (depth == 0) {
                            args.add(src.substring(open + 1, i))
                            break
                        }
                    }
                }
                i++
            }
        }
        return args
    }

    @Test fun theWalkFindsTheSheetsItIsSupposedToPolice() {
        val names = sheetFiles().map { it.name }.sorted()
        assertTrue(
            "the :sharedUi walk found $names — expected at least the seven known sheet files, so " +
                "this guard is not scanning source at all",
            names.size >= 7,
        )
        listOf(
            "AbCreateItemSheet.kt", "AbSearchablePicker.kt", "AbSettingsSummarySheet.kt",
            "LabelIdentitySheet.kt", "SearchSettingsSheet.kt", "SettingsEditorSheet.kt",
            "SpeakSettingsSheet.kt",
        ).forEach { assertTrue("$it is no longer being scanned", it in names) }
    }

    @Test fun noSheetUsesTheDefaultPartiallyExpandedState() {
        val offenders = sheetFiles()
            .filter { Regex("""rememberModalBottomSheetState\s*\(\s*\)""")
                .containsMatchIn(stripComments(it.readText())) }
            .map { it.name }.sorted()
        assertEquals(
            "these files create a bare rememberModalBottomSheetState() — pass " +
                "skipPartiallyExpanded = true so the sheet opens at content height (round 14b §7.a)",
            emptyList<String>(), offenders,
        )
    }

    @Test fun everySheetCallSiteHasItsOwnSkipPartiallyExpandedState() {
        val offenders = sheetFiles().mapNotNull { file ->
            val src = stripComments(file.readText())
            val sheets = Regex("""\bModalBottomSheet\s*\(""").findAll(src).count()
            // Tolerant on purpose: `skipPartiallyExpanded = true` may be followed by a trailing comma
            // (a formatter's doing) or by a second, legitimate argument such as
            // `confirmValueChange = { ... }` — neither changes the property this test cares about, and
            // the old exact-argument-list regex failed both with a message that did not explain why.
            val states = callArgLists(src, "rememberModalBottomSheetState")
                .count { Regex("""\bskipPartiallyExpanded\s*=\s*true\b""").containsMatchIn(it) }
            if (sheets == states) null else "${file.name} ($sheets sheets, $states compliant states)"
        }.sorted()
        assertEquals(
            "every ModalBottomSheet call site must be given a state created with " +
                "skipPartiallyExpanded = true, in the same file (round 14b §7.a / spec D4)",
            emptyList<String>(), offenders,
        )
    }

    /**
     * The count-equality check above proves a compliant state EXISTS in the file; it does not prove
     * the sheet actually RECEIVES it. Delete `sheetState = sheetState` from a `ModalBottomSheet(...)`
     * call and the counts stay equal (one sheet, one compliant-but-now-unused state) while the sheet
     * silently reverts to Material3's own partially-expanded default — which is the exact pre-round
     * shape of `AbSearchableOptionSheet`, so this is a live failure mode, not a hypothetical one.
     */
    @Test fun everySheetCallSitePassesASheetStateArgument() {
        val offenders = sheetFiles().mapNotNull { file ->
            val src = stripComments(file.readText())
            val calls = callArgLists(src, "ModalBottomSheet")
            val missing = calls.count { !Regex("""\bsheetState\s*=""").containsMatchIn(it) }
            if (missing == 0) null else "${file.name} ($missing of ${calls.size} ModalBottomSheet call(s))"
        }.sorted()
        assertEquals(
            "every ModalBottomSheet( call site must pass sheetState = <state> explicitly — without " +
                "it, Material3's own default (skipPartiallyExpanded = false) applies even though a " +
                "compliant state exists elsewhere in the file, unused (round 14b §7.a / spec D4)",
            emptyList<String>(), offenders,
        )
    }
}
