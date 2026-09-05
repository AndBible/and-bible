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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Locks Batch Z-late's epilogue (spec 10.2-10.4): the reading view's classic branches, the
 * classic bottom chrome and everything the collapse orphans are gone, and stay gone.
 *
 * Deliberately source-walking rather than behavioural, following the house pattern
 * (SettingsEditorSheetGuardTest, QuickSheetMountGuardTest, SpeakEntryPointGuardTest): the
 * decision being made permanent here is structural, and no runtime assertion can observe
 * "this branch no longer exists".
 */
class ClassicReadingViewRemovalGuardTest {
    private val mainBibleActivity =
        "src/main/java/net/bible/android/view/activity/page/MainBibleActivity.kt"

    @Test
    fun theReadingViewReadsTheFlagNowhere() {
        val code = ClassicRemovalScan.codeLinesOf(mainBibleActivity)
        assertEquals(
            "MainBibleActivity must not read use_compose_ui at all -- the flag's definition is " +
                "removed in Task 7 and a surviving read would not compile then.",
            0,
            Regex("""getBoolean\("use_compose_ui"""").findAll(code).count(),
        )
    }

    /**
     * Fix round 1, and the reason it exists: [theReadingViewReadsTheFlagNowhere] counts flag reads
     * and nothing else, so it is satisfied IDENTICALLY by a collapse to the Compose branch and a
     * collapse to the classic one. Delete the flag read but keep the toolbar tinting and
     * `statusBarColor = toolbarColor`, and the count is still 0. Nothing else in the tree closes
     * that gap: the Roborazzi goldens exercise Compose composables, not this activity's
     * system-bar code, and the compile break from the classic views' own removal does not arrive
     * until the slice that deletes them.
     *
     * These three names are the tell, because each appears only on the classic side of the branches
     * this epilogue collapsed. `toolbarColor` was the classic toolbar's colour source (deleted with
     * its last reader); `toolbarLayout` is the view every classic branch mutated -- background,
     * height, padding, visibility, slide animation; `toolbarDivider` is the monochrome-only rule
     * from inside the deleted tint block. All three are exactly zero in the file's CODE lines as of
     * this commit and each would be non-zero under a wrong-direction collapse.
     *
     * Deliberately scoped to code lines: `toolbarLayout` still appears seven times in this file's
     * PROSE ("the now-GONE `toolbarLayout`"), which [ClassicRemovalScan.codeLinesOf] strips. Those
     * comments are accurate and must stay.
     */
    @Test
    fun theReadingViewNamesNoClassicToolbarInCode() {
        val code = ClassicRemovalScan.codeLinesOf(mainBibleActivity)
        assertTrue(
            "the scan read no MainBibleActivity code at all -- this assertion would pass vacuously",
            code.contains("class MainBibleActivity"),
        )
        assertEquals(
            "MainBibleActivity must not name the classic toolbar in code. A use_compose_ui read " +
                "count of 0 on its own cannot tell a collapse to the Compose branch from a " +
                "collapse to the CLASSIC one -- these names can only reappear on the classic side.",
            emptyList<String>(),
            listOf("toolbarLayout", "toolbarColor", "toolbarDivider").filter { code.contains(it) },
        )
    }

    private val splitBibleArea =
        "src/main/java/net/bible/android/view/activity/page/screen/SplitBibleArea.kt"

    @Test
    fun theRestoreButtonsEventOutlivesItsClassicHome() {
        // RestoreButtonsVisibilityChanged is posted by WindowRepository.notifyRestoreButtonsChanged
        // and consumed by BibleView -- both on the Compose path. It must NOT die with SplitBibleArea.
        ClassicRemovalScan.assertPathsPresent(
            listOf("src/main/java/net/bible/android/view/activity/page/screen/RestoreButtonsEvents.kt"),
            "the live Compose-path event must have been split out before SplitBibleArea is deleted",
        )
        val classic = ClassicRemovalScan.codeLinesOf(splitBibleArea)
        assertTrue(
            "the scan read no SplitBibleArea code at all -- this assertion would pass vacuously",
            classic.contains("class SplitBibleArea"),
        )
        assertEquals(
            "SplitBibleArea.kt must no longer DECLARE RestoreButtonsVisibilityChanged",
            0,
            Regex("""class\s+RestoreButtonsVisibilityChanged""").findAll(classic).count(),
        )
    }

    /**
     * The second live tenant of `SplitBibleArea.kt`, found while proving the claim above rather
     * than assuming it. `var clipboardKey` is a TOP-LEVEL property in that file, and three
     * survivors import it by its fully-qualified name -- `MainBibleActivity`, `BibleView` and
     * `WindowPaneMenuStateBuilder` (the Compose pane menu's "go to copied reference" row reads the
     * very same shared state classic wrote). It has to be split out for exactly the same reason the
     * event does, so it is guarded the same way and for the same reason: nothing else in the tree
     * would notice its loss until the compile break in the slice that deletes its host.
     *
     * Both halves are load-bearing. The presence check alone would pass with the declaration
     * duplicated in two files (a redeclaration error, but only once someone compiles); the
     * declaration count alone would pass with the property simply deleted.
     */
    @Test
    fun theClipboardKeyOutlivesItsClassicHome() {
        ClassicRemovalScan.assertPathsPresent(
            listOf("src/main/java/net/bible/android/view/activity/page/screen/ClipboardKey.kt"),
            "the shared clipboardKey must have been split out before SplitBibleArea is deleted",
        )
        val classic = ClassicRemovalScan.codeLinesOf(splitBibleArea)
        assertTrue(
            "the scan read no SplitBibleArea code at all -- this assertion would pass vacuously",
            classic.contains("class SplitBibleArea"),
        )
        assertEquals(
            "SplitBibleArea.kt must no longer DECLARE clipboardKey",
            0,
            Regex("""^var\s+clipboardKey""", RegexOption.MULTILINE).findAll(classic).count(),
        )
    }
}
