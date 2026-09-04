/*
 * Copyright (c) 2024 And Bible contributors.
 *
 * This file is part of And Bible (https://github.com/AndBible/and-bible).
 *
 * And Bible is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * And Bible is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * And Bible. If not, see <https://www.gnu.org/licenses/>.
 */
package net.bible.android.view.compose

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Batch Z-late phase 1, slice S1: the six classic search screens were deleted and their
 * `ScreenLauncher` arms collapsed to the Compose implementations. This guard makes the deletion
 * durable — a regression that re-adds a file, an import or a flag branch would otherwise only be
 * noticed if something else broke.
 *
 * Source scan rather than a reflective "class not found", matching [SpeakEntryPointGuardTest].
 * Paths are relative to the `:app` module dir, which is the working directory for its unit tests.
 *
 * Task 1 seeds this file with the `BibleView` assertion alone; Task 2 adds the file-deletion and
 * whole-tree sweeps. The epilogue widens it into the phase-wide guard spec §6 describes (iterate
 * `Screen.entries` and assert every arm resolves into the Compose set) — that assertion cannot be
 * true until the last slice lands, so it is deliberately not attempted here.
 */
class ClassicSearchRemovalGuardTest {
    /** Fully-qualified names of the six classic search screens deleted in S1. */
    private val doomedClassNames = listOf(
        "net.bible.android.view.activity.search.SearchIndexProgressStatus",
        "net.bible.android.view.activity.search.SearchIndex",
        "net.bible.android.view.activity.search.SearchResults",
        "net.bible.android.view.activity.search.Search",
        "net.bible.android.view.activity.search.EpubSearch",
        "net.bible.android.view.activity.search.EpubSearchResults",
    )

    /**
     * S1: the `ACTION_PROCESS_TEXT` resolver filter in `getSupportedActivities()` compared
     * `ResolveInfo.activityInfo.name` against classic `SearchResults`. It had been unable to match
     * since the `PROCESS_TEXT` `<activity-alias>` was removed in `837515385690` (2025-08-21), so it
     * was deleted rather than retargeted at the Compose class — retargeting would have kept dead
     * code while implying a live self-exclusion mechanism (spec §9.2, Appendix F.2).
     *
     * If AndBible ever re-registers as a PROCESS_TEXT handler, a fresh alias AND a fresh,
     * deliberately-written filter belong together in that change; this assertion should then be
     * revisited rather than worked around.
     */
    @Test fun bibleViewDoesNotFilterTheProcessTextResolverByAnActivityName() {
        val path = "src/main/java/net/bible/android/view/activity/page/BibleView.kt"
        assertTrue("$path is missing — this guard would pass vacuously", File(path).isFile)
        val code = codeLinesOf(path)
        assertTrue(
            "$path must still enumerate PROCESS_TEXT handlers — the scan is pointless otherwise",
            code.contains("queryIntentActivities("),
        )
        assertEquals(
            "$path filters the PROCESS_TEXT resolver list by an activity name again. The filter " +
                "existed to strip AndBible's own PROCESS_TEXT alias, and that alias has been gone " +
                "since 837515385690 (2025-08-21), so any such filter is dead code that reads as a " +
                "live mechanism. See spec §9.2 and Appendix F.2.",
            emptyList<String>(),
            code.lines()
                .filter { it.contains("activityInfo.name") }
                .map { it.trim() },
        )
    }

    /** Non-prose lines only: an `import` line or a comment must not satisfy or defeat a scan. */
    private fun codeLinesOf(path: String): String =
        File(path).readLines().filterNot { line ->
            val trimmed = line.trimStart()
            trimmed.startsWith("import ") || trimmed.startsWith("//") ||
                trimmed.startsWith("*") || trimmed.startsWith("/*")
        }.joinToString("\n")
}
