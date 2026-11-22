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

package net.bible.sharedui.installzip

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import net.bible.sharedui.strings.LocalStrings
import net.bible.sharedui.strings.iosStrings
import kotlin.test.Test
import kotlin.test.assertEquals

/** #4192: a freshly shown install question ignores taps for a moment, so a double tap cannot answer the next one. */
@OptIn(ExperimentalTestApi::class)
class InstallZipQuestionGuardTest {
    @Test
    fun aTapRightAfterTheQuestionAppearsIsIgnoredAndALaterTapIsAccepted() = runComposeUiTest {
        mainClock.autoAdvance = false
        val answers = mutableListOf<InstallUiState>()
        setContent {
            CompositionLocalProvider(LocalStrings provides iosStrings("en")) {
                InstallZipSessionContent(InstallUiState.Overwrite(listOf("a.conf")), { answers += it }, { })
            }
        }
        mainClock.advanceTimeByFrame()
        onNodeWithTag("dialog-confirm").performClick()
        onNodeWithTag("dialog-confirm").performClick()
        assertEquals(emptyList(), answers, "taps inside the guard window")

        mainClock.advanceTimeBy(INSTALL_QUESTION_INPUT_GUARD_MILLIS + 50)
        onNodeWithTag("dialog-confirm").performClick()
        assertEquals(listOf<InstallUiState>(InstallUiState.Overwrite(listOf("a.conf"))), answers)
    }
}
