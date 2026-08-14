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
 * There is no Compose UI-test harness in this repo (nothing uses createComposeRule, and
 * compose-ui-test cannot be added under strict egress) and no instrumented coverage of this activity,
 * so this source-level guard is what stops the three drifting apart again. It is NOT evidence of
 * runtime behaviour — the device pass is (spec §8).
 */
class ImePaddingPredicateDriftTest {
    private val source =
        java.io.File("src/main/java/net/bible/android/view/activity/page/MainBibleActivity.kt").readText()

    @Test
    fun theThreeConsumersAllReadTheOnePredicate() {
        assertThat(
            "the predicate must exist",
            source.contains("private val imePaddingApplied"), equalTo(true),
        )
        assertThat(
            "padding write + bottomOffset2 + bottomOffsetForWebView = 3 reads, plus the declaration",
            Regex("imePaddingApplied").findAll(source).count(), equalTo(4),
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
