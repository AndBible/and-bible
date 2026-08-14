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

package net.bible.android.view.activity.page.screen

import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Test

/**
 * Strips Kotlin `//` line comments and slash-star ... star-slash block comments from [text], so a
 * source-text scan can't be defeated — or taxed — by a comment that legitimately quotes the very
 * pattern the scan looks for (see the class kdoc on [SearchSheetStructureGuardTest]).
 *
 * Deliberately simple, NOT a Kotlin lexer: it does not track string literals, so a comment marker
 * that happens to appear inside a Kotlin string constant would be (wrongly) treated as the start
 * of a comment. Acceptable for a guard scanning hand-written production source, where that
 * pattern doesn't occur in the lines these assertions care about.
 */
private fun stripComments(text: String): String {
    val noBlockComments = Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL).replace(text, "")
    return noBlockComments.lines().joinToString("\n") { line ->
        val commentAt = line.indexOf("//")
        if (commentAt >= 0) line.substring(0, commentAt) else line
    }
}

/**
 * F6 Task 0 — a source guard for the search sheet's one structural rule.
 *
 * The `BottomSheetScaffold` that hosts the reading-view search sheet must be **unconditionally
 * present** and must **enclose** `key(gen)`. Conditionality — not the wrapper itself — is what
 * re-parents every pane's `AndroidView` and so destroys and recreates every `BibleView` WebView each
 * time search is opened or closed. The same rule the drawer wrap follows; see the comment at
 * `ComposeReadingViewHost.kt:1350-1357`.
 *
 * This is a *source* guard because the invariant cannot be tested by rendering: `:app` has no
 * `ComposeTestRule` (`compose-ui-test` cannot be added under this container's strict egress), and in a
 * `mountComposeView` test the container is never attached to a window, so composition never runs and no
 * `AndroidView` factory is ever invoked. Same idiom as `SettingsBadgeLayoutDriftTest`.
 *
 * Note on matching: the literal `key(gen)` appears several times in the host, most of them inside
 * comments, so ordering assertions match on `key(gen) {` — the single actual call site.
 *
 * **All scanned source has its comments stripped first (see [stripComments])**, not just lines that
 * happen to start with a comment marker. A guard that scans raw source text for a literal pattern must
 * not be defeatable — or, just as bad, TAXED — by an explanatory comment that legitimately quotes the
 * very pattern the guard looks for: a whole-branch-review comment once added above the real
 * `reserveBottomInset` call site quoted `key(gen) { ReadingViewScreen(...) }` verbatim, far earlier in
 * the file than the real call, and [theScaffoldEnclosesTheGenerationKeyRatherThanSittingInsideIt] (which
 * scanned raw, unstripped `source`) picked up the comment's occurrence instead of the code's and failed
 * on a production structure that was, in fact, unchanged.
 */
class SearchSheetStructureGuardTest {

    private val rawSource = java.io.File(
        "src/main/java/net/bible/android/view/activity/page/screen/ComposeReadingViewHost.kt"
    ).readText()

    private val source = stripComments(rawSource)

    private val codeLines = source.lines()
        .map { it.trim() }
        .filterNot { it.isEmpty() }

    @Test
    fun theBottomSheetScaffoldIsPresent() {
        assertThat(codeLines.any { it.startsWith("BottomSheetScaffold(") }, equalTo(true))
    }

    @Test
    fun theScaffoldIsNotWrappedInAConditionalOnOneLine() {
        val offending = codeLines.filter { it.startsWith("if (") && it.contains("BottomSheetScaffold") }
        assertThat(offending, equalTo(emptyList()))
    }

    @Test
    fun theScaffoldIsNotTheFirstStatementOfAConditionalBlock() {
        // The one-line check above would miss the natural way to write the mistake:
        //     if (searchModeActive) {
        //         BottomSheetScaffold(
        // so also assert that none of the code lines shortly above the call opens a conditional block.
        val at = codeLines.indexOfFirst { it.startsWith("BottomSheetScaffold(") }
        assertThat("the scaffold call must exist", at >= 0, equalTo(true))
        val preceding = codeLines.subList(maxOf(0, at - 12), at)
        val openers = preceding.filter { line ->
            line.startsWith("if (") ||
                line.startsWith("else") ||
                line.startsWith("} else") ||
                line.startsWith("when (") ||
                line.startsWith("when {")
        }
        assertThat("no conditional may open just above the scaffold, found: $openers", openers, equalTo(emptyList()))
    }

    @Test
    fun theScaffoldEnclosesTheGenerationKeyRatherThanSittingInsideIt() {
        val scaffoldAt = source.indexOf("BottomSheetScaffold(")
        val keyGenAt = source.indexOf("key(gen) {")
        assertThat("the scaffold call must appear before key(gen) {", scaffoldAt in 0 until keyGenAt, equalTo(true))
    }

    @Test
    fun theSheetStateAllowsBeingFullyHidden() {
        // `skipHiddenState = false` is what lets the sheet close completely while the scaffold itself
        // never goes away — the two requirements are only compatible because of this flag.
        assertThat(source.contains("skipHiddenState = false"), equalTo(true))
    }

    @Test
    fun stripCommentsRemovesALineCommentThatQuotesTheGuardedPattern() {
        // Reproduces the exact failure this class's guards must NOT be vulnerable to: the ONLY
        // occurrence of `key(gen) {` in this synthetic source is inside a `//` comment that sits
        // BEFORE the real BottomSheetScaffold( call -- precisely the shape that made the real
        // ComposeReadingViewHost.kt:1635 comment defeat the raw-source version of
        // theScaffoldEnclosesTheGenerationKeyRatherThanSittingInsideIt.
        val synthetic = """
            |// explanatory note: this mirrors key(gen) { ReadingViewScreen(...) } elsewhere
            |BottomSheetScaffold(
            |    content = { Text("no real key(gen) call in this body") }
            |)
        """.trimMargin()
        assertThat("sanity: the pattern is present before stripping", synthetic.contains("key(gen) {"), equalTo(true))
        val stripped = stripComments(synthetic)
        assertThat("the comment's occurrence of the pattern must be gone after stripping", stripped.contains("key(gen) {"), equalTo(false))
    }

    @Test
    fun stripCommentsRemovesABlockCommentThatQuotesTheGuardedPattern() {
        val synthetic = """
            |/*
            | * explanatory note: this mirrors key(gen) { ReadingViewScreen(...) } elsewhere
            | */
            |BottomSheetScaffold(
            |    content = { Text("no real key(gen) call in this body") }
            |)
        """.trimMargin()
        assertThat("sanity: the pattern is present before stripping", synthetic.contains("key(gen) {"), equalTo(true))
        val stripped = stripComments(synthetic)
        assertThat("the comment's occurrence of the pattern must be gone after stripping", stripped.contains("key(gen) {"), equalTo(false))
    }
}
