/*
 * Copyright (c) 2026 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
 *
 * This file is part of AndBible: Bible Study (http://github.com/AndBible/and-bible).
 *
 * AndBible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * AndBible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY,
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with AndBible.
 * If not, see http://www.gnu.org/licenses/.
 */

package net.bible.android.view.activity

import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Test

/**
 * Strips Kotlin `//` line comments and slash-star ... star-slash block comments from [text], so a
 * source-text scan can't be defeated -- or taxed -- by a comment that legitimately quotes the very
 * pattern the scan looks for. Follows the same approach as
 * `net.bible.android.view.activity.page.screen.SearchSheetStructureGuardTest`, which documents why
 * this is load-bearing: a whole-branch-review comment once quoted a guarded literal verbatim,
 * earlier in the file than the real call site, and defeated a raw-source version of that guard.
 *
 * Deliberately simple, NOT a Kotlin lexer: it does not track string literals, so a comment marker
 * that happens to appear inside a Kotlin string constant would be (wrongly) treated as the start
 * of a comment. Acceptable for a guard scanning hand-written production source, where that pattern
 * doesn't occur in the lines these assertions care about.
 */
private fun stripComments(text: String): String {
    val noBlockComments = Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL).replace(text, "")
    return noBlockComments.lines().joinToString("\n") { line ->
        val commentAt = line.indexOf("//")
        if (commentAt >= 0) line.substring(0, commentAt) else line
    }
}

/**
 * Task 7b -- a source guard verifying that every in-toolbar-search host routes hardware back.
 *
 * `AbTopBarSearch.kt` deliberately does NOT intercept hardware back (see its kdoc, lines 51-52):
 * each host that shows it is individually responsible for routing back so that, while search mode
 * is active, back closes search instead of leaving the screen. The realistic failure mode is not a
 * wrong branch -- each host's branch was reviewed individually -- it is a host that silently has NO
 * routing at all, which is exactly the shape of finding F43 in
 * `docs/compose-ondevice-findings.md`: a feature that shipped because no test exercised its entry
 * point.
 *
 * **Two lists, because a host is no longer always an Activity.** [SEARCH_HOST_FILES] holds the
 * `:app` Activity hosts; [SEARCH_HOST_GRAPH_FILES] holds the nav-graph arms in `:sharedUi` that
 * have taken over from a deleted one. The nav-graph migration moves hosts from the first list to
 * the second one slice at a time -- `SettingsComposeActivity` was the first, in nav-graph 3/5/6
 * Task 9 -- and on the current trajectory the Activity list eventually empties. That is precisely
 * why [everyScannedSearchHostFileExists] and the two `isNotEmpty()` preconditions exist: a list
 * that quietly drains to zero would otherwise leave a `for` loop with nothing to iterate and a
 * guard that passes by doing nothing, which is the same failure this guard was written to catch,
 * one level up. Moving a host means moving its ENTRY in the same commit, never just deleting it.
 *
 * This is a *source* guard, not a render/instrumentation test: verifying the actual back-press
 * behaviour would need an instrumented test per activity (Activity + real back dispatch), a much
 * heavier harness than "did the author remember to wire it at all". The guard only checks for the
 * *shape* of routing -- a back entry point (`onBackPressed` override or a Compose `BackHandler`)
 * that also references the host's search-mode state -- not that the logic inside is correct.
 *
 * Three shapes are in use, by design, not by omission: six Activity hosts override the deprecated
 * `onBackPressed()`; `TextDisplaySettingsComposeActivity` uses `BackHandler` instead, because it
 * already has a registered `OnBackPressedCallback` for its own destination-stack navigation, and a
 * registered callback wins over the deprecated override -- so a second `onBackPressed` override
 * there would be dead code; and a graph arm has no Activity to override at all, so it uses
 * `PlatformBackHandler` (sharedUi's expect/actual over Compose's `BackHandler`). All three shapes
 * are asserted for, never counted: this guard checks that each named entry-point pattern and each
 * named search-state pattern is PRESENT in the stripped source, not how many times. A previous
 * round in this project set a similar guard's threshold to a match count, and the guarded feature
 * could then be deleted while the guard still passed.
 *
 * The entry-point patterns match an actual CALL SITE, not a bare token: `override\s+fun\s+
 * onBackPressed` (not bare `onBackPressed`, which also matches the harmless `super.onBackPressed()`
 * inside the override body) and `BackHandler\s*[({]` (not bare `BackHandler`, which also matches
 * `import androidx.activity.compose.BackHandler` -- an import that would keep satisfying a
 * bare-token guard even after the real `BackHandler { ... }` call site is deleted, exactly the
 * "silently no routing" shape this guard exists to catch). `PlatformBackHandler\s*\(` is the same
 * rule for the graph shape: `import net.bible.sharedui.PlatformBackHandler` has no `(` after the
 * name, so an orphaned import cannot satisfy it either -- asserted by
 * [aPlatformBackHandlerImportAloneDoesNotSatisfyTheGraphShape].
 *
 * **All scanned source has its comments stripped first** (see [stripComments]) so that a
 * commented-out override cannot satisfy this guard.
 */
class SearchHostBackRoutingGuardTest {

    companion object {
        /**
         * The seven `:app` ACTIVITIES that host an in-toolbar search bar. Add a new one here --
         * or, if the host lives in the nav graph, to [SEARCH_HOST_GRAPH_FILES] instead.
         *
         * `SettingsComposeActivity.kt` left this list in nav-graph 3/5/6 Task 9 when the Activity
         * was deleted; its entry MOVED to [SEARCH_HOST_GRAPH_FILES] rather than disappearing.
         * Every remaining entry here is a nav-graph migration target, so expect this list to
         * shrink and the graph list to grow, one slice at a time.
         */
        private val SEARCH_HOST_FILES = listOf(
            "src/main/java/net/bible/android/view/activity/download/DownloadComposeActivity.kt",
            "src/main/java/net/bible/android/view/activity/navigation/ChooseDocumentComposeActivity.kt",
            "src/main/java/net/bible/android/view/activity/bookmark/BookmarksComposeActivity.kt",
            "src/main/java/net/bible/android/view/activity/cloud/CloudDocumentsComposeActivity.kt",
            "src/main/java/net/bible/android/view/activity/settings/TextDisplaySettingsComposeActivity.kt",
            "src/main/java/net/bible/android/view/mydocuments/MyDocumentsComposeActivity.kt",
            "src/main/java/net/bible/android/view/mydocuments/MyDocumentPagesComposeActivity.kt",
        )

        /**
         * The nav-graph arms that host an in-toolbar search bar, relative to the `:app` module
         * dir (its unit tests' working directory) -- the same `../sharedUi/...` form ten sibling
         * guards in this source set already use, e.g. `SheetExpansionGuardTest`,
         * `SettingsEditorSheetGuardTest`, `MenuSeamGuardTest` and `SpeakBarTopEdgeGuardTest`.
         *
         * A graph arm is not an Activity, so it cannot override `onBackPressed`; it routes back
         * with `PlatformBackHandler(enabled = searchModeActive) { searchMode.close() }` instead.
         * The property being guarded is identical to the Activity list's -- an entry point that
         * references the search-mode state -- only the entry-point shape differs.
         */
        private val SEARCH_HOST_GRAPH_FILES = listOf(
            "../sharedUi/src/commonMain/kotlin/net/bible/sharedui/settings/nav/SettingsNavGraph.kt",
        )

        // Call-site patterns, not bare tokens: a bare "onBackPressed" also matches inside
        // `super.onBackPressed()`, and a bare "BackHandler" also matches the import statement
        // `import androidx.activity.compose.BackHandler` -- neither proves a real entry point
        // exists, so an import left behind after the real call site is deleted would otherwise
        // silently satisfy the guard.
        private val BACK_ENTRY_ON_BACK_PRESSED = Regex("override\\s+fun\\s+onBackPressed")
        private val BACK_ENTRY_BACK_HANDLER = Regex("BackHandler\\s*[({]")
        private val BACK_ENTRY_PLATFORM_BACK_HANDLER = Regex("PlatformBackHandler\\s*\\(")

        private const val SEARCH_STATE_ACTIVE = "searchModeActive"
        private const val SEARCH_STATE_MODE_ACTIVE = "searchMode.active"
    }

    private fun strippedSourceOf(path: String): String = stripComments(java.io.File(path).readText())

    @Test
    fun everyScannedSearchHostFileExists() {
        // Anti-vacuity for BOTH lists at once: a host whose file is renamed or moved (or whose
        // entry is deleted instead of moved when it migrates into the graph) must fail HERE, with
        // a message naming the path, rather than as a FileNotFoundException from a later loop --
        // or, once a list has drained to zero, as no failure at all.
        assertThat("SEARCH_HOST_FILES is empty -- the Activity loop would pass vacuously", SEARCH_HOST_FILES.isNotEmpty(), equalTo(true))
        assertThat("SEARCH_HOST_GRAPH_FILES is empty -- the graph loop would pass vacuously", SEARCH_HOST_GRAPH_FILES.isNotEmpty(), equalTo(true))
        val missing = (SEARCH_HOST_FILES + SEARCH_HOST_GRAPH_FILES).filterNot { java.io.File(it).isFile }
        assertThat(
            "these scanned search-host paths do not exist -- a host that MOVES (Activity -> nav " +
                "graph) must move its entry between the two lists in the same commit, not lose it",
            missing, equalTo(emptyList<String>()),
        )
    }

    @Test
    fun everySearchHostRoutesHardwareBack() {
        assertThat("SEARCH_HOST_FILES is empty -- this loop would pass vacuously", SEARCH_HOST_FILES.isNotEmpty(), equalTo(true))
        for (path in SEARCH_HOST_FILES) {
            val source = strippedSourceOf(path)

            val hasBackEntryPoint = BACK_ENTRY_ON_BACK_PRESSED.containsMatchIn(source) || BACK_ENTRY_BACK_HANDLER.containsMatchIn(source)
            assertThat(
                "$path must override `onBackPressed` or use `BackHandler` to route hardware back " +
                    "while search is open (found neither)",
                hasBackEntryPoint, equalTo(true),
            )

            val referencesSearchState = source.contains(SEARCH_STATE_ACTIVE) || source.contains(SEARCH_STATE_MODE_ACTIVE)
            assertThat(
                "$path's back routing must reference the search-mode state " +
                    "(`searchModeActive` or `searchMode.active`), found neither",
                referencesSearchState, equalTo(true),
            )
        }
    }

    /**
     * The graph-side twin of [everySearchHostRoutesHardwareBack], and the replacement for what was
     * lost when `SettingsComposeActivity` was deleted in nav-graph 3/5/6 Task 9. Without it, the
     * settings search bar's back routing (`SettingsNavGraph.kt`'s `PlatformBackHandler` line) had
     * no test naming it anywhere in the tree -- only a KDoc -- so deleting that line would have
     * left every gate green. That is exactly the failure shape this guard class exists for.
     */
    @Test
    fun everyGraphSearchHostRoutesHardwareBack() {
        assertThat("SEARCH_HOST_GRAPH_FILES is empty -- this loop would pass vacuously", SEARCH_HOST_GRAPH_FILES.isNotEmpty(), equalTo(true))
        for (path in SEARCH_HOST_GRAPH_FILES) {
            val source = strippedSourceOf(path)

            assertThat(
                "$path must call `PlatformBackHandler(...)` to route hardware back while search " +
                    "is open -- a graph arm has no Activity on which to override onBackPressed",
                BACK_ENTRY_PLATFORM_BACK_HANDLER.containsMatchIn(source), equalTo(true),
            )

            val referencesSearchState = source.contains(SEARCH_STATE_ACTIVE) || source.contains(SEARCH_STATE_MODE_ACTIVE)
            assertThat(
                "$path's back routing must reference the search-mode state " +
                    "(`searchModeActive` or `searchMode.active`), found neither",
                referencesSearchState, equalTo(true),
            )
        }
    }

    @Test
    fun aPlatformBackHandlerImportAloneDoesNotSatisfyTheGraphShape() {
        // The graph twin of [anUnusedBackHandlerImportDoesNotSatisfyTheBackHandlerShape]: the real
        // arm's import line carries the guarded name, so a bare-token pattern would keep passing
        // after the `PlatformBackHandler(enabled = ...)` call site itself was deleted.
        val synthetic = """
            |import net.bible.sharedui.PlatformBackHandler
            |
            |fun NavGraphBuilder.settingsGraph() {
            |    val searchModeActive = false
            |}
        """.trimMargin()
        val stripped = stripComments(synthetic)
        assertThat(
            "an import alone must not satisfy the PlatformBackHandler call-site pattern",
            BACK_ENTRY_PLATFORM_BACK_HANDLER.containsMatchIn(stripped), equalTo(false),
        )
    }

    @Test
    fun stripCommentsRemovesALineCommentThatQuotesTheGuardedPattern() {
        val synthetic = """
            |// explanatory note: some hosts use override fun onBackPressed and searchModeActive
            |class Foo
        """.trimMargin()
        assertThat("sanity: the pattern is present before stripping", BACK_ENTRY_ON_BACK_PRESSED.containsMatchIn(synthetic), equalTo(true))
        val stripped = stripComments(synthetic)
        assertThat("the comment's occurrence of the pattern must be gone after stripping", BACK_ENTRY_ON_BACK_PRESSED.containsMatchIn(stripped), equalTo(false))
    }

    @Test
    fun stripCommentsRemovesABlockCommentThatQuotesTheGuardedPattern() {
        val synthetic = """
            |/*
            | * explanatory note: some hosts use override fun onBackPressed and searchModeActive
            | */
            |class Foo
        """.trimMargin()
        assertThat("sanity: the pattern is present before stripping", BACK_ENTRY_ON_BACK_PRESSED.containsMatchIn(synthetic), equalTo(true))
        val stripped = stripComments(synthetic)
        assertThat("the comment's occurrence of the pattern must be gone after stripping", BACK_ENTRY_ON_BACK_PRESSED.containsMatchIn(stripped), equalTo(false))
    }

    @Test
    fun anUnusedBackHandlerImportDoesNotSatisfyTheBackHandlerShape() {
        // Reproduces the exact gap this round's fix closes: a bare-token match on "BackHandler"
        // would be satisfied by the import line alone, even with the real `BackHandler { ... }`
        // call site deleted -- which is precisely the "silently no routing" failure this guard
        // exists to catch.
        val synthetic = """
            |import androidx.activity.compose.BackHandler
            |
            |class Foo {
            |    fun bar() {
            |        // BackHandler call site intentionally removed
            |    }
            |}
        """.trimMargin()
        val stripped = stripComments(synthetic)
        assertThat(
            "an import alone must not satisfy the BackHandler call-site pattern",
            BACK_ENTRY_BACK_HANDLER.containsMatchIn(stripped), equalTo(false),
        )
    }
}
