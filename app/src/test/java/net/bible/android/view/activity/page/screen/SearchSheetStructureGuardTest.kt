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
 * F6 Task 0 — a source guard for the search sheet's one structural rule.
 *
 * The `BottomSheetScaffold` that hosts the reading-view search sheet must be **unconditionally
 * present** and must **enclose** `key(gen)`. Conditionality — not the wrapper itself — is what
 * re-parents every pane's `AndroidView` and so destroys and recreates every `BibleView` WebView each
 * time search is opened or closed. The same rule the drawer wrap follows; see the comment at
 * `ComposeReadingViewHost.kt:1350-1357`.
 *
 * This is a *source* guard rather than a render test. `compose-ui-test` (`ui-test-junit4` /
 * `ui-test-manifest`) IS available in this module — added in round 6 for
 * `AbSearchableOptionSheetContentTest` — so unavailability is not the reason. The reason is scope:
 * exercising this invariant by rendering would need a `ComposeTestRule` hosting the whole
 * `ComposeReadingViewHost` composable, with its WebView/AndroidView/Koin/window-manager surface —
 * a far heavier harness than this one structural rule justifies. (Separately, a plain
 * `mountComposeView` test — the idiom this guard predates `compose-ui-test` from — never attaches
 * its container to a window, so composition never runs there and no `AndroidView` factory is ever
 * invoked; that idiom genuinely cannot exercise this invariant.) Same idiom as
 * `SettingsBadgeLayoutDriftTest`.
 *
 * Note on matching: the literal `key(gen)` appears five times in the host, four of them inside comments
 * (the first at line 1351, *above* the scaffold), so ordering assertions match on `key(gen) {` — the
 * single actual call site — and the conditional scan ignores comment lines.
 */
class SearchSheetStructureGuardTest {

    private val source = java.io.File(
        "src/main/java/net/bible/android/view/activity/page/screen/ComposeReadingViewHost.kt"
    ).readText()

    private val codeLines = source.lines()
        .map { it.trim() }
        .filterNot { it.startsWith("//") || it.startsWith("*") || it.startsWith("/*") }

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
}
