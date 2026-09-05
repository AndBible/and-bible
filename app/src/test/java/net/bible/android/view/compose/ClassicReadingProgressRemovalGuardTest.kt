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
 * Batch Z-late phase 1, slice S5: the classic Reading progress screen was deleted, with its own
 * `CalendarHeatmapView`, its layout and its menu, and `ScreenLauncher`'s arm collapsed to the
 * Compose implementation.
 *
 * Five assertions rather than S8's four, because this slice is the one in its batch with
 * collaborators worth defending: `ReadingProgressColors`, `ReadingProgressKeys` and
 * `ReadHistoryDialog` sit in the SAME package as the deleted screen and are named after the same
 * feature, so the obvious tidy-up is to take the package with the screen. All three have surviving
 * consumers — the Compose twin, and `BibleJavascriptInterface`, whose calls have no compile-time
 * signal from here — so [theSurvivingProgressCollaboratorsStillExist] pins them.
 *
 * `CalendarHeatmapView` is deliberately NOT in that list: it became referenceless with the layout
 * that instantiated it, spec §2.4 protects named base classes and `view/util/widget/` wholesale
 * rather than a single screen's own custom View, and so it was deleted with the screen. The five
 * remaining mentions of it in the tree are KDoc prose, including two in `:sharedUi` — tail-sweep
 * work per §2.3.
 */
class ClassicReadingProgressRemovalGuardTest {
    private val doomedClassNames = listOf(
        "net.bible.android.view.activity.progress.ReadingProgressActivity",
        "net.bible.android.view.activity.progress.CalendarHeatmapView",
    )

    private val doomedPaths = listOf(
        "src/main/java/net/bible/android/view/activity/progress/ReadingProgressActivity.kt",
        "src/main/java/net/bible/android/view/activity/progress/CalendarHeatmapView.kt",
        "src/main/res/layout/reading_progress.xml",
        "src/main/res/menu/reading_progress_menu.xml",
        "src/test/java/net/bible/android/view/compose/HeatGridColumnsTest.kt",
    )

    @Test fun theClassicReadingProgressFilesAndResourcesAreGone() {
        ClassicRemovalScan.assertPathsGone(
            doomedPaths,
            "these classic Reading progress files should have been deleted in S5. " +
                "HeatGridColumnsTest is in the list because it read reading_progress.xml off disk " +
                "to parity-check the Compose grid constants; with the classic layout gone it has " +
                "no counterparty and would throw FileNotFoundException.",
        )
    }

    @Test fun theSurvivingProgressCollaboratorsStillExist() {
        ClassicRemovalScan.assertPathsPresent(
            listOf(
                "src/main/java/net/bible/android/view/activity/progress/ReadingProgressColors.kt",
                "src/main/java/net/bible/android/view/activity/progress/ReadingProgressKeys.kt",
                "src/main/java/net/bible/android/view/activity/progress/ReadHistoryDialog.kt",
            ),
            "S5 deleted a file it was supposed to keep. All three sit in the deleted screen's own " +
                "package and are named after the same feature, but each has a surviving consumer: " +
                "the Compose twin, and BibleJavascriptInterface, which calls " +
                "ReadHistoryDialog.showForChapter and reads ReadingProgressKeys.EXTRA_TAB from the " +
                "WebView — a path with no compile-time signal from this module.",
        )
    }

    @Test fun noSourceFileNamesAClassicReadingProgressClass() {
        ClassicRemovalScan.assertNoSourceNames(
            doomedClassNames,
            "these files still name a classic Reading progress class deleted in S5",
        )
    }

    @Test fun noManifestEntryNamesAClassicReadingProgressClass() {
        ClassicRemovalScan.assertNoManifestNames(
            doomedClassNames,
            "a manifest still names a class S5 deletes. Watch parentActivityName specifically: " +
                "TWO surviving blocks pointed their Up target at classic ReadingProgressActivity " +
                "before this batch — the classic ReadingProgressSettingsActivity (since deleted " +
                "in Z-late slice S12, covered by ClassicSettingsRemovalGuardTest) and the " +
                "permanent Compose ReadingProgressSettingsComposeActivity. Nothing compiles " +
                "against that attribute.",
        )
    }

    @Test fun screenLauncherDoesNotBranchForReadingProgress() {
        ClassicRemovalScan.assertLauncherArmsUnconditional(
            listOf("Screen.ReadingProgress"),
            "the Reading progress arm still branches on the flag (or is missing entirely) — S5 " +
                "collapses it to the Compose class unconditionally. Note this is NOT " +
                "Screen.ReadingProgressSettings: that arm belonged to S12, which has since " +
                "collapsed it too (ClassicSettingsRemovalGuardTest.theSettingsArmsResolveUnconditionally) " +
                "— it does not branch either any more, but this test does not cover it.",
        )
    }
}
