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
    /**
     * Fully-qualified names of the six classic search screens deleted in S1, plus the five
     * collaborators that went with them: `EpubSearchItemAdapter` (deleted alongside the screens,
     * in the same atomic commit) and the four orphaned collaborators deleted in the following
     * commit (`MultiSearchItemAdapter`, `SearchDocumentFilter`, and the whole
     * `searchresultsactionbar` subpackage — `SearchResultsActionBarManager` and
     * `ScriptureToggleActionBarButton`).
     */
    private val doomedClassNames = listOf(
        "net.bible.android.view.activity.search.SearchIndexProgressStatus",
        "net.bible.android.view.activity.search.SearchIndex",
        "net.bible.android.view.activity.search.SearchResults",
        "net.bible.android.view.activity.search.Search",
        "net.bible.android.view.activity.search.EpubSearch",
        "net.bible.android.view.activity.search.EpubSearchResults",
        "net.bible.android.view.activity.search.EpubSearchItemAdapter",
        "net.bible.android.view.activity.search.MultiSearchItemAdapter",
        "net.bible.android.view.activity.search.SearchDocumentFilter",
        "net.bible.android.view.activity.search.searchresultsactionbar.SearchResultsActionBarManager",
        "net.bible.android.view.activity.search.searchresultsactionbar.ScriptureToggleActionBarButton",
    )

    private val doomedClassRefs = ClassicRemovalScan.refsFor(doomedClassNames)

    /**
     * S1: confirm that `BibleView.kt` has been cleaned of references to the classic search
     * package and any self-filtering of PROCESS_TEXT handlers.
     *
     * The `ACTION_PROCESS_TEXT` resolver list was filtered to strip AndBible's own PROCESS_TEXT
     * alias (which lived in a `<activity-alias>` for SearchResults, removed in `837515385690`
     * 2025-08-21). Reading `activityInfo.name` to COMPARE it against a class is the dead filter
     * (and would be dead code if re-added at SearchResultsComposeActivity either). Reading it to
     * TARGET an intent via `setClassName()` is the live "send selected verse to another app"
     * menu, which is fine and must stay.
     *
     * See spec §9.2 and Appendix F.2.
     */
    @Test fun bibleViewDoesNotFilterTheProcessTextResolverByAnActivityName() {
        val path = "src/main/java/net/bible/android/view/activity/page/BibleView.kt"
        assertTrue("$path is missing — this guard would pass vacuously", File(path).isFile)
        val code = ClassicRemovalScan.codeLinesOf(path)
        assertTrue(
            "$path must still enumerate PROCESS_TEXT handlers — the scan is pointless otherwise",
            code.contains("queryIntentActivities("),
        )
        assertEquals(
            "$path compares an activity name against a class again. The PROCESS_TEXT resolver " +
                "list was filtered to strip AndBible's own PROCESS_TEXT alias, and that alias " +
                "has been gone since 837515385690 (2025-08-21), so any such comparison is dead " +
                "code that reads as a live self-exclusion mechanism. Reading activityInfo.name " +
                "to TARGET an intent (setClassName) is the live PROCESS_TEXT menu and is fine. " +
                "See spec §9.2 and Appendix F.2.",
            emptyList<String>(),
            code.lines()
                .filter { it.contains("activityInfo.name") && (it.contains("!=") || it.contains("==")) }
                .map { it.trim() },
        )
        assertEquals(
            "$path references one of S1's doomed classic search classes/collaborators by its " +
                "fully-qualified name. A surviving file may not name them, and retargeting at the " +
                "Compose class would be equally dead while implying a live mechanism (spec §9.2, " +
                "Appendix F.2). Scanned raw (not the comment-stripped codeLinesOf) so a re-added " +
                "import is caught here too.",
            emptyList<String>(),
            File(path).readLines()
                .filter { line -> doomedClassRefs.any { it.containsMatchIn(line) } }
                .map { it.trim() },
        )
    }

    private val doomedPaths = listOf(
        "src/main/java/net/bible/android/view/activity/search/SearchIndexProgressStatus.kt",
        "src/main/java/net/bible/android/view/activity/search/SearchIndex.kt",
        "src/main/java/net/bible/android/view/activity/search/SearchResults.kt",
        "src/main/java/net/bible/android/view/activity/search/Search.kt",
        "src/main/java/net/bible/android/view/activity/search/EpubSearch.kt",
        "src/main/java/net/bible/android/view/activity/search/EpubSearchResults.kt",
        "src/main/java/net/bible/android/view/activity/search/EpubSearchItemAdapter.kt",
    )

    @Test fun theClassicSearchScreensAreGone() {
        assertTrue(
            "cwd is not the :app module dir — this guard would pass vacuously",
            File("src/main").isDirectory,
        )
        val survivors = doomedPaths.filter { File(it).exists() }
        assertEquals("these classic search files should have been deleted in S1", emptyList<String>(), survivors)
    }

    /**
     * Three files in the same directory SURVIVE despite classic-sounding names, and
     * `EpubSearchResultKey.kt` is the trap — its name promises exactly what S1 deletes, but it holds
     * the top-level `epubKeyFor` that `EpubSearchResultsComposeActivity` and `ComposeReadingViewHost`
     * both read. Asserting they exist turns "deleted too much" into a failure instead of a silence.
     */
    @Test fun theSurvivingSearchCollaboratorsStillExist() {
        val expected = listOf(
            "src/main/java/net/bible/android/view/activity/search/EpubSearchResultKey.kt",
            "src/main/java/net/bible/android/view/activity/search/AndroidEpubSearchService.kt",
            "src/main/java/net/bible/android/view/activity/search/EpubSearchModeWire.kt",
        )
        val missing = expected.filterNot { File(it).isFile }
        assertEquals("S1 deleted a file it was supposed to keep", emptyList<String>(), missing)
    }

    /**
     * The reference proof of spec §3.3, expressed as a test so it survives this session. Delegates
     * to [ClassicRemovalScan.assertNoSourceNames], which walks every shipping source set's
     * Kotlin/Java source AND its resource XML, so a new reference to a deleted class by its
     * fully-qualified name — whether written in source or named by a layout — cannot escape.
     *
     * Matching on the FULLY-QUALIFIED name is deliberate: `Search`, `SearchIndex` and `SearchResults`
     * are ordinary English words and a bare-name scan would drown in false positives — including
     * the genuinely unrelated `net.bible.service.sword.epub.EpubSearch`.
     */
    @Test fun noSourceFileNamesAClassicSearchScreen() {
        ClassicRemovalScan.assertNoSourceNames(
            doomedClassNames,
            "these files still name a classic search screen deleted in S1",
        )
    }

    /** The routing arms must be unconditional now: no `useComposeFor` branch may mention search. */
    @Test fun screenLauncherDoesNotBranchForSearch() {
        val path = "src/main/java/net/bible/android/view/ScreenLauncher.kt"
        assertTrue("$path is missing — this guard would pass vacuously", File(path).isFile)
        val code = ClassicRemovalScan.codeLinesOf(path)
        assertTrue(
            "$path no longer reads use_compose_ui at all — the flag must survive S1 for the " +
                "remaining slices (spec §3.4)",
            code.contains("useComposeFor"),
        )
        val searchScreens = listOf(
            "Screen.SearchIndexProgress", "Screen.SearchIndex", "Screen.SearchResults",
            "Screen.Search", "Screen.EpubSearch", "Screen.EpubSearchResults",
        )
        // Each arm is `Screen.X -> XComposeActivity::class.java`. Take the text from the arm's
        // `Screen.X ->` up to the next `Screen.` and assert no branch survives inside it.
        val offenders = searchScreens.filter { screen ->
            val start = code.indexOf("$screen ->")
            if (start < 0) return@filter true
            val next = code.indexOf("Screen.", start + screen.length + 3)
            val arm = if (next < 0) code.substring(start) else code.substring(start, next)
            arm.contains("useComposeFor") || arm.contains("else ")
        }
        assertEquals(
            "these search arms still branch on the flag (or are missing entirely) — S1 collapses " +
                "them to the Compose class unconditionally",
            emptyList<String>(),
            offenders,
        )
    }
}
