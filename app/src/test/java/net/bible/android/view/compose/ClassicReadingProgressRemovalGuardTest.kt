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
 * collaborators worth defending: `ReadingProgressColors` and `ReadingProgressKeys` sit in the SAME
 * package as the deleted screen and are named after the same feature, so the obvious tidy-up is to
 * take the package with the screen. [theSurvivingProgressCollaboratorsStillExist] pins both.
 *
 * A third collaborator, `ReadHistoryDialog`, was pinned here for the same reason from S5 through
 * platform-dialog removal Task 26 — `BibleJavascriptInterface.openChapterReadHistory` called its
 * `showForChapter`, a call with no compile-time signal from this module, and that call also read
 * `ReadingProgressColors.COLOR_HEAT_MAX`. Platform-dialog removal Task 27 (run 3) deleted
 * `ReadHistoryDialog.kt` outright: `openChapterReadHistory` now opens `ReadingQuickSheet
 * .ReadHistory` instead, so the file's only reason to survive S5's package-wide sweep is gone. It
 * is no longer in [theSurvivingProgressCollaboratorsStillExist]'s list; see
 * `PlatformDialogRemovalGuardTest.BASELINE`'s Task 27 entry for the deletion itself.
 *
 * That deletion leaves `ReadingProgressColors` with no production consumer left in `app/` —
 * `ReadingProgressColorsTest` is now its only reader. Left pinned rather than deleted alongside
 * `ReadHistoryDialog`: Task 27's brief named only `ReadHistoryDialog.kt`, and `ReadingProgressColors`
 * is a general color/scale utility (`countToHeatColor`, `countBookProgressToColor`,
 * `memorizationProgressToColor`, `textColorForBackground`) rather than a single screen's leftover, so
 * whether it should follow `CalendarHeatmapView` into deletion or stay as a still-tested utility is
 * an epilogue question for the maintainer, the same way `ReadingProgressKeys`' own note below treats
 * its EXTRA_TAB.
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
            ),
            "S5 deleted a file it was supposed to keep. Both sit in the deleted screen's own " +
                "package and are named after the same feature. A third, ReadHistoryDialog, was " +
                "pinned here too from S5 through platform-dialog removal Task 26 (called from " +
                "BibleJavascriptInterface.kt, off the WebView — a path with no compile-time " +
                "signal from this module) but platform-dialog removal Task 27 (run 3) deleted it " +
                "outright, so it is no longer in this list — see this file's class kdoc. " +
                "ReadingProgressKeys' EXTRA_TAB lost BOTH readers in this migration (the screen " +
                "moved into the nav graph, where the tab travels in the route), leaving " +
                "IntentKeysTest:43 as its only consumer — kept deliberately as an epilogue " +
                "question for the maintainer, see that file's own kdoc. Task 27 leaves " +
                "ReadingProgressColors in the same position (ReadingProgressColorsTest as its " +
                "only consumer) — see this file's class kdoc.",
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
