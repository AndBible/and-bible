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
package net.bible.android.view.activity.page

import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Test

/**
 * A/B F6-B1. Three places decide whether the IME-height padding on `mainBibleView` is in effect — the
 * padding write itself and the two offset getters that zero their navigation-bar term BECAUSE the
 * padding is assumed to be covering it. They are required to agree, and they were three independent
 * `imeHeight > 0` checks: suppressing the padding without touching the getters silently drops the
 * navigation-bar offset from the WebView.
 *
 * This is a SOURCE-level guard: it proves the three consumers still read one predicate, not that the
 * padding reaches a pixel. (An earlier version of this kdoc claimed "nothing uses createComposeRule" --
 * false since well before 2026-09-18; 30 test files under `view/compose` use it, and
 * `ReadingImePaddingTest` is the measured-layout guard this one cannot be.)
 */
class ImePaddingPredicateDriftTest {
    private val source = listOf(
        // Slice 8 F3: `MainBibleActivity.kt` (deleted in F4) left this list. It contributed 0 to
        // every count below -- the three consumers had already moved to ReadingInsets.
        "src/main/java/net/bible/android/view/activity/page/ReadingInsets.kt",
        // F59: the second implementation of the sink lives here, and this guard was green on a tree
        // where that implementation was empty. The exact counts below are unaffected because this host
        // stores the px value and never spells `imePaddingApplied` -- if that changes, re-derive the
        // counts deliberately rather than bumping them.
        "src/main/java/net/bible/android/view/activity/nav/NavHostComposeActivity.kt",
    ).joinToString("\n") { path ->
        val f = java.io.File(path)
        // Anti-vacuity: a path that stops existing must fail the guard, not quietly contribute "".
        require(f.exists()) { "$path not found — ImePaddingPredicateDriftTest scans it" }
        f.readText()
    }

    @Test
    fun theThreeConsumersAllReadTheOnePredicate() {
        assertThat(
            "the predicate must exist",
            source.contains("private val imePaddingApplied"), equalTo(true),
        )
        // Counts CALL SITES, not mentions: the padding write, `bottomOffset2` and
        // `bottomOffsetForWebView`. An earlier version counted every occurrence of the identifier and so
        // failed whenever a comment or a KDoc link named it — a guard that taxes the documentation
        // explaining the code it guards. Prose is free; a fourth `if (imePaddingApplied)` is not.
        assertThat(
            "padding write + bottomOffset2 + bottomOffsetForWebView = 3 call sites",
            Regex("""if \(imePaddingApplied\)""").findAll(source).count(), equalTo(3),
        )
    }

    @Test
    fun noConsumerStillDecidesForItself() {
        assertThat(
            "an `if (imeHeight > 0) 0 else` term means a getter is deciding on its own again",
            source.contains("if (imeHeight > 0) 0 else"), equalTo(false),
        )
    }

    @Test
    fun theSuppressedCaseDoesNotHandTheKeyboardHeightToTheWebView() {
        // `bottomOffset1` INCLUDES the IME height while the keyboard is up, so using it as the
        // else-term would reserve inside the WebView exactly the space the padding stopped reserving
        // outside it — the reported symptom, one layer down. The term must be the IME-free offset.
        assertThat(
            "both offset getters must fall back to the IME-free offset",
            Regex("if \\(imePaddingApplied\\) 0 else bottomOffset1WithoutIme").findAll(source).count(),
            equalTo(2),
        )
    }

    @Test
    fun theFieldsFocusIsWhatGatesTheSuppression() {
        // NOT `composeSearchModeActive`: search mode outlives the results sheet, so a WebView note
        // editor can be opened while it is still active and must still be lifted (spec §4).
        assertThat(
            source.contains("!composeSearchFieldFocused"), equalTo(true),
        )
    }
}
