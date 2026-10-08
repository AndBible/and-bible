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
 * Batch Z-late phase 1, slice S14: the classic history screen was deleted with its two layouts.
 *
 * The epilogue finished the job. S14 had deliberately KEPT `HistoryComposeActivity` — dead since
 * round 15b rerouted history to a sheet — because deleting it then would have left `Screen.History`
 * with no Activity of any kind while the flag could still route to it. The epilogue removed the
 * last two branches that could reach it, so the activity, its `<activity>` manifest block, its
 * `@style/Theme.AbComposeDialog` window theme and the `Screen.History` enum entry all went
 * together, and the remaining caller (`MenuCommandHandler.historyButton`) only calls
 * `host.showHistorySheet()`. The long-press-BACK route (`MainBibleActivity.onKeyLongPress`) is gone (spec
 * 2026-10-08 API 36, decision 1), so History is reached only from the menu.
 *
 * What that leaves behind is recorded rather than swept: `sharedUi`'s full-screen `HistoryScreen`
 * composable is now test-only code carrying 7 goldens. It is NOT deleted — `:sharedUi` cleanup is
 * out of this phase's scope (spec 2.3), and a composable with goldens is not the same thing as
 * dead code. `HistoryListContent`, the sheet's own body, stays live.
 */
class ClassicHistoryRemovalGuardTest {
    /**
     * A LEADING boundary is as necessary as the trailing one [ClassicRemovalScan.refsFor] adds:
     * the entry below is written fully qualified with its own `navigation.` package segment in
     * front, to distinguish it from any other class ending in the substring
     * `HistoryComposeActivity`. That used to matter for a live sibling,
     * `net.bible.android.view.activity.ai.RawLogHistoryComposeActivity` -- nav-graph Task 10
     * deleted that class too, so the carve-out this qualification exists for is now dead (nothing
     * left to collide with), but harmless, and is kept rather than loosened to an unqualified
     * `HistoryComposeActivity` in case a future screen reintroduces the same suffix.
     */
    private val doomedClassNames = listOf(
        "net.bible.android.view.activity.navigation.History",
        "net.bible.android.view.activity.navigation.HistoryComposeActivity",
    )

    private val doomedPaths = listOf(
        "src/main/java/net/bible/android/view/activity/navigation/History.kt",
        "src/main/java/net/bible/android/view/activity/navigation/HistoryComposeActivity.kt",
        "src/main/res/layout/history.xml",
        "src/main/res/layout/history_list_item.xml",
    )

    @Test fun theClassicHistoryFilesAndLayoutsAreGone() {
        ClassicRemovalScan.assertPathsGone(
            doomedPaths,
            "the classic history screen or one of its layouts should have been deleted in S14, and " +
                "the unreachable HistoryComposeActivity in the epilogue",
        )
    }

    @Test fun noSourceFileNamesTheClassicHistory() {
        ClassicRemovalScan.assertNoSourceNames(
            doomedClassNames,
            "these files still name a history Activity: the classic navigation.History deleted in " +
                "S14, or navigation.HistoryComposeActivity deleted in the epilogue. Both entries " +
                "carry their navigation. package segment so neither would have matched " +
                "ai.RawLogHistoryComposeActivity, which nav-graph Task 10 has since deleted too.",
        )
    }

    @Test fun noManifestEntryNamesTheClassicHistory() {
        ClassicRemovalScan.assertNoManifestNames(
            doomedClassNames,
            "a manifest still names a deleted history Activity. Both blocks are gone: the classic " +
                "one in S14, and HistoryComposeActivity's (src/main/AndroidManifest.xml:360-365 " +
                "before removal) in the epilogue.",
        )
    }
}
