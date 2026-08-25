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
 * call site creates its OWN state in the same file, with `skipPartiallyExpanded = true`. A wrapper
 * that took a `SheetState` as a parameter instead would fail the count check — that is intentional.
 * It means "explain yourself here", not "you are wrong": add the file to the exclusions WITH the
 * reason, the same discipline `SpeakEntryPointGuardTest.excludedClassicLaunchers` uses.
 *
 * Paths are relative to the `:app` module dir, this test's working directory.
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
            val states = Regex("""rememberModalBottomSheetState\s*\(\s*skipPartiallyExpanded\s*=\s*true\s*\)""")
                .findAll(src).count()
            if (sheets == states) null else "${file.name} ($sheets sheets, $states compliant states)"
        }.sorted()
        assertEquals(
            "every ModalBottomSheet call site must be given a state created with " +
                "skipPartiallyExpanded = true, in the same file (round 14b §7.a / spec D4)",
            emptyList<String>(), offenders,
        )
    }
}
