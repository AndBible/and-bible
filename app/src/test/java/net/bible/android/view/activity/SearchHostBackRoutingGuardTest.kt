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
 * each of the six activities that host it is individually responsible for routing back so that,
 * while search mode is active, back closes search instead of leaving the screen. The realistic
 * failure mode is not a wrong branch -- each host's branch was reviewed individually -- it is a
 * host that silently has NO routing at all, which is exactly the shape of finding F43 in
 * `docs/compose-ondevice-findings.md`: a feature that shipped because no test exercised its entry
 * point.
 *
 * This is a *source* guard, not a render/instrumentation test: verifying the actual back-press
 * behaviour would need an instrumented test per activity (Activity + real back dispatch), a much
 * heavier harness than "did the author remember to wire it at all". The guard only checks for the
 * *shape* of routing -- a back entry point (`onBackPressed` override or a Compose `BackHandler`)
 * that also references the host's search-mode state -- not that the logic inside is correct.
 *
 * Two shapes are in use, by design, not by omission: five hosts override the deprecated
 * `onBackPressed()`; `TextDisplaySettingsComposeActivity` uses `BackHandler` instead, because it
 * already has a registered `OnBackPressedCallback` for its own destination-stack navigation, and a
 * registered callback wins over the deprecated override -- so a second `onBackPressed` override
 * there would be dead code. Both shapes are asserted for, never counted: this guard checks that
 * each named entry-point pattern and each named search-state pattern is PRESENT in the stripped
 * source, not how many times. A previous round in this project set a similar guard's threshold to
 * a match count, and the guarded feature could then be deleted while the guard still passed.
 *
 * **All scanned source has its comments stripped first** (see [stripComments]) so that a
 * commented-out override cannot satisfy this guard.
 */
class SearchHostBackRoutingGuardTest {

    companion object {
        /** The six activities that host an in-toolbar search bar. Add a new host here. */
        private val SEARCH_HOST_FILES = listOf(
            "src/main/java/net/bible/android/view/activity/download/DownloadComposeActivity.kt",
            "src/main/java/net/bible/android/view/activity/navigation/ChooseDocumentComposeActivity.kt",
            "src/main/java/net/bible/android/view/activity/bookmark/BookmarksComposeActivity.kt",
            "src/main/java/net/bible/android/view/activity/cloud/CloudDocumentsComposeActivity.kt",
            "src/main/java/net/bible/android/view/activity/settings/SettingsComposeActivity.kt",
            "src/main/java/net/bible/android/view/activity/settings/TextDisplaySettingsComposeActivity.kt",
        )

        private const val BACK_ENTRY_ON_BACK_PRESSED = "onBackPressed"
        private const val BACK_ENTRY_BACK_HANDLER = "BackHandler"

        private const val SEARCH_STATE_ACTIVE = "searchModeActive"
        private const val SEARCH_STATE_MODE_ACTIVE = "searchMode.active"
    }

    private fun strippedSourceOf(path: String): String = stripComments(java.io.File(path).readText())

    @Test
    fun everySearchHostRoutesHardwareBack() {
        for (path in SEARCH_HOST_FILES) {
            val source = strippedSourceOf(path)

            val hasBackEntryPoint = source.contains(BACK_ENTRY_ON_BACK_PRESSED) || source.contains(BACK_ENTRY_BACK_HANDLER)
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

    @Test
    fun stripCommentsRemovesALineCommentThatQuotesTheGuardedPattern() {
        val synthetic = """
            |// explanatory note: some hosts use onBackPressed and searchModeActive
            |class Foo
        """.trimMargin()
        assertThat("sanity: the pattern is present before stripping", synthetic.contains(BACK_ENTRY_ON_BACK_PRESSED), equalTo(true))
        val stripped = stripComments(synthetic)
        assertThat("the comment's occurrence of the pattern must be gone after stripping", stripped.contains(BACK_ENTRY_ON_BACK_PRESSED), equalTo(false))
    }

    @Test
    fun stripCommentsRemovesABlockCommentThatQuotesTheGuardedPattern() {
        val synthetic = """
            |/*
            | * explanatory note: some hosts use onBackPressed and searchModeActive
            | */
            |class Foo
        """.trimMargin()
        assertThat("sanity: the pattern is present before stripping", synthetic.contains(BACK_ENTRY_ON_BACK_PRESSED), equalTo(true))
        val stripped = stripComments(synthetic)
        assertThat("the comment's occurrence of the pattern must be gone after stripping", stripped.contains(BACK_ENTRY_ON_BACK_PRESSED), equalTo(false))
    }
}
