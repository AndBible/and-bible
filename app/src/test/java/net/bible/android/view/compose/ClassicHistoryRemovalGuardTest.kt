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
 * Batch Z-late phase 1, slice S14: the classic history screen was deleted with its two layouts, and
 * `ScreenLauncher`'s arm collapsed to `HistoryComposeActivity`.
 *
 * Spec §5's S14 row also lists `HistoryComposeActivity` — dead since round 15b rerouted history to a
 * sheet — for deletion in this slice. It is deliberately NOT deleted here, and
 * `theDeadComposeHistoryActivityIsStillHere` below is what stops that decision eroding into a
 * silent under-delivery. Deleting it would leave `Screen.History` with no Activity of any kind,
 * make flag-OFF History a no-op rather than the ordinary "flag-OFF now opens Compose" change, orphan
 * `@style/Theme.AbComposeDialog`, and — the expensive one — strand `sharedUi`'s full-screen
 * `HistoryScreen` as test-only code still carrying 7 goldens, which the next tidy-up would move.
 * All three cost nothing to defer to the epilogue, where §10.2 already collapses the two classic
 * branches that make the Compose activity unreachable.
 */
class ClassicHistoryRemovalGuardTest {
    private val doomedClassNames = listOf(
        "net.bible.android.view.activity.navigation.History",
    )

    private val doomedPaths = listOf(
        "src/main/java/net/bible/android/view/activity/navigation/History.kt",
        "src/main/res/layout/history.xml",
        "src/main/res/layout/history_list_item.xml",
    )

    @Test fun theClassicHistoryFilesAndLayoutsAreGone() {
        ClassicRemovalScan.assertPathsGone(
            doomedPaths,
            "the classic history screen or one of its layouts should have been deleted in S14",
        )
    }

    @Test fun noSourceFileNamesTheClassicHistory() {
        ClassicRemovalScan.assertNoSourceNames(
            doomedClassNames,
            "these files still name the classic navigation.History deleted in S14. The FQN's " +
                "trailing boundary spares navigation.HistoryComposeActivity, which survives this " +
                "slice — so a hit here is a real reference to the deleted class, not the Compose one.",
        )
    }

    @Test fun noManifestEntryNamesTheClassicHistory() {
        ClassicRemovalScan.assertNoManifestNames(
            doomedClassNames,
            "a manifest still names the class S14 deletes. The HistoryComposeActivity block at " +
                "main:487-492 is NOT it and must stay.",
        )
    }

    @Test fun screenLauncherDoesNotBranchForHistory() {
        ClassicRemovalScan.assertLauncherArmsUnconditional(
            listOf("Screen.History"),
            "the History arm still branches on the flag (or is missing entirely) — S14 collapses it " +
                "to HistoryComposeActivity unconditionally",
        )
    }

    @Test fun theDeadComposeHistoryActivityIsStillHere() {
        ClassicRemovalScan.assertPathsPresent(
            listOf("src/main/java/net/bible/android/view/activity/navigation/HistoryComposeActivity.kt"),
            "HistoryComposeActivity is unreachable production code and its deletion belongs to the " +
                "epilogue (spec §10.2), together with the two classic branches that make it " +
                "unreachable. Deleting it here would strand sharedUi's full-screen HistoryScreen as " +
                "test-only code holding 7 goldens, and leave Screen.History with no target class. " +
                "If it should go, take the branches and the style with it in the same change.",
        )
    }
}
