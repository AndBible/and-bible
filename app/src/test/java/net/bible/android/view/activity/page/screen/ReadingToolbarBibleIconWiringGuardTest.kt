/*
 * Copyright (c) 2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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
 * source-text scan can't be defeated -- or taxed -- by a comment that legitimately quotes the very
 * pattern the scan looks for. Same helper, same rationale, as
 * [net.bible.android.view.activity.SearchHostBackRoutingGuardTest]'s `stripComments` and
 * [SearchSheetStructureGuardTest]'s: a whole-branch-review comment once quoted a guarded literal
 * verbatim, earlier in the file than the real call site, and defeated a raw-source guard that never
 * stripped comments first.
 */
private fun stripComments(text: String): String {
    val noBlockComments = Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL).replace(text, "")
    return noBlockComments.lines().joinToString("\n") { line ->
        val commentAt = line.indexOf("//")
        if (commentAt >= 0) line.substring(0, commentAt) else line
    }
}

/**
 * F58 fix round 1 -- a wiring guard for [bibleToolbarIconRes]'s one production call site.
 *
 * [bibleToolbarIconRes] exists so the discrete-mode icon CHOICE is unit-testable
 * ([DiscreteChromeTest]) without a Compose rendering harness -- but `DiscreteChromeTest` calls the
 * helper directly, so it proves the helper is correct while proving nothing about whether
 * `readingToolbarIcons()`, the helper's only production caller, actually still uses it. Reverting
 * `bible = painterResource(bibleToolbarIconRes())` back to
 * `bible = painterResource(R.drawable.ic_bible_24dp)` leaves every existing test green: the helper
 * function itself is unreachable-but-still-present dead code, and nothing else notices. That is
 * exactly the "silently no wiring" failure shape `SearchHostBackRoutingGuardTest`'s kdoc names for
 * F43 and F55 -- a feature whose entry point nothing exercises.
 *
 * This is a *source* guard, not a render/instrumentation test, for the same reason
 * [SearchSheetStructureGuardTest] is: the rendered `Painter` two `painterResource` calls produce is
 * not usefully comparable in a unit test (no `ComposeTestRule`/real composition runs here, and
 * `VectorPainter` has no content-equality contract to assert against), so the only thing left to
 * assert is the WIRING -- that the call site spells the helper, not the raw drawable literal.
 *
 * Scoped narrowly to `readingToolbarIcons()`'s own argument list (see [readingToolbarIconsBody]),
 * not the whole file: [bibleToolbarIconRes]'s own body legitimately contains the literal
 * `R.drawable.ic_bible_24dp` as its non-discrete branch, so a file-wide "the literal must not
 * appear anywhere" assertion would fail permanently on a correct fix, not just on a reverted one.
 */
class ReadingToolbarBibleIconWiringGuardTest {

    companion object {
        private const val HOST_FILE =
            "src/main/java/net/bible/android/view/activity/page/screen/ComposeReadingViewHost.kt"
        private const val TOOLBAR_ICONS_CALL_START = "private fun readingToolbarIcons() = ReadingToolbarIcons("
        private val BIBLE_ICON_CALL_SITE = Regex("""bible\s*=\s*painterResource\(bibleToolbarIconRes\(\)\)""")
    }

    private val rawSource = java.io.File(HOST_FILE).readText()
    private val source = stripComments(rawSource)

    /**
     * `readingToolbarIcons()`'s argument list only -- from just after `ReadingToolbarIcons(` to the
     * closing `)` that sits alone on its own line, which is how the real call site is formatted
     * (each argument ends with a trailing comma; the closing paren does not). Deliberately narrow:
     * the file also defines [bibleToolbarIconRes] itself, whose non-discrete branch legitimately
     * spells the raw drawable literal this guard forbids inside the CALL SITE.
     */
    private fun readingToolbarIconsBody(): String {
        val startAt = source.indexOf(TOOLBAR_ICONS_CALL_START)
        assertThat("$HOST_FILE must still define readingToolbarIcons()", startAt >= 0, equalTo(true))
        val bodyStart = startAt + TOOLBAR_ICONS_CALL_START.length
        val endAt = source.indexOf("\n)", bodyStart)
        assertThat("readingToolbarIcons()'s closing ) must be found", endAt >= 0, equalTo(true))
        return source.substring(bodyStart, endAt)
    }

    @Test
    fun theToolbarBibleIconCallSiteUsesTheDiscreteModeHelper() {
        val body = readingToolbarIconsBody()
        assertThat(
            "readingToolbarIcons()'s `bible` argument must call bibleToolbarIconRes() " +
                "(F58) -- reverting it to a raw painterResource(R.drawable.ic_bible_24dp) leaves " +
                "discrete mode's toolbar icon un-swapped, and DiscreteChromeTest, which calls " +
                "bibleToolbarIconRes() directly, would not notice",
            BIBLE_ICON_CALL_SITE.containsMatchIn(body), equalTo(true),
        )
    }

    @Test
    fun noRawBibleDrawableLiteralRemainsInTheToolbarIconsBuilder() {
        val body = readingToolbarIconsBody()
        assertThat(
            "readingToolbarIcons()'s argument list must not spell R.drawable.ic_bible_24dp " +
                "directly -- that literal belongs only inside bibleToolbarIconRes()'s own " +
                "non-discrete branch",
            body.contains("R.drawable.ic_bible_24dp"), equalTo(false),
        )
    }

    @Test
    fun stripCommentsRemovesACommentThatQuotesTheHelperCallWithoutTheRealCallSite() {
        // Reproduces the exact failure shape this guard must not be vulnerable to: a comment
        // mentioning `bibleToolbarIconRes()` sits above a reverted, raw-literal real call site --
        // a bare (unstripped) substring search over the whole function would be satisfied by the
        // comment alone, even though the real argument no longer calls the helper.
        val synthetic = """
            |private fun readingToolbarIcons() = ReadingToolbarIcons(
            |    // F58: bible = painterResource(bibleToolbarIconRes())
            |    bible = painterResource(R.drawable.ic_bible_24dp),
            |)
        """.trimMargin()
        val stripped = stripComments(synthetic)
        val startAt = stripped.indexOf(TOOLBAR_ICONS_CALL_START)
        val body = stripped.substring(startAt + TOOLBAR_ICONS_CALL_START.length, stripped.indexOf("\n)", startAt))
        assertThat(
            "the comment's mention of the helper must be gone after stripping, leaving only the raw literal",
            BIBLE_ICON_CALL_SITE.containsMatchIn(body), equalTo(false),
        )
        assertThat(body.contains("R.drawable.ic_bible_24dp"), equalTo(true))
    }
}
