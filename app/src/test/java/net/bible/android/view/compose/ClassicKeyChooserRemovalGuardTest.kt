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
 * Batch Z-late phase 1, slice S3: the three classic key-chooser screens were deleted and their
 * `ScreenLauncher` arms collapsed to the Compose implementations. This guard makes the deletion
 * durable — a regression that re-adds a file, an import, a manifest entry or a flag branch would
 * otherwise only be noticed if something else broke.
 *
 * It also does something the S1 and S2 guards did not have to: it asserts that three files which
 * become REFERENCELESS in this slice are still present. `ChooseGeneralBookKey` and `ChooseMapKey`
 * are the only two subclasses of `ChooseKeyBase`, so once they go, the base, its adapter and its
 * layout have no consumer at all — and spec §2.4 keeps them anyway, as a tail-sweep item, together
 * with `ListActivityBase` and `DocumentSelectionBase`. Deleting them would compile, would pass the
 * `grep -a` reference proof and would pass every gate; only the written decision defends them, and
 * only this test makes that decision enforceable.
 *
 * Source scan rather than a reflective "class not found", matching [ClassicSearchRemovalGuardTest]
 * and [ClassicReadingPlanRemovalGuardTest] and sharing their machinery via [ClassicRemovalScan].
 * Paths are relative to the `:app` module dir, which is the working directory for its unit tests.
 *
 * The phase-wide guard spec §6 describes (iterate `Screen.entries` and assert every arm resolves
 * into the Compose set) cannot be true until the last slice lands, so it is deliberately not
 * attempted here.
 */
class ClassicKeyChooserRemovalGuardTest {
    /** Fully-qualified names of the three screens S3 deletes. `ChooseKeyBase` is NOT one of them. */
    private val doomedClassNames = listOf(
        "net.bible.android.view.activity.navigation.genbookmap.ChooseGeneralBookKey",
        "net.bible.android.view.activity.navigation.genbookmap.ChooseMapKey",
        "net.bible.android.view.activity.navigation.ChooseDictionaryWord",
    )

    private val doomedClassRefs = ClassicRemovalScan.refsFor(doomedClassNames)

    private val doomedPaths = listOf(
        "src/main/java/net/bible/android/view/activity/navigation/genbookmap/ChooseGeneralBookKey.kt",
        "src/main/java/net/bible/android/view/activity/navigation/genbookmap/ChooseMapKey.kt",
        "src/main/java/net/bible/android/view/activity/navigation/ChooseDictionaryWord.kt",
        "src/main/res/layout/choose_dictionary_page.xml",
    )

    @Test fun theClassicKeyChooserFilesAndResourcesAreGone() {
        assertTrue(
            "cwd is not the :app module dir — this guard would pass vacuously",
            File("src/main").isDirectory,
        )
        val survivors = doomedPaths.filter { File(it).exists() }
        assertEquals(
            "these classic key-chooser files or resources should have been deleted in S3. Note " +
                "what is NOT in this list: ChooseKeyBase.kt, KeyItemAdapter.kt and " +
                "choose_general_book_key.xml are kept on purpose (spec 2.4) even though nothing " +
                "references them after this slice.",
            emptyList<String>(),
            survivors,
        )
    }

    /**
     * The inverse assertion, and this slice's signature. Every one of these has NO consumer once
     * S3 lands, so an implementer or a later tidy-up could delete any of them and every gate would
     * still pass — a compile is green, the reference proof is green, the goldens do not move.
     * `KeyChooserKeys.kt` is the exception that proves the rule: it sits in the same doomed-looking
     * package and is named after the deleted feature, but it is read by two SURVIVING Compose
     * activities and by the reading view's quick-sheet, so deleting it breaks shipping code
     * immediately rather than silently.
     */
    @Test fun theSurvivingKeyChooserCollaboratorsStillExist() {
        val mustExist = listOf(
            "src/main/java/net/bible/android/view/activity/navigation/genbookmap/ChooseKeyBase.kt",
            "src/main/java/net/bible/android/view/activity/navigation/genbookmap/KeyItemAdapter.kt",
            "src/main/java/net/bible/android/view/activity/navigation/genbookmap/KeyChooserKeys.kt",
            "src/main/res/layout/choose_general_book_key.xml",
        )
        val missing = mustExist.filterNot { File(it).exists() }
        assertEquals(
            "S3 deleted a file it was supposed to keep. ChooseKeyBase, KeyItemAdapter and " +
                "choose_general_book_key.xml become referenceless in this slice and are kept by " +
                "spec 2.4 as a tail-sweep item, exactly like ListActivityBase and " +
                "DocumentSelectionBase; KeyChooserKeys.kt is read by the surviving Compose " +
                "activities and by ComposeReadingViewHost's quick-sheet.",
            emptyList<String>(),
            missing,
        )
    }

    /**
     * The reference proof of spec §3.3, expressed as a test so it survives this session. Delegates
     * to [ClassicRemovalScan.assertNoSourceNames], which walks every shipping source set's
     * Kotlin/Java source AND its resource XML rather than a path list, so a new reference to a
     * deleted class by its FULLY-QUALIFIED name from OUTSIDE the deleted package — whether written
     * in source or named by a layout — cannot escape. That scoping matters: an unqualified
     * same-package reference (a bare `ChooseMapKey` written inside `genbookmap/`) is invisible to
     * this sweep — but that case does not need this test, because the compiler already catches it:
     * the class it would resolve to no longer exists.
     *
     * Matching on the FULLY-QUALIFIED name with a trailing (not leading — see
     * [ClassicRemovalScan.refsFor]) non-identifier boundary is what makes this possible at all:
     * every surviving Compose twin is named after its classic original (`ChooseMapKey` /
     * `ChooseMapKeyComposeActivity`), and imports are KEPT because an import is the reference being
     * hunted — see [ClassicRemovalScan] for why both halves are load-bearing.
     */
    @Test fun noSourceFileNamesAClassicKeyChooser() {
        ClassicRemovalScan.assertNoSourceNames(
            doomedClassNames,
            "these files still name a classic key-chooser class deleted in S3",
        )
    }

    /** No manifest may declare, or point at, a class this slice deletes. */
    @Test fun noManifestEntryNamesAClassicKeyChooser() {
        assertTrue(
            "src/main/AndroidManifest.xml is missing — this guard would pass vacuously",
            File("src/main/AndroidManifest.xml").isFile,
        )
        val offenders = ClassicRemovalScan.manifestPaths
            .filter { File(it).isFile }
            .flatMap { path ->
                File(path).readLines()
                    .filter { line -> doomedClassRefs.any { it.containsMatchIn(line) } }
                    .map { "$path: ${it.trim()}" }
            }
            .sorted()
        assertEquals(
            "a manifest still names a class S3 deletes — either a leftover <activity> block or " +
                "a parentActivityName. Neither is a compile error and neither breaks another " +
                "test; the app would simply reference a class that is gone.",
            emptyList<String>(),
            offenders,
        )
    }

    /** The routing arms must be unconditional now: no `useComposeFor` branch may mention a chooser. */
    @Test fun screenLauncherDoesNotBranchForKeyChoosers() {
        val path = "src/main/java/net/bible/android/view/ScreenLauncher.kt"
        assertTrue("$path is missing — this guard would pass vacuously", File(path).isFile)
        val code = ClassicRemovalScan.codeLinesOf(path)
        val chooserScreens = listOf(
            "Screen.ChooseGeneralBookKey", "Screen.ChooseMapKey", "Screen.ChooseDictionaryWord",
        )
        // Each arm is `Screen.X -> XComposeActivity::class.java`. This is a TOKEN bound, not a
        // true `when`-arm bound: take the text from the arm's `Screen.X ->` up to the NEXT
        // occurrence of the token `Screen.` (or the end of the file), and assert no branch
        // survives inside that span. An arm whose own body happened to mention `Screen.` would be
        // truncated early by this heuristic — every collapsed arm here is a one-liner, so that
        // never happens in practice, but a future arm is not guaranteed to stay that way. The
        // literal " ->" in the search string keeps an arm from matching a longer-named sibling.
        // NOTE, deliberately different from S1's and S2's copies of this test: the branch detector
        // matches `else` as a WHOLE WORD rather than the literal `"else "`, so an `else` written at
        // the end of a line is caught too. S2's final review flagged that gap and asked for it to
        // be fixed before the pattern was copied further; this is that fix.
        val elseWord = Regex("""\belse\b""")
        val offenders = chooserScreens.filter { screen ->
            val start = code.indexOf("$screen ->")
            if (start < 0) return@filter true
            val next = code.indexOf("Screen.", start + screen.length + 3)
            val arm = if (next < 0) code.substring(start) else code.substring(start, next)
            arm.contains("useComposeFor") || elseWord.containsMatchIn(arm)
        }
        assertEquals(
            "these key-chooser arms still branch on the flag (or are missing entirely) — S3 " +
                "collapses them to the Compose class unconditionally",
            emptyList<String>(),
            offenders,
        )
    }
}
