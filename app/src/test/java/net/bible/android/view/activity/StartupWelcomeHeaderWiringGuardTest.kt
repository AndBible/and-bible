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

package net.bible.android.view.activity

import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Test

/**
 * Strips Kotlin `//` line comments and slash-star ... star-slash block comments from [text], so a
 * source-text scan can't be defeated -- or taxed -- by a comment that legitimately quotes the very
 * pattern the scan looks for. Same helper, same rationale, as
 * [net.bible.android.view.activity.page.screen.ReadingToolbarBibleIconWiringGuardTest]'s
 * `stripComments` and `SearchHostBackRoutingGuardTest`'s.
 */
private fun stripComments(text: String): String {
    val noBlockComments = Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL).replace(text, "")
    return noBlockComments.lines().joinToString("\n") { line ->
        val commentAt = line.indexOf("//")
        if (commentAt >= 0) line.substring(0, commentAt) else line
    }
}

/**
 * F62 -- a wiring guard for [net.bible.android.view.activity.startupWelcomeAppNameRes] and
 * [net.bible.android.view.activity.startupWelcomeLogoRes]'s one production call site: since slice 8 E2,
 * `WelcomeFlow.navDeps()`'s `WelcomeNavDeps(...)` call. (The `StartupWelcomeScreen(...)` call itself now
 * lives in `:sharedUi`'s `WelcomeNavGraph` and passes on whatever the deps carry, so the CHOICE is made --
 * and guarded -- here.)
 *
 * Both helpers exist so the discrete-mode CHOICE is unit-testable
 * (`StartupWelcomeDiscreteChromeTest`) without a Compose rendering harness -- but that test calls
 * the helpers directly, so it proves the helpers are correct while proving nothing about whether
 * `WelcomeFlow.navDeps()`'s `WelcomeNavDeps(...)` call, their only production call
 * site, actually still uses them. Reverting `appName = host.getString(startupWelcomeAppNameRes())` /
 * `logo = { painterResource(startupWelcomeLogoRes()) }` back to raw
 * `host.getString(R.string.app_name_long)` / `painterResource(R.drawable.ic_logo)` literals leaves every
 * existing test green: the helper functions themselves become unreachable-but-still-present dead
 * code, and nothing else notices. That is exactly the "silently no wiring" failure shape
 * `SearchHostBackRoutingGuardTest`'s kdoc names for F43 and F55, and the same shape
 * `ReadingToolbarBibleIconWiringGuardTest` was written to close for F58's toolbar icon.
 *
 * This is a *source* guard, not a render/instrumentation test, for the same reason those two are:
 * the rendered `String`/`Painter` a `getString`/`painterResource` call produces is not usefully
 * comparable in a unit test, so the only thing left to assert is the WIRING -- that the call site
 * spells the helper, not the raw resource literal.
 *
 * Scoped narrowly to the `WelcomeNavDeps(...)` call's own argument list, not the whole file:
 * the helpers' own bodies legitimately contain the literals `R.string.app_name_long` and
 * `R.drawable.ic_logo` as their non-discrete branches, so a file-wide "the literal must not appear
 * anywhere" assertion would fail permanently on a correct fix, not just on a reverted one.
 */
class StartupWelcomeHeaderWiringGuardTest {

    companion object {
        private const val HOST_FILE =
            "src/main/java/net/bible/android/view/activity/WelcomeFlow.kt"
        private const val WELCOME_CALL_START = "WelcomeNavDeps("
        private val APP_NAME_CALL_SITE = Regex("""appName\s*=\s*host\.getString\(startupWelcomeAppNameRes\(\)\)""")
        private val LOGO_CALL_SITE = Regex("""logo\s*=\s*\{\s*painterResource\(startupWelcomeLogoRes\(\)\)\s*\}""")

        // Fix round 1 (review): the header is discrete-aware, but loadInfo() -- the SAME screen's
        // body text -- independently spelled the real app name, so discrete mode's
        // "Supported formats: AndBible zip, ..." line still leaked it.
        private val FORMAT_ZIP_CALL_SITE =
            Regex("""host\.getString\(R\.string\.format_zip,\s*host\.getString\(startupWelcomeShortAppNameRes\(\)\)\)""")
        private const val RAW_FORMAT_ZIP_LITERAL =
            "host.getString(R.string.format_zip, host.getString(R.string.app_name_andbible))"
    }

    private val rawSource = java.io.File(HOST_FILE).readText()
    private val source = stripComments(rawSource)

    /**
     * The `WelcomeNavDeps(...)` call's argument list only -- from just after
     * `WelcomeNavDeps(` to the closing `)` that sits alone on its own (indented) line, which
     * is how the real call site is formatted (each argument ends with a trailing comma; the closing
     * paren does not). Unlike [net.bible.android.view.activity.page.screen.ReadingToolbarBibleIconWiringGuardTest]'s
     * top-level `readingToolbarIcons()`, this call site is a member of `WelcomeFlow`,
     * so its closing paren is indented rather than sitting at column 0 -- `\n\s*\)`, not a literal
     * `\n)`.
     */
    private val CLOSING_PAREN_LINE = Regex("""\n\s*\)""")

    private fun welcomeCallBody(): String {
        val startAt = source.indexOf(WELCOME_CALL_START)
        assertThat("$HOST_FILE must still call WelcomeNavDeps(...)", startAt >= 0, equalTo(true))
        val bodyStart = startAt + WELCOME_CALL_START.length
        val closingMatch = CLOSING_PAREN_LINE.find(source, bodyStart)
        assertThat("WelcomeNavDeps(...)'s closing ) must be found", closingMatch != null, equalTo(true))
        return source.substring(bodyStart, closingMatch!!.range.first)
    }

    @Test
    fun theWelcomeCallSiteUsesTheDiscreteModeAppNameHelper() {
        val body = welcomeCallBody()
        assertThat(
            "WelcomeNavDeps(...)'s `appName` argument must call startupWelcomeAppNameRes() " +
                "(F62) -- reverting it to a raw getString(R.string.app_name_long) leaves discrete " +
                "mode's welcome header un-swapped, and StartupWelcomeDiscreteChromeTest, which calls " +
                "startupWelcomeAppNameRes() directly, would not notice",
            APP_NAME_CALL_SITE.containsMatchIn(body), equalTo(true),
        )
    }

    @Test
    fun theWelcomeCallSiteUsesTheDiscreteModeLogoHelper() {
        val body = welcomeCallBody()
        assertThat(
            "WelcomeNavDeps(...)'s `logo` argument must call startupWelcomeLogoRes() (F62) -- " +
                "reverting it to a raw painterResource(R.drawable.ic_logo) leaves discrete mode's " +
                "welcome header logo un-swapped, and StartupWelcomeDiscreteChromeTest, which calls " +
                "startupWelcomeLogoRes() directly, would not notice",
            LOGO_CALL_SITE.containsMatchIn(body), equalTo(true),
        )
    }

    @Test
    fun noRawAppNameOrLogoLiteralRemainsInTheWelcomeCallSite() {
        val body = welcomeCallBody()
        assertThat(
            "WelcomeNavDeps(...)'s argument list must not spell R.string.app_name_long " +
                "directly -- that literal belongs only inside startupWelcomeAppNameRes()'s own " +
                "non-discrete branch",
            body.contains("R.string.app_name_long"), equalTo(false),
        )
        assertThat(
            "WelcomeNavDeps(...)'s argument list must not spell R.drawable.ic_logo directly " +
                "-- that literal belongs only inside startupWelcomeLogoRes()'s own non-discrete branch",
            body.contains("R.drawable.ic_logo"), equalTo(false),
        )
    }

    @Test
    fun stripCommentsRemovesACommentThatQuotesTheHelperCallsWithoutTheRealCallSite() {
        // Reproduces the exact failure shape this guard must not be vulnerable to: a comment
        // mentioning the helpers sits above a reverted, raw-literal real call site -- a bare
        // (unstripped) substring search over the whole function would be satisfied by the comment
        // alone, even though the real arguments no longer call the helpers.
        val synthetic = """
            |WelcomeNavDeps(
            |    // F62: appName = host.getString(startupWelcomeAppNameRes()), logo = { painterResource(startupWelcomeLogoRes()) }
            |    appName = host.getString(R.string.app_name_long),
            |    logo = { painterResource(R.drawable.ic_logo) },
            |)
        """.trimMargin()
        val stripped = stripComments(synthetic)
        val startAt = stripped.indexOf(WELCOME_CALL_START)
        val body = stripped.substring(startAt + WELCOME_CALL_START.length, stripped.indexOf("\n)", startAt))
        assertThat(
            "the comment's mention of the helpers must be gone after stripping, leaving only the raw literals",
            APP_NAME_CALL_SITE.containsMatchIn(body) || LOGO_CALL_SITE.containsMatchIn(body), equalTo(false),
        )
        assertThat(body.contains("R.string.app_name_long"), equalTo(true))
        assertThat(body.contains("R.drawable.ic_logo"), equalTo(true))
    }

    @Test
    fun loadInfoUsesTheDiscreteModeAppNameHelperForTheZipFormatLine() {
        assertThat(
            "loadInfo()'s zip format line must call getString(R.string.format_zip, " +
                "getString(startupWelcomeShortAppNameRes())) -- reverting it to the raw " +
                "getString(R.string.app_name_andbible) leaves discrete mode's " +
                "'Supported formats: ...' line un-swapped",
            FORMAT_ZIP_CALL_SITE.containsMatchIn(source), equalTo(true),
        )
        assertThat(
            "the raw, non-discrete-aware format_zip call site must be gone",
            source.contains(RAW_FORMAT_ZIP_LITERAL), equalTo(false),
        )
    }
}
