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

import org.junit.Test

/**
 * Batch Z-late phase 1, slice S4: the three classic passage-grid screens were deleted and
 * `ScreenLauncher`'s arm collapsed to the Compose implementation.
 *
 * Four assertions, not five: nothing becomes referenceless here. All three screens extended
 * `CustomTitlebarActivityBase`, which has many other subclasses, and they owned no layout at all —
 * they built their grids programmatically, so only their options menu was theirs to delete.
 *
 * Two of the three deleted classes were never in the `Screen` enum: `GridChoosePassageChapter` and
 * `GridChoosePassageVerse` were reachable only from `GridChoosePassageBook`, whose companion
 * constants they read. That is why [screenLauncherDoesNotBranchForPassageGrid] pins one arm while
 * this guard's doomed list names three classes.
 */
class ClassicPassageGridRemovalGuardTest {
    private val doomedClassNames = listOf(
        "net.bible.android.view.activity.navigation.GridChoosePassageBook",
        "net.bible.android.view.activity.navigation.GridChoosePassageChapter",
        "net.bible.android.view.activity.navigation.GridChoosePassageVerse",
    )

    private val doomedPaths = listOf(
        "src/main/java/net/bible/android/view/activity/navigation/GridChoosePassageBook.kt",
        "src/main/java/net/bible/android/view/activity/navigation/GridChoosePassageChapter.kt",
        "src/main/java/net/bible/android/view/activity/navigation/GridChoosePassageVerse.kt",
        "src/main/res/menu/choose_passage_book_menu.xml",
    )

    @Test fun theClassicPassageGridFilesAndResourcesAreGone() {
        ClassicRemovalScan.assertPathsGone(
            doomedPaths,
            "these classic passage-grid files should have been deleted in S4. Note what is NOT in " +
                "this list: no layout, because all three screens built their grids " +
                "programmatically, and GridChoosePassageComposeActivity, which survives.",
        )
    }

    @Test fun noSourceFileNamesAClassicPassageGridClass() {
        ClassicRemovalScan.assertNoSourceNames(
            doomedClassNames,
            "these files still name a classic passage-grid class deleted in S4. The likeliest " +
                "offender is a raw intent from another classic screen: S5's " +
                "ReadingProgressActivity built one to GridChoosePassageBook, which is why S5 had " +
                "to land first.",
        )
    }

    @Test fun noManifestEntryNamesAClassicPassageGridClass() {
        ClassicRemovalScan.assertNoManifestNames(
            doomedClassNames,
            "a manifest still names a class S4 deletes — either one of the three leftover " +
                "<activity> blocks or a parentActivityName. Neither is a compile error and " +
                "neither breaks another test.",
        )
    }

    @Test fun screenLauncherDoesNotBranchForPassageGrid() {
        ClassicRemovalScan.assertLauncherArmsUnconditional(
            listOf("Screen.GridChoosePassageBook"),
            "the passage-grid arm still branches on the flag (or is missing entirely) — S4 " +
                "collapses it to the Compose class unconditionally",
        )
    }
}
